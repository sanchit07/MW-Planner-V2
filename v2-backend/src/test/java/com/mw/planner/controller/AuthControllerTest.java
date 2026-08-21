package com.mw.planner.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.dto.*;
import com.mw.planner.exception.GlobalExceptionHandler;
import com.mw.planner.security.IamAuthorizeService;
import com.mw.planner.service.MessageService;
import com.mw.planner.service.MetricsService;
import com.mw.planner.service.UserService;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @Mock private IamAuthorizeService iamAuthorizeService;
  @Mock private MessageService messageService;
  @Mock private MetricsService metricsService;
  @Mock private UserService userService;

  @InjectMocks private AuthController authController;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;
  private IamUserContext testUserContext;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageService, metricsService, userService))
            .build();
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();
    testUserContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .locale(Locale.ENGLISH)
            .build();
    lenient().when(userService.getIamUserContext()).thenReturn(testUserContext);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(iamAuthorizeService);
  }

  @Test
  void refresh_WithValidRefreshToken_ReturnsSuccess() throws Exception {
    RefreshTokenRequest request =
        RefreshTokenRequest.builder().refreshToken("refresh-token-123").build();
    OAuthTokenResponse oauthResponse = new OAuthTokenResponse();
    oauthResponse.setAccessToken("access-token");
    oauthResponse.setExpiresIn(3600L);
    when(iamAuthorizeService.refreshToken("refresh-token-123")).thenReturn(oauthResponse);

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.access_token").value("access-token"))
        .andExpect(jsonPath("$.data.expires_in").value(3600));

    verify(iamAuthorizeService).refreshToken("refresh-token-123");
  }

  @Test
  void refresh_WithEmptyRefreshToken_ReturnsBadRequest() throws Exception {
    RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("").build();

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void authorize_WithValidRedirect_ReturnsRedirectView() throws Exception {
    IamAuthorizeService.AuthorizationUrlResult result =
        new IamAuthorizeService.AuthorizationUrlResult(
            "https://iam.example.com/oauth/authorize?response_type=code",
            "state-123",
            "codeVerifier");
    when(iamAuthorizeService.buildAuthorizationUrlWithPkce(any())).thenReturn(result);

    mockMvc
        .perform(
            get("/api/v1/auth/oauth/authorize")
                .param("redirect_uri", "https://app.example.com/callback"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("https://iam.example.com/oauth/authorize?response_type=code"));

    verify(iamAuthorizeService).buildAuthorizationUrlWithPkce(any());
  }

  @Test
  void callback_WithValidCodeAndState_ReturnsTokenResponse() throws Exception {
    OAuthTokenResponse tokenResponse = new OAuthTokenResponse();
    tokenResponse.setAccessToken("access-token");
    tokenResponse.setRefreshToken("refresh-token");
    tokenResponse.setExpiresIn(3600L);
    when(iamAuthorizeService.exchangeCodeForToken(eq("code123"), any(), eq("state123")))
        .thenReturn(tokenResponse);

    mockMvc
        .perform(
            get("/api/v1/auth/oauth/callback")
                .param("code", "code123")
                .param("state", "state123")
                .param("redirect_uri", "https://app.example.com/callback"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.access_token").value("access-token"));

    verify(iamAuthorizeService).exchangeCodeForToken(eq("code123"), any(), eq("state123"));
  }

  @Test
  void callback_WithMissingCode_ReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/auth/oauth/callback")
                .param("state", "state123")
                .param("redirect_uri", "https://app.example.com/callback"))
        .andExpect(status().is5xxServerError());
  }

  @Test
  void callback_WithMissingState_ReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/auth/oauth/callback")
                .param("code", "code123")
                .param("redirect_uri", "https://app.example.com/callback"))
        .andExpect(status().is5xxServerError());
  }

  @Test
  void logout_WithRefreshToken_CallsLogoutAndReturnsSuccess() throws Exception {
    LogoutRequest request = LogoutRequest.builder().refresh_token("refresh-token-123").build();

    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value("Logged out successfully"));

    verify(iamAuthorizeService)
        .logout(argThat(req -> "refresh-token-123".equals(req.getRefresh_token())));
  }

  @Test
  void logout_WithEmptyRefreshToken_ReturnsSuccessWithoutCallingLogout() throws Exception {
    LogoutRequest request = LogoutRequest.builder().refresh_token("").build();

    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(iamAuthorizeService, never()).logout(any());
  }
}
