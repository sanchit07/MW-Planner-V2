package com.mw.planner.dto.mobility;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One heatmap point: coordinates plus a normalized 0..1 footfall weight. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobilityPointDTO {
  private double lat;
  private double lng;
  private double weight;
}
