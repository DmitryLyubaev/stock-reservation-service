package dev.dlyubaev.stockreservation;

import static org.assertj.core.api.Assertions.*;

import dev.dlyubaev.stockreservation.support.*;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class StockContentionIT extends HttpFixture {
  record Sample(int index, long startNanos, long endNanos, String startedAt, int status) {}

  @Test
  void twentyActualContendersCannotReserveMoreThanTenUnits() throws Exception {
    assertThat(
            post("/api/reservations", "WARM", body("ORDER-WARM", "DEMO-RED", 0), Map.of())
                .statusCode())
        .isEqualTo(400);
    var barrier = new CyclicBarrier(21);
    var samples = new ArrayList<Sample>();
    var responses = new ArrayList<HttpResponse<String>>();
    var futures = new ArrayList<Future<Map.Entry<Sample, HttpResponse<String>>>>();
    int waiters;
    try (var hold = TransactionProbe.holdInventory(jdbc.getDataSource());
        var executor = Executors.newFixedThreadPool(20)) {
      for (int i = 0; i < 20; i++) {
        final int n = i;
        futures.add(
            executor.submit(
                () -> {
                  barrier.await(10, TimeUnit.SECONDS);
                  long start = System.nanoTime();
                  String wall = Instant.now().toString();
                  var response =
                      post(
                          "/api/reservations",
                          "SCARCE-" + n,
                          body("ORDER-" + n, "DEMO-RED", 1),
                          Map.of());
                  return Map.entry(
                      new Sample(n, start, System.nanoTime(), wall, response.statusCode()),
                      response);
                }));
      }
      barrier.await(10, TimeUnit.SECONDS);
      waiters = TransactionProbe.awaitBlocked(jdbc, 2);
      hold.close();
      for (var future : futures) {
        var result = future.get(25, TimeUnit.SECONDS);
        samples.add(result.getKey());
        responses.add(result.getValue());
      }
    }
    assertThat(responses.stream().filter(r -> r.statusCode() == 201).count()).isEqualTo(10);
    assertThat(
            responses.stream()
                .filter(
                    r ->
                        r.statusCode() == 409
                            && json(r).get("code").asText().equals("INSUFFICIENT_STOCK"))
                .count())
        .isEqualTo(10);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from reservations where state='ACTIVE'", Integer.class))
        .isEqualTo(10);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from request_results where response_status is not null",
                Integer.class))
        .isEqualTo(20);
    assertThat(
            jdbc.queryForObject(
                "select held from inventory where product_code='DEMO-RED'", Integer.class))
        .isEqualTo(10);
    assertThat(
            jdbc.queryForObject(
                "select on_hand from inventory where product_code='DEMO-RED'", Integer.class))
        .isEqualTo(10);
    var events = new ArrayList<long[]>();
    for (var sample : samples) {
      events.add(new long[] {sample.startNanos(), 1});
      events.add(new long[] {sample.endNanos(), -1});
    }
    events.sort(Comparator.<long[]>comparingLong(x -> x[0]).thenComparingLong(x -> x[1]));
    int active = 0, peak = 0;
    for (var event : events) {
      active += event[1];
      peak = Math.max(peak, active);
    }
    assertThat(peak).isGreaterThanOrEqualTo(2);
    assertInvariants();
    TransactionProbe.evidence(
        "A4-scarce-stock",
        Map.of(
            "submissions",
            20,
            "created",
            10,
            "insufficient_stock",
            10,
            "observed_lock_waiters",
            waiters,
            "peak_client_overlap",
            peak,
            "clock_source",
            "database",
            "samples",
            samples));
  }
}
