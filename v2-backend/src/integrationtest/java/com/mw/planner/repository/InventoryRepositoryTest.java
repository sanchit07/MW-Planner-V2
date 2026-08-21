package com.mw.planner.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.mw.planner.TestcontainersConfiguration;
import com.mw.planner.domain.Inventory;
import com.mw.planner.dto.CampaignInventoryFilterDTO;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

/**
 * Integration test class for InventoryRepository geospatial filtering functionality. Tests the
 * extended geospatial filtering with Polygon, Circle, and LineString geometries.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InventoryRepositoryTest {

  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private InventoryRepositoryImpl inventoryRepositoryImpl;
  @Autowired private CacheManager cacheManager;

  private Inventory inventory1;
  private Inventory inventory2;
  private Inventory inventory3;
  private Inventory inventory4;

  @BeforeEach
  void setUp() {
    // Clear caches before each test - getInventoryCountsByCountry() is @Cacheable, so a stale
    // entry from a prior test would otherwise leak into tests that mutate the dataset.
    cacheManager
        .getCacheNames()
        .forEach(
            cacheName -> {
              var cache = cacheManager.getCache(cacheName);
              if (cache != null) {
                cache.clear();
              }
            });

    // Clean up database before each test
    inventoryRepository.deleteAll();

    // Create test inventories with different locations
    inventory1 = createInventory("inventory1", -6.2088, 106.8456); // Jakarta
    inventory2 = createInventory("inventory2", -6.2100, 106.8500); // Jakarta nearby
    inventory3 = createInventory("inventory3", -6.2200, 106.8600); // Jakarta further
    inventory4 = createInventory("inventory4", -6.3000, 106.9000); // Jakarta far

    inventoryRepository.saveAll(Arrays.asList(inventory1, inventory2, inventory3, inventory4));
  }

  private Inventory createInventory(String id, double lat, double lng) {
    Inventory inventory = new Inventory();
    inventory.setId(id);
    inventory.setName("Test Inventory " + id);

    // Create location with GeoJsonPoint coordinates
    Inventory.Location location = new Inventory.Location();
    location.setLocationCoordinates(new GeoJsonPoint(lng, lat));
    location.setAddress("Test Address " + id);
    location.setCountry("Indonesia");
    location.setState("Jakarta");
    location.setCity("Jakarta");
    inventory.setLocation(location);

    inventory.setType("DIGITAL");
    inventory.setArchived(false);
    return inventory;
  }

  @Test
  @DisplayName("Filter with Polygon geometry - Should return inventories within polygon")
  void filterWithPolygonGeometry_ShouldReturnInventoriesWithinPolygon() {
    // Given - Create a polygon around Jakarta area
    CampaignInventoryFilterDTO.Geofencing.Geometry polygonGeometry =
        CampaignInventoryFilterDTO.Geofencing.Geometry.builder()
            .type("Polygon")
            .coordinates(
                Arrays.asList(
                    Arrays.asList(106.8400, -6.2000), // Top-left
                    Arrays.asList(106.8600, -6.2000), // Top-right
                    Arrays.asList(106.8600, -6.2300), // Bottom-right
                    Arrays.asList(106.8400, -6.2300), // Bottom-left
                    Arrays.asList(106.8400, -6.2000) // Close polygon
                    ))
            .isIncluded(true)
            .build();

    CampaignInventoryFilterDTO.Geofencing geofencing =
        CampaignInventoryFilterDTO.Geofencing.builder()
            .geometries(Arrays.asList(polygonGeometry))
            .build();

    CampaignInventoryFilterDTO filter =
        CampaignInventoryFilterDTO.builder().geofencing(geofencing).build();

    // When
    Page<Inventory> result =
        inventoryRepositoryImpl.findInventoriesWithFilters(filter, PageRequest.of(0, 10));

    // Then
    assertThat(result.getContent())
        .hasSize(3); // inventory1, inventory2, inventory3 should be included
    assertThat(result.getContent().stream().map(Inventory::getId))
        .containsExactlyInAnyOrder("inventory1", "inventory2", "inventory3");
  }

  @Test
  @DisplayName("Filter with Circle geometry - Should return inventories within circle")
  void filterWithCircleGeometry_ShouldReturnInventoriesWithinCircle() {
    // Given - Create a circle around Jakarta center with radius
    CampaignInventoryFilterDTO.Geofencing.Geometry circleGeometry =
        CampaignInventoryFilterDTO.Geofencing.Geometry.builder()
            .type("Circle")
            .coordinates(
                Arrays.asList(
                    Arrays.asList(106.8456, -6.2088), // Center point
                    Arrays.asList(5000.0) // Radius in meters (5km)
                    ))
            .isIncluded(true)
            .build();

    CampaignInventoryFilterDTO.Geofencing geofencing =
        CampaignInventoryFilterDTO.Geofencing.builder()
            .geometries(Arrays.asList(circleGeometry))
            .build();

    CampaignInventoryFilterDTO filter =
        CampaignInventoryFilterDTO.builder().geofencing(geofencing).build();

    // When
    Page<Inventory> result =
        inventoryRepositoryImpl.findInventoriesWithFilters(filter, PageRequest.of(0, 10));

    // Then
    assertThat(result.getContent())
        .hasSize(3); // inventory1, inventory2, inventory3 should be included
    assertThat(result.getContent().stream().map(Inventory::getId))
        .containsExactlyInAnyOrder("inventory1", "inventory2", "inventory3");
  }

  @Test
  @DisplayName("Filter with LineString geometry - Should return inventories along route")
  void filterWithLineStringGeometry_ShouldReturnInventoriesAlongRoute() {
    // Given - Create a LineString route through Jakarta
    CampaignInventoryFilterDTO.Geofencing.Geometry lineStringGeometry =
        CampaignInventoryFilterDTO.Geofencing.Geometry.builder()
            .type("LineString")
            .coordinates(
                Arrays.asList(
                    Arrays.asList(106.8400, -6.2000), // Start point
                    Arrays.asList(106.8500, -6.2100), // Waypoint 1
                    Arrays.asList(106.8600, -6.2200) // End point
                    ))
            .isIncluded(true)
            .build();

    CampaignInventoryFilterDTO.Geofencing geofencing =
        CampaignInventoryFilterDTO.Geofencing.builder()
            .geometries(Arrays.asList(lineStringGeometry))
            .build();

    CampaignInventoryFilterDTO filter =
        CampaignInventoryFilterDTO.builder().geofencing(geofencing).build();

    // When
    Page<Inventory> result =
        inventoryRepositoryImpl.findInventoriesWithFilters(filter, PageRequest.of(0, 10));

    // Then
    assertThat(result.getContent())
        .hasSize(2); // inventory2, inventory3 should be included (along the route)
    assertThat(result.getContent().stream().map(Inventory::getId))
        .containsExactlyInAnyOrder("inventory2", "inventory3");
  }

  @Test
  @DisplayName("Filter with mixed geometries - Should return inventories from all geometries")
  void filterWithMixedGeometries_ShouldReturnInventoriesFromAllGeometries() {
    // Given - Create mixed geometries (Polygon + Circle)
    CampaignInventoryFilterDTO.Geofencing.Geometry polygonGeometry =
        CampaignInventoryFilterDTO.Geofencing.Geometry.builder()
            .type("Polygon")
            .coordinates(
                Arrays.asList(
                    Arrays.asList(106.8400, -6.2000),
                    Arrays.asList(106.8500, -6.2000),
                    Arrays.asList(106.8500, -6.2100),
                    Arrays.asList(106.8400, -6.2100),
                    Arrays.asList(106.8400, -6.2000)))
            .isIncluded(true)
            .build();

    CampaignInventoryFilterDTO.Geofencing.Geometry circleGeometry =
        CampaignInventoryFilterDTO.Geofencing.Geometry.builder()
            .type("Circle")
            .coordinates(
                Arrays.asList(
                    Arrays.asList(106.8600, -6.2200), // Center point
                    Arrays.asList(10000.0) // Radius in meters (10km)
                    ))
            .isIncluded(true)
            .build();

    CampaignInventoryFilterDTO.Geofencing geofencing =
        CampaignInventoryFilterDTO.Geofencing.builder()
            .geometries(Arrays.asList(polygonGeometry, circleGeometry))
            .build();

    CampaignInventoryFilterDTO filter =
        CampaignInventoryFilterDTO.builder().geofencing(geofencing).build();

    // When
    Page<Inventory> result =
        inventoryRepositoryImpl.findInventoriesWithFilters(filter, PageRequest.of(0, 10));

    // Then
    assertThat(result.getContent()).hasSize(4); // All inventories should be included
    assertThat(result.getContent().stream().map(Inventory::getId))
        .containsExactlyInAnyOrder("inventory1", "inventory2", "inventory3", "inventory4");
  }

  @Test
  @DisplayName("Filter with exclusion geometry - Should exclude inventories from exclusion zone")
  void filterWithExclusionGeometry_ShouldExcludeInventoriesFromExclusionZone() {
    // Given - Create inclusion polygon and exclusion circle
    CampaignInventoryFilterDTO.Geofencing.Geometry inclusionPolygon =
        CampaignInventoryFilterDTO.Geofencing.Geometry.builder()
            .type("Polygon")
            .coordinates(
                Arrays.asList(
                    Arrays.asList(106.8400, -6.2000),
                    Arrays.asList(106.8700, -6.2000),
                    Arrays.asList(106.8700, -6.2300),
                    Arrays.asList(106.8400, -6.2300),
                    Arrays.asList(106.8400, -6.2000)))
            .isIncluded(true)
            .build();

    CampaignInventoryFilterDTO.Geofencing.Geometry exclusionCircle =
        CampaignInventoryFilterDTO.Geofencing.Geometry.builder()
            .type("Circle")
            .coordinates(
                Arrays.asList(
                    Arrays.asList(106.8456, -6.2088), // Center point
                    Arrays.asList(2000.0) // Radius in meters (2km)
                    ))
            .isIncluded(false) // Exclusion zone
            .build();

    CampaignInventoryFilterDTO.Geofencing geofencing =
        CampaignInventoryFilterDTO.Geofencing.builder()
            .geometries(Arrays.asList(inclusionPolygon, exclusionCircle))
            .build();

    CampaignInventoryFilterDTO filter =
        CampaignInventoryFilterDTO.builder().geofencing(geofencing).build();

    // When
    Page<Inventory> result =
        inventoryRepositoryImpl.findInventoriesWithFilters(filter, PageRequest.of(0, 10));

    // Then
    assertThat(result.getContent())
        .hasSize(1); // inventory3 should be included (inclusion - exclusion)
    assertThat(result.getContent().stream().map(Inventory::getId))
        .containsExactlyInAnyOrder("inventory3");
  }

  @Test
  @DisplayName("Filter with unsupported geometry type - Should handle gracefully")
  void filterWithUnsupportedGeometryType_ShouldHandleGracefully() {
    // Given - Create unsupported geometry type
    CampaignInventoryFilterDTO.Geofencing.Geometry unsupportedGeometry =
        CampaignInventoryFilterDTO.Geofencing.Geometry.builder()
            .type("UnsupportedType")
            .coordinates(Arrays.asList(Arrays.asList(106.8456, -6.2088)))
            .isIncluded(true)
            .build();

    CampaignInventoryFilterDTO.Geofencing geofencing =
        CampaignInventoryFilterDTO.Geofencing.builder()
            .geometries(Arrays.asList(unsupportedGeometry))
            .build();

    CampaignInventoryFilterDTO filter =
        CampaignInventoryFilterDTO.builder().geofencing(geofencing).build();

    // When
    Page<Inventory> result =
        inventoryRepositoryImpl.findInventoriesWithFilters(filter, PageRequest.of(0, 10));

    // Then
    assertThat(result.getContent())
        .hasSize(4); // All inventories should be returned (no filtering applied)
    assertThat(result.getContent().stream().map(Inventory::getId))
        .containsExactlyInAnyOrder("inventory1", "inventory2", "inventory3", "inventory4");
  }

  @Test
  @DisplayName("Filter without geofencing - Should return all inventories")
  void filterWithoutGeofencing_ShouldReturnAllInventories() {
    // Given - Filter without geofencing
    CampaignInventoryFilterDTO filter = CampaignInventoryFilterDTO.builder().build();

    // When
    Page<Inventory> result =
        inventoryRepositoryImpl.findInventoriesWithFilters(filter, PageRequest.of(0, 10));

    // Then
    assertThat(result.getContent()).hasSize(4); // All inventories should be returned
    assertThat(result.getContent().stream().map(Inventory::getId))
        .containsExactlyInAnyOrder("inventory1", "inventory2", "inventory3", "inventory4");
  }

  @Test
  @DisplayName("getInventoryCountsByCountry - Should return correct counts grouped by country")
  void getInventoryCountsByCountry_ShouldReturnCountsGroupedByCountry() {
    // Given - Clear existing data and create test inventories with different countries
    inventoryRepository.deleteAll();

    Inventory usInventory1 = createInventoryWithCountry("us1", 40.7128, -74.0060, "United States");
    Inventory usInventory2 = createInventoryWithCountry("us2", 34.0522, -118.2437, "United States");
    Inventory usInventory3 = createInventoryWithCountry("us3", 41.8781, -87.6298, "United States");

    Inventory caInventory1 = createInventoryWithCountry("ca1", 43.6532, -79.3832, "Canada");
    Inventory caInventory2 = createInventoryWithCountry("ca2", 45.5017, -73.5673, "Canada");

    Inventory sgInventory = createInventoryWithCountry("sg1", 1.3521, 103.8198, "Singapore");

    Inventory jpInventory1 = createInventoryWithCountry("jp1", 35.6762, 139.6503, "Japan");

    // Create an inventory with null country (should be filtered out)
    Inventory nullCountryInventory = createInventory("null1", 0.0, 0.0);
    nullCountryInventory.getLocation().setCountry(null);

    inventoryRepository.saveAll(
        Arrays.asList(
            usInventory1,
            usInventory2,
            usInventory3,
            caInventory1,
            caInventory2,
            sgInventory,
            jpInventory1,
            nullCountryInventory));

    // When
    java.util.Map<String, Long> result = inventoryRepositoryImpl.getInventoryCountsByCountry();

    // Then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(4); // 4 countries with valid data

    // Verify counts for each country
    assertThat(result.get("United States")).isEqualTo(3L);
    assertThat(result.get("Canada")).isEqualTo(2L);
    assertThat(result.get("Singapore")).isEqualTo(1L);
    assertThat(result.get("Japan")).isEqualTo(1L);

    // Verify null country is not in results
    assertThat(result).doesNotContainKey(null);

    // Verify total count
    long totalCount = result.values().stream().mapToLong(Long::longValue).sum();
    assertThat(totalCount).isEqualTo(7L); // Total inventories with valid countries
  }

  @Test
  @DisplayName(
      "getInventoryCountsByCountry - Should return empty map when no inventories have country")
  void getInventoryCountsByCountry_WithNoValidCountries_ShouldReturnEmptyMap() {
    // Given - Clear all data
    inventoryRepository.deleteAll();

    // Create inventories with null countries
    Inventory inv1 = createInventory("inv1", 0.0, 0.0);
    inv1.getLocation().setCountry(null);
    Inventory inv2 = createInventory("inv2", 1.0, 1.0);
    inv2.getLocation().setCountry(null);

    inventoryRepository.saveAll(Arrays.asList(inv1, inv2));

    // When
    java.util.Map<String, Long> result = inventoryRepositoryImpl.getInventoryCountsByCountry();

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("getInventoryCountsByCountry - Should return empty map when no inventories exist")
  void getInventoryCountsByCountry_WithNoInventories_ShouldReturnEmptyMap() {
    // Given - Clear all data
    inventoryRepository.deleteAll();

    // When
    java.util.Map<String, Long> result = inventoryRepositoryImpl.getInventoryCountsByCountry();

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("getInventoryCountsByCountry - Should handle special characters in country names")
  void getInventoryCountsByCountry_WithSpecialCharacters_ShouldHandleCorrectly() {
    // Given - Clear existing data
    inventoryRepository.deleteAll();

    Inventory inv1 =
        createInventoryWithCountry("special1", 48.8566, 2.3522, "Côte d'Ivoire"); // Accents
    Inventory inv2 =
        createInventoryWithCountry("special2", 51.5074, -0.1278, "São Tomé and Príncipe");
    Inventory inv3 = createInventoryWithCountry("special3", 35.6762, 139.6503, "日本"); // Japanese

    inventoryRepository.saveAll(Arrays.asList(inv1, inv2, inv3));

    // When
    java.util.Map<String, Long> result = inventoryRepositoryImpl.getInventoryCountsByCountry();

    // Then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(3);
    assertThat(result.get("Côte d'Ivoire")).isEqualTo(1L);
    assertThat(result.get("São Tomé and Príncipe")).isEqualTo(1L);
    assertThat(result.get("日本")).isEqualTo(1L);
  }

  private Inventory createInventoryWithCountry(String id, double lat, double lng, String country) {
    Inventory inventory = createInventory(id, lat, lng);
    inventory.getLocation().setCountry(country);
    return inventory;
  }

  private Inventory createInventoryWithCountryAndClassification(
      String id, double lat, double lng, String country, String classification) {
    Inventory inventory = createInventoryWithCountry(id, lat, lng, country);
    inventory.setClassification(classification);
    return inventory;
  }

  @Test
  @DisplayName(
      "getInventoryCountsByCountryAndClassification(countryNames) - Should scope counts to the"
          + " requested countries only")
  void getInventoryCountsByCountryAndClassification_WithCountryNames_ShouldScopeToRequested() {
    // Given
    inventoryRepository.deleteAll();
    inventoryRepository.saveAll(
        Arrays.asList(
            createInventoryWithCountryAndClassification(
                "us1", 40.7128, -74.0060, "United States", "Billboard"),
            createInventoryWithCountryAndClassification(
                "us2", 34.0522, -118.2437, "United States", "Transit"),
            createInventoryWithCountryAndClassification(
                "ca1", 43.6532, -79.3832, "Canada", "Billboard"),
            // Singapore exists but is NOT requested - must be excluded by the $in scoping
            createInventoryWithCountryAndClassification(
                "sg1", 1.3521, 103.8198, "Singapore", "Billboard")));

    // When
    java.util.Map<String, java.util.Map<String, Long>> result =
        inventoryRepositoryImpl.getInventoryCountsByCountryAndClassification(
            List.of("United States", "Canada"));

    // Then - only requested countries present
    assertThat(result).isNotNull();
    assertThat(result).containsOnlyKeys("United States", "Canada");
    assertThat(result.get("United States"))
        .containsOnly(entry("Billboard", 1L), entry("Transit", 1L));
    assertThat(result.get("Canada")).containsOnly(entry("Billboard", 1L));
    assertThat(result).doesNotContainKey("Singapore");
  }

  @Test
  @DisplayName(
      "getInventoryCountsByCountryAndClassification(countryNames) - Should bucket null"
          + " classification under 'Unknown'")
  void getInventoryCountsByCountryAndClassification_WithNullClassification_ShouldBucketUnknown() {
    // Given
    inventoryRepository.deleteAll();
    inventoryRepository.saveAll(
        Arrays.asList(
            createInventoryWithCountryAndClassification(
                "us1", 40.7128, -74.0060, "United States", "Billboard"),
            // classification null -> should fall back to "Unknown"
            createInventoryWithCountryAndClassification(
                "us2", 34.0522, -118.2437, "United States", null)));

    // When
    java.util.Map<String, java.util.Map<String, Long>> result =
        inventoryRepositoryImpl.getInventoryCountsByCountryAndClassification(
            List.of("United States"));

    // Then
    assertThat(result).containsOnlyKeys("United States");
    assertThat(result.get("United States"))
        .containsOnly(entry("Billboard", 1L), entry("Unknown", 1L));
  }

  @Test
  @DisplayName(
      "getInventoryCountsByCountryAndClassification(countryNames) - Should return empty map when no"
          + " country matches")
  void getInventoryCountsByCountryAndClassification_WithNoMatchingCountry_ShouldReturnEmptyMap() {
    // Given
    inventoryRepository.deleteAll();
    inventoryRepository.save(
        createInventoryWithCountryAndClassification(
            "us1", 40.7128, -74.0060, "United States", "Billboard"));

    // When
    java.util.Map<String, java.util.Map<String, Long>> result =
        inventoryRepositoryImpl.getInventoryCountsByCountryAndClassification(List.of("Atlantis"));

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // Date-range (sellingTerm.minDays) filtering
  // ---------------------------------------------------------------------------

  /**
   * Replaces the default geo dataset with five inventories that exercise the minDays filter:
   * minDays 1, 3, 4; minDays null (sellingTerm present); sellingTerm null entirely.
   */
  private void saveMinDaysDataset() {
    inventoryRepository.deleteAll();
    inventoryRepository.saveAll(
        List.of(
            createInventoryWithMinDays("min1", 1),
            createInventoryWithMinDays("min3", 3),
            createInventoryWithMinDays("min4", 4),
            createInventoryWithMinDays("minNull", null), // sellingTerm present, minDays null
            createInventoryWithoutSellingTerm("noTerm"))); // sellingTerm absent
  }

  private Inventory createInventoryWithMinDays(String id, Integer minDays) {
    Inventory inventory = createInventoryWithoutSellingTerm(id);
    inventory.setSellingTerm(Inventory.SellingTerm.builder().minDays(minDays).build());
    return inventory;
  }

  private Inventory createInventoryWithoutSellingTerm(String id) {
    Inventory inventory = new Inventory();
    inventory.setId(id);
    inventory.setName("Test Inventory " + id);
    inventory.setType("DIGITAL");
    inventory.setArchived(false);
    return inventory;
  }

  @Test
  @DisplayName(
      "Date range (inclusive) - matches minDays <= duration and allows null/missing minDays")
  void filterWithDateRange_ShouldMatchMinDaysWithinDurationAndAllowNulls() {
    // Given - 2026-01-01..2026-01-03 inclusive => 3 days
    saveMinDaysDataset();
    CampaignInventoryFilterDTO filter =
        CampaignInventoryFilterDTO.builder()
            .startDate(LocalDate.of(2026, 1, 1))
            .endDate(LocalDate.of(2026, 1, 3))
            .build();

    // When
    Page<Inventory> result =
        inventoryRepositoryImpl.findInventoriesWithFilters(filter, PageRequest.of(0, 10));

    // Then - minDays 1 and 3 qualify (3 is the inclusive boundary); null and missing minDays are
    // allowed through (no constraint); only minDays 4 is excluded (exceeds duration)
    assertThat(result.getContent().stream().map(Inventory::getId))
        .containsExactlyInAnyOrder("min1", "min3", "minNull", "noTerm");
  }

  @Test
  @DisplayName("Only startDate present - Should not apply the date filter")
  void filterWithStartDateOnly_ShouldNotApplyDateFilter() {
    // Given
    saveMinDaysDataset();
    CampaignInventoryFilterDTO filter =
        CampaignInventoryFilterDTO.builder().startDate(LocalDate.of(2026, 1, 1)).build();

    // When
    Page<Inventory> result =
        inventoryRepositoryImpl.findInventoriesWithFilters(filter, PageRequest.of(0, 10));

    // Then - all five returned, identical to no date filter
    assertThat(result.getContent().stream().map(Inventory::getId))
        .containsExactlyInAnyOrder("min1", "min3", "min4", "minNull", "noTerm");
  }

  @Test
  @DisplayName("Invalid range (endDate before startDate) - Should be a no-op")
  void filterWithInvalidRange_ShouldBeNoOp() {
    // Given - end before start
    saveMinDaysDataset();
    CampaignInventoryFilterDTO filter =
        CampaignInventoryFilterDTO.builder()
            .startDate(LocalDate.of(2026, 1, 3))
            .endDate(LocalDate.of(2026, 1, 1))
            .build();

    // When
    Page<Inventory> result =
        inventoryRepositoryImpl.findInventoriesWithFilters(filter, PageRequest.of(0, 10));

    // Then - no criterion applied, all five returned
    assertThat(result.getContent().stream().map(Inventory::getId))
        .containsExactlyInAnyOrder("min1", "min3", "min4", "minNull", "noTerm");
  }

  @Test
  @DisplayName("Date range combined with name filter - Should AND both criteria")
  void filterWithDateRangeAndName_ShouldApplyBoth() {
    // Given - name filter narrows to "min3" and "min4"; date range keeps minDays <= 3
    saveMinDaysDataset();
    CampaignInventoryFilterDTO filter =
        CampaignInventoryFilterDTO.builder()
            .name("min3")
            .startDate(LocalDate.of(2026, 1, 1))
            .endDate(LocalDate.of(2026, 1, 3))
            .build();

    // When
    Page<Inventory> result =
        inventoryRepositoryImpl.findInventoriesWithFilters(filter, PageRequest.of(0, 10));

    // Then - only "min3" satisfies both
    assertThat(result.getContent().stream().map(Inventory::getId)).containsExactly("min3");
  }
}
