package com.mw.planner.config;

import com.mw.planner.domain.Inventory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.stereotype.Component;

/**
 * Ensures the database-level dedupe guarantee for the {@code inventories} collection: a PARTIAL
 * UNIQUE index on {@code externalId}. Together with the atomic upsert in {@code
 * InventoryRepositoryImpl#upsertByNaturalKey}, this prevents the inventory-sync RabbitMQ consumer
 * from ever creating duplicate documents for the same external inventory.
 *
 * <p>The index is partial — scoped to documents where {@code externalId} is a string — so the many
 * existing documents with a null/missing {@code externalId} do not collide on a single null key.
 *
 * <p>This is written DEFENSIVELY, mirroring {@link InventoryMessageLogIndexInitializer}: the {@code
 * ensureIndex} call is wrapped in try/catch. If the collection currently contains duplicate {@code
 * externalId} values (which is exactly the bug this card addresses), index creation will throw a
 * duplicate-key error — we LOG A CLEAR WARNING and continue so application startup never fails. The
 * unique index will only actually build once a one-time prod dedupe migration has removed the
 * pre-existing duplicates.
 *
 * <p>The unique constraint is intentionally NOT declared via {@code @Indexed(unique = true)} on the
 * entity field, because {@code auto-index-creation} is enabled and that would hard-fail startup on
 * existing duplicates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryIndexInitializer implements ApplicationRunner {

  private static final String UNIQUE_EXTERNAL_ID_INDEX = "uniq_externalId_partial";

  private final MongoTemplate mongoTemplate;

  @Override
  public void run(ApplicationArguments args) {
    IndexOperations indexOps = mongoTemplate.indexOps(Inventory.class);

    // Only enforce uniqueness over documents that actually carry a string externalId, so existing
    // null/missing externalIds neither collide with each other nor block index creation.
    Document partialFilter =
        new Document("externalId", new Document("$exists", true).append("$type", "string"));

    Index uniqueExternalId =
        new Index()
            .named(UNIQUE_EXTERNAL_ID_INDEX)
            .on("externalId", Sort.Direction.ASC)
            .unique()
            .partial(PartialIndexFilter.of(partialFilter));

    ensureIndex(
        indexOps,
        uniqueExternalId,
        "partial unique index on externalId (" + UNIQUE_EXTERNAL_ID_INDEX + ")");
  }

  private void ensureIndex(IndexOperations indexOps, Index index, String description) {
    try {
      indexOps.ensureIndex(index);
      log.info("Ensured inventories {}", description);
    } catch (Exception e) {
      log.warn(
          "Could not ensure inventories {} (continuing startup): {}. "
              + "Pre-existing duplicate externalId values likely exist — a one-time dedupe "
              + "migration is required before this unique index can build.",
          description,
          e.getMessage());
    }
  }
}
