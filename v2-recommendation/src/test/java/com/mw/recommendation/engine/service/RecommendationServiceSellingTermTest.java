package com.mw.recommendation.engine.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.dto.BrowseInventoryRequestDTO;
import com.mw.recommendation.engine.dto.PaginatedRecommendationResponseDTO;
import com.mw.recommendation.engine.mapper.InventoryItemMapper;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the existing {@code Inventory.sellingTerm} domain field is surfaced, additively, in
 * the inventory-bearing API responses:
 *
 * <ul>
 *   <li>browse ({@code POST /campaigns/{id}/browse}) maps it live from the in-scope Inventory;
 *   <li>results / selected-results ({@code convertToRecommendedInventory}) map it from the
 *       denormalized snapshot on {@link RecommendationResult}.
 * </ul>
 *
 * Both paths are null-safe: a missing sellingTerm (incl. legacy snapshots written before the field
 * existed) yields a null response field, never an error.
 */
class RecommendationServiceSellingTermTest {

  private static Inventory.SellingTerm sampleSellingTerm() {
    return Inventory.SellingTerm.builder()
        .leadDays(3)
        .minHours(24)
        .minDays(7)
        .dayPartGroups(
            Map.of(
                "morning",
                Inventory.DayPartGroup.builder().start("06:00:00").end("12:00:00").build()))
        .build();
  }

  // ─── browse path (live read from Inventory) ─────────────────────────────────

  @Test
  void buildBrowseRecommendedInventory_includesSellingTermFromInventory() throws Exception {
    Inventory.SellingTerm sellingTerm = sampleSellingTerm();

    Inventory inventory = new Inventory();
    inventory.setInventoryId("inv-1");
    inventory.setName("Browse Billboard");
    inventory.setSellingTerm(sellingTerm);

    BrowseInventoryRequestDTO request = new BrowseInventoryRequestDTO();
    request.setStartDate(LocalDate.of(2026, 6, 1));
    request.setEndDate(LocalDate.of(2026, 6, 7));

    PaginatedRecommendationResponseDTO.RecommendedInventory dto =
        buildBrowse(inventory, 7, request);

    assertThat(dto.getInventoryDetails()).isNotNull();
    assertThat(dto.getInventoryDetails().getSellingTerm()).isEqualTo(sellingTerm);
    // All business-meaningful subfields are exposed, including the nested dayPartGroups.
    assertThat(dto.getInventoryDetails().getSellingTerm().getLeadDays()).isEqualTo(3);
    assertThat(dto.getInventoryDetails().getSellingTerm().getMinHours()).isEqualTo(24);
    assertThat(dto.getInventoryDetails().getSellingTerm().getMinDays()).isEqualTo(7);
    assertThat(dto.getInventoryDetails().getSellingTerm().getDayPartGroups())
        .containsKey("morning");
  }

  @Test
  void buildBrowseRecommendedInventory_nullSellingTerm_yieldsNull() throws Exception {
    Inventory inventory = new Inventory();
    inventory.setInventoryId("inv-2");
    inventory.setName("No-Term Billboard");
    inventory.setSellingTerm(null);

    BrowseInventoryRequestDTO request = new BrowseInventoryRequestDTO();
    request.setStartDate(LocalDate.of(2026, 6, 1));
    request.setEndDate(LocalDate.of(2026, 6, 7));

    PaginatedRecommendationResponseDTO.RecommendedInventory dto =
        buildBrowse(inventory, 7, request);

    assertThat(dto.getInventoryDetails()).isNotNull();
    assertThat(dto.getInventoryDetails().getSellingTerm()).isNull();
  }

  // ─── results / selected-results path (read from persisted snapshot) ─────────

  @Test
  void convertToRecommendedInventory_mapsSellingTermFromSnapshot() throws Exception {
    Inventory.SellingTerm sellingTerm = sampleSellingTerm();

    RecommendationResult result =
        RecommendationResult.builder()
            .inventoryId("inv-3")
            .name("Snapshot Billboard")
            .inventoryDetails(
                RecommendationResult.InventoryDetails.builder()
                    .type("OOH")
                    .sellingTerm(sellingTerm)
                    .build())
            .build();

    PaginatedRecommendationResponseDTO.RecommendedInventory dto = convert(result);

    assertThat(dto.getInventoryDetails()).isNotNull();
    assertThat(dto.getInventoryDetails().getSellingTerm()).isEqualTo(sellingTerm);
  }

  @Test
  void convertToRecommendedInventory_legacySnapshotWithoutSellingTerm_yieldsNull()
      throws Exception {
    // A snapshot written before sellingTerm existed: the field deserializes as null (Mongo maps an
    // absent field to the Java default). The mapper must not crash and must emit a null field.
    RecommendationResult result =
        RecommendationResult.builder()
            .inventoryId("inv-4")
            .name("Legacy Billboard")
            .inventoryDetails(RecommendationResult.InventoryDetails.builder().type("OOH").build())
            .build();

    PaginatedRecommendationResponseDTO.RecommendedInventory dto = convert(result);

    assertThat(dto.getInventoryDetails()).isNotNull();
    assertThat(dto.getInventoryDetails().getSellingTerm()).isNull();
  }

  // ─── persisted snapshot field round-trip ────────────────────────────────────

  @Test
  void recommendationResultInventoryDetails_persistsSellingTerm() {
    Inventory.SellingTerm sellingTerm = sampleSellingTerm();

    RecommendationResult.InventoryDetails details =
        RecommendationResult.InventoryDetails.builder().sellingTerm(sellingTerm).build();

    assertThat(details.getSellingTerm()).isEqualTo(sellingTerm);
  }

  // ─── reflection helpers (mirrors RecommendationServiceOperatingTimesTest) ───

  private PaginatedRecommendationResponseDTO.RecommendedInventory convert(
      RecommendationResult result) throws Exception {
    Method m =
        RecommendationService.class.getDeclaredMethod(
            "convertToRecommendedInventory", RecommendationResult.class);
    m.setAccessible(true);
    return (PaginatedRecommendationResponseDTO.RecommendedInventory) m.invoke(newService(), result);
  }

  private PaginatedRecommendationResponseDTO.RecommendedInventory buildBrowse(
      Inventory inventory, long totalDays, BrowseInventoryRequestDTO request) {
    // Browse mapping moved to InventoryItemMapper (RecommendationService.browseInventories
    // delegates
    // to it); the projection is byte-identical, so this exercises the same logic directly.
    return new InventoryItemMapper()
        .toRecommendedInventory(inventory, totalDays, request.getStartDate(), request.getEndDate());
  }

  /** A service with all collaborators null — the mappers under test are pure transforms. */
  private RecommendationService newService() {
    return new RecommendationService(
        null, null, null, null, null, null, null, null, null, null, null);
  }
}
