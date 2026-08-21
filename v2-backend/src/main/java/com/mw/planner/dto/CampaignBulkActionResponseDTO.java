package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for bulk campaign actions response. Contains information about the results of the bulk action
 * operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for bulk campaign actions")
public class CampaignBulkActionResponseDTO {

  @Schema(description = "Total number of campaigns processed", example = "5")
  private int totalProcessed;

  @Schema(description = "Number of campaigns successfully processed", example = "4")
  private int successCount;

  @Schema(description = "Number of campaigns that failed to process", example = "1")
  private int failureCount;

  @Schema(description = "List of campaign IDs that were successfully processed")
  private List<String> successfulCampaignIds;

  @Schema(description = "List of campaign IDs that failed to process")
  private List<String> failedCampaignIds;

  @Schema(description = "List of error messages for failed campaigns")
  private List<String> errorMessages;

  @Schema(description = "List of newly created campaign IDs (for duplicate action)")
  private List<String> newCampaignIds;
}
