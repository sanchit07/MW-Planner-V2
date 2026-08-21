package com.mw.recommendation.engine.repository;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.BrowseInventoryRequestDTO;
import com.mw.recommendation.engine.dto.InventoryAttributeFilters;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/** Custom repository implementation for Inventory with geospatial filtering */
@Slf4j
@Repository
@RequiredArgsConstructor
public class InventoryRepositoryImpl implements InventoryRepositoryCustom {

  private static final double GEOFENCE_RADIUS_METERS = 50000.0; // 50KM radius for geofences

  private final MongoTemplate mongoTemplate;

  @Override
  public List<Inventory> findActiveInventoriesByCountryWithGeographyTargeting(
      String countryName,
      List<String> excludedInventoryIds,
      RecommendationRequestDTO.GeographyTargeting geographyTargeting,
      Map<String, List<String>> venueTypeIds,
      List<String> mediaOwnerIds,
      List<String> classifications,
      List<String> searchKeywords,
      Long durationDays,
      Long availableLeadDays,
      LocalDate campaignStartDate,
      LocalDate campaignEndDate,
      RecommendationRequestDTO.CampaignGoal goalType,
      List<String> dsps,
      boolean programmaticEnabled,
      List<Integer> durations,
      InventoryAttributeFilters attributeFilters) {

    List<AggregationOperation> operations = new ArrayList<>();

    // Match: Country and active inventories
    log.info(
        "Inside findActiveInventoriesByCountryWithGeographyTargeting with countryName: {}",
        countryName);
    Criteria digitalPricing =
        new Criteria()
            .andOperator(
                Criteria.where("classification").is("Digital"),
                Criteria.where("prices").elemMatch(Criteria.where("cpm").exists(true).gt(0)));
    Criteria nonDigitalPricing =
        new Criteria()
            .andOperator(
                Criteria.where("classification").ne("Digital"),
                Criteria.where("prices").exists(true).not().size(0));
    Criteria matchCriteria = Criteria.where("locationHierarchy.countryName").is(countryName);
    if (containsOnlyClassic(classifications)) {
      matchCriteria.and("classification").is("Classic");
      matchCriteria
          .and("prices")
          .exists(true)
          .not()
          .size(0); // Classic inventories must have prices
      matchCriteria.andOperator(
          new Criteria()
              .orOperator(
                  Criteria.where("archived").exists(false),
                  Criteria.where("archived").is(false),
                  Criteria.where("archived").is(null)));
    } else {
      // matchCriteria.andOperator(
      //     new Criteria()
      //         .orOperator(
      //             Criteria.where("archived").exists(false),
      //             Criteria.where("archived").is(false),
      //             Criteria.where("archived").is(null)),
      //     new Criteria().orOperator(digitalPricing, nonDigitalPricing));
      matchCriteria.andOperator(
          new Criteria()
              .orOperator(
                  Criteria.where("archived").exists(false),
                  Criteria.where("archived").is(false),
                  Criteria.where("archived").is(null)));
    }

    // Exclude specified inventory IDs
    if (excludedInventoryIds != null && !excludedInventoryIds.isEmpty()) {
      matchCriteria.and("inventoryId").nin(excludedInventoryIds);
      matchCriteria.and("referenceId").nin(excludedInventoryIds);
    }

    operations.add(Aggregation.match(matchCriteria));

    // Venue filter: per-classification filter on venueTypeIds field
    if (venueTypeIds != null && !venueTypeIds.isEmpty()) {
      operations.add(Aggregation.match(buildVenueTypeIdsCriteria(venueTypeIds)));
    }

    // Keyword filter: case-insensitive substring match (skipped entirely when null/empty,
    // keeping the pipeline identical to pre-searchKeywords behavior)
    Criteria searchKeywordsCriteria = buildSearchKeywordsCriteria(searchKeywords);
    if (searchKeywordsCriteria != null) {
      operations.add(Aggregation.match(searchKeywordsCriteria));
    }

    // Filter by media owner IDs (separate $match stage to avoid andOperator chaining)
    if (mediaOwnerIds != null && !mediaOwnerIds.isEmpty()) {
      operations.add(Aggregation.match(Criteria.where("mediaOwnerId").in(mediaOwnerIds)));
    }

    // Filter by DSPs: keep inventories whose dsps array contains at least one requested DSP
    // (global filter, applied regardless of classification). Skipped when null/empty.
    if (dsps != null && !dsps.isEmpty()) {
      operations.add(Aggregation.match(Criteria.where("dsps").in(dsps)));
    }

    // Filter by classifications (e.g. "Digital", "Classic", "Transit"). When programmaticEnabled is
    // true, the Digital slice is additionally constrained to programmatic inventory (non-empty
    // programmaticDealTypes); other classifications are unaffected (see
    // buildClassificationCriteria). The Classic-only case is already handled above, so it is
    // skipped here.
    if (!containsOnlyClassic(classifications)) {
      Criteria classificationCriteria =
          buildClassificationCriteria(classifications, programmaticEnabled);
      if (classificationCriteria != null) {
        operations.add(Aggregation.match(classificationCriteria));
      }
    }

    // Add geospatial filtering if geographyTargeting is provided
    if (geographyTargeting != null) {
      addGeospatialFilteringToAggregation(operations, geographyTargeting);
    }

    // Min-days availability: the campaign window must satisfy the inventory's minimum booking
    // duration. Applied only when durationDays is non-null (both dates present). Lenient by design:
    // null/missing minDays (or missing sellingTerm) always passes; only inventories whose concrete
    // minDays exceeds the campaign duration are excluded (see minDaysAvailabilityCriteria) —
    // identical to the browse path.
    if (durationDays != null) {
      operations.add(Aggregation.match(minDaysAvailabilityCriteria(durationDays)));
    }

    // Lead-time eligibility: the inventory's required lead time must fit within the gap between
    // today and the campaign start. Applied only when availableLeadDays is non-null (startDate
    // present). Lenient by design — null/missing/<=0 leadDays always passes (see
    // leadDaysEligibilityCriteria) — identical to the browse path.
    if (availableLeadDays != null) {
      operations.add(Aggregation.match(leadDaysEligibilityCriteria(availableLeadDays)));
    }

    // Blackout exclusion: skip inventories where any blackout period overlaps with the campaign.
    // Overlap condition: blackout.startDate <= campaignEndDate AND blackout.endDate >=
    // campaignStartDate.
    // Inventories with no blackouts pass through unconditionally.
    if (campaignStartDate != null && campaignEndDate != null) {
      operations.add(
          Aggregation.match(
              new Criteria()
                  .norOperator(
                      Criteria.where("blackouts")
                          .elemMatch(
                              Criteria.where("startDate")
                                  .lte(campaignEndDate)
                                  .and("endDate")
                                  .gte(campaignStartDate)))));
    }

    // Goal-aware pricing filter: when a campaign goal is provided, require the inventory to offer
    // the pricing model the goal needs (cpm for IMPRESSIONS/REACH, spot for SOV/AD_PLAYS), always
    // accepting "monthly" (Classic OOH). CARBON and null add no filter, keeping the pipeline
    // identical to pre-goal behavior.
    if (goalType != null) {
      String primary =
          switch (goalType) {
            case IMPRESSIONS, REACH -> "cpm";
            case SOV, AD_PLAYS -> "spot";
            default -> null; // CARBON → no pricing filter
          };
      if (primary != null) {
        java.util.List<String> acceptedPriceTypes =
            new java.util.ArrayList<>(java.util.List.of(primary, "monthly"));
        // Digital inventory is CPM-priced but still DELIVERS ad plays / share-of-voice. When
        // Digital
        // is explicitly requested, a spot-goal (SOV/AD_PLAYS) must NOT exclude it via the spot-only
        // filter — otherwise the scored run returns zero while /browse (no goal filter) returns the
        // same digital inventory. Accept "cpm" only when Digital is in scope so spot-priced mixed
        // searches keep the original design. (adserver F3 — 2026-07-19)
        boolean digitalRequested =
            classifications != null
                && classifications.stream().anyMatch(c -> "Digital".equalsIgnoreCase(c));
        if (digitalRequested && !acceptedPriceTypes.contains("cpm")) {
          acceptedPriceTypes.add("cpm");
        }
        // Accept when the summary `priceTypes` names an accepted model OR the real `prices` array
        // carries a positive value for the primary model — the two can drift (stale
        // priceTypes:[spot] on inventory whose prices has a real cpm) and reading `prices`
        // (authoritative) stops valid inventory being silently dropped from a goal-scoped run.
        operations.add(
            Aggregation.match(
                new Criteria()
                    .orOperator(
                        Criteria.where("priceTypes").in(acceptedPriceTypes),
                        Criteria.where("prices")
                            .elemMatch(Criteria.where(primary).exists(true).gt(0)))));
      }
    } else {
      // No goal supplied: enforce a valid priceTypes based on the requested classification.
      Set<String> requested =
          classifications == null
              ? Set.of()
              : classifications.stream().map(String::toLowerCase).collect(Collectors.toSet());
      boolean hasClassic = requested.contains("classic");
      boolean hasDigital = requested.contains("digital");

      if (hasDigital && !hasClassic) {
        // Only Digital → require cpm via the `priceTypes` tag OR a real cpm in the `prices` array
        // (the tag can be stale while prices holds a valid cpm).
        operations.add(
            Aggregation.match(
                new Criteria()
                    .orOperator(
                        Criteria.where("priceTypes").in("cpm"),
                        Criteria.where("prices")
                            .elemMatch(Criteria.where("cpm").exists(true).gt(0)))));
      } else {
        // Classic only, both Classic+Digital, or classification unspecified →
        // require a non-empty priceTypes array (excludes missing, null, and []).
        operations.add(Aggregation.match(Criteria.where("priceTypes.0").exists(true)));
      }
    }

    // Selected spot-duration filter: EXACT-match the screen's physical slot length
    // (digitalFields.spotDuration) against the line item's selected duration(s), so the scored run
    // only evaluates screens whose slot equals the creative — a 10s-slot screen is never
    // recommended for a 15s ad (prevents the booking "creativeDuration exceeds spotDuration"
    // reject). Digital-only: Classic panels carry no spotDuration, so a non-null value excludes
    // them. Null/empty adds no stage, keeping the pipeline identical to pre-duration behavior.
    if (durations != null && !durations.isEmpty()) {
      operations.add(Aggregation.match(Criteria.where("digitalFields.spotDuration").in(durations)));
    }

    // Inventory-attribute filters (line item's selection): each EXACT-matches a real field on the
    // inventory, adding a stage only when the value is present. All optional / independent.
    addAttributeFilterStages(operations, attributeFilters);

    // Execute aggregation
    Aggregation aggregation = Aggregation.newAggregation(operations);
    log.info("Aggregation pipeline: {}", aggregation.toString());
    AggregationResults<Inventory> results =
        mongoTemplate.aggregate(aggregation, "inventories", Inventory.class);

    return results.getMappedResults();
  }

  /**
   * Add the line item's inventory-attribute filter stages to the scored-fetch pipeline. Each
   * EXACT-matches a real field on the {@code inventories} collection; a stage is added only when
   * its value is present, so an unset filter never narrows the pool.
   */
  private void addAttributeFilterStages(
      List<AggregationOperation> operations, InventoryAttributeFilters attributeFilters) {
    if (attributeFilters == null || attributeFilters.isEmpty()) {
      return;
    }
    // Inventory Format → top-level `format` (e.g. "ATM Screen").
    if (attributeFilters.getFormats() != null && !attributeFilters.getFormats().isEmpty()) {
      operations.add(Aggregation.match(Criteria.where("format").in(attributeFilters.getFormats())));
    }
    // Creative Type → nested `creativeFormats.creativeType` (video/image/audio).
    if (attributeFilters.getCreativeTypes() != null
        && !attributeFilters.getCreativeTypes().isEmpty()) {
      operations.add(
          Aggregation.match(
              Criteria.where("creativeFormats.creativeType")
                  .in(attributeFilters.getCreativeTypes())));
    }
    // DSP → `dsps`.
    if (attributeFilters.getDsps() != null && !attributeFilters.getDsps().isEmpty()) {
      operations.add(Aggregation.match(Criteria.where("dsps").in(attributeFilters.getDsps())));
    }
    // Inventory cluster → top-level `inventoryCluster` array ($in = contains any).
    if (attributeFilters.getInventoryCluster() != null
        && !attributeFilters.getInventoryCluster().isEmpty()) {
      operations.add(
          Aggregation.match(
              Criteria.where("inventoryCluster").in(attributeFilters.getInventoryCluster())));
    }
    // Programmatic purchase type → `programmaticDealTypes` (stored lowercase).
    if (attributeFilters.getDealTypes() != null && !attributeFilters.getDealTypes().isEmpty()) {
      List<String> lower =
          attributeFilters.getDealTypes().stream()
              .filter(java.util.Objects::nonNull)
              .map(s -> s.trim().toLowerCase())
              .filter(s -> !s.isEmpty())
              .toList();
      if (!lower.isEmpty()) {
        operations.add(Aggregation.match(Criteria.where("programmaticDealTypes").in(lower)));
      }
    }
    // Programmatic support → require (YES) or forbid (NO) any programmatic deal type. ALL/null adds
    // no stage. Broader than dealTypes; matched on the live top-level `programmaticDealTypes`.
    if (attributeFilters.getProgrammaticSupport() != null
        && attributeFilters.getProgrammaticSupport()
            != com.mw.recommendation.engine.enums.ProgrammaticSupport.ALL) {
      if (attributeFilters.getProgrammaticSupport()
          == com.mw.recommendation.engine.enums.ProgrammaticSupport.YES) {
        operations.add(Aggregation.match(Criteria.where("programmaticDealTypes.0").exists(true)));
      } else {
        operations.add(
            Aggregation.match(
                new Criteria()
                    .orOperator(
                        Criteria.where("programmaticDealTypes").exists(false),
                        Criteria.where("programmaticDealTypes").is(null),
                        Criteria.where("programmaticDealTypes").size(0))));
      }
    }

    // Resolution "WxH" → match ANY panel with those pixel dimensions (multiple → $or).
    Criteria resolutionCriteria = buildResolutionCriteria(attributeFilters.getResolutions());
    if (resolutionCriteria != null) {
      operations.add(Aggregation.match(resolutionCriteria));
    }
  }

  /** Build a panels $elemMatch criteria (OR across multiple resolutions); null when none parse. */
  private Criteria buildResolutionCriteria(List<String> resolutions) {
    if (resolutions == null || resolutions.isEmpty()) {
      return null;
    }
    List<Criteria> perResolution = new ArrayList<>();
    for (String res : resolutions) {
      int[] wh = parseResolution(res);
      if (wh == null) {
        continue;
      }
      perResolution.add(
          Criteria.where("panels")
              .elemMatch(Criteria.where("pixelWidth").is(wh[0]).and("pixelHeight").is(wh[1])));
    }
    if (perResolution.isEmpty()) {
      return null;
    }
    return perResolution.size() == 1
        ? perResolution.get(0)
        : new Criteria().orOperator(perResolution.toArray(new Criteria[0]));
  }

  /** "1920x1080" → [1920, 1080]; tolerant of x/X/× and whitespace; null when unparseable. */
  private int[] parseResolution(String res) {
    if (res == null) {
      return null;
    }
    String[] parts = res.trim().toLowerCase().split("[x×]");
    if (parts.length != 2) {
      return null;
    }
    try {
      return new int[] {Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Add geospatial filtering to aggregation pipeline based on geographyTargeting. Similar to
   * mw-planner implementation but without isIncluded flag check.
   *
   * @param operations Aggregation operations list
   * @param geographyTargeting Geography targeting criteria
   */
  private void addGeospatialFilteringToAggregation(
      List<AggregationOperation> operations,
      RecommendationRequestDTO.GeographyTargeting geographyTargeting) {

    List<Criteria> geoCriteriaList = new ArrayList<>();

    // Filter by cities (exact match on locationHierarchy.cityName)
    if (geographyTargeting.getCities() != null && !geographyTargeting.getCities().isEmpty()) {
      geoCriteriaList.add(
          Criteria.where("locationHierarchy.cityName").in(geographyTargeting.getCities()));
    }

    // Filter by states (exact match on locationHierarchy.stateName)
    if (geographyTargeting.getStates() != null && !geographyTargeting.getStates().isEmpty()) {
      geoCriteriaList.add(
          Criteria.where("locationHierarchy.stateName").in(geographyTargeting.getStates()));
    }

    // Filter by geofences (Polygon or Circle)
    if (geographyTargeting.getGeofences() != null && !geographyTargeting.getGeofences().isEmpty()) {
      for (RecommendationRequestDTO.Geofence geofence : geographyTargeting.getGeofences()) {
        if ("Polygon".equals(geofence.getType())
            && geofence.getCoordinates() != null
            && !geofence.getCoordinates().isEmpty()) {
          // Polygon geofence - use $geoWithin
          GeoJsonPolygon polygon = buildPolygonFromCoordinates(geofence.getCoordinates());
          geoCriteriaList.add(Criteria.where("locationCoordinates").within(polygon));
        } else if ("Circle".equals(geofence.getType())
            && geofence.getCenterLng() != null
            && geofence.getCenterLat() != null) {
          // Circle geofence - use $geoWithin with center and 50KM radius
          Point center = new Point(geofence.getCenterLng(), geofence.getCenterLat());
          Circle circle =
              new Circle(center, GEOFENCE_RADIUS_METERS / 1000.0); // Convert meters to km
          geoCriteriaList.add(Criteria.where("locationCoordinates").withinSphere(circle));
        }
      }
    }

    // Combine all geo criteria with $or (inventory matches if it satisfies any criteria)
    if (!geoCriteriaList.isEmpty()) {
      Criteria geoCriteria = new Criteria().orOperator(geoCriteriaList.toArray(new Criteria[0]));
      operations.add(Aggregation.match(geoCriteria));
    }
  }

  /**
   * Build GeoJsonPolygon from coordinates list. Coordinates format: [[lng, lat], [lng, lat], ...]
   *
   * @param coordinates List of coordinate pairs
   * @return GeoJsonPolygon
   */
  private GeoJsonPolygon buildPolygonFromCoordinates(List<List<Double>> coordinates) {
    if (coordinates == null || coordinates.isEmpty()) {
      throw new IllegalArgumentException("Coordinates cannot be null or empty");
    }

    List<Point> points = new ArrayList<>();
    for (List<Double> coord : coordinates) {
      if (coord == null || coord.size() < 2) {
        continue;
      }
      points.add(new Point(coord.get(0), coord.get(1))); // [lng, lat]
    }

    // Ensure polygon is closed (first point = last point)
    if (!points.isEmpty() && !points.get(0).equals(points.get(points.size() - 1))) {
      points.add(new Point(points.get(0).getX(), points.get(0).getY()));
    }

    return new GeoJsonPolygon(points);
  }

  public boolean containsOnlyClassic(List<String> classifications) {
    return classifications != null
        && classifications.size() == 1
        && "classic".equalsIgnoreCase(classifications.getFirst());
  }

  /**
   * Build the classification $match criteria, optionally refining the Digital slice to programmatic
   * inventory.
   *
   * <p>When {@code programmaticEnabled} is false, this reproduces the original behavior: {@code
   * classification IN classifications} (or no criteria when the list is empty).
   *
   * <p>When {@code programmaticEnabled} is true, the Digital slice must additionally have a
   * non-empty {@code programmaticDealTypes} array. Non-Digital classifications pass through
   * unchanged. If Digital is not requested, the flag is a no-op. If classifications is empty/absent
   * (all types), every non-Digital inventory passes and any Digital must be programmatic.
   *
   * @return the criteria to match, or {@code null} when no classification filter should be applied
   */
  Criteria buildClassificationCriteria(List<String> classifications, boolean programmaticEnabled) {
    boolean hasClassifications = classifications != null && !classifications.isEmpty();

    if (!programmaticEnabled) {
      return hasClassifications ? Criteria.where("classification").in(classifications) : null;
    }

    // Digital + programmatic: classification == "Digital" AND non-empty programmaticDealTypes.
    Criteria digitalProgrammatic =
        new Criteria()
            .andOperator(
                Criteria.where("classification").is("Digital"),
                Criteria.where("programmaticDealTypes.0").exists(true));

    if (!hasClassifications) {
      // All types requested: non-Digital passes, Digital must be programmatic.
      return new Criteria()
          .orOperator(Criteria.where("classification").ne("Digital"), digitalProgrammatic);
    }

    boolean hasDigital = classifications.stream().anyMatch(c -> "Digital".equalsIgnoreCase(c));
    if (!hasDigital) {
      // Digital not requested: programmatic flag is a no-op.
      return Criteria.where("classification").in(classifications);
    }

    List<String> nonDigital =
        classifications.stream()
            .filter(c -> !"Digital".equalsIgnoreCase(c))
            .collect(Collectors.toList());
    if (nonDigital.isEmpty()) {
      // Only Digital requested: must be programmatic.
      return digitalProgrammatic;
    }
    // Digital + other classifications: (Digital programmatic) OR (classification IN others).
    return new Criteria()
        .orOperator(digitalProgrammatic, Criteria.where("classification").in(nonDigital));
  }

  @Override
  public Page<Inventory> findActiveInventoriesByCountryPaginated(
      String countryName,
      List<String> excludedInventoryIds,
      RecommendationRequestDTO.GeographyTargeting geographyTargeting,
      Map<String, List<String>> venueTypeIds,
      List<String> mediaOwnerIds,
      List<String> classifications,
      String search,
      BrowseInventoryRequestDTO filterRequest,
      Long availableLeadDays,
      Pageable pageable,
      RecommendationRequestDTO.CampaignGoal goalType) {

    Criteria digitalPricingP =
        new Criteria()
            .andOperator(
                Criteria.where("classification").is("Digital"),
                Criteria.where("prices").elemMatch(Criteria.where("cpm").exists(true).gt(0)));
    Criteria nonDigitalPricingP =
        new Criteria()
            .andOperator(
                Criteria.where("classification").ne("Digital"),
                Criteria.where("prices").exists(true).not().size(0));
    // Collect every independent filter into one list and combine under a single top-level $and.
    // This is required because several filters (archived, pricing, search, geography,
    // programmatic-NO) each produce a top-level $or — and a single Query/Criteria map can hold only
    // ONE $or key (and only ONE $and key). Merging them onto one Criteria via addCriteria/and*
    // throws "you can't add a second 'null' criteria". Wrapping each in its own $and element keeps
    // them independent.
    List<Criteria> andCriteria = new ArrayList<>();

    andCriteria.add(Criteria.where("locationHierarchy.countryName").is(countryName));
    andCriteria.add(
        new Criteria()
            .orOperator(
                Criteria.where("archived").exists(false),
                Criteria.where("archived").is(false),
                Criteria.where("archived").is(null)));
    andCriteria.add(new Criteria().orOperator(digitalPricingP, nonDigitalPricingP));

    if (excludedInventoryIds != null && !excludedInventoryIds.isEmpty()) {
      andCriteria.add(Criteria.where("inventoryId").nin(excludedInventoryIds));
    }

    if (search != null && !search.isBlank()) {
      Pattern regex = Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE);
      andCriteria.add(
          new Criteria()
              .orOperator(
                  Criteria.where("name").regex(regex),
                  Criteria.where("referenceId").regex(regex),
                  Criteria.where("address").regex(regex)));
    }

    if (venueTypeIds != null && !venueTypeIds.isEmpty()) {
      andCriteria.add(buildVenueTypeIdsCriteria(venueTypeIds));
    }
    if (mediaOwnerIds != null && !mediaOwnerIds.isEmpty()) {
      andCriteria.add(Criteria.where("mediaOwnerId").in(mediaOwnerIds));
    }
    if (classifications != null && !classifications.isEmpty()) {
      andCriteria.add(Criteria.where("classification").in(classifications));
    }

    // Apply additional filters from filterRequest (matching planner pattern)
    if (filterRequest != null) {
      collectBrowseFilters(andCriteria, filterRequest, availableLeadDays);
    }

    if (geographyTargeting != null) {
      List<Criteria> geoCriteriaList = new ArrayList<>();
      if (geographyTargeting.getCities() != null && !geographyTargeting.getCities().isEmpty()) {
        geoCriteriaList.add(
            Criteria.where("locationHierarchy.cityName").in(geographyTargeting.getCities()));
      }
      if (geographyTargeting.getStates() != null && !geographyTargeting.getStates().isEmpty()) {
        geoCriteriaList.add(
            Criteria.where("locationHierarchy.stateName").in(geographyTargeting.getStates()));
      }
      if (geographyTargeting.getGeofences() != null
          && !geographyTargeting.getGeofences().isEmpty()) {
        for (RecommendationRequestDTO.Geofence geofence : geographyTargeting.getGeofences()) {
          if ("Polygon".equals(geofence.getType())
              && geofence.getCoordinates() != null
              && !geofence.getCoordinates().isEmpty()) {
            GeoJsonPolygon polygon = buildPolygonFromCoordinates(geofence.getCoordinates());
            geoCriteriaList.add(Criteria.where("locationCoordinates").within(polygon));
          } else if ("Circle".equals(geofence.getType())
              && geofence.getCenterLng() != null
              && geofence.getCenterLat() != null) {
            Point center = new Point(geofence.getCenterLng(), geofence.getCenterLat());
            Circle circle = new Circle(center, GEOFENCE_RADIUS_METERS / 1000.0);
            geoCriteriaList.add(Criteria.where("locationCoordinates").withinSphere(circle));
          }
        }
      }
      if (!geoCriteriaList.isEmpty()) {
        andCriteria.add(new Criteria().orOperator(geoCriteriaList.toArray(new Criteria[0])));
      }
    }

    // Goal-aware pricing filter: when a campaign goal is provided, require the inventory to offer
    // the pricing model the goal needs (cpm for IMPRESSIONS/REACH, spot for SOV/AD_PLAYS), always
    // accepting "monthly" (Classic OOH). CARBON and null add no filter, keeping the query identical
    // to pre-goal behavior.
    if (goalType != null) {
      String primary =
          switch (goalType) {
            case IMPRESSIONS, REACH -> "cpm";
            case SOV, AD_PLAYS -> "spot";
            default -> null; // CARBON → no pricing filter
          };
      if (primary != null) {
        // Accept the model via the `priceTypes` tag OR a real positive value in the `prices` array
        // (the two can drift) — mirrors the scored/aggregation path.
        andCriteria.add(
            new Criteria()
                .orOperator(
                    Criteria.where("priceTypes").in(primary, "monthly"),
                    Criteria.where("prices")
                        .elemMatch(Criteria.where(primary).exists(true).gt(0))));
      }
    }

    // Combine every collected filter under a single fresh top-level $and so no two filters ever
    // share a $or/$and key on the same Criteria.
    Criteria combined = new Criteria().andOperator(andCriteria.toArray(new Criteria[0]));
    Query query = new Query(combined);

    long total = mongoTemplate.count(query, Inventory.class);
    query.with(pageable);
    List<Inventory> inventories = mongoTemplate.find(query, Inventory.class);

    return new PageImpl<>(inventories, pageable, total);
  }

  /**
   * Apply browse-specific filters to the query. Follows planner's filter pattern.
   *
   * @param query MongoDB query to add criteria to
   * @param filterRequest Browse filter request with filter fields
   */
  Criteria buildVenueTypeIdsCriteria(Map<String, List<String>> venueTypeIds) {
    List<Criteria> orClauses = new ArrayList<>();
    List<String> targetedClassifications = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : venueTypeIds.entrySet()) {
      if (entry.getValue() == null || entry.getValue().isEmpty()) {
        continue; // empty/null = no filter for this classification, pass through
      }
      String classification =
          Character.toUpperCase(entry.getKey().charAt(0)) + entry.getKey().substring(1);
      targetedClassifications.add(classification);
      orClauses.add(
          Criteria.where("classification")
              .is(classification)
              .and("venueTypeIds")
              .in(entry.getValue()));
    }
    // Inventories whose classification is not in the map pass through unfiltered
    orClauses.add(Criteria.where("classification").nin(targetedClassifications));
    return new Criteria().orOperator(orClauses.toArray(new Criteria[0]));
  }

  /**
   * Build keyword search criteria: case-insensitive substring match, OR across keywords and across
   * name/referenceId/address/city/state. Mirrors the browse search regex (Pattern.quote escapes
   * regex metacharacters so keywords are matched literally). Returns null when no usable keyword
   * remains so callers can skip the $match stage entirely.
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
      orClauses.add(Criteria.where("address").regex(regex));
      orClauses.add(Criteria.where("locationHierarchy.cityName").regex(regex));
      orClauses.add(Criteria.where("locationHierarchy.stateName").regex(regex));
    }
    if (orClauses.isEmpty()) {
      return null;
    }
    return new Criteria().orOperator(orClauses.toArray(new Criteria[0]));
  }

  /**
   * Collect browse-specific filters into {@code andCriteria} (matching planner pattern). Each
   * filter is appended as an independent criteria so the caller can combine them under a single
   * top-level $and — avoiding $or/$and key collisions on a shared Criteria.
   */
  private void collectBrowseFilters(
      List<Criteria> andCriteria, BrowseInventoryRequestDTO filterRequest, Long availableLeadDays) {
    // Filter by types
    if (filterRequest.getTypes() != null && !filterRequest.getTypes().isEmpty()) {
      andCriteria.add(Criteria.where("type").in(filterRequest.getTypes()));
    }

    // Filter by formats
    if (filterRequest.getFormats() != null && !filterRequest.getFormats().isEmpty()) {
      andCriteria.add(Criteria.where("format").in(filterRequest.getFormats()));
    }

    // Filter by environments
    if (filterRequest.getEnvironments() != null && !filterRequest.getEnvironments().isEmpty()) {
      List<String> lowerCaseEnvironments =
          filterRequest.getEnvironments().stream()
              .map(String::toLowerCase)
              .toList(); // Perfect for Java 17+
      andCriteria.add(Criteria.where("environment").in(lowerCaseEnvironments));
    }

    // Filter by venue types
    if (filterRequest.getVenueTypes() != null && !filterRequest.getVenueTypes().isEmpty()) {
      andCriteria.add(Criteria.where("venueTypes").in(filterRequest.getVenueTypes()));
    }

    // Filter by sizes (check if any panel has matching size)
    if (filterRequest.getSizes() != null && !filterRequest.getSizes().isEmpty()) {
      andCriteria.add(Criteria.where("panels.size").in(filterRequest.getSizes()));
    }

    // `durations` matches the screen's physical slot length (digitalFields.spotDuration), so browse
    // ("View All Inventories") lists only screens whose slot equals the selected duration — a
    // single-element list is an exact match, excluding a 10s-slot screen for a 15s creative
    // (prevents the booking-time "creativeDuration exceeds spotDuration" reject). Digital-only.
    if (filterRequest.getDurations() != null && !filterRequest.getDurations().isEmpty()) {
      andCriteria.add(
          Criteria.where("digitalFields.spotDuration").in(filterRequest.getDurations()));
    }

    // Filter by booking mode (digitalFields nested)
    if (filterRequest.getBookingMode() != null && !filterRequest.getBookingMode().isEmpty()) {
      andCriteria.add(
          Criteria.where("digitalFields.bookingMode").in(filterRequest.getBookingMode()));
    }

    // Filter by media owner names
    if (filterRequest.getMediaOwnerNames() != null
        && !filterRequest.getMediaOwnerNames().isEmpty()) {
      andCriteria.add(Criteria.where("mediaOwnerName").in(filterRequest.getMediaOwnerNames()));
    }

    // Programmatic support filter
    if (filterRequest.getProgrammaticSupport() != null
        && filterRequest.getProgrammaticSupport()
            != com.mw.recommendation.engine.enums.ProgrammaticSupport.ALL) {
      if (filterRequest.getProgrammaticSupport()
          == com.mw.recommendation.engine.enums.ProgrammaticSupport.YES) {
        andCriteria.add(Criteria.where("programmaticDealTypes.0").exists(true));
      } else if (filterRequest.getProgrammaticSupport()
          == com.mw.recommendation.engine.enums.ProgrammaticSupport.NO) {
        andCriteria.add(
            new Criteria()
                .orOperator(
                    Criteria.where("programmaticDealTypes").exists(false),
                    Criteria.where("programmaticDealTypes").is(null),
                    Criteria.where("programmaticDealTypes").size(0)));
      }
    }

    // Deal types filter (convert enum to lowercase strings for DB matching)
    if (filterRequest.getDealTypes() != null && !filterRequest.getDealTypes().isEmpty()) {
      List<String> lowercaseTypes =
          filterRequest.getDealTypes().stream()
              .map(dt -> dt.name().toLowerCase())
              .collect(java.util.stream.Collectors.toList());
      andCriteria.add(Criteria.where("programmaticDealTypes").in(lowercaseTypes));
    }

    // Min-days availability: the campaign window must satisfy the inventory's minimum booking
    // duration. Applied only when both dates are present (getDurationDays() non-null). Lenient by
    // design — null/missing minDays (or missing sellingTerm) always passes; only inventories whose
    // concrete minDays exceeds the campaign duration are excluded.
    Long durationDays = filterRequest.getDurationDays();
    if (durationDays != null) {
      andCriteria.add(minDaysAvailabilityCriteria(durationDays));
    }

    // Lead-time eligibility: the inventory's required lead time must fit within the gap between
    // today and the campaign start. Applied only when availableLeadDays is non-null (startDate
    // present). Lenient by design — null/missing/<=0 leadDays always passes.
    if (availableLeadDays != null) {
      andCriteria.add(leadDaysEligibilityCriteria(availableLeadDays));
    }
  }

  /**
   * Builds the {@code sellingTerm.minDays} availability criteria shared by the browse and
   * recommendation-submission fetch paths. An inventory is eligible when it declares no minimum
   * selling term (minDays missing or null) OR its minimum fits the campaign window ({@code minDays
   * <= durationDays}). Lenient by design — only inventories whose concrete minDays exceeds the
   * campaign duration are excluded — consistent with {@link #leadDaysEligibilityCriteria} and the
   * v3 pipeline's sellingTerm filter.
   *
   * @param durationDays inclusive campaign duration in days (must be non-null)
   * @return criteria matching inventories whose minimum selling term fits the campaign window
   */
  private static Criteria minDaysAvailabilityCriteria(Long durationDays) {
    return new Criteria()
        .orOperator(
            Criteria.where("sellingTerm.minDays").exists(false),
            Criteria.where("sellingTerm.minDays").is(null),
            Criteria.where("sellingTerm.minDays").lte(durationDays));
  }

  /**
   * Builds the {@code sellingTerm.leadDays} lead-time eligibility criteria shared by the browse and
   * recommendation-submission fetch paths. An inventory is eligible when it imposes no usable lead
   * requirement (leadDays missing, null, or {@code <= 0}) OR its required lead time fits within
   * {@code availableLeadDays} (the gap between today and the campaign start). Lenient by design —
   * the opposite of the strict minDays filter: only inventories whose positive leadDays exceeds the
   * available gap are excluded. The explicit {@code <= 0} branch keeps null/zero inventories
   * eligible even when {@code availableLeadDays} is negative (start date in the past).
   *
   * @param availableLeadDays gap in days between today and the campaign start (must be non-null)
   * @return criteria matching inventories whose lead-time requirement is satisfiable
   */
  private static Criteria leadDaysEligibilityCriteria(Long availableLeadDays) {
    return new Criteria()
        .orOperator(
            Criteria.where("sellingTerm.leadDays").exists(false),
            Criteria.where("sellingTerm.leadDays").is(null),
            Criteria.where("sellingTerm.leadDays").lte(0),
            Criteria.where("sellingTerm.leadDays").lte(availableLeadDays));
  }
}
