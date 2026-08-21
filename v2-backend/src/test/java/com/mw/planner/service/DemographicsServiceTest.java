package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.Demographics;
import com.mw.planner.dto.*;
import com.mw.planner.enums.DemographicsType;
import com.mw.planner.exception.demographics.DemographicsValidationException;
import com.mw.planner.repository.DemographicsRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemographicsServiceTest {

  @Mock private DemographicsRepository demographicsRepository;

  @Mock private VenuesService venuesService;

  @InjectMocks private DemographicsService demographicsService;

  private Demographics ageDemographic;
  private Demographics genderDemographic;
  private Demographics incomeDemographic;
  private Demographics interestDemographic;
  private Demographics behaviorDemographic;
  private VenueItemDTO venueItem;

  @BeforeEach
  void setUp() {
    // Create age demographic
    ageDemographic = new Demographics();
    ageDemographic.setId("age1");
    ageDemographic.setDemoType(DemographicsType.AGE);
    ageDemographic.setDemoKey("18-24");
    ageDemographic.setName("18-24 years");
    ageDemographic.setDescription("Young adults");
    ageDemographic.setCountryId("US");
    ageDemographic.setCreatedBy("user1");
    ageDemographic.setLastModifiedBy("user1");
    ageDemographic.setCreatedAt(LocalDateTime.now());
    ageDemographic.setUpdatedAt(LocalDateTime.now());

    // Create gender demographic
    genderDemographic = new Demographics();
    genderDemographic.setId("gender1");
    genderDemographic.setDemoType(DemographicsType.GENDER);
    genderDemographic.setDemoKey("male");
    genderDemographic.setName("Male");
    genderDemographic.setDescription("Male gender");
    genderDemographic.setCountryId("US");
    genderDemographic.setCreatedBy("user1");
    genderDemographic.setLastModifiedBy("user1");
    genderDemographic.setCreatedAt(LocalDateTime.now());
    genderDemographic.setUpdatedAt(LocalDateTime.now());

    // Create income demographic
    incomeDemographic = new Demographics();
    incomeDemographic.setId("income1");
    incomeDemographic.setDemoType(DemographicsType.INCOME);
    incomeDemographic.setDemoKey("high");
    incomeDemographic.setName("High Income");
    incomeDemographic.setDescription("High income bracket");
    incomeDemographic.setCountryId("US");
    incomeDemographic.setCreatedBy("user1");
    incomeDemographic.setLastModifiedBy("user1");
    incomeDemographic.setCreatedAt(LocalDateTime.now());
    incomeDemographic.setUpdatedAt(LocalDateTime.now());

    // Create interest demographic
    interestDemographic = new Demographics();
    interestDemographic.setId("interest1");
    interestDemographic.setDemoType(DemographicsType.INTEREST);
    interestDemographic.setDemoKey("sports");
    interestDemographic.setName("Sports");
    interestDemographic.setDescription("Sports interest");
    interestDemographic.setCountryId("US");
    interestDemographic.setCreatedBy("user1");
    interestDemographic.setLastModifiedBy("user1");
    interestDemographic.setCreatedAt(LocalDateTime.now());
    interestDemographic.setUpdatedAt(LocalDateTime.now());

    // Create behavior demographic
    behaviorDemographic = new Demographics();
    behaviorDemographic.setId("behavior1");
    behaviorDemographic.setDemoType(DemographicsType.BEHAVIOR);
    behaviorDemographic.setDemoKey("online_shopper");
    behaviorDemographic.setName("Online Shopper");
    behaviorDemographic.setDescription("Frequently shops online");
    behaviorDemographic.setCountryId("US");
    behaviorDemographic.setCreatedBy("user1");
    behaviorDemographic.setLastModifiedBy("user1");
    behaviorDemographic.setCreatedAt(LocalDateTime.now());
    behaviorDemographic.setUpdatedAt(LocalDateTime.now());

    // Create venue item
    venueItem = new VenueItemDTO();
    venueItem.setEnumerationId(101);
    venueItem.setTier(2);
    venueItem.setName("Test Venue");
    venueItem.setDefinition("Test Venue Description");
    venueItem.setStringValue("test_value");
    venueItem.setChildren(Collections.emptyList());
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(demographicsRepository, venuesService);
  }

  @Test
  void getDemographicsById_WhenDemographicExists_ShouldReturnDemographicsResponseDTO() {
    // Given
    when(demographicsRepository.findById("age1")).thenReturn(Optional.of(ageDemographic));

    // When
    DemographicsResponseDTO result = demographicsService.getDemographicsById("age1");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("age1");
    assertThat(result.getDemoType()).isEqualTo(DemographicsType.AGE);
    assertThat(result.getDemoKey()).isEqualTo("18-24");
    assertThat(result.getName()).isEqualTo("18-24 years");
    assertThat(result.getDescription()).isEqualTo("Young adults");
    assertThat(result.getCountryId()).isEqualTo("US");
    verify(demographicsRepository).findById("age1");
  }

  @Test
  void getDemographicsById_WhenDemographicNotFound_ShouldThrowException() {
    // Given
    when(demographicsRepository.findById("nonexistent")).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> demographicsService.getDemographicsById("nonexistent"))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Demographics not found with id: nonexistent");

    verify(demographicsRepository).findById("nonexistent");
  }

  @Test
  void getConfigDemographics_WithAllTypes_ShouldGroupCorrectly() {
    // Given
    List<Demographics> allDemographics =
        Arrays.asList(ageDemographic, genderDemographic, incomeDemographic);
    when(demographicsRepository.findAll()).thenReturn(allDemographics);

    // When
    DemographicsConfigResponseDTO result = demographicsService.getConfigDemographics();

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getAge()).hasSize(1);
    assertThat(result.getGender()).hasSize(1);
    assertThat(result.getIncome()).hasSize(1);

    assertThat(result.getAge().get(0).getDemoKey()).isEqualTo("18-24");
    assertThat(result.getAge().get(0).getName()).isEqualTo("18-24 years");
    assertThat(result.getGender().get(0).getDemoKey()).isEqualTo("male");
    assertThat(result.getGender().get(0).getName()).isEqualTo("Male");
    assertThat(result.getIncome().get(0).getDemoKey()).isEqualTo("high");
    assertThat(result.getIncome().get(0).getName()).isEqualTo("High Income");

    verify(demographicsRepository).findAll();
  }

  @Test
  void getConfigDemographics_WithEmptyDatabase_ShouldReturnEmptyGroups() {
    // Given
    when(demographicsRepository.findAll()).thenReturn(Collections.emptyList());

    // When
    DemographicsConfigResponseDTO result = demographicsService.getConfigDemographics();

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getAge()).isEmpty();
    assertThat(result.getGender()).isEmpty();
    assertThat(result.getIncome()).isEmpty();
    verify(demographicsRepository).findAll();
  }

  @Test
  void autoSaveDemographic_WithValidRequest_ShouldUpdateAndReturnDemographics() {
    // Given
    DemographicAutoSaveRequestDTO requestDTO = new DemographicAutoSaveRequestDTO();
    requestDTO.setDemoType("AGE");
    requestDTO.setDemoKey("18-24");
    requestDTO.setName("Updated Age Group");
    requestDTO.setDescription("Updated description");

    when(demographicsRepository.findByDemoTypeAndDemoKey(DemographicsType.AGE, "18-24"))
        .thenReturn(Optional.of(ageDemographic));
    when(demographicsRepository.save(any(Demographics.class))).thenReturn(ageDemographic);

    // When
    DemographicsResponseDTO result = demographicsService.autoSaveDemographic(requestDTO, "US");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("age1");
    assertThat(result.getDemoType()).isEqualTo(DemographicsType.AGE);
    assertThat(result.getDemoKey()).isEqualTo("18-24");
    assertThat(result.getName()).isEqualTo("Updated Age Group");
    assertThat(result.getDescription()).isEqualTo("Updated description");
    assertThat(result.getCountryId()).isEqualTo("US");

    verify(demographicsRepository).findByDemoTypeAndDemoKey(DemographicsType.AGE, "18-24");
    verify(demographicsRepository).save(ageDemographic);
  }

  @Test
  void autoSaveDemographic_WithInvalidDemoType_ShouldThrowException() {
    // Given
    DemographicAutoSaveRequestDTO requestDTO = new DemographicAutoSaveRequestDTO();
    requestDTO.setDemoType("INVALID_TYPE");
    requestDTO.setDemoKey("test");

    // When & Then
    assertThatThrownBy(() -> demographicsService.autoSaveDemographic(requestDTO, "US"))
        .isInstanceOf(DemographicsValidationException.class)
        .hasMessage("Invalid demoType: INVALID_TYPE");

    verify(demographicsRepository, never()).findByDemoTypeAndDemoKey(any(), any());
    verify(demographicsRepository, never()).save(any());
  }

  @Test
  void autoSaveDemographic_WithNullDemoKey_ShouldThrowException() {
    // Given
    DemographicAutoSaveRequestDTO requestDTO = new DemographicAutoSaveRequestDTO();
    requestDTO.setDemoType("AGE");
    requestDTO.setDemoKey(null);

    // When & Then
    assertThatThrownBy(() -> demographicsService.autoSaveDemographic(requestDTO, "US"))
        .isInstanceOf(DemographicsValidationException.class)
        .hasMessage("DemoKey cannot be null or empty");

    verify(demographicsRepository, never()).findByDemoTypeAndDemoKey(any(), any());
    verify(demographicsRepository, never()).save(any());
  }

  @Test
  void autoSaveDemographic_WithEmptyDemoKey_ShouldThrowException() {
    // Given
    DemographicAutoSaveRequestDTO requestDTO = new DemographicAutoSaveRequestDTO();
    requestDTO.setDemoType("AGE");
    requestDTO.setDemoKey("");

    // When & Then
    assertThatThrownBy(() -> demographicsService.autoSaveDemographic(requestDTO, "US"))
        .isInstanceOf(DemographicsValidationException.class)
        .hasMessage("DemoKey cannot be null or empty");

    verify(demographicsRepository, never()).findByDemoTypeAndDemoKey(any(), any());
    verify(demographicsRepository, never()).save(any());
  }

  @Test
  void autoSaveDemographic_WithWhitespaceDemoKey_ShouldThrowException() {
    // Given
    DemographicAutoSaveRequestDTO requestDTO = new DemographicAutoSaveRequestDTO();
    requestDTO.setDemoType("AGE");
    requestDTO.setDemoKey("   ");

    // When & Then
    assertThatThrownBy(() -> demographicsService.autoSaveDemographic(requestDTO, "US"))
        .isInstanceOf(DemographicsValidationException.class)
        .hasMessage("DemoKey cannot be null or empty");

    verify(demographicsRepository, never()).findByDemoTypeAndDemoKey(any(), any());
    verify(demographicsRepository, never()).save(any());
  }

  @Test
  void autoSaveDemographic_WhenDemographicNotFound_ShouldThrowException() {
    // Given
    DemographicAutoSaveRequestDTO requestDTO = new DemographicAutoSaveRequestDTO();
    requestDTO.setDemoType("AGE");
    requestDTO.setDemoKey("nonexistent");

    when(demographicsRepository.findByDemoTypeAndDemoKey(DemographicsType.AGE, "nonexistent"))
        .thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> demographicsService.autoSaveDemographic(requestDTO, "US"))
        .isInstanceOf(DemographicsValidationException.class)
        .hasMessage("Demographics not found with demoType: AGE and demoKey: nonexistent");

    verify(demographicsRepository).findByDemoTypeAndDemoKey(DemographicsType.AGE, "nonexistent");
    verify(demographicsRepository, never()).save(any());
  }

  @Test
  void autoSaveDemographic_WithOnlyNameUpdate_ShouldUpdateOnlyName() {
    // Given
    DemographicAutoSaveRequestDTO requestDTO = new DemographicAutoSaveRequestDTO();
    requestDTO.setDemoType("AGE");
    requestDTO.setDemoKey("18-24");
    requestDTO.setName("Updated Name Only");
    requestDTO.setDescription(null);

    when(demographicsRepository.findByDemoTypeAndDemoKey(DemographicsType.AGE, "18-24"))
        .thenReturn(Optional.of(ageDemographic));
    when(demographicsRepository.save(any(Demographics.class))).thenReturn(ageDemographic);

    // When
    DemographicsResponseDTO result = demographicsService.autoSaveDemographic(requestDTO, "US");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("Updated Name Only");
    assertThat(result.getDescription()).isEqualTo("Young adults"); // Original description unchanged

    verify(demographicsRepository).findByDemoTypeAndDemoKey(DemographicsType.AGE, "18-24");
    verify(demographicsRepository).save(ageDemographic);
  }

  @Test
  void autoSaveDemographic_WithOnlyDescriptionUpdate_ShouldUpdateOnlyDescription() {
    // Given
    DemographicAutoSaveRequestDTO requestDTO = new DemographicAutoSaveRequestDTO();
    requestDTO.setDemoType("AGE");
    requestDTO.setDemoKey("18-24");
    requestDTO.setName(null);
    requestDTO.setDescription("Updated Description Only");

    when(demographicsRepository.findByDemoTypeAndDemoKey(DemographicsType.AGE, "18-24"))
        .thenReturn(Optional.of(ageDemographic));
    when(demographicsRepository.save(any(Demographics.class))).thenReturn(ageDemographic);

    // When
    DemographicsResponseDTO result = demographicsService.autoSaveDemographic(requestDTO, "US");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("18-24 years"); // Original name unchanged
    assertThat(result.getDescription()).isEqualTo("Updated Description Only");

    verify(demographicsRepository).findByDemoTypeAndDemoKey(DemographicsType.AGE, "18-24");
    verify(demographicsRepository).save(ageDemographic);
  }

  @Test
  void autoSaveDemographic_WithCaseInsensitiveDemoType_ShouldWorkCorrectly() {
    // Given
    DemographicAutoSaveRequestDTO requestDTO = new DemographicAutoSaveRequestDTO();
    requestDTO.setDemoType("age"); // lowercase
    requestDTO.setDemoKey("18-24");
    requestDTO.setName("Updated Name");

    when(demographicsRepository.findByDemoTypeAndDemoKey(DemographicsType.AGE, "18-24"))
        .thenReturn(Optional.of(ageDemographic));
    when(demographicsRepository.save(any(Demographics.class))).thenReturn(ageDemographic);

    // When
    DemographicsResponseDTO result = demographicsService.autoSaveDemographic(requestDTO, "US");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("Updated Name");

    verify(demographicsRepository).findByDemoTypeAndDemoKey(DemographicsType.AGE, "18-24");
    verify(demographicsRepository).save(ageDemographic);
  }

  @Test
  void autoSaveDemographic_WithMixedCaseDemoType_ShouldWorkCorrectly() {
    // Given
    DemographicAutoSaveRequestDTO requestDTO = new DemographicAutoSaveRequestDTO();
    requestDTO.setDemoType("AgE"); // mixed case
    requestDTO.setDemoKey("18-24");
    requestDTO.setName("Updated Name");

    when(demographicsRepository.findByDemoTypeAndDemoKey(DemographicsType.AGE, "18-24"))
        .thenReturn(Optional.of(ageDemographic));
    when(demographicsRepository.save(any(Demographics.class))).thenReturn(ageDemographic);

    // When
    DemographicsResponseDTO result = demographicsService.autoSaveDemographic(requestDTO, "US");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("Updated Name");

    verify(demographicsRepository).findByDemoTypeAndDemoKey(DemographicsType.AGE, "18-24");
    verify(demographicsRepository).save(ageDemographic);
  }
}
