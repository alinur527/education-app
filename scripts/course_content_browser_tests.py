"""Exercise the published six-lesson Python starter through the student UI.

Local Docker only. Disposable accounts/groups are fixtures; published course content
is never edited. Quiz keys come from the checked-in authoring file, not student APIs.
The optional official PDF is verified when present; CI does not download it.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import uuid
from datetime import datetime, timezone
from urllib.parse import urlparse

from playwright.sync_api import expect, sync_playwright

expect.set_options(timeout=20000)

ROOT = Path(__file__).resolve().parents[1]
BASE = os.getenv('BROWSER_BASE_URL', 'http://127.0.0.1:8081').rstrip('/')
assert urlparse(BASE).hostname in ('localhost', '127.0.0.1'), 'Local fixtures only'
OUT = ROOT / 'test-results/expansion/python-course'
SHOTS = ROOT / 'docs/screenshots/expansion/python-course'
OUT.mkdir(parents=True, exist_ok=True)
SHOTS.mkdir(parents=True, exist_ok=True)
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')


def load(path):
    return json.loads((ROOT / path).read_text(encoding='utf-8'))


def normalized(text):
    return ' '.join(text.split())


def no_keys(value):
    if isinstance(value, dict):
        for key, child in value.items():
            assert key not in {'correctOptionId', 'correctOptionIds', 'correctPairs',
                               'answerKey', 'explanationRu', 'explanationKz', 'isCorrect'}, key
            no_keys(child)
    elif isinstance(value, list):
        for child in value:
            no_keys(child)


def main():
    source = load('content/starter/python-course.json')['course']
    plan = load('content/generated/release-plan.json')
    manifest = load('content/research/python-material.json')['material']
    course_key = 'course:' + source['externalKey']
    lessons = [(module, lesson) for module in source['modules'] for lesson in module['lessons']]
    assert len(source['modules']) == 2 and len(lessons) == 6
    assert sum(len(lesson['quiz']['questions']) for _, lesson in lessons) == 30
    report = {'startedAt': datetime.now(timezone.utc).isoformat(), 'status': 'RUNNING',
              'sourceSha256': hashlib.sha256((ROOT / 'content/starter/python-course.json').read_bytes()).hexdigest(),
              'flows': [], 'inspections': [], 'errors': [], 'quizQuestionCount': 0,
              'answerSource': 'checked-in authoring JSON',
              'studentMutations': 'enrollment, quiz answers, lesson completion and assignment submission via browser UI; account/group setup via isolated fixture API',
              'editorialApprovalClaim': False}
    errors = report['errors']
    axe = (ROOT / 'frontend/node_modules/axe-core/axe.min.js').read_text(encoding='utf-8')
    with sync_playwright() as p:
        api = p.request.new_context(base_url=BASE)
        accounts = {}
        for role in ('ADMIN', 'STUDENT'):
            account = {'email': f'python-course-{role.lower()}-{uuid.uuid4()}@example.org',
                       'password': 'Fixture-' + str(uuid.uuid4())}
            result = api.post('/api/auth/register', data={**account, 'firstName': 'Python', 'lastName': 'Browser', 'language': 'ru'})
            assert result.ok, f'Fixture registration HTTP {result.status}'
            account['id'] = str(uuid.UUID(result.json()['user']['id']))
            if role == 'ADMIN':
                sql = f"UPDATE users SET role='ADMIN' WHERE id='{account['id']}'::uuid"
                subprocess.run(['docker', 'compose', 'exec', '-T', 'postgres', 'sh', '-c',
                                'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "$1"',
                                'sh', sql], check=True, capture_output=True, cwd=ROOT)
            accounts[role] = account
        result = api.post('/api/auth/login', data={k: accounts['ADMIN'][k] for k in ('email', 'password')})
        assert result.ok, f'Fixture admin login HTTP {result.status}'
        headers = {'Authorization': 'Bearer ' + result.json()['token']}

        def admin(method, path, data=None):
            result = api.fetch('/api' + path, method=method, data=data, headers=headers)
            assert result.ok, f'Fixture API {method} {path}: HTTP {result.status}'
            return result.json()

        mappings = {}
        for page_number in range(100):
            rows = admin('GET', f"/cms/content-packs/mappings?namespace={plan['namespace']}&page={page_number}")
            mappings.update({row['externalKey']: row['contentId'] for row in rows})
            if len(rows) < 100:
                break
        assert course_key in mappings, 'Published Python starter is required; run content release first'
        course_id = mappings[course_key]
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(viewport={'width': 1440, 'height': 1000}, reduced_motion='reduce', accept_downloads=True)
        page = context.new_page()
        page.set_default_timeout(20000)
        page.on('pageerror', lambda error: errors.append('pageerror: ' + str(error)))
        page.on('console', lambda msg: errors.append('console: ' + re.sub(r'data:[^\s]+', 'data:[omitted]', msg.text)) if msg.type == 'error' else None)
        page.on('requestfailed', lambda request: errors.append(f'Failed {request.url}: {request.failure}') if request.failure != 'net::ERR_ABORTED' else None)
        page.on('response', lambda response: errors.append(f'HTTP {response.status}: {response.url}') if response.status >= 400 else None)
        page.on('dialog', lambda dialog: dialog.accept())

        def go(path):
            page.goto(BASE + path)
            page.wait_for_load_state('networkidle')

        def passed(name):
            assert not errors, errors
            report['flows'].append(name)
            print('PASS ' + name, flush=True)

        def inspect(name):
            page.wait_for_load_state('networkidle')
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'Overflow ' + name
            page.evaluate(axe)
            violations = page.evaluate("async()=> (await axe.run(document,{runOnly:['wcag2a','wcag2aa','wcag21aa']})).violations.map(v=>({id:v.id,nodes:v.nodes.map(n=>n.target)}))")
            assert not violations, (name, violations)
            page.screenshot(path=str(SHOTS / (name + '.png')))
            report['inspections'].append({'name': name, 'width': page.viewport_size['width'], 'axeViolations': 0})

        try:
            go('/login')
            expect(page.get_by_label('Электронная почта')).to_be_visible()
            # Inspect the rendered entry screen before interaction (webapp-testing).
            assert 'Электронная почта' in page.locator('body').inner_text()
            page.get_by_label('Электронная почта').fill(accounts['STUDENT']['email'])
            page.get_by_label('Пароль', exact=True).fill(accounts['STUDENT']['password'])
            page.get_by_role('button', name='Войти', exact=True).click()
            expect(page.locator('.study-hero')).to_be_visible()
            go('/courses/' + course_id)
            expect(page.get_by_role('heading', name=source['titleRu'], exact=True)).to_be_visible()
            expect(page.get_by_text('0 / 6 уроков завершено', exact=True)).to_be_visible()
            page.get_by_role('button', name='Записаться на курс', exact=True).click()
            for _, lesson in lessons:
                expect(page.locator('.module-list').get_by_role('link', name=lesson['titleRu'] + ' Открыть урок', exact=True)).to_be_visible()
            passed('self-enrollment: six authored lessons available')

            group_id = admin('POST', '/teacher/groups', {'name': 'Python browser ' + uuid.uuid4().hex[:8], 'courseId': course_id})['id']
            admin('POST', f'/teacher/groups/{group_id}/members', {'email': accounts['STUDENT']['email']})
            first_module, first_lesson = lessons[0]
            first_key = course_key + ':' + first_module['externalKey'] + ':' + first_lesson['externalKey']
            assignment_id = mappings[first_key + ':assignment']
            admin('POST', f'/teacher/groups/{group_id}/assignments', {'assignmentId': assignment_id})

            files = page.locator('.material-list li').filter(has_text=manifest['originalFileName'])
            if files.count():
                with page.expect_download() as download:
                    files.get_by_role('button', name='Скачать', exact=True).click()
                content = Path(download.value.path()).read_bytes()
                assert hashlib.sha256(content).hexdigest() == manifest['sha256']
                assert len(content) == manifest['size']
                report['officialPdf'] = {'status': 'DOWNLOADED_HASH_VERIFIED', 'sha256': manifest['sha256'], 'bytes': len(content)}
                passed('published official Python PDF: protected download and exact SHA256')
            else:
                report['officialPdf'] = {'status': 'ABSENT_NOT_VERIFIED', 'reason': 'Optional source PDF is not installed. Protected PDF flow is covered separately by phase2_browser_tests.py.'}
                print('OPTIONAL official Python PDF absent; no download verification claimed', flush=True)

            for number, (module, lesson) in enumerate(lessons, 1):
                key = course_key + ':' + module['externalKey'] + ':' + lesson['externalKey']
                lesson_id = mappings[key]
                go('/lessons/' + lesson_id)
                for lang, button in (('Ru', 'РУС'), ('Kz', 'ҚАЗ')):
                    page.get_by_role('button', name=button, exact=True).first.click()
                    expect(page.locator('.learning-content').get_by_role('heading', name=lesson['title' + lang], exact=True)).to_be_visible()
                    actual = page.locator('.learning-content > .rich-text').first.inner_text()
                    assert normalized(actual) == normalized(lesson['theory' + lang]), f'Incomplete {lang} theory: {key}'
                    code_blocks = page.locator('.learning-content pre code').all_text_contents()
                    assert len(lesson['codeExamples']) == 2
                    for example in lesson['codeExamples']:
                        assert example['code'] in code_blocks, f'Missing original code: {key}/{lang}'
                        assert example['expectedStdout'] in code_blocks, f'Missing expected output: {key}/{lang}'
                page.get_by_role('button', name='РУС', exact=True).first.click()
                if number == 1:
                    page.locator('.learning-content pre').first.scroll_into_view_if_needed()
                    inspect('python-lesson-original-code')
                passed(f'lesson {number}: complete RU/KZ theory and both code examples')
                page.locator('.activity-link').filter(has_text='Проверка: ' + lesson['titleRu']).click()
                with page.expect_response(lambda r: r.request.method == 'POST' and '/quizzes/' in r.url and r.url.endswith('/attempts')) as started:
                    page.get_by_role('button', name='Начать тест урока', exact=True).click()
                attempt = started.value.json()
                no_keys(attempt['questions'])
                authored = lesson['quiz']['questions']
                assert len(attempt['questions']) == len(authored) == 5
                expect(page.locator('fieldset.question-card')).to_have_count(5)
                expect(page.get_by_text('Правильный ответ', exact=False)).to_have_count(0)
                for index, question in enumerate(authored):
                    actual = attempt['questions'][index]
                    assert actual['titleRu'] == question['titleRu']
                    assert actual['options'] == question['options']
                    correct_index = next(i for i, option in enumerate(question['options']) if option['id'] == question['correctOptionId'])
                    page.locator('fieldset.question-card').nth(index).get_by_role('radio').nth(correct_index).check()
                page.get_by_role('button', name='Завершить тест', exact=True).click()
                expect(page.get_by_role('heading', name='Результат теста', exact=True)).to_be_visible()
                expect(page.locator('.quiz-score')).to_have_text('100%')
                report['quizQuestionCount'] += 5
                if number == 6:
                    inspect('python-quiz-result')
                passed(f'quiz {number}: five authored answers, score 100%, keys hidden before finish')
                go('/lessons/' + lesson_id)
                page.get_by_role('button', name='Отметить урок завершённым', exact=True).click()
                expect(page.get_by_role('button', name='Урок завершён', exact=True)).to_be_disabled()

            go('/assignments/' + assignment_id)
            expect(page.get_by_role('heading', name=first_lesson['assignment']['titleRu'], exact=True)).to_be_visible()
            assert first_lesson['assignment']['descriptionRu'] in page.locator('.learning-content').inner_text()
            sample_output = first_lesson['assignment']['sampleRuns'][0]['expectedStdout']
            assert page.locator('.learning-content pre code').inner_text().strip() == sample_output.strip()
            inspect('python-assignment-formatted')
            solution = 'name = "Мира"\nsubject = "Python"\nminutes = 15\nprint(name)\nprint(subject)\nprint(minutes)\nminutes = minutes + 10\nprint(minutes)\n'
            execution = subprocess.run([sys.executable, '-I', '-X', 'utf8', '-c', solution], capture_output=True, text=True, encoding='utf-8', check=True)
            assert execution.stdout == first_lesson['assignment']['sampleRuns'][0]['expectedStdout']
            answer = solution + '\nФактический вывод:\n' + execution.stdout + '\nminutes — имя переменной с числом; "minutes" — строка с буквами.'
            page.get_by_label('Ваш ответ', exact=True).fill(answer)
            with page.expect_response(lambda r: r.request.method == 'POST' and r.url.endswith('/submit')) as submitted:
                page.get_by_role('button', name='Отправить ответ', exact=True).click()
            assert submitted.value.ok
            expect(page.get_by_text('Новая отправка создаст следующую версию.', exact=False)).to_be_visible()
            page.reload()
            page.wait_for_load_state('networkidle')
            expect(page.get_by_role('textbox', name='Ваш ответ', exact=True)).to_have_value(answer)
            passed('authored Python assignment: valid code/output submitted and persisted, no automatic grade claimed')
            go('/courses/' + course_id)
            expect(page.get_by_text('6 / 6 уроков завершено', exact=True)).to_be_visible()
            progress = page.get_by_role('progressbar', name='Прогресс курса')
            expect(progress).to_have_attribute('value', '6')
            expect(progress).to_have_attribute('max', '6')
            assert page.locator('.module-list').get_by_text('Завершено', exact=True).count() == 6
            inspect('python-complete-course')
            report['courseProgress'] = {'completedLessons': 6, 'totalLessons': 6, 'percent': 100}
            passed('real course progress: six explicitly completed lessons / six, 100%')
            report['status'] = 'PASS'
        except Exception as error:
            report['status'] = 'FAIL'
            report['failure'] = str(error)
            page.screenshot(path=str(OUT / 'failure.png'), full_page=True)
            raise
        finally:
            report['finishedAt'] = datetime.now(timezone.utc).isoformat()
            (OUT / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
            context.close()
            browser.close()
            api.dispose()
    print(f"PASS Python starter: {len(report['flows'])} flows, {report['quizQuestionCount']} quiz answers, {len(errors)} browser errors")


if __name__ == '__main__':
    main()
