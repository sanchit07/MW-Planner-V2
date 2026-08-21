package com.mw.planner.dto;

import com.mw.planner.domain.Campaign;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for campaign summary list sorted by total cost")
public class CampaignSummaryRequestDTO {

  @Schema(
      description =
          "Start date for campaign filter (inclusive). Optional; when omitted no start date filter is applied.",
      example = "2024-01-01")
  private LocalDate startDate;

  @Schema(
      description =
          "End date for campaign filter (inclusive). Optional; when omitted no end date filter is applied.",
      example = "2024-12-31")
  private LocalDate endDate;

  @Schema(
      description = "List of campaign statuses to filter by",
      example = "[\"DRAFT\", \"APPROVED\"]")
  private List<Campaign.Status> statuses;

  @Schema(
      description = "Sort field (by totalCost)",
      example = "totalCost",
      defaultValue = "totalCost")
  @Builder.Default
  private String sortBy = "totalCost";

  @Schema(description = "Sort direction: asc or desc", example = "desc", defaultValue = "desc")
  @Builder.Default
  private String sortDir = "desc";

  @Schema(description = "Maximum number of campaigns to return", example = "5", defaultValue = "5")
  @Builder.Default
  @Min(1)
  @Max(500)
  private Integer limit = 5;

  @Schema(
      description = "Company ID to filter campaigns (optional, defaults to user's company)",
      example = "company-123")
  private String companyId;
}
