package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for inventory import status response containing validation results for each inventory ID. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for inventory CSV import verification")
public class InventoryImportStatusResponseDTO {

  @Schema(description = "List of validation results for each inventory ID")
  private List<ValidationResult> results;

  /** Validation result for a single inventory ID. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Validation result for a single inventory ID")
  public static class ValidationResult {

    @Schema(description = "Inventory ID", example = "inv123")
    private String id;

    @Schema(description = "Validation type", example = "VALID")
    private ValidationType type;

    @Schema(description = "Validation message", example = "Inventory is valid")
    private String message;

    @Schema(description = "Row number in CSV file (1-based)", example = "2")
    private Integer row;
  }

  /** Validation type enum. */
  public enum ValidationType {
    VALID,
    INVALID,
    DUPLICATE
  }
}
