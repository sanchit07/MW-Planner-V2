package com.mw.planner.dto.recommendation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationRequestDTO {

  private String country;
  private LocalDate startDate;
  private LocalDate endDate;
  private String productId;
  private String companyId;
  private GeographyTargeting geographyTargeting;
  private AudienceTargeting audienceTargeting;
  private String brandId;
  private BigDecimal budget;
  private CampaignGoal goal;
  private Long goalValue;
  private List<String> excludedInventoryIds;
  private List<String> excludedIabCategories;
  private Integer topN;
  private Map<String, Double> budgetAllocation;
  private List<String> mediaOwnerIds;
  private List<String> searchKeywords;
  private List<String> dsps;
  private Boolean programmaticEnabled;
  private List<String> inventoryCluster;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class GeographyTargeting {
    private List<String> cities;
    private List<String> states;
    private List<Geofence> geofences;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Geofence {
    private String type;
    private List<List<Double>> coordinates;
    private Double centerLng;
    private Double centerLat;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AudienceTargeting {
    private List<String> audienceSegments;
    private Map<String, List<String>> demographics;
    private VenueTypeIds venueTypeIds;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class VenueTypeIds {
    private List<String> digital;
    private List<String> classic;
  }

  public enum CampaignGoal {
    IMPRESSIONS,
    REACH,
    SOV,
    AD_PLAYS,
    CARBON
  }
}
