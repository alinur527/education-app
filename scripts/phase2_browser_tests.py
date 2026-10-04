"""Phase 2 UI journeys against local Docker; creates isolated fixture accounts/content.

Only account roles are provisioned through Docker SQL. All curriculum, course,
group, upload, import, enrollment and grading operations use the actual web UI.
Run from the repository root after docker compose --profile app up --build --wait.
"""
import json
import os
import subprocess
import sys
import uuid
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')
BASE = os.environ.get('BROWSER_BASE_URL', 'http://127.0.0.1:8081')
assert urlparse(BASE).hostname in ('localhost', '127.0.0.1'), 'Fixture provisioning is local only'
OUT = Path('test-results/phase2')
SHOTS = Path('docs/screenshots/phase2')
OUT.mkdir(parents=True, exist_ok=True)
SHOTS.mkdir(parents=True, exist_ok=True)
run = uuid.uuid4().hex[:8]
checks, errors = [], []
expected_failure = False
accounts, ids = {}, {}
created_ids = []
pdf = b'%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n%%EOF\n'

with sync_playwright() as p:
    api = p.request.new_context(base_url=BASE)
    for role in ('ADMIN', 'TEACHER', 'CONTENT_EDITOR', 'STUDENT'):
        email = f'phase2-{role.lower()}-{uuid.uuid4()}@example.org'
        password = f'Phase2-{uuid.uuid4()}'
        result = api.post('/api/auth/register', data={'email': email, 'password': password, 'firstName': 'Проверка', 'lastName': role, 'language': 'ru'})
        assert result.ok, result.status
        uid = str(uuid.UUID(result.json()['user']['id']))
        if role != 'STUDENT':
            sql = "UPDATE users SET role='%s' WHERE id='%s'::uuid" % (role, uid)
            subprocess.run(['docker', 'compose', 'exec', '-T', 'postgres', 'sh', '-c', 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "$1"', 'sh', sql], check=True, capture_output=True)
        accounts[role] = {'email': email, 'password': password}
    browser = p.chromium.launch(headless=True)
    context = browser.new_context(viewport={'width': 1440, 'height': 1000}, reduced_motion='reduce', accept_downloads=True)
    page = context.new_page()
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.on('console', lambda m: errors.append(m.text) if m.type == 'error' and not expected_failure else None)
    page.on('response', lambda r: errors.append(f'HTTP {r.status}: {r.url}') if r.status >= 400 and not expected_failure else None)
    page.on('requestfailed', lambda r: errors.append(f'Failed: {r.url}') if not expected_failure and r.failure != 'net::ERR_ABORTED' else None)
    page.on('dialog', lambda d: d.accept())

    def inspect(name, screenshot=False):
        page.wait_for_load_state('networkidle')
        OUT.joinpath(name + '.txt').write_text(page.locator('main').inner_text(), encoding='utf-8')
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), f'Overflow {name}'
        if screenshot:
            page.screenshot(path=str(SHOTS / (name + '.png')), full_page=False)
            page.evaluate(Path('frontend/node_modules/axe-core/axe.min.js').read_text(encoding='utf-8'))
            violations = page.evaluate("async () => (await axe.run(document,{runOnly:['wcag2a','wcag2aa','wcag21aa']})).violations.map(v=>({id:v.id,impact:v.impact,nodes:v.nodes.map(n=>n.target)}))")
            assert not violations, f'Accessibility {name}: {violations}'
        checks.append(name)
        print('PASS ' + name, flush=True)

    def go(path, heading):
        page.goto(BASE + path)
        expect(page.get_by_role('heading', name=heading, exact=True).first).to_be_visible()
        page.wait_for_load_state('networkidle')

    def login(role):
        page.goto(BASE + '/login')
        page.evaluate('sessionStorage.clear()')
        page.reload()
        expect(page.get_by_label('Пароль', exact=True)).to_be_visible()
        page.wait_for_load_state('networkidle')
        page.get_by_label('Электронная почта').fill(accounts[role]['email'])
        page.get_by_label('Пароль', exact=True).fill(accounts[role]['password'])
        page.get_by_role('button', name='Войти', exact=True).click()
        expect(page.get_by_role('heading', name='Сегодня — ещё одна понятная тема.')).to_be_visible()
        inspect('login-' + role)

    def publish():
        page.get_by_role('button', name='На проверку', exact=True).click()
        expect(page.get_by_role('button', name='Опубликовать', exact=True)).to_be_enabled()
        page.get_by_role('button', name='Опубликовать', exact=True).click()
        expect(page.locator('.status-published')).to_be_visible()

    def new_content(kind, title, parent=None, parent_label=None, finish=True):
        go('/workspace/content/new?kind=' + kind, 'Новый материал')
        inspect('form-' + kind)
        if parent:
            page.get_by_label('Найти родительский материал').fill(parent[1])
            expect(page.get_by_role('combobox', name=parent_label, exact=True).locator('option', has_text=parent[1])).to_have_count(1)
            page.get_by_role('combobox', name=parent_label, exact=True).select_option(parent[0])
        page.get_by_label('Название RU', exact=True).fill(title)
        page.get_by_label('Название KZ', exact=True).fill(title + ' KZ')
        if kind == 'COURSE':
            page.get_by_label('Видимость курса').select_option('PUBLIC')
            page.get_by_label('Разрешить самостоятельную запись').check()
        if kind in ('THEORY', 'LESSON', 'ASSIGNMENT'):
            page.get_by_role('button', name='+ Текст', exact=True).click()
            page.get_by_label('RU', exact=True).fill('Разберите пример: 2 + 2 = 4. Объясните ход решения.')
            page.get_by_label('KZ', exact=True).fill('Мысалды талдаңыз: 2 + 2 = 4. Шешу жолын түсіндіріңіз.')
        if kind in ('QUESTION', 'QUIZ'):
            if kind == 'QUIZ':
                page.get_by_label('Вопрос RU', exact=True).fill('Сколько будет 2 + 2?')
                page.get_by_label('Вопрос KZ', exact=True).fill('2 + 2 қанша?')
            for option, answer in [('A', '4'), ('B', '5')]:
                for lang in ('RU', 'KZ'):
                    page.get_by_label(f'Ответ {option} {lang}', exact=True).fill(answer)
            page.get_by_label('Объяснение RU').fill('2 + 2 = 4')
            page.get_by_label('Объяснение KZ').fill('2 + 2 = 4')
        if kind == 'ASSIGNMENT':
            page.get_by_label('Максимальный балл').fill('10')
            page.get_by_label('Срок сдачи').fill('2027-05-20T18:00')
        page.get_by_role('button', name='Сохранить черновик', exact=True).click()
        expect(page.get_by_role('heading', name='Редактор материала', exact=True)).to_be_visible()
        expect(page.get_by_role('button', name='На проверку', exact=True)).to_be_enabled()
        cid = page.url.rsplit('/', 1)[1]
        assert str(uuid.UUID(cid)) == cid
        created_ids.append(cid)
        if finish:
            publish()
        return cid, title

    try:
        login('ADMIN')
        subject = new_content('SUBJECT', 'Практикум ' + run)
        topic = new_content('TOPIC', 'Сложение ' + run, subject, 'Предмет')
        theory = new_content('THEORY', 'Разбор сложения ' + run, topic, 'Тема')
        question = new_content('QUESTION', 'Сколько будет 2 + 2? ' + run, topic, 'Тема')
        inspect('ent-published', True)
        login('TEACHER')
        course = new_content('COURSE', 'Логика для начинающих ' + run)
        page.get_by_label('Почта ученика', exact=True).fill(accounts['STUDENT']['email'])
        for enrollment in ('ACTIVE','CANCELLED'):
            page.get_by_role('combobox', name='Доступ к курсу', exact=True).select_option(enrollment)
            with page.expect_response(lambda r:r.request.method=='POST' and '/enrollments' in r.url) as enrolled:
                page.get_by_role('button', name='Применить зачисление', exact=True).click()
            assert enrolled.value.ok
        inspect('manual-enrollment')
        module = new_content('MODULE', 'Первые шаги ' + run, course, 'Курс')
        lesson = new_content('LESSON', 'Решаем по шагам ' + run, module, 'Модуль', False)
        inspect('lesson-draft', True)
        page.get_by_label('Выбрать файл', exact=False).set_input_files({'name': 'lesson.pdf', 'mimeType': 'application/pdf', 'buffer': pdf})
        page.get_by_label('Название файла RU').fill('Конспект урока')
        page.get_by_label('Название файла KZ').fill('Сабақ конспектісі')
        page.get_by_role('button', name='Загрузить файл', exact=True).click()
        expect(page.get_by_role('button', name='Скачать', exact=True)).to_be_visible()
        publish()
        assignment = new_content('ASSIGNMENT', 'Объясните решение ' + run, lesson, 'Урок')
        quiz = new_content('QUIZ', 'Проверьте себя ' + run, lesson, 'Урок')
        go('/workspace/groups', 'Группы и ученики')
        inspect('group-form')
        page.get_by_label('Название группы').fill('Группа ' + run)
        page.get_by_role('combobox', name='Курс', exact=True).select_option(course[0])
        page.get_by_role('button', name='Создать группу', exact=True).click()
        expect(page.get_by_role('heading', name='Группа ' + run, exact=True)).to_be_visible()
        group_id = page.url.rsplit('/', 1)[1]
        inspect('group-empty')
        page.get_by_label('Почта зарегистрированного ученика').fill(accounts['STUDENT']['email'])
        page.get_by_role('button', name='Добавить ученика', exact=True).click()
        expect(page.get_by_role('cell', name=accounts['STUDENT']['email'], exact=False)).to_be_visible()
        page.get_by_label('Задание этого курса').select_option(assignment[0])
        page.get_by_role('button', name='Назначить группе', exact=True).click()
        expect(page.get_by_role('link', name=assignment[1], exact=True)).to_be_visible()
        inspect('teacher-group', True)
        login('STUDENT')
        go('/topics/' + topic[0], topic[1])
        inspect('student-theory', True)
        page.get_by_role('button', name='Отметить прочитанным', exact=True).click()
        page.get_by_role('button', name='Начать тест', exact=True).click()
        expect(page.get_by_role('radio').first).to_be_visible()
        inspect('student-practice')
        page.get_by_role('radio').nth(1).check()
        page.get_by_role('button', name='Ответить и завершить', exact=True).click()
        expect(page.get_by_role('heading', name='Ещё один шаг вперёд.', exact=True)).to_be_visible()
        inspect('student-ent-result', True)
        go('/learning/errors', 'Работа над ошибками')
        expect(page.get_by_role('heading', name=topic[1], exact=True)).to_be_visible()
        page.get_by_role('button', name='Повторить ошибки', exact=True).click()
        expect(page.get_by_role('radio').first).to_be_visible()
        page.get_by_role('radio').first.check()
        page.get_by_role('button', name='Ответить и завершить', exact=True).click()
        expect(page.get_by_role('heading', name='Ещё один шаг вперёд.', exact=True)).to_be_visible()
        go('/learning/errors', 'Работа над ошибками')
        expect(page.get_by_text('Вопросов на повторение нет', exact=True)).to_be_visible()
        go('/learning', 'Освоение тем')
        inspect('student-mastery', True)
        go('/courses/' + course[0], course[1])
        page.get_by_role('link', name=lesson[1], exact=False).click()
        expect(page.get_by_role('heading', name=lesson[1], exact=True)).to_be_visible()
        inspect('student-lesson', True)
        with page.expect_download() as download:
            page.get_by_role('button', name='Скачать', exact=True).click()
        assert Path(download.value.path()).read_bytes() == pdf
        page.get_by_role('button', name='Отметить урок завершённым', exact=True).click()
        expect(page.get_by_role('button', name='Урок завершён', exact=True)).to_be_disabled()
        page.get_by_role('link', name=assignment[1], exact=False).click()
        expect(page.get_by_label('Ваш ответ')).to_be_visible()
        page.get_by_label('Ваш ответ').fill('Складываем две пары и получаем четыре.')
        page.get_by_role('button', name='Отправить ответ', exact=True).click()
        expect(page.get_by_text('Повторная отправка заменит ответ и сбросит прежнюю оценку.', exact=True)).to_be_visible()
        inspect('student-assignment', True)
        go('/quizzes/' + quiz[0], 'Проверка знаний')
        page.get_by_role('button', name='Начать тест урока').click()
        expect(page.get_by_role('radio').first).to_be_visible()
        page.get_by_role('radio').first.check()
        page.get_by_role('button', name='Завершить тест', exact=True).click()
        expect(page.get_by_role('heading', name='Результат теста', exact=True)).to_be_visible()
        expect(page.locator('.quiz-score')).to_have_text('100%')
        inspect('student-quiz', True)
        go('/courses/' + course[0], course[1])
        expect(page.get_by_text('1 / 1 уроков завершено', exact=True)).to_be_visible()
        for width in (1440, 1024, 768, 390, 320):
            page.set_viewport_size({'width': width, 'height': 900})
            inspect(f'course-{width}', True)
        page.set_viewport_size({'width':1440,'height':1000})
        login('TEACHER')
        go('/workspace/assignments/' + assignment[0], 'Ответы учеников')
        inspect('grade-form')
        page.get_by_label('Балл', exact=True).fill('9')
        page.get_by_label('Комментарий учителя').fill('Решение объяснено понятно.')
        page.get_by_role('button', name='Сохранить оценку').click()
        expect(page.get_by_role('status')).to_have_text('Сохранено')
        go('/workspace/groups/' + group_id, 'Группа ' + run)
        expect(page.get_by_text('100%', exact=True)).to_be_visible()
        inspect('teacher-progress', True)
        for width in (1024, 768, 390, 320):
            page.set_viewport_size({'width':width,'height':900})
            inspect(f'group-{width}', True)
        page.set_viewport_size({'width':1440,'height':1000})
        login('CONTENT_EDITOR')
        go('/workspace/content', 'Учебные материалы')
        expect(page.get_by_role('link', name='Пользователи', exact=True)).to_have_count(0)
        inspect('editor-role', True)
        login('ADMIN')
        go('/workspace/users', 'Пользователи')
        page.get_by_label('Поиск по имени или почте').fill(accounts['CONTENT_EDITOR']['email'])
        row=page.locator('.account-row').filter(has_text=accounts['CONTENT_EDITOR']['email'])
        expect(row).to_be_visible()
        inspect('admin-users', True)
        for role in ('TEACHER','CONTENT_EDITOR'):
            row.get_by_role('combobox').select_option(role)
            with page.expect_response(lambda r:r.request.method=='PATCH' and '/admin/users/' in r.url) as updated:
                row.get_by_role('button', name='Сохранить доступ').click()
            assert updated.value.ok
            expect(row.get_by_role('button', name='Сохранить доступ')).to_be_disabled()
        inspect('admin-role-change')
        go('/workspace/imports', 'Массовый импорт')
        inspect('import-form')
        for ext, content in [('json', json.dumps([{'key':'s','kind':'SUBJECT','payload':{'titleRu':'Импорт JSON '+run,'titleKz':'JSON пән '+run}}])),('csv',f'key,kind,titleRu,titleKz\ns,SUBJECT,Импорт CSV {run},CSV пән {run}\n')]:
            page.get_by_label('Файл импорта', exact=False).set_input_files({'name':'sample.'+ext,'mimeType':'application/octet-stream','buffer':content.encode()})
            page.get_by_role('button', name='Проверить и показать preview').click()
            expect(page.get_by_role('button', name='Подтвердить импорт')).to_be_visible()
            inspect('import-preview-'+ext, True)
            page.get_by_role('button', name='Подтвердить импорт').click()
            expect(page.get_by_text('Импорт завершён. Созданы черновики.', exact=True)).to_be_visible()
            link=page.locator('.table-region').get_by_role('link').first
            href=link.get_attribute('href')
            created_ids.append(href.rsplit('/',1)[1])
            link.click()
            expect(page.get_by_role('heading', name='Редактор материала')).to_be_visible()
            publish()
            inspect('import-published-'+ext)
            go('/workspace/imports', 'Массовый импорт')
        bad=[{'key':'bad','kind':'QUESTION','parentId':topic[0],'payload':{'titleRu':'Bad','titleKz':'Bad','options':[{'id':'A','textRu':'A','textKz':'A'},{'id':'B','textRu':'B','textKz':'B'}],'correctOptionId':'D'}}]
        page.get_by_label('Файл импорта', exact=False).set_input_files({'name':'invalid.json','mimeType':'application/json','buffer':json.dumps(bad).encode()})
        page.get_by_role('button', name='Проверить и показать preview').click()
        expect(page.locator('.import-errors')).to_contain_text('correctOptionId')
        expect(page.get_by_role('button', name='Подтвердить импорт')).to_have_count(0)
        inspect('import-invalid', True)
        page.get_by_label('Файл импорта', exact=False).set_input_files({'name':'malformed-rows.json','mimeType':'application/json','buffer':b'[42,{"kind":"constructor","payload":{"titleRu":123}}]'})
        page.get_by_role('button', name='Проверить и показать preview').click()
        expect(page.locator('.import-errors')).to_contain_text('Строка 2')
        inspect('import-invalid-row-types', True)

        go('/workspace/content/' + lesson[0], 'Редактор материала')
        for width in (1440, 1024, 768, 390, 320):
            page.set_viewport_size({'width':width,'height':900})
            inspect(f'cms-{width}', True)
        page.set_viewport_size({'width':1440,'height':1000})
        page.get_by_label('Название RU', exact=True).fill('Несохранённая правка')
        page.get_by_role('link', name='Материалы', exact=True).click()
        expect(page.get_by_role('alertdialog')).to_be_visible()
        inspect('dirty-draft-dialog', True)
        page.get_by_role('button', name='Остаться', exact=True).click()
        expect(page.get_by_label('Название RU', exact=True)).to_have_value('Несохранённая правка')
        expected_failure = True
        page.route('**/api/cms/content/' + lesson[0], lambda route: route.fulfill(status=409,json={'code':'REVISION_CONFLICT'}) if route.request.method=='PUT' else route.continue_())
        page.get_by_role('button', name='Сохранить черновик', exact=True).click()
        expect(page.get_by_role('alert')).to_contain_text('Ваш текст сохранён')
        expect(page.get_by_label('Название RU', exact=True)).to_have_value('Несохранённая правка')
        inspect('draft-conflict')
        page.unroute('**/api/cms/content/' + lesson[0])
        page.route('**/api/cms/content/' + lesson[0], lambda route: route.abort('failed') if route.request.method=='PUT' else route.continue_())
        page.get_by_role('button', name='Сохранить черновик', exact=True).click()
        expect(page.get_by_role('alert')).to_be_visible()
        expect(page.get_by_label('Название RU', exact=True)).to_have_value('Несохранённая правка')
        inspect('draft-network-failure')

        page.unroute('**/api/cms/content/' + lesson[0])
        expected_failure=False
        ids.update(subject=subject[0],topic=topic[0],course=course[0],lesson=lesson[0],group=group_id,assignment=assignment[0])
        assert not errors, errors
        # Withdraw disposable published fixtures; preserve their audit/attempt history.
        auth=api.post('/api/auth/login',data=accounts['ADMIN'])
        assert auth.ok
        headers={'Authorization':'Bearer '+auth.json()['token']}
        for cid in created_ids:
            record=api.get('/api/cms/content/'+cid,headers=headers)
            assert record.ok
            archived=api.post('/api/cms/content/'+cid+'/transition',headers=headers,data={'status':'ARCHIVED','version':record.json()['version']})
            assert archived.ok

    except Exception:
        page.screenshot(path=str(OUT/'failure.png'),full_page=True)
        OUT.joinpath('failure.txt').write_text(page.locator('body').inner_text(),encoding='utf-8')
        raise
    finally:
        OUT.joinpath('report.json').write_text(json.dumps({'checks':checks,'errors':errors,'fixtureIds':ids},ensure_ascii=False,indent=2),encoding='utf-8')
        context.close()
        browser.close()
        api.dispose()
print(f'PASS Phase 2: {len(checks)} checkpoints; browser errors: {len(errors)}')
