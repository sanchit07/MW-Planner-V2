package com.mw.planner.exception.auth;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class AuthenticationException extends BaseException {

  public AuthenticationException() {
    super(ErrorCode.UNAUTHORIZED, "Authentication failed");
  }

  public AuthenticationException(String message) {
    super(ErrorCode.UNAUTHORIZED, message);
  }

  public AuthenticationException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }

  public AuthenticationException(ErrorCode errorCode, String message, Throwable cause) {
    super(errorCode, message, cause);
  }
}
