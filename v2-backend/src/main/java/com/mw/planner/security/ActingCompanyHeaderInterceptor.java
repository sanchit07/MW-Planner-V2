package com.mw.planner.security;

import com.mw.planner.constants.JwtConstants;
import com.mw.planner.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Central membership validation for tenant switching: any authenticated request carrying an {@code
 * X-Company-Id} header that names a company the user is neither a member of nor a child-company
 * tenant of is rejected with 403 — regardless of whether the target endpoint consults the acting
 * company. Global admins may act as any company.
 */
@Component
@RequiredArgsConstructor
public class ActingCompanyHeaderInterceptor implements HandlerInterceptor {

  private final UserService userService;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
      return true; // unauthenticated/whitelisted endpoints are out of scope
    }
    // Only X-Company-Id is honored — the same single header the authorities-rescoping
    // filter reads. The legacy X-Tenant-Id header is intentionally NOT supported: honoring
    // it here while CompanyScopedAuthoritiesFilter ignores it would let data scope and
    // permissions diverge (write with company A's authorities against company B's data).
    String requested = request.getHeader(UserService.COMPANY_ID_HEADER);
    if (requested == null || requested.isBlank()) {
      return true;
    }
    Jwt jwt = jwtAuth.getToken();
    if (requested.equals(jwt.getClaimAsString(JwtConstants.CLAIM_PRIMARY_COMPANY_ID))) {
      return true;
    }
    if (Boolean.TRUE.equals(jwt.getClaimAsBoolean(JwtConstants.CLAIM_IS_GLOBAL_ADMIN))) {
      return true;
    }
    if (!userService.isTenantOfCompany(requested)) {
      throw new AccessDeniedException("User is not a member of company " + requested);
    }
    return true;
  }
}
