package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for pacing configuration */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Pacing configuration")
public class PacingDTO {

  @JsonProperty("type")
  @Schema(description = "Pacing type", example = "even")
  private String type;
}
