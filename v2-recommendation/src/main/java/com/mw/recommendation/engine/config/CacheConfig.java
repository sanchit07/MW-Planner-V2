package com.mw.recommendation.engine.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

  // Service-specific cache prefix to avoid conflicts with mw-planner
  private static final String CACHE_PREFIX = "mw-recommendation-engine:";

  @Bean
  public CacheManager cacheManager(
      RedisConnectionFactory redisConnectionFactory, ObjectMapper objectMapper) {

    // Create a copy of the ObjectMapper and add Redis-specific configuration
    ObjectMapper redisMapper = objectMapper.copy();
    redisMapper.activateDefaultTyping(
        LaissezFaireSubTypeValidator.instance,
        ObjectMapper.DefaultTyping.NON_FINAL,
        JsonTypeInfo.As.PROPERTY);

    GenericJackson2JsonRedisSerializer serializer =
        new GenericJackson2JsonRedisSerializer(redisMapper);

    // Default cache configuration with service prefix
    RedisCacheConfiguration defaultConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(3600))
            .prefixCacheNameWith(CACHE_PREFIX) // Add service prefix to all cache names
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer));

    // Total possible ad plays cache - for SOV calculations
    // TTL: 1 hour (date range calculations are expensive)
    RedisCacheConfiguration totalAdPlaysConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .prefixCacheNameWith(CACHE_PREFIX)
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer));

    // Measure API cache - for reach/frequency responses
    // TTL: 10 minutes (availability data changes as bookings come in)
    // This cache significantly reduces auto-selection time from ~20s to ~5-8s
    RedisCacheConfiguration measureApiConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .prefixCacheNameWith(CACHE_PREFIX)
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer))
            .disableCachingNullValues(); // Don't cache API failures

    return RedisCacheManager.builder(redisConnectionFactory)
        .cacheDefaults(defaultConfig)
        .withCacheConfiguration("totalAdPlays", totalAdPlaysConfig)
        .withCacheConfiguration("measureReachFrequency", measureApiConfig)
        // v2 Measure client cache (additive only — same TTL as the shared Measure cache; existing
        // cache names/TTLs above are untouched)
        .withCacheConfiguration("v2MeasureReachFrequency", measureApiConfig)
        // v3 pipeline caches (additive only — existing cache names/TTLs above are untouched)
        .withCacheConfiguration("v3MeasureReachFrequency", measureApiConfig)
        .withCacheConfiguration("v3TotalAdPlays", totalAdPlaysConfig)
        .build();
  }
}
