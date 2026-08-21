package com.mw.planner.service;

import com.mw.planner.domain.AdServerRequestLog;
import com.mw.planner.repository.AdServerRequestLogRepository;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

/**
 * Service for managing AdServerRequestLog persistence. Handles logging of outbound API
 * requests/responses with sensitive data masking. Designed to be error-resilient - logging failures
 * never propagate to prevent disrupting business logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdServerRequestLogService {

  private final AdServerRequestLogRepository repository;

  /**
   * Header names that contain sensitive authentication data and should be masked before
   * persistence. Case-insensitive matching is performed.
   */
  private static final List<String> SENSITIVE_HEADER_NAMES =
      Arrays.asList("authorization", "x-api-key", "api-key", "auth-token", "bearer");

  /** Placeholder value used when masking sensitive header values */
  private static final String REDACTED_VALUE = "[REDACTED]";

  /**
   * Save an API request/response log entry to MongoDB. This method is designed to never throw
   * exceptions - any errors during persistence are logged but not propagated.
   *
   * @param endpoint Full URL called (e.g., "https://ads.movingwalls.com/api/v1/campaigns")
   * @param headers Original HTTP headers sent in the request
   * @param requestBody Request payload (DTO) sent to the API
   * @param responseCode HTTP status code received (200, 400, 500, etc.) or 0 for network errors
   * @param responseBody Response payload (DTO or error details) received from the API
   * @param campaignId Campaign ID from the request for easy filtering
   */
  public void saveLog(
      String endpoint,
      HttpHeaders headers,
      Object requestBody,
      Integer responseCode,
      Object responseBody,
      String campaignId) {

    try {
      // Mask sensitive headers before persistence
      Map<String, String> maskedHeaders = maskSensitiveHeaders(headers);

      // Build and save log entry
      AdServerRequestLog logEntry =
          AdServerRequestLog.builder()
              .endpoint(endpoint)
              .requestHeaders(maskedHeaders)
              .requestBody(requestBody)
              .responseCode(responseCode)
              .responseBody(responseBody)
              .campaignId(campaignId)
              .build();

      repository.save(logEntry);
      log.debug(
          "Saved ad server request log for campaign: {}, endpoint: {}, status: {}",
          campaignId,
          endpoint,
          responseCode);

    } catch (Exception e) {
      // CRITICAL: Never throw - logging failure should not break business flow
      log.error(
          "Failed to save ad server request log for campaign {}: {}",
          campaignId,
          e.getMessage(),
          e);
    }
  }

  /**
   * Mask sensitive header values (Authorization, API keys, etc.). Performs case-insensitive
   * matching against known sensitive header names and replaces their values with [REDACTED].
   *
   * @param headers HTTP headers to mask
   * @return New map with sensitive values masked, or empty map if headers are null/empty
   */
  private Map<String, String> maskSensitiveHeaders(HttpHeaders headers) {
    if (headers == null || headers.isEmpty()) {
      return Map.of();
    }

    Map<String, String> masked = new HashMap<>();
    headers.forEach(
        (key, values) -> {
          // Take first value only (headers can have multiple values)
          String value = (values == null || values.isEmpty()) ? null : values.get(0);

          if (value != null && isSensitiveHeader(key)) {
            masked.put(key, REDACTED_VALUE);
          } else {
            masked.put(key, value);
          }
        });

    return masked;
  }

  /**
   * Check if header name matches known sensitive patterns. Performs case-insensitive substring
   * matching.
   *
   * @param headerName Header name to check
   * @return true if header contains sensitive data, false otherwise
   */
  private boolean isSensitiveHeader(String headerName) {
    if (headerName == null) {
      return false;
    }
    String lower = headerName.toLowerCase();
    return SENSITIVE_HEADER_NAMES.stream().anyMatch(lower::contains);
  }
}
