package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Country market details with inventory count and impression information")
public class CountryMarketDetailsDTO {

  @Schema(description = "ID", example = "country123")
  private String id;

  @Schema(description = "Country ID", example = "US")
  private String countryId;

  @Schema(description = "Country name", example = "United States")
  private String countryName;

  @Schema(description = "Country population", example = "331000000")
  private Long population;

  @Schema(description = "Total number of inventories in this country", example = "1250")
  private Long inventoryCount;

  @Schema(
      description = "Inventory counts grouped by classification",
      example = "{\"Digital\": 850, \"Classic\": 320, \"Transit\": 80}")
  private Map<String, Long> inventoryCountByClassification;

  @Schema(description = "Total impressions for this country", example = "5000000")
  private Long impressions;
}
