package com.mw.recommendation.engine.v3.variation;

import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.pipeline.ScoredInventoryV3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Ranking variation per PRD §5.12 / §6.7 / H.3:
 *
 * <ul>
 *   <li>Deterministic multiplicative jitter (±7.5% default), seeded PER INVENTORY from (runSeed +
 *       inventoryId) — unlike v1's single uniform offset, this actually varies the relative ranking
 *       between runs while staying fully reproducible for a given seed (AC-10).
 *   <li>Top-1 stability (H.3): the highest raw-scoring inventory always keeps position 1.
 *   <li>{@code disableVariation} bypasses jitter entirely for deterministic consumers.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class VariationV3Service {

  private final V3Properties props;

  /** Applies jitter + sorts descending + restores top-1, then applies the topN limit. */
  public List<ScoredInventoryV3> applyAndRank(
      List<ScoredInventoryV3> scored, String seed, boolean disableVariation, Integer topN) {

    List<ScoredInventoryV3> ranked = new ArrayList<>(scored.size());
    if (disableVariation) {
      ranked.addAll(scored);
    } else {
      for (ScoredInventoryV3 item : scored) {
        ranked.add(item.withJitteredScore(jitter(item, seed)));
      }
    }
    ranked.sort(Comparator.comparingDouble(ScoredInventoryV3::finalScoreWithJitter).reversed());

    if (!disableVariation && !ranked.isEmpty()) {
      restoreTopOne(scored, ranked);
    }

    if (topN != null && topN > 0 && ranked.size() > topN) {
      return new ArrayList<>(ranked.subList(0, topN));
    }
    return ranked;
  }

  private double jitter(ScoredInventoryV3 item, String seed) {
    double range = props.getVariation().getJitterRange();
    long itemSeed = (seed + "|" + item.inventory().getInventoryId()).hashCode();
    double offset = (new Random(itemSeed).nextDouble() - 0.5) * (2 * range);
    double jittered = item.score().getFinalScore() * (1 + offset);
    return Math.max(0.0, Math.min(100.0, jittered));
  }

  /** H.3: the best raw scorer is always ranked first; variation applies to positions 2-N only. */
  private static void restoreTopOne(
      List<ScoredInventoryV3> original, List<ScoredInventoryV3> ranked) {
    ScoredInventoryV3 rawTop =
        original.stream()
            .max(Comparator.comparingDouble(s -> s.score().getFinalScore()))
            .orElse(null);
    if (rawTop == null) {
      return;
    }
    String topId = rawTop.inventory().getInventoryId();
    int index = -1;
    for (int i = 0; i < ranked.size(); i++) {
      if (ranked.get(i).inventory().getInventoryId().equals(topId)) {
        index = i;
        break;
      }
    }
    if (index > 0) {
      ScoredInventoryV3 top = ranked.remove(index);
      // Keep its jittered score at least as high as the current leader so ordering stays sorted
      double leaderScore = ranked.get(0).finalScoreWithJitter();
      ranked.add(0, top.withJitteredScore(Math.max(top.finalScoreWithJitter(), leaderScore)));
    }
  }

  /** Premium / High / Good / Acceptable band label (H.3 / premium-mid-filler mix). */
  public String band(double finalScore) {
    V3Properties.Variation cfg = props.getVariation();
    if (finalScore >= cfg.getBandPremium()) {
      return "PREMIUM";
    }
    if (finalScore >= cfg.getBandHigh()) {
      return "HIGH";
    }
    if (finalScore >= cfg.getBandGood()) {
      return "GOOD";
    }
    if (finalScore >= cfg.getBandAcceptable()) {
      return "ACCEPTABLE";
    }
    return "FILLER";
  }
}
