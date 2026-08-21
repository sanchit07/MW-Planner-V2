package com.mw.planner.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * Dashboard widget keys supported by the Customize Dashboard feature.
 *
 * <p>Serialized as kebab-case strings to match frontend keys.
 */
public enum DashboardWidgetKey {
  // Media Owner widgets
  SALES_OVERVIEW("sales-overview"),
  SALES_PERFORMANCE_SUMMARY("sales-performance-summary"),
  SALES_PIPELINE_FUNNEL("sales-pipeline-funnel"),
  REVENUE_DISTRIBUTION("revenue-distribution"),
  CREATIVE_STATUS("creative-status"),
  REGIONAL_INVENTORY_SNAPSHOT("regional-inventory-snapshot"),

  // Agency widgets
  CAMPAIGN_OVERVIEW("campaign-overview"),
  CAMPAIGN_PERFORMANCE("campaign-performance"),
  BUDGET_OVERVIEW("budget-overview"),
  BUDGET_PERFORMANCE_SUMMARY("budget-performance-summary"),
  AUDIENCE_REACH_PERFORMANCE("audience-reach-performance"),

  // Common widgets
  INVENTORY_OVERVIEW("inventory-overview"),
  UTILIZATION_BREAKDOWN("utilization-breakdown");

  private final String key;

  DashboardWidgetKey(String key) {
    this.key = key;
  }

  @JsonValue
  public String getKey() {
    return key;
  }

  @JsonCreator
  public static DashboardWidgetKey fromString(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return Arrays.stream(values())
        .filter(k -> k.key.equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown dashboard widget key: " + value));
  }
}
