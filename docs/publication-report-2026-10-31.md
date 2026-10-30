# Publication and hosted verification

31 October 2026, Australia/Brisbane. Published with explicit approval to
[DmitryLyubaev/stock-reservation-service](https://github.com/DmitryLyubaev/stock-reservation-service).
Secret scanning and push protection were enabled and read back before the first push.
The original private repository/history were preserved and excluded from public ancestry.

## Actual hosted result

[GitHub Actions run 41133721919](https://github.com/DmitryLyubaev/stock-reservation-service/actions/runs/41133721919), attempt 1, passed on
source `305d38d67fb1f46f7d45896e56a9bc0905855f9e`, tree `b769229e264b3564fb0ab98bda9c183674b6443a`.
Standard Ubuntu 24.04 job: `2026-10-30T21:48:29Z` to `2026-10-30T21:55:08Z`,
399 seconds. One observed job, not a performance benchmark.

| Check | Observed result |
|---|---|
| Canonical verification | 29 Java and 11 Python checks, zero failures/errors/skips |
| A1-A8 | All eight acceptance groups passed |
| Scarce stock | 20 requests, 10 creations, 10 expected stock rejections |
| Concurrency | Peak client overlap 20; observed database lock waiters 20 |
| Rollback | Real post-flush exception, no partial rows/key, successful same-key retry |
| Restart | Actual SIGKILL exit 137, exact original success/rejection replay, one effect |
| Real-clock expiry | Explicit expiry decision after 300.188579 database seconds |
| Cleanup | Zero owned Compose/Testcontainers; one runner volume at cleanup |

Real creation: `2026-10-30T21:50:04.930289Z`; deadline: `2026-10-30T21:55:04.930289Z`;
decision: `2026-10-30T21:55:05.118868Z`. Accelerated race cases use injected time separately.
Image: `sha256:8ddcdacd6be581e1c58757add10a667193e8b0dcbfe178ac720d3ffd4539878e`.
Canonical JAR SHA-256: `2b46dda163527474bdc339f66aa56064f4ca9ca96978893b54f37aae0b95c51d`.
Environment: `{"architecture": "x86_64", "docker": "28.0.4", "java": "openjdk version \"25.0.5\" 2026-10-20 LTS", "os": "Linux", "python": "3.12.3"}`.

[Safe aggregate evidence](evidence/hosted-2026-10-31.json) combines the validated check
summary with actual GitHub run/job metadata. The generated acceptance report alone does
not attest its hosting environment. Raw logs, private preparation files and credentials
were not committed or uploaded as artifacts. The workflow uses job logs/summary, read-only
permissions and no artifact/cache upload, larger/self-hosted runner or paid feature.

One named volume was retained on the job's hosted runner at cleanup. No application or
database cloud deployment was created. Historical [local evidence](local-report.md) stays
tied to its original measured source; those results are not relabelled as this hosted run.

## Source and scope

All 61 implementation inputs and stored modes match the reviewed local source.
Implementation-input digest:
`375cf38667b44f4cfe65a42385220cb3878d997fea068a4879da89c520a9b9c2`.
Later publication/status commits change documentation only; their exact-ref workflow
results are available from the repository's Actions history and README badge.

One independently synthetic service/database, one product per hold, trusted loopback
callers, explicit expiry and dataset-lifetime request retention remain the boundary.
The one twenty-request sample does not establish production throughput, availability,
distributed exactly-once delivery, customer adoption or commercial Java tenure.
The previously deferred unsupported-method 500 versus 405/Allow Minor remains.
No new application features, provider evaluations, paid resources or licensing terms.
