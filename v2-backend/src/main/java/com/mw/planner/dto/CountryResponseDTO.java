package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for country information")
public class CountryResponseDTO {

  @Schema(description = "Unique country identifier", example = "country_123456")
  private String id;

  @Schema(description = "Country ID", example = "US")
  private String countryId;

  @Schema(description = "Country name", example = "United States")
  private String name;

  @Schema(description = "Country latitude coordinate", example = "39.8283")
  private Double latitude;

  @Schema(description = "Country longitude coordinate", example = "-98.5795")
  private Double longitude;

  @Schema(description = "Map zoom level for the country", example = "4")
  private Integer zoom;

  @Schema(
      description = "Media owner terms and conditions URL",
      example = "https://example.com/terms")
  private String mediaOwnerTermsAndConditions;

  @Schema(
      description = "Buyer terms and conditions URL",
      example = "https://example.com/buyer-terms")
  private String buyerTermsAndConditions;

  @Schema(description = "Country population", example = "331000000")
  private Long population;

  @Schema(description = "ISO country code", example = "USA")
  private String iso;

  @Schema(description = "Postal code format", example = "#####-####")
  private String postalformat;

  @Schema(description = "Postal code name", example = "ZIP Code")
  private String postalname;

  @Schema(description = "Whether the country is active", example = "true")
  private Boolean active;

  @Schema(description = "Country dialing code", example = "+1")
  private String dialingCode;

  @Schema(description = "Country timezone", example = "America/New_York")
  private String timezone;

  @Schema(description = "Tax information for the country")
  private Tax tax;

  @JsonProperty("createdAt")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Schema(description = "Country creation timestamp", example = "2024-01-15 10:30:00")
  private LocalDateTime createdAt;

  @JsonProperty("updatedAt")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Schema(description = "Country last update timestamp", example = "2024-01-15 14:45:00")
  private LocalDateTime updatedAt;

  /** Tax information for the country */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Tax {
    @Schema(description = "Tax label", example = "VAT")
    private String label;

    @Schema(description = "Tax percentage", example = "8.5")
    private Double percent;
  }
}
