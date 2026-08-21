package com.mw.planner.exception.customfee;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CustomFeeUpdateException extends BaseException {
  public CustomFeeUpdateException(String id, String message) {
    super(
        ErrorCode.CUSTOM_FEE_UPDATE_FAILED,
        "Failed to update custom fee with ID: " + id + ". " + message);
  }

  public CustomFeeUpdateException(String id, String message, Throwable cause) {
    super(
        ErrorCode.CUSTOM_FEE_UPDATE_FAILED,
        "Failed to update custom fee with ID: " + id + ". " + message,
        cause);
  }
}
