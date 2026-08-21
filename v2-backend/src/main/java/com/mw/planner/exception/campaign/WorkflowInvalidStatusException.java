package com.mw.planner.exception.campaign;

import com.mw.planner.domain.CampaignApprovedWorkflowStatus;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class WorkflowInvalidStatusException extends BaseException {

  public WorkflowInvalidStatusException(
      CampaignApprovedWorkflowStatus.Status currentStatus,
      CampaignApprovedWorkflowStatus.Status requiredStatus) {
    super(
        ErrorCode.WORKFLOW_INVALID_STATUS,
        "Workflow status is "
            + currentStatus.name()
            + " but required status is "
            + requiredStatus.name(),
        currentStatus.name(),
        requiredStatus.name());
  }
}
