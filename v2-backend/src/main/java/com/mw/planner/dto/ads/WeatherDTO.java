package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for weather signal */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Weather signal configuration")
public class WeatherDTO {

  @JsonProperty("conditions")
  @Schema(description = "Weather conditions", example = "[\"sunny\", \"partly_cloudy\", \"clear\"]")
  private List<String> conditions;

  @JsonProperty("temperature")
  @Schema(description = "Temperature range")
  private TemperatureDTO temperature;

  /** Temperature DTO */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Temperature range")
  public static class TemperatureDTO {
    @JsonProperty("min")
    @Schema(description = "Minimum temperature", example = "45")
    private Integer min;

    @JsonProperty("max")
    @Schema(description = "Maximum temperature", example = "90")
    private Integer max;

    @JsonProperty("unit")
    @Schema(description = "Temperature unit", example = "fahrenheit")
    private String unit;
  }
}
