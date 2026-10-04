# Statistics 2.0 adversarial review

Scope: additive analytics on codex/education-content-experience, preserving CMS/LMS, content and existing API. Skills: frontend-design before UI, grill-me/grilling factual review after implementation, webapp-testing through real browser fixtures. Product choices come from the user's Statistics 2.0 brief; no new unanswered product decision blocks delivery.

An independent read-only agent reviewed source, SQL timezone behaviour and Intl formatting. No P0/P1 was confirmed. All reported P2 findings were repaired and the same reviewer rechecked the fixes:

| Finding | Repair and evidence |
| --- | --- |
| Java fixed offsets have opposite signs in PostgreSQL timezone strings | sqlZone translates fixed offsets; integration cases +05:00, UTC+05:30, -04:00 plus named-zone DST boundary |
| Java zone aliases crash Intl history rendering | activityDate normalizes aliases/offsets; frontend regression includes UTC, Z, UT, UTC+05:00, +05:30, -04:00; year is shown |
| First-read preservation regresses Continue learning | V24 separates last_read_at; rereads preserve completion but refresh resume |
| Future attempts alter recommendations beyond asOf | Analytics passes its Clock cutoff into the existing mastery calculation; future-only attempt test returns no weak topic |
| Returning learner is described as new on an empty selected period | hasPracticeHistory distinguishes no lifetime practice from no practice in period |
| Teacher sees private planner/foreign-course activity of a shared pupil | kind-aware ownership scope; shared-pupil/private-plan/foreign-assignment integration test |
| Cancelled enrollment/archived ancestors inflate assignment denominator | recipients joins active/completed enrollment and checks course/module/lesson publication access |
| Docker context can change during operator confirmation | pin the verified local endpoint/env across lookup and mutation; role/revision revalidation and row lock remain transactional |

Responsive browser inspection also caught global definition-list CSS squeezing metric values and overflowing at 1024. Analytics now resets only its own lists; all five required widths and Kazakh at 320 pass axe and overflow checks. Empty LearningSummary no longer renders a false 0% accuracy.

Follow-up review found no unresolved P0/P1/P2 in the inspected scope. This is an implementation review, not human approval of curriculum or a production deployment claim. See STUDENT_ANALYTICS.md for explicit metric scopes, timestamps, assignment denominator and known legacy history limits.
