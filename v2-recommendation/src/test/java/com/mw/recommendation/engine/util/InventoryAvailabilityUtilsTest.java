package com.mw.recommendation.engine.util;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Verifies the shared inclusive-duration computation used by browse and recommendation paths. */
@DisplayName("InventoryAvailabilityUtils.inclusiveDurationDays")
class InventoryAvailabilityUtilsTest {

  /** Fixed clock pinned to 2026-06-15 UTC so "today" is deterministic. */
  private static final Clock FIXED_TODAY =
      Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC);

  @Test
  @DisplayName("Jan 1 -> Jan 3 inclusive = 3")
  void inclusiveThreeDays() {
    assertEquals(
        3L,
        InventoryAvailabilityUtils.inclusiveDurationDays(
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 3)));
  }

  @Test
  @DisplayName("same day = 1")
  void sameDayOne() {
    assertEquals(
        1L,
        InventoryAvailabilityUtils.inclusiveDurationDays(
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1)));
  }

  @Test
  @DisplayName("startDate null = null")
  void startNull() {
    assertNull(InventoryAvailabilityUtils.inclusiveDurationDays(null, LocalDate.of(2025, 1, 3)));
  }

  @Test
  @DisplayName("endDate null = null")
  void endNull() {
    assertNull(InventoryAvailabilityUtils.inclusiveDurationDays(LocalDate.of(2025, 1, 1), null));
  }

  @Test
  @DisplayName("both null = null")
  void bothNull() {
    assertNull(InventoryAvailabilityUtils.inclusiveDurationDays(null, null));
  }

  @Test
  @DisplayName("inverted range clamps to 1")
  void invertedClampsToOne() {
    assertEquals(
        1L,
        InventoryAvailabilityUtils.inclusiveDurationDays(
            LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 3)));
  }

  @Nested
  @DisplayName("availableLeadDays(clock, startDate)")
  class AvailableLeadDays {

    @Test
    @DisplayName("today=Jun 15, start=Jun 16 → 1 (gap, NO +1)")
    void nextDayGapIsOne() {
      assertEquals(
          1L, InventoryAvailabilityUtils.availableLeadDays(FIXED_TODAY, LocalDate.of(2026, 6, 16)));
    }

    @Test
    @DisplayName("start == today → 0")
    void sameDayZero() {
      assertEquals(
          0L, InventoryAvailabilityUtils.availableLeadDays(FIXED_TODAY, LocalDate.of(2026, 6, 15)));
    }

    @Test
    @DisplayName("start five days out → 5")
    void fiveDaysOut() {
      assertEquals(
          5L, InventoryAvailabilityUtils.availableLeadDays(FIXED_TODAY, LocalDate.of(2026, 6, 20)));
    }

    @Test
    @DisplayName("start in the past → negative (no clamp)")
    void pastStartNegative() {
      assertEquals(
          -5L,
          InventoryAvailabilityUtils.availableLeadDays(FIXED_TODAY, LocalDate.of(2026, 6, 10)));
    }

    @Test
    @DisplayName("startDate null → null (filter not applied)")
    void startNull() {
      assertNull(InventoryAvailabilityUtils.availableLeadDays(FIXED_TODAY, null));
    }
  }
}
