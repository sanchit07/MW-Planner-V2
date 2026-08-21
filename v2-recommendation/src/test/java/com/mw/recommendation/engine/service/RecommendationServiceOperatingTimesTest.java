package com.mw.recommendation.engine.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.dto.BrowseInventoryRequestDTO;
import com.mw.recommendation.engine.dto.PaginatedRecommendationResponseDTO;
import com.mw.recommendation.engine.mapper.InventoryItemMapper;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies that operating hours flow from the scored {@link RecommendationResult} into the
 * paginated API response, so consumers (the planner FE) can validate schedules without a separate
 * enrichment call.
 */
class RecommendationServiceOperatingTimesTest {

  @Test
  void convertToRecommendedInventory_mapsOperatingTimesIntoInventoryDetails() throws Exception {
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimes =
        Map.of(
            Inventory.Weekday.MONDAY,
            List.of(Inventory.OperatingTime.builder().start("06:00:00").end("22:00:00").build()));

    RecommendationResult result =
        RecommendationResult.builder()
            .inventoryId("inv-1")
            .name("Test Billboard")
            .inventoryDetails(
                RecommendationResult.InventoryDetails.builder()
                    .type("OOH")
                    .operatingTimes(operatingTimes)
                    .build())
            .build();

    PaginatedRecommendationResponseDTO.RecommendedInventory dto = convert(result);

    assertThat(dto.getInventoryDetails()).isNotNull();
    assertThat(dto.getInventoryDetails().getOperatingTimes()).isEqualTo(operatingTimes);
  }

  @Test
  void buildBrowseRecommendedInventory_includesOperatingTimesFromInventory() throws Exception {
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimes =
        Map.of(
            Inventory.Weekday.TUESDAY,
            List.of(Inventory.OperatingTime.builder().start("08:00:00").end("20:00:00").build()));

    Inventory inventory = new Inventory();
    inventory.setInventoryId("inv-2");
    inventory.setName("Browse Billboard");
    inventory.setOperatingTimes(operatingTimes);

    BrowseInventoryRequestDTO request = new BrowseInventoryRequestDTO();
    request.setStartDate(LocalDate.of(2026, 6, 1));
    request.setEndDate(LocalDate.of(2026, 6, 7));

    PaginatedRecommendationResponseDTO.RecommendedInventory dto =
        buildBrowse(inventory, 7, request);

    assertThat(dto.getInventoryDetails()).isNotNull();
    assertThat(dto.getInventoryDetails().getOperatingTimes()).isEqualTo(operatingTimes);
  }

  @Test
  void enrichMissingOperatingTimes_fillsFromInventoryMapWhenResultHasNone() {
    // A response DTO whose inventoryDetails has NO operatingTimes (old run / stale result).
    PaginatedRecommendationResponseDTO.RecommendedInventory rec =
        PaginatedRecommendationResponseDTO.RecommendedInventory.builder()
            .inventoryId("inv-3")
            .inventoryDetails(
                PaginatedRecommendationResponseDTO.InventoryDetails.builder().type("OOH").build())
            .build();

    Map<Inventory.Weekday, List<Inventory.OperatingTime>> opTimes =
        Map.of(
            Inventory.Weekday.WEDNESDAY,
            List.of(Inventory.OperatingTime.builder().start("04:00:00").end("23:00:00").build()));
    Map<String, Map<Inventory.Weekday, List<Inventory.OperatingTime>>> byInventoryId =
        Map.of("inv-3", opTimes);

    RecommendationService.enrichMissingOperatingTimes(List.of(rec), byInventoryId);

    assertThat(rec.getInventoryDetails().getOperatingTimes()).isEqualTo(opTimes);
  }

  @Test
  void enrichMissingOperatingTimes_doesNotOverwriteExistingOperatingTimes() {
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> existing =
        Map.of(
            Inventory.Weekday.MONDAY,
            List.of(Inventory.OperatingTime.builder().start("09:00:00").end("17:00:00").build()));
    PaginatedRecommendationResponseDTO.RecommendedInventory rec =
        PaginatedRecommendationResponseDTO.RecommendedInventory.builder()
            .inventoryId("inv-4")
            .inventoryDetails(
                PaginatedRecommendationResponseDTO.InventoryDetails.builder()
                    .operatingTimes(existing)
                    .build())
            .build();

    Map<Inventory.Weekday, List<Inventory.OperatingTime>> other =
        Map.of(
            Inventory.Weekday.SUNDAY,
            List.of(Inventory.OperatingTime.builder().start("00:00:00").end("23:59:00").build()));

    RecommendationService.enrichMissingOperatingTimes(List.of(rec), Map.of("inv-4", other));

    assertThat(rec.getInventoryDetails().getOperatingTimes()).isEqualTo(existing);
  }

  /** Invokes the private mapper with all collaborators null — the mapper is a pure transform. */
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
