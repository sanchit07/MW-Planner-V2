package com.mw.planner.domain;

import com.mw.planner.dto.ExternalInventoryMessageDTO.ExternalId;
import java.util.List;
import java.util.Map;
import lombok.*;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "inventories")
public class Inventory extends BaseEntity<String> {

  private String name;
  @Indexed private String inventoryId; // Envelope inventoryId from RabbitMQ message
  private String externalId; // DB from external system
  private String referenceId; // User readable reference to external system
  private String classification; // "Digital", "Classic", "Transit" (from typeName[0])
  private String type; // "OOH", "Audio", etc. (from typeName[1])
  private String format; // "LED Billboard", etc. (from displayFormatName)
  private String environment; // Renamed from category
  @Indexed private List<String> venueType; // Changed from String to List<String>
  private List<String> venueTypeIds; // OpenOOH taxonomyId from RabbitMQ venues
  private Double viewingDistance; // meters
  private Boolean archived; // Replaced active (inverted: !archived = active)
  private Location location;
  private List<Panel> panels; // Replaced dimensions
  private String mediaOwnerId;
  private String mediaOwnerName;
  private String thumbnailUrl; // Default preview image
  private Map<Weekday, List<OperatingTime>>
      operatingTimes; // New field from schedule.operatingTimes
  private SellingTerm sellingTerm; // Updated structure
  private Orientation orientation; // New field
  private String timeZone; // New field
  private Boolean requiresContentApproval; // New field
  private List<String> programmaticDealTypes; // New field
  private List<CreativeFormat> creativeFormats; // New field
  private List<Price> prices; // New field from message.prices
  private List<String>
      priceTypes; // Internal-only: derived from prices (cpm/spot/monthly), not exposed in DTOs
  private DigitalFields digitalFields; // New field
  private ClassicFields classicFields; // New field
  private TransitFields transitFields; // New field
  private CinemaFields cinemaFields; // Cinema buy attributes (operator/hall/showtime windows)
  private List<ContentExclusion> contentExclusions; // Content exclusions from taxonomy
  private List<String> medias; // List of media URLs
  private List<Tag> tags; // Tags with mediaOwnerId, name, hexColor
  private List<ExternalId> externalIds; // List of external IDs with source and value
  private String size; // Freeform size descriptor from external system
  private List<String> inventoryCluster;

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
  public static class Panel {
    private Integer pixelWidth;
    private Integer pixelHeight;
    private Double physicalWidth;
    private Double physicalHeight;
    private Integer panelCount;
    @Builder.Default private String unit = "Feet"; // Default unit
    private Size size; // Size derived from panel sqft
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class OperatingTime {
    private String start; // e.g., "07:00:00"
    private String end; // e.g., "23:59:00"
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
    private String start; // e.g., "05:00:00"
    private String end; // e.g., "09:00:00"
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Location {
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private Object locationCoordinates; // Can be GeoJsonPoint or GeoJsonLineString

    private String address;
    private String country; // adminLevel0Name
    private String state; // adminLevel1Name
    private String city; // adminLevel2Name
    private String zipCode;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CreativeFormat {
    private String format; // e.g., "mp4", "jpg"
    private String creativeType; // e.g., "video", "image"
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Price {
    private Double cpm; // Cost per mille (thousand impressions)
    private Double spot; // Spot price
    private Double cps; // Cost per spot — the IMS-imported spelling of spot pricing
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
  public static class DigitalFields {
    private Integer playerSoftwareId;
    private String playerSoftwareName;
    private Integer playerCount;
    private Integer spotDuration;
    private Integer spotsPerLoop;
    private String bookingMode; // e.g., "loop"
    private Integer loopDuration; // seconds
    private Integer loopsPerHour;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ClassicFields {
    private Boolean illuminated;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TransitFields {
    private String routeId;
    private String routeName;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CinemaFields {
    private String operator;
    private String operatorId;
    private String cinemaName;
    private String hallName;
    private Integer hallNumber;
    private Integer seats;
    private String screenFormat; // e.g., "2D", "3D", "IMAX"
    private List<ShowtimeWindow> showtimeWindows;
    private List<String> genres;
    private List<String> ratings;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ShowtimeWindow {
    private String label; // e.g., "Matinee"
    private String start; // e.g., "11:00"
    private String end; // e.g., "14:00"
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
}
