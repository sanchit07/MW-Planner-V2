package com.mw.recommendation.engine.service;

import com.mw.brand.lib.dto.BrandResponseDTO;
import com.mw.recommendation.engine.domain.AudienceData;
import com.mw.recommendation.engine.domain.BookingData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.domain.RecommendationRun;
import com.mw.recommendation.engine.domain.SelectionMode;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.dto.ScheduleSummaryDTO;
import com.mw.recommendation.engine.dto.measure.MeasureInventoryDTO;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyRequestDTO;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyResponseDTO;
import com.mw.recommendation.engine.repository.AudienceRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
import com.mw.recommendation.engine.repository.RecommendationResultRepository;
import com.mw.recommendation.engine.repository.RecommendationRunRepository;
import com.mw.recommendation.engine.util.BoundedExecutor;
import com.mw.recommendation.engine.util.BudgetAllocationUtils;
import com.mw.recommendation.engine.util.InventoryAvailabilityUtils;
import com.mw.recommendation.engine.util.PerformanceProfiler;
import com.mw.recommendation.engine.util.RequestHashUtils;
import com.mw.recommendation.engine.util.VariationUtils;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for handling asynchronous recommendation processing. Separated from RecommendationService
 * to ensure @Async works properly (avoids self-invocation issues).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationAsyncServiceV2 {
  private static final int BATCH_SIZE = 1500;

  /** Score thresholds for batch processing. Inventories are processed in descending bands. */
  private static final int[] SCORE_THRESHOLDS = {90, 80, 70, 60, 50, 40, 30, 20, 10};

  /** Minimum score for auto-selection; inventories at or below this score are not recommended. */
  private static final double MIN_RECOMMENDATION_SCORE = 10.0;

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final InventoryRepository inventoryRepository;
  private final AudienceRepository audienceRepository;
  private final ScoringService scoringService;
  private final RecommendationRunRepository recommendationRunRepository;
  private final RecommendationResultRepository recommendationResultRepository;
  private final ScheduleRecommendationService scheduleRecommendationService;
  private final VirtualThreadTaskExecutor virtualThreadTaskExecutor;
  private final MongoTemplate mongoTemplate;
  // v2 uses its own Measure client with bounded timeouts + retry (finding #3); the shared
  // MeasureApiClient (10-min timeout) is left for v1.
  private final MeasureClientV2 measureClientV2;
  private final Clock clock;
  private final AutoSelectionReasonResolver autoSelectionReasonResolver;

  // Scoring fan-out bounds (findings #2, #5). Field initializers are the effective defaults when
  // the
  // bean is built without Spring property binding (e.g. unit tests via @InjectMocks); @Value
  // overrides them from configuration at runtime.
  @org.springframework.beans.factory.annotation.Value(
      "${mw-recommendation-engine.v2.scoring.chunk-size:2000}")
  private int scoringChunkSize = 2000;

  @org.springframework.beans.factory.annotation.Value(
      "${mw-recommendation-engine.v2.scoring.max-concurrency:64}")
  private int scoringMaxConcurrency = 64;

  /**
   * Process recommendations asynchronously with completion tracking
   *
   * @param runId Run ID
   * @param campaignId Campaign ID
   * @param request Recommendation request
   */
  @Async
  public void processRecommendationsAsyncOptimized(
      String runId, String campaignId, RecommendationRequestDTO request) {

    // Initialize performance profiler
    PerformanceProfiler profiler = new PerformanceProfiler("v2|runId=" + runId);

    try {
      log.info("Starting async processing for runId: {}", runId);
      updateCompletionPercentage(runId, 10);

      List<String> warnings = new ArrayList<>();
      Map<String, Integer> exclusionReasons = new HashMap<>();

      // Extract venueTypeIds from audience targeting (filter at repository level, like geography)
      Map<String, List<String>> venueTypeIds = null;
      if (request.getAudienceTargeting() != null) {
        venueTypeIds = request.getAudienceTargeting().getVenueTypeIds();
      }

      // Derive classifications from budgetAllocation when not explicitly set.
      // Budget keys with non-zero allocation (e.g. "classic") are capitalized to match
      // inventory.classification values ("Classic", "Digital", "Transit").
      List<String> effectiveClassifications = request.getClassifications();
      if ((effectiveClassifications == null || effectiveClassifications.isEmpty())
          && request.getBudgetAllocation() != null
          && !request.getBudgetAllocation().isEmpty()) {
        effectiveClassifications =
            request.getBudgetAllocation().entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .map(
                    e -> {
                      String key = e.getKey().trim().toLowerCase();
                      return Character.toUpperCase(key.charAt(0)) + key.substring(1);
                    })
                .collect(java.util.stream.Collectors.toList());
        log.info("Derived classifications {} from budgetAllocation keys", effectiveClassifications);
      }

      // Inclusive campaign duration drives the minDays availability filter (null when either date
      // is missing, in which case no minDays criteria is applied — backward compatible).
      Long durationDays =
          InventoryAvailabilityUtils.inclusiveDurationDays(
              request.getStartDate(), request.getEndDate());

      // Lead-time gap between today and the campaign start drives the leadDays eligibility filter
      // (null when startDate is missing, in which case no leadDays criteria is applied — backward
      // compatible). NOTE: leadDays depends on "today", which is not part of the dedup hash; a
      // reused run created on an earlier day reflects that day's eligibility (documented,
      // accepted).
      Long availableLeadDays =
          InventoryAvailabilityUtils.availableLeadDays(clock, request.getStartDate());

      // Programmatic filters: dsps (global) and programmaticEnabled (refines the Digital slice).
      List<String> dsps = request.getDsps();
      boolean programmaticEnabled = Boolean.TRUE.equals(request.getProgrammaticEnabled());

      // PROFILING: Fetch inventories
      profiler.startStep("1_FetchInventories");
      List<Inventory> inventories = null;
      try {
        inventories =
            fetchInventoriesByCountryWithFilters(
                request.getCountry(),
                request.getExcludedInventoryIds(),
                request.getGeographyTargeting(),
                venueTypeIds,
                request.getMediaOwnerIds(),
                effectiveClassifications,
                request.getSearchKeywords(),
                durationDays,
                availableLeadDays,
                request.getStartDate(),
                request.getEndDate(),
                exclusionReasons,
                request.getGoal(),
                dsps,
                programmaticEnabled);
      } finally {
        profiler.endStep(
            "1_FetchInventories",
            Map.of("inventoryCount", inventories != null ? inventories.size() : 0));
      }

      updateCompletionPercentage(runId, 30);

      if (inventories.isEmpty()) {
        log.warn("No inventories found for country: {}", request.getCountry());
        // Observability only: derive the auto-selection reason (NO_CANDIDATE_INVENTORIES) for the
        // fetch-zero path so these runs are queryable too.
        AutoSelectionReasonResolver.ReasonResolution emptyReason =
            autoSelectionReasonResolver.resolve(
                "v2", runId, List.of(), request, List.of(), 0, Map.of(), MIN_RECOMMENDATION_SCORE);
        completeRunWithEmptyResults(runId, campaignId, exclusionReasons, warnings, emptyReason);
        profiler.logSummary();
        return;
      }

      log.info("Found {} inventories for country: {}", inventories.size(), request.getCountry());

      // Build inventory lookup map for auto-selection (avoid re-fetching)
      Map<String, Inventory> inventoryMap =
          inventories.stream()
              .collect(Collectors.toMap(Inventory::getInventoryId, inv -> inv, (a, b) -> a));

      // Brand data is per-brand (not per-inventory), so fetch it once up front. Audience and
      // booking
      // data are fetched per chunk inside scoreInventoriesChunked so peak memory is bounded to one
      // chunk instead of the whole catalogue (finding #5).
      profiler.startStep("2a_BatchFetchBrand");
      final Map<String, BrandResponseDTO> brandDataById =
          request.getBrandId() != null
              ? scoringService.batchFetchBrandData(List.of(request.getBrandId()))
              : new HashMap<>();
      profiler.endStep("2a_BatchFetchBrand", Map.of("brandCount", brandDataById.size()));

      // PROFILING: Score all inventories in bounded chunks (findings #2, #5, #14)
      profiler.startStep("2b_ScoreInventories_Parallel");
      int totalInventories = inventories.size();
      // V2 OPTIMIZATION (deterministic output): seed jitter from the request-body hash instead of
      // the runId, so the SAME request body yields the SAME jittered scores on every run regardless
      // of runId/campaignId. hashRequest excludes campaignId and topN.
      final String jitterSeed = RequestHashUtils.hashRequest(request);
      // Availability-excluded inventories are kept (not silently dropped) so they can be
      // persisted as isExcluded=true results for planner-facing "unavailable" explanations.
      final List<ScoredInventory> availabilityExcludedInventories =
          Collections.synchronizedList(new ArrayList<>());
      List<ScoredInventory> scoredInventories;
      try {
        scoredInventories =
            scoreInventoriesChunked(
                inventories,
                request,
                brandDataById,
                jitterSeed,
                exclusionReasons,
                runId,
                availabilityExcludedInventories);
      } finally {
        profiler.endStep(
            "2b_ScoreInventories_Parallel", Map.of("totalInventories", totalInventories));
      }

      updateCompletionPercentage(runId, 85);

      // PROFILING: Sort and filter
      profiler.startStep("3_SortAndFilter");
      List<ScoredInventory> finalScoredInventories = scoredInventories;
      try {
        // Sort by final score (descending)
        scoredInventories.sort(
            Comparator.comparing(ScoredInventory::finalScoreWithJitter).reversed());

        // Apply topN limit if specified
        if (request.getTopN() != null && request.getTopN() > 0) {
          finalScoredInventories =
              scoredInventories.stream().limit(request.getTopN()).collect(Collectors.toList());
        } else {
          finalScoredInventories = scoredInventories;
        }
      } finally {
        profiler.endStep("3_SortAndFilter", Map.of("afterTopN", finalScoredInventories.size()));
      }

      // Batch-call Measure API for reach/frequency/impressions across all scored inventories
      Map<String, MeasureReachFrequencyResponseDTO> measureResponseByRefId =
          fetchMeasureReachFrequency(finalScoredInventories, request);

      // PROFILING: Build recommendation results
      profiler.startStep("4_BuildResults");
      List<RecommendationResult> results = new ArrayList<>();
      double totalScore = 0.0;
      try {
        for (ScoredInventory scored : finalScoredInventories) {
          RecommendationResult result =
              buildRecommendationResult(
                  scored, request, runId, campaignId, measureResponseByRefId, false);
          if (result != null) {
            results.add(result);
            totalScore += scored.finalScoreWithJitter();
          }
        }
      } finally {
        profiler.endStep(
            "4_BuildResults", Map.of("resultCount", results != null ? results.size() : 0));
      }

      // Availability-excluded inventories: persisted as isExcluded=true results (kept OUT of
      // `results` so auto-selection and scoring stats never see them) so planners get a clear
      // "unavailable for your dates" explanation instead of the item silently vanishing.
      List<RecommendationResult> excludedResults = new ArrayList<>();
      for (ScoredInventory scored : availabilityExcludedInventories) {
        RecommendationResult excludedResult =
            buildRecommendationResult(scored, request, runId, campaignId, Map.of(), true);
        if (excludedResult != null) {
          excludedResult.setSelectionMode(null);
          excludedResults.add(excludedResult);
        }
      }

      updateCompletionPercentage(runId, 85);

      // PROFILING: Auto-select phase
      profiler.startStep("5_AutoSelection");
      List<String> autoSelectedIds = null;
      try {
        autoSelectedIds =
            applyBudgetAwareAutoSelect(results, request, runId, campaignId, inventoryMap);
      } finally {
        profiler.endStep(
            "5_AutoSelection",
            Map.of("autoSelectedCount", autoSelectedIds != null ? autoSelectedIds.size() : 0));
      }

      // Observability only: derive WHY auto-selection produced this selection, purely from its
      // observable inputs/outputs (no selection logic is re-run or instrumented).
      AutoSelectionReasonResolver.ReasonResolution autoSelectionReason =
          autoSelectionReasonResolver.resolve(
              "v2",
              runId,
              results,
              request,
              autoSelectedIds,
              finalScoredInventories.size(),
              measureResponseByRefId,
              MIN_RECOMMENDATION_SCORE);

      // V2 OPTIMIZATION (single-pass selectionMode): stamp selectionMode onto each result IN MEMORY
      // from autoSelectedIds BEFORE inserting, so the persisted documents already carry the correct
      // AUTO/null value. This eliminates v1's redundant second full-collection
      // bulkUpdateSelectionMode pass — the final stored state is identical (a result is AUTO iff it
      // is in autoSelectedIds, otherwise null), but every document is written exactly once.
      int autoCount = 0;
      for (RecommendationResult r : results) {
        if (autoSelectedIds != null && autoSelectedIds.contains(r.getInventoryId())) {
          r.setSelectionMode(SelectionMode.AUTO);
          autoCount++;
        } else {
          r.setSelectionMode(null);
        }
      }
      log.info(
          "[V2] selectionMode stamped in-memory before insert - total={}, auto={}, null={}",
          results.size(),
          autoCount,
          (results.size() - autoCount));

      // PROFILING: Save results (single write pass — selectionMode already set)
      profiler.startStep("6_SaveResults");
      try {
        bulkInsertResults(results);
        if (!excludedResults.isEmpty()) {
          bulkInsertResults(excludedResults);
          log.info(
              "Persisted {} availability-excluded results for runId: {}",
              excludedResults.size(),
              runId);
        }
      } finally {
        profiler.endStep("6_SaveResults", Map.of("savedCount", results.size()));
      }
      updateCompletionPercentage(runId, 95);

      // Calculate average score
      double averageScore = results.isEmpty() ? 0.0 : totalScore / results.size();

      // PROFILING: Complete run
      profiler.startStep("8_CompleteRun");
      try {
        // Update run with completion and auto-selected IDs
        RecommendationRun.RecommendationMetadata metadata =
            RecommendationRun.RecommendationMetadata.builder()
                .totalInventoriesEvaluated(inventories.size())
                .totalInventoriesRecommended(results.size())
                .totalInventoriesExcluded(
                    exclusionReasons.values().stream().mapToInt(Integer::intValue).sum())
                .exclusionReasons(exclusionReasons)
                .averageScore(averageScore)
                .seed(runId)
                .build();

        completeRun(runId, metadata, warnings, autoSelectedIds, autoSelectionReason);
      } finally {
        profiler.endStep("8_CompleteRun");
      }

      log.info("Completed processing for runId: {}", runId);

      // Log performance summary
      profiler.logSummary();

    } catch (Exception e) {
      log.error("Error processing recommendations for runId {}: {}", runId, e.getMessage(), e);
      // Mark the run FAILED via a targeted $set so it reaches a terminal state instead of hanging
      // IN_PROGRESS forever (finding #1). v1 never sets FAILED, so v1 behavior is unchanged.
      failRun(runId, PROCESSING_ERROR_CODE, e.getMessage());

      // Log partial profiling data on error
      try {
        profiler.logSummary();
      } catch (Exception logEx) {
        log.warn("Failed to log performance summary on error: {}", logEx.getMessage());
      }
    }
  }

  /** Error code recorded on a run that failed during async processing. */
  static final String PROCESSING_ERROR_CODE = "PROCESSING_ERROR";

  /**
   * Terminal FAILED update via a targeted {@code $set} (no read-modify-write). Guarantees an
   * errored v2 run stops being IN_PROGRESS (finding #1). Only the v2 pipeline calls this, so v1 is
   * untouched.
   */
  private void failRun(String runId, String errorCode, String errorMessage) {
    mongoTemplate.updateFirst(
        Query.query(Criteria.where("runId").is(runId)),
        new Update()
            .set("status", RecommendationRun.RunStatus.FAILED)
            .set("completionPercentage", 0)
            .set("completedAt", LocalDateTime.now())
            .set("errorCode", errorCode)
            .set("errorMessage", errorMessage == null ? "Unknown error" : errorMessage),
        RecommendationRun.class);
  }

  /**
   * Score inventories in bounded chunks. Each chunk fetches only its own audience/booking data and
   * releases it before the next chunk (finding #5 — peak memory bounded to a chunk, not the whole
   * catalogue), scores with bounded concurrency (finding #2 — no unbounded per-inventory fan-out),
   * and collects results without a shared lock (finding #14). Output is identical to scoring the
   * whole set at once: every inventory is scored with the same data, results are accumulated and
   * sorted downstream, and the jitter seed is request-based, so ordering and ties are deterministic
   * regardless of chunking or concurrency.
   */
  private List<ScoredInventory> scoreInventoriesChunked(
      List<Inventory> inventories,
      RecommendationRequestDTO request,
      Map<String, BrandResponseDTO> brandData,
      String jitterSeed,
      Map<String, Integer> exclusionReasons,
      String runId,
      List<ScoredInventory> availabilityExcludedOut) {

    int total = inventories.size();
    List<ScoredInventory> scored = new ArrayList<>(total);
    AtomicInteger processed = new AtomicInteger(0);
    AtomicInteger scoringErrors = new AtomicInteger(0);
    AtomicInteger availabilityExcluded = new AtomicInteger(0);
    int progressStep = Math.max(1, total / 4);

    BoundedExecutor boundedExecutor =
        new BoundedExecutor(virtualThreadTaskExecutor, scoringMaxConcurrency);
    List<List<Inventory>> chunks = partitionList(inventories, Math.max(1, scoringChunkSize));

    for (List<Inventory> chunk : chunks) {
      List<String> chunkIds =
          chunk.stream().map(Inventory::getInventoryId).collect(Collectors.toList());

      // Per-chunk enrichment — these maps are dropped (GC-eligible) before the next chunk.
      Map<String, AudienceData> audienceByInventoryId = new HashMap<>();
      Map<String, AudienceData> audienceByReferenceId = new HashMap<>();
      batchFetchAudienceDataInParallel(
          chunkIds, new ArrayList<>(), 1000, audienceByInventoryId, audienceByReferenceId);
      Map<String, List<BookingData>> bookingByInventoryId =
          scoringService.batchFetchBookingData(
              chunkIds, request.getStartDate(), request.getEndDate());

      List<Supplier<ScoredInventory>> tasks = new ArrayList<>(chunk.size());
      for (Inventory inventory : chunk) {
        tasks.add(
            () -> {
              try {
                AudienceData audienceData =
                    audienceByInventoryId.getOrDefault(
                        inventory.getInventoryId(),
                        audienceByReferenceId.get(inventory.getReferenceId()));
                InventoryScore score =
                    scoringService.calculateScore(
                        inventory, audienceData, request, bookingByInventoryId, brandData);
                // Availability-aware exclusion: sold out / blocked for the plan's dates.
                if (score.getAvailability() != null
                    && score.getAvailability()
                        < RecommendationAsyncService.AVAILABILITY_EXCLUDE_BELOW_PCT
                    && bookingByInventoryId.containsKey(inventory.getInventoryId())) {
                  availabilityExcluded.incrementAndGet();
                  availabilityExcludedOut.add(
                      new ScoredInventory(inventory, audienceData, score, 0.0));
                  return null;
                }
                Double finalScoreWithJitter =
                    VariationUtils.applyJitter(score.getFinalScore(), jitterSeed);
                return new ScoredInventory(inventory, audienceData, score, finalScoreWithJitter);
              } catch (Exception e) {
                log.warn(
                    "Error scoring inventory {}: {}", inventory.getReferenceId(), e.getMessage());
                scoringErrors.incrementAndGet();
                return null;
              } finally {
                int current = processed.incrementAndGet();
                if (current % progressStep == 0 || current == total) {
                  updateCompletionPercentage(runId, 30 + (int) (50.0 * current / total));
                }
              }
            });
      }

      List<ScoredInventory> chunkResults = boundedExecutor.invokeAll(tasks);
      for (ScoredInventory si : chunkResults) {
        if (si != null) {
          scored.add(si);
        }
      }
    }

    if (scoringErrors.get() > 0) {
      exclusionReasons.merge("SCORING_ERROR", scoringErrors.get(), Integer::sum);
    }
    if (availabilityExcluded.get() > 0) {
      exclusionReasons.merge("AVAILABILITY_UNAVAILABLE", availabilityExcluded.get(), Integer::sum);
    }
    log.info(
        "v2 chunked scoring complete: scored={}, errors={}, chunks={}, chunkSize={}, maxConcurrency={}",
        scored.size(),
        scoringErrors.get(),
        chunks.size(),
        scoringChunkSize,
        scoringMaxConcurrency);
    return scored;
  }

  /**
   * Synchronous BROWSE mode: fetch inventories from local MongoDB without scoring, Measure API, or
   * auto-selection. Creates a COMPLETED run immediately with lightweight results (inventory details
   * + basic cost from prices + availability from operating times).
   */
  public void browseInventoriesSync(
      String runId, String campaignId, RecommendationRequestDTO request) {

    log.info("Starting BROWSE mode for runId: {}, campaign: {}", runId, campaignId);
    long startTime = System.currentTimeMillis();

    try {
      Map<String, List<String>> venueTypeIds = null;
      if (request.getAudienceTargeting() != null) {
        venueTypeIds = request.getAudienceTargeting().getVenueTypeIds();
      }

      List<Inventory> inventories =
          fetchInventoriesByCountryWithFilters(
              request.getCountry(),
              request.getExcludedInventoryIds(),
              request.getGeographyTargeting(),
              venueTypeIds,
              request.getMediaOwnerIds(),
              request.getClassifications(),
              request.getSearchKeywords(),
              null,
              null,
              null,
              null,
              new HashMap<String, Integer>(),
              request.getGoal(),
              request.getDsps(),
              Boolean.TRUE.equals(request.getProgrammaticEnabled()));

      if (inventories.isEmpty()) {
        log.info("No inventories found for BROWSE mode, runId: {}", runId);
        RecommendationRun.RecommendationMetadata metadata =
            RecommendationRun.RecommendationMetadata.builder()
                .totalInventoriesEvaluated(0)
                .totalInventoriesRecommended(0)
                .build();
        // BROWSE mode has no auto-selection; reason fields stay untouched.
        completeRun(runId, metadata, List.of(), List.of(), null);
        return;
      }

      long totalDays = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1;

      List<RecommendationResult> results = new ArrayList<>();
      for (Inventory inv : inventories) {
        RecommendationResult result = buildBrowseResult(inv, runId, campaignId, totalDays, request);
        if (result != null) {
          results.add(result);
        }
      }

      bulkInsertResults(results);

      RecommendationRun.RecommendationMetadata metadata =
          RecommendationRun.RecommendationMetadata.builder()
              .totalInventoriesEvaluated(inventories.size())
              .totalInventoriesRecommended(results.size())
              .build();
      // BROWSE mode has no auto-selection; reason fields stay untouched.
      completeRun(runId, metadata, List.of(), List.of(), null);

      log.info(
          "BROWSE mode complete for runId: {} — {} results in {}ms",
          runId,
          results.size(),
          System.currentTimeMillis() - startTime);

    } catch (Exception e) {
      log.error("Error in BROWSE mode for runId {}: {}", runId, e.getMessage(), e);
      updateCompletionPercentage(runId, 0);
    }
  }

  private RecommendationResult buildBrowseResult(
      Inventory inventory,
      String runId,
      String campaignId,
      long totalDays,
      RecommendationRequestDTO request) {

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

    String orientationStr =
        inventory.getOrientation() != null ? inventory.getOrientation().name().toLowerCase() : null;

    List<String> sizes = null;
    if (inventory.getPanels() != null && !inventory.getPanels().isEmpty()) {
      sizes =
          inventory.getPanels().stream()
              .filter(panel -> panel.getSize() != null)
              .map(panel -> panel.getSize().name())
              .distinct()
              .collect(Collectors.toList());
    }

    RecommendationResult.InventoryDetails inventoryDetails =
        RecommendationResult.InventoryDetails.builder()
            .name(inventory.getName())
            .classification(inventory.getClassification())
            .type(inventory.getType())
            .format(inventory.getFormat())
            .environment(inventory.getEnvironment())
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
            .size(inventory.getSize())
            .inventoryCluster(inventory.getInventoryCluster())
            .build();

    Long totalAdPlays =
        calculateInventoryAdPlays(inventory, request.getStartDate(), request.getEndDate());

    java.math.BigDecimal estimatedCost = null;
    String currency = null;
    if (inventory.getPrices() != null && !inventory.getPrices().isEmpty()) {
      Inventory.PriceModel price = inventory.getPrices().getFirst();
      currency = price.getCurrency();

      if (totalAdPlays != null
          && totalAdPlays > 0
          && price.getSpot() != null
          && price.getSpot() > 0) {
        estimatedCost =
            java.math.BigDecimal.valueOf(price.getSpot())
                .multiply(java.math.BigDecimal.valueOf(totalAdPlays))
                .setScale(2, java.math.RoundingMode.HALF_UP);
      } else if (price.getCpm() != null && price.getCpm() > 0 && totalAdPlays != null) {
        long roughImpressions = totalAdPlays * 100L;
        estimatedCost =
            java.math.BigDecimal.valueOf(price.getCpm())
                .divide(java.math.BigDecimal.valueOf(1000), 4, java.math.RoundingMode.HALF_UP)
                .multiply(java.math.BigDecimal.valueOf(roughImpressions))
                .setScale(2, java.math.RoundingMode.HALF_UP);
      }
    }

    RecommendationResult.CostEstimate cost =
        RecommendationResult.CostEstimate.builder()
            .estimatedCost(estimatedCost)
            .currency(currency)
            .totalAdPlays(totalAdPlays)
            .build();

    return RecommendationResult.builder()
        .runId(runId)
        .campaignId(campaignId)
        .inventoryId(inventory.getInventoryId())
        .referenceId(inventory.getReferenceId())
        .name(inventory.getName())
        .inventoryDetails(inventoryDetails)
        .availability(
            RecommendationResult.AvailabilitySummary.builder()
                .totalDays((int) totalDays)
                .availableDays((int) totalDays)
                .availabilityPercentage(100.0)
                .allAvailable(true)
                .build())
        .cost(cost)
        .isExcluded(false)
        .createdAt(LocalDateTime.now())
        .build();
  }

  /**
   * Fetch inventories by country with geography targeting filters applied at repository level. This
   * optimizes performance by filtering at the database level using geospatial queries. Only
   * inventories matching geographyTargeting criteria are returned.
   *
   * @param country Country name to filter by
   * @param excludedInventoryIds List of inventory IDs to exclude (can be null or empty)
   * @param geographyTargeting Geography targeting criteria (cities, states, geofences, POI targets)
   *     - can be null
   * @param venueTypeIds Map of classification → venueTypeId list; per-classification filter on
   *     inventory venueTypeIds field (null or empty = no venue filter)
   * @param exclusionReasons Map to track exclusion reasons
   * @return List of filtered inventories matching geography and venue targeting
   */
  private List<Inventory> fetchInventoriesByCountryWithFilters(
      String country,
      List<String> excludedInventoryIds,
      RecommendationRequestDTO.GeographyTargeting geographyTargeting,
      Map<String, List<String>> venueTypeIds,
      List<String> mediaOwnerIds,
      List<String> classifications,
      List<String> searchKeywords,
      Long durationDays,
      Long availableLeadDays,
      java.time.LocalDate campaignStartDate,
      java.time.LocalDate campaignEndDate,
      Map<String, Integer> exclusionReasons,
      RecommendationRequestDTO.CampaignGoal goalType,
      List<String> dsps,
      boolean programmaticEnabled) {

    // Use custom repository method with geospatial and venue filtering
    List<Inventory> inventories =
        inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            country,
            excludedInventoryIds,
            geographyTargeting,
            venueTypeIds,
            mediaOwnerIds,
            classifications,
            searchKeywords,
            durationDays,
            availableLeadDays,
            campaignStartDate,
            campaignEndDate,
            goalType,
            dsps,
            programmaticEnabled);

    // Track excluded count if exclusions were provided
    if (excludedInventoryIds != null && !excludedInventoryIds.isEmpty()) {
      exclusionReasons.put("EXCLUDED_BY_USER", excludedInventoryIds.size());
    }

    // Track geography filtering if applied
    if (geographyTargeting != null) {
      int geoFilteredCount = 0;
      if (geographyTargeting.getCities() != null && !geographyTargeting.getCities().isEmpty()) {
        geoFilteredCount += geographyTargeting.getCities().size();
      }
      if (geographyTargeting.getStates() != null && !geographyTargeting.getStates().isEmpty()) {
        geoFilteredCount += geographyTargeting.getStates().size();
      }
      if (geographyTargeting.getGeofences() != null
          && !geographyTargeting.getGeofences().isEmpty()) {
        geoFilteredCount += geographyTargeting.getGeofences().size();
      }
      if (geoFilteredCount > 0) {
        exclusionReasons.put("GEOGRAPHY_TARGETING", geoFilteredCount);
      }
    }

    // Track venue filtering if applied
    if (venueTypeIds != null && !venueTypeIds.isEmpty()) {
      exclusionReasons.put("VENUE_FILTER", venueTypeIds.size());
    }

    // Track media owner filtering if applied
    if (mediaOwnerIds != null && !mediaOwnerIds.isEmpty()) {
      exclusionReasons.put("MEDIA_OWNER_FILTER", mediaOwnerIds.size());
    }

    // Track classification filtering if applied
    if (classifications != null && !classifications.isEmpty()) {
      exclusionReasons.put("CLASSIFICATION_FILTER", classifications.size());
    }

    // Track keyword filtering if applied (blank-only lists add no $match stage, so no reason)
    if (searchKeywords != null
        && searchKeywords.stream().anyMatch(k -> k != null && !k.isBlank())) {
      exclusionReasons.put("SEARCH_KEYWORDS_FILTER", searchKeywords.size());
    }

    // Track DSP filtering if applied
    if (dsps != null && !dsps.isEmpty()) {
      exclusionReasons.put("DSPS_FILTER", dsps.size());
    }

    // Track programmatic (Digital-slice) refinement if applied
    if (programmaticEnabled) {
      exclusionReasons.put("PROGRAMMATIC_FILTER", 1);
    }

    return inventories;
  }

  /**
   * V2 OPTIMIZATION: update completion percentage with a targeted {@code $set} instead of v1's full
   * read-modify-write (findByRunId + save). This avoids loading and rewriting the entire run
   * document — and the single-document write contention it causes — on every progress tick, which
   * matters most during the highly concurrent scoring phase.
   */
  private void updateCompletionPercentage(String runId, int percentage) {
    int clamped = Math.min(100, Math.max(0, percentage));
    mongoTemplate.updateFirst(
        Query.query(Criteria.where("runId").is(runId)),
        Update.update("completionPercentage", clamped),
        RecommendationRun.class);
  }

  /**
   * V2 OPTIMIZATION (authenticated auto-select Measure calls): returns the shared virtual-thread
   * executor wrapped so the current {@link
   * org.springframework.security.core.context.SecurityContext} (and thus the bearer token read by
   * {@code MeasureApiClient}) is propagated onto the virtual threads that run the auto-selection
   * Measure/schedule calls. In v1 those dispatch on the raw executor and lose the context, so
   * Measure can be called unauthenticated. Capturing happens on the submitting thread (which holds
   * the context), so this must be called at each dispatch site.
   */
  private java.util.concurrent.Executor securityAwareExecutor() {
    final org.springframework.security.core.context.SecurityContext context =
        org.springframework.security.core.context.SecurityContextHolder.getContext();
    return command ->
        virtualThreadTaskExecutor.execute(
            () -> {
              org.springframework.security.core.context.SecurityContext previous =
                  org.springframework.security.core.context.SecurityContextHolder.getContext();
              org.springframework.security.core.context.SecurityContextHolder.setContext(context);
              try {
                command.run();
              } finally {
                org.springframework.security.core.context.SecurityContextHolder.setContext(
                    previous);
              }
            });
  }

  /**
   * Complete a run using a targeted {@code $set} (finding #4) instead of a read-modify-write save,
   * removing the full-document rewrite and its contention on the run document.
   */
  private void completeRun(
      String runId,
      RecommendationRun.RecommendationMetadata metadata,
      List<String> warnings,
      List<String> autoSelectedInventoryIds,
      AutoSelectionReasonResolver.ReasonResolution autoSelectionReason) {
    Update update =
        new Update()
            .set("status", RecommendationRun.RunStatus.COMPLETED)
            .set("completionPercentage", 100)
            .set("completedAt", LocalDateTime.now())
            .set("metadata", metadata)
            .set("warnings", warnings)
            .set(
                "autoSelectedInventoryIds",
                autoSelectedInventoryIds != null ? autoSelectedInventoryIds : List.of());
    // Observability only: fold the auto-selection reason into this same targeted $set (no
    // additional write); null (e.g. BROWSE mode) leaves the fields untouched.
    if (autoSelectionReason != null) {
      update
          .set("autoSelectionReasonCode", autoSelectionReason.code())
          .set("autoSelectionReasonDetail", autoSelectionReason.detail())
          .set("autoSelectionDiagnostics", autoSelectionReason.diagnostics());
    }
    mongoTemplate.updateFirst(
        Query.query(Criteria.where("runId").is(runId)), update, RecommendationRun.class);
  }

  /** Complete a run with empty results */
  private void completeRunWithEmptyResults(
      String runId,
      String campaignId,
      Map<String, Integer> exclusionReasons,
      List<String> warnings,
      AutoSelectionReasonResolver.ReasonResolution autoSelectionReason) {
    RecommendationRun.RecommendationMetadata metadata =
        RecommendationRun.RecommendationMetadata.builder()
            .totalInventoriesEvaluated(0)
            .totalInventoriesRecommended(0)
            .totalInventoriesExcluded(
                exclusionReasons.values().stream().mapToInt(Integer::intValue).sum())
            .exclusionReasons(exclusionReasons)
            .averageScore(0.0)
            .seed(runId)
            .build();

    completeRun(runId, metadata, warnings, List.of(), autoSelectionReason);
  }

  // ---------------------------------------------------------------------------
  // Budget-aware auto-selection with batch processing and parallel execution
  // ---------------------------------------------------------------------------

  /**
   * Budget-aware auto-selection entry point. When budget is present, inventories are processed by
   * allocation category in parallel (Round 1) with greedy redistribution of unused budget (Round
   * 2). Inventories are processed in descending score bands (>90, >80, ..., >10) to limit Measure
   * API calls. When only a goal is provided (no budget), a flat score-band approach is used
   * instead.
   */
  private List<String> applyBudgetAwareAutoSelect(
      List<RecommendationResult> results,
      RecommendationRequestDTO request,
      String runId,
      String campaignId,
      Map<String, Inventory> inventoryMap) {
    if (results.isEmpty()) {
      return List.of();
    }

    boolean hasBudget =
        request.getBudget() != null && request.getBudget().compareTo(BigDecimal.ZERO) > 0;
    boolean hasGoal = request.getGoal() != null;

    if (!hasBudget && !hasGoal) {
      clearSelectionMode(results);
      return List.of();
    }

    if (request.getStartDate() == null || request.getEndDate() == null) {
      clearSelectionMode(results);
      return List.of();
    }

    if (hasBudget) {
      return executeBudgetAwareSelection(results, request, runId, campaignId, inventoryMap);
    } else {
      return executeGoalOnlySelection(results, request, runId, campaignId, inventoryMap);
    }
  }

  /**
   * Full budget-aware selection: groups inventories by allocation category, processes each category
   * in parallel through score bands (Round 1), then redistributes unused budget greedily to capped
   * categories (Round 2). Goal acts as an overall ceiling across all categories.
   */
  private List<String> executeBudgetAwareSelection(
      List<RecommendationResult> results,
      RecommendationRequestDTO request,
      String runId,
      String campaignId,
      Map<String, Inventory> inventoryMap) {

    // Create sub-profiler for auto-selection details
    PerformanceProfiler autoSelectProfiler =
        new PerformanceProfiler("v2|autoSelect_runId=" + runId);

    try {
      BigDecimal totalBudget = request.getBudget();
      Map<String, Double> allocation = BudgetAllocationUtils.getEffectiveBudgetAllocation(request);
      Map<String, BigDecimal> categoryBudgets =
          BudgetAllocationUtils.calculateCategoryBudgets(totalBudget, allocation);

      log.info(
          "Budget-aware selection: totalBudget={}, categories={}",
          totalBudget,
          categoryBudgets.keySet());

      // Inventory map already provided from initial fetch (no re-fetch needed)
      log.debug("Using cached inventory map with {} inventories", inventoryMap.size());

      // OPTIMIZATION: Shared schedule cache across all rounds (prevents duplicate Measure API
      // calls)
      final Map<String, ScheduleSummaryDTO> globalScheduleCache = new ConcurrentHashMap<>();

      // Group results by allocation key; exclude inventories with score <= minimum threshold
      Map<String, List<RecommendationResult>> resultsByCategory =
          results.stream()
              .filter(
                  r -> r.getFinalScore() != null && r.getFinalScore() > MIN_RECOMMENDATION_SCORE)
              .collect(
                  Collectors.groupingBy(
                      r -> BudgetAllocationUtils.getAllocationKey(r.getInventoryDetails())));

      // -- Round 1: parallel category processing --
      autoSelectProfiler.startStep("6b_Round1_Parallel");
      Map<String, CategorySelectionState> categoryStates = null;
      try {
        categoryStates =
            executeRound1Parallel(
                resultsByCategory, categoryBudgets, inventoryMap, request, globalScheduleCache);
      } finally {
        Map<String, Object> round1Metadata = new HashMap<>();
        round1Metadata.put("categoryCount", categoryStates != null ? categoryStates.size() : 0);
        if (categoryStates != null) {
          for (Map.Entry<String, CategorySelectionState> entry : categoryStates.entrySet()) {
            round1Metadata.put(
                "category_" + entry.getKey() + "_selected",
                entry.getValue().selectedSchedules.size());
          }
        }
        autoSelectProfiler.endStep("6b_Round1_Parallel", round1Metadata);
      }

      // Check overall goal ceiling after Round 1
      boolean goalMet = isOverallGoalMet(categoryStates, request);

      // -- Round 2: greedy redistribution of unused budget --
      autoSelectProfiler.startStep("6c_Round2_Greedy");
      try {
        if (!goalMet) {
          executeRound2Greedy(
              categoryStates, allocation, inventoryMap, request, globalScheduleCache);
        }
      } finally {
        autoSelectProfiler.endStep("6c_Round2_Greedy", Map.of("executed", !goalMet));
      }

      // -- Round 3: allocate remaining budget to highest finalScore inventories --
      autoSelectProfiler.startStep("6d_Round3_RemainingBudget");
      Map<String, ScheduleSummaryDTO> round3Schedules = null;
      try {
        round3Schedules =
            executeRound3RemainingBudget(
                categoryStates, results, inventoryMap, request, globalScheduleCache);
      } finally {
        autoSelectProfiler.endStep(
            "6d_Round3_RemainingBudget",
            Map.of("round3Selections", round3Schedules != null ? round3Schedules.size() : 0));
      }

      // -- Post-processing: merge selections and persist --
      autoSelectProfiler.startStep("6e_FinalizeSelection");
      List<String> finalSelectedIds = null;
      try {
        finalSelectedIds =
            finalizeSelection(categoryStates, round3Schedules, results, runId, campaignId);
      } finally {
        autoSelectProfiler.endStep(
            "6e_FinalizeSelection",
            Map.of("totalSelected", finalSelectedIds != null ? finalSelectedIds.size() : 0));
      }

      // Log auto-selection sub-profiler
      autoSelectProfiler.logSummary();

      return finalSelectedIds;

    } catch (Exception e) {
      log.error("Error in executeBudgetAwareSelection: {}", e.getMessage(), e);
      autoSelectProfiler.logSummary();
      throw e;
    }
  }

  /**
   * Round 1: launches each budget allocation category as a parallel virtual-thread task. Each
   * category processes its inventories through descending score bands until its budget cap is hit.
   */
  private Map<String, CategorySelectionState> executeRound1Parallel(
      Map<String, List<RecommendationResult>> resultsByCategory,
      Map<String, BigDecimal> categoryBudgets,
      Map<String, Inventory> inventoryMap,
      RecommendationRequestDTO request,
      Map<String, ScheduleSummaryDTO> globalScheduleCache) {

    Map<String, CompletableFuture<CategorySelectionState>> futures = new LinkedHashMap<>();

    for (Map.Entry<String, List<RecommendationResult>> entry : resultsByCategory.entrySet()) {
      String categoryKey = entry.getKey();
      List<RecommendationResult> categoryResults = entry.getValue();
      BigDecimal budgetCap = categoryBudgets.getOrDefault(categoryKey, BigDecimal.ZERO);

      if (budgetCap.compareTo(BigDecimal.ZERO) <= 0 || categoryResults.isEmpty()) {
        log.debug(
            "Skipping category '{}': budgetCap={}, results={}",
            categoryKey,
            budgetCap,
            categoryResults.size());
        continue;
      }

      // Pre-sort by descending score for deterministic selection order
      List<RecommendationResult> sorted =
          categoryResults.stream()
              .sorted(
                  Comparator.comparing(
                      RecommendationResult::getFinalScore,
                      Comparator.nullsLast(Comparator.reverseOrder())))
              .toList();

      CategorySelectionState state = new CategorySelectionState(categoryKey, budgetCap, sorted);

      futures.put(
          categoryKey,
          CompletableFuture.supplyAsync(
              () -> {
                long categoryStartNanos = System.nanoTime();
                log.info(
                    "Round 1 - processing category '{}': cap={}, inventories={}",
                    categoryKey,
                    budgetCap,
                    sorted.size());
                processCategoryRound(state, inventoryMap, request, globalScheduleCache);
                long categoryDurationMs = (System.nanoTime() - categoryStartNanos) / 1_000_000;
                log.info(
                    "Round 1 - category '{}' done: used={}, selected={}, capped={}, timeMs={}",
                    categoryKey,
                    state.usedBudget,
                    state.selectedSchedules.size(),
                    state.capped,
                    categoryDurationMs);
                return state;
              },
              securityAwareExecutor()));
    }

    // Wait for all category tasks to complete
    CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).join();

    Map<String, CategorySelectionState> states = new LinkedHashMap<>();
    for (Map.Entry<String, CompletableFuture<CategorySelectionState>> entry : futures.entrySet()) {
      try {
        states.put(entry.getKey(), entry.getValue().get());
      } catch (Exception e) {
        log.error("Error in Round 1 for category '{}': {}", entry.getKey(), e.getMessage(), e);
      }
    }

    // Add placeholder states for categories that had budget but no matching inventories
    for (Map.Entry<String, BigDecimal> entry : categoryBudgets.entrySet()) {
      if (!states.containsKey(entry.getKey())) {
        states.put(
            entry.getKey(),
            new CategorySelectionState(entry.getKey(), entry.getValue(), List.of()));
      }
    }

    return states;
  }

  /**
   * Processes one category through descending score bands. Phase 3.1 optimization: batch-fetches
   * ALL inventories in the category upfront with a SINGLE Measure API call (instead of one call per
   * band), then selects inventories from each band until budget cap is reached. Caches schedules so
   * Round 2 can resume without re-calling the API.
   *
   * @param state Mutable category state (modified in place)
   * @param inventoryMap Shared read-only inventory lookup
   * @param request Original recommendation request
   * @param globalScheduleCache Shared cache across all rounds (prevents duplicate API calls)
   */
  private void processCategoryRound(
      CategorySelectionState state,
      Map<String, Inventory> inventoryMap,
      RecommendationRequestDTO request,
      Map<String, ScheduleSummaryDTO> globalScheduleCache) {

    state.capped = false;

    // Phase 3.1: Batch-fetch ALL inventories in this category upfront (single Measure API call)
    // OPTIMIZATION: Check global cache first to avoid duplicate API calls across rounds
    List<Inventory> allInventoriesToBuild =
        state.categoryResults.stream()
            .map(r -> inventoryMap.get(r.getInventoryId()))
            .filter(Objects::nonNull)
            .filter(
                inv ->
                    !state.cachedSchedules.containsKey(inv.getInventoryId())
                        && !globalScheduleCache.containsKey(inv.getInventoryId()))
            .distinct()
            .toList();

    if (!allInventoriesToBuild.isEmpty()) {
      log.warn(
          "[DIAG-AUTO] Category '{}': building schedules for {} inventories",
          state.categoryKey,
          allInventoriesToBuild.size());

      // Phase 3.1+: Batch Measure API calls (max 500 per call) with parallel execution
      int MEASURE_BATCH_SIZE = 1500;
      List<List<Inventory>> batchedInventories =
          partitionList(allInventoriesToBuild, MEASURE_BATCH_SIZE);

      log.warn(
          "[DIAG-AUTO] Category '{}': split into {} Measure API batches",
          state.categoryKey,
          batchedInventories.size());

      // Call Measure API for each batch in PARALLEL
      Map<String, ScheduleSummaryDTO> allSchedules = new ConcurrentHashMap<>();
      List<CompletableFuture<Void>> apiFutures = new ArrayList<>();
      AtomicLong totalApiTime = new AtomicLong(0);

      for (int i = 0; i < batchedInventories.size(); i++) {
        final List<Inventory> batch = batchedInventories.get(i);
        final int batchNum = i + 1;

        CompletableFuture<Void> future =
            CompletableFuture.runAsync(
                () -> {
                  long apiStart = System.currentTimeMillis();
                  Map<String, ScheduleSummaryDTO> batchSchedules =
                      scheduleRecommendationService.buildScheduleSummariesForInventories(
                          batch,
                          request.getStartDate(),
                          request.getEndDate(),
                          null,
                          request.getGoal());
                  long apiDuration = System.currentTimeMillis() - apiStart;
                  totalApiTime.addAndGet(apiDuration);

                  allSchedules.putAll(batchSchedules);
                  log.debug(
                      "[DIAG-AUTO] Measure API batch {}/{} complete: {}ms for {} inventories",
                      batchNum,
                      batchedInventories.size(),
                      apiDuration,
                      batch.size());
                },
                securityAwareExecutor());

        apiFutures.add(future);
      }

      // Wait for all API calls to complete
      CompletableFuture.allOf(apiFutures.toArray(new CompletableFuture[0])).join();
      state.cachedSchedules.putAll(allSchedules);
      globalScheduleCache.putAll(allSchedules); // OPTIMIZATION: Share with other rounds

      log.warn(
          "[DIAG-AUTO] Category '{}': Measure API complete - {} schedules, total sequential time: {}ms, actual parallel time via batching",
          state.categoryKey,
          allSchedules.size(),
          totalApiTime.get());
    }

    // OPTIMIZATION: Reuse schedules from global cache for inventories already fetched
    for (RecommendationResult result : state.categoryResults) {
      String invId = result.getInventoryId();
      if (!state.cachedSchedules.containsKey(invId) && globalScheduleCache.containsKey(invId)) {
        state.cachedSchedules.put(invId, globalScheduleCache.get(invId));
      }
    }

    // Now process each score band using the cached schedules (no more API calls)
    for (int bandIndex = 0; bandIndex < SCORE_THRESHOLDS.length; bandIndex++) {
      double lowerBound = SCORE_THRESHOLDS[bandIndex];

      // Filter results in this score band that haven't been selected yet
      final int bi = bandIndex;
      List<RecommendationResult> bandResults =
          state.categoryResults.stream()
              .filter(
                  r ->
                      r.getFinalScore() != null
                          && r.getFinalScore() > lowerBound
                          && (bi == 0 || r.getFinalScore() <= SCORE_THRESHOLDS[bi - 1])
                          && !state.selectedInventoryIds.contains(r.getInventoryId()))
              .sorted(
                  Comparator.comparing(
                      RecommendationResult::getFinalScore,
                      Comparator.nullsLast(Comparator.reverseOrder())))
              .toList();

      if (bandResults.isEmpty()) {
        continue;
      }

      // Select inventories from this band until budget cap is hit (using cached schedules)
      for (RecommendationResult result : bandResults) {
        ScheduleSummaryDTO schedule = state.cachedSchedules.get(result.getInventoryId());
        if (schedule == null || schedule.getBasePrice() == null) {
          continue;
        }

        // Exclude inventories without valid Measure API data (impressions and reach)
        if (!hasValidMeasureData(schedule)) {
          //   log.debug("Round 1 - excluding inventory {} (score={}) from category '{}' - missing
          // valid
          // Measure data (impressions={}, reach={})",
          //       result.getInventoryId(),
          //       result.getFinalScore(),
          //       state.categoryKey,
          //       schedule.getEstimatedImpressions(),
          //       schedule.getEstimatedReach());
          continue;
        }

        BigDecimal price = BigDecimal.valueOf(schedule.getBasePrice());
        BigDecimal remainingCappedBudget = state.budgetCap.subtract(state.usedBudget);
        // Only select inventories which basePrice < remainingCappedBudget
        if (remainingCappedBudget.compareTo(price) >= 0) {
          state.selectedSchedules.put(result.getInventoryId(), schedule);
          state.selectedInventoryIds.add(result.getInventoryId());
          state.usedBudget = state.usedBudget.add(price);

          if (schedule.getEstimatedImpressions() != null) {
            state.categoryImpressions += schedule.getEstimatedImpressions();
          }
          if (schedule.getEstimatedReach() != null) {
            state.categoryReach += schedule.getEstimatedReach();
          }
          if (state.usedBudget.add(price).compareTo(state.budgetCap) >= 0) {
            state.capped = true;
            return;
          }
        }
      }
    }
  }

  /**
   * Round 2: redistributes unused budget from under-utilized categories to capped categories using
   * a greedy strategy. Capped categories are sorted by original allocation percentage (descending)
   * and each receives as much of the remaining budget as it can use before the next category is
   * tried.
   */
  private void executeRound2Greedy(
      Map<String, CategorySelectionState> allStates,
      Map<String, Double> originalAllocation,
      Map<String, Inventory> inventoryMap,
      RecommendationRequestDTO request,
      Map<String, ScheduleSummaryDTO> globalScheduleCache) {

    List<CategorySelectionState> cappedCategories = new ArrayList<>();
    BigDecimal totalRemaining = BigDecimal.ZERO;

    for (CategorySelectionState state : allStates.values()) {
      if (state.capped) {
        cappedCategories.add(state);
      } else {
        BigDecimal remaining = state.budgetCap.subtract(state.usedBudget);
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
          totalRemaining = totalRemaining.add(remaining);
        }
      }
    }

    if (cappedCategories.isEmpty() || totalRemaining.compareTo(BigDecimal.ZERO) <= 0) {
      log.info(
          "Round 2 skipped: cappedCategories={}, totalRemaining={}",
          cappedCategories.size(),
          totalRemaining);
      return;
    }

    // Sort capped categories by original allocation descending (greedy: highest first)
    cappedCategories.sort(
        (a, b) -> {
          double allocA = originalAllocation.getOrDefault(a.categoryKey, 0.0);
          double allocB = originalAllocation.getOrDefault(b.categoryKey, 0.0);
          return Double.compare(allocB, allocA);
        });

    log.info(
        "Round 2 - redistributing {} to {} capped categories: {}",
        totalRemaining,
        cappedCategories.size(),
        cappedCategories.stream().map(s -> s.categoryKey).toList());

    for (CategorySelectionState state : cappedCategories) {
      if (totalRemaining.compareTo(BigDecimal.ZERO) <= 0) {
        break;
      }
      if (isOverallGoalMet(allStates, request)) {
        log.info("Round 2 - overall goal met, stopping redistribution");
        break;
      }

      BigDecimal previousUsed = state.usedBudget;
      state.budgetCap = state.budgetCap.add(totalRemaining);

      log.info(
          "Round 2 - category '{}': extra budget={}, new cap={}",
          state.categoryKey,
          totalRemaining,
          state.budgetCap);

      processCategoryRound(state, inventoryMap, request, globalScheduleCache);

      BigDecimal additionalUsage = state.usedBudget.subtract(previousUsed);
      totalRemaining = totalRemaining.subtract(additionalUsage);

      log.info(
          "Round 2 - category '{}' done: additionalUsage={}, remaining={}",
          state.categoryKey,
          additionalUsage,
          totalRemaining);
    }
  }

  /**
   * Round 3: allocates remaining budget (after R1 and R2) to highest finalScore inventories not yet
   * selected. Phase 3.1 optimization: builds ALL candidate schedules with a SINGLE Measure API
   * batch call, then greedily selects schedules that fit within budget from highest to lowest
   * score. Respects operational and booked hours.
   */
  private Map<String, ScheduleSummaryDTO> executeRound3RemainingBudget(
      Map<String, CategorySelectionState> categoryStates,
      List<RecommendationResult> results,
      Map<String, Inventory> inventoryMap,
      RecommendationRequestDTO request,
      Map<String, ScheduleSummaryDTO> globalScheduleCache) {

    BigDecimal totalRemaining = request.getBudget();
    for (CategorySelectionState state : categoryStates.values()) {
      totalRemaining = totalRemaining.subtract(state.usedBudget);
    }
    // Make final for lambda usage
    final BigDecimal totalRemainingFinal = totalRemaining;

    if (totalRemainingFinal.compareTo(BigDecimal.ZERO) <= 0) {
      log.info("Round 3 skipped: no remaining budget");
      return Map.of();
    }

    Set<String> alreadySelected = new HashSet<>();
    for (CategorySelectionState state : categoryStates.values()) {
      alreadySelected.addAll(state.selectedInventoryIds);
    }

    List<RecommendationResult> candidates =
        results.stream()
            .filter(
                r ->
                    r.getFinalScore() != null
                        && r.getFinalScore() > MIN_RECOMMENDATION_SCORE
                        && r.getInventoryId() != null
                        && !alreadySelected.contains(r.getInventoryId()))
            .sorted(
                Comparator.comparing(
                    RecommendationResult::getFinalScore,
                    Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

    if (candidates.isEmpty()) {
      log.info("Round 3 skipped: no candidates (all selected or below score threshold)");
      return Map.of();
    }

    log.warn(
        "[DIAG-AUTO] Round 3 - allocating remaining budget {} to up to {} candidates",
        totalRemainingFinal,
        candidates.size());

    LocalDate startDate = request.getStartDate();
    LocalDate endDate = request.getEndDate();
    if (startDate == null || endDate == null || !startDate.isBefore(endDate.plusDays(1))) {
      return Map.of();
    }

    // Phase 3.1+: Batch Measure API calls for Round 3 candidates
    // OPTIMIZATION: Filter out inventories already in global cache
    List<Inventory> candidateInventories =
        candidates.stream()
            .map(r -> inventoryMap.get(r.getInventoryId()))
            .filter(Objects::nonNull)
            .filter(inv -> !globalScheduleCache.containsKey(inv.getInventoryId()))
            .toList();

    log.warn(
        "[DIAG-AUTO] Round 3: fetching schedules for {} candidates", candidateInventories.size());

    // Batch the Measure API calls (max 500 per call) with parallel execution
    int MEASURE_BATCH_SIZE = 1500;
    List<List<Inventory>> batchedCandidates =
        partitionList(candidateInventories, MEASURE_BATCH_SIZE);

    log.warn("[DIAG-AUTO] Round 3: split into {} Measure API batches", batchedCandidates.size());

    Map<String, ScheduleSummaryDTO> candidateSchedules = new ConcurrentHashMap<>();
    List<CompletableFuture<Void>> apiFutures = new ArrayList<>();
    AtomicLong totalApiTime = new AtomicLong(0);

    for (int i = 0; i < batchedCandidates.size(); i++) {
      final List<Inventory> batch = batchedCandidates.get(i);
      final int batchNum = i + 1;

      CompletableFuture<Void> future =
          CompletableFuture.runAsync(
              () -> {
                long apiStart = System.currentTimeMillis();
                Map<String, ScheduleSummaryDTO> batchSchedules =
                    scheduleRecommendationService.buildBestSchedulesForBudgetCapBatch(
                        batch, startDate, endDate, totalRemainingFinal, request.getGoal());
                long apiDuration = System.currentTimeMillis() - apiStart;
                totalApiTime.addAndGet(apiDuration);

                candidateSchedules.putAll(batchSchedules);
                log.debug(
                    "[DIAG-AUTO] Round 3 Measure API batch {}/{} complete: {}ms for {} inventories",
                    batchNum,
                    batchedCandidates.size(),
                    apiDuration,
                    batch.size());
              },
              securityAwareExecutor());

      apiFutures.add(future);
    }

    // Wait for all API calls to complete
    CompletableFuture.allOf(apiFutures.toArray(new CompletableFuture[0])).join();
    globalScheduleCache.putAll(candidateSchedules); // OPTIMIZATION: Cache for potential future use

    log.warn(
        "[DIAG-AUTO] Round 3: Measure API complete - {} schedules, total sequential time: {}ms",
        candidateSchedules.size(),
        totalApiTime.get());

    // Greedy selection: iterate candidates by score, select if schedule fits remaining budget
    Map<String, ScheduleSummaryDTO> round3Schedules = new LinkedHashMap<>();
    BigDecimal remainingBudget = totalRemainingFinal;

    for (RecommendationResult result : candidates) {
      if (remainingBudget.compareTo(BigDecimal.ZERO) <= 0) {
        break;
      }
      // OPTIMIZATION: Check global cache first, then candidateSchedules
      ScheduleSummaryDTO schedule =
          globalScheduleCache.getOrDefault(
              result.getInventoryId(), candidateSchedules.get(result.getInventoryId()));
      if (schedule == null || schedule.getBasePrice() == null) {
        continue;
      }

      // Exclude inventories without valid Measure API data (impressions and reach)
      if (!hasValidMeasureData(schedule)) {
        log.debug(
            "Round 3 - excluding inventory {} (score={}) - missing valid Measure data (impressions={}, reach={})",
            result.getInventoryId(),
            result.getFinalScore(),
            schedule.getEstimatedImpressions(),
            schedule.getEstimatedReach());
        continue;
      }

      BigDecimal cost = BigDecimal.valueOf(schedule.getBasePrice());
      if (cost.compareTo(remainingBudget) <= 0) {
        round3Schedules.put(result.getInventoryId(), schedule);
        remainingBudget = remainingBudget.subtract(cost);
        log.debug(
            "Round 3 - selected inventory {} for cost {}, remaining budget {}",
            result.getInventoryId(),
            cost,
            remainingBudget);
      }
    }

    if (!round3Schedules.isEmpty()) {
      log.info(
          "Round 3 complete: {} inventories selected, remaining budget {}",
          round3Schedules.size(),
          remainingBudget);
    }
    return round3Schedules;
  }

  /**
   * Checks whether the overall goal ceiling (IMPRESSIONS or REACH) has been met across all
   * categories combined. Returns false if no goal is set or goal type is not IMPRESSIONS/REACH.
   */
  private boolean isOverallGoalMet(
      Map<String, CategorySelectionState> allStates, RecommendationRequestDTO request) {
    if (request.getGoal() == null
        || request.getGoalValue() == null
        || request.getGoalValue() <= 0) {
      return false;
    }
    long goalValue = request.getGoalValue();

    if (request.getGoal() == RecommendationRequestDTO.CampaignGoal.IMPRESSIONS) {
      long totalImpressions =
          allStates.values().stream().mapToLong(s -> s.categoryImpressions).sum();
      return totalImpressions >= goalValue;
    }
    if (request.getGoal() == RecommendationRequestDTO.CampaignGoal.REACH) {
      long totalReach = allStates.values().stream().mapToLong(s -> s.categoryReach).sum();
      return totalReach >= goalValue;
    }
    return false;
  }

  /**
   * Merges selected schedules from all categories and Round 3 (if any), persists them, and updates
   * selectionMode on results.
   */
  private List<String> finalizeSelection(
      Map<String, CategorySelectionState> categoryStates,
      Map<String, ScheduleSummaryDTO> round3Schedules,
      List<RecommendationResult> results,
      String runId,
      String campaignId) {

    Map<String, ScheduleSummaryDTO> allSelectedSchedules = new LinkedHashMap<>();
    for (CategorySelectionState state : categoryStates.values()) {
      allSelectedSchedules.putAll(state.selectedSchedules);
    }
    if (round3Schedules != null && !round3Schedules.isEmpty()) {
      allSelectedSchedules.putAll(round3Schedules);
    }

    if (allSelectedSchedules.isEmpty()) {
      clearSelectionMode(results);
      return List.of();
    }

    scheduleRecommendationService.saveSchedulesForRun(
        runId, campaignId != null ? campaignId : "", allSelectedSchedules);

    for (RecommendationResult r : results) {
      if (allSelectedSchedules.containsKey(r.getInventoryId())) {
        r.setSelectionMode(SelectionMode.AUTO);
      } else {
        r.setSelectionMode(null);
      }
    }

    log.info(
        "Budget-aware selection complete: {} inventories selected across {} categories",
        allSelectedSchedules.size(),
        categoryStates.values().stream().filter(s -> !s.selectedSchedules.isEmpty()).count());

    return new ArrayList<>(allSelectedSchedules.keySet());
  }

  public void bulkInsertResults(List<RecommendationResult> results) {
    // if (results == null || results.isEmpty()) {
    //   log.debug("bulkInsertResults: nothing to insert");
    //   return;
    // }

    // long startTime = System.currentTimeMillis();
    // int totalInserted = 0;

    // log.info("Starting bulk insert for {} documents", results.size());

    // for (int i = 0; i < results.size(); i += BATCH_SIZE) {
    //   int end = Math.min(i + BATCH_SIZE, results.size());
    //   List<RecommendationResult> batch = results.subList(i, end);

    //   long t1 = System.currentTimeMillis();

    //   mongoTemplate.insert(batch, RecommendationResult.class);
    //   totalInserted += batch.size();

    //   log.info(
    //       "Batch {}/{} — inserted={}, took={}ms",
    //       (i / BATCH_SIZE) + 1,
    //       (int) Math.ceil((double) results.size() / BATCH_SIZE),
    //       batch.size(),
    //       System.currentTimeMillis() - t1);
    // }

    // long duration = System.currentTimeMillis() - startTime;
    // log.info("Bulk insert complete — totalInserted={}, duration={}ms", totalInserted, duration);
    if (results == null || results.isEmpty()) {
      log.debug("bulkInsertResults: nothing to insert");
      return;
    }

    long startTime = System.currentTimeMillis();
    log.info("Starting parallel bulk insert for {} documents", results.size());

    // 1. Partition into batches
    List<List<RecommendationResult>> batches = new ArrayList<>();
    for (int i = 0; i < results.size(); i += BATCH_SIZE) {
      int end = Math.min(i + BATCH_SIZE, results.size());
      batches.add(results.subList(i, end));
    }

    // 2. V2 OPTIMIZATION: reuse the shared virtual-thread executor instead of creating (and later
    // shutting down) a new platform thread pool per request. Same batching, same inserted
    // documents.

    // 3. Track results and errors per batch
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    AtomicInteger totalInserted = new AtomicInteger(0);
    AtomicInteger failedBatches = new AtomicInteger(0);

    for (int i = 0; i < batches.size(); i++) {
      final List<RecommendationResult> batch = batches.get(i);
      final int batchNumber = i + 1;
      final int totalBatches = batches.size();

      CompletableFuture<Void> future =
          CompletableFuture.runAsync(
              () -> {
                long t1 = System.currentTimeMillis();
                // #10: retry once before giving up, so a transient write blip doesn't lose a batch.
                int maxAttempts = 2;
                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                  try {
                    mongoTemplate.insert(batch, RecommendationResult.class);
                    totalInserted.addAndGet(batch.size());
                    log.info(
                        "Batch {}/{} — inserted={}, attempt={}, took={}ms [thread={}]",
                        batchNumber,
                        totalBatches,
                        batch.size(),
                        attempt,
                        System.currentTimeMillis() - t1,
                        Thread.currentThread().getName());
                    return;
                  } catch (Exception e) {
                    if (attempt >= maxAttempts) {
                      failedBatches.incrementAndGet();
                      log.error(
                          "Batch {}/{} failed after {} attempt(s), {}ms — error: {}",
                          batchNumber,
                          totalBatches,
                          maxAttempts,
                          System.currentTimeMillis() - t1,
                          e.getMessage(),
                          e);
                    } else {
                      log.warn(
                          "Batch {}/{} attempt {} failed, retrying — error: {}",
                          batchNumber,
                          totalBatches,
                          attempt,
                          e.getMessage());
                    }
                  }
                }
              },
              virtualThreadTaskExecutor);

      futures.add(future);
    }

    // 4. Wait for all batches to complete
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    long duration = System.currentTimeMillis() - startTime;

    if (failedBatches.get() > 0) {
      // #10: never let a run complete with a silently partial result set. Throwing here propagates
      // to the async catch block, which marks the run FAILED (finding #1) so it can be regenerated.
      log.error(
          "Parallel bulk insert had {} failed batch(es) — totalInserted={}, duration={}ms; failing run",
          failedBatches.get(),
          totalInserted.get(),
          duration);
      throw new IllegalStateException(
          "Bulk insert failed for "
              + failedBatches.get()
              + " batch(es); marking run FAILED to avoid a partially persisted result set");
    }
    log.info(
        "Parallel bulk insert complete — totalInserted={}, duration={}ms",
        totalInserted.get(),
        duration);
  }

  /**
   * Goal-only selection (no budget): OPTIMIZED to batch ALL qualified inventories upfront instead
   * of processing 9 score bands sequentially. Reduces Measure API calls from 9 to 1. No
   * category-wise allocation.
   */
  private List<String> executeGoalOnlySelection(
      List<RecommendationResult> results,
      RecommendationRequestDTO request,
      String runId,
      String campaignId,
      Map<String, Inventory> inventoryMap) {

    Long goalValue = request.getGoalValue();
    long goalValueLong = (goalValue != null && goalValue > 0) ? goalValue : 0L;
    boolean goalImpressionsOrReach =
        goalValueLong > 0
            && (request.getGoal() == RecommendationRequestDTO.CampaignGoal.IMPRESSIONS
                || request.getGoal() == RecommendationRequestDTO.CampaignGoal.REACH);
    boolean goalSov =
        goalValueLong > 0 && request.getGoal() == RecommendationRequestDTO.CampaignGoal.SOV;

    if (!goalImpressionsOrReach && !goalSov) {
      clearSelectionMode(results);
      return List.of();
    }

    if (goalSov) {
      return executeGoalOnlySovSelection(results, request, inventoryMap, goalValueLong);
    }

    // Filter and sort qualified results by score descending
    List<RecommendationResult> qualifiedResults =
        results.stream()
            .filter(r -> r.getFinalScore() != null && r.getFinalScore() > MIN_RECOMMENDATION_SCORE)
            .sorted(
                Comparator.comparing(
                    RecommendationResult::getFinalScore,
                    Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

    if (qualifiedResults.isEmpty()) {
      clearSelectionMode(results);
      return List.of();
    }

    // OPTIMIZATION: Batch-fetch ALL qualified inventories upfront (single Measure API call)
    // Previous: 9 sequential API calls (one per score band) = 30-80s
    // Optimized: 1 batch API call = 3-8s
    log.info(
        "Goal-only selection (OPTIMIZED): fetching schedules for {} qualified inventories in single batch",
        qualifiedResults.size());

    List<Inventory> allQualifiedInventories =
        qualifiedResults.stream()
            .map(r -> inventoryMap.get(r.getInventoryId()))
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    // Single batch Measure API call for ALL qualified inventories
    long apiStartTime = System.currentTimeMillis();
    Map<String, ScheduleSummaryDTO> allSchedules =
        scheduleRecommendationService.buildScheduleSummariesForInventories(
            allQualifiedInventories,
            request.getStartDate(),
            request.getEndDate(),
            null,
            request.getGoal());
    long apiDuration = System.currentTimeMillis() - apiStartTime;

    log.info(
        "Goal-only selection: Measure API complete - {} schedules fetched in {}ms (was 9 sequential calls)",
        allSchedules.size(),
        apiDuration);

    // Greedy selection: iterate by score, select if schedule meets goal constraints
    Map<String, ScheduleSummaryDTO> qualifiedSchedules = new LinkedHashMap<>();
    long runningImpressions = 0;
    long runningReach = 0;

    for (RecommendationResult r : qualifiedResults) {
      ScheduleSummaryDTO schedule = allSchedules.get(r.getInventoryId());
      if (schedule == null) {
        continue;
      }

      // Check goal constraints
      if (request.getGoal() == RecommendationRequestDTO.CampaignGoal.IMPRESSIONS) {
        if (schedule.getEstimatedImpressions() == null) {
          continue;
        }
        long remainingImpressions = goalValueLong - runningImpressions;
        if (schedule.getEstimatedImpressions() > remainingImpressions) {
          continue;
        }
      }

      if (request.getGoal() == RecommendationRequestDTO.CampaignGoal.REACH) {
        if (schedule.getEstimatedReach() == null) {
          continue;
        }
        long remainingReach = goalValueLong - runningReach;
        if (schedule.getEstimatedReach() > remainingReach) {
          continue;
        }
      }

      // Add to qualified schedules
      qualifiedSchedules.put(r.getInventoryId(), schedule);

      // Update running totals
      if (request.getGoal() == RecommendationRequestDTO.CampaignGoal.IMPRESSIONS
          && schedule.getEstimatedImpressions() != null) {
        runningImpressions += schedule.getEstimatedImpressions();
      }
      if (request.getGoal() == RecommendationRequestDTO.CampaignGoal.REACH
          && schedule.getEstimatedReach() != null) {
        runningReach += schedule.getEstimatedReach();
      }

      // Check if goal reached
      if (runningImpressions >= goalValueLong || runningReach >= goalValueLong) {
        log.info(
            "Goal-only selection: goal reached with {} inventories (impressions={}, reach={})",
            qualifiedSchedules.size(),
            runningImpressions,
            runningReach);
        break;
      }
    }

    if (qualifiedSchedules.isEmpty()) {
      clearSelectionMode(results);
      return List.of();
    }

    scheduleRecommendationService.saveSchedulesForRun(
        runId, campaignId != null ? campaignId : "", qualifiedSchedules);

    for (RecommendationResult r : results) {
      if (qualifiedSchedules.containsKey(r.getInventoryId())) {
        r.setSelectionMode(SelectionMode.AUTO);
      } else {
        r.setSelectionMode(null);
      }
    }

    return new ArrayList<>(qualifiedSchedules.keySet());
  }

  private List<String> executeGoalOnlySovSelection(
      List<RecommendationResult> results,
      RecommendationRequestDTO request,
      Map<String, Inventory> inventoryMap,
      long goalValueLong) {

    List<RecommendationResult> qualifiedResults =
        results.stream()
            .filter(r -> r.getFinalScore() != null && r.getFinalScore() > MIN_RECOMMENDATION_SCORE)
            .sorted(
                Comparator.comparing(
                    RecommendationResult::getFinalScore,
                    Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

    if (qualifiedResults.isEmpty()) {
      clearSelectionMode(results);
      return List.of();
    }

    List<String> selectedIds = new ArrayList<>();
    double cumulativeSov = 0.0;

    for (RecommendationResult r : qualifiedResults) {
      Inventory inventory = inventoryMap.get(r.getInventoryId());
      if (inventory == null) {
        continue;
      }
      Double rawSov =
          scoringService.calculateRawSov(
              inventory, request.getCountry(), request.getStartDate(), request.getEndDate());
      if (rawSov == null) {
        continue;
      }
      selectedIds.add(r.getInventoryId());
      cumulativeSov += rawSov;
      if (cumulativeSov >= goalValueLong) {
        log.info(
            "SOV goal-only selection: goal reached with {} inventories (cumulativeSov={})",
            selectedIds.size(),
            cumulativeSov);
        break;
      }
    }

    if (selectedIds.isEmpty()) {
      clearSelectionMode(results);
      return List.of();
    }

    Set<String> selectedSet = new HashSet<>(selectedIds);
    for (RecommendationResult r : results) {
      r.setSelectionMode(selectedSet.contains(r.getInventoryId()) ? SelectionMode.AUTO : null);
    }

    return selectedIds;
  }

  /** Clears selectionMode to null for all results. */
  private static void clearSelectionMode(List<RecommendationResult> results) {
    for (RecommendationResult r : results) {
      r.setSelectionMode(null);
    }
  }

  /** Mutable state holder for per-category budget-aware selection across rounds. */
  private static class CategorySelectionState {
    final String categoryKey;
    BigDecimal budgetCap;
    BigDecimal usedBudget;
    boolean capped;
    final Map<String, ScheduleSummaryDTO> selectedSchedules;
    final Set<String> selectedInventoryIds;
    final Map<String, ScheduleSummaryDTO> cachedSchedules;
    long categoryImpressions;
    long categoryReach;
    final List<RecommendationResult> categoryResults;

    CategorySelectionState(
        String categoryKey, BigDecimal budgetCap, List<RecommendationResult> categoryResults) {
      this.categoryKey = categoryKey;
      this.budgetCap = budgetCap;
      this.usedBudget = BigDecimal.ZERO;
      this.capped = false;
      this.selectedSchedules = new LinkedHashMap<>();
      this.selectedInventoryIds = new HashSet<>();
      this.cachedSchedules = new HashMap<>();
      this.categoryImpressions = 0;
      this.categoryReach = 0;
      this.categoryResults = categoryResults;
    }
  }

  /**
   * Validates if a schedule has valid Measure API data. Returns false if impressions or reach are
   * null or <= 0. This ensures only inventories with verified audience data are auto-selected in
   * budget-aware mode, aligning with goal-only selection behavior.
   *
   * @param schedule The schedule to validate
   * @return true if schedule has valid impressions AND reach, false otherwise
   */
  private boolean hasValidMeasureData(ScheduleSummaryDTO schedule) {
    if (schedule == null) {
      return false;
    }

    // Check impressions
    if (schedule.getEstimatedImpressions() == null || schedule.getEstimatedImpressions() <= 0) {
      return false;
    }

    // Check reach
    if (schedule.getEstimatedReach() == null || schedule.getEstimatedReach() <= 0) {
      return false;
    }

    return true;
  }

  /** Build RecommendationResult from ScoredInventory */
  private RecommendationResult buildRecommendationResult(
      ScoredInventory scored,
      RecommendationRequestDTO request,
      String runId,
      String campaignId,
      Map<String, MeasureReachFrequencyResponseDTO> measureResponseByRefId,
      boolean excludedForAvailability) {

    Inventory inventory = scored.inventory();
    InventoryScore score = scored.score();

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
    long totalDays =
        java.time.temporal.ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate())
            + 1;
    Integer availableDays =
        score.getAvailability() != null ? (int) (totalDays * score.getAvailability() / 100.0) : 0;

    RecommendationResult.AvailabilitySummary availabilitySummary =
        RecommendationResult.AvailabilitySummary.builder()
            .totalDays((int) totalDays)
            .availableDays(availableDays)
            .availabilityPercentage(score.getAvailability())
            .summary(
                excludedForAvailability
                    ? RecommendationAsyncService.buildUnavailableSummaryText(
                        availableDays, (int) totalDays)
                    : RecommendationAsyncService.buildAvailabilitySummaryText(
                        availableDays, (int) totalDays, score.getAvailability()))
            .allAvailable(score.getAvailability() != null && score.getAvailability() >= 100.0)
            .build();

    // Build forecasted metrics
    String refId =
        inventory.getReferenceId() != null
            ? inventory.getReferenceId()
            : inventory.getInventoryId();
    MeasureReachFrequencyResponseDTO measureResponse = measureResponseByRefId.get(refId);
    RecommendationResult.ForecastedMetrics forecast =
        calculateForecastedMetricsForResult(measureResponse, inventory, request);

    // Excluded (unavailable) results are informational only — a missing forecast must not drop
    // them, or the planner would again see a plain unflagged row with no explanation.
    if (forecast == null && !excludedForAvailability) {
      return null;
    }

    // Build cost estimate
    RecommendationResult.CostEstimate cost =
        forecast != null
            ? calculateCostEstimateForResult(
                inventory,
                request.getStartDate(),
                request.getEndDate(),
                forecast,
                request.getGoal())
            : null;

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
              .collect(java.util.stream.Collectors.toList());
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
        .finalScore(scored.finalScoreWithJitter())
        .componentScores(componentScores)
        .why(score.getExplanation())
        .availability(availabilitySummary)
        .forecast(forecast)
        .cost(cost)
        .isExcluded(excludedForAvailability)
        .createdAt(LocalDateTime.now())
        .build();
  }

  /** Calculate forecasted metrics for RecommendationResult using Measure API response */
  private RecommendationResult.ForecastedMetrics calculateForecastedMetricsForResult(
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

  /** Calculate cost estimate for RecommendationResult */
  private RecommendationResult.CostEstimate calculateCostEstimateForResult(
      Inventory inventory,
      java.time.LocalDate startDate,
      java.time.LocalDate endDate,
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

    long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
    java.math.BigDecimal estimatedCost = java.math.BigDecimal.ZERO;
    Inventory.PriceModel price = inventory.getPrices().getFirst();
    Long totalAdPlays = calculateInventoryAdPlays(inventory, startDate, endDate);

    boolean goalBasedCalculated = false;

    // Goal-based pricing model selection
    if (goal == RecommendationRequestDTO.CampaignGoal.IMPRESSIONS
        || goal == RecommendationRequestDTO.CampaignGoal.REACH) {
      if (price.getCpm() != null && price.getCpm() > 0) {
        goalBasedCalculated = true;
        if (forecast != null && forecast.getEstimatedImpressions() != null) {
          estimatedCost =
              java.math.BigDecimal.valueOf(price.getCpm())
                  .divide(java.math.BigDecimal.valueOf(1000), 4, java.math.RoundingMode.HALF_UP)
                  .multiply(java.math.BigDecimal.valueOf(forecast.getEstimatedImpressions()))
                  .setScale(2, java.math.RoundingMode.HALF_UP);
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
              java.math.BigDecimal.valueOf(price.getSpot())
                  .multiply(java.math.BigDecimal.valueOf(totalAdPlays))
                  .setScale(2, java.math.RoundingMode.HALF_UP);
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
            java.math.BigDecimal.valueOf(price.getCpm())
                .divide(java.math.BigDecimal.valueOf(1000), 4, java.math.RoundingMode.HALF_UP)
                .multiply(java.math.BigDecimal.valueOf(forecast.getEstimatedImpressions()))
                .setScale(2, java.math.RoundingMode.HALF_UP);
      }
      // 2. Spot pricing
      else if (price.getSpot() != null
          && inventory.getClassification() != null
          && "Digital".equalsIgnoreCase(inventory.getClassification())) {
        if (totalAdPlays != null && totalAdPlays > 0) {
          estimatedCost =
              java.math.BigDecimal.valueOf(price.getSpot())
                  .multiply(java.math.BigDecimal.valueOf(totalAdPlays))
                  .setScale(2, java.math.RoundingMode.HALF_UP);
        }
      }
      // 3. Monthly
      else if (price.getMonthly() != null) {
        estimatedCost =
            java.math.BigDecimal.valueOf(price.getMonthly())
                .multiply(java.math.BigDecimal.valueOf(days))
                .divide(java.math.BigDecimal.valueOf(30), 2, java.math.RoundingMode.HALF_UP);
      }
      // 4. Daily
      else if (price.getDaily() != null) {
        estimatedCost =
            java.math.BigDecimal.valueOf(price.getDaily())
                .multiply(java.math.BigDecimal.valueOf(days))
                .setScale(2, java.math.RoundingMode.HALF_UP);
      }
      // 5. Weekly
      else if (price.getWeekly() != null) {
        estimatedCost =
            java.math.BigDecimal.valueOf(price.getWeekly())
                .multiply(java.math.BigDecimal.valueOf(days))
                .divide(java.math.BigDecimal.valueOf(7), 2, java.math.RoundingMode.HALF_UP);
      }
    }

    String currency = price.getCurrency();

    Double costPerImpression = null;
    if (forecast != null
        && forecast.getEstimatedImpressions() != null
        && forecast.getEstimatedImpressions() > 0
        && estimatedCost.compareTo(java.math.BigDecimal.ZERO) > 0) {
      costPerImpression =
          estimatedCost
              .divide(
                  java.math.BigDecimal.valueOf(forecast.getEstimatedImpressions()),
                  4,
                  java.math.RoundingMode.HALF_UP)
              .doubleValue();
    }

    return RecommendationResult.CostEstimate.builder()
        .estimatedCost(
            estimatedCost.compareTo(java.math.BigDecimal.ZERO) == 0 ? null : estimatedCost)
        .currency(currency)
        .costPerImpression(costPerImpression)
        .totalAdPlays(totalAdPlays)
        .build();
  }

  /**
   * Calculate inventory_ad_plays for a digital inventory based on operating times and digital
   * fields. Same logic as ScoringServiceImpl.
   */
  private Long calculateInventoryAdPlays(
      Inventory inventory, java.time.LocalDate startDate, java.time.LocalDate endDate) {
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
              LocalTime start =
                  LocalTime.parse(time.getStart(), DateTimeFormatter.ofPattern("HH:mm:ss"));
              LocalTime end =
                  LocalTime.parse(time.getEnd(), DateTimeFormatter.ofPattern("HH:mm:ss"));
              long seconds = ChronoUnit.SECONDS.between(start, end);
              if (seconds < 0) {
                // Handle overnight times (e.g., 22:00 to 02:00)
                seconds =
                    ChronoUnit.SECONDS.between(start, LocalTime.MAX)
                        + ChronoUnit.SECONDS.between(LocalTime.MIN, end)
                        + 1; // +1 to include both start and end
              }
              totalHourSlots += (long) Math.ceil(seconds / 3600.0);
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
  private Inventory.Weekday getWeekday(java.time.LocalDate date) {
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

  // ---------------------------------------------------------------------------
  // Phase 1.6 Optimization: Batched parallel audience data fetching
  // ---------------------------------------------------------------------------

  /**
   * Fetch audience data in batches with parallel execution using $or query. Phase 1.6 Optimization:
   * Prevents MongoDB from choking on huge $in arrays and eliminates duplicate data transfer.
   *
   * @param inventoryIds List of inventory IDs to fetch
   * @param referenceIds List of reference IDs to fetch
   * @param batchSize Size of each batch (recommended: 500)
   * @param audienceByInventoryId Output map for inventoryId lookups
   * @param audienceByReferenceId Output map for referenceId lookups (fallback)
   */
  private void batchFetchAudienceDataInParallel(
      List<String> inventoryIds,
      List<String> referenceIds,
      int batchSize,
      Map<String, AudienceData> audienceByInventoryId,
      Map<String, AudienceData> audienceByReferenceId) {

    if (inventoryIds.isEmpty() && referenceIds.isEmpty()) {
      log.warn("[DIAG-BATCH] No IDs to fetch, skipping audience batch fetch");
      return;
    }

    long startTime = System.currentTimeMillis();
    log.warn(
        "[DIAG-BATCH] Starting parallel batch fetch: {} inventoryIds, {} referenceIds, batchSize={}",
        inventoryIds.size(),
        referenceIds.size(),
        batchSize);

    // Split into batches
    List<List<String>> inventoryIdBatches = partitionList(inventoryIds, batchSize);
    // List<List<String>> referenceIdBatches = partitionList(referenceIds, batchSize);

    int totalBatches = inventoryIdBatches.size();
    log.warn(
        "[DIAG-BATCH] Created {} batches (invId batches={}, refId batches={})",
        totalBatches,
        inventoryIdBatches.size(),
        0);

    // Ensure both lists have same size by padding with empty lists
    while (inventoryIdBatches.size() < totalBatches) {
      inventoryIdBatches.add(List.of());
    }
    // while (referenceIdBatches.size() < totalBatches) {
    //   referenceIdBatches.add(List.of());
    // }

    // Use thread-safe map to collect results from parallel execution
    Map<String, AudienceData> tempAudienceByInventoryId = new ConcurrentHashMap<>();
    Map<String, AudienceData> tempAudienceByReferenceId = new ConcurrentHashMap<>();
    AtomicInteger processedBatches = new AtomicInteger(0);
    AtomicLong totalRecordsFetched = new AtomicLong(0);
    AtomicLong totalQueryTime = new AtomicLong(0);

    // Process batches in parallel using virtual threads
    List<CompletableFuture<Void>> batchFutures = new ArrayList<>();
    for (int i = 0; i < totalBatches; i++) {
      final List<String> invIdBatch = inventoryIdBatches.get(i);
      final List<String> refIdBatch = List.of(); // referenceIdBatches.get(i);
      final int batchNumber = i + 1;

      CompletableFuture<Void> future =
          CompletableFuture.runAsync(
              () -> {
                // Skip empty batches
                if (invIdBatch.isEmpty() && refIdBatch.isEmpty()) {
                  return;
                }

                long batchStart = System.currentTimeMillis();

                // Single $or query for this batch
                List<AudienceData> batchResults =
                    // audienceRepository.findByInventoryIdInOrReferenceIdIn(invIdBatch,
                    // refIdBatch);
                    audienceRepository.findByInventoryIdIn(invIdBatch);

                long batchDuration = System.currentTimeMillis() - batchStart;
                totalQueryTime.addAndGet(batchDuration);
                totalRecordsFetched.addAndGet(batchResults.size());

                // Populate both maps (thread-safe)
                for (AudienceData ad : batchResults) {
                  if (ad.getInventoryId() != null) {
                    tempAudienceByInventoryId.put(ad.getInventoryId(), ad);
                  }
                  if (ad.getReferenceId() != null) {
                    tempAudienceByReferenceId.put(ad.getReferenceId(), ad);
                  }
                }

                processedBatches.incrementAndGet();
                log.debug(
                    "[DIAG-BATCH] Batch {}/{} complete: {}ms, {} records (invIds={}, refIds={})",
                    batchNumber,
                    totalBatches,
                    batchDuration,
                    batchResults.size(),
                    invIdBatch.size(),
                    refIdBatch.size());
              },
              virtualThreadTaskExecutor);

      batchFutures.add(future);
    }

    // Wait for all batches to complete
    CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();

    // Copy results to output maps
    audienceByInventoryId.putAll(tempAudienceByInventoryId);
    audienceByReferenceId.putAll(tempAudienceByReferenceId);

    long totalDuration = System.currentTimeMillis() - startTime;
    double avgBatchTime = totalBatches > 0 ? (double) totalQueryTime.get() / totalBatches : 0;

    log.warn(
        "[DIAG-BATCH] Batch fetch complete: totalTime={}ms, avgBatchTime={}ms, "
            + "totalRecords={}, byInvId={}, byRefId={}, batches={}",
        totalDuration,
        String.format("%.1f", avgBatchTime),
        totalRecordsFetched.get(),
        audienceByInventoryId.size(),
        audienceByReferenceId.size(),
        totalBatches);
  }

  /**
   * Partition a list into smaller batches.
   *
   * @param list List to partition
   * @param batchSize Size of each batch
   * @return List of batches
   */
  private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
    if (list == null || list.isEmpty()) {
      return List.of();
    }

    List<List<T>> batches = new ArrayList<>();
    for (int i = 0; i < list.size(); i += batchSize) {
      int end = Math.min(i + batchSize, list.size());
      batches.add(list.subList(i, end));
    }
    return batches;
  }

  /** Internal class to hold scored inventory data */
  private record ScoredInventory(
      Inventory inventory,
      AudienceData audienceData,
      InventoryScore score,
      Double finalScoreWithJitter) {}

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

  /**
   * Batch-call the Measure API for all scored inventories and return a map of referenceId ->
   * response. Dayparts are generated from the campaign date range (no booking matrix at this
   * stage). Returns an empty map if the API is unavailable or returns no results.
   */
  private Map<String, MeasureReachFrequencyResponseDTO> fetchMeasureReachFrequency(
      List<ScoredInventory> scoredInventories, RecommendationRequestDTO request) {
    if (scoredInventories == null || scoredInventories.isEmpty()) {
      return Collections.emptyMap();
    }

    LocalDate startDate = request.getStartDate();
    LocalDate endDate = request.getEndDate();
    int duration = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;

    // List<MeasureInventoryDTO.Dayparts> dayparts = new ArrayList<>();
    // for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
    //   dayparts.add(
    //       MeasureInventoryDTO.Dayparts.builder()
    //           .scheduledDate(d.format(DATE_FORMATTER))
    //           .scheduledTime(null)
    //           .build());
    // }

    List<MeasureInventoryDTO> inventoryDTOs =
        scoredInventories.stream()
            .map(si -> si.inventory())
            .map(
                inv ->
                    MeasureInventoryDTO.builder()
                        .referenceId(
                            inv.getReferenceId() != null
                                ? inv.getReferenceId()
                                : inv.getInventoryId())
                        .type(mapClassificationToApiType(inv.getClassification()))
                        .spotsPerHour(calcSpotsPerHour(inv))
                        // .dayparts(dayparts)
                        .build())
            .collect(Collectors.toList());

    MeasureReachFrequencyRequestDTO measureRequest =
        MeasureReachFrequencyRequestDTO.builder()
            .inventories(inventoryDTOs)
            .duration(duration)
            .build();

    List<MeasureReachFrequencyResponseDTO> responses =
        measureClientV2.getReachAndFrequencyBySites(measureRequest, true);

    if (responses == null || responses.isEmpty()) {
      return Collections.emptyMap();
    }

    return responses.stream()
        .filter(r -> r.getStatus() != null && r.getReferenceId() != null)
        .collect(Collectors.toMap(MeasureReachFrequencyResponseDTO::getReferenceId, r -> r));
  }

  private static String mapClassificationToApiType(String classification) {
    if (classification == null) return "billboard";
    String c = classification.toLowerCase();
    if (c.contains("classic")) return "static_billboard";
    if (c.contains("network")) return "network";
    return "billboard";
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
}
