package com.mw.planner.service.proxy;

import com.mw.planner.config.MwPlannerProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class ProxyService {

  private static final String PROXY_PREFIX = "/proxy/";
  private static final String INVENTORY_API_APPLICATION = "inventory-api";
  private static final String API_KEY_PREFIX = "ApiKey ";

  private final RestTemplate restTemplate;
  private final MwPlannerProperties mwPlannerProperties;

  /**
   * Identifies the application and path from the proxy URL, builds the target URL and HTTP request
   * details from the servlet request, then forwards to the downstream application.
   *
   * @param request the incoming proxy request (e.g. GET /proxy/integration-api/api/v1/bookings/123)
   * @return response from downstream application
   * @throws IllegalArgumentException if application is not configured or URL is invalid
   */
  public ResponseEntity<String> forwardRequest(HttpServletRequest request) throws IOException {
    String applicationName = identifyApplication(request);
    String path = extractPath(request);
    String baseUrl = resolveBaseUrl(applicationName);
    String targetUrl = buildTargetUrl(baseUrl, path, request.getQueryString());

    HttpMethod method = HttpMethod.valueOf(request.getMethod());
    HttpHeaders headers = copyHeaders(request);
    // Temp commit out the header override to unblock other work, will re-enable after testing with
    // inventory-api
    overrideAuthorizationHeaderIfNeeded(applicationName, headers);
    String body = readBody(request);

    HttpEntity<String> requestEntity = new HttpEntity<>(body != null ? body : "", headers);
    try {
      ResponseEntity<String> backendResponse =
          restTemplate.exchange(targetUrl, method, requestEntity, String.class);

      // Copy headers but strip hop-by-hop headers
      HttpHeaders responseHeaders = new HttpHeaders();
      backendResponse
          .getHeaders()
          .forEach(
              (key, values) -> {
                if (!isHopByHopHeader(key)) {
                  responseHeaders.put(key, values);
                }
              });

      return ResponseEntity.status(backendResponse.getStatusCode())
          .headers(responseHeaders)
          .body(backendResponse.getBody());
    } catch (HttpStatusCodeException e) {
      String responseBody = e.getResponseBodyAsString();
      HttpHeaders responseHeaders =
          e.getResponseHeaders() != null ? e.getResponseHeaders() : new HttpHeaders();
      return ResponseEntity.status(e.getStatusCode()).headers(responseHeaders).body(responseBody);
    }
  }

  /** Extracts application name from proxy URL: /proxy/{applicationName}/... */
  private String identifyApplication(HttpServletRequest request) {
    String uri = getRequestUriWithoutContext(request);
    if (!uri.startsWith(PROXY_PREFIX)) {
      throw new IllegalArgumentException("Invalid proxy URL: must start with " + PROXY_PREFIX);
    }
    String rest = uri.substring(PROXY_PREFIX.length());
    int firstSlash = rest.indexOf('/');
    String applicationName = firstSlash < 0 ? rest : rest.substring(0, firstSlash);
    if (applicationName.isEmpty()) {
      throw new IllegalArgumentException("Invalid proxy URL: application name is missing");
    }
    return applicationName;
  }

  /** Extracts path after application name: /proxy/{applicationName}{path} -> path */
  private String extractPath(HttpServletRequest request) {
    String uri = getRequestUriWithoutContext(request);
    String rest = uri.substring(PROXY_PREFIX.length());
    int firstSlash = rest.indexOf('/');
    return firstSlash < 0 ? "/" : rest.substring(firstSlash);
  }

  private String getRequestUriWithoutContext(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
      uri = uri.substring(contextPath.length());
    }
    return uri;
  }

  private HttpHeaders copyHeaders(HttpServletRequest request) {
    HttpHeaders headers = new HttpHeaders();
    Enumeration<String> headerNames = request.getHeaderNames();
    if (headerNames != null) {
      while (headerNames.hasMoreElements()) {
        String name = headerNames.nextElement();
        // Skip Origin header to prevent CORS issues when proxying
        if ("Origin".equalsIgnoreCase(name)) {
          continue;
        }
        String value = request.getHeader(name);
        if (value != null) {
          headers.set(name, value);
        }
      }
    }
    return headers;
  }

  private String readBody(HttpServletRequest request) throws IOException {
    return request.getReader().lines().collect(Collectors.joining("\n"));
  }

  private String resolveBaseUrl(String applicationName) {
    Map<String, String> applications = mwPlannerProperties.getProxy().getApplications();
    if (applications == null || !applications.containsKey(applicationName)) {
      throw new IllegalArgumentException(
          "Unknown proxy application: "
              + applicationName
              + ". Configure mw-planner.proxy.applications.");
    }
    String baseUrl = applications.get(applicationName);
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException(
          "Base URL not configured for proxy application: " + applicationName);
    }
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  private void overrideAuthorizationHeaderIfNeeded(String applicationName, HttpHeaders headers) {
    if (!INVENTORY_API_APPLICATION.equals(applicationName)) {
      return;
    }

    String inventoryApiKey = mwPlannerProperties.getProxy().getInventoryApiKey();
    if (inventoryApiKey == null || inventoryApiKey.isBlank()) {
      return;
    }

    headers.set(HttpHeaders.AUTHORIZATION, API_KEY_PREFIX + inventoryApiKey.trim());
  }

  private String buildTargetUrl(String baseUrl, String path, String queryString) {
    String normalizedPath = path != null && !path.isEmpty() ? path : "/";
    if (!normalizedPath.startsWith("/")) {
      normalizedPath = "/" + normalizedPath;
    }
    String url = baseUrl + normalizedPath;
    if (queryString != null && !queryString.isBlank()) {
      url = url + "?" + queryString;
    }
    return url;
  }

  private boolean isHopByHopHeader(String headerName) {
    return Set.of(
            "transfer-encoding",
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "upgrade")
        .contains(headerName.toLowerCase());
  }
}
