package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for signals in delivery targeting */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Signals configuration")
public class SignalsDTO {

  @JsonProperty("weather")
  @Schema(description = "Weather signal configuration")
  private WeatherDTO weather;

  @JsonProperty("traffic")
  @Schema(description = "Traffic signal configuration")
  private TrafficDTO traffic;

  @JsonProperty("aqi")
  @Schema(description = "AQI signal configuration")
  private AqiDTO aqi;

  @JsonProperty("footfall")
  @Schema(description = "Footfall signal configuration")
  private FootfallDTO footfall;
}
