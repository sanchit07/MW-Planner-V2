package com.mw.planner.service.recommendation;

import com.mw.planner.config.MwPlannerProperties;
import com.mw.planner.dto.recommendation.*;
import com.mw.planner.service.SecurityContextService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationEngineApiClient {

  private final MwPlannerProperties mwPlannerProperties;
  private final RestTemplate restTemplate;
  private final SecurityContextService securityContextService;

  public RecommendationApiResponse<RecommendationStatusResponseDTO> generateRecommendation(
      String campaignId, RecommendationRequestDTO request, boolean forceRegenerate) {
    String url =
        mwPlannerProperties.getRecommendationEngine().getGenerateRecommendationUrl(campaignId);
    if (forceRegenerate) {
      url = url + "?forceRegenerate=true";
    }
    log.info("Calling recommendation engine - generateRecommendation: {}", url);
    log.info("generateRecommendation payload: {}", request);

    HttpEntity<RecommendationRequestDTO> entity = new HttpEntity<>(request, buildHeaders());
    ResponseEntity<RecommendationApiResponse<RecommendationStatusResponseDTO>> response =
        restTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
    return response.getBody();
  }

  public RecommendationApiResponse<PaginatedRecommendationResponseDTO> getRecommendationResults(
      String runId,
      int page,
      int size,
      List<String> sort,
      String search,
      RecommendationResultFilterDTO filter) {
    String baseUrl = mwPlannerProperties.getRecommendationEngine().getResultsUrl(runId);

    UriComponentsBuilder uriBuilder =
        UriComponentsBuilder.fromHttpUrl(baseUrl).queryParam("page", page).queryParam("size", size);
    if (sort != null && !sort.isEmpty()) {
      sort.forEach(s -> uriBuilder.queryParam("sort", s));
    }
    if (search != null && !search.isBlank()) {
      uriBuilder.queryParam("search", search);
    }

    java.net.URI uri = uriBuilder.build().toUri();
    log.info("Calling recommendation engine - getRecommendationResults: {}", uri);

    HttpEntity<RecommendationResultFilterDTO> entity = new HttpEntity<>(filter, buildHeaders());
    ResponseEntity<RecommendationApiResponse<PaginatedRecommendationResponseDTO>> response =
        restTemplate.exchange(uri, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
    return response.getBody();
  }

  public RecommendationApiResponse<RunSchedulesResponseDTO> getRecommendedSchedules(String runId) {
    String url = mwPlannerProperties.getRecommendationEngine().getSchedulesUrl(runId);
    log.info("Calling recommendation engine - getRecommendedSchedules: {}", url);

    HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
    ResponseEntity<RecommendationApiResponse<RunSchedulesResponseDTO>> response =
        restTemplate.exchange(url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
    return response.getBody();
  }

  /**
   * Notify the recommendation engine of select/deselect. Response data is always null; planner
   * creates its own default schedule on manual select and does not use any schedule from the
   * engine.
   */
  public RecommendationApiResponse<ScheduleRecommendationResponseDTO> manageSelectedInventories(
      String runId, String operationType, SelectedInventoriesDTO dto) {
    String baseUrl = mwPlannerProperties.getRecommendationEngine().getSelectedInventoriesUrl(runId);
    java.net.URI uri =
        UriComponentsBuilder.fromHttpUrl(baseUrl)
            .queryParam("operationType", operationType)
            .build()
            .toUri();
    log.info("Calling recommendation engine - manageSelectedInventories: {}", uri);

    HttpEntity<SelectedInventoriesDTO> entity = new HttpEntity<>(dto, buildHeaders());
    ResponseEntity<RecommendationApiResponse<ScheduleRecommendationResponseDTO>> response =
        restTemplate.exchange(uri, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
    return response.getBody();
  }

  /**
   * Call recommendation engine auto-optimize schedules. Body inventoryIds must be planner
   * externalIds (engine inventoryIds). Returns generated schedules per inventory.
   */
  public RecommendationApiResponse<RunSchedulesResponseDTO> autoOptimizeSchedules(
      String runId, SelectedInventoriesDTO dto) {
    String url = mwPlannerProperties.getRecommendationEngine().getAutoOptimizeSchedulesUrl(runId);
    log.info("Calling recommendation engine - autoOptimizeSchedules: {}", url);

    HttpEntity<SelectedInventoriesDTO> entity = new HttpEntity<>(dto, buildHeaders());
    ResponseEntity<RecommendationApiResponse<RunSchedulesResponseDTO>> response =
        restTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
    return response.getBody();
  }

  private HttpHeaders buildHeaders() {
    String token = securityContextService.getBearerToken();
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(token);
    return headers;
  }
}
