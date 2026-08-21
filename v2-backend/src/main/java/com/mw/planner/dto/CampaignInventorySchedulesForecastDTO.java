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
@Schema(description = "DTO for campaign inventory forecast")
public class CampaignInventorySchedulesForecastDTO {

  @Schema(description = "Estimated total ad plays", example = "5000")
  private Long estimatedAdPlays;

  @Schema(description = "Share of voice", example = "15.5")
  private Double sov;

  @Schema(
      description =
          "Planned share of time (sum of spotDuration * total booked slots for all inventories)",
      example = "5000.0")
  private Double plannedSot;

  @Schema(
      description =
          "Total share of time (sum of availableHours * campaign duration days for all inventories)",
      example = "10000.0")
  private Double totalSot;
}
