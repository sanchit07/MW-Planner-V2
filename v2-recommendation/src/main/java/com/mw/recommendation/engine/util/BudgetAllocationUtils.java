package com.mw.recommendation.engine.util;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utilities for budget allocation by format/type and for mapping inventory to allocation key. Used
 * by auto-select and selected-inventories flows. Allocation keys: digital, classic, transit,
 * retail, network, audio, experiential (lowercase).
 */
public final class BudgetAllocationUtils {

  private BudgetAllocationUtils() {}

  private static final String KEY_DIGITAL = "digital";
  private static final String KEY_CLASSIC = "classic";
  private static final String KEY_TRANSIT = "transit";
  private static final String KEY_RETAIL = "retail";
  private static final String KEY_NETWORK = "network";
  private static final String KEY_AUDIO = "audio";
  private static final String KEY_EXPERIENTIAL = "experiential";

  /**
   * Returns effective budget allocation: user's map if present and non-empty, else default by goal
   * (Default Format Allocation by Goal from requirement doc H.2). Keys are lowercase: digital,
   * classic, transit, retail, network, audio, experiential.
   */
  public static Map<String, Double> getEffectiveBudgetAllocation(RecommendationRequestDTO request) {
    if (request.getBudgetAllocation() != null && !request.getBudgetAllocation().isEmpty()) {
      return normalizeKeys(request.getBudgetAllocation());
    }
    return getDefaultAllocationByGoal(request.getGoal());
  }

  /** Default format allocation by goal. No goal → Impressions allocation. */
  public static Map<String, Double> getDefaultAllocationByGoal(
      RecommendationRequestDTO.CampaignGoal goal) {
    if (goal == null) {
      return defaultImpressions();
    }
    return switch (goal) {
      case IMPRESSIONS -> defaultImpressions();
      case REACH -> defaultReach();
      case AD_PLAYS -> defaultAdPlays();
      case SOV -> defaultSov();
      case CARBON -> defaultCarbon();
    };
  }

  private static Map<String, Double> defaultImpressions() {
    Map<String, Double> m = new LinkedHashMap<>();
    m.put(KEY_DIGITAL, 35.0);
    m.put(KEY_CLASSIC, 25.0);
    m.put(KEY_TRANSIT, 20.0);
    m.put(KEY_RETAIL, 10.0);
    m.put(KEY_NETWORK, 7.0);
    m.put(KEY_AUDIO, 0.0);
    m.put(KEY_EXPERIENTIAL, 3.0);
    return Collections.unmodifiableMap(m);
  }

  private static Map<String, Double> defaultReach() {
    Map<String, Double> m = new LinkedHashMap<>();
    m.put(KEY_CLASSIC, 30.0);
    m.put(KEY_TRANSIT, 25.0);
    m.put(KEY_RETAIL, 15.0);
    m.put(KEY_DIGITAL, 15.0);
    m.put(KEY_NETWORK, 10.0);
    m.put(KEY_AUDIO, 0.0);
    m.put(KEY_EXPERIENTIAL, 5.0);
    return Collections.unmodifiableMap(m);
  }

  private static Map<String, Double> defaultAdPlays() {
    Map<String, Double> m = new LinkedHashMap<>();
    m.put(KEY_DIGITAL, 40.0);
    m.put(KEY_NETWORK, 30.0);
    m.put(KEY_RETAIL, 15.0);
    m.put(KEY_TRANSIT, 10.0);
    m.put(KEY_CLASSIC, 5.0);
    m.put(KEY_AUDIO, 0.0);
    m.put(KEY_EXPERIENTIAL, 0.0);
    return Collections.unmodifiableMap(m);
  }

  private static Map<String, Double> defaultSov() {
    Map<String, Double> m = new LinkedHashMap<>();
    m.put(KEY_DIGITAL, 40.0);
    m.put(KEY_NETWORK, 25.0);
    m.put(KEY_TRANSIT, 20.0);
    m.put(KEY_RETAIL, 10.0);
    m.put(KEY_CLASSIC, 5.0);
    m.put(KEY_AUDIO, 0.0);
    m.put(KEY_EXPERIENTIAL, 0.0);
    return Collections.unmodifiableMap(m);
  }

  private static Map<String, Double> defaultCarbon() {
    Map<String, Double> m = new LinkedHashMap<>();
    m.put(KEY_CLASSIC, 45.0);
    m.put(KEY_TRANSIT, 30.0);
    m.put(KEY_RETAIL, 15.0);
    m.put(KEY_DIGITAL, 5.0);
    m.put(KEY_NETWORK, 5.0);
    m.put(KEY_AUDIO, 0.0);
    m.put(KEY_EXPERIENTIAL, 0.0);
    return Collections.unmodifiableMap(m);
  }

  /**
   * Calculates per-category budget caps from total budget and allocation percentages. Categories
   * with 0% allocation are included with a zero budget.
   *
   * @param totalBudget The total campaign budget
   * @param allocation Map of category key to allocation percentage (e.g. "digital" -> 35.0)
   * @return Map of category key to budget cap (e.g. "digital" -> 3500.00 for 10000 total)
   */
  public static Map<String, BigDecimal> calculateCategoryBudgets(
      BigDecimal totalBudget, Map<String, Double> allocation) {
    if (totalBudget == null || allocation == null || allocation.isEmpty()) {
      return Map.of();
    }
    Map<String, BigDecimal> budgets = new LinkedHashMap<>();
    for (Map.Entry<String, Double> entry : allocation.entrySet()) {
      double pct = entry.getValue() != null ? entry.getValue() : 0.0;
      BigDecimal categoryBudget =
          totalBudget
              .multiply(BigDecimal.valueOf(pct))
              .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
      budgets.put(entry.getKey(), categoryBudget);
    }
    return budgets;
  }

  private static Map<String, Double> normalizeKeys(Map<String, Double> allocation) {
    Map<String, Double> out = new LinkedHashMap<>();
    for (Map.Entry<String, Double> e : allocation.entrySet()) {
      String key = e.getKey() == null ? null : e.getKey().toLowerCase().trim();
      if (key != null && !key.isEmpty()) {
        out.put(key, e.getValue());
      }
    }
    return out;
  }

  /**
   * Maps an inventory to a single allocation key (digital, classic, transit, retail, network,
   * audio, experiential). Used for bucket-based selection. Based on classification + type.
   */
  public static String getAllocationKey(Inventory inventory) {
    if (inventory == null) {
      return KEY_DIGITAL;
    }
    String type = nullToEmpty(inventory.getType()).toLowerCase();
    String classification = nullToEmpty(inventory.getClassification()).toLowerCase();
    // Type-based (takes precedence for transit, retail, network, audio, experiential)
    if (type.contains("transit")) {
      return KEY_TRANSIT;
    }
    if (type.contains("retail")) {
      return KEY_RETAIL;
    }
    if (type.contains("network")) {
      return KEY_NETWORK;
    }
    if (type.contains("audio")) {
      return KEY_AUDIO;
    }
    if (type.contains("experiential")) {
      return KEY_EXPERIENTIAL;
    }
    // Classification-based
    if (classification.contains("classic")) {
      return KEY_CLASSIC;
    }
    if (classification.contains("digital")) {
      return KEY_DIGITAL;
    }
    if (classification.contains("transit")) {
      return KEY_TRANSIT;
    }
    return KEY_DIGITAL;
  }

  /** Maps inventory details (e.g. from RecommendationResult) to allocation key. */
  public static String getAllocationKey(RecommendationResult.InventoryDetails details) {
    if (details == null) {
      return KEY_DIGITAL;
    }
    String type = nullToEmpty(details.getType()).toLowerCase();
    String classification = nullToEmpty(details.getClassification()).toLowerCase();
    // if (type.contains("transit")) {
    //   return KEY_TRANSIT;
    // }
    // if (type.contains("retail")) {
    //   // return KEY_RETAIL;
    //   return KEY_DIGITAL;
    // }
    // if (type.contains("network")) {
    //   return KEY_NETWORK;
    // }
    // if (type.contains("audio")) {
    //   return KEY_AUDIO;
    // }
    // if (type.contains("experiential")) {
    //   return KEY_EXPERIENTIAL;
    // }
    if (classification.contains("classic")) {
      return KEY_CLASSIC;
    }
    if (classification.contains("digital")) {
      return KEY_DIGITAL;
    }
    if (classification.contains("transit")) {
      return KEY_TRANSIT;
    }
    return KEY_DIGITAL;
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
