package com.mw.planner.service;

import static com.mw.planner.enums.Metric.*;
import static com.mw.planner.enums.Tag.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetricsService {

  private final MeterRegistry meterRegistry;
  private static final String UNKNOWN = "unknown";

  public void incrementApiRequestSuccess(String endpoint, String method) {
    Counter.builder(API_REQUESTS_SUCCESS_TOTAL.getName())
        .description(API_REQUESTS_SUCCESS_TOTAL.getDescription())
        .tag(ENDPOINT.name(), endpoint != null ? endpoint : UNKNOWN)
        .tag(METHOD.name(), method != null ? method : UNKNOWN)
        .register(meterRegistry)
        .increment();
  }

  public void incrementApiRequestError(String endpoint, String method, String errorCode) {
    Counter.builder(API_REQUESTS_ERROR_TOTAL.getName())
        .description(API_REQUESTS_ERROR_TOTAL.getDescription())
        .tag(ENDPOINT.name(), endpoint != null ? endpoint : UNKNOWN)
        .tag(METHOD.name(), method != null ? method : UNKNOWN)
        .tag(ERROR_CODE.name(), errorCode != null ? errorCode : UNKNOWN)
        .register(meterRegistry)
        .increment();
  }
}
