package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for location in geofencing */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Location for geofencing")
public class LocationDTO {

  @JsonProperty("name")
  @Schema(description = "Location name", example = "New York Metro")
  private String name;

  @JsonProperty("lat")
  @Schema(description = "Latitude", example = "40.7128")
  private Double lat;

  @JsonProperty("lng")
  @Schema(description = "Longitude", example = "-74.006")
  private Double lng;

  @JsonProperty("radius")
  @Schema(description = "Radius in meters", example = "80000")
  private Double radius;

  @JsonProperty("address")
  @Schema(description = "Address", example = "New York, NY, USA")
  private String address;

  @JsonProperty("metadata")
  @Schema(description = "Metadata")
  private Map<String, String> metadata;

  @JsonProperty("included")
  @Schema(description = "Whether this is included", example = "true")
  private Boolean included;
}
