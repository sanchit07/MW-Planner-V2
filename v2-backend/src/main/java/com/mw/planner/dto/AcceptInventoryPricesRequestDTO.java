package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for accepting inventory prices */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(
    description =
        "Request DTO for accepting inventory prices for selected CampaignInventorySchedules")
public class AcceptInventoryPricesRequestDTO {
  @Schema(
      description =
          "List of CampaignInventorySchedules IDs to accept prices for. If null/empty, accepts all CampaignInventorySchedules for the campaign",
      example = "[\"cis123\", \"cis456\"]")
  private List<String> campaignInventorySchedulesIds = new ArrayList<>();
}
