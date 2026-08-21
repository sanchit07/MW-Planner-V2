package com.mw.planner.dto;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignApprovedWorkflowStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row of the Plan Approval inbox: a campaign whose approval workflow involves the viewer. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A campaign awaiting approval action, as seen by the current company")
public class ApprovalInboxItemDTO {

  private String campaignId;
  private String campaignName;
  private String planNumber;

  @Schema(description = "Campaign status as resolved for the viewer")
  private Campaign.Status status;

  @Schema(description = "Overall approval workflow status label")
  private String workflowStatus;

  private Double budget;
  private String currency;
  private LocalDate startDate;
  private LocalDate endDate;
  private Boolean isNegotiated;

  @Schema(description = "The stage currently awaiting action, if any")
  private CampaignApprovedWorkflowStatus.ApprovalAuthority awaitingAuthority;

  @Schema(description = "Whether the viewer can act on the awaiting stage")
  private boolean canAct;

  @Schema(description = "Workflow-status id to PUT an approval decision against when canAct")
  private String actionProgressId;

  @Schema(description = "Approval authorities the viewer holds for this campaign")
  private List<CampaignApprovalDetailsResponseDTO.ApprovalPermission> permissions;

  @Schema(
      description =
          "True when the campaign has changed prices not yet accepted — approval actions are"
              + " blocked until prices are accepted in Price Management")
  private boolean hasUnacceptedPrices;

  @Schema(
      description =
          "True when the viewer's company participates in this plan as a media owner (an external"
              + " plan created by another company). Buyer-only financials (budget with fees) are"
              + " withheld for such viewers.")
  private boolean viewerIsMediaOwner;

  @Schema(description = "Name of the company that created the plan (shown to media owners)")
  private String createdByCompanyName;

  @Schema(
      description =
          "Per-media-owner approval progress. Populated only for buyer-side viewers (creator /"
              + " shared access / global admin); media owners never see other owners' state.")
  private List<MediaOwnerProgressDTO> mediaOwners;

  @Schema(
      description =
          "The viewing media owner's own slice of the plan: proposal status, inventory count and"
              + " media cost (rate-card, no buyer fees). Null for buyer-side viewers.")
  private MediaOwnerProgressDTO viewerProposal;

  /** One media owner's progress on a plan: proposal status + their slice of the plan. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "One media owner's approval progress on the plan")
  public static class MediaOwnerProgressDTO {
    private String mediaOwnerId;
    private String mediaOwnerName;

    @Schema(description = "Proposal status: PENDING, APPROVED, NEGOTIATING or REJECTED")
    private String status;

    @Schema(description = "Number of this owner's inventories in the plan")
    private int inventoryCount;

    @Schema(description = "Sum of this owner's schedule media costs (rate card, no fees)")
    private Double mediaCost;

    @Schema(
        description =
            "True when this owner has price changes awaiting the counterparty's acceptance"
                + " (an open counter offer)")
    private boolean hasOpenCounterOffer;
  }
}
