# Live content-pack safety verification

Verified against the running Docker deployment at `http://127.0.0.1:8081` on 2026-10-04, completed at 05:10:17 UTC: **31 checks passed**, with no cleanup errors. This supplements the backend integration tests with actual HTTP behavior through nginx and the deployed application. No backend or frontend code was changed for this check.

Run from the repository root after Docker services are healthy:

```shell
python -X utf8 scripts/content_pack_safety_tests.py
```

The script uses only Python's standard library and the Docker CLI. `BROWSER_BASE_URL` optionally changes the local base URL; non-local hosts are rejected. The same command can be added to CI after its application startup/health step. This verification did not change the CI workflow.

It registers fresh synthetic ADMIN, TEACHER, and STUDENT accounts, then uses local SQL to assign the two staff roles. All pack preview/confirm/resolution, teacher edits, publication, access, and audit-history checks use the API. Additional read-only SQL counts verify that invalid batches did not leave unmapped content. Credentials and JWTs remain in process memory and are neither printed nor saved.

## Batch failure and resume

| Step                                                                                     | Observed result                                                                                                                                                          |
| ---------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Teacher/student try to preview a pack                                                    | Both receive 403.                                                                                                                                                        |
| First batch creates a private COURSE and MODULE                                          | APPLIED; exactly two content records, both DRAFT.                                                                                                                        |
| Second batch contains a valid new MODULE followed by a LESSON with the wrong parent kind | Preview is INVALID with `INVALID_PARENT`; confirm returns 409.                                                                                                           |
| Inspect after the rejected confirmation                                                  | Exactly the original two content records and mappings remain; both original CMS views are unchanged. The valid first row of the failed batch was not partially inserted. |
| Correct the failed batch while retaining its old batch key                               | 409: a changed request cannot reuse an existing batch key.                                                                                                               |
| Resume with a new corrected key, including the original rows again                       | Preview: UNCHANGED, UNCHANGED, CREATE, CREATE. Confirmation creates only the two missing records; the first two IDs remain identical.                                    |
| Retry both successful requests with their original keys                                  | Returns the same saved batch/result, without side effects. The saved `created` count describes the original application, not new writes on retry.                        |
| Replay all four rows under another new batch key                                         | Four UNCHANGED, zero created/updated, identical mappings, exactly four records.                                                                                          |

Atomicity here is **per batch**: the earlier successful batch remains applied when a later batch is invalid. Recovery uses a new key for a changed failed request; it does not roll back the entire release or silently replace the request associated with an old key.

## Actual teacher edits and explicit conflict decisions

A separate course was created by the TEACHER, adopted by identical payload into the ADMIN's pack namespace, reviewed, and published. Adoption preserved teacher ownership. Its visibility was PRIVATE; the unrelated STUDENT received 404. The teacher then saved a local edit through their own authenticated CMS API.

- An incoming changed pack preview reported `CONFLICT`. Preview and confirmation left the teacher's current draft unchanged; confirmation created a conflict candidate rather than updating content.
- Candidate detail contained the actual current teacher draft and incoming pack draft. TEACHER could not resolve an import conflict (403); ADMIN used the explicit decisions.
- `KEEP_LOCAL` marked the candidate REJECTED and left the complete CMS view unchanged. Its batch could be retried without recreating side effects.
- For `USE_INCOMING_DRAFT`, a second teacher edit after preview made the loaded revision stale. Resolution with that stale version returned 409 and retained the new teacher edit. Explicit resolution using the newly read revision produced a DRAFT containing the incoming payload and incremented the draft version once.
- Each decision appeared in content history with the ADMIN actor. Repeating an already resolved decision returned 409. A fresh pack batch containing the accepted incoming payload returned UNCHANGED with zero created/updated/conflicts.
- After both decisions and replay, the published API response and `publishedVersion` remained identical to the original reviewed publication. No implicit publication occurred. Publishing the incoming draft would require the separate review/publication workflow.

## Evidence and cleanup

Local ignored artifacts: `test-results/content-pack-safety/report.json` and `test-results/content-pack-safety.log`. The report contains check names, synthetic namespace/batch IDs, preview decisions, the original publication response hash, and cleanup results. It does not contain credentials, JWTs, or real user records.

Run namespace: `safety-3ab40499b556487780ede2fd66416fe3`. Exactly five fixture content records existed at completion. All five were archived; all three generated accounts were disabled and demoted to STUDENT. Batch history/mappings/audit entries remain as synthetic evidence, pointing to archived fixtures. No public fixture was created: four records stayed DRAFT, and the only published fixture was a PRIVATE teacher course before archival.

This was a bounded sequential safety check, including a deliberately stale revision. It does not claim distributed fault injection, interruption during database commit, or a concurrent import stress test.
