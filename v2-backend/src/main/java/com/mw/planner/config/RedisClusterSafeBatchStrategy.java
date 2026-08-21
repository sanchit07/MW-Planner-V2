package com.mw.planner.config;

import org.springframework.data.redis.cache.BatchStrategy;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.lang.NonNull;

/**
 * Redis Cluster-safe batch strategy that deletes cache keys one by one.
 *
 * <p>In Redis Cluster, multi-key commands (e.g. DEL key1 key2 key3) require all keys to hash to the
 * same slot, otherwise Redis returns CROSSSLOT. The default {@code BatchStrategies.keys()} and
 * {@code BatchStrategies.scan()} use a single DEL with multiple keys, which fails when keys are
 * distributed across slots.
 *
 * <p>This strategy uses SCAN to find matching keys and deletes each key with a separate DEL, so
 * each operation touches only one slot and is safe for Redis Cluster.
 */
public class RedisClusterSafeBatchStrategy implements BatchStrategy {

  private static final int SCAN_BATCH_SIZE = 500;

  @Override
  public long cleanCache(
      @NonNull RedisConnection connection, @NonNull String name, @NonNull byte[] pattern) {
    RedisKeyCommands commands = connection.keyCommands();
    Cursor<byte[]> cursor =
        commands.scan(ScanOptions.scanOptions().count(SCAN_BATCH_SIZE).match(pattern).build());

    long count = 0;
    try {
      while (cursor.hasNext()) {
        byte[] key = cursor.next();
        Long removed = commands.del(key);
        if (removed != null && removed > 0) {
          count++;
        }
      }
    } finally {
      cursor.close();
    }
    return count;
  }
}
