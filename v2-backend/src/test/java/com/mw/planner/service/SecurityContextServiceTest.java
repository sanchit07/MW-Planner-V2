package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mw.planner.exception.auth.AuthenticationException;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;

@ExtendWith(MockitoExtension.class)
class SecurityContextServiceTest {

  @InjectMocks private SecurityContextService securityContextService;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void getCurrentAuthentication_WhenNoContext_ThrowsAuthenticationException() {
    assertThatThrownBy(securityContextService::getCurrentAuthentication)
        .isInstanceOf(AuthenticationException.class)
        .hasMessageContaining("No authenticated user found");
  }

  @Test
  void getCurrentAuthentication_WhenContextSet_ReturnsAuthentication() {
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken("user", "token", Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThat(securityContextService.getCurrentAuthentication()).isEqualTo(auth);
  }

  @Test
  void extractTokenFromAuthentication_WhenNull_ReturnsNull() {
    assertThat(securityContextService.extractTokenFromAuthentication(null)).isNull();
  }

  @Test
  void extractTokenFromAuthentication_WhenCredentialsIsString_ReturnsToken() {
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken("user", "my-bearer-token", Collections.emptyList());

    assertThat(securityContextService.extractTokenFromAuthentication(auth))
        .isEqualTo("my-bearer-token");
  }

  @Test
  void getBearerToken_WhenAuthenticatedWithToken_ReturnsToken() {
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken("user", "bearer-token", Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThat(securityContextService.getBearerToken()).isEqualTo("bearer-token");
  }

  @Test
  void getBearerToken_WhenNoToken_ThrowsAuthenticationException() {
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken("user", null, Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThatThrownBy(securityContextService::getBearerToken)
        .isInstanceOf(AuthenticationException.class)
        .hasMessageContaining("Token not available");
  }

  @Test
  void getCurrentUsername_WhenAuthenticated_ReturnsName() {
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken("john@example.com", null, Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThat(securityContextService.getCurrentUsername()).isEqualTo("john@example.com");
  }
}
