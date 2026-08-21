package com.mw.planner.exception.inventory;

import java.util.List;

/** Exception thrown when inventory message validation fails */
public class InventoryValidationException extends InventoryProcessingException {

  private final List<String> validationErrors;

  public InventoryValidationException(String message) {
    super(message);
    this.validationErrors = null;
  }

  public InventoryValidationException(String message, String messageId) {
    super(message, messageId);
    this.validationErrors = null;
  }

  public InventoryValidationException(
      String message, String messageId, List<String> validationErrors) {
    super(message, messageId);
    this.validationErrors = validationErrors;
  }

  public InventoryValidationException(
      String message, String messageId, List<String> validationErrors, Throwable cause) {
    super(message, messageId, cause);
    this.validationErrors = validationErrors;
  }

  public List<String> getValidationErrors() {
    return validationErrors;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(super.toString());
    if (validationErrors != null && !validationErrors.isEmpty()) {
      sb.append(" [ValidationErrors: ").append(validationErrors).append("]");
    }
    return sb.toString();
  }
}
