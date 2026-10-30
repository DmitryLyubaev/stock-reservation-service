# Project 20: Java/Spring stock-reservation service

Date: 30 October 2026, Australia/Brisbane.
Status: design and Native plan approved by Dmitry on 30 October 2026. All six local tasks, one fresh review and one correction pass are verified. Merged into local main and reverified on 31 October 2026; publication remains pending.

One fresh read-only builder check on 30 October returned "nothing important left".
This checks design completeness only; no implementation or runtime validation occurred.

## Intent and agreed scope

Build a bounded personal CV/portfolio demonstration of Java/Spring transactional backend
engineering. One independent synthetic API reserves a single product and quantity for an
order reference, then confirms, cancels or expires the reservation. Its value is a small
working service with meaningful PostgreSQL observations and reproducible failure cases.

Dmitry accepted single-product reservations, the four states, stable duplicate-request
results and conflicting-key rejection. He accepted the proposed 20-request/10-unit
scarce-stock case and confirmation/expiry race, then selected the recommended Approach A.
Dmitry then approved the actual written document on 30 October 2026, including the
assistant-proposed defaults below. Local implementation planning is authorized.

## Approaches

| Approach | Trade-off | Decision |
|---|---|---|
| A. Short transactions with row locks | Requests changing the same product wait briefly; the inventory invariant and reservation transitions have a straightforward serialization point. | Recommended and accepted by Dmitry. |
| B. Optimistic version checks | Detects competing updates without acquiring a write lock at the first read, but needs explicit conflict/retry handling under scarce-stock contention. | Not selected for this bounded demonstration. |

## Goals, boundaries and failure conditions

Demonstrate Java/Spring request validation, JPA/Hibernate transactions, Flyway migrations,
database-backed duplicate handling, concurrent stock safety, rollback, expiry races and
restart persistence. Use actual PostgreSQL through Testcontainers and a runnable local
Docker setup. Finish with concise setup/API examples, architecture/state diagrams and a
dated source-bound report. A small focused acceptance set determines completion.

Excluded: multi-product baskets, multiple warehouses, replenishment, payment or shipping,
external order integrations, a frontend, an identity provider, brokers, Kubernetes,
cloud hosting, paid inference, production SLAs and arbitrary scale testing. All schemas,
records and examples are created independently; nothing comes from the employer or
another portfolio service's implementation. Projects 19/21 and Fabric are independent.

Failure conditions include overselling, releasing/consuming the same hold twice, partial
stock changes after rollback, silently accepting conflicting key reuse, confirming an
already-due hold, or presenting mocked/injected-time results as live-clock evidence.

## Proposed engineering defaults

- Independent local repository `E:\Projects\stock-reservation-service`, created only
  after design approval, with personal Git identity from RULES.md. No remote creation,
  public push, hosted execution or cloud action is part of implementation authorization.
- Java 25, Spring Boot 4.1.2, Maven Wrapper, Spring MVC/validation, Spring Data JPA with
  Boot-managed Hibernate, PostgreSQL 17, Flyway and Testcontainers. Use a checked-in
  OpenAPI document and REST examples; an interactive documentation frontend is optional.
- One application and one database. Docker Compose is the canonical runnable demo;
  integration tests start a disposable PostgreSQL container. Do not substitute H2.
- Pin exact Maven distribution, compatible dependency versions, PostgreSQL patch/image
  digest and JDK image during planning. Boot's documented Java compatibility and
  PostgreSQL 17 support were checked on 30 October; actual dependency/build compatibility
  remains to be verified. Avoid preview features and independent Hibernate overrides.
- Local demo trusts callers on this machine: no application login, roles or tenant claim.
  Native API binds to loopback. Docker publishes the API only on `127.0.0.1`; PostgreSQL
  has no host-published port. Do not add a tunnel or remote listener. Disable CORS and
  require JSON for mutations. The boundary is isolation from remote callers, not
  protection from another local process or a claim of authenticated authorization.
- Namespace Compose projects, networks, volumes and containers for Project 20. Choose a
  free API port during planning without stopping unrelated services. Keep local database
  credentials in ignored files/environment, omit them from reports, and stop only owned
  resources. No generated credentials are committed.

This is one modest local implementation plan, one fresh final review/correction pass,
and a dated demonstration. Estimate during planning after checking local prerequisites.
Public CV visibility is desirable later; actual publication and hosted CI require a
separate concrete decision under RULES.md.

## Inventory, reservations and invariants

Seed three fictional product codes with ten units each in an explicit demo-data step.
Flyway creates the schema; Hibernate validates it and never creates/updates it. No API
can edit seed inventory. Clean test scenarios use isolated databases or deliberate reset
of disposable owned data, never an application reset endpoint.

Inventory stores product code, initial quantity, on-hand quantity and held quantity.
Availability is `on_hand - held`, derived rather than independently updated. Reservation
stores UUID, order reference, product code, quantity, creation time, expiry time, state
and terminal decision time. Identity, product, quantity, order reference and expiry are
immutable after creation. An order reference is descriptive, not a deduplication key;
different keys may deliberately create distinct reservations for the same reference.

For each product after every committed transaction:

- `0 <= held <= on_hand <= initial_quantity`.
- `held = sum(quantity of reservations whose persisted state is ACTIVE)`.
- `on_hand = initial_quantity - sum(quantity of CONFIRMED reservations)`.
- Reservation quantities are integers in 1..1,000,000; available stock is still the
  actual admission limit. Database checks, foreign keys and unique keys back the
  relevant row-level rules. Aggregate invariants are checked from persisted state.

| Operation on ACTIVE | Condition at decision time | Committed effect |
|---|---|---|
| Reserve a new hold | Sufficient availability | Create ACTIVE; increase held by quantity. |
| Confirm | Decision time is strictly before expiry | Set CONFIRMED; decrease held and on-hand by quantity. |
| Cancel | Decision time is strictly before expiry | Set CANCELLED; decrease held by quantity. |
| Expire | Decision time is at or after expiry | Set EXPIRED; decrease held by quantity. |
| Confirm or cancel an overdue hold | Decision time is at or after expiry | Set EXPIRED and release held; return `409 RESERVATION_EXPIRED`. |

Terminal states cannot change. A fresh key asking for the already-achieved terminal
action returns `200` with the current reservation and changes nothing. Other terminal
transitions return `409 INVALID_STATE`. Expiry before the deadline returns
`409 NOT_DUE` without a change. Confirming means consuming stock and recording the
synthetic order reference; no separate order-management service or order entity is needed.

## Clock and expiry

The lifetime is a fixed five minutes from creation, not supplied by the caller.
Store UTC instants with PostgreSQL `timestamptz`; the deadline is inclusive for expiry.
An operation samples database `clock_timestamp()` once after acquiring all business-row
locks and uses that instant for its decision and timestamps. Transaction-start time is
not suitable when a request has waited on a lock. Creation samples after the inventory
lock. An injected clock is available only through test wiring, never an HTTP time input
or mutable demo endpoint.

Expiry is explicit: a CLI/script calls the per-reservation expire endpoint. There is no
scheduler or batch scan. Confirm/cancel also materialize overdue expiry as above. Reads
are side-effect free: a due ACTIVE reservation remains persisted ACTIVE until processed,
and GET includes `as_of` and `overdue` so this is visible. Inventory still counts that
hold, conservatively reducing availability. New reserve requests do not sweep old holds.
The documented expiry command releases them; this operational limitation is intentional.

Test before, equal-to and after the deadline with injected time, including a request
that starts before expiry but obtains locks after it. Also record one genuine five-minute
database-clock waiting case, with both observed times, separately from accelerated tests.
Time precision must be consistent between Java, stored values and equality assertions.

## Transactions and duplicate requests

Use PostgreSQL READ COMMITTED with explicit pessimistic write locks. All mutation paths
use the same lock order: request-key row, inventory row, reservation row if one exists.
The product can be located by an initial non-locking reservation read because its product
is immutable; re-read and validate state after locks. Do not hold a reservation lock
while waiting to acquire inventory. Reads need no write locks. Keep transactions short
with bounded database lock/statement waits, mapped to a retryable `503 BUSY`.

Every mutation requires an `Idempotency-Key`, 1..128 ASCII letters/digits or `._:-`.
Keys are globally scoped to this single local demo and retained for the dataset lifetime.
The canonical identity includes method, operation/target and validated body values;
JSON member order is irrelevant, strings are case-sensitive, unknown fields are rejected.
Do not trim or otherwise silently rewrite identifiers. Order reference is 1..64 ASCII
letters/digits or `._:-`; product code is a bounded exact seeded identifier. Limit JSON
bodies to 2 KiB. Syntax/validation errors return `400` before claiming a key.

Claim a unique request-key row transactionally using PostgreSQL conflict handling,
without catching a unique violation and continuing a rollback-only JPA transaction.
The builder may use a narrow native insert for this claim. A competing same-key request
waits for the first transaction and then compares canonical identity. Same identity
returns the stored original HTTP status and JSON body; different identity returns
`409 IDEMPOTENCY_CONFLICT` without changing the original record or inventory.

Commit the result and business changes in the same transaction. Retain successful and
validated business-rejection responses, including `404`, insufficient stock and invalid
transitions. Do not retain validation errors, lock timeout or unexpected `5xx` as a
completed request. In particular, a failed transaction leaves neither a stock change nor
a completed key, so retry may execute. A committed request whose HTTP response is lost
is safely replayed after restart. Response metadata may mark a replay without changing
the stored body/status. A replay of creation returns its original ACTIVE snapshot even
if the reservation later changed; GET retrieves current state.

Expected business errors that also materialize expiry must commit that expiry and their
stored response; do not throw a rollback-triggering exception for that normal outcome.
Unexpected failures roll back all changes. Set response timestamps before persisting the
response so a retry does not manufacture a new timestamp. No automatic client retry,
distributed exactly-once or unbounded key retention claim follows.

## API contract

| Route | Input/result |
|---|---|
| `GET /api/inventory/{productCode}` | On-hand, held, availability and observation time; unknown product is `404`. |
| `POST /api/reservations` | JSON order reference/product code/quantity and key; `201` reservation or `409 INSUFFICIENT_STOCK`; unknown product is `404`. |
| `GET /api/reservations/{id}` | Current reservation with `as_of`/`overdue`; unknown ID is `404`. |
| `POST /api/reservations/{id}/confirm` | Empty JSON object plus key; `200` or documented conflict. |
| `POST /api/reservations/{id}/cancel` | Empty JSON object plus key; `200` or documented conflict. |
| `POST /api/reservations/{id}/expire` | Empty JSON object plus key; `200` or documented conflict. |

Use typed DTOs, explicit enum serialization and a consistent RFC-style problem JSON
shape with status, stable code, safe detail and request path. Invalid UUID/quantity,
missing key, unknown members, unsupported body or malformed JSON have explicit validation
responses; oversized bodies return `413`, unsupported media type `415`. Internal errors
return a safe `500`; database contention timeout returns `503`. No SQL, stack trace or
credential appears in responses. Store stable problem fields for replayed business errors.

## Acceptance and measured evidence

All business integration cases use the actual HTTP/application/PostgreSQL path with
independent transactions and committed-state inspection. Test methods do not wrap the
application in a transaction that could hide its real commit boundaries. Small unit
tests are appropriate for validation/clock mapping but cannot replace database evidence.

| ID | Required observation |
|---|---|
| A1 | Fresh Flyway migration and demo seed; reserve/read/confirm once; correct counters and persisted reference; terminal repeat changes nothing. |
| A2 | Cancel and due expiry each release precisely once; early expiry, unknown IDs/products, invalid quantities and conflicting terminal actions have documented outcomes. |
| A3 | Sequential and simultaneous identical-key requests yield one effect and equal original status/body; conflicting body or target reuse is rejected. Replay survives a later state change. |
| A4 | One bounded sample of 20 simultaneous distinct-key one-unit requests against ten units: exactly ten creations and ten insufficient-stock results, no unexpected errors, and all aggregate invariants hold in PostgreSQL. Record actual start overlap and durations; no timing threshold or production-throughput claim. |
| A5 | A test-only injected exception after inventory/reservation writes but before commit produces a failed HTTP response with no partial rows/counters/completed key; retry with the same key succeeds once. Hook absent from the normal artifact/API. |
| A6 | Before/equal/after clock cases, lock-wait-across-deadline, confirm-versus-expire and confirm-versus-cancel races yield one valid terminal effect and consistent inventory. At least one race must actually contend on a business lock; report outcomes rather than relying on the thread launch alone. |
| A7 | Abruptly stop the owned application after a committed mutation, retain PostgreSQL, restart and GET/replay the exact request; stable result and one effect. Also repeat a stored rejection after restart. |
| A8 | One actual five-minute clock run proves early rejection and due expiry with source times; label injected-time tests separately. Apply migrations to a fresh PostgreSQL container and verify the built Docker application starts and serves the API. |

The canonical verification command covers formatting, compilation, focused tests and
PostgreSQL integration cases and fails if Docker/PostgreSQL prerequisites are missing;
do not quietly skip them. Define bounded test timeouts and reliable owned cleanup.
Use deterministic barriers/test-only coordination for contention and rollback rather
than random sleeps. The normal artifact contains no mutation/failure/test-clock controls.

Retain a concise dated report with exact source SHA/tree, clean/dirty status, runtime and
image versions, commands, pass/fail/not-run dispositions, HTTP results, counter queries,
sample sizes, clock provenance, concurrency overlap and rollback/restart observations.
Expected business rejections are not infrastructure failures. Preserve initial failures
when corrected. Include an architecture/state diagram and runnable API examples in the
README, plus precise limitations. Report local and any later hosted execution separately.
Raw private logs/credentials and generated scratch output stay ignored; commit only safe
summaries/fixtures. Stop owned application/test containers after verification and report
retained evidence/data volumes separately from running resources.

## Builder discretion and approval boundary

The builder chooses package layout, DTO names, exact column/index names, test framework
utilities, exact timeout values, free port, precise image/dependency pins, and scripts
within these semantics. Pick the simplest implementation and document those choices in
the plan. A typed service/repository/controller structure is sufficient. Do not add a
generic event system, audit platform, broker or production-hardening project.

Approval of this document authorizes preparation of the local implementation plan,
not a public push, hosted CI or cloud deployment. The
plan must be reviewed before product implementation starts. RULES.md remains authoritative
for personal identity, secrets, employer separation and outward actions.

## Primary references checked 30 October 2026

- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html):
  Boot 4.1.2 accepts Java 17 through 27 and Maven 3.6.3 or later; Java 25 is within that
  range. Third-party build compatibility still requires verification.
- [Spring Data JPA locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html):
  repository methods can specify explicit lock modes.
- [PostgreSQL 17 row locking](https://www.postgresql.org/docs/17/explicit-locking.html):
  row locks last through the transaction and serialize competing writes/lockers.
- [PostgreSQL versioning policy](https://www.postgresql.org/support/versioning/):
  PostgreSQL 17 is supported; pin a current patch during planning.
- [PostgreSQL date/time functions](https://www.postgresql.org/docs/current/functions-datetime.html):
  `clock_timestamp()` reflects current time while `CURRENT_TIMESTAMP` uses transaction
  start, which matters for lock-wait expiry checks.
