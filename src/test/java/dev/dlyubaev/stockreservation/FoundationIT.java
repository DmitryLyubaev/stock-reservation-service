package dev.dlyubaev.stockreservation;

import static org.assertj.core.api.Assertions.*;

import dev.dlyubaev.stockreservation.support.HttpFixture;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FoundationIT extends HttpFixture {
  @Test
  void migrationAndSeedExposeCommittedInventory() throws Exception {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class))
        .isEqualTo(1);
    var response = get("/api/inventory/DEMO-RED");
    assertThat(response.statusCode()).isEqualTo(200);
    var body = json(response);
    assertThat(body.get("on_hand").asInt()).isEqualTo(10);
    assertThat(body.get("held").asInt()).isZero();
    assertThat(body.get("available").asInt()).isEqualTo(10);
    assertThat(body.has("as_of")).isTrue();
    assertThat(jdbc.queryForObject("select count(*) from inventory", Integer.class)).isEqualTo(3);
    assertInvariants();
  }

  @Test
  void unknownProductIsSafe404() throws Exception {
    var response = get("/api/inventory/UNKNOWN");
    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(json(response).get("code").asText()).isEqualTo("PRODUCT_NOT_FOUND");
    assertThat(response.body()).doesNotContain("SQLException", "jdbc:", "password", "stackTrace");
  }

  @Test
  void databaseRejectsInvalidCountersAndQuantities() {
    for (var sql :
        new String[] {
          "update inventory set held=-1 where product_code='DEMO-RED'",
          "update inventory set held=11 where product_code='DEMO-RED'",
          "update inventory set on_hand=11 where product_code='DEMO-RED'",
          "insert into reservations(id,order_reference,product_code,quantity,state,created_at,expires_at) values(gen_random_uuid(),'ORDER-1','DEMO-RED',0,'ACTIVE',clock_timestamp(),clock_timestamp()+interval '5 minutes')"
        })
      assertThatThrownBy(() -> jdbc.update(sql))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertInvariants();
  }

  @Test
  void httpRejectsOversizeAndNonJsonBeforeBusinessHandling() throws Exception {
    assertThat(post("/api/reservations", "K-1", "x".repeat(2049), Map.of()).statusCode())
        .isEqualTo(413);
    var chunked =
        java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl + "/api/reservations"))
            .timeout(java.time.Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(
                java.net.http.HttpRequest.BodyPublishers.ofInputStream(
                    () ->
                        new java.io.ByteArrayInputStream(
                            "x".repeat(2049).getBytes(java.nio.charset.StandardCharsets.UTF_8))))
            .build();
    assertThat(
            client.send(chunked, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(413);
    assertThat(
            post("/api/reservations", "K-1", "{}", Map.of("Content-Type", "text/plain"))
                .statusCode())
        .isEqualTo(415);
    var options =
        request(
            "OPTIONS",
            "/api/reservations",
            "",
            Map.of("Origin", "https://example.invalid", "Access-Control-Request-Method", "POST"));
    assertThat(options.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
  }
}
