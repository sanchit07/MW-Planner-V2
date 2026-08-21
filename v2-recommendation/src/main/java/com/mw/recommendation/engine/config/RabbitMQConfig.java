package com.mw.recommendation.engine.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for inventory, audience, and booking message processing. Sets up fanout
 * exchanges, queues, and bindings for inventory, audience, and booking messages. Fanout exchanges
 * broadcast all messages to all bound queues.
 */
@Configuration
public class RabbitMQConfig {

  @Value("${rabbitmq.inventory.exchange.name}")
  private String inventoryExchangeName;

  @Value("${rabbitmq.inventory.queue.name}")
  private String inventoryQueueName;

  @Value("${rabbitmq.audience.exchange.name}")
  private String audienceExchangeName;

  @Value("${rabbitmq.audience.queue.name}")
  private String audienceQueueName;

  @Value("${rabbitmq.booking.exchange.name}")
  private String bookingExchangeName;

  @Value("${rabbitmq.booking.queue.name}")
  private String bookingQueueName;

  // Inventory Exchange and Queue

  /** Create the inventory fanout exchange */
  @Bean
  public FanoutExchange inventoryExchange() {
    return new FanoutExchange(inventoryExchangeName, true, false);
  }

  /** Create a durable queue for inventory messages */
  @Bean
  public Queue inventoryQueue() {
    return QueueBuilder.durable(inventoryQueueName).build();
  }

  /** Bind inventory queue to inventory exchange (fanout exchanges don't use routing keys) */
  @Bean
  public Binding inventoryBinding() {
    return BindingBuilder.bind(inventoryQueue()).to(inventoryExchange());
  }

  // Audience Exchange and Queue

  /** Create the audience fanout exchange */
  @Bean
  public FanoutExchange audienceExchange() {
    return new FanoutExchange(audienceExchangeName, true, false);
  }

  /** Create a durable queue for audience messages */
  @Bean
  public Queue audienceQueue() {
    return QueueBuilder.durable(audienceQueueName).build();
  }

  /** Bind audience queue to audience exchange (fanout exchanges don't use routing keys) */
  @Bean
  public Binding audienceBinding() {
    return BindingBuilder.bind(audienceQueue()).to(audienceExchange());
  }

  // Booking Exchange and Queue

  /** Create the booking fanout exchange */
  @Bean
  public FanoutExchange bookingExchange() {
    return new FanoutExchange(bookingExchangeName, true, false);
  }

  /** Create a durable queue for booking messages */
  @Bean
  public Queue bookingQueue() {
    return QueueBuilder.durable(bookingQueueName).build();
  }

  /** Bind booking queue to booking exchange (fanout exchanges don't use routing keys) */
  @Bean
  public Binding bookingBinding() {
    return BindingBuilder.bind(bookingQueue()).to(bookingExchange());
  }

  // Common Configuration

  /** Configure JSON message converter for RabbitMQ */
  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  /** Configure RabbitMQ listener container factory with JSON converter */
  @Bean
  public RabbitListenerContainerFactory<?> rabbitListenerContainerFactory(
      ConnectionFactory connectionFactory) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(jsonMessageConverter());
    return factory;
  }

  /**
   * RabbitAdmin bean to handle exchange/queue declarations. This will automatically declare
   * exchanges, queues, and bindings that are defined as beans.
   */
  @Bean
  public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
    RabbitAdmin admin = new RabbitAdmin(connectionFactory);
    // Set auto-startup to true to automatically declare beans
    admin.setAutoStartup(true);
    return admin;
  }
}
