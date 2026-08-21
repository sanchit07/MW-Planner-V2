package com.mw.recommendation.engine.v3.controller;

import com.mw.recommendation.engine.dto.ApiResponse;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import com.mw.recommendation.engine.v3.dto.V3ResultsResponseDTO;
import com.mw.recommendation.engine.v3.dto.V3StatusResponseDTO;
import com.mw.recommendation.engine.v3.pipeline.SubmitV3Service;
import com.mw.recommendation.engine.v3.service.RunQueryV3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * v3 recommendation endpoints — the PRD-conformant pipeline. Completely separate from the v1/v2
 * controllers; reuses only the generic {@link ApiResponse} envelope.
 */
@RestController
@RequestMapping("/api/v1/recommendation/v3")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Inventory Recommendation v3", description = "PRD-conformant recommendation engine v3")
@SecurityRequirement(name = "bearerAuth")
public class RecommendationV3Controller {

  private final SubmitV3Service submitService;
  private final RunQueryV3Service queryService;

  @PostMapping("/campaigns/{campaignId}/recommendations")
  @Operation(
      summary = "Submit v3 recommendation request",
      description =
          "Returns runId and status. Identical payloads dedup to the existing run; pass "
              + "forceRegenerate=true to delete and regenerate (ignored while IN_PROGRESS). "
              + "Supports seed / disableVariation for deterministic consumers.")
  public ResponseEntity<ApiResponse<V3StatusResponseDTO>> submit(
      @PathVariable String campaignId,
      @Parameter(description = "Delete any existing run with the same payload and regenerate")
          @RequestParam(required = false)
          Boolean forceRegenerate,
      @Valid @RequestBody RecommendationV3RequestDTO request) {

    log.info("v3 submit for campaign {} (forceRegenerate={})", campaignId, forceRegenerate);
    V3StatusResponseDTO response =
        submitService.submit(campaignId, request, Boolean.TRUE.equals(forceRegenerate));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/runs/{runId}/status")
  @Operation(
      summary = "Get v3 run status",
      description =
          "Status incl. FAILED / NO_MATCHES, warnings, structured error code, alternatives, "
              + "city clusters and stage timings.")
  public ResponseEntity<ApiResponse<V3StatusResponseDTO>> status(@PathVariable String runId) {
    return ResponseEntity.ok(ApiResponse.success(queryService.getStatus(runId)));
  }

  @PostMapping("/runs/{runId}/results")
  @Operation(
      summary = "Get v3 recommendation results",
      description =
          "Paginated scored results with component scores, score audit, why, confidence, band, "
              + "availability summary, forecast (with source) and cost (with costUnit). "
              + "Default sort: finalScore,desc.")
  public ResponseEntity<ApiResponse<V3ResultsResponseDTO>> results(
      @PathVariable String runId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @Parameter(description = "Sort as field,direction; e.g. finalScore,desc")
          @RequestParam(required = false)
          List<String> sort) {
    return ResponseEntity.ok(ApiResponse.success(queryService.getResults(runId, page, size, sort)));
  }
}
