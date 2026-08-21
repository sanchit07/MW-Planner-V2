package com.mw.planner.exception.storage;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class StorageDeleteFailedException extends BaseException {

  public StorageDeleteFailedException(String message) {
    super(ErrorCode.STORAGE_DELETE_FAILED, message);
  }

  public StorageDeleteFailedException(String message, Throwable cause) {
    super(ErrorCode.STORAGE_DELETE_FAILED, message, cause);
  }
}
