package com.mw.recommendation.engine.service;

import com.mongodb.client.result.UpdateResult;
import com.mw.recommendation.engine.domain.RecommendationRun;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Crash-recovery safety net for the v2 pipeline (finding #1). The v2 async catch block already
 * marks a run FAILED when processing throws, but a hard process death (OOM kill, pod restart) can
 * leave a run stuck IN_PROGRESS forever. This watchdog periodically flips such stale runs to
 * FAILED.
 *
 * <p><b>v1 isolation:</b> the query is scoped to {@code pipelineVersion = "v2"}, which only v2 runs
 * carry. v1 runs (null pipelineVersion) are never matched, so v1 is unaffected.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationV2Watchdog {

  private final MongoTemplate mongoTemplate;
  private final Clock clock;

  /** A v2 run IN_PROGRESS longer than this is considered stale and failed. */
  @Value("${mw-recommendation-engine.v2.stale-run-timeout-minutes:30}")
  private long staleTimeoutMinutes;

  static final String STALE_ERROR_CODE = "STALE_TIMEOUT";

  @Scheduled(fixedDelayString = "${mw-recommendation-engine.v2.watchdog-interval-ms:300000}")
  public void failStaleRuns() {
    LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(staleTimeoutMinutes);
    Query query =
        new Query(
            Criteria.where("pipelineVersion")
                .is("v2")
                .and("status")
                .is(RecommendationRun.RunStatus.IN_PROGRESS)
                .and("generatedAt")
                .lt(cutoff));
    Update update =
        new Update()
            .set("status", RecommendationRun.RunStatus.FAILED)
            .set("completedAt", LocalDateTime.now(clock))
            .set("errorCode", STALE_ERROR_CODE)
            .set(
                "errorMessage",
                "Run exceeded the " + staleTimeoutMinutes + "-minute processing timeout");

    UpdateResult result = mongoTemplate.updateMulti(query, update, RecommendationRun.class);
    if (result.getModifiedCount() > 0) {
      log.warn(
          "v2 watchdog marked {} stale run(s) FAILED (IN_PROGRESS older than {} min)",
          result.getModifiedCount(),
          staleTimeoutMinutes);
    }
  }
}
