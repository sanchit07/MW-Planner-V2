package com.mw.recommendation.engine.v3.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.config.V3Properties;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualityFitCalculatorTest {

  private final V3Properties props = new V3Properties();
  private final QualityFitCalculator calculator = new QualityFitCalculator(props);

  @Test
  void givenOnlyOwnerQualityScore87_whenCalculate_thenWeightedWithNeutralSubFactors() {
    // NOTE: qualityScore is NOT a passthrough. It only feeds the 20% owner-quality sub-factor;
    // size/pitch/visibility fall back to neutral 50 each, so the result is
    // (50×30 + 50×25 + 50×25 + 87×20) / 100 = 57.4 — not 87.
    Inventory inventory = new Inventory();
    inventory.setQualityMetrics(Inventory.QualityMetrics.builder().qualityScore(87.0).build());

    Double score = calculator.calculate(inventory);

    assertThat(score).isCloseTo(57.4, offset(0.01));
  }

  @Test
  void givenNoMetricsAndNoPanels_whenCalculate_thenNeutral50() {
    Double score = calculator.calculate(new Inventory());

    assertThat(score).isEqualTo(50.0);
  }

  @Test
  void givenXlPanelAndHighVisibility_whenCalculate_thenAbove50AndAtMost100() {
    Inventory inventory = new Inventory();
    inventory.setPanels(List.of(Inventory.Panel.builder().size(Inventory.Size.XL).build()));
    inventory.setQualityMetrics(Inventory.QualityMetrics.builder().visibility("high").build());

    Double score = calculator.calculate(inventory);

    // size 100×30 + pitch 50×25 + visibility 100×25 + owner 50×20 → 77.5
    assertThat(score).isGreaterThan(50.0).isLessThanOrEqualTo(100.0);
    assertThat(score).isCloseTo(77.5, offset(0.01));
  }
}
