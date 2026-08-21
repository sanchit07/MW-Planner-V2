package com.mw.planner.service;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.CustomFee;
import com.mw.planner.dto.BulkCustomFeeRequestDTO;
import com.mw.planner.dto.CompanyCustomFees;
import com.mw.planner.dto.CustomFeeRequestDTO;
import com.mw.planner.dto.CustomFeeResponseDTO;
import com.mw.planner.dto.CustomFeesContext;
import com.mw.planner.exception.customfee.CustomFeeAlreadyExistsException;
import com.mw.planner.exception.customfee.CustomFeeCreationException;
import com.mw.planner.exception.customfee.CustomFeeNotFoundException;
import com.mw.planner.exception.customfee.CustomFeeUpdateException;
import com.mw.planner.exception.customfee.CustomFeeValidationException;
import com.mw.planner.repository.CampaignInventorySchedulesRepository;
import com.mw.planner.repository.CustomFeeRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomFeeService {

  private final CustomFeeRepository customFeeRepository;
  private final UserService userService;
  private final CampaignInventorySchedulesRepository campaignInventorySchedulesRepository;

  /** Get custom fee by ID */
  @Cacheable(value = "customFees", key = "#id")
  public CustomFeeResponseDTO getCustomFeeById(String id) {
    log.debug("Fetching custom fee by ID: {}", id);
    CustomFee customFee =
        customFeeRepository.findById(id).orElseThrow(() -> new CustomFeeNotFoundException(id));
    return mapToCustomFeeResponseDTO(customFee);
  }

  /** Get all custom fees by company ID and optional campaign ID (null = company-level fees). */
  public List<CustomFeeResponseDTO> getCustomFeesByCompanyAndCampaign(
      String companyId, String campaignId) {
    log.debug("Fetching custom fees by companyId={}, campaignId={}", companyId, campaignId);
    List<CustomFee> customFees =
        customFeeRepository.findByCompanyIdAndCampaignId(companyId, campaignId);
    return customFees.stream().map(this::mapToCustomFeeResponseDTO).collect(Collectors.toList());
  }

  /**
   * Build a custom fees context for a campaign: company-level and campaign-level fees for the
   * campaign creator and all media owners (companyAccess), in two batch repository calls. Call once
   * per campaign and pass the context through price calculations to avoid repeated repository
   * calls.
   *
   * @param campaign Campaign (must have getId(), getCompanyId(), getCompanyAccess())
   * @return CustomFeesContext with fees grouped by companyId and split hidden/visible; empty maps
   *     if no company IDs
   */
  public CustomFeesContext getActiveCustomFeesContextForCampaign(Campaign campaign) {
    if (campaign == null) {
      return CustomFeesContext.builder().build();
    }
    Set<String> companyIds = new LinkedHashSet<>();
    if (!campaign.getCompanyId().isBlank()) {
      companyIds.add(campaign.getCompanyId());
    }
    if (campaign.getCompanyAccess() != null) {
      campaign.getCompanyAccess().stream()
          .filter(id -> id != null && !id.isBlank())
          .forEach(companyIds::add);
    }
    if (companyIds.isEmpty()) {
      log.debug(
          "No company IDs for campaign {}, returning empty custom fees context", campaign.getId());
      return CustomFeesContext.builder().build();
    }
    List<String> companyIdList = new ArrayList<>(companyIds);
    log.debug(
        "Fetching active custom fees context for campaign {} and {} company IDs",
        campaign.getId(),
        companyIdList.size());

    List<CustomFee> companyLevelFees =
        customFeeRepository.findByCompanyIdInAndCampaignIdIsNullAndIsActiveTrue(companyIdList);
    List<CustomFee> campaignLevelFees =
        customFeeRepository.findByCampaignIdAndCompanyIdInAndIsActiveTrue(
            campaign.getId(), companyIdList);

    Map<String, CompanyCustomFees> companyFeesByCompanyId =
        groupAndSplitByCompany(companyLevelFees);
    Map<String, CompanyCustomFees> campaignFeesByCompanyId =
        groupAndSplitByCompany(campaignLevelFees);

    return CustomFeesContext.builder()
        .companyFeesByCompanyId(companyFeesByCompanyId)
        .campaignFeesByCompanyId(campaignFeesByCompanyId)
        .build();
  }

  /**
   * Bulk build active custom fees context for multiple campaigns.
   *
   * <p>Loads company-level fees once for the union of all companies, and campaign-level fees once
   * for the union of (campaignIds x companyIds). This avoids 2*N repository calls for N campaigns.
   *
   * @param campaigns list of campaigns (must have id/companyId/companyAccess)
   * @return map of campaignId to CustomFeesContext
   */
  public Map<String, CustomFeesContext> getActiveCustomFeesContextForCampaigns(
      List<Campaign> campaigns) {
    if (campaigns == null || campaigns.isEmpty()) {
      return Map.of();
    }

    List<Campaign> valid = campaigns.stream().filter(c -> c != null && c.getId() != null).toList();
    if (valid.isEmpty()) {
      return Map.of();
    }

    Set<String> companyIds = new LinkedHashSet<>();
    List<String> campaignIds =
        valid.stream()
            .map(Campaign::getId)
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .toList();

    for (Campaign c : valid) {
      if (c.getCompanyId() != null && !c.getCompanyId().isBlank()) {
        companyIds.add(c.getCompanyId());
      }
      if (c.getCompanyAccess() != null) {
        c.getCompanyAccess().stream()
            .filter(id -> id != null && !id.isBlank())
            .forEach(companyIds::add);
      }
    }

    if (companyIds.isEmpty() || campaignIds.isEmpty()) {
      // Still return an entry per campaignId to simplify callers.
      Map<String, CustomFeesContext> empty = new HashMap<>();
      for (String id : campaignIds) {
        empty.put(id, CustomFeesContext.builder().build());
      }
      return Map.copyOf(empty);
    }

    List<String> companyIdList = companyIds.stream().toList();

    List<CustomFee> companyLevelFees =
        customFeeRepository.findByCompanyIdInAndCampaignIdIsNullAndIsActiveTrue(companyIdList);
    Map<String, CompanyCustomFees> companyFeesByCompanyId =
        groupAndSplitByCompany(companyLevelFees);

    List<CustomFee> campaignLevelFees =
        customFeeRepository.findByCampaignIdInAndCompanyIdInAndIsActiveTrue(
            campaignIds, companyIdList);

    Map<String, Map<String, CompanyCustomFees>> campaignFeesByCampaignId =
        campaignLevelFees.stream()
            .filter(Objects::nonNull)
            .filter(f -> f.getCampaignId() != null && f.getCompanyId() != null)
            .collect(
                Collectors.groupingBy(
                    CustomFee::getCampaignId,
                    Collectors.collectingAndThen(
                        Collectors.toList(), CustomFeeService::groupAndSplitByCompany)));

    Map<String, CustomFeesContext> result = new HashMap<>();
    for (String campaignId : campaignIds) {
      result.put(
          campaignId,
          CustomFeesContext.builder()
              .companyFeesByCompanyId(companyFeesByCompanyId)
              .campaignFeesByCompanyId(campaignFeesByCampaignId.getOrDefault(campaignId, Map.of()))
              .build());
    }
    return Map.copyOf(result);
  }

  private static Map<String, CompanyCustomFees> groupAndSplitByCompany(List<CustomFee> fees) {
    if (fees == null || fees.isEmpty()) {
      return Map.of();
    }
    return fees.stream()
        .collect(
            Collectors.groupingBy(
                CustomFee::getCompanyId,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    list -> {
                      List<CustomFee> hidden =
                          list.stream()
                              .filter(f -> !Boolean.TRUE.equals(f.getIsIncludeInMediaPlan()))
                              .toList();
                      List<CustomFee> visible =
                          list.stream()
                              .filter(f -> Boolean.TRUE.equals(f.getIsIncludeInMediaPlan()))
                              .toList();
                      return CompanyCustomFees.builder().hidden(hidden).visible(visible).build();
                    })));
  }

  /** Create a new custom fee */
  @CachePut(value = "customFees", key = "#result.id")
  public CustomFeeResponseDTO createCustomFee(CustomFeeRequestDTO customFeeRequestDTO) {
    return createCustomFee(customFeeRequestDTO, true);
  }

  /**
   * Create a new custom fee with option to reset campaign approvals.
   *
   * @param customFeeRequestDTO Custom fee request DTO
   * @param resetApprovals Whether to reset campaign approvals (set to false when called from bulk
   *     operations)
   * @return Custom fee response DTO
   */
  @CachePut(value = "customFees", key = "#result.id")
  public CustomFeeResponseDTO createCustomFee(
      CustomFeeRequestDTO customFeeRequestDTO, boolean resetApprovals) {
    log.debug("Creating new custom fee with name: {}", customFeeRequestDTO.getName());

    try {
      String companyId = userService.getActingCompanyId();
      if (companyId == null || companyId.trim().isEmpty()) {
        throw new CustomFeeValidationException(
            "Unable to determine primary company ID for the current user");
      }
      String campaignId = normalizeCampaignId(customFeeRequestDTO.getCampaignId());

      // Check if custom fee already exists by name, company ID, and campaign ID
      if (customFeeRepository.existsByNameAndCompanyIdAndCampaignId(
          customFeeRequestDTO.getName(), companyId, campaignId)) {
        throw new CustomFeeAlreadyExistsException(customFeeRequestDTO.getName());
      }

      CustomFee customFee = mapToCustomFee(customFeeRequestDTO, companyId, campaignId);
      CustomFee savedCustomFee = customFeeRepository.save(customFee);
      log.debug("Custom fee created successfully with ID: {}", savedCustomFee.getId());

      // Reset campaign approvals if this is a campaign custom fee and resetApprovals is true
      if (resetApprovals && campaignId != null) {
        resetCampaignApprovals(campaignId);
      }

      return mapToCustomFeeResponseDTO(savedCustomFee);
    } catch (Exception e) {
      log.error("Failed to create custom fee: {}", e.getMessage(), e);
      if (e instanceof CustomFeeAlreadyExistsException
          || e instanceof CustomFeeValidationException) {
        throw e;
      }
      throw new CustomFeeCreationException("Failed to create custom fee: " + e.getMessage(), e);
    }
  }

  /** Update custom fee */
  @CachePut(value = "customFees", key = "#id")
  public CustomFeeResponseDTO updateCustomFee(String id, CustomFeeRequestDTO customFeeRequestDTO) {
    return updateCustomFee(id, customFeeRequestDTO, true);
  }

  /**
   * Update custom fee with option to reset campaign approvals.
   *
   * @param id Custom fee ID
   * @param customFeeRequestDTO Custom fee request DTO
   * @param resetApprovals Whether to reset campaign approvals (set to false when called from bulk
   *     operations)
   * @return Custom fee response DTO
   */
  @CachePut(value = "customFees", key = "#id")
  public CustomFeeResponseDTO updateCustomFee(
      String id, CustomFeeRequestDTO customFeeRequestDTO, boolean resetApprovals) {
    log.debug("Updating custom fee with ID: {}", id);

    try {
      CustomFee existingCustomFee =
          customFeeRepository.findById(id).orElseThrow(() -> new CustomFeeNotFoundException(id));

      String companyId = existingCustomFee.getCompanyId();
      String campaignId = existingCustomFee.getCampaignId();

      // Check if name is being changed and if new name already exists for the same scope
      if (!existingCustomFee.getName().equals(customFeeRequestDTO.getName())) {
        if (customFeeRepository.existsByNameAndCompanyIdAndCampaignId(
            customFeeRequestDTO.getName(), companyId, campaignId)) {
          throw new CustomFeeAlreadyExistsException(customFeeRequestDTO.getName());
        }
      }

      CustomFee customFee = updateCustomFeeFromDTO(existingCustomFee, customFeeRequestDTO);
      CustomFee updatedCustomFee = customFeeRepository.save(customFee);
      log.debug("Custom fee updated successfully with ID: {}", updatedCustomFee.getId());

      // Reset campaign approvals if this is a campaign custom fee and resetApprovals is true
      if (resetApprovals && campaignId != null) {
        resetCampaignApprovals(campaignId);
      }

      return mapToCustomFeeResponseDTO(updatedCustomFee);
    } catch (Exception e) {
      log.error("Failed to update custom fee with ID {}: {}", id, e.getMessage(), e);
      if (e instanceof CustomFeeNotFoundException
          || e instanceof CustomFeeAlreadyExistsException
          || e instanceof CustomFeeValidationException) {
        throw e;
      }
      throw new CustomFeeUpdateException(id, e.getMessage(), e);
    }
  }

  /** Bulk create or update custom fees */
  public List<CustomFeeResponseDTO> bulkCreateOrUpdateCustomFees(
      List<BulkCustomFeeRequestDTO> bulkCustomFeeRequestDTOs) {
    log.debug("Bulk creating/updating {} custom fees", bulkCustomFeeRequestDTOs.size());

    if (bulkCustomFeeRequestDTOs.isEmpty()) {
      throw new CustomFeeValidationException("Custom fees list cannot be empty");
    }

    // Extract all non-null IDs and validate they exist
    List<String> idsToUpdate =
        bulkCustomFeeRequestDTOs.stream()
            .map(BulkCustomFeeRequestDTO::getId)
            .filter(id -> id != null && !id.trim().isEmpty())
            .collect(Collectors.toList());

    if (!idsToUpdate.isEmpty()) {
      // Validate all IDs exist before making any DB calls
      Set<String> existingIds =
          customFeeRepository.findAllById(idsToUpdate).stream()
              .map(CustomFee::getId)
              .collect(Collectors.toSet());

      List<String> invalidIds =
          idsToUpdate.stream().filter(id -> !existingIds.contains(id)).collect(Collectors.toList());

      if (!invalidIds.isEmpty()) {
        throw new CustomFeeValidationException(
            "Custom fees with the following IDs not found: " + String.join(", ", invalidIds));
      }
    }

    List<CustomFeeResponseDTO> results = new ArrayList<>();
    Set<String> campaignsToReset = new java.util.HashSet<>();

    // Process each custom fee (without resetting approvals individually)
    for (BulkCustomFeeRequestDTO bulkRequestDTO : bulkCustomFeeRequestDTOs) {
      try {
        if (bulkRequestDTO.getId() == null || bulkRequestDTO.getId().trim().isEmpty()) {
          // Create new custom fee (without resetting approvals)
          CustomFeeRequestDTO createRequestDTO = convertToCustomFeeRequestDTO(bulkRequestDTO);
          CustomFeeResponseDTO created = createCustomFee(createRequestDTO, false);
          results.add(created);

          // Track campaign if this is a campaign custom fee (reset will be done after all
          // processing)
          if (bulkRequestDTO.getCampaignId() != null
              && !bulkRequestDTO.getCampaignId().trim().isEmpty()) {
            campaignsToReset.add(bulkRequestDTO.getCampaignId());
          }
        } else {
          // Update existing custom fee (without resetting approvals)
          CustomFeeRequestDTO updateRequestDTO = convertToCustomFeeRequestDTO(bulkRequestDTO);
          CustomFeeResponseDTO updated =
              updateCustomFee(bulkRequestDTO.getId(), updateRequestDTO, false);
          results.add(updated);

          // Track campaign if this is a campaign custom fee (reset will be done after all
          // processing)
          CustomFee updatedFee = customFeeRepository.findById(bulkRequestDTO.getId()).orElse(null);
          if (updatedFee != null && updatedFee.getCampaignId() != null) {
            campaignsToReset.add(updatedFee.getCampaignId());
          }
        }
      } catch (Exception e) {
        log.error(
            "Failed to process custom fee with ID {}: {}",
            bulkRequestDTO.getId(),
            e.getMessage(),
            e);
        // Re-throw to maintain transaction rollback
        throw e;
      }
    }

    // Reset campaign approvals for all affected campaigns (after all custom fees are processed)
    for (String campaignId : campaignsToReset) {
      resetCampaignApprovals(campaignId);
    }

    log.debug("Successfully processed {} custom fees", results.size());
    return results;
  }

  /** Convert BulkCustomFeeRequestDTO to CustomFeeRequestDTO */
  private CustomFeeRequestDTO convertToCustomFeeRequestDTO(BulkCustomFeeRequestDTO bulkDTO) {
    return CustomFeeRequestDTO.builder()
        .name(bulkDTO.getName())
        .description(bulkDTO.getDescription())
        .type(bulkDTO.getType())
        .value(bulkDTO.getValue())
        .basedOn(bulkDTO.getBasedOn())
        .isIncludeInMediaPlan(bulkDTO.getIsIncludeInMediaPlan())
        .isActive(bulkDTO.getIsActive())
        .campaignId(bulkDTO.getCampaignId())
        .build();
  }

  /** Return trimmed campaignId or null if blank. */
  private static String normalizeCampaignId(String campaignId) {
    if (campaignId == null || campaignId.trim().isEmpty()) {
      return null;
    }
    return campaignId.trim();
  }

  // ========== Mapping Methods ==========

  private CustomFee mapToCustomFee(CustomFeeRequestDTO dto, String companyId, String campaignId) {
    CustomFee customFee = new CustomFee();
    customFee.setName(dto.getName());
    customFee.setDescription(dto.getDescription());
    customFee.setType(dto.getType());
    customFee.setValue(dto.getValue());
    customFee.setBasedOn(dto.getBasedOn());
    customFee.setIsIncludeInMediaPlan(
        dto.getIsIncludeInMediaPlan() != null ? dto.getIsIncludeInMediaPlan() : true);
    customFee.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
    customFee.setCompanyId(companyId);
    customFee.setCampaignId(campaignId);
    return customFee;
  }

  private CustomFee updateCustomFeeFromDTO(CustomFee existingCustomFee, CustomFeeRequestDTO dto) {
    existingCustomFee.setName(dto.getName());
    existingCustomFee.setDescription(dto.getDescription());
    existingCustomFee.setType(dto.getType());
    existingCustomFee.setValue(dto.getValue());
    existingCustomFee.setBasedOn(dto.getBasedOn());
    existingCustomFee.setIsIncludeInMediaPlan(
        dto.getIsIncludeInMediaPlan() != null ? dto.getIsIncludeInMediaPlan() : true);
    existingCustomFee.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
    return existingCustomFee;
  }

  private CustomFeeResponseDTO mapToCustomFeeResponseDTO(CustomFee customFee) {
    return CustomFeeResponseDTO.builder()
        .id(customFee.getId())
        .name(customFee.getName())
        .description(customFee.getDescription())
        .type(customFee.getType())
        .value(customFee.getValue())
        .basedOn(customFee.getBasedOn())
        .isIncludeInMediaPlan(customFee.getIsIncludeInMediaPlan())
        .isActive(customFee.getIsActive())
        .companyId(customFee.getCompanyId())
        .campaignId(customFee.getCampaignId())
        .createdAt(customFee.getCreatedAt())
        .updatedAt(customFee.getUpdatedAt())
        .build();
  }

  /**
   * Resets campaign approvals by changing clearing all approvedScheduleIds and approvedBy for all
   * CampaignInventorySchedules of the campaign.
   *
   * @param campaignId Campaign ID to reset approvals for
   */
  private void resetCampaignApprovals(String campaignId) {
    log.debug("Resetting campaign approvals for campaignId: {}", campaignId);

    // Get all CampaignInventorySchedules for the campaign
    List<CampaignInventorySchedules> campaignInventorySchedules =
        campaignInventorySchedulesRepository.findByCampaignId(campaignId);

    // Clear approvedScheduleIds and set approvedBy to null for all CampaignInventorySchedules
    for (CampaignInventorySchedules schedule : campaignInventorySchedules) {
      schedule.setApprovedScheduleIds(null);
      schedule.setApprovedBy(null);
    }

    // Save all updated CampaignInventorySchedules
    if (!campaignInventorySchedules.isEmpty()) {
      campaignInventorySchedulesRepository.saveAll(campaignInventorySchedules);
      log.debug(
          "Reset approvals for {} CampaignInventorySchedules for campaignId: {}",
          campaignInventorySchedules.size(),
          campaignId);
    }
  }
}
