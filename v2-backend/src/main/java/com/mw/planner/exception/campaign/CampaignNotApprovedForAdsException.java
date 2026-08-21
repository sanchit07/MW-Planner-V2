package com.mw.planner.exception.campaign;

import com.mw.planner.domain.Campaign;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

/** Exception thrown when attempting to submit a campaign to ADS that is not approved */
public class CampaignNotApprovedForAdsException extends BaseException {

  public CampaignNotApprovedForAdsException(String campaignId, Campaign.Status currentStatus) {
    super(
        ErrorCode.CAMPAIGN_NOT_APPROVED_FOR_ADS,
        "Campaign with ID "
            + campaignId
            + " is not approved for ADS submission. Current status: "
            + currentStatus.name(),
        campaignId,
        currentStatus.name());
  }
}
