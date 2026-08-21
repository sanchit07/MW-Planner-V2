package com.mw.planner.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignApprovalHistory;
import com.mw.planner.dto.ApprovalInboxItemDTO;
import com.mw.planner.dto.CampaignApprovalDetailsResponseDTO;
import com.mw.planner.dto.CampaignApprovalStatusUpdateRequestDTO;
import com.mw.planner.exception.GlobalExceptionHandler;
import com.mw.planner.exception.campaign.CampaignNotFoundException;
import com.mw.planner.service.CampaignApprovalWorkflowService;
import com.mw.planner.service.MessageService;
import com.mw.planner.service.MetricsService;
import com.mw.planner.service.UserService;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CampaignApprovalWorkflowControllerTest {

  @Mock private CampaignApprovalWorkflowService campaignApprovalWorkflowService;
  @Mock private MessageService messageService;
  @Mock private MetricsService metricsService;
  @Mock private UserService userService;

  @InjectMocks private CampaignApprovalWorkflowController campaignApprovalWorkflowController;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(campaignApprovalWorkflowController)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageService, metricsService, userService))
            .build();
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();
  }

  @AfterEach
  void tearDown() {
    reset(campaignApprovalWorkflowService, messageService, metricsService, userService);
  }

  @Test
  void submitCampaignForReview_Success() throws Exception {
    String campaignId = "campaign123";
    doNothing().when(campaignApprovalWorkflowService).submitCampaignForReview(campaignId);

    mockMvc
        .perform(
            post("/api/v1/campaign-approval-workflow/{campaignId}/submit-for-review", campaignId)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value("Campaign submitted successfully for review"));

    verify(campaignApprovalWorkflowService, times(1)).submitCampaignForReview(campaignId);
  }

  @Test
  void submitCampaignForReview_CampaignNotFound() throws Exception {
    String campaignId = "nonExistentCampaign";
    doThrow(new CampaignNotFoundException(campaignId))
        .when(campaignApprovalWorkflowService)
        .submitCampaignForReview(campaignId);

    mockMvc
        .perform(
            post("/api/v1/campaign-approval-workflow/{campaignId}/submit-for-review", campaignId)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());

    verify(campaignApprovalWorkflowService, times(1)).submitCampaignForReview(campaignId);
  }

  @Test
  void updateApprovalStatus_Approve_Success() throws Exception {
    String workflowStatusId = "workflowStatus123";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.APPROVED)
            .comment("Campaign approved")
            .build();

    doNothing()
        .when(campaignApprovalWorkflowService)
        .updateApprovalStatus(workflowStatusId, request);

    mockMvc
        .perform(
            put(
                    "/api/v1/campaign-approval-workflow/approval-status/{workflowStatusId}",
                    workflowStatusId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isString());

    verify(campaignApprovalWorkflowService, times(1))
        .updateApprovalStatus(workflowStatusId, request);
  }

  @Test
  void updateApprovalStatus_Reject_Success() throws Exception {
    String workflowStatusId = "workflowStatus123";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.REJECTED)
            .comment("Campaign rejected")
            .build();

    doNothing()
        .when(campaignApprovalWorkflowService)
        .updateApprovalStatus(workflowStatusId, request);

    mockMvc
        .perform(
            put(
                    "/api/v1/campaign-approval-workflow/approval-status/{workflowStatusId}",
                    workflowStatusId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(campaignApprovalWorkflowService, times(1))
        .updateApprovalStatus(workflowStatusId, request);
  }

  @Test
  void updateApprovalStatus_ChangeRequest_Success() throws Exception {
    String workflowStatusId = "workflowStatus123";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.IN_NEGOTIATION)
            .comment("Please make changes")
            .build();

    doNothing()
        .when(campaignApprovalWorkflowService)
        .updateApprovalStatus(workflowStatusId, request);

    mockMvc
        .perform(
            put(
                    "/api/v1/campaign-approval-workflow/approval-status/{workflowStatusId}",
                    workflowStatusId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(campaignApprovalWorkflowService, times(1))
        .updateApprovalStatus(workflowStatusId, request);
  }

  @Test
  void updateApprovalStatus_ValidationError_MissingStatus() throws Exception {
    String workflowStatusId = "workflowStatus123";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder().comment("Comment without status").build();

    mockMvc
        .perform(
            put(
                    "/api/v1/campaign-approval-workflow/approval-status/{workflowStatusId}",
                    workflowStatusId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());

    verify(campaignApprovalWorkflowService, never())
        .updateApprovalStatus(anyString(), any(CampaignApprovalStatusUpdateRequestDTO.class));
  }

  @Test
  void updateApprovalStatus_WorkflowStatusNotFound() throws Exception {
    String workflowStatusId = "nonExistentWorkflowStatus";
    CampaignApprovalStatusUpdateRequestDTO request =
        CampaignApprovalStatusUpdateRequestDTO.builder()
            .status(CampaignApprovalHistory.Status.APPROVED)
            .comment("Test comment")
            .build();

    doThrow(new RuntimeException("Workflow status not found for ID: " + workflowStatusId))
        .when(campaignApprovalWorkflowService)
        .updateApprovalStatus(workflowStatusId, request);

    mockMvc
        .perform(
            put(
                    "/api/v1/campaign-approval-workflow/approval-status/{workflowStatusId}",
                    workflowStatusId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isInternalServerError());

    verify(campaignApprovalWorkflowService, times(1))
        .updateApprovalStatus(workflowStatusId, request);
  }

  @Test
  void updateApprovalStatus_InvalidJson() throws Exception {
    String workflowStatusId = "workflowStatus123";

    mockMvc
        .perform(
            put(
                    "/api/v1/campaign-approval-workflow/approval-status/{workflowStatusId}",
                    workflowStatusId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("invalid json"))
        .andExpect(status().isBadRequest());

    verify(campaignApprovalWorkflowService, never())
        .updateApprovalStatus(anyString(), any(CampaignApprovalStatusUpdateRequestDTO.class));
  }

  @Test
  void getCampaignApprovalDetails_Success() throws Exception {
    String campaignId = "campaign123";
    CampaignApprovalDetailsResponseDTO responseDTO =
        CampaignApprovalDetailsResponseDTO.builder()
            .campaignId(campaignId)
            .campaignName("Test Campaign")
            .status(Campaign.Status.REVIEWING)
            .budget(50000.0)
            .approvalProgress(Collections.emptyList())
            .build();

    when(campaignApprovalWorkflowService.getCampaignApprovalDetails(campaignId))
        .thenReturn(responseDTO);

    mockMvc
        .perform(
            get("/api/v1/campaign-approval-workflow/{campaignId}/approval-details", campaignId)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.campaignId").value(campaignId))
        .andExpect(jsonPath("$.data.campaignName").value("Test Campaign"))
        .andExpect(jsonPath("$.data.status").value("REVIEWING"))
        .andExpect(jsonPath("$.data.budget").value(50000.0));

    verify(campaignApprovalWorkflowService, times(1)).getCampaignApprovalDetails(campaignId);
  }

  @Test
  void getCampaignApprovalDetails_CampaignNotFound() throws Exception {
    String campaignId = "nonExistentCampaign";
    when(campaignApprovalWorkflowService.getCampaignApprovalDetails(campaignId))
        .thenThrow(new CampaignNotFoundException(campaignId));

    mockMvc
        .perform(
            get("/api/v1/campaign-approval-workflow/{campaignId}/approval-details", campaignId)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());

    verify(campaignApprovalWorkflowService, times(1)).getCampaignApprovalDetails(campaignId);
  }

  // ---------------------------------------------------------------------------------
  // X-Tenant-Id header binding: the persona boundary hinges on the header reaching the
  // service verbatim (the service validates membership and redacts accordingly).
  // ---------------------------------------------------------------------------------

  @Test
  void getApprovalInbox_ForwardsTenantHeader_MediaOwnerViewGetsNoBudgetOrOwnerList()
      throws Exception {
    ApprovalInboxItemDTO redacted =
        ApprovalInboxItemDTO.builder()
            .campaignId("campaign123")
            .budget(null)
            .viewerIsMediaOwner(true)
            .hasUnacceptedPrices(false)
            .mediaOwners(null)
            .build();
    when(campaignApprovalWorkflowService.getApprovalInbox())
        .thenReturn(Collections.singletonList(redacted));

    mockMvc
        .perform(
            get("/api/v1/campaign-approval-workflow/inbox")
                .header("X-Tenant-Id", "mo-lumina")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].budget").doesNotExist())
        .andExpect(jsonPath("$.data[0].mediaOwners").doesNotExist())
        .andExpect(jsonPath("$.data[0].viewerIsMediaOwner").value(true));

    // Header must reach the service verbatim — never null, never rewritten.
    verify(campaignApprovalWorkflowService, times(1)).getApprovalInbox();
  }

  @Test
  void getApprovalInbox_NoTenantHeader_PassesNull() throws Exception {
    when(campaignApprovalWorkflowService.getApprovalInbox()).thenReturn(Collections.emptyList());

    mockMvc
        .perform(
            get("/api/v1/campaign-approval-workflow/inbox").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    verify(campaignApprovalWorkflowService, times(1)).getApprovalInbox();
  }

  @Test
  void getCampaignApprovalDetails_ForwardsTenantHeader_RedactedBudgetStaysAbsent()
      throws Exception {
    String campaignId = "campaign123";
    CampaignApprovalDetailsResponseDTO redacted =
        CampaignApprovalDetailsResponseDTO.builder().campaignId(campaignId).budget(null).build();
    when(campaignApprovalWorkflowService.getCampaignApprovalDetails(campaignId))
        .thenReturn(redacted);

    mockMvc
        .perform(
            get("/api/v1/campaign-approval-workflow/{campaignId}/approval-details", campaignId)
                .header("X-Tenant-Id", "mo-lumina")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.budget").doesNotExist());

    verify(campaignApprovalWorkflowService, times(1)).getCampaignApprovalDetails(campaignId);
  }
}
