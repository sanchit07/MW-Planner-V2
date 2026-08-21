package com.mw.recommendation.engine.v3.explain;

import com.mw.recommendation.engine.v3.pipeline.ScoredInventoryV3;
import com.mw.recommendation.engine.v3.pipeline.V3RunContext;
import org.springframework.stereotype.Component;

/**
 * Per-recommendation confidence % (PRD §2 "a confidence percentage"): a data-completeness measure,
 * NOT a quality score — 50% component coverage, 30% forecast provenance (Measure beats derived),
 * 20% availability confirmation. Deterministic and explainable.
 */
@Component
public class ConfidenceCalculator {

  public double calculate(ScoredInventoryV3 item, V3RunContext.MeasureData measure) {
    double completeness = item.score().getDataCompleteness();
    double provenance = measure == null ? 0.0 : "measure".equals(measure.source()) ? 1.0 : 0.5;
    double availabilityConfidence =
        item.availability() != null && !item.availability().unconfirmed() ? 1.0 : 0.5;

    double confidence =
        100.0 * (0.5 * completeness + 0.3 * provenance + 0.2 * availabilityConfidence);
    return Math.round(confidence * 10.0) / 10.0;
  }
}
