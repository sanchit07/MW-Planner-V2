package com.mw.recommendation.engine.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mw.recommendation.engine.domain.Inventory;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ClassicPricingTest {

  private static Inventory.PriceModel price(Double monthly, Double daily) {
    return Inventory.PriceModel.builder().monthly(monthly).daily(daily).build();
  }

  @Test
  void estimatedCost_whenMonthlyOnlyOverPartialMonth_prorratesRemainderAtMonthlyOver30() {
    // PO scenario: 69 days @ 100000/month, no daily -> 2*100000 + 9*(100000/30) = 230000.
    BigDecimal cost = ClassicPricing.estimatedCost(price(100000.0, null), 69);
    assertEquals(new BigDecimal("230000.00"), cost);
  }

  @Test
  void estimatedCost_whenExactWholeMonths_chargesOnlyMonthly() {
    // 60 days = exactly 2 months, zero remainder days.
    BigDecimal cost = ClassicPricing.estimatedCost(price(100000.0, null), 60);
    assertEquals(new BigDecimal("200000.00"), cost);
  }

  @Test
  void estimatedCost_whenLessThanOneMonth_chargesRemainderOnly() {
    // 10 days @ 100000/month, no daily -> 0*100000 + 10*(100000/30) = 33333.33
    BigDecimal cost = ClassicPricing.estimatedCost(price(100000.0, null), 10);
    assertEquals(new BigDecimal("33333.33"), cost);
  }

  @Test
  void estimatedCost_whenMonthlyAndDaily_usesDailyForRemainderDays() {
    // 35 days -> 1 month @ 100000 + 5 remainder days @ 2000 daily = 110000.
    BigDecimal cost = ClassicPricing.estimatedCost(price(100000.0, 2000.0), 35);
    assertEquals(new BigDecimal("110000.00"), cost);
  }

  @Test
  void estimatedCost_whenDailyOnly_chargesEveryDayAtDailyRate() {
    // 35 days @ 2000 daily, no monthly -> 70000 (does NOT roll up into months).
    BigDecimal cost = ClassicPricing.estimatedCost(price(null, 2000.0), 35);
    assertEquals(new BigDecimal("70000.00"), cost);
  }

  @Test
  void estimatedCost_whenNeitherMonthlyNorDaily_returnsNull() {
    assertNull(ClassicPricing.estimatedCost(price(null, null), 30));
  }

  @Test
  void estimatedCost_whenRatesAreZeroOrNegative_treatedAsAbsent() {
    assertNull(ClassicPricing.estimatedCost(price(0.0, 0.0), 30));
    assertNull(ClassicPricing.estimatedCost(price(-100.0, null), 30));
  }

  @Test
  void estimatedCost_whenPriceIsNull_returnsNull() {
    assertNull(ClassicPricing.estimatedCost(null, 30));
  }

  @Test
  void estimatedCost_whenDaysNotPositive_returnsNull() {
    assertNull(ClassicPricing.estimatedCost(price(100000.0, null), 0));
    assertNull(ClassicPricing.estimatedCost(price(100000.0, null), -5));
  }
}
