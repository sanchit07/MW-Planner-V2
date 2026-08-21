package com.mw.planner.repository;

import com.mw.planner.domain.Inventory;
import com.mw.planner.dto.CampaignInventoryFilterDTO;
import com.mw.planner.enums.ProgrammaticSupport;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@Slf4j
public class InventoryRepositoryImpl implements InventoryRepositoryCustom {

  private final MongoTemplate mongoTemplate;
  private final CampaignRepository campaignRepository;
  private final AuditorAware<String> auditorProvider;

  @Override
  public Inventory upsertByNaturalKey(Inventory inventory) {
    String externalId = inventory.getExternalId();
    String referenceId = inventory.getReferenceId();

    // Determine the natural key: externalId first (stable external system DB id), else referenceId.
    String keyField;
    String keyValue;
    if (externalId != null) {
      keyField = "externalId";
      keyValue = externalId;
    } else if (referenceId != null) {
      keyField = "referenceId";
      keyValue = referenceId;
    } else {
      // No natural key — cannot dedupe atomically. Fall back to a plain insert/save and warn.
      log.warn(
          "Inventory has neither externalId nor referenceId; cannot upsert by natural key, "
              + "inserting as a new document (potential duplicate). name: {}",
          inventory.getName());
      return mongoTemplate.save(inventory);
    }

    Query query = new Query(Criteria.where(keyField).is(keyValue));

    LocalDateTime now = LocalDateTime.now();
    String auditor = auditorProvider.getCurrentAuditor().orElse(null);

    // $set only non-null fields so a sparse message never wipes existing data; mirrors the
    // partial-update field list that updateExistingInventory() used to apply.
    Update update = new Update();
    setIfNotNull(update, "name", inventory.getName());
    setIfNotNull(update, "classification", inventory.getClassification());
    setIfNotNull(update, "type", inventory.getType());
    setIfNotNull(update, "format", inventory.getFormat());
    setIfNotNull(update, "environment", inventory.getEnvironment());
    setIfNotNull(update, "viewingDistance", inventory.getViewingDistance());
    setIfNotNull(update, "venueType", inventory.getVenueType());
    setIfNotNull(update, "archived", inventory.getArchived());
    setIfNotNull(update, "location", inventory.getLocation());
    setIfNotNull(update, "panels", inventory.getPanels());
    setIfNotNull(update, "mediaOwnerId", inventory.getMediaOwnerId());
    setIfNotNull(update, "mediaOwnerName", inventory.getMediaOwnerName());
    setIfNotNull(update, "thumbnailUrl", inventory.getThumbnailUrl());
    setIfNotNull(update, "operatingTimes", inventory.getOperatingTimes());
    setIfNotNull(update, "sellingTerm", inventory.getSellingTerm());
    setIfNotNull(update, "orientation", inventory.getOrientation());
    setIfNotNull(update, "timeZone", inventory.getTimeZone());
    setIfNotNull(update, "requiresContentApproval", inventory.getRequiresContentApproval());
    setIfNotNull(update, "programmaticDealTypes", inventory.getProgrammaticDealTypes());
    setIfNotNull(update, "creativeFormats", inventory.getCreativeFormats());
    setIfNotNull(update, "prices", inventory.getPrices());
    setIfNotNull(update, "digitalFields", inventory.getDigitalFields());
    setIfNotNull(update, "classicFields", inventory.getClassicFields());
    setIfNotNull(update, "transitFields", inventory.getTransitFields());
    setIfNotNull(update, "contentExclusions", inventory.getContentExclusions());
    setIfNotNull(update, "medias", inventory.getMedias());
    setIfNotNull(update, "tags", inventory.getTags());
    setIfNotNull(update, "referenceId", inventory.getReferenceId());
    setIfNotNull(update, "externalIds", inventory.getExternalIds());
    setIfNotNull(update, "externalId", inventory.getExternalId());

    // Auditing is bypassed by a direct MongoTemplate upsert (@EnableMongoAuditing only fires on
    // repository save/insert), so manage the audit timestamps explicitly.
    update.set("updatedAt", now);
    update.setOnInsert("createdAt", now);
    if (auditor != null) {
      update.set("lastModifiedBy", auditor);
      update.setOnInsert("createdBy", auditor);
    }
    // NOTE: the key field is intentionally NOT $setOnInsert here — it is already $set above (so it
    // is written on both insert and update) and the query equality seeds it into a brand-new doc on
    // insert. Adding it to $setOnInsert too would target the same path in both operators, which
    // MongoDB rejects with "Updating the path '<key>' would create a conflict".

    Inventory result =
        mongoTemplate.findAndModify(
            query,
            update,
            FindAndModifyOptions.options().upsert(true).returnNew(true),
            Inventory.class);

    log.info(
        "Upserted inventory id: {} by {}: {}",
        result != null ? result.getId() : null,
        keyField,
        keyValue);
    return result;
  }

  private void setIfNotNull(Update update, String field, Object value) {
    if (value != null) {
      update.set(field, value);
    }
  }

  @Override
  public Page<Inventory> findInventoriesWithFilters(
      CampaignInventoryFilterDTO filter, Pageable pageable) {

    // Build all filter criteria and operations (including geospatial and exclusion)
    // Exclusion criteria now combined in single $match stage for better performance
    List<AggregationOperation> operations = buildFilterOperations(filter);
    log.info("Inside findInventoriesWithFilter function");

    // Add sorting
    if (pageable.getSort().isSorted()) {
      operations.add(Aggregation.sort(pageable.getSort()));
    }

    // Add pagination only if pageable is paged
    if (pageable.isPaged()) {
      if (pageable.getPageNumber() > 0) {
        operations.add(Aggregation.skip((long) pageable.getPageNumber() * pageable.getPageSize()));
      }
      operations.add(Aggregation.limit(pageable.getPageSize()));
    }

    // Ensure we have at least one operation (match all if empty)
    if (operations.isEmpty()) {
      operations.add(Aggregation.match(new Criteria()));
    }

    // Execute aggregation
    Aggregation aggregation = Aggregation.newAggregation(operations);
    log.info("Executing inventory aggregation with {} operations", operations.size());
    log.info("Aggregation pipeline: {}", aggregation.toString());
    AggregationResults<Inventory> results =
        mongoTemplate.aggregate(aggregation, "inventories", Inventory.class);
    List<Inventory> inventories = results.getMappedResults();

    // Get total count using efficient count method
    long total = countInventoriesWithFilters(filter);

    return PageableExecutionUtils.getPage(inventories, pageable, () -> total);
  }

  @Override
  public List<Inventory> findInventoriesByIdsWithComplianceCheck(
      List<String> inventoryIds, CampaignInventoryFilterDTO filter) {
    if (inventoryIds == null || inventoryIds.isEmpty()) {
      return new ArrayList<>();
    }

    // Build filter operations for compliance check (ID filter first, then other filters)
    List<AggregationOperation> operations = buildComplianceCheckOperations(inventoryIds, filter);

    Aggregation aggregation = Aggregation.newAggregation(operations);
    log.info("Executing inventory aggregation with {} operations", operations.size());
    log.info("Aggregation pipeline: {}", aggregation.toString());
    AggregationResults<Inventory> results =
        mongoTemplate.aggregate(aggregation, "inventories", Inventory.class);
    return results.getMappedResults();
  }

  @Override
  public long countInventoriesWithFilters(CampaignInventoryFilterDTO filter) {
    // Build filter operations (same as query, but no pagination/sorting)
    List<AggregationOperation> countOperations = buildFilterOperations(filter);

    // Ensure we have at least one operation
    if (countOperations.isEmpty()) {
      countOperations.add(Aggregation.match(new Criteria()));
    }

    // Add count operation (MongoDB $count aggregation - very efficient)
    countOperations.add(Aggregation.count().as("total"));

    Aggregation countAggregation = Aggregation.newAggregation(countOperations);
    log.info("Aggregation pipeline for count: {}", countAggregation.toString());
    AggregationResults<org.bson.Document> countResults =
        mongoTemplate.aggregate(countAggregation, "inventories", org.bson.Document.class);

    return countResults.getMappedResults().isEmpty()
        ? 0L
        : countResults.getMappedResults().getFirst().getInteger("total", 0);
  }

  @Override
  public List<Inventory> findInventoriesWithFiltersForBulkOperation(
      CampaignInventoryFilterDTO filter) {
    // Build all filter criteria and operations (including geospatial and exclusion)
    List<AggregationOperation> operations = buildFilterOperations(filter);

    // Only required fields for bulk operations:
    Document projectDoc = new Document();
    projectDoc.append("_id", 1);
    projectDoc.append("type", 1);
    projectDoc.append("mediaOwnerId", 1);
    projectDoc.append("digitalFields", 1);
    projectDoc.append("operatingTimes", 1);
    projectDoc.append("referenceId", 1);
    projectDoc.append("classification", 1);
    projectDoc.append("prices", 1);

    operations.add(context -> new org.bson.Document("$project", projectDoc));

    // Execute aggregation
    Aggregation aggregation = Aggregation.newAggregation(operations);
    AggregationResults<Inventory> results =
        mongoTemplate.aggregate(aggregation, "inventories", Inventory.class);
    return results.getMappedResults();
  }

  /** Build all filter operations including geospatial and regular filters */
  private List<AggregationOperation> buildFilterOperations(CampaignInventoryFilterDTO filter) {
    List<AggregationOperation> operations = new ArrayList<>();

    // Add geospatial filtering FIRST (if present)
    if (filter.getGeofencing() != null) {
      addGeospatialFilteringToAggregation(operations, filter.getGeofencing());
    }

    // Add regular filter criteria
    List<Criteria> baseCriteriaList = buildFilterCriteria(filter);
    if (!baseCriteriaList.isEmpty()) {
      Criteria baseCriteria = new Criteria().andOperator(baseCriteriaList.toArray(new Criteria[0]));
      operations.add(Aggregation.match(baseCriteria));
    }

    return operations;
  }

  /** Build filter operations for compliance check (ID filter first, then other filters) */
  private List<AggregationOperation> buildComplianceCheckOperations(
      List<String> inventoryIds, CampaignInventoryFilterDTO filter) {
    List<AggregationOperation> operations = new ArrayList<>();

    // First filter by the specific inventory IDs
    operations.add(Aggregation.match(Criteria.where("_id").in(inventoryIds)));

    // Add geospatial filtering (if present)
    if (filter.getGeofencing() != null) {
      addGeospatialFilteringToAggregation(operations, filter.getGeofencing());
    }

    // Add regular filter criteria
    List<Criteria> baseCriteriaList = buildFilterCriteria(filter);
    if (!baseCriteriaList.isEmpty()) {
      Criteria baseCriteria = new Criteria().andOperator(baseCriteriaList.toArray(new Criteria[0]));
      operations.add(Aggregation.match(baseCriteria));
    }

    return operations;
  }

  /** Build filter criteria list for compliance checking and base filtering */
  List<Criteria> buildFilterCriteria(CampaignInventoryFilterDTO filter) {
    List<Criteria> criteriaList = new ArrayList<>();

    // Filter by name or referenceId (case-insensitive partial match)
    if (filter.getName() != null && !filter.getName().trim().isEmpty()) {
      String pattern = Pattern.quote(filter.getName());
      criteriaList.add(
          new Criteria()
              .orOperator(
                  Criteria.where("name").regex(pattern, "i"),
                  Criteria.where("referenceId").regex(pattern, "i")));
    }

    // Filter by media owner IDs
    if (filter.getMediaOwnerIds() != null && !filter.getMediaOwnerIds().isEmpty()) {
      criteriaList.add(Criteria.where("mediaOwnerId").in(filter.getMediaOwnerIds()));
    }

    // Filter by countries
    if (filter.getCountries() != null && !filter.getCountries().isEmpty()) {
      criteriaList.add(Criteria.where("location.country").in(filter.getCountries()));
    }

    // Filter by inventory types
    if (filter.getInventoryTypes() != null && !filter.getInventoryTypes().isEmpty()) {
      criteriaList.add(Criteria.where("classification").in(filter.getInventoryTypes()));
    }

    // Filter by types (specific inventory types like Airport, Bus, Transit Station, etc.)
    if (filter.getTypes() != null && !filter.getTypes().isEmpty()) {
      criteriaList.add(Criteria.where("type").in(filter.getTypes()));
    }

    // Filter by modes of operation
    if (filter.getBookingMode() != null && !filter.getBookingMode().isEmpty()) {
      criteriaList.add(Criteria.where("digitalFields.bookingMode").in(filter.getBookingMode()));
    }

    // Filter by states
    if (filter.getStates() != null && !filter.getStates().isEmpty()) {
      criteriaList.add(Criteria.where("location.state").in(filter.getStates()));
    }

    // Filter by cities
    if (filter.getCities() != null && !filter.getCities().isEmpty()) {
      criteriaList.add(Criteria.where("location.city").in(filter.getCities()));
    }

    // Filter by formats
    if (filter.getFormats() != null && !filter.getFormats().isEmpty()) {
      criteriaList.add(Criteria.where("format").in(filter.getFormats()));
    }

    // Filter by venue types — exact match, benefits from index on venueType
    if (filter.getVenueTypes() != null && !filter.getVenueTypes().isEmpty()) {
      criteriaList.add(Criteria.where("venueType").in(filter.getVenueTypes()));
    }

    // Filter by venue type IDs (primary — uses venueTypeIds populated from OpenOOH taxonomyId).
    // Falls through to display-name filter below for inventories not yet refreshed via RabbitMQ.
    if (filter.getVenueTypeIdFilter() != null) {
      boolean inventoryTypesSet =
          filter.getInventoryTypes() != null && !filter.getInventoryTypes().isEmpty();
      boolean includeDigital = !inventoryTypesSet || filter.getInventoryTypes().contains("Digital");
      boolean includeClassic = !inventoryTypesSet || filter.getInventoryTypes().contains("Classic");

      List<Criteria> channelCriteria = new ArrayList<>();

      List<String> digitalIds = filter.getVenueTypeIdFilter().getDigitalOoh();
      if (includeDigital && digitalIds != null && !digitalIds.isEmpty()) {
        channelCriteria.add(
            new Criteria()
                .andOperator(
                    Criteria.where("classification").is("Digital"),
                    Criteria.where("venueTypeIds").in(digitalIds)));
      }

      List<String> classicIds = filter.getVenueTypeIdFilter().getClassicOoh();
      if (includeClassic && classicIds != null && !classicIds.isEmpty()) {
        channelCriteria.add(
            new Criteria()
                .andOperator(
                    Criteria.where("classification").is("Classic"),
                    Criteria.where("venueTypeIds").in(classicIds)));
      }

      if (!channelCriteria.isEmpty()) {
        criteriaList.add(new Criteria().orOperator(channelCriteria.toArray(new Criteria[0])));
      }
    }

    // Filter by venue types split by media channel (classification-aware).
    // Guard: if inventoryTypes is set, only apply the channel whose classification matches —
    // prevents conflict where classification IN ["Digital"] AND classification="Classic" = 0
    // results.
    if (filter.getVenueTypeFilter() != null) {
      boolean inventoryTypesSet =
          filter.getInventoryTypes() != null && !filter.getInventoryTypes().isEmpty();
      boolean includeDigital = !inventoryTypesSet || filter.getInventoryTypes().contains("Digital");
      boolean includeClassic = !inventoryTypesSet || filter.getInventoryTypes().contains("Classic");

      List<Criteria> channelCriteria = new ArrayList<>();

      List<String> digitalOoh = filter.getVenueTypeFilter().getDigitalOoh();
      if (includeDigital && digitalOoh != null && !digitalOoh.isEmpty()) {
        channelCriteria.add(
            new Criteria()
                .andOperator(
                    Criteria.where("classification").is("Digital"),
                    Criteria.where("venueType").in(digitalOoh)));
      }

      List<String> classicOoh = filter.getVenueTypeFilter().getClassicOoh();
      if (includeClassic && classicOoh != null && !classicOoh.isEmpty()) {
        channelCriteria.add(
            new Criteria()
                .andOperator(
                    Criteria.where("classification").is("Classic"),
                    Criteria.where("venueType").in(classicOoh)));
      }

      if (!channelCriteria.isEmpty()) {
        criteriaList.add(new Criteria().orOperator(channelCriteria.toArray(new Criteria[0])));
      }
    }

    // Filter by categories
    if (filter.getEnvironments() != null && !filter.getEnvironments().isEmpty()) {
      // criteriaList.add(Criteria.where("environment").in(filter.getEnvironments()));
      List<String> cleanedEnvironments =
          filter.getEnvironments().stream()
              .filter(
                  Objects
                      ::nonNull) // Safe check to prevent NullPointerException if an element is null
              .map(env -> env.trim().toLowerCase())
              .collect(Collectors.toList());
      criteriaList.add(Criteria.where("environment").in(cleanedEnvironments));
    }

    // Filter by tags
    if (filter.getTags() != null && !filter.getTags().isEmpty()) {
      criteriaList.add(Criteria.where("tags").in(filter.getTags()));
    }

    // Filter by inventory size (top-level size field)
    if (filter.getSizes() != null && !filter.getSizes().isEmpty()) {
      criteriaList.add(Criteria.where("size").in(filter.getSizes()));
    }

    // Programmatic filters (on programmaticDealTypes, stored lowercase)
    if (filter.getProgrammaticSupport() != null
        && filter.getProgrammaticSupport() != ProgrammaticSupport.ALL) {
      if (filter.getProgrammaticSupport() == ProgrammaticSupport.YES) {
        criteriaList.add(Criteria.where("programmaticDealTypes.0").exists(true));
      } else if (filter.getProgrammaticSupport() == ProgrammaticSupport.NO) {
        criteriaList.add(
            new Criteria()
                .orOperator(
                    Criteria.where("programmaticDealTypes").exists(false),
                    Criteria.where("programmaticDealTypes").is(null),
                    Criteria.where("programmaticDealTypes").size(0)));
      }
    }
    if (filter.getDealTypes() != null && !filter.getDealTypes().isEmpty()) {
      List<String> lowercased =
          filter.getDealTypes().stream().map(dt -> dt.getValue()).collect(Collectors.toList());
      criteriaList.add(Criteria.where("programmaticDealTypes").in(lowercased));
    }

    // Filter by exclusion IDs (combined in single $match for performance)
    if (filter.getExcludeInventoryIds() != null && !filter.getExcludeInventoryIds().isEmpty()) {
      // List<ObjectId> excludeIds = s.stream()
      //   .map(ObjectId::new)          // throws on malformed hex — see below
      //   .toList();
      criteriaList.add(Criteria.where("_id").nin(filter.getExcludeInventoryIds()));
    }

    // GoalType-based pricing filters.
    // Uses the precomputed priceTypes array. Two pricing vocabularies coexist in the data:
    // seeded inventory derives "cpm" / "spot" / "monthly", while IMS-imported inventory carries
    // "cps" (cost-per-spot — the spot-equivalent) and "daily" rate cards. Play/spot-driven goals
    // must therefore accept both spot spellings plus time-based rates, or real imported
    // inventory silently vanishes from browse. Classic OOH inventories use monthly/daily
    // pricing — accepted for all goalTypes.
    if (filter.getGoalType() != null) {
      List<String> accepted =
          switch (filter.getGoalType()) {
            case IMPRESSIONS, REACH -> List.of("cpm", "monthly", "daily");
            case ADPLAYS, SOV -> List.of("spot", "cps", "monthly", "daily");
            default -> null; // OTHER, ATTRIBUTION → no pricing filter
          };
      if (accepted != null) {
        criteriaList.add(Criteria.where("priceTypes").in(accepted.toArray()));
      }
    }

    // Filter by campaign duration: inventory's minimum selling term must fit the range.
    // Applied only when BOTH dates are present AND the range is valid (end not before start).
    // Inclusive of both endpoints. The minDays constraint applies ONLY when the inventory
    // actually has a sellingTerm.minDays value; inventories with null/missing minDays are
    // allowed through (no constraint).
    if (filter.getStartDate() != null
        && filter.getEndDate() != null
        && !filter.getEndDate().isBefore(filter.getStartDate())) {
      long days = ChronoUnit.DAYS.between(filter.getStartDate(), filter.getEndDate()) + 1;
      criteriaList.add(
          new Criteria()
              .orOperator(
                  Criteria.where("sellingTerm.minDays").lte(days),
                  Criteria.where("sellingTerm.minDays").exists(false),
                  Criteria.where("sellingTerm.minDays").is(null)));
    }

    // Cinema-specific filters (applied only when non-empty; any-match semantics)
    if (filter.getCinemaGenres() != null && !filter.getCinemaGenres().isEmpty()) {
      criteriaList.add(Criteria.where("cinemaFields.genres").in(filter.getCinemaGenres()));
    }
    if (filter.getCinemaRatings() != null && !filter.getCinemaRatings().isEmpty()) {
      criteriaList.add(Criteria.where("cinemaFields.ratings").in(filter.getCinemaRatings()));
    }
    if (filter.getCinemaOperatorIds() != null && !filter.getCinemaOperatorIds().isEmpty()) {
      criteriaList.add(Criteria.where("cinemaFields.operatorId").in(filter.getCinemaOperatorIds()));
    }

    // Note: Geospatial filtering is handled separately using aggregation pipeline

    return criteriaList;
  }

  /** Add geospatial filtering to aggregation pipeline using AggregationOperation */
  private void addGeospatialFilteringToAggregation(
      List<AggregationOperation> operations, CampaignInventoryFilterDTO.Geofencing geofencing) {

    if (geofencing == null) {
      return;
    }

    // Handle geometry-based geofencing
    if (geofencing.getGeometries() != null && !geofencing.getGeometries().isEmpty()) {
      // Separate inclusion and exclusion geometries for proper logic handling
      List<CampaignInventoryFilterDTO.Geofencing.Geometry> inclusionGeometries =
          geofencing.getGeometries().stream()
              .filter(CampaignInventoryFilterDTO.Geofencing.Geometry::isIncluded)
              .toList();
      List<CampaignInventoryFilterDTO.Geofencing.Geometry> exclusionGeometries =
          geofencing.getGeometries().stream().filter(geometry -> !geometry.isIncluded()).toList();

      List<org.bson.Document> allGeometryConditions = new ArrayList<>();

      // Handle inclusion geometries
      if (!inclusionGeometries.isEmpty()) {
        List<org.bson.Document> inclusionConditions = new ArrayList<>();
        for (CampaignInventoryFilterDTO.Geofencing.Geometry geometry : inclusionGeometries) {
          org.bson.Document geoCondition = createGeospatialCondition(geometry);
          if (geoCondition != null) {
            inclusionConditions.add(geoCondition);
          }
        }

        if (inclusionConditions.size() == 1) {
          allGeometryConditions.add(inclusionConditions.getFirst());
        } else if (inclusionConditions.size() > 1) {
          org.bson.Document orCondition = new org.bson.Document();
          orCondition.append("$or", inclusionConditions);
          allGeometryConditions.add(orCondition);
        }
      }

      // Handle exclusion geometries
      if (!exclusionGeometries.isEmpty()) {
        List<org.bson.Document> exclusionConditions = new ArrayList<>();
        for (CampaignInventoryFilterDTO.Geofencing.Geometry geometry : exclusionGeometries) {
          org.bson.Document geoCondition = createGeospatialCondition(geometry);
          if (geoCondition != null) {
            exclusionConditions.add(geoCondition);
          }
        }

        // For exclusion, use $nor with all exclusion conditions
        if (!exclusionConditions.isEmpty()) {
          org.bson.Document norCondition = new org.bson.Document();
          norCondition.append("$nor", exclusionConditions);
          allGeometryConditions.add(norCondition);
        }
      }

      // Add geometry conditions to aggregation pipeline
      if (allGeometryConditions.size() == 1) {
        operations.add(
            _context -> new org.bson.Document("$match", allGeometryConditions.getFirst()));
      } else if (allGeometryConditions.size() > 1) {
        org.bson.Document andCondition = new org.bson.Document();
        andCondition.append("$and", allGeometryConditions);
        operations.add(_context -> new org.bson.Document("$match", andCondition));
      }
    }

    // Handle location-based geofencing for multiple locations
    if (geofencing.getLocations() != null && !geofencing.getLocations().isEmpty()) {
      // Named locations become inventory search keywords (INCLUSION only), mirroring
      // RecommendationService.buildSearchKeywords(): case-insensitive substring match across
      // name/referenceId/location.address/location.city/location.state.
      List<String> inclusionKeywords =
          geofencing.getLocations().stream()
              .filter(CampaignInventoryFilterDTO.Geofencing.Location::isIncluded)
              .map(CampaignInventoryFilterDTO.Geofencing.Location::getName)
              .filter(name -> name != null && !name.isBlank())
              .toList();
      Criteria keywordCriteria = buildSearchKeywordsCriteria(inclusionKeywords);
      if (keywordCriteria != null) {
        operations.add(Aggregation.match(keywordCriteria));
      }

      // Locations WITHOUT a name fall back to radius ($centerSphere) geofencing, preserving the
      // existing inclusion ($or) / exclusion ($nor) behavior.
      List<CampaignInventoryFilterDTO.Geofencing.Location> validLocations =
          geofencing.getLocations().stream()
              .filter(location -> location.getName() == null || location.getName().isBlank())
              .filter(
                  location ->
                      location.getLat() != null
                          && location.getLng() != null
                          && location.getRadius() != null)
              .toList();

      if (!validLocations.isEmpty()) {
        // Separate inclusion and exclusion locations for proper logic handling
        List<CampaignInventoryFilterDTO.Geofencing.Location> inclusionLocations =
            validLocations.stream()
                .filter(CampaignInventoryFilterDTO.Geofencing.Location::isIncluded)
                .toList();
        List<CampaignInventoryFilterDTO.Geofencing.Location> exclusionLocations =
            validLocations.stream().filter(location -> !location.isIncluded()).toList();

        List<org.bson.Document> allConditions = new ArrayList<>();

        // Handle inclusion locations
        if (!inclusionLocations.isEmpty()) {
          List<org.bson.Document> inclusionConditions = new ArrayList<>();
          for (CampaignInventoryFilterDTO.Geofencing.Location location : inclusionLocations) {
            double radiusInRadians = location.getRadius() / 6371000.0;
            org.bson.Document geoMatch = new org.bson.Document();
            org.bson.Document geoWithin = new org.bson.Document();
            org.bson.Document centerSphere = new org.bson.Document();

            centerSphere.append(
                "$centerSphere",
                java.util.Arrays.asList(
                    java.util.Arrays.asList(location.getLng(), location.getLat()),
                    radiusInRadians));

            geoWithin.append("$geoWithin", centerSphere);
            geoMatch.append("location.locationCoordinates", geoWithin);
            inclusionConditions.add(geoMatch);
          }

          if (inclusionConditions.size() == 1) {
            allConditions.add(inclusionConditions.getFirst());
          } else {
            org.bson.Document orCondition = new org.bson.Document();
            orCondition.append("$or", inclusionConditions);
            allConditions.add(orCondition);
          }
        }

        // Handle exclusion locations
        if (!exclusionLocations.isEmpty()) {
          List<org.bson.Document> exclusionConditions = new ArrayList<>();
          for (CampaignInventoryFilterDTO.Geofencing.Location location : exclusionLocations) {
            double radiusInRadians = location.getRadius() / 6371000.0;
            org.bson.Document geoMatch = new org.bson.Document();
            org.bson.Document geoWithin = new org.bson.Document();
            org.bson.Document centerSphere = new org.bson.Document();

            centerSphere.append(
                "$centerSphere",
                java.util.Arrays.asList(
                    java.util.Arrays.asList(location.getLng(), location.getLat()),
                    radiusInRadians));

            geoWithin.append("$geoWithin", centerSphere);
            geoMatch.append("location.locationCoordinates", geoWithin);
            exclusionConditions.add(geoMatch);
          }

          // For exclusion, use $nor with all exclusion conditions
          org.bson.Document norCondition = new org.bson.Document();
          norCondition.append("$nor", exclusionConditions);
          allConditions.add(norCondition);
        }

        // Combine all conditions with $and
        if (allConditions.size() == 1) {
          operations.add(_context -> new org.bson.Document("$match", allConditions.getFirst()));
        } else if (allConditions.size() > 1) {
          org.bson.Document andCondition = new org.bson.Document();
          andCondition.append("$and", allConditions);
          operations.add(_context -> new org.bson.Document("$match", andCondition));
        }
      }
    }
  }

  /**
   * Build keyword search criteria: case-insensitive substring match, OR across keywords and across
   * name/referenceId/location.address/location.city/location.state. Pattern.quote escapes regex
   * metacharacters so keywords match literally. Returns null when no usable keyword remains so
   * callers can skip the $match stage entirely.
   */
  Criteria buildSearchKeywordsCriteria(List<String> searchKeywords) {
    if (searchKeywords == null || searchKeywords.isEmpty()) {
      return null;
    }
    List<Criteria> orClauses = new ArrayList<>();
    for (String keyword : searchKeywords) {
      if (keyword == null || keyword.isBlank()) {
        continue;
      }
      Pattern regex = Pattern.compile(Pattern.quote(keyword.trim()), Pattern.CASE_INSENSITIVE);
      // orClauses.add(Criteria.where("name").regex(regex));
      // orClauses.add(Criteria.where("referenceId").regex(regex));
      orClauses.add(Criteria.where("location.address").regex(regex));
      orClauses.add(Criteria.where("location.city").regex(regex));
      orClauses.add(Criteria.where("location.state").regex(regex));
    }
    if (orClauses.isEmpty()) {
      return null;
    }
    return new Criteria().orOperator(orClauses.toArray(new Criteria[0]));
  }

  /**
   * Creates a geospatial condition for a given geometry type. Supports Polygon, Circle, and
   * LineString geometries.
   *
   * @param geometry the geometry to create a condition for
   * @return MongoDB document representing the geospatial condition, or null if geometry is invalid
   */
  private org.bson.Document createGeospatialCondition(
      CampaignInventoryFilterDTO.Geofencing.Geometry geometry) {

    if (geometry == null
        || geometry.getCoordinates() == null
        || geometry.getCoordinates().isEmpty()) {
      return null;
    }

    String geometryType = geometry.getType();
    List<List<Double>> coordinates = geometry.getCoordinates();

    switch (geometryType) {
      case "Polygon":
        return createPolygonCondition(coordinates);
      case "Circle":
        return createCircleCondition(coordinates);
      case "LineString":
        return createLineStringCondition(coordinates);
      default:
        // Log unsupported geometry type or handle gracefully
        return null;
    }
  }

  /** Creates a MongoDB condition for Polygon geometry using $geoIntersects. */
  private org.bson.Document createPolygonCondition(List<List<Double>> coordinates) {
    // Create polygon coordinates for MongoDB
    List<List<Double>> polygonCoordinates = new ArrayList<>(coordinates);
    // Close the polygon if not already closed
    if (!polygonCoordinates.isEmpty()
        && !polygonCoordinates.getFirst().equals(polygonCoordinates.getLast())) {
      polygonCoordinates.add(polygonCoordinates.getFirst());
    }

    org.bson.Document geoMatch = new org.bson.Document();
    org.bson.Document geoIntersects = new org.bson.Document();
    org.bson.Document polygonGeometry = new org.bson.Document();
    polygonGeometry.append("type", "Polygon");
    polygonGeometry.append("coordinates", List.of(polygonCoordinates));
    geoIntersects.append("$geoIntersects", new org.bson.Document("$geometry", polygonGeometry));
    geoMatch.append("location.locationCoordinates", geoIntersects);

    return geoMatch;
  }

  /**
   * Creates a MongoDB condition for Circle geometry using $geoWithin with $centerSphere. Expects
   * coordinates in format: [[centerLng, centerLat], [radius]]
   */
  private org.bson.Document createCircleCondition(List<List<Double>> coordinates) {
    if (coordinates.size() < 2) {
      return null;
    }

    // Extract center coordinates and radius
    List<Double> center = coordinates.get(0);
    List<Double> radiusInfo = coordinates.get(1);

    if (center.size() < 2 || radiusInfo.isEmpty()) {
      return null;
    }

    double centerLng = center.get(0);
    double centerLat = center.get(1);
    double radiusInMeters = radiusInfo.get(0);
    double radiusInRadians = radiusInMeters / 6371000.0; // Earth's radius in meters

    org.bson.Document geoMatch = new org.bson.Document();
    org.bson.Document geoWithin = new org.bson.Document();
    org.bson.Document centerSphere = new org.bson.Document();

    centerSphere.append(
        "$centerSphere",
        java.util.Arrays.asList(java.util.Arrays.asList(centerLng, centerLat), radiusInRadians));

    geoWithin.append("$geoWithin", centerSphere);
    geoMatch.append("location.locationCoordinates", geoWithin);

    return geoMatch;
  }

  /**
   * Creates a MongoDB condition for LineString geometry using $geoIntersects. Expects coordinates
   * in format: [[lng1, lat1], [lng2, lat2], ...]
   */
  private org.bson.Document createLineStringCondition(List<List<Double>> coordinates) {
    if (coordinates.size() < 2) {
      return null;
    }

    org.bson.Document geoMatch = new org.bson.Document();
    org.bson.Document geoIntersects = new org.bson.Document();
    org.bson.Document lineStringGeometry = new org.bson.Document();

    lineStringGeometry.append("type", "LineString");
    lineStringGeometry.append("coordinates", coordinates);
    geoIntersects.append("$geoIntersects", new org.bson.Document("$geometry", lineStringGeometry));
    geoMatch.append("location.locationCoordinates", geoIntersects);

    return geoMatch;
  }

  /**
   * Calculates the set of weekdays that a campaign runs on based on startDate and endDate.
   *
   * @param startDate Campaign start date
   * @param endDate Campaign end date
   * @return Set of weekday names (e.g., "MONDAY", "TUESDAY", etc.)
   */
  private Set<String> calculateCampaignWeekdays(LocalDate startDate, LocalDate endDate) {
    Set<String> weekdays = new HashSet<>();
    LocalDate currentDate = startDate;

    while (!currentDate.isAfter(endDate)) {
      DayOfWeek dayOfWeek = currentDate.getDayOfWeek();
      // Convert DayOfWeek enum to uppercase string (MONDAY, TUESDAY, etc.)
      weekdays.add(dayOfWeek.name());
      currentDate = currentDate.plusDays(1);
    }

    return weekdays;
  }

  /**
   * Calculates how many times each weekday appears in the campaign duration.
   *
   * @param startDate Campaign start date
   * @param endDate Campaign end date
   * @return Map of weekday names to their counts (e.g., "MONDAY" -> 7, "TUESDAY" -> 7, etc.)
   */
  private Map<String, Integer> calculateWeekdayCounts(LocalDate startDate, LocalDate endDate) {
    Map<String, Integer> weekdayCounts = new HashMap<>();
    LocalDate currentDate = startDate;

    while (!currentDate.isAfter(endDate)) {
      DayOfWeek dayOfWeek = currentDate.getDayOfWeek();
      String weekdayName = dayOfWeek.name();
      weekdayCounts.put(weekdayName, weekdayCounts.getOrDefault(weekdayName, 0) + 1);
      currentDate = currentDate.plusDays(1);
    }

    return weekdayCounts;
  }

  /**
   * Builds a MongoDB $switch expression to map weekday names to their counts. This is used in the
   * aggregation pipeline to multiply slots by weekday occurrence count.
   *
   * @param weekdayCounts Map of weekday names to their counts
   * @param weekdayNameDoc MongoDB expression that evaluates to weekday name
   * @return MongoDB $switch document
   */
  private org.bson.Document buildWeekdayCountSwitch(
      Map<String, Integer> weekdayCounts, org.bson.Document weekdayNameDoc) {
    java.util.List<org.bson.Document> cases = new java.util.ArrayList<>();

    // Add a case for each weekday
    for (Map.Entry<String, Integer> entry : weekdayCounts.entrySet()) {
      java.util.List<Object> whenArgs = new java.util.ArrayList<>();
      whenArgs.add(weekdayNameDoc);
      whenArgs.add(entry.getKey());
      org.bson.Document whenDoc = new org.bson.Document("$eq", whenArgs);
      org.bson.Document caseDoc = new org.bson.Document("case", whenDoc);
      caseDoc.append("then", entry.getValue());
      cases.add(caseDoc);
    }

    org.bson.Document switchDoc = new org.bson.Document();
    switchDoc.append("branches", cases);
    switchDoc.append("default", 0);

    return new org.bson.Document("$switch", switchDoc);
  }

  /**
   * Builds the MongoDB $in condition for filtering weekHourBookingMatrix entries by campaign
   * duration weekdays. Checks if the weekday (extracted from key like "MONDAY_05") exists in the
   * campaignWeekdays set.
   *
   * @param campaignWeekdays Set of weekday names that the campaign runs on
   * @return MongoDB condition document
   */
  private org.bson.Document buildCampaignWeekdayFilterCondition(Set<String> campaignWeekdays) {
    // Extract weekday from key (e.g., "MONDAY_05" -> "MONDAY")
    java.util.List<Object> splitArgs = new java.util.ArrayList<>();
    splitArgs.add("$$entry.k");
    splitArgs.add("_");
    org.bson.Document splitDoc = new org.bson.Document("$split", splitArgs);

    java.util.List<Object> arrayElemAtArgs = new java.util.ArrayList<>();
    arrayElemAtArgs.add(splitDoc);
    arrayElemAtArgs.add(0);
    org.bson.Document weekdayNameDoc = new org.bson.Document("$arrayElemAt", arrayElemAtArgs);

    // Check if the weekday name is in the campaignWeekdays set
    java.util.List<Object> inArgs = new java.util.ArrayList<>();
    inArgs.add(weekdayNameDoc);
    inArgs.add(new java.util.ArrayList<>(campaignWeekdays)); // Convert Set to List for MongoDB

    return new org.bson.Document("$in", inArgs);
  }

  /**
   * Builds the MongoDB $gt condition for filtering weekHourBookingMatrix entries by selected
   * weekdays. Checks if the weekday (extracted from key like "MONDAY_05") exists in
   * weekdayDistribution with value > 0.
   *
   * @deprecated Use buildCampaignWeekdayFilterCondition instead
   */
  @Deprecated
  private org.bson.Document buildWeekdayFilterGtCondition() {
    // Extract weekday from key (e.g., "MONDAY_05" -> "MONDAY")
    java.util.List<Object> splitArgs = new java.util.ArrayList<>();
    splitArgs.add("$$entry.k");
    splitArgs.add("_");
    org.bson.Document splitDoc = new org.bson.Document("$split", splitArgs);

    java.util.List<Object> arrayElemAtArgs = new java.util.ArrayList<>();
    arrayElemAtArgs.add(splitDoc);
    arrayElemAtArgs.add(0);
    org.bson.Document weekdayNameDoc = new org.bson.Document("$arrayElemAt", arrayElemAtArgs);

    // Convert weekdayDistribution object to array
    java.util.List<Object> weekdayDistIfNullArgs = new java.util.ArrayList<>();
    weekdayDistIfNullArgs.add("$weekdayDistribution");
    weekdayDistIfNullArgs.add(new org.bson.Document());
    org.bson.Document weekdayDistIfNullDoc =
        new org.bson.Document("$ifNull", weekdayDistIfNullArgs);

    java.util.List<Object> objectToArrayArgs = new java.util.ArrayList<>();
    objectToArrayArgs.add(weekdayDistIfNullDoc);
    org.bson.Document objectToArrayDoc = new org.bson.Document("$objectToArray", objectToArrayArgs);

    // Filter to find entry where k matches the weekday name
    java.util.List<Object> eqArgs = new java.util.ArrayList<>();
    eqArgs.add("$$distEntry.k");
    eqArgs.add(weekdayNameDoc);
    org.bson.Document eqDoc = new org.bson.Document("$eq", eqArgs);

    // Build $filter as an object with input, as, and cond fields
    org.bson.Document filterObj = new org.bson.Document();
    filterObj.append("input", objectToArrayDoc);
    filterObj.append("as", "distEntry");
    filterObj.append("cond", eqDoc);
    org.bson.Document filterDoc = new org.bson.Document("$filter", filterObj);

    // Get the value from the filtered entry (should be only one match)
    java.util.List<Object> arrayElemAtValueArgs = new java.util.ArrayList<>();
    arrayElemAtValueArgs.add(filterDoc);
    arrayElemAtValueArgs.add(0);
    org.bson.Document matchedEntryDoc = new org.bson.Document("$arrayElemAt", arrayElemAtValueArgs);

    // Extract the 'v' (value) field from the matched entry
    // Use $getField with literal "v" field name
    org.bson.Document getFieldInputDoc = new org.bson.Document();
    getFieldInputDoc.append("field", "v");
    getFieldInputDoc.append("input", matchedEntryDoc);
    org.bson.Document getVFieldDoc = new org.bson.Document("$getField", getFieldInputDoc);

    // Handle null case and check if value > 0
    java.util.List<Object> ifNullArgs = new java.util.ArrayList<>();
    ifNullArgs.add(getVFieldDoc);
    ifNullArgs.add(0.0);
    org.bson.Document ifNullDoc = new org.bson.Document("$ifNull", ifNullArgs);

    java.util.List<Object> gtArgs = new java.util.ArrayList<>();
    gtArgs.add(ifNullDoc);
    gtArgs.add(0.0);

    return new org.bson.Document("$gt", gtArgs);
  }

  @Override
  @Cacheable(value = "inventoryCountsByCountry", unless = "#result.isEmpty()")
  public Map<String, Long> getInventoryCountsByCountry() {
    // Match documents where location.country exists and is not null
    return aggregateInventoryCountsByCountry(
        Criteria.where("location.country").exists(true).ne(null));
  }

  @Override
  public Map<String, Long> getInventoryCountsByCountry(Collection<String> countryNames) {
    // $in on concrete values seeks the location.country index instead of scanning every key,
    // so this stays fast no matter how large the inventories collection grows
    return aggregateInventoryCountsByCountry(Criteria.where("location.country").in(countryNames));
  }

  private Map<String, Long> aggregateInventoryCountsByCountry(Criteria matchCriteria) {
    // Build aggregation pipeline to group by country and count
    // Uses index on location.country for optimal performance
    List<AggregationOperation> operations = new ArrayList<>();

    operations.add(Aggregation.match(matchCriteria));

    // Group by country name and count
    // After grouping, _id contains the country name
    operations.add(Aggregation.group("location.country").count().as("count"));

    // Execute aggregation
    Aggregation aggregation = Aggregation.newAggregation(operations);
    AggregationResults<org.bson.Document> results =
        mongoTemplate.aggregate(aggregation, "inventories", org.bson.Document.class);

    // Convert results to Map
    Map<String, Long> countryCounts = new HashMap<>();
    for (org.bson.Document doc : results.getMappedResults()) {
      // _id contains the country name from $group
      String country = doc.getString("_id");
      // MongoDB $count returns Integer, so we need to handle both Integer and Long
      Object countObj = doc.get("count");
      if (country != null && countObj != null) {
        Long count;
        if (countObj instanceof Integer) {
          count = ((Integer) countObj).longValue();
        } else if (countObj instanceof Long) {
          count = (Long) countObj;
        } else if (countObj instanceof Number) {
          count = ((Number) countObj).longValue();
        } else {
          continue; // Skip invalid count values
        }
        countryCounts.put(country, count);
      }
    }

    return countryCounts;
  }

  @Override
  @Cacheable(value = "inventoryCountsByCountryAndClassification", unless = "#result.isEmpty()")
  public Map<String, Map<String, Long>> getInventoryCountsByCountryAndClassification() {
    return aggregateInventoryCountsByCountryAndClassification(
        Criteria.where("location.country").exists(true).ne(null));
  }

  @Override
  public Map<String, Map<String, Long>> getInventoryCountsByCountryAndClassification(
      Collection<String> countryNames) {
    // $in on concrete values seeks the location.country index instead of scanning every key,
    // so this stays fast no matter how large the inventories collection grows
    return aggregateInventoryCountsByCountryAndClassification(
        Criteria.where("location.country").in(countryNames));
  }

  private Map<String, Map<String, Long>> aggregateInventoryCountsByCountryAndClassification(
      Criteria matchCriteria) {
    List<AggregationOperation> operations = new ArrayList<>();

    operations.add(Aggregation.match(matchCriteria));

    operations.add(
        Aggregation.group(
                Fields.fields()
                    .and("country", "location.country")
                    .and("classification", "classification"))
            .count()
            .as("count"));

    Aggregation aggregation = Aggregation.newAggregation(operations);
    log.info("Aggregation: {}", aggregation);
    AggregationResults<org.bson.Document> results =
        mongoTemplate.aggregate(aggregation, "inventories", org.bson.Document.class);

    Map<String, Map<String, Long>> result = new HashMap<>();
    for (org.bson.Document doc : results.getMappedResults()) {
      org.bson.Document id = doc.get("_id", org.bson.Document.class);
      if (id == null) continue;

      String country = id.getString("country");
      String classification = id.getString("classification");
      if (country == null) continue;

      Object countObj = doc.get("count");
      if (countObj == null) continue;

      long count;
      if (countObj instanceof Integer) {
        count = ((Integer) countObj).longValue();
      } else if (countObj instanceof Long) {
        count = (Long) countObj;
      } else if (countObj instanceof Number) {
        count = ((Number) countObj).longValue();
      } else {
        continue;
      }

      String classificationKey = classification != null ? classification : "Unknown";
      result.computeIfAbsent(country, k -> new HashMap<>()).put(classificationKey, count);
    }

    return result;
  }

  /**
   * Normalizes the groupByField to handle nested fields and field name mappings. Maps fields like
   * "country", "state", "city" to their nested paths in location.
   *
   * @param groupByField the field to normalize
   * @return the normalized field path
   */
  private String normalizeGroupByField(String groupByField) {
    if (groupByField == null || groupByField.isEmpty()) {
      return groupByField;
    }

    // Map common fields to their nested paths
    switch (groupByField) {
      case "country":
        return "location.country";
      case "state":
        return "location.state";
      case "city":
        return "location.city";
      case "size":
        return "size";
      case "type":
        return "type";
      case "venueType":
        return "venueType";
      default:
        return "mediaOwnerId";
    }
  }
}
