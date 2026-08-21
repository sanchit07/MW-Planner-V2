package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for market selection */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Market selection details")
public class MarketSelectionDTO {

  @JsonProperty("country")
  @Schema(description = "Country code", example = "US")
  private String country;

  @JsonProperty("currency")
  @Schema(description = "Currency code", example = "USD")
  private String currency;

  @JsonProperty("region")
  @Schema(description = "Region", example = "National")
  private String region;
}
