package com.mw.recommendation.engine.domain;

import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "recommendation_runs")
@CompoundIndex(name = "campaign_request_idx", def = "{'campaignId': 1, 'requestHash': 1}")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class RecommendationRun extends BaseEntity<String> {

  @Indexed(unique = true)
  private String runId; // Unique ID for this recommendation run

  @Indexed private String campaignId; // Campaign ID

  @Indexed private String productId; // Product ID

  @Indexed private String companyId; // Company ID

  @Indexed private String requestHash; // Hash of request payload for duplicate detection

  private RecommendationRequestDTO request; // Original request payload

  private RunStatus status; // IN_PROGRESS, COMPLETED, FAILED

  /** Structured error code set when status = FAILED (v2 pipeline only). */
  private String errorCode;

  /** Human-readable error message set when status = FAILED (v2 pipeline only). */
  private String errorMessage;

  /**
   * Pipeline that created this run ("v2" for the v2 pipeline; null for v1). Lets v2-only
   * maintenance (e.g. the stale-run watchdog) target its own runs without ever touching v1 runs.
   */
  private String pipelineVersion;

  private Integer completionPercentage; // 0-100

  private LocalDateTime generatedAt; // When the run was created

  private LocalDateTime completedAt; // When the run completed (null if IN_PROGRESS)

  private RecommendationMetadata metadata; // Metadata from the recommendation

  private List<String> warnings; // Warnings from the recommendation

  /** Inventory IDs that were auto-selected (selectionMode = AUTO). For quick lookup. */
  private List<String> autoSelectedInventoryIds;

  /**
   * Why auto-selection produced the selection it did (most useful when zero were selected). Derived
   * read-only after selection returns; null for runs completed before this field existed and for
   * BROWSE-mode runs (no auto-selection).
   */
  private AutoSelectionReasonCode autoSelectionReasonCode;

  /** Human-readable elaboration of {@link #autoSelectionReasonCode}. Nullable. */
  private String autoSelectionReasonDetail;

  /** Observability snapshot of the auto-selection inputs/outputs. Nullable. */
  private AutoSelectionDiagnostics autoSelectionDiagnostics;

  public enum RunStatus {
    IN_PROGRESS,
    COMPLETED,
    // Added for the v2 pipeline so an errored/stale run reaches a terminal state instead of hanging
    // IN_PROGRESS forever. v1 never sets this value, so v1 behavior is unchanged.
    FAILED
  }

  /**
   * Read-only diagnostics captured when auto-selection completes. All values are derived from data
   * already available at the completion write (candidate results, request, pipeline-level Measure
   * batch response, selected ids) — never from selection internals. All fields nullable.
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class AutoSelectionDiagnostics {
    /** Number of scored results handed to auto-selection (post topN / result build). */
    private Integer candidateCount;

    /** Candidates above the minimum recommendation score threshold. */
    private Integer scoredCount;

    /** Requested total budget (null when none). */
    private java.math.BigDecimal budget;

    /**
     * Minimum {@code cost.estimatedCost} across qualified candidates. Proxy only: selection
     * compares Measure-derived schedule basePrice, which can differ.
     */
    private java.math.BigDecimal cheapestEstimatedCost;

    /** Requested goal type (null when none). */
    private String goalType;

    /** Requested goal value (null when none). */
    private Long goalValue;

    /**
     * Sum of the goal metric (estimatedImpressions or estimatedReach from the pipeline-level
     * Measure batch) across qualified candidates. Null for SOV/AD_PLAYS (not cheaply available).
     */
    private Long achievableMetricTotal;

    /** Whether the pipeline-level Measure batch call was attempted (URL configured, sites > 0). */
    private Boolean measureApiInvoked;

    /**
     * Best-effort: whether the pipeline-level Measure call returned any rows. The client returns an
     * empty list on both failure and a genuinely empty success, so false may mean either.
     */
    private Boolean measureApiSucceeded;

    /** Number of inventories sent to the pipeline-level Measure batch call. */
    private Integer sitesRequested;

    /** Measure rows usable by selection: status=success with positive impressions AND reach. */
    private Integer sitesWithReachFrequency;

    /** Number of inventories auto-selected. */
    private Integer selectedCount;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class RecommendationMetadata {
    private Integer totalInventoriesEvaluated;
    private Integer totalInventoriesRecommended;
    private Integer totalInventoriesExcluded;
    private Map<String, Integer> exclusionReasons;
    private Double averageScore;
    private String seed;
  }
}
