package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for bulk campaign actions request. Contains the list of campaign IDs and the action to be
 * performed on them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for bulk campaign actions")
public class CampaignBulkActionRequestDTO {

  @NotEmpty(message = "validation.campaign_ids_not_empty")
  @Schema(
      description = "List of campaign IDs to perform the action on",
      example = "[\"campaign1\", \"campaign2\", \"campaign3\"]",
      required = true)
  private List<String> campaignIds;

  @NotNull(message = "validation.action_type_required")
  @Schema(
      description = "The action to be performed on the campaigns",
      example = "DUPLICATE",
      required = true)
  private CampaignAction action;
}
