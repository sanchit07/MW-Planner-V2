package com.mw.recommendation.engine.exception.auth;

import com.mw.recommendation.engine.enums.ErrorCode;
import com.mw.recommendation.engine.exception.BaseException;

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
