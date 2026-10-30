package dev.dlyubaev.stockreservation.commands;

import dev.dlyubaev.stockreservation.http.ProblemBody;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Service
public class IdempotentCommands {
  private final RequestStore requests;
  private final JdbcTemplate jdbc;
  private final JsonMapper mapper;

  public IdempotentCommands(RequestStore requests, JdbcTemplate jdbc, JsonMapper mapper) {
    this.requests = requests;
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public OperationResult execute(String key, String identity, Supplier<OperationResult> work) {
    jdbc.execute("set local lock_timeout='3s'");
    jdbc.execute("set local statement_timeout='8s'");
    var stored = requests.claimAndLock(key, identity);
    if (!stored.identity().equals(identity))
      return new OperationResult(
          409,
          mapper.writeValueAsString(
              ProblemBody.of(
                  HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", CommandIdentity.targetOf(identity))),
          false);
    if (stored.status() != null) return new OperationResult(stored.status(), stored.body(), true);
    var result = work.get();
    if (result.status() < 200 || result.status() >= 500)
      throw new IllegalStateException("Cannot retain a technical failure");
    requests.complete(key, result.status(), result.body());
    return result;
  }
}
