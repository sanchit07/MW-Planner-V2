package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;

import com.mw.recommendation.engine.dto.ScheduleSummaryDTO;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for hasValidMeasureData() validation method in RecommendationAsyncService. Tests
 * verify that inventories without valid Measure API data (impressions and reach) are correctly
 * identified for exclusion from budget-aware auto-selection.
 */
class RecommendationAsyncServiceMeasureDataValidationTest {

  private RecommendationAsyncService service;
  private Method hasValidMeasureDataMethod;

  @BeforeEach
  void setUp() throws Exception {
    // Create a minimal instance just for testing the validation method
    // Constructor params: InventoryRepository, AudienceRepository, ScoringService,
    // RecommendationRunRepository, RecommendationResultRepository,
    // ScheduleRecommendationService, VirtualThreadTaskExecutor, MongoTemplate, MeasureApiClient,
    // Clock, AutoSelectionReasonResolver
    service =
        new RecommendationAsyncService(
            null, null, null, null, null, null, null, null, null, null, null);

    // Use reflection to access the private validation method
    hasValidMeasureDataMethod =
        RecommendationAsyncService.class.getDeclaredMethod(
            "hasValidMeasureData", ScheduleSummaryDTO.class);
    hasValidMeasureDataMethod.setAccessible(true);
  }

  private boolean callHasValidMeasureData(ScheduleSummaryDTO schedule) throws Exception {
    return (boolean) hasValidMeasureDataMethod.invoke(service, schedule);
  }

  @Test
  @DisplayName("Should return false when schedule is null")
  void testNullSchedule() throws Exception {
    assertFalse(callHasValidMeasureData(null));
  }

  @Test
  @DisplayName("Should return false when estimatedImpressions is null")
  void testNullImpressions() throws Exception {
    ScheduleSummaryDTO schedule =
        ScheduleSummaryDTO.builder()
            .scheduleId("test-1")
            .estimatedImpressions(null) // null
            .estimatedReach(10000L)
            .basePrice(100.0)
            .build();

    assertFalse(callHasValidMeasureData(schedule));
  }

  @Test
  @DisplayName("Should return false when estimatedImpressions is zero")
  void testZeroImpressions() throws Exception {
    ScheduleSummaryDTO schedule =
        ScheduleSummaryDTO.builder()
            .scheduleId("test-2")
            .estimatedImpressions(0L) // zero
            .estimatedReach(10000L)
            .basePrice(100.0)
            .build();

    assertFalse(callHasValidMeasureData(schedule));
  }

  @Test
  @DisplayName("Should return false when estimatedImpressions is negative")
  void testNegativeImpressions() throws Exception {
    ScheduleSummaryDTO schedule =
        ScheduleSummaryDTO.builder()
            .scheduleId("test-3")
            .estimatedImpressions(-100L) // negative
            .estimatedReach(10000L)
            .basePrice(100.0)
            .build();

    assertFalse(callHasValidMeasureData(schedule));
  }

  @Test
  @DisplayName("Should return false when estimatedReach is null")
  void testNullReach() throws Exception {
    ScheduleSummaryDTO schedule =
        ScheduleSummaryDTO.builder()
            .scheduleId("test-4")
            .estimatedImpressions(50000L)
            .estimatedReach(null) // null
            .basePrice(100.0)
            .build();

    assertFalse(callHasValidMeasureData(schedule));
  }

  @Test
  @DisplayName("Should return false when estimatedReach is zero")
  void testZeroReach() throws Exception {
    ScheduleSummaryDTO schedule =
        ScheduleSummaryDTO.builder()
            .scheduleId("test-5")
            .estimatedImpressions(50000L)
            .estimatedReach(0L) // zero
            .basePrice(100.0)
            .build();

    assertFalse(callHasValidMeasureData(schedule));
  }

  @Test
  @DisplayName("Should return false when estimatedReach is negative")
  void testNegativeReach() throws Exception {
    ScheduleSummaryDTO schedule =
        ScheduleSummaryDTO.builder()
            .scheduleId("test-6")
            .estimatedImpressions(50000L)
            .estimatedReach(-500L) // negative
            .basePrice(100.0)
            .build();

    assertFalse(callHasValidMeasureData(schedule));
  }

  @Test
  @DisplayName("Should return false when both impressions and reach are null")
  void testBothNull() throws Exception {
    ScheduleSummaryDTO schedule =
        ScheduleSummaryDTO.builder()
            .scheduleId("test-7")
            .estimatedImpressions(null)
            .estimatedReach(null)
            .basePrice(100.0)
            .build();

    assertFalse(callHasValidMeasureData(schedule));
  }

  @Test
  @DisplayName("Should return false when both impressions and reach are zero")
  void testBothZero() throws Exception {
    ScheduleSummaryDTO schedule =
        ScheduleSummaryDTO.builder()
            .scheduleId("test-8")
            .estimatedImpressions(0L)
            .estimatedReach(0L)
            .basePrice(100.0)
            .build();

    assertFalse(callHasValidMeasureData(schedule));
  }

  @Test
  @DisplayName("Should return true when both impressions and reach are valid (positive)")
  void testValidData() throws Exception {
    ScheduleSummaryDTO schedule =
        ScheduleSummaryDTO.builder()
            .scheduleId("test-9")
            .estimatedImpressions(50000L)
            .estimatedReach(10000L)
            .basePrice(100.0)
            .build();

    assertTrue(callHasValidMeasureData(schedule));
  }

  @Test
  @DisplayName(
      "Should return true when impressions and reach are minimal positive values (edge case)")
  void testMinimalValidData() throws Exception {
    ScheduleSummaryDTO schedule =
        ScheduleSummaryDTO.builder()
            .scheduleId("test-10")
            .estimatedImpressions(1L) // minimal valid
            .estimatedReach(1L) // minimal valid
            .basePrice(100.0)
            .build();

    assertTrue(callHasValidMeasureData(schedule));
  }

  @Test
  @DisplayName("Should return true when impressions and reach are very large values (stress test)")
  void testLargeValidData() throws Exception {
    ScheduleSummaryDTO schedule =
        ScheduleSummaryDTO.builder()
            .scheduleId("test-11")
            .estimatedImpressions(10_000_000_000L) // 10 billion
            .estimatedReach(1_000_000_000L) // 1 billion
            .basePrice(100.0)
            .build();

    assertTrue(callHasValidMeasureData(schedule));
  }

  @Test
  @DisplayName("Should return true regardless of other fields when impressions and reach are valid")
  void testValidDataWithCompleteSchedule() throws Exception {
    ScheduleSummaryDTO schedule =
        ScheduleSummaryDTO.builder()
            .scheduleId("test-12")
            .scheduleStartDate(LocalDate.of(2025, 1, 1))
            .scheduleEndDate(LocalDate.of(2025, 1, 31))
            .bookingMatrix(new LinkedHashMap<>())
            .adPlays(1000L)
            .plannedSot(24.0)
            .totalSot(744.0)
            .spotsPerLoop(1L)
            .spotsPerHour(10L)
            .duration(10L)
            .basePrice(500.0)
            .estimatedImpressions(50000L) // valid
            .estimatedReach(10000L) // valid
            .currency("USD")
            .build();

    assertTrue(callHasValidMeasureData(schedule));
  }

  @Test
  @DisplayName(
      "Should return false when schedule has all fields except valid impressions (reach valid)")
  void testCompleteScheduleWithInvalidImpressions() throws Exception {
    ScheduleSummaryDTO schedule =
        ScheduleSummaryDTO.builder()
            .scheduleId("test-13")
            .scheduleStartDate(LocalDate.of(2025, 1, 1))
            .scheduleEndDate(LocalDate.of(2025, 1, 31))
            .bookingMatrix(new LinkedHashMap<>())
            .adPlays(1000L)
            .basePrice(500.0)
            .estimatedImpressions(0L) // invalid
            .estimatedReach(10000L) // valid
            .currency("USD")
            .build();

    assertFalse(callHasValidMeasureData(schedule));
  }

  @Test
  @DisplayName(
      "Should return false when schedule has all fields except valid reach (impressions valid)")
  void testCompleteScheduleWithInvalidReach() throws Exception {
    ScheduleSummaryDTO schedule =
        ScheduleSummaryDTO.builder()
            .scheduleId("test-14")
            .scheduleStartDate(LocalDate.of(2025, 1, 1))
            .scheduleEndDate(LocalDate.of(2025, 1, 31))
            .bookingMatrix(new LinkedHashMap<>())
            .adPlays(1000L)
            .basePrice(500.0)
            .estimatedImpressions(50000L) // valid
            .estimatedReach(null) // invalid
            .currency("USD")
            .build();

    assertFalse(callHasValidMeasureData(schedule));
  }

  @Test
  @DisplayName("Should handle schedule with only Measure data fields populated")
  void testMinimalScheduleWithOnlyMeasureData() throws Exception {
    ScheduleSummaryDTO schedule =
        ScheduleSummaryDTO.builder().estimatedImpressions(50000L).estimatedReach(10000L).build();

    assertTrue(callHasValidMeasureData(schedule));
  }
}
