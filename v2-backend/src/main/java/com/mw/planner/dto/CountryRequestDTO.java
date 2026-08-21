package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for creating or updating a country")
public class CountryRequestDTO {

  @NotBlank(message = "validation.country_id_required")
  @Size(max = 50, message = "validation.country_id_size")
  @Schema(description = "Unique country identifier", example = "US")
  private String countryId;

  @NotBlank(message = "validation.country_name_required")
  @Size(max = 100, message = "validation.country_name_size")
  @Schema(description = "Country name", example = "United States")
  private String name;

  @NotNull(message = "validation.latitude_required")
  @Schema(description = "Country latitude coordinate", example = "39.8283")
  private Double latitude;

  @NotNull(message = "validation.longitude_required")
  @Schema(description = "Country longitude coordinate", example = "-98.5795")
  private Double longitude;

  @Positive(message = "validation.zoom_positive")
  @Schema(description = "Map zoom level for the country", example = "4")
  private Integer zoom;

  @Size(max = 1000, message = "validation.media_owner_terms_size")
  @Schema(
      description = "Media owner terms and conditions URL",
      example = "https://example.com/terms")
  private String mediaOwnerTermsAndConditions;

  @Size(max = 1000, message = "validation.buyer_terms_size")
  @Schema(
      description = "Buyer terms and conditions URL",
      example = "https://example.com/buyer-terms")
  private String buyerTermsAndConditions;

  @PositiveOrZero(message = "validation.population_positive_or_zero")
  @Schema(description = "Country population", example = "331000000")
  private Long population;

  @NotBlank(message = "validation.iso_required")
  @Size(max = 3, message = "validation.iso_size")
  @Schema(description = "ISO country code", example = "USA")
  private String iso;

  @Size(max = 100, message = "validation.postal_format_size")
  @Schema(description = "Postal code format", example = "#####-####")
  private String postalformat;

  @Size(max = 100, message = "validation.postal_name_size")
  @Schema(description = "Postal code name", example = "ZIP Code")
  private String postalname;

  @NotNull(message = "validation.active_required")
  @Schema(description = "Whether the country is active", example = "true")
  private Boolean active;

  @Size(max = 10, message = "validation.dialing_code_size")
  @Schema(description = "Country dialing code", example = "+1")
  private String dialingCode;

  @Size(max = 50, message = "validation.timezone_size")
  @Schema(description = "Country timezone", example = "America/New_York")
  private String timezone;

  @Schema(description = "Tax information for the country")
  private Tax tax;

  /** Tax information for the country */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Tax {
    @Size(max = 100, message = "validation.tax_label_size")
    @Schema(description = "Tax label", example = "VAT")
    private String label;

    @PositiveOrZero(message = "validation.tax_percent_positive_or_zero")
    @Schema(description = "Tax percentage", example = "8.5")
    private Double percent;
  }
}
