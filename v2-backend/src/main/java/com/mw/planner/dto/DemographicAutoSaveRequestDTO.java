package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for auto-saving demographic data")
public class DemographicAutoSaveRequestDTO {

  @NotBlank(message = "validation.demo_type_required")
  @Size(max = 50, message = "validation.demo_type_size")
  @Schema(
      description = "Demographic type",
      example = "age",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String demoType;

  @NotBlank(message = "validation.demo_key_required")
  @Size(max = 100, message = "validation.demo_key_size")
  @Schema(
      description = "Demographic key identifier",
      example = "18_24",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String demoKey;

  @NotBlank(message = "validation.name_required")
  @Size(max = 255, message = "validation.name_size")
  @Schema(
      description = "Demographic name",
      example = "18-24",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String name;

  @Size(max = 1000, message = "validation.description_size")
  @Schema(description = "Demographic description", example = "Young adults aged 18 to 24 years")
  private String description;
}
