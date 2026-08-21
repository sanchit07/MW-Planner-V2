package com.mw.planner.dto.creative;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for {@code POST /api/v1/creative-assignments}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreativeAssignmentRequestDTO {

  @NotBlank private String creativeId;
  @NotBlank private String lineItemId;

  /**
   * Confirms an aspect-ratio override (PRD rule 1). Ignored for the duration (rule 2), file-size
   * (rule 3) and campaign-status (rule 4) gates, which are never overridable.
   */
  @Builder.Default private boolean forceMatch = false;
}
