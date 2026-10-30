package dev.dlyubaev.stockreservation;

import static org.assertj.core.api.Assertions.*;

import dev.dlyubaev.stockreservation.support.HttpFixture;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class ExpiryBoundaryIT extends HttpFixture {
  private static final Instant START = Instant.parse("2026-10-30T00:00:00.123456Z");

  @Test
  void earlyExpiryRejectionIsStableAndExactDeadlineExpiresOnce() throws Exception {
    clock.set(START);
    String id =
        json(post("/api/reservations", "CREATE", body("ORDER-1", "DEMO-RED", 2), Map.of()))
            .get("id")
            .asText();
    clock.set(START.plusSeconds(300).minusNanos(1000));
    var early = post("/api/reservations/" + id + "/expire", "EARLY", "{}", Map.of());
    assertThat(early.statusCode()).isEqualTo(409);
    assertThat(json(early).get("code").asText()).isEqualTo("NOT_DUE");
    clock.set(START.plusSeconds(300));
    assertThat(post("/api/reservations/" + id + "/expire", "EARLY", "{}", Map.of()).body())
        .isEqualTo(early.body());
    var due = post("/api/reservations/" + id + "/expire", "DUE", "{}", Map.of());
    assertThat(due.statusCode()).isEqualTo(200);
    assertThat(json(due).get("state").asText()).isEqualTo("EXPIRED");
    assertThat(Instant.parse(json(due).get("decided_at").asText()))
        .isEqualTo(START.plusSeconds(300));
    assertThat(post("/api/reservations/" + id + "/expire", "DUE2", "{}", Map.of()).statusCode())
        .isEqualTo(200);
    assertThat(
            post("/api/reservations/" + id + "/confirm", "TOO-LATE", "{}", Map.of()).statusCode())
        .isEqualTo(409);
    assertInvariants();
  }

  @Test
  void dueConfirmationAndCancellationCommitExpiryDespite409() throws Exception {
    for (String action : List.of("confirm", "cancel")) {
      clock.set(START);
      String id =
          json(post(
                  "/api/reservations",
                  "CREATE-" + action,
                  body("ORDER-1", "DEMO-RED", 2),
                  Map.of()))
              .get("id")
              .asText();
      clock.set(START.plusSeconds(action.equals("confirm") ? 300 : 301));
      var result = post("/api/reservations/" + id + "/" + action, "DUE-" + action, "{}", Map.of());
      assertThat(result.statusCode()).isEqualTo(409);
      assertThat(json(result).get("code").asText()).isEqualTo("RESERVATION_EXPIRED");
      assertThat(json(get("/api/reservations/" + id)).get("state").asText()).isEqualTo("EXPIRED");
      assertThat(
              jdbc.queryForObject(
                  "select held from inventory where product_code='DEMO-RED'", Integer.class))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "select response_status from request_results where request_key=?",
                  Integer.class,
                  "DUE-" + action))
          .isEqualTo(409);
      assertInvariants();
    }
  }

  @Test
  void overdueReadDoesNotReleaseStockAndReserveDoesNotSweepIt() throws Exception {
    clock.set(START);
    String id =
        json(post("/api/reservations", "CREATE", body("ORDER-1", "DEMO-RED", 10), Map.of()))
            .get("id")
            .asText();
    clock.set(START.plusSeconds(301));
    var read = json(get("/api/reservations/" + id));
    assertThat(read.get("state").asText()).isEqualTo("ACTIVE");
    assertThat(read.get("overdue").asBoolean()).isTrue();
    assertThat(json(get("/api/inventory/DEMO-RED")).get("available").asInt()).isZero();
    assertThat(
            post("/api/reservations", "BLOCKED", body("ORDER-2", "DEMO-RED", 1), Map.of())
                .statusCode())
        .isEqualTo(409);
    assertThat(post("/api/reservations/" + id + "/expire", "EXPIRE", "{}", Map.of()).statusCode())
        .isEqualTo(200);
    assertThat(json(get("/api/inventory/DEMO-RED")).get("available").asInt()).isEqualTo(10);
    assertInvariants();
  }
}
