package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Venue item information")
public class VenueItemDTO {

  @Schema(description = "OpenOOH enumeration ID", example = "101")
  private Integer enumerationId;

  @Schema(description = "Hierarchy tier: 1=Parent, 2=Child, 3=Grandchild", example = "2")
  private Integer tier;

  @Schema(description = "Venue name", example = "Airports")
  private String name;

  @Schema(description = "Venue definition", example = "Signage located throughout terminals...")
  private String definition;

  @Schema(description = "Deprecated string identifier (OpenOOH v1.1)", example = "transit.airports")
  private String stringValue;

  @Schema(description = "Child venue items")
  private List<VenueItemDTO> children;
}
