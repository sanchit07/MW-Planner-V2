package com.mw.planner.config;

import com.mw.planner.domain.InventoryMessageLog;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Creates the indexes for the {@code inventory_message_logs} collection at startup.
 *
 * <p>{@link InventoryMessageLog} carries no index annotations on purpose, so this initializer is
 * the sole index creator for the collection (Spring's {@code auto-index-creation} does nothing for
 * it).
 *
 * <ul>
 *   <li>A TTL index on {@code receivedAt} that expires documents after a fixed {@link #TTL} of 3
 *       days (259200s) — this caps collection growth; the data is for debugging only.
 *   <li>A plain index on {@code inventoryId} backing {@code
 *       findByInventoryIdOrderByReceivedAtDesc}.
 * </ul>
 *
 * <p>Each {@code ensureIndex} call is isolated: if an index with conflicting options already exists
 * (e.g. a TTL index created earlier with a different {@code expireAfterSeconds}), the conflict is
 * caught, logged as a warning, and startup continues — it never crashes the application.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryMessageLogIndexInitializer implements ApplicationRunner {

  /** Fixed 3-day retention (259200 seconds). Hardcoded — no config lookup. */
  private static final Duration TTL = Duration.ofDays(3);

  private final MongoTemplate mongoTemplate;

  @Override
  public void run(ApplicationArguments args) {
    IndexOperations indexOps = mongoTemplate.indexOps(InventoryMessageLog.class);

    ensureIndex(
        indexOps,
        new Index().on("receivedAt", Sort.Direction.ASC).expire(TTL),
        "TTL index on receivedAt (expireAfterSeconds=" + TTL.toSeconds() + ")");

    ensureIndex(
        indexOps, new Index().on("inventoryId", Sort.Direction.ASC), "index on inventoryId");
  }

  private void ensureIndex(IndexOperations indexOps, Index index, String description) {
    try {
      indexOps.ensureIndex(index);
      log.info("Ensured inventory_message_logs {}", description);
    } catch (Exception e) {
      log.warn(
          "Could not ensure inventory_message_logs {} (continuing startup): {}",
          description,
          e.getMessage());
    }
  }
}
