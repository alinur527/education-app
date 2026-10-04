# Frontend performance verification

Measured on 2026-10-04, completed at 04:52:08 UTC. The expansion remains responsive in this local test, but it is **not uniformly faster or smaller** than Phase 2. Initial JavaScript increased by 32,798 bytes (6.3%), and the expanded dashboard made three additional API requests. Warm reloads transferred no JavaScript over the network. No page/API errors, horizontal overflow, or tasks longer than 50 ms were observed in the 72 measured navigations. These results are not a claim of perfect frame rate or production Core Web Vitals.

## Controlled comparison and reproduction

Run from the repository root, with the two existing containers and the ignored synthetic student fixture available:

```powershell
.\.venv\Scripts\python.exe -X utf8 scripts/measure_ux.py
```

- Baseline: exact Phase 2 commit `e39293b`, built from `git archive` into `test-results/baseline-perf/frontend`, served at `http://127.0.0.1:8082`. Container image ID: `sha256:54c05b520f39db0109914d0f13a13007217d882d510c1a4f6acea8ed81184e7d`.
- Current: expansion working tree, served at `http://127.0.0.1:8081`. Container image ID: `sha256:5fe670168a18b87ff59119e166866422cc81075ea66a9d4d1b8abd93c9276702`. The measured entry file was `index-C6I9OEy4.js`, SHA-256 `de3843582ff9aa22c93b460a4412cf9fca7409644e1def51eaee7610692f96dd`.
- Both frontends used the **same upgraded backend, database, and student account**. This isolates frontend differences; it does not recreate the historical Phase 2 database or backend. Subject/topic/statistics/learning response hashes were unchanged before and after measurement. There were 18 subjects and 43 mathematics topics in the shared dataset.
- Chromium `145.0.7632.6`, headless on this Windows development workstation, loopback HTTP through Docker nginx, no CPU/network throttling, no other project browser suites or builds during the comparison. A 390-pixel viewport is a narrow desktop browser viewport, not real mobile hardware.
- Viewports: 1440×1000 and 390×844. Routes: dashboard `/`, subjects `/subjects`, and the mathematics subject catalog. Three repetitions per combination; baseline/current order alternated between repetitions. Each cold sample used a fresh browser context; its warm sample reloaded that same page/context. Server and OS caches were not flushed. There were 2 builds × 2 viewports × 3 routes × 3 repetitions × 2 cache states = **72 navigations**.
- Service workers were blocked for the comparison. Existing fixture authentication happened before timing, with the same token injected into session storage; the normal `/api/auth/me` restoration still ran and is included. No login typing time is included.
- Route readiness is measured from navigation start to the beginning of a 100 ms stable period with the expected route content, no loading-status element, no pending application fetch, and loaded fonts. Dashboard requires its hero and three subject cards; subjects requires all 18 cards; mathematics requires its visible topic rows. The 100 ms validation delay is excluded. This is a task-specific readiness measure, not INP or a browser-standard interactivity score.
- Resource Timing, paint, layout-shift, and long-task observers ran through network idle, loaded fonts, and two further animation frames. Observed windows were 546–662 ms after navigation. CLS is the maximum session-window sum within this short observation; later interaction shifts were not measured. LCP is the last observed candidate in that window.
- `test-results/ux-performance/raw.json` contains all samples, public resource paths/timings, asset hashes, and dataset hashes. `summary.json` contains medians/ranges; `test-results/ux-performance.log` contains the console summary. These local artifacts are ignored. The script stores no credentials, tokens, request headers, API response bodies, or sensitive HAR files.

## Route readiness

Milliseconds: median [minimum–maximum], three samples per cell. Small differences of a few milliseconds in this local, unthrottled run should not be treated as statistically established improvements.

| Width | Route       | Cache | Phase 2             | Expansion           |
| ----- | ----------- | ----- | ------------------- | ------------------- |
| 1440  | Dashboard   | Cold  | 118.2 [117.4–127.9] | 137.2 [130.5–145.9] |
| 1440  | Dashboard   | Warm  | 46.4 [45.8–59.8]    | 50.0 [46.5–61.0]    |
| 1440  | Subjects    | Cold  | 113.0 [92.0–116.3]  | 107.7 [93.5–114.7]  |
| 1440  | Subjects    | Warm  | 45.7 [45.5–47.1]    | 47.4 [46.9–52.6]    |
| 1440  | Mathematics | Cold  | 97.9 [95.6–98.3]    | 97.4 [96.7–99.2]    |
| 1440  | Mathematics | Warm  | 45.8 [43.3–46.9]    | 45.7 [45.5–47.0]    |
| 390   | Dashboard   | Cold  | 121.6 [112.4–128.0] | 142.0 [140.9–144.8] |
| 390   | Dashboard   | Warm  | 46.6 [46.0–47.0]    | 46.0 [44.4–46.5]    |
| 390   | Subjects    | Cold  | 96.4 [95.7–99.7]    | 101.3 [97.7–114.9]  |
| 390   | Subjects    | Warm  | 45.7 [45.7–45.8]    | 46.9 [46.8–47.2]    |
| 390   | Mathematics | Cold  | 85.9 [82.9–91.4]    | 93.5 [85.8–93.7]    |
| 390   | Mathematics | Warm  | 46.2 [45.6–47.5]    | 45.5 [45.4–46.3]    |

The mathematics API returned the same 43 topics to both builds. Phase 2 rendered all 43 together, creating 770 DOM elements. The expansion's initial learning tab rendered seven learning topics and 255 DOM elements; syllabus topics are available in a separate tab, with client-side page bounds. This is a deliberate change to the visible workload, **not proof that rendering the same 43 rows became faster**. It also does not reduce the topic API payload: both warm catalog reloads transferred 55,401 total bytes.

## Paints and layout shifts

FCP/LCP are cold-load medians in milliseconds. Dashboard CLS has nonzero variation, so its ranges are shown separately below. Subjects and mathematics had zero observed CLS in every cold and warm sample at both widths.

| Width | Route       | Phase 2 FCP / LCP | Expansion FCP / LCP |
| ----- | ----------- | ----------------- | ------------------- |
| 1440  | Dashboard   | 56 / 92           | 56 / 96             |
| 1440  | Subjects    | 56 / 84           | 64 / 88             |
| 1440  | Mathematics | 60 / 108          | 56 / 88             |
| 390   | Dashboard   | 56 / 84           | 60 / 144            |
| 390   | Subjects    | 52 / 72           | 56 / 96             |
| 390   | Mathematics | 52 / 96           | 56 / 80             |

| Dashboard | Phase 2 CLS median [range] | Expansion CLS median [range] |
| --------- | -------------------------- | ---------------------------- |
| 1440 cold | 0.0735 [0.0735–0.1597]     | 0.0796 [0.0377–0.0796]       |
| 1440 warm | 0.0735 [0.0735–0.1574]     | 0.0754 [0.0754–0.0796]       |
| 390 cold  | 0.0585 [0.0585–0.0585]     | 0.0904 [0.0845–0.0904]       |
| 390 warm  | 0.0585 [0.0585–0.0585]     | 0.0585 [0.0585–0.0585]       |

All 72 samples recorded zero long tasks over 50 ms. This only describes the measured navigation windows on this workstation; scrolling, typing, transitions, low-end hardware, and sustained frame timing were not profiled. Reserving dashboard loading/widget space remains an opportunity to reduce its observed shifts.

## JavaScript, compression, and requests

Every measured cold route loaded the same entry dependency set within its build. **Both deployed nginx configurations served JavaScript with identity encoding**, so Vite's gzip estimates are not actual network transfer sizes here. `transferSize` below is the browser's resource-transfer accounting including its header allowance, not a packet capture. A warm cached request is still a browser request event even when transfer size is zero.

| Initial JavaScript                                          | Phase 2 | Expansion |      Difference |
| ----------------------------------------------------------- | ------: | --------: | --------------: |
| Loaded JS files                                             |       3 |         6 |              +3 |
| Decoded / identity body bytes                               | 521,844 |   554,642 | +32,798 (+6.3%) |
| Cold browser transfer bytes                                 | 522,744 |   556,442 |         +33,698 |
| Warm browser transfer bytes                                 |       0 |         0 |               0 |
| Sum of separately gzip-9-compressed bodies, analytical only | 158,801 |   170,760 | +11,959 (+7.5%) |

The baseline build log, `test-results/expansion-baseline-perf-build.log`, reports approximately 73.33 kB entry + 447.91 kB vendor + 0.58 kB runtime and Vite gzip estimates of 21.46 + 138.92 + 0.36 kB. The served filenames and raw body sizes match that build. Its gzip estimate differs slightly from the script's explicitly defined gzip level 9 calculation. Earlier expansion build logs may describe different hashes, so the table uses the **actual served response bodies** for both builds.

| Served JS body       | Phase 2 bytes | Expansion bytes |
| -------------------- | ------------: | --------------: |
| Entry `index-*.js`   |        73,336 |          70,736 |
| Vendor `vendor-*.js` |       447,919 |         449,866 |
| Runtime              |           589 |             716 |
| Shared components    |             — |          29,219 |
| Shared model         |             — |           3,530 |
| Shared hooks         |             — |             575 |

The entry file itself is smaller, but the **complete initial dependency set is larger**. The math renderer, authoring, study-page, teacher, and admin route chunks were not fetched by these routes while service workers were blocked. The separate first-install service worker does pre-cache them, as described below. Enabling tested static-asset compression would be an additional deployment optimization; it was not silently enabled during this comparison.

Request counts and total transfer bytes were identical between widths. Counts include the document and assets as well as APIs; API counts include session restoration.

| Route       | Requests, Phase 2 → Expansion | API requests, Phase 2 → Expansion | Cold total bytes, Phase 2 → Expansion | Warm total bytes, Phase 2 → Expansion |
| ----------- | ----------------------------- | --------------------------------- | ------------------------------------- | ------------------------------------- |
| Dashboard   | 14 → 20                       | 4 → 7                             | 690,398 → 731,137                     | 30,434 → 31,576                       |
| Subjects    | 12 → 15                       | 2 → 2                             | 664,911 → 704,508                     | 4,947 → 4,947                         |
| Mathematics | 12 → 15                       | 3 → 3                             | 695,885 → 735,482                     | 55,401 → 55,401                       |

The dashboard adds study profile, task, and deadline queries. The visible new functionality accounts for additional work; the results do not establish that the dashboard became faster. Further combining or overlapping those requests could be assessed separately without changing the baseline measurement.

## Sanitized waterfall example

These are **sample 2, desktop cold dashboard** timestamps from navigation start, not medians. Only public path categories are shown; no tokens, headers, account IDs, or API bodies were captured in the report. Asset requests start in parallel, not one after another.

| Stage                               | Phase 2 start–end, ms | Expansion start–end, ms |
| ----------------------------------- | --------------------- | ----------------------- |
| Initial JS requests, aggregate span | 4.4–12.1              | 4.4–12.4                |
| Initial stylesheet requests         | 4.7–9.4               | 4.6–11.5                |
| First Cyrillic/Latin font requests  | 45.1–51.6             | 44.3–50.5               |
| `/api/auth/me`                      | 51.3–55.2             | 49.7–54.0               |
| `/api/learning/me`                  | 84.1–98.1             | 61.4–73.6               |
| `/api/subjects`                     | 84.3–92.3             | 61.6–68.5               |
| `/api/statistics/me`                | 84.4–92.1             | 61.9–68.3               |
| `/api/study/profile`                | —                     | 61.2–65.2               |
| Later font requests, aggregate span | 65.7–110.3            | 67.4–118.2              |
| `/api/study/tasks`                  | —                     | 116.4–123.0             |
| `/api/study/deadlines`              | —                     | 116.6–122.5             |

## Separate first service-worker installation

One additional fresh current-build context allowed the service worker. Navigation plus network idle plus `navigator.serviceWorker.ready` took **651.8 ms** on loopback. Its static cache contained **81 entries, including 26 JavaScript files and zero `/api/` entries**. This is one installation observation, not a three-sample comparison or an offline correctness test. Install time is not the route-ready metric above and includes the network-idle wait.

The worker pre-caches lazy route bundles, math assets, and fonts. Therefore splitting code avoids those chunks on the initial route with SW blocked, but does not eliminate their first-install download cost when PWA caching is enabled. Full installation transfer bytes were not instrumented in this run. API absence in Cache Storage verifies only this observed static-cache behavior; explicit offline content/export permissions and update behavior have separate functional tests.

## Interpretation limits

This controlled run supports the reported resource/cache behavior and short local navigation observations. Three samples are insufficient for robust tail-latency or field-performance conclusions. Real network latency, compressed delivery, devices, longer sessions, authenticated offline stores, interaction latency, accessibility, and subjective usability need their respective tests. The local data and new default syllabus/learning separation are disclosed above so that the smaller mathematics DOM is not presented as an unexplained speedup.
