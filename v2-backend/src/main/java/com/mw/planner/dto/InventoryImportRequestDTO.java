package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

/** DTO for inventory import request containing CSV file and country name for validation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for inventory CSV import verification")
public class InventoryImportRequestDTO {

  @NotNull(message = "CSV file is required")
  @Schema(description = "CSV file containing inventory_id column", required = true)
  private MultipartFile csvFile;

  @NotBlank(message = "Country name is required")
  @Schema(
      description = "Country name for inventory validation",
      example = "United States",
      required = true)
  private String countryName;
}
