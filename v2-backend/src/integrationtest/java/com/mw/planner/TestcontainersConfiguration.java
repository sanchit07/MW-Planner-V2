package com.mw.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  public MongoDBContainer mongoDBContainer() {
    // Run mongod directly (as root) instead of via the image entrypoint, which drops to the
    // non-root "mongodb" user. Some sandboxed Docker hosts cannot `docker exec` into containers
    // whose PID 1 is non-root (setns fails), which breaks Testcontainers' replica-set init.
    return new MongoDBContainer("mongo:8.0")
        // --bind_ip_all is normally injected by the image entrypoint; keep it here.
        .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("mongod", "--bind_ip_all"))
        .withReuse(false)
        .withStartupTimeout(java.time.Duration.ofMinutes(2));
  }

  @Bean
  public MinIOContainer minioContainer(DynamicPropertyRegistry registry) {
    var minio =
        new MinIOContainer("minio/minio:latest")
            .withExposedPorts(9000, 9001)
            .withCommand("server", "/data", "--console-address", ":9001")
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withReuse(false)
            .withStartupTimeout(java.time.Duration.ofMinutes(2));
    minio.start();
    registry.add(
        "aws.s3.planner.endpoint",
        () -> "http://" + minio.getHost() + ":" + minio.getMappedPort(9000));
    return minio;
  }

  @Bean
  @ServiceConnection
  public RedisContainer redisContainer() {
    return new RedisContainer("redis:7.2-alpine")
        .withReuse(false)
        .withStartupTimeout(java.time.Duration.ofMinutes(2));
  }

  @Bean
  @ServiceConnection
  public RabbitMQContainer rabbitMQContainer() {
    return new RabbitMQContainer("rabbitmq:3.13-management")
        .withReuse(false)
        .withStartupTimeout(java.time.Duration.ofMinutes(2));
  }

  @Bean
  public RedisTemplate<String, Object> redisTemplate(
      RedisConnectionFactory redisConnectionFactory, ObjectMapper objectMapper) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(redisConnectionFactory);

    GenericJackson2JsonRedisSerializer serializer =
        new GenericJackson2JsonRedisSerializer(objectMapper);

    // Set serializers
    template.setKeySerializer(new StringRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(serializer);
    template.setHashValueSerializer(serializer);

    template.afterPropertiesSet();
    return template;
  }
}
