"""Real PostgreSQL-backed student journey and explicit failure scenarios.

Run with frontend :5173 and backend :8080. Creates a unique student account.
Screenshots in docs/screenshots are intentional documentation artifacts.
"""
import json
import os
import sys
import uuid
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')
BASE = os.environ.get('BROWSER_BASE_URL', 'http://127.0.0.1:5173')
OUT = Path('test-results')
SHOTS = Path('docs/screenshots')
OUT.mkdir(exist_ok=True)
SHOTS.mkdir(parents=True, exist_ok=True)
email = f'browser-{uuid.uuid4()}@example.org'
password = f'Browser-{uuid.uuid4()}'
errors = []
expected_failure = False
checks = []

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    context = browser.new_context(viewport={'width': 1440, 'height': 1000}, reduced_motion='reduce')
    page = context.new_page()
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.on('console', lambda m: errors.append(m.text) if m.type == 'error' and not expected_failure else None)
    page.on('requestfailed', lambda r: errors.append(f'Failed: {r.method} {r.url}') if not expected_failure and r.failure != 'net::ERR_ABORTED' else None)
    page.on('response', lambda r: errors.append(f'HTTP {r.status}: {r.url}') if r.status >= 400 and not expected_failure else None)
    page.on('dialog', lambda d: d.accept())

    def inspect(name, screenshot=False):
        if screenshot:
            ready = '.subject-card' if name.startswith('dashboard') or name == 'subjects' else {
                'topics': '.topic-row', 'theory': '.theory-content section',
                'test-desktop': 'input[type=radio]', 'test-mobile': 'input[type=radio]',
                'results': '.review-item', 'statistics': '.chart', 'login': 'input[type=password]',
            }.get(name)
            if ready:
                expect(page.locator(ready).first).to_be_visible()
        page.wait_for_load_state('networkidle')
        OUT.joinpath(f'{name}.txt').write_text(page.locator('body').inner_text(), encoding='utf-8')
        assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth'), f'Overflow: {name}'
        if screenshot:
            page.screenshot(path=str(SHOTS / f'{name}.png'), full_page=not any(v in name for v in ['mobile', 'tablet', 'small']))
            page.evaluate(Path('frontend/node_modules/axe-core/axe.min.js').read_text(encoding='utf-8'))
            violations = page.evaluate("async () => (await axe.run(document, {runOnly: ['wcag2a', 'wcag2aa', 'wcag21aa']})).violations.map(v => ({id:v.id, impact:v.impact, nodes:v.nodes.map(n => n.target)}))")
            assert not violations, f'Accessibility {name}: {violations}'
        checks.append(name)
        print(f'PASS {name}', flush=True)

    try:
        page.goto(BASE)
        inspect('login', True)
        page.get_by_role('button', name='ҚАЗ', exact=True).click()
        expect(page.get_by_label('Құпиясөз')).to_be_visible()
        page.reload()
        expect(page.get_by_label('Құпиясөз')).to_be_visible()
        page.get_by_role('link', name='Тіркелу', exact=True).click()
        inspect('registration-kz')
        page.get_by_label('Аты', exact=True).fill('Айдана')
        page.get_by_label('Тегі', exact=True).fill('Тест')
        page.get_by_label('Электрондық пошта', exact=True).fill(email)
        page.get_by_label('Құпиясөз', exact=True).fill(password)
        page.get_by_role('button', name='Тіркелу', exact=True).click()
        expect(page.get_by_role('heading', name='Бүгін тағы бір тақырыпты түсінеміз.')).to_be_visible()
        inspect('dashboard-empty-kz')
        page.get_by_role('button', name='РУС', exact=True).click()
        expect(page.get_by_role('heading', name='Сегодня — ещё одна понятная тема.')).to_be_visible()
        page.reload()
        expect(page.get_by_role('heading', name='Сегодня — ещё одна понятная тема.')).to_be_visible()
        inspect('protected-refresh')
        page.get_by_role('button', name='Выйти', exact=True).click()
        expect(page.get_by_label('Пароль', exact=True)).to_be_visible()
        page.get_by_label('Электронная почта').fill(email)
        page.get_by_label('Пароль', exact=True).fill(password)
        page.get_by_role('button', name='Войти', exact=True).click()
        expect(page.get_by_role('heading', name='Сегодня — ещё одна понятная тема.')).to_be_visible()
        inspect('login-restored')
        page.get_by_role('link', name='Выбрать предмет', exact=True).first.click()
        inspect('subjects', True)
        page.locator('.subject-card').filter(has=page.get_by_role('heading', name='Математика', exact=True)).click()
        inspect('topics', True)
        page.locator('.topic-row').filter(has=page.get_by_role('heading', name='Производная', exact=True)).click()
        inspect('theory', True)
        expect(page.get_by_role('heading', name='Как найти производную: разбор примера')).to_be_visible()
        page.get_by_role('link', name='Как найти производную: разбор примера').click()
        page.get_by_role('button', name='Начать тест', exact=True).click()
        inspect('test-desktop', True)
        expect(page.get_by_role('button', name='Ответить и продолжить')).to_be_disabled()
        # Native keyboard radio navigation, followed by one persisted answer.
        page.get_by_role('radio').first.focus()
        page.keyboard.press('Space')
        page.get_by_role('button', name='Ответить и продолжить').click()
        inspect('test-next-question')
        page.get_by_role('button', name='Выйти из теста', exact=True).click()
        expect(page.get_by_role('alertdialog')).to_be_visible()
        page.get_by_role('button', name='Остаться').click()
        page.get_by_role('button', name='Выйти из теста', exact=True).click()
        page.get_by_role('alertdialog').get_by_role('button', name='Выйти из теста', exact=True).click()
        expect(page.get_by_role('heading', name='Незавершённые тесты')).to_be_visible()
        page.get_by_role('link').filter(has_text='Сохранено ответов: 1/').click()
        inspect('resume-test')
        page.reload()
        inspect('test-refresh')
        while '/tests/' in page.url:
            page.wait_for_load_state('networkidle')
            options = page.get_by_role('radio')
            if options.count() and options.first.is_enabled():
                options.first.check()
                last = page.locator('.answer-submit').inner_text().strip() == 'Ответить и завершить'
                page.locator('.answer-submit').click()
                if last:
                    page.wait_for_url('**/results/**')
                    break
            elif page.get_by_role('button', name='Следующий вопрос').count():
                page.get_by_role('button', name='Следующий вопрос').click()
            else:
                page.get_by_role('button', name='Завершить тест', exact=True).click()
            page.wait_for_load_state('networkidle')
        expect(page.get_by_role('heading', name='Результаты', exact=True)).to_be_visible()
        inspect('results', True)
        results_url = page.url
        page.get_by_role('link', name='Моя статистика', exact=True).click()
        inspect('statistics', True)
        expect(page.locator('.metric').first).to_contain_text('1')
        # Direct navigation and full Kazakh result/review translation.
        page.goto(results_url)
        page.get_by_role('button', name='ҚАЗ', exact=True).click()
        expect(page.get_by_role('heading', name='Жауаптарды талдау')).to_be_visible()
        inspect('results-kz')
        page.goto(BASE + '/settings')
        expect(page.get_by_role('heading', name='Баптаулар')).to_be_visible()
        page.get_by_role('button', name='РУС', exact=True).first.click()
        inspect('settings')
        page.goto(BASE)
        inspect('dashboard-desktop', True)
        for width, height, name in [(768, 1024, 'dashboard-tablet'), (390, 844, 'dashboard-mobile'), (320, 740, 'dashboard-small')]:
            page.set_viewport_size({'width': width, 'height': height})
            inspect(name, True)
            expect(page.get_by_role('heading', name='Ваше обучение')).to_be_visible()
        page.goto(BASE + '/subjects')
        page.locator('.subject-card').filter(has=page.get_by_role('heading', name='Математика', exact=True)).click()
        page.locator('.topic-row').filter(has=page.get_by_role('heading', name='Производная', exact=True)).click()
        page.get_by_role('button', name='Начать тест').click()
        page.set_viewport_size({'width': 390, 'height': 844})
        inspect('test-mobile', True)
        page.get_by_role('button', name='Завершить тест', exact=True).click()
        expect(page.get_by_role('alertdialog')).to_be_visible()
        page.get_by_role('button', name='Завершить попытку').click()
        inspect('early-finish-mobile')
        expect(page.locator('.result-score')).to_contain_text('0%')
        expect(page.locator('.review-status').first).to_have_text('Без ответа')
        # Failure injection tests are isolated from normal console/network assertions.
        page.set_viewport_size({'width': 1440, 'height': 1000})
        expected_failure = True
        page.route('**/api/subjects', lambda route: route.fulfill(status=500, json={'code': 'SERVER_ERROR'}))
        page.goto(BASE + '/subjects')
        expect(page.get_by_role('alert')).to_contain_text('Сервис временно недоступен')
        inspect('http-error')
        page.unroute('**/api/subjects')
        page.get_by_role('button', name='Попробовать снова').click()
        expect(page.locator('.subject-card').first).to_be_visible()
        page.route('**/api/subjects', lambda route: route.fulfill(json={'malformed': True}))
        page.reload()
        expect(page.get_by_role('alert')).to_contain_text('Не удалось прочитать ответ сервера')
        inspect('malformed-api')
        page.unroute('**/api/subjects')
        page.route('**/api/subjects', lambda route: route.fulfill(json=[]))
        page.reload()
        expect(page.get_by_role('heading', name='Предметы скоро появятся')).to_be_visible()
        inspect('empty-subjects')
        page.unroute('**/api/subjects')
        page.route('**/api/subjects', lambda route: route.abort('internetdisconnected'))
        page.reload()
        expect(page.get_by_role('alert')).to_contain_text('Не удалось связаться с сервером')
        inspect('offline-api')
        page.unroute('**/api/subjects')
        page.goto(BASE + '/unknown-route')
        expect(page.get_by_role('heading', name='Страница не найдена')).to_be_visible()
        inspect('not-found')
        page.evaluate("sessionStorage.setItem('education.session', 'expired-token')")
        page.goto(BASE + '/statistics')
        expect(page.get_by_label('Пароль', exact=True)).to_be_visible()
        expect(page.get_by_role('status')).to_contain_text('Сессия завершилась')
        # Redirect commits before the navigation's network-idle lifecycle settles.
        # A direct login refresh must also be usable, with the expired token removed.
        assert page.evaluate("sessionStorage.getItem('education.session')") is None
        page.goto(BASE + '/login')
        expect(page.get_by_label('Пароль', exact=True)).to_be_visible()
        inspect('session-expiry')
        expected_failure = False
        assert not errors, '\n'.join(errors)
        report = {'passed': checks, 'consoleErrors': errors, 'browser': 'Chromium', 'viewports': [1440, 768, 390, 320]}
        OUT.joinpath('browser-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print(f'PASS all {len(checks)} browser checkpoints; no unexpected console/network errors')
    except Exception:
        page.screenshot(path=str(OUT / 'failure.png'), full_page=True)
        OUT.joinpath('failure.txt').write_text(page.locator('body').inner_text(), encoding='utf-8')
        raise
    finally:
        browser.close()
