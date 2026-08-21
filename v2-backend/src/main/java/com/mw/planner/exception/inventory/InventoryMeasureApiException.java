package com.mw.planner.exception.inventory;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

/** Exception thrown when there are errors communicating with the MW Influence API */
public class InventoryMeasureApiException extends BaseException {

  public InventoryMeasureApiException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }

  public InventoryMeasureApiException(ErrorCode errorCode, String message, Throwable cause) {
    super(errorCode, message, cause);
  }

  public InventoryMeasureApiException(ErrorCode errorCode, String message, String... details) {
    super(errorCode, message, (Object[]) details);
  }

  public InventoryMeasureApiException(
      ErrorCode errorCode, String message, Throwable cause, String... details) {
    super(errorCode, message, cause, (Object[]) details);
  }
}
