package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "State data from MovingWalls API")
public class MwStateDTO {

  @JsonProperty("stateId")
  @Schema(description = "State identifier", example = "alabama")
  private String stateId;

  @JsonProperty("name")
  @Schema(description = "State name", example = "Alabama")
  private String name;

  @JsonProperty("nameJa")
  @Schema(description = "State name in Japanese", example = "アラバマ")
  private String nameJa;

  @JsonProperty("type")
  @Schema(description = "State type", example = "Alabama")
  private String type;

  @JsonProperty("latitude")
  @Schema(description = "State latitude", example = "32.3182314")
  private Double latitude;

  @JsonProperty("longitude")
  @Schema(description = "State longitude", example = "-86.902298")
  private Double longitude;

  @JsonProperty("zoom")
  @Schema(description = "Map zoom level", example = "12")
  private Integer zoom;

  @JsonProperty("population")
  @Schema(description = "State population", example = "5269876")
  private Long population;

  @JsonProperty("iso")
  @Schema(description = "ISO code", example = "US")
  private String iso;

  @JsonProperty("locale")
  @Schema(description = "State locale", example = "en")
  private String locale;

  @JsonProperty("trStateName")
  @Schema(description = "Translated state name")
  private String trStateName;

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

  @JsonProperty("country")
  @Schema(description = "Country information")
  private Country country;

  // Additional fields that might be present in the API response
  @JsonProperty("swLat")
  private Double swLat;

  @JsonProperty("swLng")
  private Double swLng;

  @JsonProperty("seLat")
  private Double seLat;

  @JsonProperty("seLng")
  private Double seLng;

  @JsonProperty("neLat")
  private Double neLat;

  @JsonProperty("neLng")
  private Double neLng;

  @JsonProperty("nwLat")
  private Double nwLat;

  @JsonProperty("nwLng")
  private Double nwLng;

  @JsonProperty("miDataSensorStatus")
  private String miDataSensorStatus;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  @Schema(description = "Country information for the state")
  public static class Country {
    @JsonProperty("id")
    @Schema(description = "Country ID", example = "573aa8c388041e1667e336b3")
    private String id;

    @JsonProperty("countryId")
    @Schema(description = "Country identifier", example = "united-states")
    private String countryId;

    @JsonProperty("name")
    @Schema(description = "Country name", example = "United States")
    private String name;

    @JsonProperty("nameJa")
    private String nameJa;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("zoom")
    private Integer zoom;

    @JsonProperty("population")
    private Long population;

    @JsonProperty("iso")
    private String iso;

    @JsonProperty("locale")
    private String locale;

    @JsonProperty("active")
    private Boolean active;

    @JsonProperty("createdDate")
    private LocalDateTime createdDate;

    @JsonProperty("lastModifiedDate")
    private LocalDateTime lastModifiedDate;
  }
}
