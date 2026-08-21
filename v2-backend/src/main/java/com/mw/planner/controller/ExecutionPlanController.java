package com.mw.planner.controller;

import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.ExecutionLineRequestDTO;
import com.mw.planner.dto.ExecutionPlanPushRequestDTO;
import com.mw.planner.dto.ExecutionPlanResponseDTO;
import com.mw.planner.dto.ExecutionPlanStatusDTO;
import com.mw.planner.dto.ExecutionWorkspaceResponseDTO;
import com.mw.planner.service.ExecutionPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/execution-plan")
@Tag(
    name = "Execution Plan",
    description = "Campaign execution plan (handoff to execution systems)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ExecutionPlanController {

  private final ExecutionPlanService executionPlanService;

  @GetMapping
  @PreAuthorize("hasRole('planner:plans:read')")
  @Operation(
      summary = "Get the execution plan",
      description = "Returns the campaign's execution plan, generating a baseline if none exists.")
  public ApiResponse<ExecutionPlanResponseDTO> getExecutionPlan(@PathVariable String campaignId) {
    return ApiResponse.success(executionPlanService.getExecutionPlan(campaignId));
  }

  @GetMapping("/status")
  @PreAuthorize("hasRole('planner:plans:read')")
  @Operation(
      summary = "Get execution state",
      description =
          "Lightweight execution/handoff state for the campaign view. Never generates a baseline plan.")
  public ApiResponse<ExecutionPlanStatusDTO> getExecutionPlanStatus(
      @PathVariable String campaignId) {
    return ApiResponse.success(executionPlanService.getExecutionPlanStatus(campaignId));
  }

  @PostMapping("/push")
  @PreAuthorize("hasRole('planner:plans:update')")
  @Operation(
      summary = "Push the execution plan",
      description =
          "Hands off all pending lines (optionally a subset, or retries the given failed lines), locks the plan, and takes the campaign live.")
  public ApiResponse<ExecutionPlanResponseDTO> pushExecutionPlan(
      @PathVariable String campaignId,
      @RequestBody(required = false) ExecutionPlanPushRequestDTO request) {
    return ApiResponse.success(
        executionPlanService.pushExecutionPlan(
            campaignId,
            request != null ? request.getRetryLineIds() : null,
            request != null ? request.getLineIds() : null));
  }

  // ---- media-owner Execution Workspace ----

  @GetMapping("/workspace")
  @PreAuthorize("hasRole('planner:plans:read')")
  @Operation(
      summary = "Media-owner execution workspace",
      description =
          "Viewer-scoped execution setup: own inventories, schedules, availability timeline, and editable line items. Media-owner participants only.")
  public ApiResponse<ExecutionWorkspaceResponseDTO> getWorkspace(@PathVariable String campaignId) {
    return ApiResponse.success(executionPlanService.getWorkspace(campaignId));
  }

  @PatchMapping("/lines/{lineId}")
  @PreAuthorize("hasRole('planner:plans:update')")
  @Operation(summary = "Update one of the viewer's execution lines (floor rate, target, type)")
  public ApiResponse<ExecutionWorkspaceResponseDTO> updateLine(
      @PathVariable String campaignId,
      @PathVariable String lineId,
      @RequestBody ExecutionLineRequestDTO request) {
    return ApiResponse.success(executionPlanService.updateLine(campaignId, lineId, request));
  }

  @PostMapping("/lines")
  @PreAuthorize("hasRole('planner:plans:update')")
  @Operation(summary = "Create a new empty execution line for the viewing media owner")
  public ApiResponse<ExecutionWorkspaceResponseDTO> createLine(
      @PathVariable String campaignId, @RequestBody ExecutionLineRequestDTO request) {
    return ApiResponse.success(executionPlanService.createLine(campaignId, request));
  }

  @PostMapping("/lines/{lineId}/move-inventory")
  @PreAuthorize("hasRole('planner:plans:update')")
  @Operation(summary = "Move an inventory onto this line from another of the viewer's lines")
  public ApiResponse<ExecutionWorkspaceResponseDTO> moveInventory(
      @PathVariable String campaignId,
      @PathVariable String lineId,
      @RequestBody ExecutionLineRequestDTO request) {
    return ApiResponse.success(executionPlanService.moveInventory(campaignId, lineId, request));
  }

  @DeleteMapping("/lines/{lineId}")
  @PreAuthorize("hasRole('planner:plans:update')")
  @Operation(summary = "Delete one of the viewer's empty execution lines")
  public ApiResponse<ExecutionWorkspaceResponseDTO> deleteLine(
      @PathVariable String campaignId, @PathVariable String lineId) {
    return ApiResponse.success(executionPlanService.deleteLine(campaignId, lineId));
  }

  @PostMapping("/reset")
  @PreAuthorize("hasRole('planner:plans:update')")
  @Operation(
      summary = "Reset the execution plan",
      description = "Regenerates the baseline plan. Rejected once the plan has been pushed.")
  public ApiResponse<ExecutionPlanResponseDTO> resetExecutionPlan(@PathVariable String campaignId) {
    return ApiResponse.success(executionPlanService.resetExecutionPlan(campaignId));
  }
}
