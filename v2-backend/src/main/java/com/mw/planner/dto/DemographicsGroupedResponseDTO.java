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
@Schema(description = "Response DTO for grouped demographics data")
public class DemographicsGroupedResponseDTO {

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

  @Valid
  @Schema(
      description = "Interests demographic items",
      example =
          "[{\"demoKey\": \"interest_sports\", \"name\": \"Sports\", \"description\": \"Sports enthusiasts\"}]")
  private List<DemographicItemDTO> interests;

  @Valid
  @Schema(
      description = "Behavior demographic items",
      example =
          "[{\"demoKey\": \"behavior_online_shopper\", \"name\": \"Online Shopper\", \"description\": \"Frequent online shoppers\"}]")
  private List<DemographicItemDTO> behavior;

  @Valid
  @Schema(
      description = "Venue items",
      example =
          "[{\"name\": \"Shopping Mall\", \"venueKey\": \"mall_001\", \"description\": \"Large shopping mall\"}]")
  private List<VenueItemDTO> venues;
}
