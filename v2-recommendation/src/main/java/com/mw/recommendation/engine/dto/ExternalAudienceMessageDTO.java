package com.mw.recommendation.engine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for external audience messages from mw-measure system. Represents the structure of audience
 * data received via RabbitMQ. Structure is compatible with uni_billboard format.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalAudienceMessageDTO {

  @JsonProperty("inventoryId")
  private String inventoryId; // Links to Inventory.inventoryId

  @JsonProperty("referenceId")
  private String referenceId; // Links to Inventory.referenceId

  @JsonProperty("billboardObjectId")
  private String billboardObjectId; // Original uni_billboard ID

  @JsonProperty("monthlySummary")
  private MonthlySummary monthlySummary;

  @JsonProperty("dailySummary")
  private Map<Integer, DailySummary> dailySummary; // Key: day of month (1-31)

  @JsonProperty("hourlySummary")
  private Map<Integer, HourlySummary> hourlySummary; // Key: hour (0-23)

  @JsonProperty("audienceSegments")
  private List<AudienceSegment> audienceSegments;

  @JsonProperty("frequencyModel")
  private FrequencyModel frequencyModel;

  @JsonProperty("lastUpdatedAt")
  private LocalDateTime lastUpdatedAt;

  @JsonProperty("dataSource")
  private String dataSource; // "MEASURE_API", "UNI_BILLBOARD_DUMP", "RABBITMQ"

  // Nested classes
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class MonthlySummary {
    @JsonProperty("totalVisitors")
    private Long totalVisitors;

    @JsonProperty("uniqueVisitors")
    private Long uniqueVisitors;

    // All demographics stored as key-value pairs
    // Examples: "femalePercentage", "malePercentage", "seniorPercentage", etc.
    // Note: Index scores are NOT stored (as per requirement)
    @JsonProperty("demographics")
    private Map<String, Double> demographics;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DailySummary {
    @JsonProperty("totalVisitors")
    private Long totalVisitors;

    @JsonProperty("uniqueVisitors")
    private Long uniqueVisitors;

    @JsonProperty("frequency")
    private Double frequency;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class HourlySummary {
    @JsonProperty("totalVisitors")
    private Long totalVisitors;

    @JsonProperty("uniqueVisitors")
    private Long uniqueVisitors;

    @JsonProperty("frequency")
    private Double frequency;

    @JsonProperty("hour")
    private Integer hour; // 0-23
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AudienceSegment {
    @JsonProperty("segmentId")
    private String segmentId; // Unique identifier for the segment

    @JsonProperty("segmentName")
    private String segmentName; // e.g., "Travellers", "Food Lovers"

    @JsonProperty("totalCount")
    private Long totalCount; // Total visits by segment

    @JsonProperty("uniqueCount")
    private Long uniqueCount; // Unique visitors in segment
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FrequencyModel {
    @JsonProperty("coefficient")
    private Double coefficient; // For cumulative frequency calculation

    @JsonProperty("intercept")
    private Double intercept;
  }
}
