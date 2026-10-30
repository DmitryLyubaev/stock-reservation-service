# HTTP method response follow-up

31 October 2026, Australia/Brisbane. The previously deferred method-status Minor is resolved.

When routing rejects an unsupported method on an API route, it now returns
`405 METHOD_NOT_ALLOWED` with `application/problem+json` and Spring's `Allow` header.
Existing POST boundary validation can reject malformed requests before routing. Previously the generic error
handler converted the framework's method-not-supported exception into 500. The new specific
handler preserves the route's allowed methods without entering the command transaction.

Code commit: `8cb9e8328bbc142e4215b42a23fd55b4d235c51b`. Local canonical on that clean source passed **30 Java and
11 Python checks**, zero failures/errors/skips and zero remaining owned Testcontainers.
Maven reported 23.442 seconds, one warm-cache local run. Canonical JAR SHA-256:
`fe6fe739837dc3cdfb826b6c958c8af76d715f098403dcc217298b862eceb160`.

The added real HTTP/PostgreSQL regression first observed 500 instead of 405. After the
handler change, five focused validation tests passed. It checks unsupported PUT on both
a POST route and a GET route, verifies 405/problem shape/Allow, and asserts no reservation
or request-key rows plus persisted stock invariants. The full suite then passed 30+11.

The [standard hosted workflow](https://github.com/DmitryLyubaev/stock-reservation-service/actions/workflows/ci.yml)
runs the same canonical checks and packaged A1-A8 study on the actual pushed source.
The README badge and Actions history identify the current ref's observed result.
Historical 29-test [publication measurements](publication-report-2026-10-31.md) and
their safe receipt remain unchanged and bound to their original source. This is a
functional follow-up, so the original publication-input digest is historical, not a
digest of this patched source.

No inventory, reservation state, transaction, expiry, idempotency, schema, dependency or
deployment behavior changed. The actual CV document is maintained by Dmitry; the
[portfolio notes](portfolio.md) provide copyable project wording and links.
