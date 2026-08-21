package com.mw.planner.service.inventory;

import com.mw.planner.config.MwPlannerProperties;
import com.mw.planner.dto.InventoryAvailabilityRequestDTO;
import com.mw.planner.service.SecurityContextService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Inventory API client. Handles HTTP communication with inventory-api endpoints.
 *
 * <p>This component is responsible only for API communication, not caching or business logic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryApiClient {

  private static final String APPLICATION_NAME = "inventory-api";
  private static final String INVENTORY_AVAILABILITY_PATH = "/api/v1/inventories/availability";

  private final MwPlannerProperties mwPlannerProperties;
  private final RestTemplate restTemplate;
  private final SecurityContextService securityContextService;

  public ResponseEntity<String> getAvailability(InventoryAvailabilityRequestDTO request) {
    String url = resolveBaseUrl() + INVENTORY_AVAILABILITY_PATH;
    String token = securityContextService.getBearerToken();

    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(token);

      HttpEntity<InventoryAvailabilityRequestDTO> entity = new HttpEntity<>(request, headers);
      return restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    } catch (HttpStatusCodeException ex) {
      HttpHeaders responseHeaders =
          ex.getResponseHeaders() != null ? ex.getResponseHeaders() : new HttpHeaders();
      return ResponseEntity.status(ex.getStatusCode())
          .headers(responseHeaders)
          .body(ex.getResponseBodyAsString());
    } catch (RestClientException ex) {
      log.error("Error calling inventory availability API: {}", url, ex);
      throw ex;
    }
  }

  private String resolveBaseUrl() {
    Map<String, String> applications = mwPlannerProperties.getProxy().getApplications();
    if (applications == null || !applications.containsKey(APPLICATION_NAME)) {
      throw new IllegalArgumentException(
          "Unknown proxy application: "
              + APPLICATION_NAME
              + ". Configure mw-planner.proxy.applications.");
    }

    String baseUrl = applications.get(APPLICATION_NAME);
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("Base URL not configured for: " + APPLICATION_NAME);
    }

    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }
}
