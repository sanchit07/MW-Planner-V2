package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for campaign Media Plan Targeting Strategy details")
public class AudienceDemographicsTargetingStrategyDTO {
  private List<String> ageGroups;
  private List<String> incomeLevel;
  private List<String> interests;
  private List<String> lifestyle;
}
