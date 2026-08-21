package com.mw.planner.exception.country;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CountryInactiveException extends BaseException {
  public CountryInactiveException(String id) {
    super(ErrorCode.COUNTRY_VALIDATION_FAILED, "Country with ID " + id + " is inactive");
  }
}
