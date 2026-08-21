package com.mw.planner.exception.creative;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CreativeAssignmentNotFoundException extends BaseException {

  public CreativeAssignmentNotFoundException(String lineItemId) {
    super(
        ErrorCode.CREATIVE_ASSIGNMENT_NOT_FOUND,
        "No creative assignment found for line item: " + lineItemId,
        lineItemId);
  }
}
