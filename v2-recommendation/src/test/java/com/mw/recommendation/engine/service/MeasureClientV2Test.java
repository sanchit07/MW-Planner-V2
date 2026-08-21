package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.recommendation.engine.config.MwRecommendationEngineProperties;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyRequestDTO;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyResponseDTO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

/**
 * Finding #3: the v2 Measure client must fail fast (bounded timeouts) and retry once, then degrade
 * gracefully to an empty result instead of blocking or throwing.
 */
@ExtendWith(MockitoExtension.class)
class MeasureClientV2Test {

  @Mock private RestTemplateBuilder restTemplateBuilder;
  @Mock private RestTemplate restTemplate;
  @Mock private SecurityContextService securityContextService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final MwRecommendationEngineProperties properties =
      new MwRecommendationEngineProperties();

  private MeasureClientV2 buildClient() {
    when(restTemplateBuilder.connectTimeout(any())).thenReturn(restTemplateBuilder);
    when(restTemplateBuilder.readTimeout(any())).thenReturn(restTemplateBuilder);
    when(restTemplateBuilder.build()).thenReturn(restTemplate);
    @SuppressWarnings("unchecked")
    org.springframework.beans.factory.ObjectProvider<MeasureClientV2> selfProvider =
        org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
    MeasureClientV2 client =
        new MeasureClientV2(
            properties,
            restTemplateBuilder,
            objectMapper,
            securityContextService,
            selfProvider,
            5000L,
            30000L,
            1);
    // No Spring proxy in a unit test: the self-provider returns the client itself, so the public
    // method's delegation exercises the real fetch logic (caching is a no-op here). Lenient because
    // the blank-URL test returns before ever calling the provider.
    org.mockito.Mockito.lenient().when(selfProvider.getObject()).thenReturn(client);
    return client;
  }

  private static MeasureReachFrequencyRequestDTO request() {
    return MeasureReachFrequencyRequestDTO.builder().inventories(List.of()).duration(30).build();
  }

  @BeforeEach
  void setUp() {
    properties.getMeasure().setApiUrl("http://measure.test/v2/reach-and-frequency");
  }

  @Test
  void retriesOnceThenFailsSoft() {
    MeasureClientV2 client = buildClient();
    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
        .thenThrow(new RuntimeException("measure down"));

    List<MeasureReachFrequencyResponseDTO> result =
        client.getReachAndFrequencyBySites(request(), true);

    assertTrue(result.isEmpty(), "failure must degrade to an empty result, never throw");
    // 1 initial attempt + 1 configured retry = 2 calls.
    verify(restTemplate, times(2))
        .exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class));
  }

  @Test
  void blankUrl_skipsCallEntirely() {
    properties.getMeasure().setApiUrl("");
    MeasureClientV2 client = buildClient();

    List<MeasureReachFrequencyResponseDTO> result =
        client.getReachAndFrequencyBySites(request(), true);

    assertTrue(result.isEmpty());
    verify(restTemplate, never())
        .exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class));
  }
}
