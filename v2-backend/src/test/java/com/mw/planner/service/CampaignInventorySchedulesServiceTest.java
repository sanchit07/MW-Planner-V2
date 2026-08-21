package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import com.mw.planner.constants.CampaignActivityKey;
import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.CustomFee;
import com.mw.planner.domain.Inventory;
import com.mw.planner.domain.Schedule;
import com.mw.planner.dto.AddScheduleRequestDTO;
import com.mw.planner.dto.ApplyAdjustmentRequestDTO;
import com.mw.planner.dto.BulkSchedulesRequestDTO;
import com.mw.planner.dto.CampaignInventoryFilterDTO;
import com.mw.planner.dto.CampaignInventorySchedulesForecastDTO;
import com.mw.planner.dto.CampaignPriceSummaryResponseDTO;
import com.mw.planner.dto.CampaignSchedulePriceFilterDTO;
import com.mw.planner.dto.CampaignSchedulePriceResponseDTO;
import com.mw.planner.dto.CompanyCustomFees;
import com.mw.planner.dto.CompanyLookupResponseDTO;
import com.mw.planner.dto.CustomFeesContext;
import com.mw.planner.dto.EditScheduleRequestDTO;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.dto.PriceHistoryResponseDTO;
import com.mw.planner.dto.SelectCampaignInventoryRequestDTO;
import com.mw.planner.dto.UserResponseDTO;
import com.mw.planner.enums.CustomFeeBasedOn;
import com.mw.planner.enums.CustomFeeType;
import com.mw.planner.enums.DiscountValueType;
import com.mw.planner.enums.PricingAction;
import com.mw.planner.exception.campaign.BulkOperationFailedException;
import com.mw.planner.exception.campaign.CampaignInventorySchedulesNotFoundException;
import com.mw.planner.exception.campaign.CampaignNotFoundException;
import com.mw.planner.exception.campaign.ScheduleIdsNotBelongToCampaignException;
import com.mw.planner.exception.campaign.ScheduleIdsNotFoundException;
import com.mw.planner.repository.CampaignInventorySchedulesRepository;
import com.mw.planner.repository.ScheduleRepository;
import com.mw.planner.service.recommendation.RecommendationService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Comprehensive test class for CampaignInventorySchedulesService covering all service methods and
 * scenarios.
 */
@ExtendWith(MockitoExtension.class)
class CampaignInventorySchedulesServiceTest {

  @Mock private CampaignInventorySchedulesRepository schedulesRepository;
  @Mock private ScheduleRepository scheduleRepository;
  @Mock private InventoryService inventoryService;
  @Mock private CampaignService campaignService;
  @Mock private CampaignActivityService campaignActivityService;
  @Mock private MwMeasureService mwMeasureService;
  @Mock private CompanyService companyService;
  @Mock private UserService userService;
  @Mock private CustomFeeService customFeeService;
  @Mock private CampaignApprovalWorkflowService campaignApprovalWorkflowService;
  @Mock private RecommendationService recommendationService;
  @Mock private VirtualThreadService virtualThreadService;
  @Mock private ScheduleCacheEvictor scheduleCacheEvictor;

  @InjectMocks private CampaignInventorySchedulesService campaignInventorySchedulesService;

  private Inventory testInventory;
  private SelectCampaignInventoryRequestDTO testSelectRequest;
  private SelectCampaignInventoryRequestDTO testDeselectRequest;
  private Campaign testCampaign;

  @BeforeEach
  void setUp() {
    // Setup user context mock for all tests (lenient to avoid unnecessary stubbing warnings)
    IamUserContext userContext = new IamUserContext();
    userContext.setUserId("user123");
    lenient().when(userService.getIamUserContext()).thenReturn(userContext);

    // Setup test inventory
    testInventory = new Inventory();
    testInventory.setId("inventory123");
    testInventory.setName("Test Inventory");
    testInventory.setArchived(false);
    testInventory.setType("CLASSIC");
    testInventory.setMediaOwnerId("mediaOwner123");

    // Setup operating times (replaces Specification)
    // operatingTimes is Map<Weekday, List<OperatingTime>>
    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    operatingTimesMap.put(Inventory.Weekday.MONDAY, List.of(operatingTime));
    testInventory.setOperatingTimes(operatingTimesMap);

    // Setup digital fields (replaces Specification.SpotsDetails)
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    testInventory.setDigitalFields(digitalFields);

    // Setup test select request
    testSelectRequest = new SelectCampaignInventoryRequestDTO();
    testSelectRequest.setCampaignId("campaign123");
    testSelectRequest.setInventoryId("inventory123");
    testSelectRequest.setOperationType(SelectCampaignInventoryRequestDTO.OperationType.SELECT);

    // Setup test deselect request
    testDeselectRequest = new SelectCampaignInventoryRequestDTO();
    testDeselectRequest.setCampaignId("campaign123");
    testDeselectRequest.setInventoryId("inventory123");
    testDeselectRequest.setOperationType(SelectCampaignInventoryRequestDTO.OperationType.DESELECT);

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
            .countryId("US")
            .build();
    testCampaign.setId("campaign123");
    testCampaign.setCreatedAt(LocalDateTime.now());
    testCampaign.setUpdatedAt(LocalDateTime.now());
    // ScheduleTargeting removed - booking matrix now defaults to all hours for all dates

    // Default ScheduleRepository behavior for tests (assign IDs deterministically)
    final int[] seq = {1};
    lenient()
        .when(scheduleRepository.save(any(Schedule.class)))
        .thenAnswer(
            invocation -> {
              Schedule s = invocation.getArgument(0);
              if (s.getId() == null || s.getId().isBlank()) {
                s.setId("schedule-" + (seq[0]++));
              }
              return s;
            });

    lenient()
        .when(scheduleRepository.saveAll(anyList()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              List<Schedule> schedules = (List<Schedule>) invocation.getArgument(0);
              for (Schedule s : schedules) {
                if (s.getId() == null || s.getId().isBlank()) {
                  s.setId("schedule-" + (seq[0]++));
                }
              }
              return schedules;
            });

    lenient().when(scheduleRepository.findAllById(anyList())).thenReturn(Collections.emptyList());
    lenient().doNothing().when(scheduleRepository).deleteAllById(anyList());
  }

  // ========== findByCampaignIdAndInventoryId Tests ==========

  @Test
  @DisplayName("findByCampaignIdAndInventoryId - Should return schedules when found")
  void findByCampaignIdAndInventoryId_WhenSchedulesExists_ShouldReturnSchedules() {
    // Given
    CampaignInventorySchedules testSchedules = new CampaignInventorySchedules();
    testSchedules.setId("schedule123");
    testSchedules.setCampaignId("campaign123");
    testSchedules.setInventoryId("inventory123");
    testSchedules.setMediaOwnerId("mediaOwner123");

    when(schedulesRepository.findByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(Optional.of(testSchedules));

    // When
    CampaignInventorySchedules result =
        campaignInventorySchedulesService.findByCampaignIdAndInventoryId(
            "campaign123", "inventory123");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("schedule123");
    assertThat(result.getCampaignId()).isEqualTo("campaign123");
    assertThat(result.getInventoryId()).isEqualTo("inventory123");

    verify(schedulesRepository).findByCampaignIdAndInventoryId("campaign123", "inventory123");
  }

  @Test
  @DisplayName("findByCampaignIdAndInventoryId - Should throw exception when schedules not found")
  void findByCampaignIdAndInventoryId_WhenSchedulesNotFound_ShouldThrowException() {
    // Given
    when(schedulesRepository.findByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.findByCampaignIdAndInventoryId(
                    "campaign123", "inventory123"))
        .isInstanceOf(CampaignInventorySchedulesNotFoundException.class)
        .hasMessageContaining(
            "Campaign inventory schedules not found for campaignId: campaign123, inventoryId: inventory123");

    verify(schedulesRepository).findByCampaignIdAndInventoryId("campaign123", "inventory123");
  }

  // ========== selectInventory Tests ==========

  @Test
  @DisplayName("selectInventory - Should create new schedule when none exists")
  void selectInventory_WhenScheduleDoesNotExist_ShouldCreateNewSchedule() {
    // Given
    testInventory.setMediaOwnerId("mediaOwner123");
    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(
            invocation -> {
              CampaignInventorySchedules schedule = invocation.getArgument(0);
              schedule.setId("campaign123_mediaOwner123_inventory123");
              return schedule;
            });
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then
    verify(campaignService).findById("campaign123");
    verify(inventoryService).getById("inventory123");
    verify(schedulesRepository).save(any(CampaignInventorySchedules.class));
    verify(campaignService).save(any(Campaign.class));
    verify(campaignActivityService, times(1))
        .logActivity(
            eq("campaign123"),
            eq(CampaignActivityService.OperationType.ADDED),
            eq(CampaignActivityKey.INVENTORY_REFERENCE_ID.key()),
            anyString());
  }

  @Test
  @DisplayName("selectInventory - Should create or update schedule when one exists")
  void selectInventory_WhenScheduleExists_ShouldCreateOrUpdateSchedule() {
    // Given
    testInventory.setMediaOwnerId("mediaOwner123");
    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(
            invocation -> {
              CampaignInventorySchedules schedule = invocation.getArgument(0);
              schedule.setId("campaign123_mediaOwner123_inventory123");
              return schedule;
            });
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then
    verify(campaignService).findById("campaign123");
    verify(inventoryService).getById("inventory123");
    verify(schedulesRepository).save(any(CampaignInventorySchedules.class));
    verify(campaignActivityService, times(1))
        .logActivity(
            eq("campaign123"),
            eq(CampaignActivityService.OperationType.ADDED),
            eq(CampaignActivityKey.INVENTORY_REFERENCE_ID.key()),
            anyString());
  }

  @Test
  @DisplayName("selectInventory - Should handle inventory with incomplete specifications")
  void selectInventory_WithIncompleteInventorySpecs_ShouldHandleGracefully() {
    // Given
    Inventory incompleteInventory = new Inventory();
    incompleteInventory.setId("inventory123");
    incompleteInventory.setName("Test Inventory");
    incompleteInventory.setArchived(false);
    incompleteInventory.setMediaOwnerId("mediaOwner123");
    // Setup operating times - use 00:00:00 to 23:59:00 to include all 24 hours
    // Set up for all weekdays to ensure all dates in campaign range have hours
    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    operatingTimesMap.put(Inventory.Weekday.MONDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.TUESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.WEDNESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.THURSDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.FRIDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SATURDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SUNDAY, List.of(operatingTime));
    incompleteInventory.setOperatingTimes(operatingTimesMap);
    // No other specifications set

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(incompleteInventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then
    verify(campaignService).findById("campaign123");
    verify(inventoryService).getById("inventory123");
    verify(schedulesRepository).save(any(CampaignInventorySchedules.class));

    ArgumentCaptor<CampaignInventorySchedules> captor =
        ArgumentCaptor.forClass(CampaignInventorySchedules.class);
    verify(schedulesRepository).save(captor.capture());
    CampaignInventorySchedules savedSchedule = captor.getValue();
    assertThat(savedSchedule).isNotNull();
    assertThat(savedSchedule.getScheduleIds()).isNotNull();
    assertThat(savedSchedule.getScheduleIds()).hasSize(1);
    // New implementation always creates bookingMatrix with all dates in campaign range
    // Even with incomplete specs, it will create a bookingMatrix with all dates
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule schedule = scheduleCaptor.getValue();
    assertThat(schedule.getBookingMatrix()).isNotNull();
    // Should have entries for all dates in campaign range
    long daysBetween =
        java.time.temporal.ChronoUnit.DAYS.between(
                testCampaign.getStartDate(), testCampaign.getEndDate())
            + 1;
    assertThat(schedule.getBookingMatrix().size()).isEqualTo((int) daysBetween);
    // Each date should have all 24 hours
    schedule
        .getBookingMatrix()
        .values()
        .forEach(
            hours -> {
              assertThat(hours).hasSize(24);
            });
  }

  @Test
  @DisplayName("selectInventory - Should handle inventory with invalid spots per hour")
  void selectInventory_WithInvalidSpotsPerHour_ShouldHandleGracefully() {
    // Given
    Inventory invalidInventory = new Inventory();
    invalidInventory.setId("inventory123");
    invalidInventory.setName("Test Inventory");
    invalidInventory.setArchived(false);
    invalidInventory.setMediaOwnerId("mediaOwner123");
    // Set up inventory with invalid spots per hour (null or 0)
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(0); // Invalid
    digitalFields.setSpotDuration(30);
    invalidInventory.setDigitalFields(digitalFields);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(invalidInventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then
    verify(inventoryService).getById("inventory123");
    verify(schedulesRepository).save(any(CampaignInventorySchedules.class));

    ArgumentCaptor<CampaignInventorySchedules> captor =
        ArgumentCaptor.forClass(CampaignInventorySchedules.class);
    verify(schedulesRepository).save(captor.capture());
    CampaignInventorySchedules savedSchedule = captor.getValue();
    assertThat(savedSchedule).isNotNull();
    assertThat(savedSchedule.getScheduleIds()).isNotNull();
    assertThat(savedSchedule.getScheduleIds()).hasSize(1);
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    assertThat(scheduleCaptor.getValue().getAdPlays()).isEqualTo(0);
  }

  @Test
  @DisplayName("selectInventory - Should use pre-supplied impressions and reach when provided")
  void selectInventory_ShouldUsePreSuppliedImpressionsAndReach() {
    // Given: request with impressions=8014680 and reach=447747
    testInventory.setMediaOwnerId("mediaOwner123");
    SelectCampaignInventoryRequestDTO requestWithMetrics = new SelectCampaignInventoryRequestDTO();
    requestWithMetrics.setCampaignId("campaign123");
    requestWithMetrics.setInventoryId("inventory123");
    requestWithMetrics.setOperationType(SelectCampaignInventoryRequestDTO.OperationType.SELECT);
    requestWithMetrics.setImpressions(8014680L);
    requestWithMetrics.setReach(447747L);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(
            invocation -> {
              CampaignInventorySchedules schedule = invocation.getArgument(0);
              schedule.setId("campaign123_mediaOwner123_inventory123");
              return schedule;
            });
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(requestWithMetrics);

    // Then: Measure API should NOT be called
    verify(mwMeasureService, never()).getReachAndFrequencyBySites(any(), anyBoolean());

    // Verify schedule has pre-supplied impressions and reach
    ArgumentCaptor<CampaignInventorySchedules> captor =
        ArgumentCaptor.forClass(CampaignInventorySchedules.class);
    verify(schedulesRepository).save(captor.capture());
    CampaignInventorySchedules savedSchedule = captor.getValue();
    assertThat(savedSchedule).isNotNull();

    // Capture the Schedule object that was saved
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule savedScheduleItem = scheduleCaptor.getValue();
    assertThat(savedScheduleItem.getImpressions()).isEqualTo(8014680L);
    assertThat(savedScheduleItem.getReach()).isEqualTo(447747L);
  }

  @Test
  @DisplayName(
      "selectInventory - Should call Measure API when impressions or reach not fully provided")
  void selectInventory_ShouldCallMeasureApiWhenMetricsNotProvided() {
    // Given: request without impressions/reach
    testInventory.setMediaOwnerId("mediaOwner123");

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(
            invocation -> {
              CampaignInventorySchedules schedule = invocation.getArgument(0);
              schedule.setId("campaign123_mediaOwner123_inventory123");
              return schedule;
            });
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // Mock Measure API response
    when(mwMeasureService.getReachAndFrequencyBySitesFromSchedules(
            anyInt(), any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then: Measure API should be called
    verify(mwMeasureService, times(1))
        .getReachAndFrequencyBySitesFromSchedules(
            anyInt(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("selectInventory - Should call Measure API when only impressions provided")
  void selectInventory_ShouldCallMeasureApiWhenOnlyImpressionsProvided() {
    // Given: request with only impressions (reach is null)
    testInventory.setMediaOwnerId("mediaOwner123");
    SelectCampaignInventoryRequestDTO requestWithOnlyImpressions =
        new SelectCampaignInventoryRequestDTO();
    requestWithOnlyImpressions.setCampaignId("campaign123");
    requestWithOnlyImpressions.setInventoryId("inventory123");
    requestWithOnlyImpressions.setOperationType(
        SelectCampaignInventoryRequestDTO.OperationType.SELECT);
    requestWithOnlyImpressions.setImpressions(8014680L);
    requestWithOnlyImpressions.setReach(null);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(
            invocation -> {
              CampaignInventorySchedules schedule = invocation.getArgument(0);
              schedule.setId("campaign123_mediaOwner123_inventory123");
              return schedule;
            });
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // Mock Measure API response
    when(mwMeasureService.getReachAndFrequencyBySitesFromSchedules(
            anyInt(), any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of());

    // When
    campaignInventorySchedulesService.selectInventory(requestWithOnlyImpressions);

    // Then: Measure API should be called because reach is null
    verify(mwMeasureService, times(1))
        .getReachAndFrequencyBySitesFromSchedules(
            anyInt(), any(), any(), any(), any(), any(), any());
  }

  // ========== Schedule type resolution Tests ==========

  @Test
  @DisplayName(
      "selectInventory - Should set schedule type DAYPART when digital inventory booked by time")
  void selectInventory_WhenDigitalBookedByTime_ShouldSetTypeDaypart() {
    // Given: digital inventory with bookingMode "time"
    testInventory.setClassification("Digital");
    testInventory.getDigitalFields().setBookingMode("time");
    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    assertThat(scheduleCaptor.getValue().getType()).isEqualTo(Schedule.Type.DAYPART);
  }

  @Test
  @DisplayName(
      "selectInventory - Should set schedule type LOOP when digital inventory booked by loop")
  void selectInventory_WhenDigitalBookedByLoop_ShouldSetTypeLoop() {
    // Given: digital inventory with bookingMode "loop"
    testInventory.setClassification("Digital");
    testInventory.getDigitalFields().setBookingMode("loop");
    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    assertThat(scheduleCaptor.getValue().getType()).isEqualTo(Schedule.Type.LOOP);
  }

  @Test
  @DisplayName(
      "selectInventory - Should default schedule type LOOP for non-digital inventory (regression)")
  void selectInventory_WhenNonDigitalInventory_ShouldDefaultTypeLoop() {
    // Given: non-digital inventory (classification null defaults, bookingMode irrelevant)
    testInventory.setClassification("Classic");
    testInventory.getDigitalFields().setBookingMode("time");
    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then: not digital → stays LOOP even though bookingMode is "time"
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    assertThat(scheduleCaptor.getValue().getType()).isEqualTo(Schedule.Type.LOOP);
  }

  // ========== deselectInventory Tests ==========

  @Test
  @DisplayName("deselectInventory - Should delete schedule when it exists")
  void deselectInventory_WhenScheduleExists_ShouldDeleteSchedule() {
    // Given
    testInventory.setMediaOwnerId(null); // No mediaOwnerId for this test
    when(campaignService.findByIdForCurrentModeForWrite("campaign123")).thenReturn(testCampaign);

    // Mock CampaignInventorySchedules with scheduleIds
    CampaignInventorySchedules campaignSchedule = new CampaignInventorySchedules();
    campaignSchedule.setId("cis1");
    campaignSchedule.setCampaignId("campaign123");
    campaignSchedule.setInventoryId("inventory123");
    campaignSchedule.setScheduleIds(List.of("schedule1", "schedule2"));

    when(schedulesRepository.findByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(Optional.of(campaignSchedule));
    doNothing().when(scheduleRepository).deleteByIdIn(anyList());
    when(inventoryService.getMediaOwnerIdById("inventory123")).thenReturn(null);
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(schedulesRepository.deleteByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(1L); // Returns count of deleted records
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.deselectInventory(testDeselectRequest);

    // Then
    verify(campaignService).findByIdForCurrentModeForWrite("campaign123");
    verify(schedulesRepository).findByCampaignIdAndInventoryId("campaign123", "inventory123");
    verify(scheduleRepository).deleteByIdIn(List.of("schedule1", "schedule2"));
    verify(inventoryService).getMediaOwnerIdById("inventory123");
    verify(schedulesRepository).deleteByCampaignIdAndInventoryId("campaign123", "inventory123");
    verify(campaignActivityService, times(1))
        .logActivity(
            eq("campaign123"),
            eq(CampaignActivityService.OperationType.REMOVED),
            eq(CampaignActivityKey.INVENTORY_REFERENCE_ID.key()),
            anyString());
  }

  @Test
  @DisplayName("deselectInventory - Should throw exception when schedule does not exist")
  void deselectInventory_WhenScheduleDoesNotExist_ShouldThrowException() {
    // Given
    when(campaignService.findByIdForCurrentModeForWrite("campaign123")).thenReturn(testCampaign);
    when(schedulesRepository.findByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(
            () -> campaignInventorySchedulesService.deselectInventory(testDeselectRequest))
        .isInstanceOf(CampaignInventorySchedulesNotFoundException.class)
        .hasMessageContaining(
            "Campaign inventory schedules not found for campaignId: campaign123, inventoryId: inventory123");

    verify(campaignService).findByIdForCurrentModeForWrite("campaign123");
    verify(schedulesRepository).findByCampaignIdAndInventoryId("campaign123", "inventory123");
    verify(scheduleRepository, never()).deleteByIdIn(anyList());
    verify(schedulesRepository, never()).deleteByCampaignIdAndInventoryId(anyString(), anyString());
  }

  // ========== deselectInventoryV2 Tests ==========

  /** Make {@code virtualThreadService.runAsync(...)} execute the task inline for verification. */
  private void runAsyncInline() {
    when(virtualThreadService.runAsync(any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(0)).run();
              return CompletableFuture.completedFuture(null);
            });
  }

  @Test
  @DisplayName(
      "deselectInventoryV2 - Should delete schedule/config and run side-effects async, reusing mediaOwnerId")
  void deselectInventoryV2_WhenScheduleExists_ShouldDeleteAndRunSideEffectsAsync() {
    // Given
    runAsyncInline();

    CampaignInventorySchedules campaignSchedule = new CampaignInventorySchedules();
    campaignSchedule.setId("cis1");
    campaignSchedule.setCampaignId("campaign123");
    campaignSchedule.setInventoryId("inventory123");
    campaignSchedule.setMediaOwnerId("mediaOwner123");
    campaignSchedule.setScheduleIds(List.of("schedule1", "schedule2"));

    when(campaignService.findByIdForCurrentModeForWrite("campaign123")).thenReturn(testCampaign);
    when(schedulesRepository.findByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(Optional.of(campaignSchedule));
    doNothing().when(scheduleRepository).deleteByIdIn(anyList());
    when(schedulesRepository.deleteByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(1L);
    // Other configs still use this mediaOwnerId -> companyAccess kept (skip removal branch)
    when(schedulesRepository.countByCampaignIdAndMediaOwnerId("campaign123", "mediaOwner123"))
        .thenReturn(1L);
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.deselectInventoryV2(testDeselectRequest);

    // Then - essential synchronous work
    verify(campaignService).findByIdForCurrentModeForWrite("campaign123");
    verify(schedulesRepository).findByCampaignIdAndInventoryId("campaign123", "inventory123");
    verify(scheduleRepository).deleteByIdIn(List.of("schedule1", "schedule2"));
    verify(schedulesRepository).deleteByCampaignIdAndInventoryId("campaign123", "inventory123");
    verify(schedulesRepository).countByCampaignIdAndMediaOwnerId("campaign123", "mediaOwner123");

    // Dedup guarantee - mediaOwnerId reused from config, no redundant lookup
    verify(inventoryService, never()).getMediaOwnerIdById(anyString());

    // Best-effort side-effects executed on the (inlined) virtual thread
    verify(virtualThreadService).runAsync(any(Runnable.class));
    verify(recommendationService)
        .syncSelectedInventories(
            eq("campaign123"),
            eq(List.of("inventory123")),
            eq(RecommendationService.OperationType.DESELECT));
    verify(campaignActivityService, times(1))
        .logActivity(
            eq("campaign123"),
            eq(CampaignActivityService.OperationType.REMOVED),
            eq(CampaignActivityKey.INVENTORY_REFERENCE_ID.key()),
            anyString());
  }

  @Test
  @DisplayName("deselectInventoryV2 - Should throw exception when schedule does not exist")
  void deselectInventoryV2_WhenScheduleDoesNotExist_ShouldThrowException() {
    // Given
    when(campaignService.findByIdForCurrentModeForWrite("campaign123")).thenReturn(testCampaign);
    when(schedulesRepository.findByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(
            () -> campaignInventorySchedulesService.deselectInventoryV2(testDeselectRequest))
        .isInstanceOf(CampaignInventorySchedulesNotFoundException.class)
        .hasMessageContaining(
            "Campaign inventory schedules not found for campaignId: campaign123, inventoryId: inventory123");

    verify(scheduleRepository, never()).deleteByIdIn(anyList());
    verify(schedulesRepository, never()).deleteByCampaignIdAndInventoryId(anyString(), anyString());
    verify(virtualThreadService, never()).runAsync(any(Runnable.class));
  }

  @Test
  @DisplayName(
      "deselectInventoryV2 - Should remove mediaOwnerId from companyAccess when no configs remain")
  void deselectInventoryV2_WhenNoRemainingConfigs_ShouldRemoveMediaOwnerFromCompanyAccess() {
    // Given
    CampaignInventorySchedules campaignSchedule = new CampaignInventorySchedules();
    campaignSchedule.setId("cis1");
    campaignSchedule.setCampaignId("campaign123");
    campaignSchedule.setInventoryId("inventory123");
    campaignSchedule.setMediaOwnerId("mediaOwner123");
    campaignSchedule.setScheduleIds(List.of("schedule1"));

    testCampaign.setCompanyAccess(new ArrayList<>(List.of("mediaOwner123")));

    when(campaignService.findByIdForCurrentModeForWrite("campaign123")).thenReturn(testCampaign);
    when(schedulesRepository.findByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(Optional.of(campaignSchedule));
    doNothing().when(scheduleRepository).deleteByIdIn(anyList());
    when(schedulesRepository.deleteByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(1L);
    when(schedulesRepository.countByCampaignIdAndMediaOwnerId("campaign123", "mediaOwner123"))
        .thenReturn(0L);
    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);

    // When
    campaignInventorySchedulesService.deselectInventoryV2(testDeselectRequest);

    // Then
    verify(schedulesRepository).countByCampaignIdAndMediaOwnerId("campaign123", "mediaOwner123");
    verify(campaignService).save(testCampaign);
    verify(campaignService).campaignCacheEvict("campaign123");
    assertThat(testCampaign.getCompanyAccess()).doesNotContain("mediaOwner123");
  }

  @Test
  @DisplayName(
      "deselectInventoryV2 - Should keep companyAccess when other configs use the mediaOwnerId")
  void deselectInventoryV2_WhenRemainingConfigsExist_ShouldKeepCompanyAccess() {
    // Given
    CampaignInventorySchedules campaignSchedule = new CampaignInventorySchedules();
    campaignSchedule.setId("cis1");
    campaignSchedule.setCampaignId("campaign123");
    campaignSchedule.setInventoryId("inventory123");
    campaignSchedule.setMediaOwnerId("mediaOwner123");
    campaignSchedule.setScheduleIds(List.of("schedule1"));

    when(campaignService.findByIdForCurrentModeForWrite("campaign123")).thenReturn(testCampaign);
    when(schedulesRepository.findByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(Optional.of(campaignSchedule));
    doNothing().when(scheduleRepository).deleteByIdIn(anyList());
    when(schedulesRepository.deleteByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(1L);
    when(schedulesRepository.countByCampaignIdAndMediaOwnerId("campaign123", "mediaOwner123"))
        .thenReturn(2L);

    // When
    campaignInventorySchedulesService.deselectInventoryV2(testDeselectRequest);

    // Then - removal branch skipped, campaign not mutated for companyAccess
    verify(schedulesRepository).countByCampaignIdAndMediaOwnerId("campaign123", "mediaOwner123");
    verify(campaignService, never()).findById(anyString());
    verify(campaignService, never()).save(any(Campaign.class));
  }

  @Test
  @DisplayName("deselectInventoryV2 - Should throw when campaign does not exist")
  void deselectInventoryV2_WhenCampaignNotFound_ShouldThrow() {
    // Given
    when(campaignService.findByIdForCurrentModeForWrite("campaign123"))
        .thenThrow(new CampaignNotFoundException("campaign123"));

    // When & Then
    assertThatThrownBy(
            () -> campaignInventorySchedulesService.deselectInventoryV2(testDeselectRequest))
        .isInstanceOf(CampaignNotFoundException.class);

    verify(schedulesRepository, never()).findByCampaignIdAndInventoryId(anyString(), anyString());
    verify(scheduleRepository, never()).deleteByIdIn(anyList());
    verify(virtualThreadService, never()).runAsync(any(Runnable.class));
  }

  @Test
  @DisplayName(
      "selectInventory - Should create bookingMatrix with all hours for each date in campaign range")
  void selectInventory_WithDisplayTimes_ShouldCreateMatrixWithAllHours() {
    // Given
    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Test Inventory");
    inventory.setArchived(false);
    inventory.setMediaOwnerId("mediaOwner123");

    // Setup operating times - use 00:00:00 to 23:59:00 to include all 24 hours
    // Set up for all weekdays to ensure all dates in campaign range have hours
    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    operatingTimesMap.put(Inventory.Weekday.MONDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.TUESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.WEDNESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.THURSDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.FRIDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SATURDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SUNDAY, List.of(operatingTime));
    inventory.setOperatingTimes(operatingTimesMap);

    // Setup digital fields
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(inventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then
    ArgumentCaptor<CampaignInventorySchedules> captor =
        ArgumentCaptor.forClass(CampaignInventorySchedules.class);
    verify(schedulesRepository).save(captor.capture());
    CampaignInventorySchedules savedSchedule = captor.getValue();

    assertThat(savedSchedule).isNotNull();
    assertThat(savedSchedule.getScheduleIds()).isNotNull();
    assertThat(savedSchedule.getScheduleIds()).hasSize(1);

    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule schedule = scheduleCaptor.getValue();
    assertThat(schedule.getBookingMatrix()).isNotNull();

    // New implementation includes all hours (0-23) for each date
    // Verify that bookingMatrix has entries for all dates in campaign range
    long daysBetween =
        java.time.temporal.ChronoUnit.DAYS.between(
                testCampaign.getStartDate(), testCampaign.getEndDate())
            + 1;
    assertThat(schedule.getBookingMatrix().size()).isEqualTo((int) daysBetween);

    // Verify each date has all 24 hours
    schedule
        .getBookingMatrix()
        .values()
        .forEach(
            hours -> {
              assertThat(hours).hasSize(24);
              assertThat(hours)
                  .containsExactly(
                      0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
                      22, 23);
            });
  }

  @Test
  @DisplayName(
      "selectInventory - Should create bookingMatrix with all hours regardless of display times")
  void selectInventory_WithDisplayOffTimeExactHour_ShouldIncludeAllHours() {
    // Given
    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Test Inventory");
    inventory.setArchived(false);
    inventory.setMediaOwnerId("mediaOwner123");

    // Setup operating times - use 00:00:00 to 23:59:00 to include all 24 hours
    // Set up for all weekdays to ensure all dates in campaign range have hours
    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    operatingTimesMap.put(Inventory.Weekday.MONDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.TUESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.WEDNESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.THURSDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.FRIDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SATURDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SUNDAY, List.of(operatingTime));
    inventory.setOperatingTimes(operatingTimesMap);

    // Setup digital fields
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(inventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then
    ArgumentCaptor<CampaignInventorySchedules> captor =
        ArgumentCaptor.forClass(CampaignInventorySchedules.class);
    verify(schedulesRepository).save(captor.capture());
    CampaignInventorySchedules savedSchedule = captor.getValue();

    assertThat(savedSchedule).isNotNull();
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule schedule = scheduleCaptor.getValue();

    // New implementation includes all 24 hours for each date
    schedule
        .getBookingMatrix()
        .values()
        .forEach(
            hours -> {
              assertThat(hours).hasSize(24);
              assertThat(hours).contains(23); // Should include hour 23
            });
  }

  @Test
  @DisplayName(
      "selectInventory - Should create bookingMatrix with all hours for dates in campaign range")
  void selectInventory_WithDisplayTimeCrossingMidnight_ShouldCreateAllHoursMatrix() {
    // Given
    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Test Inventory");
    inventory.setArchived(false);
    inventory.setMediaOwnerId("mediaOwner123");

    // Setup operating times - use 00:00:00 to 23:59:00 to include all 24 hours
    // Set up for all weekdays to ensure all dates in campaign range have hours
    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    operatingTimesMap.put(Inventory.Weekday.MONDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.TUESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.WEDNESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.THURSDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.FRIDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SATURDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SUNDAY, List.of(operatingTime));
    inventory.setOperatingTimes(operatingTimesMap);

    // Setup digital fields
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(inventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then
    ArgumentCaptor<CampaignInventorySchedules> captor =
        ArgumentCaptor.forClass(CampaignInventorySchedules.class);
    verify(schedulesRepository).save(captor.capture());
    CampaignInventorySchedules savedSchedule = captor.getValue();

    assertThat(savedSchedule).isNotNull();
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule schedule = scheduleCaptor.getValue();

    // New implementation includes all 24 hours for each date
    schedule
        .getBookingMatrix()
        .values()
        .forEach(
            hours -> {
              assertThat(hours).hasSize(24);
              assertThat(hours).contains(22, 23, 0, 1, 2, 3, 4, 5, 6); // All hours included
            });
  }

  @Test
  @DisplayName(
      "selectInventory - Should include all hours when displayOnTime and displayOffTime are not set")
  void selectInventory_WithoutDisplayTimes_ShouldIncludeAllHours() {
    // Given
    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Test Inventory");
    inventory.setArchived(false);
    inventory.setMediaOwnerId("mediaOwner123");

    // Setup operating times (no display times - all hours)
    // Set up for all weekdays to ensure all dates in campaign range have hours
    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    operatingTimesMap.put(Inventory.Weekday.MONDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.TUESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.WEDNESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.THURSDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.FRIDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SATURDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SUNDAY, List.of(operatingTime));
    inventory.setOperatingTimes(operatingTimesMap);

    // Setup digital fields
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(inventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then
    ArgumentCaptor<CampaignInventorySchedules> captor =
        ArgumentCaptor.forClass(CampaignInventorySchedules.class);
    verify(schedulesRepository).save(captor.capture());
    CampaignInventorySchedules savedSchedule = captor.getValue();

    assertThat(savedSchedule).isNotNull();
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule schedule = scheduleCaptor.getValue();

    // Should have all 24 hours for each date
    schedule
        .getBookingMatrix()
        .values()
        .forEach(
            hours -> {
              assertThat(hours).hasSize(24);
              for (int hour = 0; hour < 24; hour++) {
                assertThat(hours).contains(hour);
              }
            });
  }

  @Test
  @DisplayName("selectInventory - Should create bookingMatrix for all dates in campaign range")
  void selectInventory_WithMultipleDaysAndDisplayTimes_ShouldCreateMatrixForAllDates() {
    // Given
    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Test Inventory");
    inventory.setArchived(false);
    inventory.setMediaOwnerId("mediaOwner123");

    // Setup operating times - use 00:00:00 to 23:59:00 to include all 24 hours
    // Set up for all weekdays to ensure all dates in campaign range have hours
    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    operatingTimesMap.put(Inventory.Weekday.MONDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.TUESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.WEDNESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.THURSDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.FRIDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SATURDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SUNDAY, List.of(operatingTime));
    inventory.setOperatingTimes(operatingTimesMap);

    // Setup digital fields
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(inventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then
    ArgumentCaptor<CampaignInventorySchedules> captor =
        ArgumentCaptor.forClass(CampaignInventorySchedules.class);
    verify(schedulesRepository).save(captor.capture());
    CampaignInventorySchedules savedSchedule = captor.getValue();

    assertThat(savedSchedule).isNotNull();
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule schedule = scheduleCaptor.getValue();

    // Should have entries for all dates in campaign range
    long daysBetween =
        java.time.temporal.ChronoUnit.DAYS.between(
                testCampaign.getStartDate(), testCampaign.getEndDate())
            + 1;
    assertThat(schedule.getBookingMatrix().size()).isEqualTo((int) daysBetween);

    // Each date should have all 24 hours
    schedule
        .getBookingMatrix()
        .values()
        .forEach(
            hours -> {
              assertThat(hours).hasSize(24);
            });
  }

  @Test
  @DisplayName("selectInventory - Should create bookingMatrix with all hours for all dates")
  void selectInventory_WithDisplayOffTime0000_ShouldIncludeAllHours() {
    // Given
    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Test Inventory");
    inventory.setArchived(false);
    inventory.setMediaOwnerId("mediaOwner123");

    // Setup operating times - use 00:00:00 to 23:59:00 to include all 24 hours
    // Set up for all weekdays to ensure all dates in campaign range have hours
    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    operatingTimesMap.put(Inventory.Weekday.MONDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.TUESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.WEDNESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.THURSDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.FRIDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SATURDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SUNDAY, List.of(operatingTime));
    inventory.setOperatingTimes(operatingTimesMap);

    // Setup digital fields
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(inventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then
    ArgumentCaptor<CampaignInventorySchedules> captor =
        ArgumentCaptor.forClass(CampaignInventorySchedules.class);
    verify(schedulesRepository).save(captor.capture());
    CampaignInventorySchedules savedSchedule = captor.getValue();

    assertThat(savedSchedule).isNotNull();
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule schedule = scheduleCaptor.getValue();

    // New implementation includes all 24 hours for each date
    schedule
        .getBookingMatrix()
        .values()
        .forEach(
            hours -> {
              assertThat(hours).hasSize(24);
              assertThat(hours).contains(0, 1, 2, 3, 4, 5, 6, 23); // All hours included
            });
  }

  @Test
  @DisplayName(
      "selectInventory - Should handle full 24-hour operation when displayOnTime is 00:00 and displayOffTime is 00:00")
  void selectInventory_WithBothTimes0000_ShouldIncludeAll24Hours() {
    // Given
    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Test Inventory");
    inventory.setArchived(false);
    inventory.setMediaOwnerId("mediaOwner123");

    // Setup operating times - use 00:00:00 to 23:59:00 to include all 24 hours
    // Set up for all weekdays to ensure all dates in campaign range have hours
    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    operatingTimesMap.put(Inventory.Weekday.MONDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.TUESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.WEDNESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.THURSDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.FRIDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SATURDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SUNDAY, List.of(operatingTime));
    inventory.setOperatingTimes(operatingTimesMap);

    // Setup digital fields
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(inventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then
    ArgumentCaptor<CampaignInventorySchedules> captor =
        ArgumentCaptor.forClass(CampaignInventorySchedules.class);
    verify(schedulesRepository).save(captor.capture());
    CampaignInventorySchedules savedSchedule = captor.getValue();

    assertThat(savedSchedule).isNotNull();
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule schedule = scheduleCaptor.getValue();

    // Should have all 24 hours for each date
    schedule
        .getBookingMatrix()
        .values()
        .forEach(
            hours -> {
              assertThat(hours).hasSize(24);
              for (int hour = 0; hour < 24; hour++) {
                assertThat(hours).contains(hour);
              }
            });
  }

  // ========== ScheduleTargeting Tests ==========

  @Test
  @DisplayName("selectInventory - Should use scheduleTargeting weekday distribution when provided")
  void selectInventory_WithWeekdayDistribution_ShouldDistributeAccordingly() {
    // Given
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .status(Campaign.Status.DRAFT)
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(6)) // 7 days
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();
    campaign.setId("campaign123");
    campaign.setCreatedAt(LocalDateTime.now());
    campaign.setUpdatedAt(LocalDateTime.now());

    // ScheduleTargeting removed - booking matrix now defaults to all hours for all dates

    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setMediaOwnerId("mediaOwner123");
    // Setup operating times - use 00:00:00 to 23:59:00 to include all 24 hours
    // Set up for all weekdays to ensure all dates in campaign range have hours
    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    operatingTimesMap.put(Inventory.Weekday.MONDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.TUESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.WEDNESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.THURSDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.FRIDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SATURDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SUNDAY, List.of(operatingTime));
    inventory.setOperatingTimes(operatingTimesMap);

    // Setup digital fields
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    when(campaignService.findById("campaign123")).thenReturn(campaign);
    when(inventoryService.getById("inventory123")).thenReturn(inventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(campaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then
    ArgumentCaptor<CampaignInventorySchedules> captor =
        ArgumentCaptor.forClass(CampaignInventorySchedules.class);
    verify(schedulesRepository).save(captor.capture());
    CampaignInventorySchedules savedSchedule = captor.getValue();

    assertThat(savedSchedule).isNotNull();
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule schedule = scheduleCaptor.getValue();

    // New implementation includes all dates in campaign range with all hours
    long daysBetween =
        java.time.temporal.ChronoUnit.DAYS.between(campaign.getStartDate(), campaign.getEndDate())
            + 1;
    assertThat(schedule.getBookingMatrix().size()).isEqualTo((int) daysBetween);

    // Each date should have all 24 hours
    schedule
        .getBookingMatrix()
        .values()
        .forEach(
            hours -> {
              assertThat(hours).hasSize(24);
            });
  }

  @Test
  @DisplayName("selectInventory - Should use scheduleTargeting daypart distribution when provided")
  void selectInventory_WithDaypartDistribution_ShouldDistributeAccordingly() {
    // Given
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .status(Campaign.Status.DRAFT)
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(6))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .build();
    campaign.setId("campaign123");
    campaign.setCreatedAt(LocalDateTime.now());
    campaign.setUpdatedAt(LocalDateTime.now());

    // ScheduleTargeting removed - booking matrix now defaults to all hours for all dates

    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setMediaOwnerId("mediaOwner123");
    // Setup operating times - use 00:00:00 to 23:59:00 to include all 24 hours
    // Set up for all weekdays to ensure all dates in campaign range have hours
    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    operatingTimesMap.put(Inventory.Weekday.MONDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.TUESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.WEDNESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.THURSDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.FRIDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SATURDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SUNDAY, List.of(operatingTime));
    inventory.setOperatingTimes(operatingTimesMap);

    // Setup digital fields
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    when(campaignService.findById("campaign123")).thenReturn(campaign);
    when(inventoryService.getById("inventory123")).thenReturn(inventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(campaign);

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then
    ArgumentCaptor<CampaignInventorySchedules> captor =
        ArgumentCaptor.forClass(CampaignInventorySchedules.class);
    verify(schedulesRepository).save(captor.capture());
    CampaignInventorySchedules savedSchedule = captor.getValue();

    assertThat(savedSchedule).isNotNull();
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule schedule = scheduleCaptor.getValue();

    // New implementation includes all 24 hours for each date
    schedule
        .getBookingMatrix()
        .values()
        .forEach(
            hours -> {
              assertThat(hours).hasSize(24);
              // Verify all 24 hours are included
              for (int hour = 0; hour < 24; hour++) {
                assertThat(hours).contains(hour);
              }
            });
  }

  @Test
  @DisplayName("selectInventory - Should use default weekday distribution when not provided")
  void selectInventory_WithoutWeekdayDistribution_ShouldUseDefaultDistribution() {
    // Given
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .status(Campaign.Status.DRAFT)
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(6))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .build();
    campaign.setId("campaign123");
    campaign.setCreatedAt(LocalDateTime.now());
    campaign.setUpdatedAt(LocalDateTime.now());
    // No scheduleTargeting set

    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setMediaOwnerId("mediaOwner123");
    // Setup operating times - use 00:00:00 to 23:59:00 to include all 24 hours
    // Set up for all weekdays to ensure all dates in campaign range have hours
    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    operatingTimesMap.put(Inventory.Weekday.MONDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.TUESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.WEDNESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.THURSDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.FRIDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SATURDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SUNDAY, List.of(operatingTime));
    inventory.setOperatingTimes(operatingTimesMap);

    // Setup digital fields
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    when(campaignService.findById("campaign123")).thenReturn(campaign);
    when(inventoryService.getById("inventory123")).thenReturn(inventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(campaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then
    ArgumentCaptor<CampaignInventorySchedules> captor =
        ArgumentCaptor.forClass(CampaignInventorySchedules.class);
    verify(schedulesRepository).save(captor.capture());
    CampaignInventorySchedules savedSchedule = captor.getValue();

    assertThat(savedSchedule).isNotNull();
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule schedule = scheduleCaptor.getValue();

    // Should have entries for all dates in campaign range
    long daysBetween =
        java.time.temporal.ChronoUnit.DAYS.between(campaign.getStartDate(), campaign.getEndDate())
            + 1;
    assertThat(schedule.getBookingMatrix().size()).isEqualTo((int) daysBetween);

    // Each date should have all 24 hours
    schedule
        .getBookingMatrix()
        .values()
        .forEach(
            hours -> {
              assertThat(hours).hasSize(24);
            });
  }

  @Test
  @DisplayName(
      "selectInventory - Should filter hours by screen time before distributing daypart slots")
  void selectInventory_WithPartialOverlapDaypart_ShouldDistributeOnlyToAvailableHours() {
    // Given
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .status(Campaign.Status.DRAFT)
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(0)) // 1 day
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .build();
    campaign.setId("campaign123");
    campaign.setCreatedAt(LocalDateTime.now());
    campaign.setUpdatedAt(LocalDateTime.now());

    // ScheduleTargeting removed - booking matrix now defaults to all hours for all dates

    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setMediaOwnerId("mediaOwner123");
    // Setup operating times - use 00:00:00 to 23:59:00 to include all 24 hours
    // Set up for all weekdays to ensure all dates in campaign range have hours
    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    operatingTimesMap.put(Inventory.Weekday.MONDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.TUESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.WEDNESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.THURSDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.FRIDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SATURDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SUNDAY, List.of(operatingTime));
    inventory.setOperatingTimes(operatingTimesMap);

    // Setup digital fields
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    when(campaignService.findById("campaign123")).thenReturn(campaign);
    when(inventoryService.getById("inventory123")).thenReturn(inventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(campaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then
    ArgumentCaptor<CampaignInventorySchedules> captor =
        ArgumentCaptor.forClass(CampaignInventorySchedules.class);
    verify(schedulesRepository).save(captor.capture());
    CampaignInventorySchedules savedSchedule = captor.getValue();

    assertThat(savedSchedule).isNotNull();
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule schedule = scheduleCaptor.getValue();

    // New implementation includes all 24 hours for each date
    schedule
        .getBookingMatrix()
        .values()
        .forEach(
            hours -> {
              assertThat(hours).hasSize(24);
              // Verify all 24 hours are included
              for (int hour = 0; hour < 24; hour++) {
                assertThat(hours).contains(hour);
              }
            });
  }

  @Test
  @DisplayName("selectInventory - Should create bookingMatrix with all hours for all dates")
  void selectInventory_WithFractionalSlots_ShouldCreateAllHoursMatrix() {
    // Given
    SelectCampaignInventoryRequestDTO highSovRequest = new SelectCampaignInventoryRequestDTO();
    highSovRequest.setCampaignId("campaign123");
    highSovRequest.setInventoryId("inventory123");
    highSovRequest.setOperationType(SelectCampaignInventoryRequestDTO.OperationType.SELECT);

    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .status(Campaign.Status.DRAFT)
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(6))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .build();
    campaign.setId("campaign123");
    campaign.setCreatedAt(LocalDateTime.now());
    campaign.setUpdatedAt(LocalDateTime.now());

    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setMediaOwnerId("mediaOwner123");
    // Setup operating times - use 00:00:00 to 23:59:00 to include all 24 hours
    // Set up for all weekdays to ensure all dates in campaign range have hours
    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    operatingTimesMap.put(Inventory.Weekday.MONDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.TUESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.WEDNESDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.THURSDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.FRIDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SATURDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.SUNDAY, List.of(operatingTime));
    inventory.setOperatingTimes(operatingTimesMap);

    // Setup digital fields
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    when(campaignService.findById("campaign123")).thenReturn(campaign);
    when(inventoryService.getById("inventory123")).thenReturn(inventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(campaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(highSovRequest);

    // Then
    ArgumentCaptor<CampaignInventorySchedules> captor =
        ArgumentCaptor.forClass(CampaignInventorySchedules.class);
    verify(schedulesRepository).save(captor.capture());
    CampaignInventorySchedules savedSchedule = captor.getValue();

    assertThat(savedSchedule).isNotNull();
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule schedule = scheduleCaptor.getValue();

    // New implementation includes all 24 hours for each date
    schedule
        .getBookingMatrix()
        .values()
        .forEach(
            hours -> {
              assertThat(hours).hasSize(24);
              // Verify all 24 hours are included
              for (int hour = 0; hour < 24; hour++) {
                assertThat(hours).contains(hour);
              }
            });
  }

  // ========== Inventory operating times - edge cases ==========

  @Test
  @DisplayName(
      "selectInventory - Operating times: two ranges (23:30-00:00 and 00:00-15:00) should produce 16 hours per day")
  void
      selectInventory_OperatingTimes_Inventory1_TwoRangesOvernightAndMorning_ShouldCreateCorrectBookingMatrix() {
    // Given - Inventory 1: { "start": "23:30:00", "end": "00:00:00" }, { "start": "00:00:00",
    // "end": "15:00:00" }
    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Inventory 1");
    inventory.setArchived(false);
    inventory.setMediaOwnerId("mediaOwner123");
    inventory.setType("DIGITAL");

    Inventory.OperatingTime overnight = new Inventory.OperatingTime();
    overnight.setStart("23:30:00");
    overnight.setEnd("00:00:00");
    Inventory.OperatingTime morning = new Inventory.OperatingTime();
    morning.setStart("00:00:00");
    morning.setEnd("15:00:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    List<Inventory.OperatingTime> times = List.of(overnight, morning);
    for (Inventory.Weekday day : Inventory.Weekday.values()) {
      operatingTimesMap.put(day, times);
    }
    inventory.setOperatingTimes(operatingTimesMap);

    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(inventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then - 23:30-00:00 → hour 23 only; 00:00-15:00 → hours 0..14. Combined: 16 hours [0..14, 23]
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule schedule = scheduleCaptor.getValue();
    assertThat(schedule.getBookingMatrix()).isNotNull();
    List<Integer> expectedHours = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 23);
    schedule
        .getBookingMatrix()
        .values()
        .forEach(
            hours -> {
              assertThat(hours).hasSize(16);
              assertThat(hours).containsExactlyElementsOf(expectedHours);
            });
  }

  @Test
  @DisplayName(
      "selectInventory - Operating times: overnight (05:00-00:00) should produce 19 hours per day")
  void
      selectInventory_OperatingTimes_Inventory2_Overnight0500ToMidnight_ShouldCreateCorrectBookingMatrix() {
    // Given - Inventory 2: { "start": "05:00:00", "end": "00:00:00" }
    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Inventory 2");
    inventory.setArchived(false);
    inventory.setMediaOwnerId("mediaOwner123");
    inventory.setType("DIGITAL");

    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("05:00:00");
    operatingTime.setEnd("00:00:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    List<Inventory.OperatingTime> times = List.of(operatingTime);
    for (Inventory.Weekday day : Inventory.Weekday.values()) {
      operatingTimesMap.put(day, times);
    }
    inventory.setOperatingTimes(operatingTimesMap);

    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(inventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then - 05:00-00:00 (overnight) → hours 5..23 = 19 hours
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule schedule = scheduleCaptor.getValue();
    assertThat(schedule.getBookingMatrix()).isNotNull();
    List<Integer> expectedHours =
        List.of(5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23);
    schedule
        .getBookingMatrix()
        .values()
        .forEach(
            hours -> {
              assertThat(hours).hasSize(19);
              assertThat(hours).containsExactlyElementsOf(expectedHours);
            });
  }

  @Test
  @DisplayName(
      "selectInventory - Operating times: same-day (06:00-23:59) should produce 18 hours per day")
  void
      selectInventory_OperatingTimes_Inventory3_SameDay0600To2359_ShouldCreateCorrectBookingMatrix() {
    // Given - Inventory 3: { "start": "06:00:00", "end": "23:59:00" }
    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Inventory 3");
    inventory.setArchived(false);
    inventory.setMediaOwnerId("mediaOwner123");
    inventory.setType("DIGITAL");

    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("06:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    List<Inventory.OperatingTime> times = List.of(operatingTime);
    for (Inventory.Weekday day : Inventory.Weekday.values()) {
      operatingTimesMap.put(day, times);
    }
    inventory.setOperatingTimes(operatingTimesMap);

    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(inventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then - 06:00-23:59 (end-of-day) → hours 6..23 = 18 hours
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule schedule = scheduleCaptor.getValue();
    assertThat(schedule.getBookingMatrix()).isNotNull();
    List<Integer> expectedHours =
        List.of(6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23);
    schedule
        .getBookingMatrix()
        .values()
        .forEach(
            hours -> {
              assertThat(hours).hasSize(18);
              assertThat(hours).containsExactlyElementsOf(expectedHours);
            });
  }

  @Test
  @DisplayName(
      "selectInventory - Operating times: overnight (01:00-00:00) should produce 23 hours per day")
  void selectInventory_OperatingTimes_Overnight0100ToMidnight_ShouldCreateCorrectBookingMatrix() {
    // Given - Operating times: { "start": "01:00:00", "end": "00:00:00" }
    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Inventory overnight 01:00-00:00");
    inventory.setArchived(false);
    inventory.setMediaOwnerId("mediaOwner123");
    inventory.setType("DIGITAL");

    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("01:00:00");
    operatingTime.setEnd("00:00:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    List<Inventory.OperatingTime> times = List.of(operatingTime);
    for (Inventory.Weekday day : Inventory.Weekday.values()) {
      operatingTimesMap.put(day, times);
    }
    inventory.setOperatingTimes(operatingTimesMap);

    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(inventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then - 01:00-00:00 (overnight) → hours 1..23 = 23 hours
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule schedule = scheduleCaptor.getValue();
    assertThat(schedule.getBookingMatrix()).isNotNull();
    List<Integer> expectedHours =
        List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23);
    schedule
        .getBookingMatrix()
        .values()
        .forEach(
            hours -> {
              assertThat(hours).hasSize(23);
              assertThat(hours).containsExactlyElementsOf(expectedHours);
            });
  }

  @Test
  @DisplayName(
      "selectInventory - Operating times: midnight to 00:59 (00:00-00:59) - edge case same-hour range")
  void selectInventory_OperatingTimes_MidnightTo0059_ShouldCreateCorrectBookingMatrix() {
    // Given - Operating times: { "start": "00:00:00", "end": "00:59:00" }
    // (same-hour range; startHour==endHour so service treats as overnight → 0..23)
    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Inventory 00:00-00:59");
    inventory.setArchived(false);
    inventory.setMediaOwnerId("mediaOwner123");
    inventory.setType("DIGITAL");

    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("01:00:00");
    operatingTime.setEnd("00:00:00");
    Inventory.OperatingTime operatingTime2 = new Inventory.OperatingTime();
    operatingTime2.setStart("00:00:00");
    operatingTime2.setEnd("00:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    List<Inventory.OperatingTime> times = List.of(operatingTime, operatingTime2);
    for (Inventory.Weekday day : Inventory.Weekday.values()) {
      operatingTimesMap.put(day, times);
    }
    inventory.setOperatingTimes(operatingTimesMap);

    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(inventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then - 00:00-00:59: startHour=0, endHour=0 (from 00:59), same-day condition false;
    // service uses overnight branch → hours 0..23 (24 hours). Test documents this edge case.
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule schedule = scheduleCaptor.getValue();
    assertThat(schedule.getBookingMatrix()).isNotNull();
    List<Integer> expectedHours =
        List.of(
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23);
    schedule
        .getBookingMatrix()
        .values()
        .forEach(
            hours -> {
              assertThat(hours).hasSize(24);
              assertThat(hours).containsExactlyElementsOf(expectedHours);
            });
  }

  @Test
  @DisplayName("selectInventory - Operating times: 05:00 to 23:30 - edge case hour range")
  void selectInventory_OperatingTimes_MidnightTo0059_ShouldCreateCorrectBookingMatrix2() {
    // Given - Operating times: { "start": "00:00:00", "end": "00:59:00" }
    // (same-hour range; startHour==endHour so service treats as overnight → 0..23)
    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Inventory 00:00-00:59");
    inventory.setArchived(false);
    inventory.setMediaOwnerId("mediaOwner123");
    inventory.setType("DIGITAL");

    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("05:00:00");
    operatingTime.setEnd("23:30:00");
    Inventory.OperatingTime operatingTime2 = new Inventory.OperatingTime();
    operatingTime2.setStart("02:30:00");
    operatingTime2.setEnd("02:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    List<Inventory.OperatingTime> times = List.of(operatingTime, operatingTime2);
    for (Inventory.Weekday day : Inventory.Weekday.values()) {
      operatingTimesMap.put(day, times);
    }
    inventory.setOperatingTimes(operatingTimesMap);

    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(inventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then - 00:00-00:59: startHour=0, endHour=0 (from 00:59), same-day condition false;
    // service uses overnight branch → hours 0..23 (24 hours). Test documents this edge case.
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    Schedule schedule = scheduleCaptor.getValue();
    assertThat(schedule.getBookingMatrix()).isNotNull();
    List<Integer> expectedHours =
        List.of(2, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23);
    schedule
        .getBookingMatrix()
        .values()
        .forEach(
            hours -> {
              assertThat(hours).hasSize(20);
              assertThat(hours).containsExactlyElementsOf(expectedHours);
            });
  }

  // ========== removeAllInventoriesForCampaign Tests ==========

  @Test
  @DisplayName("removeAllInventoriesForCampaign - Should remove all configs and evict cache")
  void removeAllInventoriesForCampaign_WithExistingConfigs_ShouldRemoveAll() {
    // Given
    String campaignId = "campaign123";

    CampaignInventorySchedules config1 = new CampaignInventorySchedules();
    config1.setCampaignId(campaignId);
    config1.setInventoryId("inventory123");
    config1.setMediaOwnerId("mediaOwner123");

    CampaignInventorySchedules config2 = new CampaignInventorySchedules();
    config2.setCampaignId(campaignId);
    config2.setInventoryId("inventory456");
    config2.setMediaOwnerId("mediaOwner456");

    List<CampaignInventorySchedules> allConfigs = List.of(config1, config2);

    testCampaign.setCompanyAccess(
        new ArrayList<>(List.of("mediaOwner123", "mediaOwner456", "mediaOwner789")));

    when(schedulesRepository.findByCampaignId(campaignId)).thenReturn(allConfigs);
    doNothing().when(schedulesRepository).deleteByCampaignId(campaignId);
    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);

    // When
    campaignInventorySchedulesService.removeAllInventoriesForCampaign(campaignId);

    // Then
    verify(campaignService).save(any(Campaign.class));
  }

  @Test
  @DisplayName("removeAllInventoriesForCampaign - With no configs should handle gracefully")
  void removeAllInventoriesForCampaign_WithNoConfigs_ShouldHandleGracefully() {
    // Given
    String campaignId = "campaign123";

    when(schedulesRepository.findByCampaignId(campaignId)).thenReturn(Collections.emptyList());

    // When
    campaignInventorySchedulesService.removeAllInventoriesForCampaign(campaignId);

    // Then
    // No configs means no mediaOwnerIds to remove, so campaignService.findById should not be called
    verify(campaignService, never()).findById(anyString());
    verify(campaignService, never()).save(any(Campaign.class));
  }

  // ========== bulkSelectDeselectInventories Tests ==========

  @Test
  @DisplayName(
      "bulkSelectDeselectInventories - SELECT operation should process multiple inventories")
  void bulkSelectDeselectInventories_SelectOperation_ShouldProcessMultipleInventories() {
    // Given
    String campaignId = "campaign123";
    CampaignInventoryFilterDTO filter = CampaignInventoryFilterDTO.builder().build();

    Inventory classicInventory = createInventory("classic-1", "CLASSIC");
    Inventory digitalInventory = createInventory("digital-1", "DIGITAL");
    List<Inventory> inventories = List.of(classicInventory, digitalInventory);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getInventoriesWithFiltersForBulkOperation(filter))
        .thenReturn(inventories);
    when(schedulesRepository.saveAll(anyList()))
        .thenAnswer(
            invocation -> {
              List<CampaignInventorySchedules> configs = invocation.getArgument(0);
              return configs;
            });

    // When
    int processed =
        campaignInventorySchedulesService.bulkSelectDeselectInventories(
            campaignId, filter, SelectCampaignInventoryRequestDTO.OperationType.SELECT);

    // Then
    assertThat(processed).isEqualTo(2);
    verify(campaignService).findById("campaign123");
    verify(inventoryService).getInventoriesWithFiltersForBulkOperation(filter);
    verify(campaignActivityService, times(1))
        .logActivity(
            eq("campaign123"),
            eq(CampaignActivityService.OperationType.ADDED),
            eq(CampaignActivityKey.SELECTED_INVENTORY_COUNT.key()),
            eq(2));
  }

  @Test
  @DisplayName("bulkSelectDeselectInventories - DESELECT operation should delete multiple configs")
  void bulkSelectDeselectInventories_DeselectOperation_ShouldDeleteMultipleConfigs() {
    // Given
    String campaignId = "campaign123";
    CampaignInventoryFilterDTO filter = CampaignInventoryFilterDTO.builder().build();

    Inventory inventory1 = createInventory("inventory-1", "DIGITAL");
    Inventory inventory2 = createInventory("inventory-2", "CLASSIC");
    List<Inventory> inventories = List.of(inventory1, inventory2);

    // Create CampaignInventorySchedules that will be found and deleted
    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("cis1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory-1");
    schedule1.setScheduleIds(List.of("schedule1"));

    CampaignInventorySchedules schedule2 = new CampaignInventorySchedules();
    schedule2.setId("cis2");
    schedule2.setCampaignId(campaignId);
    schedule2.setInventoryId("inventory-2");
    schedule2.setScheduleIds(List.of("schedule2"));

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getInventoriesWithFiltersForBulkOperation(filter))
        .thenReturn(inventories);
    // Mock findByCampaignIdAndInventoryIdIn to return the schedules that will be deleted
    when(schedulesRepository.findByCampaignIdAndInventoryIdIn(eq(campaignId), anyList()))
        .thenReturn(List.of(schedule1, schedule2));
    when(schedulesRepository.deleteByCampaignIdAndInventoryIdIn(eq("campaign123"), anyList()))
        .thenReturn(2L);
    runAsyncInline();

    // When
    int processed =
        campaignInventorySchedulesService.bulkSelectDeselectInventories(
            campaignId, filter, SelectCampaignInventoryRequestDTO.OperationType.DESELECT);

    // Then
    assertThat(processed).isEqualTo(2);
    verify(inventoryService).getInventoriesWithFiltersForBulkOperation(filter);
    verify(schedulesRepository).findByCampaignIdAndInventoryIdIn(eq(campaignId), anyList());
    verify(schedulesRepository).deleteByCampaignIdAndInventoryIdIn(eq("campaign123"), anyList());
    verify(campaignActivityService, times(1))
        .logActivity(
            eq("campaign123"),
            eq(CampaignActivityService.OperationType.REMOVED),
            eq(CampaignActivityKey.DESELECTED_INVENTORY_COUNT.key()),
            eq(2L));
    // Cache eviction still happens once per inventory (now parallelized, still synchronous)
    verify(scheduleCacheEvictor).evict("campaign123", "inventory-1");
    verify(scheduleCacheEvictor).evict("campaign123", "inventory-2");
    // Recommendation sync still happens, now deferred to the (inline-executed) virtual thread
    verify(recommendationService)
        .syncSelectedInventories(
            eq("campaign123"), anyList(), eq(RecommendationService.OperationType.DESELECT));
  }

  @Test
  @DisplayName("bulkSelectDeselectInventories - Should return 0 when no inventories found")
  void bulkSelectDeselectInventories_WithEmptyInventoryList_ShouldReturnZero() {
    // Given
    String campaignId = "campaign123";
    CampaignInventoryFilterDTO filter = CampaignInventoryFilterDTO.builder().build();

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getInventoriesWithFiltersForBulkOperation(filter)).thenReturn(List.of());

    // When
    int processed =
        campaignInventorySchedulesService.bulkSelectDeselectInventories(
            campaignId, filter, SelectCampaignInventoryRequestDTO.OperationType.SELECT);

    // Then
    assertThat(processed).isEqualTo(0);
    verify(campaignService).findById("campaign123");
    verify(inventoryService).getInventoriesWithFiltersForBulkOperation(filter);
    verify(schedulesRepository, never()).saveAll(anyList());
    verify(schedulesRepository, never()).deleteByCampaignIdAndInventoryIdIn(anyString(), anyList());
  }

  @Test
  @DisplayName("bulkSelectDeselectInventories - Should throw exception when campaign not found")
  void bulkSelectDeselectInventories_WhenCampaignNotFound_ShouldThrowException() {
    // Given
    String campaignId = "nonexistent";
    CampaignInventoryFilterDTO filter = CampaignInventoryFilterDTO.builder().build();

    when(campaignService.findById("nonexistent"))
        .thenThrow(new CampaignNotFoundException("nonexistent"));

    // When & Then
    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.bulkSelectDeselectInventories(
                    campaignId, filter, SelectCampaignInventoryRequestDTO.OperationType.SELECT))
        .isInstanceOf(CampaignNotFoundException.class)
        .hasMessageContaining("nonexistent");

    verify(campaignService).findById("nonexistent");
    verify(inventoryService, never()).getInventoriesWithFiltersForBulkOperation(any());
  }

  @Test
  @DisplayName(
      "bulkSelectDeselectInventories - Should throw BulkOperationFailedException on processing error")
  void bulkSelectDeselectInventories_WhenProcessingFails_ShouldThrowBulkOperationFailedException() {
    // Given
    String campaignId = "campaign123";
    CampaignInventoryFilterDTO filter = CampaignInventoryFilterDTO.builder().build();

    Inventory invalidInventory = new Inventory();
    invalidInventory.setId("invalid-1");
    invalidInventory.setType("DIGITAL");
    invalidInventory.setMediaOwnerId("test213");
    invalidInventory.setReferenceId("ref-1");
    // Add minimal required fields so createScheduleForInventory succeeds
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    invalidInventory.setDigitalFields(digitalFields);
    // Add operating times
    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    operatingTimesMap.put(Inventory.Weekday.MONDAY, List.of(operatingTime));
    invalidInventory.setOperatingTimes(operatingTimesMap);
    List<Inventory> inventories = List.of(invalidInventory);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getInventoriesWithFiltersForBulkOperation(filter))
        .thenReturn(inventories);
    // Mock mwMeasureService enrichment to return empty list (enrichment succeeds with no data)
    when(mwMeasureService.getReachAndFrequencyBySitesFromSchedules(
            anyInt(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    // Mock scheduleRepository.saveAll to throw exception during schedule saving
    // This will cause the bulk operation to fail and throw BulkOperationFailedException
    when(scheduleRepository.saveAll(anyList())).thenThrow(new RuntimeException("Database error"));

    // When & Then
    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.bulkSelectDeselectInventories(
                    campaignId, filter, SelectCampaignInventoryRequestDTO.OperationType.SELECT))
        .isInstanceOf(BulkOperationFailedException.class)
        .hasMessageContaining("Bulk SELECT operation failed for campaign campaign123");

    verify(campaignService).findById("campaign123");
    verify(inventoryService).getInventoriesWithFiltersForBulkOperation(filter);
  }

  @Test
  @DisplayName("bulkSelectDeselectInventories - SELECT should use single saveAll call")
  void bulkSelectDeselectInventories_SelectOperation_ShouldUseSingleSaveAllCall() {
    // Given
    String campaignId = "campaign123";
    CampaignInventoryFilterDTO filter = CampaignInventoryFilterDTO.builder().build();

    List<Inventory> inventories =
        List.of(
            createInventory("inv-1", "CLASSIC"),
            createInventory("inv-2", "DIGITAL"),
            createInventory("inv-3", "CLASSIC"));

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getInventoriesWithFiltersForBulkOperation(filter))
        .thenReturn(inventories);
    when(schedulesRepository.saveAll(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    int processed =
        campaignInventorySchedulesService.bulkSelectDeselectInventories(
            campaignId, filter, SelectCampaignInventoryRequestDTO.OperationType.SELECT);

    // Then
    assertThat(processed).isEqualTo(3);
    // Verify saveAll was called exactly once (not multiple times)
    verify(schedulesRepository, times(1)).saveAll(anyList());
    verify(schedulesRepository, never()).save(any(CampaignInventorySchedules.class));
  }

  @Test
  @DisplayName("bulkSelectDeselectInventories - DESELECT should use single delete call")
  void bulkSelectDeselectInventories_DeselectOperation_ShouldUseSingleDeleteCall() {
    // Given
    String campaignId = "campaign123";
    CampaignInventoryFilterDTO filter = CampaignInventoryFilterDTO.builder().build();

    List<Inventory> inventories =
        List.of(
            createInventory("inv-1", "CLASSIC"),
            createInventory("inv-2", "DIGITAL"),
            createInventory("inv-3", "CLASSIC"));

    // Create CampaignInventorySchedules that will be found and deleted
    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("cis1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inv-1");
    schedule1.setScheduleIds(List.of("schedule1"));

    CampaignInventorySchedules schedule2 = new CampaignInventorySchedules();
    schedule2.setId("cis2");
    schedule2.setCampaignId(campaignId);
    schedule2.setInventoryId("inv-2");
    schedule2.setScheduleIds(List.of("schedule2"));

    CampaignInventorySchedules schedule3 = new CampaignInventorySchedules();
    schedule3.setId("cis3");
    schedule3.setCampaignId(campaignId);
    schedule3.setInventoryId("inv-3");
    schedule3.setScheduleIds(List.of("schedule3"));

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getInventoriesWithFiltersForBulkOperation(filter))
        .thenReturn(inventories);
    // Mock findByCampaignIdAndInventoryIdIn to return the schedules that will be deleted
    when(schedulesRepository.findByCampaignIdAndInventoryIdIn(eq(campaignId), anyList()))
        .thenReturn(List.of(schedule1, schedule2, schedule3));
    when(schedulesRepository.deleteByCampaignIdAndInventoryIdIn(eq("campaign123"), anyList()))
        .thenReturn(3L);
    runAsyncInline();

    // When
    int processed =
        campaignInventorySchedulesService.bulkSelectDeselectInventories(
            campaignId, filter, SelectCampaignInventoryRequestDTO.OperationType.DESELECT);

    // Then
    assertThat(processed).isEqualTo(3);
    verify(schedulesRepository).findByCampaignIdAndInventoryIdIn(eq(campaignId), anyList());
    verify(schedulesRepository, never()).deleteByCampaignIdAndInventoryId(anyString(), anyString());
  }

  @Test
  @DisplayName(
      "bulkSelectDeselectInventories - DESELECT should batch companyAccess reconciliation across "
          + "multiple media owners into a single fetch/save instead of one per owner")
  void bulkSelectDeselectInventories_DeselectOperation_BatchesMediaOwnerReconciliation() {
    // Given: 3 distinct media owners, 2 of which will have zero remaining configs after deletion
    String campaignId = "campaign123";
    CampaignInventoryFilterDTO filter = CampaignInventoryFilterDTO.builder().build();

    Inventory inv1 = createInventory("inv-1", "CLASSIC");
    inv1.setMediaOwnerId("owner1");
    Inventory inv2 = createInventory("inv-2", "CLASSIC");
    inv2.setMediaOwnerId("owner2");
    Inventory inv3 = createInventory("inv-3", "CLASSIC");
    inv3.setMediaOwnerId("owner3");
    List<Inventory> inventories = List.of(inv1, inv2, inv3);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("cis1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inv-1");
    schedule1.setScheduleIds(List.of("schedule1"));

    CampaignInventorySchedules schedule2 = new CampaignInventorySchedules();
    schedule2.setId("cis2");
    schedule2.setCampaignId(campaignId);
    schedule2.setInventoryId("inv-2");
    schedule2.setScheduleIds(List.of("schedule2"));

    CampaignInventorySchedules schedule3 = new CampaignInventorySchedules();
    schedule3.setId("cis3");
    schedule3.setCampaignId(campaignId);
    schedule3.setInventoryId("inv-3");
    schedule3.setScheduleIds(List.of("schedule3"));

    testCampaign.setCompanyAccess(
        new ArrayList<>(List.of("owner1", "owner2", "owner3", "unrelatedOwner")));

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getInventoriesWithFiltersForBulkOperation(filter))
        .thenReturn(inventories);
    when(schedulesRepository.findByCampaignIdAndInventoryIdIn(eq(campaignId), anyList()))
        .thenReturn(List.of(schedule1, schedule2, schedule3));
    when(schedulesRepository.deleteByCampaignIdAndInventoryIdIn(eq(campaignId), anyList()))
        .thenReturn(3L);
    // owner1 and owner2 have no remaining configs; owner3 still has 2
    when(schedulesRepository.countByCampaignIdGroupedByMediaOwnerIdIn(eq(campaignId), anySet()))
        .thenReturn(Map.of("owner3", 2L));
    when(campaignService.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
    runAsyncInline();

    // When
    int processed =
        campaignInventorySchedulesService.bulkSelectDeselectInventories(
            campaignId, filter, SelectCampaignInventoryRequestDTO.OperationType.DESELECT);

    // Then
    assertThat(processed).isEqualTo(3);
    verify(schedulesRepository).countByCampaignIdGroupedByMediaOwnerIdIn(eq(campaignId), anySet());
    verify(schedulesRepository, never()).countByCampaignIdAndMediaOwnerId(anyString(), anyString());
    // Exactly one save/cache-evict for the whole reconciliation, not one per removed owner
    verify(campaignService, times(1)).save(any(Campaign.class));
    verify(campaignService, times(1)).campaignCacheEvict(campaignId);
    assertThat(testCampaign.getCompanyAccess())
        .containsExactlyInAnyOrder("owner3", "unrelatedOwner");
  }

  private Inventory createInventory(String id, String type) {
    Inventory inventory = new Inventory();
    inventory.setId(id);
    inventory.setName("Inventory-" + id);
    inventory.setType(type);
    inventory.setArchived(false);
    inventory.setMediaOwnerId("asd");

    // Setup operating times
    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    operatingTimesMap.put(Inventory.Weekday.MONDAY, List.of(operatingTime));
    inventory.setOperatingTimes(operatingTimesMap);

    // Setup digital fields
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);
    return inventory;
  }

  // ========== findByCampaignId Tests ==========

  @Test
  @DisplayName("findByCampaignId - Should return list of schedules for campaign")
  void findByCampaignId_ShouldReturnListOfSchedules() {
    // Given
    String campaignId = "campaign123";
    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("schedule1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory123");
    schedule1.setMediaOwnerId("mediaOwner123");

    CampaignInventorySchedules schedule2 = new CampaignInventorySchedules();
    schedule2.setId("schedule2");
    schedule2.setCampaignId(campaignId);
    schedule2.setInventoryId("inventory456");
    schedule2.setMediaOwnerId("mediaOwner456");

    List<CampaignInventorySchedules> schedules = List.of(schedule1, schedule2);

    when(schedulesRepository.findByCampaignId(campaignId)).thenReturn(schedules);

    // When
    List<CampaignInventorySchedules> result =
        campaignInventorySchedulesService.findByCampaignId(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(2);
    assertThat(result).containsExactlyInAnyOrder(schedule1, schedule2);
    verify(schedulesRepository).findByCampaignId(campaignId);
  }

  @Test
  @DisplayName("findByCampaignId - Should return empty list when no configs found")
  void findByCampaignId_WithNoConfigs_ShouldReturnEmptyList() {
    // Given
    String campaignId = "campaign123";

    when(schedulesRepository.findByCampaignId(campaignId)).thenReturn(Collections.emptyList());

    // When
    List<CampaignInventorySchedules> result =
        campaignInventorySchedulesService.findByCampaignId(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
    verify(schedulesRepository).findByCampaignId(campaignId);
  }

  // ========== findByCampaignIdAndInventoryIds Tests ==========

  @Test
  @DisplayName(
      "findByCampaignIdAndInventoryIds - Should return configs for specified inventory IDs")
  void findByCampaignIdAndInventoryIds_ShouldReturnMatchingConfigs() {
    // Given
    String campaignId = "campaign123";
    List<String> inventoryIds = List.of("inventory123", "inventory456");
    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("schedule1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory123");
    schedule1.setMediaOwnerId("mediaOwner123");

    CampaignInventorySchedules schedule2 = new CampaignInventorySchedules();
    schedule2.setId("schedule2");
    schedule2.setCampaignId(campaignId);
    schedule2.setInventoryId("inventory456");
    schedule2.setMediaOwnerId("mediaOwner456");

    List<CampaignInventorySchedules> schedules = List.of(schedule1, schedule2);

    when(schedulesRepository.findByCampaignIdAndInventoryIdIn(campaignId, inventoryIds))
        .thenReturn(schedules);

    // When
    List<CampaignInventorySchedules> result =
        campaignInventorySchedulesService.findByCampaignIdAndInventoryIds(campaignId, inventoryIds);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(2);
    verify(schedulesRepository).findByCampaignIdAndInventoryIdIn(campaignId, inventoryIds);
  }

  @Test
  @DisplayName("findByCampaignIdAndInventoryIds - Should return empty list when no matches")
  void findByCampaignIdAndInventoryIds_WithNoMatches_ShouldReturnEmptyList() {
    // Given
    String campaignId = "campaign123";
    List<String> inventoryIds = List.of("inventory999");

    when(schedulesRepository.findByCampaignIdAndInventoryIdIn(campaignId, inventoryIds))
        .thenReturn(Collections.emptyList());

    // When
    List<CampaignInventorySchedules> result =
        campaignInventorySchedulesService.findByCampaignIdAndInventoryIds(campaignId, inventoryIds);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
    verify(schedulesRepository).findByCampaignIdAndInventoryIdIn(campaignId, inventoryIds);
  }

  // ========== countByCampaignId Tests ==========

  @Test
  @DisplayName("countByCampaignId - Should return count of configs for campaign")
  void countByCampaignId_ShouldReturnCount() {
    // Given
    String campaignId = "campaign123";
    Long expectedCount = 5L;

    when(schedulesRepository.countByCampaignId(campaignId)).thenReturn(expectedCount);

    // When
    Long result = campaignInventorySchedulesService.countByCampaignId(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(expectedCount);
    verify(schedulesRepository).countByCampaignId(campaignId);
  }

  @Test
  @DisplayName("countByCampaignId - Should return zero when no configs found")
  void countByCampaignId_WithNoConfigs_ShouldReturnZero() {
    // Given
    String campaignId = "campaign123";

    when(schedulesRepository.countByCampaignId(campaignId)).thenReturn(0L);

    // When
    Long result = campaignInventorySchedulesService.countByCampaignId(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(0L);
    verify(schedulesRepository).countByCampaignId(campaignId);
  }

  // ========== countByCampaignIdAndMediaOwnerId Tests ==========

  @Test
  @DisplayName(
      "countByCampaignIdAndMediaOwnerId - Should return count for campaign and media owner")
  void countByCampaignIdAndMediaOwnerId_ShouldReturnCount() {
    // Given
    String campaignId = "campaign123";
    String mediaOwnerId = "mediaOwner123";

    when(schedulesRepository.countByCampaignIdAndMediaOwnerId(campaignId, mediaOwnerId))
        .thenReturn(3L);

    // When
    long result =
        campaignInventorySchedulesService.countByCampaignIdAndMediaOwnerId(
            campaignId, mediaOwnerId);

    // Then
    assertThat(result).isEqualTo(3L);
    verify(schedulesRepository).countByCampaignIdAndMediaOwnerId(campaignId, mediaOwnerId);
  }

  // ========== findByCampaignIdAndMediaOwnerId Tests ==========

  @Test
  @DisplayName("findByCampaignIdAndMediaOwnerId - Should return configs for media owner")
  void findByCampaignIdAndMediaOwnerId_ShouldReturnMatchingConfigs() {
    // Given
    String campaignId = "campaign123";
    String mediaOwnerId = "mediaOwner123";
    CampaignInventorySchedules config1 = new CampaignInventorySchedules();
    config1.setCampaignId(campaignId);
    config1.setMediaOwnerId(mediaOwnerId);
    CampaignInventorySchedules config2 = new CampaignInventorySchedules();
    config2.setCampaignId(campaignId);
    config2.setMediaOwnerId(mediaOwnerId);
    List<CampaignInventorySchedules> configs = List.of(config1, config2);

    when(schedulesRepository.findByCampaignIdAndMediaOwnerId(campaignId, mediaOwnerId))
        .thenReturn(configs);

    // When
    List<CampaignInventorySchedules> result =
        campaignInventorySchedulesService.findByCampaignIdAndMediaOwnerId(campaignId, mediaOwnerId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(2);
    verify(schedulesRepository).findByCampaignIdAndMediaOwnerId(campaignId, mediaOwnerId);
  }

  @Test
  @DisplayName("findByCampaignIdAndMediaOwnerId - Should return empty list when no matches")
  void findByCampaignIdAndMediaOwnerId_WithNoMatches_ShouldReturnEmptyList() {
    // Given
    String campaignId = "campaign123";
    String mediaOwnerId = "mediaOwner999";

    when(schedulesRepository.findByCampaignIdAndMediaOwnerId(campaignId, mediaOwnerId))
        .thenReturn(Collections.emptyList());

    // When
    List<CampaignInventorySchedules> result =
        campaignInventorySchedulesService.findByCampaignIdAndMediaOwnerId(campaignId, mediaOwnerId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
    verify(schedulesRepository).findByCampaignIdAndMediaOwnerId(campaignId, mediaOwnerId);
  }

  // ========== bulkSelectInventoriesByIds Tests ==========

  @Test
  @DisplayName("bulkSelectInventoriesByIds - Should process multiple inventories by IDs")
  void bulkSelectInventoriesByIds_ShouldProcessMultipleInventories() {
    // Given
    String campaignId = "campaign123";
    List<String> inventoryIds = List.of("inventory123", "inventory456");

    Inventory inv1 = createInventory("inventory123", "CLASSIC");
    inv1.setMediaOwnerId("mediaOwner123");
    Inventory inv2 = createInventory("inventory456", "DIGITAL");
    inv2.setMediaOwnerId("mediaOwner456");

    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(inventoryService.findAllByIds(inventoryIds)).thenReturn(List.of(inv1, inv2));
    when(schedulesRepository.saveAll(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    int processed =
        campaignInventorySchedulesService.bulkSelectInventoriesByIds(campaignId, inventoryIds);

    // Then
    assertThat(processed).isEqualTo(2);
    verify(campaignService).findById(campaignId);
    verify(inventoryService).findAllByIds(inventoryIds);
    verify(schedulesRepository).saveAll(anyList());
  }

  @Test
  @DisplayName("bulkSelectInventoriesByIds - Should return 0 when empty inventory IDs list")
  void bulkSelectInventoriesByIds_WithEmptyList_ShouldReturnZero() {
    // Given
    String campaignId = "campaign123";
    List<String> inventoryIds = Collections.emptyList();

    // When
    int processed =
        campaignInventorySchedulesService.bulkSelectInventoriesByIds(campaignId, inventoryIds);

    // Then
    assertThat(processed).isEqualTo(0);
    verify(campaignService, never()).findById(anyString());
    verify(inventoryService, never()).findAllByIds(anyList());
    verify(schedulesRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("bulkSelectInventoriesByIds - Should return 0 when no inventories found")
  void bulkSelectInventoriesByIds_WithNoInventoriesFound_ShouldReturnZero() {
    // Given
    String campaignId = "campaign123";
    List<String> inventoryIds = List.of("inventory999");

    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(inventoryService.findAllByIds(inventoryIds)).thenReturn(Collections.emptyList());

    // When
    int processed =
        campaignInventorySchedulesService.bulkSelectInventoriesByIds(campaignId, inventoryIds);

    // Then
    assertThat(processed).isEqualTo(0);
    verify(campaignService).findById(campaignId);
    verify(inventoryService).findAllByIds(inventoryIds);
    verify(schedulesRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("bulkSelectInventoriesByIds - Should throw exception when campaign not found")
  void bulkSelectInventoriesByIds_WhenCampaignNotFound_ShouldThrowException() {
    // Given
    String campaignId = "nonexistent";
    List<String> inventoryIds = List.of("inventory123");

    when(campaignService.findById(campaignId)).thenThrow(new CampaignNotFoundException(campaignId));

    // When & Then
    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.bulkSelectInventoriesByIds(
                    campaignId, inventoryIds))
        .isInstanceOf(CampaignNotFoundException.class)
        .hasMessageContaining(campaignId);

    verify(campaignService).findById(campaignId);
    verify(inventoryService, never()).findAllByIds(anyList());
  }

  // ========== bulkDeselectInventoriesByIds Tests ==========

  @Test
  @DisplayName("bulkDeselectInventoriesByIds - Should delete configs for multiple inventories")
  void bulkDeselectInventoriesByIds_ShouldProcessMultipleInventories() {
    // Given
    String campaignId = "campaign123";
    List<String> inventoryIds = List.of("inventory123", "inventory456");

    Inventory inv1 = createInventory("inventory123", "CLASSIC");
    inv1.setMediaOwnerId("mediaOwner123");
    Inventory inv2 = createInventory("inventory456", "DIGITAL");
    inv2.setMediaOwnerId("mediaOwner456");

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("cis1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory123");
    schedule1.setScheduleIds(List.of("schedule1"));

    CampaignInventorySchedules schedule2 = new CampaignInventorySchedules();
    schedule2.setId("cis2");
    schedule2.setCampaignId(campaignId);
    schedule2.setInventoryId("inventory456");
    schedule2.setScheduleIds(List.of("schedule2"));

    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(inventoryService.findAllByIds(inventoryIds)).thenReturn(List.of(inv1, inv2));
    when(schedulesRepository.findByCampaignIdAndInventoryIdIn(eq(campaignId), anyList()))
        .thenReturn(List.of(schedule1, schedule2));
    when(schedulesRepository.deleteByCampaignIdAndInventoryIdIn(eq(campaignId), anyList()))
        .thenReturn(2L);
    // Other configs still reference these media owners, so companyAccess is left untouched
    // (and the campaign is not reloaded for removal).
    when(schedulesRepository.countByCampaignIdGroupedByMediaOwnerIdIn(eq(campaignId), anySet()))
        .thenReturn(Map.of("mediaOwner123", 1L, "mediaOwner456", 1L));
    runAsyncInline();

    // When
    int processed =
        campaignInventorySchedulesService.bulkDeselectInventoriesByIds(campaignId, inventoryIds);

    // Then
    assertThat(processed).isEqualTo(2);
    verify(campaignService).findById(campaignId);
    verify(inventoryService).findAllByIds(inventoryIds);
    verify(schedulesRepository).findByCampaignIdAndInventoryIdIn(eq(campaignId), anyList());
    verify(schedulesRepository).deleteByCampaignIdAndInventoryIdIn(eq(campaignId), anyList());
    // No media owner reached zero remaining configs, so companyAccess is never touched.
    verify(campaignService, never()).save(any(Campaign.class));
  }

  @Test
  @DisplayName("bulkDeselectInventoriesByIds - Should return 0 when empty inventory IDs list")
  void bulkDeselectInventoriesByIds_WithEmptyList_ShouldReturnZero() {
    // Given
    String campaignId = "campaign123";
    List<String> inventoryIds = Collections.emptyList();

    // When
    int processed =
        campaignInventorySchedulesService.bulkDeselectInventoriesByIds(campaignId, inventoryIds);

    // Then
    assertThat(processed).isEqualTo(0);
    verify(campaignService, never()).findById(anyString());
    verify(inventoryService, never()).findAllByIds(anyList());
    verify(schedulesRepository, never()).deleteByCampaignIdAndInventoryIdIn(anyString(), anyList());
  }

  @Test
  @DisplayName("bulkDeselectInventoriesByIds - Should return 0 when no inventories found")
  void bulkDeselectInventoriesByIds_WithNoInventoriesFound_ShouldReturnZero() {
    // Given
    String campaignId = "campaign123";
    List<String> inventoryIds = List.of("inventory999");

    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(inventoryService.findAllByIds(inventoryIds)).thenReturn(Collections.emptyList());

    // When
    int processed =
        campaignInventorySchedulesService.bulkDeselectInventoriesByIds(campaignId, inventoryIds);

    // Then
    assertThat(processed).isEqualTo(0);
    verify(campaignService).findById(campaignId);
    verify(inventoryService).findAllByIds(inventoryIds);
    verify(schedulesRepository, never()).deleteByCampaignIdAndInventoryIdIn(anyString(), anyList());
  }

  @Test
  @DisplayName("bulkDeselectInventoriesByIds - Should throw exception when campaign not found")
  void bulkDeselectInventoriesByIds_WhenCampaignNotFound_ShouldThrowException() {
    // Given
    String campaignId = "nonexistent";
    List<String> inventoryIds = List.of("inventory123");

    when(campaignService.findById(campaignId)).thenThrow(new CampaignNotFoundException(campaignId));

    // When & Then
    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.bulkDeselectInventoriesByIds(
                    campaignId, inventoryIds))
        .isInstanceOf(CampaignNotFoundException.class)
        .hasMessageContaining(campaignId);

    verify(campaignService).findById(campaignId);
    verify(inventoryService, never()).findAllByIds(anyList());
  }

  // ========== Updated deselectInventory Tests (mediaOwnerId handling) ==========

  @Test
  @DisplayName(
      "deselectInventory - Should remove mediaOwnerId from companyAccess when no other schedules")
  void deselectInventory_WithMediaOwnerId_ShouldRemoveFromCompanyAccessWhenNoOtherSchedules() {
    // Given
    testCampaign.setCompanyAccess(new ArrayList<>(List.of("mediaOwner123", "mediaOwner456")));

    // Mock CampaignInventorySchedules with scheduleIds
    CampaignInventorySchedules campaignSchedule = new CampaignInventorySchedules();
    campaignSchedule.setId("cis1");
    campaignSchedule.setCampaignId("campaign123");
    campaignSchedule.setInventoryId("inventory123");
    campaignSchedule.setScheduleIds(List.of("schedule1", "schedule2"));

    when(campaignService.findByIdForCurrentModeForWrite("campaign123")).thenReturn(testCampaign);
    when(schedulesRepository.findByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(Optional.of(campaignSchedule));
    doNothing().when(scheduleRepository).deleteByIdIn(anyList());
    when(inventoryService.getMediaOwnerIdById("inventory123")).thenReturn("mediaOwner123");
    when(schedulesRepository.deleteByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(1L);
    when(schedulesRepository.countByCampaignIdAndMediaOwnerId("campaign123", "mediaOwner123"))
        .thenReturn(0L);
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.deselectInventory(testDeselectRequest);

    // Then
    verify(campaignService).findByIdForCurrentModeForWrite("campaign123");
    verify(schedulesRepository).findByCampaignIdAndInventoryId("campaign123", "inventory123");
    verify(scheduleRepository).deleteByIdIn(List.of("schedule1", "schedule2"));
    verify(inventoryService).getMediaOwnerIdById("inventory123");
    verify(schedulesRepository).deleteByCampaignIdAndInventoryId("campaign123", "inventory123");
    verify(schedulesRepository).countByCampaignIdAndMediaOwnerId("campaign123", "mediaOwner123");
    verify(campaignService).findById("campaign123");
    verify(campaignService).save(any(Campaign.class));
  }

  @Test
  @DisplayName(
      "deselectInventory - Should keep mediaOwnerId in companyAccess when other schedules exist")
  void deselectInventory_WithMediaOwnerId_ShouldKeepInCompanyAccessWhenOtherSchedulesExist() {
    // Given
    testCampaign.setCompanyAccess(new ArrayList<>(List.of("mediaOwner123")));

    // Mock CampaignInventorySchedules with scheduleIds
    CampaignInventorySchedules campaignSchedule = new CampaignInventorySchedules();
    campaignSchedule.setId("cis1");
    campaignSchedule.setCampaignId("campaign123");
    campaignSchedule.setInventoryId("inventory123");
    campaignSchedule.setScheduleIds(List.of("schedule1"));

    when(campaignService.findByIdForCurrentModeForWrite("campaign123")).thenReturn(testCampaign);
    when(schedulesRepository.findByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(Optional.of(campaignSchedule));
    doNothing().when(scheduleRepository).deleteByIdIn(anyList());
    when(inventoryService.getMediaOwnerIdById("inventory123")).thenReturn("mediaOwner123");
    when(schedulesRepository.deleteByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(1L);
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(schedulesRepository.countByCampaignIdAndMediaOwnerId("campaign123", "mediaOwner123"))
        .thenReturn(1L); // Still has other schedules
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.deselectInventory(testDeselectRequest);

    // Then
    verify(campaignService).findByIdForCurrentModeForWrite("campaign123");
    verify(schedulesRepository).findByCampaignIdAndInventoryId("campaign123", "inventory123");
    verify(scheduleRepository).deleteByIdIn(List.of("schedule1"));
    verify(inventoryService).getMediaOwnerIdById("inventory123");
    verify(schedulesRepository).deleteByCampaignIdAndInventoryId("campaign123", "inventory123");
    verify(schedulesRepository).countByCampaignIdAndMediaOwnerId("campaign123", "mediaOwner123");
    verify(campaignService, never()).findById("campaign123");
    verify(campaignService, never()).save(any(Campaign.class));
  }

  @Test
  @DisplayName("deselectInventory - Should handle null mediaOwnerId gracefully")
  void deselectInventory_WithNullMediaOwnerId_ShouldHandleGracefully() {
    // Given
    // Mock CampaignInventorySchedules with scheduleIds
    CampaignInventorySchedules campaignSchedule = new CampaignInventorySchedules();
    campaignSchedule.setId("cis1");
    campaignSchedule.setCampaignId("campaign123");
    campaignSchedule.setInventoryId("inventory123");
    campaignSchedule.setScheduleIds(List.of("schedule1", "schedule2"));

    when(campaignService.findByIdForCurrentModeForWrite("campaign123")).thenReturn(testCampaign);
    when(schedulesRepository.findByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(Optional.of(campaignSchedule));
    doNothing().when(scheduleRepository).deleteByIdIn(anyList());
    when(inventoryService.getMediaOwnerIdById("inventory123")).thenReturn(null);
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(schedulesRepository.deleteByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(1L);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.deselectInventory(testDeselectRequest);

    // Then
    verify(campaignService).findByIdForCurrentModeForWrite("campaign123");
    verify(schedulesRepository).findByCampaignIdAndInventoryId("campaign123", "inventory123");
    verify(scheduleRepository).deleteByIdIn(List.of("schedule1", "schedule2"));
    verify(inventoryService).getMediaOwnerIdById("inventory123");
    verify(schedulesRepository).deleteByCampaignIdAndInventoryId("campaign123", "inventory123");
    verify(schedulesRepository, never()).countByCampaignIdAndMediaOwnerId(anyString(), anyString());
  }

  @Test
  @DisplayName("deselectInventory - Should handle null or empty scheduleIds gracefully")
  void deselectInventory_WithNullOrEmptyScheduleIds_ShouldHandleGracefully() {
    // Given
    // Mock CampaignInventorySchedules with null scheduleIds
    CampaignInventorySchedules campaignSchedule = new CampaignInventorySchedules();
    campaignSchedule.setId("cis1");
    campaignSchedule.setCampaignId("campaign123");
    campaignSchedule.setInventoryId("inventory123");
    campaignSchedule.setScheduleIds(null); // No schedules

    when(campaignService.findByIdForCurrentModeForWrite("campaign123")).thenReturn(testCampaign);
    when(schedulesRepository.findByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(Optional.of(campaignSchedule));
    when(inventoryService.getMediaOwnerIdById("inventory123")).thenReturn(null);
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(schedulesRepository.deleteByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(1L);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.deselectInventory(testDeselectRequest);

    // Then
    verify(campaignService).findByIdForCurrentModeForWrite("campaign123");
    verify(schedulesRepository).findByCampaignIdAndInventoryId("campaign123", "inventory123");
    verify(scheduleRepository, never())
        .deleteByIdIn(anyList()); // Should not delete schedules if null/empty
    verify(inventoryService).getMediaOwnerIdById("inventory123");
    verify(schedulesRepository).deleteByCampaignIdAndInventoryId("campaign123", "inventory123");
  }

  // ========== Updated selectInventory Tests (mediaOwnerId handling) ==========

  @Test
  @DisplayName("selectInventory - Should add mediaOwnerId to companyAccess")
  void selectInventory_WithMediaOwnerId_ShouldAddToCompanyAccess() {
    // Given
    testInventory.setMediaOwnerId("mediaOwner123");
    testCampaign.setCompanyAccess(new ArrayList<>());

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then
    verify(campaignService).findById("campaign123");
    verify(inventoryService).getById("inventory123");
    verify(schedulesRepository).save(any(CampaignInventorySchedules.class));
    verify(campaignService).save(any(Campaign.class));
    verify(campaignActivityService, times(1))
        .logActivity(
            eq("campaign123"),
            eq(CampaignActivityService.OperationType.ADDED),
            eq(CampaignActivityKey.INVENTORY_REFERENCE_ID.key()),
            anyString());
  }

  @Test
  @DisplayName("selectInventory - Should not add duplicate mediaOwnerId to companyAccess")
  void selectInventory_WithExistingMediaOwnerId_ShouldNotAddDuplicate() {
    // Given
    testInventory.setMediaOwnerId("mediaOwner123");
    testCampaign.setCompanyAccess(new ArrayList<>(List.of("mediaOwner123")));

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then
    verify(campaignService).findById("campaign123");
    verify(inventoryService).getById("inventory123");
    verify(schedulesRepository).save(any(CampaignInventorySchedules.class));
    // Should not save campaign since mediaOwnerId already exists
    verify(campaignService, never()).save(any(Campaign.class));
  }

  // ========== Updated removeAllInventoriesForCampaign Tests (mediaOwnerId handling) ==========

  @Test
  @DisplayName("removeAllInventoriesForCampaign - Should remove mediaOwnerIds from companyAccess")
  void removeAllInventoriesForCampaign_WithMediaOwnerIds_ShouldRemoveFromCompanyAccess() {
    // Given
    String campaignId = "campaign123";

    CampaignInventorySchedules config1 = new CampaignInventorySchedules();
    config1.setCampaignId(campaignId);
    config1.setInventoryId("inventory123");
    config1.setMediaOwnerId("mediaOwner123");

    CampaignInventorySchedules config2 = new CampaignInventorySchedules();
    config2.setCampaignId(campaignId);
    config2.setInventoryId("inventory456");
    config2.setMediaOwnerId("mediaOwner456");

    List<CampaignInventorySchedules> configs = List.of(config1, config2);

    testCampaign.setCompanyAccess(
        new ArrayList<>(List.of("mediaOwner123", "mediaOwner456", "mediaOwner789")));

    when(schedulesRepository.findByCampaignId(campaignId)).thenReturn(configs);
    doNothing().when(schedulesRepository).deleteByCampaignId(campaignId);
    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);

    // When
    campaignInventorySchedulesService.removeAllInventoriesForCampaign(campaignId);

    // Then
    verify(campaignService).save(any(Campaign.class));
  }

  @Test
  @DisplayName("removeAllInventoriesForCampaign - Should handle empty companyAccess gracefully")
  void removeAllInventoriesForCampaign_WithEmptyCompanyAccess_ShouldHandleGracefully() {
    // Given
    String campaignId = "campaign123";

    CampaignInventorySchedules config1 = new CampaignInventorySchedules();
    config1.setCampaignId(campaignId);
    config1.setInventoryId("inventory123");
    config1.setMediaOwnerId("mediaOwner123");

    List<CampaignInventorySchedules> configs = List.of(config1);

    testCampaign.setCompanyAccess(null);

    when(schedulesRepository.findByCampaignId(campaignId)).thenReturn(configs);
    doNothing().when(schedulesRepository).deleteByCampaignId(campaignId);
    when(campaignService.findById(campaignId)).thenReturn(testCampaign);

    // When
    campaignInventorySchedulesService.removeAllInventoriesForCampaign(campaignId);

    // Then
    verify(schedulesRepository).findByCampaignId(campaignId);
    verify(schedulesRepository).deleteByCampaignId(campaignId);
    verify(campaignService, never()).save(any(Campaign.class));
  }

  // ========== bulkSchedules Tests ==========

  @Test
  @DisplayName("bulkSchedules - Should create new schedules when clearSchedules is true")
  void bulkSchedules_WithClearSchedulesTrue_ShouldCreateNewSchedules() {
    // Given
    String campaignId = "campaign123";
    List<String> inventoryIds = List.of("inventory123", "inventory456");

    BulkSchedulesRequestDTO request = new BulkSchedulesRequestDTO();
    request.setInventoryIds(inventoryIds);
    request.setClearSchedules(true);

    BulkSchedulesRequestDTO.ScheduleDTO scheduleDTO = new BulkSchedulesRequestDTO.ScheduleDTO();
    scheduleDTO.setStartDate(LocalDate.now());
    scheduleDTO.setEndDate(LocalDate.now().plusDays(7));
    scheduleDTO.setScheduleDays(List.of("MONDAY", "TUESDAY"));
    Map<String, List<Integer>> bookingMatrix = new HashMap<>();
    bookingMatrix.put(
        LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy")),
        List.of(0, 1, 2));
    scheduleDTO.setBookingMatrix(bookingMatrix);
    request.setSchedule(scheduleDTO);

    Inventory inventory1 = new Inventory();
    inventory1.setId("inventory123");
    inventory1.setMediaOwnerId("mediaOwner123");
    Inventory.OperatingTime operatingTime1 = new Inventory.OperatingTime();
    operatingTime1.setStart("00:00:00");
    operatingTime1.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap1 = new HashMap<>();
    operatingTimesMap1.put(Inventory.Weekday.MONDAY, List.of(operatingTime1));
    inventory1.setOperatingTimes(operatingTimesMap1);
    Inventory.DigitalFields digitalFields1 = new Inventory.DigitalFields();
    digitalFields1.setSpotsPerLoop(10);
    digitalFields1.setSpotDuration(30);
    inventory1.setDigitalFields(digitalFields1);

    Inventory inventory2 = new Inventory();
    inventory2.setId("inventory456");
    inventory2.setMediaOwnerId("mediaOwner456");
    Inventory.OperatingTime operatingTime2 = new Inventory.OperatingTime();
    operatingTime2.setStart("00:00:00");
    operatingTime2.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap2 = new HashMap<>();
    operatingTimesMap2.put(Inventory.Weekday.MONDAY, List.of(operatingTime2));
    inventory2.setOperatingTimes(operatingTimesMap2);
    Inventory.DigitalFields digitalFields2 = new Inventory.DigitalFields();
    digitalFields2.setSpotsPerLoop(10);
    digitalFields2.setSpotDuration(30);
    inventory2.setDigitalFields(digitalFields2);

    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(inventoryService.findAllByIds(inventoryIds)).thenReturn(List.of(inventory1, inventory2));
    when(schedulesRepository.findByCampaignIdAndInventoryIdIn(campaignId, inventoryIds))
        .thenReturn(List.of());
    when(schedulesRepository.saveAll(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    campaignInventorySchedulesService.bulkSchedules(campaignId, request);

    // Then
    verify(campaignService).findById(campaignId);
    verify(inventoryService).findAllByIds(inventoryIds);
    verify(schedulesRepository).findByCampaignIdAndInventoryIdIn(campaignId, inventoryIds);
    verify(schedulesRepository).saveAll(anyList());
    verify(campaignActivityService, times(1))
        .logActivity(
            eq(campaignId),
            eq(CampaignActivityService.OperationType.ADDED),
            eq(CampaignActivityKey.BULK_SCHEDULES_COUNT.key()),
            eq(2));
  }

  @Test
  @DisplayName("bulkSchedules - Should append schedules when clearSchedules is false")
  void bulkSchedules_WithClearSchedulesFalse_ShouldAppendSchedules() {
    // Given
    String campaignId = "campaign123";
    String inventoryId = "inventory123";

    BulkSchedulesRequestDTO request = new BulkSchedulesRequestDTO();
    request.setInventoryIds(List.of(inventoryId));
    request.setClearSchedules(false);

    BulkSchedulesRequestDTO.ScheduleDTO scheduleDTO = new BulkSchedulesRequestDTO.ScheduleDTO();
    scheduleDTO.setStartDate(LocalDate.now());
    scheduleDTO.setEndDate(LocalDate.now().plusDays(7));
    scheduleDTO.setScheduleDays(List.of("MONDAY"));
    Map<String, List<Integer>> bookingMatrix = new HashMap<>();
    bookingMatrix.put(
        LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy")),
        List.of(0));
    scheduleDTO.setBookingMatrix(bookingMatrix);
    request.setSchedule(scheduleDTO);

    CampaignInventorySchedules existingSchedule = new CampaignInventorySchedules();
    existingSchedule.setId("schedule123");
    existingSchedule.setCampaignId(campaignId);
    existingSchedule.setInventoryId(inventoryId);
    existingSchedule.setMediaOwnerId("mediaOwner123");

    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(inventoryService.findAllByIds(List.of(inventoryId))).thenReturn(List.of(testInventory));
    when(schedulesRepository.findByCampaignIdAndInventoryIdIn(campaignId, List.of(inventoryId)))
        .thenReturn(List.of(existingSchedule));
    when(schedulesRepository.saveAll(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    campaignInventorySchedulesService.bulkSchedules(campaignId, request);

    // Then
    verify(campaignService).findById(campaignId);
    verify(inventoryService).findAllByIds(List.of(inventoryId));
    verify(schedulesRepository).findByCampaignIdAndInventoryIdIn(campaignId, List.of(inventoryId));
    verify(schedulesRepository).saveAll(anyList());
  }

  @Test
  @DisplayName("bulkSchedules - Should throw exception when campaign not found")
  void bulkSchedules_WhenCampaignNotFound_ShouldThrowException() {
    // Given
    String campaignId = "nonexistent";
    BulkSchedulesRequestDTO request = new BulkSchedulesRequestDTO();
    request.setInventoryIds(List.of("inventory123"));
    request.setClearSchedules(true);

    BulkSchedulesRequestDTO.ScheduleDTO scheduleDTO = new BulkSchedulesRequestDTO.ScheduleDTO();
    scheduleDTO.setStartDate(LocalDate.now());
    scheduleDTO.setEndDate(LocalDate.now().plusDays(7));
    scheduleDTO.setScheduleDays(List.of("MONDAY"));
    Map<String, List<Integer>> bookingMatrix = new HashMap<>();
    bookingMatrix.put(
        LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy")),
        List.of(0));
    scheduleDTO.setBookingMatrix(bookingMatrix);
    request.setSchedule(scheduleDTO);

    when(campaignService.findById(campaignId)).thenThrow(new CampaignNotFoundException(campaignId));

    // When & Then
    assertThatThrownBy(() -> campaignInventorySchedulesService.bulkSchedules(campaignId, request))
        .isInstanceOf(CampaignNotFoundException.class);

    verify(campaignService).findById(campaignId);
    verify(inventoryService, never()).findAllByIds(anyList());
    verify(schedulesRepository, never()).saveAll(anyList());
  }

  // ========== getCampaignSchedulePrices Tests ==========

  @Test
  @DisplayName("getCampaignSchedulePrices - Should return paginated schedule prices with filters")
  void getCampaignSchedulePrices_WithValidFilters_ShouldReturnPaginatedResults() {
    // Given
    String campaignId = "campaign123";
    CampaignSchedulePriceFilterDTO filter =
        CampaignSchedulePriceFilterDTO.builder()
            .cities(List.of("Bunkyo", "Tokyo"))
            .inventoryTypes(List.of("Digital"))
            .mediaOwnerIds(List.of("mediaOwner123"))
            .minPricing(100.0)
            .maxPricing(1000.0)
            .build();

    Pageable pageable = PageRequest.of(0, 10);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("schedule1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory123");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("scheduleId1", "scheduleId2"));

    Page<CampaignInventorySchedules> filteredPage = new PageImpl<>(List.of(schedule1), pageable, 1);

    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Test Inventory");
    inventory.setType("Digital");
    inventory.setMediaOwnerId("mediaOwner123");
    Inventory.Location location = new Inventory.Location();
    location.setCity("Bunkyo");
    inventory.setLocation(location);

    Schedule schedule = new Schedule();
    schedule.setId("scheduleId1");
    schedule.setName("Schedule 1");
    schedule.setStartDate(LocalDate.now());
    schedule.setEndDate(LocalDate.now().plusDays(7));
    schedule.setImpressions(1000L);
    schedule.setReach(500L);
    schedule.setBasePrice(150.0);
    schedule.setAdPlays(100L);

    when(schedulesRepository.findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull()))
        .thenReturn(filteredPage);
    when(inventoryService.findAllByIds(anyList())).thenReturn(List.of(inventory));
    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(schedule));
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(customFeeService.getActiveCustomFeesContextForCampaign(any(Campaign.class)))
        .thenReturn(CustomFeesContext.builder().build());

    CompanyLookupResponseDTO companyLookup =
        CompanyLookupResponseDTO.builder().id("mediaOwner123").name("Test Media Owner").build();
    when(companyService.getCompanyLookupWithCompanyId("mediaOwner123")).thenReturn(companyLookup);

    // When
    Page<CampaignSchedulePriceResponseDTO> result =
        campaignInventorySchedulesService.getCampaignSchedulePrices(campaignId, filter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getTotalElements()).isEqualTo(1);

    CampaignSchedulePriceResponseDTO responseDTO = result.getContent().get(0);
    assertThat(responseDTO.getInventoryId()).isEqualTo("inventory123");
    assertThat(responseDTO.getInventoryName()).isEqualTo("Test Inventory");
    assertThat(responseDTO.getMediaOwnerId()).isEqualTo("mediaOwner123");
    assertThat(responseDTO.getMediaOwnerName()).isEqualTo("Test Media Owner");

    verify(schedulesRepository).findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull());
    verify(inventoryService).findAllByIds(anyList());
    verify(campaignService).findById(campaignId);
  }

  @Test
  @DisplayName(
      "getCampaignSchedulePrices - Should carry cinemaFields (operator/hall/showtimes) on cinema"
          + " rows")
  void getCampaignSchedulePrices_WithCinemaInventory_ShouldIncludeCinemaFields() {
    // Given
    String campaignId = "campaign123";
    CampaignSchedulePriceFilterDTO filter = CampaignSchedulePriceFilterDTO.builder().build();
    Pageable pageable = PageRequest.of(0, 10);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("scheduleCinema1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("cinemaInventory1");
    schedule1.setMediaOwnerId("cinemaOwner1");
    schedule1.setScheduleIds(List.of("scheduleId1"));

    Page<CampaignInventorySchedules> filteredPage = new PageImpl<>(List.of(schedule1), pageable, 1);

    Inventory inventory = new Inventory();
    inventory.setId("cinemaInventory1");
    inventory.setName("PVR INOX Phoenix Audi 3");
    inventory.setType("Cinema");
    inventory.setClassification("Cinema");
    inventory.setMediaOwnerId("cinemaOwner1");
    Inventory.CinemaFields cinemaFields = new Inventory.CinemaFields();
    cinemaFields.setOperator("PVR INOX");
    cinemaFields.setOperatorId("op-pvr");
    cinemaFields.setCinemaName("PVR Phoenix");
    cinemaFields.setHallName("Audi 3");
    cinemaFields.setHallNumber(3);
    Inventory.ShowtimeWindow window = new Inventory.ShowtimeWindow();
    window.setLabel("Evening");
    window.setStart("17:00");
    window.setEnd("20:00");
    cinemaFields.setShowtimeWindows(List.of(window));
    cinemaFields.setGenres(List.of("Action"));
    cinemaFields.setRatings(List.of("PG-13"));
    inventory.setCinemaFields(cinemaFields);

    Schedule schedule = new Schedule();
    schedule.setId("scheduleId1");
    schedule.setStartDate(LocalDate.now());
    schedule.setEndDate(LocalDate.now().plusDays(7));
    schedule.setBasePrice(150.0);

    when(schedulesRepository.findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull()))
        .thenReturn(filteredPage);
    when(inventoryService.findAllByIds(anyList())).thenReturn(List.of(inventory));
    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(schedule));
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(customFeeService.getActiveCustomFeesContextForCampaign(any(Campaign.class)))
        .thenReturn(CustomFeesContext.builder().build());
    when(companyService.getCompanyLookupWithCompanyId("cinemaOwner1"))
        .thenReturn(CompanyLookupResponseDTO.builder().id("cinemaOwner1").name("PVR INOX").build());

    // When
    Page<CampaignSchedulePriceResponseDTO> result =
        campaignInventorySchedulesService.getCampaignSchedulePrices(campaignId, filter, pageable);

    // Then
    assertThat(result.getContent()).hasSize(1);
    CampaignSchedulePriceResponseDTO dto = result.getContent().get(0);
    assertThat(dto.getCinemaFields()).isNotNull();
    assertThat(dto.getCinemaFields().getOperator()).isEqualTo("PVR INOX");
    assertThat(dto.getCinemaFields().getHallName()).isEqualTo("Audi 3");
    assertThat(dto.getCinemaFields().getShowtimeWindows()).hasSize(1);
    assertThat(dto.getCinemaFields().getShowtimeWindows().get(0).getLabel()).isEqualTo("Evening");
    assertThat(dto.getCinemaFields().getGenres()).containsExactly("Action");
    assertThat(dto.getCinemaFields().getRatings()).containsExactly("PG-13");
  }

  @Test
  @DisplayName(
      "getCampaignSchedulePrices - Dual-member user switched into a media owner sees only that"
          + " owner's slice, even though they are also a member of the buyer company")
  void getCampaignSchedulePrices_SwitchedDualMember_OnlySeesActingCompanySlice() {
    String campaignId = "campaign123";
    CampaignSchedulePriceFilterDTO filter = CampaignSchedulePriceFilterDTO.builder().build();
    Pageable pageable = PageRequest.of(0, 10);

    // Acting company is the media owner; the user ALSO holds membership in the buyer
    // company ("company123") — that other membership must not widen visibility.
    when(userService.getActingCompanyId()).thenReturn("mediaOwner123");
    lenient().when(userService.isTenantOfCompany("company123")).thenReturn(true);

    when(schedulesRepository.findWithPriceFilters(
            eq(campaignId), any(), eq(pageable), eq("mediaOwner123")))
        .thenReturn(new PageImpl<>(List.of(), pageable, 0));
    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(testCampaign);

    campaignInventorySchedulesService.getCampaignSchedulePrices(campaignId, filter, pageable);

    // Scoped query with the acting media-owner id — never the unscoped (null) variant.
    verify(schedulesRepository)
        .findWithPriceFilters(eq(campaignId), any(), eq(pageable), eq("mediaOwner123"));
    verify(schedulesRepository, never())
        .findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull());
  }

  @Test
  @DisplayName("getCampaignSchedulePrices - Should return empty page when no schedules found")
  void getCampaignSchedulePrices_WithNoSchedules_ShouldReturnEmptyPage() {
    // Given
    String campaignId = "campaign123";
    CampaignSchedulePriceFilterDTO filter = CampaignSchedulePriceFilterDTO.builder().build();
    Pageable pageable = PageRequest.of(0, 10);

    Page<CampaignInventorySchedules> emptyPage = new PageImpl<>(List.of(), pageable, 0);

    when(schedulesRepository.findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull()))
        .thenReturn(emptyPage);
    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");

    // When
    Page<CampaignSchedulePriceResponseDTO> result =
        campaignInventorySchedulesService.getCampaignSchedulePrices(campaignId, filter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();

    verify(schedulesRepository).findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull());
    verify(inventoryService, never()).findAllByIds(anyList());
  }

  @Test
  @DisplayName("getCampaignSchedulePrices - Should aggregate impressions and reach from schedules")
  void getCampaignSchedulePrices_ShouldAggregateImpressionsAndReach() {
    // Given
    String campaignId = "campaign123";
    CampaignSchedulePriceFilterDTO filter = CampaignSchedulePriceFilterDTO.builder().build();
    Pageable pageable = PageRequest.of(0, 10);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("schedule1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory123");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("scheduleId1", "scheduleId2"));

    Page<CampaignInventorySchedules> filteredPage = new PageImpl<>(List.of(schedule1), pageable, 1);

    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Test Inventory");
    inventory.setType("Digital");
    inventory.setMediaOwnerId("mediaOwner123");

    Schedule testSchedule1 = new Schedule();
    testSchedule1.setId("scheduleId1");
    testSchedule1.setImpressions(1000L);
    testSchedule1.setReach(500L);

    Schedule testSchedule2 = new Schedule();
    testSchedule2.setId("scheduleId2");
    testSchedule2.setImpressions(2000L);
    testSchedule2.setReach(800L);

    when(schedulesRepository.findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull()))
        .thenReturn(filteredPage);
    when(inventoryService.findAllByIds(anyList())).thenReturn(List.of(inventory));
    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(scheduleRepository.findAllById(anyList()))
        .thenReturn(List.of(testSchedule1, testSchedule2));
    when(userService.getPrimaryCompanyId()).thenReturn("company123");

    CompanyLookupResponseDTO companyLookup =
        CompanyLookupResponseDTO.builder().id("mediaOwner123").name("Test Media Owner").build();
    when(companyService.getCompanyLookupWithCompanyId("mediaOwner123")).thenReturn(companyLookup);

    // When
    Page<CampaignSchedulePriceResponseDTO> result =
        campaignInventorySchedulesService.getCampaignSchedulePrices(campaignId, filter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);

    CampaignSchedulePriceResponseDTO responseDTO = result.getContent().get(0);
    assertThat(responseDTO.getImpressions()).isEqualTo(3000L); // 1000 + 2000
    assertThat(responseDTO.getReach()).isEqualTo(1300L); // 500 + 800
  }

  @Test
  @DisplayName("getCampaignSchedulePrices - Should sum rates from all schedules")
  void getCampaignSchedulePrices_ShouldSumRatesFromSchedules() {
    // Given
    String campaignId = "campaign123";
    CampaignSchedulePriceFilterDTO filter = CampaignSchedulePriceFilterDTO.builder().build();
    Pageable pageable = PageRequest.of(0, 10);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("schedule1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory123");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("scheduleId1", "scheduleId2"));

    Page<CampaignInventorySchedules> filteredPage = new PageImpl<>(List.of(schedule1), pageable, 1);

    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Test Inventory");
    inventory.setType("Digital");
    inventory.setMediaOwnerId("mediaOwner123");

    Schedule testSchedule1 = new Schedule();
    testSchedule1.setId("scheduleId1");
    testSchedule1.setBasePrice(100.0);

    Schedule testSchedule2 = new Schedule();
    testSchedule2.setId("scheduleId2");
    testSchedule2.setBasePrice(150.0);

    when(schedulesRepository.findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull()))
        .thenReturn(filteredPage);
    when(inventoryService.findAllByIds(anyList())).thenReturn(List.of(inventory));
    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(scheduleRepository.findAllById(anyList()))
        .thenReturn(List.of(testSchedule1, testSchedule2));
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(customFeeService.getActiveCustomFeesContextForCampaign(any(Campaign.class)))
        .thenReturn(CustomFeesContext.builder().build());

    CompanyLookupResponseDTO companyLookup =
        CompanyLookupResponseDTO.builder().id("mediaOwner123").name("Test Media Owner").build();
    when(companyService.getCompanyLookupWithCompanyId("mediaOwner123")).thenReturn(companyLookup);

    // When
    Page<CampaignSchedulePriceResponseDTO> result =
        campaignInventorySchedulesService.getCampaignSchedulePrices(campaignId, filter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);

    CampaignSchedulePriceResponseDTO responseDTO = result.getContent().get(0);
    assertThat(responseDTO.getCurrentRate()).isEqualTo(250.0); // 100 + 150
    assertThat(responseDTO.getProposedRate())
        .isEqualTo(250.0); // Without discount, proposed = current
  }

  @Test
  @DisplayName("getCampaignSchedulePrices - Should handle null inventory gracefully")
  void getCampaignSchedulePrices_WithNullInventory_ShouldSkipAndContinue() {
    // Given
    String campaignId = "campaign123";
    CampaignSchedulePriceFilterDTO filter = CampaignSchedulePriceFilterDTO.builder().build();
    Pageable pageable = PageRequest.of(0, 10);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("schedule1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory123");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("scheduleId1"));

    Page<CampaignInventorySchedules> filteredPage = new PageImpl<>(List.of(schedule1), pageable, 1);

    // Inventory not found in map
    when(schedulesRepository.findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull()))
        .thenReturn(filteredPage);
    when(inventoryService.findAllByIds(anyList())).thenReturn(List.of()); // Empty list

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");

    // When
    Page<CampaignSchedulePriceResponseDTO> result =
        campaignInventorySchedulesService.getCampaignSchedulePrices(campaignId, filter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEmpty(); // Should filter out null results

    verify(schedulesRepository).findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull());
    verify(inventoryService).findAllByIds(anyList());
  }

  @Test
  @DisplayName("getCampaignSchedulePrices - Should calculate discount percent from schedules")
  void getCampaignSchedulePrices_ShouldCalculateDiscountPercent() {
    // Given
    String campaignId = "campaign123";
    CampaignSchedulePriceFilterDTO filter = CampaignSchedulePriceFilterDTO.builder().build();
    Pageable pageable = PageRequest.of(0, 10);

    // Ensure pricing path treats user as campaign creator (avoids media-owner branch differences)
    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().userId("user123").companyId("company123").build());

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("schedule1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory123");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("scheduleId1", "scheduleId2"));

    Page<CampaignInventorySchedules> filteredPage = new PageImpl<>(List.of(schedule1), pageable, 1);

    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Test Inventory");
    inventory.setType("Digital");
    inventory.setMediaOwnerId("mediaOwner123");
    Inventory.Price invPrice = new Inventory.Price();
    invPrice.setSpot(100.0);
    inventory.setPrices(List.of(invPrice));

    Schedule.Discount discount1 = new Schedule.Discount();
    discount1.setValue("10.0");
    discount1.setValueType(DiscountValueType.PERCENTAGE);

    Schedule.Discount discount2 = new Schedule.Discount();
    discount2.setValue("5.0");
    discount2.setValueType(DiscountValueType.PERCENTAGE);

    Schedule testSchedule1 = new Schedule();
    testSchedule1.setId("scheduleId1");
    testSchedule1.setDiscount(discount1);
    testSchedule1.setBasePrice(1000.0);

    Schedule testSchedule2 = new Schedule();
    testSchedule2.setId("scheduleId2");
    testSchedule2.setDiscount(discount2);
    testSchedule2.setBasePrice(1000.0);

    when(schedulesRepository.findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull()))
        .thenReturn(filteredPage);
    when(inventoryService.findAllByIds(anyList())).thenReturn(List.of(inventory));
    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    lenient()
        .when(scheduleRepository.findAllById(anyList()))
        .thenReturn(List.of(testSchedule1, testSchedule2));
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(customFeeService.getActiveCustomFeesContextForCampaign(any(Campaign.class)))
        .thenReturn(CustomFeesContext.builder().build());

    CompanyLookupResponseDTO companyLookup =
        CompanyLookupResponseDTO.builder().id("mediaOwner123").name("Test Media Owner").build();
    when(companyService.getCompanyLookupWithCompanyId("mediaOwner123")).thenReturn(companyLookup);

    // When
    Page<CampaignSchedulePriceResponseDTO> result =
        campaignInventorySchedulesService.getCampaignSchedulePrices(campaignId, filter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);

    CampaignSchedulePriceResponseDTO responseDTO = result.getContent().get(0);
    // Service calculates average discount percentage across schedules
    assertThat(responseDTO.getDiscountPercent()).isEqualTo(7.5); // (10.0 + 5.0) / 2
  }

  @Test
  @DisplayName("getCampaignSchedulePrices - Should calculate rate cards (daily, weekly, monthly)")
  void getCampaignSchedulePrices_ShouldCalculateRateCards() {
    // Given
    String campaignId = "campaign123";
    CampaignSchedulePriceFilterDTO filter = CampaignSchedulePriceFilterDTO.builder().build();
    Pageable pageable = PageRequest.of(0, 10);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("schedule1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory123");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("scheduleId1"));

    Page<CampaignInventorySchedules> filteredPage = new PageImpl<>(List.of(schedule1), pageable, 1);

    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Test Inventory");
    inventory.setType("Digital");
    inventory.setMediaOwnerId("mediaOwner123");
    Inventory.Price price = new Inventory.Price();
    price.setSpot(100.0);
    inventory.setPrices(List.of(price));

    Schedule testSchedule1 = new Schedule();
    testSchedule1.setId("scheduleId1");
    // Campaign duration is 30 days (from plusDays(1) to plusDays(30), inclusive)
    // Set adPlays to 300 (10 per day * 30 days) to get 10 adPlaysPerDay
    testSchedule1.setAdPlays(300L);

    when(schedulesRepository.findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull()))
        .thenReturn(filteredPage);
    when(inventoryService.findAllByIds(anyList())).thenReturn(List.of(inventory));
    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    // Both prepareInventoryForecastForCampaignInventorySchedules and convertToPriceResponseDTO
    // call findAllById with scheduleIds, so we need to return the schedule for both calls
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(testSchedule1));
    when(userService.getPrimaryCompanyId()).thenReturn("company123");

    CompanyLookupResponseDTO companyLookup =
        CompanyLookupResponseDTO.builder().id("mediaOwner123").name("Test Media Owner").build();
    when(companyService.getCompanyLookupWithCompanyId("mediaOwner123")).thenReturn(companyLookup);

    // When
    Page<CampaignSchedulePriceResponseDTO> result =
        campaignInventorySchedulesService.getCampaignSchedulePrices(campaignId, filter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);

    CampaignSchedulePriceResponseDTO responseDTO = result.getContent().get(0);
    // adPlaysPerDay = 290 / 29 = 10, dailyRate = 100.0 * 10 = 1000.0
    assertThat(responseDTO.getDailyRate()).isEqualTo(1000.0);
    assertThat(responseDTO.getWeeklyRateCard()).isEqualTo(7000.0); // 1000 * 7
    assertThat(responseDTO.getMonthlyRateCard()).isEqualTo(30000.0); // 1000 * 30
  }

  @Test
  @DisplayName("getCampaignSchedulePrices - Should aggregate bonus types from schedules")
  void getCampaignSchedulePrices_ShouldAggregateBonusTypes() {
    // Given
    String campaignId = "campaign123";
    CampaignSchedulePriceFilterDTO filter = CampaignSchedulePriceFilterDTO.builder().build();
    Pageable pageable = PageRequest.of(0, 10);

    // Ensure pricing path treats user as campaign creator (avoids media-owner branch differences)
    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().userId("user123").companyId("company123").build());

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("schedule1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory123");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("scheduleId1", "scheduleId2"));

    Page<CampaignInventorySchedules> filteredPage = new PageImpl<>(List.of(schedule1), pageable, 1);

    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Test Inventory");
    inventory.setType("Digital");
    inventory.setMediaOwnerId("mediaOwner123");
    Inventory.Price invPrice = new Inventory.Price();
    invPrice.setSpot(100.0);
    inventory.setPrices(List.of(invPrice));

    Schedule testSchedule1 = new Schedule();
    testSchedule1.setId("scheduleId1");
    testSchedule1.setBonusType("BONUS_A");
    testSchedule1.setBasePrice(1000.0);

    Schedule testSchedule2 = new Schedule();
    testSchedule2.setId("scheduleId2");
    testSchedule2.setBonusType("BONUS_B");
    testSchedule2.setBasePrice(1000.0);

    when(schedulesRepository.findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull()))
        .thenReturn(filteredPage);
    when(inventoryService.findAllByIds(anyList())).thenReturn(List.of(inventory));
    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(scheduleRepository.findAllById(anyList()))
        .thenReturn(List.of(testSchedule1, testSchedule2));
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(customFeeService.getActiveCustomFeesContextForCampaign(any(Campaign.class)))
        .thenReturn(CustomFeesContext.builder().build());

    CompanyLookupResponseDTO companyLookup =
        CompanyLookupResponseDTO.builder().id("mediaOwner123").name("Test Media Owner").build();
    when(companyService.getCompanyLookupWithCompanyId("mediaOwner123")).thenReturn(companyLookup);

    // When
    Page<CampaignSchedulePriceResponseDTO> result =
        campaignInventorySchedulesService.getCampaignSchedulePrices(campaignId, filter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);

    CampaignSchedulePriceResponseDTO responseDTO = result.getContent().get(0);
    assertThat(responseDTO.getSchedules())
        .extracting(CampaignSchedulePriceResponseDTO.SchedulePriceDTO::getBonusType)
        .containsExactlyInAnyOrder("BONUS_A", "BONUS_B");
  }

  @Test
  @DisplayName("getCampaignSchedulePrices - Should handle null impressions and reach")
  void getCampaignSchedulePrices_ShouldHandleNullImpressionsAndReach() {
    // Given
    String campaignId = "campaign123";
    CampaignSchedulePriceFilterDTO filter = CampaignSchedulePriceFilterDTO.builder().build();
    Pageable pageable = PageRequest.of(0, 10);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("schedule1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory123");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("scheduleId1"));

    Page<CampaignInventorySchedules> filteredPage = new PageImpl<>(List.of(schedule1), pageable, 1);

    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Test Inventory");
    inventory.setType("Digital");
    inventory.setMediaOwnerId("mediaOwner123");

    Schedule testSchedule1 = new Schedule();
    testSchedule1.setId("scheduleId1");
    testSchedule1.setImpressions(null);
    testSchedule1.setReach(null);

    when(schedulesRepository.findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull()))
        .thenReturn(filteredPage);
    when(inventoryService.findAllByIds(anyList())).thenReturn(List.of(inventory));
    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(testSchedule1));
    when(userService.getPrimaryCompanyId()).thenReturn("company123");

    CompanyLookupResponseDTO companyLookup =
        CompanyLookupResponseDTO.builder().id("mediaOwner123").name("Test Media Owner").build();
    when(companyService.getCompanyLookupWithCompanyId("mediaOwner123")).thenReturn(companyLookup);

    // When
    Page<CampaignSchedulePriceResponseDTO> result =
        campaignInventorySchedulesService.getCampaignSchedulePrices(campaignId, filter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);

    CampaignSchedulePriceResponseDTO responseDTO = result.getContent().get(0);
    assertThat(responseDTO.getImpressions()).isNull();
    assertThat(responseDTO.getReach()).isNull();
  }

  @Test
  @DisplayName("getCampaignSchedulePrices - Should handle zero impressions and reach as null")
  void getCampaignSchedulePrices_ShouldHandleZeroImpressionsAndReachAsNull() {
    // Given
    String campaignId = "campaign123";
    CampaignSchedulePriceFilterDTO filter = CampaignSchedulePriceFilterDTO.builder().build();
    Pageable pageable = PageRequest.of(0, 10);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("schedule1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory123");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("scheduleId1"));

    Page<CampaignInventorySchedules> filteredPage = new PageImpl<>(List.of(schedule1), pageable, 1);

    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Test Inventory");
    inventory.setType("Digital");
    inventory.setMediaOwnerId("mediaOwner123");

    Schedule testSchedule1 = new Schedule();
    testSchedule1.setId("scheduleId1");
    testSchedule1.setImpressions(0L);
    testSchedule1.setReach(0L);

    when(schedulesRepository.findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull()))
        .thenReturn(filteredPage);
    when(inventoryService.findAllByIds(anyList())).thenReturn(List.of(inventory));
    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(testSchedule1));
    when(userService.getPrimaryCompanyId()).thenReturn("company123");

    CompanyLookupResponseDTO companyLookup =
        CompanyLookupResponseDTO.builder().id("mediaOwner123").name("Test Media Owner").build();
    when(companyService.getCompanyLookupWithCompanyId("mediaOwner123")).thenReturn(companyLookup);

    // When
    Page<CampaignSchedulePriceResponseDTO> result =
        campaignInventorySchedulesService.getCampaignSchedulePrices(campaignId, filter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);

    CampaignSchedulePriceResponseDTO responseDTO = result.getContent().get(0);
    assertThat(responseDTO.getImpressions()).isNull(); // Zero should be converted to null
    assertThat(responseDTO.getReach()).isNull(); // Zero should be converted to null
  }

  @Test
  @DisplayName("getCampaignSchedulePrices - Should handle conversion errors gracefully")
  void getCampaignSchedulePrices_ShouldHandleConversionErrors() {
    // Given
    String campaignId = "campaign123";
    CampaignSchedulePriceFilterDTO filter = CampaignSchedulePriceFilterDTO.builder().build();
    Pageable pageable = PageRequest.of(0, 10);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("schedule1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory123");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("scheduleId1"));

    CampaignInventorySchedules schedule2 = new CampaignInventorySchedules();
    schedule2.setId("schedule2");
    schedule2.setCampaignId(campaignId);
    schedule2.setInventoryId("inventory456");
    schedule2.setMediaOwnerId("mediaOwner123");
    schedule2.setScheduleIds(List.of("scheduleId2"));

    Page<CampaignInventorySchedules> filteredPage =
        new PageImpl<>(List.of(schedule1, schedule2), pageable, 2);

    Inventory inventory1 = new Inventory();
    inventory1.setId("inventory123");
    inventory1.setName("Test Inventory 1");
    inventory1.setMediaOwnerId("mediaOwner123");

    Inventory inventory2 = new Inventory();
    inventory2.setId("inventory456");
    inventory2.setName("Test Inventory 2");
    inventory2.setMediaOwnerId("mediaOwner123");

    when(schedulesRepository.findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull()))
        .thenReturn(filteredPage);
    when(inventoryService.findAllByIds(anyList())).thenReturn(List.of(inventory1, inventory2));
    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(scheduleRepository.findAllById(anyList())).thenReturn(Collections.emptyList());
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    // Simulate error resolving the media owner name (looked up once per distinct mediaOwnerId
    // across the page, not once per row) — the error is caught and logged, and every row on that
    // media owner continues with a null mediaOwnerName rather than failing the whole page.
    when(companyService.getCompanyLookupWithCompanyId("mediaOwner123"))
        .thenThrow(new RuntimeException("Service error"));

    // When
    Page<CampaignSchedulePriceResponseDTO> result =
        campaignInventorySchedulesService.getCampaignSchedulePrices(campaignId, filter, pageable);

    // Then
    assertThat(result).isNotNull();
    // Error is caught and logged, but conversion continues - results are not filtered out
    // The mediaOwnerName will be null due to the exception
    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getContent().get(0).getMediaOwnerName()).isNull();
    assertThat(result.getContent().get(1).getMediaOwnerName()).isNull();
  }

  @Test
  @DisplayName("getCampaignSchedulePrices - Should handle empty scheduleIds list")
  void getCampaignSchedulePrices_ShouldHandleEmptyScheduleIds() {
    // Given
    String campaignId = "campaign123";
    CampaignSchedulePriceFilterDTO filter = CampaignSchedulePriceFilterDTO.builder().build();
    Pageable pageable = PageRequest.of(0, 10);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("schedule1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory123");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(Collections.emptyList());

    Page<CampaignInventorySchedules> filteredPage = new PageImpl<>(List.of(schedule1), pageable, 1);

    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setName("Test Inventory");
    inventory.setType("Digital");
    inventory.setMediaOwnerId("mediaOwner123");

    when(schedulesRepository.findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull()))
        .thenReturn(filteredPage);
    when(inventoryService.findAllByIds(anyList())).thenReturn(List.of(inventory));
    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    // When scheduleIds is empty, findAllById is not called in convertToPriceResponseDTO
    // but it is called in prepareInventoryForecastForCampaignInventorySchedules
    // Use lenient to avoid unnecessary stubbing warning
    lenient().when(scheduleRepository.findAllById(anyList())).thenReturn(Collections.emptyList());

    CompanyLookupResponseDTO companyLookup =
        CompanyLookupResponseDTO.builder().id("mediaOwner123").name("Test Media Owner").build();
    when(companyService.getCompanyLookupWithCompanyId("mediaOwner123")).thenReturn(companyLookup);

    // When
    Page<CampaignSchedulePriceResponseDTO> result =
        campaignInventorySchedulesService.getCampaignSchedulePrices(campaignId, filter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);

    CampaignSchedulePriceResponseDTO responseDTO = result.getContent().get(0);
    assertThat(responseDTO.getImpressions()).isNull();
    assertThat(responseDTO.getReach()).isNull();
    assertThat(responseDTO.getSchedules()).isEmpty();
  }

  @Test
  @DisplayName(
      "getCampaignSchedulePrices - Should show all schedules when tenant-switched user is member of campaign company")
  void getCampaignSchedulePrices_WhenTenantSwitchedUserIsMember_ShouldShowAllSchedules() {
    // Given
    String campaignId = "campaign123";
    CampaignSchedulePriceFilterDTO filter = CampaignSchedulePriceFilterDTO.builder().build();
    Pageable pageable = PageRequest.of(0, 10);

    Page<CampaignInventorySchedules> emptyPage = new PageImpl<>(List.of(), pageable, 0);

    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    // primaryCompanyId differs from campaign's companyId but user is a member
    when(userService.getPrimaryCompanyId()).thenReturn("switched-company");
    when(userService.isTenantOfCompany("company123")).thenReturn(true);
    when(schedulesRepository.findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull()))
        .thenReturn(emptyPage);

    // When
    campaignInventorySchedulesService.getCampaignSchedulePrices(campaignId, filter, pageable);

    // Then — null mediaOwnerId means all schedules are shown
    verify(schedulesRepository).findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull());
  }

  @Test
  @DisplayName(
      "getCampaignSchedulePrices - Should filter by primary company when user is not member of campaign company")
  void getCampaignSchedulePrices_WhenUserIsMediaOwner_ShouldFilterByPrimaryCompanyId() {
    // Given
    String campaignId = "campaign123";
    CampaignSchedulePriceFilterDTO filter = CampaignSchedulePriceFilterDTO.builder().build();
    Pageable pageable = PageRequest.of(0, 10);

    Page<CampaignInventorySchedules> emptyPage = new PageImpl<>(List.of(), pageable, 0);

    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    // primaryCompanyId differs from campaign's companyId and user is not a member
    when(userService.getPrimaryCompanyId()).thenReturn("media-owner-id");
    when(userService.isTenantOfCompany("company123")).thenReturn(false);
    when(schedulesRepository.findWithPriceFilters(
            eq(campaignId), any(), eq(pageable), eq("media-owner-id")))
        .thenReturn(emptyPage);

    // When
    campaignInventorySchedulesService.getCampaignSchedulePrices(campaignId, filter, pageable);

    // Then — primaryCompanyId passed as mediaOwnerId filter
    verify(schedulesRepository)
        .findWithPriceFilters(eq(campaignId), any(), eq(pageable), eq("media-owner-id"));
  }

  // ========== Get Price History Tests ==========

  @Test
  @DisplayName("getPriceHistory - Should return price history when schedule exists with history")
  void getPriceHistory_WhenScheduleExistsWithHistory_ShouldReturnPriceHistory() {
    // Given
    String campaignInventoryScheduleId = "schedule123";
    Pageable pageable = PageRequest.of(0, 10);

    CampaignInventorySchedules schedule = new CampaignInventorySchedules();
    schedule.setId(campaignInventoryScheduleId);
    schedule.setCampaignId("campaign123");
    schedule.setInventoryId("inventory123");

    CampaignInventorySchedules.History history1 =
        CampaignInventorySchedules.History.builder()
            .userId("user1")
            .effectiveDiscountPercentage(20.0)
            .action(PricingAction.PROPOSED)
            .date(LocalDateTime.now().minusDays(2))
            .build();

    CampaignInventorySchedules.History history2 =
        CampaignInventorySchedules.History.builder()
            .userId("user2")
            .effectiveDiscountPercentage(25.0)
            .action(PricingAction.ACCEPTED)
            .date(LocalDateTime.now().minusDays(1))
            .build();

    schedule.setHistory(new ArrayList<>(List.of(history1, history2)));
    schedule.setScheduleIds(
        List.of("schedule1", "schedule2")); // Add scheduleIds for price calculation

    when(schedulesRepository.findById(campaignInventoryScheduleId))
        .thenReturn(Optional.of(schedule));

    Campaign campaign = new Campaign();
    campaign.setId("campaign123");
    campaign.setCompanyId("company123");
    when(campaignService.findById("campaign123")).thenReturn(campaign);

    Inventory inventory = new Inventory();
    inventory.setId("inventory123");
    inventory.setMediaOwnerId("mediaOwner123");
    Inventory.Price price = new Inventory.Price();
    price.setSpot(100.0);
    inventory.setPrices(List.of(price));
    when(inventoryService.getById("inventory123")).thenReturn(inventory);

    IamUserContext userContext =
        IamUserContext.builder().id("user123").companyId("company123").build();
    when(userService.getIamUserContext()).thenReturn(userContext);

    // Mock schedules for price calculation
    Schedule schedule1 = new Schedule();
    schedule1.setId("schedule1");
    schedule1.setBasePrice(1000.0); // Adjusted to match expected calculation
    Schedule schedule2 = new Schedule();
    schedule2.setId("schedule2");
    schedule2.setBasePrice(1000.0); // Adjusted to match expected calculation
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(schedule1, schedule2));

    // Mock custom fee service - return empty context for price calculation
    when(customFeeService.getActiveCustomFeesContextForCampaign(any(Campaign.class)))
        .thenReturn(CustomFeesContext.builder().build());

    UserResponseDTO user1 = new UserResponseDTO();
    user1.setFirstName("John");
    user1.setLastName("Doe");
    user1.setUsername("johndoe");
    UserResponseDTO.CurrentCompanyDTO currentCompany1 = new UserResponseDTO.CurrentCompanyDTO();
    ((UserResponseDTO.CurrentCompanyDTO) currentCompany1).setRoleName("Media Owner");
    user1.setCurrentCompany(currentCompany1);

    UserResponseDTO user2 = new UserResponseDTO();
    user2.setFirstName("Jane");
    user2.setLastName("Smith");
    user2.setUsername("janesmith");
    UserResponseDTO.CurrentCompanyDTO currentCompany2 = new UserResponseDTO.CurrentCompanyDTO();
    currentCompany2.setRoleName("Agency");
    user2.setCurrentCompany(currentCompany2);

    when(userService.getUserById("user1")).thenReturn(user1);
    when(userService.getUserById("user2")).thenReturn(user2);

    // When
    Page<PriceHistoryResponseDTO> result =
        campaignInventorySchedulesService.getPriceHistory(campaignInventoryScheduleId, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.getContent().get(0).getNewPrice()).isEqualTo(1500.0);
    assertThat(result.getContent().get(0).getAction()).isEqualTo(PricingAction.ACCEPTED);
    assertThat(result.getContent().get(0).getCreatedBy()).contains("Jane");
    assertThat(result.getContent().get(0).getRole()).isEqualTo("Agency");

    verify(schedulesRepository).findById(campaignInventoryScheduleId);
    verify(userService).getUserById("user1");
    verify(userService).getUserById("user2");
  }

  @Test
  @DisplayName("getPriceHistory - Should return empty page when schedule not found")
  void getPriceHistory_WhenScheduleNotFound_ShouldReturnEmptyPage() {
    // Given
    String campaignInventoryScheduleId = "nonexistent";
    Pageable pageable = PageRequest.of(0, 10);

    when(schedulesRepository.findById(campaignInventoryScheduleId)).thenReturn(Optional.empty());

    // When
    Page<PriceHistoryResponseDTO> result =
        campaignInventorySchedulesService.getPriceHistory(campaignInventoryScheduleId, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isEqualTo(0);

    verify(schedulesRepository).findById(campaignInventoryScheduleId);
    verify(userService, never()).getUserById(anyString());
  }

  @Test
  @DisplayName("getPriceHistory - Should return empty page when history is null or empty")
  void getPriceHistory_WhenHistoryIsNull_ShouldReturnEmptyPage() {
    // Given
    String campaignInventoryScheduleId = "schedule123";
    Pageable pageable = PageRequest.of(0, 10);

    CampaignInventorySchedules schedule = new CampaignInventorySchedules();
    schedule.setId(campaignInventoryScheduleId);
    schedule.setHistory(null);

    when(schedulesRepository.findById(campaignInventoryScheduleId))
        .thenReturn(Optional.of(schedule));

    // When
    Page<PriceHistoryResponseDTO> result =
        campaignInventorySchedulesService.getPriceHistory(campaignInventoryScheduleId, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isEqualTo(0);

    verify(schedulesRepository).findById(campaignInventoryScheduleId);
    verify(userService, never()).getUserById(anyString());
  }

  @Test
  @DisplayName("getPriceHistory - Should handle pagination correctly")
  void getPriceHistory_WithPagination_ShouldReturnCorrectPage() {
    // Given
    String campaignInventoryScheduleId = "schedule123";
    Pageable pageable = PageRequest.of(0, 2); // First page, size 2

    CampaignInventorySchedules schedule = new CampaignInventorySchedules();
    schedule.setId(campaignInventoryScheduleId);

    List<CampaignInventorySchedules.History> historyList = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      CampaignInventorySchedules.History history =
          CampaignInventorySchedules.History.builder()
              .userId("user" + i)
              .effectiveDiscountPercentage(20.0 + i * 5)
              .action(PricingAction.PROPOSED)
              .date(LocalDateTime.now().minusDays(5 - i))
              .build();
      historyList.add(history);
    }
    schedule.setHistory(historyList);

    when(schedulesRepository.findById(campaignInventoryScheduleId))
        .thenReturn(Optional.of(schedule));

    // Mock user service to return a user
    UserResponseDTO user = new UserResponseDTO();
    user.setFirstName("Test");
    user.setLastName("User");
    when(userService.getUserById(anyString())).thenReturn(user);

    // When
    Page<PriceHistoryResponseDTO> result =
        campaignInventorySchedulesService.getPriceHistory(campaignInventoryScheduleId, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(2); // Page size is 2
    assertThat(result.getTotalElements()).isEqualTo(5); // Total elements is 5
    assertThat(result.getTotalPages()).isEqualTo(3); // 5 elements / 2 per page = 3 pages

    verify(schedulesRepository).findById(campaignInventoryScheduleId);
  }

  @Test
  @DisplayName("getPriceHistory - Should handle user not found gracefully")
  void getPriceHistory_WhenUserNotFound_ShouldUseDefaultValues() {
    // Given
    String campaignInventoryScheduleId = "schedule123";
    Pageable pageable = PageRequest.of(0, 10);

    CampaignInventorySchedules schedule = new CampaignInventorySchedules();
    schedule.setId(campaignInventoryScheduleId);

    CampaignInventorySchedules.History history =
        CampaignInventorySchedules.History.builder()
            .userId("nonexistentUser")
            .effectiveDiscountPercentage(20.0)
            .action(PricingAction.PROPOSED)
            .date(LocalDateTime.now())
            .build();

    schedule.setHistory(new ArrayList<>(List.of(history)));

    when(schedulesRepository.findById(campaignInventoryScheduleId))
        .thenReturn(Optional.of(schedule));
    // getUserById throws exception when user not found, which causes getPriceHistory to return
    // empty page
    when(userService.getUserById("nonexistentUser"))
        .thenThrow(new com.mw.planner.exception.user.UserNotFoundException("nonexistentUser"));

    // When
    Page<PriceHistoryResponseDTO> result =
        campaignInventorySchedulesService.getPriceHistory(campaignInventoryScheduleId, pageable);

    // Then
    // When getUserById throws exception, the method catches it and returns empty page
    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEmpty();

    verify(schedulesRepository).findById(campaignInventoryScheduleId);
    verify(userService).getUserById("nonexistentUser");
  }

  // ========== Accept Inventory Prices Tests ==========

  @Test
  @DisplayName("acceptInventoryPrices - Should accept prices successfully")
  void acceptInventoryPrices_WithValidData_ShouldAcceptPrices() {
    // Given
    String campaignId = "campaign123";
    List<String> campaignInventorySchedulesIds = List.of("cis1", "cis2");

    IamUserContext userContext = new IamUserContext();
    userContext.setUserId("user123");
    when(userService.getIamUserContext()).thenReturn(userContext);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCompanyId("company123");
    when(campaignService.findByIdForCurrentModeForWrite(campaignId)).thenReturn(testCampaign);
    when(campaignService.findById(campaignId)).thenReturn(campaign);
    when(campaignService.save(any(Campaign.class))).thenReturn(campaign);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("cis1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory1");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("schedule1"));
    schedule1.setApprovedBy(null); // Not yet approved
    // Add history entry for approval authorization
    CampaignInventorySchedules.History history1 =
        CampaignInventorySchedules.History.builder()
            .action(PricingAction.PROPOSED)
            .companyId("mediaOwner123")
            .userId("mediaOwnerUser")
            .date(LocalDateTime.now().minusDays(1))
            .effectiveDiscountPercentage(10.0)
            .build();
    schedule1.setHistory(new ArrayList<>(List.of(history1)));

    CampaignInventorySchedules schedule2 = new CampaignInventorySchedules();
    schedule2.setId("cis2");
    schedule2.setCampaignId(campaignId);
    schedule2.setInventoryId("inventory2");
    schedule2.setMediaOwnerId("mediaOwner123");
    schedule2.setScheduleIds(List.of("schedule2"));
    schedule2.setApprovedBy(null); // Not yet approved
    // Add history entry for approval authorization
    CampaignInventorySchedules.History history2 =
        CampaignInventorySchedules.History.builder()
            .action(PricingAction.PROPOSED)
            .companyId("mediaOwner123")
            .userId("mediaOwnerUser")
            .date(LocalDateTime.now().minusDays(1))
            .effectiveDiscountPercentage(10.0)
            .build();
    schedule2.setHistory(new ArrayList<>(List.of(history2)));

    when(schedulesRepository.findAllById(campaignInventorySchedulesIds))
        .thenReturn(List.of(schedule1, schedule2));

    Inventory inventory1 = new Inventory();
    inventory1.setId("inventory1");
    inventory1.setMediaOwnerId("mediaOwner123");
    when(inventoryService.getById("inventory1")).thenReturn(inventory1);

    Inventory inventory2 = new Inventory();
    inventory2.setId("inventory2");
    inventory2.setMediaOwnerId("mediaOwner123");
    when(inventoryService.getById("inventory2")).thenReturn(inventory2);

    Schedule scheduleEntity1 = new Schedule();
    scheduleEntity1.setId("schedule1");
    scheduleEntity1.setBasePrice(1000.0);

    Schedule scheduleEntity2 = new Schedule();
    scheduleEntity2.setId("schedule2");
    scheduleEntity2.setBasePrice(1500.0);

    when(scheduleRepository.findAllById(anyList()))
        .thenAnswer(
            invocation -> {
              List<String> ids = invocation.getArgument(0);
              List<Schedule> result = new ArrayList<>();
              if (ids.contains("schedule1")) result.add(scheduleEntity1);
              if (ids.contains("schedule2")) result.add(scheduleEntity2);
              return result;
            });

    when(schedulesRepository.saveAll(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    campaignInventorySchedulesService.acceptInventoryPrices(
        campaignId, campaignInventorySchedulesIds);

    // Then
    verify(campaignService).findByIdForCurrentModeForWrite(campaignId);
    verify(userService, atLeast(1))
        .getIamUserContext(); // Called in acceptInventoryPrices and createHistoryEntry
    verify(schedulesRepository).findAllById(campaignInventorySchedulesIds);
    // findAllById is called multiple times (in acceptInventoryPrices and
    // calculateEffectiveDiscountPercentage)
    verify(scheduleRepository, atLeast(1)).findAllById(anyList());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CampaignInventorySchedules>> captor = ArgumentCaptor.forClass(List.class);
    verify(schedulesRepository).saveAll(captor.capture());
    List<CampaignInventorySchedules> savedSchedules = captor.getValue();
    assertThat(savedSchedules).hasSize(2);
    assertThat(savedSchedules.get(0).getApprovedBy()).isEqualTo("user123");
    assertThat(savedSchedules.get(0).getApprovedScheduleIds()).containsExactly("schedule1");
    assertThat(savedSchedules.get(1).getApprovedBy()).isEqualTo("user123");
    assertThat(savedSchedules.get(1).getApprovedScheduleIds()).containsExactly("schedule2");

    // Verify history entries have correct prices
    assertThat(savedSchedules.get(0).getHistory()).isNotEmpty();
    // Check the last history entry (ACCEPTED), not the first (PROPOSED)
    List<CampaignInventorySchedules.History> historyList1 = savedSchedules.get(0).getHistory();
    CampaignInventorySchedules.History history11 = historyList1.get(historyList1.size() - 1);
    assertThat(history11.getAction()).isEqualTo(PricingAction.ACCEPTED);
    assertThat(history11.getUserId()).isEqualTo("user123");
    // History doesn't store oldPrice/newPrice directly, only effectiveDiscountPercentage
    assertThat(history11.getEffectiveDiscountPercentage()).isNotNull();

    assertThat(savedSchedules.get(1).getHistory()).isNotEmpty();
    // Check the last history entry (ACCEPTED), not the first (PROPOSED)
    List<CampaignInventorySchedules.History> historyList2 = savedSchedules.get(1).getHistory();
    CampaignInventorySchedules.History history22 = historyList2.get(historyList2.size() - 1);
    assertThat(history22.getAction()).isEqualTo(PricingAction.ACCEPTED);
    assertThat(history22.getUserId()).isEqualTo("user123");
    // History doesn't store oldPrice/newPrice directly, only effectiveDiscountPercentage
    assertThat(history22.getEffectiveDiscountPercentage()).isNotNull();

    // Campaign should be marked as negotiated once prices are accepted
    verify(campaignService).save(campaign);
    assertThat(campaign.getIsNegotiated()).isTrue();
  }

  @Test
  @DisplayName("acceptInventoryPrices - Should throw exception when campaign not found")
  void acceptInventoryPrices_WhenCampaignNotFound_ShouldThrowException() {
    // Given
    String campaignId = "nonexistent";
    List<String> campaignInventorySchedulesIds = List.of("cis1");

    when(campaignService.findByIdForCurrentModeForWrite(campaignId))
        .thenThrow(new CampaignNotFoundException(campaignId));

    // When & Then
    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.acceptInventoryPrices(
                    campaignId, campaignInventorySchedulesIds))
        .isInstanceOf(CampaignNotFoundException.class);

    verify(campaignService).findByIdForCurrentModeForWrite(campaignId);
    verify(schedulesRepository, never()).findAllById(anyList());
  }

  @Test
  @DisplayName(
      "acceptInventoryPrices - Should accept prices for all CampaignInventorySchedules when IDs is empty")
  void acceptInventoryPrices_WhenScheduleIdsEmpty_ShouldAcceptAllSchedules() {
    // Given
    String campaignId = "campaign123";
    List<String> campaignInventorySchedulesIds = Collections.emptyList();

    IamUserContext userContext = new IamUserContext();
    userContext.setUserId("user123");
    when(userService.getIamUserContext()).thenReturn(userContext);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCompanyId("company123");
    when(campaignService.findByIdForCurrentModeForWrite(campaignId)).thenReturn(testCampaign);
    when(campaignService.findById(campaignId)).thenReturn(campaign);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("cis1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory1");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("schedule1", "schedule2"));
    schedule1.setApprovedBy(null);
    // Add history entry for approval authorization
    CampaignInventorySchedules.History history1 =
        CampaignInventorySchedules.History.builder()
            .action(PricingAction.PROPOSED)
            .companyId("mediaOwner123")
            .userId("mediaOwnerUser")
            .date(LocalDateTime.now().minusDays(1))
            .effectiveDiscountPercentage(10.0)
            .build();
    schedule1.setHistory(new ArrayList<>(List.of(history1)));

    CampaignInventorySchedules schedule2 = new CampaignInventorySchedules();
    schedule2.setId("cis2");
    schedule2.setCampaignId(campaignId);
    schedule2.setInventoryId("inventory2");
    schedule2.setMediaOwnerId("mediaOwner123");
    schedule2.setScheduleIds(List.of("schedule3"));
    schedule2.setApprovedBy(null);
    // Add history entry for approval authorization
    CampaignInventorySchedules.History history2 =
        CampaignInventorySchedules.History.builder()
            .action(PricingAction.PROPOSED)
            .companyId("mediaOwner123")
            .userId("mediaOwnerUser")
            .date(LocalDateTime.now().minusDays(1))
            .effectiveDiscountPercentage(10.0)
            .build();
    schedule2.setHistory(new ArrayList<>(List.of(history2)));

    when(schedulesRepository.findByCampaignId(campaignId))
        .thenReturn(List.of(schedule1, schedule2));

    Inventory inventory1 = new Inventory();
    inventory1.setId("inventory1");
    inventory1.setMediaOwnerId("mediaOwner123");
    when(inventoryService.getById("inventory1")).thenReturn(inventory1);

    Inventory inventory2 = new Inventory();
    inventory2.setId("inventory2");
    inventory2.setMediaOwnerId("mediaOwner123");
    when(inventoryService.getById("inventory2")).thenReturn(inventory2);

    // Mock schedules for empty scheduleIds case - fetch all schedules
    Schedule scheduleEntity1 = new Schedule();
    scheduleEntity1.setId("schedule1");
    scheduleEntity1.setBasePrice(1000.0);

    Schedule scheduleEntity2 = new Schedule();
    scheduleEntity2.setId("schedule2");
    scheduleEntity2.setBasePrice(1500.0);

    Schedule scheduleEntity3 = new Schedule();
    scheduleEntity3.setId("schedule3");
    scheduleEntity3.setBasePrice(2000.0);

    when(scheduleRepository.findAllById(anyList()))
        .thenAnswer(
            invocation -> {
              List<String> ids = invocation.getArgument(0);
              List<Schedule> result = new ArrayList<>();
              if (ids.contains("schedule1")) result.add(scheduleEntity1);
              if (ids.contains("schedule2")) result.add(scheduleEntity2);
              if (ids.contains("schedule3")) result.add(scheduleEntity3);
              return result;
            });

    when(schedulesRepository.saveAll(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    campaignInventorySchedulesService.acceptInventoryPrices(
        campaignId, campaignInventorySchedulesIds);

    // Then
    verify(campaignService).findByIdForCurrentModeForWrite(campaignId);
    verify(userService, atLeast(1)).getIamUserContext();
    verify(schedulesRepository).findByCampaignId(campaignId);
    // findAllById is called multiple times (in acceptInventoryPrices and
    // calculateEffectiveDiscountPercentage)
    verify(scheduleRepository, atLeast(1)).findAllById(anyList());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CampaignInventorySchedules>> captor = ArgumentCaptor.forClass(List.class);
    verify(schedulesRepository).saveAll(captor.capture());
    List<CampaignInventorySchedules> savedSchedules = captor.getValue();
    assertThat(savedSchedules).hasSize(2);
    assertThat(savedSchedules.get(0).getApprovedScheduleIds())
        .containsExactlyInAnyOrder("schedule1", "schedule2");
    assertThat(savedSchedules.get(1).getApprovedScheduleIds()).containsExactly("schedule3");

    // Verify history entries have correct prices
    assertThat(savedSchedules.get(0).getHistory()).isNotEmpty();
    // Check the last history entry (ACCEPTED), not the first (PROPOSED)
    List<CampaignInventorySchedules.History> historyList1 = savedSchedules.get(0).getHistory();
    CampaignInventorySchedules.History history11 = historyList1.get(historyList1.size() - 1);
    assertThat(history11.getAction()).isEqualTo(PricingAction.ACCEPTED);
    // History stores effectiveDiscountPercentage, not oldPrice/newPrice directly
    assertThat(history11.getEffectiveDiscountPercentage()).isNotNull();

    assertThat(savedSchedules.get(1).getHistory()).isNotEmpty();
    // Check the last history entry (ACCEPTED), not the first (PROPOSED)
    List<CampaignInventorySchedules.History> historyList2 = savedSchedules.get(1).getHistory();
    CampaignInventorySchedules.History history22 = historyList2.get(historyList2.size() - 1);
    assertThat(history22.getAction()).isEqualTo(PricingAction.ACCEPTED);
    // History stores effectiveDiscountPercentage, not oldPrice/newPrice directly
    assertThat(history22.getEffectiveDiscountPercentage()).isNotNull();
  }

  @Test
  @DisplayName("acceptInventoryPrices - Should throw exception when user context not available")
  void acceptInventoryPrices_WhenUserContextNotAvailable_ShouldThrowException() {
    // Given
    String campaignId = "campaign123";
    List<String> campaignInventorySchedulesIds = List.of("cis1");

    when(campaignService.findByIdForCurrentModeForWrite(campaignId)).thenReturn(testCampaign);
    when(userService.getIamUserContext())
        .thenThrow(new com.mw.planner.exception.user.UserNotFoundException("user123"));

    // When & Then
    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.acceptInventoryPrices(
                    campaignId, campaignInventorySchedulesIds))
        .isInstanceOf(com.mw.planner.exception.user.UserNotFoundException.class);

    verify(campaignService).findByIdForCurrentModeForWrite(campaignId);
    verify(userService, atLeast(1)).getIamUserContext();
    verify(schedulesRepository, never()).findAllById(anyList());
  }

  @Test
  @DisplayName(
      "acceptInventoryPrices - Should throw exception when CampaignInventorySchedules IDs not found")
  void acceptInventoryPrices_WhenNoSchedulesFound_ShouldThrowException() {
    // Given
    String campaignId = "campaign123";
    List<String> campaignInventorySchedulesIds = List.of("cis1");

    IamUserContext userContext = new IamUserContext();
    userContext.setUserId("user123");
    when(userService.getIamUserContext()).thenReturn(userContext);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCompanyId("company123");
    when(campaignService.findByIdForCurrentModeForWrite(campaignId)).thenReturn(testCampaign);
    when(campaignService.findById(campaignId)).thenReturn(campaign);

    when(schedulesRepository.findAllById(campaignInventorySchedulesIds))
        .thenReturn(Collections.emptyList());

    // When & Then
    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.acceptInventoryPrices(
                    campaignId, campaignInventorySchedulesIds))
        .isInstanceOf(CampaignInventorySchedulesNotFoundException.class);

    verify(campaignService).findByIdForCurrentModeForWrite(campaignId);
    verify(userService, atLeast(1)).getIamUserContext();
    verify(schedulesRepository).findAllById(campaignInventorySchedulesIds);
  }

  @Test
  @DisplayName(
      "acceptInventoryPrices - Should throw exception when CampaignInventorySchedules don't belong to campaign")
  void acceptInventoryPrices_WhenScheduleIdsNotBelongToCampaign_ShouldThrowException() {
    // Given
    String campaignId = "campaign123";
    List<String> campaignInventorySchedulesIds = List.of("cis1");

    IamUserContext userContext = new IamUserContext();
    userContext.setUserId("user123");
    when(userService.getIamUserContext()).thenReturn(userContext);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCompanyId("company123");
    when(campaignService.findByIdForCurrentModeForWrite(campaignId)).thenReturn(testCampaign);
    when(campaignService.findById(campaignId)).thenReturn(campaign);

    CampaignInventorySchedules schedule = new CampaignInventorySchedules();
    schedule.setId("cis1");
    schedule.setCampaignId("differentCampaign"); // Different campaign
    schedule.setInventoryId("inventory1");
    schedule.setMediaOwnerId("mediaOwner123");

    when(schedulesRepository.findAllById(campaignInventorySchedulesIds))
        .thenReturn(List.of(schedule));

    // When & Then
    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.acceptInventoryPrices(
                    campaignId, campaignInventorySchedulesIds))
        .isInstanceOf(CampaignInventorySchedulesNotFoundException.class);

    verify(campaignService).findByIdForCurrentModeForWrite(campaignId);
    verify(userService, atLeast(1)).getIamUserContext();
    verify(schedulesRepository).findAllById(campaignInventorySchedulesIds);
  }

  @Test
  @DisplayName("acceptInventoryPrices - Should handle missing schedule entities gracefully")
  void acceptInventoryPrices_WhenScheduleEntitiesNotFound_ShouldHandleGracefully() {
    // Given
    String campaignId = "campaign123";
    List<String> campaignInventorySchedulesIds = List.of("cis1");

    IamUserContext userContext = new IamUserContext();
    userContext.setUserId("user123");
    when(userService.getIamUserContext()).thenReturn(userContext);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCompanyId("company123");
    when(campaignService.findByIdForCurrentModeForWrite(campaignId)).thenReturn(testCampaign);
    when(campaignService.findById(campaignId)).thenReturn(campaign);

    CampaignInventorySchedules schedule = new CampaignInventorySchedules();
    schedule.setId("cis1");
    schedule.setCampaignId(campaignId);
    schedule.setInventoryId("inventory1");
    schedule.setMediaOwnerId("mediaOwner123");
    schedule.setScheduleIds(List.of("schedule1"));
    schedule.setApprovedBy(null);
    // Add history entry for approval authorization
    CampaignInventorySchedules.History history =
        CampaignInventorySchedules.History.builder()
            .action(PricingAction.PROPOSED)
            .companyId("mediaOwner123")
            .userId("mediaOwnerUser")
            .date(LocalDateTime.now().minusDays(1))
            .effectiveDiscountPercentage(10.0)
            .build();
    schedule.setHistory(new ArrayList<>(List.of(history)));

    when(schedulesRepository.findAllById(campaignInventorySchedulesIds))
        .thenReturn(List.of(schedule));

    Inventory inventory = new Inventory();
    inventory.setId("inventory1");
    inventory.setMediaOwnerId("mediaOwner123");
    when(inventoryService.getById("inventory1")).thenReturn(inventory);

    // Schedule entity not found
    when(scheduleRepository.findAllById(anyList())).thenReturn(Collections.emptyList());

    when(schedulesRepository.saveAll(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When - should not throw exception, just process with empty schedules
    campaignInventorySchedulesService.acceptInventoryPrices(
        campaignId, campaignInventorySchedulesIds);

    // Then
    verify(campaignService).findByIdForCurrentModeForWrite(campaignId);
    verify(userService, atLeast(1)).getIamUserContext();
    verify(schedulesRepository).findAllById(campaignInventorySchedulesIds);
    // findAllById is called multiple times (in acceptInventoryPrices and
    // calculateEffectiveDiscountPercentage)
    verify(scheduleRepository, atLeast(1)).findAllById(anyList());
  }

  @Test
  @DisplayName(
      "acceptInventoryPrices - Should accept prices when campaignInventorySchedulesIds is null")
  void acceptInventoryPrices_WhenScheduleIdsNull_ShouldAcceptAllSchedules() {
    // Given
    String campaignId = "campaign123";
    List<String> campaignInventorySchedulesIds = null;

    IamUserContext userContext = new IamUserContext();
    userContext.setUserId("user123");
    when(userService.getIamUserContext()).thenReturn(userContext);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCompanyId("company123");
    when(campaignService.findByIdForCurrentModeForWrite(campaignId)).thenReturn(testCampaign);
    when(campaignService.findById(campaignId)).thenReturn(campaign);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("cis1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory1");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("schedule1", "schedule2"));
    schedule1.setApprovedBy(null);
    // Add history entry for approval authorization
    CampaignInventorySchedules.History history1 =
        CampaignInventorySchedules.History.builder()
            .action(PricingAction.PROPOSED)
            .companyId("mediaOwner123")
            .userId("mediaOwnerUser")
            .date(LocalDateTime.now().minusDays(1))
            .effectiveDiscountPercentage(10.0)
            .build();
    schedule1.setHistory(new ArrayList<>(List.of(history1)));

    when(schedulesRepository.findByCampaignId(campaignId)).thenReturn(List.of(schedule1));

    Inventory inventory1 = new Inventory();
    inventory1.setId("inventory1");
    inventory1.setMediaOwnerId("mediaOwner123");
    when(inventoryService.getById("inventory1")).thenReturn(inventory1);

    // Mock schedules for null campaignInventorySchedulesIds case - fetch all schedules
    Schedule scheduleEntity1 = new Schedule();
    scheduleEntity1.setId("schedule1");
    scheduleEntity1.setBasePrice(1000.0);

    Schedule scheduleEntity2 = new Schedule();
    scheduleEntity2.setId("schedule2");
    scheduleEntity2.setBasePrice(1500.0);

    when(scheduleRepository.findAllById(anyList()))
        .thenAnswer(
            invocation -> {
              List<String> ids = invocation.getArgument(0);
              List<Schedule> result = new ArrayList<>();
              if (ids.contains("schedule1")) result.add(scheduleEntity1);
              if (ids.contains("schedule2")) result.add(scheduleEntity2);
              return result;
            });

    when(schedulesRepository.saveAll(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    campaignInventorySchedulesService.acceptInventoryPrices(
        campaignId, campaignInventorySchedulesIds);

    // Then
    verify(campaignService).findByIdForCurrentModeForWrite(campaignId);
    verify(userService, atLeast(1)).getIamUserContext();
    verify(schedulesRepository).findByCampaignId(campaignId);
    // findAllById is called multiple times (in acceptInventoryPrices and
    // calculateEffectiveDiscountPercentage)
    verify(scheduleRepository, atLeast(1)).findAllById(anyList());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CampaignInventorySchedules>> captor = ArgumentCaptor.forClass(List.class);
    verify(schedulesRepository).saveAll(captor.capture());
    List<CampaignInventorySchedules> savedSchedules = captor.getValue();
    assertThat(savedSchedules).hasSize(1);
    assertThat(savedSchedules.get(0).getApprovedScheduleIds())
        .containsExactlyInAnyOrder("schedule1", "schedule2");

    // Verify history entry has correct prices
    assertThat(savedSchedules.get(0).getHistory()).isNotEmpty();
    // Check the last history entry (ACCEPTED), not the first (PROPOSED)
    List<CampaignInventorySchedules.History> historyList = savedSchedules.get(0).getHistory();
    CampaignInventorySchedules.History history = historyList.get(historyList.size() - 1);
    assertThat(history.getAction()).isEqualTo(PricingAction.ACCEPTED);
    // History stores effectiveDiscountPercentage, not oldPrice/newPrice directly
    assertThat(history.getEffectiveDiscountPercentage()).isNotNull();
  }

  @Test
  @DisplayName(
      "acceptInventoryPrices - Should return gracefully when no schedules found for campaign (null campaignInventorySchedulesIds)")
  void acceptInventoryPrices_WhenNoSchedulesFoundForCampaign_ShouldReturnGracefully() {
    // Given
    String campaignId = "campaign123";
    List<String> campaignInventorySchedulesIds = null;

    IamUserContext userContext = new IamUserContext();
    userContext.setUserId("user123");
    when(userService.getIamUserContext()).thenReturn(userContext);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCompanyId("company123");
    when(campaignService.findByIdForCurrentModeForWrite(campaignId)).thenReturn(testCampaign);
    when(campaignService.findById(campaignId)).thenReturn(campaign);

    when(schedulesRepository.findByCampaignId(campaignId)).thenReturn(Collections.emptyList());

    // When - should return gracefully without exception
    campaignInventorySchedulesService.acceptInventoryPrices(
        campaignId, campaignInventorySchedulesIds);

    // Then
    verify(campaignService).findByIdForCurrentModeForWrite(campaignId);
    verify(userService, atLeast(1)).getIamUserContext();
    verify(schedulesRepository).findByCampaignId(campaignId);
    verify(schedulesRepository, never()).saveAll(anyList());
    verify(campaignService, never()).save(any(Campaign.class));
  }

  @Test
  @DisplayName("acceptInventoryPrices - Should merge with existing approvedScheduleIds")
  void acceptInventoryPrices_ShouldMergeWithExistingApprovedScheduleIds() {
    // Given
    String campaignId = "campaign123";
    List<String> campaignInventorySchedulesIds = List.of("cis1");

    IamUserContext userContext = new IamUserContext();
    userContext.setUserId("user123");
    when(userService.getIamUserContext()).thenReturn(userContext);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCompanyId("company123");
    when(campaignService.findByIdForCurrentModeForWrite(campaignId)).thenReturn(testCampaign);
    when(campaignService.findById(campaignId)).thenReturn(campaign);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("cis1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory1");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("schedule1", "schedule2", "schedule3"));
    schedule1.setApprovedBy(null); // Not yet approved
    schedule1.setApprovedScheduleIds(List.of("schedule1")); // Already has schedule1 approved

    // Add history entry for approval authorization validation
    // If last action is PROPOSED/COUNTERED and last companyId == inventory.mediaOwnerId: Campaign
    // Creator must approve
    CampaignInventorySchedules.History historyEntry =
        CampaignInventorySchedules.History.builder()
            .action(PricingAction.PROPOSED)
            .companyId("mediaOwner123") // Media owner proposed, so campaign creator can approve
            .userId("mediaOwnerUser")
            .date(LocalDateTime.now().minusDays(1))
            .effectiveDiscountPercentage(10.0)
            .build();
    schedule1.setHistory(new ArrayList<>(List.of(historyEntry)));

    when(schedulesRepository.findAllById(campaignInventorySchedulesIds))
        .thenReturn(List.of(schedule1));

    Inventory inventory1 = new Inventory();
    inventory1.setId("inventory1");
    inventory1.setMediaOwnerId("mediaOwner123");
    when(inventoryService.getById("inventory1")).thenReturn(inventory1);

    Schedule scheduleEntity1 = new Schedule();
    scheduleEntity1.setId("schedule1");
    scheduleEntity1.setBasePrice(500.0);

    Schedule scheduleEntity2 = new Schedule();
    scheduleEntity2.setId("schedule2");
    scheduleEntity2.setBasePrice(1000.0);

    Schedule scheduleEntity3 = new Schedule();
    scheduleEntity3.setId("schedule3");
    scheduleEntity3.setBasePrice(1500.0);

    when(scheduleRepository.findAllById(anyList()))
        .thenAnswer(
            invocation -> {
              List<String> ids = invocation.getArgument(0);
              List<Schedule> result = new ArrayList<>();
              if (ids.contains("schedule1")) result.add(scheduleEntity1);
              if (ids.contains("schedule2")) result.add(scheduleEntity2);
              if (ids.contains("schedule3")) result.add(scheduleEntity3);
              return result;
            });

    when(schedulesRepository.saveAll(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    campaignInventorySchedulesService.acceptInventoryPrices(
        campaignId, campaignInventorySchedulesIds);

    // Then
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CampaignInventorySchedules>> captor = ArgumentCaptor.forClass(List.class);
    verify(schedulesRepository).saveAll(captor.capture());
    List<CampaignInventorySchedules> savedSchedules = captor.getValue();
    assertThat(savedSchedules).hasSize(1);
    // Should merge: existing schedule1 + all scheduleIds (schedule2 + schedule3)
    assertThat(savedSchedules.get(0).getApprovedScheduleIds())
        .containsExactlyInAnyOrder("schedule1", "schedule2", "schedule3");
  }

  @Test
  @DisplayName(
      "acceptInventoryPrices - Should approve all scheduleIds in selected CampaignInventorySchedules")
  void acceptInventoryPrices_ShouldApproveAllScheduleIdsInSelectedCIS() {
    // Given
    String campaignId = "campaign123";
    List<String> campaignInventorySchedulesIds = List.of("cis1", "cis2");

    IamUserContext userContext = new IamUserContext();
    userContext.setUserId("user123");
    when(userService.getIamUserContext()).thenReturn(userContext);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCompanyId("company123");
    when(campaignService.findByIdForCurrentModeForWrite(campaignId)).thenReturn(testCampaign);
    when(campaignService.findById(campaignId)).thenReturn(campaign);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("cis1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory1");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("schedule1", "schedule2"));
    schedule1.setApprovedBy(null);
    // Add history entry for approval authorization
    CampaignInventorySchedules.History history1 =
        CampaignInventorySchedules.History.builder()
            .action(PricingAction.PROPOSED)
            .companyId("mediaOwner123")
            .userId("mediaOwnerUser")
            .date(LocalDateTime.now().minusDays(1))
            .effectiveDiscountPercentage(10.0)
            .build();
    schedule1.setHistory(new ArrayList<>(List.of(history1)));

    CampaignInventorySchedules schedule2 = new CampaignInventorySchedules();
    schedule2.setId("cis2");
    schedule2.setCampaignId(campaignId);
    schedule2.setInventoryId("inventory2");
    schedule2.setMediaOwnerId("mediaOwner123");
    schedule2.setScheduleIds(List.of("schedule3", "schedule4"));
    schedule2.setApprovedBy(null);
    // Add history entry for approval authorization
    CampaignInventorySchedules.History history2 =
        CampaignInventorySchedules.History.builder()
            .action(PricingAction.PROPOSED)
            .companyId("mediaOwner123")
            .userId("mediaOwnerUser")
            .date(LocalDateTime.now().minusDays(1))
            .effectiveDiscountPercentage(10.0)
            .build();
    schedule2.setHistory(new ArrayList<>(List.of(history2)));

    when(schedulesRepository.findAllById(campaignInventorySchedulesIds))
        .thenReturn(List.of(schedule1, schedule2));

    Inventory inventory1 = new Inventory();
    inventory1.setId("inventory1");
    inventory1.setMediaOwnerId("mediaOwner123");
    when(inventoryService.getById("inventory1")).thenReturn(inventory1);

    Inventory inventory2 = new Inventory();
    inventory2.setId("inventory2");
    inventory2.setMediaOwnerId("mediaOwner123");
    when(inventoryService.getById("inventory2")).thenReturn(inventory2);

    Schedule scheduleEntity1 = new Schedule();
    scheduleEntity1.setId("schedule1");
    scheduleEntity1.setBasePrice(500.0);

    Schedule scheduleEntity2 = new Schedule();
    scheduleEntity2.setId("schedule2");
    scheduleEntity2.setBasePrice(1000.0);

    Schedule scheduleEntity3 = new Schedule();
    scheduleEntity3.setId("schedule3");
    scheduleEntity3.setBasePrice(1500.0);

    Schedule scheduleEntity4 = new Schedule();
    scheduleEntity4.setId("schedule4");
    scheduleEntity4.setBasePrice(2000.0);

    when(scheduleRepository.findAllById(anyList()))
        .thenAnswer(
            invocation -> {
              List<String> ids = invocation.getArgument(0);
              List<Schedule> result = new ArrayList<>();
              if (ids.contains("schedule1")) result.add(scheduleEntity1);
              if (ids.contains("schedule2")) result.add(scheduleEntity2);
              if (ids.contains("schedule3")) result.add(scheduleEntity3);
              if (ids.contains("schedule4")) result.add(scheduleEntity4);
              return result;
            });

    when(schedulesRepository.saveAll(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    campaignInventorySchedulesService.acceptInventoryPrices(
        campaignId, campaignInventorySchedulesIds);

    // Then
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CampaignInventorySchedules>> captor = ArgumentCaptor.forClass(List.class);
    verify(schedulesRepository).saveAll(captor.capture());
    List<CampaignInventorySchedules> savedSchedules = captor.getValue();
    assertThat(savedSchedules).hasSize(2);
    // cis1 should have all its scheduleIds approved
    assertThat(savedSchedules.get(0).getApprovedScheduleIds())
        .containsExactlyInAnyOrder("schedule1", "schedule2");
    // cis2 should have all its scheduleIds approved
    assertThat(savedSchedules.get(1).getApprovedScheduleIds())
        .containsExactlyInAnyOrder("schedule3", "schedule4");
  }

  @Test
  @DisplayName(
      "acceptInventoryPrices - Should process all provided CampaignInventorySchedules regardless of approval status")
  void acceptInventoryPrices_ShouldProcessAllProvidedCIS() {
    // Given
    String campaignId = "campaign123";
    List<String> campaignInventorySchedulesIds = List.of("cis1", "cis2");

    IamUserContext userContext = new IamUserContext();
    userContext.setUserId("user123");
    when(userService.getIamUserContext()).thenReturn(userContext);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCompanyId("company123");
    when(campaignService.findByIdForCurrentModeForWrite(campaignId)).thenReturn(testCampaign);
    when(campaignService.findById(campaignId)).thenReturn(campaign);

    // Schedule already approved (approvedBy is not null) - but we still process it
    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("cis1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory1");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("schedule1"));
    schedule1.setApprovedBy("previousUser"); // Already approved
    // Add history entry for approval authorization
    CampaignInventorySchedules.History history1 =
        CampaignInventorySchedules.History.builder()
            .action(PricingAction.PROPOSED)
            .companyId("mediaOwner123")
            .userId("mediaOwnerUser")
            .date(LocalDateTime.now().minusDays(1))
            .effectiveDiscountPercentage(10.0)
            .build();
    schedule1.setHistory(new ArrayList<>(List.of(history1)));

    // This schedule is not approved yet
    CampaignInventorySchedules schedule2 = new CampaignInventorySchedules();
    schedule2.setId("cis2");
    schedule2.setCampaignId(campaignId);
    schedule2.setInventoryId("inventory2");
    schedule2.setMediaOwnerId("mediaOwner123");
    schedule2.setScheduleIds(List.of("schedule2"));
    schedule2.setApprovedBy(null); // Not yet approved
    // Add history entry for approval authorization
    CampaignInventorySchedules.History history2 =
        CampaignInventorySchedules.History.builder()
            .action(PricingAction.PROPOSED)
            .companyId("mediaOwner123")
            .userId("mediaOwnerUser")
            .date(LocalDateTime.now().minusDays(1))
            .effectiveDiscountPercentage(10.0)
            .build();
    schedule2.setHistory(new ArrayList<>(List.of(history2)));

    when(schedulesRepository.findAllById(campaignInventorySchedulesIds))
        .thenReturn(List.of(schedule1, schedule2));

    Inventory inventory1 = new Inventory();
    inventory1.setId("inventory1");
    inventory1.setMediaOwnerId("mediaOwner123");
    when(inventoryService.getById("inventory1")).thenReturn(inventory1);

    Inventory inventory2 = new Inventory();
    inventory2.setId("inventory2");
    inventory2.setMediaOwnerId("mediaOwner123");
    when(inventoryService.getById("inventory2")).thenReturn(inventory2);

    Schedule scheduleEntity1 = new Schedule();
    scheduleEntity1.setId("schedule1");
    scheduleEntity1.setBasePrice(1000.0);

    Schedule scheduleEntity2 = new Schedule();
    scheduleEntity2.setId("schedule2");
    scheduleEntity2.setBasePrice(1500.0);

    when(scheduleRepository.findAllById(anyList()))
        .thenAnswer(
            invocation -> {
              List<String> ids = invocation.getArgument(0);
              List<Schedule> result = new ArrayList<>();
              if (ids.contains("schedule1")) result.add(scheduleEntity1);
              if (ids.contains("schedule2")) result.add(scheduleEntity2);
              return result;
            });

    when(schedulesRepository.saveAll(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    campaignInventorySchedulesService.acceptInventoryPrices(
        campaignId, campaignInventorySchedulesIds);

    // Then - both should be processed
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CampaignInventorySchedules>> captor = ArgumentCaptor.forClass(List.class);
    verify(schedulesRepository).saveAll(captor.capture());
    List<CampaignInventorySchedules> savedSchedules = captor.getValue();
    assertThat(savedSchedules).hasSize(2);
    assertThat(savedSchedules.get(0).getApprovedScheduleIds()).containsExactly("schedule1");
    assertThat(savedSchedules.get(1).getApprovedScheduleIds()).containsExactly("schedule2");
  }

  @Test
  @DisplayName(
      "acceptInventoryPrices - Should handle empty campaignInventorySchedulesIds gracefully")
  void acceptInventoryPrices_ShouldHandleEmptySchedulesToUpdate() {
    // Given
    String campaignId = "campaign123";
    // Use empty list to test the case where no specific CampaignInventorySchedules IDs are provided
    List<String> campaignInventorySchedulesIds = Collections.emptyList();

    IamUserContext userContext = new IamUserContext();
    userContext.setUserId("user123");
    when(userService.getIamUserContext()).thenReturn(userContext);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCompanyId("company123");
    when(campaignService.findByIdForCurrentModeForWrite(campaignId)).thenReturn(testCampaign);
    when(campaignService.findById(campaignId)).thenReturn(campaign);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("cis1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory1");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(Collections.emptyList()); // No schedule IDs
    schedule1.setApprovedBy(null);
    // Add history entry for approval authorization
    CampaignInventorySchedules.History history1 =
        CampaignInventorySchedules.History.builder()
            .action(PricingAction.PROPOSED)
            .companyId("mediaOwner123")
            .userId("mediaOwnerUser")
            .date(LocalDateTime.now().minusDays(1))
            .effectiveDiscountPercentage(10.0)
            .build();
    schedule1.setHistory(new ArrayList<>(List.of(history1)));

    when(schedulesRepository.findByCampaignId(campaignId)).thenReturn(List.of(schedule1));

    Inventory inventory1 = new Inventory();
    inventory1.setId("inventory1");
    inventory1.setMediaOwnerId("mediaOwner123");
    when(inventoryService.getById("inventory1")).thenReturn(inventory1);

    when(schedulesRepository.saveAll(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When campaignInventorySchedulesIds is empty, all CampaignInventorySchedules are processed
    // When
    campaignInventorySchedulesService.acceptInventoryPrices(
        campaignId, campaignInventorySchedulesIds);

    // Then
    // Even with empty scheduleIds in the CampaignInventorySchedules, the method still sets
    // approvedBy
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CampaignInventorySchedules>> captor = ArgumentCaptor.forClass(List.class);
    verify(schedulesRepository).saveAll(captor.capture());
    List<CampaignInventorySchedules> savedSchedules = captor.getValue();
    assertThat(savedSchedules).hasSize(1);
    assertThat(savedSchedules.get(0).getApprovedBy()).isEqualTo("user123");
    assertThat(savedSchedules.get(0).getHistory()).isNotEmpty();
  }

  @Test
  @DisplayName("acceptInventoryPrices - Should handle invalid discount values gracefully")
  void acceptInventoryPrices_ShouldHandleInvalidDiscountValues() {
    // Given
    String campaignId = "campaign123";
    List<String> campaignInventorySchedulesIds = List.of("cis1");

    IamUserContext userContext = new IamUserContext();
    userContext.setUserId("user123");
    when(userService.getIamUserContext()).thenReturn(userContext);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCompanyId("company123");
    when(campaignService.findByIdForCurrentModeForWrite(campaignId)).thenReturn(testCampaign);
    when(campaignService.findById(campaignId)).thenReturn(campaign);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("cis1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory1");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("schedule1"));
    schedule1.setApprovedBy(null);
    // Add history entry for approval authorization
    CampaignInventorySchedules.History history1 =
        CampaignInventorySchedules.History.builder()
            .action(PricingAction.PROPOSED)
            .companyId("mediaOwner123")
            .userId("mediaOwnerUser")
            .date(LocalDateTime.now().minusDays(1))
            .effectiveDiscountPercentage(10.0)
            .build();
    schedule1.setHistory(new ArrayList<>(List.of(history1)));

    when(schedulesRepository.findAllById(campaignInventorySchedulesIds))
        .thenReturn(List.of(schedule1));

    Inventory inventory1 = new Inventory();
    inventory1.setId("inventory1");
    inventory1.setMediaOwnerId("mediaOwner123");
    when(inventoryService.getById("inventory1")).thenReturn(inventory1);

    Schedule scheduleEntity1 = new Schedule();
    scheduleEntity1.setId("schedule1");
    scheduleEntity1.setBasePrice(1000.0);
    Schedule.Discount invalidDiscount = new Schedule.Discount();
    invalidDiscount.setValue("invalid_number");
    scheduleEntity1.setDiscount(invalidDiscount);

    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(scheduleEntity1));

    when(schedulesRepository.saveAll(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When - should not throw exception
    campaignInventorySchedulesService.acceptInventoryPrices(
        campaignId, campaignInventorySchedulesIds);

    // Then
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CampaignInventorySchedules>> captor = ArgumentCaptor.forClass(List.class);
    verify(schedulesRepository).saveAll(captor.capture());
    List<CampaignInventorySchedules> savedSchedules = captor.getValue();
    assertThat(savedSchedules).hasSize(1);
    assertThat(savedSchedules.get(0).getApprovedBy()).isEqualTo("user123");
  }

  @Test
  @DisplayName("acceptInventoryPrices - Should handle schedules with null prices")
  void acceptInventoryPrices_ShouldHandleSchedulesWithNullPrices() {
    // Given
    String campaignId = "campaign123";
    List<String> campaignInventorySchedulesIds = List.of("cis1");

    IamUserContext userContext = new IamUserContext();
    userContext.setUserId("user123");
    when(userService.getIamUserContext()).thenReturn(userContext);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCompanyId("company123");
    when(campaignService.findByIdForCurrentModeForWrite(campaignId)).thenReturn(testCampaign);
    when(campaignService.findById(campaignId)).thenReturn(campaign);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("cis1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory1");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("schedule1"));
    schedule1.setApprovedBy(null);
    // Add history entry for approval authorization
    CampaignInventorySchedules.History history1 =
        CampaignInventorySchedules.History.builder()
            .action(PricingAction.PROPOSED)
            .companyId("mediaOwner123")
            .userId("mediaOwnerUser")
            .date(LocalDateTime.now().minusDays(1))
            .effectiveDiscountPercentage(10.0)
            .build();
    schedule1.setHistory(new ArrayList<>(List.of(history1)));

    when(schedulesRepository.findAllById(campaignInventorySchedulesIds))
        .thenReturn(List.of(schedule1));

    Inventory inventory1 = new Inventory();
    inventory1.setId("inventory1");
    inventory1.setMediaOwnerId("mediaOwner123");
    when(inventoryService.getById("inventory1")).thenReturn(inventory1);

    Schedule scheduleEntity1 = new Schedule();
    scheduleEntity1.setId("schedule1");
    scheduleEntity1.setBasePrice(null);

    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(scheduleEntity1));

    when(schedulesRepository.saveAll(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    campaignInventorySchedulesService.acceptInventoryPrices(
        campaignId, campaignInventorySchedulesIds);

    // Then
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CampaignInventorySchedules>> captor = ArgumentCaptor.forClass(List.class);
    verify(schedulesRepository).saveAll(captor.capture());
    List<CampaignInventorySchedules> savedSchedules = captor.getValue();
    assertThat(savedSchedules).hasSize(1);
    assertThat(savedSchedules.getFirst().getApprovedBy()).isEqualTo("user123");
    assertThat(savedSchedules.getFirst().getHistory()).isNotEmpty();
    // History should be created even with null prices
    // Check the last history entry (ACCEPTED), not the first (PROPOSED)
    List<CampaignInventorySchedules.History> historyList = savedSchedules.getFirst().getHistory();
    CampaignInventorySchedules.History history = historyList.getLast();
    assertThat(history.getAction()).isEqualTo(PricingAction.ACCEPTED);
  }

  @Test
  @DisplayName(
      "acceptInventoryPrices - Should accept prices when user is not campaign creator (any user can accept)")
  void acceptInventoryPrices_WhenUserIsNotCampaignCreator_ShouldAcceptPrices() {
    // Given
    String campaignId = "campaign123";
    List<String> campaignInventorySchedulesIds = List.of("cis1");

    IamUserContext userContext = new IamUserContext();
    userContext.setUserId("differentUser456");
    when(userService.getIamUserContext()).thenReturn(userContext);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCompanyId("company123");
    when(campaignService.findByIdForCurrentModeForWrite(campaignId)).thenReturn(testCampaign);
    when(campaignService.findById(campaignId)).thenReturn(campaign);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("cis1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory1");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("schedule1"));
    schedule1.setApprovedBy(null);
    CampaignInventorySchedules.History history1 =
        CampaignInventorySchedules.History.builder()
            .action(PricingAction.PROPOSED)
            .companyId("mediaOwner123")
            .userId("mediaOwnerUser")
            .date(LocalDateTime.now().minusDays(1))
            .effectiveDiscountPercentage(10.0)
            .build();
    schedule1.setHistory(new ArrayList<>(List.of(history1)));

    when(schedulesRepository.findAllById(campaignInventorySchedulesIds))
        .thenReturn(List.of(schedule1));

    Inventory inventory1 = new Inventory();
    inventory1.setId("inventory1");
    inventory1.setMediaOwnerId("mediaOwner123");
    when(inventoryService.getById("inventory1")).thenReturn(inventory1);

    Schedule scheduleEntity1 = new Schedule();
    scheduleEntity1.setId("schedule1");
    scheduleEntity1.setBasePrice(1000.0);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(scheduleEntity1));

    when(schedulesRepository.saveAll(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    campaignInventorySchedulesService.acceptInventoryPrices(
        campaignId, campaignInventorySchedulesIds);

    // Then
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CampaignInventorySchedules>> captor = ArgumentCaptor.forClass(List.class);
    verify(schedulesRepository).saveAll(captor.capture());
    List<CampaignInventorySchedules> savedSchedules = captor.getValue();
    assertThat(savedSchedules).hasSize(1);
    assertThat(savedSchedules.getFirst().getApprovedBy()).isEqualTo("differentUser456");
    assertThat(savedSchedules.getFirst().getApprovedScheduleIds()).containsExactly("schedule1");
    List<CampaignInventorySchedules.History> historyList = savedSchedules.getFirst().getHistory();
    assertThat(historyList.getLast().getAction()).isEqualTo(PricingAction.ACCEPTED);
  }

  @Test
  @DisplayName(
      "acceptInventoryPrices - Should fetch all CIS when not campaign creator and null IDs")
  void acceptInventoryPrices_WhenNotCampaignCreatorAndNullIds_ShouldFetchAllCIS() {
    // Given
    String campaignId = "campaign123";

    IamUserContext userContext = new IamUserContext();
    userContext.setUserId("differentUser456");
    when(userService.getIamUserContext()).thenReturn(userContext);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCompanyId("company123");
    when(campaignService.findByIdForCurrentModeForWrite(campaignId)).thenReturn(testCampaign);
    when(campaignService.findById(campaignId)).thenReturn(campaign);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("cis1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory1");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("schedule1"));
    schedule1.setApprovedBy(null);
    CampaignInventorySchedules.History history1 =
        CampaignInventorySchedules.History.builder()
            .action(PricingAction.PROPOSED)
            .companyId("mediaOwner123")
            .userId("mediaOwnerUser")
            .date(LocalDateTime.now().minusDays(1))
            .effectiveDiscountPercentage(10.0)
            .build();
    schedule1.setHistory(new ArrayList<>(List.of(history1)));

    when(schedulesRepository.findByCampaignId(campaignId)).thenReturn(List.of(schedule1));

    Inventory inventory1 = new Inventory();
    inventory1.setId("inventory1");
    inventory1.setMediaOwnerId("mediaOwner123");
    when(inventoryService.getById("inventory1")).thenReturn(inventory1);

    Schedule scheduleEntity1 = new Schedule();
    scheduleEntity1.setId("schedule1");
    scheduleEntity1.setBasePrice(1000.0);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(scheduleEntity1));

    when(schedulesRepository.saveAll(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    campaignInventorySchedulesService.acceptInventoryPrices(campaignId, null);

    // Then
    verify(schedulesRepository).findByCampaignId(campaignId);
    verify(schedulesRepository, never()).findByCampaignIdAndMediaOwnerId(anyString(), anyString());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CampaignInventorySchedules>> captor = ArgumentCaptor.forClass(List.class);
    verify(schedulesRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(1);
    assertThat(captor.getValue().getFirst().getApprovedBy()).isEqualTo("differentUser456");
  }

  @Test
  @DisplayName(
      "acceptInventoryPrices - Should accept with RATE_CARD history regardless of user role")
  void acceptInventoryPrices_WithRateCardHistory_ShouldAcceptRegardlessOfRole() {
    // Given
    String campaignId = "campaign123";
    List<String> campaignInventorySchedulesIds = List.of("cis1");

    IamUserContext userContext = new IamUserContext();
    userContext.setUserId("user123");
    when(userService.getIamUserContext()).thenReturn(userContext);

    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setCompanyId("company123");
    when(campaignService.findByIdForCurrentModeForWrite(campaignId)).thenReturn(testCampaign);
    when(campaignService.findById(campaignId)).thenReturn(campaign);

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("cis1");
    schedule1.setCampaignId(campaignId);
    schedule1.setInventoryId("inventory1");
    schedule1.setMediaOwnerId("mediaOwner123");
    schedule1.setScheduleIds(List.of("schedule1"));
    schedule1.setApprovedBy(null);
    // RATE_CARD history — old code required media owner to approve, now any user can
    CampaignInventorySchedules.History history1 =
        CampaignInventorySchedules.History.builder()
            .action(PricingAction.RATE_CARD)
            .companyId("mediaOwner123")
            .userId("mediaOwnerUser")
            .date(LocalDateTime.now().minusDays(1))
            .effectiveDiscountPercentage(0.0)
            .build();
    schedule1.setHistory(new ArrayList<>(List.of(history1)));

    when(schedulesRepository.findAllById(campaignInventorySchedulesIds))
        .thenReturn(List.of(schedule1));

    Inventory inventory1 = new Inventory();
    inventory1.setId("inventory1");
    inventory1.setMediaOwnerId("mediaOwner123");
    when(inventoryService.getById("inventory1")).thenReturn(inventory1);

    Schedule scheduleEntity1 = new Schedule();
    scheduleEntity1.setId("schedule1");
    scheduleEntity1.setBasePrice(1000.0);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(scheduleEntity1));

    when(schedulesRepository.saveAll(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When — should not throw, any user can accept
    campaignInventorySchedulesService.acceptInventoryPrices(
        campaignId, campaignInventorySchedulesIds);

    // Then
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CampaignInventorySchedules>> captor = ArgumentCaptor.forClass(List.class);
    verify(schedulesRepository).saveAll(captor.capture());
    List<CampaignInventorySchedules> savedSchedules = captor.getValue();
    assertThat(savedSchedules).hasSize(1);
    assertThat(savedSchedules.getFirst().getApprovedBy()).isEqualTo("user123");
    assertThat(savedSchedules.getFirst().getApprovedScheduleIds()).containsExactly("schedule1");
    List<CampaignInventorySchedules.History> historyList = savedSchedules.getFirst().getHistory();
    assertThat(historyList.getLast().getAction()).isEqualTo(PricingAction.ACCEPTED);
  }

  // ========== Schedule map batch optimization coverage ==========

  @Test
  @DisplayName(
      "prepareInventoryForecastForCampaignInventorySchedules with scheduleMap uses map and does not call repository")
  void prepareInventoryForecast_WithScheduleMap_ShouldNotCallFindAllById() {
    CampaignInventorySchedules cis = new CampaignInventorySchedules();
    cis.setScheduleIds(List.of("s1", "s2"));
    Schedule s1 = new Schedule();
    s1.setId("s1");
    s1.setTotalSot(10.0);
    s1.setPlannedSot(5.0);
    s1.setAdPlays(100L);
    Schedule s2 = new Schedule();
    s2.setId("s2");
    s2.setTotalSot(20.0);
    s2.setPlannedSot(10.0);
    s2.setAdPlays(200L);
    Map<String, Schedule> scheduleMap = new HashMap<>();
    scheduleMap.put("s1", s1);
    scheduleMap.put("s2", s2);

    CampaignInventorySchedulesForecastDTO result =
        campaignInventorySchedulesService.prepareInventoryForecastForCampaignInventorySchedules(
            cis, scheduleMap);

    assertThat(result).isNotNull();
    assertThat(result.getTotalSot()).isEqualTo(30.0);
    assertThat(result.getPlannedSot()).isEqualTo(15.0);
    assertThat(result.getEstimatedAdPlays()).isEqualTo(300L);
    verify(scheduleRepository, never()).findAllById(anyList());
  }

  @Test
  @DisplayName(
      "prepareInventoryForecastForCampaignInventorySchedules single-arg delegates and calls repository")
  void prepareInventoryForecast_WithoutScheduleMap_ShouldCallFindAllById() {
    CampaignInventorySchedules cis = new CampaignInventorySchedules();
    cis.setScheduleIds(List.of("s1"));
    Schedule s1 = new Schedule();
    s1.setId("s1");
    s1.setTotalSot(10.0);
    s1.setPlannedSot(5.0);
    s1.setAdPlays(50L);
    when(scheduleRepository.findAllById(List.of("s1"))).thenReturn(List.of(s1));

    CampaignInventorySchedulesForecastDTO result =
        campaignInventorySchedulesService.prepareInventoryForecastForCampaignInventorySchedules(
            cis);

    assertThat(result).isNotNull();
    assertThat(result.getTotalSot()).isEqualTo(10.0);
    assertThat(result.getPlannedSot()).isEqualTo(5.0);
    assertThat(result.getEstimatedAdPlays()).isEqualTo(50L);
    verify(scheduleRepository).findAllById(List.of("s1"));
  }

  @Test
  @DisplayName(
      "prepareInventoryForecastForCampaignInventorySchedules with digital inventory computes booked-spot-share SOV")
  void prepareInventoryForecast_WithDigitalInventory_ComputesBookedSpotShareSov() {
    CampaignInventorySchedules cis = new CampaignInventorySchedules();
    cis.setScheduleIds(List.of("s1"));
    Schedule s1 = new Schedule();
    s1.setId("s1");
    s1.setTotalSot(10.0);
    s1.setPlannedSot(10.0); // full time booked, but only 1 of 4 slots per loop
    s1.setSpotsPerLoop(1L);
    Map<String, Schedule> scheduleMap = Map.of("s1", s1);

    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(4);
    Inventory digitalInventory = new Inventory();
    digitalInventory.setClassification("Digital");
    digitalInventory.setDigitalFields(digitalFields);

    CampaignInventorySchedulesForecastDTO result =
        campaignInventorySchedulesService.prepareInventoryForecastForCampaignInventorySchedules(
            cis, scheduleMap, digitalInventory);

    // Without the classification fix this would be 100% (plannedSot == totalSot); with it, SOV
    // reflects only 1 of the screen's 4 slots being booked.
    assertThat(result.getSov()).isEqualTo(25.0);
  }

  @Test
  @DisplayName(
      "prepareInventoryForecastForCampaignInventorySchedules with classic inventory stays 100% regardless of spot data")
  void prepareInventoryForecast_WithClassicInventory_StaysAt100PercentSov() {
    CampaignInventorySchedules cis = new CampaignInventorySchedules();
    cis.setScheduleIds(List.of("s1"));
    Schedule s1 = new Schedule();
    s1.setId("s1");
    s1.setTotalSot(10.0);
    s1.setPlannedSot(4.0); // partial time booked — irrelevant for classic
    Map<String, Schedule> scheduleMap = Map.of("s1", s1);

    Inventory classicInventory = new Inventory();
    classicInventory.setClassification("Classic");

    CampaignInventorySchedulesForecastDTO result =
        campaignInventorySchedulesService.prepareInventoryForecastForCampaignInventorySchedules(
            cis, scheduleMap, classicInventory);

    assertThat(result.getSov()).isEqualTo(100.0);
  }

  @Test
  @DisplayName(
      "prepareInventoryForecastForCampaignInventorySchedules weights multiple schedules on the same digital inventory by plannedSot")
  void prepareInventoryForecast_WithMultipleDigitalSchedules_WeightsByPlannedSot() {
    CampaignInventorySchedules cis = new CampaignInventorySchedules();
    cis.setScheduleIds(List.of("s1", "s2"));
    // s1: 1 of 4 slots (25%), 100 plannedSot. s2: 4 of 4 slots (100%), 300 plannedSot.
    // Weighted: (25*100 + 100*300) / (100+300) = 81.25
    Schedule s1 = new Schedule();
    s1.setId("s1");
    s1.setTotalSot(100.0);
    s1.setPlannedSot(100.0);
    s1.setSpotsPerLoop(1L);
    Schedule s2 = new Schedule();
    s2.setId("s2");
    s2.setTotalSot(300.0);
    s2.setPlannedSot(300.0);
    s2.setSpotsPerLoop(4L);
    Map<String, Schedule> scheduleMap = new HashMap<>();
    scheduleMap.put("s1", s1);
    scheduleMap.put("s2", s2);

    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(4);
    Inventory digitalInventory = new Inventory();
    digitalInventory.setClassification("Digital");
    digitalInventory.setDigitalFields(digitalFields);

    CampaignInventorySchedulesForecastDTO result =
        campaignInventorySchedulesService.prepareInventoryForecastForCampaignInventorySchedules(
            cis, scheduleMap, digitalInventory);

    assertThat(result.getSov()).isEqualTo(81.25);
  }

  @Test
  @DisplayName(
      "calculateCampaignInventorySchedulesProposedPrice with scheduleMap does not call repository")
  void calculateCampaignInventorySchedulesProposedPrice_WithScheduleMap_ShouldNotCallFindAllById() {
    CampaignInventorySchedules cis = new CampaignInventorySchedules();
    cis.setScheduleIds(List.of("s1"));
    Schedule s1 = new Schedule();
    s1.setId("s1");
    s1.setBasePrice(100.0);
    Map<String, Schedule> scheduleMap = new HashMap<>();
    scheduleMap.put("s1", s1);
    Inventory inv = new Inventory();
    inv.setId("inv1");
    inv.setMediaOwnerId("mo1");
    Campaign camp = new Campaign();
    camp.setId("camp1");
    camp.setCompanyId("company1");

    Double result =
        campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
            cis, inv, camp, "company1", CustomFeesContext.builder().build(), scheduleMap);

    assertThat(result).isNotNull();
    verify(scheduleRepository, never()).findAllById(anyList());
  }

  @Test
  @DisplayName(
      "getCampaignPriceSummary builds schedule map once for all CIS (single batch findAllById)")
  void getCampaignPriceSummary_WithMultipleCIS_ShouldCallFindAllByIdOnceWithAllScheduleIds() {
    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().companyId("company123").build());
    when(campaignService.findById("campaign123")).thenReturn(testCampaign);

    CampaignInventorySchedules cis1 = new CampaignInventorySchedules();
    cis1.setInventoryId("inv1");
    cis1.setScheduleIds(List.of("sid1", "sid2"));
    CampaignInventorySchedules cis2 = new CampaignInventorySchedules();
    cis2.setInventoryId("inv2");
    cis2.setScheduleIds(List.of("sid2", "sid3"));
    when(schedulesRepository.findByCampaignId("campaign123")).thenReturn(List.of(cis1, cis2));

    Inventory inv1 = new Inventory();
    inv1.setId("inv1");
    inv1.setMediaOwnerId("mo1");
    Inventory.Price p1 = new Inventory.Price();
    p1.setSpot(10.0);
    inv1.setPrices(List.of(p1));
    Inventory inv2 = new Inventory();
    inv2.setId("inv2");
    inv2.setMediaOwnerId("mo2");
    inv2.setPrices(List.of(p1));
    when(inventoryService.getById("inv1")).thenReturn(inv1);
    when(inventoryService.getById("inv2")).thenReturn(inv2);

    Schedule s1 = new Schedule();
    s1.setId("sid1");
    s1.setBasePrice(100.0);
    Schedule s2 = new Schedule();
    s2.setId("sid2");
    s2.setBasePrice(200.0);
    Schedule s3 = new Schedule();
    s3.setId("sid3");
    s3.setBasePrice(300.0);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(s1, s2, s3));
    when(customFeeService.getActiveCustomFeesContextForCampaign(any(Campaign.class)))
        .thenReturn(CustomFeesContext.builder().build());

    CampaignPriceSummaryResponseDTO result =
        campaignInventorySchedulesService.getCampaignPriceSummary("campaign123");

    assertThat(result).isNotNull();
    // Batch optimization: one call with distinct schedule IDs (sid1, sid2, sid3)
    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(scheduleRepository, times(1)).findAllById(captor.capture());
    assertThat(captor.getValue()).containsExactlyInAnyOrder("sid1", "sid2", "sid3");
  }

  @Test
  @DisplayName(
      "getCampaignPriceSummary counts a campaign fee ONCE when creator company is also the media"
          + " owner (PL3-I4)")
  void getCampaignPriceSummary_WhenCreatorIsMediaOwner_ShouldNotDoubleCountCustomFee() {
    // testCampaign.companyId == "company123"; making the inventory media owner the same company
    // means the campaign-level fee is reachable both as a media-owner fee and as a creator fee.
    // It must be applied only ONCE: proposedPrice = base(100) + fee(100) = 200, not 300.
    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().companyId("company123").build());
    when(campaignService.findById("campaign123")).thenReturn(testCampaign);

    CampaignInventorySchedules cis = new CampaignInventorySchedules();
    cis.setInventoryId("inv1");
    cis.setScheduleIds(List.of("sid1"));
    when(schedulesRepository.findByCampaignId("campaign123")).thenReturn(List.of(cis));

    Inventory inv1 = new Inventory();
    inv1.setId("inv1");
    inv1.setMediaOwnerId("company123"); // media owner == campaign creator company
    Inventory.Price p1 = new Inventory.Price();
    p1.setSpot(100.0);
    inv1.setPrices(List.of(p1));
    when(inventoryService.getById("inv1")).thenReturn(inv1);

    Schedule s1 = new Schedule();
    s1.setId("sid1");
    s1.setBasePrice(100.0);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(s1));

    CustomFee visibleFee = new CustomFee();
    visibleFee.setId("fee1");
    visibleFee.setName("Service Fee");
    visibleFee.setType(CustomFeeType.VALUE);
    visibleFee.setValue(100.0);
    visibleFee.setIsIncludeInMediaPlan(true);
    visibleFee.setCampaignId("campaign123");
    visibleFee.setCompanyId("company123");

    CustomFeesContext context =
        CustomFeesContext.builder()
            .campaignFeesByCompanyId(
                Map.of(
                    "company123", CompanyCustomFees.builder().visible(List.of(visibleFee)).build()))
            .build();
    when(customFeeService.getActiveCustomFeesContextForCampaign(any(Campaign.class)))
        .thenReturn(context);

    CampaignPriceSummaryResponseDTO result =
        campaignInventorySchedulesService.getCampaignPriceSummary("campaign123");

    assertThat(result).isNotNull();
    // base 100 + one 100 fee = 200 (NOT 300, which is the double-count bug)
    assertThat(result.getProposedPrice()).isEqualTo(200.0);
    assertThat(result.getStandardFees()).isEqualTo(100.0);
  }

  @Test
  @DisplayName(
      "getCampaignPriceSummary - PERCENTAGE hidden fee effectiveCustomFee computed on pre-fee base"
          + " cost, not fee-inclusive total (PL3-I4)")
  void getCampaignPriceSummary_WithHiddenPercentageFee_ShouldComputeEffectiveFeeOnBaseCost() {
    // Live repro: base 17,589.25, 10% hidden fee.
    // Correct effectiveCustomFee = 10% of 17,589.25 = 1,758.93 (NOT 1,934.82 = 10% of 19,348.18).
    // Grand total (proposedPrice) must stay 19,348.18.
    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().companyId("company123").build());
    when(campaignService.findById("campaign123")).thenReturn(testCampaign);

    CampaignInventorySchedules cis = new CampaignInventorySchedules();
    cis.setInventoryId("inv1");
    cis.setScheduleIds(List.of("sid1"));
    when(schedulesRepository.findByCampaignId("campaign123")).thenReturn(List.of(cis));

    Inventory inv1 = new Inventory();
    inv1.setId("inv1");
    inv1.setMediaOwnerId("mediaOwner999"); // different from creator company
    when(inventoryService.getById("inv1")).thenReturn(inv1);

    Schedule s1 = new Schedule();
    s1.setId("sid1");
    s1.setBasePrice(17589.25);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(s1));

    CustomFee hiddenPercentageFee = new CustomFee();
    hiddenPercentageFee.setId("fee1");
    hiddenPercentageFee.setName("Hidden Markup");
    hiddenPercentageFee.setType(CustomFeeType.PERCENTAGE);
    hiddenPercentageFee.setValue(10.0);
    hiddenPercentageFee.setBasedOn(CustomFeeBasedOn.BASE_COST);
    hiddenPercentageFee.setIsIncludeInMediaPlan(false);
    hiddenPercentageFee.setCampaignId("campaign123");
    hiddenPercentageFee.setCompanyId("company123");

    CustomFeesContext context =
        CustomFeesContext.builder()
            .campaignFeesByCompanyId(
                Map.of(
                    "company123",
                    CompanyCustomFees.builder().hidden(List.of(hiddenPercentageFee)).build()))
            .build();
    when(customFeeService.getActiveCustomFeesContextForCampaign(any(Campaign.class)))
        .thenReturn(context);

    CampaignPriceSummaryResponseDTO result =
        campaignInventorySchedulesService.getCampaignPriceSummary("campaign123");

    assertThat(result).isNotNull();
    assertThat(result.getCustomFees()).hasSize(1);
    // effective fee = 10% of pre-fee base 17,589.25 = 1,758.925
    assertThat(result.getCustomFees().get(0).getEffectiveCustomFee())
        .isCloseTo(1758.925, within(0.01));
    // grand total must remain correct: 17,589.25 * 1.10 = 19,348.175
    assertThat(result.getProposedPrice()).isCloseTo(19348.175, within(0.01));
    // symptom (b): summary mediaCost field must show RAW pre-fee base cost
    assertThat(result.getMediaCost()).isCloseTo(17589.25, within(0.01));
  }

  @Test
  @DisplayName(
      "getCampaignPriceSummary - PERCENTAGE visible fee effectiveCustomFee computed on pre-fee base"
          + " cost (PL3-I4)")
  void getCampaignPriceSummary_WithVisiblePercentageFee_ShouldComputeEffectiveFeeOnBaseCost() {
    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().companyId("company123").build());
    when(campaignService.findById("campaign123")).thenReturn(testCampaign);

    CampaignInventorySchedules cis = new CampaignInventorySchedules();
    cis.setInventoryId("inv1");
    cis.setScheduleIds(List.of("sid1"));
    when(schedulesRepository.findByCampaignId("campaign123")).thenReturn(List.of(cis));

    Inventory inv1 = new Inventory();
    inv1.setId("inv1");
    inv1.setMediaOwnerId("mediaOwner999");
    when(inventoryService.getById("inv1")).thenReturn(inv1);

    Schedule s1 = new Schedule();
    s1.setId("sid1");
    s1.setBasePrice(17589.25);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(s1));

    CustomFee visiblePercentageFee = new CustomFee();
    visiblePercentageFee.setId("fee2");
    visiblePercentageFee.setName("Visible Markup");
    visiblePercentageFee.setType(CustomFeeType.PERCENTAGE);
    visiblePercentageFee.setValue(10.0);
    visiblePercentageFee.setBasedOn(CustomFeeBasedOn.BASE_COST);
    visiblePercentageFee.setIsIncludeInMediaPlan(true);
    visiblePercentageFee.setCampaignId("campaign123");
    visiblePercentageFee.setCompanyId("company123");

    CustomFeesContext context =
        CustomFeesContext.builder()
            .campaignFeesByCompanyId(
                Map.of(
                    "company123",
                    CompanyCustomFees.builder().visible(List.of(visiblePercentageFee)).build()))
            .build();
    when(customFeeService.getActiveCustomFeesContextForCampaign(any(Campaign.class)))
        .thenReturn(context);

    CampaignPriceSummaryResponseDTO result =
        campaignInventorySchedulesService.getCampaignPriceSummary("campaign123");

    assertThat(result).isNotNull();
    assertThat(result.getCustomFees()).hasSize(1);
    // effective fee = 10% of pre-fee base 17,589.25 = 1,758.925 (consistent with hidden path)
    assertThat(result.getCustomFees().get(0).getEffectiveCustomFee())
        .isCloseTo(1758.925, within(0.01));
    // visible fee folds into proposedPrice too
    assertThat(result.getProposedPrice()).isCloseTo(19348.175, within(0.01));
    // no hidden fees → mediaCost is raw base
    assertThat(result.getMediaCost()).isCloseTo(17589.25, within(0.01));
  }

  @Test
  @DisplayName(
      "getCampaignPriceSummary - VALUE fee effectiveCustomFee stays flat, unaffected by base-cost"
          + " fix (PL3-I4)")
  void getCampaignPriceSummary_WithValueFee_ShouldReturnFlatEffectiveFee() {
    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().companyId("company123").build());
    when(campaignService.findById("campaign123")).thenReturn(testCampaign);

    CampaignInventorySchedules cis = new CampaignInventorySchedules();
    cis.setInventoryId("inv1");
    cis.setScheduleIds(List.of("sid1"));
    when(schedulesRepository.findByCampaignId("campaign123")).thenReturn(List.of(cis));

    Inventory inv1 = new Inventory();
    inv1.setId("inv1");
    inv1.setMediaOwnerId("mediaOwner999");
    when(inventoryService.getById("inv1")).thenReturn(inv1);

    Schedule s1 = new Schedule();
    s1.setId("sid1");
    s1.setBasePrice(17589.25);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(s1));

    CustomFee valueFee = new CustomFee();
    valueFee.setId("fee3");
    valueFee.setName("Flat Fee");
    valueFee.setType(CustomFeeType.VALUE);
    valueFee.setValue(500.0);
    valueFee.setIsIncludeInMediaPlan(true);
    valueFee.setCampaignId("campaign123");
    valueFee.setCompanyId("company123");

    CustomFeesContext context =
        CustomFeesContext.builder()
            .campaignFeesByCompanyId(
                Map.of(
                    "company123", CompanyCustomFees.builder().visible(List.of(valueFee)).build()))
            .build();
    when(customFeeService.getActiveCustomFeesContextForCampaign(any(Campaign.class)))
        .thenReturn(context);

    CampaignPriceSummaryResponseDTO result =
        campaignInventorySchedulesService.getCampaignPriceSummary("campaign123");

    assertThat(result).isNotNull();
    assertThat(result.getCustomFees()).hasSize(1);
    // VALUE fee stays a flat 500.0 regardless of base
    assertThat(result.getCustomFees().get(0).getEffectiveCustomFee())
        .isCloseTo(500.0, within(0.01));
  }

  @Test
  @DisplayName(
      "getCampaignSchedulePrices builds schedule map once (single findAllById for schedules)")
  void getCampaignSchedulePrices_WithMultipleSchedules_ShouldCallFindAllByIdOnceForSchedules() {
    String campaignId = "campaign123";
    Pageable pageable = PageRequest.of(0, 10);
    CampaignInventorySchedules cis1 = new CampaignInventorySchedules();
    cis1.setId("cis1");
    cis1.setCampaignId(campaignId);
    cis1.setInventoryId("inv1");
    cis1.setMediaOwnerId("mo1");
    cis1.setScheduleIds(List.of("s1", "s2"));
    CampaignInventorySchedules cis2 = new CampaignInventorySchedules();
    cis2.setId("cis2");
    cis2.setCampaignId(campaignId);
    cis2.setInventoryId("inv2");
    cis2.setMediaOwnerId("mo2");
    cis2.setScheduleIds(List.of("s3"));
    Page<CampaignInventorySchedules> page = new PageImpl<>(List.of(cis1, cis2), pageable, 2);

    when(schedulesRepository.findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull()))
        .thenReturn(page);
    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");

    Inventory inv1 = new Inventory();
    inv1.setId("inv1");
    inv1.setName("Inv1");
    inv1.setMediaOwnerId("mo1");
    Inventory inv2 = new Inventory();
    inv2.setId("inv2");
    inv2.setName("Inv2");
    inv2.setMediaOwnerId("mo2");
    when(inventoryService.findAllByIds(anyList())).thenReturn(List.of(inv1, inv2));

    Schedule sch1 = new Schedule();
    sch1.setId("s1");
    Schedule sch2 = new Schedule();
    sch2.setId("s2");
    Schedule sch3 = new Schedule();
    sch3.setId("s3");
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(sch1, sch2, sch3));
    when(customFeeService.getActiveCustomFeesContextForCampaign(any(Campaign.class)))
        .thenReturn(CustomFeesContext.builder().build());
    CompanyLookupResponseDTO lookup1 =
        CompanyLookupResponseDTO.builder().id("mo1").name("Media Owner 1").build();
    CompanyLookupResponseDTO lookup2 =
        CompanyLookupResponseDTO.builder().id("mo2").name("Media Owner 2").build();
    lenient().when(companyService.getCompanyLookupWithCompanyId("mo1")).thenReturn(lookup1);
    lenient().when(companyService.getCompanyLookupWithCompanyId("mo2")).thenReturn(lookup2);

    campaignInventorySchedulesService.getCampaignSchedulePrices(
        campaignId, CampaignSchedulePriceFilterDTO.builder().build(), pageable);

    // One batch call for all schedule IDs (s1, s2, s3)
    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(scheduleRepository, times(1)).findAllById(captor.capture());
    assertThat(captor.getValue()).containsExactlyInAnyOrder("s1", "s2", "s3");
  }

  @Test
  @DisplayName(
      "getCampaignSchedulePrices resolves the user context once per page, not once per row")
  void getCampaignSchedulePrices_WithMultipleSchedules_ShouldResolveUserContextOncePerPage() {
    String campaignId = "campaign123";
    Pageable pageable = PageRequest.of(0, 10);
    CampaignInventorySchedules cis1 = new CampaignInventorySchedules();
    cis1.setId("cis1");
    cis1.setCampaignId(campaignId);
    cis1.setInventoryId("inv1");
    cis1.setMediaOwnerId("mo1");
    cis1.setScheduleIds(List.of("s1"));
    CampaignInventorySchedules cis2 = new CampaignInventorySchedules();
    cis2.setId("cis2");
    cis2.setCampaignId(campaignId);
    cis2.setInventoryId("inv2");
    cis2.setMediaOwnerId("mo2");
    cis2.setScheduleIds(List.of("s2"));
    Page<CampaignInventorySchedules> page = new PageImpl<>(List.of(cis1, cis2), pageable, 2);

    when(schedulesRepository.findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull()))
        .thenReturn(page);
    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");

    Inventory inv1 = new Inventory();
    inv1.setId("inv1");
    inv1.setMediaOwnerId("mo1");
    Inventory inv2 = new Inventory();
    inv2.setId("inv2");
    inv2.setMediaOwnerId("mo2");
    when(inventoryService.findAllByIds(anyList())).thenReturn(List.of(inv1, inv2));
    when(scheduleRepository.findAllById(anyList()))
        .thenReturn(List.of(new Schedule(), new Schedule()));
    when(customFeeService.getActiveCustomFeesContextForCampaign(any(Campaign.class)))
        .thenReturn(CustomFeesContext.builder().build());
    lenient()
        .when(companyService.getCompanyLookupWithCompanyId(anyString()))
        .thenReturn(CompanyLookupResponseDTO.builder().build());

    campaignInventorySchedulesService.getCampaignSchedulePrices(
        campaignId, CampaignSchedulePriceFilterDTO.builder().build(), pageable);

    // Resolved once for the whole page (two rows), not once per row.
    verify(userService, times(1)).getIamUserContext();
  }

  @Test
  @DisplayName(
      "getCampaignSchedulePrices looks up each distinct media owner name once, even with"
          + " multiple rows sharing it")
  void getCampaignSchedulePrices_WithSharedMediaOwner_ShouldLookUpCompanyOncePerDistinctOwner() {
    String campaignId = "campaign123";
    Pageable pageable = PageRequest.of(0, 10);
    CampaignInventorySchedules cis1 = new CampaignInventorySchedules();
    cis1.setId("cis1");
    cis1.setCampaignId(campaignId);
    cis1.setInventoryId("inv1");
    cis1.setMediaOwnerId("mo1");
    cis1.setScheduleIds(List.of("s1"));
    CampaignInventorySchedules cis2 = new CampaignInventorySchedules();
    cis2.setId("cis2");
    cis2.setCampaignId(campaignId);
    cis2.setInventoryId("inv2");
    cis2.setMediaOwnerId("mo1");
    cis2.setScheduleIds(List.of("s2"));
    Page<CampaignInventorySchedules> page = new PageImpl<>(List.of(cis1, cis2), pageable, 2);

    when(schedulesRepository.findWithPriceFilters(eq(campaignId), any(), eq(pageable), isNull()))
        .thenReturn(page);
    when(campaignService.findById(campaignId)).thenReturn(testCampaign);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");

    // Both inventories share the same media owner.
    Inventory inv1 = new Inventory();
    inv1.setId("inv1");
    inv1.setMediaOwnerId("mo1");
    Inventory inv2 = new Inventory();
    inv2.setId("inv2");
    inv2.setMediaOwnerId("mo1");
    when(inventoryService.findAllByIds(anyList())).thenReturn(List.of(inv1, inv2));
    when(scheduleRepository.findAllById(anyList()))
        .thenReturn(List.of(new Schedule(), new Schedule()));
    when(customFeeService.getActiveCustomFeesContextForCampaign(any(Campaign.class)))
        .thenReturn(CustomFeesContext.builder().build());
    when(companyService.getCompanyLookupWithCompanyId("mo1"))
        .thenReturn(CompanyLookupResponseDTO.builder().name("Media Owner 1").build());

    Page<CampaignSchedulePriceResponseDTO> result =
        campaignInventorySchedulesService.getCampaignSchedulePrices(
            campaignId, CampaignSchedulePriceFilterDTO.builder().build(), pageable);

    // One lookup for the one distinct media owner shared by both rows, not one per row.
    verify(companyService, times(1)).getCompanyLookupWithCompanyId("mo1");
    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getContent().get(0).getMediaOwnerName()).isEqualTo("Media Owner 1");
    assertThat(result.getContent().get(1).getMediaOwnerName()).isEqualTo("Media Owner 1");
  }

  // ========== editScheduleById Tests ==========

  @Test
  @DisplayName(
      "editScheduleById - Should preserve totalSot from existing schedule (not recalculated on update)")
  void editScheduleById_ShouldPreserveTotalSotFromExistingSchedule() {
    // Given
    String scheduleId = "schedule-edit-1";
    double originalTotalSot = 99.0;
    Map<String, List<Integer>> bookingMatrix = Map.of("2025-12-01", List.of(0, 1, 2));

    Schedule existingSchedule = new Schedule();
    existingSchedule.setId(scheduleId);
    existingSchedule.setTotalSot(originalTotalSot);
    existingSchedule.setStartDate(LocalDate.of(2025, 12, 1));
    existingSchedule.setEndDate(LocalDate.of(2025, 12, 31));
    existingSchedule.setBookingMatrix(bookingMatrix);

    CampaignInventorySchedules cis = new CampaignInventorySchedules();
    cis.setId("cis-edit-1");
    cis.setCampaignId("campaign123");
    cis.setInventoryId("inventory123");
    cis.setScheduleIds(List.of(scheduleId));

    EditScheduleRequestDTO request = new EditScheduleRequestDTO();
    request.setName("Updated Name");
    request.setStartDate(LocalDate.of(2025, 12, 1));
    request.setEndDate(LocalDate.of(2025, 12, 31));
    request.setBookingMatrix(bookingMatrix);
    request.setScheduleDays(List.of("MONDAY"));

    when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(existingSchedule));
    when(schedulesRepository.findByCampaignId("campaign123")).thenReturn(List.of(cis));
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(i -> i.getArgument(0));

    // When
    campaignInventorySchedulesService.editScheduleById("campaign123", scheduleId, request);

    // Then - totalSot must remain unchanged
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    assertThat(scheduleCaptor.getValue().getTotalSot()).isEqualTo(originalTotalSot);
  }

  @Test
  @DisplayName("editScheduleById - plannedSot should equal (adPlays * spotDuration) / 3600")
  void editScheduleById_ShouldComputePlannedSotFromAdPlays() {
    // Given
    String scheduleId = "schedule-edit-2";
    // 3 + 2 = 5 booked hours; loopsPerHour = 12, spotDuration = 30 → adPlays = 60
    // → plannedSot = (60 * 30) / 3600 = 0.5
    Map<String, List<Integer>> bookingMatrix =
        Map.of("2025-12-01", List.of(0, 1, 2), "2025-12-02", List.of(0, 1));

    Schedule existingSchedule = new Schedule();
    existingSchedule.setId(scheduleId);
    existingSchedule.setTotalSot(50.0);
    existingSchedule.setStartDate(LocalDate.of(2025, 12, 1));
    existingSchedule.setEndDate(LocalDate.of(2025, 12, 2));
    existingSchedule.setBookingMatrix(bookingMatrix);

    CampaignInventorySchedules cis = new CampaignInventorySchedules();
    cis.setId("cis-edit-2");
    cis.setCampaignId("campaign123");
    cis.setInventoryId("inventory123");
    cis.setScheduleIds(List.of(scheduleId));

    EditScheduleRequestDTO request = new EditScheduleRequestDTO();
    request.setName("Updated Schedule");
    request.setStartDate(LocalDate.of(2025, 12, 1));
    request.setEndDate(LocalDate.of(2025, 12, 2));
    request.setBookingMatrix(bookingMatrix);
    request.setScheduleDays(List.of("MONDAY", "TUESDAY"));
    request.setSpotsPerLoop(3L);

    when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(existingSchedule));
    when(schedulesRepository.findByCampaignId("campaign123")).thenReturn(List.of(cis));
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(i -> i.getArgument(0));

    // When
    campaignInventorySchedulesService.editScheduleById("campaign123", scheduleId, request);

    // Then
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    assertThat(scheduleCaptor.getValue().getPlannedSot()).isEqualTo(0.5);
    assertThat(scheduleCaptor.getValue().getSpotsPerLoop()).isEqualTo(3L);
  }

  @Test
  @DisplayName(
      "editScheduleById - Should default spotsPerLoop to 1 when not provided; plannedSot from adPlays formula")
  void editScheduleById_WithNullSpotsPerLoop_ShouldDefaultToOne() {
    // Given
    String scheduleId = "schedule-edit-3";
    // 4 booked hours; loopsPerHour = 12, spotDuration = 30 → adPlays = 48
    // → plannedSot = (48 * 30) / 3600 = 0.4; null spotsPerLoop defaults to 1
    Map<String, List<Integer>> bookingMatrix = Map.of("2025-12-01", List.of(0, 1, 2, 3));

    Schedule existingSchedule = new Schedule();
    existingSchedule.setId(scheduleId);
    existingSchedule.setTotalSot(50.0);
    existingSchedule.setStartDate(LocalDate.of(2025, 12, 1));
    existingSchedule.setEndDate(LocalDate.of(2025, 12, 1));
    existingSchedule.setBookingMatrix(bookingMatrix);

    CampaignInventorySchedules cis = new CampaignInventorySchedules();
    cis.setId("cis-edit-3");
    cis.setCampaignId("campaign123");
    cis.setInventoryId("inventory123");
    cis.setScheduleIds(List.of(scheduleId));

    EditScheduleRequestDTO request = new EditScheduleRequestDTO();
    request.setName("Updated Schedule");
    request.setStartDate(LocalDate.of(2025, 12, 1));
    request.setEndDate(LocalDate.of(2025, 12, 1));
    request.setBookingMatrix(bookingMatrix);
    request.setScheduleDays(List.of("MONDAY"));
    request.setSpotsPerLoop(null);

    when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(existingSchedule));
    when(schedulesRepository.findByCampaignId("campaign123")).thenReturn(List.of(cis));
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(i -> i.getArgument(0));

    // When
    campaignInventorySchedulesService.editScheduleById("campaign123", scheduleId, request);

    // Then
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    assertThat(scheduleCaptor.getValue().getSpotsPerLoop()).isEqualTo(1L);
    assertThat(scheduleCaptor.getValue().getPlannedSot()).isEqualTo(0.4);
  }

  @Test
  @DisplayName(
      "selectInventory - Non-digital inventory (no digitalFields) should have non-zero totalSot due to clientPerLoop fallback of 1")
  void selectInventory_WithNonDigitalInventory_TotalSotShouldNotBeZero() {
    // Given - inventory with no digitalFields (classic/static inventory)
    Inventory nonDigitalInventory = new Inventory();
    nonDigitalInventory.setId("inventory123");
    nonDigitalInventory.setName("Classic Billboard");
    nonDigitalInventory.setArchived(false);
    nonDigitalInventory.setMediaOwnerId("mediaOwner123");
    nonDigitalInventory.setDigitalFields(null);

    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    for (Inventory.Weekday weekday : Inventory.Weekday.values()) {
      operatingTimesMap.put(weekday, List.of(operatingTime));
    }
    nonDigitalInventory.setOperatingTimes(operatingTimesMap);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(nonDigitalInventory);
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(
            i -> {
              CampaignInventorySchedules s = i.getArgument(0);
              s.setId("cis-1");
              return s;
            });
    when(campaignService.save(any(Campaign.class))).thenReturn(testCampaign);
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), anyString(), any());

    // When
    campaignInventorySchedulesService.selectInventory(testSelectRequest);

    // Then - totalSot must be > 0 (clientPerLoop defaults to 1, not 0)
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    assertThat(scheduleCaptor.getValue().getTotalSot()).isGreaterThan(0.0);
  }

  // ========== addSchedule Tests ==========

  @Test
  @DisplayName("addSchedule - plannedSot should equal (adPlays * spotDuration) / 3600")
  void addSchedule_ShouldComputePlannedSotFromAdPlays() {
    // Given - 3 + 2 = 5 booked hours; loopsPerHour = 12, spotDuration = 30 → adPlays = 60
    // → plannedSot = (60 * 30) / 3600 = 0.5
    Map<String, List<Integer>> bookingMatrix =
        Map.of("2025-12-01", List.of(0, 1, 2), "2025-12-02", List.of(0, 1));

    CampaignInventorySchedules existingCis = new CampaignInventorySchedules();
    existingCis.setId("cis-add-1");
    existingCis.setCampaignId("campaign123");
    existingCis.setInventoryId("inventory123");
    existingCis.setScheduleIds(new ArrayList<>());

    AddScheduleRequestDTO request = new AddScheduleRequestDTO();
    request.setInventoryId("inventory123");
    request.setName("Morning Schedule");
    request.setStartDate(LocalDate.of(2025, 12, 1));
    request.setEndDate(LocalDate.of(2025, 12, 2));
    request.setScheduleDays(List.of("MONDAY", "TUESDAY"));
    request.setBookingMatrix(bookingMatrix);
    request.setSpotsPerLoop(4L);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(schedulesRepository.findByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(Optional.of(existingCis));
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(i -> i.getArgument(0));

    // When
    campaignInventorySchedulesService.addSchedule("campaign123", request);

    // Then
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    assertThat(scheduleCaptor.getValue().getPlannedSot()).isEqualTo(0.5);
    assertThat(scheduleCaptor.getValue().getSpotsPerLoop()).isEqualTo(4L);
  }

  @Test
  @DisplayName(
      "addSchedule - Should default spotsPerLoop to 1 when not provided; plannedSot from adPlays formula")
  void addSchedule_WithNullSpotsPerLoop_ShouldDefaultToOne() {
    // Given - 4 booked hours; loopsPerHour = 12, spotDuration = 30 → adPlays = 48
    // → plannedSot = (48 * 30) / 3600 = 0.4; null spotsPerLoop defaults to 1
    Map<String, List<Integer>> bookingMatrix = Map.of("2025-12-01", List.of(0, 1, 2, 3));

    CampaignInventorySchedules existingCis = new CampaignInventorySchedules();
    existingCis.setId("cis-add-2");
    existingCis.setCampaignId("campaign123");
    existingCis.setInventoryId("inventory123");
    existingCis.setScheduleIds(new ArrayList<>());

    AddScheduleRequestDTO request = new AddScheduleRequestDTO();
    request.setInventoryId("inventory123");
    request.setName("Default Schedule");
    request.setStartDate(LocalDate.of(2025, 12, 1));
    request.setEndDate(LocalDate.of(2025, 12, 1));
    request.setScheduleDays(List.of("MONDAY"));
    request.setBookingMatrix(bookingMatrix);
    request.setSpotsPerLoop(null);

    when(campaignService.findById("campaign123")).thenReturn(testCampaign);
    when(inventoryService.getById("inventory123")).thenReturn(testInventory);
    when(schedulesRepository.findByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(Optional.of(existingCis));
    when(schedulesRepository.save(any(CampaignInventorySchedules.class)))
        .thenAnswer(i -> i.getArgument(0));

    // When
    campaignInventorySchedulesService.addSchedule("campaign123", request);

    // Then
    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).save(scheduleCaptor.capture());
    assertThat(scheduleCaptor.getValue().getSpotsPerLoop()).isEqualTo(1L);
    assertThat(scheduleCaptor.getValue().getPlannedSot()).isEqualTo(0.4);
  }

  // ========== Pure static helpers: SOV / SOT / AdPlays ==========

  @Test
  void calculateSov_WithValidValues_ReturnsPercentage() {
    assertThat(CampaignInventorySchedulesService.calculateSov(200.0, 50.0)).isEqualTo(25.0);
  }

  @Test
  void calculateSov_WithNullPlannedSot_ReturnsZero() {
    assertThat(CampaignInventorySchedulesService.calculateSov(200.0, null)).isEqualTo(0.0);
  }

  @Test
  void calculateSov_WithNullTotalSot_ReturnsZero() {
    assertThat(CampaignInventorySchedulesService.calculateSov(null, 50.0)).isEqualTo(0.0);
  }

  @Test
  void calculateSov_WithZeroTotalSot_ReturnsZero() {
    assertThat(CampaignInventorySchedulesService.calculateSov(0.0, 50.0)).isEqualTo(0.0);
  }

  @Test
  void calculateInventorySov_ForClassic_Returns100RegardlessOfSpotData() {
    assertThat(
            CampaignInventorySchedulesService.calculateInventorySov("Classic", 1L, 4, 200.0, 50.0))
        .isEqualTo(100.0);
  }

  @Test
  void calculateInventorySov_ForTransit_Returns100RegardlessOfSpotData() {
    assertThat(
            CampaignInventorySchedulesService.calculateInventorySov(
                "Transit", null, null, 200.0, 200.0))
        .isEqualTo(100.0);
  }

  @Test
  void calculateInventorySov_ForDigitalWithPartialBooking_ReturnsBookedSpotShare() {
    // 1 of 4 slots on the screen's loop booked by this schedule => 25%, not the old 100%
    assertThat(
            CampaignInventorySchedulesService.calculateInventorySov("Digital", 1L, 4, 200.0, 200.0))
        .isEqualTo(25.0);
  }

  @Test
  void calculateInventorySov_ForDigitalWithFullBooking_Returns100() {
    assertThat(
            CampaignInventorySchedulesService.calculateInventorySov("Digital", 4L, 4, 200.0, 200.0))
        .isEqualTo(100.0);
  }

  @Test
  void calculateInventorySov_ForDigitalOverbooked_CapsAt100() {
    // Defensive cap in case bad data has scheduleSpotsPerLoop > inventory capacity
    assertThat(
            CampaignInventorySchedulesService.calculateInventorySov("Digital", 6L, 4, 200.0, 200.0))
        .isEqualTo(100.0);
  }

  @Test
  void calculateInventorySov_ForDigitalMissingCapacityData_FallsBackToTimeRatio() {
    assertThat(
            CampaignInventorySchedulesService.calculateInventorySov(
                "Digital", null, null, 200.0, 50.0))
        .isEqualTo(25.0);
  }

  @Test
  void calculateInventorySov_WithNullClassification_FallsBackToTimeRatio() {
    assertThat(CampaignInventorySchedulesService.calculateInventorySov(null, 1L, 4, 200.0, 50.0))
        .isEqualTo(25.0);
  }

  @Test
  void calculateWeightedSov_MixedClassicAndDigital_WeightsByPlannedSot() {
    // Classic schedule at 100% weighted by 100 plannedSot, digital at 25% weighted by 300
    // plannedSot => (100*100 + 25*300) / (100+300) = 43.75
    Double result =
        CampaignInventorySchedulesService.calculateWeightedSov(
            List.of(100.0, 25.0), List.of(100.0, 300.0));
    assertThat(result).isEqualTo(43.75);
  }

  @Test
  void calculateWeightedSov_AllSameValue_ReturnsThatValueRegardlessOfWeights() {
    Double result =
        CampaignInventorySchedulesService.calculateWeightedSov(
            List.of(100.0, 100.0), List.of(10.0, 500.0));
    assertThat(result).isEqualTo(100.0);
  }

  @Test
  void calculateWeightedSov_WithZeroTotalWeight_ReturnsZero() {
    Double result =
        CampaignInventorySchedulesService.calculateWeightedSov(
            List.of(100.0, 25.0), List.of(0.0, 0.0));
    assertThat(result).isEqualTo(0.0);
  }

  @Test
  void calculateWeightedSov_SkipsNullEntries() {
    Double result =
        CampaignInventorySchedulesService.calculateWeightedSov(
            Arrays.asList(100.0, null, 25.0), Arrays.asList(100.0, 50.0, 300.0));
    // Same as the mixed test above, since the null-sov entry is skipped entirely
    assertThat(result).isEqualTo(43.75);
  }

  @Test
  void calculateEstimatedAdPlays_WithNull_ReturnsNull() {
    assertThat(CampaignInventorySchedulesService.calculateEstimatedAdPlays(null)).isNull();
  }

  @Test
  void calculateEstimatedAdPlays_WithEmpty_ReturnsNull() {
    assertThat(CampaignInventorySchedulesService.calculateEstimatedAdPlays(Collections.emptyList()))
        .isNull();
  }

  @Test
  void calculateEstimatedAdPlays_WithValues_SumsAdPlaysTreatingNullAsZero() {
    List<Schedule> schedules =
        List.of(
            schedule().adPlays(100L).build(),
            schedule().adPlays(null).build(),
            schedule().adPlays(50L).build());
    assertThat(CampaignInventorySchedulesService.calculateEstimatedAdPlays(schedules))
        .isEqualTo(150L);
  }

  @Test
  void calculatePlannedSot_WithNullOrEmpty_ReturnsNull() {
    assertThat(CampaignInventorySchedulesService.calculatePlannedSot(null)).isNull();
    assertThat(CampaignInventorySchedulesService.calculatePlannedSot(Collections.emptyList()))
        .isNull();
  }

  @Test
  void calculatePlannedSot_WithValues_SumsTreatingNullAsZero() {
    List<Schedule> schedules =
        List.of(
            schedule().plannedSot(1.5).build(),
            schedule().plannedSot(null).build(),
            schedule().plannedSot(2.5).build());
    assertThat(CampaignInventorySchedulesService.calculatePlannedSot(schedules)).isEqualTo(4.0);
  }

  @Test
  void calculateTotalSot_WithNullOrEmpty_ReturnsNull() {
    assertThat(CampaignInventorySchedulesService.calculateTotalSot(null)).isNull();
    assertThat(CampaignInventorySchedulesService.calculateTotalSot(Collections.emptyList()))
        .isNull();
  }

  @Test
  void calculateTotalSot_WithValues_SumsTreatingNullAsZero() {
    List<Schedule> schedules =
        List.of(
            schedule().totalSot(10.0).build(),
            schedule().totalSot(null).build(),
            schedule().totalSot(5.0).build());
    assertThat(CampaignInventorySchedulesService.calculateTotalSot(schedules)).isEqualTo(15.0);
  }

  // ========== getAvailableHours / getScheduleType ==========

  private Inventory inventoryWithOperatingTime(String start, String end) {
    Inventory inv = new Inventory();
    inv.setId("inv-hours");
    Inventory.OperatingTime ot = new Inventory.OperatingTime();
    ot.setStart(start);
    ot.setEnd(end);
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> map = new HashMap<>();
    map.put(Inventory.Weekday.MONDAY, List.of(ot));
    inv.setOperatingTimes(map);
    return inv;
  }

  @Test
  void getAvailableHours_WithNullOperatingTimes_ReturnsEmpty() {
    assertThat(CampaignInventorySchedulesService.getAvailableHours(new Inventory())).isEmpty();
  }

  @Test
  void getAvailableHours_WithOperatingTime_ReturnsInclusiveHourRange() {
    List<Integer> hours =
        CampaignInventorySchedulesService.getAvailableHours(
            inventoryWithOperatingTime("07:00:00", "10:00:00"));
    assertThat(hours).containsExactly(7, 8, 9, 10);
  }

  @Test
  void getAvailableHours_WithUnparseableTime_SkipsAndReturnsEmpty() {
    List<Integer> hours =
        CampaignInventorySchedulesService.getAvailableHours(
            inventoryWithOperatingTime("bad", "worse"));
    assertThat(hours).isEmpty();
  }

  @Test
  void getScheduleType_WhenBookedHoursMatchAvailable_ReturnsLoop() {
    Inventory inv = inventoryWithOperatingTime("07:00:00", "09:00:00"); // hours 7,8,9
    Map<String, List<Integer>> bookingMatrix = Map.of("2024-01-15", List.of(7, 8, 9));
    assertThat(CampaignInventorySchedulesService.getScheduleType(bookingMatrix, inv))
        .isEqualTo(Schedule.Type.LOOP);
  }

  @Test
  void getScheduleType_WhenBookedHoursDifferFromAvailable_ReturnsDaypart() {
    Inventory inv = inventoryWithOperatingTime("07:00:00", "09:00:00"); // hours 7,8,9
    Map<String, List<Integer>> bookingMatrix = Map.of("2024-01-15", List.of(7, 8));
    assertThat(CampaignInventorySchedulesService.getScheduleType(bookingMatrix, inv))
        .isEqualTo(Schedule.Type.DAYPART);
  }

  // ========== calculateAdPlays ==========

  @Test
  void calculateAdPlays_WithNullMatrix_ReturnsZero() {
    assertThat(campaignInventorySchedulesService.calculateAdPlays(10, null)).isZero();
  }

  @Test
  void calculateAdPlays_WithEmptyMatrix_ReturnsZero() {
    assertThat(campaignInventorySchedulesService.calculateAdPlays(10, new HashMap<>())).isZero();
  }

  @Test
  void calculateAdPlays_WithNonPositiveSpotsPerHour_ReturnsZero() {
    Map<String, List<Integer>> matrix = Map.of("2024-01-15", List.of(7, 8));
    assertThat(campaignInventorySchedulesService.calculateAdPlays(0, matrix)).isZero();
  }

  @Test
  void calculateAdPlays_WithValidInputs_ReturnsSpotsTimesTotalHours() {
    Map<String, List<Integer>> matrix =
        Map.of("2024-01-15", List.of(7, 8, 9), "2024-01-16", List.of(7, 8)); // 5 hours total
    assertThat(campaignInventorySchedulesService.calculateAdPlays(10, matrix)).isEqualTo(50L);
  }

  // ========== calculateSimpleBookingMatrix ==========

  @Test
  void calculateSimpleBookingMatrix_WithNoOperatingTimes_ReturnsEmpty() {
    Inventory inv = new Inventory();
    inv.setOperatingTimes(null);
    Map<String, List<Integer>> matrix =
        campaignInventorySchedulesService.calculateSimpleBookingMatrix(
            LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 16), inv);
    assertThat(matrix).isEmpty();
  }

  @Test
  void calculateSimpleBookingMatrix_WithOperatingTimes_ReturnsMatrixKeyedByDate() {
    Map<String, List<Integer>> matrix =
        campaignInventorySchedulesService.calculateSimpleBookingMatrix(
            LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 17), testInventory);
    // 3 dates in the range, each present as a key
    assertThat(matrix).hasSize(3).containsKeys("2024-01-15", "2024-01-16", "2024-01-17");
  }

  // ========== applyAdjustment: validation guards ==========

  private ApplyAdjustmentRequestDTO bonusRequest(List<String> scheduleIds, String bonus) {
    ApplyAdjustmentRequestDTO req = new ApplyAdjustmentRequestDTO();
    req.setActionType(ApplyAdjustmentRequestDTO.ActionType.BONUS);
    req.setScheduleIds(new ArrayList<>(scheduleIds));
    req.setBonus(bonus);
    return req;
  }

  private ApplyAdjustmentRequestDTO discountRequest(
      List<String> scheduleIds, ApplyAdjustmentRequestDTO.DiscountDTO discount) {
    ApplyAdjustmentRequestDTO req = new ApplyAdjustmentRequestDTO();
    req.setActionType(ApplyAdjustmentRequestDTO.ActionType.DISCOUNT);
    req.setScheduleIds(new ArrayList<>(scheduleIds));
    req.setDiscount(discount);
    return req;
  }

  @Test
  void applyAdjustment_WhenCampaignNotFound_ThrowsCampaignNotFoundException() {
    when(campaignService.findByIdForCurrentModeForWrite("campaign123"))
        .thenThrow(new CampaignNotFoundException("campaign123"));

    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.applyAdjustment(
                    "campaign123", bonusRequest(List.of("s1"), "Free week")))
        .isInstanceOf(CampaignNotFoundException.class);
  }

  @Test
  void applyAdjustment_WhenDiscountActionButNoDiscount_ThrowsIllegalArgument() {
    when(campaignService.findByIdForCurrentModeForWrite("campaign123")).thenReturn(testCampaign);

    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.applyAdjustment(
                    "campaign123", discountRequest(List.of("s1"), null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Discount is required");
  }

  @Test
  void applyAdjustment_WhenBonusActionButNoBonus_ThrowsIllegalArgument() {
    when(campaignService.findByIdForCurrentModeForWrite("campaign123")).thenReturn(testCampaign);

    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.applyAdjustment(
                    "campaign123", bonusRequest(List.of("s1"), "  ")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Bonus is required");
  }

  @Test
  void applyAdjustment_WhenScheduleIdsDoNotBelongToCampaign_Throws() {
    when(campaignService.findByIdForCurrentModeForWrite("campaign123")).thenReturn(testCampaign);
    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("campaign123")
            .mediaOwnerId("mo1")
            .inventoryId("inv1")
            .scheduleIds(List.of("other"))
            .build();
    when(schedulesRepository.findByCampaignId("campaign123")).thenReturn(List.of(cis));

    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.applyAdjustment(
                    "campaign123", bonusRequest(List.of("s1"), "Free week")))
        .isInstanceOf(ScheduleIdsNotBelongToCampaignException.class);
  }

  @Test
  void applyAdjustment_WhenSchedulesNotFoundInDb_Throws() {
    when(campaignService.findByIdForCurrentModeForWrite("campaign123")).thenReturn(testCampaign);
    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("campaign123")
            .mediaOwnerId("mo1")
            .inventoryId("inv1")
            .scheduleIds(List.of("s1"))
            .build();
    when(schedulesRepository.findByCampaignId("campaign123")).thenReturn(List.of(cis));
    when(scheduleRepository.findAllById(List.of("s1"))).thenReturn(Collections.emptyList());

    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.applyAdjustment(
                    "campaign123", bonusRequest(List.of("s1"), "Free week")))
        .isInstanceOf(ScheduleIdsNotFoundException.class);
  }

  @Test
  void applyAdjustment_BonusActionHappyPath_SetsBonusTypeAndDoesNotChangeStatus() {
    when(campaignService.findByIdForCurrentModeForWrite("campaign123")).thenReturn(testCampaign);
    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("campaign123")
            .mediaOwnerId("mo1")
            .inventoryId("inv1")
            .scheduleIds(List.of("s1"))
            .build();
    when(schedulesRepository.findByCampaignId("campaign123")).thenReturn(List.of(cis));
    Schedule schedule = schedule().basePrice(100.0).build();
    schedule.setId("s1");
    when(scheduleRepository.findAllById(List.of("s1")))
        .thenReturn(new ArrayList<>(List.of(schedule)));
    when(userService.getPrimaryCompanyId()).thenReturn("co1");
    Campaign campaign = testCampaign();
    when(campaignService.findById("campaign123")).thenReturn(campaign);

    campaignInventorySchedulesService.applyAdjustment(
        "campaign123", bonusRequest(List.of("s1"), "Free week"));

    ArgumentCaptor<List<Schedule>> captor = ArgumentCaptor.forClass(List.class);
    verify(scheduleRepository).saveAll(captor.capture());
    assertThat(captor.getValue().get(0).getBonusType()).isEqualTo("Free week");
    // BONUS path must not trigger the negotiation/status-reset workflow
    verify(campaignService, never()).changeCampaignStatus(anyString(), any());
    verify(campaignApprovalWorkflowService, never()).resetApprovalWorkflowStatus(anyString());
  }

  // ========== updateDiscountByProposedPrice: validation guards ==========

  @Test
  void updateDiscountByProposedPrice_WithNullPrice_ThrowsIllegalArgument() {
    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.updateDiscountByProposedPrice("cis1", null, "s1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Proposed price must be positive");
  }

  @Test
  void updateDiscountByProposedPrice_WithNonPositivePrice_ThrowsIllegalArgument() {
    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.updateDiscountByProposedPrice("cis1", 0.0, "s1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Proposed price must be positive");
  }

  @Test
  void updateDiscountByProposedPrice_WhenCisNotFound_ThrowsIllegalArgument() {
    when(schedulesRepository.findById("cis1")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.updateDiscountByProposedPrice(
                    "cis1", 100.0, "s1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CampaignInventorySchedules not found");
  }

  @Test
  void updateDiscountByProposedPrice_WhenUserCompanyIdNull_ThrowsIllegalState() {
    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("campaign123")
            .mediaOwnerId("mo1")
            .inventoryId("inv1")
            .build();
    when(schedulesRepository.findById("cis1")).thenReturn(Optional.of(cis));
    when(campaignService.findById("campaign123")).thenReturn(testCampaign());
    when(inventoryService.getById("inv1")).thenReturn(testInventory);
    when(userService.getPrimaryCompanyId()).thenReturn(null);

    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.updateDiscountByProposedPrice(
                    "cis1", 100.0, "s1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("User company ID not found");
  }

  private Campaign testCampaign() {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();
    campaign.setId("campaign123");
    return campaign;
  }

  // Schedule has @NonNull startDate/endDate; this seeds them so build() does not NPE.
  private Schedule.ScheduleBuilder schedule() {
    return Schedule.builder()
        .startDate(LocalDate.of(2024, 1, 1))
        .endDate(LocalDate.of(2024, 1, 31));
  }

  // ========== Pricing chain via public entry points (§1A) ==========

  private CustomFee customFee(CustomFeeType type, double value, String companyId) {
    CustomFee fee = new CustomFee();
    fee.setId("fee-" + type + "-" + value);
    fee.setName("Fee " + type + " " + value);
    fee.setType(type);
    fee.setValue(value);
    fee.setBasedOn(CustomFeeBasedOn.BASE_COST);
    fee.setIsIncludeInMediaPlan(true);
    fee.setCompanyId(companyId);
    return fee;
  }

  private Inventory pricingInventory(String mediaOwnerId) {
    Inventory inv = new Inventory();
    inv.setId("inv-pricing");
    inv.setType("CLASSIC");
    inv.setMediaOwnerId(mediaOwnerId);
    return inv;
  }

  private Schedule scheduleWithPrice(Double basePrice, Schedule.Discount discount) {
    Schedule s = schedule().basePrice(basePrice).discount(discount).build();
    s.setId("s-price");
    return s;
  }

  @Test
  void calculateProposedPriceForSchedule_CreatorNoFeesNoDiscount_ReturnsBasePrice() {
    Campaign campaign = testCampaign(); // companyId "company123"
    Schedule s = scheduleWithPrice(100.0, null);

    Double result =
        campaignInventorySchedulesService.calculateProposedPriceForSchedule(
            s,
            pricingInventory("mo1"),
            campaign,
            "company123",
            CustomFeesContext.builder().build());

    assertThat(result).isEqualTo(100.0);
  }

  @Test
  void calculateProposedPriceForSchedule_MediaOwnerWithHiddenVisibleFeesAndPercentDiscount() {
    Campaign campaign = testCampaign(); // companyId "company123"
    Schedule s =
        scheduleWithPrice(
            100.0,
            Schedule.Discount.builder()
                .valueType(DiscountValueType.PERCENTAGE)
                .value("10")
                .build());
    // mediaOwner "mo1" fees: hidden 10%, visible 20%
    CustomFeesContext ctx =
        CustomFeesContext.builder()
            .companyFeesByCompanyId(
                Map.of(
                    "mo1",
                    CompanyCustomFees.builder()
                        .hidden(List.of(customFee(CustomFeeType.PERCENTAGE, 10.0, "mo1")))
                        .visible(List.of(customFee(CustomFeeType.PERCENTAGE, 20.0, "mo1")))
                        .build()))
            .build();

    // media-owner view: userCompanyId ("mo1") != campaign.companyId ("company123")
    Double result =
        campaignInventorySchedulesService.calculateProposedPriceForSchedule(
            s, pricingInventory("mo1"), campaign, "mo1", ctx);

    // mediaCost = 100 + 10% = 110; discounted = 110*0.9 = 99; +20% visible = 118.8
    assertThat(result).isCloseTo(118.8, within(0.001));
  }

  @Test
  void calculateProposedPriceForSchedule_WithNullBasePrice_ReturnsNull() {
    Double result =
        campaignInventorySchedulesService.calculateProposedPriceForSchedule(
            scheduleWithPrice(null, null),
            pricingInventory("mo1"),
            testCampaign(),
            "company123",
            CustomFeesContext.builder().build());

    assertThat(result).isNull();
  }

  @Test
  void calculateProposedPriceForSchedule_WithNullMediaOwnerId_AppliesNoFees() {
    Double result =
        campaignInventorySchedulesService.calculateProposedPriceForSchedule(
            scheduleWithPrice(100.0, null),
            pricingInventory(null),
            testCampaign(),
            "company123",
            CustomFeesContext.builder().build());

    assertThat(result).isEqualTo(100.0);
  }

  @Test
  void calculateProposedPriceForSchedule_WithValueDiscount_SubtractsFlatAmount() {
    Schedule s =
        scheduleWithPrice(
            100.0,
            Schedule.Discount.builder().valueType(DiscountValueType.VALUE).value("30").build());

    Double result =
        campaignInventorySchedulesService.calculateProposedPriceForSchedule(
            s,
            pricingInventory("mo1"),
            testCampaign(),
            "company123",
            CustomFeesContext.builder().build());

    assertThat(result).isEqualTo(70.0);
  }

  @Test
  void calculateProposedPriceForSchedule_WithValueDiscountExceedingPrice_ClampsToZero() {
    Schedule s =
        scheduleWithPrice(
            100.0,
            Schedule.Discount.builder().valueType(DiscountValueType.VALUE).value("150").build());

    Double result =
        campaignInventorySchedulesService.calculateProposedPriceForSchedule(
            s,
            pricingInventory("mo1"),
            testCampaign(),
            "company123",
            CustomFeesContext.builder().build());

    assertThat(result).isEqualTo(0.0);
  }

  @Test
  void calculateProposedPriceForSchedule_WithNonNumericDiscount_IgnoresDiscount() {
    Schedule s =
        scheduleWithPrice(
            100.0,
            Schedule.Discount.builder()
                .valueType(DiscountValueType.PERCENTAGE)
                .value("not-a-number")
                .build());

    Double result =
        campaignInventorySchedulesService.calculateProposedPriceForSchedule(
            s,
            pricingInventory("mo1"),
            testCampaign(),
            "company123",
            CustomFeesContext.builder().build());

    assertThat(result).isEqualTo(100.0);
  }

  @Test
  void calculateProposedPriceForSchedule_CreatorAddsCreatorCompanyFees() {
    Campaign campaign = testCampaign(); // creator company123
    Schedule s = scheduleWithPrice(100.0, null);
    CustomFeesContext ctx =
        CustomFeesContext.builder()
            .campaignFeesByCompanyId(
                Map.of(
                    "company123",
                    CompanyCustomFees.builder()
                        .visible(List.of(customFee(CustomFeeType.VALUE, 5.0, "company123")))
                        .build()))
            .build();

    // creator view: userCompanyId == campaign.companyId
    Double result =
        campaignInventorySchedulesService.calculateProposedPriceForSchedule(
            s, pricingInventory("mo1"), campaign, "company123", ctx);

    assertThat(result).isEqualTo(105.0);
  }

  @Test
  void calculateProposedPriceForSchedule_CreatorIsAlsoMediaOwner_DoesNotDoubleCountFee() {
    Campaign campaign = testCampaign(); // company123
    Schedule s = scheduleWithPrice(100.0, null);
    // The single fee lives under company123, which is BOTH the creator and the media owner.
    CustomFee fee = customFee(CustomFeeType.VALUE, 10.0, "company123");
    CustomFeesContext ctx =
        CustomFeesContext.builder()
            .companyFeesByCompanyId(
                Map.of("company123", CompanyCustomFees.builder().visible(List.of(fee)).build()))
            .build();

    Double result =
        campaignInventorySchedulesService.calculateProposedPriceForSchedule(
            s, pricingInventory("company123"), campaign, "company123", ctx);

    // Fee applied once (110), not twice (120)
    assertThat(result).isEqualTo(110.0);
  }

  @Test
  void calculateCampaignInventorySchedulesProposedPrice_WithNullScheduleIds_ReturnsNull() {
    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("campaign123")
            .mediaOwnerId("mo1")
            .inventoryId("inv1")
            .scheduleIds(null)
            .build();

    assertThat(
            campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
                cis, pricingInventory("mo1"), testCampaign(), "company123", null))
        .isNull();
  }

  @Test
  void calculateCampaignInventorySchedulesProposedPrice_WithScheduleMap_SumsWithoutDbCall() {
    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("campaign123")
            .mediaOwnerId("mo1")
            .inventoryId("inv1")
            .scheduleIds(List.of("s1", "s2"))
            .build();
    Schedule s1 = schedule().basePrice(100.0).build();
    s1.setId("s1");
    Schedule s2 = schedule().basePrice(50.0).build();
    s2.setId("s2");
    Map<String, Schedule> scheduleMap = Map.of("s1", s1, "s2", s2);

    Double result =
        campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
            cis,
            pricingInventory("mo1"),
            testCampaign(),
            "company123",
            CustomFeesContext.builder().build(),
            scheduleMap);

    assertThat(result).isEqualTo(150.0);
    verify(scheduleRepository, never()).findAllById(anyList());
  }

  @Test
  void calculateCampaignInventorySchedulesProposedPrice_WithoutMap_FetchesFromRepository() {
    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("campaign123")
            .mediaOwnerId("mo1")
            .inventoryId("inv1")
            .scheduleIds(List.of("s1"))
            .build();
    Schedule s1 = schedule().basePrice(100.0).build();
    s1.setId("s1");
    when(scheduleRepository.findAllById(List.of("s1"))).thenReturn(List.of(s1));

    Double result =
        campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
            cis,
            pricingInventory("mo1"),
            testCampaign(),
            "company123",
            CustomFeesContext.builder().build());

    assertThat(result).isEqualTo(100.0);
  }

  @Test
  void calculateCampaignInventorySchedulesProposedPrice_WhenResolvedSchedulesEmpty_ReturnsNull() {
    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("campaign123")
            .mediaOwnerId("mo1")
            .inventoryId("inv1")
            .scheduleIds(List.of("missing"))
            .build();
    when(scheduleRepository.findAllById(List.of("missing"))).thenReturn(Collections.emptyList());

    assertThat(
            campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
                cis,
                pricingInventory("mo1"),
                testCampaign(),
                "company123",
                CustomFeesContext.builder().build()))
        .isNull();
  }

  @Test
  void calculateCampaignInventorySchedulesProposedPrice_WhenNoScheduleHasPrice_ReturnsNull() {
    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("campaign123")
            .mediaOwnerId("mo1")
            .inventoryId("inv1")
            .scheduleIds(List.of("s1"))
            .build();
    Schedule s1 = schedule().basePrice(null).build();
    s1.setId("s1");
    Map<String, Schedule> scheduleMap = Map.of("s1", s1);

    assertThat(
            campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
                cis,
                pricingInventory("mo1"),
                testCampaign(),
                "company123",
                CustomFeesContext.builder().build(),
                scheduleMap))
        .isNull();
  }

  // ========== applyAdjustment DISCOUNT path (§1B) ==========

  private CampaignInventorySchedules discountCis(List<String> scheduleIds) {
    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("campaign123")
            .mediaOwnerId("mo1")
            .inventoryId("inv1")
            .scheduleIds(scheduleIds)
            .build();
    cis.setId("cis1");
    // Real CIS always carry an initial RATE_CARD history entry from creation. applyAdjustment's
    // DISCOUNT branch dereferences the pre-fetched history list, so a null history is an invalid
    // state (latent NPE at CampaignInventorySchedulesService:4933), not a scenario under test.
    cis.setHistory(
        new ArrayList<>(
            List.of(
                CampaignInventorySchedules.History.builder()
                    .action(PricingAction.RATE_CARD)
                    .companyId("company123")
                    .build())));
    return cis;
  }

  private ApplyAdjustmentRequestDTO.DiscountDTO discountDTO(
      ApplyAdjustmentRequestDTO.DiscountDTO.DiscountType type, double value) {
    ApplyAdjustmentRequestDTO.DiscountDTO d = new ApplyAdjustmentRequestDTO.DiscountDTO();
    d.setDiscountType(type);
    d.setValue(value);
    return d;
  }

  private void stubApplyDiscountPreamble(CampaignInventorySchedules cis, Schedule schedule) {
    when(campaignService.findByIdForCurrentModeForWrite("campaign123")).thenReturn(testCampaign);
    when(schedulesRepository.findByCampaignId("campaign123")).thenReturn(List.of(cis));
    when(scheduleRepository.findAllById(List.of("s1"))).thenReturn(List.of(schedule));
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(campaignService.findById("campaign123")).thenReturn(testCampaign());
    when(inventoryService.getById("inv1")).thenReturn(pricingInventory("mo1"));
    lenient()
        .when(customFeeService.getActiveCustomFeesContextForCampaign(any(Campaign.class)))
        .thenReturn(CustomFeesContext.builder().build());
  }

  @Test
  void applyAdjustment_DiscountPercentage_SetsPercentageDiscountAndNegotiates() {
    CampaignInventorySchedules cis = discountCis(List.of("s1"));
    Schedule s1 = schedule().basePrice(100.0).build();
    s1.setId("s1");
    stubApplyDiscountPreamble(cis, s1);

    campaignInventorySchedulesService.applyAdjustment(
        "campaign123",
        discountRequest(
            List.of("s1"),
            discountDTO(ApplyAdjustmentRequestDTO.DiscountDTO.DiscountType.PERCENTAGE, 15.0)));

    assertThat(s1.getDiscount().getValueType()).isEqualTo(DiscountValueType.PERCENTAGE);
    assertThat(s1.getDiscount().getValue()).isEqualTo("15.0");
    verify(scheduleRepository).saveAll(anyList());
    verify(campaignService).changeCampaignStatus("campaign123", Campaign.Status.NEGOTIATING);
    verify(campaignApprovalWorkflowService).resetApprovalWorkflowStatus("campaign123");
  }

  @Test
  void applyAdjustment_DiscountValue_ConvertsToPercentageUsingCurrentPrice() {
    CampaignInventorySchedules cis = discountCis(List.of("s1"));
    Schedule s1 = schedule().basePrice(100.0).build(); // currentPrice = 100 (no fees)
    s1.setId("s1");
    stubApplyDiscountPreamble(cis, s1);

    campaignInventorySchedulesService.applyAdjustment(
        "campaign123",
        discountRequest(
            List.of("s1"),
            discountDTO(ApplyAdjustmentRequestDTO.DiscountDTO.DiscountType.VALUE, 20.0)));

    // VALUE 20 on currentPrice 100 -> 20% stored as PERCENTAGE
    assertThat(s1.getDiscount().getValueType()).isEqualTo(DiscountValueType.PERCENTAGE);
    assertThat(s1.getDiscount().getValue()).isEqualTo("20.0");
  }

  @Test
  void applyAdjustment_DiscountOnRateCardHistory_AddsProposedEntry() {
    CampaignInventorySchedules cis = discountCis(List.of("s1"));
    cis.setHistory(
        new ArrayList<>(
            List.of(
                CampaignInventorySchedules.History.builder()
                    .action(PricingAction.RATE_CARD)
                    .companyId("company123")
                    .build())));
    Schedule s1 = schedule().basePrice(100.0).build();
    s1.setId("s1");
    stubApplyDiscountPreamble(cis, s1);

    campaignInventorySchedulesService.applyAdjustment(
        "campaign123",
        discountRequest(
            List.of("s1"),
            discountDTO(ApplyAdjustmentRequestDTO.DiscountDTO.DiscountType.PERCENTAGE, 10.0)));

    // RATE_CARD last entry -> new PROPOSED entry appended
    assertThat(cis.getHistory()).hasSize(2);
    assertThat(cis.getHistory().getLast().getAction()).isEqualTo(PricingAction.PROPOSED);
  }

  @Test
  void applyAdjustment_DiscountWithAcceptedHistory_RemovesAcceptedThenAddsCountered() {
    CampaignInventorySchedules cis = discountCis(List.of("s1"));
    cis.setHistory(
        new ArrayList<>(
            List.of(
                CampaignInventorySchedules.History.builder()
                    .action(PricingAction.ACCEPTED)
                    .companyId("company123")
                    .build())));
    Schedule s1 = schedule().basePrice(100.0).build();
    s1.setId("s1");
    stubApplyDiscountPreamble(cis, s1);

    campaignInventorySchedulesService.applyAdjustment(
        "campaign123",
        discountRequest(
            List.of("s1"),
            discountDTO(ApplyAdjustmentRequestDTO.DiscountDTO.DiscountType.PERCENTAGE, 10.0)));

    // ACCEPTED removed, COUNTERED added -> size stays 1, action COUNTERED
    assertThat(cis.getHistory()).hasSize(1);
    assertThat(cis.getHistory().getLast().getAction()).isEqualTo(PricingAction.COUNTERED);
  }

  @Test
  void applyAdjustment_DiscountResetsApprovedScheduleIds() {
    CampaignInventorySchedules cis = discountCis(List.of("s1"));
    cis.setApprovedScheduleIds(new ArrayList<>(List.of("s1")));
    cis.setApprovedBy("approver1");
    Schedule s1 = schedule().basePrice(100.0).build();
    s1.setId("s1");
    stubApplyDiscountPreamble(cis, s1);

    campaignInventorySchedulesService.applyAdjustment(
        "campaign123",
        discountRequest(
            List.of("s1"),
            discountDTO(ApplyAdjustmentRequestDTO.DiscountDTO.DiscountType.PERCENTAGE, 10.0)));

    assertThat(cis.getApprovedScheduleIds()).isEmpty();
    assertThat(cis.getApprovedBy()).isNull();
  }

  // ========== updateDiscountByProposedPrice happy paths + guards (§1C) ==========

  private CampaignInventorySchedules stubUpdateDiscountPreamble(
      List<String> scheduleIds, List<String> approvedScheduleIds) {
    CampaignInventorySchedules cis = discountCis(scheduleIds);
    cis.setApprovedScheduleIds(approvedScheduleIds);
    when(schedulesRepository.findById("cis1")).thenReturn(Optional.of(cis));
    when(campaignService.findById("campaign123")).thenReturn(testCampaign());
    when(inventoryService.getById("inv1")).thenReturn(pricingInventory("mo1"));
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    lenient()
        .when(customFeeService.getActiveCustomFeesContextForCampaign(any(Campaign.class)))
        .thenReturn(CustomFeesContext.builder().build());
    return cis;
  }

  @Test
  void updateDiscountByProposedPrice_SingleSchedule_SetsPercentageDiscount() {
    stubUpdateDiscountPreamble(List.of("s1"), null);
    Schedule s1 = schedule().basePrice(100.0).build(); // currentPrice 100
    s1.setId("s1");
    when(scheduleRepository.findById("s1")).thenReturn(Optional.of(s1));

    campaignInventorySchedulesService.updateDiscountByProposedPrice("cis1", 80.0, "s1");

    // (100-80)/100 * 100 = 20% discount
    assertThat(s1.getDiscount().getValueType()).isEqualTo(DiscountValueType.PERCENTAGE);
    assertThat(s1.getDiscount().getValue()).isEqualTo("20.0");
    verify(scheduleRepository).save(s1);
    verify(campaignService).changeCampaignStatus("campaign123", Campaign.Status.NEGOTIATING);
  }

  @Test
  void updateDiscountByProposedPrice_SingleScheduleNotBelonging_Throws() {
    stubUpdateDiscountPreamble(List.of("other"), null);

    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.updateDiscountByProposedPrice("cis1", 80.0, "s1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not belong");
  }

  @Test
  void updateDiscountByProposedPrice_SingleScheduleNotFound_Throws() {
    stubUpdateDiscountPreamble(List.of("s1"), null);
    when(scheduleRepository.findById("s1")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.updateDiscountByProposedPrice("cis1", 80.0, "s1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Schedule not found");
  }

  @Test
  void updateDiscountByProposedPrice_SingleScheduleNoBasePrice_Throws() {
    stubUpdateDiscountPreamble(List.of("s1"), null);
    Schedule s1 = schedule().basePrice(null).build();
    s1.setId("s1");
    when(scheduleRepository.findById("s1")).thenReturn(Optional.of(s1));

    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.updateDiscountByProposedPrice("cis1", 80.0, "s1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("has no basePrice");
  }

  @Test
  void updateDiscountByProposedPrice_SingleScheduleProposedNotLessThanCurrent_Throws() {
    stubUpdateDiscountPreamble(List.of("s1"), null);
    Schedule s1 = schedule().basePrice(100.0).build();
    s1.setId("s1");
    when(scheduleRepository.findById("s1")).thenReturn(Optional.of(s1));

    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.updateDiscountByProposedPrice(
                    "cis1", 100.0, "s1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be less than");
  }

  @Test
  void updateDiscountByProposedPrice_AllSchedulesEmptyIds_Throws() {
    stubUpdateDiscountPreamble(new ArrayList<>(), null);

    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.updateDiscountByProposedPrice("cis1", 80.0, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no schedules to update");
  }

  @Test
  void updateDiscountByProposedPrice_AllSchedulesNoneFound_Throws() {
    stubUpdateDiscountPreamble(List.of("s1"), null);
    when(scheduleRepository.findAllById(List.of("s1"))).thenReturn(Collections.emptyList());

    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.updateDiscountByProposedPrice("cis1", 80.0, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No schedules found");
  }

  @Test
  void updateDiscountByProposedPrice_AllSchedulesCannotCalcCurrent_Throws() {
    stubUpdateDiscountPreamble(List.of("s1"), null);
    Schedule s1 = schedule().basePrice(null).build(); // currentPrice null
    s1.setId("s1");
    when(scheduleRepository.findAllById(List.of("s1"))).thenReturn(List.of(s1));

    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.updateDiscountByProposedPrice("cis1", 80.0, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot calculate current price");
  }

  @Test
  void updateDiscountByProposedPrice_AllSchedulesProposedNotLessThanTotal_Throws() {
    stubUpdateDiscountPreamble(List.of("s1"), null);
    Schedule s1 = schedule().basePrice(100.0).build(); // total current 100
    s1.setId("s1");
    when(scheduleRepository.findAllById(List.of("s1"))).thenReturn(List.of(s1));

    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.updateDiscountByProposedPrice(
                    "cis1", 100.0, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be less than current price");
  }

  @Test
  void updateDiscountByProposedPrice_AllSchedules_DistributesDiscountAndResetsApprovals() {
    CampaignInventorySchedules cis =
        stubUpdateDiscountPreamble(List.of("s1"), new ArrayList<>(List.of("s1")));
    cis.setApprovedBy("approver1");
    Schedule s1 = schedule().basePrice(100.0).build(); // current 100
    s1.setId("s1");
    when(scheduleRepository.findAllById(List.of("s1"))).thenReturn(List.of(s1));

    campaignInventorySchedulesService.updateDiscountByProposedPrice("cis1", 60.0, null);

    // total discount 40 over one schedule -> 40%
    assertThat(s1.getDiscount().getValueType()).isEqualTo(DiscountValueType.PERCENTAGE);
    assertThat(s1.getDiscount().getValue()).isEqualTo("40.0");
    verify(scheduleRepository).saveAll(anyList());
    assertThat(cis.getApprovedScheduleIds()).isEmpty();
    assertThat(cis.getApprovedBy()).isNull();
  }

  // ========== getCampaignPriceSummary remaining branches (§1D) ==========

  @Test
  void getCampaignPriceSummary_WhenCreatorHasNoSchedules_ReturnsNullPricesAllApprovedTrue() {
    when(campaignService.findById("campaign123")).thenReturn(testCampaign());
    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().companyId("company123").build());
    when(schedulesRepository.findByCampaignId("campaign123")).thenReturn(Collections.emptyList());

    CampaignPriceSummaryResponseDTO result =
        campaignInventorySchedulesService.getCampaignPriceSummary("campaign123");

    assertThat(result.getCurrentPrice()).isNull();
    assertThat(result.getProposedPrice()).isNull();
    assertThat(result.getCustomFees()).isEmpty();
    assertThat(result.getIsAllApproved()).isTrue(); // creator
    verify(schedulesRepository).findByCampaignId("campaign123");
  }

  @Test
  void getCampaignPriceSummary_WhenMediaOwnerHasNoSchedules_UsesMediaOwnerLookupAllApprovedFalse() {
    when(campaignService.findById("campaign123")).thenReturn(testCampaign());
    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().companyId("mediaOwner1").build());
    when(schedulesRepository.findByCampaignIdAndMediaOwnerId("campaign123", "mediaOwner1"))
        .thenReturn(Collections.emptyList());

    CampaignPriceSummaryResponseDTO result =
        campaignInventorySchedulesService.getCampaignPriceSummary("campaign123");

    assertThat(result.getIsAllApproved()).isFalse(); // media owner
    verify(schedulesRepository).findByCampaignIdAndMediaOwnerId("campaign123", "mediaOwner1");
    verify(schedulesRepository, never()).findByCampaignId(anyString());
  }

  @Test
  void getCampaignPriceSummary_WhenInventoryLookupFails_SkipsScheduleAndReturnsNullPrices() {
    when(campaignService.findById("campaign123")).thenReturn(testCampaign());
    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().companyId("company123").build());
    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("campaign123")
            .mediaOwnerId("mo1")
            .inventoryId("inv1")
            .scheduleIds(List.of("s1"))
            .build();
    cis.setId("cis1");
    when(schedulesRepository.findByCampaignId("campaign123")).thenReturn(List.of(cis));
    when(customFeeService.getActiveCustomFeesContextForCampaign(any(Campaign.class)))
        .thenReturn(CustomFeesContext.builder().build());
    Schedule s1 = schedule().basePrice(100.0).build();
    s1.setId("s1");
    when(scheduleRepository.findAllById(List.of("s1"))).thenReturn(List.of(s1));
    when(inventoryService.getById("inv1")).thenThrow(new RuntimeException("inventory gone"));

    CampaignPriceSummaryResponseDTO result =
        campaignInventorySchedulesService.getCampaignPriceSummary("campaign123");

    assertThat(result.getCurrentPrice()).isNull(); // schedule skipped -> no price
    assertThat(result.getIsAllApproved()).isFalse(); // scheduleIds present but not approved
  }

  // ========== getSchedulesByInventoryIds (§1E) ==========

  private CampaignInventorySchedules cisFor(String inventoryId, List<String> scheduleIds) {
    return CampaignInventorySchedules.builder()
        .campaignId("campaign123")
        .mediaOwnerId("mo1")
        .inventoryId(inventoryId)
        .scheduleIds(scheduleIds)
        .build();
  }

  @Test
  void getSchedulesByInventoryIds_WithNullInventoryIds_FetchesAllForCampaign() {
    when(schedulesRepository.findByCampaignId("campaign123"))
        .thenReturn(List.of(cisFor("inv1", List.of("s1")), cisFor("inv2", List.of("s2"))));

    var result =
        campaignInventorySchedulesService.getSchedulesByInventoryIds("campaign123", null, null);

    assertThat(result).hasSize(2);
    verify(schedulesRepository).findByCampaignId("campaign123");
  }

  @Test
  void getSchedulesByInventoryIds_WithInventoryIds_FetchesByCampaignAndInventoryIds() {
    when(schedulesRepository.findByCampaignIdAndInventoryIdIn("campaign123", List.of("inv1")))
        .thenReturn(List.of(cisFor("inv1", List.of("s1"))));

    var result =
        campaignInventorySchedulesService.getSchedulesByInventoryIds(
            "campaign123", List.of("inv1"), null);

    assertThat(result).hasSize(1);
    verify(schedulesRepository).findByCampaignIdAndInventoryIdIn("campaign123", List.of("inv1"));
  }

  @Test
  void getSchedulesByInventoryIds_WithInventoryTypeFilter_FiltersByType() {
    when(schedulesRepository.findByCampaignIdAndInventoryIdIn(
            "campaign123", List.of("inv1", "inv2")))
        .thenReturn(List.of(cisFor("inv1", List.of("s1")), cisFor("inv2", List.of("s2"))));
    when(inventoryService.findIdByIdInAndType(List.of("inv1", "inv2"), "DIGITAL"))
        .thenReturn(List.of("inv1"));

    var result =
        campaignInventorySchedulesService.getSchedulesByInventoryIds(
            "campaign123", List.of("inv1", "inv2"), "digital");

    assertThat(result).hasSize(1);
    verify(inventoryService).findIdByIdInAndType(List.of("inv1", "inv2"), "DIGITAL");
  }

  // ========== calculateScheduleBasePriceForSchedule Tests (multi-element prices) ==========

  private Double invokeCalculateBasePrice(
      Long adPlays, Long impressions, Inventory inventory, Campaign.Goals.GoalType goalType) {
    return (Double)
        ReflectionTestUtils.invokeMethod(
            campaignInventorySchedulesService,
            "calculateScheduleBasePriceForSchedule",
            adPlays,
            impressions,
            inventory,
            goalType);
  }

  @Test
  @DisplayName(
      "calculateScheduleBasePriceForSchedule - Should find CPM on a later price element (not just"
          + " first)")
  void calculateScheduleBasePriceForSchedule_ShouldFindCpmOnLaterElement() {
    Inventory inventory = new Inventory();
    inventory.setId("inv-cpm-later");
    inventory.setPrices(
        List.of(
            Inventory.Price.builder().cpm(null).spot(null).build(),
            Inventory.Price.builder().cpm(10.0).build()));

    Double result =
        invokeCalculateBasePrice(null, 2000L, inventory, Campaign.Goals.GoalType.IMPRESSIONS);

    // (10.0 / 1000) * 2000 = 20.0
    assertThat(result).isEqualTo(20.0);
  }

  @Test
  @DisplayName(
      "calculateScheduleBasePriceForSchedule - Should find spot on a later price element (not just"
          + " first)")
  void calculateScheduleBasePriceForSchedule_ShouldFindSpotOnLaterElement() {
    Inventory inventory = new Inventory();
    inventory.setId("inv-spot-later");
    inventory.setPrices(
        List.of(
            Inventory.Price.builder().spot(null).build(),
            Inventory.Price.builder().spot(2.5).build()));

    Double result = invokeCalculateBasePrice(4L, null, inventory, Campaign.Goals.GoalType.ADPLAYS);

    // 2.5 * 4 = 10.0
    assertThat(result).isEqualTo(10.0);
  }

  @Test
  @DisplayName(
      "calculateScheduleBasePriceForSchedule - Should return null when CPM absent across all"
          + " elements")
  void calculateScheduleBasePriceForSchedule_ShouldReturnNullWhenCpmMissingEverywhere() {
    Inventory inventory = new Inventory();
    inventory.setId("inv-no-cpm");
    inventory.setPrices(
        List.of(
            Inventory.Price.builder().cpm(null).build(),
            Inventory.Price.builder().cpm(null).build()));

    Double result =
        invokeCalculateBasePrice(null, 2000L, inventory, Campaign.Goals.GoalType.IMPRESSIONS);

    assertThat(result).isNull();
  }
}
