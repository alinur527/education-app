# Phase 2 implementation record

Baseline: `28e822995620b5166c0bbca3745b16a0e4911f8b`. Work branch: `codex/education-platform-phase-2`.

Before changes: clean Git tree; 8 PostgreSQL integration tests, 9 frontend tests, typecheck, lint, build, and 28 production-browser checkpoints passed on 2026-10-04. The first sandboxed Maven invocation could not reach Central; the unrestricted rerun passed. Historical V1–V15 stay immutable.

## Architecture decisions

- Keep ENT tables and contracts. Add separate course/module/lesson tables. Shared editorial records carry drafts, immutable published payloads, revision numbers, ownership and source metadata. Draft edits preserve the last published student version; archive withdraws it. Publication synchronizes existing ENT columns atomically.
- Teacher ownership follows the course hierarchy. Teachers manage their own courses/groups; editors manage content; administrators manage accounts. Public registration always creates STUDENT. Services enforce authorization independently of navigation.
- Files live behind authorized IDs through local/S3 storage, never public upload paths. Attachments inherit access from their parent material. Imports preview a validated, bounded batch then commit all drafts in one transaction.
- Existing session snapshots remain immutable. Mastery and error review use completed attempts, including unanswered questions, with an explicitly documented formula.
- P0 CMS/LMS/files/import/security and P1 learning feedback take priority. P2 scheduling/notes will be assessed only after these are complete.

## Design plan (frontend-design)

Preserve Golos Text and the established palette: ink `#172544`, secondary text `#53627a`, action blue `#315ae8`, canvas `#f4f6fa`, white `#ffffff`, divider `#e3e8f0`. Add restrained green for publication and amber for review; status always includes text.

The teacher workspace is an editorial desk: a searchable content outline at left, a spacious bilingual editor in the center, and publication/preview actions above it. Tables use real hierarchy, compact rows and explicit status. Student course pages use an ordered lesson rail and a readable content column. Alignment stays left; long prose stays below 80 characters.

```
Workspace navigation | Search + type/status filters
                     | Content rows: title / parent / status / revision
                     | Editor: RU + KZ / blocks / attachments
                     | Save draft   Preview   Review   Publish
```

Critique before implementation: a generic dashboard of KPI tiles would obscure the teacher's main task. Use the actual course/topic hierarchy and an editor instead. Keep the original student shell; introduce denser tables only in staff surfaces. On mobile, controls wrap and forms become one column; tables scroll within labeled regions rather than overflowing the viewport. Save explicitly, preserve entered values after failed requests, and show revision conflicts without silently overwriting another editor.

## Product references inspected

- [Personalized Academic Tracker](https://github.com/nst-sdc/Personalized-Academic-Tracker): deadline-focused activity and progress summaries; its README marks external calendar sync as future work.
- [Academico](https://github.com/academico-sis/academico): separate course/enrollment management and practical staff workflows. No PHP stack or source code is imported.
- [UniStudents](https://github.com/UniStudents/unistudents-app): compact mobile academic progress and understandable charts; preserving forms on failure is relevant here, without claiming offline synchronization.
- [CollegeCGPAios](https://github.com/AbGhost-cyber/CollegeCGPAios): focused progress presentation with a small set of useful numbers. No SwiftUI or GPA model is imported.

These are product references, not verified feature dependencies. The implementation stays React/TypeScript, Spring Boot and PostgreSQL.
