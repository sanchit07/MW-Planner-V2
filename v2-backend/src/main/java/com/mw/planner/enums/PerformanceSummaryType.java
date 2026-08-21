package com.mw.planner.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Performance summary type filter for dashboard API. */
public enum PerformanceSummaryType {
  COST("cost"),
  REACH("reach");

  private final String value;

  PerformanceSummaryType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  public static PerformanceSummaryType fromString(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return Arrays.stream(values())
        .filter(t -> t.value.equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unknown performance summary type: " + value));
  }
}
