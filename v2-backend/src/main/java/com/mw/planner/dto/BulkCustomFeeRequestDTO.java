package com.mw.planner.dto;

import com.mw.planner.enums.CustomFeeBasedOn;
import com.mw.planner.enums.CustomFeeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for bulk creating or updating custom fees")
public class BulkCustomFeeRequestDTO {

  @Schema(
      description = "Custom fee ID (required for update, null for create)",
      example = "fee_123456")
  private String id;

  @NotBlank(message = "validation.custom_fee_name_required")
  @Size(max = 255, message = "validation.custom_fee_name_size")
  @Schema(
      description = "Custom fee name",
      example = "Service Fee",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String name;

  @Size(max = 1000, message = "validation.description_size")
  @Schema(description = "Custom fee description", example = "Service fee for campaign management")
  private String description;

  @NotNull(message = "validation.custom_fee_type_required")
  @Schema(
      description = "Custom fee type",
      example = "PERCENTAGE",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private CustomFeeType type;

  @NotNull(message = "validation.custom_fee_value_required")
  @Schema(
      description = "Custom fee value",
      example = "10.5",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private Double value;

  @NotNull(message = "validation.custom_fee_based_on_required")
  @Schema(
      description = "What the fee is based on",
      example = "BASE_COST",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private CustomFeeBasedOn basedOn;

  @Builder.Default
  @Schema(description = "Whether to include in media plan", example = "true")
  private Boolean isIncludeInMediaPlan = true;

  @Builder.Default
  @Schema(description = "Whether the fee is active", example = "true")
  private Boolean isActive = true;

  @Schema(
      description =
          "Campaign ID. If null, custom fee is for the company. If provided, custom fee is for this campaign.",
      example = "campaign_123")
  private String campaignId;
}
