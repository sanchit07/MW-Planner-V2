package com.mw.recommendation.engine.domain;

import java.util.List;
import java.util.Map;
import lombok.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "audience_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AudienceData extends BaseEntity<String> {

  // Core identifiers
  @Indexed(unique = true)
  private String inventoryId; // Links to Inventory.inventoryId (required for join)

  @Indexed(unique = true)
  private String referenceId; // Links to Inventory.referenceId (required for join)

  private String billboardObjectId; // Original uni_billboard ID (for migration)

  // Monthly summary (aggregated)
  private MonthlySummary monthlySummary;

  // Daily summary (for date range calculations)
  private Map<Integer, DailySummary> dailySummary; // Key: day of month (1-31)

  // Hourly summary (for daypart calculations)
  private Map<Integer, HourlySummary> hourlySummary; // Key: hour (0-23)

  // Audience segments
  private List<AudienceSegment> audienceSegments;

  // Frequency model (for reach calculations)
  private FrequencyModel frequencyModel;

  // Metadata
  private String dataSource; // "MEASURE_API", "UNI_BILLBOARD_DUMP", "RABBITMQ"

  // Nested classes
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class MonthlySummary {
    // Visitor counts
    private Long totalVisitors;
    private Long uniqueVisitors;

    // All demographics stored as key-value pairs
    // Examples: "femalePercentage", "malePercentage", "seniorPercentage",
    // "youngAdultPercentage", "adultPercentage", etc.
    private Map<String, Double> demographics; // All demographic data as key-value pairs
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DailySummary {
    private Long totalVisitors;
    private Long uniqueVisitors;
    private Double frequency;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class HourlySummary {
    private Long totalVisitors;
    private Long uniqueVisitors;
    private Double frequency;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AudienceSegment {
    private String segmentId; // Unique identifier for the segment (key field)
    private String segmentName; // e.g., "Travellers", "Food Lovers"
    private Long totalCount; // Total visits by segment
    private Long uniqueCount; // Unique visitors in segment
    // Note: Index scores are NOT stored
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FrequencyModel {
    private Double coefficient; // For cumulative frequency calculation
    private Double intercept;
  }
}
