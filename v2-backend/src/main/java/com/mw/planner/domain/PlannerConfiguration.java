package com.mw.planner.domain;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Tenant-scoped platform configuration — the porting target for V1's `workflow-config-page.tsx`
 * (which was UI-only mock state, never persisted). One document per company; unset sections fall
 * back to {@link com.mw.planner.service.config.DefaultConfigurationService} defaults at read time
 * rather than being written eagerly, so partially-configured tenants still get sane values
 * everywhere.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "planner_configurations")
public class PlannerConfiguration extends BaseEntity<String> {

  @Indexed(unique = true)
  private String companyId;

  private General general;
  private Terminology terminology;
  private Targeting targeting;
  private NumberFormats numberFormats;
  private Dashboard dashboard;
  private CampaignToggles campaign;
  private InventoryToggles inventory;
  private PoiSettings poi;
  private ScheduleSettings schedule;
  private ReportsSettings reports;
  private FiltersSettings filters;
  private ApprovalsSettings approvals;
  private BonusWorkflowSettings bonusWorkflow;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class General {
    private String dateFormat;
    private String timeFormat;
    private String currencyDisplay; // e.g. "CODE" (USD) vs "SYMBOL" ($)
    private Integer decimalPlaces;
    private Integer fiscalYearStartMonth; // 1-12
    private Boolean helpBubblesEnabled;
    private Boolean tourEnabled;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Terminology {
    /** e.g. {"campaign": "Plan"} — relabels a canonical term without renaming any identifier. */
    private java.util.Map<String, String> customTerms;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Targeting {
    private List<String> ageGroupRanges;
    private List<String> incomeBrackets;
    private List<String> geographyLevels;
    private String radiusUnit; // "km" | "mi"
    private Double defaultRadius;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class NumberFormats {
    private String thousandsSeparator;
    private String decimalSeparator;
    private Boolean compactNotation; // "1.2M" vs "1,200,000"
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Dashboard {
    private List<String> visibleWidgetKeys;
    private String defaultView;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CampaignToggles {
    private Boolean setupFeaturesEnabled;
    private Boolean targetingFeaturesEnabled;
    private Boolean advancedFeaturesEnabled;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InventoryToggles {
    private List<String> visibleColumns;
    private List<String> visibleFilters;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PoiSettings {
    private Integer maxPoiPerCampaign;
    private List<Double> radiusOptions;
    private String visibilityScope; // "COMPANY" | "USER"
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ScheduleSettings {
    private Double frequencyCap;
    private Double shareOfVoiceDefault;
    private Integer spotDurationSeconds;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ReportsSettings {
    private List<String> defaultColumns;
    private String defaultExportFormat;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FiltersSettings {
    private List<String> pinnedFilterKeys;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApprovalsSettings {
    private Integer mediaOwnerAutoApproveHours; // PRD default 72
    private Integer reminderBeforeHours; // PRD default 48
  }

  /**
   * Bonus-workflow (SAB/GB) types. V1 gated this section client-side only
   * (media_owner/owner/reseller); the server-side authority check lives in {@link
   * com.mw.planner.service.config.PlannerConfigurationService}.
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class BonusWorkflowSettings {
    private Boolean enabled;
    private List<String> allowedBonusTypes; // e.g. "SAB", "GB"
  }
}
