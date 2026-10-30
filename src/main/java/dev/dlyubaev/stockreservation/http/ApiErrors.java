package dev.dlyubaev.stockreservation.http;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiErrors {
  public static final class ApiFailure extends RuntimeException {
    public final HttpStatus status;
    public final String code;

    public ApiFailure(HttpStatus status, String code) {
      super(code);
      this.status = status;
      this.code = code;
    }
  }

  @ExceptionHandler(ApiFailure.class)
  public ResponseEntity<ProblemBody> known(ApiFailure failure, HttpServletRequest request) {
    return problem(failure.status, failure.code, request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ProblemBody> missing(HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", request);
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ProblemBody> methodNotAllowed(
      org.springframework.web.HttpRequestMethodNotSupportedException failure,
      HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .headers(failure.getHeaders())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(
            ProblemBody.of(
                HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", request.getRequestURI()));
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ProblemBody> media(HttpServletRequest request) {
    return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", request);
  }

  @ExceptionHandler({
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.bind.MethodArgumentNotValidException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.web.bind.MissingRequestHeaderException.class
  })
  public ResponseEntity<ProblemBody> invalid(HttpServletRequest request) {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemBody> unexpected(Exception failure, HttpServletRequest request) {
    Throwable cause = failure;
    for (int depth = 0; cause != null && depth < 32; depth++, cause = cause.getCause()) {
      if (cause instanceof java.sql.SQLException sql
          && ("55P03".equals(sql.getSQLState()) || "57014".equals(sql.getSQLState()))) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "BUSY", request);
      }
    }
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", request);
  }

  private ResponseEntity<ProblemBody> problem(
      HttpStatus status, String code, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(ProblemBody.of(status, code, request.getRequestURI()));
  }
}
