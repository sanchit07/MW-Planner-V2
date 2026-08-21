package com.mw.planner.service;

import static com.mw.planner.dto.InventoryResponseDTO.LocationDTO;
import static com.mw.planner.dto.InventoryResponseDTO.LocationDTO.LocationCoordinatesDTO;
import static com.mw.planner.dto.InventoryResponseDTO.LocationDTO.LocationCoordinatesDTO.CoordinatePair;

import com.mw.planner.domain.*;
import com.mw.planner.dto.*;
import com.mw.planner.dto.InventoryResponseDTO.LocationDTO;
import com.mw.planner.dto.InventoryResponseDTO.LocationDTO.LocationCoordinatesDTO;
import com.mw.planner.dto.InventoryResponseDTO.LocationDTO.LocationCoordinatesDTO.CoordinatePair;
import com.mw.planner.enums.ProgrammaticSupport;
import com.mw.planner.exception.campaign.CampaignInventorySchedulesNotFoundException;
import com.mw.planner.exception.campaign.CampaignNotFoundException;
import com.mw.planner.exception.inventory.InventoryNotFoundException;
import com.mw.planner.repository.*;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for inventory operations including filtering and performance metrics calculation. */
@Service
@Slf4j
public class InventoryService {

  private final String DEFAULT_THUMBNAIL_URL =
      "https://planner-stg.movingwalls.com/img/MW-logo-trans_1754045676555.png";

  private final InventoryRepository inventoryRepository;
  private final InventoryRepositoryCustom inventoryRepositoryCustom;
  private final CampaignService campaignService;
  private final CampaignInventorySchedulesService campaignInventorySchedulesService;
  private final CompanyService companyService;
  private final MwMeasureService mwMeasureService;
  private final UserService userService;
  private final VenuesService venuesService;

  public InventoryService(
      InventoryRepository inventoryRepository,
      InventoryRepositoryCustom inventoryRepositoryCustom,
      CampaignService campaignService,
      @Lazy CampaignInventorySchedulesService campaignInventorySchedulesService,
      @Lazy CompanyService companyService,
      MwMeasureService mwMeasureService,
      UserService userService,
      VenuesService venuesService) {
    this.inventoryRepository = inventoryRepository;
    this.inventoryRepositoryCustom = inventoryRepositoryCustom;
    this.campaignService = campaignService;
    this.campaignInventorySchedulesService = campaignInventorySchedulesService;
    this.companyService = companyService;
    this.userService = userService;
    this.mwMeasureService = mwMeasureService;
    this.venuesService = venuesService;
  }

  /** Get selected inventory IDs for a campaign */
  private List<String> getSelectedInventoryIds(String campaignId) {
    if (campaignId == null) {
      return new ArrayList<>();
    }

    return campaignInventorySchedulesService.findByCampaignId(campaignId).stream()
        .map(CampaignInventorySchedules::getInventoryId)
        .collect(Collectors.toList());
  }

  /** Get inventory ids of media owner in the campaign */
  private List<String> getSelectedMediaOwnerInventoryIds(String campaignId, String mediaOwnerId) {
    if (campaignId == null || mediaOwnerId == null) {
      return new ArrayList<>();
    }

    return campaignInventorySchedulesService
        .findByCampaignIdAndMediaOwnerId(campaignId, mediaOwnerId)
        .stream()
        .map(CampaignInventorySchedules::getInventoryId)
        .collect(Collectors.toList());
  }

  /** Get inventory ids of a set of media owners in the campaign */
  private List<String> getSelectedMediaOwnerInventoryIds(
      String campaignId, List<String> mediaOwnerIds) {
    if (campaignId == null || mediaOwnerIds == null || mediaOwnerIds.isEmpty()) {
      return new ArrayList<>();
    }

    return campaignInventorySchedulesService
        .findByCampaignIdAndMediaOwnerIdIn(campaignId, mediaOwnerIds)
        .stream()
        .map(CampaignInventorySchedules::getInventoryId)
        .collect(Collectors.toList());
  }

  /**
   * Resolve the selected inventory IDs for a campaign based on the current user's company: media
   * owners with company access only see their own inventories, everyone else sees all.
   */
  private List<String> resolveSelectedInventoryIds(Campaign campaign) {
    String actingCompanyId = userService.getActingCompanyId();
    // Any non-owner acting company (shared media owner, media owner with schedules) only
    // sees its own inventories; the full set is reserved for the owning company and
    // global admins. Never fall back to "all" for an unrecognized company.
    if (actingCompanyId != null
        && !actingCompanyId.equals(campaign.getCompanyId())
        && !userService.isCurrentUserGlobalAdmin()) {
      return getSelectedMediaOwnerInventoryIds(campaign.getId(), actingCompanyId);
    }
    return getSelectedInventoryIds(campaign.getId());
  }

  /**
   * Resolve the selected inventory IDs for a campaign, optionally filtered by a set of media
   * owners. When the request supplies a non-empty mediaOwnerIds list, only inventories belonging to
   * those media owners are returned (direct filter). Otherwise behavior is identical to {@link
   * #resolveSelectedInventoryIds(Campaign)}.
   */
  private List<String> resolveSelectedInventoryIds(
      Campaign campaign, MediaOwnerFilterRequestDTO request) {
    if (request == null
        || request.getMediaOwnerIds() == null
        || request.getMediaOwnerIds().isEmpty()) {
      return resolveSelectedInventoryIds(campaign);
    }
    // Arbitrary media-owner filters are reserved for the owning company and global admins.
    // A non-owner acting company (shared/media-owner participant) may only filter to itself —
    // otherwise a client-supplied mediaOwnerIds list would expose other owners' slices.
    String actingCompanyId = userService.getActingCompanyId();
    if (actingCompanyId != null
        && !actingCompanyId.equals(campaign.getCompanyId())
        && !userService.isCurrentUserGlobalAdmin()
        && !(request.getMediaOwnerIds().size() == 1
            && request.getMediaOwnerIds().contains(actingCompanyId))) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Media-owner filters are limited to your own company");
    }
    return getSelectedMediaOwnerInventoryIds(campaign.getId(), request.getMediaOwnerIds());
  }

  /**
   * Get selected inventories for a campaign with optional name and inventoryType filtering and
   * pagination. Uses simple Spring Data query methods - no complex aggregation needed!
   *
   * @param campaignId Campaign ID to get selected inventories for
   * @param name Optional name filter (case-insensitive partial match)
   * @param inventoryType Optional inventoryType filter (String)
   * @param pageable Pagination and sorting information
   * @return Page of selected campaign inventories
   */
  public Page<CampaignInventoryFilterResponseDTO> getSelectedInventories(
      String campaignId, String name, String inventoryType, Pageable pageable) {
    return getSelectedInventories(campaignId, name, inventoryType, pageable, null);
  }

  /**
   * Get selected inventories for a campaign, optionally filtered to a set of media owners. When
   * {@code request.mediaOwnerIds} is null/empty this behaves exactly like {@link
   * #getSelectedInventories(String, String, String, Pageable)}.
   *
   * @param request Optional media-owner filter (may be null)
   */
  public Page<CampaignInventoryFilterResponseDTO> getSelectedInventories(
      String campaignId,
      String name,
      String inventoryType,
      Pageable pageable,
      MediaOwnerFilterRequestDTO request) {

    log.info(
        "Getting selected inventories for campaignId: {} with name filter: {}, inventoryType filter: {}",
        campaignId,
        name,
        inventoryType);

    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);

    // Fetch selected inventory IDs
    List<String> selectedIds = resolveSelectedInventoryIds(campaign, request);

    if (selectedIds.isEmpty()) {
      log.info("No selected inventories found for campaignId: {}", campaignId);
      return new PageImpl<>(new ArrayList<>(), pageable, 0);
    }

    // Use simple repository query - Spring Data handles everything!
    Page<Inventory> inventoryPage;
    boolean hasNameFilter = name != null && !name.trim().isEmpty();
    boolean hasTypeFilter = inventoryType != null;

    if (hasNameFilter && hasTypeFilter) {
      inventoryPage =
          inventoryRepository.findByIdInAndNameContainingAndType(
              selectedIds, name, inventoryType, pageable);
    } else if (hasNameFilter) {
      inventoryPage = inventoryRepository.findByIdInAndNameContaining(selectedIds, name, pageable);
    } else if (hasTypeFilter) {
      inventoryPage = inventoryRepository.findByIdInAndType(selectedIds, inventoryType, pageable);
    } else {
      inventoryPage = inventoryRepository.findByIdIn(selectedIds, pageable);
    }

    // Convert to DTOs
    List<CampaignInventoryFilterResponseDTO> responseDTOs =
        inventoryPage.getContent().stream()
            .map(inv -> convertToFilterResponseDTOWithSelection(inv, campaignId, true, true))
            .collect(Collectors.toList());

    log.info(
        "Returning page {} with {} inventories out of {} total",
        pageable.getPageNumber(),
        responseDTOs.size(),
        inventoryPage.getTotalElements());

    return new PageImpl<>(responseDTOs, pageable, inventoryPage.getTotalElements());
  }

  /**
   * Get all selected inventories for a campaign as slim summaries. No pagination or filtering —
   * every selected inventory record is returned.
   *
   * @param campaignId Campaign ID to get selected inventories for
   * @return List of selected inventory summaries
   */
  public List<SelectedInventorySummaryResponseDTO> getAllSelectedInventories(String campaignId) {
    return getAllSelectedInventories(campaignId, null);
  }

  /**
   * Get all selected inventories for a campaign as slim summaries, optionally filtered to a set of
   * media owners. When {@code request.mediaOwnerIds} is null/empty this behaves exactly like {@link
   * #getAllSelectedInventories(String)}.
   *
   * @param request Optional media-owner filter (may be null)
   */
  public List<SelectedInventorySummaryResponseDTO> getAllSelectedInventories(
      String campaignId, MediaOwnerFilterRequestDTO request) {
    log.info("Getting all selected inventories for campaignId: {}", campaignId);

    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);
    List<String> selectedIds = resolveSelectedInventoryIds(campaign, request);

    if (selectedIds.isEmpty()) {
      log.info("No selected inventories found for campaignId: {}", campaignId);
      return List.of();
    }

    return inventoryRepository.findAllById(selectedIds).stream()
        .map(
            inventory -> {
              ScheduleContext scheduleContext = fetchScheduleContext(campaignId, inventory.getId());
              return SelectedInventorySummaryResponseDTO.builder()
                  .inventoryId(inventory.getInventoryId())
                  .referenceId(inventory.getReferenceId())
                  .performance(
                      calculatePerformanceMetrics(
                          inventory,
                          campaign,
                          scheduleContext.schedule(),
                          scheduleContext.scheduleEntities()))
                  .build();
            })
        .toList();
  }

  /**
   * Restrictive filters: when any of these are set, only inventories matching the filter are
   * returned (selected matching first, then non-selected matching). Other filters (location, tags,
   * formats, etc.) do not restrict selected items — all selected first, then filtered.
   */
  private static boolean hasRestrictiveFilters(CampaignInventoryFilterDTO filter) {
    if (filter == null) return false;
    return (filter.getName() != null && !filter.getName().trim().isEmpty())
        || (filter.getMediaOwnerIds() != null && !filter.getMediaOwnerIds().isEmpty())
        || (filter.getInventoryTypes() != null && !filter.getInventoryTypes().isEmpty())
        || (filter.getSizes() != null && !filter.getSizes().isEmpty())
        || (filter.getBookingMode() != null && !filter.getBookingMode().isEmpty())
        || (filter.getProgrammaticSupport() != null
            && filter.getProgrammaticSupport() != ProgrammaticSupport.ALL)
        || (filter.getDealTypes() != null && !filter.getDealTypes().isEmpty());
  }

  /**
   * Enriches the filter with venueTypeIdFilter from campaign targeting if the frontend did not
   * explicitly provide one. Slugs (e.g. "retail-mall-food-court") are resolved to OpenOOH
   * enumerationIds using the cached venue slug-to-id map, matched against venueTypeIds on
   * inventory.
   */
  private CampaignInventoryFilterDTO enrichFilterWithCampaignVenueTypes(
      CampaignInventoryFilterDTO filter, String campaignId) {
    if (campaignId == null) return filter;
    if (filter != null
        && (filter.getVenueTypeIdFilter() != null || filter.getVenueTypeFilter() != null))
      return filter;

    try {
      Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);
      if (campaign.getTargeting() == null || campaign.getTargeting().getVenueTypes() == null) {
        return filter;
      }

      Campaign.Targeting.VenueTypes venueTypes = campaign.getTargeting().getVenueTypes();
      boolean hasDigital =
          venueTypes.getDigitalOoh() != null && !venueTypes.getDigitalOoh().isEmpty();
      boolean hasClassic =
          venueTypes.getClassicOoh() != null && !venueTypes.getClassicOoh().isEmpty();

      if (!hasDigital && !hasClassic) return filter;

      Map<String, String> slugToId = venuesService.getVenueSlugToIdMap();

      List<String> digitalIds =
          hasDigital
              ? venueTypes.getDigitalOoh().stream()
                  .map(slug -> slugToId.get(slug))
                  .filter(java.util.Objects::nonNull)
                  .collect(Collectors.toList())
              : null;

      List<String> classicIds =
          hasClassic
              ? venueTypes.getClassicOoh().stream()
                  .map(slug -> slugToId.get(slug))
                  .filter(java.util.Objects::nonNull)
                  .collect(Collectors.toList())
              : null;

      boolean hasDigitalIds = digitalIds != null && !digitalIds.isEmpty();
      boolean hasClassicIds = classicIds != null && !classicIds.isEmpty();
      if (!hasDigitalIds && !hasClassicIds) return filter;

      CampaignInventoryFilterDTO.VenueTypeIdFilter venueTypeIdFilter =
          CampaignInventoryFilterDTO.VenueTypeIdFilter.builder()
              .digitalOoh(hasDigitalIds ? digitalIds : null)
              .classicOoh(hasClassicIds ? classicIds : null)
              .build();

      if (filter == null) {
        return CampaignInventoryFilterDTO.builder().venueTypeIdFilter(venueTypeIdFilter).build();
      }
      return filter.toBuilder().venueTypeIdFilter(venueTypeIdFilter).build();

    } catch (Exception e) {
      log.warn(
          "Failed to enrich filter with campaign venueTypes for campaignId {}: {}",
          campaignId,
          e.getMessage());
      return filter;
    }
  }

  /** Filter inventories based on comprehensive criteria with seamless pagination. */
  public Page<CampaignInventoryFilterResponseDTO> filterInventories(
      CampaignInventoryFilterDTO filter, String campaignId, Pageable pageable) {

    // Participation guard: unrelated switched companies must not observe this
    // campaign's selection state through the filter route (404, like all reads).
    Campaign campaign =
        campaignId != null ? campaignService.findByIdForCurrentMode(campaignId) : null;

    filter = enrichFilterWithCampaignVenueTypes(filter, campaignId);
    log.info("Filtering inventories with criteria: {}", filter);

    // Non-owner participants only see their own media-owner slice marked as selected,
    // consistent with the selected-inventory endpoints.
    List<String> selectedIds =
        campaign != null
            ? resolveSelectedInventoryIds(campaign)
            : getSelectedInventoryIds(campaignId);
    int pageSize = pageable.getPageSize();
    int startIndex = pageable.getPageNumber() * pageSize;
    int endIndex = startIndex + pageSize;

    if (hasRestrictiveFilters(filter)) {
      log.info(
          "Inventory filtering with restrictive filters applied for campaignId: {}", campaignId);
      return filterInventoriesRestrictive(
          filter, campaignId, pageable, selectedIds, pageSize, startIndex, endIndex);
    }
    log.info(
        "Inventory filtering without restrictive filters applied for campaignId: {}", campaignId);
    return filterInventoriesSelectedFirst(
        filter, campaignId, pageable, selectedIds, pageSize, startIndex, endIndex);
  }

  /**
   * When restrictive filters (name, mediaOwnerIds, inventoryTypes, sizes, bookingMode) are used:
   * return only inventories that match the filter — selected matching first, then non-selected
   * matching.
   */
  private Page<CampaignInventoryFilterResponseDTO> filterInventoriesRestrictive(
      CampaignInventoryFilterDTO filter,
      String campaignId,
      Pageable pageable,
      List<String> selectedIds,
      int pageSize,
      int startIndex,
      int endIndex) {

    List<String> selectedMatchingIds;
    if (selectedIds.isEmpty()) {
      selectedMatchingIds = List.of();
    } else {
      List<Inventory> compliantSelected =
          inventoryRepositoryCustom.findInventoriesByIdsWithComplianceCheck(selectedIds, filter);
      Set<String> compliantIds =
          compliantSelected.stream().map(Inventory::getId).collect(Collectors.toSet());
      selectedMatchingIds =
          selectedIds.stream().filter(compliantIds::contains).collect(Collectors.toList());
    }
    int totalSelectedMatching = selectedMatchingIds.size();

    List<CampaignInventoryFilterResponseDTO> results = new ArrayList<>();

    if (startIndex < totalSelectedMatching) {
      log.info("Inside selected matching inventories for campaignId: {}", campaignId);
      int selectedEnd = Math.min(endIndex, totalSelectedMatching);
      List<String> pageSelectedIds = selectedMatchingIds.subList(startIndex, selectedEnd);
      List<Inventory> selectedInventories = inventoryRepository.findAllById(pageSelectedIds);
      results.addAll(
          selectedInventories.stream()
              .map(inv -> convertToFilterResponseDTOWithSelection(inv, campaignId, true, true))
              .toList());
    }

    int remainingSlots = pageSize - results.size();
    log.info(">>>> Remaining slots after selected matching: {}", remainingSlots);
    if (remainingSlots > 0) {
      int filteredOffset = Math.max(0, startIndex - totalSelectedMatching);
      int filteredPageNumber = filteredOffset / pageSize;
      int skipWithinPage = filteredOffset % pageSize;
      long startTime = System.nanoTime();
      Page<Inventory> filteredPage =
          getFilteredInventoriesExcludingIds(
              filter,
              selectedIds,
              PageRequest.of(filteredPageNumber, pageSize, pageable.getSort()));
      double elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
      log.info("Filtered inventories query took {} seconds", elapsedSeconds);
      List<Inventory> filteredInventories =
          filteredPage.getContent().stream().skip(skipWithinPage).limit(remainingSlots).toList();
      results.addAll(
          filteredInventories.stream()
              .map(inv -> convertToFilterResponseDTOWithSelection(inv, campaignId, false, true))
              .toList());
    }

    long startTime = System.nanoTime();
    long total =
        totalSelectedMatching + getTotalFilteredInventoriesExcludingIds(filter, selectedIds);
    double elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
    log.info("Total selected matching inventories query took {} seconds", elapsedSeconds);
    return new PageImpl<>(results, pageable, total);
  }

  /**
   * When only non-restrictive filters are used: return all selected first (with compliance), then
   * fill with non-selected that match the filter.
   */
  private Page<CampaignInventoryFilterResponseDTO> filterInventoriesSelectedFirst(
      CampaignInventoryFilterDTO filter,
      String campaignId,
      Pageable pageable,
      List<String> selectedIds,
      int pageSize,
      int startIndex,
      int endIndex) {

    int totalSelected = selectedIds.size();
    List<CampaignInventoryFilterResponseDTO> results = new ArrayList<>();

    if (startIndex < totalSelected) {
      int selectedEnd = Math.min(endIndex, totalSelected);
      List<String> pageSelectedIds = selectedIds.subList(startIndex, selectedEnd);
      List<Inventory> selectedInventories = inventoryRepository.findAllById(pageSelectedIds);
      List<Inventory> compliantSelected =
          inventoryRepositoryCustom.findInventoriesByIdsWithComplianceCheck(
              pageSelectedIds, filter);
      Set<String> compliantIds =
          compliantSelected.stream().map(Inventory::getId).collect(Collectors.toSet());
      results.addAll(
          selectedInventories.stream()
              .map(
                  inv ->
                      convertToFilterResponseDTOWithSelection(
                          inv, campaignId, true, compliantIds.contains(inv.getId())))
              .toList());
    }

    int remainingSlots = pageSize - results.size();
    if (remainingSlots > 0) {
      int filteredOffset = Math.max(0, startIndex - totalSelected);
      int filteredPageNumber = filteredOffset / pageSize;
      int skipWithinPage = filteredOffset % pageSize;
      Page<Inventory> filteredPage =
          getFilteredInventoriesExcludingIds(
              filter,
              selectedIds,
              PageRequest.of(filteredPageNumber, pageSize, pageable.getSort()));
      List<Inventory> filteredInventories =
          filteredPage.getContent().stream().skip(skipWithinPage).limit(remainingSlots).toList();
      results.addAll(
          filteredInventories.stream()
              .map(inv -> convertToFilterResponseDTOWithSelection(inv, campaignId, false, true))
              .toList());
    }

    long total = totalSelected + getTotalFilteredInventoriesExcludingIds(filter, selectedIds);
    return new PageImpl<>(results, pageable, total);
  }

  /**
   * Get all inventories matching filter criteria optimized for bulk operations. This method only
   * fetches the required fields (id, type, and specifications) to reduce data transfer and improve
   * performance.
   *
   * @param filter Filter criteria
   * @return List of all inventories matching the filter
   */
  public List<Inventory> getInventoriesWithFiltersForBulkOperation(
      CampaignInventoryFilterDTO filter) {
    log.debug("Getting all inventories with filter criteria for bulk operation");

    // If filter is null, create an empty filter
    if (filter == null) {
      filter = new CampaignInventoryFilterDTO();
    }

    // Use optimized repository method
    return inventoryRepositoryCustom.findInventoriesWithFiltersForBulkOperation(filter);
  }

  /** Get filtered inventories excluding specific IDs */
  private Page<Inventory> getFilteredInventoriesExcludingIds(
      CampaignInventoryFilterDTO filter, List<String> excludeIds, Pageable pageable) {
    // Use toBuilder() to create a copy with exclusion IDs
    CampaignInventoryFilterDTO filterCopy =
        filter.toBuilder()
            .excludeInventoryIds(excludeIds) // Pass exclusion IDs
            .build();

    return inventoryRepositoryCustom.findInventoriesWithFilters(filterCopy, pageable);
  }

  /** Get total count of filtered inventories excluding specific IDs */
  private long getTotalFilteredInventoriesExcludingIds(
      CampaignInventoryFilterDTO filter, List<String> excludeIds) {
    // This is a simplified implementation - in a real scenario, you might want to optimize this
    // by creating a separate repository method that excludes IDs in the query
    // Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE);
    // return getFilteredInventoriesExcludingIds(filter, excludeIds, pageable).getTotalElements();
    CampaignInventoryFilterDTO filterCopy =
        filter.toBuilder()
            .excludeInventoryIds(excludeIds) // Pass exclusion IDs
            .build();

    return inventoryRepositoryCustom.countInventoriesWithFilters(filterCopy);
  }

  /**
   * Convert Inventory to CampaignInventoryFilterResponseDTO with selection and compliance status
   */
  private CampaignInventoryFilterResponseDTO convertToFilterResponseDTOWithSelection(
      Inventory inventory, String campaignId, boolean isSelected, boolean isCompliant) {
    CampaignInventoryFilterResponseDTO response = convertToFilterResponseDTO(inventory, campaignId);

    // Override selection and compliance status
    response.getDetail().setIsSelected(isSelected);
    response.getDetail().setIsCompliant(isCompliant);

    return response;
  }

  @Cacheable(value = "inventories", key = "#id")
  public Inventory getById(String id) {
    return inventoryRepository.findById(id).orElseThrow(() -> new InventoryNotFoundException(id));
  }

  /** Check if campaign exists by ID */
  public boolean existsById(String id) {
    return inventoryRepository.existsById(id);
  }

  public Optional<Inventory> findByInventoryId(String inventoryId) {
    return inventoryRepository.findByInventoryId(inventoryId);
  }

  public Optional<Inventory> findByExternalId(String externalId) {
    return inventoryRepository.findFirstByExternalId(externalId);
  }

  public List<Inventory> findByExternalIdIn(List<String> externalIds) {
    return inventoryRepository.findByExternalIdIn(externalIds);
  }

  public Optional<Inventory> findByReferenceId(String referenceId) {
    return inventoryRepository.findFirstByReferenceId(referenceId);
  }

  public String getMediaOwnerIdById(String id) {
    try {
      Document response = inventoryRepository.findMediaOwnerIdById(id);
      return response.getString("mediaOwnerId");
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Find all inventories by IDs in a single bulk query. Optimized for performance.
   *
   * @param ids List of inventory IDs
   * @return List of found inventories
   */
  public List<Inventory> findAllByIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return new ArrayList<>();
    }
    return inventoryRepository.findAllById(ids);
  }

  /**
   * Find all inventories by reference IDs in a single bulk query. Optimized for performance.
   *
   * @param referenceIds List of reference IDs
   * @return List of found inventories
   */
  public List<Inventory> findAllByReferenceIds(List<String> referenceIds) {
    if (referenceIds == null || referenceIds.isEmpty()) {
      return new ArrayList<>();
    }
    return inventoryRepository.findByReferenceIdIn(referenceIds);
  }

  @Transactional
  @CachePut(value = "inventories", key = "#result.id")
  public Inventory save(Inventory inventory) {
    return inventoryRepository.save(inventory);
  }

  @Transactional
  @CacheEvict(value = "inventories", key = "#id")
  public void deleteById(String id) {
    inventoryRepository.deleteById(id);
  }

  /**
   * Atomically upsert an inventory keyed on its natural key (externalId, else referenceId). Used by
   * the inventory-sync RabbitMQ consumer so concurrent refresh messages for the same inventory
   * cannot create duplicate documents. Evicts the affected id from the "inventories" cache so
   * cached reads are not stale (the direct MongoTemplate upsert bypasses @CachePut).
   */
  @CacheEvict(value = "inventories", key = "#result.id", condition = "#result != null")
  public Inventory upsertByNaturalKey(Inventory inventory) {
    return inventoryRepositoryCustom.upsertByNaturalKey(inventory);
  }

  /** Get inventory by ID with performance metrics */
  public InventoryResponseDTO getInventoryResponseDTOById(String inventoryId) {
    log.info("Getting inventory by ID: {}", inventoryId);

    Inventory inventory = getById(inventoryId);

    return convertToResponseDTO(inventory);
  }

  /** Convert Inventory to InventoryResponseDTO */
  private InventoryResponseDTO convertToResponseDTO(Inventory inventory) {
    return InventoryResponseDTO.builder()
        .id(inventory.getId())
        .name(inventory.getName())
        .referenceId(inventory.getReferenceId())
        .type(inventory.getType())
        .format(inventory.getFormat())
        .environment(inventory.getEnvironment())
        .venueType(inventory.getVenueType())
        .archived(inventory.getArchived())
        .mediaOwnerId(inventory.getMediaOwnerId())
        .mediaOwnerName(inventory.getMediaOwnerName())
        .location(convertLocation(inventory.getLocation()))
        .panels(convertPanels(inventory.getPanels()))
        .thumbnailUrl(inventory.getThumbnailUrl())
        .operatingTimes(convertOperatingTimes(inventory.getOperatingTimes()))
        .sellingTerm(convertSellingTerm(inventory.getSellingTerm()))
        .orientation(inventory.getOrientation())
        .timeZone(inventory.getTimeZone())
        .requiresContentApproval(inventory.getRequiresContentApproval())
        .programmaticDealTypes(inventory.getProgrammaticDealTypes())
        .creativeFormats(convertCreativeFormats(inventory.getCreativeFormats()))
        .prices(convertPrices(inventory.getPrices()))
        .digitalFields(convertDigitalFields(inventory.getDigitalFields()))
        .classicFields(convertClassicFields(inventory.getClassicFields()))
        .transitFields(convertTransitFields(inventory.getTransitFields()))
        .size(inventory.getSize())
        .inventoryCluster(inventory.getInventoryCluster())
        .build();
  }

  /** Convert location to DTO */
  private LocationDTO convertLocation(Inventory.Location location) {
    if (location == null) return null;

    LocationCoordinatesDTO coordinatesDTO = null;
    if (location.getLocationCoordinates() != null) {
      coordinatesDTO = convertCoordinates(location.getLocationCoordinates());
    }

    return LocationDTO.builder()
        .address(location.getAddress())
        .country(location.getCountry())
        .state(location.getState())
        .city(location.getCity())
        .zipCode(location.getZipCode())
        .locationCoordinates(coordinatesDTO)
        .build();
  }

  /** Convert coordinates (GeoJsonPoint or GeoJsonLineString) to DTO */
  @SuppressWarnings("unchecked")
  private LocationCoordinatesDTO convertCoordinates(Object coordinates) {
    if (coordinates == null) return null;

    List<CoordinatePair> coordinatePairs = new ArrayList<>();

    if (coordinates instanceof Map) {
      // Handle Map-based GeoJSON (common when deserialized from MongoDB)
      Map<String, Object> coordMap = (Map<String, Object>) coordinates;
      String type = (String) coordMap.get("type");

      if ("Point".equals(type)) {
        Object coordsObj = coordMap.get("coordinates");
        if (coordsObj instanceof List) {
          List<Object> coordsList = (List<Object>) coordsObj;
          if (coordsList.size() >= 2) {
            Double longitude = getDoubleValue(coordsList.get(0));
            Double latitude = getDoubleValue(coordsList.get(1));
            if (longitude != null && latitude != null) {
              coordinatePairs.add(
                  CoordinatePair.builder().latitude(latitude).longitude(longitude).build());
              return LocationCoordinatesDTO.builder()
                  .type("Point")
                  .coordinates(coordinatePairs)
                  .build();
            }
          }
        }
      } else if ("LineString".equals(type)) {
        Object coordsObj = coordMap.get("coordinates");
        if (coordsObj instanceof List) {
          List<Object> coordsList = (List<Object>) coordsObj;
          for (Object coordItem : coordsList) {
            if (coordItem instanceof List) {
              List<Object> pointCoords = (List<Object>) coordItem;
              if (pointCoords.size() >= 2) {
                Double longitude = getDoubleValue(pointCoords.get(0));
                Double latitude = getDoubleValue(pointCoords.get(1));
                if (longitude != null && latitude != null) {
                  coordinatePairs.add(
                      CoordinatePair.builder().latitude(latitude).longitude(longitude).build());
                }
              }
            }
          }
          if (!coordinatePairs.isEmpty()) {
            return LocationCoordinatesDTO.builder()
                .type("LineString")
                .coordinates(coordinatePairs)
                .build();
          }
        }
      }
    }

    return null;
  }

  /** Helper method to safely extract Double value from Object */
  private Double getDoubleValue(Object value) {
    switch (value) {
      case null -> {
        return null;
      }
      case Number number -> {
        return number.doubleValue();
      }
      case String s -> {
        try {
          return Double.parseDouble(s);
        } catch (NumberFormatException e) {
          log.warn("Failed to parse double value: {}", value);
          return null;
        }
      }
      default -> {}
    }
    return null;
  }

  /** Convert panels to DTO */
  private List<InventoryResponseDTO.PanelDTO> convertPanels(List<Inventory.Panel> panels) {
    if (panels == null) return null;

    return panels.stream()
        .map(
            panel ->
                InventoryResponseDTO.PanelDTO.builder()
                    .pixelWidth(panel.getPixelWidth())
                    .pixelHeight(panel.getPixelHeight())
                    .physicalWidth(panel.getPhysicalWidth())
                    .physicalHeight(panel.getPhysicalHeight())
                    .panelCount(panel.getPanelCount())
                    .unit(panel.getUnit())
                    .size(panel.getSize())
                    .build())
        .collect(Collectors.toList());
  }

  /** Convert operating times to DTO */
  private Map<Inventory.Weekday, List<InventoryResponseDTO.OperatingTimeDTO>> convertOperatingTimes(
      Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimes) {
    if (operatingTimes == null) return null;

    return operatingTimes.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry ->
                    entry.getValue().stream()
                        .map(
                            ot ->
                                InventoryResponseDTO.OperatingTimeDTO.builder()
                                    .start(ot.getStart())
                                    .end(ot.getEnd())
                                    .build())
                        .collect(Collectors.toList())));
  }

  /** Convert selling term to DTO */
  private InventoryResponseDTO.SellingTermDTO convertSellingTerm(
      Inventory.SellingTerm sellingTerm) {
    if (sellingTerm == null) return null;

    Map<String, InventoryResponseDTO.DayPartGroupDTO> dayPartGroups = null;
    if (sellingTerm.getDayPartGroups() != null) {
      dayPartGroups =
          sellingTerm.getDayPartGroups().entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey,
                      entry ->
                          InventoryResponseDTO.DayPartGroupDTO.builder()
                              .start(entry.getValue().getStart())
                              .end(entry.getValue().getEnd())
                              .build()));
    }

    return InventoryResponseDTO.SellingTermDTO.builder()
        .leadDays(sellingTerm.getLeadDays())
        .minHours(sellingTerm.getMinHours())
        .minDays(sellingTerm.getMinDays())
        .dayPartGroups(dayPartGroups)
        .build();
  }

  /** Convert creative formats to DTO */
  private List<InventoryResponseDTO.CreativeFormatDTO> convertCreativeFormats(
      List<Inventory.CreativeFormat> creativeFormats) {
    if (creativeFormats == null) return null;

    return creativeFormats.stream()
        .map(
            cf ->
                InventoryResponseDTO.CreativeFormatDTO.builder()
                    .format(cf.getFormat())
                    .creativeType(cf.getCreativeType())
                    .build())
        .collect(Collectors.toList());
  }

  /** Convert prices to DTO */
  private List<InventoryResponseDTO.PriceDTO> convertPrices(List<Inventory.Price> prices) {
    if (prices == null) return null;

    return prices.stream()
        .map(
            price ->
                InventoryResponseDTO.PriceDTO.builder()
                    .cpm(price.getCpm())
                    .spot(price.getSpot())
                    .build())
        .collect(Collectors.toList());
  }

  /** Convert digital fields to DTO */
  private InventoryResponseDTO.DigitalFieldsDTO convertDigitalFields(
      Inventory.DigitalFields digitalFields) {
    if (digitalFields == null) return null;

    return InventoryResponseDTO.DigitalFieldsDTO.builder()
        .playerSoftwareId(digitalFields.getPlayerSoftwareId())
        .playerSoftwareName(digitalFields.getPlayerSoftwareName())
        .playerCount(digitalFields.getPlayerCount())
        .spotDuration(digitalFields.getSpotDuration())
        .spotsPerLoop(digitalFields.getSpotsPerLoop())
        .bookingMode(digitalFields.getBookingMode())
        .build();
  }

  /** Convert classic fields to DTO */
  private InventoryResponseDTO.ClassicFieldsDTO convertClassicFields(
      Inventory.ClassicFields classicFields) {
    if (classicFields == null) return null;

    return InventoryResponseDTO.ClassicFieldsDTO.builder()
        .illuminated(classicFields.getIlluminated())
        .build();
  }

  /** Convert transit fields to DTO */
  private InventoryResponseDTO.TransitFieldsDTO convertTransitFields(
      Inventory.TransitFields transitFields) {
    if (transitFields == null) return null;

    return InventoryResponseDTO.TransitFieldsDTO.builder()
        .routeId(transitFields.getRouteId())
        .routeName(transitFields.getRouteName())
        .build();
  }

  /** Convert Inventory to CampaignInventoryFilterResponseDTO */
  /** Convert Inventory to CampaignInventoryFilterResponseDTO */
  private CampaignInventoryFilterResponseDTO convertToFilterResponseDTO(
      Inventory inventory, String campaignId) {
    // Get campaign for duration calculation
    Campaign campaign = null;
    if (campaignId != null) {
      try {
        campaign = campaignService.findByIdForCurrentMode(campaignId);
      } catch (CampaignNotFoundException e) {
        log.warn("Campaign not found with ID: {}", campaignId, e);
      }
    }

    // Flags representing campaign selection and approval status
    boolean isSelected = false;
    boolean isAccepted = false;

    // Calculated share of voice for the inventory
    Double sov = null;

    // Campaign–inventory schedule mapping
    CampaignInventorySchedules schedule = null;

    // Resolved Schedule entities (reused for cost calculation to avoid extra DB call)
    List<Schedule> scheduleEntities = List.of();

    // Inventory-level schedule DTOs
    List<InventorySchedulesResponseDTO.ScheduleDTO> inventorySchedules = List.of();

    // Fetch campaign-specific inventory schedule configuration and its schedule entities
    ScheduleContext scheduleContext = fetchScheduleContext(campaignId, inventory.getId());
    schedule = scheduleContext.schedule();
    scheduleEntities = scheduleContext.scheduleEntities();

    if (schedule != null) {
      // Convert schedule entities to response DTOs (classification-aware SOV per schedule)
      inventorySchedules =
          campaignInventorySchedulesService.convertSchedulesToDTO(scheduleEntities, inventory);

      // Calculate share of voice: plannedSot-weighted average of each schedule's own SOV
      sov = calculateSov(inventorySchedules);

      // Inventory is part of the campaign if schedule exists
      isSelected = true;

      // Inventory is accepted if approved by any user
      isAccepted = schedule.getApprovedBy() != null;
    }

    // Calculate performance metrics
    CampaignInventoryFilterResponseDTO.Performance performance =
        calculatePerformanceMetrics(inventory, campaign, schedule, scheduleEntities);

    // FIXME: Thumbnail URL
    String thumbnailUrl =
        (inventory.getThumbnailUrl() != null) ? inventory.getThumbnailUrl() : DEFAULT_THUMBNAIL_URL;

    // Build detail
    CampaignInventoryFilterResponseDTO.Detail detail =
        CampaignInventoryFilterResponseDTO.Detail.builder()
            .id(inventory.getId())
            .name(inventory.getName())
            .externalId(inventory.getExternalId())
            .referenceId(inventory.getReferenceId())
            .mediaOwnerId(inventory.getMediaOwnerId())
            .mediaOwnerName(inventory.getMediaOwnerName())
            // .inventoryType(inventory.getType()) FIXME: this is temporary change to support a
            // breaking change on inventory
            .inventoryType(inventory.getClassification())
            .environment(inventory.getEnvironment())
            .venueType(inventory.getVenueType())
            .thumbnail(thumbnailUrl)
            .format(inventory.getFormat())
            .panels(convertPanels(inventory.getPanels()))
            .bookingMode(
                inventory.getDigitalFields() != null
                    ? inventory.getDigitalFields().getBookingMode()
                    : null)
            .execution("placeholder") // Placeholder as requested
            .screens(
                inventory.getDigitalFields() != null
                    ? inventory.getDigitalFields().getPlayerCount()
                    : null)
            .sov(sov)
            .isSelected(isSelected)
            .isAccepted(isAccepted)
            .isCompliant(true) // Default to true, will be overridden if needed
            .contentExclusions(inventory.getContentExclusions())
            .creativeFormats(inventory.getCreativeFormats())
            .programmaticDealTypes(inventory.getProgrammaticDealTypes())
            .size(inventory.getSize())
            .inventoryCluster(inventory.getInventoryCluster())
            .sellingTerm(inventory.getSellingTerm())
            .cinemaFields(inventory.getCinemaFields())
            .build();

    // Build location info
    CampaignInventoryFilterResponseDTO.LocationInfo locationInfo =
        CampaignInventoryFilterResponseDTO.LocationInfo.builder()
            .location(convertLocation(inventory.getLocation()))
            .demographics(convertDemographics(inventory))
            .build();

    // Build operations
    CampaignInventoryFilterResponseDTO.Operations operations =
        CampaignInventoryFilterResponseDTO.Operations.builder()
            .operatingTimes(inventory.getOperatingTimes())
            .maintenanceWindow("placeholder") // Placeholder as requested
            .loopSize(extractLoopSize(inventory))
            .slotDuration(
                inventory.getDigitalFields() != null
                    ? inventory.getDigitalFields().getSpotDuration()
                    : null)
            .clientPerLoop(extractClientPerLoop(inventory))
            .cycleTime(extractCycleTime(inventory))
            .build();

    return CampaignInventoryFilterResponseDTO.builder()
        .detail(detail)
        .schedules(inventorySchedules)
        .location(locationInfo)
        .performance(performance)
        .operations(operations)
        .build();
  }

  private Double calculateSov(List<InventorySchedulesResponseDTO.ScheduleDTO> inventorySchedules) {
    // Each schedule's .sov is already classification-aware (see convertSchedulesToDTO); just
    // weight-average them by planned airtime rather than re-deriving from summed totals.
    return CampaignInventorySchedulesService.calculateWeightedSov(
        inventorySchedules.stream().map(InventorySchedulesResponseDTO.ScheduleDTO::getSov).toList(),
        inventorySchedules.stream()
            .map(InventorySchedulesResponseDTO.ScheduleDTO::getPlannedSot)
            .toList());
  }

  /** Campaign–inventory schedule mapping with its resolved schedule entities. */
  private record ScheduleContext(
      CampaignInventorySchedules schedule, List<Schedule> scheduleEntities) {}

  /**
   * Fetch the campaign-specific inventory schedule and its schedule entities. Returns an empty
   * context (null schedule) when the inventory is not selected for the campaign.
   */
  private ScheduleContext fetchScheduleContext(String campaignId, String inventoryId) {
    try {
      CampaignInventorySchedules schedule =
          campaignInventorySchedulesService.findByCampaignIdAndInventoryId(campaignId, inventoryId);

      // Load schedules only when schedule IDs are present
      List<Schedule> scheduleEntities =
          (schedule.getScheduleIds() == null || schedule.getScheduleIds().isEmpty())
              ? List.of()
              : campaignInventorySchedulesService.getSchedulesByIds(schedule.getScheduleIds());

      return new ScheduleContext(schedule, scheduleEntities);
    } catch (CampaignInventorySchedulesNotFoundException ex) {
      // Inventory not selected for the campaign
      return new ScheduleContext(null, List.of());
    }
  }

  /** Calculate performance metrics for inventory with campaign */
  private CampaignInventoryFilterResponseDTO.Performance calculatePerformanceMetrics(
      Inventory inventory,
      Campaign campaign,
      CampaignInventorySchedules schedule,
      List<Schedule> scheduleEntities) {
    Double cpmRate = getCpm(inventory);
    Double spotPrice = getSpotRate(inventory);

    // Calculate estimated cost
    Double estimatedCost = null;
    Double perDayCost = null;
    Long perDayAdPlays = null;
    Long totalAdPlays = null;
    Long estimatedImpression = null;
    Long estimatedReach = null;
    Double estimatedFrequency = null;
    double plannedSot = 0.0;
    double totalSot = 0.0;
    double sov = 0.0;

    try {
      if (campaign != null) {
        // Calculate campaign duration in days
        int duration = CampaignService.calculateDuration(campaign);

        if (schedule != null) {
          // Calculate performance based on selected schedules
          CampaignForecastDTO campaignForecast =
              campaignService.calculateCampaignForecast(campaign, List.of(schedule));
          totalSot = campaignForecast.getTotalSot();
          plannedSot = campaignForecast.getPlannedSot();
          totalAdPlays = campaignForecast.getEstimatedAdPlays();
          perDayAdPlays = totalAdPlays / duration;
          sov = campaignForecast.getSov();
          estimatedImpression = campaignForecast.getEstimatedImpression();
          estimatedReach = campaignForecast.getEstimatedReach();
          estimatedFrequency = campaignForecast.getEstimatedFrequency();

          // Sum raw basePrice across schedules — no custom fees or discounts applied
          boolean hasAnyPrice = scheduleEntities.stream().anyMatch(s -> s.getBasePrice() != null);
          estimatedCost =
              hasAnyPrice
                  ? scheduleEntities.stream()
                      .filter(s -> s.getBasePrice() != null)
                      .mapToDouble(Schedule::getBasePrice)
                      .sum()
                  : null;
          perDayCost = estimatedCost != null ? estimatedCost / duration : null;
        } else {
          Campaign.Goals.GoalType goalType =
              campaign.getGoals() != null ? campaign.getGoals().getGoalType() : null;
          if (inventory.getDigitalFields() != null
              && inventory.getDigitalFields().getSpotDuration() != null
              && inventory.getDigitalFields().getSpotDuration() > 0) {
            Map<String, List<Integer>> bookingMatrix =
                campaignInventorySchedulesService.calculateSimpleBookingMatrix(
                    campaign.getStartDate(), campaign.getEndDate(), inventory);
            long spotPerHour = getLoopsPerHour(inventory);

            // Calculate adPlays: spotsPerHour * total hours in bookingMatrix
            totalAdPlays =
                campaignInventorySchedulesService.calculateAdPlays(spotPerHour, bookingMatrix);
            perDayAdPlays = totalAdPlays / duration;

            // Estimate cost and reach/frequency for the non-selected preview.
            PreviewMetrics metrics =
                estimatePreviewMetrics(
                    inventory, duration, spotPerHour, totalAdPlays, cpmRate, spotPrice, goalType);
            estimatedCost = metrics.cost();
            estimatedImpression = metrics.impressions();
            estimatedReach = metrics.reach();
            estimatedFrequency = metrics.frequency();

            perDayCost = estimatedCost != null ? estimatedCost / duration : null;
          } else {
            // Classic inventory: no booking matrix or ad plays
            if (cpmRate != null) {
              // CPM × impressions from Measure API
              MeasureReachFrequencyResponseDTO rf =
                  getReachFrequencyWithoutDayparts(inventory, duration, 0);
              if (rf != null && rf.getImpressions() != null && rf.getImpressions() > 0) {
                estimatedCost = (cpmRate / 1000.0) * rf.getImpressions();
                estimatedImpression = rf.getImpressions();
                estimatedReach = rf.getReach();
                estimatedFrequency = rf.getFrequency();
                perDayCost = estimatedCost / duration;
              }
            } else {
              // Flat rate fallback: monthly → daily → weekly
              estimatedCost = estimateFlatRateCost(inventory, duration);
              if (estimatedCost != null) {
                perDayCost = estimatedCost / duration;
              }
            }
          }
        }
      }

    } catch (Exception e) {
      log.warn("Error calculating performance metrics: {}", e.getMessage());
    }

    return CampaignInventoryFilterResponseDTO.Performance.builder()
        .currency(campaign != null ? campaign.getCurrency() : null)
        .cpmRate(cpmRate)
        .spotRate(spotPrice)
        .estimatedCost(estimatedCost)
        .perDayCost(perDayCost)
        .perDayAdPlays(perDayAdPlays)
        .totalAdPlays(totalAdPlays)
        .plannedSot(plannedSot > 0.0 ? plannedSot : null)
        .totalSot(totalSot > 0.0 ? totalSot : null)
        .sov(sov > 0.0 ? sov : null)
        .estimatedImpression(
            estimatedImpression != null && estimatedImpression > 0 ? estimatedImpression : null)
        .estimatedReach(estimatedReach != null && estimatedReach > 0 ? estimatedReach : null)
        .estimatedFrequency(
            estimatedFrequency != null && estimatedFrequency > 0 ? estimatedFrequency : null)
        .build();
  }

  /**
   * Estimate cost from spot pricing: {@code spotPrice × totalAdPlays}. Returns {@code null} when
   * either input is unavailable.
   */
  private Double estimateSpotCost(Long totalAdPlays, Double spotPrice) {
    if (spotPrice == null || totalAdPlays == null) {
      return null;
    }
    return totalAdPlays * spotPrice;
  }

  /**
   * Preview performance for a non-selected inventory: estimated cost plus the reach/frequency
   * metrics. Reach/frequency/impressions are only populated on the CPM route (which queries the
   * Measure API); the spot route leaves them null since it computes cost locally from ad plays.
   */
  private record PreviewMetrics(Double cost, Long impressions, Long reach, Double frequency) {
    static PreviewMetrics empty() {
      return new PreviewMetrics(null, null, null, null);
    }
  }

  /**
   * Estimate preview cost and reach/frequency for a non-selected inventory. Pricing route mirrors
   * {@code CampaignInventorySchedulesService.calculateScheduleBasePriceForSchedule} so the preview
   * matches the basePrice computed once the inventory is selected:
   *
   * <ul>
   *   <li>IMPRESSIONS / REACH → CPM × impressions (Measure API call)
   *   <li>SOV / ADPLAYS → spot × ad plays (no API call)
   *   <li>default (null / OTHER / ATTRIBUTION) → spot if available, otherwise CPM fallback
   * </ul>
   */
  private PreviewMetrics estimatePreviewMetrics(
      Inventory inventory,
      int duration,
      long spotPerHour,
      Long totalAdPlays,
      Double cpmRate,
      Double spotPrice,
      Campaign.Goals.GoalType goalType) {

    boolean useCpm;
    if (goalType == Campaign.Goals.GoalType.IMPRESSIONS
        || goalType == Campaign.Goals.GoalType.REACH) {
      useCpm = true;
    } else if (goalType == Campaign.Goals.GoalType.SOV
        || goalType == Campaign.Goals.GoalType.ADPLAYS) {
      useCpm = false;
    } else {
      // Default: prefer spot pricing (no extra Measure API call), CPM only as fallback.
      useCpm = spotPrice == null;
    }

    if (!useCpm) {
      // Spot route: cost computed locally; reach/frequency/impressions intentionally left null.
      return new PreviewMetrics(estimateSpotCost(totalAdPlays, spotPrice), null, null, null);
    }

    if (cpmRate == null) {
      return PreviewMetrics.empty();
    }
    MeasureReachFrequencyResponseDTO rf =
        getReachFrequencyWithoutDayparts(inventory, duration, (int) spotPerHour);
    if (rf == null || rf.getImpressions() == null || rf.getImpressions() <= 0) {
      log.warn("Failed to get impressions from Measure API, estimatedCost will be null");
      return PreviewMetrics.empty();
    }
    double cost = (cpmRate / 1000.0) * rf.getImpressions();
    return new PreviewMetrics(cost, rf.getImpressions(), rf.getReach(), rf.getFrequency());
  }

  /**
   * Get reach &amp; frequency from the Measure API for a single inventory WITHOUT dayparts.
   *
   * @param inventory The inventory to get reach &amp; frequency for
   * @param duration Campaign duration in days
   * @param spotsPerHour Spots per hour for the inventory
   * @return Measure response (reach, frequency, impressions), or null if the API call fails or
   *     returns no impressions
   */
  private MeasureReachFrequencyResponseDTO getReachFrequencyWithoutDayparts(
      Inventory inventory, int duration, int spotsPerHour) {
    try {
      // Build MeasureInventoryDTO without dayparts
      MeasureInventoryDTO measureInventory =
          MeasureInventoryDTO.builder()
              .referenceId(inventory.getReferenceId())
              .type("billboard")
              .spotsPerHour(spotsPerHour)
              .build();

      // Create request with single inventory
      MeasureReachFrequencyRequestDTO request =
          MeasureReachFrequencyRequestDTO.builder()
              .inventories(List.of(measureInventory))
              .duration(duration)
              .build();

      // Call Measure API
      MeasureReachFrequencyResponseDTO response = mwMeasureService.getReachAndFrequency(request);

      if (response != null && response.getImpressions() != null) {
        return response;
      } else {
        log.warn(
            "Measure API returned null or no impressions for inventory {}",
            inventory.getReferenceId());
        return null;
      }

    } catch (Exception e) {
      log.error(
          "Error calling Measure API for inventory {}: {}",
          inventory.getReferenceId(),
          e.getMessage(),
          e);
      return null;
    }
  }

  /**
   * Get impressions from Measure API for a single inventory by calling reach & frequency endpoint.
   *
   * @param inventory The inventory to get impressions for
   * @param bookingMatrix The booking matrix (date -> hours)
   * @param duration Campaign duration in days
   * @param spotsPerHour Spots per hour for the inventory
   * @return Impressions value from the API, or null if API call fails
   */
  private Long getImpressionsFromMeasureApi(
      Inventory inventory,
      Map<String, List<Integer>> bookingMatrix,
      int duration,
      int spotsPerHour) {
    try {
      // Convert bookingMatrix to dayparts
      List<MeasureInventoryDTO.Dayparts> dayparts = convertBookingMatrixToDayparts(bookingMatrix);

      // Build MeasureInventoryDTO
      MeasureInventoryDTO measureInventory =
          MeasureInventoryDTO.builder()
              .referenceId(inventory.getReferenceId())
              .type("billboard")
              .spotsPerHour(spotsPerHour)
              .dayparts(dayparts)
              .build();

      // Create request with single inventory
      MeasureReachFrequencyRequestDTO request =
          MeasureReachFrequencyRequestDTO.builder()
              .inventories(List.of(measureInventory))
              .duration(duration)
              .build();

      // Call Measure API
      MeasureReachFrequencyResponseDTO response = mwMeasureService.getReachAndFrequency(request);

      if (response != null && response.getImpressions() != null) {
        log.debug(
            "Retrieved impressions from Measure API for inventory {}: {}",
            inventory.getReferenceId(),
            response.getImpressions());
        return response.getImpressions();
      } else {
        log.warn(
            "Measure API returned null or no impressions for inventory {}",
            inventory.getReferenceId());
        return null;
      }

    } catch (Exception e) {
      log.error(
          "Error calling Measure API for inventory {}: {}",
          inventory.getReferenceId(),
          e.getMessage(),
          e);
      return null;
    }
  }

  /**
   * Converts bookingMatrix to dayparts format for Measure API. Converts hours from Integer to
   * String format ("00", "01", etc.).
   *
   * @param bookingMatrix The booking matrix (date -> hours)
   * @return List of Dayparts with scheduledDate and scheduledTime
   */
  private List<MeasureInventoryDTO.Dayparts> convertBookingMatrixToDayparts(
      Map<String, List<Integer>> bookingMatrix) {
    if (bookingMatrix == null || bookingMatrix.isEmpty()) {
      return new ArrayList<>();
    }

    List<MeasureInventoryDTO.Dayparts> dayparts = new ArrayList<>();

    bookingMatrix.forEach(
        (date, hours) -> {
          if (hours != null && !hours.isEmpty()) {
            List<String> scheduledTimes =
                hours.stream()
                    .sorted()
                    .map(hour -> String.format("%02d", hour))
                    .collect(Collectors.toList());

            dayparts.add(
                MeasureInventoryDTO.Dayparts.builder()
                    .scheduledDate(date)
                    .scheduledTime(scheduledTimes)
                    .build());
          }
        });

    return dayparts;
  }

  /** Convert demographics information */
  private CampaignInventoryFilterResponseDTO.DemographicsDTO convertDemographics(
      Inventory inventory) {
    // Demographic data is no longer available in inventory messages
    // Return empty demographics DTO
    return CampaignInventoryFilterResponseDTO.DemographicsDTO.builder().build();
  }

  /** Extract loop size from inventory digitalFields */
  private Integer extractLoopSize(Inventory inventory) {
    if (inventory.getDigitalFields() != null) {
      return inventory.getDigitalFields().getLoopDuration();
    }
    return null;
  }

  /** Extract client per loop from inventory digitalFields */
  private Integer extractClientPerLoop(Inventory inventory) {
    if (inventory.getDigitalFields() != null) {
      // Client per loop can be derived from playerCount
      return inventory.getDigitalFields().getSpotsPerLoop();
    }
    return null;
  }

  /** Extract cycle time from inventory digitalFields */
  private Integer extractCycleTime(Inventory inventory) {
    if (inventory.getDigitalFields() != null) {
      // Cycle time can be derived from spotDuration
      return inventory.getDigitalFields().getSpotDuration();
    }
    return null;
  }

  /** Calculate available hours from operating times */
  public static int calculateAvailableHours(Inventory inventory) {
    if (inventory.getOperatingTimes() == null || inventory.getOperatingTimes().isEmpty()) {
      return 0;
    }

    return inventory.getOperatingTimes().values().stream().findFirst().stream()
        .mapToInt(
            operatingTimes ->
                operatingTimes.stream().findFirst().stream()
                    .mapToInt(
                        operatingTime -> {
                          LocalTime start = LocalTime.parse(operatingTime.getStart());
                          LocalTime end = LocalTime.parse(operatingTime.getEnd());

                          return Math.toIntExact(ChronoUnit.HOURS.between(start, end));
                        })
                    .sum())
        .sum();
  }

  public static long getLoopsPerHour(Inventory inventory) {
    try {
      return getSpotsPerHour(inventory) / getSpotsPerLoop(inventory);
    } catch (Exception e) {
      return 0;
    }
  }

  public static long getSpotsPerHour(Inventory inventory) {
    if (inventory.getDigitalFields() == null
        || inventory.getDigitalFields().getSpotDuration() == null
        || inventory.getDigitalFields().getSpotDuration() <= 0) {
      log.warn("Invalid spots per hour for inventoryId: {}", inventory.getId());
      return 0;
    }

    return 3600L / inventory.getDigitalFields().getSpotDuration(); // 3600Sec
  }

  public static long getSpotsPerLoop(Inventory inventory) {
    return inventory.getDigitalFields() != null
            && inventory.getDigitalFields().getSpotsPerLoop() != null
        ? inventory.getDigitalFields().getSpotsPerLoop()
        : 0;
  }

  public static long getSpotDuration(Inventory inventory) {
    return inventory.getDigitalFields() != null
            && inventory.getDigitalFields().getSpotDuration() != null
        ? inventory.getDigitalFields().getSpotDuration()
        : 0;
  }

  public static Double getCpm(Inventory inventory) {
    if (inventory.getPrices() == null) {
      return null;
    }
    for (Inventory.Price price : inventory.getPrices()) {
      if (price != null && price.getCpm() != null) {
        return price.getCpm();
      }
    }
    return null;
  }

  public static Double getMonthlyRate(Inventory inventory) {
    if (inventory.getPrices() == null) {
      return null;
    }
    for (Inventory.Price price : inventory.getPrices()) {
      if (price != null && price.getMonthly() != null) {
        return price.getMonthly();
      }
    }
    return null;
  }

  public static Double getDailyRate(Inventory inventory) {
    if (inventory.getPrices() == null) {
      return null;
    }
    for (Inventory.Price price : inventory.getPrices()) {
      if (price != null && price.getDaily() != null) {
        return price.getDaily();
      }
    }
    return null;
  }

  public static Double getWeeklyRate(Inventory inventory) {
    if (inventory.getPrices() == null) {
      return null;
    }
    for (Inventory.Price price : inventory.getPrices()) {
      if (price != null && price.getWeekly() != null) {
        return price.getWeekly();
      }
    }
    return null;
  }

  /** Estimate cost from duration-based flat rates (monthly → daily → weekly fallback). */
  public static Double estimateFlatRateCost(Inventory inventory, int durationDays) {
    Double monthly = getMonthlyRate(inventory);
    if (monthly != null) {
      return monthly * (durationDays / 30.0);
    }
    Double daily = getDailyRate(inventory);
    if (daily != null) {
      return daily * durationDays;
    }
    Double weekly = getWeeklyRate(inventory);
    if (weekly != null) {
      return weekly * (durationDays / 7.0);
    }
    return null;
  }

  public static Double getSpotRate(Inventory inventory) {
    if (inventory.getPrices() == null) {
      return null;
    }
    for (Inventory.Price price : inventory.getPrices()) {
      if (price != null && price.getSpot() != null) {
        return price.getSpot();
      }
    }
    // IMS-imported inventory stores spot pricing under "cps" (cost per spot); treat it as the
    // spot rate so imported inventory prices identically to seeded inventory.
    for (Inventory.Price price : inventory.getPrices()) {
      if (price != null && price.getCps() != null) {
        return price.getCps();
      }
    }
    return null;
  }

  public List<String> findIdByIdInAndType(List<String> inventories, String inventoryType) {
    List<Inventory> inventoryList =
        inventoryRepository.findIdByIdInAndType(inventories, inventoryType);
    return inventoryList.stream().map(Inventory::getId).toList();
  }
}
