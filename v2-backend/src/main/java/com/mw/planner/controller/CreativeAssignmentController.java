package com.mw.planner.controller;

import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.creative.CreativeAssignmentDTO;
import com.mw.planner.dto.creative.CreativeAssignmentRequestDTO;
import com.mw.planner.service.CreativeAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/creative-assignments")
@Tag(
    name = "Creative Assignment",
    description = "Binds creatives to campaign line items, enforcing spec/status gating rules")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CreativeAssignmentController {

  private final CreativeAssignmentService creativeAssignmentService;

  @PostMapping
  @PreAuthorize("hasRole('planner:creatives:update')")
  @Operation(
      summary = "Bind a creative to a line item",
      description =
          "Enforces aspect-ratio (overridable via forceMatch), duration (never overridable), "
              + "file-size and campaign-status gates. A different-spec swap on an Approved campaign "
              + "re-opens Tier 2 approval for the affected media owner instead of being rejected.")
  public ApiResponse<CreativeAssignmentDTO> bind(
      @Valid @RequestBody CreativeAssignmentRequestDTO request) {
    return ApiResponse.success(creativeAssignmentService.bind(request));
  }

  @GetMapping("/line-items/{lineItemId}")
  @PreAuthorize("hasRole('planner:creatives:read')")
  @Operation(summary = "Get the assignment for a line item")
  public ApiResponse<CreativeAssignmentDTO> getForLineItem(@PathVariable String lineItemId) {
    return ApiResponse.success(creativeAssignmentService.getForLineItem(lineItemId));
  }

  @GetMapping("/campaigns/{campaignId}")
  @PreAuthorize("hasRole('planner:creatives:read')")
  @Operation(summary = "List assignments for a campaign")
  public ApiResponse<List<CreativeAssignmentDTO>> listForCampaign(@PathVariable String campaignId) {
    return ApiResponse.success(creativeAssignmentService.listForCampaign(campaignId));
  }
}
