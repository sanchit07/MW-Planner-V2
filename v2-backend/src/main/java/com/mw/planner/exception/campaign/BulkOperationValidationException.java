package com.mw.planner.exception.campaign;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

/** Exception thrown when bulk operation validation fails */
public class BulkOperationValidationException extends BaseException {

  public BulkOperationValidationException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }
}
