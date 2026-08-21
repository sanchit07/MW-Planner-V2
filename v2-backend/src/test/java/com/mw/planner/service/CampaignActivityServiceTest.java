package com.mw.planner.service;

import static com.mw.planner.constants.CampaignActivityKey.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.brand.lib.domain.Brand;
import com.mw.brand.lib.dto.BrandResponseDTO;
import com.mw.brand.lib.service.BrandService;
import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignActivity;
import com.mw.planner.dto.*;
import com.mw.planner.dto.CompanyDto;
import com.mw.planner.repository.CampaignActivityRepository;
import com.mw.planner.service.CampaignActivityService.OperationType;
import com.mw.planner.service.config.DefaultConfigurationService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class CampaignActivityServiceTest {

  @Mock private CampaignActivityRepository campaignActivityRepository;
  @Mock private UserService userService;
  @Mock private BrandService brandService;
  @Mock private AgencyService agencyService;
  @Mock private MessageService messageService;
  @Mock private DefaultConfigurationService defaultConfigurationService;
  @Mock private com.mw.planner.service.config.ConfigService configService;
  @Mock private CompanyService companyService;

  @InjectMocks private CampaignActivityService campaignActivityService;

  private IamUserContext testUserContext;
  private Campaign testCampaign;
  private Brand testBrand;
  private CompanyDto testCompanyDto;

  private BrandResponseDTO brandResponseDTO;

  @BeforeEach
  void setUp() {
    testUserContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .firstName("John")
            .lastName("Doe")
            .locale(Locale.ENGLISH)
            .build();

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
            .agency(Campaign.CampaignAgency.builder().id("agency123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .build();

    testBrand = new Brand();
    testBrand.setId("brand123");
    testBrand.setName("Test Brand");

    brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    testCompanyDto = new CompanyDto();
    testCompanyDto.setId("company123");
    testCompanyDto.setBusinessType(CompanyDto.BusinessType.MEDIA_OWNER);
  }

  @AfterEach
  void tearDown() {
    reset(
        campaignActivityRepository,
        userService,
        brandService,
        agencyService,
        messageService,
        defaultConfigurationService,
        configService,
        companyService);
  }

  // ========== logActivity Tests ==========

  @Test
  @DisplayName("logActivity - Should save activity with user context")
  void logActivity_WithUserContext_ShouldSaveActivity() {
    // Given
    String campaignId = "campaign123";
    OperationType operationType = OperationType.CREATED;
    Map<String, Object> values = Map.of("name", "Test Campaign");
    String updatedBy = "John Doe";
    String userId = "user123";
    String companyId = "company123";

    when(campaignActivityRepository.save(any(CampaignActivity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    campaignActivityService.logActivity(
        campaignId, operationType, values, updatedBy, userId, companyId);

    // Then
    ArgumentCaptor<CampaignActivity> captor = ArgumentCaptor.forClass(CampaignActivity.class);
    verify(campaignActivityRepository).save(captor.capture());
    CampaignActivity savedActivity = captor.getValue();

    assertThat(savedActivity.getCampaignId()).isEqualTo(campaignId);
    assertThat(savedActivity.getUserId()).isEqualTo(userId);
    assertThat(savedActivity.getCompanyId()).isEqualTo(companyId);
    assertThat(savedActivity.getUpdatedBy()).isEqualTo(updatedBy);
    assertThat(savedActivity.getOperationType()).isEqualTo(operationType.name());
    assertThat(savedActivity.getValues()).isEqualTo(values);
  }

  @Test
  @DisplayName("logActivity - Should use SYSTEM_CRON when userId is null")
  void logActivity_WithNullUserId_ShouldUseSystemCron() {
    // Given
    String campaignId = "campaign123";
    OperationType operationType = OperationType.UPDATED;
    Map<String, Object> values = Map.of("status", "APPROVED");
    String updatedBy = "System";

    when(campaignActivityRepository.save(any(CampaignActivity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    campaignActivityService.logActivity(campaignId, operationType, values, updatedBy, null, null);

    // Then
    ArgumentCaptor<CampaignActivity> captor = ArgumentCaptor.forClass(CampaignActivity.class);
    verify(campaignActivityRepository).save(captor.capture());
    CampaignActivity savedActivity = captor.getValue();

    assertThat(savedActivity.getUserId()).isEqualTo("System");
    assertThat(savedActivity.getCompanyId()).isEqualTo("");
    assertThat(savedActivity.getUpdatedBy()).isEqualTo(updatedBy);
  }

  @Test
  @DisplayName("logActivity - Should handle null values map")
  void logActivity_WithNullValues_ShouldUseEmptyMap() {
    // Given
    String campaignId = "campaign123";
    OperationType operationType = OperationType.UPDATED;
    String updatedBy = "John Doe";

    when(campaignActivityRepository.save(any(CampaignActivity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    campaignActivityService.logActivity(
        campaignId, operationType, null, updatedBy, "user123", "company123");

    // Then
    ArgumentCaptor<CampaignActivity> captor = ArgumentCaptor.forClass(CampaignActivity.class);
    verify(campaignActivityRepository).save(captor.capture());
    CampaignActivity savedActivity = captor.getValue();

    assertThat(savedActivity.getValues()).isNotNull();
    assertThat(savedActivity.getValues()).isEmpty();
  }

  @Test
  @DisplayName("logActivity - Should use UserContext when called without explicit user info")
  void logActivity_WithUserContext_ShouldExtractUserInfo() {
    // Given
    String campaignId = "campaign123";
    OperationType operationType = OperationType.CREATED;
    Map<String, Object> values = Map.of("name", "Test Campaign");

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(campaignActivityRepository.save(any(CampaignActivity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    campaignActivityService.logActivity(campaignId, operationType, values);

    // Then
    ArgumentCaptor<CampaignActivity> captor = ArgumentCaptor.forClass(CampaignActivity.class);
    verify(campaignActivityRepository).save(captor.capture());
    CampaignActivity savedActivity = captor.getValue();

    assertThat(savedActivity.getUserId()).isEqualTo("user123");
    assertThat(savedActivity.getCompanyId()).isEqualTo("company123");
    assertThat(savedActivity.getUpdatedBy()).isEqualTo("John Doe");
    verify(userService).getIamUserContext();
  }

  @Test
  @DisplayName("logActivity - Should fallback to SYSTEM_CRON when UserContext fails")
  void logActivity_WhenUserContextFails_ShouldFallbackToSystemCron() {
    // Given
    String campaignId = "campaign123";
    OperationType operationType = OperationType.UPDATED;
    Map<String, Object> values = Map.of("status", "APPROVED");

    when(userService.getIamUserContext()).thenThrow(new RuntimeException("User context error"));
    when(campaignActivityRepository.save(any(CampaignActivity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    campaignActivityService.logActivity(campaignId, operationType, values);

    // Then
    ArgumentCaptor<CampaignActivity> captor = ArgumentCaptor.forClass(CampaignActivity.class);
    verify(campaignActivityRepository, times(1)).save(captor.capture());
    CampaignActivity savedActivity = captor.getValue();

    assertThat(savedActivity.getUserId()).isEqualTo("System");
    assertThat(savedActivity.getUpdatedBy()).isEqualTo("System");
  }

  @Test
  @DisplayName("logCronActivity - Should log with SYSTEM_CRON")
  void logCronActivity_ShouldUseSystemCron() {
    // Given
    String campaignId = "campaign123";
    OperationType operationType = OperationType.UPDATED;
    Map<String, Object> values = Map.of("status", "ACTIVE");

    when(campaignActivityRepository.save(any(CampaignActivity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    campaignActivityService.logCronActivity(campaignId, operationType, values);

    // Then
    ArgumentCaptor<CampaignActivity> captor = ArgumentCaptor.forClass(CampaignActivity.class);
    verify(campaignActivityRepository).save(captor.capture());
    CampaignActivity savedActivity = captor.getValue();

    assertThat(savedActivity.getUserId()).isEqualTo("System");
    assertThat(savedActivity.getUpdatedBy()).isEqualTo("System");
    assertThat(savedActivity.getCompanyId()).isEqualTo("");
  }

  // ========== getCampaignHistory Tests ==========

  @Test
  @DisplayName("getCampaignHistory - Should return paginated history")
  void getCampaignHistory_ShouldReturnPaginatedHistory() {
    // Given
    String campaignId = "campaign123";
    Locale locale = Locale.ENGLISH;
    Pageable pageable = PageRequest.of(0, 10, Sort.by("updatedAt").descending());

    CampaignActivity activity1 =
        CampaignActivity.builder()
            .campaignId(campaignId)
            .userId("user123")
            .companyId("company123")
            .updatedBy("John Doe")
            .operationType("CREATED")
            .values(Map.of("name", "Test Campaign"))
            .build();
    activity1.setUpdatedAt(LocalDateTime.now().minusHours(1));

    CampaignActivity activity2 =
        CampaignActivity.builder()
            .campaignId(campaignId)
            .userId("user123")
            .companyId("company123")
            .updatedBy("John Doe")
            .operationType("UPDATED")
            .values(Map.of("status", "APPROVED"))
            .build();
    activity2.setUpdatedAt(LocalDateTime.now());

    Page<CampaignActivity> activitiesPage =
        new PageImpl<>(List.of(activity2, activity1), pageable, 2);

    when(campaignActivityRepository.findByCampaignId(campaignId, pageable))
        .thenReturn(activitiesPage);
    when(companyService.getCompaniesByIds(anyList())).thenReturn(List.of(testCompanyDto));
    when(messageService.getMessage(anyString(), any(Locale.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    Page<CampaignActivityResponseDTO> result =
        campaignActivityService.getCampaignHistory(campaignId, locale, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getTotalElements()).isEqualTo(2);
    verify(campaignActivityRepository).findByCampaignId(campaignId, pageable);
  }

  @Test
  @DisplayName("getCampaignHistory - Should use user locale from context")
  void getCampaignHistory_WithoutLocale_ShouldUseUserLocale() {
    // Given
    String campaignId = "campaign123";
    Pageable pageable = PageRequest.of(0, 10);

    CampaignActivity activity =
        CampaignActivity.builder()
            .campaignId(campaignId)
            .userId("user123")
            .companyId("company123")
            .updatedBy("John Doe")
            .operationType("CREATED")
            .values(Map.of("name", "Test Campaign"))
            .build();

    Page<CampaignActivity> activitiesPage = new PageImpl<>(List.of(activity), pageable, 1);

    when(campaignActivityRepository.findByCampaignId(campaignId, pageable))
        .thenReturn(activitiesPage);
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(companyService.getCompaniesByIds(anyList())).thenReturn(List.of(testCompanyDto));
    when(messageService.getMessage(anyString(), any(Locale.class), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    Page<CampaignActivityResponseDTO> result =
        campaignActivityService.getCampaignHistory(campaignId, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    verify(userService).getIamUserContext();
  }

  @Test
  @DisplayName("getCampaignHistory - Should fallback to English when locale unavailable")
  void getCampaignHistory_WhenLocaleUnavailable_ShouldFallbackToEnglish() {
    // Given
    String campaignId = "campaign123";
    Pageable pageable = PageRequest.of(0, 10);

    CampaignActivity activity =
        CampaignActivity.builder()
            .campaignId(campaignId)
            .userId("user123")
            .companyId("company123")
            .updatedBy("John Doe")
            .operationType("CREATED")
            .values(Map.of("name", "Test Campaign"))
            .build();

    Page<CampaignActivity> activitiesPage = new PageImpl<>(List.of(activity), pageable, 1);

    when(campaignActivityRepository.findByCampaignId(campaignId, pageable))
        .thenReturn(activitiesPage);
    when(userService.getIamUserContext()).thenThrow(new RuntimeException("User context error"));
    when(companyService.getCompaniesByIds(anyList())).thenReturn(List.of(testCompanyDto));
    when(messageService.getMessage(anyString(), eq(Locale.ENGLISH), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    Page<CampaignActivityResponseDTO> result =
        campaignActivityService.getCampaignHistory(campaignId, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
  }

  // ========== generateLocalizedMessage Tests ==========

  @Test
  @DisplayName("generateLocalizedMessage - Should generate message for CREATED operation")
  void generateLocalizedMessage_ForCreatedOperation_ShouldGenerateMessage() {
    // Given
    String operationType = "CREATED";
    Map<String, Object> values = Map.of("name", "Test Campaign");
    Locale locale = Locale.ENGLISH;

    when(messageService.getMessage("campaign.activity.created", locale))
        .thenReturn("Created the Campaign");
    when(messageService.getMessage("campaign.activity.field.name", locale)).thenReturn("name");
    when(messageService.getMessage("campaign.activity.field.separator", locale)).thenReturn(": ");

    // When
    String message =
        campaignActivityService.generateLocalizedMessage(operationType, values, locale);

    // Then
    assertThat(message).isNotNull();
    assertThat(message).contains("Created the Campaign");
    verify(messageService).getMessage("campaign.activity.created", locale);
  }

  @Test
  @DisplayName("generateLocalizedMessage - Should handle status change")
  void generateLocalizedMessage_ForStatusChange_ShouldFormatStatusMessage() {
    // Given
    String operationType = "UPDATED";
    Map<String, Object> values = Map.of(STATUS_FROM.key(), "DRAFT", STATUS_TO.key(), "APPROVED");
    Locale locale = Locale.ENGLISH;

    when(messageService.getMessage("campaign.activity.status.changed", locale, "DRAFT", "APPROVED"))
        .thenReturn("Updated campaign status from DRAFT to APPROVED");

    // When
    String message =
        campaignActivityService.generateLocalizedMessage(operationType, values, locale);

    // Then
    assertThat(message).isNotNull();
    assertThat(message).contains("DRAFT");
    assertThat(message).contains("APPROVED");
  }

  @Test
  @DisplayName("generateLocalizedMessage - Should handle approval workflow")
  void generateLocalizedMessage_ForApprovalWorkflow_ShouldFormatApprovalMessage() {
    // Given
    String operationType = "UPDATED";
    Map<String, Object> values =
        Map.of(APPROVAL_AUTHORITY.key(), "AGENCY", APPROVAL_ACTION.key(), "Approved");
    Locale locale = Locale.ENGLISH;

    when(messageService.getMessage("campaign.activity.approval.authority.agency", locale))
        .thenReturn("Agency");
    when(messageService.getMessage("campaign.activity.approval.approved", locale, "Agency"))
        .thenReturn("Agency approved the campaign");

    // When
    String message =
        campaignActivityService.generateLocalizedMessage(operationType, values, locale);

    // Then
    assertThat(message).isNotNull();
    assertThat(message).contains("Agency");
  }

  @Test
  @DisplayName("generateLocalizedMessage - Should handle inventory operations")
  void generateLocalizedMessage_ForInventoryOperation_ShouldFormatInventoryMessage() {
    // Given
    String operationType = "ADDED";
    Map<String, Object> values = Map.of(INVENTORY_REFERENCE_ID.key(), "inv123");
    Locale locale = Locale.ENGLISH;

    when(messageService.getMessage("campaign.activity.inventory.selected", locale, "inv123"))
        .thenReturn("Selected inventory: inv123");

    // When
    String message =
        campaignActivityService.generateLocalizedMessage(operationType, values, locale);

    // Then
    assertThat(message).isNotNull();
    assertThat(message).contains("inv123");
  }

  @Test
  @DisplayName("generateLocalizedMessage - Should handle empty values")
  void generateLocalizedMessage_WithEmptyValues_ShouldReturnOperationMessage() {
    // Given
    String operationType = "UPDATED";
    Map<String, Object> values = new HashMap<>();
    Locale locale = Locale.ENGLISH;

    when(messageService.getMessage("campaign.activity.updated", locale))
        .thenReturn("Updated the Campaign");

    // When
    String message =
        campaignActivityService.generateLocalizedMessage(operationType, values, locale);

    // Then
    assertThat(message).isNotNull();
    assertThat(message).isEqualTo("Updated the Campaign");
  }

  @Test
  @DisplayName("generateLocalizedMessage - Should handle CSV upload")
  void generateLocalizedMessage_ForCsvUpload_ShouldFormatCsvMessage() {
    // Given
    String operationType = "ADDED";
    Map<String, Object> values =
        Map.of(CSV_UPLOAD_SELECTED_COUNT.key(), 5, CSV_UPLOAD_FILENAME.key(), "inventory.csv");
    Locale locale = Locale.ENGLISH;

    when(messageService.getMessage("campaign.activity.csv_upload", locale, "inventory.csv", 5))
        .thenReturn("Uploaded CSV file: inventory.csv and selected 5 inventory");

    // When
    String message =
        campaignActivityService.generateLocalizedMessage(operationType, values, locale);

    // Then
    assertThat(message).isNotNull();
    assertThat(message).contains("inventory.csv");
    assertThat(message).contains("5");
  }

  @Test
  @DisplayName("generateLocalizedMessage - Should handle inventory import")
  void generateLocalizedMessage_ForInventoryImport_ShouldFormatImportMessage() {
    // Given
    String operationType = "ADDED";
    Map<String, Object> values =
        Map.of("inventory_import_selected_count", 10, "inventory_import_filename", "import.csv");
    Locale locale = Locale.ENGLISH;

    when(messageService.getMessage(
            "campaign.activity.inventory_import.used", locale, "import.csv", 10))
        .thenReturn("Used inventory import: import.csv and selected 10 inventory");

    // When
    String message =
        campaignActivityService.generateLocalizedMessage(operationType, values, locale);

    // Then
    assertThat(message).isNotNull();
    assertThat(message).contains("import.csv");
  }

  @Test
  @DisplayName("generateLocalizedMessage - Should handle schedule updates")
  void generateLocalizedMessage_ForScheduleUpdate_ShouldFormatScheduleMessage() {
    // Given
    String operationType = "UPDATED";
    Map<String, Object> values =
        Map.of("schedules_updated_count", 3, "inventory_name", "Test Inventory");
    Locale locale = Locale.ENGLISH;

    when(messageService.getMessage(
            "campaign.activity.schedules.updated", locale, 3, "Test Inventory"))
        .thenReturn("Updated 3 schedules for inventory: Test Inventory");

    // When
    String message =
        campaignActivityService.generateLocalizedMessage(operationType, values, locale);

    // Then
    assertThat(message).isNotNull();
    assertThat(message).contains("3");
    assertThat(message).contains("Test Inventory");
  }

  // ========== buildCreationChanges Tests ==========

  @Test
  @DisplayName("buildCreationChanges - Should build changes map for campaign creation")
  void buildCreationChanges_ShouldBuildCompleteChangesMap() {
    // Given
    Campaign.Goals goals = new Campaign.Goals();
    goals.setGoalType(Campaign.Goals.GoalType.IMPRESSIONS);
    goals.setTargetValue(100000.0);
    goals.setTargetName("Target Name");

    Campaign.Targeting targeting = new Campaign.Targeting();
    targeting.setDemographics(Map.of("age", List.of("18_24", "25_34")));

    testCampaign.setGoals(goals);
    testCampaign.setTargeting(targeting);
    testCampaign.getBrand().setName("Test Brand");
    testCampaign.getAgency().setName("Test Agency");

    // When
    Map<String, Object> changes = campaignActivityService.buildCreationChanges(testCampaign);

    // Then
    assertThat(changes).isNotNull();
    assertThat(changes).containsKey("name");
    assertThat(changes).containsKey("client_type");
    assertThat(changes).containsKey("dates");
    assertThat(changes).containsKey("country");
    assertThat(changes).containsKey("currency");
    assertThat(changes).containsKey("budget_amount");
    assertThat(changes).containsKey("goal_type");
    assertThat(changes).containsKey("goal_value");
    assertThat(changes).containsKey("targeting_demographics");
    assertThat(changes).containsKey("brand");
    assertThat(changes).containsKey("agency");
  }

  @Test
  @DisplayName("buildCreationChanges - Should handle null optional fields")
  void buildCreationChanges_WithNullFields_ShouldSkipNullFields() {
    // Given
    Campaign minimalCampaign =
        Campaign.builder()
            .name("Minimal Campaign")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(7))
            .userId("userId123")
            .companyId("company123")
            .build();

    // When
    Map<String, Object> changes = campaignActivityService.buildCreationChanges(minimalCampaign);

    // Then
    assertThat(changes).isNotNull();
    assertThat(changes).containsKey("name");
    assertThat(changes).containsKey("client_type");
    assertThat(changes).containsKey("dates");
    assertThat(changes).doesNotContainKey("brand");
    assertThat(changes).doesNotContainKey("agency");
  }

  // ========== buildUpdateChanges Tests ==========

  @Test
  @DisplayName("buildUpdateChanges - Should detect field changes")
  void buildUpdateChanges_ShouldDetectChangedFields() {
    // Given
    LocalDate testDate = LocalDate.now();
    Campaign oldCampaign =
        Campaign.builder()
            .name("Old Campaign")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .description("Old Description")
            .startDate(testDate)
            .endDate(testDate.plusDays(30))
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();

    Campaign newCampaign =
        Campaign.builder()
            .name("New Campaign")
            .status(Campaign.Status.APPROVED)
            .budget(15000.0)
            .description("New Description")
            .startDate(testDate)
            .endDate(testDate.plusDays(30))
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();

    when(brandService.getBrandById(anyString())).thenReturn(Optional.empty());
    when(agencyService.getNameById(anyString())).thenReturn(null);

    // When
    Map<String, Object> changes =
        campaignActivityService.buildUpdateChanges(oldCampaign, newCampaign);

    // Then
    assertThat(changes).isNotNull();
    assertThat(changes).containsKey("name");
    assertThat(changes).containsKey("status");
    assertThat(changes).containsKey("budget_amount");
    assertThat(changes).containsKey("description");
    assertThat(changes.get("name")).isEqualTo("New Campaign");
    assertThat(changes.get("status")).isEqualTo(Campaign.Status.APPROVED);
  }

  @Test
  @DisplayName("buildUpdateChanges - Should not include unchanged fields")
  void buildUpdateChanges_WithUnchangedFields_ShouldExcludeUnchangedFields() {
    // Given
    LocalDate testDate = LocalDate.now();
    Campaign oldCampaign =
        Campaign.builder()
            .name("Same Campaign")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .startDate(testDate)
            .endDate(testDate.plusDays(30))
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();

    Campaign newCampaign =
        Campaign.builder()
            .name("Same Campaign")
            .status(Campaign.Status.APPROVED)
            .budget(10000.0)
            .startDate(testDate)
            .endDate(testDate.plusDays(30))
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();

    when(brandService.getBrandById(anyString())).thenReturn(Optional.empty());
    when(agencyService.getNameById(anyString())).thenReturn(null);

    // When
    Map<String, Object> changes =
        campaignActivityService.buildUpdateChanges(oldCampaign, newCampaign);

    // Then
    assertThat(changes).isNotNull();
    assertThat(changes).containsKey("status");
    assertThat(changes).doesNotContainKey("name");
    assertThat(changes).doesNotContainKey("budget_amount");
  }

  @Test
  @DisplayName("buildUpdateChanges - Should handle date changes")
  void buildUpdateChanges_WithDateChanges_ShouldIncludeDateRange() {
    // Given
    LocalDate oldStart = LocalDate.now();
    LocalDate oldEnd = oldStart.plusDays(30);
    LocalDate newStart = oldStart.plusDays(5);
    LocalDate newEnd = oldEnd.plusDays(5);

    Campaign oldCampaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(oldStart)
            .endDate(oldEnd)
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();

    Campaign newCampaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(newStart)
            .endDate(newEnd)
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();

    when(brandService.getBrandById(anyString())).thenReturn(Optional.empty());
    when(agencyService.getNameById(anyString())).thenReturn(null);

    // When
    Map<String, Object> changes =
        campaignActivityService.buildUpdateChanges(oldCampaign, newCampaign);

    // Then
    assertThat(changes).isNotNull();
    assertThat(changes).containsKey("dates");
    @SuppressWarnings("unchecked")
    Map<String, Object> dates = (Map<String, Object>) changes.get(DATES.key());
    assertThat(dates).containsKey(START_DATE.key());
    assertThat(dates).containsKey(END_DATE.key());
  }

  @Test
  @DisplayName("buildUpdateChanges - Should handle targeting changes")
  void buildUpdateChanges_WithTargetingChanges_ShouldIncludeTargeting() {
    // Given
    LocalDate testDate = LocalDate.now();
    Campaign.Targeting oldTargeting = new Campaign.Targeting();
    oldTargeting.setDemographics(Map.of("age", List.of("18_24")));

    Campaign.Targeting newTargeting = new Campaign.Targeting();
    newTargeting.setDemographics(
        Map.of("age", List.of("18_24", "25_34"), "gender", List.of("male")));

    Campaign oldCampaign =
        Campaign.builder()
            .name("Test Campaign")
            .targeting(oldTargeting)
            .startDate(testDate)
            .endDate(testDate.plusDays(30))
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();

    Campaign newCampaign =
        Campaign.builder()
            .name("Test Campaign")
            .targeting(newTargeting)
            .startDate(testDate)
            .endDate(testDate.plusDays(30))
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();

    when(brandService.getBrandById(anyString())).thenReturn(Optional.empty());
    when(agencyService.getNameById(anyString())).thenReturn(null);

    // When
    Map<String, Object> changes =
        campaignActivityService.buildUpdateChanges(oldCampaign, newCampaign);

    // Then
    assertThat(changes).isNotNull();
    assertThat(changes).containsKey("targeting_demographics");
  }

  // ========== logActivity(varargs) / buildActivityValues Tests ==========

  private void echoMessages() {
    // any(Object[].class) matches the whole varargs array for any arity (0, 1, 2+).
    lenient()
        .when(messageService.getMessage(anyString(), any(Locale.class), any(Object[].class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private void throwMessages() {
    lenient()
        .when(messageService.getMessage(anyString(), any(Locale.class), any(Object[].class)))
        .thenThrow(new RuntimeException("no message"));
  }

  @Test
  void logActivity_WithEvenKeyValuePairs_ShouldSaveAllPairs() {
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(campaignActivityRepository.save(any(CampaignActivity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    campaignActivityService.logActivity(
        "campaign123", OperationType.UPDATED, "k1", "v1", "k2", "v2");

    ArgumentCaptor<CampaignActivity> captor = ArgumentCaptor.forClass(CampaignActivity.class);
    verify(campaignActivityRepository).save(captor.capture());
    assertThat(captor.getValue().getValues()).containsEntry("k1", "v1").containsEntry("k2", "v2");
  }

  @Test
  void logActivity_WithUnevenKeyValuePairs_ShouldIgnoreDanglingKey() {
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(campaignActivityRepository.save(any(CampaignActivity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    campaignActivityService.logActivity(
        "campaign123", OperationType.UPDATED, "k1", "v1", "danglingKey");

    ArgumentCaptor<CampaignActivity> captor = ArgumentCaptor.forClass(CampaignActivity.class);
    verify(campaignActivityRepository).save(captor.capture());
    assertThat(captor.getValue().getValues()).containsExactly(Map.entry("k1", "v1"));
  }

  @Test
  void logActivity_WithNullValueOrNonStringKey_ShouldSkipInvalidPairs() {
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(campaignActivityRepository.save(any(CampaignActivity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    campaignActivityService.logActivity(
        "campaign123", OperationType.UPDATED, "nullVal", null, 123, "valForNonStringKey");

    ArgumentCaptor<CampaignActivity> captor = ArgumentCaptor.forClass(CampaignActivity.class);
    verify(campaignActivityRepository).save(captor.capture());
    assertThat(captor.getValue().getValues()).isEmpty();
  }

  @Test
  void logActivity_WithNullVarargs_ShouldSaveEmptyValues() {
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(campaignActivityRepository.save(any(CampaignActivity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    campaignActivityService.logActivity("campaign123", OperationType.UPDATED, (Object[]) null);

    ArgumentCaptor<CampaignActivity> captor = ArgumentCaptor.forClass(CampaignActivity.class);
    verify(campaignActivityRepository).save(captor.capture());
    assertThat(captor.getValue().getValues()).isEmpty();
  }

  // ========== generateLocalizedMessage - dispatcher arms (success/try branch) ==========

  @Test
  void generateLocalizedMessage_ForSingleInventoryDeselect_ShouldUseDeselectKey() {
    echoMessages();
    Map<String, Object> values = Map.of(INVENTORY_REFERENCE_ID.key(), "inv9");

    String message =
        campaignActivityService.generateLocalizedMessage("REMOVED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("campaign.activity.inventory.deselected");
  }

  @Test
  void generateLocalizedMessage_ForBulkInventorySelect_ShouldUseBulkSelectKey() {
    echoMessages();
    Map<String, Object> values = Map.of(SELECTED_INVENTORY_COUNT.key(), 5);

    String message =
        campaignActivityService.generateLocalizedMessage("ADDED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("campaign.activity.inventory.bulk_selected");
  }

  @Test
  void generateLocalizedMessage_ForBulkInventoryDeselect_ShouldUseBulkDeselectKey() {
    echoMessages();
    Map<String, Object> values = Map.of(DESELECTED_INVENTORY_COUNT.key(), 3);

    String message =
        campaignActivityService.generateLocalizedMessage("REMOVED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("campaign.activity.inventory.bulk_deselected");
  }

  @Test
  void generateLocalizedMessage_ForInventoryImportDelete_ShouldUseImportDeletedKey() {
    echoMessages();
    Map<String, Object> values = Map.of(INVENTORY_IMPORT_DELETED_FILENAME.key(), "a.csv");

    String message =
        campaignActivityService.generateLocalizedMessage("REMOVED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("campaign.activity.inventory_import.deleted");
  }

  @Test
  void generateLocalizedMessage_ForBulkSchedulesWithCount_ShouldUseBulkSchedulesKey() {
    echoMessages();
    Map<String, Object> values =
        Map.of(BULK_SCHEDULES_OPTIMIZATION_TYPE.key(), "Manual", BULK_SCHEDULES_COUNT.key(), 4);

    String message =
        campaignActivityService.generateLocalizedMessage("ADDED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("campaign.activity.bulk_schedules.created");
  }

  @Test
  void generateLocalizedMessage_ForBulkSchedulesWithoutCount_ShouldDefaultCountToZero() {
    throwMessages();
    Map<String, Object> values = Map.of(BULK_SCHEDULES_OPTIMIZATION_TYPE.key(), "Manual");

    String message =
        campaignActivityService.generateLocalizedMessage("ADDED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("Created schedules with Optimization Manually for 0 inventory");
  }

  @Test
  void generateLocalizedMessage_ForCommentWithFiles_ShouldUseCommentFileKey() {
    echoMessages();
    Map<String, Object> values = Map.of(COMMENT_FILE_COUNT.key(), 2);

    String message =
        campaignActivityService.generateLocalizedMessage("ADDED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("campaign.activity.comment.added.file");
  }

  @Test
  void generateLocalizedMessage_ForCommentWithoutFiles_ShouldUseCommentKey() {
    echoMessages();
    Map<String, Object> values = Map.of(COMMENT_FILE_COUNT.key(), 0);

    String message =
        campaignActivityService.generateLocalizedMessage("ADDED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("campaign.activity.comment.added");
  }

  // ========== generateLocalizedMessage - fallback (catch) branches ==========

  @Test
  void generateLocalizedMessage_ForSingleInventorySelect_WhenMessageFails_ShouldFallback() {
    throwMessages();
    Map<String, Object> values = Map.of(INVENTORY_REFERENCE_ID.key(), "inv1");

    String message =
        campaignActivityService.generateLocalizedMessage("ADDED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("Selected inventory: inv1");
  }

  @Test
  void generateLocalizedMessage_ForSingleInventoryDeselect_WhenMessageFails_ShouldFallback() {
    throwMessages();
    Map<String, Object> values = Map.of(INVENTORY_REFERENCE_ID.key(), "inv1");

    String message =
        campaignActivityService.generateLocalizedMessage("REMOVED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("Deselected inventory: inv1");
  }

  @Test
  void generateLocalizedMessage_ForBulkSelect_WhenMessageFails_ShouldFallback() {
    throwMessages();
    Map<String, Object> values = Map.of(SELECTED_INVENTORY_COUNT.key(), 5);

    String message =
        campaignActivityService.generateLocalizedMessage("ADDED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("Selected 5 inventory based on filters.");
  }

  @Test
  void generateLocalizedMessage_ForBulkDeselect_WhenMessageFails_ShouldFallback() {
    throwMessages();
    Map<String, Object> values = Map.of(DESELECTED_INVENTORY_COUNT.key(), 3);

    String message =
        campaignActivityService.generateLocalizedMessage("REMOVED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("Deselected 3 inventory based on filters.");
  }

  @Test
  void generateLocalizedMessage_ForStatusChange_WhenMessageFails_ShouldFallback() {
    throwMessages();
    Map<String, Object> values = Map.of(STATUS_FROM.key(), "DRAFT", STATUS_TO.key(), "APPROVED");

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("Updated campaign status from DRAFT to APPROVED");
  }

  @Test
  void generateLocalizedMessage_ForCsvUpload_WhenMessageFails_ShouldFallback() {
    throwMessages();
    Map<String, Object> values =
        Map.of(CSV_UPLOAD_SELECTED_COUNT.key(), 5, CSV_UPLOAD_FILENAME.key(), "f.csv");

    String message =
        campaignActivityService.generateLocalizedMessage("ADDED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("Uploaded CSV file: f.csv and selected 5 inventory");
  }

  @Test
  void generateLocalizedMessage_ForInventoryImportUsed_WhenMessageFails_ShouldFallback() {
    throwMessages();
    Map<String, Object> values =
        Map.of(INVENTORY_IMPORT_SELECTED_COUNT.key(), 7, INVENTORY_IMPORT_FILENAME.key(), "i.csv");

    String message =
        campaignActivityService.generateLocalizedMessage("ADDED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("Used inventory import: i.csv and selected 7 inventory");
  }

  @Test
  void generateLocalizedMessage_ForInventoryImportDelete_WhenMessageFails_ShouldFallback() {
    throwMessages();
    Map<String, Object> values = Map.of(INVENTORY_IMPORT_DELETED_FILENAME.key(), "a.csv");

    String message =
        campaignActivityService.generateLocalizedMessage("REMOVED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("Deleted inventory import: a.csv");
  }

  @Test
  void generateLocalizedMessage_ForScheduleUpdate_WhenMessageFails_ShouldFallback() {
    throwMessages();
    Map<String, Object> values =
        Map.of(SCHEDULES_UPDATED_COUNT.key(), 3, INVENTORY_NAME.key(), "Inv");

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("Updated 3 schedules for inventory: Inv");
  }

  @Test
  void generateLocalizedMessage_ForComment_WhenMessageFails_ShouldFallback() {
    throwMessages();
    Map<String, Object> values = Map.of(COMMENT_FILE_COUNT.key(), 2);

    String message =
        campaignActivityService.generateLocalizedMessage("ADDED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("Added campaign comment with 2 files");
  }

  @Test
  void generateLocalizedMessage_ForEmptyValues_WhenMessageFails_ShouldFallbackToVerb() {
    throwMessages();

    String message =
        campaignActivityService.generateLocalizedMessage(
            "CREATED", new HashMap<>(), Locale.ENGLISH);

    assertThat(message).isEqualTo("Created the Campaign");
  }

  // ========== approval workflow arms ==========

  @Test
  void generateLocalizedMessage_ForApprovalRejected_ShouldUseRejectedKey() {
    echoMessages();
    Map<String, Object> values =
        Map.of(APPROVAL_AUTHORITY.key(), "AGENCY", APPROVAL_ACTION.key(), "Rejected");

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("campaign.activity.approval.rejected");
  }

  @Test
  void generateLocalizedMessage_ForApprovalRequestedChanges_ShouldUseRequestedChangesKey() {
    echoMessages();
    Map<String, Object> values =
        Map.of(APPROVAL_AUTHORITY.key(), "INTERNAL", APPROVAL_ACTION.key(), "Requested Changes");

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("campaign.activity.approval.requested_changes");
  }

  @Test
  void generateLocalizedMessage_ForApprovalUnknownAction_ShouldNormalizeToKey() {
    echoMessages();
    Map<String, Object> values =
        Map.of(APPROVAL_AUTHORITY.key(), "MEDIA_OWNER", APPROVAL_ACTION.key(), "Sent Back");

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("campaign.activity.approval.sent_back");
  }

  @Test
  void generateLocalizedMessage_ForApprovalAgency_WhenMessageFails_ShouldFallbackWithAgency() {
    throwMessages();
    Map<String, Object> values =
        Map.of(APPROVAL_AUTHORITY.key(), "AGENCY", APPROVAL_ACTION.key(), "Approved");

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("Agency approved the campaign");
  }

  @Test
  void generateLocalizedMessage_ForApprovalInternal_WhenMessageFails_ShouldFallbackWithInternal() {
    throwMessages();
    Map<String, Object> values =
        Map.of(APPROVAL_AUTHORITY.key(), "INTERNAL", APPROVAL_ACTION.key(), "Rejected");

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("Internal rejected the campaign");
  }

  @Test
  void generateLocalizedMessage_ForApprovalMediaOwner_WhenMessageFails_ShouldFallback() {
    throwMessages();
    Map<String, Object> values =
        Map.of(APPROVAL_AUTHORITY.key(), "MEDIA_OWNER", APPROVAL_ACTION.key(), "Approved");

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("Media Owner approved the campaign");
  }

  @Test
  void
      generateLocalizedMessage_ForApprovalUnknownAuthority_WhenMessageFails_ShouldUseRawAuthority() {
    throwMessages();
    Map<String, Object> values =
        Map.of(APPROVAL_AUTHORITY.key(), "SUPERADMIN", APPROVAL_ACTION.key(), "Approved");

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("SUPERADMIN approved the campaign");
  }

  // ========== formatValue branches (append-fields path) ==========

  @Test
  void generateLocalizedMessage_ForGoalTypeEnum_ShouldLocalizeGoalType() {
    echoMessages();
    Map<String, Object> values = new HashMap<>();
    values.put(GOAL_TYPE.key(), Campaign.Goals.GoalType.IMPRESSIONS);

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).contains("campaign.activity.goal_type.impressions");
  }

  @Test
  void generateLocalizedMessage_ForGoalTypeString_ShouldLocalizeGoalType() {
    echoMessages();
    Map<String, Object> values = new HashMap<>();
    values.put(GOAL_TYPE.key(), "reach");

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).contains("campaign.activity.goal_type.reach");
  }

  @Test
  void generateLocalizedMessage_ForGoalTypeEnum_WhenMessageFails_ShouldFallbackToEnumName() {
    throwMessages();
    Map<String, Object> values = new HashMap<>();
    values.put(GOAL_TYPE.key(), Campaign.Goals.GoalType.SOV);

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).contains("Share of Voice");
  }

  @Test
  void generateLocalizedMessage_ForGoalTypeString_WhenMessageFails_ShouldFallbackToRawValue() {
    throwMessages();
    Map<String, Object> values = new HashMap<>();
    values.put(GOAL_TYPE.key(), "customgoal");

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).contains("customgoal");
  }

  @Test
  void generateLocalizedMessage_ForDatesString_ShouldReturnStringAsIs() {
    echoMessages();
    Map<String, Object> values = new HashMap<>();
    values.put(DATES.key(), "01/01/2024 - 02/02/2024");

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).contains("01/01/2024 - 02/02/2024");
  }

  @Test
  void generateLocalizedMessage_ForSingleLocalDateField_ShouldFormatDate() {
    echoMessages();
    Map<String, Object> values = new HashMap<>();
    values.put("startDate", LocalDate.of(2024, 1, 15));

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).contains("15/01/2024");
  }

  @Test
  void generateLocalizedMessage_ForSingleUtilDateField_ShouldFormatDate() {
    echoMessages();
    Map<String, Object> values = new HashMap<>();
    values.put(
        "startDate",
        Date.from(
            LocalDate.of(2024, 1, 15).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).contains("15/01/2024");
  }

  @Test
  void generateLocalizedMessage_ForSingleInstantField_ShouldFormatDate() {
    echoMessages();
    Map<String, Object> values = new HashMap<>();
    values.put(
        "startDate",
        LocalDate.of(2024, 1, 15).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).contains("15/01/2024");
  }

  @Test
  void generateLocalizedMessage_ForSingleParseableStringDate_ShouldFormatDate() {
    echoMessages();
    Map<String, Object> values = new HashMap<>();
    values.put("startDate", "2024-01-15");

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).contains("15/01/2024");
  }

  @Test
  void generateLocalizedMessage_ForUnparseableStringDate_ShouldFallbackToRawString() {
    echoMessages();
    Map<String, Object> values = new HashMap<>();
    values.put("startDate", "not-a-date");

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).contains("not-a-date");
  }

  @Test
  void generateLocalizedMessage_ForDatesMap_ShouldFormatRange() {
    echoMessages();
    Map<String, Object> dateMap = new HashMap<>();
    dateMap.put(START_DATE.key(), LocalDate.of(2024, 1, 1));
    dateMap.put(END_DATE.key(), LocalDate.of(2024, 2, 2));
    Map<String, Object> values = new HashMap<>();
    values.put(DATES.key(), dateMap);

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).contains("01/01/2024 - 02/02/2024");
  }

  @Test
  void generateLocalizedMessage_ForEnumField_ShouldUseEnumName() {
    echoMessages();
    Map<String, Object> values = new HashMap<>();
    values.put("clientType", Campaign.ClientType.DIRECT_ADVERTISER);

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).contains("DIRECT_ADVERTISER");
  }

  @Test
  void generateLocalizedMessage_ForEmptyCollectionField_ShouldSkipField() {
    echoMessages();
    Map<String, Object> values = new HashMap<>();
    values.put("tags", new ArrayList<>());

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    // Field skipped -> only the operation message remains (echoed key)
    assertThat(message).isEqualTo("campaign.activity.updated");
  }

  @Test
  void generateLocalizedMessage_ForNonEmptyCollectionField_ShouldJoinValues() {
    echoMessages();
    Map<String, Object> values = new HashMap<>();
    values.put("tags", List.of("a", "b"));

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).contains("a, b");
  }

  @Test
  void generateLocalizedMessage_ForNullFieldValue_ShouldSkipField() {
    echoMessages();
    Map<String, Object> values = new HashMap<>();
    values.put("someField", null);

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).isEqualTo("campaign.activity.updated");
  }

  @Test
  void generateLocalizedMessage_WhenSeparatorMessageFails_ShouldFallbackToColon() {
    // Field name + value resolve, but separator lookup throws -> fallback ": "
    when(messageService.getMessage(anyString(), any(Locale.class), any()))
        .thenAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              if (key.equals("campaign.activity.field.separator")) {
                throw new RuntimeException("no separator");
              }
              return key;
            });
    Map<String, Object> values = new HashMap<>();
    values.put("count", 42);

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).contains(": 42");
  }

  // ========== formatTargetingDemographics (via append path) ==========

  @Test
  void generateLocalizedMessage_ForTargetingDemographics_ShouldResolveNames() {
    echoMessages();
    DemographicItemDTO ageItem = new DemographicItemDTO();
    ageItem.setDemoKey("18_24");
    ageItem.setName("18-24 Years");
    DemographicsGroupedResponseDTO defaults = new DemographicsGroupedResponseDTO();
    defaults.setAge(List.of(ageItem));
    when(configService.getGroupedDemographics(any(Locale.class))).thenReturn(defaults);

    Map<String, Object> demographics = new HashMap<>();
    demographics.put("age", List.of("18_24"));
    Map<String, Object> values = new HashMap<>();
    values.put(TARGETING_DEMOGRAPHICS.key(), demographics);

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).contains("18-24 Years");
  }

  @Test
  void generateLocalizedMessage_ForTargetingDemographicsUnknownKey_ShouldFallbackToRawKey() {
    echoMessages();
    DemographicsGroupedResponseDTO defaults = new DemographicsGroupedResponseDTO();
    when(configService.getGroupedDemographics(any(Locale.class))).thenReturn(defaults);

    Map<String, Object> demographics = new HashMap<>();
    demographics.put("age", List.of("99_plus"));
    Map<String, Object> values = new HashMap<>();
    values.put(TARGETING_DEMOGRAPHICS.key(), demographics);

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).contains("99_plus");
  }

  @Test
  void
      generateLocalizedMessage_ForTargetingDemographics_WhenLookupThrows_ShouldFallbackToToString() {
    echoMessages();
    when(configService.getGroupedDemographics(any(Locale.class)))
        .thenThrow(new RuntimeException("config error"));

    Map<String, Object> demographics = new HashMap<>();
    demographics.put("age", List.of("18_24"));
    Map<String, Object> values = new HashMap<>();
    values.put(TARGETING_DEMOGRAPHICS.key(), demographics);

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).contains("18_24");
  }

  @Test
  void generateLocalizedMessage_ForEmptyTargetingDemographics_ShouldReturnEmptyBraces() {
    echoMessages();
    Map<String, Object> values = new HashMap<>();
    values.put(TARGETING_DEMOGRAPHICS.key(), new HashMap<>());

    String message =
        campaignActivityService.generateLocalizedMessage("UPDATED", values, Locale.ENGLISH);

    assertThat(message).contains("{}");
  }

  // ========== getCampaignHistory - buildCompanyRoleMap branches ==========

  private CampaignActivity activityWithCompany(String companyId) {
    return CampaignActivity.builder()
        .campaignId("campaign123")
        .userId("user123")
        .companyId(companyId)
        .updatedBy("John Doe")
        .operationType("CREATED")
        .values(Map.of("name", "Test"))
        .build();
  }

  @Test
  void getCampaignHistory_WhenAllCompanyIdsAreSystem_ShouldNotCallCompanyService() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<CampaignActivity> page =
        new PageImpl<>(List.of(activityWithCompany("System")), pageable, 1);
    when(campaignActivityRepository.findByCampaignId("campaign123", pageable)).thenReturn(page);
    echoMessages();

    Page<CampaignActivityResponseDTO> result =
        campaignActivityService.getCampaignHistory("campaign123", Locale.ENGLISH, pageable);

    assertThat(result.getContent()).hasSize(1);
    verify(companyService, never()).getCompaniesByIds(anyList());
  }

  @Test
  void getCampaignHistory_WhenCompanyServiceThrows_ShouldStillReturnHistory() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<CampaignActivity> page =
        new PageImpl<>(List.of(activityWithCompany("company123")), pageable, 1);
    when(campaignActivityRepository.findByCampaignId("campaign123", pageable)).thenReturn(page);
    when(companyService.getCompaniesByIds(anyList())).thenThrow(new RuntimeException("iam down"));
    echoMessages();

    Page<CampaignActivityResponseDTO> result =
        campaignActivityService.getCampaignHistory("campaign123", Locale.ENGLISH, pageable);

    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  void getCampaignHistory_WhenCompanyHasNullBusinessType_ShouldSkipRoleMapping() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<CampaignActivity> page =
        new PageImpl<>(List.of(activityWithCompany("company123")), pageable, 1);
    CompanyDto companyNoType = new CompanyDto();
    companyNoType.setId("company123");
    companyNoType.setBusinessType(null);
    when(campaignActivityRepository.findByCampaignId("campaign123", pageable)).thenReturn(page);
    when(companyService.getCompaniesByIds(anyList())).thenReturn(List.of(companyNoType));
    echoMessages();

    Page<CampaignActivityResponseDTO> result =
        campaignActivityService.getCampaignHistory("campaign123", Locale.ENGLISH, pageable);

    assertThat(result.getContent()).hasSize(1);
  }

  // ========== buildCreationChanges - targeting geofencing/signals ==========

  @Test
  void buildCreationChanges_WithGeofencingAndSignals_ShouldAddGeographicsAndSignals() {
    Campaign.Targeting.Geofencing.Location location =
        Campaign.Targeting.Geofencing.Location.builder().name("Downtown").lat(1.0).lng(2.0).build();
    Campaign.Targeting.Geofencing.Geometry geometry =
        Campaign.Targeting.Geofencing.Geometry.builder()
            .name("Zone A")
            .type("Polygon")
            .coordinates(List.of(List.of(1.0, 2.0)))
            .build();
    Campaign.Targeting.Geofencing geofencing =
        Campaign.Targeting.Geofencing.builder()
            .locations(List.of(location))
            .geometries(List.of(geometry))
            .build();
    Campaign.Targeting targeting =
        Campaign.Targeting.builder().geofencing(geofencing).signals(List.of("signal1")).build();
    testCampaign.setTargeting(targeting);

    Map<String, Object> changes = campaignActivityService.buildCreationChanges(testCampaign);

    assertThat(changes).containsKey(TARGETING_GEOGRAPHICS.key());
    assertThat(changes).containsKey(TARGETING_SIGNALS.key());
    @SuppressWarnings("unchecked")
    List<String> geo = (List<String>) changes.get(TARGETING_GEOGRAPHICS.key());
    assertThat(geo).contains("Downtown", "Zone A");
  }

  @Test
  void buildCreationChanges_WithInventoryCluster_ShouldAddInventoryCluster() {
    Campaign.Targeting targeting =
        Campaign.Targeting.builder().inventoryCluster(List.of("cluster-A", "cluster-B")).build();
    testCampaign.setTargeting(targeting);

    Map<String, Object> changes = campaignActivityService.buildCreationChanges(testCampaign);

    assertThat(changes).containsKey(TARGETING_INVENTORY_CLUSTER.key());
    @SuppressWarnings("unchecked")
    List<String> cluster = (List<String>) changes.get(TARGETING_INVENTORY_CLUSTER.key());
    assertThat(cluster).containsExactly("cluster-A", "cluster-B");
  }

  @Test
  void buildCreationChanges_WithEmptyInventoryCluster_ShouldNotAddInventoryCluster() {
    Campaign.Targeting targeting = Campaign.Targeting.builder().inventoryCluster(List.of()).build();
    testCampaign.setTargeting(targeting);

    Map<String, Object> changes = campaignActivityService.buildCreationChanges(testCampaign);

    assertThat(changes).doesNotContainKey(TARGETING_INVENTORY_CLUSTER.key());
  }

  @Test
  void buildCreationChanges_WithEmptyDemographics_ShouldNotAddDemographics() {
    Campaign.Targeting targeting =
        Campaign.Targeting.builder().demographics(new HashMap<>()).build();
    testCampaign.setTargeting(targeting);

    Map<String, Object> changes = campaignActivityService.buildCreationChanges(testCampaign);

    assertThat(changes).doesNotContainKey(TARGETING_DEMOGRAPHICS.key());
  }

  // ========== buildUpdateChanges - compare helper branches ==========

  private Campaign baseCampaign() {
    return Campaign.builder()
        .name("Campaign")
        .startDate(LocalDate.of(2024, 1, 1))
        .endDate(LocalDate.of(2024, 2, 1))
        .userId("user123")
        .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
        .companyId("company123")
        .build();
  }

  @Test
  void buildUpdateChanges_WhenGoalsAddedFromNull_ShouldIncludeAllGoalFields() {
    Campaign oldCampaign = baseCampaign();
    Campaign.Goals goals = new Campaign.Goals();
    goals.setGoalType(Campaign.Goals.GoalType.REACH);
    goals.setTargetValue(500.0);
    goals.setTargetName("Reach goal");
    Campaign newCampaign = baseCampaign();
    newCampaign.setGoals(goals);

    Map<String, Object> changes =
        campaignActivityService.buildUpdateChanges(oldCampaign, newCampaign);

    assertThat(changes).containsKey(GOAL_TYPE.key());
    assertThat(changes).containsKey(GOAL_VALUE.key());
    assertThat(changes).containsKey(GOAL_TARGET_NAME.key());
  }

  @Test
  void buildUpdateChanges_WhenGoalsUnchanged_ShouldNotIncludeGoals() {
    Campaign.Goals goals = new Campaign.Goals();
    goals.setGoalType(Campaign.Goals.GoalType.REACH);
    Campaign oldCampaign = baseCampaign();
    oldCampaign.setGoals(goals);
    Campaign newCampaign = baseCampaign();
    Campaign.Goals sameGoals = new Campaign.Goals();
    sameGoals.setGoalType(Campaign.Goals.GoalType.REACH);
    newCampaign.setGoals(sameGoals);

    Map<String, Object> changes =
        campaignActivityService.buildUpdateChanges(oldCampaign, newCampaign);

    assertThat(changes).doesNotContainKey(GOAL_TYPE.key());
  }

  @Test
  void buildUpdateChanges_WhenGeofencingAndSignalsChanged_ShouldIncludeThem() {
    Campaign oldCampaign = baseCampaign();
    Campaign.Targeting.Geofencing.Location location =
        Campaign.Targeting.Geofencing.Location.builder().name("Loc").lat(1.0).lng(2.0).build();
    Campaign.Targeting newTargeting =
        Campaign.Targeting.builder()
            .geofencing(
                Campaign.Targeting.Geofencing.builder().locations(List.of(location)).build())
            .signals(List.of("s1"))
            .build();
    Campaign newCampaign = baseCampaign();
    newCampaign.setTargeting(newTargeting);

    Map<String, Object> changes =
        campaignActivityService.buildUpdateChanges(oldCampaign, newCampaign);

    assertThat(changes).containsKey(TARGETING_GEOGRAPHICS.key());
    assertThat(changes).containsKey(TARGETING_SIGNALS.key());
  }

  @Test
  void buildUpdateChanges_WhenInventoryClusterChanged_ShouldIncludeIt() {
    Campaign oldCampaign = baseCampaign();
    Campaign.Targeting oldTargeting =
        Campaign.Targeting.builder().inventoryCluster(List.of("cluster-A")).build();
    oldCampaign.setTargeting(oldTargeting);

    Campaign.Targeting newTargeting =
        Campaign.Targeting.builder().inventoryCluster(List.of("cluster-B")).build();
    Campaign newCampaign = baseCampaign();
    newCampaign.setTargeting(newTargeting);

    Map<String, Object> changes =
        campaignActivityService.buildUpdateChanges(oldCampaign, newCampaign);

    assertThat(changes).containsKey(TARGETING_INVENTORY_CLUSTER.key());
  }

  @Test
  void buildUpdateChanges_WhenInventoryClusterUnchanged_ShouldNotIncludeIt() {
    Campaign.Targeting targeting =
        Campaign.Targeting.builder().inventoryCluster(List.of("cluster-A")).build();
    Campaign oldCampaign = baseCampaign();
    oldCampaign.setTargeting(targeting);
    Campaign.Targeting sameTargeting =
        Campaign.Targeting.builder().inventoryCluster(List.of("cluster-A")).build();
    Campaign newCampaign = baseCampaign();
    newCampaign.setTargeting(sameTargeting);

    Map<String, Object> changes =
        campaignActivityService.buildUpdateChanges(oldCampaign, newCampaign);

    assertThat(changes).doesNotContainKey(TARGETING_INVENTORY_CLUSTER.key());
  }

  @Test
  void buildUpdateChanges_WhenBudgetAllocationChanged_ShouldIncludeAllocation() {
    Campaign oldCampaign = baseCampaign();
    Campaign newCampaign = baseCampaign();
    newCampaign.setBudgetAllocation(Map.of("inv1", 100.0));

    Map<String, Object> changes =
        campaignActivityService.buildUpdateChanges(oldCampaign, newCampaign);

    assertThat(changes).containsKey(BUDGET_ALLOCATION.key());
  }

  @Test
  void buildUpdateChanges_WhenCompanyAccessChanged_ShouldIncludeCompanyAccess() {
    Campaign oldCampaign = baseCampaign();
    Campaign newCampaign = baseCampaign();
    newCampaign.setCompanyAccess(List.of("companyX"));

    Map<String, Object> changes =
        campaignActivityService.buildUpdateChanges(oldCampaign, newCampaign);

    assertThat(changes).containsKey(COMPANY_ACCESS.key());
  }

  @Test
  void buildUpdateChanges_WhenBrandAndAgencyIdChanged_ShouldIncludeNames() {
    Campaign oldCampaign = baseCampaign();
    Campaign newCampaign = baseCampaign();
    newCampaign.setBrand(Campaign.CampaignBrand.builder().id("brandNew").name("New Brand").build());
    newCampaign.setAgency(
        Campaign.CampaignAgency.builder().id("agencyNew").name("New Agency").build());

    Map<String, Object> changes =
        campaignActivityService.buildUpdateChanges(oldCampaign, newCampaign);

    assertThat(changes.get(BRAND.key())).isEqualTo("New Brand");
    assertThat(changes.get(AGENCY.key())).isEqualTo("New Agency");
  }
}
