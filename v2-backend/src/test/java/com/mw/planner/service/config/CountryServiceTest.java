package com.mw.planner.service.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.Country;
import com.mw.planner.dto.CountrySyncResponseDTO;
import com.mw.planner.dto.MwCountryDTO;
import com.mw.planner.repository.CountryRepository;
import com.mw.planner.service.CountryService;
import com.mw.planner.service.MwMasterDataService;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CountryServiceTest {

  @Mock private MwMasterDataService mwMasterDataService;

  @Mock private CountryRepository countryRepository;

  @InjectMocks private CountryService countryService;

  private MwCountryDTO sampleMwCountry;
  private Country existingCountry;

  @BeforeEach
  void setUp() {
    // Setup sample MW country data
    sampleMwCountry = new MwCountryDTO();
    sampleMwCountry.setCountryId("france");
    sampleMwCountry.setName("France");
    sampleMwCountry.setNameJa("フランス");
    sampleMwCountry.setLatitude(51.0344);
    sampleMwCountry.setLongitude(2.618787);
    sampleMwCountry.setZoom(5);
    sampleMwCountry.setPopulation(67150000L);
    sampleMwCountry.setIso("FR");
    sampleMwCountry.setPostalformat("99999");
    sampleMwCountry.setPostalname("Code postal");
    sampleMwCountry.setActive(true);
    sampleMwCountry.setDialingCode("+33");

    // Setup existing country
    existingCountry = new Country();
    existingCountry.setId("existing-id");
    existingCountry.setCountryId("france");
    existingCountry.setName("France");
    existingCountry.setActive(true);
  }

  @Test
  void testSyncCountriesFromExternalApi_WithNewCountries() {
    // Arrange
    List<MwCountryDTO> externalCountries = Arrays.asList(sampleMwCountry);
    when(mwMasterDataService.fetchCountriesFromMasterDataApi()).thenReturn(externalCountries);
    when(countryRepository.findAll()).thenReturn(Arrays.asList()); // No existing countries
    when(countryRepository.saveAll(anyList())).thenReturn(Arrays.asList(existingCountry));

    // Act
    CountrySyncResponseDTO result = countryService.syncCountriesFromExternalApi();

    // Assert
    assertNotNull(result);
    assertEquals(1, result.getSyncedCount());
    assertEquals(0, result.getUpdatedCount());
    assertEquals(1, result.getCreatedCount());
    assertTrue(result.getMessage().contains("Successfully synced 1 countries"));

    verify(countryRepository, times(1)).saveAll(anyList());
    verify(countryRepository, never()).save(any(Country.class));
  }

  @Test
  void testSyncCountriesFromExternalApi_WithExistingCountries() {
    // Arrange
    List<MwCountryDTO> externalCountries = Arrays.asList(sampleMwCountry);
    when(mwMasterDataService.fetchCountriesFromMasterDataApi()).thenReturn(externalCountries);
    when(countryRepository.findAll())
        .thenReturn(Arrays.asList(existingCountry)); // Existing country
    when(countryRepository.saveAll(anyList())).thenReturn(Arrays.asList(existingCountry));

    // Act
    CountrySyncResponseDTO result = countryService.syncCountriesFromExternalApi();

    // Assert
    assertNotNull(result);
    assertEquals(1, result.getSyncedCount());
    assertEquals(1, result.getUpdatedCount());
    assertEquals(0, result.getCreatedCount());
    assertTrue(result.getMessage().contains("Successfully synced 1 countries"));

    verify(countryRepository, times(1)).saveAll(anyList());
    verify(countryRepository, never()).save(any(Country.class));
  }

  @Test
  void testSyncCountriesFromExternalApi_WithNoCountries() {
    // Arrange
    when(mwMasterDataService.fetchCountriesFromMasterDataApi()).thenReturn(Arrays.asList());

    // Act
    CountrySyncResponseDTO result = countryService.syncCountriesFromExternalApi();

    // Assert
    assertNotNull(result);
    assertEquals(0, result.getSyncedCount());
    assertEquals(0, result.getUpdatedCount());
    assertEquals(0, result.getCreatedCount());
    assertEquals("No countries found in MW Master Data API", result.getMessage());

    verify(countryRepository, never()).save(any(Country.class));
    verify(countryRepository, never()).saveAll(anyList());
  }

  @Test
  void testSyncCountriesFromExternalApi_WithMultipleCountries() {
    // Arrange
    MwCountryDTO secondCountry = new MwCountryDTO();
    secondCountry.setCountryId("germany");
    secondCountry.setName("Germany");
    secondCountry.setActive(true);

    List<MwCountryDTO> externalCountries = Arrays.asList(sampleMwCountry, secondCountry);
    when(mwMasterDataService.fetchCountriesFromMasterDataApi()).thenReturn(externalCountries);
    when(countryRepository.findAll()).thenReturn(Arrays.asList()); // No existing countries
    when(countryRepository.saveAll(anyList()))
        .thenReturn(Arrays.asList(existingCountry, existingCountry));

    // Act
    CountrySyncResponseDTO result = countryService.syncCountriesFromExternalApi();

    // Assert
    assertNotNull(result);
    assertEquals(2, result.getSyncedCount());
    assertEquals(0, result.getUpdatedCount());
    assertEquals(2, result.getCreatedCount());
    assertTrue(result.getMessage().contains("Successfully synced 2 countries"));

    verify(countryRepository, times(1)).saveAll(anyList());
    verify(countryRepository, never()).save(any(Country.class));
  }

  @Test
  void testSyncCountriesFromExternalApi_WithMixedNewAndExisting() {
    // Arrange
    MwCountryDTO secondCountry = new MwCountryDTO();
    secondCountry.setCountryId("germany");
    secondCountry.setName("Germany");
    secondCountry.setActive(true);

    List<MwCountryDTO> externalCountries = Arrays.asList(sampleMwCountry, secondCountry);
    when(mwMasterDataService.fetchCountriesFromMasterDataApi()).thenReturn(externalCountries);
    when(countryRepository.findAll())
        .thenReturn(Arrays.asList(existingCountry)); // Only France exists
    when(countryRepository.saveAll(anyList()))
        .thenReturn(Arrays.asList(existingCountry, existingCountry));

    // Act
    CountrySyncResponseDTO result = countryService.syncCountriesFromExternalApi();

    // Assert
    assertNotNull(result);
    assertEquals(2, result.getSyncedCount());
    assertEquals(1, result.getUpdatedCount());
    assertEquals(1, result.getCreatedCount());
    assertTrue(result.getMessage().contains("Successfully synced 2 countries"));

    verify(countryRepository, times(1)).saveAll(anyList());
    verify(countryRepository, never()).save(any(Country.class));
  }

  @Test
  void testSyncCountriesFromExternalApi_WithProcessingError() {
    // Arrange
    List<MwCountryDTO> externalCountries = Arrays.asList(sampleMwCountry);
    when(mwMasterDataService.fetchCountriesFromMasterDataApi()).thenReturn(externalCountries);
    when(countryRepository.findAll()).thenReturn(Arrays.asList()); // No existing countries
    when(countryRepository.saveAll(anyList())).thenThrow(new RuntimeException("Database error"));

    // Act & Assert
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> countryService.syncCountriesFromExternalApi());

    assertTrue(exception.getMessage().contains("Failed to sync countries from MW Master Data API"));
    assertTrue(exception.getMessage().contains("Database error"));

    verify(countryRepository, times(1)).saveAll(anyList());
    verify(countryRepository, never()).save(any(Country.class));
  }

  @Test
  void testSyncCountriesFromExternalApi_WithApiException() {
    // Arrange
    when(mwMasterDataService.fetchCountriesFromMasterDataApi())
        .thenThrow(new RuntimeException("API connection failed"));

    // Act & Assert
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> countryService.syncCountriesFromExternalApi());

    assertTrue(exception.getMessage().contains("Failed to sync countries from MW Master Data API"));
    assertTrue(exception.getMessage().contains("API connection failed"));
  }

  @Test
  void testSyncCountriesFromExternalApi_WithTaxInformation() {
    // Arrange
    MwCountryDTO.Tax tax = new MwCountryDTO.Tax();
    tax.setLabel("VAT");
    tax.setPercent(20.0);
    sampleMwCountry.setTax(tax);

    List<MwCountryDTO> externalCountries = Arrays.asList(sampleMwCountry);
    when(mwMasterDataService.fetchCountriesFromMasterDataApi()).thenReturn(externalCountries);
    when(countryRepository.findAll()).thenReturn(Arrays.asList()); // No existing countries
    when(countryRepository.saveAll(anyList())).thenReturn(Arrays.asList(existingCountry));

    // Act
    CountrySyncResponseDTO result = countryService.syncCountriesFromExternalApi();

    // Assert
    assertNotNull(result);
    assertEquals(1, result.getSyncedCount());
    assertEquals(0, result.getUpdatedCount());
    assertEquals(1, result.getCreatedCount());

    verify(countryRepository, times(1)).saveAll(anyList());
    verify(countryRepository, never()).save(any(Country.class));
  }
}
