package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Budget summary for a single date (budget, total cost, remaining, reach, impressions). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
    description = "Budget summary for a date: budget, total cost, remaining, reach, impressions")
public class BudgetSummary {

  @Schema(description = "Budget amount for the date", example = "45677.78")
  private Double budget;

  @Schema(description = "cost for the date", example = "45559.12")
  private Double cost;

  @Schema(description = "Remaining budget (budget - totalCost)", example = "65678.32")
  private Double remaining;

  @Schema(description = "Aggregated reach for the date (when type is reach or not specified)")
  private Double reach;

  @Schema(description = "Aggregated impressions for the date (when type is reach or not specified)")
  private Double impressions;
}
