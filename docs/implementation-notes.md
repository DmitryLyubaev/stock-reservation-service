# Local implementation decisions and review

30 October 2026. Approved Native execution of the six-task plan, from baseline
`ec3af79dc7184be099c92306637693516a0d8aea`, in branch `codex/local-stock-reservation`.
All business records and code were independently created for this synthetic demonstration.

## Rulings made during execution

1. The app worktree API rejected the new product root outside the chat's selected folders.
   Used a project-local Git worktree instead. Cost if wrong: the app does not manage its
   cleanup; it must be removed explicitly after integration.
2. The pinned Maven image lacks unzip, causing Maven Wrapper to switch distribution
   formats and reject the pinned ZIP checksum. Derived a tooling-only image with unzip,
   retained checksum verification and set writable project-local Maven configuration.
   Cost if wrong: one additional local tooling package/image, separate from runtime.
3. google-java-format 1.37.0 failed Spotless 3.10.4 with a directly reproduced
   `JavaFormatterOptions.Style.valueOf(String)` incompatibility. Pinned 1.36.1 and kept
   the formatter gate. Removed the unsuccessful JVM-export hypothesis. Cost if wrong:
   newer formatter features are unavailable; business semantics do not change. The
   official pinned PostgreSQL digest uses Testcontainers' explicit postgres compatibility
   declaration, preserving the exact selected image rather than changing its identity.
4. Reused one test application per JVM and reset database, clock and failure probe before
   each case. Abrupt restart is measured separately with the normal packaged application.
   Cost if wrong: cross-case in-memory leakage; reset controls and serial classes bound it.
5. Transitive Maven metadata advertised unused authenticated vendor repositories. Used a
   project-local mirror to official Maven Central, without credentials/global settings.
   Cost if wrong: a future off-Central dependency fails visibly and requires a new decision.
6. The outage case stops/restarts its exact disposable, serial-suite-owned PostgreSQL
   container and preserves its data. Cost if wrong: accidental parallel tests could be
   interrupted; classes remain serial and recovery is in a finally block.
7. Docker's random host mapping changed after a real stop/start diagnostic. Allocate one
   free explicit loopback port for that test container's lifetime and assert it on restart.
   Cost if wrong: a small free-port allocation race causes a visible setup failure.
8. Task 6 embeds review, while Native execution orders it after the task completion gate.
   Gated implemented code/report first, then performed the final branch review and fix.
   Cost if wrong: task bookkeeping precedes review; overall integration readiness remained
   pending until the review and correction suite finished.
9. Reviewer set aside authentication, tenant isolation and production abuse controls.
   Trusted local loopback access stands. Cost if wrong: unsuitable for untrusted exposure.
10. Reviewer set aside automatic expiry and read/reserve reclamation. Explicit expiry
    stands. Cost if wrong: overdue holds reduce availability until an operation processes them.
11. Reviewer set aside request-history pruning. Dataset-lifetime retention stands.
    Cost if wrong: the database grows with local demonstration usage.
12. Reviewer set aside distributed availability, production throughput and hosted execution.
    No such claim is made. Cost if wrong: those environments/behaviors remain unproven.
13. Reviewer set aside actual network-response-loss injection. The approved captured-response
    plus actual SIGKILL/replay method stands. Cost if wrong: transport failure mechanics
    remain untested, although committed response persistence/replay is measured.
14. Reviewer set aside pending status/review checkboxes. Reconcile them during final
    documentation closeout. Cost if wrong: interim docs lag verification; integration was
    not claimed during that interval.

## Failures retained, then corrected

- Task 1: four real PostgreSQL foundation assertions failed before the migration/API;
  all passed after implementation. A Windows-to-WSL path launcher regression was corrected.
- Task 2: eight reservation/idempotency/validation checks failed before implementation;
  all passed afterward, including twenty identical concurrent requests.
- Task 3: seven lifecycle/expiry checks failed before transitions; all passed afterward.
- Task 4: a lock timeout incorrectly returned 500 instead of 503; actual SQLSTATE 55P03
  drove safe BUSY mapping. The outage fixture's changing port caused recovery errors;
  the owned stop/start diagnostic established and corrected that fixture defect.
- Task 5: normal artifact tests and packaged assertions preceded packaging. The first
  packaged observation used dirty source and is historical only. Clean-source all-mode
  subsequently passed, including 301.419636 real database seconds before explicit expiry.
- Task 6: eight report tests initially failed with no renderer; a real subprocess deadline
  test then exposed an unbounded child, and a cleanup fixture exposed acceptance of a
  retained test container. Corrected each after observing RED. Renderer fixtures are
  synthetic and never count as application observations.

Private raw logs, SQL observations, JUnit XML and failed attempts remain under ignored
`.local/`. The dated safe report identifies the exact measured clean source and image.
Later report/status commits are documentation, not retroactive measurements of their SHA.

## Publication boundary

No remote was created, no public push or hosted workflow ran, and no cloud/paid operation
was performed. The current RULES copy contains generic applicable policy. The original
private baseline history contains unrelated private planning identifiers; it must not be
published unchanged. A separate concrete publication preparation must sanitize history,
inspect the exact proposed public tree and enable the required repository protections.
Local success does not establish hosted CI, deployment, scale, an SLA, adoption or commercial
Java tenure. Explicit expiry has no background scheduler.

## Final review

One fresh read-only whole-branch reviewer (`gpt-6-astra`, high, fresh context) reviewed
`ec3af79..c607f36`. No Critical findings, one Important, one Minor. No second review.

Important: raw URI prefix checking let Spring's alternate routed spellings
`/%61pi/reservations` and `/api;ignored/reservations` bypass the POST boundary.
Actual HTTP RED proved both oversized valid bodies created a reservation (201), while
malformed/repeated keys returned 500 from database constraints. Removing the raw-path
exemption applies the boundary to POST regardless of spelling. Both regressions then
passed with the existing validation tests (4/4), with zero reservation/request rows from
rejected requests. Correction commit: `0b827f2`. Full clean correction study
`.local/studies/20261030T112312-56731` passed A1-A8 at clean source
`0b827f2d8faf39d03ebed062abbd204438e59239`: 29 Java and 11 Python tests,
zero skips/failures/errors, real expiry after 301.347639 seconds, exact restart replay,
zero owned containers and one retained volume. Later closeout commits change
documentation/status only.

Historical deferred Minor (now resolved in the [follow-up](http-method-followup-2026-10-31.md)): unsupported HTTP methods returned `500 INTERNAL_ERROR` rather than 405
with Allow. Clients receive the wrong error classification; stock correctness is unaffected.
This was re-graded and retained as Minor within the bounded demonstration, and not included
in the one Important-finding correction pass.

All six behaviors the reviewer declined to judge are resolved explicitly in rulings 9-14.
Private RED/GREEN evidence and the review record remain ignored under `.local/`.

## Local integration, 31 October 2026

At Dmitry's request, local `main` fast-forwarded from `ec3af79` to reviewed closeout
`f1d916a92c22cfcca902fb59d00435d4e83b057c`. Canonical verification on that clean merged
source passed 29 Java and 11 Python checks, zero failures/errors/skips, with zero owned
Testcontainers remaining. Private integration evidence is
`.local/results/20261030T211909-578`; its directory timestamp is UTC.

Original A1-A8 observations remain tied to their recorded source `0b827f2`. Integration
did not change executable code or rerun the packaged five-minute study. The later status
commit changes documentation only. All private results, credentials, caches and remaining
scratch files were preserved under the main checkout's ignored `.local/` before removing
the clean worktree and merged `codex/local-stock-reservation` branch. The existing deferred
Minor is unchanged. No remote, push, hosted execution, cloud action or runtime restart.

## Approved publication follow-up

31 October 2026: the preceding sections describe the earlier local implementation and
integration phases. Approved public publication and actual hosted A1-A8 verification
are now recorded in the [dated report](publication-report-2026-10-31.md). Original private
ancestry was excluded; implementation inputs and the deferred Minor are unchanged.

## HTTP method polish, 31 October 2026

Dmitry requested the remaining polish. A specific method-not-supported handler now
returns 405/METHOD_NOT_ALLOWED with Allow; a real HTTP regression observed RED then
GREEN and clean canonical verification passed 30 Java+11 Python. The original
unsupported-method Minor is closed. See the [follow-up](http-method-followup-2026-10-31.md).
