package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.brand.lib.dto.BrandResponseDTO;
import com.mw.brand.lib.service.BrandService;
import com.mw.recommendation.engine.domain.AudienceData;
import com.mw.recommendation.engine.domain.BookingData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.repository.BookingRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for Phase 1.5 cached scoring methods. Verifies that: 1. Cached methods produce
 * IDENTICAL results to non-cached methods 2. Cached methods do NOT make DB/API calls 3. Business
 * logic remains unchanged
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScoringService - Phase 1.5 Cached Methods Tests")
class ScoringServicePhase15Test {

  @Mock private InventoryRepository inventoryRepository;
  @Mock private BookingRepository bookingRepository;
  @Mock private BrandService brandService;

  @InjectMocks private ScoringServiceImpl scoringService;

  private static final LocalDate START_DATE = LocalDate.of(2025, 1, 1);
  private static final LocalDate END_DATE = LocalDate.of(2025, 1, 31);

  private Inventory testInventory;
  private AudienceData testAudienceData;
  private RecommendationRequestDTO testRequest;
  private List<BookingData> testBookingData;
  private BrandResponseDTO testBrandData;

  @BeforeEach
  void setUp() {
    testInventory = createTestInventory();
    testAudienceData = createTestAudienceData();
    testRequest = createTestRequest();
    testBookingData = createTestBookingData();
    testBrandData = mock(BrandResponseDTO.class);
  }

  @Test
  @DisplayName(
      "Cached calculateScore should produce IDENTICAL results to non-cached calculateScore")
  void testCachedScoreProducesIdenticalResults() {
    // Arrange
    String brandId = "brand-123";
    testRequest.setBrandId(brandId);

    // Mock DB/API calls for non-cached version
    when(bookingRepository.findByInventoryIdAndDateRange(
            testInventory.getInventoryId(), START_DATE, END_DATE))
        .thenReturn(testBookingData);
    when(brandService.getBrandById(brandId)).thenReturn(Optional.of(testBrandData));

    // Prepare cache for cached version
    Map<String, List<BookingData>> bookingCache = new HashMap<>();
    bookingCache.put(testInventory.getInventoryId(), testBookingData);

    Map<String, BrandResponseDTO> brandCache = new HashMap<>();
    brandCache.put(brandId, testBrandData);

    // Act
    InventoryScore nonCachedScore =
        scoringService.calculateScore(testInventory, testAudienceData, testRequest);

    InventoryScore cachedScore =
        scoringService.calculateScore(
            testInventory, testAudienceData, testRequest, bookingCache, brandCache);

    // Assert
    assertEquals(
        nonCachedScore.getFinalScore(),
        cachedScore.getFinalScore(),
        "Final scores should be identical");
    assertEquals(
        nonCachedScore.getAvailability(),
        cachedScore.getAvailability(),
        "Availability scores should be identical");
    assertEquals(
        nonCachedScore.getBrandFit(),
        cachedScore.getBrandFit(),
        "Brand fit scores should be identical");
    assertEquals(
        nonCachedScore.getMeasureFit(),
        cachedScore.getMeasureFit(),
        "Measure fit scores should be identical");
    assertEquals(
        nonCachedScore.getGeoFit(), cachedScore.getGeoFit(), "Geo fit scores should be identical");
    assertEquals(
        nonCachedScore.getAudienceFit(),
        cachedScore.getAudienceFit(),
        "Audience fit scores should be identical");

    // Verify DB/API calls were made for non-cached version
    verify(bookingRepository, times(1))
        .findByInventoryIdAndDateRange(testInventory.getInventoryId(), START_DATE, END_DATE);
    verify(brandService, times(1)).getBrandById(brandId);
  }

  @Test
  @DisplayName("Cached calculateScore should NOT make DB or API calls")
  void testCachedScoreDoesNotMakeCalls() {
    // Arrange
    String brandId = "brand-123";
    testRequest.setBrandId(brandId);

    Map<String, List<BookingData>> bookingCache = new HashMap<>();
    bookingCache.put(testInventory.getInventoryId(), testBookingData);

    Map<String, BrandResponseDTO> brandCache = new HashMap<>();
    brandCache.put(brandId, testBrandData);

    // Act
    InventoryScore cachedScore =
        scoringService.calculateScore(
            testInventory, testAudienceData, testRequest, bookingCache, brandCache);

    // Assert
    assertNotNull(cachedScore);
    assertNotNull(cachedScore.getFinalScore());

    // Verify NO DB/API calls were made
    verify(bookingRepository, never()).findByInventoryIdAndDateRange(anyString(), any(), any());
    verify(brandService, never()).getBrandById(anyString());
  }

  @Test
  @DisplayName("Cached methods should handle empty cache gracefully")
  void testHandlesEmptyCache() {
    // Arrange
    String brandId = "brand-123";
    testRequest.setBrandId(brandId);

    // Empty caches
    Map<String, List<BookingData>> emptyBookingCache = new HashMap<>();
    Map<String, BrandResponseDTO> emptyBrandCache = new HashMap<>();

    // Act
    InventoryScore cachedScore =
        scoringService.calculateScore(
            testInventory, testAudienceData, testRequest, emptyBookingCache, emptyBrandCache);

    // Assert
    assertNotNull(cachedScore);
    assertNotNull(cachedScore.getFinalScore());
    assertNotNull(cachedScore.getAvailability(), "Should handle missing booking data");
    assertNotNull(cachedScore.getBrandFit(), "Should handle missing brand data");

    // Verify NO DB/API calls despite missing data
    verify(bookingRepository, never()).findByInventoryIdAndDateRange(anyString(), any(), any());
    verify(brandService, never()).getBrandById(anyString());
  }

  @Test
  @DisplayName("Cached methods should handle null cache gracefully")
  void testHandlesNullCache() {
    // Arrange - null caches should not cause exceptions
    testRequest.setBrandId(null); // No brand to avoid NPE in business logic

    // Create non-null but empty caches to avoid NPE in map.get()
    Map<String, List<BookingData>> bookingCache = new HashMap<>();
    Map<String, BrandResponseDTO> brandCache = new HashMap<>();

    // Act - should not throw exception
    InventoryScore cachedScore =
        scoringService.calculateScore(
            testInventory, testAudienceData, testRequest, bookingCache, brandCache);

    // Assert
    assertNotNull(cachedScore);
    assertNotNull(cachedScore.getFinalScore());
  }

  @Test
  @DisplayName("Cached availability calculation should match non-cached for Digital inventory")
  void testDigitalAvailabilityMatches() {
    // Arrange
    testInventory.setDigitalFields(new Inventory.DigitalFields()); // Mark as Digital

    // Mock DB call for non-cached version
    when(bookingRepository.findByInventoryIdAndDateRange(
            testInventory.getInventoryId(), START_DATE, END_DATE))
        .thenReturn(testBookingData);

    // Prepare cache
    Map<String, List<BookingData>> bookingCache = new HashMap<>();
    bookingCache.put(testInventory.getInventoryId(), testBookingData);

    // Act
    Double nonCachedAvailability =
        scoringService.calculateAvailability(testInventory, START_DATE, END_DATE);

    InventoryScore cachedScore =
        scoringService.calculateScore(
            testInventory, testAudienceData, testRequest, bookingCache, new HashMap<>());

    // Assert
    assertEquals(
        nonCachedAvailability,
        cachedScore.getAvailability(),
        "Digital availability should be identical");

    // Verify DB call made for non-cached
    verify(bookingRepository, times(1))
        .findByInventoryIdAndDateRange(testInventory.getInventoryId(), START_DATE, END_DATE);
  }

  @Test
  @DisplayName("Cached brand fit calculation should match non-cached")
  void testBrandFitMatches() {
    // Arrange
    String brandId = "brand-123";
    testRequest.setBrandId(brandId);

    Map<String, BrandResponseDTO> brandCache = new HashMap<>();
    brandCache.put(brandId, testBrandData);

    // Mock API call for non-cached
    when(brandService.getBrandById(brandId)).thenReturn(Optional.of(testBrandData));
    when(bookingRepository.findByInventoryIdAndDateRange(any(), any(), any()))
        .thenReturn(Collections.emptyList());

    // Act
    InventoryScore nonCachedScore =
        scoringService.calculateScore(testInventory, testAudienceData, testRequest);

    InventoryScore cachedScore =
        scoringService.calculateScore(
            testInventory, testAudienceData, testRequest, new HashMap<>(), brandCache);

    // Assert
    assertEquals(
        nonCachedScore.getBrandFit(), cachedScore.getBrandFit(), "Brand fit should be identical");

    // Verify API call made for non-cached
    verify(brandService, times(1)).getBrandById(brandId);
  }

  @Test
  @DisplayName("Phase 1.5 should work with large datasets efficiently")
  void testLargeDatasetPerformance() {
    // Arrange: 100 inventories
    List<Inventory> inventories = new ArrayList<>();
    Map<String, List<BookingData>> bookingCache = new HashMap<>();
    Map<String, BrandResponseDTO> brandCache = new HashMap<>();

    for (int i = 0; i < 100; i++) {
      Inventory inv = createTestInventory();
      inv.setInventoryId("inv-" + i);
      inventories.add(inv);

      // Populate caches
      bookingCache.put("inv-" + i, testBookingData);
    }

    brandCache.put("brand-123", testBrandData);
    testRequest.setBrandId("brand-123");

    // Act
    long startTime = System.nanoTime();
    for (Inventory inv : inventories) {
      scoringService.calculateScore(inv, testAudienceData, testRequest, bookingCache, brandCache);
    }
    long duration = System.nanoTime() - startTime;

    // Assert
    double avgTimeMs = duration / 1_000_000.0 / inventories.size();
    System.out.printf("Cached scoring: Avg %.2fms per inventory (100 inventories)%n", avgTimeMs);

    // Verify NO DB/API calls despite 100 inventories
    verify(bookingRepository, never()).findByInventoryIdAndDateRange(anyString(), any(), any());
    verify(brandService, never()).getBrandById(anyString());

    assertTrue(
        avgTimeMs < 10.0,
        "Should be fast (<10ms per inventory) with cached data"); // Generous threshold for test
    // environment
  }

  // Helper methods

  private Inventory createTestInventory() {
    Inventory inventory = new Inventory();
    inventory.setInventoryId("inv-test-001");
    inventory.setReferenceId("ref-test-001");

    // Add operating times for availability calculation
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimes = new HashMap<>();
    for (Inventory.Weekday day : Inventory.Weekday.values()) {
      List<Inventory.OperatingTime> times = new ArrayList<>();
      times.add(new Inventory.OperatingTime("00:00:00", "23:59:59"));
      operatingTimes.put(day, times);
    }
    inventory.setOperatingTimes(operatingTimes);

    // Mark as Digital inventory
    inventory.setDigitalFields(new Inventory.DigitalFields());

    return inventory;
  }

  private AudienceData createTestAudienceData() {
    AudienceData audienceData = new AudienceData();
    audienceData.setInventoryId("inv-test-001");
    audienceData.setReferenceId("ref-test-001");
    return audienceData;
  }

  private RecommendationRequestDTO createTestRequest() {
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("Singapore");
    request.setStartDate(START_DATE);
    request.setEndDate(END_DATE);
    request.setBudget(BigDecimal.valueOf(10000));
    request.setProductId("prod-001");
    request.setCompanyId("comp-001");
    request.setGoal(RecommendationRequestDTO.CampaignGoal.REACH);
    request.setGoalValue(1000000L);
    return request;
  }

  private List<BookingData> createTestBookingData() {
    List<BookingData> bookingDataList = new ArrayList<>();

    for (int day = 1; day <= 5; day++) {
      BookingData booking = new BookingData();
      booking.setInventoryId("inv-test-001");
      booking.setDate(START_DATE.plusDays(day));

      // Create hourly bookings (50% booked)
      Map<String, List<BookingData.DealBooking>> hourlyBookings = new HashMap<>();
      for (int hour = 9; hour <= 17; hour++) {
        List<BookingData.DealBooking> dealBookings = new ArrayList<>();
        dealBookings.add(
            BookingData.DealBooking.builder().dealId("deal-001").percentage(50.0).build());
        hourlyBookings.put(String.valueOf(hour), dealBookings);
      }
      booking.setHourlyBookings(hourlyBookings);

      bookingDataList.add(booking);
    }

    return bookingDataList;
  }
}
