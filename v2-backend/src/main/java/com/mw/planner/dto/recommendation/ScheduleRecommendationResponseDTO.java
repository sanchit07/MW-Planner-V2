package com.mw.planner.dto.recommendation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleRecommendationResponseDTO {

  private String requestId;
  private LocalDateTime generatedAt;
  private List<InventorySchedule> schedules;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InventorySchedule {
    private String inventoryId;
    private String referenceId;
    private String inventoryName;
    private List<RecommendedSchedule> recommendedSchedules;
    private AvailabilitySummary availability;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RecommendedSchedule {
    private String scheduleId;
    private String name;
    private LocalDate scheduleStartDate;
    private LocalDate scheduleEndDate;
    private List<String> scheduleDays;
    private String type;
    private Map<String, List<Integer>> bookingMatrix;
    private Long duration;
    private Long spotsPerLoop;
    private Long spotsPerHour;
    private Long adPlays;
    private Double plannedSot;
    private Double totalSot;
    private Double basePrice;
    private Long estimatedImpressions;
    private Long estimatedReach;
    private String currency;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AvailabilitySummary {
    private Integer availableDays;
    private Integer totalDays;
    private Double availabilityPercentage;
    private String summary;
    private Boolean allAvailable;
  }
}
