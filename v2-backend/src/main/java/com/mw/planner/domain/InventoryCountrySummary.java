package com.mw.planner.domain;

import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Materialized per-country inventory count summary. Read-model backing the {@code
 * /countries/market-details} endpoint so it never has to aggregate the whole {@code inventories}
 * collection at request time.
 *
 * <p>The country name is the document {@code _id}, matching the raw {@code location.country} values
 * stored on inventories, which makes lookups and upserts O(1) and unique per country. Kept fresh by
 * {@code InventoryCountrySummaryService} whenever an inventory message is processed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "inventory_country_summary")
public class InventoryCountrySummary {

  /** Country name (matches inventories.location.country); used as the document id. */
  @Id private String country;

  /** Inventory count per classification, e.g. {@code {"Classic": 125050, "Digital": 38532}}. */
  private Map<String, Long> classificationCounts;

  /** Total inventory count for the country (sum of {@link #classificationCounts} values). */
  private long totalCount;

  /** When this summary was last recomputed. */
  private Instant updatedAt;
}
