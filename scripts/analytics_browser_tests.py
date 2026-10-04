"""Statistics 2.0 against local Docker with real completed API attempts.

Only timestamps and completed planner fixtures are set with operator SQL. Logical
days derive from the server's injected analytics Clock (asOf), never host time.
Fixtures are private disposable accounts; no auth tokens are written to artifacts.
"""
import datetime as dt
import json
import os
from pathlib import Path
import subprocess
import uuid
from urllib.parse import urlparse
from functools import lru_cache
from playwright.sync_api import sync_playwright, expect

BASE = os.getenv('BROWSER_BASE_URL', 'http://127.0.0.1:8081')
assert urlparse(BASE).hostname in ('localhost', '127.0.0.1')
OUT = Path('test-results/analytics'); OUT.mkdir(parents=True, exist_ok=True)
SHOTS = Path('docs/screenshots/analytics'); SHOTS.mkdir(parents=True, exist_ok=True)
checks, errors, accounts, created = [], [], {}, []


def sql(statement):
    result = subprocess.run(['docker','compose','exec','-T','postgres','sh','-c',
        'exec psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],
        input=statement, text=True, encoding='utf-8', capture_output=True)
    assert result.returncode == 0, 'Local analytics fixture SQL failed'
    return result.stdout.strip()


def check(name, condition=True):
    assert condition, name
    checks.append(name); print('PASS '+name, flush=True)


with sync_playwright() as p:
    api=p.request.new_context(base_url=BASE)
    def call(role, method, path, data=None):
        res=api.fetch('/api'+path, method=method, data=data,
                      headers={'Authorization':'Bearer '+accounts[role]['token']})
        assert res.ok, f'{method} {path}: HTTP {res.status}'
        return res.json()
    for role in ('ADMIN','TEACHER','STUDENT','EMPTY'):
        account={'email':f'analytics-{role.lower()}-{uuid.uuid4()}@example.org',
                 'password':'Analytics-'+str(uuid.uuid4())}
        res=api.post('/api/auth/register',data={**account,'firstName':'Analytics','lastName':role,'language':'ru'})
        assert res.ok
        account['id']=res.json()['user']['id'];account['token']=res.json()['token'];accounts[role]=account
        if role=='ADMIN':
            # Exercise the real operator CLI with a fixture's explicit ID confirmation.
            operator_command=(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File','scripts/admin.ps1'] if os.name=='nt' else ['sh','scripts/admin.sh'])
            result=subprocess.run([*operator_command,'promote',account['email']],
                input='PROMOTE '+account['id']+'\n',text=True,encoding='utf-8',capture_output=True)
            assert result.returncode==0, 'Operator CLI fixture promotion failed'
            check('operator-confirmed-promotion-audited',sql(f"SELECT count(*) FROM audit_events WHERE entity_id='{uuid.UUID(account['id'])}' AND operation='OPERATOR_PROMOTE_ADMIN';")=='1')
        elif role=='TEACHER':
            sql(f"UPDATE users SET role='TEACHER' WHERE id='{uuid.UUID(account['id'])}';")
        account['token']=api.post('/api/auth/login',data={k:account[k] for k in ('email','password')}).json()['token']
    initial=call('STUDENT','GET','/statistics/me/analytics')
    anchor=initial['asOf']
    today=dt.date.fromisoformat(initial['to'])
    @lru_cache
    def stamp(days):
        date=(today-dt.timedelta(days=days)).isoformat()
        return sql(f"SELECT to_char((timestamp '{date} 12:00:00' AT TIME ZONE 'Asia/Almaty') AT TIME ZONE 'UTC','YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"');")
    def create(kind,title,parent=None,payload=None,role='ADMIN'):
        record=call(role,'POST','/cms/content',{'kind':kind,'parentId':parent,'payload':{'titleRu':title,'titleKz':title+' KZ',**(payload or {})}})
        created.append(record['id'])
        for state in ('REVIEW','PUBLISHED'):
            record=call('ADMIN','POST','/cms/content/'+record['id']+'/transition',{'status':state,'version':record['version']})
        return record['id']
    tag=uuid.uuid4().hex[:6]
    subject=create('SUBJECT','Аналитика '+tag)
    topic=create('TOPIC','Проверка прогресса '+tag,subject)
    theory=create('THEORY','Понятный разбор '+tag,topic,{'blocks':[{'type':'TEXT','textRu':'Разберите пример.','textKz':'Мысалды талдаңыз.'}]})
    options=[{'id':k,'textRu':k,'textKz':k} for k in ('A','B','C')]
    single=create('QUESTION','Первый вопрос '+tag,topic,{'options':options,'correctOptionId':'A'})
    multiple=create('QUESTION','Два ответа '+tag,topic,{'questionType':'MULTIPLE_SELECT','options':options,'correctOptionIds':['A','B']})
    skipped=create('QUESTION','Вопрос для пропуска '+tag,topic,{'options':options,'correctOptionId':'A'})
    def attempt(days,complete,seconds):
        session=call('STUDENT','POST','/tests/start',{'topicId':topic})['sessionId']
        for question, answer in [(single,{'selectedOptionId':'A' if complete else 'B'}),(multiple,{'selectedOptionIds':['A','B'] if complete else ['A']})]:
            call('STUDENT','POST',f'/tests/{session}/answers',{'questionId':question,'timeSpentSecs':1,'answer':answer})
        if days not in (1,2):
            call('STUDENT','POST',f'/tests/{session}/answers',{'questionId':skipped,'timeSpentSecs':1,'selectedOptionId':'A'})
        call('STUDENT','POST',f'/tests/{session}/finish')
        sql(f"UPDATE test_sessions SET started_at='{stamp(days)}'::timestamptz AT TIME ZONE 'UTC',completed_at='{stamp(days)}'::timestamptz AT TIME ZONE 'UTC',time_taken_secs={seconds} WHERE id='{uuid.UUID(session)}';")
        return session
    attempt(35,True,30);attempt(8,True,30);attempt(2,False,45);attempt(1,True,60)
    call('STUDENT','POST',f'/learning/theories/{theory}/read')
    sql(f"UPDATE user_theory_progress SET read_at='{stamp(1)}'::timestamptz AT TIME ZONE 'UTC',last_read_at='{stamp(1)}'::timestamptz AT TIME ZONE 'UTC' WHERE user_id='{uuid.UUID(accounts['STUDENT']['id'])}' AND theory_id='{uuid.UUID(theory)}';")
    course=create('COURSE','Курс аналитики '+tag,payload={'visibility':'PRIVATE','selfEnroll':False},role='TEACHER')
    module=create('MODULE','Модуль '+tag,course,role='TEACHER')
    lesson=create('LESSON','Урок '+tag,module,{'blocks':[{'type':'TEXT','textRu':'Учебный материал.','textKz':'Оқу материалы.'}]},role='TEACHER')
    assignment=create('ASSIGNMENT','Задание '+tag,course,{'maxScore':10,'descriptionRu':'Объясните решение.','descriptionKz':'Шешімді түсіндіріңіз.'},role='TEACHER')
    group=call('TEACHER','POST','/teacher/groups',{'name':'Analytics '+tag,'courseId':course})['id']
    call('TEACHER','POST',f'/teacher/groups/{group}/members',{'email':accounts['STUDENT']['email']})
    call('TEACHER','POST',f'/teacher/groups/{group}/assignments',{'assignmentId':assignment})
    call('STUDENT','POST',f'/lessons/{lesson}/complete')
    call('STUDENT','POST',f'/assignments/{assignment}/submit',{'text':'Объяснение решения','revision':0,'requestKey':str(uuid.uuid4()),'fileIds':[]})
    sql(f"UPDATE lesson_progress SET completed_at='{stamp(1)}' WHERE user_id='{uuid.UUID(accounts['STUDENT']['id'])}' AND lesson_id='{uuid.UUID(lesson)}'; UPDATE submission_revisions SET submitted_at='{stamp(1)}' WHERE user_id='{uuid.UUID(accounts['STUDENT']['id'])}' AND assignment_id='{uuid.UUID(assignment)}';")
    for index in range(22):
        sql(f"INSERT INTO study_tasks(id,user_id,source_key,kind,target_kind,target_id,title_ru,title_kz,reason,duration_minutes,status,completed_at) VALUES ('{uuid.uuid4()}','{uuid.UUID(accounts['STUDENT']['id'])}','analytics-{index}','THEORY','TOPIC','{uuid.UUID(topic)}','Повторить тему','Тақырыпты қайталау','WEAK',10,'COMPLETED','{stamp(2 if index==0 else 1)}');")
    data=call('STUDENT','GET','/statistics/me/analytics?period=7d');s=data['summary']
    expected={'questionsAnswered':6,'fullyCorrectAnswers':2,'accuracyPercent':33.33,'earnedPoints':4,'maxPoints':8,'pointsPercent':50,'testsCompleted':2,'activeDays':2,'currentStreak':2,'errorsResolved':2,'theoriesRead':1,'lessonsCompleted':1,'plannerTasksCompleted':22,'assignmentsSubmitted':1,'testTimeSecs':105}
    check('server-7d-all-metrics',all(s[k]==v for k,v in expected.items()))
    check('server-empty-day-null-accuracy',data['daily'][-1]['accuracy'] is None)
    check('server-prior-period-deltas',data['comparison']['questionsDelta']==3 and data['comparison']['accuracyDelta']==-66.67 and data['comparison']['pointsPercentDelta']==-50)
    check('server-30d-and-lifetime',call('STUDENT','GET','/statistics/me/analytics?period=30d')['summary']['questionsAnswered']==9 and call('STUDENT','GET','/statistics/me/analytics?period=all')['summary']['questionsAnswered']==12)
    browser=p.chromium.launch(headless=True)
    context=browser.new_context(viewport={'width':1440,'height':1000},reduced_motion='reduce',locale='ru-RU')
    page=context.new_page();page.on('pageerror',lambda error:errors.append(str(error)))
    def go(path):
        page.goto(BASE+path);page.wait_for_load_state('networkidle')
    def login(role):
        go('/login');page.evaluate('sessionStorage.clear()');page.reload()
        page.get_by_label('Электронная почта').fill(accounts[role]['email']);page.get_by_label('Пароль',exact=True).fill(accounts[role]['password'])
        page.get_by_role('button',name='Войти',exact=True).click();expect(page.locator('.study-hero')).to_be_visible();page.wait_for_load_state('networkidle')
    def inspect(name,shot=True):
        page.wait_for_load_state('networkidle')
        if not page.evaluate('document.documentElement.scrollWidth<=innerWidth'):
            OUT.joinpath('overflow.json').write_text(json.dumps(page.evaluate("() => [...document.querySelectorAll('*')].filter(e=>e.getBoundingClientRect().right>innerWidth+1).map(e=>({tag:e.tagName,cls:e.className,right:e.getBoundingClientRect().right,width:e.getBoundingClientRect().width})).slice(-30)"),indent=2),encoding='utf-8')
            page.screenshot(path=str(OUT/'overflow.png'))
        check(name+'-no-overflow',page.evaluate('document.documentElement.scrollWidth<=innerWidth'))
        if shot:
            page.screenshot(path=str(SHOTS/(name+'.png')),full_page=False)
            page.evaluate(Path('frontend/node_modules/axe-core/axe.min.js').read_text(encoding='utf-8'))
            violations=page.evaluate("async()=> (await axe.run(document,{runOnly:['wcag2a','wcag2aa','wcag21aa']})).violations.map(v=>({id:v.id,nodes:v.nodes.map(n=>n.target)}))")
            check(name+'-axe',not violations)
    try:
        login('EMPTY');check('empty-dashboard-no-false-accuracy',page.locator('.progress-summary').get_by_text('0%',exact=True).count()==0)
        go('/statistics');expect(page.get_by_role('heading',name='Пока недостаточно данных.',exact=True)).to_be_visible()
        check('empty-no-false-zero-percent',page.locator('.analytics-metrics').get_by_text('0%',exact=True).count()==0)
        inspect('empty-desktop')
        login('STUDENT');expect(page.get_by_role('heading',name='Эта неделя',exact=True)).to_be_visible();expect(page.locator('.weekly-summary')).to_contain_text('33,3%')
        check('weekly-dashboard-summary');inspect('dashboard-weekly-1440')
        for width in (390,320):
            page.set_viewport_size({'width':width,'height':900});inspect(f'dashboard-weekly-{width}')
        page.set_viewport_size({'width':1440,'height':1000});go('/statistics')
        # Reconnaissance after hydration precedes interaction.
        OUT.joinpath('rendered-controls.txt').write_text('\n'.join(page.get_by_role('button').all_text_contents()),encoding='utf-8')
        expect(page.locator('.analytics-metrics')).to_contain_text('33,3%');expect(page.locator('.analytics-metrics')).to_contain_text('50%')
        check('separate-accuracy-and-points-visible');expect(page.locator('.accuracy-line path')).to_have_attribute('d',__import__('re').compile('^M'))
        page.get_by_text('Данные графиков по дням',exact=True).click();expect(page.get_by_role('table',name='Практика и учебные действия')).to_be_visible()
        check('accessible-daily-table');page.get_by_text('Данные графиков по дням',exact=True).click()
        cell=page.locator('.heat-cell').nth(82);cell.focus();expect(page.locator('.heatmap-detail')).to_contain_text((today-dt.timedelta(days=1)).isoformat());cell.press('ArrowUp')
        check('heatmap-keyboard-details')
        page.get_by_label('Тип действия').select_option('PLANNER_TASK');expect(page.locator('.analytics-history li')).to_have_count(20)
        page.get_by_role('button',name='Далее',exact=True).click();expect(page.locator('.analytics-history li')).to_have_count(2);check('filtered-history-pagination')
        page.get_by_role('button',name='30 дней',exact=True).click();expect(page.locator('.analytics-metrics')).to_contain_text('55,6%');check('30d-switch-real-metrics')
        page.get_by_role('button',name='Всё время',exact=True).click();expect(page.locator('.analytics-metrics')).to_contain_text('66,7%');check('all-switch-real-metrics')
        page.get_by_role('button',name='7 дней',exact=True).click();expect(page.locator('.analytics-metrics')).to_contain_text('33,3%')
        for width in (1440,1024,768,390,320):
            page.set_viewport_size({'width':width,'height':900});inspect(f'statistics-{width}')
            if width==1440:
                page.locator('.analytics-charts').screenshot(path=str(SHOTS/'daily-charts.png'))
                page.locator('.study-heatmap').screenshot(path=str(SHOTS/'heatmap.png'))
        check('reduced-motion',page.evaluate("matchMedia('(prefers-reduced-motion: reduce)').matches && getComputedStyle(document.querySelector('.accuracy-line')).animationName==='none'"))
        page.get_by_role('button',name='ҚАЗ',exact=True).click();expect(page.get_by_role('button',name='Барлық уақыт',exact=True)).to_be_visible();expect(page.get_by_role('heading',name='Оқу нәтижеңіз',exact=True)).to_be_visible();inspect('statistics-kz-320')
        check('student-has-no-staff-nav',page.get_by_role('link',name='Кабинет',exact=True).count()==0)
        page.get_by_role('button',name='РУС',exact=True).click();page.set_viewport_size({'width':1440,'height':1000})
        login('ADMIN');go('/admin');expect(page).to_have_url(BASE+'/workspace/content')
        for label in ('Контент','Пользователи','Файлы','Импорт CSV / JSON','Источники и права','Аналитика'):
            expect(page.locator('.workspace-nav').get_by_role('link',name=label,exact=True)).to_be_visible()
        check('admin-navigation-obvious');page.get_by_role('link',name='Аналитика',exact=True).click();expect(page.get_by_role('heading',name='Аналитика обучения',exact=True)).to_be_visible();inspect('staff-admin')
        login('TEACHER');go('/workspace/analytics');expect(page.get_by_text('Практика ЕНТ учеников ваших курсов и групп, события только ваших курсов. Личные планы учеников недоступны.',exact=True)).to_be_visible();inspect('staff-teacher')
        check('teacher-api-scoped',call('TEACHER','GET','/teacher/analytics')['studentsInScope']==1)
        check('no-page-errors',not errors)
        OUT.joinpath('report.json').write_text(json.dumps({'status':'PASS','checks':checks,'asOf':anchor,'logicalToday':str(today),'expected7d':expected,'errors':errors},ensure_ascii=False,indent=2),encoding='utf-8')
    finally:
        browser.close();api.dispose()
        # Retain real audit/results but archive only our authoring records and disable fixtures.
        ids=','.join("'"+str(uuid.UUID(i))+"'" for i in created)
        users=','.join("'"+str(uuid.UUID(a['id']))+"'" for a in accounts.values())
        sql(f"UPDATE content_records SET status='ARCHIVED',version=version+1 WHERE id IN ({ids}); UPDATE subjects SET is_active=false WHERE id='{uuid.UUID(subject)}'; UPDATE topics SET is_active=false WHERE id='{uuid.UUID(topic)}'; UPDATE questions SET is_active=false WHERE topic_id='{uuid.UUID(topic)}'; UPDATE theories SET is_active=false WHERE topic_id='{uuid.UUID(topic)}'; UPDATE users SET is_active=false WHERE id IN ({users});")
print(f'PASS: {len(checks)} analytics checks',flush=True)
