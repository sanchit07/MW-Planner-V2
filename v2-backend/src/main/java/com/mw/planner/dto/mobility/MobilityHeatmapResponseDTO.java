package com.mw.planner.dto.mobility;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Server-side aggregated mobility heatmap for a country (optionally one time bucket / bbox). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobilityHeatmapResponseDTO {
  private String countryId;

  /** Echo of the requested bucket, or "ALL" when aggregated across buckets. */
  private String timeBucket;

  /** Buckets that actually have data for this country (drives the UI filter). */
  private List<String> availableTimeBuckets;

  private int totalPoints;
  private List<MobilityPointDTO> points;
}
