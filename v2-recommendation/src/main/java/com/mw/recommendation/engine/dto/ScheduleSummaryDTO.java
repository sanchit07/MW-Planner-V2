package com.mw.recommendation.engine.dto;

import com.mw.recommendation.engine.domain.Inventory;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Schedule summary data (no run/inventory identity). Used when building and returning schedules.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleSummaryDTO {
  private String scheduleId;
  private LocalDate scheduleStartDate;
  private LocalDate scheduleEndDate;
  private Map<String, List<Integer>> bookingMatrix; // date -> hours
  private Long adPlays;
  private Double plannedSot;
  private Double totalSot;
  private Long spotsPerLoop;
  private Long spotsPerHour;
  private Long duration;
  private Double basePrice;
  private Long estimatedImpressions;
  private Long estimatedReach;
  private String currency;
  // Same field/shape as RecommendationResult.InventoryDetails.sellingTerm — the Optimization
  // step's schedule data source didn't expose this even though minDays already drives Round 1's
  // day-based schedule sizing internally.
  private Inventory.SellingTerm sellingTerm;
}
