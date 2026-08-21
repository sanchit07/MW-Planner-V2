package com.mw.recommendation.engine.v3.domain;

import com.mw.recommendation.engine.domain.BaseEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One scored v3 recommendation. selectionMode is stamped before insert (single write per doc — the
 * v2 optimization); the score audit subdocument is written only when v3 audit is enabled.
 */
@Document(collection = "recommendation_result_v3")
@CompoundIndex(name = "v3_run_score_idx", def = "{'runId': 1, 'finalScore': -1}")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class RecommendationResultV3 extends BaseEntity<String> {

  @Indexed private String runId;

  private String inventoryId;
  private String referenceId;
  private String name;

  /** Final weighted score after jitter (0-100). Components are stored un-jittered. */
  private Double finalScore;

  /** Premium / High / Good / Acceptable band label. */
  private String band;

  private ComponentScores componentScores;

  /** component → {raw, normalized, weight, weighted} audit trail (PRD §5.2). */
  private Map<String, ScoreAudit> scoreAudit;

  private String why;
  private Double confidence;

  private AvailabilitySummary availability;
  private Forecast forecast;
  private Cost cost;

  /** AUTO | MANUAL | null. */
  private String selectionMode;

  private InventoryDetails inventoryDetails;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ComponentScores {
    private Double measureFit;
    private Double geoFit;
    private Double availability;
    private Double budgetFit;
    private Double audienceFit;
    private Double brandFit;
    private Double qualityFit;
    private Double timeFit;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ScoreAudit {
    private Double raw;
    private Double normalized;
    private Double weight;
    private Double weighted;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AvailabilitySummary {
    private Integer availableDays;
    private Integer totalDays;
    private Double availabilityPercentage;
    private String summary;
    private Boolean allAvailable;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Forecast {
    private Long estimatedImpressions;
    private Long estimatedReach;
    private Double estimatedSov;
    private Double estimatedFrequency;

    /** "measure" | "derived" (PRD AC-03 provenance). */
    private String source;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Cost {
    private BigDecimal estimatedCost;
    private String currency;

    /** "CPM" | "CPS" | "FLAT". */
    private String costUnit;

    private Double costPerImpression;
    private Long totalAdPlays;
    private Boolean prorated;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InventoryDetails {
    private String classification;
    private String type;
    private String format;
    private String city;
    private String state;
    private String address;
    private String mediaOwnerName;
    private List<String> venueTypes;
    private Double latitude;
    private Double longitude;
    private String size;
    private List<String> inventoryCluster;
  }
}
