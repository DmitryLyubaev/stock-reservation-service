package dev.dlyubaev.stockreservation;

import static org.assertj.core.api.Assertions.*;

import dev.dlyubaev.stockreservation.support.HttpFixture;
import java.util.*;
import org.junit.jupiter.api.Test;

class ReservationLifecycleIT extends HttpFixture {
  @Test
  void confirmationConsumesExactlyOnceAndCreationReplayStaysHistorical() throws Exception {
    var created = post("/api/reservations", "CREATE", body("ORDER-1", "DEMO-RED", 2), Map.of());
    assertThat(created.statusCode()).isEqualTo(201);
    String id = json(created).get("id").asText();
    var confirmed = post("/api/reservations/" + id + "/confirm", "CONFIRM", "{}", Map.of());
    assertThat(confirmed.statusCode()).isEqualTo(200);
    assertThat(json(confirmed).get("state").asText()).isEqualTo("CONFIRMED");
    assertThat(post("/api/reservations/" + id + "/confirm", "CONFIRM-AGAIN", "{}", Map.of()).body())
        .isEqualTo(confirmed.body());
    var conflict = post("/api/reservations/" + id + "/cancel", "CANCEL", "{}", Map.of());
    assertThat(conflict.statusCode()).isEqualTo(409);
    assertThat(json(conflict).get("code").asText()).isEqualTo("INVALID_STATE");
    var replay = post("/api/reservations", "CREATE", body("ORDER-1", "DEMO-RED", 2), Map.of());
    assertThat(replay.statusCode()).isEqualTo(201);
    assertThat(replay.body()).isEqualTo(created.body());
    assertThat(json(get("/api/reservations/" + id)).get("state").asText()).isEqualTo("CONFIRMED");
    assertThat(
            jdbc.queryForObject(
                "select on_hand from inventory where product_code='DEMO-RED'", Integer.class))
        .isEqualTo(8);
    assertThat(
            jdbc.queryForObject(
                "select held from inventory where product_code='DEMO-RED'", Integer.class))
        .isZero();
    assertInvariants();
  }

  @Test
  void cancelledHoldReleasesOnceButPriorRejectionRemainsStable() throws Exception {
    var all = post("/api/reservations", "ALL", body("ORDER-1", "DEMO-RED", 10), Map.of());
    String id = json(all).get("id").asText();
    var rejected = post("/api/reservations", "NO", body("ORDER-2", "DEMO-RED", 1), Map.of());
    assertThat(rejected.statusCode()).isEqualTo(409);
    var cancelled = post("/api/reservations/" + id + "/cancel", "CANCEL", "{}", Map.of());
    assertThat(cancelled.statusCode()).isEqualTo(200);
    assertThat(json(cancelled).get("state").asText()).isEqualTo("CANCELLED");
    assertThat(post("/api/reservations/" + id + "/cancel", "CANCEL2", "{}", Map.of()).statusCode())
        .isEqualTo(200);
    assertThat(post("/api/reservations", "NO", body("ORDER-2", "DEMO-RED", 1), Map.of()).body())
        .isEqualTo(rejected.body());
    assertThat(
            post("/api/reservations", "YES", body("ORDER-2", "DEMO-RED", 1), Map.of()).statusCode())
        .isEqualTo(201);
    assertThat(
            jdbc.queryForObject(
                "select on_hand from inventory where product_code='DEMO-RED'", Integer.class))
        .isEqualTo(10);
    assertInvariants();
  }

  @Test
  void keyCannotBeReusedAcrossActionsOrTargets() throws Exception {
    String one =
        json(post("/api/reservations", "CREATE1", body("ORDER-1", "DEMO-RED", 1), Map.of()))
            .get("id")
            .asText();
    String two =
        json(post("/api/reservations", "CREATE2", body("ORDER-2", "DEMO-RED", 1), Map.of()))
            .get("id")
            .asText();
    var conflict = post("/api/reservations/" + one + "/confirm", "CREATE1", "{}", Map.of());
    assertThat(conflict.statusCode()).isEqualTo(409);
    assertThat(json(conflict).get("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
    assertThat(post("/api/reservations/" + one + "/confirm", "ACTION", "{}", Map.of()).statusCode())
        .isEqualTo(200);
    assertThat(post("/api/reservations/" + two + "/confirm", "ACTION", "{}", Map.of()).statusCode())
        .isEqualTo(409);
    assertThat(json(get("/api/reservations/" + two)).get("state").asText()).isEqualTo("ACTIVE");
    assertInvariants();
  }

  @Test
  void transitionRoutesValidateEmptyBodyAndStoreMissingReservation() throws Exception {
    String unknown = "00000000-0000-0000-0000-000000000001";
    for (String action : List.of("confirm", "cancel", "expire")) {
      String path = "/api/reservations/" + unknown + "/" + action;
      assertThat(post(path, "BAD-" + action, "{\"extra\":1}", Map.of()).statusCode())
          .isEqualTo(400);
      var missing = post(path, "MISSING-" + action, "{}", Map.of());
      assertThat(missing.statusCode()).isEqualTo(404);
      assertThat(json(missing).get("code").asText()).isEqualTo("RESERVATION_NOT_FOUND");
      assertThat(post(path, "MISSING-" + action, "{}", Map.of()).body()).isEqualTo(missing.body());
    }
    assertThat(jdbc.queryForObject("select count(*) from request_results", Integer.class))
        .isEqualTo(3);
  }
}
