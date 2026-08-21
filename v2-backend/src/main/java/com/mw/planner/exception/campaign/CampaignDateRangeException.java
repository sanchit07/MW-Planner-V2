package com.mw.planner.exception.campaign;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;
import java.time.LocalDate;

public class CampaignDateRangeException extends BaseException {

  public CampaignDateRangeException(LocalDate startDate, LocalDate endDate) {
    super(
        ErrorCode.CAMPAIGN_INVALID_DATE_RANGE,
        "Invalid date range: start date " + startDate + " cannot be after end date " + endDate,
        startDate,
        endDate);
  }
}
