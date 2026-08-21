package com.mw.recommendation.engine.v3.pipeline;

import com.mw.recommendation.engine.v3.domain.RecommendationRunV3;
import com.mw.recommendation.engine.v3.support.V3ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * All writes to the v3 run document. Every update is a targeted {@code $set} (the v2 optimization)
 * — the run is never re-read and re-saved whole, eliminating the v1 read-modify-write contention on
 * progress ticks and completion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RunV3Lifecycle {

  private final MongoTemplate mongoTemplate;

  public void updateProgress(String runId, int percentage) {
    mongoTemplate.updateFirst(
        byRunId(runId),
        Update.update("completionPercentage", Math.max(0, Math.min(100, percentage))),
        RecommendationRunV3.class);
  }

  /** Terminal COMPLETED (or NO_MATCHES) update — one {@code $set} for every completion field. */
  public void complete(
      String runId,
      RecommendationRunV3.RunStatus status,
      RecommendationRunV3.Metadata metadata,
      List<String> warnings,
      List<String> autoSelectedInventoryIds,
      List<RecommendationRunV3.Alternative> alternatives,
      List<RecommendationRunV3.CityCluster> cityClusters,
      V3ErrorCode errorCode,
      String errorMessage) {
    Update update =
        new Update()
            .set("status", status)
            .set("completionPercentage", 100)
            .set("completedAt", LocalDateTime.now())
            .set("metadata", metadata)
            .set("warnings", warnings)
            .set("autoSelectedInventoryIds", autoSelectedInventoryIds);
    if (alternatives != null && !alternatives.isEmpty()) {
      update.set("alternatives", alternatives);
    }
    if (cityClusters != null && !cityClusters.isEmpty()) {
      update.set("cityClusters", cityClusters);
    }
    if (errorCode != null) {
      update.set("errorCode", errorCode.name()).set("errorMessage", errorMessage);
    }
    mongoTemplate.updateFirst(byRunId(runId), update, RecommendationRunV3.class);
  }

  /** Terminal FAILED update — the run never stays IN_PROGRESS after a pipeline exception. */
  public void fail(String runId, V3ErrorCode errorCode, String message, List<String> warnings) {
    mongoTemplate.updateFirst(
        byRunId(runId),
        new Update()
            .set("status", RecommendationRunV3.RunStatus.FAILED)
            .set("completedAt", LocalDateTime.now())
            .set("errorCode", errorCode.name())
            .set("errorMessage", message)
            .set("warnings", warnings),
        RecommendationRunV3.class);
  }

  private static Query byRunId(String runId) {
    return new Query(Criteria.where("runId").is(runId));
  }
}
