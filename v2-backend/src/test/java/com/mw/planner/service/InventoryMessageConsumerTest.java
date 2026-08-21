package com.mw.planner.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import com.mw.planner.dto.ExternalInventoryMessageDTO;
import com.mw.planner.dto.InventoryUpdateMessageDTO;
import com.mw.planner.enums.MessageConsumeStatus;
import com.mw.planner.rabbitmq.InventoryMessageConsumer;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryMessageConsumerTest {

  @Mock private InventoryProcessingService inventoryProcessingService;

  @Mock private Validator validator;

  @Mock private RedisTemplate<String, String> redisTemplate;

  @Mock private ValueOperations<String, String> valueOperations;

  @Mock private InventoryMessageLogService inventoryMessageLogService;

  @InjectMocks private InventoryMessageConsumer inventoryMessageConsumer;

  private InventoryUpdateMessageDTO testMessage;
  private ExternalInventoryMessageDTO testDetailSnapshot;

  @BeforeEach
  void setUp() {
    // Setup test detail snapshot (the actual inventory message)
    testDetailSnapshot = new ExternalInventoryMessageDTO();
    testDetailSnapshot.setName("Test Billboard");
    testDetailSnapshot.setId("507f1f77bcf86cd799439011");
    testDetailSnapshot.setReferenceId("TEST-REF-001");
    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setExternalId("TEST-REF-001");
    testDetailSnapshot.setExternalIds(List.of(externalId));

    // Setup test message wrapper
    testMessage = new InventoryUpdateMessageDTO();
    testMessage.setId("6ad1f3b5-833b-4774-989e-e746c8a14c19");
    testMessage.setInventoryId("4df21370-0bf6-4581-90bf-24e8c2d585db");
    testMessage.setOperation("refresh");
    testMessage.setOccurredAt("2026-01-08T04:25:48.209336Z");
    testMessage.setDetailSnapshot(testDetailSnapshot);

    // Setup Redis mock (lenient - not all tests use these)
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    lenient().when(redisTemplate.hasKey(anyString())).thenReturn(false);
  }

  @Test
  void testConsume_Success() {
    // Given
    when(validator.validate(any(ExternalInventoryMessageDTO.class))).thenReturn(java.util.Set.of());
    doNothing().when(inventoryProcessingService).processInventoryMessage(any(), any());

    // When (fanout exchanges ignore routing keys, but parameter is still required)
    inventoryMessageConsumer.consume(testMessage, null, 1L, "");

    // Then
    verify(validator).validate(testDetailSnapshot);
    verify(inventoryProcessingService).processInventoryMessage(eq(testDetailSnapshot), any());
    verify(valueOperations).set(anyString(), eq("processed"), any());
  }

  @Test
  void testConsume_WhenOperationNull_SkipsProcessing() {
    InventoryUpdateMessageDTO messageWithNullOp = new InventoryUpdateMessageDTO();
    messageWithNullOp.setId("msg-1");
    messageWithNullOp.setDetailSnapshot(testDetailSnapshot);
    messageWithNullOp.setOperation(null);

    inventoryMessageConsumer.consume(messageWithNullOp, null, 1L, "");

    verify(inventoryProcessingService, never()).processInventoryMessage(any(), any());
    verify(inventoryProcessingService, never()).deleteInventoryByExternalId(any());
  }

  @Test
  void testConsume_WhenRefreshDetailSnapshotNull_SkipsProcessing() {
    testMessage.setDetailSnapshot(null);

    inventoryMessageConsumer.consume(testMessage, null, 1L, "");

    verify(inventoryProcessingService, never()).processInventoryMessage(any(), any());
  }

  @Test
  void testConsume_WhenOperationNotSupported_SkipsProcessing() {
    testMessage.setOperation("create");

    inventoryMessageConsumer.consume(testMessage, null, 1L, "");

    verify(inventoryProcessingService, never()).processInventoryMessage(any(), any());
    verify(inventoryProcessingService, never()).deleteInventoryByExternalId(any());
  }

  @Test
  void testConsume_WhenMessageAlreadyProcessed_SkipsProcessing() {
    when(redisTemplate.hasKey(anyString())).thenReturn(true);
    when(validator.validate(any(ExternalInventoryMessageDTO.class))).thenReturn(java.util.Set.of());

    inventoryMessageConsumer.consume(testMessage, null, 1L, "");

    verify(inventoryProcessingService, never()).processInventoryMessage(any(), any());
    verify(valueOperations, never()).set(anyString(), anyString(), any());
  }

  @Test
  void testConsume_WhenOperationDelete_Success() {
    InventoryUpdateMessageDTO deleteMessage = new InventoryUpdateMessageDTO();
    deleteMessage.setId("6ad1f3b5-833b-4774-989e-e746c8a14c19");
    deleteMessage.setInventoryId("4df21370-0bf6-4581-90bf-24e8c2d585db");
    deleteMessage.setOperation("delete");
    deleteMessage.setOccurredAt("2026-01-08T04:25:48.209336Z");
    deleteMessage.setDetailSnapshot(null);

    doNothing().when(inventoryProcessingService).deleteInventoryByExternalId(anyString());

    inventoryMessageConsumer.consume(deleteMessage, null, 1L, "");

    verify(inventoryProcessingService)
        .deleteInventoryByExternalId("4df21370-0bf6-4581-90bf-24e8c2d585db");
    verify(inventoryProcessingService, never()).processInventoryMessage(any(), any());
    verify(valueOperations).set(anyString(), eq("processed"), any());
  }

  @Test
  void testConsume_WhenOperationDelete_NullInventoryId_SkipsProcessing() {
    InventoryUpdateMessageDTO deleteMessage = new InventoryUpdateMessageDTO();
    deleteMessage.setId("msg-2");
    deleteMessage.setInventoryId(null);
    deleteMessage.setOperation("delete");

    inventoryMessageConsumer.consume(deleteMessage, null, 1L, "");

    verify(inventoryProcessingService, never()).deleteInventoryByExternalId(any());
    verify(inventoryProcessingService, never()).processInventoryMessage(any(), any());
  }

  @Test
  void testConsume_WhenOperationDelete_AlreadyProcessed_SkipsProcessing() {
    InventoryUpdateMessageDTO deleteMessage = new InventoryUpdateMessageDTO();
    deleteMessage.setId("6ad1f3b5-833b-4774-989e-e746c8a14c19");
    deleteMessage.setInventoryId("4df21370-0bf6-4581-90bf-24e8c2d585db");
    deleteMessage.setOperation("delete");
    deleteMessage.setOccurredAt("2026-01-08T04:25:48.209336Z");

    when(redisTemplate.hasKey(anyString())).thenReturn(true);

    inventoryMessageConsumer.consume(deleteMessage, null, 1L, "");

    verify(inventoryProcessingService, never()).deleteInventoryByExternalId(any());
    verify(valueOperations, never()).set(anyString(), anyString(), any());
  }

  // --- Observability logging (purely additive; existing behavior must be unchanged) ---

  @Test
  void testConsume_Success_LogsProcessed() {
    when(validator.validate(any(ExternalInventoryMessageDTO.class))).thenReturn(java.util.Set.of());
    doNothing().when(inventoryProcessingService).processInventoryMessage(any(), any());

    inventoryMessageConsumer.consume(testMessage, null, 1L, "");

    // Existing behavior preserved
    verify(inventoryProcessingService).processInventoryMessage(eq(testDetailSnapshot), any());
    verify(valueOperations).set(anyString(), eq("processed"), any());
    // And a PROCESSED log is written
    ArgumentCaptor<MessageConsumeStatus> statusCaptor =
        ArgumentCaptor.forClass(MessageConsumeStatus.class);
    verify(inventoryMessageLogService)
        .log(any(), eq(testMessage), isNull(), statusCaptor.capture(), any(), any(), any());
    assertEquals(MessageConsumeStatus.PROCESSED, statusCaptor.getValue());
  }

  @Test
  void testConsume_WhenOperationNotSupported_LogsSkipped() {
    testMessage.setOperation("create");

    inventoryMessageConsumer.consume(testMessage, null, 1L, "");

    verify(inventoryProcessingService, never()).processInventoryMessage(any(), any());
    verify(inventoryProcessingService, never()).deleteInventoryByExternalId(any());
    ArgumentCaptor<MessageConsumeStatus> statusCaptor =
        ArgumentCaptor.forClass(MessageConsumeStatus.class);
    verify(inventoryMessageLogService)
        .log(any(), eq(testMessage), isNull(), statusCaptor.capture(), any(), any(), any());
    assertEquals(MessageConsumeStatus.SKIPPED, statusCaptor.getValue());
  }

  @Test
  void testConsume_WhenMessageAlreadyProcessed_LogsDuplicate() {
    when(redisTemplate.hasKey(anyString())).thenReturn(true);
    when(validator.validate(any(ExternalInventoryMessageDTO.class))).thenReturn(java.util.Set.of());

    inventoryMessageConsumer.consume(testMessage, null, 1L, "");

    verify(inventoryProcessingService, never()).processInventoryMessage(any(), any());
    verify(valueOperations, never()).set(anyString(), anyString(), any());
    ArgumentCaptor<MessageConsumeStatus> statusCaptor =
        ArgumentCaptor.forClass(MessageConsumeStatus.class);
    verify(inventoryMessageLogService)
        .log(any(), eq(testMessage), isNull(), statusCaptor.capture(), any(), any(), any());
    assertEquals(MessageConsumeStatus.DUPLICATE, statusCaptor.getValue());
  }

  @Test
  void testConsume_WhenProcessingFails_RethrowsAndLogsFailed() {
    when(validator.validate(any(ExternalInventoryMessageDTO.class))).thenReturn(java.util.Set.of());
    RuntimeException boom = new RuntimeException("downstream exploded");
    doThrow(boom).when(inventoryProcessingService).processInventoryMessage(any(), any());

    // Original exception is still re-thrown to preserve the RabbitMQ retry mechanism
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> inventoryMessageConsumer.consume(testMessage, null, 1L, ""));
    assertSame(boom, thrown);

    // A FAILED log with the error message is written in the finally, before the throw propagates
    ArgumentCaptor<MessageConsumeStatus> statusCaptor =
        ArgumentCaptor.forClass(MessageConsumeStatus.class);
    ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
    verify(inventoryMessageLogService)
        .log(
            any(),
            eq(testMessage),
            isNull(),
            statusCaptor.capture(),
            errorCaptor.capture(),
            any(),
            any());
    assertEquals(MessageConsumeStatus.FAILED, statusCaptor.getValue());
    assertEquals("downstream exploded", errorCaptor.getValue());
  }

  @Test
  void testConsume_WhenLogWriteThrows_ConsumptionUnaffected() {
    when(validator.validate(any(ExternalInventoryMessageDTO.class))).thenReturn(java.util.Set.of());
    doNothing().when(inventoryProcessingService).processInventoryMessage(any(), any());
    // A misbehaving logger must not break consumption
    doThrow(new RuntimeException("logger down"))
        .when(inventoryMessageLogService)
        .log(any(), any(), any(), any(), any(), any(), any());

    // No extra exception escapes on the happy path
    assertDoesNotThrow(() -> inventoryMessageConsumer.consume(testMessage, null, 1L, ""));

    // Processing still happened and the idempotency key was still set
    verify(inventoryProcessingService).processInventoryMessage(eq(testDetailSnapshot), any());
    verify(valueOperations).set(anyString(), eq("processed"), any());
  }
}
