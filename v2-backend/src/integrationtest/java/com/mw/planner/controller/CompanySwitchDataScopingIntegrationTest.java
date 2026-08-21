package com.mw.planner.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.TestcontainersConfiguration;
import com.mw.planner.config.MwPlannerProperties;
import com.mw.planner.domain.Campaign;
import com.mw.planner.dto.UserInfoResponse;
import com.mw.planner.dto.UserResponseDTO;
import com.mw.planner.repository.CampaignRepository;
import com.mw.planner.service.iam.IamCompanyApiClient;
import com.mw.planner.service.iam.IamUserServiceApiClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Regression tests for DATA scoping after a company switch — the counterpart to {@link
 * CompanySwitchPermissionIntegrationTest}, which covers authority (permission) rescoping.
 *
 * <p>Here the REAL {@code UserService} runs (only the IAM API clients and the {@link JwtDecoder}
 * are mocked), so the shared acting-tenant resolver is exercised end-to-end:
 *
 * <ul>
 *   <li>list endpoints scope records to the switched-to company (X-Company-Id), not the primary
 *   <li>the legacy {@code X-Tenant-Id} header resolves through the same resolver
 *   <li>writes stamp the acting company, so records created after a switch belong to it
 *   <li>a company the JWT grants permissions for but the user is NOT a member of never becomes the
 *       acting company — data stays scoped to the primary company
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CompanySwitchDataScopingIntegrationTest {

  private static final String TOKEN = "company-switch-scope-token";
  private static final String USER = "scope-user";
  private static final String COMPANY_A = "company-alpha"; // primary
  private static final String COMPANY_B = "company-beta"; // secondary membership
  private static final String COMPANY_C = "company-gamma"; // JWT perms but NO membership

  @Autowired private MockMvc mockMvc;
  @Autowired private CampaignRepository campaignRepository;
  @Autowired private MwPlannerProperties mwPlannerProperties;
  @Autowired private CacheManager cacheManager;

  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private IamUserServiceApiClient iamUserService;
  @MockitoBean private IamCompanyApiClient iamCompanyApiClient;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    Jwt jwt =
        Jwt.withTokenValue(TOKEN)
            .header("alg", "none")
            .subject(USER)
            .claim("subscriptions", List.of(mwPlannerProperties.getIam().getProductId()))
            .claim("primary_company_id", COMPANY_A)
            .claim(
                "permissions",
                Map.of(
                    COMPANY_A, List.of("planner:plans:*"),
                    COMPANY_B, List.of("planner:plans:*"),
                    COMPANY_C, List.of("planner:plans:*")))
            .build();
    when(jwtDecoder.decode(TOKEN)).thenReturn(jwt);

    // Real UserService: IAM userinfo says the active (primary) company is A; the user is a
    // member of A and B only. C is deliberately absent from memberships.
    UserInfoResponse userInfo =
        UserInfoResponse.builder()
            .success(true)
            .data(
                UserInfoResponse.UserInfoData.builder()
                    .id(USER)
                    .username(USER)
                    .email(USER + "@example.com")
                    .currentCompany(UserInfoResponse.CurrentCompany.builder().id(COMPANY_A).build())
                    .memberships(memberships())
                    .build())
            .build();
    when(iamUserService.getUserInfo(anyString())).thenReturn(userInfo);
    when(iamUserService.getUserMeCompanies(anyString())).thenReturn(memberships());
    when(iamUserService.getUserById(anyString(), anyString()))
        .thenReturn(UserResponseDTO.builder().id(USER).build());
    when(iamCompanyApiClient.getCompanyChildren(anyString(), anyString())).thenReturn(List.of());

    // The IAM context is cached per username in Redis — clear so each test sees fresh mocks.
    if (cacheManager.getCache("iamUserContext") != null) {
      cacheManager.getCache("iamUserContext").clear();
    }
    if (cacheManager.getCache("iamCompanyChildren") != null) {
      cacheManager.getCache("iamCompanyChildren").clear();
    }

    campaignRepository.deleteAll();
    seedCampaign("c-alpha-1", "Alpha Plan One", COMPANY_A);
    seedCampaign("c-alpha-2", "Alpha Plan Two", COMPANY_A);
    seedCampaign("c-beta-1", "Beta Plan One", COMPANY_B);
  }

  private static List<UserInfoResponse.Membership> memberships() {
    // COMPANY_C is present but INACTIVE — IAM keeps revoked memberships in the list, so the
    // acting-tenant validation must reject it just like a missing membership.
    return List.of(
        UserInfoResponse.Membership.builder().companyId(COMPANY_A).isActive(true).build(),
        UserInfoResponse.Membership.builder().companyId(COMPANY_B).isActive(true).build(),
        UserInfoResponse.Membership.builder().companyId(COMPANY_C).isActive(false).build());
  }

  private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
    return builder.header("Authorization", "Bearer " + TOKEN);
  }

  private void seedCampaign(String id, String name, String companyId) {
    Campaign campaign = new Campaign();
    campaign.setId(id);
    campaign.setName(name);
    campaign.setStatus(Campaign.Status.DRAFT);
    campaign.setCompanyId(companyId);
    campaign.setUserId(USER);
    campaignRepository.save(campaign);
  }

  private List<String> listCampaignIds(MockHttpServletRequestBuilder builder) throws Exception {
    MvcResult result = mockMvc.perform(builder).andExpect(status().isOk()).andReturn();
    JsonNode content =
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("content");
    return content.findValuesAsText("id");
  }

  // --- 1. Default (no header): data scoped to the primary company ---

  @Test
  @DisplayName("No acting-tenant header: list is scoped to the primary company")
  void defaultListScopedToPrimaryCompany() throws Exception {
    List<String> ids = listCampaignIds(authed(get("/api/v1/campaigns")));
    assertThat(ids).containsExactlyInAnyOrder("c-alpha-1", "c-alpha-2");
  }

  // --- 2. X-Company-Id switch: data follows the switched-to company ---

  @Test
  @DisplayName("X-Company-Id switch: list shows only the switched-to company's plans")
  void companyIdHeaderScopesListToActingCompany() throws Exception {
    List<String> ids =
        listCampaignIds(authed(get("/api/v1/campaigns")).header("X-Company-Id", COMPANY_B));
    assertThat(ids).containsExactly("c-beta-1");
  }

  // --- 3. Legacy X-Tenant-Id resolves through the same shared resolver ---

  @Test
  @DisplayName("Legacy X-Tenant-Id header: same acting-tenant resolution as X-Company-Id")
  void tenantIdHeaderScopesListToActingCompany() throws Exception {
    List<String> ids =
        listCampaignIds(authed(get("/api/v1/campaigns")).header("X-Tenant-Id", COMPANY_B));
    assertThat(ids).containsExactly("c-beta-1");
  }

  // --- 4. Writes stamp the acting company ---

  @Test
  @DisplayName("Create after switch: new plan belongs to the switched-to company")
  void createAfterSwitchStampsActingCompany() throws Exception {
    mockMvc
        .perform(
            authed(post("/api/v1/campaigns"))
                .header("X-Company-Id", COMPANY_B)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"Switched Plan\", \"clientType\": \"DIRECT_ADVERTISER\","
                        + " \"startDate\": \"2030-01-01\", \"endDate\": \"2030-02-01\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.companyId").value(COMPANY_B));

    assertThat(campaignRepository.findAll())
        .filteredOn(c -> "Switched Plan".equals(c.getName()))
        .singleElement()
        .satisfies(c -> assertThat(c.getCompanyId()).isEqualTo(COMPANY_B));
  }

  // --- 5. Non-member company: fail closed, never acts on any company's data ---

  @Test
  @DisplayName("Non-member company id: request fails closed (403), nothing is read or written")
  void nonMemberCompanyHeaderFailsClosed() throws Exception {
    // The JWT still carries a permissions entry for COMPANY_C (e.g. a stale claim after the
    // membership was revoked), but IAM memberships exclude it. Authority rescoping and data
    // scoping share the same membership validation, so the request must fail closed instead of
    // exercising COMPANY_C's permissions against primary-company data.
    mockMvc
        .perform(authed(get("/api/v1/campaigns")).header("X-Company-Id", COMPANY_C))
        .andExpect(status().isForbidden());

    // Legacy X-Tenant-Id header goes through the same membership-validated resolver.
    mockMvc
        .perform(authed(get("/api/v1/campaigns")).header("X-Tenant-Id", COMPANY_C))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            authed(post("/api/v1/campaigns"))
                .header("X-Company-Id", COMPANY_C)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"Gamma Plan\", \"clientType\": \"DIRECT_ADVERTISER\","
                        + " \"startDate\": \"2030-01-01\", \"endDate\": \"2030-02-01\"}"))
        .andExpect(status().isForbidden());

    assertThat(campaignRepository.findAll()).noneMatch(c -> "Gamma Plan".equals(c.getName()));
  }

  // --- 6. Non-member company with BROADER permissions than primary cannot escalate ---

  @Test
  @DisplayName("Non-member company with write perms cannot be used to write primary data")
  void nonMemberCompanyPermissionsCannotEscalateOverPrimary() throws Exception {
    // Read-only primary, full permissions on non-member COMPANY_C: pointing the acting-tenant
    // header at C must NOT let the write proceed anywhere (neither on C nor on primary data).
    String limitedToken = "company-switch-scope-token-limited";
    Jwt limitedJwt =
        Jwt.withTokenValue(limitedToken)
            .header("alg", "none")
            .subject(USER)
            .claim("subscriptions", List.of(mwPlannerProperties.getIam().getProductId()))
            .claim("primary_company_id", COMPANY_A)
            .claim(
                "permissions",
                Map.of(
                    COMPANY_A, List.of("planner:plans:read"),
                    COMPANY_C, List.of("planner:plans:*")))
            .build();
    when(jwtDecoder.decode(limitedToken)).thenReturn(limitedJwt);

    String body =
        "{\"name\": \"Escalated Plan\", \"clientType\": \"DIRECT_ADVERTISER\","
            + " \"startDate\": \"2030-01-01\", \"endDate\": \"2030-02-01\"}";

    // Baseline: the primary company really is read-only.
    mockMvc
        .perform(
            post("/api/v1/campaigns")
                .header("Authorization", "Bearer " + limitedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    // Escalation attempt via the non-member company's broader JWT permissions.
    mockMvc
        .perform(
            post("/api/v1/campaigns")
                .header("Authorization", "Bearer " + limitedToken)
                .header("X-Company-Id", COMPANY_C)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    assertThat(campaignRepository.findAll()).noneMatch(c -> "Escalated Plan".equals(c.getName()));
  }

  // --- 7. Stale JWT primary company (membership revoked) cannot be reclaimed via header ---

  @Test
  @DisplayName("Header equal to a stale JWT primary company: forbidden, no access to other data")
  void staleJwtPrimaryCompanyHeaderFailsClosed() throws Exception {
    // Stale JWT: primary_company_id names COMPANY_C with broad permissions, but current IAM
    // memberships are A and B only (C was revoked). Explicitly pointing the header at the JWT's
    // own primary company must still be membership-validated and fail closed — otherwise the
    // stale broad authorities would apply while data scoping follows the IAM active company.
    String staleToken = "company-switch-scope-token-stale";
    Jwt staleJwt =
        Jwt.withTokenValue(staleToken)
            .header("alg", "none")
            .subject(USER)
            .claim("subscriptions", List.of(mwPlannerProperties.getIam().getProductId()))
            .claim("primary_company_id", COMPANY_C)
            .claim(
                "permissions",
                Map.of(
                    COMPANY_A, List.of("planner:plans:read"),
                    COMPANY_C, List.of("planner:plans:*")))
            .build();
    when(jwtDecoder.decode(staleToken)).thenReturn(staleJwt);

    String body =
        "{\"name\": \"Stale Primary Plan\", \"clientType\": \"DIRECT_ADVERTISER\","
            + " \"startDate\": \"2030-01-01\", \"endDate\": \"2030-02-01\"}";

    mockMvc
        .perform(
            get("/api/v1/campaigns")
                .header("Authorization", "Bearer " + staleToken)
                .header("X-Company-Id", COMPANY_C))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/campaigns")
                .header("Authorization", "Bearer " + staleToken)
                .header("X-Company-Id", COMPANY_C)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    assertThat(campaignRepository.findAll())
        .noneMatch(c -> "Stale Primary Plan".equals(c.getName()));

    // No header at all: authorities must follow IAM's acting company (A, read-only in this JWT),
    // not the stale JWT primary (C, full permissions). Reads work against A's data; the write
    // permission that only exists for the revoked company must not authorize a write.
    mockMvc
        .perform(get("/api/v1/campaigns").header("Authorization", "Bearer " + staleToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/campaigns")
                .header("Authorization", "Bearer " + staleToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    assertThat(campaignRepository.findAll())
        .noneMatch(c -> "Stale Primary Plan".equals(c.getName()));
  }
}
