package com.mw.recommendation.engine.v3.selection;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Budget allocation per PRD Part H.2: goal-specific percentages across the seven buckets (digital,
 * classic, transit, retail, network, audio, experiential). All seven buckets are live — the v1/v2
 * mapping has retail/network/audio/experiential commented out, silently funnelling their
 * allocations elsewhere. Radio(audio)/experiential are opt-in: they stay 0 unless the request's
 * budgetAllocation explicitly funds them.
 */
@Component
@RequiredArgsConstructor
public class BudgetAllocator {

  public static final String DIGITAL = "digital";
  public static final String CLASSIC = "classic";
  public static final String TRANSIT = "transit";
  public static final String RETAIL = "retail";
  public static final String NETWORK = "network";
  public static final String AUDIO = "audio";
  public static final String EXPERIENTIAL = "experiential";

  private final V3Properties props;

  /** Bucket for an inventory, from type first (more specific) then classification. */
  public String bucketOf(Inventory inventory) {
    String type = lower(inventory.getType());
    if (type.contains("transit")) {
      return TRANSIT;
    }
    if (type.contains("retail")) {
      return RETAIL;
    }
    if (type.contains("network")) {
      return NETWORK;
    }
    if (type.contains("radio") || type.contains("audio")) {
      return AUDIO;
    }
    if (type.contains("experiential")) {
      return EXPERIENTIAL;
    }
    String classification = lower(inventory.getClassification());
    if (classification.contains("classic")) {
      return CLASSIC;
    }
    if (classification.contains("transit")) {
      return TRANSIT;
    }
    if (classification.contains("audio")) {
      return AUDIO;
    }
    return DIGITAL;
  }

  /** Effective bucket → percent map: request override when present, else the goal default. */
  public Map<String, Double> effectiveAllocation(RecommendationV3RequestDTO request) {
    Map<String, Double> custom = request.getBudgetAllocation();
    if (custom != null && !custom.isEmpty()) {
      Map<String, Double> normalized = new LinkedHashMap<>();
      custom.forEach(
          (key, value) -> {
            if (key != null && value != null && value > 0) {
              normalized.put(key.trim().toLowerCase(Locale.ROOT), value);
            }
          });
      if (!normalized.isEmpty()) {
        return normalized;
      }
    }
    String goalKey =
        request.getGoal() != null ? request.getGoal().name().toLowerCase(Locale.ROOT) : "none";
    return props
        .getSelection()
        .getAllocations()
        .getOrDefault(goalKey, props.getSelection().getAllocations().get("none"));
  }

  /** Per-bucket budget caps (HALF_UP to 2dp). */
  public Map<String, BigDecimal> bucketBudgets(
      BigDecimal totalBudget, Map<String, Double> allocation) {
    Map<String, BigDecimal> budgets = new LinkedHashMap<>();
    allocation.forEach(
        (bucket, pct) ->
            budgets.put(
                bucket,
                totalBudget
                    .multiply(BigDecimal.valueOf(pct))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)));
    return budgets;
  }

  private static String lower(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }
}
