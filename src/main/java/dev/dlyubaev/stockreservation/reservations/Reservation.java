package dev.dlyubaev.stockreservation.reservations;

import dev.dlyubaev.stockreservation.reservations.ReservationDtos.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {
  @Id private UUID id;

  @Column(name = "order_reference", length = 64, nullable = false, updatable = false)
  private String orderReference;

  @Column(name = "product_code", length = 32, nullable = false, updatable = false)
  private String productCode;

  @Column(nullable = false, updatable = false)
  private int quantity;

  @Enumerated(EnumType.STRING)
  @Column(length = 16, nullable = false)
  private ReservationState state;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "decided_at")
  private Instant decidedAt;

  protected Reservation() {}

  public Reservation(UUID id, ReserveRequest request, Instant now) {
    this.id = id;
    this.orderReference = request.orderReference();
    this.productCode = request.productCode();
    this.quantity = request.quantity();
    this.state = ReservationState.ACTIVE;
    this.createdAt = now;
    this.expiresAt = now.plusSeconds(300);
  }

  public String getProductCode() {
    return productCode;
  }

  public int getQuantity() {
    return quantity;
  }

  public ReservationState getState() {
    return state;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void finish(ReservationState terminal, Instant decisionTime) {
    if (state != ReservationState.ACTIVE || terminal == ReservationState.ACTIVE)
      throw new IllegalStateException("Invalid terminal transition");
    state = terminal;
    decidedAt = decisionTime;
  }

  public ReservationSnapshot snapshot(Instant observation) {
    return new ReservationSnapshot(
        id,
        orderReference,
        productCode,
        quantity,
        state,
        createdAt,
        expiresAt,
        decidedAt,
        observation,
        observation == null
            ? null
            : state == ReservationState.ACTIVE && !observation.isBefore(expiresAt));
  }
}
