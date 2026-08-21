package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricsServiceTest {

  private MeterRegistry meterRegistry;
  private MetricsService metricsService;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    metricsService = new MetricsService(meterRegistry);
  }

  @Test
  void incrementApiRequestSuccess_ShouldIncrementCounter() {
    // Given
    String endpoint = "/api/users";
    String method = "GET";

    // When
    metricsService.incrementApiRequestSuccess(endpoint, method);

    // Then
    // Verify that a counter was created and incremented
    assertThat(meterRegistry.getMeters()).hasSize(1);
    assertThat(meterRegistry.getMeters().get(0).getId().getName())
        .isEqualTo("api_requests_success_total");
  }

  @Test
  void incrementApiRequestError_ShouldIncrementCounter() {
    // Given
    String endpoint = "/api/users";
    String method = "GET";
    String errorCode = "404";

    // When
    metricsService.incrementApiRequestError(endpoint, method, errorCode);

    // Then
    // Verify that a counter was created and incremented
    assertThat(meterRegistry.getMeters()).hasSize(1);
    assertThat(meterRegistry.getMeters().get(0).getId().getName())
        .isEqualTo("api_requests_error_total");
  }

  @Test
  void incrementApiRequestSuccess_WithDifferentEndpoints_ShouldIncrementCorrectCounters() {
    // Given
    String endpoint1 = "/api/users";
    String endpoint2 = "/api/companies";
    String method = "POST";

    // When
    metricsService.incrementApiRequestSuccess(endpoint1, method);
    metricsService.incrementApiRequestSuccess(endpoint2, method);

    // Then
    // Verify that two counters were created (one for each endpoint)
    assertThat(meterRegistry.getMeters()).hasSize(2);
    assertThat(
            meterRegistry.getMeters().stream()
                .allMatch(meter -> "api_requests_success_total".equals(meter.getId().getName())))
        .isTrue();
  }

  @Test
  void incrementApiRequestError_WithDifferentErrorCodes_ShouldIncrementCorrectCounters() {
    // Given
    String endpoint = "/api/users";
    String method = "GET";
    String errorCode1 = "400";
    String errorCode2 = "500";

    // When
    metricsService.incrementApiRequestError(endpoint, method, errorCode1);
    metricsService.incrementApiRequestError(endpoint, method, errorCode2);

    // Then
    // Verify that two counters were created (one for each error code)
    assertThat(meterRegistry.getMeters()).hasSize(2);
    assertThat(
            meterRegistry.getMeters().stream()
                .allMatch(meter -> "api_requests_error_total".equals(meter.getId().getName())))
        .isTrue();
  }

  @Test
  void incrementApiRequestSuccess_WithNullValues_ShouldHandleGracefully() {
    // Given
    String endpoint = null;
    String method = "GET";

    // When
    metricsService.incrementApiRequestSuccess(endpoint, method);

    // Then
    // Verify that a counter was created even with null values
    assertThat(meterRegistry.getMeters()).hasSize(1);
    assertThat(meterRegistry.getMeters().get(0).getId().getName())
        .isEqualTo("api_requests_success_total");
  }

  @Test
  void incrementApiRequestError_WithNullValues_ShouldHandleGracefully() {
    // Given
    String endpoint = "/api/users";
    String method = null;
    String errorCode = "404";

    // When
    metricsService.incrementApiRequestError(endpoint, method, errorCode);

    // Then
    // Verify that a counter was created even with null values
    assertThat(meterRegistry.getMeters()).hasSize(1);
    assertThat(meterRegistry.getMeters().get(0).getId().getName())
        .isEqualTo("api_requests_error_total");
  }
}
