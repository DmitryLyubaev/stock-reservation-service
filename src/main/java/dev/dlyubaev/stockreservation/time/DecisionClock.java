package dev.dlyubaev.stockreservation.time;

import java.time.Instant;

public interface DecisionClock {
  Instant now();
}
