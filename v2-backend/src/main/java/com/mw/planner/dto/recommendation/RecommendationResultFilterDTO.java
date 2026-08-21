package com.mw.planner.dto.recommendation;

import com.mw.planner.enums.ProgrammaticDealType;
import com.mw.planner.enums.ProgrammaticSupport;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResultFilterDTO {

  private List<String> inventoryIds;
  private List<String> referenceIds;
  private List<String> classifications;
  private List<String> types;
  private List<String> formats;
  private List<String> environments;
  private List<String> venueTypes;
  private List<String> orientations;
  private List<String> sizes;
  private List<String> mediaOwnerIds;
  private List<String> mediaOwnerNames;
  private List<String> countryIds;
  private List<String> countryNames;
  private List<String> stateIds;
  private List<String> stateNames;
  private List<String> cityIds;
  private List<String> cityNames;
  private Double minFinalScore;
  private Double maxFinalScore;
  private Double minMeasureFit;
  private Double maxMeasureFit;
  private Double minGeoFit;
  private Double maxGeoFit;
  private Double minAvailability;
  private Double maxAvailability;
  private Double minBudgetFit;
  private Double maxBudgetFit;
  private Double minAudienceFit;
  private Double maxAudienceFit;
  private Double minBrandFit;
  private Double maxBrandFit;
  private Double minQualityFit;
  private Double maxQualityFit;
  private Double minTimeFit;
  private Double maxTimeFit;
  private Long minEstimatedImpressions;
  private Long maxEstimatedImpressions;
  private Long minEstimatedReach;
  private Long maxEstimatedReach;
  private Double minEstimatedFrequency;
  private Double maxEstimatedFrequency;
  private BigDecimal minEstimatedCost;
  private BigDecimal maxEstimatedCost;
  private List<String> currencies;
  private Double minAvailabilityPercentage;
  private Double maxAvailabilityPercentage;
  private Boolean allAvailable;

  /** Programmatic filters (forwarded to recommendation engine) */
  private ProgrammaticSupport programmaticSupport;

  private List<ProgrammaticDealType> dealTypes;
}
