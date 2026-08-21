package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignApprovedWorkflowStatus;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.CampaignProposalStatus;
import com.mw.planner.domain.Schedule;
import com.mw.planner.dto.ApprovalInboxItemDTO;
import com.mw.planner.dto.CampaignApprovalDetailsResponseDTO;
import com.mw.planner.dto.CompanyLookupResponseDTO;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.dto.UserResponseDTO;
import com.mw.planner.repository.CampaignApprovalHistoryRepository;
import com.mw.planner.repository.CampaignApprovedWorkflowStatusRepository;
import com.mw.planner.repository.CampaignInventorySchedulesRepository;
import com.mw.planner.repository.CampaignProposalStatusRepository;
import com.mw.planner.repository.CampaignRepository;
import com.mw.planner.repository.ScheduleRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Tenant-switch persona boundary tests for the Plan Approval surfaces (inbox + approval-details). A
 * user whose acting company (X-Company-Id) is a media-owner company must never receive buyer
 * financials (budget incl. agency fees), the per-owner mediaOwners list, or another owner's
 * negotiation state — even when that company is also listed in campaign.companyAccess
 * (proposal-owner precedence). Buyer/creator tenants and global admins get full visibility.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CampaignApprovalWorkflowTenantBoundaryTest {

  private static final String CAMPAIGN_ID = "camp1";
  private static final String BUYER_COMPANY = "buyer-agency";
  private static final String MO_LUMINA = "mo-lumina";
  private static final String MO_ATLAS = "mo-atlas";

  /** A media-owner-typed company with NO proposal on the campaign. */
  private static final String MO_NOVA = "mo-nova";

  private static final Double BUDGET = 250000.0;
  private static final List<Campaign.Status> ACTIVE =
      List.of(Campaign.Status.REVIEWING, Campaign.Status.NEGOTIATING);
  private static final List<CampaignProposalStatus.Status> OPEN =
      List.of(CampaignProposalStatus.Status.PENDING, CampaignProposalStatus.Status.NEGOTIATING);

  @Mock private CampaignService campaignService;
  @Mock private CampaignRepository campaignRepository;
  @Mock private CampaignInventorySchedulesRepository campaignInventorySchedulesRepository;
  @Mock private CampaignProposalStatusRepository campaignProposalStatusRepository;
  @Mock private CampaignApprovedWorkflowStatusRepository campaignApprovedWorkflowStatusRepository;
  @Mock private CampaignApprovalHistoryRepository campaignApprovalHistoryRepository;
  @Mock private MWAdsService mwAdsService;
  @Mock private CampaignProposalStatusAndCommentService campaignProposalStatusAndCommentService;
  @Mock private UserService userService;
  @Mock private CampaignActivityService campaignActivityService;
  @Mock private CompanyService companyService;
  @Mock private ScheduleRepository scheduleRepository;
  @Mock private TestModeService testModeService;

  @InjectMocks private CampaignApprovalWorkflowService service;

  private Campaign campaign;
  private CampaignProposalStatus luminaProposal;
  private CampaignProposalStatus atlasProposal;
  private CampaignInventorySchedules luminaSchedules;
  private CampaignInventorySchedules atlasSchedules;

  @BeforeEach
  void setUp() {
    campaign = new Campaign();
    campaign.setId(CAMPAIGN_ID);
    campaign.setName("Q3 Brand Push");
    campaign.setUserId("creator-user");
    campaign.setCompanyId(BUYER_COMPANY);
    campaign.setStatus(Campaign.Status.REVIEWING);
    campaign.setBudget(BUDGET);
    campaign.setCurrency("USD");

    luminaProposal = proposal("p-lumina", MO_LUMINA, CampaignProposalStatus.Status.PENDING);
    atlasProposal = proposal("p-atlas", MO_ATLAS, CampaignProposalStatus.Status.NEGOTIATING);

    luminaSchedules = ownerSchedules(MO_LUMINA, "s1", 1);
    atlasSchedules = ownerSchedules(MO_ATLAS, "s2", 2); // history >1 => open counter offer

    // ---- Shared stubs (lenient; individual tests override the viewer identity) ----
    when(campaignService.findByIdForCurrentMode(CAMPAIGN_ID)).thenReturn(campaign);
    // Test Mode partition is orthogonal to the persona boundary — caller and campaign
    // share the same data mode in these scenarios.
    when(testModeService.matchesCallerMode(any(Campaign.class))).thenReturn(true);

    // Shared acting-tenant resolver default: no valid switch -> the buyer's active company.
    // Persona helpers below override this per scenario.
    lenient().when(userService.getActingCompanyId()).thenReturn(BUYER_COMPANY);

    CampaignApprovedWorkflowStatus internal = new CampaignApprovedWorkflowStatus();
    internal.setId("wf-internal");
    internal.setCampaignId(CAMPAIGN_ID);
    internal.setApprovalAuthority(CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL);
    internal.setStatus(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
    CampaignApprovedWorkflowStatus mediaOwner = new CampaignApprovedWorkflowStatus();
    mediaOwner.setId("wf-mo");
    mediaOwner.setCampaignId(CAMPAIGN_ID);
    mediaOwner.setApprovalAuthority(CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER);
    mediaOwner.setStatus(CampaignApprovedWorkflowStatus.Status.PENDING);
    when(campaignApprovedWorkflowStatusRepository.findByCampaignId(CAMPAIGN_ID))
        .thenReturn(List.of(internal, mediaOwner));

    when(campaignProposalStatusRepository.findByCampaignId(CAMPAIGN_ID))
        .thenReturn(List.of(luminaProposal, atlasProposal));
    when(campaignProposalStatusRepository.findStatusesByCampaignId(CAMPAIGN_ID))
        .thenReturn(List.of(luminaProposal, atlasProposal));

    // Only Atlas has an open (unaccepted) counter offer by default.
    when(campaignInventorySchedulesRepository
            .existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull(CAMPAIGN_ID))
        .thenReturn(true);
    when(campaignInventorySchedulesRepository.findByCampaignIdAndApprovedByIsNull(CAMPAIGN_ID))
        .thenReturn(List.of(atlasSchedules));
    when(campaignInventorySchedulesRepository.findByCampaignId(CAMPAIGN_ID))
        .thenReturn(List.of(luminaSchedules, atlasSchedules));

    // The inbox bulk-loads by campaign-id list (batched, no per-campaign N+1 queries).
    when(campaignProposalStatusRepository.findByCampaignIdIn(List.of(CAMPAIGN_ID)))
        .thenReturn(List.of(luminaProposal, atlasProposal));
    when(campaignInventorySchedulesRepository.findByCampaignIdIn(List.of(CAMPAIGN_ID)))
        .thenReturn(List.of(luminaSchedules, atlasSchedules));

    // Resolve any requested id set to the matching schedule fixtures.
    when(scheduleRepository.findAllById(anyList()))
        .thenAnswer(
            inv -> {
              Iterable<String> ids = inv.getArgument(0);
              List<Schedule> out = new ArrayList<>();
              for (String id : ids) {
                if ("s1".equals(id)) out.add(schedule("s1", 1000.0));
                if ("s2".equals(id)) out.add(schedule("s2", 2000.0));
              }
              return out;
            });

    when(companyService.getCompanyLookupWithCompanyId(MO_LUMINA))
        .thenReturn(company("Lumina Outdoor", "MEDIA_OWNER"));
    when(companyService.getCompanyLookupWithCompanyId(MO_ATLAS))
        .thenReturn(company("Atlas Media", "MEDIA_OWNER"));
    when(companyService.getCompanyLookupWithCompanyId(MO_NOVA))
        .thenReturn(company("Nova Outdoor", "MEDIA_OWNER"));
    when(companyService.getCompanyLookupWithCompanyId(BUYER_COMPANY))
        .thenReturn(company("Buyer Agency", "AGENCY"));

    when(userService.getUserById("creator-user"))
        .thenReturn(UserResponseDTO.builder().id("creator-user").build());
    when(campaignService.resolveCampaignStatus(any(Campaign.class), any(), anyString()))
        .thenReturn(Campaign.Status.REVIEWING);
    when(campaignApprovalHistoryRepository.findByCampaignApprovedWorkflowStatusId(anyString()))
        .thenReturn(List.of());
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            eq(CAMPAIGN_ID), anyString()))
        .thenReturn(null);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            CAMPAIGN_ID, MO_LUMINA))
        .thenReturn(luminaProposal);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            CAMPAIGN_ID, MO_ATLAS))
        .thenReturn(atlasProposal);

    // Nothing extra comes in through by-proposal discovery unless a test says so.
    when(campaignProposalStatusRepository.findByMediaOwnerIdAndStatusIn(anyString(), anyList()))
        .thenReturn(List.of());
    when(campaignRepository.findByStatusIn(anyList())).thenReturn(List.of());
    when(campaignRepository.findByStatusInAndCompanyInvolved(anyList(), anyString()))
        .thenReturn(List.of());
  }

  // ---------------------------------------------------------------------------------
  // Viewer-identity helpers: mock IAM active company + acting-company resolver (X-Company-Id)
  // ---------------------------------------------------------------------------------

  /**
   * The demo user's active IAM company is the buyer agency; tenant switch via the acting-company
   * resolver.
   */
  private void asTenant(String tenantId, boolean tenantIsValidMembership) {
    when(userService.getIamUserContext())
        .thenReturn(
            IamUserContext.builder()
                .id("demo-user")
                .companyId(BUYER_COMPANY)
                .isGlobalAdmin(false)
                .build());
    if (tenantId != null) {
      lenient().when(userService.isTenantOfCompany(tenantId)).thenReturn(tenantIsValidMembership);
    }
    // Shared acting-company resolver: a valid switched tenant resolves to that company;
    // otherwise the service falls back to the IAM context's active company.
    lenient()
        .when(userService.getActingCompanyId())
        .thenReturn(tenantIsValidMembership ? tenantId : null);
    if (tenantIsValidMembership && tenantId != null) {
      // A media-owner tenant discovers the campaign through its own open proposal.
      when(campaignProposalStatusRepository.findByMediaOwnerIdAndStatusIn(tenantId, OPEN))
          .thenReturn(
              tenantId.equals(MO_LUMINA)
                  ? List.of(luminaProposal)
                  : tenantId.equals(MO_ATLAS) ? List.of(atlasProposal) : List.of());
    }
  }

  private void asBuyerCreator() {
    when(userService.getIamUserContext())
        .thenReturn(
            IamUserContext.builder()
                .id("demo-user")
                .companyId(BUYER_COMPANY)
                .isGlobalAdmin(false)
                .build());
    when(campaignRepository.findByStatusInAndCompanyInvolved(ACTIVE, BUYER_COMPANY))
        .thenReturn(List.of(campaign));
  }

  private void asGlobalAdmin() {
    when(userService.getIamUserContext())
        .thenReturn(
            IamUserContext.builder()
                .id("admin-user")
                .companyId("admin-co")
                .isGlobalAdmin(true)
                .build());
    when(userService.getActingCompanyId()).thenReturn("admin-co");
    when(campaignRepository.findByStatusIn(ACTIVE)).thenReturn(List.of(campaign));
  }

  // ---------------------------------------------------------------------------------
  // Inbox: GET /api/v1/campaign-approval-workflow/inbox
  // ---------------------------------------------------------------------------------

  @Test
  void inbox_mediaOwnerTenant_neverSeesBudgetOrOtherOwners() {
    asTenant(MO_LUMINA, true);

    List<ApprovalInboxItemDTO> items = service.getApprovalInbox();

    assertThat(items).hasSize(1);
    ApprovalInboxItemDTO item = items.get(0);
    // Buyer financials (budget incl. agency fees) must be redacted.
    assertThat(item.getBudget()).isNull();
    // No per-owner list — other owners' state must stay invisible.
    assertThat(item.getMediaOwners()).isNull();
    assertThat(item.isViewerIsMediaOwner()).isTrue();
    // Only Atlas has an open counter offer, so Lumina must NOT see the campaign-wide flag.
    assertThat(item.isHasUnacceptedPrices()).isFalse();
    // The viewer gets only its own slice.
    assertThat(item.getViewerProposal()).isNotNull();
    assertThat(item.getViewerProposal().getMediaOwnerId()).isEqualTo(MO_LUMINA);
  }

  @Test
  void inbox_mediaOwnerTenant_hasUnacceptedPricesScopedToOwnSchedules() {
    asTenant(MO_ATLAS, true);

    List<ApprovalInboxItemDTO> items = service.getApprovalInbox();

    assertThat(items).hasSize(1);
    ApprovalInboxItemDTO item = items.get(0);
    // Atlas' own schedules carry the open counter offer — the flag is true for Atlas...
    assertThat(item.isHasUnacceptedPrices()).isTrue();
    // ...but the rest of the buyer view stays redacted.
    assertThat(item.getBudget()).isNull();
    assertThat(item.getMediaOwners()).isNull();
    assertThat(item.getViewerProposal().getMediaOwnerId()).isEqualTo(MO_ATLAS);
  }

  @Test
  void inbox_buyerCreatorTenant_getsFullBudgetAndPerOwnerList() {
    asBuyerCreator();

    List<ApprovalInboxItemDTO> items = service.getApprovalInbox();

    assertThat(items).hasSize(1);
    ApprovalInboxItemDTO item = items.get(0);
    assertThat(item.getBudget()).isEqualTo(BUDGET);
    assertThat(item.isViewerIsMediaOwner()).isFalse();
    assertThat(item.getViewerProposal()).isNull();
    // Campaign-wide unaccepted-prices flag for buyer side.
    assertThat(item.isHasUnacceptedPrices()).isTrue();
    assertThat(item.getMediaOwners()).isNotNull().hasSize(2);
    assertThat(
            item.getMediaOwners().stream()
                .map(ApprovalInboxItemDTO.MediaOwnerProgressDTO::getMediaOwnerId))
        .containsExactlyInAnyOrder(MO_LUMINA, MO_ATLAS);
    // Per-owner open-counter state is visible to the buyer.
    assertThat(
            item.getMediaOwners().stream()
                .filter(o -> MO_ATLAS.equals(o.getMediaOwnerId()))
                .findFirst()
                .orElseThrow()
                .isHasOpenCounterOffer())
        .isTrue();
  }

  @Test
  void inbox_mediaOwnerInCompanyAccess_isStillRedacted() {
    // Proposal-owner precedence: shared companyAccess must not unlock buyer financials.
    campaign.setCompanyAccess(new ArrayList<>(List.of(MO_LUMINA)));
    asTenant(MO_LUMINA, true);
    // With shared access the campaign also comes back from the involved-company query.
    when(campaignRepository.findByStatusInAndCompanyInvolved(ACTIVE, MO_LUMINA))
        .thenReturn(List.of(campaign));

    List<ApprovalInboxItemDTO> items = service.getApprovalInbox();

    assertThat(items).hasSize(1);
    ApprovalInboxItemDTO item = items.get(0);
    assertThat(item.getBudget()).isNull();
    assertThat(item.getMediaOwners()).isNull();
    assertThat(item.isViewerIsMediaOwner()).isTrue();
    assertThat(item.isHasUnacceptedPrices()).isFalse();
  }

  @Test
  void inbox_mediaOwnerTypeWithoutProposal_companyAccessOnly_isStillRedacted() {
    // A media-owner-typed company with shared companyAccess but NO proposal on the
    // campaign must not be treated as buyer-side.
    campaign.setCompanyAccess(new ArrayList<>(List.of(MO_NOVA)));
    asTenant(MO_NOVA, true);
    when(campaignRepository.findByStatusInAndCompanyInvolved(ACTIVE, MO_NOVA))
        .thenReturn(List.of(campaign));

    List<ApprovalInboxItemDTO> items = service.getApprovalInbox();

    assertThat(items).hasSize(1);
    ApprovalInboxItemDTO item = items.get(0);
    assertThat(item.getBudget()).isNull();
    assertThat(item.getMediaOwners()).isNull();
    assertThat(item.isViewerIsMediaOwner()).isTrue();
    assertThat(item.isHasUnacceptedPrices()).isFalse();
    assertThat(item.getViewerProposal()).isNull();
  }

  @Test
  void inbox_tenantTypeLookupFails_failsClosed_redacted() {
    // IAM outage while resolving the tenant's company type must never disclose fees.
    String ghost = "mo-ghost";
    campaign.setCompanyAccess(new ArrayList<>(List.of(ghost)));
    when(companyService.getCompanyLookupWithCompanyId(ghost))
        .thenThrow(new RuntimeException("IAM down"));
    asTenant(ghost, true);
    when(campaignRepository.findByStatusInAndCompanyInvolved(ACTIVE, ghost))
        .thenReturn(List.of(campaign));

    List<ApprovalInboxItemDTO> items = service.getApprovalInbox();

    assertThat(items).hasSize(1);
    assertThat(items.get(0).getBudget()).isNull();
    assertThat(items.get(0).getMediaOwners()).isNull();
    assertThat(items.get(0).isViewerIsMediaOwner()).isTrue();
  }

  @Test
  void inbox_globalAdmin_seesEverything() {
    asGlobalAdmin();

    List<ApprovalInboxItemDTO> items = service.getApprovalInbox();

    assertThat(items).hasSize(1);
    ApprovalInboxItemDTO item = items.get(0);
    assertThat(item.getBudget()).isEqualTo(BUDGET);
    assertThat(item.isViewerIsMediaOwner()).isFalse();
    assertThat(item.getMediaOwners()).isNotNull().hasSize(2);
    assertThat(item.isHasUnacceptedPrices()).isTrue();
  }

  @Test
  void inbox_invalidTenantHeader_fallsBackToIamActiveCompany() {
    // A tenant id the user is NOT a member of must be ignored — the viewer stays the
    // buyer agency, so a spoofed company header cannot flip the persona either way.
    asBuyerCreator();
    when(userService.isTenantOfCompany("some-other-co")).thenReturn(false);

    List<ApprovalInboxItemDTO> items = service.getApprovalInbox();

    assertThat(items).hasSize(1);
    assertThat(items.get(0).getBudget()).isEqualTo(BUDGET);
    assertThat(items.get(0).getMediaOwners()).isNotNull().hasSize(2);
  }

  // ---------------------------------------------------------------------------------
  // Details: GET /api/v1/campaign-approval-workflow/{campaignId}/approval-details
  // ---------------------------------------------------------------------------------

  @Test
  void details_mediaOwnerTenant_budgetIsNullAndSeesOnlyOwnStage() {
    asTenant(MO_LUMINA, true);

    CampaignApprovalDetailsResponseDTO details = service.getCampaignApprovalDetails(CAMPAIGN_ID);

    assertThat(details.getBudget()).isNull();
    // Media-owner viewers see only the Media Owner stage.
    assertThat(details.getApprovalProgress()).hasSize(1);
    assertThat(details.getApprovalProgress().get(0).getApprovalAuthority())
        .isEqualTo(CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER);
    assertThat(details.getApprovalPermissions())
        .containsExactly(CampaignApprovalDetailsResponseDTO.ApprovalPermission.MEDIA_OWNER);
  }

  @Test
  void details_mediaOwnerInCompanyAccess_budgetStillNull() {
    campaign.setCompanyAccess(new ArrayList<>(List.of(MO_LUMINA)));
    asTenant(MO_LUMINA, true);

    CampaignApprovalDetailsResponseDTO details = service.getCampaignApprovalDetails(CAMPAIGN_ID);

    assertThat(details.getBudget()).isNull();
    assertThat(details.getApprovalProgress()).hasSize(1);
  }

  @Test
  void details_mediaOwnerTypeWithoutProposal_directCall_budgetIsNull() {
    // Direct API call with a known campaign ID: a media-owner tenant with no proposal,
    // no shared access and no creator relationship is not an involved party — it gets a
    // 404 (campaign existence hidden), so buyer financials can never leak.
    asTenant(MO_NOVA, true);

    // Direct-API guard: a tenant with no involvement in the campaign must not even
    // learn it exists — 404 instead of a redacted payload.
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.getCampaignApprovalDetails(CAMPAIGN_ID))
        .isInstanceOf(com.mw.planner.exception.campaign.CampaignNotFoundException.class);
  }

  @Test
  void details_mediaOwnerTypeWithoutProposal_companyAccessOnly_budgetIsNull() {
    campaign.setCompanyAccess(new ArrayList<>(List.of(MO_NOVA)));
    asTenant(MO_NOVA, true);

    CampaignApprovalDetailsResponseDTO details = service.getCampaignApprovalDetails(CAMPAIGN_ID);

    assertThat(details.getBudget()).isNull();
  }

  @Test
  void details_tenantTypeLookupFailsOrNull_failsClosed_budgetIsNull() {
    // Unresolvable company type (null lookup or IAM error) must fail closed on the
    // financial boundary for a non-creator tenant without a proposal: such a tenant is
    // not an involved party, so it gets a 404 and buyer financials can never leak.
    String ghost = "mo-ghost";
    lenient().when(companyService.getCompanyLookupWithCompanyId(ghost)).thenReturn(null);
    asTenant(ghost, true);

    // An unresolvable, uninvolved tenant fails closed all the way to 404.
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.getCampaignApprovalDetails(CAMPAIGN_ID))
        .isInstanceOf(com.mw.planner.exception.campaign.CampaignNotFoundException.class);

    lenient()
        .when(companyService.getCompanyLookupWithCompanyId(ghost))
        .thenThrow(new RuntimeException("IAM down"));
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.getCampaignApprovalDetails(CAMPAIGN_ID))
        .isInstanceOf(com.mw.planner.exception.campaign.CampaignNotFoundException.class);
  }

  @Test
  void details_buyerCreatorTenant_seesBudget() {
    asBuyerCreator();

    CampaignApprovalDetailsResponseDTO details = service.getCampaignApprovalDetails(CAMPAIGN_ID);

    assertThat(details.getBudget()).isEqualTo(BUDGET);
    // Buyer sees the full (non-skipped) stage structure.
    assertThat(details.getApprovalProgress()).hasSize(2);
  }

  @Test
  void details_globalAdmin_seesBudget() {
    asGlobalAdmin();

    CampaignApprovalDetailsResponseDTO details = service.getCampaignApprovalDetails(CAMPAIGN_ID);

    assertThat(details.getBudget()).isEqualTo(BUDGET);
  }

  @Test
  void details_invalidTenantHeader_fallsBackToIamActiveCompany() {
    asBuyerCreator();
    when(userService.isTenantOfCompany(MO_LUMINA)).thenReturn(false);

    CampaignApprovalDetailsResponseDTO details = service.getCampaignApprovalDetails(CAMPAIGN_ID);

    // Header rejected -> viewer is still the buyer agency -> budget visible.
    assertThat(details.getBudget()).isEqualTo(BUDGET);
  }

  // ---------------------------------------------------------------------------------
  // Fixture helpers
  // ---------------------------------------------------------------------------------

  private static CampaignProposalStatus proposal(
      String id, String mediaOwnerId, CampaignProposalStatus.Status status) {
    CampaignProposalStatus p = new CampaignProposalStatus();
    p.setId(id);
    p.setCampaignId(CAMPAIGN_ID);
    p.setMediaOwnerId(mediaOwnerId);
    p.setStatus(status);
    p.setInventoryIds(List.of("inv-" + mediaOwnerId));
    return p;
  }

  private static CampaignInventorySchedules ownerSchedules(
      String mediaOwnerId, String scheduleId, int historySize) {
    CampaignInventorySchedules s = new CampaignInventorySchedules();
    s.setCampaignId(CAMPAIGN_ID);
    s.setMediaOwnerId(mediaOwnerId);
    s.setScheduleIds(List.of(scheduleId));
    List<CampaignInventorySchedules.History> history = new ArrayList<>();
    for (int i = 0; i < historySize; i++) {
      history.add(new CampaignInventorySchedules.History());
    }
    s.setHistory(history);
    return s;
  }

  private static Schedule schedule(String id, Double basePrice) {
    Schedule s = new Schedule();
    s.setId(id);
    s.setBasePrice(basePrice);
    return s;
  }

  private static CompanyLookupResponseDTO company(String name, String type) {
    return CompanyLookupResponseDTO.builder().name(name).companyType(type).build();
  }
}
