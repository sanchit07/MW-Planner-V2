package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for options in ADS request */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Options for ADS submission")
public class OptionsDTO {

  @JsonProperty("source")
  @Schema(description = "Source system", example = "external-planning-tool")
  private String source;
}
