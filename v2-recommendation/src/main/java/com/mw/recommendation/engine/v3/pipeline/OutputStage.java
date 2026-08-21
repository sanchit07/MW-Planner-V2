package com.mw.recommendation.engine.v3.pipeline;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.domain.RecommendationResultV3;
import com.mw.recommendation.engine.v3.domain.RecommendationRunV3;
import com.mw.recommendation.engine.v3.explain.ConfidenceCalculator;
import com.mw.recommendation.engine.v3.explain.WhyGenerator;
import com.mw.recommendation.engine.v3.scoring.V3Score;
import com.mw.recommendation.engine.v3.support.BoundedExecutor;
import com.mw.recommendation.engine.v3.support.GeoMath;
import com.mw.recommendation.engine.v3.variation.VariationV3Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * Stage 7/8: builds and persists result documents. selectionMode is stamped in memory before insert
 * (one write per doc — the v2 optimization; no second update pass) and inserts run as bounded
 * parallel batches. Also produces the NO_MATCHES nearest-alternatives (PRD §6.6/AC-08) and the
 * country-scale city-cluster summary (AC-12).
 */
@Component
@Slf4j
public class OutputStage {

  private final WhyGenerator whyGenerator;
  private final ConfidenceCalculator confidenceCalculator;
  private final VariationV3Service variationService;
  private final MongoTemplate mongoTemplate;
  private final V3Properties props;
  private final BoundedExecutor dbExecutor;

  public OutputStage(
      WhyGenerator whyGenerator,
      ConfidenceCalculator confidenceCalculator,
      VariationV3Service variationService,
      MongoTemplate mongoTemplate,
      V3Properties props,
      @Qualifier("v3DbExecutor") BoundedExecutor dbExecutor) {
    this.whyGenerator = whyGenerator;
    this.confidenceCalculator = confidenceCalculator;
    this.variationService = variationService;
    this.mongoTemplate = mongoTemplate;
    this.props = props;
    this.dbExecutor = dbExecutor;
  }

  public void buildAndPersist(V3RunContext ctx) {
    Set<String> autoSelected = new HashSet<>(ctx.getAutoSelectedInventoryIds());
    List<RecommendationResultV3> results = new ArrayList<>(ctx.getScored().size());
    for (ScoredInventoryV3 item : ctx.getScored()) {
      results.add(buildResult(ctx, item, autoSelected));
    }
    ctx.setResults(results);

    if (results.isEmpty()) {
      return;
    }
    int batchSize = props.getBatch().getInsertBatchSize();
    List<Supplier<Object>> inserts = new ArrayList<>();
    for (int i = 0; i < results.size(); i += batchSize) {
      List<RecommendationResultV3> batch =
          results.subList(i, Math.min(i + batchSize, results.size()));
      inserts.add(
          () -> {
            mongoTemplate.insert(batch, RecommendationResultV3.class);
            return null;
          });
    }
    dbExecutor.invokeAll(inserts);
    log.info("v3 output run {}: {} results persisted", ctx.getRunId(), results.size());
  }

  private RecommendationResultV3 buildResult(
      V3RunContext ctx, ScoredInventoryV3 item, Set<String> autoSelected) {
    Inventory inventory = item.inventory();
    V3Score score = item.score();
    V3RunContext.MeasureData measure = ctx.measureFor(inventory);

    Map<String, RecommendationResultV3.ScoreAudit> audit = null;
    if (props.getAudit().isEnabled() && !score.getAudit().isEmpty()) {
      audit = new LinkedHashMap<>();
      for (Map.Entry<String, V3Score.AuditEntry> entry : score.getAudit().entrySet()) {
        audit.put(
            entry.getKey(),
            RecommendationResultV3.ScoreAudit.builder()
                .raw(entry.getValue().raw())
                .normalized(entry.getValue().normalized())
                .weight(entry.getValue().weight())
                .weighted(entry.getValue().weighted())
                .build());
      }
    }

    // AC-06: prorate the displayed cost when availability is partial
    RecommendationResultV3.Cost cost = null;
    if (item.cost() != null && item.cost().present()) {
      double availabilityFraction =
          item.availability() != null ? Math.min(1.0, item.availability().rawPct() / 100.0) : 1.0;
      boolean prorated = availabilityFraction < 1.0;
      BigDecimal estimatedCost =
          prorated
              ? item.cost()
                  .cost()
                  .multiply(BigDecimal.valueOf(availabilityFraction))
                  .setScale(2, RoundingMode.HALF_UP)
              : item.cost().cost();
      cost =
          RecommendationResultV3.Cost.builder()
              .estimatedCost(estimatedCost)
              .currency(item.cost().currency())
              .costUnit(item.cost().costUnit())
              .costPerImpression(item.cost().costPerImpression())
              .totalAdPlays(item.cost().adPlays())
              .prorated(prorated)
              .build();
    }

    double[] latLng = GeoMath.latLng(inventory.getLocationCoordinates());

    return RecommendationResultV3.builder()
        .runId(ctx.getRunId())
        .inventoryId(inventory.getInventoryId())
        .referenceId(inventory.getReferenceId())
        .name(inventory.getName())
        .finalScore(round(item.finalScoreWithJitter()))
        .band(variationService.band(item.finalScoreWithJitter()))
        .componentScores(
            RecommendationResultV3.ComponentScores.builder()
                .measureFit(score.getMeasureFit())
                .geoFit(score.getGeoFit())
                .availability(score.getAvailability())
                .budgetFit(score.getBudgetFit())
                .audienceFit(score.getAudienceFit())
                .brandFit(score.getBrandFit())
                .qualityFit(score.getQualityFit())
                .timeFit(score.getTimeFit())
                .build())
        .scoreAudit(audit)
        .why(whyGenerator.generate(item))
        .confidence(confidenceCalculator.calculate(item, measure))
        .availability(
            item.availability() == null
                ? null
                : RecommendationResultV3.AvailabilitySummary.builder()
                    .availableDays(item.availability().availableDays())
                    .totalDays(item.availability().totalDays())
                    .availabilityPercentage(item.availability().rawPct())
                    .summary(
                        item.availability().availableDays()
                            + "/"
                            + item.availability().totalDays()
                            + " days available")
                    .allAvailable(item.availability().allAvailable())
                    .build())
        .forecast(
            measure == null
                ? null
                : RecommendationResultV3.Forecast.builder()
                    .estimatedImpressions(measure.impressions())
                    .estimatedReach(measure.reach())
                    .estimatedFrequency(
                        measure.impressions() != null
                                && measure.reach() != null
                                && measure.reach() > 0
                            ? round((double) measure.impressions() / measure.reach())
                            : null)
                    .source(measure.source())
                    .build())
        .cost(cost)
        .selectionMode(autoSelected.contains(inventory.getInventoryId()) ? "AUTO" : null)
        .inventoryDetails(
            RecommendationResultV3.InventoryDetails.builder()
                .classification(inventory.getClassification())
                .type(inventory.getType())
                .format(inventory.getFormat())
                .city(
                    inventory.getLocationHierarchy() != null
                        ? inventory.getLocationHierarchy().getCityName()
                        : null)
                .state(
                    inventory.getLocationHierarchy() != null
                        ? inventory.getLocationHierarchy().getStateName()
                        : null)
                .address(inventory.getAddress())
                .mediaOwnerName(inventory.getMediaOwnerName())
                .venueTypes(inventory.getVenueTypes())
                .latitude(latLng != null ? latLng[0] : null)
                .longitude(latLng != null ? latLng[1] : null)
                .size(inventory.getSize())
                .inventoryCluster(inventory.getInventoryCluster())
                .build())
        .build();
  }

  /** AC-12: city-cluster summary for country-scale runs (no city/geofence targeting). */
  public List<RecommendationRunV3.CityCluster> cityClusters(V3RunContext ctx) {
    var geo = ctx.getRequest().getGeographyTargeting();
    boolean countryScale =
        geo == null
            || ((geo.getCities() == null || geo.getCities().isEmpty())
                && (geo.getGeofences() == null || geo.getGeofences().isEmpty())
                && (geo.getPois() == null || geo.getPois().isEmpty()));
    if (!countryScale || ctx.getResults().size() < props.getOutput().getCityClusterThreshold()) {
      return List.of();
    }
    Map<String, List<RecommendationResultV3>> byCity = new HashMap<>();
    for (RecommendationResultV3 result : ctx.getResults()) {
      String city =
          result.getInventoryDetails() != null && result.getInventoryDetails().getCity() != null
              ? result.getInventoryDetails().getCity()
              : "Unknown";
      byCity.computeIfAbsent(city, k -> new ArrayList<>()).add(result);
    }
    ctx.getWarnings()
        .warn("Country-scale run: results grouped into city clusters; zoom in for site-level view");
    return byCity.entrySet().stream()
        .map(
            entry ->
                RecommendationRunV3.CityCluster.builder()
                    .city(entry.getKey())
                    .inventoryCount(entry.getValue().size())
                    .averageScore(
                        round(
                            entry.getValue().stream()
                                .mapToDouble(RecommendationResultV3::getFinalScore)
                                .average()
                                .orElse(0)))
                    .topScore(
                        round(
                            entry.getValue().stream()
                                .mapToDouble(RecommendationResultV3::getFinalScore)
                                .max()
                                .orElse(0)))
                    .build())
        .sorted(Comparator.comparing(RecommendationRunV3.CityCluster::getInventoryCount).reversed())
        .toList();
  }

  /** AC-08 / §6.6: nearest alternatives outside the (too tight) geofence on zero matches. */
  public List<RecommendationRunV3.Alternative> nearestAlternatives(V3RunContext ctx) {
    double[] anchor = geoAnchor(ctx);
    Query query =
        new Query(
                Criteria.where("locationHierarchy.countryName")
                    .is(ctx.getRequest().getCountry())
                    .orOperator(
                        Criteria.where("archived").exists(false),
                        Criteria.where("archived").is(false),
                        Criteria.where("archived").is(null)))
            .limit(200);
    List<Inventory> candidates = mongoTemplate.find(query, Inventory.class);
    if (candidates.isEmpty()) {
      return List.of();
    }

    record Scored(Inventory inventory, double distanceKm) {}
    List<Scored> scored = new ArrayList<>();
    for (Inventory inventory : candidates) {
      double[] latLng = GeoMath.latLng(inventory.getLocationCoordinates());
      double distanceKm =
          anchor != null && latLng != null
              ? GeoMath.distanceMeters(anchor[0], anchor[1], latLng[0], latLng[1]) / 1000.0
              : Double.MAX_VALUE;
      scored.add(new Scored(inventory, distanceKm));
    }
    scored.sort(Comparator.comparingDouble(Scored::distanceKm));

    List<RecommendationRunV3.Alternative> alternatives = new ArrayList<>();
    for (Scored item : scored) {
      if (alternatives.size() >= props.getOutput().getNoMatchAlternatives()) {
        break;
      }
      alternatives.add(
          RecommendationRunV3.Alternative.builder()
              .inventoryId(item.inventory().getInventoryId())
              .referenceId(item.inventory().getReferenceId())
              .name(item.inventory().getName())
              .city(
                  item.inventory().getLocationHierarchy() != null
                      ? item.inventory().getLocationHierarchy().getCityName()
                      : null)
              .distanceKm(
                  item.distanceKm() == Double.MAX_VALUE
                      ? null
                      : Math.round(item.distanceKm() * 10.0) / 10.0)
              .build());
    }
    if (!alternatives.isEmpty()) {
      ctx.getWarnings()
          .warn(
              "No inventory matched the targeting; nearest alternatives suggested — expand the"
                  + " radius or broaden dates");
    }
    return alternatives;
  }

  private static double[] geoAnchor(V3RunContext ctx) {
    var geo = ctx.getRequest().getGeographyTargeting();
    if (geo == null) {
      return null;
    }
    if (geo.getGeofences() != null) {
      for (var fence : geo.getGeofences()) {
        if (fence.getCenterLat() != null && fence.getCenterLng() != null) {
          return new double[] {fence.getCenterLat(), fence.getCenterLng()};
        }
        if (fence.getCoordinates() != null && !fence.getCoordinates().isEmpty()) {
          var first = fence.getCoordinates().get(0);
          if (first.size() >= 2) {
            return new double[] {first.get(1), first.get(0)};
          }
        }
      }
    }
    if (geo.getPois() != null) {
      for (var poi : geo.getPois()) {
        if (poi.getLat() != null && poi.getLng() != null) {
          return new double[] {poi.getLat(), poi.getLng()};
        }
      }
    }
    return null;
  }

  private static Double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
