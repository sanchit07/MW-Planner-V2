package com.mw.recommendation.engine.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Stored recommended schedule for one inventory in a recommendation run. Planner fetches all
 * schedules for a runId via schedule API and syncs to its own DB. One document per (runId,
 * inventoryId).
 */
@Document(collection = "run_schedule_recommendations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class RunScheduleRecommendation extends BaseEntity<String> {

  @Indexed private String runId;
  @Indexed private String campaignId;
  @Indexed private String inventoryId;

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
  private Inventory.SellingTerm sellingTerm;
}
