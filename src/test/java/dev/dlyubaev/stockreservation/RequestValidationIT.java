package dev.dlyubaev.stockreservation;

import static org.assertj.core.api.Assertions.*;

import dev.dlyubaev.stockreservation.support.HttpFixture;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RequestValidationIT extends HttpFixture {
  @Test
  void unsupportedMethodsReturn405AndAllowWithoutDatabaseWrites() throws Exception {
    for (var example :
        Map.of("/api/reservations", "POST", "/api/inventory/DEMO-RED", "GET").entrySet()) {
      var response =
          request(
              "PUT",
              example.getKey(),
              body("ORDER-1", "DEMO-RED", 1),
              Map.of("Content-Type", "application/json", "Idempotency-Key", "UNSUPPORTED-METHOD"));
      assertThat(response.statusCode()).as(example.getKey()).isEqualTo(405);
      assertThat(
              Arrays.stream(response.headers().firstValue("Allow").orElse("").split(","))
                  .map(String::trim)
                  .toList())
          .contains(example.getValue())
          .doesNotContain("PUT");
      assertThat(response.headers().firstValue("Content-Type").orElse(""))
          .startsWith("application/problem+json");
      assertThat(json(response).get("status").asInt()).isEqualTo(405);
      assertThat(json(response).get("code").asText()).isEqualTo("METHOD_NOT_ALLOWED");
      assertThat(json(response).get("instance").asText()).isEqualTo(example.getKey());
      assertThat(response.body()).doesNotContain("Exception", "stackTrace", "jdbc:");
    }
    assertThat(jdbc.queryForObject("select count(*) from request_results", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("select count(*) from reservations", Integer.class)).isZero();
    assertInvariants();
  }

  @Test
  void encodedApiPathCannotBypassMutationBoundary() throws Exception {
    assertMutationBoundary("/%61pi/reservations");
  }

  @Test
  void pathParametersCannotBypassMutationBoundary() throws Exception {
    assertMutationBoundary("/api;ignored/reservations");
  }

  private void assertMutationBoundary(String path) throws Exception {
    String valid = body("ORDER-1", "DEMO-RED", 1);
    var duplicate =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(java.time.Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", "FIRST")
            .header("Idempotency-Key", "SECOND")
            .POST(HttpRequest.BodyPublishers.ofString(valid))
            .build();
    org.junit.jupiter.api.Assertions.assertAll(
        () ->
            assertThat(post(path, "OVERSIZE", " ".repeat(2049) + valid, Map.of()).statusCode())
                .as("body limit on %s", path)
                .isEqualTo(413),
        () ->
            assertThat(post(path, "BAD KEY", valid, Map.of()).statusCode())
                .as("key grammar on %s", path)
                .isEqualTo(400),
        () ->
            assertThat(client.send(duplicate, HttpResponse.BodyHandlers.ofString()).statusCode())
                .as("physical duplicate headers on %s", path)
                .isEqualTo(400),
        () ->
            assertThat(jdbc.queryForObject("select count(*) from request_results", Integer.class))
                .isZero(),
        () ->
            assertThat(jdbc.queryForObject("select count(*) from reservations", Integer.class))
                .isZero());
    assertInvariants();
  }

  @Test
  void invalidQuantitiesAndUnknownMembersNeverClaimAKey() throws Exception {
    for (String quantity :
        List.of(
            "0", "-1", "1000001", "1.5", "1.0", "\"1\"", "true", "null", "999999999999999999999")) {
      var response =
          post(
              "/api/reservations",
              "INVALID",
              "{\"order_reference\":\"ORDER-1\",\"product_code\":\"DEMO-RED\",\"quantity\":"
                  + quantity
                  + "}",
              Map.of());
      assertThat(response.statusCode()).as(quantity).isEqualTo(400);
    }
    for (String invalid :
        List.of(
            "{}",
            "{",
            body(" ORDER-1", "DEMO-RED", 1),
            body("ORDER-1", " DEMO-RED", 1),
            "{\"order_reference\":\"ORDER-1\",\"product_code\":\"DEMO-RED\",\"quantity\":1,\"extra\":true}"))
      assertThat(post("/api/reservations", "INVALID", invalid, Map.of()).statusCode())
          .as(invalid)
          .isEqualTo(400);
    assertThat(jdbc.queryForObject("select count(*) from request_results", Integer.class)).isZero();
    assertInvariants();
  }

  @Test
  void missingMalformedAndDuplicateKeysAreRejectedBeforeClaim() throws Exception {
    for (String key : Arrays.asList(null, "BAD KEY", "x".repeat(129), "BAD,KEY"))
      assertThat(
              post("/api/reservations", key, body("ORDER-1", "DEMO-RED", 1), Map.of()).statusCode())
          .isEqualTo(400);
    var duplicate =
        HttpRequest.newBuilder(URI.create(baseUrl + "/api/reservations"))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", "FIRST")
            .header("Idempotency-Key", "SECOND")
            .POST(HttpRequest.BodyPublishers.ofString(body("ORDER-1", "DEMO-RED", 1)))
            .build();
    assertThat(client.send(duplicate, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(400);
    assertThat(get("/api/reservations/not-a-uuid").statusCode()).isEqualTo(400);
    assertThat(get("/api/reservations/00000000-0000-0000-0000-000000000001").statusCode())
        .isEqualTo(404);
    assertThat(jdbc.queryForObject("select count(*) from request_results", Integer.class)).isZero();
  }
}
