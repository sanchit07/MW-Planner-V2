package com.mw.recommendation.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.recommendation.engine.config.MwRecommendationEngineProperties;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyRequestDTO;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyResponseDTO;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * v2 Measure client (finding #3). Same wire contract and cache-key strategy as the shared {@link
 * MeasureApiClient}, but with bounded connect/read timeouts and one retry instead of the shared
 * client's 10-minute timeout, so a slow/hung Measure API can no longer stall a v2 run for minutes.
 *
 * <p><b>v1 isolation:</b> this is a separate bean used only by the v2 pipeline; the shared client
 * and its {@code RestTemplate} are untouched, so v1 keeps its existing behavior. It also reuses the
 * shared client's static cache-key generator but caches under its own name ({@code
 * v2MeasureReachFrequency}), so v1's cache entries are never affected. Unlike the shared client it
 * does not log the full request body (finding #12).
 */
@Service
@Slf4j
public class MeasureClientV2 {

  private final MwRecommendationEngineProperties properties;
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final SecurityContextService securityContextService;
  private final int retryAttempts;

  /**
   * Self-reference used to invoke the {@code @Cacheable} method through the Spring proxy; a direct
   * in-class call would be self-invocation and bypass the cache. Resolved lazily via {@link
   * ObjectProvider} to avoid a constructor-time circular dependency.
   */
  private final ObjectProvider<MeasureClientV2> self;

  public MeasureClientV2(
      MwRecommendationEngineProperties properties,
      RestTemplateBuilder restTemplateBuilder,
      ObjectMapper objectMapper,
      SecurityContextService securityContextService,
      ObjectProvider<MeasureClientV2> self,
      @Value("${mw-recommendation-engine.v2.measure.connect-timeout-ms:5000}")
          long connectTimeoutMs,
      @Value("${mw-recommendation-engine.v2.measure.read-timeout-ms:30000}") long readTimeoutMs,
      @Value("${mw-recommendation-engine.v2.measure.retry-attempts:1}") int retryAttempts) {
    this.properties = properties;
    this.self = self;
    // Built privately (not a @Bean) so we do not register a second unqualified RestTemplate bean,
    // which would break existing tests that @MockBean RestTemplate.
    this.restTemplate =
        restTemplateBuilder
            .connectTimeout(Duration.ofMillis(connectTimeoutMs))
            .readTimeout(Duration.ofMillis(readTimeoutMs))
            .build();
    this.objectMapper = objectMapper;
    this.securityContextService = securityContextService;
    this.retryAttempts = Math.max(0, retryAttempts);
  }

  /**
   * Public, fail-soft entry point. Delegates to the cached {@link #fetchReachAndFrequencyBySites}
   * through the Spring proxy and converts any failure into an empty list. Keeping the fallback out
   * here (not inside the cached method) means a failed call caches nothing, so the next request
   * retries live instead of being served a cached error for the rest of the TTL.
   */
  public List<MeasureReachFrequencyResponseDTO> getReachAndFrequencyBySites(
      MeasureReachFrequencyRequestDTO request, boolean aggregate) {
    String apiUrl = properties.getMeasure() != null ? properties.getMeasure().getApiUrl() : null;
    if (apiUrl == null || apiUrl.isBlank()) {
      log.debug("v2 Measure API URL not configured; skipping reach/frequency enrichment");
      return Collections.emptyList();
    }
    try {
      return self.getObject().fetchReachAndFrequencyBySites(request, aggregate);
    } catch (Exception e) {
      log.warn("[MEASURE-API-V2] skipping enrichment: {}", e.getMessage());
      return Collections.emptyList();
    }
  }

  /**
   * Cached Measure call. Retries transport failures up to {@code retryAttempts}; on exhaustion it
   * throws so Spring caches nothing. A degraded 200 (no usable rows) returns empty so the {@code
   * unless} clause drops it. Only validated, non-empty successful responses are cached.
   */
  @Cacheable(
      value = "v2MeasureReachFrequency",
      key =
          "T(com.mw.recommendation.engine.service.MeasureApiClient).generateCacheKeyStatic(#request, #aggregate)",
      unless = "#result == null || #result.isEmpty()")
  public List<MeasureReachFrequencyResponseDTO> fetchReachAndFrequencyBySites(
      MeasureReachFrequencyRequestDTO request, boolean aggregate) {
    String apiUrl = properties.getMeasure().getApiUrl();
    String url = apiUrl + (apiUrl.contains("?") ? "&" : "?") + "aggregate=" + aggregate;

    int attempts = 1 + retryAttempts;
    Exception last = null;
    for (int attempt = 1; attempt <= attempts; attempt++) {
      long start = System.currentTimeMillis();
      try {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String bearerToken = securityContextService.getBearerTokenOrNull();
        if (bearerToken != null && !bearerToken.isBlank()) {
          headers.setBearerAuth(bearerToken);
        }
        HttpEntity<String> entity =
            new HttpEntity<>(objectMapper.writeValueAsString(request), headers);
        ResponseEntity<List<MeasureReachFrequencyResponseDTO>> response =
            restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        List<MeasureReachFrequencyResponseDTO> body = response.getBody();
        log.info(
            "[MEASURE-API-V2] ok in {}ms (attempt {}/{}) for {} inventories, {} rows",
            System.currentTimeMillis() - start,
            attempt,
            attempts,
            request.getInventories() != null ? request.getInventories().size() : 0,
            body != null ? body.size() : 0);
        // Do not cache degraded/all-error 200 responses; empty lets `unless` drop them.
        return MeasureApiClient.hasUsableData(body) ? body : Collections.emptyList();
      } catch (Exception e) {
        last = e;
        log.warn(
            "[MEASURE-API-V2] call failed after {}ms (attempt {}/{}): {}",
            System.currentTimeMillis() - start,
            attempt,
            attempts,
            e.getMessage());
      }
    }
    // All attempts failed: throw so Spring does not cache the failure.
    throw new IllegalStateException("[MEASURE-API-V2] exhausted " + attempts + " attempt(s)", last);
  }
}
