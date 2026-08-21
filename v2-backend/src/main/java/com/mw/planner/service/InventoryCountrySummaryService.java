package com.mw.planner.service;

import com.mw.planner.domain.InventoryCountrySummary;
import com.mw.planner.repository.InventoryCountrySummaryRepository;
import com.mw.planner.repository.InventoryRepositoryCustom;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Maintains the {@code inventory_country_summary} read-model that backs the {@code
 * /countries/market-details} endpoint.
 *
 * <p>Counts are recomputed from source (a scoped aggregation over the {@code inventories}
 * collection) rather than incrementally adjusted, which keeps the summary self-healing: a missed or
 * duplicated inventory message can never cause permanent drift because every refresh re-derives the
 * truth for that country.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryCountrySummaryService {

  private final InventoryRepositoryCustom inventoryRepositoryCustom;
  private final InventoryCountrySummaryRepository summaryRepository;

  /**
   * Recompute and persist the inventory count summary for a single country. A country with no
   * inventories is stored with an empty classification map and a zero total so removals are
   * reflected. Safe to call repeatedly; the country name is the document id, so this is an upsert.
   *
   * @param country the country name (matches {@code inventories.location.country}); no-op if blank
   */
  public void refreshSummaryByCountry(String country) {
    if (country == null || country.isBlank()) {
      return;
    }

    Map<String, Long> classificationCounts =
        inventoryRepositoryCustom
            .getInventoryCountsByCountryAndClassification(Set.of(country))
            .getOrDefault(country, Map.of());

    long totalCount = classificationCounts.values().stream().mapToLong(Long::longValue).sum();

    summaryRepository.save(
        InventoryCountrySummary.builder()
            .country(country)
            .classificationCounts(classificationCounts)
            .totalCount(totalCount)
            .updatedAt(Instant.now())
            .build());

    log.debug("Refreshed inventory country summary for '{}' (total={})", country, totalCount);
  }

  /**
   * Rebuild the summary for every country in one pass using a single full aggregation. Intended as
   * a one-time seed after deployment and as an on-demand reconcile if drift is ever suspected. Runs
   * off the request path (triggered from the management endpoint).
   *
   * @return the number of countries written
   */
  public int rebuildAll() {
    log.info("Rebuilding inventory country summary for all countries");
    Map<String, Map<String, Long>> countsByCountry =
        inventoryRepositoryCustom.getInventoryCountsByCountryAndClassification();

    Instant now = Instant.now();
    int written = 0;
    for (Map.Entry<String, Map<String, Long>> entry : countsByCountry.entrySet()) {
      Map<String, Long> classificationCounts = entry.getValue();
      long totalCount = classificationCounts.values().stream().mapToLong(Long::longValue).sum();
      summaryRepository.save(
          InventoryCountrySummary.builder()
              .country(entry.getKey())
              .classificationCounts(classificationCounts)
              .totalCount(totalCount)
              .updatedAt(now)
              .build());
      written++;
    }

    log.info("Rebuilt inventory country summary for {} countries", written);
    return written;
  }
}
