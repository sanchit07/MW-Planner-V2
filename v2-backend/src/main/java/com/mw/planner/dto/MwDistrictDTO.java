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
@Schema(description = "District data from MovingWalls API")
public class MwDistrictDTO {

  @JsonProperty("districtId")
  @Schema(description = "District identifier", example = "al-hasa")
  private String districtId;

  @JsonProperty("name")
  @Schema(description = "District name", example = "Al Hasa")
  private String name;

  @JsonProperty("nameJa")
  @Schema(description = "District name in Japanese", example = "Al Hasa")
  private String nameJa;

  @JsonProperty("type")
  @Schema(description = "District type", example = "District")
  private String type;

  @JsonProperty("latitude")
  @Schema(description = "District latitude", example = "30.82622")
  private Double latitude;

  @JsonProperty("longitude")
  @Schema(description = "District longitude", example = "35.97653")
  private Double longitude;

  @JsonProperty("zoom")
  @Schema(description = "Map zoom level", example = "12")
  private Integer zoom;

  @JsonProperty("population")
  @Schema(description = "District population", example = "10243")
  private Long population;

  @JsonProperty("iso")
  @Schema(description = "ISO code", example = "JO")
  private String iso;

  @JsonProperty("locale")
  @Schema(description = "District locale", example = "en")
  private String locale;

  @JsonProperty("trDistrictName")
  @Schema(description = "Translated district name")
  private String trDistrictName;

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

  @JsonProperty("state")
  @Schema(description = "State information")
  private State state;

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
  @Schema(description = "State information for the district")
  public static class State {
    @JsonProperty("id")
    @Schema(description = "State ID", example = "640ae176afc7ae337f75df0b")
    private String id;

    @JsonProperty("stateId")
    @Schema(description = "State identifier", example = "tafilah")
    private String stateId;

    @JsonProperty("name")
    @Schema(description = "State name", example = "Tafilah")
    private String name;

    @JsonProperty("nameJa")
    private String nameJa;

    @JsonProperty("type")
    private String type;

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

    @JsonProperty("createdDate")
    private LocalDateTime createdDate;

    @JsonProperty("lastModifiedDate")
    private LocalDateTime lastModifiedDate;

    @JsonProperty("country")
    private Country country;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  @Schema(description = "Country information for the district")
  public static class Country {
    @JsonProperty("id")
    @Schema(description = "Country ID", example = "573aa8c388041e1667e33638")
    private String id;

    @JsonProperty("countryId")
    @Schema(description = "Country identifier", example = "jordan")
    private String countryId;

    @JsonProperty("name")
    @Schema(description = "Country name", example = "Jordan")
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
