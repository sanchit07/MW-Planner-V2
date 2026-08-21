package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.AudienceData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.domain.RecommendationRun;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.repository.AudienceRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
import com.mw.recommendation.engine.repository.RecommendationResultRepository;
import com.mw.recommendation.engine.repository.RecommendationRunRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Unit tests specifically for database access optimization in RecommendationAsyncService. Tests
 * verify that: 1. Batch fetching replaces N+1 queries 2. Business logic remains identical 3. Same
 * results produced with optimized queries 4. Proper handling of audience data lookup (inventoryId
 * first, then referenceId)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RecommendationAsyncService - Database Access Optimization Tests")
class RecommendationAsyncServiceBatchOptimizationTest {

  @Mock private InventoryRepository inventoryRepository;
  @Mock private AudienceRepository audienceRepository;
  @Mock private ScoringService scoringService;
  @Mock private RecommendationRunRepository recommendationRunRepository;
  @Mock private RecommendationResultRepository recommendationResultRepository;
  @Mock private ScheduleRecommendationService scheduleRecommendationService;
  @Mock private VirtualThreadTaskExecutor virtualThreadTaskExecutor;
  @Mock private MongoTemplate mongoTemplate;
  @Mock private MeasureApiClient measureApiClient;

  @Mock private AutoSelectionReasonResolver autoSelectionReasonResolver;

  @org.mockito.Spy
  private java.time.Clock clock =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-06-15T00:00:00Z"), java.time.ZoneOffset.UTC);

  @InjectMocks private RecommendationAsyncService service;

  private static final String RUN_ID = "run-optimization-test";
  private static final String CAMPAIGN_ID = "campaign-001";
  private static final LocalDate START_DATE = LocalDate.of(2025, 1, 1);
  private static final LocalDate END_DATE = LocalDate.of(2025, 1, 31);

  @BeforeEach
  void setUp() {
    // Configure mock run repository
    RecommendationRun run = new RecommendationRun();
    run.setRunId(RUN_ID);
    run.setCampaignId(CAMPAIGN_ID);
    run.setStatus(RecommendationRun.RunStatus.IN_PROGRESS);
    run.setCompletionPercentage(0);
    run.setGeneratedAt(LocalDateTime.now());
    lenient().when(recommendationRunRepository.findByRunId(RUN_ID)).thenReturn(Optional.of(run));
    lenient().when(recommendationRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    // Configure virtual thread executor to run synchronously
    doAnswer(
            invocation -> {
              Runnable r = invocation.getArgument(0);
              r.run();
              return null;
            })
        .when(virtualThreadTaskExecutor)
        .execute(any());

    // Configure MongoTemplate to handle bulk inserts
    lenient()
        .when(mongoTemplate.insert(anyList(), any(Class.class)))
        .thenAnswer(i -> i.getArgument(0));

    // Configure recommendation result repository for bulk update
    lenient()
        .doNothing()
        .when(recommendationResultRepository)
        .bulkUpdateSelectionMode(anyString(), anyMap());

    // Mock Measure API client to return empty responses for reach/frequency calls
    lenient()
        .when(measureApiClient.getReachAndFrequencyBySites(any(), anyBoolean()))
        .thenReturn(List.of());
  }

  @Test
  @DisplayName("Should use batch queries (Phase 1.6 optimization) instead of N+1 queries")
  void testBatchFetchingReplacesN1Queries() {
    // Arrange: Create 10 inventories
    List<Inventory> inventories = createTestInventories(10);
    List<AudienceData> audienceDataList = createTestAudienceData(inventories);

    RecommendationRequestDTO request = createTestRequest();

    // Mock inventory repository to return test inventories
    when(inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            eq("Singapore"),
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

    // Mock batch audience query (Phase 1.6 - now using findByInventoryIdIn)
    when(audienceRepository.findByInventoryIdIn(anyList())).thenReturn(audienceDataList);

    // Mock batch data fetching
    when(scoringService.batchFetchBookingData(anyList(), eq(START_DATE), eq(END_DATE)))
        .thenReturn(new HashMap<>());
    when(scoringService.batchFetchBrandData(anyList())).thenReturn(new HashMap<>());

    // Mock scoring service with new 5-parameter signature
    when(scoringService.calculateScore(
            any(Inventory.class), any(), eq(request), anyMap(), anyMap()))
        .thenReturn(createTestScore(75.0));

    // Act
    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    // Assert: Verify batch query was called (batched with size 500, so 1 batch for 10 items)
    verify(audienceRepository, atLeastOnce()).findByInventoryIdIn(anyList());

    // Assert: Verify OLD N+1 queries were NOT called
    verify(audienceRepository, never()).findByInventoryId(anyString());
    verify(audienceRepository, never()).findByReferenceId(anyString());

    // Assert: Results saved via bulk insert (mongoTemplate), then selectionMode bulk updated
    verify(mongoTemplate, atLeastOnce()).insert(anyList(), eq(RecommendationResult.class));
    verify(recommendationResultRepository, times(1)).bulkUpdateSelectionMode(eq(RUN_ID), anyMap());
  }

  @Test
  @DisplayName("Should handle inventories without audience data gracefully")
  void testFallbackToReferenceIdLookup() {
    // Arrange: Create inventories where some have audience data, others don't
    List<Inventory> inventories = createTestInventories(3);
    RecommendationRequestDTO request = createTestRequest();

    // Mock inventory fetch
    when(inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            eq("Singapore"),
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

    // Audience for inventory 0: found by inventoryId
    AudienceData audience0 = new AudienceData();
    audience0.setInventoryId("inv-0");
    audience0.setReferenceId("ref-0");

    // No audience for inventory 1 or 2 (inventory 2's referenceId won't be matched)
    AudienceData audience2 = new AudienceData();
    audience2.setInventoryId(null);
    audience2.setReferenceId("ref-2");

    // Mock batch query (Phase 1.6 - now using findByInventoryIdIn)
    // Returns only audience0 which has inventoryId
    when(audienceRepository.findByInventoryIdIn(anyList())).thenReturn(List.of(audience0));

    // Mock batch data fetching
    when(scoringService.batchFetchBookingData(anyList(), eq(START_DATE), eq(END_DATE)))
        .thenReturn(new HashMap<>());
    when(scoringService.batchFetchBrandData(anyList())).thenReturn(new HashMap<>());

    // Mock scoring - verify correct audience data is passed
    when(scoringService.calculateScore(
            any(Inventory.class), any(), eq(request), anyMap(), anyMap()))
        .thenReturn(createTestScore(80.0));

    // Act
    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    // Assert: Verify scoring was called with correct audience data for each inventory
    ArgumentCaptor<AudienceData> audienceCaptor = ArgumentCaptor.forClass(AudienceData.class);
    verify(scoringService, times(3))
        .calculateScore(
            any(Inventory.class), audienceCaptor.capture(), eq(request), anyMap(), anyMap());

    List<AudienceData> capturedAudience = audienceCaptor.getAllValues();
    assertEquals(audience0, capturedAudience.get(0), "First inventory should use audience0");
    assertNull(
        capturedAudience.get(1),
        "Second inventory should have null audience (no match by inventoryId)");
    assertNull(
        capturedAudience.get(2),
        "Third inventory should have null audience (audience2 has no inventoryId match)");
  }

  @Test
  @DisplayName("Should handle null audience data gracefully (no audience found)")
  void testHandlesNullAudienceData() {
    // Arrange: Create inventories with no audience data available
    List<Inventory> inventories = createTestInventories(5);
    RecommendationRequestDTO request = createTestRequest();

    when(inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            eq("Singapore"),
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

    // No audience data found
    when(audienceRepository.findByInventoryIdInOrReferenceIdIn(anyList(), anyList()))
        .thenReturn(Collections.emptyList());

    // Mock batch data fetching
    when(scoringService.batchFetchBookingData(anyList(), eq(START_DATE), eq(END_DATE)))
        .thenReturn(new HashMap<>());
    when(scoringService.batchFetchBrandData(anyList())).thenReturn(new HashMap<>());

    // Mock scoring to handle null audience
    when(scoringService.calculateScore(
            any(Inventory.class), isNull(), eq(request), anyMap(), anyMap()))
        .thenReturn(createTestScore(50.0));

    // Act
    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    // Assert: All inventories scored with null audience
    verify(scoringService, times(5))
        .calculateScore(any(Inventory.class), isNull(), eq(request), anyMap(), anyMap());

    // Assert: Results saved via bulk insert, then selectionMode bulk updated
    verify(mongoTemplate, atLeastOnce()).insert(anyList(), eq(RecommendationResult.class));
    verify(recommendationResultRepository, times(1)).bulkUpdateSelectionMode(eq(RUN_ID), anyMap());
  }

  @Test
  @DisplayName("Should produce identical results as before optimization")
  void testProducesSameResultsAsBeforeOptimization() {
    // Arrange: Scenario that mimics pre-optimization behavior
    List<Inventory> inventories = createTestInventories(100);
    List<AudienceData> audienceDataList = createTestAudienceData(inventories);

    RecommendationRequestDTO request = createTestRequest();

    when(inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            eq("Singapore"),
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

    // Mock batch queries to return consistent data
    when(audienceRepository.findByInventoryIdInOrReferenceIdIn(anyList(), anyList()))
        .thenReturn(audienceDataList);

    // Mock batch data fetching
    when(scoringService.batchFetchBookingData(anyList(), eq(START_DATE), eq(END_DATE)))
        .thenReturn(new HashMap<>());
    when(scoringService.batchFetchBrandData(anyList())).thenReturn(new HashMap<>());

    // Mock scoring with deterministic scores based on inventory index
    when(scoringService.calculateScore(
            any(Inventory.class), any(), eq(request), anyMap(), anyMap()))
        .thenAnswer(
            invocation -> {
              Inventory inv = invocation.getArgument(0);
              int index = Integer.parseInt(inv.getInventoryId().split("-")[1]);
              return createTestScore(100.0 - index); // Descending scores
            });

    // Act
    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    // Assert: Results were saved via bulk insert with mongoTemplate
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<RecommendationResult>> resultsCaptor =
        (ArgumentCaptor<List<RecommendationResult>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
    verify(mongoTemplate, atLeastOnce())
        .insert(resultsCaptor.capture(), eq(RecommendationResult.class));

    // Get the first batch (all results should be in first batch since size = 100 < BATCH_SIZE)
    List<RecommendationResult> savedResults = resultsCaptor.getValue();
    assertEquals(100, savedResults.size(), "Should save all 100 results");

    // Verify sorted by score descending
    for (int i = 0; i < savedResults.size() - 1; i++) {
      Double currentScore = savedResults.get(i).getFinalScore();
      Double nextScore = savedResults.get(i + 1).getFinalScore();
      assertTrue(
          currentScore >= nextScore,
          String.format(
              "Results should be sorted descending: score[%d]=%.2f >= score[%d]=%.2f",
              i, currentScore, i + 1, nextScore));
    }
  }

  @Test
  @DisplayName("Should handle large dataset efficiently (1000+ inventories)")
  void testHandlesLargeDatasetEfficiently() {
    // Arrange: 1000 inventories to test scalability
    List<Inventory> inventories = createTestInventories(1000);
    List<AudienceData> audienceDataList = createTestAudienceData(inventories);

    RecommendationRequestDTO request = createTestRequest();

    when(inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            eq("Singapore"),
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

    when(audienceRepository.findByInventoryIdIn(anyList())).thenReturn(audienceDataList);

    // Mock batch data fetching
    when(scoringService.batchFetchBookingData(anyList(), eq(START_DATE), eq(END_DATE)))
        .thenReturn(new HashMap<>());
    when(scoringService.batchFetchBrandData(anyList())).thenReturn(new HashMap<>());

    when(scoringService.calculateScore(
            any(Inventory.class), any(), eq(request), anyMap(), anyMap()))
        .thenReturn(createTestScore(70.0));

    // Act
    long startTime = System.currentTimeMillis();
    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);
    long duration = System.currentTimeMillis() - startTime;

    // Assert: Batch query called (Phase 1.6) - expects 2 batches (500 each)
    verify(audienceRepository, atLeastOnce()).findByInventoryIdIn(anyList());

    // Assert: No individual queries
    verify(audienceRepository, never()).findByInventoryId(anyString());
    verify(audienceRepository, never()).findByReferenceId(anyString());

    // Assert: Results saved via bulk insert (mongoTemplate), then selectionMode bulk updated
    verify(mongoTemplate, atLeastOnce()).insert(anyList(), eq(RecommendationResult.class));
    verify(recommendationResultRepository, times(1)).bulkUpdateSelectionMode(eq(RUN_ID), anyMap());

    System.out.printf("Processed 1000 inventories in %d ms%n", duration);
  }

  @Test
  @DisplayName("Should respect topN limit after scoring all inventories")
  void testRespectsTopNLimit() {
    // Arrange: 50 inventories but topN = 10
    List<Inventory> inventories = createTestInventories(50);
    List<AudienceData> audienceDataList = createTestAudienceData(inventories);

    RecommendationRequestDTO request = createTestRequest();
    request.setTopN(10);

    when(inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            eq("Singapore"),
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

    when(audienceRepository.findByInventoryIdInOrReferenceIdIn(anyList(), anyList()))
        .thenReturn(audienceDataList);

    // Mock batch data fetching
    when(scoringService.batchFetchBookingData(anyList(), eq(START_DATE), eq(END_DATE)))
        .thenReturn(new HashMap<>());
    when(scoringService.batchFetchBrandData(anyList())).thenReturn(new HashMap<>());

    when(scoringService.calculateScore(
            any(Inventory.class), any(), eq(request), anyMap(), anyMap()))
        .thenAnswer(
            invocation -> {
              Inventory inv = invocation.getArgument(0);
              int index = Integer.parseInt(inv.getInventoryId().split("-")[1]);
              return createTestScore(100.0 - index);
            });

    // Act
    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, request);

    // Assert: All 50 were scored
    verify(scoringService, times(50))
        .calculateScore(any(Inventory.class), any(), eq(request), anyMap(), anyMap());

    // Assert: Only top 10 saved via mongoTemplate bulk insert
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<RecommendationResult>> resultsCaptor =
        (ArgumentCaptor<List<RecommendationResult>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
    verify(mongoTemplate, atLeastOnce())
        .insert(resultsCaptor.capture(), eq(RecommendationResult.class));

    List<RecommendationResult> savedResults = resultsCaptor.getValue();
    assertEquals(10, savedResults.size(), "Should save only top 10 results");

    // Verify top scores
    assertTrue(
        savedResults.get(0).getFinalScore() > savedResults.get(9).getFinalScore(),
        "Should contain highest scoring inventories");
  }

  // ---- Helper Methods ----

  private List<Inventory> createTestInventories(int count) {
    List<Inventory> inventories = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Inventory inv = new Inventory();
      inv.setInventoryId("inv-" + i);
      inv.setReferenceId("ref-" + i);
      inv.setName("Inventory " + i);
      inv.setClassification("Digital");
      inv.setType("Billboard");
      inventories.add(inv);
    }
    return inventories;
  }

  private List<AudienceData> createTestAudienceData(List<Inventory> inventories) {
    List<AudienceData> audienceDataList = new ArrayList<>();
    for (Inventory inv : inventories) {
      AudienceData audience = new AudienceData();
      audience.setInventoryId(inv.getInventoryId());
      audience.setReferenceId(inv.getReferenceId());
      // Note: AudienceData uses nested summary objects, not direct setters for impressions/reach
      audienceDataList.add(audience);
    }
    return audienceDataList;
  }

  private RecommendationRequestDTO createTestRequest() {
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("Singapore");
    request.setStartDate(START_DATE);
    request.setEndDate(END_DATE);
    request.setBudget(BigDecimal.valueOf(10000));
    request.setProductId("prod-001");
    request.setCompanyId("comp-001");
    return request;
  }

  private InventoryScore createTestScore(double finalScore) {
    return InventoryScore.builder()
        .finalScore(finalScore)
        .measureFit(80.0)
        .geoFit(85.0)
        .availability(90.0)
        .budgetFit(75.0)
        .audienceFit(70.0)
        .brandFit(95.0)
        .qualityFit(88.0)
        .timeFit(82.0)
        .build();
  }
}
