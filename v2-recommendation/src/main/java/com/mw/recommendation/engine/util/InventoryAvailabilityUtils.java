package com.mw.recommendation.engine.util;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Shared helpers for inventory availability filtering. Centralises the inclusive campaign-duration
 * computation so the browse path and the recommendation submission path stay in lockstep and cannot
 * drift.
 */
public final class InventoryAvailabilityUtils {

  private InventoryAvailabilityUtils() {}

  /**
   * Inclusive campaign duration in days (Jan 1 -> Jan 3 = 3). Returns {@code null} when either date
   * is missing, in which case the minDays availability filter must not be applied. An inverted
   * range (endDate before startDate) is clamped to 1 rather than producing a negative duration.
   *
   * @param startDate campaign start date (nullable)
   * @param endDate campaign end date (nullable)
   * @return inclusive duration in days, or {@code null} if either date is missing
   */
  public static Long inclusiveDurationDays(LocalDate startDate, LocalDate endDate) {
    if (startDate == null || endDate == null) {
      return null;
    }
    return Math.max(1L, ChronoUnit.DAYS.between(startDate, endDate) + 1);
  }

  /**
   * Lead-time gap between today (per the supplied {@link Clock}) and the campaign start date. This
   * is a gap, not an inclusive duration, so there is NO {@code +1} (today=Jun 15, start=Jun 16 ->
   * 1). Returns {@code null} when {@code startDate} is missing, in which case the leadDays
   * eligibility filter must not be applied. May be negative when the start date is in the past; the
   * eligibility criteria handles that lenient case explicitly.
   *
   * @param clock clock used to resolve "today" (injected so tests are deterministic)
   * @param startDate campaign start date (nullable)
   * @return available lead days, or {@code null} if {@code startDate} is missing
   */
  public static Long availableLeadDays(Clock clock, LocalDate startDate) {
    if (startDate == null) {
      return null;
    }
    return ChronoUnit.DAYS.between(LocalDate.now(clock), startDate);
  }
}
