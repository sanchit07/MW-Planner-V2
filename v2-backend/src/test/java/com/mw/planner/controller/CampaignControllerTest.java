package com.mw.planner.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.domain.Campaign;
import com.mw.planner.dto.*;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.enums.CostSplit;
import com.mw.planner.exception.GlobalExceptionHandler;
import com.mw.planner.service.*;
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
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CampaignControllerTest {

  @Mock private CampaignService campaignService;
  @Mock private UserService userService;
  @Mock private MessageService messageService;
  @Mock private MetricsService metricsService;
  @Mock private CampaignActivityService campaignActivityService;

  @InjectMocks private CampaignController campaignController;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;
  private Campaign testCampaign;
  private CampaignRequestDTO testCampaignRequestDTO;
  private CampaignResponseDTO testCampaignResponseDTO;
  private IamUserContext testUserContext;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(campaignController)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageService, metricsService, userService))
            .build();
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();

    testCampaign =
        Campaign.builder()
            .name("Test Campaign")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .currentCompanyId("company456")
            .currentCompanyName("Test Company Ltd")
            .build();
    testCampaign.setId("campaign123");
    testCampaign.setCreatedAt(LocalDateTime.now());
    testCampaign.setUpdatedAt(LocalDateTime.now());

    testCampaignRequestDTO =
        CampaignRequestDTO.builder()
            .name("Test Campaign")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .currentCompanyId("company456")
            .currentCompanyName("Test Company Ltd")
            .build();

    testCampaignResponseDTO = CampaignResponseDTO.mapToDto(testCampaign);

    testUserContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .locale(Locale.ENGLISH)
            .build();

    // Mock the iamUserContextService.getIamUserContext() call that GlobalExceptionHandler makes
    when(userService.getIamUserContext()).thenReturn(testUserContext);
  }

  @AfterEach
  void tearDown() {
    // Reset mocks to clear any interactions for next test
    reset(campaignService, userService, messageService, metricsService, campaignActivityService);
  }

  // ========== createCampaign Tests ==========

  @Test
  void createCampaign_WithValidData_ShouldReturnCreatedCampaign() throws Exception {
    // Given
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(campaignService.createCampaign(any(CampaignRequestDTO.class)))
        .thenReturn(testCampaignResponseDTO);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaigns")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCampaignRequestDTO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("campaign123"))
        .andExpect(jsonPath("$.data.name").value("Test Campaign"))
        .andExpect(jsonPath("$.data.status").value("DRAFT"))
        .andExpect(jsonPath("$.data.budget").value(10000.0));

    ArgumentCaptor<CampaignRequestDTO> captor = ArgumentCaptor.forClass(CampaignRequestDTO.class);
    verify(userService).getIamUserContext();
    verify(campaignService).createCampaign(captor.capture());
    assertThat(captor.getValue().getCompanyId()).isEqualTo("company123");
  }

  @Test
  void createCampaign_WhenCompanyIdAlreadySet_ShouldNotOverrideCompanyId() throws Exception {
    // Given — DTO has a companyId pre-populated (e.g. set programmatically before the call)
    testCampaignRequestDTO.setCompanyId("pre-existing-company");
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(campaignService.createCampaign(any(CampaignRequestDTO.class)))
        .thenReturn(testCampaignResponseDTO);

    mockMvc
        .perform(
            post("/api/v1/campaigns")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCampaignRequestDTO)))
        .andExpect(status().isOk());

    ArgumentCaptor<CampaignRequestDTO> captor = ArgumentCaptor.forClass(CampaignRequestDTO.class);
    verify(campaignService).createCampaign(captor.capture());
    assertThat(captor.getValue().getCompanyId()).isEqualTo("pre-existing-company");
  }

  @Test
  void createCampaign_WithInvalidData_ShouldReturnBadRequest() throws Exception {
    // Given
    CampaignRequestDTO invalidRequest =
        CampaignRequestDTO.builder()
            .name("") // Invalid: empty name
            .build();

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaigns")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());

    // getUserContext() is called by GlobalExceptionHandler, so we can't use never()
    verify(campaignService, never()).createCampaign(any(CampaignRequestDTO.class));
  }

  @Test
  void createCampaign_WithMissingRequiredFields_ShouldReturnBadRequest() throws Exception {
    // Given
    CampaignRequestDTO incompleteRequest =
        CampaignRequestDTO.builder()
            .name("Test Campaign")
            // Missing required fields: startDate, endDate, clientType
            .build();

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaigns")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(incompleteRequest)))
        .andExpect(status().isBadRequest());

    verify(campaignService, never()).createCampaign(any(CampaignRequestDTO.class));
  }

  // ========== getCampaignById Tests ==========

  @Test
  void getCampaignById_WithValidId_ShouldReturnCampaign() throws Exception {
    // Given
    CampaignResponseDTO responseWithNames =
        CampaignResponseDTO.builder()
            .id("campaign123")
            .name("Test Campaign")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").name("Test Brand").build())
            .agency(Campaign.CampaignAgency.builder().id("agency123").name("Test Agency").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .inventoryCount(5L)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    when(campaignService.getCampaignById("campaign123")).thenReturn(responseWithNames);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaigns/campaign123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("campaign123"))
        .andExpect(jsonPath("$.data.name").value("Test Campaign"))
        .andExpect(jsonPath("$.data.brand.name").value("Test Brand"))
        .andExpect(jsonPath("$.data.agency.name").value("Test Agency"))
        .andExpect(jsonPath("$.data.inventoryCount").value(5));

    verify(campaignService).getCampaignById("campaign123");
  }

  @Test
  void getCampaignById_WithNullBrandAndAgency_ShouldReturnCampaign() throws Exception {
    // Given
    CampaignResponseDTO responseWithoutNames =
        CampaignResponseDTO.builder()
            .id("campaign123")
            .name("Test Campaign")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .inventoryCount(0L)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    when(campaignService.getCampaignById("campaign123")).thenReturn(responseWithoutNames);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaigns/campaign123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("campaign123"))
        .andExpect(jsonPath("$.data.name").value("Test Campaign"))
        .andExpect(jsonPath("$.data.brand.name").doesNotExist())
        .andExpect(jsonPath("$.data.agency").doesNotExist())
        .andExpect(jsonPath("$.data.inventoryCount").value(0));

    verify(campaignService).getCampaignById("campaign123");
  }

  @Test
  void getCampaignById_WithInvalidId_ShouldReturnNotFound() throws Exception {
    // Given
    when(campaignService.getCampaignById("invalid123"))
        .thenThrow(new com.mw.planner.exception.campaign.CampaignNotFoundException("invalid123"));

    // When & Then
    mockMvc.perform(get("/api/v1/campaigns/invalid123")).andExpect(status().isNotFound());

    verify(campaignService).getCampaignById("invalid123");
  }

  // ========== getCampaignByName Tests ==========

  @Test
  void getCampaignByName_WithValidName_ShouldReturnCampaign() throws Exception {
    // Given
    when(campaignService.getCampaignByName("Test Campaign")).thenReturn(testCampaignResponseDTO);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaigns/name/Test Campaign"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.name").value("Test Campaign"));

    verify(campaignService).getCampaignByName("Test Campaign");
  }

  @Test
  void getCampaignByName_WithInvalidName_ShouldReturnNotFound() throws Exception {
    // Given
    when(campaignService.getCampaignByName("Invalid Campaign"))
        .thenThrow(
            new com.mw.planner.exception.campaign.CampaignNotFoundException("Invalid Campaign"));

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaigns/name/Invalid Campaign"))
        .andExpect(status().isNotFound());

    verify(campaignService).getCampaignByName("Invalid Campaign");
  }

  // ========== updateCampaign Tests ==========

  @Test
  void updateCampaign_WithValidData_ShouldReturnUpdatedCampaign() throws Exception {
    // Given
    CampaignRequestDTO updateRequest =
        CampaignRequestDTO.builder()
            .name("Updated Campaign")
            .description("Updated Description")
            .status(Campaign.Status.APPROVED)
            .budget(15000.0)
            .currency("EUR")
            .startDate(LocalDate.now().plusDays(2))
            .endDate(LocalDate.now().plusDays(35))
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .build();

    CampaignResponseDTO updatedResponse =
        CampaignResponseDTO.builder()
            .id("campaign123")
            .name("Updated Campaign")
            .description("Updated Description")
            .status(Campaign.Status.APPROVED)
            .budget(15000.0)
            .currency("EUR")
            .startDate(LocalDate.now().plusDays(2))
            .endDate(LocalDate.now().plusDays(35))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").name("Updated Brand").build())
            .agency(
                Campaign.CampaignAgency.builder().id("agency123").name("Updated Agency").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    when(campaignService.updateCampaign(eq("campaign123"), any(CampaignRequestDTO.class)))
        .thenReturn(updatedResponse);

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/campaigns/campaign123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.name").value("Updated Campaign"))
        .andExpect(jsonPath("$.data.status").value("APPROVED"))
        .andExpect(jsonPath("$.data.budget").value(15000.0))
        .andExpect(jsonPath("$.data.brand.name").value("Updated Brand"))
        .andExpect(jsonPath("$.data.agency.name").value("Updated Agency"));

    verify(campaignService).updateCampaign(eq("campaign123"), any(CampaignRequestDTO.class));
  }

  @Test
  void updateCampaign_WithInvalidId_ShouldReturnNotFound() throws Exception {
    // Given
    when(campaignService.updateCampaign(eq("invalid123"), any(CampaignRequestDTO.class)))
        .thenThrow(new com.mw.planner.exception.campaign.CampaignNotFoundException("invalid123"));

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/campaigns/invalid123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCampaignRequestDTO)))
        .andExpect(status().isNotFound());

    verify(campaignService).updateCampaign(eq("invalid123"), any(CampaignRequestDTO.class));
  }

  @Test
  void updateCampaign_WithInvalidData_ShouldReturnBadRequest() throws Exception {
    // Given
    CampaignRequestDTO invalidRequest =
        CampaignRequestDTO.builder()
            .name("") // Invalid: empty name
            .budget(-100.0) // Invalid: negative budget
            .build();

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/campaigns/campaign123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());

    verify(campaignService, never()).updateCampaign(anyString(), any(CampaignRequestDTO.class));
  }

  @Test
  void updateCampaign_WithDuplicateName_ShouldReturnConflict() throws Exception {
    // Given
    CampaignRequestDTO updateRequest =
        CampaignRequestDTO.builder()
            .name("Existing Campaign")
            .description("Updated Description")
            .status(Campaign.Status.APPROVED)
            .budget(15000.0)
            .currency("EUR")
            .startDate(LocalDate.now().plusDays(2))
            .endDate(LocalDate.now().plusDays(35))
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .build();

    when(campaignService.updateCampaign(eq("campaign123"), any(CampaignRequestDTO.class)))
        .thenThrow(
            new com.mw.planner.exception.campaign.CampaignAlreadyExistsException(
                "Existing Campaign"));

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/campaigns/campaign123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isConflict());

    verify(campaignService).updateCampaign(eq("campaign123"), any(CampaignRequestDTO.class));
  }

  // ========== deleteCampaign Tests ==========

  @Test
  void deleteCampaign_WithValidId_ShouldReturnSuccess() throws Exception {
    // Given
    doNothing().when(campaignService).deleteCampaign("campaign123");

    // When & Then
    mockMvc
        .perform(delete("/api/v1/campaigns/campaign123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").doesNotExist());

    verify(campaignService).deleteCampaign("campaign123");
  }

  @Test
  void deleteCampaign_WithInvalidId_ShouldReturnNotFound() throws Exception {
    // Given
    doThrow(new com.mw.planner.exception.campaign.CampaignNotFoundException("invalid123"))
        .when(campaignService)
        .deleteCampaign("invalid123");

    // When & Then
    mockMvc.perform(delete("/api/v1/campaigns/invalid123")).andExpect(status().isNotFound());

    verify(campaignService).deleteCampaign("invalid123");
  }

  // ========== getCampaignsWithFilters Tests ==========

  @Test
  void getCampaignsWithFilters_WithDefaultParameters_ShouldReturnPagedCampaigns() throws Exception {
    // Given
    CampaignFilterResponseDTO filterResponseDTO =
        CampaignFilterResponseDTO.builder()
            .id("campaign123")
            .name("Test Campaign")
            .status(String.valueOf(Campaign.Status.DRAFT))
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .brandName("Test Brand")
            .categoryName("Sports")
            .userName("Test User")
            .currentCompanyId("company456")
            .currentCompanyName("Test Company Ltd")
            .inventory(5)
            .estimatedImpression(100000L)
            .estimatedReach(50000L)
            .sov(15.5)
            .totalSot(82.0)
            .build();

    Page<CampaignFilterResponseDTO> campaignPage =
        new PageImpl<>(List.of(filterResponseDTO), PageRequest.of(0, 10), 1);
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(campaignService.getCampaignsWithFilters(any(CampaignFilterDTO.class), any(Pageable.class)))
        .thenReturn(campaignPage);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaigns"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].id").value("campaign123"))
        .andExpect(jsonPath("$.data.content[0].name").value("Test Campaign"))
        .andExpect(jsonPath("$.data.content[0].currentCompanyId").value("company456"))
        .andExpect(jsonPath("$.data.content[0].currentCompanyName").value("Test Company Ltd"))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.totalPages").value(1));

    verify(userService).getIamUserContext();
    verify(campaignService)
        .getCampaignsWithFilters(any(CampaignFilterDTO.class), any(Pageable.class));
  }

  @Test
  void getCampaignsWithFilters_WithCustomParameters_ShouldReturnFilteredCampaigns()
      throws Exception {
    // Given
    CampaignFilterResponseDTO filterResponseDTO =
        CampaignFilterResponseDTO.builder()
            .id("campaign123")
            .name("Test Campaign")
            .status(String.valueOf(Campaign.Status.DRAFT))
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .brandName("Test Brand")
            .categoryName("Sports")
            .userName("Test User")
            .currentCompanyId("company456")
            .currentCompanyName("Test Company Ltd")
            .inventory(5)
            .estimatedImpression(100000L)
            .estimatedReach(50000L)
            .sov(15.5)
            .totalSot(82.0)
            .build();

    Page<CampaignFilterResponseDTO> campaignPage =
        new PageImpl<>(List.of(filterResponseDTO), PageRequest.of(0, 5), 1);
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(campaignService.getCampaignsWithFilters(any(CampaignFilterDTO.class), any(Pageable.class)))
        .thenReturn(campaignPage);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaigns")
                .param("nameContains", "Summer")
                .param("statuses", "DRAFT,APPROVED")
                .param("goalTypes", "IMPRESSIONS,REACH")
                .param("userIds", "user1,user2")
                .param("startDateFrom", "2024-01-01")
                .param("startDateTo", "2024-12-31")
                .param("page", "0")
                .param("size", "5")
                .param("sortBy", "name")
                .param("sortDir", "asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].currentCompanyId").value("company456"))
        .andExpect(jsonPath("$.data.content[0].currentCompanyName").value("Test Company Ltd"))
        .andExpect(jsonPath("$.data.size").value(5));

    verify(userService).getIamUserContext();
    verify(campaignService)
        .getCampaignsWithFilters(any(CampaignFilterDTO.class), any(Pageable.class));
  }

  @Test
  void getCampaignsWithFilters_WithInvalidDateParameters_ShouldReturnBadRequest() throws Exception {
    // Given
    when(userService.getIamUserContext()).thenReturn(testUserContext);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaigns")
                .param("startDateFrom", "invalid-date")
                .param("startDateTo", "invalid-date"))
        .andExpect(status().isBadRequest());

    // getUserContext() is called by both controller and GlobalExceptionHandler
    verify(campaignService, never())
        .getCampaignsWithFilters(any(CampaignFilterDTO.class), any(Pageable.class));
  }

  // ========== performBulkAction Tests ==========

  @Test
  void performBulkAction_WithDuplicateAction_ShouldReturnBulkResponse() throws Exception {
    // Given
    CampaignBulkActionRequestDTO request =
        CampaignBulkActionRequestDTO.builder()
            .campaignIds(List.of("campaign123"))
            .action(CampaignAction.DUPLICATE)
            .build();

    CampaignBulkActionResponseDTO response =
        CampaignBulkActionResponseDTO.builder()
            .totalProcessed(1)
            .successCount(1)
            .failureCount(0)
            .successfulCampaignIds(List.of("campaign123"))
            .failedCampaignIds(List.of())
            .errorMessages(List.of())
            .newCampaignIds(List.of("newCampaign123"))
            .build();

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(campaignService.performBulkAction(
            any(CampaignBulkActionRequestDTO.class), any(IamUserContext.class)))
        .thenReturn(response);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaigns/bulk-actions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalProcessed").value(1))
        .andExpect(jsonPath("$.data.successCount").value(1))
        .andExpect(jsonPath("$.data.failureCount").value(0))
        .andExpect(jsonPath("$.data.successfulCampaignIds[0]").value("campaign123"))
        .andExpect(jsonPath("$.data.newCampaignIds[0]").value("newCampaign123"));

    verify(userService).getIamUserContext();
    verify(campaignService)
        .performBulkAction(any(CampaignBulkActionRequestDTO.class), any(IamUserContext.class));
  }

  @Test
  void performBulkAction_WithArchiveAction_ShouldReturnBulkResponse() throws Exception {
    // Given
    CampaignBulkActionRequestDTO request =
        CampaignBulkActionRequestDTO.builder()
            .campaignIds(List.of("campaign123"))
            .action(CampaignAction.ARCHIVE)
            .build();

    CampaignBulkActionResponseDTO response =
        CampaignBulkActionResponseDTO.builder()
            .totalProcessed(1)
            .successCount(1)
            .failureCount(0)
            .successfulCampaignIds(List.of("campaign123"))
            .failedCampaignIds(List.of())
            .errorMessages(List.of())
            .newCampaignIds(List.of())
            .build();

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(campaignService.performBulkAction(
            any(CampaignBulkActionRequestDTO.class), any(IamUserContext.class)))
        .thenReturn(response);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaigns/bulk-actions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalProcessed").value(1))
        .andExpect(jsonPath("$.data.successCount").value(1));

    verify(userService).getIamUserContext();
    verify(campaignService)
        .performBulkAction(any(CampaignBulkActionRequestDTO.class), any(IamUserContext.class));
  }

  @Test
  void performBulkAction_WithDeleteAction_ShouldReturnBulkResponse() throws Exception {
    // Given
    CampaignBulkActionRequestDTO request =
        CampaignBulkActionRequestDTO.builder()
            .campaignIds(List.of("campaign123"))
            .action(CampaignAction.DELETE)
            .build();

    CampaignBulkActionResponseDTO response =
        CampaignBulkActionResponseDTO.builder()
            .totalProcessed(1)
            .successCount(1)
            .failureCount(0)
            .successfulCampaignIds(List.of("campaign123"))
            .failedCampaignIds(List.of())
            .errorMessages(List.of())
            .newCampaignIds(List.of())
            .build();

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(campaignService.performBulkAction(
            any(CampaignBulkActionRequestDTO.class), any(IamUserContext.class)))
        .thenReturn(response);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaigns/bulk-actions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalProcessed").value(1))
        .andExpect(jsonPath("$.data.successCount").value(1));

    verify(userService).getIamUserContext();
    verify(campaignService)
        .performBulkAction(any(CampaignBulkActionRequestDTO.class), any(IamUserContext.class));
  }

  @Test
  void performBulkAction_WithInvalidRequest_ShouldReturnBadRequest() throws Exception {
    // Given
    CampaignBulkActionRequestDTO invalidRequest =
        CampaignBulkActionRequestDTO.builder()
            .campaignIds(List.of()) // Invalid: empty list
            .action(CampaignAction.DUPLICATE)
            .build();

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaigns/bulk-actions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());

    // getUserContext() is called by GlobalExceptionHandler, so we can't use never()
    verify(campaignService, never())
        .performBulkAction(any(CampaignBulkActionRequestDTO.class), any(IamUserContext.class));
  }

  // ========== autosaveCampaign Tests ==========

  @Test
  void autosaveCampaign_WithValidData_ShouldReturnAutosavedCampaign() throws Exception {
    // Given
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder()
            .name("Updated Name")
            .description("Updated Description")
            .budget(15000.0)
            .build();

    CampaignResponseDTO autosavedResponse =
        CampaignResponseDTO.builder()
            .id("campaign123")
            .name("Updated Name")
            .description("Updated Description")
            .status(Campaign.Status.DRAFT)
            .budget(15000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").name("Test Brand").build())
            .agency(Campaign.CampaignAgency.builder().id("agency123").name("Test Agency").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    when(campaignService.autosaveCampaign(eq("campaign123"), any(CampaignAutosaveRequestDTO.class)))
        .thenReturn(autosavedResponse);

    // When & Then
    mockMvc
        .perform(
            patch("/api/v1/campaigns/campaign123/autosave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(autosaveRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("campaign123"))
        .andExpect(jsonPath("$.data.name").value("Updated Name"))
        .andExpect(jsonPath("$.data.status").value("DRAFT"))
        .andExpect(jsonPath("$.data.budget").value(15000.0))
        .andExpect(jsonPath("$.data.brand.name").value("Test Brand"))
        .andExpect(jsonPath("$.data.agency.name").value("Test Agency"));

    verify(campaignService)
        .autosaveCampaign(eq("campaign123"), any(CampaignAutosaveRequestDTO.class));
  }

  @Test
  void autosaveCampaign_WithPerformance_ShouldReturnSavedPerformance() throws Exception {
    // Given
    CampaignForecastDTO performance =
        CampaignForecastDTO.builder()
            .totalInventories(5)
            .estimatedImpression(1000000L)
            .estimatedReach(50000L)
            .sov(15.5)
            .totalCost(10000.50)
            .plannedSot(5000.0)
            .totalSot(10000.0)
            .build();

    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder().performance(performance).build();

    CampaignResponseDTO autosavedResponse =
        CampaignResponseDTO.builder()
            .id("campaign123")
            .name("Test Campaign")
            .status(Campaign.Status.DRAFT)
            .performance(performance)
            .build();

    when(campaignService.autosaveCampaign(eq("campaign123"), any(CampaignAutosaveRequestDTO.class)))
        .thenReturn(autosavedResponse);

    // When & Then
    mockMvc
        .perform(
            patch("/api/v1/campaigns/campaign123/autosave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(autosaveRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("campaign123"))
        .andExpect(jsonPath("$.data.status").value("DRAFT"))
        .andExpect(jsonPath("$.data.performance.totalInventories").value(5))
        .andExpect(jsonPath("$.data.performance.estimatedImpression").value(1000000))
        .andExpect(jsonPath("$.data.performance.estimatedReach").value(50000))
        .andExpect(jsonPath("$.data.performance.sov").value(15.5))
        .andExpect(jsonPath("$.data.performance.totalCost").value(10000.50))
        .andExpect(jsonPath("$.data.performance.plannedSot").value(5000.0))
        .andExpect(jsonPath("$.data.performance.totalSot").value(10000.0));

    ArgumentCaptor<CampaignAutosaveRequestDTO> captor =
        ArgumentCaptor.forClass(CampaignAutosaveRequestDTO.class);
    verify(campaignService).autosaveCampaign(eq("campaign123"), captor.capture());
    assertThat(captor.getValue().getPerformance()).isEqualTo(performance);
  }

  @Test
  void autosaveCampaign_WithInvalidId_ShouldReturnNotFound() throws Exception {
    // Given
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder().name("Updated Name").build();

    when(campaignService.autosaveCampaign(eq("invalid123"), any(CampaignAutosaveRequestDTO.class)))
        .thenThrow(new com.mw.planner.exception.campaign.CampaignNotFoundException("invalid123"));

    // When & Then
    mockMvc
        .perform(
            patch("/api/v1/campaigns/invalid123/autosave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(autosaveRequest)))
        .andExpect(status().isNotFound());

    verify(campaignService)
        .autosaveCampaign(eq("invalid123"), any(CampaignAutosaveRequestDTO.class));
  }

  @Test
  void autosaveCampaign_WithNonDraftStatus_ShouldReturnBadRequest() throws Exception {
    // Given
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder().name("Updated Name").build();

    when(campaignService.autosaveCampaign(eq("campaign123"), any(CampaignAutosaveRequestDTO.class)))
        .thenThrow(
            new com.mw.planner.exception.campaign.CampaignInvalidStatusException(
                Campaign.Status.APPROVED, Campaign.Status.DRAFT));

    // When & Then
    mockMvc
        .perform(
            patch("/api/v1/campaigns/campaign123/autosave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(autosaveRequest)))
        .andExpect(status().isBadRequest());

    verify(campaignService)
        .autosaveCampaign(eq("campaign123"), any(CampaignAutosaveRequestDTO.class));
  }

  @Test
  void autosaveCampaign_WithNullBrandAndAgency_ShouldReturnAutosavedCampaign() throws Exception {
    // Given
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder()
            .name("Updated Name")
            .description("Updated Description")
            .budget(15000.0)
            .build();

    CampaignResponseDTO autosavedResponse =
        CampaignResponseDTO.builder()
            .id("campaign123")
            .name("Updated Name")
            .description("Updated Description")
            .status(Campaign.Status.DRAFT)
            .budget(15000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    when(campaignService.autosaveCampaign(eq("campaign123"), any(CampaignAutosaveRequestDTO.class)))
        .thenReturn(autosavedResponse);

    // When & Then
    mockMvc
        .perform(
            patch("/api/v1/campaigns/campaign123/autosave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(autosaveRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("campaign123"))
        .andExpect(jsonPath("$.data.name").value("Updated Name"))
        .andExpect(jsonPath("$.data.status").value("DRAFT"))
        .andExpect(jsonPath("$.data.budget").value(15000.0))
        .andExpect(jsonPath("$.data.brand.name").doesNotExist())
        .andExpect(jsonPath("$.data.agency").doesNotExist());

    verify(campaignService)
        .autosaveCampaign(eq("campaign123"), any(CampaignAutosaveRequestDTO.class));
  }

  @Test
  void autosaveCampaign_WithCountryChange_ShouldReturnAutosavedCampaign() throws Exception {
    // Given
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder()
            .name("Updated Name")
            .countryId("UK") // Different country
            .build();

    CampaignResponseDTO autosavedResponse =
        CampaignResponseDTO.builder()
            .id("campaign123")
            .name("Updated Name")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").name("Test Brand").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("UK")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    when(campaignService.autosaveCampaign(eq("campaign123"), any(CampaignAutosaveRequestDTO.class)))
        .thenReturn(autosavedResponse);

    // When & Then
    mockMvc
        .perform(
            patch("/api/v1/campaigns/campaign123/autosave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(autosaveRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("campaign123"))
        .andExpect(jsonPath("$.data.name").value("Updated Name"))
        .andExpect(jsonPath("$.data.status").value("DRAFT"))
        .andExpect(jsonPath("$.data.countryId").value("UK"));

    verify(campaignService)
        .autosaveCampaign(eq("campaign123"), any(CampaignAutosaveRequestDTO.class));
  }

  // ========== Edge Cases and Error Handling Tests ==========

  @Test
  void createCampaign_WithNullRequestBody_ShouldReturnBadRequest() throws Exception {
    // When & Then
    mockMvc
        .perform(post("/api/v1/campaigns").contentType(MediaType.APPLICATION_JSON).content(""))
        .andExpect(status().isBadRequest());

    // getUserContext() is called by GlobalExceptionHandler, so we can't use never()
    verify(campaignService, never()).createCampaign(any(CampaignRequestDTO.class));
  }

  @Test
  void updateCampaign_WithNullRequestBody_ShouldReturnBadRequest() throws Exception {
    // When & Then
    mockMvc
        .perform(
            put("/api/v1/campaigns/campaign123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
        .andExpect(status().isBadRequest());

    verify(campaignService, never()).updateCampaign(anyString(), any(CampaignRequestDTO.class));
  }

  @Test
  void performBulkAction_WithNullRequestBody_ShouldReturnBadRequest() throws Exception {
    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaigns/bulk-actions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
        .andExpect(status().isBadRequest());

    // getUserContext() is called by GlobalExceptionHandler, so we can't use never()
    verify(campaignService, never())
        .performBulkAction(any(CampaignBulkActionRequestDTO.class), any(IamUserContext.class));
  }

  @Test
  void autosaveCampaign_WithNullRequestBody_ShouldReturnBadRequest() throws Exception {
    // When & Then
    mockMvc
        .perform(
            patch("/api/v1/campaigns/campaign123/autosave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
        .andExpect(status().isBadRequest());

    verify(campaignService, never())
        .autosaveCampaign(anyString(), any(CampaignAutosaveRequestDTO.class));
  }

  @Test
  void getCampaignsWithFilters_WithInvalidPaginationParameters_ShouldUseDefaults()
      throws Exception {
    // Given
    CampaignFilterResponseDTO filterResponseDTO =
        CampaignFilterResponseDTO.builder()
            .id("campaign123")
            .name("Test Campaign")
            .status(String.valueOf(Campaign.Status.DRAFT))
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .brandName("Test Brand")
            .categoryName("Sports")
            .userName("Test User")
            .currentCompanyId("company456")
            .currentCompanyName("Test Company Ltd")
            .inventory(5)
            .estimatedImpression(100000L)
            .estimatedReach(50000L)
            .sov(15.5)
            .totalSot(82.0)
            .build();

    Page<CampaignFilterResponseDTO> campaignPage =
        new PageImpl<>(List.of(filterResponseDTO), PageRequest.of(0, 10), 1);
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(campaignService.getCampaignsWithFilters(any(CampaignFilterDTO.class), any(Pageable.class)))
        .thenReturn(campaignPage);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaigns")
                .param("page", "-1") // Invalid page
                .param("size", "0")) // Invalid size
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(userService).getIamUserContext();
    verify(campaignService)
        .getCampaignsWithFilters(any(CampaignFilterDTO.class), any(Pageable.class));
  }

  // ========== getCampaignMediaPlanDetails Tests ==========

  @Test
  void getCampaignMediaPlanDetails_WithValidId_ShouldReturnMediaPlanDetails() throws Exception {
    // Given
    HeaderInfoDTO headerInfo =
        HeaderInfoDTO.builder()
            .id("campaign123")
            .name("Test Campaign")
            .startDate(LocalDate.now().plusDays(1).toString())
            .endDate(LocalDate.now().plusDays(31).toString())
            .budget(10000.0)
            .status("DRAFT")
            .currency("USD")
            .preparedBy("John Doe")
            .duration(30)
            .build();

    CampaignForecastDTO performanceMetrics =
        CampaignForecastDTO.builder()
            .estimatedImpression(100000L)
            .estimatedReach(50000L)
            .estimatedFrequency(2.0)
            .avgECpm(5.0)
            .avgCpm(300.0)
            .estimatedAdPlays(6000L)
            .sov(45.0)
            .totalSot(6.0)
            .build();

    AudienceDemographicsTargetingStrategyDTO demographics =
        AudienceDemographicsTargetingStrategyDTO.builder()
            .ageGroups(List.of("18-24", "25-34"))
            .interests(List.of("Sports", "Technology"))
            .lifestyle(List.of("Urban"))
            .incomeLevel(List.of("High"))
            .build();

    Map<String, Object> geographicTargeting = new HashMap<>();
    geographicTargeting.put("cities", List.of());
    geographicTargeting.put("venueTypes", List.of());

    SchedulesDTO schedules =
        SchedulesDTO.builder().dailySchedule(Map.of("6am-10am", 25.0, "10am-2pm", 30.0)).build();

    CampaignMediaPlanResponseDTO mediaPlanResponse =
        CampaignMediaPlanResponseDTO.builder()
            .headerInfoDTO(headerInfo)
            .campaignForecast(performanceMetrics)
            .audienceDemographicsTargetingStrategyDTO(demographics)
            .schedulesDTO(schedules)
            .build();

    when(campaignService.getCampaignMediaPlanDetails("campaign123")).thenReturn(mediaPlanResponse);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaigns/campaign123/media-plan"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.headerInfo").exists())
        .andExpect(jsonPath("$.data.headerInfo.id").value("campaign123"))
        .andExpect(jsonPath("$.data.headerInfo.name").value("Test Campaign"))
        .andExpect(jsonPath("$.data.headerInfo.status").value("DRAFT"))
        .andExpect(jsonPath("$.data.headerInfo.budget").value(10000.0))
        .andExpect(jsonPath("$.data.headerInfo.duration").value(30))
        .andExpect(jsonPath("$.data.headerInfo.preparedBy").value("John Doe"))
        .andExpect(jsonPath("$.data.performanceMetrics").exists())
        .andExpect(jsonPath("$.data.performanceMetrics.estimatedImpression").value(100000))
        .andExpect(jsonPath("$.data.performanceMetrics.estimatedReach").value(50000))
        .andExpect(jsonPath("$.data.performanceMetrics.estimatedFrequency").value(2.0))
        .andExpect(jsonPath("$.data.audienceDemographics").exists())
        .andExpect(jsonPath("$.data.schedules").exists());

    verify(campaignService).getCampaignMediaPlanDetails("campaign123");
  }

  @Test
  void getCampaignMediaPlanDetails_WithInvalidId_ShouldReturnNotFound() throws Exception {
    // Given
    when(campaignService.getCampaignMediaPlanDetails("invalid123"))
        .thenThrow(new com.mw.planner.exception.campaign.CampaignNotFoundException("invalid123"));

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaigns/invalid123/media-plan"))
        .andExpect(status().isNotFound());

    verify(campaignService).getCampaignMediaPlanDetails("invalid123");
  }

  @Test
  void getCampaignMediaPlanDetails_WithMinimalData_ShouldReturnMediaPlanDetails() throws Exception {
    // Given
    HeaderInfoDTO headerInfo =
        HeaderInfoDTO.builder()
            .id("campaign123")
            .name("Minimal Campaign")
            .startDate(LocalDate.now().plusDays(1).toString())
            .endDate(LocalDate.now().plusDays(31).toString())
            .budget(10000.0)
            .status("DRAFT")
            .duration(30)
            .currency("USD")
            .preparedBy("Unknown")
            .build();

    CampaignForecastDTO performanceMetrics = CampaignForecastDTO.builder().build();

    AudienceDemographicsTargetingStrategyDTO demographics =
        AudienceDemographicsTargetingStrategyDTO.builder().build();

    Map<String, Object> geographicTargeting = new HashMap<>();

    SchedulesDTO schedules = SchedulesDTO.builder().build();

    CampaignMediaPlanResponseDTO mediaPlanResponse =
        CampaignMediaPlanResponseDTO.builder()
            .headerInfoDTO(headerInfo)
            .campaignForecast(performanceMetrics)
            .audienceDemographicsTargetingStrategyDTO(demographics)
            .schedulesDTO(schedules)
            .build();

    when(campaignService.getCampaignMediaPlanDetails("campaign123")).thenReturn(mediaPlanResponse);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaigns/campaign123/media-plan"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.headerInfo").exists())
        .andExpect(jsonPath("$.data.headerInfo.id").value("campaign123"))
        .andExpect(jsonPath("$.data.headerInfo.name").value("Minimal Campaign"))
        .andExpect(jsonPath("$.data.brandDetails").doesNotExist());

    verify(campaignService).getCampaignMediaPlanDetails("campaign123");
  }

  @Test
  void createCampaign_WithGeometryContainingPoiAndMetadata_ShouldReturnCreatedCampaign()
      throws Exception {
    // Given
    List<String> poiList = List.of("poi1", "poi2", "poi3");
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value2");

    Campaign.Targeting.Geofencing.Geometry geometry =
        Campaign.Targeting.Geofencing.Geometry.builder()
            .name("Test Geometry")
            .type("Polygon")
            .coordinates(
                List.of(List.of(0.0, 0.0), List.of(1.0, 0.0), List.of(1.0, 1.0), List.of(0.0, 1.0)))
            .isIncluded(true)
            .poi(poiList)
            .metadata(metadata)
            .build();

    Campaign.Targeting.Geofencing geofencing =
        Campaign.Targeting.Geofencing.builder().geometries(List.of(geometry)).build();

    Campaign.Targeting targeting = Campaign.Targeting.builder().geofencing(geofencing).build();

    Campaign campaignWithGeometry =
        Campaign.builder()
            .name("Campaign with Geometry")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .targeting(targeting)
            .build();
    campaignWithGeometry.setId("campaign456");
    campaignWithGeometry.setCreatedAt(LocalDateTime.now());
    campaignWithGeometry.setUpdatedAt(LocalDateTime.now());

    CampaignResponseDTO responseDTO = CampaignResponseDTO.mapToDto(campaignWithGeometry);

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(campaignService.createCampaign(any(CampaignRequestDTO.class))).thenReturn(responseDTO);

    // Create request DTO with geometry
    CampaignRequestDTO requestDTO =
        CampaignRequestDTO.builder()
            .name("Campaign with Geometry")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .targeting(
                CampaignRequestDTO.Targeting.builder()
                    .geofencing(
                        CampaignRequestDTO.Targeting.Geofencing.builder()
                            .geometries(
                                List.of(
                                    CampaignRequestDTO.Targeting.Geofencing.Geometry.builder()
                                        .name("Test Geometry")
                                        .type("Polygon")
                                        .coordinates(
                                            List.of(
                                                List.of(0.0, 0.0),
                                                List.of(1.0, 0.0),
                                                List.of(1.0, 1.0),
                                                List.of(0.0, 1.0)))
                                        .isIncluded(true)
                                        .poi(poiList)
                                        .metadata(metadata)
                                        .build()))
                            .build())
                    .build())
            .build();

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaigns")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("campaign456"))
        .andExpect(jsonPath("$.data.name").value("Campaign with Geometry"));

    verify(userService).getIamUserContext();
    verify(campaignService).createCampaign(any(CampaignRequestDTO.class));
  }

  @Test
  void createCampaign_WithLocationContainingPoiAndMetadata_ShouldReturnCreatedCampaign()
      throws Exception {
    // Given
    List<String> poiList = List.of("location_poi1", "location_poi2");
    Map<String, String> metadata = new HashMap<>();
    metadata.put("location_key1", "location_value1");
    metadata.put("location_key2", "location_value2");

    Campaign.Targeting.Geofencing.Location location =
        Campaign.Targeting.Geofencing.Location.builder()
            .name("Test Location")
            .lat(40.7128)
            .lng(-74.0060)
            .radius(1000.0)
            .address("123 Test St, New York, NY")
            .isIncluded(true)
            .poi(poiList)
            .metadata(metadata)
            .build();

    Campaign.Targeting.Geofencing geofencing =
        Campaign.Targeting.Geofencing.builder().locations(List.of(location)).build();

    Campaign.Targeting targeting = Campaign.Targeting.builder().geofencing(geofencing).build();

    Campaign campaignWithLocation =
        Campaign.builder()
            .name("Campaign with Location")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .targeting(targeting)
            .build();
    campaignWithLocation.setId("campaign789");
    campaignWithLocation.setCreatedAt(LocalDateTime.now());
    campaignWithLocation.setUpdatedAt(LocalDateTime.now());

    CampaignResponseDTO responseDTO = CampaignResponseDTO.mapToDto(campaignWithLocation);

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(campaignService.createCampaign(any(CampaignRequestDTO.class))).thenReturn(responseDTO);

    // Create request DTO with location
    CampaignRequestDTO requestDTO =
        CampaignRequestDTO.builder()
            .name("Campaign with Location")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .targeting(
                CampaignRequestDTO.Targeting.builder()
                    .geofencing(
                        CampaignRequestDTO.Targeting.Geofencing.builder()
                            .locations(
                                List.of(
                                    CampaignRequestDTO.Targeting.Geofencing.Location.builder()
                                        .name("Test Location")
                                        .lat(40.7128)
                                        .lng(-74.0060)
                                        .radius(1000.0)
                                        .address("123 Test St, New York, NY")
                                        .isIncluded(true)
                                        .poi(poiList)
                                        .metadata(metadata)
                                        .build()))
                            .build())
                    .build())
            .build();

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaigns")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("campaign789"))
        .andExpect(jsonPath("$.data.name").value("Campaign with Location"));

    verify(userService).getIamUserContext();
    verify(campaignService).createCampaign(any(CampaignRequestDTO.class));
  }

  // ========== getCampaignCostSplitBy Tests ==========

  @Test
  void getCampaignCostSplitBy_WithValidIdAndSplitBy_ShouldReturnCostSplit() throws Exception {
    // Given
    String campaignId = "campaign123";
    CostSplit splitBy = CostSplit.MEDIA_OWNER;
    List<CostSplitByResponseDTO> costSplitList =
        List.of(CostSplitByResponseDTO.builder().name("mediaOwner123").totalAmount(1000.0).build());

    when(campaignService.getCampaignCostSplitBy(eq(campaignId), eq(splitBy), any(Locale.class)))
        .thenReturn(costSplitList);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaigns/" + campaignId + "/cost-split-by")
                .param("splitBy", splitBy.name()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].name").value("mediaOwner123"))
        .andExpect(jsonPath("$.data[0].totalAmount").value(1000.0));

    verify(campaignService).getCampaignCostSplitBy(eq(campaignId), eq(splitBy), any(Locale.class));
  }

  @Test
  void getCampaignCostSplitBy_WhenCampaignNotFound_ShouldReturnNotFound() throws Exception {
    // Given
    String campaignId = "nonexistent";
    CostSplit splitBy = CostSplit.MEDIA_OWNER;

    when(campaignService.getCampaignCostSplitBy(eq(campaignId), eq(splitBy), any(Locale.class)))
        .thenThrow(new com.mw.planner.exception.campaign.CampaignNotFoundException(campaignId));

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaigns/" + campaignId + "/cost-split-by")
                .param("splitBy", splitBy.name()))
        .andExpect(status().isNotFound());

    verify(campaignService).getCampaignCostSplitBy(eq(campaignId), eq(splitBy), any(Locale.class));
  }

  @Test
  void getCampaignCostSplitBy_WithDifferentSplitByValues_ShouldReturnCorrectResults()
      throws Exception {
    // Given
    String campaignId = "campaign123";
    CostSplit splitBy = CostSplit.INVENTORY_TYPE;
    List<CostSplitByResponseDTO> costSplitList =
        List.of(
            CostSplitByResponseDTO.builder().name("CLASSIC").totalAmount(500.0).build(),
            CostSplitByResponseDTO.builder().name("DIGITAL").totalAmount(300.0).build());

    when(campaignService.getCampaignCostSplitBy(eq(campaignId), eq(splitBy), any(Locale.class)))
        .thenReturn(costSplitList);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaigns/" + campaignId + "/cost-split-by")
                .param("splitBy", splitBy.name()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.hasSize(2)));

    verify(campaignService).getCampaignCostSplitBy(eq(campaignId), eq(splitBy), any(Locale.class));
  }

  // ========== getCampaignHistory Tests ==========

  @Test
  void getCampaignHistory_WithValidId_ShouldReturnHistory() throws Exception {
    // Given
    String campaignId = "campaign123";
    Pageable pageable = PageRequest.of(0, 10, Sort.by("updatedAt").descending());

    CampaignActivityResponseDTO activity1 =
        CampaignActivityResponseDTO.builder()
            .id("activity1")
            .campaignId(campaignId)
            .userId("user123")
            .createdBy("John Doe")
            .message("Created the Campaign")
            .createdAt(LocalDateTime.now().minusHours(1))
            .build();

    CampaignActivityResponseDTO activity2 =
        CampaignActivityResponseDTO.builder()
            .id("activity2")
            .campaignId(campaignId)
            .userId("user123")
            .createdBy("John Doe")
            .message("Updated the Campaign")
            .createdAt(LocalDateTime.now())
            .build();

    Page<CampaignActivityResponseDTO> historyPage =
        new PageImpl<>(List.of(activity2, activity1), pageable, 2);

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(new Campaign());
    when(campaignActivityService.getCampaignHistory(
            eq(campaignId), any(Locale.class), eq(pageable)))
        .thenReturn(historyPage);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaigns/" + campaignId + "/history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].id").value("activity2"))
        .andExpect(jsonPath("$.data.content[0].message").value("Updated the Campaign"))
        .andExpect(jsonPath("$.data.content[1].id").value("activity1"))
        .andExpect(jsonPath("$.data.totalElements").value(2));

    verify(campaignService).findByIdForCurrentMode(campaignId);
    verify(campaignActivityService)
        .getCampaignHistory(eq(campaignId), any(Locale.class), eq(pageable));
  }

  @Test
  void getCampaignHistory_WithPaginationParameters_ShouldUseCustomPagination() throws Exception {
    // Given
    String campaignId = "campaign123";
    Pageable pageable = PageRequest.of(1, 5, Sort.by("updatedAt").ascending());

    Page<CampaignActivityResponseDTO> historyPage =
        new PageImpl<>(Collections.emptyList(), pageable, 0);

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(new Campaign());
    when(campaignActivityService.getCampaignHistory(
            eq(campaignId), any(Locale.class), any(Pageable.class)))
        .thenReturn(historyPage);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaigns/" + campaignId + "/history")
                .param("page", "1")
                .param("size", "5")
                .param("sortBy", "updatedAt")
                .param("sortDir", "asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray());

    verify(campaignService).findByIdForCurrentMode(campaignId);
    verify(campaignActivityService)
        .getCampaignHistory(eq(campaignId), any(Locale.class), any(Pageable.class));
  }

  @Test
  void getCampaignHistory_WithInvalidCampaignId_ShouldReturnNotFound() throws Exception {
    // Given
    String campaignId = "nonexistent";

    when(campaignService.findByIdForCurrentMode(campaignId))
        .thenThrow(new com.mw.planner.exception.campaign.CampaignNotFoundException(campaignId));

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaigns/" + campaignId + "/history"))
        .andExpect(status().isNotFound());

    verify(campaignService).findByIdForCurrentMode(campaignId);
    verify(campaignActivityService, never())
        .getCampaignHistory(anyString(), any(Locale.class), any(Pageable.class));
  }

  @Test
  void getCampaignHistory_WithInvalidPaginationParameters_ShouldUseDefaults() throws Exception {
    // Given
    String campaignId = "campaign123";
    Pageable pageable = PageRequest.of(0, 10, Sort.by("updatedAt").descending());

    Page<CampaignActivityResponseDTO> historyPage =
        new PageImpl<>(Collections.emptyList(), pageable, 0);

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(new Campaign());
    when(campaignActivityService.getCampaignHistory(
            eq(campaignId), any(Locale.class), any(Pageable.class)))
        .thenReturn(historyPage);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaigns/" + campaignId + "/history")
                .param("page", "-1") // Invalid page
                .param("size", "0")) // Invalid size
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(campaignService).findByIdForCurrentMode(campaignId);
    verify(campaignActivityService)
        .getCampaignHistory(eq(campaignId), any(Locale.class), any(Pageable.class));
  }
}
