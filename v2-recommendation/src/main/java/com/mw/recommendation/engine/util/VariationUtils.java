package com.mw.recommendation.engine.util;

import java.util.Random;

/** Utility class for applying deterministic variation (jitter) to scores */
public class VariationUtils {

  private static final double JITTER_RANGE = 0.075; // ±7.5%

  /**
   * Apply deterministic jitter to final score
   *
   * @param score Original score
   * @param runId Run ID for seed generation
   * @return Jittered score
   */
  public static Double applyJitter(Double score, String runId) {
    if (score == null || runId == null) {
      return score;
    }

    // Generate seed from runId
    long seed = runId.hashCode();
    Random random = new Random(seed);

    // Jitter range: ±7.5%
    double jitter = (random.nextDouble() - 0.5) * (2 * JITTER_RANGE); // -0.075 to +0.075
    double jitteredScore = score * (1 + jitter);

    // Ensure score stays within 0-100 range
    return Math.max(0.0, Math.min(100.0, jitteredScore));
  }
}
