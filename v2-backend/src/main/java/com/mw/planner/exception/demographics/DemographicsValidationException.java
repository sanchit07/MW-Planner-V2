package com.mw.planner.exception.demographics;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class DemographicsValidationException extends BaseException {

  public DemographicsValidationException() {
    super(ErrorCode.DEMOGRAPHICS_VALIDATION_FAILED, "Demographics validation failed");
  }

  public DemographicsValidationException(String message) {
    super(ErrorCode.DEMOGRAPHICS_VALIDATION_FAILED, message);
  }

  public DemographicsValidationException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }

  public DemographicsValidationException(ErrorCode errorCode, String message, Throwable cause) {
    super(errorCode, message, cause);
  }
}
