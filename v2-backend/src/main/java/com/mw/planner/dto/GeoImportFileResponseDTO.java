package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mw.planner.domain.CampaignGeoImportFile;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for geo coordinates import file information")
public class GeoImportFileResponseDTO {

  @Schema(description = "Unique geo import file identifier", example = "geo_import_123456")
  private String id;

  @Schema(description = "Name of the uploaded CSV file", example = "geo_coordinates.csv")
  private String fileName;

  @Schema(description = "Country name", example = "United States")
  private String countryName;

  @Schema(description = "Company ID", example = "company123")
  private String companyId;

  @Schema(description = "List of geo coordinate details")
  private List<GeoDetailsDTO> geoDetails;

  @Schema(description = "Count of geo coordinates in the import", example = "150")
  private Integer countOfCoordinates;

  @Schema(description = "User who created the import", example = "user@example.com")
  private String createdBy;

  @Schema(description = "User who last modified the import", example = "user@example.com")
  private String lastModifiedBy;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Schema(description = "Import file creation timestamp", example = "2024-01-15 10:30:00")
  private LocalDateTime createdAt;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Schema(description = "Import file last update timestamp", example = "2024-01-15 10:30:00")
  private LocalDateTime updatedAt;

  /** Geo details information */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Geo coordinate details")
  public static class GeoDetailsDTO {
    @Schema(description = "Location name", example = "Times Square")
    private String locationName;

    @Schema(description = "Radius in meters", example = "1000")
    private String radius;

    @Schema(description = "Latitude", example = "40.7128")
    private String latitude;

    @Schema(description = "Longitude", example = "-74.0060")
    private String longitude;

    @Schema(description = "Site type", example = "OUTDOOR")
    private String siteType;
  }

  /**
   * Convert CampaignGeoImportFile entity to list of GeoDetailsDTO.
   *
   * @param geoImportFile CampaignGeoImportFile entity
   * @return List of GeoDetailsDTO
   */
  public static List<GeoDetailsDTO> toGeoDetailsList(CampaignGeoImportFile geoImportFile) {
    if (geoImportFile == null || geoImportFile.getGeoDetails() == null) {
      return new ArrayList<>();
    }

    return geoImportFile.getGeoDetails().stream()
        .map(
            geoDetail ->
                GeoDetailsDTO.builder()
                    .locationName(geoDetail.getLocationName())
                    .radius(geoDetail.getRadius())
                    .latitude(geoDetail.getLatitude())
                    .longitude(geoDetail.getLongitude())
                    .siteType(geoDetail.getSiteType())
                    .build())
        .collect(Collectors.toList());
  }
}
