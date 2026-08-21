package com.mw.recommendation.engine.v3.scoring;

import com.mw.recommendation.engine.domain.Inventory;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * brandFit per PRD §5.8 — graded brand-category ↔ venue affinity:
 *
 * <ul>
 *   <li>No brand provided → 50 neutral (PRD; the v1 pipeline returns 100 here)
 *   <li>Brand provided but unresolvable → 50 neutral + warning upstream
 *   <li>Category ↔ venue exact affinity → 100; related venue → 70; unrelated → 30; no venue signal
 *       → 50
 * </ul>
 *
 * Category-excluded inventories never reach scoring (partitioned out at fetch), so exclusion
 * handling here is defensive only. The affinity table is a static keyword map (the PRD's AI
 * semantic mapping is behind {@code AiInferencePort} for a later epic).
 */
@Component
public class BrandFitCalculator {

  /** category keyword → venue keywords granting full (first list) and partial (second) affinity. */
  private static final Map<String, List<List<String>>> AFFINITY =
      Map.ofEntries(
          Map.entry(
              "travel",
              List.of(List.of("airport", "transit", "station"), List.of("hotel", "mall"))),
          Map.entry("airline", List.of(List.of("airport"), List.of("transit", "station", "hotel"))),
          Map.entry(
              "automotive",
              List.of(List.of("roadside", "highway", "billboard"), List.of("mall", "transit"))),
          Map.entry(
              "fmcg",
              List.of(List.of("mall", "retail", "supermarket", "convenience"), List.of("transit"))),
          Map.entry(
              "food",
              List.of(List.of("mall", "retail", "restaurant"), List.of("transit", "office"))),
          Map.entry("fashion", List.of(List.of("mall", "retail"), List.of("airport", "transit"))),
          Map.entry(
              "sports", List.of(List.of("gym", "fitness", "stadium"), List.of("mall", "transit"))),
          Map.entry(
              "finance",
              List.of(List.of("office", "cbd", "business"), List.of("airport", "transit"))),
          Map.entry("technology", List.of(List.of("office", "cbd"), List.of("mall", "airport"))),
          Map.entry(
              "entertainment",
              List.of(List.of("mall", "cinema", "entertainment"), List.of("transit"))));

  public Double calculate(Inventory inventory, String brandId, String brandCategory) {
    if (brandId == null || brandId.isBlank()) {
      return 50.0; // PRD: neutral when no brand
    }
    if (brandCategory == null || brandCategory.isBlank()) {
      return 50.0; // invalid/unresolved brand → neutral (warning emitted by the pipeline)
    }

    String category = brandCategory.toLowerCase(Locale.ROOT);
    String venueText = venueText(inventory);
    if (venueText.isBlank()) {
      return 50.0; // no venue signal to grade against
    }

    for (Map.Entry<String, List<List<String>>> entry : AFFINITY.entrySet()) {
      if (!category.contains(entry.getKey())) {
        continue;
      }
      List<String> exact = entry.getValue().get(0);
      List<String> partial = entry.getValue().get(1);
      if (exact.stream().anyMatch(venueText::contains)) {
        return 100.0;
      }
      if (partial.stream().anyMatch(venueText::contains)) {
        return 70.0;
      }
      return 30.0; // known category, unrelated venue
    }
    return 50.0; // category not in the affinity table — neutral
  }

  private static String venueText(Inventory inventory) {
    StringBuilder sb = new StringBuilder();
    if (inventory.getVenueTypes() != null) {
      sb.append(String.join(" ", inventory.getVenueTypes())).append(' ');
    }
    if (inventory.getType() != null) {
      sb.append(inventory.getType()).append(' ');
    }
    if (inventory.getFormat() != null) {
      sb.append(inventory.getFormat()).append(' ');
    }
    if (inventory.getName() != null) {
      sb.append(inventory.getName());
    }
    return sb.toString().toLowerCase(Locale.ROOT);
  }
}
