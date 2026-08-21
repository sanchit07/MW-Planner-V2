package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for Measure reach and frequency API")
public class MeasureReachFrequencyRequestDTO {

  @NotEmpty(message = "validation.measure_inventory_inventories_required")
  @Valid
  @Schema(description = "List of inventory items")
  private List<MeasureInventoryDTO> inventories;

  @Positive(message = "validation.measure_inventory_duration_positive")
  @Schema(description = "Campaign duration in days", example = "30")
  private Integer duration;

  @Schema(description = "Campaign start date in YYYY-MM-DD format", example = "2025-01-01")
  private String startDate;

  @Schema(description = "Campaign end date in YYYY-MM-DD format", example = "2025-01-31")
  private String endDate;
}
