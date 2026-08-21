package com.mw.planner.exception.company;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CompanyNotFoundException extends BaseException {

  public CompanyNotFoundException(String companyId) {
    super(ErrorCode.COMPANY_NOT_FOUND, "Company not found with ID: " + companyId, companyId);
  }

  public CompanyNotFoundException(String companyId, Throwable cause) {
    super(ErrorCode.COMPANY_NOT_FOUND, "Company not found with ID: " + companyId, cause, companyId);
  }
}
