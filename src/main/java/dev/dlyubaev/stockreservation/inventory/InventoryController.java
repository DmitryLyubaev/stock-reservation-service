package dev.dlyubaev.stockreservation.inventory;

import dev.dlyubaev.stockreservation.http.ApiErrors.ApiFailure;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
  private final InventoryRepository inventory;
  private final JdbcTemplate jdbc;

  public InventoryController(InventoryRepository inventory, JdbcTemplate jdbc) {
    this.inventory = inventory;
    this.jdbc = jdbc;
  }

  @GetMapping("/{productCode}")
  public Map<String, Object> read(@PathVariable String productCode) {
    var item =
        inventory
            .findById(productCode)
            .orElseThrow(() -> new ApiFailure(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND"));
    return Map.of(
        "product_code",
        item.getProductCode(),
        "on_hand",
        item.getOnHand(),
        "held",
        item.getHeld(),
        "available",
        item.getAvailable(),
        "as_of",
        jdbc.queryForObject("select clock_timestamp()", OffsetDateTime.class).toInstant());
  }
}
