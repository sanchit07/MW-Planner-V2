package com.mw.planner.exception.storage;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class StorageDownloadFailedException extends BaseException {

  public StorageDownloadFailedException(String message) {
    super(ErrorCode.STORAGE_DOWNLOAD_FAILED, message);
  }

  public StorageDownloadFailedException(String message, Throwable cause) {
    super(ErrorCode.STORAGE_DOWNLOAD_FAILED, message, cause);
  }
}
