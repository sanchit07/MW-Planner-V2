package com.mw.planner.exception.agency;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class AgencyCreationException extends BaseException {
  public AgencyCreationException(String message) {
    super(ErrorCode.AGENCY_CREATION_FAILED, message);
  }

  public AgencyCreationException(String message, String field) {
    super(ErrorCode.AGENCY_CREATION_FAILED, message, field);
  }
}
