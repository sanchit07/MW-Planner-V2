package com.mw.planner.service;

import com.mw.planner.domain.Country;
import com.mw.planner.domain.InventoryCountrySummary;
import com.mw.planner.dto.*;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.country.CountryAlreadyExistsException;
import com.mw.planner.exception.country.CountryNotFoundException;
import com.mw.planner.exception.masterdata.MasterDataApiException;
import com.mw.planner.repository.CountryRepository;
import com.mw.planner.repository.InventoryCountrySummaryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CountryService {

  private final CountryRepository countryRepository;
  private final InventoryCountrySummaryRepository inventoryCountrySummaryRepository;
  private final MwMasterDataService mwMasterDataService;

  /** Sync countries from external API using token-based authentication */
  @Transactional
  @CacheEvict(value = "countries", allEntries = true)
  public CountrySyncResponseDTO syncCountriesFromExternalApi() {
    log.info("Starting country sync from MW Master Data API");

    try {
      // Fetch countries from external API
      List<MwCountryDTO> externalCountries = mwMasterDataService.fetchCountriesFromMasterDataApi();

      if (externalCountries.isEmpty()) {
        log.warn("No countries received from MW Master Data API");
        return new CountrySyncResponseDTO(0, 0, 0, "No countries found in MW Master Data API");
      }

      log.info("Retrieved {} countries from MW Master Data API", externalCountries.size());

      List<String> countryIds = externalCountries.stream().map(MwCountryDTO::getCountryId).toList();

      Map<String, Country> existingCountriesMap =
          countryRepository.findAll().stream()
              .filter(country -> countryIds.contains(country.getCountryId()))
              .collect(Collectors.toMap(Country::getCountryId, country -> country));

      int updatedCount = 0;
      int createdCount = 0;
      List<Country> countriesToSave = new ArrayList<>();

      // Process each country with batch operations
      for (MwCountryDTO externalCountry : externalCountries) {
        try {
          Country existingCountry = existingCountriesMap.get(externalCountry.getCountryId());
          Country processedCountry;

          if (existingCountry != null) {
            processedCountry = updateExistingCountry(existingCountry, externalCountry);
            updatedCount++;
          } else {
            processedCountry = createNewCountry(externalCountry);
            createdCount++;
          }

          countriesToSave.add(processedCountry);
        } catch (Exception e) {
          log.error(
              "Failed to process country {}: {}", externalCountry.getCountryId(), e.getMessage());
        }
      }

      if (!countriesToSave.isEmpty()) {
        countryRepository.saveAll(countriesToSave);
        log.debug("Batch saved {} countries", countriesToSave.size());
      }

      int totalSynced = updatedCount + createdCount;
      String message =
          String.format(
              "Successfully synced %d countries (%d updated, %d created)",
              totalSynced, updatedCount, createdCount);

      log.info("Country sync completed: {}", message);
      return new CountrySyncResponseDTO(totalSynced, updatedCount, createdCount, message);

    } catch (Exception e) {
      log.error("Failed to sync countries from MW Master Data API", e);
      throw new MasterDataApiException(
          ErrorCode.MASTER_DATA_API_ERROR,
          "Failed to sync countries from MW Master Data API: " + e.getMessage());
    }
  }

  /** Update existing country with external data */
  private Country updateExistingCountry(Country existingCountry, MwCountryDTO externalCountry) {
    log.debug("Updating existing country: {}", externalCountry.getCountryId());

    // Update fields from external data
    existingCountry.setName(externalCountry.getName());
    existingCountry.setLatitude(externalCountry.getLatitude());
    existingCountry.setLongitude(externalCountry.getLongitude());
    existingCountry.setZoom(externalCountry.getZoom());
    existingCountry.setMediaOwnerTermsAndConditions(
        externalCountry.getMediaOwnerTermsAndConditions());
    existingCountry.setBuyerTermsAndConditions(externalCountry.getBuyerTermsAndConditions());
    existingCountry.setPopulation(externalCountry.getPopulation());
    existingCountry.setIso(externalCountry.getIso());
    existingCountry.setPostalformat(externalCountry.getPostalformat());
    existingCountry.setPostalname(externalCountry.getPostalname());
    existingCountry.setActive(externalCountry.getActive());
    existingCountry.setDialingCode(externalCountry.getDialingCode());
    existingCountry.setTimezone(externalCountry.getTimezone());

    // Update tax information
    if (externalCountry.getTax() != null) {
      Country.Tax tax = new Country.Tax();
      tax.setLabel(externalCountry.getTax().getLabel());
      tax.setPercent(externalCountry.getTax().getPercent());
      existingCountry.setTax(tax);
    } else {
      existingCountry.setTax(null);
    }

    return existingCountry;
  }

  /** Create new country from external data */
  private Country createNewCountry(MwCountryDTO externalCountry) {
    log.debug("Creating new country: {}", externalCountry.getCountryId());

    Country newCountry = new Country();
    newCountry.setId(externalCountry.getId());
    newCountry.setCountryId(externalCountry.getCountryId());
    newCountry.setName(externalCountry.getName());
    newCountry.setLatitude(externalCountry.getLatitude());
    newCountry.setLongitude(externalCountry.getLongitude());
    newCountry.setZoom(externalCountry.getZoom());
    newCountry.setMediaOwnerTermsAndConditions(externalCountry.getMediaOwnerTermsAndConditions());
    newCountry.setBuyerTermsAndConditions(externalCountry.getBuyerTermsAndConditions());
    newCountry.setPopulation(externalCountry.getPopulation());
    newCountry.setIso(externalCountry.getIso());
    newCountry.setPostalformat(externalCountry.getPostalformat());
    newCountry.setPostalname(externalCountry.getPostalname());
    newCountry.setActive(externalCountry.getActive());
    newCountry.setDialingCode(externalCountry.getDialingCode());
    newCountry.setTimezone(externalCountry.getTimezone());

    // Set tax information
    if (externalCountry.getTax() != null) {
      Country.Tax tax = new Country.Tax();
      tax.setLabel(externalCountry.getTax().getLabel());
      tax.setPercent(externalCountry.getTax().getPercent());
      newCountry.setTax(tax);
    }

    return newCountry;
  }

  // ========== CRUD Operations ==========

  /** Create a new country */
  @Transactional
  @CachePut(value = "countries", key = "#result.countryId")
  public CountryResponseDTO createCountry(CountryRequestDTO countryRequestDTO) {
    log.debug("Creating new country with countryId: {}", countryRequestDTO.getCountryId());

    // Check if country already exists by countryId
    if (countryRepository.existsByCountryId(countryRequestDTO.getCountryId())) {
      throw new CountryAlreadyExistsException("countryId", countryRequestDTO.getCountryId());
    }

    // Check if country already exists by ISO
    if (countryRepository.existsByIso(countryRequestDTO.getIso())) {
      throw new CountryAlreadyExistsException("ISO", countryRequestDTO.getIso());
    }

    Country country = mapRequestDTOToCountry(countryRequestDTO);
    Country savedCountry = countryRepository.save(country);
    log.debug("Country created successfully with ID: {}", savedCountry.getId());

    return mapCountryToResponseDTO(savedCountry);
  }

  /** Get country by ID */
  @Cacheable(value = "countries", key = "#id")
  public CountryResponseDTO getCountryById(String id) {
    log.debug("Fetching country by ID: {}", id);
    Country country =
        countryRepository.findById(id).orElseThrow(() -> new CountryNotFoundException(id));
    return mapCountryToResponseDTO(country);
  }

  @Cacheable(value = "countries", key = "#countryId")
  public CountryResponseDTO getCountryByCountryId(String countryId) {
    log.debug("Fetching country by country ID: {}", countryId);
    Country country =
        countryRepository
            .findByCountryId(countryId)
            .orElseThrow(() -> new CountryNotFoundException(countryId));
    return mapCountryToResponseDTO(country);
  }

  /** Get country by name */
  @Cacheable(value = "countries", key = "#name")
  public CountryResponseDTO getCountryByName(String name) {
    log.debug("Fetching country by name: {}", name);
    Country country =
        countryRepository.findByName(name).orElseThrow(() -> new CountryNotFoundException(name));
    return mapCountryToResponseDTO(country);
  }

  /** Get country entity by name */
  @Cacheable(value = "countries", key = "#name + '_entity'")
  public Optional<Country> findByName(String name) {
    log.debug("Fetching country entity by name: {}", name);
    return countryRepository.findByName(name);
  }

  /** Check if country exists by name */
  public void validateCountryExists(String countryName) {
    if (!countryRepository.existsByName(countryName)) {
      throw new CountryNotFoundException(countryName);
    }
  }

  /** Update country */
  @Transactional
  @CachePut(value = "countries", key = "#id")
  public CountryResponseDTO updateCountry(String id, CountryRequestDTO countryRequestDTO) {
    log.debug("Updating country with ID: {}", id);

    Country existingCountry =
        countryRepository.findById(id).orElseThrow(() -> new CountryNotFoundException(id));

    // Check if countryId is being changed and if the new countryId already exists
    if (!existingCountry.getCountryId().equals(countryRequestDTO.getCountryId())) {
      if (countryRepository.existsByCountryId(countryRequestDTO.getCountryId())) {
        throw new CountryAlreadyExistsException("countryId", countryRequestDTO.getCountryId());
      }
    }

    // Check if ISO is being changed and if the new ISO already exists
    if (!existingCountry.getIso().equals(countryRequestDTO.getIso())) {
      if (countryRepository.existsByIso(countryRequestDTO.getIso())) {
        throw new CountryAlreadyExistsException("ISO", countryRequestDTO.getIso());
      }
    }

    updateCountryFromRequestDTO(existingCountry, countryRequestDTO);
    Country savedCountry = countryRepository.save(existingCountry);
    log.debug("Country updated successfully with ID: {}", savedCountry.getId());

    return mapCountryToResponseDTO(savedCountry);
  }

  /** Get all countries with pagination */
  public Page<CountryResponseDTO> getAllCountries(Pageable pageable) {
    log.debug("Fetching all countries with pagination: {}", pageable);
    Page<Country> countries = countryRepository.findAll(pageable);
    return countries.map(this::mapCountryToResponseDTO);
  }

  /** Get market details for countries based on company's market access */
  public List<CountryMarketDetailsDTO> getCountryMarketDetails(
      String companyId, List<String> countryIds, List<String> countryIso) {
    log.debug("Fetching country market details for company: {}", companyId);

    // TODO get country based on logged in company has marketAccess
    // Get company to access marketAccess
    //    Company company = companyService.getCompanyById(companyId);
    //    List<String> marketAccess = company.getMarketAccess();
    //
    //    if (marketAccess == null || marketAccess.isEmpty()) {
    //      log.debug("No market access found for company: {}", companyId);
    //      return new ArrayList<>();
    //    }
    //
    //    // Get countries by country IDs from marketAccess
    //    List<Country> countries = countryRepository.findAllById(marketAccess);
    // countryIds takes precedence over countryIso; only one filter is ever applied
    List<Country> countries;
    if (countryIds != null && !countryIds.isEmpty()) {
      countries = countryRepository.findByIdIn(countryIds);
    } else if (countryIso != null && !countryIso.isEmpty()) {
      countries = countryRepository.findByIsoIn(countryIso);
    } else {
      countries = countryRepository.findAll();
    }

    // Map countries to market details using streams for better performance
    List<CountryMarketDetailsDTO> marketDetails = getMarketDetails(countries);

    log.debug("Found {} market details for company: {}", marketDetails.size(), companyId);
    return marketDetails;
  }

  private List<CountryMarketDetailsDTO> getMarketDetails(List<Country> countries) {
    if (countries.isEmpty()) {
      return List.of();
    }
    List<String> countryNames =
        countries.stream().map(Country::getName).filter(Objects::nonNull).toList();

    // Read pre-aggregated counts from the materialized summary collection instead of aggregating
    // the whole inventories collection at request time. Keyed lowercase to match the lookup below.
    Map<String, Map<String, Long>> normalizedCounts =
        inventoryCountrySummaryRepository.findByCountryIn(countryNames).stream()
            .filter(s -> s.getCountry() != null && s.getClassificationCounts() != null)
            .collect(
                Collectors.toMap(
                    s -> s.getCountry().toLowerCase(),
                    InventoryCountrySummary::getClassificationCounts,
                    (a, b) -> a));

    return countries.stream()
        .map(
            country -> {
              Map<String, Long> classificationCounts =
                  normalizedCounts.getOrDefault(
                      country.getName() != null ? country.getName().toLowerCase() : "", Map.of());
              long totalCount =
                  classificationCounts.values().stream().mapToLong(Long::longValue).sum();

              return CountryMarketDetailsDTO.builder()
                  .id(country.getId())
                  .countryId(country.getCountryId())
                  .countryName(country.getName())
                  .population(country.getPopulation())
                  .inventoryCount(totalCount)
                  .inventoryCountByClassification(classificationCounts)
                  .impressions(country.getImpressions())
                  .build();
            })
        .collect(Collectors.toList());
  }

  // ========== Mapping Methods ==========

  /** Map CountryRequestDTO to Country entity */
  private Country mapRequestDTOToCountry(CountryRequestDTO requestDTO) {
    Country country = new Country();
    country.setCountryId(requestDTO.getCountryId());
    country.setName(requestDTO.getName());
    country.setLatitude(requestDTO.getLatitude());
    country.setLongitude(requestDTO.getLongitude());
    country.setZoom(requestDTO.getZoom());
    country.setMediaOwnerTermsAndConditions(requestDTO.getMediaOwnerTermsAndConditions());
    country.setBuyerTermsAndConditions(requestDTO.getBuyerTermsAndConditions());
    country.setPopulation(requestDTO.getPopulation());
    country.setIso(requestDTO.getIso());
    country.setPostalformat(requestDTO.getPostalformat());
    country.setPostalname(requestDTO.getPostalname());
    country.setActive(requestDTO.getActive());
    country.setDialingCode(requestDTO.getDialingCode());
    country.setTimezone(requestDTO.getTimezone());

    // Map tax information if present
    if (requestDTO.getTax() != null) {
      Country.Tax tax = new Country.Tax();
      tax.setLabel(requestDTO.getTax().getLabel());
      tax.setPercent(requestDTO.getTax().getPercent());
      country.setTax(tax);
    }

    return country;
  }

  /** Update existing Country entity with data from CountryRequestDTO */
  private void updateCountryFromRequestDTO(Country country, CountryRequestDTO requestDTO) {
    country.setCountryId(requestDTO.getCountryId());
    country.setName(requestDTO.getName());
    country.setLatitude(requestDTO.getLatitude());
    country.setLongitude(requestDTO.getLongitude());
    country.setZoom(requestDTO.getZoom());
    country.setMediaOwnerTermsAndConditions(requestDTO.getMediaOwnerTermsAndConditions());
    country.setBuyerTermsAndConditions(requestDTO.getBuyerTermsAndConditions());
    country.setPopulation(requestDTO.getPopulation());
    country.setIso(requestDTO.getIso());
    country.setPostalformat(requestDTO.getPostalformat());
    country.setPostalname(requestDTO.getPostalname());
    country.setActive(requestDTO.getActive());
    country.setDialingCode(requestDTO.getDialingCode());
    country.setTimezone(requestDTO.getTimezone());

    // Update tax information if present
    if (requestDTO.getTax() != null) {
      Country.Tax tax = new Country.Tax();
      tax.setLabel(requestDTO.getTax().getLabel());
      tax.setPercent(requestDTO.getTax().getPercent());
      country.setTax(tax);
    } else {
      country.setTax(null);
    }
  }

  /** Map Country entity to CountryResponseDTO */
  public CountryResponseDTO mapCountryToResponseDTO(Country country) {
    CountryResponseDTO.CountryResponseDTOBuilder builder =
        CountryResponseDTO.builder()
            .id(country.getId())
            .countryId(country.getCountryId())
            .name(country.getName())
            .latitude(country.getLatitude())
            .longitude(country.getLongitude())
            .zoom(country.getZoom())
            .mediaOwnerTermsAndConditions(country.getMediaOwnerTermsAndConditions())
            .buyerTermsAndConditions(country.getBuyerTermsAndConditions())
            .population(country.getPopulation())
            .iso(country.getIso())
            .postalformat(country.getPostalformat())
            .postalname(country.getPostalname())
            .active(country.getActive())
            .dialingCode(country.getDialingCode())
            .timezone(country.getTimezone())
            .createdAt(country.getCreatedAt())
            .updatedAt(country.getUpdatedAt());

    // Map tax information if present
    if (country.getTax() != null) {
      CountryResponseDTO.Tax tax = new CountryResponseDTO.Tax();
      tax.setLabel(country.getTax().getLabel());
      tax.setPercent(country.getTax().getPercent());
      builder.tax(tax);
    }

    return builder.build();
  }
}
