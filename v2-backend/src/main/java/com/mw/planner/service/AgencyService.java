package com.mw.planner.service;

import com.mw.planner.domain.Agency;
import com.mw.planner.domain.Country;
import com.mw.planner.dto.*;
import com.mw.planner.exception.agency.AgencyAlreadyExistsException;
import com.mw.planner.exception.agency.AgencyCreationException;
import com.mw.planner.exception.agency.AgencyNotFoundException;
import com.mw.planner.exception.agency.AgencyUpdateException;
import com.mw.planner.exception.country.CountryInactiveException;
import com.mw.planner.exception.country.CountryNotFoundException;
import com.mw.planner.repository.AgencyRepository;
import com.mw.planner.repository.CountryRepository;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgencyService {

  private final AgencyRepository agencyRepository;
  private final CountryRepository countryRepository;

  /** Get agency by ID */
  @Cacheable(value = "agencies", key = "#id")
  public AgencyResponseDTO getAgencyById(String id) {
    log.debug("Fetching agency by ID: {}", id);
    return mapToAgencyResponseDTO(
        agencyRepository.findById(id).orElseThrow(() -> new AgencyNotFoundException(id)));
  }

  /** Create a new agency */
  @Transactional
  @CachePut(value = "agencies", key = "#result.id")
  public AgencyResponseDTO createAgency(AgencyRequestDTO agencyRequestDTO) {
    log.debug("Creating new agency with name: {}", agencyRequestDTO.getName());

    try {
      // Validate country is active if provided
      validateCountryForAgency(agencyRequestDTO.getCountryId());

      // Check if agency already exists by name
      if (agencyRepository.existsByName(agencyRequestDTO.getName())) {
        throw new AgencyAlreadyExistsException(agencyRequestDTO.getName());
      }

      // TODO Create company during Agency creation
      // Get current user context
      // IamUserContext userContext = userService.getIamUserContext();
      // Create company in MW API and planner
      //      String companyId = createCompanyForAgency(agencyRequestDTO, userContext);
      // Set the company ID in the agency
      //      agencyRequestDTO.setCompanyId(companyId);

      Agency agency = mapToAgency(agencyRequestDTO);
      agency.setActivated(true);
      Agency savedAgency = agencyRepository.save(agency);
      log.debug("Agency created successfully with ID: {}", savedAgency.getId());

      return mapToAgencyResponseDTO(savedAgency);
    } catch (Exception e) {
      log.error("Failed to create agency: {}", e.getMessage(), e);
      if (e instanceof AgencyAlreadyExistsException || e instanceof CountryInactiveException) {
        throw e;
      }
      throw new AgencyCreationException("Failed to create agency: " + e.getMessage());
    }
  }

  /** Update agency */
  @Transactional
  @CachePut(value = "agencies", key = "#id")
  public AgencyResponseDTO updateAgency(String id, AgencyRequestDTO agencyRequestDTO) {
    log.debug("Updating agency with ID: {}", id);

    try {
      Agency existingAgency =
          agencyRepository.findById(id).orElseThrow(() -> new AgencyNotFoundException(id));

      // Validate country is active if provided
      validateCountryForAgency(agencyRequestDTO.getCountryId());

      // Check if name is being changed and if new name already exists
      if (!existingAgency.getName().equals(agencyRequestDTO.getName())) {
        Optional<Agency> existingAgencyWithName =
            agencyRepository.findByName(agencyRequestDTO.getName());
        if (existingAgencyWithName.isPresent()
            && !existingAgencyWithName.get().getId().equals(id)) {
          throw new AgencyAlreadyExistsException(agencyRequestDTO.getName());
        }
      }

      Agency agency = updateAgencyFromDTO(existingAgency, agencyRequestDTO);
      Agency updatedAgency = agencyRepository.save(agency);
      log.debug("Agency updated successfully with ID: {}", updatedAgency.getId());

      return mapToAgencyResponseDTO(updatedAgency);
    } catch (Exception e) {
      log.error("Failed to update agency with ID {}: {}", id, e.getMessage(), e);
      if (e instanceof AgencyNotFoundException
          || e instanceof AgencyAlreadyExistsException
          || e instanceof CountryInactiveException) {
        throw e;
      }
      throw new AgencyUpdateException(id, e.getMessage());
    }
  }

  /** Get all agencies with pagination and optional search */
  public Page<AgencyResponseDTO> getAllAgencies(Pageable pageable, String search) {
    log.debug(
        "Fetching all agencies with pagination: page={}, size={}, search={}",
        pageable.getPageNumber(),
        pageable.getPageSize(),
        search);

    Page<Agency> agencyPage;
    if (search != null && !search.trim().isEmpty()) {
      Pattern pattern = Pattern.compile(Pattern.quote(search.trim()), Pattern.CASE_INSENSITIVE);
      agencyPage = agencyRepository.findByNameRegex(pattern, pageable);
    } else {
      agencyPage = agencyRepository.findAll(pageable);
    }

    return agencyPage.map(this::mapToAgencyResponseDTO);
  }

  // ========== Helper Methods ==========

  @Cacheable(value = "countryNames", key = "#countryId")
  private String getCountryName(String countryId) {
    if (countryId == null) {
      return "Unknown";
    }

    return countryRepository.findById(countryId).map(Country::getName).orElse("Unknown");
  }

  /** Validate country for agency operations */
  private void validateCountryForAgency(String countryId) {
    if (countryId == null) {
      return;
    }

    Optional<Country> country = countryRepository.findById(countryId);
    if (country.isEmpty()) {
      throw new CountryNotFoundException(countryId, "Country does not exist");
    }

    if (!country.get().getActive()) {
      throw new CountryInactiveException(countryId);
    }
  }

  // ========== Mapping Methods ==========

  private Agency mapToAgency(AgencyRequestDTO agencyRequestDTO) {
    Agency agency = new Agency();
    agency.setName(agencyRequestDTO.getName());
    agency.setMediaOwnerId(agencyRequestDTO.getMediaOwnerId());
    agency.setCompanyEmail(agencyRequestDTO.getCompanyEmail());
    agency.setCountryId(agencyRequestDTO.getCountryId());
    agency.setCompanyId(agencyRequestDTO.getCompanyId());
    agency.setSeatId(agencyRequestDTO.getSeatId());
    agency.setBrandRefId(agencyRequestDTO.getBrandRefId());
    return agency;
  }

  public AgencyResponseDTO mapToAgencyResponseDTO(Agency agency) {
    String countryName = null;
    if (agency.getCountryId() != null) {
      countryName = getCountryName(agency.getCountryId());
    }

    return AgencyResponseDTO.builder()
        .id(agency.getId())
        .name(agency.getName())
        .mediaOwnerId(agency.getMediaOwnerId())
        .companyEmail(agency.getCompanyEmail())
        .countryId(agency.getCountryId())
        .countryName(countryName)
        .companyId(agency.getCompanyId())
        .seatId(agency.getSeatId())
        .brandRefId(agency.getBrandRefId())
        .activated(agency.isActivated())
        .createdAt(agency.getCreatedAt())
        .updatedAt(agency.getUpdatedAt())
        .build();
  }

  private Agency updateAgencyFromDTO(Agency agency, AgencyRequestDTO agencyRequestDTO) {
    agency.setName(agencyRequestDTO.getName());
    agency.setMediaOwnerId(agencyRequestDTO.getMediaOwnerId());
    agency.setCompanyEmail(agencyRequestDTO.getCompanyEmail());
    agency.setCountryId(agencyRequestDTO.getCountryId());
    agency.setCompanyId(agencyRequestDTO.getCompanyId());
    agency.setSeatId(agencyRequestDTO.getSeatId());
    agency.setBrandRefId(agencyRequestDTO.getBrandRefId());
    return agency;
  }

  @Cacheable(value = "agencyName", key = "#id")
  public String getNameById(String id) {
    log.debug("Fetching agency name by ID: {}", id);
    return agencyRepository.findById(id).map(Agency::getName).orElse("Unknown");
  }
}
