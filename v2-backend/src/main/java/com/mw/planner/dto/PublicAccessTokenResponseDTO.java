package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing the generated public access token")
public class PublicAccessTokenResponseDTO {

  @Schema(
      description = "Generated public access token ID",
      example = "b6a7c9a2-9c2f-4c8b-9e33-1a6e8d123456")
  private String publicToken;
}
