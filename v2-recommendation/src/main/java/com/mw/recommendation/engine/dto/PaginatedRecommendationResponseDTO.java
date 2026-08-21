package com.mw.recommendation.engine.dto;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.domain.Inventory.SellingTerm;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for paginated recommendation results */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaginatedRecommendationResponseDTO {
  private String runId;
  private String campaignId;
  private String productId;
  private String companyId;
  private List<RecommendedInventory> recommendations;
  private PaginationInfo pagination;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
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
    private InventoryDetails inventoryDetails; // Grouped inventory details
    private Boolean
        isExcluded; // Whether this inventory was excluded during initial recommendation generation

    /**
     * MANUAL = user selected via selected-inventories, AUTO = system auto-selected, null = not
     * selected
     */
    private String selectionMode;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
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
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class AvailabilitySummary {
    private Integer availableDays;
    private Integer totalDays;
    private Double availabilityPercentage;
    private String summary;
    private Boolean allAvailable;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class ForecastedMetrics {
    private Long estimatedImpressions;
    private Long estimatedReach;
    private Double estimatedSov;
    private Double estimatedFrequency;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class CostEstimate {
    private BigDecimal estimatedCost;
    private String currency;
    private Double costPerImpression;
    private Long totalAdPlays;
    // Raw rate-card rates (CLASSIC/OOH) so the client can recompute cost as flight days change.
    private Double monthly;
    private Double daily;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class InventoryDetails {
    private String name;
    private String classification; // "Digital", "Classic", "Transit"
    private String type; // "OOH", "Audio", etc.
    private String format; // "LED Billboard", etc.
    private String environment; // "outdoor", "indoor"
    private List<String> venueTypes; // ["Outdoor", "Billboards", "Roadside"]
    private String orientation; // "landscape", "portrait"
    private List<String> sizes; // ["XS", "S", "M", "L", "XL"]
    private List<String> resolutions; // ["1920x1080", "1080x1920"] - WxH from panels
    private List<Integer> durations; // [10, 15, 30] - from prices.durationSeconds
    private String mediaOwnerId;
    private String mediaOwnerName;
    private String address;
    private InventoryLocation location; // Combined location details (hierarchy + coordinates)
    private Double cpmRate;
    private Double spotRate;
    private List<String> programmaticDealTypes;
    private List<String> venueTypeIds;
    private String resolution; // derived: "pixelWidth x pixelHeight"
    private String thumbnailUrl;
    // Digital fields (all)
    private String timeZone;
    private String bookingMode;
    private Integer spotDuration;
    private Integer spotsPerLoop;
    private Integer loopDuration;
    private Integer loopsPerHour;
    private Integer spotsPerHour; // calculated: loopsPerHour * spotsPerLoop
    private Integer playerCount;
    private Integer playerSoftwareId;
    private String playerSoftwareName;
    // External refs
    private List<ExternalRef> externalRefIds;
    private String deviceId;
    // Operating hours per weekday — lets consumers validate schedules without a
    // separate enrichment call. Jackson serializes the enum key as the weekday name.
    private Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimes;
    private SellingTerm sellingTerm;
    // Physical display panels — panels.size() is the authoritative screen count
    private List<Inventory.Panel> panels;
    private String size;
    private List<String> inventoryCluster;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class ExternalRef {
    private String source;
    private String externalRefId;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class InventoryLocation {
    private String countryId;
    private String countryName;
    private String stateId;
    private String stateName;
    private String cityId;
    private String cityName;
    private Object locationCoordinates; // Can be GeoJsonPoint or GeoJsonLineString
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class PaginationInfo {
    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
    private Boolean hasNext;
    private Boolean hasPrevious;
  }
}
