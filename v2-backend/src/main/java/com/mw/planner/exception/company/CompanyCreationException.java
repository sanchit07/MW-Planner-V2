package com.mw.planner.exception.company;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CompanyCreationException extends BaseException {

  public CompanyCreationException(String message) {
    super(ErrorCode.COMPANY_CREATION_FAILED, message);
  }

  public CompanyCreationException(String message, Throwable cause) {
    super(ErrorCode.COMPANY_CREATION_FAILED, message, cause);
  }
}
