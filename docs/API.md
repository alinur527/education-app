# API contract

Base path `/api`. JSON uses camelCase; identifiers are UUID strings. `language` is `ru` or `kz`; the HTML language code for Kazakh is `kk`. Dates are UTC; session `LocalDateTime` strings have no suffix and must be interpreted as UTC. Statistics dates include the UTC offset. Question indexes are **zero-based**.

Except `POST /auth/register`, `POST /auth/login`, and `GET /test/ping`, requests require `Authorization: Bearer <token>`. Never send tokens in URLs. The legacy question-list contract was intentionally tightened: answer keys and explanations are no longer returned to students before completion.

## Authentication

| Method / path | Body / response |
| --- | --- |
| `POST /auth/register` | `{email,password,firstName,lastName,language}` → `{token,user}` |
| `POST /auth/login` | `{email,password}` → `{token,user}` |
| `GET /auth/me` | `User` |
| `PATCH /auth/me/language` | `{language:"ru"\|"kz"}` → updated `User` |

`User`: `{id,email,firstName,lastName,language,role}`; role is `STUDENT`, `TEACHER`, `CONTENT_EDITOR` or `ADMIN`. Registration always creates an active STUDENT; supplied extra role fields cannot grant privileges. Email is normalized case-insensitively and has a unique database index. Passwords require at least 8 characters at registration and at most 72 UTF-8 bytes. Login and JWT verification reject inactive accounts. Public failures do not disclose whether an email exists during login.

No refresh token, password reset, email delivery or token revocation endpoint is implemented. Logout deletes the browser token. Disabling the user invalidates all their tokens; rotating the signing key invalidates all sessions. Default expiration is one day.

## Content

| Path | Response |
| --- | --- |
| `GET /subjects` | `Subject[]` |
| `GET /topics/subject/{subjectId}` | `Topic[]` |
| `GET /topics/{topicId}` | `Topic` |
| `GET /theories/topic/{topicId}` | `Theory[]`, sorted by `sortOrder` |
| `GET /questions/topic/{topicId}` | Question previews, without correct answers/explanations |

- `Subject`: `{id,nameRu,nameKz,icon,color,questionCount,durationMinutes}`. `questionCount` is active question inventory under active topics. `durationMinutes` is retained legacy configuration and is not presented as an enforced timer.
- `Topic`: `{id,subjectId,titleRu,titleKz,descriptionRu,descriptionKz,sortOrder,questionCount,theoryCount}`.
- `Theory`: `{id,topicId,titleRu,titleKz,contentRu,contentKz,sortOrder}`. Plain text paragraphs and `## ` headings are rendered safely; arbitrary HTML is not interpreted.
- `Option`: `{id,textRu,textKz}`. Option ids are case-sensitive, unique within a question, at most 10 characters.

Inactive parents hide their content and prevent new test starts. Existing attempts remain resumable and readable from their snapshot, even after content is unpublished. A missing or inactive subject/topic returns `404`; an active parent with no children returns `[]`.

## Test sessions

| Method / path | Contract |
| --- | --- |
| `POST /tests/start` | `{topicId}` → `{sessionId,totalQuestions,startedAt}` |
| `GET /tests/{id}` | `{sessionId,topicId,subjectId,status,totalQuestions,startedAt,answers:[{questionId,selectedOptionId}]}` |
| `GET /tests/{id}/questions/{index}` | `{sessionId,index,totalQuestions,questionId,topicId,topicRu,topicKz,questionRu,questionKz,options,difficulty,year}` |
| `POST /tests/{id}/answers` | `{questionId,selectedOptionId,timeSpentSecs?}` → `{questionId,selectedOptionId}` |
| `POST /tests/{id}/finish` | `{sessionId,correctAnswers,totalQuestions,score,timeTakenSecs}` |
| `GET /tests/{id}/results` | Finish fields + `{topicId,subjectId,status,answers:AnswerReview[]}` |

`AnswerReview`: `{questionId,questionRu,questionKz,options,selectedOptionId,correctOptionId,isCorrect,explanationRu,explanationKz}`. Unanswered questions are included with `selectedOptionId:null` and `isCorrect:false`.

- Starting snapshots the ordered active question set. No arbitrary timer or random score is introduced.
- Empty topic: `409`. Unknown/inactive topic: `404`.
- All session endpoints check ownership, including reads and finish; another account receives `404`.
- Correctness is not included in answer receipts. Results before completion return `409`.
- Answers must refer to a question and exact option in the snapshot; invalid input is `400`. Time per answer is optional and constrained to 0–86400 seconds. Total time is calculated on the server.
- Same answer retried while active returns the same receipt. A different replacement is `409`. Completed sessions reject answers with `409`.
- Finish is idempotent. Unanswered questions count as incorrect. Score is `correct/total*100`, rounded to two decimals.
- Answer and finish operations lock the owning session row in a database transaction. A race has a definite ordering; a submission that loses to finish is rejected.
- Start creates a new attempt on each successful request. The client prevents concurrent double clicks; retrying after an uncertain network failure can leave an extra empty attempt, visible under unfinished tests.

## Statistics

`GET /statistics/me` returns:

```json
{
  "testsTaken": 0,
  "averageScore": 0,
  "bestScore": 0,
  "timeTakenSecs": 0,
  "recentAttempts": [],
  "subjects": [],
  "activeAttempts": []
}
```

All completed attempts contribute to totals; incomplete attempts never affect scores. `averageScore` is the unweighted mean of attempt percentages, not a weighted fraction of answered questions. Early-finished attempts count, including unanswered questions.

- `recentAttempts`: latest 10 `{sessionId,topicId,subjectId,nameRu,nameKz,score,totalQuestions,correctAnswers,completedAt}` in descending completion order.
- `subjects`: `{subjectId,nameRu,nameKz,testsTaken,averageScore,bestScore}`.
- `activeAttempts`: most recently updated 5 `{sessionId,topicId,nameRu,nameKz,totalQuestions,answeredQuestions}`.

Charts render this data only. A new account has no fabricated percentage; the UI displays an em dash for averages until the first completed test.

## Admin CRUD

All `/admin/**` paths require a currently active user with the database role `ADMIN`. Unauthenticated callers receive `401`; STUDENT receives `403`. DELETE is soft-delete (`isActive=false`); successful CRUD responses use `200` to preserve the existing API.

| Resource | List | Mutations |
| --- | --- | --- |
| Subjects | `GET /admin/subjects` | `POST /admin/subjects`, `PUT /admin/subjects/{id}`, `DELETE /admin/subjects/{id}` |
| Topics | `GET /admin/topics/subject/{subjectId}` | `POST /admin/topics`, `PUT /admin/topics/{id}`, `DELETE /admin/topics/{id}` |
| Theories | `GET /admin/theories/topic/{topicId}` | `POST /admin/theories`, `PUT /admin/theories/{id}`, `DELETE /admin/theories/{id}` |
| Questions | `GET /admin/questions/topic/{topicId}` | `POST /admin/questions`, `PUT /admin/questions/{id}`, `DELETE /admin/questions/{id}` |

PUT uses a complete request body. Responses contain those fields plus `id`:

- Subject: `{nameRu,nameKz,icon?,color?,questionCount,durationMinutes,isActive}`. The legacy configured `questionCount` remains editable but public counts are calculated from content.
- Topic: `{subjectId,titleRu,titleKz,descriptionRu?,descriptionKz?,sortOrder,isActive}`. Moving/renaming a topic atomically synchronizes the denormalized subject/title fields in its questions and theories. Existing attempt snapshots remain unchanged.
- Theory: `{topicId,titleRu,titleKz,contentRu,contentKz,sortOrder,isActive}`.
- Question: `{topicId,questionRu,questionKz,options:[{id,textRu,textKz}],correctOptionId,explanationRu?,explanationKz?,difficulty,year?,isActive}`. Supply 2–8 non-null options with unique ids. `correctOptionId` must match one option. `difficulty` is `easy`, `medium` or `hard`.

To provision an administrator, register a named account normally, then promote **that exact verified account id** through a trusted database console:

```sql
UPDATE users SET role = 'ADMIN' WHERE id = '<verified-account-uuid>' AND is_active = TRUE;
```

There is no unauthenticated role-management endpoint, automatic bootstrap administrator or known default password. Use this operator step only to establish the first trusted administrator; subsequent role/status changes use the audited ADMIN UI/API below. JWT authorization consults the current row.

## Errors

JSON errors contain a stable `code`; clients localize by status. No raw exception message, password hash or stack trace is returned.

| Status | Meaning |
| --- | --- |
| 400 | Invalid fields, UUID, index or option |
| 401 | Invalid credentials, missing/expired/invalid JWT or inactive account |
| 403 | Missing ADMIN role or rejected CORS origin |
| 404 | Resource missing, hidden or not owned |
| 405 | Unsupported HTTP method |
| 409 | Duplicate email, conflicting saved answer or wrong session state |
| 500 | Unexpected server failure; generic response |

## Migration notes

Original migration content V1–V15 is retained unchanged in Phase 2. V6 was absent in the archive; the numeric gap is valid Flyway history. Do not use `repair` to conceal arbitrary checksum mismatches.

V13 removes the historical seeded test identity (selected by its legacy email) and cascades its attempts; adds case-insensitive email uniqueness, session topic/snapshot columns, indexes and a nonnegative answer-time constraint. **Back up an existing database before upgrade.** This deliberately retires the old known administrator; create/promote a real account explicitly.

V14 adds a bilingual worked derivative lesson. V15 freezes existing attempts using the content available at upgrade, preserving question order; it cannot reconstruct edits made before this migration. New attempts always snapshot at creation.

If importing a database with case-colliding emails or negative answer times introduced outside the validated application, audit and correct those rows before migration. Existing secrets must be replaced; no old `.env` or database password is transferred from the archives. Do not roll this backend back to the legacy version after applying the security migration.


## Phase 2 editorial API

All paths below have the `/api` prefix. Default paginated shape is `{items,page,size,total}` with zero-based `page`, 25 items per page. Content lists accept `size` clamped to 1–100. Requests and responses are DTOs, never serialized JPA entities. Ownership fields supplied in requests are not bound to entities; service-assigned ownership follows the parent.

| Method/path | Request → response | Permission |
| --- | --- | --- |
| GET `/cms/content?kind=&status=&q=&page=&size=` | → page of ContentSummary | Teacher sees owned non-ENT; editor/admin all |
| GET `/cms/content/{id}` | → Content | Editable ownership |
| POST `/cms/content` | `{kind,parentId?,payload}` → Content DRAFT | Staff; ENT editor/admin only |
| PUT `/cms/content/{id}` | `{kind,parentId?,payload,version}` → Content DRAFT | Same; immutable kind/parent; archived rejected |
| POST `/cms/content/{id}/transition` | `{status,version}` → Content | Owner/editor publish; ADMIN archive/restore |
| GET `/cms/content/{id}/history` | → `[{actorId,actorName,operation,revision,createdAt}]`, latest 100 | Editable ownership |
| GET `/content/{id}` | → `{id,kind,parentId,content}` | Publication, ancestry and course/group access |
| GET `/search?q=` | → `[{id,kind,titleRu,titleKz}]`, at most 30 | Published visible subjects/topics/courses/lessons |

`ContentSummary` is Content without payload; list queries do not load or transmit question/block bodies.

`Content` = `{id,kind,parentId,ownerId,titleRu,titleKz,payload,status,version,publishedVersion,createdBy,updatedBy,createdAt,updatedAt}`. Kinds, payload fields, limits and examples are in [IMPORT.md](IMPORT.md). Status is DRAFT, REVIEW, PUBLISHED or ARCHIVED. Content exposes editable draft data only to staff. `/content/{id}` returns only published payload and **rejects QUESTION/QUIZ**: use the session endpoints for answer-safe delivery.

Conflicts return 409: `REVISION_CONFLICT`, `INVALID_TRANSITION`, `ARCHIVED_CONTENT`, `ASSIGNMENT_SCALE_LOCKED`. Invalid fields/translations/options return 400; ownership 403 or hidden resource 404. Any absent/unpublished/archived ancestor hides student content. Legacy ADMIN CRUD remains ADMIN-only; its intentional immediate publication behavior is documented in [PLATFORM.md](PLATFORM.md).

## Materials

| Method/path | Request → response | Permission |
| --- | --- | --- |
| POST `/cms/materials` | multipart `contentId,titleRu,titleKz?,file` → Material | Editable parent |
| GET `/content/{id}/materials` | → Material[] (max 100) | Visible parent; enrolled course files; staff drafts |
| GET `/materials/{id}/download` | → authorized byte stream, attachment/nosniff/no-store | Parent ownership or published/enrolled/assigned access |

`Material` = `{id,contentId,titleRu,titleKz,originalFileName,mimeType,size,published}`. Storage keys and credentials are never returned. Allowed parent kinds are TOPIC, THEORY, COURSE, LESSON, ASSIGNMENT. Files remain draft until parent publication. Public course catalog visibility alone does not grant file access; an unenrolled viewer gets an empty list and a direct download gets 404. See [STORAGE.md](STORAGE.md). 400 covers name/MIME/signature/type/parent validation, 413 HTTP oversize, 403 forbidden upload, 404 hidden download; storage outages produce a generic server error.

## Courses, enrollment and progress

| Method/path | Request → response | Permission |
| --- | --- | --- |
| GET `/courses?q=&page=` | → page of `{id,titleRu,titleKz,descriptionRu,descriptionKz,icon,visibility,selfEnroll,enrollment}` | Published public or own enrollment |
| GET `/courses/{id}` | → CourseDetail | Same |
| POST `/courses/{id}/enroll` | no body → `{saved:true}` | STUDENT; public, published and selfEnroll enabled |
| POST `/teacher/courses/{id}/enrollments` | `{email,status:"ACTIVE"/"CANCELLED"}` → saved | ADMIN or owning TEACHER |
| POST `/lessons/{id}/complete` | no body → saved | Enrolled student-facing access |
| GET `/lessons/{id}/activities` | → `[{id,kind,titleRu,titleKz}]` | Enrolled, published lesson; assignment group policy applies |

`CourseDetail` = `{id,content,enrollment,totalLessons,completedLessons,modules:[{id,titleRu,titleKz,lessons:[{id,titleRu,titleKz,completed}]}]}`. Modules/lessons in the outline are published only. Catalog viewers may see the outline but cannot read lessons without enrollment. Enrollment can be ACTIVE/COMPLETED/CANCELLED or null. Role/email validation fails with 400/404; forbidden self-enrollment 403; hidden course/lesson 404. Completion is idempotent and uses a shared enrollment lock.

## Groups and assignments

| Method/path | Request → response | Permission |
| --- | --- | --- |
| GET `/teacher/groups?q=&page=` | → page of `{id,name,courseId,courseRu,courseKz,students}` | Own teacher groups / ADMIN |
| POST `/teacher/groups` | `{name,courseId}` → `{id}` | Owned course / ADMIN |
| GET `/teacher/groups/{id}` | → GroupDetail | Own / ADMIN |
| POST `/teacher/groups/{id}/members` | `{email}` → saved | Own / ADMIN; active STUDENT account |
| DELETE `/teacher/groups/{id}/members/{userId}` | → saved | Own / ADMIN |
| POST `/teacher/groups/{id}/assignments` | `{assignmentId}` → saved | Editable assignment on same course |
| GET `/assignments` | → `[{id,titleRu,titleKz,dueAt,maxScore,submittedAt,score}]`, max 100 | Current user's assigned published content |
| GET `/assignments/{id}` | → `{id,content,submission}` | Enrollment and group assignment; content staff preview allowed |
| POST `/assignments/{id}/submit` | `{text}` (up to 20,000 chars) → saved | Assigned STUDENT |
| GET `/teacher/assignments/{id}/submissions?page=` | → page of `{userId,firstName,lastName,text,score,feedback,revision,submittedAt}` | Owned assignment and current group students / ADMIN |
| POST `/teacher/assignments/{id}/submissions/{userId}/grade` | `{score,feedback?,revision}` → saved | Same; range 0..published maxScore |

`GroupDetail` = `{id,name,courseId,students:[{id,email,firstName,lastName,enrollment,completed}],totalLessons,averageProgress,assignments:[{id,titleRu,titleKz,dueAt,submitted}],availableAssignments:[{id,titleRu,titleKz}],weakTopics:[{id,titleRu,titleKz,accuracy}]}`. `submission` is null or `{text,submittedAt,score,feedback}`; ungraded fields are null. Groups do not create new user accounts. There is no student file-submission endpoint. Group/course mismatch and score outside scale return 400, unrelated resources 404, stale grade or unpublished assignment 409. Resubmission clears the grade and increments revision.

## Lesson quizzes

| Method/path | Request → response |
| --- | --- |
| POST `/quizzes/{id}/attempts` | no body → QuizAttempt |
| GET `/quiz-attempts/{id}` | → own QuizAttempt |
| POST `/quiz-attempts/{id}/finish` | `{answers:["A","B",...]}` in question order → completed QuizAttempt |

`QuizAttempt` = `{id,quizId,questions,score,answers}`. Questions are frozen at start and expose bilingual wording/options only until finished. Score/answers are null before finish; completed question data additionally includes correctOptionId and explanations. The attempt belongs to its authenticated creator. Creation requires visible lesson/course access; reading/finishing enforces ownership. Finish validates each option, locks the attempt, and is idempotent. 400 invalid answer set; 404 foreign/hidden quiz or attempt.

## Import

| Method/path | Request → response | Permission |
| --- | --- | --- |
| POST `/cms/imports` | multipart file → ImportPreview | ADMIN / CONTENT_EDITOR |
| GET `/cms/imports?page=` | → page of `{id,fileName,status,createdAt}` | Own previews; ADMIN all |
| GET `/cms/imports/{id}` | → ImportPreview | Owner or ADMIN |
| POST `/cms/imports/{id}/confirm` | no body → imported ImportPreview | Same |

`ImportPreview` = `{id,fileName,status,rows,errors,result}`. Rows retain the input, including invalid scalar/object rows. `errors` = `[{row,field,code,detail}]`; `result` is null or a key→UUID object. Status is VALID/INVALID/IMPORTED. Syntax/encoding/size errors return 400; invalid preview or changed references 409; another editor's preview 404. Confirmation is a row-locked, all-or-nothing transaction and repeat-safe. See [IMPORT.md](IMPORT.md) and its examples.

## Learning activity

| Method/path | Request → response |
| --- | --- |
| GET `/learning/me` | → LearningOverview for current user |
| POST `/learning/theories/{id}/read` | no body → saved; active theory/ancestors only |
| POST `/learning/errors/practice` | `{topicId}` → existing StartTestResponse |

`LearningOverview` = `{questionsAnswered,testsCompleted,accuracy,errorCount,continueTopic,topics,subjects,errorTopics}`. `continueTopic` is null or `{topicId,titleRu,titleKz}`. Topics include `{topicId,subjectId,titleRu,titleKz,subjectRu,subjectKz,attempts,bestScore,lastScore,recentAccuracy,theoryTotal,theoryRead,mastery}`. Subjects include `{id,titleRu,titleKz,mastery,topics}`; errorTopics `{topicId,titleRu,titleKz,count}`. Formula/deduplication are in [PLATFORM.md](PLATFORM.md). No errors available returns 409 `NO_ERRORS`. Error review reuses the existing `/tests/{sessionId}` endpoints, ownership, snapshots and finish-before-reveal rule.

## Account administration

GET `/admin/users?q=&role=&page=` returns a page of `{id,email,firstName,lastName,role,active,revision}`. PATCH `/admin/users/{id}` requires `{role,active,revision}` and returns saved. Both require ADMIN. Revision mismatch, self-demotion/deactivation, or losing the last active administrator return 409. Role/status changes are audited. No password hashes are returned.

V16 creates the editorial/LMS/file/import/audit schema and backfills legacy content as published/archived without changing IDs. V17 adds practice modes and the completed-question activity view. V18 adds submission revisions. The migration upgrade integration test applies V1–V15, inserts an old completed attempt and users, upgrades, and asserts unchanged snapshot/score/roles plus visible activity.
