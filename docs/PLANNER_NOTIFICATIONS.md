# Personal study workspace

The `/study`, `/notes`, and `/notifications` student pages share the existing authenticated account and RU/KZ language. Flyway V20 adds only personal workspace tables; it does not rewrite attempts, grades, roles, publications, or enrollments. All API routes below require authentication. Every row is scoped to the current user, including mutations by UUID. Staff do not gain access to another person's notes.

## Profile and plan

Save a goal, selected ENT subjects, target date (today through 365 days ahead), ISO weekdays (1=Monday), 10–360 minutes per available day, and an IANA time zone. The default is `Asia/Almaty`. The profile's zone determines calendar boundaries, quiet hours, and repetition dates; stored task timestamps are absolute `timestamptz` values.

`POST /api/study/plan/recompute` uses real server data:

1. Assigned, accessible, unsubmitted assignments, ordered by deadline.
2. Existing error-review topics from `LearningRepository`.
3. Weak topics (existing server mastery below 70% after an attempt) and due personal cards.
4. Unread topic theory and unfinished enrolled course lessons.
5. Practice for selected subjects with active questions.

Every task includes a reason code rendered in both languages. No frontend mastery formula is introduced. This plan is a suggested allocation, not a prediction of exam readiness. A completed plan task is a personal checklist event; it does not mark theory read, complete a lesson, or fabricate a test attempt.

Task estimates are 10 minutes for a personal card, 15 for error review, 20 for topic theory/practice, 25 for a lesson, and 30 for an assignment. Automatic sessions start at 18:00 in the profile zone, using available weekdays and daily capacity. After 18:00, new automatic allocation starts the following day. Users can move tasks to any preferred time with labelled date/time inputs; no dragging is required. Estimates are not measured study time. Tasks are not split into fragments: a 20-minute task with a 10-minute daily budget stays unscheduled.

The deterministic sort uses priority, deadline, and stable source key. Recomputing unchanged inputs in the same planning day/window keeps task IDs and timestamps stable. A unique `(user_id, source_key)` prevents duplicates. A stable user-row lock serializes recompute and manual updates. Pinned, manually moved, completed, and skipped tasks are preserved; completed source keys are not regenerated. New automatic tasks avoid preserved occupied intervals. Manual choices remain authoritative even if they overlap or exceed the budget. Unscheduled tasks and a capacity warning are returned explicitly. Recompute removes only obsolete automatic planned tasks.

New/revoked publications, mastery, assignments, or card reviews affect suggestions on the next explicit recompute. The calendar's separate deadline query always reads current assignments, so a newly assigned deadline appears without recomputation. Calendar views are accessible dated agendas for today/week/month, with previous/next/date controls, 50 tasks per page, and a separate unscheduled filter. Assignment deadlines have their own 25-row pagination.

## Notes, bookmarks, and cards

Choose an accessible published topic or lesson through search, then save a private note. A bookmark is a note with `bookmarked=true` and may have an empty body. Notes are plain text; text is never evaluated or rendered as HTML. Titles/bodies are searched within the current account. A note keeps its original target and remains readable/editable after that target is archived or access is revoked. Its material link becomes unavailable; other users still cannot read it.

A note can have one optional two-sided personal card. Both sides must be supplied together. The student writes the question and answer using their note or checked learning material; no answers are generated or presented as verified automatically. Reveal the stored answer, then choose Again, Good, or Easy. The server schedules the next review at 18:00 in the profile zone: Again=1 day, Good=max(3, previous interval×2), capped at 60 days; Easy=max(7, previous interval×3), capped at 90 days. Editing a card resets its review schedule. Reviewing completes current plan tasks for that note. A later due review gets a new date-based source key. This schedule is a simple repetition aid, not a second course/mastery model.

The note editor guards unsaved navigation and browser unload; save conflicts keep the entered text. Deletes require confirmation and the latest revision. Deleting a note also removes its personal repetition tasks. Notes/cards are online-only and are not placed in a service-worker or browser storage cache by this feature.

## Notifications and worker

The in-app worker is enabled by default. Properties:

| Property                                   | Default |
| ------------------------------------------ | ------- |
| `app.study.notifications.enabled`          | `true`  |
| `app.study.notifications.interval-ms`      | `60000` |
| `app.study.notifications.initial-delay-ms` | `30000` |

Each pass handles up to 100 active accounts by UUID cursor. It catches per-user failures so another account can proceed, and revisits failed users on a subsequent sweep. A user's event writes run in a transaction; PostgreSQL uniqueness on `(user_id,event_key)` makes retries and concurrent instances safe.

Supported events: planned tasks within the previous/next day, unsubmitted assignment deadlines within the same window, grades published in the previous seven days, and published group lessons updated or newly joined in the previous seven days. Keys include task time, assignment deadline, grade revision, or lesson publication version respectively. Events use current publication, ancestor, enrollment, and assignment-group access checks before insertion. Opening an event checks access again and returns only a server-derived application URL; unavailable destinations return 404. Read/unread state remains personal.

Preferences independently enable plans/deadlines/grades/group materials, plus an overall switch and quiet hours. Quiet hours use the profile zone and can cross midnight; equal start/end means no quiet period. Default quiet hours are 22:00–08:00. During quiet hours the worker defers new events; existing events remain visible. The next eligible pass catches up within the stated lookback windows. There is no browser permission prompt, web push, email delivery, Firebase dependency, or external account.

This is polling with bounded lookback, not a durable event outbox: an outage or muted period exceeding the lookback is not guaranteed to replay every old event. A group-material reminder represents a published lesson/version, not every attached file change. Grade history itself stays in the teaching subsystem. Notification reads use the current submission projection; a revised grade yields a distinct event. Worker capacity and per-user query sizes should be monitored as enrollment grows; each active user's real candidate set is processed without silently truncating after the first 100 records.

## API

Bodies and responses use camelCase. `revision=0` creates a profile/preferences/note; subsequent edits use the returned revision. Conflicts return `409 REVISION_CONFLICT`, unavailable/foreign objects return 404, invalid profile/date windows return 400. Task updates require `revision>=1`. Shared frontend `request()` supplies timeout and session-expiry handling.

| Method and path                                                               | Contract                                                                                        |
| ----------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `GET /api/study/profile`                                                      | Stored profile or unsaved defaults with revision 0                                              |
| `PUT /api/study/profile`                                                      | `goal,targetDate,availableDays,minutesPerDay,timeZone,selectedSubjects,revision`                |
| `POST /api/study/plan/recompute`                                              | Returns `planned,preserved,unscheduled,requiredMinutes,capacityMinutes,capacityWarning`         |
| `GET /api/study/tasks?from=YYYY-MM-DD&to=YYYY-MM-DD&page=0&unscheduled=false` | Inclusive profile-zone date window, maximum 93-day span; task page plus total unscheduled count |
| `PATCH /api/study/tasks/{id}`                                                 | `revision` and one or more of `scheduledAt,pinned,status`; statuses `PLANNED/COMPLETED/SKIPPED` |
| `POST /api/study/tasks/{id}/open`                                             | Rechecks destination access; returns `{url}`                                                    |
| `GET /api/study/deadlines?from=…&to=…&page=0`                                 | Current accessible assignments in date window; includes deadline, submitted flag, safe URL      |
| `GET /api/study/notes?q=&bookmarked=false&due=false&page=0`                   | Private searched 25-row page                                                                    |
| `GET /api/study/notes/{id}`                                                   | Private note and current material availability                                                  |
| `PUT /api/study/notes/{clientGeneratedUUID}`                                  | `targetKind` TOPIC/LESSON, `targetId,title,body,bookmarked,cardFront,cardBack,revision`         |
| `DELETE /api/study/notes/{id}?revision=N`                                     | Removes note/card/bookmark and its repetition tasks                                             |
| `POST /api/study/notes/{id}/review`                                           | `{rating: AGAIN/GOOD/EASY,revision}`; updated note                                              |
| `GET/PUT /api/study/notifications/preferences`                                | `enabled,plans,deadlines,grades,materials,quietEnabled,quietStart,quietEnd,revision`            |
| `GET /api/study/notifications?page=0&unread=false`                            | 25-row page, total and unread counts, current availability                                      |
| `PATCH /api/study/notifications/{id}`                                         | `{read: boolean}`                                                                               |
| `POST /api/study/notifications/{id}/open`                                     | Rechecks access, marks read, returns `{url}`                                                    |

Notes permit 300-character titles, 20,000-character bodies, and 4,000 characters on each card side. Query terms are limited to 200 characters. Profile subject lists have at most 30 IDs, validated against available subjects. Future/past manual timestamps are limited to 730 days from now. A manually moved task cannot be returned to automatic scheduling in this version; edits/completion remain available. ICS export is not implemented.

## Verification boundary

`StudyIntegrationTests` exercises real PostgreSQL/Flyway, invalid/stale profiles, deterministic plan preservation, private-note isolation, archive retention, versioned reviews, notification deduplication/quiet hours/current access, and group/enrollment-dependent deadlines. `frontend/src/features/study/study.test.tsx` covers time-zone/DST conversion, keyboard-operable transfer controls, unsaved/conflicting notes, stored flashcard answers, and preferences. Integrated browser/axe and whole-repository regression results belong in the release verification report; this document does not claim those checks from unit/integration coverage alone.
