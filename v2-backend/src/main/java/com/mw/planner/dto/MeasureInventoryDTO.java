package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Inventory item for Measure API request")
public class MeasureInventoryDTO {

  @NotBlank(message = "validation.measure_inventory_reference_id_required")
  @Size(max = 100, message = "validation.measure_inventory_reference_id_size")
  @Schema(description = "Inventory reference ID", example = "USA-NEW-D-00000-03420")
  private String referenceId;

  @NotBlank(message = "validation.measure_inventory_type_required")
  @Size(max = 50, message = "validation.measure_inventory_type_size")
  @Schema(description = "Inventory type", example = "billboard")
  private String type;

  @NotNull(message = "validation.measure_inventory_spots_per_hour_required")
  @Positive(message = "validation.measure_inventory_spots_per_hour_positive")
  @Schema(description = "Number of spots per hour", example = "36")
  private Integer spotsPerHour;

  @Schema(description = "Daypart details for the inventory")
  private List<Dayparts> dayparts;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Daypart information for inventory")
  public static class Dayparts {
    @Schema(description = "Scheduled date in YYYY-MM-DD format", example = "2024-07-01")
    private String scheduledDate;

    @Schema(
        description = "List of scheduled times in HH format",
        example = "[\"08\", \"12\", \"16\"]")
    private List<String> scheduledTime;
  }
}
