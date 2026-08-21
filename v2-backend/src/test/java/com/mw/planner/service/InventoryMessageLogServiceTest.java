package com.mw.planner.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.InventoryMessageLog;
import com.mw.planner.dto.InventoryUpdateMessageDTO;
import com.mw.planner.enums.MessageConsumeStatus;
import com.mw.planner.repository.InventoryMessageLogRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryMessageLogServiceTest {

  @Mock private InventoryMessageLogRepository repository;

  @InjectMocks private InventoryMessageLogService inventoryMessageLogService;

  private InventoryUpdateMessageDTO message(String inventoryId, String operation) {
    InventoryUpdateMessageDTO dto = new InventoryUpdateMessageDTO();
    dto.setId("msg-1");
    dto.setInventoryId(inventoryId);
    dto.setOperation(operation);
    dto.setOccurredAt("2026-01-08T04:25:48.209336Z");
    return dto;
  }

  @Test
  void log_mapsAndSavesProcessedRecord() {
    Instant receivedAt = Instant.parse("2026-06-18T10:00:00Z");
    Instant processedAt = Instant.parse("2026-06-18T10:00:01Z");
    InventoryUpdateMessageDTO message = message("inv-123", "refresh");

    inventoryMessageLogService.log(
        "{\"raw\":\"payload\"}",
        message,
        null,
        MessageConsumeStatus.PROCESSED,
        null,
        receivedAt,
        processedAt);

    ArgumentCaptor<InventoryMessageLog> captor = ArgumentCaptor.forClass(InventoryMessageLog.class);
    verify(repository).save(captor.capture());
    InventoryMessageLog saved = captor.getValue();
    assertEquals("inv-123", saved.getInventoryId());
    assertEquals("refresh", saved.getOperation());
    assertEquals("{\"raw\":\"payload\"}", saved.getRawPayload());
    assertEquals(MessageConsumeStatus.PROCESSED, saved.getStatus());
    assertEquals(receivedAt, saved.getReceivedAt());
    assertEquals(processedAt, saved.getProcessedAt());
    assertNull(saved.getErrorMessage());
  }

  @Test
  void log_doesNotPropagateWhenRepositoryThrows() {
    when(repository.save(any())).thenThrow(new RuntimeException("mongo down"));

    assertDoesNotThrow(
        () ->
            inventoryMessageLogService.log(
                "{\"raw\":\"payload\"}",
                message("inv-123", "refresh"),
                null,
                MessageConsumeStatus.RECEIVED,
                null,
                Instant.parse("2026-06-18T10:00:00Z"),
                null));

    verify(repository).save(any());
  }

  @Test
  void log_failedPathWithNullInventoryIdStillPersistsPayloadAndError() {
    InventoryUpdateMessageDTO message = message(null, "refresh");

    inventoryMessageLogService.log(
        "{\"unparseable\":true}",
        message,
        null,
        MessageConsumeStatus.FAILED,
        "boom",
        Instant.parse("2026-06-18T10:00:00Z"),
        null);

    ArgumentCaptor<InventoryMessageLog> captor = ArgumentCaptor.forClass(InventoryMessageLog.class);
    verify(repository).save(captor.capture());
    InventoryMessageLog saved = captor.getValue();
    assertNull(saved.getInventoryId());
    assertEquals("{\"unparseable\":true}", saved.getRawPayload());
    assertEquals(MessageConsumeStatus.FAILED, saved.getStatus());
    assertEquals("boom", saved.getErrorMessage());
    assertNull(saved.getProcessedAt());
  }
}
