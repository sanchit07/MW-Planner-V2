package com.mw.planner.dto;

import com.mw.planner.domain.Campaign;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for campaign list information")
public class CampaignFilterResponseDTO {
  @Schema(description = "Campaign ID", example = "campaign123")
  private String id;

  @Schema(description = "Campaign name", example = "Summer Sale Campaign 2024")
  private String name;

  @Schema(
      description = "Human-readable 12-digit plan ID (date + daily sequence)",
      example = "202607210001")
  private String planNumber;

  @Schema(description = "Brand Name associated with the campaign", example = "Nike")
  private String brandName;

  @Schema(description = "Agency Name if client type is AGENCY", example = "MG Agency")
  private String agencyName;

  @Schema(description = "Category Name associated with the brand of campaign", example = "Nike")
  private String categoryName;

  @Schema(description = "User Name who created the campaign", example = "Rishabh")
  private String userName;

  @Schema(description = "First name of the user who created the campaign", example = "Rishabh")
  private String firstName;

  @Schema(description = "Last name of the user who created the campaign", example = "Kumar")
  private String lastName;

  @Schema(
      description = "Company Name of the user who created the campaign",
      example = "Acme Corporation")
  private String companyName;

  @Schema(description = "Campaign status", example = "awaiting_agency_acceptance")
  private String status;

  @Schema(description = "Campaign goals")
  private CampaignResponseDTO.Goals goals;

  @Schema(description = "Campaign start date", example = "2024-06-01")
  private LocalDate startDate;

  @Schema(description = "Campaign end date", example = "2024-08-31")
  private LocalDate endDate;

  @Schema(description = "Campaign budget", example = "10000.00")
  private Double budget;

  @Schema(description = "Currency code", example = "USD")
  private String currency;

  @Schema(description = "Campaign inventory count", example = "10")
  private Integer inventory;

  @Schema(description = "Campaign inventory count", example = "10")
  private Double totalCost;

  @Schema(description = "Estimated total impressions", example = "1000000")
  private Long estimatedImpression;

  @Schema(description = "Estimated total reach", example = "50000")
  private Long estimatedReach;

  @Schema(description = "Average share of voice", example = "15.5")
  private Double sov;

  @Schema(description = "Total share of time", example = "82.00")
  private Double totalSot;

  @Schema(description = "Planned share of time", example = "6hrs")
  private Double plannedSot;

  @Schema(
      description = "Current company ID reference",
      example = "93b4f544-e657-4eb7-872e-7b9c1d0e0197")
  private String currentCompanyId;

  @Schema(description = "Current company name reference", example = "QA Internal")
  private String currentCompanyName;

  @Schema(description = "Whether the campaign's inventory prices have been negotiated/accepted")
  private Boolean isNegotiated;

  @Schema(description = "Data partition of the plan (\"live\" or \"demo\")", example = "live")
  private String dataMode;

  // Nested classes for complex JSON fields
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Campaign goals")
  public static class Goals {
    @Schema(description = "Goal type", example = "IMPRESSIONS")
    private Campaign.Goals.GoalType goalType;

    @Schema(description = "Target name for custom goals", example = "Brand Awareness")
    private String targetName;

    @Schema(description = "Target value", example = "1000000.0")
    private Double targetValue;

    @Schema(description = "Goal type name", example = "Impressions")
    private String typeName;
  }
}
