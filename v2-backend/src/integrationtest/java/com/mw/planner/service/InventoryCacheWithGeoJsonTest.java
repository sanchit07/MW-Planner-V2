package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.planner.TestcontainersConfiguration;
import com.mw.planner.domain.Inventory;
import com.mw.planner.repository.InventoryRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InventoryCacheWithGeoJsonTest {

  @Autowired private InventoryService inventoryService;

  @Autowired private InventoryRepository inventoryRepository;

  @Autowired private CacheManager cacheManager;

  @Autowired private MongoTemplate mongoTemplate;

  @BeforeEach
  void setUp() {
    // Clear cache before each test
    cacheManager
        .getCacheNames()
        .forEach(
            cacheName -> {
              var cache = cacheManager.getCache(cacheName);
              if (cache != null) {
                cache.clear();
              }
            });

    // Clean up database collections that might have conflicting data
    try {
      // Drop and recreate collections to ensure clean state
      mongoTemplate.dropCollection("inventories");
      mongoTemplate.dropCollection("countries");
      mongoTemplate.dropCollection("states");
      mongoTemplate.dropCollection("districts");

      // Clean up inventory repository
      inventoryRepository.deleteAll();
    } catch (Exception e) {
      // If drop fails, just clear the data
      inventoryRepository.deleteAll();
    }
  }

  @Test
  @DisplayName("Should cache and retrieve Inventory with GeoJsonPoint without errors")
  void shouldCacheAndRetrieveInventoryWithGeoJsonPoint() {
    // Given
    Inventory inventory = createTestInventoryWithGeoJsonPoint();
    inventoryRepository.save(inventory);

    // When - First call should hit database and cache the result
    Inventory result1 = inventoryService.getById(inventory.getId());

    // When - Second call should hit cache
    Inventory result2 = inventoryService.getById(inventory.getId());

    // Then
    assertThat(result1).isNotNull();
    assertThat(result2).isNotNull();
    assertThat(result1.getId()).isEqualTo(result2.getId());
    assertThat(result1.getName()).isEqualTo(result2.getName());

    // Verify GeoJsonPoint is properly deserialized
    assertThat(result1.getLocation()).isNotNull();
    assertThat(result2.getLocation()).isNotNull();
    assertThat(result1.getLocation().getLocationCoordinates()).isNotNull();
    assertThat(result2.getLocation().getLocationCoordinates()).isNotNull();

    // locationCoordinates can be GeoJsonPoint or Map when deserialized
    Object coords1 = result1.getLocation().getLocationCoordinates();
    Object coords2 = result2.getLocation().getLocationCoordinates();

    // Extract coordinates - handle both GeoJsonPoint and Map deserialization
    double x1, y1, x2, y2;
    if (coords1 instanceof GeoJsonPoint) {
      GeoJsonPoint point1 = (GeoJsonPoint) coords1;
      x1 = point1.getX();
      y1 = point1.getY();
    } else if (coords1 instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map1 = (Map<String, Object>) coords1;
      @SuppressWarnings("unchecked")
      List<Object> coordsList1 = (List<Object>) map1.get("coordinates");
      x1 = getDoubleValue(coordsList1.get(0));
      y1 = getDoubleValue(coordsList1.get(1));
    } else {
      throw new AssertionError("Unexpected coordinates type: " + coords1.getClass());
    }

    if (coords2 instanceof GeoJsonPoint) {
      GeoJsonPoint point2 = (GeoJsonPoint) coords2;
      x2 = point2.getX();
      y2 = point2.getY();
    } else if (coords2 instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map2 = (Map<String, Object>) coords2;
      @SuppressWarnings("unchecked")
      List<Object> coordsList2 = (List<Object>) map2.get("coordinates");
      x2 = getDoubleValue(coordsList2.get(0));
      y2 = getDoubleValue(coordsList2.get(1));
    } else {
      throw new AssertionError("Unexpected coordinates type: " + coords2.getClass());
    }

    assertThat(x1).isEqualTo(x2);
    assertThat(y1).isEqualTo(y2);
    assertThat(x1).isEqualTo(12.345);
    assertThat(y1).isEqualTo(67.890);
  }

  @Test
  @DisplayName("Should handle Inventory with null GeoJsonPoint")
  void shouldHandleInventoryWithNullGeoJsonPoint() {
    // Given
    Inventory inventory = createTestInventoryWithNullGeoJsonPoint();
    inventoryRepository.save(inventory);

    // When
    Inventory result1 = inventoryService.getById(inventory.getId());
    Inventory result2 = inventoryService.getById(inventory.getId());

    // Then
    assertThat(result1).isNotNull();
    assertThat(result2).isNotNull();
    assertThat(result1.getLocation()).isNotNull();
    assertThat(result2.getLocation()).isNotNull();
    // Location coordinates can be null or deserialized in different formats
    if (result1.getLocation().getLocationCoordinates() != null) {
      Object coords1 = result1.getLocation().getLocationCoordinates();
      Object coords2 = result2.getLocation().getLocationCoordinates();

      // Extract coordinates - handle both GeoJsonPoint and Map deserialization
      double x1, y1, x2, y2;
      if (coords1 instanceof GeoJsonPoint) {
        GeoJsonPoint point1 = (GeoJsonPoint) coords1;
        x1 = point1.getX();
        y1 = point1.getY();
      } else if (coords1 instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map1 = (Map<String, Object>) coords1;
        @SuppressWarnings("unchecked")
        List<Object> coordsList1 = (List<Object>) map1.get("coordinates");
        x1 = getDoubleValue(coordsList1.get(0));
        y1 = getDoubleValue(coordsList1.get(1));
      } else {
        // If it's not a GeoJsonPoint or Map, skip coordinate comparison
        return;
      }

      if (coords2 instanceof GeoJsonPoint) {
        GeoJsonPoint point2 = (GeoJsonPoint) coords2;
        x2 = point2.getX();
        y2 = point2.getY();
      } else if (coords2 instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map2 = (Map<String, Object>) coords2;
        @SuppressWarnings("unchecked")
        List<Object> coordsList2 = (List<Object>) map2.get("coordinates");
        x2 = getDoubleValue(coordsList2.get(0));
        y2 = getDoubleValue(coordsList2.get(1));
      } else {
        // If it's not a GeoJsonPoint or Map, skip coordinate comparison
        return;
      }

      assertThat(x1).isEqualTo(x2);
      assertThat(y1).isEqualTo(y2);
    }
  }

  private Inventory createTestInventoryWithGeoJsonPoint() {
    Inventory inventory = new Inventory();
    inventory.setId("inventory-geo-test");
    inventory.setName("Test Inventory with Geo");
    inventory.setArchived(false); // active = !archived
    inventory.setExternalId("ext-geo-123");
    inventory.setReferenceId("ref-geo-123");
    inventory.setType("DIGITAL");
    inventory.setEnvironment("OUTDOOR");
    inventory.setFormat("LED");

    // Create location with GeoJsonPoint
    Inventory.Location location = new Inventory.Location();
    location.setLocationCoordinates(new GeoJsonPoint(12.345, 67.890));
    location.setAddress("123 Test Street");
    location.setCountry("Test Country");
    location.setState("Test State");
    location.setCity("Test City");
    location.setZipCode("12345");
    inventory.setLocation(location);

    // Setup operating times
    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    operatingTimesMap.put(Inventory.Weekday.MONDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.TUESDAY, List.of(operatingTime));
    inventory.setOperatingTimes(operatingTimesMap);

    // Setup digital fields
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    return inventory;
  }

  private Inventory createTestInventoryWithNullGeoJsonPoint() {
    Inventory inventory = new Inventory();
    inventory.setId("inventory-null-geo-test");
    inventory.setName("Test Inventory with Null Geo");
    inventory.setArchived(false); // active = !archived
    inventory.setExternalId("ext-null-geo-123");
    inventory.setReferenceId("ref-null-geo-123");
    inventory.setType("DIGITAL");
    inventory.setEnvironment("OUTDOOR");
    inventory.setFormat("LED");

    // Create location with null GeoJsonPoint
    Inventory.Location location = new Inventory.Location();
    location.setLocationCoordinates(null); // This should not cause deserialization errors
    location.setAddress("123 Test Street");
    location.setCountry("Test Country");
    location.setState("Test State");
    location.setCity("Test City");
    location.setZipCode("12345");
    inventory.setLocation(location);

    // Setup operating times
    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    operatingTimesMap.put(Inventory.Weekday.MONDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.TUESDAY, List.of(operatingTime));
    inventory.setOperatingTimes(operatingTimesMap);

    // Setup digital fields
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    inventory.setDigitalFields(digitalFields);

    return inventory;
  }

  /** Helper method to extract double value from various numeric types */
  private double getDoubleValue(Object value) {
    if (value instanceof Double) {
      return (Double) value;
    } else if (value instanceof Integer) {
      return ((Integer) value).doubleValue();
    } else if (value instanceof Long) {
      return ((Long) value).doubleValue();
    } else if (value instanceof Float) {
      return ((Float) value).doubleValue();
    } else if (value instanceof Number) {
      return ((Number) value).doubleValue();
    } else {
      throw new AssertionError("Cannot convert to double: " + value.getClass());
    }
  }
}
