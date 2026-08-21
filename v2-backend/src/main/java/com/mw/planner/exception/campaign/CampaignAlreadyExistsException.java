package com.mw.planner.exception.campaign;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CampaignAlreadyExistsException extends BaseException {
  public CampaignAlreadyExistsException(String campaignName) {
    super(
        ErrorCode.CAMPAIGN_ALREADY_EXISTS,
        "Campaign already exists: " + campaignName,
        campaignName);
  }
}
