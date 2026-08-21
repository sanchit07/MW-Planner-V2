package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.Inventory;
import com.mw.planner.dto.ExternalInventoryMessageDTO;
import com.mw.planner.rabbitmq.ExternalInventoryMessageConverter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryProcessingServiceTest {

  @Mock private ExternalInventoryMessageConverter messageConverter;

  @Mock private InventoryService inventoryService;

  @Mock private InventoryCountrySummaryService inventoryCountrySummaryService;

  @Mock private VirtualThreadService virtualThreadService;

  @InjectMocks private InventoryProcessingService inventoryProcessingService;

  private ExternalInventoryMessageDTO testMessage;
  private Inventory testInventory;

  @BeforeEach
  void setUp() {
    // Setup test message
    testMessage = new ExternalInventoryMessageDTO();
    testMessage.setName("Test Billboard");
    testMessage.setId("507f1f77bcf86cd799439011");
    testMessage.setReferenceId("TEST-REF-001");
    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setExternalId("TEST-REF-001");
    testMessage.setExternalIds(List.of(externalId));

    // Setup test inventory
    testInventory = new Inventory();
    testInventory.setId("inventory-id-123");
    testInventory.setReferenceId("TEST-REF-001");
    testInventory.setName("Test Billboard");
    testInventory.setExternalId("507f1f77bcf86cd799439011");
  }

  @Test
  void testProcessInventoryMessage_PerformsSingleAtomicUpsert() {
    // Given
    when(messageConverter.convertToInventory(testMessage)).thenReturn(testInventory);
    when(inventoryService.upsertByNaturalKey(any(Inventory.class))).thenReturn(testInventory);

    // When
    inventoryProcessingService.processInventoryMessage(testMessage, null);

    // Then — a single atomic upsert is performed, keyed on the converted entity
    verify(messageConverter).convertToInventory(testMessage);
    verify(inventoryService).upsertByNaturalKey(testInventory);

    // The old non-atomic check-then-act SAVE path must never be used (a pre-upsert lookup is now
    // performed only to detect a country change for the summary read-model).
    verify(inventoryService, never()).save(any(Inventory.class));
  }

  @Test
  void testProcessInventoryMessage_SetsMediaOwnerNameOnUpsertedEntity() {
    // Given
    testMessage.setMediaOwnerId("media-owner-123");
    testMessage.setMediaOwnerName("Test Media Owner");

    when(messageConverter.convertToInventory(testMessage)).thenReturn(testInventory);
    when(inventoryService.upsertByNaturalKey(any(Inventory.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    // When
    inventoryProcessingService.processInventoryMessage(testMessage, null);

    // Then
    ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
    verify(inventoryService).upsertByNaturalKey(captor.capture());
    Inventory upserted = captor.getValue();
    org.junit.jupiter.api.Assertions.assertEquals("media-owner-123", upserted.getMediaOwnerId());
    org.junit.jupiter.api.Assertions.assertEquals("Test Media Owner", upserted.getMediaOwnerName());
  }

  @Test
  void testProcessInventoryMessage_PropagatesNonNullFieldsOnlyToUpsert() {
    // Given — a sparse message: only name is populated, all other fields null
    Inventory sparse = new Inventory();
    sparse.setExternalId("507f1f77bcf86cd799439011");
    sparse.setName("Updated Name");
    // referenceId, location, prices, etc. all null

    when(messageConverter.convertToInventory(testMessage)).thenReturn(sparse);
    when(inventoryService.upsertByNaturalKey(any(Inventory.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    // When
    inventoryProcessingService.processInventoryMessage(testMessage, "inv-id");

    // Then — the converted entity is passed through verbatim; the upsert layer is
    // responsible for only $set-ting non-null fields so existing data is never wiped.
    ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
    verify(inventoryService).upsertByNaturalKey(captor.capture());
    Inventory upserted = captor.getValue();
    org.junit.jupiter.api.Assertions.assertEquals("Updated Name", upserted.getName());
    org.junit.jupiter.api.Assertions.assertNull(upserted.getLocation());
    org.junit.jupiter.api.Assertions.assertNull(upserted.getPrices());
  }

  @Test
  void testDeleteInventoryByExternalId_WhenInventoryFound_DeletesInventory() {
    // Given
    String externalId = "507f1f77bcf86cd799439011";
    when(inventoryService.findByExternalId(externalId)).thenReturn(Optional.of(testInventory));
    doNothing().when(inventoryService).deleteById(testInventory.getId());

    // When
    inventoryProcessingService.deleteInventoryByExternalId(externalId);

    // Then
    verify(inventoryService).findByExternalId(externalId);
    verify(inventoryService).deleteById("inventory-id-123");
  }

  @Test
  void testDeleteInventoryByExternalId_WhenInventoryNotFound_DoesNotDelete() {
    // Given
    String externalId = "non-existent-id";
    when(inventoryService.findByExternalId(externalId)).thenReturn(Optional.empty());

    // When
    inventoryProcessingService.deleteInventoryByExternalId(externalId);

    // Then
    verify(inventoryService).findByExternalId(externalId);
    verify(inventoryService, never()).deleteById(anyString());
  }

  @Test
  void testProcessInventoryMessage_RefreshesCountrySummaryForNewCountry() {
    // Given — the converted inventory is located in a country
    Inventory located = new Inventory();
    located.setExternalId("507f1f77bcf86cd799439011");
    Inventory.Location location = new Inventory.Location();
    location.setCountry("India");
    located.setLocation(location);

    when(messageConverter.convertToInventory(testMessage)).thenReturn(located);
    when(inventoryService.upsertByNaturalKey(any(Inventory.class))).thenReturn(located);
    runAsyncSynchronously();

    // When
    inventoryProcessingService.processInventoryMessage(testMessage, "inv-id");

    // Then — the affected country's summary is refreshed on a virtual thread
    verify(inventoryCountrySummaryService).refreshSummaryByCountry("India");
  }

  @Test
  void testProcessInventoryMessage_RefreshesBothCountriesOnCountryChange() {
    // Given — the inventory previously lived in India and now moves to Nepal
    Inventory previous = new Inventory();
    Inventory.Location previousLocation = new Inventory.Location();
    previousLocation.setCountry("India");
    previous.setLocation(previousLocation);

    Inventory updated = new Inventory();
    updated.setExternalId("507f1f77bcf86cd799439011");
    Inventory.Location updatedLocation = new Inventory.Location();
    updatedLocation.setCountry("Nepal");
    updated.setLocation(updatedLocation);

    when(messageConverter.convertToInventory(testMessage)).thenReturn(updated);
    when(inventoryService.findByExternalId("507f1f77bcf86cd799439011"))
        .thenReturn(Optional.of(previous));
    when(inventoryService.upsertByNaturalKey(any(Inventory.class))).thenReturn(updated);
    runAsyncSynchronously();

    // When
    inventoryProcessingService.processInventoryMessage(testMessage, "inv-id");

    // Then — both the old and the new country summaries are refreshed
    verify(inventoryCountrySummaryService).refreshSummaryByCountry("India");
    verify(inventoryCountrySummaryService).refreshSummaryByCountry("Nepal");
  }

  @Test
  void testDeleteInventoryByExternalId_RefreshesCountrySummary() {
    // Given
    String externalId = "507f1f77bcf86cd799439011";
    Inventory.Location location = new Inventory.Location();
    location.setCountry("India");
    testInventory.setLocation(location);
    when(inventoryService.findByExternalId(externalId)).thenReturn(Optional.of(testInventory));
    runAsyncSynchronously();

    // When
    inventoryProcessingService.deleteInventoryByExternalId(externalId);

    // Then
    verify(inventoryService).deleteById("inventory-id-123");
    verify(inventoryCountrySummaryService).refreshSummaryByCountry("India");
  }

  /** Make the virtual-thread executor run the submitted task inline so refreshes are observable. */
  private void runAsyncSynchronously() {
    when(virtualThreadService.runAsync(any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(0).run();
              return CompletableFuture.completedFuture(null);
            });
  }

  @Test
  void testProcessInventoryMessage_PropagatesVenueTypeIdsToUpsert() {
    // Given — the converted entity carries venueType/venueTypeIds
    Inventory convertedInventory = new Inventory();
    convertedInventory.setExternalId("507f1f77bcf86cd799439011");
    convertedInventory.setName("Test Billboard");
    convertedInventory.setVenueType(List.of("Arrival Hall"));
    convertedInventory.setVenueTypeIds(List.of("305"));

    when(messageConverter.convertToInventory(testMessage)).thenReturn(convertedInventory);
    when(inventoryService.upsertByNaturalKey(any(Inventory.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    // When
    inventoryProcessingService.processInventoryMessage(testMessage, null);

    // Then — the converted venueType/venueTypeIds reach the upsert layer verbatim.
    // (Preservation of existing values when new data is null is the upsert layer's
    // responsibility — covered by InventoryUpsertIntegrationTest.)
    ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
    verify(inventoryService).upsertByNaturalKey(captor.capture());
    assertThat(captor.getValue().getVenueTypeIds()).containsExactly("305");
    assertThat(captor.getValue().getVenueType()).containsExactly("Arrival Hall");
  }
}
