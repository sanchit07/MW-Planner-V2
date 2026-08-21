package com.mw.planner.exception.agency;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class AgencyAlreadyExistsException extends BaseException {
  public AgencyAlreadyExistsException(String name) {
    super(ErrorCode.AGENCY_ALREADY_EXISTS, "Agency with name " + name + " already exists", name);
  }
}
