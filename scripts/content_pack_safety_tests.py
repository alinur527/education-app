"""Bounded live content-pack safety checks against local Docker only.

Standard-library Python; no browser or stored credentials. Provisioning promotes
fresh synthetic accounts through local SQL. Product behavior is exercised through
HTTP. Cleanup archives only these fixtures and disables the generated accounts.
"""
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import subprocess
import uuid
from urllib.error import HTTPError
from urllib.parse import quote, urlparse
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
BASE = os.getenv('BROWSER_BASE_URL', 'http://127.0.0.1:8081').rstrip('/')
assert urlparse(BASE).hostname in ('localhost', '127.0.0.1'), 'Local Docker only'
OUT = ROOT / 'test-results/content-pack-safety'


def sql(statement):
    result = subprocess.run(
        ['docker', 'compose', 'exec', '-T', 'postgres', 'sh', '-c',
         'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -At -c "$1"',
         'sh', statement], cwd=ROOT, capture_output=True, text=True, timeout=30,
    )
    assert result.returncode == 0, 'Local fixture SQL failed'
    return result.stdout.strip()


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True).encode()).hexdigest()


def main():
    run = uuid.uuid4().hex
    namespace = 'safety-' + run
    prefix = 'Pack safety ' + run
    accounts, checks, batches, cleanup_errors = {}, [], [], []
    report = {'namespace': namespace, 'baseUrl': BASE, 'checks': checks, 'batches': batches}
    failure = None

    def call(method, path, role='ADMIN', data=None, status=200):
        headers = {'Content-Type': 'application/json'}
        if role:
            headers['Authorization'] = 'Bearer ' + accounts[role]['token']
        req = Request(BASE + '/api' + path, method=method, headers=headers,
                      data=json.dumps(data).encode() if data is not None else None)
        try:
            with urlopen(req, timeout=30) as response:
                actual, raw = response.status, response.read()
        except HTTPError as response:
            actual, raw = response.code, response.read()
        # Never include raw responses in failures: auth responses contain tokens.
        assert actual == status, f'{method} {path}: expected {status}, received {actual}'
        return json.loads(raw) if raw else None

    def check(name, condition=True):
        assert condition, name
        checks.append(name)
        print('PASS ' + name, flush=True)

    def account(role):
        credentials = {'email': f'pack-safety-{role.lower()}-{run}@example.org',
                       'password': 'PackSafety-' + str(uuid.uuid4())}
        registered = call('POST', '/auth/register', role=None,
                          data={**credentials, 'firstName': 'Safety', 'lastName': role, 'language': 'ru'})
        identifier = str(uuid.UUID(registered['user']['id']))
        accounts[role] = {'id': identifier, 'token': registered['token']}
        if role != 'STUDENT':
            assert role in ('ADMIN', 'TEACHER')
            sql(f"UPDATE users SET role='{role}' WHERE id='{identifier}'::uuid")
        accounts[role]['token'] = call('POST', '/auth/login', role=None, data=credentials)['token']

    def payload(suffix):
        return {'titleRu': prefix + ' ' + suffix, 'titleKz': prefix + ' ' + suffix + ' KZ'}

    def row(key, kind, parent=None, value=None, existing=None):
        result = {'externalKey': key, 'kind': kind, 'sourceVersion': 'v1',
                  'payload': value or payload(key)}
        if parent:
            result['parentExternalKey'] = parent
        if existing:
            result['existingId'] = existing
        return result

    def pack(key, rows):
        return {'schema': 'education-content-pack/v1', 'namespace': namespace,
                'packVersion': 'v1', 'batchKey': key, 'rows': rows}

    def preview(request):
        value = call('POST', '/cms/content-packs', data=request)
        batches.append({'key': request['batchKey'], 'id': value['id'], 'status': value['status'],
                        'actions': [item.get('action') for item in value['preview']],
                        'errors': [item['error'] for item in value['preview'] if 'error' in item]})
        return value

    def confirm(value):
        result = call('POST', '/cms/content-packs/' + value['id'] + '/confirm')
        assert result['status'] == 'APPLIED'
        return result

    def mappings():
        values, page = [], 0
        while True:
            part = call('GET', f'/cms/content-packs/mappings?namespace={namespace}&page={page}')
            values.extend(part)
            if len(part) < 100:
                return {v['externalKey']: v['contentId'] for v in values}
            page += 1

    def cms(identifier, role='ADMIN'):
        return call('GET', '/cms/content/' + identifier, role)

    def edit(value, content, role='TEACHER'):
        return call('PUT', '/cms/content/' + value['id'], role,
                    {'kind': value['kind'], 'parentId': value['parentId'],
                     'payload': content, 'version': value['version']})

    def candidate(batch_id, content_id):
        page = 0
        while True:
            rows = call('GET', '/cms/content-packs/conflicts?page=' + str(page))
            found = next((v for v in rows if v['batchId'] == batch_id and v['contentId'] == content_id), None)
            if found:
                return call('GET', '/cms/content-packs/conflicts/' + found['id'])
            assert len(rows) == 25, 'Expected conflict candidate is missing'
            page += 1

    def creator_count(role):
        identifier = str(uuid.UUID(accounts[role]['id']))
        return int(sql(f"SELECT count(*) FROM content_records WHERE created_by='{identifier}'::uuid"))

    try:
        for role in ('ADMIN', 'TEACHER', 'STUDENT'):
            account(role)
        original_rows = [row('course', 'COURSE', value={**payload('course'), 'visibility': 'PRIVATE'}),
                         row('module', 'MODULE', 'course')]
        first_request = pack('first', original_rows)
        call('POST', '/cms/content-packs', 'TEACHER', first_request, 403)
        call('POST', '/cms/content-packs', 'STUDENT', first_request, 403)
        check('teacher-and-student-cannot-import-packs')
        first = confirm(preview(first_request))
        original_ids = mappings()
        check('first-batch-created-two-drafts', first['result']['created'] == 2 and creator_count('ADMIN') == 2
              and all(cms(identifier)['status'] == 'DRAFT' for identifier in original_ids.values()))
        original_views = {key: cms(identifier) for key, identifier in original_ids.items()}

        later_rows = [row('resume-module', 'MODULE', 'course'), row('resume-lesson', 'LESSON', 'course')]
        invalid = preview(pack('second-invalid', later_rows))
        check('later-batch-reports-invalid-parent', invalid['status'] == 'INVALID'
              and invalid['preview'][0]['action'] == 'CREATE'
              and invalid['preview'][1]['error'] == 'INVALID_PARENT')
        call('POST', '/cms/content-packs/' + invalid['id'] + '/confirm', status=409)
        check('invalid-batch-writes-no-content-or-mappings', mappings() == original_ids and creator_count('ADMIN') == 2
              and original_views == {key: cms(identifier) for key, identifier in original_ids.items()})

        fixed_later = [later_rows[0], row('resume-lesson', 'LESSON', 'resume-module')]
        call('POST', '/cms/content-packs', data=pack('second-invalid', fixed_later), status=409)
        check('changed-request-cannot-reuse-failed-batch-key')
        resumed_request = pack('second-fixed', original_rows + fixed_later)
        resumed_preview = preview(resumed_request)
        check('resume-preview-skips-first-batch-rows', [v['action'] for v in resumed_preview['preview']]
              == ['UNCHANGED', 'UNCHANGED', 'CREATE', 'CREATE'])
        resumed = confirm(resumed_preview)
        resumed_ids = mappings()
        check('resume-creates-only-two-missing-records', resumed['result']['created'] == 2
              and resumed['result']['unchanged'] == 2 and creator_count('ADMIN') == 4
              and len(resumed_ids) == 4 and all(resumed_ids[k] == v for k, v in original_ids.items()))
        repeated = preview(resumed_request)
        check('same-batch-retry-returns-saved-result', repeated == resumed and confirm(repeated) == resumed)
        check('first-batch-retry-remains-identical', preview(first_request) == first)
        replay = confirm(preview(pack('third-replay', original_rows + fixed_later)))
        check('new-batch-replay-keeps-all-ids-without-duplicates', replay['result']['unchanged'] == 4
              and replay['result']['created'] == 0 and replay['result']['updated'] == 0
              and mappings() == resumed_ids and creator_count('ADMIN') == 4)

        published_payload = {**payload('teacher-original'), 'visibility': 'PRIVATE', 'selfEnroll': False,
                             'descriptionRu': 'Original reviewed text', 'descriptionKz': 'Бастапқы мәтін'}
        teacher_course = call('POST', '/cms/content', 'TEACHER',
                              {'kind': 'COURSE', 'payload': published_payload})
        course_id = teacher_course['id']
        adopted = confirm(preview(pack('adopt-teacher-course',
                                      [row('teacher-course', 'COURSE', value=teacher_course['payload'], existing=course_id)])))
        check('adoption-keeps-teacher-ownership', adopted['result']['unchanged'] == 1
              and cms(course_id)['ownerId'] == accounts['TEACHER']['id'])
        for state in ('REVIEW', 'PUBLISHED'):
            teacher_course = call('POST', '/cms/content/' + course_id + '/transition', 'TEACHER',
                                  {'status': state, 'version': teacher_course['version']})
        published = call('GET', '/content/' + course_id, 'TEACHER')
        published_version = teacher_course['publishedVersion']
        report['publishedSnapshotSha256'] = digest(published)
        call('GET', '/content/' + course_id, 'STUDENT', status=404)
        check('published-fixture-is-private-to-unenrolled-student')
        local_payload = {**published_payload, 'titleRu': prefix + ' teacher-local'}
        local = edit(teacher_course, local_payload)
        incoming_payload = {**published_payload, 'titleRu': prefix + ' pack-incoming'}

        for decision, expected_status in (('KEEP_LOCAL', 'REJECTED'), ('USE_INCOMING_DRAFT', 'RESOLVED')):
            request = pack('conflict-' + decision.lower(), [row('teacher-course', 'COURSE', value=incoming_payload)])
            before_view = cms(course_id)
            conflict_preview = preview(request)
            check(decision + '-preview-is-explicit-conflict', conflict_preview['status'] == 'VALID'
                  and conflict_preview['preview'][0]['action'] == 'CONFLICT' and cms(course_id) == before_view)
            batch = confirm(conflict_preview)
            check(decision + '-confirm-does-not-overwrite-teacher', batch['result']['conflicts'] == 1
                  and batch['result']['updated'] == 0 and cms(course_id) == before_view)
            conflict = candidate(batch['id'], course_id)
            check(decision + '-candidate-shows-current-and-incoming', conflict['current']['payload'] == local_payload
                  and conflict['incoming'] == incoming_payload)
            path = '/cms/content-packs/conflicts/' + conflict['id'] + '/resolve'
            call('POST', path, 'TEACHER', {'decision': decision, 'version': before_view['version']}, 403)
            if decision == 'USE_INCOMING_DRAFT':
                # A second real teacher edit makes the administrator's previously loaded revision stale.
                local_payload = {**local_payload, 'descriptionRu': 'Teacher edited again after conflict preview'}
                local = edit(cms(course_id, 'TEACHER'), local_payload)
                call('POST', path, data={'decision': decision, 'version': before_view['version']}, status=409)
                check('stale-conflict-resolution-preserves-new-teacher-edit', cms(course_id) == local)
                before_view = cms(course_id)
            resolved = call('POST', path, data={'decision': decision, 'version': before_view['version']})
            current = cms(course_id)
            check(decision + '-resolution-is-explicit', resolved['status'] == expected_status
                  and (current == before_view if decision == 'KEEP_LOCAL' else
                       current['payload'] == incoming_payload and current['status'] == 'DRAFT'
                       and current['version'] == before_view['version'] + 1))
            check(decision + '-published-snapshot-unchanged', call('GET', '/content/' + course_id, 'TEACHER') == published
                  and current['publishedVersion'] == published_version)
            call('POST', path, data={'decision': decision, 'version': current['version']}, status=409)
            check(decision + '-resolution-cannot-run-twice')
            check(decision + '-batch-retry-has-no-side-effects', preview(request) == batch and cms(course_id) == current)

        current = cms(course_id)
        unchanged = confirm(preview(pack('post-resolution-replay', [row('teacher-course', 'COURSE', value=incoming_payload)])))
        check('resolved-incoming-replay-is-unchanged', unchanged['result']['unchanged'] == 1
              and unchanged['result']['created'] == 0 and unchanged['result']['updated'] == 0
              and unchanged['result']['conflicts'] == 0 and cms(course_id) == current)
        history = call('GET', '/cms/content/' + course_id + '/history')
        check('both-conflict-decisions-have-admin-audit-history', all(
            any(h['operation'] == 'PACK_' + decision and h['actorId'] == accounts['ADMIN']['id'] for h in history)
            for decision in ('KEEP_LOCAL', 'USE_INCOMING_DRAFT')))
        check('final-published-snapshot-still-original', digest(call('GET', '/content/' + course_id, 'TEACHER'))
              == report['publishedSnapshotSha256'] and cms(course_id)['publishedVersion'] == published_version)
        report['contentCountBeforeCleanup'] = creator_count('ADMIN') + creator_count('TEACHER')
        check('only-five-fixture-content-records-exist', report['contentCountBeforeCleanup'] == 5)
    except Exception as error:
        failure = error
        report['failure'] = str(error)
    finally:
        archived = 0
        if 'ADMIN' in accounts:
            try:
                rows = call('GET', '/cms/content?q=' + quote(prefix) + '&size=100')['items']
                owned = {v['id'] for v in accounts.values()}
                assert len(rows) < 100, 'Unexpected fixture cleanup size'
                # Reversed creation order archives children before parents; IDs and creator are checked.
                for item in reversed(sorted(rows, key=lambda v: v['createdAt'])):
                    assert item['createdBy'] in owned and item['titleRu'].startswith(prefix), 'Cleanup ownership mismatch'
                    content = cms(str(uuid.UUID(item['id'])))
                    if content['status'] != 'ARCHIVED':
                        call('POST', '/cms/content/' + content['id'] + '/transition',
                             data={'status': 'ARCHIVED', 'version': content['version']})
                    assert cms(content['id'])['status'] == 'ARCHIVED'
                    archived += 1
            except Exception as error:
                cleanup_errors.append(str(error))
        for value in accounts.values():
            try:
                identifier = str(uuid.UUID(value['id']))
                sql(f"UPDATE users SET is_active=false,role='STUDENT' WHERE id='{identifier}'::uuid")
                assert sql(f"SELECT is_active FROM users WHERE id='{identifier}'::uuid") == 'f'
            except Exception as error:
                cleanup_errors.append(str(error))
        report.update(completedAt=dt.datetime.now(dt.timezone.utc).isoformat(),
                      archivedFixtures=archived, disabledAccounts=len(accounts), cleanupErrors=cleanup_errors,
                      status='PASSED' if failure is None and not cleanup_errors else 'FAILED')
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print(f'{report["status"]}: {len(checks)} checks; archived {archived} fixtures; disabled {len(accounts)} accounts', flush=True)
    if failure:
        raise failure
    assert not cleanup_errors, cleanup_errors


if __name__ == '__main__':
    main()
