package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for geo coordinates import request containing file name, country name, and geo details. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for geo coordinates import")
public class GeoCoordinatesImportRequestDTO {

  @NotBlank(message = "validation.file_name_required")
  @Schema(
      description = "Name of the import file",
      example = "geo_import_file.csv",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String fileName;

  @Schema(description = "Country name for geo coordinates", example = "Singapore")
  private String countryName;

  @NotEmpty(message = "validation.geo_details_required")
  @Schema(description = "List of geo coordinate details")
  private List<GeoDetailDTO> geoDetails;

  /** Geo detail information */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Geo coordinate detail")
  public static class GeoDetailDTO {

    @Schema(description = "Location name", example = "Singapore")
    private String locationName;

    @Schema(description = "Radius in meters", example = "45")
    private String radius;

    @Schema(description = "Latitude", example = "1.3352566")
    private String latitude;

    @Schema(description = "Longitude", example = "103.963586")
    private String longitude;

    @Schema(description = "Site type", example = "location1")
    private String siteType;
  }
}
