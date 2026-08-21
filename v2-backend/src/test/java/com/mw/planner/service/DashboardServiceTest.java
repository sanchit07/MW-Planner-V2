package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.Inventory;
import com.mw.planner.domain.Schedule;
import com.mw.planner.domain.UserDashboardConfig;
import com.mw.planner.dto.BudgetPerformanceSummaryResponse;
import com.mw.planner.dto.BudgetSummary;
import com.mw.planner.dto.CampaignFilterResponseDTO;
import com.mw.planner.dto.CampaignStatistics;
import com.mw.planner.dto.CampaignSummaryRequestDTO;
import com.mw.planner.dto.CustomFeesContext;
import com.mw.planner.dto.DashboardWidgetConfigItem;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.enums.DashboardWidgetKey;
import com.mw.planner.enums.PerformanceSummaryType;
import com.mw.planner.exception.campaign.CampaignDateRangeException;
import com.mw.planner.exception.user.UserContextInvalidException;
import com.mw.planner.repository.CampaignRepository;
import com.mw.planner.repository.ScheduleRepository;
import com.mw.planner.repository.UserDashboardConfigRepository;
import com.mw.planner.service.dashboard.DashboardWidgetDefaultsProvider;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

  @Mock private CampaignService campaignService;
  @Mock private TestModeService testModeService;
  @Mock private CampaignRepository campaignRepository;
  @Mock private UserService userService;
  @Mock private UserDashboardConfigRepository userDashboardConfigRepository;
  @Mock private DashboardWidgetDefaultsProvider dashboardWidgetDefaultsProvider;
  @Mock private CampaignInventorySchedulesService campaignInventorySchedulesService;
  @Mock private CustomFeeService customFeeService;
  @Mock private InventoryService inventoryService;
  @Mock private ScheduleRepository scheduleRepository;

  @InjectMocks private DashboardService dashboardService;

  private CampaignStatistics expectedStats;

  @BeforeEach
  void setUp() {
    expectedStats =
        CampaignStatistics.builder()
            .totalCampaigns(10L)
            .draftCampaigns(3L)
            .reviewingCampaigns(2L)
            .pendingCampaigns(1L)
            .approvedCampaigns(2L)
            .dealRequestedCampaigns(1L)
            .activeCampaigns(1L)
            .negotiatingCampaigns(0L)
            .completedCampaigns(1L)
            .archivedCampaigns(0L)
            .build();
  }

  @AfterEach
  void tearDown() {
    reset(
        campaignService,
        userDashboardConfigRepository,
        dashboardWidgetDefaultsProvider,
        campaignRepository,
        userService,
        campaignInventorySchedulesService,
        customFeeService,
        inventoryService,
        scheduleRepository);
  }

  // --- getPerformanceSummary ---

  @Test
  void getPerformanceSummary_WithValidDatesAndNoType_ShouldReturnSummaryWithEmptyMap() {
    String companyId = "company123";
    LocalDate startDate = LocalDate.parse("2026-01-01");
    LocalDate endDate = LocalDate.parse("2026-01-31");

    when(campaignService.getCampaignsByCompanyOverlappingDateRange(
            companyId, startDate, endDate, null))
        .thenReturn(List.of());

    BudgetPerformanceSummaryResponse result =
        dashboardService.getPerformanceSummary(companyId, "USD", startDate, endDate, null, null);

    assertThat(result).isNotNull();
    assertThat(result.getDateWiseSchedulePerDateRate()).isEmpty();
    verify(campaignService)
        .getCampaignsByCompanyOverlappingDateRange(companyId, startDate, endDate, null);
    verifyNoInteractions(campaignInventorySchedulesService);
  }

  @Test
  void getPerformanceSummary_WithStatusFilter_PassesStatusesThroughToCampaignService() {
    String companyId = "company123";
    LocalDate startDate = LocalDate.parse("2026-01-01");
    LocalDate endDate = LocalDate.parse("2026-01-31");
    List<Campaign.Status> statuses = List.of(Campaign.Status.ACTIVE);

    when(campaignService.getCampaignsByCompanyOverlappingDateRange(
            companyId, startDate, endDate, statuses))
        .thenReturn(List.of());

    BudgetPerformanceSummaryResponse result =
        dashboardService.getPerformanceSummary(
            companyId, "USD", startDate, endDate, null, statuses);

    assertThat(result).isNotNull();
    verify(campaignService)
        .getCampaignsByCompanyOverlappingDateRange(companyId, startDate, endDate, statuses);
  }

  @Test
  void getPerformanceSummary_WithValidDatesAndTypeCost_ShouldReturnSummary() {
    String companyId = "company123";
    LocalDate startDate = LocalDate.parse("2026-01-01");
    LocalDate endDate = LocalDate.parse("2026-01-31");

    when(campaignService.getCampaignsByCompanyOverlappingDateRange(
            companyId, startDate, endDate, null))
        .thenReturn(List.of());
    when(campaignService.getCampaignsByCompanyOverlappingDateRange(
            companyId, LocalDate.parse("2025-12-01"), LocalDate.parse("2025-12-31"), null))
        .thenReturn(List.of());

    BudgetPerformanceSummaryResponse result =
        dashboardService.getPerformanceSummary(
            companyId, "USD", startDate, endDate, PerformanceSummaryType.COST, null);

    assertThat(result).isNotNull();
    assertThat(result.getDateWiseSchedulePerDateRate()).isEmpty();
    verify(campaignService)
        .getCampaignsByCompanyOverlappingDateRange(companyId, startDate, endDate, null);
  }

  @Test
  void getPerformanceSummary_WithValidDatesAndTypeReach_ShouldReturnSummary() {
    String companyId = "company123";
    LocalDate startDate = LocalDate.parse("2026-01-01");
    LocalDate endDate = LocalDate.parse("2026-01-31");

    when(campaignService.getCampaignsByCompanyOverlappingDateRange(
            companyId, startDate, endDate, null))
        .thenReturn(List.of());

    BudgetPerformanceSummaryResponse result =
        dashboardService.getPerformanceSummary(
            companyId, "USD", startDate, endDate, PerformanceSummaryType.REACH, null);

    assertThat(result).isNotNull();
    assertThat(result.getDateWiseSchedulePerDateRate()).isEmpty();
    verify(campaignService)
        .getCampaignsByCompanyOverlappingDateRange(companyId, startDate, endDate, null);
  }

  @Test
  void getPerformanceSummary_WithNullStartDate_ShouldThrowCampaignDateRangeException() {
    String companyId = "company123";
    LocalDate endDate = LocalDate.parse("2026-01-31");

    assertThatThrownBy(
            () ->
                dashboardService.getPerformanceSummary(companyId, "USD", null, endDate, null, null))
        .isInstanceOf(CampaignDateRangeException.class)
        .hasMessageContaining("Invalid date range");

    verifyNoInteractions(campaignService);
  }

  @Test
  void getPerformanceSummary_WithNullEndDate_ShouldThrowCampaignDateRangeException() {
    String companyId = "company123";
    LocalDate startDate = LocalDate.parse("2026-01-01");

    assertThatThrownBy(
            () ->
                dashboardService.getPerformanceSummary(
                    companyId, "USD", startDate, null, null, null))
        .isInstanceOf(CampaignDateRangeException.class)
        .hasMessageContaining("Invalid date range");

    verifyNoInteractions(campaignService);
  }

  @Test
  void getPerformanceSummary_WithStartDateAfterEndDate_ShouldThrowCampaignDateRangeException() {
    String companyId = "company123";
    LocalDate startDate = LocalDate.parse("2026-01-31");
    LocalDate endDate = LocalDate.parse("2026-01-01");

    assertThatThrownBy(
            () ->
                dashboardService.getPerformanceSummary(
                    companyId, "USD", startDate, endDate, null, null))
        .isInstanceOf(CampaignDateRangeException.class)
        .hasMessageContaining("Invalid date range");

    verifyNoInteractions(campaignService);
  }

  @Test
  void getCampaignOverviewByStatus_WithValidDates_ShouldDelegateToCampaignService() {
    // Given
    String companyId = "company123";
    LocalDate startDate = LocalDate.parse("2026-03-01");
    LocalDate endDate = LocalDate.parse("2026-03-10");

    when(campaignService.getCampaignStatistics(companyId, startDate, endDate))
        .thenReturn(expectedStats);

    // When
    CampaignStatistics result =
        dashboardService.getCampaignOverviewByStatus(companyId, startDate, endDate);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTotalCampaigns()).isEqualTo(10L);
    verify(campaignService).getCampaignStatistics(companyId, startDate, endDate);
  }

  @Test
  void getCampaignOverviewByStatus_WithNullDates_ShouldDelegateToCampaignService() {
    // Given
    String companyId = "company123";
    when(campaignService.getCampaignStatistics(companyId, null, null)).thenReturn(expectedStats);

    // When
    CampaignStatistics result = dashboardService.getCampaignOverviewByStatus(companyId, null, null);

    // Then
    assertThat(result).isNotNull();
    verify(campaignService).getCampaignStatistics(companyId, null, null);
  }

  @Test
  void
      getCampaignOverviewByStatus_WithStartDateAfterEndDate_ShouldThrowCampaignDateRangeException() {
    // Given
    String companyId = "company123";
    LocalDate startDate = LocalDate.parse("2026-03-10");
    LocalDate endDate = LocalDate.parse("2026-03-01");

    // When & Then
    assertThatThrownBy(
            () -> dashboardService.getCampaignOverviewByStatus(companyId, startDate, endDate))
        .isInstanceOf(CampaignDateRangeException.class)
        .hasMessageContaining("Invalid date range");

    verifyNoInteractions(campaignService);
  }

  @Test
  void getCampaignOverviewByStatus_WithSameStartAndEndDate_ShouldBeAllowed() {
    // Given
    String companyId = "company123";
    LocalDate sameDay = LocalDate.parse("2026-03-01");
    when(campaignService.getCampaignStatistics(companyId, sameDay, sameDay))
        .thenReturn(expectedStats);

    // When
    CampaignStatistics result =
        dashboardService.getCampaignOverviewByStatus(companyId, sameDay, sameDay);

    // Then
    assertThat(result).isNotNull();
    verify(campaignService).getCampaignStatistics(companyId, sameDay, sameDay);
  }

  @Test
  void getAvailableWidgets_WithExistingConfig_ShouldReturnStoredWidgets() {
    // Given
    IamUserContext userContext =
        IamUserContext.builder().userId("user123").companyId("company123").build();
    UserDashboardConfig cfg =
        UserDashboardConfig.builder()
            .userId("user123")
            .companyId("company123")
            .widgets(
                List.of(
                    UserDashboardConfig.DashboardWidgetConfig.builder()
                        .key(DashboardWidgetKey.CAMPAIGN_OVERVIEW)
                        .isEnable(false)
                        .build()))
            .build();

    when(userDashboardConfigRepository.findByUserIdAndCompanyId("user123", "company123"))
        .thenReturn(Optional.of(cfg));

    // When
    List<DashboardWidgetConfigItem> result = dashboardService.getAvailableWidgets(userContext);

    // Then
    assertThat(result)
        .containsExactly(
            DashboardWidgetConfigItem.builder()
                .key(DashboardWidgetKey.CAMPAIGN_OVERVIEW)
                .isEnable(false)
                .build());
    verify(userDashboardConfigRepository).findByUserIdAndCompanyId("user123", "company123");
    verifyNoInteractions(dashboardWidgetDefaultsProvider);
  }

  @Test
  void getAvailableWidgets_WhenNoConfig_ShouldReturnDefaults() {
    // Given
    IamUserContext userContext =
        IamUserContext.builder().userId("user123").companyId("company123").build();
    List<DashboardWidgetConfigItem> defaults =
        List.of(
            DashboardWidgetConfigItem.builder()
                .key(DashboardWidgetKey.CAMPAIGN_OVERVIEW)
                .isEnable(true)
                .build());

    when(userDashboardConfigRepository.findByUserIdAndCompanyId("user123", "company123"))
        .thenReturn(Optional.empty());
    when(dashboardWidgetDefaultsProvider.defaultsFor(userContext)).thenReturn(defaults);

    // When
    List<DashboardWidgetConfigItem> result = dashboardService.getAvailableWidgets(userContext);

    // Then
    assertThat(result).isSameAs(defaults);
    verify(userDashboardConfigRepository).findByUserIdAndCompanyId("user123", "company123");
    verify(dashboardWidgetDefaultsProvider).defaultsFor(userContext);
  }

  @Test
  void getAvailableWidgets_WhenConfigWidgetsEmpty_ShouldReturnDefaults() {
    // Given
    IamUserContext userContext =
        IamUserContext.builder().userId("user123").companyId("company123").build();
    UserDashboardConfig cfg =
        UserDashboardConfig.builder()
            .userId("user123")
            .companyId("company123")
            .widgets(List.of())
            .build();
    List<DashboardWidgetConfigItem> defaults =
        List.of(
            DashboardWidgetConfigItem.builder()
                .key(DashboardWidgetKey.CAMPAIGN_OVERVIEW)
                .isEnable(true)
                .build());

    when(userDashboardConfigRepository.findByUserIdAndCompanyId("user123", "company123"))
        .thenReturn(Optional.of(cfg));
    when(dashboardWidgetDefaultsProvider.defaultsFor(userContext)).thenReturn(defaults);

    // When
    List<DashboardWidgetConfigItem> result = dashboardService.getAvailableWidgets(userContext);

    // Then
    assertThat(result).isSameAs(defaults);
    verify(dashboardWidgetDefaultsProvider).defaultsFor(userContext);
  }

  @Test
  void upsertWidgets_ShouldNormalizeDuplicatesAndNulls_AndUpsert() {
    // Given
    IamUserContext userContext =
        IamUserContext.builder().userId("user123").companyId("company123").build();

    // List.of(...) does not allow null elements, so use Arrays.asList(...) for this case.
    List<DashboardWidgetConfigItem> requested =
        new ArrayList<>(
            Arrays.asList(
                DashboardWidgetConfigItem.builder()
                    .key(DashboardWidgetKey.CAMPAIGN_OVERVIEW)
                    .isEnable(true)
                    .build(),
                DashboardWidgetConfigItem.builder()
                    .key(DashboardWidgetKey.BUDGET_OVERVIEW)
                    .isEnable(true)
                    .build(),
                DashboardWidgetConfigItem.builder()
                    .key(DashboardWidgetKey.CAMPAIGN_OVERVIEW)
                    .isEnable(false) // last wins and moves to end
                    .build(),
                null,
                DashboardWidgetConfigItem.builder().key(null).isEnable(true).build()));

    when(userDashboardConfigRepository.findByUserIdAndCompanyId("user123", "company123"))
        .thenReturn(Optional.empty());
    when(userDashboardConfigRepository.save(any(UserDashboardConfig.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    List<DashboardWidgetConfigItem> result = dashboardService.upsertWidgets(userContext, requested);

    // Then (normalized order: budget-overview, campaign-overview)
    assertThat(result)
        .containsExactly(
            DashboardWidgetConfigItem.builder()
                .key(DashboardWidgetKey.BUDGET_OVERVIEW)
                .isEnable(true)
                .build(),
            DashboardWidgetConfigItem.builder()
                .key(DashboardWidgetKey.CAMPAIGN_OVERVIEW)
                .isEnable(false)
                .build());

    ArgumentCaptor<UserDashboardConfig> saveCaptor =
        ArgumentCaptor.forClass(UserDashboardConfig.class);
    verify(userDashboardConfigRepository).save(saveCaptor.capture());
    UserDashboardConfig saved = saveCaptor.getValue();
    assertThat(saved.getUserId()).isEqualTo("user123");
    assertThat(saved.getCompanyId()).isEqualTo("company123");
    assertThat(saved.getWidgets())
        .extracting(UserDashboardConfig.DashboardWidgetConfig::getKey)
        .containsExactly(DashboardWidgetKey.BUDGET_OVERVIEW, DashboardWidgetKey.CAMPAIGN_OVERVIEW);
  }

  @Test
  void upsertWidgets_WithNullUserContext_ShouldThrowUserContextInvalidException() {
    assertThatThrownBy(() -> dashboardService.upsertWidgets(null, List.of()))
        .isInstanceOf(UserContextInvalidException.class)
        .hasMessageContaining("missing required fields");
    verifyNoInteractions(userDashboardConfigRepository);
  }

  // --- getCampaignPerformanceByTotalCost ---

  @Test
  void
      getCampaignPerformanceByTotalCost_WithValidRequest_ShouldReturnTopCampaignsSortedByTotalCost() {
    // Given
    IamUserContext userContext =
        IamUserContext.builder().id("user1").companyId("company123").locale(Locale.ENGLISH).build();
    when(userService.getIamUserContext()).thenReturn(userContext);

    Campaign campaign1 =
        Campaign.builder()
            .name("Campaign 1")
            .companyId("company123")
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .userId("user1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .build();
    campaign1.setId("c1");
    Campaign campaign2 =
        Campaign.builder()
            .name("Campaign 2")
            .companyId("company123")
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .userId("user1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .build();
    campaign2.setId("c2");
    Page<Campaign> campaignPage = new PageImpl<>(List.of(campaign1, campaign2));
    when(campaignRepository.findCampaignsWithFilters(any(), eq(Pageable.unpaged())))
        .thenReturn(campaignPage);
    Map<String, Double> totals = new HashMap<>();
    totals.put("c1", 500.0);
    totals.put("c2", 1000.0);
    when(campaignService.calculateTotalCostsForDashboard(List.of(campaign1, campaign2)))
        .thenReturn(totals);

    CampaignFilterResponseDTO dto1 =
        CampaignFilterResponseDTO.builder()
            .id("c1")
            .name("Campaign 1")
            .totalCost(500.0)
            .estimatedImpression(1000L)
            .estimatedReach(100L)
            .sov(10.0)
            .build();
    CampaignFilterResponseDTO dto2 =
        CampaignFilterResponseDTO.builder()
            .id("c2")
            .name("Campaign 2")
            .totalCost(1000.0)
            .estimatedImpression(2000L)
            .estimatedReach(200L)
            .sov(20.0)
            .build();
    when(campaignService.convertToCampaignFilterResponseDTO(campaign1)).thenReturn(dto1);
    when(campaignService.convertToCampaignFilterResponseDTO(campaign2)).thenReturn(dto2);

    CampaignSummaryRequestDTO request =
        CampaignSummaryRequestDTO.builder()
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .sortBy("totalCost")
            .sortDir("desc")
            .limit(5)
            .build();

    // When
    List<CampaignFilterResponseDTO> result =
        dashboardService.getCampaignPerformanceByTotalCost(request);

    // Then: top by total cost then secondary sort; dto2 has higher totalCost so first
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getId()).isEqualTo("c2");
    assertThat(result.get(0).getTotalCost()).isEqualTo(1000.0);
    assertThat(result.get(1).getId()).isEqualTo("c1");
    assertThat(result.get(1).getTotalCost()).isEqualTo(500.0);
    verify(campaignRepository).findCampaignsWithFilters(any(), eq(Pageable.unpaged()));
    verify(campaignService, times(2)).convertToCampaignFilterResponseDTO(any(Campaign.class));
  }

  @Test
  void
      getCampaignPerformanceByTotalCost_WithStartDateAfterEndDate_ShouldThrowCampaignDateRangeException() {
    CampaignSummaryRequestDTO request =
        CampaignSummaryRequestDTO.builder()
            .startDate(LocalDate.parse("2026-01-31"))
            .endDate(LocalDate.parse("2026-01-01"))
            .limit(5)
            .build();

    assertThatThrownBy(() -> dashboardService.getCampaignPerformanceByTotalCost(request))
        .isInstanceOf(CampaignDateRangeException.class)
        .hasMessageContaining("Invalid date range");

    verifyNoInteractions(campaignRepository);
    verifyNoInteractions(campaignService);
  }

  @Test
  void getCampaignPerformanceByTotalCost_WithEmptyCampaigns_ShouldReturnEmptyList() {
    when(userService.getIamUserContext())
        .thenReturn(
            IamUserContext.builder().companyId("company123").locale(Locale.ENGLISH).build());
    when(campaignRepository.findCampaignsWithFilters(any(), eq(Pageable.unpaged())))
        .thenReturn(new PageImpl<>(List.of()));

    CampaignSummaryRequestDTO request =
        CampaignSummaryRequestDTO.builder()
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .limit(5)
            .build();

    List<CampaignFilterResponseDTO> result =
        dashboardService.getCampaignPerformanceByTotalCost(request);

    assertThat(result).isEmpty();
    verify(campaignRepository).findCampaignsWithFilters(any(), eq(Pageable.unpaged()));
    verifyNoInteractions(campaignService);
  }

  @Test
  void getCampaignPerformanceByTotalCost_WithNullLimit_ShouldUseDefaultLimit5() {
    when(userService.getIamUserContext())
        .thenReturn(
            IamUserContext.builder().companyId("company123").locale(Locale.ENGLISH).build());
    Campaign campaign =
        Campaign.builder()
            .name("C1")
            .companyId("company123")
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .userId("user1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .build();
    campaign.setId("c1");
    when(campaignRepository.findCampaignsWithFilters(any(), eq(Pageable.unpaged())))
        .thenReturn(new PageImpl<>(List.of(campaign)));
    when(campaignService.calculateTotalCostsForDashboard(List.of(campaign)))
        .thenReturn(Map.of("c1", 100.0));
    CampaignFilterResponseDTO dto =
        CampaignFilterResponseDTO.builder().id("c1").totalCost(100.0).build();
    when(campaignService.convertToCampaignFilterResponseDTO(campaign)).thenReturn(dto);

    CampaignSummaryRequestDTO request =
        CampaignSummaryRequestDTO.builder()
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .sortDir("desc")
            .limit(null)
            .build();

    List<CampaignFilterResponseDTO> result =
        dashboardService.getCampaignPerformanceByTotalCost(request);

    assertThat(result).hasSize(1);
    verify(campaignRepository).findCampaignsWithFilters(any(), eq(Pageable.unpaged()));
  }

  @Test
  void getCampaignPerformanceByTotalCost_WithNullSortDir_ShouldUseDefaultDesc() {
    when(userService.getIamUserContext())
        .thenReturn(
            IamUserContext.builder().companyId("company123").locale(Locale.ENGLISH).build());
    Campaign campaign =
        Campaign.builder()
            .name("C1")
            .companyId("company123")
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .userId("user1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .build();
    campaign.setId("c1");
    when(campaignRepository.findCampaignsWithFilters(any(), eq(Pageable.unpaged())))
        .thenReturn(new PageImpl<>(List.of(campaign)));
    when(campaignService.calculateTotalCostsForDashboard(List.of(campaign)))
        .thenReturn(Map.of("c1", 100.0));
    CampaignFilterResponseDTO dto =
        CampaignFilterResponseDTO.builder().id("c1").totalCost(100.0).build();
    when(campaignService.convertToCampaignFilterResponseDTO(campaign)).thenReturn(dto);

    CampaignSummaryRequestDTO request =
        CampaignSummaryRequestDTO.builder()
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .sortDir(null)
            .limit(5)
            .build();

    List<CampaignFilterResponseDTO> result =
        dashboardService.getCampaignPerformanceByTotalCost(request);

    assertThat(result).hasSize(1);
    verify(campaignRepository).findCampaignsWithFilters(any(), eq(Pageable.unpaged()));
  }

  @Test
  void getCampaignPerformanceByTotalCost_WithLimit2_ShouldReturnOnlyTop2ByTotalCost() {
    IamUserContext userContext =
        IamUserContext.builder().id("user1").companyId("company123").locale(Locale.ENGLISH).build();
    when(userService.getIamUserContext()).thenReturn(userContext);

    Campaign c1 =
        Campaign.builder()
            .name("Campaign 1")
            .companyId("company123")
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .userId("user1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .build();
    c1.setId("c1");
    Campaign c2 =
        Campaign.builder()
            .name("Campaign 2")
            .companyId("company123")
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .userId("user1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .build();
    c2.setId("c2");
    Campaign c3 =
        Campaign.builder()
            .name("Campaign 3")
            .companyId("company123")
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .userId("user1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .build();
    c3.setId("c3");

    CampaignFilterResponseDTO dto1 =
        CampaignFilterResponseDTO.builder().id("c1").totalCost(100.0).build();
    CampaignFilterResponseDTO dto2 =
        CampaignFilterResponseDTO.builder().id("c2").totalCost(500.0).build();
    CampaignFilterResponseDTO dto3 =
        CampaignFilterResponseDTO.builder().id("c3").totalCost(1000.0).build();

    when(campaignRepository.findCampaignsWithFilters(any(), eq(Pageable.unpaged())))
        .thenReturn(new PageImpl<>(List.of(c1, c2, c3)));
    when(campaignService.calculateTotalCostsForDashboard(List.of(c1, c2, c3)))
        .thenReturn(Map.of("c1", 100.0, "c2", 500.0, "c3", 1000.0));
    when(campaignService.convertToCampaignFilterResponseDTO(c1)).thenReturn(dto1);
    when(campaignService.convertToCampaignFilterResponseDTO(c2)).thenReturn(dto2);
    when(campaignService.convertToCampaignFilterResponseDTO(c3)).thenReturn(dto3);

    CampaignSummaryRequestDTO request =
        CampaignSummaryRequestDTO.builder()
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .sortDir("desc")
            .limit(2)
            .build();

    List<CampaignFilterResponseDTO> result =
        dashboardService.getCampaignPerformanceByTotalCost(request);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getId()).isEqualTo("c3");
    assertThat(result.get(0).getTotalCost()).isEqualTo(1000.0);
    assertThat(result.get(1).getId()).isEqualTo("c2");
    assertThat(result.get(1).getTotalCost()).isEqualTo(500.0);
    verify(campaignRepository).findCampaignsWithFilters(any(), eq(Pageable.unpaged()));
    // Service only builds DTOs for the selected top N campaigns (N = limit).
    verify(campaignService, times(2)).convertToCampaignFilterResponseDTO(any(Campaign.class));
  }

  @Test
  void getCampaignPerformanceByTotalCost_WithSortDirAsc_ShouldApplyAscendingSecondarySort() {
    IamUserContext userContext =
        IamUserContext.builder().companyId("company123").locale(Locale.ENGLISH).build();
    when(userService.getIamUserContext()).thenReturn(userContext);

    Campaign campaign1 =
        Campaign.builder()
            .name("A")
            .companyId("company123")
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .userId("user1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .build();
    campaign1.setId("c1");
    Campaign campaign2 =
        Campaign.builder()
            .name("B")
            .companyId("company123")
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .userId("user1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .build();
    campaign2.setId("c2");

    CampaignFilterResponseDTO dto1 =
        CampaignFilterResponseDTO.builder()
            .id("c1")
            .totalCost(500.0)
            .estimatedImpression(2000L)
            .estimatedReach(200L)
            .sov(20.0)
            .build();
    CampaignFilterResponseDTO dto2 =
        CampaignFilterResponseDTO.builder()
            .id("c2")
            .totalCost(500.0)
            .estimatedImpression(1000L)
            .estimatedReach(100L)
            .sov(10.0)
            .build();

    when(campaignRepository.findCampaignsWithFilters(any(), eq(Pageable.unpaged())))
        .thenReturn(new PageImpl<>(List.of(campaign1, campaign2)));
    when(campaignService.calculateTotalCostsForDashboard(List.of(campaign1, campaign2)))
        .thenReturn(Map.of("c1", 500.0, "c2", 500.0));
    when(campaignService.convertToCampaignFilterResponseDTO(campaign1)).thenReturn(dto1);
    when(campaignService.convertToCampaignFilterResponseDTO(campaign2)).thenReturn(dto2);

    CampaignSummaryRequestDTO request =
        CampaignSummaryRequestDTO.builder()
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .sortDir("asc")
            .limit(5)
            .build();

    List<CampaignFilterResponseDTO> result =
        dashboardService.getCampaignPerformanceByTotalCost(request);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getTotalCost()).isEqualTo(500.0);
    assertThat(result.get(1).getTotalCost()).isEqualTo(500.0);
    verify(campaignRepository).findCampaignsWithFilters(any(), eq(Pageable.unpaged()));
  }

  // ========== getBudgetSummaryByDate (reach path + guard branches) ==========

  private Campaign campaignForBudget(String id) {
    Campaign c =
        Campaign.builder()
            .name("C")
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .userId("u1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();
    c.setId(id);
    return c;
  }

  private CampaignInventorySchedules cisForBudget(String campaignId, List<String> scheduleIds) {
    return CampaignInventorySchedules.builder()
        .campaignId(campaignId)
        .mediaOwnerId("mo1")
        .inventoryId("inv1")
        .scheduleIds(scheduleIds)
        .build();
  }

  @Test
  void getBudgetSummaryByDate_WithNullCampaigns_ReturnsEmpty() {
    Map<String, BudgetSummary> result =
        dashboardService.getBudgetSummaryByDate(
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-31"),
            null,
            Map.of("camp1", List.of()),
            "company123",
            PerformanceSummaryType.REACH);
    assertThat(result).isEmpty();
  }

  @Test
  void getBudgetSummaryByDate_WithEmptyCampaignToCisMap_ReturnsEmpty() {
    Map<String, BudgetSummary> result =
        dashboardService.getBudgetSummaryByDate(
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-31"),
            List.of(campaignForBudget("camp1")),
            Map.of(),
            "company123",
            PerformanceSummaryType.REACH);
    assertThat(result).isEmpty();
  }

  @Test
  void getBudgetSummaryByDate_WhenNoScheduleIds_ReturnsEmpty() {
    CampaignInventorySchedules cis = cisForBudget("camp1", null);
    Map<String, BudgetSummary> result =
        dashboardService.getBudgetSummaryByDate(
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-31"),
            List.of(campaignForBudget("camp1")),
            Map.of("camp1", List.of(cis)),
            "company123",
            PerformanceSummaryType.REACH);
    assertThat(result).isEmpty();
  }

  @Test
  void getBudgetSummaryByDate_ReachPath_AggregatesReachPerDateAndSkipsOutOfRangeAndEmptyHours() {
    Campaign campaign = campaignForBudget("camp1");
    CampaignInventorySchedules cis = cisForBudget("camp1", List.of("s1"));

    Map<String, List<Integer>> bookingMatrix = new HashMap<>();
    bookingMatrix.put("2026-01-05", List.of(7, 8)); // 2 hours, in range
    bookingMatrix.put("2026-01-06", List.of(9)); // 1 hour, in range
    bookingMatrix.put("2025-12-31", List.of(7)); // before range -> skipped
    bookingMatrix.put("2026-01-07", List.of()); // empty hours -> skipped
    Schedule schedule =
        Schedule.builder()
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .bookingMatrix(bookingMatrix)
            .reach(100L)
            .impressions(1000L)
            .plannedSot(10.0)
            .build();
    schedule.setId("s1");
    when(scheduleRepository.findAllById(any())).thenReturn(List.of(schedule));

    Map<String, BudgetSummary> result =
        dashboardService.getBudgetSummaryByDate(
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-31"),
            List.of(campaign),
            Map.of("camp1", List.of(cis)),
            "company123",
            PerformanceSummaryType.REACH);

    // perHourReach = 100/10 = 10; perHourImpressions = 1000/10 = 100
    assertThat(result).containsOnlyKeys("2026-01-05", "2026-01-06");
    assertThat(result.get("2026-01-05").getReach()).isEqualTo(20.0);
    assertThat(result.get("2026-01-05").getImpressions()).isEqualTo(200.0);
    assertThat(result.get("2026-01-06").getReach()).isEqualTo(10.0);
    // REACH type -> cost fields not populated
    assertThat(result.get("2026-01-05").getBudget()).isNull();
  }

  @Test
  void getBudgetSummaryByDate_ReachPath_NullPlannedSotDefaultsToOne() {
    Campaign campaign = campaignForBudget("camp1");
    CampaignInventorySchedules cis = cisForBudget("camp1", List.of("s1"));

    Map<String, List<Integer>> bookingMatrix = new HashMap<>();
    bookingMatrix.put("2026-01-05", List.of(7));
    Schedule schedule =
        Schedule.builder()
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .bookingMatrix(bookingMatrix)
            .reach(50L)
            .impressions(null) // null impressions -> treated as 0
            .plannedSot(null) // null -> defaults to 1.0
            .build();
    schedule.setId("s1");
    when(scheduleRepository.findAllById(any())).thenReturn(List.of(schedule));

    Map<String, BudgetSummary> result =
        dashboardService.getBudgetSummaryByDate(
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-31"),
            List.of(campaign),
            Map.of("camp1", List.of(cis)),
            "company123",
            PerformanceSummaryType.REACH);

    // perHourReach = 50/1.0 = 50; 1 hour -> 50
    assertThat(result.get("2026-01-05").getReach()).isEqualTo(50.0);
    assertThat(result.get("2026-01-05").getImpressions()).isEqualTo(0.0);
  }

  @Test
  void getBudgetSummaryByDate_WhenScheduleHasNoBookingMatrix_ProducesNoEntries() {
    Campaign campaign = campaignForBudget("camp1");
    CampaignInventorySchedules cis = cisForBudget("camp1", List.of("s1"));
    Schedule schedule =
        Schedule.builder()
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .bookingMatrix(null)
            .reach(100L)
            .build();
    schedule.setId("s1");
    when(scheduleRepository.findAllById(any())).thenReturn(List.of(schedule));

    Map<String, BudgetSummary> result =
        dashboardService.getBudgetSummaryByDate(
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-31"),
            List.of(campaign),
            Map.of("camp1", List.of(cis)),
            "company123",
            PerformanceSummaryType.REACH);

    assertThat(result).isEmpty();
  }

  @Test
  void getPerformanceSummary_ReachType_WithSchedules_AggregatesReach() {
    String companyId = "company123";
    LocalDate startDate = LocalDate.parse("2026-01-01");
    LocalDate endDate = LocalDate.parse("2026-01-31");
    Campaign campaign = campaignForBudget("camp1");

    when(campaignService.getCampaignsByCompanyOverlappingDateRange(
            companyId, startDate, endDate, null))
        .thenReturn(List.of(campaign));
    when(campaignInventorySchedulesService.findByCampaignIds(List.of("camp1")))
        .thenReturn(List.of(cisForBudget("camp1", List.of("s1"))));

    Map<String, List<Integer>> bookingMatrix = new HashMap<>();
    bookingMatrix.put("2026-01-10", List.of(7, 8));
    Schedule schedule =
        Schedule.builder()
            .startDate(startDate)
            .endDate(endDate)
            .bookingMatrix(bookingMatrix)
            .reach(200L)
            .impressions(2000L)
            .plannedSot(10.0)
            .build();
    schedule.setId("s1");
    when(scheduleRepository.findAllById(any())).thenReturn(List.of(schedule));

    BudgetPerformanceSummaryResponse result =
        dashboardService.getPerformanceSummary(
            companyId, "USD", startDate, endDate, PerformanceSummaryType.REACH, null);

    assertThat(result.getDateWiseSchedulePerDateRate()).containsKey("2026-01-10");
    assertThat(result.getDateWiseSchedulePerDateRate().get("2026-01-10").getReach())
        .isEqualTo(40.0); // (200/10)=20 per hour * 2 hours
  }

  // ========== getBudgetSummaryByDate COST path (§2A) ==========

  private Schedule costSchedule(String id, Map<String, List<Integer>> bookingMatrix) {
    Schedule s =
        Schedule.builder()
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .bookingMatrix(bookingMatrix)
            .adPlays(10L)
            .spotsPerHour(5L)
            .build();
    s.setId(id);
    return s;
  }

  private void stubCostChain(Schedule schedule, Double proposedPrice) {
    when(customFeeService.getActiveCustomFeesContextForCampaigns(anyList()))
        .thenReturn(Map.of("camp1", CustomFeesContext.builder().build()));
    when(scheduleRepository.findAllById(any())).thenReturn(List.of(schedule));
    Inventory inv = new Inventory();
    inv.setId("inv1");
    when(inventoryService.findAllByIds(anyList())).thenReturn(List.of(inv));
    when(campaignInventorySchedulesService.calculateProposedPriceForSchedule(
            any(), any(), any(), any(), any()))
        .thenReturn(proposedPrice);
  }

  @Test
  void getBudgetSummaryByDate_CostPath_WithBudget_UsesBudgetPerDay() {
    Campaign campaign = campaignForBudget("camp1"); // 31 days
    campaign.setBudget(3100.0); // 3100/31 = 100 per day
    CampaignInventorySchedules cis = cisForBudget("camp1", List.of("s1"));
    Map<String, List<Integer>> bm = new HashMap<>();
    bm.put("2026-01-05", List.of(7, 8)); // 2 hours
    stubCostChain(costSchedule("s1", bm), 100.0); // proposedPrice 100 -> spotRate 100/10 = 10

    Map<String, BudgetSummary> result =
        dashboardService.getBudgetSummaryByDate(
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-31"),
            List.of(campaign),
            Map.of("camp1", List.of(cis)),
            "company123",
            PerformanceSummaryType.COST);

    // perDateRate = spotRate(10) * hours(2) * spotsPerHour(5) = 100; budget/day = 100
    BudgetSummary day = result.get("2026-01-05");
    assertThat(day.getCost()).isEqualTo(100.0);
    assertThat(day.getBudget()).isEqualTo(100.0);
    assertThat(day.getRemaining()).isEqualTo(0.0);
  }

  @Test
  void getBudgetSummaryByDate_CostPath_TwoSchedulesSameCampaignSameDate_CreditsBudgetOnce() {
    // Regression test: the campaign's daily budget share is a campaign-level quantity, not a
    // per-schedule one. Two schedules booked on the same date under the same campaign must not
    // double the day's budget contribution — only the (correctly per-schedule) cost should sum.
    Campaign campaign = campaignForBudget("camp1"); // 31 days
    campaign.setBudget(3100.0); // 3100/31 = 100 per day

    Schedule s1 =
        Schedule.builder()
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .bookingMatrix(Map.of("2026-01-05", List.of(7, 8))) // 2 hours
            .adPlays(10L)
            .spotsPerHour(5L)
            .build();
    s1.setId("s1");
    Schedule s2 =
        Schedule.builder()
            .startDate(LocalDate.parse("2026-01-01"))
            .endDate(LocalDate.parse("2026-01-31"))
            .bookingMatrix(Map.of("2026-01-05", List.of(9, 10))) // 2 hours
            .adPlays(10L)
            .spotsPerHour(5L)
            .build();
    s2.setId("s2");

    CampaignInventorySchedules cis = cisForBudget("camp1", List.of("s1", "s2"));

    when(customFeeService.getActiveCustomFeesContextForCampaigns(anyList()))
        .thenReturn(Map.of("camp1", CustomFeesContext.builder().build()));
    when(scheduleRepository.findAllById(any())).thenReturn(List.of(s1, s2));
    Inventory inv = new Inventory();
    inv.setId("inv1");
    when(inventoryService.findAllByIds(anyList())).thenReturn(List.of(inv));
    when(campaignInventorySchedulesService.calculateProposedPriceForSchedule(
            any(), any(), any(), any(), any()))
        .thenReturn(100.0); // proposedPrice 100 -> spotRate 100/10 = 10, same for both schedules

    Map<String, BudgetSummary> result =
        dashboardService.getBudgetSummaryByDate(
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-31"),
            List.of(campaign),
            Map.of("camp1", List.of(cis)),
            "company123",
            PerformanceSummaryType.COST);

    // perDateRate per schedule = spotRate(10) * hours(2) * spotsPerHour(5) = 100, so both
    // schedules' actual cost correctly sums to 200. The budget share (100/day) must be credited
    // only once for this (campaign, date) pair, not once per schedule (200, the pre-fix bug).
    BudgetSummary day = result.get("2026-01-05");
    assertThat(day.getCost()).isEqualTo(200.0);
    assertThat(day.getBudget()).isEqualTo(100.0);
    assertThat(day.getRemaining()).isEqualTo(0.0);
  }

  @Test
  void getBudgetSummaryByDate_CostPath_WithNullBudget_FallsBackToRate() {
    Campaign campaign = campaignForBudget("camp1");
    campaign.setBudget(null);
    CampaignInventorySchedules cis = cisForBudget("camp1", List.of("s1"));
    Map<String, List<Integer>> bm = new HashMap<>();
    bm.put("2026-01-05", List.of(7, 8));
    stubCostChain(costSchedule("s1", bm), 100.0);

    Map<String, BudgetSummary> result =
        dashboardService.getBudgetSummaryByDate(
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-31"),
            List.of(campaign),
            Map.of("camp1", List.of(cis)),
            "company123",
            PerformanceSummaryType.COST);

    // budget null -> perDateBudget = perDateRate = 100
    BudgetSummary day = result.get("2026-01-05");
    assertThat(day.getCost()).isEqualTo(100.0);
    assertThat(day.getBudget()).isEqualTo(100.0);
  }

  @Test
  void getBudgetSummaryByDate_CostPath_WhenProposedPriceNull_SkipsSchedule() {
    Campaign campaign = campaignForBudget("camp1");
    CampaignInventorySchedules cis = cisForBudget("camp1", List.of("s1"));
    Map<String, List<Integer>> bm = new HashMap<>();
    bm.put("2026-01-05", List.of(7, 8));
    stubCostChain(costSchedule("s1", bm), null); // proposedPrice null -> continue

    Map<String, BudgetSummary> result =
        dashboardService.getBudgetSummaryByDate(
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-31"),
            List.of(campaign),
            Map.of("camp1", List.of(cis)),
            "company123",
            PerformanceSummaryType.COST);

    assertThat(result).isEmpty();
  }

  @Test
  void getBudgetSummaryByDate_CostPath_WhenInventoryMissing_SkipsCis() {
    Campaign campaign = campaignForBudget("camp1");
    CampaignInventorySchedules cis = cisForBudget("camp1", List.of("s1"));
    Map<String, List<Integer>> bm = new HashMap<>();
    bm.put("2026-01-05", List.of(7, 8));
    when(customFeeService.getActiveCustomFeesContextForCampaigns(anyList()))
        .thenReturn(Map.of("camp1", CustomFeesContext.builder().build()));
    when(scheduleRepository.findAllById(any())).thenReturn(List.of(costSchedule("s1", bm)));
    // inventoryService returns nothing -> inventory null -> CIS skipped
    when(inventoryService.findAllByIds(anyList())).thenReturn(Collections.emptyList());

    Map<String, BudgetSummary> result =
        dashboardService.getBudgetSummaryByDate(
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-31"),
            List.of(campaign),
            Map.of("camp1", List.of(cis)),
            "company123",
            PerformanceSummaryType.COST);

    assertThat(result).isEmpty();
  }

  // ========== getPerformanceSummary COST branch (§2B) ==========

  @Test
  void getPerformanceSummary_CostType_AggregatesCurrentAndLastPeriodTotals() {
    String companyId = "company123";
    LocalDate startDate = LocalDate.parse("2026-01-01");
    LocalDate endDate = LocalDate.parse("2026-01-31");
    // last period computed as 2025-12-01..2025-12-31
    LocalDate lastStart = LocalDate.parse("2025-12-01");
    LocalDate lastEnd = LocalDate.parse("2025-12-31");

    Campaign current = campaignForBudget("camp1");
    current.setBudget(3100.0);
    current.setStatus(Campaign.Status.COMPLETED); // converted -> conversion rate branch
    when(campaignService.getCampaignsByCompanyOverlappingDateRange(
            companyId, startDate, endDate, null))
        .thenReturn(List.of(current));
    when(campaignService.getCampaignsByCompanyOverlappingDateRange(
            companyId, lastStart, lastEnd, null))
        .thenReturn(List.of());

    when(campaignInventorySchedulesService.findByCampaignIds(List.of("camp1")))
        .thenReturn(List.of(cisForBudget("camp1", List.of("s1"))));

    Map<String, List<Integer>> bm = new HashMap<>();
    bm.put("2026-01-05", List.of(7, 8)); // in current range
    stubCostChain(costSchedule("s1", bm), 100.0);

    BudgetPerformanceSummaryResponse result =
        dashboardService.getPerformanceSummary(
            companyId, "USD", startDate, endDate, PerformanceSummaryType.COST, null);

    assertThat(result.getDateWiseSchedulePerDateRate()).containsKey("2026-01-05");
    assertThat(result.getTotalCost()).isEqualTo(100.0);
    assertThat(result.getLastPeriodTotalCost()).isEqualTo(0.0);
    assertThat(result.getConversionRate()).isEqualTo(100.0); // 1 of 1 converted
  }
}
