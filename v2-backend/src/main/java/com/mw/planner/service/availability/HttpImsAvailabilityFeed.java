package com.mw.planner.service.availability;

import com.mw.planner.domain.Inventory;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Production IMS availability feed client.
 *
 * <p>Active when {@code mw-planner.ims.availability-feed-url} is configured (see {@link
 * ImsAvailabilityFeedConfig}); fetches per-inventory availability from the real IMS endpoint. When
 * no endpoint is configured (local/dev), the deterministic {@link SimulatedImsAvailabilityFeed}
 * takes its place, keeping the ingestion path identical.
 */
@Slf4j
@RequiredArgsConstructor
public class HttpImsAvailabilityFeed implements ImsAvailabilityFeed {

  private final RestTemplate restTemplate;
  private final String baseUrl;
  private final String apiKey;

  @Override
  public Map<String, Object> fetchAvailability(String externalId, Inventory inventory)
      throws ImsFeedException {
    String url = baseUrl.replaceAll("/$", "") + "/availability/" + externalId;
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      if (apiKey != null && !apiKey.isBlank()) {
        headers.set("X-Api-Key", apiKey);
      }
      ResponseEntity<Map<String, Object>> response =
          restTemplate.exchange(
              url,
              HttpMethod.GET,
              new HttpEntity<>(headers),
              new ParameterizedTypeReference<>() {});
      Map<String, Object> body = response.getBody();
      if (body == null) {
        throw new ImsFeedException("IMS returned an empty availability payload for " + externalId);
      }
      return body;
    } catch (RestClientException e) {
      throw new ImsFeedException("IMS availability fetch failed for " + externalId, e);
    }
  }
}
