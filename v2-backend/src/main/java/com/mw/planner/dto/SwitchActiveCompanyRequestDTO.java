package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for switching active company")
public class SwitchActiveCompanyRequestDTO {

  @NotBlank(message = "validation.active_company_id_required")
  @Size(max = 50, message = "validation.active_company_id_size")
  @Schema(description = "ID of the company to switch to", example = "comp_123456")
  private String activeCompanyId;
}
