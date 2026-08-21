package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for filtering campaign schedule prices. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Filter criteria for campaign schedule prices")
public class CampaignSchedulePriceFilterDTO {

  @Schema(description = "Inventory name to filter by")
  private String name;

  @Schema(description = "List of cities to filter by")
  private List<String> cities;

  @Schema(description = "List of inventory types to filter by")
  private List<String> inventoryTypes;

  @Schema(description = "List of media owner IDs to filter by")
  private List<String> mediaOwnerIds;

  @Schema(description = "Minimum pricing filter")
  private Double minPricing;

  @Schema(description = "Maximum pricing filter")
  private Double maxPricing;
}
