package dev.dlyubaev.stockreservation.commands;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RequestStore {
  public record RequestRecord(String identity, Integer status, String body) {}

  private final JdbcTemplate jdbc;

  public RequestStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public RequestRecord claimAndLock(String key, String identity) {
    jdbc.update(
        "insert into request_results(request_key,canonical_identity) values (?,?) on conflict(request_key) do nothing",
        key,
        identity);
    return jdbc.queryForObject(
        "select canonical_identity,response_status,response_body from request_results where request_key=? for update",
        (rs, n) -> new RequestRecord(rs.getString(1), (Integer) rs.getObject(2), rs.getString(3)),
        key);
  }

  public void complete(String key, int status, String body) {
    if (jdbc.update(
            "update request_results set response_status=?,response_body=? where request_key=? and response_status is null",
            status,
            body,
            key)
        != 1) throw new IllegalStateException("Request completion was not exclusive");
  }
}
