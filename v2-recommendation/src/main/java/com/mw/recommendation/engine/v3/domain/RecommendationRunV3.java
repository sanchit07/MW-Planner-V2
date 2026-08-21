package com.mw.recommendation.engine.v3.domain;

import com.mw.recommendation.engine.domain.BaseEntity;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import java.time.LocalDateTime;
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
 * v3 run document. Lives in its own collection so the v3 pipeline never touches v1/v2 data. Extends
 * the v1 lifecycle with FAILED and NO_MATCHES terminal states (PRD §14), a persisted seed,
 * structured error code, alternatives, city clusters and stage timings.
 */
@Document(collection = "recommendation_run_v3")
@CompoundIndex(name = "v3_campaign_request_idx", def = "{'campaignId': 1, 'requestHash': 1}")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class RecommendationRunV3 extends BaseEntity<String> {

  @Indexed(unique = true)
  private String runId;

  @Indexed private String campaignId;
  private String productId;
  private String companyId;

  @Indexed private String requestHash;

  private RecommendationV3RequestDTO request;

  private RunStatus status;
  private Integer completionPercentage;
  private LocalDateTime generatedAt;
  private LocalDateTime completedAt;

  /** Seed used for jitter/variation — auditable per PRD §5.12/AC-11. */
  private String seed;

  private Integer engineVersion;

  private Metadata metadata;
  private List<String> warnings;
  private List<String> autoSelectedInventoryIds;

  /** Structured error code for FAILED / NO_MATCHES (PRD §14.6). */
  private String errorCode;

  private String errorMessage;

  /** Nearest alternatives persisted on NO_MATCHES (PRD §6.6). */
  private List<Alternative> alternatives;

  /** City-cluster summary for country-scale runs (PRD AC-12). */
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
  public static class Metadata {
    private Integer totalInventoriesEvaluated;
    private Integer totalInventoriesRecommended;

    /** True count of inventories dropped by pipeline stages (not filter-input sizes). */
    private Integer totalInventoriesExcluded;

    private Map<String, Integer> exclusionReasons;
    private Double averageScore;
    private Map<String, Long> stageTimingsMs;
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
