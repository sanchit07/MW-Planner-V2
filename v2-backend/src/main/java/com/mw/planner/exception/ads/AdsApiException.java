package com.mw.planner.exception.ads;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

/** Exception thrown when ADS API encounters an error */
public class AdsApiException extends BaseException {

  public AdsApiException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }

  public AdsApiException(ErrorCode errorCode, String message, Object... args) {
    super(errorCode, message, args);
  }

  public AdsApiException(ErrorCode errorCode, String message, Throwable cause) {
    super(errorCode, message, cause);
  }

  public AdsApiException(ErrorCode errorCode, String message, Throwable cause, Object... args) {
    super(errorCode, message, cause, args);
  }
}
