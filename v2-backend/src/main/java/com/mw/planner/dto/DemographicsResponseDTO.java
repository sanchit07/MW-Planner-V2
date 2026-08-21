package com.mw.planner.dto;

import com.mw.planner.enums.DemographicsType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for demographic information")
public class DemographicsResponseDTO {

  @Schema(description = "Demographic ID", example = "demo123")
  private String id;

  @NotNull(message = "validation.demo_type_required")
  @Schema(
      description = "Demographic type",
      example = "AGE",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private DemographicsType demoType;

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

  @Schema(description = "Country ID", example = "US")
  private String countryId;

  @Schema(description = "User who created the demographic", example = "user123")
  private String createdBy;

  @Schema(description = "User who last modified the demographic", example = "user456")
  private String lastModifiedBy;

  @Schema(description = "Creation timestamp", example = "2024-01-15T10:30:00")
  private LocalDateTime createdAt;

  @Schema(description = "Last update timestamp", example = "2024-01-15T14:45:00")
  private LocalDateTime updatedAt;
}
