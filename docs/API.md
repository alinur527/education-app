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

`User`: `{id,email,firstName,lastName,language,role}`; role is `STUDENT` or `ADMIN`. Registration always creates an active STUDENT; supplied extra role fields cannot grant privileges. Email is normalized case-insensitively and has a unique database index. Passwords require at least 8 characters at registration and at most 72 UTF-8 bytes. Login and JWT verification reject inactive accounts. Public failures do not disclose whether an email exists during login.

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

There is no public role-management endpoint, automatic bootstrap administrator or known default password. Audit promotions operationally. To revoke access, set the role back to STUDENT or `is_active=false`; JWT authorization consults the current row.

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

Original migration content V1–V12 is retained unchanged. V6 was absent in the archive; the numeric gap is valid Flyway history. Do not use `repair` to conceal arbitrary checksum mismatches.

V13 removes the historical seeded test identity (selected by its legacy email) and cascades its attempts; adds case-insensitive email uniqueness, session topic/snapshot columns, indexes and a nonnegative answer-time constraint. **Back up an existing database before upgrade.** This deliberately retires the old known administrator; create/promote a real account explicitly.

V14 adds a bilingual worked derivative lesson. V15 freezes existing attempts using the content available at upgrade, preserving question order; it cannot reconstruct edits made before this migration. New attempts always snapshot at creation.

If importing a database with case-colliding emails or negative answer times introduced outside the validated application, audit and correct those rows before migration. Existing secrets must be replaced; no old `.env` or database password is transferred from the archives. Do not roll this backend back to the legacy version after applying the security migration.
