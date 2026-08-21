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
@Schema(description = "Inventory mapping entry showing distance from a geo-import location")
public class InventoriesMappingResponseDTO {

  @Schema(description = "Geo-import location name", example = "Central Park")
  private String name;

  @Schema(description = "Geo-import location latitude", example = "1.3352566")
  private String latitude;

  @Schema(description = "Geo-import location longitude", example = "103.963586")
  private String longitude;

  @Schema(description = "Inventory (billboard) name", example = "Leher CHSL|bf2b96e5-909a-465")
  private String billboardName;

  @Schema(description = "Inventory reference ID", example = "IND-ADO-D-00000-88842")
  private String referenceId;

  @Schema(
      description = "Distance in meters from the geo-import location to the inventory",
      example = "125.5")
  private Double distanceMeters;

  @Schema(description = "State name from inventory location", example = "Maharashtra")
  private String stateName;

  @Schema(description = "District/city name from inventory location", example = "Mumbai Suburban")
  private String districtName;
}
