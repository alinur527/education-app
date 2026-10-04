"""Live expansion journeys; local Docker only. API calls provision disposable fixtures.

Real UI covers sources/packs, planner/notes, student files, teacher grading and PWA.
Content authoring/group creation remains covered by phase2_browser_tests.py.
No user secrets, submissions or database dumps are written to tracked artifacts.
"""
import datetime as dt
import json
import os
from pathlib import Path
import subprocess
import uuid
import time
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

BASE=os.getenv('BROWSER_BASE_URL','http://127.0.0.1:8081')
assert urlparse(BASE).hostname in ('localhost','127.0.0.1')
OUT=Path('test-results/expansion');OUT.mkdir(parents=True,exist_ok=True)
SHOTS=Path('docs/screenshots/expansion/after');SHOTS.mkdir(parents=True,exist_ok=True)
run=uuid.uuid4().hex[:8];checks=[];errors=[];created=[];accounts={}
expected_failure=False

with sync_playwright() as p:
    api=p.request.new_context(base_url=BASE)
    for role in ('ADMIN','TEACHER','STUDENT'):
        account={'email':f'expansion-{role.lower()}-{uuid.uuid4()}@example.org','password':'Expansion-'+str(uuid.uuid4())}
        res=api.post('/api/auth/register',data={**account,'firstName':'Expansion','lastName':role,'language':'ru'})
        assert res.ok,(res.status,res.text())
        account['id']=res.json()['user']['id']
        if role!='STUDENT':
            sql=f"UPDATE users SET role='{role}' WHERE id='{uuid.UUID(account['id'])}'::uuid"
            subprocess.run(['docker','compose','exec','-T','postgres','sh','-c','psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "$1"','sh',sql],check=True,capture_output=True)
        auth=api.post('/api/auth/login',data={k:account[k] for k in ('email','password')});assert auth.ok
        account['token']=auth.json()['token'];accounts[role]=account
    def call(role,method,path,data=None):
        res=api.fetch('/api'+path,method=method,data=data,headers={'Authorization':'Bearer '+accounts[role]['token']})
        assert res.ok,(method,path,res.status,res.text()[:600])
        return res.json()
    # Full original starter pack is installed even in CI's empty isolated database.
    env={**os.environ,'EDU_EMAIL':accounts['ADMIN']['email'],'EDU_PASSWORD':accounts['ADMIN']['password']}
    import sys
    with OUT.joinpath('content-apply.log').open('w',encoding='utf-8') as f:
        subprocess.run([sys.executable,'-X','utf8','scripts/content_release.py','apply','--publish','--base-url',BASE],env=env,check=True,stdout=f,stderr=subprocess.STDOUT)
    mapping={};n=0
    while True:
        rows=call('ADMIN','GET',f'/cms/content-packs/mappings?namespace=education-kz-2026&page={n}')
        mapping.update({r['externalKey']:r['contentId'] for r in rows})
        if len(rows)<100:break
        n+=1
    def create(kind,title,parent=None,payload=None):
        c=call('TEACHER','POST','/cms/content',{'kind':kind,'parentId':parent,'payload':{'titleRu':title,'titleKz':title+' KZ',**(payload or {})}})
        created.append(c['id'])
        for state in ('REVIEW','PUBLISHED'):c=call('ADMIN','POST','/cms/content/'+c['id']+'/transition',{'status':state,'version':c['version']})
        return c['id']
    course=create('COURSE','Browser files '+run,payload={'visibility':'PUBLIC','selfEnroll':True})
    assignment=create('ASSIGNMENT','Проверка ответа '+run,course,{'maxScore':10,'descriptionRu':'Объясните решение. Приложите TXT.','descriptionKz':'Шешімді түсіндіріңіз. TXT тіркеңіз.','dueAt':(dt.datetime.now(dt.timezone.utc)+dt.timedelta(days=1)).isoformat()})
    group=call('TEACHER','POST','/teacher/groups',{'name':'Expansion '+run,'courseId':course})['id']
    call('TEACHER','POST',f'/teacher/groups/{group}/members',{'email':accounts['STUDENT']['email']})
    call('TEACHER','POST',f'/teacher/groups/{group}/assignments',{'assignmentId':assignment})
    browser=p.chromium.launch(headless=True)
    context=browser.new_context(viewport={'width':1440,'height':1000},reduced_motion='reduce',accept_downloads=True)
    page=context.new_page()
    page.on('pageerror',lambda e:errors.append(str(e)))
    page.on('console',lambda m:errors.append(m.text) if m.type=='error' and not expected_failure else None)
    page.on('response',lambda r:errors.append(f'HTTP {r.status} {r.url}') if r.status>=400 and not expected_failure else None)
    page.on('requestfailed',lambda r:errors.append(f'Failed {r.url}: {r.failure}') if not expected_failure and r.failure!='net::ERR_ABORTED' else None)
    page.on('dialog',lambda d:d.accept())
    def go(path):
        page.goto(BASE+path);page.wait_for_load_state('networkidle')
    def login(role):
        go('/login');page.evaluate('sessionStorage.clear()');page.reload()
        page.get_by_label('Электронная почта').fill(accounts[role]['email']);page.get_by_label('Пароль',exact=True).fill(accounts[role]['password'])
        page.get_by_role('button',name='Войти',exact=True).click();expect(page.locator('.study-hero')).to_be_visible();page.wait_for_load_state('networkidle')
    def inspect(name,shot=False):
        page.wait_for_load_state('networkidle')
        assert page.evaluate('document.documentElement.scrollWidth<=innerWidth'), 'Overflow '+name
        if shot:
            page.screenshot(path=str(SHOTS/(name+'.png')))
            page.evaluate(Path('frontend/node_modules/axe-core/axe.min.js').read_text(encoding='utf-8'))
            violations=page.evaluate("async()=> (await axe.run(document,{runOnly:['wcag2a','wcag2aa','wcag21aa']})).violations.map(v=>({id:v.id,nodes:v.nodes.map(n=>n.target)}))")
            assert not violations,(name,violations)
        checks.append(name);print('PASS '+name,flush=True)
    try:
        login('ADMIN');go('/workspace/sources')
        page.get_by_text('Добавить ссылку на источник',exact=True).click()
        page.get_by_label('Постоянный ключ (латиница)').fill('E2E-'+run)
        page.get_by_label('Название',exact=True).fill('Оригинальный источник '+run)
        page.get_by_label('URL',exact=True).fill('https://docs.python.org/3/tutorial/')
        page.get_by_role('button',name='Сохранить ссылку',exact=True).click()
        expect(page.get_by_label('Постоянный ключ (латиница)')).to_have_value('');inspect('source-create',True)
        go('/workspace/packs')
        batch={'schema':'education-content-pack/v1','namespace':'browser-'+run,'packVersion':'v1','batchKey':'first','rows':[{'externalKey':'subject','kind':'SUBJECT','sourceVersion':'v1','payload':{'titleRu':'Browser pack '+run,'titleKz':'Browser pack '+run}}]}
        upload=page.get_by_label('Пакет JSON, до 2 МБ')
        upload.set_input_files({'name':'valid.json','mimeType':'application/json','buffer':json.dumps(batch).encode()})
        expect(page.get_by_role('button',name='Подтвердить импорт в черновики')).to_be_visible();inspect('pack-preview',True)
        page.get_by_role('button',name='Подтвердить импорт в черновики').click();expect(page.get_by_text('Статус: APPLIED',exact=True)).to_be_visible();inspect('pack-confirm')
        upload.set_input_files({'name':'repeat.json','mimeType':'application/json','buffer':json.dumps(batch).encode()});expect(page.get_by_text('Статус: APPLIED',exact=True)).to_be_visible();inspect('pack-idempotent')
        bad={**batch,'batchKey':'bad','rows':[{'externalKey':'bad','kind':'QUESTION','payload':{'titleRu':'Invalid','titleKz':'Invalid','correctOptionId':'D'}}]}
        upload.set_input_files({'name':'bad.json','mimeType':'application/json','buffer':json.dumps(bad).encode()});expect(page.get_by_text('Статус: INVALID',exact=True)).to_be_visible();expect(page.get_by_role('button',name='Подтвердить импорт в черновики')).to_have_count(0);inspect('pack-invalid')
        imported=call('ADMIN','GET','/cms/content-packs/mappings?namespace=browser-'+run)[0]['contentId']
        created.append(imported)
        go('/workspace/content?kind=SUBJECT&q=Browser%20pack%20'+run)
        for target in ('REVIEW','PUBLISHED'):
            page.get_by_role('checkbox',name='Выбрать: Browser pack '+run,exact=True).check()
            page.locator('.bulk-actions').get_by_role('combobox').select_option(target)
            page.get_by_role('button',name='Предпросмотр изменений',exact=True).click()
            expect(page.get_by_role('region',name='Предпросмотр массовой операции')).to_be_visible()
            expect(page.get_by_role('button',name='Подтвердить изменения',exact=True)).to_be_enabled()
            page.get_by_role('button',name='Подтвердить изменения',exact=True).click()
            expect(page.get_by_role('row').filter(has_text='Browser pack '+run).locator('.status-badge')).to_have_text('На проверке' if target=='REVIEW' else 'Опубликовано')
            assert call('ADMIN','GET','/cms/content/'+imported)['status']==target
            inspect('pack-bulk-'+target.lower(),True)
        login('STUDENT');go('/study')
        page.get_by_label('Моя цель',exact=True).fill('Закрепить математику')
        page.get_by_label('Целевая дата').fill((dt.date.today()+dt.timedelta(days=30)).isoformat())
        page.get_by_label('Минут в учебный день').fill('60');page.get_by_label('Часовой пояс',exact=True).fill('Asia/Almaty')
        page.get_by_label('Математика',exact=True).check()
        for label in ('Пн','Вт','Ср','Чт','Пт','Сб','Вс'):page.get_by_label(label,exact=True).check()
        page.get_by_role('button',name='Сохранить профиль',exact=True).click()
        expect(page.get_by_role('button',name='Составить / обновить план')).to_be_enabled()
        page.get_by_role('button',name='Составить / обновить план').click()
        expect(page.locator('.study-task').first).to_be_visible();inspect('planner-generated',True)
        task=page.locator('.study-task').first;task.get_by_role('button',name='Перенести',exact=True).click()
        tomorrow=(dt.date.today()+dt.timedelta(days=1)).isoformat()
        task.get_by_label('Новая дата и время',exact=False).fill(tomorrow+'T18:30')
        task.get_by_role('button',name='Применить перенос').click();page.wait_for_load_state('networkidle');page.get_by_label('Дата календаря',exact=True).fill(tomorrow);page.wait_for_load_state('networkidle')
        page.get_by_role('button',name='Составить / обновить план').click();page.wait_for_load_state('networkidle')
        expect(page.get_by_text('Перенесено вами',exact=False).first).to_be_visible();inspect('planner-move-preserved')
        topic=next(v for k,v in mapping.items() if k.startswith('starter:') and ':theory' not in k and ':q:' not in k and ':context:' not in k)
        go('/notes?kind=TOPIC&target='+topic+'&title=Browser-note')
        page.get_by_label('Моя заметка',exact=True).fill('Единицы измерения проверять перед вычислением.')
        page.get_by_label('Добавить в закладки',exact=True).check();page.get_by_label('Вопрос / термин').fill('Единица силы?');page.get_by_label('Ответ / определение').fill('Ньютон, Н.')
        page.get_by_role('button',name='Сохранить запись').click();page.wait_for_load_state('networkidle');page.get_by_role('button',name='К списку',exact=True).click()
        expect(page.get_by_text('Browser-note',exact=True)).to_be_visible();inspect('note-bookmark-card',True)
        go('/assignments/'+assignment);page.get_by_label('Ваш ответ').fill('Мой ответ с файлом.')
        page.get_by_label('Выбрать файл к ответу').set_input_files({'name':'solution.txt','mimeType':'text/plain','buffer':'2 + 2 = 4\n'.encode()})
        page.get_by_role('button',name='Загрузить и проверить файл').click();expect(page.get_by_text('solution.txt',exact=True).first).to_be_visible()
        expect(page.get_by_role('checkbox',name='solution.txt')).to_be_checked();inspect('student-clean-upload',True)
        page.get_by_role('button',name='Отправить ответ',exact=True).click();page.wait_for_load_state('networkidle');inspect('student-text-file-submit')
        login('TEACHER');go('/workspace/assignments/'+assignment)
        page.get_by_label('Балл',exact=True).fill('8');page.get_by_label('Комментарий учителя').fill('Проверено: версия 1.')
        page.get_by_role('button',name='Сохранить оценку').click();page.wait_for_load_state('networkidle');inspect('teacher-grade-file-revision',True)
        login('STUDENT');go('/notifications')
        page.get_by_text('Настроить уведомления',exact=True).click();page.get_by_label('Тихие часы',exact=True).uncheck()
        page.get_by_role('button',name='Сохранить настройки',exact=True).click();page.wait_for_load_state('networkidle')
        reminder=page.locator('.study-notification').filter(has_text='Оценка опубликована')
        # Scheduler scans 100 accounts/minute; a final empty cursor cycle can add one minute.
        until=time.monotonic()+155
        while reminder.count()==0 and time.monotonic()<until:
            time.sleep(2);page.get_by_role('button',name='Обновить',exact=True).click();page.wait_for_load_state('networkidle')
        expect(reminder.first).to_be_visible();reminder.first.get_by_role('button',name='Прочитано',exact=True).click();page.wait_for_load_state('networkidle');inspect('grade-reminder-read',True)
        go('/assignments/'+assignment)
        expect(page.get_by_text('8 / 10',exact=True)).to_be_visible()
        page.get_by_label('Ваш ответ').fill('Уточнение решения, новая версия.')
        page.get_by_role('button',name='Отправить ответ',exact=True).click();page.wait_for_load_state('networkidle')
        expect(page.locator('.learning-callout').filter(has_text='8 / 10')).to_have_count(0);inspect('resubmission-resets-current-grade')
        go('/notifications');inspect('reminder-preferences',True)
        theory_key=next(k for k in mapping if k.startswith('starter:') and k.endswith(':theory'))
        go('/topics/'+mapping[theory_key[:-7]])
        page.get_by_role('button',name='Сохранить для чтения без интернета',exact=True).click();page.wait_for_load_state('networkidle')
        expect(page.get_by_role('button',name='Сохранено на устройстве',exact=True)).to_be_visible()
        page.evaluate('async()=>{await navigator.serviceWorker.ready;}');go('/saved');page.reload();page.wait_for_load_state('networkidle')
        assert page.evaluate('!!navigator.serviceWorker.controller')
        cached=page.evaluate('async()=>{const names=await caches.keys();return (await Promise.all(names.map(async n=>(await(await caches.open(n)).keys()).map(r=>r.url)))).flat();}')
        assert cached and all('/api/' not in u for u in cached),cached
        expected_failure=True;context.set_offline(True);page.reload();expect(page.get_by_text('Офлайн: показана сохранённая версия',exact=True)).to_be_visible()
        page.locator('.saved-list .text-link').first.click();expect(page.locator('.saved-reading')).to_be_visible();inspect('offline-public-reading',True)
        context.set_offline(False);expected_failure=False
        go('/practice')
        page.get_by_role('button',name='Тренировка с таймером',exact=True).click()
        page.get_by_label('Минуты',exact=True).fill('2');page.get_by_label('Количество вопросов (до 50)',exact=True).fill('2')
        page.get_by_role('button',name='Начать тренировку',exact=True).click();expect(page.locator('.question-card')).to_be_visible()
        sid=page.url.rsplit('/',1)[1];before=call('STUDENT','GET','/tests/'+sid)
        page.reload();expect(page.locator('.question-card')).to_be_visible();after=call('STUDENT','GET','/tests/'+sid)
        assert before['deadlineAt']==after['deadlineAt'];inspect('timed-refresh-deadline',True)
        page.get_by_role('button',name='Завершить тест',exact=True).click()
        page.get_by_role('alertdialog').get_by_role('button').last.click();expect(page.locator('.result-banner')).to_be_visible();inspect('timed-results',True)
        for width in (320,390,768,1024,1440):
            page.set_viewport_size({'width':width,'height':900});go('/study');inspect('planner-'+str(width),True);go('/practice');inspect('practice-'+str(width),True)
        assert not errors,errors
        for cid in reversed(created):
            c=call('ADMIN','GET','/cms/content/'+cid)
            call('ADMIN','POST','/cms/content/'+cid+'/transition',{'status':'ARCHIVED','version':c['version']})
    except Exception:
        page.screenshot(path=str(OUT/'failure.png'),full_page=True);OUT.joinpath('failure.txt').write_text(page.locator('body').inner_text(),encoding='utf-8');raise
    finally:
        OUT.joinpath('report.json').write_text(json.dumps({'checks':checks,'errors':errors},ensure_ascii=False,indent=2),encoding='utf-8')
        context.close();browser.close();api.dispose()
print(f'PASS expansion: {len(checks)} checkpoints, {len(errors)} unexpected browser errors')
