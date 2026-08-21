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
@Schema(description = "Result of the one-off numeric plan-number backfill")
public class PlanNumberBackfillResultDTO {

  @Schema(description = "Campaigns examined (had a missing planNumber)", example = "142")
  private long processed;

  @Schema(
      description = "Campaigns that actually got a planNumber assigned in this run",
      example = "142")
  private long assigned;
}
