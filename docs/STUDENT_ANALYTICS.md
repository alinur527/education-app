# Student Analytics 2.0

## Design plan, reviewed before implementation

Audience: a Kazakhstan student deciding what to practise next. Retain Golos Text and the existing palette: ink #172544, secondary #53627A, action #315AE8, canvas #F4F6FA, paper #FFFFFF, success #21735B. Left-aligned learning log, a compact summary band, two charts, subject evidence, mastery recommendations, then dated activity. The 12-week study calendar is the distinctive visual element; avoid a wall of interchangeable KPI cards. Mobile stacks sections; charts scale inside their containers. Tables expose the same data without relying on colour. No chart dependency or decorative animation.

Reviewed against the brief: this is a study record, not a business dashboard. Evidence and next learning actions take priority over large headline numbers. Preserve the existing shell and staff routes.

## Contract and formulas

The legacy `GET /api/statistics/me` response stays compatible. New endpoints are documented in API.md. Analytics use an injected UTC Clock and the student's study profile timezone, default Asia/Almaty. Periods include today and the preceding 6 or 29 local dates; the prior period is the immediately preceding 7 or 30 dates. Cutoffs are local midnight converted to instants; future events after the Clock instant are excluded.

Question and test metrics describe completed ENT practice sessions (topic, mixed, error review, mock); lesson quizzes are currently separate. `questionsAnswered` is the number of snapshot questions in those sessions, including unanswered questions. `fullyCorrectAnswers` counts fully correct outcomes. Accuracy is `100 * fullyCorrectAnswers / questionsAnswered`; zero denominator returns null. Points percentage is `100 * sum(earned_points) / sum(max_points)`; partial credit contributes points but not fully correct outcomes. Test time sums session time once per completed session, and is not study time.

Study actions are completed practice sessions, first marked theory reads, completed lessons, completed planner tasks and assignment submission revisions. Error resolutions are false-to-true transitions in a question's completed practice history; they are shown separately and do not double-count study actions. Ties use session UUID, matching existing error-review ordering. History shows real stored events, not every page opening. Assignment summary counts submission revisions, including resubmissions. Completion projections (theory, lesson, task) preserve one current completion event; reopening a task removes its completion and completing it again establishes a new date. Previously overwritten theory timestamps cannot be reconstructed. V23 backfills completed task timestamps from their last recorded update.

V24 adds `last_read_at` separately so rereading still updates Continue learning without moving completion evidence. Legacy practice and theory columns are UTC wall times in the verified Docker baseline (`hibernate.jdbc.time_zone: UTC`); SQL explicitly interprets them as UTC before converting to the profile timezone. Named zones retain their daylight-saving rules; Java fixed offsets are translated to PostgreSQL's inverse-sign POSIX syntax. The UI formats fixed offsets without passing unsupported Java aliases to Intl.

Active days count local dates with at least one study action. Current streak is consecutive active dates ending today, or yesterday if today is not active; it uses all history, independently of selected period. Comparison count deltas are absolute counts, accuracy and points deltas are percentage points. If the previous period has no study activity comparison is unavailable; missing question evidence produces null percentage deltas.

Mastery is reused unchanged: recent accuracy is fully correct questions divided by all questions in the latest three topic attempts; with theory it contributes 80%, and the read-theory fraction contributes 20%. Without theory, mastery equals recent accuracy. Weak topics have attempts and mastery below 70. Strong topics require mastery at least 80, at least three attempts and at least ten distinct snapshot questions, preventing a single repeated question from establishing strength. Recommendations use lifetime evidence and are labelled accordingly.

Daily buckets include empty dates; accuracy is null on dates without questions. The heatmap covers today and the preceding 83 dates. History is filtered and paginated on the server. Subject accuracy is weighted by question count, points by available points, with distinct attempt counts; no average-of-averages.

The all-time summary covers all recorded activity through asOf. All daily buckets are returned; when more than 90 dates exist charts show the latest 90, explicitly labelled, while the table retains the entire period. Weekly/monthly aggregates scan bounded dates; streak uses distinct active dates from lifetime history. Queries are grouped, not per-day or per-subject requests. An expression index supports normalized practice completion timestamps.

## Staff analytics

`/workspace/analytics` is separate from student statistics. ADMIN aggregates active STUDENT accounts. TEACHER's roster is active STUDENT accounts currently in their own groups or active/completed course enrollments. Shared ENT practice/theory metrics describe that roster; LMS activity only includes the teacher's own courses and assignments. Private planner actions and other teachers' course events are excluded. Courses, enrollments, assignments and coverage stay within ownership. No student names, notes, answers or submission text are returned.

New enrollments count active/completed enrollments created in the selected period. Assignment rate is unique currently assigned `(assignment, student)` pairs with at least one submitted revision in the period divided by all currently assigned pairs. Revisions do not inflate it. Cancelled enrollment and unavailable course/module/lesson ancestors exclude pairs. A zero denominator is null. Coverage reports publication snapshots and current draft/review/archive states; an existing published snapshot can overlap a newer draft.

## First admin: local operator

Register a normal account, then run from a machine with operator access to the project's local Docker Compose database:

```powershell
./scripts/admin.ps1 promote user@example.com
```

```sh
sh scripts/admin.sh promote user@example.com
```

Python 3 and Docker Compose are required (the repository's .venv is preferred). The script finds exactly one active account, displays its ID/email/role, and requires typing `PROMOTE <displayed UUID>`. Anything else cancels. Zero/multiple matches and remote Docker endpoints are rejected. The checked local Docker endpoint is pinned across the prompt; DB credentials come from the running Compose postgres container, never from source code or an API. After confirmation the script revalidates role/revision/email/active status under a row lock, updates role and revision, and records `OPERATOR_PROMOTE_ADMIN` with null actor (local operator) in audit_events. An already-ADMIN account is a no-op. Sign out and back in afterward. There is no public bootstrap endpoint or default admin.

## Verification

Commands and final measured evidence are recorded in VERIFICATION.md. `AnalyticsIntegrationTests` uses an injected fixed Clock and independent PostgreSQL 17 Testcontainers fixtures. Browser fixtures obtain the server's asOf/local date, create actual completed attempts through the API, and relocate only fixture dates; no host current-date dependency. The browser script covers exact 7-day summary/deltas, 30-day/all, accessible chart tables, heatmap keyboard details, filtered pagination, RU/KZ, 320/390/768/1024/1440, reduced motion, dashboard, staff navigation and the confirmed local operator flow. Test accounts are disabled and their content archived afterward.
