package com.mw.recommendation.engine.v3.scoring;

import com.mw.recommendation.engine.v3.config.V3Properties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Combines the eight component scores into finalScore per PRD §5.11. When measureFit is null its
 * weight is redistributed to geo/quality/audience/brand in the PRD 12:8:6:4 ratio, scaled so the
 * effective weights sum to exactly 1.0 — the v1 implementation adds the PRD example's absolute
 * increments (built for a 30% measure weight) onto a 20% table, producing weights that sum to 1.10
 * and inflating no-measure scores by ~10%.
 */
@Component
@RequiredArgsConstructor
public class WeightingEngine {

  private final V3Properties props;

  public V3Score combine(
      Double measureFit,
      Double geoFit,
      Double availability,
      Double budgetFit,
      Double audienceFit,
      Double brandFit,
      Double qualityFit,
      Double timeFit,
      boolean auditEnabled) {

    V3Properties.Scoring cfg = props.getScoring();
    Map<String, Double> weights = effectiveWeights(measureFit == null, cfg);

    Map<String, Double> components = new LinkedHashMap<>();
    components.put("measureFit", measureFit);
    components.put("geoFit", geoFit);
    components.put("availability", availability);
    components.put("budgetFit", budgetFit);
    components.put("audienceFit", audienceFit);
    components.put("brandFit", brandFit);
    components.put("qualityFit", qualityFit);
    components.put("timeFit", timeFit);

    double finalScore = 0.0;
    Map<String, V3Score.AuditEntry> audit = auditEnabled ? new LinkedHashMap<>() : Map.of();
    List<Map.Entry<String, Double>> weightedContributions = new ArrayList<>();

    for (Map.Entry<String, Double> entry : components.entrySet()) {
      double weight = weights.getOrDefault(entry.getKey(), 0.0);
      double value = entry.getValue() != null ? entry.getValue() : 0.0;
      double weighted = weight * value;
      finalScore += weighted;
      weightedContributions.add(Map.entry(entry.getKey(), weighted));
      if (auditEnabled) {
        audit.put(
            entry.getKey(),
            new V3Score.AuditEntry(entry.getValue(), entry.getValue(), weight, weighted));
      }
    }

    List<String> topSignals =
        weightedContributions.stream()
            .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
            .limit(3)
            .map(Map.Entry::getKey)
            .toList();

    long presentComponents =
        components.values().stream().filter(java.util.Objects::nonNull).count();

    return V3Score.builder()
        .measureFit(measureFit)
        .geoFit(geoFit)
        .availability(availability)
        .budgetFit(budgetFit)
        .audienceFit(audienceFit)
        .brandFit(brandFit)
        .qualityFit(qualityFit)
        .timeFit(timeFit)
        .finalScore(Math.max(0.0, Math.min(100.0, finalScore)))
        .audit(audit)
        .topSignals(topSignals)
        .dataCompleteness(presentComponents / (double) components.size())
        .build();
  }

  /** Effective weight per component; redistributed weights always sum to 1.0. */
  public Map<String, Double> effectiveWeights(boolean measureMissing, V3Properties.Scoring cfg) {
    Map<String, Double> weights = new LinkedHashMap<>();
    if (!measureMissing) {
      weights.put("measureFit", cfg.getMeasureFitWeight());
      weights.put("geoFit", cfg.getGeoFitWeight());
      weights.put("availability", cfg.getAvailabilityWeight());
      weights.put("budgetFit", cfg.getBudgetFitWeight());
      weights.put("audienceFit", cfg.getAudienceFitWeight());
      weights.put("brandFit", cfg.getBrandFitWeight());
      weights.put("qualityFit", cfg.getQualityFitWeight());
      weights.put("timeFit", cfg.getTimeFitWeight());
      return weights;
    }

    // Scale the PRD 12:8:6:4 shares so exactly measureFitWeight is redistributed
    double shareTotal =
        cfg.getRedistributionGeoShare()
            + cfg.getRedistributionQualityShare()
            + cfg.getRedistributionAudienceShare()
            + cfg.getRedistributionBrandShare();
    double pool = cfg.getMeasureFitWeight();

    weights.put("measureFit", 0.0);
    weights.put(
        "geoFit", cfg.getGeoFitWeight() + pool * cfg.getRedistributionGeoShare() / shareTotal);
    weights.put("availability", cfg.getAvailabilityWeight());
    weights.put("budgetFit", cfg.getBudgetFitWeight());
    weights.put(
        "audienceFit",
        cfg.getAudienceFitWeight() + pool * cfg.getRedistributionAudienceShare() / shareTotal);
    weights.put(
        "brandFit",
        cfg.getBrandFitWeight() + pool * cfg.getRedistributionBrandShare() / shareTotal);
    weights.put(
        "qualityFit",
        cfg.getQualityFitWeight() + pool * cfg.getRedistributionQualityShare() / shareTotal);
    weights.put("timeFit", cfg.getTimeFitWeight());
    return weights;
  }
}
