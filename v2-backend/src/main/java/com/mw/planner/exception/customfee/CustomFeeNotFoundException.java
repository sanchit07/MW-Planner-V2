package com.mw.planner.exception.customfee;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CustomFeeNotFoundException extends BaseException {
  public CustomFeeNotFoundException(String id) {
    super(ErrorCode.CUSTOM_FEE_NOT_FOUND, "Custom fee not found with ID: " + id);
  }

  public CustomFeeNotFoundException(String id, String message) {
    super(ErrorCode.CUSTOM_FEE_NOT_FOUND, message + " for custom fee ID: " + id);
  }
}
