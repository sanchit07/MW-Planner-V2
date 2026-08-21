package com.mw.planner.exception.creative;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CreativeFileTooLargeException extends BaseException {
  public CreativeFileTooLargeException(long fileSize, long maxSize) {
    super(
        ErrorCode.CREATIVE_FILE_TOO_LARGE,
        "File size exceeds limit. Size: " + fileSize + ", Max: " + maxSize,
        fileSize,
        maxSize);
  }
}
