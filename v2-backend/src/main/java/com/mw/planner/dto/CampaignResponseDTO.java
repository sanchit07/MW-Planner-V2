package com.mw.planner.dto;

import com.mw.planner.domain.Campaign;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for campaign information")
public class CampaignResponseDTO {

  @Schema(description = "Campaign ID", example = "campaign123")
  private String id;

  @Schema(description = "Campaign name", example = "Summer Sale Campaign 2024")
  private String name;

  @Schema(
      description = "Human-readable 12-digit plan ID (date + daily sequence)",
      example = "202607210001")
  private String planNumber;

  @Schema(
      description = "Campaign description",
      example = "A comprehensive summer sale campaign targeting young adults")
  private String description;

  @Schema(description = "Campaign status", example = "DRAFT")
  private Campaign.Status status;

  @Schema(description = "Campaign budget", example = "10000.00")
  private Double budget;

  @Schema(description = "Currency code", example = "USD")
  private String currency;

  @Schema(description = "Campaign start date", example = "2024-06-01")
  private LocalDate startDate;

  @Schema(description = "Campaign end date", example = "2024-08-31")
  private LocalDate endDate;

  @Schema(description = "User ID who created the campaign", example = "user123")
  private String userId;

  @Schema(description = "Brand associated with the campaign")
  private Campaign.CampaignBrand brand;

  @Schema(description = "Client type", example = "DIRECT_ADVERTISER")
  private Campaign.ClientType clientType;

  @Schema(description = "Agency associated with the campaign")
  private Campaign.CampaignAgency agency;

  @Schema(description = "Company ID", example = "company123")
  private String companyId;

  @Schema(description = "Country ID for the campaign", example = "US")
  private String countryId;

  @Schema(
      description = "Current company ID reference",
      example = "93b4f544-e657-4eb7-872e-7b9c1d0e0197")
  private String currentCompanyId;

  @Schema(description = "Current company name reference", example = "QA Internal")
  private String currentCompanyName;

  @Schema(description = "Selected inventory count", example = "450")
  private Long inventoryCount;

  @Schema(description = "Campaign goals")
  private Goals goals;

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

  @Schema(description = "Campaign optimization settings")
  private Optimization optimization;

  @Schema(description = "Skip recommendation processing for this campaign", example = "false")
  private Boolean skipRecommendation;

  @Schema(description = "Whether the campaign's inventory prices have been negotiated/accepted")
  private Boolean isNegotiated;

  @Schema(description = "Demand Side Platform name or identifier", example = "DV360")
  private String dsp;

  @Schema(description = "Data partition of the plan (\"live\" or \"demo\")", example = "live")
  private String dataMode;

  @Schema(description = "Campaign performance forecast snapshot")
  private CampaignForecastDTO performance;

  @Schema(description = "Campaign creation timestamp", example = "2024-01-15T10:30:00")
  private LocalDateTime createdAt;

  @Schema(description = "Campaign last update timestamp", example = "2024-01-15T14:45:00")
  private LocalDateTime updatedAt;

  // Nested classes for complex JSON fields
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Campaign goals")
  public static class Goals {
    @Schema(description = "Goal type", example = "IMPRESSIONS")
    private Campaign.Goals.GoalType goalType;

    @Schema(description = "Target name for custom goals", example = "Brand Awareness")
    private String targetName;

    @Schema(description = "Target value", example = "1000000.0")
    private Double targetValue;

    @Schema(description = "Goal type name", example = "Impressions")
    private String typeName;
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
      @Schema(description = "Digital OOH venue type string values")
      private List<String> digitalOoh;

      @Schema(description = "Classic OOH venue type string values")
      private List<String> classicOoh;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Geofencing information")
    public static class Geofencing {
      @Schema(description = "Geometric shapes for targeting")
      private List<Geometry> geometries;

      @Schema(description = "Location points for targeting")
      private List<Location> locations;

      @Data
      @Builder
      @NoArgsConstructor
      @AllArgsConstructor
      @Schema(description = "Geometric shape for geofencing")
      public static class Geometry {
        @Schema(description = "Name of the geometry", example = "Downtown Area")
        private String name;

        @Schema(description = "Geometry type", example = "Polygon")
        private String type;

        @Schema(description = "GeoJSON coordinates")
        private List<List<Double>> coordinates;

        @Schema(description = "Whether this is an inclusion or exclusion zone", example = "true")
        private boolean isIncluded;

        @Schema(description = "Geometry type", example = "[\"p1\", \"p2\"]")
        private List<String> poi;

        @Schema(
            description = "metadata",
            example = "{\"additionalProp1\": \"m1\",\"additionalProp2\": \"m2}")
        private Map<String, String> metadata;
      }

      @Data
      @Builder
      @NoArgsConstructor
      @AllArgsConstructor
      @Schema(description = "Location point for geofencing")
      public static class Location {
        @Schema(description = "Name of the location", example = "Times Square")
        private String name;

        @Schema(description = "Latitude", example = "40.7128")
        private Double lat;

        @Schema(description = "Longitude", example = "-74.0060")
        private Double lng;

        @Schema(description = "Radius in meters", example = "1000.0")
        private Double radius;

        @Schema(description = "Address description", example = "Times Square, New York")
        private String address;

        @Schema(description = "Whether this is an inclusion or exclusion zone", example = "true")
        private boolean isIncluded;

        @Schema(description = "poi", example = "[\"p11\", \"p22\"]")
        private List<String> poi;

        @Schema(
            description = "metadata",
            example = "{\"additionalProp1\": \"m11\",\"additionalProp2\": \"m22\"}")
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
            .status(this.status)
            .budget(this.budget)
            .currency(this.currency)
            .startDate(this.startDate)
            .endDate(this.endDate)
            .userId(this.userId)
            .brand(this.brand)
            .clientType(this.clientType)
            .agency(this.agency)
            .companyId(this.companyId)
            .budgetAllocation(this.budgetAllocation)
            .skipRecommendation(this.skipRecommendation);

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
                              .poi(geo.getPoi())
                              .metadata(geo.metadata)
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
                              .poi(loc.getPoi())
                              .metadata(loc.metadata)
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

    Campaign campaign = builder.build();
    campaign.setId(this.id);
    campaign.setCreatedAt(this.createdAt);
    campaign.setUpdatedAt(this.updatedAt);
    return campaign;
  }

  /**
   * Maps a Campaign entity to this DTO
   *
   * @param campaign Campaign entity
   * @return CampaignResponseDTO
   */
  public static CampaignResponseDTO mapToDto(Campaign campaign) {
    CampaignResponseDTO.CampaignResponseDTOBuilder builder =
        CampaignResponseDTO.builder()
            .id(campaign.getId())
            .name(campaign.getName())
            .planNumber(campaign.getPlanNumber())
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
            .currentCompanyId(campaign.getCurrentCompanyId())
            .currentCompanyName(campaign.getCurrentCompanyName())
            .budgetAllocation(campaign.getBudgetAllocation())
            .mediaChannels(campaign.getMediaChannels())
            .skipRecommendation(campaign.getSkipRecommendation())
            .isNegotiated(campaign.getIsNegotiated())
            .performance(campaign.getPerformance())
            .createdAt(campaign.getCreatedAt())
            .updatedAt(campaign.getUpdatedAt())
            .dsp(campaign.getDsp())
            .dataMode(campaign.getDataMode() == null ? "live" : campaign.getDataMode());

    // Map Goals
    Optional.of(campaign)
        .map(Campaign::getGoals)
        .ifPresent(
            srcGoals ->
                builder.goals(
                    Goals.builder()
                        .goalType(srcGoals.getGoalType())
                        .targetName(srcGoals.getTargetName())
                        .targetValue(srcGoals.getTargetValue())
                        .typeName(srcGoals.getGoalType() != null ? srcGoals.getTypeName() : null)
                        .build()));

    // Map Targeting
    if (campaign.getTargeting() != null) {
      Targeting.TargetingBuilder targetingBuilder =
          Targeting.builder()
              .demographics(campaign.getTargeting().getDemographics())
              .signals(campaign.getTargeting().getSignals())
              .programmaticOnly(campaign.getTargeting().getProgrammaticOnly())
              .inventoryCluster(campaign.getTargeting().getInventoryCluster());

      if (campaign.getTargeting().getVenueTypes() != null) {
        targetingBuilder.venueTypes(
            Targeting.VenueTypes.builder()
                .digitalOoh(campaign.getTargeting().getVenueTypes().getDigitalOoh())
                .classicOoh(campaign.getTargeting().getVenueTypes().getClassicOoh())
                .build());
      }

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

  public CampaignResponseDTO withInventoryCount(long count) {
    this.inventoryCount = count;
    return this;
  }
}
