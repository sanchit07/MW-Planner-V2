package com.mw.recommendation.engine.v3.explain;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Default {@link AiInferencePort}: the PRD's keyword-map fallback (AC-09 middle tier). Purely
 * lexical, deterministic, zero cost — stands in until the Gemini/OpenAI adapters land.
 */
@Component
public class KeywordMapAdapter implements AiInferencePort {

  private static final Map<String, String> VENUE_KEYWORDS =
      Map.ofEntries(
          Map.entry("airport", "airport"),
          Map.entry("terminal", "airport"),
          Map.entry("station", "transit"),
          Map.entry("mrt", "transit"),
          Map.entry("lrt", "transit"),
          Map.entry("bus", "transit"),
          Map.entry("mall", "retail"),
          Map.entry("plaza", "retail"),
          Map.entry("supermarket", "retail"),
          Map.entry("gym", "fitness"),
          Map.entry("office", "office"),
          Map.entry("tower", "office"),
          Map.entry("highway", "roadside"),
          Map.entry("expressway", "roadside"),
          Map.entry("billboard", "roadside"));

  private static final Map<String, String> BRAND_KEYWORDS =
      Map.ofEntries(
          Map.entry("air", "Travel/Airlines"),
          Map.entry("fly", "Travel/Airlines"),
          Map.entry("bank", "Finance"),
          Map.entry("tech", "Technology"),
          Map.entry("motor", "Automotive"),
          Map.entry("auto", "Automotive"),
          Map.entry("food", "Food & Beverage"),
          Map.entry("fashion", "Fashion"));

  @Override
  public Optional<Inference> inferVenueType(String inventoryName, String address, String metadata) {
    String text =
        ((inventoryName != null ? inventoryName : "")
                + " "
                + (address != null ? address : "")
                + " "
                + (metadata != null ? metadata : ""))
            .toLowerCase(Locale.ROOT);
    for (Map.Entry<String, String> entry : VENUE_KEYWORDS.entrySet()) {
      if (text.contains(entry.getKey())) {
        return Optional.of(new Inference(entry.getValue(), 0.6, "keyword-map"));
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<Inference> mapBrandToCategory(String brandName) {
    if (brandName == null || brandName.isBlank()) {
      return Optional.empty();
    }
    String name = brandName.toLowerCase(Locale.ROOT);
    for (Map.Entry<String, String> entry : BRAND_KEYWORDS.entrySet()) {
      if (name.contains(entry.getKey())) {
        return Optional.of(new Inference(entry.getValue(), 0.5, "keyword-map"));
      }
    }
    return Optional.empty();
  }
}
