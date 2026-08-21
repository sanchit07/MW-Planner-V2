package com.mw.planner.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum SalesPerformanceShowBy {
  COUNTRY("country"),
  CITY("city"),
  ADVERTISER("advertiser"),
  AGENCY("agency"),
  TEAM("team");

  private final String key;

  SalesPerformanceShowBy(String key) {
    this.key = key;
  }

  @JsonValue
  public String getKey() {
    return key;
  }

  @JsonCreator
  public static SalesPerformanceShowBy fromString(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return Arrays.stream(values())
        .filter(v -> v.key.equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown showBy: " + value));
  }
}
