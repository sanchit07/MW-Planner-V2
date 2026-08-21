package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for campaign comments")
public class CampaignCommentsResponseDTO {

  @Schema(description = "Comment text", example = "test comment")
  private String comment;

  @Schema(description = "User who created the comment", example = "user@example.com")
  private String createdBy;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Schema(description = "Comment creation timestamp", example = "2024-01-15 10:30:00")
  private LocalDateTime createdAt;

  @Schema(
      description = "Business type of the company from taggedCompanyIds",
      example = "MEDIA_OWNER")
  private CompanyDto.BusinessType businessType;

  @Schema(
      description = "Uploaded file Urls in S3",
      example = "bmw-car-logo-735811696610457s9siw7ivja.png")
  private List<String> fileUrls;
}
