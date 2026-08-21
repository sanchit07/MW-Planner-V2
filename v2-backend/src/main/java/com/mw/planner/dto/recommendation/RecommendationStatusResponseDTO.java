package com.mw.planner.dto.recommendation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationStatusResponseDTO {

  private String runId;
  private RunStatus status;
  private Integer completionPercentage;
  private String campaignId;
  private String productId;
  private String companyId;
  private LocalDateTime generatedAt;
  private RecommendationMetadata metadata;
  private List<String> warnings;
  private String autoSelectionReasonCode;
  private String autoSelectionReasonDetail;

  public enum RunStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RecommendationMetadata {
    private Integer totalInventoriesEvaluated;
    private Integer totalInventoriesRecommended;
    private Integer totalInventoriesExcluded;
    private Map<String, Integer> exclusionReasons;
    private Double averageScore;
    private String seed;
    private List<String> autoSelectedInventoryIds;
  }
}
