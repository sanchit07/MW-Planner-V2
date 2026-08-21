package com.mw.recommendation.engine.v3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * v3 recommendation request. Superset of the v1 contract plus the PRD inputs v1 lacks: POI
 * targeting, user-defined circle radius, dayparts, channels, seed / variation controls, and budget
 * currency. Optional additions are NON_NULL-serialized so the dedup hash of requests that omit them
 * stays stable as fields are added.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecommendationV3RequestDTO {

  // ── Mandatory (PRD §3.1) ────────────────────────────────────────────────
  @NotBlank(message = "Country is required")
  private String country;

  @NotNull(message = "Start date is required")
  private LocalDate startDate;

  @NotNull(message = "End date is required")
  private LocalDate endDate;

  // ── Optional targeting ──────────────────────────────────────────────────
  private String productId;
  private String companyId;
  private GeographyTargeting geographyTargeting;
  private AudienceTargeting audienceTargeting;

  /** Requested dayparts for timeFit scoring (PRD §5.10). */
  private List<Daypart> dayparts;

  /** Channel hints (e.g. "airport") granting the geoFit channel bonus (PRD §5.4). */
  private List<String> channels;

  private String brandId;
  private BigDecimal budget;

  /** ISO currency of the budget; defaults to the configured default when absent. */
  private String budgetCurrency;

  private CampaignGoal goal;
  private Long goalValue;
  private List<String> excludedInventoryIds;
  private List<String> excludedIabCategories;
  private Integer topN;
  private List<String> mediaOwnerIds;
  private List<String> classifications;

  /** Optional bucket→percent override of the goal-default budget allocation (PRD H.2). */
  private Map<String, Double> budgetAllocation;

  private List<String> searchKeywords;

  // ── Variation / determinism controls (PRD §6.7, AC-10/11, H.3) ──────────
  /** Client-supplied seed for fully deterministic results. Excluded from the dedup hash. */
  private String seed;

  /** Disables ranking jitter and the selection variation pool entirely. */
  private Boolean disableVariation;

  /** When true, candidates whose cost exceeds the budget are excluded (PRD AC-05). */
  private Boolean strictBudget;

  /** Opts out of geographic diversity spread (PRD Part H.1 step 3). */
  private Boolean concentrateBudget;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class GeographyTargeting {
    private List<String> cities;
    private List<String> states;
    private List<Geofence> geofences;

    /** POI targeting (PRD §3.1) — weighted points of interest. */
    private List<Poi> pois;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Geofence {
    /** "Polygon" or "Circle". */
    private String type;

    /** Polygon: [[lng, lat], ...]. */
    private List<List<Double>> coordinates;

    private Double centerLng;
    private Double centerLat;

    /** Circle radius in meters; defaults to the configured 50 km when absent. */
    private Double radiusMeters;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Poi {
    private String name;
    private Double lat;
    private Double lng;

    /** Relative weight for the weighted min-distance geoFit rule (PRD §5.4); default 1.0. */
    private Double weight;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AudienceTargeting {
    private List<String> audienceSegments;

    /**
     * Optional campaign share per segment (0-1, e.g. Travellers → 0.6) for the PRD §5.7 min-overlap
     * formula. Segments without a share default to a uniform split.
     */
    private Map<String, Double> audienceSegmentShares;

    /** demographic dimension → requested values, e.g. age → ["25-34"]. */
    private Map<String, List<String>> demographics;

    /** classification (lowercase) → venue type ids, used as a fetch filter. */
    private Map<String, List<String>> venueTypeIds;
  }

  /** Daypart window with weight (PRD §5.10: timeFit = Σ weight × inventory daypart %). */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Daypart {
    /** 0-23 inclusive start hour. */
    private Integer startHour;

    /** Exclusive end hour, 1-24. */
    private Integer endHour;

    private Double weight;
  }

  public enum CampaignGoal {
    IMPRESSIONS,
    REACH,
    SOV,
    AD_PLAYS,
    CARBON
  }
}
