package com.mw.planner.exception.campaign;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CampaignAgencyNotValidException extends BaseException {

  public CampaignAgencyNotValidException() {
    super(
        ErrorCode.CAMPAIGN_AGENCY_ID_NOT_VALID,
        "Valid Agency ID is required when client type is AGENCY");
  }
}
