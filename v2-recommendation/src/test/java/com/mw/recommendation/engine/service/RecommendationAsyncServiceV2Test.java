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
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests for the optimized v2 async pipeline. Behavior (auto-selection, scoring, scheduling) must
 * match v1 exactly; in addition, v2 must apply its output-neutral optimizations: selectionMode is
 * persisted in a single pass (set on the inserted documents) and the redundant {@code
 * bulkUpdateSelectionMode} second pass is NOT performed.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationAsyncServiceV2Test {

  @Mock private InventoryRepository inventoryRepository;
  @Mock private AudienceRepository audienceRepository;
  @Mock private ScoringService scoringService;
  @Mock private RecommendationRunRepository recommendationRunRepository;
  @Mock private RecommendationResultRepository recommendationResultRepository;
  @Mock private ScheduleRecommendationService scheduleRecommendationService;
  @Mock private VirtualThreadTaskExecutor virtualThreadTaskExecutor;
  @Mock private MongoTemplate mongoTemplate;
  @Mock private MeasureClientV2 measureClientV2;

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

  @InjectMocks private RecommendationAsyncServiceV2 service;

  private static final String RUN_ID = "run-test-001";
  private static final String CAMPAIGN_ID = "campaign-001";
  private static final LocalDate START_DATE = LocalDate.of(2025, 1, 1);
  private static final LocalDate END_DATE = LocalDate.of(2025, 1, 31);

  @BeforeEach
  void setUp() {
    lenient()
        .doAnswer(
            invocation -> {
              Runnable r = invocation.getArgument(0);
              r.run();
              return null;
            })
        .when(virtualThreadTaskExecutor)
        .execute(any());

    RecommendationRun run = new RecommendationRun();
    run.setRunId(RUN_ID);
    run.setStatus(RecommendationRun.RunStatus.IN_PROGRESS);
    lenient().when(recommendationRunRepository.findByRunId(RUN_ID)).thenReturn(Optional.of(run));
    lenient().when(recommendationRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    lenient()
        .when(mongoTemplate.insert(anyList(), any(Class.class)))
        .thenAnswer(i -> i.getArgument(0));
    // v2 uses a targeted $set for progress rather than read-modify-write
    lenient().when(mongoTemplate.updateFirst(any(), any(), any(Class.class))).thenReturn(null);

    lenient()
        .when(
            scheduleRecommendationService.buildBestScheduleForBudgetCap(
                any(), any(), any(), any(), any()))
        .thenReturn(Optional.empty());

    lenient()
        .when(measureClientV2.getReachAndFrequencyBySites(any(), anyBoolean()))
        .thenReturn(List.of());
  }

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
            anyBoolean()))
        .thenReturn(inventories);

    lenient()
        .when(audienceRepository.findByInventoryIdInOrReferenceIdIn(anyList(), anyList()))
        .thenReturn(List.of());
    lenient().when(audienceRepository.findByReferenceIdIn(anyList())).thenReturn(List.of());
    lenient().when(audienceRepository.findByInventoryIdIn(anyList())).thenReturn(List.of());

    lenient()
        .when(scoringService.batchFetchBookingData(anyList(), any(), any()))
        .thenReturn(new HashMap<>());
    lenient().when(scoringService.batchFetchBrandData(anyList())).thenReturn(new HashMap<>());

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

  /** Collect every RecommendationResult passed to mongoTemplate.insert across batches. */
  @SuppressWarnings("unchecked")
  private Map<String, RecommendationResult> capturedInsertedResults() {
    ArgumentCaptor<List<RecommendationResult>> captor = ArgumentCaptor.forClass(List.class);
    verify(mongoTemplate, atLeastOnce()).insert(captor.capture(), any(Class.class));
    Map<String, RecommendationResult> byId = new HashMap<>();
    for (List<RecommendationResult> batch : captor.getAllValues()) {
      for (RecommendationResult r : batch) {
        byId.put(r.getInventoryId(), r);
      }
    }
    return byId;
  }

  private void stubSchedules(double defaultPrice) {
    when(scheduleRecommendationService.buildScheduleSummariesForInventories(
            anyList(), eq(START_DATE), eq(END_DATE), isNull(), any()))
        .thenAnswer(
            invocation -> {
              List<Inventory> invs = invocation.getArgument(0);
              Map<String, ScheduleSummaryDTO> result = new HashMap<>();
              for (Inventory inv : invs) {
                result.put(inv.getInventoryId(), buildSchedule(defaultPrice));
              }
              return result;
            });
  }

  // ---- Phase 2: bounded/chunked scoring (findings #2, #5, #14) ----

  @Test
  void chunkedScoring_fetchesEnrichmentPerChunk_andScoresEveryInventory() {
    // chunkSize=1 forces one chunk per inventory, exercising the multi-chunk path and proving each
    // chunk fetches its own enrichment (finding #5) while output stays complete (findings #2/#14).
    ReflectionTestUtils.setField(service, "scoringChunkSize", 1);
    RecommendationRequestDTO request = buildRequest(new BigDecimal("2000"));
    Inventory a = buildInventory("a", "Digital", "OOH");
    Inventory b = buildInventory("b", "Digital", "OOH");
    Inventory c = buildInventory("c", "Digital", "OOH");
    setupScoringPipeline(List.of(a, b, c), Map.of("a", 95.0, "b", 90.0, "c", 85.0));
    stubSchedules(300.0);

    service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request);

    // Enrichment fetched once per chunk (3 inventories at chunkSize=1 => 3 booking fetches).
    verify(scoringService, times(3)).batchFetchBookingData(anyList(), any(), any());

    // Every inventory is still scored and persisted despite chunking.
    Map<String, RecommendationResult> inserted = capturedInsertedResults();
    assertTrue(inserted.containsKey("a"));
    assertTrue(inserted.containsKey("b"));
    assertTrue(inserted.containsKey("c"));
  }

  @Test
  void chunkedScoring_singleChunkWhenChunkLargerThanInput() {
    // Default chunk size (large) => a single chunk, one enrichment fetch, all inventories scored.
    RecommendationRequestDTO request = buildRequest(new BigDecimal("2000"));
    Inventory a = buildInventory("a", "Digital", "OOH");
    Inventory b = buildInventory("b", "Digital", "OOH");
    setupScoringPipeline(List.of(a, b), Map.of("a", 95.0, "b", 90.0));
    stubSchedules(300.0);

    service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request);

    verify(scoringService, times(1)).batchFetchBookingData(anyList(), any(), any());
    Map<String, RecommendationResult> inserted = capturedInsertedResults();
    assertTrue(inserted.containsKey("a"));
    assertTrue(inserted.containsKey("b"));
  }

  // ---- Reliability: finding #1 (no stuck IN_PROGRESS runs) ----

  @Test
  void asyncPipeline_onError_marksRunFailed() {
    RecommendationRequestDTO request = buildRequest(new BigDecimal("1000"));
    // Force the inventory fetch to throw so the top-level catch block runs.
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
            anyBoolean()))
        .thenThrow(new RuntimeException("boom"));

    // Must not propagate — the pipeline swallows and records the failure.
    assertDoesNotThrow(
        () -> service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request));

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate, atLeastOnce())
        .updateFirst(any(Query.class), updateCaptor.capture(), eq(RecommendationRun.class));
    boolean markedFailed =
        updateCaptor.getAllValues().stream()
            .anyMatch(u -> u.getUpdateObject().toString().contains("FAILED"));
    assertTrue(markedFailed, "an errored run must be marked FAILED, never left IN_PROGRESS");
  }

  @Test
  void bulkInsertFailure_marksRunFailed_notSilentlyCompleted() {
    // Finding #10: a persistent insert failure must fail the run, not complete it with a partial
    // set.
    RecommendationRequestDTO request = buildRequest(new BigDecimal("2000"));
    Inventory a = buildInventory("a", "Digital", "OOH");
    setupScoringPipeline(List.of(a), Map.of("a", 95.0));
    stubSchedules(300.0);
    when(mongoTemplate.insert(anyList(), any(Class.class)))
        .thenThrow(new RuntimeException("write failed"));

    assertDoesNotThrow(
        () -> service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request));

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate, atLeastOnce())
        .updateFirst(any(Query.class), updateCaptor.capture(), eq(RecommendationRun.class));
    boolean markedFailed =
        updateCaptor.getAllValues().stream()
            .anyMatch(u -> u.getUpdateObject().toString().contains("FAILED"));
    assertTrue(markedFailed, "a persistent insert failure must mark the run FAILED");
  }

  // ---- Behavioral parity with v1 ----

  @Test
  void budgetAware_respectsCategoryCaps() {
    RecommendationRequestDTO request = buildRequest(new BigDecimal("2000"));
    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    Inventory cls1 = buildInventory("cls1", "Classic", "OOH");
    setupScoringPipeline(List.of(dig1, cls1), Map.of("dig1", 95.0, "cls1", 92.0));
    stubSchedules(300.0);

    service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ScheduleSummaryDTO>> scheduleCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(scheduleRecommendationService)
        .saveSchedulesForRun(eq(RUN_ID), eq(CAMPAIGN_ID), scheduleCaptor.capture());
    Map<String, ScheduleSummaryDTO> saved = scheduleCaptor.getValue();
    assertTrue(saved.containsKey("dig1"));
    assertTrue(saved.containsKey("cls1"));
  }

  @Test
  void scoreBandBatching_processesHighScoresFirst() {
    RecommendationRequestDTO request = buildRequest(new BigDecimal("400"));
    Inventory high = buildInventory("high", "Digital", "OOH");
    Inventory mid = buildInventory("mid", "Digital", "OOH");
    Inventory low = buildInventory("low", "Digital", "OOH");
    setupScoringPipeline(List.of(high, mid, low), Map.of("high", 95.0, "mid", 85.0, "low", 45.0));

    when(scheduleRecommendationService.buildScheduleSummariesForInventories(
            anyList(), eq(START_DATE), eq(END_DATE), isNull(), any()))
        .thenAnswer(
            invocation -> {
              List<Inventory> invs = invocation.getArgument(0);
              Map<String, ScheduleSummaryDTO> result = new HashMap<>();
              for (Inventory inv : invs) {
                result.put(
                    inv.getInventoryId(),
                    buildSchedule("low".equals(inv.getInventoryId()) ? 500.0 : 100.0));
              }
              return result;
            });

    service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ScheduleSummaryDTO>> scheduleCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(scheduleRecommendationService)
        .saveSchedulesForRun(eq(RUN_ID), eq(CAMPAIGN_ID), scheduleCaptor.capture());
    Map<String, ScheduleSummaryDTO> saved = scheduleCaptor.getValue();
    assertTrue(saved.containsKey("high"));
    assertTrue(saved.containsKey("mid"));
    assertFalse(saved.containsKey("low"));
  }

  @Test
  void noBudgetNoGoal_noSelection() {
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("US");
    request.setStartDate(START_DATE);
    request.setEndDate(END_DATE);
    Inventory inv = buildInventory("inv1", "Digital", "OOH");
    setupScoringPipeline(List.of(inv), Map.of("inv1", 95.0));

    service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request);

    verify(scheduleRecommendationService, never())
        .saveSchedulesForRun(anyString(), anyString(), anyMap());
  }

  @Test
  void goalOnly_flatApproach_selectsUntilGoalMet() {
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("US");
    request.setStartDate(START_DATE);
    request.setEndDate(END_DATE);
    request.setGoal(RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);
    request.setGoalValue(20000L);

    Inventory inv1 = buildInventory("inv1", "Digital", "OOH");
    Inventory inv2 = buildInventory("inv2", "Classic", "OOH");
    setupScoringPipeline(List.of(inv1, inv2), Map.of("inv1", 95.0, "inv2", 85.0));
    stubSchedules(100.0);

    service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ScheduleSummaryDTO>> scheduleCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(scheduleRecommendationService)
        .saveSchedulesForRun(eq(RUN_ID), eq(CAMPAIGN_ID), scheduleCaptor.capture());
    assertEquals(2, scheduleCaptor.getValue().size());
  }

  @Test
  void budgetAware_excludesLowScoredInventories() {
    RecommendationRequestDTO request = buildRequest(new BigDecimal("10000"));
    Inventory high = buildInventory("high", "Digital", "OOH");
    Inventory low = buildInventory("low", "Digital", "OOH");
    setupScoringPipeline(List.of(high, low), Map.of("high", 95.0, "low", 8.0));
    stubSchedules(100.0);

    service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ScheduleSummaryDTO>> scheduleCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(scheduleRecommendationService)
        .saveSchedulesForRun(eq(RUN_ID), eq(CAMPAIGN_ID), scheduleCaptor.capture());
    Map<String, ScheduleSummaryDTO> saved = scheduleCaptor.getValue();
    assertTrue(saved.containsKey("high"));
    assertFalse(saved.containsKey("low"));
  }

  // ---- v2-specific: single-pass selectionMode, no second bulk update ----

  @Test
  void setsSelectionModeOnInsertedDocs_andNeverCallsBulkUpdate() {
    RecommendationRequestDTO request = buildRequest(new BigDecimal("500"));
    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    Inventory dig2 = buildInventory("dig2", "Digital", "OOH");
    // dig2 scores below the 10.0 recommendation threshold, so it is never auto-selected → null.
    setupScoringPipeline(List.of(dig1, dig2), Map.of("dig1", 95.0, "dig2", 8.0));
    stubSchedules(100.0);

    service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request);

    // v2 persists results exactly once and never issues the second selectionMode pass
    verify(mongoTemplate, atLeastOnce()).insert(anyList(), any(Class.class));
    verify(recommendationResultRepository, never()).bulkUpdateSelectionMode(anyString(), anyMap());

    Map<String, RecommendationResult> inserted = capturedInsertedResults();
    assertEquals(
        SelectionMode.AUTO,
        inserted.get("dig1").getSelectionMode(),
        "dig1 (auto-selected) must be persisted AUTO in a single write pass");
    assertNull(
        inserted.get("dig2").getSelectionMode(),
        "dig2 (below score threshold) must be persisted with null selectionMode");
  }

  @Test
  void noBudgetSov_selectsUntilCumulativeSovMet_onInsertedDocs() {
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("US");
    request.setStartDate(START_DATE);
    request.setEndDate(END_DATE);
    request.setBudget(null);
    request.setGoal(RecommendationRequestDTO.CampaignGoal.SOV);
    request.setGoalValue(50L);

    Inventory sov1 = buildInventory("sov1", "Digital", "OOH");
    Inventory sov2 = buildInventory("sov2", "Digital", "OOH");
    Inventory sov3 = buildInventory("sov3", "Digital", "OOH");
    setupScoringPipeline(
        List.of(sov1, sov2, sov3), Map.of("sov1", 95.0, "sov2", 85.0, "sov3", 75.0));
    lenient()
        .when(scoringService.calculateRawSov(eq(sov1), eq("US"), any(), any()))
        .thenReturn(30.0);
    lenient()
        .when(scoringService.calculateRawSov(eq(sov2), eq("US"), any(), any()))
        .thenReturn(25.0);
    lenient()
        .when(scoringService.calculateRawSov(eq(sov3), eq("US"), any(), any()))
        .thenReturn(20.0);

    service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request);

    verify(recommendationResultRepository, never()).bulkUpdateSelectionMode(anyString(), anyMap());
    Map<String, RecommendationResult> inserted = capturedInsertedResults();
    assertEquals(SelectionMode.AUTO, inserted.get("sov1").getSelectionMode());
    assertEquals(SelectionMode.AUTO, inserted.get("sov2").getSelectionMode());
    assertNull(inserted.get("sov3").getSelectionMode(), "goal met before sov3");
  }

  // ---- v2-specific: deterministic across runs (jitter seeded by request body, not runId) ----

  @Test
  void sameRequestBody_differentRunId_producesIdenticalFinalScores() {
    // Two genuine runs of the SAME request body under different runIds/campaignIds. Because v2
    // seeds
    // jitter from the request hash (not the runId), the persisted finalScores — and therefore
    // eligibility and the auto-selected count — must be identical across runs.
    RecommendationRequestDTO request = buildRequest(new BigDecimal("2000"));
    Inventory a = buildInventory("a", "Digital", "OOH");
    Inventory b = buildInventory("b", "Digital", "OOH");
    Inventory c = buildInventory("c", "Classic", "OOH");
    setupScoringPipeline(List.of(a, b, c), Map.of("a", 80.0, "b", 60.0, "c", 40.0));
    stubSchedules(100.0);

    lenient()
        .when(recommendationRunRepository.findByRunId(anyString()))
        .thenAnswer(
            inv -> {
              RecommendationRun r = new RecommendationRun();
              r.setRunId(inv.getArgument(0));
              r.setStatus(RecommendationRun.RunStatus.IN_PROGRESS);
              return Optional.of(r);
            });

    service.processRecommendationsAsyncOptimized("run-A", "cmp-A", request);
    service.processRecommendationsAsyncOptimized("run-B", "cmp-B", request);

    Map<String, Double> scoresA = insertedFinalScoresForRun("run-A");
    Map<String, Double> scoresB = insertedFinalScoresForRun("run-B");

    assertEquals(scoresA.keySet(), scoresB.keySet(), "same inventories scored in both runs");
    assertFalse(scoresA.isEmpty(), "results were persisted");
    for (String id : scoresA.keySet()) {
      assertEquals(
          scoresA.get(id),
          scoresB.get(id),
          "finalScore for " + id + " must be identical across runs (deterministic jitter)");
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Double> insertedFinalScoresForRun(String runId) {
    ArgumentCaptor<List<RecommendationResult>> captor = ArgumentCaptor.forClass(List.class);
    verify(mongoTemplate, atLeastOnce()).insert(captor.capture(), any(Class.class));
    Map<String, Double> byId = new HashMap<>();
    for (List<RecommendationResult> batch : captor.getAllValues()) {
      for (RecommendationResult r : batch) {
        if (runId.equals(r.getRunId())) {
          byId.put(r.getInventoryId(), r.getFinalScore());
        }
      }
    }
    return byId;
  }

  // ---- Auto-selection reason observability (folded into the completion $set) ----

  /** Last completion Update's {@code $set} document that carries the auto-selection reason. */
  private org.bson.Document capturedReasonSet() {
    ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate, atLeastOnce())
        .updateFirst(any(Query.class), captor.capture(), eq(RecommendationRun.class));
    org.bson.Document last = null;
    for (Update u : captor.getAllValues()) {
      Object set = u.getUpdateObject().get("$set");
      if (set instanceof org.bson.Document d && d.containsKey("autoSelectionReasonCode")) {
        last = d;
      }
    }
    assertNotNull(last, "the completion write must carry autoSelectionReasonCode");
    return last;
  }

  private RecommendationRun.AutoSelectionDiagnostics reasonDiagnostics(org.bson.Document set) {
    return (RecommendationRun.AutoSelectionDiagnostics) set.get("autoSelectionDiagnostics");
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
    RecommendationRequestDTO request = buildRequest(new BigDecimal("2000"));
    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    setupScoringPipeline(List.of(dig1), Map.of("dig1", 95.0));
    stubSchedules(300.0);

    service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request);

    org.bson.Document set = capturedReasonSet();
    assertEquals(AutoSelectionReasonCode.INVENTORIES_SELECTED, set.get("autoSelectionReasonCode"));
    assertEquals(1, reasonDiagnostics(set).getSelectedCount());
  }

  @Test
  void reason_noBudgetNoGoal() {
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("US");
    request.setStartDate(START_DATE);
    request.setEndDate(END_DATE);
    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    setupScoringPipeline(List.of(dig1), Map.of("dig1", 95.0));

    service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request);

    org.bson.Document set = capturedReasonSet();
    assertEquals(AutoSelectionReasonCode.NO_BUDGET_NO_GOAL, set.get("autoSelectionReasonCode"));
    assertEquals(0, reasonDiagnostics(set).getSelectedCount());
  }

  @Test
  void reason_goalAdPlaysNoBudget() {
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("US");
    request.setStartDate(START_DATE);
    request.setEndDate(END_DATE);
    request.setGoal(RecommendationRequestDTO.CampaignGoal.AD_PLAYS);
    request.setGoalValue(500L);
    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    setupScoringPipeline(List.of(dig1), Map.of("dig1", 95.0));

    service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request);

    assertEquals(
        AutoSelectionReasonCode.GOAL_ADPLAYS_NO_BUDGET,
        capturedReasonSet().get("autoSelectionReasonCode"));
  }

  @Test
  void reason_noCandidateInventories_whenFetchReturnsNothing() {
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
            anyBoolean()))
        .thenReturn(List.of());

    service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request);

    org.bson.Document set = capturedReasonSet();
    assertEquals(
        AutoSelectionReasonCode.NO_CANDIDATE_INVENTORIES, set.get("autoSelectionReasonCode"));
    assertEquals(0, reasonDiagnostics(set).getCandidateCount());
  }

  @Test
  void reason_measureDataUnavailable_goalOnlyReach() {
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

    service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request);

    org.bson.Document set = capturedReasonSet();
    assertEquals(
        AutoSelectionReasonCode.MEASURE_DATA_UNAVAILABLE, set.get("autoSelectionReasonCode"));
    assertEquals(0, reasonDiagnostics(set).getSitesWithReachFrequency());
  }

  @Test
  void reason_budgetTooLow() {
    RecommendationRequestDTO request = buildRequest(new BigDecimal("50"));
    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    dig1.setPrices(List.of(Inventory.PriceModel.builder().cpm(100.0).currency("USD").build()));
    setupScoringPipeline(List.of(dig1), Map.of("dig1", 95.0));
    // Pipeline-level Measure batch returns a usable row → forecast impressions 100k → estimated
    // CPM cost 10,000, far above the 50 budget; schedule basePrice 300 exceeds every category cap.
    when(measureClientV2.getReachAndFrequencyBySites(any(), anyBoolean()))
        .thenReturn(
            List.of(
                MeasureReachFrequencyResponseDTO.builder()
                    .referenceId("REF-dig1")
                    .status("success")
                    .impressions(100_000L)
                    .reach(5_000L)
                    .build()));
    stubSchedules(300.0);

    service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request);

    org.bson.Document set = capturedReasonSet();
    assertEquals(AutoSelectionReasonCode.BUDGET_TOO_LOW, set.get("autoSelectionReasonCode"));
    assertTrue(
        reasonDiagnostics(set).getCheapestEstimatedCost().compareTo(new BigDecimal("50")) > 0);
  }

  @Test
  void reason_goalUnreachable_goalOnlyReach() {
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
    when(measureClientV2.getReachAndFrequencyBySites(any(), anyBoolean()))
        .thenReturn(
            List.of(
                MeasureReachFrequencyResponseDTO.builder()
                    .referenceId("REF-dig1")
                    .status("success")
                    .impressions(200L)
                    .reach(100L)
                    .build()));
    stubSchedulesForAll(buildScheduleWithoutMeasureData(100.0));

    service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request);

    org.bson.Document set = capturedReasonSet();
    assertEquals(AutoSelectionReasonCode.GOAL_UNREACHABLE, set.get("autoSelectionReasonCode"));
    assertEquals(100L, reasonDiagnostics(set).getAchievableMetricTotal());
  }

  @Test
  void budgetOnly_noGoal_autoSelects() {
    // Budget present, NO goal: a supported "rely on the budget" campaign. With schedules priced
    // affordably (basePrice below budget) and valid Measure data, budget-aware selection must fill
    // the budget rather than return zero.
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("US");
    request.setStartDate(START_DATE);
    request.setEndDate(END_DATE);
    request.setBudget(new BigDecimal("500000"));
    request.setBudgetAllocation(Map.of("digital", 100.0));
    // goal + goalValue intentionally left null
    Inventory dig1 = buildInventory("dig1", "Digital", "OOH");
    setupScoringPipeline(List.of(dig1), Map.of("dig1", 95.0));
    stubSchedules(3500.0); // affordable CPM-based price, well under the 500k budget

    service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ScheduleSummaryDTO>> scheduleCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(scheduleRecommendationService)
        .saveSchedulesForRun(eq(RUN_ID), eq(CAMPAIGN_ID), scheduleCaptor.capture());
    assertTrue(scheduleCaptor.getValue().containsKey("dig1"), "budget-only run must auto-select");

    org.bson.Document set = capturedReasonSet();
    assertEquals(AutoSelectionReasonCode.INVENTORIES_SELECTED, set.get("autoSelectionReasonCode"));
    assertEquals(1, reasonDiagnostics(set).getSelectedCount());
  }

  @Test
  void reason_selectionYieldedZero_goalTooSmallForEveryCandidate() {
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
    when(measureClientV2.getReachAndFrequencyBySites(any(), anyBoolean()))
        .thenReturn(
            List.of(
                MeasureReachFrequencyResponseDTO.builder()
                    .referenceId("REF-dig1")
                    .status("success")
                    .impressions(10_000L)
                    .reach(5_000L)
                    .build()));
    stubSchedules(100.0);

    service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, request);

    org.bson.Document set = capturedReasonSet();
    assertEquals(
        AutoSelectionReasonCode.SELECTION_YIELDED_ZERO, set.get("autoSelectionReasonCode"));
    assertEquals(10_000L, reasonDiagnostics(set).getAchievableMetricTotal());
  }
}
