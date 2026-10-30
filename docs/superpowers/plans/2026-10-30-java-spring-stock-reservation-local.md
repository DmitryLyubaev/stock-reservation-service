# Java/Spring Stock Reservation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Execution:** Approved 30 October 2026, Native. All six tasks, one fresh review and bounded correction pass verified. Merged into local main and reverified on 31 October 2026; no publication or hosted run.

**Goal:** Deliver the approved bounded synthetic stock-reservation API with actual PostgreSQL concurrency, duplicate-request, rollback, expiry and restart evidence.

**Architecture:** One Spring Boot application owns inventory, reservations and request results in one PostgreSQL database. Short READ COMMITTED transactions lock the request key, inventory and reservation in that order; a database-clock decision and stored response commit together. Docker Compose serves the local demonstration, while Testcontainers exercises real PostgreSQL through HTTP.

**Tech Stack:** Java 25, Spring Boot 4.1.2, Maven 3.10.0, JPA/Hibernate, PostgreSQL 17.11, Flyway 12.4.0, Testcontainers 2.0.5, JUnit 6.0.3, Docker/Compose and small standard-library Python demonstration scripts.

**Spec:** [Approved Project 20 design](../specs/2026-10-30-java-spring-stock-reservation-design.md), approved by Dmitry on 30 October 2026. Read it and [RULES.md](../../../RULES.md) before execution.

## Global Constraints

- This is a bounded personal CV/portfolio demonstration, not a production system.
- One product and quantity per reservation; `ACTIVE`, `CONFIRMED`, `CANCELLED`, `EXPIRED`.
- `0 <= held <= on_hand <= initial_quantity`; verify aggregate reservation/counter equality from committed PostgreSQL state.
- The lifetime is a fixed five minutes from creation, not supplied by the caller.
- Use PostgreSQL READ COMMITTED with explicit pessimistic write locks.
- All mutation paths use the same lock order: request-key row, inventory row, reservation row if one exists.
- An operation samples database `clock_timestamp()` once after acquiring all business-row locks and uses that instant for its decision and timestamps.
- Every mutation requires an `Idempotency-Key`, 1..128 ASCII letters/digits or `._:-`.
- Order reference is 1..64 ASCII letters/digits or `._:-`; quantity is an integer in 1..1,000,000; JSON limit is 2 KiB.
- Commit the result and business changes in the same transaction; unexpected failures roll back all changes.
- Reads are side-effect free; overdue ACTIVE holds remain counted until an explicit expiry/confirm/cancel processes them.
- The normal artifact contains no mutation/failure/test-clock controls.
- Local demo trusts callers on this machine: no application login, roles or tenant claim. Docker publishes the API only on `127.0.0.1`; PostgreSQL has no host-published port.
- Use actual PostgreSQL through Testcontainers; missing Docker is a failing prerequisite, never a silent skip.
- No employer material, other-project implementation reuse, cloud resources, paid calls, remote creation, public push or hosted execution.

## Review Focus

1. JSON coercion must not turn decimal, string, boolean, null or overflowing quantities into valid integers. Task 2 pins strict 400 responses and no request rows.
2. Repeated physical idempotency headers and unknown mutation members must fail before claiming a key. Tasks 1/2 cover the actual HTTP boundary.
3. A stored insufficient-stock response must remain a rejection after stock is released; a new key may succeed. Task 3 exercises both, including restart in Task 5.
4. A request begun before expiry but blocked until the deadline must expire the hold rather than confirm it. Task 4 holds a real database lock and advances only the test clock.
5. Database outage/timeout and a lost response must not be mistaken for business rejection or duplicate completion. Task 4 checks rollback/503; Task 5 checks committed replay after abrupt restart.

## Execution environment, pins and size

Product root: `E:\Projects\stock-reservation-service`, not yet created. Use a distinct
`codex/local-stock-reservation` implementation branch and an isolated workspace according
to the execution skill. Do not move or modify other projects. All file paths below are
relative to this product root unless explicitly identified as planning-repo paths.

Read-only preparation found Windows Java 8 with no native Maven/Docker commands. WSL
Ubuntu has Java 21.0.12.1, Docker Engine 29.7.2 and Compose 5.4.0, linux/amd64. Therefore
run Java 25 builds inside the pinned Maven container through WSL; no global JDK install
or machine Java change. Port 8220 was observed free; recheck at execution time.

[Planning lock](project-20/toolchain-lock.json) contains official registry digests,
platform descriptors, the Maven distribution SHA-512 and availability observations.
Manifest bytes were SHA-256 checked against registry headers. These prove availability,
not successful pull/build/runtime compatibility. Copy the lock into `config/toolchain-lock.json`
in Task 1 and verify the selected artifacts at use time.

| Component | Pin |
|---|---|
| Build image | `maven:3.10.0-eclipse-temurin-25-noble@sha256:51a751cf747bad5968979a66142d443e8d94854172c3891ae2496edec544c6b3` |
| Runtime image | `eclipse-temurin:25-jre-noble@sha256:2ef7a81085e0502d7d4ff7e7c0257b6d4f46433f8ea6021180aa3c8a2b9ca3ec` |
| Database image | `postgres:17.11-bookworm@sha256:839fbb5a4942a7758dd93e18b42ea5649807491a0545b5eeeb8f0b8bb11857a5` |
| Maven Wrapper plugin | `org.apache.maven.plugins:maven-wrapper-plugin:3.3.4`, only-script distribution |
| Formatting | Spotless Maven plugin `3.10.4`, google-java-format `1.37.0` |
| Boot-managed dependencies | Hibernate `7.4.13.Final`, PostgreSQL JDBC `42.7.14`, Flyway `12.4.0`, Testcontainers `2.0.5`, JUnit `6.0.3`, Surefire/Failsafe `3.5.6` |

Use Boot's parent/BOM, not separate dependency overrides. Include the PostgreSQL Flyway
module and the Testcontainers 2 module coordinates (`testcontainers-postgresql`,
`testcontainers-junit-jupiter`); do not paste obsolete Testcontainers 1 package imports.
Generate `mvnw`/`mvnw.cmd` for Maven 3.10.0, verify its ZIP against the recorded SHA-512,
and set `distributionSha256Sum` from those verified bytes. Pin build plugins explicitly
or through Boot parent management. No preview features, Lombok or interactive API UI.

Container verification runs with the checkout UID/GID, access to the Docker socket group,
host networking, `TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1`, and an ignored writable
`.local/maven` cache/home. This lets tests reach loopback-bound PostgreSQL host mappings
without changing firewall settings or publishing the demo database. In the Testcontainers
fixture request ephemeral PostgreSQL port bindings on `127.0.0.1` and verify Docker's
actual binding. Keep Ryuk enabled for cleanup. Test app listeners also bind loopback.

Choose a three-second database lock timeout and eight-second statement timeout per
mutation transaction, a 24-connection pool and 32 request threads. Client timeout is
20 seconds; each accelerated integration case has a 60-second outer deadline. The
real-clock case has a seven-minute deadline. Do not impose a throughput/latency target.

Planning estimate: six cohesive tasks, approximately 2-3 focused implementation days,
including toolchain bring-up, one final review/fix pass and evidence. This is a working
estimate, not a measured duration. Recommend Native execution because these tasks share
transaction/HTTP contracts and the local synthetic service has a small blast radius.

## File responsibilities and shared contracts

Java package: `dev.dlyubaev.stockreservation`; abbreviate it as `J` below, meaning
`src/main/java/dev/dlyubaev/stockreservation`. `T` means the equivalent `src/test/java` path.

- `pom.xml`, `.mvn/wrapper/*`, `mvnw*`, `.gitattributes`, `.gitignore`, `config/toolchain-lock.json`:
  fixed toolchain, LF shell scripts and ignored secrets/results.
- `J/StockReservationApplication.java`, `J/config/HttpConfiguration.java`,
  `src/main/resources/application.yaml`: startup, strict JSON, loopback and validated config.
- `J/http/JsonBoundaryFilter.java`, `J/http/ApiErrors.java`, `J/http/ProblemBody.java`:
  body/media constraints and safe stable errors.
- `J/inventory/{Inventory,InventoryRepository,InventoryController}.java`: counters,
  product locking and read-only inventory endpoint.
- `J/commands/{OperationResult,CommandIdentity,RequestStore,IdempotentCommands}.java`:
  canonical request identity, transactional key claim, saved status/body and replay.
- `J/reservations/{Reservation,ReservationState,ReservationRepository,ReservationService,ReservationController,ReservationDtos}.java`:
  single-product lifecycle, typed request/snapshot DTOs and HTTP routing.
- `J/time/{DecisionClock,DatabaseDecisionClock}.java`: one transaction-bound database time source.
- `src/main/resources/db/migration/V1__stock_reservations.sql`: schema and constraints;
  `scripts/seed.sql`: three synthetic products `DEMO-RED`, `DEMO-BLUE`, `DEMO-GREEN`, ten units each.
- `T/support/{PostgresFixture,HttpFixture,DatabaseAssertions,ControlledClock,TransactionProbe}.java`:
  actual PostgreSQL, HTTP helpers, committed-state queries and test-only control.
- `T/*IT.java`: Failsafe HTTP/database acceptance; `T/*Test.java`: focused validation/unit checks.
- `Dockerfile`, `compose.yaml`, `.dockerignore`, `scripts/{verify.sh,demo.sh,cleanup.sh,acceptance.py,report.py}`,
  `openapi.yaml`, `README.md`, `docs/local-report.md`, `.github/workflows/ci.yml`: reproducibility and safe evidence.

Shared production interfaces are fixed here so each task uses the same names:

```java
record OperationResult(int status, String body, boolean replayed) {}
interface DecisionClock { Instant now(); }
record ReserveRequest(String orderReference, String productCode, Integer quantity) {}
enum ReservationState { ACTIVE, CONFIRMED, CANCELLED, EXPIRED }
String CommandIdentity.canonicalize(String operation, String target, Object validatedBody);
OperationResult IdempotentCommands.execute(String key, String identity, Supplier<OperationResult> work);
OperationResult ReservationService.reserve(ReserveRequest request);
OperationResult ReservationService.transition(UUID id, ReservationState requestedTerminal);
ReservationSnapshot ReservationService.read(UUID id);
```

Use snake_case JSON (`order_reference`, `product_code`, `created_at`, `expires_at`,
`decided_at`, `as_of`, `overdue`) and Java record names above. `ReservationSnapshot`
contains UUID id, order reference, product code, quantity, state, creation/expiry/nullable
decision instants; read adds `as_of`/`overdue` without changing stored command snapshots.
`ProblemBody` contains `type`, `title`, `status`, `code`, `detail`, `instance`; all business
error fields are deterministic. Serialize command responses once to a UTF-8 JSON string
and store that text unchanged. Replay may add `Idempotency-Replayed: true` as a header.

`IdempotentCommands.execute` is the externally invoked Spring transaction boundary;
controller validation occurs first and the controller supplies the service callback.
Service mutations require an existing transaction. JdbcTemplate key/time queries share
the JPA transaction's connection. Return normal business rejection values, not exceptions
that roll back overdue expiry. Technical exceptions escape and map to safe errors only
after transaction rollback. Never return an HTTP success before the proxy has committed.

## Task 1: Runnable PostgreSQL foundation and HTTP boundary

**Files:** Create toolchain/config files, application/config/http/inventory files,
`V1__stock_reservations.sql`, `scripts/seed.sql`, `scripts/verify.sh`,
`T/support/{PostgresFixture,HttpFixture,DatabaseAssertions}.java`, `T/FoundationIT.java`.

**Interfaces:** Produces `InventoryRepository.lockByProductCode(String): Optional<Inventory>`,
read endpoint `GET /api/inventory/{productCode}`, and `DatabaseAssertions.assertInvariants()`.
`HttpFixture` starts the actual application at a random loopback port and uses Java's HTTP
client. `PostgresFixture` supplies a fresh disposable PostgreSQL 17.11 database and fails
when Docker is unavailable. `DatabaseAssertions` writes safe query text/result rows to
the current ignored `.local/results/<run-id>/` record so the final report has persisted-state
evidence, not just a test count. Fixtures also record source identity, times and image IDs.

- [x] **1.1 Establish the isolated repository and toolchain.** Set personal author identity,
  copy the lock, generate/verify the Maven Wrapper, and use Boot web MVC, validation,
  data JPA, Flyway/PostgreSQL and test starters with the pinned parent. Configure Spotless,
  Surefire `*Test` and Failsafe `*IT`, with test parallelism off and skipped tests forbidden.
  `scripts/verify.sh` runs pinned Java 25 `./mvnw -B spotless:check verify` via WSL Docker,
  records exact command/output/exit status privately and preserves nonzero exit status.
- [x] **1.2 Write `FoundationIT` first.** Assertions: fresh database has successful Flyway
  V1; explicit seed produces exactly three ten-unit products; GET `DEMO-RED` is 200 with
  `on_hand=10`, `held=0`, `available=10`; unknown product is 404; direct negative held,
  held above on-hand and invalid reservation quantity inserts violate constraints.
  HTTP tests also prove oversized streamed/chunked body is 413, non-JSON mutation is
  415, no permissive CORS response and no credential/SQL/stack text in error bodies.
- [x] **1.3 Run `./scripts/verify.sh -Dit.test=FoundationIT` and observe RED.** Missing
  schema/endpoint/constraint or boundary behavior is the expected failure, not Docker absence.
- [x] **1.4 Implement the foundation.** Schema tables are `inventory`, `reservations`,
  `request_results`; the last has unique key, canonical identity, nullable response status
  and response text populated before commit. Add FK, state/quantity/counter/time checks.
  Set Hibernate `ddl-auto=validate`, disable open-in-view, enforce strict bounded JSON
  reading even without Content-Length and use typed ProblemBody handling. Schema contains
  no seed data; `seed.sql` initializes only an empty disposable demo dataset and refuses
  reseeding nonempty data. Inspect actual container bindings and migration startup logs.
- [x] **1.5 Repeat the focused command and full `./scripts/verify.sh`; expect GREEN, zero skips.**
  Capture Java/Maven/dependency/image versions; verify no control/test classes in the JAR.
- [x] **1.6 Commit only Task 1 files:** `feat: add PostgreSQL service foundation and HTTP boundary`.

## Task 2: Transactional reservation creation and stored duplicate results

**Files:** Create commands files, `Reservation`, `ReservationState`, `ReservationRepository`,
`ReservationService`, `ReservationController`, `ReservationDtos`, time files,
`T/ReservationCreationIT.java`, `T/IdempotencyIT.java`, `T/RequestValidationIT.java`.

**Interfaces:** Consumes Task 1 locking/fixture/assertions. Produces the shared
`reserve`, `read`, `execute`, `canonicalize` and clock interfaces above, plus
`RequestStore.claimAndLock(String key, String identity): RequestRecord` and
`RequestStore.complete(String key, int status, String body): void`. `RequestRecord`
is a JDBC-mapped record, not an additional ORM persistence context.

- [x] **2.1 Write failing HTTP tests.** Reserve two units: 201 ACTIVE, held=2/on-hand=10,
  expiry exactly creation+300 seconds and GET matches immutable fields. Same-key requests
  with reordered JSON return byte-equal body/status and one row/effect. Same key with
  changed quantity/product/order returns 409 IDEMPOTENCY_CONFLICT.
  Twenty concurrent copies of one key have one effect. Unknown product returns stored
  404; insufficient stock returns a stored 409 with a byte-equal repeat. Two different keys with the same order
  reference are distinct reservations. Assert all aggregate invariants.
- [x] **2.2 Add strict input tests with no request rows.** Quantities `0`, `-1`, `1000001`,
  `1.5`, `"1"`, `true`, `null`, oversized integers; missing/unknown members; invalid key
  characters/length; duplicate physical key headers; whitespace-modified identifiers;
  malformed JSON and UUID. All are 400 except documented 413/415 boundary errors.
- [x] **2.3 Run `./scripts/verify.sh -Dit.test=ReservationCreationIT,IdempotencyIT,RequestValidationIT`; expect RED.**
- [x] **2.4 Implement creation and request execution.** Claim with `INSERT ... ON CONFLICT
  DO NOTHING`, then lock/read the key in the same transaction. Compare exact canonical
  identity before any inventory lock; replay a completed result unchanged. Lock product,
  sample database time and persist the hold or normal rejection, then complete the key.
  Lock/statement timeouts are set locally on the transaction. Never commit a pending key.
  Preserve status/body as text; generate snapshot timestamps only once. Java instants
  are normalized to PostgreSQL microsecond precision before storing/serializing.
- [x] **2.5 Repeat focused and full verification; expect GREEN and no orphan request rows.**
  Confirm the PostgreSQL response-log and inventory updates share the same commit boundary.
- [x] **2.6 Commit:** `feat: reserve stock with durable idempotent responses`.

## Task 3: Confirm, cancel and explicit database-clock expiry

**Files:** Modify reservation service/controller/DTO/repository; create
`T/support/ControlledClock.java`, `T/ReservationLifecycleIT.java`, `T/ExpiryBoundaryIT.java`.

**Interfaces:** Consumes Task 2 executor/clock/read interfaces. Produces
`transition(UUID id, ReservationState requestedTerminal)` and three JSON POST routes
ending `/confirm`, `/cancel`, `/expire`. Each requires an empty object and a key.
`ControlledClock.set(Instant)` exists only in test source and is reset between tests.

- [x] **3.1 Write lifecycle tests before implementation.** Confirm two units gives held=0,
  on-hand=8; cancel releases held and leaves on-hand=10. Terminal repeat with a fresh key
  returns 200 without effect; conflicting terminal action is 409 INVALID_STATE.
  Expire before deadline is 409 NOT_DUE. At exact expiry and after, expire yields EXPIRED;
  confirm/cancel instead yields stored 409 RESERVATION_EXPIRED while committing expiry
  and releasing held. Replay original creation after confirmation returns original ACTIVE
  snapshot; GET returns CONFIRMED. Replay a NOT_DUE result after time advances stays NOT_DUE.
- [x] **3.2 Pin read semantics.** GET overdue ACTIVE has `overdue=true` without writing;
  inventory still includes held, and a new reservation does not sweep it. Explicit expiry
  restores availability. Test unknown ID 404 and stable saved rejection for each route.
  Reserve all ten units, record a different key's insufficient-stock rejection, cancel
  the hold, then prove replay of the rejection stays byte-equal while a new key succeeds.
  Reuse a creation/transition key across operation or target and require
  409 IDEMPOTENCY_CONFLICT without changing its original response or either reservation.
- [x] **3.3 Run `./scripts/verify.sh -Dit.test=ReservationLifecycleIT,ExpiryBoundaryIT`; expect RED.**
- [x] **3.4 Implement the specified transition table.** Locate immutable product without
  a lock, acquire inventory then reservation write locks, re-read state, then sample
  `DecisionClock`. At `now >= expires_at`, due ACTIVE confirm/cancel materializes EXPIRED
  and returns a business rejection value that commits. Terminal states never change.
  No scheduler, background scan, HTTP clock input or production failure-control endpoint.
- [x] **3.5 Repeat focused and full verification; expect GREEN.** Inspect counters and
  request results after every expected conflict, including committed expiry on a 409.
- [x] **3.6 Commit:** `feat: add reservation terminal states and explicit expiry`.

## Task 4: Actual lock contention, rollback and technical failure evidence

**Files:** Create `T/support/TransactionProbe.java`, `T/StockContentionIT.java`,
`T/TransactionRollbackIT.java`, `T/ReservationRaceIT.java`, `T/DatabaseFailureIT.java`.
Modify transaction/error mapping only when a failing test demonstrates a missing behavior.

**Interfaces:** Consumes all production contracts and shared HTTP/PostgreSQL fixtures.
Test-only `TransactionProbe` holds a JDBC row lock from an independent connection,
observes blocked sessions via `pg_stat_activity`/`pg_locks`, and releases via an AutoCloseable
handle. A test-context repository spy throws once after real `saveAndFlush`, after both
stock and reservation SQL writes have executed. Neither facility is packaged in main.

- [x] **4.1 Write scarce-stock assertions.** Release 20 independent HTTP clients from one
  barrier against ten units, each with a distinct key and order reference. Require exactly
  ten 201s, ten 409 INSUFFICIENT_STOCK, ten ACTIVE rows, held=10/on-hand=10/available=0,
  twenty completed request results and zero unexpected failures. Retain actual request
  start/end times and peak overlap; the server uses independent connections/transactions.
- [x] **4.2 Write deterministic race assertions.** Hold the inventory lock, start a confirm
  before deadline, observe its database wait, move test time to the exact deadline and
  release: require committed EXPIRED and 409 RESERVATION_EXPIRED. Exercise confirm/expire
  and confirm/cancel contention; assert only allowed terminal outcomes, one counter effect
  and no stale overwrite. Include a real-clock query-under-lock test that brackets
  DatabaseDecisionClock.now with PostgreSQL wall-clock observations, so clock wiring is
  not proven solely by injection.
- [x] **4.3 Write rollback/timeout assertions.** Inject the post-flush exception: HTTP 500,
  no hold, original counters and no request-key row; same-key retry succeeds once. Hold
  a business lock beyond three seconds: HTTP 503 BUSY, no completed key/effect, then
  successful retry after release. Stop a dedicated disposable database for one separate
  case: safe technical failure, not a stored 404/409. Recreate only that fixture and
  inspect transaction state. Never stop the shared Docker daemon or unrelated containers.
- [x] **4.4 Run `./scripts/verify.sh -Dit.test=StockContentionIT,TransactionRollbackIT,ReservationRaceIT,DatabaseFailureIT`.**
  Record actual failures first. Existing correct behavior may already be GREEN; do not
  break it to manufacture RED. For discovered defects, preserve the reproducing failure
  and make only the minimal production correction, then rerun that case.
- [x] **4.5 Run full verification; require all eight designed invariant paths covered,
  no skips, no leaked test transactions/listeners/containers.** Preserve slow/failing samples
  rather than silently replacing them with favourable reruns. No production throughput claim.
- [x] **4.6 Commit:** `test: prove stock contention rollback and expiry races`.

## Task 5: Built application, abrupt restart and genuine five-minute evidence

**Files:** Create `Dockerfile`, `compose.yaml`, `.dockerignore`,
`scripts/{demo.sh,cleanup.sh,acceptance.py}`, `T/ArtifactBoundaryIT.java`.
Extend `scripts/verify.sh` with a separate `--packaged` mode; keep the default canonical
verification complete for formatting/build/focused/PostgreSQL checks.

**Interfaces:** `demo.sh start|seed|reserve|confirm|cancel|expire|show|stop` drives actual
HTTP/Compose operations. `acceptance.py --base-url URL --results PATH --mode restart|real-clock|all`
uses Python stdlib HTTP/JSON, invokes only the exact Compose-owned process/container,
and writes per-case safe results. Exit 0 means all requested cases passed; failure or
not-run required cases exit nonzero. `cleanup.sh` stops only the fixed Project 20 Compose
project and records retained volumes. No global prune or delete of another worktree.

- [x] **5.1 Write packaged-case assertions.** The production JAR contains no test support,
  controlled clock, rollback spy or test-only route. The container runs as a fixed non-root
  user. Compose publishes only `127.0.0.1:8220` for API and no PostgreSQL host port; native
  configuration binds loopback. Fresh startup migrates and explicit seed makes fixtures.
- [x] **5.2 Build the normal artifact and image from the pinned builder/runtime parents.**
  Use the normal JAR, default database clock and no test configuration. The API listens
  on the container interface only under Compose's loopback host publication. Health/readiness
  may use the known inventory GET after seed; add no new administration platform.
- [x] **5.3 Run `./scripts/verify.sh --packaged --mode restart`.** Create a committed success
  and business rejection, save original HTTP status/body, abruptly SIGKILL the owned API
  container, retain PostgreSQL, start the same image and replay both keys. Require
  byte-equal body/status, one effect and unchanged aggregate invariants. Treat the stored
  response as the caller's lost response and show fresh GET state separately. Timestamp
  shutdown/restart and capture image/source identity; a graceful stop is not crash evidence.
- [x] **5.4 Run `./scripts/verify.sh --packaged --mode real-clock`.** In a fresh isolated
  dataset reserve one unit; early expiry is 409 NOT_DUE. Wait the actual five minutes
  using short interruptible polling (at most five seconds per wait) and a seven-minute
  deadline. A new expiry key becomes 200 EXPIRED at/after the stored database deadline;
  held returns to zero. Preserve database/source times and early/late responses. No
  changed lifetime, injected clock or reused early-rejection key for the due operation.
- [x] **5.5 Run default verification and packaged checks; expect zero failed/skipped
  required cases.** Always clean up owned processes/containers in finally/trap handling;
  preserve safe results and report retained data/evidence volumes explicitly.
- [x] **5.6 Commit:** `feat: package local reservation demo and restart acceptance`.

## Task 6: Reproducible report, API documentation and local closeout

**Files:** Create `scripts/report.py`, `openapi.yaml`, `README.md`, `docs/local-report.md`,
`.github/workflows/ci.yml`; modify demonstration scripts only for evidence gaps.
Update only Project 20 status/links in planning-repo tracker/handover at closeout.

**Interfaces:** `report.py --results PATH --output PATH` reads actual verification records,
requires A1-A8 dispositions/source/environment/clock provenance and returns nonzero if
required evidence is absent. It must not invent passed cases from successful HTTP codes.
Allow a report of observed failure, explicitly labelled; passing acceptance still requires
all required cases. Report generation never calls the API or reruns a scenario.

- [x] **6.1 Write report validation checks.** Missing source, missing case, simulated clock
  mislabelled real, absent persisted invariant queries or an unexpected skipped case cannot
  yield a passing summary. Use tiny synthetic report fixtures solely for renderer checks;
  they are never the reported application evidence.
- [x] **6.2 Write runnable docs and OpenAPI.** Include six routes, strict input/error rules,
  raw curl examples with required keys, stable-response versus current-GET distinction,
  explicit-expiry limitation and a small architecture/state Mermaid diagram. Document
  WSL execution, first-download/cache behavior, owned cleanup and retained volumes. Define
  standard GitHub Actions for the same offline checks and packaged smoke with timeouts,
  safe artifacts and cleanup, but do not create a remote, push or trigger hosted execution.
- [x] **6.3 Run a clean frozen setup and the final canonical plus packaged verification.**
  Capture exact clean executable source SHA/tree and any later docs-only commit separately,
  toolchain/image identities, commands and all A1-A8 results. Report the actual 20-request
  sample and limits, accelerated versus five-minute observations and expected rejections.
  Record pass/fail/not-run truthfully; do not describe local results as hosted/cloud proof.
- [x] **6.4 Run one fresh whole-branch review against the approved design/plan.** Follow the
  selected execution skill's review procedure. Reproduce Important findings with focused
  failing evidence, fix them in one bounded correction pass, rerun affected checks and the
  final canonical suite. List any deferred Minor finding with its practical effect; do not
  expand this into repeated production-hardening audits.
- [x] **6.5 Commit the safe dated report/docs and record local integration readiness.**
  Verify personal author, inspect staged files for raw credentials/logs and keep private
  evidence ignored. Stop owned resources and record retained volumes. Update Project 20
  tracker/handover with exact evidence/review/source and the pending integration/publication
  distinction. Product code must be committed; public repository/CI remain a separately
  approved CV-visibility milestone. Follow the finishing skill for integration choice.

## Self-review and handoff

| Design acceptance | Owning tasks |
|---|---|
| A1 migration, reserve/read/confirm | 1, 2, 3 |
| A2 release, validation, transitions | 2, 3 |
| A3 duplicate identity and replay | 2, 3, 5 |
| A4 20-request/10-unit contention | 4 |
| A5 post-write rollback and retry | 4 |
| A6 clock boundaries and actual races | 3, 4 |
| A7 abrupt restart persistence | 5 |
| A8 real five-minute run and built startup | 1, 5 |

Self-review this plan against the design before handing it over: all invariants and A1-A8
map to tasks; clock/error/status rules agree across tasks; declared signatures/types are
consistent; every Review Focus entry has an owning test; task boundaries have their own
checkable deliverables. No extra subagent plan review is required.

Review the plan and choose execution before implementation. Native is recommended:
the same agent implements all six tasks, then one fresh whole-branch reviewer checks the
result. Subagent-driven is also available: a fresh implementer and reviewer per task,
followed by a final review, at higher context cost. Preserve the chosen method thereafter.

Primary version sources: [Boot dependency POM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom),
[Maven downloads](https://maven.apache.org/download.cgi),
[Boot Testcontainers documentation](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html),
and the official metadata/registry endpoints recorded in the planning lock.

Self-review completed on 30 October 2026: A1-A8 and the five Review Focus entries have
owning tasks; cross-operation duplicate tests follow the lifecycle routes they need;
declared interfaces, error/clock semantics and pin references agree with the approved
design. This is document verification, not implementation/build/runtime evidence.
