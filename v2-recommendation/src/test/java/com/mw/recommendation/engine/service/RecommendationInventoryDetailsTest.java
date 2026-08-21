package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.domain.RecommendationResult;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for verifying that durations and resolutions fields are properly derived from
 * Inventory objects in RecommendationService
 */
class RecommendationInventoryDetailsTest {

  private RecommendationService recommendationService;

  @BeforeEach
  void setUp() {
    // We need to create a minimal instance to access private methods via reflection
    recommendationService =
        new RecommendationService(null, null, null, null, null, null, null, null, null, null, null);
  }

  @Test
  void deriveResolutions_withMultiplePanels_returnsAllUniqueResolutions() throws Exception {
    // Arrange
    Inventory.Panel panel1 = Inventory.Panel.builder().pixelWidth(1920).pixelHeight(1080).build();

    Inventory.Panel panel2 = Inventory.Panel.builder().pixelWidth(1080).pixelHeight(1920).build();

    Inventory.Panel panel3 =
        Inventory.Panel.builder().pixelWidth(1920).pixelHeight(1080).build(); // Duplicate

    Inventory inventory = new Inventory();
    inventory.setPanels(Arrays.asList(panel1, panel2, panel3));

    // Act
    Method method =
        RecommendationService.class.getDeclaredMethod("deriveResolutions", Inventory.class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<String> resolutions = (List<String>) method.invoke(recommendationService, inventory);

    // Assert
    assertNotNull(resolutions);
    assertEquals(2, resolutions.size()); // Should have 2 unique resolutions
    assertTrue(resolutions.contains("1920x1080"));
    assertTrue(resolutions.contains("1080x1920"));
  }

  @Test
  void deriveResolutions_withNullPanels_returnsNull() throws Exception {
    // Arrange
    Inventory inventory = new Inventory();
    inventory.setPanels(null);

    // Act
    Method method =
        RecommendationService.class.getDeclaredMethod("deriveResolutions", Inventory.class);
    method.setAccessible(true);
    List<?> resolutions = (List<?>) method.invoke(recommendationService, inventory);

    // Assert
    assertNull(resolutions);
  }

  @Test
  void deriveResolutions_withEmptyPanels_returnsNull() throws Exception {
    // Arrange
    Inventory inventory = new Inventory();
    inventory.setPanels(List.of());

    // Act
    Method method =
        RecommendationService.class.getDeclaredMethod("deriveResolutions", Inventory.class);
    method.setAccessible(true);
    List<?> resolutions = (List<?>) method.invoke(recommendationService, inventory);

    // Assert
    assertNull(resolutions);
  }

  @Test
  void deriveResolutions_withNullPixelDimensions_filtersOutInvalidPanels() throws Exception {
    // Arrange
    Inventory.Panel validPanel =
        Inventory.Panel.builder().pixelWidth(1920).pixelHeight(1080).build();

    Inventory.Panel invalidPanel1 =
        Inventory.Panel.builder().pixelWidth(null).pixelHeight(1080).build();

    Inventory.Panel invalidPanel2 =
        Inventory.Panel.builder().pixelWidth(1920).pixelHeight(null).build();

    Inventory inventory = new Inventory();
    inventory.setPanels(Arrays.asList(validPanel, invalidPanel1, invalidPanel2));

    // Act
    Method method =
        RecommendationService.class.getDeclaredMethod("deriveResolutions", Inventory.class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<String> resolutions = (List<String>) method.invoke(recommendationService, inventory);

    // Assert
    assertNotNull(resolutions);
    assertEquals(1, resolutions.size());
    assertEquals("1920x1080", resolutions.get(0));
  }

  @Test
  void deriveDurations_withMultiplePrices_returnsAllUniqueDurationsSorted() throws Exception {
    // Arrange
    Inventory.PriceModel price1 =
        Inventory.PriceModel.builder().cpm(5.0).durationSeconds(30).build();

    Inventory.PriceModel price2 =
        Inventory.PriceModel.builder().cpm(3.0).durationSeconds(10).build();

    Inventory.PriceModel price3 =
        Inventory.PriceModel.builder().cpm(8.0).durationSeconds(60).build();

    Inventory.PriceModel price4 =
        Inventory.PriceModel.builder().cpm(5.5).durationSeconds(30).build(); // Duplicate

    Inventory inventory = new Inventory();
    inventory.setPrices(Arrays.asList(price1, price2, price3, price4));

    // Act
    Method method =
        RecommendationService.class.getDeclaredMethod("deriveDurations", Inventory.class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<Integer> durations = (List<Integer>) method.invoke(recommendationService, inventory);

    // Assert
    assertNotNull(durations);
    assertEquals(3, durations.size()); // Should have 3 unique durations
    assertEquals(Arrays.asList(10, 30, 60), durations); // Should be sorted
  }

  @Test
  void deriveDurations_withNullPrices_returnsNull() throws Exception {
    // Arrange
    Inventory inventory = new Inventory();
    inventory.setPrices(null);

    // Act
    Method method =
        RecommendationService.class.getDeclaredMethod("deriveDurations", Inventory.class);
    method.setAccessible(true);
    List<?> durations = (List<?>) method.invoke(recommendationService, inventory);

    // Assert
    assertNull(durations);
  }

  @Test
  void deriveDurations_withEmptyPrices_returnsNull() throws Exception {
    // Arrange
    Inventory inventory = new Inventory();
    inventory.setPrices(List.of());

    // Act
    Method method =
        RecommendationService.class.getDeclaredMethod("deriveDurations", Inventory.class);
    method.setAccessible(true);
    List<?> durations = (List<?>) method.invoke(recommendationService, inventory);

    // Assert
    assertNull(durations);
  }

  @Test
  void deriveDurations_withNullDurationSeconds_filtersOutInvalidPrices() throws Exception {
    // Arrange
    Inventory.PriceModel validPrice =
        Inventory.PriceModel.builder().cpm(5.0).durationSeconds(30).build();

    Inventory.PriceModel invalidPrice =
        Inventory.PriceModel.builder().cpm(3.0).durationSeconds(null).build();

    Inventory inventory = new Inventory();
    inventory.setPrices(Arrays.asList(validPrice, invalidPrice));

    // Act
    Method method =
        RecommendationService.class.getDeclaredMethod("deriveDurations", Inventory.class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<Integer> durations = (List<Integer>) method.invoke(recommendationService, inventory);

    // Assert
    assertNotNull(durations);
    assertEquals(1, durations.size());
    assertEquals(30, durations.get(0));
  }

  @Test
  void deriveDurations_withMixedDurations_returnsSortedList() throws Exception {
    // Arrange
    Inventory.PriceModel price1 =
        Inventory.PriceModel.builder().cpm(5.0).durationSeconds(60).build();

    Inventory.PriceModel price2 =
        Inventory.PriceModel.builder().cpm(3.0).durationSeconds(15).build();

    Inventory.PriceModel price3 =
        Inventory.PriceModel.builder().cpm(8.0).durationSeconds(30).build();

    Inventory.PriceModel price4 =
        Inventory.PriceModel.builder().cpm(2.0).durationSeconds(10).build();

    Inventory inventory = new Inventory();
    inventory.setPrices(Arrays.asList(price1, price2, price3, price4));

    // Act
    Method method =
        RecommendationService.class.getDeclaredMethod("deriveDurations", Inventory.class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<Integer> durations = (List<Integer>) method.invoke(recommendationService, inventory);

    // Assert
    assertNotNull(durations);
    assertEquals(4, durations.size());
    assertEquals(Arrays.asList(10, 15, 30, 60), durations); // Should be sorted ascending
  }

  @Test
  void deriveDurations_withAllNullDurations_returnsEmptyList() throws Exception {
    // Arrange
    Inventory.PriceModel price1 =
        Inventory.PriceModel.builder().cpm(5.0).durationSeconds(null).build();

    Inventory.PriceModel price2 =
        Inventory.PriceModel.builder().cpm(3.0).durationSeconds(null).build();

    Inventory inventory = new Inventory();
    inventory.setPrices(Arrays.asList(price1, price2));

    // Act
    Method method =
        RecommendationService.class.getDeclaredMethod("deriveDurations", Inventory.class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<Integer> durations = (List<Integer>) method.invoke(recommendationService, inventory);

    // Assert
    assertNotNull(durations);
    assertTrue(durations.isEmpty());
  }

  // ─── spotPrice field tests ──────────────────────────────────────────────────

  @Test
  void inventoryDetails_spotPrice_isPopulatedFromFirstPrice() {
    // Arrange & Act
    RecommendationResult.InventoryDetails details =
        RecommendationResult.InventoryDetails.builder().cpmRate(2.0).spotRate(0.02).build();

    // Assert
    assertEquals(0.02, details.getSpotRate());
    assertEquals(2.0, details.getCpmRate());
  }

  @Test
  void inventoryDetails_spotPrice_isNullWhenNotSet() {
    // Arrange & Act
    RecommendationResult.InventoryDetails details =
        RecommendationResult.InventoryDetails.builder().cpmRate(25.0).build();

    // Assert
    assertNull(details.getSpotRate());
    assertEquals(25.0, details.getCpmRate());
  }

  @Test
  void inventoryDetails_spotPrice_worksWithNullCpmRate() {
    // Arrange & Act — inventory may have spot price but no CPM
    RecommendationResult.InventoryDetails details =
        RecommendationResult.InventoryDetails.builder().spotRate(0.05).build();

    // Assert
    assertEquals(0.05, details.getSpotRate());
    assertNull(details.getCpmRate());
  }

  @Test
  void priceModel_spot_fieldExistsAndIsAccessible() {
    // Arrange & Act
    Inventory.PriceModel price =
        Inventory.PriceModel.builder()
            .cpm(2.0)
            .spot(0.02)
            .currency("JPY")
            .durationSeconds(15)
            .build();

    // Assert
    assertEquals(0.02, price.getSpot());
    assertEquals(2.0, price.getCpm());
    assertEquals("JPY", price.getCurrency());
    assertEquals(15, price.getDurationSeconds());
  }

  @Test
  void priceModel_spot_isNullWhenNotProvided() {
    // Arrange & Act
    Inventory.PriceModel price = Inventory.PriceModel.builder().cpm(25.0).currency("USD").build();

    // Assert
    assertNull(price.getSpot());
  }
}
