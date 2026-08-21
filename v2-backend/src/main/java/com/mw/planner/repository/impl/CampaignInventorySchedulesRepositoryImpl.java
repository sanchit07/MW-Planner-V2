package com.mw.planner.repository.impl;

import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.dto.CampaignSchedulePriceFilterDTO;
import com.mw.planner.repository.CampaignInventorySchedulesRepositoryCustom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

/**
 * Custom repository implementation for CampaignInventorySchedules with optimized aggregation
 * queries.
 */
@Repository
@RequiredArgsConstructor
public class CampaignInventorySchedulesRepositoryImpl
    implements CampaignInventorySchedulesRepositoryCustom {

  private final MongoTemplate mongoTemplate;

  @Override
  public Page<CampaignInventorySchedules> findWithPriceFilters(
      String campaignId,
      CampaignSchedulePriceFilterDTO filter,
      Pageable pageable,
      String mediaOwnerId) {

    // Shared prefix: every expensive stage (the inventory lookup above all) runs exactly once,
    // then $facet branches into the paginated data and the total count — instead of the old
    // approach of running two entirely separate aggregations (and thus this same expensive
    // lookup) once for data and once for count.
    List<AggregationOperation> operations = new ArrayList<>();

    // Step 1: Match by campaignId and ensure scheduleIds exists and is not empty
    Criteria baseCriteria =
        Criteria.where("campaignId")
            .is(campaignId)
            .and("scheduleIds")
            .exists(true)
            .ne(new ArrayList<>());

    // Add mediaOwnerId filter if provided
    if (mediaOwnerId != null && !mediaOwnerId.trim().isEmpty()) {
      baseCriteria = baseCriteria.and("mediaOwnerId").is(mediaOwnerId);
    }

    operations.add(Aggregation.match(baseCriteria));

    // Step 2: Lookup Inventory collection, matching by _id directly.
    // inventoryId is stored as a String — an ObjectId hex string for seeded inventory, or the
    // external UUID for IMS-imported inventory (whose docs use that UUID as _id directly).
    // Converting it once to an ObjectId here and matching directly against _id
    // lets Mongo use the _id index for this lookup, instead of the previous computed
    // $toString($_id) comparison, which can't use any index and forces a full scan of the
    // inventories collection per outer document (the dominant cost behind the ~27s load time).
    // $convert's onError/onNull (rather than the raw $toObjectId) means a malformed legacy
    // inventoryId just fails to match instead of erroring the whole aggregation.
    operations.add(context -> buildInventoryLookupStage());

    // Step 3: Unwind inventory array (should be single element)
    operations.add(Aggregation.unwind("inventory", true));

    // Step 4: Apply inventory filters (cities, inventoryTypes)
    // First ensure inventory exists (lookup was successful)
    operations.add(Aggregation.match(Criteria.where("inventory").exists(true).ne(null)));

    if (filter != null) {
      List<Criteria> inventoryCriteria = new ArrayList<>();

      // Filter by name (case-insensitive partial match)
      if (filter.getName() != null && !filter.getName().trim().isEmpty()) {
        inventoryCriteria.add(
            Criteria.where("inventory.name").regex(Pattern.quote(filter.getName()), "i"));
      }

      // Filter by cities
      if (filter.getCities() != null && !filter.getCities().isEmpty()) {
        inventoryCriteria.add(Criteria.where("inventory.location.city").in(filter.getCities()));
      }

      // Filter by inventory types
      if (filter.getInventoryTypes() != null && !filter.getInventoryTypes().isEmpty()) {
        inventoryCriteria.add(
            Criteria.where("inventory.classification").in(filter.getInventoryTypes()));
      }

      // Filter by media owners
      if (filter.getMediaOwnerIds() != null && !filter.getMediaOwnerIds().isEmpty()) {
        inventoryCriteria.add(Criteria.where("mediaOwnerId").in(filter.getMediaOwnerIds()));
      }

      if (!inventoryCriteria.isEmpty()) {
        operations.add(
            Aggregation.match(
                new Criteria().andOperator(inventoryCriteria.toArray(new Criteria[0]))));
      }
    }

    // Step 5: Lookup Schedule collection with price filtering using pipeline
    if (filter != null && (filter.getMinPricing() != null || filter.getMaxPricing() != null)) {
      // Use $lookup with pipeline to filter schedules by price at lookup time
      Document lookupPipeline = buildScheduleLookupPipeline(filter);
      operations.add(
          context ->
              new Document(
                  "$lookup",
                  new Document("from", "schedules")
                      .append("let", new Document("scheduleIds", "$scheduleIds"))
                      .append("pipeline", lookupPipeline.getList("pipeline", Document.class))
                      .append("as", "schedules")));

      // Match documents where at least one schedule matches price criteria
      operations.add(
          Aggregation.match(
              Criteria.where("schedules").exists(true).ne(new ArrayList<>()).not().size(0)));
    } else {
      // Simple lookup without price filtering - use scheduleIds
      LookupOperation scheduleLookup =
          LookupOperation.newLookup()
              .from("schedules")
              .localField("scheduleIds")
              .foreignField("_id")
              .as("schedules");
      operations.add(scheduleLookup);
    }

    // Step 6: Branch into paginated data + total count via $facet, sharing every stage above.
    List<AggregationOperation> dataBranch = new ArrayList<>();
    dataBranch.add(Aggregation.project().andExclude("inventory").andExclude("schedules"));
    if (pageable.isPaged()) {
      dataBranch.add(Aggregation.skip(pageable.getOffset()));
      dataBranch.add(Aggregation.limit(pageable.getPageSize()));
    }

    List<AggregationOperation> countBranch = new ArrayList<>();
    countBranch.add(Aggregation.count().as("total"));

    operations.add(
        Aggregation.facet(dataBranch.toArray(new AggregationOperation[0]))
            .as("data")
            .and(countBranch.toArray(new AggregationOperation[0]))
            .as("totalCount"));

    Aggregation aggregation = Aggregation.newAggregation(operations);
    AggregationResults<FacetResult> results =
        mongoTemplate.aggregate(aggregation, "campaign_inventory_schedules", FacetResult.class);

    FacetResult facetResult = results.getUniqueMappedResult();
    List<CampaignInventorySchedules> schedules =
        facetResult != null && facetResult.getData() != null ? facetResult.getData() : List.of();
    long totalCount =
        facetResult != null
                && facetResult.getTotalCount() != null
                && !facetResult.getTotalCount().isEmpty()
            ? facetResult.getTotalCount().get(0).getTotal()
            : 0;

    return new PageImpl<>(schedules, pageable, totalCount);
  }

  /**
   * Builds the $lookup stage joining campaign_inventory_schedules.inventoryId (a String) to
   * inventories._id (an ObjectId) by converting the string to an ObjectId once per outer document
   * and matching it directly against _id — index-friendly, unlike converting _id to a string.
   */
  private Document buildInventoryLookupStage() {
    Document convertToObjectId =
        new Document(
            "$convert",
            new Document("input", "$inventoryId")
                .append("to", "objectId")
                .append("onError", (Object) null)
                .append("onNull", (Object) null));
    // Two id shapes coexist in `inventories`: seeded docs have a Mongo ObjectId _id (inventoryId
    // holds its hex string), while IMS-imported docs use the external UUID string as _id
    // directly. Match either — the ObjectId conversion alone silently drops every imported
    // inventory (onError → null → no match), which showed up as an empty Price Management page.
    Document lookupDoc =
        new Document("from", "inventories")
            .append(
                "let",
                new Document("inventoryObjId", convertToObjectId)
                    .append("inventoryIdStr", "$inventoryId"))
            .append(
                "pipeline",
                List.of(
                    new Document(
                        "$match",
                        new Document(
                            "$expr",
                            new Document(
                                "$or",
                                Arrays.asList(
                                    new Document("$eq", Arrays.asList("$_id", "$$inventoryObjId")),
                                    new Document(
                                        "$eq", Arrays.asList("$_id", "$$inventoryIdStr"))))))))
            .append("as", "inventory");
    return new Document("$lookup", lookupDoc);
  }

  /**
   * Build $lookup pipeline for schedules with price filtering.
   *
   * @param filter Filter containing price range
   * @return Document with pipeline array
   */
  private Document buildScheduleLookupPipeline(CampaignSchedulePriceFilterDTO filter) {
    List<Document> pipeline = new ArrayList<>();

    // Match schedules that are in the scheduleIds array.
    // scheduleIds are stored as strings in campaign_inventory_schedules; schedules._id may be
    // ObjectId in DB. Support both: match when _id is in array OR when $toString(_id) is in array.
    pipeline.add(
        new Document(
            "$match",
            new Document(
                "$expr",
                new Document(
                    "$or",
                    Arrays.asList(
                        new Document("$in", Arrays.asList("$_id", "$$scheduleIds")),
                        new Document(
                            "$in",
                            Arrays.asList(new Document("$toString", "$_id"), "$$scheduleIds")))))));

    // Filter by price range if provided
    if (filter.getMinPricing() != null || filter.getMaxPricing() != null) {
      // Build a single basePrice condition with $gte and/or $lte (same key cannot appear twice)
      Document basePriceCondition = new Document();
      if (filter.getMinPricing() != null) {
        basePriceCondition.append("$gte", filter.getMinPricing());
      }
      if (filter.getMaxPricing() != null) {
        basePriceCondition.append("$lte", filter.getMaxPricing());
      }
      if (!basePriceCondition.isEmpty()) {
        Document basePriceMatch = new Document("basePrice", basePriceCondition);
        pipeline.add(
            new Document(
                "$match",
                new Document(
                    "$and",
                    Arrays.asList(
                        new Document("basePrice", new Document("$ne", null)), basePriceMatch))));
      }
    }

    return new Document("pipeline", pipeline);
  }

  /** Helper class for count aggregation result */
  @Data
  private static class CountResult {
    private long total;
  }

  /** Helper class mapping the $facet stage's output in {@link #findWithPriceFilters}. */
  @Data
  private static class FacetResult {
    private List<CampaignInventorySchedules> data;
    private List<CountResult> totalCount;
  }

  @Override
  public boolean existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull(
      String campaignId) {
    // Use aggregation to check if any document matches the criteria
    List<AggregationOperation> operations = new ArrayList<>();

    // Match by campaignId and approvedBy is null
    operations.add(
        Aggregation.match(
            Criteria.where("campaignId")
                .is(campaignId)
                .and("approvedBy")
                .isNull()
                .and("history")
                .exists(true)
                .ne(null)));

    // Add project to calculate history size
    operations.add(
        Aggregation.project().and("history").size().as("historySize").and("_id").as("_id"));

    // Match where historySize > 1
    operations.add(Aggregation.match(Criteria.where("historySize").gt(1)));

    // Limit to 1 for efficiency
    operations.add(Aggregation.limit(1));

    Aggregation aggregation = Aggregation.newAggregation(operations);
    AggregationResults<Document> results =
        mongoTemplate.aggregate(aggregation, "campaign_inventory_schedules", Document.class);

    return !results.getMappedResults().isEmpty();
  }

  @Override
  public List<CampaignInventorySchedules> findByCampaignIdWithUnapprovedSchedules(
      String campaignId, List<String> scheduleIds) {
    // Use aggregation to find CampaignInventorySchedules where approvedScheduleIds doesn't
    // contain all scheduleIds
    List<AggregationOperation> operations = new ArrayList<>();

    // Match by campaignId and ensure scheduleIds exists
    Criteria baseCriteria =
        Criteria.where("campaignId")
            .is(campaignId)
            .and("scheduleIds")
            .exists(true)
            .ne(null)
            .ne(new ArrayList<>());

    operations.add(Aggregation.match(baseCriteria));

    // Add project to calculate unapproved scheduleIds
    // Unapproved = scheduleIds that are not in approvedScheduleIds
    operations.add(
        context -> {
          Document projectDoc = new Document();
          projectDoc.append("campaignId", 1);
          projectDoc.append("mediaOwnerId", 1);
          projectDoc.append("inventoryId", 1);
          projectDoc.append("scheduleIds", 1);
          projectDoc.append("approvedScheduleIds", 1);
          projectDoc.append("history", 1);
          projectDoc.append("approvedBy", 1);

          // Calculate unapproved scheduleIds: setDifference(scheduleIds, approvedScheduleIds)
          // If approvedScheduleIds is null, all scheduleIds are unapproved
          Document setDifferenceExpr =
              new Document(
                  "$cond",
                  Arrays.asList(
                      new Document("$eq", Arrays.asList("$approvedScheduleIds", null)),
                      new Document(
                          "$ifNull",
                          Arrays.asList(
                              "$scheduleIds",
                              new ArrayList<>())), // If null, all scheduleIds are unapproved
                      new Document(
                          "$setDifference",
                          Arrays.asList(
                              new Document(
                                  "$ifNull", Arrays.asList("$scheduleIds", new ArrayList<>())),
                              new Document(
                                  "$ifNull",
                                  Arrays.asList("$approvedScheduleIds", new ArrayList<>()))))));

          // Convert unapprovedScheduleIds to strings to handle ObjectId/String comparison
          // Use $cond to handle null/empty arrays
          Document toStringArrayExpr =
              new Document(
                  "$cond",
                  Arrays.asList(
                      new Document(
                          "$or",
                          Arrays.asList(
                              new Document("$eq", Arrays.asList(setDifferenceExpr, null)),
                              new Document(
                                  "$eq", Arrays.asList(setDifferenceExpr, new ArrayList<>())))),
                      new ArrayList<>(), // Return empty array if null or empty
                      new Document(
                          "$map",
                          new Document("input", setDifferenceExpr)
                              .append("as", "id")
                              .append("in", new Document("$toString", "$$id")))));

          projectDoc.append("unapprovedScheduleIds", toStringArrayExpr);
          return new Document("$project", projectDoc);
        });

    // Match where there are unapproved scheduleIds (array is not empty)
    operations.add(
        Aggregation.match(
            Criteria.where("unapprovedScheduleIds")
                .exists(true)
                .ne(null)
                .ne(new ArrayList<>())
                .not()
                .size(0)));

    // If scheduleIds is provided, filter to only include CampaignInventorySchedules that have
    // at least one of the provided scheduleIds in their unapprovedScheduleIds
    // Use $expr with $in to check if any unapprovedScheduleId is in the input scheduleIds
    if (scheduleIds != null && !scheduleIds.isEmpty()) {
      operations.add(
          context -> {
            // Use $anyElementTrue with $map to check if any element in unapprovedScheduleIds
            // is in the input scheduleIds list
            Document anyMatchExpr =
                new Document(
                    "$anyElementTrue",
                    new Document(
                        "$map",
                        new Document(
                                "input",
                                new Document(
                                    "$ifNull",
                                    Arrays.asList("$unapprovedScheduleIds", new ArrayList<>())))
                            .append("as", "id")
                            .append(
                                "in", new Document("$in", Arrays.asList("$$id", scheduleIds)))));
            Document matchDoc = new Document("$expr", anyMatchExpr);
            return new Document("$match", matchDoc);
          });
    }

    // Remove the temporary unapprovedScheduleIds field from result
    // Project all fields except unapprovedScheduleIds
    operations.add(
        context -> {
          Document projectDoc = new Document();
          projectDoc.append("campaignId", 1);
          projectDoc.append("mediaOwnerId", 1);
          projectDoc.append("inventoryId", 1);
          projectDoc.append("scheduleIds", 1);
          projectDoc.append("approvedScheduleIds", 1);
          projectDoc.append("history", 1);
          projectDoc.append("approvedBy", 1);
          projectDoc.append("_id", 1);
          projectDoc.append("createdAt", 1);
          projectDoc.append("updatedAt", 1);
          return new Document("$project", projectDoc);
        });

    Aggregation aggregation = Aggregation.newAggregation(operations);
    AggregationResults<CampaignInventorySchedules> results =
        mongoTemplate.aggregate(
            aggregation, "campaign_inventory_schedules", CampaignInventorySchedules.class);

    return results.getMappedResults();
  }

  @Override
  public List<CampaignInventorySchedules> findByCampaignIdAndMediaOwnerIdWithUnapprovedSchedules(
      String campaignId, String mediaOwnerId, List<String> scheduleIds) {
    // Use aggregation to find CampaignInventorySchedules where approvedScheduleIds doesn't
    // contain all scheduleIds, filtered by mediaOwnerId
    List<AggregationOperation> operations = new ArrayList<>();

    // Match by campaignId, mediaOwnerId and ensure scheduleIds exists
    Criteria baseCriteria =
        Criteria.where("campaignId")
            .is(campaignId)
            .and("mediaOwnerId")
            .is(mediaOwnerId)
            .and("scheduleIds")
            .exists(true)
            .ne(null)
            .ne(new ArrayList<>());

    operations.add(Aggregation.match(baseCriteria));

    // Add project to calculate unapproved scheduleIds
    operations.add(
        context -> {
          Document projectDoc = new Document();
          projectDoc.append("campaignId", 1);
          projectDoc.append("mediaOwnerId", 1);
          projectDoc.append("inventoryId", 1);
          projectDoc.append("scheduleIds", 1);
          projectDoc.append("approvedScheduleIds", 1);
          projectDoc.append("history", 1);
          projectDoc.append("approvedBy", 1);

          // Calculate unapproved scheduleIds: setDifference(scheduleIds, approvedScheduleIds)
          Document setDifferenceExpr =
              new Document(
                  "$cond",
                  Arrays.asList(
                      new Document("$eq", Arrays.asList("$approvedScheduleIds", null)),
                      new Document("$ifNull", Arrays.asList("$scheduleIds", new ArrayList<>())),
                      new Document(
                          "$setDifference",
                          Arrays.asList(
                              new Document(
                                  "$ifNull", Arrays.asList("$scheduleIds", new ArrayList<>())),
                              new Document(
                                  "$ifNull",
                                  Arrays.asList("$approvedScheduleIds", new ArrayList<>()))))));

          // Convert unapprovedScheduleIds to strings to handle ObjectId/String comparison
          // Use $cond to handle null/empty arrays
          Document toStringArrayExpr =
              new Document(
                  "$cond",
                  Arrays.asList(
                      new Document(
                          "$or",
                          Arrays.asList(
                              new Document("$eq", Arrays.asList(setDifferenceExpr, null)),
                              new Document(
                                  "$eq", Arrays.asList(setDifferenceExpr, new ArrayList<>())))),
                      new ArrayList<>(), // Return empty array if null or empty
                      new Document(
                          "$map",
                          new Document("input", setDifferenceExpr)
                              .append("as", "id")
                              .append("in", new Document("$toString", "$$id")))));

          projectDoc.append("unapprovedScheduleIds", toStringArrayExpr);
          return new Document("$project", projectDoc);
        });

    // Match where there are unapproved scheduleIds (array is not empty)
    operations.add(
        Aggregation.match(
            Criteria.where("unapprovedScheduleIds")
                .exists(true)
                .ne(null)
                .ne(new ArrayList<>())
                .not()
                .size(0)));

    // If scheduleIds is provided, filter to only include CampaignInventorySchedules that have
    // at least one of the provided scheduleIds in their unapprovedScheduleIds
    // Use $expr with $in to check if any unapprovedScheduleId is in the input scheduleIds
    if (scheduleIds != null && !scheduleIds.isEmpty()) {
      operations.add(
          context -> {
            // Use $anyElementTrue with $map to check if any element in unapprovedScheduleIds
            // is in the input scheduleIds list
            Document anyMatchExpr =
                new Document(
                    "$anyElementTrue",
                    new Document(
                        "$map",
                        new Document(
                                "input",
                                new Document(
                                    "$ifNull",
                                    Arrays.asList("$unapprovedScheduleIds", new ArrayList<>())))
                            .append("as", "id")
                            .append(
                                "in", new Document("$in", Arrays.asList("$$id", scheduleIds)))));
            Document matchDoc = new Document("$expr", anyMatchExpr);
            return new Document("$match", matchDoc);
          });
    }

    // Remove the temporary unapprovedScheduleIds field from result
    // Project all fields except unapprovedScheduleIds
    operations.add(
        context -> {
          Document projectDoc = new Document();
          projectDoc.append("campaignId", 1);
          projectDoc.append("mediaOwnerId", 1);
          projectDoc.append("inventoryId", 1);
          projectDoc.append("scheduleIds", 1);
          projectDoc.append("approvedScheduleIds", 1);
          projectDoc.append("history", 1);
          projectDoc.append("approvedBy", 1);
          projectDoc.append("_id", 1);
          projectDoc.append("createdAt", 1);
          projectDoc.append("updatedAt", 1);
          return new Document("$project", projectDoc);
        });

    Aggregation aggregation = Aggregation.newAggregation(operations);
    AggregationResults<CampaignInventorySchedules> results =
        mongoTemplate.aggregate(
            aggregation, "campaign_inventory_schedules", CampaignInventorySchedules.class);

    return results.getMappedResults();
  }

  @Override
  public Map<String, Long> countByCampaignIdGroupedByMediaOwnerIdIn(
      String campaignId, Collection<String> mediaOwnerIds) {
    if (mediaOwnerIds == null || mediaOwnerIds.isEmpty()) {
      return Map.of();
    }

    Aggregation aggregation =
        Aggregation.newAggregation(
            Aggregation.match(
                Criteria.where("campaignId").is(campaignId).and("mediaOwnerId").in(mediaOwnerIds)),
            Aggregation.group("mediaOwnerId").count().as("count"));

    AggregationResults<Document> results =
        mongoTemplate.aggregate(aggregation, "campaign_inventory_schedules", Document.class);

    Map<String, Long> countsByMediaOwnerId = new HashMap<>();
    for (Document result : results.getMappedResults()) {
      countsByMediaOwnerId.put(result.getString("_id"), result.getLong("count"));
    }
    return countsByMediaOwnerId;
  }
}
