package com.mw.recommendation.engine.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "recommendation_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@CompoundIndex(name = "runId_inventoryId_idx", def = "{'runId': 1, 'inventoryId': 1}")
public class RecommendationResult extends BaseEntity<String> {

  @Indexed private String runId; // Links to RecommendationRun.runId

  @Indexed private String campaignId; // Campaign ID

  // Inventory identifiers
  private String inventoryId;
  private String referenceId;
  private String name;

  // Inventory details (grouped)
  private InventoryDetails inventoryDetails;

  // Scoring
  private Double finalScore; // 0-100
  private ComponentScores componentScores;
  private String why; // Explanation text

  // Availability
  private AvailabilitySummary availability;

  // Forecast
  private ForecastedMetrics forecast;

  // Cost
  private CostEstimate cost;

  // Selection tracking
  private Boolean
      isExcluded; // Whether this inventory was excluded during initial recommendation generation

  /**
   * MANUAL = user selected via selected-inventories, AUTO = system auto-selected, null = not
   * selected.
   */
  private SelectionMode selectionMode;

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
    private List<String> sizes; // ["XS", "S", "M", "L", "XL"] - from panels
    private List<String> resolutions; // ["920x1200"] - from panelspixelWidth x pixelHeight
    private List<Integer> durations; // from panels
    private String mediaOwnerId;
    private String mediaOwnerName;
    private String address;
    private InventoryLocation location; // Combined location details (hierarchy + coordinates)
    private Double cpmRate;
    private Double spotRate;
    private List<String> programmaticDealTypes; // From inventory, stored lowercase
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
    // Operating hours per weekday — surfaced so consumers can validate schedules
    // without a separate enrichment call.
    private Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimes;
    private Inventory.SellingTerm sellingTerm;
    // Physical display panels — panels.size() is the authoritative screen count
    private List<Inventory.Panel> panels;
    // Size (top-level string from external system)
    private String size;
    // Inventory cluster grouping
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
}
