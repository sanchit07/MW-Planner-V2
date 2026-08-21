package com.mw.planner.service.recommendation;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.Inventory;
import com.mw.planner.domain.Schedule;
import com.mw.planner.dto.CampaignForecastDTO;
import com.mw.planner.dto.InventoryResponseDTO;
import com.mw.planner.dto.recommendation.*;
import com.mw.planner.repository.CampaignInventorySchedulesRepository;
import com.mw.planner.repository.CampaignRepository;
import com.mw.planner.repository.ScheduleRepository;
import com.mw.planner.service.CampaignService;
import com.mw.planner.service.InventoryService;
import com.mw.planner.service.ScheduleCacheEvictor;
import com.mw.planner.service.VenuesService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

  private static final String PRODUCT_ID = "mw-planner";

  private final RecommendationEngineApiClient apiClient;
  private final CampaignRepository campaignRepository;
  private final CampaignInventorySchedulesRepository inventorySchedulesRepository;
  private final ScheduleRepository scheduleRepository;
  private final CampaignService campaignService;
  private final InventoryService inventoryService;
  private final VenuesService venuesService;
  private final ScheduleCacheEvictor scheduleCacheEvictor;

  // ─── 5b. Generate Recommendation ──────────────────────────────────────────────

  public RecommendationStatusResponseDTO generateRecommendation(
      String campaignId,
      GenerateRecommendationRequestDTO generateRequest,
      boolean forceRegenerate) {
    Campaign campaign =
        campaignRepository
            .findById(campaignId)
            .orElseThrow(() -> new RuntimeException("Campaign not found: " + campaignId));

    RecommendationRequestDTO request = buildRecommendationRequest(campaign);

    if (generateRequest != null
        && generateRequest.getMediaOwnerIds() != null
        && !generateRequest.getMediaOwnerIds().isEmpty()) {
      request.setMediaOwnerIds(generateRequest.getMediaOwnerIds());
    }
    try {
      log.info(
          "RecommendationRequestDTO for campaignId {}: {}",
          campaignId,
          new com.fasterxml.jackson.databind.ObjectMapper()
              .findAndRegisterModules()
              .writeValueAsString(request));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      log.warn("Failed to serialize RecommendationRequestDTO for campaignId {}", campaignId, e);
    }
    RecommendationApiResponse<RecommendationStatusResponseDTO> apiResponse =
        apiClient.generateRecommendation(campaignId, request, forceRegenerate);

    if (apiResponse == null || !apiResponse.isSuccess() || apiResponse.getData() == null) {
      throw new RuntimeException("Failed to generate recommendation for campaignId: " + campaignId);
    }

    RecommendationStatusResponseDTO response = apiResponse.getData();
    if (!Boolean.TRUE.equals(campaign.getSkipRecommendation())
        && !response.getRunId().equals(campaign.getRunId())
        && response.getStatus() == RecommendationStatusResponseDTO.RunStatus.COMPLETED) {
      // Update runId and sync auto select/delect
      handleNewCompletedRun(campaign, response);
    } else if (Boolean.TRUE.equals(campaign.getSkipRecommendation())
        && !response.getRunId().equals(campaign.getRunId())
        && response.getStatus() == RecommendationStatusResponseDTO.RunStatus.COMPLETED) {
      // Skip-recommendation campaign that re-enabled recommendations: record the
      // runId WITHOUT deleting/resyncing schedules, so the user's manually
      // selected inventory is preserved. Later generates (after the flag flips
      // back to false) see a matching runId and won't wipe the selection either;
      // only an explicit force-regenerate ("Restore AI recommendation") replaces
      // the selection.
      log.info(
          "Campaign {} has skipRecommendation=true — recording runId {} without syncing selection",
          campaignId,
          response.getRunId());
      campaign.setRunId(response.getRunId());
      campaignService.save(campaign);
    }

    return response;
  }

  private void handleNewCompletedRun(Campaign campaign, RecommendationStatusResponseDTO response) {
    String campaignId = campaign.getId();
    log.info(
        "New completed run detected for campaign {}. Old runId: {}, new runId: {}",
        campaignId,
        campaign.getRunId(),
        response.getRunId());

    // Delete all existing CampaignInventorySchedules and their Schedules
    List<CampaignInventorySchedules> existing =
        inventorySchedulesRepository.findByCampaignId(campaignId);
    for (CampaignInventorySchedules cis : existing) {
      if (cis.getScheduleIds() != null && !cis.getScheduleIds().isEmpty()) {
        scheduleRepository.deleteByIdIn(cis.getScheduleIds());
      }
      // Evict stale per-inventory CIS cache so /selected-inventory does not serve deleted schedules
      scheduleCacheEvictor.evict(campaignId, cis.getInventoryId());
    }
    inventorySchedulesRepository.deleteByCampaignId(campaignId);

    // Update campaign runId
    campaign.setRunId(response.getRunId());
    campaignService.save(campaign);

    // If auto-selected inventories exist, fetch and store their schedules
    List<String> autoSelectedIds = getAutoSelectedInventoryIds(response);
    if (!autoSelectedIds.isEmpty()) {
      log.info("Auto-selected {} inventories for campaign {}", autoSelectedIds.size(), campaignId);
      fetchAndStoreSchedules(campaign, response.getRunId());
    }
  }

  private List<String> getAutoSelectedInventoryIds(RecommendationStatusResponseDTO response) {
    if (response.getMetadata() != null
        && response.getMetadata().getAutoSelectedInventoryIds() != null) {
      return response.getMetadata().getAutoSelectedInventoryIds();
    }
    return Collections.emptyList();
  }

  private void fetchAndStoreSchedules(Campaign campaign, String runId) {
    RecommendationApiResponse<RunSchedulesResponseDTO> schedulesResponse =
        apiClient.getRecommendedSchedules(runId);

    if (schedulesResponse == null
        || !schedulesResponse.isSuccess()
        || schedulesResponse.getData() == null
        || schedulesResponse.getData().getSchedules() == null) {
      log.warn("No schedules returned for runId: {}", runId);
      return;
    }

    List<RunSchedulesResponseDTO.RunScheduleItemDTO> items =
        schedulesResponse.getData().getSchedules();

    // Bulk-resolve engine inventoryIds (externalIds) to planner Inventory objects
    List<String> externalIds =
        items.stream()
            .map(RunSchedulesResponseDTO.RunScheduleItemDTO::getInventoryId)
            .distinct()
            .collect(Collectors.toList());
    Map<String, Inventory> externalIdToInventory =
        inventoryService.findByExternalIdIn(externalIds).stream()
            .collect(Collectors.toMap(Inventory::getExternalId, Function.identity(), (a, b) -> a));

    for (RunSchedulesResponseDTO.RunScheduleItemDTO item : items) {
      Inventory inventory = externalIdToInventory.get(item.getInventoryId());
      if (inventory == null) {
        log.warn(
            "No planner inventory found for engine inventoryId (externalId): {}",
            item.getInventoryId());
        continue;
      }
      createAndStoreScheduleFromRecommendation(campaign, item, inventory);
    }
  }

  private void createAndStoreScheduleFromRecommendation(
      Campaign campaign, RunSchedulesResponseDTO.RunScheduleItemDTO item, Inventory inventory) {
    List<Schedule.Weekday> scheduleDays =
        extractScheduleDaysFromBookingMatrix(item.getBookingMatrix());
    Schedule schedule =
        Schedule.builder()
            .startDate(item.getScheduleStartDate())
            .endDate(item.getScheduleEndDate())
            .name("Schedule 1")
            .bookingMatrix(item.getBookingMatrix())
            .scheduleDays(scheduleDays)
            .type(Schedule.Type.LOOP)
            .duration(item.getDuration())
            .spotsPerLoop(item.getSpotsPerLoop())
            .spotsPerHour(item.getSpotsPerHour())
            .adPlays(item.getAdPlays())
            .plannedSot(item.getPlannedSot())
            .totalSot(item.getTotalSot())
            .order(1)
            .basePrice(item.getBasePrice())
            .impressions(item.getEstimatedImpressions())
            .reach(item.getEstimatedReach())
            .build();
    Schedule saved = scheduleRepository.save(schedule);

    String internalId = inventory.getId();
    String mediaOwnerId = inventory.getMediaOwnerId();

    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId(campaign.getId())
            .inventoryId(internalId)
            .mediaOwnerId(mediaOwnerId != null ? mediaOwnerId : "")
            .scheduleIds(List.of(saved.getId()))
            .build();
    cis.setId(campaign.getId() + "_" + mediaOwnerId + "_" + internalId);
    inventorySchedulesRepository.save(cis);
    // Evict per-inventory CIS cache so the freshly created schedule is served immediately
    scheduleCacheEvictor.evict(campaign.getId(), internalId);

    updateCampaignCompanyAccess(campaign, mediaOwnerId);

    log.debug(
        "Created schedule {} and CIS for inventory {} (externalId: {}) in campaign {}",
        saved.getId(),
        internalId,
        item.getInventoryId(),
        campaign.getId());
  }

  private List<Schedule.Weekday> extractScheduleDaysFromBookingMatrix(
      Map<String, List<Integer>> bookingMatrix) {
    if (bookingMatrix == null || bookingMatrix.isEmpty()) {
      return Collections.emptyList();
    }

    Set<Schedule.Weekday> uniqueWeekdays = new LinkedHashSet<>();
    for (String dateKey : bookingMatrix.keySet()) {
      try {
        LocalDate date = LocalDate.parse(dateKey);
        uniqueWeekdays.add(Schedule.Weekday.valueOf(date.getDayOfWeek().name()));
      } catch (Exception e) {
        log.warn("Skipping invalid bookingMatrix date key: {}", dateKey);
      }
    }

    return new ArrayList<>(uniqueWeekdays);
  }

  /**
   * Update campaign's companyAccess list with mediaOwnerId and evict campaign cache.
   *
   * @param campaign The campaign entity
   * @param mediaOwnerId The media owner ID to add to companyAccess Redundant method to avoid
   *     circular dependency between CampaignService and RecommendationService since campaign cache
   */
  public void updateCampaignCompanyAccess(Campaign campaign, String mediaOwnerId) {
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

  // ─── 5c. Get Recommendation Results (with Performance enrichment) ─────────

  public PaginatedRecommendationResponseDTO getRecommendationResults(
      String campaignId,
      String runId,
      int page,
      int size,
      List<String> sort,
      String search,
      RecommendationResultFilterDTO filter) {

    long startTime = System.nanoTime();
    RecommendationApiResponse<PaginatedRecommendationResponseDTO> apiResponse =
        apiClient.getRecommendationResults(runId, page, size, sort, search, filter);
    double elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
    log.info(
        "apiClient.getRecommendationResults for runId {} took {} seconds", runId, elapsedSeconds);

    if (apiResponse == null || !apiResponse.isSuccess() || apiResponse.getData() == null) {
      throw new RuntimeException("Failed to get recommendation results for runId: " + runId);
    }

    PaginatedRecommendationResponseDTO response = apiResponse.getData();

    // Enrich with Performance
    if (response.getRecommendations() != null && !response.getRecommendations().isEmpty()) {
      startTime = System.nanoTime();
      Campaign campaign =
          campaignRepository
              .findById(campaignId)
              .orElseThrow(() -> new RuntimeException("Campaign not found: " + campaignId));
      elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
      log.info(
          "campaign repository find by id for runId {} took {} seconds", runId, elapsedSeconds);
      enrichWithInternalIdsAndPerformance(campaign, response.getRecommendations());

      // Filter out recommendations where inventoryDetails.internalId is null
      int originalSize = response.getRecommendations().size();
      List<PaginatedRecommendationResponseDTO.RecommendedInventory> filteredRecommendations =
          response.getRecommendations().stream()
              .filter(
                  rec ->
                      rec.getInventoryDetails() != null
                          && rec.getInventoryDetails().getInternalId() != null)
              .collect(Collectors.toList());

      int filteredSize = filteredRecommendations.size();
      int removedCount = originalSize - filteredSize;

      if (removedCount > 0) {
        log.warn(
            "Filtered out {} recommendation(s) with null internalId for campaignId={}, runId={}, page={}",
            removedCount,
            campaignId,
            runId,
            page);
      }

      response.setRecommendations(filteredRecommendations);
    }

    return response;
  }

  private void enrichWithInternalIdsAndPerformance(
      Campaign campaign,
      List<PaginatedRecommendationResponseDTO.RecommendedInventory> recommendations) {

    // Bulk-resolve engine inventoryIds (externalIds) to planner internal IDs
    List<String> externalIds =
        recommendations.stream()
            .map(PaginatedRecommendationResponseDTO.RecommendedInventory::getInventoryId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    long startTime = System.nanoTime();
    List<Inventory> resolvedInventories = inventoryService.findByExternalIdIn(externalIds);
    double elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
    log.info(
        "inventory service find by external id find by id for runId {} took {} seconds",
        null,
        elapsedSeconds);
    Map<String, String> externalIdToInternalId =
        resolvedInventories.stream()
            .collect(Collectors.toMap(Inventory::getExternalId, Inventory::getId, (a, b) -> a));
    Map<String, Inventory> externalIdToInventory =
        resolvedInventories.stream()
            .collect(Collectors.toMap(Inventory::getExternalId, inv -> inv, (a, b) -> a));

    for (PaginatedRecommendationResponseDTO.RecommendedInventory rec : recommendations) {
      PaginatedRecommendationResponseDTO.Performance performance;

      if ("AUTO".equals(rec.getSelectionMode()) || "MANUAL".equals(rec.getSelectionMode())) {
        String internalId = externalIdToInternalId.get(rec.getInventoryId());
        performance = buildPerformanceForSelected(campaign, rec, internalId);
      } else {
        performance = buildPerformanceForUnselected(rec);
      }

      // Set internal ID and digitalFields only if inventoryDetails exists
      if (rec.getInventoryDetails() != null) {
        rec.getInventoryDetails().setInternalId(externalIdToInternalId.get(rec.getInventoryId()));

        Inventory inventory = externalIdToInventory.get(rec.getInventoryId());
        if (inventory != null) {
          rec.getInventoryDetails().setSize(inventory.getSize());
          rec.getInventoryDetails().setInventoryCluster(inventory.getInventoryCluster());
        }
        if (inventory != null && inventory.getDigitalFields() != null) {
          Inventory.DigitalFields df = inventory.getDigitalFields();
          rec.getInventoryDetails()
              .setDigitalFields(
                  InventoryResponseDTO.DigitalFieldsDTO.builder()
                      .playerSoftwareId(df.getPlayerSoftwareId())
                      .playerSoftwareName(df.getPlayerSoftwareName())
                      .playerCount(df.getPlayerCount())
                      .spotDuration(df.getSpotDuration())
                      .spotsPerLoop(df.getSpotsPerLoop())
                      .bookingMode(df.getBookingMode())
                      .build());
        }
      }
      rec.setPerformance(performance);
    }
  }

  private PaginatedRecommendationResponseDTO.Performance buildPerformanceForSelected(
      Campaign campaign,
      PaginatedRecommendationResponseDTO.RecommendedInventory rec,
      String internalInventoryId) {

    if (internalInventoryId == null) {
      log.warn(
          "No planner inventory found for engine inventoryId (externalId): {}",
          rec.getInventoryId());
      return buildPerformanceForUnselected(rec);
    }

    Optional<CampaignInventorySchedules> cisOpt =
        inventorySchedulesRepository.findByCampaignIdAndInventoryId(
            campaign.getId(), internalInventoryId);

    if (cisOpt.isEmpty()) {
      log.warn(
          "No CampaignInventorySchedules found for selected inventory {} in campaign {}",
          rec.getInventoryId(),
          campaign.getId());
      return buildPerformanceForUnselected(rec);
    }

    CampaignForecastDTO forecast =
        campaignService.calculateCampaignForecast(campaign, List.of(cisOpt.get()));

    Integer totalDays = rec.getAvailability() != null ? rec.getAvailability().getTotalDays() : null;
    Double cpmRate =
        rec.getInventoryDetails() != null ? rec.getInventoryDetails().getCpmRate() : null;
    Double spotRate =
        rec.getInventoryDetails() != null ? rec.getInventoryDetails().getSpotRate() : null;

    Long totalAdPlays = forecast.getEstimatedAdPlays();
    Double estimatedCost = forecast.getTotalCost();

    return PaginatedRecommendationResponseDTO.Performance.builder()
        .estimatedImpressions(forecast.getEstimatedImpression())
        .estimatedReach(forecast.getEstimatedReach())
        .estimatedFrequency(forecast.getEstimatedFrequency())
        .totalSot(forecast.getTotalSot())
        .plannedSot(forecast.getPlannedSot())
        .sov(forecast.getSov())
        .totalAdPlays(totalAdPlays)
        .perDayAdPlays(
            totalAdPlays != null && totalDays != null && totalDays > 0
                ? totalAdPlays / totalDays
                : null)
        .estimatedCost(estimatedCost)
        .perDayCost(
            estimatedCost != null && totalDays != null && totalDays > 0
                ? estimatedCost / totalDays
                : null)
        .cpmRate(cpmRate)
        .spotRate(spotRate)
        .build();
  }

  private PaginatedRecommendationResponseDTO.Performance buildPerformanceForUnselected(
      PaginatedRecommendationResponseDTO.RecommendedInventory rec) {

    PaginatedRecommendationResponseDTO.ForecastedMetrics fm = rec.getForecast();
    PaginatedRecommendationResponseDTO.CostEstimate ce = rec.getCost();
    Integer totalDays = rec.getAvailability() != null ? rec.getAvailability().getTotalDays() : null;
    Double cpmRate =
        rec.getInventoryDetails() != null ? rec.getInventoryDetails().getCpmRate() : null;
    Double spotRate =
        rec.getInventoryDetails() != null ? rec.getInventoryDetails().getSpotRate() : null;

    Long totalAdPlays = ce != null ? ce.getTotalAdPlays() : null;
    Double estimatedCost =
        ce != null && ce.getEstimatedCost() != null ? ce.getEstimatedCost().doubleValue() : null;

    return PaginatedRecommendationResponseDTO.Performance.builder()
        .estimatedImpressions(fm != null ? fm.getEstimatedImpressions() : null)
        .estimatedReach(fm != null ? fm.getEstimatedReach() : null)
        .estimatedFrequency(fm != null ? fm.getEstimatedFrequency() : null)
        .totalSot(null)
        .plannedSot(null)
        .sov(null)
        .totalAdPlays(totalAdPlays)
        .perDayAdPlays(
            totalAdPlays != null && totalDays != null && totalDays > 0
                ? totalAdPlays / totalDays
                : null)
        .estimatedCost(estimatedCost)
        .perDayCost(
            estimatedCost != null && totalDays != null && totalDays > 0
                ? estimatedCost / totalDays
                : null)
        .cpmRate(cpmRate)
        .spotRate(spotRate)
        .build();
  }

  // ─── 5d. Sync Selected Inventories ────────────────────────────────────────────

  /**
   * Sync selected inventories with the recommendation engine. Called after select/deselect on
   * mw-planner. For SELECT, the engine marks inventories as MANUAL and returns null schedule data;
   * mw-planner uses its own default schedule (created in CampaignInventorySchedulesService).
   *
   * @param campaignId the campaign ID
   * @param inventoryIds planner-internal inventory IDs (not engine externalIds)
   * @param operationType SELECT or DESELECT
   */
  public void syncSelectedInventories(
      String campaignId, List<String> inventoryIds, OperationType operationType) {
    Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
    if (campaign == null || campaign.getRunId() == null) {
      log.debug("Skipping recommendation sync: campaign {} has no runId", campaignId);
      return;
    }

    try {
      // Convert planner internal IDs to engine externalIds
      List<String> externalIds = resolveToExternalIds(inventoryIds);
      if (externalIds.isEmpty()) {
        log.warn("Could not resolve any externalIds for inventoryIds: {}", inventoryIds);
        return;
      }

      SelectedInventoriesDTO dto =
          SelectedInventoriesDTO.builder().inventoryIds(externalIds).build();
      apiClient.manageSelectedInventories(campaign.getRunId(), operationType.name(), dto);
      log.info(
          "Synced {} inventories ({}) with recommendation engine for campaign {}",
          externalIds.size(),
          operationType,
          campaignId);
    } catch (Exception e) {
      log.warn(
          "Failed to sync selected inventories with recommendation engine for campaign {}: {}",
          campaignId,
          e.getMessage());
    }
  }

  private List<String> resolveToExternalIds(List<String> internalIds) {
    List<Inventory> found = inventoryService.findAllByIds(internalIds);
    Set<String> foundIds = found.stream().map(Inventory::getId).collect(Collectors.toSet());
    internalIds.stream()
        .filter(id -> !foundIds.contains(id))
        .forEach(
            id ->
                log.warn("Could not resolve externalId for inventory {}: inventory not found", id));

    return found.stream().map(Inventory::getExternalId).filter(Objects::nonNull).toList();
  }

  /**
   * Resolve planner inventory IDs to externalIds and build a map externalId -> planner inventoryId
   * for use when mapping engine response back without a second fetch.
   */
  private Map<String, String> resolveToExternalIdsWithMap(List<String> plannerInventoryIds) {
    Map<String, String> externalIdToPlannerInventoryId = new HashMap<>();
    for (String plannerId : plannerInventoryIds) {
      try {
        Inventory inv = inventoryService.getById(plannerId);
        if (inv.getExternalId() != null) {
          externalIdToPlannerInventoryId.put(inv.getExternalId(), plannerId);
        }
      } catch (Exception e) {
        log.warn("Could not resolve externalId for inventory {}: {}", plannerId, e.getMessage());
      }
    }
    return externalIdToPlannerInventoryId;
  }

  // ─── Auto-optimize schedules ───────────────────────────────────────────────────

  /**
   * Call recommendation engine auto-optimize for the given campaign and run, then on success remove
   * existing schedules, clear schedule/approval fields on CampaignInventorySchedules, and create
   * new schedules from the response linked to existing CIS. Inventory IDs sent to the engine are
   * planner externalIds (engine inventoryIds).
   *
   * @param campaignId campaign ID
   * @param runId recommendation run ID
   * @return RunSchedulesResponseDTO from the engine on success
   */
  public RunSchedulesResponseDTO autoOptimizeSchedulesAndSync(String campaignId, String runId) {
    if (!campaignService.existsById(campaignId)) {
      throw new RuntimeException("Campaign not found: " + campaignId);
    }

    List<CampaignInventorySchedules> cisList =
        inventorySchedulesRepository.findByCampaignId(campaignId);
    if (cisList.isEmpty()) {
      log.info("No campaign inventory schedules for campaignId: {}", campaignId);
      return RunSchedulesResponseDTO.builder()
          .runId(runId)
          .schedules(Collections.emptyList())
          .build();
    }

    List<String> plannerInventoryIds =
        cisList.stream()
            .map(CampaignInventorySchedules::getInventoryId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    Map<String, String> externalIdToPlannerInventoryId =
        resolveToExternalIdsWithMap(plannerInventoryIds);
    List<String> externalIds = new ArrayList<>(externalIdToPlannerInventoryId.keySet());
    if (externalIds.isEmpty()) {
      log.warn("Could not resolve externalIds for campaignId: {}", campaignId);
      throw new RuntimeException("Could not resolve inventory externalIds for campaign");
    }

    SelectedInventoriesDTO request =
        SelectedInventoriesDTO.builder().inventoryIds(externalIds).build();
    RecommendationApiResponse<RunSchedulesResponseDTO> apiResponse =
        apiClient.autoOptimizeSchedules(runId, request);

    if (apiResponse == null || !apiResponse.isSuccess() || apiResponse.getData() == null) {
      throw new RuntimeException(
          "Auto-optimize schedules failed for runId: "
              + runId
              + (apiResponse != null && apiResponse.getError() != null
                  ? "; " + apiResponse.getError().getMessage()
                  : ""));
    }

    RunSchedulesResponseDTO data = apiResponse.getData();
    List<RunSchedulesResponseDTO.RunScheduleItemDTO> items =
        data.getSchedules() != null ? data.getSchedules() : Collections.emptyList();

    // Remove all existing schedule entities linked to these CIS
    List<String> allScheduleIds = new ArrayList<>();
    for (CampaignInventorySchedules cis : cisList) {
      if (cis.getScheduleIds() != null && !cis.getScheduleIds().isEmpty()) {
        allScheduleIds.addAll(cis.getScheduleIds());
      }
    }
    if (!allScheduleIds.isEmpty()) {
      scheduleRepository.deleteByIdIn(allScheduleIds);
      log.debug(
          "Deleted {} existing schedules for campaignId: {}", allScheduleIds.size(), campaignId);
    }

    // Clear schedule and approval fields on each CIS for re-approval
    for (CampaignInventorySchedules cis : cisList) {
      cis.setScheduleIds(null);
      cis.setApprovedScheduleIds(null);
      cis.setHistory(null);
      cis.setApprovedBy(null);
      inventorySchedulesRepository.save(cis);
      // Evict per-inventory CIS cache so cleared/re-optimized state is served fresh
      scheduleCacheEvictor.evict(campaignId, cis.getInventoryId());
    }

    if (items.isEmpty()) {
      log.info("Auto-optimize returned no schedules for runId: {}", runId);
      return data;
    }

    // Use pre-built map (externalId -> planner inventoryId) so we don't fetch Inventory again
    for (RunSchedulesResponseDTO.RunScheduleItemDTO item : items) {
      String plannerInventoryId = externalIdToPlannerInventoryId.get(item.getInventoryId());
      if (plannerInventoryId == null) {
        log.warn(
            "No planner inventory found for engine inventoryId (externalId): {}",
            item.getInventoryId());
        continue;
      }
      createScheduleAndLinkToExistingCis(campaignId, item, plannerInventoryId);
    }

    log.info(
        "Auto-optimize completed for campaignId: {}, runId: {}, schedules: {}",
        campaignId,
        runId,
        items.size());
    return data;
  }

  private void createScheduleAndLinkToExistingCis(
      String campaignId,
      RunSchedulesResponseDTO.RunScheduleItemDTO item,
      String plannerInventoryId) {
    List<Schedule.Weekday> scheduleDays =
        extractScheduleDaysFromBookingMatrix(item.getBookingMatrix());
    Schedule schedule =
        Schedule.builder()
            .startDate(item.getScheduleStartDate())
            .endDate(item.getScheduleEndDate())
            .name("Schedule 1")
            .bookingMatrix(item.getBookingMatrix())
            .scheduleDays(scheduleDays)
            .type(Schedule.Type.LOOP)
            .duration(item.getDuration())
            .spotsPerLoop(item.getSpotsPerLoop())
            .spotsPerHour(item.getSpotsPerHour())
            .adPlays(item.getAdPlays())
            .plannedSot(item.getPlannedSot())
            .totalSot(item.getTotalSot())
            .order(1)
            .basePrice(item.getBasePrice())
            .impressions(item.getEstimatedImpressions())
            .reach(item.getEstimatedReach())
            .build();
    Schedule saved = scheduleRepository.save(schedule);

    Optional<CampaignInventorySchedules> cisOpt =
        inventorySchedulesRepository.findByCampaignIdAndInventoryId(campaignId, plannerInventoryId);
    if (cisOpt.isEmpty()) {
      log.warn(
          "No CampaignInventorySchedules for campaignId: {}, inventoryId: {}",
          campaignId,
          plannerInventoryId);
      return;
    }
    CampaignInventorySchedules cis = cisOpt.get();
    cis.setScheduleIds(List.of(saved.getId()));
    inventorySchedulesRepository.save(cis);
    // Evict per-inventory CIS cache so the newly linked schedule is served immediately
    scheduleCacheEvictor.evict(campaignId, plannerInventoryId);

    log.debug(
        "Created schedule {} and linked to CIS for inventory {} (externalId: {}) in campaign {}",
        saved.getId(),
        plannerInventoryId,
        item.getInventoryId(),
        campaignId);
  }

  public enum OperationType {
    SELECT,
    DESELECT
  }

  // ─── 5a. Campaign Filter to RecommendationRequestDTO Mapping ──────────────

  RecommendationRequestDTO buildRecommendationRequest(Campaign campaign) {
    RecommendationRequestDTO.RecommendationRequestDTOBuilder builder =
        RecommendationRequestDTO.builder()
            .country(campaign.getCountryId())
            .startDate(campaign.getStartDate())
            .endDate(campaign.getEndDate())
            .productId(PRODUCT_ID)
            .companyId(campaign.getCompanyId())
            .brandId(campaign.getBrand() != null ? campaign.getBrand().getId() : null)
            .budget(campaign.getBudget() != null ? BigDecimal.valueOf(campaign.getBudget()) : null);

    // Goals mapping
    if (campaign.getGoals() != null) {
      builder.goal(mapGoalType(campaign.getGoals().getGoalType()));
      if (campaign.getGoals().getTargetValue() != null) {
        builder.goalValue(campaign.getGoals().getTargetValue().longValue());
      }
    }

    // Budget allocation
    if (campaign.getBudgetAllocation() != null) {
      builder.budgetAllocation(campaign.getBudgetAllocation());
    }

    // Targeting
    if (campaign.getTargeting() != null) {
      builder.audienceTargeting(buildAudienceTargeting(campaign.getTargeting()));
      builder.geographyTargeting(buildGeographyTargeting(campaign.getTargeting()));
      builder.searchKeywords(buildSearchKeywords(campaign.getTargeting()));

      if (campaign.getTargeting().getInventoryCluster() != null
          && !campaign.getTargeting().getInventoryCluster().isEmpty()) {
        builder.inventoryCluster(campaign.getTargeting().getInventoryCluster());
      }
    }

    // DSP / programmatic
    String dsp = campaign.getDsp();
    if (dsp != null && !dsp.isBlank()) {
      String normalized = dsp.trim().toUpperCase(Locale.ROOT);
      List<String> dsps =
          "ACTIVATE".equals(normalized) ? List.of("ACTIVATE", "MAX") : List.of(normalized);
      builder.dsps(dsps);
      builder.programmaticEnabled(true);
    }
    // else: leave dsps + programmaticEnabled null (omitted from payload)

    // Programmatic-only opt-in (independent of DSP; additive, never forces false)
    if (campaign.getTargeting() != null
        && Boolean.TRUE.equals(campaign.getTargeting().getProgrammaticOnly())) {
      builder.programmaticEnabled(true);
    }

    return builder.build();
  }

  private RecommendationRequestDTO.CampaignGoal mapGoalType(Campaign.Goals.GoalType goalType) {
    if (goalType == null) return null;
    return switch (goalType) {
      case IMPRESSIONS -> RecommendationRequestDTO.CampaignGoal.IMPRESSIONS;
      case REACH -> RecommendationRequestDTO.CampaignGoal.REACH;
      case SOV -> RecommendationRequestDTO.CampaignGoal.SOV;
      case ADPLAYS -> RecommendationRequestDTO.CampaignGoal.AD_PLAYS;
      default -> null;
    };
  }

  private RecommendationRequestDTO.AudienceTargeting buildAudienceTargeting(
      Campaign.Targeting targeting) {
    Map<String, List<String>> demographics = new HashMap<>();
    List<String> audienceSegments = new ArrayList<>();

    if (targeting.getDemographics() != null) {
      Map<String, List<String>> demo = targeting.getDemographics();

      if (demo.containsKey("age")) {
        demographics.put("age", convertAge(demo.get("age")));
      }
      if (demo.containsKey("gender")) {
        demographics.put("gender", convertGender(demo.get("gender")));
      }
      if (demo.containsKey("income")) {
        demographics.put("income", convertIncome(demo.get("income")));
      }
      if (demo.containsKey("interests")) {
        demographics.put("interests", demo.get("interests"));
      }
      if (demo.containsKey("behavior")) {
        audienceSegments.addAll(demo.get("behavior"));
      }
    }

    // Merge signals into audience segments
    if (targeting.getSignals() != null) {
      audienceSegments.addAll(targeting.getSignals());
    }

    // Venue types — resolve campaign slugs to enumerationIds, kept separate by channel.
    RecommendationRequestDTO.VenueTypeIds venueTypeIds = null;
    if (targeting.getVenueTypes() != null) {
      Campaign.Targeting.VenueTypes vt = targeting.getVenueTypes();
      boolean hasDigital = vt.getDigitalOoh() != null && !vt.getDigitalOoh().isEmpty();
      boolean hasClassic = vt.getClassicOoh() != null && !vt.getClassicOoh().isEmpty();
      if (hasDigital || hasClassic) {
        Map<String, String> slugToId = venuesService.getVenueSlugToIdMap();
        List<String> digitalIds =
            hasDigital
                ? vt.getDigitalOoh().stream()
                    .map(slugToId::get)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList())
                : null;
        List<String> classicIds =
            hasClassic
                ? vt.getClassicOoh().stream()
                    .map(slugToId::get)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList())
                : null;
        boolean hasDigitalIds = digitalIds != null && !digitalIds.isEmpty();
        boolean hasClassicIds = classicIds != null && !classicIds.isEmpty();
        if (hasDigitalIds || hasClassicIds) {
          venueTypeIds =
              RecommendationRequestDTO.VenueTypeIds.builder()
                  .digital(hasDigitalIds ? digitalIds : null)
                  .classic(hasClassicIds ? classicIds : null)
                  .build();
        }
      }
    }

    return RecommendationRequestDTO.AudienceTargeting.builder()
        .demographics(demographics.isEmpty() ? null : demographics)
        .audienceSegments(audienceSegments.isEmpty() ? null : audienceSegments)
        .venueTypeIds(venueTypeIds)
        .build();
  }

  private List<String> convertAge(List<String> ages) {
    return ages.stream().map(a -> a.replace("_", "-")).collect(Collectors.toList());
  }

  private List<String> convertGender(List<String> genders) {
    return genders.stream().map(String::toUpperCase).collect(Collectors.toList());
  }

  private List<String> convertIncome(List<String> incomes) {
    return incomes.stream().map(this::toCanonicalIncome).collect(Collectors.toList());
  }

  private String toCanonicalIncome(String income) {
    return switch (income.toLowerCase()) {
      case "low" -> "Low";
      case "lower_middle" -> "Lower-middle";
      case "middle" -> "Middle";
      case "upper_middle" -> "Upper-middle";
      case "high" -> "High";
      default -> capitalize(income);
    };
  }

  private String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
  }

  private RecommendationRequestDTO.GeographyTargeting buildGeographyTargeting(
      Campaign.Targeting targeting) {
    if (targeting.getGeofencing() == null) return null;

    List<RecommendationRequestDTO.Geofence> geofences = new ArrayList<>();

    Campaign.Targeting.Geofencing geofencing = targeting.getGeofencing();

    if (geofencing.getGeometries() != null) {
      for (Campaign.Targeting.Geofencing.Geometry geometry : geofencing.getGeometries()) {
        if (!geometry.isIncluded()) continue;
        geofences.add(
            RecommendationRequestDTO.Geofence.builder()
                .type("Polygon")
                .coordinates(geometry.getCoordinates())
                .build());
      }
    }

    if (geofencing.getLocations() != null) {
      for (Campaign.Targeting.Geofencing.Location location : geofencing.getLocations()) {
        if (!location.isIncluded()) continue;
        if (!isCircleLocation(location)) continue;
        geofences.add(
            RecommendationRequestDTO.Geofence.builder()
                .type("Circle")
                .centerLat(location.getLat())
                .centerLng(location.getLng())
                .build());
      }
    }

    if (geofences.isEmpty()) return null;

    return RecommendationRequestDTO.GeographyTargeting.builder().geofences(geofences).build();
  }

  private boolean isCircleLocation(Campaign.Targeting.Geofencing.Location location) {
    Map<String, String> metadata = location.getMetadata();
    if (metadata == null) return false;
    return "circle".equalsIgnoreCase(metadata.get("type"));
  }

  /**
   * Collects names of non-circle, included geofencing locations to pass to the recommendation
   * engine as inventory-name search keywords. Circle locations are handled as geofences instead.
   */
  private List<String> buildSearchKeywords(Campaign.Targeting targeting) {
    if (targeting == null
        || targeting.getGeofencing() == null
        || targeting.getGeofencing().getLocations() == null) {
      return null;
    }

    List<String> keywords = new ArrayList<>();
    for (Campaign.Targeting.Geofencing.Location location :
        targeting.getGeofencing().getLocations()) {
      if (!location.isIncluded()) continue;
      if (isCircleLocation(location)) continue;
      String name = location.getName();
      if (name != null && !name.isBlank()) {
        keywords.add(name);
      }
    }

    return keywords.isEmpty() ? null : keywords;
  }
}
