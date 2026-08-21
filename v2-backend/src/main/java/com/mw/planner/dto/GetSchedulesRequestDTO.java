package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for retrieving schedules by inventory IDs */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for retrieving schedules by inventory IDs")
public class GetSchedulesRequestDTO {

  @Schema(
      description =
          "List of inventory IDs to retrieve schedules for. If empty or not provided, returns schedules for all inventories in the campaign.",
      example = "[\"inv123\", \"inv456\", \"inv789\"]")
  private List<String> inventoryIds;

  @Schema(
      description =
          "Optional inventory type filter. If provided, only schedules for inventories matching this type will be returned.",
      example = "CLASSIC")
  private String inventoryType;
}
