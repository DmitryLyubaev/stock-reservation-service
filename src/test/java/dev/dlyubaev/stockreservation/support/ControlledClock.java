package dev.dlyubaev.stockreservation.support;

import dev.dlyubaev.stockreservation.time.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

public final class ControlledClock implements DecisionClock {
  private final DatabaseDecisionClock database;
  private final AtomicReference<Instant> instant = new AtomicReference<>();

  public ControlledClock(DatabaseDecisionClock database) {
    this.database = database;
  }

  public void set(Instant value) {
    instant.set(value.truncatedTo(ChronoUnit.MICROS));
  }

  public void reset() {
    instant.set(null);
  }

  @Override
  public Instant now() {
    var value = instant.get();
    return value == null ? database.now() : value;
  }

  @TestConfiguration(proxyBeanMethods = false)
  public static class Wiring {
    @Bean
    static org.springframework.beans.factory.config.BeanPostProcessor transactionProbe() {
      return TransactionProbe.repositoryInterceptor();
    }

    @Bean
    @Primary
    ControlledClock controlledClock(DatabaseDecisionClock database) {
      return new ControlledClock(database);
    }
  }
}
