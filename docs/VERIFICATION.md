# Verification and handoff record

The expansion release is recorded first. Phase 1 and Phase 2 sections below are historical baseline evidence; their deferred-feature lists do not describe the current expansion.

## Unified expansion — 2026-10-04

Branch `codex/education-content-experience` extends Phase 2 commit `e39293b3d9a044006a8768698dccffbaaa1b6234`, whose PR #15 remains open. The release PR targets `codex/education-platform-phase-2`; neither PR is automatically merged. Exact release SHA and final CI are recorded in the PR and final handoff, separately from earlier baseline runs.

| Command / evidence | Local result |
|---|---|
| `backend/mvnw.cmd -q -f backend/pom.xml verify` with JDK 21 | 58 backend tests, zero failures/errors/skips; real PostgreSQL/Testcontainers, legacy migration upgrade, concurrency, ownership, scoring, imports, S3 and scanner tests |
| `npm --prefix frontend run typecheck` | Pass |
| `npm --prefix frontend run lint` | Pass, zero warnings |
| `npm --prefix frontend test` | 45 frontend tests pass, including RU/KZ safe assignment description rendering |
| `docker compose --profile app build` | Both production images pass; final frontend rebuilt after readability repair |
| `docker compose --profile app --profile malware up -d --build --wait --wait-timeout 300` | Nginx, backend, PostgreSQL and real ClamAV healthy |
| `python scripts/validate_content.py --verify-cache` | All 480 questions structurally checked; 147 pilot/core proof checks, 112 model sample checks, 12 Python examples and 8 boundary cases; 77 cached NCT source hashes match; zero network requests |
| `python scripts/browser_tests.py` with `BROWSER_BASE_URL=http://127.0.0.1:8081` | 28 original student browser checkpoints pass |
| `python scripts/phase2_browser_tests.py` | 68 CMS/LMS browser checkpoints pass; four roles, ENT publication, courses, PDF upload/download, enrollment, group, assignment, grading, quiz, mastery/error review, CSV/JSON imports and invalid imports |
| `python scripts/content_browser_tests.py` | 18 real subject journeys / 180 UI answers / 18 completed attempts; 175 single, 3 multiple, 2 matching and 10 shared-context answers; no keys exposed in 180 question responses; 22 axe/overflow inspections including RU/KZ KaTeX/table/code and keyboard scrolling |
| `python scripts/expansion_browser_tests.py` | 29 UI checkpoints, zero unexpected errors; includes actual scheduled grade reminder and mass REVIEW/PUBLISHED confirmation |
| `python scripts/course_content_browser_tests.py` | 16 local course checkpoints, 30 UI quiz answers, six-of-six lesson completion, assigned response and exact protected-PDF SHA; CI omits the licensed external PDF and reports that optional check as absent, retaining the other 15 checkpoints |
| `python scripts/content_pack_safety_tests.py` | 31 live API checks: atomic failure, resume, idempotence, teacher-edit conflict, both explicit resolutions, stale versions, audit and unchanged published snapshots |
| `python scripts/pwa_update_tests.py` | Actual service-worker update scenarios pass: active attempt blocks update, refresh retains deadline/session and waiting worker, second tab blocks activation, explicit safe single-tab activation reloads |
| `python scripts/measure_ux.py` | 72 cold/warm navigations against exact Phase 2 and current frontend with the same API dataset; see [UX_VERIFICATION.md](UX_VERIFICATION.md) for raw denominators/tradeoffs |

On Windows run the Python commands with `.venv/Scripts/python.exe -X utf8`; on Linux use `.venv/bin/python`. Browser dependencies and Chromium are installed from `scripts/requirements-browser.txt`. Run the browser suites only on a local/isolated database: they register disposable accounts, use local operator SQL solely to provision test roles, and perform actual content/learning operations through HTTP/UI. Screenshots are committed under `docs/screenshots/expansion`; local traces and backups stay ignored. The CI workflow uses offline structural validation and safe original PDF fixtures; it does not download books or call NCT websites.

The expansion suite additionally covers versioned pack/source UI, mass review/publication preview and confirmation, deterministic planner creation/move preservation, private notes/bookmarks/cards, CLEAN file upload, real teacher grade and scheduled grade reminder, resubmission grade reset, explicit offline public reading and timed training refresh. The dedicated Python course suite covers all six lessons, all thirty quiz answers, explicit six-of-six completion, a real assigned text response and the protected licensed PDF when installed locally. Their final checkpoint counts are in the release handoff.

Viewports: 320/390/768/1024/1440. Reduced motion, long RU/KZ text, responsive CMS hierarchy/editor/preview, groups/courses, planner/practice and safe rich content were exercised. Axe found no violations in tested surfaces; this is not full accessibility certification. No unexpected browser/console/network errors remained in passing runs.

The independent [security review](EXPANSION_SECURITY_REVIEW.md) closed editorial answer-evidence leakage, pack locking/conflicts, context withdrawal, stale session/deadline races and legacy assessment compatibility. No unresolved P0/P1 findings remain. Source content is untrusted; safe renderers never execute imported HTML. Student attachments use real ClamAV, revision-specific grades and protected downloads.

[CONTENT_RELEASE_REPORT.md](CONTENT_RELEASE_REPORT.md) records actual publication, file hashes, repeat/resume and preservation of 51 old users / 26 unchanged old attempts / 17 old Flyway checksums. [PRODUCTION_CHECKLIST.md](PRODUCTION_CHECKLIST.md) identifies deployment work not claimed as completed. Full ENT content and human subject approval remain incomplete; working software is not evidence of a complete or expert-reviewed exam bank.

## Historical Phase 1 baseline

Verified locally on 2026-10-04, Windows PowerShell, JDK 21.0.9, Node 25.8.0, Docker Desktop, PostgreSQL 17. Production containers use Java 21 and Node 24 for the frontend build. CI uses Node 24 and Java 21 on Ubuntu.

## Executed

| Check | Result |
| --- | --- |
| `backend/mvnw.cmd -f backend/pom.xml -q verify` | 8 integration tests passed, 0 skipped; real isolated PostgreSQL via Testcontainers |
| `npm ci` | Clean dependency installation passed |
| `npm run typecheck` | Strict TypeScript passed |
| `npm run lint` | ESLint passed without warnings |
| `npm test` | 9 component/integration tests passed |
| `npm run build` | Production bundle passed; self-hosted fonts included |
| `npm audit --omit=dev` | No reported runtime vulnerabilities at verification time |
| `docker compose up -d --wait postgres` | PostgreSQL healthy on local port 55432 |
| `docker compose --profile app build` | Both production images built |
| `docker compose --profile app up -d --wait` | PostgreSQL, backend and Nginx frontend healthy |
| `GET http://localhost:8081/api/test/ping` | `pong`, through Nginx |
| `GET http://localhost:8081/statistics` | SPA fallback works; CSP, frame, nosniff, referrer and no-cache headers verified |
| `scripts/setup.ps1` | Generates random secrets outside repo; preserves existing `.env` |
| Python Playwright against `http://127.0.0.1:8081` | 28 browser checkpoints passed |

The first development-browser runs used the reviewed `webapp-testing/scripts/with_server.py` helper to launch the Java JAR and Vite. Final regression used the running production Docker stack. The helper was read and its `--help` inspected before use. No hidden mock API supplies normal-flow data.

## Browser coverage

Fresh registration in Kazakh; language persistence; logout/login; protected-page refresh; subjects/topics; multiple theory blocks; keyboard radio selection; complete test; exit confirmation/cancel; server-side resume; test refresh; result review; real statistics; Kazakh results; settings; direct routes; 1440/768/390/320 px layouts; early finish and unanswered review; HTTP 500 with retry; malformed successful response; empty catalogue; offline request; 404; expired session and cleared token.

Normal-flow console errors, uncaught exceptions, unexpected HTTP failures and broken requests fail the script. Deliberately injected failure responses are scoped separately. Axe checks WCAG 2 A/AA and 2.1 AA rules on the documented major screens, including rendered test questions. No violations were returned in the final run. This is automated coverage, not a claim of exhaustive accessibility certification.

The report is written to ignored `test-results/browser-report.json`; screenshots retained in `docs/screenshots/` are documentation artifacts. CSS also respects reduced motion. Tests use role/label selectors and assert no horizontal overflow.

## Critical review and fixes

The invoked `grilling` instructions required factual investigation rather than guessing. A read-only audit sub-agent inspected security, concurrency, schema, API, UI and deployment. No P0/P1 remained; these concrete P2 issues were fixed and regression checked:

| Finding | Fix / evidence |
| --- | --- |
| Stale language PATCH could undo logout; stale 401 could clear a new token | Captured-token checks, serialized language updates; 2 targeted frontend tests |
| Topic moves left questions/theories in the old subject | Atomic propagation of parent and denormalized titles; API integration assertions |
| Old attempt reviews depended on mutable question rows | V15 backfill in original question order; isolated V12→latest upgrade test |
| Multibyte passwords could exceed BCrypt's byte limit | Server UTF-8 byte validation on both auth endpoints; regression assertions |
| Requests could wait indefinitely | 15-second abort deadline covers fetch and response body |
| Nginx location overrode security-header inheritance | Replaced location `add_header` with `expires -1`; real header checks |
| Mobile account lacked logout | Logout added to settings |
| Topic numbers had insufficient contrast | Darkened token, reran axe |
| Duplicate answer writes could race | Session row lock and idempotent receipts; 4 simultaneous retries persist one answer |

Original audit issues (pre-finish answer leakage, disabled accounts, hardcoded credentials, historical seeded admin, mismatched DTOs, fake phone shell, dead auth actions and missing statistics) are documented in [AUDIT.md](AUDIT.md) and addressed in the implementation.

## Skills actually applied

- **frontend-design**: read before implementation; created and critiqued `DESIGN.md`, defined palette/type/layout, built custom components, then inspected real screenshots and corrected contrast.
- **webapp-testing**: read before browser work; used native Python Playwright and the inspected server helper, collected screenshots, checked actual user flows and failures against PostgreSQL.
- **grill-me / grilling**: `grill-me` is a wrapper requesting `grilling`; both instruction files were read. `grilling` is an interview skill, not an automated code-review engine. Its factual-investigation step drove the audit sub-agent; its product-scope question was answered by the user: deliver the platform on current content, not a complete course. No further unresolved product decisions remain for this scope.
- **handoff**: final continuity note is saved to the OS temporary directory, outside the repository, referencing this document and the final commits rather than duplicating them.

## Scope retained

Current learning inventory: 3 subjects, 4 topics, 6 questions, 5 theory blocks. At that baseline, Admin CRUD was integration-tested and documented without an admin UI. Phase 2 below adds the UI. Password reset/email verification and public hosting are not implemented. JWT logout is client-side. Before internet exposure, configure HTTPS, auth rate limits, monitoring and database backups. These are explicit scope boundaries, not placeholder controls in the student UI.

GitHub workflow results must be checked after push; local test success alone does not certify the remote runner.


## Phase 2 verification — 2026-10-04

Baseline compatibility was checked before changes and after implementation. V1–V15 are unchanged. Work is on `codex/education-platform-phase-2`; main is untouched.

| Exact command (repository root unless noted) | Local result |
| --- | --- |
| `backend/mvnw.cmd -f backend/pom.xml verify` with JAVA_HOME pointing at JDK 21 | 26 tests passed: 8 baseline, 13 platform PostgreSQL integration, 3 validation, 2 storage; zero skipped |
| `npm run typecheck` (frontend) | Passed |
| `npm run lint` (frontend) | Passed |
| `npm test` (frontend) | 15 passed: 9 baseline + 6 platform |
| `npm run build` (frontend) | Passed; cached vendor chunk 447.91 kB / 138.92 kB gzip, application entry 73.33 kB / 21.46 kB gzip; CMS/LMS pages split into small lazy chunks |
| `docker compose --profile app up -d --build --wait --wait-timeout 180` | Backend/frontend images built; all three services healthy |
| `.venv/Scripts/python.exe scripts/browser_tests.py` with BROWSER_BASE_URL=http://127.0.0.1:8081 | All 28 baseline checkpoints passed; no unexpected browser/network errors |
| `.venv/Scripts/python.exe scripts/phase2_browser_tests.py` | All 57 Phase 2 checkpoints passed; no unexpected browser/network errors |
| `git diff --check` | Passed |

On Linux/CI use `.venv/bin/python` and `cd backend && ./mvnw -B verify`. The existing three-job workflow now includes both browser suites; no extra slow workflow was added. Remote results are available under [GitHub Actions](https://github.com/alinur527/education-app/actions); local success is not a substitute for checking the PR's final run.

### Actual browser journeys

- ADMIN login → subject → topic → block theory → bilingual question → review/publish.
- TEACHER login → public course → manual enrollment/cancellation → module → lesson draft → PDF upload → publish → assignment/deadline → lesson quiz → group → student membership → assign task.
- STUDENT → new ENT topic → mark theory read → wrong practice answer → result → error review → correct answer → empty error list → real mastery.
- STUDENT → enrolled course outline → lesson → authorized PDF download (bytes compared) → text assignment submission → lesson quiz/result → completed course progress.
- TEACHER → grade submission → group shows real 100% lesson progress and submission count.
- CONTENT_EDITOR → workspace access with no user-management navigation. ADMIN → search user → change role and restore it.
- ADMIN → valid JSON preview/confirm → open draft and publish; repeat CSV; invalid correctOptionId and malformed row types retain line-specific errors and cannot confirm.
- Draft navigation confirmation, revision409 preservation and network-failure preservation.

Axe WCAG 2 A/AA + 2.1 AA ran on the main new screens, dialogs, forms, import errors, course/quiz and group views. Course, group and CMS views were inspected at 1440, 1024, 768, 390 and 320 px (the desktop teacher screenshot is 1440). The baseline suite also exercises mobile practice and failure states. Screenshots are in [screenshots/phase2](screenshots/phase2). These automated checks do not claim a complete assistive-technology audit.

The script provisions unique role fixtures through local-only Docker SQL, then authors content through real UI. Successful runs archive their disposable content; users/audit/attempt history remain. No public bootstrap API or default credentials are introduced.

### Backend coverage and review repairs

Checked roles/public escalation, current-role JWTs, ownership, drafts, archive/restore, stale revisions, groups, manual/self/group enrollment, cancellation, simultaneous group removals, simultaneous lesson completion, assignment access and grading conflicts, quiz snapshots/answer secrecy, private lesson and course attachments, MIME/name/traversal guards, JSON/CSV validation/idempotence, mastery/deduplication and migration upgrade with old users/attempts.

Storage tests perform a real signed S3 PUT/GET/DELETE against disposable digest-pinned RustFS, including rejection of a wrong secret. The previously available MinIO images returned registry errors; that failed attempt is not counted as passing interoperability. DOCX/PPTX archive structure, macros/traversal, executable/MIME rejection and size limits are tested directly. Browser upload/download coverage uses PDF.

The independent adversarial review found and drove fixes for archive resurrection, legacy draft-block publication, stale grades, concurrent enrollment revocation, max-score changes after submissions, nullable import contracts, prototype-like invalid kind values, duplicate CSV headers, numeric overflow, dirty-form navigation and missing enrollment on course attachments. Published content/list queries no longer load full bodies for CMS indexes. No unresolved P0/P1 remained at the final source review; relevant P2 defects were repaired before regression.

### Scope and skills

P0 CMS/LMS/files/import/roles and P1 mastery/error-review/analytics are implemented. P2 planner/calendar/in-app reminders/bookmarks/notes, Mixed Practice, the official mock exam and full ENT content are deferred. Lesson completion is self-reported, text assignments are teacher-graded, deadlines do not reject late answers, and the scanner interface is a no-op by default. These limits are described in [PLATFORM.md](PLATFORM.md) and [STORAGE.md](STORAGE.md).

Skills applied this phase: frontend-design (prior design plan + screenshot critique), webapp-testing (native Python Playwright + rendered-DOM reconnaissance + actual browser runs), grill-me/grilling (brief's settled scope and independent adversarial factual review). The handoff skill is applied only after delivery, saving an OS-temp continuity note with final PR/SHA references.
