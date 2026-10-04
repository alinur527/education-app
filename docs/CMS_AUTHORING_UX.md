# Guided authoring and file navigation

## Design plan and critique

The audience is a teacher preparing a real course or an editor preparing ЕНТ topics. The interface should answer where a material belongs, what remains unfinished, what students currently see and what to do next.

The existing token system is retained: paper `#FFFFFF`, workspace `#F4F6FA`, ink `#172544`, secondary text `#53627A`, blue `#315AE8` and success `#21735B`. Golos Text remains the only type family. Form text is left aligned, instructions stay within 70 characters per line where space allows, and paired RU/KZ fields collapse on mobile.

```
parent link + contents of parent
1 Basic information | 2 Content | 3 Review and publication
active step: focused heading, fields or student-style preview
Back / Next + Save draft
next level: add topic/theory/question or module/lesson/quiz
files + enrolment where applicable
```

The numbered control is an actual sequence with distinct editable panels. It does not imply an editorial check has passed. Authors may save a draft at any step or inspect the preview before finishing. A failed native field check opens its actual step and focuses the field, including advanced fields inside a details element. The design uses the existing blue outlined active step and one quiet guidance panel; extra dashboards, decorative metrics and a second CMS shell were rejected.

## Implemented behavior

- `ContentEditor` has basic/content/review steps and keeps current step after saving an existing record. It retains revision conflict handling, unsaved-navigation confirmation and values after an HTTP failure.
- Changing a new material's type with unsaved text requires an in-app confirmation. Parent selection marks the form dirty. A parent chosen through a create link is preserved even when it is outside the first parent-search page.
- Publication wording separates the current draft from the published version. The review step lists missing title, description, body, answer, explanation and block-language fields. Filled fields do not imply accurate translation or human approval.
- `AuthoringGuide` provides real parent links, scoped child lists, and prefilled create links. Subject → topic → theory/question/context and course → module → lesson → quiz/assignment use the existing entities. Each item has its own save/review/publish workflow.
- `ContentList` keeps search/type/status/page in the URL. Parent and missing-KZ-title filters are server-side and paginated. The translation queue is explicitly limited to **titles**; it does not claim to certify the full body.
- `QuestionEditor` clears multiple-answer and matching keys when their referenced option is removed.
- `/workspace/files` is a staff file library with search, pagination, download and links to the owning material. It uses the existing authorized download endpoint. Upload still begins in a selected material's editor, preserving attachment ownership.
- Editable existing theory records integrate the separate offline-rights form outside the main editor form. No nested HTML forms are introduced.

## Verified checks

`authoring.test.tsx` exercises invalid hidden fields, draft save with its parent, type-change confirmation, scoped child navigation, published-version wording, URL filter restoration, removal of multiple/matching keys, and the file library. The file test checks ownership links, scan blocking, authenticated download failure, preserved search, pagination and resetting the page after a search change. `expansion.test.tsx` also verifies that practice counts respect real topic/difficulty availability. Existing CMS conflict/navigation tests continue to pass.

On 2026-10-04, the complete frontend Vitest suite passed **45/45** tests; `tsc --noEmit` and ESLint for the changed CMS/components/tests passed. These checks use the bundled Node executable with `node --no-experimental-webstorage node_modules/vitest/vitest.mjs run`, `node node_modules/typescript/bin/tsc --noEmit` and `node node_modules/eslint/bin/eslint.js <changed files> --max-warnings 0` from `frontend`.

The rebuilt local Docker application passed `./.venv/Scripts/python.exe scripts/phase2_browser_tests.py`: **68 checkpoints, zero browser errors** on 2026-10-04. This retains the earlier 57 checkpoints and adds the file library plus content/preview steps at five widths. The actual browser flow creates and publishes ENT content, creates a course/module/lesson, uploads an original valid PDF, downloads the same bytes through teacher and student access, creates a group and assignment, completes student practice/error review/quiz, grades work, changes roles, imports CSV/JSON and rejects invalid imports. Draft text survives simulated 409 and network failures.

Chromium screenshots and axe checks cover course and all three CMS steps at 1440, 1024, 768, 390 and 320 pixels, and teacher groups through 320 pixels. No page overflow was detected. Screenshots in `docs/screenshots/phase2/` were visually inspected for the lesson preview, file library and smallest-width CMS/course/group views; the run report is ignored at `test-results/phase2/report.json`. One intermediate rerun was interrupted by a concurrent frontend container replacement; the complete final rerun above passed after the server was stable. Responsive passage/table rendering outside these flows is verified separately by the expansion content suite.
