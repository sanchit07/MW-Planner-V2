package com.mw.recommendation.engine.rabbitmq;

import com.mw.recommendation.engine.domain.AudienceData;
import com.mw.recommendation.engine.dto.ExternalAudienceMessageDTO;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Converter service to transform external audience messages to internal AudienceData entities */
@Component
@Slf4j
public class ExternalAudienceMessageConverter {

  /** Convert external audience message to internal AudienceData entity */
  public AudienceData convertToAudienceData(ExternalAudienceMessageDTO externalMessage) {
    log.debug(
        "Converting external audience message for inventoryId: {}",
        externalMessage.getInventoryId());

    AudienceData audienceData = new AudienceData();

    // Core identifiers
    audienceData.setInventoryId(externalMessage.getInventoryId());
    audienceData.setReferenceId(externalMessage.getReferenceId());
    audienceData.setBillboardObjectId(externalMessage.getBillboardObjectId());

    // Monthly summary
    if (externalMessage.getMonthlySummary() != null) {
      // Normalize demographics keys to match expected request format
      Map<String, Double> normalizedDemographics =
          normalizeDemographics(externalMessage.getMonthlySummary().getDemographics());

      AudienceData.MonthlySummary monthlySummary =
          AudienceData.MonthlySummary.builder()
              .totalVisitors(externalMessage.getMonthlySummary().getTotalVisitors())
              .uniqueVisitors(externalMessage.getMonthlySummary().getUniqueVisitors())
              .demographics(normalizedDemographics)
              .build();
      audienceData.setMonthlySummary(monthlySummary);
    }

    // Daily summary
    if (externalMessage.getDailySummary() != null) {
      audienceData.setDailySummary(
          externalMessage.getDailySummary().entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey,
                      entry ->
                          AudienceData.DailySummary.builder()
                              .totalVisitors(entry.getValue().getTotalVisitors())
                              .uniqueVisitors(entry.getValue().getUniqueVisitors())
                              .frequency(entry.getValue().getFrequency())
                              .build())));
    }

    // Hourly summary
    if (externalMessage.getHourlySummary() != null) {
      Map<Integer, AudienceData.HourlySummary> hourlySummaryMap =
          externalMessage.getHourlySummary().entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey,
                      entry ->
                          AudienceData.HourlySummary.builder()
                              .totalVisitors(entry.getValue().getTotalVisitors())
                              .uniqueVisitors(entry.getValue().getUniqueVisitors())
                              .frequency(entry.getValue().getFrequency())
                              .build()));
      audienceData.setHourlySummary(hourlySummaryMap);
    }

    // Audience segments (normalize segment names for income brackets)
    if (externalMessage.getAudienceSegments() != null) {
      List<AudienceData.AudienceSegment> segments =
          externalMessage.getAudienceSegments().stream()
              .map(
                  es -> {
                    // Normalize income bracket segment names to match request format
                    String normalizedSegmentName = normalizeSegmentName(es.getSegmentName());
                    return AudienceData.AudienceSegment.builder()
                        .segmentId(es.getSegmentId())
                        .segmentName(normalizedSegmentName)
                        .totalCount(es.getTotalCount())
                        .uniqueCount(es.getUniqueCount())
                        .build();
                  })
              .collect(Collectors.toList());
      audienceData.setAudienceSegments(segments);
    }

    // Frequency model
    if (externalMessage.getFrequencyModel() != null) {
      AudienceData.FrequencyModel frequencyModel =
          AudienceData.FrequencyModel.builder()
              .coefficient(externalMessage.getFrequencyModel().getCoefficient())
              .intercept(externalMessage.getFrequencyModel().getIntercept())
              .build();
      audienceData.setFrequencyModel(frequencyModel);
    }

    // Metadata
    audienceData.setUpdatedAt(
        externalMessage.getLastUpdatedAt() != null
            ? externalMessage.getLastUpdatedAt()
            : LocalDateTime.now());
    audienceData.setDataSource(
        externalMessage.getDataSource() != null ? externalMessage.getDataSource() : "RABBITMQ");

    log.debug(
        "Successfully converted audience data for inventoryId: {}", audienceData.getInventoryId());
    return audienceData;
  }

  /**
   * Normalize demographics keys to match expected request format for scoring. Maps various
   * demographic key formats to standardized keys that match request format.
   *
   * @param demographics Original demographics map from external message
   * @return Normalized demographics map with standardized keys
   */
  private Map<String, Double> normalizeDemographics(Map<String, Double> demographics) {
    if (demographics == null || demographics.isEmpty()) {
      return new HashMap<>();
    }

    // Copy all original keys (preserve original data)
    Map<String, Double> normalized = new HashMap<>(demographics);

    // Normalize age demographics to match request format (e.g., "18-24", "25-34")
    // Map aggregated keys
    if (demographics.containsKey("youngAdultPercentage")) {
      normalized.put("18-24", demographics.get("youngAdultPercentage"));
    }
    if (demographics.containsKey("adultPercentage")) {
      normalized.put("25-34", demographics.get("adultPercentage"));
      normalized.put("35-44", demographics.get("adultPercentage"));
      normalized.put("45-54", demographics.get("adultPercentage"));
    }
    if (demographics.containsKey("seniorPercentage")) {
      normalized.put("55-64", demographics.get("seniorPercentage"));
      normalized.put("60+", demographics.get("seniorPercentage"));
      normalized.put("65+", demographics.get("seniorPercentage"));
    }

    // Map detailed age range keys to standardized format
    mapIfExists(demographics, normalized, "10_19_percentage", "10-19");
    mapIfExists(demographics, normalized, "18_24_percentage", "18-24");
    mapIfExists(demographics, normalized, "20_29_percentage", "20-29");
    mapIfExists(demographics, normalized, "25_34_percentage", "25-34");
    mapIfExists(demographics, normalized, "30_39_percentage", "30-39");
    mapIfExists(demographics, normalized, "35_44_percentage", "35-44");
    mapIfExists(demographics, normalized, "40_49_percentage", "40-49");
    mapIfExists(demographics, normalized, "45_54_percentage", "45-54");
    mapIfExists(demographics, normalized, "50_59_percentage", "50-59");
    mapIfExists(demographics, normalized, "55_64_percentage", "55-64");
    mapIfExists(demographics, normalized, "60_plus_percentage", "60+");
    mapIfExists(demographics, normalized, "65_plus_percentage", "65+");

    // Normalize gender demographics to match request format (uppercase)
    mapIfExists(demographics, normalized, "malePercentage", "MALE");
    mapIfExists(demographics, normalized, "femalePercentage", "FEMALE");

    // Normalize income demographics to canonical values: Low, Lower-middle, Middle, Upper-middle,
    // High
    mapIfExists(demographics, normalized, "lowIncomePercentage", "Low");
    mapIfExists(demographics, normalized, "lowerMiddleIncomePercentage", "Lower-middle");
    mapIfExists(demographics, normalized, "middleIncomePercentage", "Middle");
    mapIfExists(demographics, normalized, "midIncomePercentage", "Middle");
    mapIfExists(demographics, normalized, "upperMiddleIncomePercentage", "Upper-middle");
    mapIfExists(demographics, normalized, "highIncomePercentage", "High");
    mapIfExists(demographics, normalized, "affluentPercentage", "High");

    // Normalize interests demographics to canonical values (Sports & Fitness, Technology, etc.)
    mapIfExists(demographics, normalized, "sportsFitnessPercentage", "Sports & Fitness");
    mapIfExists(demographics, normalized, "sportsAndFitnessPercentage", "Sports & Fitness");
    mapIfExists(demographics, normalized, "technologyPercentage", "Technology");
    mapIfExists(demographics, normalized, "foodDiningPercentage", "Food & Dining");
    mapIfExists(demographics, normalized, "foodAndDiningPercentage", "Food & Dining");
    mapIfExists(demographics, normalized, "travelPercentage", "Travel");
    mapIfExists(demographics, normalized, "musicPercentage", "Music");
    mapIfExists(demographics, normalized, "entertainmentPercentage", "Entertainment");
    mapIfExists(demographics, normalized, "homeGardenPercentage", "Home & Garden");
    mapIfExists(demographics, normalized, "homeAndGardenPercentage", "Home & Garden");
    mapIfExists(demographics, normalized, "healthWellnessPercentage", "Health & Wellness");
    mapIfExists(demographics, normalized, "healthAndWellnessPercentage", "Health & Wellness");
    mapIfExists(demographics, normalized, "automotivePercentage", "Automotive");

    return normalized;
  }

  /** Helper method to map a key if it exists in the source map. */
  private void mapIfExists(
      Map<String, Double> source, Map<String, Double> target, String sourceKey, String targetKey) {
    if (source.containsKey(sourceKey) && source.get(sourceKey) != null) {
      target.put(targetKey, source.get(sourceKey));
    }
  }

  /**
   * Normalize segment names to match expected request format. Specifically handles income bracket
   * segment names.
   *
   * @param segmentName Original segment name
   * @return Normalized segment name
   */
  private String normalizeSegmentName(String segmentName) {
    if (segmentName == null) {
      return null;
    }
    // Normalize income bracket segment names
    String normalized = segmentName.trim();
    return switch (normalized) {
      case "Affluent People" -> "High Income"; // Map to request format
      case "Low Income", "Mid Income", "Lower-middle Income", "Higher-middle Income" -> normalized;
      default -> normalized; // Keep other segment names as-is
    };
  }
}
