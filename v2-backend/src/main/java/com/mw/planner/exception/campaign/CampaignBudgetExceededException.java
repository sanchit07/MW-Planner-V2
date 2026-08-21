package com.mw.planner.exception.campaign;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CampaignBudgetExceededException extends BaseException {
  public CampaignBudgetExceededException(String campaignId, double budget, double spent) {
    super(
        ErrorCode.CAMPAIGN_BUDGET_EXCEEDED,
        "Campaign budget exceeded. Budget: " + budget + ", Spent: " + spent,
        campaignId,
        budget,
        spent);
  }
}
