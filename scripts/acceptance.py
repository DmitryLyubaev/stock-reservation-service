#!/usr/bin/env python3
"""Actual packaged HTTP/PostgreSQL checks. No model/provider calls or clock injection."""
import argparse
import datetime as dt
import json
import math
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
PROJECT = "p20-stock-reservation"


def command(*args, stdin=None):
    timeout = float(os.environ.get("P20_COMMAND_TIMEOUT", "30"))
    if not math.isfinite(timeout) or not 0 < timeout <= 30:
        raise ValueError("P20_COMMAND_TIMEOUT must be greater than zero and at most 30 seconds")
    return subprocess.run(args, input=stdin, text=True, capture_output=True, check=True, timeout=timeout).stdout.strip()


def compose(*args, stdin=None):
    return command("docker", "compose", "--project-name", PROJECT, "--file", str(ROOT / "compose.yaml"),
                   "--env-file", str(ROOT / ".local/demo.env"), *args, stdin=stdin)


def owned(service):
    identity = compose("ps", "--all", "--quiet", service)
    assert identity and "\n" not in identity, f"One owned {service} container is required"
    inspected = json.loads(command("docker", "inspect", identity))[0]
    labels = inspected["Config"]["Labels"]
    assert labels.get("com.docker.compose.project") == PROJECT and labels.get("io.project20.demo") == "true"
    return identity, inspected


def sql(query):
    owned("db")
    return compose("exec", "-T", "db", "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "p20_demo", "-d", "project20", "-Atc", query)


def reset_fixture():
    sql("TRUNCATE request_results,reservations,inventory;")
    compose("exec", "-T", "db", "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "p20_demo", "-d", "project20", stdin=(ROOT / "scripts/seed.sql").read_text())


def http(base, method, path, body=None, key=None):
    headers = {"Content-Type": "application/json"}
    if key is not None:
        headers["Idempotency-Key"] = key
    data = None if body is None else json.dumps(body, separators=(",", ":")).encode()
    request = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as response:
        return response.code, response.read().decode()


def ready(base):
    deadline = time.monotonic() + 75
    while time.monotonic() < deadline:
        try:
            status, body = http(base, "GET", "/api/inventory/DEMO-RED")
            parsed = json.loads(body)
            if (status == 404 and parsed.get("code") == "PRODUCT_NOT_FOUND") or (status == 200 and parsed.get("product_code") == "DEMO-RED"):
                return
        except (OSError, ValueError):
            pass
        time.sleep(1)
    raise AssertionError("Packaged API did not become ready")


def invariants():
    query = """select coalesce(json_agg(t order by product_code),'[]'::json) from (
      select i.*,coalesce((select sum(quantity) from reservations r where r.product_code=i.product_code and r.state='ACTIVE'),0) active_sum,
      coalesce((select sum(quantity) from reservations r where r.product_code=i.product_code and r.state='CONFIRMED'),0) confirmed_sum
      from inventory i) t"""
    rows = json.loads(sql(query))
    assert len(rows) == 3
    for row in rows:
        assert 0 <= row["held"] <= row["on_hand"] <= row["initial_quantity"]
        assert row["held"] == row["active_sum"]
        assert row["on_hand"] == row["initial_quantity"] - row["confirmed_sum"]
    return {"query": query, "rows": rows}


def instant(value):
    return dt.datetime.fromisoformat(value.replace("Z", "+00:00"))


def database_now():
    return instant(sql("select clock_timestamp();"))


def artifact(base):
    api_id, api = owned("api")
    db_id, db = owned("db")
    assert api["Config"]["User"] == "10001:10001"
    binding = api["NetworkSettings"]["Ports"]["8220/tcp"]
    assert binding == [{"HostIp": "127.0.0.1", "HostPort": "8220"}]
    assert not db["HostConfig"].get("PortBindings")
    labels = api["Config"]["Labels"]
    assert labels["org.opencontainers.image.revision"] == os.environ["P20_SOURCE_SHA"]
    assert labels["io.project20.source.tree"] == os.environ["P20_SOURCE_TREE"]
    assert labels["io.project20.source.dirty"] == os.environ["P20_SOURCE_DIRTY"]
    for path in ("/api/test/clock", "/api/test/fail"):
        assert http(base, "POST", path, {}, "BOUNDARY-" + uuid.uuid4().hex)[0] == 404
    return {"api_container": api_id, "database_container": db_id, "image_id": api["Image"],
            "source_sha": labels["org.opencontainers.image.revision"], "source_tree": labels["io.project20.source.tree"],
            "source_dirty": labels["io.project20.source.dirty"] == "true", "runtime_user": api["Config"]["User"],
            "api_binding": binding, "database_host_bindings": db["HostConfig"].get("PortBindings", {})}


def restart_case(base):
    reset_fixture()
    request = {"order_reference": "RESTART-ORDER", "product_code": "DEMO-RED", "quantity": 2}
    success = http(base, "POST", "/api/reservations", request, "RESTART-SUCCESS")
    assert success[0] == 201
    rejected_body = {**request, "quantity": 11}
    rejected = http(base, "POST", "/api/reservations", rejected_body, "RESTART-REJECTED")
    assert rejected[0] == 409 and json.loads(rejected[1])["code"] == "INSUFFICIENT_STOCK"
    before = artifact(base)
    killed_at = database_now().isoformat()
    compose("kill", "--signal", "SIGKILL", "api")
    _, stopped = owned("api")
    assert not stopped["State"]["Running"] and stopped["State"]["ExitCode"] == 137
    compose("up", "--detach", "--no-deps", "api")
    ready(base)
    after = artifact(base)
    assert before["image_id"] == after["image_id"]
    assert before["database_container"] == after["database_container"]
    assert http(base, "POST", "/api/reservations", request, "RESTART-SUCCESS") == success
    assert http(base, "POST", "/api/reservations", rejected_body, "RESTART-REJECTED") == rejected
    assert int(sql("select count(*) from reservations;")) == 1
    assert int(sql("select count(*) from request_results;")) == 2
    rows = invariants()
    assert next(row for row in rows["rows"] if row["product_code"] == "DEMO-RED")["held"] == 2
    return {"case": "A7", "status": "pass", "clock_source": "database", "killed_at": killed_at,
            "replayed_at": database_now().isoformat(), "exit_code": 137, "success_status": success[0],
            "rejection_status": rejected[0], "exact_status_body_replay": True, "reservations": 1,
            "completed_keys": 2, "before": before, "after": after, "persisted_invariants": rows}


def real_clock_case(base, results):
    reset_fixture()
    created = http(base, "POST", "/api/reservations", {"order_reference": "CLOCK-ORDER", "product_code": "DEMO-RED", "quantity": 1}, "CLOCK-CREATE")
    assert created[0] == 201
    snapshot = json.loads(created[1])
    identity = str(uuid.UUID(snapshot["id"]))
    expires = instant(snapshot["expires_at"])
    assert expires - instant(snapshot["created_at"]) == dt.timedelta(seconds=300)
    path = "/api/reservations/" + identity + "/expire"
    early_at = database_now()
    early = http(base, "POST", path, {}, "CLOCK-EARLY")
    assert early[0] == 409 and json.loads(early[1])["code"] == "NOT_DUE" and early_at < expires
    deadline = time.monotonic() + 420
    observations = [early_at.isoformat()]
    while True:
        observed = database_now()
        assert time.monotonic() < deadline, "Real-clock deadline exceeded"
        observations.append(observed.isoformat())
        progress = {"case": "A8", "status": "running", "clock_source": "database", "created_at": snapshot["created_at"],
                    "expires_at": snapshot["expires_at"], "database_now": observed.isoformat()}
        (results / "real-clock-progress.json").write_text(json.dumps(progress, indent=2) + "\n")
        if observed >= expires:
            break
        assert time.monotonic() < deadline, "Real-clock deadline exceeded"
        time.sleep(min(5, max(0.01, (expires - observed).total_seconds())))
    assert http(base, "POST", path, {}, "CLOCK-EARLY") == early
    due = http(base, "POST", path, {}, "CLOCK-DUE")
    assert due[0] == 200
    terminal = json.loads(due[1])
    assert terminal["state"] == "EXPIRED" and instant(terminal["decided_at"]) >= expires
    rows = invariants()
    assert next(row for row in rows["rows"] if row["product_code"] == "DEMO-RED")["held"] == 0
    return {"case": "A8", "status": "pass", "clock_source": "database", "created_at": snapshot["created_at"],
            "expires_at": snapshot["expires_at"], "early_at": early_at.isoformat(), "early_status": 409,
            "decided_at": terminal["decided_at"], "due_status": 200, "early_replay_unchanged": True,
            "elapsed_database_seconds": (instant(terminal["decided_at"]) - instant(snapshot["created_at"])).total_seconds(),
            "observations": observations, "persisted_invariants": rows, "artifact": artifact(base)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--results", type=Path, required=True)
    parser.add_argument("--mode", choices=("restart", "real-clock", "all"), required=True)
    args = parser.parse_args()
    assert urllib.parse.urlparse(args.base_url).hostname == "127.0.0.1"
    args.results.mkdir(parents=True, exist_ok=True)
    ready(args.base_url)
    metadata = artifact(args.base_url)
    (args.results / "artifact.json").write_text(json.dumps(metadata, indent=2) + "\n")
    for name, run in (("restart", lambda: restart_case(args.base_url)), ("real-clock", lambda: real_clock_case(args.base_url, args.results))):
        if args.mode not in (name, "all"):
            continue
        try:
            result = run()
        except Exception as failure:
            (args.results / (name + ".json")).write_text(json.dumps({"case": name, "status": "fail", "error": str(failure), "artifact": metadata}, indent=2) + "\n")
            raise
        (args.results / (name + ".json")).write_text(json.dumps(result, indent=2) + "\n")
        print(name + ": PASS", flush=True)


if __name__ == "__main__":
    main()
