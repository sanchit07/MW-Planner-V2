package com.mw.planner.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.mw.planner.config.MwPlannerProperties;
import com.mw.planner.dto.recommendation.RecommendationApiResponse;
import com.mw.planner.dto.recommendation.RecommendationRequestDTO;
import com.mw.planner.dto.recommendation.RecommendationStatusResponseDTO;
import com.mw.planner.service.SecurityContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class RecommendationEngineApiClientTest {

  private static final String BASE_URL = "http://recommendation-engine";
  private static final String EXPECTED_GENERATE_URL =
      BASE_URL + "/api/v1/recommendation/campaigns/campaign123/recommendations";

  @Mock private RestTemplate restTemplate;
  @Mock private SecurityContextService securityContextService;

  private RecommendationEngineApiClient apiClient;

  @BeforeEach
  void setUp() {
    MwPlannerProperties mwPlannerProperties = new MwPlannerProperties();
    mwPlannerProperties.getRecommendationEngine().setBaseUrl(BASE_URL);
    apiClient =
        new RecommendationEngineApiClient(
            mwPlannerProperties, restTemplate, securityContextService);
    when(securityContextService.getBearerToken()).thenReturn("token123");
  }

  @SuppressWarnings("unchecked")
  private ArgumentCaptor<String> stubExchangeAndCaptureUrl() {
    RecommendationApiResponse<RecommendationStatusResponseDTO> apiResponse =
        new RecommendationApiResponse<>();
    apiResponse.setSuccess(true);
    RecommendationStatusResponseDTO statusResponse = new RecommendationStatusResponseDTO();
    statusResponse.setRunId("run123");
    apiResponse.setData(statusResponse);

    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    when(restTemplate.exchange(
            urlCaptor.capture(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
        .thenReturn(ResponseEntity.ok(apiResponse));
    return urlCaptor;
  }

  @Test
  void generateRecommendation_forceRegenerateFalse_shouldUseExactUrlWithoutQueryParam() {
    // Given
    ArgumentCaptor<String> urlCaptor = stubExchangeAndCaptureUrl();

    // When
    RecommendationApiResponse<RecommendationStatusResponseDTO> result =
        apiClient.generateRecommendation("campaign123", new RecommendationRequestDTO(), false);

    // Then: URL must be byte-for-byte identical to the pre-forceRegenerate behavior
    assertThat(result).isNotNull();
    assertThat(result.getData().getRunId()).isEqualTo("run123");
    assertThat(urlCaptor.getValue()).isEqualTo(EXPECTED_GENERATE_URL);
  }

  @Test
  void generateRecommendation_forceRegenerateTrue_shouldAppendQueryParam() {
    // Given
    ArgumentCaptor<String> urlCaptor = stubExchangeAndCaptureUrl();

    // When
    RecommendationApiResponse<RecommendationStatusResponseDTO> result =
        apiClient.generateRecommendation("campaign123", new RecommendationRequestDTO(), true);

    // Then
    assertThat(result).isNotNull();
    assertThat(urlCaptor.getValue()).isEqualTo(EXPECTED_GENERATE_URL + "?forceRegenerate=true");
  }
}
