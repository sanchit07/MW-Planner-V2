package com.mw.planner.exception.creative;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

/** Marking a creative Inadequate requires a specific reason (PRD §11 / Tier 1 approval). */
public class CreativeTier1ReasonRequiredException extends BaseException {

  public CreativeTier1ReasonRequiredException(String creativeId) {
    super(
        ErrorCode.CREATIVE_TIER1_REASON_REQUIRED,
        "A reason is required to mark creative " + creativeId + " as inadequate",
        creativeId);
  }
}
