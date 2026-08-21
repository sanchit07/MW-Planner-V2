package com.mw.planner.exception.user;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

/** Thrown when the IAM user context is missing required identifiers. */
public class UserContextInvalidException extends BaseException {
  public UserContextInvalidException(String missingFields) {
    super(
        ErrorCode.USER_CONTEXT_INVALID,
        "User context is missing required fields: " + missingFields,
        missingFields);
  }

  public UserContextInvalidException(String missingFields, Throwable cause) {
    super(
        ErrorCode.USER_CONTEXT_INVALID,
        "User context is missing required fields: " + missingFields,
        cause,
        missingFields);
  }
}
