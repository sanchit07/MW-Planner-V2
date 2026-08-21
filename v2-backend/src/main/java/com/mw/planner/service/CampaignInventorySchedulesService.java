package com.mw.planner.service;

import static com.mw.planner.constants.CampaignActivityKey.*;
import static com.mw.planner.service.InventoryService.*;
import static com.mw.planner.service.UserService.extractUserName;
import static com.mw.planner.service.UserService.extractUserRole;
import static java.time.temporal.ChronoUnit.DAYS;

import com.mw.planner.domain.*;
import com.mw.planner.dto.*;
import com.mw.planner.enums.CustomFeeType;
import com.mw.planner.enums.DiscountValueType;
import com.mw.planner.enums.PricingAction;
import com.mw.planner.exception.campaign.BulkOperationFailedException;
import com.mw.planner.exception.campaign.CampaignInventorySchedulesNotFoundException;
import com.mw.planner.exception.campaign.CampaignNotFoundException;
import com.mw.planner.exception.campaign.InvalidDateException;
import com.mw.planner.exception.campaign.ScheduleIdsNotBelongToCampaignException;
import com.mw.planner.exception.campaign.ScheduleIdsNotFoundException;
import com.mw.planner.repository.CampaignInventorySchedulesRepository;
import com.mw.planner.repository.ScheduleRepository;
import com.mw.planner.service.recommendation.RecommendationService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service for managing campaign inventory schedules configuration. Handles the creation and
 * management of week-hour booking matrices.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignInventorySchedulesService {

  private final CampaignInventorySchedulesRepository inventorySchedulesRepository;
  private final ScheduleRepository scheduleRepository;
  private final InventoryService inventoryService;
  private final CampaignService campaignService;
  private final CampaignActivityService campaignActivityService;
  private final UserService userService;
  private final CompanyService companyService;
  private final CustomFeeService customFeeService;
  private final MwMeasureService mwMeasureService;
  private final CampaignApprovalWorkflowService campaignApprovalWorkflowService;
  private final com.mw.planner.service.recommendation.RecommendationService recommendationService;
  private final VirtualThreadService virtualThreadService;
  private final ScheduleCacheEvictor scheduleCacheEvictor;

  private static final int DEFAULT_DURATION = 30;
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  /** Find campaign inventory schedules by campaign ID and inventory ID. */
  @Cacheable(value = "campaignInventorySchedules", key = "#campaignId + '_' + #inventoryId")
  public CampaignInventorySchedules findByCampaignIdAndInventoryId(
      String campaignId, String inventoryId) {
    log.debug(
        "Finding campaign inventory schedules config for campaignId: {}, inventoryId: {}",
        campaignId,
        inventoryId);

    return inventorySchedulesRepository
        .findByCampaignIdAndInventoryId(campaignId, inventoryId)
        .orElseThrow(
            () -> new CampaignInventorySchedulesNotFoundException(campaignId, inventoryId));
  }

  /** Find all campaign inventory schedules configurations by campaign ID */
  public List<CampaignInventorySchedules> findByCampaignId(String campaignId) {
    log.debug("Finding all campaign inventory schedules configs for campaignId: {}", campaignId);
    return inventorySchedulesRepository.findByCampaignId(campaignId);
  }

  /** Bulk find all campaign inventory schedules configurations by campaign IDs. */
  public List<CampaignInventorySchedules> findByCampaignIds(List<String> campaignIds) {
    if (campaignIds == null || campaignIds.isEmpty()) {
      return List.of();
    }
    return inventorySchedulesRepository.findByCampaignIdIn(campaignIds);
  }

  /** Find campaign inventory schedules configurations by campaign ID and list of inventory IDs */
  public List<CampaignInventorySchedules> findByCampaignIdAndInventoryIds(
      String campaignId, List<String> inventoryIds) {
    log.debug(
        "Finding campaign inventory schedules configs for campaignId: {} and {} inventory IDs",
        campaignId,
        inventoryIds.size());
    return inventorySchedulesRepository.findByCampaignIdAndInventoryIdIn(campaignId, inventoryIds);
  }

  /**
   * Select campaign inventory, create/update the schedule and update the campaign company access.
   * If a schedule already exists for this campaign-inventory combination, it will not create a new
   * one.
   */
  @CacheEvict(
      value = "campaignInventorySchedules",
      key = "#request.campaignId + '_' + #request.inventoryId")
  public void selectInventory(SelectCampaignInventoryRequestDTO request) {
    log.info(
        "Selecting campaign inventory for campaignId: {}, inventoryId: {}",
        request.getCampaignId(),
        request.getInventoryId());

    // Owner-only write guard (also covers the early "already exists" return below)
    Campaign guardedCampaign =
        campaignService.findByIdForCurrentModeForWrite(request.getCampaignId());

    // Check if schedule already exists - if so, skip creation
    if (inventorySchedulesRepository.existsByCampaignIdAndInventoryId(
        request.getCampaignId(), request.getInventoryId())) {
      log.info(
          "Schedule already exists for campaignId: {}, inventoryId: {}. Skipping creation.",
          request.getCampaignId(),
          request.getInventoryId());
      return;
    }

    // Campaign already loaded and write-guarded above
    var campaign = guardedCampaign;

    // Find the inventory
    Inventory inventory = inventoryService.getById(request.getInventoryId());

    // Extract mediaOwnerId from inventory and update campaign's companyAccess
    String mediaOwnerId = inventory.getMediaOwnerId();
    if (mediaOwnerId != null && !mediaOwnerId.isBlank()) {
      updateCampaignCompanyAccess(campaign, mediaOwnerId);
    }

    // Create and Update CampaignInventorySchedules
    CampaignInventorySchedules schedule =
        createCampaignInventorySchedules(
            campaign, inventory, request.getImpressions(), request.getReach());
    inventorySchedulesRepository.save(schedule);

    log.info("Successfully saved campaign inventory schedule with ID: {}", schedule.getId());

    recommendationService.syncSelectedInventories(
        request.getCampaignId(),
        List.of(request.getInventoryId()),
        RecommendationService.OperationType.SELECT);

    try {
      // Log activity for inventory selection
      String referenceId =
          inventory.getReferenceId() != null ? inventory.getReferenceId() : inventory.getId();
      campaignActivityService.logActivity(
          request.getCampaignId(),
          CampaignActivityService.OperationType.ADDED,
          INVENTORY_REFERENCE_ID.key(),
          referenceId);
    } catch (Exception e) {
      log.warn("Failed to log for select inventory activity: {}", e.getMessage());
    }
  }

  /**
   * Deselect campaign inventory by removing the configuration and schedule Check mediaOwnerId and
   * remove the companyAccess from Campaign
   */
  @CacheEvict(
      value = "campaignInventorySchedules",
      key = "#request.campaignId + '_' + #request.inventoryId")
  public void deselectInventory(SelectCampaignInventoryRequestDTO request) {
    log.info(
        "Deselecting campaign inventory for campaignId: {}, inventoryId: {}",
        request.getCampaignId(),
        request.getInventoryId());

    // Owner-only write guard: validates existence, data mode, and acting-company ownership
    campaignService.findByIdForCurrentModeForWrite(request.getCampaignId());

    // Fetch CampaignInventorySchedules by campaignId and inventoryId
    CampaignInventorySchedules campaignInventorySchedules =
        inventorySchedulesRepository
            .findByCampaignIdAndInventoryId(request.getCampaignId(), request.getInventoryId())
            .orElseThrow(
                () ->
                    new CampaignInventorySchedulesNotFoundException(
                        request.getCampaignId(), request.getInventoryId()));

    // Get all scheduleIds from CampaignInventorySchedules
    List<String> scheduleIds = campaignInventorySchedules.getScheduleIds();
    if (scheduleIds != null && !scheduleIds.isEmpty()) {
      // Delete all schedules first
      scheduleRepository.deleteByIdIn(scheduleIds);
      log.debug(
          "Deleted {} Schedule entities for campaignId: {}, inventoryId: {}",
          scheduleIds.size(),
          request.getCampaignId(),
          request.getInventoryId());
    }

    // Extract mediaOwnerId by inventory id
    String mediaOwnerId = inventoryService.getMediaOwnerIdById(request.getInventoryId());

    // Delete the CampaignInventorySchedules
    inventorySchedulesRepository.deleteByCampaignIdAndInventoryId(
        request.getCampaignId(), request.getInventoryId());

    recommendationService.syncSelectedInventories(
        request.getCampaignId(),
        List.of(request.getInventoryId()),
        com.mw.planner.service.recommendation.RecommendationService.OperationType.DESELECT);

    // Check if configuration exists using cacheable method
    try {
      // If config was deleted and mediaOwnerId exists, check if we need to remove it from
      // companyAccess
      if (mediaOwnerId != null && !mediaOwnerId.isBlank()) {
        // Use count method to check if there are any other configs for this campaign with the same
        // mediaOwnerId
        long remainingScheduleCount =
            inventorySchedulesRepository.countByCampaignIdAndMediaOwnerId(
                request.getCampaignId(), mediaOwnerId);

        log.debug(
            "After deleting config for campaignId: {}, inventoryId: {}, mediaOwnerId: {} - Remaining configs count: {}",
            request.getCampaignId(),
            request.getInventoryId(),
            mediaOwnerId,
            remainingScheduleCount);

        // If no other configs exist with this mediaOwnerId, remove it from companyAccess
        if (remainingScheduleCount == 0) {
          log.info(
              "No remaining configs found for mediaOwnerId: {} in campaignId: {}, removing from companyAccess",
              mediaOwnerId,
              request.getCampaignId());
          removeMediaOwnerIdFromCompanyAccess(request.getCampaignId(), mediaOwnerId);
        } else {
          log.debug(
              "Found {} remaining config(s) for mediaOwnerId: {} in campaignId: {}, keeping in companyAccess",
              remainingScheduleCount,
              mediaOwnerId,
              request.getCampaignId());
        }
      } else {
        log.debug(
            "mediaOwnerId is null or blank for inventoryId: {}, skipping companyAccess check",
            request.getInventoryId());
      }

      log.info(
          "Successfully deselected campaign inventory for campaignId: {}, inventoryId: {}",
          request.getCampaignId(),
          request.getInventoryId());

      // Log activity for inventory deselection
      Inventory inventory = inventoryService.getById(request.getInventoryId());
      String referenceId =
          inventory.getReferenceId() != null ? inventory.getReferenceId() : inventory.getId();
      campaignActivityService.logActivity(
          request.getCampaignId(),
          CampaignActivityService.OperationType.REMOVED,
          INVENTORY_REFERENCE_ID.key(),
          referenceId);
    } catch (CampaignInventorySchedulesNotFoundException e) {
      log.warn(
          "No configuration found to deselect for campaignId: {}, inventoryId: {}",
          request.getCampaignId(),
          request.getInventoryId());
    }
  }

  /**
   * Optimized variant of {@link #deselectInventory(SelectCampaignInventoryRequestDTO)}. Keeps the
   * correctness-bearing work (schedule delete, config delete, companyAccess reconciliation) on the
   * request thread, but:
   *
   * <ul>
   *   <li>reuses {@code mediaOwnerId} already present on the fetched configuration instead of
   *       issuing a redundant lookup, and
   *   <li>runs the best-effort side-effects (recommendation-engine sync + activity logging) on a
   *       virtual thread so a large deselect returns as soon as the essential deletes commit.
   * </ul>
   *
   * <p>The async side-effects are already non-transactional and fully best-effort, so backgrounding
   * them changes latency only, not observable behavior.
   */
  @CacheEvict(
      value = "campaignInventorySchedules",
      key = "#request.campaignId + '_' + #request.inventoryId")
  public void deselectInventoryV2(SelectCampaignInventoryRequestDTO request) {
    final String campaignId = request.getCampaignId();
    final String inventoryId = request.getInventoryId();

    log.info(
        "Deselecting campaign inventory (V2) for campaignId: {}, inventoryId: {}",
        campaignId,
        inventoryId);

    // Owner-only write guard: validates existence, data mode, and acting-company ownership
    campaignService.findByIdForCurrentModeForWrite(campaignId);

    // Fetch CampaignInventorySchedules by campaignId and inventoryId
    CampaignInventorySchedules campaignInventorySchedules =
        inventorySchedulesRepository
            .findByCampaignIdAndInventoryId(campaignId, inventoryId)
            .orElseThrow(
                () -> new CampaignInventorySchedulesNotFoundException(campaignId, inventoryId));

    // Delete all schedules first (essential, synchronous)
    List<String> scheduleIds = campaignInventorySchedules.getScheduleIds();
    if (scheduleIds != null && !scheduleIds.isEmpty()) {
      scheduleRepository.deleteByIdIn(scheduleIds);
      log.debug(
          "Deleted {} Schedule entities for campaignId: {}, inventoryId: {}",
          scheduleIds.size(),
          campaignId,
          inventoryId);
    }

    // Reuse mediaOwnerId already carried by the fetched configuration (no extra query)
    final String mediaOwnerId = campaignInventorySchedules.getMediaOwnerId();

    // Delete the CampaignInventorySchedules (essential, synchronous)
    inventorySchedulesRepository.deleteByCampaignIdAndInventoryId(campaignId, inventoryId);

    // Reconcile companyAccess (essential, synchronous): if no other config for this campaign uses
    // the same mediaOwnerId, remove it from the campaign's companyAccess.
    if (mediaOwnerId != null && !mediaOwnerId.isBlank()) {
      long remainingScheduleCount =
          inventorySchedulesRepository.countByCampaignIdAndMediaOwnerId(campaignId, mediaOwnerId);

      log.debug(
          "After deleting config for campaignId: {}, inventoryId: {}, mediaOwnerId: {} - Remaining configs count: {}",
          campaignId,
          inventoryId,
          mediaOwnerId,
          remainingScheduleCount);

      if (remainingScheduleCount == 0) {
        log.info(
            "No remaining configs found for mediaOwnerId: {} in campaignId: {}, removing from companyAccess",
            mediaOwnerId,
            campaignId);
        removeMediaOwnerIdFromCompanyAccess(campaignId, mediaOwnerId);
      } else {
        log.debug(
            "Found {} remaining config(s) for mediaOwnerId: {} in campaignId: {}, keeping in companyAccess",
            remainingScheduleCount,
            mediaOwnerId,
            campaignId);
      }
    } else {
      log.debug(
          "mediaOwnerId is null or blank for inventoryId: {}, skipping companyAccess check",
          inventoryId);
    }

    log.info(
        "Successfully deselected campaign inventory (V2) for campaignId: {}, inventoryId: {}",
        campaignId,
        inventoryId);

    // Fire best-effort side-effects off the request path. Each is independently guarded so a
    // failure never escapes into the CompletableFuture nor blocks the other side-effect.
    virtualThreadService.runAsync(
        () -> {
          try {
            recommendationService.syncSelectedInventories(
                campaignId,
                List.of(inventoryId),
                com.mw.planner.service.recommendation.RecommendationService.OperationType.DESELECT);
          } catch (Exception e) {
            log.warn(
                "Async recommendation sync failed for deselect campaignId: {}, inventoryId: {}: {}",
                campaignId,
                inventoryId,
                e.getMessage());
          }

          try {
            Inventory inventory = inventoryService.getById(inventoryId);
            String referenceId =
                inventory.getReferenceId() != null ? inventory.getReferenceId() : inventory.getId();
            campaignActivityService.logActivity(
                campaignId,
                CampaignActivityService.OperationType.REMOVED,
                INVENTORY_REFERENCE_ID.key(),
                referenceId);
          } catch (Exception e) {
            log.warn(
                "Async activity log failed for deselect campaignId: {}, inventoryId: {}: {}",
                campaignId,
                inventoryId,
                e.getMessage());
          }
        });
  }

  /**
   * Update campaign's companyAccess list with mediaOwnerId and evict campaign cache.
   *
   * @param campaign The campaign entity
   * @param mediaOwnerId The media owner ID to add to companyAccess
   */
  private void updateCampaignCompanyAccess(Campaign campaign, String mediaOwnerId) {
    log.debug(
        "Updating companyAccess for campaignId: {} with mediaOwnerId: {}",
        campaign.getId(),
        mediaOwnerId);

    // Initialize companyAccess list if null
    if (campaign.getCompanyAccess() == null) {
      campaign.setCompanyAccess(new ArrayList<>());
    }

    // Add mediaOwnerId if not already present
    if (!campaign.getCompanyAccess().contains(mediaOwnerId)) {
      campaign.getCompanyAccess().add(mediaOwnerId);
      campaignService.save(campaign);

      // evict campaign cache
      campaignService.campaignCacheEvict(campaign.getId());

      log.info(
          "Added mediaOwnerId: {} to companyAccess for campaignId: {}",
          mediaOwnerId,
          campaign.getId());
    } else {
      log.debug(
          "mediaOwnerId: {} already exists in companyAccess for campaignId: {}",
          mediaOwnerId,
          campaign.getId());
    }
  }

  /**
   * Remove mediaOwnerId from campaign's companyAccess list and evict campaign cache.
   *
   * @param campaignId The campaign ID
   * @param mediaOwnerId The media owner ID to remove from companyAccess
   */
  private void removeMediaOwnerIdFromCompanyAccess(String campaignId, String mediaOwnerId) {
    log.debug(
        "Removing mediaOwnerId: {} from companyAccess for campaignId: {}",
        mediaOwnerId,
        campaignId);

    // Find the campaign
    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);

    // Check if companyAccess exists and contains the mediaOwnerId
    if (campaign.getCompanyAccess() != null && campaign.getCompanyAccess().contains(mediaOwnerId)) {
      campaign.getCompanyAccess().remove(mediaOwnerId);
      campaignService.save(campaign);

      // evict campaign cache
      campaignService.campaignCacheEvict(campaignId);

      log.info(
          "Removed mediaOwnerId: {} from companyAccess for campaignId: {}",
          mediaOwnerId,
          campaignId);
    } else {
      log.debug(
          "mediaOwnerId: {} not found in companyAccess for campaignId: {}",
          mediaOwnerId,
          campaignId);
    }
  }

  /**
   * Remove multiple mediaOwnerIds from campaign's companyAccess list in a single read/write cycle
   * and evict campaign cache once, instead of one fetch/save/evict per mediaOwnerId. Used by bulk
   * deselect, where several mediaOwnerIds can become fully unreferenced in the same operation.
   *
   * @param campaignId The campaign ID
   * @param mediaOwnerIdsToRemove The media owner IDs to remove from companyAccess
   */
  private void removeMediaOwnerIdsFromCompanyAccess(
      String campaignId, List<String> mediaOwnerIdsToRemove) {
    if (mediaOwnerIdsToRemove.isEmpty()) {
      return;
    }

    log.debug(
        "Removing mediaOwnerIds: {} from companyAccess for campaignId: {}",
        mediaOwnerIdsToRemove,
        campaignId);

    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);

    if (campaign.getCompanyAccess() != null
        && campaign.getCompanyAccess().removeAll(mediaOwnerIdsToRemove)) {
      campaignService.save(campaign);
      campaignService.campaignCacheEvict(campaignId);

      log.info(
          "Removed mediaOwnerIds: {} from companyAccess for campaignId: {}",
          mediaOwnerIdsToRemove,
          campaignId);
    } else {
      log.debug(
          "None of mediaOwnerIds: {} were found in companyAccess for campaignId: {}",
          mediaOwnerIdsToRemove,
          campaignId);
    }
  }

  /**
   * Bulk select or deselect inventories based on filter criteria. SOV is automatically calculated
   * based on inventory type: CLASSIC = 100%, Others = 10%. This is an all-or-nothing operation - if
   * any inventory fails, the entire operation rolls back.
   *
   * @param campaignId Campaign ID for the bulk operation
   * @param filter Filter criteria for inventory selection
   * @param operationType Operation type (SELECT or DESELECT)
   * @return Total count of inventories processed successfully
   * @throws BulkOperationFailedException if any inventory fails during processing
   */
  public int bulkSelectDeselectInventories(
      String campaignId,
      CampaignInventoryFilterDTO filter,
      SelectCampaignInventoryRequestDTO.OperationType operationType) {

    log.info(
        "Bulk {} operation for campaignId: {} with filter criteria", operationType, campaignId);

    // Validate campaign exists
    Campaign campaign = campaignService.findByIdForCurrentModeForWrite(campaignId);

    // Load all inventories matching the filter at once
    // Use optimized fetching: for both SELECT and DESELECT, we only need specific fields
    // SELECT needs: id, type, mediaOwnerId, specifications (availableDays,
    // spotsDetails.spotsPerHour,
    // displayOnTime, displayOffTime)
    // DESELECT needs: id only
    // The repository method handles field projection to fetch only required fields
    List<Inventory> inventories =
        inventoryService.getInventoriesWithFiltersForBulkOperation(filter);

    if (inventories.isEmpty()) {
      log.info("No inventories found matching the filter criteria for campaignId: {}", campaignId);
      return 0;
    }

    try {
      if (operationType == SelectCampaignInventoryRequestDTO.OperationType.SELECT) {
        return bulkSelectInventories(campaign, inventories);
      } else {
        return bulkDeselectInventories(campaignId, inventories);
      }
    } catch (Exception e) {
      log.error("Bulk {} operation failed for campaignId: {}", operationType, campaignId, e);

      // Get the root cause
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      String errorMessage = cause.getMessage() != null ? cause.getMessage() : "Unknown error";

      throw new BulkOperationFailedException(operationType.toString(), campaignId, errorMessage, e);
    }
  }

  /**
   * Bulk select inventories by inventory IDs. Reuses the existing bulk select logic.
   *
   * @param campaignId Campaign ID
   * @param inventoryIds List of inventory IDs to select
   * @return Total count of inventories processed successfully
   * @throws BulkOperationFailedException if any inventory fails during processing
   */
  public int bulkSelectInventoriesByIds(String campaignId, List<String> inventoryIds) {
    if (inventoryIds == null || inventoryIds.isEmpty()) {
      log.info(
          "No inventory IDs provided for bulk select operation for campaignId: {}", campaignId);
      return 0;
    }

    log.info(
        "Bulk selecting {} inventories by IDs for campaignId: {}", inventoryIds.size(), campaignId);

    // Validate campaign exists
    Campaign campaign = campaignService.findByIdForCurrentModeForWrite(campaignId);

    // Fetch all inventories by IDs in a single query
    List<Inventory> inventories = inventoryService.findAllByIds(inventoryIds);

    if (inventories.isEmpty()) {
      log.info("No inventories found for the provided IDs for campaignId: {}", campaignId);
      return 0;
    }

    return bulkSelectInventories(campaign, inventories);
  }

  /**
   * Bulk deselect inventories by inventory IDs. Mirrors {@link #bulkSelectInventoriesByIds} and
   * reuses the existing optimized batch deselect logic (single fetch, batch schedule/config
   * deletes, deduped companyAccess reconciliation, single recommendation sync).
   *
   * @param campaignId Campaign ID
   * @param inventoryIds List of inventory IDs to deselect
   * @return Total count of inventories processed successfully
   * @throws BulkOperationFailedException if any inventory fails during processing
   */
  public int bulkDeselectInventoriesByIds(String campaignId, List<String> inventoryIds) {
    if (inventoryIds == null || inventoryIds.isEmpty()) {
      log.info(
          "No inventory IDs provided for bulk deselect operation for campaignId: {}", campaignId);
      return 0;
    }

    log.info(
        "Bulk deselecting {} inventories by IDs for campaignId: {}",
        inventoryIds.size(),
        campaignId);

    // Validate campaign exists
    campaignService.findByIdForCurrentModeForWrite(campaignId);

    // Fetch all inventories by IDs in a single query
    List<Inventory> inventories = inventoryService.findAllByIds(inventoryIds);

    if (inventories.isEmpty()) {
      log.info("No inventories found for the provided IDs for campaignId: {}", campaignId);
      return 0;
    }

    return bulkDeselectInventories(campaignId, inventories);
  }

  /** Result of a bulk-select-by-reference-IDs operation. */
  public record BulkSelectByReferenceIdsResult(int count, List<String> notFoundReferenceIds) {}

  /**
   * Bulk select inventories by reference IDs. Mirrors {@link #bulkSelectInventoriesByIds} but
   * resolves inventories via {@code referenceId} instead of the Mongo {@code _id}. Reuses the same
   * optimized batch select logic (single fetch, batch schedule/config saves, deduped companyAccess
   * reconciliation, single recommendation sync).
   *
   * @param campaignId Campaign ID
   * @param referenceIds List of reference IDs to select
   * @return Result containing count of inventories processed and any reference IDs not found
   * @throws BulkOperationFailedException if any inventory fails during processing
   */
  public BulkSelectByReferenceIdsResult bulkSelectInventoriesByReferenceIds(
      String campaignId, List<String> referenceIds) {
    if (referenceIds == null || referenceIds.isEmpty()) {
      log.info(
          "No reference IDs provided for bulk select operation for campaignId: {}", campaignId);
      return new BulkSelectByReferenceIdsResult(0, List.of());
    }

    log.info(
        "Bulk selecting {} inventories by reference IDs for campaignId: {}",
        referenceIds.size(),
        campaignId);

    // Validate campaign exists
    Campaign campaign = campaignService.findByIdForCurrentModeForWrite(campaignId);

    // Fetch all inventories by reference IDs in a single query
    List<Inventory> inventories = inventoryService.findAllByReferenceIds(referenceIds);

    // Detect which reference IDs had no matching inventory
    Set<String> foundReferenceIds =
        inventories.stream()
            .map(Inventory::getReferenceId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    List<String> notFoundReferenceIds =
        referenceIds.stream().filter(id -> !foundReferenceIds.contains(id)).toList();

    if (!notFoundReferenceIds.isEmpty()) {
      log.warn(
          "Bulk select: {} reference ID(s) not found for campaignId: {}: {}",
          notFoundReferenceIds.size(),
          campaignId,
          notFoundReferenceIds);
    }

    if (inventories.isEmpty()) {
      log.info(
          "No inventories found for the provided reference IDs for campaignId: {}", campaignId);
      return new BulkSelectByReferenceIdsResult(0, notFoundReferenceIds);
    }

    int count = bulkSelectInventories(campaign, inventories);
    return new BulkSelectByReferenceIdsResult(count, notFoundReferenceIds);
  }

  /**
   * Bulk deselect inventories by reference IDs. Mirrors {@link #bulkDeselectInventoriesByIds} but
   * resolves inventories via {@code referenceId} instead of the Mongo {@code _id}. Reuses the same
   * optimized batch deselect logic (single fetch, batch schedule/config deletes, deduped
   * companyAccess reconciliation, single recommendation sync).
   *
   * @param campaignId Campaign ID
   * @param referenceIds List of reference IDs to deselect
   * @return Total count of inventories processed successfully
   * @throws BulkOperationFailedException if any inventory fails during processing
   */
  public int bulkDeselectInventoriesByReferenceIds(String campaignId, List<String> referenceIds) {
    if (referenceIds == null || referenceIds.isEmpty()) {
      log.info(
          "No reference IDs provided for bulk deselect operation for campaignId: {}", campaignId);
      return 0;
    }

    log.info(
        "Bulk deselecting {} inventories by reference IDs for campaignId: {}",
        referenceIds.size(),
        campaignId);

    // Validate campaign exists
    campaignService.findByIdForCurrentModeForWrite(campaignId);

    // Fetch all inventories by reference IDs in a single query
    List<Inventory> inventories = inventoryService.findAllByReferenceIds(referenceIds);

    if (inventories.isEmpty()) {
      log.info(
          "No inventories found for the provided reference IDs for campaignId: {}", campaignId);
      return 0;
    }

    return bulkDeselectInventories(campaignId, inventories);
  }

  /**
   * Bulk select inventories - use a single database call for saving all configurations. Uses
   * unified Map-based approach with Stream API for efficient batch processing.
   *
   * @param campaign The campaign entity
   * @param inventories List of inventories to select
   * @return Total count of inventories processed successfully
   */
  private int bulkSelectInventories(Campaign campaign, List<Inventory> inventories) {
    String campaignId = campaign.getId();
    log.info("Bulk selecting {} inventories for campaignId: {}", inventories.size(), campaignId);

    if (inventories.isEmpty()) {
      return 0;
    }

    // Build Map of referenceId -> Inventory for efficient lookup
    Map<String, Inventory> inventoryMap =
        inventories.stream()
            .filter(inv -> inv.getReferenceId() != null && !inv.getReferenceId().isBlank())
            .collect(
                Collectors.toMap(
                    Inventory::getReferenceId,
                    Function.identity(),
                    (existing, replacement) -> existing));

    // Build all schedules in memory using Stream API
    // Use a record to track inventory -> schedule relationship
    record InventorySchedulePair(
        Inventory inventory, Schedule schedule, CampaignInventorySchedules campaignSchedule) {}

    List<InventorySchedulePair> pairs = new ArrayList<>();
    Set<String> uniqueMediaOwnerIds = new HashSet<>();

    for (Inventory inventory : inventories) {
      try {
        // Extract and collect unique mediaOwnerIds
        String mediaOwnerId = inventory.getMediaOwnerId();
        if (mediaOwnerId != null && !mediaOwnerId.isBlank()) {
          uniqueMediaOwnerIds.add(mediaOwnerId);
        }

        // Create schedule without API call (will be enriched later in batch)
        Schedule schedule = createScheduleForInventory(campaign, inventory);

        // Create CampaignInventorySchedules structure (schedule will be saved and ID added later)
        CampaignInventorySchedules campaignSchedule = new CampaignInventorySchedules();
        campaignSchedule.setId(
            campaign.getId() + "_" + inventory.getMediaOwnerId() + "_" + inventory.getId());
        campaignSchedule.setCampaignId(campaign.getId());
        campaignSchedule.setInventoryId(inventory.getId());
        campaignSchedule.setMediaOwnerId(inventory.getMediaOwnerId());

        pairs.add(new InventorySchedulePair(inventory, schedule, campaignSchedule));

        log.debug(
            "Prepared schedule for inventory {} (type: {})",
            inventory.getId(),
            inventory.getType());
      } catch (Exception e) {
        log.error(
            "Failed to prepare schedule for inventory {}: {}",
            inventory.getId(),
            e.getMessage(),
            e);
        throw new BulkOperationFailedException(
            "SELECT",
            campaignId,
            "Failed to prepare schedule for inventory: " + inventory.getId(),
            e);
      }
    }

    // Build schedule map for enrichment (key = referenceId)
    Map<String, Schedule> scheduleMap =
        pairs.stream()
            .collect(
                Collectors.toMap(
                    pair ->
                        pair.inventory().getReferenceId() != null
                            ? pair.inventory().getReferenceId()
                            : pair.inventory().getId(),
                    InventorySchedulePair::schedule,
                    (existing, replacement) -> existing));

    // Enrich all schedules with reach and frequency data in a single API call
    if (!scheduleMap.isEmpty() && !inventoryMap.isEmpty()) {
      enrichSchedulesWithReachAndFrequency(scheduleMap, inventoryMap, campaign);
    }

    // Update basePrice for all schedules with enriched impressions
    pairs.forEach(
        pair -> {
          String referenceId =
              pair.inventory().getReferenceId() != null
                  ? pair.inventory().getReferenceId()
                  : pair.inventory().getId();
          Schedule schedule = scheduleMap.get(referenceId);
          if (schedule != null) {
            int campaignDurationDays =
                (int) DAYS.between(campaign.getStartDate(), campaign.getEndDate());
            schedule.setBasePrice(
                calculateScheduleBasePriceForSchedule(
                    schedule.getAdPlays(),
                    schedule.getImpressions(),
                    pair.inventory(),
                    campaign.getGoals() != null ? campaign.getGoals().getGoalType() : null,
                    campaignDurationDays));
          }
        });

    // Save all schedules in a single database call
    List<Schedule> schedulesToSave =
        pairs.stream()
            .map(
                pair -> {
                  String referenceId =
                      pair.inventory().getReferenceId() != null
                          ? pair.inventory().getReferenceId()
                          : pair.inventory().getId();
                  return scheduleMap.get(referenceId);
                })
            .filter(Objects::nonNull)
            .toList();

    List<Schedule> savedSchedules = scheduleRepository.saveAll(schedulesToSave);

    // Create map of referenceId -> saved schedule for efficient lookup
    Map<String, Schedule> savedScheduleMap = new HashMap<>();
    for (int i = 0; i < schedulesToSave.size() && i < savedSchedules.size(); i++) {
      Schedule originalSchedule = schedulesToSave.get(i);
      Schedule savedSchedule = savedSchedules.get(i);
      pairs.stream()
          .filter(pair -> pair.schedule().equals(originalSchedule))
          .map(
              pair ->
                  pair.inventory().getReferenceId() != null
                      ? pair.inventory().getReferenceId()
                      : pair.inventory().getId())
          .findFirst()
          .ifPresent(referenceId -> savedScheduleMap.put(referenceId, savedSchedule));
    }

    // Update CampaignInventorySchedules with schedule IDs and history
    for (InventorySchedulePair pair : pairs) {
      String referenceId =
          pair.inventory().getReferenceId() != null
              ? pair.inventory().getReferenceId()
              : pair.inventory().getId();
      Schedule savedSchedule = savedScheduleMap.get(referenceId);
      if (savedSchedule != null) {
        pair.campaignSchedule().setScheduleIds(List.of(savedSchedule.getId()));

        // Add history details with RATE_CARD action
        CampaignInventorySchedules.History history =
            createHistoryEntry(
                List.of(savedSchedule),
                PricingAction.RATE_CARD,
                pair.campaignSchedule(),
                pair.inventory(),
                campaign);
        pair.campaignSchedule().setHistory(List.of(history));
      }
    }

    // Update campaign's companyAccess with all unique mediaOwnerIds
    uniqueMediaOwnerIds.forEach(
        mediaOwnerId -> updateCampaignCompanyAccess(campaign, mediaOwnerId));

    // Save all CampaignInventorySchedules in a single database call
    List<CampaignInventorySchedules> campaignSchedulesToSave =
        pairs.stream().map(InventorySchedulePair::campaignSchedule).toList();

    if (!campaignSchedulesToSave.isEmpty()) {
      inventorySchedulesRepository.saveAll(campaignSchedulesToSave);
      log.info(
          "Successfully saved {} schedules in bulk for campaignId: {}",
          campaignSchedulesToSave.size(),
          campaignId);

      // Evict cache for all affected inventories
      campaignSchedulesToSave.forEach(
          schedule -> scheduleCacheEvictor.evict(campaignId, schedule.getInventoryId()));

      try {
        // Log activity for bulk inventory selection
        campaignActivityService.logActivity(
            campaignId,
            CampaignActivityService.OperationType.ADDED,
            SELECTED_INVENTORY_COUNT.key(),
            campaignSchedulesToSave.size());
      } catch (Exception e) {
        log.warn("Failed to log for bulk select inventories activity: {}", e.getMessage());
      }
    }

    log.info(
        "Bulk SELECT operation completed successfully for campaignId: {} - Total processed: {}",
        campaignId,
        campaignSchedulesToSave.size());

    // Sync selected inventories with recommendation engine
    List<String> selectedIds =
        campaignSchedulesToSave.stream().map(CampaignInventorySchedules::getInventoryId).toList();
    recommendationService.syncSelectedInventories(
        campaignId, selectedIds, RecommendationService.OperationType.SELECT);

    return campaignSchedulesToSave.size();
  }

  /**
   * Create a Schedule for an inventory without calling the reach and frequency API. This method is
   * used for batch processing where schedules are enriched later.
   *
   * @param campaign The campaign entity
   * @param inventory The inventory entity
   * @return Created Schedule without impressions and reach (will be enriched later)
   */
  private Schedule createScheduleForInventory(Campaign campaign, Inventory inventory) {
    // Calculate simple date-based booking matrix (all hours 0-23 for each date)
    Map<String, List<Integer>> bookingMatrix =
        calculateSimpleBookingMatrix(campaign.getStartDate(), campaign.getEndDate(), inventory);

    // Default booked spotPerHour
    long spotPerHour = getLoopsPerHour(inventory);

    // Calculate adPlays: spotsPerHour * total hours in bookingMatrix
    long adPlays = calculateAdPlays(spotPerHour, bookingMatrix);

    // Convert Inventory.Weekday to Schedule.Weekday
    List<Schedule.Weekday> scheduleDays = getWeekdays(inventory);

    // Calculate plannedSot: (adPlays * spotDuration) / 3600
    double plannedSot = calculatePlannedSotFromAdPlays(adPlays, getSpotDuration(inventory));
    // Calculate totalSot: total operating hours for campaign dates and schedule days
    double totalSot =
        calculateTotalSotFromCampaignDates(
            campaign.getStartDate(), campaign.getEndDate(), scheduleDays, inventory);

    // Create schedule without impressions and reach (will be enriched later)
    return Schedule.builder()
        .startDate(campaign.getStartDate())
        .name("Schedule 1")
        .endDate(campaign.getEndDate())
        .scheduleDays(scheduleDays)
        .bookingMatrix(bookingMatrix)
        .duration(getSpotDuration(inventory))
        .type(resolveScheduleType(inventory))
        .spotsPerLoop(1L) // default 1 spot per loop
        .spotsPerHour(spotPerHour) // spotsPerHour will always in multiplication on loops per hr
        .adPlays(adPlays)
        .plannedSot(plannedSot)
        .totalSot(totalSot)
        .order(1)
        .build();
  }

  /**
   * Create CampaignInventorySchedules from campaign and inventory. This method creates the schedule
   * with all required fields including bookingMatrix, scheduleDays, duration, spotsPerLoop,
   * spotsPerHour, adPlays, plannedSot, and totalSot. Uses unified approach with Map and Stream API
   * for reach and frequency enrichment.
   *
   * @param campaign The campaign entity
   * @param inventory The inventory entity
   * @return Created CampaignInventorySchedules
   */
  private CampaignInventorySchedules createCampaignInventorySchedules(
      Campaign campaign, Inventory inventory, Long preSuppliedImpressions, Long preSuppliedReach) {

    CampaignInventorySchedules campaignInventorySchedules = new CampaignInventorySchedules();
    campaignInventorySchedules.setId(
        campaign.getId() + "_" + inventory.getMediaOwnerId() + "_" + inventory.getId());
    campaignInventorySchedules.setCampaignId(campaign.getId());
    campaignInventorySchedules.setInventoryId(inventory.getId());
    campaignInventorySchedules.setMediaOwnerId(inventory.getMediaOwnerId());

    // Calculate simple date-based booking matrix (all hours 0-23 for each date)
    Map<String, List<Integer>> bookingMatrix =
        calculateSimpleBookingMatrix(campaign.getStartDate(), campaign.getEndDate(), inventory);

    // Default booked spotPerHour
    long spotPerHour = getLoopsPerHour(inventory);

    // Calculate adPlays: spotsPerHour * total hours in bookingMatrix
    long adPlays = calculateAdPlays(spotPerHour, bookingMatrix);

    // Convert Inventory.Weekday to Schedule.Weekday
    List<Schedule.Weekday> scheduleDays = getWeekdays(inventory);

    // Calculate plannedSot: (adPlays * spotDuration) / 3600
    double plannedSot = calculatePlannedSotFromAdPlays(adPlays, getSpotDuration(inventory));
    // Calculate totalSot: total operating hours for campaign dates and schedule days
    double totalSot =
        calculateTotalSotFromCampaignDates(
            campaign.getStartDate(), campaign.getEndDate(), scheduleDays, inventory);

    // Create schedule with all required fields (impressions and reach will be enriched later)
    Schedule scheduleItem =
        Schedule.builder()
            .startDate(campaign.getStartDate())
            .name("Schedule 1")
            .endDate(campaign.getEndDate())
            .scheduleDays(scheduleDays)
            .bookingMatrix(bookingMatrix)
            .duration(getSpotDuration(inventory))
            .type(resolveScheduleType(inventory))
            .spotsPerLoop(1L) // default 1 spot per loop
            .spotsPerHour(spotPerHour) // spotsPerHour will always in multiplication on loops per hr
            .adPlays(adPlays)
            .plannedSot(plannedSot)
            .totalSot(totalSot)
            .order(1)
            .build();

    // Use unified approach to enrich schedule with reach and frequency data
    String referenceId =
        inventory.getReferenceId() != null ? inventory.getReferenceId() : inventory.getId();

    Schedule enrichedSchedule;
    if (preSuppliedImpressions != null && preSuppliedReach != null) {
      // Use caller-supplied impressions/reach directly — skip the Measure API call
      scheduleItem.setImpressions(preSuppliedImpressions);
      scheduleItem.setReach(preSuppliedReach);
      enrichedSchedule = scheduleItem;
    } else {
      Map<String, Schedule> scheduleMap = Map.of(referenceId, scheduleItem);
      Map<String, Inventory> inventoryMap = Map.of(referenceId, inventory);

      Map<String, Schedule> enrichedScheduleMap =
          enrichSchedulesWithReachAndFrequency(
              new HashMap<>(scheduleMap), new HashMap<>(inventoryMap), campaign);

      enrichedSchedule = enrichedScheduleMap.get(referenceId);
    }

    // Calculate basePrice with enriched impressions, driven by campaign goal type
    int campaignDurationDays = (int) DAYS.between(campaign.getStartDate(), campaign.getEndDate());
    enrichedSchedule.setBasePrice(
        calculateScheduleBasePriceForSchedule(
            adPlays,
            enrichedSchedule.getImpressions(),
            inventory,
            campaign.getGoals() != null ? campaign.getGoals().getGoalType() : null,
            campaignDurationDays));

    // Save schedule and get its ID
    Schedule savedSchedule = scheduleRepository.save(enrichedSchedule);
    campaignInventorySchedules.setScheduleIds(List.of(savedSchedule.getId()));

    // Add history details with RATE_CARD action (effectiveDiscountPercentage = 0.0)
    CampaignInventorySchedules.History campaignInventorySchedulesHistory =
        createHistoryEntry(
            List.of(savedSchedule),
            PricingAction.RATE_CARD,
            campaignInventorySchedules,
            inventory,
            campaign);
    campaignInventorySchedules.setHistory(List.of(campaignInventorySchedulesHistory));

    return campaignInventorySchedules;
  }

  /**
   * Unified method to enrich schedules with reach and frequency data using Map-based approach. This
   * method calls the getReachAndFrequencyBySites API once for all inventories and maps the response
   * back to schedules using referenceId. Uses schedule-specific dates when available, otherwise
   * falls back to campaign dates.
   *
   * @param scheduleMap Map of inventory referenceId to Schedule (will be updated with
   *     impressions/reach)
   * @param inventoryMap Map of inventory referenceId to Inventory entity
   * @param campaign The campaign entity for date range calculation (used as fallback)
   * @return Map of inventory referenceId to Schedule with enriched impressions and reach
   */
  private Map<String, Schedule> enrichSchedulesWithReachAndFrequency(
      Map<String, Schedule> scheduleMap, Map<String, Inventory> inventoryMap, Campaign campaign) {

    if (scheduleMap == null
        || scheduleMap.isEmpty()
        || inventoryMap == null
        || inventoryMap.isEmpty()) {
      log.warn("Cannot enrich schedules: scheduleMap or inventoryMap is empty");
      return scheduleMap;
    }

    try {
      LocalDate startDate = campaign.getStartDate();
      LocalDate endDate = campaign.getEndDate();
      int duration = (int) DAYS.between(startDate, endDate) + 1;

      // Wrap single-schedule map into List<Schedule> map for shared payload builder
      Map<String, List<Schedule>> referenceIdToSchedules =
          scheduleMap.entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> List.of(e.getValue())));

      List<MeasureReachFrequencyResponseDTO> responses =
          mwMeasureService.getReachAndFrequencyBySitesFromSchedules(
              duration,
              referenceIdToSchedules,
              inventoryMap,
              startDate,
              endDate,
              startDate,
              endDate);

      if (responses == null || responses.isEmpty()) {
        log.warn("No valid inventories found for reach and frequency enrichment");
        return scheduleMap;
      }

      Map<String, MeasureReachFrequencyResponseDTO> responseMap =
          responses.stream()
              .filter(response -> response.getReferenceId() != null)
              .collect(
                  Collectors.toMap(
                      MeasureReachFrequencyResponseDTO::getReferenceId,
                      Function.identity(),
                      (existing, replacement) -> existing));

      // Enrich schedules with impressions and reach using Stream API
      scheduleMap.replaceAll(
          (referenceId, schedule) -> {
            MeasureReachFrequencyResponseDTO response = responseMap.get(referenceId);
            if (response != null && Objects.equals(response.getStatus(), "success")) {
              schedule.setImpressions(response.getImpressions());
              schedule.setReach(response.getReach());
              log.debug(
                  "Enriched schedule for referenceId: {} with impressions: {}, reach: {}",
                  referenceId,
                  response.getImpressions(),
                  response.getReach());
            } else {
              // Set null if inventory not found in response
              schedule.setImpressions(null);
              schedule.setReach(null);
              log.warn(
                  "No reach and frequency data found for inventory referenceId: {}. Setting values to null.",
                  referenceId);
            }
            return schedule;
          });

      log.info(
          "Successfully enriched {} schedules with reach and frequency data", scheduleMap.size());

    } catch (Exception e) {
      log.warn(
          "Failed to enrich schedules with reach and frequency data. Setting all values to null. Error: {}",
          e.getMessage(),
          e);
      // Set null for all schedules on error
      scheduleMap
          .values()
          .forEach(
              schedule -> {
                schedule.setImpressions(null);
                schedule.setReach(null);
              });
    }

    return scheduleMap;
  }

  /**
   * Enriches the request with inventory data from database. Fetches all required inventories in a
   * single query for performance optimization.
   *
   * @param request The original request
   * @return Enriched request with default values filled in
   */
  private MeasureReachFrequencyRequestDTO enrichRequestWithInventoryData(
      MeasureReachFrequencyRequestDTO request) {

    // Set default duration if not provided
    if (request.getDuration() == null) {
      request.setDuration(DEFAULT_DURATION);
    }

    if (request.getInventories() == null || request.getInventories().isEmpty()) {
      return request;
    }

    // Extract all reference IDs for bulk query
    List<String> referenceIds =
        request.getInventories().stream()
            .map(MeasureInventoryDTO::getReferenceId)
            .filter(refId -> refId != null && !refId.isEmpty())
            .distinct()
            .collect(Collectors.toList());

    if (referenceIds.isEmpty()) {
      return request;
    }

    // Fetch all inventories in a single database call for performance
    List<Inventory> inventories = inventoryService.findAllByReferenceIds(referenceIds);

    // Create a map for quick lookup
    Map<String, Inventory> inventoryMap =
        inventories.stream()
            .collect(Collectors.toMap(Inventory::getReferenceId, Function.identity()));

    // Enrich each inventory DTO with default values from database
    List<MeasureInventoryDTO> enrichedInventories =
        request.getInventories().stream()
            .map(
                invDto -> {
                  Inventory inventory = inventoryMap.get(invDto.getReferenceId());

                  // Set default spotsPerHour from inventory if not provided
                  if (invDto.getSpotsPerHour() == null && inventory != null) {
                    Integer spotsPerHour = (int) InventoryService.getSpotsPerHour(inventory);
                    invDto.setSpotsPerHour(spotsPerHour);
                  }

                  // Set default type from inventory if not provided
                  if (invDto.getType() == null || invDto.getType().isEmpty()) {
                    if (inventory != null) {
                      String inventoryType = inventory.getClassification();
                      // Map inventory type to API type
                      invDto.setType(mapInventoryTypeToApiType(inventoryType));
                    }
                  }

                  return invDto;
                })
            .collect(Collectors.toList());

    request.setInventories(enrichedInventories);
    return request;
  }

  /**
   * Converts booking matrix to dayparts format for reach and frequency API. Converts hours from
   * Integer to String format ("00", "01", etc.).
   *
   * @param bookingMatrix The booking matrix with date as key and list of hours as value
   * @param isClassicInventory If true, scheduledTime will be null (not required for classic)
   * @return List of daypart schedules
   */
  private List<MeasureInventoryDTO.Dayparts> convertBookingMatrixToDayparts(
      Map<String, List<Integer>> bookingMatrix, boolean isClassicInventory) {

    if (bookingMatrix == null || bookingMatrix.isEmpty()) {
      return Collections.emptyList();
    }

    return bookingMatrix.entrySet().stream()
        .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
        .map(
            entry -> {
              String scheduledDate = entry.getKey();
              List<Integer> hours = entry.getValue();

              // Convert hours from Integer to String format ("00", "01", etc.)
              List<String> scheduledTimes = null;
              if (!isClassicInventory) {
                scheduledTimes =
                    hours.stream()
                        .sorted()
                        .map(hour -> String.format("%02d", hour))
                        .collect(Collectors.toList());
              }

              return MeasureInventoryDTO.Dayparts.builder()
                  .scheduledDate(scheduledDate)
                  .scheduledTime(scheduledTimes)
                  .build();
            })
        .collect(Collectors.toList());
  }

  /**
   * Maps internal inventory type to API type format.
   *
   * @param inventoryClassification Internal inventory type
   * @return API type (network, billboard, or static_billboard)
   */
  private String mapInventoryTypeToApiType(String inventoryClassification) {
    if (inventoryClassification == null) {
      return "billboard";
    }

    if (inventoryClassification.contains("Classic")) {
      return "static_billboard";
    } else if (inventoryClassification.contains("Network")) {
      return "network";
    } else {
      return "billboard";
    }
  }

  /**
   * Checks if inventory is classic type (doesn't require scheduledTime in dayparts).
   *
   * @param inventory The inventory entity
   * @return true if inventory is classic type
   */
  private boolean isClassicInventory(Inventory inventory) {
    if (inventory == null || inventory.getClassification() == null) {
      return false;
    }
    return inventory.getClassification().equalsIgnoreCase("Classic");
  }

  /**
   * Resolves the schedule type from the inventory's digital booking mode. Digital inventory with a
   * "time" booking mode produces DAYPART schedules; a "loop" booking mode (or any other case)
   * produces LOOP schedules.
   *
   * @param inventory The inventory entity
   * @return DAYPART when the inventory is digital and booked by time, otherwise LOOP
   */
  private static Schedule.Type resolveScheduleType(Inventory inventory) {
    if (inventory == null
        || inventory.getClassification() == null
        || !inventory.getClassification().equalsIgnoreCase("Digital")
        || inventory.getDigitalFields() == null) {
      return Schedule.Type.LOOP;
    }

    String bookingMode = inventory.getDigitalFields().getBookingMode();
    if (bookingMode != null && bookingMode.equalsIgnoreCase("time")) {
      return Schedule.Type.DAYPART;
    }

    return Schedule.Type.LOOP;
  }

  private static List<Schedule.Weekday> getWeekdays(Inventory inventory) {
    List<Schedule.Weekday> scheduleDays = null;
    if (inventory.getOperatingTimes() != null && !inventory.getOperatingTimes().isEmpty()) {
      scheduleDays =
          inventory.getOperatingTimes().keySet().stream()
              .map(weekday -> Schedule.Weekday.valueOf(weekday.name()))
              .collect(Collectors.toList());
    }
    return scheduleDays;
  }

  /**
   * Calculate simple booking matrix with hours based on inventory operating times for each specific
   * day of the week. For each date, the system matches the date with its corresponding day of the
   * week, retrieves the applicable start and end time ranges from operatingTimes for that day,
   * converts each time range into a list of hour values, and adds these hour values to the booking
   * matrix under the matching date key.
   *
   * @param startDate Campaign start date
   * @param endDate Campaign end date
   * @param inventory The inventory entity containing operatingTimes
   * @return Map with date strings (yyyy-MM-dd) as keys and list of hours based on operating times
   *     for that day as values
   */
  public Map<String, List<Integer>> calculateSimpleBookingMatrix(
      LocalDate startDate, LocalDate endDate, Inventory inventory) {
    Map<String, List<Integer>> bookingMatrix = new HashMap<>();

    // If inventory has no operating times, return empty booking matrix
    if (inventory.getOperatingTimes() == null || inventory.getOperatingTimes().isEmpty()) {
      return bookingMatrix;
    }

    // Iterate through each date in the campaign date range
    LocalDate currentDate = startDate;
    while (!currentDate.isAfter(endDate)) {
      String dateKey = currentDate.format(DATE_FORMATTER);

      // Get the day of week for the current date
      DayOfWeek dayOfWeek = currentDate.getDayOfWeek();

      // Map DayOfWeek to Inventory.Weekday
      Inventory.Weekday weekday = mapDayOfWeekToInventoryWeekday(dayOfWeek);

      // Get operating times for this specific weekday
      List<Integer> hoursForDate = getHoursForWeekday(inventory.getOperatingTimes().get(weekday));

      // Add hours to booking matrix for this date
      bookingMatrix.put(dateKey, hoursForDate);

      currentDate = currentDate.plusDays(1);
    }

    return bookingMatrix;
  }

  /**
   * Map Java's DayOfWeek to Inventory.Weekday enum.
   *
   * @param dayOfWeek Java's DayOfWeek
   * @return Corresponding Inventory.Weekday
   */
  private Inventory.Weekday mapDayOfWeekToInventoryWeekday(DayOfWeek dayOfWeek) {
    return switch (dayOfWeek) {
      case MONDAY -> Inventory.Weekday.MONDAY;
      case TUESDAY -> Inventory.Weekday.TUESDAY;
      case WEDNESDAY -> Inventory.Weekday.WEDNESDAY;
      case THURSDAY -> Inventory.Weekday.THURSDAY;
      case FRIDAY -> Inventory.Weekday.FRIDAY;
      case SATURDAY -> Inventory.Weekday.SATURDAY;
      case SUNDAY -> Inventory.Weekday.SUNDAY;
    };
  }

  public static List<Integer> getAvailableHours(Inventory inventory) {
    if (inventory.getOperatingTimes() == null || inventory.getOperatingTimes().isEmpty()) {
      return List.of();
    }

    Set<Integer> hourSet = new TreeSet<>(); // Sorted & unique

    inventory
        .getOperatingTimes()
        .values()
        .forEach(
            times -> {
              for (Inventory.OperatingTime time : times) {
                try {
                  LocalTime start = LocalTime.parse(time.getStart());
                  LocalTime end = LocalTime.parse(time.getEnd());

                  int startHour = start.getHour();
                  int endHour = end.getHour();

                  for (int hour = startHour; hour <= endHour; hour++) {
                    hourSet.add(hour);
                  }

                } catch (Exception e) {
                  log.warn("Error parsing operating time: {}", e.getMessage());
                }
              }
            });

    return new ArrayList<>(hourSet);
  }

  /**
   * Calculate adPlays: spotsPerHour * total hours in bookingMatrix.
   *
   * @param spotsPerHour The spotPerHour booked within a schedule
   * @param bookingMatrix The date-based booking matrix
   * @return Total ad plays
   */
  public long calculateAdPlays(long spotsPerHour, Map<String, List<Integer>> bookingMatrix) {
    if (bookingMatrix == null || bookingMatrix.isEmpty()) {
      return 0;
    }

    if (spotsPerHour <= 0) {
      return 0;
    }

    // Count total hours across all dates
    long totalHours = bookingMatrix.values().stream().mapToLong(List::size).sum();

    // adPlays = spotsPerHour * total hours
    return spotsPerHour * totalHours;
  }

  /**
   * Calculate Planned SOT from booking matrix. Planned SOT is the sum of hours in the booking
   * matrix.
   *
   * @param bookingMatrix The booking matrix mapping dates to hours
   * @return Planned SOT in hours (sum of all hours in booking matrix)
   */
  private static double calculatePlannedSotFromBookingMatrix(
      Map<String, List<Integer>> bookingMatrix) {
    if (bookingMatrix == null || bookingMatrix.isEmpty()) {
      return 0.0;
    }
    return bookingMatrix.values().stream().mapToLong(List::size).sum();
  }

  /**
   * Calculate plannedSot from planned ad plays and spot duration.
   *
   * <p>Formula: (plannedAdPlay * spotDuration) / 3600 — total planned broadcast hours of the ad (ad
   * plays x seconds-per-play, converted from seconds to hours). Floating-point division keeps
   * fractional hours.
   *
   * @param plannedAdPlay the schedule's planned ad plays (adPlays)
   * @param spotDuration the spot duration in seconds
   * @return planned SOT in hours
   */
  private static double calculatePlannedSotFromAdPlays(long plannedAdPlay, long spotDuration) {
    if (plannedAdPlay <= 0 || spotDuration <= 0) {
      return 0.0;
    }
    return (plannedAdPlay * spotDuration) / 3600.0;
  }

  /**
   * Calculate Total SOT based on campaign dates, schedule days, and inventory operating times.
   * Total SOT represents the total available operating hours for all days that fall within the
   * campaign date range and match the schedule days.
   *
   * @param startDate Campaign/schedule start date
   * @param endDate Campaign/schedule end date
   * @param scheduleDays List of weekdays when the schedule is active
   * @param inventory The inventory entity containing operating times
   * @return Total SOT in hours
   */
  private static double calculateTotalSotFromCampaignDates(
      LocalDate startDate,
      LocalDate endDate,
      List<Schedule.Weekday> scheduleDays,
      Inventory inventory) {
    if (startDate == null
        || endDate == null
        || scheduleDays == null
        || scheduleDays.isEmpty()
        || inventory == null
        || inventory.getOperatingTimes() == null
        || inventory.getOperatingTimes().isEmpty()) {
      return 0.0;
    }

    // Convert Schedule.Weekday to Inventory.Weekday for lookup
    Set<Inventory.Weekday> inventoryWeekdays =
        scheduleDays.stream()
            .map(day -> Inventory.Weekday.valueOf(day.name()))
            .collect(Collectors.toSet());

    double totalHours = 0.0;

    // Iterate through each date in the campaign date range
    LocalDate currentDate = startDate;
    while (!currentDate.isAfter(endDate)) {
      // Get the day of week for the current date
      DayOfWeek dayOfWeek = currentDate.getDayOfWeek();
      Inventory.Weekday weekday = mapDayOfWeekToInventoryWeekdayStatic(dayOfWeek);

      // Only calculate hours if this day is in the schedule days
      if (inventoryWeekdays.contains(weekday)) {
        // Get operating times for this specific weekday
        List<Inventory.OperatingTime> operatingTimes = inventory.getOperatingTimes().get(weekday);
        if (operatingTimes != null && !operatingTimes.isEmpty()) {
          // Calculate hours for this day from operating times
          totalHours += calculateHoursFromOperatingTimes(operatingTimes);
        }
      }

      currentDate = currentDate.plusDays(1);
    }

    return totalHours * getClientPerLoop(inventory);
  }

  private static long getClientPerLoop(Inventory inventory) {
    try {
      return inventory.getDigitalFields() != null
              && inventory.getDigitalFields().getSpotsPerLoop() != null
          ? inventory.getDigitalFields().getSpotsPerLoop()
          : 1;
    } catch (Exception e) {
      return 1;
    }
  }

  /**
   * Get list of hours for a specific weekday based on inventory operating times.
   *
   * @param operatingTimes The inventory operating time ranges
   * @return List of hours (0–23), sorted and unique
   */
  private List<Integer> getHoursForWeekday(List<Inventory.OperatingTime> operatingTimes) {

    return new ArrayList<>(resolveActiveHours(operatingTimes));
  }

  /**
   * Calculate total hours from a list of operating times. Supports overnight ranges (e.g., 22:00 →
   * 02:00, 05:00 → 00:00). The end hour is exclusive (e.g., 05:00-10:00 = 5 hours: 5,6,7,8,9).
   *
   * @param operatingTimes List of operating time ranges
   * @return Total hours
   */
  private static double calculateHoursFromOperatingTimes(
      List<Inventory.OperatingTime> operatingTimes) {

    return resolveActiveHours(operatingTimes).size();
  }

  /**
   * Resolves all active operating hours (0–23) from inventory operating time ranges.
   *
   * <p>Business rules: - Any activity within an hour counts that hour - End hour is exclusive -
   * Supports overnight ranges and midnight boundaries - Treats 23:xx as end-of-day (24:00)
   *
   * @param operatingTimes list of inventory operating time ranges
   * @return sorted set of active hours (0–23)
   */
  private static Set<Integer> resolveActiveHours(List<Inventory.OperatingTime> operatingTimes) {

    if (operatingTimes == null || operatingTimes.isEmpty()) {
      return Set.of();
    }

    Set<Integer> activeHours = new TreeSet<>();

    for (Inventory.OperatingTime time : operatingTimes) {
      try {
        if (time.getStart() == null || time.getEnd() == null) {
          log.warn("Missing operating time values: {}", time);
          continue;
        }

        LocalTime start = LocalTime.parse(time.getStart());
        LocalTime end = LocalTime.parse(time.getEnd());

        if (start.equals(end)) {
          continue; // Treat same start and end as closed interval
        }

        int startHour = start.getHour();
        int endHour = end.getHour();

        boolean isEndOfDay = end.getHour() == 23 && end.getMinute() > 0;
        boolean isMidnight = end.equals(LocalTime.MIDNIGHT);

        if (isEndOfDay) {
          endHour = 24;
        }

        if (startHour == endHour && !isMidnight) {
          activeHours.add(startHour); // Partial-hour activity
          continue;
        }

        if (startHour < endHour && !isMidnight) {
          for (int h = startHour; h < endHour; h++) {
            activeHours.add(h); // Same-day range
          }
          continue;
        }

        for (int h = startHour; h < 24; h++) {
          activeHours.add(h); // Overnight current-day hours
        }

        if (!isMidnight) {
          for (int h = 0; h < endHour; h++) {
            activeHours.add(h); // Overnight next-day hours
          }
        }

      } catch (Exception e) {
        log.warn(
            "Invalid operating time [start={}, end={}]: {}",
            time.getStart(),
            time.getEnd(),
            e.getMessage());
      }
    }

    return activeHours;
  }

  /**
   * Map Java's DayOfWeek to Inventory.Weekday enum (static version for use in static methods).
   *
   * @param dayOfWeek Java's DayOfWeek
   * @return Corresponding Inventory.Weekday
   */
  private static Inventory.Weekday mapDayOfWeekToInventoryWeekdayStatic(DayOfWeek dayOfWeek) {
    return switch (dayOfWeek) {
      case MONDAY -> Inventory.Weekday.MONDAY;
      case TUESDAY -> Inventory.Weekday.TUESDAY;
      case WEDNESDAY -> Inventory.Weekday.WEDNESDAY;
      case THURSDAY -> Inventory.Weekday.THURSDAY;
      case FRIDAY -> Inventory.Weekday.FRIDAY;
      case SATURDAY -> Inventory.Weekday.SATURDAY;
      case SUNDAY -> Inventory.Weekday.SUNDAY;
    };
  }

  /**
   * Bulk deselect inventories - optimized to use a single database call for deleting all
   * configurations. First deletes all related Schedule records, then deletes the
   * CampaignInventorySchedules. Cache eviction is fanned out across virtual threads (still awaited
   * before returning, so the cache is guaranteed consistent by the time this method returns) and
   * the recommendation-engine sync is deferred to a virtual thread after the essential DB mutations
   * complete, so large batches don't block the response.
   *
   * @param campaignId The campaign ID
   * @param inventories List of inventories to deselect
   * @return Total count of inventories processed successfully
   */
  private int bulkDeselectInventories(String campaignId, List<Inventory> inventories) {
    log.info("Bulk deselecting {} inventories for campaignId: {}", inventories.size(), campaignId);

    // Extract inventory IDs and collect unique mediaOwnerIds
    List<String> inventoryIds =
        inventories.stream().map(Inventory::getId).collect(Collectors.toList());
    Set<String> mediaOwnerIds =
        inventories.stream()
            .map(Inventory::getMediaOwnerId)
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toSet());

    // Fetch all CampaignInventorySchedules to collect schedule IDs before deletion
    List<CampaignInventorySchedules> schedulesToDelete =
        inventorySchedulesRepository.findByCampaignIdAndInventoryIdIn(campaignId, inventoryIds);

    if (schedulesToDelete.isEmpty()) {
      log.debug(
          "No campaign inventory schedules found for campaignId: {} and {} inventory IDs",
          campaignId,
          inventoryIds.size());
      recommendationService.syncSelectedInventories(
          campaignId, inventoryIds, RecommendationService.OperationType.DESELECT);
      return 0;
    }

    // Collect all schedule IDs that need to be deleted
    List<String> scheduleIdsToDelete =
        schedulesToDelete.stream()
            .filter(s -> s.getScheduleIds() != null && !s.getScheduleIds().isEmpty())
            .flatMap(s -> s.getScheduleIds().stream())
            .distinct()
            .toList();

    // Delete all Schedule entities first in a single batch call
    if (!scheduleIdsToDelete.isEmpty()) {
      scheduleRepository.deleteByIdIn(scheduleIdsToDelete);
      log.debug(
          "Deleted {} Schedule entities for campaignId: {} and {} inventories",
          scheduleIdsToDelete.size(),
          campaignId,
          inventoryIds.size());
    }

    // Delete all CampaignInventorySchedules in a single database call
    long deletedCount =
        inventorySchedulesRepository.deleteByCampaignIdAndInventoryIdIn(campaignId, inventoryIds);

    log.info(
        "Successfully deleted {} configurations and schedules in bulk for campaignId: {}",
        deletedCount,
        campaignId);

    // Evict cache for all affected inventories. Fanned out across virtual threads (rather than
    // looped sequentially) so N Redis round-trips collapse into ~1 round-trip; still joined here
    // so the cache is guaranteed fully evicted before this method returns - callers elsewhere
    // (e.g. the /filter and selected-inventory endpoints) read this cache synchronously, so that
    // guarantee cannot be deferred without risking a stale read right after this response.
    List<CompletableFuture<Void>> evictions =
        inventoryIds.stream()
            .map(
                inventoryId ->
                    virtualThreadService.runAsync(
                        () -> scheduleCacheEvictor.evict(campaignId, inventoryId)))
            .toList();
    evictions.forEach(CompletableFuture::join);

    // Check and remove mediaOwnerIds from companyAccess if no other configs exist. Grouped into a
    // single aggregation query instead of one count query per distinct mediaOwnerId.
    if (!mediaOwnerIds.isEmpty()) {
      Map<String, Long> remainingCountsByMediaOwnerId =
          inventorySchedulesRepository.countByCampaignIdGroupedByMediaOwnerIdIn(
              campaignId, mediaOwnerIds);

      List<String> mediaOwnerIdsToRemove =
          mediaOwnerIds.stream()
              .filter(id -> id != null && !id.isBlank())
              .filter(id -> remainingCountsByMediaOwnerId.getOrDefault(id, 0L) == 0L)
              .toList();

      log.debug(
          "After bulk deletion for campaignId: {}, {} of {} distinct mediaOwnerIds have no remaining configs",
          campaignId,
          mediaOwnerIdsToRemove.size(),
          mediaOwnerIds.size());

      removeMediaOwnerIdsFromCompanyAccess(campaignId, mediaOwnerIdsToRemove);
    }

    log.info(
        "Bulk DESELECT operation completed successfully for campaignId: {} - Total processed: {}",
        campaignId,
        deletedCount);

    // Sync deselected inventories with recommendation engine off the request path - it's already
    // self-contained (internal try/catch, doesn't affect the return value) and nothing in
    // mw-planner reads its result back synchronously.
    virtualThreadService.runAsync(
        () -> {
          try {
            recommendationService.syncSelectedInventories(
                campaignId, inventoryIds, RecommendationService.OperationType.DESELECT);
          } catch (Exception e) {
            log.warn(
                "Async recommendation sync failed for bulk deselect campaignId: {}: {}",
                campaignId,
                e.getMessage());
          }
        });

    // Log activity for bulk inventory deselection
    if (deletedCount > 0) {
      try {
        campaignActivityService.logActivity(
            campaignId,
            CampaignActivityService.OperationType.REMOVED,
            DESELECTED_INVENTORY_COUNT.key(),
            deletedCount);
      } catch (Exception e) {
        log.warn("Failed to log bulk deselect inventories: {}", e.getMessage());
      }
    }

    return (int) deletedCount;
  }

  public Long countByCampaignId(String campaignId) {
    log.debug("Finding selected inventory count for campaignId: {}", campaignId);
    return inventorySchedulesRepository.countByCampaignId(campaignId);
  }

  public long countByCampaignIdAndMediaOwnerId(String campaignId, String mediaOwnerId) {
    log.debug(
        "Counting inventory schedules for campaignId: {} and mediaOwnerId: {}",
        campaignId,
        mediaOwnerId);
    return inventorySchedulesRepository.countByCampaignIdAndMediaOwnerId(campaignId, mediaOwnerId);
  }

  public List<CampaignInventorySchedules> findByCampaignIdAndMediaOwnerId(
      String campaignId, String mediaOwnerId) {
    log.debug(
        "Finding all campaign inventory schedules configs for campaignId: {} and mediaOwnerId: {}",
        campaignId,
        mediaOwnerId);
    return inventorySchedulesRepository.findByCampaignIdAndMediaOwnerId(campaignId, mediaOwnerId);
  }

  public List<CampaignInventorySchedules> findByCampaignIdAndMediaOwnerIdIn(
      String campaignId, List<String> mediaOwnerIds) {
    log.debug(
        "Finding all campaign inventory schedules configs for campaignId: {} and mediaOwnerIds: {}",
        campaignId,
        mediaOwnerIds);
    return inventorySchedulesRepository.findByCampaignIdAndMediaOwnerIdIn(
        campaignId, mediaOwnerIds);
  }

  /**
   * Remove all inventory schedules for a campaign. This method is called when the campaign's
   * country is changed, as inventories are country-specific. Also deletes all associated Schedule
   * entities.
   *
   * @param campaignId Campaign ID
   */
  public void removeAllInventoriesForCampaign(String campaignId) {
    log.info("Removing all inventory schedules for campaignId: {}", campaignId);

    // Get all configs to collect mediaOwnerIds, inventory IDs, and schedule IDs
    List<CampaignInventorySchedules> allSchedules =
        inventorySchedulesRepository.findByCampaignId(campaignId);

    if (allSchedules.isEmpty()) {
      log.debug("No schedules found for campaignId: {}", campaignId);
      return;
    }

    // Collect all schedule IDs that need to be deleted
    List<String> scheduleIdsToDelete =
        allSchedules.stream()
            .filter(s -> s.getScheduleIds() != null && !s.getScheduleIds().isEmpty())
            .flatMap(s -> s.getScheduleIds().stream())
            .distinct()
            .collect(Collectors.toList());

    // Collect unique mediaOwnerIds from configs
    Set<String> mediaOwnerIds =
        allSchedules.stream()
            .map(CampaignInventorySchedules::getMediaOwnerId)
            .filter(id -> !id.isBlank())
            .collect(Collectors.toSet());

    // Delete all Schedule entities in a single batch call
    if (!scheduleIdsToDelete.isEmpty()) {
      scheduleRepository.deleteByIdIn(scheduleIdsToDelete);
      log.debug(
          "Deleted {} Schedule entities for campaignId: {}",
          scheduleIdsToDelete.size(),
          campaignId);
    }

    // Delete all CampaignInventorySchedules in a single database call
    inventorySchedulesRepository.deleteByCampaignId(campaignId);

    // Evict cache for all affected inventories
    for (CampaignInventorySchedules schedule : allSchedules) {
      scheduleCacheEvictor.evict(campaignId, schedule.getInventoryId());
    }

    // Clear all mediaOwnerIds from companyAccess since all configs are removed
    if (!mediaOwnerIds.isEmpty()) {
      Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);
      if (campaign.getCompanyAccess() != null && !campaign.getCompanyAccess().isEmpty()) {
        // Remove all mediaOwnerIds that were in the deleted configs
        campaign.getCompanyAccess().removeAll(mediaOwnerIds);
        campaignService.save(campaign);

        // evict campaign cache
        campaignService.campaignCacheEvict(campaignId);

        log.info(
            "Removed {} mediaOwnerIds from companyAccess for campaignId: {}",
            mediaOwnerIds.size(),
            campaignId);
      }
    }

    log.info(
        "Successfully removed {} inventory schedules and {} Schedule entities for campaignId: {}",
        allSchedules.size(),
        scheduleIdsToDelete.size(),
        campaignId);
  }

  /**
   * Retrieve all schedules from CampaignInventorySchedules for given inventory IDs. Returns
   * schedules grouped by inventory ID. If inventoryIds is empty, returns all schedules for the
   * campaign. Supports optional filtering by inventoryType.
   *
   * @param campaignId Campaign ID
   * @param inventoryIds List of inventory IDs (can be empty to get all schedules for campaign)
   * @param inventoryType Optional inventory type filter
   * @return List of InventorySchedulesResponseDTO containing schedules grouped by inventory ID
   */
  public List<InventorySchedulesResponseDTO> getSchedulesByInventoryIds(
      String campaignId, List<String> inventoryIds, String inventoryType) {
    log.info(
        "Retrieving schedules for campaignId: {} with {} inventory IDs and inventoryType filter: {}",
        campaignId,
        inventoryIds != null ? inventoryIds.size() : 0,
        inventoryType);

    List<CampaignInventorySchedules> schedulesList;

    // If inventoryIds is empty or null, get all schedules for the campaign
    if (inventoryIds == null || inventoryIds.isEmpty()) {
      log.info(
          "No inventory IDs provided, retrieving all schedules for campaignId: {}", campaignId);
      schedulesList = inventorySchedulesRepository.findByCampaignId(campaignId);
    } else {
      // Fetch schedules for the given campaign and inventory IDs
      schedulesList =
          inventorySchedulesRepository.findByCampaignIdAndInventoryIdIn(campaignId, inventoryIds);
    }

    // Map schedules by inventory ID for quick lookup
    Map<String, List<Schedule>> schedulesByInventoryId = groupSchedulesByInventoryId(schedulesList);

    // Get the list of inventory IDs to process (either from request or from schedules)
    List<String> inventoryIdsToProcess;
    if (inventoryIds == null || inventoryIds.isEmpty()) {
      // Extract inventory IDs from schedules
      inventoryIdsToProcess =
          schedulesList.stream()
              .map(CampaignInventorySchedules::getInventoryId)
              .distinct()
              .toList();
    } else {
      inventoryIdsToProcess = inventoryIds;
    }

    // Apply inventoryType filter if provided
    List<String> filteredInventoryIds = inventoryIdsToProcess;
    if (inventoryType != null && !inventoryType.trim().isEmpty()) {
      String normalizedInventoryType = inventoryType.trim().toUpperCase();
      log.info("Filtering schedules by inventoryType: {}", normalizedInventoryType);
      filteredInventoryIds =
          inventoryService.findIdByIdInAndType(inventoryIdsToProcess, normalizedInventoryType);
    }

    // Batch-fetch inventories (one query) so SOV can be computed classification-aware below
    Map<String, Inventory> inventoryById =
        inventoryService.findAllByIds(filteredInventoryIds).stream()
            .collect(Collectors.toMap(Inventory::getId, Function.identity()));

    // Map filtered inventory IDs to response DTOs
    return filteredInventoryIds.stream()
        .map(
            inventoryId ->
                createInventorySchedulesResponseDTO(
                    inventoryId,
                    schedulesByInventoryId.get(inventoryId),
                    inventoryById.get(inventoryId)))
        .toList();
  }

  // Group schedules by inventory ID
  private Map<String, List<Schedule>> groupSchedulesByInventoryId(
      List<CampaignInventorySchedules> schedulesList) {
    Map<String, List<Schedule>> schedulesByInventoryId = new HashMap<>();

    // Collect all schedule IDs
    List<String> allScheduleIds =
        schedulesList.stream()
            .filter(s -> s.getScheduleIds() != null && !s.getScheduleIds().isEmpty())
            .flatMap(s -> s.getScheduleIds().stream())
            .distinct()
            .collect(Collectors.toList());

    // Fetch all schedules in one query
    Map<String, Schedule> scheduleMap = new HashMap<>();
    if (!allScheduleIds.isEmpty()) {
      scheduleMap =
          scheduleRepository.findAllById(allScheduleIds).stream()
              .collect(Collectors.toMap(Schedule::getId, s -> s));
    }

    // Group schedules by inventory ID
    for (CampaignInventorySchedules schedules : schedulesList) {
      String inventoryId = schedules.getInventoryId();
      if (schedules.getScheduleIds() != null && !schedules.getScheduleIds().isEmpty()) {
        List<Schedule> schedulesForInventory =
            schedules.getScheduleIds().stream()
                .map(scheduleMap::get)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        if (!schedulesForInventory.isEmpty()) {
          schedulesByInventoryId.put(inventoryId, schedulesForInventory);
        }
      }
    }

    return schedulesByInventoryId;
  }

  // Create the response DTO for a specific inventoryId
  private InventorySchedulesResponseDTO createInventorySchedulesResponseDTO(
      String inventoryId, List<Schedule> schedules, Inventory inventory) {
    List<InventorySchedulesResponseDTO.ScheduleDTO> scheduleDTOs =
        convertSchedulesToDTO(schedules, inventory);

    return InventorySchedulesResponseDTO.builder()
        .inventoryId(inventoryId)
        .schedules(scheduleDTOs)
        .build();
  }

  /**
   * Get schedules by their IDs.
   *
   * @param scheduleIds List of schedule IDs
   * @return List of Schedule entities
   */
  public List<Schedule> getSchedulesByIds(List<String> scheduleIds) {
    if (scheduleIds == null || scheduleIds.isEmpty()) {
      return Collections.emptyList();
    }
    return scheduleRepository.findAllById(scheduleIds);
  }

  // Convert schedules to DTOs, computing a classification-aware SOV per schedule
  public List<InventorySchedulesResponseDTO.ScheduleDTO> convertSchedulesToDTO(
      List<Schedule> schedules, Inventory inventory) {
    String classification = inventory != null ? inventory.getClassification() : null;
    Integer maxSpotsPerLoop = getInventoryMaxSpotsPerLoop(inventory);
    return schedules != null
        ? schedules.stream()
            .map(
                schedule ->
                    InventorySchedulesResponseDTO.ScheduleDTO.builder()
                        .id(schedule.getId())
                        .name(schedule.getName())
                        .startDate(schedule.getStartDate())
                        .endDate(schedule.getEndDate())
                        .scheduleDays(convertScheduleDaysToStringList(schedule.getScheduleDays()))
                        .bookingMatrix(schedule.getBookingMatrix())
                        .duration(schedule.getDuration())
                        .spotsPerLoop(schedule.getSpotsPerLoop())
                        .spotsPerHour(schedule.getSpotsPerHour())
                        .adPlays(schedule.getAdPlays())
                        .plannedSot(schedule.getPlannedSot())
                        .totalSot(schedule.getTotalSot())
                        .sov(
                            calculateInventorySov(
                                classification,
                                schedule.getSpotsPerLoop(),
                                maxSpotsPerLoop,
                                schedule.getTotalSot(),
                                schedule.getPlannedSot()))
                        .order(schedule.getOrder())
                        .basePrice(schedule.getBasePrice())
                        .impressions(schedule.getImpressions())
                        .reach(schedule.getReach())
                        .frequency(
                            calculateFrequency(schedule.getImpressions(), schedule.getReach()))
                        .discount(
                            schedule.getDiscount() != null
                                ? InventorySchedulesResponseDTO.ScheduleDTO.DiscountDTO.builder()
                                    .valueType(
                                        schedule.getDiscount().getValueType() != null
                                            ? schedule.getDiscount().getValueType().name()
                                            : null)
                                    .value(schedule.getDiscount().getValue())
                                    .build()
                                : null)
                        .bonusType(schedule.getBonusType())
                        .build())
            .toList()
        : Collections.emptyList();
  }

  private static Double calculateFrequency(Long impressions, Long reach) {
    if (impressions == null || reach == null || reach == 0L) {
      return null;
    }
    return impressions.doubleValue() / reach;
  }

  // Convert scheduleDays enum to a list of strings
  private List<String> convertScheduleDaysToStringList(List<Schedule.Weekday> scheduleDays) {
    return scheduleDays != null
        ? scheduleDays.stream().map(Enum::name).toList()
        : Collections.emptyList();
  }

  /**
   * Create or update schedules for multiple inventories in bulk. This method efficiently handles
   * bulk operations by fetching all required data in minimal database calls.
   *
   * <p>This method allows bulk creation/update of schedules across multiple inventories for a
   * campaign. It supports two modes: clearSchedules=true: Deletes all existing schedules for each
   * inventory and creates a new "Schedule 1" learSchedules=false: Appends a new schedule with the
   * next sequential name (e.g.,"Schedule 2", "Schedule 3") to existing schedules
   *
   * <p>The method automatically calculates backend-managed fields (duration, spotsPerHour, adPlays,
   * plannedSot, totalSot) from inventory configuration.
   *
   * @param campaignId Campaign ID
   * @param request Bulk schedules request containing inventory IDs, clearSchedules flag, and
   *     schedule details
   * @throws CampaignNotFoundException if campaign doesn't exist
   * @throws IllegalArgumentException if request validation fails
   */
  public void bulkSchedules(String campaignId, BulkSchedulesRequestDTO request) {
    log.info(
        "Processing bulk schedules for campaignId: {} with {} inventories, clearSchedules: {}",
        campaignId,
        request.getInventoryIds().size(),
        request.isClearSchedules());

    // Validate campaign exists - throws exception if not found
    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);

    // Validate request parameters
    if (request.getInventoryIds() == null || request.getInventoryIds().isEmpty()) {
      throw new IllegalArgumentException("Inventory IDs cannot be empty");
    }
    if (request.getSchedule() == null) {
      throw new IllegalArgumentException("Schedule details are required");
    }

    // Fetch all inventories in a single batch query
    List<Inventory> inventories = inventoryService.findAllByIds(request.getInventoryIds());
    if (inventories.isEmpty()) {
      log.warn("No inventories found for the provided IDs");
      return;
    }
    if (inventories.size() != request.getInventoryIds().size()) {
      log.warn(
          "Some inventories not found. Requested: {}, Found: {}",
          request.getInventoryIds().size(),
          inventories.size());
    }

    // Fetch all existing CampaignInventorySchedules configurations in a single batch query
    List<CampaignInventorySchedules> existingSchedules =
        inventorySchedulesRepository.findByCampaignIdAndInventoryIdIn(
            campaignId, request.getInventoryIds());

    // Create a lookup map: inventoryId -> CampaignInventorySchedules entity
    Map<String, CampaignInventorySchedules> schedulesMap =
        existingSchedules.stream()
            .collect(
                Collectors.toMap(
                    CampaignInventorySchedules::getInventoryId,
                    Function.identity(),
                    (existing, replacement) -> existing)); // Keep first if duplicate keys

    // Collect all existing schedule IDs from all CampaignInventorySchedules
    List<String> allExistingScheduleIds =
        existingSchedules.stream()
            .filter(s -> s.getScheduleIds() != null && !s.getScheduleIds().isEmpty())
            .flatMap(s -> s.getScheduleIds().stream())
            .distinct()
            .collect(Collectors.toList());

    // Fetch all existing Schedule entities in a single batch query
    Map<String, Schedule> existingScheduleMap = new HashMap<>();
    if (!allExistingScheduleIds.isEmpty()) {
      existingScheduleMap =
          scheduleRepository.findAllById(allExistingScheduleIds).stream()
              .collect(Collectors.toMap(Schedule::getId, Function.identity()));
    }

    // Collect all schedule IDs that need to be deleted (if clearSchedules=true)
    List<String> scheduleIdsToDelete = new ArrayList<>();

    // Use record to track inventory -> schedule relationship for batch processing
    record InventoryScheduleData(
        Inventory inventory,
        CampaignInventorySchedules campaignSchedule,
        Schedule schedule,
        List<String> currentScheduleIds) {}

    List<InventoryScheduleData> scheduleDataList = new ArrayList<>();

    // Build all schedules in memory first
    for (Inventory inventory : inventories) {
      String inventoryId = inventory.getId();

      // Get existing CampaignInventorySchedules entity or create new one
      CampaignInventorySchedules scheduleEntity = schedulesMap.getOrDefault(inventoryId, null);

      if (scheduleEntity == null) {
        // This inventory doesn't have a CampaignInventorySchedules configuration yet
        // Create a new one with basic structure
        scheduleEntity = createNewScheduleEntity(campaign, inventory);
      }

      // Get existing schedule IDs for this inventory (if any)
      List<String> currentScheduleIds =
          scheduleEntity.getScheduleIds() != null
              ? new ArrayList<>(scheduleEntity.getScheduleIds())
              : new ArrayList<>();

      // Get existing Schedule entities for this inventory (for name generation)
      // We need these to determine the next schedule name (e.g., "Schedule 2", "Schedule 3")
      List<Schedule> existingSchedulesForInventory =
          currentScheduleIds.stream()
              .map(existingScheduleMap::get)
              .filter(Objects::nonNull)
              .collect(Collectors.toList());

      // If clearSchedules flag is true, mark all existing schedules for deletion
      if (request.isClearSchedules()) {
        scheduleIdsToDelete.addAll(currentScheduleIds);
        currentScheduleIds.clear(); // Clear the list for this inventory
      }

      // Create new Schedule entity from request DTO (without impressions/reach - will be enriched
      // later)
      String scheduleName =
          generateScheduleName(existingSchedulesForInventory, request.isClearSchedules());
      Integer scheduleOrder =
          generateScheduleOrder(existingSchedulesForInventory, request.isClearSchedules());
      Schedule newSchedule =
          createScheduleFromRequest(
              request.getSchedule(),
              inventory,
              scheduleName,
              scheduleOrder,
              campaign.getGoals() != null ? campaign.getGoals().getGoalType() : null);

      scheduleDataList.add(
          new InventoryScheduleData(inventory, scheduleEntity, newSchedule, currentScheduleIds));
    }

    // Build Map for unified enrichment: referenceId -> Schedule
    Map<String, Schedule> scheduleMap =
        scheduleDataList.stream()
            .collect(
                Collectors.toMap(
                    data ->
                        data.inventory().getReferenceId() != null
                            ? data.inventory().getReferenceId()
                            : data.inventory().getId(),
                    InventoryScheduleData::schedule,
                    (existing, replacement) -> existing));

    // Build Map for unified enrichment: referenceId -> Inventory
    Map<String, Inventory> inventoryMap =
        scheduleDataList.stream()
            .collect(
                Collectors.toMap(
                    data ->
                        data.inventory().getReferenceId() != null
                            ? data.inventory().getReferenceId()
                            : data.inventory().getId(),
                    InventoryScheduleData::inventory,
                    (existing, replacement) -> existing));

    // Enrich all schedules with reach and frequency data in a single API call
    if (!scheduleMap.isEmpty() && !inventoryMap.isEmpty()) {
      enrichSchedulesWithReachAndFrequency(scheduleMap, inventoryMap, campaign);
    }

    // Update basePrice for all schedules with enriched impressions
    scheduleDataList.forEach(
        data -> {
          String referenceId =
              data.inventory().getReferenceId() != null
                  ? data.inventory().getReferenceId()
                  : data.inventory().getId();
          Schedule schedule = scheduleMap.get(referenceId);
          if (schedule != null) {
            int scheduleDurationDays =
                (int) DAYS.between(schedule.getStartDate(), schedule.getEndDate());
            schedule.setBasePrice(
                calculateScheduleBasePriceForSchedule(
                    schedule.getAdPlays(),
                    schedule.getImpressions(),
                    data.inventory(),
                    campaign.getGoals() != null ? campaign.getGoals().getGoalType() : null,
                    scheduleDurationDays));
          }
        });

    // Batch delete all schedules that need to be removed (if clearSchedules=true)
    // Single database call instead of multiple calls inside the loop
    if (!scheduleIdsToDelete.isEmpty()) {
      scheduleRepository.deleteAllById(scheduleIdsToDelete);
      log.debug("Deleted {} existing schedules in batch", scheduleIdsToDelete.size());
    }

    // Batch save all new Schedule entities
    List<Schedule> schedulesToSave =
        scheduleDataList.stream()
            .map(
                data -> {
                  String referenceId =
                      data.inventory().getReferenceId() != null
                          ? data.inventory().getReferenceId()
                          : data.inventory().getId();
                  return scheduleMap.get(referenceId);
                })
            .filter(Objects::nonNull)
            .toList();

    List<Schedule> savedSchedules = scheduleRepository.saveAll(schedulesToSave);
    log.debug("Created {} new schedules in batch", savedSchedules.size());

    // Update CampaignInventorySchedules entities with the newly created schedule IDs
    for (int i = 0; i < scheduleDataList.size() && i < savedSchedules.size(); i++) {
      InventoryScheduleData data = scheduleDataList.get(i);
      Schedule savedSchedule = savedSchedules.get(i);
      String savedScheduleId = savedSchedule.getId();

      // Add the new schedule ID to the list
      List<String> updatedScheduleIds =
          data.currentScheduleIds() != null
              ? new ArrayList<>(data.currentScheduleIds())
              : new ArrayList<>();
      updatedScheduleIds.add(savedScheduleId);
      data.campaignSchedule().setScheduleIds(updatedScheduleIds);

      // Get all schedules for this CampaignInventorySchedules (including the newly added one)
      List<Schedule> allSchedulesForCampaignSchedule =
          scheduleRepository.findAllById(updatedScheduleIds);

      // Reset history and approvals when schedule is added
      resetHistoryAndApprovals(
          data.campaignSchedule(), allSchedulesForCampaignSchedule, data.inventory(), campaign);
    }

    List<CampaignInventorySchedules> campaignSchedulesToSave =
        scheduleDataList.stream().map(InventoryScheduleData::campaignSchedule).toList();

    // Batch save all CampaignInventorySchedules entities
    inventorySchedulesRepository.saveAll(campaignSchedulesToSave);
    log.debug(
        "Updated {} CampaignInventorySchedules configurations in batch",
        campaignSchedulesToSave.size());

    // Evict cache for all affected inventories (moved outside the loop for better performance)
    for (String inventoryId : request.getInventoryIds()) {
      scheduleCacheEvictor.evict(campaignId, inventoryId);
    }

    // Log activity for bulk schedules creation/update
    try {
      campaignActivityService.logActivity(
          campaignId,
          CampaignActivityService.OperationType.ADDED,
          BULK_SCHEDULES_COUNT.key(),
          campaignSchedulesToSave.size());
    } catch (Exception e) {
      log.warn("Failed to log bulk schedules activity: {}", e.getMessage());
    }

    log.info(
        "Successfully processed bulk schedules for {} inventories in campaignId: {}. "
            + "Created {} new schedules, deleted {} existing schedules",
        campaignSchedulesToSave.size(),
        campaignId,
        savedSchedules.size(),
        scheduleIdsToDelete.size());
  }

  /**
   * Get schedule name from request or generate it if not provided or blank. If name is provided and
   * not blank, returns it. Otherwise, generates the next sequential name based on existing
   * schedules.
   *
   * @param requestedName Schedule name from request (can be null or blank)
   * @param existingSchedules List of existing schedules for name generation
   * @return Schedule name to use
   */
  private String getOrGenerateScheduleName(String requestedName, List<Schedule> existingSchedules) {
    if (requestedName != null && !requestedName.trim().isEmpty()) {
      return requestedName.trim();
    }
    return generateScheduleName(existingSchedules, false);
  }

  /**
   * Generate the next schedule name. If clearSchedules is true, returns "Schedule 1". Otherwise,
   * returns the next sequential name (e.g., "Schedule 2", "Schedule 3").
   *
   * @param existingSchedules List of existing schedules
   * @param clearSchedules Whether schedules are being cleared
   * @return Next schedule name
   */
  private String generateScheduleName(List<Schedule> existingSchedules, boolean clearSchedules) {

    if (clearSchedules || existingSchedules == null || existingSchedules.isEmpty()) {
      return "Schedule 1";
    }

    int maxNumber = extractMaxNumber(existingSchedules, Schedule::getName);

    return "Schedule " + (maxNumber + 1);
  }

  /**
   * Generate the next schedule order. If clearSchedules is true, returns 1. Otherwise, returns the
   * next sequential order (max order + 1).
   *
   * @param existingSchedules List of existing schedules
   * @param clearSchedules Whether schedules are being cleared
   * @return Next schedule order
   */
  private Integer generateScheduleOrder(List<Schedule> existingSchedules, boolean clearSchedules) {

    if (clearSchedules || existingSchedules == null || existingSchedules.isEmpty()) {
      return 1;
    }

    return existingSchedules.size() + 1;
  }

  /**
   * Utility to extract the highest integer value from a list of schedules. The extractor function
   * decides *what* to parse (e.g., name).
   */
  private int extractMaxNumber(List<Schedule> schedules, Function<Schedule, String> extractor) {
    int maxNumber = 0;
    if (schedules == null) return 0;

    for (Schedule schedule : schedules) {
      String value = extractor.apply(schedule);
      if (value == null) continue;

      try {
        // Remove prefix if needed
        if (value.startsWith("Schedule ")) {
          value = value.substring("Schedule ".length()).trim();
        }

        int number = Integer.parseInt(value);
        maxNumber = Math.max(maxNumber, number);
      } catch (NumberFormatException ignored) {
        // Ignore invalid values exactly like your original code
      }
    }

    return maxNumber;
  }

  /**
   * Create a new CampaignInventorySchedules entity with basic structure.
   *
   * @param campaign Campaign entity
   * @param inventory Inventory entity
   * @return New CampaignInventorySchedules entity
   */
  private CampaignInventorySchedules createNewScheduleEntity(
      Campaign campaign, Inventory inventory) {
    CampaignInventorySchedules schedule = new CampaignInventorySchedules();
    schedule.setId(campaign.getId() + "_" + inventory.getMediaOwnerId() + "_" + inventory.getId());
    schedule.setCampaignId(campaign.getId());
    schedule.setInventoryId(inventory.getId());
    schedule.setMediaOwnerId(inventory.getMediaOwnerId());
    return schedule;
  }

  /**
   * Create a Schedule from the request DTO, computing backend-managed fields from inventory.
   *
   * @param scheduleDTO Schedule DTO from request
   * @param inventory Inventory entity to derive fields from
   * @param scheduleName Schedule Name
   * @param scheduleOrder Schedule Order
   * @return Created Schedule entity
   */
  private Schedule createScheduleFromRequest(
      BulkSchedulesRequestDTO.ScheduleDTO scheduleDTO,
      Inventory inventory,
      String scheduleName,
      Integer scheduleOrder,
      Campaign.Goals.GoalType goalType) {

    // Convert scheduleDays from String to Weekday enum
    List<Schedule.Weekday> scheduleDays =
        scheduleDTO.getScheduleDays() != null
            ? scheduleDTO.getScheduleDays().stream()
                .map(day -> Schedule.Weekday.valueOf(day.toUpperCase()))
                .collect(Collectors.toList())
            : Collections.emptyList();

    // Compute backend-managed fields from inventory
    long spotsPerHour = getLoopsPerHour(inventory);
    long adPlays = calculateAdPlays(spotsPerHour, scheduleDTO.getBookingMatrix());
    // Calculate plannedSot: (adPlays * spotDuration) / 3600
    double plannedSot = calculatePlannedSotFromAdPlays(adPlays, getSpotDuration(inventory));
    // Calculate totalSot: total operating hours for schedule dates and schedule days
    double totalSot =
        calculateTotalSotFromCampaignDates(
            scheduleDTO.getStartDate(), scheduleDTO.getEndDate(), scheduleDays, inventory);

    // Convert discount DTO to domain object
    Schedule.Discount discount = null;
    if (scheduleDTO.getDiscount() != null) {
      discount =
          Schedule.Discount.builder()
              .valueType(
                  scheduleDTO.getDiscount().getValueType() != null
                      ? DiscountValueType.valueOf(
                          scheduleDTO.getDiscount().getValueType().toUpperCase())
                      : null)
              .value(scheduleDTO.getDiscount().getValue())
              .build();
    }

    // Calculate basePrice from inventory pricing, driven by campaign goal type
    int scheduleDurationDays =
        (int) DAYS.between(scheduleDTO.getStartDate(), scheduleDTO.getEndDate());
    Double basePrice =
        calculateScheduleBasePriceForSchedule(
            adPlays, null, inventory, goalType, scheduleDurationDays);

    return Schedule.builder()
        .name(scheduleName)
        .startDate(scheduleDTO.getStartDate())
        .endDate(scheduleDTO.getEndDate())
        .scheduleDays(scheduleDays)
        .type(getScheduleType(scheduleDTO.getBookingMatrix(), inventory))
        .bookingMatrix(scheduleDTO.getBookingMatrix())
        .duration(getSpotDuration(inventory))
        .spotsPerLoop(1L) // default 1 spot per loop
        .spotsPerHour(spotsPerHour) // spotsPerHour will always in multiplication on loops per hr
        .adPlays(adPlays)
        .plannedSot(plannedSot)
        .totalSot(totalSot)
        .order(scheduleOrder)
        .basePrice(basePrice)
        .discount(discount)
        .bonusType(scheduleDTO.getBonusType())
        .build();
  }

  public static Schedule.Type getScheduleType(
      Map<String, List<Integer>> bookingMatrix, Inventory inventory) {

    List<Integer> availableHours = getAvailableHours(inventory);

    // Normalize to sets for comparison
    Set<Integer> availableSet = new HashSet<>(availableHours);

    for (Map.Entry<String, List<Integer>> entry : bookingMatrix.entrySet()) {
      List<Integer> bookedHours = entry.getValue();
      Set<Integer> bookedSet = new HashSet<>(bookedHours);

      // Compare
      if (!bookedSet.equals(availableSet)) {
        return Schedule.Type.DAYPART; // hours mismatch → it's a day part schedule
      }
    }

    return Schedule.Type.LOOP; // all dates match full available hours
  }

  /**
   * Calculate SOV (Share of Voice) percentage from plannedSot and totalSot. Formula: (plannedSot /
   * totalSot) * 100
   *
   * @param totalSot Total SOT (Share of Time) in hours
   * @param plannedSot Planned SOT (Share of Time) in hours
   * @return Calculated SOV percentage, or null if calculation cannot be performed
   */
  public static Double calculateSov(Double totalSot, Double plannedSot) {
    if (plannedSot == null || totalSot == null || totalSot == 0.0) {
      return 0.0;
    }
    try {
      return (plannedSot / totalSot) * 100;
    } catch (Exception e) {
      return 0.0;
    }
  }

  /**
   * Calculate SOV (Share of Voice) for a single schedule, aware of inventory classification.
   *
   * <p>Classic/transit inventory is always 100% (there is no competing-advertiser slot concept).
   * Digital inventory's SOV is the share of the screen's loop capacity this schedule has actually
   * booked: {@code scheduleSpotsPerLoop / inventoryMaxSpotsPerLoop * 100} — 100% only when every
   * slot on the screen is booked, matching the QA requirement exactly. Falls back to the legacy
   * time-ratio formula when classification or spot-capacity data isn't available (e.g. legacy
   * records), so older data doesn't suddenly show a null/zero SOV.
   */
  public static Double calculateInventorySov(
      String classification,
      Long scheduleSpotsPerLoop,
      Integer inventoryMaxSpotsPerLoop,
      Double totalSot,
      Double plannedSot) {
    if (classification != null && !"Digital".equalsIgnoreCase(classification)) {
      return 100.0;
    }
    if ("Digital".equalsIgnoreCase(classification)
        && scheduleSpotsPerLoop != null
        && inventoryMaxSpotsPerLoop != null
        && inventoryMaxSpotsPerLoop > 0) {
      return Math.min(
          (scheduleSpotsPerLoop.doubleValue() / inventoryMaxSpotsPerLoop) * 100.0, 100.0);
    }
    return calculateSov(totalSot, plannedSot);
  }

  /**
   * The screen's total loop capacity (max slots per loop), or null when not a digital inventory.
   */
  public static Integer getInventoryMaxSpotsPerLoop(Inventory inventory) {
    return inventory != null && inventory.getDigitalFields() != null
        ? inventory.getDigitalFields().getSpotsPerLoop()
        : null;
  }

  /**
   * plannedSot-weighted average of per-schedule SOV values. Weighting by planned airtime means an
   * all-classic or all-digital input reduces to the same number every schedule already has, and a
   * mixed set blends proportionally to how much airtime each schedule represents. Returns 0.0 when
   * there's no weight to average over (mirrors {@link #calculateSov}'s zero-data behavior).
   */
  public static Double calculateWeightedSov(List<Double> sovValues, List<Double> weights) {
    double weightedSum = 0.0;
    double totalWeight = 0.0;
    for (int i = 0; i < sovValues.size(); i++) {
      Double sov = sovValues.get(i);
      Double weight = weights.get(i);
      if (sov != null && weight != null && weight > 0) {
        weightedSum += sov * weight;
        totalWeight += weight;
      }
    }
    return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
  }

  /**
   * Prepares forecast data for a single campaign inventory schedule. This method calculates all
   * forecast metrics for a given CampaignInventorySchedules including estimated ad plays, planned
   * SOT, total SOT, SOV percentage.
   *
   * <p>The method:
   *
   * <ol>
   *   <li>Calculates total SOT by summing totalSot from all schedules
   *   <li>Calculates planned SOT by summing plannedSot from all schedules
   *   <li>Calculates SOV as (plannedSot / totalSot) * 100
   *   <li>Calculates estimated ad plays by summing adPlays from all schedules
   * </ol>
   *
   * @param campaignInventorySchedule The CampaignInventorySchedules to prepare forecast for
   * @return CampaignInventorySchedulesForecastDTO containing all forecast metrics for the schedule
   */
  public CampaignInventorySchedulesForecastDTO
      prepareInventoryForecastForCampaignInventorySchedules(
          CampaignInventorySchedules campaignInventorySchedule) {
    return prepareInventoryForecastForCampaignInventorySchedules(campaignInventorySchedule, null);
  }

  /**
   * Overload that accepts a pre-loaded schedule map. When non-null, schedules are resolved from the
   * map (no DB call). When null, schedules are fetched via {@code scheduleRepository}. Fetches the
   * inventory itself (one extra DB call) to compute a classification-aware SOV — callers that
   * already have the {@link Inventory} loaded should use the 3-arg overload instead.
   */
  public CampaignInventorySchedulesForecastDTO
      prepareInventoryForecastForCampaignInventorySchedules(
          CampaignInventorySchedules campaignInventorySchedule, Map<String, Schedule> scheduleMap) {
    Inventory inventory = inventoryService.getById(campaignInventorySchedule.getInventoryId());
    return prepareInventoryForecastForCampaignInventorySchedules(
        campaignInventorySchedule, scheduleMap, inventory);
  }

  /**
   * Overload that accepts a pre-loaded schedule map and the schedule's inventory (no extra DB call)
   * — use this when the caller already has the {@link Inventory} in hand.
   */
  public CampaignInventorySchedulesForecastDTO
      prepareInventoryForecastForCampaignInventorySchedules(
          CampaignInventorySchedules campaignInventorySchedule,
          Map<String, Schedule> scheduleMap,
          Inventory inventory) {
    List<String> scheduleIds = campaignInventorySchedule.getScheduleIds();
    List<Schedule> schedules = resolveSchedules(scheduleIds, scheduleMap);

    Double totalSot = calculateTotalSot(schedules);
    Double plannedSot = calculatePlannedSot(schedules);

    String classification = inventory != null ? inventory.getClassification() : null;
    Integer maxSpotsPerLoop = getInventoryMaxSpotsPerLoop(inventory);
    Double sov =
        calculateWeightedSov(
            schedules.stream()
                .map(
                    s ->
                        calculateInventorySov(
                            classification,
                            s.getSpotsPerLoop(),
                            maxSpotsPerLoop,
                            s.getTotalSot(),
                            s.getPlannedSot()))
                .toList(),
            schedules.stream().map(Schedule::getPlannedSot).toList());
    return CampaignInventorySchedulesForecastDTO.builder()
        .estimatedAdPlays(calculateEstimatedAdPlays(schedules))
        .totalSot(totalSot)
        .plannedSot(plannedSot)
        .sov(sov)
        .build();
  }

  /**
   * Resolves Schedule entities from IDs. When scheduleMap is non-null, looks up from map; otherwise
   * fetches from repository.
   */
  private List<Schedule> resolveSchedules(
      List<String> scheduleIds, Map<String, Schedule> scheduleMap) {
    if (scheduleIds == null || scheduleIds.isEmpty()) {
      return Collections.emptyList();
    }
    if (scheduleMap != null && !scheduleMap.isEmpty()) {
      return scheduleIds.stream().map(scheduleMap::get).filter(Objects::nonNull).toList();
    }
    return scheduleRepository.findAllById(scheduleIds);
  }

  /**
   * Calculates the total estimated ad plays from a list of schedules. This method sums up the
   * adPlays value from each schedule in the provided list.
   *
   * <p>Formula: Sum of all schedule.adPlays values
   *
   * @param schedules List of Schedule objects to calculate ad plays from
   * @return Total estimated ad plays as Long, or null if schedules list is null or empty
   */
  public static Long calculateEstimatedAdPlays(List<Schedule> schedules) {
    if (schedules == null || schedules.isEmpty()) {
      return null;
    }
    return schedules.stream().mapToLong(s -> s.getAdPlays() != null ? s.getAdPlays() : 0L).sum();
  }

  /**
   * Calculates the total planned Share of Time (SOT) from a list of schedules. Planned SOT
   * represents the sum of spotDuration * total booked slots for all schedules, typically measured
   * in hours.
   *
   * <p>Formula: Sum of all schedule.plannedSot values
   *
   * @param schedules List of Schedule objects to calculate planned SOT from
   * @return Total planned SOT as Double in hours, or null if schedules list is null or empty
   */
  public static Double calculatePlannedSot(List<Schedule> schedules) {
    if (schedules == null || schedules.isEmpty()) {
      return null;
    }
    return schedules.stream()
        .mapToDouble(s -> s.getPlannedSot() != null ? s.getPlannedSot() : 0.0)
        .sum();
  }

  /**
   * Calculates the total Share of Time (SOT) from a list of schedules. Total SOT represents the sum
   * of availableHours * campaign duration days for all schedules, typically measured in hours.
   *
   * <p>Formula: Sum of all schedule.totalSot values
   *
   * @param schedules List of Schedule objects to calculate total SOT from
   * @return Total SOT as Double in hours, or null if schedules list is null or empty
   */
  public static Double calculateTotalSot(List<Schedule> schedules) {
    if (schedules == null || schedules.isEmpty()) {
      return null;
    }
    return schedules.stream()
        .mapToDouble(s -> s.getTotalSot() != null ? s.getTotalSot() : 0.0)
        .sum();
  }

  private void validateDateFormat(String dateKey) {
    try {
      LocalDate.parse(dateKey, DATE_FORMATTER);
    } catch (Exception e) {
      log.error("Invalid date format in bookingMatrix: {}.", dateKey);
      throw new InvalidDateException("Invalid Date Format, date Format should be : yyyy-MM-dd");
    }
  }

  /**
   * Edit a schedule by its ID. Updates the schedule with new data and recalculates computed fields.
   *
   * @param campaignId Campaign ID
   * @param scheduleId Schedule ID to edit
   * @param request Schedule update request
   */
  public void editScheduleById(
      String campaignId, String scheduleId, EditScheduleRequestDTO request) {
    log.info("Editing schedule with ID: {} for campaignId: {}", scheduleId, campaignId);

    // Validate bookingMatrix date format if provided
    if (request.getBookingMatrix() != null && !request.getBookingMatrix().isEmpty()) {
      request.getBookingMatrix().keySet().forEach(this::validateDateFormat);
    }

    // Find the schedule - fail fast if not found
    Schedule existingSchedule =
        scheduleRepository
            .findById(scheduleId)
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

    // Find which CampaignInventorySchedules contains this schedule
    List<CampaignInventorySchedules> allSchedules =
        inventorySchedulesRepository.findByCampaignId(campaignId);
    CampaignInventorySchedules campaignSchedule =
        allSchedules.stream()
            .filter(s -> s.getScheduleIds() != null && s.getScheduleIds().contains(scheduleId))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Schedule " + scheduleId + " not found in campaign " + campaignId));

    // Get inventory and campaign for calculations
    Inventory inventory = inventoryService.getById(campaignSchedule.getInventoryId());
    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);

    // Convert scheduleDays from String to Weekday enum
    List<Schedule.Weekday> scheduleDays = convertScheduleDaysToEnum(request.getScheduleDays());

    // Calculate computed fields
    long spotsPerHour =
        request.getSpotsPerHour() != null ? request.getSpotsPerHour() : getLoopsPerHour(inventory);
    long adPlays = calculateAdPlays(spotsPerHour, request.getBookingMatrix());
    long spotsPerLoop = request.getSpotsPerLoop() != null ? request.getSpotsPerLoop() : 1L;
    long duration =
        request.getDuration() != null ? request.getDuration() : getSpotDuration(inventory);
    // Calculate plannedSot: (adPlays * spotDuration) / 3600
    double plannedSot = calculatePlannedSotFromAdPlays(adPlays, duration);

    // Check if anything affecting impressions/reach changed (need to recalculate). spotsPerHour and
    // spotsPerLoop drive the Measure payload too, so a change in either must re-trigger enrichment
    // —
    // otherwise stale impressions/reach (and the basePrice derived from them) would be persisted.
    boolean needsReachAndFrequencyUpdate =
        !Objects.equals(existingSchedule.getStartDate(), request.getStartDate())
            || !Objects.equals(existingSchedule.getEndDate(), request.getEndDate())
            || !Objects.equals(existingSchedule.getBookingMatrix(), request.getBookingMatrix())
            || !Objects.equals(existingSchedule.getSpotsPerHour(), spotsPerHour)
            || !Objects.equals(existingSchedule.getSpotsPerLoop(), spotsPerLoop);

    // Update schedule fields
    existingSchedule.setName(request.getName());
    existingSchedule.setStartDate(request.getStartDate());
    existingSchedule.setEndDate(request.getEndDate());
    existingSchedule.setScheduleDays(scheduleDays);
    existingSchedule.setBookingMatrix(request.getBookingMatrix());
    existingSchedule.setType(
        request.getBookingMatrix() != null
            ? getScheduleType(request.getBookingMatrix(), inventory)
            : Schedule.Type.LOOP);
    existingSchedule.setDuration(duration);
    existingSchedule.setSpotsPerLoop(spotsPerLoop);
    existingSchedule.setSpotsPerHour(spotsPerHour);
    existingSchedule.setAdPlays(adPlays);
    existingSchedule.setPlannedSot(plannedSot);

    // Use unified approach to enrich schedule with reach and frequency data if dates/bookingMatrix
    // changed
    if (needsReachAndFrequencyUpdate) {
      String referenceId =
          inventory.getReferenceId() != null ? inventory.getReferenceId() : inventory.getId();
      Map<String, Schedule> scheduleMap = new HashMap<>();
      scheduleMap.put(referenceId, existingSchedule);
      Map<String, Inventory> inventoryMap = Map.of(referenceId, inventory);

      enrichSchedulesWithReachAndFrequency(scheduleMap, inventoryMap, campaign);
    }

    // Recalculate basePrice with updated impressions, driven by campaign goal type
    int scheduleDurationDays =
        (int) DAYS.between(existingSchedule.getStartDate(), existingSchedule.getEndDate());
    Double recalculatedBasePrice =
        calculateScheduleBasePriceForSchedule(
            adPlays,
            existingSchedule.getImpressions(),
            inventory,
            campaign.getGoals() != null ? campaign.getGoals().getGoalType() : null,
            scheduleDurationDays);
    existingSchedule.setBasePrice(recalculatedBasePrice);

    // Save the updated schedule
    scheduleRepository.save(existingSchedule);

    // Get all schedules for the CampaignInventorySchedules
    List<String> allScheduleIds = campaignSchedule.getScheduleIds();
    List<Schedule> allSchedulesForCampaignSchedule =
        (allScheduleIds != null && !allScheduleIds.isEmpty())
            ? scheduleRepository.findAllById(allScheduleIds)
            : Collections.emptyList();

    // Reset history and approvals when schedule is edited
    resetHistoryAndApprovals(
        campaignSchedule, allSchedulesForCampaignSchedule, inventory, campaign);

    // Save the updated CampaignInventorySchedules
    inventorySchedulesRepository.save(campaignSchedule);

    // Evict cache for the affected inventory
    scheduleCacheEvictor.evict(campaignId, campaignSchedule.getInventoryId());

    // Log activity
    try {
      campaignActivityService.logActivity(
          campaignId,
          CampaignActivityService.OperationType.UPDATED,
          SCHEDULES_UPDATED_COUNT.key(),
          1,
          INVENTORY_NAME.key(),
          inventory.getName());
    } catch (Exception e) {
      log.warn("Failed to log schedule edit activity: {}", e.getMessage());
    }

    log.info("Successfully edited schedule with ID: {} for campaignId: {}", scheduleId, campaignId);
  }

  /**
   * Add a new schedule for a campaign and inventory. Creates the schedule and adds it to the
   * CampaignInventorySchedules.
   *
   * @param campaignId Campaign ID
   * @param request Add schedule request containing inventoryId and schedule details
   */
  @CacheEvict(
      value = "campaignInventorySchedules",
      key = "#campaignId + '_' + #request.inventoryId")
  public void addSchedule(String campaignId, AddScheduleRequestDTO request) {
    log.info(
        "Adding new schedule for campaignId: {}, inventoryId: {}",
        campaignId,
        request.getInventoryId());

    // Validate bookingMatrix date format if provided
    if (request.getBookingMatrix() != null && !request.getBookingMatrix().isEmpty()) {
      request.getBookingMatrix().keySet().forEach(this::validateDateFormat);
    }

    // Get inventory and campaign for calculations
    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);
    Inventory inventory = inventoryService.getById(request.getInventoryId());

    // Find or create CampaignInventorySchedules for this campaign-inventory combination
    CampaignInventorySchedules campaignSchedule =
        inventorySchedulesRepository
            .findByCampaignIdAndInventoryId(campaignId, request.getInventoryId())
            .orElseGet(() -> createNewScheduleEntity(campaign, inventory));

    // Get existing schedule IDs and fetch existing schedules for name generation
    List<String> existingScheduleIds =
        campaignSchedule.getScheduleIds() != null
            ? new ArrayList<>(campaignSchedule.getScheduleIds())
            : new ArrayList<>();

    // Fetch existing schedules in a single query for name generation
    List<Schedule> existingSchedules = Collections.emptyList();
    if (!existingScheduleIds.isEmpty()) {
      existingSchedules = scheduleRepository.findAllById(existingScheduleIds);
    }

    // Get or generate schedule name
    String scheduleName = getOrGenerateScheduleName(request.getName(), existingSchedules);

    // Create new Schedule from request (without impressions/reach - will be enriched later)
    Schedule newSchedule =
        createScheduleFromAddRequest(
            request,
            inventory,
            scheduleName,
            campaign.getGoals() != null ? campaign.getGoals().getGoalType() : null);

    // Use unified approach to enrich schedule with reach and frequency data
    String referenceId =
        inventory.getReferenceId() != null ? inventory.getReferenceId() : inventory.getId();
    Map<String, Schedule> scheduleMap = new HashMap<>();
    scheduleMap.put(referenceId, newSchedule);
    Map<String, Inventory> inventoryMap = Map.of(referenceId, inventory);

    enrichSchedulesWithReachAndFrequency(scheduleMap, inventoryMap, campaign);

    Schedule enrichedSchedule = scheduleMap.get(referenceId);

    // Update basePrice with enriched impressions, driven by campaign goal type
    int scheduleDurationDays =
        enrichedSchedule.getStartDate() != null && enrichedSchedule.getEndDate() != null
            ? (int) DAYS.between(enrichedSchedule.getStartDate(), enrichedSchedule.getEndDate())
            : (int) DAYS.between(campaign.getStartDate(), campaign.getEndDate());
    enrichedSchedule.setBasePrice(
        calculateScheduleBasePriceForSchedule(
            enrichedSchedule.getAdPlays(),
            enrichedSchedule.getImpressions(),
            inventory,
            campaign.getGoals() != null ? campaign.getGoals().getGoalType() : null,
            scheduleDurationDays));

    // Save the new schedule
    Schedule savedSchedule = scheduleRepository.save(enrichedSchedule);

    // Add the new schedule ID to CampaignInventorySchedules
    existingScheduleIds.add(savedSchedule.getId());
    campaignSchedule.setScheduleIds(existingScheduleIds);

    // Get all schedules for the CampaignInventorySchedules (including the newly added one)
    List<Schedule> allSchedulesForCampaignSchedule =
        existingScheduleIds.isEmpty()
            ? Collections.emptyList()
            : scheduleRepository.findAllById(existingScheduleIds);

    // Reset history and approvals when schedule is added
    resetHistoryAndApprovals(
        campaignSchedule, allSchedulesForCampaignSchedule, inventory, campaign);

    // Save the updated CampaignInventorySchedules
    inventorySchedulesRepository.save(campaignSchedule);

    // Evict cache for the affected inventory
    scheduleCacheEvictor.evict(campaignId, request.getInventoryId());

    // Log activity
    try {
      campaignActivityService.logActivity(
          campaignId,
          CampaignActivityService.OperationType.ADDED,
          SCHEDULES_UPDATED_COUNT.key(),
          1,
          INVENTORY_NAME.key(),
          inventory.getName());
    } catch (Exception e) {
      log.warn("Failed to log schedule add activity: {}", e.getMessage());
    }

    log.info(
        "Successfully added schedule with ID: {} for campaignId: {}, inventoryId: {}",
        savedSchedule.getId(),
        campaignId,
        request.getInventoryId());
  }

  /**
   * Create a Schedule from AddScheduleRequestDTO, computing backend-managed fields from inventory.
   * Reuses logic from createScheduleFromRequest but works with AddScheduleRequestDTO.
   *
   * @param request Add schedule request DTO
   * @param inventory Inventory entity to derive fields from
   * @param scheduleName Schedule name
   * @return Created Schedule entity
   */
  private Schedule createScheduleFromAddRequest(
      AddScheduleRequestDTO request,
      Inventory inventory,
      String scheduleName,
      Campaign.Goals.GoalType goalType) {

    // Convert scheduleDays from String to Weekday enum
    List<Schedule.Weekday> scheduleDays = convertScheduleDaysToEnum(request.getScheduleDays());

    // Compute backend-managed fields from inventory
    long spotsPerHour =
        request.getSpotsPerHour() != null ? request.getSpotsPerHour() : getLoopsPerHour(inventory);
    long adPlays = calculateAdPlays(spotsPerHour, request.getBookingMatrix());
    long spotsPerLoop = request.getSpotsPerLoop() != null ? request.getSpotsPerLoop() : 1L;
    long duration =
        request.getDuration() != null ? request.getDuration() : getSpotDuration(inventory);
    // Calculate plannedSot: (adPlays * spotDuration) / 3600
    double plannedSot = calculatePlannedSotFromAdPlays(adPlays, duration);
    // Calculate totalSot: total operating hours for schedule dates and schedule days
    double totalSot =
        calculateTotalSotFromCampaignDates(
            request.getStartDate(), request.getEndDate(), scheduleDays, inventory);

    // Calculate basePrice from inventory pricing, driven by campaign goal type
    int scheduleDurationDays = (int) DAYS.between(request.getStartDate(), request.getEndDate());
    Double basePrice =
        calculateScheduleBasePriceForSchedule(
            adPlays, null, inventory, goalType, scheduleDurationDays);

    return Schedule.builder()
        .name(scheduleName)
        .startDate(request.getStartDate())
        .endDate(request.getEndDate())
        .scheduleDays(scheduleDays)
        .type(
            request.getBookingMatrix() != null
                ? getScheduleType(request.getBookingMatrix(), inventory)
                : Schedule.Type.LOOP)
        .bookingMatrix(request.getBookingMatrix())
        .duration(duration)
        .spotsPerLoop(spotsPerLoop)
        .spotsPerHour(spotsPerHour)
        .adPlays(adPlays)
        .plannedSot(plannedSot)
        .basePrice(basePrice)
        .totalSot(totalSot)
        .order(request.getOrder())
        .build();
  }

  /**
   * Delete a schedule by its ID. Removes the schedule and updates the CampaignInventorySchedules.
   *
   * @param campaignId Campaign ID
   * @param scheduleId Schedule ID to delete
   */
  public void deleteScheduleById(String campaignId, String scheduleId) {
    log.info("Deleting schedule with ID: {} for campaignId: {}", scheduleId, campaignId);

    // Find which CampaignInventorySchedules contains this schedule
    List<CampaignInventorySchedules> allSchedules =
        inventorySchedulesRepository.findByCampaignId(campaignId);
    CampaignInventorySchedules campaignSchedule =
        allSchedules.stream()
            .filter(s -> s.getScheduleIds() != null && s.getScheduleIds().contains(scheduleId))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Schedule " + scheduleId + " not found in campaign " + campaignId));

    // Remove schedule ID from the list
    List<String> updatedScheduleIds = new ArrayList<>(campaignSchedule.getScheduleIds());
    updatedScheduleIds.remove(scheduleId);
    campaignSchedule.setScheduleIds(updatedScheduleIds);

    // Get inventory and campaign for history reset
    Inventory inventory = inventoryService.getById(campaignSchedule.getInventoryId());
    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);

    // Get all remaining schedules for the CampaignInventorySchedules
    List<Schedule> remainingSchedules =
        updatedScheduleIds.isEmpty()
            ? Collections.emptyList()
            : scheduleRepository.findAllById(updatedScheduleIds);

    // Reset history and approvals when schedule is deleted
    resetHistoryAndApprovals(campaignSchedule, remainingSchedules, inventory, campaign);

    // Save the updated CampaignInventorySchedules
    inventorySchedulesRepository.save(campaignSchedule);

    // Delete the schedule
    scheduleRepository.deleteById(scheduleId);

    // Evict cache for the affected inventory
    scheduleCacheEvictor.evict(campaignId, campaignSchedule.getInventoryId());

    // Log activity
    try {
      campaignActivityService.logActivity(
          campaignId,
          CampaignActivityService.OperationType.REMOVED,
          SCHEDULES_UPDATED_COUNT.key(),
          1,
          INVENTORY_NAME.key(),
          inventory.getName());
    } catch (Exception e) {
      log.warn("Failed to log schedule delete activity: {}", e.getMessage());
    }

    log.info(
        "Successfully deleted schedule with ID: {} for campaignId: {}", scheduleId, campaignId);
  }

  /**
   * Convert schedule days from String list to Weekday enum list.
   *
   * @param scheduleDays List of weekday strings
   * @return List of Weekday enums
   */
  private List<Schedule.Weekday> convertScheduleDaysToEnum(List<String> scheduleDays) {
    if (scheduleDays == null || scheduleDays.isEmpty()) {
      return Collections.emptyList();
    }
    return scheduleDays.stream().map(day -> Schedule.Weekday.valueOf(day.toUpperCase())).toList();
  }

  /**
   * Get campaign schedule prices with filtering and pagination. Fetches approved campaign inventory
   * schedules with comprehensive pricing information. Uses optimized aggregation pipeline for
   * database-level filtering.
   *
   * @param campaignId Campaign ID (required)
   * @param filter Filter criteria (cities, inventoryTypes, mediaOwners, price range)
   * @param pageable Pagination parameters
   * @return Page of campaign schedule price responses
   */
  public Page<CampaignSchedulePriceResponseDTO> getCampaignSchedulePrices(
      String campaignId, CampaignSchedulePriceFilterDTO filter, Pageable pageable) {
    log.info(
        "Getting campaign schedule prices for campaignId: {} with filters: {}", campaignId, filter);

    // Get campaign to check companyId
    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);

    // Get user's primaryCompanyId using UserService
    final String primaryCompanyId = userService.getActingCompanyId();

    // Determine filtering logic based on companyId match
    List<CampaignInventorySchedules> filteredSchedules;
    Page<CampaignInventorySchedules> filteredSchedulesPage;

    // Full visibility only when the resolved ACTING company is the campaign's buyer company.
    // Any other membership the user holds (e.g. also a member of the buyer while switched
    // into a media-owner company) must NOT widen visibility — otherwise a dual-member user
    // switched to one media owner could read every owner's schedules and prices.
    if (primaryCompanyId != null && primaryCompanyId.equals(campaign.getCompanyId())) {
      // If user.primaryCompanyId == campaign.companyId: show all schedules for campaignId
      log.debug(
          "Primary company ID matches campaign company ID. Showing all schedules for campaignId: {}",
          campaignId);
      filteredSchedulesPage =
          inventorySchedulesRepository.findWithPriceFilters(campaignId, filter, pageable, null);
    } else {
      // If IDs don't match: filter by campaignId AND mediaOwnerId == primaryCompanyId
      log.debug(
          "Primary company ID does not match campaign company ID. Filtering by campaignId: {} and mediaOwnerId: {}",
          campaignId,
          primaryCompanyId);
      // Filter directly at database level by campaignId and mediaOwnerId == primaryCompanyId
      filteredSchedulesPage =
          inventorySchedulesRepository.findWithPriceFilters(
              campaignId, filter, pageable, primaryCompanyId);
    }

    if (filteredSchedulesPage.isEmpty()) {
      return Page.empty(pageable);
    }

    filteredSchedules = filteredSchedulesPage.getContent();

    // Get all inventory IDs from filtered results
    List<String> inventoryIds =
        filteredSchedules.stream()
            .map(CampaignInventorySchedules::getInventoryId)
            .distinct()
            .collect(Collectors.toList());

    // Fetch all inventories in a single query
    Map<String, Inventory> inventoryMap =
        inventoryService.findAllByIds(inventoryIds).stream()
            .collect(Collectors.toMap(Inventory::getId, Function.identity()));

    // Calculate campaign duration
    int duration = CampaignService.calculateDuration(campaign);

    CustomFeesContext customFeesContext =
        customFeeService.getActiveCustomFeesContextForCampaign(campaign);

    // Build schedule map once for all filtered schedules (one DB call instead of 2*N)
    List<String> allScheduleIds =
        filteredSchedules.stream()
            .filter(s -> s.getScheduleIds() != null && !s.getScheduleIds().isEmpty())
            .flatMap(s -> s.getScheduleIds().stream())
            .distinct()
            .toList();
    Map<String, Schedule> scheduleMap = new HashMap<>();
    if (!allScheduleIds.isEmpty()) {
      List<Schedule> allSchedules = scheduleRepository.findAllById(allScheduleIds);
      for (Schedule s : allSchedules) {
        if (s != null && s.getId() != null) {
          scheduleMap.put(s.getId(), s);
        }
      }
    }

    // Resolved once for the whole page — every row's conversion needs the same values, so
    // fetching/resolving them per row (as the old code did) is pure repeated work.
    String userCompanyId = userService.getIamUserContext().getCompanyId();
    boolean isCampaignCreator = isCampaignCreator(campaign, userCompanyId);
    Map<String, String> mediaOwnerNameByMediaOwnerId =
        inventoryMap.values().stream()
            .map(Inventory::getMediaOwnerId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(
                HashMap::new,
                (map, mediaOwnerId) -> {
                  try {
                    CompanyLookupResponseDTO companyDto =
                        companyService.getCompanyLookupWithCompanyId(mediaOwnerId);
                    map.put(mediaOwnerId, companyDto != null ? companyDto.getName() : null);
                  } catch (Exception e) {
                    log.warn("Failed to get media owner name for ID: {}", mediaOwnerId, e);
                  }
                },
                HashMap::putAll);

    // Convert to response DTOs
    List<CampaignSchedulePriceResponseDTO> responseDTOs =
        filteredSchedules.stream()
            .map(
                schedule -> {
                  try {
                    Inventory inventory = inventoryMap.get(schedule.getInventoryId());
                    if (inventory == null) {
                      log.warn("Inventory not found for ID: {}", schedule.getInventoryId());
                      return null;
                    }
                    return convertToPriceResponseDTO(
                        schedule,
                        inventory,
                        campaign,
                        duration,
                        customFeesContext,
                        scheduleMap,
                        userCompanyId,
                        isCampaignCreator,
                        mediaOwnerNameByMediaOwnerId);
                  } catch (Exception e) {
                    log.warn(
                        "Error converting schedule to price response DTO for inventoryId: {}",
                        schedule.getInventoryId(),
                        e);
                    return null;
                  }
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    return new PageImpl<>(responseDTOs, pageable, filteredSchedulesPage.getTotalElements());
  }

  /**
   * Get campaign price summary aggregating all schedules for a campaign.
   *
   * @param campaignId Campaign ID
   * @return CampaignPriceSummaryResponseDTO with aggregated price calculations
   */
  public CampaignPriceSummaryResponseDTO getCampaignPriceSummary(String campaignId) {
    log.info("Getting campaign price summary for campaignId: {}", campaignId);

    // Get campaign entity
    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);

    // Get user context; fall back to campaign owner view for unauthenticated callers
    String userCompanyId = resolveUserCompanyId(campaign.getCompanyId());
    boolean isCampaignCreator = isCampaignCreator(campaign, userCompanyId);

    // Get user-filtered CampaignInventorySchedules
    List<CampaignInventorySchedules> campaignInventorySchedulesList;
    if (userCompanyId.equals(campaign.getCompanyId())) {
      // Campaign creator: get all schedules
      campaignInventorySchedulesList = inventorySchedulesRepository.findByCampaignId(campaignId);
    } else {
      // Media owner: filter by mediaOwnerId
      campaignInventorySchedulesList =
          inventorySchedulesRepository.findByCampaignIdAndMediaOwnerId(campaignId, userCompanyId);
    }

    if (campaignInventorySchedulesList.isEmpty()) {
      log.warn("No campaign inventory schedules found for campaignId: {}", campaignId);
      return CampaignPriceSummaryResponseDTO.builder()
          .currentPrice(null)
          .proposedPrice(null)
          .changeInPrice(null)
          .changeInPercentage(null)
          .mediaCost(null)
          .discountedMediaCost(null)
          .standardFees(null)
          .customFees(Collections.emptyList())
          .isAllApproved(isCampaignCreator)
          .build();
    }

    CustomFeesContext customFeesContext =
        customFeeService.getActiveCustomFeesContextForCampaign(campaign);

    // Build schedule map once for all CampaignInventorySchedules (one DB call instead of N)
    List<String> allScheduleIds =
        campaignInventorySchedulesList.stream()
            .filter(s -> s.getScheduleIds() != null && !s.getScheduleIds().isEmpty())
            .flatMap(s -> s.getScheduleIds().stream())
            .distinct()
            .toList();
    Map<String, Schedule> scheduleMap = new HashMap<>();
    if (!allScheduleIds.isEmpty()) {
      List<Schedule> allSchedules = scheduleRepository.findAllById(allScheduleIds);
      for (Schedule s : allSchedules) {
        if (s != null && s.getId() != null) {
          scheduleMap.put(s.getId(), s);
        }
      }
    }

    // Aggregate values across all schedules
    double totalMediaCost = 0.0;
    double totalDiscountedMediaCost = 0.0;
    double totalCurrentPrice = 0.0;
    double totalProposedPrice = 0.0;
    // Pre-fee base cost (post-discount, before any custom fee). PL3-I4: PERCENTAGE custom fees and
    // the summary mediaCost field must be based on this, NOT the fee-inclusive media cost.
    double totalBaseCost = 0.0;
    boolean hasAnyPrice = false;

    // Collect unique displayable custom fees
    Set<CustomFee> displayableCustomFees = new HashSet<>();

    // Process each CampaignInventorySchedules
    for (CampaignInventorySchedules campaignInventorySchedule : campaignInventorySchedulesList) {
      List<String> scheduleIds = campaignInventorySchedule.getScheduleIds();
      if (scheduleIds == null || scheduleIds.isEmpty()) {
        continue;
      }

      List<Schedule> schedules = resolveSchedules(scheduleIds, scheduleMap);
      if (schedules.isEmpty()) {
        continue;
      }

      Inventory inventory;
      try {
        inventory = inventoryService.getById(campaignInventorySchedule.getInventoryId());
      } catch (Exception e) {
        log.warn(
            "Inventory not found for ID: {}, skipping",
            campaignInventorySchedule.getInventoryId(),
            e);
        continue;
      }

      // Get visible fees for this inventory
      List<CustomFee>[] fees;
      if (isCampaignCreator) {
        fees =
            getCampaignCreatorFees(inventory, campaign.getId(), userCompanyId, customFeesContext);
      } else {
        fees = getMediaOwnerFees(inventory, campaign.getId(), customFeesContext);
      }

      if (fees.length > 1) {
        if (fees[0] != null) {
          if (isCampaignCreator) {
            // For campaign creator: only show their own campaign-level hidden fees
            // (hide media owner's campaign-level hidden fees)
            Set<CustomFee> campaignHiddenCustomFees =
                fees[0].stream()
                    .filter(
                        customFee ->
                            customFee.getCampaignId() != null
                                && customFee.getCompanyId().equals(userCompanyId))
                    .collect(Collectors.toSet());
            displayableCustomFees.addAll(campaignHiddenCustomFees);
          } else {
            // For media owner: show all campaign-level hidden fees (already filtered to only media
            // owner's)
            Set<CustomFee> campaignHiddenCustomFees =
                fees[0].stream()
                    .filter(customFee -> customFee.getCampaignId() != null)
                    .collect(Collectors.toSet());
            displayableCustomFees.addAll(campaignHiddenCustomFees);
          }
        }
        if (fees[1] != null) {
          // Visible fees: add all (media owner's include-in-media-plan campaign fees + campaign
          // creator's visible fees)
          displayableCustomFees.addAll(fees[1]);
        }
      }

      // Calculate prices for each schedule
      for (Schedule schedule : schedules) {
        Double mediaCost =
            calculateMediaCost(
                schedule, inventory, campaign, userCompanyId, isCampaignCreator, customFeesContext);
        if (mediaCost == null) {
          continue;
        }

        Double discountedMediaCost = calculateDiscountedPrice(mediaCost, schedule.getDiscount());
        Double currentPrice =
            calculateCurrentPrice(
                schedule, inventory, campaign, userCompanyId, isCampaignCreator, customFeesContext);
        Double proposedPrice =
            calculateProposedPrice(
                schedule, inventory, campaign, userCompanyId, isCampaignCreator, customFeesContext);

        totalMediaCost += mediaCost;
        hasAnyPrice = true;
        if (discountedMediaCost != null) {
          totalDiscountedMediaCost += discountedMediaCost;
        }
        // PL3-I4: pre-fee base = post-discount basePrice (before custom fees fold in).
        Double baseCost = applyDiscountToPrice(schedule.getBasePrice(), schedule.getDiscount());
        if (baseCost != null) {
          totalBaseCost += baseCost;
        }
        if (currentPrice != null) {
          totalCurrentPrice += currentPrice;
        }
        if (proposedPrice != null) {
          totalProposedPrice += proposedPrice;
        }
      }
    }

    // Calculate summary values
    // PL3-I4 symptom (b): the summary mediaCost field must show the RAW pre-fee base cost
    // (post-discount), not the fee-inclusive media cost. Same value drives PERCENTAGE fees below.
    Double finalBaseCost = hasAnyPrice && totalBaseCost > 0.0 ? totalBaseCost : null;
    Double finalDiscountedMediaCost =
        hasAnyPrice && totalDiscountedMediaCost > 0.0 ? totalDiscountedMediaCost : null;
    Double finalCurrentPrice = hasAnyPrice && totalCurrentPrice > 0.0 ? totalCurrentPrice : null;
    Double finalProposedPrice = hasAnyPrice && totalProposedPrice > 0.0 ? totalProposedPrice : null;

    // Calculate StandardFees: proposedPrice - discountedMediaCost
    Double standardFees = null;
    if (finalProposedPrice != null && finalDiscountedMediaCost != null) {
      standardFees = finalProposedPrice - finalDiscountedMediaCost;
      if (standardFees < 0) {
        standardFees = 0.0;
      }
    }

    // Calculate changeInPrice and changeInPercentage
    Double changeInPrice = null;
    Double changeInPercentage = null;
    if (finalCurrentPrice != null && finalProposedPrice != null) {
      changeInPrice = finalCurrentPrice - finalProposedPrice;
      changeInPercentage = (changeInPrice / finalCurrentPrice) * 100.0;
    }

    // Convert CustomFee objects to CustomFeeResponseDTO with effectiveCustomFee calculation
    List<CustomFeeResponseDTO> customFeeResponseDTOs =
        displayableCustomFees.stream()
            .map(
                fee -> {
                  CustomFeeResponseDTO dto = mapToCustomFeeResponseDTO(fee);
                  // PL3-I4: PERCENTAGE fees (basedOn BASE_COST) must be computed against the
                  // pre-fee base cost, NOT the fee-inclusive media cost, otherwise a hidden
                  // percentage fee compounds on itself.
                  Double effectiveFee = calculateEffectiveCustomFee(fee, finalBaseCost);
                  dto.setEffectiveCustomFee(effectiveFee);
                  return dto;
                })
            .sorted(Comparator.comparing(CustomFeeResponseDTO::getName))
            .collect(Collectors.toList());

    // Check if all schedules are approved
    // For media owners, isAllApproved is always false
    // Only campaign creators can have true value based on approval status
    boolean isAllApproved = false;
    if (isCampaignCreator) {
      // Only check approval status for campaign creators
      isAllApproved = true;
      for (CampaignInventorySchedules campaignInventorySchedule : campaignInventorySchedulesList) {
        List<String> scheduleIds = campaignInventorySchedule.getScheduleIds();
        List<String> approvedScheduleIds = campaignInventorySchedule.getApprovedScheduleIds();

        // If scheduleIds is null or empty, skip this check
        if (scheduleIds == null || scheduleIds.isEmpty()) {
          continue;
        }

        // If approvedScheduleIds is null or sizes don't match, not all approved
        if (approvedScheduleIds == null || approvedScheduleIds.size() != scheduleIds.size()) {
          isAllApproved = false;
          break;
        }
      }
    }

    return CampaignPriceSummaryResponseDTO.builder()
        .currentPrice(finalCurrentPrice)
        .proposedPrice(finalProposedPrice)
        .changeInPrice(changeInPrice)
        .changeInPercentage(changeInPercentage)
        .mediaCost(finalBaseCost)
        .discountedMediaCost(finalDiscountedMediaCost)
        .standardFees(standardFees)
        .customFees(customFeeResponseDTOs)
        .isAllApproved(isAllApproved)
        .build();
  }

  /**
   * Calculate effective custom fee amount for a single custom fee based on the pre-fee base cost.
   *
   * <p>PL3-I4: PERCENTAGE fees are {@code CustomFeeBasedOn.BASE_COST} (the only supported basedOn),
   * so they must be applied to the post-discount, pre-fee base cost. Applying them to a
   * fee-inclusive figure would compound a hidden fee on itself.
   *
   * @param customFee CustomFee entity
   * @param baseCost Post-discount, pre-fee base cost to apply the fee to
   * @return Effective custom fee amount, or null if baseCost is null
   */
  private Double calculateEffectiveCustomFee(CustomFee customFee, Double baseCost) {
    if (baseCost == null || customFee == null || customFee.getValue() == null) {
      return null;
    }

    if (customFee.getType() == CustomFeeType.PERCENTAGE) {
      // Percentage: (fee.getValue() / 100.0) * baseCost
      return (customFee.getValue() / 100.0) * baseCost;
    } else if (customFee.getType() == CustomFeeType.VALUE) {
      // Value: fixed amount
      return customFee.getValue();
    }

    return null;
  }

  /**
   * Map CustomFee to CustomFeeResponseDTO.
   *
   * @param customFee CustomFee entity
   * @return CustomFeeResponseDTO
   */
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
   * Calculate base price for a schedule based on inventory pricing. This method is used when
   * creating new schedules to calculate and store basePrice. Once stored, basePrice should be used
   * directly from the schedule entity. Falls back to spot if available, else CPM when goalType is
   * null.
   *
   * @param adPlays Number of ad plays (for spot-based pricing)
   * @param impressions Number of impressions (for CPM-based pricing)
   * @param inventory Inventory entity
   * @return Base price calculated from impressions*cpm or spot*adPlays
   */
  private Double calculateScheduleBasePriceForSchedule(
      Long adPlays, Long impressions, Inventory inventory) {
    return calculateScheduleBasePriceForSchedule(adPlays, impressions, inventory, null, null);
  }

  private Double calculateScheduleBasePriceForSchedule(
      Long adPlays, Long impressions, Inventory inventory, Campaign.Goals.GoalType goalType) {
    return calculateScheduleBasePriceForSchedule(adPlays, impressions, inventory, goalType, null);
  }

  /**
   * Calculate base price for a schedule based on inventory pricing and campaign goal type.
   * IMPRESSIONS/REACH: (cpm/1000) * impressions, SOV/ADPLAYS: spot * adPlays, else spot or CPM. For
   * classic inventories with no CPM, falls back to monthly/daily/weekly flat rates when
   * durationDays is provided.
   *
   * @param adPlays Number of ad plays
   * @param impressions Number of impressions
   * @param inventory Inventory entity
   * @param goalType Campaign goal type
   * @param durationDays Campaign or schedule duration in days (for flat-rate classic fallback)
   * @return Base price, or null if required price data is unavailable
   */
  private Double calculateScheduleBasePriceForSchedule(
      Long adPlays,
      Long impressions,
      Inventory inventory,
      Campaign.Goals.GoalType goalType,
      Integer durationDays) {
    if (inventory.getPrices() == null || inventory.getPrices().isEmpty()) {
      log.warn("Inventory {} has no prices", inventory.getId());
      return null;
    }

    // Scan all price elements for the first non-null cpm and the first non-null spot
    // (they may live on different elements); null only when absent across every element.
    Double cpm = InventoryService.getCpm(inventory);
    Double spot = InventoryService.getSpotRate(inventory);

    if (goalType == Campaign.Goals.GoalType.IMPRESSIONS
        || goalType == Campaign.Goals.GoalType.REACH) {
      if (cpm != null && impressions != null) {
        return (cpm / 1000.0) * impressions;
      }
      // Flat-rate fallback: the browse filter admits monthly/daily-priced (classic) inventory for
      // every goal, so goal-specific pricing must be able to price it too — otherwise a
      // selectable inventory lands in the plan with a null base price.
      if (durationDays != null && durationDays > 0) {
        return InventoryService.estimateFlatRateCost(inventory, durationDays);
      }
      return null;
    } else if (goalType == Campaign.Goals.GoalType.SOV
        || goalType == Campaign.Goals.GoalType.ADPLAYS) {
      if (spot != null && adPlays != null) {
        return spot * adPlays;
      }
      // Flat-rate fallback — same rationale as the impressions branch above.
      if (durationDays != null && durationDays > 0) {
        return InventoryService.estimateFlatRateCost(inventory, durationDays);
      }
      return null;
    } else {
      // Default (null / OTHER / ATTRIBUTION): spot-based if adPlays > 0, else CPM
      if (spot != null && adPlays != null && adPlays > 0) {
        return spot * adPlays;
      }
      if (cpm != null && impressions != null) {
        return (cpm / 1000.0) * impressions;
      }
      // Classic inventory fallback: monthly → daily → weekly flat rate
      if (durationDays != null && durationDays > 0) {
        return InventoryService.estimateFlatRateCost(inventory, durationDays);
      }
      return null;
    }
  }

  /**
   * Apply custom fees to a price based on fee type and basedOn value.
   *
   * @param basePrice Base price to apply fees to
   * @param fees List of custom fees to apply
   * @return Price after applying all fees
   */
  private Double applyCustomFeesToPrice(Double basePrice, List<CustomFee> fees) {
    if (basePrice == null || fees == null || fees.isEmpty()) {
      return basePrice;
    }

    double result = basePrice;
    for (CustomFee fee : fees) {
      if (fee.getType() == com.mw.planner.enums.CustomFeeType.PERCENTAGE) {
        // Percentage: add percentage of basePrice
        result += (fee.getValue() / 100.0) * basePrice;
      } else if (fee.getType() == com.mw.planner.enums.CustomFeeType.VALUE) {
        // Value: add fixed amount
        result += fee.getValue();
      }
    }
    return result;
  }

  /**
   * Calculate prices for media owner using pre-loaded context. Context must be built once per
   * campaign.
   *
   * @param schedule Schedule entity
   * @param inventory Inventory entity
   * @param campaignId Campaign ID
   * @param context Pre-loaded custom fees context for the campaign
   * @return Array with [currentRate, proposedRate]
   */
  private Double[] calculatePricesForMediaOwner(
      Schedule schedule, Inventory inventory, String campaignId, CustomFeesContext context) {
    Double basePrice = schedule.getBasePrice();
    if (basePrice == null) {
      return new Double[] {null, null};
    }

    String mediaOwnerCompanyId = inventory.getMediaOwnerId();
    if (mediaOwnerCompanyId == null) {
      log.warn("Inventory {} has no mediaOwnerId", inventory.getId());
      return new Double[] {basePrice, basePrice};
    }

    List<CustomFee>[] fees = getMediaOwnerFees(inventory, campaignId, context);
    List<CustomFee> allHiddenFees = fees[0];
    List<CustomFee> allVisibleFees = fees[1];

    // Calculate currentRate: basePrice + all fees (hidden and visible)
    Double currentRate = basePrice;
    currentRate = applyCustomFeesToPrice(currentRate, allHiddenFees);
    currentRate = applyCustomFeesToPrice(currentRate, allVisibleFees);

    // Calculate proposedRate: basePrice + hidden fees + discount + visible fees
    Double proposedRate = basePrice;
    proposedRate = applyCustomFeesToPrice(proposedRate, allHiddenFees);
    // Apply discount after hidden fees, before visible fees
    proposedRate = applyDiscountToPrice(proposedRate, schedule.getDiscount());
    // Apply visible fees after discount
    proposedRate = applyCustomFeesToPrice(proposedRate, allVisibleFees);

    return new Double[] {currentRate, proposedRate};
  }

  /**
   * Calculate prices for campaign creator. Same as media owner plus campaign creator company fees.
   * Context must be built once per campaign.
   *
   * @param schedule Schedule entity
   * @param inventory Inventory entity
   * @param campaignId Campaign ID
   * @param campaignCreatorCompanyId Campaign creator company ID
   * @return Array with [currentRate, proposedRate]
   */
  private Double[] calculatePricesForCampaignCreator(
      Schedule schedule,
      Inventory inventory,
      String campaignId,
      String campaignCreatorCompanyId,
      CustomFeesContext context) {
    Double basePrice = schedule.getBasePrice();
    if (basePrice == null) {
      return new Double[] {null, null};
    }

    String mediaOwnerCompanyId = inventory.getMediaOwnerId();
    if (mediaOwnerCompanyId == null) {
      log.warn("Inventory {} has no mediaOwnerId", inventory.getId());
      return new Double[] {basePrice, basePrice};
    }

    List<CustomFee>[] fees =
        getCampaignCreatorFees(inventory, campaignId, campaignCreatorCompanyId, context);
    List<CustomFee> allHiddenFees = fees[0];
    List<CustomFee> allVisibleFees = fees[1];

    // Calculate currentRate: basePrice + all fees (hidden and visible) from media owner, campaign,
    // and campaign creator
    Double currentRate = basePrice;
    currentRate = applyCustomFeesToPrice(currentRate, allHiddenFees);
    currentRate = applyCustomFeesToPrice(currentRate, allVisibleFees);

    // Calculate proposedRate: basePrice + hidden fees (all) + discount + visible fees (all)
    Double proposedRate = basePrice;
    proposedRate = applyCustomFeesToPrice(proposedRate, allHiddenFees);
    proposedRate = applyDiscountToPrice(proposedRate, schedule.getDiscount());
    proposedRate = applyCustomFeesToPrice(proposedRate, allVisibleFees);

    return new Double[] {currentRate, proposedRate};
  }

  /**
   * Apply discount to a price based on schedule discount.
   *
   * @param price Price to apply discount to
   * @param discount Discount information
   * @return Price after discount
   */
  private Double applyDiscountToPrice(Double price, Schedule.Discount discount) {
    if (price == null || discount == null || discount.getValue() == null) {
      return price;
    }

    try {
      double discountValue = Double.parseDouble(discount.getValue());
      if (discount.getValueType() == DiscountValueType.PERCENTAGE) {
        // Percentage discount: price * (1 - discount/100)
        return price * (1 - discountValue / 100.0);
      } else {
        // Value discount: price - discount
        return Math.max(0.0, price - discountValue);
      }
    } catch (NumberFormatException e) {
      log.warn("Invalid discount value: {}", discount.getValue());
      return price;
    }
  }

  /**
   * Get custom fees for media owner (hidden and visible) from pre-loaded context. Context must be
   * built once per campaign and passed through; returns empty lists when context is null.
   *
   * @param inventory Inventory entity
   * @param campaignId Campaign ID
   * @param context Pre-loaded custom fees context for the campaign
   * @return Array with [hiddenFees, visibleFees]
   */
  @SuppressWarnings("unchecked")
  private List<CustomFee>[] getMediaOwnerFees(
      Inventory inventory, String campaignId, CustomFeesContext context) {
    String mediaOwnerCompanyId = inventory.getMediaOwnerId();
    if (mediaOwnerCompanyId == null || context == null) {
      return new List[] {new ArrayList<>(), new ArrayList<>()};
    }
    List<CustomFee> hiddenFees = new ArrayList<>();
    List<CustomFee> visibleFees = new ArrayList<>();
    CompanyCustomFees companyFees =
        context.getCompanyFeesByCompanyId() != null
            ? context.getCompanyFeesByCompanyId().get(mediaOwnerCompanyId)
            : null;
    CompanyCustomFees campaignFees =
        context.getCampaignFeesByCompanyId() != null
            ? context.getCampaignFeesByCompanyId().get(mediaOwnerCompanyId)
            : null;
    if (companyFees != null) {
      if (companyFees.getHidden() != null) hiddenFees.addAll(companyFees.getHidden());
      if (companyFees.getVisible() != null) visibleFees.addAll(companyFees.getVisible());
    }
    if (campaignFees != null) {
      if (campaignFees.getHidden() != null) hiddenFees.addAll(campaignFees.getHidden());
      if (campaignFees.getVisible() != null) visibleFees.addAll(campaignFees.getVisible());
    }
    return new List[] {hiddenFees, visibleFees};
  }

  /**
   * Get custom fees for campaign creator (media owner fees plus creator company/campaign fees) from
   * pre-loaded context. Context must be built once per campaign; returns only media owner fees when
   * context is null.
   *
   * @param inventory Inventory entity
   * @param campaignId Campaign ID
   * @param campaignCreatorCompanyId Campaign creator company ID
   * @param context Pre-loaded custom fees context for the campaign
   * @return Array with [hiddenFees, visibleFees]
   */
  @SuppressWarnings("unchecked")
  private List<CustomFee>[] getCampaignCreatorFees(
      Inventory inventory,
      String campaignId,
      String campaignCreatorCompanyId,
      CustomFeesContext context) {
    List<CustomFee>[] mediaOwnerFees = getMediaOwnerFees(inventory, campaignId, context);
    // Dedupe while merging: when the campaign creator's company is also the inventory's media
    // owner, getMediaOwnerFees already returned that company's campaign/company fees, and the
    // creator branch below would add the same fee a second time -> the fee gets applied twice in
    // the price calculation (PL3-I4). LinkedHashSet keeps insertion order and drops duplicates via
    // CustomFee.equals/hashCode.
    Set<CustomFee> hiddenFees = new LinkedHashSet<>(mediaOwnerFees[0]);
    Set<CustomFee> visibleFees = new LinkedHashSet<>(mediaOwnerFees[1]);
    if (context != null) {
      CompanyCustomFees creatorCompanyFees =
          context.getCompanyFeesByCompanyId() != null
              ? context.getCompanyFeesByCompanyId().get(campaignCreatorCompanyId)
              : null;
      CompanyCustomFees creatorCampaignFees =
          context.getCampaignFeesByCompanyId() != null
              ? context.getCampaignFeesByCompanyId().get(campaignCreatorCompanyId)
              : null;
      if (creatorCompanyFees != null) {
        if (creatorCompanyFees.getHidden() != null)
          hiddenFees.addAll(creatorCompanyFees.getHidden());
        if (creatorCompanyFees.getVisible() != null)
          visibleFees.addAll(creatorCompanyFees.getVisible());
      }
      if (creatorCampaignFees != null) {
        if (creatorCampaignFees.getHidden() != null)
          hiddenFees.addAll(creatorCampaignFees.getHidden());
        if (creatorCampaignFees.getVisible() != null)
          visibleFees.addAll(creatorCampaignFees.getVisible());
      }
    }
    return new List[] {new ArrayList<>(hiddenFees), new ArrayList<>(visibleFees)};
  }

  /** Calculate media cost for a schedule (basePrice + hidden fees) using pre-loaded context. */
  private Double calculateMediaCost(
      Schedule schedule,
      Inventory inventory,
      Campaign campaign,
      String userCompanyId,
      boolean isCampaignCreator,
      CustomFeesContext context) {
    Double basePrice = schedule.getBasePrice();
    if (basePrice == null) {
      return null;
    }

    List<CustomFee>[] fees;
    if (isCampaignCreator) {
      fees = getCampaignCreatorFees(inventory, campaign.getId(), userCompanyId, context);
    } else {
      fees = getMediaOwnerFees(inventory, campaign.getId(), context);
    }

    return applyCustomFeesToPrice(basePrice, fees[0]); // fees[0] is hidden fees
  }

  /**
   * Calculate discounted price (mediaCost after applying discount).
   *
   * @param mediaCost Media cost (basePrice + hidden fees)
   * @param discount Discount information
   * @return Discounted price, or mediaCost if no discount
   */
  private Double calculateDiscountedPrice(Double mediaCost, Schedule.Discount discount) {
    if (mediaCost == null) {
      return null;
    }
    return applyDiscountToPrice(mediaCost, discount);
  }

  /** Calculate current price for a schedule (mediaCost + visible fees) using pre-loaded context. */
  private Double calculateCurrentPrice(
      Schedule schedule,
      Inventory inventory,
      Campaign campaign,
      String userCompanyId,
      boolean isCampaignCreator,
      CustomFeesContext context) {
    Double mediaCost =
        calculateMediaCost(
            schedule, inventory, campaign, userCompanyId, isCampaignCreator, context);
    if (mediaCost == null) {
      return null;
    }

    List<CustomFee>[] fees;
    if (isCampaignCreator) {
      fees = getCampaignCreatorFees(inventory, campaign.getId(), userCompanyId, context);
    } else {
      fees = getMediaOwnerFees(inventory, campaign.getId(), context);
    }

    return applyCustomFeesToPrice(mediaCost, fees[1]); // fees[1] is visible fees
  }

  /**
   * Calculate proposed price for a schedule (discountedPrice + visible fees) using pre-loaded
   * context.
   */
  private Double calculateProposedPrice(
      Schedule schedule,
      Inventory inventory,
      Campaign campaign,
      String userCompanyId,
      boolean isCampaignCreator,
      CustomFeesContext context) {
    Double mediaCost =
        calculateMediaCost(
            schedule, inventory, campaign, userCompanyId, isCampaignCreator, context);
    if (mediaCost == null) {
      return null;
    }

    Double discountedPrice = calculateDiscountedPrice(mediaCost, schedule.getDiscount());

    List<CustomFee>[] fees;
    if (isCampaignCreator) {
      fees = getCampaignCreatorFees(inventory, campaign.getId(), userCompanyId, context);
    } else {
      fees = getMediaOwnerFees(inventory, campaign.getId(), context);
    }

    return applyCustomFeesToPrice(discountedPrice, fees[1]); // fees[1] is visible fees
  }

  private Double calculateCampaignInventorySchedulesCurrentPrice(
      CampaignInventorySchedules campaignInventorySchedules,
      Inventory inventory,
      Campaign campaign,
      String userCompanyId,
      CustomFeesContext context) {
    return calculateCampaignInventorySchedulesCurrentPrice(
        campaignInventorySchedules, inventory, campaign, userCompanyId, context, null);
  }

  private Double calculateCampaignInventorySchedulesCurrentPrice(
      CampaignInventorySchedules campaignInventorySchedules,
      Inventory inventory,
      Campaign campaign,
      String userCompanyId,
      CustomFeesContext context,
      Map<String, Schedule> scheduleMap) {
    List<String> scheduleIds = campaignInventorySchedules.getScheduleIds();
    if (scheduleIds == null || scheduleIds.isEmpty()) {
      return null;
    }

    List<Schedule> schedules = resolveSchedules(scheduleIds, scheduleMap);
    if (schedules.isEmpty()) {
      return null;
    }

    boolean isCampaignCreator = isCampaignCreator(campaign, userCompanyId);
    double totalCurrentPrice = 0.0;
    boolean hasAnyPrice = false;

    for (Schedule schedule : schedules) {
      Double currentPrice =
          calculateCurrentPrice(
              schedule, inventory, campaign, userCompanyId, isCampaignCreator, context);
      if (currentPrice != null) {
        totalCurrentPrice += currentPrice;
        hasAnyPrice = true;
      }
    }

    return hasAnyPrice ? totalCurrentPrice : null;
  }

  /**
   * Calculate total proposed price using pre-loaded custom fees context to avoid repeated
   * repository calls. Call when context was built once for the campaign (e.g. from cost split or
   * forecast).
   */
  public Double calculateCampaignInventorySchedulesProposedPrice(
      CampaignInventorySchedules campaignInventorySchedules,
      Inventory inventory,
      Campaign campaign,
      String userCompanyId,
      CustomFeesContext context) {
    return calculateCampaignInventorySchedulesProposedPrice(
        campaignInventorySchedules, inventory, campaign, userCompanyId, context, null);
  }

  /**
   * Overload that accepts a pre-loaded schedule map. When non-null, schedules are resolved from the
   * map (no DB call). When null, schedules are fetched via repository.
   */
  public Double calculateCampaignInventorySchedulesProposedPrice(
      CampaignInventorySchedules campaignInventorySchedules,
      Inventory inventory,
      Campaign campaign,
      String userCompanyId,
      CustomFeesContext context,
      Map<String, Schedule> scheduleMap) {
    List<String> scheduleIds = campaignInventorySchedules.getScheduleIds();
    if (scheduleIds == null || scheduleIds.isEmpty()) {
      return null;
    }

    List<Schedule> schedules = resolveSchedules(scheduleIds, scheduleMap);
    if (schedules.isEmpty()) {
      return null;
    }

    boolean isCampaignCreator = isCampaignCreator(campaign, userCompanyId);
    double totalProposedPrice = 0.0;
    boolean hasAnyPrice = false;

    for (Schedule schedule : schedules) {
      Double proposedPrice =
          calculateProposedPrice(
              schedule, inventory, campaign, userCompanyId, isCampaignCreator, context);
      if (proposedPrice != null) {
        totalProposedPrice += proposedPrice;
        hasAnyPrice = true;
      }
    }

    return hasAnyPrice ? totalProposedPrice : null;
  }

  /**
   * Calculate proposed price for a single schedule (for use by dashboard budget summary and similar
   * aggregations). Delegates to the internal price calculator with campaign-creator flag derived
   * from campaign and user company.
   *
   * @param schedule Schedule entity
   * @param inventory Inventory entity
   * @param campaign Campaign entity
   * @param userCompanyId User's company ID
   * @param context Custom fees context for the campaign
   * @return Proposed price, or null if not calculable
   */
  public Double calculateProposedPriceForSchedule(
      Schedule schedule,
      Inventory inventory,
      Campaign campaign,
      String userCompanyId,
      CustomFeesContext context) {
    boolean isCampaignCreator = isCampaignCreator(campaign, userCompanyId);
    return calculateProposedPrice(
        schedule, inventory, campaign, userCompanyId, isCampaignCreator, context);
  }

  /**
   * Check if user is campaign creator.
   *
   * @param campaign Campaign entity
   * @param userCompanyId User's company ID
   * @return true if user is campaign creator
   */
  private boolean isCampaignCreator(Campaign campaign, String userCompanyId) {
    return campaign.getCompanyId().equals(userCompanyId);
  }

  private String resolveUserCompanyId(String fallback) {
    try {
      return userService.getIamUserContext().getCompanyId();
    } catch (Exception e) {
      return fallback;
    }
  }

  /**
   * Check if user is media owner for the given inventory.
   *
   * @param inventory Inventory entity
   * @param userCompanyId User's company ID
   * @return true if user is media owner
   */
  private boolean isMediaOwner(Inventory inventory, String userCompanyId) {
    return inventory.getMediaOwnerId() != null && inventory.getMediaOwnerId().equals(userCompanyId);
  }

  /**
   * Validate if the logged-in user has authorization to approve the given
   * CampaignInventorySchedules based on the last history entry.
   *
   * @param campaignSchedule CampaignInventorySchedules to validate
   * @param campaign Campaign entity
   * @param inventory Inventory entity
   * @param loggedInUserCompanyId Logged-in user's company ID
   * @return true if approval is authorized, false otherwise
   */
  private boolean validateApprovalAuthorization(
      CampaignInventorySchedules campaignSchedule,
      Campaign campaign,
      Inventory inventory,
      String loggedInUserCompanyId) {
    List<CampaignInventorySchedules.History> history = campaignSchedule.getHistory();
    if (history == null || history.isEmpty()) {
      log.warn("No history found for CampaignInventorySchedules: {}", campaignSchedule.getId());
      return false;
    }

    // Get last history entry
    CampaignInventorySchedules.History lastHistory = history.getLast();
    PricingAction lastAction = lastHistory.getAction();
    String lastHistoryCompanyId = lastHistory.getCompanyId();

    if (lastAction == null) {
      log.warn(
          "Last history entry has no action for CampaignInventorySchedules: {}",
          campaignSchedule.getId());
      return false;
    }

    // Rule 1: If last action is RATE_CARD, MediaOwner must approve
    if (lastAction == PricingAction.RATE_CARD) {
      return isMediaOwner(inventory, loggedInUserCompanyId);
    }

    // Rule 2 & 3: If last action is PROPOSED or COUNTERED
    if (lastAction == PricingAction.PROPOSED || lastAction == PricingAction.COUNTERED) {
      if (lastHistoryCompanyId == null) {
        log.warn(
            "Last history entry has no companyId for CampaignInventorySchedules: {}",
            campaignSchedule.getId());
        return false;
      }

      // Rule 2: If last history companyId == campaign.companyId (campaign creator proposed),
      // then mediaOwner must approve
      if (lastHistoryCompanyId.equals(campaign.getCompanyId())) {
        return isMediaOwner(inventory, loggedInUserCompanyId);
      }

      // Rule 3: If last history companyId == inventory.mediaOwnerId (mediaOwner proposed),
      // then campaign creator must approve
      if (lastHistoryCompanyId.equals(inventory.getMediaOwnerId())) {
        return isCampaignCreator(campaign, loggedInUserCompanyId);
      }
    }

    // For ACCEPTED or other actions, return false (should not happen in normal flow)
    log.warn(
        "Unexpected last action {} for CampaignInventorySchedules: {}",
        lastAction,
        campaignSchedule.getId());
    return false;
  }

  /**
   * Convert CampaignInventorySchedules to CampaignSchedulePriceResponseDTO with all pricing
   * calculations. Uses pre-loaded custom fees context (must be built once per campaign).
   *
   * @param schedule Campaign inventory schedule
   * @param inventory Inventory entity
   * @param campaign Campaign entity
   * @param duration Campaign duration in days
   * @param customFeesContext Pre-loaded custom fees context for the campaign
   * @return Response DTO with pricing information
   */
  /**
   * Overload that accepts a pre-loaded schedule map, plus the caller's userCompanyId/
   * isCampaignCreator and a media-owner-name lookup map — all identical across every row of one
   * {@code getCampaignSchedulePrices} page, so the caller resolves them once and passes them down
   * rather than each row re-fetching the current user context or the same company name.
   */
  private CampaignSchedulePriceResponseDTO convertToPriceResponseDTO(
      CampaignInventorySchedules schedule,
      Inventory inventory,
      Campaign campaign,
      int duration,
      CustomFeesContext customFeesContext,
      Map<String, Schedule> scheduleMap,
      String userCompanyId,
      boolean isCampaignCreator,
      Map<String, String> mediaOwnerNameByMediaOwnerId) {

    List<String> scheduleIds = schedule.getScheduleIds();
    List<Schedule> schedules = resolveSchedules(scheduleIds, scheduleMap);

    // Calculate forecast metrics (use scheduleMap when provided to avoid per-item fetch)
    CampaignInventorySchedulesForecastDTO forecast =
        prepareInventoryForecastForCampaignInventorySchedules(schedule, scheduleMap, inventory);

    // Get pricing from inventory
    Double spotRate = InventoryService.getSpotRate(inventory);
    Double cpmRate = InventoryService.getCpm(inventory);

    // Calculate ad plays per day
    Long totalAdPlays = forecast.getEstimatedAdPlays();
    Long adPlaysPerDay = totalAdPlays != null && duration > 0 ? totalAdPlays / duration : null;

    // Calculate rate cards
    Double dailyRate = null;
    Double weeklyRate = null;
    Double monthlyRate = null;
    if (spotRate != null && adPlaysPerDay != null) {
      dailyRate = spotRate * adPlaysPerDay;
      weeklyRate = dailyRate * 7;
      monthlyRate = dailyRate * 30;
    }

    // Get impressions and reach from schedules (sum of all approved schedules)
    Long impressions = null;
    Long reach = null;
    if (!schedules.isEmpty()) {
      impressions =
          schedules.stream()
              .map(Schedule::getImpressions)
              .filter(Objects::nonNull)
              .mapToLong(Long::longValue)
              .sum();
      reach =
          schedules.stream()
              .map(Schedule::getReach)
              .filter(Objects::nonNull)
              .mapToLong(Long::longValue)
              .sum();
      // If sum is 0, set to null to indicate no data
      if (impressions == 0) {
        impressions = null;
      }
      if (reach == 0) {
        reach = null;
      }
    }

    // Calculate current and proposed rates from schedules with custom fees
    Double currentRate = null;
    Double proposedRate = null;
    if (!schedules.isEmpty()) {
      double totalCurrentRate = 0.0;
      double totalProposedRate = 0.0;

      for (Schedule s : schedules) {
        Double[] prices;
        if (isCampaignCreator) {
          prices =
              calculatePricesForCampaignCreator(
                  s, inventory, campaign.getId(), userCompanyId, customFeesContext);
        } else {
          prices = calculatePricesForMediaOwner(s, inventory, campaign.getId(), customFeesContext);
        }

        if (prices[0] != null) {
          totalCurrentRate += prices[0];
        }
        if (prices[1] != null) {
          totalProposedRate += prices[1];
        }
      }

      currentRate = totalCurrentRate > 0.0 ? totalCurrentRate : null;
      proposedRate = totalProposedRate > 0.0 ? totalProposedRate : null;
    }

    // If no rates from schedules, use inventory spot rate
    if (currentRate == null) {
      currentRate = spotRate;
    }

    // Calculate average discount percentage from all schedules
    Double discountPercent =
        schedules.stream()
            .map(Schedule::getDiscount)
            .filter(Objects::nonNull)
            .map(Schedule.Discount::getValue)
            .filter(Objects::nonNull)
            .mapToDouble(this::parseDiscount)
            .filter(d -> d > 0)
            .average()
            .orElse(0.0);

    if (discountPercent == 0.0) {
      discountPercent = null;
    }

    // Media owner name — resolved once per page by the caller, not once per row.
    String mediaOwnerName =
        !schedule.getMediaOwnerId().isBlank() && mediaOwnerNameByMediaOwnerId != null
            ? mediaOwnerNameByMediaOwnerId.get(inventory.getMediaOwnerId())
            : null;

    // Convert schedules to DTOs with calculated prices
    final boolean finalIsCampaignCreator = isCampaignCreator;
    final String finalUserCompanyId = userCompanyId;
    final CustomFeesContext finalCustomFeesContext = customFeesContext;
    final String inventoryClassification = inventory.getClassification();
    final Integer inventoryMaxSpotsPerLoop = getInventoryMaxSpotsPerLoop(inventory);
    List<CampaignSchedulePriceResponseDTO.SchedulePriceDTO> scheduleDTOs =
        schedules.stream()
            .map(
                s -> {
                  Double[] prices;
                  if (finalIsCampaignCreator) {
                    prices =
                        calculatePricesForCampaignCreator(
                            s,
                            inventory,
                            campaign.getId(),
                            finalUserCompanyId,
                            finalCustomFeesContext);
                  } else {
                    prices =
                        calculatePricesForMediaOwner(
                            s, inventory, campaign.getId(), finalCustomFeesContext);
                  }

                  return CampaignSchedulePriceResponseDTO.SchedulePriceDTO.builder()
                      .id(s.getId())
                      .name(s.getName())
                      .startDate(s.getStartDate())
                      .endDate(s.getEndDate())
                      .scheduleDays(
                          s.getScheduleDays() != null
                              ? s.getScheduleDays().stream().map(Enum::name).toList()
                              : Collections.emptyList())
                      .type(s.getType() != null ? s.getType().name() : null)
                      .bonusType(s.getBonusType())
                      .bookingMatrix(s.getBookingMatrix())
                      .duration(s.getDuration())
                      .spotsPerLoop(s.getSpotsPerLoop())
                      .spotsPerHour(s.getSpotsPerHour())
                      .sov(
                          calculateInventorySov(
                              inventoryClassification,
                              s.getSpotsPerLoop(),
                              inventoryMaxSpotsPerLoop,
                              s.getTotalSot(),
                              s.getPlannedSot()))
                      .adPlays(s.getAdPlays())
                      .plannedSot(s.getPlannedSot())
                      .totalSot(s.getTotalSot())
                      .order(s.getOrder())
                      .impressions(s.getImpressions())
                      .reach(s.getReach())
                      .discount(
                          s.getDiscount() != null
                              ? CampaignSchedulePriceResponseDTO.SchedulePriceDTO.DiscountDTO
                                  .builder()
                                  .valueType(
                                      s.getDiscount().getValueType() != null
                                          ? s.getDiscount().getValueType().name()
                                          : null)
                                  .value(s.getDiscount().getValue())
                                  .build()
                              : null)
                      .currentRate(prices[0])
                      .proposedRate(prices[1])
                      .build();
                })
            .collect(Collectors.toList());

    return CampaignSchedulePriceResponseDTO.builder()
        .id(schedule.getId())
        .inventoryId(inventory.getId())
        .inventoryName(inventory.getName())
        .startDate(campaign.getStartDate())
        .endDate(campaign.getEndDate())
        .timeslot(extractTimeslot(inventory))
        .sov(forecast.getSov())
        .adPlays(totalAdPlays)
        .currentRate(currentRate)
        .proposedRate(proposedRate)
        .mediaOwnerId(schedule.getMediaOwnerId())
        .mediaOwnerName(mediaOwnerName)
        .impressions(impressions)
        .discountPercent(discountPercent)
        .monthlyRateCard(monthlyRate)
        .weeklyRateCard(weeklyRate)
        .dailyRate(dailyRate)
        .cpmRate(cpmRate)
        .reach(reach)
        .schedules(scheduleDTOs)
        .cinemaFields(inventory.getCinemaFields())
        .build();
  }

  private double parseDiscount(String value) {
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      log.warn("Invalid discount value: {}", value);
      return 0.0;
    }
  }

  /**
   * Extract timeslot information from schedule and inventory.
   *
   * @param inventory Inventory entity
   * @return Timeslot DTO
   */
  private CampaignSchedulePriceResponseDTO.Timeslot extractTimeslot(Inventory inventory) {
    if (inventory.getOperatingTimes() == null || inventory.getOperatingTimes().isEmpty()) {
      return CampaignSchedulePriceResponseDTO.Timeslot.builder()
          .startTime("00:00")
          .endTime("23:59")
          .build();
    }

    // Get first operating time from any weekday
    String startTime = "00:00";
    String endTime = "23:59";
    for (List<Inventory.OperatingTime> operatingTimes : inventory.getOperatingTimes().values()) {
      if (operatingTimes != null && !operatingTimes.isEmpty()) {
        Inventory.OperatingTime firstTime = operatingTimes.getFirst();
        startTime = firstTime.getStart();
        endTime = firstTime.getEnd();
        break;
      }
    }

    return CampaignSchedulePriceResponseDTO.Timeslot.builder()
        .startTime(startTime)
        .endTime(endTime)
        .build();
  }

  /**
   * Get price history for a specific inventory with pagination support. Fetches all price history
   * entries from CampaignInventorySchedules and enriches them with user information (createdBy and
   * role). Results are sorted by latest activity first (date descending).
   *
   * @param campaignInventoryScheduleId Campaign ID to filter price history
   * @param pageable Pagination and sorting information
   * @return Page of PriceHistoryResponseDTO containing price history entries
   * @throws CampaignInventorySchedulesNotFoundException if no schedules found for the
   *     campaign-inventory combination
   */
  public Page<PriceHistoryResponseDTO> getPriceHistory(
      String campaignInventoryScheduleId, Pageable pageable) {
    log.info(
        "Getting price history for campaignInventoryScheduleId: {} with pagination",
        campaignInventoryScheduleId);

    // Use repository to find CampaignInventorySchedules by campaignId and inventoryId
    CampaignInventorySchedules schedule;
    try {
      schedule = inventorySchedulesRepository.findById(campaignInventoryScheduleId).orElse(null);
    } catch (Exception e) {
      log.error(
          "Error fetching campaign inventory schedules for campaignInventoryScheduleId: {}",
          campaignInventoryScheduleId,
          e);
      return emptyPage(pageable);
    }

    if (schedule == null) {
      log.warn(
          "No campaign inventory schedules found for campaignInventoryScheduleId: {},",
          campaignInventoryScheduleId);
      return emptyPage(pageable);
    }

    List<CampaignInventorySchedules.History> historyList = schedule.getHistory();
    if (historyList == null || historyList.isEmpty()) {
      log.warn("No history found for campaignInventoryScheduleId: {}", campaignInventoryScheduleId);
      return emptyPage(pageable);
    }

    // Get campaign and inventory for price calculation context. The mode-checked load must NOT be
    // swallowed: cross-mode access to a schedule's history behaves as if it does not exist (Test
    // Mode partition), instead of degrading to a context-less response that would leak the data.
    Campaign campaign = null;
    Inventory inventory = null;
    try {
      campaign = campaignService.findByIdForCurrentMode(schedule.getCampaignId());
    } catch (Exception e) {
      log.warn(
          "Campaign {} not accessible for price history (missing or cross-mode): {}",
          schedule.getCampaignId(),
          e.getMessage());
      return emptyPage(pageable);
    }
    try {
      inventory = inventoryService.getById(schedule.getInventoryId());
    } catch (Exception e) {
      log.warn("Error fetching inventory for price history calculation: {}", e.getMessage());
    }

    CustomFeesContext customFeesContext =
        campaign != null ? customFeeService.getActiveCustomFeesContextForCampaign(campaign) : null;

    // Convert History objects to PriceHistoryResponseDTO
    final Campaign finalCampaign = campaign;
    final Inventory finalInventory = inventory;
    final CustomFeesContext finalCustomFeesContext = customFeesContext;
    List<PriceHistoryResponseDTO> allHistoryEntries;
    try {
      allHistoryEntries =
          historyList.stream()
              .filter(this::isValidHistoryEntry)
              .map(
                  history ->
                      convertToPriceHistoryDTO(
                          history, schedule, finalCampaign, finalInventory, finalCustomFeesContext))
              .sorted(
                  Comparator.comparing(
                      PriceHistoryResponseDTO::getCreatedAt,
                      Comparator.nullsLast(Comparator.reverseOrder())))
              .collect(Collectors.toList());
    } catch (Exception e) {
      log.error(
          "Error processing history entries for campaignInventoryScheduleId: {},",
          campaignInventoryScheduleId,
          e);
      return emptyPage(pageable);
    }

    return createPaginatedResponse(allHistoryEntries, pageable, campaignInventoryScheduleId);
  }

  private Page<PriceHistoryResponseDTO> emptyPage(Pageable pageable) {
    return new PageImpl<>(Collections.emptyList(), pageable, 0);
  }

  private boolean isValidHistoryEntry(CampaignInventorySchedules.History history) {
    return history.getAction() != null;
  }

  private PriceHistoryResponseDTO convertToPriceHistoryDTO(
      CampaignInventorySchedules.History history,
      CampaignInventorySchedules campaignInventorySchedule,
      Campaign campaign,
      Inventory inventory,
      CustomFeesContext customFeesContext) {
    String userId = history.getUserId();
    String companyId = history.getCompanyId();
    PricingAction action = history.getAction();
    LocalDateTime date = history.getDate();
    LocalDateTime createdAt = date != null ? date : LocalDateTime.now();

    UserResponseDTO user = userService.getUserById(userId);

    // Calculate oldPrice and newPrice using stored effectiveDiscountPercentage
    // oldPrice = currentRate (basePrice + custom fees) - same for all history entries
    // newPrice = oldPrice * (1 - effectiveDiscountPercentage / 100)
    Double oldPrice = null;
    Double newPrice = null;

    try {
      // Get schedules for this campaign inventory schedule
      List<Schedule> schedules = Collections.emptyList();
      if (campaignInventorySchedule.getScheduleIds() != null
          && !campaignInventorySchedule.getScheduleIds().isEmpty()) {
        schedules = scheduleRepository.findAllById(campaignInventorySchedule.getScheduleIds());
      }

      if (!schedules.isEmpty() && campaign != null && inventory != null) {
        // Determine user type for price calculation (use history user's company)
        String currentUserCompanyId = userService.getIamUserContext().getCompanyId();
        boolean isCampaignCreator = isCampaignCreator(campaign, currentUserCompanyId);

        // Calculate currentRate (oldPrice) - same for all history entries
        double totalCurrentRate = 0.0;

        for (Schedule s : schedules) {
          Double[] prices;
          if (isCampaignCreator) {
            prices =
                calculatePricesForCampaignCreator(
                    s, inventory, campaign.getId(), currentUserCompanyId, customFeesContext);
          } else {
            prices =
                calculatePricesForMediaOwner(s, inventory, campaign.getId(), customFeesContext);
          }

          if (prices[0] != null) {
            totalCurrentRate += prices[0];
          }
        }

        oldPrice = totalCurrentRate > 0.0 ? totalCurrentRate : null;

        // Calculate newPrice using stored effectiveDiscountPercentage
        Double effectiveDiscountPercentage = history.getEffectiveDiscountPercentage();
        if (oldPrice != null && effectiveDiscountPercentage != null) {
          newPrice = oldPrice * (1 - effectiveDiscountPercentage / 100.0);
        } else {
          newPrice = oldPrice; // If no discount, newPrice equals oldPrice
        }
      }
    } catch (Exception e) {
      log.warn("Error calculating prices for history entry: {}", e.getMessage(), e);
    }

    return PriceHistoryResponseDTO.builder()
        .oldPrice(oldPrice)
        .newPrice(newPrice)
        .action(action)
        .userId(userId)
        .companyId(companyId)
        .createdBy(extractUserName(user))
        .role(extractUserRole(user))
        .createdAt(createdAt)
        .build();
  }

  private Page<PriceHistoryResponseDTO> createPaginatedResponse(
      List<PriceHistoryResponseDTO> allHistoryEntries,
      Pageable pageable,
      String campaignInventoryScheduleId) {
    int totalElements = allHistoryEntries.size();
    int page = pageable.getPageNumber();
    int size = pageable.getPageSize();
    int start = page * size;
    int end = Math.min(start + size, totalElements);

    List<PriceHistoryResponseDTO> paginatedContent =
        start < totalElements ? allHistoryEntries.subList(start, end) : Collections.emptyList();

    log.info(
        "Retrieved {} price history entries for campaignInventoryScheduleId: {}, (page {}, size {})",
        totalElements,
        campaignInventoryScheduleId,
        page,
        size);

    return new PageImpl<>(paginatedContent, pageable, totalElements);
  }

  /**
   * Creates a history entry for pricing actions based on schedules. Sets userId and companyId from
   * the current user context. Stores effectiveDiscountPercentage for calculating newPrice later.
   *
   * @param schedules List of Schedule entities (used for validation and discount calculation)
   * @param action PricingAction to set in the history entry
   * @param campaignInventorySchedule CampaignInventorySchedules entity (for calculating effective
   *     discount)
   * @param inventory Inventory entity (for price calculation)
   * @param campaign Campaign entity (for price calculation)
   * @return CampaignInventorySchedules.History object with user information and
   *     effectiveDiscountPercentage
   */
  public CampaignInventorySchedules.History createHistoryEntry(
      List<Schedule> schedules,
      PricingAction action,
      CampaignInventorySchedules campaignInventorySchedule,
      Inventory inventory,
      Campaign campaign) {
    // Get current user context
    var userContext = userService.getIamUserContext();
    String userId = userContext.getUserId();
    String companyId = userContext.getCompanyId();

    // Calculate effectiveDiscountPercentage
    double effectiveDiscountPercentage = 0.0;
    if (action == PricingAction.RATE_CARD) {
      effectiveDiscountPercentage = 0.0;
    } else {
      CustomFeesContext customFeesContext =
          customFeeService.getActiveCustomFeesContextForCampaign(campaign);
      effectiveDiscountPercentage =
          calculateEffectiveDiscountPercentage(
              schedules, campaignInventorySchedule, inventory, campaign, customFeesContext);
    }

    // Create history entry
    CampaignInventorySchedules.History history = new CampaignInventorySchedules.History();
    history.setUserId(userId);
    history.setCompanyId(companyId);
    history.setAction(action);
    history.setDate(LocalDateTime.now());
    history.setEffectiveDiscountPercentage(effectiveDiscountPercentage);

    log.debug(
        "Created history entry: action={}, userId={}, companyId={}, effectiveDiscountPercentage={}",
        action,
        userId,
        companyId,
        effectiveDiscountPercentage);

    return history;
  }

  /**
   * Calculate effective discount percentage based on all schedules in CampaignInventorySchedules.
   * Uses pre-loaded custom fees context (built once per campaign).
   *
   * @param schedules List of schedules (used for context, but we calculate based on all schedules)
   * @param campaignInventorySchedule CampaignInventorySchedules entity
   * @param inventory Inventory entity
   * @param campaign Campaign entity
   * @param customFeesContext Pre-loaded custom fees context for the campaign
   * @return Effective discount percentage (0.0 if no discount)
   */
  private Double calculateEffectiveDiscountPercentage(
      List<Schedule> schedules,
      CampaignInventorySchedules campaignInventorySchedule,
      Inventory inventory,
      Campaign campaign,
      CustomFeesContext customFeesContext) {
    try {
      List<Schedule> allSchedules = Collections.emptyList();
      if (campaignInventorySchedule.getScheduleIds() != null
          && !campaignInventorySchedule.getScheduleIds().isEmpty()) {
        allSchedules = scheduleRepository.findAllById(campaignInventorySchedule.getScheduleIds());
      }

      if (allSchedules.isEmpty()) {
        return 0.0;
      }

      var userContext = userService.getIamUserContext();
      String userCompanyId = userContext.getCompanyId();
      boolean isCampaignCreator = isCampaignCreator(campaign, userCompanyId);

      double totalCurrentRate = 0.0;
      double totalProposedRate = 0.0;

      for (Schedule s : allSchedules) {
        Double[] prices;
        if (isCampaignCreator) {
          prices =
              calculatePricesForCampaignCreator(
                  s, inventory, campaign.getId(), userCompanyId, customFeesContext);
        } else {
          prices = calculatePricesForMediaOwner(s, inventory, campaign.getId(), customFeesContext);
        }

        if (prices[0] != null) {
          totalCurrentRate += prices[0];
        }
        if (prices[1] != null) {
          totalProposedRate += prices[1];
        }
      }

      if (totalCurrentRate > 0.0) {
        double discountAmount = totalCurrentRate - totalProposedRate;
        return (discountAmount / totalCurrentRate) * 100.0;
      }
    } catch (Exception e) {
      log.warn("Error calculating effective discount percentage: {}", e.getMessage());
    }

    return 0.0;
  }

  /**
   * Accept inventory prices for selected CampaignInventorySchedules. Validates campaign and
   * CampaignInventorySchedules IDs, then updates approval data and logs acceptance in history. This
   * operation is atomic - all updates succeed or fail together. Any authenticated user can accept.
   *
   * @param campaignId Campaign ID
   * @param campaignInventorySchedulesIds List of CampaignInventorySchedules IDs to accept prices
   *     for. If null/empty, accepts all CampaignInventorySchedules for the campaign
   * @throws CampaignNotFoundException if campaign doesn't exist
   * @throws CampaignInventorySchedulesNotFoundException if CampaignInventorySchedules IDs don't
   *     exist or don't belong to the campaign
   */
  @CacheEvict(value = "campaignInventorySchedules", allEntries = true)
  public void acceptInventoryPrices(String campaignId, List<String> campaignInventorySchedulesIds) {
    boolean hasCampaignInventorySchedulesIds =
        campaignInventorySchedulesIds != null && !campaignInventorySchedulesIds.isEmpty();
    log.info(
        "Accepting inventory prices for campaignId: {} with {} CampaignInventorySchedules IDs",
        campaignId,
        hasCampaignInventorySchedulesIds ? campaignInventorySchedulesIds.size() : "all");

    // Owner-only write guard: validates existence, data mode, and acting-company ownership
    Campaign campaign = campaignService.findByIdForCurrentModeForWrite(campaignId);

    // Get current user ID
    String userId = userService.getIamUserContext().getUserId();

    // Fetch CampaignInventorySchedules based on provided IDs or get all for campaign
    List<CampaignInventorySchedules> schedulesToProcess;
    if (hasCampaignInventorySchedulesIds) {
      // Get specific CampaignInventorySchedules by IDs
      schedulesToProcess = inventorySchedulesRepository.findAllById(campaignInventorySchedulesIds);

      // Validate all provided IDs were found
      if (schedulesToProcess.size() != campaignInventorySchedulesIds.size()) {
        Set<String> foundIds =
            schedulesToProcess.stream()
                .map(CampaignInventorySchedules::getId)
                .collect(Collectors.toSet());
        List<String> missingIds = new ArrayList<>(campaignInventorySchedulesIds);
        missingIds.removeAll(foundIds);
        throw new CampaignInventorySchedulesNotFoundException(
            campaignId, "CampaignInventorySchedules IDs not found: " + missingIds);
      }

      // Validate all CampaignInventorySchedules belong to the campaign
      List<String> invalidIds = new ArrayList<>();
      for (CampaignInventorySchedules cis : schedulesToProcess) {
        if (!campaignId.equals(cis.getCampaignId())) {
          invalidIds.add(cis.getId());
        }
      }
      if (!invalidIds.isEmpty()) {
        throw new CampaignInventorySchedulesNotFoundException(
            campaignId, "CampaignInventorySchedules IDs do not belong to campaign: " + invalidIds);
      }

      // Filter out already fully approved CampaignInventorySchedules
      List<String> alreadyApprovedIds = new ArrayList<>();
      schedulesToProcess =
          schedulesToProcess.stream()
              .filter(
                  cis -> {
                    // Check if already fully approved
                    if (cis.getScheduleIds() != null && !cis.getScheduleIds().isEmpty()) {
                      List<String> scheduleIds = cis.getScheduleIds();
                      List<String> approvedScheduleIds =
                          Optional.ofNullable(cis.getApprovedScheduleIds())
                              .orElse(Collections.emptyList());

                      // Check if all scheduleIds are already approved
                      if (new HashSet<>(approvedScheduleIds).containsAll(scheduleIds)) {
                        alreadyApprovedIds.add(cis.getId());
                        log.debug(
                            "CampaignInventorySchedules {} is already fully approved, skipping",
                            cis.getId());
                        return false;
                      }
                    }
                    return true;
                  })
              .collect(Collectors.toList());

      // Log if any were already approved
      if (!alreadyApprovedIds.isEmpty()) {
        log.info(
            "Skipping {} already approved CampaignInventorySchedules: {}",
            alreadyApprovedIds.size(),
            alreadyApprovedIds);
      }
    } else {
      // Get all CampaignInventorySchedules for the campaign
      schedulesToProcess = inventorySchedulesRepository.findByCampaignId(campaignId);
    }

    if (schedulesToProcess.isEmpty()) {
      if (hasCampaignInventorySchedulesIds) {
        // If IDs were provided but all are already approved or don't exist, return gracefully
        log.info(
            "No CampaignInventorySchedules to process for campaignId: {} - all may be already approved or invalid",
            campaignId);
        return;
      } else {
        log.info("No CampaignInventorySchedules to approve for campaignId: {}", campaignId);
        return;
      }
    }

    // Fetch all inventories for validation
    Map<String, Inventory> inventoryMap = new HashMap<>();
    for (CampaignInventorySchedules cis : schedulesToProcess) {
      if (!inventoryMap.containsKey(cis.getInventoryId())) {
        inventoryMap.put(cis.getInventoryId(), inventoryService.getById(cis.getInventoryId()));
      }
    }

    // Collect all schedule IDs from all CampaignInventorySchedules to fetch
    Set<String> allScheduleIds = new HashSet<>();
    for (CampaignInventorySchedules schedule : schedulesToProcess) {
      if (schedule.getScheduleIds() != null) {
        allScheduleIds.addAll(schedule.getScheduleIds());
      }
    }

    // Fetch all schedules and create a map for quick lookup
    Map<String, Schedule> scheduleMap = new HashMap<>();
    if (!allScheduleIds.isEmpty()) {
      List<Schedule> allSchedules = scheduleRepository.findAllById(new ArrayList<>(allScheduleIds));
      scheduleMap =
          allSchedules.stream().collect(Collectors.toMap(Schedule::getId, Function.identity()));
    }

    // Process each CampaignInventorySchedule
    List<CampaignInventorySchedules> schedulesToUpdate = new ArrayList<>();

    for (CampaignInventorySchedules campaignSchedule : schedulesToProcess) {
      Inventory inventory = inventoryMap.get(campaignSchedule.getInventoryId());
      if (inventory == null) {
        log.warn(
            "Inventory not found for inventoryId: {}, skipping", campaignSchedule.getInventoryId());
        continue;
      }

      // Approve ALL scheduleIds within this CampaignInventorySchedules
      List<String> relevantScheduleIds = new ArrayList<>();
      if (campaignSchedule.getScheduleIds() != null
          && !campaignSchedule.getScheduleIds().isEmpty()) {
        relevantScheduleIds = new ArrayList<>(campaignSchedule.getScheduleIds());

        // Update approvedScheduleIds - merge with existing approvedScheduleIds
        Set<String> approvedIds =
            new LinkedHashSet<>(
                Optional.ofNullable(campaignSchedule.getApprovedScheduleIds())
                    .orElse(Collections.emptyList()));
        approvedIds.addAll(relevantScheduleIds);
        campaignSchedule.setApprovedScheduleIds(new ArrayList<>(approvedIds));
      }

      campaignSchedule.setApprovedBy(userId);

      // Get relevant schedules for this CampaignInventorySchedules
      List<Schedule> relevantSchedules =
          relevantScheduleIds.stream()
              .filter(scheduleMap::containsKey)
              .map(scheduleMap::get)
              .collect(Collectors.toList());

      // Create history entry using createHistoryEntry() method
      CampaignInventorySchedules.History historyEntry =
          createHistoryEntry(
              relevantSchedules, PricingAction.ACCEPTED, campaignSchedule, inventory, campaign);

      if (campaignSchedule.getHistory() == null) {
        campaignSchedule.setHistory(new ArrayList<>());
      }
      campaignSchedule.getHistory().add(historyEntry);

      schedulesToUpdate.add(campaignSchedule);

      log.debug("Prepared approval update for inventoryId: {}", campaignSchedule.getInventoryId());
    }

    if (schedulesToUpdate.isEmpty()) {
      log.info("No schedules to update after filtering");
      return;
    }

    // Save all updates atomically
    inventorySchedulesRepository.saveAll(schedulesToUpdate);

    // Mark campaign as negotiated now that prices have been accepted
    campaign.setIsNegotiated(true);
    campaignService.save(campaign);

    // Evict cache for all affected inventories
    for (CampaignInventorySchedules schedule : schedulesToUpdate) {
      scheduleCacheEvictor.evict(campaignId, schedule.getInventoryId());
    }

    log.info(
        "Successfully accepted inventory prices for {} CampaignInventorySchedules in campaignId: {}",
        schedulesToUpdate.size(),
        campaignId);
  }

  /**
   * Apply discount or bonus to selected schedules. Validates that all schedule IDs belong to the
   * campaign, then applies the discount or bonus accordingly.
   *
   * <p>For DISCOUNT:
   *
   * <ul>
   *   <li>Calculates proposedPrice based on discountType (PERCENTAGE or VALUE) applied to
   *       actualPrice
   *   <li>Sets Schedule.discount with discount details
   *   <li>Adds history entry to CampaignInventorySchedules with PricingAction PROPOSED or COUNTERED
   *       based on user's primary company vs mediaOwnerId
   * </ul>
   *
   * <p>For BONUS:
   *
   * <ul>
   *   <li>Sets Schedule.bonusType with the provided bonus description
   * </ul>
   *
   * @param campaignId Campaign ID
   * @param request Request containing scheduleIds, actionType, discount, and bonus
   * @throws CampaignNotFoundException if campaign doesn't exist
   * @throws ScheduleIdsNotBelongToCampaignException if schedule IDs don't belong to the campaign
   * @throws ScheduleIdsNotFoundException if schedule IDs are not found in the database
   */
  @CacheEvict(value = "campaignInventorySchedules", allEntries = true)
  public void applyAdjustment(String campaignId, ApplyAdjustmentRequestDTO request) {
    log.info(
        "Applying {} adjustment for campaignId: {} with {} schedule IDs",
        request.getActionType(),
        campaignId,
        request.getScheduleIds().size());

    // Owner-only write guard: validates existence, data mode, and acting-company ownership
    campaignService.findByIdForCurrentModeForWrite(campaignId);

    // Validate request based on actionType
    if (request.getActionType() == ApplyAdjustmentRequestDTO.ActionType.DISCOUNT) {
      if (request.getDiscount() == null) {
        throw new IllegalArgumentException("Discount is required when actionType is DISCOUNT");
      }
    } else if (request.getActionType() == ApplyAdjustmentRequestDTO.ActionType.BONUS) {
      if (request.getBonus() == null || request.getBonus().trim().isEmpty()) {
        throw new IllegalArgumentException("Bonus is required when actionType is BONUS");
      }
    }

    // Get all CampaignInventorySchedules for this campaign
    List<CampaignInventorySchedules> allCampaignSchedules =
        inventorySchedulesRepository.findByCampaignId(campaignId);

    // Collect all schedule IDs that belong to this campaign
    Set<String> campaignScheduleIds = new HashSet<>();
    Map<String, CampaignInventorySchedules> scheduleIdToCampaignScheduleMap = new HashMap<>();
    for (CampaignInventorySchedules campaignSchedule : allCampaignSchedules) {
      if (campaignSchedule.getScheduleIds() != null) {
        for (String scheduleId : campaignSchedule.getScheduleIds()) {
          campaignScheduleIds.add(scheduleId);
          scheduleIdToCampaignScheduleMap.put(scheduleId, campaignSchedule);
        }
      }
    }

    // Validate all provided schedule IDs belong to the campaign
    List<String> invalidScheduleIds = new ArrayList<>();
    for (String scheduleId : request.getScheduleIds()) {
      if (!campaignScheduleIds.contains(scheduleId)) {
        invalidScheduleIds.add(scheduleId);
      }
    }

    if (!invalidScheduleIds.isEmpty()) {
      throw new ScheduleIdsNotBelongToCampaignException(campaignId, invalidScheduleIds);
    }

    // Fetch all schedules from database
    List<Schedule> schedules = scheduleRepository.findAllById(request.getScheduleIds());
    if (schedules.size() != request.getScheduleIds().size()) {
      List<String> foundScheduleIds = schedules.stream().map(Schedule::getId).toList();
      List<String> missingScheduleIds = new ArrayList<>(request.getScheduleIds());
      missingScheduleIds.removeAll(foundScheduleIds);
      throw new ScheduleIdsNotFoundException(missingScheduleIds);
    }

    // Get user's primary company ID
    String primaryCompanyId = userService.getActingCompanyId();

    // Get campaign for price calculation (needed for converting VALUE discount to percentage)
    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);
    boolean isCampaignCreator = isCampaignCreator(campaign, primaryCompanyId);
    CustomFeesContext customFeesContext =
        customFeeService.getActiveCustomFeesContextForCampaign(campaign);

    // Group schedules by CampaignInventorySchedules for history updates
    Map<CampaignInventorySchedules, List<Schedule>> campaignScheduleToSchedulesMap =
        new HashMap<>();

    // Apply adjustment based on actionType
    if (request.getActionType() == ApplyAdjustmentRequestDTO.ActionType.DISCOUNT) {
      ApplyAdjustmentRequestDTO.DiscountDTO discountDTO = request.getDiscount();
      boolean isPercentageDiscount =
          discountDTO.getDiscountType()
              == ApplyAdjustmentRequestDTO.DiscountDTO.DiscountType.PERCENTAGE;

      for (Schedule schedule : schedules) {
        if (schedule.getBasePrice() == null) {
          log.warn("Schedule {} has no basePrice, skipping discount calculation", schedule.getId());
          continue;
        }

        // Get CampaignInventorySchedules and Inventory for this schedule
        CampaignInventorySchedules campaignSchedule =
            scheduleIdToCampaignScheduleMap.get(schedule.getId());
        Inventory inventory = inventoryService.getById(campaignSchedule.getInventoryId());

        // Calculate discount value and convert to percentage if needed
        double discountValue = discountDTO.getValue();
        double discountPercentage = discountValue;

        // If discount type is VALUE, convert to percentage
        if (!isPercentageDiscount) {
          // Calculate schedule's currentPrice (mediaCost + visible fees, without discount)
          Double scheduleCurrentPrice =
              calculateCurrentPrice(
                  schedule,
                  inventory,
                  campaign,
                  primaryCompanyId,
                  isCampaignCreator,
                  customFeesContext);

          if (scheduleCurrentPrice == null || scheduleCurrentPrice <= 0) {
            log.warn(
                "Schedule {} has invalid currentPrice: {}, skipping discount calculation",
                schedule.getId(),
                scheduleCurrentPrice);
            continue;
          }

          // Convert VALUE discount to percentage: (discountValue / currentPrice) * 100
          discountPercentage = (discountValue / scheduleCurrentPrice) * 100.0;
        }

        // Set discount details - always store as PERCENTAGE
        Schedule.Discount discount =
            Schedule.Discount.builder()
                .valueType(DiscountValueType.PERCENTAGE)
                .value(String.valueOf(discountPercentage))
                .build();
        schedule.setDiscount(discount);

        // Group by CampaignInventorySchedules for history
        campaignScheduleToSchedulesMap
            .computeIfAbsent(campaignSchedule, k -> new ArrayList<>())
            .add(schedule);
      }
    } else if (request.getActionType() == ApplyAdjustmentRequestDTO.ActionType.BONUS) {
      // Set bonusType for all schedules
      for (Schedule schedule : schedules) {
        schedule.setBonusType(request.getBonus());

        // Group by CampaignInventorySchedules for history
        CampaignInventorySchedules campaignSchedule =
            scheduleIdToCampaignScheduleMap.get(schedule.getId());
        campaignScheduleToSchedulesMap
            .computeIfAbsent(campaignSchedule, k -> new ArrayList<>())
            .add(schedule);
      }
    }

    // Save all updated schedules
    scheduleRepository.saveAll(schedules);

    // Update history for each CampaignInventorySchedules (only for DISCOUNT)
    if (request.getActionType() == ApplyAdjustmentRequestDTO.ActionType.DISCOUNT) {
      // Campaign already retrieved above for discount conversion

      List<CampaignInventorySchedules> campaignSchedulesToUpdate = new ArrayList<>();

      for (Map.Entry<CampaignInventorySchedules, List<Schedule>> entry :
          campaignScheduleToSchedulesMap.entrySet()) {
        CampaignInventorySchedules campaignSchedule = entry.getKey();
        List<Schedule> relevantSchedules = entry.getValue();

        // Get inventory for this campaign schedule
        Inventory inventory = inventoryService.getById(campaignSchedule.getInventoryId());

        // Check if last history entry has the same PricingAction
        List<CampaignInventorySchedules.History> history = campaignSchedule.getHistory();
        CampaignInventorySchedules.History lastEntry = null;

        if (history != null && !history.isEmpty()) {
          lastEntry = history.getLast();
        }

        // Initialize history if null
        if (campaignSchedule.getHistory() == null) {
          campaignSchedule.setHistory(new ArrayList<>());
        }

        // If last entry is ACCEPTED, remove it before adding new discount history
        if (lastEntry != null && lastEntry.getAction() == PricingAction.ACCEPTED) {
          history.remove(lastEntry);
          log.debug(
              "Removed ACCEPTED history entry before adding new discount history for CampaignInventorySchedules: {}",
              campaignSchedule.getId());
          // Update lastEntry after removal
          lastEntry = history.isEmpty() ? null : history.getLast();
        }

        // Determine PricingAction: PROPOSED if adjustment is applied on RATE_CARD
        PricingAction pricingAction;
        if (lastEntry != null && lastEntry.getAction().equals(PricingAction.RATE_CARD)) {
          pricingAction = PricingAction.PROPOSED;
        } else {
          pricingAction = PricingAction.COUNTERED;
        }

        if (lastEntry != null
            && lastEntry.getAction() == pricingAction
            && lastEntry.getCompanyId().equals(primaryCompanyId)) {
          // Update the last history entry instead of adding a new one
          CampaignInventorySchedules.History updatedHistoryEntry =
              createHistoryEntry(
                  relevantSchedules, pricingAction, campaignSchedule, inventory, campaign);
          lastEntry.setDate(LocalDateTime.now());
          lastEntry.setUserId(updatedHistoryEntry.getUserId());
          lastEntry.setCompanyId(updatedHistoryEntry.getCompanyId());
          lastEntry.setEffectiveDiscountPercentage(
              updatedHistoryEntry.getEffectiveDiscountPercentage());
          log.debug(
              "Updated existing history entry with PricingAction {} for campaignSchedule inventoryId: {}",
              pricingAction,
              campaignSchedule.getInventoryId());
        } else {
          // Create and add new history entry
          CampaignInventorySchedules.History historyEntry =
              createHistoryEntry(
                  relevantSchedules, pricingAction, campaignSchedule, inventory, campaign);
          if (history == null || history.isEmpty()) {
            history = new ArrayList<>();
          }
          history.add(historyEntry);
          campaignSchedule.setHistory(history);

          log.debug(
              "Added new history entry with PricingAction {} for campaignSchedule inventoryId: {}",
              pricingAction,
              campaignSchedule.getInventoryId());
        }

        campaignSchedulesToUpdate.add(campaignSchedule);
      }

      // Save updated CampaignInventorySchedules
      if (!campaignSchedulesToUpdate.isEmpty()) {
        inventorySchedulesRepository.saveAll(campaignSchedulesToUpdate);
      }

      // Change campaign status to NEGOTIATING and reset approvals for impacted scheduleIds
      campaignService.changeCampaignStatus(campaignId, Campaign.Status.NEGOTIATING);

      // Reset approval workflow statues
      campaignApprovalWorkflowService.resetApprovalWorkflowStatus(campaignId);

      // Remove impacted scheduleIds from approvedScheduleIds and reset approvedBy if needed
      // Group scheduleIds by CampaignInventorySchedules to avoid processing same schedule multiple
      // times
      Map<CampaignInventorySchedules, List<String>> scheduleIdsByCampaignSchedule = new HashMap<>();
      for (String scheduleId : request.getScheduleIds()) {
        CampaignInventorySchedules campaignSchedule =
            scheduleIdToCampaignScheduleMap.get(scheduleId);
        if (campaignSchedule != null) {
          scheduleIdsByCampaignSchedule
              .computeIfAbsent(campaignSchedule, k -> new ArrayList<>())
              .add(scheduleId);
        }
      }

      // Update approvals for each CampaignInventorySchedules
      for (Map.Entry<CampaignInventorySchedules, List<String>> entry :
          scheduleIdsByCampaignSchedule.entrySet()) {
        CampaignInventorySchedules campaignSchedule = entry.getKey();
        List<String> scheduleIdsToRemove = entry.getValue();

        if (campaignSchedule.getApprovedScheduleIds() != null
            && !campaignSchedule.getApprovedScheduleIds().isEmpty()) {
          removeApprovedScheduleIds(scheduleIdsToRemove, campaignSchedule);
        }
      }

      // Save updated CampaignInventorySchedules with reset approvals
      if (!scheduleIdsByCampaignSchedule.isEmpty()) {
        inventorySchedulesRepository.saveAll(scheduleIdsByCampaignSchedule.keySet());
      }
    }

    log.info(
        "Successfully applied {} adjustment to {} schedules in campaignId: {}",
        request.getActionType(),
        schedules.size(),
        campaignId);
  }

  /**
   * Update discount on all schedules of a CampaignInventorySchedules based on a proposed price.
   * Calculates the total discount from current price and proposed price, then distributes it
   * proportionally across all schedules based on their price weightage.
   *
   * <p>Workflow:
   *
   * <ul>
   *   <li>Calculate total currentPrice for CampaignInventorySchedules (sum of all schedules'
   *       currentPrice)
   *   <li>Calculate total discount: currentPrice - proposedPrice
   *   <li>For each schedule: calculate weightage, distribute discount proportionally, convert to
   *       percentage, and update schedule
   *   <li>Update CampaignInventorySchedules history (similar to applyAdjustment)
   * </ul>
   *
   * @param campaignInventorySchedulesId CampaignInventorySchedules ID
   * @param proposedPrice Proposed price for the CampaignInventorySchedules
   * @throws CampaignInventorySchedulesNotFoundException if CampaignInventorySchedules not found
   * @throws IllegalArgumentException if proposedPrice is invalid or no schedules found
   */
  @CacheEvict(value = "campaignInventorySchedules", allEntries = true)
  public void updateDiscountByProposedPrice(
      String campaignInventorySchedulesId, Double proposedPrice, String scheduleId) {
    log.info(
        "Updating discount for CampaignInventorySchedules: {} with proposed price: {} and scheduleId: {}",
        campaignInventorySchedulesId,
        proposedPrice,
        scheduleId);

    // Validate proposedPrice
    if (proposedPrice == null || proposedPrice <= 0) {
      throw new IllegalArgumentException("Proposed price must be positive");
    }

    // Find CampaignInventorySchedules by ID
    CampaignInventorySchedules campaignInventorySchedules =
        inventorySchedulesRepository
            .findById(campaignInventorySchedulesId)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "CampaignInventorySchedules not found with ID: "
                            + campaignInventorySchedulesId));

    // Get Campaign and Inventory entities
    Campaign campaign =
        campaignService.findByIdForCurrentMode(campaignInventorySchedules.getCampaignId());
    Inventory inventory = inventoryService.getById(campaignInventorySchedules.getInventoryId());

    // Determine user type (campaignCreator vs mediaOwner)
    String userCompanyId = userService.getActingCompanyId();
    if (userCompanyId == null) {
      throw new IllegalStateException("User company ID not found");
    }
    boolean isCampaignCreator = isCampaignCreator(campaign, userCompanyId);

    CustomFeesContext customFeesContext =
        customFeeService.getActiveCustomFeesContextForCampaign(campaign);

    List<Schedule> schedules;

    // If scheduleId is provided, update only that specific schedule
    if (scheduleId != null && !scheduleId.trim().isEmpty()) {
      // Validate scheduleId belongs to this CampaignInventorySchedules
      List<String> scheduleIds = campaignInventorySchedules.getScheduleIds();
      if (scheduleIds == null || !scheduleIds.contains(scheduleId)) {
        throw new IllegalArgumentException(
            "Schedule with ID: "
                + scheduleId
                + " does not belong to CampaignInventorySchedules: "
                + campaignInventorySchedulesId);
      }

      // Find the specific schedule
      Schedule schedule =
          scheduleRepository
              .findById(scheduleId)
              .orElseThrow(
                  () -> new IllegalArgumentException("Schedule not found with ID: " + scheduleId));

      if (schedule.getBasePrice() == null) {
        throw new IllegalArgumentException("Schedule " + scheduleId + " has no basePrice");
      }

      // Calculate schedule's currentPrice
      Double scheduleCurrentPrice =
          calculateCurrentPrice(
              schedule, inventory, campaign, userCompanyId, isCampaignCreator, customFeesContext);

      double discountPercentage =
          getDiscountPercentage(proposedPrice, scheduleId, scheduleCurrentPrice);

      // Create Schedule.Discount with PERCENTAGE type
      Schedule.Discount discount =
          Schedule.Discount.builder()
              .valueType(DiscountValueType.PERCENTAGE)
              .value(String.valueOf(discountPercentage))
              .build();

      schedule.setDiscount(discount);
      schedules = List.of(schedule);

      // Save the updated schedule
      scheduleRepository.save(schedule);
    } else {
      // Original behavior: update all schedules proportionally
      // Get all schedules for this CampaignInventorySchedules
      List<String> allScheduleIds = campaignInventorySchedules.getScheduleIds();
      if (allScheduleIds == null || allScheduleIds.isEmpty()) {
        throw new IllegalArgumentException("CampaignInventorySchedules has no schedules to update");
      }

      schedules = scheduleRepository.findAllById(allScheduleIds);
      if (schedules.isEmpty()) {
        throw new IllegalArgumentException("No schedules found for CampaignInventorySchedules");
      }

      // Calculate total currentPrice for CampaignInventorySchedules
      Double totalCurrentPrice =
          calculateCampaignInventorySchedulesCurrentPrice(
              campaignInventorySchedules, inventory, campaign, userCompanyId, customFeesContext);

      if (totalCurrentPrice == null || totalCurrentPrice <= 0) {
        throw new IllegalArgumentException(
            "Cannot calculate current price for CampaignInventorySchedules");
      }

      // Validate proposedPrice is less than currentPrice
      if (proposedPrice >= totalCurrentPrice) {
        throw new IllegalArgumentException(
            "Proposed price must be less than current price. Current: "
                + totalCurrentPrice
                + ", Proposed: "
                + proposedPrice);
      }

      // Calculate total discount
      double totalDiscount = totalCurrentPrice - proposedPrice;

      // Distribute discount across schedules
      for (Schedule schedule : schedules) {
        if (schedule.getBasePrice() == null) {
          log.warn("Schedule {} has no basePrice, skipping discount calculation", schedule.getId());
          continue;
        }

        // Calculate schedule's currentPrice
        Double scheduleCurrentPrice =
            calculateCurrentPrice(
                schedule, inventory, campaign, userCompanyId, isCampaignCreator, customFeesContext);

        if (scheduleCurrentPrice == null || scheduleCurrentPrice <= 0) {
          log.warn(
              "Schedule {} has invalid currentPrice: {}, skipping discount calculation",
              schedule.getId(),
              scheduleCurrentPrice);
          continue;
        }

        // Calculate weightage: schedule price / total price
        double weightage = scheduleCurrentPrice / totalCurrentPrice;

        // Calculate schedule discount: total discount * weightage
        double scheduleDiscount = totalDiscount * weightage;

        // Calculate discount percentage: (scheduleDiscount / scheduleCurrentPrice) * 100
        double discountPercentage = (scheduleDiscount / scheduleCurrentPrice) * 100.0;

        // Create Schedule.Discount with PERCENTAGE type
        Schedule.Discount discount =
            Schedule.Discount.builder()
                .valueType(DiscountValueType.PERCENTAGE)
                .value(String.valueOf(discountPercentage))
                .build();

        schedule.setDiscount(discount);
      }

      // Save all updated schedules
      scheduleRepository.saveAll(schedules);
    }

    // Update CampaignInventorySchedules history (similar to applyAdjustment)
    List<CampaignInventorySchedules.History> history = campaignInventorySchedules.getHistory();
    CampaignInventorySchedules.History lastEntry = null;

    if (history != null && !history.isEmpty()) {
      lastEntry = history.getLast();
    }

    // Initialize history if null
    if (campaignInventorySchedules.getHistory() == null) {
      campaignInventorySchedules.setHistory(new ArrayList<>());
    }

    // If last entry is ACCEPTED, remove it before adding new discount history
    if (lastEntry != null && lastEntry.getAction() == PricingAction.ACCEPTED) {
      history.remove(lastEntry);
      log.debug(
          "Removed ACCEPTED history entry before adding new discount history for CampaignInventorySchedules: {}",
          campaignInventorySchedulesId);
      // Update lastEntry after removal
      lastEntry = history.isEmpty() ? null : history.getLast();
    }

    // Determine PricingAction: PROPOSED if adjustment is applied on RATE_CARD, else COUNTERED
    PricingAction pricingAction;
    if (lastEntry != null && lastEntry.getAction().equals(PricingAction.RATE_CARD)) {
      pricingAction = PricingAction.PROPOSED;
    } else {
      pricingAction = PricingAction.COUNTERED;
    }

    // Check if we should update existing history entry or create new one
    if (lastEntry != null
        && lastEntry.getAction() == pricingAction
        && lastEntry.getCompanyId() != null
        && lastEntry.getCompanyId().equals(userCompanyId)) {
      // Update the last history entry instead of adding a new one
      CampaignInventorySchedules.History updatedHistoryEntry =
          createHistoryEntry(
              schedules, pricingAction, campaignInventorySchedules, inventory, campaign);
      lastEntry.setDate(LocalDateTime.now());
      lastEntry.setUserId(updatedHistoryEntry.getUserId());
      lastEntry.setCompanyId(updatedHistoryEntry.getCompanyId());
      lastEntry.setEffectiveDiscountPercentage(
          updatedHistoryEntry.getEffectiveDiscountPercentage());
      log.debug(
          "Updated existing history entry with PricingAction {} for CampaignInventorySchedules: {}",
          pricingAction,
          campaignInventorySchedulesId);
    } else {
      // Create and add new history entry
      CampaignInventorySchedules.History historyEntry =
          createHistoryEntry(
              schedules, pricingAction, campaignInventorySchedules, inventory, campaign);
      campaignInventorySchedules.getHistory().add(historyEntry);

      log.debug(
          "Added new history entry with PricingAction {} for CampaignInventorySchedules: {}",
          pricingAction,
          campaignInventorySchedulesId);
    }

    // Save updated CampaignInventorySchedules
    inventorySchedulesRepository.save(campaignInventorySchedules);

    // Change campaign status to NEGOTIATING and reset approvals for impacted scheduleIds
    String campaignId = campaignInventorySchedules.getCampaignId();
    campaignService.changeCampaignStatus(campaignId, Campaign.Status.NEGOTIATING);

    // Remove impacted scheduleIds from approvedScheduleIds and reset approvedBy if needed
    List<String> impactedScheduleIds;
    if (scheduleId != null && !scheduleId.trim().isEmpty()) {
      // Single schedule was updated
      impactedScheduleIds = List.of(scheduleId);
    } else {
      // All schedules were updated
      impactedScheduleIds =
          campaignInventorySchedules.getScheduleIds() != null
              ? campaignInventorySchedules.getScheduleIds()
              : Collections.emptyList();
    }

    if (!impactedScheduleIds.isEmpty()
        && campaignInventorySchedules.getApprovedScheduleIds() != null
        && !campaignInventorySchedules.getApprovedScheduleIds().isEmpty()) {
      removeApprovedScheduleIds(impactedScheduleIds, campaignInventorySchedules);
      inventorySchedulesRepository.save(campaignInventorySchedules);
    }

    log.info(
        "Successfully updated discount for CampaignInventorySchedules: {} with {} schedules",
        campaignInventorySchedulesId,
        schedules.size());
  }

  private static double getDiscountPercentage(
      Double proposedPrice, String scheduleId, Double scheduleCurrentPrice) {
    if (scheduleCurrentPrice == null || scheduleCurrentPrice <= 0) {
      throw new IllegalArgumentException(
          "Schedule " + scheduleId + " has invalid currentPrice: " + scheduleCurrentPrice);
    }

    // Validate proposedPrice is less than schedule's currentPrice
    if (proposedPrice >= scheduleCurrentPrice) {
      throw new IllegalArgumentException(
          "Proposed price must be less than schedule's current price. Current: "
              + scheduleCurrentPrice
              + ", Proposed: "
              + proposedPrice);
    }

    // Calculate discount for this schedule
    double scheduleDiscount = scheduleCurrentPrice - proposedPrice;

    // Calculate discount percentage: (scheduleDiscount / scheduleCurrentPrice) * 100
    return (scheduleDiscount / scheduleCurrentPrice) * 100.0;
  }

  /**
   * Removes schedule IDs from approvedScheduleIds and sets approvedBy to null if
   * approvedScheduleIds becomes empty.
   *
   * @param scheduleIdsToRemove List of schedule IDs to remove from approvedScheduleIds
   * @param campaignSchedule CampaignInventorySchedules to update
   */
  private void removeApprovedScheduleIds(
      List<String> scheduleIdsToRemove, CampaignInventorySchedules campaignSchedule) {
    if (scheduleIdsToRemove == null || scheduleIdsToRemove.isEmpty()) {
      return;
    }

    List<String> approvedScheduleIds = campaignSchedule.getApprovedScheduleIds();
    if (approvedScheduleIds == null || approvedScheduleIds.isEmpty()) {
      return;
    }

    // Remove the schedule IDs from approvedScheduleIds
    approvedScheduleIds.removeAll(scheduleIdsToRemove);

    // Set approvedBy to null if approvedScheduleIds becomes empty
    if (approvedScheduleIds.isEmpty()) {
      campaignSchedule.setApprovedBy(null);
    }
  }

  /**
   * Resets history and approvals for a CampaignInventorySchedules when schedules are modified.
   * Clears all existing history, creates a new RATE_CARD entry with 0.0
   * effectiveDiscountPercentage, and resets approvedScheduleIds and approvedBy to null.
   *
   * @param campaignSchedule CampaignInventorySchedules to reset
   * @param schedules List of schedules for the CampaignInventorySchedules
   * @param inventory Inventory entity
   * @param campaign Campaign entity
   */
  private void resetHistoryAndApprovals(
      CampaignInventorySchedules campaignSchedule,
      List<Schedule> schedules,
      Inventory inventory,
      Campaign campaign) {
    log.debug(
        "Resetting history and approvals for CampaignInventorySchedules: {}",
        campaignSchedule.getId());

    // Clear all existing history
    campaignSchedule.setHistory(new ArrayList<>());

    // Create new RATE_CARD entry with 0.0 effectiveDiscountPercentage
    CampaignInventorySchedules.History rateCardHistory =
        createHistoryEntry(
            schedules, PricingAction.RATE_CARD, campaignSchedule, inventory, campaign);
    campaignSchedule.getHistory().add(rateCardHistory);

    // Reset approvals
    campaignSchedule.setApprovedScheduleIds(null);
    campaignSchedule.setApprovedBy(null);

    log.debug(
        "History reset complete for CampaignInventorySchedules: {} with RATE_CARD entry",
        campaignSchedule.getId());
  }
}
