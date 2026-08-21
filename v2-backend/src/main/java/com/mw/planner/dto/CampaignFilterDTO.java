package com.mw.planner.dto;

import com.mw.planner.domain.Campaign;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Filter criteria for campaign search")
public class CampaignFilterDTO {

  @Schema(description = "Campaign name contains this string (case-insensitive)", example = "Summer")
  private String nameContains;

  @Schema(
      description = "List of campaign statuses to filter by",
      example = "[\"DRAFT\", \"APPROVED\"]")
  private List<Campaign.Status> statuses;

  @Schema(description = "List of goal types to filter by", example = "[\"IMPRESSIONS\", \"REACH\"]")
  private List<Campaign.Goals.GoalType> goalTypes;

  @Schema(description = "List of user IDs to filter by", example = "[\"user1\", \"user2\"]")
  private List<String> userIds;

  @Schema(description = "Start date for date range filter (inclusive)", example = "2024-01-01")
  private LocalDate startDateFrom;

  @Schema(description = "End date for date range filter (inclusive)", example = "2024-12-31")
  private LocalDate startDateTo;

  @Schema(
      description = "Filter by campaign createdAt >= this date (inclusive)",
      example = "2026-07-17")
  private LocalDate createdAtFrom;

  @Schema(
      description = "Filter by campaign createdAt <= this date (inclusive, whole day)",
      example = "2026-07-24")
  private LocalDate createdAtTo;

  @Schema(description = "Company ID to filter by", example = "company123")
  private String companyId;

  @Schema(
      description =
          "Data-mode partition to filter by (\"live\" or \"demo\"). Set server-side from the"
              + " caller's Test Mode; client-supplied values are ignored.",
      example = "live",
      accessMode = Schema.AccessMode.READ_ONLY)
  private String dataMode;
}
