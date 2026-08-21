package com.mw.planner.exception.company;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class InvalidCompanyDataException extends BaseException {

  public InvalidCompanyDataException(String fieldName, String invalidValue) {
    super(
        ErrorCode.COMPANY_INVALID_DATA,
        "Invalid company data: " + fieldName + " = " + invalidValue,
        fieldName,
        invalidValue);
  }

  public InvalidCompanyDataException(String fieldName, String invalidValue, Throwable cause) {
    super(
        ErrorCode.COMPANY_INVALID_DATA,
        "Invalid company data: " + fieldName + " = " + invalidValue,
        cause,
        fieldName,
        invalidValue);
  }
}
