package com.mw.planner.security;

import static com.mw.planner.constants.JwtConstants.*;
import static com.mw.planner.security.SecurityConfiguration.GLOBAL_ADMIN_ROLE;
import static com.mw.planner.security.SecurityConfiguration.SYSTEM_ADMIN_ROLE;

import com.mw.planner.exception.auth.JwtNoSubscriptionsException;
import com.mw.planner.exception.auth.JwtSubscriptionMismatchException;
import java.util.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class CustomJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  /**
   * Domain -> resource names used for expanding wildcard permissions (e.g. camp:*:* ->
   * camp:campaigns:read, camp:campaigns:create, ...). Aligns with hasRole() checks in controllers.
   */
  private static final Map<String, Set<String>> DOMAIN_RESOURCES =
      Map.of(
          "camp", Set.of("campaigns", "creatives", "schedules"),
          "iam", Set.of("users", "companies", "products"),
          "plan", Set.of("plans", "audiences", "proposals"),
          "planner", Set.of("plans", "audiences", "proposals"),
          "meas", Set.of(),
          "inv", Set.of());

  private final String productId;

  public CustomJwtAuthenticationConverter(String productId) {
    this.productId = productId;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {

    // Check if user is global admin or has system role - they bypass subscription validation
    Boolean isGlobalAdmin = jwt.getClaimAsBoolean(CLAIM_IS_GLOBAL_ADMIN);
    Boolean hasSystemRole = jwt.getClaimAsBoolean(CLAIM_HAS_SYSTEM_ROLE);
    boolean isSuperUser = Boolean.TRUE.equals(isGlobalAdmin) || Boolean.TRUE.equals(hasSystemRole);

    // Extract subscriptions
    List<String> subscriptions = jwt.getClaimAsStringList(CLAIM_SUBSCRIPTIONS);

    // Verify user has subscription to this product (skip for global/system admins)
    if (!isSuperUser) {
      if (subscriptions == null || subscriptions.isEmpty()) {
        throw new JwtNoSubscriptionsException(productId);
      }

      if (!subscriptions.contains(productId)) {
        throw new JwtSubscriptionMismatchException(productId, subscriptions);
      }
    }

    // Extract roles
    Collection<GrantedAuthority> authorities = extractAuthorities(jwt);

    // Create custom authentication token
    return new JwtAuthenticationToken(jwt, authorities);
  }

  /**
   * Extracts authorities from JWT token. This includes: 1. Global Admin and System Admin roles
   * (from JWT claims) 2. System permissions (from system_permissions claim) 3. Company-scoped
   * permissions (from primary company only) 4. Company IDs as authorities
   *
   * @param jwt The JWT token
   * @return Collection of GrantedAuthority objects
   */
  private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
    return extractAuthoritiesForCompany(jwt, jwt.getClaimAsString(CLAIM_PRIMARY_COMPANY_ID));
  }

  /**
   * Extracts authorities scoped to a specific company (used for tenant switching: the X-Company-Id
   * header selects which company's permissions from the JWT's per-company permissions map become
   * authorities). Global/system-level authorities are always included.
   */
  public Collection<GrantedAuthority> extractAuthoritiesForCompany(Jwt jwt, String companyId) {
    List<GrantedAuthority> authorities = new ArrayList<>();

    // 1. Extract Global Admin role
    Boolean isGlobalAdmin = jwt.getClaimAsBoolean(CLAIM_IS_GLOBAL_ADMIN);
    if (Boolean.TRUE.equals(isGlobalAdmin)) {
      authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + GLOBAL_ADMIN_ROLE));
    }

    // 2. Extract System Admin role
    Boolean hasSystemRole = jwt.getClaimAsBoolean(CLAIM_HAS_SYSTEM_ROLE);
    if (Boolean.TRUE.equals(hasSystemRole)) {
      authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + SYSTEM_ADMIN_ROLE));
    }

    // 3. Extract System Permissions (system-level permissions, not company-scoped)
    List<String> systemPermissions = jwt.getClaimAsStringList(CLAIM_SYSTEM_PERMISSIONS);
    if (systemPermissions != null && !systemPermissions.isEmpty()) {
      systemPermissions.forEach(
          perm -> {
            // Add permission and expand wildcards (e.g., *:*:* grants all permissions)
            addCompanyPermissionAuthorities(authorities, perm);
          });
    }

    // 4. Extract permissions for the acting company (primary by default; the
    // CompanyScopedAuthoritiesFilter re-invokes this with the X-Company-Id header value)
    Map<String, List<String>> permissions = jwt.getClaim(CLAIM_PERMISSIONS);

    if (permissions != null && companyId != null) {
      List<String> companyPermissions = permissions.get(companyId);
      if (companyPermissions != null && !companyPermissions.isEmpty()) {
        companyPermissions.forEach(
            permission -> addCompanyPermissionAuthorities(authorities, permission));
      }
    }
    return authorities;
  }

  /**
   * Adds authorities for a single company permission. Handles:
   *
   * <ul>
   *   <li>domain:resource:* (e.g. camp:campaigns:*) -> expand to CRUD actions
   *   <li>domain:*:* (e.g. camp:*:*, plan:*:*) -> expand to domain:resource:action for known
   *       resources
   *   <li>domain:*:action (e.g. iam:*:read) -> expand to domain:resource:action for known resources
   * </ul>
   *
   * Always adds the literal permission with ROLE_ prefix so hasRole() can match.
   */
  private void addCompanyPermissionAuthorities(
      List<GrantedAuthority> authorities, String permission) {
    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + permission));

    String[] parts = permission.split(":", -1);
    if (parts.length != 3) {
      // Non 3-part permission: keep legacy behaviour (e.g. ends with :* -> expand actions)
      if (permission.endsWith(WILDCARD_SUFFIX)) {
        String resource = permission.substring(0, permission.length() - WILDCARD_SUFFIX.length());
        for (String action : CRUD_ACTIONS) {
          authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + resource + ":" + action));
        }
      }
      return;
    }

    String domain = parts[0];
    String resource = parts[1];
    String action = parts[2];

    // Special case: *:*:* -> expand to ALL domains, ALL resources, ALL actions
    if (WILDCARD.equals(domain) && WILDCARD.equals(resource) && WILDCARD.equals(action)) {
      for (Map.Entry<String, Set<String>> entry : DOMAIN_RESOURCES.entrySet()) {
        String domainName = entry.getKey();
        Set<String> resources = entry.getValue();
        for (String res : resources) {
          for (String act : CRUD_ACTIONS) {
            authorities.add(
                new SimpleGrantedAuthority(ROLE_PREFIX + domainName + ":" + res + ":" + act));
          }
        }
      }
      return;
    }

    Set<String> knownResources = DOMAIN_RESOURCES.getOrDefault(domain, Collections.emptySet());

    // domain:*:* -> expand to domain:resource:action for each known resource and CRUD action
    if (WILDCARD.equals(resource) && WILDCARD.equals(action)) {
      for (String res : knownResources) {
        for (String act : CRUD_ACTIONS) {
          authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + domain + ":" + res + ":" + act));
        }
      }
      return;
    }

    // domain:resource:* -> expand to domain:resource:read, create, update, delete
    if (WILDCARD.equals(action)) {
      String resourcePrefix = domain + ":" + resource + ":";
      for (String act : CRUD_ACTIONS) {
        authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + resourcePrefix + act));
      }
      return;
    }

    // domain:*:action -> expand to domain:resource:action for each known resource
    if (WILDCARD.equals(resource)) {
      for (String res : knownResources) {
        authorities.add(
            new SimpleGrantedAuthority(ROLE_PREFIX + domain + ":" + res + ":" + action));
      }
    }
  }
}
