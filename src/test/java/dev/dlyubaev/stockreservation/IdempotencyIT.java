package dev.dlyubaev.stockreservation;

import static org.assertj.core.api.Assertions.*;

import dev.dlyubaev.stockreservation.support.HttpFixture;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class IdempotencyIT extends HttpFixture {
  @Test
  void reorderedJsonReplaysExactResultButChangedDetailsConflict() throws Exception {
    var original = post("/api/reservations", "SAME", body("ORDER-1", "DEMO-RED", 2), Map.of());
    assertThat(original.statusCode()).isEqualTo(201);
    var repeated =
        post(
            "/api/reservations",
            "SAME",
            "{\"quantity\":2,\"product_code\":\"DEMO-RED\",\"order_reference\":\"ORDER-1\"}",
            Map.of());
    assertThat(repeated.statusCode()).isEqualTo(201);
    assertThat(repeated.body()).isEqualTo(original.body());
    assertThat(repeated.headers().firstValue("Idempotency-Replayed")).contains("true");
    for (var changed :
        List.of(
            body("ORDER-1", "DEMO-RED", 3),
            body("ORDER-2", "DEMO-RED", 2),
            body("ORDER-1", "DEMO-BLUE", 2))) {
      var response = post("/api/reservations", "SAME", changed, Map.of());
      assertThat(response.statusCode()).isEqualTo(409);
      assertThat(json(response).get("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
    }
    assertThat(jdbc.queryForObject("select count(*) from reservations", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select response_body from request_results where request_key='SAME'", String.class))
        .isEqualTo(original.body());
    assertInvariants();
  }

  @Test
  void twentyConcurrentCopiesHaveOneCommittedEffect() throws Exception {
    var barrier = new CyclicBarrier(20);
    var futures = new ArrayList<Future<HttpResponse<String>>>();
    try (var executor = Executors.newFixedThreadPool(20)) {
      for (int i = 0; i < 20; i++)
        futures.add(
            executor.submit(
                () -> {
                  barrier.await(10, TimeUnit.SECONDS);
                  return post(
                      "/api/reservations", "PARALLEL", body("ORDER-1", "DEMO-RED", 1), Map.of());
                }));
      var responses = new ArrayList<HttpResponse<String>>();
      for (var future : futures) responses.add(future.get(25, TimeUnit.SECONDS));
      assertThat(responses)
          .allSatisfy(
              r -> {
                assertThat(r.statusCode()).isEqualTo(201);
                assertThat(r.body()).isEqualTo(responses.getFirst().body());
              });
    }
    assertThat(jdbc.queryForObject("select count(*) from request_results", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from reservations", Integer.class))
        .isEqualTo(1);
    assertInvariants();
  }

  @Test
  void competingDifferentPayloadsCannotSilentlyShareAKey() throws Exception {
    var barrier = new CyclicBarrier(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a =
          executor.submit(
              () -> {
                barrier.await();
                return post("/api/reservations", "RACE", body("ORDER-1", "DEMO-RED", 2), Map.of());
              });
      var b =
          executor.submit(
              () -> {
                barrier.await();
                return post("/api/reservations", "RACE", body("ORDER-1", "DEMO-RED", 3), Map.of());
              });
      var responses = List.of(a.get(25, TimeUnit.SECONDS), b.get(25, TimeUnit.SECONDS));
      assertThat(responses.stream().map(HttpResponse::statusCode).sorted().toList())
          .containsExactly(201, 409);
      var winner = responses.stream().filter(r -> r.statusCode() == 201).findFirst().orElseThrow();
      assertThat(
              jdbc.queryForObject(
                  "select held from inventory where product_code='DEMO-RED'", Integer.class))
          .isEqualTo(json(winner).get("quantity").asInt());
    }
    assertInvariants();
  }
}
