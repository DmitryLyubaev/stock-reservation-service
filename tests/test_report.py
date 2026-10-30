"""Synthetic renderer fixtures only. These are never published as application evidence."""
import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SHA = "a" * 40
TREE = "b" * 40
META = {"source_sha": SHA, "source_tree": TREE, "source_dirty": False}
ROWS = [{"product_code": "DEMO-RED", "initial_quantity": 10, "on_hand": 10, "held": 0, "active_sum": 0, "confirmed_sum": 0}]
INVARIANTS = {"query": "select fixture_counter_observations", "rows": ROWS}
ARTIFACT = {**META, "image_id": "sha256:" + "c" * 64, "runtime_user": "10001:10001",
            "database_container": "f" * 64, "api_binding": [{"HostIp": "127.0.0.1", "HostPort": "8220"}], "database_host_bindings": {}}


class ReportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        names = {"FoundationIT": 4, "ReservationCreationIT": 3, "IdempotencyIT": 3, "RequestValidationIT": 2,
                 "ReservationLifecycleIT": 4, "ExpiryBoundaryIT": 3, "StockContentionIT": 1, "TransactionRollbackIT": 1,
                 "ReservationRaceIT": 3, "DatabaseFailureIT": 2, "ArtifactBoundaryIT": 1}
        self.write("canonical/verification.json", {**META, "kind": "canonical", "full_suite": True, "exit_code": 0,
            "java_tests": 27, "java_failures": 0, "java_errors": 0, "java_skipped": 0, "python_tests": 8,
            "testcontainers_remaining": 0,
            "python_exit_code": 0, "python_skipped": 0, "jar_sha256": "d" * 64,
            "environment": {"os": "synthetic renderer fixture", "architecture": "synthetic"},
            "classes": [{"name": "dev.dlyubaev.stockreservation." + name, "tests": count, "failures": 0, "errors": 0, "skipped": 0} for name, count in names.items()]})
        self.write("canonical/A4-scarce-stock.json", {**META, "submissions": 20, "created": 10, "insufficient_stock": 10,
            "peak_client_overlap": 20, "observed_lock_waiters": 3, "clock_source": "database"})
        self.write("canonical/A5-rollback.json", {**META, "injected_after_real_flush": True, "failed_status": 500,
            "post_failure_reservations": 0, "post_failure_keys": 0, "retry_status": 201})
        self.write("canonical/A6-lock-wait-deadline.json", {**META, "observed_waiters": 1, "clock_source": "injected", "state": "EXPIRED", "status": 409})
        self.write("canonical/A6-terminal-races.json", {**META, "clock_source": "injected", "observations": [
            {"opponent": "cancel", "confirm_status": 200, "other_status": 409, "state": "CONFIRMED", "observed_waiters": 2},
            {"opponent": "expire", "confirm_status": 409, "other_status": 200, "state": "EXPIRED", "observed_waiters": 2}]})
        self.write("canonical/A6-real-clock-adapter.json", {**META, "clock_source": "database", "transaction_start": "2026-10-30T00:00:00Z",
            "before": "2026-10-30T00:00:00.1Z", "observed": "2026-10-30T00:00:00.11Z", "after": "2026-10-30T00:00:00.12Z"})
        self.write("canonical/invariants-fixture.json", {**INVARIANTS, "source_sha": SHA})
        self.write("packaged/artifact.json", ARTIFACT)
        self.write("packaged/restart.json", {"case": "A7", "status": "pass", "clock_source": "database", "exit_code": 137,
            "success_status": 201, "rejection_status": 409, "exact_status_body_replay": True,
            "reservations": 1, "completed_keys": 2, "before": ARTIFACT, "after": ARTIFACT, "persisted_invariants": INVARIANTS})
        self.write("packaged/real-clock.json", {"case": "A8", "status": "pass", "clock_source": "database",
            "created_at": "2026-10-30T00:00:00Z", "expires_at": "2026-10-30T00:05:00Z", "early_at": "2026-10-30T00:00:01Z",
            "decided_at": "2026-10-30T00:05:01Z", "early_status": 409, "due_status": 200,
            "early_replay_unchanged": True, "elapsed_database_seconds": 301, "persisted_invariants": INVARIANTS, "artifact": ARTIFACT})
        self.write("packaged/cleanup.json", {"remaining_owned_containers": 0, "retained_volumes": ["p20-stock-reservation_pgdata"]})

    def write(self, relative, value):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value))

    def change(self, relative, change):
        path = self.root / relative
        value = json.loads(path.read_text())
        change(value)
        self.write(relative, value)

    def run_report(self):
        return subprocess.run([sys.executable, str(ROOT / "scripts/report.py"), "--results", str(self.root),
                               "--output", str(self.root / "report.md")], capture_output=True, text=True, timeout=10)

    def reject(self, reason):
        result = self.run_report()
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn(reason, result.stderr)
        self.assertNotEqual(json.loads((self.root / "report.json").read_text())["status"], "pass")

    def test_complete_fixture_generates_all_eight_acceptance_dispositions(self):
        result = self.run_report()
        self.assertEqual(result.returncode, 0, result.stderr)
        summary = json.loads((self.root / "report.json").read_text())
        self.assertEqual(summary["status"], "pass")
        self.assertEqual(summary["acceptance"], {f"A{i}": "pass" for i in range(1, 9)})
        self.assertEqual(summary["java_tests"], 27)
        self.assertEqual(summary["scarce_stock"]["created"], 10)

    def test_missing_source_cannot_qualify(self):
        self.change("canonical/verification.json", lambda value: value.pop("source_sha"))
        self.reject("source")

    def test_missing_real_clock_case_cannot_qualify(self):
        (self.root / "packaged/real-clock.json").unlink()
        self.reject("real-clock.json")

    def test_injected_clock_cannot_be_labelled_real(self):
        self.change("packaged/real-clock.json", lambda value: value.update(clock_source="injected"))
        self.reject("database clock")

    def test_skipped_case_cannot_qualify(self):
        self.change("canonical/verification.json", lambda value: value.update(java_skipped=1))
        self.reject("skipped")

    def test_missing_persisted_queries_cannot_qualify(self):
        (self.root / "canonical/invariants-fixture.json").unlink()
        self.reject("persisted invariants")

    def test_conflicting_packaged_source_cannot_qualify(self):
        self.change("packaged/artifact.json", lambda value: value.update(source_sha="e" * 40))
        self.reject("source")

    def test_invalid_counters_cannot_qualify(self):
        self.change("packaged/restart.json", lambda value: value["persisted_invariants"]["rows"][0].update(held=11))
        self.reject("invariant")

    def test_remaining_owned_testcontainer_cannot_qualify(self):
        self.change("canonical/verification.json", lambda value: value.update(testcontainers_remaining=1))
        self.reject("testcontainer cleanup")


if __name__ == "__main__":
    unittest.main()
