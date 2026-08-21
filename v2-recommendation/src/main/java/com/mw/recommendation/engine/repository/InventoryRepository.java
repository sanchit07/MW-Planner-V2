package com.mw.recommendation.engine.repository;

import com.mw.recommendation.engine.domain.Inventory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/** Repository for Inventory entities. Provides basic CRUD operations for inventory data. */
@Repository
public interface InventoryRepository
    extends MongoRepository<Inventory, String>, InventoryRepositoryCustom {

  /**
   * Find inventory by inventory ID. Uses {@code findFirst} (LIMIT 1) rather than a strict single
   * match so that duplicate {@code inventoryId} documents do not raise
   * IncorrectResultSizeDataAccessException during lookup.
   */
  Optional<Inventory> findFirstByInventoryId(String inventoryId);

  /** Delete inventory by inventory ID */
  void deleteByInventoryId(String inventoryId);

  /** Find all inventories by inventory IDs (batch load for schedule generation). */
  @Query("{ 'inventoryId': { $in: ?0 } }")
  List<Inventory> findByInventoryIdIn(List<String> inventoryIds);

  /** Find inventory by reference ID */
  Optional<Inventory> findByReferenceId(String referenceId);

  /** Find all inventories by reference IDs in a single bulk query */
  @Query("{ 'referenceId': { $in: ?0 } }")
  List<Inventory> findByReferenceIdIn(List<String> referenceIds);

  /**
   * Find active inventories by country, excluding specified inventory IDs and archived inventories.
   * Filters are applied at database level for optimal performance.
   *
   * @param countryName Country name to filter by (from locationHierarchy.countryName)
   * @param excludedIds List of inventory IDs to exclude (checks both inventoryId and referenceId)
   * @return List of matching inventories
   */
  @Query(
      value =
          "{"
              + "  'locationHierarchy.countryName': ?0,"
              + "  $or: ["
              + "    { 'archived': { $exists: false } },"
              + "    { 'archived': false },"
              + "    { 'archived': null }"
              + "  ],"
              + "  $and: ["
              + "    { 'inventoryId': { $nin: ?1 } },"
              + "    { 'referenceId': { $nin: ?1 } }"
              + "  ]"
              + "}")
  List<Inventory> findActiveInventoriesByCountryExcludingIds(
      String countryName, List<String> excludedIds);

  /**
   * Find active inventories by country without exclusions. Filters are applied at database level
   * for optimal performance.
   *
   * @param countryName Country name to filter by (from locationHierarchy.countryName)
   * @return List of matching inventories
   */
  @Query(
      value =
          "{"
              + "  'locationHierarchy.countryName': ?0,"
              + "  $or: ["
              + "    { 'archived': { $exists: false } },"
              + "    { 'archived': false },"
              + "    { 'archived': null }"
              + "  ]"
              + "}")
  List<Inventory> findActiveInventoriesByCountry(String countryName);

  /**
   * Find all active digital inventories (inventories with digitalFields). Used for calculating
   * total_possible_ad_plays for SOV calculations.
   *
   * @return List of digital inventories
   */
  @Query(
      value =
          "{"
              + "  'digitalFields': { $exists: true, $ne: null },"
              + "  $or: ["
              + "    { 'archived': { $exists: false } },"
              + "    { 'archived': false },"
              + "    { 'archived': null }"
              + "  ]"
              + "}")
  List<Inventory> findAllActiveDigitalInventories();

  /**
   * Find all active digital inventories by country (inventories with digitalFields). Used for
   * calculating total_possible_ad_plays for SOV calculations (country-specific).
   *
   * @param countryName Country name to filter by (from locationHierarchy.countryName)
   * @return List of digital inventories for the country
   */
  @Query(
      value =
          "{"
              + "  'digitalFields': { $exists: true, $ne: null },"
              + "  'locationHierarchy.countryName': ?0,"
              + "  $or: ["
              + "    { 'archived': { $exists: false } },"
              + "    { 'archived': false },"
              + "    { 'archived': null }"
              + "  ]"
              + "}")
  List<Inventory> findAllActiveDigitalInventoriesByCountry(String countryName);
}
