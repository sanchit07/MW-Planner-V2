package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for campaign details")
public class CampaignViewResponseDTO {

  @Schema(description = "Unique identifier of the campaign", example = "camp-12345")
  private String id;

  @Schema(description = "Name of the campaign", example = "Summer Sale Campaign")
  private String name;

  @Schema(description = "Human-readable plan number of the campaign", example = "202607210001")
  private String planNumber;

  @Schema(description = "Status of the campaign", example = "DRAFT")
  private String status;

  @Schema(description = "Currency used in the campaign", example = "USD")
  private String currency;

  @Schema(description = "Whether the campaign's inventory prices have been negotiated/accepted")
  private Boolean isNegotiated;

  @Schema(description = "Key Stakeholder Details")
  @JsonProperty("campaignDetail")
  private CampaignDetail campaignDetail;

  @Schema(description = "Key Stakeholder Details")
  @JsonProperty("keyStakeholderDetail")
  private CampaignKeyStakeholderDetail campaignKeyStakeholderDetail;

  @Schema(description = "Goals Details")
  @JsonProperty("goals")
  private Goals goals;

  @Schema(description = "Targeting Details")
  @JsonProperty("targeting")
  private Targeting targeting;

  @Schema(description = "Selected Inventory Overview Details")
  @JsonProperty("inventoryOverview")
  private InventoryOverview inventoryOverview;

  @Schema(description = "Performance Metrics")
  @JsonProperty("performance")
  private CampaignForecastDTO campaignForecast;

  @Schema(description = "Cost Breakdown Details")
  @JsonProperty("costBreakdown")
  private CostBreakdown costBreakdown;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CampaignDetail {

    @Schema(description = "Campaign country name", example = "India")
    private String country;

    @Schema(description = "Budget allocated for the campaign", example = "5000.00")
    private Double budget;

    @Schema(
        description = "Start date of the campaign in ISO 8601 format",
        example = "2024-06-01T00:00:00Z")
    private String startDate;

    @Schema(
        description = "End date of the campaign in ISO 8601 format",
        example = "2024-06-30T23:59:59Z")
    private String endDate;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CampaignKeyStakeholderDetail {

    @Schema(description = "Agency handling the campaign", example = "MW-Planner")
    private String agency;

    @Schema(description = "User who created the campaign", example = "Rishabh")
    private String planner;

    @Schema(description = "Brand associated with the campaign", example = "BrandX")
    private String brand;

    @Schema(description = "Brand Category associated with the campaign", example = "Electronics")
    private String brandCategory;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Targeting {

    @Schema(description = "Targeting Strategy Details")
    @JsonProperty("audienceDemographics")
    private AudienceDemographicsTargetingStrategyDTO audienceDemographicsTargetingStrategyDTO;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InventoryOverview {
    private int totalInventories;
    private int totalFormats;
    private int totalTypes;
    private int totalCity;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CostBreakdown {
    @Schema(description = "Total Media Cost")
    private Double mediaCost;

    @Schema(description = "Platform Fee")
    private Double platformFee;

    @Schema(description = "Net Cost")
    private Double netCost;

    @Schema(description = "Fee Structure details")
    private Map<String, Double> customFees;

    @Schema(description = "Total Custom Fees")
    private Double totalCustomFees;

    @Schema(description = "Total Cost")
    private Double totalCost;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Goals {
    private String goalType;
    private Double targetValue;
    private Double achievedValue;
    private Map<String, Double> weeklyBreakdown;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class WeeklyGoals {
    private Integer weekNumber;
    private Integer days;
    private LocalDate startDate;
    private LocalDate endDate;
  }
}
