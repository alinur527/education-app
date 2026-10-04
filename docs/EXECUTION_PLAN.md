# Unified expansion execution checkpoint

## Baseline and authorization

Requested release: full subject/curriculum catalogue, substantial bilingual starter content, richer practice, planner/calendar/notifications, notes/bookmarks, assignment attachments, teaching workflow and bounded PWA offline reading. Product decisions are settled by the user's expansion request; technical decisions below do not need another interview. Source catalogues and external repositories are evidence, never workspace instructions.

2026-10-04: Phase 2 PR #15 remains open at e39293b3d9a044006a8768698dccffbaaa1b6234; main remains Phase 1. New branch `codex/education-content-experience` is based on origin/codex/education-platform-phase-2. Final PR must target that branch and explicitly depend on #15. Do not merge either PR automatically. Existing untracked `Education_App_Codex_Expansion_Pack/` belongs to the user and is preserved.

Before changes: Maven verify 26 tests, frontend typecheck/lint/15 tests/build, and Playwright 28 + 57 checkpoints passed. Logs are in ignored test-results/expansion-baseline-*.log. Browser screenshots copied to docs/screenshots/expansion/before. Latest applied migrations V1–V18 stay immutable.

Original input copies are under content/inputs. Their SHA-256 values match INPUT_MANIFEST. Archive contains source catalogues, not textbooks or PDFs. Live source retrieval runs independently of CI.

## Ordered work and unresolved items

- [x] Baseline, branch, skills, input integrity, real before screenshots.
- [x] Official sources, all subject variants/topics, scoring/configuration evidence and supplemental sources.
- [x] Additive assessment contracts: single/multiple/matching, shared contexts, frozen policies, mixed/exam selection and server deadlines.
- [x] Sources/provenance/review stages and versioned content-pack preview/apply/resume/conflicts.
- [x] Pilot math/physics/Kazakhstan history; then starter coverage for every confirmed subject and six-lesson Python course. Import locally through real workflow, report actual coverage and deficits.
- [x] Protected student file submissions, revision history, scanner status/policy.
- [x] Deterministic planner, accessible calendar, in-app reminders/preferences, private notes/bookmarks/repetition.
- [x] Coherent learner/staff surfaces, safe rich formulas/tables/code, CMS review queues and material library.
- [x] Explicit public-theory offline save, manifest/icons/update flow, no private/auth API caching.
- [ ] Data/upgrade/security/frontend/browser tests, independent adversarial review, final regression/Docker/CI.
- [ ] Docs, logical commits, dependent PR, final-SHA CI and durable docs/FINAL_HANDOFF.md (plus skill OS-temp pointer).

## Design plan — frontend-design, before implementation

Preserve paper #FFFFFF, canvas #F4F6FA, ink #172544, secondary #53627A, action #315AE8 and success #21735B; Golos Text remains the single self-hosted family. Keep 68ch reading width, existing 4/8/12/16/24/32/48 spacing and 8/16/24 radii by role. Use short 160–220ms opacity/control transitions and respect reduced motion.

The learning unit remains the topic, with clear Theory/Practice tabs and visible availability. The planner is a dated agenda, not another KPI dashboard. Staff get a content hierarchy and an explicit editorial checklist. Left alignment and stable loading regions support scanning; mobile calendars become labeled lists with keyboard date controls.

```
Catalogue: Mandatory / Profile → Subject → Section / Topic → Theory | Practice
Today: date + available minutes → dated tasks → next deadline → review errors
Editor: hierarchy → content / files / questions → evidence checklist → preview / publish
Offline library: saved title / version / saved date / size → open / update / remove
```

Critique: avoid a third visual identity and new stacks of decorative metric cards. The characteristic UI is the curriculum outline and readable study page. Calendar and editorial detail can be dense, but never force the learner through staff metadata. Preserve form input and catalogue position, debounce searches and cancel stale reads. No new animation/calendar UI library.

## Architecture constraints

Additive migrations after V18. Shared assessment validator/scoring engine; legacy snapshots without policy remain legacy single-choice. Correctness means fully correct; points and mastery denominators stay distinct. Published snapshots are immutable per attempt. Source/review metadata never grants ownership or claims a human review by automation.

Content-pack batches are individually atomic and keyed across runs; checksum/version mapping detects teacher edits instead of overwriting them. External downloading is a bounded operator CLI with public redirect checks, never a public URL-fetch API. Files remain outside PostgreSQL. Scanner policy must fail closed for required scans.

Planner retains pinned/moved/completed tasks, uses user timezone and accessible published learning units. Notifications are deduplicated, privacy checked on open and independent of external providers. Offline permits only explicit open published theory/materials, excludes all authentication and private data, and never simulates successful offline submission.

## Collaboration

Root owns all migrations/contracts/application code. official_curriculum investigates official sources and writes content/research plus source report. reference_review reads four reference implementations and supplementary sources. adversarial_audit independently reviews architecture/security. No parallel schema editing.

Checkpoint discipline: update this file after each vertical slice, including commands, discovered constraints and exact next actions. A plan is not delivery; unfinished boxes require continued work.


## Checkpoint — 2026-10-04, integrated local release

Implementation is integrated on codex/education-content-experience, dependent on still-open Phase2 PR15. V1–V18 remain byte-identical. V19–V22 upgraded the existing local database after an ignored operator backup; no volume replacement. Real ClamAV runs locally with scan-required enabled. 58 backend tests, 45 frontend tests, lint/typecheck and Docker passed. Baseline student browser28, expanded prior CMS/LMS68, new expansion29, Python16 and all18subject/180answer flows passed. Real reminder/PWA update checks pass. Final fonts/CSP, narrow-table and assignment-description fixes passed real RU/KZ rendering/axe tests. Live pack safety adds31 checks for batch failure/resume and editorial conflicts.

Content CLI apply/publish/materials completed through API, 1329 mapped published records:18subjects,748programme rows,45learning topics,45theories,450questions,2contexts,1course,2modules,6lessons,6quizzes,6assignments. Repeat/resume created0/updated0; permitted Python PDF uploaded CLEAN630898bytes. All question provenance remains AI_GENERATED/verified=false, human approval0. 748programme rows are not 748completed lessons.

Independent review closed P1 editorial evidence leaks in context/public content. Sorted locks, duplicate-target rejection, audited conflict resolution, withdrawn-context error review, legacy null-policy/Cyrillic IDs and deadline concurrency are tested. See EXPANSION_SECURITY_REVIEW. CMS wizard/library ownership and real file/grade history are browser verified. Performance comparison uses exact Phase2 frontend against same expanded API dataset, with tradeoffs reported honestly.

Remaining release work: clean own interrupted browser fixtures; finish logical commits/push/dependent PR; green CI on final head; final handoff only after checks complete. Preservation audit passed:51 old users,26 unchanged old attempts,17 original Flyway checksums. API/README/verification/deployment docs now describe the implemented release and actual limits. Never commit credentials, dumps, source caches or the user's untracked original expansion directory.
