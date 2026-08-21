package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Root DTO for ADS campaign submission request */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "ADS campaign submission request")
public class AdsCampaignRequestDTO {

  @JsonProperty("payloadType")
  @Schema(description = "Payload type", example = "DIRECT_PUBLISHER_SPLIT")
  private String payloadType;

  @JsonProperty("externalPayload")
  @Schema(description = "External payload containing campaign and inventories")
  private ExternalPayloadDTO externalPayload;

  @JsonProperty("options")
  @Schema(description = "Options for ADS submission")
  private OptionsDTO options;
}
