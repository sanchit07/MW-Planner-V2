package com.mw.recommendation.engine.dto;

import com.mw.recommendation.engine.domain.Inventory;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response for GET run schedules: runId and list of schedules per inventory. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RunSchedulesResponseDTO {
  private String runId;
  private List<RunScheduleItemDTO> schedules;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
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
    private Inventory.SellingTerm sellingTerm;
  }
}
