package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for targeting in external payload */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Targeting configuration")
public class ExternalTargetingDTO {

  @JsonProperty("demographics")
  @Schema(description = "Demographics targeting")
  private DemographicsDTO demographics;

  @JsonProperty("venueTypes")
  @Schema(description = "List of venue types")
  private List<String> venueTypes;

  @JsonProperty("geofencing")
  @Schema(description = "Geofencing configuration")
  private ExternalGeofencingDTO geofencing;
}
