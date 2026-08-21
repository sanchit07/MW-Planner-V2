package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for traffic signal */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Traffic signal configuration")
public class TrafficDTO {

  @JsonProperty("density")
  @Schema(description = "Traffic density", example = "[\"medium\", \"high\"]")
  private List<String> density;
}
