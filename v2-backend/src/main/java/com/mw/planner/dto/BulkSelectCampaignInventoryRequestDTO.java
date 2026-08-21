package com.mw.planner.dto;

import com.mw.planner.validation.ValidBulkSelectCampaignInventory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for bulk selecting/deselecting multiple campaign inventories. Provide exactly one of
 * {@code inventoryIds} or {@code referenceIds} - supplying both (or neither) is rejected.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ValidBulkSelectCampaignInventory
@Schema(description = "Request DTO for bulk selecting or deselecting campaign inventories by IDs")
public class BulkSelectCampaignInventoryRequestDTO {

  @Schema(hidden = true, description = "Campaign ID (set internally from path variable)")
  private String campaignId;

  @Schema(
      description =
          "List of inventory IDs to select or deselect. Provide either inventoryIds or "
              + "referenceIds, not both.",
      example = "[\"inventory_456\", \"inventory_789\"]")
  private List<String> inventoryIds;

  @Schema(
      description =
          "List of reference IDs to select or deselect. Provide either inventoryIds or "
              + "referenceIds, not both.",
      example = "[\"ref_456\", \"ref_789\"]")
  private List<String> referenceIds;

  @NotNull(message = "validation.operation_type_required")
  @Schema(description = "Operation type: SELECT or DESELECT", example = "SELECT", required = true)
  private SelectCampaignInventoryRequestDTO.OperationType operationType;
}
