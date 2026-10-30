package dev.dlyubaev.stockreservation;

import static org.assertj.core.api.Assertions.*;

import dev.dlyubaev.stockreservation.support.*;
import dev.dlyubaev.stockreservation.time.DatabaseDecisionClock;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ReservationRaceIT extends HttpFixture {
  private static final Instant START = Instant.parse("2026-10-30T00:00:00Z");

  @Test
  void requestWaitingAcrossDeadlineCannotConfirm() throws Exception {
    clock.set(START);
    String id =
        json(post("/api/reservations", "CREATE", body("ORDER-1", "DEMO-RED", 2), Map.of()))
            .get("id")
            .asText();
    int waiters;
    try (var hold = TransactionProbe.holdInventory(jdbc.getDataSource());
        var executor = Executors.newSingleThreadExecutor()) {
      var future =
          executor.submit(
              () -> post("/api/reservations/" + id + "/confirm", "CONFIRM", "{}", Map.of()));
      waiters = TransactionProbe.awaitBlocked(jdbc, 1);
      clock.set(START.plusSeconds(300));
      hold.close();
      var response = future.get(20, TimeUnit.SECONDS);
      assertThat(response.statusCode()).isEqualTo(409);
      assertThat(json(response).get("code").asText()).isEqualTo("RESERVATION_EXPIRED");
    }
    assertThat(json(get("/api/reservations/" + id)).get("state").asText()).isEqualTo("EXPIRED");
    assertInvariants();
    TransactionProbe.evidence(
        "A6-lock-wait-deadline",
        Map.of(
            "observed_waiters",
            waiters,
            "clock_source",
            "injected",
            "decision_time",
            START.plusSeconds(300).toString(),
            "state",
            "EXPIRED",
            "status",
            409));
  }

  @Test
  void confirmationCancellationAndExpiryRacesHaveOneEffect() throws Exception {
    var observations = new ArrayList<Map<String, Object>>();
    for (String opponent : List.of("cancel", "expire")) {
      clock.set(START);
      String id =
          json(post(
                  "/api/reservations",
                  "CREATE-" + opponent,
                  body("ORDER-1", "DEMO-RED", 2),
                  Map.of()))
              .get("id")
              .asText();
      if (opponent.equals("expire")) clock.set(START.plusSeconds(300));
      try (var hold = TransactionProbe.holdInventory(jdbc.getDataSource());
          var executor = Executors.newFixedThreadPool(2)) {
        var confirm =
            executor.submit(
                () ->
                    post(
                        "/api/reservations/" + id + "/confirm",
                        "CONFIRM-" + opponent,
                        "{}",
                        Map.of()));
        var other =
            executor.submit(
                () ->
                    post(
                        "/api/reservations/" + id + "/" + opponent,
                        "OTHER-" + opponent,
                        "{}",
                        Map.of()));
        int blocked = TransactionProbe.awaitBlocked(jdbc, 2);
        hold.close();
        var a = confirm.get(20, TimeUnit.SECONDS);
        var b = other.get(20, TimeUnit.SECONDS);
        assertThat(List.of(a.statusCode(), b.statusCode()).stream().sorted().toList())
            .containsExactly(200, 409);
        String state = json(get("/api/reservations/" + id)).get("state").asText();
        if (opponent.equals("expire")) assertThat(state).isEqualTo("EXPIRED");
        else assertThat(state).isIn("CONFIRMED", "CANCELLED");
        observations.add(
            Map.of(
                "opponent",
                opponent,
                "confirm_status",
                a.statusCode(),
                "other_status",
                b.statusCode(),
                "state",
                state,
                "observed_waiters",
                blocked));
      }
      assertInvariants();
    }
    TransactionProbe.evidence(
        "A6-terminal-races", Map.of("clock_source", "injected", "observations", observations));
  }

  @Test
  void productionDatabaseClockAdvancesWithinAnAlreadyOpenTransaction() throws Exception {
    var observed = new HashMap<String, Object>();
    new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
        .executeWithoutResult(
            status -> {
              jdbc.execute(
                  "select product_code from inventory where product_code='DEMO-RED' for update");
              var started =
                  jdbc.queryForObject("select transaction_timestamp()", OffsetDateTime.class)
                      .toInstant();
              jdbc.execute("select pg_sleep(0.05)");
              var before =
                  jdbc.queryForObject("select clock_timestamp()", OffsetDateTime.class).toInstant();
              var actual = context.getBean(DatabaseDecisionClock.class).now();
              var after =
                  jdbc.queryForObject("select clock_timestamp()", OffsetDateTime.class).toInstant();
              assertThat(actual).isBetween(before, after).isAfter(started.plusMillis(30));
              observed.put("transaction_start", started.toString());
              observed.put("before", before.toString());
              observed.put("observed", actual.toString());
              observed.put("after", after.toString());
            });
    observed.put("clock_source", "database");
    TransactionProbe.evidence("A6-real-clock-adapter", observed);
  }
}
