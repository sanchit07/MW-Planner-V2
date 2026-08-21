package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for external payload containing campaign and inventories */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "External payload for ADS submission")
public class ExternalPayloadDTO {

  @JsonProperty("campaign")
  @Schema(description = "Campaign details")
  private ExternalCampaignDTO campaign;

  @JsonProperty("inventories")
  @Schema(description = "List of inventories")
  private List<ExternalInventoryDTO> inventories;
}
