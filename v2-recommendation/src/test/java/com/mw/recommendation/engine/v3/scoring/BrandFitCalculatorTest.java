package com.mw.recommendation.engine.v3.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.recommendation.engine.domain.Inventory;
import java.util.List;
import org.junit.jupiter.api.Test;

class BrandFitCalculatorTest {

  private final BrandFitCalculator calculator = new BrandFitCalculator();

  @Test
  void givenNoBrandId_whenCalculate_thenNeutral50() {
    Inventory inventory = new Inventory();
    inventory.setVenueTypes(List.of("Airport"));

    assertThat(calculator.calculate(inventory, null, "Travel")).isEqualTo(50.0);
    assertThat(calculator.calculate(inventory, "  ", "Travel")).isEqualTo(50.0);
  }

  @Test
  void givenBrandIdButUnresolvedCategory_whenCalculate_thenNeutral50() {
    Inventory inventory = new Inventory();
    inventory.setVenueTypes(List.of("Airport"));

    assertThat(calculator.calculate(inventory, "brand-1", null)).isEqualTo(50.0);
  }

  @Test
  void givenTravelAirlinesCategoryAndAirportVenue_whenCalculate_thenExactAffinity100() {
    Inventory inventory = new Inventory();
    inventory.setVenueTypes(List.of("Airport"));

    assertThat(calculator.calculate(inventory, "brand-1", "Travel/Airlines")).isEqualTo(100.0);
  }

  @Test
  void givenTravelCategoryAndMallVenue_whenCalculate_thenPartialAffinity70() {
    Inventory inventory = new Inventory();
    inventory.setName("Mid Valley Mall");
    inventory.setVenueTypes(List.of("Mall"));

    assertThat(calculator.calculate(inventory, "brand-1", "Travel")).isEqualTo(70.0);
  }

  @Test
  void givenTravelCategoryAndOfficeVenue_whenCalculate_thenUnrelated30() {
    Inventory inventory = new Inventory();
    inventory.setName("Office Tower");
    inventory.setVenueTypes(List.of("Office"));

    assertThat(calculator.calculate(inventory, "brand-1", "Travel")).isEqualTo(30.0);
  }
}
