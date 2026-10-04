# Starter content: bounded second-pass review

Reviewed on 2026-10-04. This report records a model review of source JSON, not human subject-expert approval or a claim that a full ЕНТ/ҰБТ curriculum is ready. It does not publish content or set editorial approval. Pilot/core were authored by a different agent; languages/Python received a second pass from their authoring agent.

## Scope and result

| Pack | Structural checks | Questions read in RU/KZ | Selection |
| --- | ---: | ---: | --- |
| `content/starter/pilot.json` | 150 | 40 | Questions 3 and 8 in all 15 topics, plus all 7 multiple-select and 3 matching questions |
| `content/starter/core.json` | 160 | 32 | Questions 3 and 8 in all 16 topics |
| `content/starter/languages.json` | 140 | 28 | Questions 3 and 8 in all 14 topics |
| `content/starter/python-course.json` | 30 | 12 | Questions 2 and 5 in all 6 lessons |
| **Total** | **480** | **112** | Fixed coverage sample; not a statistical estimate of bank accuracy |

No incorrect answer key or meaning-changing RU/KZ mismatch was found in this sample. Each selected prompt, all its alternatives, the selected key and both explanations were read. Reasons and exact file SHA-256 values are recorded in [content-sample-review.json](../content/research/content-sample-review.json). A later content edit requires comparing those hashes and checking affected entries; a successful old report does not verify new bytes.

Metadata amendment: the core percentages topic is linked to `SPEC-08`, official code `02`. The language pack now carries explicit stable `externalKey` values on its 140 questions; prompts, answers and explanations are unchanged. The structural and selected-key checks were rerun and the report hashes refreshed after these identity/linkage changes.

Structural checks cover unique option IDs and texts in each language, existing answer IDs, unique multiple-choice keys, complete matching left-side coverage, nonempty bilingual prompts/explanations, and theory length of at least 1,500 characters in each language. These checks do not establish teaching quality by themselves.

Both original reading contexts were read in full in RU/KZ. All 20 context references resolve to the intended context. The book-library passage preserves the 48/30/18/42/6 quantities, the reason for delay, optional consent to replacement and the May continuation. The watering passage preserves 70/95 litres, the limits of one-week observation and the distinction between evidence and a universal claim.

## Concrete answer checks

| Case | Independent reasoning or source check |
| --- | --- |
| Pilot radicals, q10 | The four results are 8, 5, 4 and 6, so A/C/D are true and B is false. |
| Pilot quadratic, q10 | Substituting −2 and 3 into x²−x−6 gives zero; the two other values do not. |
| Pilot right-triangle matching | 9²+12²=15² and 8²+15²=17². |
| Pilot Newton, q10 | Zero net force in an inertial frame permits rest or constant straight-line velocity; circular motion has acceleration. |
| Pilot Kazakhstan history | Botai expedition leader, western Zhetysu migration and 1992 UN admission were checked against the sources below. |
| Core percentages | 9,000/0.75=12,000; 45/0.15=300. Exact `Fraction` arithmetic was also executed. |
| Core map/weather units | 6 km at 1:100,000 is 6 cm; 30′ is 0.5°; 5 mm over 1 m² is 5 litres. |
| Core chemistry | Neutral Z=12 gives 12 electrons; 54/18=3 mol; 0.5×6.02×10²³=3.01×10²³ particles. |
| Core law | Sampled rules and article numbers were read in the official 2026 RU and KZ versions, rather than inferred from the former Constitution. |
| Russian | Compound future uses an imperfective infinitive; a following speech attribution begins with lowercase; punctuation stays consistent with question-mark direct speech. |
| Kazakh | бөлме → бөлмеге, instrumental -мен without a back-vowel counterpart, locative сыныпта and accusative question нені? were checked. |
| Literature | Sampled plot/argument questions match the named original texts; the Abai smartphone example is explicitly distinguished from an original statement. |
| English/German/French | Agreement, auxiliary forms, verb-second order and French `commençons` are consistent; original reading passages supply every sampled factual answer. |
| Python | Assignment, comment syntax, `int`, indentation, `append`, `return`, context-managed file closure and stdout/file distinctions were checked. |

## Sources inspected during authoring and this pass

The authoring files retain topic-specific source URLs. These examples make the factual checks reproducible; they do not grant permission to republish source books or illustrations.

- The [Botai culture account](https://e-history.kz/ru/kazakhstanika/show/10041) identifies V. F. Zaibert. The [Kerey biography](https://e-history.kz/ru/prominent-figures/show/12623) supports western Zhetysu and the formation context. These pages require permission for copying; only original educational explanations/questions were authored.
- The [UN admission resolution record](https://digitallibrary.un.org/record/147692) gives the 2 March 1992 adoption date. The [UN Charter page](https://www.un.org/en/about-us/un-charter/) distinguishes signature on 26 June from entry into force on 24 October 1945. The Charter page required its indexed official version during this pass after direct retrieval returned an error.
- Official [2026 Constitution, RU](https://old.adilet.zan.kz/rus/docs/K2600000000) and [KZ](https://old.adilet.zan.kz/kaz/docs/K2600000000) confirm the sampled articles 2(1), 7(1), 12(1) and 36(1). The lessons state their source date and avoid silently carrying over old article numbers. This content needs a date-sensitive editorial check before a later release.
- [Abai’s thirty-first word](https://www.abai.institute/qara-sozder/otyz-birinsi-soz), [Krylov’s original fable](https://ilibrary.ru/text/2175/p.1/index.html) and [Gogol’s first act](https://ilibrary.ru/text/473/p.1/index.html) support the literary samples. The lessons use original explanations rather than copied contemporary commentary.
- [Emle harmony rules](https://emle.kz/kz/rule?id=85), [Emle case rules](https://emle.kz/kz/rule?id=126), [British Council present tense](https://learnenglish.britishcouncil.org/free-resources/grammar/english-grammar-reference/present-tense), [Goethe A1 forms](https://lernen.goethe.de/deutschonline/A1/PDF/DE/deutschonline_Redemittel_und_Grammatik_9.pdf) and [UT Austin spelling-changing verbs](https://www.laits.utexas.edu/tex/gr/ver2.html) underpin the grammar examples. Direct Gramota retrieval was blocked during Russian authoring; indexed official results and normative reasoning were used, with that access limitation retained in research notes.
- [OpenStax prokaryotes](https://openstax.org/books/biology-2e/pages/4-2-prokaryotic-cells), [photosynthesis](https://openstax.org/books/biology-2e/pages/8-1-overview-of-photosynthesis) and [atomic structure](https://openstax.org/books/chemistry-atoms-first-2e/pages/2-3-atomic-structure-and-symbolism) corroborate scientific facts. Their current pages display **CC BY-NC-SA and additional AI-use restrictions**. Do not describe current source assets as unrestricted CC BY, rehost the textbooks, or infer permission for commercial derivative material. The pack uses independently phrased facts and exercises, not those assets.
- [Python input/output documentation](https://docs.python.org/3/tutorial/inputoutput.html) supports explicit encoding, file modes, `with` and file output. The separate authorized English tutorial PDF has a distinct [download/license manifest](../content/research/python-material.json); it is not a RU/KZ translation.

## Corrections and execution evidence

The second pass found case changes in Kazakh Python instructions (`Name`, `Days.txt`, `Max`) that could confuse learners because Python identifiers and portable filenames are case-sensitive. These now retain `name`, `days.txt` and `max`; related function names in explanations also retain their actual spelling. Quiz keys and runnable examples did not change.

The course builder then executed all **12 original code examples** in separate temporary directories using Python **3.12.14**, checking exact stdout and any output-file contents. It also executed **8 condition-boundary cases**: −1, 0, 49, 50, 79, 80, 100 and 101. All passed. The source JSON stores the tested code hashes. This verifies the supplied examples; student submissions remain teacher-reviewed and there is no claimed browser Python runtime or automated grading.

Pilot spacing issues and an internal `REF-02` label in a student explanation were reported to the pack author and corrected in the reviewed final bytes. The capital-transfer explanation now names Akmola in 1997 and its later renaming in 1998 in both languages. The matching/multiple-select sample did not require answer changes. Repeated option-position patterns in the authored packs are a limitation of this small learning starter: it must not be marketed as a calibrated or psychometrically validated exam bank.

## Related frontend regression

[expansion.test.tsx](../frontend/src/expansion.test.tsx) adds 12 Testing Library/Vitest scenarios for multiple and matching controls, frozen submitted answers and no pre-finish explanation, insufficient practice banks, content-pack preview/confirm, invalid JSON and invalid server previews, both conflict decisions, a recoverable 409, and stale bulk previews. It caught a race in which an old REVIEW preview reappeared after switching to PUBLISHED; the request snapshot now includes the target. The regression passes after that fix.

Commands executed from `frontend`:

```powershell
& 'C:/Users/FLANTE/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe' node_modules/typescript/bin/tsc --noEmit
& 'C:/Users/FLANTE/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe' node_modules/eslint/bin/eslint.js src/expansion.test.tsx --max-warnings 0
& 'C:/Users/FLANTE/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe' --no-experimental-webstorage node_modules/vitest/vitest.mjs run
```

Observed result at this checkpoint: **4 test files, 32 tests passed**, TypeScript and the new test file's lint passed. These tests use mocked HTTP in jsdom. They verify client behavior and do not replace live browser E2E, database migration tests or backend authorization checks; later application changes need the final regression run.

## Published Python course browser verification

On 2026-10-04, `.venv/Scripts/python.exe scripts/course_content_browser_tests.py` passed **16 flows**, **30 authored quiz answers**, and **zero browser errors** against the rebuilt local Docker application. The same script is in the existing browser CI job after `content_browser_tests.py`.

The script registers disposable accounts, logs in through the student UI and self-enrolls in the actual published Python starter. It compares the complete rendered RU/KZ theory of each of six lessons to the authoring source, checks both original code examples and expected stdout in each language, and answers all six five-question quizzes through the UI using only checked-in authored keys. Quiz responses contain no keys or explanations before completion. Every quiz scores 100%. Explicit lesson-completion actions produce real **6 / 6** course progress; reading alone is not represented as completion.

An isolated fixture group assigns the existing first Python assignment. The student submits the actual solution, its locally verified output and an explanation through the UI; reloading retains the answer. No teacher score or automatic assessment is claimed. This flow caught collapsed multi-paragraph assignment descriptions; the existing safe `RichText` renderer now preserves descriptions, sample output and RU/KZ text. Two unit tests also check that literal HTML remains text.

The installed official English PDF was downloaded through the protected UI action: **630,898 bytes**, SHA256 `5ff988b60b2d6bde55ef4a4f55fc9d3e809c767d83f3b8b38934a4df4848bf85`, matching its license/download manifest. Where CI has no optional PDF, the report explicitly says `ABSENT_NOT_VERIFIED` and the remaining **15 flows** run; the independent Phase 2 suite still verifies authorized PDF upload/download.

Four screenshots in [screenshots/expansion/python-course](screenshots/expansion/python-course) were visually inspected, and all four screens passed axe WCAG 2 A/AA + 2.1 AA and horizontal-overflow checks. The ignored detailed report is `test-results/expansion/python-course/report.json`; it contains no credentials or JWTs. These are local application checks, distinct from the source review and from remote CI status.
