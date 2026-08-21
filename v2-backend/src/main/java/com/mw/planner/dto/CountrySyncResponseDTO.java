package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for country sync operations")
public class CountrySyncResponseDTO {

  @Schema(description = "Total number of countries synced", example = "195")
  private int syncedCount;

  @Schema(description = "Number of countries updated", example = "12")
  private int updatedCount;

  @Schema(description = "Number of countries created", example = "3")
  private int createdCount;

  @Schema(description = "Sync operation message", example = "Country sync completed successfully")
  private String message;
}
