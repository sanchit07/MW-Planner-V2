package com.mw.planner.exception.campaign;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;
import java.util.List;

/** Exception thrown when schedule IDs do not belong to the specified campaign */
public class ScheduleIdsNotBelongToCampaignException extends BaseException {

  public ScheduleIdsNotBelongToCampaignException(
      String campaignId, List<String> invalidScheduleIds) {
    super(
        ErrorCode.SCHEDULE_IDS_NOT_BELONG_TO_CAMPAIGN,
        "The following schedule IDs do not belong to campaign "
            + campaignId
            + ": "
            + invalidScheduleIds,
        campaignId,
        invalidScheduleIds);
  }

  public ScheduleIdsNotBelongToCampaignException(String campaignId, String scheduleId) {
    super(
        ErrorCode.SCHEDULE_IDS_NOT_BELONG_TO_CAMPAIGN,
        "Schedule ID " + scheduleId + " does not belong to campaign " + campaignId,
        campaignId,
        scheduleId);
  }
}
