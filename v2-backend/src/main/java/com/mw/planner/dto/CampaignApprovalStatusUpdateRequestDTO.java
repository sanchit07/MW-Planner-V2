package com.mw.planner.dto;

import com.mw.planner.domain.CampaignApprovalHistory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for updating campaign approval status")
public class CampaignApprovalStatusUpdateRequestDTO {

  @NotNull(message = "validation.approval_status_required")
  @Schema(
      description = "Approval action",
      example = "APPROVED",
      allowableValues = {"APPROVED", "REJECTED", "IN_NEGOTIATION"},
      requiredMode = Schema.RequiredMode.REQUIRED)
  private CampaignApprovalHistory.Status status;

  @Schema(description = "Comment for the approval action", example = "Campaign approved by agency")
  private String comment;
}
