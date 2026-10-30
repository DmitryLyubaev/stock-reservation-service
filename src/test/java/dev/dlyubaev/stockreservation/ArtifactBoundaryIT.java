package dev.dlyubaev.stockreservation;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class ArtifactBoundaryIT {
  @Test
  void normalBuiltJarContainsApplicationButNoTestControls() throws Exception {
    try (var jar =
        new ZipFile(Path.of("target/stock-reservation-service-0.1.0-SNAPSHOT.jar").toFile())) {
      var entries = jar.stream().map(java.util.zip.ZipEntry::getName).toList();
      assertThat(entries)
          .contains(
              "BOOT-INF/classes/dev/dlyubaev/stockreservation/StockReservationApplication.class");
      assertThat(entries)
          .noneMatch(
              name ->
                  name.startsWith("BOOT-INF/classes/dev/dlyubaev/stockreservation/support/")
                      || name.contains("ControlledClock")
                      || name.contains("TransactionProbe")
                      || name.contains("TestConfiguration"));
    }
  }
}
