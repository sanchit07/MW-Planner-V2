package com.mw.recommendation.engine.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for generating recommendations */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationRequestDTO {
  // Mandatory fields
  @NotBlank(message = "Country is required")
  private String country; // ISO country code

  @NotNull(message = "Start date is required")
  private LocalDate startDate;

  @NotNull(message = "End date is required")
  private LocalDate endDate;

  // Optional fields
  private String productId; // Product ID
  private String companyId; // Company ID
  private GeographyTargeting geographyTargeting;
  private AudienceTargeting audienceTargeting;
  private String brandId; // Optional, may be invalid ("test", "demo")
  private BigDecimal budget; // Optional
  private CampaignGoal goal; // Optional: IMPRESSIONS, REACH, SOV
  private Long goalValue; // Optional: target value for the goal
  private List<String> excludedInventoryIds; // Optional
  private List<String> excludedIabCategories; // Optional (from brand)
  private Integer topN; // Optional: number of top recommendations to return
  private List<String> mediaOwnerIds; // Optional: restrict scoring pool to these media owner IDs
  private List<String> classifications; // Optional: e.g. ["Digital"], ["Classic"], ["Transit"]

  /**
   * Optional. Selected spot duration(s) in seconds (e.g. [15]). EXACT-matched ($in) at the scored
   * fetch against the screen's physical slot length {@code digitalFields.spotDuration}, so the run
   * only scores screens whose slot equals the selected duration — a 10s-slot screen is never
   * recommended for a 15s creative (prevents the booking "creativeDuration exceeds spotDuration"
   * reject). Digital-only (Classic panels carry no spotDuration). NON_NULL so the dedup hash of
   * duration-less requests is unchanged (RequestHashUtils serializes this DTO).
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<Integer> durations;

  /**
   * Optional inventory-attribute filters, all EXACT-matched at the scored fetch (like {@link
   * #classifications} / {@link #durations}) so the run only scores screens matching the line item's
   * selection. All NON_NULL, so a request that omits them hashes exactly as before.
   */
  // Inventory Format → top-level `format`.
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<String> formats;

  // Resolution as "WxH" strings → matched against a `panels` entry.
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<String> resolutions;

  // Creative Type → `creativeFormats.creativeType`.
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<String> creativeTypes;

  // DSP → `dsps`. (Field declared once below, shared by both the attribute-filter and the
  // standalone DSP/programmatic filter paths.)

  // Programmatic purchase type → `programmaticDealTypes` (raw lowercase tokens, matched $in).
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<String> dealTypes;

  // Programmatic support toggle (YES/NO/ALL) → filter by whether the inventory offers ANY
  // programmatic deal type. Broader than dealTypes; useful when the flag and deal-type disagree.
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private com.mw.recommendation.engine.enums.ProgrammaticSupport programmaticSupport;

  /**
   * Optional. Keys: digital, transit, classic, retail, network, audio, experiential (lowercase).
   * Values: percentages (e.g. 25.0). If absent, default allocation by goal is used.
   */
  private Map<String, Double> budgetAllocation;

  /**
   * Optional. Case-insensitive substring keywords; an inventory is kept if ANY keyword matches its
   * name, referenceId, address, city, or state. Null/empty = no filtering. NON_NULL so the dedup
   * hash of keyword-less requests is unchanged (RequestHashUtils serializes this DTO).
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<String> searchKeywords;

  /**
   * Optional. Restrict the pool to inventories whose {@code dsps} array contains at least one of
   * these DSPs (e.g. ["MAX", "LMX"]). Null/empty = no filtering. NON_NULL so the dedup hash of
   * requests without this field is unchanged.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<String> dsps;

  /**
   * Optional. When true, the Digital slice of the results must be programmatic: classification ==
   * "Digital" AND programmaticDealTypes has at least one element. Non-Digital classifications are
   * unaffected. Null/false = no programmatic constraint. NON_NULL so the dedup hash of requests
   * without this field is unchanged.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean programmaticEnabled;

  /**
   * Optional. Restrict the pool to inventories whose {@code inventoryCluster} array contains at
   * least one of these clusters. Match-any ($in), like {@link #dsps}. Null/empty = no filtering.
   * NON_NULL so the dedup hash of requests without this field is unchanged (RequestHashUtils
   * serializes this DTO).
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<String> inventoryCluster;

  @AssertTrue(message = "goalValue for AD_PLAYS must be between 100 and 1000000")
  public boolean isGoalValueValidForAdPlays() {
    if (goal != CampaignGoal.AD_PLAYS || goalValue == null) return true;
    return goalValue >= 100 && goalValue <= 1_000_000;
  }

  /*
   * @JsonIgnore is load-bearing: RequestHashUtils serializes this DTO with a default ObjectMapper,
   * which auto-detects boolean is-getters as properties. Without it, a new "searchKeywordsValid"
   * property appears in the JSON and changes the hash of EVERY request, breaking dedup against
   * pre-deploy runs. (The existing isGoalValueValidForAdPlays() lacks @JsonIgnore and already
   * leaks into the hash — deterministically, so it's harmless — but it must NOT be retrofitted:
   * that would also change every stored hash.)
   */
  @JsonIgnore
  @AssertTrue(
      message =
          "searchKeywords must contain at most 20 keywords, each non-blank and at most 100 characters")
  public boolean isSearchKeywordsValid() {
    if (searchKeywords == null || searchKeywords.isEmpty()) return true;
    if (searchKeywords.size() > 20) return false;
    return searchKeywords.stream()
        .allMatch(k -> k != null && !k.isBlank() && k.trim().length() <= 100);
  }

  // Geography targeting
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class GeographyTargeting {
    private List<String> cities;
    private List<String> states;
    private List<Geofence> geofences; // Polygons or Circles
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Geofence {
    private String type; // "Polygon", "Circle"
    private List<List<Double>> coordinates; // For polygon: [[lng, lat], ...]
    private Double centerLng; // For circle: center longitude
    private Double centerLat; // For circle: center latitude
    // Note: Circle geofences always use 50KM radius
  }

  // Audience targeting
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AudienceTargeting {
    private List<String> audienceSegments; // e.g., ["Travellers", "Food Lovers"]
    private Map<String, List<String>> demographics; // age, gender, etc.
    private Map<String, List<String>>
        venueTypeIds; // keyed by classification (digital, classic, etc.)
  }

  // Campaign goal
  public enum CampaignGoal {
    IMPRESSIONS,
    REACH,
    SOV,
    AD_PLAYS,
    CARBON
  }
}
