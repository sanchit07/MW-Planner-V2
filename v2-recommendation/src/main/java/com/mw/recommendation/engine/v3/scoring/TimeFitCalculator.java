package com.mw.recommendation.engine.v3.scoring;

import com.mw.recommendation.engine.domain.AudienceData;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * timeFit per PRD §5.10: Σ(user_daypart_weight × inventory_daypart_percent) normalized to 0-100,
 * where inventory_daypart_percent is the share of the inventory's daily audience falling inside
 * each requested window (from the hourly traffic curve). No dayparts requested or no hourly data →
 * 50 neutral (timeFit carries a deliberately low weight in Phase 1).
 */
@Component
public class TimeFitCalculator {

  public Double calculate(
      AudienceData audience, List<RecommendationV3RequestDTO.Daypart> dayparts) {

    if (dayparts == null || dayparts.isEmpty()) {
      return 50.0;
    }
    Map<Integer, AudienceData.HourlySummary> hourly =
        audience != null ? audience.getHourlySummary() : null;
    if (hourly == null || hourly.isEmpty()) {
      return 50.0;
    }

    long dailyTotal = 0;
    for (AudienceData.HourlySummary summary : hourly.values()) {
      if (summary.getTotalVisitors() != null) {
        dailyTotal += summary.getTotalVisitors();
      }
    }
    if (dailyTotal <= 0) {
      return 50.0;
    }

    double weightedShare = 0.0;
    double totalWeight = 0.0;
    for (RecommendationV3RequestDTO.Daypart daypart : dayparts) {
      if (daypart.getStartHour() == null || daypart.getEndHour() == null) {
        continue;
      }
      double weight =
          daypart.getWeight() != null && daypart.getWeight() > 0 ? daypart.getWeight() : 1.0;
      long windowVisitors = 0;
      for (int hour = daypart.getStartHour(); hour < daypart.getEndHour() && hour < 24; hour++) {
        AudienceData.HourlySummary summary = hourly.get(hour);
        if (summary != null && summary.getTotalVisitors() != null) {
          windowVisitors += summary.getTotalVisitors();
        }
      }
      weightedShare += weight * (windowVisitors / (double) dailyTotal);
      totalWeight += weight;
    }
    if (totalWeight <= 0) {
      return 50.0;
    }
    return Math.max(0.0, Math.min(100.0, weightedShare / totalWeight * 100.0));
  }
}
