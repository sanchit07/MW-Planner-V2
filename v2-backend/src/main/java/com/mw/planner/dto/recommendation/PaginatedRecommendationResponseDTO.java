package com.mw.planner.dto.recommendation;

import com.mw.planner.dto.InventoryResponseDTO;
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
public class PaginatedRecommendationResponseDTO {

  private String runId;
  private String campaignId;
  private String productId;
  private String companyId;
  private List<RecommendedInventory> recommendations;
  private PaginationInfo pagination;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RecommendedInventory {
    private String inventoryId;
    private String referenceId;
    private String name;
    private Double finalScore;
    private ComponentScores componentScores;
    private String why;
    private AvailabilitySummary availability;
    private ForecastedMetrics forecast;
    private CostEstimate cost;
    private InventoryDetails inventoryDetails;
    private Boolean isExcluded;
    private String selectionMode;
    private Performance performance;
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
  public static class AvailabilitySummary {
    private Integer availableDays;
    private Integer totalDays;
    private Double availabilityPercentage;
    private String summary;
    private Boolean allAvailable;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ForecastedMetrics {
    private Long estimatedImpressions;
    private Long estimatedReach;
    private Double estimatedSov;
    private Double estimatedFrequency;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CostEstimate {
    private BigDecimal estimatedCost;
    private String currency;
    private Double costPerImpression;
    private Long totalAdPlays;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InventoryDetails {
    private String internalId;
    private String name;
    private String classification;
    private String type;
    private String format;
    private String environment;
    private List<String> venueTypes;
    private String orientation;
    private List<String> sizes;
    private String mediaOwnerId;
    private String mediaOwnerName;
    private String address;
    private InventoryLocation location;
    private Double cpmRate;
    private Double spotRate;
    private String thumbnailUrl;
    private List<ExternalRefId> externalRefIds;
    private InventoryResponseDTO.DigitalFieldsDTO digitalFields;
    private InventoryResponseDTO.SellingTermDTO sellingTerm;
    private String size;
    private List<String> inventoryCluster;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InventoryLocation {
    private String countryId;
    private String countryName;
    private String stateId;
    private String stateName;
    private String cityId;
    private String cityName;
    private Object locationCoordinates;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PaginationInfo {
    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
    private Boolean hasNext;
    private Boolean hasPrevious;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Performance {
    private Long estimatedImpressions;
    private Long estimatedReach;
    private Double estimatedFrequency;
    private Double totalSot;
    private Double plannedSot;
    private Double sov;
    private Long totalAdPlays;
    private Long perDayAdPlays;
    private Double estimatedCost;
    private Double perDayCost;
    private Double cpmRate;
    private Double spotRate;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ExternalRefId {
    private String source;
    private String externalRefId;
  }
}
