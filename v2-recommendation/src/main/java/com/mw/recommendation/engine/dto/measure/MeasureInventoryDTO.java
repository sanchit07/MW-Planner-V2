package com.mw.recommendation.engine.dto.measure;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Inventory item for Measure API request (align with mw-planner). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureInventoryDTO {
  private String referenceId;
  private String type;
  private Integer spotsPerHour;
  private List<Dayparts> dayparts;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Dayparts {
    private String scheduledDate;
    private List<String> scheduledTime;
  }
}
