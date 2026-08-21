package com.mw.recommendation.engine.v3.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.recommendation.engine.config.MwRecommendationEngineProperties;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyRequestDTO;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyResponseDTO;
import com.mw.recommendation.engine.service.SecurityContextService;
import com.mw.recommendation.engine.v3.config.V3Properties;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
 * v3 Measure client. Same wire contract as the shared client (request DTOs are reused read-only)
 * plus what the PRD requires and v1 lacks: one retry on failure (PRD §3.3), explicit connect/read
 * timeouts (bounded worst case), and a v3-scoped cache name. The normalized cache key (sorted
 * referenceIds + duration + aggregate) is kept — it is the optimization that makes iterative
 * planning ~54% faster.
 */
@Service
@Slf4j
public class MeasureV3Client {

  private final V3Properties properties;
  private final MwRecommendationEngineProperties sharedProperties;
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final SecurityContextService securityContextService;

  /**
   * Self-reference used to invoke the {@code @Cacheable} method through the Spring proxy; a direct
   * in-class call would be self-invocation and bypass the cache. Resolved lazily via {@link
   * ObjectProvider} to avoid a constructor-time circular dependency.
   */
  private final ObjectProvider<MeasureV3Client> self;

  public MeasureV3Client(
      V3Properties properties,
      MwRecommendationEngineProperties sharedProperties,
      org.springframework.boot.web.client.RestTemplateBuilder restTemplateBuilder,
      ObjectMapper objectMapper,
      SecurityContextService securityContextService,
      ObjectProvider<MeasureV3Client> self) {
    this.properties = properties;
    this.self = self;
    this.sharedProperties = sharedProperties;
    // Built privately (not a @Bean): a second RestTemplate bean would break existing tests
    // that @MockBean an unqualified RestTemplate. Timeouts bound the retry's worst case.
    this.restTemplate =
        restTemplateBuilder
            .connectTimeout(
                java.time.Duration.ofMillis(properties.getMeasure().getConnectTimeoutMs()))
            .readTimeout(java.time.Duration.ofMillis(properties.getMeasure().getReadTimeoutMs()))
            .build();
    this.objectMapper = objectMapper;
    this.securityContextService = securityContextService;
  }

  /** v3-specific URL when configured, else the shared measure URL (read-only reuse). */
  private String resolveApiUrl() {
    String v3Url = properties.getMeasure().getApiUrl();
    if (v3Url != null && !v3Url.isBlank()) {
      return v3Url;
    }
    return sharedProperties.getMeasure() != null ? sharedProperties.getMeasure().getApiUrl() : null;
  }

  public static String cacheKey(MeasureReachFrequencyRequestDTO request, boolean aggregate) {
    if (request.getInventories() == null || request.getInventories().isEmpty()) {
      return "empty_" + aggregate;
    }
    String sortedRefIds =
        request.getInventories().stream()
            .map(inv -> inv.getReferenceId() != null ? inv.getReferenceId() : "null")
            .sorted()
            .collect(Collectors.joining(","));
    return sortedRefIds + "_" + request.getDuration() + "_" + aggregate;
  }

  /**
   * Public, fail-soft entry point. Delegates to the cached {@link #fetchReachAndFrequency} through
   * the Spring proxy and converts any failure into an empty list. Keeping the fallback out here
   * (not inside the cached method) means a failed call caches nothing, so the next request retries
   * live instead of being served a cached error for the rest of the TTL.
   */
  public List<MeasureReachFrequencyResponseDTO> getReachAndFrequency(
      MeasureReachFrequencyRequestDTO request, boolean aggregate) {
    String apiUrl = resolveApiUrl();
    if (apiUrl == null || apiUrl.isBlank()) {
      log.debug("v3 Measure API URL not configured; skipping enrichment");
      return Collections.emptyList();
    }
    try {
      return self.getObject().fetchReachAndFrequency(request, aggregate);
    } catch (Exception e) {
      log.warn("v3 Measure skipping enrichment: {}", e.getMessage());
      return Collections.emptyList();
    }
  }

  /**
   * Cached Measure call. Retries transport failures up to the configured attempts; on exhaustion it
   * throws so Spring caches nothing. A degraded 200 (no usable rows) returns empty so the {@code
   * unless} clause drops it. Only validated, non-empty successful responses are cached.
   */
  @Cacheable(
      value = "v3MeasureReachFrequency",
      key =
          "T(com.mw.recommendation.engine.v3.pipeline.MeasureV3Client).cacheKey(#request, #aggregate)",
      unless = "#result == null || #result.isEmpty()")
  public List<MeasureReachFrequencyResponseDTO> fetchReachAndFrequency(
      MeasureReachFrequencyRequestDTO request, boolean aggregate) {
    String apiUrl = resolveApiUrl();
    String url = apiUrl + (apiUrl.contains("?") ? "&" : "?") + "aggregate=" + aggregate;

    int attempts = 1 + Math.max(0, properties.getMeasure().getRetryAttempts());
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
            "v3 Measure call ok in {}ms (attempt {}/{}) for {} inventories, {} rows",
            System.currentTimeMillis() - start,
            attempt,
            attempts,
            request.getInventories().size(),
            body != null ? body.size() : 0);
        // Do not cache degraded/all-error 200 responses; empty lets `unless` drop them.
        return com.mw.recommendation.engine.service.MeasureApiClient.hasUsableData(body)
            ? body
            : Collections.emptyList();
      } catch (Exception e) {
        last = e;
        log.warn(
            "v3 Measure call failed after {}ms (attempt {}/{}): {}",
            System.currentTimeMillis() - start,
            attempt,
            attempts,
            e.getMessage());
      }
    }
    // All attempts failed: throw so Spring does not cache the failure.
    throw new IllegalStateException("v3 Measure exhausted " + attempts + " attempts", last);
  }
}
