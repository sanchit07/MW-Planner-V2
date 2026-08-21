package com.mw.planner.service;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.Creative;
import com.mw.planner.domain.CreativeAssignment;
import com.mw.planner.domain.Inventory;
import com.mw.planner.dto.creative.CreativeAssignmentDTO;
import com.mw.planner.dto.creative.CreativeAssignmentRequestDTO;
import com.mw.planner.exception.campaign.CampaignNotFoundException;
import com.mw.planner.exception.creative.CreativeAspectRatioMismatchException;
import com.mw.planner.exception.creative.CreativeAssignmentNotFoundException;
import com.mw.planner.exception.creative.CreativeCampaignStatusIneligibleException;
import com.mw.planner.exception.creative.CreativeDurationMismatchException;
import com.mw.planner.exception.creative.CreativeNotAcceptedException;
import com.mw.planner.exception.creative.CreativeNotFoundException;
import com.mw.planner.repository.CampaignInventorySchedulesRepository;
import com.mw.planner.repository.CampaignRepository;
import com.mw.planner.repository.CreativeAssignmentRepository;
import com.mw.planner.repository.CreativeRepository;
import com.mw.planner.repository.InventoryRepository;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Creative Assignment — binds a {@link Creative} to a campaign line item ({@link
 * CampaignInventorySchedules}), enforcing the five gating rules from PRD §11.1. V1 enforced none of
 * these server-side (see the creatives V1-vs-V2 research note); this is genuinely new validation,
 * not a port of existing logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeAssignmentService {

  private final CreativeAssignmentRepository creativeAssignmentRepository;
  private final CreativeRepository creativeRepository;
  private final CampaignInventorySchedulesRepository campaignInventorySchedulesRepository;
  private final CampaignRepository campaignRepository;
  private final InventoryRepository inventoryRepository;
  private final CampaignApprovalWorkflowService campaignApprovalWorkflowService;
  private final UserService userService;

  /**
   * Rule 4: only these campaign statuses may accept a creative binding, per PRD's "Reviewing,
   * Approved, Active, Paused".
   */
  private static final Set<Campaign.Status> ELIGIBLE_STATUSES =
      EnumSet.of(
          Campaign.Status.REVIEWING,
          Campaign.Status.APPROVED,
          Campaign.Status.ACTIVE,
          Campaign.Status.PAUSE);

  public CreativeAssignmentDTO getForLineItem(String lineItemId) {
    CreativeAssignment assignment =
        creativeAssignmentRepository
            .findByLineItemId(lineItemId)
            .orElseThrow(() -> new CreativeAssignmentNotFoundException(lineItemId));
    assertCanActForCampaign(assignment.getCampaignId());
    return CreativeAssignmentDTO.from(assignment);
  }

  public List<CreativeAssignmentDTO> listForCampaign(String campaignId) {
    assertCanActForCampaign(campaignId);
    return creativeAssignmentRepository.findByCampaignId(campaignId).stream()
        .map(CreativeAssignmentDTO::from)
        .toList();
  }

  public CreativeAssignmentDTO bind(CreativeAssignmentRequestDTO request) {
    Creative creative =
        creativeRepository
            .findById(request.getCreativeId())
            .orElseThrow(() -> new CreativeNotFoundException(request.getCreativeId()));
    CampaignInventorySchedules lineItem =
        campaignInventorySchedulesRepository
            .findById(request.getLineItemId())
            .orElseThrow(() -> new CreativeAssignmentNotFoundException(request.getLineItemId()));
    Campaign campaign =
        campaignRepository
            .findById(lineItem.getCampaignId())
            .orElseThrow(() -> new CampaignNotFoundException(lineItem.getCampaignId()));
    // Buyer-side only: a creative can only be bound onto a line item of a campaign the acting
    // company actually owns or has shared access to (mirrors ExecutionPlanService.isBuyerSide).
    assertCanActForCampaign(campaign.getId());
    // The creative itself must also belong to the acting company's own library.
    if (!creative.getCompanyId().equals(userService.getActingCompanyId())
        && !userService.isCurrentUserGlobalAdmin()) {
      throw new CreativeNotFoundException(creative.getId());
    }
    // Tier 1 gate: only an Accepted creative may be assigned (PRD §11 / creative-management spec
    // — "Cannot assign inadequate creatives"; Processing/Archive are equally ineligible).
    if (creative.getTier1Status() != Creative.Tier1Status.ACCEPTED) {
      throw new CreativeNotAcceptedException(creative.getId(), creative.getTier1Status().name());
    }
    Inventory inventory =
        inventoryRepository
            .findById(lineItem.getInventoryId())
            .orElse(null); // missing inventory degrades gates gracefully rather than blocking

    // Rule 4 — campaign status gate.
    if (!ELIGIBLE_STATUSES.contains(campaign.getStatus())) {
      throw new CreativeCampaignStatusIneligibleException(
          campaign.getId(), campaign.getStatus().name());
    }

    CreativeAssignment.SpecSnapshot newSpec =
        CreativeAssignment.SpecSnapshot.builder()
            .aspectRatio(creative.deriveAspectRatio())
            .durationSeconds(creative.getDurationSeconds())
            .fileSizeBytes(creative.getFileSizeBytes())
            .build();

    boolean forcedMatch = checkAspectRatio(creative, inventory, request.isForceMatch());
    checkDuration(creative, inventory);
    // Rule 3 (file-size cap) is enforced at upload time in CreativeService, against the
    // creative's own format cap — Inventory carries no per-panel CMS spec field yet (IMS gap).

    // Mutate the existing loaded entity in place (preserving its _id) rather than rebuilding via
    // toBuilder() — @Builder on a BaseEntity subclass does not carry superclass fields (id/audit
    // timestamps) through toBuilder(), which would otherwise silently insert a duplicate document
    // and violate the unique index on lineItemId on every re-bind.
    CreativeAssignment assignment =
        creativeAssignmentRepository
            .findByLineItemId(lineItem.getId())
            .orElseGet(() -> CreativeAssignment.builder().lineItemId(lineItem.getId()).build());
    boolean isNew = assignment.getId() == null;
    boolean sameSpecSwap = !isNew && newSpec.sameSpecAs(assignment.getSpecSnapshot());
    boolean needsReapproval =
        !isNew && !sameSpecSwap && campaign.getStatus() == Campaign.Status.APPROVED;

    assignment.setCreativeId(creative.getId());
    assignment.setCampaignId(lineItem.getCampaignId());
    assignment.setMediaOwnerId(lineItem.getMediaOwnerId());
    assignment.setInventoryId(lineItem.getInventoryId());
    assignment.setSpecSnapshot(newSpec);

    if (needsReapproval) {
      assignment.setBindingStatus(CreativeAssignment.BindingStatus.PENDING_REAPPROVAL);
    } else if (forcedMatch) {
      assignment.setBindingStatus(CreativeAssignment.BindingStatus.FORCED_MATCH);
      assignment.setForcedMatch(true);
      assignment.setForcedMatchReason("Aspect ratio override confirmed by caller");
      assignment.setForcedMatchBy(currentUserIdOrNull());
      assignment.setForcedMatchAt(LocalDateTime.now());
    } else {
      assignment.setBindingStatus(CreativeAssignment.BindingStatus.BOUND);
      assignment.setForcedMatch(false);
      assignment.setForcedMatchReason(null);
    }

    CreativeAssignment saved = creativeAssignmentRepository.save(assignment);

    if (needsReapproval) {
      // Rule 5: a different-spec swap on an Approved campaign re-opens Tier 2 for the affected
      // media owner, mirroring the price-change re-approval trigger already used by
      // CustomFeeService.
      campaignApprovalWorkflowService.resetApprovalWorkflowStatus(lineItem.getCampaignId());
      log.info(
          "Creative swap on line item {} triggered re-approval for campaign {}",
          lineItem.getId(),
          lineItem.getCampaignId());
    }

    return CreativeAssignmentDTO.from(saved);
  }

  /** Rule 1 — returns whether a forced (overridden) match was applied. */
  private boolean checkAspectRatio(Creative creative, Inventory inventory, boolean forceMatch) {
    String creativeRatio = creative.deriveAspectRatio();
    String inventoryRatio = inventoryAspectRatio(inventory);
    if (creativeRatio == null
        || inventoryRatio == null
        || Objects.equals(creativeRatio, inventoryRatio)) {
      return false;
    }
    if (!forceMatch) {
      throw new CreativeAspectRatioMismatchException(creativeRatio, inventoryRatio);
    }
    return true;
  }

  /**
   * Rule 2 — never overridable. Only applies to inventories with a digital spot duration on file.
   */
  private void checkDuration(Creative creative, Inventory inventory) {
    if (creative.getDurationSeconds() == null) return; // static creative — no duration to match
    Integer requiredSeconds =
        inventory != null && inventory.getDigitalFields() != null
            ? inventory.getDigitalFields().getSpotDuration()
            : null;
    if (requiredSeconds == null)
      return; // no accepted-slot-length data on file — nothing to enforce
    if (!requiredSeconds.equals(creative.getDurationSeconds())) {
      throw new CreativeDurationMismatchException(creative.getDurationSeconds(), requiredSeconds);
    }
  }

  private String inventoryAspectRatio(Inventory inventory) {
    if (inventory == null || inventory.getPanels() == null || inventory.getPanels().isEmpty()) {
      return null;
    }
    Inventory.Panel panel = inventory.getPanels().get(0);
    if (panel.getPixelWidth() == null || panel.getPixelHeight() == null) return null;
    int w = panel.getPixelWidth();
    int h = panel.getPixelHeight();
    if (w <= 0 || h <= 0) return null;
    int gcd = gcd(w, h);
    return (w / gcd) + ":" + (h / gcd);
  }

  private static int gcd(int a, int b) {
    return b == 0 ? a : gcd(b, a % b);
  }

  private String currentUserIdOrNull() {
    try {
      return userService.getIamUserContext().getUserId();
    } catch (Exception e) {
      return null;
    }
  }

  /** Buyer-side access only, mirroring ExecutionPlanService.loadCampaignWithAccessCheck. */
  private void assertCanActForCampaign(String campaignId) {
    if (userService.isCurrentUserGlobalAdmin()) {
      return;
    }
    Campaign campaign =
        campaignRepository
            .findById(campaignId)
            .orElseThrow(() -> new CampaignNotFoundException(campaignId));
    String actingCompanyId = userService.getActingCompanyId();
    boolean isBuyer =
        actingCompanyId != null
            && (actingCompanyId.equals(campaign.getCompanyId())
                || (campaign.getCompanyAccess() != null
                    && campaign.getCompanyAccess().contains(actingCompanyId)));
    if (!isBuyer) {
      // 404 rather than 403 to avoid leaking campaign existence across companies.
      throw new CampaignNotFoundException(campaignId);
    }
  }
}
