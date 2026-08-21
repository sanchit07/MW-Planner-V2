package com.mw.planner.exception.storage;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class StorageFileNotFoundException extends BaseException {

  public StorageFileNotFoundException(String filePath) {
    super(ErrorCode.STORAGE_DOWNLOAD_FAILED, "Creative file not found: " + filePath);
  }

  public StorageFileNotFoundException(String filePath, Throwable cause) {
    super(ErrorCode.STORAGE_DOWNLOAD_FAILED, "Creative file not found: " + filePath, cause);
  }
}
