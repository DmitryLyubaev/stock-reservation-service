package dev.dlyubaev.stockreservation.reservations;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class ReservationDtos {
  private ReservationDtos() {}

  public record ReserveRequest(
      @NotNull @Pattern(regexp = "[A-Za-z0-9._:-]{1,64}") String orderReference,
      @NotNull @Pattern(regexp = "[A-Za-z0-9._:-]{1,32}") String productCode,
      @NotNull @Min(1) @Max(1000000) Integer quantity) {}

  public record ReservationSnapshot(
      UUID id,
      String orderReference,
      String productCode,
      int quantity,
      ReservationState state,
      Instant createdAt,
      Instant expiresAt,
      Instant decidedAt,
      @JsonInclude(JsonInclude.Include.NON_NULL) Instant asOf,
      @JsonInclude(JsonInclude.Include.NON_NULL) Boolean overdue) {}
}
