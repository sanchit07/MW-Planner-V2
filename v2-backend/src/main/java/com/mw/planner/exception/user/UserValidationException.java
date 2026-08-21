package com.mw.planner.exception.user;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class UserValidationException extends BaseException {
  public UserValidationException(String field, Object value) {
    super(
        ErrorCode.USER_VALIDATION_FAILED,
        "Invalid user data: " + field + " = " + value,
        field,
        value);
  }

  public UserValidationException(String message) {
    super(ErrorCode.USER_VALIDATION_FAILED, message);
  }
}
