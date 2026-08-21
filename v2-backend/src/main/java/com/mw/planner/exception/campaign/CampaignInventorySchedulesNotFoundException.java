package com.mw.planner.exception.campaign;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CampaignInventorySchedulesNotFoundException extends BaseException {

  public CampaignInventorySchedulesNotFoundException(String campaignId, String inventoryId) {
    super(
        ErrorCode.CAMPAIGN_INVENTORY_SCHEDULES_NOT_FOUND,
        "Campaign inventory schedules not found for campaignId: "
            + campaignId
            + ", inventoryId: "
            + inventoryId,
        campaignId,
        inventoryId);
  }
}
