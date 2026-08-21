package com.mw.planner.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

/**
 * Dedicated bean for evicting the {@code campaignInventorySchedules} cache.
 *
 * <p>Spring's cache advice is proxy-based, so {@code @CacheEvict} only fires when the annotated
 * method is invoked through the Spring proxy (i.e. from a different bean). Calling an evicting
 * method from within the same class (self-invocation) bypasses the proxy and silently does nothing.
 * Keeping the eviction in its own component guarantees every call crosses a proxy boundary and
 * actually clears the cache.
 */
@Slf4j
@Component
public class ScheduleCacheEvictor {

  /** Evict the cached campaign-inventory schedule entry for the given campaign + inventory. */
  @CacheEvict(value = "campaignInventorySchedules", key = "#campaignId + '_' + #inventoryId")
  public void evict(String campaignId, String inventoryId) {
    log.debug("Evicting campaignInventorySchedules cache for {}_{}", campaignId, inventoryId);
  }
}
