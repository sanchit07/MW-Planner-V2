package com.mw.recommendation.engine.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Model to hold component scores and final score for an inventory */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryScore {
  private Double measureFit; // 0-100 or null
  private Double geoFit; // 0-100
  private Double availability; // 0-100
  private Double budgetFit; // 0-100
  private Double audienceFit; // 0-100
  private Double brandFit; // 0-100 (50 if brand not provided)
  private Double qualityFit; // 0-100
  private Double timeFit; // 0-100
  private Double finalScore; // 0-100 (weighted sum of components)
  private String explanation; // Human-readable explanation
}
