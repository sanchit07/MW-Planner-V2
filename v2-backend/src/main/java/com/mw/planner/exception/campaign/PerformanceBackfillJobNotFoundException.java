package com.mw.planner.exception.campaign;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class PerformanceBackfillJobNotFoundException extends BaseException {
  public PerformanceBackfillJobNotFoundException(String jobId) {
    super(
        ErrorCode.PERFORMANCE_BACKFILL_JOB_NOT_FOUND,
        "Performance backfill job not found with ID: " + jobId,
        jobId);
  }
}
