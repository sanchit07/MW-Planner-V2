package com.mw.planner.exception.company;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CompanyAlreadyExistsException extends BaseException {
  public CompanyAlreadyExistsException(String name) {
    super(ErrorCode.COMPANY_ALREADY_EXISTS, "Company with name " + name + " already exists", name);
  }
}
