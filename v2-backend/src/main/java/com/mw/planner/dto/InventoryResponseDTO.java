package com.mw.planner.dto;

import com.mw.planner.domain.Inventory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for inventory response with performance metrics. Contains inventory details and performance
 * metrics for campaign planning.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Inventory response with performance metrics")
public class InventoryResponseDTO {

  @Schema(description = "Inventory ID")
  private String id;

  @Schema(description = "Inventory name")
  private String name;

  @Schema(description = "Inventory reference ID")
  private String referenceId;

  @Schema(description = "Inventory type")
  private String type;

  @Schema(description = "Inventory format")
  private String format;

  @Schema(description = "Environment")
  private String environment;

  @Schema(description = "Venue types")
  private List<String> venueType;

  @Schema(description = "Archived status")
  private Boolean archived;

  @Schema(description = "Media owner name")
  private String mediaOwnerName;

  @Schema(description = "Media owner ID")
  private String mediaOwnerId;

  @Schema(description = "Location information")
  private LocationDTO location;

  @Schema(description = "Panels")
  private List<PanelDTO> panels;

  @Schema(description = "Thumbnail URL")
  private String thumbnailUrl;

  @Schema(description = "Operating times")
  private Map<Inventory.Weekday, List<OperatingTimeDTO>> operatingTimes;

  @Schema(description = "Selling term")
  private SellingTermDTO sellingTerm;

  @Schema(description = "Orientation")
  private Inventory.Orientation orientation;

  @Schema(description = "Time zone")
  private String timeZone;

  @Schema(description = "Requires content approval")
  private Boolean requiresContentApproval;

  @Schema(description = "Programmatic deal types")
  private List<String> programmaticDealTypes;

  @Schema(description = "Creative formats")
  private List<CreativeFormatDTO> creativeFormats;

  @Schema(description = "Prices")
  private List<PriceDTO> prices;

  @Schema(description = "Digital fields")
  private DigitalFieldsDTO digitalFields;

  @Schema(description = "Classic fields")
  private ClassicFieldsDTO classicFields;

  @Schema(description = "Transit fields")
  private TransitFieldsDTO transitFields;

  @Schema(description = "Inventory size")
  private String size;

  @Schema(description = "Inventory cluster")
  private List<String> inventoryCluster;

  /** Location information */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Location information")
  public static class LocationDTO {
    @Schema(description = "Address")
    private String address;

    @Schema(description = "Country")
    private String country;

    @Schema(description = "State")
    private String state;

    @Schema(description = "City")
    private String city;

    @Schema(description = "ZIP code")
    private String zipCode;

    @Schema(description = "Coordinates with type and lat/lng arrays")
    private LocationCoordinatesDTO locationCoordinates;

    /** Location coordinates information */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Location coordinates information")
    public static class LocationCoordinatesDTO {
      @Schema(description = "Type of geometry (Point or LineString)")
      private String type;

      @Schema(description = "List of coordinate pairs [latitude, longitude]")
      private List<CoordinatePair> coordinates;

      /** Coordinate pair containing latitude and longitude */
      @Data
      @Builder
      @NoArgsConstructor
      @AllArgsConstructor
      @Schema(description = "Coordinate pair")
      public static class CoordinatePair {
        @Schema(description = "Latitude")
        private Double latitude;

        @Schema(description = "Longitude")
        private Double longitude;
      }
    }
  }

  /** Panel information */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Panel information")
  public static class PanelDTO {
    @Schema(description = "Pixel width")
    private Integer pixelWidth;

    @Schema(description = "Pixel height")
    private Integer pixelHeight;

    @Schema(description = "Physical width")
    private Double physicalWidth;

    @Schema(description = "Physical height")
    private Double physicalHeight;

    @Schema(description = "Panel count")
    private Integer panelCount;

    @Schema(description = "Unit")
    private String unit;

    @Schema(description = "Size")
    private Inventory.Size size;
  }

  /** Operating time information */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Operating time information")
  public static class OperatingTimeDTO {
    @Schema(description = "Start time")
    private String start;

    @Schema(description = "End time")
    private String end;
  }

  /** Selling term information */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Selling term information")
  public static class SellingTermDTO {
    @Schema(description = "Lead days")
    private Integer leadDays;

    @Schema(description = "Minimum hours")
    private Integer minHours;

    @Schema(description = "Minimum days")
    private Integer minDays;

    @Schema(description = "Day part groups")
    private Map<String, DayPartGroupDTO> dayPartGroups;
  }

  /** Day part group information */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Day part group information")
  public static class DayPartGroupDTO {
    @Schema(description = "Start time")
    private String start;

    @Schema(description = "End time")
    private String end;
  }

  /** Creative format information */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Creative format information")
  public static class CreativeFormatDTO {
    @Schema(description = "Format")
    private String format;

    @Schema(description = "Creative type")
    private String creativeType;
  }

  /** Price information */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Price information")
  public static class PriceDTO {
    @Schema(description = "CPM (Cost per mille)")
    private Double cpm;

    @Schema(description = "Spot price")
    private Double spot;
  }

  /** Digital fields information */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Digital fields information")
  public static class DigitalFieldsDTO {
    @Schema(description = "Player software ID")
    private Integer playerSoftwareId;

    @Schema(description = "Player software name")
    private String playerSoftwareName;

    @Schema(description = "Player count")
    private Integer playerCount;

    @Schema(description = "Spot duration")
    private Integer spotDuration;

    @Schema(description = "Spots per loop")
    private Integer spotsPerLoop;

    @Schema(description = "Booking mode")
    private String bookingMode;
  }

  /** Classic fields information */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Classic fields information")
  public static class ClassicFieldsDTO {
    @Schema(description = "Illuminated")
    private Boolean illuminated;
  }

  /** Transit fields information */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Transit fields information")
  public static class TransitFieldsDTO {
    @Schema(description = "Route ID")
    private String routeId;

    @Schema(description = "Route name")
    private String routeName;
  }
}
