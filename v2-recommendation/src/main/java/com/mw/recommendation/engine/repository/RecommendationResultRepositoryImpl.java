package com.mw.recommendation.engine.repository;

import com.mongodb.client.result.UpdateResult;
import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.domain.SelectionMode;
import com.mw.recommendation.engine.dto.RecommendationResultFilterDTO;
import com.mw.recommendation.engine.enums.ProgrammaticSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/** Custom repository implementation for RecommendationResult with dynamic filtering */
@Repository
@RequiredArgsConstructor
@Slf4j
public class RecommendationResultRepositoryImpl implements RecommendationResultRepositoryCustom {
  private static final int BATCH_SIZE = 1000;

  private final MongoTemplate mongoTemplate;

  @Override
  public Page<RecommendationResult> findByRunIdWithFilters(
      String runId, RecommendationResultFilterDTO filter, String search, Pageable pageable) {

    // Build base criteria
    Criteria baseCriteria = Criteria.where("runId").is(runId);

    // Apply filters if provided
    if (filter != null) {
      List<Criteria> filterCriteriaList = buildFilterCriteria(filter);
      if (!filterCriteriaList.isEmpty()) {
        Criteria filterCriteria =
            new Criteria().andOperator(filterCriteriaList.toArray(new Criteria[0]));
        baseCriteria = new Criteria().andOperator(baseCriteria, filterCriteria);
      }
    }

    // Apply search criteria if provided (AND with existing criteria)
    if (search != null && !search.isBlank()) {
      String trimmed = search.trim();
      String escaped = ".*" + Pattern.quote(trimmed) + ".*";
      List<Criteria> searchCriteriaList = new ArrayList<>();
      searchCriteriaList.add(Criteria.where("referenceId").regex(escaped, "i"));
      searchCriteriaList.add(Criteria.where("inventoryDetails.name").regex(escaped, "i"));
      searchCriteriaList.add(Criteria.where("inventoryDetails.address").regex(escaped, "i"));
      searchCriteriaList.add(
          Criteria.where("inventoryDetails.location.cityName").regex(escaped, "i"));
      searchCriteriaList.add(Criteria.where("inventoryDetails.classification").regex(escaped, "i"));
      searchCriteriaList.add(Criteria.where("inventoryDetails.type").regex(escaped, "i"));
      Criteria searchCriteria =
          new Criteria().orOperator(searchCriteriaList.toArray(new Criteria[0]));
      baseCriteria = new Criteria().andOperator(baseCriteria, searchCriteria);
    }

    // Create query for counting (without pagination)
    Query countQuery = new Query(baseCriteria);
    long total = mongoTemplate.count(countQuery, RecommendationResult.class);

    // Create query for fetching (with pagination and sorting)
    Query query = new Query(baseCriteria);
    query.with(pageable);

    // Fetch paginated results
    List<RecommendationResult> results = mongoTemplate.find(query, RecommendationResult.class);

    // Return Page implementation
    return new PageImpl<>(results, pageable, total);
  }

  /**
   * Build MongoDB Criteria from filter DTO
   *
   * @param filter Filter DTO
   * @return List of Criteria
   */
  private List<Criteria> buildFilterCriteria(RecommendationResultFilterDTO filter) {
    List<Criteria> criteriaList = new ArrayList<>();

    // Inventory identifiers
    if (filter.getInventoryIds() != null && !filter.getInventoryIds().isEmpty()) {
      criteriaList.add(Criteria.where("inventoryId").in(filter.getInventoryIds()));
    }
    if (filter.getReferenceIds() != null && !filter.getReferenceIds().isEmpty()) {
      criteriaList.add(Criteria.where("referenceId").in(filter.getReferenceIds()));
    }

    // Inventory classification (now nested in inventoryDetails)
    if (filter.getClassifications() != null && !filter.getClassifications().isEmpty()) {
      criteriaList.add(
          Criteria.where("inventoryDetails.classification").in(filter.getClassifications()));
    }
    if (filter.getTypes() != null && !filter.getTypes().isEmpty()) {
      criteriaList.add(Criteria.where("inventoryDetails.type").in(filter.getTypes()));
    }
    if (filter.getFormats() != null && !filter.getFormats().isEmpty()) {
      criteriaList.add(Criteria.where("inventoryDetails.format").in(filter.getFormats()));
    }
    if (filter.getEnvironments() != null && !filter.getEnvironments().isEmpty()) {
      List<String> lowerCaseEnvironments =
          filter.getEnvironments().stream()
              .map(String::toLowerCase)
              .toList(); // Perfect for Java 17+
      criteriaList.add(Criteria.where("inventoryDetails.environment").in(lowerCaseEnvironments));
    }
    if (filter.getVenueTypes() != null && !filter.getVenueTypes().isEmpty()) {
      criteriaList.add(Criteria.where("inventoryDetails.venueTypes").in(filter.getVenueTypes()));
    }
    if (filter.getOrientations() != null && !filter.getOrientations().isEmpty()) {
      criteriaList.add(Criteria.where("inventoryDetails.orientation").in(filter.getOrientations()));
    }
    if (filter.getSizes() != null && !filter.getSizes().isEmpty()) {
      criteriaList.add(Criteria.where("inventoryDetails.sizes").in(filter.getSizes()));
    }
    if (filter.getResolutions() != null && !filter.getResolutions().isEmpty()) {
      criteriaList.add(Criteria.where("inventoryDetails.resolutions").in(filter.getResolutions()));
    }
    // `durations` matches the screen's physical slot length (inventoryDetails.spotDuration) — there
    // is no inventoryDetails.durations field. Digital-only; a single-element list is an exact
    // match,
    // so a 10s-slot screen is excluded for a 15s creative (prevents the booking-time
    // "creativeDuration exceeds spotDuration" reject).
    if (filter.getDurations() != null && !filter.getDurations().isEmpty()) {
      criteriaList.add(Criteria.where("inventoryDetails.spotDuration").in(filter.getDurations()));
    }

    // Media owner (now nested in inventoryDetails)
    if (filter.getMediaOwnerIds() != null && !filter.getMediaOwnerIds().isEmpty()) {
      criteriaList.add(
          Criteria.where("inventoryDetails.mediaOwnerId").in(filter.getMediaOwnerIds()));
    }
    if (filter.getMediaOwnerNames() != null && !filter.getMediaOwnerNames().isEmpty()) {
      criteriaList.add(
          Criteria.where("inventoryDetails.mediaOwnerName").in(filter.getMediaOwnerNames()));
    }

    // Location filters (now nested in inventoryDetails.location)
    if (filter.getCountryIds() != null && !filter.getCountryIds().isEmpty()) {
      criteriaList.add(
          Criteria.where("inventoryDetails.location.countryId").in(filter.getCountryIds()));
    }
    if (filter.getCountryNames() != null && !filter.getCountryNames().isEmpty()) {
      criteriaList.add(
          Criteria.where("inventoryDetails.location.countryName").in(filter.getCountryNames()));
    }
    if (filter.getStateIds() != null && !filter.getStateIds().isEmpty()) {
      criteriaList.add(
          Criteria.where("inventoryDetails.location.stateId").in(filter.getStateIds()));
    }
    if (filter.getStateNames() != null && !filter.getStateNames().isEmpty()) {
      criteriaList.add(
          Criteria.where("inventoryDetails.location.stateName").in(filter.getStateNames()));
    }
    if (filter.getCityIds() != null && !filter.getCityIds().isEmpty()) {
      criteriaList.add(Criteria.where("inventoryDetails.location.cityId").in(filter.getCityIds()));
    }
    if (filter.getCityNames() != null && !filter.getCityNames().isEmpty()) {
      criteriaList.add(
          Criteria.where("inventoryDetails.location.cityName").in(filter.getCityNames()));
    }

    // Final score range
    if (filter.getMinFinalScore() != null) {
      criteriaList.add(Criteria.where("finalScore").gte(filter.getMinFinalScore()));
    }
    if (filter.getMaxFinalScore() != null) {
      criteriaList.add(Criteria.where("finalScore").lte(filter.getMaxFinalScore()));
    }

    // Component score ranges
    if (filter.getMinMeasureFit() != null) {
      criteriaList.add(Criteria.where("componentScores.measureFit").gte(filter.getMinMeasureFit()));
    }
    if (filter.getMaxMeasureFit() != null) {
      criteriaList.add(Criteria.where("componentScores.measureFit").lte(filter.getMaxMeasureFit()));
    }
    if (filter.getMinGeoFit() != null) {
      criteriaList.add(Criteria.where("componentScores.geoFit").gte(filter.getMinGeoFit()));
    }
    if (filter.getMaxGeoFit() != null) {
      criteriaList.add(Criteria.where("componentScores.geoFit").lte(filter.getMaxGeoFit()));
    }
    if (filter.getMinAvailability() != null) {
      criteriaList.add(
          Criteria.where("componentScores.availability").gte(filter.getMinAvailability()));
    }
    if (filter.getMaxAvailability() != null) {
      criteriaList.add(
          Criteria.where("componentScores.availability").lte(filter.getMaxAvailability()));
    }
    if (filter.getMinBudgetFit() != null) {
      criteriaList.add(Criteria.where("componentScores.budgetFit").gte(filter.getMinBudgetFit()));
    }
    if (filter.getMaxBudgetFit() != null) {
      criteriaList.add(Criteria.where("componentScores.budgetFit").lte(filter.getMaxBudgetFit()));
    }
    if (filter.getMinAudienceFit() != null) {
      criteriaList.add(
          Criteria.where("componentScores.audienceFit").gte(filter.getMinAudienceFit()));
    }
    if (filter.getMaxAudienceFit() != null) {
      criteriaList.add(
          Criteria.where("componentScores.audienceFit").lte(filter.getMaxAudienceFit()));
    }
    if (filter.getMinBrandFit() != null) {
      criteriaList.add(Criteria.where("componentScores.brandFit").gte(filter.getMinBrandFit()));
    }
    if (filter.getMaxBrandFit() != null) {
      criteriaList.add(Criteria.where("componentScores.brandFit").lte(filter.getMaxBrandFit()));
    }
    if (filter.getMinQualityFit() != null) {
      criteriaList.add(Criteria.where("componentScores.qualityFit").gte(filter.getMinQualityFit()));
    }
    if (filter.getMaxQualityFit() != null) {
      criteriaList.add(Criteria.where("componentScores.qualityFit").lte(filter.getMaxQualityFit()));
    }
    if (filter.getMinTimeFit() != null) {
      criteriaList.add(Criteria.where("componentScores.timeFit").gte(filter.getMinTimeFit()));
    }
    if (filter.getMaxTimeFit() != null) {
      criteriaList.add(Criteria.where("componentScores.timeFit").lte(filter.getMaxTimeFit()));
    }

    // Forecast filters
    if (filter.getMinEstimatedImpressions() != null) {
      criteriaList.add(
          Criteria.where("forecast.estimatedImpressions").gte(filter.getMinEstimatedImpressions()));
    }
    if (filter.getMaxEstimatedImpressions() != null) {
      criteriaList.add(
          Criteria.where("forecast.estimatedImpressions").lte(filter.getMaxEstimatedImpressions()));
    }
    if (filter.getMinEstimatedReach() != null) {
      criteriaList.add(
          Criteria.where("forecast.estimatedReach").gte(filter.getMinEstimatedReach()));
    }
    if (filter.getMaxEstimatedReach() != null) {
      criteriaList.add(
          Criteria.where("forecast.estimatedReach").lte(filter.getMaxEstimatedReach()));
    }
    if (filter.getMinEstimatedFrequency() != null) {
      criteriaList.add(
          Criteria.where("forecast.estimatedFrequency").gte(filter.getMinEstimatedFrequency()));
    }
    if (filter.getMaxEstimatedFrequency() != null) {
      criteriaList.add(
          Criteria.where("forecast.estimatedFrequency").lte(filter.getMaxEstimatedFrequency()));
    }

    // Cost filters
    if (filter.getMinEstimatedCost() != null) {
      criteriaList.add(Criteria.where("cost.estimatedCost").gte(filter.getMinEstimatedCost()));
    }
    if (filter.getMaxEstimatedCost() != null) {
      criteriaList.add(Criteria.where("cost.estimatedCost").lte(filter.getMaxEstimatedCost()));
    }
    if (filter.getCurrencies() != null && !filter.getCurrencies().isEmpty()) {
      criteriaList.add(Criteria.where("cost.currency").in(filter.getCurrencies()));
    }

    // Availability filters
    if (filter.getMinAvailabilityPercentage() != null) {
      criteriaList.add(
          Criteria.where("availability.availabilityPercentage")
              .gte(filter.getMinAvailabilityPercentage()));
    }
    if (filter.getMaxAvailabilityPercentage() != null) {
      criteriaList.add(
          Criteria.where("availability.availabilityPercentage")
              .lte(filter.getMaxAvailabilityPercentage()));
    }
    if (filter.getAllAvailable() != null) {
      criteriaList.add(Criteria.where("availability.allAvailable").is(filter.getAllAvailable()));
    }

    // Selection mode filter
    if (filter.getSelectionModes() != null && !filter.getSelectionModes().isEmpty()) {
      criteriaList.add(Criteria.where("selectionMode").in(filter.getSelectionModes()));
    }

    // Booking mode filter
    if (filter.getBookingMode() != null && !filter.getBookingMode().isEmpty()) {
      criteriaList.add(Criteria.where("inventoryDetails.bookingMode").in(filter.getBookingMode()));
    }

    // Programmatic filters (on inventoryDetails.programmaticDealTypes, stored lowercase)
    if (filter.getProgrammaticSupport() != null
        && filter.getProgrammaticSupport() != ProgrammaticSupport.ALL) {
      if (filter.getProgrammaticSupport() == ProgrammaticSupport.YES) {
        criteriaList.add(Criteria.where("inventoryDetails.programmaticDealTypes.0").exists(true));
      } else if (filter.getProgrammaticSupport() == ProgrammaticSupport.NO) {
        criteriaList.add(
            new Criteria()
                .orOperator(
                    Criteria.where("inventoryDetails.programmaticDealTypes").exists(false),
                    Criteria.where("inventoryDetails.programmaticDealTypes").is(null),
                    Criteria.where("inventoryDetails.programmaticDealTypes").size(0)));
      }
    }
    if (filter.getDealTypes() != null && !filter.getDealTypes().isEmpty()) {
      List<String> lowercased =
          filter.getDealTypes().stream().map(dt -> dt.getValue()).collect(Collectors.toList());
      criteriaList.add(Criteria.where("inventoryDetails.programmaticDealTypes").in(lowercased));
    }

    return criteriaList;
  }

  @Override
  public void bulkUpdateSelectionMode(
      String runId, Map<String, SelectionMode> selectionModeByInventoryId) {
    //   if (selectionModeByInventoryId == null || selectionModeByInventoryId.isEmpty()) {
    //     log.debug("Bulk update selectionMode: no updates to perform");
    //     return;
    //   }

    //   long startTime = System.currentTimeMillis();
    //   log.warn(
    //       "[DIAG] Starting bulk update for runId={} with {} updates",
    //       runId,
    //       selectionModeByInventoryId.size());

    //   // Use MongoDB bulk write API for maximum efficiency (Phase 3.2 optimization)
    //   long t1 = System.currentTimeMillis();
    //   BulkOperations bulkOps =
    //       mongoTemplate.bulkOps(
    //           BulkOperations.BulkMode.UNORDERED, // Parallel execution on MongoDB side
    //           RecommendationResult.class);
    //   long t2 = System.currentTimeMillis();
    //   log.warn("[DIAG] Created BulkOperations in {}ms", (t2 - t1));

    //   int updateCount = 0;
    //   LocalDateTime now = LocalDateTime.now();
    //   for (Map.Entry<String, SelectionMode> entry : selectionModeByInventoryId.entrySet()) {
    //     String inventoryId = entry.getKey();
    //     SelectionMode mode = entry.getValue();

    //     // Build query: find by runId AND inventoryId
    //     Query query =
    //         Query.query(Criteria.where("runId").is(runId).and("inventoryId").is(inventoryId));

    //     // Build update: set selectionMode and updatedAt fields
    //     Update update = new Update().set("selectionMode", mode).set("updatedAt", now);

    //     bulkOps.updateOne(query, update);
    //     updateCount++;
    //   }
    //   long t3 = System.currentTimeMillis();
    //   log.warn(
    //       "[DIAG] Built {} bulk operations in {}ms ({}ms per operation)",
    //       updateCount,
    //       (t3 - t2),
    //       (t3 - t2) / (double) updateCount);

    //   // Execute all updates in a single round trip
    //   long t4 = System.currentTimeMillis();
    //   BulkWriteResult result = bulkOps.execute();
    //   long t5 = System.currentTimeMillis();
    //   log.warn(
    //       "[DIAG] MongoDB execute() took {}ms ({}ms per update)",
    //       (t5 - t4),
    //       (t5 - t4) / (double) updateCount);

    //   long duration = System.currentTimeMillis() - startTime;

    //   log.info(
    //       "Phase 3.2: Bulk updated selectionMode for {} results in runId {} - matched={},
    // modified={}, duration={}ms",
    //       updateCount,
    //       runId,
    //       result.getMatchedCount(),
    //       result.getModifiedCount(),
    //       duration);
    // if (selectionModeByInventoryId == null || selectionModeByInventoryId.isEmpty()) {
    //   log.debug("Bulk update selectionMode: no updates to perform");
    //   return;
    // }

    // long startTime = System.currentTimeMillis();
    // int totalMatched = 0;
    // int totalModified = 0;

    // Instant now = Instant.now();
    // int batchSize = 1000;
    // List<Map.Entry<String, SelectionMode>> entries =
    //     new ArrayList<>(selectionModeByInventoryId.entrySet());

    // log.info(
    //     "Starting bulk update selectionMode for runId={}, totalUpdates={}, batchSize={}",
    //     runId,
    //     entries.size(),
    //     batchSize);

    // for (int i = 0; i < entries.size(); i += batchSize) {
    //   int end = Math.min(i + batchSize, entries.size());
    //   List<Map.Entry<String, SelectionMode>> batch = entries.subList(i, end);

    //   BulkOperations bulkOps =
    //       mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, RecommendationResult.class);

    //   for (Map.Entry<String, SelectionMode> entry : batch) {
    //     Query query =
    //         Query.query(Criteria.where("runId").is(runId).and("inventoryId").is(entry.getKey()));
    //     Update update = new Update().set("selectionMode", entry.getValue()).set("updatedAt",
    // now);
    //     bulkOps.updateOne(query, update);
    //   }

    //   long t1 = System.currentTimeMillis();
    //   BulkWriteResult result = bulkOps.execute();
    //   log.info("execute() took {}ms", System.currentTimeMillis() - t1);
    //   totalMatched += result.getMatchedCount();
    //   totalModified += result.getModifiedCount();

    //   log.debug(
    //       "Batch {}/{} executed — matched={}, modified={}",
    //       (i / batchSize) + 1,
    //       (int) Math.ceil((double) entries.size() / batchSize),
    //       result.getMatchedCount(),
    //       result.getModifiedCount());
    // }

    // long duration = System.currentTimeMillis() - startTime;
    // log.info(
    //     "Bulk updated selectionMode for runId={}, totalMatched={}, totalModified={},
    // duration={}ms",
    //     runId,
    //     totalMatched,
    //     totalModified,
    //     duration);
    if (selectionModeByInventoryId == null || selectionModeByInventoryId.isEmpty()) {
      log.debug("Bulk update selectionMode: no updates to perform");
      return;
    }

    long startTime = System.currentTimeMillis();
    Instant now = Instant.now();

    log.info(
        "Starting bulk update selectionMode for runId={}, totalUpdates={}",
        runId,
        selectionModeByInventoryId.size());

    // Filter out null keys and null SelectionMode values before grouping
    Map<SelectionMode, List<String>> inventoryIdsByMode =
        selectionModeByInventoryId.entrySet().stream()
            .filter(e -> e.getKey() != null && e.getValue() != null)
            .collect(
                Collectors.groupingBy(
                    Map.Entry::getValue,
                    Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

    if (inventoryIdsByMode.isEmpty()) {
      log.warn("Bulk update selectionMode: all entries were null for runId={}", runId);
      return;
    }

    int totalMatched = 0;
    int totalModified = 0;

    for (Map.Entry<SelectionMode, List<String>> entry : inventoryIdsByMode.entrySet()) {
      SelectionMode mode = entry.getKey();
      List<String> inventoryIds = entry.getValue();

      long t1 = System.currentTimeMillis();

      Query query =
          Query.query(Criteria.where("runId").is(runId).and("inventoryId").in(inventoryIds));

      Update update = new Update().set("selectionMode", mode).set("updatedAt", now);

      UpdateResult result = mongoTemplate.updateMulti(query, update, RecommendationResult.class);
      totalMatched += result.getMatchedCount();
      totalModified += result.getModifiedCount();

      log.info(
          "Updated selectionMode={} for {} inventories — matched={}, modified={}, took={}ms",
          mode,
          inventoryIds.size(),
          result.getMatchedCount(),
          result.getModifiedCount(),
          System.currentTimeMillis() - t1);
    }

    long duration = System.currentTimeMillis() - startTime;
    log.info(
        "Bulk update selectionMode complete for runId={}, totalMatched={}, totalModified={}, duration={}ms",
        runId,
        totalMatched,
        totalModified,
        duration);
  }
}
