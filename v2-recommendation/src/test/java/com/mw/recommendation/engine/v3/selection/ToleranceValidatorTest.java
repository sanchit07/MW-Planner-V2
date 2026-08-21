package com.mw.recommendation.engine.v3.selection;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.support.WarningCollector;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ToleranceValidatorTest {

  private final ToleranceValidator validator = new ToleranceValidator(new V3Properties());

  @Test
  void givenTier2BudgetFullySpent_whenValidate_thenWithinToleranceAndNoWarning() {
    WarningCollector warnings = new WarningCollector();

    ToleranceValidator.Verdict verdict =
        validator.validate(BigDecimal.valueOf(50_000), "USD", BigDecimal.valueOf(50_000), warnings);

    assertThat(verdict.tolerancePct()).isEqualTo(7.0); // 50k USD → tier 2 (±7%)
    assertThat(verdict.spendPct()).isEqualTo(100.0);
    assertThat(verdict.withinTolerance()).isTrue();
    assertThat(warnings.warnings()).isEmpty();
  }

  @Test
  void givenSpendAt80Percent_whenValidate_thenUnderToleranceWarningEmitted() {
    WarningCollector warnings = new WarningCollector();

    ToleranceValidator.Verdict verdict =
        validator.validate(BigDecimal.valueOf(50_000), "USD", BigDecimal.valueOf(40_000), warnings);

    assertThat(verdict.spendPct()).isEqualTo(80.0);
    assertThat(verdict.withinTolerance()).isFalse();
    assertThat(warnings.warnings())
        .anySatisfy(w -> assertThat(w).contains("could not be allocated"));
  }

  @Test
  void givenBudgetSizes_whenValidate_thenTierToleranceSelectedByUsdEquivalent() {
    WarningCollector warnings = new WarningCollector();

    // 5,000 USD → tier 1 (±10%)
    assertThat(
            validator
                .validate(BigDecimal.valueOf(5_000), "USD", BigDecimal.valueOf(5_000), warnings)
                .tolerancePct())
        .isEqualTo(10.0);
    // 200,000 USD → tier 3 (±5%)
    assertThat(
            validator
                .validate(BigDecimal.valueOf(200_000), "USD", BigDecimal.valueOf(200_000), warnings)
                .tolerancePct())
        .isEqualTo(5.0);
    // 1,000,000 USD → enterprise tier (±3%)
    assertThat(
            validator
                .validate(
                    BigDecimal.valueOf(1_000_000), "USD", BigDecimal.valueOf(1_000_000), warnings)
                .tolerancePct())
        .isEqualTo(3.0);
  }

  @Test
  void givenMyrBudget_whenValidate_thenTierChosenOnUsdConversion() {
    // 50,000 MYR × 0.22 = 11,000 USD → above tier 1 cutoff, lands in tier 2 (±7%)
    WarningCollector warnings = new WarningCollector();

    ToleranceValidator.Verdict verdict =
        validator.validate(BigDecimal.valueOf(50_000), "MYR", BigDecimal.valueOf(50_000), warnings);

    assertThat(verdict.tolerancePct()).isEqualTo(7.0);
  }
}
