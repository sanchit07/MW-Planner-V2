package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for geometry in geofencing */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Geometry for geofencing")
public class GeometryDTO {

  @JsonProperty("type")
  @Schema(
      description = "Geometry type",
      example = "Polygon",
      allowableValues = {"Polygon", "MultiPolygon"})
  private String type;

  @JsonProperty("coordinates")
  @Schema(description = "GeoJSON coordinates")
  private List<List<List<Double>>> coordinates;

  @JsonProperty("included")
  @Schema(description = "Whether this is included", example = "true")
  private Boolean included;
}
