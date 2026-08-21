package com.mw.recommendation.engine.util;

import com.mw.recommendation.engine.domain.Inventory;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Classic (OOH / rate-card) inventory cost calculation.
 *
 * <p>Classic is billed by whole 30-day months at the {@code monthly} rate, with the leftover days
 * charged at the {@code daily} rate. When no daily rate is present, the leftover days fall back to
 * a pro-rated {@code monthly / 30}.
 *
 * <pre>
 *   fullMonths    = days / 30            (integer division)
 *   remainderDays = days - fullMonths*30
 *   dailyRate     = daily (if &gt; 0) else monthly / 30
 *   estimatedCost = fullMonths*monthly + remainderDays*dailyRate
 * </pre>
 *
 * <p>Example (PO scenario): 69 days, monthly 100000, no daily -&gt; {@code 2*100000 + 9*(100000/30)
 * = 230000}.
 *
 * <p>Shared by the browse and scored recommendation paths so both agree with the adserver FE. See
 * adserver {@code docs/v3/CLASSIC-PRICING-SPEC-2026-07-18.md} §5.
 */
public final class ClassicPricing {

  private static final BigDecimal DAYS_PER_MONTH = BigDecimal.valueOf(30);

  private ClassicPricing() {}

  /**
   * Estimated cost (scaled to 2dp) for a monthly/daily rate-card inventory over an inclusive {@code
   * days} window, or {@code null} when neither a monthly nor a daily rate is present.
   */
  public static BigDecimal estimatedCost(Inventory.PriceModel price, long days) {
    if (price == null || days <= 0) {
      return null;
    }
    Double monthly = price.getMonthly();
    Double daily = price.getDaily();
    boolean hasMonthly = monthly != null && monthly > 0;
    boolean hasDaily = daily != null && daily > 0;
    if (!hasMonthly && !hasDaily) {
      return null;
    }
    if (hasMonthly) {
      long fullMonths = days / 30;
      long remainderDays = days - (fullMonths * 30);
      BigDecimal monthlyRate = BigDecimal.valueOf(monthly);
      BigDecimal dailyRate =
          hasDaily
              ? BigDecimal.valueOf(daily)
              : monthlyRate.divide(DAYS_PER_MONTH, 6, RoundingMode.HALF_UP);
      return monthlyRate
          .multiply(BigDecimal.valueOf(fullMonths))
          .add(dailyRate.multiply(BigDecimal.valueOf(remainderDays)))
          .setScale(2, RoundingMode.HALF_UP);
    }
    // daily only
    return BigDecimal.valueOf(daily)
        .multiply(BigDecimal.valueOf(days))
        .setScale(2, RoundingMode.HALF_UP);
  }
}
