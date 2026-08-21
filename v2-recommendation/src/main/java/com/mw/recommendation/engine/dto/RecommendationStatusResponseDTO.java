package com.mw.recommendation.engine.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for recommendation status (returned when submitting a request) */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationStatusResponseDTO {
  private String runId;
  private RunStatus status;
  private Integer completionPercentage; // 0-100
  private String campaignId;
  private String productId;
  private String companyId;
  private LocalDateTime generatedAt;
  private RecommendationMetadata metadata;
  private List<String> warnings;

  /**
   * Why auto-selection produced the selection it did (see {@code AutoSelectionReasonCode}); most
   * useful when zero inventories were auto-selected. Nullable: absent while IN_PROGRESS, for runs
   * completed before this field existed, and for BROWSE-mode runs.
   */
  private String autoSelectionReasonCode;

  /** Human-readable elaboration of {@link #autoSelectionReasonCode}. Nullable. */
  private String autoSelectionReasonDetail;

  public enum RunStatus {
    IN_PROGRESS,
    COMPLETED,
    // Terminal failure state for the v2 pipeline. v1 runs never reach this value.
    FAILED
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

    /**
     * Auto-selected inventory IDs (selectionMode = AUTO). Present when status is COMPLETED. Planner
     * uses this to fetch schedules via GET /schedules/runs/{runId}.
     */
    private List<String> autoSelectedInventoryIds;
  }
}
