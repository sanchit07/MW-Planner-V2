package com.mw.recommendation.engine.v3.scoring;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import com.mw.recommendation.engine.v3.pipeline.V3RunContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * measureFit per PRD §5.3 — how well the inventory delivers the campaign goal.
 *
 * <ul>
 *   <li>IMPRESSIONS: min(impressions / goalValue, 1) × 100
 *   <li>REACH: min(reach / goalValue, 1) × 100
 *   <li>SOV: siteAdPlays / totalPossibleAdPlays × 100 (rescaled by goalValue when given)
 *   <li>AD_PLAYS: min(siteAdPlays / goalValue, 1) × 100
 *   <li>CARBON: null — measureFit is undefined; weight is redistributed
 *   <li>No goal: normalized per-day impressions against configurable per-market bounds (§5.2)
 * </ul>
 *
 * Returns null when the required inputs are missing (triggers weight redistribution).
 */
@Component
@RequiredArgsConstructor
public class MeasureFitCalculator {

  private final V3Properties props;
  private final TotalAdPlaysProvider totalAdPlaysProvider;

  public Double calculate(Inventory inventory, V3RunContext ctx) {
    RecommendationV3RequestDTO request = ctx.getRequest();
    V3RunContext.MeasureData measure = ctx.measureFor(inventory);
    RecommendationV3RequestDTO.CampaignGoal goal = request.getGoal();
    Long goalValue = request.getGoalValue();
    long days = ctx.campaignDays();

    if (goal == null) {
      // No-goal proxy (PRD §5.3): normalized per-day impressions with per-market bounds
      if (measure == null || measure.impressions() == null || measure.impressions() <= 0) {
        return null;
      }
      long[] bounds =
          props
              .getScoring()
              .getImpressionsNormBoundsByCountry()
              .getOrDefault(
                  request.getCountry(),
                  new long[] {
                    props.getScoring().getImpressionsNormMin(),
                    props.getScoring().getImpressionsNormMax()
                  });
      double perDay = measure.impressions() / (double) days;
      double normalized = (perDay - bounds[0]) / (double) Math.max(1, bounds[1] - bounds[0]);
      return clamp(normalized * 100.0);
    }

    return switch (goal) {
      case IMPRESSIONS -> {
        if (measure == null
            || measure.impressions() == null
            || goalValue == null
            || goalValue <= 0) {
          yield null;
        }
        yield Math.min(measure.impressions() / (double) goalValue, 1.0) * 100.0;
      }
      case REACH -> {
        if (measure == null || measure.reach() == null || goalValue == null || goalValue <= 0) {
          yield null;
        }
        yield Math.min(measure.reach() / (double) goalValue, 1.0) * 100.0;
      }
      case SOV -> {
        Long adPlays = adPlaysForWindow(inventory, days);
        Long total = totalAdPlaysProvider.totalPossibleAdPlays(request.getCountry(), days);
        if (adPlays == null || adPlays <= 0 || total == null || total <= 0) {
          yield null;
        }
        double sov = clamp(adPlays / (double) total * 100.0);
        if (goalValue != null && goalValue > 0) {
          sov = Math.min(sov / goalValue, 1.0) * 100.0;
        }
        yield sov;
      }
      case AD_PLAYS -> {
        Long adPlays = adPlaysForWindow(inventory, days);
        if (adPlays == null || adPlays <= 0 || goalValue == null || goalValue <= 0) {
          yield null;
        }
        yield Math.min(adPlays / (double) goalValue, 1.0) * 100.0;
      }
      case CARBON -> null;
    };
  }

  /** Ad plays over the window from loop math × daily operating hours (PRD §12.2). */
  public static Long adPlaysForWindow(Inventory inventory, long days) {
    Inventory.DigitalFields digital = inventory.getDigitalFields();
    if (digital == null) {
      return null;
    }
    Integer loopDuration = digital.getLoopDuration();
    Integer spotsPerLoop = digital.getSpotsPerLoop();
    if (loopDuration == null || loopDuration <= 0 || spotsPerLoop == null || spotsPerLoop <= 0) {
      return null;
    }
    int loopsPerHour = 3600 / loopDuration;
    long dailyHours = dailyOperatingHours(inventory);
    return (long) loopsPerHour * spotsPerLoop * dailyHours * days;
  }

  /** Average daily operating hours from operatingTimes; digital default 18h, else 24h. */
  public static long dailyOperatingHours(Inventory inventory) {
    if (inventory.getOperatingTimes() == null || inventory.getOperatingTimes().isEmpty()) {
      return inventory.getDigitalFields() != null ? 18 : 24;
    }
    long totalHours = 0;
    int daysWithData = 0;
    for (var entry : inventory.getOperatingTimes().entrySet()) {
      long hours = 0;
      for (Inventory.OperatingTime time : entry.getValue()) {
        hours += windowHours(time.getStart(), time.getEnd());
      }
      totalHours += hours;
      daysWithData++;
    }
    return daysWithData == 0 ? 24 : Math.max(1, totalHours / daysWithData);
  }

  static long windowHours(String start, String end) {
    try {
      int startHour = Integer.parseInt(start.substring(0, 2));
      int endHour = Integer.parseInt(end.substring(0, 2));
      if (endHour == 0 && startHour > 0) {
        endHour = 24; // "00:00:00" end = midnight
      }
      return endHour >= startHour ? endHour - startHour : (24 - startHour) + endHour;
    } catch (Exception e) {
      return 0;
    }
  }

  private static double clamp(double value) {
    return Math.max(0.0, Math.min(100.0, value));
  }
}
