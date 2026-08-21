package com.mw.planner.dto;

import com.mw.planner.validation.ValidSelectCampaignInventory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for selecting/deselecting campaign inventory with SOV value */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ValidSelectCampaignInventory
@Schema(description = "Request DTO for selecting or deselecting campaign inventory")
public class SelectCampaignInventoryRequestDTO {

  @Schema(hidden = true, description = "Campaign ID (set internally from path variable)")
  private String campaignId;

  @NotBlank(message = "validation.inventory_id_required")
  @Schema(description = "Inventory ID", example = "inventory_456")
  private String inventoryId;

  @NotNull(message = "validation.operation_type_required")
  @Schema(description = "Operation type: SELECT or DESELECT", example = "SELECT")
  private OperationType operationType;

  @Schema(
      description =
          "Pre-supplied impressions value. When both impressions and reach are provided, the"
              + " Measure API call is skipped and these values are used directly.",
      example = "8014680")
  private Long impressions;

  @Schema(
      description =
          "Pre-supplied reach value. When both impressions and reach are provided, the"
              + " Measure API call is skipped and these values are used directly.",
      example = "447747")
  private Long reach;

  public enum OperationType {
    SELECT,
    DESELECT
  }
}
