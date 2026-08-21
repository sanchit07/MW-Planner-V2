package com.mw.planner.rabbitmq;

import com.mw.planner.dto.ExternalInventoryMessageDTO;
import com.mw.planner.dto.InventoryUpdateMessageDTO;
import com.mw.planner.enums.MessageConsumeStatus;
import com.mw.planner.service.InventoryMessageLogService;
import com.mw.planner.service.InventoryProcessingService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

/**
 * RabbitMQ message consumer for inventory messages with idempotency support. Implements
 * MessageConsumer interface and delegates business logic to InventoryProcessingService. Ensures
 * only one replica processes each message even with multiple instances running.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryMessageConsumer implements MessageConsumer<InventoryUpdateMessageDTO> {

  private final InventoryProcessingService inventoryProcessingService;
  private final Validator validator;
  private final RedisTemplate<String, String> redisTemplate;
  private final InventoryMessageLogService inventoryMessageLogService;

  private static final String IDEMPOTENCY_KEY_PREFIX = "inventory:message:";
  private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

  /** Consume inventory messages from RabbitMQ exchange */
  @RabbitListener(
      queues = "${rabbitmq.inventory.queue.name}",
      concurrency = "#{T(java.lang.Runtime).getRuntime().availableProcessors() * 2}")
  public void consume(
      InventoryUpdateMessageDTO message,
      Message amqpMessage,
      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
      @Header(value = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {

    // Observability capture (purely additive — never changes consumer flow). The single log write
    // happens in the finally below so it runs on every path, including before the retry re-throw.
    String rawPayload = extractRawPayload(amqpMessage);
    Instant receivedAt = Instant.now();
    MessageConsumeStatus status = MessageConsumeStatus.RECEIVED;
    String errorMessage = null;
    Instant processedAt = null;
    try {
      String messageId = generateMessageId(message);
      String operation = message.getOperation();

      if (operation == null) {
        log.warn("Skipping inventory message with ID: {} - operation is null", messageId);
        status = MessageConsumeStatus.SKIPPED;
        return;
      }

      if ("delete".equalsIgnoreCase(operation)) {
        if (message.getInventoryId() == null) {
          log.warn("Skipping delete message with ID: {} - inventoryId is null", messageId);
          status = MessageConsumeStatus.SKIPPED;
          return;
        }
        log.info(
            "Received delete message with ID: {} for inventoryId: {}",
            messageId,
            message.getInventoryId());
      } else if ("refresh".equalsIgnoreCase(operation)) {
        if (message.getDetailSnapshot() == null) {
          log.warn("Skipping refresh message with ID: {} - detailSnapshot is null", messageId);
          status = MessageConsumeStatus.SKIPPED;
          return;
        }
        String referenceId =
            message.getDetailSnapshot().getReferenceId() != null
                ? message.getDetailSnapshot().getReferenceId()
                : "unknown";
        log.info(
            "Received refresh message with ID: {} for referenceId: {}", messageId, referenceId);
      } else {
        log.info(
            "Skipping inventory message with ID: {} - operation '{}' not supported",
            messageId,
            operation);
        status = MessageConsumeStatus.SKIPPED;
        return;
      }

      try {
        if (isMessageAlreadyProcessed(messageId)) {
          log.info("Message {} already processed, skipping", messageId);
          status = MessageConsumeStatus.DUPLICATE;
          return;
        }

        if ("refresh".equalsIgnoreCase(operation)) {
          validateMessage(message);
        }

        process(message);
        markMessageAsProcessed(messageId);
        log.info("Successfully processed {} message {}", operation, messageId);
        status = MessageConsumeStatus.PROCESSED;
        processedAt = Instant.now();

      } catch (Exception e) {
        log.error("Error processing {} message {}: {}", operation, messageId, e.getMessage(), e);
        status = MessageConsumeStatus.FAILED;
        errorMessage = e.getMessage();
        throw e; // Re-throw to trigger retry mechanism
      }
    } finally {
      // Guard the log write: an exception here would mask a re-thrown processing exception and
      // break the RabbitMQ retry contract. Logging must never affect consumption.
      try {
        inventoryMessageLogService.log(
            rawPayload, message, amqpMessage, status, errorMessage, receivedAt, processedAt);
      } catch (Exception logFailure) {
        log.warn("Inventory message logging failed (ignored): {}", logFailure.getMessage());
      }
    }
  }

  /** Best-effort, null-safe extraction of the raw AMQP body. Never throws. */
  private String extractRawPayload(Message amqpMessage) {
    try {
      if (amqpMessage == null || amqpMessage.getBody() == null) {
        return null;
      }
      return new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public void process(InventoryUpdateMessageDTO message) {
    if ("delete".equalsIgnoreCase(message.getOperation())) {
      inventoryProcessingService.deleteInventoryByExternalId(message.getInventoryId());
    } else {
      inventoryProcessingService.processInventoryMessage(
          message.getDetailSnapshot(), message.getInventoryId());
    }
  }

  /** Generate a unique message ID based on message content for idempotency */
  private String generateMessageId(InventoryUpdateMessageDTO message) {
    try {
      // Create a hash based on key fields that should make the message unique:
      // id, inventoryId, operation, and occurredAt
      String content =
          String.format(
              "%s:%s:%s:%s",
              message.getId() != null ? message.getId() : "null",
              message.getInventoryId() != null ? message.getInventoryId() : "null",
              message.getOperation() != null ? message.getOperation() : "null",
              message.getOccurredAt() != null ? message.getOccurredAt() : "null");

      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));

      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }

      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      log.error("Error generating message ID", e);
      // Fallback to a simple hash
      String fallbackContent =
          String.format(
              "%s:%s:%s:%s",
              message.getId() != null ? message.getId() : "null",
              message.getInventoryId() != null ? message.getInventoryId() : "null",
              message.getOperation() != null ? message.getOperation() : "null",
              message.getOccurredAt() != null ? message.getOccurredAt() : "null");
      return String.valueOf(fallbackContent.hashCode());
    }
  }

  /** Check if message has already been processed using Redis */
  private boolean isMessageAlreadyProcessed(String messageId) {
    if (messageId == null) {
      return false;
    }
    String key = IDEMPOTENCY_KEY_PREFIX + messageId;
    return redisTemplate.hasKey(key);
  }

  /** Mark message as processed in Redis for idempotency */
  private void markMessageAsProcessed(String messageId) {
    String key = IDEMPOTENCY_KEY_PREFIX + messageId;
    redisTemplate.opsForValue().set(key, "processed", IDEMPOTENCY_TTL);
  }

  /** Validate the incoming message */
  private void validateMessage(InventoryUpdateMessageDTO message) {
    // detailSnapshot null check is done in consume() method, so it should never be null here
    // but adding a safety check to avoid NPE
    if (message.getDetailSnapshot() == null) {
      log.warn("Skipping validation - detailSnapshot is null");
      return;
    }

    Set<ConstraintViolation<ExternalInventoryMessageDTO>> violations =
        validator.validate(message.getDetailSnapshot());

    if (!violations.isEmpty()) {
      StringBuilder errorMessage = new StringBuilder("Validation failed for inventory message: ");
      for (ConstraintViolation<ExternalInventoryMessageDTO> violation : violations) {
        errorMessage
            .append(violation.getPropertyPath())
            .append(": ")
            .append(violation.getMessage())
            .append("; ");
      }

      log.error("Message validation failed: {}", errorMessage);
      throw new IllegalArgumentException("Invalid inventory message: " + errorMessage);
    }

    String referenceId =
        message.getDetailSnapshot().getReferenceId() != null
            ? message.getDetailSnapshot().getReferenceId()
            : "null";
    log.debug("Message validation passed for referenceId: {}", referenceId);
  }
}
