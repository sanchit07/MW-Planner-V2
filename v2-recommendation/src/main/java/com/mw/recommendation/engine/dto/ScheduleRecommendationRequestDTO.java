package com.mw.recommendation.engine.dto;

import com.mw.recommendation.engine.dto.RecommendationRequestDTO.CampaignGoal;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for generating schedule recommendations for specific inventories */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleRecommendationRequestDTO {

  @NotEmpty(message = "Inventory IDs are required")
  private List<String> inventoryIds;

  @NotNull(message = "Start date is required")
  private LocalDate startDate;

  @NotNull(message = "End date is required")
  private LocalDate endDate;

  private Integer creativeDuration; // seconds (optional, defaults to inventory's spotDuration)

  private String
      bookingType; // "guaranteed" or "non-guaranteed" (optional, defaults to "guaranteed")

  /**
   * Optional budget share for this request (e.g. for one inventory). Used when budget is provided.
   */
  private BigDecimal budgetShare;

  /** Optional campaign goal for schedule optimization (IMPRESSIONS, REACH, SOV). */
  private CampaignGoal goal;

  /** Optional run ID for context/caching. */
  private String runId;
}
