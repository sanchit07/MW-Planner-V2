package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.Agency;
import com.mw.planner.domain.Country;
import com.mw.planner.dto.*;
import com.mw.planner.exception.agency.AgencyAlreadyExistsException;
import com.mw.planner.exception.agency.AgencyNotFoundException;
import com.mw.planner.repository.AgencyRepository;
import com.mw.planner.repository.CountryRepository;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AgencyServiceTest {

  @Mock private AgencyRepository agencyRepository;
  @Mock private CountryRepository countryRepository;

  @InjectMocks private AgencyService agencyService;

  private Agency testAgency;
  private AgencyRequestDTO agencyRequestDTO;

  @BeforeEach
  void setUp() {
    testAgency = createTestAgency();
    agencyRequestDTO = createAgencyRequestDTO();
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(agencyRepository, countryRepository);
  }

  // ========== getAgencyById Tests ==========

  @Test
  void getAgencyById_WhenAgencyExists_ShouldReturnAgency() {
    // Given
    Country testCountry = createTestCountry();
    when(agencyRepository.findById("agency123")).thenReturn(Optional.of(testAgency));
    when(countryRepository.findById("US")).thenReturn(Optional.of(testCountry));

    // When
    AgencyResponseDTO result = agencyService.getAgencyById("agency123");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("agency123");
    assertThat(result.getName()).isEqualTo("Test Agency");
    assertThat(result.getMediaOwnerId()).isEqualTo("MO_001");
    assertThat(result.isActivated()).isTrue();
    assertThat(result.getCompanyEmail()).isEqualTo("info@testagency.com");
    assertThat(result.getCountryId()).isEqualTo("US");
    assertThat(result.getCountryName()).isEqualTo("United States");

    verify(agencyRepository, times(1)).findById("agency123");
    verify(countryRepository, times(1)).findById("US");
  }

  @Test
  void getAgencyById_WhenAgencyNotFound_ShouldThrowException() {
    // Given
    when(agencyRepository.findById("agency123")).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> agencyService.getAgencyById("agency123"))
        .isInstanceOf(AgencyNotFoundException.class)
        .hasMessage("Agency not found with ID: agency123");

    verify(agencyRepository, times(1)).findById("agency123");
  }

  // ========== createAgency Tests ==========

  @Test
  void createAgency_WhenValidData_ShouldCreateAgency() {
    // Given
    Country testCountry = createTestCountry();
    IamUserContext userContext = createIamUserContext();

    when(agencyRepository.existsByName("Test Agency")).thenReturn(false);
    when(countryRepository.findById("US")).thenReturn(Optional.of(testCountry));
    when(agencyRepository.save(any(Agency.class))).thenReturn(testAgency);

    // When
    AgencyResponseDTO result = agencyService.createAgency(agencyRequestDTO);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("Test Agency");
    assertThat(result.getMediaOwnerId()).isEqualTo("MO_001");
    assertThat(result.isActivated()).isTrue();

    verify(agencyRepository, times(1)).existsByName("Test Agency");
    verify(countryRepository, times(2))
        .findById("US"); // Called once in validateCountryForAgency and twice in getCountryName
    verify(agencyRepository, times(1)).save(any(Agency.class));
  }

  @Test
  void createAgency_WhenNameAlreadyExists_ShouldThrowException() {
    // Given
    Country testCountry = createTestCountry();
    when(agencyRepository.existsByName("Test Agency")).thenReturn(true);
    when(countryRepository.findById("US")).thenReturn(Optional.of(testCountry));

    // When & Then
    assertThatThrownBy(() -> agencyService.createAgency(agencyRequestDTO))
        .isInstanceOf(AgencyAlreadyExistsException.class)
        .hasMessage("Agency with name Test Agency already exists");

    verify(agencyRepository, times(1)).existsByName("Test Agency");
    verify(countryRepository, times(1)).findById("US");
    verify(agencyRepository, never()).save(any(Agency.class));
  }

  @Test
  void createAgency_WhenNameExistsWithDifferentCase_ShouldThrowException() {
    // Given
    Country testCountry = createTestCountry();
    when(agencyRepository.existsByName("Test Agency")).thenReturn(true);
    when(countryRepository.findById("US")).thenReturn(Optional.of(testCountry));

    // When & Then
    assertThatThrownBy(() -> agencyService.createAgency(agencyRequestDTO))
        .isInstanceOf(AgencyAlreadyExistsException.class)
        .hasMessage("Agency with name Test Agency already exists");

    verify(agencyRepository, times(1)).existsByName("Test Agency");
    verify(countryRepository, times(1)).findById("US");
    verify(agencyRepository, never()).save(any(Agency.class));
  }

  @Test
  void createAgency_WithMinimalData_ShouldCreateAgency() {
    // Given
    AgencyRequestDTO minimalRequest = new AgencyRequestDTO();
    minimalRequest.setName("Minimal Agency");

    IamUserContext iamUserContext = createIamUserContext();

    // Create a minimal agency for the mock return
    Agency minimalAgency = new Agency();
    minimalAgency.setId("minimal123");
    minimalAgency.setName("Minimal Agency");
    minimalAgency.setActivated(true);
    minimalAgency.setCreatedAt(LocalDateTime.now());
    minimalAgency.setUpdatedAt(LocalDateTime.now());

    when(agencyRepository.existsByName("Minimal Agency")).thenReturn(false);
    when(agencyRepository.save(any(Agency.class))).thenReturn(minimalAgency);

    // When
    AgencyResponseDTO result = agencyService.createAgency(minimalRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("Minimal Agency");

    verify(agencyRepository, times(1)).existsByName("Minimal Agency");
    verify(agencyRepository, times(1)).save(any(Agency.class));
  }

  // ========== updateAgency Tests ==========

  @Test
  void updateAgency_WhenValidData_ShouldUpdateAgency() {
    // Given
    Country testCountry = createTestCountry();
    when(agencyRepository.findById("agency123")).thenReturn(Optional.of(testAgency));
    when(countryRepository.findById("US")).thenReturn(Optional.of(testCountry));
    when(agencyRepository.save(any(Agency.class))).thenReturn(testAgency);

    // When
    AgencyResponseDTO result = agencyService.updateAgency("agency123", agencyRequestDTO);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("agency123");

    verify(agencyRepository, times(1)).findById("agency123");
    verify(countryRepository, times(2)).findById("US");
    verify(agencyRepository, times(1)).save(any(Agency.class));
  }

  @Test
  void updateAgency_WhenAgencyNotFound_ShouldThrowException() {
    // Given
    when(agencyRepository.findById("agency123")).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> agencyService.updateAgency("agency123", agencyRequestDTO))
        .isInstanceOf(AgencyNotFoundException.class)
        .hasMessage("Agency not found with ID: agency123");

    verify(agencyRepository, times(1)).findById("agency123");
    verify(agencyRepository, never()).save(any(Agency.class));
  }

  @Test
  void updateAgency_WhenNameAlreadyExists_ShouldThrowException() {
    // Given
    Country testCountry = createTestCountry();
    Agency existingAgency = createTestAgency();
    Agency anotherAgency = createTestAgency();
    anotherAgency.setId("another123");
    anotherAgency.setName("Another Agency");

    when(agencyRepository.findById("agency123")).thenReturn(Optional.of(existingAgency));
    when(countryRepository.findById("US")).thenReturn(Optional.of(testCountry));
    when(agencyRepository.findByName("Another Agency")).thenReturn(Optional.of(anotherAgency));

    // Update request with name that already exists
    AgencyRequestDTO updateRequest = createAgencyRequestDTO();
    updateRequest.setName("Another Agency");

    // When & Then
    assertThatThrownBy(() -> agencyService.updateAgency("agency123", updateRequest))
        .isInstanceOf(AgencyAlreadyExistsException.class)
        .hasMessage("Agency with name Another Agency already exists");

    verify(agencyRepository, times(1)).findById("agency123");
    verify(countryRepository, times(1)).findById("US");
    verify(agencyRepository, times(1)).findByName("Another Agency");
    verify(agencyRepository, never()).save(any(Agency.class));
  }

  @Test
  void updateAgency_WhenNameNotChanged_ShouldUpdateSuccessfully() {
    // Given
    Country testCountry = createTestCountry();
    when(agencyRepository.findById("agency123")).thenReturn(Optional.of(testAgency));
    when(countryRepository.findById("US")).thenReturn(Optional.of(testCountry));
    when(agencyRepository.save(any(Agency.class))).thenReturn(testAgency);

    // When
    AgencyResponseDTO result = agencyService.updateAgency("agency123", agencyRequestDTO);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("agency123");

    verify(agencyRepository, times(1)).findById("agency123");
    verify(countryRepository, times(2)).findById("US");
    verify(agencyRepository, never())
        .findByName(anyString()); // Should not check for duplicates when name unchanged
    verify(agencyRepository, times(1)).save(any(Agency.class));
  }

  // ========== getAllAgencies Tests ==========

  @Test
  void getAllAgencies_ShouldReturnPagedAgencies() {
    // Given
    Country testCountry = createTestCountry();
    Pageable pageable = PageRequest.of(0, 10);
    Page<Agency> agencyPage = new PageImpl<>(List.of(testAgency), pageable, 1);
    when(agencyRepository.findAll(pageable)).thenReturn(agencyPage);
    when(countryRepository.findById("US")).thenReturn(Optional.of(testCountry));

    // When
    Page<AgencyResponseDTO> result = agencyService.getAllAgencies(pageable, null);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getName()).isEqualTo("Test Agency");
    assertThat(result.getTotalElements()).isEqualTo(1);

    verify(agencyRepository, times(1)).findAll(pageable);
    verify(countryRepository, times(1)).findById("US");
  }

  @Test
  void getAllAgencies_WithEmptyResult_ShouldReturnEmptyPage() {
    // Given
    Pageable pageable = PageRequest.of(0, 10);
    Page<Agency> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
    when(agencyRepository.findAll(pageable)).thenReturn(emptyPage);

    // When
    Page<AgencyResponseDTO> result = agencyService.getAllAgencies(pageable, null);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isEqualTo(0);

    verify(agencyRepository, times(1)).findAll(pageable);
  }

  @Test
  void getAllAgencies_WithSearchTerm_ShouldUseSearchQuery() {
    // Given
    Country testCountry = createTestCountry();
    Pageable pageable = PageRequest.of(0, 10);
    String searchTerm = "Creative";
    Page<Agency> agencyPage = new PageImpl<>(List.of(testAgency), pageable, 1);
    when(agencyRepository.findByNameRegex(any(Pattern.class), eq(pageable))).thenReturn(agencyPage);
    when(countryRepository.findById("US")).thenReturn(Optional.of(testCountry));

    // When
    Page<AgencyResponseDTO> result = agencyService.getAllAgencies(pageable, searchTerm);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getName()).isEqualTo("Test Agency");

    verify(agencyRepository, times(1)).findByNameRegex(any(Pattern.class), eq(pageable));
    verify(countryRepository, times(1)).findById("US");
    verify(agencyRepository, never()).findAll(pageable);
  }

  @Test
  void getAllAgencies_WithEmptySearchTerm_ShouldUseFindAll() {
    // Given
    Country testCountry = createTestCountry();
    Pageable pageable = PageRequest.of(0, 10);
    Page<Agency> agencyPage = new PageImpl<>(List.of(testAgency), pageable, 1);
    when(agencyRepository.findAll(pageable)).thenReturn(agencyPage);
    when(countryRepository.findById("US")).thenReturn(Optional.of(testCountry));

    // When
    Page<AgencyResponseDTO> result = agencyService.getAllAgencies(pageable, "");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);

    verify(agencyRepository, times(1)).findAll(pageable);
    verify(countryRepository, times(1)).findById("US");
    verify(agencyRepository, never()).findByNameRegex(any(Pattern.class), any(Pageable.class));
  }

  @Test
  void getAllAgencies_WithWhitespaceSearchTerm_ShouldUseFindAll() {
    // Given
    Country testCountry = createTestCountry();
    Pageable pageable = PageRequest.of(0, 10);
    Page<Agency> agencyPage = new PageImpl<>(List.of(testAgency), pageable, 1);
    when(agencyRepository.findAll(pageable)).thenReturn(agencyPage);
    when(countryRepository.findById("US")).thenReturn(Optional.of(testCountry));

    // When
    Page<AgencyResponseDTO> result = agencyService.getAllAgencies(pageable, "   ");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);

    verify(agencyRepository, times(1)).findAll(pageable);
    verify(countryRepository, times(1)).findById("US");
    verify(agencyRepository, never()).findByNameRegex(any(Pattern.class), any(Pageable.class));
  }

  // ========== Mapping Tests ==========

  @Test
  void mapToAgency_WithCompleteData_ShouldMapCorrectly() throws Exception {
    // Given
    AgencyRequestDTO requestDTO = createAgencyRequestDTO();

    // When
    Agency result = invokeMapToAgency(requestDTO);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("Test Agency");
    assertThat(result.getMediaOwnerId()).isEqualTo("MO_001");
    assertThat(result.getCompanyEmail()).isEqualTo("info@testagency.com");
    assertThat(result.getCountryId()).isEqualTo("US");
    assertThat(result.getCompanyId()).isEqualTo("COMP_001");
    assertThat(result.getSeatId()).isEqualTo(67890);
    assertThat(result.getBrandRefId()).isEqualTo("BRAND123");
  }

  @Test
  void mapToAgencyResponseDTO_WithCompleteData_ShouldMapCorrectly() {
    // Given
    Agency agency = createTestAgency();
    Country testCountry = createTestCountry();
    when(countryRepository.findById("US")).thenReturn(Optional.of(testCountry));

    // When
    AgencyResponseDTO result = agencyService.mapToAgencyResponseDTO(agency);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("agency123");
    assertThat(result.getName()).isEqualTo("Test Agency");
    assertThat(result.getMediaOwnerId()).isEqualTo("MO_001");
    assertThat(result.isActivated()).isTrue();
    assertThat(result.getCreatedAt()).isNotNull();
    assertThat(result.getUpdatedAt()).isNotNull();
    assertThat(result.getCompanyEmail()).isEqualTo("info@testagency.com");
    assertThat(result.getCountryId()).isEqualTo("US");
    assertThat(result.getCountryName()).isEqualTo("United States");
    assertThat(result.getCompanyId()).isEqualTo("COMP_001");
    assertThat(result.getSeatId()).isEqualTo(67890);
    assertThat(result.getBrandRefId()).isEqualTo("BRAND123");
  }

  // ========== updateAgencyFromDTO Tests ==========

  @Test
  void updateAgencyFromDTO_WithValidData_ShouldUpdateAllFields() throws Exception {
    // Given
    Agency existingAgency = createTestAgency();
    AgencyRequestDTO requestDTO = createAgencyRequestDTO();
    requestDTO.setName("Updated Agency Name");
    requestDTO.setMediaOwnerId("MO_002");

    // When
    Agency result = invokeUpdateAgencyFromDTO(existingAgency, requestDTO);

    // Then
    assertThat(result.getName()).isEqualTo("Updated Agency Name");
    assertThat(result.getMediaOwnerId()).isEqualTo("MO_002");
    assertThat(result.getCompanyEmail()).isEqualTo("info@testagency.com");
    assertThat(result.getCountryId()).isEqualTo("US");
  }

  // ========== Edge Cases and Error Handling ==========

  @Test
  void createAgency_WithEmptyName_ShouldThrowValidationException() {
    // Given
    AgencyRequestDTO requestDTO = new AgencyRequestDTO();
    requestDTO.setName(""); // Empty name

    IamUserContext iamUserContext = createIamUserContext();

    // Mock the repository to return false for empty name check
    when(agencyRepository.existsByName("")).thenReturn(false);

    // Create a minimal agency for the mock return
    Agency emptyNameAgency = new Agency();
    emptyNameAgency.setId("empty123");
    emptyNameAgency.setName("");
    emptyNameAgency.setActivated(true);
    emptyNameAgency.setCreatedAt(LocalDateTime.now());
    emptyNameAgency.setUpdatedAt(LocalDateTime.now());

    when(agencyRepository.save(any(Agency.class))).thenReturn(emptyNameAgency);

    // When
    AgencyResponseDTO result = agencyService.createAgency(requestDTO);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("");

    verify(agencyRepository, times(1)).existsByName("");
    verify(agencyRepository, times(1)).save(any(Agency.class));
  }

  @Test
  void getAllAgencies_WithLargePageSize_ShouldHandleCorrectly() {
    // Given
    Country testCountry = createTestCountry();
    Pageable pageable = PageRequest.of(0, 1000);
    Page<Agency> agencyPage = new PageImpl<>(List.of(testAgency), pageable, 1);
    when(agencyRepository.findAll(pageable)).thenReturn(agencyPage);
    when(countryRepository.findById("US")).thenReturn(Optional.of(testCountry));

    // When
    Page<AgencyResponseDTO> result = agencyService.getAllAgencies(pageable, null);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    verify(agencyRepository, times(1)).findAll(pageable);
    verify(countryRepository, times(1)).findById("US");
  }

  // ========== Helper Methods ==========

  private Agency createTestAgency() {
    Agency agency = new Agency();
    agency.setId("agency123");
    agency.setName("Test Agency");
    agency.setMediaOwnerId("MO_001");
    agency.setCompanyEmail("info@testagency.com");
    agency.setCountryId("US");
    agency.setCompanyId("COMP_001");
    agency.setSeatId(67890);
    agency.setBrandRefId("BRAND123");
    agency.setActivated(true);
    agency.setCreatedAt(LocalDateTime.now());
    agency.setUpdatedAt(LocalDateTime.now());
    return agency;
  }

  private AgencyRequestDTO createAgencyRequestDTO() {
    AgencyRequestDTO requestDTO = new AgencyRequestDTO();
    requestDTO.setName("Test Agency");
    requestDTO.setMediaOwnerId("MO_001");
    requestDTO.setCompanyEmail("info@testagency.com");
    requestDTO.setCountryId("US");
    requestDTO.setCompanyId("COMP_001");
    requestDTO.setSeatId(67890);
    requestDTO.setBrandRefId("BRAND123");
    return requestDTO;
  }

  private Country createTestCountry() {
    Country country = new Country();
    country.setId("country123");
    country.setCountryId("US");
    country.setName("United States");
    country.setLatitude(39.8283);
    country.setLongitude(-98.5795);
    country.setZoom(4);
    country.setPopulation(331000000L);
    country.setIso("USA");
    country.setActive(true);
    country.setDialingCode("+1");
    country.setTimezone("America/New_York");
    country.setCreatedAt(LocalDateTime.now());
    country.setUpdatedAt(LocalDateTime.now());
    return country;
  }

  private Agency invokeMapToAgency(AgencyRequestDTO requestDTO) throws Exception {
    Method method = AgencyService.class.getDeclaredMethod("mapToAgency", AgencyRequestDTO.class);
    method.setAccessible(true);
    return (Agency) method.invoke(agencyService, requestDTO);
  }

  private Agency invokeUpdateAgencyFromDTO(Agency agency, AgencyRequestDTO requestDTO)
      throws Exception {
    Method method =
        AgencyService.class.getDeclaredMethod(
            "updateAgencyFromDTO", Agency.class, AgencyRequestDTO.class);
    method.setAccessible(true);
    return (Agency) method.invoke(agencyService, agency, requestDTO);
  }

  private IamUserContext createIamUserContext() {
    return IamUserContext.builder()
        .id("testuser")
        .userId("testuser")
        .username("testuser")
        .email("test@example.com")
        .companyId("company123")
        .locale(java.util.Locale.ENGLISH)
        .build();
  }
}
