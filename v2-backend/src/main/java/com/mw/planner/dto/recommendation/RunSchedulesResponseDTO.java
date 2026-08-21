package com.mw.planner.dto.recommendation;

import java.time.LocalDate;
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
public class RunSchedulesResponseDTO {

  private String runId;
  private List<RunScheduleItemDTO> schedules;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RunScheduleItemDTO {
    private String inventoryId;
    private String scheduleId;
    private LocalDate scheduleStartDate;
    private LocalDate scheduleEndDate;
    private Map<String, List<Integer>> bookingMatrix;
    private Long adPlays;
    private Double plannedSot;
    private Double totalSot;
    private Long spotsPerLoop;
    private Long spotsPerHour;
    private Long duration;
    private Double basePrice;
    private Long estimatedImpressions;
    private Long estimatedReach;
    private String currency;
  }
}
