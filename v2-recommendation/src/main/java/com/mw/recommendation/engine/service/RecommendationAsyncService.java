package com.mw.recommendation.engine.service;

import com.mw.brand.lib.dto.BrandResponseDTO;
import com.mw.recommendation.engine.domain.AudienceData;
import com.mw.recommendation.engine.domain.BookingData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.domain.RecommendationRun;
import com.mw.recommendation.engine.domain.SelectionMode;
import com.mw.recommendation.engine.dto.InventoryAttributeFilters;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.dto.ScheduleSummaryDTO;
import com.mw.recommendation.engine.dto.measure.MeasureInventoryDTO;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyRequestDTO;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyResponseDTO;
import com.mw.recommendation.engine.repository.AudienceRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
import com.mw.recommendation.engine.repository.RecommendationResultRepository;
import com.mw.recommendation.engine.repository.RecommendationRunRepository;
import com.mw.recommendation.engine.util.BudgetAllocationUtils;
import com.mw.recommendation.engine.util.InventoryAvailabilityUtils;
import com.mw.recommendation.engine.util.PerformanceProfiler;
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
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Service for handling asynchronous recommendation processing. Separated from RecommendationService
 * to ensure @Async works properly (avoids self-invocation issues).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationAsyncService {
  private static final int BATCH_SIZE = 1500;

  /** Score thresholds for batch processing. Inventories are processed in descending bands. */
  private static final int[] SCORE_THRESHOLDS = {90, 80, 70, 60, 50, 40, 30, 20, 10};

  /** Minimum score for auto-selection; inventories at or below this score are not recommended. */
  private static final double MIN_RECOMMENDATION_SCORE = 10.0;

  /**
   * Availability score below which an inventory (with booking data present) is treated as sold out
   * / blocked for the plan's dates and excluded from recommendations.
   */
  static final double AVAILABILITY_EXCLUDE_BELOW_PCT = 10.0;

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  /**
   * Human-readable availability annotation surfaced to planners. Partial availability is called out
   * explicitly so Step 4 can explain why an item was flagged.
   */
  static String buildAvailabilitySummaryText(
      Integer availableDays, int totalDays, Double availabilityPct) {
    if (availabilityPct != null && availabilityPct < 100.0) {
      return String.format(
          "Limited availability for your dates: %d/%d days available", availableDays, totalDays);
    }
    return String.format("%d/%d days available", availableDays, totalDays);
  }

  /**
   * Annotation for inventories excluded as effectively sold out / blocked for the plan's dates.
   * These are persisted as isExcluded=true results so Step 4 can show a clear "unavailable" state
   * instead of a plain unflagged row.
   */
  static String buildUnavailableSummaryText(Integer availableDays, int totalDays) {
    return String.format(
        "Unavailable for your dates: %d/%d days available",
        availableDays != null ? availableDays : 0, totalDays);
  }

  private final InventoryRepository inventoryRepository;
  private final AudienceRepository audienceRepository;
  private final ScoringService scoringService;
  private final RecommendationRunRepository recommendationRunRepository;
  private final RecommendationResultRepository recommendationResultRepository;
  private final ScheduleRecommendationService scheduleRecommendationService;
  private final VirtualThreadTaskExecutor virtualThreadTaskExecutor;
  private final MongoTemplate mongoTemplate;
  private final MeasureApiClient measureApiClient;
  private final Clock clock;
  private final AutoSelectionReasonResolver autoSelectionReasonResolver;

  /**
   * Process recommendations asynchronously with completion tracking
   *
   * @param runId Run ID
   * @param campaignId Campaign ID
   * @param request Recommendation request
   */
  @Async
  public void processRecommendationsAsync(
      String runId, String campaignId, RecommendationRequestDTO request) {

    // Initialize performance profiler
    PerformanceProfiler profiler = new PerformanceProfiler("runId=" + runId);

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
                programmaticEnabled,
                request.getDurations(),
                buildAttributeFilters(request));
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
                "v1", runId, List.of(), request, List.of(), 0, Map.of(), MIN_RECOMMENDATION_SCORE);
        completeRunWithEmptyResults(runId, campaignId, exclusionReasons, warnings, emptyReason);
        profiler.logSummary();
        return;
      }

      log.info("Found {} inventories for country: {}", inventories.size(), request.getCountry());

      // Build inventory lookup map for auto-selection (avoid re-fetching)
      Map<String, Inventory> inventoryMap =
          inventories.stream()
              .collect(Collectors.toMap(Inventory::getInventoryId, inv -> inv, (a, b) -> a));

      // PROFILING: Batch fetch all data before scoring (Phase 1 optimization)
      profiler.startStep("2a_BatchFetchData");
      final Map<String, AudienceData> audienceDataByInventoryId = new HashMap<>();
      final Map<String, AudienceData> audienceDataByReferenceId = new HashMap<>();
      Map<String, List<BookingData>> bookingDataByInventoryId = new HashMap<>();
      Map<String, BrandResponseDTO> brandDataById = new HashMap<>();

      try {
        // Extract all inventory IDs and reference IDs
        List<String> inventoryIds =
            inventories.stream().map(Inventory::getInventoryId).collect(Collectors.toList());
        List<String> referenceIds = new ArrayList<>();
        // List<String> referenceIds =
        //     inventories.stream().map(Inventory::getReferenceId).collect(Collectors.toList());

        // Audience, booking, and brand fetches are independent — run booking and brand
        // concurrently with the audience fetch instead of sequentially after it.
        AtomicLong bookingFetchDurationRef = new AtomicLong();
        AtomicLong brandFetchDurationRef = new AtomicLong();
        Executor batchFetchExecutor = securityAwareExecutor();

        // Batch fetch booking data (1 query instead of 1000)
        CompletableFuture<Map<String, List<BookingData>>> bookingFuture =
            CompletableFuture.supplyAsync(
                () -> {
                  long start = System.currentTimeMillis();
                  Map<String, List<BookingData>> bookings =
                      scoringService.batchFetchBookingData(
                          inventoryIds, request.getStartDate(), request.getEndDate());
                  bookingFetchDurationRef.set(System.currentTimeMillis() - start);
                  return bookings;
                },
                batchFetchExecutor);

        // Batch fetch brand data if brandId provided (1 call instead of 1000)
        CompletableFuture<Map<String, BrandResponseDTO>> brandFuture =
            request.getBrandId() != null
                ? CompletableFuture.supplyAsync(
                    () -> {
                      long start = System.currentTimeMillis();
                      Map<String, BrandResponseDTO> brands =
                          scoringService.batchFetchBrandData(List.of(request.getBrandId()));
                      brandFetchDurationRef.set(System.currentTimeMillis() - start);
                      return brands;
                    },
                    batchFetchExecutor)
                : CompletableFuture.completedFuture(new HashMap<>());

        // Phase 1.6: Batch fetch audience data with parallel execution
        // Uses $or query + batching (500 per batch) to prevent MongoDB choking on huge $in arrays
        long audienceFetchStart = System.currentTimeMillis();
        // batchFetchAudienceDataInParallel(
        //     inventoryIds, referenceIds, 500, audienceDataByInventoryId,
        // audienceDataByReferenceId);
        batchFetchAudienceDataInParallel(
            inventoryIds, referenceIds, 1000, audienceDataByInventoryId, audienceDataByReferenceId);
        long audienceFetchDuration = System.currentTimeMillis() - audienceFetchStart;
        log.info(
            "Batch fetched audience data in {}ms ({} by invId, {} by refId)",
            audienceFetchDuration,
            audienceDataByInventoryId.size(),
            audienceDataByReferenceId.size());

        bookingDataByInventoryId = bookingFuture.join();
        brandDataById = brandFuture.join();
        long bookingFetchDuration = bookingFetchDurationRef.get();
        long brandFetchDuration = brandFetchDurationRef.get();

        log.info(
            "Batch fetch completed - Audience: {}ms ({} by invId, {} by refId), Booking: {}ms ({} inventories with bookings), Brand: {}ms ({} brands)",
            audienceFetchDuration,
            audienceDataByInventoryId.size(),
            audienceDataByReferenceId.size(),
            bookingFetchDuration,
            bookingDataByInventoryId.size(),
            brandFetchDuration,
            brandDataById.size());

      } finally {
        Map<String, Object> batchFetchMetadata = new HashMap<>();
        batchFetchMetadata.put("audienceByInventoryIdCount", audienceDataByInventoryId.size());
        batchFetchMetadata.put("audienceByReferenceIdCount", audienceDataByReferenceId.size());
        batchFetchMetadata.put("bookingDataInventoryCount", bookingDataByInventoryId.size());
        batchFetchMetadata.put("brandCount", brandDataById.size());
        profiler.endStep("2a_BatchFetchData", batchFetchMetadata);
      }

      // PROFILING: Score all inventories (Phase 2 - parallel with virtual threads)
      profiler.startStep("2b_ScoreInventories_Parallel");
      int totalInventories = inventories.size();

      // Create final references for lambda capture (Java effectively final requirement)
      final Map<String, List<BookingData>> bookingDataFinal = bookingDataByInventoryId;
      final Map<String, BrandResponseDTO> brandDataFinal = brandDataById;

      // Use thread-safe collections and atomic counters for parallel processing
      final List<ScoredInventory> scoredInventories =
          Collections.synchronizedList(new ArrayList<>(totalInventories));
      AtomicInteger processedCount = new AtomicInteger(0);
      AtomicInteger scoringErrors = new AtomicInteger(0);
      AtomicLong audienceFetchTimeTotal = new AtomicLong(0);
      AtomicLong scoreCalcTimeTotal = new AtomicLong(0);
      AtomicInteger bookingCacheHits = new AtomicInteger(0);
      AtomicInteger brandCacheHits = new AtomicInteger(0);
      Map<String, Integer> exclusionReasonsThreadSafe = new ConcurrentHashMap<>(exclusionReasons);
      // Availability-excluded inventories are kept (not silently dropped) so they can be
      // persisted as isExcluded=true results for planner-facing "unavailable" explanations.
      final List<ScoredInventory> availabilityExcludedInventories =
          Collections.synchronizedList(new ArrayList<>());

      try {
        // Create parallel tasks using virtual threads for each inventory
        List<CompletableFuture<Void>> scoringFutures =
            inventories.stream()
                .map(
                    inventory ->
                        CompletableFuture.runAsync(
                            () -> {
                              try {
                                // Cache lookup (O(1) instead of DB query) - thread-safe read
                                long audienceStart = System.nanoTime();
                                AudienceData audienceData =
                                    audienceDataByInventoryId.getOrDefault(
                                        inventory.getInventoryId(),
                                        audienceDataByReferenceId.get(inventory.getReferenceId()));
                                audienceFetchTimeTotal.addAndGet(System.nanoTime() - audienceStart);

                                // Track cache hits for analysis
                                if (bookingDataFinal.containsKey(inventory.getInventoryId())) {
                                  bookingCacheHits.incrementAndGet();
                                }
                                if (request.getBrandId() != null
                                    && brandDataFinal.containsKey(request.getBrandId())) {
                                  brandCacheHits.incrementAndGet();
                                }

                                // Track score calculation using cached data (Phase 1.5 -
                                // eliminates N+1 queries!)
                                long scoreStart = System.nanoTime();
                                InventoryScore score =
                                    scoringService.calculateScore(
                                        inventory,
                                        audienceData,
                                        request,
                                        bookingDataFinal, // Pass cached booking data
                                        brandDataFinal); // Pass cached brand data
                                scoreCalcTimeTotal.addAndGet(System.nanoTime() - scoreStart);

                                // Availability-aware exclusion: booking data existed for this
                                // inventory and it is effectively sold out / blocked for the
                                // plan's dates — drop it instead of recommending it.
                                if (score.getAvailability() != null
                                    && score.getAvailability() < AVAILABILITY_EXCLUDE_BELOW_PCT
                                    && bookingDataFinal.containsKey(inventory.getInventoryId())) {
                                  exclusionReasonsThreadSafe.merge(
                                      "AVAILABILITY_UNAVAILABLE", 1, Integer::sum);
                                  availabilityExcludedInventories.add(
                                      new ScoredInventory(inventory, audienceData, score, 0.0));
                                  processedCount.incrementAndGet();
                                  return;
                                }

                                // Apply jitter for deterministic variation
                                Double finalScoreWithJitter =
                                    VariationUtils.applyJitter(score.getFinalScore(), runId);

                                // Add to thread-safe list
                                scoredInventories.add(
                                    new ScoredInventory(
                                        inventory, audienceData, score, finalScoreWithJitter));

                              } catch (Exception e) {
                                log.warn(
                                    "Error scoring inventory {}: {}",
                                    inventory.getReferenceId(),
                                    e.getMessage());
                                exclusionReasonsThreadSafe.merge("SCORING_ERROR", 1, Integer::sum);
                                scoringErrors.incrementAndGet();
                              }

                              // Update progress atomically
                              int current = processedCount.incrementAndGet();

                              // Log progress every 100 inventories for visibility
                              if (current % 100 == 0) {
                                // double avgScoreTimeMs =
                                //     scoreCalcTimeTotal.get() / 1_000_000.0 / current;
                                // log.info(
                                //     "Scoring progress: {}/{} inventories ({}%) - Avg time per"
                                //         + " inventory: {}ms",
                                //     current,
                                //     totalInventories,
                                //     (100 * current / totalInventories),
                                //     String.format("%.2f", avgScoreTimeMs));
                              }

                              // Update completion: 30% (fetch) + 50% (scoring) = 80% total
                              if (current % 10 == 0) { // Update every 10 to reduce contention
                                int completion = 30 + (int) (50.0 * current / totalInventories);
                                updateCompletionPercentage(runId, completion);
                              }
                            },
                            virtualThreadTaskExecutor))
                .toList();

        // Wait for all parallel scoring tasks to complete
        CompletableFuture.allOf(scoringFutures.toArray(new CompletableFuture[0])).join();

        // Copy thread-safe exclusion reasons back to original map
        exclusionReasons.putAll(exclusionReasonsThreadSafe);
      } finally {
        Map<String, Object> scoringMetadata = new HashMap<>();
        scoringMetadata.put("totalInventories", totalInventories);
        scoringMetadata.put("successfullyScored", totalInventories - scoringErrors.get());
        scoringMetadata.put("scoringErrors", scoringErrors.get());
        scoringMetadata.put("audienceLookupMs", audienceFetchTimeTotal.get() / 1_000_000.0);
        scoringMetadata.put("scoreCalcMs", scoreCalcTimeTotal.get() / 1_000_000.0);
        scoringMetadata.put("bookingCacheHits", bookingCacheHits.get());
        scoringMetadata.put("brandCacheHits", brandCacheHits.get());
        scoringMetadata.put("parallelized", true); // Phase 2 marker
        scoringMetadata.put(
            "avgPerInventoryMs",
            totalInventories > 0
                ? (audienceFetchTimeTotal.get() + scoreCalcTimeTotal.get())
                    / 1_000_000.0
                    / totalInventories
                : 0);

        // Enhanced logging for Phase 2 analysis (parallel execution)
        log.info(
            "Scoring completed (PARALLEL) - Total: {}s, Avg per inventory: {}ms, "
                + "Cache utilization - Booking: {}/{} ({}%), Brand: {}/{} ({}%)",
            String.format("%.2f", scoreCalcTimeTotal.get() / 1_000_000_000.0),
            String.format(
                "%.2f",
                totalInventories > 0
                    ? (scoreCalcTimeTotal.get() / 1_000_000.0 / totalInventories)
                    : 0),
            bookingCacheHits.get(),
            totalInventories,
            String.format(
                "%.1f",
                totalInventories > 0 ? (100.0 * bookingCacheHits.get() / totalInventories) : 0),
            brandCacheHits.get(),
            totalInventories,
            String.format(
                "%.1f",
                totalInventories > 0 ? (100.0 * brandCacheHits.get() / totalInventories) : 0));

        profiler.endStep("2b_ScoreInventories_Parallel", scoringMetadata);
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

      // PROFILING: Batch-call Measure API for reach/frequency/impressions across all scored
      // inventories
      profiler.startStep("3b_FetchMeasureReachFrequency");
      Map<String, MeasureReachFrequencyResponseDTO> measureResponseByRefId = Collections.emptyMap();
      try {
        measureResponseByRefId = fetchMeasureReachFrequency(finalScoredInventories, request);
      } finally {
        profiler.endStep(
            "3b_FetchMeasureReachFrequency",
            Map.of(
                "inventoryCount",
                finalScoredInventories.size(),
                "responseCount",
                measureResponseByRefId.size()));
      }

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
          excludedResults.add(excludedResult);
        }
      }

      updateCompletionPercentage(runId, 85);

      // PROFILING: Auto-select phase
      profiler.startStep("5_AutoSelection");
      List<String> autoSelectedIds = null;
      try {
        log.info("Starting auto-selection for runId: {}, campaign: {}", runId, campaignId);
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
              "v1",
              runId,
              results,
              request,
              autoSelectedIds,
              finalScoredInventories.size(),
              measureResponseByRefId,
              MIN_RECOMMENDATION_SCORE);

      // PROFILING: Save results with initial data
      profiler.startStep("6_SaveResults");
      try {
        long diagSaveT1 = System.currentTimeMillis();
        // recommendationResultRepository.saveAll(results);
        // mongoTemplate.insert(results, RecommendationResult.class);
        bulkInsertResults(results);
        if (!excludedResults.isEmpty()) {
          bulkInsertResults(excludedResults);
          log.info(
              "Persisted {} availability-excluded results for runId: {}",
              excludedResults.size(),
              runId);
        }

        long diagSaveT2 = System.currentTimeMillis();
        log.warn(
            "[DIAG] saveAll() took {}ms for {} documents ({}ms/doc)",
            (diagSaveT2 - diagSaveT1),
            results.size(),
            (diagSaveT2 - diagSaveT1) / (double) results.size());
      } finally {
        profiler.endStep("6_SaveResults", Map.of("savedCount", results.size()));
      }

      // PROFILING: Bulk update selectionMode (Phase 3.2)
      log.warn(
          "[DIAG] About to update selectionMode - results.size()={}, autoSelectedIds.size()={}",
          results.size(),
          (autoSelectedIds != null ? autoSelectedIds.size() : 0));
      profiler.startStep("7_UpdateSelectionMode");
      try {
        // DIAGNOSTICS: Track timing of each phase
        long diagT1 = System.currentTimeMillis();

        // Build update map for bulk operation
        Map<String, SelectionMode> selectionModeMap = new HashMap<>();
        int autoCount = 0;
        for (RecommendationResult r : results) {
          if (autoSelectedIds != null && autoSelectedIds.contains(r.getInventoryId())) {
            selectionModeMap.put(r.getInventoryId(), SelectionMode.AUTO);
            autoCount++;
          } else {
            selectionModeMap.put(r.getInventoryId(), null);
          }
        }

        long diagT2 = System.currentTimeMillis();
        log.warn(
            "[DIAG] Built selectionMode map in {}ms - total={}, auto={}, null={}",
            (diagT2 - diagT1),
            selectionModeMap.size(),
            autoCount,
            (selectionModeMap.size() - autoCount));

        // Bulk update only selectionMode field
        recommendationResultRepository.bulkUpdateSelectionMode(runId, selectionModeMap);

        long diagT3 = System.currentTimeMillis();
        log.warn(
            "[DIAG] bulkUpdateSelectionMode took {}ms ({}ms/update)",
            (diagT3 - diagT2),
            (diagT3 - diagT2) / (double) selectionModeMap.size());
      } finally {
        profiler.endStep("7_UpdateSelectionMode", Map.of("updatedCount", results.size()));
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
      // Mark run as failed or keep IN_PROGRESS - you might want to add a FAILED status
      updateCompletionPercentage(runId, 0);

      // Log partial profiling data on error
      try {
        profiler.logSummary();
      } catch (Exception logEx) {
        log.warn("Failed to log performance summary on error: {}", logEx.getMessage());
      }
    }
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
              Boolean.TRUE.equals(request.getProgrammaticEnabled()),
              request.getDurations(),
              buildAttributeFilters(request));

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
    Double monthlyRate = null;
    Double dailyRate = null;
    if (inventory.getPrices() != null && !inventory.getPrices().isEmpty()) {
      Inventory.PriceModel price = inventory.getPrices().getFirst();
      currency = price.getCurrency();
      monthlyRate = price.getMonthly();
      dailyRate = price.getDaily();

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
      } else if ((price.getMonthly() != null && price.getMonthly() > 0)
          || (price.getDaily() != null && price.getDaily() > 0)) {
        // Classic rate-card: whole-months@monthly + remainder-days@daily (fallback monthly/30).
        estimatedCost =
            com.mw.recommendation.engine.util.ClassicPricing.estimatedCost(price, totalDays);
      }
    }

    RecommendationResult.CostEstimate cost =
        RecommendationResult.CostEstimate.builder()
            .estimatedCost(estimatedCost)
            .currency(currency)
            .totalAdPlays(totalAdPlays)
            // Raw rate-card rates so the client can recompute cost as flight days change.
            .monthly(monthlyRate)
            .daily(dailyRate)
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
  private InventoryAttributeFilters buildAttributeFilters(RecommendationRequestDTO request) {
    return InventoryAttributeFilters.builder()
        .formats(request.getFormats())
        .resolutions(request.getResolutions())
        .creativeTypes(request.getCreativeTypes())
        .dsps(request.getDsps())
        .dealTypes(request.getDealTypes())
        .programmaticSupport(request.getProgrammaticSupport())
        .inventoryCluster(request.getInventoryCluster())
        .build();
  }

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
      boolean programmaticEnabled,
      List<Integer> durations,
      InventoryAttributeFilters attributeFilters) {

    // Use custom repository method with geospatial and venue filtering. All four extra filters
    // (dsps, programmaticEnabled, durations, attributeFilters) are passed to the canonical method;
    // empty/null durations and attributeFilters are treated as no-ops in the repository, so the
    // no-filter case behaves identically to the back-compat overloads.
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
            programmaticEnabled,
            durations,
            attributeFilters);

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

  /** Update completion percentage for a run */
  private void updateCompletionPercentage(String runId, int percentage) {
    recommendationRunRepository
        .findByRunId(runId)
        .ifPresent(
            run -> {
              run.setCompletionPercentage(Math.min(100, Math.max(0, percentage)));
              recommendationRunRepository.save(run);
            });
  }

  /**
   * Complete a run with results. The optional auto-selection reason is folded into this same
   * existing save (no additional write); null (e.g. BROWSE mode) leaves the fields untouched.
   */
  private void completeRun(
      String runId,
      RecommendationRun.RecommendationMetadata metadata,
      List<String> warnings,
      List<String> autoSelectedInventoryIds,
      AutoSelectionReasonResolver.ReasonResolution autoSelectionReason) {
    recommendationRunRepository
        .findByRunId(runId)
        .ifPresent(
            run -> {
              run.setStatus(RecommendationRun.RunStatus.COMPLETED);
              run.setCompletionPercentage(100);
              run.setCompletedAt(LocalDateTime.now());
              run.setMetadata(metadata);
              run.setWarnings(warnings);
              run.setAutoSelectedInventoryIds(
                  autoSelectedInventoryIds != null ? autoSelectedInventoryIds : List.of());
              if (autoSelectionReason != null) {
                run.setAutoSelectionReasonCode(autoSelectionReason.code());
                run.setAutoSelectionReasonDetail(autoSelectionReason.detail());
                run.setAutoSelectionDiagnostics(autoSelectionReason.diagnostics());
              }
              recommendationRunRepository.save(run);
            });
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

    log.info(">>>> Request hasBudget={}, hasGoal={}", hasBudget, hasGoal);

    if (!hasBudget && !hasGoal) {
      clearSelectionMode(results);
      return List.of();
    }

    if (request.getStartDate() == null || request.getEndDate() == null) {
      clearSelectionMode(results);
      return List.of();
    }

    if (hasBudget) {
      log.info(
          "Executing budget-aware auto-selection for runId: {}, campaign: {}", runId, campaignId);
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
    PerformanceProfiler autoSelectProfiler = new PerformanceProfiler("autoSelect_runId=" + runId);

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
      log.info(
          "Results for category '{}': budgetCap={}, inventories={}",
          categoryKey,
          budgetCap,
          categoryResults.size());

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
              virtualThreadTaskExecutor));
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
    log.info("Global schedule cache size: {}", globalScheduleCache.size());
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
    log.info(
        "TTTT Inventories to build schedules for category '{}': {} - Category Results Count: {}",
        state.categoryKey,
        allInventoriesToBuild.size(),
        state.categoryResults.size());

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
                virtualThreadTaskExecutor);

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
              virtualThreadTaskExecutor);

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

    // 2. Create a thread pool — capped at number of batches or 4 threads max
    int threadCount = Math.min(batches.size(), 2);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

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
                try {
                  mongoTemplate.insert(batch, RecommendationResult.class);
                  totalInserted.addAndGet(batch.size());

                  log.info(
                      "Batch {}/{} — inserted={}, took={}ms [thread={}]",
                      batchNumber,
                      totalBatches,
                      batch.size(),
                      System.currentTimeMillis() - t1,
                      Thread.currentThread().getName());

                } catch (Exception e) {
                  failedBatches.incrementAndGet();
                  log.error(
                      "Batch {}/{} failed after {}ms — error: {}",
                      batchNumber,
                      totalBatches,
                      System.currentTimeMillis() - t1,
                      e.getMessage(),
                      e);
                }
              },
              executor);

      futures.add(future);
    }

    // 4. Wait for all batches to complete
    try {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    } finally {
      executor.shutdown(); // always release threads
    }

    long duration = System.currentTimeMillis() - startTime;

    if (failedBatches.get() > 0) {
      log.error(
          "Parallel bulk insert completed with {} failed batches — totalInserted={}, duration={}ms",
          failedBatches.get(),
          totalInserted.get(),
          duration);
    } else {
      log.info(
          "Parallel bulk insert complete — totalInserted={}, duration={}ms",
          totalInserted.get(),
          duration);
    }
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
                    ? buildUnavailableSummaryText(availableDays, (int) totalDays)
                    : buildAvailabilitySummaryText(
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
      // 3. Classic rate-card (monthly + daily): whole-months@monthly + remainder-days@daily
      //    (fallback monthly/30). Covers monthly-only, daily-only, and monthly+daily.
      else if ((price.getMonthly() != null && price.getMonthly() > 0)
          || (price.getDaily() != null && price.getDaily() > 0)) {
        java.math.BigDecimal classic =
            com.mw.recommendation.engine.util.ClassicPricing.estimatedCost(price, days);
        if (classic != null) {
          estimatedCost = classic;
        }
      }
      // 4. Weekly
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
        // Expose the raw rate-card rates so the client can recompute cost as flight days change.
        .monthly(price.getMonthly())
        .daily(price.getDaily())
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

  /**
   * Wraps the shared virtual-thread executor so the submitting thread's {@link SecurityContext}
   * (and thus the bearer token read by {@code MeasureApiClient}) is visible on the virtual threads
   * that run batched Measure calls. The raw executor does not propagate it — only the
   * {@code @Async} executor is wrapped in {@code VirtualThreadConfig}. Capture happens on the
   * submitting thread, so this must be called at each dispatch site.
   */
  private Executor securityAwareExecutor() {
    final SecurityContext context = SecurityContextHolder.getContext();
    return command ->
        virtualThreadTaskExecutor.execute(
            () -> {
              SecurityContext previous = SecurityContextHolder.getContext();
              SecurityContextHolder.setContext(context);
              try {
                command.run();
              } finally {
                SecurityContextHolder.setContext(previous);
              }
            });
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

    // Sort by referenceId and dedupe so batch composition is deterministic across runs (scored
    // order varies with per-run jitter) — this keeps the per-batch @Cacheable keys stable — and
    // so each refId lives in exactly one batch.
    Map<String, MeasureInventoryDTO> dtoByRefId = new TreeMap<>();
    for (ScoredInventory si : scoredInventories) {
      Inventory inv = si.inventory();
      String refId = inv.getReferenceId() != null ? inv.getReferenceId() : inv.getInventoryId();
      dtoByRefId.putIfAbsent(
          String.valueOf(refId),
          MeasureInventoryDTO.builder()
              .referenceId(refId)
              .type(mapClassificationToApiType(inv.getClassification()))
              .spotsPerHour(calcSpotsPerHour(inv))
              // .dayparts(dayparts)
              .build());
    }
    List<MeasureInventoryDTO> inventoryDTOs = new ArrayList<>(dtoByRefId.values());

    // Batch and run in parallel (same size as the auto-selection Measure calls) so wall-clock is
    // one batch, not the whole set. Each batch flows through the fail-soft, cached client — a
    // failed batch degrades only its own inventories instead of wiping all enrichment.
    List<List<MeasureInventoryDTO>> batches = partitionList(inventoryDTOs, BATCH_SIZE);
    Map<String, MeasureReachFrequencyResponseDTO> responseByRefId = new ConcurrentHashMap<>();
    Executor executor = securityAwareExecutor();
    List<CompletableFuture<Void>> futures = new ArrayList<>(batches.size());
    for (List<MeasureInventoryDTO> batch : batches) {
      futures.add(
          CompletableFuture.runAsync(
              () -> {
                MeasureReachFrequencyRequestDTO batchRequest =
                    MeasureReachFrequencyRequestDTO.builder()
                        .inventories(batch)
                        .duration(duration)
                        .build();
                List<MeasureReachFrequencyResponseDTO> responses =
                    measureApiClient.getReachAndFrequencyBySites(batchRequest, true);
                if (responses == null) {
                  return;
                }
                for (MeasureReachFrequencyResponseDTO r : responses) {
                  if (r.getStatus() != null && r.getReferenceId() != null) {
                    responseByRefId.put(r.getReferenceId(), r);
                  }
                }
              },
              executor));
    }
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    log.info(
        "Measure reach/frequency fetched in {} parallel batches: {} inventories requested, {} responses",
        batches.size(),
        inventoryDTOs.size(),
        responseByRefId.size());
    return responseByRefId;
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
