# Offline reading and installation

The production frontend generates a service worker and install manifest during `npm run build`. No additional PWA runtime dependency is used. Installation needs HTTPS or a loopback development origin. Install availability is controlled by the browser; the app offers its button when `beforeinstallprompt` is provided.

## What is stored

* A versioned application shell: the exact build list of JavaScript, CSS, fonts, icons, manifest and static HTML. The worker never caches an API response or a request carrying an Authorization header.
* Only a theory the reader explicitly saves through **Сохранить для чтения без интернета**, and attached files independently authorized for offline redistribution. IndexedDB `education-public-library` contains the public export, version, saved date, byte size and file blobs. It contains no JWT, user ID, attempts, grades, notes, planner, enrollment or submission.
* All downloaded editions are visible to anyone using the browser profile, including after logout. This is stated beside the save button. Private app state remains in memory; session credentials retain the existing sessionStorage behavior and are removed at logout.

The default limit is 100 MiB for the public library; one file cannot exceed 20 MiB. A complete edition is written atomically after every permitted file has downloaded. A network failure leaves the previous edition intact. Saved items can be updated, deleted individually or cleared together. Size and save date remain visible while offline. Downloads are not counted as study completion.

## Server policy

`GET /api/offline/theories/{id}` requires authentication and checks all of these independently of an admin/editor bypass:

1. The content kind is THEORY in the public ENT hierarchy.
2. The theory and every ancestor have a published snapshot and are not archived.
3. The published payload explicitly sets `offlineAllowed: true`.
4. The response uses a field allowlist containing only reading content. Editorial notes and answer keys cannot be exported by extending the payload schema.

Courses, lessons, assignments, quiz questions and student submissions cannot use this endpoint, even when the caller owns them. Private course access is never converted into an offline copy by enrollment alone.

Files default to `offline_allowed=false`. An editor records an explicit rights basis through `POST /api/cms/materials/{id}/offline-rights` before a file can accompany its public parent theory. Published files with a failed/infected scan are excluded. Existing trusted teacher files can retain an explicitly labeled unscanned status; this does not make them CLEAN. Student uploads use separate storage metadata and always require CLEAN to download. See [student files](STUDENT_FILES.md).

`GET /api/offline/materials/{id}` rechecks the current parent and file policy on every download. Responses remain `private, no-store`; explicit application code stores only the approved blob. There is no generic URL cache or server-side URL downloader.

An offline copy is a download, so a disconnected device cannot receive a later revocation. The library shows the saved version, not a claim of current publication. An explicit online update returning 404 removes the withdrawn edition. A failed network request does not falsely revoke it.

## Safe updates

The worker does not call `skipWaiting()` at installation. Existing sessions continue on their existing shell. An update requires an explicit click from `/`, `/settings` or `/saved`, with only one app tab open. The worker checks the current client list itself; a second tab, test or form blocks activation. The client reloads only the same safe path that received approval. Closing every app tab also permits the normal browser worker lifecycle.

Offline navigation opens `/saved`, which is outside the authenticated route guard. The private application does not pretend that a failed API response is cached progress. Practice submission and material uploads require a connection; there is no background queue that could replay them later.

References used for lifecycle and storage behavior: [MDN skipWaiting](https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerGlobalScope/skipWaiting), [MDN CacheStorage](https://developer.mozilla.org/en-US/docs/Web/API/CacheStorage), [web.dev PWA updates](https://web.dev/learn/pwa/update).

Browser verification evidence is recorded in [UX verification](UX_VERIFICATION.md); an implementation description alone is not a pass report.
