package com.mw.recommendation.engine.v3.selection;

import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import com.mw.recommendation.engine.v3.pipeline.ScoredInventoryV3;
import com.mw.recommendation.engine.v3.pipeline.V3RunContext;
import com.mw.recommendation.engine.v3.scoring.MeasureFitCalculator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stage 5: auto-selection per PRD §12 / Parts B-D-H.
 *
 * <p>Decision tree: no budget + no goal → nothing selected (informational warning). Budget → bucket
 * allocation (H.2) with three rounds: (1) per-bucket score-descending selection with the
 * contribution tie-break (Part D: within 5 score points prefer higher goal contribution), (2)
 * unused budget pooled and redistributed to buckets that still have affordable candidates, (3)
 * leftover to global top scorers. Goal-only (no budget) → greedy by score until the cumulative
 * metric reaches the goal (IMPRESSIONS/REACH/AD_PLAYS/SOV; CARBON has no metric → no selection).
 *
 * <p>Cross-cutting: goal ceiling stops selection at 100% of goalValue for every metric goal (v1
 * checks only impressions/reach); feasibility pre-check emits GOAL_UNREACHABLE gap warnings;
 * DiversityEnforcer applies city caps (Part H.1) unless concentrateBudget; ToleranceValidator
 * reports under-allocation (Part F). Everything here is an in-memory pass over already-priced
 * candidates.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SelectionStage {

  private final BudgetAllocator allocator;
  private final DiversityEnforcer diversityEnforcer;
  private final ToleranceValidator toleranceValidator;
  private final V3Properties props;

  public void select(V3RunContext ctx) {
    List<ScoredInventoryV3> ranked = ctx.getScored();
    RecommendationV3RequestDTO request = ctx.getRequest();
    boolean hasBudget = request.getBudget() != null && request.getBudget().signum() > 0;
    boolean hasGoal = request.getGoal() != null;

    if (ranked.isEmpty() || (!hasBudget && !hasGoal)) {
      if (!hasBudget && !hasGoal) {
        ctx.getWarnings()
            .warn(
                "No budget or goal specified — recommendations ranked only, nothing"
                    + " auto-selected. Provide a budget or goal for an auto-plan.");
      }
      ctx.setSelectedSpend(Map.of());
      ctx.setAutoSelectedInventoryIds(List.of());
      return;
    }

    List<ScoredInventoryV3> eligible =
        ranked.stream()
            .filter(s -> s.score().getFinalScore() > props.getSelection().getMinScore())
            .toList();

    checkGoalFeasibility(ctx, eligible, hasBudget);

    Map<String, BigDecimal> selectedSpend =
        hasBudget ? budgetSelection(ctx, eligible) : goalOnlySelection(ctx, eligible);

    ctx.setSelectedSpend(selectedSpend);
    ctx.setAutoSelectedInventoryIds(new ArrayList<>(selectedSpend.keySet()));
    log.info(
        "v3 selection run {}: {} of {} eligible selected",
        ctx.getRunId(),
        selectedSpend.size(),
        eligible.size());
  }

  // ── Budget path (PRD §12.3 / H.2, rounds per v1 lineage) ─────────────────

  private Map<String, BigDecimal> budgetSelection(
      V3RunContext ctx, List<ScoredInventoryV3> eligible) {
    RecommendationV3RequestDTO request = ctx.getRequest();
    Map<String, Double> allocation = allocator.effectiveAllocation(request);
    Map<String, BigDecimal> bucketBudgets =
        allocator.bucketBudgets(request.getBudget(), allocation);

    Map<String, List<ScoredInventoryV3>> byBucket = new LinkedHashMap<>();
    for (ScoredInventoryV3 item : eligible) {
      if (item.cost() == null || !item.cost().present()) {
        continue; // unpriceable → cannot be budgeted
      }
      byBucket
          .computeIfAbsent(allocator.bucketOf(item.inventory()), k -> new ArrayList<>())
          .add(item);
    }
    byBucket.values().forEach(list -> list.sort(selectionOrder(ctx)));

    Map<String, BigDecimal> selected = new LinkedHashMap<>();
    long[] cumulativeMetric = {0L};

    // Round 1: per-bucket caps
    Map<String, BigDecimal> unusedPerBucket = new LinkedHashMap<>();
    for (Map.Entry<String, BigDecimal> entry : bucketBudgets.entrySet()) {
      String bucket = entry.getKey();
      BigDecimal remaining =
          selectFromList(
              byBucket.getOrDefault(bucket, List.of()),
              entry.getValue(),
              selected,
              cumulativeMetric,
              ctx);
      unusedPerBucket.put(bucket, remaining);
      if (goalMet(ctx, cumulativeMetric[0])) {
        break;
      }
    }

    // Round 2: pool unused budget, revisit buckets by original allocation descending
    if (!goalMet(ctx, cumulativeMetric[0])) {
      BigDecimal pool = unusedPerBucket.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
      if (pool.signum() > 0) {
        List<String> bucketsByAllocation =
            allocation.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
        for (String bucket : bucketsByAllocation) {
          pool =
              selectFromList(
                  byBucket.getOrDefault(bucket, List.of()), pool, selected, cumulativeMetric, ctx);
          if (pool.signum() <= 0 || goalMet(ctx, cumulativeMetric[0])) {
            break;
          }
        }

        // Round 3: whatever is left → global top scorers regardless of bucket
        if (pool.signum() > 0 && !goalMet(ctx, cumulativeMetric[0])) {
          List<ScoredInventoryV3> global = new ArrayList<>(eligible);
          global.sort(selectionOrder(ctx));
          selectFromList(global, pool, selected, cumulativeMetric, ctx);
        }
      }
    }

    // Diversity (Part H.1) then tolerance report (Part F)
    List<ScoredInventoryV3> selectedItems =
        eligible.stream()
            .filter(s -> selected.containsKey(s.inventory().getInventoryId()))
            .toList();
    List<ScoredInventoryV3> unselectedItems =
        eligible.stream()
            .filter(s -> !selected.containsKey(s.inventory().getInventoryId()))
            .toList();
    Map<String, BigDecimal> diversified =
        diversityEnforcer.enforce(
            selected,
            selectedItems,
            unselectedItems,
            Boolean.TRUE.equals(request.getConcentrateBudget()),
            ctx.getWarnings());

    BigDecimal totalSpend = diversified.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    toleranceValidator.validate(
        request.getBudget(), request.getBudgetCurrency(), totalSpend, ctx.getWarnings());

    if (diversified.isEmpty()) {
      cheapestUnaffordableWarning(ctx, eligible);
    }
    return diversified;
  }

  /** Selects affordably from a pre-sorted list; returns the remaining budget. */
  private BigDecimal selectFromList(
      List<ScoredInventoryV3> candidates,
      BigDecimal budget,
      Map<String, BigDecimal> selected,
      long[] cumulativeMetric,
      V3RunContext ctx) {
    BigDecimal remaining = budget;
    for (ScoredInventoryV3 candidate : candidates) {
      if (goalMet(ctx, cumulativeMetric[0])) {
        break;
      }
      String id = candidate.inventory().getInventoryId();
      if (selected.containsKey(id) || candidate.cost() == null || !candidate.cost().present()) {
        continue;
      }
      BigDecimal cost = candidate.cost().cost();
      if (cost.compareTo(remaining) <= 0) {
        selected.put(id, cost);
        remaining = remaining.subtract(cost);
        cumulativeMetric[0] += goalMetric(candidate, ctx);
      }
    }
    return remaining;
  }

  // ── Goal-only path (no budget) ───────────────────────────────────────────

  private Map<String, BigDecimal> goalOnlySelection(
      V3RunContext ctx, List<ScoredInventoryV3> eligible) {
    RecommendationV3RequestDTO request = ctx.getRequest();
    Long goalValue = request.getGoalValue();
    if (request.getGoal() == RecommendationV3RequestDTO.CampaignGoal.CARBON
        || goalValue == null
        || goalValue <= 0) {
      ctx.getWarnings()
          .warn(
              "Goal-only auto-selection needs a measurable goal value"
                  + (request.getGoal() == RecommendationV3RequestDTO.CampaignGoal.CARBON
                      ? " (CARBON has no per-inventory metric yet — CO2 data pending)"
                      : "")
                  + "; nothing auto-selected");
      return Map.of();
    }

    List<ScoredInventoryV3> sorted = new ArrayList<>(eligible);
    sorted.sort(selectionOrder(ctx));

    Map<String, BigDecimal> selected = new LinkedHashMap<>();
    long cumulative = 0;
    for (ScoredInventoryV3 candidate : sorted) {
      long metric = goalMetric(candidate, ctx);
      if (metric <= 0) {
        continue;
      }
      selected.put(
          candidate.inventory().getInventoryId(),
          candidate.cost() != null && candidate.cost().present()
              ? candidate.cost().cost()
              : BigDecimal.ZERO);
      cumulative += metric;
      if (cumulative >= goalValue) {
        break;
      }
    }
    if (cumulative < goalValue && !selected.isEmpty()) {
      ctx.getWarnings()
          .warn(
              String.format(
                  "Selected inventories forecast %,d against a goal of %,d (%.0f%%)",
                  cumulative, goalValue, 100.0 * cumulative / goalValue));
    }
    return selected;
  }

  // ── Shared helpers ───────────────────────────────────────────────────────

  /**
   * Score-descending order with the Part D contribution tie-break: within the configured window
   * (default 5 points) the higher goal contribution wins.
   */
  private Comparator<ScoredInventoryV3> selectionOrder(V3RunContext ctx) {
    double window = props.getSelection().getContributionTieBreakWindow();
    return (a, b) -> {
      double scoreA = a.score().getFinalScore();
      double scoreB = b.score().getFinalScore();
      if (Math.abs(scoreA - scoreB) <= window && ctx.getRequest().getGoal() != null) {
        int byContribution = Long.compare(goalMetric(b, ctx), goalMetric(a, ctx));
        if (byContribution != 0) {
          return byContribution;
        }
      }
      return Double.compare(scoreB, scoreA);
    };
  }

  /** Per-inventory contribution toward the goal metric. */
  private long goalMetric(ScoredInventoryV3 candidate, V3RunContext ctx) {
    RecommendationV3RequestDTO.CampaignGoal goal = ctx.getRequest().getGoal();
    if (goal == null) {
      return 0;
    }
    V3RunContext.MeasureData measure = ctx.measureFor(candidate.inventory());
    return switch (goal) {
      case IMPRESSIONS ->
          measure != null && measure.impressions() != null ? measure.impressions() : 0;
      case REACH -> measure != null && measure.reach() != null ? measure.reach() : 0;
      case AD_PLAYS, SOV -> {
        Long plays =
            MeasureFitCalculator.adPlaysForWindow(candidate.inventory(), ctx.campaignDays());
        yield plays != null ? plays : 0;
      }
      case CARBON -> 0;
    };
  }

  private boolean goalMet(V3RunContext ctx, long cumulative) {
    Long goalValue = ctx.getRequest().getGoalValue();
    RecommendationV3RequestDTO.CampaignGoal goal = ctx.getRequest().getGoal();
    if (goal == null || goalValue == null || goalValue <= 0) {
      return false;
    }
    // SOV goal value is a percentage of total plays, not an absolute count
    if (goal == RecommendationV3RequestDTO.CampaignGoal.SOV) {
      return false; // cumulative SOV ceiling handled via feasibility warning only
    }
    return cumulative >= goalValue;
  }

  /** PRD Part G scenario 4 + §14.3: feasibility gap warnings before selection. */
  private void checkGoalFeasibility(
      V3RunContext ctx, List<ScoredInventoryV3> eligible, boolean hasBudget) {
    RecommendationV3RequestDTO request = ctx.getRequest();
    Long goalValue = request.getGoalValue();
    if (request.getGoal() == null || goalValue == null || goalValue <= 0) {
      return;
    }
    long potential = 0;
    for (ScoredInventoryV3 candidate : eligible) {
      potential += goalMetric(candidate, ctx);
    }
    if (request.getGoal() != RecommendationV3RequestDTO.CampaignGoal.SOV && potential < goalValue) {
      ctx.getWarnings()
          .warn(
              String.format(
                  "GOAL_UNREACHABLE: available inventory can deliver at most %,d of the %,d"
                      + " %s goal (%.0f%%). Consider expanding geography or dates%s",
                  potential,
                  goalValue,
                  request.getGoal(),
                  100.0 * potential / goalValue,
                  hasBudget ? " or increasing budget" : ""));
    }
  }

  private void cheapestUnaffordableWarning(V3RunContext ctx, List<ScoredInventoryV3> eligible) {
    eligible.stream()
        .filter(s -> s.cost() != null && s.cost().present())
        .map(s -> s.cost().cost())
        .min(Comparator.naturalOrder())
        .ifPresent(
            cheapest ->
                ctx.getWarnings()
                    .warn(
                        "BUDGET_TOO_LOW: budget "
                            + ctx.getRequest().getBudget()
                            + " is below the cheapest available inventory cost ("
                            + cheapest
                            + "); nothing auto-selected"));
  }
}
