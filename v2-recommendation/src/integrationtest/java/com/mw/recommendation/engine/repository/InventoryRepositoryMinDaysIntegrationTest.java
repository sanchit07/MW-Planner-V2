package com.mw.recommendation.engine.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.mw.recommendation.engine.TestcontainersConfiguration;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.BrowseInventoryRequestDTO;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Proves the lenient-null minDays semantics against a real MongoDB. Null/missing minDays (or a
 * missing sellingTerm) passes the filter; only inventories whose concrete minDays exceeds the
 * campaign duration are excluded. Only an end-to-end query can validate the interaction of the
 * $or/$exists clauses with real documents.
 */
@SpringBootTest
@ContextConfiguration(classes = TestcontainersConfiguration.class)
@DisplayName("InventoryRepository - minDays availability filter (Testcontainers)")
class InventoryRepositoryMinDaysIntegrationTest {

  private static final String COUNTRY = "MinDaysTestland";

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
    BrowseInventoryRequestDTO request = baseRequest();
    request.setStartDate(LocalDate.of(2025, 1, 1));
    request.setEndDate(LocalDate.of(2025, 1, 3)); // inclusive duration = 3

    Page<Inventory> page =
        inventoryRepository.findActiveInventoriesByCountryPaginated(
            COUNTRY,
            null,
            null,
            null,
            null,
            null,
            null,
            request,
            null,
            PageRequest.of(0, 50),
            null);

    List<String> ids =
        page.getContent().stream().map(Inventory::getId).collect(Collectors.toList());

    assertTrue(ids.contains(minDays2Id), "minDays=2 must be returned");
    assertTrue(ids.contains(minDays3Id), "minDays=3 (boundary) must be returned");
    assertFalse(ids.contains(minDays5Id), "minDays=5 must be excluded");
    assertTrue(ids.contains(minDaysNullId), "minDays=null now passes (lenient)");
    assertTrue(ids.contains(noSellingTermId), "missing sellingTerm now passes (lenient)");
    assertEquals(4, ids.size(), "minDays 2, 3, null and missing-sellingTerm satisfy the window");
  }

  @Test
  @DisplayName("no startDate → filter off, all seeded inventories returned")
  void noDates_filterDisabled() {
    BrowseInventoryRequestDTO request = baseRequest();
    // dates left null → getDurationDays() returns null → filter not applied

    Page<Inventory> page =
        inventoryRepository.findActiveInventoriesByCountryPaginated(
            COUNTRY,
            null,
            null,
            null,
            null,
            null,
            null,
            request,
            null,
            PageRequest.of(0, 50),
            null);

    List<String> ids =
        page.getContent().stream().map(Inventory::getId).collect(Collectors.toList());

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

  private BrowseInventoryRequestDTO baseRequest() {
    BrowseInventoryRequestDTO request = new BrowseInventoryRequestDTO();
    request.setCountry(COUNTRY);
    return request;
  }

  /**
   * Builds a non-archived inventory in COUNTRY with a positive CPM price (passes base criteria).
   */
  private Inventory baseInventory() {
    Inventory inv = new Inventory();
    inv.setInventoryId("mindays-it-" + UUID.randomUUID().toString().substring(0, 8));
    inv.setName("MinDays IT inventory");
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
