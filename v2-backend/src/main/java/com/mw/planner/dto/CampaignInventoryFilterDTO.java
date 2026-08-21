package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.mw.planner.domain.Campaign;
import com.mw.planner.enums.ProgrammaticDealType;
import com.mw.planner.enums.ProgrammaticSupport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for filtering campaign inventories based on various criteria. Supports filtering by location,
 * demographics, geofencing, and inventory properties.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Filter criteria for campaign inventory search")
public class CampaignInventoryFilterDTO {

  @Schema(description = "Name to filter by (case-insensitive partial match)", example = "billboard")
  private String name;

  @Schema(description = "List of countries to filter by", example = "[\"Singapore\", \"Japan\"]")
  private List<String> countries;

  @Schema(
      description = "Goal type for pricing filter criteria",
      example = "IMPRESSIONS",
      allowableValues = {"IMPRESSIONS", "REACH", "SOV", "ADPLAYS", "ATTRIBUTION", "OTHER"})
  private Campaign.Goals.GoalType goalType;

  @Schema(description = "List of states to filter by", example = "[\"jakarta\", \"tokyo\"]")
  private List<String> states;

  @Schema(description = "List of cities to filter by", example = "[\"jakarta\", \"shibuya\"]")
  private List<String> cities;

  @Schema(
      description = "Demographics targeting (same as campaign demographics)",
      example = "{\"age\": [\"18-24\", \"25-34\"], \"gender\": [\"MALE\"]}")
  private Map<String, List<String>> demographics;

  @Schema(description = "Geofencing information (same as campaign geofencing)")
  private Geofencing geofencing;

  @Schema(
      description = "List of media owner IDs to filter by",
      example = "[\"owner1\", \"owner2\"]")
  private List<String> mediaOwnerIds;

  @JsonAlias("classifications")
  @Schema(
      description = "List of inventory types to filter by",
      example = "[\"Digital\", \"Classic\"]")
  private List<String> inventoryTypes;

  @Schema(
      description = "Type of the inventory",
      example =
          "Airport | Bus | Cinema | Commercial Fleet | OOH | Rail & Metro | Retail | Taxi & Rideshare | Transit Statio")
  private List<String> types;

  @Schema(description = "List of modes of booking to filter by", example = "[\"spot\", \"loop\"]")
  private List<String> bookingMode;

  @Schema(description = "Size filter criteria")
  private List<String> sizes;

  @Schema(description = "List of tags to filter by", example = "[\"premium\", \"high-traffic\"]")
  private List<String> tags;

  @Schema(
      description = "List of formats to filter by",
      example = "[\"LANDSCAPE\", \"PORTRAIT\", \"SQUARE\"]")
  private List<String> formats;

  @Schema(
      description = "List of venue types to filter by (string values)",
      example = "[\"Outdoor\", \"Billboards\", \"Roadside\"]")
  private List<String> venueTypes;

  @Schema(description = "Venue types split by media channel for classification-aware filtering")
  private VenueTypeFilter venueTypeFilter;

  @Schema(
      description =
          "Venue type IDs split by media channel — preferred over venueTypeFilter when available")
  private VenueTypeIdFilter venueTypeIdFilter;

  @Schema(description = "List of environment to filter by", example = "[\"OUTDOOR\", \"INDOOR\"]")
  private List<String> environments;

  @Schema(
      description = "List of groups to filter by",
      example = "[\"TRAVEL\", \"WORK\", \"EDUCATION\"]")
  private List<String> groups;

  @Schema(
      description =
          "List of inventory IDs to exclude from results (for filtering out selected inventories)",
      example = "[\"inventory1\", \"inventory2\"]")
  private List<String> excludeInventoryIds;

  @Schema(
      description = "Programmatic support filter: YES = has deal types, NO = none, ALL = no filter",
      example = "YES")
  private ProgrammaticSupport programmaticSupport;

  @Schema(
      description =
          "List of programmatic deal types to filter by (e.g. guaranteed, preferred_deal)",
      example = "[\"guaranteed\", \"preferred_deal\"]")
  private List<ProgrammaticDealType> dealTypes;

  @Schema(
      description = "Cinema genres to filter by (any-match against cinemaFields.genres)",
      example = "[\"Action\", \"Drama\"]")
  private List<String> cinemaGenres;

  @Schema(
      description = "Cinema ratings to filter by (any-match against cinemaFields.ratings)",
      example = "[\"U\", \"UA\"]")
  private List<String> cinemaRatings;

  @Schema(
      description = "Cinema operator IDs to filter by (matched against cinemaFields.operatorId)",
      example = "[\"mo-cinema-pvr-inox\"]")
  private List<String> cinemaOperatorIds;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Venue type filter split by media channel classification")
  public static class VenueTypeFilter {
    @Schema(
        description =
            "Digital OOH venue string values — matched against inventories with classification=Digital")
    private List<String> digitalOoh;

    @Schema(
        description =
            "Classic OOH venue string values — matched against inventories with classification=Classic")
    private List<String> classicOoh;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(
      description = "Venue type ID filter split by media channel — uses venueTypeIds on inventory")
  public static class VenueTypeIdFilter {
    @Schema(description = "Digital OOH venue taxonomy IDs matched against classification=Digital")
    private List<String> digitalOoh;

    @Schema(description = "Classic OOH venue taxonomy IDs matched against classification=Classic")
    private List<String> classicOoh;
  }

  @Schema(
      description =
          "Campaign start date (ISO yyyy-MM-dd). Optional; only applied together with endDate.",
      example = "2026-01-01")
  private LocalDate startDate;

  @Schema(
      description =
          "Campaign end date (ISO yyyy-MM-dd). Optional; only applied together with startDate.",
      example = "2026-01-03")
  private LocalDate endDate;

  /** Geofencing information (same structure as campaign geofencing) */
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
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Location point for geofencing")
    public static class Location {
      @Schema(description = "Name of the location", example = "Times Square")
      private String name;

      @Schema(description = "Latitude")
      private Double lat;

      @Schema(description = "Longitude")
      private Double lng;

      @Schema(description = "Radius in meters")
      private Double radius;

      @Schema(description = "Address")
      private String address;

      @Schema(description = "Whether this is an inclusion or exclusion zone", example = "true")
      private boolean isIncluded;
    }
  }
}
