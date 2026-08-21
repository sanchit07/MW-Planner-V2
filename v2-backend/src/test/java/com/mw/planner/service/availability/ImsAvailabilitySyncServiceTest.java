package com.mw.planner.service.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.mongodb.client.DistinctIterable;
import com.mongodb.client.MongoCollection;
import com.mw.planner.domain.AvailabilitySyncStatus;
import com.mw.planner.domain.InventoryAvailabilityRecord;
import com.mw.planner.dto.InventoryAvailabilityRequestDTO;
import com.mw.planner.repository.AvailabilitySyncStatusRepository;
import com.mw.planner.repository.InventoryAvailabilityRecordRepository;
import com.mw.planner.repository.InventoryRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImsAvailabilitySyncServiceTest {

  @Mock private ImsAvailabilityFeed feed;
  @Mock private InventoryAvailabilityRecordRepository availabilityRepository;
  @Mock private AvailabilitySyncStatusRepository syncStatusRepository;
  @Mock private InventoryRepository inventoryRepository;
  @Mock private MongoTemplate mongoTemplate;
  @Mock private MongoCollection<Document> mongoCollection;
  @Mock private DistinctIterable<String> distinctIterable;
  @Mock private BulkOperations bulkOperations;

  private ImsAvailabilitySyncService service;

  @BeforeEach
  void setUp() {
    service =
        new ImsAvailabilitySyncService(
            feed, availabilityRepository, syncStatusRepository, inventoryRepository, mongoTemplate);

    when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());
    when(syncStatusRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(mongoTemplate.getCollectionName(any(Class.class))).thenReturn("inventories");
    when(mongoTemplate.getCollection("inventories")).thenReturn(mongoCollection);
    when(mongoCollection.distinct("externalId", String.class)).thenReturn(distinctIterable);
    when(mongoTemplate.bulkOps(any(), any(Class.class))).thenReturn(bulkOperations);
    when(inventoryRepository.findByExternalIdIn(anyList())).thenReturn(List.of());
    when(availabilityRepository.findByExternalIdIn(anyList())).thenReturn(List.of());
  }

  private void givenExternalIds(String... ids) {
    when(distinctIterable.into(any()))
        .thenAnswer(
            inv -> {
              ArrayList<String> target = inv.getArgument(0);
              target.addAll(List.of(ids));
              return target;
            });
  }

  @Test
  void syncAllIngestsFeedPayloadsAndReportsSuccess() throws Exception {
    givenExternalIds("EXT-1", "EXT-2");
    when(feed.fetchAvailability(anyString(), any())).thenReturn(Map.of("id", "x"));

    AvailabilitySyncStatus status = service.syncAll(AvailabilitySyncStatus.Trigger.MANUAL);

    assertThat(status.getState()).isEqualTo(AvailabilitySyncStatus.State.SUCCESS);
    assertThat(status.getInventoryCount()).isEqualTo(2);
    assertThat(status.getError()).isNull();
    assertThat(status.getLastSuccessAt()).isNotNull();
  }

  @Test
  void syncAllRecordsPerInventoryFeedFailuresAsFailedStatus() throws Exception {
    givenExternalIds("EXT-OK", "EXT-BAD");
    when(feed.fetchAvailability(anyString(), any()))
        .thenAnswer(
            inv -> {
              if ("EXT-BAD".equals(inv.getArgument(0))) {
                throw new ImsAvailabilityFeed.ImsFeedException("feed down");
              }
              return Map.of("id", "ok");
            });

    AvailabilitySyncStatus status = service.syncAll(AvailabilitySyncStatus.Trigger.SCHEDULED);

    assertThat(status.getState()).isEqualTo(AvailabilitySyncStatus.State.FAILED);
    assertThat(status.getError()).contains("EXT-BAD").contains("feed down");
    assertThat(status.getInventoryCount()).isEqualTo(1);
  }

  @Test
  void manualTriggerIsSingleFlightWhileRunning() throws Exception {
    givenExternalIds("EXT-1");
    CountDownLatch feedEntered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(feed.fetchAvailability(anyString(), any()))
        .thenAnswer(
            inv -> {
              feedEntered.countDown();
              release.await(5, TimeUnit.SECONDS);
              return Map.of("id", "x");
            });

    boolean first = service.syncAllAsync(AvailabilitySyncStatus.Trigger.MANUAL);
    assertThat(first).isTrue();
    assertThat(feedEntered.await(5, TimeUnit.SECONDS)).isTrue();

    // Second trigger while the first is still running must be rejected, not queued.
    assertThat(service.isSyncRunning()).isTrue();
    assertThat(service.syncAllAsync(AvailabilitySyncStatus.Trigger.MANUAL)).isFalse();

    release.countDown();
    for (int i = 0; i < 100 && service.isSyncRunning(); i++) {
      Thread.sleep(50);
    }
    assertThat(service.isSyncRunning()).isFalse();
  }

  @Test
  void syncAllToleratesLegacyDocsThatFailDomainMapping() throws Exception {
    // Legacy inventory documents can fail Spring Data domain mapping wholesale;
    // the sync must fall back per id and still ingest every inventory with feed defaults.
    givenExternalIds("EXT-LEGACY", "EXT-OK");
    when(inventoryRepository.findByExternalIdIn(anyList()))
        .thenThrow(new org.springframework.data.mapping.MappingException("bad weekday enum"));
    when(feed.fetchAvailability(anyString(), any())).thenReturn(Map.of("id", "x"));

    AvailabilitySyncStatus status = service.syncAll(AvailabilitySyncStatus.Trigger.MANUAL);

    assertThat(status.getState()).isEqualTo(AvailabilitySyncStatus.State.SUCCESS);
    assertThat(status.getInventoryCount()).isEqualTo(2);
    // Feed was still called for both ids with null inventory (feed defaults).
    org.mockito.Mockito.verify(feed).fetchAvailability("EXT-LEGACY", null);
    org.mockito.Mockito.verify(feed).fetchAvailability("EXT-OK", null);
  }

  @Test
  void syncAllRecordsFatalErrorAsFailedStatus() throws Exception {
    when(distinctIterable.into(any())).thenThrow(new RuntimeException("mongo down"));

    AvailabilitySyncStatus status = service.syncAll(AvailabilitySyncStatus.Trigger.SCHEDULED);

    assertThat(status.getState()).isEqualTo(AvailabilitySyncStatus.State.FAILED);
    assertThat(status.getError()).contains("mongo down");
    assertThat(status.getLastSuccessAt()).isNull();
  }

  @Test
  void getAvailabilityToleratesLegacyDocOnOnDemandIngest() throws Exception {
    // On-demand ingest of an id whose inventory doc fails domain mapping must
    // fall back to feed defaults instead of failing the read.
    when(availabilityRepository.findByExternalIdIn(anyList())).thenReturn(List.of());
    when(inventoryRepository.findByExternalIdIn(anyList()))
        .thenThrow(new org.springframework.data.mapping.MappingException("bad enum"));
    when(availabilityRepository.findByExternalId(anyString())).thenReturn(Optional.empty());
    when(availabilityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(feed.fetchAvailability(any(), any())).thenReturn(Map.of("id", "fresh"));

    InventoryAvailabilityRequestDTO request = new InventoryAvailabilityRequestDTO();
    request.setInventoryIds(List.of("EXT-LEGACY"));

    Map<String, Object> response = service.getAvailability(request);

    @SuppressWarnings("unchecked")
    Map<String, Object> inventories = (Map<String, Object>) response.get("inventories");
    assertThat(inventories).containsKey("EXT-LEGACY");
    @SuppressWarnings("unchecked")
    Map<String, Object> sync = (Map<String, Object>) response.get("sync");
    assertThat(sync.get("error")).isNull();
  }

  @Test
  void getAvailabilityServesStoreAndIngestsMissingOnDemand() throws Exception {
    InventoryAvailabilityRecord stored =
        InventoryAvailabilityRecord.builder()
            .externalId("EXT-STORED")
            .payload(Map.of("id", "stored"))
            .syncedAt(Instant.parse("2026-08-14T00:00:00Z"))
            .build();
    when(availabilityRepository.findByExternalIdIn(anyList())).thenReturn(List.of(stored));
    when(availabilityRepository.findByExternalId("EXT-MISS")).thenReturn(Optional.empty());
    when(availabilityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(feed.fetchAvailability(any(), any())).thenReturn(Map.of("id", "fresh"));

    InventoryAvailabilityRequestDTO request = new InventoryAvailabilityRequestDTO();
    request.setInventoryIds(List.of("EXT-STORED", "EXT-MISS"));

    Map<String, Object> response = service.getAvailability(request);

    @SuppressWarnings("unchecked")
    Map<String, Object> inventories = (Map<String, Object>) response.get("inventories");
    assertThat(inventories).containsKeys("EXT-STORED", "EXT-MISS");
    @SuppressWarnings("unchecked")
    Map<String, Object> sync = (Map<String, Object>) response.get("sync");
    assertThat(sync).containsKeys("source", "lastSyncedAt", "status");
  }

  @Test
  void getAvailabilitySurfacesOnDemandFeedFailuresInSyncInfo() throws Exception {
    when(availabilityRepository.findByExternalIdIn(anyList())).thenReturn(List.of());
    when(feed.fetchAvailability(any(), any()))
        .thenThrow(new ImsAvailabilityFeed.ImsFeedException("ims offline"));

    InventoryAvailabilityRequestDTO request = new InventoryAvailabilityRequestDTO();
    request.setInventoryIds(List.of("EXT-X"));

    Map<String, Object> response = service.getAvailability(request);

    @SuppressWarnings("unchecked")
    Map<String, Object> sync = (Map<String, Object>) response.get("sync");
    assertThat((String) sync.get("error")).contains("EXT-X");
  }
}
