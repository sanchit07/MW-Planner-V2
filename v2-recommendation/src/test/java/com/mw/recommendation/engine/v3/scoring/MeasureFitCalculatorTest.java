package com.mw.recommendation.engine.v3.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.mock;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import com.mw.recommendation.engine.v3.pipeline.V3RunContext;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MeasureFitCalculatorTest {

  private final V3Properties props = new V3Properties();
  private final TotalAdPlaysProvider totalAdPlaysProvider = mock(TotalAdPlaysProvider.class);
  private final MeasureFitCalculator calculator =
      new MeasureFitCalculator(props, totalAdPlaysProvider);

  private static Inventory inventory() {
    Inventory inventory = new Inventory();
    inventory.setInventoryId("inv-1");
    inventory.setReferenceId("REF-001");
    return inventory;
  }

  private static V3RunContext context(RecommendationV3RequestDTO request) {
    return new V3RunContext("run-1", "camp-1", request, "seed-1");
  }

  private static RecommendationV3RequestDTO.RecommendationV3RequestDTOBuilder baseRequest() {
    return RecommendationV3RequestDTO.builder()
        .country("Malaysia")
        .startDate(LocalDate.of(2026, 8, 1))
        .endDate(LocalDate.of(2026, 8, 10)); // 10-day window
  }

  private static void putMeasure(
      V3RunContext ctx, String referenceId, Long impressions, Long reach) {
    ctx.setMeasureByReferenceId(
        Map.of(referenceId, new V3RunContext.MeasureData(impressions, reach, "test")));
  }

  @Test
  void givenImpressionsGoal_whenMeasureDelivers12Percent_thenScoreIs12() {
    // PRD §5.3 worked example: 120,000 impressions vs 1,000,000 goal → 12.0
    RecommendationV3RequestDTO request =
        baseRequest()
            .goal(RecommendationV3RequestDTO.CampaignGoal.IMPRESSIONS)
            .goalValue(1_000_000L)
            .build();
    V3RunContext ctx = context(request);
    Inventory inventory = inventory();
    putMeasure(ctx, "REF-001", 120_000L, 60_000L);

    Double score = calculator.calculate(inventory, ctx);

    assertThat(score).isCloseTo(12.0, offset(0.01));
  }

  @Test
  void givenImpressionsAboveGoal_whenCalculate_thenCappedAt100() {
    RecommendationV3RequestDTO request =
        baseRequest()
            .goal(RecommendationV3RequestDTO.CampaignGoal.IMPRESSIONS)
            .goalValue(1_000_000L)
            .build();
    V3RunContext ctx = context(request);
    Inventory inventory = inventory();
    putMeasure(ctx, "REF-001", 5_000_000L, 100_000L);

    Double score = calculator.calculate(inventory, ctx);

    assertThat(score).isEqualTo(100.0);
  }

  @Test
  void givenGoalButNoMeasureData_whenCalculate_thenNullTriggersRedistribution() {
    RecommendationV3RequestDTO request =
        baseRequest()
            .goal(RecommendationV3RequestDTO.CampaignGoal.IMPRESSIONS)
            .goalValue(1_000_000L)
            .build();
    V3RunContext ctx = context(request); // no measure map entries

    Double score = calculator.calculate(inventory(), ctx);

    assertThat(score).isNull();
  }

  @Test
  void givenNoGoalAndMeasurePresent_whenCalculate_thenNormalizedProxyWithinBounds() {
    RecommendationV3RequestDTO request = baseRequest().build();
    V3RunContext ctx = context(request);
    Inventory inventory = inventory();
    // 3,000,000 over 10 days → 300,000/day → (300000-1000)/999000 ≈ 0.2993 → 29.93
    putMeasure(ctx, "REF-001", 3_000_000L, 500_000L);

    Double score = calculator.calculate(inventory, ctx);

    assertThat(score).isNotNull();
    assertThat(score).isBetween(0.0, 100.0);
    assertThat(score).isCloseTo(29.93, offset(0.05));
  }

  @Test
  void givenCarbonGoal_whenCalculate_thenNull() {
    RecommendationV3RequestDTO request =
        baseRequest().goal(RecommendationV3RequestDTO.CampaignGoal.CARBON).goalValue(100L).build();
    V3RunContext ctx = context(request);
    putMeasure(ctx, "REF-001", 120_000L, 60_000L);

    Double score = calculator.calculate(inventory(), ctx);

    assertThat(score).isNull();
  }

  @Test
  void givenAdPlaysGoal_whenDigitalLoopMathApplies_thenScoreIsMinRatioTimes100() {
    // loopDuration=10s → 360 loops/hour, spotsPerLoop=1, no operatingTimes → digital default
    // 18h/day
    // 10-day window → adPlays = 360 × 1 × 18 × 10 = 64,800; goal 100,000 → 64.8
    RecommendationV3RequestDTO request =
        baseRequest()
            .goal(RecommendationV3RequestDTO.CampaignGoal.AD_PLAYS)
            .goalValue(100_000L)
            .build();
    V3RunContext ctx = context(request);
    Inventory inventory = inventory();
    inventory.setDigitalFields(
        Inventory.DigitalFields.builder().loopDuration(10).spotsPerLoop(1).build());

    Double score = calculator.calculate(inventory, ctx);

    long expectedAdPlays = 360L * 1 * 18 * 10;
    assertThat(expectedAdPlays).isEqualTo(64_800L);
    assertThat(score).isCloseTo(Math.min(expectedAdPlays / 100_000.0, 1.0) * 100.0, offset(0.01));
    assertThat(score).isCloseTo(64.8, offset(0.01));
  }
}
