package com.mw.recommendation.engine.v3.variation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.pipeline.ScoredInventoryV3;
import com.mw.recommendation.engine.v3.scoring.V3Score;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariationV3ServiceTest {

  private final VariationV3Service service = new VariationV3Service(new V3Properties());

  private static ScoredInventoryV3 scored(String inventoryId, double finalScore) {
    Inventory inventory = new Inventory();
    inventory.setInventoryId(inventoryId);
    V3Score score = V3Score.builder().finalScore(finalScore).build();
    return new ScoredInventoryV3(inventory, score, null, null, finalScore);
  }

  /** 20 items with tightly clustered scores: 80.0, 79.9, 79.8, ... 78.1. */
  private static List<ScoredInventoryV3> closeScores() {
    List<ScoredInventoryV3> items = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      items.add(scored("inv-" + i, 80.0 - i * 0.1));
    }
    return items;
  }

  private static List<String> ids(List<ScoredInventoryV3> ranked) {
    return ranked.stream().map(s -> s.inventory().getInventoryId()).toList();
  }

  @Test
  void givenSameSeedTwice_whenApplyAndRank_thenIdenticalOrderAndScores() {
    // AC-10: full reproducibility for a given seed
    List<ScoredInventoryV3> first = service.applyAndRank(closeScores(), "seed-A", false, null);
    List<ScoredInventoryV3> second = service.applyAndRank(closeScores(), "seed-A", false, null);

    assertThat(ids(first)).isEqualTo(ids(second));
    for (int i = 0; i < first.size(); i++) {
      assertThat(first.get(i).finalScoreWithJitter())
          .isEqualTo(second.get(i).finalScoreWithJitter());
    }
  }

  @Test
  void givenDifferentSeeds_whenApplyAndRank_thenRankingDiffers() {
    List<ScoredInventoryV3> rankedA = service.applyAndRank(closeScores(), "seed-A", false, null);
    List<ScoredInventoryV3> rankedB = service.applyAndRank(closeScores(), "seed-B", false, null);

    assertThat(ids(rankedA)).isNotEqualTo(ids(rankedB));
  }

  @Test
  void givenDisableVariation_whenApplyAndRank_thenRawScoreOrderAndUnjitteredScores() {
    List<ScoredInventoryV3> input = closeScores();
    List<ScoredInventoryV3> expectedOrder = new ArrayList<>(input);
    expectedOrder.sort(
        Comparator.comparingDouble((ScoredInventoryV3 s) -> s.score().getFinalScore()).reversed());

    List<ScoredInventoryV3> ranked = service.applyAndRank(input, "seed-A", true, null);

    assertThat(ids(ranked)).isEqualTo(ids(expectedOrder));
    for (ScoredInventoryV3 item : ranked) {
      assertThat(item.finalScoreWithJitter()).isEqualTo(item.score().getFinalScore());
    }
  }

  @Test
  void givenAnySeed_whenApplyAndRank_thenTopRawScorerAlwaysFirst() {
    // H.3: top-1 stability — variation only reshuffles positions 2..N
    for (int i = 0; i < 10; i++) {
      List<ScoredInventoryV3> ranked =
          service.applyAndRank(closeScores(), "seed-" + i, false, null);
      assertThat(ranked.get(0).inventory().getInventoryId())
          .as("seed-%d must keep the raw top scorer first", i)
          .isEqualTo("inv-0");
    }
  }

  @Test
  void givenBandThresholds_whenBand_thenPrdLabels() {
    assertThat(service.band(95.0)).isEqualTo("PREMIUM");
    assertThat(service.band(85.0)).isEqualTo("HIGH");
    assertThat(service.band(75.0)).isEqualTo("GOOD");
    assertThat(service.band(65.0)).isEqualTo("ACCEPTABLE");
    assertThat(service.band(50.0)).isEqualTo("FILLER");
  }

  @Test
  void givenTopN_whenApplyAndRank_thenResultLimited() {
    List<ScoredInventoryV3> ranked = service.applyAndRank(closeScores(), "seed-A", false, 5);

    assertThat(ranked).hasSize(5);
  }
}
