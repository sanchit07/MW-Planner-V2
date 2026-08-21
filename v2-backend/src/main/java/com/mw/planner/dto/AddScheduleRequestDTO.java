package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for adding a new schedule */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for adding a new schedule")
public class AddScheduleRequestDTO {

  @NotBlank(message = "validation.inventory_id_required")
  @Schema(description = "Inventory ID", example = "inventory123", required = true)
  private String inventoryId;

  @Schema(description = "Schedule name", example = "Morning Schedule")
  private String name;

  @NotNull(message = "validation.schedule_start_date_required")
  @Schema(description = "Schedule start date", example = "2025-12-01", required = true)
  private LocalDate startDate;

  @NotNull(message = "validation.schedule_end_date_required")
  @Schema(description = "Schedule end date", example = "2025-12-31", required = true)
  private LocalDate endDate;

  @Schema(
      description = "List of days when schedule is active",
      example = "[\"MONDAY\", \"TUESDAY\", \"WEDNESDAY\"]")
  private List<String> scheduleDays;

  @Schema(
      description =
          "Booking matrix mapping dates to scheduled hours. Key: Date string in format 'yyyy-MM-dd' (e.g., '2025-12-25'). Value: List of hours (0-23) when ads are scheduled for that date.",
      example = "{\"2025-12-01\": [0, 1, 2, 3], \"2025-12-02\": [10, 11, 12, 13]}")
  private Map<String, List<Integer>> bookingMatrix;

  @Schema(description = "Duration in seconds", example = "30")
  private Long duration;

  @Schema(description = "Number of spots per loop", example = "1")
  private Long spotsPerLoop;

  @Schema(description = "Number of spots per hour", example = "12")
  private Long spotsPerHour;

  @Schema(description = "Order", example = "1")
  private Integer order;
}
