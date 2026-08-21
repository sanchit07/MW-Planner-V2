package com.mw.recommendation.engine.domain;

/**
 * Why auto-selection ended with the selection it did — derived read-only AFTER {@code
 * applyBudgetAwareAutoSelect} returns, from its observable inputs/outputs (candidates, request
 * budget/goal, pipeline-level Measure result, selected ids). Persisted on {@link RecommendationRun}
 * so zero-selection runs are queryable; never influences selection itself.
 */
public enum AutoSelectionReasonCode {
  /** Auto-selection selected at least one inventory. */
  INVENTORIES_SELECTED,
  /** Neither budget nor goal (or a goal without a usable goalValue) — selection not applicable. */
  NO_BUDGET_NO_GOAL,
  /** Goal is AD_PLAYS with no budget — this mode performs no selection by design. */
  GOAL_ADPLAYS_NO_BUDGET,
  /** No scored candidates reached selection (none fetched, or none above the minimum score). */
  NO_CANDIDATE_INVENTORIES,
  /** Budget mode: the cheapest candidate's estimated cost exceeds the total budget. */
  BUDGET_TOO_LOW,
  /** Goal mode: Measure data exists but the summed achievable metric is below goalValue. */
  GOAL_UNREACHABLE,
  /** The Measure API returned no usable reach/frequency rows, so selection had nothing to pick. */
  MEASURE_DATA_UNAVAILABLE,
  /** Candidates, budget/goal and data were present, yet selection still yielded zero. */
  SELECTION_YIELDED_ZERO
}
