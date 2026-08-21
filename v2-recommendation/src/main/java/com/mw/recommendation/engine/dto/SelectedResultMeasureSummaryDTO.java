package com.mw.recommendation.engine.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight projection of a selected inventory (selectionMode AUTO or MANUAL) exposing only
 * inventoryId, referenceId, cost, and forecast. Returned by the selected-results measure-summary
 * endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SelectedResultMeasureSummaryDTO {
  private String inventoryId;
  private String referenceId;
  private PaginatedRecommendationResponseDTO.CostEstimate cost;
  private PaginatedRecommendationResponseDTO.ForecastedMetrics forecast;
}
