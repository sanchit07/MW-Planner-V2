package com.mw.recommendation.engine.v3.scoring;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import com.mw.recommendation.engine.v3.pipeline.V3RunContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * budgetFit per PRD §5.6. The campaign cost estimate is goal-aware and covers all pricing models:
 * CPM × estimated impressions, CPS (spot) × estimated ad plays, and flat monthly/daily/weekly
 * prorated to the window. The proportion cost/budget maps to the PRD buckets (95/75/50/30/10 at
 * ≤0.2/0.5/0.8/1.2 — configurable). No budget → neutral 50.
 */
@Component
@RequiredArgsConstructor
public class BudgetFitCalculator {

  private final V3Properties props;

  public record CostEstimate(
      BigDecimal cost, String currency, String costUnit, Long adPlays, Double costPerImpression) {
    public boolean present() {
      return cost != null && cost.signum() > 0;
    }
  }

  public record Result(double score, CostEstimate estimate) {}

  public Result calculate(Inventory inventory, V3RunContext ctx) {
    CostEstimate estimate = estimateCost(inventory, ctx);
    RecommendationV3RequestDTO request = ctx.getRequest();

    if (request.getBudget() == null || request.getBudget().signum() <= 0 || !estimate.present()) {
      return new Result(props.getBudget().getNeutralScore(), estimate);
    }

    double proportion =
        estimate.cost().divide(request.getBudget(), 4, RoundingMode.HALF_UP).doubleValue();
    for (double[] bucket : props.getBudget().getBuckets()) {
      if (proportion <= bucket[0]) {
        return new Result(bucket[1], estimate);
      }
    }
    return new Result(props.getBudget().getOverBucketScore(), estimate);
  }

  /**
   * Campaign-window cost estimate (PRD §5.6 / §6.5). Model priority is goal-aware: CPM first for
   * IMPRESSIONS/REACH, spot first for SOV/AD_PLAYS, then flat monthly/daily/weekly proration.
   */
  public CostEstimate estimateCost(Inventory inventory, V3RunContext ctx) {
    if (inventory.getPrices() == null || inventory.getPrices().isEmpty()) {
      return new CostEstimate(null, null, null, null, null);
    }
    Inventory.PriceModel price = inventory.getPrices().get(0);
    long days = ctx.campaignDays();
    V3RunContext.MeasureData measure = ctx.measureFor(inventory);
    Long impressions = measure != null ? measure.impressions() : null;
    Long adPlays = MeasureFitCalculator.adPlaysForWindow(inventory, days);
    RecommendationV3RequestDTO.CampaignGoal goal = ctx.getRequest().getGoal();

    boolean spotFirst =
        goal == RecommendationV3RequestDTO.CampaignGoal.SOV
            || goal == RecommendationV3RequestDTO.CampaignGoal.AD_PLAYS;

    CostEstimate estimate =
        spotFirst
            ? firstPresent(
                spotCost(price, adPlays), cpmCost(price, impressions), flatCost(price, days))
            : firstPresent(
                cpmCost(price, impressions), spotCost(price, adPlays), flatCost(price, days));

    if (estimate == null) {
      return new CostEstimate(null, price.getCurrency(), null, adPlays, null);
    }
    Double cpi = null;
    if (impressions != null && impressions > 0 && estimate.cost() != null) {
      cpi =
          estimate
              .cost()
              .divide(BigDecimal.valueOf(impressions), 6, RoundingMode.HALF_UP)
              .doubleValue();
    }
    return new CostEstimate(
        estimate.cost(), price.getCurrency(), estimate.costUnit(), adPlays, cpi);
  }

  private static CostEstimate cpmCost(Inventory.PriceModel price, Long impressions) {
    if (price.getCpm() == null || price.getCpm() <= 0 || impressions == null || impressions <= 0) {
      return null;
    }
    BigDecimal cost =
        BigDecimal.valueOf(price.getCpm() / 1000.0 * impressions).setScale(2, RoundingMode.HALF_UP);
    return new CostEstimate(cost, price.getCurrency(), "CPM", null, null);
  }

  private static CostEstimate spotCost(Inventory.PriceModel price, Long adPlays) {
    if (price.getSpot() == null || price.getSpot() <= 0 || adPlays == null || adPlays <= 0) {
      return null;
    }
    BigDecimal cost =
        BigDecimal.valueOf(price.getSpot() * adPlays).setScale(2, RoundingMode.HALF_UP);
    return new CostEstimate(cost, price.getCurrency(), "CPS", null, null);
  }

  private static CostEstimate flatCost(Inventory.PriceModel price, long days) {
    BigDecimal cost = null;
    if (price.getMonthly() != null && price.getMonthly() > 0) {
      cost = BigDecimal.valueOf(price.getMonthly() * days / 30.0);
    } else if (price.getDaily() != null && price.getDaily() > 0) {
      cost = BigDecimal.valueOf(price.getDaily() * days);
    } else if (price.getWeekly() != null && price.getWeekly() > 0) {
      cost = BigDecimal.valueOf(price.getWeekly() * days / 7.0);
    }
    return cost == null
        ? null
        : new CostEstimate(
            cost.setScale(2, RoundingMode.HALF_UP), price.getCurrency(), "FLAT", null, null);
  }

  private static CostEstimate firstPresent(CostEstimate... estimates) {
    for (CostEstimate estimate : estimates) {
      if (estimate != null) {
        return estimate;
      }
    }
    return null;
  }
}
