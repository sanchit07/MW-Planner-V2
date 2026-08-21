package com.mw.planner.dto;

import com.mw.planner.domain.Campaign;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for campaign Media Plan Header details")
public class HeaderInfoDTO {

  @Schema(description = "Unique identifier of the campaign", example = "camp-12345")
  private String id;

  @Schema(description = "Name of the campaign", example = "Summer Sale Campaign")
  private String name;

  @Schema(description = "Human-readable plan number of the campaign", example = "202607210001")
  private String planNumber;

  @Schema(
      description = "Start date of the campaign in ISO 8601 format",
      example = "2024-06-01T00:00:00Z")
  private String startDate;

  @Schema(
      description = "End date of the campaign in ISO 8601 format",
      example = "2024-06-30T23:59:59Z")
  private String endDate;

  @Schema(description = "Budget allocated for the campaign", example = "5000.00")
  private Double budget;

  @Schema(description = "Status of the campaign", example = "DRAFT")
  private String status;

  @Schema(description = "Duration of the campaign", example = "5000.00")
  private Integer duration;

  @Schema(description = "Currency used in the campaign", example = "USD")
  private String currency;

  @Schema(description = "User who created the campaign", example = "Rishabh")
  private String preparedBy;

  @Schema(description = "Date the campaign was created", example = "2024-06-01T10:30:00")
  private String createdAt;

  @Schema(description = "Campaign goal type", example = "IMPRESSIONS")
  private String goalType;

  @Schema(description = "Campaign target value for the goal", example = "100000")
  private Double targetValue;

  @Schema(description = "Company details of the campaign owner")
  private Campaign.CompanyDetails companyDetails;

  @Schema(description = "Email address of the campaign creator", example = "user@example.com")
  private String userEmail;

  @Schema(description = "Demand Side Platform name or identifier", example = "DV360")
  private String dsp;

  @Schema(description = "Whether the campaign's inventory prices have been negotiated/accepted")
  private Boolean isNegotiated;
}
