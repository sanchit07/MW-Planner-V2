package com.mw.planner.exception.country;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CountryNotFoundException extends BaseException {
  public CountryNotFoundException(String id) {
    super(ErrorCode.COUNTRY_NOT_FOUND, "Country not found with ID: " + id);
  }

  public CountryNotFoundException(String id, String message) {
    super(ErrorCode.COUNTRY_NOT_FOUND, message + " for country ID: " + id);
  }
}
