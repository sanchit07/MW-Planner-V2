package com.mw.recommendation.engine.service;

import com.mw.recommendation.engine.domain.AudienceData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.domain.RecommendationRun;
import com.mw.recommendation.engine.domain.SelectionMode;
import com.mw.recommendation.engine.dto.BrowseInventoryRequestDTO;
import com.mw.recommendation.engine.dto.PaginatedRecommendationResponseDTO;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.dto.RecommendationResponseDTO;
import com.mw.recommendation.engine.dto.RecommendationResultFilterDTO;
import com.mw.recommendation.engine.dto.RecommendationStatusResponseDTO;
import com.mw.recommendation.engine.dto.SelectedInventoriesDTO;
import com.mw.recommendation.engine.dto.SelectedResultMeasureSummaryDTO;
import com.mw.recommendation.engine.dto.measure.MeasureInventoryDTO;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyRequestDTO;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyResponseDTO;
import com.mw.recommendation.engine.enums.ErrorCode;
import com.mw.recommendation.engine.exception.BaseException;
import com.mw.recommendation.engine.mapper.InventoryItemMapper;
import com.mw.recommendation.engine.repository.AudienceRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
import com.mw.recommendation.engine.repository.RecommendationResultRepository;
import com.mw.recommendation.engine.repository.RecommendationRunRepository;
import com.mw.recommendation.engine.repository.RunScheduleRecommendationRepository;
import com.mw.recommendation.engine.util.BudgetAllocationUtils;
import com.mw.recommendation.engine.util.InventoryAvailabilityUtils;
import com.mw.recommendation.engine.util.RequestHashUtils;
import com.mw.recommendation.engine.util.VariationUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** Service for generating inventory recommendations based on campaign requirements and scoring */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final InventoryRepository inventoryRepository;
  private final RecommendationRunRepository recommendationRunRepository;
  private final RecommendationResultRepository recommendationResultRepository;
  private final RecommendationAsyncService recommendationAsyncService;
  private final ScoringService scoringService;
  private final AudienceRepository audienceRepository;
  private final ScheduleRecommendationService scheduleRecommendationService;
  private final MeasureApiClient measureApiClient;
  private final Clock clock;
  private final RunScheduleRecommendationRepository runScheduleRecommendationRepository;
  private final InventoryItemMapper inventoryItemMapper;

  /**
   * Submit a recommendation request. Returns existing runId if same payload exists, otherwise
   * creates new runId and starts async processing.
   *
   * @param campaignId Campaign ID
   * @param request Recommendation request
   * @return Status response with runId and status
   */
  public RecommendationStatusResponseDTO submitRecommendation(
      String campaignId, RecommendationRequestDTO request) {
    return submitRecommendation(campaignId, request, false);
  }

  /**
   * Submit a recommendation request. Returns existing runId if same payload exists, otherwise
   * creates new runId and starts async processing.
   *
   * @param campaignId Campaign ID
   * @param request Recommendation request
   * @param forceRegenerate If true, delete any existing run with the same payload (and its
   *     dependent results/schedules) and regenerate. Ignored when the existing run is still
   *     IN_PROGRESS.
   * @return Status response with runId and status
   */
  public RecommendationStatusResponseDTO submitRecommendation(
      String campaignId, RecommendationRequestDTO request, boolean forceRegenerate) {

    log.info("Submitting recommendation request for campaign: {}", campaignId);

    // Generate hash of request payload (excluding topN)
    String requestHash = RequestHashUtils.hashRequest(request);

    // Check if a run with same campaignId and requestHash already exists
    Optional<RecommendationRun> existingRun =
        recommendationRunRepository.findByCampaignIdAndRequestHash(campaignId, requestHash);

    if (existingRun.isPresent()) {
      RecommendationRun run = existingRun.get();
      if (!forceRegenerate) {
        log.info(
            "Found existing runId {} for campaign {} with same payload",
            run.getRunId(),
            campaignId);
        return buildStatusResponse(run);
      }
      if (run.getStatus() == RecommendationRun.RunStatus.IN_PROGRESS) {
        // Deleting a run mid-processing would leave the async pipeline writing orphaned results
        log.warn(
            "forceRegenerate requested for campaign {} but run {} is IN_PROGRESS; returning existing run",
            campaignId,
            run.getRunId());
        return buildStatusResponse(run);
      }
      log.info(
          "forceRegenerate=true: deleting existing run {} and dependent documents for campaign {}",
          run.getRunId(),
          campaignId);
      // Delete the parent run first so the dedup lookup never sees a run without its results
      recommendationRunRepository.delete(run);
      recommendationResultRepository.deleteByRunId(run.getRunId());
      runScheduleRecommendationRepository.deleteByRunId(run.getRunId());
    }

    // Create new run
    String runId = UUID.randomUUID().toString();
    LocalDateTime now = LocalDateTime.now();

    RecommendationRun newRun =
        RecommendationRun.builder()
            .runId(runId)
            .campaignId(campaignId)
            .productId(request.getProductId())
            .companyId(request.getCompanyId())
            .requestHash(requestHash)
            .request(request)
            .status(RecommendationRun.RunStatus.IN_PROGRESS)
            .completionPercentage(0)
            .generatedAt(now)
            .warnings(new ArrayList<>())
            .build();

    recommendationRunRepository.save(newRun);
    log.info("Created new runId {} for campaign {}", runId, campaignId);

    // Start async processing via dedicated async service
    recommendationAsyncService.processRecommendationsAsync(runId, campaignId, request);

    return buildStatusResponse(newRun);
  }

  /**
   * Get paginated recommendation results for a runId with filtering and sorting support
   *
   * @param runId Run ID
   * @param page Page number (0-based)
   * @param size Page size
   * @param sort Sort parameters (format: "field,direction" e.g., "finalScore,desc")
   * @param search Optional search text (case-insensitive match on name, address, city,
   *     classification, type, referenceId)
   * @param filter Filter DTO for filtering results (can be null)
   * @return Paginated recommendation results
   */
  public PaginatedRecommendationResponseDTO getRecommendationResults(
      String runId,
      int page,
      int size,
      List<String> sort,
      String search,
      RecommendationResultFilterDTO filter) {

    log.info(
        "Fetching recommendation results for runId: {}, page: {}, size: {}, sort: {}, search: {}, filter: {}",
        runId,
        page,
        size,
        sort,
        search,
        filter);

    // Check if run exists
    RecommendationRun run =
        recommendationRunRepository
            .findByRunId(runId)
            .orElseThrow(
                () ->
                    new BaseException(
                        ErrorCode.RECOMMENDATION_RUN_NOT_FOUND,
                        "Recommendation run not found for runId: " + runId));

    // Check if still in progress
    if (run.getStatus() == RecommendationRun.RunStatus.IN_PROGRESS) {
      throw new BaseException(
          ErrorCode.RECOMMENDATION_IN_PROGRESS,
          "Recommendation is still in progress. Current completion: "
              + run.getCompletionPercentage()
              + "%");
    }

    // Build Sort object from sort parameters
    Sort sortObj = buildSort(sort);

    // Fetch paginated results with filtering and sorting
    Pageable pageable = PageRequest.of(page, size, sortObj);
    Page<RecommendationResult> resultPage =
        recommendationResultRepository.findByRunIdWithFilters(runId, filter, search, pageable);

    // Convert to DTO
    List<PaginatedRecommendationResponseDTO.RecommendedInventory> recommendations =
        resultPage.getContent().stream()
            .map(this::convertToRecommendedInventory)
            .collect(Collectors.toList());

    // Enrich any results missing operatingTimes from the live inventory (covers runs created
    // before operatingTimes were persisted onto results, so /results always returns them).
    enrichMissingOperatingTimes(recommendations, fetchOperatingTimesByInventoryId(recommendations));

    PaginatedRecommendationResponseDTO.PaginationInfo paginationInfo =
        PaginatedRecommendationResponseDTO.PaginationInfo.builder()
            .page(page)
            .size(size)
            .totalElements(resultPage.getTotalElements())
            .totalPages(resultPage.getTotalPages())
            .hasNext(resultPage.hasNext())
            .hasPrevious(resultPage.hasPrevious())
            .build();

    List<PaginatedRecommendationResponseDTO.RecommendedInventory> autoSelected =
        recommendations.stream()
            .filter(r -> "AUTO".equals(r.getSelectionMode()))
            .collect(Collectors.toList());
    log.info(
        "Auto-selected inventories for runId {}: count={}, inventories={}",
        runId,
        autoSelected.size(),
        autoSelected);

    return PaginatedRecommendationResponseDTO.builder()
        .runId(runId)
        .campaignId(run.getCampaignId())
        .productId(run.getProductId())
        .companyId(run.getCompanyId())
        .recommendations(recommendations)
        .pagination(paginationInfo)
        .build();
  }

  /**
   * Get all selected inventories (selectionMode AUTO or MANUAL) for a runId as a lightweight
   * measure summary. Returns every selected result in a single list (no pagination, sort, search,
   * or user-facing filters), each carrying only inventoryId, referenceId, cost, and forecast.
   *
   * @param runId Run ID
   * @return All selected inventories projected to {inventoryId, referenceId, cost, forecast}
   */
  public List<SelectedResultMeasureSummaryDTO> getSelectedResultsMeasureSummary(String runId) {

    log.info("Fetching selected-results measure summary for runId: {}", runId);

    // Check if run exists
    RecommendationRun run =
        recommendationRunRepository
            .findByRunId(runId)
            .orElseThrow(
                () ->
                    new BaseException(
                        ErrorCode.RECOMMENDATION_RUN_NOT_FOUND,
                        "Recommendation run not found for runId: " + runId));

    // Check if still in progress
    if (run.getStatus() == RecommendationRun.RunStatus.IN_PROGRESS) {
      throw new BaseException(
          ErrorCode.RECOMMENDATION_IN_PROGRESS,
          "Recommendation is still in progress. Current completion: "
              + run.getCompletionPercentage()
              + "%");
    }

    // Reuse the existing query, restricting to selected inventories only and stripping
    // pagination/sort/search/user-filter (unpaged Pageable, null search, selectionMode-only
    // filter).
    RecommendationResultFilterDTO filter = new RecommendationResultFilterDTO();
    filter.setSelectionModes(List.of(SelectionMode.AUTO, SelectionMode.MANUAL));

    Page<RecommendationResult> resultPage =
        recommendationResultRepository.findByRunIdWithFilters(
            runId, filter, null, Pageable.unpaged());

    return resultPage.getContent().stream()
        .map(
            result ->
                SelectedResultMeasureSummaryDTO.builder()
                    .inventoryId(result.getInventoryId())
                    .referenceId(result.getReferenceId())
                    .cost(mapCost(result))
                    .forecast(mapForecast(result))
                    .build())
        .collect(Collectors.toList());
  }

  /** Null-safe mapping of the persisted cost estimate to the DTO-layer CostEstimate. */
  private PaginatedRecommendationResponseDTO.CostEstimate mapCost(RecommendationResult result) {
    if (result.getCost() == null) {
      return null;
    }
    return PaginatedRecommendationResponseDTO.CostEstimate.builder()
        .estimatedCost(result.getCost().getEstimatedCost())
        .currency(result.getCost().getCurrency())
        .costPerImpression(result.getCost().getCostPerImpression())
        .totalAdPlays(result.getCost().getTotalAdPlays())
        // Raw rate-card rates so the client can recompute cost as flight days change.
        .monthly(result.getCost().getMonthly())
        .daily(result.getCost().getDaily())
        .build();
  }

  /** Null-safe mapping of the persisted forecast to the DTO-layer ForecastedMetrics. */
  private PaginatedRecommendationResponseDTO.ForecastedMetrics mapForecast(
      RecommendationResult result) {
    if (result.getForecast() == null) {
      return null;
    }
    return PaginatedRecommendationResponseDTO.ForecastedMetrics.builder()
        .estimatedImpressions(result.getForecast().getEstimatedImpressions())
        .estimatedReach(result.getForecast().getEstimatedReach())
        .estimatedSov(result.getForecast().getEstimatedSov())
        .estimatedFrequency(result.getForecast().getEstimatedFrequency())
        .build();
  }

  /**
   * Browse inventories directly from MongoDB with pagination. No scoring, no Measure API, no async
   * processing. Returns results in the same shape as recommendation results for frontend
   * compatibility.
   */
  public PaginatedRecommendationResponseDTO browseInventories(
      String campaignId,
      BrowseInventoryRequestDTO request,
      int page,
      int size,
      List<String> sort,
      String search) {

    log.info("Browsing inventories for campaign: {}, page: {}, size: {}", campaignId, page, size);
    long startTime = System.currentTimeMillis();

    Map<String, List<String>> venueTypeIds =
        request.getAudienceTargeting() != null
            ? request.getAudienceTargeting().getVenueTypeIds()
            : null;

    Sort sortObj = buildBrowseSort(sort);
    Pageable pageable = PageRequest.of(page, size, sortObj);

    // Lead-time gap between today and the campaign start drives the leadDays eligibility filter
    // (null when startDate is missing, in which case no leadDays criteria is applied).
    Long availableLeadDays =
        InventoryAvailabilityUtils.availableLeadDays(clock, request.getStartDate());

    Page<Inventory> inventoryPage =
        inventoryRepository.findActiveInventoriesByCountryPaginated(
            request.getCountry(),
            request.getExcludedInventoryIds(),
            request.getGeographyTargeting(),
            venueTypeIds,
            request.getMediaOwnerIds(),
            request.getClassifications(),
            search,
            request,
            availableLeadDays,
            pageable,
            null);

    long totalDays =
        request.getStartDate() != null && request.getEndDate() != null
            ? Math.max(
                1L, ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1)
            : 30;

    List<PaginatedRecommendationResponseDTO.RecommendedInventory> recommendations =
        inventoryPage.getContent().stream()
            .map(
                inv ->
                    inventoryItemMapper.toRecommendedInventory(
                        inv, totalDays, request.getStartDate(), request.getEndDate()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    PaginatedRecommendationResponseDTO.PaginationInfo paginationInfo =
        PaginatedRecommendationResponseDTO.PaginationInfo.builder()
            .page(page)
            .size(size)
            .totalElements(inventoryPage.getTotalElements())
            .totalPages(inventoryPage.getTotalPages())
            .hasNext(inventoryPage.hasNext())
            .hasPrevious(inventoryPage.hasPrevious())
            .build();

    log.info(
        "Browse complete: {} results of {} total in {}ms",
        recommendations.size(),
        inventoryPage.getTotalElements(),
        System.currentTimeMillis() - startTime);

    return PaginatedRecommendationResponseDTO.builder()
        .campaignId(campaignId)
        .productId(request.getProductId())
        .companyId(request.getCompanyId())
        .recommendations(recommendations)
        .pagination(paginationInfo)
        .build();
  }

  private Sort buildBrowseSort(List<String> sortParams) {
    if (sortParams == null || sortParams.isEmpty()) {
      return Sort.by(Sort.Direction.ASC, "name");
    }
    List<Sort.Order> orders = new ArrayList<>();
    for (String param : sortParams) {
      String[] parts = param.split(",");
      String field = parts[0];
      Sort.Direction dir =
          parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
              ? Sort.Direction.DESC
              : Sort.Direction.ASC;
      orders.add(new Sort.Order(dir, field));
    }
    return Sort.by(orders);
  }

  /**
   * Build Sort object from sort parameters. Defaults to finalScore DESC if no sort specified.
   *
   * @param sortParams List of sort parameters in format "field,direction" (e.g., "finalScore,desc")
   * @return Sort object
   */
  private Sort buildSort(List<String> sortParams) {
    // Default sort: finalScore DESC
    if (sortParams == null || sortParams.isEmpty()) {
      return Sort.by(Sort.Direction.DESC, "finalScore");
    }

    List<Sort.Order> orders = new ArrayList<>();

    // Valid sortable fields
    Set<String> validFields =
        Set.of(
            "finalScore",
            "inventoryId",
            "referenceId",
            "name",
            "selectionMode",
            "createdAt",
            "estimatedImpressions",
            "estimatedReach",
            "estimatedFrequency",
            "estimatedCost",
            // Component scores
            "measureFit",
            "geoFit",
            "availability",
            "budgetFit",
            "audienceFit",
            "brandFit",
            "qualityFit",
            "timeFit");

    for (int i = 0; i < sortParams.size(); i++) {
      String sortParam = sortParams.get(i);
      if (sortParam == null || sortParam.trim().isEmpty()) {
        continue;
      }

      String field;
      Sort.Direction direction = Sort.Direction.DESC;

      if (sortParam.contains(",")) {
        String[] parts = sortParam.split(",", 2);
        field = parts[0].trim();
        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
          direction = parseSortDirection(parts[1]);
        }
      } else {
        field = sortParam.trim();
        if (i + 1 < sortParams.size() && isDirectionToken(sortParams.get(i + 1))) {
          direction = parseSortDirection(sortParams.get(i + 1));
          i++;
        }
      }

      // Defensive: skip tokens that are only direction values (e.g., malformed input)
      if (isDirectionToken(field)) {
        log.warn("Sort token '{}' is a direction without a field. Skipping.", field);
        continue;
      }

      // Validate field name
      if (!validFields.contains(field)) {
        log.warn("Invalid sort field: {}. Skipping. Valid fields: {}", field, validFields);
        continue;
      }

      // Handle nested fields
      // Forecast fields
      if ("estimatedImpressions".equals(field)
          || "estimatedReach".equals(field)
          || "estimatedFrequency".equals(field)) {
        field = "forecast." + field;
      }
      // Cost fields
      else if ("estimatedCost".equals(field)) {
        field = "cost." + field;
      }
      // Component score fields
      else if ("measureFit".equals(field)
          || "geoFit".equals(field)
          || "availability".equals(field)
          || "budgetFit".equals(field)
          || "audienceFit".equals(field)
          || "brandFit".equals(field)
          || "qualityFit".equals(field)
          || "timeFit".equals(field)) {
        field = "componentScores." + field;
      }

      orders.add(new Sort.Order(direction, field));
    }

    // If no valid orders, default to finalScore DESC
    if (orders.isEmpty()) {
      return Sort.by(Sort.Direction.DESC, "finalScore");
    }

    return Sort.by(orders);
  }

  private boolean isDirectionToken(String token) {
    if (token == null) {
      return false;
    }
    String normalized = token.trim().toUpperCase();
    return "ASC".equals(normalized)
        || "ASCENDING".equals(normalized)
        || "DESC".equals(normalized)
        || "DESCENDING".equals(normalized);
  }

  private Sort.Direction parseSortDirection(String directionToken) {
    String normalized = directionToken == null ? "" : directionToken.trim().toUpperCase();
    return ("DESC".equals(normalized) || "DESCENDING".equals(normalized))
        ? Sort.Direction.DESC
        : Sort.Direction.ASC;
  }

  /**
   * Map the persisted run status to the response status. IN_PROGRESS and COMPLETED (and any legacy
   * or null value) map exactly as before, so v1 behavior is unchanged; FAILED is surfaced for v2
   * runs, which are the only runs that ever carry it.
   */
  // Package-private for direct regression testing of the v1-neutral status mapping.
  static RecommendationStatusResponseDTO.RunStatus mapRunStatus(
      RecommendationRun.RunStatus status) {
    if (status == RecommendationRun.RunStatus.IN_PROGRESS) {
      return RecommendationStatusResponseDTO.RunStatus.IN_PROGRESS;
    }
    if (status == RecommendationRun.RunStatus.FAILED) {
      return RecommendationStatusResponseDTO.RunStatus.FAILED;
    }
    return RecommendationStatusResponseDTO.RunStatus.COMPLETED;
  }

  /** Build status response from RecommendationRun */
  private RecommendationStatusResponseDTO buildStatusResponse(RecommendationRun run) {
    RecommendationStatusResponseDTO.RecommendationMetadata metadata = null;
    if (run.getMetadata() != null) {
      metadata =
          RecommendationStatusResponseDTO.RecommendationMetadata.builder()
              .totalInventoriesEvaluated(run.getMetadata().getTotalInventoriesEvaluated())
              .totalInventoriesRecommended(run.getMetadata().getTotalInventoriesRecommended())
              .totalInventoriesExcluded(run.getMetadata().getTotalInventoriesExcluded())
              .exclusionReasons(run.getMetadata().getExclusionReasons())
              .averageScore(run.getMetadata().getAverageScore())
              .seed(run.getMetadata().getSeed())
              .autoSelectedInventoryIds(run.getAutoSelectedInventoryIds())
              .build();
    }

    return RecommendationStatusResponseDTO.builder()
        .runId(run.getRunId())
        .status(mapRunStatus(run.getStatus()))
        .completionPercentage(run.getCompletionPercentage())
        .campaignId(run.getCampaignId())
        .productId(run.getProductId())
        .companyId(run.getCompanyId())
        .generatedAt(run.getGeneratedAt())
        .metadata(metadata)
        .warnings(run.getWarnings())
        // Observability passthrough: null until the run completes (and for pre-existing runs).
        .autoSelectionReasonCode(
            run.getAutoSelectionReasonCode() != null
                ? run.getAutoSelectionReasonCode().name()
                : null)
        .autoSelectionReasonDetail(run.getAutoSelectionReasonDetail())
        .build();
  }

  /** Convert RecommendationResult to PaginatedRecommendationResponseDTO.RecommendedInventory */
  private PaginatedRecommendationResponseDTO.RecommendedInventory convertToRecommendedInventory(
      RecommendationResult result) {

    PaginatedRecommendationResponseDTO.ComponentScores componentScores = null;
    if (result.getComponentScores() != null) {
      componentScores =
          PaginatedRecommendationResponseDTO.ComponentScores.builder()
              .measureFit(result.getComponentScores().getMeasureFit())
              .geoFit(result.getComponentScores().getGeoFit())
              .availability(result.getComponentScores().getAvailability())
              .budgetFit(result.getComponentScores().getBudgetFit())
              .audienceFit(result.getComponentScores().getAudienceFit())
              .brandFit(result.getComponentScores().getBrandFit())
              .qualityFit(result.getComponentScores().getQualityFit())
              .timeFit(result.getComponentScores().getTimeFit())
              .build();
    }

    PaginatedRecommendationResponseDTO.AvailabilitySummary availability = null;
    if (result.getAvailability() != null) {
      availability =
          PaginatedRecommendationResponseDTO.AvailabilitySummary.builder()
              .availableDays(result.getAvailability().getAvailableDays())
              .totalDays(result.getAvailability().getTotalDays())
              .availabilityPercentage(result.getAvailability().getAvailabilityPercentage())
              .summary(result.getAvailability().getSummary())
              .allAvailable(result.getAvailability().getAllAvailable())
              .build();
    }

    PaginatedRecommendationResponseDTO.ForecastedMetrics forecast = null;
    if (result.getForecast() != null) {
      forecast =
          PaginatedRecommendationResponseDTO.ForecastedMetrics.builder()
              .estimatedImpressions(result.getForecast().getEstimatedImpressions())
              .estimatedReach(result.getForecast().getEstimatedReach())
              .estimatedSov(result.getForecast().getEstimatedSov())
              .estimatedFrequency(result.getForecast().getEstimatedFrequency())
              .build();
    }

    PaginatedRecommendationResponseDTO.CostEstimate cost = null;
    if (result.getCost() != null) {
      cost =
          PaginatedRecommendationResponseDTO.CostEstimate.builder()
              .estimatedCost(result.getCost().getEstimatedCost())
              .currency(result.getCost().getCurrency())
              .costPerImpression(result.getCost().getCostPerImpression())
              .totalAdPlays(result.getCost().getTotalAdPlays())
              // Raw rate-card rates so the client can recompute cost as flight days change.
              .monthly(result.getCost().getMonthly())
              .daily(result.getCost().getDaily())
              .build();
    }

    PaginatedRecommendationResponseDTO.InventoryDetails inventoryDetails = null;
    if (result.getInventoryDetails() != null) {
      PaginatedRecommendationResponseDTO.InventoryLocation location = null;
      if (result.getInventoryDetails().getLocation() != null) {
        location =
            PaginatedRecommendationResponseDTO.InventoryLocation.builder()
                .countryId(result.getInventoryDetails().getLocation().getCountryId())
                .countryName(result.getInventoryDetails().getLocation().getCountryName())
                .stateId(result.getInventoryDetails().getLocation().getStateId())
                .stateName(result.getInventoryDetails().getLocation().getStateName())
                .cityId(result.getInventoryDetails().getLocation().getCityId())
                .cityName(result.getInventoryDetails().getLocation().getCityName())
                .locationCoordinates(
                    result.getInventoryDetails().getLocation().getLocationCoordinates())
                .build();
      }

      inventoryDetails =
          PaginatedRecommendationResponseDTO.InventoryDetails.builder()
              .name(result.getInventoryDetails().getName())
              .classification(result.getInventoryDetails().getClassification())
              .type(result.getInventoryDetails().getType())
              .format(result.getInventoryDetails().getFormat())
              .environment(result.getInventoryDetails().getEnvironment())
              .venueTypes(result.getInventoryDetails().getVenueTypes())
              .orientation(result.getInventoryDetails().getOrientation())
              .sizes(result.getInventoryDetails().getSizes())
              .resolutions(result.getInventoryDetails().getResolutions())
              .durations(result.getInventoryDetails().getDurations())
              .mediaOwnerId(result.getInventoryDetails().getMediaOwnerId())
              .mediaOwnerName(result.getInventoryDetails().getMediaOwnerName())
              .address(result.getInventoryDetails().getAddress())
              .location(location)
              .cpmRate(result.getInventoryDetails().getCpmRate())
              .spotRate(result.getInventoryDetails().getSpotRate())
              .programmaticDealTypes(result.getInventoryDetails().getProgrammaticDealTypes())
              .venueTypeIds(result.getInventoryDetails().getVenueTypeIds())
              .resolution(result.getInventoryDetails().getResolution())
              .thumbnailUrl(result.getInventoryDetails().getThumbnailUrl())
              .timeZone(result.getInventoryDetails().getTimeZone())
              .bookingMode(result.getInventoryDetails().getBookingMode())
              .spotDuration(result.getInventoryDetails().getSpotDuration())
              .spotsPerLoop(result.getInventoryDetails().getSpotsPerLoop())
              .loopDuration(result.getInventoryDetails().getLoopDuration())
              .loopsPerHour(result.getInventoryDetails().getLoopsPerHour())
              .spotsPerHour(result.getInventoryDetails().getSpotsPerHour())
              .playerCount(result.getInventoryDetails().getPlayerCount())
              .playerSoftwareId(result.getInventoryDetails().getPlayerSoftwareId())
              .playerSoftwareName(result.getInventoryDetails().getPlayerSoftwareName())
              .externalRefIds(mapExternalRefs(result.getInventoryDetails().getExternalRefIds()))
              .deviceId(result.getInventoryDetails().getDeviceId())
              .operatingTimes(result.getInventoryDetails().getOperatingTimes())
              .sellingTerm(result.getInventoryDetails().getSellingTerm())
              .panels(result.getInventoryDetails().getPanels())
              .size(result.getInventoryDetails().getSize())
              .inventoryCluster(result.getInventoryDetails().getInventoryCluster())
              .build();
    }

    String selectionModeStr =
        result.getSelectionMode() != null ? result.getSelectionMode().name() : null;
    return PaginatedRecommendationResponseDTO.RecommendedInventory.builder()
        .inventoryId(result.getInventoryId())
        .referenceId(result.getReferenceId())
        .name(result.getName())
        .finalScore(result.getFinalScore())
        .componentScores(componentScores)
        .why(result.getWhy())
        .availability(availability)
        .forecast(forecast)
        .cost(cost)
        .inventoryDetails(inventoryDetails)
        .isExcluded(result.getIsExcluded() != null && result.getIsExcluded())
        .selectionMode(selectionModeStr)
        .build();
  }

  /**
   * Batch-fetch live operatingTimes (keyed by inventoryId) for the recommendations that are missing
   * them. One DB query for the whole page; returns an empty map when nothing needs enrichment.
   */
  private Map<String, Map<Inventory.Weekday, List<Inventory.OperatingTime>>>
      fetchOperatingTimesByInventoryId(
          List<PaginatedRecommendationResponseDTO.RecommendedInventory> recommendations) {
    List<String> idsNeeding =
        recommendations.stream()
            .filter(
                r ->
                    r != null
                        && r.getInventoryDetails() != null
                        && r.getInventoryDetails().getOperatingTimes() == null
                        && r.getInventoryId() != null)
            .map(PaginatedRecommendationResponseDTO.RecommendedInventory::getInventoryId)
            .distinct()
            .collect(Collectors.toList());
    if (idsNeeding.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, Map<Inventory.Weekday, List<Inventory.OperatingTime>>> map = new HashMap<>();
    for (Inventory inv : inventoryRepository.findByInventoryIdIn(idsNeeding)) {
      if (inv.getInventoryId() != null && inv.getOperatingTimes() != null) {
        map.put(inv.getInventoryId(), inv.getOperatingTimes());
      }
    }
    return map;
  }

  /**
   * Fill operatingTimes on response inventories that are missing it, using a pre-fetched map of
   * live inventory operatingTimes keyed by inventoryId. Runs created before operatingTimes were
   * persisted onto results have null operatingTimes; this read-time enrichment makes /results and
   * /browse always return operating hours regardless of run age. Existing values are never
   * overwritten.
   */
  static void enrichMissingOperatingTimes(
      List<PaginatedRecommendationResponseDTO.RecommendedInventory> recommendations,
      Map<String, Map<Inventory.Weekday, List<Inventory.OperatingTime>>>
          operatingTimesByInventoryId) {
    if (recommendations == null || operatingTimesByInventoryId == null) {
      return;
    }
    for (PaginatedRecommendationResponseDTO.RecommendedInventory rec : recommendations) {
      if (rec == null || rec.getInventoryDetails() == null) {
        continue;
      }
      if (rec.getInventoryDetails().getOperatingTimes() != null) {
        continue; // already present — do not overwrite
      }
      Map<Inventory.Weekday, List<Inventory.OperatingTime>> opTimes =
          operatingTimesByInventoryId.get(rec.getInventoryId());
      if (opTimes != null && !opTimes.isEmpty()) {
        rec.getInventoryDetails().setOperatingTimes(opTimes);
      }
    }
  }

  /**
   * Calculate forecasted metrics (impressions, reach, frequency, SOV) based on DailySummary for the
   * provided date range. DailySummary is keyed by day of month (1-31), so we iterate through each
   * date in the range and sum up the daily values.
   */
  private RecommendationResponseDTO.ForecastedMetrics calculateForecastedMetrics(
      AudienceData audienceData, Inventory inventory, RecommendationRequestDTO request) {

    LocalDate startDate = request.getStartDate();
    LocalDate endDate = request.getEndDate();

    Long estimatedImpressions = null;
    Long estimatedReach = null;
    Double estimatedFrequency = null;

    // Calculate impressions and reach from DailySummary
    if (audienceData != null && audienceData.getDailySummary() != null) {
      long totalImpressions = 0;
      long totalReach = 0;
      int dayCount = 0;

      // Iterate through each date in the range
      for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
        int dayOfMonth = date.getDayOfMonth();
        AudienceData.DailySummary daily = audienceData.getDailySummary().get(dayOfMonth);

        if (daily != null) {
          if (daily.getTotalVisitors() != null) {
            totalImpressions += daily.getTotalVisitors();
          }
          if (daily.getUniqueVisitors() != null) {
            totalReach += daily.getUniqueVisitors();
          }
          dayCount++;
        }
      }

      if (dayCount > 0) {
        estimatedImpressions = totalImpressions;
        estimatedReach = totalReach;

        // Calculate frequency: impressions / reach
        if (estimatedReach > 0 && estimatedImpressions > 0) {
          estimatedFrequency = (double) estimatedImpressions / estimatedReach;
        }
      }
    }

    // Fallback to monthly summary if daily summary not available
    if (estimatedImpressions == null
        && audienceData != null
        && audienceData.getMonthlySummary() != null) {
      long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
      if (audienceData.getMonthlySummary().getTotalVisitors() != null) {
        estimatedImpressions =
            (long) (audienceData.getMonthlySummary().getTotalVisitors() * days / 30.0);
      }
      if (audienceData.getMonthlySummary().getUniqueVisitors() != null) {
        estimatedReach =
            (long) (audienceData.getMonthlySummary().getUniqueVisitors() * days / 30.0);
      }
      if (estimatedReach != null && estimatedReach > 0 && estimatedImpressions != null) {
        estimatedFrequency = (double) estimatedImpressions / estimatedReach;
      }
    }

    return RecommendationResponseDTO.ForecastedMetrics.builder()
        .estimatedImpressions(estimatedImpressions)
        .estimatedReach(estimatedReach)
        .estimatedFrequency(estimatedFrequency)
        .build();
  }

  /**
   * Calculate inventory_ad_plays for a digital inventory based on operating times and digital
   * fields. Same logic as ScoringServiceImpl.
   */
  private Long calculateInventoryAdPlays(
      Inventory inventory, LocalDate startDate, LocalDate endDate) {
    Inventory.DigitalFields digitalFields = inventory.getDigitalFields();
    if (digitalFields == null
        || digitalFields.getSpotDuration() == null
        || digitalFields.getSpotDuration() == 0
        || digitalFields.getSpotsPerLoop() == null
        || digitalFields.getSpotsPerLoop() == 0) {
      return null;
    }

    // Calculate loops per hour: (3600 / spotDuration) / spotsPerLoop
    int loopsPerHour = (3600 / digitalFields.getSpotDuration()) / digitalFields.getSpotsPerLoop();

    // Calculate total hour slots using ceiling of each operating time window
    long totalHourSlots = 0;
    if (inventory.getOperatingTimes() != null && !inventory.getOperatingTimes().isEmpty()) {
      for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
        Inventory.Weekday weekday = getWeekday(date);
        List<Inventory.OperatingTime> times = inventory.getOperatingTimes().get(weekday);
        if (times != null && !times.isEmpty()) {
          for (Inventory.OperatingTime time : times) {
            try {
              java.time.LocalTime start =
                  java.time.LocalTime.parse(
                      time.getStart(), java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
              java.time.LocalTime end =
                  java.time.LocalTime.parse(
                      time.getEnd(), java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
              int startH = start.getHour();
              int endH = end.getHour();
              if (startH <= endH) {
                totalHourSlots += endH - startH + 1;
              } else {
                totalHourSlots += (24 - startH) + endH + 1;
              }
            } catch (Exception e) {
              log.warn(
                  "Error parsing operating time for inventory {}: {}",
                  inventory.getReferenceId(),
                  e.getMessage());
            }
          }
        }
      }
    }

    // Calculate total ad plays: loopsPerHour * totalHourSlots
    long totalAdPlays = loopsPerHour * totalHourSlots;
    return totalAdPlays > 0 ? totalAdPlays : null;
  }

  /** Get weekday from LocalDate */
  private Inventory.Weekday getWeekday(LocalDate date) {
    return switch (date.getDayOfWeek()) {
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
   * Calculate cost estimate using the same logic as mw-planner. Supports all pricing models: - CPM
   * (Cost Per Mille): cost = (CPM / 1000) * impressions - Spot: cost = spot price * number of spots
   * (for digital inventories) - Monthly: cost = monthly * (days / 30) - Daily: cost = daily * days
   * - Weekly: cost = weekly * (days / 7)
   */
  private RecommendationResponseDTO.CostEstimate calculateCostEstimate(
      Inventory inventory,
      LocalDate startDate,
      LocalDate endDate,
      RecommendationResponseDTO.ForecastedMetrics forecast) {

    if (inventory == null || inventory.getPrices() == null || inventory.getPrices().isEmpty()) {
      return RecommendationResponseDTO.CostEstimate.builder()
          .estimatedCost(null)
          .currency(null)
          .costPerImpression(null)
          .build();
    }

    // Calculate estimated cost for the date range
    long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
    BigDecimal estimatedCost = BigDecimal.ZERO;

    // Use first available price model (priority: CPM > Spot > Monthly > Daily > Weekly)
    Inventory.PriceModel price = inventory.getPrices().getFirst();

    // 1. CPM (Cost Per Mille) - requires impressions
    if (price.getCpm() != null && forecast != null && forecast.getEstimatedImpressions() != null) {
      // CPM = cost per 1000 impressions
      // Cost = (CPM / 1000) * impressions
      estimatedCost =
          BigDecimal.valueOf(price.getCpm())
              .divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP)
              .multiply(BigDecimal.valueOf(forecast.getEstimatedImpressions()))
              .setScale(2, RoundingMode.HALF_UP);
    }
    // 2. Spot pricing - for digital inventories, requires ad plays
    else if (price.getSpot() != null
        && inventory.getClassification() != null
        && "Digital".equalsIgnoreCase(inventory.getClassification())) {
      Long adPlays = calculateInventoryAdPlays(inventory, startDate, endDate);
      if (adPlays != null && adPlays > 0) {
        // Cost = spot price * number of spots (ad plays)
        estimatedCost =
            BigDecimal.valueOf(price.getSpot())
                .multiply(BigDecimal.valueOf(adPlays))
                .setScale(2, RoundingMode.HALF_UP);
      }
    }
    // 3. Monthly flat rate
    else if (price.getMonthly() != null) {
      estimatedCost =
          BigDecimal.valueOf(price.getMonthly())
              .multiply(BigDecimal.valueOf(days))
              .divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
    }
    // 4. Daily rate
    else if (price.getDaily() != null) {
      estimatedCost =
          BigDecimal.valueOf(price.getDaily())
              .multiply(BigDecimal.valueOf(days))
              .setScale(2, RoundingMode.HALF_UP);
    }
    // 5. Weekly rate
    else if (price.getWeekly() != null) {
      estimatedCost =
          BigDecimal.valueOf(price.getWeekly())
              .multiply(BigDecimal.valueOf(days))
              .divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);
    }

    // Get currency from price model if available
    String currency = null;
    if (price.getCurrency() != null) {
      currency = price.getCurrency();
    }

    // Calculate cost per impression (if impressions available)
    Double costPerImpression = null;
    if (forecast != null
        && forecast.getEstimatedImpressions() != null
        && forecast.getEstimatedImpressions() > 0
        && estimatedCost.compareTo(BigDecimal.ZERO) > 0) {
      costPerImpression =
          estimatedCost
              .divide(
                  BigDecimal.valueOf(forecast.getEstimatedImpressions()), 4, RoundingMode.HALF_UP)
              .doubleValue();
    }

    return RecommendationResponseDTO.CostEstimate.builder()
        .estimatedCost(estimatedCost.compareTo(BigDecimal.ZERO) == 0 ? null : estimatedCost)
        .currency(currency)
        .costPerImpression(costPerImpression)
        .build();
  }

  /**
   * Mark selected inventories for a recommendation run. If selected inventoryIds are not in
   * existing results (e.g., excluded by geoTargeting), generate recommendations for them and
   * re-sort all results by finalScore.
   *
   * @param runId Run ID
   * @param selectedInventories DTO containing list of selected inventory IDs
   */
  public void markSelectedInventories(String runId, SelectedInventoriesDTO selectedInventories) {
    log.info(
        "Marking selected inventories for runId: {}, count: {}",
        runId,
        selectedInventories.getInventoryIds().size());

    // Check if run exists and is completed
    RecommendationRun run =
        recommendationRunRepository
            .findByRunId(runId)
            .orElseThrow(
                () ->
                    new BaseException(
                        ErrorCode.RECOMMENDATION_RUN_NOT_FOUND,
                        "Recommendation run not found for runId: " + runId));

    if (run.getStatus() == RecommendationRun.RunStatus.IN_PROGRESS) {
      throw new BaseException(
          ErrorCode.RECOMMENDATION_IN_PROGRESS,
          "Cannot mark selected inventories while recommendation is still in progress");
    }

    RecommendationRequestDTO request = run.getRequest();
    String campaignId = run.getCampaignId();
    List<String> selectedInventoryIds = selectedInventories.getInventoryIds();

    if (selectedInventoryIds == null || selectedInventoryIds.isEmpty()) {
      log.warn("No inventory IDs provided for runId: {}", runId);
      return;
    }

    // Get all existing results for this runId
    List<RecommendationResult> existingResults = recommendationResultRepository.findByRunId(runId);
    Set<String> existingInventoryIds =
        existingResults.stream()
            .map(RecommendationResult::getInventoryId)
            .collect(Collectors.toSet());

    // Mark existing results: selectionMode MANUAL for selected, null for others
    List<RecommendationResult> resultsToUpdate = new ArrayList<>();
    for (RecommendationResult result : existingResults) {
      if (selectedInventoryIds.contains(result.getInventoryId())) {
        result.setSelectionMode(SelectionMode.MANUAL);
        resultsToUpdate.add(result);
      } else {
        result.setSelectionMode(null);
        resultsToUpdate.add(result);
      }
    }

    // Find missing inventory IDs (not in existing results)
    List<String> missingInventoryIds =
        selectedInventoryIds.stream()
            .filter(id -> !existingInventoryIds.contains(id))
            .collect(Collectors.toList());

    List<RecommendationResult> newResults = new ArrayList<>();

    // Generate recommendations for missing inventories
    if (!missingInventoryIds.isEmpty()) {
      log.info("Generating recommendations for {} missing inventories", missingInventoryIds.size());

      // Fetch inventories
      List<Inventory> missingInventories = new ArrayList<>();
      for (String inventoryId : missingInventoryIds) {
        inventoryRepository
            .findById(inventoryId)
            .or(() -> inventoryRepository.findByReferenceId(inventoryId))
            .ifPresent(missingInventories::add);
      }

      // Score and create results for missing inventories
      for (Inventory inventory : missingInventories) {
        try {
          // Fetch audience data
          AudienceData audienceData =
              audienceRepository
                  .findByInventoryId(inventory.getInventoryId())
                  .or(() -> audienceRepository.findByReferenceId(inventory.getReferenceId()))
                  .orElse(null);

          // Calculate score
          InventoryScore score = scoringService.calculateScore(inventory, audienceData, request);

          // Apply jitter for deterministic variation
          Double finalScoreWithJitter = VariationUtils.applyJitter(score.getFinalScore(), runId);

          // Build recommendation result (reuse logic from RecommendationAsyncService)
          RecommendationResult result =
              buildRecommendationResultForInventory(
                  inventory, audienceData, score, finalScoreWithJitter, request, runId, campaignId);
          result.setSelectionMode(SelectionMode.MANUAL);
          result.setIsExcluded(
              true); // Mark as excluded since it was not in original recommendations
          newResults.add(result);

        } catch (Exception e) {
          log.warn(
              "Error generating recommendation for inventory {}: {}",
              inventory.getInventoryId(),
              e.getMessage());
        }
      }
    }

    // Merge all results (existing + new). Note: resultsToUpdate contains ALL existing (selected +
    // unselected)
    List<RecommendationResult> allResults = new ArrayList<>(resultsToUpdate);
    allResults.addAll(newResults);

    // Sort all results by finalScore (descending)
    allResults.sort(
        Comparator.comparing(
            RecommendationResult::getFinalScore, Comparator.nullsLast(Comparator.reverseOrder())));

    // Recompute schedule recommendations for selected inventories (budget/goal from run request)
    Set<String> selectedIds = new HashSet<>(selectedInventoryIds);
    if (!selectedIds.isEmpty() && request.getStartDate() != null && request.getEndDate() != null) {
      Map<String, Double> allocation = BudgetAllocationUtils.getEffectiveBudgetAllocation(request);
      Map<String, BigDecimal> budgetShareByInventoryId = null;
      boolean hasBudget =
          request.getBudget() != null && request.getBudget().compareTo(BigDecimal.ZERO) > 0;
      if (hasBudget) {
        BigDecimal totalBudget = request.getBudget();
        Map<String, BigDecimal> caps = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : allocation.entrySet()) {
          double pct = e.getValue() == null ? 0 : e.getValue();
          caps.put(e.getKey(), totalBudget.multiply(BigDecimal.valueOf(pct / 100.0)));
        }
        // Group selected results by allocation key
        Map<String, List<String>> selectedIdsByKey = new LinkedHashMap<>();
        for (RecommendationResult r : allResults) {
          if (!selectedIds.contains(r.getInventoryId())) {
            continue;
          }
          String key = BudgetAllocationUtils.getAllocationKey(r.getInventoryDetails());
          selectedIdsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(r.getInventoryId());
        }
        // Redistribute: keys with no selected inventory contribute to pool
        Set<String> keysWithSelection = selectedIdsByKey.keySet();
        BigDecimal pool = BigDecimal.ZERO;
        for (Map.Entry<String, Double> e : allocation.entrySet()) {
          if (!keysWithSelection.contains(e.getKey())) {
            BigDecimal cap = caps.get(e.getKey());
            if (cap != null) {
              pool = pool.add(cap);
            }
          }
        }
        double weightSum =
            allocation.entrySet().stream()
                .filter(e -> keysWithSelection.contains(e.getKey()))
                .mapToDouble(e -> e.getValue() == null ? 0 : e.getValue())
                .sum();
        if (weightSum > 0 && pool.compareTo(BigDecimal.ZERO) > 0) {
          for (Map.Entry<String, Double> e : allocation.entrySet()) {
            if (!keysWithSelection.contains(e.getKey())) {
              continue;
            }
            double w = e.getValue() == null ? 0 : e.getValue();
            BigDecimal extra = pool.multiply(BigDecimal.valueOf(w / weightSum));
            caps.merge(e.getKey(), extra, BigDecimal::add);
          }
        }
        budgetShareByInventoryId = new HashMap<>();
        for (Map.Entry<String, List<String>> e : selectedIdsByKey.entrySet()) {
          BigDecimal keyCap = caps.getOrDefault(e.getKey(), BigDecimal.ZERO);
          int count = e.getValue().size();
          BigDecimal share =
              count > 0
                  ? keyCap.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                  : BigDecimal.ZERO;
          for (String invId : e.getValue()) {
            budgetShareByInventoryId.put(invId, share);
          }
        }
      }
      List<Inventory> inventoriesForSchedule =
          inventoryRepository.findByInventoryIdIn(new ArrayList<>(selectedIds));
      scheduleRecommendationService.saveSchedulesForRun(
          runId,
          campaignId != null ? campaignId : "",
          scheduleRecommendationService.buildScheduleSummariesForInventories(
              inventoriesForSchedule,
              request.getStartDate(),
              request.getEndDate(),
              budgetShareByInventoryId,
              request.getGoal()));
    }

    // Save all results
    recommendationResultRepository.saveAll(allResults);
    log.info(
        "Updated {} existing results and created {} new results for runId: {}",
        resultsToUpdate.size(),
        newResults.size(),
        runId);
  }

  /**
   * Build RecommendationResult from inventory, audience data, and score. Similar to
   * RecommendationAsyncService.buildRecommendationResult but accepts individual components.
   */
  private RecommendationResult buildRecommendationResultForInventory(
      Inventory inventory,
      AudienceData audienceData,
      InventoryScore score,
      Double finalScoreWithJitter,
      RecommendationRequestDTO request,
      String runId,
      String campaignId) {

    // Build component scores
    RecommendationResult.ComponentScores componentScores =
        RecommendationResult.ComponentScores.builder()
            .measureFit(score.getMeasureFit())
            .geoFit(score.getGeoFit())
            .availability(score.getAvailability())
            .budgetFit(score.getBudgetFit())
            .audienceFit(score.getAudienceFit())
            .brandFit(score.getBrandFit())
            .qualityFit(score.getQualityFit())
            .timeFit(score.getTimeFit())
            .build();

    // Build availability summary
    long totalDays = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1;
    Integer availableDays =
        score.getAvailability() != null ? (int) (totalDays * score.getAvailability() / 100.0) : 0;

    RecommendationResult.AvailabilitySummary availabilitySummary =
        RecommendationResult.AvailabilitySummary.builder()
            .totalDays((int) totalDays)
            .availableDays(availableDays)
            .availabilityPercentage(score.getAvailability())
            .summary(String.format("%d/%d days available", availableDays, totalDays))
            .allAvailable(score.getAvailability() != null && score.getAvailability() >= 100.0)
            .build();

    // Build forecasted metrics via Measure API
    MeasureReachFrequencyResponseDTO measureResponse =
        fetchMeasureReachFrequencyForInventory(inventory, request);
    RecommendationResult.ForecastedMetrics forecast =
        calculateForecastedMetricsForSelectedInventory(measureResponse, inventory, request);

    // Build cost estimate
    RecommendationResult.CostEstimate cost =
        calculateCostEstimateForSelectedInventory(
            inventory, request.getStartDate(), request.getEndDate(), forecast, request.getGoal());

    // Build combined location object (hierarchy + coordinates)
    RecommendationResult.InventoryLocation location = null;
    if (inventory.getLocationHierarchy() != null || inventory.getLocationCoordinates() != null) {
      RecommendationResult.InventoryLocation.InventoryLocationBuilder locationBuilder =
          RecommendationResult.InventoryLocation.builder();

      if (inventory.getLocationHierarchy() != null) {
        locationBuilder
            .countryId(inventory.getLocationHierarchy().getCountryId())
            .countryName(inventory.getLocationHierarchy().getCountryName())
            .stateId(inventory.getLocationHierarchy().getStateId())
            .stateName(inventory.getLocationHierarchy().getStateName())
            .cityId(inventory.getLocationHierarchy().getCityId())
            .cityName(inventory.getLocationHierarchy().getCityName());
      }

      if (inventory.getLocationCoordinates() != null) {
        locationBuilder.locationCoordinates(inventory.getLocationCoordinates());
      }

      location = locationBuilder.build();
    }

    // Convert orientation enum to string
    String orientationStr = null;
    if (inventory.getOrientation() != null) {
      orientationStr = inventory.getOrientation().name().toLowerCase();
    }

    // Extract unique sizes from panels
    List<String> sizes = null;
    if (inventory.getPanels() != null && !inventory.getPanels().isEmpty()) {
      sizes =
          inventory.getPanels().stream()
              .filter(panel -> panel.getSize() != null)
              .map(panel -> panel.getSize().name())
              .distinct()
              .collect(Collectors.toList());
    }

    // Build inventory details object
    RecommendationResult.InventoryDetails inventoryDetails =
        RecommendationResult.InventoryDetails.builder()
            .name(inventory.getName())
            .classification(inventory.getClassification()) // "Digital", "Classic", "Transit"
            .type(inventory.getType()) // "OOH", "Audio", etc.
            .format(inventory.getFormat()) // "LED Billboard", etc.
            .environment(inventory.getEnvironment()) // "outdoor", "indoor"
            .venueTypes(inventory.getVenueTypes())
            .orientation(orientationStr)
            .sizes(sizes)
            .resolutions(deriveResolutions(inventory))
            .durations(deriveDurations(inventory))
            .mediaOwnerId(inventory.getMediaOwnerId())
            .mediaOwnerName(inventory.getMediaOwnerName())
            .address(inventory.getAddress())
            .location(location)
            .cpmRate(
                inventory.getPrices() != null && !inventory.getPrices().isEmpty()
                    ? inventory.getPrices().getFirst().getCpm()
                    : null)
            .spotRate(
                inventory.getPrices() != null && !inventory.getPrices().isEmpty()
                    ? inventory.getPrices().getFirst().getSpot()
                    : null)
            .programmaticDealTypes(
                inventory.getProgrammaticDealTypes() != null
                    ? inventory.getProgrammaticDealTypes().stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.toList())
                    : null)
            .venueTypeIds(inventory.getVenueTypeIds())
            .resolution(deriveResolution(inventory))
            .thumbnailUrl(inventory.getThumbnailUrl())
            .timeZone(inventory.getTimeZone())
            .bookingMode(
                inventory.getDigitalFields() != null
                    ? inventory.getDigitalFields().getBookingMode()
                    : null)
            .spotDuration(
                inventory.getDigitalFields() != null
                    ? inventory.getDigitalFields().getSpotDuration()
                    : null)
            .spotsPerLoop(
                inventory.getDigitalFields() != null
                    ? inventory.getDigitalFields().getSpotsPerLoop()
                    : null)
            .loopDuration(
                inventory.getDigitalFields() != null
                    ? inventory.getDigitalFields().getLoopDuration()
                    : null)
            .loopsPerHour(
                inventory.getDigitalFields() != null
                    ? inventory.getDigitalFields().getLoopsPerHour()
                    : null)
            .spotsPerHour(calcSpotsPerHour(inventory))
            .playerCount(
                inventory.getDigitalFields() != null
                    ? inventory.getDigitalFields().getPlayerCount()
                    : null)
            .playerSoftwareId(
                inventory.getDigitalFields() != null
                    ? inventory.getDigitalFields().getPlayerSoftwareId()
                    : null)
            .playerSoftwareName(
                inventory.getDigitalFields() != null
                    ? inventory.getDigitalFields().getPlayerSoftwareName()
                    : null)
            .externalRefIds(buildExternalRefIds(inventory))
            .deviceId(extractDeviceId(inventory))
            .operatingTimes(inventory.getOperatingTimes())
            .sellingTerm(inventory.getSellingTerm())
            .panels(inventory.getPanels())
            .size(inventory.getSize())
            .inventoryCluster(inventory.getInventoryCluster())
            .build();

    return RecommendationResult.builder()
        .runId(runId)
        .campaignId(campaignId)
        .inventoryId(inventory.getInventoryId())
        .referenceId(inventory.getReferenceId())
        .name(inventory.getName())
        .inventoryDetails(inventoryDetails)
        .finalScore(finalScoreWithJitter)
        .componentScores(componentScores)
        .why(score.getExplanation())
        .availability(availabilitySummary)
        .forecast(forecast)
        .cost(cost)
        .isExcluded(true) // This inventory was excluded during initial recommendation generation
        .createdAt(LocalDateTime.now())
        .build();
  }

  /** Call Measure API for a single inventory and return the response. */
  private MeasureReachFrequencyResponseDTO fetchMeasureReachFrequencyForInventory(
      Inventory inventory, RecommendationRequestDTO request) {

    LocalDate startDate = request.getStartDate();
    LocalDate endDate = request.getEndDate();
    int duration = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;

    String refId =
        inventory.getReferenceId() != null
            ? inventory.getReferenceId()
            : inventory.getInventoryId();

    // List<MeasureInventoryDTO.Dayparts> dayparts = new ArrayList<>();
    // for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
    //   dayparts.add(
    //       MeasureInventoryDTO.Dayparts.builder()
    //           .scheduledDate(d.format(DATE_FORMATTER))
    //           .scheduledTime(null)
    //           .build());
    // }

    MeasureInventoryDTO inventoryDTO =
        MeasureInventoryDTO.builder()
            .referenceId(refId)
            .type(mapClassificationToApiType(inventory.getClassification()))
            .spotsPerHour(calcSpotsPerHour(inventory))
            // .dayparts(dayparts)
            .build();

    MeasureReachFrequencyRequestDTO measureRequest =
        MeasureReachFrequencyRequestDTO.builder()
            .inventories(List.of(inventoryDTO))
            .duration(duration)
            .build();

    List<MeasureReachFrequencyResponseDTO> responses =
        measureApiClient.getReachAndFrequencyBySites(measureRequest, true);

    if (responses == null || responses.isEmpty()) {
      return null;
    }
    return responses.stream()
        .filter(r -> refId.equals(r.getReferenceId()))
        .findFirst()
        .orElse(null);
  }

  /** Calculate forecasted metrics for selected inventory using Measure API response */
  private RecommendationResult.ForecastedMetrics calculateForecastedMetricsForSelectedInventory(
      MeasureReachFrequencyResponseDTO measureResponse,
      Inventory inventory,
      RecommendationRequestDTO request) {

    Long estimatedImpressions = null;
    Long estimatedReach = null;
    Double estimatedFrequency = null;

    if (measureResponse != null && "success".equalsIgnoreCase(measureResponse.getStatus())) {
      estimatedImpressions = measureResponse.getImpressions();
      estimatedReach = measureResponse.getReach();

      if (estimatedImpressions != null
          && estimatedReach != null
          && estimatedReach > 0
          && estimatedImpressions > 0) {
        estimatedFrequency = (double) estimatedImpressions / estimatedReach;
      }
    }

    return RecommendationResult.ForecastedMetrics.builder()
        .estimatedImpressions(estimatedImpressions)
        .estimatedReach(estimatedReach)
        .estimatedFrequency(estimatedFrequency)
        .build();
  }

  /** Calculate cost estimate for selected inventory */
  private RecommendationResult.CostEstimate calculateCostEstimateForSelectedInventory(
      Inventory inventory,
      LocalDate startDate,
      LocalDate endDate,
      RecommendationResult.ForecastedMetrics forecast,
      RecommendationRequestDTO.CampaignGoal goal) {

    if (inventory == null || inventory.getPrices() == null || inventory.getPrices().isEmpty()) {
      return RecommendationResult.CostEstimate.builder()
          .estimatedCost(null)
          .currency(null)
          .costPerImpression(null)
          .totalAdPlays(null)
          .build();
    }

    long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
    BigDecimal estimatedCost = BigDecimal.ZERO;
    Inventory.PriceModel price = inventory.getPrices().getFirst();
    Long totalAdPlays = calculateInventoryAdPlaysForSelected(inventory, startDate, endDate);

    boolean goalBasedCalculated = false;

    // Goal-based pricing model selection
    if (goal == RecommendationRequestDTO.CampaignGoal.IMPRESSIONS
        || goal == RecommendationRequestDTO.CampaignGoal.REACH) {
      if (price.getCpm() != null && price.getCpm() > 0) {
        goalBasedCalculated = true;
        if (forecast != null && forecast.getEstimatedImpressions() != null) {
          estimatedCost =
              BigDecimal.valueOf(price.getCpm())
                  .divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP)
                  .multiply(BigDecimal.valueOf(forecast.getEstimatedImpressions()))
                  .setScale(2, RoundingMode.HALF_UP);
        }
      }
    } else if (goal == RecommendationRequestDTO.CampaignGoal.SOV
        || goal == RecommendationRequestDTO.CampaignGoal.AD_PLAYS) {
      if (price.getSpot() != null
          && price.getSpot() > 0
          && inventory.getClassification() != null
          && "Digital".equalsIgnoreCase(inventory.getClassification())) {
        goalBasedCalculated = true;
        if (totalAdPlays != null && totalAdPlays > 0) {
          estimatedCost =
              BigDecimal.valueOf(price.getSpot())
                  .multiply(BigDecimal.valueOf(totalAdPlays))
                  .setScale(2, RoundingMode.HALF_UP);
        }
      }
    }
    // Fall through to existing priority flow if goal-based calculation did not apply
    if (!goalBasedCalculated) {
      // 1. CPM
      if (price.getCpm() != null
          && forecast != null
          && forecast.getEstimatedImpressions() != null) {
        estimatedCost =
            BigDecimal.valueOf(price.getCpm())
                .divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(forecast.getEstimatedImpressions()))
                .setScale(2, RoundingMode.HALF_UP);
      }
      // 2. Spot pricing
      else if (price.getSpot() != null
          && inventory.getClassification() != null
          && "Digital".equalsIgnoreCase(inventory.getClassification())) {
        if (totalAdPlays != null && totalAdPlays > 0) {
          estimatedCost =
              BigDecimal.valueOf(price.getSpot())
                  .multiply(BigDecimal.valueOf(totalAdPlays))
                  .setScale(2, RoundingMode.HALF_UP);
        }
      }
      // 3. Monthly
      else if (price.getMonthly() != null) {
        estimatedCost =
            BigDecimal.valueOf(price.getMonthly())
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
      }
      // 4. Daily
      else if (price.getDaily() != null) {
        estimatedCost =
            BigDecimal.valueOf(price.getDaily())
                .multiply(BigDecimal.valueOf(days))
                .setScale(2, RoundingMode.HALF_UP);
      }
      // 5. Weekly
      else if (price.getWeekly() != null) {
        estimatedCost =
            BigDecimal.valueOf(price.getWeekly())
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);
      }
    }

    String currency = price.getCurrency();

    Double costPerImpression = null;
    if (forecast != null
        && forecast.getEstimatedImpressions() != null
        && forecast.getEstimatedImpressions() > 0
        && estimatedCost.compareTo(BigDecimal.ZERO) > 0) {
      costPerImpression =
          estimatedCost
              .divide(
                  BigDecimal.valueOf(forecast.getEstimatedImpressions()), 4, RoundingMode.HALF_UP)
              .doubleValue();
    }

    return RecommendationResult.CostEstimate.builder()
        .estimatedCost(estimatedCost.compareTo(BigDecimal.ZERO) == 0 ? null : estimatedCost)
        .currency(currency)
        .costPerImpression(costPerImpression)
        .totalAdPlays(totalAdPlays)
        .build();
  }

  /** Calculate inventory ad plays for selected inventory */
  private Long calculateInventoryAdPlaysForSelected(
      Inventory inventory, LocalDate startDate, LocalDate endDate) {
    Inventory.DigitalFields digitalFields = inventory.getDigitalFields();
    if (digitalFields == null
        || digitalFields.getSpotDuration() == null
        || digitalFields.getSpotDuration() == 0
        || digitalFields.getSpotsPerLoop() == null
        || digitalFields.getSpotsPerLoop() == 0) {
      return null;
    }

    // Calculate loops per hour: (3600 / spotDuration) / spotsPerLoop
    int loopsPerHour = (3600 / digitalFields.getSpotDuration()) / digitalFields.getSpotsPerLoop();

    // Calculate total hour slots using ceiling of each operating time window
    long totalHourSlots = 0;
    if (inventory.getOperatingTimes() != null && !inventory.getOperatingTimes().isEmpty()) {
      for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
        Inventory.Weekday weekday = getWeekdayForSelected(date);
        List<Inventory.OperatingTime> times = inventory.getOperatingTimes().get(weekday);
        if (times != null && !times.isEmpty()) {
          for (Inventory.OperatingTime time : times) {
            try {
              java.time.LocalTime start =
                  java.time.LocalTime.parse(
                      time.getStart(), java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
              java.time.LocalTime end =
                  java.time.LocalTime.parse(
                      time.getEnd(), java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
              int startH = start.getHour();
              int endH = end.getHour();
              if (startH <= endH) {
                totalHourSlots += endH - startH + 1;
              } else {
                totalHourSlots += (24 - startH) + endH + 1;
              }
            } catch (Exception e) {
              log.warn(
                  "Error parsing operating time for inventory {}: {}",
                  inventory.getReferenceId(),
                  e.getMessage());
            }
          }
        }
      }
    }

    // Calculate total ad plays: loopsPerHour * totalHourSlots
    long totalAdPlays = loopsPerHour * totalHourSlots;
    return totalAdPlays > 0 ? totalAdPlays : null;
  }

  /** Get weekday enum from LocalDate */
  private Inventory.Weekday getWeekdayForSelected(LocalDate date) {
    return switch (date.getDayOfWeek()) {
      case MONDAY -> Inventory.Weekday.MONDAY;
      case TUESDAY -> Inventory.Weekday.TUESDAY;
      case WEDNESDAY -> Inventory.Weekday.WEDNESDAY;
      case THURSDAY -> Inventory.Weekday.THURSDAY;
      case FRIDAY -> Inventory.Weekday.FRIDAY;
      case SATURDAY -> Inventory.Weekday.SATURDAY;
      case SUNDAY -> Inventory.Weekday.SUNDAY;
    };
  }

  private String deriveResolution(Inventory inventory) {
    if (inventory.getPanels() == null || inventory.getPanels().isEmpty()) return null;
    Inventory.Panel first = inventory.getPanels().getFirst();
    if (first.getPixelWidth() != null && first.getPixelHeight() != null)
      return first.getPixelWidth() + "x" + first.getPixelHeight();
    return null;
  }

  private List<String> deriveResolutions(Inventory inventory) {
    if (inventory.getPanels() == null || inventory.getPanels().isEmpty()) return null;
    return inventory.getPanels().stream()
        .filter(p -> p.getPixelWidth() != null && p.getPixelHeight() != null)
        .map(p -> p.getPixelWidth() + "x" + p.getPixelHeight())
        .distinct()
        .collect(Collectors.toList());
  }

  private List<Integer> deriveDurations(Inventory inventory) {
    if (inventory.getPrices() == null || inventory.getPrices().isEmpty()) return null;
    return inventory.getPrices().stream()
        .map(Inventory.PriceModel::getDurationSeconds)
        .filter(duration -> duration != null)
        .distinct()
        .sorted()
        .collect(Collectors.toList());
  }

  private Integer calcSpotsPerHour(Inventory inventory) {
    Inventory.DigitalFields df = inventory.getDigitalFields();
    if (df == null
        || df.getSpotDuration() == null
        || df.getSpotDuration() == 0
        || df.getSpotsPerLoop() == null
        || df.getSpotsPerLoop() == 0) return null;
    return (3600 / df.getSpotDuration()) / df.getSpotsPerLoop();
  }

  private static String mapClassificationToApiType(String classification) {
    if (classification == null) return "billboard";
    String c = classification.toLowerCase();
    if (c.contains("classic")) return "static_billboard";
    if (c.contains("network")) return "network";
    return "billboard";
  }

  private List<RecommendationResult.ExternalRef> buildExternalRefIds(Inventory inventory) {
    if (inventory.getExternalIds() == null || inventory.getExternalIds().isEmpty()) return null;
    return inventory.getExternalIds().stream()
        .map(
            e ->
                RecommendationResult.ExternalRef.builder()
                    .source(e.getPlatform())
                    .externalRefId(e.getExternalRefId())
                    .build())
        .collect(Collectors.toList());
  }

  private String extractDeviceId(Inventory inventory) {
    if (inventory.getExternalIds() == null) return null;
    return inventory.getExternalIds().stream()
        .filter(e -> e.getPlatform() != null && e.getPlatform().equalsIgnoreCase("CMS"))
        .map(Inventory.ExternalId::getExternalRefId)
        .findFirst()
        .orElse(null);
  }

  private List<PaginatedRecommendationResponseDTO.ExternalRef> mapExternalRefs(
      List<RecommendationResult.ExternalRef> refs) {
    if (refs == null) return null;
    return refs.stream()
        .map(
            r ->
                PaginatedRecommendationResponseDTO.ExternalRef.builder()
                    .source(r.getSource())
                    .externalRefId(r.getExternalRefId())
                    .build())
        .collect(Collectors.toList());
  }
}
