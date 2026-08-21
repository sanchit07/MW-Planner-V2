package com.mw.planner.exception.campaign;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;
import java.util.List;

/** Exception thrown when schedule IDs cannot be approved by the current user */
public class InvalidApprovalException extends BaseException {

  public InvalidApprovalException(List<String> failingScheduleIds) {
    super(
        ErrorCode.INVALID_APPROVAL,
        "Invalid approval: The following schedule IDs cannot be approved by the current user: "
            + failingScheduleIds,
        failingScheduleIds);
  }
}
