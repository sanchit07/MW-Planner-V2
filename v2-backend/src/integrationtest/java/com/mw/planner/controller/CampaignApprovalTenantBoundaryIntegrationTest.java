package com.mw.planner.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.TestcontainersConfiguration;
import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignApprovedWorkflowStatus;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.CampaignProposalStatus;
import com.mw.planner.domain.Schedule;
import com.mw.planner.dto.CompanyLookupResponseDTO;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.dto.UserResponseDTO;
import com.mw.planner.repository.CampaignApprovedWorkflowStatusRepository;
import com.mw.planner.repository.CampaignInventorySchedulesRepository;
import com.mw.planner.repository.CampaignProposalStatusRepository;
import com.mw.planner.repository.CampaignRepository;
import com.mw.planner.repository.ScheduleRepository;
import com.mw.planner.service.CompanyService;
import com.mw.planner.service.UserService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Tenant-switch persona boundary through the real HTTP surface (X-Tenant-Id header) with real
 * repositories/Mongo: media owners must never see agency fees (budget) or other owners' state on
 * GET /api/v1/campaign-approval-workflow/inbox and /{campaignId}/approval-details — even via direct
 * API calls, even when granted shared companyAccess, and even without a proposal.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Import(TestcontainersConfiguration.class)
class CampaignApprovalTenantBoundaryIntegrationTest {

  private static final String CAMPAIGN_ID = "camp-boundary";
  private static final String BUYER_COMPANY = "buyer-agency";
  private static final String MO_LUMINA = "mo-lumina";
  private static final String MO_ATLAS = "mo-atlas";
  private static final String MO_NOVA = "mo-nova"; // media-owner type, no proposal
  private static final double BUDGET = 250000.0;

  @Autowired private MockMvc mockMvc;
  @Autowired private CampaignRepository campaignRepository;
  @Autowired private CampaignApprovedWorkflowStatusRepository workflowStatusRepository;
  @Autowired private CampaignProposalStatusRepository proposalStatusRepository;
  @Autowired private CampaignInventorySchedulesRepository schedulesRepository;
  @Autowired private ScheduleRepository scheduleRepository;

  // IAM is external — mock the IAM-facing services; everything below them is real.
  @MockitoBean private UserService userService;
  @MockitoBean private CompanyService companyService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    objectMapper.findAndRegisterModules();

    TestingAuthenticationToken auth =
        new TestingAuthenticationToken(
            "boundary-user", "n/a", List.of(new SimpleGrantedAuthority("ROLE_planner:plans:read")));
    auth.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(auth);

    campaignRepository.deleteAll();
    workflowStatusRepository.deleteAll();
    proposalStatusRepository.deleteAll();
    schedulesRepository.deleteAll();
    scheduleRepository.deleteAll();

    // ---- Seed a campaign in an active approval cycle with two media owners ----
    Campaign campaign = new Campaign();
    campaign.setId(CAMPAIGN_ID);
    campaign.setName("Boundary Plan");
    campaign.setUserId("creator-user");
    campaign.setCompanyId(BUYER_COMPANY);
    campaign.setStatus(Campaign.Status.REVIEWING);
    campaign.setBudget(BUDGET);
    campaign.setCurrency("USD");
    campaign.setStartDate(LocalDate.of(2030, 1, 1));
    campaign.setEndDate(LocalDate.of(2030, 2, 1));
    campaign.setDataMode("live");
    campaignRepository.save(campaign);

    saveWorkflowStatus(
        "wf-internal",
        CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL,
        CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
    saveWorkflowStatus(
        "wf-mo",
        CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER,
        CampaignApprovedWorkflowStatus.Status.PENDING);

    saveProposal("p-lumina", MO_LUMINA, CampaignProposalStatus.Status.PENDING);
    saveProposal("p-atlas", MO_ATLAS, CampaignProposalStatus.Status.NEGOTIATING);

    saveSchedule("s1", 1000.0);
    saveSchedule("s2", 2000.0);
    saveOwnerSchedules(MO_LUMINA, "s1", 1); // no open counter offer
    saveOwnerSchedules(MO_ATLAS, "s2", 2); // history > 1 + unapproved => open counter offer

    // ---- IAM mocks: user context, tenant membership, company types ----
    when(userService.getIamUserContext())
        .thenReturn(
            IamUserContext.builder()
                .id("boundary-user")
                .companyId(BUYER_COMPANY)
                .isGlobalAdmin(false)
                .locale(Locale.ENGLISH)
                .build());
    when(userService.isTenantOfCompany(anyString())).thenReturn(false);
    when(userService.isTenantOfCompany(MO_LUMINA)).thenReturn(true);
    when(userService.isTenantOfCompany(MO_ATLAS)).thenReturn(true);
    when(userService.isTenantOfCompany(MO_NOVA)).thenReturn(true);
    // Shared acting-tenant resolver: mimic real behavior (valid tenant wins, else active company)
    when(userService.resolveActingCompanyId(any()))
        .thenAnswer(
            inv -> {
              String t = inv.getArgument(0);
              if (t != null && !t.isBlank() && userService.isTenantOfCompany(t)) {
                return t;
              }
              return userService.getIamUserContext().getCompanyId();
            });
    when(userService.getUserById("creator-user"))
        .thenReturn(UserResponseDTO.builder().id("creator-user").build());

    when(companyService.getCompanyLookupWithCompanyId(anyString())).thenReturn(null);
    when(companyService.getCompanyLookupWithCompanyId(BUYER_COMPANY))
        .thenReturn(company("Buyer Agency", "AGENCY"));
    when(companyService.getCompanyLookupWithCompanyId(MO_LUMINA))
        .thenReturn(company("Lumina Outdoor", "MEDIA_OWNER"));
    when(companyService.getCompanyLookupWithCompanyId(MO_ATLAS))
        .thenReturn(company("Atlas Media", "MEDIA_OWNER"));
    when(companyService.getCompanyLookupWithCompanyId(MO_NOVA))
        .thenReturn(company("Nova Outdoor", "MEDIA_OWNER"));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // ---------------------------------------------------------------------------------
  // Inbox
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("Inbox as media-owner tenant: budget null, no mediaOwners, own-scoped price flag")
  void inbox_mediaOwnerTenant_redacted() throws Exception {
    JsonNode items =
        getData(get("/api/v1/campaign-approval-workflow/inbox").header("X-Tenant-Id", MO_LUMINA));

    assertThat(items).hasSize(1);
    JsonNode item = items.get(0);
    assertThat(item.hasNonNull("budget")).isFalse();
    assertThat(item.hasNonNull("mediaOwners")).isFalse();
    assertThat(item.path("viewerIsMediaOwner").asBoolean()).isTrue();
    // Only Atlas has an open counter offer — Lumina must not see the campaign-wide flag.
    assertThat(item.path("hasUnacceptedPrices").asBoolean()).isFalse();
    assertThat(item.path("viewerProposal").path("mediaOwnerId").asText()).isEqualTo(MO_LUMINA);
  }

  @Test
  @DisplayName("Inbox as media-owner tenant with own open counter offer: flag true, still redacted")
  void inbox_mediaOwnerTenant_ownScheduleScope() throws Exception {
    JsonNode items =
        getData(get("/api/v1/campaign-approval-workflow/inbox").header("X-Tenant-Id", MO_ATLAS));

    assertThat(items).hasSize(1);
    JsonNode item = items.get(0);
    assertThat(item.path("hasUnacceptedPrices").asBoolean()).isTrue();
    assertThat(item.hasNonNull("budget")).isFalse();
    assertThat(item.hasNonNull("mediaOwners")).isFalse();
  }

  @Test
  @DisplayName("Inbox as buyer/creator: full budget + per-owner list + campaign-wide flag")
  void inbox_buyerTenant_fullVisibility() throws Exception {
    JsonNode items = getData(get("/api/v1/campaign-approval-workflow/inbox"));

    assertThat(items).hasSize(1);
    JsonNode item = items.get(0);
    assertThat(item.path("budget").asDouble()).isEqualTo(BUDGET);
    assertThat(item.path("viewerIsMediaOwner").asBoolean()).isFalse();
    assertThat(item.path("hasUnacceptedPrices").asBoolean()).isTrue();
    assertThat(item.path("mediaOwners")).hasSize(2);
    assertThat(item.path("mediaOwners").findValuesAsText("mediaOwnerId"))
        .containsExactlyInAnyOrder(MO_LUMINA, MO_ATLAS);
  }

  @Test
  @DisplayName("Inbox: media owner in companyAccess is still redacted (proposal-owner precedence)")
  void inbox_companyAccessMediaOwner_stillRedacted() throws Exception {
    grantCompanyAccess(MO_LUMINA);

    JsonNode items =
        getData(get("/api/v1/campaign-approval-workflow/inbox").header("X-Tenant-Id", MO_LUMINA));

    assertThat(items).hasSize(1);
    JsonNode item = items.get(0);
    assertThat(item.hasNonNull("budget")).isFalse();
    assertThat(item.hasNonNull("mediaOwners")).isFalse();
    assertThat(item.path("viewerIsMediaOwner").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("Inbox: media-owner-typed tenant with companyAccess but NO proposal is redacted")
  void inbox_companyAccessMediaOwnerType_noProposal_redacted() throws Exception {
    grantCompanyAccess(MO_NOVA);

    JsonNode items =
        getData(get("/api/v1/campaign-approval-workflow/inbox").header("X-Tenant-Id", MO_NOVA));

    assertThat(items).hasSize(1);
    JsonNode item = items.get(0);
    assertThat(item.hasNonNull("budget")).isFalse();
    assertThat(item.hasNonNull("mediaOwners")).isFalse();
    assertThat(item.path("viewerIsMediaOwner").asBoolean()).isTrue();
    assertThat(item.path("hasUnacceptedPrices").asBoolean()).isFalse();
  }

  @Test
  @DisplayName("Inbox as global admin: sees everything")
  void inbox_globalAdmin_seesEverything() throws Exception {
    when(userService.getIamUserContext())
        .thenReturn(
            IamUserContext.builder()
                .id("admin-user")
                .companyId("admin-co")
                .isGlobalAdmin(true)
                .locale(Locale.ENGLISH)
                .build());

    JsonNode items = getData(get("/api/v1/campaign-approval-workflow/inbox"));

    assertThat(items).hasSize(1);
    JsonNode item = items.get(0);
    assertThat(item.path("budget").asDouble()).isEqualTo(BUDGET);
    assertThat(item.path("mediaOwners")).hasSize(2);
  }

  @Test
  @DisplayName("Inbox: spoofed X-Tenant-Id without membership is ignored")
  void inbox_invalidTenantHeader_ignored() throws Exception {
    JsonNode items =
        getData(
            get("/api/v1/campaign-approval-workflow/inbox")
                .header("X-Tenant-Id", "not-my-company"));

    // Falls back to the IAM active company (the buyer) — full visibility, no persona flip.
    assertThat(items).hasSize(1);
    assertThat(items.get(0).path("budget").asDouble()).isEqualTo(BUDGET);
    assertThat(items.get(0).path("mediaOwners")).hasSize(2);
  }

  // ---------------------------------------------------------------------------------
  // Approval details
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("Details as media-owner tenant: budget null, only own stage visible")
  void details_mediaOwnerTenant_redacted() throws Exception {
    JsonNode data =
        getData(
            get("/api/v1/campaign-approval-workflow/{id}/approval-details", CAMPAIGN_ID)
                .header("X-Tenant-Id", MO_LUMINA));

    assertThat(data.hasNonNull("budget")).isFalse();
    assertThat(data.path("approvalProgress")).hasSize(1);
    assertThat(data.path("approvalProgress").get(0).path("approvalAuthority").asText())
        .isEqualTo("MEDIA_OWNER");
  }

  @Test
  @DisplayName("Details: media owner in companyAccess still gets budget=null")
  void details_companyAccessMediaOwner_stillRedacted() throws Exception {
    grantCompanyAccess(MO_LUMINA);

    JsonNode data =
        getData(
            get("/api/v1/campaign-approval-workflow/{id}/approval-details", CAMPAIGN_ID)
                .header("X-Tenant-Id", MO_LUMINA));

    assertThat(data.hasNonNull("budget")).isFalse();
  }

  @Test
  @DisplayName("Details: media-owner-typed tenant with NO proposal gets budget=null (direct call)")
  void details_mediaOwnerType_noProposal_redacted() throws Exception {
    JsonNode data =
        getData(
            get("/api/v1/campaign-approval-workflow/{id}/approval-details", CAMPAIGN_ID)
                .header("X-Tenant-Id", MO_NOVA));

    assertThat(data.hasNonNull("budget")).isFalse();
  }

  @Test
  @DisplayName("Details: tenant with unresolvable company type (null IAM lookup) fails closed")
  void details_unresolvableTenantType_failsClosed() throws Exception {
    // "mo-ghost" is a valid membership but its company lookup returns null (default stub).
    when(userService.isTenantOfCompany("mo-ghost")).thenReturn(true);
    grantCompanyAccess("mo-ghost");

    JsonNode data =
        getData(
            get("/api/v1/campaign-approval-workflow/{id}/approval-details", CAMPAIGN_ID)
                .header("X-Tenant-Id", "mo-ghost"));

    assertThat(data.hasNonNull("budget")).isFalse();
  }

  @Test
  @DisplayName("Inbox: tenant with unresolvable company type (null IAM lookup) fails closed")
  void inbox_unresolvableTenantType_failsClosed() throws Exception {
    when(userService.isTenantOfCompany("mo-ghost")).thenReturn(true);
    grantCompanyAccess("mo-ghost");

    JsonNode items =
        getData(get("/api/v1/campaign-approval-workflow/inbox").header("X-Tenant-Id", "mo-ghost"));

    assertThat(items).hasSize(1);
    assertThat(items.get(0).hasNonNull("budget")).isFalse();
    assertThat(items.get(0).hasNonNull("mediaOwners")).isFalse();
  }

  @Test
  @DisplayName("Details as buyer/creator: budget visible with full stage structure")
  void details_buyerTenant_seesBudget() throws Exception {
    JsonNode data =
        getData(get("/api/v1/campaign-approval-workflow/{id}/approval-details", CAMPAIGN_ID));

    assertThat(data.path("budget").asDouble()).isEqualTo(BUDGET);
    assertThat(data.path("approvalProgress")).hasSize(2);
  }

  @Test
  @DisplayName("Details as global admin: budget visible")
  void details_globalAdmin_seesBudget() throws Exception {
    when(userService.getIamUserContext())
        .thenReturn(
            IamUserContext.builder()
                .id("admin-user")
                .companyId("admin-co")
                .isGlobalAdmin(true)
                .locale(Locale.ENGLISH)
                .build());

    JsonNode data =
        getData(get("/api/v1/campaign-approval-workflow/{id}/approval-details", CAMPAIGN_ID));

    assertThat(data.path("budget").asDouble()).isEqualTo(BUDGET);
  }

  // ---------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------

  private JsonNode getData(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req)
      throws Exception {
    MvcResult result = mockMvc.perform(req).andExpect(status().isOk()).andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
  }

  private void grantCompanyAccess(String companyId) {
    Campaign campaign = campaignRepository.findById(CAMPAIGN_ID).orElseThrow();
    campaign.setCompanyAccess(new ArrayList<>(List.of(companyId)));
    campaignRepository.save(campaign);
  }

  private void saveWorkflowStatus(
      String id,
      CampaignApprovedWorkflowStatus.ApprovalAuthority authority,
      CampaignApprovedWorkflowStatus.Status status) {
    CampaignApprovedWorkflowStatus ws = new CampaignApprovedWorkflowStatus();
    ws.setId(id);
    ws.setCampaignId(CAMPAIGN_ID);
    ws.setApprovalAuthority(authority);
    ws.setStatus(status);
    workflowStatusRepository.save(ws);
  }

  private void saveProposal(String id, String mediaOwnerId, CampaignProposalStatus.Status status) {
    CampaignProposalStatus p = new CampaignProposalStatus();
    p.setId(id);
    p.setCampaignId(CAMPAIGN_ID);
    p.setMediaOwnerId(mediaOwnerId);
    p.setStatus(status);
    p.setInventoryIds(List.of("inv-" + mediaOwnerId));
    proposalStatusRepository.save(p);
  }

  private void saveSchedule(String id, Double basePrice) {
    Schedule s = new Schedule();
    s.setId(id);
    s.setBasePrice(basePrice);
    scheduleRepository.save(s);
  }

  private void saveOwnerSchedules(String mediaOwnerId, String scheduleId, int historySize) {
    CampaignInventorySchedules s = new CampaignInventorySchedules();
    s.setCampaignId(CAMPAIGN_ID);
    s.setMediaOwnerId(mediaOwnerId);
    s.setInventoryId("inv-" + mediaOwnerId);
    s.setScheduleIds(List.of(scheduleId));
    List<CampaignInventorySchedules.History> history = new ArrayList<>();
    for (int i = 0; i < historySize; i++) {
      history.add(new CampaignInventorySchedules.History());
    }
    s.setHistory(history);
    s.setApprovedBy(null);
    schedulesRepository.save(s);
  }

  private static CompanyLookupResponseDTO company(String name, String type) {
    return CompanyLookupResponseDTO.builder().name(name).companyType(type).build();
  }
}
