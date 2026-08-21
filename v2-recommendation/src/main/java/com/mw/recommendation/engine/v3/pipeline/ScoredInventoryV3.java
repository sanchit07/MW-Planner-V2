package com.mw.recommendation.engine.v3.pipeline;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.scoring.AvailabilityCalculator;
import com.mw.recommendation.engine.v3.scoring.BudgetFitCalculator;
import com.mw.recommendation.engine.v3.scoring.V3Score;

/**
 * An inventory with its full scoring output plus the availability/cost details computed during
 * scoring (reused when building the persisted result — no recomputation downstream).
 */
public record ScoredInventoryV3(
    Inventory inventory,
    V3Score score,
    AvailabilityCalculator.Result availability,
    BudgetFitCalculator.CostEstimate cost,
    double finalScoreWithJitter) {

  public ScoredInventoryV3 withJitteredScore(double jittered) {
    return new ScoredInventoryV3(inventory, score, availability, cost, jittered);
  }
}
