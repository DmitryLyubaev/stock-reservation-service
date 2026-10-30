package dev.dlyubaev.stockreservation.support;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

public final class DatabaseAssertions {
  private static final String QUERY =
      "select i.*,coalesce((select sum(quantity) from reservations r where r.product_code=i.product_code and r.state='ACTIVE'),0) active_sum,coalesce((select sum(quantity) from reservations r where r.product_code=i.product_code and r.state='CONFIRMED'),0) confirmed_sum from inventory i order by product_code";

  private DatabaseAssertions() {}

  public static void assertInvariants(JdbcTemplate jdbc) {
    var rows = jdbc.queryForList(QUERY);
    for (var row : rows) {
      int initial = ((Number) row.get("initial_quantity")).intValue(),
          onHand = ((Number) row.get("on_hand")).intValue(),
          held = ((Number) row.get("held")).intValue();
      assertThat(held).isBetween(0, onHand);
      assertThat(onHand).isLessThanOrEqualTo(initial);
      assertThat(held).isEqualTo(((Number) row.get("active_sum")).intValue());
      assertThat(initial - onHand).isEqualTo(((Number) row.get("confirmed_sum")).intValue());
    }
    var directory = System.getenv("P20_RESULTS_DIR");
    if (directory != null) {
      try {
        Files.createDirectories(Path.of(directory));
        Files.writeString(
            Path.of(directory, "invariants-" + UUID.randomUUID() + ".json"),
            new JsonMapper()
                .writeValueAsString(
                    Map.of(
                        "query",
                        QUERY,
                        "rows",
                        rows,
                        "observed_at",
                        java.time.Instant.now().toString(),
                        "source_sha",
                        System.getenv().getOrDefault("P20_SOURCE_SHA", "unknown"))));
      } catch (Exception failure) {
        throw new AssertionError("Cannot preserve invariant evidence", failure);
      }
    }
  }
}
