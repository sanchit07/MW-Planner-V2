package com.mw.recommendation.engine.rabbitmq;

import static org.junit.jupiter.api.Assertions.*;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.ExternalInventoryMessageDTO;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExternalInventoryMessageConverterTest {

  private ExternalInventoryMessageConverter converter;

  @BeforeEach
  void setUp() {
    converter = new ExternalInventoryMessageConverter();
  }

  @Test
  void testConvertPrices_WithAllFields() {
    // Arrange
    ExternalInventoryMessageDTO.Price externalPrice1 = new ExternalInventoryMessageDTO.Price();
    externalPrice1.setCpm(5.0);
    externalPrice1.setCps(10.0);
    externalPrice1.setMonthly(200.0);
    externalPrice1.setCurrency("USD");
    externalPrice1.setDurationSeconds(30);

    ExternalInventoryMessageDTO.Price externalPrice2 = new ExternalInventoryMessageDTO.Price();
    externalPrice2.setCpm(3.0);
    externalPrice2.setCps(7.0);
    externalPrice2.setMonthly(150.0);
    externalPrice2.setCurrency("EUR");
    externalPrice2.setDurationSeconds(15);

    ExternalInventoryMessageDTO externalMessage = new ExternalInventoryMessageDTO();
    externalMessage.setId("inv-123");
    externalMessage.setName("Test Inventory");
    externalMessage.setPrices(Arrays.asList(externalPrice1, externalPrice2));

    // Mock required fields for conversion
    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setExternalId("REF-123");
    externalId.setPlatform("LMX");
    externalMessage.setExternalIds(Collections.singletonList(externalId));

    // Act
    Inventory inventory = converter.convertToInventory(externalMessage);

    // Assert
    assertNotNull(inventory);
    assertNotNull(inventory.getPrices());
    assertEquals(2, inventory.getPrices().size());

    // Verify first price
    Inventory.PriceModel price1 = inventory.getPrices().get(0);
    assertEquals(5.0, price1.getCpm());
    assertEquals(10.0, price1.getSpot());
    assertEquals(200.0, price1.getMonthly());
    assertEquals("USD", price1.getCurrency());
    assertEquals(30, price1.getDurationSeconds());

    // Verify second price
    Inventory.PriceModel price2 = inventory.getPrices().get(1);
    assertEquals(3.0, price2.getCpm());
    assertEquals(7.0, price2.getSpot());
    assertEquals(150.0, price2.getMonthly());
    assertEquals("EUR", price2.getCurrency());
    assertEquals(15, price2.getDurationSeconds());
  }

  @Test
  void testConvertPrices_WithNullCurrencyAndDuration() {
    // Arrange
    ExternalInventoryMessageDTO.Price externalPrice = new ExternalInventoryMessageDTO.Price();
    externalPrice.setCpm(5.0);
    externalPrice.setCps(10.0);
    externalPrice.setCurrency(null);
    externalPrice.setDurationSeconds(null);

    ExternalInventoryMessageDTO externalMessage = new ExternalInventoryMessageDTO();
    externalMessage.setId("inv-123");
    externalMessage.setName("Test Inventory");
    externalMessage.setPrices(Collections.singletonList(externalPrice));

    // Mock required fields
    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setExternalId("REF-123");
    externalId.setPlatform("LMX");
    externalMessage.setExternalIds(Collections.singletonList(externalId));

    // Act
    Inventory inventory = converter.convertToInventory(externalMessage);

    // Assert
    assertNotNull(inventory);
    assertNotNull(inventory.getPrices());
    assertEquals(1, inventory.getPrices().size());

    Inventory.PriceModel price = inventory.getPrices().get(0);
    assertEquals(5.0, price.getCpm());
    assertEquals(10.0, price.getSpot());
    assertNull(price.getCurrency());
    assertNull(price.getDurationSeconds());
  }

  @Test
  void testConvertPrices_WithOnlyDurationSeconds() {
    // Arrange
    ExternalInventoryMessageDTO.Price externalPrice = new ExternalInventoryMessageDTO.Price();
    externalPrice.setCpm(5.0);
    externalPrice.setCps(10.0);
    externalPrice.setCurrency(null);
    externalPrice.setDurationSeconds(60);

    ExternalInventoryMessageDTO externalMessage = new ExternalInventoryMessageDTO();
    externalMessage.setId("inv-123");
    externalMessage.setName("Test Inventory");
    externalMessage.setPrices(Collections.singletonList(externalPrice));

    // Mock required fields
    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setExternalId("REF-123");
    externalId.setPlatform("LMX");
    externalMessage.setExternalIds(Collections.singletonList(externalId));

    // Act
    Inventory inventory = converter.convertToInventory(externalMessage);

    // Assert
    assertNotNull(inventory);
    assertNotNull(inventory.getPrices());
    assertEquals(1, inventory.getPrices().size());

    Inventory.PriceModel price = inventory.getPrices().get(0);
    assertEquals(5.0, price.getCpm());
    assertEquals(10.0, price.getSpot());
    assertNull(price.getCurrency());
    assertEquals(60, price.getDurationSeconds());
  }

  @Test
  void testConvertPrices_WithOnlyCurrency() {
    // Arrange
    ExternalInventoryMessageDTO.Price externalPrice = new ExternalInventoryMessageDTO.Price();
    externalPrice.setCpm(5.0);
    externalPrice.setCps(10.0);
    externalPrice.setCurrency("JPY");
    externalPrice.setDurationSeconds(null);

    ExternalInventoryMessageDTO externalMessage = new ExternalInventoryMessageDTO();
    externalMessage.setId("inv-123");
    externalMessage.setName("Test Inventory");
    externalMessage.setPrices(Collections.singletonList(externalPrice));

    // Mock required fields
    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setExternalId("REF-123");
    externalId.setPlatform("LMX");
    externalMessage.setExternalIds(Collections.singletonList(externalId));

    // Act
    Inventory inventory = converter.convertToInventory(externalMessage);

    // Assert
    assertNotNull(inventory);
    assertNotNull(inventory.getPrices());
    assertEquals(1, inventory.getPrices().size());

    Inventory.PriceModel price = inventory.getPrices().get(0);
    assertEquals(5.0, price.getCpm());
    assertEquals(10.0, price.getSpot());
    assertEquals("JPY", price.getCurrency());
    assertNull(price.getDurationSeconds());
  }

  @Test
  void testConvertPrices_WithNullPricesList() {
    // Arrange
    ExternalInventoryMessageDTO externalMessage = new ExternalInventoryMessageDTO();
    externalMessage.setId("inv-123");
    externalMessage.setName("Test Inventory");
    externalMessage.setPrices(null);

    // Mock required fields
    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setExternalId("REF-123");
    externalId.setPlatform("LMX");
    externalMessage.setExternalIds(Collections.singletonList(externalId));

    // Act
    Inventory inventory = converter.convertToInventory(externalMessage);

    // Assert
    assertNotNull(inventory);
    assertNull(inventory.getPrices());
  }

  @Test
  void testConvertPrices_WithEmptyPricesList() {
    // Arrange
    ExternalInventoryMessageDTO externalMessage = new ExternalInventoryMessageDTO();
    externalMessage.setId("inv-123");
    externalMessage.setName("Test Inventory");
    externalMessage.setPrices(Collections.emptyList());

    // Mock required fields
    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setExternalId("REF-123");
    externalId.setPlatform("LMX");
    externalMessage.setExternalIds(Collections.singletonList(externalId));

    // Act
    Inventory inventory = converter.convertToInventory(externalMessage);

    // Assert
    assertNotNull(inventory);
    assertNotNull(inventory.getPrices());
    assertTrue(inventory.getPrices().isEmpty());
  }

  @Test
  void testConvertPrices_WithMultipleDurations() {
    // Arrange - Different prices for different durations (e.g., 10s, 30s, 60s spots)
    ExternalInventoryMessageDTO.Price price10s = new ExternalInventoryMessageDTO.Price();
    price10s.setCpm(2.0);
    price10s.setCps(5.0);
    price10s.setCurrency("USD");
    price10s.setDurationSeconds(10);

    ExternalInventoryMessageDTO.Price price30s = new ExternalInventoryMessageDTO.Price();
    price30s.setCpm(5.0);
    price30s.setCps(12.0);
    price30s.setCurrency("USD");
    price30s.setDurationSeconds(30);

    ExternalInventoryMessageDTO.Price price60s = new ExternalInventoryMessageDTO.Price();
    price60s.setCpm(9.0);
    price60s.setCps(20.0);
    price60s.setCurrency("USD");
    price60s.setDurationSeconds(60);

    ExternalInventoryMessageDTO externalMessage = new ExternalInventoryMessageDTO();
    externalMessage.setId("inv-123");
    externalMessage.setName("Test Digital Billboard");
    externalMessage.setPrices(Arrays.asList(price10s, price30s, price60s));

    // Mock required fields
    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setExternalId("REF-123");
    externalId.setPlatform("LMX");
    externalMessage.setExternalIds(Collections.singletonList(externalId));

    // Act
    Inventory inventory = converter.convertToInventory(externalMessage);

    // Assert
    assertNotNull(inventory);
    assertNotNull(inventory.getPrices());
    assertEquals(3, inventory.getPrices().size());

    // Verify 10-second spot pricing
    Inventory.PriceModel converted10s = inventory.getPrices().get(0);
    assertEquals(10, converted10s.getDurationSeconds());
    assertEquals(2.0, converted10s.getCpm());
    assertEquals(5.0, converted10s.getSpot());
    assertEquals("USD", converted10s.getCurrency());

    // Verify 30-second spot pricing
    Inventory.PriceModel converted30s = inventory.getPrices().get(1);
    assertEquals(30, converted30s.getDurationSeconds());
    assertEquals(5.0, converted30s.getCpm());
    assertEquals(12.0, converted30s.getSpot());

    // Verify 60-second spot pricing
    Inventory.PriceModel converted60s = inventory.getPrices().get(2);
    assertEquals(60, converted60s.getDurationSeconds());
    assertEquals(9.0, converted60s.getCpm());
    assertEquals(20.0, converted60s.getSpot());
  }

  @Test
  void testConvertPrices_WithMonthlyOnly_ClassicInventory() {
    // Arrange - Classic OOH inventory: cpm and cps are null, only monthly rate set
    ExternalInventoryMessageDTO.Price externalPrice = new ExternalInventoryMessageDTO.Price();
    externalPrice.setCpm(null);
    externalPrice.setCps(null);
    externalPrice.setMonthly(100000.0);
    externalPrice.setCurrency("INR");
    externalPrice.setDurationSeconds(null);

    ExternalInventoryMessageDTO externalMessage = new ExternalInventoryMessageDTO();
    externalMessage.setId("inv-classic-123");
    externalMessage.setName("Classic Billboard");
    externalMessage.setPrices(Collections.singletonList(externalPrice));

    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setExternalId("IND-MAX-C-00000-47294");
    externalId.setPlatform("LMX");
    externalMessage.setExternalIds(Collections.singletonList(externalId));

    // Act
    Inventory inventory = converter.convertToInventory(externalMessage);

    // Assert
    assertNotNull(inventory);
    assertNotNull(inventory.getPrices());
    assertEquals(1, inventory.getPrices().size());

    Inventory.PriceModel price = inventory.getPrices().get(0);
    assertNull(price.getCpm());
    assertNull(price.getSpot());
    assertEquals(100000.0, price.getMonthly());
    assertEquals("INR", price.getCurrency());
    assertNull(price.getDurationSeconds());
  }

  @Test
  void testGeneratePriceTypes_OnlyCpm() {
    List<Inventory.PriceModel> prices = List.of(Inventory.PriceModel.builder().cpm(5.0).build());

    List<String> types = converter.generatePriceTypes(prices);

    assertEquals(1, types.size());
    assertTrue(types.contains("cpm"));
  }

  @Test
  void testGeneratePriceTypes_AllThreeOnSingleElement() {
    List<Inventory.PriceModel> prices =
        List.of(Inventory.PriceModel.builder().cpm(5.0).spot(10.0).monthly(200.0).build());

    List<String> types = converter.generatePriceTypes(prices);

    assertEquals(3, types.size());
    assertTrue(types.contains("cpm"));
    assertTrue(types.contains("spot"));
    assertTrue(types.contains("monthly"));
  }

  @Test
  void testGeneratePriceTypes_ValuesSpreadAcrossElements() {
    List<Inventory.PriceModel> prices =
        Arrays.asList(
            Inventory.PriceModel.builder().cpm(5.0).build(),
            Inventory.PriceModel.builder().spot(10.0).build());

    List<String> types = converter.generatePriceTypes(prices);

    assertEquals(2, types.size());
    assertTrue(types.contains("cpm"));
    assertTrue(types.contains("spot"));
    assertFalse(types.contains("monthly"));
  }

  @Test
  void testGeneratePriceTypes_ZeroNegativeAndNullIgnored() {
    List<Inventory.PriceModel> prices =
        Arrays.asList(
            Inventory.PriceModel.builder().cpm(0.0).build(),
            Inventory.PriceModel.builder().spot(-1.0).build(),
            Inventory.PriceModel.builder().monthly(null).build(),
            null);

    List<String> types = converter.generatePriceTypes(prices);

    assertTrue(types.isEmpty());
  }

  @Test
  void testGeneratePriceTypes_NullPrices() {
    List<String> types = converter.generatePriceTypes(null);

    assertNotNull(types);
    assertTrue(types.isEmpty());
  }

  @Test
  void testGeneratePriceTypes_EmptyPrices() {
    List<String> types = converter.generatePriceTypes(Collections.emptyList());

    assertNotNull(types);
    assertTrue(types.isEmpty());
  }

  @Test
  void testConvertPrices_MapsDailyAndWeekly() {
    // Arrange - rate-card price carrying daily + weekly + monthly (Classic OOH).
    ExternalInventoryMessageDTO.Price externalPrice = new ExternalInventoryMessageDTO.Price();
    externalPrice.setMonthly(100000.0);
    externalPrice.setDaily(4000.0);
    externalPrice.setWeekly(25000.0);
    externalPrice.setCurrency("INR");

    ExternalInventoryMessageDTO externalMessage = new ExternalInventoryMessageDTO();
    externalMessage.setId("inv-classic-456");
    externalMessage.setName("Classic Billboard With Daily/Weekly");
    externalMessage.setPrices(Collections.singletonList(externalPrice));

    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setExternalId("IND-MAX-C-00000-47295");
    externalId.setPlatform("LMX");
    externalMessage.setExternalIds(Collections.singletonList(externalId));

    // Act
    Inventory inventory = converter.convertToInventory(externalMessage);

    // Assert - daily and weekly are no longer dropped during ingest.
    assertNotNull(inventory.getPrices());
    assertEquals(1, inventory.getPrices().size());
    Inventory.PriceModel price = inventory.getPrices().get(0);
    assertEquals(100000.0, price.getMonthly());
    assertEquals(4000.0, price.getDaily());
    assertEquals(25000.0, price.getWeekly());
    assertEquals("INR", price.getCurrency());
  }

  @Test
  void testGeneratePriceTypes_IncludesDaily() {
    List<Inventory.PriceModel> prices =
        List.of(Inventory.PriceModel.builder().daily(4000.0).build());

    List<String> types = converter.generatePriceTypes(prices);

    assertEquals(1, types.size());
    assertTrue(types.contains("daily"));
  }

  @Test
  void testGeneratePriceTypes_IncludesWeekly() {
    List<Inventory.PriceModel> prices =
        List.of(Inventory.PriceModel.builder().weekly(25000.0).build());

    List<String> types = converter.generatePriceTypes(prices);

    assertEquals(1, types.size());
    assertTrue(types.contains("weekly"));
  }

  @Test
  void testGeneratePriceTypes_AllRateCardTypesInOrder() {
    // A single element carrying every model — order must stay cpm, spot, monthly, daily, weekly.
    List<Inventory.PriceModel> prices =
        List.of(
            Inventory.PriceModel.builder()
                .cpm(5.0)
                .spot(10.0)
                .monthly(200.0)
                .daily(20.0)
                .weekly(100.0)
                .build());

    List<String> types = converter.generatePriceTypes(prices);

    assertEquals(List.of("cpm", "spot", "monthly", "daily", "weekly"), types);
  }

  @Test
  void testGeneratePriceTypes_ZeroAndNegativeDailyWeeklyIgnored() {
    List<Inventory.PriceModel> prices =
        Arrays.asList(
            Inventory.PriceModel.builder().daily(0.0).build(),
            Inventory.PriceModel.builder().weekly(-1.0).build());

    List<String> types = converter.generatePriceTypes(prices);

    assertFalse(types.contains("daily"));
    assertFalse(types.contains("weekly"));
    assertTrue(types.isEmpty());
  }
}
