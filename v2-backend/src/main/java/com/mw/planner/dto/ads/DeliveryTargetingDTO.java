package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for delivery targeting */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Delivery targeting configuration")
public class DeliveryTargetingDTO {

  @JsonProperty("signals")
  @Schema(description = "Signals configuration")
  private SignalsDTO signals;
}
