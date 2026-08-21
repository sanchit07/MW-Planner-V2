package com.mw.planner.exception.inventory;

import com.mw.planner.enums.ErrorCode;
import lombok.Getter;

/** Exception thrown when the MW Influence API returns a non-successful status. */
@Getter
public class InventoryMeasureApiFailureException extends RuntimeException {

  private final ErrorCode errorCode;
  private final String failureMessage;

  public InventoryMeasureApiFailureException(ErrorCode errorCode, String failureMessage) {
    super(String.format("MW Influence API failed with status '%s'", failureMessage));
    this.errorCode = errorCode;
    this.failureMessage = failureMessage;
  }

  public InventoryMeasureApiFailureException(
      ErrorCode errorCode, String failureMessage, Throwable cause) {
    super(String.format("MW Influence API failed with status '%s'", failureMessage), cause);
    this.errorCode = errorCode;
    this.failureMessage = failureMessage;
  }
}
