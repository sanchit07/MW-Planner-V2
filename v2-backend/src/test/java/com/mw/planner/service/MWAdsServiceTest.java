package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.Inventory;
import com.mw.planner.domain.Schedule;
import com.mw.planner.dto.CompanyLookupResponseDTO;
import com.mw.planner.dto.ExternalInventoryMessageDTO;
import com.mw.planner.dto.UserResponseDTO;
import com.mw.planner.dto.ads.AdsCampaignRequestDTO;
import com.mw.planner.dto.ads.AdsSubmissionResponseDTO;
import com.mw.planner.dto.ads.ExternalInventoryDTO;
import com.mw.planner.exception.ads.AdsApiException;
import com.mw.planner.exception.campaign.CampaignNotApprovedForAdsException;
import com.mw.planner.repository.CampaignInventorySchedulesRepository;
import com.mw.planner.repository.ScheduleRepository;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class MWAdsServiceTest {

  @Mock private com.mw.planner.config.MwPlannerProperties mwPlannerProperties;
  @Mock private org.springframework.web.client.RestTemplate restTemplate;
  @Mock private CampaignService campaignService;
  @Mock private CampaignInventorySchedulesRepository campaignInventorySchedulesRepository;
  @Mock private InventoryService inventoryService;
  @Mock private CountryService countryService;
  @Mock private com.mw.brand.lib.service.BrandService brandService;
  @Mock private CompanyService companyService;
  @Mock private UserService userService;
  @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
  @Mock private ScheduleRepository scheduleRepository;
  @Mock private AdServerRequestLogService adServerRequestLogService;
  @Mock private SecurityContextService securityContextService;

  @InjectMocks private MWAdsService mwAdsService;

  @BeforeEach
  void setUp() {
    lenient()
        .when(mwPlannerProperties.getAds())
        .thenReturn(new com.mw.planner.config.MwPlannerProperties.Ads());
    lenient().when(securityContextService.getBearerToken()).thenReturn("test-bearer-token");
  }

  @Test
  void
      submitApprovedCampaignToAds_WhenCampaignStatusNotReviewing_ThrowsCampaignNotApprovedForAdsException() {
    Campaign campaign =
        Campaign.builder()
            .name("Test")
            .status(Campaign.Status.DRAFT)
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .companyId("company-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .build();
    campaign.setId("campaign-1");

    when(campaignService.findById("campaign-1")).thenReturn(campaign);

    assertThatThrownBy(
            () -> mwAdsService.submitApprovedCampaignToAds("campaign-1", List.of("inv-1")))
        .isInstanceOf(CampaignNotApprovedForAdsException.class)
        .hasMessageContaining("campaign-1");

    verify(campaignService).findById("campaign-1");
    verify(restTemplate, never()).exchange(any(), any(), any(), any(Class.class));
  }

  @Test
  void submitApprovedCampaignToAds_WhenExceptionDuringSubmit_ThrowsAdsApiException()
      throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test")
            .status(Campaign.Status.REVIEWING)
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .companyId("company-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyAccess(List.of("mo-1"))
            .build();
    campaign.setId("campaign-1");

    UserResponseDTO user = new UserResponseDTO();
    user.setExternalId("ext-user");
    CompanyLookupResponseDTO companyLookup =
        CompanyLookupResponseDTO.builder()
            .id("company-1")
            .name("Company")
            .seatId(1)
            .externalId("ext-company-1")
            .build();
    CompanyLookupResponseDTO mediaOwnerLookup =
        CompanyLookupResponseDTO.builder()
            .id("mo-1")
            .name("Media Owner")
            .externalId("ext-mo")
            .notificationEmail("mo@example.com")
            .build();

    Inventory.DigitalFields digitalFields =
        Inventory.DigitalFields.builder()
            .bookingMode("loop")
            .loopDuration(360)
            .spotsPerLoop(24)
            .spotDuration(15)
            .build();

    Inventory inventory = new Inventory();
    inventory.setId("inv-1");
    inventory.setClassification("Digital");
    inventory.setName("Test Billboard");
    inventory.setDigitalFields(digitalFields);
    inventory.setMediaOwnerId("mo-1");

    CampaignInventorySchedules scheduleConfig =
        CampaignInventorySchedules.builder()
            .campaignId("campaign-1")
            .mediaOwnerId("mo-1")
            .inventoryId("inv-1")
            .scheduleIds(List.of("sched-1"))
            .build();

    Schedule schedule =
        Schedule.builder()
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .adPlays(100L)
            .impressions(5000L)
            .reach(1000L)
            .spotsPerHour(10L)
            .spotsPerLoop(24L)
            .build();
    schedule.setId("sched-1");

    when(campaignService.findById("campaign-1")).thenReturn(campaign);
    when(campaignInventorySchedulesRepository.findByCampaignIdAndInventoryIdIn(
            "campaign-1", List.of("inv-1")))
        .thenReturn(List.of(scheduleConfig));
    lenient().when(inventoryService.getById("inv-1")).thenReturn(inventory);
    lenient()
        .when(scheduleRepository.findAllById(List.of("sched-1")))
        .thenReturn(List.of(schedule));
    lenient().when(userService.getUserById("user-1")).thenReturn(user);
    lenient()
        .when(companyService.getCompanyLookupWithCompanyId("company-1", false))
        .thenReturn(companyLookup);
    lenient()
        .when(companyService.getCompanyLookupWithCompanyId("mo-1", false))
        .thenReturn(mediaOwnerLookup);
    when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("serialize fail"));

    assertThatThrownBy(
            () -> mwAdsService.submitApprovedCampaignToAds("campaign-1", List.of("inv-1")))
        .isInstanceOf(AdsApiException.class)
        .hasMessageContaining("Failed to submit campaign to ADS");

    verify(campaignService).findById("campaign-1");
    verify(objectMapper).writeValueAsString(any());
    verify(restTemplate, never()).exchange(any(), any(), any(), any(Class.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void
      submitApprovedCampaignToAds_WithDigitalInventory_PopulatesBookingModeMetadataAndAllocationFields()
          throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .status(Campaign.Status.REVIEWING)
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .companyId("company-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyAccess(List.of("mo-1"))
            .build();
    campaign.setId("campaign-1");

    Inventory.DigitalFields digitalFields =
        Inventory.DigitalFields.builder()
            .bookingMode("loop")
            .loopDuration(360)
            .spotsPerLoop(24)
            .spotDuration(15)
            .build();
    ExternalInventoryMessageDTO.ExternalId extId =
        new ExternalInventoryMessageDTO.ExternalId("LMX", "JPN-JEK-D-00001");

    Inventory inventory = new Inventory();
    inventory.setId("inv-1");
    inventory.setClassification("Digital");
    inventory.setName("Test Billboard");
    inventory.setDigitalFields(digitalFields);
    inventory.setExternalIds(List.of(extId));
    inventory.setMediaOwnerId("mo-1");

    CampaignInventorySchedules scheduleConfig =
        CampaignInventorySchedules.builder()
            .campaignId("campaign-1")
            .mediaOwnerId("mo-1")
            .inventoryId("inv-1")
            .scheduleIds(List.of("sched-1"))
            .build();

    Schedule schedule =
        Schedule.builder()
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .adPlays(100L)
            .impressions(5000L)
            .reach(1000L)
            .spotsPerHour(10L)
            .spotsPerLoop(24L)
            .build();
    schedule.setId("sched-1");

    UserResponseDTO user = new UserResponseDTO();
    user.setExternalId("ext-user");
    CompanyLookupResponseDTO companyLookup =
        CompanyLookupResponseDTO.builder()
            .id("company-1")
            .name("Advertiser Co")
            .seatId(1)
            .externalId("ext-co-1")
            .build();
    CompanyLookupResponseDTO mediaOwnerLookup =
        CompanyLookupResponseDTO.builder()
            .id("mo-1")
            .name("Media Owner")
            .externalId("ext-mo-1")
            .notificationEmail("mo@test.com")
            .build();
    AdsSubmissionResponseDTO adsResponse =
        AdsSubmissionResponseDTO.builder().total(1).successful(1).failed(0).build();

    when(campaignService.findById("campaign-1")).thenReturn(campaign);
    lenient().when(userService.getUserById("user-1")).thenReturn(user);
    lenient()
        .when(companyService.getCompanyLookupWithCompanyId("company-1", false))
        .thenReturn(companyLookup);
    lenient()
        .when(companyService.getCompanyLookupWithCompanyId("mo-1", false))
        .thenReturn(mediaOwnerLookup);
    when(campaignInventorySchedulesRepository.findByCampaignIdAndInventoryIdIn(
            "campaign-1", List.of("inv-1")))
        .thenReturn(List.of(scheduleConfig));
    when(inventoryService.getById("inv-1")).thenReturn(inventory);
    when(scheduleRepository.findAllById(List.of("sched-1"))).thenReturn(List.of(schedule));
    lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(AdsSubmissionResponseDTO.class)))
        .thenReturn(ResponseEntity.ok(adsResponse));

    mwAdsService.submitApprovedCampaignToAds("campaign-1", List.of("inv-1"));

    ArgumentCaptor<HttpEntity<AdsCampaignRequestDTO>> captor =
        ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .exchange(
            anyString(), eq(HttpMethod.POST), captor.capture(), eq(AdsSubmissionResponseDTO.class));

    ExternalInventoryDTO inv =
        captor.getValue().getBody().getExternalPayload().getInventories().get(0);

    assertThat(inv.getBookingMode()).isEqualTo("loop");
    assertThat(inv.getPublisherExternalId()).isEqualTo("ext-mo-1");
    assertThat(inv.getPublisher()).isNotNull();
    assertThat(inv.getPublisher().getExternalId()).isEqualTo("ext-mo-1");
    assertThat(inv.getMetadata()).isNotNull();
    assertThat(inv.getMetadata().getExternalRefIds()).hasSize(1);
    assertThat(inv.getMetadata().getExternalRefIds().get(0).getSource()).isEqualTo("LMX");
    assertThat(inv.getMetadata().getExternalRefIds().get(0).getExternalRefId())
        .isEqualTo("JPN-JEK-D-00001");

    ExternalInventoryDTO.AllocationDTO allocation = inv.getPlanning().getAllocation();
    assertThat(allocation.getLoopDuration()).isEqualTo(360);
    assertThat(allocation.getSpotsPerLoop()).isEqualTo(24);
    assertThat(allocation.getBookedSpotsPerLoop()).isEqualTo(1);
    assertThat(allocation.getBookedSpotsPerHour()).isEqualTo(10); // 3600 / 360
    assertThat(allocation.getSov()).isEqualTo(100.0); // 24 / 24 * 100
  }

  @Test
  @SuppressWarnings("unchecked")
  void submitApprovedCampaignToAds_SovCalculation_UsesPlannedVsTotalSpotsPerLoop()
      throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("SOV Test Campaign")
            .status(Campaign.Status.REVIEWING)
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .companyId("company-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyAccess(List.of("mo-1"))
            .build();
    campaign.setId("campaign-1");

    // totalSpots = 12 (inventory capacity)
    Inventory.DigitalFields digitalFields =
        Inventory.DigitalFields.builder()
            .bookingMode("loop")
            .loopDuration(360)
            .spotsPerLoop(12)
            .spotDuration(30)
            .build();

    Inventory inventory = new Inventory();
    inventory.setId("inv-1");
    inventory.setClassification("Digital");
    inventory.setName("Test Billboard");
    inventory.setDigitalFields(digitalFields);
    inventory.setExternalIds(List.of(new ExternalInventoryMessageDTO.ExternalId("CMS", "cms-001")));
    inventory.setMediaOwnerId("mo-1");

    CampaignInventorySchedules scheduleConfig =
        CampaignInventorySchedules.builder()
            .campaignId("campaign-1")
            .mediaOwnerId("mo-1")
            .inventoryId("inv-1")
            .scheduleIds(List.of("sched-1"))
            .build();

    // plannedSpots = 4 (campaign booked 4 out of 12 slots)
    Schedule schedule =
        Schedule.builder()
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .adPlays(100L)
            .spotsPerHour(10L)
            .spotsPerLoop(4L)
            .impressions(2000L)
            .reach(500L)
            .build();
    schedule.setId("sched-1");

    UserResponseDTO user = new UserResponseDTO();
    user.setExternalId("ext-user");
    CompanyLookupResponseDTO companyLookup =
        CompanyLookupResponseDTO.builder()
            .id("company-1")
            .name("Advertiser Co")
            .seatId(1)
            .externalId("ext-co-1")
            .build();
    CompanyLookupResponseDTO mediaOwnerLookup =
        CompanyLookupResponseDTO.builder()
            .id("mo-1")
            .name("Media Owner")
            .externalId("ext-mo-1")
            .notificationEmail("mo@test.com")
            .build();
    AdsSubmissionResponseDTO adsResponse =
        AdsSubmissionResponseDTO.builder().total(1).successful(1).failed(0).build();

    when(campaignService.findById("campaign-1")).thenReturn(campaign);
    lenient().when(userService.getUserById("user-1")).thenReturn(user);
    lenient()
        .when(companyService.getCompanyLookupWithCompanyId("company-1", false))
        .thenReturn(companyLookup);
    lenient()
        .when(companyService.getCompanyLookupWithCompanyId("mo-1", false))
        .thenReturn(mediaOwnerLookup);
    when(campaignInventorySchedulesRepository.findByCampaignIdAndInventoryIdIn(
            "campaign-1", List.of("inv-1")))
        .thenReturn(List.of(scheduleConfig));
    when(inventoryService.getById("inv-1")).thenReturn(inventory);
    when(scheduleRepository.findAllById(List.of("sched-1"))).thenReturn(List.of(schedule));
    lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(AdsSubmissionResponseDTO.class)))
        .thenReturn(ResponseEntity.ok(adsResponse));

    mwAdsService.submitApprovedCampaignToAds("campaign-1", List.of("inv-1"));

    ArgumentCaptor<HttpEntity<AdsCampaignRequestDTO>> captor =
        ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .exchange(
            anyString(), eq(HttpMethod.POST), captor.capture(), eq(AdsSubmissionResponseDTO.class));

    ExternalInventoryDTO.AllocationDTO allocation =
        captor
            .getValue()
            .getBody()
            .getExternalPayload()
            .getInventories()
            .get(0)
            .getPlanning()
            .getAllocation();

    // SOV = (plannedSpots / totalSpots) * 100 = (4 / 12) * 100 = 33.33...
    assertThat(allocation.getSov()).isCloseTo(33.33, within(0.01));
  }

  @Test
  @SuppressWarnings("unchecked")
  void submitApprovedCampaignToAds_WhenExternalIdsEmpty_MetadataIsNull() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .status(Campaign.Status.REVIEWING)
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .companyId("company-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyAccess(List.of("mo-1"))
            .build();
    campaign.setId("campaign-1");

    Inventory.DigitalFields digitalFields =
        Inventory.DigitalFields.builder()
            .bookingMode("spot")
            .loopDuration(180)
            .spotsPerLoop(12)
            .spotDuration(10)
            .build();

    Inventory inventory = new Inventory();
    inventory.setId("inv-1");
    inventory.setClassification("Digital");
    inventory.setName("Test Screen");
    inventory.setDigitalFields(digitalFields);
    inventory.setExternalIds(Collections.emptyList());
    inventory.setMediaOwnerId("mo-1");

    CampaignInventorySchedules scheduleConfig =
        CampaignInventorySchedules.builder()
            .campaignId("campaign-1")
            .mediaOwnerId("mo-1")
            .inventoryId("inv-1")
            .scheduleIds(List.of("sched-2"))
            .build();

    Schedule schedule =
        Schedule.builder()
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .adPlays(50L)
            .impressions(2500L)
            .reach(500L)
            .spotsPerHour(20L)
            .build();
    schedule.setId("sched-2");

    UserResponseDTO user = new UserResponseDTO();
    user.setExternalId("ext-user");
    CompanyLookupResponseDTO companyLookup =
        CompanyLookupResponseDTO.builder()
            .id("company-1")
            .name("Advertiser Co")
            .seatId(1)
            .externalId("ext-co-1")
            .build();
    CompanyLookupResponseDTO mediaOwnerLookup =
        CompanyLookupResponseDTO.builder()
            .id("mo-1")
            .name("Media Owner")
            .externalId("ext-mo-1")
            .notificationEmail("mo@test.com")
            .build();
    AdsSubmissionResponseDTO adsResponse =
        AdsSubmissionResponseDTO.builder().total(1).successful(1).failed(0).build();

    when(campaignService.findById("campaign-1")).thenReturn(campaign);
    lenient().when(userService.getUserById("user-1")).thenReturn(user);
    lenient()
        .when(companyService.getCompanyLookupWithCompanyId("company-1", false))
        .thenReturn(companyLookup);
    lenient()
        .when(companyService.getCompanyLookupWithCompanyId("mo-1", false))
        .thenReturn(mediaOwnerLookup);
    when(campaignInventorySchedulesRepository.findByCampaignIdAndInventoryIdIn(
            "campaign-1", List.of("inv-1")))
        .thenReturn(List.of(scheduleConfig));
    when(inventoryService.getById("inv-1")).thenReturn(inventory);
    when(scheduleRepository.findAllById(List.of("sched-2"))).thenReturn(List.of(schedule));
    lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(AdsSubmissionResponseDTO.class)))
        .thenReturn(ResponseEntity.ok(adsResponse));

    mwAdsService.submitApprovedCampaignToAds("campaign-1", List.of("inv-1"));

    ArgumentCaptor<HttpEntity<AdsCampaignRequestDTO>> captor =
        ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .exchange(
            anyString(), eq(HttpMethod.POST), captor.capture(), eq(AdsSubmissionResponseDTO.class));

    ExternalInventoryDTO inv =
        captor.getValue().getBody().getExternalPayload().getInventories().get(0);

    assertThat(inv.getMetadata()).isNull();
    assertThat(inv.getBookingMode()).isEqualTo("spot");

    ExternalInventoryDTO.AllocationDTO allocation = inv.getPlanning().getAllocation();
    assertThat(allocation.getLoopDuration()).isEqualTo(180);
    assertThat(allocation.getSpotsPerLoop()).isEqualTo(12);
    assertThat(allocation.getBookedSpotsPerLoop()).isEqualTo(1);
    assertThat(allocation.getBookedSpotsPerHour()).isEqualTo(20); // 3600 / 180
  }

  // ==================== campaignGoal mapping tests ====================

  @ParameterizedTest(name = "{0} -> \"{1}\"")
  @CsvSource({
    "IMPRESSIONS, IMPRESSIONS",
    "REACH,       REACH",
    "SOV,         SHARE_OF_VOICE",
    "ADPLAYS,     AD_PLAYS",
  })
  @SuppressWarnings("unchecked")
  void buildCampaignGoalDTO_MapsGoalTypeToAdsFormat(
      Campaign.Goals.GoalType goalType, String expectedAdsType) throws Exception {
    Campaign campaign = buildMinimalReviewingCampaign();
    campaign.setGoals(Campaign.Goals.builder().goalType(goalType).targetValue(100000.0).build());

    setupCommonMocks(campaign);

    mwAdsService.submitApprovedCampaignToAds("campaign-1", List.of("inv-1"));

    ArgumentCaptor<HttpEntity<AdsCampaignRequestDTO>> captor =
        ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .exchange(
            anyString(), eq(HttpMethod.POST), captor.capture(), eq(AdsSubmissionResponseDTO.class));

    var campaignGoal =
        captor.getValue().getBody().getExternalPayload().getCampaign().getCampaignGoal();
    assertThat(campaignGoal).isNotNull();
    assertThat(campaignGoal.getType()).isEqualTo(expectedAdsType);
    assertThat(campaignGoal.getTargetValue()).isEqualTo(100000.0);
  }

  @ParameterizedTest(name = "{0} -> campaignGoal omitted")
  @EnumSource(
      value = Campaign.Goals.GoalType.class,
      names = {"ATTRIBUTION", "OTHER"})
  @SuppressWarnings("unchecked")
  void buildCampaignGoalDTO_UnsupportedGoalType_OmitsCampaignGoal(Campaign.Goals.GoalType goalType)
      throws Exception {
    Campaign campaign = buildMinimalReviewingCampaign();
    campaign.setGoals(Campaign.Goals.builder().goalType(goalType).targetValue(50000.0).build());

    setupCommonMocks(campaign);

    mwAdsService.submitApprovedCampaignToAds("campaign-1", List.of("inv-1"));

    ArgumentCaptor<HttpEntity<AdsCampaignRequestDTO>> captor =
        ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .exchange(
            anyString(), eq(HttpMethod.POST), captor.capture(), eq(AdsSubmissionResponseDTO.class));

    assertThat(captor.getValue().getBody().getExternalPayload().getCampaign().getCampaignGoal())
        .isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void buildCampaignGoalDTO_NullGoals_OmitsCampaignGoal() throws Exception {
    Campaign campaign = buildMinimalReviewingCampaign();
    // goals deliberately not set

    setupCommonMocks(campaign);

    mwAdsService.submitApprovedCampaignToAds("campaign-1", List.of("inv-1"));

    ArgumentCaptor<HttpEntity<AdsCampaignRequestDTO>> captor =
        ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .exchange(
            anyString(), eq(HttpMethod.POST), captor.capture(), eq(AdsSubmissionResponseDTO.class));

    assertThat(captor.getValue().getBody().getExternalPayload().getCampaign().getCampaignGoal())
        .isNull();
  }

  // ==================== helpers ====================

  private Campaign buildMinimalReviewingCampaign() {
    Campaign campaign =
        Campaign.builder()
            .name("Goal Test Campaign")
            .status(Campaign.Status.REVIEWING)
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .companyId("company-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyAccess(List.of("mo-1"))
            .build();
    campaign.setId("campaign-1");
    return campaign;
  }

  private void setupCommonMocks(Campaign campaign) throws Exception {
    UserResponseDTO user = new UserResponseDTO();
    user.setExternalId("ext-user");
    CompanyLookupResponseDTO companyLookup =
        CompanyLookupResponseDTO.builder()
            .id("company-1")
            .name("Advertiser Co")
            .seatId(1)
            .externalId("ext-co-1")
            .build();
    CompanyLookupResponseDTO mediaOwnerLookup =
        CompanyLookupResponseDTO.builder()
            .id("mo-1")
            .name("Media Owner")
            .externalId("ext-mo-1")
            .notificationEmail("mo@test.com")
            .build();

    Inventory.DigitalFields digitalFields =
        Inventory.DigitalFields.builder()
            .bookingMode("loop")
            .loopDuration(360)
            .spotsPerLoop(24)
            .spotDuration(15)
            .build();

    Inventory inventory = new Inventory();
    inventory.setId("inv-1");
    inventory.setClassification("Digital");
    inventory.setName("Goal Test Inventory");
    inventory.setDigitalFields(digitalFields);
    inventory.setMediaOwnerId("mo-1");

    CampaignInventorySchedules scheduleConfig =
        CampaignInventorySchedules.builder()
            .campaignId("campaign-1")
            .mediaOwnerId("mo-1")
            .inventoryId("inv-1")
            .scheduleIds(List.of("sched-1"))
            .build();

    Schedule schedule =
        Schedule.builder()
            .startDate(campaign.getStartDate())
            .endDate(campaign.getEndDate())
            .adPlays(100L)
            .impressions(5000L)
            .reach(1000L)
            .spotsPerHour(10L)
            .spotsPerLoop(24L)
            .build();
    schedule.setId("sched-1");

    when(campaignService.findById("campaign-1")).thenReturn(campaign);
    lenient().when(userService.getUserById("user-1")).thenReturn(user);
    lenient()
        .when(companyService.getCompanyLookupWithCompanyId("company-1", false))
        .thenReturn(companyLookup);
    lenient()
        .when(companyService.getCompanyLookupWithCompanyId("mo-1", false))
        .thenReturn(mediaOwnerLookup);
    lenient()
        .when(
            campaignInventorySchedulesRepository.findByCampaignIdAndInventoryIdIn(
                "campaign-1", List.of("inv-1")))
        .thenReturn(List.of(scheduleConfig));
    lenient().when(inventoryService.getById("inv-1")).thenReturn(inventory);
    lenient()
        .when(scheduleRepository.findAllById(List.of("sched-1")))
        .thenReturn(List.of(schedule));
    lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(AdsSubmissionResponseDTO.class)))
        .thenReturn(
            ResponseEntity.ok(
                AdsSubmissionResponseDTO.builder().total(0).successful(0).failed(0).build()));
  }

  @Test
  @SuppressWarnings("unchecked")
  void submitApprovedCampaignToAds_SetsBearerTokenOnAdsRequest() throws Exception {
    Campaign campaign = buildMinimalReviewingCampaign();
    setupCommonMocks(campaign);

    mwAdsService.submitApprovedCampaignToAds("campaign-1", List.of("inv-1"));

    ArgumentCaptor<HttpEntity<AdsCampaignRequestDTO>> captor =
        ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .exchange(
            anyString(), eq(HttpMethod.POST), captor.capture(), eq(AdsSubmissionResponseDTO.class));

    assertThat(captor.getValue().getHeaders().getFirst("Authorization"))
        .isEqualTo("Bearer test-bearer-token");
  }
}
