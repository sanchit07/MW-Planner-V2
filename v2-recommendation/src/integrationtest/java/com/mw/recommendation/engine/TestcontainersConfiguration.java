package com.mw.recommendation.engine;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  public MongoDBContainer mongoDBContainer() {
    return new MongoDBContainer("mongo:8.0")
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
        "aws.s3.brand.endpoint",
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
}
