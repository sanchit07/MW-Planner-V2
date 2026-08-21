package com.mw.planner.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

  @Mock private JwtUtil jwtUtil;
  @Mock private com.mw.planner.service.UserService userService;

  @InjectMocks private JwtAuthFilter jwtAuthFilter;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private FilterChain filterChain;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    filterChain = new MockFilterChain();
  }

  @Test
  void doFilterInternal_WhenNoAuthorizationHeader_ContinuesChain() throws Exception {
    jwtAuthFilter.doFilterInternal(request, response, filterChain);

    verify(jwtUtil, never()).extractUsername(anyString());
    verify(userService, never()).initIamUserContext(anyString(), anyString());
  }

  @Test
  void doFilterInternal_WhenBearerTokenAndValidUsername_InitializesUserContext() throws Exception {
    request.addHeader("Authorization", "Bearer valid.jwt.token");
    when(jwtUtil.extractUsername("valid.jwt.token")).thenReturn("user@example.com");

    jwtAuthFilter.doFilterInternal(request, response, filterChain);

    verify(jwtUtil).extractUsername("valid.jwt.token");
    verify(userService).initIamUserContext(eq("user@example.com"), eq("valid.jwt.token"));
  }

  @Test
  void doFilterInternal_WhenBearerTokenButExtractUsernameThrows_ContinuesChain() throws Exception {
    request.addHeader("Authorization", "Bearer invalid.token");
    when(jwtUtil.extractUsername("invalid.token")).thenThrow(new RuntimeException("Parse error"));

    jwtAuthFilter.doFilterInternal(request, response, filterChain);

    verify(jwtUtil).extractUsername("invalid.token");
    verify(userService, never()).initIamUserContext(anyString(), anyString());
  }

  @Test
  void doFilterInternal_WhenInitIamUserContextThrows_StillContinuesChain() throws Exception {
    request.addHeader("Authorization", "Bearer valid.jwt.token");
    when(jwtUtil.extractUsername("valid.jwt.token")).thenReturn("user@example.com");
    doThrow(new RuntimeException("IAM error"))
        .when(userService)
        .initIamUserContext(anyString(), anyString());

    jwtAuthFilter.doFilterInternal(request, response, filterChain);

    verify(userService).initIamUserContext(eq("user@example.com"), eq("valid.jwt.token"));
  }

  @Test
  void doFilterInternal_WhenHeaderDoesNotStartWithBearer_ContinuesChain() throws Exception {
    request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

    jwtAuthFilter.doFilterInternal(request, response, filterChain);

    verify(jwtUtil, never()).extractUsername(anyString());
    verify(userService, never()).initIamUserContext(anyString(), anyString());
  }
}
