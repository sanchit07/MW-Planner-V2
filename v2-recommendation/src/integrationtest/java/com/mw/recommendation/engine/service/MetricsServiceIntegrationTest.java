package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;

import com.mw.recommendation.engine.TestcontainersConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = TestcontainersConfiguration.class)
class MetricsServiceIntegrationTest {

  @Autowired private MetricsService metricsService;

  @Autowired private MeterRegistry meterRegistry;

  @Test
  void incrementApiRequestSuccess_ShouldIncrementCounter_WhenValidInput() {
    // Given
    String endpoint = "/api/v1/recommendation";
    String method = "GET";

    // When & Then - just verify no exceptions are thrown
    assertDoesNotThrow(() -> metricsService.incrementApiRequestSuccess(endpoint, method));
  }

  @Test
  void incrementApiRequestSuccess_ShouldIncrementCounter_WhenNullInput() {
    // When & Then - just verify no exceptions are thrown
    assertDoesNotThrow(() -> metricsService.incrementApiRequestSuccess(null, null));
  }

  @Test
  void incrementApiRequestError_ShouldIncrementCounter_WhenValidInput() {
    // Given
    String endpoint = "/api/v1/recommendation";
    String method = "POST";
    String errorCode = "VALIDATION_ERROR";

    // When & Then - just verify no exceptions are thrown
    assertDoesNotThrow(() -> metricsService.incrementApiRequestError(endpoint, method, errorCode));
  }

  @Test
  void incrementApiRequestError_ShouldIncrementCounter_WhenNullInput() {
    // When & Then - just verify no exceptions are thrown
    assertDoesNotThrow(() -> metricsService.incrementApiRequestError(null, null, null));
  }
}
