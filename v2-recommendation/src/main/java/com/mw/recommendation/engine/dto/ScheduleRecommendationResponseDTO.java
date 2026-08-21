package com.mw.recommendation.engine.dto;

import com.mw.recommendation.engine.domain.Inventory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for schedule recommendations */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleRecommendationResponseDTO {

  private String requestId; // Unique ID for this request
  private LocalDateTime generatedAt;
  private List<InventorySchedule> schedules;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class InventorySchedule {
    private String inventoryId;
    private String referenceId;
    private String inventoryName;
    private List<RecommendedSchedule> recommendedSchedules;
    private AvailabilitySummary availability;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class RecommendedSchedule {
    private String scheduleId; // Generated ID
    private String name; // Optional schedule name
    private LocalDate scheduleStartDate;
    private LocalDate scheduleEndDate;
    private List<Weekday> scheduleDays; // Which days of the week
    private ScheduleType type; // LOOP or DAYPART
    private Map<String, List<Integer>> bookingMatrix; // Date -> List of hours (0-23)
    private Long duration; // Creative duration in seconds
    private Long spotsPerLoop; // For loop-based booking
    private Long spotsPerHour; // Total plays per hour
    private Long adPlays; // Total ad plays for the schedule
    private Double plannedSot; // Planned Share of Time
    private Double totalSot; // Total Share of Time

    /** Spot price or CPM-derived; null when inventory has no price. */
    private Double basePrice;

    private Long estimatedImpressions; // From Measure API (seasonal factors)
    private Long estimatedReach;
    private String currency;
    private Inventory.SellingTerm sellingTerm;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class AvailabilitySummary {
    private Integer availableDays;
    private Integer totalDays;
    private Double availabilityPercentage;
    private String summary; // e.g., "6/10 days available"
    private Boolean allAvailable; // From availability API response
  }

  public enum ScheduleType {
    LOOP,
    DAYPART
  }

  public enum Weekday {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
  }
}
