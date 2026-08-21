package com.mw.recommendation.engine.v3.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import com.mw.recommendation.engine.v3.config.V3Properties;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeightingEngineTest {

  private final V3Properties props = new V3Properties();
  private final WeightingEngine engine = new WeightingEngine(props);

  @Test
  void givenDefaultConfig_whenEffectiveWeightsWithMeasure_thenSumIsExactlyOne() {
    Map<String, Double> weights = engine.effectiveWeights(false, props.getScoring());

    double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
    assertThat(sum).isCloseTo(1.0, offset(1e-9));
  }

  @Test
  void givenMeasureMissing_whenEffectiveWeights_thenRedistributedSumIsExactlyOne() {
    // Flagship fix vs v1: v1's redistribution produced weights summing to 1.10
    Map<String, Double> weights = engine.effectiveWeights(true, props.getScoring());

    double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
    assertThat(sum).isCloseTo(1.0, offset(1e-9));
  }

  @Test
  void givenMeasureMissing_whenEffectiveWeights_thenPoolSplitProportionalToPrdShares() {
    // 0.20 pool split 12:8:6:4 across geo/quality/audience/brand
    Map<String, Double> weights = engine.effectiveWeights(true, props.getScoring());

    assertThat(weights.get("measureFit")).isEqualTo(0.0);
    assertThat(weights.get("geoFit")).isCloseTo(0.20 + 0.08, offset(1e-9));
    assertThat(weights.get("qualityFit")).isCloseTo(0.06 + 0.0533, offset(1e-3));
    assertThat(weights.get("audienceFit")).isCloseTo(0.10 + 0.04, offset(1e-9));
    assertThat(weights.get("brandFit")).isCloseTo(0.10 + 0.0267, offset(1e-3));
    // Untouched components keep their defaults
    assertThat(weights.get("availability")).isEqualTo(0.10);
    assertThat(weights.get("budgetFit")).isEqualTo(0.20);
    assertThat(weights.get("timeFit")).isEqualTo(0.04);
  }

  @Test
  void givenPrdExampleComponents_whenCombine_thenFinalScoreMatchesWorkedExample() {
    // PRD §5.11: .20*70 + .20*90 + .10*100 + .20*95 + .10*80 + .10*85 + .06*70 + .04*60
    //          = 14 + 18 + 10 + 19 + 8 + 8.5 + 4.2 + 2.4 = 84.1
    V3Score score = engine.combine(70.0, 90.0, 100.0, 95.0, 80.0, 85.0, 70.0, 60.0, false);

    assertThat(score.getFinalScore()).isCloseTo(84.1, offset(0.01));
  }

  @Test
  void givenAuditEnabled_whenCombine_thenAuditPopulatedForAllComponents() {
    V3Score score = engine.combine(70.0, 90.0, 100.0, 95.0, 80.0, 85.0, 70.0, 60.0, true);

    assertThat(score.getAudit()).hasSize(8);
    assertThat(score.getAudit().get("budgetFit").weighted()).isCloseTo(19.0, offset(1e-9));
    assertThat(score.getAudit().get("geoFit").weight()).isEqualTo(0.20);
  }

  @Test
  void givenAuditDisabled_whenCombine_thenAuditEmpty() {
    V3Score score = engine.combine(70.0, 90.0, 100.0, 95.0, 80.0, 85.0, 70.0, 60.0, false);

    assertThat(score.getAudit()).isEmpty();
  }

  @Test
  void givenCombined_whenTopSignals_thenAtMostThreeSortedByWeightedContribution() {
    // weighted: budgetFit 19, geoFit 18, measureFit 14, availability 10, ...
    V3Score score = engine.combine(70.0, 90.0, 100.0, 95.0, 80.0, 85.0, 70.0, 60.0, true);

    assertThat(score.getTopSignals()).hasSize(3);
    assertThat(score.getTopSignals()).containsExactly("budgetFit", "geoFit", "measureFit");
  }
}
