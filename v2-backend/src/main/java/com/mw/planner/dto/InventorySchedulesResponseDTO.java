package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for inventory schedules grouped by inventory ID */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response DTO containing schedules for an inventory")
public class InventorySchedulesResponseDTO {

  @Schema(description = "Inventory ID", example = "inv123")
  private String inventoryId;

  @Schema(description = "List of schedules for this inventory")
  private List<ScheduleDTO> schedules;

  /** Schedule DTO containing schedule details */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  @Schema(description = "Schedule details")
  public static class ScheduleDTO {

    @Schema(description = "Schedule ID", example = "schedule123")
    private String id;

    @Schema(description = "Schedule Name", example = "sch1")
    private String name;

    @Schema(description = "Schedule start date", example = "2025-01-10")
    private LocalDate startDate;

    @Schema(description = "Schedule end date", example = "2025-01-20")
    private LocalDate endDate;

    @Schema(
        description = "List of days when schedule is active",
        example = "[\"MONDAY\", \"TUESDAY\", \"WEDNESDAY\"]")
    private List<String> scheduleDays;

    @Schema(
        description =
            "Booking matrix mapping dates to scheduled hours. Key: Date string in format 'dd-MMM-yyyy' (e.g., '01-Dec-2025'). Value: List of hours (0-23) when ads are scheduled for that date.",
        example = "{\"01-Dec-2025\": [0, 1, 2, 3], \"02-Dec-2025\": [10, 11, 12, 13]}")
    private Map<String, List<Integer>> bookingMatrix;

    @Schema(description = "Duration in days (calculated from start and end date)", example = "10")
    private Long duration;

    @Schema(description = "Number of spots per loop", example = "5")
    private Long spotsPerLoop;

    @Schema(description = "Number of spots per hour", example = "12")
    private Long spotsPerHour;

    @Schema(description = "Number of ad plays", example = "60")
    private Long adPlays;

    private Double plannedSot;

    private Double totalSot;

    private Double sov;

    @Schema(description = "Order", example = "1")
    private Integer order;

    @Schema(description = "Base Price", example = "100.50")
    private Double basePrice;

    @Schema(description = "Discount information")
    private DiscountDTO discount;

    @Schema(description = "Bonus type", example = "BONUS_TYPE_1")
    private String bonusType;

    @Schema(description = "Estimated impressions", example = "447747")
    private Long impressions;

    @Schema(description = "Estimated reach", example = "8014680")
    private Long reach;

    @Schema(description = "Estimated frequency", example = "17.900019430615952")
    private Double frequency;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Discount details")
    public static class DiscountDTO {
      @Schema(description = "Discount value type", example = "PERCENTAGE")
      private String valueType;

      @Schema(description = "Discount value", example = "10")
      private String value;
    }
  }
}
