package dev.dlyubaev.stockreservation.inventory;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface InventoryRepository extends JpaRepository<Inventory, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Inventory i where i.productCode = :productCode")
  Optional<Inventory> lockByProductCode(String productCode);
}
