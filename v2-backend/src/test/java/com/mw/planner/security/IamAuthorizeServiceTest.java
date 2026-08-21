package com.mw.planner.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.config.MwPlannerProperties;
import com.mw.planner.dto.OAuthTokenResponse;
import com.mw.planner.exception.auth.AuthenticationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class IamAuthorizeServiceTest {

  @Mock private MwPlannerProperties mwPlannerProperties;
  @Mock private RestTemplate restTemplate;
  @Mock private ObjectMapper objectMapper;
  @Mock private RedisTemplate<String, String> redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  @InjectMocks private IamAuthorizeService iamAuthorizeService;

  private MwPlannerProperties.IAM iamConfig;

  @BeforeEach
  void setUp() {
    iamConfig = new MwPlannerProperties.IAM();
    iamConfig.setServiceUrl("https://iam.example.com");
    iamConfig.setClientId("client-id");
    iamConfig.setRedirectUri("https://app.example.com/callback");
    iamConfig.setScope("openid");
    iamConfig.setCodeChallengeMethod("S256");
    MwPlannerProperties.IAM.Endpoints endpoints = new MwPlannerProperties.IAM.Endpoints();
    endpoints.setAuthorize("/oauth/authorize");
    endpoints.setToken("/oauth/token");
    endpoints.setLogout("/oauth/logout");
    iamConfig.setEndpoints(endpoints);
    lenient().when(mwPlannerProperties.getIam()).thenReturn(iamConfig);
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  /**
   * Stubs a stored PKCE code_verifier for the given state, as buildAuthorizationUrlWithPkce would.
   */
  private void givenStoredCodeVerifier(String state, String codeVerifier) {
    when(valueOperations.get("oauth:pkce:" + state)).thenReturn(codeVerifier);
  }

  @Test
  void buildAuthorizationUrlWithPkce_WithValidConfig_ReturnsUrlWithStateAndCodeVerifier() {
    var result = iamAuthorizeService.buildAuthorizationUrlWithPkce(null);

    assertThat(result.authorizationUrl()).startsWith("https://iam.example.com/oauth/authorize");
    assertThat(result.authorizationUrl()).contains("response_type=code");
    assertThat(result.authorizationUrl()).contains("client_id=client-id");
    assertThat(result.authorizationUrl()).contains("state=");
    assertThat(result.authorizationUrl()).contains("code_challenge=");
    assertThat(result.state()).isNotBlank();
    assertThat(result.codeVerifier()).isNotBlank();
  }

  @Test
  void buildAuthorizationUrlWithPkce_WhenAuthorizeUrlEmpty_ThrowsAuthenticationException() {
    iamConfig.setServiceUrl("");
    when(mwPlannerProperties.getIam()).thenReturn(iamConfig);
    MwPlannerProperties.IAM.Endpoints endpoints = new MwPlannerProperties.IAM.Endpoints();
    endpoints.setAuthorize("");
    iamConfig.setEndpoints(endpoints);

    assertThatThrownBy(() -> iamAuthorizeService.buildAuthorizationUrlWithPkce(null))
        .isInstanceOf(AuthenticationException.class)
        .hasMessageContaining("OAuth base URL is not configured");
  }

  @Test
  void generateCodeVerifier_ReturnsBase64UrlEncodedString() {
    String verifier = IamAuthorizeService.generateCodeVerifier();

    assertThat(verifier).isNotBlank();
    assertThat(verifier).matches("^[A-Za-z0-9_-]+$");
  }

  @Test
  void generateCodeChallenge_WithVerifier_ReturnsConsistentChallenge() {
    String verifier = "test-verifier-string";
    String challenge1 = IamAuthorizeService.generateCodeChallenge(verifier);
    String challenge2 = IamAuthorizeService.generateCodeChallenge(verifier);

    assertThat(challenge1).isNotBlank();
    assertThat(challenge1).isEqualTo(challenge2);
  }

  @Test
  void exchangeCodeForToken_WithMapResponse_ReturnsOAuthTokenResponse() {
    Map<String, Object> tokenMap = new LinkedHashMap<>();
    tokenMap.put("access_token", "access-123");
    tokenMap.put("refresh_token", "refresh-456");
    tokenMap.put("expires_in", 3600L);
    tokenMap.put("token_type", "Bearer");
    ResponseEntity<Object> responseEntity =
        ResponseEntity.status(HttpStatus.OK).body((Object) tokenMap);

    when(mwPlannerProperties.getIam()).thenReturn(iamConfig);
    givenStoredCodeVerifier("state", "stored-verifier");
    when(restTemplate.exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.POST),
            any(org.springframework.http.HttpEntity.class),
            eq(Object.class)))
        .thenReturn(responseEntity);

    OAuthTokenResponse result = iamAuthorizeService.exchangeCodeForToken("code", null, "state");

    assertThat(result).isNotNull();
    assertThat(result.getAccessToken()).isEqualTo("access-123");
    assertThat(result.getRefreshToken()).isEqualTo("refresh-456");
    assertThat(result.getState()).isEqualTo("state");
    // The PKCE verifier is single-use: consumed (deleted) once redeemed.
    verify(redisTemplate).delete("oauth:pkce:state");
  }

  @Test
  void exchangeCodeForToken_WhenNoStoredCodeVerifier_ThrowsAuthenticationException() {
    assertThatThrownBy(
            () -> iamAuthorizeService.exchangeCodeForToken("code", null, "unknown-state"))
        .isInstanceOf(AuthenticationException.class)
        .hasMessageContaining("code_verifier");
  }

  @Test
  void exchangeCodeForToken_WhenHttpClientError_ThrowsAuthenticationException() {
    when(mwPlannerProperties.getIam()).thenReturn(iamConfig);
    givenStoredCodeVerifier("state", "stored-verifier");
    when(restTemplate.exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.POST),
            any(org.springframework.http.HttpEntity.class),
            eq(Object.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "invalid_grant"));

    assertThatThrownBy(() -> iamAuthorizeService.exchangeCodeForToken("bad-code", null, "state"))
        .isInstanceOf(AuthenticationException.class);
  }

  @Test
  void refreshToken_WithValidToken_ReturnsOAuthTokenResponse() {
    OAuthTokenResponse tokenResponse = new OAuthTokenResponse();
    tokenResponse.setAccessToken("new-access");
    tokenResponse.setRefreshToken("new-refresh");
    ResponseEntity<OAuthTokenResponse> responseEntity =
        ResponseEntity.status(HttpStatus.OK).body(tokenResponse);

    when(mwPlannerProperties.getIam()).thenReturn(iamConfig);
    when(restTemplate.exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.POST),
            any(org.springframework.http.HttpEntity.class),
            eq(OAuthTokenResponse.class)))
        .thenReturn(responseEntity);

    OAuthTokenResponse result = iamAuthorizeService.refreshToken("refresh-token");

    assertThat(result).isNotNull();
    assertThat(result.getAccessToken()).isEqualTo("new-access");
  }

  @Test
  void refreshToken_WhenHttpClientError_ThrowsAuthenticationException() {
    when(mwPlannerProperties.getIam()).thenReturn(iamConfig);
    when(restTemplate.exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.POST),
            any(org.springframework.http.HttpEntity.class),
            eq(OAuthTokenResponse.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "invalid_token"));

    assertThatThrownBy(() -> iamAuthorizeService.refreshToken("bad-refresh"))
        .isInstanceOf(AuthenticationException.class);
  }

  @Test
  void logout_WithValidRequest_CompletesWithoutThrow() {
    when(mwPlannerProperties.getIam()).thenReturn(iamConfig);
    when(restTemplate.exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.POST),
            any(org.springframework.http.HttpEntity.class),
            eq(Void.class)))
        .thenReturn(ResponseEntity.ok().build());

    com.mw.planner.dto.LogoutRequest request = new com.mw.planner.dto.LogoutRequest();
    request.setRefresh_token("token-to-revoke");

    iamAuthorizeService.logout(request);

    verify(restTemplate).exchange(anyString(), any(), any(), eq(Void.class));
  }
}
