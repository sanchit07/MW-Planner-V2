package com.mw.recommendation.engine.v3.scoring;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.repository.InventoryRepository;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Country-wide total possible ad plays for SOV (PRD §5.3 denominator). One repository read per
 * (country, days) key per process, memoized in memory — computed at most once per run and shared
 * across all scoring threads. Reuses the read-only inventory repository.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TotalAdPlaysProvider {

  private final InventoryRepository inventoryRepository;
  private final ConcurrentHashMap<String, Long> cache = new ConcurrentHashMap<>();

  public Long totalPossibleAdPlays(String country, long days) {
    return cache.computeIfAbsent(
        country + "|" + days,
        key -> {
          List<Inventory> digitals =
              inventoryRepository.findAllActiveDigitalInventoriesByCountry(country);
          long total = 0;
          for (Inventory inv : digitals) {
            Long plays = MeasureFitCalculator.adPlaysForWindow(inv, days);
            if (plays != null) {
              total += plays;
            }
          }
          log.info(
              "v3 totalPossibleAdPlays computed for {} ({} days): {} across {} digital inventories",
              country,
              days,
              total,
              digitals.size());
          return total;
        });
  }
}
