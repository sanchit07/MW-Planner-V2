package com.mw.planner.exception.agency;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class AgencyUpdateException extends BaseException {
  public AgencyUpdateException(String id, String message) {
    super(ErrorCode.AGENCY_UPDATE_FAILED, "Failed to update agency with ID " + id + ": " + message);
  }
}
