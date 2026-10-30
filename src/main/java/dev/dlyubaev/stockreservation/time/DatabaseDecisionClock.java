package dev.dlyubaev.stockreservation.time;

import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseDecisionClock implements DecisionClock {
  private final JdbcTemplate jdbc;

  public DatabaseDecisionClock(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Instant now() {
    return jdbc.queryForObject("select clock_timestamp()", OffsetDateTime.class)
        .toInstant()
        .truncatedTo(ChronoUnit.MICROS);
  }
}
