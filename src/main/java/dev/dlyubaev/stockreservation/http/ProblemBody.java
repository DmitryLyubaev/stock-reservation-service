package dev.dlyubaev.stockreservation.http;

import org.springframework.http.HttpStatus;

public record ProblemBody(
    String type, String title, int status, String code, String detail, String instance) {
  public static ProblemBody of(HttpStatus status, String code, String path) {
    return new ProblemBody(
        "about:blank",
        status.getReasonPhrase(),
        status.value(),
        code,
        status.getReasonPhrase(),
        path);
  }
}
