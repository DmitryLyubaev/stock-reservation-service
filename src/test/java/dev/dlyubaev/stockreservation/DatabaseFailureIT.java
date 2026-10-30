package dev.dlyubaev.stockreservation;

import static org.assertj.core.api.Assertions.*;

import dev.dlyubaev.stockreservation.support.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DatabaseFailureIT extends HttpFixture {
  @Test
  void lockTimeoutIsRetryableAndDoesNotCompleteAKey() throws Exception {
    try (var hold = TransactionProbe.holdInventory(jdbc.getDataSource())) {
      var response = post("/api/reservations", "BUSY", body("ORDER-1", "DEMO-RED", 1), Map.of());
      assertThat(response.statusCode()).isEqualTo(503);
      assertThat(json(response).get("code").asText()).isEqualTo("BUSY");
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from request_results where request_key='BUSY'", Integer.class))
        .isZero();
    assertThat(jdbc.queryForObject("select count(*) from reservations", Integer.class)).isZero();
    assertInvariants();
    assertThat(
            post("/api/reservations", "BUSY", body("ORDER-1", "DEMO-RED", 1), Map.of())
                .statusCode())
        .isEqualTo(201);
    assertInvariants();
    TransactionProbe.evidence(
        "technical-lock-timeout",
        Map.of("timeout_status", 503, "key_after_failure", 0, "retry_status", 201));
  }

  @Test
  void actualDatabaseStopIsNotAStoredBusinessRejection() throws Exception {
    var before = post("/api/reservations", "BEFORE", body("ORDER-1", "DEMO-RED", 2), Map.of());
    assertThat(before.statusCode()).isEqualTo(201);
    int failedStatus;
    PostgresFixture.stopDatabaseProcess();
    try {
      var response = post("/api/reservations", "OUTAGE", body("ORDER-2", "DEMO-RED", 1), Map.of());
      failedStatus = response.statusCode();
      assertThat(failedStatus).isBetween(500, 599);
      assertThat(response.body()).doesNotContain("password", "jdbc:", "SQLException", "stackTrace");
    } finally {
      PostgresFixture.restartDatabaseProcess();
    }
    long deadline = System.nanoTime() + 15_000_000_000L;
    while (true) {
      try {
        jdbc.queryForObject("select 1", Integer.class);
        break;
      } catch (Exception unavailable) {
        if (System.nanoTime() > deadline) throw unavailable;
        Thread.sleep(100);
      }
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from request_results where request_key='OUTAGE'", Integer.class))
        .isZero();
    assertThat(jdbc.queryForObject("select count(*) from reservations", Integer.class))
        .isEqualTo(1);
    assertThat(
            post("/api/reservations", "OUTAGE", body("ORDER-2", "DEMO-RED", 1), Map.of())
                .statusCode())
        .isEqualTo(201);
    assertInvariants();
    TransactionProbe.evidence(
        "technical-database-stop",
        Map.of(
            "observed_failure_status",
            failedStatus,
            "uncompleted_key",
            true,
            "existing_reservation_preserved",
            true,
            "retry_status",
            201));
  }
}
