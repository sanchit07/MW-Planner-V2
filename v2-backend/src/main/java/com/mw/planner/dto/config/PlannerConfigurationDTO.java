package com.mw.planner.dto.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mw.planner.domain.PlannerConfiguration;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request/response shape for {@code GET|PUT /api/v1/config/settings/{companyId}}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlannerConfigurationDTO {

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
    private String currencyDisplay;
    private Integer decimalPlaces;
    private Integer fiscalYearStartMonth;
    private Boolean helpBubblesEnabled;
    private Boolean tourEnabled;

    public static General from(PlannerConfiguration.General d) {
      if (d == null) return null;
      return General.builder()
          .dateFormat(d.getDateFormat())
          .timeFormat(d.getTimeFormat())
          .currencyDisplay(d.getCurrencyDisplay())
          .decimalPlaces(d.getDecimalPlaces())
          .fiscalYearStartMonth(d.getFiscalYearStartMonth())
          .helpBubblesEnabled(d.getHelpBubblesEnabled())
          .tourEnabled(d.getTourEnabled())
          .build();
    }

    public PlannerConfiguration.General toDomain() {
      return PlannerConfiguration.General.builder()
          .dateFormat(dateFormat)
          .timeFormat(timeFormat)
          .currencyDisplay(currencyDisplay)
          .decimalPlaces(decimalPlaces)
          .fiscalYearStartMonth(fiscalYearStartMonth)
          .helpBubblesEnabled(helpBubblesEnabled)
          .tourEnabled(tourEnabled)
          .build();
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Terminology {
    private Map<String, String> customTerms;

    public static Terminology from(PlannerConfiguration.Terminology d) {
      if (d == null) return null;
      return Terminology.builder().customTerms(d.getCustomTerms()).build();
    }

    public PlannerConfiguration.Terminology toDomain() {
      return PlannerConfiguration.Terminology.builder().customTerms(customTerms).build();
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Targeting {
    private List<String> ageGroupRanges;
    private List<String> incomeBrackets;
    private List<String> geographyLevels;
    private String radiusUnit;
    private Double defaultRadius;

    public static Targeting from(PlannerConfiguration.Targeting d) {
      if (d == null) return null;
      return Targeting.builder()
          .ageGroupRanges(d.getAgeGroupRanges())
          .incomeBrackets(d.getIncomeBrackets())
          .geographyLevels(d.getGeographyLevels())
          .radiusUnit(d.getRadiusUnit())
          .defaultRadius(d.getDefaultRadius())
          .build();
    }

    public PlannerConfiguration.Targeting toDomain() {
      return PlannerConfiguration.Targeting.builder()
          .ageGroupRanges(ageGroupRanges)
          .incomeBrackets(incomeBrackets)
          .geographyLevels(geographyLevels)
          .radiusUnit(radiusUnit)
          .defaultRadius(defaultRadius)
          .build();
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class NumberFormats {
    private String thousandsSeparator;
    private String decimalSeparator;
    private Boolean compactNotation;

    public static NumberFormats from(PlannerConfiguration.NumberFormats d) {
      if (d == null) return null;
      return NumberFormats.builder()
          .thousandsSeparator(d.getThousandsSeparator())
          .decimalSeparator(d.getDecimalSeparator())
          .compactNotation(d.getCompactNotation())
          .build();
    }

    public PlannerConfiguration.NumberFormats toDomain() {
      return PlannerConfiguration.NumberFormats.builder()
          .thousandsSeparator(thousandsSeparator)
          .decimalSeparator(decimalSeparator)
          .compactNotation(compactNotation)
          .build();
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Dashboard {
    private List<String> visibleWidgetKeys;
    private String defaultView;

    public static Dashboard from(PlannerConfiguration.Dashboard d) {
      if (d == null) return null;
      return Dashboard.builder()
          .visibleWidgetKeys(d.getVisibleWidgetKeys())
          .defaultView(d.getDefaultView())
          .build();
    }

    public PlannerConfiguration.Dashboard toDomain() {
      return PlannerConfiguration.Dashboard.builder()
          .visibleWidgetKeys(visibleWidgetKeys)
          .defaultView(defaultView)
          .build();
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CampaignToggles {
    private Boolean setupFeaturesEnabled;
    private Boolean targetingFeaturesEnabled;
    private Boolean advancedFeaturesEnabled;

    public static CampaignToggles from(PlannerConfiguration.CampaignToggles d) {
      if (d == null) return null;
      return CampaignToggles.builder()
          .setupFeaturesEnabled(d.getSetupFeaturesEnabled())
          .targetingFeaturesEnabled(d.getTargetingFeaturesEnabled())
          .advancedFeaturesEnabled(d.getAdvancedFeaturesEnabled())
          .build();
    }

    public PlannerConfiguration.CampaignToggles toDomain() {
      return PlannerConfiguration.CampaignToggles.builder()
          .setupFeaturesEnabled(setupFeaturesEnabled)
          .targetingFeaturesEnabled(targetingFeaturesEnabled)
          .advancedFeaturesEnabled(advancedFeaturesEnabled)
          .build();
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InventoryToggles {
    private List<String> visibleColumns;
    private List<String> visibleFilters;

    public static InventoryToggles from(PlannerConfiguration.InventoryToggles d) {
      if (d == null) return null;
      return InventoryToggles.builder()
          .visibleColumns(d.getVisibleColumns())
          .visibleFilters(d.getVisibleFilters())
          .build();
    }

    public PlannerConfiguration.InventoryToggles toDomain() {
      return PlannerConfiguration.InventoryToggles.builder()
          .visibleColumns(visibleColumns)
          .visibleFilters(visibleFilters)
          .build();
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PoiSettings {
    private Integer maxPoiPerCampaign;
    private List<Double> radiusOptions;
    private String visibilityScope;

    public static PoiSettings from(PlannerConfiguration.PoiSettings d) {
      if (d == null) return null;
      return PoiSettings.builder()
          .maxPoiPerCampaign(d.getMaxPoiPerCampaign())
          .radiusOptions(d.getRadiusOptions())
          .visibilityScope(d.getVisibilityScope())
          .build();
    }

    public PlannerConfiguration.PoiSettings toDomain() {
      return PlannerConfiguration.PoiSettings.builder()
          .maxPoiPerCampaign(maxPoiPerCampaign)
          .radiusOptions(radiusOptions)
          .visibilityScope(visibilityScope)
          .build();
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ScheduleSettings {
    private Double frequencyCap;
    private Double shareOfVoiceDefault;
    private Integer spotDurationSeconds;

    public static ScheduleSettings from(PlannerConfiguration.ScheduleSettings d) {
      if (d == null) return null;
      return ScheduleSettings.builder()
          .frequencyCap(d.getFrequencyCap())
          .shareOfVoiceDefault(d.getShareOfVoiceDefault())
          .spotDurationSeconds(d.getSpotDurationSeconds())
          .build();
    }

    public PlannerConfiguration.ScheduleSettings toDomain() {
      return PlannerConfiguration.ScheduleSettings.builder()
          .frequencyCap(frequencyCap)
          .shareOfVoiceDefault(shareOfVoiceDefault)
          .spotDurationSeconds(spotDurationSeconds)
          .build();
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ReportsSettings {
    private List<String> defaultColumns;
    private String defaultExportFormat;

    public static ReportsSettings from(PlannerConfiguration.ReportsSettings d) {
      if (d == null) return null;
      return ReportsSettings.builder()
          .defaultColumns(d.getDefaultColumns())
          .defaultExportFormat(d.getDefaultExportFormat())
          .build();
    }

    public PlannerConfiguration.ReportsSettings toDomain() {
      return PlannerConfiguration.ReportsSettings.builder()
          .defaultColumns(defaultColumns)
          .defaultExportFormat(defaultExportFormat)
          .build();
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FiltersSettings {
    private List<String> pinnedFilterKeys;

    public static FiltersSettings from(PlannerConfiguration.FiltersSettings d) {
      if (d == null) return null;
      return FiltersSettings.builder().pinnedFilterKeys(d.getPinnedFilterKeys()).build();
    }

    public PlannerConfiguration.FiltersSettings toDomain() {
      return PlannerConfiguration.FiltersSettings.builder()
          .pinnedFilterKeys(pinnedFilterKeys)
          .build();
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApprovalsSettings {
    private Integer mediaOwnerAutoApproveHours;
    private Integer reminderBeforeHours;

    public static ApprovalsSettings from(PlannerConfiguration.ApprovalsSettings d) {
      if (d == null) return null;
      return ApprovalsSettings.builder()
          .mediaOwnerAutoApproveHours(d.getMediaOwnerAutoApproveHours())
          .reminderBeforeHours(d.getReminderBeforeHours())
          .build();
    }

    public PlannerConfiguration.ApprovalsSettings toDomain() {
      return PlannerConfiguration.ApprovalsSettings.builder()
          .mediaOwnerAutoApproveHours(mediaOwnerAutoApproveHours)
          .reminderBeforeHours(reminderBeforeHours)
          .build();
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class BonusWorkflowSettings {
    private Boolean enabled;
    private List<String> allowedBonusTypes;

    public static BonusWorkflowSettings from(PlannerConfiguration.BonusWorkflowSettings d) {
      if (d == null) return null;
      return BonusWorkflowSettings.builder()
          .enabled(d.getEnabled())
          .allowedBonusTypes(d.getAllowedBonusTypes())
          .build();
    }

    public PlannerConfiguration.BonusWorkflowSettings toDomain() {
      return PlannerConfiguration.BonusWorkflowSettings.builder()
          .enabled(enabled)
          .allowedBonusTypes(allowedBonusTypes)
          .build();
    }
  }

  /** Maps the full domain document to its DTO. Null-safe per section. */
  public static PlannerConfigurationDTO from(PlannerConfiguration d) {
    return PlannerConfigurationDTO.builder()
        .companyId(d.getCompanyId())
        .general(General.from(d.getGeneral()))
        .terminology(Terminology.from(d.getTerminology()))
        .targeting(Targeting.from(d.getTargeting()))
        .numberFormats(NumberFormats.from(d.getNumberFormats()))
        .dashboard(Dashboard.from(d.getDashboard()))
        .campaign(CampaignToggles.from(d.getCampaign()))
        .inventory(InventoryToggles.from(d.getInventory()))
        .poi(PoiSettings.from(d.getPoi()))
        .schedule(ScheduleSettings.from(d.getSchedule()))
        .reports(ReportsSettings.from(d.getReports()))
        .filters(FiltersSettings.from(d.getFilters()))
        .approvals(ApprovalsSettings.from(d.getApprovals()))
        .bonusWorkflow(BonusWorkflowSettings.from(d.getBonusWorkflow()))
        .build();
  }
}
