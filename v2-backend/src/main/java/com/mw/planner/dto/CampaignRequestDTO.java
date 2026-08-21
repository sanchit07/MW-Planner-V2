package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mw.planner.domain.Campaign;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for creating or updating a campaign")
public class CampaignRequestDTO {

  @NotBlank(message = "validation.campaign_name_is_required")
  @Size(max = 255, message = "validation.campaign_name_size")
  @Schema(
      description = "Campaign name",
      example = "Campaign_Sep_17_25_001",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String name;

  @Size(max = 1000, message = "validation.description_size")
  @Schema(
      description = "Campaign description",
      example = "A comprehensive summer sale campaign targeting young adults")
  private String description;

  @Schema(description = "Campaign status", example = "DRAFT")
  private Campaign.Status status;

  @DecimalMin(value = "0.0", message = "validation.budget_decimal_min")
  @Schema(description = "Campaign budget", example = "10000.00")
  private Double budget;

  @Size(max = 3, message = "validation.currency_size")
  @Schema(description = "Currency code", example = "USD")
  private String currency;

  @NotNull(message = "validation.start_date_required")
  @Schema(
      description = "Campaign start date",
      example = "2024-06-01",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private LocalDate startDate;

  @NotNull(message = "validation.end_date_required")
  @Schema(
      description = "Campaign end date",
      example = "2024-08-31",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private LocalDate endDate;

  @JsonIgnore private String userId;

  @Schema(description = "Brand associated with the campaign")
  private Campaign.CampaignBrand brand;

  @NotNull(message = "validation.client_type_required")
  @Schema(
      description = "Client type",
      example = "DIRECT_ADVERTISER",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private Campaign.ClientType clientType;

  @Schema(description = "Agency associated with the campaign")
  private Campaign.CampaignAgency agency;

  @Schema(description = "Company ID associated with the campaign", example = "company123")
  private String companyId;

  @Schema(description = "Country ID for the campaign", example = "US")
  private String countryId;

  @Schema(
      description = "Current company ID reference",
      example = "93b4f544-e657-4eb7-872e-7b9c1d0e0197")
  private String currentCompanyId;

  @Schema(description = "Current company name reference", example = "QA Internal")
  private String currentCompanyName;

  @Valid
  @Schema(description = "Campaign goals")
  private Goals goals;

  @Valid
  @Schema(description = "Campaign targeting information")
  private Targeting targeting;

  @Valid
  @Schema(description = "Campaign performance forecast")
  private CampaignForecastDTO performance;

  @Schema(
      description = "Budget allocation by channel",
      example = "{\"DIGITAL\": 60.0, \"TV\": 40.0}")
  private Map<String, Double> budgetAllocation;

  @Schema(
      description = "Media channels for the campaign",
      example = "[\"DIGITAL_OOH\", \"CLASSIC_OOH\"]")
  private List<Campaign.MediaChannel> mediaChannels;

  @Schema(description = "Demand Side Platform name or identifier", example = "DV360")
  private String dsp;

  @Valid
  @Schema(description = "Campaign optimization settings")
  private Optimization optimization;

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
    @Schema(
        description = "Demographics targeting",
        example = "{\"age\": [\"18-24\", \"25-34\"], \"gender\": [\"MALE\"]}")
    private Map<String, List<String>> demographics;

    @Schema(description = "Venue types by media channel")
    private VenueTypes venueTypes;

    @Valid
    @Schema(description = "Geofencing information")
    private Geofencing geofencing;

    @Schema(
        description = "Targeting signals",
        example = "[\"interest_sports\", \"behavior_online_shopping\"]")
    private List<String> signals;

    @Schema(
        description = "Restrict recommendations to programmatic inventory only",
        example = "true")
    private Boolean programmaticOnly;

    @Schema(description = "Inventory cluster targeting", example = "[\"cluster-A\", \"cluster-B\"]")
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
    @Schema(description = "Geofencing information")
    public static class Geofencing {
      @Valid
      @Schema(description = "Geometric shapes for targeting")
      private List<Geometry> geometries;

      @Valid
      @Schema(description = "Location points for targeting")
      private List<Location> locations;

      @Data
      @Builder
      @NoArgsConstructor
      @AllArgsConstructor
      @Schema(description = "Geometric shape for geofencing")
      public static class Geometry {
        @Size(max = 255, message = "validation.name_size")
        @Schema(description = "Name of the geometry", example = "Downtown Area")
        private String name;

        @NotBlank(message = "validation.geometry_type_required")
        @Schema(
            description = "Geometry type",
            example = "Polygon/Circle/LineString",
            requiredMode = Schema.RequiredMode.REQUIRED)
        private String type;

        @NotNull(message = "validation.coordinates_required")
        @Schema(description = "GeoJSON coordinates", requiredMode = Schema.RequiredMode.REQUIRED)
        private List<List<Double>> coordinates;

        @Schema(description = "Whether this is an inclusion or exclusion zone", example = "true")
        private boolean isIncluded;

        @Schema(description = "poi", example = "[\"house_complex\", \"establishment\"]")
        private List<String> poi;

        @Schema(description = "metadata", example = "{\"type\": \"circle\"}")
        private Map<String, String> metadata;
      }

      @Data
      @Builder
      @NoArgsConstructor
      @AllArgsConstructor
      @Schema(description = "Location point for geofencing")
      public static class Location {
        @Size(max = 255, message = "validation.name_size")
        @Schema(description = "Name of the location", example = "Times Square")
        private String name;

        @NotNull(message = "validation.latitude_required")
        @DecimalMin(value = "-90.0", message = "validation.latitude_range")
        @DecimalMax(value = "90.0", message = "validation.latitude_range")
        @Schema(
            description = "Latitude",
            example = "40.7128",
            requiredMode = Schema.RequiredMode.REQUIRED)
        private Double lat;

        @NotNull(message = "validation.longitude_required")
        @DecimalMin(value = "-180.0", message = "validation.longitude_range")
        @DecimalMax(value = "180.0", message = "validation.longitude_range")
        @Schema(
            description = "Longitude",
            example = "-74.0060",
            requiredMode = Schema.RequiredMode.REQUIRED)
        private Double lng;

        @DecimalMin(value = "0.0", message = "validation.radius_decimal_min")
        @Schema(description = "Radius in meters", example = "1000.0")
        private Double radius;

        @Size(max = 255, message = "validation.address_size")
        @Schema(description = "Address description", example = "Times Square, New York")
        private String address;

        @Schema(description = "Whether this is an inclusion or exclusion zone", example = "true")
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
    @Schema(description = "Budget allocation optimization settings")
    private Map<String, Object> budgetAllocation;

    @Schema(description = "Schedule optimization settings")
    private Map<String, Object> schedule;

    @Schema(description = "Auto optimization enabled", example = "true")
    private Boolean autoOptimize;
  }

  /**
   * Maps this DTO to a Campaign entity
   *
   * @return Campaign entity
   */
  public Campaign mapToEntity() {
    Campaign.CampaignBuilder builder =
        Campaign.builder()
            .name(this.name)
            .description(this.description)
            .status(this.status != null ? this.status : Campaign.Status.DRAFT)
            .budget(this.budget)
            .currency(this.currency != null ? this.currency : "USD")
            .startDate(this.startDate)
            .endDate(this.endDate)
            .userId(this.userId)
            .brand(this.brand)
            .clientType(this.clientType)
            .agency(this.agency)
            .companyId(this.companyId)
            .countryId(this.countryId)
            .currentCompanyId(this.currentCompanyId)
            .currentCompanyName(this.currentCompanyName)
            .budgetAllocation(this.budgetAllocation)
            .mediaChannels(this.mediaChannels)
            .performance(this.performance)
            .dsp(this.dsp);

    // Map Goals
    if (this.goals != null) {
      Campaign.Goals campaignGoals =
          Campaign.Goals.builder()
              .goalType(this.goals.goalType)
              .targetName(this.goals.targetName)
              .targetValue(this.goals.targetValue)
              .build();
      builder.goals(campaignGoals);
    }

    // Map Targeting
    if (this.targeting != null) {
      Campaign.Targeting.TargetingBuilder targetingBuilder =
          Campaign.Targeting.builder()
              .demographics(this.targeting.demographics)
              .signals(this.targeting.signals)
              .programmaticOnly(this.targeting.programmaticOnly);

      if (this.targeting.venueTypes != null) {
        targetingBuilder.venueTypes(
            Campaign.Targeting.VenueTypes.builder()
                .digitalOoh(this.targeting.venueTypes.digitalOoh)
                .classicOoh(this.targeting.venueTypes.classicOoh)
                .build());
      }

      // Map Geofencing
      if (this.targeting.geofencing != null) {
        Campaign.Targeting.Geofencing.GeofencingBuilder geofencingBuilder =
            Campaign.Targeting.Geofencing.builder();

        // Map geometries
        if (this.targeting.geofencing.geometries != null) {
          List<Campaign.Targeting.Geofencing.Geometry> campaignGeometries =
              this.targeting.geofencing.geometries.stream()
                  .map(
                      geo ->
                          Campaign.Targeting.Geofencing.Geometry.builder()
                              .name(geo.name)
                              .type(geo.type)
                              .coordinates(geo.coordinates)
                              .isIncluded(geo.isIncluded)
                              .poi(geo.poi)
                              .metadata(geo.getMetadata())
                              .build())
                  .collect(java.util.stream.Collectors.toList());
          geofencingBuilder.geometries(campaignGeometries);
        }

        // Map locations
        if (this.targeting.geofencing.locations != null) {
          List<Campaign.Targeting.Geofencing.Location> campaignLocations =
              this.targeting.geofencing.locations.stream()
                  .map(
                      loc ->
                          Campaign.Targeting.Geofencing.Location.builder()
                              .name(loc.name)
                              .lat(loc.lat)
                              .lng(loc.lng)
                              .radius(loc.radius)
                              .address(loc.address)
                              .isIncluded(loc.isIncluded)
                              .poi(loc.poi)
                              .metadata(loc.getMetadata())
                              .build())
                  .collect(java.util.stream.Collectors.toList());
          geofencingBuilder.locations(campaignLocations);
        }

        targetingBuilder.geofencing(geofencingBuilder.build());
      }

      builder.targeting(targetingBuilder.build());
    }

    // Map Optimization
    if (this.optimization != null) {
      Campaign.Optimization optimization =
          Campaign.Optimization.builder()
              .budgetAllocation(this.optimization.budgetAllocation)
              .schedule(this.optimization.schedule)
              .autoOptimize(this.optimization.autoOptimize)
              .build();
      builder.optimization(optimization);
    }

    return builder.build();
  }

  /**
   * Maps a Campaign entity to this DTO
   *
   * @param campaign Campaign entity
   * @return CampaignRequestDTO
   */
  public static CampaignRequestDTO mapToDto(Campaign campaign) {
    CampaignRequestDTO.CampaignRequestDTOBuilder builder =
        CampaignRequestDTO.builder()
            .name(campaign.getName())
            .description(campaign.getDescription())
            .status(campaign.getStatus())
            .budget(campaign.getBudget())
            .currency(campaign.getCurrency())
            .startDate(campaign.getStartDate())
            .endDate(campaign.getEndDate())
            .userId(campaign.getUserId())
            .brand(campaign.getBrand())
            .clientType(campaign.getClientType())
            .agency(campaign.getAgency())
            .companyId(campaign.getCompanyId())
            .countryId(campaign.getCountryId())
            .budgetAllocation(campaign.getBudgetAllocation())
            .dsp(campaign.getDsp());

    // Map Goals
    if (campaign.getGoals() != null) {
      Goals goals =
          Goals.builder()
              .goalType(campaign.getGoals().getGoalType())
              .targetName(campaign.getGoals().getTargetName())
              .targetValue(campaign.getGoals().getTargetValue())
              .build();
      builder.goals(goals);
    }

    // Map Targeting
    if (campaign.getTargeting() != null) {
      Targeting.TargetingBuilder targetingBuilder =
          Targeting.builder()
              .demographics(campaign.getTargeting().getDemographics())
              .signals(campaign.getTargeting().getSignals())
              .programmaticOnly(campaign.getTargeting().getProgrammaticOnly())
              .inventoryCluster(campaign.getTargeting().getInventoryCluster());

      // Map Geofencing
      if (campaign.getTargeting().getGeofencing() != null) {
        Targeting.Geofencing.GeofencingBuilder geofencingBuilder = Targeting.Geofencing.builder();

        // Map geometries
        if (campaign.getTargeting().getGeofencing().getGeometries() != null) {
          List<Targeting.Geofencing.Geometry> dtoGeometries =
              campaign.getTargeting().getGeofencing().getGeometries().stream()
                  .map(
                      geo ->
                          Targeting.Geofencing.Geometry.builder()
                              .name(geo.getName())
                              .type(geo.getType())
                              .coordinates(geo.getCoordinates())
                              .isIncluded(geo.isIncluded())
                              .poi(geo.getPoi())
                              .metadata(geo.getMetadata())
                              .build())
                  .collect(java.util.stream.Collectors.toList());
          geofencingBuilder.geometries(dtoGeometries);
        }

        // Map locations
        if (campaign.getTargeting().getGeofencing().getLocations() != null) {
          List<Targeting.Geofencing.Location> dtoLocations =
              campaign.getTargeting().getGeofencing().getLocations().stream()
                  .map(
                      loc ->
                          Targeting.Geofencing.Location.builder()
                              .name(loc.getName())
                              .lat(loc.getLat())
                              .lng(loc.getLng())
                              .radius(loc.getRadius())
                              .address(loc.getAddress())
                              .isIncluded(loc.isIncluded())
                              .poi(loc.getPoi())
                              .metadata(loc.getMetadata())
                              .build())
                  .collect(java.util.stream.Collectors.toList());
          geofencingBuilder.locations(dtoLocations);
        }

        targetingBuilder.geofencing(geofencingBuilder.build());
      }

      builder.targeting(targetingBuilder.build());
    }

    // Map Optimization
    if (campaign.getOptimization() != null) {
      Optimization optimization =
          Optimization.builder()
              .budgetAllocation(campaign.getOptimization().getBudgetAllocation())
              .schedule(campaign.getOptimization().getSchedule())
              .autoOptimize(campaign.getOptimization().getAutoOptimize())
              .build();
      builder.optimization(optimization);
    }

    return builder.build();
  }
}
