package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for refreshed access token")
public class RefreshTokenResponse {

  @JsonProperty("access_token")
  @Schema(description = "New access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
  private String accessToken;

  @JsonProperty("expires_in")
  @Schema(description = "Token expiration time in seconds", example = "3600")
  private Long expiresIn;
}
