# Legacy audit and rebuild decisions

Sources: user-provided ZIPs, extracted outside the repository. Target remote had no refs at inspection. Archived binaries, runtimes, dependencies, IDE caches and generated assets are excluded.

## Preserve
Java / Spring Boot 3.5, PostgreSQL, JPA domain and UUIDs, BCrypt, signed expiring JWTs, student/admin routes, bilingual curriculum and soft-delete CRUD. Existing session ownership lookup is sound. Schema already enforces one answer per session/question.

## Repair
- Public question listing exposes answer keys; unfinished results also reveal answers.
- Disabled accounts can authenticate. JWT debug logs include email addresses.
- Answer endpoint accepts nonexistent options/negative times and races with finish. Add database locking, validation and idempotent retries.
- Mutable questions change the meaning of historical results. Snapshot at test start.
- No session resume, real statistics or language update. Seeded subject question counts do not reflect inventory.
- Hardcoded database credentials and fallback JWT secret. Environment configuration must fail closed.
- V11 seeds a known account and V12 grants ADMIN. Preserve historical migration bytes for checksum compatibility, then V13 removes that exact legacy seeded email before the app becomes available. No new demo/admin accounts are provided. Back up existing databases: that identity and its attempts are intentionally removed.
- Only a context smoke test depends on a manually running database. Replace with PostgreSQL Testcontainers integration tests.

## Replace
Frontend is a Figma export with MUI + Radix + Tailwind and unused dependencies, fake phone/status bar, dead registration/password-reset controls, partial localization, guessed DTOs and no typecheck/lint/tests. Retain domain and route intent; rebuild typed React with native semantic controls, Tailwind and Radix confirmation dialog.

Statistics must be database aggregates. Public question counts reflect active questions. Existing theory seeds are short introductions, not a complete exam curriculum. Student flow takes priority; admin API remains supported and tested.
