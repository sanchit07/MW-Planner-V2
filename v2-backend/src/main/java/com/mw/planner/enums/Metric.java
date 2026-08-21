package com.mw.planner.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Metric {
  API_REQUESTS_SUCCESS_TOTAL(
      "api_requests_success_total", "Total number of successful API requests"),
  API_REQUESTS_ERROR_TOTAL("api_requests_error_total", "Total number of failed API requests");

  private final String name;
  private final String description;
}
