#!/usr/bin/env python3
"""Validate source-bound local observations and render safe aggregate evidence."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REQUIRED_CLASSES = {"FoundationIT", "ReservationCreationIT", "IdempotencyIT", "RequestValidationIT",
                    "ReservationLifecycleIT", "ExpiryBoundaryIT", "StockContentionIT", "TransactionRollbackIT",
                    "ReservationRaceIT", "DatabaseFailureIT", "ArtifactBoundaryIT"}


class EvidenceError(ValueError):
    pass


def need(condition, message):
    if not condition:
        raise EvidenceError(message)


def load(path):
    need(path.is_file(), "missing evidence: " + path.name)
    return json.loads(path.read_text())


def parse_time(value):
    parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    need(parsed.tzinfo is not None, "timezone missing")
    return parsed


def source_of(value, reference=None):
    need(re.fullmatch(r"[a-f0-9]{40}", value.get("source_sha", "")) is not None, "source SHA missing or invalid")
    need(re.fullmatch(r"[a-f0-9]{40}", value.get("source_tree", "")) is not None, "source tree missing or invalid")
    need(value.get("source_dirty") is False, "source was dirty")
    if reference:
        need(value["source_sha"] == reference["source_sha"] and value["source_tree"] == reference["source_tree"], "source mismatch")


def persisted(value):
    need(value.get("query") and value.get("rows"), "persisted invariants missing")
    for row in value["rows"]:
        need(0 <= row["held"] <= row["on_hand"] <= row["initial_quantity"], "inventory invariant violated")
        need(row["held"] == row["active_sum"], "held invariant violated")
        need(row["on_hand"] == row["initial_quantity"] - row["confirmed_sum"], "confirmed invariant violated")


def check_artifact(artifact, suite):
    source_of(artifact, suite)
    need(artifact["runtime_user"] == "10001:10001", "runtime user mismatch")
    need(artifact["api_binding"] == [{"HostIp": "127.0.0.1", "HostPort": "8220"}], "API binding mismatch")
    need(not artifact["database_host_bindings"], "database has a host port")
    need(re.fullmatch(r"sha256:[a-f0-9]{64}", artifact["image_id"]) is not None, "image identity missing")


def validate(results):
    canonical = results / "canonical"
    packaged = results / "packaged"
    suite = load(canonical / "verification.json")
    source_of(suite)
    need(suite.get("full_suite") is True and suite.get("exit_code") == 0, "canonical verification not complete/passing")
    need(suite.get("testcontainers_remaining") == 0, "testcontainer cleanup incomplete or unobserved")
    need(suite["java_failures"] == suite["java_errors"] == suite["java_skipped"] == 0, "failed or skipped Java tests")
    need(suite["python_tests"] > 0 and suite["python_exit_code"] == suite["python_skipped"] == 0, "failed or skipped report tests")
    classes = suite["classes"]
    need(REQUIRED_CLASSES <= {item["name"].rsplit(".", 1)[-1] for item in classes}, "required acceptance test class missing")
    need(all(item["tests"] > 0 and item["failures"] == item["errors"] == item["skipped"] == 0 for item in classes), "failed or skipped class")
    need(sum(item["tests"] for item in classes) == suite["java_tests"], "JUnit count mismatch")
    invariants = list(canonical.glob("invariants-*.json"))
    need(invariants, "persisted invariants missing")
    for path in invariants:
        item = load(path)
        need(item["source_sha"] == suite["source_sha"], "source mismatch in persisted observations")
        persisted(item)
    probes = {name: load(canonical / (name + ".json")) for name in
              ("A4-scarce-stock", "A5-rollback", "A6-lock-wait-deadline", "A6-terminal-races", "A6-real-clock-adapter")}
    for value in probes.values():
        source_of(value, suite)
    scarce = probes["A4-scarce-stock"]
    need(scarce["submissions"] == 20 and scarce["created"] == scarce["insufficient_stock"] == 10,
         "scarce-stock counts invalid")
    need(scarce["peak_client_overlap"] >= 2 and scarce["observed_lock_waiters"] >= 2, "concurrency overlap missing")
    rollback = probes["A5-rollback"]
    need(rollback["injected_after_real_flush"] is True and rollback["failed_status"] == 500 and
         rollback["post_failure_reservations"] == rollback["post_failure_keys"] == 0 and rollback["retry_status"] == 201,
         "rollback evidence invalid")
    wait = probes["A6-lock-wait-deadline"]
    need(wait["clock_source"] == "injected" and wait["observed_waiters"] >= 1 and wait["state"] == "EXPIRED" and wait["status"] == 409,
         "injected deadline race invalid")
    races = probes["A6-terminal-races"]
    need(races["clock_source"] == "injected" and {item["opponent"] for item in races["observations"]} == {"cancel", "expire"},
         "terminal races missing")
    for item in races["observations"]:
        need(item["observed_waiters"] >= 2 and sorted([item["confirm_status"], item["other_status"]]) == [200, 409], "terminal race invalid")
        need(item["state"] in ({"EXPIRED"} if item["opponent"] == "expire" else {"CONFIRMED", "CANCELLED"}), "terminal outcome invalid")
    adapter = probes["A6-real-clock-adapter"]
    need(adapter["clock_source"] == "database" and parse_time(adapter["before"]) <= parse_time(adapter["observed"]) <= parse_time(adapter["after"]) and
         parse_time(adapter["observed"]) > parse_time(adapter["transaction_start"]), "database clock adapter invalid")
    artifact = load(packaged / "artifact.json")
    check_artifact(artifact, suite)
    restart = load(packaged / "restart.json")
    need(restart.get("case") == "A7" and restart.get("status") == "pass", "A7 failed or not run")
    for name in ("before", "after"):
        check_artifact(restart[name], suite)
    need(restart["exit_code"] == 137 and restart["exact_status_body_replay"] is True and restart["success_status"] == 201 and
         restart["rejection_status"] == 409 and restart["reservations"] == 1 and restart["completed_keys"] == 2, "restart replay invalid")
    need(restart["before"]["image_id"] == restart["after"]["image_id"] and
         restart["before"]["database_container"] == restart["after"]["database_container"], "restart identity changed")
    persisted(restart["persisted_invariants"])
    clock = load(packaged / "real-clock.json")
    need(clock.get("case") == "A8" and clock.get("status") == "pass", "A8 failed or not run")
    need(clock["clock_source"] == "database", "A8 requires database clock, not injected time")
    check_artifact(clock["artifact"], suite)
    created, expiry, decision = (parse_time(clock[name]) for name in ("created_at", "expires_at", "decided_at"))
    need((expiry - created).total_seconds() == 300 and parse_time(clock["early_at"]) < expiry <= decision, "real-clock boundary invalid")
    need(clock["early_status"] == 409 and clock["due_status"] == 200 and clock["early_replay_unchanged"] is True and
         abs(clock["elapsed_database_seconds"] - (decision - created).total_seconds()) < 0.00001, "real-clock observation invalid")
    persisted(clock["persisted_invariants"])
    cleanup = load(packaged / "cleanup.json")
    need(cleanup["remaining_owned_containers"] == 0, "owned cleanup incomplete")
    return {"status": "pass", "observed_at": dt.datetime.now(dt.timezone.utc).isoformat(),
            "source_sha": suite["source_sha"], "source_tree": suite["source_tree"], "source_dirty": False,
            "acceptance": {"A" + str(i): "pass" for i in range(1, 9)}, "java_tests": suite["java_tests"],
            "python_tests": suite["python_tests"], "skipped": 0, "jar_sha256": suite["jar_sha256"],
            "environment": suite["environment"], "image_id": artifact["image_id"],
            "scarce_stock": {key: scarce[key] for key in ("submissions", "created", "insufficient_stock", "peak_client_overlap", "observed_lock_waiters")},
            "rollback": {"post_flush_failure": 500, "retry": 201, "partial_rows": 0}, "restart": {"exit_code": 137, "exact_replay": True},
            "real_clock": {key: clock[key] for key in ("created_at", "expires_at", "decided_at", "elapsed_database_seconds")},
            "testcontainers_remaining": 0, "cleanup": cleanup, "limitations": ["Synthetic local demonstration; one host and one 20-request scarce-stock sample.",
            "Accelerated race tests use injected time; A8 separately uses the actual database clock.",
            "Expiry is explicit; overdue holds remain counted until processed.",
            "No hosted CI, remote deployment, production scale, SLA or commercial Java tenure is established by this local run."]}


def capture(results, exit_code, full_suite):
    results.mkdir(parents=True, exist_ok=True)
    log = (results / "maven.log").read_text()
    classes = []
    destination = results / "junit"
    destination.mkdir(exist_ok=True)
    for name in dict.fromkeys(re.findall(r"\[INFO\] Running ([\w.$]+)", log)):
        path = ROOT / "target/failsafe-reports" / ("TEST-" + name + ".xml")
        need(path.is_file(), "JUnit XML missing: " + name)
        suite = ET.parse(path).getroot()
        classes.append({"name": name, **{key: int(suite.attrib.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}})
        shutil.copyfile(path, destination / path.name)
    python_log = (results / "python-tests.log").read_text()
    count = re.search(r"Ran (\d+) tests?", python_log)
    skipped = re.search(r"skipped=(\d+)", python_log)
    environment = {"os": platform.system(), "architecture": platform.machine(), "python": platform.python_version(),
        "docker": subprocess.check_output(["docker", "version", "--format", "{{.Server.Version}}"], text=True, timeout=20).strip(),
        "java": subprocess.run(["docker", "run", "--rm", "--network", "none", "--entrypoint", "java", "p20-stock-tooling:local", "-version"],
                               text=True, capture_output=True, check=True, timeout=30).stderr.splitlines()[0]}
    jar = ROOT / "target/stock-reservation-service-0.1.0-SNAPSHOT.jar"
    remaining = None
    fixture_path = results / "postgres-fixture.json"
    if fixture_path.is_file():
        session = load(fixture_path).get("session_id", "")
        need(re.fullmatch(r"[a-f0-9-]{36}", session) is not None, "testcontainer session identity missing")
        deadline = time.monotonic() + 20
        while True:
            owned = subprocess.check_output(["docker", "ps", "-aq", "--filter",
                "label=org.testcontainers.sessionId=" + session], text=True, timeout=20).split()
            remaining = len(owned)
            if remaining == 0 or time.monotonic() >= deadline:
                break
            time.sleep(0.25)
    result = {"kind": "canonical", "source_sha": os.environ["P20_SOURCE_SHA"], "source_tree": os.environ["P20_SOURCE_TREE"],
        "source_dirty": os.environ["P20_SOURCE_DIRTY"] == "true", "full_suite": full_suite, "exit_code": exit_code,
        "java_tests": sum(item["tests"] for item in classes), "java_failures": sum(item["failures"] for item in classes),
        "java_errors": sum(item["errors"] for item in classes), "java_skipped": sum(item["skipped"] for item in classes),
        "python_tests": int(count[1]) if count else 0, "python_skipped": int(skipped[1]) if skipped else 0,
        "python_exit_code": int(os.environ["P20_PYTHON_EXIT"]), "classes": classes, "environment": environment,
        "testcontainers_remaining": remaining,
        "jar_sha256": hashlib.sha256(jar.read_bytes()).hexdigest() if jar.is_file() else None}
    (results / "verification.json").write_text(json.dumps(result, indent=2) + "\n")
    if exit_code == 0:
        need(remaining == 0, "testcontainer cleanup incomplete or unobserved")
        need(classes and result["java_tests"] > 0 and result["java_failures"] == result["java_errors"] == result["java_skipped"] == 0,
             "failed, missing or skipped Java verification")
        need(result["python_tests"] > 0 and result["python_exit_code"] == result["python_skipped"] == 0, "failed, missing or skipped report verification")


def markdown(summary):
    if summary["status"] != "pass":
        return "# Local demonstration evidence\n\nStatus: incomplete or failed.\n\n" + summary["reason"] + "\n"
    sample = summary["scarce_stock"]
    clock = summary["real_clock"]
    return f"""# Local demonstration evidence

Measured source: `{summary['source_sha']}`. Tree: `{summary['source_tree']}`. Clean at measurement.
Report generated: {summary['observed_at']}.

All eight A1-A8 acceptance groups passed locally: {summary['java_tests']} actual PostgreSQL/HTTP/artifact tests and {summary['python_tests']} Python report/harness tests, zero skips.

| Observation | Result |
|---|---|
| Scarce stock | {sample['submissions']} requests, {sample['created']} creations, {sample['insufficient_stock']} expected stock rejections |
| Concurrency | Peak client overlap {sample['peak_client_overlap']}; observed database lock waiters {sample['observed_lock_waiters']} |
| Rollback | Real post-flush exception, no partial rows/key, successful same-key retry |
| Restart | SIGKILL exit 137, exact original success/rejection replay, one reservation effect |
| Five-minute expiry | {clock['elapsed_database_seconds']:.6f} database seconds before explicit expiry decision |
| Cleanup | Zero owned Compose/Testcontainers containers; {len(summary['cleanup']['retained_volumes'])} retained volume(s) |

Real-clock creation: `{clock['created_at']}`; deadline: `{clock['expires_at']}`; decision: `{clock['decided_at']}`.
Image: `{summary['image_id']}`. JAR SHA-256: `{summary['jar_sha256']}`.
Environment: `{json.dumps(summary['environment'], sort_keys=True)}`.

""" + "\n".join("- " + item for item in summary["limitations"]) + "\n"


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "capture":
        parser = argparse.ArgumentParser()
        parser.add_argument("capture")
        parser.add_argument("--results", type=Path, required=True)
        parser.add_argument("--exit-code", type=int, required=True)
        parser.add_argument("--full-suite", choices=("true", "false"), required=True)
        args = parser.parse_args()
        capture(args.results, args.exit_code, args.full_suite == "true")
        return
    parser = argparse.ArgumentParser()
    parser.add_argument("--results", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        summary = validate(args.results)
    except (EvidenceError, KeyError, TypeError, ValueError) as failure:
        summary = {"status": "incomplete", "reason": str(failure)}
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.with_suffix(".json").write_text(json.dumps(summary, indent=2) + "\n")
        args.output.write_text(markdown(summary))
        print("evidence validation: " + str(failure), file=sys.stderr)
        raise SystemExit(2)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.with_suffix(".json").write_text(json.dumps(summary, indent=2) + "\n")
    args.output.write_text(markdown(summary))
    print("A1-A8: PASS; safe report: " + str(args.output))


if __name__ == "__main__":
    main()
