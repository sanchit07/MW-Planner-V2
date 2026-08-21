package com.mw.planner.exception.customfee;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CustomFeeValidationException extends BaseException {
  public CustomFeeValidationException(String message) {
    super(ErrorCode.CUSTOM_FEE_VALIDATION_FAILED, message);
  }

  public CustomFeeValidationException(String message, Throwable cause) {
    super(ErrorCode.CUSTOM_FEE_VALIDATION_FAILED, message, cause);
  }
}
