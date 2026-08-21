package com.mw.recommendation.engine.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.mw.recommendation.engine.TestcontainersConfiguration;
import com.mw.recommendation.engine.domain.Inventory;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Proves the lenient-null minDays semantics on the recommendation submission fetch path
 * (findActiveInventoriesByCountryWithGeographyTargeting) against a real MongoDB. Null/missing
 * minDays (or a missing sellingTerm) passes the filter; only inventories whose concrete minDays
 * exceeds the campaign duration are excluded. Only an end-to-end query can validate the $or/$exists
 * clauses against real documents. Mirrors the browse-path
 * InventoryRepositoryMinDaysIntegrationTest.
 */
@SpringBootTest
@ContextConfiguration(classes = TestcontainersConfiguration.class)
@DisplayName("InventoryRepository - geo-targeting fetch minDays filter (Testcontainers)")
class InventoryRepositoryGeoTargetingMinDaysIntegrationTest {

  private static final String COUNTRY = "GeoMinDaysTestland";

  @Autowired private InventoryRepository inventoryRepository;

  private String minDays2Id;
  private String minDays3Id;
  private String minDays5Id;
  private String minDaysNullId;
  private String noSellingTermId;

  @BeforeEach
  void setUp() {
    minDays2Id = save(inventoryWithMinDays(2));
    minDays3Id = save(inventoryWithMinDays(3));
    minDays5Id = save(inventoryWithMinDays(5));
    minDaysNullId = save(inventoryWithMinDays(null));
    noSellingTermId = save(inventoryWithoutSellingTerm());
  }

  @AfterEach
  void tearDown() {
    List.of(minDays2Id, minDays3Id, minDays5Id, minDaysNullId, noSellingTermId)
        .forEach(inventoryRepository::deleteById);
  }

  @Test
  @DisplayName("3-day window returns minDays <= 3 plus null/missing; only minDays > 3 excluded")
  void threeDayWindow_appliesLenientFilter() {
    List<Inventory> result =
        inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            COUNTRY, null, null, null, null, null, null, 3L, null, null, null, null, null,
            false); // inclusive duration = 3

    List<String> ids = result.stream().map(Inventory::getId).collect(Collectors.toList());

    assertTrue(ids.contains(minDays2Id), "minDays=2 must be returned");
    assertTrue(ids.contains(minDays3Id), "minDays=3 (boundary) must be returned");
    assertFalse(ids.contains(minDays5Id), "minDays=5 must be excluded");
    assertTrue(ids.contains(minDaysNullId), "minDays=null now passes (lenient)");
    assertTrue(ids.contains(noSellingTermId), "missing sellingTerm now passes (lenient)");
    assertEquals(4, ids.size(), "minDays 2, 3, null and missing-sellingTerm satisfy the window");
  }

  @Test
  @DisplayName("durationDays null → filter off, all seeded inventories returned")
  void noDuration_filterDisabled() {
    List<Inventory> result =
        inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            COUNTRY, null, null, null, null, null, null, null, null, null, null, null, null, false);

    List<String> ids = result.stream().map(Inventory::getId).collect(Collectors.toList());

    assertTrue(ids.contains(minDays2Id));
    assertTrue(ids.contains(minDays3Id));
    assertTrue(ids.contains(minDays5Id));
    assertTrue(ids.contains(minDaysNullId), "null minDays included when filter off");
    assertTrue(ids.contains(noSellingTermId), "missing sellingTerm included when filter off");
    assertEquals(5, ids.size(), "all five seeded inventories returned");
  }

  private String save(Inventory inventory) {
    return inventoryRepository.save(inventory).getId();
  }

  /**
   * Builds a non-archived inventory in COUNTRY with a positive CPM price (passes base criteria).
   */
  private Inventory baseInventory() {
    Inventory inv = new Inventory();
    inv.setInventoryId("geo-mindays-it-" + UUID.randomUUID().toString().substring(0, 8));
    inv.setName("Geo MinDays IT inventory");
    inv.setArchived(false);
    inv.setLocationHierarchy(Inventory.LocationHierarchy.builder().countryName(COUNTRY).build());
    inv.setPrices(List.of(Inventory.PriceModel.builder().cpm(5.0).currency("USD").build()));
    return inv;
  }

  private Inventory inventoryWithMinDays(Integer minDays) {
    Inventory inv = baseInventory();
    inv.setSellingTerm(Inventory.SellingTerm.builder().minDays(minDays).build());
    return inv;
  }

  private Inventory inventoryWithoutSellingTerm() {
    return baseInventory(); // sellingTerm left null
  }
}
