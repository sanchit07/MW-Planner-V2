package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mw.brand.lib.dto.BrandResponseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for campaign Media Plan details")
public class CampaignMediaPlanResponseDTO {

  @Schema(description = "Header Information")
  @JsonProperty("headerInfo")
  private HeaderInfoDTO headerInfoDTO;

  @Schema(description = "Brand Details")
  @JsonProperty("brandDetails")
  private BrandResponseDTO brandResponseDTO;

  @Schema(description = "Performance Metrics")
  @JsonProperty("performanceMetrics")
  private CampaignForecastDTO campaignForecast;

  @Schema(description = "Targeting Strategy Details")
  @JsonProperty("audienceDemographics")
  private AudienceDemographicsTargetingStrategyDTO audienceDemographicsTargetingStrategyDTO;

  @Schema(description = "Schedules Details")
  @JsonProperty("schedules")
  private SchedulesDTO schedulesDTO;
}
