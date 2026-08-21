package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Cost Split By Response DTO")
public class CostSplitByResponseDTO {
  @Schema(description = "name")
  private String name;

  @Schema(description = "Total amount")
  private Double totalAmount;

  @Schema(description = "Total amount in percentage")
  private Double totalAmountInPercentage;

  @Schema(description = "Estimated impressions")
  private Long impressions;

  @Schema(description = "Estimated reach")
  private Long reach;

  @Schema(description = "Estimated frequency")
  private Double frequency;

  @Schema(description = "Average CPM")
  private Double avgCpm;

  @Schema(description = "Population (only available for COUNTRY, STATE, or CITY)")
  private Long population;

  @Schema(description = "Total number of inventories in the group")
  private Integer totalInventories;
}
