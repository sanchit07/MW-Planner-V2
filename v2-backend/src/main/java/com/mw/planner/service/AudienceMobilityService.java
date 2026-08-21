package com.mw.planner.service;

import com.mw.planner.domain.AudienceMobility;
import com.mw.planner.dto.mobility.MobilityHeatmapResponseDTO;
import com.mw.planner.dto.mobility.MobilityPointDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

/**
 * Read side of the audience-mobility store. Aggregates server-side (group by geo cell) so the
 * frontend receives a bounded, render-ready point set regardless of how granular the underlying
 * feed is.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudienceMobilityService {

  public static final Set<String> TIME_BUCKETS = Set.of("MORNING", "AFTERNOON", "EVENING", "NIGHT");

  /** Hard cap on points returned to keep map rendering fast. */
  private static final int MAX_POINTS = 5000;

  private final MongoTemplate mongoTemplate;

  public MobilityHeatmapResponseDTO getHeatmap(
      String countryId,
      String timeBucket,
      Double minLat,
      Double maxLat,
      Double minLng,
      Double maxLng) {

    String normalizedCountry = countryId == null ? "" : countryId.trim().toLowerCase(Locale.ROOT);
    String normalizedBucket =
        timeBucket == null ? null : timeBucket.trim().toUpperCase(Locale.ROOT);
    if (normalizedBucket != null
        && !normalizedBucket.isEmpty()
        && !"ALL".equals(normalizedBucket)
        && !TIME_BUCKETS.contains(normalizedBucket)) {
      throw new IllegalArgumentException(
          "Invalid timeBucket '" + timeBucket + "'. Expected one of " + TIME_BUCKETS + " or ALL");
    }
    boolean allBuckets =
        normalizedBucket == null || normalizedBucket.isEmpty() || "ALL".equals(normalizedBucket);

    List<Criteria> criteria = new ArrayList<>();
    criteria.add(Criteria.where("countryId").is(normalizedCountry));
    if (!allBuckets) {
      criteria.add(Criteria.where("timeBucket").is(normalizedBucket));
    }
    if (minLat != null && maxLat != null) {
      criteria.add(Criteria.where("lat").gte(minLat).lte(maxLat));
    }
    if (minLng != null && maxLng != null) {
      criteria.add(Criteria.where("lng").gte(minLng).lte(maxLng));
    }
    Criteria match = new Criteria().andOperator(criteria.toArray(new Criteria[0]));

    // Group by geo cell so an "ALL" query sums the per-bucket weights of the same cell instead of
    // returning 4 stacked points; sort by weight so the cap keeps the strongest signal.
    Aggregation aggregation =
        Aggregation.newAggregation(
            Aggregation.match(match),
            Aggregation.group("lat", "lng").sum("weight").as("weight"),
            Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "weight"),
            Aggregation.limit(MAX_POINTS),
            Aggregation.project("weight")
                .and("_id.lat")
                .as("lat")
                .and("_id.lng")
                .as("lng")
                .andExclude("_id"));

    AggregationResults<MobilityPointDTO> results =
        mongoTemplate.aggregate(aggregation, AudienceMobility.class, MobilityPointDTO.class);
    List<MobilityPointDTO> points = results.getMappedResults();

    // Re-normalize to 0..1 (summing across buckets can exceed 1).
    double max = points.stream().mapToDouble(MobilityPointDTO::getWeight).max().orElse(0d);
    if (max > 0) {
      points.forEach(p -> p.setWeight(Math.round(p.getWeight() / max * 1000d) / 1000d));
    }

    List<String> availableBuckets =
        mongoTemplate
            .findDistinct(
                org.springframework.data.mongodb.core.query.Query.query(
                    Criteria.where("countryId").is(normalizedCountry)),
                "timeBucket",
                AudienceMobility.class,
                String.class)
            .stream()
            .filter(TIME_BUCKETS::contains)
            .sorted()
            .toList();

    return MobilityHeatmapResponseDTO.builder()
        .countryId(normalizedCountry)
        .timeBucket(allBuckets ? "ALL" : normalizedBucket)
        .availableTimeBuckets(availableBuckets)
        .totalPoints(points.size())
        .points(points)
        .build();
  }
}
