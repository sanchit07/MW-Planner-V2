package com.mw.planner.exception.campaign;

import com.mw.planner.domain.Campaign;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CampaignInvalidStatusException extends BaseException {

  public CampaignInvalidStatusException(
      Campaign.Status currentStatus, Campaign.Status requiredStatus) {
    super(
        ErrorCode.CAMPAIGN_INVALID_STATUS,
        "Campaign status is "
            + currentStatus.name()
            + " but required status is "
            + requiredStatus.name(),
        currentStatus.name(),
        requiredStatus.name());
  }

  public CampaignInvalidStatusException(Campaign.Status currentStatus, String requiredStatus) {
    super(
        ErrorCode.CAMPAIGN_INVALID_STATUS,
        "Campaign status is " + currentStatus.name() + " but required status is " + requiredStatus,
        currentStatus.name(),
        requiredStatus);
  }
}
