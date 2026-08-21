package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.brand.lib.dto.BrandResponseDTO;
import com.mw.brand.lib.repository.BrandRepository;
import com.mw.brand.lib.service.BrandService;
import com.mw.planner.domain.*;
import com.mw.planner.dto.*;
import com.mw.planner.dto.CompanyDto;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.enums.CostSplit;
import com.mw.planner.exception.campaign.CampaignAgencyNotValidException;
import com.mw.planner.exception.campaign.CampaignAlreadyExistsException;
import com.mw.planner.exception.campaign.CampaignDateRangeException;
import com.mw.planner.exception.campaign.CampaignInvalidStatusException;
import com.mw.planner.exception.campaign.CampaignNotFoundException;
import com.mw.planner.exception.campaign.CampaignValidationException;
import com.mw.planner.exception.user.UserNotFoundException;
import com.mw.planner.repository.CampaignRepository;
import com.mw.planner.repository.ScheduleRepository;
import com.mw.planner.service.config.DefaultConfigurationService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Locale;
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
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

  @Mock private CampaignRepository campaignRepository;
  @Mock private SequencerService sequencerService;
  @Mock private MessageService messageService;
  @Mock private BrandRepository brandRepository;
  @Mock private DefaultConfigurationService defaultConfigurationService;
  @Mock private MwMeasureService mwMeasureService;
  @Mock private BrandService brandService;
  @Mock private InventoryService inventoryService;
  @Mock private CampaignInventorySchedulesService configService;
  @Mock private UserService userService;
  @Mock private CountryService countryService;
  @Mock private AgencyService agencyService;
  @Mock private CampaignProposalStatusAndCommentService campaignProposalStatusAndCommentService;
  @Mock private com.mw.planner.repository.CampaignCommentsRepository campaignCommentsRepository;
  @Mock private com.mw.planner.service.storage.CloudStorageService cloudStorageService;
  @Mock private CompanyService companyService;
  @Mock private CampaignApprovalWorkflowService campaignApprovalWorkflowService;
  @Mock private StateService stateService;
  @Mock private DistrictService districtService;
  @Mock private CampaignActivityService campaignActivityService;
  @Mock private ScheduleRepository scheduleRepository;
  @Mock private CustomFeeService customFeeService;
  @Mock private VenuesService venuesService;
  @Mock private TestModeService testModeService;

  @InjectMocks private CampaignService campaignService;

  private Campaign testCampaign;
  private CampaignRequestDTO testCampaignRequestDTO;
  private IamUserContext testUserContext;
  private UserResponseDTO testUserResponseDTO;

  @BeforeEach
  void setUp() {
    org.springframework.test.util.ReflectionTestUtils.setField(
        campaignService, "testModeService", testModeService);
    // Default: test campaigns are in the caller's mode; cross-mode cases stub this to false.
    lenient().when(testModeService.matchesCallerMode(any(Campaign.class))).thenReturn(true);
    lenient().when(testModeService.hasAuthenticatedCaller()).thenReturn(true);
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

    testCampaignRequestDTO =
        CampaignRequestDTO.builder()
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

    testUserContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .locale(Locale.ENGLISH)
            .build();

    testUserResponseDTO = new UserResponseDTO();
    testUserResponseDTO.setId("user123");
    testUserResponseDTO.setFirstName("Test");
    testUserResponseDTO.setLastName("User");
    testUserResponseDTO.setActiveCompanyId("company123");

    // Every campaign-creation path now generates a plan number; stub it leniently so tests that
    // don't care about the exact value (most of them) don't each need their own stub.
    lenient().when(sequencerService.getNextSequenceAtomic(anyString())).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    // Reset mocks to clear any interactions for next test
    reset(
        campaignRepository,
        sequencerService,
        messageService,
        brandRepository,
        defaultConfigurationService,
        configService,
        inventoryService,
        mwMeasureService,
        brandService,
        userService,
        countryService,
        agencyService,
        campaignProposalStatusAndCommentService,
        campaignCommentsRepository,
        cloudStorageService,
        companyService,
        campaignApprovalWorkflowService,
        stateService,
        districtService,
        campaignActivityService);
  }

  // ========== createCampaign Tests ==========

  @Test
  void createCampaign_WithValidData_ShouldCreateCampaign() {
    // Given
    when(campaignRepository.findByNameIgnoreCase("Test Campaign")).thenReturn(Optional.empty());
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    when(sequencerService.incrementSequenceForCampaignName("Test Campaign")).thenReturn(1L);
    when(campaignActivityService.buildCreationChanges(any(Campaign.class)))
        .thenReturn(Map.of("name", "Test Campaign"));
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    CampaignResponseDTO result = campaignService.createCampaign(testCampaignRequestDTO);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("Test Campaign");
    assertThat(result.getStatus()).isEqualTo(Campaign.Status.DRAFT);
    assertThat(result.getBudget()).isEqualTo(10000.0);
    assertThat(result.getIsNegotiated()).isEqualTo(testCampaign.getIsNegotiated());

    verify(campaignRepository).findByNameIgnoreCase("Test Campaign");
    verify(campaignRepository).save(any(Campaign.class));
    verify(sequencerService).incrementSequenceForCampaignName("Test Campaign");
    verify(campaignActivityService).buildCreationChanges(any(Campaign.class));
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void createCampaign_ShouldGenerateTodayDatedPlanNumberBeforeSaving() {
    // Given
    when(campaignRepository.findByNameIgnoreCase("Test Campaign")).thenReturn(Optional.empty());
    when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
    when(sequencerService.getNextSequenceAtomic(anyString())).thenReturn(7L);
    when(sequencerService.incrementSequenceForCampaignName("Test Campaign")).thenReturn(1L);
    when(campaignActivityService.buildCreationChanges(any(Campaign.class)))
        .thenReturn(Map.of("name", "Test Campaign"));
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    CampaignResponseDTO result = campaignService.createCampaign(testCampaignRequestDTO);

    // Then
    String expectedDatePrefix =
        LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    String expectedPlanNumber = expectedDatePrefix + "0007";

    ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
    verify(campaignRepository).save(captor.capture());
    assertThat(captor.getValue().getPlanNumber()).isEqualTo(expectedPlanNumber);
    assertThat(result.getPlanNumber()).isEqualTo(expectedPlanNumber);
    verify(sequencerService).getNextSequenceAtomic("PLAN_" + expectedDatePrefix);
  }

  @Test
  void createCampaign_WithPerformance_ShouldPersistPerformance() {
    // Given
    CampaignForecastDTO performance = buildTestForecast();
    testCampaignRequestDTO.setPerformance(performance);

    when(campaignRepository.findByNameIgnoreCase("Test Campaign")).thenReturn(Optional.empty());
    when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
    when(sequencerService.incrementSequenceForCampaignName("Test Campaign")).thenReturn(1L);
    when(campaignActivityService.buildCreationChanges(any(Campaign.class)))
        .thenReturn(Map.of("name", "Test Campaign"));
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    CampaignResponseDTO result = campaignService.createCampaign(testCampaignRequestDTO);

    // Then
    ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
    verify(campaignRepository).save(captor.capture());
    assertThat(captor.getValue().getPerformance()).isEqualTo(performance);
    assertThat(result.getPerformance()).isEqualTo(performance);
  }

  @Test
  void createCampaign_WithExistingName_ShouldThrowException() {
    // Given
    when(campaignRepository.findByNameIgnoreCase("Test Campaign"))
        .thenReturn(Optional.of(testCampaign));

    // When & Then
    assertThatThrownBy(() -> campaignService.createCampaign(testCampaignRequestDTO))
        .isInstanceOf(CampaignAlreadyExistsException.class)
        .hasMessageContaining("Test Campaign");

    verify(campaignRepository).findByNameIgnoreCase("Test Campaign");
    verify(campaignRepository, never()).save(any(Campaign.class));
  }

  @Test
  void createCampaign_WithNullStatus_ShouldSetDefaultStatus() {
    // Given
    testCampaignRequestDTO.setStatus(null);
    when(campaignRepository.findByNameIgnoreCase("Test Campaign")).thenReturn(Optional.empty());
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    when(sequencerService.incrementSequenceForCampaignName("Test Campaign")).thenReturn(1L);

    // When
    CampaignResponseDTO result = campaignService.createCampaign(testCampaignRequestDTO);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(Campaign.Status.DRAFT);

    verify(campaignRepository).findByNameIgnoreCase("Test Campaign");
    verify(campaignRepository).save(any(Campaign.class));
  }

  @Test
  void createCampaign_WithNullCurrency_ShouldSetDefaultCurrency() {
    // Given
    testCampaignRequestDTO.setCurrency(null);
    when(campaignRepository.findByNameIgnoreCase("Test Campaign")).thenReturn(Optional.empty());
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    when(sequencerService.incrementSequenceForCampaignName("Test Campaign")).thenReturn(1L);

    // When
    CampaignResponseDTO result = campaignService.createCampaign(testCampaignRequestDTO);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getCurrency()).isEqualTo("USD");

    verify(campaignRepository).findByNameIgnoreCase("Test Campaign");
    verify(campaignRepository).save(any(Campaign.class));
  }

  @Test
  void createCampaign_WithPastStartDate_ShouldThrowException() {
    // Given
    testCampaignRequestDTO.setStartDate(LocalDate.now().minusDays(1));

    // When & Then
    assertThatThrownBy(() -> campaignService.createCampaign(testCampaignRequestDTO))
        .isInstanceOf(CampaignValidationException.class);

    // Validation happens before repository calls
    verify(campaignRepository, never()).findByNameIgnoreCase(anyString());
    verify(campaignRepository, never()).save(any(Campaign.class));
  }

  @Test
  void createCampaign_WithInvalidDateRange_ShouldThrowException() {
    // Given
    testCampaignRequestDTO.setStartDate(LocalDate.now().plusDays(10));
    testCampaignRequestDTO.setEndDate(LocalDate.now().plusDays(5));

    // When & Then
    assertThatThrownBy(() -> campaignService.createCampaign(testCampaignRequestDTO))
        .isInstanceOf(CampaignDateRangeException.class);

    // Validation happens before repository calls
    verify(campaignRepository, never()).findByNameIgnoreCase(anyString());
    verify(campaignRepository, never()).save(any(Campaign.class));
  }

  @Test
  void createCampaign_WithNegativeBudget_ShouldThrowException() {
    // Given
    testCampaignRequestDTO.setBudget(-100.0);

    // When & Then
    assertThatThrownBy(() -> campaignService.createCampaign(testCampaignRequestDTO))
        .isInstanceOf(CampaignValidationException.class);

    // Validation happens before repository calls
    verify(campaignRepository, never()).findByNameIgnoreCase(anyString());
    verify(campaignRepository, never()).save(any(Campaign.class));
  }

  @Test
  void createCampaign_WithAgencyClientTypeButNoAgencyId_ShouldThrowException() {
    // Given
    testCampaignRequestDTO.setClientType(Campaign.ClientType.AGENCY);
    testCampaignRequestDTO.setAgency(null);

    // When & Then
    assertThatThrownBy(() -> campaignService.createCampaign(testCampaignRequestDTO))
        .isInstanceOf(CampaignAgencyNotValidException.class);

    // Validation happens before repository calls
    verify(campaignRepository, never()).findByNameIgnoreCase(anyString());
    verify(campaignRepository, never()).save(any(Campaign.class));
  }

  @Test
  void createCampaign_WithSequencerServiceFailure_ShouldStillCreateCampaign() {
    // Given
    when(campaignRepository.findByNameIgnoreCase("Test Campaign")).thenReturn(Optional.empty());
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    when(sequencerService.incrementSequenceForCampaignName("Test Campaign"))
        .thenThrow(new RuntimeException("Sequencer service error"));

    // When
    CampaignResponseDTO result = campaignService.createCampaign(testCampaignRequestDTO);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("Test Campaign");

    verify(campaignRepository).findByNameIgnoreCase("Test Campaign");
    verify(campaignRepository).save(any(Campaign.class));
    verify(sequencerService).incrementSequenceForCampaignName("Test Campaign");
  }

  // ========== getCampaignById Tests ==========

  @Test
  void getCampaignById_WithValidId_ShouldReturnCampaign() {
    // Given
    long inventoryCount = 5L;

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(configService.countByCampaignId("campaign123")).thenReturn(inventoryCount);
    when(userService.getUserById("user123")).thenReturn(testUserResponseDTO);
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", "company123"))
        .thenReturn(null);

    // When
    CampaignResponseDTO result = campaignService.getCampaignById("campaign123");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");
    assertThat(result.getName()).isEqualTo("Test Campaign");
    assertThat(result.getBrand().getName()).isNull(); // getCampaignById doesn't set brandName
    assertThat(result.getAgency()).isNull();
    assertThat(result.getInventoryCount()).isEqualTo(inventoryCount);

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(configService).countByCampaignId("campaign123");
  }

  @Test
  void getCampaignById_WithBrandAndAgency_ShouldReturnCampaignWithNames() {
    // Given
    testCampaign.setAgency(Campaign.CampaignAgency.builder().id("agency123").build());
    long inventoryCount = 10L;

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(configService.countByCampaignId("campaign123")).thenReturn(inventoryCount);
    when(userService.getUserById("user123")).thenReturn(testUserResponseDTO);
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", "company123"))
        .thenReturn(null);

    // When
    CampaignResponseDTO result = campaignService.getCampaignById("campaign123");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");
    assertThat(result.getBrand().getId()).isEqualTo("brand123");
    assertThat(result.getAgency().getId()).isEqualTo("agency123");
    assertThat(result.getBrand().getName()).isNull(); // getCampaignById doesn't set brandName
    assertThat(result.getAgency().getName()).isNull();
    assertThat(result.getInventoryCount()).isEqualTo(inventoryCount);

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(configService).countByCampaignId("campaign123");
  }

  @Test
  void getCampaignById_WithNullBrand_ShouldReturnCampaignWithoutBrand() {
    // Given
    testCampaign.setBrand(null);
    long inventoryCount = 3L;

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(configService.countByCampaignId("campaign123")).thenReturn(inventoryCount);
    when(userService.getUserById("user123")).thenReturn(testUserResponseDTO);
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", "company123"))
        .thenReturn(null);

    // When
    CampaignResponseDTO result = campaignService.getCampaignById("campaign123");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");
    assertThat(result.getBrand()).isNull();
    assertThat(result.getInventoryCount()).isEqualTo(inventoryCount);

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(brandService, never()).getBrandById(anyString());
    verify(configService).countByCampaignId("campaign123");
  }

  @Test
  void getCampaignById_WithInvalidId_ShouldThrowException() {
    // Given
    when(campaignRepository.findById("invalid123")).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> campaignService.getCampaignById("invalid123"))
        .isInstanceOf(CampaignNotFoundException.class)
        .hasMessageContaining("invalid123");

    verify(campaignRepository).findById("invalid123");
  }

  @Test
  void getCampaignById_WithZeroInventoryCount_ShouldReturnCampaignWithZeroCount() {
    // Given
    long inventoryCount = 0L;

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(configService.countByCampaignId("campaign123")).thenReturn(inventoryCount);
    when(userService.getUserById("user123")).thenReturn(testUserResponseDTO);
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", "company123"))
        .thenReturn(null);

    // When
    CampaignResponseDTO result = campaignService.getCampaignById("campaign123");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");
    assertThat(result.getInventoryCount()).isEqualTo(0L);

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(configService).countByCampaignId("campaign123");
  }

  // ========== getCampaignByIdForPublicAccess Tests ==========

  @Test
  void getCampaignByIdForPublicAccess_WithUserContext_ShouldReturnCampaign() {
    // Given
    long inventoryCount = 5L;

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(configService.countByCampaignId("campaign123")).thenReturn(inventoryCount);
    when(userService.getUserById("user123")).thenReturn(testUserResponseDTO);
    when(userService.getIamUserContext()).thenReturn(testUserContext);

    // When
    CampaignResponseDTO result = campaignService.getCampaignByIdForPublicAccess("campaign123");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");
    assertThat(result.getName()).isEqualTo("Test Campaign");
    assertThat(result.getInventoryCount()).isEqualTo(inventoryCount);

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(configService).countByCampaignId("campaign123");
  }

  @Test
  void getCampaignByIdForPublicAccess_WithoutAuth_ShouldFallBackAndReturnCampaign() {
    // Given: unauthenticated public caller — user lookup and IAM context both fail
    long inventoryCount = 2L;

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(configService.countByCampaignId("campaign123")).thenReturn(inventoryCount);
    when(userService.getUserById("user123"))
        .thenThrow(new UserNotFoundException("no authenticated user"));
    when(userService.getIamUserContext())
        .thenThrow(new UserNotFoundException("no authenticated user"));

    // When
    CampaignResponseDTO result = campaignService.getCampaignByIdForPublicAccess("campaign123");

    // Then: exceptions swallowed, falls back to campaign's own companyId, still returns DTO
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");
    assertThat(result.getName()).isEqualTo("Test Campaign");
    assertThat(result.getStatus()).isEqualTo(Campaign.Status.DRAFT);
    assertThat(result.getInventoryCount()).isEqualTo(inventoryCount);

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(configService).countByCampaignId("campaign123");
  }

  @Test
  void getCampaignByIdForPublicAccess_WithInvalidId_ShouldThrowException() {
    // Given
    when(campaignRepository.findById("invalid123")).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> campaignService.getCampaignByIdForPublicAccess("invalid123"))
        .isInstanceOf(CampaignNotFoundException.class)
        .hasMessageContaining("invalid123");

    verify(campaignRepository).findById("invalid123");
  }

  // ========== getCampaignByName Tests ==========

  @Test
  void getCampaignByName_WithValidName_ShouldReturnCampaign() {
    // Given
    when(campaignRepository.findByNameIgnoreCase("Test Campaign"))
        .thenReturn(Optional.of(testCampaign));

    // When
    CampaignResponseDTO result = campaignService.getCampaignByName("Test Campaign");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("Test Campaign");

    verify(campaignRepository).findByNameIgnoreCase("Test Campaign");
  }

  @Test
  void getCampaignByName_WithInvalidName_ShouldThrowException() {
    // Given
    when(campaignRepository.findByNameIgnoreCase("Invalid Campaign")).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> campaignService.getCampaignByName("Invalid Campaign"))
        .isInstanceOf(CampaignNotFoundException.class)
        .hasMessageContaining("Invalid Campaign");

    verify(campaignRepository).findByNameIgnoreCase("Invalid Campaign");
  }

  // ========== updateCampaign Tests ==========

  @Test
  void updateCampaign_WithValidData_ShouldUpdateCampaign() {
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
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();

    Campaign updatedCampaign =
        Campaign.builder()
            .name("Updated Campaign")
            .description("Updated Description")
            .status(Campaign.Status.APPROVED)
            .budget(15000.0)
            .currency("EUR")
            .startDate(LocalDate.now().plusDays(2))
            .endDate(LocalDate.now().plusDays(35))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").name("Test Brand").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .build();
    updatedCampaign.setId("campaign123");
    updatedCampaign.setCreatedAt(LocalDateTime.now());
    updatedCampaign.setUpdatedAt(LocalDateTime.now());

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.findByNameIgnoreCase("Updated Campaign")).thenReturn(Optional.empty());
    when(campaignRepository.save(any(Campaign.class))).thenReturn(updatedCampaign);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of("name", "Updated Campaign", "status", Campaign.Status.APPROVED));
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    CampaignResponseDTO result = campaignService.updateCampaign("campaign123", updateRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("Updated Campaign");
    assertThat(result.getStatus()).isEqualTo(Campaign.Status.APPROVED);
    assertThat(result.getBudget()).isEqualTo(15000.0);
    assertThat(result.getBrand().getName()).isEqualTo("Test Brand");

    ArgumentCaptor<Campaign> oldCampaignCaptor = ArgumentCaptor.forClass(Campaign.class);
    ArgumentCaptor<Campaign> newCampaignCaptor = ArgumentCaptor.forClass(Campaign.class);

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).findByNameIgnoreCase("Updated Campaign");
    verify(campaignRepository).save(any(Campaign.class));
    verify(campaignActivityService)
        .buildUpdateChanges(oldCampaignCaptor.capture(), newCampaignCaptor.capture());
    assertThat(oldCampaignCaptor.getValue().getName()).isEqualTo("Test Campaign");
    assertThat(newCampaignCaptor.getValue().getName()).isEqualTo("Updated Campaign");
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void updateCampaign_WithBrandAndAgency_ShouldReturnUpdatedCampaignWithNames() {
    // Given
    testCampaign.setAgency(
        Campaign.CampaignAgency.builder().id("agency123").name("Test Agency").build());
    CampaignRequestDTO updateRequest =
        CampaignRequestDTO.builder()
            .name("Updated Campaign")
            .description("Updated Description")
            .status(Campaign.Status.APPROVED)
            .budget(15000.0)
            .currency("EUR")
            .startDate(LocalDate.now().plusDays(2))
            .endDate(LocalDate.now().plusDays(35))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .agency(Campaign.CampaignAgency.builder().id("agency123").name("Test Agency").build())
            .build();

    Campaign updatedCampaign =
        Campaign.builder()
            .name("Updated Campaign")
            .description("Updated Description")
            .status(Campaign.Status.APPROVED)
            .budget(15000.0)
            .currency("EUR")
            .startDate(LocalDate.now().plusDays(2))
            .endDate(LocalDate.now().plusDays(35))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").name("Test Brand").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .agency(Campaign.CampaignAgency.builder().id("agency123").name("Test Agency").build())
            .countryId("US")
            .build();
    updatedCampaign.setId("campaign123");
    updatedCampaign.setCreatedAt(LocalDateTime.now());
    updatedCampaign.setUpdatedAt(LocalDateTime.now());

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");
    String agencyName = "Test Agency";

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.findByNameIgnoreCase("Updated Campaign")).thenReturn(Optional.empty());
    when(campaignRepository.save(any(Campaign.class))).thenReturn(updatedCampaign);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of("name", "Updated Campaign"));
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    CampaignResponseDTO result = campaignService.updateCampaign("campaign123", updateRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("Updated Campaign");
    assertThat(result.getBrand().getName()).isEqualTo("Test Brand");
    assertThat(result.getAgency().getName()).isEqualTo(agencyName);

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).save(any(Campaign.class));
    verify(campaignActivityService).buildUpdateChanges(any(Campaign.class), any(Campaign.class));
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void updateCampaign_WithSameName_ShouldUpdateCampaign() {
    // Given
    CampaignRequestDTO updateRequest =
        CampaignRequestDTO.builder()
            .name("Test Campaign") // Same name
            .description("Updated Description")
            .status(Campaign.Status.APPROVED)
            .budget(15000.0)
            .currency("EUR")
            .startDate(LocalDate.now().plusDays(2))
            .endDate(LocalDate.now().plusDays(35))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").name("Test Brand").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    testCampaign.getBrand().setName("Test Brand");
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of("description", "Updated Description"));
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    CampaignResponseDTO result = campaignService.updateCampaign("campaign123", updateRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("Test Campaign");
    assertThat(result.getBrand().getName()).isEqualTo("Test Brand");

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository, never()).findByNameIgnoreCase(anyString());
    verify(campaignRepository).save(any(Campaign.class));
    verify(campaignActivityService).buildUpdateChanges(any(Campaign.class), any(Campaign.class));
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void updateCampaign_WithExistingNameFromDifferentCampaign_ShouldThrowException() {
    // Given
    Campaign existingCampaign =
        Campaign.builder()
            .name("Existing Campaign")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(10))
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .userId("user123")
            .companyId("company123")
            .countryId("US")
            .build();
    existingCampaign.setId("different123");

    CampaignRequestDTO updateRequest =
        CampaignRequestDTO.builder()
            .name("Existing Campaign")
            .description("Updated Description")
            .status(Campaign.Status.APPROVED)
            .budget(15000.0)
            .currency("EUR")
            .startDate(LocalDate.now().plusDays(2))
            .endDate(LocalDate.now().plusDays(35))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.findByNameIgnoreCase("Existing Campaign"))
        .thenReturn(Optional.of(existingCampaign));

    // When & Then
    assertThatThrownBy(() -> campaignService.updateCampaign("campaign123", updateRequest))
        .isInstanceOf(CampaignAlreadyExistsException.class)
        .hasMessageContaining("Existing Campaign");

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).findByNameIgnoreCase("Existing Campaign");
    verify(campaignRepository, never()).save(any(Campaign.class));
  }

  @Test
  void updateCampaign_WithInvalidId_ShouldThrowException() {
    // Given
    when(campaignRepository.findById("invalid123")).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> campaignService.updateCampaign("invalid123", testCampaignRequestDTO))
        .isInstanceOf(CampaignNotFoundException.class)
        .hasMessageContaining("invalid123");

    verify(campaignRepository).findById("invalid123");
    verify(campaignRepository, never()).save(any(Campaign.class));
  }

  @Test
  void updateCampaign_WhenActivityLoggingFails_ShouldStillUpdateCampaign() {
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
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();

    Campaign updatedCampaign =
        Campaign.builder()
            .name("Updated Campaign")
            .description("Updated Description")
            .status(Campaign.Status.APPROVED)
            .budget(15000.0)
            .currency("EUR")
            .startDate(LocalDate.now().plusDays(2))
            .endDate(LocalDate.now().plusDays(35))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").name("Test Brand").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .build();
    updatedCampaign.setId("campaign123");
    updatedCampaign.setCreatedAt(LocalDateTime.now());
    updatedCampaign.setUpdatedAt(LocalDateTime.now());

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.findByNameIgnoreCase("Updated Campaign")).thenReturn(Optional.empty());
    when(campaignRepository.save(any(Campaign.class))).thenReturn(updatedCampaign);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenThrow(new RuntimeException("Activity logging failed"));

    // When
    CampaignResponseDTO result = campaignService.updateCampaign("campaign123", updateRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("Updated Campaign");
    assertThat(result.getStatus()).isEqualTo(Campaign.Status.APPROVED);
    assertThat(result.getBudget()).isEqualTo(15000.0);

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).save(any(Campaign.class));
    verify(campaignActivityService).buildUpdateChanges(any(Campaign.class), any(Campaign.class));
    verify(campaignActivityService, never()).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void updateCampaign_WithMediaChannels_ShouldPersistMediaChannels() {
    // Given
    CampaignRequestDTO updateRequest =
        CampaignRequestDTO.builder()
            .name("Test Campaign")
            .description("Updated Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .mediaChannels(
                List.of(Campaign.MediaChannel.DIGITAL_OOH, Campaign.MediaChannel.CLASSIC_OOH))
            .build();

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.findByNameIgnoreCase("Test Campaign"))
        .thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
    when(brandService.getBrandById("brand123")).thenReturn(Optional.empty());
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of());
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    campaignService.updateCampaign("campaign123", updateRequest);

    // Then
    ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
    verify(campaignRepository).save(captor.capture());
    assertThat(captor.getValue().getMediaChannels())
        .containsExactlyInAnyOrder(
            Campaign.MediaChannel.DIGITAL_OOH, Campaign.MediaChannel.CLASSIC_OOH);
  }

  @Test
  void updateCampaign_WithVenueTypes_ShouldPersistVenueTypes() {
    // Given
    CampaignRequestDTO.Targeting targeting = new CampaignRequestDTO.Targeting();
    CampaignRequestDTO.Targeting.VenueTypes venueTypes =
        new CampaignRequestDTO.Targeting.VenueTypes();
    venueTypes.setDigitalOoh(List.of("transit-airports-arrival-hall"));
    venueTypes.setClassicOoh(List.of("outdoor-billboards"));
    targeting.setVenueTypes(venueTypes);

    CampaignRequestDTO updateRequest =
        CampaignRequestDTO.builder()
            .name("Test Campaign")
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

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.findByNameIgnoreCase("Test Campaign"))
        .thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
    when(brandService.getBrandById("brand123")).thenReturn(Optional.empty());
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of());
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    campaignService.updateCampaign("campaign123", updateRequest);

    // Then
    ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
    verify(campaignRepository).save(captor.capture());
    Campaign saved = captor.getValue();
    assertThat(saved.getTargeting()).isNotNull();
    assertThat(saved.getTargeting().getVenueTypes()).isNotNull();
    assertThat(saved.getTargeting().getVenueTypes().getDigitalOoh())
        .containsExactly("transit-airports-arrival-hall");
    assertThat(saved.getTargeting().getVenueTypes().getClassicOoh())
        .containsExactly("outdoor-billboards");
  }

  @Test
  void updateCampaign_WithProgrammaticOnly_ShouldPersistFlag() {
    // Given
    CampaignRequestDTO.Targeting targeting = new CampaignRequestDTO.Targeting();
    targeting.setProgrammaticOnly(true);

    CampaignRequestDTO updateRequest =
        CampaignRequestDTO.builder()
            .name("Test Campaign")
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

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.findByNameIgnoreCase("Test Campaign"))
        .thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
    when(brandService.getBrandById("brand123")).thenReturn(Optional.empty());
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of());
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    campaignService.updateCampaign("campaign123", updateRequest);

    // Then
    ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
    verify(campaignRepository).save(captor.capture());
    Campaign saved = captor.getValue();
    assertThat(saved.getTargeting()).isNotNull();
    assertThat(saved.getTargeting().getProgrammaticOnly()).isTrue();
  }

  @Test
  void updateCampaign_WithInventoryCluster_ShouldPersistInventoryCluster() {
    // Given
    CampaignRequestDTO.Targeting targeting = new CampaignRequestDTO.Targeting();
    targeting.setInventoryCluster(List.of("cluster-A", "cluster-B"));

    CampaignRequestDTO updateRequest =
        CampaignRequestDTO.builder()
            .name("Test Campaign")
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

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.findByNameIgnoreCase("Test Campaign"))
        .thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
    when(brandService.getBrandById("brand123")).thenReturn(Optional.empty());
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of());
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    campaignService.updateCampaign("campaign123", updateRequest);

    // Then
    ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
    verify(campaignRepository).save(captor.capture());
    Campaign saved = captor.getValue();
    assertThat(saved.getTargeting()).isNotNull();
    assertThat(saved.getTargeting().getInventoryCluster())
        .containsExactly("cluster-A", "cluster-B");
  }

  // ========== deleteCampaign Tests ==========

  @Test
  void deleteCampaign_WithDraftStatus_ShouldDeleteCampaign() {
    // Given
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    // When
    campaignService.deleteCampaign("campaign123");

    // Then
    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).delete(testCampaign);
  }

  @Test
  void deleteCampaign_WithNonDraftStatus_ShouldThrowException() {
    // Given
    testCampaign.setStatus(Campaign.Status.APPROVED);
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    // When & Then
    assertThatThrownBy(() -> campaignService.deleteCampaign("campaign123"))
        .isInstanceOf(CampaignInvalidStatusException.class);

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository, never()).delete(any(Campaign.class));
  }

  @Test
  void deleteCampaign_WithInvalidId_ShouldThrowException() {
    // Given
    when(campaignRepository.findById("invalid123")).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> campaignService.deleteCampaign("invalid123"))
        .isInstanceOf(CampaignNotFoundException.class)
        .hasMessageContaining("invalid123");

    verify(campaignRepository).findById("invalid123");
    verify(campaignRepository, never()).delete(any(Campaign.class));
  }

  // ========== getCampaignsWithFilters Tests ==========

  @Test
  void getCampaignsWithFilters_WithValidFilter_ShouldReturnFilteredCampaigns() {
    // Given
    CampaignFilterDTO filter =
        CampaignFilterDTO.builder().nameContains("Test").companyId("company123").build();
    Pageable pageable = PageRequest.of(0, 10);
    testCampaign.getBrand().setName("Test Brand");
    Page<Campaign> campaignPage = new PageImpl<>(List.of(testCampaign), pageable, 1);

    when(campaignRepository.findCampaignsWithFilters(filter, pageable)).thenReturn(campaignPage);
    when(configService.findByCampaignId("campaign123")).thenReturn(Collections.emptyList());
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("user123")).thenReturn(testUserResponseDTO);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", "company123"))
        .thenReturn(null);

    // When
    Page<CampaignFilterResponseDTO> result =
        campaignService.getCampaignsWithFilters(filter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().getFirst().getName()).isEqualTo("Test Campaign");
    assertThat(result.getContent().getFirst().getBrandName()).isEqualTo("Test Brand");

    verify(campaignRepository).findCampaignsWithFilters(filter, pageable);
    verify(configService).findByCampaignId("campaign123");
  }

  @Test
  void convertToCampaignFilterResponseDTO_WithBrandNotFound_ShouldNotSetBrandFields() {
    // Given
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .status(Campaign.Status.DRAFT)
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .budget(10000.0)
            .currency("USD")
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .currentCompanyId("company456")
            .currentCompanyName("Test Company Ltd")
            .build();
    campaign.setId("campaign123");

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("user123")).thenReturn(testUserResponseDTO);
    when(campaignApprovalWorkflowService.isMaintainer(campaign, "company123")).thenReturn(false);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", "company123"))
        .thenReturn(null);
    when(configService.findByCampaignId("campaign123")).thenReturn(Collections.emptyList());

    // When
    CampaignFilterResponseDTO result = campaignService.convertToCampaignFilterResponseDTO(campaign);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getBrandName()).isNull();
    assertThat(result.getCategoryName()).isNull();
  }

  @Test
  void convertToCampaignFilterResponseDTO_ShouldMapPlanNumber() {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .planNumber("202607210001")
            .status(Campaign.Status.DRAFT)
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .budget(10000.0)
            .currency("USD")
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();
    campaign.setId("campaign123");
    campaign.setIsNegotiated(true);

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("user123")).thenReturn(testUserResponseDTO);
    when(campaignApprovalWorkflowService.isMaintainer(campaign, "company123")).thenReturn(false);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", "company123"))
        .thenReturn(null);
    when(configService.findByCampaignId("campaign123")).thenReturn(Collections.emptyList());

    CampaignFilterResponseDTO result = campaignService.convertToCampaignFilterResponseDTO(campaign);

    assertThat(result.getPlanNumber()).isEqualTo("202607210001");
    assertThat(result.getIsNegotiated()).isTrue();
  }

  @Test
  void convertToCampaignFilterResponseDTO_WithoutUser_ShouldNotSetUserName() {
    // Given
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .status(Campaign.Status.DRAFT)
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .budget(10000.0)
            .currency("USD")
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .currentCompanyId("company456")
            .currentCompanyName("Test Company Ltd")
            .build();
    campaign.setId("campaign123");

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("user123")).thenReturn(testUserResponseDTO);
    when(campaignApprovalWorkflowService.isMaintainer(campaign, "company123")).thenReturn(false);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", "company123"))
        .thenReturn(null);
    when(configService.findByCampaignId("campaign123")).thenReturn(Collections.emptyList());

    // When
    CampaignFilterResponseDTO result = campaignService.convertToCampaignFilterResponseDTO(campaign);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getUserName()).isNull();
  }

  @Test
  void convertToCampaignFilterResponseDTO_WithForecastData_ShouldMapForecastFields() {
    // Given
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .status(Campaign.Status.DRAFT)
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .budget(10000.0)
            .currency("USD")
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .currentCompanyId("company456")
            .currentCompanyName("Test Company Ltd")
            .build();
    campaign.setId("campaign123");

    CampaignInventorySchedules schedule = new CampaignInventorySchedules();
    schedule.setInventoryId("inv1");
    schedule.setCampaignId("campaign123");
    schedule.setMediaOwnerId("mediaOwner123");
    schedule.setScheduleIds(new ArrayList<>());

    Inventory inventory = new Inventory();
    inventory.setId("inv1");
    inventory.setName("Test Inventory");
    inventory.setType("DIGITAL");
    inventory.setFormat("LED");
    Inventory.Location location =
        Inventory.Location.builder().city("New York").country("USA").state("NY").build();
    inventory.setLocation(location);
    Inventory.Price price = Inventory.Price.builder().spot(100.0).cpm(10.0).build();
    inventory.setPrices(List.of(price));

    MeasureReachFrequencyResponseDTO measureResponse =
        MeasureReachFrequencyResponseDTO.builder()
            .impressions(1000L)
            .reach(500L)
            .frequency(2.0)
            .status("Ok")
            .build();

    CampaignInventorySchedulesForecastDTO forecastDTO =
        CampaignInventorySchedulesForecastDTO.builder()
            .estimatedAdPlays(100L)
            .totalSot(1000.0)
            .plannedSot(500.0)
            .build();

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("user123")).thenReturn(testUserResponseDTO);
    when(campaignApprovalWorkflowService.isMaintainer(campaign, "company123")).thenReturn(false);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", "company123"))
        .thenReturn(null);
    when(configService.findByCampaignId("campaign123")).thenReturn(List.of(schedule));
    when(inventoryService.getById("inv1")).thenReturn(inventory);
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(measureResponse);
    when(configService.prepareInventoryForecastForCampaignInventorySchedules(any(), any(), any()))
        .thenReturn(forecastDTO);

    // When
    CampaignFilterResponseDTO result = campaignService.convertToCampaignFilterResponseDTO(campaign);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getInventory()).isNotNull();
    assertThat(result.getTotalCost()).isNotNull();
    assertThat(result.getEstimatedImpression()).isNotNull();
    assertThat(result.getEstimatedReach()).isNotNull();
    assertThat(result.getSov()).isNotNull();
    assertThat(result.getTotalSot()).isNotNull();
    assertThat(result.getPlannedSot()).isNotNull();
    assertThat(result.getCurrentCompanyId()).isEqualTo("company456");
    assertThat(result.getCurrentCompanyName()).isEqualTo("Test Company Ltd");

    verify(configService).findByCampaignId("campaign123");
    verify(inventoryService, times(2)).getById("inv1");
  }

  // ========== resolveCampaignStatus Tests ==========

  @Test
  void resolveCampaignStatus_WithNoProposal_ShouldReturnCampaignStatus() {
    // Given - campaign status DRAFT, no proposal
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", "company123"))
        .thenReturn(null);

    // When
    Campaign.Status result =
        campaignService.resolveCampaignStatus(testCampaign, testUserResponseDTO, "company123");

    // Then
    assertThat(result).isEqualTo(Campaign.Status.DRAFT);
  }

  @Test
  void resolveCampaignStatus_WithProposalPending_ShouldReturnReviewing() {
    // Given
    testCampaign.setStatus(Campaign.Status.REVIEWING);
    CampaignProposalStatus proposal = new CampaignProposalStatus();
    proposal.setStatus(CampaignProposalStatus.Status.PENDING);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", "company123"))
        .thenReturn(proposal);

    // When
    Campaign.Status result =
        campaignService.resolveCampaignStatus(testCampaign, testUserResponseDTO, "company123");

    // Then
    assertThat(result).isEqualTo(Campaign.Status.REVIEWING);
  }

  @Test
  void resolveCampaignStatus_WithProposalApproved_ShouldReturnApproved() {
    // Given
    testCampaign.setStatus(Campaign.Status.REVIEWING);
    CampaignProposalStatus proposal = new CampaignProposalStatus();
    proposal.setStatus(CampaignProposalStatus.Status.APPROVED);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", "company123"))
        .thenReturn(proposal);

    // When
    Campaign.Status result =
        campaignService.resolveCampaignStatus(testCampaign, testUserResponseDTO, "company123");

    // Then
    assertThat(result).isEqualTo(Campaign.Status.APPROVED);
  }

  @Test
  void resolveCampaignStatus_WithProposalRejected_ShouldReturnRejected() {
    // Given
    testCampaign.setStatus(Campaign.Status.REVIEWING);
    CampaignProposalStatus proposal = new CampaignProposalStatus();
    proposal.setStatus(CampaignProposalStatus.Status.REJECTED);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", "company123"))
        .thenReturn(proposal);

    // When
    Campaign.Status result =
        campaignService.resolveCampaignStatus(testCampaign, testUserResponseDTO, "company123");

    // Then
    assertThat(result).isEqualTo(Campaign.Status.REJECTED);
  }

  @Test
  void resolveCampaignStatus_WithReviewingAndNotMaintainer_ShouldReturnPlanned() {
    // Given - campaign in REVIEWING, user is not maintainer -> show as PLANNED for media owner
    testCampaign.setStatus(Campaign.Status.REVIEWING);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", "company123"))
        .thenReturn(null);
    when(campaignApprovalWorkflowService.isMaintainer(testCampaign, "company123"))
        .thenReturn(false);

    // When
    Campaign.Status result =
        campaignService.resolveCampaignStatus(testCampaign, testUserResponseDTO, "company123");

    // Then
    assertThat(result).isEqualTo(Campaign.Status.PLANNED);
  }

  @Test
  void resolveCampaignStatus_WithReviewingAndMaintainer_ShouldReturnReviewing() {
    // Given - campaign in REVIEWING, user is maintainer -> keep REVIEWING
    testCampaign.setStatus(Campaign.Status.REVIEWING);
    CampaignProposalStatus proposal = new CampaignProposalStatus();
    proposal.setStatus(CampaignProposalStatus.Status.PENDING);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", "company123"))
        .thenReturn(proposal);
    when(campaignApprovalWorkflowService.isMaintainer(testCampaign, "company123")).thenReturn(true);

    // When
    Campaign.Status result =
        campaignService.resolveCampaignStatus(testCampaign, testUserResponseDTO, "company123");

    // Then
    assertThat(result).isEqualTo(Campaign.Status.REVIEWING);
  }

  @Test
  void resolveCampaignStatus_WhenStatusAlreadyRejected_ShouldNotOverrideWithProposal() {
    // Given - campaign already REJECTED; proposal status should not override
    testCampaign.setStatus(Campaign.Status.REJECTED);
    CampaignProposalStatus proposal = new CampaignProposalStatus();
    proposal.setStatus(CampaignProposalStatus.Status.APPROVED);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", "company123"))
        .thenReturn(proposal);

    // When
    Campaign.Status result =
        campaignService.resolveCampaignStatus(testCampaign, testUserResponseDTO, "company123");

    // Then - remains REJECTED
    assertThat(result).isEqualTo(Campaign.Status.REJECTED);
  }

  @Test
  void resolveCampaignStatus_WithNullUser_ShouldNotApplyMaintainerLogic() {
    // Given - REVIEWING but null user -> maintainer check not applied, no proposal
    testCampaign.setStatus(Campaign.Status.REVIEWING);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            "campaign123", "company123"))
        .thenReturn(null);

    // When - user null so REVIEWING -> PLANNED branch not taken (user != null fails)
    Campaign.Status result =
        campaignService.resolveCampaignStatus(testCampaign, null, "company123");

    // Then - status unchanged
    assertThat(result).isEqualTo(Campaign.Status.REVIEWING);
  }

  // ========== getCampaignStatistics Tests ==========

  @Test
  void getCampaignStatistics_WithValidCompanyId_ShouldReturnStatistics() {
    // Given
    String companyId = "company123";
    CampaignStatistics expectedStats =
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

    when(campaignRepository.getCampaignStatisticsByCompanyId(companyId, null, null, null))
        .thenReturn(expectedStats);

    // When
    CampaignStatistics result = campaignService.getCampaignStatistics(companyId, null, null);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTotalCampaigns()).isEqualTo(10L);
    assertThat(result.getDraftCampaigns()).isEqualTo(3L);

    verify(campaignRepository).getCampaignStatisticsByCompanyId(companyId, null, null, null);
  }

  @Test
  void getCampaignStatistics_ShouldPassCallerDataModeToRepository() {
    // Given: caller is in Test Mode (demo partition)
    String companyId = "company123";
    when(testModeService.getEffectiveDataMode()).thenReturn("demo");
    when(campaignRepository.getCampaignStatisticsByCompanyId(companyId, null, null, "demo"))
        .thenReturn(CampaignStatistics.builder().totalCampaigns(1L).build());

    // When
    CampaignStatistics result = campaignService.getCampaignStatistics(companyId, null, null);

    // Then: dashboard statistics are partitioned by the caller's mode
    assertThat(result.getTotalCampaigns()).isEqualTo(1L);
    verify(campaignRepository).getCampaignStatisticsByCompanyId(companyId, null, null, "demo");
  }

  @Test
  void getCampaignsByCompanyOverlappingDateRange_ShouldPassCallerDataModeToRepository() {
    // Given
    String companyId = "company123";
    when(testModeService.getEffectiveDataMode()).thenReturn("live");
    when(campaignRepository.findCampaignsByCompanyIdOverlappingDateRange(
            companyId, null, null, null, "live"))
        .thenReturn(List.of(testCampaign));

    // When
    List<Campaign> result =
        campaignService.getCampaignsByCompanyOverlappingDateRange(companyId, null, null, null);

    // Then
    assertThat(result).hasSize(1);
    verify(campaignRepository)
        .findCampaignsByCompanyIdOverlappingDateRange(companyId, null, null, null, "live");
  }

  @Test
  void findByIdForCurrentMode_WithCrossModeCampaign_ShouldThrowNotFound() {
    // Given: campaign exists but belongs to the other Test Mode partition
    testCampaign.setId("campaign123");
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(testModeService.matchesCallerMode(testCampaign)).thenReturn(false);

    // When & Then: cross-mode by-ID access behaves as if the record does not exist
    assertThatThrownBy(() -> campaignService.findByIdForCurrentMode("campaign123"))
        .isInstanceOf(CampaignNotFoundException.class);
  }

  // ========== performBulkAction Tests ==========

  @Test
  void performBulkAction_WithDuplicateAction_ShouldDuplicateCampaigns() {
    // Given
    CampaignBulkActionRequestDTO request =
        CampaignBulkActionRequestDTO.builder()
            .campaignIds(List.of("campaign123"))
            .action(CampaignAction.DUPLICATE)
            .build();

    Campaign duplicatedCampaign =
        Campaign.builder()
            .name("Test Campaign Copy")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .build();
    duplicatedCampaign.setId("newCampaign123");

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenReturn(duplicatedCampaign);
    when(sequencerService.extractPrefixFromCampaignName("Test Campaign"))
        .thenReturn("Test_Campaign_");
    when(sequencerService.getSequence("Test_Campaign_")).thenReturn(1L);

    // When
    CampaignBulkActionResponseDTO result =
        campaignService.performBulkAction(request, testUserContext);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTotalProcessed()).isEqualTo(1);
    assertThat(result.getSuccessCount()).isEqualTo(1);
    assertThat(result.getFailureCount()).isEqualTo(0);
    assertThat(result.getSuccessfulCampaignIds()).contains("campaign123");
    assertThat(result.getNewCampaignIds()).hasSize(1);

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).save(any(Campaign.class));
  }

  @Test
  void performBulkAction_WithDuplicateAction_GivesTheCopyItsOwnFreshPlanNumber() {
    testCampaign.setPlanNumber("202601010001"); // original's number, must not be reused

    CampaignBulkActionRequestDTO request =
        CampaignBulkActionRequestDTO.builder()
            .campaignIds(List.of("campaign123"))
            .action(CampaignAction.DUPLICATE)
            .build();

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
    when(sequencerService.getNextSequenceAtomic(anyString())).thenReturn(9L);

    campaignService.performBulkAction(request, testUserContext);

    ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
    verify(campaignRepository).save(captor.capture());
    String expectedDatePrefix =
        LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    assertThat(captor.getValue().getPlanNumber()).isEqualTo(expectedDatePrefix + "0009");
    assertThat(captor.getValue().getPlanNumber()).isNotEqualTo("202601010001");
  }

  // ========== calculateCampaignForecast Tests ==========

  @Test
  void calculateCampaignForecast_ShouldReturnMetrics_WhenConfigsExist() {
    // Ensure campaign is initialized for this test
    testCampaign.setId("campaign123");
    if (testCampaign.getStartDate() == null) {
      testCampaign.setStartDate(LocalDate.now());
    }
    if (testCampaign.getEndDate() == null) {
      testCampaign.setEndDate(LocalDate.now().plusDays(10));
    }
    CampaignInventorySchedules cfg = new CampaignInventorySchedules();
    cfg.setInventoryId("i1");
    cfg.setMediaOwnerId("mediaOwner123");
    cfg.setScheduleIds(List.of("schedule1")); // Add scheduleIds for impressions calculation
    Inventory inv = new Inventory();
    inv.setId("i1");

    Inventory.Location location =
        Inventory.Location.builder().city("New York").country("USA").state("NY").build();

    Inventory.Price price = Inventory.Price.builder().spot(100.0).cpm(10.0).build();

    Inventory inventory = new Inventory();
    inventory.setId("i1");
    inventory.setName("Test Inventory");
    inventory.setType("DIGITAL");
    inventory.setFormat("LED");
    inventory.setVenueType(List.of("Mall"));
    inventory.setLocation(location);
    inventory.setPrices(List.of(price));

    // Create Schedule with impressions
    Schedule schedule = new Schedule();
    schedule.setId("schedule1");
    schedule.setImpressions(1000L);
    schedule.setReach(500L);

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(configService.findByCampaignId("campaign123")).thenReturn(List.of(cfg));
    when(inventoryService.getById("i1")).thenReturn(inventory);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(schedule));
    // Use real util logic (static methods) for duration and request building

    MeasureReachFrequencyResponseDTO influence =
        MeasureReachFrequencyResponseDTO.builder()
            .impressions(1000L)
            .reach(500L)
            .frequency(2.0)
            .status("Ok")
            .build();
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(influence);
    when(configService.prepareInventoryForecastForCampaignInventorySchedules(any(), any(), any()))
        .thenReturn(
            CampaignInventorySchedulesForecastDTO.builder()
                .estimatedAdPlays(100L)
                .totalSot(1000.0)
                .plannedSot(500.0)
                .sov(50.0)
                .build());

    CampaignForecastDTO result = campaignService.calculateCampaignForecast(testCampaign);

    assertThat(result.getEstimatedImpression()).isEqualTo(1000L);
  }

  @Test
  void calculateCampaignForecast_WithMixedClassicAndDigitalInventory_WeightsSovByPlannedSot() {
    // Campaign-level SOV must plannedSot-weight-average each inventory's own (already
    // classification-aware) SOV — not sum totalSot/plannedSot across classifications and reapply
    // a single ratio, which doesn't make sense once digital SOV stops being a time ratio.
    testCampaign.setId("campaign123");
    if (testCampaign.getStartDate() == null) {
      testCampaign.setStartDate(LocalDate.now());
    }
    if (testCampaign.getEndDate() == null) {
      testCampaign.setEndDate(LocalDate.now().plusDays(10));
    }

    CampaignInventorySchedules classicCfg = new CampaignInventorySchedules();
    classicCfg.setInventoryId("classic1");
    classicCfg.setMediaOwnerId("mediaOwner123");
    classicCfg.setScheduleIds(List.of("scheduleClassic"));

    CampaignInventorySchedules digitalCfg = new CampaignInventorySchedules();
    digitalCfg.setInventoryId("digital1");
    digitalCfg.setMediaOwnerId("mediaOwner123");
    digitalCfg.setScheduleIds(List.of("scheduleDigital"));

    Inventory classicInventory = new Inventory();
    classicInventory.setId("classic1");
    classicInventory.setClassification("Classic");

    Inventory digitalInventory = new Inventory();
    digitalInventory.setId("digital1");
    digitalInventory.setClassification("Digital");

    Schedule classicSchedule = new Schedule();
    classicSchedule.setId("scheduleClassic");
    Schedule digitalSchedule = new Schedule();
    digitalSchedule.setId("scheduleDigital");

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(configService.findByCampaignId("campaign123")).thenReturn(List.of(classicCfg, digitalCfg));
    when(inventoryService.getById("classic1")).thenReturn(classicInventory);
    when(inventoryService.getById("digital1")).thenReturn(digitalInventory);
    when(scheduleRepository.findAllById(anyList()))
        .thenReturn(List.of(classicSchedule, digitalSchedule));

    MeasureReachFrequencyResponseDTO influence =
        MeasureReachFrequencyResponseDTO.builder()
            .impressions(1000L)
            .reach(500L)
            .frequency(2.0)
            .status("Ok")
            .build();
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(influence);

    // Classic inventory: 100% SOV, 100 plannedSot. Digital inventory: 25% SOV, 300 plannedSot.
    // Weighted: (100*100 + 25*300) / (100+300) = 43.75
    when(configService.prepareInventoryForecastForCampaignInventorySchedules(
            any(), any(), eq(classicInventory)))
        .thenReturn(
            CampaignInventorySchedulesForecastDTO.builder()
                .estimatedAdPlays(0L)
                .totalSot(100.0)
                .plannedSot(100.0)
                .sov(100.0)
                .build());
    when(configService.prepareInventoryForecastForCampaignInventorySchedules(
            any(), any(), eq(digitalInventory)))
        .thenReturn(
            CampaignInventorySchedulesForecastDTO.builder()
                .estimatedAdPlays(0L)
                .totalSot(300.0)
                .plannedSot(300.0)
                .sov(25.0)
                .build());

    CampaignForecastDTO result = campaignService.calculateCampaignForecast(testCampaign);

    assertThat(result.getSov()).isEqualTo(43.75);
  }

  @Test
  void calculateCampaignForecast_ShouldReturnEmpty_WhenNoConfigs() {
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(configService.findByCampaignId("campaign123")).thenReturn(List.of());

    CampaignForecastDTO result = campaignService.calculateCampaignForecast(testCampaign);

    assertThat(result.getEstimatedImpression()).isEqualTo(0L);
  }

  @Test
  void calculateCampaignForecast_ShouldRecomputeSovSot_WhenStoredSnapshotMissingThem() {
    // PL3-I17: a stored performance snapshot persisted before SOV/SOT existed (or sent by an FE
    // that omitted them) must not be returned verbatim — those fields must be recomputed.
    testCampaign.setId("campaign123");
    if (testCampaign.getStartDate() == null) {
      testCampaign.setStartDate(LocalDate.now());
    }
    if (testCampaign.getEndDate() == null) {
      testCampaign.setEndDate(LocalDate.now().plusDays(10));
    }

    // Stale stored snapshot: has the other metrics but sov/plannedSot/totalSot are null.
    testCampaign.setPerformance(
        CampaignForecastDTO.builder()
            .totalInventories(5)
            .estimatedImpression(1000L)
            .estimatedReach(500L)
            .estimatedFrequency(2.0)
            .estimatedAdPlays(100L)
            .avgCpm(10.0)
            .totalCost(5000.0)
            .build());

    CampaignInventorySchedules cfg = new CampaignInventorySchedules();
    cfg.setInventoryId("i1");
    cfg.setMediaOwnerId("mediaOwner123");
    cfg.setScheduleIds(List.of("schedule1"));

    Inventory inventory = new Inventory();
    inventory.setId("i1");
    inventory.setPrices(List.of(Inventory.Price.builder().spot(100.0).cpm(10.0).build()));

    Schedule schedule = new Schedule();
    schedule.setId("schedule1");
    schedule.setImpressions(1000L);
    schedule.setReach(500L);

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(configService.findByCampaignId("campaign123")).thenReturn(List.of(cfg));
    when(inventoryService.getById("i1")).thenReturn(inventory);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(schedule));
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(
            MeasureReachFrequencyResponseDTO.builder()
                .impressions(1000L)
                .reach(500L)
                .frequency(2.0)
                .status("Ok")
                .build());
    when(configService.prepareInventoryForecastForCampaignInventorySchedules(any(), any(), any()))
        .thenReturn(
            CampaignInventorySchedulesForecastDTO.builder()
                .estimatedAdPlays(100L)
                .totalSot(1000.0)
                .plannedSot(500.0)
                .sov(50.0)
                .build());

    CampaignForecastDTO result = campaignService.calculateCampaignForecast(testCampaign);

    assertThat(result.getSov()).isNotNull();
    assertThat(result.getPlannedSot()).isNotNull();
    assertThat(result.getTotalSot()).isNotNull();
  }

  @Test
  void
      calculateCampaignForecast_WhenCampaignIsNegotiated_ShouldBypassSnapshotFastPathAndRecompute() {
    // Even when the schedule list count matches the stored snapshot (which alone would satisfy
    // the fast path), a negotiated campaign must always recompute rather than trust the snapshot.
    testCampaign.setId("campaign123");
    if (testCampaign.getStartDate() == null) {
      testCampaign.setStartDate(LocalDate.now());
    }
    if (testCampaign.getEndDate() == null) {
      testCampaign.setEndDate(LocalDate.now().plusDays(10));
    }
    testCampaign.setIsNegotiated(true);

    CampaignForecastDTO snapshot =
        CampaignForecastDTO.builder()
            .totalInventories(1)
            .estimatedImpression(99999L)
            .sov(50.0)
            .plannedSot(500.0)
            .totalSot(1000.0)
            .build();
    testCampaign.setPerformance(snapshot);

    CampaignInventorySchedules cfg = new CampaignInventorySchedules();
    cfg.setInventoryId("i1");
    cfg.setMediaOwnerId("mediaOwner123");
    cfg.setScheduleIds(List.of("schedule1"));
    List<CampaignInventorySchedules> schedules = List.of(cfg); // size 1, matches totalInventories

    Inventory inventory = new Inventory();
    inventory.setId("i1");
    inventory.setPrices(List.of(Inventory.Price.builder().spot(100.0).cpm(10.0).build()));

    Schedule schedule = new Schedule();
    schedule.setId("schedule1");
    schedule.setImpressions(1000L);
    schedule.setReach(500L);

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(inventoryService.getById("i1")).thenReturn(inventory);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(schedule));
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(
            MeasureReachFrequencyResponseDTO.builder()
                .impressions(1000L)
                .reach(500L)
                .frequency(2.0)
                .status("Ok")
                .build());
    when(configService.prepareInventoryForecastForCampaignInventorySchedules(any(), any(), any()))
        .thenReturn(
            CampaignInventorySchedulesForecastDTO.builder()
                .estimatedAdPlays(100L)
                .totalSot(1000.0)
                .plannedSot(500.0)
                .sov(50.0)
                .build());

    CampaignForecastDTO result = campaignService.calculateCampaignForecast(testCampaign, schedules);

    // Recomputed from the live schedule (1000L), NOT the stored snapshot's 99999L, despite the
    // matching schedule count.
    assertThat(result).isNotSameAs(snapshot);
    assertThat(result.getEstimatedImpression()).isEqualTo(1000L);
  }

  @Test
  void calculateCampaignForecast_WithMediaOwnerIds_ShouldFilterSchedules() {
    List<String> mediaOwnerIds = List.of("owner-1");
    MediaOwnerFilterRequestDTO request =
        MediaOwnerFilterRequestDTO.builder().mediaOwnerIds(mediaOwnerIds).build();

    when(configService.findByCampaignIdAndMediaOwnerIdIn("campaign123", mediaOwnerIds))
        .thenReturn(List.of());

    CampaignForecastDTO result = campaignService.calculateCampaignForecast(testCampaign, request);

    assertThat(result).isNotNull();
    assertThat(result.getEstimatedImpression()).isEqualTo(0L);
    verify(configService).findByCampaignIdAndMediaOwnerIdIn("campaign123", mediaOwnerIds);
    // The user-scoping schedule-loading path must NOT be used when filtering by mediaOwnerIds.
    verify(userService, never()).getIamUserContext();
    verify(configService, never()).findByCampaignId(anyString());
  }

  @Test
  void calculateCampaignForecast_WithEmptyMediaOwnerIds_ShouldDelegateToSingleArg() {
    MediaOwnerFilterRequestDTO request =
        MediaOwnerFilterRequestDTO.builder().mediaOwnerIds(List.of()).build();

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(configService.findByCampaignId("campaign123")).thenReturn(List.of());

    CampaignForecastDTO result = campaignService.calculateCampaignForecast(testCampaign, request);

    assertThat(result.getEstimatedImpression()).isEqualTo(0L);
    verify(configService).findByCampaignId("campaign123");
    verify(configService, never()).findByCampaignIdAndMediaOwnerIdIn(anyString(), anyList());
  }

  @Test
  void
      calculateCampaignForecast_WithForceRegenerateTrue_ShouldRecompute_EvenWhenSnapshotComplete() {
    // forceRegenerate=true must bypass a complete, count-matching stored snapshot and recompute.
    testCampaign.setId("campaign123");
    if (testCampaign.getStartDate() == null) {
      testCampaign.setStartDate(LocalDate.now());
    }
    if (testCampaign.getEndDate() == null) {
      testCampaign.setEndDate(LocalDate.now().plusDays(10));
    }

    // Complete snapshot that WOULD short-circuit (sov/plannedSot/totalSot present, count matches).
    testCampaign.setPerformance(
        CampaignForecastDTO.builder()
            .totalInventories(1)
            .estimatedImpression(99999L)
            .sov(50.0)
            .plannedSot(500.0)
            .totalSot(1000.0)
            .build());

    CampaignInventorySchedules cfg = new CampaignInventorySchedules();
    cfg.setInventoryId("i1");
    cfg.setMediaOwnerId("mediaOwner123");
    cfg.setScheduleIds(List.of("schedule1"));

    Inventory inventory = new Inventory();
    inventory.setId("i1");
    inventory.setPrices(List.of(Inventory.Price.builder().spot(100.0).cpm(10.0).build()));

    Schedule schedule = new Schedule();
    schedule.setId("schedule1");
    schedule.setImpressions(1000L);
    schedule.setReach(500L);

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(configService.findByCampaignId("campaign123")).thenReturn(List.of(cfg));
    when(inventoryService.getById("i1")).thenReturn(inventory);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(schedule));
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(
            MeasureReachFrequencyResponseDTO.builder()
                .impressions(1000L)
                .reach(500L)
                .frequency(2.0)
                .status("Ok")
                .build());
    when(configService.prepareInventoryForecastForCampaignInventorySchedules(any(), any(), any()))
        .thenReturn(
            CampaignInventorySchedulesForecastDTO.builder()
                .estimatedAdPlays(100L)
                .totalSot(1000.0)
                .plannedSot(500.0)
                .sov(50.0)
                .build());

    CampaignForecastDTO result = campaignService.calculateCampaignForecast(testCampaign, true);

    // Recomputed value from the schedule, NOT the stale snapshot's 99999.
    assertThat(result.getEstimatedImpression()).isEqualTo(1000L);
  }

  @Test
  void calculateCampaignForecast_WithForceRegenerateFalse_ShouldReturnStoredSnapshot() {
    // forceRegenerate=false keeps the existing behavior: a complete, count-matching snapshot is
    // returned verbatim without recomputing. The count is validated via a cheap countByCampaignId
    // query, so the full schedule list is never loaded on this happy path.
    testCampaign.setId("campaign123");

    CampaignForecastDTO snapshot =
        CampaignForecastDTO.builder()
            .totalInventories(1)
            .estimatedImpression(99999L)
            .sov(50.0)
            .plannedSot(500.0)
            .totalSot(1000.0)
            .build();
    testCampaign.setPerformance(snapshot);

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(configService.countByCampaignId("campaign123")).thenReturn(1L);

    CampaignForecastDTO result = campaignService.calculateCampaignForecast(testCampaign, false);

    // Stored snapshot returned verbatim; no recompute and no full-list load happened.
    assertThat(result).isSameAs(snapshot);
    assertThat(result.getEstimatedImpression()).isEqualTo(99999L);
    verify(configService).countByCampaignId("campaign123");
    verify(configService, never()).findByCampaignId(anyString());
    verify(configService, never())
        .prepareInventoryForecastForCampaignInventorySchedules(any(), any(), any());
  }

  @Test
  void calculateCampaignForecast_WhenCountMismatchesSnapshot_ShouldRecomputeFromFullList() {
    // A complete snapshot whose stored totalInventories no longer matches the live count must be
    // recomputed: the fast path detects the mismatch via countByCampaignId and falls through to
    // load the full schedule list.
    testCampaign.setId("campaign123");
    if (testCampaign.getStartDate() == null) {
      testCampaign.setStartDate(LocalDate.now());
    }
    if (testCampaign.getEndDate() == null) {
      testCampaign.setEndDate(LocalDate.now().plusDays(10));
    }

    // Complete snapshot claims 5 inventories, but the live count is 1 -> stale.
    testCampaign.setPerformance(
        CampaignForecastDTO.builder()
            .totalInventories(5)
            .estimatedImpression(99999L)
            .sov(50.0)
            .plannedSot(500.0)
            .totalSot(1000.0)
            .build());

    CampaignInventorySchedules cfg = new CampaignInventorySchedules();
    cfg.setInventoryId("i1");
    cfg.setMediaOwnerId("mediaOwner123");
    cfg.setScheduleIds(List.of("schedule1"));

    Inventory inventory = new Inventory();
    inventory.setId("i1");
    inventory.setPrices(List.of(Inventory.Price.builder().spot(100.0).cpm(10.0).build()));

    Schedule schedule = new Schedule();
    schedule.setId("schedule1");
    schedule.setImpressions(1000L);
    schedule.setReach(500L);

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(configService.countByCampaignId("campaign123")).thenReturn(1L);
    when(configService.findByCampaignId("campaign123")).thenReturn(List.of(cfg));
    when(inventoryService.getById("i1")).thenReturn(inventory);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(schedule));
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(
            MeasureReachFrequencyResponseDTO.builder()
                .impressions(1000L)
                .reach(500L)
                .frequency(2.0)
                .status("Ok")
                .build());
    when(configService.prepareInventoryForecastForCampaignInventorySchedules(any(), any(), any()))
        .thenReturn(
            CampaignInventorySchedulesForecastDTO.builder()
                .estimatedAdPlays(100L)
                .totalSot(1000.0)
                .plannedSot(500.0)
                .sov(50.0)
                .build());

    CampaignForecastDTO result = campaignService.calculateCampaignForecast(testCampaign, false);

    // Recomputed from the live schedule (1000L), NOT the stale snapshot's 99999L.
    assertThat(result.getEstimatedImpression()).isEqualTo(1000L);
    verify(configService).countByCampaignId("campaign123");
    verify(configService).findByCampaignId("campaign123");
  }

  @Test
  void calculateCampaignForecast_WhenCampaignIsNegotiated_ShouldBypassCountFastPathAndRecompute() {
    // Even when the stored count matches the live count (which alone would satisfy the fast
    // path), a negotiated campaign must always recompute rather than trust the snapshot.
    testCampaign.setId("campaign123");
    if (testCampaign.getStartDate() == null) {
      testCampaign.setStartDate(LocalDate.now());
    }
    if (testCampaign.getEndDate() == null) {
      testCampaign.setEndDate(LocalDate.now().plusDays(10));
    }
    testCampaign.setIsNegotiated(true);

    // Complete snapshot with a count that matches the live count (1) -> would fast-path if not
    // negotiated.
    testCampaign.setPerformance(
        CampaignForecastDTO.builder()
            .totalInventories(1)
            .estimatedImpression(99999L)
            .sov(50.0)
            .plannedSot(500.0)
            .totalSot(1000.0)
            .build());

    CampaignInventorySchedules cfg = new CampaignInventorySchedules();
    cfg.setInventoryId("i1");
    cfg.setMediaOwnerId("mediaOwner123");
    cfg.setScheduleIds(List.of("schedule1"));

    Inventory inventory = new Inventory();
    inventory.setId("i1");
    inventory.setPrices(List.of(Inventory.Price.builder().spot(100.0).cpm(10.0).build()));

    Schedule schedule = new Schedule();
    schedule.setId("schedule1");
    schedule.setImpressions(1000L);
    schedule.setReach(500L);

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(configService.findByCampaignId("campaign123")).thenReturn(List.of(cfg));
    when(inventoryService.getById("i1")).thenReturn(inventory);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(schedule));
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(
            MeasureReachFrequencyResponseDTO.builder()
                .impressions(1000L)
                .reach(500L)
                .frequency(2.0)
                .status("Ok")
                .build());
    when(configService.prepareInventoryForecastForCampaignInventorySchedules(any(), any(), any()))
        .thenReturn(
            CampaignInventorySchedulesForecastDTO.builder()
                .estimatedAdPlays(100L)
                .totalSot(1000.0)
                .plannedSot(500.0)
                .sov(50.0)
                .build());

    CampaignForecastDTO result = campaignService.calculateCampaignForecast(testCampaign, false);

    // Recomputed from the live schedule (1000L), NOT the stored snapshot's 99999L. The negotiated
    // check short-circuits the whole fast-path block, so the count is never even checked.
    assertThat(result.getEstimatedImpression()).isEqualTo(1000L);
    verify(configService, never()).countByCampaignId(anyString());
    verify(configService).findByCampaignId("campaign123");
  }

  @Test
  void calculateCampaignForecast_WhenMediaOwner_ShouldSkipCountFastPathAndRecompute() {
    // A media owner (a company granted access to a campaign it does not own) must never receive the
    // owner's stored snapshot: the count fast path is skipped and the forecast is recomputed from
    // the media owner's own schedules.
    testCampaign.setId("campaign123");
    testCampaign.setCompanyId("owner-co");
    testCampaign.setCompanyAccess(List.of("company123"));
    if (testCampaign.getStartDate() == null) {
      testCampaign.setStartDate(LocalDate.now());
    }
    if (testCampaign.getEndDate() == null) {
      testCampaign.setEndDate(LocalDate.now().plusDays(10));
    }

    testCampaign.setPerformance(
        CampaignForecastDTO.builder()
            .totalInventories(1)
            .estimatedImpression(99999L)
            .sov(50.0)
            .plannedSot(500.0)
            .totalSot(1000.0)
            .build());

    CampaignInventorySchedules cfg = new CampaignInventorySchedules();
    cfg.setInventoryId("i1");
    cfg.setMediaOwnerId("company123");
    cfg.setScheduleIds(List.of("schedule1"));

    Inventory inventory = new Inventory();
    inventory.setId("i1");
    inventory.setPrices(List.of(Inventory.Price.builder().spot(100.0).cpm(10.0).build()));

    Schedule schedule = new Schedule();
    schedule.setId("schedule1");
    schedule.setImpressions(1000L);
    schedule.setReach(500L);

    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(configService.findByCampaignIdAndMediaOwnerId("campaign123", "company123"))
        .thenReturn(List.of(cfg));
    when(inventoryService.getById("i1")).thenReturn(inventory);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(schedule));
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(
            MeasureReachFrequencyResponseDTO.builder()
                .impressions(1000L)
                .reach(500L)
                .frequency(2.0)
                .status("Ok")
                .build());
    when(configService.prepareInventoryForecastForCampaignInventorySchedules(any(), any(), any()))
        .thenReturn(
            CampaignInventorySchedulesForecastDTO.builder()
                .estimatedAdPlays(100L)
                .totalSot(1000.0)
                .plannedSot(500.0)
                .sov(50.0)
                .build());

    CampaignForecastDTO result = campaignService.calculateCampaignForecast(testCampaign, false);

    // Recomputed for the media owner (1000L), NOT the owner's stored snapshot (99999L).
    assertThat(result.getEstimatedImpression()).isEqualTo(1000L);
    verify(configService, never()).countByCampaignId(anyString());
    verify(configService).findByCampaignIdAndMediaOwnerId("campaign123", "company123");
  }

  @Test
  void calculateCampaignForecast_WhenViewerFromOtherCompany_ShouldCountByMediaOwnerOnFastPath() {
    // A non-media-owner viewer whose company differs from the campaign owner validates the snapshot
    // via countByCampaignIdAndMediaOwnerId (mirroring the media-owner-scoped list loader) instead
    // of
    // counting the whole campaign.
    testCampaign.setId("campaign123");
    testCampaign.setCompanyId("company123");
    testCampaign.setCompanyAccess(null); // not a media owner

    CampaignForecastDTO snapshot =
        CampaignForecastDTO.builder()
            .totalInventories(2)
            .estimatedImpression(99999L)
            .sov(50.0)
            .plannedSot(500.0)
            .totalSot(1000.0)
            .build();
    testCampaign.setPerformance(snapshot);

    IamUserContext viewerContext =
        IamUserContext.builder().id("u9").companyId("viewer-co").locale(Locale.ENGLISH).build();

    when(userService.getIamUserContext()).thenReturn(viewerContext);
    when(userService.getPrimaryCompanyId()).thenReturn("viewer-co");
    when(configService.countByCampaignIdAndMediaOwnerId("campaign123", "viewer-co")).thenReturn(2L);

    CampaignForecastDTO result = campaignService.calculateCampaignForecast(testCampaign, false);

    assertThat(result).isSameAs(snapshot);
    verify(configService).countByCampaignIdAndMediaOwnerId("campaign123", "viewer-co");
    verify(configService, never()).countByCampaignId(anyString());
    verify(configService, never()).findByCampaignId(anyString());
  }

  @Test
  void performBulkAction_WithArchiveAction_ShouldChangeCampaignsStatus() {
    // Given
    CampaignBulkActionRequestDTO request =
        CampaignBulkActionRequestDTO.builder()
            .campaignIds(List.of("campaign123"))
            .action(CampaignAction.ARCHIVE)
            .build();

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);

    // When
    CampaignBulkActionResponseDTO result =
        campaignService.performBulkAction(request, testUserContext);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTotalProcessed()).isEqualTo(1);
    assertThat(result.getSuccessCount()).isEqualTo(1);
    assertThat(result.getFailureCount()).isEqualTo(0);
    assertThat(result.getSuccessfulCampaignIds()).contains("campaign123");

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).save(any(Campaign.class));
  }

  @Test
  void performBulkAction_WithDeleteAction_ShouldDeleteCampaigns() {
    // Given
    CampaignBulkActionRequestDTO request =
        CampaignBulkActionRequestDTO.builder()
            .campaignIds(List.of("campaign123"))
            .action(CampaignAction.DELETE)
            .build();

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    // When
    CampaignBulkActionResponseDTO result =
        campaignService.performBulkAction(request, testUserContext);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTotalProcessed()).isEqualTo(1);
    assertThat(result.getSuccessCount()).isEqualTo(1);
    assertThat(result.getFailureCount()).isEqualTo(0);
    assertThat(result.getSuccessfulCampaignIds()).contains("campaign123");

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).delete(testCampaign);
  }

  @Test
  void performBulkAction_WithMixedResults_ShouldReturnPartialSuccess() {
    // Given
    CampaignBulkActionRequestDTO request =
        CampaignBulkActionRequestDTO.builder()
            .campaignIds(List.of("campaign123", "invalid123"))
            .action(CampaignAction.ARCHIVE)
            .build();

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.findById("invalid123")).thenReturn(Optional.empty());
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    when(messageService.getMessage(anyString(), any(Locale.class), any()))
        .thenReturn("Campaign not found");

    // When
    CampaignBulkActionResponseDTO result =
        campaignService.performBulkAction(request, testUserContext);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTotalProcessed()).isEqualTo(2);
    assertThat(result.getSuccessCount()).isEqualTo(1);
    assertThat(result.getFailureCount()).isEqualTo(1);
    assertThat(result.getSuccessfulCampaignIds()).contains("campaign123");
    assertThat(result.getFailedCampaignIds()).contains("invalid123");
    assertThat(result.getErrorMessages()).hasSize(1);

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).findById("invalid123");
    verify(campaignRepository).save(any(Campaign.class));
  }

  // ========== autosaveCampaign Tests ==========

  @Test
  void autosaveCampaign_WithDraftStatus_ShouldAutosaveCampaign() {
    // Given
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder()
            .name("Updated Name")
            .description("Updated Description")
            .budget(15000.0)
            .build();

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of("name", "Updated Name", "budget", 15000.0));
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    CampaignResponseDTO result = campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");
    assertThat(result.getBrand().getName()).isEqualTo("Test Brand");
    assertThat(result.getStatus()).isEqualTo(Campaign.Status.DRAFT);

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).save(any(Campaign.class));
    verify(campaignActivityService).buildUpdateChanges(any(Campaign.class), any(Campaign.class));
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void autosaveCampaign_WithBrandAndAgency_ShouldReturnAutosavedCampaignWithNames() {
    // Given
    testCampaign.setAgency(
        Campaign.CampaignAgency.builder().id("agency123").name("Test Agency").build());
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder()
            .name("Updated Name")
            .description("Updated Description")
            .budget(15000.0)
            .build();

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");
    String agencyName = "Test Agency";

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of("name", "Updated Name"));
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    CampaignResponseDTO result = campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");
    assertThat(result.getBrand().getName()).isEqualTo("Test Brand");
    assertThat(result.getAgency().getName()).isEqualTo(agencyName);
    assertThat(result.getStatus()).isEqualTo(Campaign.Status.DRAFT);

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).save(any(Campaign.class));
    verify(campaignActivityService).buildUpdateChanges(any(Campaign.class), any(Campaign.class));
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void autosaveCampaign_WithNoChanges_ShouldNotLogActivity() {
    // Given
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder().build(); // No changes

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Collections.emptyMap()); // No changes

    // When
    CampaignResponseDTO result = campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).save(any(Campaign.class));
    verify(campaignActivityService).buildUpdateChanges(any(Campaign.class), any(Campaign.class));
    verify(campaignActivityService, never()).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void autosaveCampaign_WithNonDraftStatus_ShouldThrowException() {
    // Given
    testCampaign.setStatus(Campaign.Status.APPROVED);
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder().name("Updated Name").build();

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    // When & Then
    assertThatThrownBy(() -> campaignService.autosaveCampaign("campaign123", autosaveRequest))
        .isInstanceOf(CampaignInvalidStatusException.class);

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository, never()).save(any(Campaign.class));
  }

  @Test
  void autosaveCampaign_WithInvalidId_ShouldThrowException() {
    // Given
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder().name("Updated Name").build();

    when(campaignRepository.findById("invalid123")).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> campaignService.autosaveCampaign("invalid123", autosaveRequest))
        .isInstanceOf(CampaignNotFoundException.class)
        .hasMessageContaining("invalid123");

    verify(campaignRepository).findById("invalid123");
    verify(campaignRepository, never()).save(any(Campaign.class));
  }

  @Test
  void autosaveCampaign_WithCountryChange_ShouldRemoveAllInventories() {
    // Given
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder()
            .name("Updated Name")
            .countryId("UK") // Different from existing "US"
            .build();

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of("name", "Updated Name", "country", "UK"));
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));
    doNothing().when(configService).removeAllInventoriesForCampaign("campaign123");

    // When
    CampaignResponseDTO result = campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");
    assertThat(result.getBrand().getName()).isEqualTo("Test Brand");

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).save(any(Campaign.class));
    verify(configService).removeAllInventoriesForCampaign("campaign123");
    verify(campaignActivityService).buildUpdateChanges(any(Campaign.class), any(Campaign.class));
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void autosaveCampaign_WithSameCountry_ShouldNotRemoveInventories() {
    // Given
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder()
            .name("Updated Name")
            .countryId("US") // Same as existing
            .build();

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of("name", "Updated Name"));
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    CampaignResponseDTO result = campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");
    assertThat(result.getBrand().getName()).isEqualTo("Test Brand");

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).save(any(Campaign.class));
    verify(configService, never()).removeAllInventoriesForCampaign(anyString());
    verify(campaignActivityService).buildUpdateChanges(any(Campaign.class), any(Campaign.class));
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void autosaveCampaign_WithNullCountry_ShouldNotRemoveInventories() {
    // Given
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder().name("Updated Name").build(); // No countryId

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of("name", "Updated Name"));
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    CampaignResponseDTO result = campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");
    assertThat(result.getBrand().getName()).isEqualTo("Test Brand");

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).save(any(Campaign.class));
    verify(configService, never()).removeAllInventoriesForCampaign(anyString());
    verify(campaignActivityService).buildUpdateChanges(any(Campaign.class), any(Campaign.class));
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void autosaveCampaign_WhenActivityLoggingFails_ShouldStillAutosaveCampaign() {
    // Given
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder()
            .name("Updated Name")
            .description("Updated Description")
            .budget(15000.0)
            .build();

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenThrow(new RuntimeException("Activity logging failed"));

    // When
    CampaignResponseDTO result = campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");
    assertThat(result.getBrand().getName()).isEqualTo("Test Brand");
    assertThat(result.getStatus()).isEqualTo(Campaign.Status.DRAFT);

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).save(any(Campaign.class));
    verify(campaignActivityService).buildUpdateChanges(any(Campaign.class), any(Campaign.class));
    verify(campaignActivityService, never()).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void autosaveCampaign_WithStartDateChange_ShouldRecreateSchedules() {
    // Given
    LocalDate newStartDate = LocalDate.now().plusDays(5);
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder()
            .name("Updated Name")
            .startDate(newStartDate) // Different from existing
            .build();

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setInventoryId("inventory1");
    CampaignInventorySchedules schedule2 = new CampaignInventorySchedules();
    schedule2.setInventoryId("inventory2");

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of("name", "Updated Name", "startDate", newStartDate));
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));
    when(configService.findByCampaignId("campaign123")).thenReturn(List.of(schedule1, schedule2));
    doNothing().when(configService).removeAllInventoriesForCampaign("campaign123");
    when(configService.bulkSelectInventoriesByIds(eq("campaign123"), anyList())).thenReturn(2);

    // When
    CampaignResponseDTO result = campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");
    assertThat(result.getBrand().getName()).isEqualTo("Test Brand");

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).save(any(Campaign.class));
    verify(configService).findByCampaignId("campaign123");
    verify(configService).removeAllInventoriesForCampaign("campaign123");
    verify(configService)
        .bulkSelectInventoriesByIds(
            eq("campaign123"),
            argThat(
                list ->
                    list != null
                        && list.size() == 2
                        && list.contains("inventory1")
                        && list.contains("inventory2")));
    verify(campaignActivityService).buildUpdateChanges(any(Campaign.class), any(Campaign.class));
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void autosaveCampaign_WithEndDateChange_ShouldRecreateSchedules() {
    // Given
    LocalDate newEndDate = LocalDate.now().plusDays(45);
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder()
            .name("Updated Name")
            .endDate(newEndDate) // Different from existing
            .build();

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    CampaignInventorySchedules schedule = new CampaignInventorySchedules();
    schedule.setInventoryId("inventory1");

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of("name", "Updated Name", "endDate", newEndDate));
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));
    when(configService.findByCampaignId("campaign123")).thenReturn(List.of(schedule));
    doNothing().when(configService).removeAllInventoriesForCampaign("campaign123");
    when(configService.bulkSelectInventoriesByIds(eq("campaign123"), anyList())).thenReturn(1);

    // When
    CampaignResponseDTO result = campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");
    assertThat(result.getBrand().getName()).isEqualTo("Test Brand");

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).save(any(Campaign.class));
    verify(configService).findByCampaignId("campaign123");
    verify(configService).removeAllInventoriesForCampaign("campaign123");
    verify(configService)
        .bulkSelectInventoriesByIds(
            eq("campaign123"),
            argThat(list -> list != null && list.size() == 1 && list.contains("inventory1")));
    verify(campaignActivityService).buildUpdateChanges(any(Campaign.class), any(Campaign.class));
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void autosaveCampaign_WithDateChangeButNoExistingSchedules_ShouldSkipRecreation() {
    // Given
    LocalDate newStartDate = LocalDate.now().plusDays(5);
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder().name("Updated Name").startDate(newStartDate).build();

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of("name", "Updated Name", "startDate", newStartDate));
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));
    when(configService.findByCampaignId("campaign123")).thenReturn(Collections.emptyList());

    // When
    CampaignResponseDTO result = campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");
    assertThat(result.getBrand().getName()).isEqualTo("Test Brand");

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).save(any(Campaign.class));
    verify(configService).findByCampaignId("campaign123");
    verify(configService, never()).removeAllInventoriesForCampaign(anyString());
    verify(configService, never()).bulkSelectInventoriesByIds(anyString(), anyList());
    verify(campaignActivityService).buildUpdateChanges(any(Campaign.class), any(Campaign.class));
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void autosaveCampaign_WithBothCountryAndDateChange_ShouldRemoveInventoriesAndRecreateSchedules() {
    // Given
    LocalDate newStartDate = LocalDate.now().plusDays(5);
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder()
            .name("Updated Name")
            .countryId("UK") // Different from existing "US"
            .startDate(newStartDate) // Different from existing
            .build();

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of("name", "Updated Name", "country", "UK", "startDate", newStartDate));
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));
    doNothing().when(configService).removeAllInventoriesForCampaign("campaign123");
    // When country changes, schedules are removed, so date change won't find any schedules
    when(configService.findByCampaignId("campaign123")).thenReturn(Collections.emptyList());

    // When
    CampaignResponseDTO result = campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");
    assertThat(result.getBrand().getName()).isEqualTo("Test Brand");

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).save(any(Campaign.class));
    // Country change removes inventories (called first)
    verify(configService, times(1)).removeAllInventoriesForCampaign("campaign123");
    // Date change tries to recreate but finds no schedules
    verify(configService, never()).bulkSelectInventoriesByIds(anyString(), anyList());
    verify(campaignActivityService).buildUpdateChanges(any(Campaign.class), any(Campaign.class));
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void autosaveCampaign_WhenLogActivityThrowsException_ShouldStillAutosaveCampaign() {
    // Given
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder()
            .name("Updated Name")
            .description("Updated Description")
            .budget(15000.0)
            .build();

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of("name", "Updated Name", "budget", 15000.0));
    doThrow(new RuntimeException("Log activity failed"))
        .when(campaignActivityService)
        .logActivity(anyString(), any(), any(Map.class));

    // When
    CampaignResponseDTO result = campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");
    assertThat(result.getBrand().getName()).isEqualTo("Test Brand");
    assertThat(result.getStatus()).isEqualTo(Campaign.Status.DRAFT);

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).save(any(Campaign.class));
    verify(campaignActivityService).buildUpdateChanges(any(Campaign.class), any(Campaign.class));
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void autosaveCampaign_WithComprehensiveFieldUpdates_ShouldUpdateAllFields() {
    // Given
    Map<String, Double> budgetAllocation = Map.of("DIGITAL", 60.0, "TV", 40.0);
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder()
            .name("Updated Name")
            .description("Updated Description")
            .budget(20000.0)
            .currency("EUR")
            .brand(Campaign.CampaignBrand.builder().id("brand456").name("Updated Brand").build())
            .clientType(Campaign.ClientType.AGENCY)
            .agency(
                Campaign.CampaignAgency.builder().id("agency789").name("Updated Agency").build())
            .budgetAllocation(budgetAllocation)
            .performance(buildTestForecast())
            .build();

    String agencyName = "Updated Agency";

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(
            Map.of(
                "name",
                "Updated Name",
                "budget",
                20000.0,
                "currency",
                "EUR",
                "brandId",
                "brand456",
                "clientType",
                Campaign.ClientType.AGENCY,
                "agencyId",
                "agency789"));
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    CampaignResponseDTO result = campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");
    assertThat(result.getBrand().getName()).isEqualTo("Updated Brand");
    assertThat(result.getAgency().getName()).isEqualTo(agencyName);
    assertThat(result.getStatus()).isEqualTo(Campaign.Status.DRAFT);
    assertThat(result.getPerformance()).isEqualTo(buildTestForecast());

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).save(any(Campaign.class));
    verify(campaignActivityService).buildUpdateChanges(any(Campaign.class), any(Campaign.class));
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void autosaveCampaign_WithSameDates_ShouldNotRecreateSchedules() {
    // Given
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder()
            .name("Updated Name")
            .startDate(testCampaign.getStartDate()) // Same as existing
            .endDate(testCampaign.getEndDate()) // Same as existing
            .build();

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of("name", "Updated Name"));
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    CampaignResponseDTO result = campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("campaign123");
    assertThat(result.getBrand().getName()).isEqualTo("Test Brand");

    verify(campaignRepository, atLeastOnce()).findById("campaign123");
    verify(campaignRepository).save(any(Campaign.class));
    verify(configService, never()).findByCampaignId(anyString());
    verify(configService, never()).removeAllInventoriesForCampaign(anyString());
    verify(configService, never()).bulkSelectInventoriesByIds(anyString(), anyList());
    verify(campaignActivityService).buildUpdateChanges(any(Campaign.class), any(Campaign.class));
    verify(campaignActivityService, times(1)).logActivity(anyString(), any(), any(Map.class));
  }

  @Test
  void autosaveCampaign_WithMediaChannels_ShouldPersistMediaChannels() {
    // Given
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder()
            .mediaChannels(List.of(Campaign.MediaChannel.DIGITAL_OOH))
            .build();

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
    when(brandService.getBrandById("brand123")).thenReturn(Optional.empty());
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of());
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
    verify(campaignRepository).save(captor.capture());
    assertThat(captor.getValue().getMediaChannels())
        .containsExactly(Campaign.MediaChannel.DIGITAL_OOH);
  }

  private CampaignForecastDTO buildTestForecast() {
    return CampaignForecastDTO.builder()
        .totalInventories(5)
        .estimatedImpression(1000000L)
        .estimatedReach(50000L)
        .estimatedFrequency(2.5)
        .estimatedAdPlays(5000L)
        .sov(15.5)
        .avgCpm(3.2)
        .avgECpm(4.1)
        .totalCost(10000.50)
        .plannedSot(5000.0)
        .totalSot(10000.0)
        .build();
  }

  @Test
  void autosaveCampaign_WithPerformance_ShouldPersistPerformance() {
    // Given
    CampaignForecastDTO performance = buildTestForecast();
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder().performance(performance).build();

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
    when(brandService.getBrandById("brand123")).thenReturn(Optional.empty());
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of());
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
    verify(campaignRepository).save(captor.capture());
    assertThat(captor.getValue().getPerformance()).isEqualTo(performance);
    assertThat(captor.getValue().getStatus()).isEqualTo(Campaign.Status.DRAFT);
  }

  @Test
  void autosaveCampaign_WithoutPerformance_ShouldPreserveExistingPerformance() {
    // Given
    CampaignForecastDTO existingPerformance = buildTestForecast();
    testCampaign.setPerformance(existingPerformance);
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder().name("Updated Name").build();

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.findByNameIgnoreCase("Updated Name")).thenReturn(Optional.empty());
    when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
    when(brandService.getBrandById("brand123")).thenReturn(Optional.empty());
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of());
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
    verify(campaignRepository).save(captor.capture());
    assertThat(captor.getValue().getPerformance()).isEqualTo(existingPerformance);
    assertThat(captor.getValue().getStatus()).isEqualTo(Campaign.Status.DRAFT);
  }

  @Test
  void autosaveCampaign_WithPerformance_ShouldReturnPerformanceInResponse() {
    // Given
    CampaignForecastDTO performance = buildTestForecast();
    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder().performance(performance).build();

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
    when(brandService.getBrandById("brand123")).thenReturn(Optional.empty());
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of());
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    CampaignResponseDTO result = campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getPerformance()).isEqualTo(performance);
    assertThat(result.getStatus()).isEqualTo(Campaign.Status.DRAFT);
  }

  @Test
  void autosaveCampaign_WithVenueTypes_ShouldPersistVenueTypes() {
    // Given
    CampaignAutosaveRequestDTO.Targeting targeting = new CampaignAutosaveRequestDTO.Targeting();
    CampaignAutosaveRequestDTO.Targeting.VenueTypes venueTypes =
        new CampaignAutosaveRequestDTO.Targeting.VenueTypes();
    venueTypes.setDigitalOoh(List.of("health-beauty-gyms"));
    venueTypes.setClassicOoh(List.of("outdoor-billboards"));
    targeting.setVenueTypes(venueTypes);

    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder().targeting(targeting).build();

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
    when(brandService.getBrandById("brand123")).thenReturn(Optional.empty());
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of());
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
    verify(campaignRepository).save(captor.capture());
    Campaign saved = captor.getValue();
    assertThat(saved.getTargeting()).isNotNull();
    assertThat(saved.getTargeting().getVenueTypes()).isNotNull();
    assertThat(saved.getTargeting().getVenueTypes().getDigitalOoh())
        .containsExactly("health-beauty-gyms");
    assertThat(saved.getTargeting().getVenueTypes().getClassicOoh())
        .containsExactly("outdoor-billboards");
  }

  @Test
  void autosaveCampaign_WithProgrammaticOnly_ShouldPersistFlag() {
    // Given
    CampaignAutosaveRequestDTO.Targeting targeting = new CampaignAutosaveRequestDTO.Targeting();
    targeting.setProgrammaticOnly(true);

    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder().targeting(targeting).build();

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
    when(brandService.getBrandById("brand123")).thenReturn(Optional.empty());
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of());
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
    verify(campaignRepository).save(captor.capture());
    Campaign saved = captor.getValue();
    assertThat(saved.getTargeting()).isNotNull();
    assertThat(saved.getTargeting().getProgrammaticOnly()).isTrue();
  }

  @Test
  void autosaveCampaign_WithInventoryCluster_ShouldPersistInventoryCluster() {
    // Given
    CampaignAutosaveRequestDTO.Targeting targeting = new CampaignAutosaveRequestDTO.Targeting();
    targeting.setInventoryCluster(List.of("cluster-A", "cluster-B"));

    CampaignAutosaveRequestDTO autosaveRequest =
        CampaignAutosaveRequestDTO.builder().targeting(targeting).build();

    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
    when(brandService.getBrandById("brand123")).thenReturn(Optional.empty());
    when(agencyService.getNameById(anyString())).thenReturn(null);
    when(campaignActivityService.buildUpdateChanges(any(Campaign.class), any(Campaign.class)))
        .thenReturn(Map.of());
    doNothing().when(campaignActivityService).logActivity(anyString(), any(), any(Map.class));

    // When
    campaignService.autosaveCampaign("campaign123", autosaveRequest);

    // Then
    ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
    verify(campaignRepository).save(captor.capture());
    Campaign saved = captor.getValue();
    assertThat(saved.getTargeting()).isNotNull();
    assertThat(saved.getTargeting().getInventoryCluster())
        .containsExactly("cluster-A", "cluster-B");
  }

  // ========== getCampaignMediaPlanDetails Tests ==========

  @Test
  void getCampaignMediaPlanDetails_WithValidCampaign_ShouldReturnCompleteMediaPlan() {
    // Given
    String campaignId = "campaign123";
    LocalDate startDate = LocalDate.now().plusDays(1);
    LocalDate endDate = LocalDate.now().plusDays(30);

    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(startDate)
            .endDate(endDate)
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").name("Test Brand").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .targeting(
                Campaign.Targeting.builder()
                    .demographics(
                        Map.of(
                            "age", List.of("18-24", "25-34"),
                            "interests", List.of("Sports", "Technology"),
                            "behavior", List.of("Urban"),
                            "income", List.of("High")))
                    .build())
            .build();
    campaign.setId(campaignId);
    campaign.setIsNegotiated(true);

    CampaignInventorySchedules config = new CampaignInventorySchedules();
    config.setInventoryId("inventory1");
    config.setScheduleIds(new ArrayList<>());

    Inventory.Location location =
        Inventory.Location.builder().city("New York").country("USA").state("NY").build();

    Inventory.Price price = Inventory.Price.builder().spot(100.0).cpm(10.0).build();

    Inventory inventory = new Inventory();
    inventory.setId("inventory1");
    inventory.setName("Test Inventory");
    inventory.setType("DIGITAL");
    inventory.setFormat("LED");
    inventory.setVenueType(List.of("Mall"));
    inventory.setLocation(location);
    inventory.setPrices(List.of(price));

    MeasureReachFrequencyResponseDTO influenceResponse =
        MeasureReachFrequencyResponseDTO.builder()
            .impressions(10000L)
            .reach(5000L)
            .frequency(2.0)
            .status("Ok")
            .build();

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    when(configService.findByCampaignId(campaignId)).thenReturn(List.of(config));
    when(inventoryService.getById("inventory1")).thenReturn(inventory);
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("user123")).thenReturn(testUserResponseDTO);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, "company123"))
        .thenReturn(null);
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(influenceResponse);
    when(configService.prepareInventoryForecastForCampaignInventorySchedules(any(), any(), any()))
        .thenReturn(
            CampaignInventorySchedulesForecastDTO.builder()
                .estimatedAdPlays(100L)
                .totalSot(1000.0)
                .plannedSot(500.0)
                .sov(50.0)
                .build());

    // When
    CampaignMediaPlanResponseDTO result = campaignService.getCampaignMediaPlanDetails(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getHeaderInfoDTO()).isNotNull();
    assertThat(result.getHeaderInfoDTO().getName()).isEqualTo("Test Campaign");
    assertThat(result.getHeaderInfoDTO().getDuration()).isEqualTo(30);
    assertThat(result.getHeaderInfoDTO().getIsNegotiated()).isTrue();
    assertThat(result.getBrandResponseDTO()).isNotNull();
    assertThat(result.getBrandResponseDTO().getName()).isEqualTo("Test Brand");
    assertThat(result.getCampaignForecast()).isNotNull();
    assertThat(result.getAudienceDemographicsTargetingStrategyDTO()).isNotNull();
    assertThat(result.getSchedulesDTO()).isNotNull();

    verify(campaignRepository).findById(campaignId);
    verify(configService).findByCampaignId(campaignId);
    verify(inventoryService, times(2)).getById("inventory1");
  }

  @Test
  void getCampaignMediaPlanDetails_WithInvalidCampaignId_ShouldThrowException() {
    // Given
    String invalidId = "invalid123";
    when(campaignRepository.findById(invalidId)).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> campaignService.getCampaignMediaPlanDetails(invalidId))
        .isInstanceOf(CampaignNotFoundException.class)
        .hasMessageContaining(invalidId);

    verify(campaignRepository).findById(invalidId);
    verify(configService, never()).findByCampaignId(anyString());
  }

  @Test
  void getCampaignMediaPlanDetails_WithNoConfigs_ShouldReturnMediaPlanWithEmptyInventories() {
    // Given
    String campaignId = "campaign123";
    LocalDate startDate = LocalDate.now().plusDays(1);
    LocalDate endDate = LocalDate.now().plusDays(31);

    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(startDate)
            .endDate(endDate)
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .build();
    campaign.setId(campaignId);

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    when(configService.findByCampaignId(campaignId)).thenReturn(Collections.emptyList());
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("user123")).thenReturn(testUserResponseDTO);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, "company123"))
        .thenReturn(null);
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(
            MeasureReachFrequencyResponseDTO.builder()
                .impressions(0L)
                .reach(0L)
                .frequency(0.0)
                .status("Ok")
                .build());

    // When
    CampaignMediaPlanResponseDTO result = campaignService.getCampaignMediaPlanDetails(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getHeaderInfoDTO()).isNotNull();
    assertThat(result.getBrandResponseDTO()).isNotNull();
    assertThat(result.getCampaignForecast()).isNotNull();
    assertThat(result.getAudienceDemographicsTargetingStrategyDTO()).isNotNull();

    verify(campaignRepository).findById(campaignId);
    verify(configService).findByCampaignId(campaignId);
    verify(inventoryService, never()).getById(any());
  }

  @Test
  void getCampaignMediaPlanDetails_WithNoBrand_ShouldReturnMediaPlanWithoutBrand() {
    // Given
    String campaignId = "campaign123";
    LocalDate startDate = LocalDate.now().plusDays(1);
    LocalDate endDate = LocalDate.now().plusDays(31);

    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(startDate)
            .endDate(endDate)
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .build();
    campaign.setId(campaignId);

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    when(configService.findByCampaignId(campaignId)).thenReturn(Collections.emptyList());
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("user123")).thenReturn(testUserResponseDTO);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, "company123"))
        .thenReturn(null);
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(
            MeasureReachFrequencyResponseDTO.builder()
                .impressions(0L)
                .reach(0L)
                .frequency(0.0)
                .status("Ok")
                .build());

    // When
    CampaignMediaPlanResponseDTO result = campaignService.getCampaignMediaPlanDetails(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getBrandResponseDTO()).isNull();
  }

  @Test
  void getCampaignMediaPlanDetails_WithNoCustomFees_ShouldReturnMediaPlanWithoutCostBreakdown() {
    // Given
    String campaignId = "campaign123";
    LocalDate startDate = LocalDate.now().plusDays(1);
    LocalDate endDate = LocalDate.now().plusDays(31);

    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(startDate)
            .endDate(endDate)
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .build();
    campaign.setId(campaignId);

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    when(configService.findByCampaignId(campaignId)).thenReturn(Collections.emptyList());
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("user123")).thenReturn(testUserResponseDTO);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, "company123"))
        .thenReturn(null);
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(
            MeasureReachFrequencyResponseDTO.builder()
                .impressions(0L)
                .reach(0L)
                .frequency(0.0)
                .status("Ok")
                .build());

    // When
    CampaignMediaPlanResponseDTO result = campaignService.getCampaignMediaPlanDetails(campaignId);

    // Then
    assertThat(result).isNotNull();

    verify(campaignRepository).findById(campaignId);
  }

  @Test
  void getCampaignMediaPlanDetails_WithNullTargeting_ShouldReturnMediaPlanWithEmptyDemographics() {
    // Given
    String campaignId = "campaign123";
    LocalDate startDate = LocalDate.now().plusDays(1);
    LocalDate endDate = LocalDate.now().plusDays(31);

    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(startDate)
            .endDate(endDate)
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .targeting(null) // Null targeting
            .build();
    campaign.setId(campaignId);

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    when(configService.findByCampaignId(campaignId)).thenReturn(Collections.emptyList());
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("user123")).thenReturn(testUserResponseDTO);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, "company123"))
        .thenReturn(null);
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(
            MeasureReachFrequencyResponseDTO.builder()
                .impressions(0L)
                .reach(0L)
                .frequency(0.0)
                .status("Ok")
                .build());

    // When
    CampaignMediaPlanResponseDTO result = campaignService.getCampaignMediaPlanDetails(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getAudienceDemographicsTargetingStrategyDTO()).isNotNull();
    // Should handle null targeting gracefully
    assertThat(result.getAudienceDemographicsTargetingStrategyDTO().getAgeGroups()).isNull();

    verify(campaignRepository).findById(campaignId);
  }

  @Test
  void getCampaignMediaPlanDetails_WithMultipleInventories_ShouldAggregateCorrectly() {
    // Given
    String campaignId = "campaign123";
    LocalDate startDate = LocalDate.now().plusDays(1);
    LocalDate endDate = LocalDate.now().plusDays(31);

    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(startDate)
            .endDate(endDate)
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .build();
    campaign.setId(campaignId);

    CampaignInventorySchedules config1 = new CampaignInventorySchedules();
    config1.setInventoryId("inventory1");
    config1.setScheduleIds(new ArrayList<>());

    CampaignInventorySchedules config2 = new CampaignInventorySchedules();
    config2.setInventoryId("inventory2");
    config2.setScheduleIds(new ArrayList<>());

    Inventory.Location location1 =
        Inventory.Location.builder().city("New York").country("USA").state("NY").build();

    Inventory.Location location2 =
        Inventory.Location.builder().city("Los Angeles").country("USA").state("CA").build();

    Inventory.Price price1 = Inventory.Price.builder().spot(100.0).cpm(10.0).build();

    Inventory.Price price2 = Inventory.Price.builder().spot(150.0).cpm(15.0).build();

    Inventory inventory1 = new Inventory();
    inventory1.setId("inventory1");
    inventory1.setName("Inventory 1");
    inventory1.setType("DIGITAL");
    inventory1.setFormat("LED");
    inventory1.setVenueType(List.of("Mall"));
    inventory1.setLocation(location1);
    inventory1.setPrices(List.of(price1));

    Inventory inventory2 = new Inventory();
    inventory2.setId("inventory2");
    inventory2.setName("Inventory 2");
    inventory2.setType("CLASSIC");
    inventory2.setFormat("OTHERS");
    inventory2.setVenueType(List.of("Airport"));
    inventory2.setLocation(location2);
    inventory2.setPrices(List.of(price2));

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    MeasureReachFrequencyResponseDTO influenceResponse =
        MeasureReachFrequencyResponseDTO.builder()
            .impressions(20000L)
            .reach(10000L)
            .frequency(2.0)
            .status("Ok")
            .build();

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    when(configService.findByCampaignId(campaignId)).thenReturn(List.of(config1, config2));
    when(inventoryService.getById("inventory1")).thenReturn(inventory1);
    when(inventoryService.getById("inventory2")).thenReturn(inventory2);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("user123")).thenReturn(testUserResponseDTO);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, "company123"))
        .thenReturn(null);
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(influenceResponse);
    when(configService.prepareInventoryForecastForCampaignInventorySchedules(any(), any(), any()))
        .thenReturn(
            CampaignInventorySchedulesForecastDTO.builder()
                .estimatedAdPlays(100L)
                .totalSot(1000.0)
                .plannedSot(500.0)
                .sov(50.0)
                .build());

    // When
    CampaignMediaPlanResponseDTO result = campaignService.getCampaignMediaPlanDetails(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getSchedulesDTO()).isNotNull();

    verify(campaignRepository).findById(campaignId);
    verify(configService).findByCampaignId(campaignId);
    verify(inventoryService, times(2)).getById("inventory1");
  }

  @Test
  void getCampaignMediaPlanDetails_WithUnknownUser_ShouldUseUnknownPreparedBy() {
    // Given
    String campaignId = "campaign123";
    LocalDate startDate = LocalDate.now().plusDays(1);
    LocalDate endDate = LocalDate.now().plusDays(31);

    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(startDate)
            .endDate(endDate)
            .userId("unknownUser")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .build();
    campaign.setId(campaignId);

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    when(campaignRepository.existsById(campaignId)).thenReturn(true);
    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    when(configService.findByCampaignId(campaignId)).thenReturn(Collections.emptyList());
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("unknownUser")).thenReturn(null);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, "company123"))
        .thenReturn(null);
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(
            MeasureReachFrequencyResponseDTO.builder()
                .impressions(0L)
                .reach(0L)
                .frequency(0.0)
                .status("Ok")
                .build());

    // When
    CampaignMediaPlanResponseDTO result = campaignService.getCampaignMediaPlanDetails(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getHeaderInfoDTO()).isNotNull();
    assertThat(result.getHeaderInfoDTO().getPreparedBy()).isEqualTo("Unknown");
  }

  // ========== getCampaignViewDetails Tests ==========

  @Test
  void getCampaignViewDetails_WithValidCampaign_ShouldReturnCompleteViewDetails() {
    // Given
    String campaignId = "campaign123";
    LocalDate startDate = LocalDate.now().plusDays(1);
    LocalDate endDate = LocalDate.now().plusDays(31);

    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .description("Test Description")
            .status(Campaign.Status.APPROVED)
            .budget(10000.0)
            .currency("USD")
            .startDate(startDate)
            .endDate(endDate)
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .goals(
                Campaign.Goals.builder()
                    .goalType(Campaign.Goals.GoalType.IMPRESSIONS)
                    .targetValue(1000000.0)
                    .build())
            .targeting(
                Campaign.Targeting.builder()
                    .demographics(
                        Map.of(
                            "age", List.of("18-24", "25-34"),
                            "interests", List.of("Sports", "Technology")))
                    .build())
            .build();
    campaign.setId(campaignId);
    campaign.setIsNegotiated(true);

    CampaignInventorySchedules config = new CampaignInventorySchedules();
    config.setInventoryId("inventory1");
    config.setScheduleIds(new ArrayList<>());

    Inventory.Location location =
        Inventory.Location.builder().city("New York").country("USA").state("NY").build();
    Inventory.Price price = Inventory.Price.builder().spot(100.0).cpm(10.0).build();
    Inventory inventory = new Inventory();
    inventory.setId("inventory1");
    inventory.setName("Test Inventory");
    inventory.setType("DIGITAL");
    inventory.setFormat("LED");
    inventory.setLocation(location);
    inventory.setPrices(List.of(price));

    UserResponseDTO userResponseDTO = new UserResponseDTO();
    userResponseDTO.setId("user123");
    userResponseDTO.setFirstName("John");
    userResponseDTO.setLastName("Doe");

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");
    brandResponseDTO.setCategory("IAB17");

    CountryResponseDTO countryResponseDTO =
        CountryResponseDTO.builder().id("US").name("United States").build();

    MeasureReachFrequencyResponseDTO influenceResponse =
        MeasureReachFrequencyResponseDTO.builder()
            .impressions(10000L)
            .reach(5000L)
            .frequency(2.0)
            .status("Ok")
            .build();

    CostSplitByResponseDTO costSplitBy = new CostSplitByResponseDTO();
    costSplitBy.setName("Media Owner 1");
    costSplitBy.setTotalAmount(5000.0);
    // Non-zero so the totalCost mirroring assertion below proves real data flows through end to
    // end, rather than both sides trivially defaulting to Mockito's null -> 0.0.
    when(configService.calculateCampaignInventorySchedulesProposedPrice(
            any(), any(), any(), any(), any(), any()))
        .thenReturn(12345.67);

    CampaignProposalStatus proposalStatus = new CampaignProposalStatus();
    proposalStatus.setStatus(CampaignProposalStatus.Status.APPROVED);

    when(campaignRepository.existsById(campaignId)).thenReturn(true);
    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    when(configService.findByCampaignId(campaignId)).thenReturn(List.of(config));
    when(inventoryService.getById("inventory1")).thenReturn(inventory);
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("user123")).thenReturn(userResponseDTO);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, "company123"))
        .thenReturn(proposalStatus);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(countryService.getCountryByName("US")).thenReturn(countryResponseDTO);
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(influenceResponse);
    when(configService.prepareInventoryForecastForCampaignInventorySchedules(any(), any(), any()))
        .thenReturn(
            CampaignInventorySchedulesForecastDTO.builder()
                .estimatedAdPlays(100L)
                .totalSot(1000.0)
                .plannedSot(500.0)
                .sov(50.0)
                .build());

    // When
    CampaignViewResponseDTO result = campaignService.getCampaignViewDetails(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(campaignId);
    assertThat(result.getName()).isEqualTo("Test Campaign");
    assertThat(result.getStatus()).isEqualTo("APPROVED");
    assertThat(result.getCurrency()).isEqualTo("USD");
    assertThat(result.getIsNegotiated()).isTrue();

    // Campaign Detail
    assertThat(result.getCampaignDetail()).isNotNull();
    assertThat(result.getCampaignDetail().getBudget()).isEqualTo(10000.0);
    assertThat(result.getCampaignDetail().getCountry()).isEqualTo("United States");

    // Key Stakeholder Detail
    assertThat(result.getCampaignKeyStakeholderDetail()).isNotNull();

    // Goals
    assertThat(result.getGoals()).isNotNull();
    assertThat(result.getGoals().getGoalType()).isEqualTo("Impressions");
    assertThat(result.getGoals().getTargetValue()).isEqualTo(1000000.0);

    // Targeting
    assertThat(result.getTargeting()).isNotNull();
    assertThat(result.getTargeting().getAudienceDemographicsTargetingStrategyDTO()).isNotNull();

    // Inventory Overview
    assertThat(result.getInventoryOverview()).isNotNull();
    assertThat(result.getInventoryOverview().getTotalInventories()).isEqualTo(1);

    // Performance Metrics
    assertThat(result.getCampaignForecast()).isNotNull();

    // Cost Breakdown — totalCost must mirror the forecast's already-correct total. Asserted
    // against the concrete stubbed value (not just self-consistency) so this can't pass by both
    // sides trivially defaulting to 0.0.
    assertThat(result.getCostBreakdown()).isNotNull();
    assertThat(result.getCostBreakdown().getTotalCost()).isEqualTo(12345.67);
    assertThat(result.getCostBreakdown().getTotalCost())
        .isEqualTo(result.getCampaignForecast().getTotalCost());

    // Cost Split By

    verify(campaignRepository).findById(campaignId);
    verify(configService).findByCampaignId(campaignId);
    // Called multiple times: in getCampaignViewDetails, calculateCampaignForecast, and potentially
    // in other methods
    verify(inventoryService, atLeast(1)).getById("inventory1");
    verify(countryService).getCountryByName("US");
  }

  @Test
  void getCampaignViewDetails_WithAgencyClientType_ShouldIncludeAgencyDetails() {
    // Given
    String campaignId = "campaign123";
    LocalDate startDate = LocalDate.now().plusDays(1);
    LocalDate endDate = LocalDate.now().plusDays(31);

    Campaign campaign =
        Campaign.builder()
            .name("Agency Campaign")
            .status(Campaign.Status.DRAFT)
            .budget(20000.0)
            .currency("USD")
            .startDate(startDate)
            .endDate(endDate)
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.AGENCY)
            .agency(Campaign.CampaignAgency.builder().id("agency123").name("Test Agency").build())
            .companyId("company123")
            .countryId("US")
            .goals(
                Campaign.Goals.builder()
                    .goalType(Campaign.Goals.GoalType.REACH)
                    .targetValue(50000.0)
                    .build())
            .build();
    campaign.setId(campaignId);

    UserResponseDTO userResponseDTO = new UserResponseDTO();
    userResponseDTO.setId("user123");
    userResponseDTO.setFirstName("Jane");
    userResponseDTO.setLastName("Smith");

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Agency Brand");

    AgencyResponseDTO agencyResponseDTO =
        AgencyResponseDTO.builder().id("agency123").name("Test Agency").build();

    CountryResponseDTO countryResponseDTO =
        CountryResponseDTO.builder().id("US").name("United States").build();

    when(campaignRepository.existsById(campaignId)).thenReturn(true);
    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    when(configService.findByCampaignId(campaignId)).thenReturn(Collections.emptyList());
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("user123")).thenReturn(userResponseDTO);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, "company123"))
        .thenReturn(null);
    testCampaign.getBrand().setName("Test Brand");
    when(countryService.getCountryByName("US")).thenReturn(countryResponseDTO);
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(
            MeasureReachFrequencyResponseDTO.builder()
                .impressions(0L)
                .reach(0L)
                .frequency(0.0)
                .status("Ok")
                .build());

    // When
    CampaignViewResponseDTO result = campaignService.getCampaignViewDetails(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getCampaignKeyStakeholderDetail()).isNotNull();
  }

  @Test
  void getCampaignViewDetails_WithNoInventories_ShouldReturnViewDetailsWithEmptyInventories() {
    // Given
    String campaignId = "campaign123";
    LocalDate startDate = LocalDate.now().plusDays(1);
    LocalDate endDate = LocalDate.now().plusDays(31);

    Campaign campaign =
        Campaign.builder()
            .name("No Inventory Campaign")
            .status(Campaign.Status.DRAFT)
            .budget(5000.0)
            .currency("USD")
            .startDate(startDate)
            .endDate(endDate)
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .goals(
                Campaign.Goals.builder()
                    .goalType(Campaign.Goals.GoalType.SOV)
                    .targetValue(20.0)
                    .build())
            .build();
    campaign.setId(campaignId);

    UserResponseDTO userResponseDTO = new UserResponseDTO();
    userResponseDTO.setId("user123");
    userResponseDTO.setFirstName("Test");
    userResponseDTO.setLastName("User");

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    CountryResponseDTO countryResponseDTO =
        CountryResponseDTO.builder().id("US").name("United States").build();

    when(campaignRepository.existsById(campaignId)).thenReturn(true);
    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    when(configService.findByCampaignId(campaignId)).thenReturn(Collections.emptyList());
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("user123")).thenReturn(userResponseDTO);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, "company123"))
        .thenReturn(null);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(countryService.getCountryByName("US")).thenReturn(countryResponseDTO);
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(
            MeasureReachFrequencyResponseDTO.builder()
                .impressions(0L)
                .reach(0L)
                .frequency(0.0)
                .status("Ok")
                .build());

    // When
    CampaignViewResponseDTO result = campaignService.getCampaignViewDetails(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getInventoryOverview()).isNotNull();
    assertThat(result.getInventoryOverview().getTotalInventories()).isEqualTo(0);

    verify(configService).findByCampaignId(campaignId);
    verify(inventoryService, never()).getById(anyString());
  }

  @Test
  void getCampaignViewDetails_WithMultipleInventories_ShouldAggregateCorrectly() {
    // Given
    String campaignId = "campaign123";
    LocalDate startDate = LocalDate.now().plusDays(1);
    LocalDate endDate = LocalDate.now().plusDays(31);

    Campaign campaign =
        Campaign.builder()
            .name("Multi Inventory Campaign")
            .status(Campaign.Status.APPROVED)
            .budget(30000.0)
            .currency("USD")
            .startDate(startDate)
            .endDate(endDate)
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .goals(
                Campaign.Goals.builder()
                    .goalType(Campaign.Goals.GoalType.IMPRESSIONS)
                    .targetValue(2000000.0)
                    .build())
            .build();
    campaign.setId(campaignId);

    CampaignInventorySchedules config1 = new CampaignInventorySchedules();
    config1.setInventoryId("inventory1");
    config1.setScheduleIds(new ArrayList<>());

    CampaignInventorySchedules config2 = new CampaignInventorySchedules();
    config2.setInventoryId("inventory2");
    config2.setScheduleIds(new ArrayList<>());

    Inventory inventory1 = new Inventory();
    inventory1.setId("inventory1");
    inventory1.setName("Inventory 1");
    inventory1.setType("DIGITAL");
    inventory1.setFormat("LED");
    inventory1.setLocation(
        Inventory.Location.builder().city("New York").country("USA").state("NY").build());
    inventory1.setPrices(List.of(Inventory.Price.builder().spot(100.0).cpm(10.0).build()));

    Inventory inventory2 = new Inventory();
    inventory2.setId("inventory2");
    inventory2.setName("Inventory 2");
    inventory2.setType("CLASSIC");
    inventory2.setFormat("OTHERS");
    inventory2.setLocation(
        Inventory.Location.builder().city("Los Angeles").country("USA").state("CA").build());
    inventory2.setPrices(List.of(Inventory.Price.builder().spot(150.0).cpm(15.0).build()));

    UserResponseDTO userResponseDTO = new UserResponseDTO();
    userResponseDTO.setId("user123");
    userResponseDTO.setFirstName("Multi");
    userResponseDTO.setLastName("User");

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    CountryResponseDTO countryResponseDTO =
        CountryResponseDTO.builder().id("US").name("United States").build();

    MeasureReachFrequencyResponseDTO influenceResponse =
        MeasureReachFrequencyResponseDTO.builder()
            .impressions(20000L)
            .reach(10000L)
            .frequency(2.0)
            .status("Ok")
            .build();

    when(campaignRepository.existsById(campaignId)).thenReturn(true);
    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    when(configService.findByCampaignId(campaignId)).thenReturn(List.of(config1, config2));
    when(inventoryService.getById("inventory1")).thenReturn(inventory1);
    when(inventoryService.getById("inventory2")).thenReturn(inventory2);
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("user123")).thenReturn(userResponseDTO);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, "company123"))
        .thenReturn(null);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(countryService.getCountryByName("US")).thenReturn(countryResponseDTO);
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(influenceResponse);
    when(configService.prepareInventoryForecastForCampaignInventorySchedules(any(), any(), any()))
        .thenReturn(
            CampaignInventorySchedulesForecastDTO.builder()
                .estimatedAdPlays(100L)
                .totalSot(1000.0)
                .plannedSot(500.0)
                .sov(50.0)
                .build());

    // When
    CampaignViewResponseDTO result = campaignService.getCampaignViewDetails(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getInventoryOverview()).isNotNull();
    assertThat(result.getInventoryOverview().getTotalInventories()).isEqualTo(2);
    assertThat(result.getInventoryOverview().getTotalTypes()).isEqualTo(2);
    assertThat(result.getInventoryOverview().getTotalCity()).isEqualTo(2);

    // Called twice for each inventory: once in getCampaignViewDetails and once in
    // calculateCampaignForecast
    // Called multiple times for each inventory: in getCampaignViewDetails,
    // calculateCampaignForecast, and potentially in other methods
    verify(inventoryService, atLeast(1)).getById("inventory1");
    verify(inventoryService, atLeast(1)).getById("inventory2");
  }

  @Test
  void getCampaignViewDetails_WithDifferentGoalTypes_ShouldCalculateAchievedValueCorrectly() {
    // Given
    String campaignId = "campaign123";
    LocalDate startDate = LocalDate.now().plusDays(1);
    LocalDate endDate = LocalDate.now().plusDays(31);

    // Test with REACH goal type
    Campaign campaign =
        Campaign.builder()
            .name("Reach Goal Campaign")
            .status(Campaign.Status.DRAFT)
            .budget(15000.0)
            .currency("USD")
            .startDate(startDate)
            .endDate(endDate)
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .goals(
                Campaign.Goals.builder()
                    .goalType(Campaign.Goals.GoalType.REACH)
                    .targetValue(100000.0)
                    .build())
            .build();
    campaign.setId(campaignId);

    CampaignInventorySchedules config = new CampaignInventorySchedules();
    config.setInventoryId("inventory1");
    config.setScheduleIds(List.of("schedule1")); // Add scheduleIds for reach calculation

    Inventory inventory = new Inventory();
    inventory.setId("inventory1");
    inventory.setName("Test Inventory");
    inventory.setType("DIGITAL");
    inventory.setFormat("LED");
    inventory.setLocation(
        Inventory.Location.builder().city("New York").country("USA").state("NY").build());
    inventory.setPrices(List.of(Inventory.Price.builder().spot(100.0).cpm(10.0).build()));

    // Create Schedule with reach
    Schedule schedule = new Schedule();
    schedule.setId("schedule1");
    schedule.setImpressions(50000L);
    schedule.setReach(25000L);

    UserResponseDTO userResponseDTO = new UserResponseDTO();
    userResponseDTO.setId("user123");
    userResponseDTO.setFirstName("Goal");
    userResponseDTO.setLastName("Tester");

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    CountryResponseDTO countryResponseDTO =
        CountryResponseDTO.builder().id("US").name("United States").build();

    MeasureReachFrequencyResponseDTO influenceResponse =
        MeasureReachFrequencyResponseDTO.builder()
            .impressions(50000L)
            .reach(25000L)
            .frequency(2.0)
            .status("Ok")
            .build();

    when(campaignRepository.existsById(campaignId)).thenReturn(true);
    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    when(configService.findByCampaignId(campaignId)).thenReturn(List.of(config));
    when(inventoryService.getById("inventory1")).thenReturn(inventory);
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("user123")).thenReturn(userResponseDTO);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, "company123"))
        .thenReturn(null);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(countryService.getCountryByName("US")).thenReturn(countryResponseDTO);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(schedule));
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(influenceResponse);
    when(configService.prepareInventoryForecastForCampaignInventorySchedules(any(), any(), any()))
        .thenReturn(
            CampaignInventorySchedulesForecastDTO.builder()
                .estimatedAdPlays(100L)
                .totalSot(1000.0)
                .plannedSot(500.0)
                .sov(50.0)
                .build());

    // When
    CampaignViewResponseDTO result = campaignService.getCampaignViewDetails(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getGoals()).isNotNull();
    assertThat(result.getGoals().getGoalType()).isEqualTo("Reach");
    assertThat(result.getGoals().getTargetValue()).isEqualTo(100000.0);
    assertThat(result.getGoals().getAchievedValue()).isEqualTo(25000.0); // From reach in response
  }

  @Test
  void getCampaignViewDetails_WithAdPlaysGoalType_ShouldCalculateAchievedValueCorrectly() {
    // Given
    String campaignId = "campaign123";
    LocalDate startDate = LocalDate.now().plusDays(1);
    LocalDate endDate = LocalDate.now().plusDays(31);

    Campaign campaign =
        Campaign.builder()
            .name("AdPlays Goal Campaign")
            .status(Campaign.Status.DRAFT)
            .budget(15000.0)
            .currency("USD")
            .startDate(startDate)
            .endDate(endDate)
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .goals(
                Campaign.Goals.builder()
                    .goalType(Campaign.Goals.GoalType.ADPLAYS)
                    .targetValue(10000.0)
                    .build())
            .build();
    campaign.setId(campaignId);

    CampaignInventorySchedules config = new CampaignInventorySchedules();
    config.setCampaignId(campaignId);
    config.setMediaOwnerId("company123");
    config.setInventoryId("inventory1");
    config.setScheduleIds(List.of("schedule1"));

    Inventory inventory = new Inventory();
    inventory.setId("inventory1");
    inventory.setName("Test Inventory");
    inventory.setType("DIGITAL");
    inventory.setFormat("LED");
    inventory.setLocation(
        Inventory.Location.builder().city("New York").country("USA").state("NY").build());
    inventory.setPrices(List.of(Inventory.Price.builder().spot(100.0).cpm(10.0).build()));

    Schedule schedule = new Schedule();
    schedule.setId("schedule1");
    schedule.setAdPlays(5000L);
    schedule.setImpressions(50000L);
    schedule.setReach(25000L);

    UserResponseDTO userResponseDTO = new UserResponseDTO();
    userResponseDTO.setId("user123");
    userResponseDTO.setFirstName("AdPlays");
    userResponseDTO.setLastName("Tester");

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    CountryResponseDTO countryResponseDTO =
        CountryResponseDTO.builder().id("US").name("United States").build();

    MeasureReachFrequencyResponseDTO influenceResponse =
        MeasureReachFrequencyResponseDTO.builder()
            .impressions(50000L)
            .reach(25000L)
            .frequency(2.0)
            .status("Ok")
            .build();

    when(campaignRepository.existsById(campaignId)).thenReturn(true);
    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    when(configService.findByCampaignId(campaignId)).thenReturn(List.of(config));
    when(inventoryService.getById("inventory1")).thenReturn(inventory);
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("user123")).thenReturn(userResponseDTO);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, "company123"))
        .thenReturn(null);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(countryService.getCountryByName("US")).thenReturn(countryResponseDTO);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(schedule));
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(influenceResponse);
    when(configService.prepareInventoryForecastForCampaignInventorySchedules(any(), any(), any()))
        .thenReturn(
            CampaignInventorySchedulesForecastDTO.builder()
                .estimatedAdPlays(5000L)
                .totalSot(1000.0)
                .plannedSot(500.0)
                .sov(50.0)
                .build());

    // When
    CampaignViewResponseDTO result = campaignService.getCampaignViewDetails(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getGoals()).isNotNull();
    assertThat(result.getGoals().getGoalType()).isEqualTo("Ad Plays");
    assertThat(result.getGoals().getTargetValue()).isEqualTo(10000.0);
    assertThat(result.getGoals().getAchievedValue())
        .isEqualTo(5000.0); // From estimatedAdPlays in forecast
  }

  @Test
  void getCampaignViewDetails_WithAdPlaysGoalType_ShouldIncludeWeeklyBreakdown() {
    // Given
    String campaignId = "campaign123";
    LocalDate startDate = LocalDate.now().plusDays(1);
    LocalDate endDate = startDate.plusDays(20); // 3 weeks campaign

    Campaign campaign =
        Campaign.builder()
            .name("AdPlays Weekly Breakdown Campaign")
            .status(Campaign.Status.DRAFT)
            .budget(15000.0)
            .currency("USD")
            .startDate(startDate)
            .endDate(endDate)
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .goals(
                Campaign.Goals.builder()
                    .goalType(Campaign.Goals.GoalType.ADPLAYS)
                    .targetValue(15000.0)
                    .targetName("Ad Plays")
                    .build())
            .build();
    campaign.setId(campaignId);

    CampaignInventorySchedules config = new CampaignInventorySchedules();
    config.setCampaignId(campaignId);
    config.setMediaOwnerId("company123");
    config.setInventoryId("inventory1");
    config.setScheduleIds(List.of("schedule1"));

    Inventory inventory = new Inventory();
    inventory.setId("inventory1");
    inventory.setName("Test Inventory");
    inventory.setType("DIGITAL");
    inventory.setFormat("LED");
    inventory.setLocation(
        Inventory.Location.builder().city("New York").country("USA").state("NY").build());
    inventory.setPrices(List.of(Inventory.Price.builder().spot(100.0).cpm(10.0).build()));

    // Create schedule with booking matrix spanning the campaign duration
    Map<String, List<Integer>> bookingMatrix = new HashMap<>();
    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      bookingMatrix.put(date.toString(), List.of(9, 10, 11, 12, 13, 14, 15, 16, 17, 18));
    }

    Schedule schedule = new Schedule();
    schedule.setId("schedule1");
    schedule.setStartDate(startDate);
    schedule.setEndDate(endDate);
    schedule.setBookingMatrix(bookingMatrix);
    schedule.setAdPlays(15000L);
    schedule.setImpressions(150000L);
    schedule.setReach(50000L);

    UserResponseDTO userResponseDTO = new UserResponseDTO();
    userResponseDTO.setId("user123");
    userResponseDTO.setFirstName("Weekly");
    userResponseDTO.setLastName("Tester");

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    CountryResponseDTO countryResponseDTO =
        CountryResponseDTO.builder().id("US").name("United States").build();

    MeasureReachFrequencyResponseDTO influenceResponse =
        MeasureReachFrequencyResponseDTO.builder()
            .impressions(50000L)
            .reach(20000L)
            .frequency(2.5)
            .status("Ok")
            .build();

    when(campaignRepository.existsById(campaignId)).thenReturn(true);
    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    when(configService.findByCampaignId(campaignId)).thenReturn(List.of(config));
    when(inventoryService.getById("inventory1")).thenReturn(inventory);
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("user123")).thenReturn(userResponseDTO);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, "company123"))
        .thenReturn(null);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(countryService.getCountryByName("US")).thenReturn(countryResponseDTO);
    when(scheduleRepository.findAllById(anyList())).thenReturn(List.of(schedule));
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(influenceResponse);
    // Note: findById is not called for weekly breakdown - only findAllById is used
    when(configService.prepareInventoryForecastForCampaignInventorySchedules(any(), any(), any()))
        .thenReturn(
            CampaignInventorySchedulesForecastDTO.builder()
                .estimatedAdPlays(15000L)
                .totalSot(2100.0)
                .plannedSot(1050.0)
                .sov(50.0)
                .build());

    // When
    CampaignViewResponseDTO result = campaignService.getCampaignViewDetails(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getGoals()).isNotNull();
    assertThat(result.getGoals().getGoalType()).isEqualTo("Ad Plays");
    assertThat(result.getGoals().getTargetValue()).isEqualTo(15000.0);

    // Verify weekly breakdown is populated (ADPLAYS is included in goal types that support weekly
    // breakdown)
    assertThat(result.getGoals().getWeeklyBreakdown()).isNotNull();

    // If weekly breakdown is populated, verify the structure
    if (result.getGoals().getWeeklyBreakdown() != null
        && !result.getGoals().getWeeklyBreakdown().isEmpty()) {
      // Verify all percentage values are valid
      result
          .getGoals()
          .getWeeklyBreakdown()
          .forEach(
              (week, percentage) -> {
                assertThat(percentage).isGreaterThanOrEqualTo(0.0);
              });
    }
  }

  @Test
  void getCampaignViewDetails_WithCostSplitBy_ShouldIncludeCostSplitDetails() {
    // Given
    String campaignId = "campaign123";
    LocalDate startDate = LocalDate.now().plusDays(1);
    LocalDate endDate = LocalDate.now().plusDays(31);

    Campaign campaign =
        Campaign.builder()
            .name("Cost Split Campaign")
            .status(Campaign.Status.APPROVED)
            .budget(25000.0)
            .currency("USD")
            .startDate(startDate)
            .endDate(endDate)
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .goals(
                Campaign.Goals.builder()
                    .goalType(Campaign.Goals.GoalType.IMPRESSIONS)
                    .targetValue(1500000.0)
                    .build())
            .build();
    campaign.setId(campaignId);

    CampaignInventorySchedules config = new CampaignInventorySchedules();
    config.setInventoryId("inventory1");
    config.setScheduleIds(new ArrayList<>());

    Inventory inventory = new Inventory();
    inventory.setId("inventory1");
    inventory.setName("Test Inventory");
    inventory.setType("DIGITAL");
    inventory.setFormat("LED");
    inventory.setLocation(
        Inventory.Location.builder().city("New York").country("USA").state("NY").build());
    inventory.setPrices(List.of(Inventory.Price.builder().spot(100.0).cpm(10.0).build()));

    UserResponseDTO userResponseDTO = new UserResponseDTO();
    userResponseDTO.setId("user123");
    userResponseDTO.setFirstName("Cost");
    userResponseDTO.setLastName("Split");

    BrandResponseDTO brandResponseDTO = new BrandResponseDTO();
    brandResponseDTO.setId("brand123");
    brandResponseDTO.setName("Test Brand");

    CountryResponseDTO countryResponseDTO =
        CountryResponseDTO.builder().id("US").name("United States").build();

    MeasureReachFrequencyResponseDTO influenceResponse =
        MeasureReachFrequencyResponseDTO.builder()
            .impressions(15000L)
            .reach(7500L)
            .frequency(2.0)
            .status("Ok")
            .build();

    CostSplitByResponseDTO costSplit1 = new CostSplitByResponseDTO();
    costSplit1.setName("Media Owner 1");
    costSplit1.setTotalAmount(8000.0);
    costSplit1.setTotalAmountInPercentage(80.0);

    CostSplitByResponseDTO costSplit2 = new CostSplitByResponseDTO();
    costSplit2.setName("Media Owner 2");
    costSplit2.setTotalAmount(2000.0);
    costSplit2.setTotalAmountInPercentage(20.0);

    when(campaignRepository.existsById(campaignId)).thenReturn(true);
    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    when(configService.findByCampaignId(campaignId)).thenReturn(List.of(config));
    when(inventoryService.getById("inventory1")).thenReturn(inventory);
    when(userService.getIamUserContext()).thenReturn(testUserContext);
    when(userService.getUserById("user123")).thenReturn(userResponseDTO);
    when(campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
            campaignId, "company123"))
        .thenReturn(null);
    testCampaign.getBrand().setName("Test Brand");
    // brandService no longer called for brand name lookup after CampaignBrand refactor
    when(countryService.getCountryByName("US")).thenReturn(countryResponseDTO);
    when(mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
            anyInt(), anyList(), anyMap(), any(), any()))
        .thenReturn(influenceResponse);
    when(configService.prepareInventoryForecastForCampaignInventorySchedules(any(), any(), any()))
        .thenReturn(
            CampaignInventorySchedulesForecastDTO.builder()
                .estimatedAdPlays(100L)
                .totalSot(1000.0)
                .plannedSot(500.0)
                .sov(50.0)
                .build());

    // When
    CampaignViewResponseDTO result = campaignService.getCampaignViewDetails(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getInventoryOverview()).isNotNull();
  }

  // ========== createCampaignComment Tests ==========

  @Test
  void createCampaignComment_WithValidDataAndFiles_ShouldCreateComment() {
    // Given
    String campaignId = "campaign123";
    String comment = "Test comment";
    String companyId = "company123";
    List<String> taggedCompanyIds = List.of("company456", "company789");
    List<String> fileUrls = List.of("s3://bucket/file1.pdf", "s3://bucket/file2.jpg");

    org.springframework.mock.web.MockMultipartFile file1 =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "test1.pdf", "application/pdf", "test content 1".getBytes());
    org.springframework.mock.web.MockMultipartFile file2 =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "test2.jpg", "image/jpeg", "test content 2".getBytes());
    List<org.springframework.web.multipart.MultipartFile> files = List.of(file1, file2);

    CampaignComments savedComment =
        CampaignComments.builder()
            .comment(comment)
            .fileUrls(fileUrls)
            .taggedCompanyIds(taggedCompanyIds)
            .campaignId(campaignId)
            .companyId(companyId)
            .build();
    savedComment.setId("comment123");

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(testCampaign));
    when(cloudStorageService.uploadFile(file1, "campaign-comments"))
        .thenReturn("s3://bucket/file1.pdf");
    when(cloudStorageService.uploadFile(file2, "campaign-comments"))
        .thenReturn("s3://bucket/file2.jpg");
    when(campaignCommentsRepository.save(any(CampaignComments.class))).thenReturn(savedComment);
    doNothing()
        .when(campaignActivityService)
        .logActivity(anyString(), any(), anyString(), anyInt());

    // When
    campaignService.createCampaignComment(campaignId, comment, files, taggedCompanyIds, companyId);

    // Then
    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(cloudStorageService).uploadFile(file1, "campaign-comments");
    verify(cloudStorageService).uploadFile(file2, "campaign-comments");
    verify(campaignCommentsRepository).save(any(CampaignComments.class));
    verify(campaignActivityService, times(1))
        .logActivity(
            eq(campaignId),
            eq(CampaignActivityService.OperationType.ADDED),
            eq(com.mw.planner.constants.CampaignActivityKey.COMMENT_FILE_COUNT.key()),
            eq(2));
  }

  @Test
  void createCampaignComment_WithValidDataWithoutFiles_ShouldCreateComment() {
    // Given
    String campaignId = "campaign123";
    String comment = "Test comment without files";
    String companyId = "company123";
    List<String> taggedCompanyIds = List.of("company456");

    CampaignComments savedComment =
        CampaignComments.builder()
            .comment(comment)
            .fileUrls(new ArrayList<>())
            .taggedCompanyIds(taggedCompanyIds)
            .campaignId(campaignId)
            .companyId(companyId)
            .build();
    savedComment.setId("comment123");

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(testCampaign));
    when(campaignCommentsRepository.save(any(CampaignComments.class))).thenReturn(savedComment);
    doNothing()
        .when(campaignActivityService)
        .logActivity(anyString(), any(), anyString(), anyInt());

    // When
    campaignService.createCampaignComment(campaignId, comment, null, taggedCompanyIds, companyId);

    // Then
    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(cloudStorageService, never()).uploadFile(any(), anyString());
    verify(campaignCommentsRepository).save(any(CampaignComments.class));
    verify(campaignActivityService, times(1))
        .logActivity(
            eq(campaignId),
            eq(CampaignActivityService.OperationType.ADDED),
            eq(com.mw.planner.constants.CampaignActivityKey.COMMENT_FILE_COUNT.key()),
            eq(0));
  }

  @Test
  void createCampaignComment_WithValidDataWithoutTaggedCompanyIds_ShouldCreateComment() {
    // Given
    String campaignId = "campaign123";
    String comment = "Test comment without tagged companies";
    String companyId = "company123";

    org.springframework.mock.web.MockMultipartFile file =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "test.pdf", "application/pdf", "test content".getBytes());
    List<org.springframework.web.multipart.MultipartFile> files = List.of(file);

    CampaignComments savedComment =
        CampaignComments.builder()
            .comment(comment)
            .fileUrls(List.of("s3://bucket/test.pdf"))
            .taggedCompanyIds(new ArrayList<>())
            .campaignId(campaignId)
            .companyId(companyId)
            .build();
    savedComment.setId("comment123");

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(testCampaign));
    when(cloudStorageService.uploadFile(file, "campaign-comments"))
        .thenReturn("s3://bucket/test.pdf");
    when(campaignCommentsRepository.save(any(CampaignComments.class))).thenReturn(savedComment);
    doNothing()
        .when(campaignActivityService)
        .logActivity(anyString(), any(), anyString(), anyInt());

    // When
    campaignService.createCampaignComment(campaignId, comment, files, null, companyId);

    // Then
    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(cloudStorageService).uploadFile(file, "campaign-comments");
    verify(campaignCommentsRepository).save(any(CampaignComments.class));
    verify(campaignActivityService, times(1))
        .logActivity(
            eq(campaignId),
            eq(CampaignActivityService.OperationType.ADDED),
            eq(com.mw.planner.constants.CampaignActivityKey.COMMENT_FILE_COUNT.key()),
            eq(1));
  }

  @Test
  void createCampaignComment_WithCampaignNotFound_ShouldThrowException() {
    // Given
    String campaignId = "nonexistent";
    String comment = "Test comment";
    String companyId = "company123";

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(
            () -> campaignService.createCampaignComment(campaignId, comment, null, null, companyId))
        .isInstanceOf(CampaignNotFoundException.class)
        .hasMessageContaining(campaignId);

    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(campaignCommentsRepository, never()).save(any(CampaignComments.class));
  }

  @Test
  void createCampaignComment_WithFileUploadFailure_ShouldThrowException() {
    // Given
    String campaignId = "campaign123";
    String comment = "Test comment";
    String companyId = "company123";

    org.springframework.mock.web.MockMultipartFile file =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "test.pdf", "application/pdf", "test content".getBytes());
    List<org.springframework.web.multipart.MultipartFile> files = List.of(file);

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(testCampaign));
    when(cloudStorageService.uploadFile(file, "campaign-comments"))
        .thenThrow(new RuntimeException("S3 upload failed"));

    // When & Then
    assertThatThrownBy(
            () ->
                campaignService.createCampaignComment(campaignId, comment, files, null, companyId))
        .isInstanceOf(com.mw.planner.exception.storage.StorageUploadFailedException.class)
        .hasMessageContaining("Failed to upload file: test.pdf");

    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(cloudStorageService).uploadFile(file, "campaign-comments");
    verify(campaignCommentsRepository, never()).save(any(CampaignComments.class));
  }

  @Test
  void createCampaignComment_WithEmptyFileList_ShouldCreateComment() {
    // Given
    String campaignId = "campaign123";
    String comment = "Test comment with empty file list";
    String companyId = "company123";
    List<org.springframework.web.multipart.MultipartFile> emptyFiles = new ArrayList<>();

    CampaignComments savedComment =
        CampaignComments.builder()
            .comment(comment)
            .fileUrls(new ArrayList<>())
            .taggedCompanyIds(new ArrayList<>())
            .campaignId(campaignId)
            .companyId(companyId)
            .build();
    savedComment.setId("comment123");

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(testCampaign));
    when(campaignCommentsRepository.save(any(CampaignComments.class))).thenReturn(savedComment);

    // When
    campaignService.createCampaignComment(campaignId, comment, emptyFiles, null, companyId);

    // Then
    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(cloudStorageService, never()).uploadFile(any(), anyString());
    verify(campaignCommentsRepository).save(any(CampaignComments.class));
  }

  @Test
  void createCampaignComment_WithNullFileInList_ShouldSkipNullFile() {
    // Given
    String campaignId = "campaign123";
    String comment = "Test comment with null file";
    String companyId = "company123";

    org.springframework.mock.web.MockMultipartFile validFile =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "test.pdf", "application/pdf", "test content".getBytes());
    List<org.springframework.web.multipart.MultipartFile> files =
        Arrays.asList(null, validFile, null);

    CampaignComments savedComment =
        CampaignComments.builder()
            .comment(comment)
            .fileUrls(List.of("s3://bucket/test.pdf"))
            .taggedCompanyIds(new ArrayList<>())
            .campaignId(campaignId)
            .companyId(companyId)
            .build();
    savedComment.setId("comment123");

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(testCampaign));
    when(cloudStorageService.uploadFile(validFile, "campaign-comments"))
        .thenReturn("s3://bucket/test.pdf");
    when(campaignCommentsRepository.save(any(CampaignComments.class))).thenReturn(savedComment);

    // When
    campaignService.createCampaignComment(campaignId, comment, files, null, companyId);

    // Then
    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(cloudStorageService, times(1)).uploadFile(any(), anyString());
    verify(cloudStorageService).uploadFile(validFile, "campaign-comments");
    verify(campaignCommentsRepository).save(any(CampaignComments.class));
  }

  @Test
  void createCampaignComment_WithEmptyFileInList_ShouldSkipEmptyFile() {
    // Given
    String campaignId = "campaign123";
    String comment = "Test comment with empty file";
    String companyId = "company123";

    org.springframework.mock.web.MockMultipartFile validFile =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "test.pdf", "application/pdf", "test content".getBytes());
    org.springframework.mock.web.MockMultipartFile emptyFile =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "empty.pdf", "application/pdf", new byte[0]);
    List<org.springframework.web.multipart.MultipartFile> files = List.of(emptyFile, validFile);

    CampaignComments savedComment =
        CampaignComments.builder()
            .comment(comment)
            .fileUrls(List.of("s3://bucket/test.pdf"))
            .taggedCompanyIds(new ArrayList<>())
            .campaignId(campaignId)
            .companyId(companyId)
            .build();
    savedComment.setId("comment123");

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(testCampaign));
    when(cloudStorageService.uploadFile(validFile, "campaign-comments"))
        .thenReturn("s3://bucket/test.pdf");
    when(campaignCommentsRepository.save(any(CampaignComments.class))).thenReturn(savedComment);

    // When
    campaignService.createCampaignComment(campaignId, comment, files, null, companyId);

    // Then
    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(cloudStorageService, times(1)).uploadFile(any(), anyString());
    verify(cloudStorageService).uploadFile(validFile, "campaign-comments");
    verify(campaignCommentsRepository).save(any(CampaignComments.class));
  }

  @Test
  void createCampaignComment_WithMultipleFiles_ShouldUploadAllFiles() {
    // Given
    String campaignId = "campaign123";
    String comment = "Test comment with multiple files";
    String companyId = "company123";

    org.springframework.mock.web.MockMultipartFile file1 =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "test1.pdf", "application/pdf", "content1".getBytes());
    org.springframework.mock.web.MockMultipartFile file2 =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "test2.jpg", "image/jpeg", "content2".getBytes());
    org.springframework.mock.web.MockMultipartFile file3 =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "test3.png", "image/png", "content3".getBytes());
    List<org.springframework.web.multipart.MultipartFile> files = List.of(file1, file2, file3);

    CampaignComments savedComment =
        CampaignComments.builder()
            .comment(comment)
            .fileUrls(
                List.of("s3://bucket/test1.pdf", "s3://bucket/test2.jpg", "s3://bucket/test3.png"))
            .taggedCompanyIds(new ArrayList<>())
            .campaignId(campaignId)
            .companyId(companyId)
            .build();
    savedComment.setId("comment123");

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(testCampaign));
    when(cloudStorageService.uploadFile(file1, "campaign-comments"))
        .thenReturn("s3://bucket/test1.pdf");
    when(cloudStorageService.uploadFile(file2, "campaign-comments"))
        .thenReturn("s3://bucket/test2.jpg");
    when(cloudStorageService.uploadFile(file3, "campaign-comments"))
        .thenReturn("s3://bucket/test3.png");
    when(campaignCommentsRepository.save(any(CampaignComments.class))).thenReturn(savedComment);

    // When
    campaignService.createCampaignComment(campaignId, comment, files, null, companyId);

    // Then
    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(cloudStorageService).uploadFile(file1, "campaign-comments");
    verify(cloudStorageService).uploadFile(file2, "campaign-comments");
    verify(cloudStorageService).uploadFile(file3, "campaign-comments");
    verify(campaignCommentsRepository).save(any(CampaignComments.class));
  }

  @Test
  void createCampaignComment_WithTaggedCompanyIds_ShouldStoreSeparatelyFromCompanyId() {
    // Given
    String campaignId = "campaign123";
    String comment = "Test comment";
    String companyId = "company123";
    List<String> taggedCompanyIds = List.of("company456", "company789");

    CampaignComments savedComment =
        CampaignComments.builder()
            .comment(comment)
            .fileUrls(new ArrayList<>())
            .taggedCompanyIds(taggedCompanyIds)
            .campaignId(campaignId)
            .companyId(companyId) // Stored separately
            .build();
    savedComment.setId("comment123");

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(testCampaign));
    when(campaignCommentsRepository.save(any(CampaignComments.class))).thenReturn(savedComment);

    // When
    campaignService.createCampaignComment(campaignId, comment, null, taggedCompanyIds, companyId);

    // Then
    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(campaignCommentsRepository)
        .save(
            argThat(
                c -> {
                  CampaignComments cc = (CampaignComments) c;
                  return cc.getCompanyId().equals(companyId)
                      && cc.getTaggedCompanyIds().equals(taggedCompanyIds)
                      && !cc.getTaggedCompanyIds()
                          .contains(companyId); // companyId not in taggedCompanyIds
                }));
  }

  @Test
  void createCampaignComment_WithNullCompanyId_ShouldStoreNullCompanyId() {
    // Given
    String campaignId = "campaign123";
    String comment = "Test comment";
    String companyId = null;

    CampaignComments savedComment =
        CampaignComments.builder()
            .comment(comment)
            .fileUrls(new ArrayList<>())
            .taggedCompanyIds(new ArrayList<>())
            .campaignId(campaignId)
            .companyId(null)
            .build();
    savedComment.setId("comment123");

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(testCampaign));
    when(campaignCommentsRepository.save(any(CampaignComments.class))).thenReturn(savedComment);

    // When
    campaignService.createCampaignComment(campaignId, comment, null, null, companyId);

    // Then
    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(campaignCommentsRepository)
        .save(
            argThat(
                c -> {
                  CampaignComments cc = (CampaignComments) c;
                  return cc.getCompanyId() == null;
                }));
  }

  // ========== getCommentsByCampaignId Tests ==========

  @Test
  void getCommentsByCampaignId_WithValidCampaignAndComments_ShouldReturnComments() {
    // Given
    String campaignId = "campaign123";
    LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

    CampaignComments comment1 =
        CampaignComments.builder()
            .comment("First comment")
            .fileUrls(List.of("s3://bucket/file1.pdf"))
            .taggedCompanyIds(List.of("company456"))
            .campaignId(campaignId)
            .companyId("company123")
            .build();
    comment1.setId("comment1");
    comment1.setCreatedBy("user1@example.com");
    comment1.setCreatedAt(createdAt);

    CampaignComments comment2 =
        CampaignComments.builder()
            .comment("Second comment")
            .fileUrls(new ArrayList<>())
            .taggedCompanyIds(new ArrayList<>())
            .campaignId(campaignId)
            .companyId("company789")
            .build();
    comment2.setId("comment2");
    comment2.setCreatedBy("user2@example.com");
    comment2.setCreatedAt(createdAt.plusHours(1));

    CompanyLookupResponseDTO companyLookup1 =
        CompanyLookupResponseDTO.builder().id("company123").companyType("MEDIA_BUYER").build();
    CompanyLookupResponseDTO companyLookup2 =
        CompanyLookupResponseDTO.builder().id("company789").companyType("MEDIA_OWNER").build();

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(testCampaign));
    when(campaignCommentsRepository.findByCampaignId(campaignId))
        .thenReturn(List.of(comment1, comment2));
    when(companyService.getCompanyLookupWithCompanyId("company123")).thenReturn(companyLookup1);
    when(companyService.getCompanyLookupWithCompanyId("company789")).thenReturn(companyLookup2);

    // When
    List<CampaignCommentsResponseDTO> result = campaignService.getCommentsByCampaignId(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getComment()).isEqualTo("First comment");
    assertThat(result.get(0).getCreatedBy()).isEqualTo("user1@example.com");
    assertThat(result.get(0).getBusinessType()).isEqualTo(CompanyDto.BusinessType.MEDIA_BUYER);
    assertThat(result.get(1).getComment()).isEqualTo("Second comment");
    assertThat(result.get(1).getCreatedBy()).isEqualTo("user2@example.com");
    assertThat(result.get(1).getBusinessType()).isEqualTo(CompanyDto.BusinessType.MEDIA_OWNER);

    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(campaignCommentsRepository).findByCampaignId(campaignId);
    verify(companyService).getCompanyLookupWithCompanyId("company123");
    verify(companyService).getCompanyLookupWithCompanyId("company789");
  }

  @Test
  void getCommentsByCampaignId_WithEmptyComments_ShouldReturnEmptyList() {
    // Given
    String campaignId = "campaign123";

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(testCampaign));
    when(campaignCommentsRepository.findByCampaignId(campaignId)).thenReturn(new ArrayList<>());

    // When
    List<CampaignCommentsResponseDTO> result = campaignService.getCommentsByCampaignId(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();

    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(campaignCommentsRepository).findByCampaignId(campaignId);
    verify(companyService, never()).getCompanyLookupWithCompanyId(anyString());
  }

  @Test
  void getCommentsByCampaignId_WithCampaignNotFound_ShouldThrowException() {
    // Given
    String campaignId = "nonexistent";

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> campaignService.getCommentsByCampaignId(campaignId))
        .isInstanceOf(CampaignNotFoundException.class)
        .hasMessageContaining(campaignId);

    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(campaignCommentsRepository, never()).findByCampaignId(anyString());
  }

  @Test
  void getCommentsByCampaignId_WithCommentWithoutCompanyId_ShouldReturnNullBusinessType() {
    // Given
    String campaignId = "campaign123";
    LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

    CampaignComments comment =
        CampaignComments.builder()
            .comment("Comment without companyId")
            .fileUrls(new ArrayList<>())
            .taggedCompanyIds(new ArrayList<>())
            .campaignId(campaignId)
            .companyId(null) // No companyId
            .build();
    comment.setId("comment1");
    comment.setCreatedBy("user@example.com");
    comment.setCreatedAt(createdAt);

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(testCampaign));
    when(campaignCommentsRepository.findByCampaignId(campaignId)).thenReturn(List.of(comment));

    // When
    List<CampaignCommentsResponseDTO> result = campaignService.getCommentsByCampaignId(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getComment()).isEqualTo("Comment without companyId");
    assertThat(result.get(0).getBusinessType()).isNull();

    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(campaignCommentsRepository).findByCampaignId(campaignId);
    verify(companyService, never()).getCompanyLookupWithCompanyId(anyString());
  }

  @Test
  void getCommentsByCampaignId_WithCompanyNotFound_ShouldReturnNullBusinessType() {
    // Given
    String campaignId = "campaign123";
    LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

    CampaignComments comment =
        CampaignComments.builder()
            .comment("Comment with non-existent company")
            .fileUrls(new ArrayList<>())
            .taggedCompanyIds(new ArrayList<>())
            .campaignId(campaignId)
            .companyId("nonexistent")
            .build();
    comment.setId("comment1");
    comment.setCreatedBy("user@example.com");
    comment.setCreatedAt(createdAt);

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(testCampaign));
    when(campaignCommentsRepository.findByCampaignId(campaignId)).thenReturn(List.of(comment));
    when(companyService.getCompanyLookupWithCompanyId("nonexistent"))
        .thenThrow(new com.mw.planner.exception.company.CompanyNotFoundException("nonexistent"));

    // When
    List<CampaignCommentsResponseDTO> result = campaignService.getCommentsByCampaignId(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getComment()).isEqualTo("Comment with non-existent company");
    assertThat(result.get(0).getBusinessType()).isNull(); // Should be null when company not found

    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(campaignCommentsRepository).findByCampaignId(campaignId);
    verify(companyService).getCompanyLookupWithCompanyId("nonexistent");
  }

  @Test
  void getCommentsByCampaignId_WithCompanyServiceException_ShouldReturnNullBusinessType() {
    // Given
    String campaignId = "campaign123";
    LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

    CampaignComments comment =
        CampaignComments.builder()
            .comment("Comment with company service error")
            .fileUrls(new ArrayList<>())
            .taggedCompanyIds(new ArrayList<>())
            .campaignId(campaignId)
            .companyId("company123")
            .build();
    comment.setId("comment1");
    comment.setCreatedBy("user@example.com");
    comment.setCreatedAt(createdAt);

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(testCampaign));
    when(campaignCommentsRepository.findByCampaignId(campaignId)).thenReturn(List.of(comment));
    when(companyService.getCompanyLookupWithCompanyId("company123"))
        .thenThrow(new RuntimeException("Database connection error"));

    // When
    List<CampaignCommentsResponseDTO> result = campaignService.getCommentsByCampaignId(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getComment()).isEqualTo("Comment with company service error");
    assertThat(result.get(0).getBusinessType()).isNull(); // Should be null when exception occurs

    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(campaignCommentsRepository).findByCampaignId(campaignId);
    verify(companyService).getCompanyLookupWithCompanyId("company123");
  }

  @Test
  void getCommentsByCampaignId_WithMultipleCommentsDifferentBusinessTypes_ShouldReturnAll() {
    // Given
    String campaignId = "campaign123";
    LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

    CampaignComments comment1 =
        CampaignComments.builder()
            .comment("Comment from media buyer")
            .campaignId(campaignId)
            .companyId("company1")
            .build();
    comment1.setId("comment1");
    comment1.setCreatedBy("buyer@example.com");
    comment1.setCreatedAt(createdAt);

    CampaignComments comment2 =
        CampaignComments.builder()
            .comment("Comment from media owner")
            .campaignId(campaignId)
            .companyId("company2")
            .build();
    comment2.setId("comment2");
    comment2.setCreatedBy("owner@example.com");
    comment2.setCreatedAt(createdAt.plusHours(1));

    CampaignComments comment3 =
        CampaignComments.builder()
            .comment("Comment from media operator")
            .campaignId(campaignId)
            .companyId("company3")
            .build();
    comment3.setId("comment3");
    comment3.setCreatedBy("operator@example.com");
    comment3.setCreatedAt(createdAt.plusHours(2));

    CompanyLookupResponseDTO companyLookup1 =
        CompanyLookupResponseDTO.builder().id("company1").companyType("MEDIA_BUYER").build();
    CompanyLookupResponseDTO companyLookup2 =
        CompanyLookupResponseDTO.builder().id("company2").companyType("MEDIA_OWNER").build();
    CompanyLookupResponseDTO companyLookup3 =
        CompanyLookupResponseDTO.builder().id("company3").companyType("MEDIA_OPERATOR").build();

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(testCampaign));
    when(campaignCommentsRepository.findByCampaignId(campaignId))
        .thenReturn(List.of(comment1, comment2, comment3));
    when(companyService.getCompanyLookupWithCompanyId("company1")).thenReturn(companyLookup1);
    when(companyService.getCompanyLookupWithCompanyId("company2")).thenReturn(companyLookup2);
    when(companyService.getCompanyLookupWithCompanyId("company3")).thenReturn(companyLookup3);

    // When
    List<CampaignCommentsResponseDTO> result = campaignService.getCommentsByCampaignId(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(3);
    assertThat(result.get(0).getBusinessType()).isEqualTo(CompanyDto.BusinessType.MEDIA_BUYER);
    assertThat(result.get(1).getBusinessType()).isEqualTo(CompanyDto.BusinessType.MEDIA_OWNER);
    assertThat(result.get(2).getBusinessType()).isEqualTo(CompanyDto.BusinessType.MEDIA_OPERATOR);

    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(campaignCommentsRepository).findByCampaignId(campaignId);
    verify(companyService).getCompanyLookupWithCompanyId("company1");
    verify(companyService).getCompanyLookupWithCompanyId("company2");
    verify(companyService).getCompanyLookupWithCompanyId("company3");
  }

  @Test
  void getCommentsByCampaignId_WithSingleComment_ShouldReturnSingleComment() {
    // Given
    String campaignId = "campaign123";
    LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

    CampaignComments comment =
        CampaignComments.builder()
            .comment("Single comment")
            .fileUrls(List.of("s3://bucket/file.pdf"))
            .taggedCompanyIds(List.of("company456"))
            .campaignId(campaignId)
            .companyId("company123")
            .build();
    comment.setId("comment1");
    comment.setCreatedBy("user@example.com");
    comment.setCreatedAt(createdAt);

    CompanyLookupResponseDTO companyLookup =
        CompanyLookupResponseDTO.builder().id("company123").companyType("MEDIA_AGENCY").build();

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(testCampaign));
    when(campaignCommentsRepository.findByCampaignId(campaignId)).thenReturn(List.of(comment));
    when(companyService.getCompanyLookupWithCompanyId("company123")).thenReturn(companyLookup);

    // When
    List<CampaignCommentsResponseDTO> result = campaignService.getCommentsByCampaignId(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getComment()).isEqualTo("Single comment");
    assertThat(result.get(0).getCreatedBy()).isEqualTo("user@example.com");
    assertThat(result.get(0).getBusinessType()).isEqualTo(CompanyDto.BusinessType.MEDIA_AGENCY);

    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(campaignCommentsRepository).findByCampaignId(campaignId);
    verify(companyService).getCompanyLookupWithCompanyId("company123");
  }

  @Test
  void getCommentsByCampaignId_WithMixedCommentsWithAndWithoutCompanyId_ShouldHandleBoth() {
    // Given
    String campaignId = "campaign123";
    LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

    CampaignComments comment1 =
        CampaignComments.builder()
            .comment("Comment with companyId")
            .campaignId(campaignId)
            .companyId("company123")
            .build();
    comment1.setId("comment1");
    comment1.setCreatedBy("user1@example.com");
    comment1.setCreatedAt(createdAt);

    CampaignComments comment2 =
        CampaignComments.builder()
            .comment("Comment without companyId")
            .campaignId(campaignId)
            .companyId(null)
            .build();
    comment2.setId("comment2");
    comment2.setCreatedBy("user2@example.com");
    comment2.setCreatedAt(createdAt.plusHours(1));

    CompanyLookupResponseDTO companyLookup =
        CompanyLookupResponseDTO.builder().id("company123").companyType("MEDIA_BUYER").build();

    when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(testCampaign));
    when(campaignCommentsRepository.findByCampaignId(campaignId))
        .thenReturn(List.of(comment1, comment2));
    when(companyService.getCompanyLookupWithCompanyId("company123")).thenReturn(companyLookup);

    // When
    List<CampaignCommentsResponseDTO> result = campaignService.getCommentsByCampaignId(campaignId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getBusinessType()).isEqualTo(CompanyDto.BusinessType.MEDIA_BUYER);
    assertThat(result.get(1).getBusinessType()).isNull();

    verify(campaignRepository, atLeastOnce()).findById(campaignId);
    verify(campaignCommentsRepository).findByCampaignId(campaignId);
    verify(companyService).getCompanyLookupWithCompanyId("company123");
  }

  // ========== getCampaignCostSplitBy (cost-split cluster + getSplitFieldValue arms) ==========

  private Campaign costSplitCampaign() {
    Campaign campaign =
        Campaign.builder()
            .name("Cost Split Campaign")
            .startDate(LocalDate.of(2026, 1, 1))
            .endDate(LocalDate.of(2026, 1, 31))
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();
    campaign.setId("camp1");
    campaign.setCompanyAccess(null); // ensures isMediaOwner=false -> stored snapshot is used
    campaign.setPerformance(
        CampaignForecastDTO.builder()
            .sov(1.0)
            .plannedSot(1.0)
            .totalSot(1.0)
            .totalInventories(1)
            .estimatedImpression(500L)
            .estimatedReach(200L)
            .build());
    return campaign;
  }

  private void setupCostSplitCommon(Campaign campaign, Inventory inventory) {
    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("camp1")
            .mediaOwnerId("mo1")
            .inventoryId("inv1")
            .scheduleIds(List.of("s1"))
            .build();
    when(campaignRepository.existsById("camp1")).thenReturn(true);
    when(campaignRepository.findById("camp1")).thenReturn(Optional.of(campaign));
    IamUserContext ctx = new IamUserContext();
    ctx.setCompanyId("company123");
    when(userService.getIamUserContext()).thenReturn(ctx);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(configService.findByCampaignId("camp1")).thenReturn(List.of(cis));
    when(inventoryService.getById("inv1")).thenReturn(inventory);
    when(customFeeService.getActiveCustomFeesContextForCampaign(campaign)).thenReturn(null);
    when(scheduleRepository.findAllById(anyList())).thenReturn(Collections.emptyList());
    when(configService.calculateCampaignInventorySchedulesProposedPrice(
            any(), any(), any(), any(), any(), any()))
        .thenReturn(100.0);
  }

  @Test
  void getCampaignCostSplitBy_WhenCampaignNotFound_ThrowsCampaignNotFoundException() {
    when(campaignRepository.existsById("missing")).thenReturn(false);

    assertThatThrownBy(
            () ->
                campaignService.getCampaignCostSplitBy(
                    "missing", CostSplit.INVENTORY_TYPE, Locale.ENGLISH))
        .isInstanceOf(CampaignNotFoundException.class);
  }

  @Test
  void getCampaignCostSplitBy_WhenNoSchedules_ReturnsEmptyList() {
    Campaign campaign = costSplitCampaign();
    when(campaignRepository.existsById("camp1")).thenReturn(true);
    when(campaignRepository.findById("camp1")).thenReturn(Optional.of(campaign));
    IamUserContext ctx = new IamUserContext();
    ctx.setCompanyId("company123");
    when(userService.getIamUserContext()).thenReturn(ctx);
    when(configService.findByCampaignId("camp1")).thenReturn(Collections.emptyList());

    assertThat(
            campaignService.getCampaignCostSplitBy(
                "camp1", CostSplit.INVENTORY_TYPE, Locale.ENGLISH))
        .isEmpty();
  }

  @Test
  void getCampaignCostSplitBy_ByInventoryType_GroupsByType() {
    Campaign campaign = costSplitCampaign();
    Inventory inventory = new Inventory();
    inventory.setId("inv1");
    inventory.setType("DIGITAL");
    setupCostSplitCommon(campaign, inventory);

    List<CostSplitByResponseDTO> result =
        campaignService.getCampaignCostSplitBy("camp1", CostSplit.INVENTORY_TYPE, Locale.ENGLISH);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("DIGITAL");
    assertThat(result.get(0).getTotalAmount()).isEqualTo(100.0);
    assertThat(result.get(0).getTotalAmountInPercentage()).isEqualTo(100.0);
    assertThat(result.get(0).getTotalInventories()).isEqualTo(1);
    assertThat(result.get(0).getPopulation())
        .isNull(); // INVENTORY_TYPE does not support population
  }

  @Test
  void getCampaignCostSplitBy_ByMediaOwner_ResolvesCompanyName() {
    Campaign campaign = costSplitCampaign();
    Inventory inventory = new Inventory();
    inventory.setId("inv1");
    inventory.setMediaOwnerId("mo1");
    setupCostSplitCommon(campaign, inventory);
    when(companyService.getCompanyLookupWithCompanyId("mo1"))
        .thenReturn(CompanyLookupResponseDTO.builder().id("mo1").name("Owner Co").build());

    List<CostSplitByResponseDTO> result =
        campaignService.getCampaignCostSplitBy("camp1", CostSplit.MEDIA_OWNER, Locale.ENGLISH);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("Owner Co");
  }

  @Test
  void getCampaignCostSplitBy_ByMediaOwner_WhenLookupFails_FallsBackToId() {
    Campaign campaign = costSplitCampaign();
    Inventory inventory = new Inventory();
    inventory.setId("inv1");
    inventory.setMediaOwnerId("mo1");
    setupCostSplitCommon(campaign, inventory);
    when(companyService.getCompanyLookupWithCompanyId("mo1"))
        .thenThrow(new RuntimeException("lookup failed"));

    List<CostSplitByResponseDTO> result =
        campaignService.getCampaignCostSplitBy("camp1", CostSplit.MEDIA_OWNER, Locale.ENGLISH);

    assertThat(result.get(0).getName()).isEqualTo("mo1");
  }

  @Test
  void getCampaignCostSplitBy_BySize_UsesFirstPanelSize() {
    Campaign campaign = costSplitCampaign();
    Inventory inventory = new Inventory();
    inventory.setId("inv1");
    inventory.setSize("48x14");
    setupCostSplitCommon(campaign, inventory);

    List<CostSplitByResponseDTO> result =
        campaignService.getCampaignCostSplitBy("camp1", CostSplit.SIZE, Locale.ENGLISH);

    assertThat(result.get(0).getName()).isEqualTo("48x14");
  }

  @Test
  void getCampaignCostSplitBy_ByVenueType_UsesLastVenueType() {
    Campaign campaign = costSplitCampaign();
    Inventory inventory = new Inventory();
    inventory.setId("inv1");
    inventory.setVenueType(List.of("outdoor", "transit"));
    setupCostSplitCommon(campaign, inventory);
    when(venuesService.getLocalizedVenueName("transit", Locale.ENGLISH)).thenReturn("transit");

    List<CostSplitByResponseDTO> result =
        campaignService.getCampaignCostSplitBy("camp1", CostSplit.VENUE_TYPE, Locale.ENGLISH);

    assertThat(result.get(0).getName()).isEqualTo("transit");
  }

  @Test
  void getCampaignCostSplitBy_ByCountry_GroupsByLocationCountryAndLooksUpPopulation() {
    Campaign campaign = costSplitCampaign();
    Inventory inventory = new Inventory();
    inventory.setId("inv1");
    Inventory.Location location = new Inventory.Location();
    location.setCountry("Singapore");
    inventory.setLocation(location);
    setupCostSplitCommon(campaign, inventory);
    when(countryService.findByName("Singapore")).thenReturn(Optional.empty());

    List<CostSplitByResponseDTO> result =
        campaignService.getCampaignCostSplitBy("camp1", CostSplit.COUNTRY, Locale.ENGLISH);

    assertThat(result.get(0).getName()).isEqualTo("Singapore");
    assertThat(result.get(0).getPopulation()).isNull();
  }

  @Test
  void getCampaignCostSplitBy_WhenSplitValueMissing_GroupsUnderUnknown() {
    Campaign campaign = costSplitCampaign();
    Inventory inventory = new Inventory();
    inventory.setId("inv1");
    inventory.setType(null); // INVENTORY_TYPE yields null -> "Unknown"
    setupCostSplitCommon(campaign, inventory);

    List<CostSplitByResponseDTO> result =
        campaignService.getCampaignCostSplitBy("camp1", CostSplit.INVENTORY_TYPE, Locale.ENGLISH);

    assertThat(result.get(0).getName()).isEqualTo("Unknown");
  }

  // ========== backfillPlanNumbers ==========

  private Campaign.CampaignBuilder legacyCampaignBuilder(String name, LocalDate date) {
    return Campaign.builder()
        .name(name)
        .startDate(date)
        .endDate(date)
        .userId("user123")
        .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
        .companyId("company123");
  }

  @Test
  void backfillPlanNumbers_DatesEachCampaignByItsOwnCreatedAt_NotByToday() {
    LocalDate oldDate = LocalDate.now().minusYears(1);
    Campaign legacy = legacyCampaignBuilder("Legacy", oldDate).build();
    legacy.setId("legacy1");
    legacy.setCreatedAt(oldDate.atStartOfDay());

    when(campaignRepository.findByPlanNumberIsNull(null, 500)).thenReturn(List.of(legacy));
    when(campaignRepository.findByPlanNumberIsNull("legacy1", 500)).thenReturn(List.of());
    when(sequencerService.getNextSequenceAtomic(anyString())).thenReturn(3L);
    when(campaignRepository.setPlanNumberIfNull(eq("legacy1"), anyString())).thenReturn(true);

    PlanNumberBackfillResultDTO result = campaignService.backfillPlanNumbers(500);

    String expectedDatePrefix =
        oldDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    String expectedPlanNumber = expectedDatePrefix + "0003";

    assertThat(result.getProcessed()).isEqualTo(1);
    assertThat(result.getAssigned()).isEqualTo(1);
    verify(sequencerService).getNextSequenceAtomic("PLAN_" + expectedDatePrefix);
    verify(campaignRepository).setPlanNumberIfNull("legacy1", expectedPlanNumber);
  }

  @Test
  void backfillPlanNumbers_FallsBackToStartDate_WhenCreatedAtMissing() {
    LocalDate startDate = LocalDate.now().minusMonths(6);
    Campaign legacy = legacyCampaignBuilder("Legacy", startDate).build();
    legacy.setId("legacy2");
    legacy.setCreatedAt(null);

    when(campaignRepository.findByPlanNumberIsNull(null, 500)).thenReturn(List.of(legacy));
    when(campaignRepository.findByPlanNumberIsNull("legacy2", 500)).thenReturn(List.of());
    when(sequencerService.getNextSequenceAtomic(anyString())).thenReturn(1L);
    when(campaignRepository.setPlanNumberIfNull(eq("legacy2"), anyString())).thenReturn(true);

    campaignService.backfillPlanNumbers(500);

    String expectedDatePrefix =
        startDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    verify(sequencerService).getNextSequenceAtomic("PLAN_" + expectedDatePrefix);
  }

  @Test
  void backfillPlanNumbers_PaginatesAcrossMultipleBatches() {
    LocalDate today = LocalDate.now();
    Campaign first = legacyCampaignBuilder("First", today).build();
    first.setId("id1");
    first.setCreatedAt(today.atStartOfDay());
    Campaign second = legacyCampaignBuilder("Second", today).build();
    second.setId("id2");
    second.setCreatedAt(today.atStartOfDay());

    when(campaignRepository.findByPlanNumberIsNull(null, 1)).thenReturn(List.of(first));
    when(campaignRepository.findByPlanNumberIsNull("id1", 1)).thenReturn(List.of(second));
    when(campaignRepository.findByPlanNumberIsNull("id2", 1)).thenReturn(List.of());
    when(sequencerService.getNextSequenceAtomic(anyString())).thenReturn(1L, 2L);
    when(campaignRepository.setPlanNumberIfNull(anyString(), anyString())).thenReturn(true);

    PlanNumberBackfillResultDTO result = campaignService.backfillPlanNumbers(1);

    assertThat(result.getProcessed()).isEqualTo(2);
    assertThat(result.getAssigned()).isEqualTo(2);
    verify(campaignRepository, times(3)).findByPlanNumberIsNull(any(), eq(1));
  }

  @Test
  void backfillPlanNumbers_WhenAlreadyAssignedConcurrently_DoesNotCountAsAssigned() {
    // setPlanNumberIfNull returning false simulates a race with normal campaign creation, which
    // already set a planNumber between the find and the conditional update.
    Campaign legacy = legacyCampaignBuilder("Legacy", LocalDate.now()).build();
    legacy.setId("legacy3");
    legacy.setCreatedAt(LocalDate.now().atStartOfDay());

    when(campaignRepository.findByPlanNumberIsNull(null, 500)).thenReturn(List.of(legacy));
    when(campaignRepository.findByPlanNumberIsNull("legacy3", 500)).thenReturn(List.of());
    when(sequencerService.getNextSequenceAtomic(anyString())).thenReturn(1L);
    when(campaignRepository.setPlanNumberIfNull(eq("legacy3"), anyString())).thenReturn(false);

    PlanNumberBackfillResultDTO result = campaignService.backfillPlanNumbers(500);

    assertThat(result.getProcessed()).isEqualTo(1);
    assertThat(result.getAssigned()).isEqualTo(0);
  }

  @Test
  void backfillPlanNumbers_WhenNothingMissing_ReturnsZeroCounts() {
    when(campaignRepository.findByPlanNumberIsNull(null, 500)).thenReturn(List.of());

    PlanNumberBackfillResultDTO result = campaignService.backfillPlanNumbers(500);

    assertThat(result.getProcessed()).isEqualTo(0);
    assertThat(result.getAssigned()).isEqualTo(0);
    verify(sequencerService, never()).getNextSequenceAtomic(anyString());
  }
}
