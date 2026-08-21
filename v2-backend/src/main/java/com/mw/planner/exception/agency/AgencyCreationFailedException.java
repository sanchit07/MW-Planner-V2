package com.mw.planner.exception.agency;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class AgencyCreationFailedException extends BaseException {
  public AgencyCreationFailedException(String name) {
    super(ErrorCode.AGENCY_CREATION_FAILED, "Failed to create agency with name: " + name, name);
  }

  public AgencyCreationFailedException(String name, String reason) {
    super(
        ErrorCode.AGENCY_CREATION_FAILED,
        "Failed to create agency with name: " + name + ". Reason: " + reason,
        name,
        reason);
  }
}
