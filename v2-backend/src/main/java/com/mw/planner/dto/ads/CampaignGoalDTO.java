package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for campaign goal */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Campaign goal details")
public class CampaignGoalDTO {

  @JsonProperty("type")
  @Schema(description = "Goal type", example = "IMPRESSIONS")
  private String type;

  @JsonProperty("targetValue")
  @Schema(description = "Target value", example = "25000000")
  private Double targetValue;
}
