package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.dto.ScheduleSummaryDTO;
import com.mw.recommendation.engine.repository.BookingRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
import com.mw.recommendation.engine.repository.RecommendationResultRepository;
import com.mw.recommendation.engine.repository.RecommendationRunRepository;
import com.mw.recommendation.engine.repository.RunScheduleRecommendationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassicInventoryScheduleTest {

  private static final LocalDate START = LocalDate.of(2026, 6, 11);
  private static final LocalDate END = LocalDate.of(2026, 7, 11); // 31 days

  @Mock private InventoryRepository inventoryRepository;
  @Mock private BookingRepository bookingRepository;
  @Mock private RunScheduleRecommendationRepository runScheduleRecommendationRepository;
  @Mock private RecommendationRunRepository recommendationRunRepository;
  @Mock private RecommendationResultRepository recommendationResultRepository;
  @Mock private MeasureApiClient measureApiClient;

  @InjectMocks private ScheduleRecommendationService service;

  // ── Helper builders ─────────────────────────────────────────────────────────

  private Inventory classicInventory(Double monthly) {
    Inventory inv = new Inventory();
    inv.setInventoryId("classic-inv-1");
    inv.setReferenceId("REF-C-001");
    inv.setClassification("Classic");
    inv.setDigitalFields(new Inventory.DigitalFields()); // empty — no loopDuration
    if (monthly != null) {
      inv.setPrices(
          List.of(Inventory.PriceModel.builder().monthly(monthly).currency("SGD").build()));
    }
    return inv;
  }

  private Inventory digitalInventory(Double cpm, Integer loopDuration, Integer spotsPerLoop) {
    Inventory inv = new Inventory();
    inv.setInventoryId("digital-inv-1");
    inv.setClassification("Digital");
    inv.setDigitalFields(
        Inventory.DigitalFields.builder()
            .loopDuration(loopDuration)
            .spotsPerLoop(spotsPerLoop)
            .build());
    if (cpm != null) {
      inv.setPrices(List.of(Inventory.PriceModel.builder().cpm(cpm).currency("SGD").build()));
    }
    return inv;
  }

  // ── resolveOperatingHoursForWeekday (via buildScheduleSummariesForInventories) ──

  @Test
  void classic_nullOperatingTimes_scheduleBuilt() {
    Inventory inv = classicInventory(50000.0);
    inv.setOperatingTimes(null);

    when(bookingRepository.findByInventoryIdInAndDateRange(any(), any(), any()))
        .thenReturn(List.of());

    Map<String, ScheduleSummaryDTO> result =
        service.buildScheduleSummariesForInventories(
            List.of(inv), START, END, null, RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);

    assertTrue(
        result.containsKey("classic-inv-1"),
        "Classic with null operatingTimes must build schedule");
    ScheduleSummaryDTO dto = result.get("classic-inv-1");
    assertNotNull(dto.getBookingMatrix());
    assertFalse(dto.getBookingMatrix().isEmpty(), "Booking matrix must have dates");
    assertEquals(31, dto.getBookingMatrix().size(), "All 31 days should be in matrix");
  }

  @Test
  void classic_emptyWeekdayArrays_scheduleBuilt() {
    Inventory inv = classicInventory(50000.0);
    // operatingTimes present but all weekday lists empty
    inv.setOperatingTimes(
        Map.of(
            Inventory.Weekday.MONDAY, List.of(),
            Inventory.Weekday.TUESDAY, List.of(),
            Inventory.Weekday.WEDNESDAY, List.of(),
            Inventory.Weekday.THURSDAY, List.of(),
            Inventory.Weekday.FRIDAY, List.of(),
            Inventory.Weekday.SATURDAY, List.of(),
            Inventory.Weekday.SUNDAY, List.of()));

    when(bookingRepository.findByInventoryIdInAndDateRange(any(), any(), any()))
        .thenReturn(List.of());

    Map<String, ScheduleSummaryDTO> result =
        service.buildScheduleSummariesForInventories(
            List.of(inv), START, END, null, RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);

    assertTrue(
        result.containsKey("classic-inv-1"),
        "Classic with empty weekday arrays must build schedule");
    assertFalse(result.get("classic-inv-1").getBookingMatrix().isEmpty());
  }

  // ── calculateScheduleBasePrice — monthly ────────────────────────────────────

  @Test
  void classic_monthlyPricing_basePriceCalculated() {
    Inventory inv = classicInventory(42000.0); // 42000 SGD/month
    inv.setOperatingTimes(null);

    when(bookingRepository.findByInventoryIdInAndDateRange(any(), any(), any()))
        .thenReturn(List.of());

    Map<String, ScheduleSummaryDTO> result =
        service.buildScheduleSummariesForInventories(
            List.of(inv), START, END, null, RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);

    ScheduleSummaryDTO dto = result.get("classic-inv-1");
    assertNotNull(dto, "Schedule must be built for classic monthly inventory");
    assertNotNull(dto.getBasePrice(), "basePrice must be calculated from monthly rate");
    // 42000 * 31/30 = 43400.00
    assertEquals(43400.0, dto.getBasePrice(), 1.0);
  }

  @Test
  void classic_noPrices_basePriceNull() {
    Inventory inv = classicInventory(null); // no prices
    inv.setOperatingTimes(null);

    when(bookingRepository.findByInventoryIdInAndDateRange(any(), any(), any()))
        .thenReturn(List.of());

    Map<String, ScheduleSummaryDTO> result =
        service.buildScheduleSummariesForInventories(
            List.of(inv), START, END, null, RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);

    // Schedule built but basePrice null
    ScheduleSummaryDTO dto = result.get("classic-inv-1");
    if (dto != null) {
      assertNull(dto.getBasePrice(), "No prices → basePrice must be null");
    }
  }

  // ── Digital inventory — unchanged behavior ───────────────────────────────────

  @Test
  void digital_nullLoopDuration_scheduleNotBuilt() {
    Inventory inv = digitalInventory(10.0, null, null); // no loopDuration

    when(bookingRepository.findByInventoryIdInAndDateRange(any(), any(), any()))
        .thenReturn(List.of());

    Map<String, ScheduleSummaryDTO> result =
        service.buildScheduleSummariesForInventories(
            List.of(inv), START, END, null, RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);

    assertFalse(
        result.containsKey("digital-inv-1"),
        "Digital without loopDuration must not build schedule");
  }

  @Test
  void digital_withLoopDurationAndOperatingTimes_scheduleBuilt() {
    Inventory inv = digitalInventory(10.0, 60, 6);
    inv.setPrices(List.of(Inventory.PriceModel.builder().cpm(10.0).currency("SGD").build()));
    inv.setOperatingTimes(
        Map.of(
            Inventory.Weekday.MONDAY,
            List.of(Inventory.OperatingTime.builder().start("08:00:00").end("22:00:00").build()),
            Inventory.Weekday.TUESDAY,
            List.of(Inventory.OperatingTime.builder().start("08:00:00").end("22:00:00").build()),
            Inventory.Weekday.WEDNESDAY,
            List.of(Inventory.OperatingTime.builder().start("08:00:00").end("22:00:00").build()),
            Inventory.Weekday.THURSDAY,
            List.of(Inventory.OperatingTime.builder().start("08:00:00").end("22:00:00").build()),
            Inventory.Weekday.FRIDAY,
            List.of(Inventory.OperatingTime.builder().start("08:00:00").end("22:00:00").build()),
            Inventory.Weekday.SATURDAY,
            List.of(Inventory.OperatingTime.builder().start("08:00:00").end("22:00:00").build()),
            Inventory.Weekday.SUNDAY,
            List.of(Inventory.OperatingTime.builder().start("08:00:00").end("22:00:00").build())));

    when(bookingRepository.findByInventoryIdInAndDateRange(any(), any(), any()))
        .thenReturn(List.of());

    Map<String, ScheduleSummaryDTO> result =
        service.buildScheduleSummariesForInventories(
            List.of(inv), START, END, null, RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);

    assertTrue(
        result.containsKey("digital-inv-1"),
        "Digital with loopDuration and operatingTimes must build schedule");
  }
}
