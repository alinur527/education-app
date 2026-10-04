# Student answers, files, and revision history

Student attachments are private answer artifacts, stored in `submission_files`, not teaching `materials`. A student sends plain text and/or up to five files, each no larger than 20 MiB. Text is limited to 20,000 characters. Supported extensions are PDF, DOCX, PPTX, PNG, JPG/JPEG, WebP, TXT, and Markdown. File names, supplied MIME types, file signatures, UTF-8 text, and Office archive bounds are validated before storage. Office files containing VBA are rejected. Downloads use attachment disposition, `private, no-store`, and `nosniff`; the application does not preview executable document content.

## Workflow and privacy

1. An enrolled student with current access to a published, assigned assignment uploads a file with a client-generated UUID `requestKey`.
2. The server stores an opaque UUID object key, SHA-256, owner, assignment, metadata, and scan state. It synchronously attempts an antivirus scan. An unavailable or unconfigured scanner leaves the file quarantined and visible to its owner with its actual state.
3. Only `CLEAN` files can be selected for submission, downloaded, or graded. The frontend offers retry for unscanned/failed files; the API also permits an authorized manual rescan of a previously clean or infected file. Rescans verify the hash and scan the same stored bytes.
4. Sending the answer creates an immutable content revision and binds its selected file IDs. The current answer projection is updated and its current grade cleared. Earlier text, file bindings, and grades remain in history.
5. The assigned teacher reviews the exact revision and grades using its current optimistic revision. A concurrent student resubmission or teacher grade change returns `409 REVISION_CONFLICT` rather than grading a different answer silently.

The current student owner can read their own answer history and clean files even after leaving the group; this preserves their personal record. Current assigned teachers must own the assignment and an assigned group containing that student. Removed group members cease to be readable by that teacher. Demoted teachers, unrelated teachers, classmates, and content editors cannot read these files/history. Administrators can access them. A teacher cannot inspect a student's staged, unsubmitted file. Student upload/submission checks current publication, enrollment, and assignment-group access again on the server. Existing archived history remains a record, not permission to submit new work.

The student UI preserves text and selected files after a failed save and guards navigation with unsent changes. History is paginated at ten answer revisions per page. Each revision displays its associated grades, including earlier regrades; grade changes do not increment `contentRevision`.

## Version and retry contracts

`contentRevision` counts student sends: 1, 2, 3, and so on. `revision` is the existing optimistic concurrency value of `assignment_submissions`; it changes for student sends and teacher grades. `submissionId` identifies an immutable history revision. Files have independent UUIDs and SHA-256 values. Editing an answer never replaces stored bytes. A clean file may be reused by the same owner for another revision of the same assignment.

Upload idempotency is unique by `(user, requestKey)`. The same assignment, filename, MIME type, and bytes return the existing file; changed input or reuse of a deleted upload's key returns `409 REQUEST_KEY_REUSED`. Submission idempotency is also unique by `(user, requestKey)` and hashes assignment, exact text, and sorted file IDs. An identical retry returns its original revision without resetting a newer answer or grade. Changed content requires a new key. Access is checked before retry handling.

The extended submit body is `{text, fileIds, requestKey, revision}`. File submissions require both `requestKey` and `revision`; use revision 0 for an initial answer. Legacy text-only clients can continue posting `{text}`. Such legacy calls do not gain retry deduplication or stale-edit protection unless they supply the new fields. Responses preserve `saved: true` and add `submissionId` and `contentRevision`.

Flyway V21 migrates only the last answer and current grade that actually exist in the old projection into history, labelled `legacyImported`. It sets `contentRevision=1` while retaining the pre-existing optimistic `revision`. It cannot reconstruct overwritten earlier answers or grades. No historical versions are invented. `assignment_submissions` remains the current projection consumed by teaching lists and notifications.

## API

All routes require the normal authenticated account. UUID possession is not authorization.

| Method and path                                                | Contract                                                                                                 |
| -------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| `POST /api/assignments/{id}/files`                             | Multipart `file` and UUID `requestKey`; returns metadata and actual scan status                          |
| `GET /api/assignments/{id}/files`                              | Student's unbound files plus files in their current answer; older files are available through history    |
| `POST /api/submission-files/{id}/scan`                         | Authorized scan retry; attempts within 30 seconds return current metadata                                |
| `GET /api/submission-files/{id}/download`                      | Authorized `CLEAN` bytes with hash verification; otherwise 409                                           |
| `DELETE /api/submission-files/{id}`                            | Owner removes a staged, unbound file; bound history files return 409                                     |
| `POST /api/assignments/{id}/submit`                            | Creates the immutable revision from text and/or at most five distinct clean file IDs                     |
| `GET /api/assignments/{id}/submission-history?page=0&userId=…` | Ten revisions per page; `userId` defaults to the caller; teacher/admin reads still require authorization |
| Existing teacher grade endpoint                                | Existing optimistic `revision` check, plus append-only grade history bound to `latest_submission_id`     |

Foreign IDs are hidden with 404. Relevant error codes include `FILE_NOT_CLEAN`, `SUBMISSION_FILES_NOT_CLEAN`, `FILE_INTEGRITY_ERROR`, `REQUEST_KEY_REUSED`, `REVISION_CONFLICT`, `UPLOAD_QUOTA`, and `STAGED_FILE_LIMIT`. File metadata includes `id,assignmentId,originalFileName,mimeType,size,sha256,scanStatus,scanMessage,scanAttempts,scannedAt,createdAt,bound`. Scanner engine details are recorded in the database for operations.

## Scanner and local configuration

The real ClamAV adapter uses clamd `INSTREAM`: a NUL-terminated command, network-order length-prefixed chunks, and a zero-length terminator. Only an explicit `stream: OK` response becomes `CLEAN`; a detected signature becomes `INFECTED`, and transport/protocol errors become `SCAN_FAILED`. The connection and overall request have bounded timeouts. The protocol follows the [official clamd protocol](https://docs.clamav.net/manual/Usage/ClamdProtocol.html).

| Property / environment variable                      | Default / meaning                                                        |
| ---------------------------------------------------- | ------------------------------------------------------------------------ |
| `app.scan.engine` / `APP_SCAN_ENGINE`                | `none` or `clamav`; `none` returns `UNSCANNED`, never `CLEAN`            |
| `app.scan.host` / `APP_SCAN_HOST`                    | `127.0.0.1` for a host JVM; Compose backend defaults to service `clamav` |
| `app.scan.port` / `APP_SCAN_PORT`                    | `3310`                                                                   |
| `app.scan.timeout-ms` / `APP_SCAN_TIMEOUT_MS`        | `10000` milliseconds, permitted range 100–60000                          |
| `app.scan.required` / `APP_SCAN_REQUIRED`            | `false`; controls new teacher-material policy, not student acceptance    |
| `app.submission-files.total-bytes`                   | `524288000` (500 MiB active stored bytes per student)                    |
| `app.submission-files.daily-bytes`                   | `209715200` (200 MiB uploaded per rolling 24 hours)                      |
| `app.submission-files.worker-enabled`                | `true`                                                                   |
| `app.submission-files.retry-ms` / `initial-delay-ms` | Both `60000` milliseconds                                                |

Every student attachment requires `CLEAN`, independently of `APP_SCAN_REQUIRED`. Setting `APP_SCAN_REQUIRED=true` also rejects new teacher materials when scanning is unavailable/unconfigured. Existing trusted teaching files are labelled `UNSCANNED_LEGACY` for compatibility. New optional-scan teacher files are labelled `UNSCANNED`; they are not silently promoted to clean. Teacher-material downloads honor this policy and verify hashes where present. Legacy files without a stored hash retain their explicit legacy status.

The optional `malware` Compose profile runs the official `clamav/clamav:1.4` image, persisted signature data, a health check, and a 3 GiB memory limit. The host port is bound to `127.0.0.1`, not all interfaces. Start it with:

```powershell
docker compose --profile malware up -d clamav
docker compose --profile malware ps clamav
```

Wait for healthy before enabling `APP_SCAN_ENGINE=clamav` in the backend environment and restarting the backend. A host JVM connects to `127.0.0.1:3310`; a Compose backend connects to `clamav:3310`. Compose `.env` substitution alone does not export variables into a separately launched host JVM. To use a different host port, set `CLAMAV_PORT` for the container mapping and the matching `APP_SCAN_PORT` for the host JVM. Keep the Compose backend's internal port at 3310. Official image configuration and signature updates are described in the [ClamAV Docker documentation](https://docs.clamav.net/manual/Installing/Docker.html).

The worker takes up to 25 due `PENDING`, `SCAN_FAILED`, or `UNSCANNED` files per pass. The first five scan attempts use retry delays of 2, 4, 8, 16, and 32 minutes; automatic retries stop after attempt five. Clean/infected files are not automatically rescanned. Authorized manual retry remains available. A rescan updates scan metadata, not the file's hash or its revision bindings. Stored hash mismatch fails closed and is never treated as a new upload.

## Storage, limits, and operations

Student objects use the existing local/S3 storage provider behind private application routes. There is no public object URL or offline student-file export. Quarantine is an authorization state: the object stays in private storage while non-clean bytes cannot be downloaded through the application. Production buckets must remain private; local storage must not be mounted under a static web root.

Besides byte quotas, each student may upload at most 50 files per rolling 24 hours and retain at most ten unbound staged files. The user-row lock serializes quota checks and submissions. A deleted staged file leaves a metadata tombstone so deletion cannot reset the daily quota or recycle an idempotency key. Its blob is removed after database commit. New upload blobs are cleaned up on transaction rollback. Files referenced by answer history cannot be deleted through the student endpoint.

Database and object storage are not one distributed transaction. A process crash between writing bytes and committing the row, or failure during post-commit deletion, can leave an orphaned object. Back up database and objects together, monitor storage/scan errors, and reconcile orphaned keys operationally; this release does not include an automatic orphan collector or retention deletion for bound history. Scan retries are bounded and observable in metadata, not a durable external queue. The default `none` engine intentionally prevents student files from being sent until a real scanner is configured. A clean result records the scanner's verdict at scan time; keep signatures current and use manual rescan when investigating a file.

## Verification boundary

`SubmissionFilesIntegrationTests` passed six tests against real PostgreSQL/Flyway and local Docker ClamAV. Coverage includes clean and EICAR detection, fail-closed/no-op quarantine, current-role/group privacy, staged-file privacy, foreign attachment IDs, immutable answer/grade history, retry keys, daily quota after deletion, stored-byte integrity, and actual V18-to-current legacy migration. `MalwareScannerTests` passed two tests covering the no-op result and exact INSTREAM framing/error handling. `frontend/src/features/submissions/submissions.test.tsx` passed four tests for clean-only controls, retry-key preservation, conflict/draft retention, and history navigation without accidental grading. The Compose configuration validates. These are targeted integration/component checks; whole-release browser, accessibility, and full regression results are recorded separately by the release owner.
