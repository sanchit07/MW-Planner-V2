package com.mw.recommendation.engine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.recommendation.engine.config.MwRecommendationEngineProperties;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyRequestDTO;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyResponseDTO;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
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
 * Measure API client for reach and frequency. When mw-recommendation-engine.measure.api-url is
 * blank, returns empty list so schedule enrichment is skipped. Aligns with mw-planner
 * getReachAndFrequencyBySites.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeasureApiClient {

  private final MwRecommendationEngineProperties properties;
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final SecurityContextService securityContextService;

  /**
   * Self-reference used to invoke the {@code @Cacheable} method through the Spring proxy. A direct
   * in-class call would be self-invocation and would bypass the cache entirely (see CLAUDE.md — the
   * same trap that makes {@code totalAdPlays} fall back to an in-memory map). {@link
   * ObjectProvider} is resolved lazily, so it does not create a constructor-time circular
   * dependency.
   */
  private final ObjectProvider<MeasureApiClient> self;

  /**
   * Generate a normalized cache key based on inventory IDs and duration. This allows cache hits
   * even when campaign-specific parameters (budget, allocation, etc.) change, since reach/frequency
   * depends primarily on which inventories and duration.
   *
   * @param request The Measure API request
   * @param aggregate The aggregate flag
   * @return Normalized cache key string
   */
  public static String generateCacheKeyStatic(
      MeasureReachFrequencyRequestDTO request, boolean aggregate) {
    if (request.getInventories() == null || request.getInventories().isEmpty()) {
      return "empty_" + aggregate;
    }

    String sortedRefIds =
        request.getInventories().stream()
            .map(inv -> inv.getReferenceId() != null ? inv.getReferenceId() : "null")
            .sorted()
            .collect(Collectors.joining(","));

    return String.format("%s_%s_%s", sortedRefIds, request.getDuration(), aggregate);
  }

  /**
   * A response is worth caching only when it carries at least one genuinely usable row — a {@code
   * "success"} status with a positive impressions or reach figure. Degraded/partial 200 responses
   * (all rows non-success, or zero metrics) are treated as "no usable data" so they are never
   * cached and the next request retries a live call. This is what stops a transient upstream hiccup
   * from poisoning the cache for the full 10-minute TTL.
   */
  public static boolean hasUsableData(List<MeasureReachFrequencyResponseDTO> body) {
    if (body == null || body.isEmpty()) {
      return false;
    }
    return body.stream()
        .anyMatch(
            r ->
                "success".equalsIgnoreCase(r.getStatus())
                    && ((r.getImpressions() != null && r.getImpressions() > 0)
                        || (r.getReach() != null && r.getReach() > 0)));
  }

  /**
   * Public, fail-soft entry point. Delegates to the cached {@link #fetchReachAndFrequencyBySites}
   * through the Spring proxy and converts any failure into an empty list so callers can cleanly
   * skip enrichment. The {@code try/catch} lives here (outside the cache) on purpose: when the
   * cached call throws, Spring caches nothing, so the next request retries a live call instead of
   * being served a cached error for the rest of the TTL.
   */
  public List<MeasureReachFrequencyResponseDTO> getReachAndFrequencyBySites(
      MeasureReachFrequencyRequestDTO request, boolean aggregate) {
    String apiUrl = properties.getMeasure() != null ? properties.getMeasure().getApiUrl() : null;
    log.info("Resolved Measure API URL: {}", apiUrl);
    if (apiUrl == null || apiUrl.isBlank()) {
      log.debug("Measure API URL not configured; skipping reach/frequency enrichment");
      return Collections.emptyList();
    }
    try {
      return self.getObject().fetchReachAndFrequencyBySites(request, aggregate);
    } catch (Exception e) {
      log.warn("Measure API call failed; skipping schedule enrichment: {}", e.getMessage());
      return Collections.emptyList();
    }
  }

  /**
   * Cached Measure API call. Since reach/frequency depends primarily on inventory IDs and date
   * range, the cache key is normalized to those stable parameters so campaign-specific params (like
   * budget) still hit the cache.
   *
   * <p>Only validated, non-empty successful responses are cached. This method throws on any HTTP or
   * transport failure (so Spring skips caching) and returns an empty list for degraded 200
   * responses (so the {@code unless} clause drops them). Callers must go through {@link
   * #getReachAndFrequencyBySites}, which supplies the fail-soft fallback.
   *
   * <p>Cache key format: sorted_referenceIds + duration + aggregate. Performance: cache MISS
   * ~18-20s, cache HIT ~5-8s. TTL is 10 minutes (see {@code CacheConfig}) and is absolute — reads
   * do not extend it, so any entry self-expires within 10 minutes.
   */
  @Cacheable(
      value = "measureReachFrequency",
      key =
          "T(com.mw.recommendation.engine.service.MeasureApiClient).generateCacheKeyStatic(#request, #aggregate)",
      unless = "#result == null || #result.isEmpty()")
  public List<MeasureReachFrequencyResponseDTO> fetchReachAndFrequencyBySites(
      MeasureReachFrequencyRequestDTO request, boolean aggregate) {
    long startTime = System.currentTimeMillis();
    String cacheKey = generateCacheKeyStatic(request, aggregate);
    String apiUrl = properties.getMeasure().getApiUrl();
    String url = apiUrl + (apiUrl.contains("?") ? "&" : "?") + "aggregate=" + aggregate;

    log.debug(
        "[CACHE-MISS] Calling Measure API for cache key: {}, {} inventories",
        cacheKey,
        request.getInventories().size());
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String bearerToken = securityContextService.getBearerTokenOrNull();
    if (bearerToken != null && !bearerToken.isBlank()) {
      headers.setBearerAuth(bearerToken);
    }
    String payload;
    try {
      payload = objectMapper.writeValueAsString(request);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize Measure API request", e);
    }
    HttpEntity<String> entity = new HttpEntity<>(payload, headers);
    ResponseEntity<List<MeasureReachFrequencyResponseDTO>> response =
        restTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
    List<MeasureReachFrequencyResponseDTO> body = response.getBody();
    long elapsed = System.currentTimeMillis() - startTime;
    log.info(
        "[MEASURE-API] Call completed in {}ms for {} inventories, result size: {} - URL: {} - Toby",
        elapsed,
        request.getInventories().size(),
        body != null ? body.size() : 0,
        url);
    // Do not cache degraded/all-error 200 responses; returning empty lets `unless` drop them.
    if (!hasUsableData(body)) {
      return Collections.emptyList();
    }
    return body;
  }
}
