package dev.dlyubaev.stockreservation.support;

import dev.dlyubaev.stockreservation.reservations.ReservationRepository;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

public final class TransactionProbe {
  private static final AtomicBoolean armed = new AtomicBoolean();
  private static final AtomicBoolean injected = new AtomicBoolean();

  private TransactionProbe() {}

  public static void reset() {
    armed.set(false);
    injected.set(false);
  }

  public static void failNextFlush() {
    injected.set(false);
    armed.set(true);
  }

  public static boolean wasInjected() {
    return injected.get();
  }

  public static BeanPostProcessor repositoryInterceptor() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String name) {
        if (!(bean instanceof ReservationRepository)) return bean;
        var proxy = new ProxyFactory(bean);
        proxy.addAdvice(
            (MethodInterceptor)
                invocation -> {
                  Object result = invocation.proceed();
                  if (invocation.getMethod().getName().equals("saveAndFlush")
                      && armed.compareAndSet(true, false)) {
                    injected.set(true);
                    throw new IllegalStateException("Injected after real SQL flush");
                  }
                  return result;
                });
        return proxy.getProxy();
      }
    };
  }

  public static Hold holdInventory(DataSource source) throws SQLException {
    var connection = source.getConnection();
    try {
      connection.setAutoCommit(false);
      try (var statement = connection.createStatement();
          var rows =
              statement.executeQuery(
                  "select product_code from inventory where product_code='DEMO-RED' for update")) {
        if (!rows.next()) throw new SQLException("Fixture product absent");
      }
      return new Hold(connection);
    } catch (Exception failure) {
      connection.close();
      throw failure;
    }
  }

  public static final class Hold implements AutoCloseable {
    private final Connection connection;
    private boolean closed;

    Hold(Connection connection) {
      this.connection = connection;
    }

    @Override
    public void close() throws SQLException {
      if (!closed) {
        closed = true;
        try {
          connection.rollback();
        } finally {
          connection.close();
        }
      }
    }
  }

  public static int awaitBlocked(JdbcTemplate jdbc, int minimum) throws Exception {
    long deadline = System.nanoTime() + 2_000_000_000L;
    int blocked = 0;
    do {
      blocked =
          jdbc.queryForObject(
              "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and pid<>pg_backend_pid()",
              Integer.class);
      if (blocked >= minimum) return blocked;
      Thread.sleep(10);
    } while (System.nanoTime() < deadline);
    throw new AssertionError(
        "Expected actual database lock waiters >=" + minimum + ", observed " + blocked);
  }

  public static void evidence(String name, Map<String, ?> data) throws Exception {
    String directory = System.getenv("P20_RESULTS_DIR");
    if (directory == null)
      throw new IllegalStateException("P20_RESULTS_DIR required for measured evidence");
    var output = new TreeMap<String, Object>(data);
    output.put("source_sha", System.getenv("P20_SOURCE_SHA"));
    output.put("source_tree", System.getenv("P20_SOURCE_TREE"));
    output.put("source_dirty", Boolean.parseBoolean(System.getenv("P20_SOURCE_DIRTY")));
    output.put("observed_at", java.time.Instant.now().toString());
    Files.writeString(
        Path.of(directory, name + ".json"), new JsonMapper().writeValueAsString(output));
  }
}
