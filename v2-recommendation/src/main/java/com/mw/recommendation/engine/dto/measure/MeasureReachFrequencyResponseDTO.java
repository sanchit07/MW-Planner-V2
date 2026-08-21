package com.mw.recommendation.engine.dto.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response from Measure API reach and frequency (align with mw-planner). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureReachFrequencyResponseDTO {
  private String referenceId;
  private Long reach;
  private String status;
  private Long impressions;
}
