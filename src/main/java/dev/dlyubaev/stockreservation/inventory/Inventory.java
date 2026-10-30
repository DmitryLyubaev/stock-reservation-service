package dev.dlyubaev.stockreservation.inventory;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory")
public class Inventory {
  @Id
  @Column(name = "product_code", length = 32)
  private String productCode;

  @Column(name = "initial_quantity", nullable = false)
  private int initialQuantity;

  @Column(name = "on_hand", nullable = false)
  private int onHand;

  @Column(nullable = false)
  private int held;

  protected Inventory() {}

  public String getProductCode() {
    return productCode;
  }

  public int getOnHand() {
    return onHand;
  }

  public int getHeld() {
    return held;
  }

  public int getAvailable() {
    return onHand - held;
  }

  public void hold(int quantity) {
    if (quantity <= 0 || quantity > getAvailable())
      throw new IllegalArgumentException("Invalid hold quantity");
    held += quantity;
  }

  public void releaseHold(int quantity) {
    if (quantity <= 0 || quantity > held)
      throw new IllegalArgumentException("Invalid release quantity");
    held -= quantity;
  }

  public void consumeHold(int quantity) {
    releaseHold(quantity);
    onHand -= quantity;
  }
}
