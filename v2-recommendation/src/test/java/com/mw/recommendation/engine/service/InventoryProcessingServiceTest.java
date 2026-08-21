package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.ExternalInventoryMessageDTO;
import com.mw.recommendation.engine.rabbitmq.ExternalInventoryMessageConverter;
import com.mw.recommendation.engine.repository.InventoryRepository;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class InventoryProcessingServiceTest {

  @Mock private ExternalInventoryMessageConverter messageConverter;
  @Mock private InventoryRepository inventoryRepository;
  @Mock private MongoTemplate mongoTemplate;

  private InventoryProcessingService inventoryProcessingService;

  private ExternalInventoryMessageDTO testMessage;
  private Inventory testInventory;

  @BeforeEach
  void setUp() {
    inventoryProcessingService =
        new InventoryProcessingService(
            messageConverter, inventoryRepository, mongoTemplate, Optional.empty());

    testMessage = new ExternalInventoryMessageDTO();
    testMessage.setId("inv-123");
    testMessage.setReferenceId("ref-123");
    testMessage.setName("Test Inventory");

    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setPlatform("source");
    externalId.setExternalId("ref-123");
    testMessage.setExternalIds(Collections.singletonList(externalId));

    testInventory = new Inventory();
    testInventory.setInventoryId("inv-123");
    testInventory.setReferenceId("ref-123");
    testInventory.setName("Test Inventory");
  }

  // ─── Atomic upsert tests (race condition fix) ────────────────────────────

  @Test
  @DisplayName("Should use atomic upsert instead of read-then-write to prevent race conditions")
  void testSaveInventoryUsesAtomicUpsert() {
    when(messageConverter.convertToInventory(testMessage)).thenReturn(testInventory);

    inventoryProcessingService.processInventoryMessage(testMessage);

    verify(mongoTemplate, times(1))
        .upsert(any(Query.class), any(Update.class), eq(Inventory.class));
    verify(inventoryRepository, never()).findFirstByInventoryId(any());
    verify(inventoryRepository, never()).findByReferenceId(any());
    verify(inventoryRepository, never()).save(any(Inventory.class));
  }

  @Test
  @DisplayName("Should upsert using inventoryId as primary filter when present")
  void testUpsertFiltersByInventoryId() {
    when(messageConverter.convertToInventory(testMessage)).thenReturn(testInventory);

    inventoryProcessingService.processInventoryMessage(testMessage);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).upsert(queryCaptor.capture(), any(Update.class), eq(Inventory.class));
    assertTrue(
        queryCaptor.getValue().getQueryObject().toJson().contains("inv-123"),
        "Query must filter by inventoryId");
  }

  @Test
  @DisplayName("Should upsert using referenceId as fallback filter when inventoryId is null")
  void testUpsertFiltersByReferenceIdWhenInventoryIdNull() {
    Inventory refOnlyInventory = new Inventory();
    refOnlyInventory.setReferenceId("ref-only-456");
    when(messageConverter.convertToInventory(testMessage)).thenReturn(refOnlyInventory);

    inventoryProcessingService.processInventoryMessage(testMessage);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).upsert(queryCaptor.capture(), any(Update.class), eq(Inventory.class));
    assertTrue(
        queryCaptor.getValue().getQueryObject().toJson().contains("ref-only-456"),
        "Query must filter by referenceId when inventoryId is null");
  }

  @Test
  @DisplayName("Should skip upsert and log warning when both inventoryId and referenceId are null")
  void testSkipsUpsertWhenBothIdsNull() {
    Inventory nullIdsInventory = new Inventory();
    when(messageConverter.convertToInventory(testMessage)).thenReturn(nullIdsInventory);

    inventoryProcessingService.processInventoryMessage(testMessage);

    verify(mongoTemplate, never()).upsert(any(), any(), eq(Inventory.class));
  }

  @Test
  @DisplayName("Should include name in upsert update document when present")
  void testUpsertIncludesNonNullFields() {
    testInventory.setName("Billboard Central");
    testInventory.setClassification("Digital");
    when(messageConverter.convertToInventory(testMessage)).thenReturn(testInventory);

    inventoryProcessingService.processInventoryMessage(testMessage);

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate).upsert(any(Query.class), updateCaptor.capture(), eq(Inventory.class));
    String updateJson = updateCaptor.getValue().getUpdateObject().toJson();
    assertTrue(updateJson.contains("Billboard Central"), "Update must include name");
    assertTrue(updateJson.contains("Digital"), "Update must include classification");
  }

  @Test
  @DisplayName("Should set both archived and active fields when archived is present")
  void testUpsertSetsArchivedAndActive() {
    testInventory.setArchived(true);
    when(messageConverter.convertToInventory(testMessage)).thenReturn(testInventory);

    inventoryProcessingService.processInventoryMessage(testMessage);

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate).upsert(any(Query.class), updateCaptor.capture(), eq(Inventory.class));
    String updateJson = updateCaptor.getValue().getUpdateObject().toJson();
    assertTrue(updateJson.contains("archived"), "Update must include archived field");
    assertTrue(updateJson.contains("active"), "Update must include active field");
  }

  @Test
  @DisplayName(
      "Should include referenceId in $set so null referenceId on existing docs gets filled in")
  void testReferenceIdUsesSetSoExistingNullGetsUpdated() {
    when(messageConverter.convertToInventory(testMessage)).thenReturn(testInventory);

    inventoryProcessingService.processInventoryMessage(testMessage);

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate).upsert(any(Query.class), updateCaptor.capture(), eq(Inventory.class));

    String setJson = updateCaptor.getValue().getUpdateObject().get("$set").toString();
    assertTrue(
        setJson.contains("referenceId"),
        "referenceId must be in $set to update existing null values");
  }

  @Test
  @DisplayName("Should not include null fields in the update document")
  void testNullFieldsExcludedFromUpdate() {
    Inventory sparseInventory = new Inventory();
    sparseInventory.setInventoryId("inv-sparse");
    sparseInventory.setName("Sparse");
    // all other fields are null — must not appear in update

    when(messageConverter.convertToInventory(testMessage)).thenReturn(sparseInventory);

    inventoryProcessingService.processInventoryMessage(testMessage);

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate).upsert(any(Query.class), updateCaptor.capture(), eq(Inventory.class));

    String setJson = updateCaptor.getValue().getUpdateObject().get("$set").toString();
    assertFalse(setJson.contains("classification"), "null classification must not be in update");
    assertFalse(setJson.contains("digitalFields"), "null digitalFields must not be in update");
    assertFalse(setJson.contains("archived"), "null archived must not be in update");
    assertFalse(
        setJson.contains("locationHierarchy"), "null locationHierarchy must not be in update");
  }

  // ─── externalIds preservation (Device ID sync) ─────────────────────────────

  @Test
  @DisplayName("Should include externalIds in $set when the incoming list is populated")
  void testUpsertIncludesPopulatedExternalIds() {
    Inventory.ExternalId cms =
        Inventory.ExternalId.builder().platform("CMS").externalRefId("DEVICE-999").build();
    testInventory.setExternalIds(java.util.List.of(cms));
    when(messageConverter.convertToInventory(testMessage)).thenReturn(testInventory);

    inventoryProcessingService.processInventoryMessage(testMessage);

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate).upsert(any(Query.class), updateCaptor.capture(), eq(Inventory.class));
    String setJson = updateCaptor.getValue().getUpdateObject().get("$set").toString();
    assertTrue(setJson.contains("externalIds"), "populated externalIds must be in $set");
    assertTrue(setJson.contains("DEVICE-999"), "the CMS device id must be persisted");
  }

  @Test
  @DisplayName(
      "Should NOT $set externalIds when the incoming list is empty (preserve stored Device ID)")
  void testUpsertSkipsEmptyExternalIds() {
    testInventory.setExternalIds(Collections.emptyList());
    when(messageConverter.convertToInventory(testMessage)).thenReturn(testInventory);

    inventoryProcessingService.processInventoryMessage(testMessage);

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate).upsert(any(Query.class), updateCaptor.capture(), eq(Inventory.class));
    String setJson = updateCaptor.getValue().getUpdateObject().get("$set").toString();
    assertFalse(
        setJson.contains("externalIds"),
        "an empty externalIds list must not overwrite the stored ids");
  }

  // ─── Delete tests ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("Should delete inventory by inventoryId via repository")
  void testDeleteInventoryCallsRepository() {
    doNothing().when(inventoryRepository).deleteByInventoryId("inv-123");

    inventoryProcessingService.deleteInventory("inv-123");

    verify(inventoryRepository, times(1)).deleteByInventoryId("inv-123");
  }

  @Test
  @DisplayName("Should propagate exception when delete fails")
  void testDeleteInventoryPropagatesException() {
    doThrow(new RuntimeException("Database error"))
        .when(inventoryRepository)
        .deleteByInventoryId("inv-123");

    assertThrows(
        RuntimeException.class, () -> inventoryProcessingService.deleteInventory("inv-123"));
    verify(inventoryRepository, times(1)).deleteByInventoryId("inv-123");
  }

  // ─── Error handling tests ─────────────────────────────────────────────────

  @Test
  @DisplayName("Should propagate exception when message conversion fails")
  void testPropagatesExceptionOnConversionFailure() {
    when(messageConverter.convertToInventory(testMessage))
        .thenThrow(new RuntimeException("Conversion error"));

    assertThrows(
        RuntimeException.class,
        () -> inventoryProcessingService.processInventoryMessage(testMessage));
  }

  @Test
  @DisplayName("Should propagate exception when upsert fails")
  void testPropagatesExceptionOnUpsertFailure() {
    when(messageConverter.convertToInventory(testMessage)).thenReturn(testInventory);
    doThrow(new RuntimeException("Database error"))
        .when(mongoTemplate)
        .upsert(any(Query.class), any(Update.class), eq(Inventory.class));

    assertThrows(
        RuntimeException.class,
        () -> inventoryProcessingService.processInventoryMessage(testMessage));
  }
}
