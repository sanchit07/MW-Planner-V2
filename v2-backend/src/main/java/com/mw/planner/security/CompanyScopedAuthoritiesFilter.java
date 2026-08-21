package com.mw.planner.security;

import com.mw.planner.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Aligns the request's AUTHORITIES with the same acting company that DATA scoping uses, on every
 * authenticated request. The acting company comes from the single shared resolver
 * (UserService.getActingCompanyId(): {@code X-Company-Id}, then legacy {@code X-Tenant-Id},
 * membership-validated against current IAM data, falling back to IAM's active company). The
 * request's company-scoped authorities are then re-extracted from the JWT's per-company permissions
 * map for exactly that company.
 *
 * <p>Fail-closed rules:
 *
 * <ul>
 *   <li>An explicit acting-tenant header that does not survive membership validation (unknown
 *       company, revoked/inactive membership — even one equal to the JWT's own {@code
 *       primary_company_id}) strips ALL company-scoped authorities (only global/system remain).
 *   <li>No header: authorities follow IAM's current acting company, so a stale JWT whose {@code
 *       primary_company_id} names a company the user was removed from can no longer exercise that
 *       company's permissions against the IAM-active company's data.
 *   <li>If the acting company cannot be resolved at all, company authorities are stripped.
 * </ul>
 */
public class CompanyScopedAuthoritiesFilter extends OncePerRequestFilter {

  public static final String COMPANY_ID_HEADER = ActingTenantHeaders.COMPANY_ID_HEADER;

  private final CustomJwtAuthenticationConverter converter;
  private final ObjectProvider<UserService> userServiceProvider;

  public CompanyScopedAuthoritiesFilter(
      CustomJwtAuthenticationConverter converter, ObjectProvider<UserService> userServiceProvider) {
    this.converter = converter;
    this.userServiceProvider = userServiceProvider;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jwtAuth) {
      Jwt jwt = jwtAuth.getToken();
      String jwtPrimary =
          jwt.getClaimAsString(com.mw.planner.constants.JwtConstants.CLAIM_PRIMARY_COMPANY_ID);
      String header = ActingTenantHeaders.resolve(request);
      String acting = resolveActingCompany();

      // Which company's JWT permissions may become authorities for this request:
      // - explicit header: only itself, and only if the shared resolver accepted it (a rejected
      //   header resolves to the fallback company instead — fail closed, do NOT silently fall
      //   back to different permissions than the caller asked to act under);
      // - no header: whatever IAM says the user's acting company is (null -> strip).
      String authorityCompany;
      if (header != null && !header.isBlank()) {
        authorityCompany = header.equals(acting) ? header : null;
      } else {
        authorityCompany = acting;
      }

      // The converter already extracted authorities for the JWT's primary company; only replace
      // them when the effective company differs (or must be stripped).
      if (!Objects.equals(authorityCompany, jwtPrimary)) {
        JwtAuthenticationToken rescoped =
            new JwtAuthenticationToken(
                jwt, converter.extractAuthoritiesForCompany(jwt, authorityCompany));
        rescoped.setDetails(jwtAuth.getDetails());
        SecurityContextHolder.getContext().setAuthentication(rescoped);
      }
    }
    filterChain.doFilter(request, response);
  }

  /** Shared acting-company resolution; any failure resolves to null (fail closed). */
  private String resolveActingCompany() {
    try {
      return userServiceProvider.getObject().getActingCompanyId();
    } catch (Exception e) {
      logger.warn("Could not resolve acting company for authority scoping; failing closed", e);
      return null;
    }
  }
}
