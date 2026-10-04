# Teacher CMS and LMS

Phase 2 extends baseline `28e822995620b5166c0bbca3745b16a0e4911f8b`. It retains the ENT schema and student contracts. It does not supply a complete ENT curriculum.

## Roles and entry

Register at `/register`, sign in at `/login`. Public registration creates only STUDENT. An existing administrator changes roles in **Кабинет → Пользователи**. Bootstrap the first administrator using the operator procedure in [API.md](API.md#admin-crud); this is the only initial console step. There are no shared passwords. Roles are read from the current user row on every authenticated request, so deactivation immediately invalidates subsequent requests with existing JWTs.

| Role | Content | Groups, assignments, enrollments | Users |
| --- | --- | --- | --- |
| STUDENT | Published ENT; published courses according to visibility/enrollment | Own enrolled courses and assigned groups; own submissions/progress | Own profile |
| TEACHER | Own courses and their descendants; own files | Own groups/students and course enrollments | No account administration |
| CONTENT_EDITOR | All curriculum/course content; imports | No student roster, grade or group management | No account administration |
| ADMIN | All content; archive/restore | All teacher operations | Search, role changes, activation |

CMS routes are `/workspace/content`, `/workspace/content/new`, `/workspace/content/{id}`. Teacher groups use `/workspace/groups`; grading uses `/workspace/assignments/{id}`. Imports and users have their own workspace tabs. `/admin` and `/teacher` redirect to this common workspace. Backend checks are independent of these route guards.

Account updates require a revision; self-demotion/deactivation and removal of the last active administrator are rejected. Concurrent administrative updates serialize. Changes are audited.

## Authoring and publication

Choose **Создать материал**, select a type, find its parent by title, and fill RU/KZ fields. Save a draft before attaching files. Create ENT content in order: **Предмет → Тема → Теория / Вопрос**. Course content uses **Курс → Модуль → Урок → Тест урока / Задание**. The API also supports an assignment directly under a course.

Blocks support text, headings, images, files, external video/URL links, quotes, safely rendered formulas, code, tables and callouts. Upload an image/file first, then select it in a block. Blocks can be moved up or removed. External URLs are links, not embedded executable HTML. Legacy theory text stays readable alongside new blocks. Source type, URL/name, year and separate editorial evidence stages belong to the staff editor; publication alone is not human verification.

New content starts DRAFT. **На проверку** validates the bilingual title and question translations; **Опубликовать** publishes REVIEW content. Teachers may publish their own course hierarchy, editors may publish content, and administrators may publish/archive/restore. There is no separate mandatory reviewer identity in this first workflow.

Draft changes leave the last published student version intact. New drafts have no student-visible version. An archived parent hides its entire subtree. Archiving clears the accessible published version; only an administrator may restore it to DRAFT, after which review/publication is required again. A new child cannot become visible while an ancestor is unpublished.

**Предпросмотр** renders the current draft, including quiz questions, without publishing it. Correct quiz answers are not marked in student-style preview. Explicit save is used instead of autosave. A revision conflict preserves the form and asks the editor to compare versions. Fields are disabled while saving; navigation away from unsaved changes requires confirmation. Reloading/closing the tab still relies on the browser's native warning; there is no offline draft storage.

Each record has created/updated actor and timestamp, current revision and last publication revision. History shows the actor, operation, time and revision. This is an audit history, not full historical text restoration. The four existing ADMIN CRUD APIs remain available and publish their legacy fields immediately; only trusted administrators have access. They preserve unrelated unpublished blocks and use the same lock order as the CMS.

## Courses, groups and assignments

A course is PUBLIC (listed) or PRIVATE (enrollment only). Published public courses can allow self-enrollment. A teacher/admin can enroll or cancel a registered student from the course editor using email. Enrollment status is ACTIVE, COMPLETED or CANCELLED.

Create a group for an owned course, then add registered students by email. Adding a group member grants course enrollment. Removing a member cancels access only when neither another group on that course nor a manual/self-enrollment grants it. Explicit cancellation in the course editor revokes the enrollment even if a group membership remains; re-enroll to restore it. Concurrent removals share an enrollment lock.

Create an assignment under a lesson, attach materials, set a deadline and maximum score, publish it, and assign it from the group's page. Only current members of an assigned group with an active/completed course enrollment can read/submit it. Deadlines are displayed; late text submissions remain allowed. A student's new submission replaces their text and clears any prior grade. Teachers grade from the group assignment link. Grading requires the current submission revision; a stale grade cannot overwrite a new answer. The maximum score cannot change after the first submission, including after archive/restore.

Lesson completion is an explicit student action. Course progress is completed published lessons divided by all published lessons under published modules. Completing all currently published lessons changes enrollment to COMPLETED. New lessons can lower the computed percentage while retaining that historical enrollment status; COMPLETED still grants access. Quiz scores and assignment grades are separate evidence, not prerequisites falsely implied by a completion checkmark.

Teacher analytics show current group members, actual completed lesson counts, average progress, submission counts and weak ENT topics from their students' completed attempts. They expose no other teacher's group. Grading lists, groups, users, CMS content, imports and the course catalog are paginated; lesson/module outlines and a group's roster are returned as a bounded-by-course/group view, not a global student export.

## Learning feedback

`/learning` and the dashboard calculate mastery on the server:

```
recentAccuracy = 100 × fully correct questions in latest 3 completed attempts containing the topic
                      / all snapshot questions for that topic in those attempts
theoryFraction = marked-read active theories / all active theories
mastery = 20 × theoryFraction + 0.8 × recentAccuracy
```

Without theory, mastery is recentAccuracy. No attempts means zero practice accuracy. Percentages are rounded to one decimal server-side; compact UI values may round to integers. Unanswered questions in completed attempts count as incorrect. An abandoned/in-progress attempt does not contribute. Subject mastery is the unweighted mean of available learning-topic mastery, including unstudied learning topics. This is an activity-based learning indicator, not a prediction of an official ENT score.

The dashboard shows completed-attempt question count (including unanswered), accuracy, subject mastery, weak attempted topics followed by new topics, error count, and the most recently practiced/marked-read active topic. It does not invent study time.

`/learning/errors` takes the latest completed outcome for each question ID across attempts. Incorrect/unanswered outcomes appear once. Correctly answering in a later completed attempt removes the question; another later mistake returns it. Archived topics are excluded. Each review session takes at most 50 frozen questions from one topic; repeat again for additional errors. Answer keys remain server-side until that session finishes. Existing TOPIC_PRACTICE is preserved; ERROR_REVIEW is implemented. MIXED_PRACTICE and shortened timed practice are implemented; the full official-format mock is not available.

## Intentional limits

No payments, ERP, sales CRM, complete human-reviewed ENT course, automatic grading/execution of student code, reviewer assignment workflow, full revision rollback or private offline synchronization. Planner/calendar/reminders/bookmarks/notes, versioned student files and working ClamAV are now implemented. The content remains a starter release; see [CURRICULUM_COVERAGE](CURRICULUM_COVERAGE.md).

The current guided CMS and file library are described in [CMS_AUTHORING_UX](CMS_AUTHORING_UX.md). [EXPANSION_API](EXPANSION_API.md) supersedes older single-choice-only and text-submission-only examples. Mixed practice and an explicitly shortened timed practice are working modes; a full official mock remains unavailable. Programme-only syllabus rows do not dilute mastery: only topics with available theory or practice are included. Mixed attempts contribute frozen per-question earned/max points to the corresponding topics.
