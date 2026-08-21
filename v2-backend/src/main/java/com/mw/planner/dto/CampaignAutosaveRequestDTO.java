package com.mw.planner.dto;

import com.mw.planner.domain.Campaign;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.*;
import org.openapitools.jackson.nullable.JsonNullable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for autosaving campaign draft")
public class CampaignAutosaveRequestDTO {

  @Size(max = 255, message = "validation.campaign_name_size")
  @Schema(description = "Campaign name", example = "Campaign_Sep_17_25_001")
  private String name;

  @Size(max = 1000, message = "validation.description_size")
  @Schema(
      description = "Campaign description",
      example = "A comprehensive summer sale campaign targeting young adults")
  private String description;

  @DecimalMin(value = "0.0", message = "validation.budget_decimal_min")
  @Schema(description = "Campaign budget", example = "10000.00")
  private Double budget;

  @Size(max = 3, message = "validation.currency_size")
  @Schema(description = "Currency code", example = "USD")
  private String currency;

  @Schema(description = "Campaign start date", example = "2024-06-01")
  private LocalDate startDate;

  @Schema(description = "Campaign end date", example = "2024-08-31")
  private LocalDate endDate;

  @Schema(description = "Brand associated with the campaign")
  private Campaign.CampaignBrand brand;

  @Schema(description = "Client type", example = "DIRECT_ADVERTISER")
  private Campaign.ClientType clientType;

  @Schema(description = "Agency associated with the campaign")
  private Campaign.CampaignAgency agency;

  @Schema(description = "Country ID for the campaign", example = "US")
  private String countryId;

  @Valid
  @Schema(description = "Campaign goals")
  private Goals goals;

  @Valid
  @Schema(description = "Campaign targeting information")
  private Targeting targeting;

  @Schema(
      description = "Budget allocation by channel",
      example = "{\"DIGITAL\": 60.0, \"TV\": 40.0}")
  private Map<String, Double> budgetAllocation;

  @Schema(
      description = "Media channels for the campaign",
      example = "[\"DIGITAL_OOH\", \"CLASSIC_OOH\"]")
  private List<Campaign.MediaChannel> mediaChannels;

  @Valid
  @Schema(description = "Campaign optimization settings")
  private Optimization optimization;

  @Schema(description = "Skip recommendation processing for this campaign", example = "false")
  private Boolean skipRecommendation;

  @Schema(description = "Demand Side Platform name or identifier", example = "DV360")
  private JsonNullable<String> dsp;

  @Schema(description = "Campaign performance forecast snapshot")
  private CampaignForecastDTO performance;

  // Nested classes for complex JSON fields
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Campaign goals")
  public static class Goals {
    @Schema(description = "Goal type", example = "IMPRESSIONS")
    private Campaign.Goals.GoalType goalType;

    @Size(max = 255, message = "validation.target_name_size")
    @Schema(description = "Target name for custom goals", example = "Brand Awareness")
    private String targetName;

    @DecimalMin(value = "0.0", message = "validation.target_value_decimal_min")
    @Schema(description = "Target value", example = "1000000.0")
    private Double targetValue;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Campaign targeting information")
  public static class Targeting {
    @Schema(description = "Demographics targeting")
    private Map<String, List<String>> demographics;

    @Schema(description = "Venue types by media channel")
    private VenueTypes venueTypes;

    @Valid
    @Schema(description = "Geofencing targeting")
    private Geofencing geofencing;

    @Schema(description = "Targeting signals")
    private List<String> signals;

    @Schema(description = "Restrict recommendations to programmatic inventory only")
    private Boolean programmaticOnly;

    @Schema(description = "Inventory cluster targeting")
    private List<String> inventoryCluster;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Venue types split by media channel")
    public static class VenueTypes {
      @Schema(
          description = "Digital OOH venue type string values",
          example = "[\"health-beauty\", \"health-beauty-gyms\"]")
      private List<String> digitalOoh;

      @Schema(
          description = "Classic OOH venue type string values",
          example = "[\"outdoor\", \"outdoor-billboards\"]")
      private List<String> classicOoh;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Geofencing targeting")
    public static class Geofencing {
      @Schema(description = "Geofencing geometries")
      private List<Geometry> geometries;

      @Schema(description = "Geofencing locations")
      private List<Location> locations;

      @Data
      @Builder
      @NoArgsConstructor
      @AllArgsConstructor
      @Schema(description = "Geofencing geometry")
      public static class Geometry {
        @Schema(description = "Name of the geometry", example = "Downtown Area")
        private String name;

        @Schema(description = "Geometry type", example = "Polygon")
        private String type;

        @Schema(description = "GeoJSON coordinates")
        private List<List<Double>> coordinates;

        @Schema(description = "Whether this geometry is included or excluded")
        private boolean isIncluded;

        @Schema(description = "Geometry type", example = "[\"house_complex\", \"establishment\"]")
        private List<String> poi;

        @Schema(description = "", example = "{\"type\": \"circle\"}")
        private Map<String, String> metadata;
      }

      @Data
      @Builder
      @NoArgsConstructor
      @AllArgsConstructor
      @Schema(description = "Geofencing location")
      public static class Location {
        @Schema(description = "Name of the location", example = "Times Square")
        private String name;

        @Schema(description = "Latitude", example = "40.7128")
        private Double lat;

        @Schema(description = "Longitude", example = "-74.0060")
        private Double lng;

        @Schema(description = "Radius in meters", example = "1000.0")
        private Double radius;

        @Schema(description = "Address", example = "New York, NY")
        private String address;

        @Schema(description = "Whether this location is included or excluded")
        private boolean isIncluded;

        @Schema(description = "poi", example = "[\"house_complex\", \"establishment\"]")
        private List<String> poi;

        @Schema(description = "metadata", example = "{\"type\": \"circle\"}")
        private Map<String, String> metadata;
      }
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Campaign optimization settings")
  public static class Optimization {
    @Schema(description = "Budget allocation optimization")
    private Map<String, Object> budgetAllocation;

    @Schema(description = "Schedule optimization")
    private Map<String, Object> schedule;

    @Schema(description = "Auto-optimization enabled")
    private Boolean autoOptimize;
  }
}
