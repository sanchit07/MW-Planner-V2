package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mw.planner.enums.CustomFeeBasedOn;
import com.mw.planner.enums.CustomFeeType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for custom fee information")
public class CustomFeeResponseDTO {

  @Schema(description = "Custom fee ID", example = "fee_123456")
  private String id;

  @Schema(description = "Custom fee name", example = "Service Fee")
  private String name;

  @Schema(description = "Custom fee description", example = "Service fee for campaign management")
  private String description;

  @Schema(description = "Custom fee type", example = "PERCENTAGE")
  private CustomFeeType type;

  @Schema(description = "Custom fee value", example = "10.5")
  private Double value;

  @Schema(description = "What the fee is based on", example = "BASE_COST")
  private CustomFeeBasedOn basedOn;

  @Schema(description = "Whether to include in media plan", example = "true")
  private Boolean isIncludeInMediaPlan;

  @Schema(description = "Whether the fee is active", example = "true")
  private Boolean isActive;

  @Schema(description = "Company ID", example = "company_123")
  private String companyId;

  @Schema(
      description =
          "Campaign ID. If null, fee is for company; if non-null, fee is for this campaign.",
      example = "campaign_123")
  private String campaignId;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Schema(description = "Custom fee creation timestamp", example = "2024-01-15 10:30:00")
  private LocalDateTime createdAt;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Schema(description = "Custom fee last update timestamp", example = "2024-01-15 14:45:00")
  private LocalDateTime updatedAt;

  @Schema(
      description = "Effective custom fee amount (calculated based on discounted media cost)",
      example = "150.0")
  private Double effectiveCustomFee;
}
