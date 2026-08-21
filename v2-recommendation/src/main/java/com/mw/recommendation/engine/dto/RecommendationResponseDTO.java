package com.mw.recommendation.engine.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for recommendations */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationResponseDTO {
  private String campaignId;
  private String runId; // Unique ID for this recommendation run
  private LocalDateTime generatedAt;
  private List<RecommendedInventory> recommendations;
  private RecommendationMetadata metadata;
  private List<String> warnings; // e.g., "Some inventories excluded due to missing data"

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class RecommendedInventory {
    private String inventoryId;
    private String referenceId;
    private String name;
    private Double finalScore; // 0-100
    private ComponentScores componentScores;
    private String why; // Explanation text
    private AvailabilitySummary availability;
    private ForecastedMetrics forecast;
    private CostEstimate cost;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class ComponentScores {
    private Double measureFit; // 0-100
    private Double geoFit; // 0-100
    private Double availability; // 0-100
    private Double budgetFit; // 0-100
    private Double audienceFit; // 0-100
    private Double brandFit; // 0-100 (50 if brand not provided)
    private Double qualityFit; // 0-100
    private Double timeFit; // 0-100
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class AvailabilitySummary {
    private Integer availableDays;
    private Integer totalDays;
    private Double availabilityPercentage;
    private String summary; // e.g., "6/10 days available"
    private Boolean allAvailable; // From availability API response
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class ForecastedMetrics {
    private Long estimatedImpressions;
    private Long estimatedReach;
    private Double estimatedSov; // Share of Voice
    private Double estimatedFrequency;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class CostEstimate {
    private BigDecimal estimatedCost;
    private String currency;
    private Double costPerImpression; // If impressions available
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class RecommendationMetadata {
    private Integer totalInventoriesEvaluated;
    private Integer totalInventoriesRecommended;
    private Integer totalInventoriesExcluded;
    private Map<String, Integer> exclusionReasons; // Reason -> count
    private Double averageScore;
    private String seed; // For deterministic variation
  }
}
