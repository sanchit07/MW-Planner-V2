package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for footfall signal */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Footfall signal configuration")
public class FootfallDTO {

  @JsonProperty("min")
  @Schema(description = "Minimum footfall", example = "500")
  private Integer min;

  @JsonProperty("peakHours")
  @Schema(description = "Peak hours only", example = "true")
  private Boolean peakHours;
}
