package com.mw.planner.service.availability;

import com.mw.planner.domain.AvailabilitySyncStatus;
import com.mw.planner.domain.Inventory;
import com.mw.planner.domain.InventoryAvailabilityRecord;
import com.mw.planner.dto.InventoryAvailabilityRequestDTO;
import com.mw.planner.repository.AvailabilitySyncStatusRepository;
import com.mw.planner.repository.InventoryAvailabilityRecordRepository;
import com.mw.planner.repository.InventoryRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * IMS availability sync pipeline.
 *
 * <p>Ingests per-inventory availability from the IMS feed into the canonical
 * `inventory_availability` store (scheduled + manually triggerable), tracks sync status, and serves
 * availability reads from the synced store so every surface sees the same data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImsAvailabilitySyncService {

  private static final String SOURCE = "IMS";
  private static final int SYNC_BATCH_SIZE = 500;

  /**
   * Number of batches processed concurrently. Bounded (not one thread per batch) so a 65k-catalog
   * run doesn't open 130 simultaneous Mongo bulk writes; 8 workers keeps well within the default
   * connection pool while turning a ~25 minute serial run into a few minutes.
   */
  private static final int SYNC_PARALLELISM = 8;

  private final ImsAvailabilityFeed imsAvailabilityFeed;
  private final InventoryAvailabilityRecordRepository availabilityRepository;
  private final AvailabilitySyncStatusRepository syncStatusRepository;
  private final InventoryRepository inventoryRepository;
  private final MongoTemplate mongoTemplate;

  /** Single-flight guard: at most one full sync runs at a time; extra triggers are rejected. */
  private final AtomicBoolean syncInFlight = new AtomicBoolean(false);

  /**
   * Kick off a full sync in the background (used by the manual trigger; a full run over the whole
   * inventory catalog takes minutes, so the HTTP request must not block on it).
   */
  public boolean syncAllAsync(AvailabilitySyncStatus.Trigger trigger) {
    if (!syncInFlight.compareAndSet(false, true)) {
      log.info("IMS availability sync already running; rejecting {} trigger", trigger);
      return false;
    }
    Thread.ofVirtual()
        .name("ims-availability-sync")
        .start(
            () -> {
              try {
                doSyncAll(trigger);
              } catch (Exception e) {
                log.error("Async IMS availability sync failed", e);
              } finally {
                syncInFlight.set(false);
              }
            });
    return true;
  }

  /** Scheduled full sync (default: every 6 hours). */
  @Scheduled(cron = "${mw-planner.scheduler.ims-availability-sync.cron:0 0 */6 * * *}")
  public void scheduledSync() {
    try {
      syncAll(AvailabilitySyncStatus.Trigger.SCHEDULED);
    } catch (Exception e) {
      // Failure already recorded in sync status; keep the scheduler alive.
      log.error("Scheduled IMS availability sync failed", e);
    }
  }

  /**
   * Full sync of all known inventories from the IMS feed. Single-flight: returns the current status
   * without starting another run when a sync is already in progress.
   */
  public AvailabilitySyncStatus syncAll(AvailabilitySyncStatus.Trigger trigger) {
    if (!syncInFlight.compareAndSet(false, true)) {
      log.info("IMS availability sync already running; skipping {} trigger", trigger);
      return getStatus();
    }
    try {
      return doSyncAll(trigger);
    } finally {
      syncInFlight.set(false);
    }
  }

  private AvailabilitySyncStatus doSyncAll(AvailabilitySyncStatus.Trigger trigger) {
    Instant startedAt = Instant.now();
    AvailabilitySyncStatus status =
        syncStatusRepository
            .findById(AvailabilitySyncStatus.IMS_AVAILABILITY_ID)
            .orElseGet(
                () ->
                    AvailabilitySyncStatus.builder()
                        .id(AvailabilitySyncStatus.IMS_AVAILABILITY_ID)
                        .build());
    status.setState(AvailabilitySyncStatus.State.RUNNING);
    status.setTrigger(trigger);
    status.setStartedAt(startedAt);
    status.setCompletedAt(null);
    status.setError(null);
    syncStatusRepository.save(status);

    try {
      // Raw distinct read: legacy inventory documents can fail domain mapping
      // (e.g. weekday enums stored as numeric strings); one bad doc must not
      // abort the whole sync.
      List<String> externalIds =
          mongoTemplate
              .getCollection(mongoTemplate.getCollectionName(Inventory.class))
              .distinct("externalId", String.class)
              .into(new ArrayList<>());
      // Batched: with tens of thousands of inventories, per-id queries would
      // make each run take many minutes.
      List<String> cleanIds =
          externalIds.stream().filter(id -> id != null && !id.isBlank()).toList();
      List<List<String>> batches = new ArrayList<>();
      for (int from = 0; from < cleanIds.size(); from += SYNC_BATCH_SIZE) {
        batches.add(cleanIds.subList(from, Math.min(from + SYNC_BATCH_SIZE, cleanIds.size())));
      }

      // Parallel batch processing: each batch does its own tolerant inventory
      // load, payload generation and one bulk upsert. Bounded parallelism keeps
      // Mongo connection pressure predictable while cutting a ~25 minute serial
      // run down to a few minutes.
      AtomicInteger synced = new AtomicInteger();
      Queue<String> failures = new ConcurrentLinkedQueue<>();
      try (ExecutorService pool = Executors.newFixedThreadPool(SYNC_PARALLELISM)) {
        List<Future<?>> futures = new ArrayList<>();
        for (List<String> batch : batches) {
          futures.add(pool.submit(() -> syncBatch(batch, synced, failures)));
        }
        for (Future<?> f : futures) {
          f.get(); // syncBatch never throws for per-inventory failures; this surfaces infra errors.
        }
      }

      Instant completedAt = Instant.now();
      status.setCompletedAt(completedAt);
      status.setInventoryCount(synced.get());
      if (!failures.isEmpty()) {
        List<String> failureList = new ArrayList<>(failures);
        status.setState(AvailabilitySyncStatus.State.FAILED);
        status.setError(
            "Failed for "
                + failureList.size()
                + " of "
                + (synced.get() + failureList.size())
                + " inventories: "
                + String.join("; ", failureList.subList(0, Math.min(5, failureList.size()))));
      } else {
        status.setState(AvailabilitySyncStatus.State.SUCCESS);
        status.setError(null);
        status.setLastSuccessAt(completedAt);
      }
      syncStatusRepository.save(status);
      log.info(
          "IMS availability sync completed: {} inventories, {} failures ({} trigger)",
          synced.get(),
          failures.size(),
          trigger);
      return status;
    } catch (Exception e) {
      status.setState(AvailabilitySyncStatus.State.FAILED);
      status.setCompletedAt(Instant.now());
      status.setError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
      syncStatusRepository.save(status);
      log.error("IMS availability sync failed", e);
      return status;
    }
  }

  /**
   * Sync one batch: tolerant inventory load, payload generation, single bulk upsert. Per-inventory
   * failures are recorded and never abort the batch; only infrastructure errors (e.g. the bulk
   * write itself) propagate.
   */
  private void syncBatch(List<String> batch, AtomicInteger synced, Queue<String> failures) {
    Map<String, Inventory> inventoryById = safeLoadInventories(batch);
    // Bulk upsert keyed on externalId: per-document saves make a full-catalog
    // run take tens of minutes.
    BulkOperations bulkOps =
        mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, InventoryAvailabilityRecord.class);
    Instant now = Instant.now();
    int inBatch = 0;
    for (String externalId : batch) {
      try {
        Map<String, Object> payload =
            imsAvailabilityFeed.fetchAvailability(externalId, inventoryById.get(externalId));
        bulkOps.upsert(
            Query.query(Criteria.where("externalId").is(externalId)),
            new Update().set("payload", payload).set("syncedAt", now).set("source", SOURCE));
        inBatch++;
      } catch (Exception e) {
        failures.add(externalId + ": " + e.getMessage());
        log.warn("IMS availability sync failed for inventory {}", externalId, e);
      }
    }
    if (inBatch > 0) {
      bulkOps.execute();
      synced.addAndGet(inBatch);
    }
  }

  /** Whether a full sync is currently running. */
  public boolean isSyncRunning() {
    return syncInFlight.get();
  }

  /** Current sync status (may be null when no sync has run yet). */
  public AvailabilitySyncStatus getStatus() {
    return syncStatusRepository.findById(AvailabilitySyncStatus.IMS_AVAILABILITY_ID).orElse(null);
  }

  /**
   * Availability for the requested inventories, served from the synced store. Inventories missing
   * from the store are ingested on demand from the IMS feed (production-shaped cache-miss path) so
   * all surfaces stay consistent.
   */
  public Map<String, Object> getAvailability(InventoryAvailabilityRequestDTO request) {
    List<String> requestedIds =
        request.getInventoryIds() != null ? request.getInventoryIds() : List.of();

    Map<String, Object> inventoriesOut = new LinkedHashMap<>();
    Map<String, InventoryAvailabilityRecord> byExternalId = new HashMap<>();
    for (InventoryAvailabilityRecord record :
        availabilityRepository.findByExternalIdIn(requestedIds)) {
      byExternalId.put(record.getExternalId(), record);
    }

    List<String> missing = new ArrayList<>();
    for (String id : requestedIds) {
      InventoryAvailabilityRecord record = byExternalId.get(id);
      if (record != null && record.getPayload() != null) {
        inventoriesOut.put(id, record.getPayload());
      } else {
        missing.add(id);
      }
    }

    String onDemandError = null;
    if (!missing.isEmpty()) {
      for (String id : missing) {
        try {
          Map<String, Object> payload =
              imsAvailabilityFeed.fetchAvailability(id, safeLoadInventory(id));
          upsertRecord(id, payload);
          inventoriesOut.put(id, payload);
        } catch (Exception e) {
          onDemandError = "IMS feed unavailable for inventory " + id + ": " + e.getMessage();
          log.warn("On-demand IMS availability ingest failed for {}", id, e);
        }
      }
    }

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("inventories", inventoriesOut);
    response.put("sync", buildSyncInfo(requestedIds, onDemandError));
    return response;
  }

  private Map<String, Object> buildSyncInfo(List<String> requestedIds, String onDemandError) {
    AvailabilitySyncStatus status = getStatus();

    Instant lastSyncedAt = null;
    for (InventoryAvailabilityRecord record :
        availabilityRepository.findByExternalIdIn(requestedIds)) {
      if (record.getSyncedAt() != null
          && (lastSyncedAt == null || record.getSyncedAt().isBefore(lastSyncedAt))) {
        // Oldest synced record of the requested set — the honest "data as of" marker.
        lastSyncedAt = record.getSyncedAt();
      }
    }
    if (lastSyncedAt == null && status != null) {
      lastSyncedAt = status.getLastSuccessAt();
    }

    Map<String, Object> sync = new LinkedHashMap<>();
    sync.put("source", SOURCE);
    sync.put("lastSyncedAt", lastSyncedAt != null ? lastSyncedAt.toString() : null);
    sync.put("status", status != null ? status.getState().name() : "SUCCESS");
    String error = onDemandError;
    if (error == null
        && status != null
        && status.getState() == AvailabilitySyncStatus.State.FAILED) {
      error = status.getError();
    }
    sync.put("error", error);
    return sync;
  }

  /**
   * Load an inventory tolerantly: legacy documents that fail domain mapping yield {@code null} so
   * the feed falls back to its default schedule instead of failing the inventory.
   */
  private Inventory safeLoadInventory(String externalId) {
    try {
      return inventoryRepository.findByExternalIdIn(List.of(externalId)).stream()
          .findFirst()
          .orElse(null);
    } catch (Exception e) {
      log.debug("Inventory {} failed domain mapping; using feed defaults", externalId, e);
      return null;
    }
  }

  /**
   * Batch-load inventories; if the whole batch fails domain mapping, fall back to per-id loads so
   * only the offending documents degrade to feed defaults.
   */
  private Map<String, Inventory> safeLoadInventories(List<String> externalIds) {
    Map<String, Inventory> byId = new HashMap<>();
    try {
      for (Inventory inv : inventoryRepository.findByExternalIdIn(externalIds)) {
        if (inv.getExternalId() != null) byId.put(inv.getExternalId(), inv);
      }
    } catch (Exception e) {
      log.debug("Batch inventory load failed domain mapping; retrying per id", e);
      for (String id : externalIds) {
        Inventory inv = safeLoadInventory(id);
        if (inv != null) byId.put(id, inv);
      }
    }
    return byId;
  }

  private void upsertRecord(String externalId, Map<String, Object> payload) {
    InventoryAvailabilityRecord record =
        availabilityRepository
            .findByExternalId(externalId)
            .orElseGet(() -> InventoryAvailabilityRecord.builder().externalId(externalId).build());
    record.setPayload(payload);
    record.setSyncedAt(Instant.now());
    record.setSource(SOURCE);
    availabilityRepository.save(record);
  }
}
