package com.mw.recommendation.engine.v3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Paginated v3 results. Each recommendation carries the PRD output contract: component scores,
 * signal-referencing "why" (AC-14), confidence %, premium/mid/filler band, availability summary
 * (AC-06), forecast with its data source (AC-03), and cost with costUnit + proration flag.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class V3ResultsResponseDTO {

  private String runId;
  private String campaignId;
  private String productId;
  private String companyId;
  private List<RecommendedInventory> recommendations;
  private List<String> warnings;
  private Pagination pagination;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class RecommendedInventory {
    private String inventoryId;
    private String referenceId;
    private String name;
    private Double finalScore;

    /** Premium / High / Good / Acceptable band label (PRD §2 "mix", H.3 bands). */
    private String band;

    private ComponentScores componentScores;

    /** Raw → normalized → weighted audit per component (PRD §5.2), when audit is enabled. */
    private Map<String, ScoreAuditEntry> scoreAudit;

    private String why;

    /** 0-100 data-completeness confidence (PRD §2). */
    private Double confidence;

    private AvailabilitySummary availability;
    private Forecast forecast;
    private Cost cost;
    private String selectionMode;
    private InventoryDetails inventoryDetails;
  }

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
  public static class ScoreAuditEntry {
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

    /** e.g. "6/10 days available" (PRD §5.5). */
    private String summary;

    private Boolean allAvailable;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Forecast {
    private Long estimatedImpressions;
    private Long estimatedReach;
    private Double estimatedSov;
    private Double estimatedFrequency;

    /** "measure" | "derived" — provenance flag (PRD AC-03). */
    private String source;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Cost {
    private BigDecimal estimatedCost;
    private String currency;

    /** "CPM" | "CPS" | "FLAT" (pending costUnit from the technical doc §3.5). */
    private String costUnit;

    private Double costPerImpression;
    private Long totalAdPlays;

    /** True when the cost reflects partial availability proration (PRD AC-06). */
    private Boolean prorated;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
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

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Pagination {
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
  }
}
