package com.mw.recommendation.engine.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.dto.BrowseInventoryRequestDTO;
import com.mw.recommendation.engine.dto.PaginatedRecommendationResponseDTO;
import com.mw.recommendation.engine.mapper.InventoryItemMapper;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@code Inventory.panels} is surfaced in inventoryDetails so consumers can derive
 * the true screen count (panels.size()) instead of using playerCount.
 *
 * <ul>
 *   <li>browse path maps panels live from the Inventory domain object;
 *   <li>results path maps panels from the persisted RecommendationResult snapshot.
 * </ul>
 *
 * Both paths are null-safe: missing panels yields null, never an error.
 */
class RecommendationServicePanelsTest {

  private static List<Inventory.Panel> samplePanels() {
    Inventory.Panel portrait =
        Inventory.Panel.builder().pixelWidth(1080).pixelHeight(1920).size(Inventory.Size.L).build();
    Inventory.Panel landscape =
        Inventory.Panel.builder().pixelWidth(1920).pixelHeight(1080).size(Inventory.Size.L).build();
    return Arrays.asList(portrait, landscape);
  }

  // ─── browse path (live read from Inventory) ─────────────────────────────────

  @Test
  void buildBrowseRecommendedInventory_includesPanelsFromInventory() throws Exception {
    List<Inventory.Panel> panels = samplePanels();

    Inventory inventory = new Inventory();
    inventory.setInventoryId("inv-1");
    inventory.setName("Multi-Panel Billboard");
    inventory.setPanels(panels);

    BrowseInventoryRequestDTO request = new BrowseInventoryRequestDTO();
    request.setStartDate(LocalDate.of(2026, 6, 1));
    request.setEndDate(LocalDate.of(2026, 6, 7));

    PaginatedRecommendationResponseDTO.RecommendedInventory dto =
        buildBrowse(inventory, 7, request);

    assertThat(dto.getInventoryDetails()).isNotNull();
    assertThat(dto.getInventoryDetails().getPanels()).hasSize(2);
    assertThat(dto.getInventoryDetails().getPanels().get(0).getPixelWidth()).isEqualTo(1080);
    assertThat(dto.getInventoryDetails().getPanels().get(1).getPixelWidth()).isEqualTo(1920);
  }

  @Test
  void buildBrowseRecommendedInventory_nullPanels_yieldsNull() throws Exception {
    Inventory inventory = new Inventory();
    inventory.setInventoryId("inv-2");
    inventory.setName("Single-Panel Billboard");
    inventory.setPanels(null);

    BrowseInventoryRequestDTO request = new BrowseInventoryRequestDTO();
    request.setStartDate(LocalDate.of(2026, 6, 1));
    request.setEndDate(LocalDate.of(2026, 6, 7));

    PaginatedRecommendationResponseDTO.RecommendedInventory dto =
        buildBrowse(inventory, 7, request);

    assertThat(dto.getInventoryDetails()).isNotNull();
    assertThat(dto.getInventoryDetails().getPanels()).isNull();
  }

  // ─── results path (read from persisted RecommendationResult snapshot) ────────

  @Test
  void convertToRecommendedInventory_mapsPanelsFromSnapshot() throws Exception {
    List<Inventory.Panel> panels = samplePanels();

    RecommendationResult result =
        RecommendationResult.builder()
            .inventoryId("inv-3")
            .name("Snapshot Billboard")
            .inventoryDetails(
                RecommendationResult.InventoryDetails.builder().type("OOH").panels(panels).build())
            .build();

    PaginatedRecommendationResponseDTO.RecommendedInventory dto = convert(result);

    assertThat(dto.getInventoryDetails()).isNotNull();
    assertThat(dto.getInventoryDetails().getPanels()).hasSize(2);
    assertThat(dto.getInventoryDetails().getPanels().get(0).getPixelWidth()).isEqualTo(1080);
    assertThat(dto.getInventoryDetails().getPanels().get(1).getPixelWidth()).isEqualTo(1920);
  }

  @Test
  void convertToRecommendedInventory_legacySnapshotWithoutPanels_yieldsNull() throws Exception {
    RecommendationResult result =
        RecommendationResult.builder()
            .inventoryId("inv-4")
            .name("Legacy Billboard")
            .inventoryDetails(RecommendationResult.InventoryDetails.builder().type("OOH").build())
            .build();

    PaginatedRecommendationResponseDTO.RecommendedInventory dto = convert(result);

    assertThat(dto.getInventoryDetails()).isNotNull();
    assertThat(dto.getInventoryDetails().getPanels()).isNull();
  }

  // ─── persisted snapshot field round-trip ────────────────────────────────────

  @Test
  void recommendationResultInventoryDetails_persistsPanels() {
    List<Inventory.Panel> panels = samplePanels();

    RecommendationResult.InventoryDetails details =
        RecommendationResult.InventoryDetails.builder().panels(panels).build();

    assertThat(details.getPanels()).hasSize(2);
    assertThat(details.getPanels().get(0).getPixelWidth()).isEqualTo(1080);
    assertThat(details.getPanels().get(1).getPixelWidth()).isEqualTo(1920);
  }

  // ─── reflection helpers ──────────────────────────────────────────────────────

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

  /** A service with all collaborators null — the results-path mapper is a pure transform. */
  private RecommendationService newService() {
    return new RecommendationService(
        null, null, null, null, null, null, null, null, null, null, null);
  }
}
