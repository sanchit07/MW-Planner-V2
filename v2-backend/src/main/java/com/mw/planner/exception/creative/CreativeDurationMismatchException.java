package com.mw.planner.exception.creative;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

/** Rule 2 (duration match) — never overridable; the creative itself must be edited. */
public class CreativeDurationMismatchException extends BaseException {

  public CreativeDurationMismatchException(int creativeDurationSeconds, int requiredSlotSeconds) {
    super(
        ErrorCode.CREATIVE_DURATION_MISMATCH,
        "Creative duration "
            + creativeDurationSeconds
            + "s does not match the inventory's accepted slot length of "
            + requiredSlotSeconds
            + "s",
        creativeDurationSeconds,
        requiredSlotSeconds);
  }
}
