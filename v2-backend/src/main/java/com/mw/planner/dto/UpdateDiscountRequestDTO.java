package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for updating discount on all schedules of a CampaignInventorySchedules */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for updating discount based on proposed price")
public class UpdateDiscountRequestDTO {

  @NotNull(message = "validation.proposed_price_required")
  @Positive(message = "validation.proposed_price_must_be_positive")
  @Schema(
      description = "Proposed price for the CampaignInventorySchedules or specific schedule",
      example = "80.0")
  private Double proposedPrice;

  @Schema(
      description =
          "Optional schedule ID. If provided, discount will be applied only to this schedule",
      example = "schedule_123")
  private String scheduleId;
}
