package com.mw.recommendation.engine.security;

import static com.mw.recommendation.engine.constants.JwtConstants.*;
import static com.mw.recommendation.engine.security.SecurityConfiguration.GLOBAL_ADMIN_ROLE;
import static com.mw.recommendation.engine.security.SecurityConfiguration.SYSTEM_ADMIN_ROLE;

import com.mw.recommendation.engine.exception.auth.JwtNoSubscriptionsException;
import com.mw.recommendation.engine.exception.auth.JwtSubscriptionMismatchException;
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
   * Domain -> resource names for expanding wildcard permissions. Aligns with hasRole() checks in
   * controllers.
   */
  private static final Map<String, Set<String>> DOMAIN_RESOURCES =
      Map.of(
          "reco", Set.of("recommendations", "schedules"),
          "camp", Set.of("campaigns", "creatives", "schedules"),
          "iam", Set.of("users", "companies", "products"),
          "plan", Set.of("plans", "audiences", "proposals"),
          "meas", Set.of(),
          "inv", Set.of());

  private final String productId;

  public CustomJwtAuthenticationConverter(String productId) {
    this.productId = productId;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {

    // Global admins bypass subscription validation
    Boolean isGlobalAdmin = jwt.getClaimAsBoolean(CLAIM_IS_GLOBAL_ADMIN);
    if (!Boolean.TRUE.equals(isGlobalAdmin)) {
      List<String> subscriptions = jwt.getClaimAsStringList(CLAIM_SUBSCRIPTIONS);

      if (subscriptions == null || subscriptions.isEmpty()) {
        throw new JwtNoSubscriptionsException(productId);
      }

      if (!subscriptions.contains(productId)) {
        throw new JwtSubscriptionMismatchException(productId, subscriptions);
      }
    }

    Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
    return new JwtAuthenticationToken(jwt, authorities);
  }

  private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
    List<GrantedAuthority> authorities = new ArrayList<>();

    Boolean isGlobalAdmin = jwt.getClaimAsBoolean(CLAIM_IS_GLOBAL_ADMIN);
    if (Boolean.TRUE.equals(isGlobalAdmin)) {
      authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + GLOBAL_ADMIN_ROLE));
    }

    Boolean hasSystemRole = jwt.getClaimAsBoolean(CLAIM_HAS_SYSTEM_ROLE);
    if (Boolean.TRUE.equals(hasSystemRole)) {
      authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + SYSTEM_ADMIN_ROLE));
    }

    List<String> systemPermissions = jwt.getClaimAsStringList(CLAIM_SYSTEM_PERMISSIONS);
    if (systemPermissions != null && !systemPermissions.isEmpty()) {
      systemPermissions.forEach(
          perm -> authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + perm)));
    }

    String primaryCompanyId = jwt.getClaimAsString(CLAIM_PRIMARY_COMPANY_ID);
    Map<String, List<String>> permissions = jwt.getClaim(CLAIM_PERMISSIONS);

    if (permissions != null && primaryCompanyId != null) {
      List<String> companyPermissions = permissions.get(primaryCompanyId);
      if (companyPermissions != null && !companyPermissions.isEmpty()) {
        companyPermissions.forEach(
            permission -> addCompanyPermissionAuthorities(authorities, permission));
      }
    }
    return authorities;
  }

  private void addCompanyPermissionAuthorities(
      List<GrantedAuthority> authorities, String permission) {
    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + permission));

    String[] parts = permission.split(":", -1);
    if (parts.length != 3) {
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
    Set<String> knownResources = DOMAIN_RESOURCES.getOrDefault(domain, Collections.emptySet());

    if (WILDCARD.equals(resource) && WILDCARD.equals(action)) {
      for (String res : knownResources) {
        for (String act : CRUD_ACTIONS) {
          authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + domain + ":" + res + ":" + act));
        }
      }
      return;
    }

    if (WILDCARD.equals(action)) {
      String resourcePrefix = domain + ":" + resource + ":";
      for (String act : CRUD_ACTIONS) {
        authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + resourcePrefix + act));
      }
      return;
    }

    if (WILDCARD.equals(resource)) {
      for (String res : knownResources) {
        authorities.add(
            new SimpleGrantedAuthority(ROLE_PREFIX + domain + ":" + res + ":" + action));
      }
    }
  }
}
