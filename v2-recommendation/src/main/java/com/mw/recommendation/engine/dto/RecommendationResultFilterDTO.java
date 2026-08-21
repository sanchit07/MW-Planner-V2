package com.mw.recommendation.engine.dto;

import com.mw.recommendation.engine.domain.SelectionMode;
import com.mw.recommendation.engine.enums.ProgrammaticDealType;
import com.mw.recommendation.engine.enums.ProgrammaticSupport;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Filter DTO for recommendation results. Filters are applied on inventory properties stored in
 * RecommendationResult.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationResultFilterDTO {

  // Inventory identifiers
  private List<String> inventoryIds;
  private List<String> referenceIds;

  // Inventory classification
  private List<String> classifications; // "Digital", "Classic", "Transit"
  private List<String> types; // "OOH", "Audio", etc.
  private List<String> formats; // "LED Billboard", etc.
  private List<String> environments; // "outdoor", "indoor"
  private List<String> venueTypes; // ["Outdoor", "Billboards", "Roadside"]
  private List<String> orientations; // "landscape", "portrait"
  private List<String> sizes; // "XS", "S", "M", "L", "XL"
  private List<String> resolutions; // ["920x1200"] - from panelspixelWidth x pixelHeight
  private List<Integer> durations;

  // Media owner
  private List<String> mediaOwnerIds;
  private List<String> mediaOwnerNames;

  // Location
  private List<String> countryIds;
  private List<String> countryNames;
  private List<String> stateIds;
  private List<String> stateNames;
  private List<String> cityIds;
  private List<String> cityNames;

  // Scoring filters (range filters)
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

  // Forecast filters
  private Long minEstimatedImpressions;
  private Long maxEstimatedImpressions;
  private Long minEstimatedReach;
  private Long maxEstimatedReach;
  private Double minEstimatedFrequency;
  private Double maxEstimatedFrequency;

  // Cost filters
  private java.math.BigDecimal minEstimatedCost;
  private java.math.BigDecimal maxEstimatedCost;
  private List<String> currencies;

  // Availability filters
  private Double minAvailabilityPercentage;
  private Double maxAvailabilityPercentage;
  private Boolean allAvailable; // Filter by allAvailable flag

  // Selection mode filter
  private List<SelectionMode> selectionModes; // AUTO, MANUAL

  // Booking mode filter (on inventoryDetails.bookingMode)
  private List<String> bookingMode; // "loop", "spot"

  // Programmatic filters (on inventoryDetails.programmaticDealTypes)
  private ProgrammaticSupport programmaticSupport;
  private List<ProgrammaticDealType> dealTypes;
}
