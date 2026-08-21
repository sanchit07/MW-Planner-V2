package com.mw.planner.exception.agency;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class AgencyNotFoundException extends BaseException {
  public AgencyNotFoundException(String id) {
    super(ErrorCode.AGENCY_NOT_FOUND, "Agency not found with ID: " + id);
  }
}
