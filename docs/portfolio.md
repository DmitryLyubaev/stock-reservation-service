# Portfolio and interview notes

This independently synthetic project adds hands-on Java/Spring backend evidence alongside
existing commercial experience. It does not establish years of commercial Java delivery.

## Suggested CV wording

Built a synthetic Java 25/Spring Boot stock-reservation API with PostgreSQL transactions,
JPA/Hibernate, Flyway and durable idempotency. Verified contention, post-flush rollback,
expiry races and restart replay through actual HTTP/PostgreSQL tests; packaged with Docker
and a GitHub Actions workflow, verified locally and in GitHub Actions.

[Actual hosted verification](https://github.com/DmitryLyubaev/stock-reservation-service/actions/runs/41133721919) passed A1-A8 with 29 Java and 11 Python checks,
zero skips. See the [dated hosted report](publication-report-2026-10-31.md) for source,
clock, sample and limitations; local and hosted observations are separate samples.

## Concrete evidence to discuss

- Six HTTP routes and four states: ACTIVE, CONFIRMED, CANCELLED and EXPIRED.
- Global request-key identity stores the original status/body in the same transaction as
  stock changes. Replaying creation can return the old ACTIVE snapshot; GET shows now.
- Consistent request-key, inventory and reservation lock ordering; database time is sampled
  after business locks, so a request waiting past its deadline cannot confirm late.
- One measured local scarce-stock sample: twenty competing requests for ten units,
  ten creations and ten expected stock rejections, with persisted aggregate invariants.
- A real SQL flush followed by an injected exception leaves no partial state or completed
  key; a retry can succeed. Test controls are absent from the normal artifact.
- Actual SIGKILL restart retains exact original success and rejection responses.
- A separate actual database-clock run observed explicit expiry after 301.347639 seconds.
  Accelerated race tests use an injected clock and are labelled separately.

## Tradeoffs and limits

One product per reservation, one application/database, trusted loopback callers and
operator-triggered expiry keep the demonstration bounded. Overdue holds still count until
processed; request history lasts for the dataset lifetime. There is no scheduler, basket,
broker, distributed exactly-once delivery, cloud deployment or production SLA.

The local suite passed 29 Java and 11 Python checks with zero skips. These include artifact
and harness checks as well as actual PostgreSQL/HTTP tests; they are not forty independent
business scenarios. The twenty-request sample is not a throughput benchmark.

One deferred Minor remains: unsupported HTTP methods return 500 rather than 405/Allow,
without changing stock. See the implementation notes for the reviewed finding and limits.
