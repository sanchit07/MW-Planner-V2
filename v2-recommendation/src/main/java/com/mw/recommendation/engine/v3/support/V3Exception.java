package com.mw.recommendation.engine.v3.support;

import lombok.Getter;

/** Exception for synchronous v3 failures, handled by {@code V3ExceptionAdvice} only. */
@Getter
public class V3Exception extends RuntimeException {

  private final V3ErrorCode errorCode;

  public V3Exception(V3ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }
}
