package com.mw.planner.service.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mw.planner.config.MwPlannerProperties;
import com.mw.planner.dto.InventoryAvailabilityRequestDTO;
import com.mw.planner.service.SecurityContextService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class InventoryApiClientTest {

  @Mock private MwPlannerProperties mwPlannerProperties;
  @Mock private MwPlannerProperties.Proxy proxyConfig;
  @Mock private RestTemplate restTemplate;
  @Mock private SecurityContextService securityContextService;

  @InjectMocks private InventoryApiClient inventoryApiClient;

  private static final String EXPECTED_PATH = "/api/v1/inventories/availability";

  @BeforeEach
  void setUp() {
    lenient().when(mwPlannerProperties.getProxy()).thenReturn(proxyConfig);
  }

  private InventoryAvailabilityRequestDTO request() {
    return InventoryAvailabilityRequestDTO.builder().build();
  }

  @Test
  void getAvailability_WhenDownstreamSucceeds_ReturnsResponseAndCallsResolvedUrl() {
    when(proxyConfig.getApplications())
        .thenReturn(Map.of("inventory-api", "https://inventory.example.com"));
    when(securityContextService.getBearerToken()).thenReturn("token-123");
    ResponseEntity<String> downstream = ResponseEntity.ok("{\"available\":true}");
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
        .thenReturn(downstream);

    ResponseEntity<String> result = inventoryApiClient.getAvailability(request());

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isEqualTo("{\"available\":true}");
    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(restTemplate)
        .exchange(urlCaptor.capture(), eq(HttpMethod.POST), any(), eq(String.class));
    assertThat(urlCaptor.getValue()).isEqualTo("https://inventory.example.com" + EXPECTED_PATH);
  }

  @Test
  void getAvailability_WhenBaseUrlHasTrailingSlash_TrimsSlashBeforeAppendingPath() {
    when(proxyConfig.getApplications())
        .thenReturn(Map.of("inventory-api", "https://inventory.example.com/"));
    when(securityContextService.getBearerToken()).thenReturn("token-123");
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
        .thenReturn(ResponseEntity.ok("ok"));

    inventoryApiClient.getAvailability(request());

    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(restTemplate)
        .exchange(urlCaptor.capture(), eq(HttpMethod.POST), any(), eq(String.class));
    assertThat(urlCaptor.getValue()).isEqualTo("https://inventory.example.com" + EXPECTED_PATH);
  }

  @Test
  void getAvailability_WhenDownstreamReturnsHttpError_ProxiesStatusHeadersAndBody() {
    when(proxyConfig.getApplications())
        .thenReturn(Map.of("inventory-api", "https://inventory.example.com"));
    when(securityContextService.getBearerToken()).thenReturn("token-123");

    HttpHeaders errorHeaders = new HttpHeaders();
    errorHeaders.add("X-Trace", "abc");
    HttpStatusCodeException ex = mock(HttpStatusCodeException.class);
    when(ex.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);
    when(ex.getResponseHeaders()).thenReturn(errorHeaders);
    when(ex.getResponseBodyAsString()).thenReturn("bad request body");
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
        .thenThrow(ex);

    ResponseEntity<String> result = inventoryApiClient.getAvailability(request());

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(result.getBody()).isEqualTo("bad request body");
    assertThat(result.getHeaders().getFirst("X-Trace")).isEqualTo("abc");
  }

  @Test
  void getAvailability_WhenHttpErrorHasNullHeaders_FallsBackToEmptyHeaders() {
    when(proxyConfig.getApplications())
        .thenReturn(Map.of("inventory-api", "https://inventory.example.com"));
    when(securityContextService.getBearerToken()).thenReturn("token-123");

    HttpStatusCodeException ex = mock(HttpStatusCodeException.class);
    when(ex.getStatusCode()).thenReturn(HttpStatus.NOT_FOUND);
    when(ex.getResponseHeaders()).thenReturn(null);
    when(ex.getResponseBodyAsString()).thenReturn("not found");
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
        .thenThrow(ex);

    ResponseEntity<String> result = inventoryApiClient.getAvailability(request());

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(result.getBody()).isEqualTo("not found");
    assertThat(result.getHeaders()).isEmpty();
  }

  @Test
  void getAvailability_WhenRestClientExceptionThrown_RethrowsException() {
    when(proxyConfig.getApplications())
        .thenReturn(Map.of("inventory-api", "https://inventory.example.com"));
    when(securityContextService.getBearerToken()).thenReturn("token-123");
    RestClientException ex = new RestClientException("connection reset");
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
        .thenThrow(ex);

    assertThatThrownBy(() -> inventoryApiClient.getAvailability(request()))
        .isInstanceOf(RestClientException.class)
        .hasMessageContaining("connection reset");
  }

  @Test
  void getAvailability_WhenApplicationsMapIsNull_ThrowsIllegalArgumentAndSkipsHttp() {
    when(proxyConfig.getApplications()).thenReturn(null);

    assertThatThrownBy(() -> inventoryApiClient.getAvailability(request()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown proxy application");
    verify(securityContextService, never()).getBearerToken();
  }

  @Test
  void getAvailability_WhenApplicationNotConfigured_ThrowsIllegalArgument() {
    when(proxyConfig.getApplications())
        .thenReturn(Map.of("other-api", "https://other.example.com"));

    assertThatThrownBy(() -> inventoryApiClient.getAvailability(request()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown proxy application");
  }

  @Test
  void getAvailability_WhenBaseUrlIsNull_ThrowsIllegalArgument() {
    Map<String, String> apps = new HashMap<>();
    apps.put("inventory-api", null);
    when(proxyConfig.getApplications()).thenReturn(apps);

    assertThatThrownBy(() -> inventoryApiClient.getAvailability(request()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Base URL not configured");
  }

  @Test
  void getAvailability_WhenBaseUrlIsBlank_ThrowsIllegalArgument() {
    when(proxyConfig.getApplications()).thenReturn(Map.of("inventory-api", "   "));

    assertThatThrownBy(() -> inventoryApiClient.getAvailability(request()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Base URL not configured");
  }
}
