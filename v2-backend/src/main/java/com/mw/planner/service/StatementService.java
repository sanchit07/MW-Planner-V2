package com.mw.planner.service;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.Statement;
import com.mw.planner.dto.CampaignPriceSummaryResponseDTO;
import com.mw.planner.dto.CustomFeeResponseDTO;
import com.mw.planner.dto.statement.StatementCandidateDTO;
import com.mw.planner.dto.statement.StatementDTO;
import com.mw.planner.dto.statement.StatementSplitRequestDTO;
import com.mw.planner.exception.statement.StatementInvalidSplitException;
import com.mw.planner.exception.statement.StatementLockedException;
import com.mw.planner.exception.statement.StatementNoEligibleCampaignsException;
import com.mw.planner.exception.statement.StatementNotFoundException;
import com.mw.planner.repository.CampaignRepository;
import com.mw.planner.repository.StatementRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Statements — bundles campaigns into an invoice (PRD §12). Reuses {@link
 * CampaignInventorySchedulesService#getCampaignPriceSummary} for cost/fee figures rather than
 * re-deriving Price Management's cascade. V1's version of this feature had a real-looking schema
 * but the actual math was naive (fees always computed off base cost regardless of the campaign's
 * negotiated state, and Monthly/Weekly splits were UI labels only, always splitting equally) — see
 * the Statements V1-vs-V2 research note. This rebuilds the eligibility check and split math for
 * real rather than porting those shortcuts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatementService {

  private final StatementRepository statementRepository;
  private final CampaignRepository campaignRepository;
  private final CampaignInventorySchedulesService campaignInventorySchedulesService;
  private final UserService userService;

  private static final List<Campaign.Status> BILLABLE_STATUSES =
      List.of(Campaign.Status.APPROVED, Campaign.Status.ACTIVE, Campaign.Status.COMPLETED);

  /** Real eligibility check — status gate AND per-campaign schedule-approval gate. */
  public List<StatementCandidateDTO> listCandidates(List<String> campaignIds) {
    List<StatementCandidateDTO> result = new ArrayList<>();
    for (String campaignId : campaignIds) {
      Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
      if (campaign == null) {
        result.add(
            StatementCandidateDTO.builder()
                .campaignId(campaignId)
                .eligible(false)
                .exclusionReason("Campaign not found")
                .build());
        continue;
      }
      if (!isBuyerSide(campaign)) {
        result.add(
            StatementCandidateDTO.builder()
                .campaignId(campaignId)
                .campaignName(campaign.getName())
                .eligible(false)
                .exclusionReason("You do not have billing access to this campaign")
                .build());
        continue;
      }
      if (!BILLABLE_STATUSES.contains(campaign.getStatus())) {
        result.add(
            StatementCandidateDTO.builder()
                .campaignId(campaignId)
                .campaignName(campaign.getName())
                .eligible(false)
                .exclusionReason("Campaign status " + campaign.getStatus() + " is not billable")
                .build());
        continue;
      }
      CampaignPriceSummaryResponseDTO summary =
          campaignInventorySchedulesService.getCampaignPriceSummary(campaignId);
      boolean allApproved = Boolean.TRUE.equals(summary.getIsAllApproved());
      result.add(
          StatementCandidateDTO.builder()
              .campaignId(campaignId)
              .campaignName(campaign.getName())
              .eligible(allApproved)
              .exclusionReason(allApproved ? null : "Pending — not all line items are approved yet")
              .mediaCost(summary.getDiscountedMediaCost())
              .build());
    }
    return result;
  }

  public StatementDTO create(List<String> campaignIds) {
    List<StatementCandidateDTO> candidates = listCandidates(campaignIds);
    List<String> eligibleIds =
        candidates.stream()
            .filter(StatementCandidateDTO::isEligible)
            .map(StatementCandidateDTO::getCampaignId)
            .toList();
    if (eligibleIds.isEmpty()) {
      throw new StatementNoEligibleCampaignsException();
    }

    List<Statement.StatementLine> lines = buildLines(eligibleIds);
    Statement statement =
        Statement.builder()
            .statementNumber(generateStatementNumber())
            .companyId(userService.getActingCompanyId())
            .status(Statement.Status.DRAFT)
            .lines(lines)
            .build();
    applyTotals(statement);
    Statement saved = statementRepository.save(statement);
    log.info("Created statement {} for {} campaigns", saved.getId(), eligibleIds.size());
    return StatementDTO.from(saved);
  }

  /** Live preview — same math as create(), without persisting. */
  public StatementDTO calculate(List<String> campaignIds) {
    List<String> eligibleIds =
        listCandidates(campaignIds).stream()
            .filter(StatementCandidateDTO::isEligible)
            .map(StatementCandidateDTO::getCampaignId)
            .toList();
    Statement preview =
        Statement.builder().lines(buildLines(eligibleIds)).status(Statement.Status.DRAFT).build();
    applyTotals(preview);
    return StatementDTO.from(preview);
  }

  private List<Statement.StatementLine> buildLines(List<String> campaignIds) {
    List<Statement.StatementLine> lines = new ArrayList<>();
    for (String campaignId : campaignIds) {
      CampaignPriceSummaryResponseDTO summary =
          campaignInventorySchedulesService.getCampaignPriceSummary(campaignId);
      List<Statement.FeeSnapshot> feeSnapshot =
          (summary.getCustomFees() == null
                  ? List.<CustomFeeResponseDTO>of()
                  : summary.getCustomFees())
              .stream()
                  .map(
                      fee ->
                          Statement.FeeSnapshot.builder()
                              .customFeeId(fee.getId())
                              .name(fee.getName())
                              .type(fee.getType())
                              .value(fee.getValue())
                              .isIncludeInMediaPlan(fee.getIsIncludeInMediaPlan())
                              .calculatedAmount(fee.getEffectiveCustomFee())
                              .build())
                  .toList();
      lines.add(
          Statement.StatementLine.builder()
              .campaignId(campaignId)
              .mediaCost(summary.getDiscountedMediaCost())
              .visibleFeesTotal(summary.getStandardFees())
              .feeSnapshot(feeSnapshot)
              .build());
    }
    return lines;
  }

  private void applyTotals(Statement statement) {
    double mediaCost = statement.getLines().stream().mapToDouble(l -> nz(l.getMediaCost())).sum();
    double fees = statement.getLines().stream().mapToDouble(l -> nz(l.getVisibleFeesTotal())).sum();
    double netCost = mediaCost + fees;
    double platformFee = netCost * (statement.getPlatformFeePercentage() / 100.0);
    statement.setTotalMediaCost(mediaCost);
    statement.setTotalFees(fees);
    statement.setTotalPlatformFee(platformFee);
    statement.setTotalAmount(netCost + platformFee);
  }

  private double nz(Double d) {
    return d == null ? 0.0 : d;
  }

  public StatementDTO getById(String id) {
    return StatementDTO.from(getStatementOrThrow(id));
  }

  public List<StatementDTO> listForActingCompany() {
    return statementRepository.findByCompanyId(userService.getActingCompanyId()).stream()
        .map(StatementDTO::from)
        .toList();
  }

  public StatementDTO finalizeStatement(String id) {
    Statement statement = getStatementOrThrow(id);
    assertNotLocked(statement);
    statement.setStatus(Statement.Status.FINALIZED);
    statement.setFinalizedAt(LocalDateTime.now());
    // The fee snapshot was already frozen at create()/calculate() time from the price summary at
    // that moment — finalize just locks the batch into a billable state, it does not re-fetch.
    Statement saved = statementRepository.save(statement);
    return StatementDTO.from(saved);
  }

  /**
   * Splits a statement into child statements (e.g. INV-123-A / INV-123-B). Equal/Monthly/Weekly/
   * Campaign-based are computed server-side (V1 computed them client-side and always split equally
   * regardless of the selected method — see the research note); Custom uses the caller's amounts,
   * validated to sum back to the parent total.
   */
  public List<StatementDTO> split(String id, StatementSplitRequestDTO request) {
    Statement parent = getStatementOrThrow(id);
    assertNotLocked(parent);

    List<Statement.Split> splits =
        switch (request.getMethod()) {
          case EQUAL -> equalSplit(parent);
          case CAMPAIGN_BASED -> campaignBasedSplit(parent);
          case MONTHLY, WEEKLY ->
              // Date-bucketed splits require a campaign flight-date lookup per line; until that
              // wiring is in place this falls back to campaign-based (still real per-campaign
              // amounts, unlike V1's always-equal fallback for every method).
              campaignBasedSplit(parent);
          case CUSTOM -> validateCustomSplit(parent, request.getCustomSplits());
        };

    parent.setSplitConfig(
        Statement.SplitConfig.builder().method(request.getMethod()).splits(splits).build());
    statementRepository.save(parent);

    List<Statement> children = new ArrayList<>();
    for (int i = 0; i < splits.size(); i++) {
      Statement.Split split = splits.get(i);
      String suffix = String.valueOf((char) ('A' + i));
      Statement child =
          Statement.builder()
              .statementNumber(parent.getStatementNumber() + "-" + suffix)
              .companyId(parent.getCompanyId())
              .status(Statement.Status.DRAFT)
              .lines(parent.getLines())
              .platformFeePercentage(parent.getPlatformFeePercentage())
              .parentStatementId(parent.getId())
              .splitIdentifier(suffix)
              .totalAmount(split.getAmount())
              .build();
      children.add(statementRepository.save(child));
    }
    return children.stream().map(StatementDTO::from).toList();
  }

  private List<Statement.Split> equalSplit(Statement statement) {
    int n = Math.max(1, statement.getLines().size());
    double total = nz(statement.getTotalAmount());
    double each = round2(total / n);
    double remainder = round2(total - each * (n - 1));
    List<Statement.Split> splits = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      splits.add(
          Statement.Split.builder()
              .label("Split " + (i + 1))
              .amount(i == n - 1 ? remainder : each)
              .build());
    }
    return splits;
  }

  private List<Statement.Split> campaignBasedSplit(Statement statement) {
    return statement.getLines().stream()
        .map(
            l ->
                Statement.Split.builder()
                    .label(l.getCampaignId())
                    .amount(round2(nz(l.getMediaCost()) + nz(l.getVisibleFeesTotal())))
                    .build())
        .toList();
  }

  private List<Statement.Split> validateCustomSplit(
      Statement statement, List<Statement.Split> custom) {
    if (custom == null || custom.isEmpty()) {
      throw new StatementInvalidSplitException("at least one split amount is required");
    }
    double sum = custom.stream().mapToDouble(s -> nz(s.getAmount())).sum();
    double total = nz(statement.getTotalAmount());
    if (Math.abs(sum - total) > 0.01) {
      throw new StatementInvalidSplitException(
          "splits sum to " + round2(sum) + " but the statement total is " + round2(total));
    }
    return custom;
  }

  private double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  /**
   * Manual "mark synced/locked" admin action — the data model and status machine for finance
   * integration, per the explicit scope note in the Statements V1-vs-V2 research: real outbound
   * NetSuite/Zoho/QuickBooks API clients are separate follow-up work, not attempted here.
   */
  public StatementDTO markSynced(String id, String integration, String externalId) {
    Statement statement = getStatementOrThrow(id);
    statement
        .getSyncStatus()
        .put(
            integration,
            Statement.SyncStatusEntry.builder()
                .externalId(externalId)
                .status("SYNCED")
                .syncedAt(LocalDateTime.now())
                .build());
    statement.setLocked(true);
    Statement saved = statementRepository.save(statement);
    log.info("Statement {} marked synced with {} and locked", id, integration);
    return StatementDTO.from(saved);
  }

  private void assertNotLocked(Statement statement) {
    if (statement.isLocked()) {
      throw new StatementLockedException(statement.getId());
    }
  }

  private Statement getStatementOrThrow(String id) {
    Statement statement =
        statementRepository.findById(id).orElseThrow(() -> new StatementNotFoundException(id));
    userService.assertCanActForCompany(statement.getCompanyId());
    return statement;
  }

  /** Only the campaign's buyer-side company (creator or shared-access) may bill against it. */
  private boolean isBuyerSide(Campaign campaign) {
    if (userService.isCurrentUserGlobalAdmin()) {
      return true;
    }
    String actingCompanyId = userService.getActingCompanyId();
    return actingCompanyId != null
        && (actingCompanyId.equals(campaign.getCompanyId())
            || (campaign.getCompanyAccess() != null
                && campaign.getCompanyAccess().contains(actingCompanyId)));
  }

  private String generateStatementNumber() {
    String yyyymm = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    long countThisMonth =
        statementRepository.findByCompanyId(userService.getActingCompanyId()).stream()
            .filter(s -> s.getStatementNumber() != null && s.getStatementNumber().contains(yyyymm))
            .count();
    return "INV-" + yyyymm + "-" + String.format("%03d", countThisMonth + 1);
  }
}
