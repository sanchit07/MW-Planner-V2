package com.mw.planner.exception.storage;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class StorageUploadFailedException extends BaseException {

  public StorageUploadFailedException(String message) {
    super(ErrorCode.STORAGE_UPLOAD_FAILED, message);
  }

  public StorageUploadFailedException(String message, Throwable cause) {
    super(ErrorCode.STORAGE_UPLOAD_FAILED, message, cause);
  }
}
