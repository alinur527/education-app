# Deployment checklist

This release is verified in local Docker and CI. It does not change Railway, Supabase or external hosting. Before a public deployment:

- Terminate HTTPS at a trusted reverse proxy; retain CSP, strict allowed origins and security headers. Do not expose PostgreSQL, ClamAV or object-store administration publicly. Use a production hostname rather than publishing the loopback development ports unchanged.
- Generate independent database/JWT/object-store secrets outside Git. Rotate deliberately; changing the JWT signing key invalidates existing sessions. Review administrator access and remove/deactivate disposable browser-test identities before promoting any development data.
- Back up PostgreSQL **and** the material/submission storage. Test restoring them together and retain the source manifests. Run Flyway upgrade against a restored copy before deploying; never rewrite V1–V18.
- Select local durable storage or configure the S3-compatible implementation with restricted bucket credentials. Enforce private downloads through the API. Review storage limits, retention of submission revisions and reconciliation of orphaned files after crashes.
- Set `APP_SCAN_ENGINE=clamav` and `APP_SCAN_REQUIRED=true` for untrusted uploads. Keep ClamAV definitions current, monitor connectivity and fail closed on scanner errors. `UNSCANNED` and grandfathered `UNSCANNED_LEGACY` are not CLEAN. See [STORAGE.md](STORAGE.md) and [STUDENT_FILES.md](STUDENT_FILES.md).
- Apply proxy rate limits to register/login and general abuse controls; retain application request/body limits and practice-start throttling. Configure bounded request timeouts and monitor auth failures, 409 conflicts, import failures, scanner failures and storage growth without recording passwords/JWTs.
- Monitor the scheduled reminder worker and its bounded account cursor. Current notifications are best-effort deduplicated in-app reminders with the documented lookback; there is no durable external push/outbox guarantee. Check user timezones and quiet hours.
- Require an actual subject/language editor to approve AI-authored starter material before representing it as verified curriculum. Review publication/source permissions, legal material currency and licensing before public redistribution. The full official ENT bank remains unavailable.
- Test a production PWA upgrade with one active study session and multiple tabs. Cache policy must continue excluding `/api/**` and private data. An explicitly downloaded open file cannot be remotely revoked.

Use the final PR/CI SHA and [VERIFICATION.md](VERIFICATION.md) as release evidence. This checklist is operational guidance, not a claim that external production deployment or load testing occurred.
