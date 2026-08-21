package com.mw.recommendation.engine.v3.selection;

import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.support.WarningCollector;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Budget tolerance per PRD Part F: tiered ±10/7/5/3% by USD-equivalent budget size (static FX table
 * until a live source exists). The v3 allocator never spends over budget, so the over-budget branch
 * is a hard invariant check; the under-budget branch emits the PRD's "Budget allocation is X%…"
 * message when spend falls short by more than the tier tolerance.
 */
@Component
@RequiredArgsConstructor
public class ToleranceValidator {

  private final V3Properties props;

  public record Verdict(double tolerancePct, double spendPct, boolean withinTolerance) {}

  public Verdict validate(
      BigDecimal budget, String currency, BigDecimal totalSpend, WarningCollector warnings) {

    double usdRate =
        props
            .getTolerance()
            .getUsdRates()
            .getOrDefault(
                currency != null
                    ? currency.toUpperCase()
                    : props.getTolerance().getDefaultCurrency(),
                1.0);
    double budgetUsd = budget.doubleValue() * usdRate;

    double tolerancePct = props.getTolerance().getTiers().get(0)[1];
    for (double[] tier : props.getTolerance().getTiers()) {
      if (budgetUsd <= tier[0]) {
        tolerancePct = tier[1];
        break;
      }
    }

    double spendPct =
        budget.signum() > 0
            ? totalSpend
                .multiply(BigDecimal.valueOf(100))
                .divide(budget, 1, RoundingMode.HALF_UP)
                .doubleValue()
            : 0.0;

    boolean within = spendPct >= 100.0 - tolerancePct && spendPct <= 100.0 + tolerancePct;
    if (spendPct < 100.0 - tolerancePct) {
      BigDecimal leftover = budget.subtract(totalSpend);
      warnings.warn(
          String.format(
              "Budget allocation is %.1f%%. Remaining %.0f%% (%s %s) could not be allocated"
                  + " efficiently",
              spendPct, 100.0 - spendPct, currency != null ? currency : "", leftover));
    }
    return new Verdict(tolerancePct, spendPct, within);
  }
}
