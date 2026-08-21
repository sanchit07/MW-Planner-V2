package com.mw.recommendation.engine.v3.support;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe per-run collector for user-facing warnings (PRD requires transparency messages on
 * nearly every fallback/exclusion) and TRUE exclusion counts (the v1 metadata records filter input
 * sizes, not real counts). Accumulates in memory during the pipeline and is flushed once at run
 * completion — never on the hot path.
 */
public class WarningCollector {

  private final Queue<String> warnings = new ConcurrentLinkedQueue<>();
  private final ConcurrentHashMap<String, AtomicInteger> exclusions = new ConcurrentHashMap<>();

  /** Adds a user-facing warning, deduplicating exact repeats. */
  public void warn(String message) {
    if (message != null && !message.isBlank() && !warnings.contains(message)) {
      warnings.add(message);
    }
  }

  /** Records one inventory excluded for the given reason. */
  public void exclude(String reason) {
    exclusions.computeIfAbsent(reason, k -> new AtomicInteger()).incrementAndGet();
  }

  /** Records {@code count} inventories excluded for the given reason. */
  public void exclude(String reason, int count) {
    if (count > 0) {
      exclusions.computeIfAbsent(reason, k -> new AtomicInteger()).addAndGet(count);
    }
  }

  public List<String> warnings() {
    return List.copyOf(warnings);
  }

  public java.util.Map<String, Integer> exclusionReasons() {
    java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
    exclusions.forEach((k, v) -> out.put(k, v.get()));
    return out;
  }

  public int totalExcluded() {
    return exclusions.values().stream().mapToInt(AtomicInteger::get).sum();
  }
}
