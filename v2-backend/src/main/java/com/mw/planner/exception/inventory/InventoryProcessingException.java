package com.mw.planner.exception.inventory;

/** Base exception for all inventory processing related errors */
public class InventoryProcessingException extends RuntimeException {

  private final String messageId;
  private final String inventoryId;

  public InventoryProcessingException(String message) {
    super(message);
    this.messageId = null;
    this.inventoryId = null;
  }

  public InventoryProcessingException(String message, Throwable cause) {
    super(message, cause);
    this.messageId = null;
    this.inventoryId = null;
  }

  public InventoryProcessingException(String message, String messageId) {
    super(message);
    this.messageId = messageId;
    this.inventoryId = null;
  }

  public InventoryProcessingException(String message, String messageId, Throwable cause) {
    super(message, cause);
    this.messageId = messageId;
    this.inventoryId = null;
  }

  public InventoryProcessingException(String message, String messageId, String inventoryId) {
    super(message);
    this.messageId = messageId;
    this.inventoryId = inventoryId;
  }

  public InventoryProcessingException(
      String message, String messageId, String inventoryId, Throwable cause) {
    super(message, cause);
    this.messageId = messageId;
    this.inventoryId = inventoryId;
  }

  public String getMessageId() {
    return messageId;
  }

  public String getInventoryId() {
    return inventoryId;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(super.toString());
    if (messageId != null) {
      sb.append(" [MessageId: ").append(messageId).append("]");
    }
    if (inventoryId != null) {
      sb.append(" [InventoryId: ").append(inventoryId).append("]");
    }
    return sb.toString();
  }
}
