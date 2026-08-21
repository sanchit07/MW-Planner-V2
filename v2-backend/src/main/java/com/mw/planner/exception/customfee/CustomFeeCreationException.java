package com.mw.planner.exception.customfee;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CustomFeeCreationException extends BaseException {
  public CustomFeeCreationException(String message) {
    super(ErrorCode.CUSTOM_FEE_CREATION_FAILED, message);
  }

  public CustomFeeCreationException(String message, Throwable cause) {
    super(ErrorCode.CUSTOM_FEE_CREATION_FAILED, message, cause);
  }
}
