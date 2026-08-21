package com.mw.recommendation.engine.util;

/** Utility class for normalizing values to 0-100 range using min-max normalization */
public class NormalizationUtils {

  /**
   * Normalize a value using min-max normalization
   *
   * @param value Raw value
   * @param min Minimum bound
   * @param max Maximum bound
   * @return Normalized value (0-100)
   */
  public static Double normalize(Double value, Double min, Double max) {
    if (value == null || min == null || max == null) {
      return null;
    }
    if (max.equals(min)) {
      return 50.0; // Neutral score if no range
    }
    double normalized = ((value - min) / (max - min)) * 100;
    return Math.max(0.0, Math.min(100.0, normalized)); // Clamp to 0-100
  }
}
