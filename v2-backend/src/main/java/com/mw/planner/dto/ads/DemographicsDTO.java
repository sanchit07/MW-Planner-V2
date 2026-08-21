package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for demographics targeting */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Demographics targeting details")
public class DemographicsDTO {

  @JsonProperty("ageGroups")
  @Schema(description = "List of age groups", example = "[\"18-24\", \"25-34\"]")
  private List<String> ageGroups;

  @JsonProperty("genders")
  @Schema(description = "List of genders", example = "[\"male\", \"female\"]")
  private List<String> genders;

  @JsonProperty("incomeGroups")
  @Schema(description = "List of income groups", example = "[\"middle\", \"upper-middle\"]")
  private List<String> incomeGroups;

  @JsonProperty("interests")
  @Schema(description = "List of interests", example = "[\"sports\", \"fitness\"]")
  private List<String> interests;

  @JsonProperty("audienceBehaviour")
  @Schema(
      description = "List of audience behaviors",
      example = "[\"active_shoppers\", \"fitness_enthusiasts\"]")
  private List<String> audienceBehaviour;
}
