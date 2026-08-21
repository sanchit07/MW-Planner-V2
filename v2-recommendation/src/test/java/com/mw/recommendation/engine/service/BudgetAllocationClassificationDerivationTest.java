package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.RecommendationRun;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.repository.AudienceRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
import com.mw.recommendation.engine.repository.RecommendationResultRepository;
import com.mw.recommendation.engine.repository.RecommendationRunRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Tests for classification derivation from budgetAllocation in processRecommendationsAsync.
 * Verifies that when classifications is null, the fetch is filtered by budget allocation keys.
 */
@ExtendWith(MockitoExtension.class)
class BudgetAllocationClassificationDerivationTest {

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

  @Spy
  private java.time.Clock clock =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-06-15T00:00:00Z"), java.time.ZoneOffset.UTC);

  @InjectMocks private RecommendationAsyncService service;

  private static final LocalDate START = LocalDate.of(2026, 6, 11);
  private static final LocalDate END = LocalDate.of(2026, 7, 11);

  private RecommendationRequestDTO buildRequest(
      Map<String, Double> budgetAllocation, List<String> classifications) {
    RecommendationRequestDTO req = new RecommendationRequestDTO();
    req.setCountry("Singapore");
    req.setStartDate(START);
    req.setEndDate(END);
    req.setBudget(BigDecimal.valueOf(100000));
    req.setGoal(RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);
    req.setGoalValue(1000L);
    req.setBudgetAllocation(budgetAllocation);
    req.setClassifications(classifications);
    return req;
  }

  private void stubRunAndCompleteEarly(String runId) {
    RecommendationRun run = new RecommendationRun();
    run.setRunId(runId);
    run.setStatus(RecommendationRun.RunStatus.IN_PROGRESS);
    when(recommendationRunRepository.findByRunId(runId)).thenReturn(Optional.of(run));
    // Return empty inventories so processing completes quickly
    when(inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            any(),
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
    when(recommendationRunRepository.save(any())).thenReturn(run);
  }

  @Test
  void classic100Percent_derivesClassicClassification() {
    String runId = "run-classic-100";
    stubRunAndCompleteEarly(runId);

    RecommendationRequestDTO req = buildRequest(Map.of("classic", 100.0), null);

    service.processRecommendationsAsync(runId, "campaign-1", req);

    ArgumentCaptor<List> classifCaptor = ArgumentCaptor.forClass(List.class);
    verify(inventoryRepository)
        .findActiveInventoriesByCountryWithGeographyTargeting(
            eq("Singapore"),
            any(),
            any(),
            any(),
            any(),
            classifCaptor.capture(),
            any(),
            any(),
            nullable(Long.class),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any(),
            any());

    List<String> captured = classifCaptor.getValue();
    assertNotNull(captured);
    assertEquals(1, captured.size());
    assertEquals("Classic", captured.get(0));
  }

  @Test
  void classicAndDigital_derivesBothClassifications() {
    String runId = "run-classic-digital";
    stubRunAndCompleteEarly(runId);

    RecommendationRequestDTO req = buildRequest(Map.of("classic", 50.0, "digital", 50.0), null);

    service.processRecommendationsAsync(runId, "campaign-1", req);

    ArgumentCaptor<List> classifCaptor = ArgumentCaptor.forClass(List.class);
    verify(inventoryRepository)
        .findActiveInventoriesByCountryWithGeographyTargeting(
            any(),
            any(),
            any(),
            any(),
            any(),
            classifCaptor.capture(),
            any(),
            any(),
            nullable(Long.class),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any(),
            any());

    List<String> captured = classifCaptor.getValue();
    assertNotNull(captured);
    assertEquals(2, captured.size());
    assertTrue(captured.contains("Classic"));
    assertTrue(captured.contains("Digital"));
  }

  @Test
  void zeroValueKeys_excluded() {
    String runId = "run-zero-transit";
    stubRunAndCompleteEarly(runId);

    // classic=100, transit=0 — transit should be excluded
    RecommendationRequestDTO req = buildRequest(Map.of("classic", 100.0, "transit", 0.0), null);

    service.processRecommendationsAsync(runId, "campaign-1", req);

    ArgumentCaptor<List> classifCaptor = ArgumentCaptor.forClass(List.class);
    verify(inventoryRepository)
        .findActiveInventoriesByCountryWithGeographyTargeting(
            any(),
            any(),
            any(),
            any(),
            any(),
            classifCaptor.capture(),
            any(),
            any(),
            nullable(Long.class),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any(),
            any());

    List<String> captured = classifCaptor.getValue();
    assertNotNull(captured);
    assertEquals(1, captured.size());
    assertEquals("Classic", captured.get(0));
    assertFalse(captured.contains("Transit"));
  }

  @Test
  void explicitClassifications_budgetAllocationDerivationSkipped() {
    String runId = "run-explicit-classif";
    stubRunAndCompleteEarly(runId);

    // explicit classifications set — derivation must be skipped
    RecommendationRequestDTO req = buildRequest(Map.of("classic", 100.0), List.of("Digital"));

    service.processRecommendationsAsync(runId, "campaign-1", req);

    ArgumentCaptor<List> classifCaptor = ArgumentCaptor.forClass(List.class);
    verify(inventoryRepository)
        .findActiveInventoriesByCountryWithGeographyTargeting(
            any(),
            any(),
            any(),
            any(),
            any(),
            classifCaptor.capture(),
            any(),
            any(),
            nullable(Long.class),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any(),
            any());

    List<String> captured = classifCaptor.getValue();
    assertEquals(
        List.of("Digital"), captured, "Explicit classifications must override budget derivation");
  }

  @Test
  void noBudgetAllocation_classificationsNull() {
    String runId = "run-no-allocation";
    stubRunAndCompleteEarly(runId);

    RecommendationRequestDTO req = buildRequest(null, null);

    service.processRecommendationsAsync(runId, "campaign-1", req);

    ArgumentCaptor<List> classifCaptor = ArgumentCaptor.forClass(List.class);
    verify(inventoryRepository)
        .findActiveInventoriesByCountryWithGeographyTargeting(
            any(),
            any(),
            any(),
            any(),
            any(),
            classifCaptor.capture(),
            any(),
            any(),
            nullable(Long.class),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any(),
            any());

    assertNull(
        classifCaptor.getValue(), "No budgetAllocation → classifications must be null (fetch all)");
  }

  @Test
  void budgetAllocationKeys_caseNormalized() {
    String runId = "run-uppercase-key";
    stubRunAndCompleteEarly(runId);

    // Key sent as "Classic" (title case) instead of "classic"
    RecommendationRequestDTO req = buildRequest(Map.of("Classic", 100.0), null);

    service.processRecommendationsAsync(runId, "campaign-1", req);

    ArgumentCaptor<List> classifCaptor = ArgumentCaptor.forClass(List.class);
    verify(inventoryRepository)
        .findActiveInventoriesByCountryWithGeographyTargeting(
            any(),
            any(),
            any(),
            any(),
            any(),
            classifCaptor.capture(),
            any(),
            any(),
            nullable(Long.class),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any(),
            any());

    List<String> captured = classifCaptor.getValue();
    assertNotNull(captured);
    assertEquals(1, captured.size());
    assertEquals("Classic", captured.get(0), "Title-case key must produce correct classification");
  }
}
