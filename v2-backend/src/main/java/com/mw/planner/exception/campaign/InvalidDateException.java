package com.mw.planner.exception.campaign;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class InvalidDateException extends BaseException {
  public InvalidDateException(String date) {
    super(ErrorCode.INVALID_DATE_FORMAT, date);
  }
}
