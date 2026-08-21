package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.Country;
import com.mw.planner.domain.Inventory;
import com.mw.planner.domain.InventoryCountrySummary;
import com.mw.planner.dto.CountryMarketDetailsDTO;
import com.mw.planner.dto.CountryRequestDTO;
import com.mw.planner.dto.CountryResponseDTO;
import com.mw.planner.exception.country.CountryAlreadyExistsException;
import com.mw.planner.exception.country.CountryNotFoundException;
import com.mw.planner.repository.CountryRepository;
import com.mw.planner.repository.InventoryCountrySummaryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
class CountryServiceTest {

  @Mock private CountryRepository countryRepository;
  @Mock private InventoryCountrySummaryRepository inventoryCountrySummaryRepository;

  @InjectMocks private CountryService countryService;

  private Country testCountry;
  private CountryRequestDTO countryRequestDTO;

  @BeforeEach
  void setUp() {
    testCountry = createTestCountry();
    countryRequestDTO = createCountryRequestDTO();
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(countryRepository, inventoryCountrySummaryRepository);
  }

  // ========== createCountry Tests ==========

  @Test
  void createCountry_WhenValidData_ShouldCreateCountry() {
    // Given
    when(countryRepository.existsByCountryId("US")).thenReturn(false);
    when(countryRepository.existsByIso("USA")).thenReturn(false);
    when(countryRepository.save(any(Country.class))).thenReturn(testCountry);

    // When
    CountryResponseDTO result = countryService.createCountry(countryRequestDTO);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("country123");
    assertThat(result.getCountryId()).isEqualTo("US");
    assertThat(result.getName()).isEqualTo("United States");
    assertThat(result.getIso()).isEqualTo("USA");
    assertThat(result.getActive()).isTrue();

    verify(countryRepository, times(1)).existsByCountryId("US");
    verify(countryRepository, times(1)).existsByIso("USA");
    verify(countryRepository, times(1)).save(any(Country.class));
  }

  @Test
  void createCountry_WhenCountryIdAlreadyExists_ShouldThrowException() {
    // Given
    when(countryRepository.existsByCountryId("US")).thenReturn(true);

    // When & Then
    assertThatThrownBy(() -> countryService.createCountry(countryRequestDTO))
        .isInstanceOf(CountryAlreadyExistsException.class)
        .hasMessage("Country with countryId US already exists");

    verify(countryRepository, times(1)).existsByCountryId("US");
    verify(countryRepository, never()).existsByIso(any());
    verify(countryRepository, never()).save(any());
  }

  @Test
  void createCountry_WhenIsoAlreadyExists_ShouldThrowException() {
    // Given
    when(countryRepository.existsByCountryId("US")).thenReturn(false);
    when(countryRepository.existsByIso("USA")).thenReturn(true);

    // When & Then
    assertThatThrownBy(() -> countryService.createCountry(countryRequestDTO))
        .isInstanceOf(CountryAlreadyExistsException.class)
        .hasMessage("Country with ISO USA already exists");

    verify(countryRepository, times(1)).existsByCountryId("US");
    verify(countryRepository, times(1)).existsByIso("USA");
    verify(countryRepository, never()).save(any());
  }

  // ========== getCountryById Tests ==========

  @Test
  void getCountryById_WhenCountryExists_ShouldReturnCountry() {
    // Given
    when(countryRepository.findById("country123")).thenReturn(Optional.of(testCountry));

    // When
    CountryResponseDTO result = countryService.getCountryById("country123");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("country123");
    assertThat(result.getCountryId()).isEqualTo("US");
    assertThat(result.getName()).isEqualTo("United States");
    assertThat(result.getIso()).isEqualTo("USA");
    assertThat(result.getActive()).isTrue();

    verify(countryRepository, times(1)).findById("country123");
  }

  @Test
  void getCountryById_WhenCountryNotFound_ShouldThrowException() {
    // Given
    when(countryRepository.findById("country123")).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> countryService.getCountryById("country123"))
        .isInstanceOf(CountryNotFoundException.class)
        .hasMessage("Country not found with ID: country123");

    verify(countryRepository, times(1)).findById("country123");
  }

  // ========== getCountryByCountryId Tests ==========

  @Test
  void getCountryByCountryId_WhenCountryExists_ShouldReturnCountry() {
    // Given
    when(countryRepository.findByCountryId("US")).thenReturn(Optional.of(testCountry));

    // When
    CountryResponseDTO result = countryService.getCountryByCountryId("US");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("country123");
    assertThat(result.getCountryId()).isEqualTo("US");
    assertThat(result.getName()).isEqualTo("United States");
    assertThat(result.getIso()).isEqualTo("USA");
    assertThat(result.getActive()).isTrue();

    verify(countryRepository, times(1)).findByCountryId("US");
  }

  @Test
  void getCountryByCountryId_WhenCountryNotFound_ShouldThrowException() {
    // Given
    when(countryRepository.findByCountryId("XX")).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> countryService.getCountryByCountryId("XX"))
        .isInstanceOf(CountryNotFoundException.class)
        .hasMessage("Country not found with ID: XX");

    verify(countryRepository, times(1)).findByCountryId("XX");
  }

  @Test
  void getCountryByCountryId_WithDifferentCountryId_ShouldReturnCorrectCountry() {
    // Given
    Country canadaCountry = createTestCountry();
    canadaCountry.setId("country456");
    canadaCountry.setCountryId("CA");
    canadaCountry.setName("Canada");
    canadaCountry.setIso("CAN");

    when(countryRepository.findByCountryId("CA")).thenReturn(Optional.of(canadaCountry));

    // When
    CountryResponseDTO result = countryService.getCountryByCountryId("CA");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getCountryId()).isEqualTo("CA");
    assertThat(result.getName()).isEqualTo("Canada");
    assertThat(result.getIso()).isEqualTo("CAN");

    verify(countryRepository, times(1)).findByCountryId("CA");
  }

  // ========== updateCountry Tests ==========

  @Test
  void updateCountry_WhenValidData_ShouldUpdateCountry() {
    // Given
    when(countryRepository.findById("country123")).thenReturn(Optional.of(testCountry));
    when(countryRepository.save(any(Country.class))).thenReturn(testCountry);

    // When
    CountryResponseDTO result = countryService.updateCountry("country123", countryRequestDTO);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("country123");
    assertThat(result.getCountryId()).isEqualTo("US");
    assertThat(result.getName()).isEqualTo("United States");

    verify(countryRepository, times(1)).findById("country123");
    verify(countryRepository, times(1)).save(any(Country.class));
  }

  @Test
  void updateCountry_WhenCountryNotFound_ShouldThrowException() {
    // Given
    when(countryRepository.findById("country123")).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> countryService.updateCountry("country123", countryRequestDTO))
        .isInstanceOf(CountryNotFoundException.class)
        .hasMessage("Country not found with ID: country123");

    verify(countryRepository, times(1)).findById("country123");
    verify(countryRepository, never()).save(any());
  }

  @Test
  void updateCountry_WhenCountryIdChangedAndExists_ShouldThrowException() {
    // Given
    Country existingCountry = createTestCountry();
    existingCountry.setCountryId("CA"); // Different countryId
    when(countryRepository.findById("country123")).thenReturn(Optional.of(existingCountry));
    when(countryRepository.existsByCountryId("US")).thenReturn(true);

    // When & Then
    assertThatThrownBy(() -> countryService.updateCountry("country123", countryRequestDTO))
        .isInstanceOf(CountryAlreadyExistsException.class)
        .hasMessage("Country with countryId US already exists");

    verify(countryRepository, times(1)).findById("country123");
    verify(countryRepository, times(1)).existsByCountryId("US");
    verify(countryRepository, never()).save(any());
  }

  @Test
  void updateCountry_WhenIsoChangedAndExists_ShouldThrowException() {
    // Given
    Country existingCountry = createTestCountry();
    existingCountry.setIso("CAN"); // Different ISO
    when(countryRepository.findById("country123")).thenReturn(Optional.of(existingCountry));
    when(countryRepository.existsByIso("USA")).thenReturn(true);

    // When & Then
    assertThatThrownBy(() -> countryService.updateCountry("country123", countryRequestDTO))
        .isInstanceOf(CountryAlreadyExistsException.class)
        .hasMessage("Country with ISO USA already exists");

    verify(countryRepository, times(1)).findById("country123");
    verify(countryRepository, never()).existsByCountryId(any());
    verify(countryRepository, times(1)).existsByIso("USA");
    verify(countryRepository, never()).save(any());
  }

  // ========== getAllCountries Tests ==========

  @Test
  void getAllCountries_WhenCalled_ShouldReturnCountries() {
    // Given
    Pageable pageable = PageRequest.of(0, 10);
    Page<Country> countriesPage = new PageImpl<>(java.util.List.of(testCountry));
    when(countryRepository.findAll(pageable)).thenReturn(countriesPage);

    // When
    Page<CountryResponseDTO> result = countryService.getAllCountries(pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getId()).isEqualTo("country123");
    assertThat(result.getContent().get(0).getCountryId()).isEqualTo("US");

    verify(countryRepository, times(1)).findAll(pageable);
  }

  // ========== getMarketDetails Tests ==========

  @Test
  void getMarketDetails_WhenCompanyHasMarketAccess_ShouldReturnMarketDetails() {
    // Given
    String companyId = "company123";

    Country country1 = createTestCountry();
    country1.setCountryId("US");
    country1.setName("United States");
    country1.setPopulation(331000000L);

    Country country2 = createTestCountry();
    country2.setId("country456");
    country2.setCountryId("CA");
    country2.setName("Canada");
    country2.setPopulation(38000000L);

    Country country3 = createTestCountry();
    country3.setId("country789");
    country3.setCountryId("MX");
    country3.setName("Mexico");
    country3.setPopulation(126000000L);

    // Nested counts by country and classification (Mexico absent to test default value of 0)
    when(countryRepository.findAll()).thenReturn(List.of(country1, country2, country3));
    when(inventoryCountrySummaryRepository.findByCountryIn(anyCollection()))
        .thenReturn(
            List.of(
                summary("United States", Map.of("Billboard", 1L, "Transit", 1L)), // sums to 2
                summary("Canada", Map.of("Transit", 1L))));
    // Mexico is intentionally absent to test default value of 0

    // When
    List<CountryMarketDetailsDTO> result =
        countryService.getCountryMarketDetails(companyId, null, null);

    // Then
    assertThat(result).hasSize(3);
    assertThat(result.get(0).getCountryId()).isEqualTo("US");
    assertThat(result.get(0).getCountryName()).isEqualTo("United States");
    assertThat(result.get(0).getPopulation()).isEqualTo(331000000L);
    assertThat(result.get(0).getInventoryCount()).isEqualTo(2L);
    // Nested classification breakdown is passed through unchanged
    assertThat(result.get(0).getInventoryCountByClassification())
        .containsEntry("Billboard", 1L)
        .containsEntry("Transit", 1L);
    // Service sets impressions to 0L with FIXME comment - waiting for Measure API
    assertThat(result.get(0).getImpressions()).isEqualTo(0L);

    assertThat(result.get(1).getCountryId()).isEqualTo("CA");
    assertThat(result.get(1).getCountryName()).isEqualTo("Canada");
    assertThat(result.get(1).getInventoryCount()).isEqualTo(1L);
    assertThat(result.get(1).getInventoryCountByClassification()).containsEntry("Transit", 1L);

    // Mexico (not in map) returns total 0 and empty classification map
    assertThat(result.get(2).getCountryId()).isEqualTo("MX");
    assertThat(result.get(2).getCountryName()).isEqualTo("Mexico");
    assertThat(result.get(2).getInventoryCount()).isEqualTo(0L);
    assertThat(result.get(2).getInventoryCountByClassification()).isEmpty();

    verify(countryRepository).findAll();
    verify(countryRepository, never()).findByIdIn(anyList());
    verify(countryRepository, never()).findByIsoIn(anyList());
    verify(inventoryCountrySummaryRepository).findByCountryIn(anyCollection());
  }

  @Test
  void getMarketDetails_WithEmptyCountryIdsAndEmptyCountryIso_ShouldFetchAllCountries() {
    // Given
    String companyId = "company123";

    when(countryRepository.findAll()).thenReturn(List.of(testCountry));
    when(inventoryCountrySummaryRepository.findByCountryIn(anyCollection())).thenReturn(List.of());

    // When
    List<CountryMarketDetailsDTO> result =
        countryService.getCountryMarketDetails(companyId, List.of(), List.of());

    // Then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCountryId()).isEqualTo(testCountry.getCountryId());

    verify(countryRepository).findAll();
    verify(countryRepository, never()).findByIdIn(anyList());
    verify(countryRepository, never()).findByIsoIn(anyList());
    verify(inventoryCountrySummaryRepository).findByCountryIn(anyCollection());
  }

  @Test
  void getMarketDetails_WithCountryIds_ShouldFetchOnlyMatchingCountries() {
    // Given
    String companyId = "company123";
    List<String> countryIds = List.of("country123", "country456");

    Country country1 = createTestCountry();
    country1.setCountryId("US");
    country1.setName("United States");
    country1.setPopulation(331000000L);

    Country country2 = createTestCountry();
    country2.setId("country456");
    country2.setCountryId("CA");
    country2.setName("Canada");
    country2.setPopulation(38000000L);

    // Canada is intentionally absent to test default value of 0
    when(countryRepository.findByIdIn(countryIds)).thenReturn(List.of(country1, country2));
    when(inventoryCountrySummaryRepository.findByCountryIn(anyCollection()))
        .thenReturn(List.of(summary("United States", Map.of("Billboard", 2L))));

    // When
    List<CountryMarketDetailsDTO> result =
        countryService.getCountryMarketDetails(companyId, countryIds, null);

    // Then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getId()).isEqualTo(country1.getId());
    assertThat(result.get(0).getCountryId()).isEqualTo("US");
    assertThat(result.get(0).getCountryName()).isEqualTo("United States");
    assertThat(result.get(0).getPopulation()).isEqualTo(331000000L);
    assertThat(result.get(0).getInventoryCount()).isEqualTo(2L);
    assertThat(result.get(0).getImpressions()).isEqualTo(0L);

    assertThat(result.get(1).getId()).isEqualTo("country456");
    assertThat(result.get(1).getCountryId()).isEqualTo("CA");
    assertThat(result.get(1).getCountryName()).isEqualTo("Canada");
    assertThat(result.get(1).getInventoryCount()).isEqualTo(0L);

    verify(countryRepository).findByIdIn(countryIds);
    verify(countryRepository, never()).findByIsoIn(anyList());
    verify(countryRepository, never()).findAll();
    verify(inventoryCountrySummaryRepository).findByCountryIn(anyCollection());
  }

  @Test
  void getMarketDetails_WithNonMatchingCountryIds_ShouldReturnEmptyList() {
    // Given
    String companyId = "company123";
    List<String> countryIds = List.of("nonexistent");

    when(countryRepository.findByIdIn(countryIds)).thenReturn(List.of());

    // When
    List<CountryMarketDetailsDTO> result =
        countryService.getCountryMarketDetails(companyId, countryIds, null);

    // Then
    assertThat(result).isEmpty();

    verify(countryRepository).findByIdIn(countryIds);
    verify(countryRepository, never()).findByIsoIn(anyList());
    verify(countryRepository, never()).findAll();
    // No countries matched -> getMarketDetails short-circuits before reading the summary
    verify(inventoryCountrySummaryRepository, never()).findByCountryIn(anyCollection());
  }

  @Test
  void getMarketDetails_WithCountryIso_ShouldFetchByIsoCodes() {
    // Given
    String companyId = "company123";
    // ISO codes are stored uppercase in the countries collection; the query is exact-match
    List<String> countryIso = List.of("US", "CA");

    Country country1 = createTestCountry();
    country1.setCountryId("US");
    country1.setName("United States");
    country1.setPopulation(331000000L);

    Country country2 = createTestCountry();
    country2.setId("country456");
    country2.setCountryId("CA");
    country2.setName("Canada");
    country2.setPopulation(38000000L);

    // Canada is intentionally absent to test default value of 0
    when(countryRepository.findByIsoIn(countryIso)).thenReturn(List.of(country1, country2));
    when(inventoryCountrySummaryRepository.findByCountryIn(anyCollection()))
        .thenReturn(List.of(summary("United States", Map.of("Billboard", 2L))));

    // When
    List<CountryMarketDetailsDTO> result =
        countryService.getCountryMarketDetails(companyId, null, countryIso);

    // Then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getId()).isEqualTo(country1.getId());
    assertThat(result.get(0).getCountryId()).isEqualTo("US");
    assertThat(result.get(0).getCountryName()).isEqualTo("United States");
    assertThat(result.get(0).getPopulation()).isEqualTo(331000000L);
    assertThat(result.get(0).getInventoryCount()).isEqualTo(2L);
    assertThat(result.get(0).getImpressions()).isEqualTo(0L);

    assertThat(result.get(1).getId()).isEqualTo("country456");
    assertThat(result.get(1).getCountryId()).isEqualTo("CA");
    assertThat(result.get(1).getCountryName()).isEqualTo("Canada");
    assertThat(result.get(1).getInventoryCount()).isEqualTo(0L);

    verify(countryRepository).findByIsoIn(countryIso);
    verify(countryRepository, never()).findByIdIn(anyList());
    verify(countryRepository, never()).findAll();
    verify(inventoryCountrySummaryRepository).findByCountryIn(anyCollection());
  }

  @Test
  void getMarketDetails_WithCountryIdsAndCountryIso_ShouldOnlyFilterByIds() {
    // Given
    String companyId = "company123";
    List<String> countryIds = List.of("country123");
    List<String> countryIso = List.of("US", "SG");

    when(countryRepository.findByIdIn(countryIds)).thenReturn(List.of(testCountry));
    when(inventoryCountrySummaryRepository.findByCountryIn(anyCollection())).thenReturn(List.of());

    // When
    List<CountryMarketDetailsDTO> result =
        countryService.getCountryMarketDetails(companyId, countryIds, countryIso);

    // Then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCountryId()).isEqualTo(testCountry.getCountryId());

    verify(countryRepository).findByIdIn(countryIds);
    verify(countryRepository, never()).findByIsoIn(anyList());
    verify(countryRepository, never()).findAll();
    verify(inventoryCountrySummaryRepository).findByCountryIn(anyCollection());
  }

  @Test
  void getMarketDetails_WithEmptyCountryIdsAndPopulatedCountryIso_ShouldFetchByIsoCodes() {
    // Given
    String companyId = "company123";
    List<String> countryIso = List.of("US");

    when(countryRepository.findByIsoIn(countryIso)).thenReturn(List.of(testCountry));
    when(inventoryCountrySummaryRepository.findByCountryIn(anyCollection())).thenReturn(List.of());

    // When
    List<CountryMarketDetailsDTO> result =
        countryService.getCountryMarketDetails(companyId, List.of(), countryIso);

    // Then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCountryId()).isEqualTo(testCountry.getCountryId());

    verify(countryRepository).findByIsoIn(countryIso);
    verify(countryRepository, never()).findByIdIn(anyList());
    verify(countryRepository, never()).findAll();
    verify(inventoryCountrySummaryRepository).findByCountryIn(anyCollection());
  }

  // ========== mapCountryToResponseDTO Tests ==========

  @Test
  void mapCountryToResponseDTO_WithTaxInformation_ShouldMapCorrectly() {
    // Given
    Country country = createTestCountry();
    country.setTax(new Country.Tax());
    country.getTax().setLabel("VAT");
    country.getTax().setPercent(8.5);

    // When
    CountryResponseDTO result = countryService.mapCountryToResponseDTO(country);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("country123");
    assertThat(result.getCountryId()).isEqualTo("US");
    assertThat(result.getName()).isEqualTo("United States");
    assertThat(result.getTax()).isNotNull();
    assertThat(result.getTax().getLabel()).isEqualTo("VAT");
    assertThat(result.getTax().getPercent()).isEqualTo(8.5);
  }

  @Test
  void mapCountryToResponseDTO_WithoutTaxInformation_ShouldMapCorrectly() {
    // Given
    Country country = createTestCountry();
    country.setTax(null);

    // When
    CountryResponseDTO result = countryService.mapCountryToResponseDTO(country);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("country123");
    assertThat(result.getCountryId()).isEqualTo("US");
    assertThat(result.getName()).isEqualTo("United States");
    assertThat(result.getTax()).isNull();
  }

  // ========== Edge Cases and Error Handling Tests ==========

  @Test
  void createCountry_WithNullTaxInformation_ShouldCreateCountry() {
    // Given
    CountryRequestDTO requestDTO = createCountryRequestDTO();
    requestDTO.setTax(null);

    when(countryRepository.existsByCountryId("US")).thenReturn(false);
    when(countryRepository.existsByIso("USA")).thenReturn(false);
    when(countryRepository.save(any(Country.class))).thenReturn(testCountry);

    // When
    CountryResponseDTO result = countryService.createCountry(requestDTO);

    // Then
    assertThat(result).isNotNull();
    verify(countryRepository).save(any(Country.class));
  }

  @Test
  void updateCountry_WithNullTaxInformation_ShouldUpdateCountry() {
    // Given
    CountryRequestDTO requestDTO = createCountryRequestDTO();
    requestDTO.setTax(null);

    when(countryRepository.findById("country123")).thenReturn(Optional.of(testCountry));
    when(countryRepository.save(any(Country.class))).thenReturn(testCountry);

    // When
    CountryResponseDTO result = countryService.updateCountry("country123", requestDTO);

    // Then
    assertThat(result).isNotNull();
    verify(countryRepository).save(any(Country.class));
  }

  @Test
  void getAllCountries_WithEmptyPage_ShouldReturnEmptyPage() {
    // Given
    Pageable pageable = PageRequest.of(0, 10);
    Page<Country> emptyPage = new PageImpl<>(List.of(), pageable, 0);
    when(countryRepository.findAll(pageable)).thenReturn(emptyPage);

    // When
    Page<CountryResponseDTO> result = countryService.getAllCountries(pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isEqualTo(0);
    verify(countryRepository).findAll(pageable);
  }

  // ========== Helper Methods ==========

  private InventoryCountrySummary summary(String country, Map<String, Long> classificationCounts) {
    long total = classificationCounts.values().stream().mapToLong(Long::longValue).sum();
    return InventoryCountrySummary.builder()
        .country(country)
        .classificationCounts(classificationCounts)
        .totalCount(total)
        .build();
  }

  private Country createTestCountry() {
    Country.Tax tax = new Country.Tax();
    tax.setLabel("VAT");
    tax.setPercent(8.5);

    Country country = new Country();
    country.setId("country123");
    country.setCountryId("US");
    country.setName("United States");
    country.setLatitude(39.8283);
    country.setLongitude(-98.5795);
    country.setZoom(4);
    country.setIso("USA");
    country.setActive(true);
    country.setDialingCode("+1");
    country.setTimezone("America/New_York");
    country.setTax(tax);
    country.setCreatedAt(LocalDateTime.now());
    country.setUpdatedAt(LocalDateTime.now());
    return country;
  }

  private CountryRequestDTO createCountryRequestDTO() {
    CountryRequestDTO.Tax tax = new CountryRequestDTO.Tax();
    tax.setLabel("VAT");
    tax.setPercent(8.5);

    return CountryRequestDTO.builder()
        .countryId("US")
        .name("United States")
        .latitude(39.8283)
        .longitude(-98.5795)
        .zoom(4)
        .iso("USA")
        .active(true)
        .dialingCode("+1")
        .timezone("America/New_York")
        .tax(tax)
        .build();
  }

  private Inventory createTestInventoryWithAvailability(String id, Long totalVisitors) {
    Inventory inventory = new Inventory();
    inventory.setId(id);
    // Note: DailyAvailability field doesn't exist in Inventory class
    // This method is kept for compatibility but doesn't set availability data
    return inventory;
  }
}
