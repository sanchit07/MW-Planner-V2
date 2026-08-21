package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for geofencing in external payload */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Geofencing configuration")
public class ExternalGeofencingDTO {

  @JsonProperty("geometrics")
  @Schema(description = "List of geometries")
  private List<GeometryDTO> geometrics;

  @JsonProperty("locations")
  @Schema(description = "List of locations")
  private List<LocationDTO> locations;
}
