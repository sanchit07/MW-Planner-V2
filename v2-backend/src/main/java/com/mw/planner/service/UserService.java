package com.mw.planner.service;

import static com.mw.planner.constants.JwtConstants.CLAIM_PRIMARY_COMPANY_ID;

import com.mw.planner.dto.IamUserContext;
import com.mw.planner.dto.UserInfoResponse;
import com.mw.planner.dto.UserResponseDTO;
import com.mw.planner.exception.auth.AuthenticationException;
import com.mw.planner.exception.user.UserNotFoundException;
import com.mw.planner.exception.user.UserValidationException;
import com.mw.planner.security.ActingTenantHeaders;
import com.mw.planner.service.iam.IamCompanyApiClient;
import com.mw.planner.service.iam.IamUserServiceApiClient;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Service for managing IAM user context with caching. Handles fetching, caching, and retrieving IAM
 * user context from IAM API. All IAM user context operations are cache-only (no database storage).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

  private final IamUserServiceApiClient iamUserService;
  private final IamCompanyApiClient iamCompanyApiClient;
  private final SecurityContextService securityContextService;
  @Lazy @Autowired UserService userService;

  /**
   * Gets IAM user context for the currently authenticated user, scoped to the ACTING company.
   *
   * <p>The underlying IAM context is cached per username and reflects the user's primary/active
   * company. When the request carries an acting-tenant header (X-Company-Id, or legacy X-Tenant-Id
   * — resolved via {@link ActingTenantHeaders}, the same resolver authority rescoping uses) naming
   * a company the user is a member of, the returned context is rescoped so {@code companyId} (and
   * the supplier-side flag / child companies where resolvable) follow the switched-to company. Data
   * scoping downstream therefore follows the company the user switched to, not just their
   * permissions.
   *
   * @return IamUserContext for current user, rescoped to the acting company
   * @throws UserNotFoundException if no authenticated user found or token not available
   */
  public IamUserContext getIamUserContext() {
    IamUserContext base = getBaseIamUserContext();
    String acting = resolveActingCompanyFrom(base, null);
    if (acting == null || acting.equals(base.getCompanyId())) {
      return base;
    }
    return rescopeToActingCompany(base, acting);
  }

  /**
   * The cached, primary-company-scoped IAM context. Internal building block: acting-company
   * resolution and membership checks run against this so they cannot recurse or be influenced by
   * the acting-tenant header itself.
   */
  private IamUserContext getBaseIamUserContext() {
    try {
      String username = securityContextService.getCurrentUsername();
      String token = securityContextService.getBearerToken();
      return userService.initIamUserContext(username, token);
    } catch (AuthenticationException e) {
      throw new UserNotFoundException("Unable to get IAM user context", e);
    }
  }

  /**
   * The single shared resolver for the acting company/tenant of the current request. Resolution
   * order: {@code X-Company-Id} header, then {@code X-Tenant-Id} header (same order as authority
   * rescoping in CompanyScopedAuthoritiesFilter), then the explicit tenant id argument, and only if
   * the winning candidate is a company the user is actually a member of (directly or via child
   * companies). Otherwise falls back to the user's primary/active company.
   *
   * @param explicitTenantId optional tenant id passed explicitly by a controller (legacy {@code
   *     X-Tenant-Id} request-param binding); may be null
   * @return the acting company id (never null while authenticated with an active company)
   */
  public String resolveActingCompanyId(String explicitTenantId) {
    return resolveActingCompanyFrom(getBaseIamUserContext(), explicitTenantId);
  }

  private String resolveActingCompanyFrom(IamUserContext base, String explicitTenantId) {
    String candidate = ActingTenantHeaders.fromCurrentRequest();
    if (candidate == null || candidate.isBlank()) {
      candidate = explicitTenantId;
    }
    if (candidate != null
        && !candidate.isBlank()
        && !candidate.equals(base.getCompanyId())
        && isMemberOf(base, candidate)) {
      return candidate;
    }
    return base.getCompanyId();
  }

  /**
   * Rescopes the cached (primary-company) context to the acting company: companyId, the
   * supplier-side flag (from the acting membership's company type when known), and the acting
   * company's child companies (cached per company; cleared on failure rather than silently keeping
   * the primary company's children).
   */
  private IamUserContext rescopeToActingCompany(IamUserContext base, String actingCompanyId) {
    IamUserContext acting = base.toBuilder().companyId(actingCompanyId).build();

    if (base.getMemberships() != null) {
      base.getMemberships().stream()
          .filter(m -> actingCompanyId.equals(m.getCompanyId()))
          .findFirst()
          .map(UserInfoResponse.Membership::getCompanyType)
          .map(UserInfoResponse.CompanyType::getIsSupplierSide)
          .ifPresent(acting::setIsSupplierSide);
    }

    acting.setChildCompanies(null);
    try {
      String token = securityContextService.getBearerToken();
      java.util.List<UserInfoResponse.ChildCompany> children =
          userService.getCompanyChildrenCached(actingCompanyId, token);
      if (children != null && !children.isEmpty()) {
        acting.setChildCompanies(children);
      }
    } catch (Exception e) {
      log.warn(
          "Could not resolve child companies for acting company {}: {}",
          actingCompanyId,
          e.getMessage());
    }
    return acting;
  }

  /** Child companies of a company, cached per companyId. */
  @Cacheable(value = "iamCompanyChildren", key = "#companyId", unless = "#result == null")
  public java.util.List<UserInfoResponse.ChildCompany> getCompanyChildrenCached(
      String companyId, String token) {
    return iamCompanyApiClient.getCompanyChildren(companyId, token);
  }

  /**
   * Gets user information by user ID from IAM API with caching. First checks cache, if not found,
   * extracts token from security context and fetches from IAM API.
   *
   * @param userId User ID to fetch
   * @return UserResponseDTO containing user information
   * @throws UserNotFoundException if user not found or no authenticated user found
   * @throws UserValidationException if userId is invalid
   */
  @Cacheable(value = "iamUsers", key = "#userId", unless = "#result == null")
  public UserResponseDTO getUserById(String userId) {
    if (userId == null || userId.trim().isEmpty()) {
      throw new UserValidationException("userId", userId);
    }

    try {
      String token = securityContextService.getBearerToken();
      log.debug("Fetching user from IAM API for user ID: {}", userId);
      return iamUserService.getUserById(userId, token);
    } catch (AuthenticationException e) {
      throw new UserNotFoundException(userId, e);
    } catch (Exception e) {
      log.error("Error fetching user by ID: {}", userId, e);
      throw new UserNotFoundException(userId, e);
    }
  }

  /**
   * Gets IAM user context for the specified username. First checks cache, if not found, fetches
   * from IAM API using the provided token.
   *
   * @param username Username (subject) to get context for
   * @param token Bearer token for IAM API authentication (required if not in cache)
   * @return IamUserContext containing user information, permissions, and memberships
   * @throws UserValidationException if username or token is invalid
   * @throws UserNotFoundException if user not found in IAM system
   */
  @Cacheable(value = "iamUserContext", key = "#username", unless = "#result == null")
  public IamUserContext initIamUserContext(@NotNull String username, @NotNull String token) {
    if (username == null || username.trim().isEmpty()) {
      throw new UserValidationException("username", username);
    }
    if (token == null || token.trim().isEmpty()) {
      throw new UserValidationException("token", "token is null or empty");
    }

    log.info("IamUserContext not in cache, fetching from IAM API for username: {}", username);
    try {
      // Fetch user info from IAM API
      UserInfoResponse userInfoResponse = iamUserService.getUserInfo(token);

      if (userInfoResponse == null) {
        throw new UserNotFoundException(
            username, new IllegalStateException("IAM API returned null response"));
      }

      if (userInfoResponse.getData() == null) {
        throw new UserNotFoundException(
            username, new IllegalStateException("IAM API response data is null"));
      }

      IamUserContext iamUserContext = IamUserContext.fromUserInfoData(userInfoResponse.getData());

      // Enrich with full company membership list from /users/me/companies (paginated)
      java.util.List<UserInfoResponse.Membership> memberships =
          iamUserService.getUserMeCompanies(token);
      if (!memberships.isEmpty()) {
        iamUserContext.setMemberships(memberships);
      }

      // Enrich with all child companies from /companies/{companyId}/children
      String activeCompanyId = iamUserContext.getCompanyId();
      if (activeCompanyId != null) {
        java.util.List<UserInfoResponse.ChildCompany> children =
            iamCompanyApiClient.getCompanyChildren(activeCompanyId, token);
        if (!children.isEmpty()) {
          iamUserContext.setChildCompanies(children);
        }
      }

      log.info("IAM user context initialized and cached for username: {}", username);
      return iamUserContext;
    } catch (UserNotFoundException e) {
      throw e;
    } catch (Exception e) {
      log.error("Error fetching IAM user context for username: {}", username, e);
      throw new UserNotFoundException(username, e);
    }
  }

  public static String extractUserName(UserResponseDTO user) {
    if (user.getFirstName() != null || user.getLastName() != null) {
      String firstName = user.getFirstName() != null ? user.getFirstName() : "";
      String lastName = user.getLastName() != null ? " " + user.getLastName() : "";
      return (firstName + lastName).trim();
    }
    return user.getUsername() != null ? user.getUsername() : "Unknown";
  }

  public static String extractUserRole(UserResponseDTO user) {
    if (user.getCurrentCompany() != null && user.getCurrentCompany().getRoleName() != null) {
      return user.getCurrentCompany().getRoleName();
    }
    return "Agency";
  }

  /**
   * Gets the primary company ID from the current user's JWT token.
   *
   * @return Primary company ID from JWT token, or null if not available
   */
  public String getPrimaryCompanyId() {
    try {
      Authentication authentication = securityContextService.getCurrentAuthentication();
      if (authentication instanceof JwtAuthenticationToken) {
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        String primaryCompanyId = jwt.getClaimAsString(CLAIM_PRIMARY_COMPANY_ID);
        log.debug("Extracted primaryCompanyId from JWT: {}", primaryCompanyId);
        return primaryCompanyId;
      }
    } catch (Exception e) {
      log.warn("Failed to extract primaryCompanyId from JWT token: {}", e.getMessage());
    }
    return null;
  }

  /** Header that selects the acting company for both authorization and data scoping. */
  public static final String COMPANY_ID_HEADER = "X-Company-Id";

  /**
   * Resolves the company the current user is acting as — the single source of truth for data
   * scoping under tenant switching.
   *
   * <p>Reads the {@code X-Company-Id} header from the current request. When absent or equal to the
   * JWT's primary company, the primary company is returned. When it names another company,
   * membership is validated (direct membership or child company; global admins may act as any
   * company) and the switched company is returned; a non-member header is rejected with 403.
   *
   * @return the acting company ID (may be null only when no authenticated context is available)
   * @throws org.springframework.security.access.AccessDeniedException if the header names a company
   *     the user is not a member of
   */
  public String getActingCompanyId() {
    String primary = getPrimaryCompanyId();
    String requested = getRequestedActingCompanyId();
    if (requested == null || requested.isBlank() || requested.equals(primary)) {
      if (primary != null) {
        return primary;
      }
      try {
        return getIamUserContext().getCompanyId();
      } catch (Exception e) {
        log.debug("No user context available to resolve acting company: {}", e.getMessage());
        return null;
      }
    }
    if (isCurrentUserGlobalAdmin() || isTenantOfCompany(requested)) {
      return requested;
    }
    throw new org.springframework.security.access.AccessDeniedException(
        "User is not a member of company " + requested);
  }

  /**
   * Asserts the current user may act for (attribute data to / query data of) the given company.
   * Only the resolved ACTING company (or any company, for global admins) qualifies — other
   * memberships the user holds do NOT: the endpoint's authorities were already evaluated against
   * the acting company by {@code CompanyScopedAuthoritiesFilter}, so accepting a different member
   * company here would let a dual-member user apply company A's permissions to company B's data.
   *
   * @throws org.springframework.security.access.AccessDeniedException otherwise
   */
  public void assertCanActForCompany(String companyId) {
    if (companyId == null || companyId.isBlank()) {
      return;
    }
    if (companyId.equals(getActingCompanyId()) || isCurrentUserGlobalAdmin()) {
      return;
    }
    throw new org.springframework.security.access.AccessDeniedException(
        "User is not a member of company " + companyId);
  }

  /** Reads the acting-company header ({@code X-Company-Id}). */
  private String getRequestedActingCompanyId() {
    org.springframework.web.context.request.RequestAttributes attrs =
        org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
    if (attrs
        instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
      jakarta.servlet.http.HttpServletRequest request = servletAttrs.getRequest();
      return request.getHeader(COMPANY_ID_HEADER);
    }
    return null;
  }

  /** Whether the current user's JWT carries the global-admin claim. */
  public boolean isCurrentUserGlobalAdmin() {
    try {
      Authentication authentication = securityContextService.getCurrentAuthentication();
      if (authentication instanceof JwtAuthenticationToken jwtAuth) {
        return Boolean.TRUE.equals(
            jwtAuth
                .getToken()
                .getClaimAsBoolean(com.mw.planner.constants.JwtConstants.CLAIM_IS_GLOBAL_ADMIN));
      }
    } catch (Exception e) {
      log.debug("Failed to read global-admin claim: {}", e.getMessage());
    }
    return false;
  }

  /**
   * Checks whether the given company ID is present in the current user's membership list.
   *
   * @param companyId Company ID to check
   * @return true if the user is a member of the given company, false otherwise
   */
  public boolean isTenantOfCompany(String companyId) {
    if (companyId == null || companyId.isBlank()) {
      return false;
    }
    return isMemberOf(getBaseIamUserContext(), companyId);
  }

  /**
   * Membership check against a given context (direct membership or child company).
   *
   * <p>A direct membership only counts when it is explicitly ACTIVE ({@code isActive == TRUE}): IAM
   * keeps deactivated/revoked memberships in the list, and a stale JWT could otherwise still
   * exercise permissions for a company the user was removed from. Child companies count unless
   * explicitly flagged inactive (the children endpoint returns active children; the flag may be
   * absent).
   */
  private static boolean isMemberOf(IamUserContext context, String companyId) {
    if (context.getMemberships() != null) {
      boolean directActiveMember =
          context.getMemberships().stream()
              .filter(m -> companyId.equals(m.getCompanyId()))
              .anyMatch(m -> Boolean.TRUE.equals(m.getIsActive()));
      if (directActiveMember) {
        return true;
      }
    }

    // Child companies carry no active flag in the IAM payload — the children endpoint only
    // returns currently valid children, so presence in the list is the active-state check.
    if (context.getChildCompanies() != null) {
      return context.getChildCompanies().stream()
          .map(UserInfoResponse.ChildCompany::getId)
          .filter(java.util.Objects::nonNull)
          .anyMatch(companyId::equals);
    }

    return false;
  }
}
