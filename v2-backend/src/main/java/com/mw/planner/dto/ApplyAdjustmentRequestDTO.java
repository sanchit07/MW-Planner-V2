package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for applying discount or bonus to schedules */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for applying discount or bonus to selected schedules")
public class ApplyAdjustmentRequestDTO {

  @NotEmpty(message = "validation.schedule_ids_required")
  @Schema(
      description = "List of schedule IDs to apply adjustment to",
      example = "[\"schedule123\", \"schedule456\"]")
  private List<String> scheduleIds = new ArrayList<>();

  @NotNull(message = "validation.action_type_required")
  @Schema(description = "Type of action: DISCOUNT or BONUS", example = "DISCOUNT")
  private ActionType actionType;

  @Valid
  @Schema(description = "Discount details (required when actionType is DISCOUNT)")
  private DiscountDTO discount;

  @Schema(
      description = "Bonus description (required when actionType is BONUS)",
      example = "Free extra slot")
  private String bonus;

  public enum ActionType {
    DISCOUNT,
    BONUS
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Discount details")
  public static class DiscountDTO {
    @NotNull(message = "validation.discount_type_required")
    @Schema(description = "Type of discount: PERCENTAGE or VALUE", example = "PERCENTAGE")
    private DiscountType discountType;

    @NotNull(message = "validation.discount_value_required")
    @Schema(description = "Discount value", example = "10")
    private Double value;

    public enum DiscountType {
      PERCENTAGE,
      VALUE
    }
  }
}
