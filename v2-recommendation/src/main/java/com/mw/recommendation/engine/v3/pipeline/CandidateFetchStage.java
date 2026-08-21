package com.mw.recommendation.engine.v3.pipeline;

import com.mw.brand.lib.dto.BrandResponseDTO;
import com.mw.brand.lib.service.BrandService;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

/**
 * Stage 1: one aggregation query with every eligibility filter pushed down to MongoDB plus a
 * $project limiting documents to the fields later stages actually read. The only in-memory filter
 * is the brand/IAB exclusion partition — done in memory so the run reports a TRUE excluded count
 * (PRD Part G scenario 6: "N inventories excluded due to brand category restrictions").
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CandidateFetchStage {

  private static final String[] PROJECTED_FIELDS = {
    "inventoryId",
    "referenceId",
    "name",
    "address",
    "classification",
    "type",
    "format",
    "venueTypes",
    "venueTypeIds",
    "mediaOwnerId",
    "mediaOwnerName",
    "locationCoordinates",
    "locationHierarchy",
    "panels",
    "qualityMetrics",
    "prices",
    "priceTypes",
    "operatingTimes",
    "sellingTerm",
    "digitalFields",
    "contentExclusions",
    "timeZone"
  };

  private final MongoTemplate mongoTemplate;
  private final BrandService brandService;
  private final V3Properties props;
  private final Clock clock;

  /** Resolved brand category, shared with brandFit scoring via the context. */
  public record FetchResult(List<Inventory> candidates, String brandCategory) {}

  public FetchResult fetch(V3RunContext ctx) {
    RecommendationV3RequestDTO request = ctx.getRequest();

    List<AggregationOperation> operations = new ArrayList<>();
    operations.add(Aggregation.match(baseCriteria(request)));

    geographyCriteria(request).ifPresent(c -> operations.add(Aggregation.match(c)));
    venueTypeCriteria(request).ifPresent(c -> operations.add(Aggregation.match(c)));
    keywordCriteria(request).ifPresent(c -> operations.add(Aggregation.match(c)));
    sellingTermCriteria(request, ctx).ifPresent(c -> operations.add(Aggregation.match(c)));
    blackoutCriteria(request).ifPresent(c -> operations.add(Aggregation.match(c)));
    goalPricingCriteria(request).ifPresent(c -> operations.add(Aggregation.match(c)));

    operations.add(Aggregation.project(PROJECTED_FIELDS));

    List<Inventory> fetched =
        mongoTemplate
            .aggregate(Aggregation.newAggregation(operations), "inventories", Inventory.class)
            .getMappedResults();
    ctx.setFetchedCount(fetched.size());

    // Brand / IAB exclusion — in-memory partition for a TRUE excluded count (PRD §4.2, AC-04)
    String brandCategory = resolveBrandCategory(request.getBrandId());
    List<String> exclusionTerms = exclusionTerms(request, brandCategory);
    List<Inventory> candidates;
    if (exclusionTerms.isEmpty()) {
      candidates = fetched;
    } else {
      candidates = new ArrayList<>(fetched.size());
      int excluded = 0;
      for (Inventory inv : fetched) {
        if (excludedByCategory(inv, exclusionTerms)) {
          excluded++;
        } else {
          candidates.add(inv);
        }
      }
      if (excluded > 0) {
        ctx.getWarnings().exclude("BRAND_CATEGORY_EXCLUSION", excluded);
        ctx.getWarnings()
            .warn(excluded + " inventories excluded due to brand category restrictions");
      }
    }

    log.info(
        "v3 fetch run {}: {} fetched, {} candidates after IAB exclusion",
        ctx.getRunId(),
        fetched.size(),
        candidates.size());
    return new FetchResult(candidates, brandCategory);
  }

  private Criteria baseCriteria(RecommendationV3RequestDTO request) {
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

    Criteria base = Criteria.where("locationHierarchy.countryName").is(request.getCountry());
    List<Criteria> ands = new ArrayList<>();
    ands.add(
        new Criteria()
            .orOperator(
                Criteria.where("archived").exists(false),
                Criteria.where("archived").is(false),
                Criteria.where("archived").is(null)));
    ands.add(new Criteria().orOperator(digitalPricing, nonDigitalPricing));

    if (request.getExcludedInventoryIds() != null && !request.getExcludedInventoryIds().isEmpty()) {
      base.and("inventoryId").nin(request.getExcludedInventoryIds());
      ands.add(Criteria.where("referenceId").nin(request.getExcludedInventoryIds()));
    }
    if (request.getMediaOwnerIds() != null && !request.getMediaOwnerIds().isEmpty()) {
      base.and("mediaOwnerId").in(request.getMediaOwnerIds());
    }
    if (request.getClassifications() != null && !request.getClassifications().isEmpty()) {
      base.and("classification").in(request.getClassifications());
    }
    base.andOperator(ands.toArray(new Criteria[0]));
    return base;
  }

  private java.util.Optional<Criteria> geographyCriteria(RecommendationV3RequestDTO request) {
    RecommendationV3RequestDTO.GeographyTargeting geo = request.getGeographyTargeting();
    if (geo == null) {
      return java.util.Optional.empty();
    }
    List<Criteria> geoCriteria = new ArrayList<>();
    if (geo.getCities() != null && !geo.getCities().isEmpty()) {
      geoCriteria.add(Criteria.where("locationHierarchy.cityName").in(geo.getCities()));
    }
    if (geo.getStates() != null && !geo.getStates().isEmpty()) {
      geoCriteria.add(Criteria.where("locationHierarchy.stateName").in(geo.getStates()));
    }
    if (geo.getGeofences() != null) {
      for (RecommendationV3RequestDTO.Geofence fence : geo.getGeofences()) {
        if ("Polygon".equalsIgnoreCase(fence.getType())
            && fence.getCoordinates() != null
            && !fence.getCoordinates().isEmpty()) {
          geoCriteria.add(
              Criteria.where("locationCoordinates").within(polygon(fence.getCoordinates())));
        } else if ("Circle".equalsIgnoreCase(fence.getType())
            && fence.getCenterLng() != null
            && fence.getCenterLat() != null) {
          double radiusMeters =
              fence.getRadiusMeters() != null && fence.getRadiusMeters() > 0
                  ? fence.getRadiusMeters()
                  : props.getGeo().getDefaultCircleRadiusMeters();
          geoCriteria.add(
              Criteria.where("locationCoordinates")
                  .withinSphere(
                      new Circle(
                          new Point(fence.getCenterLng(), fence.getCenterLat()),
                          new org.springframework.data.geo.Distance(
                              radiusMeters / 1000.0, Metrics.KILOMETERS))));
        }
      }
    }
    // POIs act as R2-radius eligibility circles; precise POI distance shapes geoFit later.
    if (geo.getPois() != null) {
      for (RecommendationV3RequestDTO.Poi poi : geo.getPois()) {
        if (poi.getLat() != null && poi.getLng() != null) {
          geoCriteria.add(
              Criteria.where("locationCoordinates")
                  .withinSphere(
                      new Circle(
                          new Point(poi.getLng(), poi.getLat()),
                          new org.springframework.data.geo.Distance(
                              props.getGeo().getR2Meters() / 1000.0, Metrics.KILOMETERS))));
        }
      }
    }
    return geoCriteria.isEmpty()
        ? java.util.Optional.empty()
        : java.util.Optional.of(new Criteria().orOperator(geoCriteria.toArray(new Criteria[0])));
  }

  private java.util.Optional<Criteria> venueTypeCriteria(RecommendationV3RequestDTO request) {
    Map<String, List<String>> venueTypeIds =
        request.getAudienceTargeting() != null
            ? request.getAudienceTargeting().getVenueTypeIds()
            : null;
    if (venueTypeIds == null || venueTypeIds.isEmpty()) {
      return java.util.Optional.empty();
    }
    List<Criteria> orClauses = new ArrayList<>();
    List<String> targeted = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : venueTypeIds.entrySet()) {
      if (entry.getValue() == null || entry.getValue().isEmpty()) {
        continue;
      }
      String classification =
          Character.toUpperCase(entry.getKey().charAt(0))
              + entry.getKey().substring(1).toLowerCase(Locale.ROOT);
      targeted.add(classification);
      orClauses.add(
          Criteria.where("classification")
              .is(classification)
              .and("venueTypeIds")
              .in(entry.getValue()));
    }
    if (orClauses.isEmpty()) {
      return java.util.Optional.empty();
    }
    orClauses.add(Criteria.where("classification").nin(targeted));
    return java.util.Optional.of(new Criteria().orOperator(orClauses.toArray(new Criteria[0])));
  }

  private java.util.Optional<Criteria> keywordCriteria(RecommendationV3RequestDTO request) {
    if (request.getSearchKeywords() == null || request.getSearchKeywords().isEmpty()) {
      return java.util.Optional.empty();
    }
    List<Criteria> orClauses = new ArrayList<>();
    for (String keyword : request.getSearchKeywords()) {
      if (keyword == null || keyword.isBlank()) {
        continue;
      }
      Pattern regex = Pattern.compile(Pattern.quote(keyword.trim()), Pattern.CASE_INSENSITIVE);
      orClauses.add(Criteria.where("name").regex(regex));
      orClauses.add(Criteria.where("referenceId").regex(regex));
      orClauses.add(Criteria.where("address").regex(regex));
      orClauses.add(Criteria.where("locationHierarchy.cityName").regex(regex));
      orClauses.add(Criteria.where("locationHierarchy.stateName").regex(regex));
    }
    return orClauses.isEmpty()
        ? java.util.Optional.empty()
        : java.util.Optional.of(new Criteria().orOperator(orClauses.toArray(new Criteria[0])));
  }

  /**
   * Min-days: inventories requiring a longer booking than the campaign window are excluded here
   * (PRD Part E offers extend-or-exclude; extension is priced in the schedule stage for selected
   * inventories only). Unlike v1, null/missing minDays passes — absence of a selling term is not
   * grounds for exclusion. Lead-days keeps the lenient v1 semantics.
   */
  private java.util.Optional<Criteria> sellingTermCriteria(
      RecommendationV3RequestDTO request, V3RunContext ctx) {
    List<Criteria> ands = new ArrayList<>();
    long durationDays = ctx.campaignDays();
    ands.add(
        new Criteria()
            .orOperator(
                Criteria.where("sellingTerm.minDays").exists(false),
                Criteria.where("sellingTerm.minDays").is(null),
                Criteria.where("sellingTerm.minDays").lte(durationDays)));

    long availableLeadDays = ChronoUnit.DAYS.between(LocalDate.now(clock), request.getStartDate());
    ands.add(
        new Criteria()
            .orOperator(
                Criteria.where("sellingTerm.leadDays").exists(false),
                Criteria.where("sellingTerm.leadDays").is(null),
                Criteria.where("sellingTerm.leadDays").lte(0),
                Criteria.where("sellingTerm.leadDays").lte(availableLeadDays)));
    return java.util.Optional.of(new Criteria().andOperator(ands.toArray(new Criteria[0])));
  }

  private java.util.Optional<Criteria> blackoutCriteria(RecommendationV3RequestDTO request) {
    return java.util.Optional.of(
        new Criteria()
            .norOperator(
                Criteria.where("blackouts")
                    .elemMatch(
                        Criteria.where("startDate")
                            .lte(request.getEndDate())
                            .and("endDate")
                            .gte(request.getStartDate()))));
  }

  /** Goal-aware pricing (PRD §12.3): cpm for IMPRESSIONS/REACH, spot for SOV/AD_PLAYS. */
  private java.util.Optional<Criteria> goalPricingCriteria(RecommendationV3RequestDTO request) {
    if (request.getGoal() == null) {
      return java.util.Optional.empty();
    }
    String primary =
        switch (request.getGoal()) {
          case IMPRESSIONS, REACH -> "cpm";
          case SOV, AD_PLAYS -> "spot";
          case CARBON -> null;
        };
    return primary == null
        ? java.util.Optional.empty()
        : java.util.Optional.of(Criteria.where("priceTypes").in(primary, "monthly"));
  }

  private String resolveBrandCategory(String brandId) {
    if (brandId == null || brandId.isBlank()) {
      return null;
    }
    try {
      BrandResponseDTO brand = brandService.getBrandById(brandId).orElse(null);
      return brand != null ? brand.getCategory() : null;
    } catch (Exception e) {
      log.warn("v3 brand lookup failed for {}: {}", brandId, e.getMessage());
      return null;
    }
  }

  private static List<String> exclusionTerms(
      RecommendationV3RequestDTO request, String brandCategory) {
    List<String> terms = new ArrayList<>();
    if (brandCategory != null && !brandCategory.isBlank()) {
      terms.add(brandCategory.toLowerCase(Locale.ROOT));
    }
    if (request.getExcludedIabCategories() != null) {
      request.getExcludedIabCategories().stream()
          .filter(c -> c != null && !c.isBlank())
          .map(c -> c.toLowerCase(Locale.ROOT))
          .forEach(terms::add);
    }
    return terms;
  }

  private static boolean excludedByCategory(Inventory inventory, List<String> exclusionTerms) {
    if (inventory.getContentExclusions() == null) {
      return false;
    }
    return inventory.getContentExclusions().stream()
        .anyMatch(
            exclusion -> {
              String name =
                  exclusion.getName() != null ? exclusion.getName().toLowerCase(Locale.ROOT) : null;
              String taxonomyId =
                  exclusion.getTaxonomyId() != null
                      ? exclusion.getTaxonomyId().toLowerCase(Locale.ROOT)
                      : null;
              return exclusionTerms.stream()
                  .anyMatch(
                      term ->
                          (name != null && name.equals(term))
                              || (taxonomyId != null && taxonomyId.equals(term)));
            });
  }

  private static GeoJsonPolygon polygon(List<List<Double>> coordinates) {
    List<Point> points = new ArrayList<>();
    for (List<Double> coord : coordinates) {
      if (coord != null && coord.size() >= 2) {
        points.add(new Point(coord.get(0), coord.get(1)));
      }
    }
    if (!points.isEmpty() && !points.get(0).equals(points.get(points.size() - 1))) {
      points.add(new Point(points.get(0).getX(), points.get(0).getY()));
    }
    return new GeoJsonPolygon(points);
  }
}
