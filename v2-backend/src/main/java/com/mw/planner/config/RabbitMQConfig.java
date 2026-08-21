package com.mw.planner.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for inventory message processing. Sets up fanout exchange, queue, and
 * binding for inventory messages. Fanout exchanges broadcast all messages to all bound queues.
 */
@Configuration
public class RabbitMQConfig {

  @Value("${rabbitmq.inventory.exchange.name}")
  private String inventoryExchangeName;

  @Value("${rabbitmq.inventory.queue.name}")
  private String inventoryQueueName;

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
}
