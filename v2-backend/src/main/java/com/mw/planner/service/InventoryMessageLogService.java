package com.mw.planner.service;

import com.mw.planner.domain.InventoryMessageLog;
import com.mw.planner.dto.InventoryUpdateMessageDTO;
import com.mw.planner.enums.MessageConsumeStatus;
import com.mw.planner.repository.InventoryMessageLogRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Service;

/**
 * Persists an observability record for each inventory message the consumer receives.
 *
 * <p>This is purely additive logging. Every write is wrapped so a persistence failure can never
 * propagate into the consumer path — on failure it logs a warning and returns normally. It must
 * never cause an ack failure, a retry, or any change in processing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryMessageLogService {

  private final InventoryMessageLogRepository repository;

  /**
   * Persist a single {@link InventoryMessageLog}. All field mapping lives here. Never throws.
   *
   * @param rawPayload raw AMQP body as received (may be null)
   * @param message the deserialized message (may carry null inventoryId/operation)
   * @param amqpMessage the raw AMQP message (may be null; read null-safely)
   * @param status the consumption outcome
   * @param errorMessage error detail for FAILED (nullable)
   * @param receivedAt when the message was received (TTL anchor)
   * @param processedAt when processing succeeded (nullable)
   */
  public void log(
      String rawPayload,
      InventoryUpdateMessageDTO message,
      Message amqpMessage,
      MessageConsumeStatus status,
      String errorMessage,
      Instant receivedAt,
      Instant processedAt) {
    try {
      InventoryMessageLog entry =
          InventoryMessageLog.builder()
              .inventoryId(message != null ? message.getInventoryId() : null)
              .operation(message != null ? message.getOperation() : null)
              .rawPayload(rawPayload)
              .messageId(extractMessageId(amqpMessage))
              .correlationId(extractCorrelationId(amqpMessage))
              .status(status)
              .errorMessage(errorMessage)
              .receivedAt(receivedAt)
              .processedAt(processedAt)
              .build();
      repository.save(entry);
    } catch (Exception e) {
      log.warn(
          "Failed to persist inventory message log (observability only, ignored): {}",
          e.getMessage());
    }
  }

  private String extractMessageId(Message amqpMessage) {
    if (amqpMessage == null) {
      return null;
    }
    MessageProperties properties = amqpMessage.getMessageProperties();
    return properties != null ? properties.getMessageId() : null;
  }

  private String extractCorrelationId(Message amqpMessage) {
    if (amqpMessage == null) {
      return null;
    }
    MessageProperties properties = amqpMessage.getMessageProperties();
    return properties != null ? properties.getCorrelationId() : null;
  }
}
