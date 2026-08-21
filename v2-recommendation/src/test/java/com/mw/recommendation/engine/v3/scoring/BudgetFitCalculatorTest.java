package com.mw.recommendation.engine.v3.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import com.mw.recommendation.engine.v3.pipeline.V3RunContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BudgetFitCalculatorTest {

  private final V3Properties props = new V3Properties();
  private final BudgetFitCalculator calculator = new BudgetFitCalculator(props);

  // 30-day inclusive window so a monthly price prorates to exactly itself
  private static final LocalDate START = LocalDate.of(2026, 8, 1);
  private static final LocalDate END = LocalDate.of(2026, 8, 30);

  private static Inventory monthlyPriced(double monthly) {
    Inventory inventory = new Inventory();
    inventory.setInventoryId("inv-1");
    inventory.setReferenceId("REF-001");
    inventory.setPrices(
        List.of(Inventory.PriceModel.builder().monthly(monthly).currency("USD").build()));
    return inventory;
  }

  private static V3RunContext context(BigDecimal budget) {
    RecommendationV3RequestDTO request =
        RecommendationV3RequestDTO.builder()
            .country("Malaysia")
            .startDate(START)
            .endDate(END)
            .budget(budget)
            .build();
    return new V3RunContext("run-1", "camp-1", request, "seed-1");
  }

  @Test
  void givenPrdExampleCost4500AgainstBudget30000_whenCalculate_then95() {
    // PRD §5.6: proportion 4500/30000 = 0.15 ≤ 0.2 → 95
    BudgetFitCalculator.Result result =
        calculator.calculate(monthlyPriced(4500.0), context(BigDecimal.valueOf(30_000)));

    assertThat(result.estimate().cost()).isEqualByComparingTo(BigDecimal.valueOf(4500.00));
    assertThat(result.score()).isEqualTo(95.0);
  }

  @Test
  void givenProportionBucketEdges_whenCalculate_thenPrdBucketScores() {
    BigDecimal budget = BigDecimal.valueOf(10_000);

    // proportion 0.5 → 75
    assertThat(calculator.calculate(monthlyPriced(5_000.0), context(budget)).score())
        .isEqualTo(75.0);
    // proportion 0.8 → 50
    assertThat(calculator.calculate(monthlyPriced(8_000.0), context(budget)).score())
        .isEqualTo(50.0);
    // proportion 1.2 → 30
    assertThat(calculator.calculate(monthlyPriced(12_000.0), context(budget)).score())
        .isEqualTo(30.0);
    // proportion > 1.2 → 10
    assertThat(calculator.calculate(monthlyPriced(12_500.0), context(budget)).score())
        .isEqualTo(10.0);
  }

  @Test
  void givenNoBudget_whenCalculate_thenNeutral50() {
    BudgetFitCalculator.Result result = calculator.calculate(monthlyPriced(4500.0), context(null));

    assertThat(result.score()).isEqualTo(50.0);
  }

  @Test
  void givenSovGoalWithBothSpotAndCpm_whenEstimateCost_thenSpotPricingPreferred() {
    Inventory inventory = new Inventory();
    inventory.setInventoryId("inv-1");
    inventory.setReferenceId("REF-001");
    inventory.setPrices(
        List.of(Inventory.PriceModel.builder().spot(2.0).cpm(5.0).currency("USD").build()));
    inventory.setDigitalFields(
        Inventory.DigitalFields.builder().loopDuration(10).spotsPerLoop(1).build());

    RecommendationV3RequestDTO request =
        RecommendationV3RequestDTO.builder()
            .country("Malaysia")
            .startDate(START)
            .endDate(END)
            .budget(BigDecimal.valueOf(1_000_000))
            .goal(RecommendationV3RequestDTO.CampaignGoal.SOV)
            .goalValue(10L)
            .build();
    V3RunContext ctx = new V3RunContext("run-1", "camp-1", request, "seed-1");
    // Impressions available too — CPM would be viable, but SOV must prefer spot pricing
    ctx.setMeasureByReferenceId(
        Map.of("REF-001", new V3RunContext.MeasureData(500_000L, 100_000L, "test")));

    BudgetFitCalculator.CostEstimate estimate = calculator.estimateCost(inventory, ctx);

    assertThat(estimate.costUnit()).isEqualTo("CPS");
    assertThat(estimate.cost()).isNotNull();
  }
}
