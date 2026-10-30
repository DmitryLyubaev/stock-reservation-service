package dev.dlyubaev.stockreservation;

import static org.assertj.core.api.Assertions.*;

import dev.dlyubaev.stockreservation.support.HttpFixture;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReservationCreationIT extends HttpFixture {
  @Test
  void reserveHoldsStockAndPersistsFiveMinuteSnapshot() throws Exception {
    var response = post("/api/reservations", "CREATE-1", body("ORDER-1", "DEMO-RED", 2), Map.of());
    assertThat(response.statusCode()).isEqualTo(201);
    var created = json(response);
    assertThat(created.get("state").asText()).isEqualTo("ACTIVE");
    assertThat(created.get("order_reference").asText()).isEqualTo("ORDER-1");
    assertThat(
            Duration.between(
                Instant.parse(created.get("created_at").asText()),
                Instant.parse(created.get("expires_at").asText())))
        .isEqualTo(Duration.ofMinutes(5));
    var current = get("/api/reservations/" + created.get("id").asText());
    assertThat(current.statusCode()).isEqualTo(200);
    assertThat(json(current).get("quantity").asInt()).isEqualTo(2);
    assertThat(json(current).get("overdue").asBoolean()).isFalse();
    var inventory = json(get("/api/inventory/DEMO-RED"));
    assertThat(inventory.get("on_hand").asInt()).isEqualTo(10);
    assertThat(inventory.get("held").asInt()).isEqualTo(2);
    assertThat(inventory.get("available").asInt()).isEqualTo(8);
    assertInvariants();
  }

  @Test
  void businessRejectionsAreStoredWithoutChangingStock() throws Exception {
    var unknown = post("/api/reservations", "UNKNOWN-1", body("ORDER-1", "UNKNOWN", 1), Map.of());
    assertThat(unknown.statusCode()).isEqualTo(404);
    assertThat(json(unknown).get("code").asText()).isEqualTo("PRODUCT_NOT_FOUND");
    assertThat(
            post("/api/reservations", "UNKNOWN-1", body("ORDER-1", "UNKNOWN", 1), Map.of()).body())
        .isEqualTo(unknown.body());
    var insufficient =
        post("/api/reservations", "SCARCE-1", body("ORDER-1", "DEMO-RED", 11), Map.of());
    assertThat(insufficient.statusCode()).isEqualTo(409);
    assertThat(json(insufficient).get("code").asText()).isEqualTo("INSUFFICIENT_STOCK");
    var repeat = post("/api/reservations", "SCARCE-1", body("ORDER-1", "DEMO-RED", 11), Map.of());
    assertThat(repeat.statusCode()).isEqualTo(409);
    assertThat(repeat.body()).isEqualTo(insufficient.body());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from request_results where response_status is not null",
                Integer.class))
        .isEqualTo(2);
    assertThat(jdbc.queryForObject("select count(*) from reservations", Integer.class)).isZero();
    assertInvariants();
  }

  @Test
  void orderReferenceIsDescriptiveRatherThanADuplicateKey() throws Exception {
    var one = post("/api/reservations", "ONE", body("SAME-ORDER", "DEMO-RED", 2), Map.of());
    var two = post("/api/reservations", "TWO", body("SAME-ORDER", "DEMO-RED", 2), Map.of());
    assertThat(one.statusCode()).isEqualTo(201);
    assertThat(two.statusCode()).isEqualTo(201);
    assertThat(json(one).get("id")).isNotEqualTo(json(two).get("id"));
    assertThat(
            jdbc.queryForObject(
                "select held from inventory where product_code='DEMO-RED'", Integer.class))
        .isEqualTo(4);
    assertInvariants();
  }
}
