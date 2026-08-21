package com.mw.recommendation.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mw.recommendation.engine.config.MwRecommendationEngineProperties;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyRequestDTO;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Debug logger for Measure API requests. Writes detailed request/response information to files for
 * troubleshooting. Thread-safe and async to prevent blocking main application flow.
 *
 * <p>Output format: JSON lines (one JSON object per line) in daily-rotated files.
 */
@Service
@Slf4j
public class MeasureApiDebugLogger {

  private final MwRecommendationEngineProperties properties;
  private final ObjectMapper debugObjectMapper;
  private final ConcurrentHashMap<String, Long> requestTimestamps = new ConcurrentHashMap<>();

  public MeasureApiDebugLogger(MwRecommendationEngineProperties properties) {
    this.properties = properties;
    // Create dedicated ObjectMapper for debug logging
    this.debugObjectMapper = new ObjectMapper();
    this.debugObjectMapper.registerModule(new JavaTimeModule());
    this.debugObjectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    this.debugObjectMapper.enable(SerializationFeature.INDENT_OUTPUT);
  }

  /** Check if debug logging is enabled */
  public boolean isEnabled() {
    return properties.getMeasure() != null
        && properties.getMeasure().getDebug() != null
        && properties.getMeasure().getDebug().isEnabled();
  }

  /**
   * Log Measure API request payload asynchronously.
   *
   * @param request The request payload
   * @param cacheKey The cache key used
   * @param aggregate The aggregate flag
   * @param url The API URL
   * @return Request ID for correlation with response/error logs
   */
  public String logRequestStart(
      MeasureReachFrequencyRequestDTO request, String cacheKey, boolean aggregate, String url) {
    if (!isEnabled()) {
      return null;
    }

    String requestId = UUID.randomUUID().toString();
    requestTimestamps.put(requestId, System.currentTimeMillis());

    logRequestAsync(request, cacheKey, aggregate, url, requestId);
    return requestId;
  }

  /**
   * Log successful API response asynchronously.
   *
   * @param requestId Request ID from logRequestStart
   * @param response The API response
   * @param elapsedMs Time taken for the API call
   */
  public void logResponse(String requestId, ResponseEntity<?> response, long elapsedMs) {
    if (!isEnabled() || requestId == null) {
      return;
    }

    logResponseAsync(requestId, response, elapsedMs);
    requestTimestamps.remove(requestId);
  }

  /**
   * Log API call error asynchronously.
   *
   * @param requestId Request ID from logRequestStart
   * @param error The exception that occurred
   * @param elapsedMs Time taken before error
   */
  public void logError(String requestId, Exception error, long elapsedMs) {
    if (!isEnabled() || requestId == null) {
      return;
    }

    logErrorAsync(requestId, error, elapsedMs);
    requestTimestamps.remove(requestId);
  }

  @Async("virtualThreadTaskExecutor")
  void logRequestAsync(
      MeasureReachFrequencyRequestDTO request,
      String cacheKey,
      boolean aggregate,
      String url,
      String requestId) {
    try {
      Map<String, Object> logEntry = new HashMap<>();
      logEntry.put("timestamp", LocalDateTime.now().toString());
      logEntry.put("requestId", requestId);
      logEntry.put("type", "REQUEST");
      logEntry.put("cacheKey", cacheKey);
      logEntry.put("aggregate", aggregate);
      logEntry.put("url", url);
      logEntry.put(
          "inventoryCount", request.getInventories() != null ? request.getInventories().size() : 0);
      logEntry.put("payload", request);

      writeToFile(logEntry);

      log.debug(
          "[MEASURE-DEBUG] Logged request {} with {} inventories",
          requestId,
          request.getInventories() != null ? request.getInventories().size() : 0);
    } catch (Exception e) {
      log.warn("Failed to write debug log for request {}: {}", requestId, e.getMessage());
    }
  }

  @Async("virtualThreadTaskExecutor")
  void logResponseAsync(String requestId, ResponseEntity<?> response, long elapsedMs) {
    try {
      Map<String, Object> logEntry = new HashMap<>();
      logEntry.put("timestamp", LocalDateTime.now().toString());
      logEntry.put("requestId", requestId);
      logEntry.put("type", "RESPONSE");
      logEntry.put("elapsedMs", elapsedMs);

      Map<String, Object> responseData = new HashMap<>();
      responseData.put("statusCode", response.getStatusCode().value());
      responseData.put("statusText", response.getStatusCode().toString());

      if (properties.getMeasure().getDebug().isIncludeResponse() && response.getBody() != null) {
        responseData.put("body", response.getBody());
      } else {
        responseData.put("bodySize", response.getBody() != null ? "present" : "null");
      }

      logEntry.put("response", responseData);

      writeToFile(logEntry);

      log.debug("[MEASURE-DEBUG] Logged response for request {} ({}ms)", requestId, elapsedMs);
    } catch (Exception e) {
      log.warn("Failed to write debug log for response {}: {}", requestId, e.getMessage());
    }
  }

  @Async("virtualThreadTaskExecutor")
  void logErrorAsync(String requestId, Exception error, long elapsedMs) {
    try {
      Map<String, Object> logEntry = new HashMap<>();
      logEntry.put("timestamp", LocalDateTime.now().toString());
      logEntry.put("requestId", requestId);
      logEntry.put("type", "ERROR");
      logEntry.put("elapsedMs", elapsedMs);

      Map<String, Object> errorData = new HashMap<>();
      errorData.put("message", error.getMessage());
      errorData.put("class", error.getClass().getName());

      logEntry.put("error", errorData);

      writeToFile(logEntry);

      log.debug("[MEASURE-DEBUG] Logged error for request {} ({}ms)", requestId, elapsedMs);
    } catch (Exception e) {
      log.warn("Failed to write debug log for error {}: {}", requestId, e.getMessage());
    }
  }

  private synchronized void writeToFile(Map<String, Object> logEntry) throws IOException {
    String logDirectory = properties.getMeasure().getDebug().getLogDirectory();
    Path dirPath = Paths.get(logDirectory);

    // Create directory if it doesn't exist
    if (!Files.exists(dirPath)) {
      Files.createDirectories(dirPath);
    }

    // Generate daily log file name
    String fileName =
        String.format(
            "measure-api-requests-%s.log",
            LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
    Path filePath = dirPath.resolve(fileName);

    // Check file size and rotate if needed
    if (Files.exists(filePath)) {
      long fileSizeMb = Files.size(filePath) / (1024 * 1024);
      if (fileSizeMb >= properties.getMeasure().getDebug().getMaxFileSizeMb()) {
        rotateFile(filePath);
      }
    }

    // Write log entry as single-line JSON (JSON Lines format)
    String jsonLine = debugObjectMapper.writeValueAsString(logEntry);
    try (FileWriter writer = new FileWriter(filePath.toFile(), true)) {
      writer.write(jsonLine);
      writer.write(System.lineSeparator());
    }

    // Cleanup old files
    cleanupOldFiles(dirPath);
  }

  private void rotateFile(Path filePath) throws IOException {
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
    String rotatedName =
        filePath.getFileName().toString().replace(".log", "-" + timestamp + ".log");
    Path rotatedPath = filePath.getParent().resolve(rotatedName);
    Files.move(filePath, rotatedPath);
    log.info("[MEASURE-DEBUG] Rotated log file to {}", rotatedName);
  }

  private void cleanupOldFiles(Path dirPath) {
    try {
      int retentionDays = properties.getMeasure().getDebug().getRetentionDays();
      LocalDate cutoffDate = LocalDate.now().minusDays(retentionDays);

      File dir = dirPath.toFile();
      File[] files = dir.listFiles((d, name) -> name.startsWith("measure-api-requests-"));

      if (files != null) {
        for (File file : files) {
          // Extract date from filename (format: measure-api-requests-2026-03-29.log)
          String name = file.getName();
          if (name.matches("measure-api-requests-\\d{4}-\\d{2}-\\d{2}.*\\.log")) {
            String dateStr = name.substring(24, 34); // Extract YYYY-MM-DD
            LocalDate fileDate = LocalDate.parse(dateStr);
            if (fileDate.isBefore(cutoffDate)) {
              if (file.delete()) {
                log.info("[MEASURE-DEBUG] Deleted old log file: {}", name);
              }
            }
          }
        }
      }
    } catch (Exception e) {
      log.warn("[MEASURE-DEBUG] Failed to cleanup old log files: {}", e.getMessage());
    }
  }
}
