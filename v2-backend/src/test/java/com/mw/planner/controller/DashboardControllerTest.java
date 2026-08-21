package com.mw.planner.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.domain.Campaign;
import com.mw.planner.dto.BudgetPerformanceSummaryResponse;
import com.mw.planner.dto.BudgetSummary;
import com.mw.planner.dto.CampaignFilterResponseDTO;
import com.mw.planner.dto.CampaignStatistics;
import com.mw.planner.dto.CampaignSummaryRequestDTO;
import com.mw.planner.dto.CompanyLookupResponseDTO;
import com.mw.planner.dto.DashboardWidgetConfigItem;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.dto.sales.SalesPerformanceLocationItemDTO;
import com.mw.planner.enums.DashboardWidgetKey;
import com.mw.planner.enums.PerformanceSummaryType;
import com.mw.planner.enums.SalesPerformanceShowBy;
import com.mw.planner.exception.GlobalExceptionHandler;
import com.mw.planner.exception.campaign.CampaignDateRangeException;
import com.mw.planner.service.DashboardService;
import com.mw.planner.service.MessageService;
import com.mw.planner.service.MetricsService;
import com.mw.planner.service.UserService;
import com.mw.planner.service.iam.IamCompanyApiClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

  @Mock private DashboardService dashboardService;
  @Mock private UserService userService;
  @Mock private MessageService messageService;
  @Mock private MetricsService metricsService;
  @Mock private IamCompanyApiClient iamCompanyApiClient;

  @InjectMocks private DashboardController dashboardController;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;
  private IamUserContext testUserContext;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(dashboardController)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageService, metricsService, userService))
            .build();
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();

    testUserContext =
        IamUserContext.builder()
            .id("user123")
            .userId("user123")
            .companyId("company123")
            .locale(Locale.ENGLISH)
            .build();

    // Mock the getIamUserContext() call that GlobalExceptionHandler may make
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(iamCompanyApiClient.getCompanyCurrencyCodeWithCompanyId("company123"))
        .thenReturn(CompanyLookupResponseDTO.builder().currencyCode("USD").build());
  }

  @AfterEach
  void tearDown() {
    reset(dashboardService, userService, messageService, metricsService, iamCompanyApiClient);
  }

  @Test
  void getCampaignOverviewByStatus_WithDateRangeFilter_ShouldReturnStatistics() throws Exception {
    // Given
    LocalDate startDate = LocalDate.parse("2026-03-01");
    LocalDate endDate = LocalDate.parse("2026-03-10");
    CampaignStatistics stats =
        CampaignStatistics.builder()
            .totalCampaigns(10L)
            .draftCampaigns(3L)
            .approvedCampaigns(2L)
            .activeCampaigns(1L)
            .completedCampaigns(1L)
            .build();

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(dashboardService.getCampaignOverviewByStatus("company123", startDate, endDate))
        .thenReturn(stats);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/dashboard/campaign-overview-by-status")
                .param("startDate", "2026-03-01")
                .param("endDate", "2026-03-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalCampaigns").value(10))
        .andExpect(jsonPath("$.data.draftCampaigns").value(3))
        .andExpect(jsonPath("$.data.approvedCampaigns").value(2))
        .andExpect(jsonPath("$.data.activeCampaigns").value(1))
        .andExpect(jsonPath("$.data.completedCampaigns").value(1));

    verify(userService).getIamUserContext();
    verify(dashboardService).getCampaignOverviewByStatus("company123", startDate, endDate);
  }

  @Test
  void getCampaignOverviewByStatus_WithNoDates_ShouldReturnBadRequest() throws Exception {
    mockMvc
        .perform(get("/api/v1/dashboard/campaign-overview-by-status"))
        .andExpect(status().isBadRequest());

    verify(dashboardService, never()).getCampaignOverviewByStatus(any(), any(), any());
  }

  @Test
  void getCampaignOverviewByStatus_WithOnlyStartDate_ShouldReturnBadRequest() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dashboard/campaign-overview-by-status").param("startDate", "2026-03-01"))
        .andExpect(status().isBadRequest());

    verify(dashboardService, never()).getCampaignOverviewByStatus(any(), any(), any());
  }

  @Test
  void getCampaignOverviewByStatus_WithOnlyEndDate_ShouldReturnBadRequest() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dashboard/campaign-overview-by-status").param("endDate", "2026-03-10"))
        .andExpect(status().isBadRequest());

    verify(dashboardService, never()).getCampaignOverviewByStatus(any(), any(), any());
  }

  @Test
  void getCampaignOverviewByStatus_WithInvalidStartDate_ShouldReturnBadRequest() throws Exception {
    // When & Then
    mockMvc
        .perform(
            get("/api/v1/dashboard/campaign-overview-by-status")
                .param("startDate", "invalid-date")
                .param("endDate", "2026-03-10"))
        .andExpect(status().isBadRequest());

    verify(dashboardService, never()).getCampaignOverviewByStatus(any(), any(), any());
  }

  @Test
  void getCampaignOverviewByStatus_WithStartDateAfterEndDate_ShouldReturnBadRequest()
      throws Exception {
    // Given (service throws business exception)
    LocalDate startDate = LocalDate.parse("2026-03-10");
    LocalDate endDate = LocalDate.parse("2026-03-01");
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(dashboardService.getCampaignOverviewByStatus("company123", startDate, endDate))
        .thenThrow(new CampaignDateRangeException(startDate, endDate));

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/dashboard/campaign-overview-by-status")
                .param("startDate", "2026-03-10")
                .param("endDate", "2026-03-01"))
        .andExpect(status().isBadRequest());

    // Called by controller and also by GlobalExceptionHandler (locale resolution)
    verify(userService, atLeastOnce()).getIamUserContext();
    verify(dashboardService).getCampaignOverviewByStatus("company123", startDate, endDate);
  }

  @Test
  void getCampaignOverviewByStatus_WithExplicitCompanyId_ShouldUseProvidedCompanyId()
      throws Exception {
    LocalDate startDate = LocalDate.parse("2026-03-01");
    LocalDate endDate = LocalDate.parse("2026-03-10");
    CampaignStatistics stats = CampaignStatistics.builder().totalCampaigns(5L).build();

    when(dashboardService.getCampaignOverviewByStatus("other-company", startDate, endDate))
        .thenReturn(stats);

    mockMvc
        .perform(
            get("/api/v1/dashboard/campaign-overview-by-status")
                .param("startDate", "2026-03-01")
                .param("endDate", "2026-03-10")
                .param("companyId", "other-company"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalCampaigns").value(5));

    verify(dashboardService).getCampaignOverviewByStatus("other-company", startDate, endDate);
    verify(dashboardService, never()).getCampaignOverviewByStatus(eq("company123"), any(), any());
  }

  // --- getPerformanceSummary ---

  @Test
  void getPerformanceSummary_WithValidDates_ShouldReturnSummary() throws Exception {
    LocalDate startDate = LocalDate.parse("2026-01-01");
    LocalDate endDate = LocalDate.parse("2026-01-31");
    Map<String, BudgetSummary> dateWise =
        Map.of(
            "2026-01-15",
            BudgetSummary.builder()
                .budget(10000.0)
                .cost(5000.0)
                .remaining(5000.0)
                .reach(1000.0)
                .impressions(50000.0)
                .build());
    BudgetPerformanceSummaryResponse response =
        BudgetPerformanceSummaryResponse.builder().dateWiseSchedulePerDateRate(dateWise).build();

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(dashboardService.getPerformanceSummary(
            "company123", "USD", startDate, endDate, null, null))
        .thenReturn(response);

    mockMvc
        .perform(
            get("/api/v1/dashboard/performance-summary")
                .param("startDate", "2026-01-01")
                .param("endDate", "2026-01-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.dateWiseSchedulePerDateRate.2026-01-15.budget").value(10000.0))
        .andExpect(jsonPath("$.data.dateWiseSchedulePerDateRate.2026-01-15.cost").value(5000.0))
        .andExpect(
            jsonPath("$.data.dateWiseSchedulePerDateRate.2026-01-15.remaining").value(5000.0))
        .andExpect(jsonPath("$.data.dateWiseSchedulePerDateRate.2026-01-15.reach").value(1000.0))
        .andExpect(
            jsonPath("$.data.dateWiseSchedulePerDateRate.2026-01-15.impressions").value(50000.0));

    verify(userService).getIamUserContext();
    verify(dashboardService)
        .getPerformanceSummary("company123", "USD", startDate, endDate, null, null);
  }

  @Test
  void getPerformanceSummary_WithTypeCost_ShouldCallServiceWithCost() throws Exception {
    LocalDate startDate = LocalDate.parse("2026-01-01");
    LocalDate endDate = LocalDate.parse("2026-01-31");
    Map<String, BudgetSummary> dateWise =
        Map.of(
            "2026-01-15",
            BudgetSummary.builder().budget(5000.0).cost(2000.0).remaining(3000.0).build());
    BudgetPerformanceSummaryResponse response =
        BudgetPerformanceSummaryResponse.builder().dateWiseSchedulePerDateRate(dateWise).build();

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(dashboardService.getPerformanceSummary(
            "company123", "USD", startDate, endDate, PerformanceSummaryType.COST, null))
        .thenReturn(response);

    mockMvc
        .perform(
            get("/api/v1/dashboard/performance-summary")
                .param("startDate", "2026-01-01")
                .param("endDate", "2026-01-31")
                .param("type", "cost"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.dateWiseSchedulePerDateRate.2026-01-15.budget").value(5000.0))
        .andExpect(jsonPath("$.data.dateWiseSchedulePerDateRate.2026-01-15.cost").value(2000.0))
        .andExpect(
            jsonPath("$.data.dateWiseSchedulePerDateRate.2026-01-15.remaining").value(3000.0));

    verify(dashboardService)
        .getPerformanceSummary(
            "company123", "USD", startDate, endDate, PerformanceSummaryType.COST, null);
  }

  @Test
  void getPerformanceSummary_WithTypeReach_ShouldCallServiceWithReach() throws Exception {
    LocalDate startDate = LocalDate.parse("2026-01-01");
    LocalDate endDate = LocalDate.parse("2026-01-31");
    Map<String, BudgetSummary> dateWise =
        Map.of("2026-01-15", BudgetSummary.builder().reach(2500.0).impressions(100000.0).build());
    BudgetPerformanceSummaryResponse response =
        BudgetPerformanceSummaryResponse.builder().dateWiseSchedulePerDateRate(dateWise).build();

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(dashboardService.getPerformanceSummary(
            "company123", "USD", startDate, endDate, PerformanceSummaryType.REACH, null))
        .thenReturn(response);

    mockMvc
        .perform(
            get("/api/v1/dashboard/performance-summary")
                .param("startDate", "2026-01-01")
                .param("endDate", "2026-01-31")
                .param("type", "reach"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.dateWiseSchedulePerDateRate.2026-01-15.reach").value(2500.0))
        .andExpect(
            jsonPath("$.data.dateWiseSchedulePerDateRate.2026-01-15.impressions").value(100000.0));

    verify(dashboardService)
        .getPerformanceSummary(
            "company123", "USD", startDate, endDate, PerformanceSummaryType.REACH, null);
  }

  @Test
  void getPerformanceSummary_WithStatusesParam_ShouldCallServiceWithParsedStatusList()
      throws Exception {
    LocalDate startDate = LocalDate.parse("2026-01-01");
    LocalDate endDate = LocalDate.parse("2026-01-31");
    BudgetPerformanceSummaryResponse response =
        BudgetPerformanceSummaryResponse.builder().dateWiseSchedulePerDateRate(Map.of()).build();
    List<Campaign.Status> expectedStatuses =
        List.of(Campaign.Status.ACTIVE, Campaign.Status.APPROVED);

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(dashboardService.getPerformanceSummary(
            "company123", "USD", startDate, endDate, null, expectedStatuses))
        .thenReturn(response);

    mockMvc
        .perform(
            get("/api/v1/dashboard/performance-summary")
                .param("startDate", "2026-01-01")
                .param("endDate", "2026-01-31")
                .param("statuses", "ACTIVE,APPROVED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(dashboardService)
        .getPerformanceSummary("company123", "USD", startDate, endDate, null, expectedStatuses);
  }

  @Test
  void getPerformanceSummary_WithMissingStartDate_ShouldReturnBadRequest() throws Exception {
    mockMvc
        .perform(get("/api/v1/dashboard/performance-summary").param("endDate", "2026-01-31"))
        .andExpect(status().isBadRequest());

    verify(dashboardService, never())
        .getPerformanceSummary(any(), any(), any(), any(), any(), any());
  }

  @Test
  void getPerformanceSummary_WithMissingEndDate_ShouldReturnBadRequest() throws Exception {
    mockMvc
        .perform(get("/api/v1/dashboard/performance-summary").param("startDate", "2026-01-01"))
        .andExpect(status().isBadRequest());

    verify(dashboardService, never())
        .getPerformanceSummary(any(), any(), any(), any(), any(), any());
  }

  @Test
  void getPerformanceSummary_WithInvalidDate_ShouldReturnBadRequest() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dashboard/performance-summary")
                .param("startDate", "not-a-date")
                .param("endDate", "2026-01-31"))
        .andExpect(status().isBadRequest());

    verify(dashboardService, never())
        .getPerformanceSummary(any(), any(), any(), any(), any(), any());
  }

  @Test
  void getPerformanceSummary_WithInvalidType_ShouldReturnBadRequest() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dashboard/performance-summary")
                .param("startDate", "2026-01-01")
                .param("endDate", "2026-01-31")
                .param("type", "invalid-type"))
        .andExpect(status().isBadRequest());

    verify(dashboardService, never())
        .getPerformanceSummary(any(), any(), any(), any(), any(), any());
  }

  @Test
  void getPerformanceSummary_WithStartDateAfterEndDate_ShouldReturnBadRequest() throws Exception {
    LocalDate startDate = LocalDate.parse("2026-01-31");
    LocalDate endDate = LocalDate.parse("2026-01-01");
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(dashboardService.getPerformanceSummary(
            "company123", "USD", startDate, endDate, null, null))
        .thenThrow(new CampaignDateRangeException(startDate, endDate));

    mockMvc
        .perform(
            get("/api/v1/dashboard/performance-summary")
                .param("startDate", "2026-01-31")
                .param("endDate", "2026-01-01"))
        .andExpect(status().isBadRequest());

    verify(dashboardService)
        .getPerformanceSummary("company123", "USD", startDate, endDate, null, null);
  }

  @Test
  void getPerformanceSummary_WithExplicitCompanyId_ShouldUseProvidedCompanyIdAndSkipUserContext()
      throws Exception {
    LocalDate startDate = LocalDate.parse("2026-01-01");
    LocalDate endDate = LocalDate.parse("2026-01-31");
    BudgetPerformanceSummaryResponse response =
        BudgetPerformanceSummaryResponse.builder().dateWiseSchedulePerDateRate(Map.of()).build();

    when(iamCompanyApiClient.getCompanyCurrencyCodeWithCompanyId("other-company"))
        .thenReturn(CompanyLookupResponseDTO.builder().currencyCode("EUR").build());
    when(dashboardService.getPerformanceSummary(
            "other-company", "EUR", startDate, endDate, null, null))
        .thenReturn(response);

    mockMvc
        .perform(
            get("/api/v1/dashboard/performance-summary")
                .param("startDate", "2026-01-01")
                .param("endDate", "2026-01-31")
                .param("companyId", "other-company"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(userService, never()).getIamUserContext();
    verify(iamCompanyApiClient).getCompanyCurrencyCodeWithCompanyId("other-company");
    verify(dashboardService)
        .getPerformanceSummary("other-company", "EUR", startDate, endDate, null, null);
  }

  @Test
  void getWidgets_ShouldReturnAvailableWidgets() throws Exception {
    // Given
    List<DashboardWidgetConfigItem> widgets =
        List.of(
            DashboardWidgetConfigItem.builder()
                .key(DashboardWidgetKey.CAMPAIGN_OVERVIEW)
                .isEnable(true)
                .build());
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(dashboardService.getAvailableWidgets(testUserContext)).thenReturn(widgets);

    // When & Then
    mockMvc
        .perform(get("/api/v1/dashboard/widgets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].key").value("campaign-overview"))
        .andExpect(jsonPath("$.data[0].isEnable").value(true));

    verify(userService).getIamUserContext();
    verify(dashboardService).getAvailableWidgets(testUserContext);
  }

  @Test
  void updateWidgets_WithValidBody_ShouldUpsertAndReturnUpdatedConfig() throws Exception {
    // Given
    List<DashboardWidgetConfigItem> request =
        List.of(
            DashboardWidgetConfigItem.builder()
                .key(DashboardWidgetKey.CAMPAIGN_OVERVIEW)
                .isEnable(true)
                .build(),
            DashboardWidgetConfigItem.builder()
                .key(DashboardWidgetKey.BUDGET_OVERVIEW)
                .isEnable(false)
                .build());

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(dashboardService.upsertWidgets(testUserContext, request)).thenReturn(request);

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/dashboard/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].key").value("campaign-overview"))
        .andExpect(jsonPath("$.data[0].isEnable").value(true))
        .andExpect(jsonPath("$.data[1].key").value("budget-overview"))
        .andExpect(jsonPath("$.data[1].isEnable").value(false));

    verify(userService).getIamUserContext();
    verify(dashboardService).upsertWidgets(testUserContext, request);
  }

  @Test
  void updateWidgets_WithMissingRequiredFields_ShouldReturnBadRequest() throws Exception {
    // Given: key is null (violates @NotNull)
    String invalidBody =
        """
        [
          { "isEnable": true }
        ]
        """;

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/dashboard/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
        .andExpect(status().isBadRequest());

    verify(dashboardService, never()).upsertWidgets(any(), any());
  }

  @Test
  void updateWidgets_WithUnknownWidgetKey_ShouldReturnBadRequest() throws Exception {
    // Given: enum @JsonCreator should reject unknown value
    String invalidBody =
        """
        [
          { "key": "unknown-widget", "isEnable": true }
        ]
        """;

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/dashboard/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
        .andExpect(status().isBadRequest());

    verify(dashboardService, never()).upsertWidgets(any(), any());
  }

  // --- getCampaignPerformanceByTotalCost ---

  @Test
  void getCampaignPerformanceByTotalCost_WithAllParams_ShouldReturnSummaries() throws Exception {
    CampaignFilterResponseDTO dto =
        CampaignFilterResponseDTO.builder().id("c1").name("Campaign One").totalCost(1000.0).build();
    List<CampaignFilterResponseDTO> summaries = List.of(dto);
    when(dashboardService.getCampaignPerformanceByTotalCost(any(CampaignSummaryRequestDTO.class)))
        .thenReturn(summaries);
    when(userService.getActingCompanyId()).thenReturn("company123");

    mockMvc
        .perform(
            get("/api/v1/dashboard/campaign-performance")
                .param("startDate", "2026-01-01")
                .param("endDate", "2026-01-31")
                .param("status", "DRAFT")
                .param("sortBy", "totalCost")
                .param("sortDir", "desc")
                .param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value("c1"))
        .andExpect(jsonPath("$.data[0].name").value("Campaign One"))
        .andExpect(jsonPath("$.data[0].totalCost").value(1000.0));

    ArgumentCaptor<CampaignSummaryRequestDTO> captor =
        ArgumentCaptor.forClass(CampaignSummaryRequestDTO.class);
    verify(dashboardService).getCampaignPerformanceByTotalCost(captor.capture());
    CampaignSummaryRequestDTO request = captor.getValue();
    assertEquals(LocalDate.parse("2026-01-01"), request.getStartDate());
    assertEquals(LocalDate.parse("2026-01-31"), request.getEndDate());
    assertEquals(List.of(Campaign.Status.DRAFT), request.getStatuses());
    assertEquals("totalCost", request.getSortBy());
    assertEquals("desc", request.getSortDir());
    assertEquals(10, request.getLimit());
    assertEquals("company123", request.getCompanyId());
  }

  @Test
  void getCampaignPerformanceByTotalCost_WhenSwitched_ShouldScopeToActingCompany()
      throws Exception {
    when(dashboardService.getCampaignPerformanceByTotalCost(any(CampaignSummaryRequestDTO.class)))
        .thenReturn(List.of());
    // The user is switched into a secondary company; data must scope to it, not the primary.
    when(userService.getActingCompanyId()).thenReturn("company-switched");

    mockMvc
        .perform(
            get("/api/v1/dashboard/campaign-performance")
                .param("startDate", "2026-01-01")
                .param("endDate", "2026-01-31"))
        .andExpect(status().isOk());

    ArgumentCaptor<CampaignSummaryRequestDTO> captor =
        ArgumentCaptor.forClass(CampaignSummaryRequestDTO.class);
    verify(dashboardService).getCampaignPerformanceByTotalCost(captor.capture());
    assertEquals("company-switched", captor.getValue().getCompanyId());
    verify(userService).assertCanActForCompany("company-switched");
  }

  @Test
  void getCampaignPerformanceByTotalCost_WithForeignCompanyIdOverride_ShouldBeRejected()
      throws Exception {
    when(userService.getActingCompanyId()).thenReturn("company123");
    org.mockito.Mockito.doThrow(
            new org.springframework.security.access.AccessDeniedException("Cannot act for company"))
        .when(userService)
        .assertCanActForCompany("company-foreign");

    mockMvc
        .perform(
            get("/api/v1/dashboard/campaign-performance")
                .param("startDate", "2026-01-01")
                .param("endDate", "2026-01-31")
                .param("companyId", "company-foreign"))
        .andExpect(status().isForbidden());

    verify(dashboardService, never()).getCampaignPerformanceByTotalCost(any());
  }

  @Test
  void getCampaignPerformanceByTotalCost_WithNoParams_ShouldReturnBadRequest() throws Exception {
    when(dashboardService.getCampaignPerformanceByTotalCost(any(CampaignSummaryRequestDTO.class)))
        .thenReturn(List.of());

    mockMvc
        .perform(get("/api/v1/dashboard/campaign-performance"))
        .andExpect(status().isBadRequest());

    verify(dashboardService, never()).getCampaignPerformanceByTotalCost(any());
  }

  @Test
  void getCampaignPerformanceByTotalCost_WithInvalidDate_ShouldReturnBadRequest() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dashboard/campaign-performance")
                .param("startDate", "not-a-date")
                .param("endDate", "2026-01-31"))
        .andExpect(status().isBadRequest());

    verify(dashboardService, never()).getCampaignPerformanceByTotalCost(any());
  }

  @Test
  void getCampaignPerformanceByTotalCost_WithStartDateAfterEndDate_ShouldReturnBadRequest()
      throws Exception {
    when(dashboardService.getCampaignPerformanceByTotalCost(any(CampaignSummaryRequestDTO.class)))
        .thenThrow(
            new CampaignDateRangeException(
                LocalDate.parse("2026-01-31"), LocalDate.parse("2026-01-01")));

    mockMvc
        .perform(
            get("/api/v1/dashboard/campaign-performance")
                .param("startDate", "2026-01-31")
                .param("endDate", "2026-01-01"))
        .andExpect(status().isBadRequest());

    verify(dashboardService)
        .getCampaignPerformanceByTotalCost(any(CampaignSummaryRequestDTO.class));
  }

  @Test
  void getCampaignPerformanceByTotalCost_WithLimitClamped_ShouldClampTo1To500() throws Exception {
    when(dashboardService.getCampaignPerformanceByTotalCost(any(CampaignSummaryRequestDTO.class)))
        .thenReturn(List.of());

    mockMvc
        .perform(
            get("/api/v1/dashboard/campaign-performance")
                .param("startDate", "2026-01-01")
                .param("endDate", "2026-01-31")
                .param("limit", "1000"))
        .andExpect(status().isOk());

    ArgumentCaptor<CampaignSummaryRequestDTO> captor =
        ArgumentCaptor.forClass(CampaignSummaryRequestDTO.class);
    verify(dashboardService).getCampaignPerformanceByTotalCost(captor.capture());
    assertEquals(500, captor.getValue().getLimit());
  }

  @Test
  void getCampaignPerformanceByTotalCost_WithLimitZero_ShouldClampTo1() throws Exception {
    when(dashboardService.getCampaignPerformanceByTotalCost(any(CampaignSummaryRequestDTO.class)))
        .thenReturn(List.of());

    mockMvc
        .perform(
            get("/api/v1/dashboard/campaign-performance")
                .param("startDate", "2026-01-01")
                .param("endDate", "2026-01-31")
                .param("limit", "0"))
        .andExpect(status().isOk());

    ArgumentCaptor<CampaignSummaryRequestDTO> captor =
        ArgumentCaptor.forClass(CampaignSummaryRequestDTO.class);
    verify(dashboardService).getCampaignPerformanceByTotalCost(captor.capture());
    assertEquals(1, captor.getValue().getLimit());
  }

  @Test
  void getCampaignPerformanceByTotalCost_WithExplicitCompanyId_ShouldUseProvidedCompanyId()
      throws Exception {
    when(dashboardService.getCampaignPerformanceByTotalCost(any(CampaignSummaryRequestDTO.class)))
        .thenReturn(List.of());

    mockMvc
        .perform(
            get("/api/v1/dashboard/campaign-performance")
                .param("startDate", "2026-01-01")
                .param("endDate", "2026-01-31")
                .param("companyId", "specific-company"))
        .andExpect(status().isOk());

    ArgumentCaptor<CampaignSummaryRequestDTO> captor =
        ArgumentCaptor.forClass(CampaignSummaryRequestDTO.class);
    verify(dashboardService).getCampaignPerformanceByTotalCost(captor.capture());
    assertEquals("specific-company", captor.getValue().getCompanyId());
    verify(userService, never()).getIamUserContext();
  }

  // --- getSalesPerformanceSummary ---

  @Test
  void getSalesPerformanceSummary_WithDefaultParams_ShouldReturnCountryPage() throws Exception {
    // This endpoint defaults to:
    // - showBy=country
    // - page=0
    // - size=10
    LocalDate start = LocalDate.parse("2026-02-11");
    LocalDate end = LocalDate.parse("2026-02-12");

    Page<SalesPerformanceLocationItemDTO> page =
        new PageImpl<>(
            List.of(
                SalesPerformanceLocationItemDTO.builder()
                    .country("India")
                    .city(null)
                    .inventories(2L)
                    .countCampaigns(1L)
                    .cost(100.0)
                    .revenue(120.0)
                    .build()),
            PageRequest.of(0, 10),
            1);

    when(dashboardService.getSalesPerformanceSummary(
            start, end, SalesPerformanceShowBy.COUNTRY, null, null, 0, 10))
        .thenReturn(page);

    mockMvc
        .perform(
            get("/api/v1/dashboard/sales-performance-summary")
                .param("startDate", "2026-02-11")
                .param("endDate", "2026-02-12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].country").value("India"))
        .andExpect(jsonPath("$.data.content[0].revenue").value(120.0));

    verify(dashboardService)
        .getSalesPerformanceSummary(start, end, SalesPerformanceShowBy.COUNTRY, null, null, 0, 10);
  }

  @Test
  void getSalesPerformanceSummary_WithShowByCountry_ShouldIncludeClassificationInResponse()
      throws Exception {
    LocalDate start = LocalDate.parse("2026-02-11");
    LocalDate end = LocalDate.parse("2026-02-12");

    Page<SalesPerformanceLocationItemDTO> page =
        new PageImpl<>(
            List.of(
                SalesPerformanceLocationItemDTO.builder()
                    .country("Japan")
                    .city(null)
                    .inventories(5L)
                    .countCampaigns(1L)
                    .cost(500.0)
                    .revenue(600.0)
                    .classification(Map.of("CLASSIC_NETWORK", 2L, "Digital", 1L, "Transit", 2L))
                    .build()),
            PageRequest.of(0, 10),
            1);

    when(dashboardService.getSalesPerformanceSummary(
            start, end, SalesPerformanceShowBy.COUNTRY, null, null, 0, 10))
        .thenReturn(page);

    mockMvc
        .perform(
            get("/api/v1/dashboard/sales-performance-summary")
                .param("startDate", "2026-02-11")
                .param("endDate", "2026-02-12")
                .param("showBy", "country"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].country").value("Japan"))
        .andExpect(jsonPath("$.data.content[0].inventories").value(5))
        .andExpect(jsonPath("$.data.content[0].classification.CLASSIC_NETWORK").value(2))
        .andExpect(jsonPath("$.data.content[0].classification.Digital").value(1))
        .andExpect(jsonPath("$.data.content[0].classification.Transit").value(2));

    verify(dashboardService)
        .getSalesPerformanceSummary(start, end, SalesPerformanceShowBy.COUNTRY, null, null, 0, 10);
  }

  @Test
  void getSalesPerformanceSummary_WithShowByCity_ShouldReturnPagedItems() throws Exception {
    LocalDate start = LocalDate.parse("2026-02-11");
    LocalDate end = LocalDate.parse("2026-02-12");

    Page<SalesPerformanceLocationItemDTO> page =
        new PageImpl<>(
            List.of(
                SalesPerformanceLocationItemDTO.builder()
                    .country("India")
                    .city("Mumbai")
                    .inventories(2L)
                    .countCampaigns(1L)
                    .cost(100.0)
                    .revenue(120.0)
                    .build()),
            PageRequest.of(0, 10),
            1);

    when(dashboardService.getSalesPerformanceSummary(
            start, end, SalesPerformanceShowBy.CITY, null, null, 0, 10))
        .thenReturn(page);

    mockMvc
        .perform(
            get("/api/v1/dashboard/sales-performance-summary")
                .param("startDate", "2026-02-11")
                .param("endDate", "2026-02-12")
                .param("showBy", "city"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].country").value("India"))
        .andExpect(jsonPath("$.data.content[0].city").value("Mumbai"))
        .andExpect(jsonPath("$.data.content[0].revenue").value(120.0));

    verify(dashboardService)
        .getSalesPerformanceSummary(start, end, SalesPerformanceShowBy.CITY, null, null, 0, 10);
  }

  @Test
  void getSalesPerformanceSummary_WithShowByOverview_ShouldReturnBadRequest() throws Exception {
    when(messageService.getMessage(anyString(), any(Locale.class), any(Object[].class)))
        .thenReturn("Validation error");

    mockMvc
        .perform(
            get("/api/v1/dashboard/sales-performance-summary")
                .param("startDate", "2026-02-11")
                .param("endDate", "2026-02-12")
                .param("showBy", "overview"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    verify(dashboardService, never())
        .getSalesPerformanceSummary(any(), any(), any(), any(), any(), anyInt(), anyInt());
  }

  @Test
  void getSalesPerformanceSummary_WithMissingDates_ShouldReturnBadRequest() throws Exception {
    // startDate and endDate are required request parameters.
    mockMvc
        .perform(get("/api/v1/dashboard/sales-performance-summary").param("endDate", "2026-02-12"))
        .andExpect(status().isBadRequest());

    verify(dashboardService, never())
        .getSalesPerformanceSummary(any(), any(), any(), any(), any(), anyInt(), anyInt());
  }

  @Test
  void getSalesPerformanceSummary_WithInvalidShowBy_ShouldReturnBadRequest() throws Exception {
    // Invalid showBy values are rejected by SalesPerformanceShowBy#fromString.
    when(messageService.getMessage(anyString(), any(Locale.class), any(Object[].class)))
        .thenReturn("Validation error");

    mockMvc
        .perform(
            get("/api/v1/dashboard/sales-performance-summary")
                .param("startDate", "2026-02-11")
                .param("endDate", "2026-02-12")
                .param("showBy", "unknown"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    verify(dashboardService, never())
        .getSalesPerformanceSummary(any(), any(), any(), any(), any(), anyInt(), anyInt());
  }
}
