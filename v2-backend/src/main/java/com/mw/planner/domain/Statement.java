package com.mw.planner.domain;

import com.mw.planner.enums.CustomFeeType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Bundles one or more campaigns into a single invoice (PRD §12). Reuses {@link
 * com.mw.planner.service.CampaignInventorySchedulesService#getCampaignPriceSummary} per campaign
 * for cost/fee figures rather than re-deriving pricing logic — this is the same
 * discountedMediaCost/standardFees cascade Price Management already computes and tests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "statements")
public class Statement extends BaseEntity<String> {

  @Indexed private String statementNumber;
  @Indexed private String companyId; // the billing (creator) company

  @Builder.Default private Status status = Status.DRAFT;

  @Builder.Default private List<StatementLine> lines = new ArrayList<>();

  /**
   * % of Net Cost — PRD §12/§8.3 describes this as set at company onboarding, but no such
   * company-level rate exists anywhere in v2-backend yet (confirmed: no platformFee field/config on
   * any Company-related type). Defaults to 0 until that onboarding data exists; callers may
   * override it explicitly at finalize time in the interim.
   */
  @Builder.Default private double platformFeePercentage = 0.0;

  private SplitConfig splitConfig;

  private String parentStatementId;
  private String splitIdentifier;

  @Builder.Default private Map<String, SyncStatusEntry> syncStatus = new HashMap<>();

  @Builder.Default private boolean locked = false;

  private Double totalMediaCost;
  private Double totalFees;
  private Double totalPlatformFee;
  private Double totalAmount;

  private LocalDateTime finalizedAt;

  public enum Status {
    DRAFT,
    FINALIZED,
    SENT,
    PAID,
    PARTIALLY_PAID,
    OVERDUE,
    CANCELLED
  }

  public enum SplitMethod {
    EQUAL,
    MONTHLY,
    WEEKLY,
    CAMPAIGN_BASED,
    CUSTOM
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class StatementLine {
    private String campaignId;
    private Double mediaCost; // discountedMediaCost from the price summary
    private Double visibleFeesTotal; // standardFees from the price summary
    @Builder.Default private List<FeeSnapshot> feeSnapshot = new ArrayList<>();
  }

  /** A frozen copy of a CustomFee at finalize time — never a live reference (PRD §12). */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FeeSnapshot {
    private String customFeeId;
    private String name;
    private CustomFeeType type;
    private Double value;
    private Boolean isIncludeInMediaPlan;
    private Double calculatedAmount;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SplitConfig {
    private SplitMethod method;
    @Builder.Default private List<Split> splits = new ArrayList<>();
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Split {
    private String label;
    private Double amount;
  }

  /**
   * Finance-integration sync — data model + status machine only per the Statements V1-vs-V2
   * research note (neither stack has a working outbound API client; V1's equivalent field was
   * schema-only and never populated). Real NetSuite/Zoho/QuickBooks integration is separate
   * follow-up work.
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SyncStatusEntry {
    private String externalId;
    private String status; // e.g. PENDING, SYNCED, FAILED
    private LocalDateTime syncedAt;
    private String lastError;
  }
}
