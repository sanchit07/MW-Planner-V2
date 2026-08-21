package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for AQI signal */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AQI signal configuration")
public class AqiDTO {

  @JsonProperty("min")
  @Schema(description = "Minimum AQI", example = "0")
  private Integer min;

  @JsonProperty("max")
  @Schema(description = "Maximum AQI", example = "100")
  private Integer max;
}
