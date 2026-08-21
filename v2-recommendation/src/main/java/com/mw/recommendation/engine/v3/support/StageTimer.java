package com.mw.recommendation.engine.v3.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Per-run stage timer. Records each stage's duration for run metadata (observability) and logs a
 * summary with the bottleneck stage flagged. v3-local so the shared PerformanceProfiler stays
 * untouched.
 */
@Slf4j
public class StageTimer {

  private final String operationId;
  private final Map<String, Long> timingsMs = new LinkedHashMap<>();

  public StageTimer(String operationId) {
    this.operationId = operationId;
  }

  public <T> T time(String stage, Supplier<T> work) {
    long start = System.nanoTime();
    try {
      return work.get();
    } finally {
      record(stage, start);
    }
  }

  public void time(String stage, Runnable work) {
    long start = System.nanoTime();
    try {
      work.run();
    } finally {
      record(stage, start);
    }
  }

  private synchronized void record(String stage, long startNanos) {
    timingsMs.put(stage, (System.nanoTime() - startNanos) / 1_000_000);
  }

  public synchronized Map<String, Long> timingsMs() {
    return new LinkedHashMap<>(timingsMs);
  }

  public synchronized void logSummary() {
    long total = timingsMs.values().stream().mapToLong(Long::longValue).sum();
    String bottleneck =
        timingsMs.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("-");
    StringBuilder sb =
        new StringBuilder("v3 stage timings | ")
            .append(operationId)
            .append(" | totalMs=")
            .append(total)
            .append(" | bottleneck=")
            .append(bottleneck);
    timingsMs.forEach(
        (stage, ms) -> sb.append(" | ").append(stage).append("=").append(ms).append("ms"));
    log.info(sb.toString());
  }
}
