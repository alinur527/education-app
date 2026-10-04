#!/usr/bin/env python3
"""Offline integrity gate for the checked-in curriculum and original starter packs.

Stdlib only, no network, database, application login or content publication.
Run from any directory. The default also reruns the 12 bounded authored Python
examples and eight boundary cases; --verify-cache requires ignored source bytes.
It is not a subject-expert or human editorial approval.
"""
from __future__ import annotations

import argparse
import ast
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
CHECK_FIELDS = {'sourceRead', 'extractionChecked', 'answerChecked', 'translationChecked', 'explanationChecked'}
EXPECTED_SUBJECTS = {
    'Математическая грамотность': (2, 20), 'Грамотность чтения': (2, 20),
    'История Казахстана': (5, 50), 'Математика': (5, 50), 'Физика': (5, 50),
    'Химия': (2, 20), 'Биология': (2, 20), 'География': (2, 20),
    'Информатика': (2, 20), 'Всемирная история': (2, 20), 'Основы права': (2, 20),
    'Русский язык': (2, 20), 'Русская литература': (2, 20), 'Казахский язык': (2, 20),
    'Казахская литература': (2, 20), 'Английский язык': (2, 20),
    'Немецкий язык': (2, 20), 'Французский язык': (2, 20),
}
SHA = re.compile(r'^[0-9a-f]{64}$')
KEY = re.compile(r'^[a-z0-9][a-z0-9-]*$')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read(relative):
    return json.loads((ROOT / relative).read_text(encoding='utf-8-sig'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def text(value, label, minimum=1):
    require(isinstance(value, str) and len(value.strip()) >= minimum, f'{label}: missing/short text')
    require('\ufffd' not in value and '\x00' not in value, f'{label}: damaged Unicode')


def url(value):
    parsed = urlsplit(value)
    require(parsed.scheme == 'https' and parsed.hostname and not parsed.username and not parsed.password,
            f'Expected credential-free HTTPS source URL: {value}')


def key_of(q):
    return q.get('correctOptionId', q.get('correctOptionIds', q.get('correctPairs')))


def question(q, label):
    for name in ('titleRu', 'titleKz', 'explanationRu', 'explanationKz', 'answerEvidence'):
        text(q.get(name), f'{label}/{name}')
    require(q.get('difficulty') in {'easy', 'medium', 'hard'}, f'{label}: invalid difficulty')
    require(q.get('sourceType', 'AI_GENERATED') == 'AI_GENERATED', f'{label}: original question mislabelled as official')
    require(not q.get('verified') and not q.get('editorialApproval'), f'{label}: unexpected human/verified approval')
    require(set(q.get('reviewChecks', {})) == CHECK_FIELDS and all(v is True for v in q['reviewChecks'].values()),
            f'{label}: incomplete model review checks')
    options = q.get('options', [])
    ids = [o.get('id') for o in options]
    require(2 <= len(ids) <= 10 and len(set(ids)) == len(ids) and all(isinstance(x, str) and x for x in ids), f'{label}: option IDs')
    for lang in ('Ru', 'Kz'):
        for option in options:
            text(option.get('text' + lang), f'{label}/option/{lang}')
        require(len({o['text' + lang].strip() for o in options}) == len(ids), f'{label}: duplicate option text/{lang}')
    kind = q.get('questionType')
    require(kind in {'SINGLE_CHOICE', 'MULTIPLE_SELECT', 'MATCHING'}, f'{label}: unknown question type')
    correct_fields = [name for name in ('correctOptionId', 'correctOptionIds', 'correctPairs') if name in q]
    require(len(correct_fields) == 1, f'{label}: ambiguous answer representation')
    if kind == 'SINGLE_CHOICE':
        require(q.get('correctOptionId') in ids, f'{label}: missing single answer')
    elif kind == 'MULTIPLE_SELECT':
        answers = q.get('correctOptionIds', [])
        require(1 <= len(answers) <= 3 and len(set(answers)) == len(answers) and set(answers) <= set(ids), f'{label}: invalid multiple key')
    else:
        left = q.get('leftOptions', [])
        left_ids = [o.get('id') for o in left]
        pairs = q.get('correctPairs', [])
        require(len(left_ids) == 2 and len(set(left_ids)) == 2, f'{label}: matching needs two distinct left IDs')
        for item in left:
            text(item.get('textRu'), f'{label}/left/Ru'); text(item.get('textKz'), f'{label}/left/Kz')
        require(len(pairs) == 2 and {p.get('leftId') for p in pairs} == set(left_ids)
                and all(p.get('rightId') in ids for p in pairs), f'{label}: incomplete/forged matching relationship')


def sources(verify_cache):
    input_manifest = read('content/inputs/INPUT_MANIFEST.json')
    for item in input_manifest['files']:
        path = ROOT / 'content/inputs' / Path(item['path']).name
        require(path.is_file() and path.stat().st_size == item['bytes'] and sha(path) == item['sha256'], f'Original user input changed: {path.name}')
    manifest = read('content/research/official-source-manifest.json')
    entries = {s['sourceId']: s for s in manifest['sources']}
    require(len(entries) == len(manifest['sources']) == 77, 'Expected distinct NTC manifests: 73 PDF + 4 HTML')
    require(Counter(s['contentType'] for s in entries.values()) == {'application/pdf': 73, 'text/html': 4}, 'Source MIME accounting changed')
    cached = 0
    cache_root = (ROOT / 'test-results/source-research').resolve()
    for sid, item in entries.items():
        for name in ('url', 'resolvedUrl'): url(item[name])
        require(SHA.fullmatch(item['sha256']) and 0 < item['byteSize'] <= 20 * 1024**2, f'{sid}: invalid SHA/size')
        require(item['accessStatus'] == 'DOWNLOADED' and item['retrievedAt'], f'{sid}: missing download evidence')
        require(item['reuseMode'] == 'EXTERNAL_LINK_UNLESS_REUSE_CONFIRMED', f'{sid}: unexplained source redistribution grant')
        path = (ROOT / item['localPath']).resolve()
        require(path.is_relative_to(cache_root), f'{sid}: source-cache traversal')
        if path.exists():
            require(path.stat().st_size == item['byteSize'] and sha(path) == item['sha256'], f'{sid}: cached bytes differ from manifest')
            if item['contentType'] == 'application/pdf': require(path.read_bytes().startswith(b'%PDF-'), f'{sid}: PDF signature')
            cached += 1
        elif verify_cache: raise ValueError(f'{sid}: source bytes absent; run explicit research download outside CI')
    material = read('content/research/python-material.json')
    m = material['material']; rights = material['rights']
    require(m['language'] == 'en' and m['pageCount'] == 157 and SHA.fullmatch(m['sha256']), 'Python PDF version/language evidence')
    require(rights['reuseDecision'] == 'REDISTRIBUTION_ALLOWED_WITH_NOTICES_RETAINED'
            and rights['noticesPreservedInPdf'] and rights['fullLicensePreservedInPdf']
            and not rights['translationClaim'] and not rights['endorsementClaim'], 'Python PDF rights/attribution lost')
    for name in ('licenseUrl', 'versionLicenseUrl'): url(rights[name])
    path = (ROOT / m['localIgnoredPath']).resolve()
    require(path.is_relative_to(cache_root), 'Python PDF cache traversal')
    if path.exists(): require(sha(path) == m['sha256'] and path.stat().st_size == m['size'], 'Python PDF bytes changed')
    elif verify_cache: raise ValueError('Permitted Python PDF absent from source cache')
    return entries, cached


def curriculum(entries):
    data = read('content/research/official-curriculum.json')
    require(data['editorialApproval'] is False, 'Research must not become editorial approval')
    programmes = {p['sourceId']: p for p in data['programmes']}
    require(len(programmes) == 36 and {p['subjectRu'] for p in programmes.values()} == set(EXPECTED_SUBJECTS), 'Official programme/direction coverage')
    mapping = {}
    for sid, p in programmes.items():
        require(p['sha256'] == entries[sid]['sha256'] and p['url'] == entries[sid]['url'], f'{sid}: programme provenance mismatch')
        require(p['appliesFrom'] == 2026 and p['examVersion'] == 'NTC_FROM_2026' and p['appliesFromEvidence']['page'] == 1, f'{sid}: unsupported version inference')
        require(p['documentLanguage'] in {'ru', 'kk'} and p['programmeVariant'], f'{sid}: language variant lost')
        topics = [t for section in p['sections'] for t in section['topics']]
        require(len(topics) == p['topicCount'] and [int(t['officialCode']) for t in topics] == list(range(1, len(topics) + 1)), f'{sid}: topic sequence missing/duplicated')
        for t in topics:
            text(t['title'], f'{sid}/{t["officialCode"]}')
            require(t['sourceId'] == sid and t['pages'] and all(1 <= page <= p['pageCount'] for page in t['pages']), f'{sid}: invalid source page')
            mapping[(sid, t['officialCode'])] = (p, t)
    require(len(mapping) == 748, 'Official variant topic count must remain distinct from starter count')
    require(mapping[('SPEC-30', '23')][1]['extractionStatus'] == 'UNCERTAIN_SOURCE_TITLE_VISIBLY_CLIPPED', 'Known clipped source must retain uncertainty')
    require(mapping[('SPEC-30', '24')][1].get('extractedTitleBeforeReview'), 'Visual correction must retain original extracted text')
    require(any(s['officialCode'] is None and s.get('sourceAnomaly') for s in programmes['SPEC-21']['sections']), 'Blank official section code must not be invented')
    return mapping


def school_packs(mapping):
    packs = {}; units = {}; all_questions = {}; titles = set(); coverage = {}; contexts = {}; types = Counter()
    for name in ('pilot', 'core', 'languages'):
        data = read(f'content/starter/{name}.json'); packs[name] = data
        require(data['schema'] == 'education-starter-authoring/v1', f'{name}: schema')
        for subject in data['subjects']:
            ru = subject['subjectRu']; text(subject['subjectKz'], f'{ru}/Kz')
            require(ru in EXPECTED_SUBJECTS and ru not in coverage, f'{ru}: unknown/duplicated direction')
            topics = subject['topics']; coverage[ru] = (len(topics), sum(len(t['questions']) for t in topics))
            for t in topics:
                key = t['externalKey']; require(KEY.fullmatch(key) and key not in units, f'Duplicate/unstable lesson key {key}')
                units[key] = t
                p, official = mapping[(t['officialSourceId'], t['officialCode'])]
                require(p['subjectRu'] == ru, f'{key}: maps to another subject')
                require(not official['extractionStatus'].startswith('UNCERTAIN'), f'{key}: starter based on unresolved source title')
                for lang in ('Ru', 'Kz'):
                    text(t.get('title' + lang), f'{key}/title/{lang}')
                    text(t.get('theory' + lang), f'{key}/theory/{lang}', 1500)
                require(t['theoryRu'] != t['theoryKz'], f'{key}: untranslated theory copy')
                require(t['sourceUrls'], f'{key}: missing factual references')
                for reference in t['sourceUrls']: url(reference)
                for c in t.get('contexts', []):
                    require(c['externalKey'] not in contexts and c.get('original') is True, f'{key}: duplicated/non-original context')
                    for lang in ('Ru', 'Kz'): text(c.get('content' + lang), f'{key}/context/{lang}', 500)
                    contexts[c['externalKey']] = key
                require(len(t['questions']) == 10, f'{key}: expected ten varied questions')
                for index, q in enumerate(t['questions'], 1):
                    qkey = q.get('externalKey', f'{key}-q{index:02}')
                    require(qkey not in all_questions, f'Duplicate question key {qkey}')
                    all_questions[qkey] = q; question(q, qkey); types[q['questionType']] += 1
                    require(q['titleRu'] not in titles, f'{qkey}: duplicate Russian prompt'); titles.add(q['titleRu'])
                    if 'contextExternalKey' in q: require(contexts.get(q['contextExternalKey']) == key, f'{qkey}: missing/foreign context')
                    if ru == 'Основы права':
                        ev = q.get('sourceEvidence', {})
                        require(t.get('legalInstrument') == 'K2600000000' and t.get('legalEffectiveFrom') == '2026-07-01', f'{key}: old constitutional applicability')
                        require(ev.get('effectiveFrom') == '2026-07-01' and ev.get('checkedAt') == '2026-10-04' and ev.get('article'), f'{qkey}: law evidence')
                        require(ev.get('url') == 'https://old.adilet.zan.kz/rus/docs/K2600000000' and ev['article'] in read_law_articles(), f'{qkey}: missing current-law excerpt')
    require(coverage == EXPECTED_SUBJECTS and len(units) == 45 and len(all_questions) == 450, 'Starter release coverage changed')
    require(len(contexts) == 2 and sum('contextExternalKey' in q for q in all_questions.values()) == 20, 'Shared reading contexts/reference coverage')
    for name in ('pilot', 'core'):
        proof = read(f'content/starter/{name}-proofs.json')
        require(proof['sourceSha256'] == sha(ROOT / f'content/starter/{name}.json'), f'{name}: proof report does not describe these bytes')
        seen = set()
        for result in proof['proofs']:
            qkey = result['questionExternalKey']; require(qkey in all_questions and qkey not in seen and result['passed'] is True, f'{name}: invalid/duplicate proof')
            seen.add(qkey); q = all_questions[qkey]
            expected = result['expectedAnswer']
            actual = next(o['textRu'] for o in q['options'] if o['id'] == q['correctOptionId']) if q['questionType'] == 'SINGLE_CHOICE' else key_of(q)
            if isinstance(actual, str): actual = actual.replace('−', '-'); expected = expected.replace('−', '-')
            require(actual == expected, f'{qkey}: answer/proof mismatch')
        require(proof['explicitAnswerChecks'] == len(seen), f'{name}: misleading proof total')
    return packs, units, coverage, types


def read_law_articles():
    law = read('content/research/current-law-2026.json')
    require(law['instrumentId'] == 'K2600000000' and law['effectiveFrom'] == '2026-07-01'
            and law['humanLegalReview'] is False and law['curriculumConflict'], 'Current-law applicability/review boundary')
    require(law['downloadedFileSha256'] is None and law['shaLimitation'], 'Unarchived legal HTML must not invent a hash')
    # A citation to article 94 legitimately resolves to its quoted paragraph 94(1).
    articles = {item['article'] for item in law['readExcerpts'] if item.get('textRu')}
    return articles | {article.split('(')[0] for article in articles}


def python_course():
    data = read('content/starter/python-course.json'); course = data['course']
    require(course['sourceType'] == 'AI_GENERATED' and course['editorialApproval'] is False, 'Python course approval boundary')
    require(len(course['modules']) == 2, 'Python module coverage')
    lessons = {}; examples = []
    for module in course['modules']:
        require(len(module['lessons']) == 3, 'Python module must contain three lessons')
        for lesson in module['lessons']:
            key = lesson['externalKey']; require(key not in lessons and KEY.fullmatch(key), 'Python lesson key')
            lessons[key] = lesson
            for lang in ('Ru', 'Kz'):
                text(lesson['theory' + lang], f'{key}/theory/{lang}', 1500)
                text(lesson['assignment']['description' + lang], f'{key}/assignment/{lang}')
                require(lesson['assignment']['acceptanceCriteria' + lang], f'{key}: assignment criteria')
            a = lesson['assignment']; require(a['maxScore'] > 0 and a['rubric'] and a['sampleRuns'], f'{key}: assignment assessment missing')
            require('.py' not in a['allowedExtensions'], f'{key}: executable upload mistakenly required')
            require(len(lesson['quiz']['questions']) == 5, f'{key}: quiz coverage')
            for i, q in enumerate(lesson['quiz']['questions'], 1): question(q, f'{key}-q{i:02}')
            require(len(lesson['codeExamples']) == 2, f'{key}: examples missing')
            for i, example in enumerate(lesson['codeExamples'], 1):
                examples.append((key, i, example))
    runs = {(r['lesson'], r['example']): r for r in data['verification']['exampleRuns']}
    require(len(runs) == len(examples) == 12, 'Python execution evidence coverage')
    for key, i, example in examples:
        require(hashlib.sha256(example['code'].encode()).hexdigest() == runs[(key, i)]['codeSha256']
                and runs[(key, i)]['stdoutMatched'], f'{key}/{i}: stale execution evidence')
        run_example(example, f'{key}/{i}')
    condition = lessons['python-start-conditions']['codeExamples'][0]
    cases = [(-1, 'invalid'), (0, 'restart'), (49, 'restart'), (50, 'practice'), (79, 'practice'), (80, 'ready'), (100, 'ready'), (101, 'invalid')]
    for value, output in cases: run_example({**condition, 'stdin': str(value) + '\n', 'expectedStdout': output + '\n'}, f'boundary/{value}')
    return lessons


def run_example(example, label):
    """Run only the bounded Python teaching syntax, never arbitrary imported code."""
    code = example['code']; require(len(code) <= 10000, f'{label}: code too large')
    tree = ast.parse(code)
    forbidden = (ast.Import, ast.ImportFrom, ast.ClassDef, ast.Lambda, ast.While, ast.AsyncFunctionDef, ast.Global, ast.Nonlocal, ast.Delete)
    functions = {n.name for n in ast.walk(tree) if isinstance(n, ast.FunctionDef)}
    allowed_calls = {'print', 'input', 'int', 'len', 'range', 'open'} | functions
    for node in ast.walk(tree):
        require(not isinstance(node, forbidden), f'{label}: unsupported teaching syntax {type(node).__name__}')
        if isinstance(node, ast.Attribute): require(node.attr in {'append', 'strip', 'read', 'write'}, f'{label}: forbidden attribute')
        if isinstance(node, ast.Name): require(not node.id.startswith('__'), f'{label}: forbidden special name')
        if isinstance(node, ast.Call):
            require((isinstance(node.func, ast.Name) and node.func.id in allowed_calls) or isinstance(node.func, ast.Attribute), f'{label}: unsupported call')
        if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)): require(abs(node.value) <= 10000, f'{label}: oversized literal')
    allowed_files = set(example['inputFiles']) | set(example['expectedFiles'])
    require(all(Path(name).name == name and name not in {'.', '..'} for name in allowed_files), f'{label}: unsafe fixture path')
    harness = '''import builtins,json,pathlib,sys
p=json.loads(pathlib.Path("fixture.json").read_text(encoding="utf-8"))
base=pathlib.Path.cwd().resolve()
allowed=set(p["inputFiles"])|set(p["expectedFiles"])
def checked_open(name,mode="r",**kwargs):
 path=pathlib.Path(name)
 if str(name) not in allowed or path.name!=str(name) or mode not in ("r","w"):
  raise ValueError("Example file access outside declared fixture")
 return builtins.open(base/path,mode,**kwargs)
scope={"__builtins__":{"print":print,"input":input,"int":int,"len":len,"range":range,"open":checked_open}}
exec(compile(p["code"],"<authored-example>","exec"),scope,scope)
'''
    with tempfile.TemporaryDirectory(prefix='education-content-') as directory:
        folder = Path(directory)
        for name, value in example['inputFiles'].items(): (folder / name).write_text(value, encoding='utf-8')
        (folder / 'fixture.json').write_text(json.dumps(example, ensure_ascii=False), encoding='utf-8')
        (folder / 'runner.py').write_text(harness, encoding='utf-8')
        run = subprocess.run([sys.executable, '-I', '-X', 'utf8', str(folder / 'runner.py')], cwd=folder,
                             input=example['stdin'], text=True, encoding='utf-8', capture_output=True, timeout=3)
        require(run.returncode == 0, f'{label}: code failed: {run.stderr[:500]}')
        require(run.stdout == example['expectedStdout'], f'{label}: stdout mismatch')
        for name, value in example['expectedFiles'].items():
            require((folder / name).is_file() and (folder / name).read_text(encoding='utf-8') == value, f'{label}: file output mismatch')


def review_report(units, lessons):
    report = read('content/research/content-sample-review.json')
    require(report['editorialApproval'] is False and 'MODEL' in report['reviewKind'], 'Sample review is not human approval')
    for item in report['files'].values(): require(sha(ROOT / item['path']) == item['sha256'], f'Stale sample review: {item["path"]}')
    seen = set()
    for case in report['cases']:
        identity = (case['file'], case['topicOrLesson'], case['questionOrdinal'])
        require(identity not in seen, 'Duplicate sample review case'); seen.add(identity)
        unit = lessons[identity[1]] if identity[0] == 'python-course' else units[identity[1]]
        qs = unit['quiz']['questions'] if identity[0] == 'python-course' else unit['questions']
        require(key_of(qs[identity[2] - 1]) == case['expectedKey'] and case['reason'], f'Review key no longer matches {identity}')
        require(all(case[field] == 'PASS_MODEL_REVIEW' for field in ('answerCheck', 'ruKzSemanticEquivalence', 'explanationCheck')), f'Unresolved sampled review finding {identity}')
    require(len(seen) == report['sampleCount'] == 112, 'Review sample count')


def exam_configuration(entries):
    exam = read('content/research/exam-config.json')
    require(exam['officialSimulatorReady'] is False and exam['notReadyReason'], 'Starter bank must not claim complete official exam readiness')
    require((exam['totalQuestions'], exam['maxScore'], exam['durationMinutes']) == (120, 140, 240), 'NTC standard format mismatch')
    require([x['count'] for x in exam['profileBlueprint']] == [25, 5, 5, 5], 'Profile blueprint mismatch')
    require(sum(s['questionCount'] for s in exam['mandatorySubjects']) + 2 * sum(s['count'] for s in exam['profileBlueprint']) == 120, 'Question totals inconsistent')
    require(sum(s['maxScore'] for s in exam['mandatorySubjects']) + 2 * sum(s['count'] * s['pointsEach'] for s in exam['profileBlueprint']) == 140, 'Point totals inconsistent')
    pairs = set()
    for pair in exam['allowedProfilePairs']:
        names = pair['subjects']; require(len(names) == 2 and len(set(names)) == 2 and set(names) <= set(EXPECTED_SUBJECTS), 'Profile pair subject mismatch')
        identity = tuple(sorted(names)); require(identity not in pairs, 'Duplicated unordered pair'); pairs.add(identity)
        ev = pair['sourceEvidence']; source = entries[ev['sourceId']]
        require(ev['sha256'] == source['sha256'] and ev['url'] == source['url'] and ev['page'] >= 1 and ev['section'], 'Profile pair evidence mismatch')
    require(len(pairs) == 16, 'Profile language expansion changed')
    require({p['id'] for p in exam['scoringPolicies']} == {'ENT_MULTIPLE_2026_V1', 'ENT_MATCHING_2026_V1'}, 'Scoring policy identities')
    require(all(p['paragraph'] == '18(2)' and p['sourceUrl'].endswith('/V1700015173') for p in exam['scoringPolicies']), 'Scoring legal evidence lost')
    require(exam['deadline']['status'] == 'HISTORICAL_NOT_NEXT_EXAM_DEADLINE', 'Historical deadline misrepresented as future')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify-cache', action='store_true', help='Require downloaded research bytes; leave off in CI')
    args = parser.parse_args()
    entries, cache_count = sources(args.verify_cache)
    mapping = curriculum(entries)
    _, units, coverage, types = school_packs(mapping)
    lessons = python_course()
    review_report(units, lessons)
    exam_configuration(entries)
    print(json.dumps({'status': 'PASS', 'directions': len(coverage), 'officialVariantTopics': len(mapping),
                      'completedStarterLessons': len(units), 'entStarterQuestions': 450,
                      'questionTypes': dict(types), 'pythonLessons': len(lessons), 'pythonQuizQuestions': 30,
                      'executedPythonExamples': 12, 'executedBoundaryCases': 8, 'modelSampleChecks': 112,
                      'humanApproved': 0, 'fullEntBankReady': False, 'cachedNtcSourcesVerified': cache_count,
                      'networkRequests': 0}, ensure_ascii=False))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, TypeError, OSError, subprocess.TimeoutExpired) as exc:
        print(f'Content integrity FAILED: {exc}', file=sys.stderr)
        sys.exit(1)
