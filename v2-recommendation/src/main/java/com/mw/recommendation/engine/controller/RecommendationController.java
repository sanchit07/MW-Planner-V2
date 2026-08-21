package com.mw.recommendation.engine.controller;

import com.mw.recommendation.engine.domain.OperationType;
import com.mw.recommendation.engine.domain.SelectionMode;
import com.mw.recommendation.engine.dto.ApiResponse;
import com.mw.recommendation.engine.dto.BrowseInventoryRequestDTO;
import com.mw.recommendation.engine.dto.PaginatedRecommendationResponseDTO;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.dto.RecommendationResultFilterDTO;
import com.mw.recommendation.engine.dto.RecommendationStatusResponseDTO;
import com.mw.recommendation.engine.dto.ScheduleRecommendationResponseDTO;
import com.mw.recommendation.engine.dto.SelectedInventoriesDTO;
import com.mw.recommendation.engine.dto.SelectedResultMeasureSummaryDTO;
import com.mw.recommendation.engine.service.RecommendationService;
import com.mw.recommendation.engine.service.RecommendationV2Service;
import com.mw.recommendation.engine.service.ScheduleRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for inventory recommendation endpoints. Provides endpoints for generating score-based
 * inventory recommendations for campaigns.
 */
@RestController
@RequestMapping("/api/v1/recommendation")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Inventory Recommendation",
    description = "APIs to generate recommendations based on user inputs")
@SecurityRequirement(name = "bearerAuth")
public class RecommendationController {

  private final RecommendationService recommendationService;
  private final RecommendationV2Service recommendationV2Service;
  private final ScheduleRecommendationService scheduleRecommendationService;

  /**
   * Submit a recommendation request. Returns runId and status. If same payload exists, returns
   * existing runId.
   *
   * @param campaignId Campaign ID
   * @param forceRegenerate If true, delete any existing run with the same payload and regenerate
   * @param request Recommendation request
   * @return Status response with runId, status, and completion percentage
   */
  @PostMapping("/campaigns/{campaignId}/recommendations")
  @Operation(
      summary = "Submit recommendation request",
      description =
          "Submit a recommendation request. Returns runId and status. If same payload already exists, returns existing runId. "
              + "Pass forceRegenerate=true to delete the existing run and regenerate; ignored while the existing run is IN_PROGRESS.")
  public ResponseEntity<ApiResponse<RecommendationStatusResponseDTO>> submitRecommendation(
      @PathVariable String campaignId,
      @Parameter(
              description =
                  "If true, delete any existing run with the same payload and regenerate. "
                      + "Ignored (existing run returned) if the existing run is still IN_PROGRESS.")
          @RequestParam(required = false)
          Boolean forceRegenerate,
      @Valid @RequestBody RecommendationRequestDTO request,
      Authentication authentication) {

    log.info(
        "Received recommendation request for campaign: {}, forceRegenerate: {}, payload: {}",
        campaignId,
        forceRegenerate,
        request);

    RecommendationStatusResponseDTO response =
        recommendationService.submitRecommendation(
            campaignId, request, Boolean.TRUE.equals(forceRegenerate));

    return ResponseEntity.status(HttpStatus.OK)
        .body(
            ApiResponse.<RecommendationStatusResponseDTO>builder()
                .success(true)
                .data(response)
                .build());
  }

  /**
   * Submit a recommendation request using the optimized ("v2") pipeline. Contract is identical to
   * the v1 endpoint above (same request body, same {@code forceRegenerate} semantics, same
   * response), but processing is delegated to {@link RecommendationV2Service} so the two pipelines
   * can be compared A/B while producing equivalent output. Results are still read via {@code
   * /runs/{runId}/results}.
   *
   * @param campaignId Campaign ID
   * @param forceRegenerate If true, delete any existing run with the same payload and regenerate
   * @param request Recommendation request
   * @return Status response with runId, status, and completion percentage
   */
  @PostMapping("/campaigns/{campaignId}/recommendations/v2")
  @Operation(
      summary = "Submit recommendation request (optimized v2)",
      description =
          "Same contract as the v1 submit endpoint but runs the optimized pipeline. Returns runId and "
              + "status; if the same payload already exists, returns the existing runId. Pass "
              + "forceRegenerate=true to delete the existing run and regenerate; ignored while the "
              + "existing run is IN_PROGRESS. Output is equivalent to v1.")
  public ResponseEntity<ApiResponse<RecommendationStatusResponseDTO>> submitRecommendationV2(
      @PathVariable String campaignId,
      @Parameter(
              description =
                  "If true, delete any existing run with the same payload and regenerate. "
                      + "Ignored (existing run returned) if the existing run is still IN_PROGRESS.")
          @RequestParam(required = false)
          Boolean forceRegenerate,
      @Valid @RequestBody RecommendationRequestDTO request,
      Authentication authentication) {

    log.info(
        "Received v2 recommendation request for campaign: {}, forceRegenerate: {}, payload: {}",
        campaignId,
        forceRegenerate,
        request);

    RecommendationStatusResponseDTO response =
        recommendationV2Service.submitRecommendation(
            campaignId, request, Boolean.TRUE.equals(forceRegenerate));

    return ResponseEntity.status(HttpStatus.OK)
        .body(
            ApiResponse.<RecommendationStatusResponseDTO>builder()
                .success(true)
                .data(response)
                .build());
  }

  /**
   * Get paginated recommendation results for a runId with filtering and sorting support
   *
   * @param runId Run ID
   * @param page Page number (0-based, default: 0)
   * @param size Page size (default: 20)
   * @param sort Sort parameters (format: "field,direction" e.g., "finalScore,desc"). Multiple sorts
   *     can be specified. Default: finalScore,desc
   * @param filter Filter DTO for filtering results (optional)
   * @return Paginated recommendation results
   */
  @PostMapping("/runs/{runId}/results")
  @Operation(
      summary = "Get recommendation results with filtering",
      description =
          "Get paginated recommendation results for a runId with filtering and sorting support. "
              + "Returns error if runId is IN_PROGRESS or not found. "
              + "Default sort: finalScore,desc. "
              + "Valid sort fields: finalScore, inventoryId, referenceId, name, createdAt, "
              + "estimatedImpressions, estimatedReach, estimatedFrequency, estimatedCost, "
              + "measureFit, geoFit, availability, budgetFit, audienceFit, brandFit, qualityFit, timeFit, selectionMode. "
              + "Filters can be applied on inventory properties, scores, forecasts, and costs. "
              + "Optional body filters: programmaticSupport (YES | NO | ALL), dealTypes (list of: guaranteed, preferred_deal, private_auction, open_auction, evergreen_pmp). "
              + "Optional query param 'search' filters by inventoryDetails name, address, city, classification, type, and referenceId (case-insensitive match, AND with body filters).")
  public ResponseEntity<ApiResponse<PaginatedRecommendationResponseDTO>> getRecommendationResults(
      @PathVariable String runId,
      @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
      @Parameter(
              description =
                  "Sort parameters (format: field,direction). Multiple sorts can be specified. "
                      + "Example: sort=finalScore,desc&sort=selectionMode,asc")
          @RequestParam(required = false)
          List<String> sort,
      @Parameter(
              description =
                  "Search text to match inventoryDetails name, address, city, classification, type, or referenceId (case-insensitive, AND with body filters)")
          @RequestParam(required = false)
          String search,
      @Parameter(description = "Filter criteria (optional)") @RequestBody(required = false)
          RecommendationResultFilterDTO filter) {

    log.info(
        "Fetching recommendation results for runId: {}, page: {}, size: {}, sort: {}, search: {}, filter: {}",
        runId,
        page,
        size,
        sort,
        search,
        filter);

    PaginatedRecommendationResponseDTO response =
        recommendationService.getRecommendationResults(runId, page, size, sort, search, filter);

    return ResponseEntity.status(HttpStatus.OK)
        .body(
            ApiResponse.<PaginatedRecommendationResponseDTO>builder()
                .success(true)
                .data(response)
                .build());
  }

  /**
   * Get paginated recommendation results for a runId, returning only selected inventories
   * (selectionMode = AUTO or MANUAL). Supports same filtering, sorting, and search as v1.
   */
  @PostMapping("/runs/{runId}/selected-results")
  @Operation(
      summary = "Get selected recommendation results (v2)",
      description =
          "Same as v1 but returns only inventories with selectionMode AUTO or MANUAL. "
              + "All v1 filters, sorting, and search are supported.")
  public ResponseEntity<ApiResponse<PaginatedRecommendationResponseDTO>>
      getSelectedRecommendationResults(
          @PathVariable String runId,
          @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0")
              int page,
          @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
          @Parameter(description = "Sort parameters (format: field,direction). Multiple allowed.")
              @RequestParam(required = false)
              List<String> sort,
          @Parameter(
                  description =
                      "Search text (case-insensitive match on name, address, city, classification, type, referenceId)")
              @RequestParam(required = false)
              String search,
          @Parameter(description = "Filter criteria (optional)") @RequestBody(required = false)
              RecommendationResultFilterDTO filter) {

    log.info(
        "Fetching selected recommendation results for runId: {}, page: {}, size: {}, sort: {}, search: {}",
        runId,
        page,
        size,
        sort,
        search);

    RecommendationResultFilterDTO effectiveFilter =
        filter != null ? filter : new RecommendationResultFilterDTO();
    effectiveFilter.setSelectionModes(List.of(SelectionMode.AUTO, SelectionMode.MANUAL));

    PaginatedRecommendationResponseDTO response =
        recommendationService.getRecommendationResults(
            runId, page, size, sort, search, effectiveFilter);

    return ResponseEntity.status(HttpStatus.OK)
        .body(
            ApiResponse.<PaginatedRecommendationResponseDTO>builder()
                .success(true)
                .data(response)
                .build());
  }

  /**
   * Get a measure summary for all selected inventories (selectionMode AUTO or MANUAL) of a run.
   * Returns every selected inventory in a single response (no pagination, sorting, search, or
   * filters), each carrying only inventoryId, referenceId, cost, and forecast.
   *
   * @param runId Run ID
   * @return All selected inventories projected to {inventoryId, referenceId, cost, forecast}
   */
  @GetMapping("/runs/{runId}/selected-results/measure-summary")
  @Operation(
      summary = "Get measure summary for selected results",
      description =
          "Returns all selected inventories (selectionMode AUTO or MANUAL) for the run in a single "
              + "response with only inventoryId, referenceId, cost and forecast. No pagination, "
              + "sorting, search, or filters.")
  public ResponseEntity<ApiResponse<List<SelectedResultMeasureSummaryDTO>>>
      getSelectedResultsMeasureSummary(@PathVariable String runId) {

    log.info("Fetching selected-results measure summary for runId: {}", runId);

    List<SelectedResultMeasureSummaryDTO> response =
        recommendationService.getSelectedResultsMeasureSummary(runId);

    return ResponseEntity.status(HttpStatus.OK)
        .body(
            ApiResponse.<List<SelectedResultMeasureSummaryDTO>>builder()
                .success(true)
                .data(response)
                .build());
  }

  /**
   * Select or deselect inventories for a recommendation run.
   *
   * <p>SELECT: marks the given inventories as MANUAL in recommendation results. No schedule
   * recommendation is generated or returned; the caller (e.g. mw-planner) creates its own default
   * schedules. Response data is null.
   *
   * <p>DESELECT: removes any stored schedules and clears selectionMode for the given inventories.
   * Response data is null.
   *
   * @param runId Run ID
   * @param operationType SELECT or DESELECT
   * @param selectedInventories DTO containing list of inventory IDs (externalIds in caller system)
   * @return Success with data=null for both SELECT and DESELECT
   */
  @PostMapping("/runs/{runId}/selected-inventories")
  @Operation(
      summary = "Select or deselect inventories",
      description =
          "SELECT marks inventories as manually selected (no schedule recommendation returned). "
              + "DESELECT clears selection and removes stored schedules for the given inventories. "
              + "Response data is null for both operations.")
  public ResponseEntity<ApiResponse<ScheduleRecommendationResponseDTO>> manageSelectedInventories(
      @PathVariable String runId,
      @Parameter(description = "Operation type: SELECT or DESELECT", required = true) @RequestParam
          OperationType operationType,
      @Valid @RequestBody SelectedInventoriesDTO selectedInventories) {

    log.info(
        "{} inventories for runId: {}, count: {}",
        operationType,
        runId,
        selectedInventories.getInventoryIds().size());

    if (operationType == OperationType.SELECT) {
      scheduleRecommendationService.markInventoriesAsSelectedOnly(
          runId, selectedInventories.getInventoryIds());
    } else {
      scheduleRecommendationService.deselectInventories(
          runId, selectedInventories.getInventoryIds());
    }
    log.info(
        "Successfully completed {} operation for runId: {}, inventory count: {}",
        operationType,
        runId,
        selectedInventories.getInventoryIds().size());

    log.info("PK >>> SELECTED INVENTORIES: {}", selectedInventories);
    return ResponseEntity.status(HttpStatus.OK)
        .body(
            ApiResponse.<ScheduleRecommendationResponseDTO>builder()
                .success(true)
                .data(null)
                .build());
  }

  /**
   * Browse all inventories directly with pagination. No scoring, no async processing. Returns
   * results in the same shape as recommendation results for frontend compatibility.
   */
  @PostMapping("/campaigns/{campaignId}/browse")
  @Operation(
      summary = "Browse all inventories with pagination",
      description =
          "Directly browse inventories from local MongoDB without scoring or async processing. "
              + "Returns paginated results in the same shape as recommendation results. "
              + "Supports search, sorting, and filtering.")
  public ResponseEntity<ApiResponse<PaginatedRecommendationResponseDTO>> browseInventories(
      @PathVariable String campaignId,
      @Valid @RequestBody BrowseInventoryRequestDTO request,
      @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
      @Parameter(description = "Sort parameters (format: field,direction). Default: name,asc")
          @RequestParam(required = false)
          List<String> sort,
      @Parameter(
              description = "Search text to match name, referenceId, or address (case-insensitive)")
          @RequestParam(required = false)
          String search) {

    log.info("Browse inventories for campaign: {}, page: {}, size: {}", campaignId, page, size);

    PaginatedRecommendationResponseDTO response =
        recommendationService.browseInventories(campaignId, request, page, size, sort, search);

    return ResponseEntity.status(HttpStatus.OK)
        .body(
            ApiResponse.<PaginatedRecommendationResponseDTO>builder()
                .success(true)
                .data(response)
                .build());
  }
}
