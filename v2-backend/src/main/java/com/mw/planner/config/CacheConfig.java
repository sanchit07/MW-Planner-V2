package com.mw.planner.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class CacheConfig {

  @Bean
  public CacheManager cacheManager(
      RedisConnectionFactory redisConnectionFactory,
      MwPlannerProperties mwPlannerProperties,
      ObjectMapper objectMapper) {

    // Create a copy of the ObjectMapper and add Redis-specific configuration
    ObjectMapper redisMapper = objectMapper.copy();
    redisMapper.activateDefaultTyping(
        LaissezFaireSubTypeValidator.instance,
        ObjectMapper.DefaultTyping.NON_FINAL,
        JsonTypeInfo.As.PROPERTY);

    // Configure to handle missing type information gracefully
    // This prevents deserialization errors when @class property is missing from cached entries
    redisMapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
    redisMapper.configure(DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY, false);

    GenericJackson2JsonRedisSerializer serializer =
        new GenericJackson2JsonRedisSerializer(redisMapper);

    RedisCacheConfiguration config =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(mwPlannerProperties.getCache().getTtlSeconds()))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer));

    // Use cluster-safe batch strategy: delete keys one-by-one to avoid CROSSSLOT when clearing
    // caches (e.g. @CacheEvict(allEntries = true)) on Redis Cluster.
    RedisCacheWriter cacheWriter =
        RedisCacheWriter.nonLockingRedisCacheWriter(
            redisConnectionFactory, new RedisClusterSafeBatchStrategy());
    return RedisCacheManager.builder(cacheWriter).cacheDefaults(config).build();
  }
}
