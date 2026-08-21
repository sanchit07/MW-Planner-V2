package com.mw.planner.service.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mw.planner.config.MwPlannerProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class ProxyServiceTest {

  @Mock private RestTemplate restTemplate;
  @Mock private MwPlannerProperties mwPlannerProperties;
  @Mock private MwPlannerProperties.Proxy proxyConfig;

  @InjectMocks private ProxyService proxyService;

  @BeforeEach
  void setUp() {
    lenient().when(mwPlannerProperties.getProxy()).thenReturn(proxyConfig);
    lenient()
        .when(proxyConfig.getApplications())
        .thenReturn(
            Map.of(
                "integration-api", "https://api.example.com",
                "inventory-api", "https://inventory.example.com"));
  }

  @Test
  void forwardRequest_WithValidRequest_ReturnsDownstreamResponse() throws IOException {
    HttpServletRequest request = mockRequest("/proxy/integration-api/api/v1/bookings", "GET", "");

    ResponseEntity<String> downstreamResponse =
        ResponseEntity.status(HttpStatus.OK).body("{\"id\":\"123\"}");
    when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
        .thenReturn(downstreamResponse);

    ResponseEntity<String> result = proxyService.forwardRequest(request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isEqualTo("{\"id\":\"123\"}");
    verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class));
  }

  @Test
  void forwardRequest_WhenDownstreamReturnsError_ProxiesErrorResponse() throws IOException {
    HttpServletRequest request = mockRequest("/proxy/integration-api/api/v1/bookings", "GET", "");

    HttpStatusCodeException ex = mock(HttpStatusCodeException.class);
    when(ex.getStatusCode()).thenReturn(HttpStatus.NOT_FOUND);
    when(ex.getResponseBodyAsString()).thenReturn("Not found");
    when(ex.getResponseHeaders()).thenReturn(new org.springframework.http.HttpHeaders());
    when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
        .thenThrow(ex);

    ResponseEntity<String> result = proxyService.forwardRequest(request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(result.getBody()).isEqualTo("Not found");
  }

  @Test
  void forwardRequest_WhenInvalidProxyUrl_ThrowsIllegalArgumentException() throws IOException {
    HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/api/other");
    when(request.getContextPath()).thenReturn("");

    assertThatThrownBy(() -> proxyService.forwardRequest(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid proxy URL");
  }

  @Test
  void forwardRequest_WhenApplicationNotConfigured_ThrowsIllegalArgumentException()
      throws IOException {
    when(proxyConfig.getApplications()).thenReturn(Collections.emptyMap());
    HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/proxy/unknown-app/path");
    when(request.getContextPath()).thenReturn("");

    assertThatThrownBy(() -> proxyService.forwardRequest(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown proxy application");
  }

  @Disabled("Disabled until overrideAuthorizationHeaderIfNeeded is re-enabled in ProxyService")
  @Test
  void forwardRequest_WhenInventoryApiConfigured_OverridesAuthorizationHeader() throws IOException {
    HttpServletRequest request =
        mockRequest(
            "/proxy/inventory-api/api/v1/bookings",
            "GET",
            "",
            Map.of(HttpHeaders.AUTHORIZATION, "Bearer incoming-token"));
    when(proxyConfig.getInventoryApiKey()).thenReturn("test-inventory-api-key");

    ResponseEntity<String> downstreamResponse = ResponseEntity.status(HttpStatus.OK).body("ok");
    when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
        .thenReturn(downstreamResponse);

    proxyService.forwardRequest(request);

    verify(restTemplate)
        .exchange(
            anyString(),
            eq(HttpMethod.GET),
            argThat(
                requestEntity ->
                    "ApiKey test-inventory-api-key"
                        .equals(requestEntity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))),
            eq(String.class));
  }

  private static HttpServletRequest mockRequest(String requestUri, String method, String body)
      throws IOException {
    return mockRequest(requestUri, method, body, Collections.emptyMap());
  }

  private static HttpServletRequest mockRequest(
      String requestUri, String method, String body, Map<String, String> headers)
      throws IOException {
    HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn(requestUri);
    when(request.getContextPath()).thenReturn("");
    when(request.getMethod()).thenReturn(method);
    when(request.getQueryString()).thenReturn(null);
    when(request.getHeaderNames()).thenReturn(Collections.enumeration(headers.keySet()));
    headers.forEach((name, value) -> when(request.getHeader(name)).thenReturn(value));
    BufferedReader reader = new BufferedReader(new StringReader(body));
    when(request.getReader()).thenReturn(reader);
    return request;
  }
}
