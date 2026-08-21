package com.mw.planner.dto;

import com.mw.planner.domain.CampaignActivity;
import com.mw.planner.service.CampaignActivityService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for campaign activity/history")
public class CampaignActivityResponseDTO {

  @Schema(description = "Activity ID", example = "activity123")
  private String id;

  @Schema(description = "Campaign ID", example = "campaign123")
  private String campaignId;

  @Schema(description = "User ID who performed the action", example = "user123")
  private String userId;

  @Schema(description = "Company ID", example = "company123")
  private String companyId;

  @Schema(description = "Role", example = "Media Owner")
  private String role;

  @Schema(description = "Who updated (user name or System)", example = "John Doe")
  private String createdBy;

  @Schema(
      description = "Human-readable localized message describing the action",
      example = "Created the Campaign with name: Campaign_Dec_10_25_001")
  private String message;

  @Schema(description = "Timestamp when the activity occurred", example = "2025-12-10T10:30:00")
  private LocalDateTime createdAt;

  /**
   * Convert CampaignActivity entity to DTO with localized message generation.
   *
   * @param activity CampaignActivity entity
   * @param locale User's locale for message generation
   * @param activityService Service instance for message generation
   * @param role Business type role (e.g., "Media Owner", "Agency", "Media Buyer")
   * @return CampaignActivityResponseDTO with localized message and role
   */
  public static CampaignActivityResponseDTO fromEntity(
      CampaignActivity activity,
      Locale locale,
      CampaignActivityService activityService,
      String role) {
    String localizedMessage =
        activityService.generateLocalizedMessage(
            activity.getOperationType(), activity.getValues(), locale);

    return CampaignActivityResponseDTO.builder()
        .id(activity.getId())
        .campaignId(activity.getCampaignId())
        .userId(activity.getUserId())
        .companyId(activity.getCompanyId())
        .role(role)
        .createdBy(activity.getUpdatedBy())
        .message(localizedMessage)
        .createdAt(activity.getUpdatedAt())
        .build();
  }
}
