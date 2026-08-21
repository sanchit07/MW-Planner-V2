package com.mw.recommendation.engine.v3.explain;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.pipeline.ScoredInventoryV3;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "why" text per PRD §7.1 / AC-14: built ONLY from computed signals (top weighted components +
 * availability summary), template-based and deterministic, capped at the configured length (default
 * 120 chars). Signal phrases carry actual values so the sentence is verifiable against the
 * component scores.
 */
@Component
@RequiredArgsConstructor
public class WhyGenerator {

  private final V3Properties props;

  public String generate(ScoredInventoryV3 item) {
    List<String> phrases = new ArrayList<>(3);
    for (String signal : item.score().getTopSignals()) {
      String phrase = phraseFor(signal, item);
      if (phrase != null) {
        phrases.add(phrase);
      }
      if (phrases.size() == 3) {
        break;
      }
    }
    if (phrases.isEmpty()) {
      phrases.add("Balanced fit across scoring factors");
    }
    String why = String.join(" • ", phrases);
    int max = props.getOutput().getWhyMaxLength();
    return why.length() <= max ? why : why.substring(0, max - 1) + "…";
  }

  private String phraseFor(String signal, ScoredInventoryV3 item) {
    Inventory inventory = item.inventory();
    return switch (signal) {
      case "measureFit" ->
          item.score().getMeasureFit() != null
              ? "Delivers " + Math.round(item.score().getMeasureFit()) + "% of goal"
              : null;
      case "geoFit" -> {
        String city =
            inventory.getLocationHierarchy() != null
                ? inventory.getLocationHierarchy().getCityName()
                : null;
        yield city != null ? "Prime location in " + city : "Strong location match";
      }
      case "availability" ->
          item.availability() != null
              ? item.availability().availableDays()
                  + "/"
                  + item.availability().totalDays()
                  + " days available"
              : null;
      case "budgetFit" -> "Budget-efficient";
      case "audienceFit" -> "Audience profile matches targeting";
      case "brandFit" -> "Venue suits the brand category";
      case "qualityFit" -> "High display quality";
      case "timeFit" -> "Peaks in requested dayparts";
      default -> null;
    };
  }
}
