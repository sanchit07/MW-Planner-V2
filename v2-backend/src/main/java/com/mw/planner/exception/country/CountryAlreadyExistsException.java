package com.mw.planner.exception.country;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CountryAlreadyExistsException extends BaseException {
  public CountryAlreadyExistsException(String field, String value) {
    super(
        ErrorCode.COUNTRY_ALREADY_EXISTS,
        "Country with " + field + " " + value + " already exists",
        field,
        value);
  }

  public CountryAlreadyExistsException(String countryId) {
    super(
        ErrorCode.COUNTRY_ALREADY_EXISTS,
        "Country with countryId " + countryId + " already exists",
        countryId);
  }
}
