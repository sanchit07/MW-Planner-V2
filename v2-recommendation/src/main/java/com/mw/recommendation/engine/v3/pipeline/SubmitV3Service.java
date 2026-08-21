package com.mw.recommendation.engine.v3.pipeline;

import com.mw.recommendation.engine.v3.domain.RecommendationRunV3;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import com.mw.recommendation.engine.v3.dto.V3StatusResponseDTO;
import com.mw.recommendation.engine.v3.repository.ResultV3Repository;
import com.mw.recommendation.engine.v3.repository.RunV3Repository;
import com.mw.recommendation.engine.v3.repository.ScheduleV3Repository;
import com.mw.recommendation.engine.v3.service.RunQueryV3Service;
import com.mw.recommendation.engine.v3.support.RequestHashV3Utils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * v3 submission: payload-hash dedup (identical payloads return the existing run — PRD AC-10
 * reproducibility), forceRegenerate semantics matching v1 (ignored while IN_PROGRESS), seed
 * derivation, run creation, async pipeline kickoff.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubmitV3Service {

  private final RunV3Repository runRepository;
  private final ResultV3Repository resultRepository;
  private final ScheduleV3Repository scheduleRepository;
  private final PipelineV3Service pipelineService;
  private final RunQueryV3Service queryService;

  public V3StatusResponseDTO submit(
      String campaignId, RecommendationV3RequestDTO request, boolean forceRegenerate) {

    String requestHash = RequestHashV3Utils.hashRequest(request);
    Optional<RecommendationRunV3> existing =
        runRepository.findByCampaignIdAndRequestHash(campaignId, requestHash);

    if (existing.isPresent()) {
      RecommendationRunV3 run = existing.get();
      if (!forceRegenerate) {
        log.info("v3 dedup hit: returning run {} for campaign {}", run.getRunId(), campaignId);
        return queryService.toStatus(run);
      }
      if (run.getStatus() == RecommendationRunV3.RunStatus.IN_PROGRESS) {
        log.warn(
            "v3 forceRegenerate ignored for campaign {}: run {} is IN_PROGRESS",
            campaignId,
            run.getRunId());
        return queryService.toStatus(run);
      }
      log.info("v3 forceRegenerate: deleting run {} and dependents", run.getRunId());
      runRepository.delete(run);
      resultRepository.deleteByRunId(run.getRunId());
      scheduleRepository.deleteByRunId(run.getRunId());
    }

    String runId = UUID.randomUUID().toString();
    String seed = deriveSeed(request, requestHash);

    RecommendationRunV3 run =
        RecommendationRunV3.builder()
            .runId(runId)
            .campaignId(campaignId)
            .productId(request.getProductId())
            .companyId(request.getCompanyId())
            .requestHash(requestHash)
            .request(request)
            .status(RecommendationRunV3.RunStatus.IN_PROGRESS)
            .completionPercentage(0)
            .generatedAt(LocalDateTime.now())
            .seed(seed)
            .engineVersion(3)
            .warnings(new ArrayList<>())
            .build();
    runRepository.save(run);
    log.info("v3 created run {} for campaign {} (seed={})", runId, campaignId, seed);

    pipelineService.process(new V3RunContext(runId, campaignId, request, seed));
    return queryService.toStatus(run);
  }

  /**
   * Seed = client seed when supplied (PRD §6.7 deterministic consumers); otherwise request hash +
   * submission minute, so a forceRegenerate in a different minute varies the ranking (AC-11) while
   * regeneration within the same minute reproduces it exactly (AC-10).
   */
  private static String deriveSeed(RecommendationV3RequestDTO request, String requestHash) {
    if (request.getSeed() != null && !request.getSeed().isBlank()) {
      return request.getSeed().trim();
    }
    long minuteBucket = System.currentTimeMillis() / 60_000;
    return requestHash + "|" + minuteBucket;
  }
}
