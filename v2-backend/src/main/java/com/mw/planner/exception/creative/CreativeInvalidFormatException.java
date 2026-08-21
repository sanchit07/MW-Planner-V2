package com.mw.planner.exception.creative;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CreativeInvalidFormatException extends BaseException {
  public CreativeInvalidFormatException(String format) {
    super(ErrorCode.CREATIVE_INVALID_FORMAT, "Invalid creative format: " + format, format);
  }
}
