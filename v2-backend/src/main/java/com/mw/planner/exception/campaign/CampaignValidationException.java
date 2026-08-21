package com.mw.planner.exception.campaign;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class CampaignValidationException extends BaseException {

  public CampaignValidationException(String fieldName, String invalidValue) {
    super(
        ErrorCode.CAMPAIGN_VALIDATION_FAILED,
        "Invalid campaign data: " + fieldName + " = " + invalidValue,
        fieldName,
        invalidValue);
  }

  public CampaignValidationException(String fieldName, String invalidValue, Throwable cause) {
    super(
        ErrorCode.CAMPAIGN_VALIDATION_FAILED,
        "Invalid campaign data: " + fieldName + " = " + invalidValue,
        cause,
        fieldName,
        invalidValue);
  }

  public CampaignValidationException(String fieldName) {
    super(ErrorCode.CAMPAIGN_INVENTORY_VALIDATION_FAILED, fieldName);
  }

  public CampaignValidationException(ErrorCode errorCode, Object... args) {
    super(errorCode, errorCode.getMessageKey(), args);
  }
}
