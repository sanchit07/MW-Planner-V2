package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.mw.planner.dto.IamUserContext;
import com.mw.planner.dto.UserInfoResponse;
import com.mw.planner.dto.UserResponseDTO;
import com.mw.planner.exception.auth.AuthenticationException;
import com.mw.planner.exception.user.UserNotFoundException;
import com.mw.planner.exception.user.UserValidationException;
import com.mw.planner.service.iam.IamCompanyApiClient;
import com.mw.planner.service.iam.IamUserServiceApiClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private IamUserServiceApiClient iamUserService;
  @Mock private IamCompanyApiClient iamCompanyApiClient;
  @Mock private SecurityContextService securityContextService;

  @InjectMocks private UserService userService;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    ReflectionTestUtils.setField(userService, "userService", userService);
  }

  private void authWithPrimaryCompany(String primary) {
    Jwt jwt =
        Jwt.withTokenValue("t")
            .header("alg", "none")
            .subject("u1")
            .claim("primary_company_id", primary)
            .build();
    lenient()
        .when(securityContextService.getCurrentAuthentication())
        .thenReturn(new JwtAuthenticationToken(jwt));
  }

  private void requestWithHeader(String name, String value) {
    org.springframework.mock.web.MockHttpServletRequest req =
        new org.springframework.mock.web.MockHttpServletRequest();
    if (name != null) {
      req.addHeader(name, value);
    }
    org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
        new org.springframework.web.context.request.ServletRequestAttributes(req));
  }

  @org.junit.jupiter.api.AfterEach
  void resetRequestContext() {
    org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void getActingCompanyId_LegacyTenantHeader_IsIgnored() {
    // Only X-Company-Id may switch the acting company. The legacy X-Tenant-Id header must
    // NOT be honored: CompanyScopedAuthoritiesFilter rescopes authorities on X-Company-Id
    // only, so accepting X-Tenant-Id for data scoping would let permissions and data
    // attribution diverge for dual-member users.
    authWithPrimaryCompany("primary-co");
    requestWithHeader("X-Tenant-Id", "secondary-co");

    assertThat(userService.getActingCompanyId()).isEqualTo("primary-co");
    verifyNoInteractions(iamCompanyApiClient);
  }

  @Test
  void assertCanActForCompany_OtherMembershipCompany_IsRejected() {
    // A company the user merely holds ANOTHER membership in does not qualify — only the
    // resolved acting company (or global admin) does, because @PreAuthorize already ran
    // against the acting company's authorities.
    authWithPrimaryCompany("primary-co");
    requestWithHeader(null, null);

    assertThatThrownBy(() -> userService.assertCanActForCompany("secondary-co"))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    // Membership lookups are irrelevant to this decision and must not be consulted.
    verifyNoInteractions(iamCompanyApiClient);
  }

  @Test
  void assertCanActForCompany_ActingCompany_IsAllowed() {
    authWithPrimaryCompany("primary-co");
    requestWithHeader("X-Company-Id", "primary-co");

    userService.assertCanActForCompany("primary-co"); // no exception
  }

  @Test
  void initIamUserContext_WithEmptyUsername_ThrowsUserValidationException() {
    assertThatThrownBy(() -> userService.initIamUserContext("", "token"))
        .isInstanceOf(UserValidationException.class)
        .hasMessageContaining("username");
  }

  @Test
  void initIamUserContext_WithEmptyToken_ThrowsUserValidationException() {
    assertThatThrownBy(() -> userService.initIamUserContext("user@example.com", ""))
        .isInstanceOf(UserValidationException.class)
        .hasMessageContaining("token");
  }

  @Test
  void initIamUserContext_WithValidArgs_ReturnsIamUserContext() {
    UserInfoResponse.UserInfoData data =
        UserInfoResponse.UserInfoData.builder()
            .id("id1")
            .userId("user1")
            .sub("sub1")
            .email("user@example.com")
            .username("user1")
            .build();
    UserInfoResponse userInfoResponse = UserInfoResponse.builder().success(true).data(data).build();

    when(iamUserService.getUserInfo("bearer-token")).thenReturn(userInfoResponse);

    IamUserContext result = userService.initIamUserContext("user@example.com", "bearer-token");

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id1");
    verify(iamUserService).getUserInfo(eq("bearer-token"));
  }

  @Test
  void getIamUserContext_WhenSecurityContextThrows_ThrowsUserNotFoundException() {
    when(securityContextService.getCurrentUsername())
        .thenThrow(
            new AuthenticationException(com.mw.planner.enums.ErrorCode.UNAUTHORIZED, "No user"));

    assertThatThrownBy(userService::getIamUserContext)
        .isInstanceOf(UserNotFoundException.class)
        .hasMessageContaining("Unable to get IAM user context");
  }

  @Test
  void getUserById_WithEmptyUserId_ThrowsUserValidationException() {
    assertThatThrownBy(() -> userService.getUserById(""))
        .isInstanceOf(UserValidationException.class)
        .hasMessageContaining("userId");
  }

  @Test
  void getUserById_WithValidUserId_ReturnsUserResponse() {
    UserResponseDTO userResponse = new UserResponseDTO();
    userResponse.setUserId("user1");
    userResponse.setUsername("user1");
    when(securityContextService.getBearerToken()).thenReturn("token");
    when(iamUserService.getUserById(eq("user1"), eq("token"))).thenReturn(userResponse);

    UserResponseDTO result = userService.getUserById("user1");

    assertThat(result).isNotNull();
    assertThat(result.getUserId()).isEqualTo("user1");
    verify(iamUserService).getUserById(eq("user1"), eq("token"));
  }

  @Test
  void getPrimaryCompanyId_WhenJwtPresent_ReturnsClaim() {
    Jwt jwt = mock(Jwt.class);
    when(jwt.getClaimAsString(com.mw.planner.constants.JwtConstants.CLAIM_PRIMARY_COMPANY_ID))
        .thenReturn("company-123");
    JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, null);
    when(securityContextService.getCurrentAuthentication()).thenReturn(auth);

    String result = userService.getPrimaryCompanyId();

    assertThat(result).isEqualTo("company-123");
  }

  @Test
  void getPrimaryCompanyId_WhenNotJwt_ReturnsNull() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("user", null));

    String result = userService.getPrimaryCompanyId();

    assertThat(result).isNull();
  }

  @Test
  void extractUserName_WithFirstAndLastName_ReturnsConcatenated() {
    UserResponseDTO user = new UserResponseDTO();
    user.setFirstName("John");
    user.setLastName("Doe");

    String result = UserService.extractUserName(user);

    assertThat(result).isEqualTo("John Doe");
  }

  @Test
  void extractUserRole_WhenCurrentCompanyHasRole_ReturnsRoleName() {
    UserResponseDTO user = new UserResponseDTO();
    UserResponseDTO.CurrentCompanyDTO currentCompany = new UserResponseDTO.CurrentCompanyDTO();
    currentCompany.setRoleName("Admin");
    user.setCurrentCompany(currentCompany);

    String result = UserService.extractUserRole(user);

    assertThat(result).isEqualTo("Admin");
  }

  @Test
  void extractUserRole_WhenNoCurrentCompany_ReturnsAgency() {
    UserResponseDTO user = new UserResponseDTO();

    String result = UserService.extractUserRole(user);

    assertThat(result).isEqualTo("Agency");
  }

  // ========== isTenantOfCompany Tests ==========

  @Test
  void isTenantOfCompany_WhenCompanyIdIsNull_ReturnsFalse() {
    assertThat(userService.isTenantOfCompany(null)).isFalse();
  }

  @Test
  void isTenantOfCompany_WhenCompanyIdIsBlank_ReturnsFalse() {
    assertThat(userService.isTenantOfCompany("  ")).isFalse();
  }

  @Test
  void isTenantOfCompany_WhenMembershipsIsNull_ReturnsFalse() {
    UserInfoResponse.UserInfoData data =
        UserInfoResponse.UserInfoData.builder()
            .id("id1")
            .userId("user1")
            .sub("user1")
            .username("user1")
            .memberships(null)
            .build();
    UserInfoResponse response = UserInfoResponse.builder().success(true).data(data).build();
    when(securityContextService.getCurrentUsername()).thenReturn("user@example.com");
    when(securityContextService.getBearerToken()).thenReturn("token");
    when(iamUserService.getUserInfo("token")).thenReturn(response);

    assertThat(userService.isTenantOfCompany("company-abc")).isFalse();
  }

  @Test
  void isTenantOfCompany_WhenCompanyIdInMemberships_ReturnsTrue() {
    UserInfoResponse.Membership membership =
        UserInfoResponse.Membership.builder().companyId("company-abc").isActive(true).build();
    UserInfoResponse.UserInfoData data =
        UserInfoResponse.UserInfoData.builder()
            .id("id1")
            .userId("user1")
            .sub("user1")
            .username("user1")
            .memberships(List.of(membership))
            .build();
    UserInfoResponse response = UserInfoResponse.builder().success(true).data(data).build();
    when(securityContextService.getCurrentUsername()).thenReturn("user@example.com");
    when(securityContextService.getBearerToken()).thenReturn("token");
    when(iamUserService.getUserInfo("token")).thenReturn(response);

    assertThat(userService.isTenantOfCompany("company-abc")).isTrue();
  }

  @Test
  void isTenantOfCompany_WhenMembershipIsInactive_ReturnsFalse() {
    UserInfoResponse.Membership membership =
        UserInfoResponse.Membership.builder().companyId("company-abc").isActive(false).build();
    UserInfoResponse.UserInfoData data =
        UserInfoResponse.UserInfoData.builder()
            .id("id1")
            .userId("user1")
            .sub("user1")
            .username("user1")
            .memberships(List.of(membership))
            .build();
    UserInfoResponse response = UserInfoResponse.builder().success(true).data(data).build();
    when(securityContextService.getCurrentUsername()).thenReturn("user@example.com");
    when(securityContextService.getBearerToken()).thenReturn("token");
    when(iamUserService.getUserInfo("token")).thenReturn(response);

    assertThat(userService.isTenantOfCompany("company-abc")).isFalse();
  }

  @Test
  void isTenantOfCompany_WhenMembershipActiveFlagMissing_ReturnsFalse() {
    UserInfoResponse.Membership membership =
        UserInfoResponse.Membership.builder().companyId("company-abc").build();
    UserInfoResponse.UserInfoData data =
        UserInfoResponse.UserInfoData.builder()
            .id("id1")
            .userId("user1")
            .sub("user1")
            .username("user1")
            .memberships(List.of(membership))
            .build();
    UserInfoResponse response = UserInfoResponse.builder().success(true).data(data).build();
    when(securityContextService.getCurrentUsername()).thenReturn("user@example.com");
    when(securityContextService.getBearerToken()).thenReturn("token");
    when(iamUserService.getUserInfo("token")).thenReturn(response);

    assertThat(userService.isTenantOfCompany("company-abc")).isFalse();
  }

  @Test
  void isTenantOfCompany_WhenCompanyIdNotInMemberships_ReturnsFalse() {
    UserInfoResponse.Membership membership =
        UserInfoResponse.Membership.builder().companyId("company-xyz").build();
    UserInfoResponse.UserInfoData data =
        UserInfoResponse.UserInfoData.builder()
            .id("id1")
            .userId("user1")
            .sub("user1")
            .username("user1")
            .memberships(List.of(membership))
            .build();
    UserInfoResponse response = UserInfoResponse.builder().success(true).data(data).build();
    when(securityContextService.getCurrentUsername()).thenReturn("user@example.com");
    when(securityContextService.getBearerToken()).thenReturn("token");
    when(iamUserService.getUserInfo("token")).thenReturn(response);

    assertThat(userService.isTenantOfCompany("company-abc")).isFalse();
  }

  @Test
  void isTenantOfCompany_WhenCompanyIdInChildCompanies_ReturnsTrue() {
    UserInfoResponse.ChildCompany childCompany =
        UserInfoResponse.ChildCompany.builder().id("child-company-abc").build();
    UserInfoResponse.ChildCompanies childCompaniesPage =
        UserInfoResponse.ChildCompanies.builder().items(List.of(childCompany)).build();
    UserInfoResponse.CurrentCompany currentCompany =
        UserInfoResponse.CurrentCompany.builder()
            .id("parent-company")
            .childCompanies(childCompaniesPage)
            .build();
    UserInfoResponse.UserInfoData data =
        UserInfoResponse.UserInfoData.builder()
            .id("id1")
            .userId("user1")
            .sub("user1")
            .username("user1")
            .memberships(
                List.of(UserInfoResponse.Membership.builder().companyId("parent-company").build()))
            .currentCompany(currentCompany)
            .build();
    UserInfoResponse response = UserInfoResponse.builder().success(true).data(data).build();
    when(securityContextService.getCurrentUsername()).thenReturn("user@example.com");
    when(securityContextService.getBearerToken()).thenReturn("token");
    when(iamUserService.getUserInfo("token")).thenReturn(response);
    when(iamCompanyApiClient.getCompanyChildren("parent-company", "token")).thenReturn(List.of());

    assertThat(userService.isTenantOfCompany("child-company-abc")).isTrue();
  }

  @Test
  void isTenantOfCompany_WhenCompanyIdNotInMembershipsOrChildCompanies_ReturnsFalse() {
    UserInfoResponse.ChildCompany childCompany =
        UserInfoResponse.ChildCompany.builder().id("child-company-xyz").build();
    UserInfoResponse.ChildCompanies childCompaniesPage =
        UserInfoResponse.ChildCompanies.builder().items(List.of(childCompany)).build();
    UserInfoResponse.CurrentCompany currentCompany =
        UserInfoResponse.CurrentCompany.builder()
            .id("parent-company")
            .childCompanies(childCompaniesPage)
            .build();
    UserInfoResponse.UserInfoData data =
        UserInfoResponse.UserInfoData.builder()
            .id("id1")
            .userId("user1")
            .sub("user1")
            .username("user1")
            .memberships(
                List.of(UserInfoResponse.Membership.builder().companyId("parent-company").build()))
            .currentCompany(currentCompany)
            .build();
    UserInfoResponse response = UserInfoResponse.builder().success(true).data(data).build();
    when(securityContextService.getCurrentUsername()).thenReturn("user@example.com");
    when(securityContextService.getBearerToken()).thenReturn("token");
    when(iamUserService.getUserInfo("token")).thenReturn(response);
    when(iamCompanyApiClient.getCompanyChildren("parent-company", "token")).thenReturn(List.of());

    assertThat(userService.isTenantOfCompany("company-abc")).isFalse();
  }
}
