package com.mw.recommendation.engine.rabbitmq;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.dto.ExternalInventoryMessageDTO;
import com.mw.recommendation.engine.dto.InventoryUpdateMessageDTO;
import com.mw.recommendation.engine.service.InventoryProcessingService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class InventoryMessageConsumerTest {

  @Mock private InventoryProcessingService inventoryProcessingService;

  @Mock private Validator validator;

  @Mock private RedisTemplate<String, String> redisTemplate;

  @Mock private ValueOperations<String, String> valueOperations;

  @InjectMocks private InventoryMessageConsumer inventoryMessageConsumer;

  private InventoryUpdateMessageDTO validMessage;
  private ExternalInventoryMessageDTO detailSnapshot;
  private Message amqpMessage;

  @BeforeEach
  void setUp() {
    // Setup valid detailSnapshot
    detailSnapshot = new ExternalInventoryMessageDTO();
    detailSnapshot.setId("inventory-123");
    detailSnapshot.setName("Test Inventory");
    detailSnapshot.setReferenceId("REF-123");

    // Setup valid message
    validMessage = new InventoryUpdateMessageDTO();
    validMessage.setId("msg-1");
    validMessage.setInventoryId("inventory-123");
    validMessage.setOperation("refresh");
    validMessage.setOccurredAt("2025-01-01T00:00:00Z");
    validMessage.setDetailSnapshot(detailSnapshot);

    // Setup AMQP message mock
    amqpMessage = mock(Message.class);

    // Setup Redis mocks - use lenient() since these may not be used in all tests
    lenient().when(redisTemplate.hasKey(anyString())).thenReturn(false);
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    lenient().doNothing().when(valueOperations).set(anyString(), anyString(), any());

    // Setup validator mock - use lenient() since this may not be used in all tests
    lenient()
        .when(validator.validate(any(ExternalInventoryMessageDTO.class)))
        .thenReturn(Collections.emptySet());
  }

  @Test
  void testConsume_WithNullDetailSnapshot_SkipsProcessing() {
    // Arrange
    InventoryUpdateMessageDTO message = new InventoryUpdateMessageDTO();
    message.setId("msg-1");
    message.setOperation("refresh");
    message.setDetailSnapshot(null);

    // Act
    inventoryMessageConsumer.consume(message, amqpMessage, 1L, "");

    // Assert
    verify(inventoryProcessingService, never()).processInventoryMessage(any());
    verify(redisTemplate, never()).opsForValue();
  }

  @Test
  void testConsume_WithNullOperation_SkipsProcessing() {
    // Arrange
    InventoryUpdateMessageDTO message = new InventoryUpdateMessageDTO();
    message.setId("msg-1");
    message.setOperation(null);
    message.setDetailSnapshot(detailSnapshot);

    // Act
    inventoryMessageConsumer.consume(message, amqpMessage, 1L, "");

    // Assert
    verify(inventoryProcessingService, never()).processInventoryMessage(any());
  }

  @Test
  void testConsume_WithOperationCreate_SkipsProcessing() {
    // Arrange
    InventoryUpdateMessageDTO message = new InventoryUpdateMessageDTO();
    message.setId("msg-1");
    message.setOperation("create");
    message.setDetailSnapshot(detailSnapshot);

    // Act
    inventoryMessageConsumer.consume(message, amqpMessage, 1L, "");

    // Assert
    verify(inventoryProcessingService, never()).processInventoryMessage(any());
  }

  @Test
  void testConsume_WithOperationUpdate_SkipsProcessing() {
    // Arrange
    InventoryUpdateMessageDTO message = new InventoryUpdateMessageDTO();
    message.setId("msg-1");
    message.setOperation("update");
    message.setDetailSnapshot(detailSnapshot);

    // Act
    inventoryMessageConsumer.consume(message, amqpMessage, 1L, "");

    // Assert
    verify(inventoryProcessingService, never()).processInventoryMessage(any());
  }

  @Test
  void testConsume_WithOperationDelete_CallsDeleteInventory() {
    // Arrange
    InventoryUpdateMessageDTO message = new InventoryUpdateMessageDTO();
    message.setId("msg-1");
    message.setInventoryId("inventory-123");
    message.setOperation("delete");
    message.setOccurredAt("2025-01-01T00:00:00Z");

    // Act
    inventoryMessageConsumer.consume(message, amqpMessage, 1L, "");

    // Assert
    verify(inventoryProcessingService, times(1)).deleteInventory("inventory-123");
    verify(inventoryProcessingService, never()).processInventoryMessage(any());
    verify(redisTemplate, times(1)).hasKey(anyString());
    verify(redisTemplate, times(1)).opsForValue();
  }

  @Test
  void testConsume_WithOperationDeleteUpperCase_CallsDeleteInventory() {
    // Arrange
    InventoryUpdateMessageDTO message = new InventoryUpdateMessageDTO();
    message.setId("msg-1");
    message.setInventoryId("inventory-123");
    message.setOperation("DELETE");
    message.setOccurredAt("2025-01-01T00:00:00Z");

    // Act
    inventoryMessageConsumer.consume(message, amqpMessage, 1L, "");

    // Assert
    verify(inventoryProcessingService, times(1)).deleteInventory("inventory-123");
  }

  @Test
  void testConsume_WithOperationDelete_NullInventoryId_SkipsProcessing() {
    // Arrange
    InventoryUpdateMessageDTO message = new InventoryUpdateMessageDTO();
    message.setId("msg-1");
    message.setInventoryId(null);
    message.setOperation("delete");

    // Act
    inventoryMessageConsumer.consume(message, amqpMessage, 1L, "");

    // Assert
    verify(inventoryProcessingService, never()).deleteInventory(any());
    verify(redisTemplate, never()).hasKey(anyString());
  }

  @Test
  void testConsume_WithOperationDelete_AlreadyProcessed_SkipsDelete() {
    // Arrange
    InventoryUpdateMessageDTO message = new InventoryUpdateMessageDTO();
    message.setId("msg-1");
    message.setInventoryId("inventory-123");
    message.setOperation("delete");
    message.setOccurredAt("2025-01-01T00:00:00Z");
    when(redisTemplate.hasKey(anyString())).thenReturn(true);

    // Act
    inventoryMessageConsumer.consume(message, amqpMessage, 1L, "");

    // Assert
    verify(inventoryProcessingService, never()).deleteInventory(any());
    verify(redisTemplate, times(1)).hasKey(anyString());
    verify(redisTemplate, never()).opsForValue();
  }

  @Test
  void testConsume_WithOperationRefreshLowerCase_ProcessesMessage() {
    // Arrange - operation is "refresh" (lowercase)
    validMessage.setOperation("refresh");

    // Act
    inventoryMessageConsumer.consume(validMessage, amqpMessage, 1L, "");

    // Assert
    verify(inventoryProcessingService, times(1)).processInventoryMessage(eq(detailSnapshot));
    verify(redisTemplate, times(1)).hasKey(anyString());
    verify(redisTemplate, times(1)).opsForValue();
  }

  @Test
  void testConsume_WithOperationRefreshUpperCase_ProcessesMessage() {
    // Arrange - operation is "REFRESH" (uppercase)
    validMessage.setOperation("REFRESH");

    // Act
    inventoryMessageConsumer.consume(validMessage, amqpMessage, 1L, "");

    // Assert
    verify(inventoryProcessingService, times(1)).processInventoryMessage(eq(detailSnapshot));
  }

  @Test
  void testConsume_WithOperationRefreshMixedCase_ProcessesMessage() {
    // Arrange - operation is "Refresh" (mixed case)
    validMessage.setOperation("Refresh");

    // Act
    inventoryMessageConsumer.consume(validMessage, amqpMessage, 1L, "");

    // Assert
    verify(inventoryProcessingService, times(1)).processInventoryMessage(eq(detailSnapshot));
  }

  @Test
  void testConsume_WithValidMessage_ProcessesSuccessfully() {
    // Act
    inventoryMessageConsumer.consume(validMessage, amqpMessage, 1L, "");

    // Assert
    verify(inventoryProcessingService, times(1)).processInventoryMessage(eq(detailSnapshot));
    verify(validator, times(1)).validate(eq(detailSnapshot));
    verify(redisTemplate, times(1)).hasKey(anyString());
    verify(redisTemplate, times(1)).opsForValue();
  }

  @Test
  void testConsume_WithAlreadyProcessedMessage_SkipsProcessing() {
    // Arrange - message already processed
    when(redisTemplate.hasKey(anyString())).thenReturn(true);

    // Act
    inventoryMessageConsumer.consume(validMessage, amqpMessage, 1L, "");

    // Assert
    verify(inventoryProcessingService, never()).processInventoryMessage(any());
    verify(redisTemplate, times(1)).hasKey(anyString());
    verify(redisTemplate, never()).opsForValue();
  }

  @Test
  void testConsume_WithValidationFailure_DoesNotThrow_AndDropsMessage() {
    // Arrange - validation fails (permanent bad data, e.g. missing name)
    @SuppressWarnings("unchecked")
    ConstraintViolation<ExternalInventoryMessageDTO> violation = mock(ConstraintViolation.class);
    when(violation.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
    when(violation.getMessage()).thenReturn("Name is required");
    when(validator.validate(any(ExternalInventoryMessageDTO.class))).thenReturn(Set.of(violation));

    // Act - must NOT throw; a permanently-invalid message should be dropped,
    // not re-thrown into an infinite RabbitMQ redelivery/retry loop.
    inventoryMessageConsumer.consume(validMessage, amqpMessage, 1L, "routing.key");

    // Assert - message was dropped without processing or being marked processed
    verify(inventoryProcessingService, never()).processInventoryMessage(any());
    verify(redisTemplate, never()).opsForValue();
  }

  @Test
  void testConsume_LogsRawMessageBody() {
    // Arrange - capture logs from the consumer + a known raw body on the AMQP message
    ch.qos.logback.classic.Logger logger =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(InventoryMessageConsumer.class);
    ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
        new ch.qos.logback.core.read.ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    String rawBody =
        "{\"id\":\"msg-1\",\"operation\":\"refresh\",\"detailSnapshot\":{\"name\":\"x\"}}";
    when(amqpMessage.getBody())
        .thenReturn(rawBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    // Act
    inventoryMessageConsumer.consume(validMessage, amqpMessage, 1L, "");

    // Assert - the raw JSON body appears in the logs
    boolean logged =
        appender.list.stream()
            .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
            .anyMatch(msg -> msg.contains(rawBody));
    logger.detachAppender(appender);
    org.junit.jupiter.api.Assertions.assertTrue(
        logged, "Expected raw AMQP message body to be logged, but it was not");
  }

  @Test
  void testProcess_WithNullDetailSnapshot_SkipsProcessing() {
    // Arrange
    InventoryUpdateMessageDTO message = new InventoryUpdateMessageDTO();
    message.setDetailSnapshot(null);

    // Act
    inventoryMessageConsumer.process(message);

    // Assert
    verify(inventoryProcessingService, never()).processInventoryMessage(any());
  }

  @Test
  void testProcess_WithValidDetailSnapshot_CallsService() {
    // Arrange
    InventoryUpdateMessageDTO message = new InventoryUpdateMessageDTO();
    message.setDetailSnapshot(detailSnapshot);

    // Act
    inventoryMessageConsumer.process(message);

    // Assert
    verify(inventoryProcessingService, times(1)).processInventoryMessage(eq(detailSnapshot));
  }
}
