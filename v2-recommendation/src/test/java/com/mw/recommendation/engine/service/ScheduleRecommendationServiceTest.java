package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.domain.RecommendationRun;
import com.mw.recommendation.engine.domain.RunScheduleRecommendation;
import com.mw.recommendation.engine.domain.SelectionMode;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.dto.ScheduleRecommendationResponseDTO;
import com.mw.recommendation.engine.exception.BaseException;
import com.mw.recommendation.engine.repository.BookingRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
import com.mw.recommendation.engine.repository.RecommendationResultRepository;
import com.mw.recommendation.engine.repository.RecommendationRunRepository;
import com.mw.recommendation.engine.repository.RunScheduleRecommendationRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleRecommendationServiceTest {

  @Mock private InventoryRepository inventoryRepository;
  @Mock private BookingRepository bookingRepository;
  @Mock private RunScheduleRecommendationRepository runScheduleRecommendationRepository;
  @Mock private RecommendationRunRepository recommendationRunRepository;
  @Mock private RecommendationResultRepository recommendationResultRepository;
  @Mock private MeasureApiClient measureApiClient;

  @InjectMocks private ScheduleRecommendationService service;

  private static final String RUN_ID = "run-001";
  private static final String CAMPAIGN_ID = "campaign-001";
  private static final LocalDate START_DATE = LocalDate.of(2025, 1, 1);
  private static final LocalDate END_DATE = LocalDate.of(2025, 1, 31);

  private RecommendationRun completedRun;

  @BeforeEach
  void setUp() {
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setStartDate(START_DATE);
    request.setEndDate(END_DATE);
    request.setGoal(RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);

    completedRun = new RecommendationRun();
    completedRun.setRunId(RUN_ID);
    completedRun.setCampaignId(CAMPAIGN_ID);
    completedRun.setStatus(RecommendationRun.RunStatus.COMPLETED);
    completedRun.setRequest(request);
  }

  // ---- selectInventoriesAndGetSchedules ----

  @Test
  void select_runNotFound_throwsException() {
    when(recommendationRunRepository.findByRunId("no-such-run")).thenReturn(Optional.empty());

    assertThrows(
        BaseException.class,
        () -> service.selectInventoriesAndGetSchedules("no-such-run", List.of("inv-1")));
  }

  @Test
  void select_runInProgress_throwsException() {
    RecommendationRun inProgressRun = new RecommendationRun();
    inProgressRun.setRunId(RUN_ID);
    inProgressRun.setStatus(RecommendationRun.RunStatus.IN_PROGRESS);
    when(recommendationRunRepository.findByRunId(RUN_ID)).thenReturn(Optional.of(inProgressRun));

    assertThrows(
        BaseException.class,
        () -> service.selectInventoriesAndGetSchedules(RUN_ID, List.of("inv-1")));
  }

  @Test
  void select_existingSchedules_returnedWithoutRegeneration() {
    when(recommendationRunRepository.findByRunId(RUN_ID)).thenReturn(Optional.of(completedRun));

    RunScheduleRecommendation existingSchedule =
        RunScheduleRecommendation.builder()
            .runId(RUN_ID)
            .inventoryId("inv-1")
            .scheduleId("sched-1")
            .scheduleStartDate(START_DATE)
            .scheduleEndDate(END_DATE)
            .bookingMatrix(Map.of("2025-01-01", List.of(9, 10, 11)))
            .adPlays(100L)
            .basePrice(500.0)
            .currency("USD")
            .build();

    when(runScheduleRecommendationRepository.findByRunIdAndInventoryIdIn(RUN_ID, List.of("inv-1")))
        .thenReturn(List.of(existingSchedule));

    Inventory inv = new Inventory();
    inv.setInventoryId("inv-1");
    inv.setReferenceId("REF-1");
    inv.setName("Test Screen");
    when(inventoryRepository.findById("inv-1")).thenReturn(Optional.of(inv));

    when(recommendationResultRepository.findByRunIdAndInventoryIdIn(RUN_ID, List.of("inv-1")))
        .thenReturn(new ArrayList<>());

    ScheduleRecommendationResponseDTO response =
        service.selectInventoriesAndGetSchedules(RUN_ID, List.of("inv-1"));

    assertNotNull(response);
    assertEquals(1, response.getSchedules().size());
    assertEquals("inv-1", response.getSchedules().get(0).getInventoryId());

    verify(runScheduleRecommendationRepository, never()).saveAll(anyList());
  }

  @Test
  void select_existingSchedules_sellingTermSurvivesFromPersistedEntity() {
    // selectInventoriesAndGetSchedules is a separate response shape
    // (ScheduleRecommendationResponseDTO) fed by the same RunScheduleRecommendation entity as
    // getSchedulesByRunId — must carry sellingTerm through this path too.
    when(recommendationRunRepository.findByRunId(RUN_ID)).thenReturn(Optional.of(completedRun));

    Inventory.SellingTerm sellingTerm = Inventory.SellingTerm.builder().minDays(3).build();
    RunScheduleRecommendation existingSchedule =
        RunScheduleRecommendation.builder()
            .runId(RUN_ID)
            .inventoryId("inv-1")
            .scheduleId("sched-1")
            .scheduleStartDate(START_DATE)
            .scheduleEndDate(END_DATE)
            .bookingMatrix(Map.of("2025-01-01", List.of(9, 10, 11)))
            .sellingTerm(sellingTerm)
            .build();
    when(runScheduleRecommendationRepository.findByRunIdAndInventoryIdIn(RUN_ID, List.of("inv-1")))
        .thenReturn(List.of(existingSchedule));

    Inventory inv = new Inventory();
    inv.setInventoryId("inv-1");
    when(inventoryRepository.findById("inv-1")).thenReturn(Optional.of(inv));
    when(recommendationResultRepository.findByRunIdAndInventoryIdIn(RUN_ID, List.of("inv-1")))
        .thenReturn(new ArrayList<>());

    ScheduleRecommendationResponseDTO response =
        service.selectInventoriesAndGetSchedules(RUN_ID, List.of("inv-1"));

    assertEquals(
        sellingTerm,
        response.getSchedules().get(0).getRecommendedSchedules().get(0).getSellingTerm());
  }

  @Test
  void select_newInventory_persistsSellingTermOntoNewEntity() {
    // Distinct from select_existingSchedules_sellingTermSurvivesFromPersistedEntity: this
    // exercises the OTHER branch of selectInventoriesAndGetSchedules — an inventoryId with no
    // existing RunScheduleRecommendation, which generates and persists a brand-new entity. The
    // immediate response is built from the in-memory ScheduleSummaryDTO either way, but the
    // persisted copy must also carry sellingTerm or a later read (GET by run id, or re-selecting
    // the same inventory) would silently see it as null.
    when(recommendationRunRepository.findByRunId(RUN_ID)).thenReturn(Optional.of(completedRun));
    when(runScheduleRecommendationRepository.findByRunIdAndInventoryIdIn(RUN_ID, List.of("inv-1")))
        .thenReturn(List.of());

    Inventory inv = buildDigitalInventory("02:00:00", "14:00:00", 15, 8, 120);
    inv.setInventoryId("inv-1");
    Inventory.SellingTerm sellingTerm = Inventory.SellingTerm.builder().minDays(4).build();
    inv.setSellingTerm(sellingTerm);
    when(inventoryRepository.findByInventoryIdIn(any())).thenReturn(List.of(inv));
    when(bookingRepository.findByInventoryIdInAndDateRange(any(), any(), any()))
        .thenReturn(List.of());
    when(recommendationResultRepository.findByRunIdAndInventoryIdIn(RUN_ID, List.of("inv-1")))
        .thenReturn(new ArrayList<>());

    service.selectInventoriesAndGetSchedules(RUN_ID, List.of("inv-1"));

    var captor = org.mockito.ArgumentCaptor.forClass(List.class);
    verify(runScheduleRecommendationRepository).saveAll(captor.capture());
    @SuppressWarnings("unchecked")
    List<RunScheduleRecommendation> saved = (List<RunScheduleRecommendation>) captor.getValue();
    assertEquals(1, saved.size());
    assertEquals(sellingTerm, saved.get(0).getSellingTerm());
  }

  @Test
  void select_marksResultsAsManual() {
    when(recommendationRunRepository.findByRunId(RUN_ID)).thenReturn(Optional.of(completedRun));

    when(runScheduleRecommendationRepository.findByRunIdAndInventoryIdIn(eq(RUN_ID), anyList()))
        .thenReturn(
            List.of(
                RunScheduleRecommendation.builder()
                    .runId(RUN_ID)
                    .inventoryId("inv-1")
                    .scheduleId("sched-1")
                    .scheduleStartDate(START_DATE)
                    .scheduleEndDate(END_DATE)
                    .bookingMatrix(Map.of("2025-01-01", List.of(9)))
                    .build()));

    when(inventoryRepository.findById("inv-1")).thenReturn(Optional.of(new Inventory()));

    RecommendationResult result = new RecommendationResult();
    result.setRunId(RUN_ID);
    result.setInventoryId("inv-1");
    result.setSelectionMode(null);
    when(recommendationResultRepository.findByRunIdAndInventoryIdIn(eq(RUN_ID), anyList()))
        .thenReturn(List.of(result));
    when(recommendationResultRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

    service.selectInventoriesAndGetSchedules(RUN_ID, List.of("inv-1"));

    assertEquals(SelectionMode.MANUAL, result.getSelectionMode());
    verify(recommendationResultRepository).saveAll(anyList());
  }

  // ---- deselectInventories ----

  @Test
  void deselect_deletesSchedulesAndClearsSelectionMode() {
    RecommendationResult result = new RecommendationResult();
    result.setRunId(RUN_ID);
    result.setInventoryId("inv-1");
    result.setSelectionMode(SelectionMode.MANUAL);

    when(recommendationResultRepository.findByRunIdAndInventoryIdIn(RUN_ID, List.of("inv-1")))
        .thenReturn(List.of(result));
    when(recommendationResultRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

    service.deselectInventories(RUN_ID, List.of("inv-1"));

    verify(runScheduleRecommendationRepository)
        .deleteByRunIdAndInventoryIdIn(RUN_ID, List.of("inv-1"));
    assertNull(result.getSelectionMode());
    verify(recommendationResultRepository).saveAll(anyList());
  }

  @Test
  void deselect_noMatchingResults_doesNotCallSaveAll() {
    when(recommendationResultRepository.findByRunIdAndInventoryIdIn(RUN_ID, List.of("inv-999")))
        .thenReturn(List.of());

    service.deselectInventories(RUN_ID, List.of("inv-999"));

    verify(runScheduleRecommendationRepository)
        .deleteByRunIdAndInventoryIdIn(RUN_ID, List.of("inv-999"));
    verify(recommendationResultRepository, never()).saveAll(anyList());
  }

  @Test
  void deselect_multipleInventories_clearsAllSelectionModes() {
    RecommendationResult r1 = new RecommendationResult();
    r1.setRunId(RUN_ID);
    r1.setInventoryId("inv-1");
    r1.setSelectionMode(SelectionMode.AUTO);

    RecommendationResult r2 = new RecommendationResult();
    r2.setRunId(RUN_ID);
    r2.setInventoryId("inv-2");
    r2.setSelectionMode(SelectionMode.MANUAL);

    List<String> ids = List.of("inv-1", "inv-2");
    when(recommendationResultRepository.findByRunIdAndInventoryIdIn(RUN_ID, ids))
        .thenReturn(List.of(r1, r2));
    when(recommendationResultRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

    service.deselectInventories(RUN_ID, ids);

    assertNull(r1.getSelectionMode());
    assertNull(r2.getSelectionMode());
    verify(runScheduleRecommendationRepository).deleteByRunIdAndInventoryIdIn(RUN_ID, ids);
  }

  // ---- getSchedulesByRunIdAndSelectionModeAuto ----

  @Test
  void getAutoSchedules_autoResultsExist_returnsOnlyMatchingSchedules() {
    RecommendationResult autoResult = new RecommendationResult();
    autoResult.setRunId(RUN_ID);
    autoResult.setInventoryId("inv-1");
    autoResult.setSelectionMode(SelectionMode.AUTO);
    when(recommendationResultRepository.findByRunIdAndSelectionMode(RUN_ID, SelectionMode.AUTO))
        .thenReturn(List.of(autoResult));

    RunScheduleRecommendation schedule =
        RunScheduleRecommendation.builder()
            .runId(RUN_ID)
            .inventoryId("inv-1")
            .scheduleId("sched-1")
            .scheduleStartDate(START_DATE)
            .scheduleEndDate(END_DATE)
            .build();
    when(runScheduleRecommendationRepository.findByRunIdAndInventoryIdIn(RUN_ID, List.of("inv-1")))
        .thenReturn(List.of(schedule));

    var response = service.getSchedulesByRunIdAndSelectionModeAuto(RUN_ID);

    assertNotNull(response);
    assertEquals(RUN_ID, response.getRunId());
    assertEquals(1, response.getSchedules().size());
    assertEquals("inv-1", response.getSchedules().get(0).getInventoryId());
    verify(runScheduleRecommendationRepository)
        .findByRunIdAndInventoryIdIn(RUN_ID, List.of("inv-1"));
  }

  @Test
  void getAutoSchedules_sellingTermOnPersistedEntity_survivesIntoResponseDTO() {
    // Covers the GET-by-run-id read path specifically: sellingTerm must come back out of the
    // persisted RunScheduleRecommendation entity, not just the in-memory build path covered by
    // buildScheduleSummary_carriesInventorySellingTermOntoTheSchedule.
    RecommendationResult autoResult = new RecommendationResult();
    autoResult.setRunId(RUN_ID);
    autoResult.setInventoryId("inv-1");
    autoResult.setSelectionMode(SelectionMode.AUTO);
    when(recommendationResultRepository.findByRunIdAndSelectionMode(RUN_ID, SelectionMode.AUTO))
        .thenReturn(List.of(autoResult));

    Inventory.SellingTerm sellingTerm = Inventory.SellingTerm.builder().minDays(5).build();
    RunScheduleRecommendation schedule =
        RunScheduleRecommendation.builder()
            .runId(RUN_ID)
            .inventoryId("inv-1")
            .scheduleId("sched-1")
            .scheduleStartDate(START_DATE)
            .scheduleEndDate(END_DATE)
            .sellingTerm(sellingTerm)
            .build();
    when(runScheduleRecommendationRepository.findByRunIdAndInventoryIdIn(RUN_ID, List.of("inv-1")))
        .thenReturn(List.of(schedule));

    var response = service.getSchedulesByRunIdAndSelectionModeAuto(RUN_ID);

    assertEquals(sellingTerm, response.getSchedules().get(0).getSellingTerm());
  }

  @Test
  void getAutoSchedules_noAutoResults_returnsEmptyAndSkipsInQuery() {
    when(recommendationResultRepository.findByRunIdAndSelectionMode(RUN_ID, SelectionMode.AUTO))
        .thenReturn(List.of());

    var response = service.getSchedulesByRunIdAndSelectionModeAuto(RUN_ID);

    assertNotNull(response);
    assertEquals(RUN_ID, response.getRunId());
    assertTrue(response.getSchedules().isEmpty());
    verify(runScheduleRecommendationRepository, never())
        .findByRunIdAndInventoryIdIn(anyString(), anyList());
  }

  @Test
  void getAutoSchedules_duplicateInventoryIds_dedupedBeforeInQuery() {
    RecommendationResult r1 = new RecommendationResult();
    r1.setRunId(RUN_ID);
    r1.setInventoryId("inv-1");
    r1.setSelectionMode(SelectionMode.AUTO);
    RecommendationResult r2 = new RecommendationResult();
    r2.setRunId(RUN_ID);
    r2.setInventoryId("inv-1");
    r2.setSelectionMode(SelectionMode.AUTO);
    when(recommendationResultRepository.findByRunIdAndSelectionMode(RUN_ID, SelectionMode.AUTO))
        .thenReturn(List.of(r1, r2));
    when(runScheduleRecommendationRepository.findByRunIdAndInventoryIdIn(RUN_ID, List.of("inv-1")))
        .thenReturn(List.of());

    service.getSchedulesByRunIdAndSelectionModeAuto(RUN_ID);

    verify(runScheduleRecommendationRepository)
        .findByRunIdAndInventoryIdIn(RUN_ID, List.of("inv-1"));
  }

  // ---- buildScheduleSummaryForInventory: adPlays (inclusive hour counting) ----

  private Inventory buildDigitalInventory(
      String start, String end, int spotDuration, int spotsPerLoop, int loopDuration) {
    Inventory inv = new Inventory();
    inv.setInventoryId("inv-test");
    inv.setClassification("Digital");
    Inventory.DigitalFields df =
        Inventory.DigitalFields.builder()
            .spotDuration(spotDuration)
            .spotsPerLoop(spotsPerLoop)
            .loopDuration(loopDuration)
            .loopsPerHour(3600 / loopDuration)
            .build();
    inv.setDigitalFields(df);
    Inventory.OperatingTime ot = new Inventory.OperatingTime();
    ot.setStart(start);
    ot.setEnd(end);
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> times =
        new java.util.EnumMap<>(Inventory.Weekday.class);
    for (Inventory.Weekday day : Inventory.Weekday.values()) {
      times.put(day, List.of(ot));
    }
    inv.setOperatingTimes(times);
    Inventory.PriceModel price = new Inventory.PriceModel();
    price.setSpot(0.23);
    price.setCpm(35.0);
    price.setCurrency("MYR");
    inv.setPrices(List.of(price));
    return inv;
  }

  @Test
  void buildScheduleSummary_inclusiveHourCount_nonExactBoundary() {
    // 07:30→23:00: startH=7, endH=23 → 17 slots/day, 31 days, loopsPerHour=8
    // adPlays = 8 × 31 × 17 = 4216
    Inventory inv = buildDigitalInventory("07:30:00", "23:00:00", 15, 30, 450);
    LocalDate start = LocalDate.of(2025, 5, 1);
    LocalDate end = LocalDate.of(2025, 5, 31);

    var dto = service.buildScheduleSummaryForInventory(inv, List.of(), start, end, null, null);

    assertNotNull(dto);
    assertEquals(4216L, dto.getAdPlays());
  }

  @Test
  void buildScheduleSummary_inclusiveHourCount_exactBoundary() {
    // 02:00→14:00: startH=2, endH=14 → 13 slots/day, 31 days, loopsPerHour=30
    // adPlays = 30 × 31 × 13 = 12090
    Inventory inv = buildDigitalInventory("02:00:00", "14:00:00", 15, 8, 120);
    LocalDate start = LocalDate.of(2025, 5, 1);
    LocalDate end = LocalDate.of(2025, 5, 31);

    var dto = service.buildScheduleSummaryForInventory(inv, List.of(), start, end, null, null);

    assertNotNull(dto);
    assertEquals(12090L, dto.getAdPlays());
  }

  @Test
  void buildScheduleSummary_carriesInventorySellingTermOntoTheSchedule() {
    // The Optimization step's schedule data didn't expose sellingTerm/minDays even though
    // getMinDays() already reads it internally to size Round 1 schedules — this asserts the gap
    // is closed: whatever sellingTerm the inventory carries comes back out on the schedule.
    Inventory inv = buildDigitalInventory("02:00:00", "14:00:00", 15, 8, 120);
    Inventory.SellingTerm sellingTerm =
        Inventory.SellingTerm.builder().leadDays(2).minDays(7).minHours(4).build();
    inv.setSellingTerm(sellingTerm);
    LocalDate start = LocalDate.of(2025, 5, 1);
    LocalDate end = LocalDate.of(2025, 5, 31);

    var dto = service.buildScheduleSummaryForInventory(inv, List.of(), start, end, null, null);

    assertNotNull(dto);
    assertEquals(sellingTerm, dto.getSellingTerm());
    assertEquals(7, dto.getSellingTerm().getMinDays());
  }

  @Test
  void buildScheduleSummary_withNoSellingTermOnInventory_leavesItNull() {
    Inventory inv = buildDigitalInventory("02:00:00", "14:00:00", 15, 8, 120);
    LocalDate start = LocalDate.of(2025, 5, 1);
    LocalDate end = LocalDate.of(2025, 5, 31);

    var dto = service.buildScheduleSummaryForInventory(inv, List.of(), start, end, null, null);

    assertNotNull(dto);
    assertNull(dto.getSellingTerm());
  }

  // ---- calculateScheduleBasePrice: goal-based pricing ----

  @Test
  void buildScheduleSummary_sovGoal_usesSpotPricing() {
    // SOV goal → spot × adPlays; spot=0.23, adPlays=12090 → basePrice=2780.70
    Inventory inv = buildDigitalInventory("02:00:00", "14:00:00", 15, 8, 120);
    LocalDate start = LocalDate.of(2025, 5, 1);
    LocalDate end = LocalDate.of(2025, 5, 31);

    var dto =
        service.buildScheduleSummaryForInventory(
            inv, List.of(), start, end, null, RecommendationRequestDTO.CampaignGoal.SOV);

    assertNotNull(dto);
    assertEquals(2780.70, dto.getBasePrice(), 0.01);
  }

  @Test
  void buildScheduleSummary_impressionsGoal_fallsBackToSpotWhenNoImpressions() {
    // IMPRESSIONS goal without Measure API impressions → spot × adPlays fallback
    // spot=0.23, adPlays=12090 → basePrice=2780.70
    // CPM × impressions pricing applies only after enrichSchedulesWithReachAndFrequency enrichment
    Inventory inv = buildDigitalInventory("02:00:00", "14:00:00", 15, 8, 120);
    inv.setReferenceId("REF-TEST");
    LocalDate start = LocalDate.of(2025, 5, 1);
    LocalDate end = LocalDate.of(2025, 5, 31);

    var dto =
        service.buildScheduleSummaryForInventory(
            inv, List.of(), start, end, null, RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);

    assertNotNull(dto);
    assertEquals(2780.70, dto.getBasePrice(), 0.01);
  }

  @Test
  void buildScheduleSummary_nullGoal_preEnrichment_fallsBackToSpot() {
    // Build-time (no Measure impressions yet): a null goal has no CPM×impressions to compute, so it
    // falls back to spot × adPlays (spot=0.23, adPlays=12090 → 2780.70). CPM pricing for a null
    // goal
    // only applies after enrichment populates impressions (see nullGoal_enriched test below).
    Inventory inv = buildDigitalInventory("02:00:00", "14:00:00", 15, 8, 120);
    LocalDate start = LocalDate.of(2025, 5, 1);
    LocalDate end = LocalDate.of(2025, 5, 31);

    var dto = service.buildScheduleSummaryForInventory(inv, List.of(), start, end, null, null);

    assertNotNull(dto);
    assertEquals(2780.70, dto.getBasePrice(), 0.01);
  }

  @Test
  void buildScheduleSummaries_nullGoal_enriched_usesCpmPricing() {
    // A null goal is treated as IMPRESSIONS: after Measure enrichment supplies impressions, the
    // schedule is priced by CPM × impressions (cpm=35, impressions=100000 → 35/1000 × 100000 =
    // 3500)
    // — NOT the full-run spot buyout (0.23 × 12090 = 2780.70 would have been the pre-enrichment
    // value). This is what makes budget-only (no-goal) auto-selection affordable.
    Inventory inv = buildDigitalInventory("02:00:00", "14:00:00", 15, 8, 120);
    inv.setReferenceId("REF-nullgoal");
    LocalDate start = LocalDate.of(2025, 5, 1);
    LocalDate end = LocalDate.of(2025, 5, 31);

    when(bookingRepository.findByInventoryIdInAndDateRange(anyList(), eq(start), eq(end)))
        .thenReturn(List.of());
    when(measureApiClient.getReachAndFrequencyBySites(any(), eq(true)))
        .thenReturn(
            List.of(
                com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyResponseDTO.builder()
                    .referenceId("REF-nullgoal")
                    .status("success")
                    .impressions(100000L)
                    .reach(50000L)
                    .build()));

    Map<String, com.mw.recommendation.engine.dto.ScheduleSummaryDTO> out =
        service.buildScheduleSummariesForInventories(List.of(inv), start, end, null, null);

    com.mw.recommendation.engine.dto.ScheduleSummaryDTO schedule = out.get(inv.getInventoryId());
    assertNotNull(schedule);
    assertEquals(100000L, schedule.getEstimatedImpressions());
    assertEquals(3500.0, schedule.getBasePrice(), 0.01);
  }

  // ---- buildBestScheduleForBudgetCap (Round 3) ----

  @Test
  void buildBestScheduleForBudgetCap_nullInventory_returnsEmpty() {
    Optional<?> result =
        service.buildBestScheduleForBudgetCap(
            null,
            START_DATE,
            END_DATE,
            BigDecimal.valueOf(1000),
            RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);
    assertTrue(result.isEmpty());
  }

  @Test
  void buildBestScheduleForBudgetCap_zeroBudgetCap_returnsEmpty() {
    Inventory inv = new Inventory();
    inv.setInventoryId("inv-1");
    Optional<?> result =
        service.buildBestScheduleForBudgetCap(
            inv,
            START_DATE,
            END_DATE,
            BigDecimal.ZERO,
            RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);
    assertTrue(result.isEmpty());
  }
}
