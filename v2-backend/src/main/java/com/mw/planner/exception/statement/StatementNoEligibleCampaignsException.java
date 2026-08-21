package com.mw.planner.exception.statement;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class StatementNoEligibleCampaignsException extends BaseException {
  public StatementNoEligibleCampaignsException() {
    super(
        ErrorCode.STATEMENT_NO_ELIGIBLE_CAMPAIGNS,
        "None of the selected campaigns are eligible for billing");
  }
}
