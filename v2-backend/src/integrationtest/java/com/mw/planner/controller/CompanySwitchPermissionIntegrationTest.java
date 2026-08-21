package com.mw.planner.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.mw.planner.TestcontainersConfiguration;
import com.mw.planner.config.MwPlannerProperties;
import com.mw.planner.domain.Campaign;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.dto.UserInfoResponse;
import com.mw.planner.repository.CampaignRepository;
import com.mw.planner.service.UserService;
import com.mw.planner.service.iam.IamUserServiceApiClient;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Regression tests for company-switch permission enforcement (Admin Console IAM path):
 *
 * <ul>
 *   <li>the JWT carries a per-company authority map; the primary company's permissions apply by
 *       default
 *   <li>{@code CompanyScopedAuthoritiesFilter} rescopes authorities when {@code X-Company-Id}
 *       differs from the primary company: a read-only secondary company gets 403 on writes but 200
 *       on reads
 *   <li>an unknown company id yields no company authorities at all (403 even on reads)
 *   <li>{@code /users/userinfo} preserves the {@code company_permissions} map end-to-end (the DTO
 *       re-serialization must not strip it)
 * </ul>
 *
 * <p>The full security filter chain is active (no {@code addFilters = false}); only the {@link
 * JwtDecoder} is mocked so a crafted JWT with a per-company permissions claim can be injected.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CompanySwitchPermissionIntegrationTest {

  private static final String TOKEN = "company-switch-test-token";
  private static final String PRIMARY_COMPANY = "company-primary";
  private static final String READONLY_COMPANY = "company-readonly";
  private static final String WRITER_COMPANY = "company-writer";
  private static final String UNKNOWN_COMPANY = "company-unknown";

  @Autowired private MockMvc mockMvc;
  @Autowired private CampaignRepository campaignRepository;

  @Autowired
  private com.mw.planner.repository.CampaignInventorySchedulesRepository
      inventorySchedulesRepository;

  @Autowired private MwPlannerProperties mwPlannerProperties;

  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private UserService userService;
  @MockitoBean private IamUserServiceApiClient iamUserService;

  @BeforeEach
  void setUp() {
    Jwt jwt =
        Jwt.withTokenValue(TOKEN)
            .header("alg", "none")
            .subject("perm-user")
            .claim("subscriptions", List.of(mwPlannerProperties.getIam().getProductId()))
            .claim("primary_company_id", PRIMARY_COMPANY)
            .claim(
                "permissions",
                Map.of(
                    PRIMARY_COMPANY,
                    List.of("planner:plans:*"),
                    READONLY_COMPANY,
                    List.of("planner:plans:read"),
                    WRITER_COMPANY,
                    List.of("planner:plans:*")))
            .build();
    when(jwtDecoder.decode(TOKEN)).thenReturn(jwt);

    IamUserContext ctx =
        IamUserContext.builder()
            .id("perm-user")
            .companyId(PRIMARY_COMPANY)
            .locale(Locale.ENGLISH)
            .build();
    when(userService.getIamUserContext()).thenReturn(ctx);
    // Membership validation shared with data scoping: only the read-only secondary company is a
    // real membership; UNKNOWN_COMPANY is not (authority rescoping must fail closed for it).
    when(userService.isTenantOfCompany(anyString())).thenReturn(false);
    when(userService.isTenantOfCompany(PRIMARY_COMPANY)).thenReturn(true);
    when(userService.isTenantOfCompany(READONLY_COMPANY)).thenReturn(true);
    // Mimic the shared acting-company resolver: an explicit header wins when it is a validated
    // membership, otherwise resolution falls back to the primary/active company.
    when(userService.getActingCompanyId())
        .thenAnswer(
            inv -> {
              String header = com.mw.planner.security.ActingTenantHeaders.fromCurrentRequest();
              return (PRIMARY_COMPANY.equals(header) || READONLY_COMPANY.equals(header))
                  ? header
                  : PRIMARY_COMPANY;
            });

    // Membership + acting-company resolution: the user is a member of both the primary
    // and the read-only secondary company (so the central ActingCompanyHeaderInterceptor
    // and getActingCompanyId admit them), but NOT of the unknown company.
    when(userService.isTenantOfCompany(PRIMARY_COMPANY)).thenReturn(true);
    when(userService.isTenantOfCompany(READONLY_COMPANY)).thenReturn(true);
    when(userService.isTenantOfCompany(WRITER_COMPANY)).thenReturn(true);
    when(userService.isTenantOfCompany(UNKNOWN_COMPANY)).thenReturn(false);
    when(userService.getPrimaryCompanyId()).thenReturn(PRIMARY_COMPANY);
    // Resolve the acting company from the request header, like the real implementation:
    // X-Company-Id when present (membership was validated by the interceptor stubs above),
    // primary company otherwise.
    when(userService.getActingCompanyId())
        .thenAnswer(
            invocation -> {
              var attrs =
                  org.springframework.web.context.request.RequestContextHolder
                      .getRequestAttributes();
              if (attrs
                  instanceof
                  org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
                String requested = servletAttrs.getRequest().getHeader("X-Company-Id");
                if (requested != null && !requested.isBlank()) {
                  return requested;
                }
              }
              return PRIMARY_COMPANY;
            });

    campaignRepository.deleteAll();
  }

  private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
    return builder.header("Authorization", "Bearer " + TOKEN);
  }

  private MockHttpServletRequestBuilder actingAs(
      MockHttpServletRequestBuilder builder, String companyId) {
    return authed(builder).header("X-Company-Id", companyId);
  }

  /** Minimal valid campaign JSON — must pass bean validation so 403s are authz, not 400s. */
  private String campaignJson(String name) {
    return "{\"name\": \""
        + name
        + "\", \"clientType\": \"DIRECT_ADVERTISER\","
        + " \"startDate\": \"2030-01-01\", \"endDate\": \"2030-02-01\"}";
  }

  private Campaign seedCampaign(String id, String name) {
    Campaign campaign = new Campaign();
    campaign.setId(id);
    campaign.setName(name);
    campaign.setStatus(Campaign.Status.DRAFT);
    campaign.setCompanyId(PRIMARY_COMPANY);
    return campaignRepository.save(campaign);
  }

  // --- 1. Primary company: full permissions apply by default ---

  @Test
  @DisplayName("Primary company (no X-Company-Id): reads and writes allowed")
  void primaryCompanyAllowsReadsAndWrites() throws Exception {
    mockMvc.perform(authed(get("/api/v1/campaigns"))).andExpect(status().isOk());
    mockMvc
        .perform(
            authed(post("/api/v1/campaigns"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(campaignJson("Primary Write Plan")))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("X-Company-Id equal to primary company: writes still allowed")
  void explicitPrimaryCompanyHeaderKeepsWrites() throws Exception {
    mockMvc
        .perform(
            actingAs(post("/api/v1/campaigns"), PRIMARY_COMPANY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(campaignJson("Explicit Primary Plan")))
        .andExpect(status().isOk());
  }

  // --- 2. Read-only secondary company: 200 on reads, 403 on writes ---

  @Test
  @DisplayName(
      "Read-only secondary company: list is allowed (scoped), but a direct read of another"
          + " company's campaign is hidden with 404")
  void readonlySecondaryCompanyCanRead() throws Exception {
    seedCampaign("c-perm-1", "Readable Plan");
    mockMvc
        .perform(actingAs(get("/api/v1/campaigns"), READONLY_COMPANY))
        .andExpect(status().isOk());
    // c-perm-1 belongs to the primary company; the acting read-only secondary company is
    // not a participant, so existence must not be revealed.
    mockMvc
        .perform(actingAs(get("/api/v1/campaigns/c-perm-1"), READONLY_COMPANY))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Shared-with secondary company: direct read of the shared campaign returns 200")
  void sharedSecondaryCompanyCanReadSharedCampaign() throws Exception {
    Campaign shared = seedCampaign("c-perm-shared", "Shared Plan");
    shared.setCompanyAccess(List.of(READONLY_COMPANY));
    campaignRepository.save(shared);

    mockMvc
        .perform(actingAs(get("/api/v1/campaigns/c-perm-shared"), READONLY_COMPANY))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "Secondary company WITH write permissions still cannot touch another company's campaign:"
          + " update/delete of an unrelated primary-company campaign return 404")
  void writerSecondaryCompanyCannotTouchUnrelatedCampaign() throws Exception {
    seedCampaign("c-perm-3", "Foreign Plan");

    mockMvc
        .perform(
            actingAs(put("/api/v1/campaigns/c-perm-3"), WRITER_COMPANY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(campaignJson("Hijacked From Writer")))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(actingAs(delete("/api/v1/campaigns/c-perm-3"), WRITER_COMPANY))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(actingAs(get("/api/v1/campaigns/c-perm-3"), WRITER_COMPANY))
        .andExpect(status().isNotFound());

    Campaign untouched = campaignRepository.findById("c-perm-3").orElseThrow();
    assertThat(untouched.getName()).isEqualTo("Foreign Plan");
  }

  @Test
  @DisplayName(
      "Every other by-ID surface is closed to an unrelated switched company: autosave, media-plan,"
          + " view, cost split and history all return 404")
  void writerSecondaryCompanyGets404OnAllOtherByIdSurfaces() throws Exception {
    seedCampaign("c-perm-4", "Hidden Plan");

    mockMvc
        .perform(
            actingAs(patch("/api/v1/campaigns/c-perm-4/autosave"), WRITER_COMPANY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Sneaky Autosave\"}"))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(actingAs(get("/api/v1/campaigns/c-perm-4/media-plan"), WRITER_COMPANY))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(actingAs(get("/api/v1/campaigns/c-perm-4/view-campaign"), WRITER_COMPANY))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            actingAs(
                get("/api/v1/campaigns/c-perm-4/cost-split-by").param("splitBy", "CITY"),
                WRITER_COMPANY))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(actingAs(get("/api/v1/campaigns/c-perm-4/history"), WRITER_COMPANY))
        .andExpect(status().isNotFound());

    Campaign untouched = campaignRepository.findById("c-perm-4").orElseThrow();
    assertThat(untouched.getName()).isEqualTo("Hidden Plan");
  }

  @Test
  @DisplayName(
      "Selected-inventory endpoints are closed to an unrelated switched company: 404, never the"
          + " full inventory set")
  void writerSecondaryCompanyGets404OnSelectedInventoryEndpoints() throws Exception {
    seedCampaign("c-perm-5", "Inventory Plan");

    mockMvc
        .perform(
            actingAs(get("/api/v1/campaign-inventory/c-perm-5/selected-inventory"), WRITER_COMPANY))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            actingAs(
                get("/api/v1/campaign-inventory/c-perm-5/selected-inventory/all"), WRITER_COMPANY))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(actingAs(get("/api/v1/campaign-inventory/c-perm-5/comments"), WRITER_COMPANY))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Select/deselect inventory on a foreign campaign is rejected for a switched company")
  void writerSecondaryCompanyCannotSelectOrDeselectInventoryOnForeignCampaign() throws Exception {
    seedCampaign("c-perm-6", "Selection Plan");

    mockMvc
        .perform(
            actingAs(post("/api/v1/campaign-inventory/c-perm-6/select"), WRITER_COMPANY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"campaignId\": \"c-perm-6\", \"inventoryId\": \"inv-x\","
                        + " \"operation\": \"SELECT\"}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            actingAs(post("/api/v1/campaign-inventory/c-perm-6/select"), WRITER_COMPANY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"campaignId\": \"c-perm-6\", \"inventoryId\": \"inv-x\","
                        + " \"operation\": \"DESELECT\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "Dual member switched into a media-owner company is denied on buyer-side execution-plan"
          + " endpoints")
  void switchedMediaOwnerParticipantDeniedOnBuyerExecutionPlanSurfaces() throws Exception {
    seedCampaign("c-perm-7", "Execution Plan Campaign");
    // WRITER_COMPANY participates only as a media owner through a schedule config.
    com.mw.planner.domain.CampaignInventorySchedules cfg =
        new com.mw.planner.domain.CampaignInventorySchedules();
    cfg.setCampaignId("c-perm-7");
    cfg.setInventoryId("inv-ep-1");
    cfg.setMediaOwnerId(WRITER_COMPANY);
    inventorySchedulesRepository.save(cfg);

    // Buyer-side surfaces must follow the ACTING company: the primary company owns the
    // campaign, but while switched into the media-owner company the buyer view is hidden.
    mockMvc
        .perform(actingAs(get("/api/v1/campaigns/c-perm-7/execution-plan"), WRITER_COMPANY))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(actingAs(get("/api/v1/campaigns/c-perm-7/execution-plan/status"), WRITER_COMPANY))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(actingAs(post("/api/v1/campaigns/c-perm-7/execution-plan/reset"), WRITER_COMPANY))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "Switched participant (media-owner) and unrelated company cannot bulk select/deselect"
          + " inventories on a buyer-owned campaign")
  void switchedParticipantAndUnrelatedCompanyCannotBulkSelectInventories() throws Exception {
    seedCampaign("c-perm-8", "Bulk Selection Plan");
    // READONLY_COMPANY participates only as a media owner through a schedule config;
    // WRITER_COMPANY is entirely unrelated to this campaign.
    com.mw.planner.domain.CampaignInventorySchedules cfg =
        new com.mw.planner.domain.CampaignInventorySchedules();
    cfg.setCampaignId("c-perm-8");
    cfg.setInventoryId("inv-bulk-1");
    cfg.setMediaOwnerId(READONLY_COMPANY);
    inventorySchedulesRepository.save(cfg);

    String selectAllBody = "{\"operationType\": \"SELECT\"}";
    String bulkBody = "{\"inventoryIds\": [\"inv-bulk-1\"], \"operationType\": \"DESELECT\"}";

    // Unrelated switched company: campaign existence is hidden (404).
    mockMvc
        .perform(
            actingAs(post("/api/v1/campaign-inventory/c-perm-8/select-all"), WRITER_COMPANY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(selectAllBody))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            actingAs(post("/api/v1/campaign-inventory/c-perm-8/bulk-select"), WRITER_COMPANY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bulkBody))
        .andExpect(status().isNotFound());

    // Participating media-owner company: may read, but bulk mutation is owner-only (403).
    mockMvc
        .perform(
            actingAs(post("/api/v1/campaign-inventory/c-perm-8/select-all"), READONLY_COMPANY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(selectAllBody))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            actingAs(post("/api/v1/campaign-inventory/c-perm-8/bulk-select"), READONLY_COMPANY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bulkBody))
        .andExpect(status().isForbidden());

    // Nothing changed: the seeded schedule config is still the only one.
    assertThat(inventorySchedulesRepository.findByCampaignId("c-perm-8")).hasSize(1);
  }

  @Test
  @DisplayName(
      "Switched media-owner participant may filter selected inventory only to itself; foreign"
          + " media-owner filters are rejected")
  void switchedParticipantCannotFilterSelectedInventoryToOtherOwners() throws Exception {
    seedCampaign("c-perm-9", "Filter Plan");
    com.mw.planner.domain.CampaignInventorySchedules cfg =
        new com.mw.planner.domain.CampaignInventorySchedules();
    cfg.setCampaignId("c-perm-9");
    cfg.setInventoryId("inv-filter-1");
    cfg.setMediaOwnerId(READONLY_COMPANY);
    inventorySchedulesRepository.save(cfg);

    String foreignFilter = "{\"mediaOwnerIds\": [\"some-other-owner\"]}";
    String ownFilter = "{\"mediaOwnerIds\": [\"" + READONLY_COMPANY + "\"]}";

    // A participating (non-owner) company may not request another owner's slice.
    mockMvc
        .perform(
            actingAs(
                    post("/api/v1/campaign-inventory/c-perm-9/selected-inventory"),
                    READONLY_COMPANY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(foreignFilter))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            actingAs(
                    post("/api/v1/campaign-inventory/c-perm-9/selected-inventory/all"),
                    READONLY_COMPANY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(foreignFilter))
        .andExpect(status().isForbidden());

    // Filtering to exactly its own company remains allowed.
    mockMvc
        .perform(
            actingAs(
                    post("/api/v1/campaign-inventory/c-perm-9/selected-inventory/all"),
                    READONLY_COMPANY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ownFilter))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Inventory filter route is participation-guarded like all other campaign reads")
  void switchedUnrelatedCompanyCannotProbeCampaignThroughInventoryFilter() throws Exception {
    seedCampaign("c-perm-10", "Probe Plan");

    // Unrelated switched company: the campaign (and its selection state) is hidden.
    mockMvc
        .perform(
            actingAs(post("/api/v1/campaign-inventory/c-perm-10/filter"), WRITER_COMPANY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Read-only secondary company: create/update/delete return 403 and change nothing")
  void readonlySecondaryCompanyCannotWrite() throws Exception {
    seedCampaign("c-perm-2", "Untouchable Plan");

    mockMvc
        .perform(
            actingAs(post("/api/v1/campaigns"), READONLY_COMPANY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(campaignJson("Forbidden Create")))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            actingAs(put("/api/v1/campaigns/c-perm-2"), READONLY_COMPANY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(campaignJson("Hijacked Name")))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(actingAs(delete("/api/v1/campaigns/c-perm-2"), READONLY_COMPANY))
        .andExpect(status().isForbidden());

    Campaign untouched = campaignRepository.findById("c-perm-2").orElseThrow();
    assertThat(untouched.getName()).isEqualTo("Untouchable Plan");
    assertThat(campaignRepository.count()).isEqualTo(1);
  }

  // --- 3. Unknown company id: no company authorities at all ---

  @Test
  @DisplayName("Unknown company id: reads and writes both return 403")
  void unknownCompanyIdIsForbidden() throws Exception {
    mockMvc
        .perform(actingAs(get("/api/v1/campaigns"), UNKNOWN_COMPANY))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            actingAs(post("/api/v1/campaigns"), UNKNOWN_COMPANY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(campaignJson("Never Created")))
        .andExpect(status().isForbidden());
    assertThat(campaignRepository.count()).isZero();
  }

  // --- 4. /users/userinfo preserves company_permissions ---

  @Test
  @DisplayName("/users/userinfo passes the company_permissions map through the DTO untouched")
  void userinfoPreservesCompanyPermissions() throws Exception {
    UserInfoResponse iamResponse =
        UserInfoResponse.builder()
            .success(true)
            .data(
                UserInfoResponse.UserInfoData.builder()
                    .id("perm-user")
                    .email("perm-user@example.com")
                    .permissions(List.of("planner:plans:read"))
                    .companyPermissions(
                        Map.of(
                            PRIMARY_COMPANY,
                            List.of(
                                "planner:plans:read",
                                "planner:plans:create",
                                "planner:plans:update",
                                "planner:plans:delete"),
                            READONLY_COMPANY,
                            List.of("planner:plans:read")))
                    .build())
            .build();
    when(iamUserService.getUserInfo(anyString())).thenReturn(iamResponse);

    mockMvc
        .perform(authed(get("/api/v1/users/userinfo")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.company_permissions").exists())
        .andExpect(
            jsonPath("$.data.company_permissions['" + PRIMARY_COMPANY + "'].length()").value(4))
        .andExpect(
            jsonPath("$.data.company_permissions['" + READONLY_COMPANY + "'][0]")
                .value("planner:plans:read"))
        .andExpect(jsonPath("$.data.permissions[0]").value("planner:plans:read"));
  }
}
