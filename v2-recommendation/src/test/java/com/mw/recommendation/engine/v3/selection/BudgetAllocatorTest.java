package com.mw.recommendation.engine.v3.selection;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BudgetAllocatorTest {

  private final BudgetAllocator allocator = new BudgetAllocator(new V3Properties());

  private static Inventory inventoryOf(String classification, String type) {
    Inventory inventory = new Inventory();
    inventory.setClassification(classification);
    inventory.setType(type);
    return inventory;
  }

  private static RecommendationV3RequestDTO.RecommendationV3RequestDTOBuilder baseRequest() {
    return RecommendationV3RequestDTO.builder()
        .country("Malaysia")
        .startDate(LocalDate.of(2026, 8, 1))
        .endDate(LocalDate.of(2026, 8, 31));
  }

  @Test
  void givenEachInventoryKind_whenBucketOf_thenAllSevenBucketsAreLive() {
    // All 7 buckets resolvable — v1 has retail/network/audio/experiential dead
    assertThat(allocator.bucketOf(inventoryOf("Digital", "Transit"))).isEqualTo("transit");
    assertThat(allocator.bucketOf(inventoryOf("Digital", "Retail"))).isEqualTo("retail");
    assertThat(allocator.bucketOf(inventoryOf("Digital", "Network"))).isEqualTo("network");
    assertThat(allocator.bucketOf(inventoryOf("Digital", "Radio"))).isEqualTo("audio");
    assertThat(allocator.bucketOf(inventoryOf("Digital", "Experiential")))
        .isEqualTo("experiential");
    assertThat(allocator.bucketOf(inventoryOf("Classic", "OOH"))).isEqualTo("classic");
    assertThat(allocator.bucketOf(inventoryOf("Digital", "OOH"))).isEqualTo("digital");
  }

  @Test
  void givenImpressionsGoal_whenEffectiveAllocation_thenH2TableValues() {
    RecommendationV3RequestDTO request =
        baseRequest().goal(RecommendationV3RequestDTO.CampaignGoal.IMPRESSIONS).build();

    Map<String, Double> allocation = allocator.effectiveAllocation(request);

    assertThat(allocation)
        .containsEntry("digital", 35d)
        .containsEntry("classic", 25d)
        .containsEntry("transit", 20d)
        .containsEntry("retail", 10d)
        .containsEntry("network", 7d)
        .containsEntry("audio", 0d)
        .containsEntry("experiential", 3d);
  }

  @Test
  void givenNoGoal_whenEffectiveAllocation_thenNoneTableUsed() {
    RecommendationV3RequestDTO request = baseRequest().build();

    Map<String, Double> allocation = allocator.effectiveAllocation(request);

    assertThat(allocation)
        .containsEntry("digital", 35d)
        .containsEntry("classic", 25d)
        .containsEntry("transit", 20d)
        .containsEntry("retail", 10d)
        .containsEntry("network", 7d)
        .containsEntry("audio", 0d)
        .containsEntry("experiential", 3d);
  }

  @Test
  void givenCustomBudgetAllocation_whenEffectiveAllocation_thenOverridesGoalDefaults() {
    RecommendationV3RequestDTO request =
        baseRequest()
            .goal(RecommendationV3RequestDTO.CampaignGoal.IMPRESSIONS)
            .budgetAllocation(Map.of("Digital", 60d, "Classic", 40d))
            .build();

    Map<String, Double> allocation = allocator.effectiveAllocation(request);

    assertThat(allocation).hasSize(2);
    assertThat(allocation).containsEntry("digital", 60d).containsEntry("classic", 40d);
  }

  @Test
  void given100kBudgetWithImpressionsTable_whenBucketBudgets_thenDigitalCap35000() {
    RecommendationV3RequestDTO request =
        baseRequest().goal(RecommendationV3RequestDTO.CampaignGoal.IMPRESSIONS).build();

    Map<String, BigDecimal> budgets =
        allocator.bucketBudgets(
            BigDecimal.valueOf(100_000), allocator.effectiveAllocation(request));

    assertThat(budgets.get("digital")).isEqualByComparingTo(new BigDecimal("35000.00"));
  }
}
