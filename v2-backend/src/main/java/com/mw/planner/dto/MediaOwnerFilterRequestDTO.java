package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for filtering selected inventory / forecast by media owner. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for filtering selected inventory / forecast by media owner")
public class MediaOwnerFilterRequestDTO {

  @Schema(
      description =
          "List of media owner IDs to filter by. If null or empty, no media-owner filtering is"
              + " applied and behavior matches the GET endpoint.",
      example = "[\"owner1\", \"owner2\"]")
  private List<String> mediaOwnerIds;
}
