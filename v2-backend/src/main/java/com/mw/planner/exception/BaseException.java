package com.mw.planner.exception;

import com.mw.planner.enums.ErrorCode;
import lombok.Getter;

@Getter
public abstract class BaseException extends RuntimeException {
  private final ErrorCode errorCode;
  private final Object[] args;

  protected BaseException(ErrorCode errorCode, String message, Object... args) {
    super(message);
    this.errorCode = errorCode;
    this.args = args;
  }

  protected BaseException(ErrorCode errorCode, String message, Throwable cause, Object... args) {
    super(message, cause);
    this.errorCode = errorCode;
    this.args = args;
  }
}
