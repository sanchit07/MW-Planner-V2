package com.mw.recommendation.engine.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "inventories")
@CompoundIndex(
    name = "country_archived_idx",
    def = "{'locationHierarchy.countryName': 1, 'archived': 1}")
@CompoundIndex(
    name = "country_city_idx",
    def = "{'locationHierarchy.countryName': 1, 'locationHierarchy.cityName': 1}")
@CompoundIndex(
    name = "country_state_idx",
    def = "{'locationHierarchy.countryName': 1, 'locationHierarchy.stateName': 1}")
@CompoundIndex(
    name = "country_mediaowner_idx",
    def = "{'locationHierarchy.countryName': 1, 'mediaOwnerId': 1}")
@CompoundIndex(
    name = "country_classification_idx",
    def = "{'locationHierarchy.countryName': 1, 'classification': 1}")
@CompoundIndex(
    name = "country_archived_prices_idx",
    def = "{'locationHierarchy.countryName': 1, 'archived': 1, 'prices.cpm': 1}")
public class Inventory extends BaseEntity<String> {

  // Core identifiers
  @Indexed(unique = true, sparse = true)
  private String inventoryId; // UUID from mw-inventory

  private String referenceId; // Human-readable ID (e.g., "JPN-JEK-D-00000-01022")

  // Basic information
  private String name;
  private String mediaOwnerId;
  private String mediaOwnerName;

  // Location
  @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
  private Object locationCoordinates; // Can be GeoJsonPoint or GeoJsonLineString

  private String address;
  private String timeZone;
  private LocationHierarchy locationHierarchy;

  // Classification
  private String classification; // "Digital", "Classic", "Transit" (from typeName[0])
  private String type; // "OOH", "Audio", etc. (from typeName[1])
  private String format; // "LED Billboard", etc. (from displayFormatName)
  private String environment; // "outdoor", "indoor"
  private List<String> venueTypes; // ["Outdoor", "Billboards", "Roadside"]
  private List<String> venueTypeIds; // taxonomy IDs for venue types
  private Orientation orientation; // "landscape", "portrait"
  private Double viewingDistance; // meters

  // Physical properties
  private List<Panel> panels;
  private QualityMetrics qualityMetrics;

  // Pricing
  private List<PriceModel> prices;

  // Derived: pricing models offered ("cpm", "spot", "monthly"); populated from prices on sync
  private List<String> priceTypes = new ArrayList<>();
  private PricingMetadata pricingMetadata;

  // Availability & Scheduling
  private Map<Weekday, List<OperatingTime>> operatingTimes;
  private SellingTerm sellingTerm;

  // Digital-specific (if applicable)
  private DigitalFields digitalFields;

  // Operational data (calculated at runtime if not present)
  private Integer operationalHours; // Hours per day (from operatingTimes)
  private Integer dailySpots; // Spots per day (calculated from digitalFields if missing)
  private Integer monthlySpots; // Spots per month (calculated from digitalFields if missing)

  // Programmatic
  private List<String> programmaticDealTypes;
  private List<String> dsps; // ["MAX", "LMX"]

  // Creative
  private List<CreativeFormat> creativeFormats;
  private Boolean requiresContentApproval;

  // Status
  private Boolean archived;
  private Boolean active; // Computed: !archived

  // Exclusions (for brand category filtering)
  private List<ContentExclusion> contentExclusions; // Content exclusions from taxonomy

  // Media
  private List<String> medias; // List of media URLs

  // Tags
  private List<Tag> tags; // Tags with mediaOwnerId, name, hexColor

  // External IDs (platform -> externalId mappings)
  private List<ExternalId> externalIds;

  // Blackout periods (dates when inventory is unavailable)
  private List<Blackout> blackouts;

  // Size (top-level, distinct from Panel.Size enum)
  private String size;

  // Inventory cluster grouping
  private List<String> inventoryCluster;

  // Metadata
  private String thumbnailUrl;
  private LocalDateTime lastSyncedAt;
  private String lastSyncedFrom;

  public enum Weekday {
    SUNDAY, // 0
    MONDAY, // 1
    TUESDAY, // 2
    WEDNESDAY, // 3
    THURSDAY, // 4
    FRIDAY, // 5
    SATURDAY // 6
  }

  public enum Orientation {
    LANDSCAPE,
    PORTRAIT
  }

  public enum Size {
    XS, // < 5 sqft
    S, // >= 5 sqft & < 30 sqft
    M, // >= 30 sqft & < 100 sqft
    L, // >= 100 sqft & < 300 sqft
    XL // >= 300 sqft
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class LocationHierarchy {
    private String countryId;
    private String countryName;
    private String stateId;
    private String stateName;
    private String cityId;
    private String cityName;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Panel {
    private Integer pixelWidth;
    private Integer pixelHeight;
    private Double physicalWidth; // meters
    private Double physicalHeight; // meters
    private Integer panelCount;
    private Double totalArea; // sqft (computed)
    private Size size; // XS, S, M, L, XL (computed)
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class QualityMetrics {
    private Double screenSize; // sqft
    private Integer pitch; // LED pitch (if digital)
    private String visibility; // "high", "medium", "low"
    private Integer angle; // degrees
    private Boolean hasObstructions;
    private Integer lanesVisible;
    private Double qualityScore; // 0-100 (computed)
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PriceModel {
    private Double cpm; // Cost per mille
    private Double spot; // Cost per spot
    private Double monthly; // Monthly flat rate
    private Double daily; // Daily rate
    private Double weekly; // Weekly rate
    private String currency; // ISO currency code
    private Integer durationSeconds; // Duration for this price (e.g., 10s, 30s spot)
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PricingMetadata {
    private String pricingModel; // "CPM", "SPOT", "MONTHLY", "MIXED"
    private Map<String, Double> daypartRates; // Hourly rates if applicable
    private Map<String, Double> dayWisePrices; // Day-specific pricing
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DigitalFields {
    private Integer playerSoftwareId;
    private String playerSoftwareName;
    private Integer playerCount;
    private Integer spotDuration; // seconds
    private Integer spotsPerLoop;
    private String bookingMode; // "loop", "spot"
    private Integer loopDuration; // seconds
    private Integer loopsPerHour;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CreativeFormat {
    private String format; // "mp4", "jpg"
    private String creativeType; // "video", "image"
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class OperatingTime {
    private String start; // "HH:mm:ss"
    private String end; // "HH:mm:ss"
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SellingTerm {
    private Integer leadDays;
    private Integer minHours;
    private Integer minDays;
    private Map<String, DayPartGroup> dayPartGroups;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DayPartGroup {
    private String start;
    private String end;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ContentExclusion {
    private String name;
    private String taxonomyId;
    private String version;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Tag {
    private String id;
    private String mediaOwnerId;
    private String name;
    private String hexColor;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ExternalId {
    private String platform;
    private String externalRefId;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Blackout {
    private String id;
    private String reason;
    private LocalDate startDate;
    private LocalDate endDate;
  }
}
