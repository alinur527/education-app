# Expansion API contract

All paths below start with `/api`. JWT and current active-account checks apply. Public registration still creates STUDENT only. Existing topic practice, results, role and CMS routes remain compatible. JSON bodies, including chunked bodies, are capped at 2 MiB before parsing; multipart files remain 20 MiB (21 MiB request).

## Assessment

`GET /practice` returns `{subjects,topics,configuration}`. Subjects have `id,titleRu,titleKz,available`; topic rows additionally carry `subjectId,difficulty,available`. Counts include only accessible bilingual published questions with live parents/contexts. Catalog rows contain no question keys.

`POST /practice/sessions` accepts `{mode,subjectIds?,topicIds?,difficulty?,count,minutes?,profilePair?}` and returns the existing `{sessionId,...}` start response. MIXED_PRACTICE uses 1–5 subjects, optional topic IDs and `easy|medium|hard` difficulty, 1–50 unique questions. SHORTENED_ENT uses a verified configuration pair index and 1–240 minutes; its stored mode is MOCK_ENT with an explicit frozen `officialFormat:false,shortened:true`. It is not a full official simulation. Unsupported full MOCK_ENT returns 409 `FULL_ENT_BANK_NOT_READY`; insufficient unique content returns 409 `INSUFFICIENT_BANK`. Invalid selections return 400. Starting attempts is limited to 30 per account/minute and returns 429 thereafter.

`GET /practice/exam-bank?profilePair=0` returns configuration ID, officialReady=false, required/available totals and per-subject/per-type/context deficits. A sufficient raw question count does not certify difficulty, human review or complete context blueprints. The verified source configuration is `content/research/exam-config.json`, packaged in backend resources.

The existing session response adds `questionIds,practiceMode,deadlineAt,maxPoints`; deadlineAt is an offset timestamp fixed by the server. Refresh never extends it. Expired attempts reject new answers and finish using the persisted deadline. Each answer request supports either legacy `selectedOptionId` or `answer:{selectedOptionId}` / `answer:{selectedOptionIds:[...]}` / `answer:{pairs:[{leftId,rightId},...]}`. Duplicate/unknown IDs, incomplete pairs and wrong shapes return 400. An identical saved answer is retry-safe; changing it returns 409.

Question DTOs expose an allowlisted assessment (type/options/leftOptions/max points/policy identifier) and an optional allowlisted frozen context. Correct keys, explanation and editorial evidence are absent before completion. Results add earnedPoints/maxPoints and per-answer topicId/subjectId; old snapshots without assessment keep their original single-choice rules and Cyrillic option IDs. Session creation freezes wording, keys, scoring policy and context version. Editing or archiving a source cannot rewrite completed results. Archived contexts are removed from new practice/error-review availability.

Quiz finish accepts the legacy string array or typed answer objects in question order. Nested course-quiz context references are rejected explicitly; standalone ENT shared contexts are supported.

## Authoring, sources and publication

`GET /cms/content` adds optional `parentId` and `missingTranslation=true` (missing title KZ specifically) filters to existing kind/status/query/page filters. Ownership and the 25-row default page remain enforced. `GET /cms/contexts?topicId=&q=` returns at most 50 published contexts of that topic, including published version; ADMIN/CONTENT_EDITOR only. CONTEXT is a separate content kind under TOPIC. Publishing creates an immutable context version. QUESTION payloads reference `contextId,contextVersion`; omitted version is pinned at publication.

Provenance fields: `sourceIds,sourceType,sourceUrl,sourceName,contentLanguage,studiedLanguage,curriculumVariant,examVersion,answerEvidence,difficultyReason,reviewChecks`. Review booleans are sourceRead/extractionChecked/answerChecked/translationChecked/explanationChecked. They do not represent human approval. `curriculum` preserves sourceId, officialCode, platformCode, sectionRu/Kz, variant, examVersion, page and extractionStatus. The public topic response adds contentRole (SYLLABUS/LEARNING), curriculum, documentLanguage and sourceUrl. Learning mastery excludes programme-only rows with no available lesson/practice.

| Method/path | Request → response | Permission |
| --- | --- | --- |
| GET `/cms/sources?q=&page=0` | → up to 50 `{sourceId,revision,metadata}` | ADMIN/CONTENT_EDITOR |
| POST `/cms/sources` | up to 100 `{sourceId,revision?,metadata}` → records | Same; existing changes require current revision |
| POST `/cms/content-batches/preview` | `{items:[{id,version}],target:REVIEW|PUBLISHED}` → `{valid,rows,confirmation}` | ADMIN/CONTENT_EDITOR; max100 |
| POST `/cms/content-batches/confirm` | same body plus confirmation → outcome | Same, sorted row locks, atomic batch, stale409 |
| GET `/cms/content/{id}/editorial-review` | → latest25 decisions with reviewer/time/current flag | ADMIN/CONTENT_EDITOR |
| POST same path | `{version,decision:APPROVED|CHANGES_REQUIRED,note,confirmedHumanReview:true}` | Same; explicit personal review, immutable audit snapshot |

Source registry URLs are metadata; the API never fetches arbitrary external URLs. Automated import cannot set HUMAN_REVIEWED or forge a personal reviewer. Public lesson/theory/course/assignment/context payloads strip editorial evidence and answer-key fields; CMS retains them. A teacher-authored teaching text may of course contain a worked example intentionally.

## Versioned content and material packs

`POST /cms/content-packs` previews `{schema:"education-content-pack/v1",namespace,packVersion,batchKey,rows}`. Each row has `externalKey,kind,parentExternalKey? or parentId?,existingId?,sourceVersion,payload`. Maximum500 rows/2MiB. The result is `{id,status,preview:[{row,externalKey,action,error?}],result}`. Actions are CREATE/ADOPT/UNCHANGED/UPDATE_DRAFT/CONFLICT. Invalid rows never partially apply.

`POST /cms/content-packs/{id}/confirm` atomically applies a valid batch and records UUID mappings/result counts. Repeat identical packs are idempotent across operator runs. Changed bytes under the same batch identity return409. Multiple external keys targeting one existing record in a batch are rejected. Teacher changes create a conflict and preserve publication. An entire multi-batch release is not one transaction.

`GET /cms/content-packs?page=`, `/{id}`, `/conflicts?page=`, `/conflicts/{id}`, `/mappings?namespace=&page=` expose history/candidates/maps to ADMIN/CONTENT_EDITOR. History/conflicts have25 rows, mappings100. `POST /conflicts/{id}/resolve` accepts `{decision:KEEP_LOCAL|USE_INCOMING_DRAFT,version}`; it records the actor's decision and does not publish the incoming draft.

`POST /cms/material-packs` takes multipart `namespace,externalKey,contentId,titleRu,titleKz,file`. Same key+parent+SHA returns the existing material; conflicting bytes/parent return409. Metadata and bytes use the normal validated/scanned protected storage. No large binary enters PostgreSQL. See [CONTENT_RELEASE](CONTENT_RELEASE.md) for Windows/Linux dry-run/apply/resume/report and pinned download manifest.

`GET /cms/materials?q=&page=0` returns `{items,page,size:25,total}`; items include normal material metadata plus contentTitleRu/Kz/contentKind. ADMIN/CONTENT_EDITOR see editorial content; TEACHER sees only owned non-ENT content. No storage keys or student submissions are exposed. Existing protected download is unchanged.

## Study, submission files and offline

The complete versioned profile, calendar, private notes/cards, reminders/preferences endpoints and limits are documented in [PLANNER_NOTIFICATIONS](PLANNER_NOTIFICATIONS.md). File upload/scan/retry/download, immutable submission and grade revisions are documented in [STUDENT_FILES](STUDENT_FILES.md). Existing submit accepts `{text,fileIds?,requestKey?,revision?}`; a new revision resets the current grade while retaining history. STUDENT can submit/download only CLEAN files; no-op scanning is UNSCANNED, never CLEAN.

`GET /offline/theories/{id}` returns `{id,version,policy:PUBLIC_THEORY_V1,content,files}` for explicit publicly reusable ENT theory only. No teacher/admin bypass allows a private/draft course export. `GET /offline/materials/{id}` additionally requires published rights-approved parent/file access and scan policy. Downloads are private/no-store; the browser explicitly stores only this bounded public snapshot.

`POST /cms/materials/{id}/offline-rights` accepts `{allowed,basis}`; ADMIN/CONTENT_EDITOR and editable ENT THEORY parent required. Rights changes are audited. Permission failures return403/404, invalid body400, stale state409, oversized request413. [OFFLINE_POLICY](OFFLINE_POLICY.md) explains the 100MiB library, explicit updates, revocation limitations and zero auth/private API caching.
