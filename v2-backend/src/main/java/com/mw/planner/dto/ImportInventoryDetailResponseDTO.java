package com.mw.planner.dto;

import com.mw.planner.domain.Inventory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.geo.GeoJsonLineString;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

/**
 * DTO for inventory details in an import record. Contains only the required fields for import
 * inventory listing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Inventory detail response for import records")
public class ImportInventoryDetailResponseDTO {

  @Schema(description = "Inventory ID")
  private String id;

  @Schema(description = "Inventory name")
  private String inventoryName;

  @Schema(description = "Inventory reference ID")
  private String referenceId;

  @Schema(description = "Inventory type")
  private String type;

  @Schema(description = "Environment")
  private String environment;

  @Schema(description = "Location information")
  private LocationDTO location;

  @Schema(description = "Thumbnail URL")
  private String thumbnail;

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

  /**
   * Convert Inventory entity to ImportInventoryDetailResponseDTO.
   *
   * @param inventory Inventory entity
   * @return ImportInventoryDetailResponseDTO
   */
  public static ImportInventoryDetailResponseDTO fromEntity(Inventory inventory) {
    if (inventory == null) {
      return null;
    }

    LocationDTO locationDTO = null;
    if (inventory.getLocation() != null) {
      Inventory.Location loc = inventory.getLocation();
      LocationDTO.LocationCoordinatesDTO coordinatesDTO = null;
      if (loc.getLocationCoordinates() != null) {
        coordinatesDTO = convertCoordinates(loc.getLocationCoordinates());
      }

      locationDTO =
          LocationDTO.builder()
              .address(loc.getAddress())
              .country(loc.getCountry())
              .state(loc.getState())
              .city(loc.getCity())
              .zipCode(loc.getZipCode())
              .locationCoordinates(coordinatesDTO)
              .build();
    }

    return ImportInventoryDetailResponseDTO.builder()
        .id(inventory.getId())
        .inventoryName(inventory.getName())
        .referenceId(inventory.getReferenceId())
        .type(inventory.getType())
        .environment(inventory.getEnvironment())
        .location(locationDTO)
        .thumbnail(inventory.getThumbnailUrl())
        .size(inventory.getSize())
        .inventoryCluster(inventory.getInventoryCluster())
        .build();
  }

  /** Convert coordinates (GeoJsonPoint or GeoJsonLineString) to DTO */
  @SuppressWarnings("unchecked")
  private static LocationDTO.LocationCoordinatesDTO convertCoordinates(Object coordinates) {
    if (coordinates == null) return null;

    List<LocationDTO.LocationCoordinatesDTO.CoordinatePair> coordinatePairs = new ArrayList<>();

    if (coordinates instanceof Map) {
      // Handle Map-based GeoJSON (common when deserialized from MongoDB)
      Map<String, Object> coordMap = (Map<String, Object>) coordinates;
      String type = (String) coordMap.get("type");

      if ("Point".equals(type)) {
        Object coordsObj = coordMap.get("coordinates");
        if (coordsObj instanceof List) {
          List<Object> coordsList = (List<Object>) coordsObj;
          if (coordsList.size() >= 2) {
            Double longitude = getDoubleValue(coordsList.get(0));
            Double latitude = getDoubleValue(coordsList.get(1));
            if (longitude != null && latitude != null) {
              coordinatePairs.add(
                  LocationDTO.LocationCoordinatesDTO.CoordinatePair.builder()
                      .latitude(latitude)
                      .longitude(longitude)
                      .build());
              return LocationDTO.LocationCoordinatesDTO.builder()
                  .type("Point")
                  .coordinates(coordinatePairs)
                  .build();
            }
          }
        }
      } else if ("LineString".equals(type)) {
        Object coordsObj = coordMap.get("coordinates");
        if (coordsObj instanceof List) {
          List<Object> coordsList = (List<Object>) coordsObj;
          for (Object coordItem : coordsList) {
            if (coordItem instanceof List) {
              List<Object> pointCoords = (List<Object>) coordItem;
              if (pointCoords.size() >= 2) {
                Double longitude = getDoubleValue(pointCoords.get(0));
                Double latitude = getDoubleValue(pointCoords.get(1));
                if (longitude != null && latitude != null) {
                  coordinatePairs.add(
                      LocationDTO.LocationCoordinatesDTO.CoordinatePair.builder()
                          .latitude(latitude)
                          .longitude(longitude)
                          .build());
                }
              }
            }
          }
          if (!coordinatePairs.isEmpty()) {
            return LocationDTO.LocationCoordinatesDTO.builder()
                .type("LineString")
                .coordinates(coordinatePairs)
                .build();
          }
        }
      }
    } else if (coordinates instanceof GeoJsonPoint) {
      // Handle GeoJsonPoint directly
      GeoJsonPoint point = (GeoJsonPoint) coordinates;
      Double latitude = point.getY();
      Double longitude = point.getX();
      if (latitude != null && longitude != null) {
        coordinatePairs.add(
            LocationDTO.LocationCoordinatesDTO.CoordinatePair.builder()
                .latitude(latitude)
                .longitude(longitude)
                .build());
        return LocationDTO.LocationCoordinatesDTO.builder()
            .type("Point")
            .coordinates(coordinatePairs)
            .build();
      }
    } else if (coordinates instanceof GeoJsonLineString) {
      // Handle GeoJsonLineString directly
      GeoJsonLineString lineString = (GeoJsonLineString) coordinates;
      for (Point point : lineString.getCoordinates()) {
        Double latitude = point.getY();
        Double longitude = point.getX();
        if (latitude != null && longitude != null) {
          coordinatePairs.add(
              LocationDTO.LocationCoordinatesDTO.CoordinatePair.builder()
                  .latitude(latitude)
                  .longitude(longitude)
                  .build());
        }
      }
      if (!coordinatePairs.isEmpty()) {
        return LocationDTO.LocationCoordinatesDTO.builder()
            .type("LineString")
            .coordinates(coordinatePairs)
            .build();
      }
    }

    return null;
  }

  /** Helper method to safely extract Double value from Object */
  private static Double getDoubleValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    if (value instanceof String) {
      try {
        return Double.parseDouble((String) value);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }
}
