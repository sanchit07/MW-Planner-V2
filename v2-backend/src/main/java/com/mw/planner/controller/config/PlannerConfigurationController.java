package com.mw.planner.controller.config;

import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.config.PlannerConfigurationDTO;
import com.mw.planner.service.config.PlannerConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/config/settings/{companyId}")
@Tag(
    name = "Planner Configuration",
    description = "Tenant-scoped platform configuration (general/terminology/targeting/etc.)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class PlannerConfigurationController {

  private final PlannerConfigurationService plannerConfigurationService;

  @GetMapping
  @PreAuthorize("hasRole('planner:config:read')")
  @Operation(
      summary = "Get tenant configuration",
      description =
          "Returns the company's saved configuration merged with platform defaults for any unset section.")
  public ApiResponse<PlannerConfigurationDTO> getConfiguration(@PathVariable String companyId) {
    return ApiResponse.success(plannerConfigurationService.getForCompany(companyId));
  }

  @PutMapping
  @PreAuthorize("hasRole('planner:config:update')")
  @Operation(
      summary = "Update tenant configuration",
      description =
          "Upserts the company's configuration. Only sections present in the request body are "
              + "changed. The bonusWorkflow section additionally requires a media-owner or admin caller.")
  public ApiResponse<PlannerConfigurationDTO> updateConfiguration(
      @PathVariable String companyId, @RequestBody PlannerConfigurationDTO update) {
    return ApiResponse.success(plannerConfigurationService.update(companyId, update));
  }
}
