package com.mw.planner.exception.campaign;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class PerformanceBackfillAlreadyRunningException extends BaseException {
  public PerformanceBackfillAlreadyRunningException(String runningJobId) {
    super(
        ErrorCode.PERFORMANCE_BACKFILL_ALREADY_RUNNING,
        "A performance backfill is already running with job ID: " + runningJobId,
        runningJobId);
  }
}
