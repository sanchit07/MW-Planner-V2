package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for campaign forecast data")
public class CampaignForecastDTO {

  @Schema(description = "Total selected Inventories", example = "50")
  private Integer totalInventories;

  @Schema(description = "Estimated total impressions", example = "1000000")
  private Long estimatedImpression;

  @Schema(description = "Estimated total reach", example = "50000")
  private Long estimatedReach;

  @Schema(description = "Estimated frequency", example = "2.5")
  private Double estimatedFrequency;

  @Schema(description = "Estimated total ad plays", example = "5000")
  private Long estimatedAdPlays;

  @Schema(description = "Share of voice", example = "15.5")
  private Double sov;

  @Schema(description = "Average cost per mille", example = "0.0")
  private Double avgCpm;

  @Schema(description = "Average effective cost per mille", example = "0.0")
  private Double avgECpm;

  @Schema(description = "Total cost", example = "10000.50")
  private Double totalCost;

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

  @Schema(description = "Warning messages for failed inventory calls")
  private List<String> warnings;
}
