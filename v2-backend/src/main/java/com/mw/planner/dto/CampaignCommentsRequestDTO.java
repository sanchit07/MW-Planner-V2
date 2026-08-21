package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for creating campaign comments with file uploads")
public class CampaignCommentsRequestDTO {

  @NotBlank(message = "validation.comment_required")
  @Schema(
      description = "Comment text",
      example = "Test Comment",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String comment;

  @Schema(description = "List of tagged company IDs", example = "[\"5ea826a860e5e930d8d6cf47\"]")
  private List<String> taggedCompanyIds;
}
