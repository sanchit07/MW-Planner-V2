package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO from Measure reach and frequency API")
public class MeasureReachFrequencyResponseDTO {

  @Schema(
      description = "Inventory reference ID (present in site-level responses)",
      example = "BRA-HEZ-C-00000-38167")
  private String referenceId;

  @Schema(
      description = "Inventory type (present in site-level responses)",
      example = "static_billboard")
  private String type;

  @Schema(description = "Total reach count", example = "447747")
  private Long reach;

  @Schema(description = "Response status", example = "success")
  private String status;

  @Schema(description = "Average frequency", example = "17.900019430615952")
  private Double frequency;

  @Schema(description = "Total impressions", example = "8014680")
  private Long impressions;
}
