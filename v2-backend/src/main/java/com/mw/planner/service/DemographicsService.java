package com.mw.planner.service;

import com.mw.planner.domain.Demographics;
import com.mw.planner.dto.*;
import com.mw.planner.enums.DemographicsType;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.demographics.DemographicsValidationException;
import com.mw.planner.repository.DemographicsRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemographicsService {

  private final DemographicsRepository demographicsRepository;

  /** Get demographics by ID */
  @Cacheable(value = "demographics", key = "#id")
  public DemographicsResponseDTO getDemographicsById(String id) {
    log.debug("Fetching demographics by ID: {}", id);
    Demographics demographics =
        demographicsRepository
            .findById(id)
            .orElseThrow(() -> new RuntimeException("Demographics not found with id: " + id));
    return mapToDemographicsResponseDTO(demographics);
  }

  /** Get grouped demographics for API response */
  public DemographicsConfigResponseDTO getConfigDemographics() {
    log.debug("Fetching grouped demographics from database");

    List<DemographicItemDTO> age = new ArrayList<>();
    List<DemographicItemDTO> gender = new ArrayList<>();
    List<DemographicItemDTO> income = new ArrayList<>();

    List<Demographics> allDemographics = demographicsRepository.findAll();

    for (Demographics demo : allDemographics) {
      DemographicItemDTO item =
          new DemographicItemDTO(demo.getDemoKey(), demo.getName(), demo.getDescription());

      switch (demo.getDemoType()) {
        case AGE:
          age.add(item);
          break;
        case GENDER:
          gender.add(item);
          break;
        case INCOME:
          income.add(item);
          break;
      }
    }

    return new DemographicsConfigResponseDTO(age, gender, income);
  }

  /** Auto-save demographics by demoType and demoKey */
  @Transactional
  @CachePut(value = "demographics", key = "#result.id")
  public DemographicsResponseDTO autoSaveDemographic(
      DemographicAutoSaveRequestDTO requestDTO, String countryId) {
    log.debug(
        "Auto-saving demographics for demoType: {} and demoKey: {}",
        requestDTO.getDemoType(),
        requestDTO.getDemoKey());

    validateDemoTypeAndKey(requestDTO);

    // Validate demoType
    DemographicsType demoType;
    try {
      demoType = DemographicsType.valueOf(requestDTO.getDemoType().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new DemographicsValidationException(
          ErrorCode.DEMOGRAPHICS_INVALID_TYPE, "Invalid demoType: " + requestDTO.getDemoType());
    }

    // Find existing demographics by demoType and demoKey
    Demographics existingDemographics =
        demographicsRepository
            .findByDemoTypeAndDemoKey(demoType, requestDTO.getDemoKey())
            .orElseThrow(
                () ->
                    new DemographicsValidationException(
                        ErrorCode.DEMOGRAPHICS_NOT_FOUND,
                        "Demographics not found with demoType: "
                            + demoType
                            + " and demoKey: "
                            + requestDTO.getDemoKey()));

    // Update name and description
    updateDemographicsForAutoSave(existingDemographics, requestDTO);
    existingDemographics.setCountryId(countryId);

    Demographics updatedDemographics = demographicsRepository.save(existingDemographics);
    log.debug("Demographics auto-save successfully with ID: {}", updatedDemographics.getId());

    return mapToDemographicsResponseDTO(updatedDemographics);
  }

  private static void validateDemoTypeAndKey(DemographicAutoSaveRequestDTO requestDTO) {
    // Validate demoType is not null or empty
    if (requestDTO.getDemoType() == null || requestDTO.getDemoType().trim().isEmpty()) {
      throw new DemographicsValidationException(
          ErrorCode.DEMOGRAPHICS_INVALID_TYPE, "Demographics type cannot be null or empty");
    }
    // Validate demoKey is not null or empty
    if (requestDTO.getDemoKey() == null || requestDTO.getDemoKey().trim().isEmpty()) {
      throw new DemographicsValidationException(
          ErrorCode.DEMOGRAPHICS_INVALID_KEY, "DemoKey cannot be null or empty");
    }
  }

  /** Map Demographics entity to DemographicsResponseDTO */
  private DemographicsResponseDTO mapToDemographicsResponseDTO(Demographics demographics) {
    DemographicsResponseDTO responseDTO = new DemographicsResponseDTO();
    responseDTO.setId(demographics.getId());
    responseDTO.setDemoType(demographics.getDemoType());
    responseDTO.setDemoKey(demographics.getDemoKey());
    responseDTO.setName(demographics.getName());
    responseDTO.setDescription(demographics.getDescription());
    responseDTO.setCountryId(demographics.getCountryId());
    responseDTO.setCreatedBy(demographics.getCreatedBy());
    responseDTO.setLastModifiedBy(demographics.getLastModifiedBy());
    responseDTO.setCreatedAt(demographics.getCreatedAt());
    responseDTO.setUpdatedAt(demographics.getUpdatedAt());
    return responseDTO;
  }

  /** Update Demographics entity from DTO */
  private void updateDemographicsForAutoSave(
      Demographics demographics, DemographicAutoSaveRequestDTO requestDTO) {
    if (requestDTO.getName() != null) {
      demographics.setName(requestDTO.getName());
    }
    if (requestDTO.getDescription() != null) {
      demographics.setDescription(requestDTO.getDescription());
    }
  }
}
