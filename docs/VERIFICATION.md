# Verification and handoff record

Verified locally on 2026-10-04, Windows PowerShell, JDK 21.0.9, Node 25.8.0, Docker Desktop, PostgreSQL 17. Production containers use Java 21 and Node 24 for the frontend build. CI uses Node 24 and Java 21 on Ubuntu.

## Executed

| Check | Result |
| --- | --- |
| `backend/mvnw.cmd -f backend/pom.xml -q verify` | 8 integration tests passed, 0 skipped; real isolated PostgreSQL via Testcontainers |
| `npm ci` | Clean dependency installation passed |
| `npm run typecheck` | Strict TypeScript passed |
| `npm run lint` | ESLint passed without warnings |
| `npm test` | 9 component/integration tests passed |
| `npm run build` | Production bundle passed; self-hosted fonts included |
| `npm audit --omit=dev` | No reported runtime vulnerabilities at verification time |
| `docker compose up -d --wait postgres` | PostgreSQL healthy on local port 55432 |
| `docker compose --profile app build` | Both production images built |
| `docker compose --profile app up -d --wait` | PostgreSQL, backend and Nginx frontend healthy |
| `GET http://localhost:8081/api/test/ping` | `pong`, through Nginx |
| `GET http://localhost:8081/statistics` | SPA fallback works; CSP, frame, nosniff, referrer and no-cache headers verified |
| `scripts/setup.ps1` | Generates random secrets outside repo; preserves existing `.env` |
| Python Playwright against `http://127.0.0.1:8081` | 28 browser checkpoints passed |

The first development-browser runs used the reviewed `webapp-testing/scripts/with_server.py` helper to launch the Java JAR and Vite. Final regression used the running production Docker stack. The helper was read and its `--help` inspected before use. No hidden mock API supplies normal-flow data.

## Browser coverage

Fresh registration in Kazakh; language persistence; logout/login; protected-page refresh; subjects/topics; multiple theory blocks; keyboard radio selection; complete test; exit confirmation/cancel; server-side resume; test refresh; result review; real statistics; Kazakh results; settings; direct routes; 1440/768/390/320 px layouts; early finish and unanswered review; HTTP 500 with retry; malformed successful response; empty catalogue; offline request; 404; expired session and cleared token.

Normal-flow console errors, uncaught exceptions, unexpected HTTP failures and broken requests fail the script. Deliberately injected failure responses are scoped separately. Axe checks WCAG 2 A/AA and 2.1 AA rules on the documented major screens, including rendered test questions. No violations were returned in the final run. This is automated coverage, not a claim of exhaustive accessibility certification.

The report is written to ignored `test-results/browser-report.json`; screenshots retained in `docs/screenshots/` are documentation artifacts. CSS also respects reduced motion. Tests use role/label selectors and assert no horizontal overflow.

## Critical review and fixes

The invoked `grilling` instructions required factual investigation rather than guessing. A read-only audit sub-agent inspected security, concurrency, schema, API, UI and deployment. No P0/P1 remained; these concrete P2 issues were fixed and regression checked:

| Finding | Fix / evidence |
| --- | --- |
| Stale language PATCH could undo logout; stale 401 could clear a new token | Captured-token checks, serialized language updates; 2 targeted frontend tests |
| Topic moves left questions/theories in the old subject | Atomic propagation of parent and denormalized titles; API integration assertions |
| Old attempt reviews depended on mutable question rows | V15 backfill in original question order; isolated V12→latest upgrade test |
| Multibyte passwords could exceed BCrypt's byte limit | Server UTF-8 byte validation on both auth endpoints; regression assertions |
| Requests could wait indefinitely | 15-second abort deadline covers fetch and response body |
| Nginx location overrode security-header inheritance | Replaced location `add_header` with `expires -1`; real header checks |
| Mobile account lacked logout | Logout added to settings |
| Topic numbers had insufficient contrast | Darkened token, reran axe |
| Duplicate answer writes could race | Session row lock and idempotent receipts; 4 simultaneous retries persist one answer |

Original audit issues (pre-finish answer leakage, disabled accounts, hardcoded credentials, historical seeded admin, mismatched DTOs, fake phone shell, dead auth actions and missing statistics) are documented in [AUDIT.md](AUDIT.md) and addressed in the implementation.

## Skills actually applied

- **frontend-design**: read before implementation; created and critiqued `DESIGN.md`, defined palette/type/layout, built custom components, then inspected real screenshots and corrected contrast.
- **webapp-testing**: read before browser work; used native Python Playwright and the inspected server helper, collected screenshots, checked actual user flows and failures against PostgreSQL.
- **grill-me / grilling**: `grill-me` is a wrapper requesting `grilling`; both instruction files were read. `grilling` is an interview skill, not an automated code-review engine. Its factual-investigation step drove the audit sub-agent; its product-scope question was answered by the user: deliver the platform on current content, not a complete course. No further unresolved product decisions remain for this scope.
- **handoff**: final continuity note is saved to the OS temporary directory, outside the repository, referencing this document and the final commits rather than duplicating them.

## Scope retained

Current learning inventory: 3 subjects, 4 topics, 6 questions, 5 theory blocks. Admin CRUD is integration-tested and documented; no admin UI. Password reset/email verification and public hosting are not implemented. JWT logout is client-side. Before internet exposure, configure HTTPS, auth rate limits, monitoring and database backups. These are explicit scope boundaries, not placeholder controls in the student UI.

GitHub workflow results must be checked after push; local test success alone does not certify the remote runner.
