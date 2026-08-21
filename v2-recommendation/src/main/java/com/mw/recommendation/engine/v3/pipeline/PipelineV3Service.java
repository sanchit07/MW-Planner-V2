package com.mw.recommendation.engine.v3.pipeline;

import com.mw.recommendation.engine.v3.domain.RecommendationRunV3;
import com.mw.recommendation.engine.v3.schedule.ScheduleStage;
import com.mw.recommendation.engine.v3.selection.SelectionStage;
import com.mw.recommendation.engine.v3.support.V3ErrorCode;
import com.mw.recommendation.engine.v3.variation.VariationV3Service;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * v3 pipeline orchestrator: fetch → enrich → score → vary/rank → select → schedule → output →
 * complete. Lifecycle contract: any exception marks the run FAILED (never a stuck IN_PROGRESS),
 * zero candidates completes as NO_MATCHES with nearest alternatives, and every run write is a
 * targeted $set. Each stage is timed; timings land on the run metadata.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineV3Service {

  private final RunV3Lifecycle lifecycle;
  private final CandidateFetchStage fetchStage;
  private final EnrichmentStage enrichmentStage;
  private final ScoringStage scoringStage;
  private final VariationV3Service variationService;
  private final SelectionStage selectionStage;
  private final ScheduleStage scheduleStage;
  private final OutputStage outputStage;

  @Async("v3AsyncExecutor")
  public void process(V3RunContext ctx) {
    String runId = ctx.getRunId();
    try {
      lifecycle.updateProgress(runId, 10);

      CandidateFetchStage.FetchResult fetched =
          ctx.getTimer().time("1_Fetch", () -> fetchStage.fetch(ctx));
      ctx.setCandidates(fetched.candidates());
      ctx.setBrandCategory(fetched.brandCategory());
      lifecycle.updateProgress(runId, 20);

      if (ctx.getCandidates().isEmpty()) {
        completeNoMatches(ctx);
        return;
      }

      ctx.getTimer().time("2_Enrich", () -> enrichmentStage.enrich(ctx));
      lifecycle.updateProgress(runId, 30);

      if (ctx.getCandidates().isEmpty()) {
        completeNoMatches(ctx); // everything excluded for missing impressions data (AC-04)
        return;
      }

      ctx.getTimer().time("3_Score", () -> scoringStage.score(ctx));
      // scoring stage advances progress 30→80 at configured milestones

      ctx.getTimer()
          .time(
              "4_Rank",
              () ->
                  ctx.setScored(
                      variationService.applyAndRank(
                          ctx.getScored(),
                          ctx.getSeed(),
                          Boolean.TRUE.equals(ctx.getRequest().getDisableVariation()),
                          ctx.getRequest().getTopN())));
      lifecycle.updateProgress(runId, 85);

      if (ctx.getScored().isEmpty()) {
        completeNoMatches(ctx);
        return;
      }

      ctx.getTimer().time("5_Select", () -> selectionStage.select(ctx));
      ctx.getTimer().time("6_Schedule", () -> scheduleStage.buildSchedules(ctx));
      lifecycle.updateProgress(runId, 90);

      ctx.getTimer().time("7_Persist", () -> outputStage.buildAndPersist(ctx));
      lifecycle.updateProgress(runId, 95);

      List<RecommendationRunV3.CityCluster> clusters = outputStage.cityClusters(ctx);
      lifecycle.complete(
          runId,
          RecommendationRunV3.RunStatus.COMPLETED,
          buildMetadata(ctx),
          ctx.getWarnings().warnings(),
          ctx.getAutoSelectedInventoryIds(),
          List.of(),
          clusters,
          null,
          null);
      ctx.getTimer().logSummary();
    } catch (Exception e) {
      log.error("v3 pipeline failed for run {}: {}", runId, e.getMessage(), e);
      lifecycle.fail(
          runId, V3ErrorCode.INTERNAL_ERROR, e.getMessage(), ctx.getWarnings().warnings());
    }
  }

  private void completeNoMatches(V3RunContext ctx) {
    boolean nothingFetched = ctx.getFetchedCount() == 0;
    List<RecommendationRunV3.Alternative> alternatives =
        ctx.getTimer().time("Alternatives", () -> outputStage.nearestAlternatives(ctx));
    if (nothingFetched) {
      ctx.getWarnings()
          .warn(
              "No inventory available in "
                  + ctx.getRequest().getCountry()
                  + " for the requested dates and filters");
    }
    lifecycle.complete(
        ctx.getRunId(),
        RecommendationRunV3.RunStatus.NO_MATCHES,
        buildMetadata(ctx),
        ctx.getWarnings().warnings(),
        List.of(),
        alternatives,
        List.of(),
        nothingFetched ? V3ErrorCode.NO_INVENTORY : V3ErrorCode.INSUFFICIENT_DATA,
        nothingFetched
            ? "No inventory matched the request"
            : "All matched inventories lacked usable impressions/availability data");
    ctx.getTimer().logSummary();
  }

  private RecommendationRunV3.Metadata buildMetadata(V3RunContext ctx) {
    double averageScore =
        ctx.getScored().isEmpty()
            ? 0.0
            : ctx.getScored().stream()
                .mapToDouble(ScoredInventoryV3::finalScoreWithJitter)
                .average()
                .orElse(0.0);
    return RecommendationRunV3.Metadata.builder()
        .totalInventoriesEvaluated(ctx.getFetchedCount())
        .totalInventoriesRecommended(ctx.getScored().size())
        .totalInventoriesExcluded(ctx.getWarnings().totalExcluded())
        .exclusionReasons(ctx.getWarnings().exclusionReasons())
        .averageScore(Math.round(averageScore * 100.0) / 100.0)
        .stageTimingsMs(ctx.getTimer().timingsMs())
        .build();
  }
}
