package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import com.mw.recommendation.engine.domain.RecommendationRun;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Finding #1 (crash-recovery). The watchdog must fail stale v2 runs while never touching v1 runs —
 * its query is scoped to {@code pipelineVersion = "v2"}, which v1 runs never carry.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationV2WatchdogTest {

  @Mock private MongoTemplate mongoTemplate;

  private final Clock clock = Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC);

  private RecommendationV2Watchdog watchdog;

  @BeforeEach
  void setUp() {
    watchdog = new RecommendationV2Watchdog(mongoTemplate, clock);
    ReflectionTestUtils.setField(watchdog, "staleTimeoutMinutes", 30L);
  }

  @Test
  void failStaleRuns_isScopedToV2AndSetsFailed() {
    when(mongoTemplate.updateMulti(
            any(Query.class), any(Update.class), eq(RecommendationRun.class)))
        .thenReturn(UpdateResult.acknowledged(1L, 1L, null));

    watchdog.failStaleRuns();

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate)
        .updateMulti(queryCaptor.capture(), updateCaptor.capture(), eq(RecommendationRun.class));

    String query = queryCaptor.getValue().getQueryObject().toString();
    // v1 isolation: the query is restricted to v2 runs that are IN_PROGRESS.
    assertTrue(query.contains("pipelineVersion"), "query must be scoped by pipelineVersion");
    assertTrue(query.contains("v2"), "query must target v2 runs only");
    assertTrue(query.contains("IN_PROGRESS"), "query must target IN_PROGRESS runs");

    String update = updateCaptor.getValue().getUpdateObject().toString();
    assertTrue(update.contains("FAILED"), "stale runs must be marked FAILED");
  }
}
