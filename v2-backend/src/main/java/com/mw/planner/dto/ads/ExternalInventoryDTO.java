package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for inventory in external payload */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Inventory details in external payload")
public class ExternalInventoryDTO {

  @JsonIgnore private String classification;

  @JsonProperty("id")
  @Schema(description = "Inventory ID", example = "inv-mall-nyc-001")
  private String id;

  @JsonProperty("adUnitCode")
  @Schema(description = "Ad unit code", example = "NYC-MALL-ENTRANCE-A")
  private String adUnitCode;

  @JsonProperty("name")
  @Schema(description = "Inventory name", example = "NYC Mall Main Entrance")
  private String name;

  @JsonProperty("referenceId")
  @Schema(description = "Reference ID", example = "ref-nyc-mall-001")
  private String referenceId;

  @JsonProperty("deviceId")
  @Schema(description = "Device ID", example = "device-nyc-001")
  private String deviceId;

  @JsonProperty("size")
  @Schema(description = "Display size", example = "1920x1080")
  private String size;

  @JsonProperty("publisher")
  @Schema(description = "Publisher information")
  private PublisherDTO publisher;

  @JsonProperty("latitude")
  @Schema(description = "Latitude", example = "40.7589")
  private Double latitude;

  @JsonProperty("longitude")
  @Schema(description = "Longitude", example = "-73.9851")
  private Double longitude;

  @JsonProperty("venueType")
  @Schema(description = "Venue type", example = "retail.malls")
  private String venueType;

  @JsonProperty("venueTypeIds")
  @Schema(description = "List of venue type IDs")
  private List<String> venueTypeIds;

  @JsonProperty("thumbnail")
  @Schema(
      description = "Thumbnail URL",
      example = "https://cdn.example.com/thumbnails/nyc-mall-001.jpg")
  private String thumbnail;

  @JsonProperty("timezone")
  @Schema(description = "Timezone", example = "America/New_York")
  private String timezone;

  @JsonProperty("countryIso2")
  @Schema(description = "Country ISO2 code", example = "US")
  private String countryIso2;

  @JsonProperty("countryIso3")
  @Schema(description = "Country ISO3 code", example = "USA")
  private String countryIso3;

  @JsonProperty("spotsPerHour")
  @Schema(description = "Spots per hour", example = "120")
  private Integer spotsPerHour;

  @JsonProperty("spotDuration")
  @Schema(description = "Spot duration in seconds", example = "15")
  private Integer spotDuration;

  @JsonProperty("clients")
  @Schema(description = "Number of clients/audience", example = "50000")
  private Integer clients;

  @JsonProperty("group")
  @Schema(description = "Inventory group", example = "premium-malls")
  private String group;

  @JsonProperty("networkId")
  @Schema(description = "Network ID", example = "network-mall-east")
  private String networkId;

  @JsonProperty("packageId")
  @Schema(description = "Package ID", example = "pkg-premium-retail")
  private String packageId;

  @JsonProperty("bcat")
  @Schema(description = "Blocked categories")
  private List<BcatDTO> bcat;

  @JsonProperty("publisherDomain")
  @Schema(description = "Publisher domain", example = "mallmedia.com")
  private String publisherDomain;

  @JsonProperty("publisherExternalId")
  @Schema(description = "Publisher external ID", example = "694b7167a694e80af4521305")
  private String publisherExternalId;

  @JsonProperty("enableAspectRatio")
  @Schema(description = "Enable aspect ratio", example = "true")
  private Boolean enableAspectRatio;

  @JsonProperty("displayAspectRatio")
  @Schema(description = "Display aspect ratio", example = "16:9")
  private String displayAspectRatio;

  @JsonProperty("schedule")
  @Schema(description = "Inventory schedule configuration")
  private List<ScheduleDTO> schedule;

  @JsonProperty("bookingMode")
  @Schema(description = "Booking mode", example = "loop")
  private String bookingMode;

  @JsonProperty("transit")
  @Schema(description = "Transit types")
  private List<String> transit;

  @JsonProperty("planning")
  @Schema(description = "Planning information with allocation, estimates, and pricing")
  private PlanningDTO planning;

  @JsonProperty("metadata")
  @Schema(description = "Metadata with external reference IDs")
  private MetadataDTO metadata;

  @JsonProperty("inventoryType")
  @Schema(description = "Inventory type (e.g. DIGITAL or CLASSIC)", example = "DIGITAL")
  private String inventoryType;

  /** Metadata DTO */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Metadata with external reference IDs")
  public static class MetadataDTO {
    @JsonProperty("externalRefIds")
    @Schema(description = "List of external reference IDs from various platforms")
    private List<ExternalRefIdDTO> externalRefIds;
  }

  /** External Reference ID DTO */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "External reference ID from a specific platform")
  public static class ExternalRefIdDTO {
    @JsonProperty("source")
    @Schema(description = "Source platform name", example = "LMX")
    private String source;

    @JsonProperty("externalRefId")
    @Schema(description = "External reference ID value", example = "JPN-JEK-D-00000-00032")
    private String externalRefId;
  }

  /** Publisher DTO */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Publisher information")
  public static class PublisherDTO {
    @JsonProperty("id")
    @Schema(description = "Publisher ID", example = "pub-mall-media-001")
    private String id;

    @JsonProperty("name")
    @Schema(description = "Publisher name", example = "Mall Media Network")
    private String name;

    @JsonProperty("externalId")
    @Schema(description = "Publisher external ID", example = "694b7167a694e80af4521305")
    private String externalId;
  }

  /** Blocked Category DTO */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Blocked category information")
  public static class BcatDTO {
    @JsonProperty("name")
    @Schema(description = "Category name", example = "Alcohol")
    private String name;

    @JsonProperty("code")
    @Schema(description = "Category code", example = "IAB8-5")
    private String code;
  }

  /** Planning DTO */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Planning information")
  public static class PlanningDTO {
    @JsonProperty("allocation")
    @Schema(description = "Allocation information")
    private AllocationDTO allocation;

    @JsonProperty("estimates")
    @Schema(description = "Estimates information")
    private EstimatesDTO estimates;

    @JsonProperty("pricing")
    @Schema(description = "Pricing information")
    private PricingDTO pricing;
  }

  /** Allocation DTO */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Allocation information")
  public static class AllocationDTO {
    @JsonProperty("slots")
    @Schema(description = "Total slots", example = "128160")
    private Long slots;

    @JsonProperty("playTimeSec")
    @Schema(description = "Total play time in seconds", example = "1922400")
    private Long playTimeSec;

    @JsonProperty("sov")
    @Schema(description = "Share of Voice", example = "0.5")
    private Double sov;

    @JsonProperty("sot")
    @Schema(description = "Share of Time", example = "0.5")
    private Double sot;

    @JsonProperty("loopDuration")
    @Schema(description = "Duration of one loop in seconds", example = "360")
    private Integer loopDuration;

    @JsonProperty("spotsPerLoop")
    @Schema(description = "Number of spots per loop", example = "24")
    private Integer spotsPerLoop;

    @JsonProperty("bookedSpotsPerLoop")
    @Schema(description = "Spots booked for this advertiser per loop", example = "1")
    private Integer bookedSpotsPerLoop;

    @JsonProperty("bookedSpotsPerHour")
    @Schema(description = "Spots booked per hour", example = "10")
    private Integer bookedSpotsPerHour;
  }

  /** Estimates DTO */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Estimates information")
  public static class EstimatesDTO {
    @JsonProperty("impressions")
    @Schema(description = "Estimated impressions", example = "6408000")
    private Long impressions;

    @JsonProperty("reach")
    @Schema(description = "Estimated reach", example = "1281600")
    private Long reach;

    @JsonProperty("frequency")
    @Schema(description = "Estimated frequency", example = "5.0")
    private Double frequency;
  }

  /** Pricing DTO */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Pricing information")
  public static class PricingDTO {
    @JsonProperty("cpm")
    @Schema(description = "Cost per mille", example = "7.5")
    private Double cpm;

    @JsonProperty("estimatedCost")
    @Schema(description = "Estimated cost", example = "48060")
    private Double estimatedCost;
  }
}
