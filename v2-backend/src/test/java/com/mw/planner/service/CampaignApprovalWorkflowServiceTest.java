package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.*;
import com.mw.planner.dto.CampaignApprovalDetailsResponseDTO;
import com.mw.planner.dto.CampaignApprovalStatusUpdateRequestDTO;
import com.mw.planner.dto.CompanyLookupResponseDTO;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.dto.UserResponseDTO;
import com.mw.planner.dto.ads.AdsSubmissionResponseDTO;
import com.mw.planner.exception.campaign.WorkflowInvalidStatusException;
import com.mw.planner.repository.CampaignApprovalHistoryRepository;
import com.mw.planner.repository.CampaignApprovedWorkflowStatusRepository;
import com.mw.planner.repository.CampaignInventorySchedulesRepository;
import com.mw.planner.repository.CampaignProposalStatusRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CampaignApprovalWorkflowServiceTest {

  @Mock private CampaignService campaignService;
  @Mock private CampaignInventorySchedulesRepository campaignInventorySchedulesRepository;
  @Mock private CampaignProposalStatusRepository campaignProposalStatusRepository;
  @Mock private CampaignApprovedWorkflowStatusRepository campaignApprovedWorkflowStatusRepository;
  @Mock private CampaignApprovalHistoryRepository campaignApprovalHistoryRepository;
  @Mock private MWAdsService mwAdsService;
  @Mock private CampaignProposalStatusAndCommentService campaignProposalStatusAndCommentService;
  @Mock private UserService userService;
  @Mock private CampaignActivityService campaignActivityService;
  @Mock private CompanyService companyService;

  @InjectMocks private CampaignApprovalWorkflowService campaignApprovalWorkflowService;

  private Campaign testCampaign;
  private CampaignApprovedWorkflowStatus agencyWorkflowStatus;
  private CampaignApprovedWorkflowStatus internalWorkflowStatus;
  private CampaignApprovedWorkflowStatus mediaOwnerWorkflowStatus;

  @BeforeEach
  void setUp() {
    // The 2-stage flow is always on (Agency stage skipped). The flag is hardcoded in the
    // service, so there is no longer a seam to exercise the legacy 3-stage flow from a unit test.
    testCampaign = new Campaign();
    testCampaign.setId("campaign123");
    testCampaign.setStatus(Campaign.Status.REVIEWING);
    testCampaign.setCompanyId("company123");
    // The service loads campaigns through the mode-checked loader; individual tests
    // override this default stub with their own campaign objects where needed.
    lenient().when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(testCampaign);

    agencyWorkflowStatus = new CampaignApprovedWorkflowStatus();
    agencyWorkflowStatus.setId("agencyWorkflowId");
    agencyWorkflowStatus.setCampaignId("campaign123");
    agencyWorkflowStatus.setApprovalAuthority(
        CampaignApprovedWorkflowStatus.ApprovalAuthority.AGENCY);
    agencyWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.SKIPPED);

    internalWorkflowStatus = new CampaignApprovedWorkflowStatus();
    internalWorkflowStatus.setId("internalWorkflowId");
    internalWorkflowStatus.setCampaignId("campaign123");
    internalWorkflowStatus.setApprovalAuthority(
        CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL);
    internalWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);

    mediaOwnerWorkflowStatus = new CampaignApprovedWorkflowStatus();
    mediaOwnerWorkflowStatus.setId("mediaOwnerWorkflowId");
    mediaOwnerWorkflowStatus.setCampaignId("campaign123");
    mediaOwnerWorkflowStatus.setApprovalAuthority(
        CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER);
    mediaOwnerWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.PENDING);
  }

  @AfterEach
  void tearDown() {
    reset(
        campaignService,
        campaignInventorySchedulesRepository,
        campaignProposalStatusRepository,
        campaignApprovedWorkflowStatusRepository,
        campaignApprovalHistoryRepository,
        mwAdsService,
        campaignProposalStatusAndCommentService,
        userService,
        campaignActivityService,
        companyService);
  }

  @Test
  void submitCampaignForReview_Success() {
    String campaignId = "campaign123";

    // Arrange
    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCountryId("Japan");
    campaign.setStatus(Campaign.Status.PLANNED);
    campaign.setUserId("user123");

    CampaignInventorySchedules schedule = new CampaignInventorySchedules();
    schedule.setCampaignId(campaignId);
    schedule.setMediaOwnerId("mediaOwner");
    schedule.setInventoryId("invId13");

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(campaign);
    doNothing().when(campaignService).changeCampaignStatus(campaignId, Campaign.Status.REVIEWING);

    when(campaignInventorySchedulesRepository.findByCampaignId(campaignId))
        .thenReturn(List.of(schedule));
    when(campaignInventorySchedulesRepository.countByCampaignId(campaignId))
        .thenReturn(1L); // ✅ at least one inventory count

    when(campaignApprovedWorkflowStatusRepository.saveAll(anyList())).thenReturn(List.of());
    when(campaignProposalStatusRepository.saveAll(anyList())).thenReturn(List.of());

    IamUserContext userContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .firstName("Test")
            .lastName("User")
            .build();
    when(userService.getIamUserContext()).thenReturn(userContext);
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(
            inv -> inv.getArgument(0) != null ? inv.getArgument(0) : userContext.getCompanyId());
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // Act
    campaignApprovalWorkflowService.submitCampaignForReview(campaignId);

    // Assert
    verify(campaignService, times(1)).findByIdForCurrentMode(campaignId);
    verify(campaignService, times(1)).changeCampaignStatus(campaignId, Campaign.Status.REVIEWING);
    verify(campaignApprovedWorkflowStatusRepository, times(1)).saveAll(anyList());
    verify(campaignProposalStatusRepository, times(1)).saveAll(anyList());
  }

  @Test
  void updateApprovalStatus_AgencyApproval_TwoStage_ThrowsBecauseAgencyIsSkipped() {
    String workflowStatusId = "agencyWorkflowId";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.APPROVED)
            .comment("Approved by agency")
            .build();

    when(campaignApprovedWorkflowStatusRepository.findById(workflowStatusId))
        .thenReturn(Optional.of(agencyWorkflowStatus)); // status is SKIPPED per setUp()

    assertThatThrownBy(
            () -> campaignApprovalWorkflowService.updateApprovalStatus(workflowStatusId, request))
        .isInstanceOf(WorkflowInvalidStatusException.class);

    verify(campaignApprovalHistoryRepository, never()).save(any(CampaignApprovalHistory.class));
  }

  @Test
  void updateApprovalStatus_AgencyRejection_TwoStage_ThrowsBecauseAgencyIsSkipped() {
    String workflowStatusId = "agencyWorkflowId";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.REJECTED)
            .comment("Rejected by agency")
            .build();

    when(campaignApprovedWorkflowStatusRepository.findById(workflowStatusId))
        .thenReturn(Optional.of(agencyWorkflowStatus)); // status is SKIPPED per setUp()

    assertThatThrownBy(
            () -> campaignApprovalWorkflowService.updateApprovalStatus(workflowStatusId, request))
        .isInstanceOf(WorkflowInvalidStatusException.class);

    verify(campaignApprovalHistoryRepository, never()).save(any(CampaignApprovalHistory.class));
  }

  @Test
  void updateApprovalStatus_AgencyChangeRequest_TwoStage_ThrowsBecauseAgencyIsSkipped() {
    String workflowStatusId = "agencyWorkflowId";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.IN_NEGOTIATION)
            .comment("Changes requested by agency")
            .build();

    when(campaignApprovedWorkflowStatusRepository.findById(workflowStatusId))
        .thenReturn(Optional.of(agencyWorkflowStatus)); // status is SKIPPED per setUp()

    assertThatThrownBy(
            () -> campaignApprovalWorkflowService.updateApprovalStatus(workflowStatusId, request))
        .isInstanceOf(WorkflowInvalidStatusException.class);

    verify(campaignApprovalHistoryRepository, never()).save(any(CampaignApprovalHistory.class));
  }

  @Test
  void updateApprovalStatus_InternalApproval_Success() {
    String workflowStatusId = "internalWorkflowId";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.APPROVED)
            .comment("Approved by internal")
            .build();

    when(campaignApprovedWorkflowStatusRepository.findById(workflowStatusId))
        .thenReturn(Optional.of(internalWorkflowStatus));
    when(campaignInventorySchedulesRepository
            .existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull("campaign123"))
        .thenReturn(false);
    when(campaignProposalStatusRepository.findStatusesByCampaignId("campaign123"))
        .thenReturn(Collections.emptyList());
    when(campaignApprovedWorkflowStatusRepository.findByCampaignIdAndApprovalAuthority(
            eq("campaign123"), eq(CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER)))
        .thenReturn(Optional.of(mediaOwnerWorkflowStatus));
    when(campaignApprovedWorkflowStatusRepository.save(any(CampaignApprovedWorkflowStatus.class)))
        .thenReturn(internalWorkflowStatus);
    when(campaignApprovalHistoryRepository.save(any(CampaignApprovalHistory.class)))
        .thenReturn(new CampaignApprovalHistory());

    IamUserContext userContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .firstName("Test")
            .lastName("User")
            .build();
    when(userService.getIamUserContext()).thenReturn(userContext);
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(
            inv -> inv.getArgument(0) != null ? inv.getArgument(0) : userContext.getCompanyId());
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    campaignApprovalWorkflowService.updateApprovalStatus(workflowStatusId, request);

    verify(campaignApprovedWorkflowStatusRepository, times(1)).findById(workflowStatusId);
    verify(campaignApprovalHistoryRepository, times(1)).save(any(CampaignApprovalHistory.class));
    verify(campaignApprovedWorkflowStatusRepository, atLeastOnce())
        .save(any(CampaignApprovedWorkflowStatus.class));
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void updateApprovalStatus_InternalRejection_Success() {
    String workflowStatusId = "internalWorkflowId";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.REJECTED)
            .comment("Rejected by internal")
            .build();

    when(campaignApprovedWorkflowStatusRepository.findById(workflowStatusId))
        .thenReturn(Optional.of(internalWorkflowStatus));
    when(campaignInventorySchedulesRepository
            .existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull("campaign123"))
        .thenReturn(false);
    when(campaignApprovedWorkflowStatusRepository.save(any(CampaignApprovedWorkflowStatus.class)))
        .thenReturn(internalWorkflowStatus);
    when(campaignApprovalHistoryRepository.save(any(CampaignApprovalHistory.class)))
        .thenReturn(new CampaignApprovalHistory());

    IamUserContext userContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .firstName("Test")
            .lastName("User")
            .build();
    when(userService.getIamUserContext()).thenReturn(userContext);
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(
            inv -> inv.getArgument(0) != null ? inv.getArgument(0) : userContext.getCompanyId());
    doNothing().when(campaignService).changeCampaignStatus("campaign123", Campaign.Status.REJECTED);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    campaignApprovalWorkflowService.updateApprovalStatus(workflowStatusId, request);

    verify(campaignApprovedWorkflowStatusRepository, times(1)).findById(workflowStatusId);
    verify(campaignService, times(1)).changeCampaignStatus("campaign123", Campaign.Status.REJECTED);
    verify(campaignApprovalHistoryRepository, times(1)).save(any(CampaignApprovalHistory.class));
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void updateApprovalStatus_InternalChangeRequest_TwoStage_NoResetSinceInternalIsEntry() {
    String workflowStatusId = "internalWorkflowId";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.IN_NEGOTIATION)
            .comment("Changes requested by internal")
            .build();

    when(campaignApprovedWorkflowStatusRepository.findById(workflowStatusId))
        .thenReturn(Optional.of(internalWorkflowStatus));
    when(campaignInventorySchedulesRepository
            .existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull("campaign123"))
        .thenReturn(false);
    when(campaignApprovedWorkflowStatusRepository.save(any(CampaignApprovedWorkflowStatus.class)))
        .thenReturn(internalWorkflowStatus);
    when(campaignApprovalHistoryRepository.save(any(CampaignApprovalHistory.class)))
        .thenReturn(new CampaignApprovalHistory());

    IamUserContext userContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .firstName("Test")
            .lastName("User")
            .build();
    when(userService.getIamUserContext()).thenReturn(userContext);
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(
            inv -> inv.getArgument(0) != null ? inv.getArgument(0) : userContext.getCompanyId());
    doNothing()
        .when(campaignService)
        .changeCampaignStatus("campaign123", Campaign.Status.NEGOTIATING);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    campaignApprovalWorkflowService.updateApprovalStatus(workflowStatusId, request);

    verify(campaignService, times(1))
        .changeCampaignStatus("campaign123", Campaign.Status.NEGOTIATING);
    // No reset: Internal is the 2-stage entry stage, so Agency and Media Owner must never be
    // looked up (resetApprovalWorkflowStatus must not run)
    verify(campaignApprovedWorkflowStatusRepository, never())
        .findByCampaignIdAndApprovalAuthority(
            eq("campaign123"), eq(CampaignApprovedWorkflowStatus.ApprovalAuthority.AGENCY));
    verify(campaignApprovedWorkflowStatusRepository, never())
        .findByCampaignIdAndApprovalAuthority(
            eq("campaign123"), eq(CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER));
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void updateApprovalStatus_MediaOwnerChangeRequest_ResetsWholeWorkflow() {
    // The acted-on Media Owner stage must be active, matching the server-side rule
    mediaOwnerWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
    String workflowStatusId = "mediaOwnerWorkflowId";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.IN_NEGOTIATION)
            .comment("Changes requested by media owner")
            .build();

    when(campaignApprovedWorkflowStatusRepository.findById(workflowStatusId))
        .thenReturn(Optional.of(mediaOwnerWorkflowStatus));
    when(campaignInventorySchedulesRepository
            .existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull("campaign123"))
        .thenReturn(false);
    when(campaignApprovedWorkflowStatusRepository.findByCampaignIdAndApprovalAuthority(
            eq("campaign123"), eq(CampaignApprovedWorkflowStatus.ApprovalAuthority.AGENCY)))
        .thenReturn(Optional.of(agencyWorkflowStatus));
    when(campaignApprovedWorkflowStatusRepository.findByCampaignIdAndApprovalAuthority(
            eq("campaign123"), eq(CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL)))
        .thenReturn(Optional.of(internalWorkflowStatus));
    when(campaignApprovedWorkflowStatusRepository.findByCampaignIdAndApprovalAuthority(
            eq("campaign123"), eq(CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER)))
        .thenReturn(Optional.of(mediaOwnerWorkflowStatus));
    when(campaignApprovedWorkflowStatusRepository.save(any(CampaignApprovedWorkflowStatus.class)))
        .thenReturn(mediaOwnerWorkflowStatus);
    when(campaignApprovalHistoryRepository.save(any(CampaignApprovalHistory.class)))
        .thenReturn(new CampaignApprovalHistory());
    // The acting company must hold MEDIA_OWNER authority: it owns a proposal
    CampaignProposalStatus actingProposal = new CampaignProposalStatus();
    actingProposal.setCampaignId("campaign123");
    actingProposal.setMediaOwnerId("company123");
    actingProposal.setStatus(CampaignProposalStatus.Status.PENDING);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", "company123"))
        .thenReturn(actingProposal);

    IamUserContext userContext =
        IamUserContext.builder().id("user123").companyId("company123").isGlobalAdmin(false).build();
    when(userService.getIamUserContext()).thenReturn(userContext);
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(
            inv -> inv.getArgument(0) != null ? inv.getArgument(0) : userContext.getCompanyId());
    doNothing()
        .when(campaignService)
        .changeCampaignStatus("campaign123", Campaign.Status.NEGOTIATING);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    campaignApprovalWorkflowService.updateApprovalStatus(workflowStatusId, request);

    // Reset restores Agency to SKIPPED (2-stage) and Internal to IN_PROGRESS (the entry stage)
    verify(campaignApprovedWorkflowStatusRepository, atLeastOnce())
        .save(
            argThat(
                status ->
                    status.getApprovalAuthority()
                            == CampaignApprovedWorkflowStatus.ApprovalAuthority.AGENCY
                        && status.getStatus() == CampaignApprovedWorkflowStatus.Status.SKIPPED));
    verify(campaignApprovedWorkflowStatusRepository, atLeastOnce())
        .save(
            argThat(
                status ->
                    status.getApprovalAuthority()
                            == CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL
                        && status.getStatus()
                            == CampaignApprovedWorkflowStatus.Status.IN_PROGRESS));
  }

  @Test
  void
      updateApprovalStatus_MediaOwnerChangeRequest_MediaOwnerCreated_ResetsToMediaOwnerEntryStage() {
    // The acted-on Media Owner stage must be active, matching the server-side rule
    mediaOwnerWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
    String workflowStatusId = "mediaOwnerWorkflowId";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.IN_NEGOTIATION)
            .comment("Changes requested by media owner")
            .build();

    // Internal is SKIPPED — the durable signal that this campaign was created by a media-owner
    // company, so the reset must land Media Owner back on IN_PROGRESS, not PENDING.
    internalWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.SKIPPED);

    when(campaignApprovedWorkflowStatusRepository.findById(workflowStatusId))
        .thenReturn(Optional.of(mediaOwnerWorkflowStatus));
    when(campaignInventorySchedulesRepository
            .existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull("campaign123"))
        .thenReturn(false);
    when(campaignApprovedWorkflowStatusRepository.findByCampaignIdAndApprovalAuthority(
            eq("campaign123"), eq(CampaignApprovedWorkflowStatus.ApprovalAuthority.AGENCY)))
        .thenReturn(Optional.of(agencyWorkflowStatus));
    when(campaignApprovedWorkflowStatusRepository.findByCampaignIdAndApprovalAuthority(
            eq("campaign123"), eq(CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL)))
        .thenReturn(Optional.of(internalWorkflowStatus));
    when(campaignApprovedWorkflowStatusRepository.findByCampaignIdAndApprovalAuthority(
            eq("campaign123"), eq(CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER)))
        .thenReturn(Optional.of(mediaOwnerWorkflowStatus));
    when(campaignApprovedWorkflowStatusRepository.save(any(CampaignApprovedWorkflowStatus.class)))
        .thenReturn(mediaOwnerWorkflowStatus);
    when(campaignApprovalHistoryRepository.save(any(CampaignApprovalHistory.class)))
        .thenReturn(new CampaignApprovalHistory());
    // The acting company must hold MEDIA_OWNER authority: it owns a proposal
    CampaignProposalStatus actingProposal = new CampaignProposalStatus();
    actingProposal.setCampaignId("campaign123");
    actingProposal.setMediaOwnerId("company123");
    actingProposal.setStatus(CampaignProposalStatus.Status.PENDING);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", "company123"))
        .thenReturn(actingProposal);

    IamUserContext userContext =
        IamUserContext.builder().id("user123").companyId("company123").isGlobalAdmin(false).build();
    when(userService.getIamUserContext()).thenReturn(userContext);
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(
            inv -> inv.getArgument(0) != null ? inv.getArgument(0) : userContext.getCompanyId());
    doNothing()
        .when(campaignService)
        .changeCampaignStatus("campaign123", Campaign.Status.NEGOTIATING);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    campaignApprovalWorkflowService.updateApprovalStatus(workflowStatusId, request);

    assertThat(agencyWorkflowStatus.getStatus())
        .isEqualTo(CampaignApprovedWorkflowStatus.Status.SKIPPED);
    assertThat(internalWorkflowStatus.getStatus())
        .isEqualTo(CampaignApprovedWorkflowStatus.Status.SKIPPED);
    verify(campaignApprovedWorkflowStatusRepository, atLeastOnce())
        .save(
            argThat(
                status ->
                    status.getApprovalAuthority()
                            == CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER
                        && status.getStatus()
                            == CampaignApprovedWorkflowStatus.Status.IN_PROGRESS));
  }

  @Test
  void updateApprovalStatus_MediaOwnerApproval_SetsCampaignApproved() {
    // The acted-on Media Owner stage must be active, matching the server-side rule
    mediaOwnerWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
    String workflowStatusId = "mediaOwnerWorkflowId";
    String mediaOwnerId = "mediaOwner123";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.APPROVED)
            .comment("Approved by media owner")
            .build();

    IamUserContext iamUserContext =
        IamUserContext.builder()
            .id("user123")
            .companyId(mediaOwnerId)
            .locale(Locale.ENGLISH)
            .build();

    CampaignProposalStatus proposalStatus = new CampaignProposalStatus();
    proposalStatus.setId("proposal123");
    proposalStatus.setCampaignId("campaign123");
    proposalStatus.setMediaOwnerId(mediaOwnerId);
    proposalStatus.setInventoryIds(Collections.singletonList("inventory123"));
    proposalStatus.setStatus(CampaignProposalStatus.Status.PENDING);

    AdsSubmissionResponseDTO adsResponse =
        AdsSubmissionResponseDTO.builder()
            .status(201)
            .message("Campaign submitted successfully")
            .total(1)
            .successful(1)
            .failed(0)
            .build();

    when(campaignApprovedWorkflowStatusRepository.findById(workflowStatusId))
        .thenReturn(Optional.of(mediaOwnerWorkflowStatus));
    when(campaignInventorySchedulesRepository
            .existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull("campaign123"))
        .thenReturn(false);
    when(campaignApprovedWorkflowStatusRepository.save(any(CampaignApprovedWorkflowStatus.class)))
        .thenReturn(mediaOwnerWorkflowStatus);
    when(campaignApprovalHistoryRepository.save(any(CampaignApprovalHistory.class)))
        .thenReturn(new CampaignApprovalHistory());
    doNothing().when(campaignService).changeCampaignStatus("campaign123", Campaign.Status.APPROVED);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));
    when(userService.getIamUserContext()).thenReturn(iamUserContext);
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(
            inv -> inv.getArgument(0) != null ? inv.getArgument(0) : iamUserContext.getCompanyId());
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", mediaOwnerId))
        .thenReturn(proposalStatus);
    when(mwAdsService.submitApprovedCampaignToAds(
            "campaign123", Collections.singletonList("inventory123")))
        .thenReturn(adsResponse);
    when(campaignProposalStatusRepository.save(any(CampaignProposalStatus.class)))
        .thenReturn(proposalStatus);

    campaignApprovalWorkflowService.updateApprovalStatus(workflowStatusId, request);

    verify(campaignService, times(1)).changeCampaignStatus("campaign123", Campaign.Status.APPROVED);
    verify(campaignApprovalHistoryRepository, times(1)).save(any(CampaignApprovalHistory.class));
    verify(userService, times(1)).getIamUserContext();
    verify(campaignProposalStatusAndCommentService, atLeastOnce())
        .getProposalsByCampaignIdAndMediaOwnerId("campaign123", mediaOwnerId);
    verify(mwAdsService, times(1))
        .submitApprovedCampaignToAds("campaign123", Collections.singletonList("inventory123"));
    verify(campaignProposalStatusRepository, times(1)).save(any(CampaignProposalStatus.class));
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void updateApprovalStatus_WorkflowStatusNotFound_ThrowsException() {
    String workflowStatusId = "nonExistentWorkflowId";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.APPROVED)
            .comment("Test comment")
            .build();

    when(campaignApprovedWorkflowStatusRepository.findById(workflowStatusId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> campaignApprovalWorkflowService.updateApprovalStatus(workflowStatusId, request))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Workflow status not found for ID: " + workflowStatusId);

    verify(campaignApprovedWorkflowStatusRepository, times(1)).findById(workflowStatusId);
    verify(campaignApprovalHistoryRepository, never()).save(any(CampaignApprovalHistory.class));
  }

  @Test
  void getCampaignApprovalDetails_Success_WithWorkflowStatuses() {
    // Given
    String campaignId = "campaign123";
    String userCompanyId = "company123";
    LocalDate startDate = LocalDate.now();
    LocalDate endDate = startDate.plusDays(30);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setName("Test Campaign");
    campaign.setUserId("user123");
    campaign.setBudget(50000.0);
    campaign.setStartDate(startDate);
    campaign.setEndDate(endDate);
    campaign.setStatus(Campaign.Status.REVIEWING);
    campaign.setCompanyId(userCompanyId);
    campaign.setIsNegotiated(true);

    // This test covers the general 3-document response shape, not 2-stage skip behavior, so all
    // three stages are made visible (non-SKIPPED) regardless of setUp()'s 2-stage default.
    agencyWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
    internalWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.PENDING);

    IamUserContext iamUserContext =
        IamUserContext.builder()
            .id("user123")
            .companyId(userCompanyId)
            .locale(Locale.ENGLISH)
            .build();

    UserResponseDTO userResponse =
        UserResponseDTO.builder().id("user123").firstName("Test").lastName("User").build();

    List<CampaignApprovedWorkflowStatus> workflowStatuses =
        Arrays.asList(agencyWorkflowStatus, internalWorkflowStatus, mediaOwnerWorkflowStatus);

    CampaignApprovalHistory history1 = new CampaignApprovalHistory();
    history1.setComment("Agency approved");
    history1.setCreatedAt(LocalDateTime.now().minusDays(1));

    CampaignApprovalHistory history2 = new CampaignApprovalHistory();
    history2.setComment("Internal review comment");
    history2.setCreatedAt(LocalDateTime.now());

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(campaign);
    when(companyService.getCompanyLookupWithCompanyId(anyString()))
        .thenReturn(CompanyLookupResponseDTO.builder().companyType("ADVERTISER").build());
    when(userService.getIamUserContext()).thenReturn(iamUserContext);
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(
            inv -> inv.getArgument(0) != null ? inv.getArgument(0) : iamUserContext.getCompanyId());
    when(userService.getUserById("user123")).thenReturn(userResponse);
    when(campaignService.resolveCampaignStatus(
            eq(campaign), any(UserResponseDTO.class), eq(userCompanyId)))
        .thenReturn(Campaign.Status.REVIEWING);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, userCompanyId))
        .thenReturn(null);
    when(campaignApprovedWorkflowStatusRepository.findByCampaignId(campaignId))
        .thenReturn(workflowStatuses);
    when(campaignApprovalHistoryRepository.findByCampaignApprovedWorkflowStatusId(
            "agencyWorkflowId"))
        .thenReturn(Collections.singletonList(history1));
    when(campaignApprovalHistoryRepository.findByCampaignApprovedWorkflowStatusId(
            "internalWorkflowId"))
        .thenReturn(Collections.singletonList(history2));
    when(campaignApprovalHistoryRepository.findByCampaignApprovedWorkflowStatusId(
            "mediaOwnerWorkflowId"))
        .thenReturn(Collections.emptyList());
    when(campaignProposalStatusRepository.findStatusesByCampaignId(campaignId))
        .thenReturn(Collections.emptyList());

    // When
    CampaignApprovalDetailsResponseDTO result =
        campaignApprovalWorkflowService.getCampaignApprovalDetails(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getCampaignId()).isEqualTo(campaignId);
    assertThat(result.getCampaignName()).isEqualTo("Test Campaign");
    assertThat(result.getBudget()).isEqualTo(50000.0);
    assertThat(result.getApprovalProgress()).isNotNull();
    assertThat(result.getApprovalProgress()).hasSize(3);
    assertThat(result.getIsNegotiated()).isTrue();

    verify(campaignService, times(1)).findByIdForCurrentMode(campaignId);
    verify(userService, times(1)).getIamUserContext();
    verify(campaignApprovedWorkflowStatusRepository, times(2)).findByCampaignId(campaignId);
  }

  @Test
  void getCampaignApprovalDetails_Success_WithNoWorkflowStatuses() {
    // Given
    String campaignId = "campaign123";
    String userCompanyId = "company123";
    LocalDate startDate = LocalDate.now();
    LocalDate endDate = startDate.plusDays(15);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setName("Test Campaign");
    campaign.setUserId("user123");
    campaign.setBudget(25000.0);
    campaign.setStartDate(startDate);
    campaign.setEndDate(endDate);
    campaign.setStatus(Campaign.Status.DRAFT);
    campaign.setCompanyId(userCompanyId);

    IamUserContext iamUserContext =
        IamUserContext.builder()
            .id("user123")
            .companyId(userCompanyId)
            .locale(Locale.ENGLISH)
            .build();

    UserResponseDTO userResponse =
        UserResponseDTO.builder().id("user123").firstName("Test").lastName("User").build();

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(campaign);
    when(companyService.getCompanyLookupWithCompanyId(anyString()))
        .thenReturn(CompanyLookupResponseDTO.builder().companyType("ADVERTISER").build());
    when(userService.getIamUserContext()).thenReturn(iamUserContext);
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(
            inv -> inv.getArgument(0) != null ? inv.getArgument(0) : iamUserContext.getCompanyId());
    when(userService.getUserById("user123")).thenReturn(userResponse);
    when(campaignService.resolveCampaignStatus(
            eq(campaign), any(UserResponseDTO.class), eq(userCompanyId)))
        .thenReturn(Campaign.Status.DRAFT);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, userCompanyId))
        .thenReturn(null);
    when(campaignApprovedWorkflowStatusRepository.findByCampaignId(campaignId))
        .thenReturn(Collections.emptyList());
    when(campaignProposalStatusRepository.findStatusesByCampaignId(campaignId))
        .thenReturn(Collections.emptyList());

    // When
    CampaignApprovalDetailsResponseDTO result =
        campaignApprovalWorkflowService.getCampaignApprovalDetails(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getCampaignId()).isEqualTo(campaignId);
    assertThat(result.getCampaignName()).isEqualTo("Test Campaign");
    assertThat(result.getBudget()).isEqualTo(25000.0);
    assertThat(result.getApprovalProgress()).isNotNull();
    assertThat(result.getApprovalProgress()).isEmpty();

    verify(campaignService, times(1)).findByIdForCurrentMode(campaignId);
    verify(userService, times(1)).getIamUserContext();
    verify(campaignApprovedWorkflowStatusRepository, times(2)).findByCampaignId(campaignId);
  }

  @Test
  void getCampaignApprovalDetails_Success_WithApprovalHistoryComments() {
    // Given
    String campaignId = "campaign123";
    String userCompanyId = "company123";
    LocalDate startDate = LocalDate.now();
    LocalDate endDate = startDate.plusDays(10);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setName("Campaign with Comments");
    campaign.setUserId("user123");
    campaign.setBudget(10000.0);
    campaign.setStartDate(startDate);
    campaign.setEndDate(endDate);
    campaign.setStatus(Campaign.Status.REVIEWING);
    campaign.setCompanyId(userCompanyId);

    IamUserContext iamUserContext =
        IamUserContext.builder()
            .id("user123")
            .companyId(userCompanyId)
            .locale(Locale.ENGLISH)
            .build();

    UserResponseDTO userResponse =
        UserResponseDTO.builder().id("user123").firstName("Test").lastName("User").build();

    // This test covers latest-comment resolution for a single stage, not 2-stage skip behavior,
    // so Agency is made visible (non-SKIPPED) regardless of setUp()'s 2-stage default.
    agencyWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
    agencyWorkflowStatus.setCreatedBy("user1");
    agencyWorkflowStatus.setCreatedAt(LocalDateTime.now().minusDays(2));

    CampaignApprovalHistory latestHistory = new CampaignApprovalHistory();
    latestHistory.setComment("Latest approval comment");
    latestHistory.setCreatedAt(LocalDateTime.now().minusHours(1));

    CampaignApprovalHistory olderHistory = new CampaignApprovalHistory();
    olderHistory.setComment("Older comment");
    olderHistory.setCreatedAt(LocalDateTime.now().minusDays(1));

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(campaign);
    when(companyService.getCompanyLookupWithCompanyId(anyString()))
        .thenReturn(CompanyLookupResponseDTO.builder().companyType("ADVERTISER").build());
    when(userService.getIamUserContext()).thenReturn(iamUserContext);
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(
            inv -> inv.getArgument(0) != null ? inv.getArgument(0) : iamUserContext.getCompanyId());
    when(userService.getUserById("user123")).thenReturn(userResponse);
    when(campaignService.resolveCampaignStatus(
            eq(campaign), any(UserResponseDTO.class), eq(userCompanyId)))
        .thenReturn(Campaign.Status.REVIEWING);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, userCompanyId))
        .thenReturn(null);
    when(campaignApprovedWorkflowStatusRepository.findByCampaignId(campaignId))
        .thenReturn(Collections.singletonList(agencyWorkflowStatus));
    when(campaignApprovalHistoryRepository.findByCampaignApprovedWorkflowStatusId(
            "agencyWorkflowId"))
        .thenReturn(Arrays.asList(olderHistory, latestHistory));
    when(campaignProposalStatusRepository.findStatusesByCampaignId(campaignId))
        .thenReturn(Collections.emptyList());

    // When
    CampaignApprovalDetailsResponseDTO result =
        campaignApprovalWorkflowService.getCampaignApprovalDetails(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getApprovalProgress()).hasSize(1);
    assertThat(result.getApprovalProgress().get(0).getComment())
        .isEqualTo("Latest approval comment"); // Should return the latest comment

    verify(campaignApprovalHistoryRepository, times(1))
        .findByCampaignApprovedWorkflowStatusId("agencyWorkflowId");
  }

  @Test
  void getCampaignApprovalDetails_CampaignNotFound_ThrowsException() {
    // Given
    String campaignId = "nonExistentCampaign";
    when(campaignService.findByIdForCurrentMode(campaignId))
        .thenThrow(new com.mw.planner.exception.campaign.CampaignNotFoundException(campaignId));

    // When & Then
    assertThatThrownBy(() -> campaignApprovalWorkflowService.getCampaignApprovalDetails(campaignId))
        .isInstanceOf(com.mw.planner.exception.campaign.CampaignNotFoundException.class);

    verify(campaignService, times(1)).findByIdForCurrentMode(campaignId);
    verify(userService, never()).getIamUserContext();
    verify(campaignApprovedWorkflowStatusRepository, never()).findByCampaignId(anyString());
  }

  @Test
  void getCampaignApprovalDetails_Success_WithApprovedStatus() {
    // Given
    String campaignId = "campaign123";
    String userCompanyId = "company123";
    LocalDate startDate = LocalDate.now();
    LocalDate endDate = startDate.plusDays(20);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setName("Approved Campaign");
    campaign.setUserId("user123");
    campaign.setBudget(75000.0);
    campaign.setStartDate(startDate);
    campaign.setEndDate(endDate);
    campaign.setStatus(Campaign.Status.APPROVED);
    campaign.setCompanyId(userCompanyId);

    IamUserContext iamUserContext =
        IamUserContext.builder()
            .id("user123")
            .companyId(userCompanyId)
            .locale(Locale.ENGLISH)
            .build();

    UserResponseDTO userResponse =
        UserResponseDTO.builder().id("user123").firstName("Test").lastName("User").build();

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(campaign);
    when(companyService.getCompanyLookupWithCompanyId(anyString()))
        .thenReturn(CompanyLookupResponseDTO.builder().companyType("ADVERTISER").build());
    when(userService.getIamUserContext()).thenReturn(iamUserContext);
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(
            inv -> inv.getArgument(0) != null ? inv.getArgument(0) : iamUserContext.getCompanyId());
    when(userService.getUserById("user123")).thenReturn(userResponse);
    when(campaignService.resolveCampaignStatus(
            eq(campaign), any(UserResponseDTO.class), eq(userCompanyId)))
        .thenReturn(Campaign.Status.APPROVED);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, userCompanyId))
        .thenReturn(null);
    when(campaignApprovedWorkflowStatusRepository.findByCampaignId(campaignId))
        .thenReturn(Collections.emptyList());
    when(campaignProposalStatusRepository.findStatusesByCampaignId(campaignId))
        .thenReturn(Collections.emptyList());

    // When
    CampaignApprovalDetailsResponseDTO result =
        campaignApprovalWorkflowService.getCampaignApprovalDetails(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(Campaign.Status.APPROVED);

    verify(campaignService, times(1)).findByIdForCurrentMode(campaignId);
    verify(userService, times(1)).getIamUserContext();
  }

  @Test
  void resetWorkflowStatusForResubmission_TwoStage_SkipsAgency() {
    String campaignId = "campaign123";
    List<CampaignApprovedWorkflowStatus> workflowStatuses =
        Arrays.asList(agencyWorkflowStatus, internalWorkflowStatus, mediaOwnerWorkflowStatus);
    agencyWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.COMPLETED);
    internalWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.COMPLETED);

    when(campaignApprovedWorkflowStatusRepository.findByCampaignId(campaignId))
        .thenReturn(workflowStatuses);
    when(campaignApprovedWorkflowStatusRepository.saveAll(anyList())).thenReturn(workflowStatuses);

    campaignApprovalWorkflowService.resetWorkflowStatusForResubmission(campaignId);

    verify(campaignApprovedWorkflowStatusRepository, times(1)).findByCampaignId(campaignId);
    verify(campaignApprovedWorkflowStatusRepository, times(1)).saveAll(workflowStatuses);
    // 2-stage flow: Agency is skipped, not re-opened
    assertThat(agencyWorkflowStatus.getStatus())
        .isEqualTo(CampaignApprovedWorkflowStatus.Status.SKIPPED);
    assertThat(internalWorkflowStatus.getStatus())
        .isEqualTo(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
    assertThat(mediaOwnerWorkflowStatus.getStatus())
        .isEqualTo(CampaignApprovedWorkflowStatus.Status.PENDING);
  }

  @Test
  void resetWorkflowStatusForResubmission_WithNoWorkflow_DoesNothing() {
    String campaignId = "campaign123";
    when(campaignApprovedWorkflowStatusRepository.findByCampaignId(campaignId))
        .thenReturn(Collections.emptyList());

    campaignApprovalWorkflowService.resetWorkflowStatusForResubmission(campaignId);

    verify(campaignApprovedWorkflowStatusRepository, times(1)).findByCampaignId(campaignId);
    verify(campaignApprovedWorkflowStatusRepository, never()).saveAll(anyList());
  }

  @Test
  void resetWorkflowStatusForResubmission_MediaOwnerCreated_KeepsInternalSkipped() {
    String campaignId = "campaign123";
    // Internal is SKIPPED, not COMPLETED/IN_PROGRESS — the durable signal that this workflow was
    // created for a media-owner-created campaign (Agency+Internal never ran).
    agencyWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.SKIPPED);
    internalWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.SKIPPED);
    mediaOwnerWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.REJECTED);
    List<CampaignApprovedWorkflowStatus> workflowStatuses =
        Arrays.asList(agencyWorkflowStatus, internalWorkflowStatus, mediaOwnerWorkflowStatus);

    when(campaignApprovedWorkflowStatusRepository.findByCampaignId(campaignId))
        .thenReturn(workflowStatuses);
    when(campaignApprovedWorkflowStatusRepository.saveAll(anyList())).thenReturn(workflowStatuses);

    campaignApprovalWorkflowService.resetWorkflowStatusForResubmission(campaignId);

    assertThat(agencyWorkflowStatus.getStatus())
        .isEqualTo(CampaignApprovedWorkflowStatus.Status.SKIPPED);
    assertThat(internalWorkflowStatus.getStatus())
        .isEqualTo(CampaignApprovedWorkflowStatus.Status.SKIPPED);
    // Media Owner is the entry stage for a media-owner-created campaign, not PENDING.
    assertThat(mediaOwnerWorkflowStatus.getStatus())
        .isEqualTo(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
  }

  @Test
  void isMaintainer_WhenCompanyMatches_ReturnsTrue() {
    Campaign campaign = new Campaign();
    campaign.setCompanyId("company123");
    assertThat(campaignApprovalWorkflowService.isMaintainer(campaign, "company123")).isTrue();
  }

  @Test
  void isMaintainer_WhenCompanyDiffers_ReturnsFalse() {
    Campaign campaign = new Campaign();
    campaign.setCompanyId("company123");
    assertThat(campaignApprovalWorkflowService.isMaintainer(campaign, "otherCompany")).isFalse();
  }

  @Test
  void isMaintainer_WhenCampaignOrUserCompanyNull_ReturnsFalse() {
    Campaign campaign = new Campaign();
    campaign.setCompanyId("company123");
    assertThat(campaignApprovalWorkflowService.isMaintainer(null, "company123")).isFalse();
    assertThat(campaignApprovalWorkflowService.isMaintainer(campaign, null)).isFalse();
  }

  @Test
  void getCampaignStatus_WhenCampaignNull_ReturnsNull() {
    assertThat(campaignApprovalWorkflowService.getCampaignStatus(null, "company123", false))
        .isNull();
  }

  @Test
  void getCampaignStatus_WhenCampaignRejected_ReturnsRejected() {
    Campaign campaign = new Campaign();
    campaign.setId("campaign123");
    campaign.setStatus(Campaign.Status.REJECTED);
    campaign.setCompanyId("company123");
    assertThat(campaignApprovalWorkflowService.getCampaignStatus(campaign, "company123", false))
        .isEqualTo(com.mw.planner.enums.CampaignApprovalStatus.REJECTED);
  }

  @Test
  void getCampaignStatus_WhenCampaignApproved_ReturnsApproved() {
    Campaign campaign = new Campaign();
    campaign.setId("campaign123");
    campaign.setStatus(Campaign.Status.APPROVED);
    campaign.setCompanyId("company123");
    assertThat(campaignApprovalWorkflowService.getCampaignStatus(campaign, "company123", false))
        .isEqualTo(com.mw.planner.enums.CampaignApprovalStatus.APPROVED);
  }

  @Test
  void getCampaignStatus_WhenMaintainerAndInternalInProgress_ReturnsAwaitingInternalReview() {
    Campaign campaign = new Campaign();
    campaign.setId("campaign123");
    campaign.setStatus(Campaign.Status.REVIEWING);
    campaign.setCompanyId("company123");

    CampaignApprovedWorkflowStatus internalStatus = new CampaignApprovedWorkflowStatus();
    internalStatus.setApprovalAuthority(CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL);
    internalStatus.setStatus(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);

    when(campaignApprovedWorkflowStatusRepository.findByCampaignId("campaign123"))
        .thenReturn(List.of(internalStatus));
    when(campaignProposalStatusRepository.findStatusesByCampaignId("campaign123"))
        .thenReturn(Collections.emptyList());

    assertThat(campaignApprovalWorkflowService.getCampaignStatus(campaign, "company123", false))
        .isEqualTo(com.mw.planner.enums.CampaignApprovalStatus.AWAITING_INTERNAL_REVIEW);
  }

  @Test
  void getCampaignStatus_WhenViewerIsMediaOwner_BypassesMaintainerBranchEvenIfCompanyMatches() {
    Campaign campaign = new Campaign();
    campaign.setId("campaign123");
    campaign.setStatus(Campaign.Status.REVIEWING);
    campaign.setCompanyId("company123");

    CampaignApprovedWorkflowStatus internalStatus = new CampaignApprovedWorkflowStatus();
    internalStatus.setApprovalAuthority(CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL);
    internalStatus.setStatus(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);

    when(campaignApprovedWorkflowStatusRepository.findByCampaignId("campaign123"))
        .thenReturn(List.of(internalStatus));
    when(campaignProposalStatusRepository.findStatusesByCampaignId("campaign123"))
        .thenReturn(Collections.emptyList());

    // userCompanyId matches the campaign's own company, so isMaintainer(...) alone would be true;
    // isMediaOwner=true must still take precedence over that company-ID match.
    assertThat(campaignApprovalWorkflowService.getCampaignStatus(campaign, "company123", true))
        .isNull();
  }

  @Test
  void submitCampaignForReview_WhenNotCreator_ThrowsException() {
    String campaignId = "campaign123";
    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCountryId("Japan");
    campaign.setStatus(Campaign.Status.PLANNED);
    campaign.setUserId("creator123");

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(campaign);
    when(campaignInventorySchedulesRepository.countByCampaignId(campaignId)).thenReturn(1L);
    IamUserContext userContext =
        IamUserContext.builder()
            .id("otherUser")
            .companyId("company123")
            .firstName("Other")
            .lastName("User")
            .build();
    when(userService.getIamUserContext()).thenReturn(userContext);
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(
            inv -> inv.getArgument(0) != null ? inv.getArgument(0) : userContext.getCompanyId());

    assertThatThrownBy(() -> campaignApprovalWorkflowService.submitCampaignForReview(campaignId))
        .isInstanceOf(com.mw.planner.exception.campaign.CampaignValidationException.class);

    verify(campaignService, never()).changeCampaignStatus(anyString(), any());
  }

  @Test
  void submitCampaignForReview_WhenInvalidStatus_ThrowsException() {
    String campaignId = "campaign123";
    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCountryId("Japan");
    campaign.setStatus(Campaign.Status.REVIEWING);
    campaign.setUserId("user123");

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(campaign);
    IamUserContext userContext =
        IamUserContext.builder().id("user123").companyId("company123").build();
    when(userService.getIamUserContext()).thenReturn(userContext);
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(
            inv -> inv.getArgument(0) != null ? inv.getArgument(0) : userContext.getCompanyId());

    assertThatThrownBy(() -> campaignApprovalWorkflowService.submitCampaignForReview(campaignId))
        .isInstanceOf(com.mw.planner.exception.campaign.CampaignValidationException.class);

    verify(campaignService, never()).changeCampaignStatus(anyString(), any());
  }

  @Test
  void submitCampaignForReview_WhenNoInventory_ThrowsException() {
    String campaignId = "campaign123";
    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCountryId("Japan");
    campaign.setStatus(Campaign.Status.PLANNED);
    campaign.setUserId("user123");

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(campaign);
    when(campaignInventorySchedulesRepository.countByCampaignId(campaignId)).thenReturn(0L);
    IamUserContext userContext =
        IamUserContext.builder().id("user123").companyId("company123").build();
    when(userService.getIamUserContext()).thenReturn(userContext);
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(
            inv -> inv.getArgument(0) != null ? inv.getArgument(0) : userContext.getCompanyId());

    assertThatThrownBy(() -> campaignApprovalWorkflowService.submitCampaignForReview(campaignId))
        .isInstanceOf(com.mw.planner.exception.campaign.CampaignValidationException.class);

    verify(campaignService, never()).changeCampaignStatus(anyString(), any());
  }

  @Test
  void updateApprovalStatus_WhenWorkflowStatusNotUpdatable_ThrowsWorkflowInvalidStatusException() {
    String workflowStatusId = "agencyWorkflowId";
    agencyWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.COMPLETED);
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.APPROVED)
            .comment("Approved")
            .build();

    when(campaignApprovedWorkflowStatusRepository.findById(workflowStatusId))
        .thenReturn(Optional.of(agencyWorkflowStatus));

    assertThatThrownBy(
            () -> campaignApprovalWorkflowService.updateApprovalStatus(workflowStatusId, request))
        .isInstanceOf(com.mw.planner.exception.campaign.WorkflowInvalidStatusException.class);

    verify(campaignApprovalHistoryRepository, never()).save(any(CampaignApprovalHistory.class));
  }

  @Test
  void updateApprovalStatus_WhenPricesNeedApproval_ThrowsCampaignValidationException() {
    String workflowStatusId = "internalWorkflowId";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.APPROVED)
            .comment("Approved")
            .build();

    when(campaignApprovedWorkflowStatusRepository.findById(workflowStatusId))
        .thenReturn(Optional.of(internalWorkflowStatus));
    when(campaignInventorySchedulesRepository
            .existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull("campaign123"))
        .thenReturn(true);
    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().id("user123").companyId("company123").build());
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(inv -> inv.getArgument(0) != null ? inv.getArgument(0) : "company123");

    assertThatThrownBy(
            () -> campaignApprovalWorkflowService.updateApprovalStatus(workflowStatusId, request))
        .isInstanceOf(com.mw.planner.exception.campaign.CampaignValidationException.class)
        .hasMessageContaining("one or more prices need to be approved");

    verify(campaignApprovalHistoryRepository, never()).save(any(CampaignApprovalHistory.class));
  }

  @Test
  void updateApprovalStatus_MediaOwnerRejection_RejectsProposalAndWorkflow() {
    // The acted-on Media Owner stage must be active, matching the server-side rule
    mediaOwnerWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
    String workflowStatusId = "mediaOwnerWorkflowId";
    String mediaOwnerId = "mediaOwner123";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.REJECTED)
            .comment("Rejected by media owner")
            .build();

    CampaignProposalStatus proposal = new CampaignProposalStatus();
    proposal.setId("proposal1");
    proposal.setCampaignId("campaign123");
    proposal.setMediaOwnerId(mediaOwnerId);
    proposal.setStatus(CampaignProposalStatus.Status.PENDING);

    IamUserContext iamUserContext =
        IamUserContext.builder().id("user123").companyId(mediaOwnerId).build();

    when(campaignApprovedWorkflowStatusRepository.findById(workflowStatusId))
        .thenReturn(Optional.of(mediaOwnerWorkflowStatus));
    when(campaignInventorySchedulesRepository
            .existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull("campaign123"))
        .thenReturn(false);
    when(userService.getIamUserContext()).thenReturn(iamUserContext);
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(
            inv -> inv.getArgument(0) != null ? inv.getArgument(0) : iamUserContext.getCompanyId());
    // The acting company must hold MEDIA_OWNER authority: it owns a proposal
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", mediaOwnerId))
        .thenReturn(proposal);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignId("campaign123"))
        .thenReturn(Collections.singletonList(proposal));
    when(campaignApprovedWorkflowStatusRepository.save(any(CampaignApprovedWorkflowStatus.class)))
        .thenReturn(mediaOwnerWorkflowStatus);
    when(campaignProposalStatusRepository.save(any(CampaignProposalStatus.class)))
        .thenReturn(proposal);
    when(campaignApprovalHistoryRepository.save(any(CampaignApprovalHistory.class)))
        .thenReturn(new CampaignApprovalHistory());
    doNothing().when(campaignService).changeCampaignStatus("campaign123", Campaign.Status.REJECTED);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    campaignApprovalWorkflowService.updateApprovalStatus(workflowStatusId, request);

    verify(campaignProposalStatusRepository, times(1)).save(proposal);
    verify(campaignService, times(1)).changeCampaignStatus("campaign123", Campaign.Status.REJECTED);
    assertThat(proposal.getStatus()).isEqualTo(CampaignProposalStatus.Status.REJECTED);
  }

  @Test
  void
      updateApprovalStatus_MediaOwnerApproval_WhenNotAllProposalsApproved_KeepsWorkflowInProgress() {
    String workflowStatusId = "mediaOwnerWorkflowId";
    String mediaOwnerId = "mediaOwner123";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.APPROVED)
            .comment("Approved by media owner")
            .build();

    CampaignProposalStatus myProposal = new CampaignProposalStatus();
    myProposal.setId("proposal1");
    myProposal.setCampaignId("campaign123");
    myProposal.setMediaOwnerId(mediaOwnerId);
    myProposal.setInventoryIds(Collections.singletonList("inventory123"));
    myProposal.setStatus(CampaignProposalStatus.Status.PENDING);

    CampaignProposalStatus otherProposal = new CampaignProposalStatus();
    otherProposal.setId("proposal2");
    otherProposal.setCampaignId("campaign123");
    otherProposal.setMediaOwnerId("otherMediaOwner");
    otherProposal.setStatus(CampaignProposalStatus.Status.PENDING);

    IamUserContext iamUserContext =
        IamUserContext.builder().id("user123").companyId(mediaOwnerId).build();

    AdsSubmissionResponseDTO adsResponse =
        AdsSubmissionResponseDTO.builder()
            .status(201)
            .message("Submitted")
            .total(1)
            .successful(1)
            .failed(0)
            .build();

    when(campaignApprovedWorkflowStatusRepository.findById(workflowStatusId))
        .thenReturn(Optional.of(mediaOwnerWorkflowStatus));
    when(campaignInventorySchedulesRepository
            .existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull("campaign123"))
        .thenReturn(false);
    when(userService.getIamUserContext()).thenReturn(iamUserContext);
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(
            inv -> inv.getArgument(0) != null ? inv.getArgument(0) : iamUserContext.getCompanyId());
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", mediaOwnerId))
        .thenReturn(myProposal);
    when(mwAdsService.submitApprovedCampaignToAds(
            eq("campaign123"), eq(Collections.singletonList("inventory123"))))
        .thenReturn(adsResponse);
    when(campaignProposalStatusRepository.save(any(CampaignProposalStatus.class)))
        .thenReturn(myProposal);
    when(campaignProposalStatusRepository.findStatusesByCampaignId("campaign123"))
        .thenReturn(Arrays.asList(myProposal, otherProposal));
    when(campaignApprovedWorkflowStatusRepository.save(any(CampaignApprovedWorkflowStatus.class)))
        .thenReturn(mediaOwnerWorkflowStatus);
    when(campaignApprovalHistoryRepository.save(any(CampaignApprovalHistory.class)))
        .thenReturn(new CampaignApprovalHistory());
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    campaignApprovalWorkflowService.updateApprovalStatus(workflowStatusId, request);

    verify(campaignService, never())
        .changeCampaignStatus(eq("campaign123"), eq(Campaign.Status.APPROVED));
    verify(campaignProposalStatusRepository, times(1)).save(myProposal);
    assertThat(myProposal.getStatus()).isEqualTo(CampaignProposalStatus.Status.APPROVED);
  }

  @Test
  void updateApprovalStatus_GlobalAdminApproval_ApprovesAllMediaOwnersWithSingleAdsCall() {
    String workflowStatusId = "mediaOwnerWorkflowId";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.APPROVED)
            .comment("Approved by global admin")
            .build();

    // Create multiple proposals for different media owners
    CampaignProposalStatus proposal1 = new CampaignProposalStatus();
    proposal1.setId("proposal1");
    proposal1.setCampaignId("campaign123");
    proposal1.setMediaOwnerId("mediaOwner1");
    proposal1.setInventoryIds(Arrays.asList("inv1", "inv2"));
    proposal1.setStatus(CampaignProposalStatus.Status.PENDING);

    CampaignProposalStatus proposal2 = new CampaignProposalStatus();
    proposal2.setId("proposal2");
    proposal2.setCampaignId("campaign123");
    proposal2.setMediaOwnerId("mediaOwner2");
    proposal2.setInventoryIds(Arrays.asList("inv3", "inv4", "inv5"));
    proposal2.setStatus(CampaignProposalStatus.Status.PENDING);

    CampaignProposalStatus proposal3 = new CampaignProposalStatus();
    proposal3.setId("proposal3");
    proposal3.setCampaignId("campaign123");
    proposal3.setMediaOwnerId("mediaOwner3");
    proposal3.setInventoryIds(Collections.singletonList("inv6"));
    proposal3.setStatus(CampaignProposalStatus.Status.PENDING);

    List<CampaignProposalStatus> allProposals = Arrays.asList(proposal1, proposal2, proposal3);

    IamUserContext globalAdminContext =
        IamUserContext.builder().id("admin123").isGlobalAdmin(true).build();

    AdsSubmissionResponseDTO adsResponse =
        AdsSubmissionResponseDTO.builder()
            .status(201)
            .message("Campaign submitted successfully")
            .total(6)
            .successful(6)
            .failed(0)
            .build();

    when(campaignApprovedWorkflowStatusRepository.findById(workflowStatusId))
        .thenReturn(Optional.of(mediaOwnerWorkflowStatus));
    when(campaignInventorySchedulesRepository
            .existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull("campaign123"))
        .thenReturn(false);
    when(userService.getIamUserContext()).thenReturn(globalAdminContext);
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(
            inv ->
                inv.getArgument(0) != null
                    ? inv.getArgument(0)
                    : globalAdminContext.getCompanyId());
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignId("campaign123"))
        .thenReturn(allProposals);

    // Critical: Expect ONE call with ALL 6 inventories
    when(mwAdsService.submitApprovedCampaignToAds(
            eq("campaign123"), eq(Arrays.asList("inv1", "inv2", "inv3", "inv4", "inv5", "inv6"))))
        .thenReturn(adsResponse);

    when(campaignProposalStatusRepository.save(any(CampaignProposalStatus.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignProposalStatusRepository.findStatusesByCampaignId("campaign123"))
        .thenReturn(allProposals);
    when(campaignApprovedWorkflowStatusRepository.save(any(CampaignApprovedWorkflowStatus.class)))
        .thenReturn(mediaOwnerWorkflowStatus);
    when(campaignApprovalHistoryRepository.save(any(CampaignApprovalHistory.class)))
        .thenReturn(new CampaignApprovalHistory());
    doNothing().when(campaignService).changeCampaignStatus("campaign123", Campaign.Status.APPROVED);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    campaignApprovalWorkflowService.updateApprovalStatus(workflowStatusId, request);

    // Verify only ONE call to ADS with all inventories
    verify(mwAdsService, times(1))
        .submitApprovedCampaignToAds(
            eq("campaign123"), eq(Arrays.asList("inv1", "inv2", "inv3", "inv4", "inv5", "inv6")));

    // Verify all 3 proposals are saved as APPROVED
    verify(campaignProposalStatusRepository, times(3)).save(any(CampaignProposalStatus.class));
    assertThat(proposal1.getStatus()).isEqualTo(CampaignProposalStatus.Status.APPROVED);
    assertThat(proposal2.getStatus()).isEqualTo(CampaignProposalStatus.Status.APPROVED);
    assertThat(proposal3.getStatus()).isEqualTo(CampaignProposalStatus.Status.APPROVED);

    // Verify campaign status is changed to APPROVED
    verify(campaignService, times(1)).changeCampaignStatus("campaign123", Campaign.Status.APPROVED);
  }

  // ===========================================================================
  // 2-stage / media-owner single-stage behavior
  // ===========================================================================

  @Test
  void submitCampaignForReview_TwoStage_CreatesAgencyStageAsSkipped() {
    String campaignId = "campaign123";
    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCountryId("Japan");
    campaign.setStatus(Campaign.Status.PLANNED);
    campaign.setUserId("user123");
    campaign.setCompanyId("company123");

    CampaignInventorySchedules schedule = new CampaignInventorySchedules();
    schedule.setCampaignId(campaignId);
    schedule.setMediaOwnerId("mediaOwner");
    schedule.setInventoryId("inv1");

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(campaign);
    when(campaignInventorySchedulesRepository.countByCampaignId(campaignId)).thenReturn(1L);
    when(campaignInventorySchedulesRepository.findByCampaignId(campaignId))
        .thenReturn(List.of(schedule));
    when(campaignApprovedWorkflowStatusRepository.findByCampaignId(campaignId))
        .thenReturn(Collections.emptyList());
    when(campaignProposalStatusRepository.saveAll(anyList())).thenReturn(List.of());
    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().id("user123").companyId("company123").build());
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(inv -> inv.getArgument(0) != null ? inv.getArgument(0) : "company123");

    campaignApprovalWorkflowService.submitCampaignForReview(campaignId);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CampaignApprovedWorkflowStatus>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(campaignApprovedWorkflowStatusRepository).saveAll(captor.capture());
    List<CampaignApprovedWorkflowStatus> steps = captor.getValue();

    assertThat(statusOf(steps, CampaignApprovedWorkflowStatus.ApprovalAuthority.AGENCY))
        .isEqualTo(CampaignApprovedWorkflowStatus.Status.SKIPPED);
    assertThat(statusOf(steps, CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL))
        .isEqualTo(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
    assertThat(statusOf(steps, CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER))
        .isEqualTo(CampaignApprovedWorkflowStatus.Status.PENDING);
  }

  private static CampaignApprovedWorkflowStatus.Status statusOf(
      List<CampaignApprovedWorkflowStatus> steps,
      CampaignApprovedWorkflowStatus.ApprovalAuthority authority) {
    return steps.stream()
        .filter(s -> s.getApprovalAuthority() == authority)
        .findFirst()
        .orElseThrow()
        .getStatus();
  }

  @Test
  void submitCampaignForReview_MediaOwnerCreated_SkipsAgencyAndInternal() {
    String campaignId = "campaign123";
    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCountryId("Japan");
    campaign.setStatus(Campaign.Status.PLANNED);
    campaign.setUserId("user123");
    campaign.setCompanyId("mediaOwnerCompany");

    CampaignInventorySchedules schedule = new CampaignInventorySchedules();
    schedule.setCampaignId(campaignId);
    schedule.setMediaOwnerId("mediaOwnerCompany");
    schedule.setInventoryId("inv1");

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(campaign);
    when(campaignInventorySchedulesRepository.countByCampaignId(campaignId)).thenReturn(1L);
    when(campaignInventorySchedulesRepository.findByCampaignId(campaignId))
        .thenReturn(List.of(schedule));
    when(campaignApprovedWorkflowStatusRepository.findByCampaignId(campaignId))
        .thenReturn(Collections.emptyList());
    when(campaignProposalStatusRepository.saveAll(anyList())).thenReturn(List.of());
    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().id("user123").companyId("mediaOwnerCompany").build());
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(inv -> inv.getArgument(0) != null ? inv.getArgument(0) : "mediaOwnerCompany");
    when(companyService.getCompanyLookupWithCompanyId("mediaOwnerCompany"))
        .thenReturn(CompanyLookupResponseDTO.builder().companyType("MEDIA_OWNER").build());

    campaignApprovalWorkflowService.submitCampaignForReview(campaignId);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CampaignApprovedWorkflowStatus>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(campaignApprovedWorkflowStatusRepository).saveAll(captor.capture());
    List<CampaignApprovedWorkflowStatus> steps = captor.getValue();

    assertThat(statusOf(steps, CampaignApprovedWorkflowStatus.ApprovalAuthority.AGENCY))
        .isEqualTo(CampaignApprovedWorkflowStatus.Status.SKIPPED);
    assertThat(statusOf(steps, CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL))
        .isEqualTo(CampaignApprovedWorkflowStatus.Status.SKIPPED);
    assertThat(statusOf(steps, CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER))
        .isEqualTo(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
  }

  @Test
  void submitCampaignForReview_CompanyLookupFails_DefaultsToNonMediaOwnerTwoStageFlow() {
    String campaignId = "campaign123";
    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCountryId("Japan");
    campaign.setStatus(Campaign.Status.PLANNED);
    campaign.setUserId("user123");
    campaign.setCompanyId("company123");

    CampaignInventorySchedules schedule = new CampaignInventorySchedules();
    schedule.setCampaignId(campaignId);
    schedule.setMediaOwnerId("mediaOwner");
    schedule.setInventoryId("inv1");

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(campaign);
    when(campaignInventorySchedulesRepository.countByCampaignId(campaignId)).thenReturn(1L);
    when(campaignInventorySchedulesRepository.findByCampaignId(campaignId))
        .thenReturn(List.of(schedule));
    when(campaignApprovedWorkflowStatusRepository.findByCampaignId(campaignId))
        .thenReturn(Collections.emptyList());
    when(campaignProposalStatusRepository.saveAll(anyList())).thenReturn(List.of());
    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().id("user123").companyId("company123").build());
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(inv -> inv.getArgument(0) != null ? inv.getArgument(0) : "company123");
    when(companyService.getCompanyLookupWithCompanyId("company123"))
        .thenThrow(new RuntimeException("IAM unavailable"));

    campaignApprovalWorkflowService.submitCampaignForReview(campaignId);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CampaignApprovedWorkflowStatus>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(campaignApprovedWorkflowStatusRepository).saveAll(captor.capture());
    List<CampaignApprovedWorkflowStatus> steps = captor.getValue();

    // A failed company-type lookup falls back to the standard 2-stage flow rather than breaking
    // submission or accidentally skipping Internal.
    assertThat(statusOf(steps, CampaignApprovedWorkflowStatus.ApprovalAuthority.AGENCY))
        .isEqualTo(CampaignApprovedWorkflowStatus.Status.SKIPPED);
    assertThat(statusOf(steps, CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL))
        .isEqualTo(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
    assertThat(statusOf(steps, CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER))
        .isEqualTo(CampaignApprovedWorkflowStatus.Status.PENDING);
  }

  @Test
  void updateApprovalStatus_InternalApproval_TwoStage_SkipsAgencyAndOpensMediaOwner() {
    String workflowStatusId = "internalWorkflowId";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.APPROVED)
            .comment("Approved by company")
            .build();

    when(campaignApprovedWorkflowStatusRepository.findById(workflowStatusId))
        .thenReturn(Optional.of(internalWorkflowStatus));
    when(campaignInventorySchedulesRepository
            .existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull("campaign123"))
        .thenReturn(false);
    when(campaignProposalStatusRepository.findStatusesByCampaignId("campaign123"))
        .thenReturn(Collections.emptyList());
    when(campaignApprovedWorkflowStatusRepository.findByCampaignIdAndApprovalAuthority(
            "campaign123", CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER))
        .thenReturn(Optional.of(mediaOwnerWorkflowStatus));
    when(campaignApprovedWorkflowStatusRepository.save(any(CampaignApprovedWorkflowStatus.class)))
        .thenReturn(internalWorkflowStatus);
    when(campaignApprovalHistoryRepository.save(any(CampaignApprovalHistory.class)))
        .thenReturn(new CampaignApprovalHistory());
    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().id("user123").companyId("company123").build());
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(inv -> inv.getArgument(0) != null ? inv.getArgument(0) : "company123");
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    campaignApprovalWorkflowService.updateApprovalStatus(workflowStatusId, request);

    assertThat(mediaOwnerWorkflowStatus.getStatus())
        .isEqualTo(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
    verify(campaignService, never())
        .changeCampaignStatus(eq("campaign123"), eq(Campaign.Status.APPROVED));
  }

  @Test
  void updateApprovalStatus_InternalApproval_SelfOwnedPlan_AutoApprovesAndSubmitsToAds() {
    String workflowStatusId = "internalWorkflowId";
    String companyId = "company123";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.APPROVED)
            .comment("Self-owned plan approval")
            .build();

    Campaign campaign = new Campaign();
    campaign.setId("campaign123");
    campaign.setCompanyId(companyId);

    // Single proposal owned by the planning company itself -> self-owned plan
    CampaignProposalStatus proposal = new CampaignProposalStatus();
    proposal.setId("proposal1");
    proposal.setCampaignId("campaign123");
    proposal.setMediaOwnerId(companyId);
    proposal.setInventoryIds(Collections.singletonList("inv1"));
    proposal.setStatus(CampaignProposalStatus.Status.PENDING);

    AdsSubmissionResponseDTO adsResponse =
        AdsSubmissionResponseDTO.builder().status(201).total(1).successful(1).failed(0).build();

    when(campaignApprovedWorkflowStatusRepository.findById(workflowStatusId))
        .thenReturn(Optional.of(internalWorkflowStatus));
    when(campaignInventorySchedulesRepository
            .existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull("campaign123"))
        .thenReturn(false);
    when(campaignApprovedWorkflowStatusRepository.findByCampaignIdAndApprovalAuthority(
            "campaign123", CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER))
        .thenReturn(Optional.of(mediaOwnerWorkflowStatus));
    when(campaignProposalStatusRepository.findStatusesByCampaignId("campaign123"))
        .thenReturn(Collections.singletonList(proposal));
    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(campaign);
    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().id("user123").companyId(companyId).build());
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(inv -> inv.getArgument(0) != null ? inv.getArgument(0) : companyId);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", companyId))
        .thenReturn(proposal);
    when(mwAdsService.submitApprovedCampaignToAds("campaign123", Collections.singletonList("inv1")))
        .thenReturn(adsResponse);
    when(campaignProposalStatusRepository.save(any(CampaignProposalStatus.class)))
        .thenReturn(proposal);
    when(campaignApprovedWorkflowStatusRepository.save(any(CampaignApprovedWorkflowStatus.class)))
        .thenReturn(internalWorkflowStatus);
    when(campaignApprovalHistoryRepository.save(any(CampaignApprovalHistory.class)))
        .thenReturn(new CampaignApprovalHistory());
    doNothing().when(campaignService).changeCampaignStatus("campaign123", Campaign.Status.APPROVED);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    campaignApprovalWorkflowService.updateApprovalStatus(workflowStatusId, request);

    // Media owner approval was auto-completed within the single internal approval action
    verify(mwAdsService, times(1))
        .submitApprovedCampaignToAds("campaign123", Collections.singletonList("inv1"));
    verify(campaignService, times(1)).changeCampaignStatus("campaign123", Campaign.Status.APPROVED);
    assertThat(proposal.getStatus()).isEqualTo(CampaignProposalStatus.Status.APPROVED);
  }

  @Test
  void getCampaignApprovalDetails_MediaOwnerViewer_ShowsSingleMediaOwnerStage() {
    String campaignId = "campaign123";
    String mediaOwnerId = "mediaOwner1";

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setName("Test Campaign");
    campaign.setUserId("user123");
    campaign.setStatus(Campaign.Status.REVIEWING);
    campaign.setCompanyId("company123");

    agencyWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.COMPLETED);
    internalWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.SKIPPED);
    mediaOwnerWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);

    CampaignProposalStatus proposal = new CampaignProposalStatus();
    proposal.setCampaignId(campaignId);
    proposal.setMediaOwnerId(mediaOwnerId);
    proposal.setStatus(CampaignProposalStatus.Status.PENDING);

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(campaign);
    when(companyService.getCompanyLookupWithCompanyId(anyString()))
        .thenReturn(CompanyLookupResponseDTO.builder().companyType("MEDIA_OWNER").build());
    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().id("mo-user").companyId(mediaOwnerId).build());
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(inv -> inv.getArgument(0) != null ? inv.getArgument(0) : mediaOwnerId);
    when(userService.getUserById("user123"))
        .thenReturn(UserResponseDTO.builder().id("user123").build());
    when(campaignService.resolveCampaignStatus(
            eq(campaign), any(UserResponseDTO.class), eq(mediaOwnerId)))
        .thenReturn(Campaign.Status.REVIEWING);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, mediaOwnerId))
        .thenReturn(proposal);
    when(campaignApprovedWorkflowStatusRepository.findByCampaignId(campaignId))
        .thenReturn(
            Arrays.asList(agencyWorkflowStatus, internalWorkflowStatus, mediaOwnerWorkflowStatus));
    when(campaignProposalStatusRepository.findStatusesByCampaignId(campaignId))
        .thenReturn(Collections.singletonList(proposal));
    when(campaignApprovalHistoryRepository.findByCampaignApprovedWorkflowStatusId(
            "mediaOwnerWorkflowId"))
        .thenReturn(Collections.emptyList());

    CampaignApprovalDetailsResponseDTO result =
        campaignApprovalWorkflowService.getCampaignApprovalDetails(campaignId);

    assertThat(result.getApprovalProgress()).hasSize(1);
    assertThat(result.getApprovalProgress().get(0).getApprovalAuthority())
        .isEqualTo(CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER);
    assertThat(result.getApprovalPermissions())
        .containsExactly(CampaignApprovalDetailsResponseDTO.ApprovalPermission.MEDIA_OWNER);
  }

  @Test
  void getCampaignApprovalDetails_CompanyViewer_TwoStage_HidesSkippedAgencyStage() {
    String campaignId = "campaign123";
    String userCompanyId = "company123";

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setName("Test Campaign");
    campaign.setUserId("user123");
    campaign.setStatus(Campaign.Status.REVIEWING);
    campaign.setCompanyId(userCompanyId);

    agencyWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.SKIPPED);
    internalWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);
    mediaOwnerWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.PENDING);

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(campaign);
    when(companyService.getCompanyLookupWithCompanyId(anyString()))
        .thenReturn(CompanyLookupResponseDTO.builder().companyType("ADVERTISER").build());
    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().id("user123").companyId(userCompanyId).build());
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(inv -> inv.getArgument(0) != null ? inv.getArgument(0) : userCompanyId);
    when(userService.getUserById("user123"))
        .thenReturn(UserResponseDTO.builder().id("user123").build());
    when(campaignService.resolveCampaignStatus(
            eq(campaign), any(UserResponseDTO.class), eq(userCompanyId)))
        .thenReturn(Campaign.Status.REVIEWING);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, userCompanyId))
        .thenReturn(null);
    when(campaignApprovedWorkflowStatusRepository.findByCampaignId(campaignId))
        .thenReturn(
            Arrays.asList(agencyWorkflowStatus, internalWorkflowStatus, mediaOwnerWorkflowStatus));
    when(campaignProposalStatusRepository.findStatusesByCampaignId(campaignId))
        .thenReturn(Collections.emptyList());
    when(campaignApprovalHistoryRepository.findByCampaignApprovedWorkflowStatusId(anyString()))
        .thenReturn(Collections.emptyList());

    CampaignApprovalDetailsResponseDTO result =
        campaignApprovalWorkflowService.getCampaignApprovalDetails(campaignId);

    assertThat(result.getApprovalProgress()).hasSize(2);
    assertThat(
            result.getApprovalProgress().stream()
                .map(CampaignApprovalDetailsResponseDTO.ApprovalProgressDTO::getApprovalAuthority))
        .containsExactlyInAnyOrder(
            CampaignApprovedWorkflowStatus.ApprovalAuthority.INTERNAL,
            CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER);
    assertThat(result.getApprovalPermissions())
        .doesNotContain(CampaignApprovalDetailsResponseDTO.ApprovalPermission.AGENCY);
  }

  @Test
  void
      getCampaignApprovalDetails_GlobalAdmin_MediaOwnerCreatedCampaign_NoAgencyOrInternalPermission() {
    String campaignId = "campaign123";
    String mediaOwnerCompanyId = "mediaOwnerCompany";

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setName("Test Campaign");
    campaign.setUserId("user123");
    campaign.setStatus(Campaign.Status.REVIEWING);
    campaign.setCompanyId(mediaOwnerCompanyId);

    agencyWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.SKIPPED);
    internalWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.SKIPPED);
    mediaOwnerWorkflowStatus.setStatus(CampaignApprovedWorkflowStatus.Status.IN_PROGRESS);

    CampaignProposalStatus proposal = new CampaignProposalStatus();
    proposal.setCampaignId(campaignId);
    proposal.setMediaOwnerId(mediaOwnerCompanyId);
    proposal.setStatus(CampaignProposalStatus.Status.PENDING);

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(campaign);
    when(userService.getIamUserContext())
        .thenReturn(
            IamUserContext.builder()
                .id("admin-user")
                .companyId("adminCompany")
                .isGlobalAdmin(true)
                .build());
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(inv -> inv.getArgument(0) != null ? inv.getArgument(0) : "adminCompany");
    when(companyService.getCompanyLookupWithCompanyId(anyString())).thenReturn(null);
    when(companyService.getCompanyLookupWithCompanyId(mediaOwnerCompanyId))
        .thenReturn(CompanyLookupResponseDTO.builder().companyType("MEDIA_OWNER").build());
    when(userService.getUserById("user123"))
        .thenReturn(UserResponseDTO.builder().id("user123").build());
    when(campaignService.resolveCampaignStatus(
            eq(campaign), any(UserResponseDTO.class), eq("adminCompany")))
        .thenReturn(Campaign.Status.REVIEWING);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, "adminCompany"))
        .thenReturn(null);
    when(campaignApprovedWorkflowStatusRepository.findByCampaignId(campaignId))
        .thenReturn(
            Arrays.asList(agencyWorkflowStatus, internalWorkflowStatus, mediaOwnerWorkflowStatus));
    when(campaignProposalStatusRepository.findStatusesByCampaignId(campaignId))
        .thenReturn(Collections.singletonList(proposal));
    when(campaignApprovalHistoryRepository.findByCampaignApprovedWorkflowStatusId(anyString()))
        .thenReturn(Collections.emptyList());

    CampaignApprovalDetailsResponseDTO result =
        campaignApprovalWorkflowService.getCampaignApprovalDetails(campaignId);

    assertThat(result.getApprovalPermissions())
        .containsExactly(CampaignApprovalDetailsResponseDTO.ApprovalPermission.MEDIA_OWNER);
  }

  // ---------------------------------------------------------------------------
  // Direct-API access guard + persona redaction on /approval-details
  // ---------------------------------------------------------------------------

  private Campaign buyerCampaign(String campaignId, String buyerCompanyId) {
    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setName("Buyer Campaign");
    campaign.setUserId("buyerUser");
    campaign.setBudget(90000.0);
    campaign.setStatus(Campaign.Status.REVIEWING);
    campaign.setCompanyId(buyerCompanyId);
    return campaign;
  }

  private void stubDetailsCommonMocks(
      Campaign campaign, String viewerCompanyId, String viewerCompanyType) {
    IamUserContext ctx =
        IamUserContext.builder()
            .id("viewerUser")
            .companyId(viewerCompanyId)
            .locale(Locale.ENGLISH)
            .build();
    lenient().when(userService.getIamUserContext()).thenReturn(ctx);
    lenient()
        .when(userService.resolveActingCompanyId(any()))
        .thenAnswer(inv -> inv.getArgument(0) != null ? inv.getArgument(0) : ctx.getCompanyId());
    lenient().when(campaignService.findByIdForCurrentMode(campaign.getId())).thenReturn(campaign);
    lenient()
        .when(companyService.getCompanyLookupWithCompanyId(viewerCompanyId))
        .thenReturn(CompanyLookupResponseDTO.builder().companyType(viewerCompanyType).build());
    lenient()
        .when(companyService.getCompanyLookupWithCompanyId(campaign.getCompanyId()))
        .thenReturn(CompanyLookupResponseDTO.builder().companyType("ADVERTISER").build());
    lenient()
        .when(userService.getUserById(anyString()))
        .thenReturn(UserResponseDTO.builder().id("buyerUser").build());
    lenient()
        .when(campaignService.resolveCampaignStatus(eq(campaign), any(), eq(viewerCompanyId)))
        .thenReturn(campaign.getStatus());
    lenient()
        .when(campaignApprovedWorkflowStatusRepository.findByCampaignId(campaign.getId()))
        .thenReturn(Collections.singletonList(mediaOwnerWorkflowStatus));
    lenient()
        .when(campaignProposalStatusRepository.findStatusesByCampaignId(campaign.getId()))
        .thenReturn(Collections.emptyList());
    lenient()
        .when(campaignApprovalHistoryRepository.findByCampaignApprovedWorkflowStatusId(anyString()))
        .thenReturn(Collections.emptyList());
    lenient()
        .when(campaignProposalStatusRepository.findByCampaignId(campaign.getId()))
        .thenReturn(Collections.emptyList());
    lenient()
        .when(
            campaignInventorySchedulesRepository.findByCampaignIdAndApprovedByIsNull(
                campaign.getId()))
        .thenReturn(Collections.emptyList());
    lenient()
        .when(campaignInventorySchedulesRepository.findByCampaignId(campaign.getId()))
        .thenReturn(Collections.emptyList());
  }

  private CampaignProposalStatus proposalFor(String campaignId, String mediaOwnerId) {
    CampaignProposalStatus proposal = new CampaignProposalStatus();
    proposal.setCampaignId(campaignId);
    proposal.setMediaOwnerId(mediaOwnerId);
    proposal.setStatus(CampaignProposalStatus.Status.PENDING);
    return proposal;
  }

  @Test
  void getCampaignApprovalDetails_UninvolvedMediaOwner_NotFound() {
    // A media-owner company with no proposal, no shared access and no creator relationship
    // must get a 404 — never the buyer budget — even when calling the API directly.
    String campaignId = "campaignGuard1";
    Campaign campaign = buyerCampaign(campaignId, "buyerCo");
    stubDetailsCommonMocks(campaign, "unrelatedMoCo", "MEDIA_OWNER");
    lenient()
        .when(
            campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
                campaignId, "unrelatedMoCo"))
        .thenReturn(null);

    assertThatThrownBy(() -> campaignApprovalWorkflowService.getCampaignApprovalDetails(campaignId))
        .isInstanceOf(com.mw.planner.exception.campaign.CampaignNotFoundException.class);
  }

  @Test
  void getCampaignApprovalDetails_SharedAccessMediaOwnerCompany_NoProposal_BudgetRedacted() {
    // A media-owner company granted shared access but owning no proposal may read details,
    // but buyer financials and other owners' state stay hidden.
    String campaignId = "campaignGuard2";
    Campaign campaign = buyerCampaign(campaignId, "buyerCo");
    campaign.setCompanyAccess(List.of("sharedMoCo"));
    stubDetailsCommonMocks(campaign, "sharedMoCo", "MEDIA_OWNER");
    lenient()
        .when(
            campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
                campaignId, "sharedMoCo"))
        .thenReturn(null);
    lenient()
        .when(campaignProposalStatusRepository.findByCampaignId(campaignId))
        .thenReturn(Collections.singletonList(proposalFor(campaignId, "otherMoCo")));

    CampaignApprovalDetailsResponseDTO result =
        campaignApprovalWorkflowService.getCampaignApprovalDetails(campaignId);

    assertThat(result.getBudget()).isNull();
    assertThat(result.getMediaOwners()).isNull();
    assertThat(result.getViewerProposal()).isNull();
  }

  @Test
  void getCampaignApprovalDetails_SharedAccessViewer_IamLookupFails_FailsClosed() {
    // If the viewer's company type cannot be resolved (IAM outage), a shared-access
    // viewer must NOT be classified buyer-side: budget and per-owner progress stay hidden.
    String campaignId = "campaignGuard5";
    Campaign campaign = buyerCampaign(campaignId, "buyerCo");
    campaign.setCompanyAccess(List.of("flakyCo"));
    stubDetailsCommonMocks(campaign, "flakyCo", "MEDIA_OWNER");
    lenient()
        .when(companyService.getCompanyLookupWithCompanyId("flakyCo"))
        .thenThrow(new RuntimeException("IAM unavailable"));
    lenient()
        .when(
            campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
                campaignId, "flakyCo"))
        .thenReturn(null);
    lenient()
        .when(campaignProposalStatusRepository.findByCampaignId(campaignId))
        .thenReturn(Collections.singletonList(proposalFor(campaignId, "otherMoCo")));

    CampaignApprovalDetailsResponseDTO result =
        campaignApprovalWorkflowService.getCampaignApprovalDetails(campaignId);

    assertThat(result.getBudget()).isNull();
    assertThat(result.getMediaOwners()).isNull();
    assertThat(result.getViewerProposal()).isNull();
  }

  @Test
  void getCampaignApprovalDetails_MediaOwnerViewer_GetsOwnSliceOnly() {
    String campaignId = "campaignGuard3";
    Campaign campaign = buyerCampaign(campaignId, "buyerCo");
    stubDetailsCommonMocks(campaign, "moCo", "MEDIA_OWNER");
    CampaignProposalStatus ownProposal = proposalFor(campaignId, "moCo");
    lenient()
        .when(
            campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
                campaignId, "moCo"))
        .thenReturn(ownProposal);
    lenient()
        .when(campaignProposalStatusRepository.findByCampaignId(campaignId))
        .thenReturn(Arrays.asList(ownProposal, proposalFor(campaignId, "otherMoCo")));

    CampaignApprovalDetailsResponseDTO result =
        campaignApprovalWorkflowService.getCampaignApprovalDetails(campaignId);

    // Budget (buyer fees) redacted; only the viewer's own slice exposed
    assertThat(result.getBudget()).isNull();
    assertThat(result.getMediaOwners()).isNull();
    assertThat(result.getViewerProposal()).isNotNull();
    assertThat(result.getViewerProposal().getMediaOwnerId()).isEqualTo("moCo");
    // Media-owner viewers only see the Media Owner stage
    assertThat(result.getApprovalProgress())
        .allMatch(
            p ->
                p.getApprovalAuthority()
                    == CampaignApprovedWorkflowStatus.ApprovalAuthority.MEDIA_OWNER);
  }

  @Test
  void getCampaignApprovalDetails_MediaOwnerCreator_BudgetStillRedacted() {
    // Media-owner-created campaigns exist; a non-admin media-owner company must not see
    // budget (fee-bearing) even on its own campaign — only global admins are exempt.
    String campaignId = "campaignGuard6";
    Campaign campaign = buyerCampaign(campaignId, "moCreatorCo");
    stubDetailsCommonMocks(campaign, "moCreatorCo", "MEDIA_OWNER");
    lenient()
        .when(companyService.getCompanyLookupWithCompanyId("moCreatorCo"))
        .thenReturn(CompanyLookupResponseDTO.builder().companyType("MEDIA_OWNER").build());
    lenient()
        .when(
            campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
                campaignId, "moCreatorCo"))
        .thenReturn(null);

    CampaignApprovalDetailsResponseDTO result =
        campaignApprovalWorkflowService.getCampaignApprovalDetails(campaignId);

    assertThat(result.getBudget()).isNull();
    assertThat(result.getViewerProposal()).isNull();
    // Creator status never overrides the media-owner persona: no other owners' state
    assertThat(result.getMediaOwners()).isNull();
  }

  @Test
  void getCampaignApprovalDetails_MediaOwnerCreatorWithProposal_GetsOnlyOwnSlice() {
    // A media-owner creator that also owns a proposal gets only its own slice —
    // never the full per-owner progress list.
    String campaignId = "campaignGuard7";
    Campaign campaign = buyerCampaign(campaignId, "moCreatorCo");
    stubDetailsCommonMocks(campaign, "moCreatorCo", "MEDIA_OWNER");
    CampaignProposalStatus ownProposal = proposalFor(campaignId, "moCreatorCo");
    lenient()
        .when(
            campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
                campaignId, "moCreatorCo"))
        .thenReturn(ownProposal);
    lenient()
        .when(campaignProposalStatusRepository.findByCampaignId(campaignId))
        .thenReturn(Arrays.asList(ownProposal, proposalFor(campaignId, "otherMoCo")));

    CampaignApprovalDetailsResponseDTO result =
        campaignApprovalWorkflowService.getCampaignApprovalDetails(campaignId);

    assertThat(result.getBudget()).isNull();
    assertThat(result.getMediaOwners()).isNull();
    assertThat(result.getViewerProposal()).isNotNull();
    assertThat(result.getViewerProposal().getMediaOwnerId()).isEqualTo("moCreatorCo");
  }

  @Test
  void getCampaignApprovalDetails_BuyerCreator_GetsPerOwnerProgress() {
    String campaignId = "campaignGuard4";
    Campaign campaign = buyerCampaign(campaignId, "buyerCo");
    stubDetailsCommonMocks(campaign, "buyerCo", "ADVERTISER");
    lenient()
        .when(
            campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
                campaignId, "buyerCo"))
        .thenReturn(null);
    lenient()
        .when(campaignProposalStatusRepository.findByCampaignId(campaignId))
        .thenReturn(Arrays.asList(proposalFor(campaignId, "moA"), proposalFor(campaignId, "moB")));
    lenient()
        .when(companyService.getCompanyLookupWithCompanyId("moA"))
        .thenReturn(
            CompanyLookupResponseDTO.builder().companyType("MEDIA_OWNER").name("MO A").build());
    lenient()
        .when(companyService.getCompanyLookupWithCompanyId("moB"))
        .thenReturn(
            CompanyLookupResponseDTO.builder().companyType("MEDIA_OWNER").name("MO B").build());

    CampaignApprovalDetailsResponseDTO result =
        campaignApprovalWorkflowService.getCampaignApprovalDetails(campaignId);

    assertThat(result.getBudget()).isEqualTo(90000.0);
    assertThat(result.getViewerProposal()).isNull();
    assertThat(result.getMediaOwners()).isNotNull();
    assertThat(result.getMediaOwners())
        .extracting(m -> m.getMediaOwnerId())
        .containsExactly("moA", "moB");
  }
}
