package com.mw.recommendation.engine.v3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Status response for a v3 run. Extends the v1 contract with FAILED / NO_MATCHES statuses,
 * nearest-alternative suggestions (PRD §6.6/AC-08), city clusters for country-scale runs (AC-12), a
 * structured error code (PRD §14.6), and always-populated warnings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class V3StatusResponseDTO {

  private String runId;
  private RunStatus status;
  private Integer completionPercentage;
  private String campaignId;
  private String productId;
  private String companyId;
  private LocalDateTime generatedAt;
  private LocalDateTime completedAt;
  private String seed;
  private Metadata metadata;
  private List<String> warnings;

  /** Structured error code when status = FAILED or NO_MATCHES (PRD §14.6). */
  private String errorCode;

  private String errorMessage;

  /** Nearest alternatives when status = NO_MATCHES (PRD §6.6). */
  private List<Alternative> alternatives;

  /** City-cluster summary for large country-scale runs (PRD AC-12). */
  private List<CityCluster> cityClusters;

  public enum RunStatus {
    IN_PROGRESS,
    COMPLETED,
    NO_MATCHES,
    FAILED
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Metadata {
    private Integer totalInventoriesEvaluated;
    private Integer totalInventoriesRecommended;
    private Integer totalInventoriesExcluded;
    private Map<String, Integer> exclusionReasons;
    private Double averageScore;
    private List<String> autoSelectedInventoryIds;

    /** Per-stage durations in milliseconds, for observability. */
    private Map<String, Long> stageTimingsMs;

    private Integer engineVersion;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Alternative {
    private String inventoryId;
    private String referenceId;
    private String name;
    private String city;
    private Double distanceKm;
    private Long estimatedDailyImpressions;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CityCluster {
    private String city;
    private Integer inventoryCount;
    private Double averageScore;
    private Double topScore;
  }
}
