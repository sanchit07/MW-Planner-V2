package com.mw.planner.exception.inventory;

/** Exception thrown when inventory persistence operations fail */
public class InventoryPersistenceException extends InventoryProcessingException {

  private final String operation;
  private final String inventoryId;

  public InventoryPersistenceException(String message) {
    super(message);
    this.operation = null;
    this.inventoryId = null;
  }

  public InventoryPersistenceException(String message, String messageId) {
    super(message, messageId);
    this.operation = null;
    this.inventoryId = null;
  }

  public InventoryPersistenceException(String message, String messageId, String inventoryId) {
    super(message, messageId, inventoryId);
    this.operation = null;
    this.inventoryId = inventoryId;
  }

  public InventoryPersistenceException(
      String message, String messageId, String inventoryId, String operation) {
    super(message, messageId, inventoryId);
    this.operation = operation;
    this.inventoryId = inventoryId;
  }

  public InventoryPersistenceException(
      String message, String messageId, String inventoryId, String operation, Throwable cause) {
    super(message, messageId, inventoryId, cause);
    this.operation = operation;
    this.inventoryId = inventoryId;
  }

  public String getOperation() {
    return operation;
  }

  @Override
  public String getInventoryId() {
    return inventoryId;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(super.toString());
    if (operation != null) {
      sb.append(" [Operation: ").append(operation).append("]");
    }
    return sb.toString();
  }
}
