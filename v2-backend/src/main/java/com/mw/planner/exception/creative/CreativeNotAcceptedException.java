package com.mw.planner.exception.creative;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

/** Tier 1 gate — only a creative in ACCEPTED status may be bound to a line item. */
public class CreativeNotAcceptedException extends BaseException {

  public CreativeNotAcceptedException(String creativeId, String tier1Status) {
    super(
        ErrorCode.CREATIVE_NOT_ACCEPTED,
        "Creative " + creativeId + " is " + tier1Status + " and cannot be assigned",
        creativeId,
        tier1Status);
  }
}
