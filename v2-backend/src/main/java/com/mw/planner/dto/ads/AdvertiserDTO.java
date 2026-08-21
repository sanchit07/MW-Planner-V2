package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for advertiser information */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Advertiser details")
public class AdvertiserDTO {

  @JsonProperty("id")
  @Schema(description = "Advertiser ID", example = "adv-adidas-001")
  private String id;

  @JsonProperty("seatId")
  @Schema(description = "Seat ID", example = "seat-adidas")
  private String seatId;

  @JsonProperty("name")
  @Schema(description = "Advertiser name", example = "Adidas AG")
  private String name;
}
