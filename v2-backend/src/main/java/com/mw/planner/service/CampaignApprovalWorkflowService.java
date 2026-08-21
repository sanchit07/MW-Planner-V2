package com.mw.planner.service;

import static com.mw.planner.constants.CampaignActivityKey.*;

import com.mw.planner.domain.*;
import com.mw.planner.dto.ApprovalInboxItemDTO;
import com.mw.planner.dto.CampaignApprovalDetailsResponseDTO;
import com.mw.planner.dto.CampaignApprovalStatusUpdateRequestDTO;
import com.mw.planner.dto.CompanyLookupResponseDTO;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.dto.UserResponseDTO;
import com.mw.planner.dto.ads.AdsSubmissionResponseDTO;
import com.mw.planner.enums.CampaignApprovalStatus;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.ads.AdsApiException;
import com.mw.planner.exception.campaign.CampaignNotApprovedForAdsException;
import com.mw.planner.exception.campaign.CampaignNotFoundException;
import com.mw.planner.exception.campaign.CampaignValidationException;
import com.mw.planner.exception.campaign.WorkflowInvalidStatusException;
import com.mw.planner.exception.proposal.ProposalNotFoundException;
import com.mw.planner.repository.CampaignApprovalHistoryRepository;
import com.mw.planner.repository.CampaignApprovedWorkflowStatusRepository;
import com.mw.planner.repository.CampaignInventorySchedulesRepository;
import com.mw.planner.repository.CampaignProposalStatusRepository;
import com.mw.planner.repository.CampaignRepository;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class CampaignApprovalWorkflowService {
  private final CampaignService campaignService;
  private final CampaignRepository campaignRepository;
  private final CampaignInventorySchedulesRepository campaignInventorySchedulesRepository;
  private final CampaignProposalStatusRepository campaignProposalStatusRepository;
  private final CampaignApprovedWorkflowStatusRepository campaignApprovedWorkflowStatusRepository;
  private final CampaignApprovalHistoryRepository campaignApprovalHistoryRepository;
  private final MWAdsService mwAdsService;
  private final CampaignProposalStatusAndCommentService campaignProposalStatusAndCommentService;
  private final UserService userService;
  private final CampaignActivityService campaignActivityService;
  private final CompanyService companyService;
  private final com.mw.planner.repository.ScheduleRepository scheduleRepository;
  private final TestModeService testModeService;

  /**
   * Whether the 2-stage company-side approval flow is enabled. When true the Agency stage is
   * skipped, so non-media-owner users see 2 stages (Internal → Media Owner); when false the legacy
   * 3-stage flow is used. Media-owner users always see a single stage regardless.
   *
   * <p>Hardcoded to {@code true} for now (the previous {@code
   * mw-planner.approval.two-stage-enabled} property has been removed). This method remains the
   * single toggle point so the 3-stage flow can be re-enabled later without threading a flag
   * through the call sites.
   */
  private boolean isTwoStageEnabled() {
    return true;
  }

  /**
   * Whether the given company is a MEDIA_OWNER per IAM's company lookup. Fails safe to {@code
   * false} on a null/unresolvable company or an IAM lookup error, so a transient IAM issue falls
   * back to the standard 2-stage flow rather than breaking the caller.
   */
  /**
   * Resolve a company's display name via IAM, memoized in the given per-request cache so each
   * unique company id is looked up at most once per call (inbox batching).
   */
  private String resolveCompanyName(String companyId, Map<String, String> cache) {
    if (companyId == null || companyId.isBlank()) {
      return null;
    }
    String cached = cache.get(companyId);
    if (cached != null) {
      return cached;
    }
    String resolved = resolveCompanyName(companyId);
    cache.put(companyId, resolved != null ? resolved : companyId);
    return resolved;
  }

  /** Resolve a company's display name via IAM, falling back to the raw id. */
  private String resolveCompanyName(String companyId) {
    if (companyId == null || companyId.isBlank()) {
      return null;
    }
    try {
      CompanyLookupResponseDTO company = companyService.getCompanyLookupWithCompanyId(companyId);
      return company != null && StringUtils.hasText(company.getName())
          ? company.getName()
          : companyId;
    } catch (Exception e) {
      log.warn("Could not resolve company name for {}: {}", companyId, e.getMessage());
      return companyId;
    }
  }

  /**
   * Fail-closed company-type check for the financial persona boundary: when the company's type
   * cannot be resolved (null lookup, missing type, or IAM error), the company is treated as a media
   * owner so buyer financials stay redacted. Never use this for permission grants — only for
   * withholding buyer-side data.
   */
  private boolean isCompanyMediaOwnerFailClosed(String companyId) {
    if (companyId == null || companyId.isBlank()) {
      return false;
    }
    try {
      CompanyLookupResponseDTO companyDto = companyService.getCompanyLookupWithCompanyId(companyId);
      return companyDto == null
          || companyDto.getCompanyType() == null
          || "MEDIA_OWNER".equals(companyDto.getCompanyType());
    } catch (Exception e) {
      log.warn(
          "Could not resolve company type for companyId: {}. Failing closed (treating as media"
              + " owner) for financial redaction.",
          companyId,
          e);
      return true;
    }
  }

  /**
   * Like {@link #isCompanyMediaOwner(String)} but strict: returns {@link Optional#empty()} when the
   * IAM lookup fails, so security-sensitive callers can fail closed instead of silently treating an
   * unresolvable company as buyer-side.
   */
  private Optional<Boolean> isCompanyMediaOwnerStrict(String companyId) {
    if (companyId == null || companyId.isBlank()) {
      return Optional.of(false);
    }
    try {
      CompanyLookupResponseDTO companyDto = companyService.getCompanyLookupWithCompanyId(companyId);
      if (companyDto == null || companyDto.getCompanyType() == null) {
        // Unresolvable type — same fail-closed semantics as isCompanyMediaOwnerFailClosed.
        return Optional.empty();
      }
      return Optional.of("MEDIA_OWNER".equals(companyDto.getCompanyType()));
    } catch (Exception e) {
      log.warn(
          "Could not resolve company type for companyId: {} (strict lookup, failing closed).",
          companyId,
          e);
      return Optional.empty();
    }
  }

  private boolean isCompanyMediaOwner(String companyId) {
    if (companyId == null || companyId.isBlank()) {
      return false;
    }
    try {
      CompanyLookupResponseDTO companyDto = companyService.getCompanyLookupWithCompanyId(companyId);
      return companyDto != null && "MEDIA_OWNER".equals(companyDto.getCompanyType());
    } catch (Exception e) {
      log.warn(
          "Could not resolve company type for companyId: {}. Defaulting to non-media-owner.",
          companyId,
          e);
      return false;
    }
  }

  // Submits a campaign for review by updating its status and creating approval workflow steps and
  // proposal status entries
  public void submitCampaignForReview(String campaignId) {
    log.info("Submitting campaign for review: {}", campaignId);

    // Fetch campaign to check status
    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);

    // Validate mandatory fields before finalizing
    validateCampaignForReview(campaign);

    // Validation 1: Check if logged-in user is campaign creator
    String currentUserId = userService.getIamUserContext().getId();
    if (!campaign.getUserId().equals(currentUserId)) {
      log.warn(
          "Campaign {} cannot be submitted for review. Current user: {}, Campaign creator: {}",
          campaignId,
          currentUserId,
          campaign.getUserId());
      throw new CampaignValidationException(ErrorCode.CAMPAIGN_SUBMIT_NOT_CREATOR);
    }

    // Validation 2: Check if campaign status is PLANNED or NEGOTIATING
    if (campaign.getStatus() != Campaign.Status.PLANNED
        && campaign.getStatus() != Campaign.Status.NEGOTIATING) {
      log.warn(
          "Campaign {} cannot be submitted for review. Current status: {}, Required status: PLANNED or NEGOTIATING",
          campaignId,
          campaign.getStatus());
      throw new CampaignValidationException(
          ErrorCode.CAMPAIGN_SUBMIT_INVALID_STATUS, campaign.getStatus().name());
    }

    // Validation 3: If status is NEGOTIATING, validate all schedules are approved
    if (campaign.getStatus() == Campaign.Status.NEGOTIATING) {
      List<CampaignInventorySchedules> campaignInventorySchedules =
          campaignInventorySchedulesRepository.findByCampaignId(campaignId);

      for (CampaignInventorySchedules schedule : campaignInventorySchedules) {
        List<String> scheduleIds = schedule.getScheduleIds();
        List<String> approvedScheduleIds = schedule.getApprovedScheduleIds();

        // Handle null cases
        int scheduleIdsSize = (scheduleIds != null) ? scheduleIds.size() : 0;
        int approvedScheduleIdsSize =
            (approvedScheduleIds != null) ? approvedScheduleIds.size() : 0;

        if (scheduleIdsSize != approvedScheduleIdsSize) {
          log.warn(
              "Campaign {} cannot be submitted for review. CampaignInventorySchedules {} has {} scheduleIds but only {} approvedScheduleIds",
              campaignId,
              schedule.getId(),
              scheduleIdsSize,
              approvedScheduleIdsSize);
          throw new CampaignValidationException(ErrorCode.CAMPAIGN_SUBMIT_PRICES_NOT_APPROVED);
        }
      }
    }

    // Update campaign status to REVIEWING
    campaignService.changeCampaignStatus(campaignId, Campaign.Status.REVIEWING);

    // Reuse existing workflow if present (e.g. resubmit after REJECTED→PLANNED); otherwise create
    List<CampaignApprovedWorkflowStatus> existingWorkflow =
        campaignApprovedWorkflowStatusRepository.findByCampaignId(campaignId);
    if (existingWorkflow.isEmpty()) {
      boolean mediaOwnerCreated = isCompanyMediaOwner(campaign.getCompanyId());
      createApprovalWorkflowSteps(campaignId, mediaOwnerCreated);
      createProposalStatusEntries(campaignId);
    }
  }

  /**
   * Validates that all mandatory campaign fields are present and valid before finalizing.
   *
   * @param campaign Campaign to validate
   * @throws CampaignValidationException if any mandatory field is missing or invalid
   */
  private void validateCampaignForReview(Campaign campaign) {
    log.debug("Validating campaign for review: {}", campaign.getId());

    if (campaign.getCountryId() == null || campaign.getCountryId().trim().isEmpty()) {
      log.warn("Campaign {} validation failed: countryId is required", campaign.getId());
      throw new CampaignValidationException("countryId", null);
    }

    long inventoryCount = campaignInventorySchedulesRepository.countByCampaignId(campaign.getId());
    if (inventoryCount == 0) {
      log.warn(
          "Campaign {} validation failed: at least one inventory must be selected",
          campaign.getId());
      throw new CampaignValidationException("at least one inventory must be selected");
    }
  }

  // Creates approval workflow steps for Agency, Internal, and Media Owner authorities.
  // All three documents are always created so the data shape stays constant; each authority's
  // entry status is resolved by initialStatusFor, which also encodes the media-owner-created
  // bypass (Agency + Internal skipped, Media Owner is the entry stage).
  private void createApprovalWorkflowSteps(String campaignId, boolean mediaOwnerCreated) {
    List<CampaignApprovedWorkflowStatus> workflowSteps =
        Arrays.asList(
            createWorkflowStep(
                campaignId,
                CampaignApprovedWorkflowStatus.ApprovalAuthority.AGENCY,
                initialStatusFor(
                    CampaignApprovedWorkflowStatus.ApprovalAuthority.AGENCY, mediaOwnerCreated)),
            createWorkflowStep(
                campaignId,
                CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL,
                initialStatusFor(
                    CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL, mediaOwnerCreated)),
            createWorkflowStep(
                campaignId,
                CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER,
                initialStatusFor(
                    CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER,
                    mediaOwnerCreated)));

    campaignApprovedWorkflowStatusRepository.saveAll(workflowSteps);
    log.debug("Created {} workflow steps for campaign: {}", workflowSteps.size(), campaignId);
  }

  /**
   * Resolves the entry/reset status for an authority. When {@code mediaOwnerCreated} is true (the
   * campaign's owning company is a MEDIA_OWNER), Agency and Internal are SKIPPED and Media Owner is
   * the entry stage — the media owner approves their own submission directly, with no company-side
   * gate. Otherwise this returns exactly what the 2-stage flag has always resolved to, so
   * non-media-owner-created campaigns are unaffected.
   */
  private CampaignApprovedWorkflowStatus.Status initialStatusFor(
      CampaignApprovedWorkflowStatus.ApprovalAuthority authority, boolean mediaOwnerCreated) {
    if (mediaOwnerCreated) {
      return authority == CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER
          ? CampaignApprovedWorkflowStatus.Status.IN_PROGRESS
          : CampaignApprovedWorkflowStatus.Status.SKIPPED;
    }
    return switch (authority) {
      case AGENCY ->
          isTwoStageEnabled()
              ? CampaignApprovedWorkflowStatus.Status.SKIPPED
              : CampaignApprovedWorkflowStatus.Status.IN_PROGRESS;
      case INTERNAL ->
          isTwoStageEnabled()
              ? CampaignApprovedWorkflowStatus.Status.IN_PROGRESS
              : CampaignApprovedWorkflowStatus.Status.PENDING;
      case MEDIA_OWNER -> CampaignApprovedWorkflowStatus.Status.PENDING;
    };
  }

  // Creates proposal status entries for each media owner based on campaign inventory schedules
  private void createProposalStatusEntries(String campaignId) {
    List<CampaignInventorySchedules> schedules =
        campaignInventorySchedulesRepository.findByCampaignId(campaignId);

    Map<String, List<String>> inventoryByMediaOwner =
        schedules.stream()
            .collect(
                Collectors.groupingBy(
                    CampaignInventorySchedules::getMediaOwnerId,
                    Collectors.mapping(
                        CampaignInventorySchedules::getInventoryId, Collectors.toList())));

    List<CampaignProposalStatus> proposals =
        inventoryByMediaOwner.entrySet().stream()
            .map(
                entry -> {
                  CampaignProposalStatus proposal = new CampaignProposalStatus();
                  proposal.setCampaignId(campaignId);
                  proposal.setStatus(CampaignProposalStatus.Status.PENDING);
                  proposal.setMediaOwnerId(entry.getKey());
                  proposal.setInventoryIds(entry.getValue());
                  return proposal;
                })
            .collect(Collectors.toList());

    List<CampaignProposalStatus> savedProposals =
        campaignProposalStatusRepository.saveAll(proposals);
    log.debug(
        "Created {} proposal status entries for campaign: {}", savedProposals.size(), campaignId);
  }

  /**
   * Resets workflow statuses for resubmission (REJECTED→PLANNED). Statuses are resolved per
   * authority via {@link #resubmitStatusFor}, honouring the 2-stage flag; MEDIA_OWNER always resets
   * to PENDING. Single read + single saveAll for performance.
   *
   * @param campaignId Campaign ID
   */
  public void resetWorkflowStatusForResubmission(String campaignId) {
    List<CampaignApprovedWorkflowStatus> workflowStatuses =
        campaignApprovedWorkflowStatusRepository.findByCampaignId(campaignId);
    if (workflowStatuses.isEmpty()) {
      return;
    }
    boolean mediaOwnerCreated = isMediaOwnerCreatedWorkflow(workflowStatuses);
    applyResubmitStatuses(workflowStatuses, mediaOwnerCreated);
    campaignApprovedWorkflowStatusRepository.saveAll(workflowStatuses);
    log.debug("Reset workflow statuses for resubmit, campaign: {}", campaignId);
  }

  /** Applies resubmit status to each workflow entry via {@link #initialStatusFor}. */
  private void applyResubmitStatuses(
      List<CampaignApprovedWorkflowStatus> workflowStatuses, boolean mediaOwnerCreated) {
    for (CampaignApprovedWorkflowStatus w : workflowStatuses) {
      w.setStatus(initialStatusFor(w.getApprovalAuthority(), mediaOwnerCreated));
    }
  }

  /**
   * A workflow is "media-owner-created" when its Internal stage is currently SKIPPED — the durable
   * signal {@link #createApprovalWorkflowSteps} writes at creation time for campaigns owned by a
   * MEDIA_OWNER company. Reading it back here avoids a fresh IAM lookup on every resubmit/reset.
   */
  private boolean isMediaOwnerCreatedWorkflow(
      List<CampaignApprovedWorkflowStatus> workflowStatuses) {
    return workflowStatuses.stream()
        .filter(
            ws ->
                ws.getApprovalAuthority()
                    == CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL)
        .anyMatch(ws -> ws.getStatus() == CampaignApprovedWorkflowStatus.Status.SKIPPED);
  }

  // Creates a new workflow step entity with the specified campaign ID, approval authority, and
  // status
  private CampaignApprovedWorkflowStatus createWorkflowStep(
      String campaignId,
      CampaignApprovedWorkflowStatus.ApprovalAuthority approvalAuthority,
      CampaignApprovedWorkflowStatus.Status status) {
    CampaignApprovedWorkflowStatus workflowStep = new CampaignApprovedWorkflowStatus();
    workflowStep.setCampaignId(campaignId);
    workflowStep.setApprovalAuthority(approvalAuthority);
    workflowStep.setStatus(status);
    return workflowStep;
  }

  /**
   * Update approval status for a campaign workflow stage (Agency, Internal, or Media Owner).
   *
   * @param workflowStatusId Campaign Approved Workflow Status ID
   * @param request Approval status update request
   */
  public void updateApprovalStatus(
      String workflowStatusId, CampaignApprovalStatusUpdateRequestDTO request) {
    log.info(
        "Updating approval status for workflowStatusId: {}, status: {}",
        workflowStatusId,
        request.getStatus());

    // Get workflow status by ID
    CampaignApprovedWorkflowStatus workflowStatus =
        campaignApprovedWorkflowStatusRepository
            .findById(workflowStatusId)
            .orElseThrow(
                () ->
                    new RuntimeException("Workflow status not found for ID: " + workflowStatusId));

    // Check if workflow status is IN_PROGRESS before updating
    CampaignApprovedWorkflowStatus.Status currentStatus = workflowStatus.getStatus();
    boolean isUpdatable =
        currentStatus == CampaignApprovedWorkflowStatus.Status.IN_PROGRESS
            || currentStatus == CampaignApprovedWorkflowStatus.Status.PENDING;

    if (!isUpdatable) {
      log.warn(
          "Workflow status {} cannot be updated. Current status: {}, Required status: IN_PROGRESS",
          workflowStatusId,
          currentStatus);
      throw new WorkflowInvalidStatusException(
          currentStatus, CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
    }

    String campaignId = workflowStatus.getCampaignId();
    CampaignApprovedWorkflowStatus.ApprovalAuthority approvalAuthority =
        workflowStatus.getApprovalAuthority();

    // Resolve effective company ID via the shared acting-company resolver (X-Company-Id)
    IamUserContext userContext = userService.getIamUserContext();
    String actingCompanyId = userService.getActingCompanyId();
    String effectiveCompanyId =
        actingCompanyId != null ? actingCompanyId : userContext.getCompanyId();

    // ---- Server-side authorization: the effective company must actually hold this stage's
    // authority for this campaign. Client-side gating (drawer/inbox) is advisory only.
    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);
    boolean isGlobalAdmin = Boolean.TRUE.equals(userContext.getIsGlobalAdmin());
    if (!isGlobalAdmin) {
      boolean isMediaOwnerCompany = isCompanyMediaOwner(effectiveCompanyId);
      boolean createdByMediaOwner = isCompanyMediaOwner(campaign.getCompanyId());
      List<CampaignApprovalDetailsResponseDTO.ApprovalPermission> permissions =
          getApprovalPermissions(
              campaign,
              campaignId,
              effectiveCompanyId,
              false,
              isMediaOwnerCompany,
              createdByMediaOwner);
      boolean holdsAuthority =
          permissions != null
              && permissions.stream().anyMatch(p -> p.name().equals(approvalAuthority.name()));
      if (!holdsAuthority) {
        log.warn(
            "Company {} attempted to act on {} stage of campaign {} without holding that"
                + " authority",
            effectiveCompanyId,
            approvalAuthority,
            campaignId);
        throw new WorkflowInvalidStatusException(
            currentStatus, CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
      }
    }

    // ---- Stage-order enforcement: only the currently active stage may be acted on.
    // Agency/Internal must be IN_PROGRESS; Media Owner may act on a PENDING stage only once
    // every company-side stage has settled (COMPLETED or SKIPPED).
    if (currentStatus == CampaignApprovedWorkflowStatus.Status.PENDING) {
      boolean pendingActionable =
          approvalAuthority == CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER
              && campaignApprovedWorkflowStatusRepository.findByCampaignId(campaignId).stream()
                  .filter(
                      ws ->
                          ws.getApprovalAuthority()
                              != CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER)
                  .allMatch(
                      ws ->
                          ws.getStatus() == CampaignApprovedWorkflowStatus.Status.COMPLETED
                              || ws.getStatus() == CampaignApprovedWorkflowStatus.Status.SKIPPED);
      if (!pendingActionable) {
        log.warn(
            "Stage {} of campaign {} is PENDING and not yet actionable (prior stages unsettled)",
            approvalAuthority,
            campaignId);
        throw new WorkflowInvalidStatusException(
            currentStatus, CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
      }
    }

    // Check if prices need approval before allowing workflow status update
    if (campaignInventorySchedulesRepository
        .existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull(campaignId)) {
      log.warn(
          "Cannot update approval status for campaign {}: one or more prices need to be approved on the campaign",
          campaignId);
      throw new CampaignValidationException(
          "one or more prices need to be approved on the campaign");
    }

    // Create approval history entry
    CampaignApprovalHistory history = new CampaignApprovalHistory();
    history.setCampaignApprovedWorkflowStatusId(workflowStatus.getId());
    history.setComment(request.getComment());
    history.setStatus(request.getStatus());
    campaignApprovalHistoryRepository.save(history);

    // Handle based on status and authority
    switch (request.getStatus()) {
      case APPROVED:
        handleApproval(
            campaignId, approvalAuthority, workflowStatus, userContext, effectiveCompanyId);
        logApprovalActivity(campaignId, approvalAuthority, "Approved");
        break;
      case REJECTED:
        handleRejection(
            campaignId, approvalAuthority, workflowStatus, userContext, effectiveCompanyId);
        logApprovalActivity(campaignId, approvalAuthority, "Rejected");
        break;
      case IN_NEGOTIATION:
        handleChangeRequest(
            campaignId, approvalAuthority, workflowStatus, userContext, effectiveCompanyId);
        logApprovalActivity(campaignId, approvalAuthority, "Requested Changes");
        break;
    }

    log.info(
        "Successfully updated approval status for campaignId: {}, authority: {}, status: {}",
        campaignId,
        approvalAuthority,
        request.getStatus());
  }

  /**
   * Log approval workflow activity
   *
   * @param campaignId Campaign ID
   * @param approvalAuthority Approval authority
   * @param action Action taken (Approved, Rejected, Requested Changes)
   */
  private void logApprovalActivity(
      String campaignId,
      CampaignApprovedWorkflowStatus.ApprovalAuthority approvalAuthority,
      String action) {
    try {
      Map<String, Object> changes = new java.util.LinkedHashMap<>();
      changes.put(APPROVAL_AUTHORITY.key(), approvalAuthority.name());
      changes.put(APPROVAL_ACTION.key(), action);
      campaignActivityService.logActivity(
          campaignId, CampaignActivityService.OperationType.UPDATED, changes);
    } catch (Exception e) {
      log.warn("Failed to log approval activity: {}", e.getMessage());
    }
  }

  /**
   * Handles the approval workflow for a given campaign and approval authority. This method
   * delegates to specific handlers based on the approval authority.
   */
  private void handleApproval(
      String campaignId,
      CampaignApprovedWorkflowStatus.ApprovalAuthority approvalAuthority,
      CampaignApprovedWorkflowStatus workflowStatus,
      IamUserContext userContext,
      String effectiveCompanyId) {
    log.debug("Processing approval. campaignId={}, authority={}", campaignId, approvalAuthority);

    switch (approvalAuthority) {
      case AGENCY, INTERNAL ->
          handleAgencyOrInternalApproval(campaignId, approvalAuthority, workflowStatus);
      case MEDIA_OWNER ->
          handleMediaOwnerApproval(campaignId, workflowStatus, userContext, effectiveCompanyId);
      default ->
          throw new IllegalArgumentException(
              "Unsupported approval authority: " + approvalAuthority);
    }
  }

  /**
   * Handles approval flow for AGENCY and INTERNAL authorities. Both get marked as COMPLETED
   * immediately.
   */
  private void handleAgencyOrInternalApproval(
      String campaignId,
      CampaignApprovedWorkflowStatus.ApprovalAuthority authority,
      CampaignApprovedWorkflowStatus workflowStatus) {
    workflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.COMPLETED);
    campaignApprovedWorkflowStatusRepository.save(workflowStatus);

    if (authority == CampaignApprovedWorkflowStatus.ApprovalAuthority.AGENCY) {
      handleAgencyApproval(campaignId);
    } else {
      handleInternalApproval(campaignId);
    }
  }

  // Handles agency approval by activating the Internal stage. Only reachable in the legacy
  // 3-stage flow — in the 2-stage flow Agency is created/reset as SKIPPED, and
  // updateApprovalStatus's IN_PROGRESS/PENDING guard means a SKIPPED stage can never be approved.
  private void handleAgencyApproval(String campaignId) {
    log.debug("Handling Agency approval for campaignId: {}", campaignId);
    updateWorkflowStatus(
        campaignId,
        CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL,
        CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
  }

  /**
   * Advances the workflow to the Media Owner stage. When the plan is self-owned (the planning
   * company is the only media owner) there is no third party to review, so the media-owner approval
   * is auto-completed in the same action — matching "the media owner simply approves their own
   * plan". Otherwise the Media Owner stage is opened (IN_PROGRESS) for the media owner to act on.
   */
  private void advanceToMediaOwnerStage(String campaignId) {
    if (isSelfOwnedPlan(campaignId)) {
      log.info(
          "Self-owned plan detected for campaignId: {}. Auto-completing media owner approval.",
          campaignId);
      IamUserContext autoContext = userService.getIamUserContext();
      campaignApprovedWorkflowStatusRepository
          .findByCampaignIdAndApprovalAuthority(
              campaignId, CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER)
          .ifPresent(
              mediaOwnerStatus ->
                  handleMediaOwnerApproval(
                      campaignId, mediaOwnerStatus, autoContext, autoContext.getCompanyId()));
      return;
    }

    updateWorkflowStatus(
        campaignId,
        CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER,
        CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
  }

  /**
   * A plan is "self-owned" when every media-owner proposal belongs to the planning company itself,
   * i.e. the media owner created the plan and there is no separate agency/media-owner party.
   */
  private boolean isSelfOwnedPlan(String campaignId) {
    List<CampaignProposalStatus> proposals =
        campaignProposalStatusRepository.findStatusesByCampaignId(campaignId);
    if (proposals == null || proposals.isEmpty()) {
      return false;
    }
    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);
    String companyId = campaign.getCompanyId();
    return companyId != null
        && proposals.stream().allMatch(p -> companyId.equals(p.getMediaOwnerId()));
  }

  // Handles internal approval by advancing to the Media Owner stage. Reachable both as the
  // 2-stage flow's entry-stage completion (Internal is created IN_PROGRESS, Agency SKIPPED) and
  // as the second step of the legacy 3-stage flow (after Agency completes) — in both cases the
  // next step is Media Owner, so this always routes through advanceToMediaOwnerStage to also
  // pick up the self-owned-plan auto-complete check.
  private void handleInternalApproval(String campaignId) {
    log.debug("Handling Internal approval for campaignId: {}", campaignId);
    advanceToMediaOwnerStage(campaignId);
  }

  // Handles media owner approval by submitting campaign to ADS and updating proposal status, then
  // checking if all proposals are approved
  @Transactional
  private void handleMediaOwnerApproval(
      String campaignId,
      CampaignApprovedWorkflowStatus workflowStatus,
      IamUserContext userContext,
      String effectiveCompanyId) {
    log.debug("Handling Media Owner approval for campaignId: {}", campaignId);

    Boolean isGlobalAdmin = userContext.getIsGlobalAdmin();

    // Determine which proposals to approve based on user type
    List<CampaignProposalStatus> proposalsToApprove;

    if (Boolean.TRUE.equals(isGlobalAdmin)) {
      // Super Admin: Approve all PENDING media owner proposals
      log.info(
          "Global admin approving all PENDING media owner proposals for campaignId: {}",
          campaignId);
      List<CampaignProposalStatus> allProposals =
          campaignProposalStatusAndCommentService.getProposalsByCampaignId(campaignId);
      proposalsToApprove =
          allProposals.stream()
              .filter(p -> p.getStatus() == CampaignProposalStatus.Status.PENDING)
              .collect(Collectors.toList());

      if (proposalsToApprove.isEmpty()) {
        log.warn(
            "Global admin attempted to approve media owners for campaignId: {}, but no PENDING proposals found",
            campaignId);
        throw new CampaignValidationException("No pending media owner proposals found to approve");
      }
    } else {
      // Regular Media Owner: Approve only their own proposal
      String mediaOwnerId = effectiveCompanyId;

      // Validate mediaOwnerId is not null
      if (mediaOwnerId == null || mediaOwnerId.trim().isEmpty()) {
        log.error(
            "Cannot approve media owner proposal: User companyId is null or empty. UserId: {}",
            userContext.getId());
        throw new CampaignValidationException(
            "User company ID is required to approve media owner proposal");
      }

      CampaignProposalStatus campaignProposalStatus =
          campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
              campaignId, mediaOwnerId);

      if (campaignProposalStatus == null) {
        throw new ProposalNotFoundException(mediaOwnerId);
      }

      proposalsToApprove = List.of(campaignProposalStatus);
    }

    // Collect all inventory IDs from all proposals to approve
    List<String> allInventoryIds =
        proposalsToApprove.stream()
            .filter(
                proposal ->
                    proposal.getInventoryIds() != null && !proposal.getInventoryIds().isEmpty())
            .flatMap(proposal -> proposal.getInventoryIds().stream())
            .distinct()
            .collect(Collectors.toList());

    log.info(
        "Submitting {} inventories to ADS for campaignId: {} (from {} proposals)",
        allInventoryIds.size(),
        campaignId,
        proposalsToApprove.size());

    // Validate that we have inventories to submit
    if (allInventoryIds.isEmpty()) {
      log.error(
          "Cannot submit to ADS: No inventories found in proposals for campaignId: {}", campaignId);
      throw new CampaignValidationException(
          "No inventories found in proposals. Cannot approve without inventories.");
    }

    // Submit all inventories to ADS in ONE call
    try {
      AdsSubmissionResponseDTO adsSubmissionResponseDTO =
          mwAdsService.submitApprovedCampaignToAds(campaignId, allInventoryIds);
      log.info("ADS response: {}", adsSubmissionResponseDTO);

      // Update all proposals to APPROVED only if ADS submission is successful
      for (CampaignProposalStatus proposal : proposalsToApprove) {
        proposal.setStatus(CampaignProposalStatus.Status.APPROVED);
        campaignProposalStatusRepository.save(proposal);
        log.info(
            "Updated campaignProposalStatus to APPROVED for campaignId: {}, mediaOwnerId: {}",
            campaignId,
            proposal.getMediaOwnerId());
      }
    } catch (AdsApiException | CampaignNotApprovedForAdsException e) {
      // Handle ADS exceptions without updating any campaignProposalStatus
      log.error(
          "ADS submission failed for campaignId: {}. Error: {}. Proposals remain PENDING.",
          campaignId,
          e.getMessage(),
          e);
      // Re-throw the exception to propagate it
      throw e;
    }

    // Check if all proposals for the campaign are approved
    List<CampaignProposalStatus> proposalStatuses =
        campaignProposalStatusRepository.findStatusesByCampaignId(campaignId);

    boolean allProposalsApproved =
        proposalStatuses.stream()
            .allMatch(proposal -> proposal.getStatus() == CampaignProposalStatus.Status.APPROVED);

    // Update workflow status based on whether all proposals are approved
    if (allProposalsApproved) {
      // All proposals approved → set workflow status to COMPLETED
      workflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.COMPLETED);
      campaignApprovedWorkflowStatusRepository.save(workflowStatus);
      log.info("All proposals are approved for campaignId: {}. Approving campaign.", campaignId);
      campaignService.changeCampaignStatus(campaignId, Campaign.Status.APPROVED);
    } else {
      // Not all proposals approved → set workflow status to IN_PROGRESS
      workflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
      campaignApprovedWorkflowStatusRepository.save(workflowStatus);
      log.debug(
          "Not all proposals are approved for campaignId: {}. Workflow status set to IN_PROGRESS.",
          campaignId);
    }
  }

  /**
   * Handles campaign rejection based on approval authority.
   *
   * <p>Business rules: - Non-media-owner rejection → campaign + workflow are immediately REJECTED -
   * Media-owner rejection → proposal is rejected first, then workflow & campaign status are derived
   * from all proposal statuses
   */
  private void handleRejection(
      String campaignId,
      CampaignApprovedWorkflowStatus.ApprovalAuthority approvalAuthority,
      CampaignApprovedWorkflowStatus workflowStatus,
      IamUserContext userContext,
      String effectiveCompanyId) {

    log.debug("Handling rejection for campaignId={}, authority={}", campaignId, approvalAuthority);

    // Non-media-owner rejection → immediate workflow + campaign rejection
    if (approvalAuthority != CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER) {
      rejectWorkflowAndCampaign(campaignId, workflowStatus);
      return;
    }

    List<CampaignProposalStatus> proposals =
        campaignProposalStatusAndCommentService.getProposalsByCampaignId(campaignId);

    // Reject current media owner's proposal
    rejectMediaOwnerProposal(proposals, userContext, effectiveCompanyId);

    // Resolve final workflow status from the same dataset
    CampaignApprovedWorkflowStatus.Status finalStatus = resolveFinalStatus(proposals);

    // If proposals are still pending, workflow/campaign state must not change
    if (finalStatus == null) {
      log.debug("Workflow state unchanged due to pending proposals for campaignId={}", campaignId);
      return;
    }

    updateWorkflowAndCampaign(campaignId, workflowStatus, finalStatus);
  }

  /**
   * Rejects workflow and campaign immediately. Used when rejection authority is NOT media owner.
   */
  private void rejectWorkflowAndCampaign(
      String campaignId, CampaignApprovedWorkflowStatus workflowStatus) {

    workflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.REJECTED);
    campaignApprovedWorkflowStatusRepository.save(workflowStatus);

    campaignService.changeCampaignStatus(campaignId, Campaign.Status.REJECTED);
  }

  /**
   * Rejects the proposal belonging to the current media owner. For global admins, rejects all
   * pending proposals. Uses in-memory filtering to avoid additional DB calls.
   */
  private void rejectMediaOwnerProposal(
      List<CampaignProposalStatus> proposals,
      IamUserContext userContext,
      String effectiveCompanyId) {

    Boolean isGlobalAdmin = userContext.getIsGlobalAdmin();

    if (Boolean.TRUE.equals(isGlobalAdmin)) {
      // Super Admin: Reject all PENDING media owner proposals
      List<CampaignProposalStatus> pendingProposals =
          proposals.stream()
              .filter(p -> p.getStatus() == CampaignProposalStatus.Status.PENDING)
              .collect(Collectors.toList());

      if (pendingProposals.isEmpty()) {
        log.warn("Global admin attempted to reject, but no PENDING proposals found");
      } else {
        pendingProposals.forEach(
            proposal -> {
              proposal.setStatus(CampaignProposalStatus.Status.REJECTED);
              campaignProposalStatusRepository.save(proposal);
              log.info(
                  "Global admin rejected proposal for campaignId={}, mediaOwnerId={}",
                  proposal.getCampaignId(),
                  proposal.getMediaOwnerId());
            });
      }
    } else {
      // Regular Media Owner: Reject only their own proposal
      String mediaOwnerId = effectiveCompanyId;

      // Validate mediaOwnerId is not null
      if (mediaOwnerId == null || mediaOwnerId.trim().isEmpty()) {
        log.warn(
            "Cannot reject media owner proposal: User companyId is null or empty. UserId: {}",
            userContext.getId());
        return;
      }

      proposals.stream()
          .filter(p -> mediaOwnerId.equals(p.getMediaOwnerId()))
          .findFirst()
          .ifPresent(
              proposal -> {
                proposal.setStatus(CampaignProposalStatus.Status.REJECTED);
                campaignProposalStatusRepository.save(proposal);

                log.info(
                    "Rejected proposal for campaignId={}, mediaOwnerId={}",
                    proposal.getCampaignId(),
                    mediaOwnerId);
              });
    }
  }

  /**
   * Determines final workflow status from proposal list.
   *
   * <p>Rules: - Any PENDING → return null - Any REJECTED → REJECTED - All APPROVED → APPROVED
   */
  private CampaignApprovedWorkflowStatus.Status resolveFinalStatus(
      List<CampaignProposalStatus> proposals) {

    boolean hasPending =
        proposals.stream().anyMatch(p -> p.getStatus() == CampaignProposalStatus.Status.PENDING);

    if (hasPending) {
      return null;
    }

    boolean hasRejected =
        proposals.stream().anyMatch(p -> p.getStatus() == CampaignProposalStatus.Status.REJECTED);

    return hasRejected
        ? CampaignApprovedWorkflowStatus.Status.REJECTED
        : CampaignApprovedWorkflowStatus.Status.COMPLETED;
  }

  /** Updates workflow and campaign consistently based on resolved status. */
  private void updateWorkflowAndCampaign(
      String campaignId,
      CampaignApprovedWorkflowStatus workflowStatus,
      CampaignApprovedWorkflowStatus.Status finalStatus) {

    workflowStatus.setStatus(finalStatus);
    campaignApprovedWorkflowStatusRepository.save(workflowStatus);

    Campaign.Status campaignStatus =
        (finalStatus == CampaignApprovedWorkflowStatus.Status.REJECTED)
            ? Campaign.Status.REJECTED
            : Campaign.Status.APPROVED;

    campaignService.changeCampaignStatus(campaignId, campaignStatus);
  }

  // Handles change request by updating workflow status to CHANGES_REQUESTED, setting campaign to
  // NEGOTIATING, and resetting workflow if needed
  private void handleChangeRequest(
      String campaignId,
      CampaignApprovedWorkflowStatus.ApprovalAuthority approvalAuthority,
      CampaignApprovedWorkflowStatus workflowStatus,
      IamUserContext userContext,
      String effectiveCompanyId) {
    log.debug(
        "Handling change request for campaignId: {}, authority: {}", campaignId, approvalAuthority);

    // Update current authority status to CHANGES_REQUESTED
    workflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.CHANGES_REQUESTED);
    campaignApprovedWorkflowStatusRepository.save(workflowStatus);

    // Campaign → NEGOTIATING
    campaignService.changeCampaignStatus(campaignId, Campaign.Status.NEGOTIATING);

    // If Media Owner requests changes, update campaignProposalStatus to NEGOTIATING
    if (approvalAuthority == CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER) {
      Boolean isGlobalAdmin = userContext.getIsGlobalAdmin();

      if (Boolean.TRUE.equals(isGlobalAdmin)) {
        // Super Admin: Set all PENDING proposals to NEGOTIATING
        List<CampaignProposalStatus> allProposals =
            campaignProposalStatusAndCommentService.getProposalsByCampaignId(campaignId);
        List<CampaignProposalStatus> pendingProposals =
            allProposals.stream()
                .filter(p -> p.getStatus() == CampaignProposalStatus.Status.PENDING)
                .collect(Collectors.toList());

        if (pendingProposals.isEmpty()) {
          log.warn(
              "Global admin requested changes for campaignId: {}, but no PENDING proposals found",
              campaignId);
        } else {
          pendingProposals.forEach(
              proposal -> {
                proposal.setStatus(CampaignProposalStatus.Status.NEGOTIATING);
                campaignProposalStatusRepository.save(proposal);
                log.info(
                    "Global admin updated campaignProposalStatus to NEGOTIATING for campaignId: {}, mediaOwnerId: {}",
                    campaignId,
                    proposal.getMediaOwnerId());
              });
        }
      } else {
        // Regular Media Owner: Update only their own proposal
        String mediaOwnerId = effectiveCompanyId;

        // Validate mediaOwnerId is not null
        if (mediaOwnerId == null || mediaOwnerId.trim().isEmpty()) {
          log.warn(
              "Cannot update proposal to NEGOTIATING: User companyId is null or empty. UserId: {}",
              userContext.getId());
        } else {
          CampaignProposalStatus campaignProposalStatus =
              campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
                  campaignId, mediaOwnerId);
          if (campaignProposalStatus != null) {
            campaignProposalStatus.setStatus(CampaignProposalStatus.Status.NEGOTIATING);
            campaignProposalStatusRepository.save(campaignProposalStatus);
            log.info(
                "Updated campaignProposalStatus to NEGOTIATING for campaignId: {}, mediaOwnerId: {}",
                campaignId,
                mediaOwnerId);
          }
        }
      }
    }

    switch (approvalAuthority) {
      case AGENCY:
        // Entry stage of the legacy 3-stage flow only (unreachable in the 2-stage flow, where
        // Agency is SKIPPED). Nothing has progressed yet, so no reset is needed.
        break;
      case INTERNAL:
        if (!isTwoStageEnabled()) {
          // Legacy 3-stage flow: Internal is reached only after Agency completed, so a change
          // request here must rewind the whole workflow, same as Media Owner below.
          log.info(
              "Resetting workflow to beginning for campaignId: {} due to change request from {}",
              campaignId,
              approvalAuthority);
          resetApprovalWorkflowStatus(campaignId);
        }
        // 2-stage flow: Internal is the entry stage — nothing has progressed yet, so no reset.
        break;
      case MEDIA_OWNER:
        log.info(
            "Resetting workflow to beginning for campaignId: {} due to change request from {}",
            campaignId,
            approvalAuthority);
        resetApprovalWorkflowStatus(campaignId);
        break;
    }
  }

  public void resetApprovalWorkflowStatus(String campaignId) {
    boolean mediaOwnerCreated =
        campaignApprovedWorkflowStatusRepository
            .findByCampaignIdAndApprovalAuthority(
                campaignId, CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL)
            .map(ws -> ws.getStatus() == CampaignApprovedWorkflowStatus.Status.SKIPPED)
            .orElse(false);

    updateWorkflowStatus(
        campaignId,
        CampaignApprovedWorkflowStatus.ApprovalAuthority.AGENCY,
        initialStatusFor(
            CampaignApprovedWorkflowStatus.ApprovalAuthority.AGENCY, mediaOwnerCreated));
    updateWorkflowStatus(
        campaignId,
        CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL,
        initialStatusFor(
            CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL, mediaOwnerCreated));
    updateWorkflowStatus(
        campaignId,
        CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER,
        initialStatusFor(
            CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER, mediaOwnerCreated));
  }

  // Updates the workflow status for a specific campaign and approval authority
  private void updateWorkflowStatus(
      String campaignId,
      CampaignApprovedWorkflowStatus.ApprovalAuthority approvalAuthority,
      CampaignApprovedWorkflowStatus.Status status) {
    campaignApprovedWorkflowStatusRepository
        .findByCampaignIdAndApprovalAuthority(campaignId, approvalAuthority)
        .ifPresentOrElse(
            workflowStatus -> {
              workflowStatus.setStatus(status);
              campaignApprovedWorkflowStatusRepository.save(workflowStatus);
              log.debug(
                  "Updated workflow status for campaignId: {}, authority: {}, status: {}",
                  campaignId,
                  approvalAuthority,
                  status);
            },
            () ->
                log.warn(
                    "Workflow status not found for campaignId: {}, authority: {}",
                    campaignId,
                    approvalAuthority));
  }

  /**
   * Get campaign approval details by campaign ID.
   *
   * @param campaignId Campaign ID
   * @return Campaign approval details response DTO
   */
  /**
   * Plan Approval inbox: every campaign in an active approval cycle (REVIEWING/NEGOTIATING) that
   * the viewer's company is involved in — as creator, shared-access company, or media owner with a
   * proposal. Reuses the per-campaign details computation so visibility, permissions and display
   * statuses stay identical to the Approval Drawer.
   */
  public List<ApprovalInboxItemDTO> getApprovalInbox() {
    IamUserContext userContext = userService.getIamUserContext();
    boolean isGlobalAdmin = Boolean.TRUE.equals(userContext.getIsGlobalAdmin());
    String actingCompanyId = userService.getActingCompanyId();
    String userCompanyId = actingCompanyId != null ? actingCompanyId : userContext.getCompanyId();

    // Candidate campaigns in an active approval cycle. Scoped in the query for regular
    // users (creator or shared access); global admins see every active cycle.
    List<Campaign.Status> activeStatuses =
        List.of(Campaign.Status.REVIEWING, Campaign.Status.NEGOTIATING);
    List<Campaign> inCycle =
        isGlobalAdmin
            ? campaignRepository.findByStatusIn(activeStatuses)
            : StringUtils.hasText(userCompanyId)
                ? campaignRepository.findByStatusInAndCompanyInvolved(activeStatuses, userCompanyId)
                : List.of();

    Map<String, Campaign> candidates = new LinkedHashMap<>();
    for (Campaign c : inCycle) {
      // Test Mode partition: the inbox must only surface plans from the caller's data mode.
      if (testModeService.matchesCallerMode(c)) {
        candidates.put(c.getId(), c);
      }
    }
    // ...or the viewer's company is a media owner with an unresolved proposal
    if (StringUtils.hasText(userCompanyId)) {
      for (CampaignProposalStatus proposal :
          campaignProposalStatusRepository.findByMediaOwnerIdAndStatusIn(
              userCompanyId,
              List.of(
                  CampaignProposalStatus.Status.PENDING,
                  CampaignProposalStatus.Status.NEGOTIATING))) {
        if (!candidates.containsKey(proposal.getCampaignId())) {
          try {
            Campaign c = campaignService.findByIdForCurrentMode(proposal.getCampaignId());
            if (c.getStatus() == Campaign.Status.REVIEWING
                || c.getStatus() == Campaign.Status.NEGOTIATING) {
              candidates.put(c.getId(), c);
            }
          } catch (Exception e) {
            log.warn("Skipping proposal with missing campaign {}", proposal.getCampaignId());
          }
        }
      }
    }

    // ---- Bulk loads (avoid per-campaign N+1 queries) -------------------------
    List<String> candidateIds = new ArrayList<>(candidates.keySet());
    Map<String, List<CampaignProposalStatus>> proposalsByCampaign =
        candidateIds.isEmpty()
            ? Map.of()
            : campaignProposalStatusRepository.findByCampaignIdIn(candidateIds).stream()
                .collect(Collectors.groupingBy(CampaignProposalStatus::getCampaignId));
    Map<String, List<CampaignInventorySchedules>> invSchedulesByCampaign =
        candidateIds.isEmpty()
            ? Map.of()
            : campaignInventorySchedulesRepository.findByCampaignIdIn(candidateIds).stream()
                .filter(s -> s.getCampaignId() != null)
                .collect(Collectors.groupingBy(CampaignInventorySchedules::getCampaignId));
    // One bulk load of schedule prices for every schedule referenced by any candidate.
    List<String> allScheduleIds =
        invSchedulesByCampaign.values().stream()
            .flatMap(List::stream)
            .flatMap(
                s ->
                    s.getScheduleIds() == null
                        ? java.util.stream.Stream.<String>empty()
                        : s.getScheduleIds().stream())
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<String, Double> basePriceByScheduleId = new HashMap<>();
    if (!allScheduleIds.isEmpty()) {
      for (Schedule schedule : scheduleRepository.findAllById(allScheduleIds)) {
        if (schedule.getBasePrice() != null) {
          basePriceByScheduleId.put(schedule.getId(), schedule.getBasePrice());
        }
      }
    }
    // Company display names resolved at most once per unique id across the whole inbox.
    Map<String, String> companyNameCache = new HashMap<>();

    // A media-owner-typed company (per IAM) is never buyer-side, proposal or not —
    // e.g. a media owner granted shared companyAccess without any proposal must still
    // never see buyer financials or other owners' state.
    // Fail-closed: unresolvable company type counts as media owner. Campaign creators stay
    // buyer-side via the per-campaign creator check below.
    boolean viewerCompanyIsMediaOwnerType =
        !isGlobalAdmin && isCompanyMediaOwnerFailClosed(userCompanyId);

    List<ApprovalInboxItemDTO> items = new ArrayList<>();
    for (Campaign campaign : candidates.values()) {
      try {
        CampaignApprovalDetailsResponseDTO details = getCampaignApprovalDetails(campaign.getId());

        // The stage currently awaiting action = first non-terminal stage in workflow order.
        CampaignApprovalDetailsResponseDTO.ApprovalProgressDTO awaiting =
            details.getApprovalProgress() == null
                ? null
                : details.getApprovalProgress().stream()
                    .filter(
                        p ->
                            p.getStatus() == CampaignApprovedWorkflowStatus.Status.IN_PROGRESS
                                || p.getStatus() == CampaignApprovedWorkflowStatus.Status.PENDING)
                    .findFirst()
                    .orElse(null);

        List<CampaignInventorySchedules> allSchedules =
            invSchedulesByCampaign.getOrDefault(campaign.getId(), List.of());

        // Same semantics as existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull,
        // computed from the bulk-loaded schedules instead of a per-campaign query.
        boolean hasUnacceptedPrices =
            allSchedules.stream()
                .anyMatch(
                    s ->
                        s.getApprovedBy() == null
                            && s.getHistory() != null
                            && s.getHistory().size() > 1);

        // Actionable = the stage is actually active (IN_PROGRESS — matches the drawer and the
        // server-side update rules), the viewer holds its authority, and no changed price is
        // waiting for acceptance (the server rejects every decision until prices are accepted).
        boolean canAct =
            awaiting != null
                && awaiting.getStatus() == CampaignApprovedWorkflowStatus.Status.IN_PROGRESS
                && campaign.getStatus() == Campaign.Status.REVIEWING
                && !hasUnacceptedPrices
                && details.getApprovalPermissions() != null
                && details.getApprovalPermissions().stream()
                    .anyMatch(p -> p.name().equals(awaiting.getApprovalAuthority().name()));

        // ---- Persona enrichment -------------------------------------------------
        // Per-media-owner proposal progress (for buyer-side viewers) and the viewing
        // media owner's own slice (for media-owner viewers). Media owners must never
        // see buyer financials (budget incl. fees) or other owners' state.
        List<CampaignProposalStatus> proposals =
            proposalsByCampaign.getOrDefault(campaign.getId(), List.of());
        boolean viewerIsMediaOwner =
            StringUtils.hasText(userCompanyId)
                && !userCompanyId.equals(campaign.getCompanyId())
                && (viewerCompanyIsMediaOwnerType
                    || proposals.stream().anyMatch(p -> userCompanyId.equals(p.getMediaOwnerId())));
        // Proposal-owner redaction takes precedence over shared companyAccess: a media
        // owner granted access must still never see buyer financials or other owners'
        // state. Only the creator and global admins are buyer-side for such viewers.
        boolean viewerIsBuyerSide =
            isGlobalAdmin
                || (StringUtils.hasText(userCompanyId)
                    && (userCompanyId.equals(campaign.getCompanyId())
                        || (!viewerIsMediaOwner
                            && campaign.getCompanyAccess() != null
                            && campaign.getCompanyAccess().contains(userCompanyId))));

        // Owners with an open (unaccepted) counter offer, grouped from unapproved
        // schedule history — same source as hasUnacceptedPrices, but per owner.
        Set<String> ownersWithOpenCounter =
            hasUnacceptedPrices
                ? allSchedules.stream()
                    .filter(s -> s.getApprovedBy() == null)
                    .filter(s -> s.getHistory() != null && s.getHistory().size() > 1)
                    .map(CampaignInventorySchedules::getMediaOwnerId)
                    .collect(Collectors.toSet())
                : Set.of();

        Map<String, ApprovalInboxItemDTO.MediaOwnerProgressDTO> ownerProgress =
            new LinkedHashMap<>();
        if (!proposals.isEmpty()) {
          Map<String, List<CampaignInventorySchedules>> byOwner =
              allSchedules.stream()
                  .filter(s -> s.getMediaOwnerId() != null)
                  .collect(Collectors.groupingBy(CampaignInventorySchedules::getMediaOwnerId));
          for (CampaignProposalStatus proposal : proposals) {
            List<CampaignInventorySchedules> ownerSchedules =
                byOwner.getOrDefault(proposal.getMediaOwnerId(), List.of());
            List<String> scheduleIds =
                ownerSchedules.stream()
                    .flatMap(
                        s ->
                            s.getScheduleIds() == null
                                ? java.util.stream.Stream.<String>empty()
                                : s.getScheduleIds().stream())
                    .filter(Objects::nonNull)
                    .toList();
            double mediaCost = 0d;
            for (String scheduleId : scheduleIds) {
              Double basePrice = basePriceByScheduleId.get(scheduleId);
              if (basePrice != null) {
                mediaCost += basePrice;
              }
            }
            ownerProgress.put(
                proposal.getMediaOwnerId(),
                ApprovalInboxItemDTO.MediaOwnerProgressDTO.builder()
                    .mediaOwnerId(proposal.getMediaOwnerId())
                    .mediaOwnerName(
                        resolveCompanyName(proposal.getMediaOwnerId(), companyNameCache))
                    .status(proposal.getStatus() != null ? proposal.getStatus().name() : null)
                    .inventoryCount(
                        proposal.getInventoryIds() != null
                            ? proposal.getInventoryIds().size()
                            : ownerSchedules.size())
                    .mediaCost(mediaCost)
                    .hasOpenCounterOffer(ownersWithOpenCounter.contains(proposal.getMediaOwnerId()))
                    .build());
          }
        }

        items.add(
            ApprovalInboxItemDTO.builder()
                .campaignId(campaign.getId())
                .campaignName(campaign.getName())
                .planNumber(campaign.getPlanNumber())
                .status(details.getStatus())
                .workflowStatus(details.getWorkflowStatus())
                // Budget carries buyer-side fees — media-owner viewers get their own
                // media cost via viewerProposal instead.
                .budget(viewerIsMediaOwner && !viewerIsBuyerSide ? null : campaign.getBudget())
                .currency(campaign.getCurrency())
                .startDate(campaign.getStartDate())
                .endDate(campaign.getEndDate())
                .isNegotiated(campaign.getIsNegotiated())
                .awaitingAuthority(awaiting != null ? awaiting.getApprovalAuthority() : null)
                .canAct(canAct)
                .actionProgressId(canAct ? awaiting.getId() : null)
                .permissions(details.getApprovalPermissions())
                // Campaign-wide flag would reveal other owners' negotiation state to a
                // media-owner viewer — scope it to their own schedules for them.
                .hasUnacceptedPrices(
                    viewerIsMediaOwner && !viewerIsBuyerSide
                        ? ownersWithOpenCounter.contains(userCompanyId)
                        : hasUnacceptedPrices)
                .viewerIsMediaOwner(viewerIsMediaOwner && !viewerIsBuyerSide)
                .createdByCompanyName(
                    viewerIsMediaOwner && !viewerIsBuyerSide
                        ? resolveCompanyName(campaign.getCompanyId(), companyNameCache)
                        : null)
                .mediaOwners(viewerIsBuyerSide ? new ArrayList<>(ownerProgress.values()) : null)
                .viewerProposal(
                    viewerIsMediaOwner && !viewerIsBuyerSide
                        ? ownerProgress.get(userCompanyId)
                        : null)
                .build());
      } catch (Exception e) {
        log.warn("Skipping inbox item for campaign {}: {}", campaign.getId(), e.getMessage());
      }
    }

    // Actionable plans first, then most recently updated
    items.sort(
        Comparator.comparing(ApprovalInboxItemDTO::isCanAct)
            .reversed()
            .thenComparing(i -> i.getStartDate() == null ? LocalDate.MIN : i.getStartDate()));
    return items;
  }

  /**
   * Media owners with a price change awaiting the counterparty's acceptance (an open counter
   * offer), grouped from unapproved schedule history.
   */
  private Set<String> ownersWithOpenCounterOffer(String campaignId) {
    return campaignInventorySchedulesRepository
        .findByCampaignIdAndApprovedByIsNull(campaignId)
        .stream()
        .filter(s -> s.getHistory() != null && s.getHistory().size() > 1)
        .map(CampaignInventorySchedules::getMediaOwnerId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  /**
   * Per-media-owner proposal progress: status, inventory count, base-price media cost (no buyer
   * fees) and open-counter-offer flag. Keyed by media owner id, in proposal order.
   */
  private Map<String, ApprovalInboxItemDTO.MediaOwnerProgressDTO> buildOwnerProgress(
      String campaignId,
      List<CampaignProposalStatus> proposals,
      Set<String> ownersWithOpenCounter) {
    Map<String, ApprovalInboxItemDTO.MediaOwnerProgressDTO> ownerProgress = new LinkedHashMap<>();
    if (proposals.isEmpty()) {
      return ownerProgress;
    }
    List<CampaignInventorySchedules> allSchedules =
        campaignInventorySchedulesRepository.findByCampaignId(campaignId);
    Map<String, List<CampaignInventorySchedules>> byOwner =
        allSchedules.stream()
            .filter(s -> s.getMediaOwnerId() != null)
            .collect(Collectors.groupingBy(CampaignInventorySchedules::getMediaOwnerId));
    for (CampaignProposalStatus proposal : proposals) {
      List<CampaignInventorySchedules> ownerSchedules =
          byOwner.getOrDefault(proposal.getMediaOwnerId(), List.of());
      List<String> scheduleIds =
          ownerSchedules.stream()
              .flatMap(
                  s ->
                      s.getScheduleIds() == null
                          ? java.util.stream.Stream.<String>empty()
                          : s.getScheduleIds().stream())
              .filter(Objects::nonNull)
              .toList();
      double mediaCost = 0d;
      if (!scheduleIds.isEmpty()) {
        for (Schedule schedule : scheduleRepository.findAllById(scheduleIds)) {
          if (schedule.getBasePrice() != null) {
            mediaCost += schedule.getBasePrice();
          }
        }
      }
      ownerProgress.put(
          proposal.getMediaOwnerId(),
          ApprovalInboxItemDTO.MediaOwnerProgressDTO.builder()
              .mediaOwnerId(proposal.getMediaOwnerId())
              .mediaOwnerName(resolveCompanyName(proposal.getMediaOwnerId()))
              .status(proposal.getStatus() != null ? proposal.getStatus().name() : null)
              .inventoryCount(
                  proposal.getInventoryIds() != null
                      ? proposal.getInventoryIds().size()
                      : ownerSchedules.size())
              .mediaCost(mediaCost)
              .hasOpenCounterOffer(ownersWithOpenCounter.contains(proposal.getMediaOwnerId()))
              .build());
    }
    return ownerProgress;
  }

  public CampaignApprovalDetailsResponseDTO getCampaignApprovalDetails(String campaignId) {
    log.info("Fetching campaign approval details for campaignId: {}", campaignId);

    // Get campaign
    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);

    // Get user context for status calculation
    IamUserContext userContext = userService.getIamUserContext();
    Boolean isGlobalAdmin = userContext.getIsGlobalAdmin();

    // Acting company from the shared resolver (X-Company-Id aware, membership validated)
    String actingCompanyId = userService.getActingCompanyId();
    String userCompanyId = actingCompanyId != null ? actingCompanyId : userContext.getCompanyId();

    // Whether the viewer's own company is a media owner — such companies never get
    // Agency/Internal permission on any campaign. Resolved strictly (empty on IAM
    // lookup failure) so financial redaction below can fail closed.
    Optional<Boolean> viewerCompanyIsMediaOwner = isCompanyMediaOwnerStrict(userCompanyId);
    boolean isMediaOwner = viewerCompanyIsMediaOwner.orElse(false);
    // Whether this campaign was created by a media-owner company — Agency/Internal never apply
    // to it, for any viewer (including global admin).
    boolean campaignCreatedByMediaOwner = isCompanyMediaOwner(campaign.getCompanyId());

    // Get all workflow statuses for the campaign
    List<CampaignApprovedWorkflowStatus> workflowStatuses =
        campaignApprovedWorkflowStatusRepository.findByCampaignId(campaignId);

    // Build the list of approval permissions for the current user and campaign
    List<CampaignApprovalDetailsResponseDTO.ApprovalPermission> approvalPermissions =
        getApprovalPermissions(
            campaign,
            campaignId,
            userCompanyId,
            isGlobalAdmin,
            isMediaOwner,
            campaignCreatedByMediaOwner);

    UserResponseDTO user = userService.getUserById(campaign.getUserId());
    Campaign.Status campaignStatus =
        campaignService.resolveCampaignStatus(campaign, user, userCompanyId);

    CampaignApprovedWorkflowStatus.Status mediaOwnerStatus =
        mediaOwnerWorkflowStatus(campaignStatus);

    // Determine the viewer's relationship to the campaign. Media-owner users only ever see a
    // single stage (their own approval); the company-side 2-vs-3 stage structure is hidden from
    // them. A viewer is a media owner when a proposal exists for their company.
    CampaignProposalStatus viewerProposal =
        (userCompanyId == null || userCompanyId.isBlank())
            ? null
            : campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
                campaignId, userCompanyId);
    boolean viewerIsMediaOwner = viewerProposal != null;

    // ---- Direct-API access guard --------------------------------------------
    // Only involved parties may read approval details: global admins, the creating
    // company, shared-access companies, a media owner with a proposal on this
    // campaign. Anyone else — including an uninvolved media-owner-typed company —
    // gets a 404 (don't reveal the campaign exists).
    boolean viewerIsCreator =
        StringUtils.hasText(userCompanyId) && userCompanyId.equals(campaign.getCompanyId());
    boolean viewerHasSharedAccess =
        StringUtils.hasText(userCompanyId)
            && campaign.getCompanyAccess() != null
            && campaign.getCompanyAccess().contains(userCompanyId);
    if (!Boolean.TRUE.equals(isGlobalAdmin)
        && !viewerIsCreator
        && !viewerHasSharedAccess
        && !viewerIsMediaOwner) {
      throw new CampaignNotFoundException(campaignId);
    }

    // Hide SKIPPED stages from everyone; media-owner viewers see only the Media Owner stage.
    List<CampaignApprovedWorkflowStatus> visibleStatuses =
        workflowStatuses.stream()
            .filter(ws -> ws.getStatus() != CampaignApprovedWorkflowStatus.Status.SKIPPED)
            .filter(
                ws ->
                    !viewerIsMediaOwner
                        || ws.getApprovalAuthority()
                            == CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER)
            .toList();

    // Build approval progress list
    List<CampaignApprovalDetailsResponseDTO.ApprovalProgressDTO> approvalProgress =
        visibleStatuses.stream()
            .map(
                workflowStatus -> {
                  // Get the latest approval history comment for this workflow status
                  String comment = getLatestComment(workflowStatus.getId());

                  var status =
                      resolveDisplayStatus(
                          workflowStatus, viewerIsMediaOwner, viewerProposal, mediaOwnerStatus);

                  return CampaignApprovalDetailsResponseDTO.ApprovalProgressDTO.builder()
                      .id(workflowStatus.getId())
                      .status(status)
                      .approvalAuthority(workflowStatus.getApprovalAuthority())
                      .comment(comment)
                      .updatedAt(workflowStatus.getUpdatedAt())
                      .updatedBy(workflowStatus.getLastModifiedBy())
                      .createdBy(workflowStatus.getCreatedBy())
                      .createdAt(workflowStatus.getCreatedAt())
                      .build();
                })
            .toList();

    // Get campaign status using the new method
    CampaignApprovalStatus approvalStatus =
        getCampaignStatus(campaign, userCompanyId, isMediaOwner && viewerIsMediaOwner);

    // Budget carries buyer-side fees. Redact it for any media-owner viewer that is
    // not the creator or a global admin — proposal-owner redaction takes precedence
    // over shared companyAccess, so a media owner granted access never sees fees.
    // Redact based on the viewer's media-owner persona (company type or proposal
    // ownership), not just proposal presence — a media-owner company with shared
    // access but no proposal must still never see buyer financials. Fails CLOSED:
    // when the company type cannot be resolved (IAM outage), the viewer is treated
    // as a media-owner persona, never buyer-side.
    // Any identified media-owner persona is redacted — including a media-owner company
    // viewing a campaign it created itself. Only global admins are exempt.
    boolean viewerMediaOwnerPersona = viewerIsMediaOwner || viewerCompanyIsMediaOwner.orElse(true);
    boolean redactBuyerFinancials = !Boolean.TRUE.equals(isGlobalAdmin) && viewerMediaOwnerPersona;

    // ---- Persona enrichment -------------------------------------------------
    // Same redaction rules as the Plan Approval inbox: per-media-owner progress is
    // buyer-side only (creator / shared access / global admin); a media-owner viewer
    // only ever gets their own slice (viewerProposal), never other owners' state.
    // Buyer-side = global admin, or a non-media-owner persona with creator/shared
    // access. Creator status never overrides a media-owner persona: a media-owner
    // creator gets only its own slice, never other owners' state.
    boolean viewerIsBuyerSide =
        Boolean.TRUE.equals(isGlobalAdmin)
            || (!viewerMediaOwnerPersona && (viewerIsCreator || viewerHasSharedAccess));

    List<CampaignProposalStatus> allProposals =
        campaignProposalStatusRepository.findByCampaignId(campaignId);
    Map<String, ApprovalInboxItemDTO.MediaOwnerProgressDTO> ownerProgress =
        allProposals.isEmpty()
            ? Map.of()
            : buildOwnerProgress(campaignId, allProposals, ownersWithOpenCounterOffer(campaignId));

    // Build and return response
    return CampaignApprovalDetailsResponseDTO.builder()
        .campaignName(campaign.getName())
        .campaignId(campaign.getId())
        .planNumber(campaign.getPlanNumber())
        .status(campaignStatus)
        .workflowStatus(approvalStatus != null ? approvalStatus.name() : null)
        .budget(redactBuyerFinancials ? null : campaign.getBudget())
        .currency(campaign.getCurrency())
        .startDate(campaign.getStartDate())
        .endDate(campaign.getEndDate())
        .approvalPermissions(approvalPermissions)
        .approvalProgress(approvalProgress)
        .isNegotiated(campaign.getIsNegotiated())
        .mediaOwners(viewerIsBuyerSide ? new ArrayList<>(ownerProgress.values()) : null)
        // Every non-buyer-side media-owner persona gets at most its own slice
        .viewerProposal(
            viewerMediaOwnerPersona && !viewerIsBuyerSide ? ownerProgress.get(userCompanyId) : null)
        .build();
  }

  private CampaignApprovedWorkflowStatus.Status mediaOwnerWorkflowStatus(
      Campaign.Status campaignStatus) {

    if (campaignStatus == Campaign.Status.APPROVED) {
      return CampaignApprovedWorkflowStatus.Status.COMPLETED;
    } else if (campaignStatus == Campaign.Status.REJECTED) {
      return CampaignApprovedWorkflowStatus.Status.REJECTED;
    } else {
      return null;
    }
  }

  /**
   * Resolves the status shown for a stage row. Non-Media-Owner rows use their own stored status.
   * For the Media Owner row: a media-owner viewer sees the status derived from their own proposal
   * (so it is accurate per media owner in multi-owner campaigns); other viewers see the
   * campaign-level media-owner status when resolved, else the stored stage status.
   */
  private CampaignApprovedWorkflowStatus.Status resolveDisplayStatus(
      CampaignApprovedWorkflowStatus workflowStatus,
      boolean viewerIsMediaOwner,
      CampaignProposalStatus viewerProposal,
      CampaignApprovedWorkflowStatus.Status campaignLevelMediaOwnerStatus) {

    if (workflowStatus.getApprovalAuthority()
        != CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER) {
      return workflowStatus.getStatus();
    }

    if (viewerIsMediaOwner && viewerProposal != null) {
      return switch (viewerProposal.getStatus()) {
        case APPROVED -> CampaignApprovedWorkflowStatus.Status.COMPLETED;
        case REJECTED -> CampaignApprovedWorkflowStatus.Status.REJECTED;
        case NEGOTIATING -> CampaignApprovedWorkflowStatus.Status.CHANGES_REQUESTED;
        case PENDING -> workflowStatus.getStatus();
      };
    }

    return campaignLevelMediaOwnerStatus != null
        ? campaignLevelMediaOwnerStatus
        : workflowStatus.getStatus();
  }

  /**
   * Determines and returns all approval permissions applicable for the given campaign and user
   * company.
   *
   * @param campaign the campaign entity
   * @param campaignId the campaign identifier
   * @param userCompanyId the logged-in user's company identifier
   * @return list of applicable approval permissions
   */
  private List<CampaignApprovalDetailsResponseDTO.ApprovalPermission> getApprovalPermissions(
      Campaign campaign,
      String campaignId,
      String userCompanyId,
      Boolean isGlobalAdmin,
      boolean isMediaOwner,
      boolean campaignCreatedByMediaOwner) {

    List<CampaignApprovalDetailsResponseDTO.ApprovalPermission> permissions = new ArrayList<>();

    // Super Admin: Grant all approval permissions
    if (Boolean.TRUE.equals(isGlobalAdmin)) {
      log.info(
          "User is a global admin. Granting all approval permissions for campaignId: {}",
          campaignId);
      // Agency/Internal never apply to a media-owner-created campaign — not even for admins.
      if (!campaignCreatedByMediaOwner) {
        if (!isTwoStageEnabled()) {
          permissions.add(CampaignApprovalDetailsResponseDTO.ApprovalPermission.AGENCY);
        }
        permissions.add(CampaignApprovalDetailsResponseDTO.ApprovalPermission.INTERNAL);
      }

      // Check if there are any media owner proposals for this campaign
      List<CampaignProposalStatus> proposals =
          campaignProposalStatusRepository.findStatusesByCampaignId(campaignId);
      if (!proposals.isEmpty()) {
        permissions.add(CampaignApprovalDetailsResponseDTO.ApprovalPermission.MEDIA_OWNER);
      }

      log.debug("Global admin granted all approval permissions for campaignId: {}", campaignId);
      return permissions;
    }

    // Regular users: Add permissions based on role
    if (!isMediaOwner) {
      addMaintainerPermissions(permissions, campaign, userCompanyId, campaignCreatedByMediaOwner);
    }
    addMediaOwnerPermission(permissions, campaignId, userCompanyId);

    return permissions;
  }

  /**
   * Adds the company-side (INTERNAL) permission when the user is a campaign maintainer. The AGENCY
   * permission is only added in the legacy 3-stage flow; under the 2-stage flow the company side
   * has a single stage (Internal). Neither applies when the campaign was created by a media-owner
   * company — there is no company-side party to review.
   */
  private void addMaintainerPermissions(
      List<CampaignApprovalDetailsResponseDTO.ApprovalPermission> permissions,
      Campaign campaign,
      String userCompanyId,
      boolean campaignCreatedByMediaOwner) {

    if (isMaintainer(campaign, userCompanyId) && !campaignCreatedByMediaOwner) {
      if (!isTwoStageEnabled()) {
        permissions.add(CampaignApprovalDetailsResponseDTO.ApprovalPermission.AGENCY);
      }
      permissions.add(CampaignApprovalDetailsResponseDTO.ApprovalPermission.INTERNAL);
    }
  }

  /** Adds MEDIA_OWNER permission when a proposal exists for the given campaign and media owner. */
  private void addMediaOwnerPermission(
      List<CampaignApprovalDetailsResponseDTO.ApprovalPermission> permissions,
      String campaignId,
      String userCompanyId) {

    // Skip if userCompanyId is null (super admins handled separately)
    if (userCompanyId == null || userCompanyId.trim().isEmpty()) {
      return;
    }

    CampaignProposalStatus proposalStatus =
        campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, userCompanyId);

    if (proposalStatus != null) {
      permissions.add(CampaignApprovalDetailsResponseDTO.ApprovalPermission.MEDIA_OWNER);
    }
  }

  /**
   * Get the latest comment from approval history for a workflow status.
   *
   * @param workflowStatusId Workflow status ID
   * @return Latest comment or null if no history found
   */
  private String getLatestComment(String workflowStatusId) {
    List<CampaignApprovalHistory> historyList =
        campaignApprovalHistoryRepository.findByCampaignApprovedWorkflowStatusId(workflowStatusId);

    if (historyList.isEmpty()) {
      return null;
    }

    // Get the latest history entry (most recent by createdAt)
    return historyList.stream()
        .max(Comparator.comparing(CampaignApprovalHistory::getCreatedAt))
        .map(CampaignApprovalHistory::getComment)
        .orElse(null);
  }

  /**
   * Check if the logged-in user is a maintainer (agency/internal) of the campaign. A user is a
   * maintainer if their company ID matches the campaign's company ID.
   *
   * @param campaign Campaign entity
   * @param userCompanyId Logged-in user's company ID
   * @return true if user is maintainer (agency/internal), false if media owner
   */
  public boolean isMaintainer(Campaign campaign, String userCompanyId) {
    if (campaign == null || userCompanyId == null) {
      return false;
    }
    return campaign.getCompanyId().equals(userCompanyId);
  }

  /**
   * Get campaign status based on workflow statuses, proposal statuses, and user type. This method
   * determines the appropriate status enum for display purposes.
   *
   * @param campaign Campaign entity
   * @param userCompanyId Logged-in user's company ID
   * @return CampaignApprovalStatus enum value
   */
  public CampaignApprovalStatus getCampaignStatus(
      Campaign campaign, String userCompanyId, Boolean isMediaOwner) {
    if (campaign == null) {
      return null;
    }

    // Check campaign-level statuses first - return as-is for REJECTED, NEGOTIATING, APPROVED
    Campaign.Status campaignStatus = campaign.getStatus();
    if (campaignStatus == Campaign.Status.REJECTED) {
      return CampaignApprovalStatus.REJECTED;
    }
    if (campaignStatus == Campaign.Status.NEGOTIATING) {
      return CampaignApprovalStatus.IN_NEGOTIATION;
    }
    if (campaignStatus == Campaign.Status.APPROVED) {
      return CampaignApprovalStatus.APPROVED;
    }

    CampaignApprovalStatus result = null;

    try {
      // Check if user is maintainer (agency/internal) or media owner. A media-owner viewer is
      // never treated as a maintainer, even if their company happens to own the campaign.
      boolean isMaintainer =
          !Boolean.TRUE.equals(isMediaOwner) && isMaintainer(campaign, userCompanyId);

      // Fetch workflow statuses and proposal statuses in parallel for performance
      List<CampaignApprovedWorkflowStatus> workflowStatuses =
          campaignApprovedWorkflowStatusRepository.findByCampaignId(campaign.getId());
      List<CampaignProposalStatus> proposalStatuses =
          campaignProposalStatusRepository.findStatusesByCampaignId(campaign.getId());

      if (isMaintainer) {
        // For agency/internal: check workflow statuses
        Optional<CampaignApprovedWorkflowStatus> agencyStatus =
            workflowStatuses.stream()
                .filter(
                    ws ->
                        ws.getApprovalAuthority()
                            == CampaignApprovedWorkflowStatus.ApprovalAuthority.AGENCY)
                .findFirst();

        Optional<CampaignApprovedWorkflowStatus> internalStatus =
            workflowStatuses.stream()
                .filter(
                    ws ->
                        ws.getApprovalAuthority()
                            == CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL)
                .findFirst();

        // If agency is in progress
        if (agencyStatus.isPresent()
            && agencyStatus.get().getStatus()
                == CampaignApprovedWorkflowStatus.Status.IN_PROGRESS) {
          result = CampaignApprovalStatus.AWAITING_AGENCY_ACCEPTANCE;
        }

        // If internal is in progress
        if (result == null
            && internalStatus.isPresent()
            && internalStatus.get().getStatus()
                == CampaignApprovedWorkflowStatus.Status.IN_PROGRESS) {
          result = CampaignApprovalStatus.AWAITING_INTERNAL_REVIEW;
        }

        // Check if any proposal are pending
        if (result == null
            && !proposalStatuses.isEmpty()
            && proposalStatuses.stream()
                .anyMatch(
                    proposal -> proposal.getStatus() == CampaignProposalStatus.Status.PENDING)) {
          result = CampaignApprovalStatus.AWAITING_MEDIA_OWNER_APPROVAL;
        }
      } else {
        // For media owner: check their specific proposal status
        // Check if any proposal are pending
        if (result == null
            && !proposalStatuses.isEmpty()
            && proposalStatuses.stream()
                .anyMatch(
                    proposal -> proposal.getStatus() == CampaignProposalStatus.Status.PENDING)) {
          result = CampaignApprovalStatus.AWAITING_MEDIA_OWNER_APPROVAL;
        }
        Optional<CampaignProposalStatus> mediaOwnerProposal =
            proposalStatuses.stream()
                .filter(
                    proposal ->
                        userCompanyId != null && userCompanyId.equals(proposal.getMediaOwnerId()))
                .findFirst();

        // If media owner's proposal is approved, return approved
        if (mediaOwnerProposal.isPresent()
            && mediaOwnerProposal.get().getStatus() == CampaignProposalStatus.Status.APPROVED) {
          result = CampaignApprovalStatus.APPROVED;
        }
      }
    } catch (Exception e) {
      // If any exception occurs when fetching/processing workflow or proposal statuses,
      // log the error and fall back to campaign status
      log.warn(
          "Exception occurred while fetching campaign status from workflow/proposal statuses for campaignId: {}. Falling back to campaign status. Error: {}",
          campaign.getId(),
          e.getMessage(),
          e);
    }

    // If status is not set from getCampaignStatus logic, try to use campaign status
    if (result == null && campaignStatus != null) {
      try {
        result = CampaignApprovalStatus.valueOf(campaignStatus.name());
      } catch (IllegalArgumentException e) {
        // If campaign status doesn't map to CampaignApprovalStatus, return null
        log.warn("Campaign status {} does not map to CampaignApprovalStatus enum", campaignStatus);
        return null;
      }
    }

    return result;
  }
}
