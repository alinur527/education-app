# Reproducible content release

The source inputs under `content/inputs/` are preserved originals, with hashes in `INPUT_MANIFEST.json`. The XLSX catalogue, NotebookLM Markdown and source JSON are research inputs, not accepted CMS import formats. `scripts/content_release.py` is the explicit adapter into the versioned CMS pack schema.

## Operator commands

Python 3.11+ is sufficient for the adapter; it uses the standard library. From the repository root:

```sh
python scripts/content_release.py dry-run
python scripts/content_release.py apply --email editor@example.test --publish --materials
python scripts/content_release.py resume --email editor@example.test --publish --materials
python scripts/content_release.py report
```

On Windows, use `.venv/Scripts/python.exe -X utf8` in place of `python`. The default command is dry-run. It validates the source structure and builds `content/generated/release-plan.json`; it does not contact a server. Every apply batch then goes through the actual server preview and confirmation endpoints. `--publish` separately requests the real DRAFT → REVIEW → PUBLISHED workflow with a version-bound preview. `--materials` uploads the permitted Python PDF only after its local bytes match the reviewed source manifest.

The default base URL is `http://127.0.0.1:8081`. The operator CLI deliberately accepts loopback hosts only. This release is not a production deployment. Passwords are read with `getpass` or the ephemeral `EDU_PASSWORD` environment variable; never pass credentials on the command line or commit them. `EDU_EMAIL` is also supported. Local run reports are written to ignored `test-results/content-release-state.json` and contain no credentials.

The source PDF is `test-results/source-research/python-3.12.0-tutorial-en.pdf`. Its exact archive URL, archive member, license, size and two SHA-256 hashes are documented in `content/research/python-material.json`. It is the original English archive tutorial, not a translation or a recommendation to install an old interpreter. Its embedded license and notices remain unchanged. No copyrighted NCT material with uncertain reuse permission is silently mirrored; those records remain external links.

## Identity and updates

Each CMS row has a `namespace`, stable `externalKey` and `sourceVersion`. The server computes a canonical SHA-256 of the payload and keeps its UUID mapping independently of a particular batch. Existing baseline subjects can be adopted only with identical kind, parent and payload; their UUIDs, question relationships and results remain intact. A later explicit content update goes through the same draft workflow.

An identical pack returns the previous result without repeating writes. An identical payload in a different batch is UNCHANGED. Changed incoming content updates a draft only when the current payload still matches the previously applied payload. Publication increments alone do not create a false conflict. Any teacher/editor text change is preserved as a conflict candidate; archived content is not automatically revived.

The CMS **Пакеты** page previews every row, reports validation errors, shows history and compares conflict candidates. **Оставить текущий текст** rejects that candidate. **Принять входящий черновик** uses an optimistic version check and saves a draft; it never overwrites the published snapshot. Subsequent publication is a separate choice.

A confirmed batch is one database transaction. A multi-batch release is intentionally not a global transaction: parent batches are applied first, followed by children and contexts before dependent questions. The durable server mapping makes resume safe after a client interruption. Reusing a batch key with different bytes is a 409 error. Use a new pack version/batch key for a changed release. A stale preview never partially applies.

## Curriculum versus completed lessons

The adapter preserves all 748 official topic records across 36 language/programme variants. Each official record retains its source, printed code, page, variant and full original heading. Short UI headings do not replace the original description. Where there is no equivalent official translation, the original-language label is explicit. Extraction uncertainties remain recorded in the research artefacts.

Completed starter lessons are separate pedagogical topics linked to a specific official source/code. This avoids pretending that one short lesson covers an entire official syllabus row. For example, the two lessons on linear and quadratic equations both map to official mathematics topic 05 but remain two distinct learning activities. The subject UI separates **Уроки** from **Программа НЦТ**. Empty syllabus entries cannot generate practice or planner tasks and do not inflate mastery.

The starter target is 45 bilingual learning topics / 450 authored questions: five topics each for mathematics, physics and Kazakhstan history; two for each remaining direction. The Python course is separate: two modules, six lessons, thirty quiz questions and six assignments. Actual published counts, shortages and review status belong in [coverage](CURRICULUM_COVERAGE.md), not a generated claim of completeness.

## Editorial evidence

Authored questions are AI_GENERATED with `verified=false`. Source reading, extraction, arithmetic/key checks, translation checks and explanation checks are separate flags. These are automated checks, not subject-specialist approval. Explicit editorial review records capture an authenticated reviewer, immutable reviewed payload, decision and note. Publication does not manufacture a human review record.

The configured standard ENT structure is versioned in `content/research/exam-config.json` and copied into the backend resource. Full official simulation stays unavailable until its complete reviewed bank, intact contexts and difficulty blueprint are certified. The user can run mixed practice and an explicitly shortened timed training instead. The UI shows total and format-specific bank shortages.
