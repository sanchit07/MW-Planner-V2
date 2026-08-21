package com.mw.planner.exception.csv;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CsvUploadException extends BaseException {

  public CsvUploadException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }

  public CsvUploadException(ErrorCode errorCode, String message, Object... args) {
    super(errorCode, message, args);
  }

  public CsvUploadException(ErrorCode errorCode, String message, Throwable cause, Object... args) {
    super(errorCode, message, cause, args);
  }
}
