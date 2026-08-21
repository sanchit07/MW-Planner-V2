package com.mw.planner.exception.campaign;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CampaignInvalidGoalTypeException extends BaseException {

  public CampaignInvalidGoalTypeException() {
    super(
        ErrorCode.CAMPAIGN_INVALID_GOAL_TYPE,
        "Invalid goal type. Valid values are: IMPRESSIONS, REACH, SOV, ATTRIBUTION, OTHER, ADPLAYS");
  }
}
