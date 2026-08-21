package com.mw.recommendation.engine.rabbitmq;

import org.springframework.amqp.core.Message;

/**
 * Generic interface for message consumers that handle RabbitMQ messages. Provides a consistent
 * pattern for consuming and processing messages.
 *
 * @param <T> The type of message DTO to be processed
 */
public interface MessageConsumer<T> {

  /**
   * Consume a message from RabbitMQ. This method handles the RabbitMQ-specific concerns like
   * idempotency, validation, and error handling.
   *
   * @param message The message DTO to be processed
   * @param amqpMessage The raw AMQP message
   * @param deliveryTag The delivery tag for acknowledgment
   * @param routingKey The routing key of the message
   */
  void consume(T message, Message amqpMessage, long deliveryTag, String routingKey);

  /**
   * Process the business logic for the message. This method contains the actual business logic and
   * delegates to appropriate services.
   *
   * @param message The message DTO to be processed
   */
  void process(T message);
}
