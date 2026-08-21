package com.mw.recommendation.engine.v3.scoring;

import com.mw.recommendation.engine.domain.AudienceData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * audienceFit per PRD §5.7: overlap = Σ min(site%, campaign%) over requested segments and
 * demographics, normalized to 0-100. Campaign shares default to a uniform split across the
 * requested values of each dimension (the request carries names, not percentages). Site shares come
 * from Measure-synced audience data: segment uniqueCount ÷ total uniqueVisitors, and the
 * demographics percentage map. Fallbacks: no targeting → 50 neutral; targeting but no audience data
 * → venue-type heuristics (PRD: airport→travellers, mall→shoppers) instead of v1's harsh 0.
 */
@Component
public class AudienceFitCalculator {

  public Double calculate(
      Inventory inventory,
      AudienceData audience,
      RecommendationV3RequestDTO.AudienceTargeting targeting) {

    boolean hasSegments =
        targeting != null
            && targeting.getAudienceSegments() != null
            && !targeting.getAudienceSegments().isEmpty();
    boolean hasDemographics =
        targeting != null
            && targeting.getDemographics() != null
            && targeting.getDemographics().values().stream()
                .anyMatch(v -> v != null && !v.isEmpty());

    if (!hasSegments && !hasDemographics) {
      return 50.0; // PRD: neutral when no audience specified
    }

    if (audience == null) {
      return venueHeuristicScore(inventory, targeting);
    }

    List<Double> dimensionScores = new ArrayList<>();
    if (hasSegments) {
      dimensionScores.add(
          segmentOverlap(
              audience, targeting.getAudienceSegments(), targeting.getAudienceSegmentShares()));
    }
    if (hasDemographics) {
      for (Map.Entry<String, List<String>> dimension : targeting.getDemographics().entrySet()) {
        if (dimension.getValue() != null && !dimension.getValue().isEmpty()) {
          dimensionScores.add(demographicOverlap(audience, dimension.getValue()));
        }
      }
    }
    if (dimensionScores.isEmpty()) {
      return 50.0;
    }
    return dimensionScores.stream().mapToDouble(Double::doubleValue).average().orElse(50.0);
  }

  /**
   * PRD §5.7: overlap = Σ min(site%, campaign%) × 100. Campaign shares come from the request's
   * audienceSegmentShares when supplied (reproduces the PRD example: 60/40 campaign vs 70/20 site →
   * 0.8 → 80), else a uniform split across the requested segments.
   */
  private static double segmentOverlap(
      AudienceData audience, List<String> requestedSegments, Map<String, Double> shares) {
    Long totalUnique =
        audience.getMonthlySummary() != null
            ? audience.getMonthlySummary().getUniqueVisitors()
            : null;
    if (audience.getAudienceSegments() == null
        || audience.getAudienceSegments().isEmpty()
        || totalUnique == null
        || totalUnique <= 0) {
      return 0.0;
    }
    double uniformShare = 1.0 / requestedSegments.size();
    double overlap = 0.0;
    for (String requested : requestedSegments) {
      double campaignShare = uniformShare;
      if (shares != null) {
        for (Map.Entry<String, Double> entry : shares.entrySet()) {
          if (entry.getKey() != null
              && entry.getKey().equalsIgnoreCase(requested)
              && entry.getValue() != null
              && entry.getValue() > 0) {
            campaignShare = Math.min(1.0, entry.getValue());
            break;
          }
        }
      }
      for (AudienceData.AudienceSegment segment : audience.getAudienceSegments()) {
        if (segment.getSegmentName() != null
            && segment.getSegmentName().equalsIgnoreCase(requested)
            && segment.getUniqueCount() != null) {
          double sitePct = Math.min(1.0, segment.getUniqueCount() / (double) totalUnique);
          overlap += Math.min(sitePct, campaignShare);
          break;
        }
      }
    }
    return Math.min(1.0, overlap) * 100.0;
  }

  /**
   * Overlap for one demographic dimension: requested values matched against the demographics
   * percentage map by case-insensitive key containment (e.g. "female" → "femalePercentage").
   */
  private static double demographicOverlap(AudienceData audience, List<String> requestedValues) {
    Map<String, Double> demographics =
        audience.getMonthlySummary() != null
            ? audience.getMonthlySummary().getDemographics()
            : null;
    if (demographics == null || demographics.isEmpty()) {
      return 0.0;
    }
    double campaignShare = 1.0 / requestedValues.size();
    double overlap = 0.0;
    for (String requested : requestedValues) {
      Double sitePctValue = findPercentage(demographics, requested);
      if (sitePctValue != null) {
        double sitePct = Math.min(1.0, sitePctValue > 1.0 ? sitePctValue / 100.0 : sitePctValue);
        overlap += Math.min(sitePct, campaignShare);
      }
    }
    return Math.min(1.0, overlap) * 100.0;
  }

  private static Double findPercentage(Map<String, Double> demographics, String requested) {
    String needle = requested.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    for (Map.Entry<String, Double> entry : demographics.entrySet()) {
      String key = entry.getKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
      if (key.contains(needle) && entry.getValue() != null && entry.getValue() > 0) {
        return entry.getValue();
      }
    }
    return null;
  }

  /**
   * PRD §5.7 fallback when audience data is missing entirely: site-type heuristics. A requested
   * segment implied by the venue (airport→travellers, mall→shoppers, gym→fitness…) scores a
   * plausible 65; otherwise a weak 30 — never v1's flat 0.
   */
  private static double venueHeuristicScore(
      Inventory inventory, RecommendationV3RequestDTO.AudienceTargeting targeting) {
    List<String> requested =
        targeting.getAudienceSegments() != null ? targeting.getAudienceSegments() : List.of();
    String venueText =
        ((inventory.getVenueTypes() != null ? String.join(" ", inventory.getVenueTypes()) : "")
                + " "
                + (inventory.getType() != null ? inventory.getType() : "")
                + " "
                + (inventory.getName() != null ? inventory.getName() : ""))
            .toLowerCase(Locale.ROOT);

    for (String segment : requested) {
      String s = segment.toLowerCase(Locale.ROOT);
      boolean implied =
          (s.contains("travel") && (venueText.contains("airport") || venueText.contains("transit")))
              || (s.contains("shopper")
                  && (venueText.contains("mall") || venueText.contains("retail")))
              || (s.contains("fitness") && venueText.contains("gym"))
              || (s.contains("commut")
                  && (venueText.contains("transit") || venueText.contains("station")));
      if (implied) {
        return 65.0;
      }
    }
    return 30.0;
  }
}
