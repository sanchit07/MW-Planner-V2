package com.mw.recommendation.engine.util;

import lombok.Getter;

/** Default weights for scoring components. Weights are redistributed when measure_fit is null. */
@Getter
public class ScoringWeights {

  // Default weights
  public static final double MEASURE_FIT_WEIGHT = 0.20;
  public static final double GEO_FIT_WEIGHT = 0.20;
  public static final double AVAILABILITY_WEIGHT = 0.10;
  public static final double BUDGET_FIT_WEIGHT = 0.20;
  public static final double AUDIENCE_FIT_WEIGHT = 0.10;
  public static final double BRAND_FIT_WEIGHT = 0.10;
  public static final double QUALITY_FIT_WEIGHT = 0.06;
  public static final double TIME_FIT_WEIGHT = 0.04;

  /**
   * Get redistributed weights when measure_fit is null. Redistributes 20% weight proportionally: -
   * geo_fit: +12% (20% → 32%) - quality_fit: +8% (6% → 14%) - audience_fit: +6% (10% → 16%) -
   * brand_fit: +4% (10% → 14%)
   */
  public static class RedistributedWeights {
    public static final double GEO_FIT_WEIGHT = 0.32;
    public static final double AVAILABILITY_WEIGHT = 0.10;
    public static final double BUDGET_FIT_WEIGHT = 0.20;
    public static final double AUDIENCE_FIT_WEIGHT = 0.16;
    public static final double BRAND_FIT_WEIGHT = 0.14;
    public static final double QUALITY_FIT_WEIGHT = 0.14;
    public static final double TIME_FIT_WEIGHT = 0.04;
  }
}
