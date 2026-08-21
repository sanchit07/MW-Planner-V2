package com.mw.recommendation.engine.controller;

import com.mw.recommendation.engine.dto.ApiResponse;
import com.mw.recommendation.engine.dto.RunSchedulesResponseDTO;
import com.mw.recommendation.engine.dto.SelectedInventoriesDTO;
import com.mw.recommendation.engine.service.ScheduleRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for schedule recommendation endpoints. Provides endpoints for fetching schedule
 * recommendations by run ID.
 */
@RestController
@RequestMapping("/api/v1/recommendation/schedules")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Schedule Recommendations",
    description = "APIs for generating schedule recommendations")
@SecurityRequirement(name = "bearerAuth")
public class ScheduleRecommendationController {

  private final ScheduleRecommendationService scheduleRecommendationService;

  /**
   * Get all recommended schedules for a run. Planner uses this with runId (e.g. from
   * RecommendationRun.autoSelectedInventoryIds) to fetch and sync schedules to its own DB.
   *
   * @param runId Recommendation run ID
   * @return All schedules stored for this run
   */
  @GetMapping("/runs/{runId}")
  @Operation(
      summary = "Get schedules by run ID",
      description =
          "Fetch all recommended schedules for a recommendation run. Planner calls this to sync schedules to its DB.")
  public ResponseEntity<ApiResponse<RunSchedulesResponseDTO>> getSchedulesByRunId(
      @PathVariable String runId) {
    RunSchedulesResponseDTO response = scheduleRecommendationService.getSchedulesByRunId(runId);
    return ResponseEntity.ok(
        ApiResponse.<RunSchedulesResponseDTO>builder().success(true).data(response).build());
  }

  /**
   * Get only the AUTO-selected recommended schedules for a run. Same shape as {@link
   * #getSchedulesByRunId(String)}, filtered to inventories that were auto-selected for this run.
   *
   * @param runId Recommendation run ID
   * @return Schedules whose inventory was AUTO-selected for this run
   */
  @GetMapping("/runs/{runId}/auto-selection")
  @Operation(
      summary = "Get auto-selected schedules by run ID",
      description =
          "Fetch only the schedules whose inventory was AUTO-selected for this recommendation run.")
  public ResponseEntity<ApiResponse<RunSchedulesResponseDTO>> getAutoSelectedSchedulesByRunId(
      @PathVariable String runId) {
    RunSchedulesResponseDTO response =
        scheduleRecommendationService.getSchedulesByRunIdAndSelectionModeAuto(runId);
    return ResponseEntity.ok(
        ApiResponse.<RunSchedulesResponseDTO>builder().success(true).data(response).build());
  }

  /**
   * Auto-optimize schedules for the provided inventory IDs. Cleans up existing schedules for this
   * run and the given inventories, then generates new schedules using Round 1 (day-based: minDays
   * or 1 day + iteration) and optionally Round 2 (hour-based: minHours when a full day would exceed
   * budget) until budget or goal is met. Respects operational times and availability (booking &lt;
   * 90%).
   *
   * @param runId Recommendation run ID (path)
   * @param request Body containing inventoryIds (engine IDs = planner externalIds)
   * @return Run schedules response with generated schedules per inventory
   */
  @PostMapping("/runs/{runId}/auto-optimize")
  @Operation(
      summary = "Auto-optimize schedules",
      description =
          "Generate and return optimized schedules for the given inventory IDs. Cleans up existing"
              + " schedules for this run and inventories, then builds schedules respecting"
              + " sellingTerm.minDays/minHours, operational times and availability, until budget or"
              + " goal is met. Round 1 is day-based; Round 2 (non-classic only) adds minHours when"
              + " a full day would exceed budget.")
  public ResponseEntity<ApiResponse<RunSchedulesResponseDTO>> autoOptimizeSchedules(
      @Parameter(description = "Recommendation run ID", required = true) @PathVariable String runId,
      @Valid @RequestBody SelectedInventoriesDTO request) {
    log.info(
        "Auto-optimize schedules for runId: {}, inventoryCount: {}",
        runId,
        request.getInventoryIds().size());
    RunSchedulesResponseDTO response =
        scheduleRecommendationService.autoOptimizeSchedules(runId, request.getInventoryIds());
    return ResponseEntity.ok(
        ApiResponse.<RunSchedulesResponseDTO>builder().success(true).data(response).build());
  }
}
