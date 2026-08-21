package com.mw.recommendation.engine.service;

import com.mw.recommendation.engine.constants.JwtConstants;
import com.mw.recommendation.engine.enums.ErrorCode;
import com.mw.recommendation.engine.exception.auth.AuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
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

  public Authentication getCurrentAuthentication() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      throw new AuthenticationException(ErrorCode.UNAUTHORIZED, "No authenticated user found");
    }
    return authentication;
  }

  /**
   * Extracts the bearer token from the authentication. Supports: (1) String credentials, (2) Jwt
   * credentials (OAuth2 resource server) via getTokenValue(), (3) Authorization header when in a
   * request context. This allows the token to be available after SecurityContext propagation to
   * async threads, where the propagated Authentication holds the Jwt.
   */
  public String extractTokenFromAuthentication(Authentication authentication) {
    if (authentication == null) {
      return null;
    }

    Object credentials = authentication.getCredentials();
    if (credentials instanceof String) {
      return (String) credentials;
    }
    if (credentials instanceof Jwt jwt) {
      return jwt.getTokenValue();
    }

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

  public String getBearerToken() {
    Authentication authentication = getCurrentAuthentication();
    String token = extractTokenFromAuthentication(authentication);
    if (token == null || token.isBlank()) {
      throw new AuthenticationException(ErrorCode.UNAUTHORIZED, "Token not available in context");
    }
    return token;
  }

  /**
   * Returns the bearer token when available, or null if there is no authentication or no token. Use
   * when the caller may run outside a request thread (e.g. async) and can proceed without auth.
   * With SecurityContext propagation, the token is obtained from the propagated Authentication's
   * Jwt credentials (see extractTokenFromAuthentication).
   */
  public String getBearerTokenOrNull() {
    try {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication == null) {
        return null;
      }
      return extractTokenFromAuthentication(authentication);
    } catch (Exception e) {
      log.trace("No bearer token in context: {}", e.getMessage());
      return null;
    }
  }

  public String getCurrentUsername() {
    Authentication authentication = getCurrentAuthentication();
    return authentication.getName();
  }

  /**
   * Returns the caller's primary company id from the JWT {@code primary_company_id} claim. Read
   * from the token (tamper-proof) rather than trusted from a request body. The claim can live on
   * either the credentials or the principal depending on how the JWT was converted.
   *
   * @throws AuthenticationException if there is no authenticated JWT carrying the claim
   */
  public String getPrimaryCompanyId() {
    Authentication authentication = getCurrentAuthentication();

    String companyId = extractPrimaryCompanyId(authentication.getCredentials());
    if (companyId == null) {
      companyId = extractPrimaryCompanyId(authentication.getPrincipal());
    }

    if (companyId == null || companyId.isBlank()) {
      throw new AuthenticationException(
          ErrorCode.UNAUTHORIZED, "primary_company_id claim not available in token");
    }
    return companyId;
  }

  private String extractPrimaryCompanyId(Object source) {
    if (source instanceof Jwt jwt) {
      return jwt.getClaimAsString(JwtConstants.CLAIM_PRIMARY_COMPANY_ID);
    }
    return null;
  }
}
