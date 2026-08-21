package com.mw.planner.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.dto.*;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.enums.CustomFeeBasedOn;
import com.mw.planner.enums.CustomFeeType;
import com.mw.planner.enums.PricingAction;
import com.mw.planner.exception.GlobalExceptionHandler;
import com.mw.planner.exception.campaign.CampaignNotFoundException;
import com.mw.planner.exception.customfee.CustomFeeAlreadyExistsException;
import com.mw.planner.exception.customfee.CustomFeeNotFoundException;
import com.mw.planner.exception.customfee.CustomFeeValidationException;
import com.mw.planner.service.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PriceManagementControllerTest {

  @Mock private CustomFeeService customFeeService;
  @Mock private CampaignInventorySchedulesService campaignInventorySchedulesService;
  @Mock private CampaignService campaignService;
  @Mock private MessageService messageService;
  @Mock private UserService userService;

  @InjectMocks private PriceManagementController priceManagementController;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;
  private CustomFeeRequestDTO testCustomFeeRequestDTO;
  private CustomFeeResponseDTO testCustomFeeResponseDTO;
  private IamUserContext testUserContext;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(priceManagementController)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageService, mock(MetricsService.class), userService))
            .build();
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();

    testCustomFeeRequestDTO =
        CustomFeeRequestDTO.builder()
            .name("Service Fee")
            .description("Service fee for campaign management")
            .type(CustomFeeType.PERCENTAGE)
            .value(10.5)
            .basedOn(CustomFeeBasedOn.BASE_COST)
            .isIncludeInMediaPlan(true)
            .isActive(true)
            .campaignId("campaign123")
            .build();

    testCustomFeeResponseDTO =
        CustomFeeResponseDTO.builder()
            .id("fee_123456")
            .name("Service Fee")
            .description("Service fee for campaign management")
            .type(CustomFeeType.PERCENTAGE)
            .value(10.5)
            .basedOn(CustomFeeBasedOn.BASE_COST)
            .isIncludeInMediaPlan(true)
            .isActive(true)
            .companyId("company123")
            .campaignId("campaign123")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

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
    reset(
        customFeeService,
        campaignInventorySchedulesService,
        campaignService,
        messageService,
        userService);
  }

  // ========== createCustomFee Tests ==========

  @Test
  void createCustomFee_WithValidData_ShouldReturnCreatedCustomFee() throws Exception {
    // Given
    when(customFeeService.createCustomFee(any(CustomFeeRequestDTO.class)))
        .thenReturn(testCustomFeeResponseDTO);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/price-management/custom-fees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCustomFeeRequestDTO)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("fee_123456"))
        .andExpect(jsonPath("$.data.name").value("Service Fee"))
        .andExpect(jsonPath("$.data.type").value("PERCENTAGE"))
        .andExpect(jsonPath("$.data.value").value(10.5));

    verify(customFeeService).createCustomFee(any(CustomFeeRequestDTO.class));
  }

  @Test
  void createCustomFee_WithInvalidData_ShouldReturnBadRequest() throws Exception {
    // Given
    CustomFeeRequestDTO invalidRequest =
        CustomFeeRequestDTO.builder()
            .name("") // Invalid: empty name
            .build();

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/price-management/custom-fees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());

    verify(customFeeService, never()).createCustomFee(any(CustomFeeRequestDTO.class));
  }

  @Test
  void createCustomFee_WithMissingRequiredFields_ShouldReturnBadRequest() throws Exception {
    // Given
    CustomFeeRequestDTO incompleteRequest =
        CustomFeeRequestDTO.builder()
            .name("Service Fee")
            // Missing required fields: type, value, basedOn
            .build();

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/price-management/custom-fees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(incompleteRequest)))
        .andExpect(status().isBadRequest());

    verify(customFeeService, never()).createCustomFee(any(CustomFeeRequestDTO.class));
  }

  @Test
  void createCustomFee_WithAlreadyExists_ShouldReturnConflict() throws Exception {
    // Given
    when(customFeeService.createCustomFee(any(CustomFeeRequestDTO.class)))
        .thenThrow(new CustomFeeAlreadyExistsException("Service Fee"));

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/price-management/custom-fees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCustomFeeRequestDTO)))
        .andExpect(status().isConflict());

    verify(customFeeService).createCustomFee(any(CustomFeeRequestDTO.class));
  }

  // ========== getCustomFeeById Tests ==========

  @Test
  void getCustomFeeById_WithValidId_ShouldReturnCustomFee() throws Exception {
    // Given
    when(customFeeService.getCustomFeeById("fee_123456")).thenReturn(testCustomFeeResponseDTO);

    // When & Then
    mockMvc
        .perform(get("/api/v1/price-management/custom-fees/fee_123456"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("fee_123456"))
        .andExpect(jsonPath("$.data.name").value("Service Fee"))
        .andExpect(jsonPath("$.data.type").value("PERCENTAGE"));

    verify(customFeeService).getCustomFeeById("fee_123456");
  }

  @Test
  void getCustomFeeById_WithInvalidId_ShouldReturnNotFound() throws Exception {
    // Given
    when(customFeeService.getCustomFeeById("invalid123"))
        .thenThrow(new CustomFeeNotFoundException("invalid123"));

    // When & Then
    mockMvc
        .perform(get("/api/v1/price-management/custom-fees/invalid123"))
        .andExpect(status().isNotFound());

    verify(customFeeService).getCustomFeeById("invalid123");
  }

  // ========== getCustomFees Tests ==========

  @Test
  void getCustomFees_WithCampaignId_ShouldReturnCustomFees() throws Exception {
    // Given - companyId derived from logged-in user
    when(userService.getActingCompanyId()).thenReturn("company123");
    List<CustomFeeResponseDTO> customFees = Arrays.asList(testCustomFeeResponseDTO);
    when(customFeeService.getCustomFeesByCompanyAndCampaign("company123", "campaign123"))
        .thenReturn(customFees);

    // When & Then
    mockMvc
        .perform(get("/api/v1/price-management/custom-fees").param("campaignId", "campaign123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].id").value("fee_123456"))
        .andExpect(jsonPath("$.data[0].name").value("Service Fee"));

    verify(userService).getActingCompanyId();
    verify(customFeeService).getCustomFeesByCompanyAndCampaign("company123", "campaign123");
  }

  @Test
  void getCustomFees_WithEmptyList_ShouldReturnEmptyList() throws Exception {
    // Given
    when(userService.getActingCompanyId()).thenReturn("company123");
    when(customFeeService.getCustomFeesByCompanyAndCampaign("company123", "campaign123"))
        .thenReturn(Collections.emptyList());

    // When & Then
    mockMvc
        .perform(get("/api/v1/price-management/custom-fees").param("campaignId", "campaign123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").isEmpty());

    verify(userService).getActingCompanyId();
    verify(customFeeService).getCustomFeesByCompanyAndCampaign("company123", "campaign123");
  }

  @Test
  void getCustomFees_WithNoPrimaryCompany_ShouldReturnBadRequest() throws Exception {
    // Given - user has no primary company
    when(userService.getActingCompanyId()).thenReturn(null);

    // When & Then
    mockMvc.perform(get("/api/v1/price-management/custom-fees")).andExpect(status().isBadRequest());

    verify(userService).getActingCompanyId();
    verify(customFeeService, never()).getCustomFeesByCompanyAndCampaign(anyString(), any());
  }

  // ========== updateCustomFee Tests ==========

  @Test
  void updateCustomFee_WithValidData_ShouldReturnUpdatedCustomFee() throws Exception {
    // Given
    when(customFeeService.getCustomFeeById("fee_123456")).thenReturn(testCustomFeeResponseDTO);
    CustomFeeResponseDTO updatedResponse =
        CustomFeeResponseDTO.builder()
            .id("fee_123456")
            .name("Updated Service Fee")
            .description("Updated description")
            .type(CustomFeeType.PERCENTAGE)
            .value(15.0)
            .basedOn(CustomFeeBasedOn.BASE_COST)
            .isIncludeInMediaPlan(true)
            .isActive(true)
            .companyId("company123")
            .campaignId("campaign123")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    when(customFeeService.updateCustomFee(anyString(), any(CustomFeeRequestDTO.class)))
        .thenReturn(updatedResponse);

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/price-management/custom-fees/fee_123456")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCustomFeeRequestDTO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("fee_123456"))
        .andExpect(jsonPath("$.data.name").value("Updated Service Fee"))
        .andExpect(jsonPath("$.data.value").value(15.0));

    verify(customFeeService).updateCustomFee(eq("fee_123456"), any(CustomFeeRequestDTO.class));
  }

  @Test
  void updateCustomFee_WithInvalidId_ShouldReturnNotFound() throws Exception {
    // Given
    when(customFeeService.getCustomFeeById("invalid123"))
        .thenThrow(new CustomFeeNotFoundException("invalid123"));

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/price-management/custom-fees/invalid123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCustomFeeRequestDTO)))
        .andExpect(status().isNotFound());

    verify(customFeeService).getCustomFeeById("invalid123");
  }

  @Test
  void updateCustomFee_WithInvalidData_ShouldReturnBadRequest() throws Exception {
    // Given
    CustomFeeRequestDTO invalidRequest =
        CustomFeeRequestDTO.builder()
            .name("") // Invalid: empty name
            .build();

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/price-management/custom-fees/fee_123456")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());

    verify(customFeeService, never()).updateCustomFee(anyString(), any(CustomFeeRequestDTO.class));
  }

  @Test
  void updateCustomFee_WithAlreadyExists_ShouldReturnConflict() throws Exception {
    // Given
    when(customFeeService.getCustomFeeById("fee_123456")).thenReturn(testCustomFeeResponseDTO);
    when(customFeeService.updateCustomFee(anyString(), any(CustomFeeRequestDTO.class)))
        .thenThrow(new CustomFeeAlreadyExistsException("Service Fee"));

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/price-management/custom-fees/fee_123456")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCustomFeeRequestDTO)))
        .andExpect(status().isConflict());

    verify(customFeeService).updateCustomFee(eq("fee_123456"), any(CustomFeeRequestDTO.class));
  }

  // ========== updateDiscount Tests ==========

  @Test
  void updateDiscount_WithValidData_ShouldReturnSuccess() throws Exception {
    // Given
    UpdateDiscountRequestDTO request = new UpdateDiscountRequestDTO();
    request.setProposedPrice(9000.0);
    request.setScheduleId("schedule123");

    doNothing()
        .when(campaignInventorySchedulesService)
        .updateDiscountByProposedPrice(anyString(), anyDouble(), anyString());

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/price-management/campaign-inventory-schedules/cis123/update-discount")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value("Discount updated successfully"));

    verify(campaignInventorySchedulesService)
        .updateDiscountByProposedPrice(eq("cis123"), eq(9000.0), eq("schedule123"));
  }

  @Test
  void updateDiscount_WithInvalidData_ShouldReturnBadRequest() throws Exception {
    // Given
    UpdateDiscountRequestDTO invalidRequest = new UpdateDiscountRequestDTO();
    invalidRequest.setProposedPrice(-100.0); // Invalid: negative price

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/price-management/campaign-inventory-schedules/cis123/update-discount")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());

    verify(campaignInventorySchedulesService, never())
        .updateDiscountByProposedPrice(anyString(), anyDouble(), anyString());
  }

  // ========== getCampaignPriceSummary Tests ==========

  @Test
  void getCampaignPriceSummary_WithValidCampaignId_ShouldReturnSummary() throws Exception {
    // Given
    CampaignPriceSummaryResponseDTO summary =
        CampaignPriceSummaryResponseDTO.builder()
            .currentPrice(10000.0)
            .proposedPrice(9000.0)
            .changeInPrice(1000.0)
            .changeInPercentage(10.0)
            .mediaCost(8000.0)
            .discountedMediaCost(7200.0)
            .standardFees(1800.0)
            .customFees(Collections.emptyList())
            .build();

    when(campaignInventorySchedulesService.getCampaignPriceSummary("campaign123"))
        .thenReturn(summary);

    // When & Then
    mockMvc
        .perform(get("/api/v1/price-management/campaigns/campaign123/price-summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.currentPrice").value(10000.0))
        .andExpect(jsonPath("$.data.proposedPrice").value(9000.0))
        .andExpect(jsonPath("$.data.changeInPrice").value(1000.0));

    verify(campaignInventorySchedulesService).getCampaignPriceSummary("campaign123");
  }

  @Test
  void getCampaignPriceSummary_WithInvalidCampaignId_ShouldReturnNotFound() throws Exception {
    // Given
    when(campaignInventorySchedulesService.getCampaignPriceSummary("invalid123"))
        .thenThrow(new CampaignNotFoundException("invalid123"));

    // When & Then
    mockMvc
        .perform(get("/api/v1/price-management/campaigns/invalid123/price-summary"))
        .andExpect(status().isNotFound());

    verify(campaignInventorySchedulesService).getCampaignPriceSummary("invalid123");
  }

  // ========== bulkCreateOrUpdateCustomFees Tests ==========

  @Test
  void bulkCreateOrUpdateCustomFees_WithCrossModeExistingFee_ShouldReturnNotFound()
      throws Exception {
    // Given: an update entry targets an existing fee whose campaign is in the OTHER Test Mode
    // partition; the payload itself carries no campaignId (attempted bypass).
    BulkCustomFeeRequestDTO bulkRequest =
        BulkCustomFeeRequestDTO.builder()
            .id("fee_crossmode")
            .name("Cross Mode Fee")
            .type(CustomFeeType.PERCENTAGE)
            .value(12.5)
            .basedOn(CustomFeeBasedOn.BASE_COST)
            .isIncludeInMediaPlan(true)
            .isActive(true)
            .build();
    when(customFeeService.getCustomFeeById("fee_crossmode"))
        .thenReturn(
            CustomFeeResponseDTO.builder().id("fee_crossmode").campaignId("demoCampaign").build());
    when(campaignService.findByIdForCurrentMode("demoCampaign"))
        .thenThrow(new com.mw.planner.exception.campaign.CampaignNotFoundException("demoCampaign"));

    // When & Then: cross-mode behaves as not-found; the fee is never updated
    mockMvc
        .perform(
            post("/api/v1/price-management/custom-fees/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Arrays.asList(bulkRequest))))
        .andExpect(status().isNotFound());

    verify(customFeeService, never()).bulkCreateOrUpdateCustomFees(anyList());
  }

  @Test
  void bulkCreateOrUpdateCustomFees_WithValidData_ShouldReturnResults() throws Exception {
    // Given
    BulkCustomFeeRequestDTO bulkRequest =
        BulkCustomFeeRequestDTO.builder()
            .name("Bulk Service Fee")
            .description("Bulk fee description")
            .type(CustomFeeType.PERCENTAGE)
            .value(12.5)
            .basedOn(CustomFeeBasedOn.BASE_COST)
            .isIncludeInMediaPlan(true)
            .isActive(true)
            .campaignId("campaign123")
            .build();

    CustomFeeResponseDTO bulkResponse =
        CustomFeeResponseDTO.builder()
            .id("fee_bulk123")
            .name("Bulk Service Fee")
            .type(CustomFeeType.PERCENTAGE)
            .value(12.5)
            .companyId("company123")
            .campaignId("campaign123")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    List<BulkCustomFeeRequestDTO> bulkRequests = Arrays.asList(bulkRequest);
    List<CustomFeeResponseDTO> bulkResponses = Arrays.asList(bulkResponse);

    when(customFeeService.bulkCreateOrUpdateCustomFees(bulkRequests)).thenReturn(bulkResponses);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/price-management/custom-fees/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bulkRequests)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].id").value("fee_bulk123"))
        .andExpect(jsonPath("$.data[0].name").value("Bulk Service Fee"));

    verify(customFeeService).bulkCreateOrUpdateCustomFees(bulkRequests);
  }

  @Test
  void bulkCreateOrUpdateCustomFees_WithEmptyList_ShouldReturnBadRequest() throws Exception {
    // Given
    when(customFeeService.bulkCreateOrUpdateCustomFees(anyList()))
        .thenThrow(new CustomFeeValidationException("Custom fees list cannot be empty"));

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/price-management/custom-fees/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Collections.emptyList())))
        .andExpect(status().isBadRequest());

    verify(customFeeService).bulkCreateOrUpdateCustomFees(anyList());
  }

  // ========== getCampaignSchedulePrices Tests ==========

  @Test
  void getCampaignSchedulePrices_WithValidParams_ShouldReturnPage() throws Exception {
    // Given
    CampaignSchedulePriceResponseDTO schedulePrice =
        CampaignSchedulePriceResponseDTO.builder()
            .id("cis123")
            .inventoryId("inventory123")
            .inventoryName("Test Inventory")
            .build();

    Page<CampaignSchedulePriceResponseDTO> page =
        new PageImpl<>(Arrays.asList(schedulePrice), PageRequest.of(0, 10), 1);

    when(campaignInventorySchedulesService.getCampaignSchedulePrices(
            anyString(), any(), any(Pageable.class)))
        .thenReturn(page);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/price-management/campaigns/campaign123/schedule-prices")
                .param("page", "0")
                .param("size", "10")
                .param("sortBy", "updatedAt")
                .param("sortDir", "asc")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CampaignSchedulePriceFilterDTO())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].id").value("cis123"))
        .andExpect(jsonPath("$.data.totalElements").value(1));

    verify(campaignService).findByIdForCurrentMode("campaign123");
    verify(campaignInventorySchedulesService)
        .getCampaignSchedulePrices(anyString(), any(), any(Pageable.class));
  }

  @Test
  void getCampaignSchedulePrices_WithInvalidCampaignId_ShouldReturnNotFound() throws Exception {
    // Given
    when(campaignService.findByIdForCurrentMode("invalid123"))
        .thenThrow(new com.mw.planner.exception.campaign.CampaignNotFoundException("invalid123"));

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/price-management/campaigns/invalid123/schedule-prices")
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CampaignSchedulePriceFilterDTO())))
        .andExpect(status().isNotFound());

    verify(campaignService).findByIdForCurrentMode("invalid123");
    verify(campaignInventorySchedulesService, never())
        .getCampaignSchedulePrices(anyString(), any(), any(Pageable.class));
  }

  @Test
  void getCampaignSchedulePrices_WithInvalidPagination_ShouldUseDefaults() throws Exception {
    // Given
    Page<CampaignSchedulePriceResponseDTO> page =
        new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 1), 0);

    when(campaignInventorySchedulesService.getCampaignSchedulePrices(
            anyString(), any(), any(Pageable.class)))
        .thenReturn(page);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/price-management/campaigns/campaign123/schedule-prices")
                .param("page", "-1") // Invalid: negative page
                .param("size", "0") // Invalid: zero size
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CampaignSchedulePriceFilterDTO())))
        .andExpect(status().isOk());

    verify(campaignService).findByIdForCurrentMode("campaign123");
    verify(campaignInventorySchedulesService)
        .getCampaignSchedulePrices(anyString(), any(), any(Pageable.class));
  }

  // ========== acceptInventoryPrices Tests ==========

  @Test
  void acceptInventoryPrices_WithValidData_ShouldReturnSuccess() throws Exception {
    // Given
    AcceptInventoryPricesRequestDTO request = new AcceptInventoryPricesRequestDTO();
    request.setCampaignInventorySchedulesIds(Arrays.asList("cis1", "cis2"));

    when(messageService.getMessage(anyString(), any(Locale.class)))
        .thenReturn("Operation completed successfully");
    doNothing()
        .when(campaignInventorySchedulesService)
        .acceptInventoryPrices(anyString(), anyList());

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/price-management/campaigns/campaign123/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(campaignService).findByIdForCurrentMode("campaign123");
    verify(campaignInventorySchedulesService).acceptInventoryPrices(eq("campaign123"), anyList());
  }

  @Test
  void acceptInventoryPrices_WithInvalidCampaignId_ShouldReturnNotFound() throws Exception {
    // Given
    AcceptInventoryPricesRequestDTO request = new AcceptInventoryPricesRequestDTO();
    request.setCampaignInventorySchedulesIds(Arrays.asList("cis1", "cis2"));

    when(campaignService.findByIdForCurrentMode("invalid123"))
        .thenThrow(new com.mw.planner.exception.campaign.CampaignNotFoundException("invalid123"));

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/price-management/campaigns/invalid123/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());

    verify(campaignService).findByIdForCurrentMode("invalid123");
    verify(campaignInventorySchedulesService, never())
        .acceptInventoryPrices(anyString(), anyList());
  }

  @Test
  void acceptInventoryPrices_WithNullIds_ShouldAcceptAll() throws Exception {
    // Given
    AcceptInventoryPricesRequestDTO request = new AcceptInventoryPricesRequestDTO();
    request.setCampaignInventorySchedulesIds(null);

    when(messageService.getMessage(anyString(), any(Locale.class)))
        .thenReturn("Operation completed successfully");
    doNothing().when(campaignInventorySchedulesService).acceptInventoryPrices(anyString(), any());

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/price-management/campaigns/campaign123/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(campaignService).findByIdForCurrentMode("campaign123");
    verify(campaignInventorySchedulesService).acceptInventoryPrices(eq("campaign123"), isNull());
  }

  // ========== getPriceHistory Tests ==========

  @Test
  void getPriceHistory_WithValidParams_ShouldReturnPage() throws Exception {
    // Given
    PriceHistoryResponseDTO historyEntry =
        PriceHistoryResponseDTO.builder()
            .oldPrice(10000.0)
            .newPrice(9000.0)
            .action(PricingAction.PROPOSED)
            .createdBy("user123")
            .role("Media Owner")
            .createdAt(LocalDateTime.now())
            .build();

    Page<PriceHistoryResponseDTO> page =
        new PageImpl<>(Arrays.asList(historyEntry), PageRequest.of(0, 10), 1);

    when(campaignInventorySchedulesService.getPriceHistory(anyString(), any(Pageable.class)))
        .thenReturn(page);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/price-management/campaign-inventory-schedules/cis123/price-history")
                .param("page", "0")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].oldPrice").value(10000.0))
        .andExpect(jsonPath("$.data.content[0].newPrice").value(9000.0))
        .andExpect(jsonPath("$.data.content[0].action").value("PROPOSED"));

    verify(campaignInventorySchedulesService).getPriceHistory(eq("cis123"), any(Pageable.class));
  }

  @Test
  void getPriceHistory_WithInvalidPagination_ShouldUseDefaults() throws Exception {
    // Given
    Page<PriceHistoryResponseDTO> page =
        new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 1), 0);

    when(campaignInventorySchedulesService.getPriceHistory(anyString(), any(Pageable.class)))
        .thenReturn(page);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/price-management/campaign-inventory-schedules/cis123/price-history")
                .param("page", "-1") // Invalid: negative page
                .param("size", "0")) // Invalid: zero size
        .andExpect(status().isOk());

    verify(campaignInventorySchedulesService).getPriceHistory(eq("cis123"), any(Pageable.class));
  }
}
