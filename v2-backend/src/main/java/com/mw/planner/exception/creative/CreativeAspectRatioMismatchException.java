package com.mw.planner.exception.creative;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

/** Rule 1 (aspect-ratio match) — overridable via the caller's explicit {@code forceMatch} flag. */
public class CreativeAspectRatioMismatchException extends BaseException {

  public CreativeAspectRatioMismatchException(
      String creativeAspectRatio, String inventoryAspectRatio) {
    super(
        ErrorCode.CREATIVE_ASPECT_RATIO_MISMATCH,
        "Creative aspect ratio "
            + creativeAspectRatio
            + " does not match inventory aspect ratio "
            + inventoryAspectRatio,
        creativeAspectRatio,
        inventoryAspectRatio);
  }
}
