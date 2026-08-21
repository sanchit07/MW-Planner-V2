package com.mw.recommendation.engine.rabbitmq;

import com.mw.recommendation.engine.dto.ExternalInventoryMessageDTO;
import com.mw.recommendation.engine.dto.InventoryUpdateMessageDTO;
import com.mw.recommendation.engine.service.InventoryProcessingService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
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

    String messageId = generateMessageId(message);

    // Log the full raw payload exactly as published by the upstream inventory system, before any
    // processing — so even messages that later fail validation have their incoming data captured.
    if (amqpMessage != null && amqpMessage.getBody() != null) {
      log.info(
          "Incoming inventory message {} (routingKey: {}) raw body: {}",
          messageId,
          routingKey,
          new String(amqpMessage.getBody(), StandardCharsets.UTF_8));
    }

    // Handle delete operation — no detailSnapshot needed
    if ("delete".equalsIgnoreCase(message.getOperation())) {
      if (message.getInventoryId() == null) {
        log.warn("Skipping delete message {} - inventoryId is null", messageId);
        return;
      }
      log.info(
          "Received delete inventory message {} for inventoryId: {}",
          messageId,
          message.getInventoryId());
      try {
        if (isMessageAlreadyProcessed(messageId)) {
          log.info("Message {} already processed, skipping", messageId);
          return;
        }
        inventoryProcessingService.deleteInventory(message.getInventoryId());
        markMessageAsProcessed(messageId);
        log.info(
            "Successfully processed delete message {} for inventoryId: {}",
            messageId,
            message.getInventoryId());
      } catch (Exception e) {
        log.error(
            "Error processing delete message {} for inventoryId: {}: {}",
            messageId,
            message.getInventoryId(),
            e.getMessage(),
            e);
        throw e;
      }
      return;
    }

    // Skip processing if detailSnapshot is null
    if (message.getDetailSnapshot() == null) {
      log.warn("Skipping inventory message with ID: {} - detailSnapshot is null", messageId);
      return;
    }

    // Only process messages with operation = "refresh", skip others
    if (message.getOperation() == null || !"refresh".equalsIgnoreCase(message.getOperation())) {
      log.info(
          "Skipping inventory message with ID: {} - operation is '{}' (only 'refresh' is supported)",
          messageId,
          message.getOperation());
      return;
    }

    String referenceId =
        message.getDetailSnapshot().getReferenceId() != null
            ? message.getDetailSnapshot().getReferenceId()
            : "unknown";

    log.info("Received inventory message with ID: {} for referenceId: {}", messageId, referenceId);

    try {
      // Check for idempotency
      log.debug("[STEP 1] Checking if message {} is already processed (Redis hasKey)", messageId);
      if (isMessageAlreadyProcessed(messageId)) {
        log.info("Message {} already processed, skipping", messageId);
        return;
      }
      log.debug("[STEP 1] Message {} is not yet processed, proceeding", messageId);

      // Validate message
      log.debug("[STEP 2] Validating message {}", messageId);
      validateMessage(message);
      log.debug("[STEP 2] Message {} validation passed", messageId);

      // Process the message using the service
      log.debug("[STEP 3] Processing message {} with InventoryProcessingService", messageId);
      process(message);
      log.debug("[STEP 3] Message {} processing completed", messageId);

      // Mark message as processed for idempotency
      log.debug("[STEP 4] Marking message {} as processed (Redis set)", messageId);
      markMessageAsProcessed(messageId);
      log.debug("[STEP 4] Message {} marked as processed", messageId);

      log.info(
          "Successfully processed inventory message {} for referenceId: {}",
          messageId,
          referenceId);

    } catch (IllegalArgumentException e) {
      // Permanent failure (e.g. validation: missing required fields). Retrying would loop
      // forever because the same malformed payload can never pass validation. Drop it.
      String refId =
          message.getDetailSnapshot() != null
                  && message.getDetailSnapshot().getReferenceId() != null
              ? message.getDetailSnapshot().getReferenceId()
              : "unknown";
      log.error(
          "Dropping invalid inventory message {} for referenceId: {} (not retried): {}",
          messageId,
          refId,
          e.getMessage());
      // Do not re-throw — ack the message so RabbitMQ does not redeliver/retry it.
    } catch (Exception e) {
      String refId =
          message.getDetailSnapshot() != null
                  && message.getDetailSnapshot().getReferenceId() != null
              ? message.getDetailSnapshot().getReferenceId()
              : "unknown";
      log.error(
          "Error processing inventory message {} for referenceId: {}: {}",
          messageId,
          refId,
          e.getMessage(),
          e);
      throw e; // Re-throw to trigger retry mechanism (transient failures only)
    }
  }

  /** Process the business logic for the inventory message */
  @Override
  public void process(InventoryUpdateMessageDTO message) {
    // detailSnapshot null check is done in consume() method, so it should never be null here
    // but adding a safety check to avoid NPE
    if (message.getDetailSnapshot() == null) {
      log.warn("Skipping processing - detailSnapshot is null");
      return;
    }
    inventoryProcessingService.processInventoryMessage(message.getDetailSnapshot());
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
    try {
      String key = IDEMPOTENCY_KEY_PREFIX + messageId;
      Boolean result = redisTemplate.hasKey(key);
      log.debug("Redis hasKey check for {}: {}", key, result);
      return Boolean.TRUE.equals(result);
    } catch (Exception e) {
      log.error(
          "Redis exception in isMessageAlreadyProcessed for messageId {}: {}",
          messageId,
          e.getMessage(),
          e);
      throw new org.springframework.data.redis.RedisSystemException(
          "Failed to check idempotency in Redis", e);
    }
  }

  /** Mark message as processed in Redis for idempotency */
  private void markMessageAsProcessed(String messageId) {
    try {
      String key = IDEMPOTENCY_KEY_PREFIX + messageId;
      redisTemplate.opsForValue().set(key, "processed", IDEMPOTENCY_TTL);
      log.debug("Redis set completed for key {}", key);
    } catch (Exception e) {
      log.error(
          "Redis exception in markMessageAsProcessed for messageId {}: {}",
          messageId,
          e.getMessage(),
          e);
      throw new org.springframework.data.redis.RedisSystemException(
          "Failed to mark message as processed in Redis", e);
    }
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
            : "unknown";
    log.debug("Message validation passed for referenceId: {}", referenceId);
  }
}
