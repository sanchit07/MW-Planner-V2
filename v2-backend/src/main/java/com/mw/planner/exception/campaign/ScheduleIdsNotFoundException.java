package com.mw.planner.exception.campaign;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;
import java.util.List;

/** Exception thrown when schedule IDs are not found in the database */
public class ScheduleIdsNotFoundException extends BaseException {

  public ScheduleIdsNotFoundException(List<String> missingScheduleIds) {
    super(
        ErrorCode.SCHEDULE_IDS_NOT_FOUND,
        "The following schedule IDs were not found: " + missingScheduleIds,
        missingScheduleIds);
  }

  public ScheduleIdsNotFoundException(String scheduleId) {
    super(ErrorCode.SCHEDULE_IDS_NOT_FOUND, "Schedule ID not found: " + scheduleId, scheduleId);
  }
}
