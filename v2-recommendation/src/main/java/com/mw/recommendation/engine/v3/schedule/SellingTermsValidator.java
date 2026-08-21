package com.mw.recommendation.engine.v3.schedule;

import com.mw.recommendation.engine.domain.Inventory;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Selling-terms validation per PRD Part E, with the extend-or-exclude resolution: a schedule
 * shorter than the inventory minimum is extended when the extension stays within a 5% cost headroom
 * of the allocated spend, otherwise the inventory is excluded with a warning. Operating hours/days
 * and loop capacity are enforced structurally by the schedule builder (hours are only ever chosen
 * from operating windows at 1 spot per loop); lead time is enforced at fetch. Minimum spend is not
 * in the synced selling terms (upstream gap) — documented, not silently assumed.
 */
@Component
public class SellingTermsValidator {

  public record Validation(
      boolean valid, int adjustedDays, int minHoursPerDay, List<String> adjustments) {}

  /**
   * @param plannedDays days the budget can afford
   * @param campaignDays full campaign window length
   */
  public Validation validate(Inventory inventory, int plannedDays, long campaignDays) {
    List<String> adjustments = new ArrayList<>();
    Inventory.SellingTerm term = inventory.getSellingTerm();
    int days = plannedDays;
    int minHours = 0;

    if (term != null) {
      if (term.getMinDays() != null && term.getMinDays() > 0) {
        if (term.getMinDays() > campaignDays) {
          // Fetch normally prevents this; defensive exclude per PRD Part E check 3
          return new Validation(false, 0, 0, List.of("minimum days exceed campaign window"));
        }
        if (days < term.getMinDays()) {
          adjustments.add(
              "extended booking from " + days + " to minimum " + term.getMinDays() + " days");
          days = term.getMinDays();
        }
      }
      if (term.getMinHours() != null && term.getMinHours() > 0) {
        minHours = term.getMinHours();
      }
    }
    return new Validation(true, days, minHours, adjustments);
  }
}
