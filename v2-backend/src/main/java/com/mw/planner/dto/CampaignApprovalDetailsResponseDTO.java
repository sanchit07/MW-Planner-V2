package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignApprovedWorkflowStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for campaign approval details")
public class CampaignApprovalDetailsResponseDTO {

  @Schema(description = "Campaign name", example = "Summer Campaign 2024")
  private String campaignName;

  @Schema(description = "Campaign ID", example = "campaign123")
  private String campaignId;

  @Schema(description = "Human-readable plan number of the campaign", example = "202607210001")
  private String planNumber;

  @Schema(description = "Campaign status", example = "REVIEWING")
  private Campaign.Status status;

  @Schema(description = "Campaign budget", example = "50000.00")
  private Double budget;

  @Schema(description = "Campaign currency", example = "USD")
  private String currency;

  @Schema(description = "List of approval progress from workflow status")
  private List<ApprovalProgressDTO> approvalProgress;

  @Schema(description = "List of approval permissions based on logged in user")
  private List<ApprovalPermission> approvalPermissions;

  @Schema(description = "Workflow status", example = "IN_PROGRESS")
  private String workflowStatus;

  @Schema(
      description = "Start date of the campaign in ISO 8601 format",
      example = "2024-06-01T00:00:00Z")
  private LocalDate startDate;

  @Schema(
      description = "End date of the campaign in ISO 8601 format",
      example = "2024-06-30T23:59:59Z")
  private LocalDate endDate;

  @Schema(description = "Whether the campaign's inventory prices have been negotiated/accepted")
  private Boolean isNegotiated;

  @Schema(
      description =
          "Per-media-owner approval progress for the Media Owner stage. Populated only for"
              + " buyer-side viewers (creator / shared access / global admin); media owners never"
              + " see other owners' state.")
  private List<ApprovalInboxItemDTO.MediaOwnerProgressDTO> mediaOwners;

  @Schema(
      description =
          "The viewing media owner's own slice of the plan: proposal status, inventory count and"
              + " media cost (rate-card, no buyer fees). Null for buyer-side viewers.")
  private ApprovalInboxItemDTO.MediaOwnerProgressDTO viewerProposal;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Approval progress details for each authority")
  public static class ApprovalProgressDTO {
    @Schema(description = "Workflow status ID", example = "workflowStatus123")
    private String id;

    @Schema(description = "Approval status", example = "IN_PROGRESS")
    private CampaignApprovedWorkflowStatus.Status status;

    @Schema(description = "Approval authority", example = "AGENCY")
    private CampaignApprovedWorkflowStatus.ApprovalAuthority approvalAuthority;

    @Schema(description = "Comment from approval history", example = "Campaign approved by agency")
    private String comment;

    @Schema(description = "User who created the workflow status", example = "user123")
    private String createdBy;

    @Schema(description = "Creation timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "User who updated the workflow status", example = "user123")
    private String updatedBy;

    @Schema(description = "Update timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
  }

  public enum ApprovalPermission {
    AGENCY,
    INTERNAL,
    MEDIA_OWNER,
  }
}
