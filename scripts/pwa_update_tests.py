"""Verify real service-worker update blocking/activation; restore served sw.js in finally.

Requires local Docker. Temporarily changes only the compiled worker cache version,
never repository source, API responses or student data.
"""
import json,os,subprocess,uuid
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright,expect
BASE=os.getenv('BROWSER_BASE_URL','http://127.0.0.1:8081')
assert urlparse(BASE).hostname in ('localhost','127.0.0.1')
OUT=Path('test-results/pwa-update');OUT.mkdir(parents=True,exist_ok=True)
with sync_playwright() as p:
    api=p.request.new_context(base_url=BASE)
    original=api.get('/sw.js');assert original.ok
    raw=original.body();assert b'education-shell-' in raw
    restore=OUT/'original-sw.js';restore.write_bytes(raw)
    updated=OUT/'updated-sw.js';updated.write_bytes(raw.replace(b'education-shell-',('education-shell-update-test-'+uuid.uuid4().hex+'-').encode(),1))
    account={'email':'pwa-'+str(uuid.uuid4())+'@example.org','password':'Pwa-'+str(uuid.uuid4()),'firstName':'PWA','lastName':'Verification','language':'ru'}
    r=api.post('/api/auth/register',data=account);assert r.ok
    browser=p.chromium.launch(headless=True);context=browser.new_context();page=context.new_page();errors=[]
    page.on('pageerror',lambda e:errors.append(str(e)))
    page.goto(BASE+'/login');page.get_by_label('Электронная почта').fill(account['email']);page.get_by_label('Пароль',exact=True).fill(account['password']);page.get_by_role('button',name='Войти',exact=True).click();expect(page.locator('.study-hero')).to_be_visible()
    page.evaluate('async()=>{await navigator.serviceWorker.ready;}');page.reload();page.wait_for_function('!!navigator.serviceWorker.controller')
    try:
        page.goto(BASE+'/practice');page.get_by_label('Математика',exact=False).first.check();page.get_by_label('Количество вопросов (до 50)').fill('2');page.get_by_role('button',name='Начать тренировку',exact=True).click();expect(page.locator('.question-card')).to_be_visible()
        url=page.url
        subprocess.run(['docker','compose','cp',str(updated),'frontend:/usr/share/nginx/html/sw.js'],check=True,capture_output=True)
        page.evaluate('async()=>{const r=await navigator.serviceWorker.getRegistration();await r.update();}')
        page.wait_for_function('async()=>!!(await navigator.serviceWorker.getRegistration()).waiting')
        expect(page.get_by_role('button',name='Обновить приложение',exact=True)).to_be_disabled()
        page.evaluate('async()=>{const r=await navigator.serviceWorker.getRegistration();r.waiting.postMessage({type:"APPLY_UPDATE"});}')
        expect(page.get_by_text('Для обновления закройте другие вкладки приложения и откройте главную страницу.',exact=True)).to_be_visible()
        assert page.url==url and page.evaluate('async()=>!!(await navigator.serviceWorker.getRegistration()).waiting')
        page.reload();expect(page.locator('.question-card')).to_be_visible();assert page.url==url
        page.get_by_role('button',name='Завершить тест',exact=True).click();page.get_by_role('button',name='Завершить попытку',exact=True).click();expect(page.locator('.result-banner')).to_be_visible()
        page.goto(BASE+'/');expect(page.locator('.study-hero')).to_be_visible()
        extra=context.new_page();extra.goto(BASE+'/saved')
        page.get_by_role('button',name='Обновить приложение',exact=True).click()
        expect(page.get_by_text('Для обновления закройте другие вкладки приложения и откройте главную страницу.',exact=True)).to_be_visible()
        assert page.evaluate('async()=>!!(await navigator.serviceWorker.getRegistration()).waiting')
        extra.close();page.get_by_role('button',name='Обновить приложение',exact=True).click()
        page.wait_for_function('async()=>!(await navigator.serviceWorker.getRegistration()).waiting');expect(page.locator('.study-hero')).to_be_visible()
        cache=page.evaluate('async()=>await caches.keys()');assert any('update-test' in k for k in cache)
        assert not errors,errors
        OUT.joinpath('report.json').write_text(json.dumps({'checks':['unsafe attempt blocks activation','refresh preserves session and pending update','second tab blocks activation','explicit single safe tab activation and reload'],'errors':errors},indent=2),encoding='utf-8')
        print('PASS PWA: active attempt, reload, multiple tabs, explicit safe update')
    finally:
        subprocess.run(['docker','compose','cp',str(restore),'frontend:/usr/share/nginx/html/sw.js'],check=True,capture_output=True)
        context.close();browser.close();api.dispose()
