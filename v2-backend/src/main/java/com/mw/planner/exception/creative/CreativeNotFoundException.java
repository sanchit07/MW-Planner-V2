package com.mw.planner.exception.creative;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CreativeNotFoundException extends BaseException {
  public CreativeNotFoundException(String creativeId) {
    super(ErrorCode.CREATIVE_NOT_FOUND, "Creative not found with ID: " + creativeId, creativeId);
  }
}
