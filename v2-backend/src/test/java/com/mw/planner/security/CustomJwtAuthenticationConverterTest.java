package com.mw.planner.security;

import static com.mw.planner.constants.JwtConstants.*;
import static com.mw.planner.security.SecurityConfiguration.GLOBAL_ADMIN_ROLE;
import static com.mw.planner.security.SecurityConfiguration.SYSTEM_ADMIN_ROLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mw.planner.exception.auth.JwtNoSubscriptionsException;
import com.mw.planner.exception.auth.JwtSubscriptionMismatchException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@DisplayName("CustomJwtAuthenticationConverter")
class CustomJwtAuthenticationConverterTest {

  private static final String PRODUCT_ID = "mw-planner";

  private CustomJwtAuthenticationConverter converter;

  @BeforeEach
  void setUp() {
    converter = new CustomJwtAuthenticationConverter(PRODUCT_ID);
  }

  private static Jwt buildJwt(Map<String, Object> claims) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600));
    if (claims != null) {
      claims.forEach(builder::claim);
    }
    return builder.build();
  }

  @Nested
  @DisplayName("convert - subscription validation")
  class SubscriptionValidation {

    @Test
    @DisplayName("throws JwtNoSubscriptionsException when subscriptions claim is null")
    void convert_WhenSubscriptionsNull_ThrowsJwtNoSubscriptionsException() {
      Jwt jwt = buildJwt(Map.of());

      assertThatThrownBy(() -> converter.convert(jwt))
          .isInstanceOf(JwtNoSubscriptionsException.class)
          .hasMessageContaining(PRODUCT_ID)
          .hasMessageContaining("no subscriptions");
    }

    @Test
    @DisplayName("throws JwtNoSubscriptionsException when subscriptions claim is empty list")
    void convert_WhenSubscriptionsEmpty_ThrowsJwtNoSubscriptionsException() {
      Jwt jwt = buildJwt(Map.of(CLAIM_SUBSCRIPTIONS, List.<String>of()));

      assertThatThrownBy(() -> converter.convert(jwt))
          .isInstanceOf(JwtNoSubscriptionsException.class)
          .hasMessageContaining(PRODUCT_ID);
    }

    @Test
    @DisplayName("throws JwtSubscriptionMismatchException when productId not in subscriptions")
    void convert_WhenProductIdNotInSubscriptions_ThrowsJwtSubscriptionMismatchException() {
      List<String> subscriptions = List.of("other-product", "another-product");
      Jwt jwt = buildJwt(Map.of(CLAIM_SUBSCRIPTIONS, subscriptions));

      assertThatThrownBy(() -> converter.convert(jwt))
          .isInstanceOf(JwtSubscriptionMismatchException.class)
          .hasMessageContaining(PRODUCT_ID)
          .hasMessageContaining("other-product");
    }

    @Test
    @DisplayName("returns JwtAuthenticationToken when productId is in subscriptions")
    void convert_WhenProductIdInSubscriptions_ReturnsJwtAuthenticationToken() {
      Jwt jwt =
          buildJwt(
              Map.of(CLAIM_SUBSCRIPTIONS, List.of("other-product", PRODUCT_ID, "another-product")));

      AbstractAuthenticationToken result = converter.convert(jwt);

      assertThat(result).isInstanceOf(JwtAuthenticationToken.class);
      assertThat(result.getPrincipal()).isSameAs(jwt);
    }

    @Test
    @DisplayName("bypasses subscription check when is_global_admin is true")
    void convert_WhenGlobalAdmin_BypassesSubscriptionCheck() {
      // Global admin with no subscriptions should succeed
      Jwt jwt = buildJwt(Map.of(CLAIM_IS_GLOBAL_ADMIN, true));

      AbstractAuthenticationToken result = converter.convert(jwt);

      assertThat(result).isInstanceOf(JwtAuthenticationToken.class);
      assertThat(result.getPrincipal()).isSameAs(jwt);
    }

    @Test
    @DisplayName("bypasses subscription check when has_system_role is true")
    void convert_WhenSystemAdmin_BypassesSubscriptionCheck() {
      // System admin with no subscriptions should succeed
      Jwt jwt = buildJwt(Map.of(CLAIM_HAS_SYSTEM_ROLE, true));

      AbstractAuthenticationToken result = converter.convert(jwt);

      assertThat(result).isInstanceOf(JwtAuthenticationToken.class);
      assertThat(result.getPrincipal()).isSameAs(jwt);
    }

    @Test
    @DisplayName("bypasses subscription check when global admin has wrong subscriptions")
    void convert_WhenGlobalAdminWithWrongSubscriptions_Succeeds() {
      // Global admin with subscriptions that don't include this product should succeed
      Jwt jwt =
          buildJwt(
              Map.of(
                  CLAIM_IS_GLOBAL_ADMIN,
                  true,
                  CLAIM_SUBSCRIPTIONS,
                  List.of("other-product", "another-product")));

      AbstractAuthenticationToken result = converter.convert(jwt);

      assertThat(result).isInstanceOf(JwtAuthenticationToken.class);
      assertThat(result.getPrincipal()).isSameAs(jwt);
    }

    @Test
    @DisplayName("bypasses subscription check when system admin has empty subscriptions")
    void convert_WhenSystemAdminWithEmptySubscriptions_Succeeds() {
      // System admin with empty subscriptions should succeed
      Jwt jwt =
          buildJwt(Map.of(CLAIM_HAS_SYSTEM_ROLE, true, CLAIM_SUBSCRIPTIONS, List.<String>of()));

      AbstractAuthenticationToken result = converter.convert(jwt);

      assertThat(result).isInstanceOf(JwtAuthenticationToken.class);
      assertThat(result.getPrincipal()).isSameAs(jwt);
    }
  }

  @Nested
  @DisplayName("convert - authority extraction")
  class AuthorityExtraction {

    @Test
    @DisplayName("adds ROLE_GLOBAL_ADMIN when is_global_admin is true")
    void convert_WhenIsGlobalAdminTrue_AddsGlobalAdminAuthority() {
      Jwt jwt =
          buildJwt(Map.of(CLAIM_SUBSCRIPTIONS, List.of(PRODUCT_ID), CLAIM_IS_GLOBAL_ADMIN, true));

      AbstractAuthenticationToken result = converter.convert(jwt);

      List<String> authorities = getAuthorityStrings(result);
      assertThat(authorities).contains(ROLE_PREFIX + GLOBAL_ADMIN_ROLE);
    }

    @Test
    @DisplayName("does not add ROLE_GLOBAL_ADMIN when is_global_admin is false")
    void convert_WhenIsGlobalAdminFalse_DoesNotAddGlobalAdminAuthority() {
      Jwt jwt =
          buildJwt(Map.of(CLAIM_SUBSCRIPTIONS, List.of(PRODUCT_ID), CLAIM_IS_GLOBAL_ADMIN, false));

      AbstractAuthenticationToken result = converter.convert(jwt);

      List<String> authorities = getAuthorityStrings(result);
      assertThat(authorities).isNotNull();
      assertThat(authorities).doesNotContain(ROLE_PREFIX + GLOBAL_ADMIN_ROLE);
    }

    @Test
    @DisplayName("adds ROLE_SYSTEM_ADMIN when has_system_role is true")
    void convert_WhenHasSystemRoleTrue_AddsSystemAdminAuthority() {
      Jwt jwt =
          buildJwt(Map.of(CLAIM_SUBSCRIPTIONS, List.of(PRODUCT_ID), CLAIM_HAS_SYSTEM_ROLE, true));

      AbstractAuthenticationToken result = converter.convert(jwt);

      List<String> authorities = getAuthorityStrings(result);
      assertThat(authorities).contains(ROLE_PREFIX + SYSTEM_ADMIN_ROLE);
    }

    @Test
    @DisplayName("adds system_permissions with ROLE_ prefix")
    void convert_WhenSystemPermissionsPresent_AddsWithRolePrefix() {
      List<String> systemPermissions = List.of("perm:read", "perm:write");
      Jwt jwt =
          buildJwt(
              Map.of(
                  CLAIM_SUBSCRIPTIONS,
                  List.of(PRODUCT_ID),
                  CLAIM_SYSTEM_PERMISSIONS,
                  systemPermissions));

      AbstractAuthenticationToken result = converter.convert(jwt);

      List<String> authorities = getAuthorityStrings(result);
      assertThat(authorities).contains(ROLE_PREFIX + "perm:read", ROLE_PREFIX + "perm:write");
    }

    @Test
    @DisplayName("expands system permission wildcard *:*:* to all domain:resource:action")
    void convert_WhenSystemPermissionsWildcard_ExpandsToAllPermissions() {
      List<String> systemPermissions = List.of("*:*:*");
      Jwt jwt =
          buildJwt(
              Map.of(
                  CLAIM_IS_GLOBAL_ADMIN,
                  true, // Bypass subscription check
                  CLAIM_SYSTEM_PERMISSIONS,
                  systemPermissions));

      AbstractAuthenticationToken result = converter.convert(jwt);

      List<String> authorities = getAuthorityStrings(result);
      // Verify wildcard literal is added
      assertThat(authorities).contains(ROLE_PREFIX + "*:*:*");
      // Verify expansion to specific permissions
      assertThat(authorities)
          .contains(
              ROLE_PREFIX + "planner:plans:create",
              ROLE_PREFIX + "camp:campaigns:create",
              ROLE_PREFIX + "iam:users:read");
    }

    @Test
    @DisplayName("adds primary company permissions with ROLE_ prefix and expands domain:resource:*")
    void convert_WhenPrimaryCompanyPermissions_AddsAndExpandsDomainResourceWildcard() {
      String primaryCompanyId = "company-123";
      Map<String, List<String>> permissions = Map.of(primaryCompanyId, List.of("camp:campaigns:*"));
      Jwt jwt =
          buildJwt(
              Map.of(
                  CLAIM_SUBSCRIPTIONS,
                  List.of(PRODUCT_ID),
                  CLAIM_PRIMARY_COMPANY_ID,
                  primaryCompanyId,
                  CLAIM_PERMISSIONS,
                  permissions));

      AbstractAuthenticationToken result = converter.convert(jwt);

      List<String> authorities = getAuthorityStrings(result);
      assertThat(authorities).contains(ROLE_PREFIX + "camp:campaigns:*");
      assertThat(authorities)
          .contains(
              ROLE_PREFIX + "camp:campaigns:read",
              ROLE_PREFIX + "camp:campaigns:create",
              ROLE_PREFIX + "camp:campaigns:update",
              ROLE_PREFIX + "camp:campaigns:delete");
    }

    @Test
    @DisplayName("uses only primary company permissions, not other companies")
    void convert_WhenPermissionsForMultipleCompanies_AddsOnlyPrimaryCompanyAuthorities() {
      Map<String, List<String>> permissions =
          Map.of(
              "other-company",
              List.of("camp:campaigns:delete"),
              "company-123",
              List.of("camp:campaigns:read"));
      Jwt jwt =
          buildJwt(
              Map.of(
                  CLAIM_SUBSCRIPTIONS,
                  List.of(PRODUCT_ID),
                  CLAIM_PRIMARY_COMPANY_ID,
                  "company-123",
                  CLAIM_PERMISSIONS,
                  permissions));

      AbstractAuthenticationToken result = converter.convert(jwt);

      List<String> authorities = getAuthorityStrings(result);
      assertThat(authorities).contains(ROLE_PREFIX + "camp:campaigns:read");
      assertThat(authorities).doesNotContain(ROLE_PREFIX + "camp:campaigns:delete");
      assertThat(authorities).hasSize(1);
    }
  }

  @Nested
  @DisplayName("convert - permission expansion")
  class PermissionExpansion {

    @Test
    @DisplayName("expands domain:*:* to domain:resource:action for known resources")
    void convert_WhenDomainWildcardWildcard_ExpandsToKnownResourcesAndCrud() {
      String primaryCompanyId = "company-123";
      Map<String, List<String>> permissions = Map.of(primaryCompanyId, List.of("camp:*:*"));
      Jwt jwt =
          buildJwt(
              Map.of(
                  CLAIM_SUBSCRIPTIONS,
                  List.of(PRODUCT_ID),
                  CLAIM_PRIMARY_COMPANY_ID,
                  primaryCompanyId,
                  CLAIM_PERMISSIONS,
                  permissions));

      AbstractAuthenticationToken result = converter.convert(jwt);

      List<String> authorities = getAuthorityStrings(result);
      assertThat(authorities).contains(ROLE_PREFIX + "camp:*:*");
      assertThat(authorities).contains(ROLE_PREFIX + "camp:campaigns:read");
      assertThat(authorities).contains(ROLE_PREFIX + "camp:campaigns:create");
      assertThat(authorities).contains(ROLE_PREFIX + "camp:creatives:read");
      assertThat(authorities).contains(ROLE_PREFIX + "camp:schedules:delete");
    }

    @Test
    @DisplayName("expands domain:*:action to domain:resource:action for known resources")
    void convert_WhenDomainWildcardAction_ExpandsToKnownResources() {
      String primaryCompanyId = "company-123";
      Map<String, List<String>> permissions = Map.of(primaryCompanyId, List.of("iam:*:read"));
      Jwt jwt =
          buildJwt(
              Map.of(
                  CLAIM_SUBSCRIPTIONS,
                  List.of(PRODUCT_ID),
                  CLAIM_PRIMARY_COMPANY_ID,
                  primaryCompanyId,
                  CLAIM_PERMISSIONS,
                  permissions));

      AbstractAuthenticationToken result = converter.convert(jwt);

      List<String> authorities = getAuthorityStrings(result);
      assertThat(authorities).contains(ROLE_PREFIX + "iam:*:read");
      assertThat(authorities).contains(ROLE_PREFIX + "iam:users:read");
      assertThat(authorities).contains(ROLE_PREFIX + "iam:companies:read");
      assertThat(authorities).contains(ROLE_PREFIX + "iam:products:read");
    }

    @Test
    @DisplayName("adds literal permission for domain:resource:action (no wildcard)")
    void convert_WhenExactPermission_AddsLiteralOnly() {
      String primaryCompanyId = "company-123";
      Map<String, List<String>> permissions =
          Map.of(primaryCompanyId, List.of("planner:plans:read"));
      Jwt jwt =
          buildJwt(
              Map.of(
                  CLAIM_SUBSCRIPTIONS,
                  List.of(PRODUCT_ID),
                  CLAIM_PRIMARY_COMPANY_ID,
                  primaryCompanyId,
                  CLAIM_PERMISSIONS,
                  permissions));

      AbstractAuthenticationToken result = converter.convert(jwt);

      List<String> authorities = getAuthorityStrings(result);
      assertThat(authorities).containsExactly(ROLE_PREFIX + "planner:plans:read");
    }

    @Test
    @DisplayName("domain with no known resources (meas) adds only literal for domain:*:*")
    void convert_WhenDomainWithNoKnownResources_AddsOnlyLiteral() {
      String primaryCompanyId = "company-123";
      Map<String, List<String>> permissions = Map.of(primaryCompanyId, List.of("meas:*:*"));
      Jwt jwt =
          buildJwt(
              Map.of(
                  CLAIM_SUBSCRIPTIONS,
                  List.of(PRODUCT_ID),
                  CLAIM_PRIMARY_COMPANY_ID,
                  primaryCompanyId,
                  CLAIM_PERMISSIONS,
                  permissions));

      AbstractAuthenticationToken result = converter.convert(jwt);

      List<String> authorities = getAuthorityStrings(result);
      assertThat(authorities).contains(ROLE_PREFIX + "meas:*:*");
      assertThat(authorities).hasSize(1);
    }
  }

  @Nested
  @DisplayName("convert - combined authorities")
  class CombinedAuthorities {

    @Test
    @DisplayName("combines global admin, system admin, system permissions and company permissions")
    void convert_WhenMultipleAuthoritySources_CombinesAll() {
      String primaryCompanyId = "company-123";
      Map<String, List<String>> permissions =
          Map.of(primaryCompanyId, List.of("planner:plans:read"));
      Jwt jwt =
          buildJwt(
              Map.of(
                  CLAIM_SUBSCRIPTIONS,
                  List.of(PRODUCT_ID),
                  CLAIM_IS_GLOBAL_ADMIN,
                  true,
                  CLAIM_HAS_SYSTEM_ROLE,
                  true,
                  CLAIM_SYSTEM_PERMISSIONS,
                  List.of("sys:perm"),
                  CLAIM_PRIMARY_COMPANY_ID,
                  primaryCompanyId,
                  CLAIM_PERMISSIONS,
                  permissions));

      AbstractAuthenticationToken result = converter.convert(jwt);

      List<String> authorities = getAuthorityStrings(result);
      assertThat(authorities).contains(ROLE_PREFIX + GLOBAL_ADMIN_ROLE);
      assertThat(authorities).contains(ROLE_PREFIX + SYSTEM_ADMIN_ROLE);
      assertThat(authorities).contains(ROLE_PREFIX + "sys:perm");
      assertThat(authorities).contains(ROLE_PREFIX + "planner:plans:read");
    }
  }

  private static List<String> getAuthorityStrings(AbstractAuthenticationToken token) {
    return token.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toList());
  }
}
