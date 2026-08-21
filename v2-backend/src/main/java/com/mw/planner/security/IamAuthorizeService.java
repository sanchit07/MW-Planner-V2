package com.mw.planner.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.config.MwPlannerProperties;
import com.mw.planner.dto.LogoutRequest;
import com.mw.planner.dto.OAuthTokenResponse;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.auth.AuthenticationException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class IamAuthorizeService {

  private final MwPlannerProperties mwPlannerProperties;
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final RedisTemplate<String, String> redisTemplate;

  private static final String PKCE_REDIS_KEY_PREFIX = "oauth:pkce:";

  /**
   * PKCE verifiers must survive the round trip to IAM's login page and back, which is a separate
   * HTTP request (the browser's redirect), so the in-memory local variable from {@link
   * #buildAuthorizationUrlWithPkce} cannot carry it — it is stashed here, keyed by the one-time
   * {@code state} value that IAM echoes back unchanged in the callback, and consumed exactly once
   * in {@link #exchangeCodeForToken}.
   */
  private static final Duration PKCE_VERIFIER_TTL = Duration.ofMinutes(10);

  /**
   * Builds the OAuth authorization URL with PKCE parameters and stashes the code_verifier in Redis
   * (keyed by state) so {@link #exchangeCodeForToken} can retrieve and send it at the token step.
   *
   * @param customRedirectUri Optional custom redirect URI. If null, uses the configured default.
   * @return AuthorizationUrlResult containing URL, state, and code_verifier
   */
  public AuthorizationUrlResult buildAuthorizationUrlWithPkce(String customRedirectUri) {
    MwPlannerProperties.IAM iamConfig = mwPlannerProperties.getIam();
    String authorizeUrl = iamConfig.getFullAuthorizeUrl();

    // Validate base URL is absolute
    if (!StringUtils.hasText(authorizeUrl)) {
      throw new AuthenticationException(
          ErrorCode.INTERNAL_SERVER_ERROR, "OAuth base URL is not configured");
    }

    // Generate PKCE values
    String state = UUID.randomUUID().toString();
    String codeVerifier = generateCodeVerifier();
    String codeChallenge = generateCodeChallenge(codeVerifier);
    redisTemplate.opsForValue().set(PKCE_REDIS_KEY_PREFIX + state, codeVerifier, PKCE_VERIFIER_TTL);

    // Build query parameters
    Map<String, String> queryParams =
        buildQueryParameters(iamConfig, state, codeChallenge, customRedirectUri);

    // Build complete URL with query parameters
    String fullUrl = buildUrlWithQueryParams(authorizeUrl, queryParams);
    log.debug("Built authorization URL: {}", fullUrl);

    return new AuthorizationUrlResult(fullUrl, state, codeVerifier);
  }

  public static String generateCodeVerifier() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static String generateCodeChallenge(String verifier) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(verifier.getBytes());
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (Exception e) {
      throw new RuntimeException("Error generating code challenge", e);
    }
  }

  /**
   * Builds query parameters map for OAuth authorization request
   *
   * @param iamConfig IAM configuration parameters
   * @param state State parameter for CSRF protection
   * @param codeChallenge PKCE code challenge
   * @param customRedirectUri Optional custom redirect URI. If null, uses the configured default.
   * @return Map of query parameter names to values
   */
  private Map<String, String> buildQueryParameters(
      MwPlannerProperties.IAM iamConfig,
      String state,
      String codeChallenge,
      String customRedirectUri) {
    Map<String, String> queryParams = new LinkedHashMap<>();

    // Use custom redirect_uri if provided, otherwise use configured default
    String redirectUri =
        StringUtils.hasText(customRedirectUri) ? customRedirectUri : iamConfig.getRedirectUri();

    queryParams.put("redirect_uri", redirectUri);
    queryParams.put("client_id", iamConfig.getClientId());
    queryParams.put("response_type", "code");
    queryParams.put("scope", iamConfig.getScope());
    queryParams.put("state", state);
    queryParams.put("code_challenge", codeChallenge);
    queryParams.put("code_challenge_method", iamConfig.getCodeChallengeMethod());
    return queryParams;
  }

  /**
   * Builds a complete URL with query parameters, ensuring it's an absolute URL
   *
   * @param baseUrl The base URL (must be absolute)
   * @param queryParams Map of query parameter names to values
   * @return Complete URL with query string
   */
  private String buildUrlWithQueryParams(String baseUrl, Map<String, String> queryParams) {
    URI baseUri = validateBaseUrl(baseUrl);

    UriComponentsBuilder builder = UriComponentsBuilder.fromUri(baseUri);

    queryParams.forEach(
        (key, value) -> {
          if (StringUtils.hasText(value)) {
            builder.queryParam(key, value);
          }
        });

    String finalUrl = builder.build(true).toUriString();
    log.debug("Constructed authorization URL: {}", finalUrl);

    return finalUrl;
  }

  private URI validateBaseUrl(String baseUrl) {
    try {
      URI uri = URI.create(baseUrl);
      if (uri.getScheme() == null
          || (!"http".equalsIgnoreCase(uri.getScheme())
              && !"https".equalsIgnoreCase(uri.getScheme()))) {
        throw invalidBaseUrlException(baseUrl);
      }

      return uri;
    } catch (IllegalArgumentException ex) {
      log.error("Invalid OAuth base URL: {}", baseUrl, ex);
      throw invalidBaseUrlException(baseUrl);
    }
  }

  private AuthenticationException invalidBaseUrlException(String baseUrl) {
    return new AuthenticationException(
        ErrorCode.INTERNAL_SERVER_ERROR,
        "OAuth base URL must be a valid http(s) URL. Current value: " + baseUrl);
  }

  /**
   * Exchanges an OAuth2 authorization code for an access token using Authorization Code Grant with
   * PKCE. Flow: 1. Resolve IAM configuration and redirect URI 2. Build form-urlencoded token
   * request 3. Call IAM token endpoint 4. Convert response into OAuthTokenResponse 5. Enrich
   * missing fields (state, scope, token_type)
   *
   * @param authorizationCode Authorization code received from IAM
   * @param redirectUri Optional redirect URI (falls back to configured value)
   * @param state CSRF protection state value
   * @return OAuthTokenResponse containing access and refresh tokens
   * @throws AuthenticationException when token exchange fails
   */
  public OAuthTokenResponse exchangeCodeForToken(
      String authorizationCode, String redirectUri, String state) {

    MwPlannerProperties.IAM iamConfig = mwPlannerProperties.getIam();
    String tokenUrl = iamConfig.getFullTokenUrl();

    // Resolve redirect URI: prefer request value, otherwise use configured default
    String finalRedirectUri = resolveRedirectUri(redirectUri, iamConfig);

    // Consume (one-time) the code_verifier stashed against this state at /authorize time. Its
    // absence means either state is forged/unknown or the verifier already expired/was used —
    // either way the exchange must not proceed without it.
    String pkceKey = PKCE_REDIS_KEY_PREFIX + state;
    String codeVerifier = redisTemplate.opsForValue().get(pkceKey);
    if (!StringUtils.hasText(codeVerifier)) {
      throw new AuthenticationException(
          ErrorCode.INVALID_CREDENTIALS,
          "Missing or expired PKCE code_verifier for this authorization attempt");
    }
    redisTemplate.delete(pkceKey);

    try {
      // Build HTTP request entity for token exchange
      HttpEntity<MultiValueMap<String, String>> requestEntity =
          buildTokenRequestEntity(authorizationCode, finalRedirectUri, codeVerifier, iamConfig);

      // Execute POST request to IAM token endpoint
      ResponseEntity<Object> response =
          restTemplate.exchange(tokenUrl, HttpMethod.POST, requestEntity, Object.class);

      // Validate HTTP response status
      if (!response.getStatusCode().is2xxSuccessful()) {
        log.error("Token exchange failed with status: {}", response.getStatusCode());
        throw tokenExchangeFailure();
      }

      // Convert IAM response into domain object
      OAuthTokenResponse tokenResponse = convertToOAuthTokenResponse(response.getBody());

      // Enrich missing or optional fields
      enrichTokenResponse(tokenResponse, state, iamConfig);

      return tokenResponse;

    } catch (HttpClientErrorException ex) {
      // IAM returned a 4xx error (invalid code, redirect mismatch, etc.)
      String errorMessage = extractErrorMessage(ex);
      log.error("HTTP error during token exchange: {} - {}", ex.getStatusCode(), errorMessage, ex);

      throw new AuthenticationException(
          ErrorCode.INVALID_CREDENTIALS,
          StringUtils.hasText(errorMessage)
              ? errorMessage
              : "Failed to exchange authorization code for token");

    } catch (RestClientException ex) {
      // Network or client-side error
      log.error("REST error during token exchange", ex);
      throw tokenExchangeFailure();

    } catch (Exception ex) {
      // Defensive fallback for unexpected runtime issues
      log.error("Unexpected error during token exchange", ex);
      throw tokenExchangeFailure();
    }
  }

  /**
   * Refreshes an OAuth2 access token using the refresh_token grant type. Flow: 1. Load IAM
   * configuration 2. Build refresh token request (form-urlencoded) 3. Call IAM token endpoint 4.
   * Validate response and access token
   *
   * @param refreshToken Refresh token issued by IAM
   * @return OAuthTokenResponse containing new access token and related metadata
   * @throws AuthenticationException if refresh token is invalid or expired
   */
  public OAuthTokenResponse refreshToken(String refreshToken) {

    MwPlannerProperties.IAM iamConfig = mwPlannerProperties.getIam();
    String tokenUrl = iamConfig.getFullTokenUrl();

    try {
      // Build HTTP request entity for refresh token grant
      HttpEntity<MultiValueMap<String, String>> requestEntity =
          buildRefreshTokenRequestEntity(refreshToken, iamConfig);

      // Execute refresh token request
      ResponseEntity<OAuthTokenResponse> response =
          restTemplate.exchange(tokenUrl, HttpMethod.POST, requestEntity, OAuthTokenResponse.class);

      // Validate successful HTTP response
      if (!response.getStatusCode().is2xxSuccessful()) {
        log.error("Token refresh failed with status: {}", response.getStatusCode());
        throw refreshTokenInvalid();
      }

      // Validate response body and access token
      OAuthTokenResponse tokenResponse = response.getBody();
      validateRefreshTokenResponse(tokenResponse);

      return tokenResponse;

    } catch (HttpClientErrorException ex) {
      // IAM returned a 4xx error (expired, revoked, or invalid refresh token)
      log.error("HTTP error during token refresh: {}", ex.getResponseBodyAsString(), ex);
      throw refreshTokenInvalid();

    } catch (RestClientException ex) {
      // Network, timeout, or client-side failure
      log.error("REST error during token refresh", ex);
      throw refreshTokenInvalid();

    } catch (AuthenticationException ex) {
      // Explicitly rethrow domain-level authentication exceptions
      throw ex;

    } catch (Exception ex) {
      // Defensive fallback for unexpected runtime issues
      log.error("Unexpected error during token refresh", ex);
      throw refreshTokenInvalid();
    }
  }

  /**
   * Revokes a refresh token by calling the IAM logout API. This method handles failures gracefully
   * and logs errors only, as logout should succeed even if the refresh token is already revoked.
   *
   * @param logoutRequest The refresh token to be revoked
   */
  public void logout(LogoutRequest logoutRequest) {
    try {
      MwPlannerProperties.IAM iamConfig = mwPlannerProperties.getIam();
      String logoutUrl = iamConfig.getFullLogoutUrl();

      // Prepare request headers
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      HttpEntity<LogoutRequest> entity = new HttpEntity<>(logoutRequest, headers);

      // Make logout request
      restTemplate.exchange(logoutUrl, HttpMethod.POST, entity, Void.class);

      log.debug("Successfully revoked refresh token");
    } catch (HttpClientErrorException ex) {
      // Log but don't throw - logout should succeed even if token is already revoked
      log.warn(
          "HTTP error during logout (token may already be revoked): {}",
          ex.getResponseBodyAsString());
    } catch (RestClientException ex) {
      // Log but don't throw - logout should succeed even if IAM call fails
      log.warn("Error calling IAM logout API: {}", ex.getMessage());
    } catch (Exception e) {
      // Log but don't throw - logout should succeed even if unexpected error occurs
      log.warn("Unexpected error during logout: {}", e.getMessage());
    }
  }

  /**
   * Resolves the redirect URI to be used for token exchange. Prefers the provided value, otherwise
   * falls back to IAM configuration.
   *
   * @param redirectUri Optional redirect URI from request
   * @param iamConfig IAM configuration containing default redirect URI
   * @return Resolved redirect URI
   */
  private String resolveRedirectUri(String redirectUri, MwPlannerProperties.IAM iamConfig) {
    return StringUtils.hasText(redirectUri) ? redirectUri : iamConfig.getRedirectUri();
  }

  /**
   * Builds the HTTP entity for OAuth2 token exchange request using
   * application/x-www-form-urlencoded content type.
   *
   * @param authorizationCode Authorization code from OAuth callback
   * @param redirectUri Redirect URI used in authorization request
   * @param codeVerifier PKCE code_verifier matching the code_challenge sent at /authorize
   * @param iamConfig IAM configuration containing client credentials
   * @return HTTP entity with form-urlencoded body and headers
   */
  private HttpEntity<MultiValueMap<String, String>> buildTokenRequestEntity(
      String authorizationCode,
      String redirectUri,
      String codeVerifier,
      MwPlannerProperties.IAM iamConfig) {

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "authorization_code");
    body.add("code", authorizationCode);
    body.add("client_id", iamConfig.getClientId());
    body.add("client_secret", iamConfig.getClientSecret());
    body.add("redirect_uri", redirectUri);
    body.add("code_verifier", codeVerifier);

    return new HttpEntity<>(body, headers);
  }

  /**
   * Enriches the OAuthTokenResponse with missing optional values such as state, token type, and
   * scope.
   *
   * @param tokenResponse Token response to enrich
   * @param state State parameter from authorization request
   * @param iamConfig IAM configuration for default scope
   */
  private void enrichTokenResponse(
      OAuthTokenResponse tokenResponse, String state, MwPlannerProperties.IAM iamConfig) {

    if (tokenResponse == null) {
      return;
    }

    if (StringUtils.hasText(state)) {
      tokenResponse.setState(state);
    }

    if (!StringUtils.hasText(tokenResponse.getTokenType())) {
      tokenResponse.setTokenType("Bearer");
    }

    if (!StringUtils.hasText(tokenResponse.getScope())) {
      tokenResponse.setScope(iamConfig.getScope());
    }
  }

  /**
   * Converts the IAM token endpoint response into OAuthTokenResponse. Supports: - Direct
   * OAuthTokenResponse - Map-based responses (LinkedHashMap) - Generic object conversion via
   * ObjectMapper
   *
   * @param responseBody Raw response body from IAM token endpoint
   * @return Converted OAuthTokenResponse
   * @throws AuthenticationException if conversion fails
   */
  private OAuthTokenResponse convertToOAuthTokenResponse(Object responseBody) {
    if (responseBody == null) {
      return null;
    }

    try {
      // Already converted
      if (responseBody instanceof OAuthTokenResponse) {
        return (OAuthTokenResponse) responseBody;
      }

      // Map-based conversion (typical RestTemplate response)
      if (responseBody instanceof Map<?, ?> responseMap) {
        return mapToOAuthTokenResponse(responseMap);
      }

      // Fallback conversion
      return objectMapper.convertValue(responseBody, OAuthTokenResponse.class);

    } catch (Exception ex) {
      log.error("Error converting token response", ex);
      throw new AuthenticationException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Failed to parse token response from server");
    }
  }

  /**
   * Converts a Map-based OAuth token response into OAuthTokenResponse.
   *
   * @param responseMap Map containing OAuth token response fields
   * @return OAuthTokenResponse with mapped values
   */
  private OAuthTokenResponse mapToOAuthTokenResponse(Map<?, ?> responseMap) {
    OAuthTokenResponse tokenResponse = new OAuthTokenResponse();

    tokenResponse.setAccessToken((String) responseMap.get("access_token"));
    tokenResponse.setRefreshToken((String) responseMap.get("refresh_token"));
    tokenResponse.setTokenType((String) responseMap.get("token_type"));
    tokenResponse.setState((String) responseMap.get("state"));

    // Handle expires_in (String or Number)
    Object expiresIn = responseMap.get("expires_in");
    if (expiresIn instanceof Number number) {
      tokenResponse.setExpiresIn(number.longValue());
    } else if (expiresIn instanceof String value) {
      tokenResponse.setExpiresIn(Long.parseLong(value));
    }

    // Handle scope (String, List, or Array)
    Object scope = responseMap.get("scope");
    tokenResponse.setScope(normalizeScope(scope));

    return tokenResponse;
  }

  /**
   * Normalizes OAuth scope into a space-delimited string. Handles String, List, and Array formats.
   *
   * @param scope Scope value in various formats
   * @return Normalized space-delimited scope string
   */
  private String normalizeScope(Object scope) {
    if (scope == null) {
      return null;
    }

    if (scope instanceof String) {
      return (String) scope;
    }

    if (scope instanceof List<?> list) {
      return list.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(" "));
    }

    if (scope.getClass().isArray()) {
      return java.util.Arrays.stream((Object[]) scope)
          .map(Object::toString)
          .collect(java.util.stream.Collectors.joining(" "));
    }

    return scope.toString();
  }

  /**
   * Extracts a meaningful OAuth error message from an HTTP 4xx response. Tries to parse standard
   * OAuth fields: - error - error_description Falls back to raw response or HTTP status text.
   *
   * @param ex HTTP client error exception from IAM
   * @return Extracted error message
   */
  private String extractErrorMessage(HttpClientErrorException ex) {
    try {
      String responseBody = ex.getResponseBodyAsString();

      if (StringUtils.hasText(responseBody)) {
        Map<String, Object> errorMap = objectMapper.readValue(responseBody, Map.class);

        String errorDescription = (String) errorMap.get("error_description");
        if (StringUtils.hasText(errorDescription)) {
          return errorDescription;
        }

        String error = (String) errorMap.get("error");
        if (StringUtils.hasText(error)) {
          return "OAuth error: " + error;
        }

        return responseBody;
      }
    } catch (Exception e) {
      log.warn("Failed to parse IAM error response", e);
    }

    return ex.getStatusCode() + " " + ex.getStatusText();
  }

  /**
   * Creates a standard AuthenticationException for token exchange failures.
   *
   * @return AuthenticationException with appropriate error code and message
   */
  private AuthenticationException tokenExchangeFailure() {
    return new AuthenticationException(
        ErrorCode.INTERNAL_SERVER_ERROR, "Failed to exchange authorization code for token");
  }

  /**
   * Builds the HTTP entity for OAuth2 refresh token grant request. Uses
   * application/x-www-form-urlencoded content type as per OAuth2 spec.
   *
   * @param refreshToken Refresh token to exchange for new access token
   * @param iamConfig IAM configuration containing client credentials
   * @return HTTP entity with form-urlencoded body and headers
   */
  private HttpEntity<MultiValueMap<String, String>> buildRefreshTokenRequestEntity(
      String refreshToken, MwPlannerProperties.IAM iamConfig) {

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "refresh_token");
    body.add("refresh_token", refreshToken);
    body.add("client_id", iamConfig.getClientId());
    body.add("client_secret", iamConfig.getClientSecret());

    return new HttpEntity<>(body, headers);
  }

  /**
   * Validates the token refresh response. Ensures: - Response body is not null - Access token is
   * present
   *
   * @param tokenResponse Token response to validate
   * @throws AuthenticationException if validation fails
   */
  private void validateRefreshTokenResponse(OAuthTokenResponse tokenResponse) {
    if (tokenResponse == null || !StringUtils.hasText(tokenResponse.getAccessToken())) {
      log.error("Token refresh returned invalid or empty access token");
      throw refreshTokenInvalid();
    }
  }

  /**
   * Creates a standard AuthenticationException for invalid or expired refresh tokens.
   *
   * @return AuthenticationException with appropriate error code and message
   */
  private AuthenticationException refreshTokenInvalid() {
    return new AuthenticationException(
        ErrorCode.INVALID_CREDENTIALS, "Invalid or expired refresh token. Please login again.");
  }

  /** Result class for OAuth authorization URL building */
  public record AuthorizationUrlResult(
      String authorizationUrl, String state, String codeVerifier) {}
}
