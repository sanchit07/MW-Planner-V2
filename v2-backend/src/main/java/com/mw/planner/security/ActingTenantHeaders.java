package com.mw.planner.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Single source of truth for resolving the "acting" company/tenant a request is scoped to.
 *
 * <p>Historically two headers existed: {@code X-Company-Id} (used by authority rescoping in {@link
 * CompanyScopedAuthoritiesFilter}) and {@code X-Tenant-Id} (used by some approval/execution APIs
 * for data scoping). Both authority rescoping and data scoping now resolve the acting tenant
 * through this class so they can never diverge: {@code X-Company-Id} wins, {@code X-Tenant-Id} is
 * the fallback.
 *
 * <p>This resolves the raw header only — membership validation and the fallback to the user's
 * primary/active company live in {@code UserService.resolveActingCompanyId}.
 */
public final class ActingTenantHeaders {

  public static final String COMPANY_ID_HEADER = "X-Company-Id";
  public static final String TENANT_ID_HEADER = "X-Tenant-Id";

  private ActingTenantHeaders() {}

  /**
   * Acting-tenant header value from the given request: X-Company-Id, else X-Tenant-Id, else null.
   */
  public static String resolve(HttpServletRequest request) {
    String companyId = request.getHeader(COMPANY_ID_HEADER);
    if (companyId != null && !companyId.isBlank()) {
      return companyId;
    }
    String tenantId = request.getHeader(TENANT_ID_HEADER);
    if (tenantId != null && !tenantId.isBlank()) {
      return tenantId;
    }
    return null;
  }

  /** Same as {@link #resolve}, from the current thread-bound request; null outside a request. */
  public static String fromCurrentRequest() {
    if (RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes servletAttributes) {
      return resolve(servletAttributes.getRequest());
    }
    return null;
  }
}
