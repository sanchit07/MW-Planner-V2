package com.mw.planner.exception.inventory;

/** Exception thrown when inventory message conversion fails */
public class InventoryConversionException extends InventoryProcessingException {

  private final String conversionType;

  public InventoryConversionException(String message) {
    super(message);
    this.conversionType = null;
  }

  public InventoryConversionException(String message, String messageId) {
    super(message, messageId);
    this.conversionType = null;
  }

  public InventoryConversionException(String message, String messageId, String conversionType) {
    super(message, messageId);
    this.conversionType = conversionType;
  }

  public InventoryConversionException(
      String message, String messageId, String conversionType, Throwable cause) {
    super(message, messageId, cause);
    this.conversionType = conversionType;
  }

  public String getConversionType() {
    return conversionType;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(super.toString());
    if (conversionType != null) {
      sb.append(" [ConversionType: ").append(conversionType).append("]");
    }
    return sb.toString();
  }
}
