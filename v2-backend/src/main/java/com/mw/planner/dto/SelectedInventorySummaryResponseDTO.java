package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Slim response DTO for selected campaign inventory with performance metrics. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Slim summary of a selected campaign inventory")
public class SelectedInventorySummaryResponseDTO {

  @Schema(description = "Envelope inventory ID from the external inventory system")
  private String inventoryId;

  @Schema(description = "User readable reference to the external system")
  private String referenceId;

  @Schema(description = "Performance metrics and calculations")
  private CampaignInventoryFilterResponseDTO.Performance performance;
}
