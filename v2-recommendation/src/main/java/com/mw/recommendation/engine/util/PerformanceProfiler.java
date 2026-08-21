package com.mw.recommendation.engine.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility for tracking and logging performance metrics of multi-step operations. Captures timing,
 * metadata, and provides structured output for bottleneck analysis.
 */
@Slf4j
public class PerformanceProfiler {

  private final String operationId;
  private final long startTimeNanos;
  private final Map<String, StepMetric> metrics;
  private final Deque<String> stepStack;
  private final ObjectMapper objectMapper;

  public PerformanceProfiler(String operationId) {
    this.operationId = operationId;
    this.startTimeNanos = System.nanoTime();
    this.metrics = new LinkedHashMap<>();
    this.stepStack = new ArrayDeque<>();
    this.objectMapper = new ObjectMapper();
  }

  /** Start tracking a step */
  public void startStep(String stepName) {
    stepStack.push(stepName);
    StepMetric metric = metrics.computeIfAbsent(stepName, k -> new StepMetric(stepName));
    metric.startTimeNanos = System.nanoTime();
  }

  /** End tracking a step with optional metadata */
  public void endStep(String stepName, Map<String, Object> metadata) {
    if (!stepStack.isEmpty() && stepStack.peek().equals(stepName)) {
      stepStack.pop();
    }

    StepMetric metric = metrics.get(stepName);
    if (metric != null && metric.startTimeNanos != null) {
      metric.durationNanos = System.nanoTime() - metric.startTimeNanos;
      metric.durationMs = metric.durationNanos / 1_000_000.0;
      if (metadata != null) {
        metric.metadata.putAll(metadata);
      }
    }
  }

  /** End tracking a step without metadata */
  public void endStep(String stepName) {
    endStep(stepName, null);
  }

  /** End tracking a step that encountered an error */
  public void endStepError(String stepName, Exception exception) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("error", exception.getClass().getSimpleName());
    metadata.put("errorMessage", exception.getMessage());
    endStep(stepName, metadata);
  }

  /** Log performance summary with hybrid format (multi-line + JSON) */
  public void logSummary() {
    long totalDurationNanos = System.nanoTime() - startTimeNanos;
    double totalDurationMs = totalDurationNanos / 1_000_000.0;
    double totalDurationSec = totalDurationMs / 1000.0;

    // Calculate percentages
    for (StepMetric metric : metrics.values()) {
      if (metric.durationNanos != null) {
        metric.percentage = (metric.durationNanos * 100.0) / totalDurationNanos;
      }
    }

    // Build multi-line summary
    StringBuilder summary = new StringBuilder();
    summary.append(String.format("\n========================================\n"));
    summary.append(String.format("Performance Profile [%s]\n", operationId));
    summary.append(String.format("========================================\n"));
    summary.append(
        String.format(
            "Total Processing Time: %.2fs (%.0fms)\n", totalDurationSec, totalDurationMs));
    summary.append("----------------------------------------\n");

    // Find bottleneck
    StepMetric bottleneck =
        metrics.values().stream()
            .filter(m -> m.durationNanos != null)
            .max(Comparator.comparing(m -> m.durationNanos))
            .orElse(null);

    if (bottleneck != null) {
      summary.append(
          String.format("⚠️  Bottleneck: %s (%.1f%%)\n", bottleneck.name, bottleneck.percentage));
      summary.append("----------------------------------------\n");
    }

    // List all steps with timing
    for (StepMetric metric : metrics.values()) {
      if (metric.durationNanos != null) {
        String icon = metric == bottleneck ? "🔴" : "  ";
        summary.append(
            String.format(
                "%s %-40s: %8.2fs (%6.1f%%)",
                icon, metric.name, metric.durationMs / 1000.0, metric.percentage));

        // Add metadata if present
        if (!metric.metadata.isEmpty()) {
          List<String> metadataStrings = new ArrayList<>();
          for (Map.Entry<String, Object> entry : metric.metadata.entrySet()) {
            metadataStrings.add(entry.getKey() + "=" + entry.getValue());
          }
          summary.append(" [").append(String.join(", ", metadataStrings)).append("]");
        }
        summary.append("\n");
      }
    }

    summary.append("========================================\n");

    // Log multi-line summary
    log.info(summary.toString());

    // Build and log JSON
    try {
      Map<String, Object> jsonOutput = buildJsonOutput(totalDurationMs);
      String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonOutput);
      log.info("Performance Profile JSON [{}]:\n{}", operationId, json);
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize performance metrics to JSON: {}", e.getMessage());
    }
  }

  /** Build JSON structure for programmatic consumption */
  private Map<String, Object> buildJsonOutput(double totalDurationMs) {
    Map<String, Object> output = new LinkedHashMap<>();
    output.put("operationId", operationId);
    output.put("totalDurationMs", Math.round(totalDurationMs * 100.0) / 100.0);
    output.put("totalDurationSec", Math.round((totalDurationMs / 1000.0) * 100.0) / 100.0);

    Map<String, Map<String, Object>> phases = new LinkedHashMap<>();
    for (StepMetric metric : metrics.values()) {
      if (metric.durationNanos != null) {
        Map<String, Object> phaseData = new LinkedHashMap<>();
        phaseData.put("durationMs", Math.round(metric.durationMs * 100.0) / 100.0);
        phaseData.put("durationSec", Math.round((metric.durationMs / 1000.0) * 100.0) / 100.0);
        phaseData.put("percentage", Math.round(metric.percentage * 10.0) / 10.0);
        if (!metric.metadata.isEmpty()) {
          phaseData.put("metadata", metric.metadata);
        }
        phases.put(metric.name, phaseData);
      }
    }
    output.put("phases", phases);

    // Add bottleneck info
    StepMetric bottleneck =
        metrics.values().stream()
            .filter(m -> m.durationNanos != null)
            .max(Comparator.comparing(m -> m.durationNanos))
            .orElse(null);
    if (bottleneck != null) {
      output.put("bottleneck", bottleneck.name);
      output.put("bottleneckPercentage", Math.round(bottleneck.percentage * 10.0) / 10.0);
    }

    return output;
  }

  /** Internal class to track individual step metrics */
  private static class StepMetric {
    String name;
    Long startTimeNanos;
    Long durationNanos;
    Double durationMs;
    Double percentage;
    Map<String, Object> metadata;

    StepMetric(String name) {
      this.name = name;
      this.metadata = new LinkedHashMap<>();
    }
  }
}
