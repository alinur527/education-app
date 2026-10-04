# Local content release evidence

Executed 2026-10-04 against the existing local PostgreSQL database through `http://127.0.0.1:8081/api`. This is a local content release, not an external production deployment.

- Namespace: `education-kz-2026`; version: `2026.10.04-v1`.
- Canonical plan SHA-256: `179d0e532f5a032a53c485f24e97c5292aee5a62e0c31387e309dd77b63dff92`.
- 30 bounded content batches; 1,329 permanent mappings. All mapped records have a published version.
- Repeat/resume: 30 previously applied batches replayed, zero newly created or updated records; same 1,329 mappings. A replay result includes the original CREATE/UPDATE action labels; those labels do not mean the operation executed again.

| Kind | Published records |
|---|---:|
| SUBJECT | 18 |
| TOPIC | 793: 748 programme entries + 45 actual learning topics |
| THEORY | 45, each with RU/KZ content |
| QUESTION | 450 unique bilingual questions |
| CONTEXT | 2 original reading passages |
| COURSE / MODULE / LESSON | 1 / 2 / 6 |
| QUIZ / ASSIGNMENT | 6 / 6; 30 separate Python quiz questions |

All 45 ENT lessons and 450 questions are published starter material, **not human-approved curriculum**. Human subject approvals remain zero. Each direction's exact official variants, completed lessons, questions and gaps are in [CURRICULUM_COVERAGE.md](CURRICULUM_COVERAGE.md). Publication and verification are independent states.

## Actual files

Downloaded for research: 77 NCT source documents (73 PDF, 4 HTML), including 36 programme specifications, plus one permitted Python tutorial PDF extracted from the official archive. The source downloader manifest records every URL, status, byte count and SHA; offline cache verification matched all 77 NCT files. Access limitations for some supplemental Gramota/UN pages are recorded in [CONTENT_SOURCES.md](CONTENT_SOURCES.md), not counted as successful full-document downloads.

Uploaded to application storage for this release: **one** unchanged English Python Tutorial PDF, 630,898 bytes, 157 pages, material `1076faab-765d-451e-b607-3031ba2521c2`, SHA-256 `5ff988b60b2d6bde55ef4a4f55fc9d3e809c767d83f3b8b38934a4df4848bf85`. It is attached to the published Python course and has real ClamAV **CLEAN** status. The course browser test downloads it through the protected endpoint and compares its bytes/hash. Repeat material application retained this same file mapping.

Each ENT direction currently has **zero uploaded source binaries**. NCT programme/reference PDFs remain attributed external links because redistribution rights have not been established. Temporary test uploads are not counted as educational release files. Research manifests preserve the historical pre-upload observation; this report records the later runtime upload.

## Upgrade preservation

The existing database was backed up before upgrade; no volumes were replaced. Post-upgrade audit matched all 51 pre-existing user UUIDs and all 26 pre-existing attempts, including their status, score and completion timestamp. All 17 existing Flyway history checksums matched (V6 is absent in the original history); migration files V1–V18 are unchanged. Existing enrollment/material counts did not decrease. New migrations are V19–V22. Testcontainers also verifies fresh installation and an explicit V18 legacy assessment upgrade with old Cyrillic option identifiers and absent policy metadata.

Local raw reports and the backup are in ignored `test-results/`; they are intentionally excluded from the repository and public CI artifacts. No passwords or JWTs are included in this report.
