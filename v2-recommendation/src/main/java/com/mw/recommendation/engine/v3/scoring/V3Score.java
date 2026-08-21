package com.mw.recommendation.engine.v3.scoring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Full scoring output for one inventory: the eight PRD components (null = data missing, which
 * triggers weight redistribution for measureFit), the weighted final score, the raw→normalized→
 * weighted audit trail (PRD §5.2), and the strongest signals for "why" generation.
 */
@Data
@Builder
public class V3Score {

  private Double measureFit;
  private Double geoFit;
  private Double availability;
  private Double budgetFit;
  private Double audienceFit;
  private Double brandFit;
  private Double qualityFit;
  private Double timeFit;

  /** Weighted sum, 0-100, before jitter. */
  private double finalScore;

  /** component → audit entry; populated only when v3 audit is enabled. */
  @Builder.Default private Map<String, AuditEntry> audit = new LinkedHashMap<>();

  /** Top contributing signal names, strongest first (feeds WhyGenerator). */
  @Builder.Default private List<String> topSignals = List.of();

  /** Share of components that had real (non-fallback) data — feeds ConfidenceCalculator. */
  private double dataCompleteness;

  public record AuditEntry(Double raw, Double normalized, Double weight, Double weighted) {}
}
