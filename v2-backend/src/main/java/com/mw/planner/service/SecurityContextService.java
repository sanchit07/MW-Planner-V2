package com.mw.planner.service;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.auth.AuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Service for extracting security context information, particularly Bearer tokens from
 * authentication objects or HTTP request headers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityContextService {

  /**
   * Gets the current authentication from SecurityContext.
   *
   * @return Authentication object
   * @throws AuthenticationException if no authenticated user found
   */
  public Authentication getCurrentAuthentication() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      throw new AuthenticationException(ErrorCode.UNAUTHORIZED, "No authenticated user found");
    }
    return authentication;
  }

  /**
   * Extracts Bearer token from authentication object or HTTP request. Tries multiple sources:
   * credentials, request header.
   *
   * @param authentication The authentication object
   * @return Bearer token string or null if not found
   */
  public String extractTokenFromAuthentication(Authentication authentication) {
    if (authentication == null) {
      return null;
    }

    // Try credentials first (if stored by any filter)
    Object credentials = authentication.getCredentials();
    if (credentials instanceof String) {
      return (String) credentials;
    }

    // Try to get from HTTP request header
    RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    if (requestAttributes instanceof ServletRequestAttributes) {
      HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
      String authHeader = request.getHeader("Authorization");
      if (authHeader != null && authHeader.startsWith("Bearer ")) {
        return authHeader.substring(7);
      }
    }

    return null;
  }

  /**
   * Gets Bearer token from security context. Throws exception if not available.
   *
   * @return Bearer token string
   * @throws AuthenticationException if no authenticated user found or token not available
   */
  public String getBearerToken() {
    Authentication authentication = getCurrentAuthentication();
    String token = extractTokenFromAuthentication(authentication);
    if (token == null || token.isBlank()) {
      throw new AuthenticationException(ErrorCode.UNAUTHORIZED, "Token not available in context");
    }
    return token;
  }

  /**
   * Gets username from current authentication.
   *
   * @return Username string
   * @throws AuthenticationException if no authenticated user found
   */
  public String getCurrentUsername() {
    Authentication authentication = getCurrentAuthentication();
    return authentication.getName();
  }
}
