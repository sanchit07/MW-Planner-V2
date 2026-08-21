package com.mw.planner.exception.creative;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

/** Rule 4 (campaign status gate) — only Reviewing/Approved/Active campaigns accept bindings. */
public class CreativeCampaignStatusIneligibleException extends BaseException {

  public CreativeCampaignStatusIneligibleException(String campaignId, String status) {
    super(
        ErrorCode.CREATIVE_CAMPAIGN_STATUS_INELIGIBLE,
        "Campaign " + campaignId + " is " + status + " and cannot accept creative bindings",
        campaignId,
        status);
  }
}
