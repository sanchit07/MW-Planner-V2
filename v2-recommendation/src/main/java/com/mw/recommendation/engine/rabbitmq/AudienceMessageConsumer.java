package com.mw.recommendation.engine.rabbitmq;

import com.mw.recommendation.engine.dto.ExternalAudienceMessageDTO;
import com.mw.recommendation.engine.service.AudienceProcessingService;
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
 * RabbitMQ message consumer for audience messages with idempotency support. Implements
 * MessageConsumer interface and delegates business logic to AudienceProcessingService. Ensures only
 * one replica processes each message even with multiple instances running.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AudienceMessageConsumer implements MessageConsumer<ExternalAudienceMessageDTO> {

  private final AudienceProcessingService audienceProcessingService;
  private final Validator validator;
  private final RedisTemplate<String, String> redisTemplate;

  private static final String IDEMPOTENCY_KEY_PREFIX = "audience:message:";
  private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

  /** Consume audience messages from RabbitMQ exchange */
  @RabbitListener(queues = "${rabbitmq.audience.queue.name}")
  public void consume(
      ExternalAudienceMessageDTO message,
      Message amqpMessage,
      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
      @Header(value = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {

    String messageId = generateMessageId(message);
    String inventoryId = message.getInventoryId() != null ? message.getInventoryId() : "unknown";
    log.info("Received audience message with ID: {} for inventoryId: {}", messageId, inventoryId);

    try {
      // Check for idempotency
      if (isMessageAlreadyProcessed(messageId)) {
        log.info("Message {} already processed, skipping", messageId);
        return;
      }

      // Validate message
      validateMessage(message);

      // Process the message using the service
      process(message);

      // Mark message as processed for idempotency
      markMessageAsProcessed(messageId);

      log.info(
          "Successfully processed audience message {} for inventoryId: {}", messageId, inventoryId);

    } catch (Exception e) {
      String invId = message.getInventoryId() != null ? message.getInventoryId() : "unknown";
      log.error(
          "Error processing audience message {} for inventoryId: {}: {}",
          messageId,
          invId,
          e.getMessage(),
          e);
      throw e; // Re-throw to trigger retry mechanism
    }
  }

  /** Process the business logic for the audience message */
  @Override
  public void process(ExternalAudienceMessageDTO message) {
    audienceProcessingService.processAudienceMessage(message);
  }

  /** Generate a unique message ID based on message content for idempotency */
  private String generateMessageId(ExternalAudienceMessageDTO message) {
    try {
      String inventoryId = message.getInventoryId() != null ? message.getInventoryId() : "null";
      String referenceId = message.getReferenceId() != null ? message.getReferenceId() : "null";
      // Create a hash based on key fields that should make the message unique
      String content =
          String.format(
              "%s:%s:%s",
              inventoryId,
              referenceId,
              message.getLastUpdatedAt() != null ? message.getLastUpdatedAt().toString() : "null");

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
      String inventoryId = message.getInventoryId() != null ? message.getInventoryId() : "null";
      String referenceId = message.getReferenceId() != null ? message.getReferenceId() : "null";
      String fallbackContent =
          String.format(
              "%s:%s:%s",
              inventoryId,
              referenceId,
              message.getBillboardObjectId() != null ? message.getBillboardObjectId() : "null");
      return String.valueOf(fallbackContent.hashCode());
    }
  }

  /** Check if message has already been processed using Redis */
  private boolean isMessageAlreadyProcessed(String messageId) {
    String key = IDEMPOTENCY_KEY_PREFIX + messageId;
    return redisTemplate.hasKey(key);
  }

  /** Mark message as processed in Redis for idempotency */
  private void markMessageAsProcessed(String messageId) {
    String key = IDEMPOTENCY_KEY_PREFIX + messageId;
    redisTemplate.opsForValue().set(key, "processed", IDEMPOTENCY_TTL);
  }

  /** Validate the incoming message */
  private void validateMessage(ExternalAudienceMessageDTO message) {
    Set<ConstraintViolation<ExternalAudienceMessageDTO>> violations = validator.validate(message);

    if (!violations.isEmpty()) {
      StringBuilder errorMessage = new StringBuilder("Validation failed for audience message: ");
      for (ConstraintViolation<ExternalAudienceMessageDTO> violation : violations) {
        errorMessage
            .append(violation.getPropertyPath())
            .append(": ")
            .append(violation.getMessage())
            .append("; ");
      }

      log.error("Message validation failed: {}", errorMessage);
      throw new IllegalArgumentException("Invalid audience message: " + errorMessage);
    }

    String inventoryId = message.getInventoryId() != null ? message.getInventoryId() : "unknown";
    log.debug("Message validation passed for inventoryId: {}", inventoryId);
  }
}
