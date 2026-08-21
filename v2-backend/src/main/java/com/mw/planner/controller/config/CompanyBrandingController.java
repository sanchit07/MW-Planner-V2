package com.mw.planner.controller.config;

import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.config.CompanyBrandingDTO;
import com.mw.planner.service.config.CompanyBrandingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Planner-side tenant branding (white-label + logo). Deliberately not scoped by
 * {@code @PreAuthorize} on the resource path — company membership (for reads) and admin membership
 * (for writes) are both checked inside {@link CompanyBrandingService} against {@code
 * IamUserContext}/{@code UserService}, matching how {@code ManagementController} centralizes its
 * own admin gate, since "admin" here means global/system admin rather than any per-company
 * authority.
 */
@RestController
@RequestMapping("/api/v1/companies/{companyId}/branding")
@Tag(name = "Company Branding", description = "Tenant white-label + logo (admin-only)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CompanyBrandingController {

  private final CompanyBrandingService companyBrandingService;

  @GetMapping
  @Operation(
      summary = "Get company branding",
      description = "Returns the tenant's white-label flag and logo URL, or defaults if unset.")
  public ApiResponse<CompanyBrandingDTO> getBranding(@PathVariable String companyId) {
    return ApiResponse.success(companyBrandingService.getForCompany(companyId));
  }

  @PatchMapping
  @Operation(
      summary = "Update company branding",
      description = "Admin-only. Updates the white-label flag and/or logo URL for a tenant.")
  public ApiResponse<CompanyBrandingDTO> updateBranding(
      @PathVariable String companyId, @RequestBody CompanyBrandingDTO update) {
    return ApiResponse.success(companyBrandingService.update(companyId, update));
  }
}
