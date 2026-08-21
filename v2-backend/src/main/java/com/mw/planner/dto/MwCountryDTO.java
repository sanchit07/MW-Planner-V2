package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Country data from MovingWalls API")
public class MwCountryDTO {

  @JsonProperty("countryId")
  @Schema(description = "Country identifier", example = "france")
  private String countryId;

  @JsonProperty("name")
  @Schema(description = "Country name", example = "France")
  private String name;

  @JsonProperty("nameJa")
  @Schema(description = "Country name in Japanese", example = "フランス")
  private String nameJa;

  @JsonProperty("latitude")
  @Schema(description = "Country latitude", example = "51.0344")
  private Double latitude;

  @JsonProperty("longitude")
  @Schema(description = "Country longitude", example = "2.618787")
  private Double longitude;

  @JsonProperty("zoom")
  @Schema(description = "Map zoom level", example = "5")
  private Integer zoom;

  @JsonProperty("mediaOwnerTermsAndConditions")
  @Schema(description = "Media owner terms and conditions URL")
  private String mediaOwnerTermsAndConditions;

  @JsonProperty("buyerTermsAndConditions")
  @Schema(description = "Buyer terms and conditions URL")
  private String buyerTermsAndConditions;

  @JsonProperty("swLat")
  @Schema(description = "Southwest latitude")
  private Double swLat;

  @JsonProperty("swLng")
  @Schema(description = "Southwest longitude")
  private Double swLng;

  @JsonProperty("seLat")
  @Schema(description = "Southeast latitude")
  private Double seLat;

  @JsonProperty("seLng")
  @Schema(description = "Southeast longitude")
  private Double seLng;

  @JsonProperty("neLat")
  @Schema(description = "Northeast latitude")
  private Double neLat;

  @JsonProperty("neLng")
  @Schema(description = "Northeast longitude")
  private Double neLng;

  @JsonProperty("nwLat")
  @Schema(description = "Northwest latitude")
  private Double nwLat;

  @JsonProperty("nwLng")
  @Schema(description = "Northwest longitude")
  private Double nwLng;

  @JsonProperty("population")
  @Schema(description = "Country population", example = "67150000")
  private Long population;

  @JsonProperty("iso")
  @Schema(description = "ISO country code", example = "FR")
  private String iso;

  @JsonProperty("postalformat")
  @Schema(description = "Postal code format", example = "99999")
  private String postalformat;

  @JsonProperty("postalname")
  @Schema(description = "Postal code name", example = "Code postal")
  private String postalname;

  @JsonProperty("geopc")
  @Schema(description = "Geopolitical code")
  private Integer geopc;

  @JsonProperty("active")
  @Schema(description = "Whether the country is active", example = "true")
  private Boolean active;

  @JsonProperty("dialingCode")
  @Schema(description = "Country dialing code", example = "+33")
  private String dialingCode;

  @JsonProperty("timezone")
  @Schema(description = "Country timezone")
  private String timezone;

  @JsonProperty("miDataSensorStatus")
  @Schema(description = "MI data sensor status")
  private String miDataSensorStatus;

  @JsonProperty("tax")
  @Schema(description = "Tax information")
  private Tax tax;

  @JsonProperty("region")
  @Schema(description = "Country region")
  private String region;

  @JsonProperty("locale")
  @Schema(description = "Country locale", example = "en")
  private String locale;

  @JsonProperty("trCountryName")
  @Schema(description = "Translated country name")
  private String trCountryName;

  @JsonProperty("id")
  @Schema(description = "External system ID")
  private String id;

  @JsonProperty("createdDate")
  @Schema(description = "Creation date")
  private LocalDateTime createdDate;

  @JsonProperty("lastModifiedBy")
  @Schema(description = "Last modified by user")
  private String lastModifiedBy;

  @JsonProperty("lastModifiedDate")
  @Schema(description = "Last modification date")
  private LocalDateTime lastModifiedDate;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Tax information for the country")
  public static class Tax {
    @JsonProperty("label")
    @Schema(description = "Tax label", example = "Tax")
    private String label;

    @JsonProperty("percent")
    @Schema(description = "Tax percentage", example = "20.0")
    private Double percent;
  }
}
