package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for Budget Performance Summary API. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Budget performance summary: date-wise budget, total cost, and remaining")
public class BudgetPerformanceSummaryResponse {

  @Schema(description = "Map of date (yyyy-MM-dd) to budget summary (budget, totalCost, remaining)")
  private Map<String, BudgetSummary> dateWiseSchedulePerDateRate;

  @Schema(description = "Currency code for the currency used in the budget performance summary")
  private String currencyCode;

  @Schema(description = "Sum of all date-wise budgets (when type is cost)")
  private Double totalBudget;

  @Schema(description = "Sum of all date-wise budgets for the previous period (when type is cost)")
  private Double lastPeriodTotalBudget;

  @Schema(description = "Sum of all date-wise costs for the previous period (when type is cost)")
  private Double lastPeriodTotalCost;

  @Schema(description = "Sum of all date-wise costs (when type is cost)")
  private Double totalCost;

  @Schema(description = "Sum of all date-wise remaining budgets (when type is cost)")
  private Double remainingBudget;

  @Schema(description = "Average revenue per campaign for selected period (when type is cost)")
  private Double averageRevenuePerUnit;

  @Schema(
      description =
          "Average revenue per campaign for previous period matching selected duration (when type is cost)")
  private Double lastPeriodAverageRevenuePerUnit;

  @Schema(
      description =
          "Conversion rate percentage = (completed + approved + active campaigns) / total campaigns * 100 (when type is cost)")
  private Double conversionRate;

  @Schema(
      description =
          "Previous period conversion rate percentage using same formula (when type is cost)")
  private Double lastPeriodConversionRate;

  @Schema(description = "Total revenue for selected period (when type is cost)")
  private Double totalRevenue;

  @Schema(
      description =
          "Total revenue for previous period matching selected duration (when type is cost)")
  private Double lastPeriodTotalRevenue;
}
