package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mw.planner.enums.PricingAction;
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
@Schema(description = "Response DTO for price history entry")
public class PriceHistoryResponseDTO {

  @Schema(description = "Old price value", example = "1000.50")
  private Double oldPrice;

  @Schema(description = "New price value", example = "1200.75")
  private Double newPrice;

  @Schema(
      description = "Pricing action type",
      example = "PROPOSED",
      allowableValues = {"PENDING", "PROPOSED", "COUNTERED", "ACCEPTED"})
  private PricingAction action;

  @Schema(description = "User ID who created the price change", example = "user123")
  private String userId;

  @Schema(
      description = "Company ID of the user who created the price change",
      example = "company123")
  private String companyId;

  @Schema(description = "User who created the price change", example = "John Doe")
  private String createdBy;

  @Schema(description = "Role of the user who created the price change", example = "Media Owner")
  private String role;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Schema(
      description = "Timestamp when the price change was created",
      example = "2025-01-15 10:30:00")
  private LocalDateTime createdAt;
}
