package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.AdServerRequestLog;
import com.mw.planner.dto.ads.AdsCampaignRequestDTO;
import com.mw.planner.dto.ads.AdsSubmissionResponseDTO;
import com.mw.planner.repository.AdServerRequestLogRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

@ExtendWith(MockitoExtension.class)
class AdServerRequestLogServiceTest {

  @Mock private AdServerRequestLogRepository repository;

  @InjectMocks private AdServerRequestLogService adServerRequestLogService;

  private String testEndpoint;
  private HttpHeaders testHeaders;
  private AdsCampaignRequestDTO testRequestBody;
  private AdsSubmissionResponseDTO testResponseBody;
  private String testCampaignId;

  @BeforeEach
  void setUp() {
    testEndpoint = "https://ads.movingwalls.com/api/v1/campaigns";
    testCampaignId = "campaign-123";

    testHeaders = new HttpHeaders();
    testHeaders.set("Content-Type", "application/json");
    testHeaders.set("User-Agent", "mw-planner");

    testRequestBody = AdsCampaignRequestDTO.builder().payloadType("DIRECT_PUBLISHER_SPLIT").build();

    testResponseBody = AdsSubmissionResponseDTO.builder().total(1).successful(1).failed(0).build();
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(repository);
  }

  // ========== saveLog Tests ==========

  @Test
  void saveLog_WithValidData_ShouldSaveLog() {
    // Given
    when(repository.save(any(AdServerRequestLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    adServerRequestLogService.saveLog(
        testEndpoint, testHeaders, testRequestBody, 200, testResponseBody, testCampaignId);

    // Then
    ArgumentCaptor<AdServerRequestLog> logCaptor =
        ArgumentCaptor.forClass(AdServerRequestLog.class);
    verify(repository, times(1)).save(logCaptor.capture());

    AdServerRequestLog savedLog = logCaptor.getValue();
    assertThat(savedLog.getEndpoint()).isEqualTo(testEndpoint);
    assertThat(savedLog.getCampaignId()).isEqualTo(testCampaignId);
    assertThat(savedLog.getResponseCode()).isEqualTo(200);
    assertThat(savedLog.getRequestBody()).isEqualTo(testRequestBody);
    assertThat(savedLog.getResponseBody()).isEqualTo(testResponseBody);
    assertThat(savedLog.getRequestHeaders()).containsEntry("Content-Type", "application/json");
    assertThat(savedLog.getRequestHeaders()).containsEntry("User-Agent", "mw-planner");
  }

  @Test
  void saveLog_WithSensitiveHeaders_ShouldMaskThem() {
    // Given
    testHeaders.set("Authorization", "Bearer secret-token-12345");
    testHeaders.set("X-API-Key", "api-key-67890");
    testHeaders.set("Custom-Header", "safe-value");

    when(repository.save(any(AdServerRequestLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    adServerRequestLogService.saveLog(
        testEndpoint, testHeaders, testRequestBody, 200, testResponseBody, testCampaignId);

    // Then
    ArgumentCaptor<AdServerRequestLog> logCaptor =
        ArgumentCaptor.forClass(AdServerRequestLog.class);
    verify(repository, times(1)).save(logCaptor.capture());

    AdServerRequestLog savedLog = logCaptor.getValue();
    assertThat(savedLog.getRequestHeaders()).containsEntry("Authorization", "[REDACTED]");
    assertThat(savedLog.getRequestHeaders()).containsEntry("X-API-Key", "[REDACTED]");
    assertThat(savedLog.getRequestHeaders()).containsEntry("Custom-Header", "safe-value");
  }

  @Test
  void saveLog_WithCaseInsensitiveSensitiveHeaders_ShouldMaskThem() {
    // Given
    testHeaders.set("AUTHORIZATION", "Bearer token");
    testHeaders.set("authorization", "Bearer token2");
    testHeaders.set("auth-token", "token3");

    when(repository.save(any(AdServerRequestLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    adServerRequestLogService.saveLog(
        testEndpoint, testHeaders, testRequestBody, 200, testResponseBody, testCampaignId);

    // Then
    ArgumentCaptor<AdServerRequestLog> logCaptor =
        ArgumentCaptor.forClass(AdServerRequestLog.class);
    verify(repository, times(1)).save(logCaptor.capture());

    AdServerRequestLog savedLog = logCaptor.getValue();
    // All should be redacted (case-insensitive matching)
    savedLog
        .getRequestHeaders()
        .forEach(
            (key, value) -> {
              if (key.toLowerCase().contains("auth")) {
                assertThat(value).isEqualTo("[REDACTED]");
              }
            });
  }

  @Test
  void saveLog_WithNullHeaders_ShouldSaveEmptyHeadersMap() {
    // Given
    when(repository.save(any(AdServerRequestLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    adServerRequestLogService.saveLog(
        testEndpoint, null, testRequestBody, 200, testResponseBody, testCampaignId);

    // Then
    ArgumentCaptor<AdServerRequestLog> logCaptor =
        ArgumentCaptor.forClass(AdServerRequestLog.class);
    verify(repository, times(1)).save(logCaptor.capture());

    AdServerRequestLog savedLog = logCaptor.getValue();
    assertThat(savedLog.getRequestHeaders()).isEmpty();
  }

  @Test
  void saveLog_WithEmptyHeaders_ShouldSaveEmptyHeadersMap() {
    // Given
    HttpHeaders emptyHeaders = new HttpHeaders();
    when(repository.save(any(AdServerRequestLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    adServerRequestLogService.saveLog(
        testEndpoint, emptyHeaders, testRequestBody, 200, testResponseBody, testCampaignId);

    // Then
    ArgumentCaptor<AdServerRequestLog> logCaptor =
        ArgumentCaptor.forClass(AdServerRequestLog.class);
    verify(repository, times(1)).save(logCaptor.capture());

    AdServerRequestLog savedLog = logCaptor.getValue();
    assertThat(savedLog.getRequestHeaders()).isEmpty();
  }

  @Test
  void saveLog_WithErrorResponse_ShouldSaveErrorDetails() {
    // Given
    Map<String, Object> errorResponse = Map.of("error", "BadRequest", "message", "Invalid payload");

    when(repository.save(any(AdServerRequestLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    adServerRequestLogService.saveLog(
        testEndpoint, testHeaders, testRequestBody, 400, errorResponse, testCampaignId);

    // Then
    ArgumentCaptor<AdServerRequestLog> logCaptor =
        ArgumentCaptor.forClass(AdServerRequestLog.class);
    verify(repository, times(1)).save(logCaptor.capture());

    AdServerRequestLog savedLog = logCaptor.getValue();
    assertThat(savedLog.getResponseCode()).isEqualTo(400);
    assertThat(savedLog.getResponseBody()).isEqualTo(errorResponse);
  }

  @Test
  void saveLog_WithNetworkError_ShouldSaveZeroResponseCode() {
    // Given
    Map<String, Object> networkError =
        Map.of("error", "NetworkError", "message", "Connection timeout");

    when(repository.save(any(AdServerRequestLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    adServerRequestLogService.saveLog(
        testEndpoint, testHeaders, testRequestBody, 0, networkError, testCampaignId);

    // Then
    ArgumentCaptor<AdServerRequestLog> logCaptor =
        ArgumentCaptor.forClass(AdServerRequestLog.class);
    verify(repository, times(1)).save(logCaptor.capture());

    AdServerRequestLog savedLog = logCaptor.getValue();
    assertThat(savedLog.getResponseCode()).isEqualTo(0);
    assertThat(savedLog.getResponseBody()).isEqualTo(networkError);
  }

  @Test
  void saveLog_WhenRepositoryThrowsException_ShouldNotPropagate() {
    // Given
    when(repository.save(any(AdServerRequestLog.class)))
        .thenThrow(new RuntimeException("MongoDB connection error"));

    // When - should not throw exception
    adServerRequestLogService.saveLog(
        testEndpoint, testHeaders, testRequestBody, 200, testResponseBody, testCampaignId);

    // Then - verify repository.save was called despite the exception
    verify(repository, times(1)).save(any(AdServerRequestLog.class));
  }

  @Test
  void saveLog_WithNullCampaignId_ShouldSaveWithNullCampaignId() {
    // Given
    when(repository.save(any(AdServerRequestLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    adServerRequestLogService.saveLog(
        testEndpoint, testHeaders, testRequestBody, 200, testResponseBody, null);

    // Then
    ArgumentCaptor<AdServerRequestLog> logCaptor =
        ArgumentCaptor.forClass(AdServerRequestLog.class);
    verify(repository, times(1)).save(logCaptor.capture());

    AdServerRequestLog savedLog = logCaptor.getValue();
    assertThat(savedLog.getCampaignId()).isNull();
  }

  @Test
  void saveLog_WithMultiValueHeaders_ShouldTakeFirstValue() {
    // Given
    testHeaders.add("Accept", "application/json");
    testHeaders.add("Accept", "text/html"); // Add second value

    when(repository.save(any(AdServerRequestLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    adServerRequestLogService.saveLog(
        testEndpoint, testHeaders, testRequestBody, 200, testResponseBody, testCampaignId);

    // Then
    ArgumentCaptor<AdServerRequestLog> logCaptor =
        ArgumentCaptor.forClass(AdServerRequestLog.class);
    verify(repository, times(1)).save(logCaptor.capture());

    AdServerRequestLog savedLog = logCaptor.getValue();
    // Should take first value only
    assertThat(savedLog.getRequestHeaders()).containsEntry("Accept", "application/json");
  }
}
