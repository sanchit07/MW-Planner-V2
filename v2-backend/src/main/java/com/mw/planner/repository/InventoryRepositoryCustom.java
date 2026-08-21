package com.mw.planner.repository;

import com.mw.planner.domain.Inventory;
import com.mw.planner.dto.CampaignInventoryFilterDTO;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InventoryRepositoryCustom {

  /**
   * Atomically upsert an inventory keyed on its natural key (externalId when present, else
   * referenceId). Performs a single MongoDB findAndModify with upsert=true so concurrent
   * inventory-sync messages for the same inventory can never create duplicate documents. Only
   * non-null fields on the incoming entity are {@code $set} (partial update — a message missing a
   * field never wipes existing data); audit fields are managed explicitly since a direct
   * MongoTemplate upsert bypasses {@code @EnableMongoAuditing}.
   *
   * @param inventory the converted inventory carrying the natural key and the fields to apply
   * @return the upserted inventory (with its persisted {@code _id})
   */
  Inventory upsertByNaturalKey(Inventory inventory);

  Page<Inventory> findInventoriesWithFilters(CampaignInventoryFilterDTO filter, Pageable pageable);

  /**
   * Count total inventories matching filter criteria without fetching data. Uses MongoDB $count
   * aggregation for optimal performance.
   *
   * @param filter Filter criteria
   * @return Total count of matching inventories
   */
  long countInventoriesWithFilters(CampaignInventoryFilterDTO filter);

  List<Inventory> findInventoriesByIdsWithComplianceCheck(
      List<String> inventoryIds, CampaignInventoryFilterDTO filter);

  /**
   * Find inventories with filters, but only fetch required fields for bulk operations. This is
   * optimized to reduce data transfer and improve performance.
   *
   * @param filter Filter criteria
   * @return List of inventories matching the filter
   */
  List<Inventory> findInventoriesWithFiltersForBulkOperation(CampaignInventoryFilterDTO filter);

  /**
   * Get inventory counts grouped by country name. Returns a map where key is country name and value
   * is the count of inventories for that country.
   *
   * @return Map of country name to inventory count
   */
  Map<String, Long> getInventoryCountsByCountry();

  /**
   * Get inventory counts grouped by country name, scoped to the given country names. Uses an index
   * seek on location.country instead of scanning the whole collection, so prefer this overload
   * whenever the caller already knows which countries it needs.
   *
   * @param countryNames country names to count inventories for
   * @return Map of country name to inventory count (countries with no inventories are absent)
   */
  Map<String, Long> getInventoryCountsByCountry(Collection<String> countryNames);

  /**
   * Get inventory counts grouped by country name and classification. Returns a nested map where the
   * outer key is country name and the inner map is classification → count.
   *
   * @return Map of country name to (classification → count)
   */
  Map<String, Map<String, Long>> getInventoryCountsByCountryAndClassification();

  /**
   * Get inventory counts grouped by country name and classification, scoped to the given country
   * names. Uses an index seek on location.country instead of scanning the whole collection, so
   * prefer this overload whenever the caller already knows which countries it needs.
   *
   * @param countryNames country names to count inventories for
   * @return Map of country name to (classification → count) (countries with no inventories are
   *     absent)
   */
  Map<String, Map<String, Long>> getInventoryCountsByCountryAndClassification(
      Collection<String> countryNames);
}
