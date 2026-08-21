package com.mw.planner.exception.customfee;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CustomFeeAlreadyExistsException extends BaseException {
  public CustomFeeAlreadyExistsException(String name) {
    super(ErrorCode.CUSTOM_FEE_ALREADY_EXISTS, "Custom fee already exists with name: " + name);
  }
}
