package com.mw.recommendation.engine.v3.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

class GeoFitCalculatorTest {

  private final V3Properties props = new V3Properties(); // R1=1000m, R2=50000m defaults
  private final GeoFitCalculator calculator = new GeoFitCalculator(props);

  private static RecommendationV3RequestDTO.RecommendationV3RequestDTOBuilder baseRequest() {
    return RecommendationV3RequestDTO.builder()
        .country("Malaysia")
        .startDate(LocalDate.of(2026, 8, 1))
        .endDate(LocalDate.of(2026, 8, 10));
  }

  private static Inventory inventoryInCity(String city, String state) {
    Inventory inventory = new Inventory();
    inventory.setInventoryId("inv-1");
    inventory.setLocationHierarchy(
        Inventory.LocationHierarchy.builder().cityName(city).stateName(state).build());
    return inventory;
  }

  @Test
  void givenNoTargetingAndCityInTopRegions_whenCalculate_then100() {
    Inventory inventory = inventoryInCity("Kuala Lumpur", "Selangor");

    Double score =
        calculator.calculate(inventory, baseRequest().build(), Set.of("kuala lumpur", "penang"));

    assertThat(score).isEqualTo(100.0);
  }

  @Test
  void givenNoTargetingAndCityNotInTopRegions_whenCalculate_then70() {
    Inventory inventory = inventoryInCity("Ipoh", "Perak");

    Double score =
        calculator.calculate(inventory, baseRequest().build(), Set.of("kuala lumpur", "penang"));

    assertThat(score).isEqualTo(70.0);
  }

  @Test
  void givenNoTargetingAndEmptyTopRegions_whenCalculate_thenNeutral50() {
    Inventory inventory = inventoryInCity("Kuala Lumpur", "Selangor");

    Double score = calculator.calculate(inventory, baseRequest().build(), Set.of());

    assertThat(score).isEqualTo(50.0);
  }

  @Test
  void givenCityTargetingMatchesCaseInsensitive_whenCalculate_then100() {
    Inventory inventory = inventoryInCity("Kuala Lumpur", "Selangor");
    RecommendationV3RequestDTO request =
        baseRequest()
            .geographyTargeting(
                RecommendationV3RequestDTO.GeographyTargeting.builder()
                    .cities(List.of("KUALA LUMPUR"))
                    .build())
            .build();

    Double score = calculator.calculate(inventory, request, Set.of());

    assertThat(score).isEqualTo(100.0);
  }

  @Test
  void givenStateTargetingMatches_whenCalculate_then95() {
    Inventory inventory = inventoryInCity("Petaling Jaya", "Selangor");
    RecommendationV3RequestDTO request =
        baseRequest()
            .geographyTargeting(
                RecommendationV3RequestDTO.GeographyTargeting.builder()
                    .cities(List.of("Kuala Lumpur"))
                    .states(List.of("selangor"))
                    .build())
            .build();

    Double score = calculator.calculate(inventory, request, Set.of());

    assertThat(score).isEqualTo(95.0);
  }

  @Test
  void givenPolygonTargetingAndInventory10KmFromEdge_whenCalculate_thenAbout80() {
    // PRD §5.4 worked example: d=10 km, R2=50 km → 100 × (1 − 10/50) = 80
    // Square polygon near the equator; point ~10 km west of its western edge.
    Inventory inventory = new Inventory();
    inventory.setInventoryId("inv-geo");
    inventory.setLocationCoordinates(new GeoJsonPoint(-0.0899, 0.05)); // lng, lat
    RecommendationV3RequestDTO request =
        baseRequest()
            .geographyTargeting(
                RecommendationV3RequestDTO.GeographyTargeting.builder()
                    .geofences(
                        List.of(
                            RecommendationV3RequestDTO.Geofence.builder()
                                .type("Polygon")
                                .coordinates(
                                    List.of(
                                        List.of(0.0, 0.0),
                                        List.of(0.1, 0.0),
                                        List.of(0.1, 0.1),
                                        List.of(0.0, 0.1)))
                                .build()))
                    .build())
            .build();

    Double score = calculator.calculate(inventory, request, Set.of());

    assertThat(score).isCloseTo(80.0, offset(2.0));
  }

  @Test
  void givenInventoryInsidePolygon_whenCalculate_then100() {
    // PRD: inside a geofence = 100 (v1 gave 90)
    Inventory inventory = new Inventory();
    inventory.setInventoryId("inv-geo");
    inventory.setLocationCoordinates(new GeoJsonPoint(0.05, 0.05));
    RecommendationV3RequestDTO request =
        baseRequest()
            .geographyTargeting(
                RecommendationV3RequestDTO.GeographyTargeting.builder()
                    .geofences(
                        List.of(
                            RecommendationV3RequestDTO.Geofence.builder()
                                .type("Polygon")
                                .coordinates(
                                    List.of(
                                        List.of(0.0, 0.0),
                                        List.of(0.1, 0.0),
                                        List.of(0.1, 0.1),
                                        List.of(0.0, 0.1)))
                                .build()))
                    .build())
            .build();

    Double score = calculator.calculate(inventory, request, Set.of());

    assertThat(score).isEqualTo(100.0);
  }

  @Test
  void givenAirportChannelAndAirportVenue_whenCalculate_thenBonus10Applied() {
    // Base 70 (known city outside topRegions) + 10 channel bonus = 80
    Inventory inventory = inventoryInCity("Ipoh", "Perak");
    inventory.setVenueTypes(List.of("Airport"));
    RecommendationV3RequestDTO request = baseRequest().channels(List.of("airport")).build();

    Double score = calculator.calculate(inventory, request, Set.of("kuala lumpur"));

    assertThat(score).isEqualTo(80.0);
  }

  @Test
  void givenAirportChannelOnPerfectCityMatch_whenCalculate_thenCappedAt100() {
    Inventory inventory = inventoryInCity("Kuala Lumpur", "Selangor");
    inventory.setVenueTypes(List.of("Airport"));
    RecommendationV3RequestDTO request =
        baseRequest()
            .channels(List.of("airport"))
            .geographyTargeting(
                RecommendationV3RequestDTO.GeographyTargeting.builder()
                    .cities(List.of("Kuala Lumpur"))
                    .build())
            .build();

    Double score = calculator.calculate(inventory, request, Set.of());

    assertThat(score).isEqualTo(100.0);
  }
}
