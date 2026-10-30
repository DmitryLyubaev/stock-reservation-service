package dev.dlyubaev.stockreservation.reservations;

import dev.dlyubaev.stockreservation.commands.OperationResult;
import dev.dlyubaev.stockreservation.http.*;
import dev.dlyubaev.stockreservation.inventory.InventoryRepository;
import dev.dlyubaev.stockreservation.reservations.ReservationDtos.*;
import dev.dlyubaev.stockreservation.time.DecisionClock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ReservationService {
  private final InventoryRepository inventory;
  private final ReservationRepository reservations;
  private final DecisionClock clock;
  private final JsonMapper mapper;

  public ReservationService(
      InventoryRepository inventory,
      ReservationRepository reservations,
      DecisionClock clock,
      JsonMapper mapper) {
    this.inventory = inventory;
    this.reservations = reservations;
    this.clock = clock;
    this.mapper = mapper;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public OperationResult reserve(ReserveRequest request) {
    var item = inventory.lockByProductCode(request.productCode());
    if (item.isEmpty())
      return reject(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "/api/reservations");
    var now = clock.now();
    if (item.get().getAvailable() < request.quantity())
      return reject(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "/api/reservations");
    item.get().hold(request.quantity());
    var reservation = reservations.saveAndFlush(new Reservation(UUID.randomUUID(), request, now));
    return new OperationResult(201, mapper.writeValueAsString(reservation.snapshot(null)), false);
  }

  @Transactional(readOnly = true)
  public ReservationSnapshot read(UUID id) {
    return reservations
        .findById(id)
        .orElseThrow(() -> new ApiErrors.ApiFailure(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND"))
        .snapshot(clock.now());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public OperationResult transition(UUID id, ReservationState requestedTerminal) {
    if (requestedTerminal == ReservationState.ACTIVE)
      throw new IllegalArgumentException("Terminal state required");
    String action =
        switch (requestedTerminal) {
          case CONFIRMED -> "confirm";
          case CANCELLED -> "cancel";
          case EXPIRED -> "expire";
          default -> throw new IllegalArgumentException("Terminal state required");
        };
    String path = "/api/reservations/" + id + "/" + action;
    // A scalar projection avoids caching a stale entity before waiting for its product lock.
    var product = reservations.findProductCode(id);
    if (product.isEmpty()) return reject(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", path);
    var item = inventory.lockByProductCode(product.get()).orElseThrow();
    var reservation = reservations.lockById(id).orElseThrow();
    var now = clock.now();
    if (reservation.getState() != ReservationState.ACTIVE) {
      if (reservation.getState() == requestedTerminal)
        return new OperationResult(
            200, mapper.writeValueAsString(reservation.snapshot(null)), false);
      return reject(HttpStatus.CONFLICT, "INVALID_STATE", path);
    }
    if (!now.isBefore(reservation.getExpiresAt())) {
      item.releaseHold(reservation.getQuantity());
      reservation.finish(ReservationState.EXPIRED, now);
      reservations.saveAndFlush(reservation);
      if (requestedTerminal != ReservationState.EXPIRED)
        return reject(HttpStatus.CONFLICT, "RESERVATION_EXPIRED", path);
    } else {
      if (requestedTerminal == ReservationState.EXPIRED)
        return reject(HttpStatus.CONFLICT, "NOT_DUE", path);
      if (requestedTerminal == ReservationState.CONFIRMED)
        item.consumeHold(reservation.getQuantity());
      else item.releaseHold(reservation.getQuantity());
      reservation.finish(requestedTerminal, now);
      reservations.saveAndFlush(reservation);
    }
    return new OperationResult(200, mapper.writeValueAsString(reservation.snapshot(null)), false);
  }

  private OperationResult reject(HttpStatus status, String code, String path) {
    return new OperationResult(
        status.value(), mapper.writeValueAsString(ProblemBody.of(status, code, path)), false);
  }
}
