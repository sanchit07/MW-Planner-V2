package com.mw.recommendation.engine.v3.scoring;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.config.V3Properties;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * qualityFit per PRD §5.9: weighted sub-factors — size 30%, pitch 25%, visibility 25%, owner
 * quality 20% (weights configurable). A missing sub-factor contributes a neutral 50 so weights
 * always sum consistently. A stored ownerQuality index feeds the 20% factor; when the whole metrics
 * block is absent every factor is neutral → 50.
 */
@Component
@RequiredArgsConstructor
public class QualityFitCalculator {

  private final V3Properties props;

  public Double calculate(Inventory inventory) {
    double sizeScore = sizeScore(inventory);
    double pitchScore = pitchScore(inventory);
    double visibilityScore = visibilityScore(inventory);
    double ownerScore = ownerScore(inventory);

    double wSize = props.getScoring().getQualitySizeWeight();
    double wPitch = props.getScoring().getQualityPitchWeight();
    double wVisibility = props.getScoring().getQualityVisibilityWeight();
    double wOwner = props.getScoring().getQualityOwnerWeight();
    double totalWeight = wSize + wPitch + wVisibility + wOwner;

    double score =
        (sizeScore * wSize
                + pitchScore * wPitch
                + visibilityScore * wVisibility
                + ownerScore * wOwner)
            / totalWeight;
    return Math.max(0.0, Math.min(100.0, score));
  }

  /** Panel size class → score (XS 20 … XL 100); falls back to screenSize sqft, else neutral. */
  private static double sizeScore(Inventory inventory) {
    if (inventory.getPanels() != null && !inventory.getPanels().isEmpty()) {
      Inventory.Size size = inventory.getPanels().get(0).getSize();
      if (size != null) {
        return switch (size) {
          case XS -> 20.0;
          case S -> 40.0;
          case M -> 60.0;
          case L -> 80.0;
          case XL -> 100.0;
        };
      }
    }
    Inventory.QualityMetrics metrics = inventory.getQualityMetrics();
    if (metrics != null && metrics.getScreenSize() != null) {
      double sqft = metrics.getScreenSize();
      if (sqft >= 300) return 100.0;
      if (sqft >= 100) return 80.0;
      if (sqft >= 30) return 60.0;
      if (sqft >= 5) return 40.0;
      return 20.0;
    }
    return 50.0;
  }

  /** LED pitch: lower = sharper. ≤6mm → 100, ≤10 → 75, ≤16 → 50, else 30; N/A → neutral. */
  private static double pitchScore(Inventory inventory) {
    Inventory.QualityMetrics metrics = inventory.getQualityMetrics();
    if (metrics == null || metrics.getPitch() == null || metrics.getPitch() <= 0) {
      return 50.0;
    }
    int pitch = metrics.getPitch();
    if (pitch <= 6) return 100.0;
    if (pitch <= 10) return 75.0;
    if (pitch <= 16) return 50.0;
    return 30.0;
  }

  private static double visibilityScore(Inventory inventory) {
    Inventory.QualityMetrics metrics = inventory.getQualityMetrics();
    if (metrics == null || metrics.getVisibility() == null) {
      return 50.0;
    }
    double base =
        switch (metrics.getVisibility().toLowerCase(Locale.ROOT)) {
          case "high" -> 100.0;
          case "medium" -> 60.0;
          case "low" -> 20.0;
          default -> 50.0;
        };
    if (Boolean.TRUE.equals(metrics.getHasObstructions())) {
      base = Math.max(0.0, base - 20.0);
    }
    return base;
  }

  private static double ownerScore(Inventory inventory) {
    Inventory.QualityMetrics metrics = inventory.getQualityMetrics();
    if (metrics != null && metrics.getQualityScore() != null) {
      return Math.max(0.0, Math.min(100.0, metrics.getQualityScore()));
    }
    return 50.0;
  }
}
