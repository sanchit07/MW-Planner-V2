package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.CustomFee;
import com.mw.planner.dto.BulkCustomFeeRequestDTO;
import com.mw.planner.dto.CompanyCustomFees;
import com.mw.planner.dto.CustomFeeRequestDTO;
import com.mw.planner.dto.CustomFeeResponseDTO;
import com.mw.planner.dto.CustomFeesContext;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.enums.CustomFeeBasedOn;
import com.mw.planner.enums.CustomFeeType;
import com.mw.planner.exception.customfee.CustomFeeAlreadyExistsException;
import com.mw.planner.exception.customfee.CustomFeeCreationException;
import com.mw.planner.exception.customfee.CustomFeeNotFoundException;
import com.mw.planner.exception.customfee.CustomFeeUpdateException;
import com.mw.planner.exception.customfee.CustomFeeValidationException;
import com.mw.planner.repository.CampaignInventorySchedulesRepository;
import com.mw.planner.repository.CustomFeeRepository;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomFeeServiceTest {

  @Mock private CustomFeeRepository customFeeRepository;
  @Mock private UserService userService;
  @Mock private CampaignService campaignService;
  @Mock private CampaignInventorySchedulesRepository campaignInventorySchedulesRepository;

  @InjectMocks private CustomFeeService customFeeService;

  private CustomFee testCustomFee;
  private CustomFeeRequestDTO testCustomFeeRequestDTO;
  private IamUserContext testUserContext;

  @BeforeEach
  void setUp() {
    testCustomFee = new CustomFee();
    testCustomFee.setId("fee_123456");
    testCustomFee.setName("Service Fee");
    testCustomFee.setDescription("Service fee for campaign management");
    testCustomFee.setType(CustomFeeType.PERCENTAGE);
    testCustomFee.setValue(10.5);
    testCustomFee.setBasedOn(CustomFeeBasedOn.BASE_COST);
    testCustomFee.setIsIncludeInMediaPlan(true);
    testCustomFee.setIsActive(true);
    testCustomFee.setCompanyId("company123");
    testCustomFee.setCampaignId("campaign123");
    testCustomFee.setCreatedAt(LocalDateTime.now());
    testCustomFee.setUpdatedAt(LocalDateTime.now());

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

    testUserContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .locale(Locale.ENGLISH)
            .build();
  }

  @AfterEach
  void tearDown() {
    // Reset mocks to clear any interactions for next test
    reset(customFeeRepository, userService, campaignService, campaignInventorySchedulesRepository);
  }

  // ========== getCustomFeeById Tests ==========

  @Test
  void getCustomFeeById_WithValidId_ShouldReturnCustomFee() {
    // Given
    when(customFeeRepository.findById("fee_123456")).thenReturn(Optional.of(testCustomFee));

    // When
    CustomFeeResponseDTO result = customFeeService.getCustomFeeById("fee_123456");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("fee_123456");
    assertThat(result.getName()).isEqualTo("Service Fee");
    assertThat(result.getType()).isEqualTo(CustomFeeType.PERCENTAGE);
    assertThat(result.getValue()).isEqualTo(10.5);

    verify(customFeeRepository).findById("fee_123456");
  }

  @Test
  void getCustomFeeById_WithInvalidId_ShouldThrowException() {
    // Given
    when(customFeeRepository.findById("invalid123")).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> customFeeService.getCustomFeeById("invalid123"))
        .isInstanceOf(CustomFeeNotFoundException.class)
        .hasMessageContaining("invalid123");

    verify(customFeeRepository).findById("invalid123");
  }

  // ========== getCustomFeesByCompanyAndCampaign Tests ==========

  @Test
  void getCustomFeesByCompanyAndCampaign_WithValidParams_ShouldReturnCustomFees() {
    // Given
    List<CustomFee> customFees = Arrays.asList(testCustomFee);
    when(customFeeRepository.findByCompanyIdAndCampaignId("company123", "campaign123"))
        .thenReturn(customFees);

    // When
    List<CustomFeeResponseDTO> result =
        customFeeService.getCustomFeesByCompanyAndCampaign("company123", "campaign123");

    // Then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo("fee_123456");
    assertThat(result.get(0).getName()).isEqualTo("Service Fee");

    verify(customFeeRepository).findByCompanyIdAndCampaignId("company123", "campaign123");
  }

  @Test
  void getCustomFeesByCompanyAndCampaign_WithNoResults_ShouldReturnEmptyList() {
    // Given
    when(customFeeRepository.findByCompanyIdAndCampaignId("company123", "campaign123"))
        .thenReturn(Collections.emptyList());

    // When
    List<CustomFeeResponseDTO> result =
        customFeeService.getCustomFeesByCompanyAndCampaign("company123", "campaign123");

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();

    verify(customFeeRepository).findByCompanyIdAndCampaignId("company123", "campaign123");
  }

  // ========== getActiveCustomFeesContextForCampaign Tests ==========

  @Test
  void getActiveCustomFeesContextForCampaign_WithNullCampaign_ShouldReturnEmptyContext() {
    CustomFeesContext result = customFeeService.getActiveCustomFeesContextForCampaign(null);
    assertThat(result).isNotNull();
    assertThat(result.getCompanyFeesByCompanyId()).isEmpty();
    assertThat(result.getCampaignFeesByCompanyId()).isEmpty();
    verify(customFeeRepository, never())
        .findByCompanyIdInAndCampaignIdIsNullAndIsActiveTrue(anyList());
    verify(customFeeRepository, never())
        .findByCampaignIdAndCompanyIdInAndIsActiveTrue(anyString(), anyList());
  }

  @Test
  void
      getActiveCustomFeesContextForCampaign_WithBlankCompanyIdAndNoCompanyAccess_ShouldReturnEmptyContext() {
    Campaign campaign = new Campaign();
    campaign.setId("campaign123");
    campaign.setCompanyId("");
    campaign.setCompanyAccess(null);

    CustomFeesContext result = customFeeService.getActiveCustomFeesContextForCampaign(campaign);
    assertThat(result).isNotNull();
    assertThat(result.getCompanyFeesByCompanyId()).isEmpty();
    assertThat(result.getCampaignFeesByCompanyId()).isEmpty();
    verify(customFeeRepository, never())
        .findByCompanyIdInAndCampaignIdIsNullAndIsActiveTrue(anyList());
    verify(customFeeRepository, never())
        .findByCampaignIdAndCompanyIdInAndIsActiveTrue(anyString(), anyList());
  }

  @Test
  void getActiveCustomFeesContextForCampaign_WithCompanyId_ShouldCallBatchRepositoryMethods() {
    Campaign campaign = new Campaign();
    campaign.setId("campaign123");
    campaign.setCompanyId("company123");
    campaign.setCompanyAccess(null);

    when(customFeeRepository.findByCompanyIdInAndCampaignIdIsNullAndIsActiveTrue(
            List.of("company123")))
        .thenReturn(Collections.emptyList());
    when(customFeeRepository.findByCampaignIdAndCompanyIdInAndIsActiveTrue(
            "campaign123", List.of("company123")))
        .thenReturn(Collections.emptyList());

    CustomFeesContext result = customFeeService.getActiveCustomFeesContextForCampaign(campaign);
    assertThat(result).isNotNull();
    assertThat(result.getCompanyFeesByCompanyId()).isEmpty();
    assertThat(result.getCampaignFeesByCompanyId()).isEmpty();

    verify(customFeeRepository)
        .findByCompanyIdInAndCampaignIdIsNullAndIsActiveTrue(List.of("company123"));
    verify(customFeeRepository)
        .findByCampaignIdAndCompanyIdInAndIsActiveTrue("campaign123", List.of("company123"));
  }

  @Test
  void
      getActiveCustomFeesContextForCampaign_WithCompanyIdAndCompanyAccess_ShouldIncludeAllCompanyIds() {
    Campaign campaign = new Campaign();
    campaign.setId("campaign123");
    campaign.setCompanyId("company123");
    campaign.setCompanyAccess(List.of("mediaOwner1", "mediaOwner2"));

    when(customFeeRepository.findByCompanyIdInAndCampaignIdIsNullAndIsActiveTrue(
            List.of("company123", "mediaOwner1", "mediaOwner2")))
        .thenReturn(List.of(testCustomFee));
    when(customFeeRepository.findByCampaignIdAndCompanyIdInAndIsActiveTrue(
            "campaign123", List.of("company123", "mediaOwner1", "mediaOwner2")))
        .thenReturn(Collections.emptyList());

    CustomFeesContext result = customFeeService.getActiveCustomFeesContextForCampaign(campaign);
    assertThat(result).isNotNull();
    assertThat(result.getCompanyFeesByCompanyId()).containsKey("company123");
    CompanyCustomFees companyFees = result.getCompanyFeesByCompanyId().get("company123");
    assertThat(companyFees).isNotNull();
    assertThat(companyFees.getHidden()).isEmpty();
    assertThat(companyFees.getVisible()).hasSize(1);
    assertThat(companyFees.getVisible().get(0).getId()).isEqualTo("fee_123456");

    verify(customFeeRepository)
        .findByCompanyIdInAndCampaignIdIsNullAndIsActiveTrue(
            List.of("company123", "mediaOwner1", "mediaOwner2"));
    verify(customFeeRepository)
        .findByCampaignIdAndCompanyIdInAndIsActiveTrue(
            "campaign123", List.of("company123", "mediaOwner1", "mediaOwner2"));
  }

  // ========== createCustomFee Tests ==========

  @Test
  void createCustomFee_WithValidData_ShouldCreateCustomFee() {
    // Given - campaign fee: companyId from user, campaignId from request
    when(userService.getActingCompanyId()).thenReturn("company123");
    when(customFeeRepository.existsByNameAndCompanyIdAndCampaignId(
            "Service Fee", "company123", "campaign123"))
        .thenReturn(false);
    when(customFeeRepository.save(any(CustomFee.class))).thenReturn(testCustomFee);
    when(campaignInventorySchedulesRepository.findByCampaignId("campaign123"))
        .thenReturn(Collections.emptyList());

    // When
    CustomFeeResponseDTO result = customFeeService.createCustomFee(testCustomFeeRequestDTO);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("fee_123456");
    assertThat(result.getName()).isEqualTo("Service Fee");
    assertThat(result.getType()).isEqualTo(CustomFeeType.PERCENTAGE);
    assertThat(result.getValue()).isEqualTo(10.5);

    verify(customFeeRepository)
        .existsByNameAndCompanyIdAndCampaignId("Service Fee", "company123", "campaign123");
    verify(customFeeRepository).save(any(CustomFee.class));
  }

  @Test
  void createCustomFee_WithCompanyLevel_ShouldUsePrimaryCompanyId() {
    // Given - company fee: campaignId null
    CustomFeeRequestDTO companyRequest =
        CustomFeeRequestDTO.builder()
            .name("Company Service Fee")
            .description("Company fee")
            .type(CustomFeeType.PERCENTAGE)
            .value(5.0)
            .basedOn(CustomFeeBasedOn.BASE_COST)
            .isIncludeInMediaPlan(true)
            .isActive(true)
            .campaignId(null)
            .build();

    when(userService.getActingCompanyId()).thenReturn("company123");
    when(customFeeRepository.existsByNameAndCompanyIdAndCampaignId(
            "Company Service Fee", "company123", null))
        .thenReturn(false);
    when(customFeeRepository.save(any(CustomFee.class))).thenReturn(testCustomFee);

    // When
    CustomFeeResponseDTO result = customFeeService.createCustomFee(companyRequest);

    // Then
    assertThat(result).isNotNull();
    verify(userService).getActingCompanyId();
    verify(customFeeRepository)
        .existsByNameAndCompanyIdAndCampaignId("Company Service Fee", "company123", null);
  }

  @Test
  void createCustomFee_WithAlreadyExists_ShouldThrowException() {
    // Given
    when(userService.getActingCompanyId()).thenReturn("company123");
    when(customFeeRepository.existsByNameAndCompanyIdAndCampaignId(
            "Service Fee", "company123", "campaign123"))
        .thenReturn(true);

    // When & Then
    assertThatThrownBy(() -> customFeeService.createCustomFee(testCustomFeeRequestDTO))
        .isInstanceOf(CustomFeeAlreadyExistsException.class)
        .hasMessageContaining("Service Fee");

    verify(customFeeRepository)
        .existsByNameAndCompanyIdAndCampaignId("Service Fee", "company123", "campaign123");
    verify(customFeeRepository, never()).save(any(CustomFee.class));
  }

  @Test
  void createCustomFee_WithNoPrimaryCompanyId_ShouldThrowException() {
    // Given - company-level fee (campaignId null) but user has no primary company
    CustomFeeRequestDTO companyRequest =
        CustomFeeRequestDTO.builder()
            .name("Company Service Fee")
            .type(CustomFeeType.PERCENTAGE)
            .value(5.0)
            .basedOn(CustomFeeBasedOn.BASE_COST)
            .campaignId(null)
            .build();

    when(userService.getActingCompanyId()).thenReturn(null);

    // When & Then
    assertThatThrownBy(() -> customFeeService.createCustomFee(companyRequest))
        .isInstanceOf(CustomFeeValidationException.class)
        .hasMessageContaining("primary company ID");

    verify(userService).getActingCompanyId();
    verify(customFeeRepository, never()).save(any(CustomFee.class));
  }

  @Test
  void createCustomFee_WithCampaignEntity_ShouldResetCampaignApprovals() {
    // Given
    CampaignInventorySchedules schedule1 = new CampaignInventorySchedules();
    schedule1.setId("cis1");
    schedule1.setCampaignId("campaign123");
    schedule1.setApprovedScheduleIds(Arrays.asList("s1", "s2"));
    schedule1.setApprovedBy("user123");

    CampaignInventorySchedules schedule2 = new CampaignInventorySchedules();
    schedule2.setId("cis2");
    schedule2.setCampaignId("campaign123");
    schedule2.setApprovedScheduleIds(Arrays.asList("s3"));
    schedule2.setApprovedBy("user123");

    when(userService.getActingCompanyId()).thenReturn("company123");
    when(customFeeRepository.existsByNameAndCompanyIdAndCampaignId(
            "Service Fee", "company123", "campaign123"))
        .thenReturn(false);
    when(customFeeRepository.save(any(CustomFee.class))).thenReturn(testCustomFee);
    when(campaignInventorySchedulesRepository.findByCampaignId("campaign123"))
        .thenReturn(Arrays.asList(schedule1, schedule2));
    when(campaignInventorySchedulesRepository.saveAll(anyList()))
        .thenReturn(Arrays.asList(schedule1, schedule2));

    // When
    CustomFeeResponseDTO result = customFeeService.createCustomFee(testCustomFeeRequestDTO);

    // Then
    assertThat(result).isNotNull();
    verify(campaignInventorySchedulesRepository).findByCampaignId("campaign123");
    verify(campaignInventorySchedulesRepository).saveAll(anyList());
    assertThat(schedule1.getApprovedScheduleIds()).isNull();
    assertThat(schedule1.getApprovedBy()).isNull();
    assertThat(schedule2.getApprovedScheduleIds()).isNull();
    assertThat(schedule2.getApprovedBy()).isNull();
  }

  @Test
  void createCustomFee_WithRepositoryException_ShouldThrowCreationException() {
    // Given
    when(userService.getActingCompanyId()).thenReturn("company123");
    when(customFeeRepository.existsByNameAndCompanyIdAndCampaignId(
            "Service Fee", "company123", "campaign123"))
        .thenReturn(false);
    when(customFeeRepository.save(any(CustomFee.class)))
        .thenThrow(new RuntimeException("Database error"));

    // When & Then
    assertThatThrownBy(() -> customFeeService.createCustomFee(testCustomFeeRequestDTO))
        .isInstanceOf(CustomFeeCreationException.class)
        .hasMessageContaining("Failed to create custom fee");

    verify(customFeeRepository).save(any(CustomFee.class));
  }

  // ========== updateCustomFee Tests ==========

  @Test
  void updateCustomFee_WithValidData_ShouldUpdateCustomFee() {
    // Given - existing fee has companyId and campaignId; update keeps same scope
    CustomFeeRequestDTO updateRequest =
        CustomFeeRequestDTO.builder()
            .name("Updated Service Fee")
            .description("Updated description")
            .type(CustomFeeType.PERCENTAGE)
            .value(15.0)
            .basedOn(CustomFeeBasedOn.BASE_COST)
            .isIncludeInMediaPlan(true)
            .isActive(true)
            .campaignId("campaign123")
            .build();

    CustomFee updatedCustomFee = new CustomFee();
    updatedCustomFee.setId("fee_123456");
    updatedCustomFee.setName("Updated Service Fee");
    updatedCustomFee.setValue(15.0);
    updatedCustomFee.setCompanyId("company123");
    updatedCustomFee.setCampaignId("campaign123");

    when(customFeeRepository.findById("fee_123456")).thenReturn(Optional.of(testCustomFee));
    when(customFeeRepository.existsByNameAndCompanyIdAndCampaignId(
            "Updated Service Fee", "company123", "campaign123"))
        .thenReturn(false);
    when(customFeeRepository.save(any(CustomFee.class))).thenReturn(updatedCustomFee);
    when(campaignInventorySchedulesRepository.findByCampaignId("campaign123"))
        .thenReturn(Collections.emptyList());

    // When
    CustomFeeResponseDTO result = customFeeService.updateCustomFee("fee_123456", updateRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("fee_123456");
    assertThat(result.getName()).isEqualTo("Updated Service Fee");
    assertThat(result.getValue()).isEqualTo(15.0);

    verify(customFeeRepository).findById("fee_123456");
    verify(customFeeRepository).save(any(CustomFee.class));
  }

  @Test
  void updateCustomFee_WithInvalidId_ShouldThrowException() {
    // Given
    when(customFeeRepository.findById("invalid123")).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(
            () -> customFeeService.updateCustomFee("invalid123", testCustomFeeRequestDTO))
        .isInstanceOf(CustomFeeNotFoundException.class)
        .hasMessageContaining("invalid123");

    verify(customFeeRepository).findById("invalid123");
    verify(customFeeRepository, never()).save(any(CustomFee.class));
  }

  @Test
  void updateCustomFee_WithSameName_ShouldNotCheckDuplicate() {
    // Given
    when(customFeeRepository.findById("fee_123456")).thenReturn(Optional.of(testCustomFee));
    when(customFeeRepository.save(any(CustomFee.class))).thenReturn(testCustomFee);
    when(campaignInventorySchedulesRepository.findByCampaignId("campaign123"))
        .thenReturn(Collections.emptyList());

    // When
    CustomFeeResponseDTO result =
        customFeeService.updateCustomFee("fee_123456", testCustomFeeRequestDTO);

    // Then
    assertThat(result).isNotNull();
    verify(customFeeRepository, never())
        .existsByNameAndCompanyIdAndCampaignId(anyString(), anyString(), any());
    verify(customFeeRepository).save(any(CustomFee.class));
  }

  @Test
  void updateCustomFee_WithNewNameAlreadyExists_ShouldThrowException() {
    // Given
    CustomFeeRequestDTO updateRequest =
        CustomFeeRequestDTO.builder()
            .name("Existing Fee Name")
            .type(CustomFeeType.PERCENTAGE)
            .value(10.5)
            .basedOn(CustomFeeBasedOn.BASE_COST)
            .campaignId("campaign123")
            .build();

    when(customFeeRepository.findById("fee_123456")).thenReturn(Optional.of(testCustomFee));
    when(customFeeRepository.existsByNameAndCompanyIdAndCampaignId(
            "Existing Fee Name", "company123", "campaign123"))
        .thenReturn(true);

    // When & Then
    assertThatThrownBy(() -> customFeeService.updateCustomFee("fee_123456", updateRequest))
        .isInstanceOf(CustomFeeAlreadyExistsException.class)
        .hasMessageContaining("Existing Fee Name");

    verify(customFeeRepository).findById("fee_123456");
    verify(customFeeRepository, never()).save(any(CustomFee.class));
  }

  @Test
  void updateCustomFee_WithRepositoryException_ShouldThrowUpdateException() {
    // Given
    when(customFeeRepository.findById("fee_123456")).thenReturn(Optional.of(testCustomFee));
    when(customFeeRepository.save(any(CustomFee.class)))
        .thenThrow(new RuntimeException("Database error"));

    // When & Then
    assertThatThrownBy(
            () -> customFeeService.updateCustomFee("fee_123456", testCustomFeeRequestDTO))
        .isInstanceOf(CustomFeeUpdateException.class)
        .hasMessageContaining("Failed to update custom fee");

    verify(customFeeRepository).findById("fee_123456");
    verify(customFeeRepository).save(any(CustomFee.class));
  }

  // ========== bulkCreateOrUpdateCustomFees Tests ==========

  @Test
  void bulkCreateOrUpdateCustomFees_WithValidData_ShouldCreateAndUpdate() {
    // Given
    BulkCustomFeeRequestDTO createRequest =
        BulkCustomFeeRequestDTO.builder()
            .id(null) // Create new
            .name("New Fee")
            .type(CustomFeeType.PERCENTAGE)
            .value(10.0)
            .basedOn(CustomFeeBasedOn.BASE_COST)
            .campaignId("campaign123")
            .build();

    BulkCustomFeeRequestDTO updateRequest =
        BulkCustomFeeRequestDTO.builder()
            .id("fee_123456") // Update existing
            .name("Updated Fee")
            .type(CustomFeeType.PERCENTAGE)
            .value(15.0)
            .basedOn(CustomFeeBasedOn.BASE_COST)
            .campaignId("campaign123")
            .build();

    List<BulkCustomFeeRequestDTO> bulkRequests = Arrays.asList(createRequest, updateRequest);

    CustomFee newCustomFee = new CustomFee();
    newCustomFee.setId("fee_new123");
    newCustomFee.setName("New Fee");

    when(userService.getActingCompanyId()).thenReturn("company123");
    when(customFeeRepository.findAllById(anyList())).thenReturn(Arrays.asList(testCustomFee));
    when(customFeeRepository.existsByNameAndCompanyIdAndCampaignId(anyString(), anyString(), any()))
        .thenReturn(false);
    when(customFeeRepository.findById("fee_123456")).thenReturn(Optional.of(testCustomFee));
    when(customFeeRepository.save(any(CustomFee.class)))
        .thenReturn(newCustomFee)
        .thenReturn(testCustomFee);
    when(campaignInventorySchedulesRepository.findByCampaignId("campaign123"))
        .thenReturn(Collections.emptyList());

    // When
    List<CustomFeeResponseDTO> result = customFeeService.bulkCreateOrUpdateCustomFees(bulkRequests);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(2);
    verify(customFeeRepository).findAllById(anyList());
  }

  @Test
  void bulkCreateOrUpdateCustomFees_WithEmptyList_ShouldThrowException() {
    // Given
    List<BulkCustomFeeRequestDTO> emptyList = Collections.emptyList();

    // When & Then
    assertThatThrownBy(() -> customFeeService.bulkCreateOrUpdateCustomFees(emptyList))
        .isInstanceOf(CustomFeeValidationException.class)
        .hasMessageContaining("cannot be empty");

    verify(customFeeRepository, never()).save(any(CustomFee.class));
  }

  @Test
  void bulkCreateOrUpdateCustomFees_WithInvalidIds_ShouldThrowException() {
    // Given
    BulkCustomFeeRequestDTO invalidRequest =
        BulkCustomFeeRequestDTO.builder()
            .id("invalid123")
            .name("Fee")
            .type(CustomFeeType.PERCENTAGE)
            .value(10.0)
            .basedOn(CustomFeeBasedOn.BASE_COST)
            .campaignId("campaign123")
            .build();

    when(customFeeRepository.findAllById(anyList())).thenReturn(Collections.emptyList());

    // When & Then
    assertThatThrownBy(
            () -> customFeeService.bulkCreateOrUpdateCustomFees(Arrays.asList(invalidRequest)))
        .isInstanceOf(CustomFeeValidationException.class)
        .hasMessageContaining("not found");

    verify(customFeeRepository).findAllById(anyList());
    verify(customFeeRepository, never()).save(any(CustomFee.class));
  }

  @Test
  void bulkCreateOrUpdateCustomFees_WithCampaignEntity_ShouldResetApprovalsOnce() {
    // Given
    BulkCustomFeeRequestDTO request1 =
        BulkCustomFeeRequestDTO.builder()
            .id(null)
            .name("Fee 1")
            .type(CustomFeeType.PERCENTAGE)
            .value(10.0)
            .basedOn(CustomFeeBasedOn.BASE_COST)
            .campaignId("campaign123")
            .build();

    BulkCustomFeeRequestDTO request2 =
        BulkCustomFeeRequestDTO.builder()
            .id(null)
            .name("Fee 2")
            .type(CustomFeeType.PERCENTAGE)
            .value(15.0)
            .basedOn(CustomFeeBasedOn.BASE_COST)
            .campaignId("campaign123")
            .build();

    List<BulkCustomFeeRequestDTO> bulkRequests = Arrays.asList(request1, request2);

    CustomFee fee1 = new CustomFee();
    fee1.setId("fee1");
    CustomFee fee2 = new CustomFee();
    fee2.setId("fee2");

    when(userService.getActingCompanyId()).thenReturn("company123");
    when(customFeeRepository.findAllById(anyList())).thenReturn(Collections.emptyList());
    when(customFeeRepository.existsByNameAndCompanyIdAndCampaignId(anyString(), anyString(), any()))
        .thenReturn(false);
    when(customFeeRepository.save(any(CustomFee.class))).thenReturn(fee1).thenReturn(fee2);
    when(campaignInventorySchedulesRepository.findByCampaignId("campaign123"))
        .thenReturn(Collections.emptyList());
    when(campaignInventorySchedulesRepository.saveAll(anyList()))
        .thenReturn(Collections.emptyList());

    // When
    List<CustomFeeResponseDTO> result = customFeeService.bulkCreateOrUpdateCustomFees(bulkRequests);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(2);
    // Should reset approvals only once for the campaign, even though both fees are for same
    // campaign
    verify(campaignInventorySchedulesRepository, times(1)).findByCampaignId("campaign123");
  }
}
