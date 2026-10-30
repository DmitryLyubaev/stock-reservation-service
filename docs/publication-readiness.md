# Publication provenance and readiness

31 October 2026, Australia/Brisbane. Local publication candidate only; no public repository,
push or hosted workflow execution is established by this document.

## History and inputs

The public candidate begins from a fresh root containing the reviewed source snapshot.
The existing private repository and original evidence remain intact. Unrelated private
planning identifiers in its earlier history are excluded by publishing no ancestor objects.
Historical source IDs in dated reports remain historical IDs; they are not reachable public
commits, and their observations are not relabelled as measurements of a new public commit.

The source snapshot is private main `ee6a387fd9ca3bd249acd8d221e01cffb18e7bd9`.
Its executable implementation matches the full A1-A8 measured source `0b827f2` and the
post-merge canonical source `f1d916a`. Publication changes README/portfolio/provenance
documentation and the workflow's reporting step only. No application, tests, fixtures,
dependency pins, API contract, Docker configuration or demonstration script is changed.

The publication preparation receipt records the exact sorted per-file SHA-256 mapping,
implementation digest, final Git commit/tree, bundle and tracked-file manifest. These
identities are kept outside the measured payload to avoid self-referential commit claims.

## Verification status

The original clean study passed A1-A8: 29 Java and 11 Python checks, twenty scarce-stock
submissions with ten creations/ten expected rejections, actual rollback/races/outage,
SIGKILL replay and a genuine five-minute expiry run. See the dated local report for source,
clock, image, sample and limitations. A later clean main canonical run also passed 29+11.

A fresh native-Linux clone of public snapshot `03d06bbe916b618d3a7682ea8560e1a82d340c3a` passed canonical verification: **29 Java and 11 Python checks**, zero failures/errors/skips and zero remaining owned Testcontainers. Maven reported 52.390 seconds. The clone began with an empty per-checkout Maven cache; existing host Docker image/build caches were available. No private local credentials were copied. This is a local Linux run, not a GitHub-hosted run.

The root-history Gitleaks 8.30.1 scan and actionlint 1.7.12 passed. The final approval proposal records the final history scan, tree/bundle identities and publication-only review after this documentation update. The full A1-A8 packaged study was not rerun during publication preparation because implementation inputs are unchanged.

Implementation-input digest: `375cf38667b44f4cfe65a42385220cb3878d997fea068a4879da89c520a9b9c2` (61 files, stored Git modes and SHA-256 of each blob). The digest is SHA-256 of UTF-8 compact sorted JSON mapping paths to `mode` and `sha256`. Inputs comprise `src`, `scripts`, `tests`, `config`, `.mvn`, and the top-level Maven wrappers/POM, Dockerfiles, Compose, Docker/Git attributes and ignore files, and OpenAPI contract.

## Proposed hosted execution

The workflow uses a standard Ubuntu 24.04 runner, read-only repository permission and
pinned checkout without persisted credentials. It runs the canonical checks and actual
packaged restart/expiry study, then always attempts owned cleanup. Only safe aggregate
Markdown is printed to logs and the job summary; no artifact/cache upload, self-hosted or
larger runner, provider credential, paid evaluation or application deployment is requested.

The target proposal is `DmitryLyubaev/stock-reservation-service`, public, with the existing
license-file state unchanged (no added license). Repository creation, protection settings,
push and actual hosted execution await explicit approval under the portfolio rules.

The existing deferred unsupported-method status Minor remains. Publication does not add
commercial Java tenure, real orders, customer adoption, production scale or availability.
