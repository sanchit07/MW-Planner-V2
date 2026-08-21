package com.mw.recommendation.engine.dto.measure;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request for Measure API reach and frequency (align with mw-planner). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureReachFrequencyRequestDTO {
  private List<MeasureInventoryDTO> inventories;
  private Integer duration;
}
