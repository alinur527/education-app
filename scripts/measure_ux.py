"""Controlled local cold-context / warm-reload frontend comparison.

Uses an existing ignored synthetic fixture. Reports public URLs and numeric timings only;
never persists credentials, JWTs, request headers, API bodies, or a HAR containing them.
"""
import argparse
import datetime as dt
import gzip
import hashlib
import json
import statistics
import time
from pathlib import Path
from urllib.parse import urlparse

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
INIT = r"""(() => {
  const m=window.__ux={pending:0,longTasks:[],shifts:[],lcp:0};
  const fetchOriginal=window.fetch;
  window.fetch=function(...args){m.pending++;return fetchOriginal.apply(this,args).finally(()=>m.pending--);};
  for(const [type,save] of [
    ['longtask',e=>m.longTasks.push({start:e.startTime,duration:e.duration})],
    ['layout-shift',e=>{if(!e.hadRecentInput)m.shifts.push({start:e.startTime,value:e.value});}],
    ['largest-contentful-paint',e=>m.lcp=e.startTime]
  ])try{new PerformanceObserver(list=>list.getEntries().forEach(save)).observe({type,buffered:true});}catch{}
})();"""
READY = r"""({route,subjectCount})=>new Promise((resolve,reject)=>{
  let first=null;
  const timeout=setTimeout(()=>reject(new Error('Route readiness timed out')),30000);
  function frame(){
    const m=window.__ux;
    const primary=route==='dashboard'?document.querySelector('.study-hero')&&document.querySelectorAll('.subject-card').length===3:
      route==='subjects'?document.querySelectorAll('.subject-card').length===subjectCount:
      document.querySelectorAll('.topic-row').length>0;
    const good=primary&&m.pending===0&&!document.querySelector('[role="status"]')&&document.fonts.status==='loaded';
    if(good){if(first===null)first=performance.now();if(performance.now()-first>=100){clearTimeout(timeout);resolve(first);return;}}
    else first=null;
    requestAnimationFrame(frame);
  }frame();
})"""
READ = r"""()=>{
 const m=window.__ux,nav=performance.getEntriesByType('navigation')[0];
 let cls=0,value=0,first=0,last=0;
 for(const e of m.shifts){if(e.start-last>1000||e.start-first>5000){value=e.value;first=e.start;}else value+=e.value;last=e.start;cls=Math.max(cls,value);}
 const resources=performance.getEntriesByType('resource').filter(e=>new URL(e.name).origin===location.origin).map(e=>({path:new URL(e.name).pathname,initiator:e.initiatorType,startMs:e.startTime,durationMs:e.duration,transferBytes:e.transferSize,encodedBodyBytes:e.encodedBodySize,decodedBodyBytes:e.decodedBodySize}));
 return {observedWindowMs:performance.now(),fcpMs:performance.getEntriesByName('first-contentful-paint')[0]?.startTime??null,lcpMs:m.lcp||null,cls,longTaskCount:m.longTasks.length,longTaskMs:m.longTasks.reduce((s,e)=>s+e.duration,0),longTasks:m.longTasks,documentTransferBytes:nav.transferSize,resources,domElements:document.getElementsByTagName('*').length,visibleTopicRows:document.querySelectorAll('.topic-row').length,entryScript:new URL(document.querySelector('script[type=module]').src).pathname,overflow:document.documentElement.scrollWidth>innerWidth};
}"""


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False).encode()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', default='http://127.0.0.1:8082')
    parser.add_argument('--current', default='http://127.0.0.1:8081')
    parser.add_argument('--samples', type=int, default=3)
    parser.add_argument('--out', default='test-results/ux-performance')
    args = parser.parse_args()
    assert 1 <= args.samples <= 10
    origins = {'baseline': args.baseline.rstrip('/'), 'current': args.current.rstrip('/')}
    assert all(urlparse(url).hostname in ('localhost', '127.0.0.1') for url in origins.values())
    out = ROOT / args.out
    out.mkdir(parents=True, exist_ok=True)
    account = json.loads((ROOT / 'test-results/phase2-fixtures.json').read_text(encoding='utf-8-sig'))['STUDENT']
    records, errors = [], []
    with sync_playwright() as p:
        api = p.request.new_context(base_url=origins['current'])
        auth = api.post('/api/auth/login', data={key: account[key] for key in ('email', 'password')})
        assert auth.ok, f'Fixture login status {auth.status}'
        token = auth.json()['token']
        headers = {'Authorization': 'Bearer ' + token}
        subjects = api.get('/api/subjects', headers=headers).json()
        math = next(s['id'] for s in subjects if s['nameRu'] == 'Математика')
        paths = {'dashboard': '/', 'subjects': '/subjects', 'math_catalog': '/subjects/' + math}
        dataset_paths = ['/api/subjects', '/api/topics/subject/' + math, '/api/statistics/me', '/api/learning/me']
        def dataset():
            return {path: fingerprint(api.get(path, headers=headers).json()) for path in dataset_paths}
        before = dataset()
        browser = p.chromium.launch(headless=True)
        browser_version = browser.version
        for viewport in ({'width': 1440, 'height': 1000}, {'width': 390, 'height': 844}):
            for route, path in paths.items():
                for sample in range(args.samples):
                    # Alternate ordering to reduce always-first server/OS cache bias.
                    order = ['baseline', 'current'] if sample % 2 == 0 else ['current', 'baseline']
                    for build in order:
                        context = browser.new_context(viewport=viewport, locale='ru-RU', service_workers='block')
                        context.add_init_script('sessionStorage.setItem("education.session",' + json.dumps(token) + ');' + INIT)
                        page = context.new_page()
                        page.set_default_timeout(30000)
                        requests = []
                        page.on('request', lambda request: requests.append({'path': urlparse(request.url).path, 'type': request.resource_type}))
                        page.on('pageerror', lambda error: errors.append({'kind': 'page', 'message': str(error)}))
                        page.on('response', lambda response: errors.append({'kind': 'http', 'status': response.status, 'path': urlparse(response.url).path}) if response.status >= 400 else None)
                        for cache in ('cold', 'warm'):
                            requests.clear()
                            if cache == 'cold':
                                page.goto(origins[build] + path, wait_until='domcontentloaded')
                            else:
                                page.reload(wait_until='domcontentloaded')
                            ready = page.evaluate(READY, {'route': route, 'subjectCount': len(subjects)})
                            page.wait_for_load_state('networkidle')
                            page.evaluate('document.fonts.ready')
                            page.evaluate('()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))')
                            data = page.evaluate(READ)
                            data.update(build=build, viewport=viewport['width'], route=route, cache=cache, sample=sample + 1, routeReadyMs=ready, requestCount=len(requests), apiRequestCount=sum(r['path'].startswith('/api/') for r in requests))
                            js = [r for r in data['resources'] if r['path'].endswith('.js')]
                            data['jsTransferBytes'] = sum(r['transferBytes'] for r in js)
                            data['jsEncodedBodyBytes'] = sum(r['encodedBodyBytes'] for r in js)
                            data['totalTransferBytes'] = data['documentTransferBytes'] + sum(r['transferBytes'] for r in data['resources'])
                            records.append(data)
                            print(f'{build:8} {viewport["width"]} {route:12} {cache:4} {sample+1}: ready={ready:.1f}ms JS={data["jsTransferBytes"]}B CLS={data["cls"]:.4f}', flush=True)
                        context.close()
        after = dataset()
        assert before == after, 'Dataset changed during comparison; rerun in a stable window'
        assets = {}
        for build, origin in origins.items():
            paths_js = sorted({r['path'] for row in records if row['build'] == build for r in row['resources'] if r['path'].endswith('.js')})
            assets[build] = []
            for path in paths_js:
                response = api.get(origin + path)
                assert response.ok, path
                body = response.body()
                assets[build].append({'path': path, 'rawBytes': len(body), 'gzip9Bytes': len(gzip.compress(body, compresslevel=9, mtime=0)), 'contentEncoding': response.headers.get('content-encoding', 'identity'), 'sha256': hashlib.sha256(body).hexdigest()})
        # Separate PWA first-install run; service-worker prefetch is excluded above.
        sw_context = browser.new_context(viewport={'width': 1440, 'height': 1000}, service_workers='allow')
        sw_context.add_init_script('sessionStorage.setItem("education.session",' + json.dumps(token) + ');')
        sw_page = sw_context.new_page()
        start = time.perf_counter()
        sw_page.goto(origins['current'] + '/', wait_until='domcontentloaded')
        sw_page.wait_for_load_state('networkidle')
        sw_page.evaluate('()=>Promise.race([navigator.serviceWorker.ready,new Promise((_,reject)=>setTimeout(()=>reject(new Error("Service worker install timed out")),30000))]).then(()=>null)')
        caches = sw_page.evaluate('async()=>{const names=await caches.keys();return(await Promise.all(names.map(async n=>(await(await caches.open(n)).keys()).map(r=>new URL(r.url).pathname)))).flat();}')
        assert not any(path.startswith('/api/') for path in caches), 'API unexpectedly present in static SW cache'
        sw = {'installAndNavigationMs': (time.perf_counter() - start) * 1000, 'cachedEntries': len(caches), 'cachedJsEntries': sum(path.endswith('.js') for path in caches), 'apiEntries': sum(path.startswith('/api/') for path in caches), 'paths': caches}
        sw_context.close()
        browser.close()
        result = {'measuredAt': dt.datetime.now(dt.timezone.utc).isoformat(), 'browser': browser_version, 'origins': origins, 'samplesPerCell': args.samples, 'baselineCommit': 'e39293b', 'sameBackendAndData': True, 'datasetHashes': before, 'subjectCount': len(subjects), 'mathTopicCount': len(api.get('/api/topics/subject/' + math, headers=headers).json()), 'records': records, 'assets': assets, 'pwaFirstInstallSeparate': sw, 'errors': errors}
        (out / 'raw.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
        rows = []
        for build in origins:
            for viewport in (1440, 390):
                for route in paths:
                    for cache in ('cold', 'warm'):
                        data = [r for r in records if (r['build'], r['viewport'], r['route'], r['cache']) == (build, viewport, route, cache)]
                        row = {'build': build, 'viewport': viewport, 'route': route, 'cache': cache}
                        for key in ('routeReadyMs', 'fcpMs', 'lcpMs', 'cls', 'longTaskCount', 'longTaskMs', 'requestCount', 'apiRequestCount', 'jsTransferBytes', 'jsEncodedBodyBytes', 'totalTransferBytes', 'domElements', 'visibleTopicRows'):
                            values = [r[key] for r in data if r[key] is not None]
                            row[key] = {'median': statistics.median(values), 'min': min(values), 'max': max(values)} if values else None
                        rows.append(row)
        (out / 'summary.json').write_text(json.dumps({'cells': rows, 'assets': assets, 'pwaFirstInstallSeparate': sw, 'errors': errors}, ensure_ascii=False, indent=2), encoding='utf-8')
        assert not errors, errors
        assert not any(r['overflow'] for r in records), 'Horizontal overflow observed'
        print(f'Saved {len(records)} measured navigations; browser/API errors: {len(errors)}', flush=True)


if __name__ == '__main__':
    main()
