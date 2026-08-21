package com.mw.recommendation.engine.service;

import com.mw.recommendation.engine.domain.RecommendationRun;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.dto.RecommendationStatusResponseDTO;
import com.mw.recommendation.engine.repository.RecommendationResultRepository;
import com.mw.recommendation.engine.repository.RecommendationRunRepository;
import com.mw.recommendation.engine.repository.RunScheduleRecommendationRepository;
import com.mw.recommendation.engine.util.RequestHashUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Synchronous entry point for the optimized ("v2") recommendation endpoint.
 *
 * <p>Mirrors {@link RecommendationService#submitRecommendation} exactly (hash-based dedup,
 * forceRegenerate handling, run creation) but triggers {@link RecommendationAsyncServiceV2} instead
 * of the v1 async pipeline. Kept as a separate class so v1 is left completely untouched.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationV2Service {

  private final RecommendationRunRepository recommendationRunRepository;
  private final RecommendationResultRepository recommendationResultRepository;
  private final RunScheduleRecommendationRepository runScheduleRecommendationRepository;
  private final RecommendationAsyncServiceV2 recommendationAsyncServiceV2;
  private final VirtualThreadTaskExecutor virtualThreadTaskExecutor;

  /**
   * Submit a recommendation request through the optimized pipeline. Returns existing runId if same
   * payload exists, otherwise creates a new runId and starts async processing.
   *
   * @param campaignId Campaign ID
   * @param request Recommendation request
   * @param forceRegenerate If true, delete any existing run with the same payload (and its
   *     dependent results/schedules) and regenerate. Ignored when the existing run is still
   *     IN_PROGRESS.
   * @return Status response with runId and status
   */
  public RecommendationStatusResponseDTO submitRecommendation(
      String campaignId, RecommendationRequestDTO request, boolean forceRegenerate) {

    log.info("Submitting v2 recommendation request for campaign: {}", campaignId);

    // Generate hash of request payload (excluding topN) — identical dedup key to v1
    String requestHash = RequestHashUtils.hashRequest(request);

    Optional<RecommendationRun> existingRun =
        recommendationRunRepository.findByCampaignIdAndRequestHash(campaignId, requestHash);

    if (existingRun.isPresent()) {
      RecommendationRun run = existingRun.get();
      boolean isFailed = run.getStatus() == RecommendationRun.RunStatus.FAILED;

      // A FAILED run is not a usable cached result: always regenerate it (finding #1), even without
      // forceRegenerate, so a transient failure can never be served back to the client forever.
      if (!forceRegenerate && !isFailed) {
        log.info(
            "Found existing runId {} for campaign {} with same payload (v2)",
            run.getRunId(),
            campaignId);
        return buildStatusResponse(run);
      }
      // Never delete a still-running pipeline's run — that would orphan the results it is writing.
      if (run.getStatus() == RecommendationRun.RunStatus.IN_PROGRESS) {
        log.warn(
            "forceRegenerate requested for campaign {} but run {} is IN_PROGRESS; returning existing run (v2)",
            campaignId,
            run.getRunId());
        return buildStatusResponse(run);
      }
      log.info(
          "Regenerating v2 run {} for campaign {} (forceRegenerate={}, failed={}): deleting dependent documents",
          run.getRunId(),
          campaignId,
          forceRegenerate,
          isFailed);
      // Delete the parent run synchronously so the dedup lookup stays consistent, then clean up the
      // (potentially large) dependent documents off the request thread (#13). The dependents are
      // keyed by the OLD runId, so they never collide with the fresh run created below.
      recommendationRunRepository.delete(run);
      final String oldRunId = run.getRunId();
      virtualThreadTaskExecutor.execute(
          () -> {
            try {
              recommendationResultRepository.deleteByRunId(oldRunId);
              runScheduleRecommendationRepository.deleteByRunId(oldRunId);
            } catch (Exception e) {
              log.warn(
                  "Async cleanup of dependents for old v2 run {} failed (will be reconciled on next regenerate): {}",
                  oldRunId,
                  e.getMessage());
            }
          });
    }

    String runId = UUID.randomUUID().toString();
    LocalDateTime now = LocalDateTime.now();

    RecommendationRun newRun =
        RecommendationRun.builder()
            .runId(runId)
            .campaignId(campaignId)
            .productId(request.getProductId())
            .companyId(request.getCompanyId())
            .requestHash(requestHash)
            .request(request)
            .status(RecommendationRun.RunStatus.IN_PROGRESS)
            .completionPercentage(0)
            .generatedAt(now)
            .warnings(new ArrayList<>())
            .pipelineVersion("v2") // scopes the v2 stale-run watchdog; v1 runs leave this null
            .build();

    recommendationRunRepository.save(newRun);
    log.info("Created new v2 runId {} for campaign {}", runId, campaignId);

    // Start async processing via the optimized v2 pipeline
    recommendationAsyncServiceV2.processRecommendationsAsyncOptimized(runId, campaignId, request);

    return buildStatusResponse(newRun);
  }

  /** Build a status response from a run (faithful copy of v1's mapping). */
  private RecommendationStatusResponseDTO buildStatusResponse(RecommendationRun run) {
    RecommendationStatusResponseDTO.RecommendationMetadata metadata = null;
    if (run.getMetadata() != null) {
      metadata =
          RecommendationStatusResponseDTO.RecommendationMetadata.builder()
              .totalInventoriesEvaluated(run.getMetadata().getTotalInventoriesEvaluated())
              .totalInventoriesRecommended(run.getMetadata().getTotalInventoriesRecommended())
              .totalInventoriesExcluded(run.getMetadata().getTotalInventoriesExcluded())
              .exclusionReasons(run.getMetadata().getExclusionReasons())
              .averageScore(run.getMetadata().getAverageScore())
              .seed(run.getMetadata().getSeed())
              .autoSelectedInventoryIds(run.getAutoSelectedInventoryIds())
              .build();
    }

    return RecommendationStatusResponseDTO.builder()
        .runId(run.getRunId())
        .status(
            run.getStatus() == RecommendationRun.RunStatus.IN_PROGRESS
                ? RecommendationStatusResponseDTO.RunStatus.IN_PROGRESS
                : RecommendationStatusResponseDTO.RunStatus.COMPLETED)
        .completionPercentage(run.getCompletionPercentage())
        .campaignId(run.getCampaignId())
        .productId(run.getProductId())
        .companyId(run.getCompanyId())
        .generatedAt(run.getGeneratedAt())
        .metadata(metadata)
        .warnings(run.getWarnings())
        // Observability passthrough: null until the run completes (and for pre-existing runs).
        .autoSelectionReasonCode(
            run.getAutoSelectionReasonCode() != null
                ? run.getAutoSelectionReasonCode().name()
                : null)
        .autoSelectionReasonDetail(run.getAutoSelectionReasonDetail())
        .build();
  }
}
