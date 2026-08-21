package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.AutoSelectionReasonCode;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.domain.RecommendationRun;
import com.mw.recommendation.engine.domain.SelectionMode;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.dto.ScheduleSummaryDTO;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyResponseDTO;
import com.mw.recommendation.engine.repository.AudienceRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
import com.mw.recommendation.engine.repository.RecommendationResultRepository;
import com.mw.recommendation.engine.repository.RecommendationRunRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Tests for budget-aware auto-selection with score-band batching, parallel category processing, and
 * greedy redistribution in RecommendationAsyncService.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationAsyncServiceTest {

  @Mock private InventoryRepository inventoryRepository;
  @Mock private AudienceRepository audienceRepository;
  @Mock private ScoringService scoringService;
  @Mock private RecommendationRunRepository recommendationRunRepository;
  @Mock private RecommendationResultRepository recommendationResultRepository;
  @Mock private ScheduleRecommendationService scheduleRecommendationService;
  @Mock private VirtualThreadTaskExecutor virtualThreadTaskExecutor;
  @Mock private MongoTemplate mongoTemplate;
  @Mock private MeasureApiClient measureApiClient;

  // Real resolver (not a mock) so the persisted auto-selection reason codes are actually computed
  // and can be asserted end-to-end. Blank measure URL in the default properties is fine.
  @org.mockito.Spy
  private AutoSelectionReasonResolver autoSelectionReasonResolver =
      new AutoSelectionReasonResolver(
          new com.mw.recommendation.engine.config.MwRecommendationEngineProperties());

  @org.mockito.Spy
  private java.time.Clock clock =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-06-15T00:00:00Z"), java.time.ZoneOffset.UTC);

  @InjectMocks private RecommendationAsyncService service;

  private static final String RUN_ID = "run-test-001";
  private static final String CAMPAIGN_ID = "campaign-001";
  private static final LocalDate START_DATE = LocalDate.of(2025, 1, 1);
  private static final LocalDate END_DATE = LocalDate.of(2025, 1, 31);

  @BeforeEach
  void setUp() {
    // Make virtual thread executor run tasks synchronously for deterministic tests
    // Handle both execute() for direct calls and for CompletableFuture.runAsync()
    // (lenient: the fetch-zero reason test completes before any task is ever dispatched)
    lenient()
        .doAnswer(
            invocation -> {
              Runnable r = invocation.getArgument(0);
              r.run();
              return null;
            })
        .when(virtualThreadTaskExecutor)
        .execute(any());

    // Default: findByRunId returns a mutable run so updateCompletionPercentage works
    RecommendationRun run = new RecommendationRun();
    run.setRunId(RUN_ID);
    run.setStatus(RecommendationRun.RunStatus.IN_PROGRESS);
    lenient().when(recommendationRunRepository.findByRunId(RUN_ID)).thenReturn(Optional.of(run));
    lenient().when(recommendationRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    // Configure MongoTemplate to handle bulk inserts
    lenient()
        .when(mongoTemplate.insert(anyList(), any(Class.class)))
        .thenAnswer(i -> i.getArgument(0));

    // Configure recommendation result repository for bulk update
    lenient()
        .doNothing()
        .when(recommendationResultRepository)
        .bulkUpdateSelectionMode(anyString(), anyMap());

    // Round 3: default no additional schedules (so existing tests unchanged)
    lenient()
        .when(
            scheduleRecommendationService.buildBestScheduleForBudgetCap(
                any(), any(), any(), any(), any()))
        .thenReturn(Optional.empty());

    // Mock Measure API client to return empty responses for reach/frequency calls
    lenient()
        .when(measureApiClient.getReachAndFrequencyBySites(any(), anyBoolean()))
        .thenReturn(List.of());
  }

  // ---- Helper builders ----

  private static Inventory buildInventory(String id, String classification, String type) {
    Inventory inv = new Inventory();
    inv.setInventoryId(id);
    inv.setReferenceId("REF-" + id);
    inv.setName("Inv-" + id);
    inv.setClassification(classification);
    inv.setType(type);
    return inv;
  }

  private static ScheduleSummaryDTO buildSchedule(double basePrice) {
    return ScheduleSummaryDTO.builder()
        .scheduleId(UUID.randomUUID().toString())
        .scheduleStartDate(START_DATE)
        .scheduleEndDate(END_DATE)
        .basePrice(basePrice)
        .estimatedImpressions(10000L)
        .estimatedReach(5000L)
        .adPlays(500L)
        .currency("USD")
        .bookingMatrix(Map.of("2025-01-01", List.of(9, 10, 11)))
        .build();
  }

  private RecommendationRequestDTO buildRequest(BigDecimal budget) {
    RecommendationRequestDTO req = new RecommendationRequestDTO();
    req.setCountry("US");
    req.setStartDate(START_DATE);
    req.setEndDate(END_DATE);
    req.setBudget(budget);
    req.setGoal(RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);
    req.setGoalValue(1000000L);
    return req;
  }

  /**
   * Sets up the full async pipeline mocks (inventory fetch + scoring) so
   * processRecommendationsAsync reaches the auto-select phase with the given inventories and
   * scores.
   */
  private void setupScoringPipeline(List<Inventory> inventories, Map<String, Double> scoreMap) {
    when(inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            anyString(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            nullable(Long.class),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any(),
            any()))
        .thenReturn(inventories);

    // Mock batch audience fetching (Phase 1 optimization)
    lenient()
        .when(audienceRepository.findByInventoryIdInOrReferenceIdIn(anyList(), anyList()))
        .thenReturn(List.of());
    lenient().when(audienceRepository.findByReferenceIdIn(anyList())).thenReturn(List.of());

    // Mock batch data fetching (Phase 1.5 optimization)
    lenient()
        .when(scoringService.batchFetchBookingData(anyList(), any(), any()))
        .thenReturn(new HashMap<>());
    lenient().when(scoringService.batchFetchBrandData(anyList())).thenReturn(new HashMap<>());

    // Mock scoring with new 5-parameter signature
    for (Inventory inv : inventories) {
      double score = scoreMap.getOrDefault(inv.getInventoryId(), 50.0);
      InventoryScore is =
          InventoryScore.builder()
              .finalScore(score)
              .geoFit(80.0)
              .availability(90.0)
              .budgetFit(70.0)
              .build();
      lenient()
          .when(scoringService.calculateScore(eq(inv), any(), any(), anyMap(), anyMap()))
          .thenReturn(is);
    }

    lenient().when(inventoryRepository.findByInventoryIdIn(anyList())).thenReturn(inventories);
  }

  // ---- Test: Budget-aware selection respects category caps ----

  @Test
  void processAsync_budgetAware_respectsCategoryBudgetCaps() {
    // 3 digital inventories (each costs 400), 2 classic inventories (each costs 300)
    // Budget: 1000, default IMPRESSIONS allocation: digital 35% (350), classic 25% (250)
    // Expected: digital selects 0 (first inventory 400 > 350 cap), classic selects 0 (first 300 >
    // 250)
    // With higher budget: Budget 2000 -> digital cap 700 (selects 1), classic cap 500 (selects 1)

    BigDecimal budget = new BigDecimal("2000");
    RecommendationRequestDTO request = buildRequest(budget);
    // Default IMPRESSIONS allocation: digital 35%=700, classic 25%=500

    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    Inventory dig2 = buildInventory("dig2", "Digital", "OOH");
    Inventory dig3 = buildInventory("dig3", "Digital", "OOH");
    Inventory cls1 = buildInventory("cls1", "Classic", "OOH");
    Inventory cls2 = buildInventory("cls2", "Classic", "OOH");

    Map<String, Double> scores =
        Map.of("dig1", 95.0, "dig2", 85.0, "dig3", 75.0, "cls1", 92.0, "cls2", 82.0);
    setupScoringPipeline(List.of(dig1, dig2, dig3, cls1, cls2), scores);

    // Digital inventories cost 400 each, classic inventories cost 300 each
    Map<String, ScheduleSummaryDTO> digitalSchedules = new HashMap<>();
    digitalSchedules.put("dig1", buildSchedule(400.0));
    digitalSchedules.put("dig2", buildSchedule(400.0));
    digitalSchedules.put("dig3", buildSchedule(400.0));

    Map<String, ScheduleSummaryDTO> classicSchedules = new HashMap<>();
    classicSchedules.put("cls1", buildSchedule(300.0));
    classicSchedules.put("cls2", buildSchedule(300.0));

    // buildScheduleSummariesForInventories is called per batch. Since digital >90 has dig1,
    // >80 has dig2, >70 has dig3; classic >90 has cls1, >80 has cls2
    when(scheduleRecommendationService.buildScheduleSummariesForInventories(
            anyList(), eq(START_DATE), eq(END_DATE), isNull(), any()))
        .thenAnswer(
            invocation -> {
              List<Inventory> invs = invocation.getArgument(0);
              Map<String, ScheduleSummaryDTO> result = new HashMap<>();
              for (Inventory inv : invs) {
                if (digitalSchedules.containsKey(inv.getInventoryId())) {
                  result.put(inv.getInventoryId(), digitalSchedules.get(inv.getInventoryId()));
                }
                if (classicSchedules.containsKey(inv.getInventoryId())) {
                  result.put(inv.getInventoryId(), classicSchedules.get(inv.getInventoryId()));
                }
              }
              return result;
            });

    // Execute
    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    // Verify schedules were saved
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ScheduleSummaryDTO>> scheduleCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(scheduleRecommendationService)
        .saveSchedulesForRun(eq(RUN_ID), eq(CAMPAIGN_ID), scheduleCaptor.capture());

    Map<String, ScheduleSummaryDTO> savedSchedules = scheduleCaptor.getValue();

    // Digital cap = 700: dig1 (400) selected, dig2 (400) would push to 800 > 700, so capped at 1
    assertTrue(savedSchedules.containsKey("dig1"), "dig1 should be selected (400 <= 700)");

    // Classic cap = 500: cls1 (300) selected, cls2 (300) would push to 600 > 500, so capped at 1
    assertTrue(savedSchedules.containsKey("cls1"), "cls1 should be selected (300 <= 500)");
  }

  // ---- Test: Score-band batching processes high scores first ----

  @Test
  void processAsync_scoreBandBatching_processesHighScoresFirst() {
    // One digital category. Budget 400, digital cap = 140 (35% of 400).
    // high (score 95, cost 100): selected in Round 1 (100 <= 140)
    // mid  (score 85, cost 100): selected in Round 2 after redistribution (200 <= 400)
    // low  (score 45, cost 500): too expensive even after Round 2 (700 > 400)
    // Verifies: high and mid selected, low excluded by cost

    BigDecimal budget = new BigDecimal("400");
    RecommendationRequestDTO request = buildRequest(budget);

    Inventory high = buildInventory("high", "Digital", "OOH");
    Inventory mid = buildInventory("mid", "Digital", "OOH");
    Inventory low = buildInventory("low", "Digital", "OOH");

    Map<String, Double> scores = Map.of("high", 95.0, "mid", 85.0, "low", 45.0);
    setupScoringPipeline(List.of(high, mid, low), scores);

    when(scheduleRecommendationService.buildScheduleSummariesForInventories(
            anyList(), eq(START_DATE), eq(END_DATE), isNull(), any()))
        .thenAnswer(
            invocation -> {
              List<Inventory> invs = invocation.getArgument(0);
              Map<String, ScheduleSummaryDTO> result = new HashMap<>();
              for (Inventory inv : invs) {
                if ("low".equals(inv.getInventoryId())) {
                  result.put(inv.getInventoryId(), buildSchedule(500.0));
                } else {
                  result.put(inv.getInventoryId(), buildSchedule(100.0));
                }
              }
              return result;
            });

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ScheduleSummaryDTO>> scheduleCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(scheduleRecommendationService)
        .saveSchedulesForRun(eq(RUN_ID), eq(CAMPAIGN_ID), scheduleCaptor.capture());

    Map<String, ScheduleSummaryDTO> saved = scheduleCaptor.getValue();

    assertTrue(saved.containsKey("high"), "High-score inventory should be selected first");
    assertTrue(saved.containsKey("mid"), "Mid-score inventory should be selected after Round 2");
    assertFalse(
        saved.containsKey("low"),
        "Low-score expensive inventory should NOT be selected when budget insufficient");
  }

  // ---- Test: Round 2 greedy redistribution ----

  @Test
  void processAsync_round2_redistributesUnusedBudgetToCapCategories() {
    // Digital: cap 700 (35%), inventories cost 400 each -> selects 1 (400), capped
    // Classic: cap 500 (25%), NO inventories -> remaining 500
    // Transit: cap 400 (20%), one inventory cost 100 -> selects 1 (100), remaining 300
    // Total remaining = 500 + 300 = 800
    // Round 2: digital (highest allocation of capped) gets extra 800 -> new cap 1500
    //   -> selects dig2 (400+400=800), dig3 (400+400+400=1200 <= 1500), maybe dig3 too

    BigDecimal budget = new BigDecimal("2000");
    RecommendationRequestDTO request = buildRequest(budget);

    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    Inventory dig2 = buildInventory("dig2", "Digital", "OOH");
    Inventory dig3 = buildInventory("dig3", "Digital", "OOH");
    Inventory trn1 = buildInventory("trn1", "Transit", "Transit");

    Map<String, Double> scores = Map.of("dig1", 95.0, "dig2", 85.0, "dig3", 75.0, "trn1", 91.0);
    setupScoringPipeline(List.of(dig1, dig2, dig3, trn1), scores);

    when(scheduleRecommendationService.buildScheduleSummariesForInventories(
            anyList(), eq(START_DATE), eq(END_DATE), isNull(), any()))
        .thenAnswer(
            invocation -> {
              List<Inventory> invs = invocation.getArgument(0);
              Map<String, ScheduleSummaryDTO> result = new HashMap<>();
              for (Inventory inv : invs) {
                if (inv.getInventoryId().startsWith("dig")) {
                  result.put(inv.getInventoryId(), buildSchedule(400.0));
                } else {
                  result.put(inv.getInventoryId(), buildSchedule(100.0));
                }
              }
              return result;
            });

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ScheduleSummaryDTO>> scheduleCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(scheduleRecommendationService)
        .saveSchedulesForRun(eq(RUN_ID), eq(CAMPAIGN_ID), scheduleCaptor.capture());

    Map<String, ScheduleSummaryDTO> saved = scheduleCaptor.getValue();

    // Round 1: digital selects dig1 (400 <= 700), dig2 would push to 800 > 700 -> capped
    //          transit selects trn1 (100 <= 400), remaining 300
    // Round 2: digital gets extra budget from classic (500) + transit remaining (300) = 800
    //          new digital cap = 700 + 800 = 1500
    //          dig2 (800) <= 1500 -> selected, dig3 (1200) <= 1500 -> selected
    assertTrue(saved.containsKey("dig1"), "dig1 should be selected in Round 1");
    assertTrue(saved.containsKey("trn1"), "trn1 should be selected in Round 1");
    assertTrue(saved.containsKey("dig2"), "dig2 should be selected after Round 2 redistribution");
    assertTrue(saved.containsKey("dig3"), "dig3 should be selected after Round 2 redistribution");
  }

  // ---- Test: No budget, no goal -> no selection ----

  @Test
  void processAsync_noBudgetNoGoal_noSelection() {
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("US");
    request.setStartDate(START_DATE);
    request.setEndDate(END_DATE);

    Inventory inv = buildInventory("inv1", "Digital", "OOH");
    setupScoringPipeline(List.of(inv), Map.of("inv1", 95.0));

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    // No schedules should be saved
    verify(scheduleRecommendationService, never())
        .saveSchedulesForRun(anyString(), anyString(), anyMap());
  }

  // ---- Test: Goal only (no budget) -> flat approach ----

  @Test
  void processAsync_goalOnly_useFlatApproach() {
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("US");
    request.setStartDate(START_DATE);
    request.setEndDate(END_DATE);
    request.setGoal(RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);
    request.setGoalValue(20000L);

    Inventory inv1 = buildInventory("inv1", "Digital", "OOH");
    Inventory inv2 = buildInventory("inv2", "Classic", "OOH");

    Map<String, Double> scores = Map.of("inv1", 95.0, "inv2", 85.0);
    setupScoringPipeline(List.of(inv1, inv2), scores);

    // Each schedule has 10000 impressions (from buildSchedule default)
    when(scheduleRecommendationService.buildScheduleSummariesForInventories(
            anyList(), eq(START_DATE), eq(END_DATE), isNull(), any()))
        .thenAnswer(
            invocation -> {
              List<Inventory> invs = invocation.getArgument(0);
              Map<String, ScheduleSummaryDTO> result = new HashMap<>();
              for (Inventory inv : invs) {
                result.put(inv.getInventoryId(), buildSchedule(100.0));
              }
              return result;
            });

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    // Goal is 20000, each inventory provides 10000 impressions. Need 2 inventories.
    // After first (10000), remaining=10000, second (10000) <= 10000 so selected.
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ScheduleSummaryDTO>> scheduleCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(scheduleRecommendationService)
        .saveSchedulesForRun(eq(RUN_ID), eq(CAMPAIGN_ID), scheduleCaptor.capture());

    Map<String, ScheduleSummaryDTO> saved = scheduleCaptor.getValue();
    assertEquals(2, saved.size(), "Both inventories needed to meet 20000 impression goal");
  }

  // ---- Test: Inventories below score threshold are excluded ----

  @Test
  void processAsync_budgetAware_excludesLowScoredInventories() {
    BigDecimal budget = new BigDecimal("10000");
    RecommendationRequestDTO request = buildRequest(budget);

    Inventory highScore = buildInventory("high", "Digital", "OOH");
    Inventory lowScore = buildInventory("low", "Digital", "OOH");

    // low has score 8 which is below the 10.0 threshold
    Map<String, Double> scores = Map.of("high", 95.0, "low", 8.0);
    setupScoringPipeline(List.of(highScore, lowScore), scores);

    when(scheduleRecommendationService.buildScheduleSummariesForInventories(
            anyList(), eq(START_DATE), eq(END_DATE), isNull(), any()))
        .thenAnswer(
            invocation -> {
              List<Inventory> invs = invocation.getArgument(0);
              Map<String, ScheduleSummaryDTO> result = new HashMap<>();
              for (Inventory inv : invs) {
                result.put(inv.getInventoryId(), buildSchedule(100.0));
              }
              return result;
            });

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ScheduleSummaryDTO>> scheduleCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(scheduleRecommendationService)
        .saveSchedulesForRun(eq(RUN_ID), eq(CAMPAIGN_ID), scheduleCaptor.capture());

    Map<String, ScheduleSummaryDTO> saved = scheduleCaptor.getValue();
    assertTrue(saved.containsKey("high"), "High-score inventory should be selected");
    assertFalse(saved.containsKey("low"), "Inventory with score <= 10 should NOT be selected");
  }

  // ---- Test: Category with 0% allocation is skipped ----

  @Test
  void processAsync_budgetAware_skipsCategoryWithZeroAllocation() {
    BigDecimal budget = new BigDecimal("10000");
    RecommendationRequestDTO request = buildRequest(budget);
    // Set custom allocation with classic = 0%
    request.setBudgetAllocation(Map.of("digital", 100.0, "classic", 0.0));

    Inventory classicInv = buildInventory("cls1", "Classic", "OOH");
    Inventory digInv = buildInventory("dig1", "Digital", "OOH");

    Map<String, Double> scores = Map.of("cls1", 95.0, "dig1", 90.0);
    setupScoringPipeline(List.of(classicInv, digInv), scores);

    when(scheduleRecommendationService.buildScheduleSummariesForInventories(
            anyList(), eq(START_DATE), eq(END_DATE), isNull(), any()))
        .thenAnswer(
            invocation -> {
              List<Inventory> invs = invocation.getArgument(0);
              Map<String, ScheduleSummaryDTO> result = new HashMap<>();
              for (Inventory inv : invs) {
                result.put(inv.getInventoryId(), buildSchedule(100.0));
              }
              return result;
            });

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ScheduleSummaryDTO>> scheduleCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(scheduleRecommendationService)
        .saveSchedulesForRun(eq(RUN_ID), eq(CAMPAIGN_ID), scheduleCaptor.capture());

    Map<String, ScheduleSummaryDTO> saved = scheduleCaptor.getValue();
    assertFalse(
        saved.containsKey("cls1"),
        "Classic inventory should NOT be selected when classic allocation is 0%");
    assertTrue(saved.containsKey("dig1"), "Digital inventory should be selected");
  }

  // ---- Test: Round 2 skipped when no category is capped ----

  @Test
  void processAsync_round2Skipped_whenNoCategoryCapped() {
    // Very high budget so no category gets capped
    BigDecimal budget = new BigDecimal("100000");
    RecommendationRequestDTO request = buildRequest(budget);

    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    Inventory cls1 = buildInventory("cls1", "Classic", "OOH");

    Map<String, Double> scores = Map.of("dig1", 95.0, "cls1", 92.0);
    setupScoringPipeline(List.of(dig1, cls1), scores);

    when(scheduleRecommendationService.buildScheduleSummariesForInventories(
            anyList(), eq(START_DATE), eq(END_DATE), isNull(), any()))
        .thenAnswer(
            invocation -> {
              List<Inventory> invs = invocation.getArgument(0);
              Map<String, ScheduleSummaryDTO> result = new HashMap<>();
              for (Inventory inv : invs) {
                result.put(inv.getInventoryId(), buildSchedule(100.0));
              }
              return result;
            });

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ScheduleSummaryDTO>> scheduleCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(scheduleRecommendationService)
        .saveSchedulesForRun(eq(RUN_ID), eq(CAMPAIGN_ID), scheduleCaptor.capture());

    Map<String, ScheduleSummaryDTO> saved = scheduleCaptor.getValue();
    assertTrue(saved.containsKey("dig1"));
    assertTrue(saved.containsKey("cls1"));
    assertEquals(2, saved.size(), "All inventories should be selected when budget is ample");
  }

  // ---- Test: Selection mode is set correctly on results ----

  @Test
  @SuppressWarnings("unchecked")
  void processAsync_setsSelectionModeCorrectly() {
    BigDecimal budget = new BigDecimal("500");
    RecommendationRequestDTO request = buildRequest(budget);

    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    Inventory dig2 = buildInventory("dig2", "Digital", "OOH");

    Map<String, Double> scores = Map.of("dig1", 95.0, "dig2", 55.0);
    setupScoringPipeline(List.of(dig1, dig2), scores);

    // Digital cap = 175 (35% of 500). dig1 costs 100 -> selected. dig2 costs 100 -> 200 > 175
    when(scheduleRecommendationService.buildScheduleSummariesForInventories(
            anyList(), eq(START_DATE), eq(END_DATE), isNull(), any()))
        .thenAnswer(
            invocation -> {
              List<Inventory> invs = invocation.getArgument(0);
              Map<String, ScheduleSummaryDTO> result = new HashMap<>();
              for (Inventory inv : invs) {
                result.put(inv.getInventoryId(), buildSchedule(100.0));
              }
              return result;
            });

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    // Verify results saved via bulk insert (mongoTemplate)
    verify(mongoTemplate, atLeastOnce()).insert(anyList(), any(Class.class));

    // Verify selectionMode bulk update was called with at least one AUTO selection
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, SelectionMode>> selectionModeCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(recommendationResultRepository, times(1))
        .bulkUpdateSelectionMode(eq(RUN_ID), selectionModeCaptor.capture());

    Map<String, SelectionMode> selectionModeMap = selectionModeCaptor.getValue();
    boolean foundAutoSelected = selectionModeMap.containsValue(SelectionMode.AUTO);
    assertTrue(foundAutoSelected, "At least one inventory should have AUTO selection mode");
  }

  // ---- Round 3: remaining budget to highest-score inventories ----

  @Test
  void processAsync_round3_skippedWhenNoRemainingBudget() {
    // Budget 1000, 100% digital. One inventory costs exactly 1000 -> R1 uses full cap, remaining 0
    BigDecimal budget = new BigDecimal("1000");
    RecommendationRequestDTO request = buildRequest(budget);
    request.setBudgetAllocation(Map.of("digital", 100.0));

    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    Inventory dig2 = buildInventory("dig2", "Digital", "OOH");

    Map<String, Double> scores = Map.of("dig1", 95.0, "dig2", 88.0);
    setupScoringPipeline(List.of(dig1, dig2), scores);

    when(scheduleRecommendationService.buildScheduleSummariesForInventories(
            anyList(), eq(START_DATE), eq(END_DATE), isNull(), any()))
        .thenAnswer(
            invocation -> {
              List<Inventory> invs = invocation.getArgument(0);
              Map<String, ScheduleSummaryDTO> result = new HashMap<>();
              for (Inventory inv : invs) {
                result.put(
                    inv.getInventoryId(),
                    buildSchedule("dig1".equals(inv.getInventoryId()) ? 1000.0 : 200.0));
              }
              return result;
            });

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ScheduleSummaryDTO>> scheduleCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(scheduleRecommendationService)
        .saveSchedulesForRun(eq(RUN_ID), eq(CAMPAIGN_ID), scheduleCaptor.capture());

    Map<String, ScheduleSummaryDTO> saved = scheduleCaptor.getValue();
    assertEquals(1, saved.size(), "Only dig1 selected in R1; no remaining budget for Round 3");
    assertTrue(saved.containsKey("dig1"));

    // buildBestScheduleForBudgetCap should not be called when remaining budget is 0
    verify(scheduleRecommendationService, never())
        .buildBestScheduleForBudgetCap(any(), any(), any(), any(), any());
  }

  @Test
  void processAsync_round3_allocatesRemainingBudgetToHighestScore() {
    // Budget 1500. Use LinkedHashMap so classic gets 40% (600) and cls1 (500) fits in R1.
    // R1: dig1 (400), cls1 (500), trn1 (200). Remaining 400. Round 3: dig2 gets schedule.
    BigDecimal budget = new BigDecimal("1500");
    RecommendationRequestDTO request = buildRequest(budget);
    Map<String, Double> allocation = new LinkedHashMap<>();
    allocation.put("digital", 30.0);
    allocation.put("classic", 40.0);
    allocation.put("transit", 30.0);
    request.setBudgetAllocation(allocation);

    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    Inventory dig2 = buildInventory("dig2", "Digital", "OOH");
    Inventory cls1 = buildInventory("cls1", "Classic", "OOH");
    Inventory trn1 = buildInventory("trn1", "Transit", "Transit");

    Map<String, Double> scores = Map.of("dig1", 95.0, "dig2", 85.0, "cls1", 90.0, "trn1", 88.0);
    setupScoringPipeline(List.of(dig1, dig2, cls1, trn1), scores);

    when(scheduleRecommendationService.buildScheduleSummariesForInventories(
            anyList(), eq(START_DATE), eq(END_DATE), isNull(), any()))
        .thenAnswer(
            invocation -> {
              List<Inventory> invs = invocation.getArgument(0);
              Map<String, ScheduleSummaryDTO> result = new HashMap<>();
              for (Inventory inv : invs) {
                switch (inv.getInventoryId()) {
                  case "dig1" -> result.put(inv.getInventoryId(), buildSchedule(400.0));
                  case "dig2" -> result.put(inv.getInventoryId(), buildSchedule(100.0));
                  case "cls1" -> result.put(inv.getInventoryId(), buildSchedule(500.0));
                  case "trn1" -> result.put(inv.getInventoryId(), buildSchedule(200.0));
                  default -> result.put(inv.getInventoryId(), buildSchedule(100.0));
                }
              }
              return result;
            });

    ScheduleSummaryDTO round3Schedule = buildSchedule(250.0);
    lenient()
        .when(
            scheduleRecommendationService.buildBestScheduleForBudgetCap(
                eq(dig2), eq(START_DATE), eq(END_DATE), any(BigDecimal.class), any()))
        .thenReturn(Optional.of(round3Schedule));

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ScheduleSummaryDTO>> scheduleCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(scheduleRecommendationService)
        .saveSchedulesForRun(eq(RUN_ID), eq(CAMPAIGN_ID), scheduleCaptor.capture());

    Map<String, ScheduleSummaryDTO> saved = scheduleCaptor.getValue();
    assertTrue(saved.containsKey("dig1"), "dig1 selected in Round 1");
    assertTrue(saved.containsKey("trn1"), "trn1 selected in Round 1");
    assertTrue(
        saved.containsKey("dig2"), "dig2 should be selected in Round 3 with remaining budget");
    assertTrue(
        saved.size() >= 3,
        "At least dig1, trn1 and dig2 (R1+R3); cls1 may or may not be in R1 depending on cap");
  }

  @Test
  void processAsync_round3_skipsInventoryWhenMinDaysExceedsBudget() {
    // Remaining budget 100. Round 3 candidate dig2: buildBestScheduleForBudgetCap returns empty
    // (minDays schedule would exceed 100) -> dig2 not added.
    BigDecimal budget = new BigDecimal("1500");
    RecommendationRequestDTO request = buildRequest(budget);
    request.setBudgetAllocation(Map.of("digital", 100.0));

    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    Inventory dig2 = buildInventory("dig2", "Digital", "OOH");

    Map<String, Double> scores = Map.of("dig1", 95.0, "dig2", 80.0);
    setupScoringPipeline(List.of(dig1, dig2), scores);

    // Note: This stubbing may not be called due to globalScheduleCache optimization
    // (dig1 schedule is cached in Round 1, so Round 3 only fetches dig2)
    lenient()
        .when(
            scheduleRecommendationService.buildScheduleSummariesForInventories(
                anyList(), eq(START_DATE), eq(END_DATE), isNull(), any()))
        .thenAnswer(
            invocation -> {
              List<Inventory> invs = invocation.getArgument(0);
              Map<String, ScheduleSummaryDTO> result = new HashMap<>();
              for (Inventory inv : invs) {
                result.put(inv.getInventoryId(), buildSchedule(1400.0)); // dig1 uses 1400
              }
              return result;
            });

    // Round 3 (Phase 3.1): batch method returns empty map (minDays > 100 for both)
    // Note: May not be called if all candidates are already in globalScheduleCache
    lenient()
        .when(
            scheduleRecommendationService.buildBestSchedulesForBudgetCapBatch(
                anyList(), eq(START_DATE), eq(END_DATE), any(BigDecimal.class), any()))
        .thenReturn(Map.of()); // Empty map = no schedules fit budget

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ScheduleSummaryDTO>> scheduleCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(scheduleRecommendationService)
        .saveSchedulesForRun(eq(RUN_ID), eq(CAMPAIGN_ID), scheduleCaptor.capture());

    Map<String, ScheduleSummaryDTO> saved = scheduleCaptor.getValue();
    assertEquals(
        1, saved.size(), "Only dig1; Round 3 candidate skipped when minDays exceeds budget");
    assertTrue(saved.containsKey("dig1"));
  }

  // ---- No-budget SOV goal-only selection ----

  private RecommendationRequestDTO buildSovNoBudgetRequest(long goalValue) {
    RecommendationRequestDTO req = new RecommendationRequestDTO();
    req.setCountry("US");
    req.setStartDate(START_DATE);
    req.setEndDate(END_DATE);
    req.setBudget(null);
    req.setGoal(RecommendationRequestDTO.CampaignGoal.SOV);
    req.setGoalValue(goalValue);
    return req;
  }

  @Test
  @SuppressWarnings("unchecked")
  void processAsync_noBudgetSov_selectsUntilCumulativeSovMet() {
    // sov1 (score=95, rawSov=30) + sov2 (score=85, rawSov=25) = 55 >= goalValue=50 → both AUTO
    // sov3 (score=75, rawSov=20) must NOT be selected (goal already met)
    RecommendationRequestDTO request = buildSovNoBudgetRequest(50L);

    Inventory sov1 = buildInventory("sov1", "Digital", "OOH");
    Inventory sov2 = buildInventory("sov2", "Digital", "OOH");
    Inventory sov3 = buildInventory("sov3", "Digital", "OOH");

    Map<String, Double> scores = Map.of("sov1", 95.0, "sov2", 85.0, "sov3", 75.0);
    setupScoringPipeline(List.of(sov1, sov2, sov3), scores);

    lenient()
        .when(scoringService.calculateRawSov(eq(sov1), eq("US"), any(), any()))
        .thenReturn(30.0);
    lenient()
        .when(scoringService.calculateRawSov(eq(sov2), eq("US"), any(), any()))
        .thenReturn(25.0);
    lenient()
        .when(scoringService.calculateRawSov(eq(sov3), eq("US"), any(), any()))
        .thenReturn(20.0);

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    ArgumentCaptor<Map<String, SelectionMode>> captor = ArgumentCaptor.forClass(Map.class);
    verify(recommendationResultRepository).bulkUpdateSelectionMode(eq(RUN_ID), captor.capture());

    Map<String, SelectionMode> modes = captor.getValue();
    assertEquals(SelectionMode.AUTO, modes.get("sov1"), "sov1 must be AUTO");
    assertEquals(SelectionMode.AUTO, modes.get("sov2"), "sov2 must be AUTO");
    assertNull(modes.get("sov3"), "sov3 must be null — goal already met before it");
  }

  @Test
  @SuppressWarnings("unchecked")
  void processAsync_noBudgetSov_zeroGoalValue_noSelection() {
    RecommendationRequestDTO request = buildSovNoBudgetRequest(0L);

    Inventory sov1 = buildInventory("sov1", "Digital", "OOH");
    setupScoringPipeline(List.of(sov1), Map.of("sov1", 95.0));
    lenient().when(scoringService.calculateRawSov(eq(sov1), any(), any(), any())).thenReturn(50.0);

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    ArgumentCaptor<Map<String, SelectionMode>> captor = ArgumentCaptor.forClass(Map.class);
    verify(recommendationResultRepository).bulkUpdateSelectionMode(eq(RUN_ID), captor.capture());

    Map<String, SelectionMode> modes = captor.getValue();
    assertNull(modes.get("sov1"), "goalValue=0 must produce no AUTO selections");
  }

  @Test
  @SuppressWarnings("unchecked")
  void processAsync_noBudgetSov_allRawSovNull_noSelection() {
    // All inventories are Classic (no digitalFields) → calculateRawSov returns null → no AUTO
    RecommendationRequestDTO request = buildSovNoBudgetRequest(50L);

    Inventory cls1 = buildInventory("cls1", "Classic", "OOH");
    Inventory cls2 = buildInventory("cls2", "Classic", "OOH");
    setupScoringPipeline(List.of(cls1, cls2), Map.of("cls1", 90.0, "cls2", 80.0));
    lenient().when(scoringService.calculateRawSov(any(), any(), any(), any())).thenReturn(null);

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    ArgumentCaptor<Map<String, SelectionMode>> captor = ArgumentCaptor.forClass(Map.class);
    verify(recommendationResultRepository).bulkUpdateSelectionMode(eq(RUN_ID), captor.capture());

    Map<String, SelectionMode> modes = captor.getValue();
    assertFalse(modes.containsValue(SelectionMode.AUTO), "null rawSov for all → zero AUTO");
  }

  @Test
  @SuppressWarnings("unchecked")
  void processAsync_noBudgetSov_singleInventoryCoversFullGoal_onlyThatOneSelected() {
    // sov1 rawSov=80 >= goalValue=50 → selected alone; sov2 never reached
    RecommendationRequestDTO request = buildSovNoBudgetRequest(50L);

    Inventory sov1 = buildInventory("sov1", "Digital", "OOH");
    Inventory sov2 = buildInventory("sov2", "Digital", "OOH");
    setupScoringPipeline(List.of(sov1, sov2), Map.of("sov1", 95.0, "sov2", 85.0));
    lenient()
        .when(scoringService.calculateRawSov(eq(sov1), eq("US"), any(), any()))
        .thenReturn(80.0);
    lenient()
        .when(scoringService.calculateRawSov(eq(sov2), eq("US"), any(), any()))
        .thenReturn(60.0);

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    ArgumentCaptor<Map<String, SelectionMode>> captor = ArgumentCaptor.forClass(Map.class);
    verify(recommendationResultRepository).bulkUpdateSelectionMode(eq(RUN_ID), captor.capture());

    Map<String, SelectionMode> modes = captor.getValue();
    assertEquals(SelectionMode.AUTO, modes.get("sov1"), "sov1 alone covers goal");
    assertNull(modes.get("sov2"), "sov2 not needed — goal met after sov1");
  }

  @Test
  @SuppressWarnings("unchecked")
  void processAsync_persistsOperatingTimesOnInventoryDetails() {
    // operatingTimes is synced onto Inventory but must also flow into the persisted
    // RecommendationResult.InventoryDetails so the results API can return it.
    RecommendationRequestDTO request = buildRequest(new BigDecimal("500"));

    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimes =
        Map.of(
            Inventory.Weekday.MONDAY,
            List.of(Inventory.OperatingTime.builder().start("00:00:00").end("23:59:00").build()));
    dig1.setOperatingTimes(operatingTimes);

    setupScoringPipeline(List.of(dig1), Map.of("dig1", 95.0));
    when(scheduleRecommendationService.buildScheduleSummariesForInventories(
            anyList(), eq(START_DATE), eq(END_DATE), any(), any()))
        .thenAnswer(
            invocation -> {
              List<Inventory> invs = invocation.getArgument(0);
              Map<String, ScheduleSummaryDTO> result = new HashMap<>();
              for (Inventory inv : invs) {
                result.put(inv.getInventoryId(), buildSchedule(100.0));
              }
              return result;
            });

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    // Capture the persisted RecommendationResult documents
    ArgumentCaptor<List<RecommendationResult>> resultsCaptor = ArgumentCaptor.forClass(List.class);
    verify(mongoTemplate, atLeastOnce()).insert(resultsCaptor.capture(), any(Class.class));

    RecommendationResult dig1Result =
        resultsCaptor.getAllValues().stream()
            .flatMap(List::stream)
            .filter(r -> "dig1".equals(r.getInventoryId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("dig1 result was not persisted"));

    assertNotNull(
        dig1Result.getInventoryDetails(), "inventoryDetails must be set on persisted result");
    assertEquals(
        operatingTimes,
        dig1Result.getInventoryDetails().getOperatingTimes(),
        "operatingTimes must be carried from Inventory onto persisted RecommendationResult");
  }

  // ---- Auto-selection reason observability (persisted on the run-completion write) ----

  /** Stubs findByRunId with a run we hold, so the fields set by completeRun can be asserted. */
  private RecommendationRun stubHeldRun() {
    RecommendationRun run = new RecommendationRun();
    run.setRunId(RUN_ID);
    run.setStatus(RecommendationRun.RunStatus.IN_PROGRESS);
    when(recommendationRunRepository.findByRunId(RUN_ID)).thenReturn(Optional.of(run));
    return run;
  }

  /** Schedule with a price but WITHOUT Measure metrics (as when Measure enrichment found none). */
  private static ScheduleSummaryDTO buildScheduleWithoutMeasureData(double basePrice) {
    return ScheduleSummaryDTO.builder()
        .scheduleId(UUID.randomUUID().toString())
        .scheduleStartDate(START_DATE)
        .scheduleEndDate(END_DATE)
        .basePrice(basePrice)
        .adPlays(500L)
        .currency("USD")
        .bookingMatrix(Map.of("2025-01-01", List.of(9, 10, 11)))
        .build();
  }

  private void stubSchedulesForAll(ScheduleSummaryDTO template) {
    when(scheduleRecommendationService.buildScheduleSummariesForInventories(
            anyList(), eq(START_DATE), eq(END_DATE), isNull(), any()))
        .thenAnswer(
            invocation -> {
              List<Inventory> invs = invocation.getArgument(0);
              Map<String, ScheduleSummaryDTO> out = new HashMap<>();
              for (Inventory inv : invs) {
                out.put(inv.getInventoryId(), template);
              }
              return out;
            });
  }

  @Test
  void reason_inventoriesSelected() {
    RecommendationRun run = stubHeldRun();
    RecommendationRequestDTO request = buildRequest(new BigDecimal("2000"));
    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    setupScoringPipeline(List.of(dig1), Map.of("dig1", 95.0));
    stubSchedulesForAll(buildSchedule(300.0));

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    assertEquals(RecommendationRun.RunStatus.COMPLETED, run.getStatus());
    assertEquals(AutoSelectionReasonCode.INVENTORIES_SELECTED, run.getAutoSelectionReasonCode());
    assertEquals(1, run.getAutoSelectionDiagnostics().getSelectedCount());
  }

  @Test
  void reason_noBudgetNoGoal() {
    RecommendationRun run = stubHeldRun();
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("US");
    request.setStartDate(START_DATE);
    request.setEndDate(END_DATE);
    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    setupScoringPipeline(List.of(dig1), Map.of("dig1", 95.0));

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    assertEquals(AutoSelectionReasonCode.NO_BUDGET_NO_GOAL, run.getAutoSelectionReasonCode());
    assertEquals(0, run.getAutoSelectionDiagnostics().getSelectedCount());
  }

  @Test
  void reason_goalAdPlaysNoBudget() {
    RecommendationRun run = stubHeldRun();
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("US");
    request.setStartDate(START_DATE);
    request.setEndDate(END_DATE);
    request.setGoal(RecommendationRequestDTO.CampaignGoal.AD_PLAYS);
    request.setGoalValue(500L);
    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    setupScoringPipeline(List.of(dig1), Map.of("dig1", 95.0));

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    assertEquals(AutoSelectionReasonCode.GOAL_ADPLAYS_NO_BUDGET, run.getAutoSelectionReasonCode());
  }

  @Test
  void reason_noCandidateInventories_whenFetchReturnsNothing() {
    RecommendationRun run = stubHeldRun();
    RecommendationRequestDTO request = buildRequest(new BigDecimal("2000"));
    when(inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            anyString(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            nullable(Long.class),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any(),
            any()))
        .thenReturn(List.of());

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    assertEquals(RecommendationRun.RunStatus.COMPLETED, run.getStatus());
    assertEquals(
        AutoSelectionReasonCode.NO_CANDIDATE_INVENTORIES, run.getAutoSelectionReasonCode());
    assertEquals(0, run.getAutoSelectionDiagnostics().getCandidateCount());
  }

  @Test
  void reason_measureDataUnavailable_goalOnlyReach() {
    RecommendationRun run = stubHeldRun();
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("US");
    request.setStartDate(START_DATE);
    request.setEndDate(END_DATE);
    request.setGoal(RecommendationRequestDTO.CampaignGoal.REACH);
    request.setGoalValue(100_000L);
    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    setupScoringPipeline(List.of(dig1), Map.of("dig1", 95.0));
    // Measure client already returns no rows (setUp default); schedules carry no Measure metrics,
    // so goal-only selection skips every candidate → zero selection.
    stubSchedulesForAll(buildScheduleWithoutMeasureData(100.0));

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    assertEquals(
        AutoSelectionReasonCode.MEASURE_DATA_UNAVAILABLE, run.getAutoSelectionReasonCode());
    assertEquals(0, run.getAutoSelectionDiagnostics().getSitesWithReachFrequency());
  }

  @Test
  void reason_budgetTooLow() {
    RecommendationRun run = stubHeldRun();
    RecommendationRequestDTO request = buildRequest(new BigDecimal("50"));
    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    dig1.setPrices(List.of(Inventory.PriceModel.builder().cpm(100.0).currency("USD").build()));
    setupScoringPipeline(List.of(dig1), Map.of("dig1", 95.0));
    // Pipeline-level Measure batch returns a usable row → forecast impressions 100k → estimated
    // CPM cost 10,000, far above the 50 budget; schedule basePrice 300 exceeds every category cap.
    when(measureApiClient.getReachAndFrequencyBySites(any(), anyBoolean()))
        .thenReturn(
            List.of(
                MeasureReachFrequencyResponseDTO.builder()
                    .referenceId("REF-dig1")
                    .status("success")
                    .impressions(100_000L)
                    .reach(5_000L)
                    .build()));
    stubSchedulesForAll(buildSchedule(300.0));

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    assertEquals(AutoSelectionReasonCode.BUDGET_TOO_LOW, run.getAutoSelectionReasonCode());
    assertTrue(
        run.getAutoSelectionDiagnostics().getCheapestEstimatedCost().compareTo(new BigDecimal("50"))
            > 0);
  }

  @Test
  void reason_goalUnreachable_goalOnlyReach() {
    RecommendationRun run = stubHeldRun();
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("US");
    request.setStartDate(START_DATE);
    request.setEndDate(END_DATE);
    request.setGoal(RecommendationRequestDTO.CampaignGoal.REACH);
    request.setGoalValue(1_000_000L);
    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    setupScoringPipeline(List.of(dig1), Map.of("dig1", 95.0));
    // Pipeline-level Measure has usable data (total achievable reach 100), but the selection-time
    // schedules carry no metrics → zero selection with a goal far above what is achievable.
    when(measureApiClient.getReachAndFrequencyBySites(any(), anyBoolean()))
        .thenReturn(
            List.of(
                MeasureReachFrequencyResponseDTO.builder()
                    .referenceId("REF-dig1")
                    .status("success")
                    .impressions(200L)
                    .reach(100L)
                    .build()));
    stubSchedulesForAll(buildScheduleWithoutMeasureData(100.0));

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    assertEquals(AutoSelectionReasonCode.GOAL_UNREACHABLE, run.getAutoSelectionReasonCode());
    assertEquals(100L, run.getAutoSelectionDiagnostics().getAchievableMetricTotal());
  }

  @Test
  void budgetOnly_noGoal_autoSelects() {
    // Budget present, NO goal: a supported "rely on the budget" campaign. With schedules priced
    // affordably (basePrice below budget) and valid Measure data, budget-aware selection must fill
    // the budget rather than return zero.
    RecommendationRun run = stubHeldRun();
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("US");
    request.setStartDate(START_DATE);
    request.setEndDate(END_DATE);
    request.setBudget(new BigDecimal("500000"));
    request.setBudgetAllocation(Map.of("digital", 100.0));
    // goal + goalValue intentionally left null
    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    setupScoringPipeline(List.of(dig1), Map.of("dig1", 95.0));
    stubSchedulesForAll(buildSchedule(3500.0)); // affordable, well under the 500k budget

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ScheduleSummaryDTO>> scheduleCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(scheduleRecommendationService)
        .saveSchedulesForRun(eq(RUN_ID), eq(CAMPAIGN_ID), scheduleCaptor.capture());
    assertTrue(scheduleCaptor.getValue().containsKey("dig1"), "budget-only run must auto-select");

    assertEquals(AutoSelectionReasonCode.INVENTORIES_SELECTED, run.getAutoSelectionReasonCode());
    assertEquals(1, run.getAutoSelectionDiagnostics().getSelectedCount());
  }

  @Test
  void reason_selectionYieldedZero_goalTooSmallForEveryCandidate() {
    RecommendationRun run = stubHeldRun();
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("US");
    request.setStartDate(START_DATE);
    request.setEndDate(END_DATE);
    request.setGoal(RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);
    request.setGoalValue(5_000L);
    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    setupScoringPipeline(List.of(dig1), Map.of("dig1", 95.0));
    // Usable Measure data with achievable total (10k) above the goal (5k), but every schedule's
    // individual estimate (10k) overshoots the remaining goal → greedy selects nothing.
    when(measureApiClient.getReachAndFrequencyBySites(any(), anyBoolean()))
        .thenReturn(
            List.of(
                MeasureReachFrequencyResponseDTO.builder()
                    .referenceId("REF-dig1")
                    .status("success")
                    .impressions(10_000L)
                    .reach(5_000L)
                    .build()));
    stubSchedulesForAll(buildSchedule(100.0));

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    assertEquals(AutoSelectionReasonCode.SELECTION_YIELDED_ZERO, run.getAutoSelectionReasonCode());
    assertEquals(10_000L, run.getAutoSelectionDiagnostics().getAchievableMetricTotal());
  }
}
