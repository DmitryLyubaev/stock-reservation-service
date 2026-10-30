package dev.dlyubaev.stockreservation.support;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Timeout(60)
public abstract class HttpFixture {
  protected static ConfigurableApplicationContext context;
  protected static JdbcTemplate jdbc;
  protected static String baseUrl;
  protected static ControlledClock clock;
  protected static final JsonMapper mapper = new JsonMapper();
  protected static final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @BeforeAll
  static void startApp() throws Exception {
    if (context != null && context.isActive()) return;
    PostgresFixture.start();
    // Class lookup keeps the test compilable before the application exists.
    context =
        new SpringApplication(
                Class.forName("dev.dlyubaev.stockreservation.StockReservationApplication"),
                ControlledClock.Wiring.class)
            .run(
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--spring.datasource.url=" + PostgresFixture.jdbcUrl(),
                "--spring.datasource.username=demo",
                "--spring.datasource.password=demo",
                "--spring.datasource.hikari.maximum-pool-size=24");
    jdbc = context.getBean(JdbcTemplate.class);
    clock = context.getBean(ControlledClock.class);
    baseUrl = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
  }

  @BeforeEach
  void freshData() {
    TransactionProbe.reset();
    clock.reset();
    assertThat(jdbc.queryForObject("select to_regclass('public.inventory')::text", String.class))
        .as("Flyway must create inventory before fixtures can seed it")
        .isEqualTo("inventory");
    jdbc.execute("truncate request_results,reservations,inventory");
    jdbc.update(
        "insert into inventory(product_code,initial_quantity,on_hand,held) values('DEMO-RED',10,10,0),('DEMO-BLUE',10,10,0),('DEMO-GREEN',10,10,0)");
  }

  protected HttpResponse<String> get(String path) throws Exception {
    return request("GET", path, "", Map.of());
  }

  protected HttpResponse<String> post(
      String path, String key, String body, Map<String, String> extra) throws Exception {
    var headers = new HashMap<>(extra);
    headers.putIfAbsent("Content-Type", "application/json");
    if (key != null) headers.put("Idempotency-Key", key);
    return request("POST", path, body, headers);
  }

  protected HttpResponse<String> request(
      String method, String path, String body, Map<String, String> headers) throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(20));
    headers.forEach(builder::header);
    return client.send(
        builder
            .method(
                method,
                body.isEmpty()
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  protected JsonNode json(HttpResponse<String> response) {
    return mapper.readTree(response.body());
  }

  protected static String body(String orderReference, String productCode, int quantity) {
    return mapper.writeValueAsString(
        Map.of(
            "order_reference", orderReference, "product_code", productCode, "quantity", quantity));
  }

  protected void assertInvariants() {
    DatabaseAssertions.assertInvariants(jdbc);
  }
}
