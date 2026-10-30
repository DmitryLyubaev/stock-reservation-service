package dev.dlyubaev.stockreservation.reservations;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
  @Query("select r.productCode from Reservation r where r.id = :id")
  Optional<String> findProductCode(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Reservation r where r.id = :id")
  Optional<Reservation> lockById(UUID id);
}
