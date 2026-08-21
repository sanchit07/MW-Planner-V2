package com.mw.planner.controller;

import com.mw.planner.domain.Creative;
import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.creative.CreativeDTO;
import com.mw.planner.dto.creative.CreativeTier1StatusRequestDTO;
import com.mw.planner.service.CreativeService;
import com.mw.planner.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/creatives")
@Tag(name = "Creatives", description = "Creative asset library")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CreativeController {

  private final CreativeService creativeService;
  private final UserService userService;

  @GetMapping
  @PreAuthorize("hasRole('planner:creatives:read')")
  @Operation(
      summary = "List creatives",
      description =
          "Lists active creatives for the acting company, optionally filtered by Tier 1 status.")
  public ApiResponse<List<CreativeDTO>> list(
      @RequestParam(value = "tier1Status", required = false) Creative.Tier1Status tier1Status) {
    return ApiResponse.success(
        creativeService.listForCompany(userService.getActingCompanyId(), tier1Status));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('planner:creatives:read')")
  @Operation(summary = "Get a creative")
  public ApiResponse<CreativeDTO> getById(@PathVariable String id) {
    return ApiResponse.success(creativeService.getById(id));
  }

  @PostMapping(consumes = "multipart/form-data")
  @PreAuthorize("hasRole('planner:creatives:create')")
  @Operation(
      summary = "Upload a creative",
      description =
          "Uploads a creative asset via the shared CloudStorageService and adds it to the library.")
  public ApiResponse<CreativeDTO> upload(
      @RequestPart("file") MultipartFile file,
      @RequestParam("name") String name,
      @RequestParam("format") Creative.Format format,
      @RequestParam(value = "brandId", required = false) String brandId,
      @RequestParam(value = "pixelWidth", required = false) Integer pixelWidth,
      @RequestParam(value = "pixelHeight", required = false) Integer pixelHeight,
      @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds,
      @Parameter(description = "Tags") @RequestParam(value = "tags", required = false)
          List<String> tags) {
    return ApiResponse.success(
        creativeService.upload(
            userService.getActingCompanyId(),
            brandId,
            name,
            format,
            pixelWidth,
            pixelHeight,
            durationSeconds,
            tags,
            file));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('planner:creatives:delete')")
  @Operation(
      summary = "Deactivate a creative",
      description = "Soft-deletes a creative from the library.")
  public ApiResponse<Void> deactivate(@PathVariable String id) {
    creativeService.deactivate(id);
    return ApiResponse.success(null);
  }

  @PatchMapping("/{id}/tier1-status")
  @PreAuthorize("hasRole('planner:creatives:approve')")
  @Operation(
      summary = "Tier 1 approval decision",
      description =
          "Transitions a creative Processing -> Accepted or Inadequate. A rejectionReason is "
              + "required when marking a creative Inadequate. Only Accepted creatives may be "
              + "assigned to a line item.")
  public ApiResponse<CreativeDTO> updateTier1Status(
      @PathVariable String id, @Valid @RequestBody CreativeTier1StatusRequestDTO request) {
    return ApiResponse.success(
        creativeService.updateTier1Status(
            id, request.getTier1Status(), request.getRejectionReason()));
  }
}
