# Local demonstration evidence

Measured source: `0b827f2d8faf39d03ebed062abbd204438e59239`. Tree: `a3f4ee1a6a3f750d827aa5de8aa4a7c0a9739f4b`. Clean at measurement.
Report generated: 2026-10-30T11:31:16.625583+00:00.

All eight A1-A8 acceptance groups passed locally: 29 actual PostgreSQL/HTTP/artifact tests and 11 Python report/harness tests, zero skips.

| Observation | Result |
|---|---|
| Scarce stock | 20 requests, 10 creations, 10 expected stock rejections |
| Concurrency | Peak client overlap 20; observed database lock waiters 8 |
| Rollback | Real post-flush exception, no partial rows/key, successful same-key retry |
| Restart | SIGKILL exit 137, exact original success/rejection replay, one reservation effect |
| Five-minute expiry | 301.347639 database seconds before explicit expiry decision |
| Cleanup | Zero owned Compose/Testcontainers containers; 1 retained volume(s) |

Real-clock creation: `2026-10-30T11:26:06.905953Z`; deadline: `2026-10-30T11:31:06.905953Z`; decision: `2026-10-30T11:31:08.253592Z`.
Image: `sha256:d4482c63b5b60fff8ba9c1bb011dbd75f85d3cc3056b339600877c651263c935`. JAR SHA-256: `2b46dda163527474bdc339f66aa56064f4ca9ca96978893b54f37aae0b95c51d`.
Environment: `{"architecture": "x86_64", "docker": "29.7.2", "java": "openjdk version \"25.0.5\" 2026-10-20 LTS", "os": "Linux", "python": "3.14.4"}`.

- Synthetic local demonstration; one host and one 20-request scarce-stock sample.
- Accelerated race tests use injected time; A8 separately uses the actual database clock.
- Expiry is explicit; overdue holds remain counted until processed.
- No hosted CI, remote deployment, production scale, SLA or commercial Java tenure is established by this local run.
