package com.mw.planner.controller;

import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.ApprovalInboxItemDTO;
import com.mw.planner.dto.CampaignApprovalDetailsResponseDTO;
import com.mw.planner.dto.CampaignApprovalStatusUpdateRequestDTO;
import com.mw.planner.service.CampaignApprovalWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** Controller for managing campaign approval workflow operations. */
@Slf4j
@RestController
@RequestMapping("/api/v1/campaign-approval-workflow")
@RequiredArgsConstructor
@Tag(
    name = "Campaign Approval Workflow",
    description = "Operations for managing campaign approval workflow and status")
@SecurityRequirement(name = "bearerAuth")
public class CampaignApprovalWorkflowController {

  private final CampaignApprovalWorkflowService campaignApprovalWorkflowService;

  @PostMapping("/{campaignId}/submit-for-review")
  @Operation(
      summary = "Submit campaign for review",
      description =
          "Submits a campaign for review by moving it to REVIEWING status and creating approval workflow steps")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Campaign submitted successfully for review",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": \"Campaign submitted successfully for review\"}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request or campaign cannot be submitted",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<String> submitCampaignForReview(
      @Parameter(description = "Campaign ID to submit") @PathVariable String campaignId) {
    log.info("Submitting campaign for review: {}", campaignId);
    campaignApprovalWorkflowService.submitCampaignForReview(campaignId);
    return ApiResponse.success("Campaign submitted successfully for review");
  }

  /**
   * Update approval status for a campaign workflow stage (Approve / Reject / Change Request).
   *
   * @param workflowStatusId Campaign Approved Workflow Status ID from
   *     campaign_approved_workflow_status collection
   * @param request Approval status update request
   * @return Success response
   */
  @PutMapping("/approval-status/{workflowStatusId}")
  @Operation(
      summary = "Update Campaign Approval Status",
      description =
          "Update approval status for a campaign workflow stage using the workflow status ID. Supports Approve, Reject, and Change Request actions for Agency, Internal, and Media Owner stages.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Approval status updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Workflow status not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<String> updateApprovalStatus(
      @Parameter(
              description = "Campaign Approved Workflow Status ID",
              example = "workflowStatus123")
          @PathVariable
          String workflowStatusId,
      @Valid @RequestBody CampaignApprovalStatusUpdateRequestDTO request) {

    log.info(
        "Updating approval status for workflowStatusId: {}, status: {}",
        workflowStatusId,
        request.getStatus());

    // Update approval status
    campaignApprovalWorkflowService.updateApprovalStatus(workflowStatusId, request);

    log.info(
        "Successfully updated approval status for workflowStatusId: {}, status: {}",
        workflowStatusId,
        request.getStatus());

    String successMessage =
        String.format(
            "Successfully updated approval status for workflow: status: %s", request.getStatus());
    return ApiResponse.success(successMessage);
  }

  /**
   * Get campaign approval details by campaign ID.
   *
   * @param campaignId Campaign ID
   * @return Campaign approval details response
   */
  @GetMapping("/inbox")
  @Operation(
      summary = "Get Plan Approval inbox",
      description =
          "Lists campaigns in an active approval cycle (REVIEWING/NEGOTIATING) that involve the"
              + " current company, with the awaiting stage and whether the viewer can act")
  public ApiResponse<List<ApprovalInboxItemDTO>> getApprovalInbox() {
    return ApiResponse.success(campaignApprovalWorkflowService.getApprovalInbox());
  }

  @GetMapping("/{campaignId}/approval-details")
  @Operation(
      summary = "Get Campaign Approval Details",
      description =
          "Retrieves comprehensive approval details for a campaign including workflow status, approval progress, and comments from approval history")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Campaign approval details retrieved successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CampaignApprovalDetailsResponseDTO.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<CampaignApprovalDetailsResponseDTO> getCampaignApprovalDetails(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId) {
    log.info("Fetching campaign approval details for campaignId: {}", campaignId);
    CampaignApprovalDetailsResponseDTO details =
        campaignApprovalWorkflowService.getCampaignApprovalDetails(campaignId);
    return ApiResponse.success(details);
  }
}
