package com.mw.recommendation.engine.v3.selection;

import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.pipeline.ScoredInventoryV3;
import com.mw.recommendation.engine.v3.support.WarningCollector;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Geographic diversity per PRD Part H.1: when the selection spans 2+ cities and the user has not
 * opted out (concentrateBudget), no city may exceed the cap (default 50%) of selected spend.
 * Over-cap cities shed their lowest-scoring selections; the freed budget goes to the best
 * unselected candidates from under-represented cities (tier minimums honoured best-effort). Pure
 * in-memory pass over already-priced candidates — no extra I/O.
 */
@Component
@RequiredArgsConstructor
public class DiversityEnforcer {

  private final V3Properties props;

  /**
   * @param selected currently selected items with their spend
   * @param unselected remaining priced candidates (score-descending order not required)
   * @return possibly adjusted selection map (inventoryId → spend)
   */
  public Map<String, BigDecimal> enforce(
      Map<String, BigDecimal> selectedSpend,
      List<ScoredInventoryV3> selected,
      List<ScoredInventoryV3> unselected,
      boolean concentrateBudget,
      WarningCollector warnings) {

    if (concentrateBudget || selected.size() < 2) {
      return selectedSpend;
    }

    Map<String, List<ScoredInventoryV3>> byCity = groupByCity(selected);
    if (byCity.size() < 2) {
      return selectedSpend; // single-city selection — no diversity rules (PRD scenario C)
    }

    BigDecimal total = selectedSpend.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    if (total.signum() <= 0) {
      return selectedSpend;
    }

    double capPct = props.getDiversity().getCityCapPct();
    Map<String, BigDecimal> adjusted = new LinkedHashMap<>(selectedSpend);
    BigDecimal freed = BigDecimal.ZERO;
    List<String> cappedCities = new ArrayList<>();

    for (Map.Entry<String, List<ScoredInventoryV3>> entry : byCity.entrySet()) {
      BigDecimal citySpend = spendOf(entry.getValue(), adjusted);
      BigDecimal cap = total.multiply(BigDecimal.valueOf(capPct)).divide(BigDecimal.valueOf(100));
      if (citySpend.compareTo(cap) <= 0) {
        continue;
      }
      // Shed lowest-scoring selections from the over-cap city until within cap
      List<ScoredInventoryV3> cityItems = new ArrayList<>(entry.getValue());
      cityItems.sort(Comparator.comparingDouble(s -> s.score().getFinalScore()));
      for (ScoredInventoryV3 item : cityItems) {
        if (citySpend.compareTo(cap) <= 0) {
          break;
        }
        BigDecimal itemSpend = adjusted.remove(item.inventory().getInventoryId());
        if (itemSpend != null) {
          citySpend = citySpend.subtract(itemSpend);
          freed = freed.add(itemSpend);
        }
      }
      cappedCities.add(entry.getKey());
    }

    if (freed.signum() > 0) {
      // Reallocate to the best candidates from cities not currently over-represented
      List<ScoredInventoryV3> candidates =
          unselected.stream()
              .filter(c -> c.cost() != null && c.cost().present())
              .filter(c -> !cappedCities.contains(cityOf(c)))
              .sorted(
                  Comparator.comparingDouble((ScoredInventoryV3 s) -> s.score().getFinalScore())
                      .reversed())
              .toList();
      for (ScoredInventoryV3 candidate : candidates) {
        BigDecimal cost = candidate.cost().cost();
        if (cost.compareTo(freed) <= 0
            && !adjusted.containsKey(candidate.inventory().getInventoryId())) {
          adjusted.put(candidate.inventory().getInventoryId(), cost);
          freed = freed.subtract(cost);
        }
        if (freed.signum() <= 0) {
          break;
        }
      }
      warnings.warn(
          "Geographic diversity applied: "
              + String.join(", ", cappedCities)
              + " capped at "
              + (int) capPct
              + "% of budget; freed budget redistributed to other cities");
    }
    return adjusted;
  }

  private static Map<String, List<ScoredInventoryV3>> groupByCity(List<ScoredInventoryV3> items) {
    Map<String, List<ScoredInventoryV3>> byCity = new HashMap<>();
    for (ScoredInventoryV3 item : items) {
      byCity.computeIfAbsent(cityOf(item), k -> new ArrayList<>()).add(item);
    }
    return byCity;
  }

  private static String cityOf(ScoredInventoryV3 item) {
    var hierarchy = item.inventory().getLocationHierarchy();
    return hierarchy != null && hierarchy.getCityName() != null
        ? hierarchy.getCityName().toLowerCase(Locale.ROOT)
        : "unknown";
  }

  private static BigDecimal spendOf(List<ScoredInventoryV3> items, Map<String, BigDecimal> spend) {
    BigDecimal total = BigDecimal.ZERO;
    for (ScoredInventoryV3 item : items) {
      BigDecimal itemSpend = spend.get(item.inventory().getInventoryId());
      if (itemSpend != null) {
        total = total.add(itemSpend);
      }
    }
    return total;
  }
}
