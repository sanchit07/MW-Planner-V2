package com.mw.recommendation.engine.service;

import com.mw.recommendation.engine.config.MwRecommendationEngineProperties;
import com.mw.recommendation.engine.domain.AutoSelectionReasonCode;
import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.domain.RecommendationRun;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyResponseDTO;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Derives WHY auto-selection ended with the selection it did — strictly AFTER {@code
 * applyBudgetAwareAutoSelect} has returned, and strictly from its observable inputs and outputs
 * (candidate results, request budget/goal, the pipeline-level Measure batch response, and the
 * selected ids). It reconstructs the selection branch from the outside; it never instruments or
 * influences selection, and it never throws (a diagnostics bug must not fail a run).
 *
 * <p>Measure signal caveat: selection internally enriches schedules via {@code
 * ScheduleRecommendationService}, whose Measure call is not observable here. The pipeline-level
 * batch ({@code fetchMeasureReachFrequency}) uses the same client, referenceIds, duration and Redis
 * cache, so its response map is used as an honest proxy for Measure availability.
 *
 * <p>MEASURE_DATA_UNAVAILABLE vs GOAL_UNREACHABLE: schedule {@code estimatedImpressions}/{@code
 * estimatedReach} are populated ONLY from successful Measure rows with positive impressions and
 * reach, and both the goal-only accumulation and the budget-mode {@code hasValidMeasureData} gate
 * consume them — so zero usable Measure rows deterministically yields zero selection and is checked
 * first. GOAL_UNREACHABLE requires usable Measure data whose summed metric is still below
 * goalValue. Note that in goal-only mode the greedy loop selects any schedule whose metric fits the
 * remaining goal, so a zero-selection run with usable data usually means every individual estimate
 * exceeded goalValue (goal too small — SELECTION_YIELDED_ZERO); GOAL_UNREACHABLE there chiefly
 * flags divergence between the pipeline-level Measure data and the selection-time enrichment.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutoSelectionReasonResolver {

  private final MwRecommendationEngineProperties properties;

  /** Immutable outcome of a resolution: code + detail + diagnostics snapshot. */
  public record ReasonResolution(
      AutoSelectionReasonCode code,
      String detail,
      RecommendationRun.AutoSelectionDiagnostics diagnostics) {}

  /**
   * Resolve the auto-selection reason for a completed run. Read-only; never throws.
   *
   * @param pipeline "v1" or "v2" (log labelling only)
   * @param runId run being completed (log labelling only)
   * @param results the exact result list handed to {@code applyBudgetAwareAutoSelect}
   * @param request the original recommendation request
   * @param autoSelectedIds what auto-selection returned (null treated as empty)
   * @param sitesRequested inventories sent to the pipeline-level Measure batch call
   * @param measureResponseByRefId the pipeline-level Measure batch response map
   * @param minScoreThreshold the service's MIN_RECOMMENDATION_SCORE
   */
  public ReasonResolution resolve(
      String pipeline,
      String runId,
      List<RecommendationResult> results,
      RecommendationRequestDTO request,
      List<String> autoSelectedIds,
      int sitesRequested,
      Map<String, MeasureReachFrequencyResponseDTO> measureResponseByRefId,
      double minScoreThreshold) {
    try {
      ReasonResolution resolution =
          doResolve(
              results,
              request,
              autoSelectedIds,
              sitesRequested,
              measureResponseByRefId,
              minScoreThreshold);
      logResolution(pipeline, runId, resolution);
      return resolution;
    } catch (Exception e) {
      log.warn(
          "[AUTO-SELECT-REASON] runId={} pipeline={} reason derivation failed: {}",
          runId,
          pipeline,
          e.getMessage(),
          e);
      return new ReasonResolution(null, null, null);
    }
  }

  private ReasonResolution doResolve(
      List<RecommendationResult> results,
      RecommendationRequestDTO request,
      List<String> autoSelectedIds,
      int sitesRequested,
      Map<String, MeasureReachFrequencyResponseDTO> measureResponseByRefId,
      double minScoreThreshold) {

    List<RecommendationResult> candidates = results != null ? results : List.of();
    int selectedCount = autoSelectedIds != null ? autoSelectedIds.size() : 0;

    List<RecommendationResult> qualified =
        candidates.stream()
            .filter(r -> r.getFinalScore() != null && r.getFinalScore() > minScoreThreshold)
            .toList();

    boolean hasBudget =
        request.getBudget() != null && request.getBudget().compareTo(BigDecimal.ZERO) > 0;
    RecommendationRequestDTO.CampaignGoal goal = request.getGoal();
    long goalValue =
        (request.getGoalValue() != null && request.getGoalValue() > 0)
            ? request.getGoalValue()
            : 0L;

    int usableMeasureRows = countUsableMeasureRows(measureResponseByRefId);
    String measureApiUrl =
        properties.getMeasure() != null ? properties.getMeasure().getApiUrl() : null;
    boolean measureConfigured = measureApiUrl != null && !measureApiUrl.isBlank();

    Long achievableMetricTotal = achievableMetricTotal(qualified, goal);
    BigDecimal cheapestEstimatedCost = cheapestEstimatedCost(qualified);

    RecommendationRun.AutoSelectionDiagnostics diagnostics =
        RecommendationRun.AutoSelectionDiagnostics.builder()
            .candidateCount(candidates.size())
            .scoredCount(qualified.size())
            .budget(request.getBudget())
            .cheapestEstimatedCost(cheapestEstimatedCost)
            .goalType(goal != null ? goal.name() : null)
            .goalValue(request.getGoalValue())
            .achievableMetricTotal(achievableMetricTotal)
            .measureApiInvoked(measureConfigured && sitesRequested > 0)
            .measureApiSucceeded(
                measureResponseByRefId != null && !measureResponseByRefId.isEmpty())
            .sitesRequested(sitesRequested)
            .sitesWithReachFrequency(usableMeasureRows)
            .selectedCount(selectedCount)
            .build();

    AutoSelectionReasonCode code;
    String detail;

    if (selectedCount > 0) {
      code = AutoSelectionReasonCode.INVENTORIES_SELECTED;
      detail = selectedCount + " of " + candidates.size() + " candidates auto-selected";
    } else if (candidates.isEmpty()) {
      code = AutoSelectionReasonCode.NO_CANDIDATE_INVENTORIES;
      detail = "no scored candidate inventories reached auto-selection";
    } else if (!hasBudget && goal == null) {
      code = AutoSelectionReasonCode.NO_BUDGET_NO_GOAL;
      detail = "request has neither budget nor goal; auto-selection not applicable";
    } else if (request.getStartDate() == null || request.getEndDate() == null) {
      code = AutoSelectionReasonCode.SELECTION_YIELDED_ZERO;
      detail = "campaign startDate/endDate missing; auto-selection skipped";
    } else if (!hasBudget
        && goal != null
        && goal != RecommendationRequestDTO.CampaignGoal.AD_PLAYS
        && goalValue <= 0) {
      code = AutoSelectionReasonCode.NO_BUDGET_NO_GOAL;
      detail = "goal " + goal + " present but goalValue missing/non-positive; selection skipped";
    } else if (!hasBudget && goal == RecommendationRequestDTO.CampaignGoal.AD_PLAYS) {
      code = AutoSelectionReasonCode.GOAL_ADPLAYS_NO_BUDGET;
      detail = "goal AD_PLAYS without budget performs no auto-selection by design";
    } else if (qualified.isEmpty()) {
      code = AutoSelectionReasonCode.NO_CANDIDATE_INVENTORIES;
      detail =
          "0 of "
              + candidates.size()
              + " candidates above minimum recommendation score "
              + minScoreThreshold;
    } else if (hasBudget) {
      if (usableMeasureRows == 0) {
        code = AutoSelectionReasonCode.MEASURE_DATA_UNAVAILABLE;
        detail =
            "budget-aware selection requires Measure impressions/reach; 0 of "
                + sitesRequested
                + " sites had usable Measure data";
      } else if (cheapestEstimatedCost != null
          && cheapestEstimatedCost.compareTo(request.getBudget()) > 0) {
        code = AutoSelectionReasonCode.BUDGET_TOO_LOW;
        detail =
            "cheapest candidate estimated cost "
                + cheapestEstimatedCost
                + " exceeds budget "
                + request.getBudget();
      } else {
        code = AutoSelectionReasonCode.SELECTION_YIELDED_ZERO;
        detail =
            "budget-aware selection yielded zero despite "
                + qualified.size()
                + " qualified candidates and usable Measure data (e.g. per-category allocation or"
                + " schedule basePrice constraints)";
      }
    } else if (goal == RecommendationRequestDTO.CampaignGoal.SOV) {
      code = AutoSelectionReasonCode.SELECTION_YIELDED_ZERO;
      detail = "SOV goal selection yielded zero: no candidate had SOV (ad-plays) data";
    } else {
      // Goal-only IMPRESSIONS/REACH
      if (usableMeasureRows == 0) {
        code = AutoSelectionReasonCode.MEASURE_DATA_UNAVAILABLE;
        detail =
            "goal-only selection requires Measure "
                + goal
                + " estimates; 0 of "
                + sitesRequested
                + " sites had usable Measure data";
      } else if (achievableMetricTotal != null && achievableMetricTotal < goalValue) {
        code = AutoSelectionReasonCode.GOAL_UNREACHABLE;
        detail =
            "sum of achievable "
                + goal
                + " across qualified candidates ("
                + achievableMetricTotal
                + ") is below goalValue "
                + goalValue;
      } else {
        code = AutoSelectionReasonCode.SELECTION_YIELDED_ZERO;
        detail =
            "goal-only selection yielded zero despite usable Measure data (every candidate's"
                + " individual estimate likely exceeds goalValue "
                + goalValue
                + ")";
      }
    }

    return new ReasonResolution(code, detail, diagnostics);
  }

  /** Rows selection can actually consume: status success with positive impressions AND reach. */
  private static int countUsableMeasureRows(
      Map<String, MeasureReachFrequencyResponseDTO> measureResponseByRefId) {
    if (measureResponseByRefId == null || measureResponseByRefId.isEmpty()) {
      return 0;
    }
    return (int)
        measureResponseByRefId.values().stream()
            .filter(Objects::nonNull)
            .filter(r -> "success".equalsIgnoreCase(r.getStatus()))
            .filter(r -> r.getImpressions() != null && r.getImpressions() > 0)
            .filter(r -> r.getReach() != null && r.getReach() > 0)
            .count();
  }

  private static Long achievableMetricTotal(
      List<RecommendationResult> qualified, RecommendationRequestDTO.CampaignGoal goal) {
    if (goal != RecommendationRequestDTO.CampaignGoal.IMPRESSIONS
        && goal != RecommendationRequestDTO.CampaignGoal.REACH) {
      return null;
    }
    return qualified.stream()
        .map(RecommendationResult::getForecast)
        .filter(Objects::nonNull)
        .map(
            f ->
                goal == RecommendationRequestDTO.CampaignGoal.IMPRESSIONS
                    ? f.getEstimatedImpressions()
                    : f.getEstimatedReach())
        .filter(Objects::nonNull)
        .mapToLong(Long::longValue)
        .sum();
  }

  private static BigDecimal cheapestEstimatedCost(List<RecommendationResult> qualified) {
    return qualified.stream()
        .map(RecommendationResult::getCost)
        .filter(Objects::nonNull)
        .map(RecommendationResult.CostEstimate::getEstimatedCost)
        .filter(Objects::nonNull)
        .filter(c -> c.compareTo(BigDecimal.ZERO) > 0)
        .min(Comparator.naturalOrder())
        .orElse(null);
  }

  private void logResolution(String pipeline, String runId, ReasonResolution resolution) {
    RecommendationRun.AutoSelectionDiagnostics d = resolution.diagnostics();
    String message =
        "[AUTO-SELECT-REASON] runId={} pipeline={} code={} candidates={} qualified={} budget={}"
            + " goal={} goalValue={} measureSites={}/{} achievableTotal={} cheapestCost={}"
            + " selected={} detail={}";
    Object[] args = {
      runId,
      pipeline,
      resolution.code(),
      d != null ? d.getCandidateCount() : null,
      d != null ? d.getScoredCount() : null,
      d != null ? d.getBudget() : null,
      d != null ? d.getGoalType() : null,
      d != null ? d.getGoalValue() : null,
      d != null ? d.getSitesWithReachFrequency() : null,
      d != null ? d.getSitesRequested() : null,
      d != null ? d.getAchievableMetricTotal() : null,
      d != null ? d.getCheapestEstimatedCost() : null,
      d != null ? d.getSelectedCount() : null,
      resolution.detail()
    };
    if (resolution.code() == AutoSelectionReasonCode.INVENTORIES_SELECTED) {
      log.info(message, args);
    } else {
      log.warn(message, args);
    }
  }
}
