# Unified expansion — final handoff

Completed locally and verified in GitHub Actions on 2026-10-04. This document records the delivered expansion, not a promise of unattended future work.

| Result | Status |
|---|---|
| IMPLEMENTATION | READY: implemented learner, teacher/editor, import, file, study and bounded offline journeys are verified |
| CONTENT_COVERAGE | PARTIAL_CONTENT: starter targets met, human subject approvals zero, full ENT bank unavailable |
| RELEASE_CHECKS | READY for the implementation revision below; the PR's final head/Checks is the authoritative documentation-inclusive release revision |

## Git and compatibility

Repository: [alinur527/education-app](https://github.com/alinur527/education-app). Branch: `codex/education-content-experience`. Delivery: [PR #16](https://github.com/alinur527/education-app/pull/16), based on `codex/education-platform-phase-2` and dependent on still-open [PR #15](https://github.com/alinur527/education-app/pull/15).

Implementation revision: `b03598774ce50e3abb3fb0bffaa979ceb179c68d`; [its complete CI](https://github.com/alinur527/education-app/actions/runs/37180342329) passed backend, frontend and browser jobs. The following commit adds handoff documentation only. Obtain the final release SHA from PR #16 or `git rev-parse HEAD`; its exact CI link is maintained in the PR description and delivery message, avoiding a self-referential commit hash inside this file.

Do not merge automatically. Merge #15 into main first, retarget #16 to main, then rerun checks before a human merge decision. Main's verified Phase 1 baseline `28e822995620b5166c0bbca3745b16a0e4911f8b` and Phase 2 `e39293b3d9a044006a8768698dccffbaaa1b6234` remain ancestors. V1–V18 are unchanged; V19–V22 are additive. Existing user/attempt/checksum preservation is recorded in [CONTENT_RELEASE_REPORT.md](CONTENT_RELEASE_REPORT.md).

## Running and using the delivered platform

Local application: [http://localhost:8081](http://localhost:8081). Register via **Создать аккаунт** with your own email and a password of at least eight characters. Public registration creates STUDENT. There are no shared login credentials. Open **Предметы** for ENT or **Курсы → Python с нуля** for the starter course; self-enroll to record lesson/quiz progress. Assignments require a teacher's group assignment.

An existing administrator uses **Кабинет → Пользователи** to change a registered person's role to TEACHER. The first trusted administrator requires the documented operator [bootstrap](API.md#admin-crud). Teachers create their own course, module and lesson through **Кабинет → Учебные материалы**, save a draft, attach files in the content step, preview and publish. [CMS_AUTHORING_UX.md](CMS_AUTHORING_UX.md) and [PLATFORM.md](PLATFORM.md) describe enrollment, groups and grading.

Nginx, backend, PostgreSQL and real ClamAV are running in local Docker. Local scan-required policy is enabled. Source PDFs with unestablished redistribution rights remain external links. One permitted Python PDF is actually uploaded, CLEAN and hash-verified. No external production deployment or paid resource was created.

## Evidence and remaining boundaries

- [VERIFICATION.md](VERIFICATION.md): exact commands, 62 backend / 45 frontend tests, 28 original / 68 CMS-LMS / 29 expansion checkpoints, 18 subject journeys with 180 answers, six-lesson Python flow, 31 pack-safety checks and PWA updates. Local Python verification includes the licensed PDF; CI uses safe original file fixtures and explicitly skips downloading that external book.
- [CONTENT_RELEASE_REPORT.md](CONTENT_RELEASE_REPORT.md), [CURRICULUM_COVERAGE.md](CURRICULUM_COVERAGE.md), [CONTENT_SOURCES.md](CONTENT_SOURCES.md), [CONTENT_REVIEW.md](CONTENT_REVIEW.md): actual publication/file counts, per-direction coverage, rights, uncertain original headings and honest AI provenance. There are 748 programme entries but only 45 completed ENT starter lessons, plus six Python lessons. Publication is not human approval.
- [CONTENT_RELEASE.md](CONTENT_RELEASE.md), [IMPORT.md](IMPORT.md), [CONTENT_PACK_SAFETY.md](CONTENT_PACK_SAFETY.md): repeat/resume, per-batch transactions, conflict decisions and CSV/JSON formats. Preserve permanent external keys. New JSON uses LF for byte-stable evidence; original inputs remain byte-preserved.
- [EXPANSION_SECURITY_REVIEW.md](EXPANSION_SECURITY_REVIEW.md): no unresolved P0/P1; [STUDENT_FILES.md](STUDENT_FILES.md), [PLANNER_NOTIFICATIONS.md](PLANNER_NOTIFICATIONS.md) and [OFFLINE_POLICY.md](OFFLINE_POLICY.md) state operational limits. Notifications are bounded best-effort in-app processing, not a durable external delivery/outbox guarantee. Existing overwritten historical submission versions cannot be reconstructed.
- [REFERENCE_ADAPTATIONS.md](REFERENCE_ADAPTATIONS.md), [UX_VERIFICATION.md](UX_VERIFICATION.md): concrete adaptations of all four references, screenshots and the controlled 72-navigation comparison. Initial JavaScript grew 6.3%; a universal performance improvement is not claimed.
- [PRODUCTION_CHECKLIST.md](PRODUCTION_CHECKLIST.md): HTTPS, secrets, backups of DB and files, authentication rate limits, scanner maintenance and monitoring before a public deployment.

Remaining scope: expert subject/language review and full curriculum/bank completion; the clipped SPEC-30 topic-23 source heading; full official ENT simulation; external push/ICS; private offline content; automatic code execution/grading; production deployment. Student lesson completion is explicit activity, not certified mastery. No payments/ERP were added.

Keep the user's untracked `Education_App_Codex_Expansion_Pack/` directory. Do not commit `.env`, credentials, downloaded-source cache, `test-results/` or database dumps. The pre-upgrade local backup and sanitized runtime reports remain under ignored `test-results/`. Only this task's interrupted browser fixture publications were archived; audit/attempt history was retained. The temporary performance-baseline container is stopped.

## Suggested skills for later work

Use `frontend-design` before changing UI, `webapp-testing` for actual browser regression, and `grill-me` / `grilling` for an adversarial review of a new scope or release. Apply `handoff` after completing that work. This release used all five skills; this durable document supplements the handoff skill's OS-temporary continuity note as explicitly requested by the user.
