package dev.dlyubaev.stockreservation.reservations;

import dev.dlyubaev.stockreservation.commands.*;
import dev.dlyubaev.stockreservation.http.ApiErrors.ApiFailure;
import dev.dlyubaev.stockreservation.reservations.ReservationDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {
  private final ReservationService reservations;
  private final IdempotentCommands commands;

  public ReservationController(ReservationService reservations, IdempotentCommands commands) {
    this.reservations = reservations;
    this.commands = commands;
  }

  @PostMapping
  public ResponseEntity<String> reserve(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody ReserveRequest body,
      HttpServletRequest request) {
    return response(
        commands.execute(
            key,
            CommandIdentity.canonicalize("reserve", request.getRequestURI(), body),
            () -> reservations.reserve(body)));
  }

  @GetMapping("/{id}")
  public ReservationSnapshot read(@PathVariable UUID id) {
    return reservations.read(id);
  }

  @PostMapping("/{id}/confirm")
  public ResponseEntity<String> confirm(
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> body,
      HttpServletRequest request) {
    return transition(id, key, body, request, ReservationState.CONFIRMED);
  }

  @PostMapping("/{id}/cancel")
  public ResponseEntity<String> cancel(
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> body,
      HttpServletRequest request) {
    return transition(id, key, body, request, ReservationState.CANCELLED);
  }

  @PostMapping("/{id}/expire")
  public ResponseEntity<String> expire(
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> body,
      HttpServletRequest request) {
    return transition(id, key, body, request, ReservationState.EXPIRED);
  }

  private ResponseEntity<String> transition(
      UUID id,
      String key,
      Map<String, Object> body,
      HttpServletRequest request,
      ReservationState terminal) {
    if (!body.isEmpty()) throw new ApiFailure(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    return response(
        commands.execute(
            key,
            CommandIdentity.canonicalize(terminal.name(), request.getRequestURI(), body),
            () -> reservations.transition(id, terminal)));
  }

  private ResponseEntity<String> response(OperationResult result) {
    var response =
        ResponseEntity.status(result.status())
            .contentType(
                result.status() >= 400
                    ? MediaType.APPLICATION_PROBLEM_JSON
                    : MediaType.APPLICATION_JSON);
    if (result.replayed()) response.header("Idempotency-Replayed", "true");
    return response.body(result.body());
  }
}
