package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;

import com.mw.recommendation.engine.config.MwRecommendationEngineProperties;
import com.mw.recommendation.engine.domain.AutoSelectionReasonCode;
import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyResponseDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Decision-table tests for {@link AutoSelectionReasonResolver}. The resolver is pure/read-only, so
 * every reason code is asserted directly from inputs mirroring the observable state after {@code
 * applyBudgetAwareAutoSelect} returns.
 */
class AutoSelectionReasonResolverTest {

  private static final double MIN_SCORE = 10.0;
  private static final LocalDate START = LocalDate.of(2025, 1, 1);
  private static final LocalDate END = LocalDate.of(2025, 1, 31);

  private MwRecommendationEngineProperties properties;
  private AutoSelectionReasonResolver resolver;

  @BeforeEach
  void setUp() {
    properties = new MwRecommendationEngineProperties();
    properties.getMeasure().setApiUrl("https://measure.example.com/api");
    resolver = new AutoSelectionReasonResolver(properties);
  }

  // ---- helpers ----

  private static RecommendationRequestDTO request(BigDecimal budget) {
    RecommendationRequestDTO req = new RecommendationRequestDTO();
    req.setCountry("US");
    req.setStartDate(START);
    req.setEndDate(END);
    req.setBudget(budget);
    return req;
  }

  private static RecommendationResult result(
      String id, Double score, BigDecimal estimatedCost, Long impressions, Long reach) {
    RecommendationResult r = new RecommendationResult();
    r.setInventoryId(id);
    r.setReferenceId("REF-" + id);
    r.setFinalScore(score);
    if (estimatedCost != null) {
      r.setCost(RecommendationResult.CostEstimate.builder().estimatedCost(estimatedCost).build());
    }
    if (impressions != null || reach != null) {
      r.setForecast(
          RecommendationResult.ForecastedMetrics.builder()
              .estimatedImpressions(impressions)
              .estimatedReach(reach)
              .build());
    }
    return r;
  }

  private static Map<String, MeasureReachFrequencyResponseDTO> usableMeasureRow(String refId) {
    return Map.of(
        refId,
        MeasureReachFrequencyResponseDTO.builder()
            .referenceId(refId)
            .status("success")
            .impressions(10_000L)
            .reach(5_000L)
            .build());
  }

  private AutoSelectionReasonResolver.ReasonResolution resolve(
      List<RecommendationResult> results,
      RecommendationRequestDTO req,
      List<String> selected,
      int sitesRequested,
      Map<String, MeasureReachFrequencyResponseDTO> measure) {
    return resolver.resolve(
        "v1", "run-1", results, req, selected, sitesRequested, measure, MIN_SCORE);
  }

  // ---- decision table ----

  @Test
  @DisplayName("any selection short-circuits to INVENTORIES_SELECTED")
  void selectedInventories() {
    RecommendationResult a = result("a", 90.0, new BigDecimal("100"), 1000L, 500L);
    AutoSelectionReasonResolver.ReasonResolution res =
        resolve(List.of(a), request(new BigDecimal("500")), List.of("a"), 1, Map.of());

    assertEquals(AutoSelectionReasonCode.INVENTORIES_SELECTED, res.code());
    assertEquals(1, res.diagnostics().getSelectedCount());
    assertEquals(1, res.diagnostics().getCandidateCount());
  }

  @Test
  @DisplayName("no candidates at all → NO_CANDIDATE_INVENTORIES")
  void noCandidates() {
    AutoSelectionReasonResolver.ReasonResolution res =
        resolve(List.of(), request(new BigDecimal("500")), List.of(), 0, Map.of());

    assertEquals(AutoSelectionReasonCode.NO_CANDIDATE_INVENTORIES, res.code());
    assertEquals(0, res.diagnostics().getCandidateCount());
  }

  @Test
  @DisplayName("neither budget nor goal → NO_BUDGET_NO_GOAL")
  void noBudgetNoGoal() {
    RecommendationResult a = result("a", 90.0, null, null, null);
    AutoSelectionReasonResolver.ReasonResolution res =
        resolve(List.of(a), request(null), List.of(), 1, Map.of());

    assertEquals(AutoSelectionReasonCode.NO_BUDGET_NO_GOAL, res.code());
  }

  @Test
  @DisplayName("missing campaign dates (defensive branch) → SELECTION_YIELDED_ZERO with detail")
  void missingDates() {
    RecommendationRequestDTO req = request(new BigDecimal("500"));
    req.setStartDate(null);
    RecommendationResult a = result("a", 90.0, null, null, null);

    AutoSelectionReasonResolver.ReasonResolution res =
        resolve(List.of(a), req, List.of(), 1, Map.of());

    assertEquals(AutoSelectionReasonCode.SELECTION_YIELDED_ZERO, res.code());
    assertTrue(res.detail().contains("startDate/endDate"));
  }

  @Test
  @DisplayName("goal without usable goalValue → NO_BUDGET_NO_GOAL with detail")
  void goalWithoutGoalValue() {
    RecommendationRequestDTO req = request(null);
    req.setGoal(RecommendationRequestDTO.CampaignGoal.REACH);
    req.setGoalValue(null);
    RecommendationResult a = result("a", 90.0, null, null, null);

    AutoSelectionReasonResolver.ReasonResolution res =
        resolve(List.of(a), req, List.of(), 1, Map.of());

    assertEquals(AutoSelectionReasonCode.NO_BUDGET_NO_GOAL, res.code());
    assertTrue(res.detail().contains("goalValue"));
  }

  @Test
  @DisplayName("AD_PLAYS goal with no budget → GOAL_ADPLAYS_NO_BUDGET")
  void adPlaysNoBudget() {
    RecommendationRequestDTO req = request(null);
    req.setGoal(RecommendationRequestDTO.CampaignGoal.AD_PLAYS);
    req.setGoalValue(500L);
    RecommendationResult a = result("a", 90.0, null, null, null);

    AutoSelectionReasonResolver.ReasonResolution res =
        resolve(List.of(a), req, List.of(), 1, Map.of());

    assertEquals(AutoSelectionReasonCode.GOAL_ADPLAYS_NO_BUDGET, res.code());
  }

  @Test
  @DisplayName("all candidates below min score → NO_CANDIDATE_INVENTORIES with detail")
  void allBelowMinScore() {
    RecommendationResult a = result("a", 5.0, null, null, null);
    RecommendationResult b = result("b", null, null, null, null);

    AutoSelectionReasonResolver.ReasonResolution res =
        resolve(List.of(a, b), request(new BigDecimal("500")), List.of(), 2, Map.of());

    assertEquals(AutoSelectionReasonCode.NO_CANDIDATE_INVENTORIES, res.code());
    assertTrue(res.detail().contains("0 of 2"));
    assertEquals(0, res.diagnostics().getScoredCount());
    assertEquals(2, res.diagnostics().getCandidateCount());
  }

  @Test
  @DisplayName("budget mode with zero usable Measure rows → MEASURE_DATA_UNAVAILABLE")
  void budgetMode_noMeasureData() {
    RecommendationResult a = result("a", 90.0, new BigDecimal("100"), null, null);

    AutoSelectionReasonResolver.ReasonResolution res =
        resolve(List.of(a), request(new BigDecimal("500")), List.of(), 1, Map.of());

    assertEquals(AutoSelectionReasonCode.MEASURE_DATA_UNAVAILABLE, res.code());
    assertEquals(0, res.diagnostics().getSitesWithReachFrequency());
  }

  @Test
  @DisplayName("budget mode: non-usable Measure rows (failed status) count as unavailable")
  void budgetMode_failedMeasureRowsAreNotUsable() {
    RecommendationResult a = result("a", 90.0, new BigDecimal("100"), null, null);
    Map<String, MeasureReachFrequencyResponseDTO> failedRow =
        Map.of(
            "REF-a",
            MeasureReachFrequencyResponseDTO.builder()
                .referenceId("REF-a")
                .status("failed")
                .build());

    AutoSelectionReasonResolver.ReasonResolution res =
        resolve(List.of(a), request(new BigDecimal("500")), List.of(), 1, failedRow);

    assertEquals(AutoSelectionReasonCode.MEASURE_DATA_UNAVAILABLE, res.code());
    assertTrue(res.diagnostics().getMeasureApiSucceeded(), "rows returned, just not usable");
    assertEquals(0, res.diagnostics().getSitesWithReachFrequency());
  }

  @Test
  @DisplayName("budget mode: cheapest estimated cost above budget → BUDGET_TOO_LOW")
  void budgetTooLow() {
    RecommendationResult a = result("a", 90.0, new BigDecimal("10000"), 1000L, 500L);

    AutoSelectionReasonResolver.ReasonResolution res =
        resolve(List.of(a), request(new BigDecimal("50")), List.of(), 1, usableMeasureRow("REF-a"));

    assertEquals(AutoSelectionReasonCode.BUDGET_TOO_LOW, res.code());
    assertEquals(new BigDecimal("10000"), res.diagnostics().getCheapestEstimatedCost());
  }

  @Test
  @DisplayName("budget mode: affordable candidates but still zero → SELECTION_YIELDED_ZERO")
  void budgetMode_yieldedZero() {
    RecommendationResult a = result("a", 90.0, new BigDecimal("20"), 1000L, 500L);

    AutoSelectionReasonResolver.ReasonResolution res =
        resolve(List.of(a), request(new BigDecimal("50")), List.of(), 1, usableMeasureRow("REF-a"));

    assertEquals(AutoSelectionReasonCode.SELECTION_YIELDED_ZERO, res.code());
  }

  @Test
  @DisplayName("goal-only REACH with zero usable Measure rows → MEASURE_DATA_UNAVAILABLE")
  void goalOnly_noMeasureData() {
    RecommendationRequestDTO req = request(null);
    req.setGoal(RecommendationRequestDTO.CampaignGoal.REACH);
    req.setGoalValue(100_000L);
    RecommendationResult a = result("a", 90.0, null, null, null);

    AutoSelectionReasonResolver.ReasonResolution res =
        resolve(List.of(a), req, List.of(), 1, Map.of());

    assertEquals(AutoSelectionReasonCode.MEASURE_DATA_UNAVAILABLE, res.code());
  }

  @Test
  @DisplayName("goal-only REACH: usable rows but achievable total below goal → GOAL_UNREACHABLE")
  void goalOnly_goalUnreachable() {
    RecommendationRequestDTO req = request(null);
    req.setGoal(RecommendationRequestDTO.CampaignGoal.REACH);
    req.setGoalValue(1_000_000L);
    RecommendationResult a = result("a", 90.0, null, 10_000L, 5_000L);

    AutoSelectionReasonResolver.ReasonResolution res =
        resolve(List.of(a), req, List.of(), 1, usableMeasureRow("REF-a"));

    assertEquals(AutoSelectionReasonCode.GOAL_UNREACHABLE, res.code());
    assertEquals(5_000L, res.diagnostics().getAchievableMetricTotal());
  }

  @Test
  @DisplayName(
      "goal-only IMPRESSIONS: achievable total meets goal but zero selected →"
          + " SELECTION_YIELDED_ZERO")
  void goalOnly_yieldedZeroDespiteAchievable() {
    RecommendationRequestDTO req = request(null);
    req.setGoal(RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);
    req.setGoalValue(5_000L);
    RecommendationResult a = result("a", 90.0, null, 10_000L, 5_000L);

    AutoSelectionReasonResolver.ReasonResolution res =
        resolve(List.of(a), req, List.of(), 1, usableMeasureRow("REF-a"));

    assertEquals(AutoSelectionReasonCode.SELECTION_YIELDED_ZERO, res.code());
    assertEquals(10_000L, res.diagnostics().getAchievableMetricTotal());
  }

  @Test
  @DisplayName("goal-only SOV zero selection → SELECTION_YIELDED_ZERO with SOV detail")
  void goalOnly_sov() {
    RecommendationRequestDTO req = request(null);
    req.setGoal(RecommendationRequestDTO.CampaignGoal.SOV);
    req.setGoalValue(50L);
    RecommendationResult a = result("a", 90.0, null, null, null);

    AutoSelectionReasonResolver.ReasonResolution res =
        resolve(List.of(a), req, List.of(), 1, Map.of());

    assertEquals(AutoSelectionReasonCode.SELECTION_YIELDED_ZERO, res.code());
    assertTrue(res.detail().contains("SOV"));
    assertNull(res.diagnostics().getAchievableMetricTotal(), "SOV total is not cheaply available");
  }

  // ---- diagnostics + robustness ----

  @Test
  @DisplayName("measureApiInvoked reflects configured URL and requested sites")
  void measureInvokedFlag() {
    RecommendationResult a = result("a", 90.0, null, null, null);

    AutoSelectionReasonResolver.ReasonResolution configured =
        resolve(List.of(a), request(null), List.of(), 3, Map.of());
    assertTrue(configured.diagnostics().getMeasureApiInvoked());
    assertFalse(configured.diagnostics().getMeasureApiSucceeded());
    assertEquals(3, configured.diagnostics().getSitesRequested());

    properties.getMeasure().setApiUrl("");
    AutoSelectionReasonResolver.ReasonResolution unconfigured =
        resolve(List.of(a), request(null), List.of(), 3, Map.of());
    assertFalse(unconfigured.diagnostics().getMeasureApiInvoked());
  }

  @Test
  @DisplayName("null selected list is treated as zero selection")
  void nullSelectedList() {
    RecommendationResult a = result("a", 90.0, null, null, null);
    AutoSelectionReasonResolver.ReasonResolution res =
        resolve(List.of(a), request(null), null, 1, Map.of());

    assertEquals(AutoSelectionReasonCode.NO_BUDGET_NO_GOAL, res.code());
    assertEquals(0, res.diagnostics().getSelectedCount());
  }

  @Test
  @DisplayName("resolver never throws — a broken input yields a null resolution, not an exception")
  void neverThrows() {
    AutoSelectionReasonResolver.ReasonResolution res =
        assertDoesNotThrow(
            () -> resolver.resolve("v1", "run-1", null, null, null, 0, null, MIN_SCORE));

    assertNull(res.code());
    assertNull(res.diagnostics());
  }
}
