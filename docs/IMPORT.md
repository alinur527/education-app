# CSV / JSON content import

Open **Кабинет → Импорт** as ADMIN or CONTENT_EDITOR. Choose a UTF-8 `.json` or `.csv` file (up to 2 MiB, 500 rows), preview validation, then confirm. Validation creates only an import record. Confirmation creates the entire batch as **DRAFT** in one database transaction. Publish from the CMS after reviewing content/translations. No SQL or Java changes are needed.

Examples: [curriculum.json](examples/curriculum.json), [curriculum.csv](examples/curriculum.csv), [course.json](examples/course.json), [invalid-question.json](examples/invalid-question.json). These are small illustrative fixtures, not verified examination material. Replace titles/source metadata with your reviewed material.

## Common row schema

JSON is an array of objects:

```json
{
  "key": "topic-trigonometry",
  "kind": "TOPIC",
  "parentKey": "subject-math",
  "payload": {
    "titleRu": "Тригонометрия",
    "titleKz": "Тригонометрия",
    "sortOrder": 1,
    "sourceType": "EDITOR_CREATED",
    "verified": false
  }
}
```

`key` must be unique within the file and match `[A-Za-z0-9_-]{1,80}`. `kind` is SUBJECT, TOPIC, THEORY, QUESTION, COURSE, MODULE, LESSON, QUIZ or ASSIGNMENT. Root SUBJECT/COURSE rows have no parent. `parentKey` refers to an earlier row in the file; use `parentId` to attach to an existing editable parent. Do not supply both. Hierarchy follows [PLATFORM.md](PLATFORM.md). Cycles/forward references and wrong parent kinds are rejected.

Every payload needs `titleRu`. `titleKz` and option translations can be completed later in the CMS, but are required before review/publication. SUBJECT titles are at most 200 characters, other headings 300, QUESTION text 10,000. Payloads are bounded to 200,000 JSON characters. Unknown top-level payload fields are rejected. Ownership is set by the authenticated author and parent; uploaded role/owner fields cannot grant access.

Common metadata: `sourceType` = OFFICIAL_SAMPLE / EDITOR_CREATED / AI_GENERATED / IMPORTED; optional `sourceName`, HTTP(S) `sourceUrl`, `year` (1900–2200), `language` (ru/kz/both), `verified` boolean. Imports without sourceType receive IMPORTED. Verification is an editor assertion, not automated fact-checking.

| Kind | Additional fields |
| --- | --- |
| SUBJECT | `icon`, `color`, `durationMinutes` |
| TOPIC | `descriptionRu/Kz`, `sortOrder` |
| THEORY / LESSON | `contentRu/Kz`, `blocks`, `sortOrder`; lesson also descriptions |
| QUESTION | `options:[{id,textRu,textKz}]`, `correctOptionId`, explanations, difficulty easy/medium/hard |
| COURSE | descriptions, `visibility:PUBLIC/PRIVATE`, `selfEnroll`, `icon` |
| MODULE | `sortOrder` |
| QUIZ | `questions` array of question payloads (1–100) |
| ASSIGNMENT | descriptions, blocks, `dueAt` ISO timestamp with offset, `maxScore` 1–10000 |

Questions require 2–8 unique option IDs and a matching correctOptionId, including in a draft. Blocks are an ordered array (maximum 100). Text-like blocks have `type` plus `textRu/Kz`; FILE/IMAGE need an existing `materialId` belonging to the same content record; VIDEO has an HTTP(S) `url`. Upload bytes separately in the CMS after creating drafts. A file reference is validated again during publication. Formulas are safely displayed as plain text.

## CSV

The first row is the header. Structural columns are `key,kind,parentKey,parentId`; remaining columns become payload fields. Arrays such as `options`, `questions` and `blocks` contain JSON inside an RFC4180-quoted CSV cell. Double embedded quotes (`""`), and quote commas/newlines. Boolean fields use `true`/`false`; integer fields use decimal integers. Empty optional cells are omitted. The parser accepts UTF-8 BOM and CRLF/LF, at most 64 columns and 200,000 characters per cell. A spreadsheet editor can export this format; manual DB JSON editing is unnecessary.

## Errors and transaction guarantees

Invalid preview returns `status:INVALID`, original rows and `errors:[{row,field,code,detail}]`. Row is the 1-based **data row**, excluding the CSV header; multiline CSV rows count as one. A bad answer key produces, for example, `correctOptionId=D; options=[A, B, C]`. Malformed JSON/CSV syntax fails the upload with 400 instead of creating a misleading preview.

An invalid batch cannot be confirmed. A valid preview is revalidated at confirmation; missing/changed references produce 409. The import row is locked, all creates run in one transaction, and a retry after success returns the same key→UUID result without duplicates. Any failure rolls back the whole batch. The preview owner or ADMIN can view/confirm it. History is paginated. Import confirmation does not implicitly publish any row.
