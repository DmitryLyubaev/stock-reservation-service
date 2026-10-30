package dev.dlyubaev.stockreservation;

import static org.assertj.core.api.Assertions.*;

import dev.dlyubaev.stockreservation.support.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TransactionRollbackIT extends HttpFixture {
  @Test
  void exceptionAfterRealFlushRollsBackInventoryReservationAndKey() throws Exception {
    TransactionProbe.failNextFlush();
    var failed = post("/api/reservations", "ROLLBACK", body("ORDER-1", "DEMO-RED", 2), Map.of());
    assertThat(failed.statusCode()).isEqualTo(500);
    assertThat(TransactionProbe.wasInjected()).isTrue();
    assertThat(jdbc.queryForObject("select count(*) from reservations", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("select count(*) from request_results", Integer.class)).isZero();
    assertThat(
            jdbc.queryForObject(
                "select held from inventory where product_code='DEMO-RED'", Integer.class))
        .isZero();
    assertInvariants();
    var retry = post("/api/reservations", "ROLLBACK", body("ORDER-1", "DEMO-RED", 2), Map.of());
    assertThat(retry.statusCode()).isEqualTo(201);
    assertThat(jdbc.queryForObject("select count(*) from reservations", Integer.class))
        .isEqualTo(1);
    assertInvariants();
    TransactionProbe.evidence(
        "A5-rollback",
        Map.of(
            "injected_after_real_flush",
            true,
            "failed_status",
            500,
            "post_failure_reservations",
            0,
            "post_failure_keys",
            0,
            "retry_status",
            201,
            "clock_source",
            "database"));
  }
}
