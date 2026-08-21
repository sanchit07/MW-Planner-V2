package com.mw.planner.exception.campaign;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CampaignNotFoundException extends BaseException {
  public CampaignNotFoundException(String campaignId) {
    super(
        ErrorCode.CAMPAIGN_NOT_FOUND, "Campaign not found with ID/name: " + campaignId, campaignId);
  }
}
