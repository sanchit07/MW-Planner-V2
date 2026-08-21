package com.mw.recommendation.engine.rabbitmq;

import com.mw.recommendation.engine.dto.ExternalBookingMessageDTO;
import com.mw.recommendation.engine.service.BookingProcessingService;
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
 * RabbitMQ message consumer for booking messages with idempotency support. Implements
 * MessageConsumer interface and delegates business logic to BookingProcessingService. Ensures only
 * one replica processes each message even with multiple instances running.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingMessageConsumer implements MessageConsumer<ExternalBookingMessageDTO> {

  private final BookingProcessingService bookingProcessingService;
  private final Validator validator;
  private final RedisTemplate<String, String> redisTemplate;

  private static final String IDEMPOTENCY_KEY_PREFIX = "booking:message:";
  private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

  /** Consume booking messages from RabbitMQ exchange */
  @RabbitListener(queues = "${rabbitmq.booking.queue.name}")
  public void consume(
      ExternalBookingMessageDTO message,
      Message amqpMessage,
      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
      @Header(value = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {

    String messageId = generateMessageId(message);
    String dealId = message.getDealId() != null ? message.getDealId() : "unknown";
    log.info("Received booking message with ID: {} for dealId: {}", messageId, dealId);

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

      log.info("Successfully processed booking message {} for dealId: {}", messageId, dealId);

    } catch (Exception e) {
      String deal = message.getDealId() != null ? message.getDealId() : "unknown";
      log.error(
          "Error processing booking message {} for dealId: {}: {}",
          messageId,
          deal,
          e.getMessage(),
          e);
      throw e; // Re-throw to trigger retry mechanism
    }
  }

  /** Process the business logic for the booking message */
  @Override
  public void process(ExternalBookingMessageDTO message) {
    bookingProcessingService.processBookingMessage(message);
  }

  /** Generate a unique message ID based on message content for idempotency */
  private String generateMessageId(ExternalBookingMessageDTO message) {
    try {
      String dealId = message.getDealId() != null ? message.getDealId() : "null";
      // Create a hash based on key fields that should make the message unique
      String content =
          String.format(
              "%s:%s:%s",
              dealId,
              message.getId() != null ? message.getId() : "null",
              message.getUpdatedAt() != null ? message.getUpdatedAt().toString() : "null");

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
      String dealId = message.getDealId() != null ? message.getDealId() : "null";
      String fallbackContent =
          String.format(
              "%s:%s:%s",
              dealId,
              message.getId() != null ? message.getId() : "null",
              message.getCreatedAt() != null ? message.getCreatedAt().toString() : "null");
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
  private void validateMessage(ExternalBookingMessageDTO message) {
    Set<ConstraintViolation<ExternalBookingMessageDTO>> violations = validator.validate(message);

    if (!violations.isEmpty()) {
      StringBuilder errorMessage = new StringBuilder("Validation failed for booking message: ");
      for (ConstraintViolation<ExternalBookingMessageDTO> violation : violations) {
        errorMessage
            .append(violation.getPropertyPath())
            .append(": ")
            .append(violation.getMessage())
            .append("; ");
      }

      log.error("Message validation failed: {}", errorMessage);
      throw new IllegalArgumentException("Invalid booking message: " + errorMessage);
    }

    String dealId = message.getDealId() != null ? message.getDealId() : "unknown";
    log.debug("Message validation passed for dealId: {}", dealId);
  }
}
