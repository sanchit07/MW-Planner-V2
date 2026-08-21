package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for bulk schedule operations */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for bulk schedule operations")
public class BulkSchedulesRequestDTO {

  @NotEmpty(message = "validation.inventory_ids_not_empty")
  @Schema(
      description = "List of inventory IDs to create/update schedules for",
      example = "[\"inv1\", \"inv2\", \"inv3\"]",
      required = true)
  private List<String> inventoryIds;

  @Schema(
      description = "Whether to clear all existing schedules before creating new one",
      example = "true",
      required = true)
  private boolean clearSchedules;

  @Valid
  @Schema(description = "Schedule details to apply", required = true)
  private ScheduleDTO schedule;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Schedule details")
  public static class ScheduleDTO {

    @Schema(description = "Schedule ID", example = "schedule123")
    private String id;

    @Schema(description = "Schedule start date", example = "2025-12-01", required = true)
    private LocalDate startDate;

    @Schema(description = "Schedule end date", example = "2025-12-31", required = true)
    private LocalDate endDate;

    @Schema(
        description = "List of days when schedule is active",
        example = "[\"MONDAY\", \"TUESDAY\"]",
        required = true)
    private List<String> scheduleDays;

    @Schema(
        description =
            "Booking matrix mapping dates to scheduled hours. Key: Date string in format 'yyyy-MM-dd' (e.g., '2025-12-01'). Value: List of hours (0-23) when ads are scheduled for that date.",
        example = "{\"2025-12-01\": [1, 5, 10], \"2025-12-02\": [2, 6, 14]}",
        required = true)
    private Map<String, List<Integer>> bookingMatrix;

    @Schema(description = "Order", example = "1")
    private Integer order;

    @Schema(description = "Base Price", example = "100.50")
    private Double basePrice;

    @Schema(description = "Discount information")
    private DiscountDTO discount;

    @Schema(description = "Bonus type", example = "BONUS_TYPE_1")
    private String bonusType;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Discount details")
    public static class DiscountDTO {
      @Schema(description = "Discount value type", example = "PERCENTAGE")
      private String valueType;

      @Schema(description = "Discount value", example = "10")
      private String value;
    }
  }
}
