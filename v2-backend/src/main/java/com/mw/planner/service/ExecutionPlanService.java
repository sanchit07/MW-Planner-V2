package com.mw.planner.service;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.ExecutionPlan;
import com.mw.planner.domain.Inventory;
import com.mw.planner.domain.Schedule;
import com.mw.planner.dto.ExecutionPlanResponseDTO;
import com.mw.planner.dto.ExecutionPlanStatusDTO;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.exception.campaign.CampaignInvalidStatusException;
import com.mw.planner.exception.campaign.CampaignNotFoundException;
import com.mw.planner.repository.CampaignInventorySchedulesRepository;
import com.mw.planner.repository.ExecutionPlanRepository;
import com.mw.planner.repository.ScheduleRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Builds, saves and pushes the campaign Execution Plan.
 *
 * <p>Baseline generation groups the campaign's selected inventories per media owner and per
 * classification: digital inventory routes to INFLUENCE (GUARANTEED when the campaign has a DSP,
 * DIRECT otherwise), classic/transit inventory routes to the OMS as an ORDER line.
 *
 * <p>Pushing requires an APPROVED campaign with no unaccepted price changes. It queues every
 * pending line, locks the plan, and promotes the campaign to ACTIVE — but only from a pre-live
 * status so a paused or completed campaign is never downgraded.
 *
 * <p>Handoff to Influence/OMS is simulated in this environment: after a push each line advances
 * QUEUED → SENT → ACKNOWLEDGED on a timer as the plan is re-read; a deterministic subset of lines
 * fails on the first attempt so the per-line retry path is exercised, and retries always succeed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionPlanService {

  private static final Set<Campaign.Status> PRE_LIVE_STATUSES =
      EnumSet.of(
          Campaign.Status.DRAFT,
          Campaign.Status.PLANNED,
          Campaign.Status.REVIEWING,
          Campaign.Status.APPROVED);

  /** Push block reasons surfaced to the client (translated there). */
  public static final String BLOCK_NOT_APPROVED = "NOT_APPROVED";

  public static final String BLOCK_UNACCEPTED_PRICES = "UNACCEPTED_PRICES";
  public static final String BLOCK_NO_LINES = "NO_LINES";

  /** Simulated transport timings for the staged handoff. */
  private static final Duration QUEUED_TO_SENT = Duration.ofSeconds(3);

  private static final Duration SENT_TO_FINAL = Duration.ofSeconds(7);

  private final ExecutionPlanRepository executionPlanRepository;
  private final CampaignService campaignService;
  private final CampaignInventorySchedulesService campaignInventorySchedulesService;
  private final InventoryService inventoryService;
  private final ScheduleRepository scheduleRepository;
  private final CampaignActivityService campaignActivityService;
  private final UserService userService;
  private final CampaignInventorySchedulesRepository campaignInventorySchedulesRepository;
  private final com.mw.planner.repository.CampaignProposalStatusRepository
      campaignProposalStatusRepository;
  private final CompanyService companyService;

  /** Get the execution plan for a campaign, generating and persisting a baseline if absent. */
  public ExecutionPlanResponseDTO getExecutionPlan(String campaignId) {
    Campaign campaign = loadCampaignWithAccessCheck(campaignId);
    ExecutionPlan plan = advanceHandoffs(getOrCreatePlan(campaign));
    return toDto(campaign, plan);
  }

  /**
   * Lightweight execution state for the campaign view. Never generates a baseline — a campaign that
   * was never opened in the Execution Plan page simply reports {@code exists=false}.
   */
  public ExecutionPlanStatusDTO getExecutionPlanStatus(String campaignId) {
    loadCampaignWithAccessCheck(campaignId);
    ExecutionPlan plan = executionPlanRepository.findByCampaignId(campaignId).orElse(null);
    if (plan == null) {
      return ExecutionPlanStatusDTO.builder().campaignId(campaignId).exists(false).build();
    }
    plan = advanceHandoffs(plan);
    int lineCount = plan.getLines() != null ? plan.getLines().size() : 0;
    int acknowledged = 0;
    int failed = 0;
    int inProgress = 0;
    if (plan.getLines() != null) {
      for (ExecutionPlan.Line line : plan.getLines()) {
        ExecutionPlan.HandoffStatus status = normalize(line.getHandoffStatus());
        if (status == ExecutionPlan.HandoffStatus.ACKNOWLEDGED) {
          acknowledged++;
        } else if (status == ExecutionPlan.HandoffStatus.FAILED) {
          failed++;
        } else if (status == ExecutionPlan.HandoffStatus.QUEUED
            || status == ExecutionPlan.HandoffStatus.SENT) {
          inProgress++;
        }
      }
    }
    return ExecutionPlanStatusDTO.builder()
        .campaignId(campaignId)
        .exists(true)
        .locked(plan.isLocked())
        .pushedAt(plan.getPushedAt())
        .lineCount(lineCount)
        .acknowledgedCount(acknowledged)
        .failedCount(failed)
        .inProgressCount(inProgress)
        .build();
  }

  /** Regenerate the baseline plan. Rejected once the plan is locked (already pushed). */
  public ExecutionPlanResponseDTO resetExecutionPlan(String campaignId) {
    Campaign campaign = loadCampaignWithAccessCheck(campaignId);
    ExecutionPlan existing = executionPlanRepository.findByCampaignId(campaignId).orElse(null);
    if (existing != null && existing.isLocked()) {
      throw new CampaignInvalidStatusException(
          campaign.getStatus(), "not yet pushed (plan is locked)");
    }
    // Conditional delete: only removes an unlocked plan, so a reset racing a
    // concurrent push can never wipe a freshly locked (pushed) plan.
    if (existing != null
        && executionPlanRepository.deleteByCampaignIdAndLockedIsFalse(campaignId) == 0) {
      throw new CampaignInvalidStatusException(
          campaign.getStatus(), "not yet pushed (plan is locked)");
    }
    return toDto(campaign, getOrCreatePlan(campaign));
  }

  /**
   * Find-or-create behind the unique campaignId index: if a concurrent request inserted the
   * baseline first, the duplicate-key error is resolved by re-reading the winner's plan.
   */
  private ExecutionPlan getOrCreatePlan(Campaign campaign) {
    return executionPlanRepository
        .findByCampaignId(campaign.getId())
        .orElseGet(
            () -> {
              try {
                return executionPlanRepository.save(generateBaseline(campaign));
              } catch (org.springframework.dao.DuplicateKeyException e) {
                return executionPlanRepository
                    .findByCampaignId(campaign.getId())
                    .orElseThrow(() -> e);
              }
            });
  }

  /**
   * Push the execution plan: queue all pending lines for handoff (or retry the given failed lines
   * when the plan is already locked), lock the plan, and promote the campaign to ACTIVE.
   *
   * <p>Guardrails: an initial push is only allowed from an APPROVED campaign with no changed prices
   * awaiting acceptance. Retries of failed lines stay allowed after the campaign is live.
   */
  public ExecutionPlanResponseDTO pushExecutionPlan(String campaignId, List<String> retryLineIds) {
    return pushExecutionPlan(campaignId, retryLineIds, null);
  }

  public ExecutionPlanResponseDTO pushExecutionPlan(
      String campaignId, List<String> retryLineIds, List<String> lineIds) {
    // Resolve the caller: buyer-side companies keep the campaign-wide push; a media owner may
    // only push their OWN lines, and only after approving the plan.
    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);
    IamUserContext userContext = userService.getIamUserContext();
    String viewer = resolveViewerCompany(userContext);
    boolean buyerSide = isBuyerSide(campaign, viewer);
    Set<String> ownLineIds = null; // null = unrestricted (buyer)
    if (!buyerSide) {
      requireApprovedProposalOwner(campaignId);
      final String owner = viewer;
      ExecutionPlan forScope = executionPlanRepository.findByCampaignId(campaignId).orElse(null);
      ownLineIds =
          forScope == null || forScope.getLines() == null
              ? Set.of()
              : forScope.getLines().stream()
                  .filter(l -> owner.equals(l.getMediaOwnerId()))
                  .map(ExecutionPlan.Line::getId)
                  .collect(java.util.stream.Collectors.toSet());
      // A media owner push is always scoped to their own lines; requested IDs outside that
      // set are rejected rather than silently dropped.
      List<String> requested =
          retryLineIds != null && !retryLineIds.isEmpty() ? retryLineIds : lineIds;
      if (requested != null) {
        for (String id : requested) {
          if (!ownLineIds.contains(id)) {
            throw new CampaignNotFoundException(campaignId);
          }
        }
      }
      if (lineIds == null || lineIds.isEmpty()) {
        lineIds = new ArrayList<>(ownLineIds);
      }
    }

    ExecutionPlan plan =
        executionPlanRepository
            .findByCampaignId(campaignId)
            .orElseGet(() -> generateBaseline(campaign));
    plan = advanceHandoffs(plan);

    boolean isRetry = retryLineIds != null && !retryLineIds.isEmpty();

    // Server-side push guardrails. Retries only re-send already-failed, already-approved work,
    // so they are exempt (the campaign is typically ACTIVE by then).
    if (!isRetry) {
      if (campaign.getStatus() != Campaign.Status.APPROVED
          && campaign.getStatus() != Campaign.Status.ACTIVE) {
        throw new CampaignInvalidStatusException(campaign.getStatus(), Campaign.Status.APPROVED);
      }
      if (hasUnacceptedPrices(campaignId)) {
        throw new CampaignInvalidStatusException(
            campaign.getStatus(), "APPROVED with all price changes accepted");
      }
      if (plan.getLines() == null || plan.getLines().isEmpty()) {
        throw new CampaignInvalidStatusException(
            campaign.getStatus(), "a plan with at least one execution line");
      }
    }

    LocalDateTime now = LocalDateTime.now();
    int queued = 0;
    if (plan.getLines() != null) {
      for (ExecutionPlan.Line line : plan.getLines()) {
        ExecutionPlan.HandoffStatus status = normalize(line.getHandoffStatus());
        boolean owned = ownLineIds == null || ownLineIds.contains(line.getId());
        boolean inScope = lineIds == null || lineIds.isEmpty() || lineIds.contains(line.getId());
        boolean eligible =
            isRetry
                ? owned
                    && retryLineIds.contains(line.getId())
                    && status == ExecutionPlan.HandoffStatus.FAILED
                : owned
                    && inScope
                    && (status == ExecutionPlan.HandoffStatus.PENDING_HANDOFF
                        || status == ExecutionPlan.HandoffStatus.FAILED);
        if (!eligible) {
          continue;
        }
        line.setHandoffStatus(ExecutionPlan.HandoffStatus.QUEUED);
        line.setHandoffError(null);
        line.setHandedOffAt(null);
        line.setQueuedAt(now);
        line.setAttemptCount(line.getAttemptCount() == null ? 1 : line.getAttemptCount() + 1);
        queued++;
      }
    }

    if (queued == 0) {
      throw new CampaignInvalidStatusException(
          campaign.getStatus(), "at least one pending line to push");
    }

    boolean anyDispatched =
        plan.getLines() != null
            && plan.getLines().stream()
                .anyMatch(
                    l ->
                        normalize(l.getHandoffStatus())
                            != ExecutionPlan.HandoffStatus.PENDING_HANDOFF);
    if (anyDispatched) {
      plan.setLocked(true);
      if (plan.getPushedAt() == null) {
        plan.setPushedAt(now);
      }
    }
    plan = executionPlanRepository.save(plan);

    // Going live is a side effect of a successful push — never downgrade a live/paused/completed
    // campaign.
    if (anyDispatched && PRE_LIVE_STATUSES.contains(campaign.getStatus())) {
      campaignService.changeCampaignStatus(campaignId, Campaign.Status.ACTIVE);
    }

    campaignActivityService.logActivity(
        campaignId,
        CampaignActivityService.OperationType.UPDATED,
        isRetry ? "execution_plan_retry" : "execution_plan_push",
        queued + " line(s) queued for handoff");

    Campaign refreshed = campaignService.findByIdForCurrentMode(campaignId);
    return toDto(refreshed, plan);
  }

  // --- internals ---

  private boolean hasUnacceptedPrices(String campaignId) {
    // Same predicate as the approval inbox: a schedule doc whose price history was changed
    // (size > 1) but never accepted (approvedBy null) blocks going live.
    return campaignInventorySchedulesRepository
        .existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull(campaignId);
  }

  /** Map the legacy terminal HANDED_OFF value onto the staged lifecycle. */
  private static ExecutionPlan.HandoffStatus normalize(ExecutionPlan.HandoffStatus status) {
    return status == ExecutionPlan.HandoffStatus.HANDED_OFF
        ? ExecutionPlan.HandoffStatus.ACKNOWLEDGED
        : status;
  }

  /**
   * Simulated transport: advance QUEUED/SENT lines based on time elapsed since they were queued.
   * First attempts fail deterministically for a subset of lines (so retry is a real path); retries
   * always succeed. Persists the plan when anything changed.
   */
  private ExecutionPlan advanceHandoffs(ExecutionPlan plan) {
    if (plan.getLines() == null) {
      return plan;
    }
    LocalDateTime now = LocalDateTime.now();
    boolean changed = false;
    for (ExecutionPlan.Line line : plan.getLines()) {
      // Normalize legacy terminal value.
      if (line.getHandoffStatus() == ExecutionPlan.HandoffStatus.HANDED_OFF) {
        line.setHandoffStatus(ExecutionPlan.HandoffStatus.ACKNOWLEDGED);
        changed = true;
      }
      ExecutionPlan.HandoffStatus status = line.getHandoffStatus();
      if (status != ExecutionPlan.HandoffStatus.QUEUED
          && status != ExecutionPlan.HandoffStatus.SENT) {
        continue;
      }
      LocalDateTime queuedAt = line.getQueuedAt() != null ? line.getQueuedAt() : now;
      Duration elapsed = Duration.between(queuedAt, now);
      if (elapsed.compareTo(SENT_TO_FINAL) >= 0) {
        int attempt = line.getAttemptCount() == null ? 1 : line.getAttemptCount();
        if (attempt <= 1 && shouldSimulateFailure(line)) {
          line.setHandoffStatus(ExecutionPlan.HandoffStatus.FAILED);
          line.setHandoffError(
              (line.getDestination() == ExecutionPlan.Destination.OMS ? "OMS" : "Influence")
                  + " did not acknowledge the handoff (simulated transport failure). Retry to"
                  + " re-send this line.");
        } else {
          line.setHandoffStatus(ExecutionPlan.HandoffStatus.ACKNOWLEDGED);
          line.setHandoffError(null);
          line.setHandedOffAt(queuedAt.plus(SENT_TO_FINAL));
        }
        changed = true;
      } else if (elapsed.compareTo(QUEUED_TO_SENT) >= 0
          && status == ExecutionPlan.HandoffStatus.QUEUED) {
        line.setHandoffStatus(ExecutionPlan.HandoffStatus.SENT);
        changed = true;
      }
    }
    return changed ? executionPlanRepository.save(plan) : plan;
  }

  /** Deterministic per-line failure so the retry path is demonstrable without flakiness. */
  private static boolean shouldSimulateFailure(ExecutionPlan.Line line) {
    String id = line.getId() != null ? line.getId() : "";
    return Math.floorMod(id.hashCode(), 4) == 0;
  }

  /**
   * Buyer-side access only: creator or shared-access companies. Media owners are NOT admitted here
   * — they use the workspace surface, which loads via {@link #loadForProposalOwner}.
   */
  private Campaign loadCampaignWithAccessCheck(String campaignId) {
    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);
    // Buyer-side access follows the acting (switched) company, never the primary company:
    // a dual member switched into a media-owner company must use the workspace path only.
    String companyId = resolveViewerCompany(userService.getIamUserContext());
    if (!isBuyerSide(campaign, companyId)) {
      // 404 rather than 403 to avoid leaking campaign existence across companies.
      throw new CampaignNotFoundException(campaignId);
    }
    return campaign;
  }

  private static boolean isBuyerSide(Campaign campaign, String companyId) {
    return companyId != null
        && (companyId.equals(campaign.getCompanyId())
            || (campaign.getCompanyAccess() != null
                && campaign.getCompanyAccess().contains(companyId)));
  }

  private record ProposalOwnerContext(
      Campaign campaign, String viewer, com.mw.planner.domain.CampaignProposalStatus proposal) {}

  /**
   * Workspace access path: the viewer (validated switched tenant or active company) must own a
   * proposal on this campaign. 404 otherwise — this surface never serves buyers.
   */
  private ProposalOwnerContext loadForProposalOwner(String campaignId) {
    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);
    String viewer = resolveViewerCompany(userService.getIamUserContext());
    com.mw.planner.domain.CampaignProposalStatus proposal =
        viewer == null
            ? null
            : campaignProposalStatusRepository.findByCampaignIdAndMediaOwnerId(campaignId, viewer);
    if (proposal == null) {
      throw new CampaignNotFoundException(campaignId);
    }
    return new ProposalOwnerContext(campaign, viewer, proposal);
  }

  /** Workspace mutations additionally require the viewer to have approved the plan. */
  private ProposalOwnerContext requireApprovedProposalOwner(String campaignId) {
    ProposalOwnerContext ctx = loadForProposalOwner(campaignId);
    if (ctx.proposal().getStatus()
        != com.mw.planner.domain.CampaignProposalStatus.Status.APPROVED) {
      throw new CampaignInvalidStatusException(
          ctx.campaign().getStatus(), "your approval of this plan");
    }
    return ctx;
  }

  /** Effective viewer company: validated switched tenant, else the context's active company. */
  private String resolveViewerCompany(IamUserContext userContext) {
    String acting = userService.getActingCompanyId();
    if (acting != null) {
      return acting;
    }
    return userContext != null ? userContext.getCompanyId() : null;
  }

  private ExecutionPlan generateBaseline(Campaign campaign) {
    List<CampaignInventorySchedules> schedules =
        campaignInventorySchedulesService.findByCampaignId(campaign.getId());

    // Group inventory by media owner + classification bucket.
    Map<String, ExecutionPlan.Line> lineMap = new LinkedHashMap<>();
    Map<String, Double> lineCost = new LinkedHashMap<>();
    Map<String, Long> lineImpressions = new LinkedHashMap<>();
    Set<String> pricedLines = new HashSet<>();
    boolean hasDsp = campaign.getDsp() != null && !campaign.getDsp().isBlank();

    for (CampaignInventorySchedules cis : schedules) {
      Inventory inventory;
      try {
        inventory = inventoryService.getById(cis.getInventoryId());
      } catch (Exception e) {
        log.warn(
            "Skipping unknown inventory {} for campaign {}",
            cis.getInventoryId(),
            campaign.getId());
        continue;
      }
      ExecutionPlan.Classification classification =
          "Digital".equalsIgnoreCase(inventory.getClassification())
              ? ExecutionPlan.Classification.DIGITAL
              : ExecutionPlan.Classification.CLASSIC;
      String key = inventory.getMediaOwnerId() + "|" + classification;
      ExecutionPlan.Line line =
          lineMap.computeIfAbsent(
              key,
              k ->
                  ExecutionPlan.Line.builder()
                      .id(UUID.randomUUID().toString())
                      .mediaOwnerId(inventory.getMediaOwnerId())
                      .mediaOwnerName(inventory.getMediaOwnerName())
                      .classification(classification)
                      .destination(
                          classification == ExecutionPlan.Classification.DIGITAL
                              ? ExecutionPlan.Destination.INFLUENCE
                              : ExecutionPlan.Destination.OMS)
                      .purchaseType(
                          classification == ExecutionPlan.Classification.DIGITAL
                              ? (hasDsp
                                  ? ExecutionPlan.PurchaseType.GUARANTEED
                                  : ExecutionPlan.PurchaseType.DIRECT)
                              : ExecutionPlan.PurchaseType.ORDER)
                      .inventoryIds(new ArrayList<>())
                      .handoffStatus(ExecutionPlan.HandoffStatus.PENDING_HANDOFF)
                      .attemptCount(0)
                      .build());
      line.getInventoryIds().add(cis.getInventoryId());

      // Cost/impressions from the persisted schedule docs when available.
      if (cis.getScheduleIds() != null && !cis.getScheduleIds().isEmpty()) {
        for (Schedule schedule : scheduleRepository.findAllById(cis.getScheduleIds())) {
          if (schedule.getBasePrice() != null) {
            lineCost.merge(key, schedule.getBasePrice(), Double::sum);
            pricedLines.add(key);
          }
          if (schedule.getImpressions() != null) {
            lineImpressions.merge(key, schedule.getImpressions(), Long::sum);
          }
        }
      }
    }

    // Fallback: spread the campaign budget evenly across inventories for lines without any
    // schedule-derived price, so planned cost is never silently zero.
    long totalInventories =
        lineMap.values().stream().mapToLong(l -> l.getInventoryIds().size()).sum();
    Double budget = campaign.getBudget();
    for (Map.Entry<String, ExecutionPlan.Line> entry : lineMap.entrySet()) {
      ExecutionPlan.Line line = entry.getValue();
      Double cost = lineCost.get(entry.getKey());
      if (!pricedLines.contains(entry.getKey())
          && budget != null
          && budget > 0
          && totalInventories > 0) {
        cost = budget * line.getInventoryIds().size() / totalInventories;
      }
      line.setPlannedCost(cost);
      line.setPlannedImpressions(lineImpressions.get(entry.getKey()));
    }

    return ExecutionPlan.builder()
        .campaignId(Objects.requireNonNull(campaign.getId()))
        .lines(new ArrayList<>(lineMap.values()))
        .locked(false)
        .build();
  }

  private ExecutionPlanResponseDTO toDto(Campaign campaign, ExecutionPlan plan) {
    List<ExecutionPlanResponseDTO.LineDTO> lineDtos = new ArrayList<>();
    int inventoryCount = 0;
    double totalCost = 0;
    boolean hasCost = false;
    long totalImpressions = 0;
    boolean hasImpressions = false;
    int queuedCount = 0;
    int sentCount = 0;
    int acknowledgedCount = 0;
    int failedCount = 0;

    if (plan.getLines() != null) {
      for (ExecutionPlan.Line line : plan.getLines()) {
        List<ExecutionPlanResponseDTO.InventoryItemDTO> items = new ArrayList<>();
        if (line.getInventoryIds() != null) {
          for (String inventoryId : line.getInventoryIds()) {
            try {
              Inventory inventory = inventoryService.getById(inventoryId);
              items.add(
                  ExecutionPlanResponseDTO.InventoryItemDTO.builder()
                      .id(inventoryId)
                      .name(inventory.getName())
                      .classification(inventory.getClassification())
                      .type(inventory.getType())
                      .format(inventory.getFormat())
                      .build());
            } catch (Exception e) {
              items.add(
                  ExecutionPlanResponseDTO.InventoryItemDTO.builder().id(inventoryId).build());
            }
          }
        }
        inventoryCount += items.size();
        if (line.getPlannedCost() != null) {
          totalCost += line.getPlannedCost();
          hasCost = true;
        }
        if (line.getPlannedImpressions() != null) {
          totalImpressions += line.getPlannedImpressions();
          hasImpressions = true;
        }
        switch (normalize(line.getHandoffStatus())) {
          case QUEUED -> queuedCount++;
          case SENT -> sentCount++;
          case ACKNOWLEDGED -> acknowledgedCount++;
          case FAILED -> failedCount++;
          default -> {}
        }
        lineDtos.add(ExecutionPlanResponseDTO.mapLine(line, items));
      }
    }

    // Mirror the push guardrails so the client can disable the button with a reason instead of
    // round-tripping a rejected push.
    String blockedReason = null;
    if (!plan.isLocked()) {
      if (campaign.getStatus() != Campaign.Status.APPROVED) {
        blockedReason = BLOCK_NOT_APPROVED;
      } else if (hasUnacceptedPrices(campaign.getId())) {
        blockedReason = BLOCK_UNACCEPTED_PRICES;
      } else if (lineDtos.isEmpty()) {
        blockedReason = BLOCK_NO_LINES;
      }
    }

    return ExecutionPlanResponseDTO.builder()
        .campaignId(campaign.getId())
        .campaignName(campaign.getName())
        .campaignStatus(campaign.getStatus() != null ? campaign.getStatus().name() : null)
        .budget(campaign.getBudget())
        .currency(campaign.getCurrency())
        .locked(plan.isLocked())
        .pushedAt(plan.getPushedAt())
        .canPush(!plan.isLocked() && blockedReason == null)
        .pushBlockedReason(blockedReason)
        .summary(
            ExecutionPlanResponseDTO.Summary.builder()
                .lineCount(lineDtos.size())
                .inventoryCount(inventoryCount)
                .totalPlannedCost(hasCost ? totalCost : null)
                .totalPlannedImpressions(hasImpressions ? totalImpressions : null)
                .queuedCount(queuedCount)
                .sentCount(sentCount)
                .acknowledgedCount(acknowledgedCount)
                .failedCount(failedCount)
                .build())
        .lines(lineDtos)
        .build();
  }

  // ------------------------------------------------------------------
  // Media-owner Execution Workspace
  // ------------------------------------------------------------------

  /**
   * Media-owner scoped workspace: only the viewer's inventories, schedules, costs and lines.
   * Enabled once the viewer has approved the plan; Influence access comes from the company record
   * and gates the digital handoff (no access = handle execution offline).
   */
  public com.mw.planner.dto.ExecutionWorkspaceResponseDTO getWorkspace(String campaignId) {
    ProposalOwnerContext poc = loadForProposalOwner(campaignId);
    Campaign campaign = poc.campaign();
    String viewer = poc.viewer();
    com.mw.planner.domain.CampaignProposalStatus proposal = poc.proposal();
    boolean approvedByViewer =
        proposal.getStatus() == com.mw.planner.domain.CampaignProposalStatus.Status.APPROVED;
    boolean hasInfluenceAccess = companyHasInfluenceAccess(viewer);

    var builder =
        com.mw.planner.dto.ExecutionWorkspaceResponseDTO.builder()
            .campaignId(campaignId)
            .campaignName(campaign.getName())
            .planNumber(campaign.getPlanNumber())
            .campaignStatus(campaign.getStatus() != null ? campaign.getStatus().name() : null)
            .agencyName(resolveCompanyNameSafe(campaign.getCompanyId()))
            .goalType(campaign.getGoals() != null ? campaign.getGoals().getTypeName() : null)
            .goalTarget(campaign.getGoals() != null ? campaign.getGoals().getTargetValue() : null)
            .startDate(campaign.getStartDate())
            .endDate(campaign.getEndDate())
            .currency(campaign.getCurrency())
            .approvedByViewer(approvedByViewer)
            .viewerProposalStatus(proposal.getStatus() != null ? proposal.getStatus().name() : null)
            .hasInfluenceAccess(hasInfluenceAccess);

    if (!approvedByViewer) {
      // Gate: no plan detail until the media owner has approved their slice.
      return builder.canPush(false).pushBlockedReason(BLOCK_NOT_APPROVED).build();
    }

    ExecutionPlan plan = advanceHandoffs(getOrCreatePlan(campaign));

    // --- viewer's schedules + availability timeline ---
    List<CampaignInventorySchedules> ownCis =
        campaignInventorySchedulesRepository.findByCampaignIdAndMediaOwnerId(campaignId, viewer);
    java.time.LocalDate flightStart = campaign.getStartDate();
    java.time.LocalDate flightEnd = campaign.getEndDate();

    double approvedCost = 0;
    long plannedImpressions = 0;
    long plannedAdPlays = 0;
    long potentialTotal = 0;
    Map<String, Long> inventoryPotential = new LinkedHashMap<>();
    List<com.mw.planner.dto.ExecutionWorkspaceResponseDTO.InventoryDetail> inventoryDetails =
        new ArrayList<>();

    for (CampaignInventorySchedules cis : ownCis) {
      Inventory inventory;
      try {
        inventory = inventoryService.getById(cis.getInventoryId());
      } catch (Exception e) {
        continue;
      }
      boolean digital = "Digital".equalsIgnoreCase(inventory.getClassification());
      int capacity = digital ? loopCapacity(inventory) : 1;

      double invCost = 0;
      long invImpr = 0;
      long invPlays = 0;
      int ownSpots = 0;
      java.time.LocalDate schedStart = null;
      java.time.LocalDate schedEnd = null;
      if (cis.getScheduleIds() != null) {
        for (Schedule s : scheduleRepository.findAllById(cis.getScheduleIds())) {
          if (s.getBasePrice() != null) invCost += s.getBasePrice();
          if (s.getImpressions() != null) invImpr += s.getImpressions();
          if (s.getAdPlays() != null) invPlays += s.getAdPlays();
          if (s.getSpotsPerLoop() != null) ownSpots += s.getSpotsPerLoop().intValue();
          if (schedStart == null || s.getStartDate().isBefore(schedStart))
            schedStart = s.getStartDate();
          if (schedEnd == null || s.getEndDate().isAfter(schedEnd)) schedEnd = s.getEndDate();
        }
      }
      if (ownSpots == 0) ownSpots = 1;

      // Other campaigns' bookings on the same screen (real schedule docs, any buyer).
      List<CampaignInventorySchedules> otherCis =
          campaignInventorySchedulesRepository.findByInventoryId(cis.getInventoryId()).stream()
              .filter(o -> !campaignId.equals(o.getCampaignId()))
              .toList();
      List<Schedule> otherSchedules = new ArrayList<>();
      for (CampaignInventorySchedules o : otherCis) {
        if (o.getScheduleIds() != null) {
          scheduleRepository.findAllById(o.getScheduleIds()).forEach(otherSchedules::add);
        }
      }

      java.time.LocalDate ss = schedStart != null ? schedStart : flightStart;
      java.time.LocalDate se = schedEnd != null ? schedEnd : flightEnd;
      long flightDays =
          ss != null && se != null ? java.time.temporal.ChronoUnit.DAYS.between(ss, se) + 1 : 0;
      long imprPerSpotDay =
          digital && flightDays > 0 && ownSpots > 0
              ? Math.round((double) invImpr / (flightDays * ownSpots))
              : 0;

      List<com.mw.planner.dto.ExecutionWorkspaceResponseDTO.TimelineDay> timeline =
          new ArrayList<>();
      long freeSpotDays = 0;
      if (flightStart != null && flightEnd != null) {
        for (java.time.LocalDate d = flightStart; !d.isAfter(flightEnd); d = d.plusDays(1)) {
          final java.time.LocalDate day = d;
          int own =
              (schedStart != null
                      && schedEnd != null
                      && !day.isBefore(schedStart)
                      && !day.isAfter(schedEnd))
                  ? ownSpots
                  : 0;
          int other =
              otherSchedules.stream()
                  .filter(
                      s ->
                          s.getStartDate() != null
                              && s.getEndDate() != null
                              && !day.isBefore(s.getStartDate())
                              && !day.isAfter(s.getEndDate()))
                  .mapToInt(s -> s.getSpotsPerLoop() != null ? s.getSpotsPerLoop().intValue() : 1)
                  .sum();
          int free = Math.max(0, capacity - own - Math.min(other, capacity - own));
          if (own > 0) freeSpotDays += free; // only count free capacity inside the flight
          timeline.add(
              com.mw.planner.dto.ExecutionWorkspaceResponseDTO.TimelineDay.builder()
                  .date(day)
                  .capacity(capacity)
                  .bookedOwn(own)
                  .bookedOther(Math.min(other, capacity - own))
                  .free(free)
                  .build());
        }
      }
      long invPotential = digital ? invImpr + freeSpotDays * imprPerSpotDay : invImpr;

      approvedCost += invCost;
      plannedImpressions += invImpr;
      plannedAdPlays += invPlays;
      potentialTotal += invPotential;
      inventoryPotential.put(cis.getInventoryId(), invPotential);

      inventoryDetails.add(
          com.mw.planner.dto.ExecutionWorkspaceResponseDTO.InventoryDetail.builder()
              .id(cis.getInventoryId())
              .name(inventory.getName())
              .classification(inventory.getClassification())
              .type(inventory.getType())
              .format(inventory.getFormat())
              .spotsPerLoop(digital ? capacity : null)
              .approvedCost(invCost)
              .plannedImpressions(invImpr)
              .plannedAdPlays(invPlays > 0 ? invPlays : null)
              .scheduleStart(schedStart)
              .scheduleEnd(schedEnd)
              .impressionsPerSpotPerDay(imprPerSpotDay > 0 ? imprPerSpotDay : null)
              .potentialImpressions(invPotential)
              .timeline(timeline)
              .build());
    }

    // --- viewer's lines only ---
    long committed = 0;
    List<com.mw.planner.dto.ExecutionWorkspaceResponseDTO.LineItem> lineItems = new ArrayList<>();
    if (plan.getLines() != null) {
      for (ExecutionPlan.Line line : plan.getLines()) {
        if (!viewer.equals(line.getMediaOwnerId())) continue;
        long capacityImpr =
            line.getInventoryIds() == null
                ? 0
                : line.getInventoryIds().stream()
                    .mapToLong(id -> inventoryPotential.getOrDefault(id, 0L))
                    .sum();
        if (line.getTargetImpressions() != null) committed += line.getTargetImpressions();
        lineItems.add(
            com.mw.planner.dto.ExecutionWorkspaceResponseDTO.LineItem.builder()
                .id(line.getId())
                .classification(
                    line.getClassification() != null ? line.getClassification().name() : null)
                .destination(line.getDestination() != null ? line.getDestination().name() : null)
                .purchaseType(line.getPurchaseType() != null ? line.getPurchaseType().name() : null)
                .inventoryIds(line.getInventoryIds())
                .plannedCost(line.getPlannedCost())
                .plannedImpressions(line.getPlannedImpressions())
                .targetImpressions(line.getTargetImpressions())
                .floorRate(line.getFloorRate())
                .handoffStatus(
                    line.getHandoffStatus() != null
                        ? normalize(line.getHandoffStatus()).name()
                        : null)
                .handoffError(line.getHandoffError())
                .handedOffAt(line.getHandedOffAt())
                .capacityImpressions(capacityImpr)
                .build());
      }
    }

    boolean hasOwnPending =
        lineItems.stream().anyMatch(l -> "PENDING_HANDOFF".equals(l.getHandoffStatus()));
    // "locked" is viewer-scoped: this owner is done once all THEIR lines are dispatched.
    // Other owners' lines never lock this owner's workspace.
    boolean viewerLocked = !lineItems.isEmpty() && !hasOwnPending;

    String blockedReason = null;
    if (!hasInfluenceAccess) {
      blockedReason = "NO_INFLUENCE_ACCESS";
    } else if (viewerLocked) {
      blockedReason = null; // locked handled by locked flag
    } else if (campaign.getStatus() != Campaign.Status.APPROVED
        && campaign.getStatus() != Campaign.Status.ACTIVE) {
      blockedReason = BLOCK_NOT_APPROVED;
    } else if (hasUnacceptedPrices(campaignId)) {
      blockedReason = BLOCK_UNACCEPTED_PRICES;
    } else if (lineItems.isEmpty()) {
      blockedReason = BLOCK_NO_LINES;
    }

    return builder
        .locked(viewerLocked)
        .pushedAt(plan.getPushedAt())
        .canPush(!viewerLocked && blockedReason == null)
        .pushBlockedReason(blockedReason)
        .summary(
            com.mw.planner.dto.ExecutionWorkspaceResponseDTO.Summary.builder()
                .approvedCost(approvedCost)
                .plannedImpressions(plannedImpressions)
                .potentialImpressions(potentialTotal)
                .plannedAdPlays(plannedAdPlays > 0 ? plannedAdPlays : null)
                .inventoryCount(inventoryDetails.size())
                .lineCount(lineItems.size())
                .committedImpressions(committed > 0 ? committed : null)
                .build())
        .inventories(inventoryDetails)
        .lines(lineItems)
        .build();
  }

  /** Update floor rate / target impressions / purchase type on one of the viewer's lines. */
  public com.mw.planner.dto.ExecutionWorkspaceResponseDTO updateLine(
      String campaignId, String lineId, com.mw.planner.dto.ExecutionLineRequestDTO request) {
    LineContext ctx = loadOwnLineForEdit(campaignId, lineId);
    ExecutionPlan.Line line = ctx.line;

    if (request.getFloorRate() != null) {
      if (request.getFloorRate() < 0) {
        throw new CampaignInvalidStatusException(ctx.campaign.getStatus(), "floorRate >= 0");
      }
      line.setFloorRate(request.getFloorRate());
    }
    if (request.getPurchaseType() != null) {
      ExecutionPlan.PurchaseType newType =
          ExecutionPlan.PurchaseType.valueOf(request.getPurchaseType());
      validatePurchaseType(ctx.campaign, line.getClassification(), newType);
      line.setPurchaseType(newType);
    }
    if (request.getTargetImpressions() != null) {
      if (request.getTargetImpressions() < 0) {
        throw new CampaignInvalidStatusException(
            ctx.campaign.getStatus(), "targetImpressions >= 0");
      }
      line.setTargetImpressions(request.getTargetImpressions());
    }
    // A guaranteed commitment must fit within what the line's screens can actually deliver.
    // Validate the FINAL state so switching an over-committed line to GUARANTEED (or raising
    // the target) can never sneak past the capacity check.
    if (line.getPurchaseType() == ExecutionPlan.PurchaseType.GUARANTEED
        && line.getTargetImpressions() != null) {
      long capacity = lineCapacity(campaignId, ctx.viewer, line);
      if (line.getTargetImpressions() > capacity) {
        throw new CampaignInvalidStatusException(
            ctx.campaign.getStatus(),
            "guaranteed target within capacity (" + capacity + " impressions)");
      }
    }
    executionPlanRepository.save(ctx.plan);
    return getWorkspace(campaignId);
  }

  /** Create a new empty line for the viewing media owner. */
  public com.mw.planner.dto.ExecutionWorkspaceResponseDTO createLine(
      String campaignId, com.mw.planner.dto.ExecutionLineRequestDTO request) {
    ProposalOwnerContext poc = requireApprovedProposalOwner(campaignId);
    Campaign campaign = poc.campaign();
    String viewer = poc.viewer();
    ExecutionPlan plan = getOrCreatePlan(campaign);
    ExecutionPlan.Classification classification =
        ExecutionPlan.Classification.valueOf(
            request.getClassification() != null ? request.getClassification() : "DIGITAL");
    ExecutionPlan.PurchaseType purchaseType =
        request.getPurchaseType() != null
            ? ExecutionPlan.PurchaseType.valueOf(request.getPurchaseType())
            : (classification == ExecutionPlan.Classification.DIGITAL
                ? ExecutionPlan.PurchaseType.DIRECT
                : ExecutionPlan.PurchaseType.ORDER);
    validatePurchaseType(campaign, classification, purchaseType);
    ExecutionPlan.Line line =
        ExecutionPlan.Line.builder()
            .id(UUID.randomUUID().toString())
            .mediaOwnerId(viewer)
            .mediaOwnerName(resolveCompanyNameSafe(viewer))
            .classification(classification)
            .destination(
                classification == ExecutionPlan.Classification.DIGITAL
                    ? ExecutionPlan.Destination.INFLUENCE
                    : ExecutionPlan.Destination.OMS)
            .purchaseType(purchaseType)
            .inventoryIds(new ArrayList<>())
            .handoffStatus(ExecutionPlan.HandoffStatus.PENDING_HANDOFF)
            .attemptCount(0)
            .floorRate(request.getFloorRate())
            .build();
    if (plan.getLines() == null) plan.setLines(new ArrayList<>());
    plan.getLines().add(line);
    executionPlanRepository.save(plan);
    return getWorkspace(campaignId);
  }

  /** Move one inventory from one of the viewer's lines to another, revalidating classification. */
  public com.mw.planner.dto.ExecutionWorkspaceResponseDTO moveInventory(
      String campaignId, String toLineId, com.mw.planner.dto.ExecutionLineRequestDTO request) {
    LineContext ctx = loadOwnLineForEdit(campaignId, toLineId);
    ExecutionPlan.Line target = ctx.line;
    ExecutionPlan.Line source =
        ctx.plan.getLines().stream()
            .filter(l -> l.getId().equals(request.getFromLineId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new CampaignInvalidStatusException(
                        ctx.campaign.getStatus(), "a valid source line"));
    if (!ctx.viewer.equals(source.getMediaOwnerId())) {
      throw new CampaignNotFoundException(campaignId);
    }
    if (normalize(source.getHandoffStatus()) != ExecutionPlan.HandoffStatus.PENDING_HANDOFF) {
      throw new CampaignInvalidStatusException(
          ctx.campaign.getStatus(), "a source line that has not been handed off yet");
    }
    String inventoryId = request.getInventoryId();
    if (source.getInventoryIds() == null || !source.getInventoryIds().contains(inventoryId)) {
      throw new CampaignInvalidStatusException(
          ctx.campaign.getStatus(), "the inventory to be on the source line");
    }
    // Classification guard: classic can never enter an Influence line and vice versa.
    Inventory inventory = inventoryService.getById(inventoryId);
    ExecutionPlan.Classification invClass =
        "Digital".equalsIgnoreCase(inventory.getClassification())
            ? ExecutionPlan.Classification.DIGITAL
            : ExecutionPlan.Classification.CLASSIC;
    if (invClass != target.getClassification()) {
      throw new CampaignInvalidStatusException(
          ctx.campaign.getStatus(),
          invClass == ExecutionPlan.Classification.CLASSIC
              ? "classic inventory to stay on OMS/poster-ops lines"
              : "digital inventory to stay on Influence lines");
    }
    source.getInventoryIds().remove(inventoryId);
    if (target.getInventoryIds() == null) target.setInventoryIds(new ArrayList<>());
    target.getInventoryIds().add(inventoryId);
    // Re-split planned cost/impressions between the two lines from schedule data.
    reallocateLineTotals(campaignId, ctx.viewer, source, target);
    executionPlanRepository.save(ctx.plan);
    return getWorkspace(campaignId);
  }

  /** Delete one of the viewer's lines (only when it carries no inventory). */
  public com.mw.planner.dto.ExecutionWorkspaceResponseDTO deleteLine(
      String campaignId, String lineId) {
    LineContext ctx = loadOwnLineForEdit(campaignId, lineId);
    if (ctx.line.getInventoryIds() != null && !ctx.line.getInventoryIds().isEmpty()) {
      throw new CampaignInvalidStatusException(
          ctx.campaign.getStatus(), "an empty line (move its inventories first)");
    }
    ctx.plan.getLines().remove(ctx.line);
    executionPlanRepository.save(ctx.plan);
    return getWorkspace(campaignId);
  }

  // --- workspace internals ---

  private record LineContext(
      Campaign campaign, ExecutionPlan plan, ExecutionPlan.Line line, String viewer) {}

  private LineContext loadOwnLineForEdit(String campaignId, String lineId) {
    ProposalOwnerContext poc = requireApprovedProposalOwner(campaignId);
    Campaign campaign = poc.campaign();
    String viewer = poc.viewer();
    ExecutionPlan plan =
        executionPlanRepository
            .findByCampaignId(campaignId)
            .orElseThrow(() -> new CampaignNotFoundException(campaignId));
    ExecutionPlan.Line line =
        plan.getLines() == null
            ? null
            : plan.getLines().stream()
                .filter(l -> lineId.equals(l.getId()))
                .findFirst()
                .orElse(null);
    if (line == null || !viewer.equals(line.getMediaOwnerId())) {
      // Own-lines only; hide others' lines entirely.
      throw new CampaignNotFoundException(campaignId);
    }
    // Locking is per-line: once THIS line was dispatched it is immutable; this owner's other
    // pending lines and other owners' lines stay editable.
    if (normalize(line.getHandoffStatus()) != ExecutionPlan.HandoffStatus.PENDING_HANDOFF) {
      throw new CampaignInvalidStatusException(
          campaign.getStatus(), "a line that has not been handed off yet");
    }
    return new LineContext(campaign, plan, line, viewer);
  }

  private void validatePurchaseType(
      Campaign campaign,
      ExecutionPlan.Classification classification,
      ExecutionPlan.PurchaseType type) {
    boolean valid =
        classification == ExecutionPlan.Classification.DIGITAL
            ? (type == ExecutionPlan.PurchaseType.GUARANTEED
                || type == ExecutionPlan.PurchaseType.DIRECT)
            : type == ExecutionPlan.PurchaseType.ORDER;
    if (!valid) {
      throw new CampaignInvalidStatusException(
          campaign.getStatus(),
          classification == ExecutionPlan.Classification.DIGITAL
              ? "GUARANTEED or DIRECT (preferred deal) for digital lines"
              : "ORDER for classic (poster-ops) lines");
    }
  }

  /** Loop capacity of a digital screen (spots per loop, defaulting to a 6-spot loop). */
  private static int loopCapacity(Inventory inventory) {
    Integer spl =
        inventory.getDigitalFields() != null
            ? inventory.getDigitalFields().getSpotsPerLoop()
            : null;
    return spl != null && spl > 0 ? spl : 6;
  }

  /** Availability-aware ceiling for a line: sum of its inventories' potential impressions. */
  private long lineCapacity(String campaignId, String viewer, ExecutionPlan.Line line) {
    Map<String, Long> potentials = computeInventoryPotentials(campaignId, viewer);
    return line.getInventoryIds() == null
        ? 0
        : line.getInventoryIds().stream().mapToLong(id -> potentials.getOrDefault(id, 0L)).sum();
  }

  /** Per-inventory potential impressions (same availability math as the workspace timeline). */
  private Map<String, Long> computeInventoryPotentials(String campaignId, String viewer) {
    Map<String, Long> potentials = new LinkedHashMap<>();
    for (CampaignInventorySchedules cis :
        campaignInventorySchedulesRepository.findByCampaignIdAndMediaOwnerId(campaignId, viewer)) {
      Inventory inventory;
      try {
        inventory = inventoryService.getById(cis.getInventoryId());
      } catch (Exception e) {
        continue;
      }
      boolean digital = "Digital".equalsIgnoreCase(inventory.getClassification());
      int capacity = digital ? loopCapacity(inventory) : 1;
      long invImpr = 0;
      int ownSpots = 0;
      java.time.LocalDate schedStart = null;
      java.time.LocalDate schedEnd = null;
      if (cis.getScheduleIds() != null) {
        for (Schedule s : scheduleRepository.findAllById(cis.getScheduleIds())) {
          if (s.getImpressions() != null) invImpr += s.getImpressions();
          if (s.getSpotsPerLoop() != null) ownSpots += s.getSpotsPerLoop().intValue();
          if (schedStart == null || s.getStartDate().isBefore(schedStart))
            schedStart = s.getStartDate();
          if (schedEnd == null || s.getEndDate().isAfter(schedEnd)) schedEnd = s.getEndDate();
        }
      }
      if (ownSpots == 0) ownSpots = 1;
      if (!digital || schedStart == null) {
        potentials.put(cis.getInventoryId(), invImpr);
        continue;
      }
      long days = java.time.temporal.ChronoUnit.DAYS.between(schedStart, schedEnd) + 1;
      long perSpotDay = days > 0 ? Math.round((double) invImpr / (days * ownSpots)) : 0;
      List<Schedule> otherSchedules = new ArrayList<>();
      for (CampaignInventorySchedules o :
          campaignInventorySchedulesRepository.findByInventoryId(cis.getInventoryId())) {
        if (campaignId.equals(o.getCampaignId()) || o.getScheduleIds() == null) continue;
        scheduleRepository.findAllById(o.getScheduleIds()).forEach(otherSchedules::add);
      }
      long freeSpotDays = 0;
      for (java.time.LocalDate d = schedStart; !d.isAfter(schedEnd); d = d.plusDays(1)) {
        final java.time.LocalDate day = d;
        int other =
            otherSchedules.stream()
                .filter(
                    s ->
                        s.getStartDate() != null
                            && s.getEndDate() != null
                            && !day.isBefore(s.getStartDate())
                            && !day.isAfter(s.getEndDate()))
                .mapToInt(s -> s.getSpotsPerLoop() != null ? s.getSpotsPerLoop().intValue() : 1)
                .sum();
        freeSpotDays += Math.max(0, capacity - ownSpots - Math.min(other, capacity - ownSpots));
      }
      potentials.put(cis.getInventoryId(), invImpr + freeSpotDays * perSpotDay);
    }
    return potentials;
  }

  /** After a move, re-derive planned cost/impressions of both lines from their schedule docs. */
  private void reallocateLineTotals(
      String campaignId, String viewer, ExecutionPlan.Line a, ExecutionPlan.Line b) {
    Map<String, double[]> perInventory = new LinkedHashMap<>(); // [cost, impressions]
    for (CampaignInventorySchedules cis :
        campaignInventorySchedulesRepository.findByCampaignIdAndMediaOwnerId(campaignId, viewer)) {
      double cost = 0;
      long impr = 0;
      if (cis.getScheduleIds() != null) {
        for (Schedule s : scheduleRepository.findAllById(cis.getScheduleIds())) {
          if (s.getBasePrice() != null) cost += s.getBasePrice();
          if (s.getImpressions() != null) impr += s.getImpressions();
        }
      }
      perInventory.put(cis.getInventoryId(), new double[] {cost, impr});
    }
    for (ExecutionPlan.Line line : List.of(a, b)) {
      double cost = 0;
      long impr = 0;
      if (line.getInventoryIds() != null) {
        for (String id : line.getInventoryIds()) {
          double[] v = perInventory.get(id);
          if (v != null) {
            cost += v[0];
            impr += (long) v[1];
          }
        }
      }
      line.setPlannedCost(cost);
      line.setPlannedImpressions(impr);
    }
  }

  private boolean companyHasInfluenceAccess(String companyId) {
    try {
      var company = companyService.getCompanyLookupWithCompanyId(companyId);
      return company != null && Boolean.TRUE.equals(company.getInfluenceAccess());
    } catch (Exception e) {
      log.warn("Could not resolve Influence access for {}: {}", companyId, e.getMessage());
      return false;
    }
  }

  private String resolveCompanyNameSafe(String companyId) {
    try {
      var company = companyService.getCompanyLookupWithCompanyId(companyId);
      return company != null && company.getName() != null ? company.getName() : companyId;
    } catch (Exception e) {
      return companyId;
    }
  }
}
