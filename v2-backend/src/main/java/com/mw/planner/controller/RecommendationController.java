package com.mw.planner.controller;

import com.mw.planner.domain.Campaign;
import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.recommendation.GenerateRecommendationRequestDTO;
import com.mw.planner.dto.recommendation.PaginatedRecommendationResponseDTO;
import com.mw.planner.dto.recommendation.RecommendationResultFilterDTO;
import com.mw.planner.dto.recommendation.RecommendationStatusResponseDTO;
import com.mw.planner.dto.recommendation.RunSchedulesResponseDTO;
import com.mw.planner.service.CampaignService;
import com.mw.planner.service.recommendation.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/recommendation")
@Tag(name = "Recommendation Management", description = "Recommendation engine integration APIs")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class RecommendationController {

  private final RecommendationService recommendationService;
  private final CampaignService campaignService;

  @PostMapping("/campaigns/{campaignId}/generate")
  @Operation(
      summary = "Generate recommendation",
      description =
          "Generates inventory recommendations for a campaign by calling the recommendation engine")
  public ApiResponse<RecommendationStatusResponseDTO> generateRecommendation(
      @PathVariable String campaignId,
      @Parameter(
              description =
                  "When true, the recommendation engine is asked to force a regeneration. "
                      + "Absent, empty, or false keeps the existing behavior.")
          @RequestParam(defaultValue = "false")
          boolean forceRegenerate,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              description =
                  "Optional recommendation parameters (e.g. mediaOwnerIds). "
                      + "When omitted, defaults are used.")
          @RequestBody(required = false)
          GenerateRecommendationRequestDTO request) {
    log.info(
        "Generating recommendation for campaignId {} (forceRegenerate={}) with request: {}",
        campaignId,
        forceRegenerate,
        request);
    RecommendationStatusResponseDTO result =
        recommendationService.generateRecommendation(campaignId, request, forceRegenerate);
    return ApiResponse.success(result);
  }

  @PostMapping("/campaigns/{campaignId}/runs/{runId}/results")
  @Operation(
      summary = "Get recommendation results",
      description =
          "Returns paginated recommendation results enriched with performance metrics. "
              + "Optional body filters: programmaticSupport (YES | NO | ALL), dealTypes (e.g. guaranteed, preferred_deal).")
  public ApiResponse<PaginatedRecommendationResponseDTO> getRecommendationResults(
      @PathVariable String campaignId,
      @PathVariable String runId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) List<String> sort,
      @RequestParam(required = false) String search,
      @RequestBody(required = false) RecommendationResultFilterDTO filter) {
    PaginatedRecommendationResponseDTO result =
        recommendationService.getRecommendationResults(
            campaignId, runId, page, size, sort, search, filter);
    return ApiResponse.success(result);
  }

  @PostMapping("/campaigns/{campaignId}/auto-optimize-schedules")
  @Operation(
      summary = "Auto-optimize schedules",
      description =
          "Call recommendation engine to auto-optimize schedules for all campaign inventory "
              + "schedules. Uses the campaign's runId. On success, existing schedules are removed "
              + "and new schedules from the response are created and linked. Schedule/approval "
              + "fields are cleared for re-approval.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Auto-optimize completed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request (e.g. campaign not found or campaign has no runId)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error or recommendation engine error")
      })
  public ApiResponse<RunSchedulesResponseDTO> autoOptimizeSchedules(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId) {
    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);
    String runId = campaign.getRunId();
    if (runId == null || runId.isBlank()) {
      throw new RuntimeException(
          "Campaign has no recommendation runId; run recommendations first.");
    }
    RunSchedulesResponseDTO response =
        recommendationService.autoOptimizeSchedulesAndSync(campaignId, runId);
    return ApiResponse.success(response);
  }
}
