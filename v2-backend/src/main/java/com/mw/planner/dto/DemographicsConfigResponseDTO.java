package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for demographics configuration")
public class DemographicsConfigResponseDTO {

  @Valid
  @Schema(
      description = "Age demographic items",
      example =
          "[{\"demoKey\": \"18_24\", \"name\": \"18-24\", \"description\": \"Young adults\"}]")
  private List<DemographicItemDTO> age;

  @Valid
  @Schema(
      description = "Gender demographic items",
      example = "[{\"demoKey\": \"male\", \"name\": \"Male\", \"description\": \"Male gender\"}]")
  private List<DemographicItemDTO> gender;

  @Valid
  @Schema(
      description = "Income demographic items",
      example =
          "[{\"demoKey\": \"income_high\", \"name\": \"High Income\", \"description\": \"High income earners\"}]")
  private List<DemographicItemDTO> income;
}
