package com.mw.planner.exception.campaign;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

/** Exception thrown when bulk operation fails during processing */
public class BulkOperationFailedException extends BaseException {

  public BulkOperationFailedException(
      String operationType, String campaignId, String reason, Throwable cause) {
    super(
        ErrorCode.BULK_OPERATION_FAILED,
        String.format(
            "Bulk %s operation failed for campaign %s: %s", operationType, campaignId, reason),
        cause,
        operationType,
        campaignId,
        reason);
  }
}
