package com.mw.recommendation.engine.v3.domain;

import com.mw.recommendation.engine.domain.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Schedule generated for an auto-selected v3 inventory: the validated, CPM-optimized booking matrix
 * plus the metrics backing the selection decision (PRD §12.4 / Part E).
 */
@Document(collection = "run_schedule_recommendation_v3")
@CompoundIndex(name = "v3_run_inventory_idx", def = "{'runId': 1, 'inventoryId': 1}")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class RunScheduleV3 extends BaseEntity<String> {

  @Indexed private String runId;
  private String campaignId;
  private String inventoryId;
  private String referenceId;
  private String scheduleId;

  private LocalDate startDate;
  private LocalDate endDate;

  /** ISO date string → selected hours (0-23) for that date. Empty list = full-day (classic). */
  private Map<String, List<Integer>> bookingMatrix;

  private Long adPlays;
  private Integer plannedSot;
  private Integer totalSot;
  private Integer spotsPerLoop;
  private Integer spotsPerHour;
  private Integer durationSeconds;

  private BigDecimal basePrice;
  private String currency;

  private Long estimatedImpressions;
  private Long estimatedReach;

  /** True when selling terms forced an adjustment (e.g. extended to minimum days). */
  private Boolean adjustedForSellingTerms;

  private List<String> adjustments;
}
