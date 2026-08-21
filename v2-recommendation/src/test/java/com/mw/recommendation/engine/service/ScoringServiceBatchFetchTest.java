package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.brand.lib.dto.BrandResponseDTO;
import com.mw.brand.lib.service.BrandService;
import com.mw.recommendation.engine.domain.BookingData;
import com.mw.recommendation.engine.repository.BookingRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
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
 * Unit tests for batch fetching methods in ScoringServiceImpl. Tests verify: 1. Batch fetch methods
 * perform single DB/API calls instead of N+1 queries 2. Correct grouping of results by
 * inventory/brand ID 3. Proper handling of edge cases (empty lists, null data, etc.) 4. Performance
 * logging and metrics
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScoringService - Batch Fetch Methods Tests")
class ScoringServiceBatchFetchTest {

  @Mock private InventoryRepository inventoryRepository;
  @Mock private BookingRepository bookingRepository;
  @Mock private BrandService brandService;
  @Mock private PlannerAvailabilityService plannerAvailabilityService;

  @InjectMocks private ScoringServiceImpl scoringService;

  private static final LocalDate START_DATE = LocalDate.of(2025, 1, 1);
  private static final LocalDate END_DATE = LocalDate.of(2025, 1, 31);

  @BeforeEach
  void setUp() {
    // Common setup if needed
  }

  @Test
  @DisplayName("batchFetchBookingData should fetch all booking data in single query")
  void testBatchFetchBookingDataSingleQuery() {
    // Arrange
    List<String> inventoryIds = List.of("inv-1", "inv-2", "inv-3");
    List<BookingData> mockBookingData = createMockBookingData();

    when(bookingRepository.findByInventoryIdInAndDateRange(inventoryIds, START_DATE, END_DATE))
        .thenReturn(mockBookingData);

    // Act
    Map<String, List<BookingData>> result =
        scoringService.batchFetchBookingData(inventoryIds, START_DATE, END_DATE);

    // Assert
    verify(bookingRepository, times(1))
        .findByInventoryIdInAndDateRange(inventoryIds, START_DATE, END_DATE);
    verify(bookingRepository, never()).findByInventoryIdAndDateRange(anyString(), any(), any());

    assertNotNull(result);
    assertEquals(3, result.size());
    assertTrue(result.containsKey("inv-1"));
    assertTrue(result.containsKey("inv-2"));
    assertTrue(result.containsKey("inv-3"));
  }

  @Test
  @DisplayName("canonical availability replaces engine booking data, including empty records")
  void testCanonicalOverlayReplacesEngineData() {
    List<String> inventoryIds = List.of("inv-1", "inv-2");
    // Engine booking_data has stale bookings for both inventories
    when(bookingRepository.findByInventoryIdInAndDateRange(inventoryIds, START_DATE, END_DATE))
        .thenReturn(
            List.of(
                createBookingData("inv-1", LocalDate.of(2025, 1, 1)),
                createBookingData("inv-2", LocalDate.of(2025, 1, 1))));
    // Canonical store: inv-1 has a synced record with NO bookings in window (fully
    // available — e.g. bookings outside the flight); inv-2 has no canonical record.
    when(plannerAvailabilityService.isEnabled()).thenReturn(true);
    when(plannerAvailabilityService.fetchBookingData(any(), eq(START_DATE), eq(END_DATE)))
        .thenReturn(Map.of("inv-1", List.of()));

    Map<String, List<BookingData>> result =
        scoringService.batchFetchBookingData(inventoryIds, START_DATE, END_DATE);

    // inv-1: canonical empty list replaces stale engine bookings
    assertTrue(result.containsKey("inv-1"));
    assertTrue(result.get("inv-1").isEmpty(), "canonical empty record must clear stale bookings");
    // inv-2: falls back to engine booking data
    assertEquals(1, result.get("inv-2").size());
  }

  @Test
  @DisplayName(
      "fully booked canonical window drives availability to ~0; short slot barely dents it")
  void testAvailabilityThroughScoring() {
    com.mw.recommendation.engine.domain.Inventory inv =
        new com.mw.recommendation.engine.domain.Inventory();
    inv.setInventoryId("inv-1");
    com.mw.recommendation.engine.domain.Inventory.DigitalFields df =
        new com.mw.recommendation.engine.domain.Inventory.DigitalFields();
    df.setSpotsPerLoop(4);
    inv.setDigitalFields(df);
    // Operate all day, every day
    java.util.Map<
            com.mw.recommendation.engine.domain.Inventory.Weekday,
            List<com.mw.recommendation.engine.domain.Inventory.OperatingTime>>
        ot = new java.util.EnumMap<>(com.mw.recommendation.engine.domain.Inventory.Weekday.class);
    for (var wd : com.mw.recommendation.engine.domain.Inventory.Weekday.values()) {
      ot.put(
          wd,
          List.of(
              com.mw.recommendation.engine.domain.Inventory.OperatingTime.builder()
                  .start("00:00:00")
                  .end("23:59:59")
                  .build()));
    }
    inv.setOperatingTimes(ot);

    PlannerAvailabilityService converter = new PlannerAvailabilityService("", "mw-planner");
    LocalDate start = LocalDate.of(2026, 9, 1);
    LocalDate end = LocalDate.of(2026, 9, 7);

    // Case 1: all 4 positions booked for the whole window → availability ~0 → excludable
    org.bson.Document fullSlot =
        new org.bson.Document("startTime", "2026-09-01T00:00:00Z")
            .append("endTime", "2026-09-07T23:59:59Z")
            .append("timeZone", "UTC")
            .append("slotPositions", List.of(1, 2, 3, 4));
    org.bson.Document fullPayload =
        new org.bson.Document(
            "bookings",
            List.of(
                new org.bson.Document("dealId", "DL-FULL")
                    .append("status", "booked")
                    .append("slots", List.of(fullSlot))));
    when(bookingRepository.findByInventoryIdAndDateRange("inv-1", start, end))
        .thenReturn(converter.convertPayload("inv-1", inv, fullPayload, start, end));
    Double fullAvail = scoringService.calculateAvailability(inv, start, end);
    assertTrue(
        fullAvail < RecommendationAsyncService.AVAILABILITY_EXCLUDE_BELOW_PCT,
        "fully booked window must fall below the exclusion threshold, was " + fullAvail);

    // Case 2: a single fully-allocated one-hour slot must NOT read as sold out
    org.bson.Document hourSlot =
        new org.bson.Document("startTime", "2026-09-01T12:00:00Z")
            .append("endTime", "2026-09-01T13:00:00Z")
            .append("timeZone", "UTC")
            .append("slotPositions", List.of(1, 2, 3, 4));
    org.bson.Document hourPayload =
        new org.bson.Document(
            "bookings",
            List.of(
                new org.bson.Document("dealId", "DL-HOUR")
                    .append("status", "booked")
                    .append("slots", List.of(hourSlot))));
    when(bookingRepository.findByInventoryIdAndDateRange("inv-1", start, end))
        .thenReturn(converter.convertPayload("inv-1", inv, hourPayload, start, end));
    Double hourAvail = scoringService.calculateAvailability(inv, start, end);
    assertTrue(
        hourAvail > 90.0,
        "one booked hour out of a week must stay highly available, was " + hourAvail);
  }

  @Test
  @DisplayName("batchFetchBookingData should group results correctly by inventoryId")
  void testBatchFetchBookingDataGrouping() {
    // Arrange
    List<String> inventoryIds = List.of("inv-1", "inv-2", "inv-3");

    BookingData booking1a = createBookingData("inv-1", LocalDate.of(2025, 1, 1));
    BookingData booking1b = createBookingData("inv-1", LocalDate.of(2025, 1, 2));
    BookingData booking2 = createBookingData("inv-2", LocalDate.of(2025, 1, 1));
    BookingData booking3a = createBookingData("inv-3", LocalDate.of(2025, 1, 1));
    BookingData booking3b = createBookingData("inv-3", LocalDate.of(2025, 1, 2));
    BookingData booking3c = createBookingData("inv-3", LocalDate.of(2025, 1, 3));

    List<BookingData> mockBookingData =
        List.of(booking1a, booking1b, booking2, booking3a, booking3b, booking3c);

    when(bookingRepository.findByInventoryIdInAndDateRange(inventoryIds, START_DATE, END_DATE))
        .thenReturn(mockBookingData);

    // Act
    Map<String, List<BookingData>> result =
        scoringService.batchFetchBookingData(inventoryIds, START_DATE, END_DATE);

    // Assert
    assertEquals(3, result.size(), "Should have 3 inventory groups");
    assertEquals(2, result.get("inv-1").size(), "inv-1 should have 2 bookings");
    assertEquals(1, result.get("inv-2").size(), "inv-2 should have 1 booking");
    assertEquals(3, result.get("inv-3").size(), "inv-3 should have 3 bookings");

    // Verify correct booking data is grouped
    assertTrue(result.get("inv-1").contains(booking1a));
    assertTrue(result.get("inv-1").contains(booking1b));
    assertTrue(result.get("inv-2").contains(booking2));
    assertTrue(result.get("inv-3").contains(booking3a));
    assertTrue(result.get("inv-3").contains(booking3b));
    assertTrue(result.get("inv-3").contains(booking3c));
  }

  @Test
  @DisplayName("batchFetchBookingData should handle empty inventory list")
  void testBatchFetchBookingDataEmptyList() {
    // Act
    Map<String, List<BookingData>> result =
        scoringService.batchFetchBookingData(Collections.emptyList(), START_DATE, END_DATE);

    // Assert
    verify(bookingRepository, never()).findByInventoryIdInAndDateRange(anyList(), any(), any());
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("batchFetchBookingData should handle null inventory list")
  void testBatchFetchBookingDataNullList() {
    // Act
    Map<String, List<BookingData>> result =
        scoringService.batchFetchBookingData(null, START_DATE, END_DATE);

    // Assert
    verify(bookingRepository, never()).findByInventoryIdInAndDateRange(anyList(), any(), any());
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("batchFetchBookingData should handle no booking data found")
  void testBatchFetchBookingDataNoDataFound() {
    // Arrange
    List<String> inventoryIds = List.of("inv-1", "inv-2", "inv-3");

    when(bookingRepository.findByInventoryIdInAndDateRange(inventoryIds, START_DATE, END_DATE))
        .thenReturn(Collections.emptyList());

    // Act
    Map<String, List<BookingData>> result =
        scoringService.batchFetchBookingData(inventoryIds, START_DATE, END_DATE);

    // Assert
    assertNotNull(result);
    assertTrue(result.isEmpty(), "Should return empty map when no booking data found");
  }

  @Test
  @DisplayName("batchFetchBrandData should fetch all brands with single API call per brand")
  void testBatchFetchBrandDataSingleCallPerBrand() {
    // Arrange
    List<String> brandIds = List.of("brand-1", "brand-2", "brand-3");

    BrandResponseDTO brand1 = createMockBrand("brand-1", "Brand One");
    BrandResponseDTO brand2 = createMockBrand("brand-2", "Brand Two");
    BrandResponseDTO brand3 = createMockBrand("brand-3", "Brand Three");

    when(brandService.getBrandById("brand-1")).thenReturn(Optional.of(brand1));
    when(brandService.getBrandById("brand-2")).thenReturn(Optional.of(brand2));
    when(brandService.getBrandById("brand-3")).thenReturn(Optional.of(brand3));

    // Act
    Map<String, BrandResponseDTO> result = scoringService.batchFetchBrandData(brandIds);

    // Assert
    verify(brandService, times(1)).getBrandById("brand-1");
    verify(brandService, times(1)).getBrandById("brand-2");
    verify(brandService, times(1)).getBrandById("brand-3");

    assertEquals(3, result.size());
    assertEquals(brand1, result.get("brand-1"));
    assertEquals(brand2, result.get("brand-2"));
    assertEquals(brand3, result.get("brand-3"));
  }

  @Test
  @DisplayName("batchFetchBrandData should deduplicate brand IDs before fetching")
  void testBatchFetchBrandDataDeduplication() {
    // Arrange: Duplicate brand IDs
    List<String> brandIds = List.of("brand-1", "brand-2", "brand-1", "brand-2", "brand-1");

    BrandResponseDTO brand1 = createMockBrand("brand-1", "Brand One");
    BrandResponseDTO brand2 = createMockBrand("brand-2", "Brand Two");

    when(brandService.getBrandById("brand-1")).thenReturn(Optional.of(brand1));
    when(brandService.getBrandById("brand-2")).thenReturn(Optional.of(brand2));

    // Act
    Map<String, BrandResponseDTO> result = scoringService.batchFetchBrandData(brandIds);

    // Assert: Each brand fetched only once despite duplicates
    verify(brandService, times(1)).getBrandById("brand-1");
    verify(brandService, times(1)).getBrandById("brand-2");

    assertEquals(2, result.size());
    assertEquals(brand1, result.get("brand-1"));
    assertEquals(brand2, result.get("brand-2"));
  }

  @Test
  @DisplayName("batchFetchBrandData should handle empty brand list")
  void testBatchFetchBrandDataEmptyList() {
    // Act
    Map<String, BrandResponseDTO> result =
        scoringService.batchFetchBrandData(Collections.emptyList());

    // Assert
    verify(brandService, never()).getBrandById(anyString());
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("batchFetchBrandData should handle null brand list")
  void testBatchFetchBrandDataNullList() {
    // Act
    Map<String, BrandResponseDTO> result = scoringService.batchFetchBrandData(null);

    // Assert
    verify(brandService, never()).getBrandById(anyString());
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("batchFetchBrandData should handle brand not found gracefully")
  void testBatchFetchBrandDataBrandNotFound() {
    // Arrange
    List<String> brandIds = List.of("brand-1", "brand-2", "brand-3");

    BrandResponseDTO brand1 = createMockBrand("brand-1", "Brand One");
    // brand-2 not found (returns empty Optional)
    BrandResponseDTO brand3 = createMockBrand("brand-3", "Brand Three");

    when(brandService.getBrandById("brand-1")).thenReturn(Optional.of(brand1));
    when(brandService.getBrandById("brand-2")).thenReturn(Optional.empty());
    when(brandService.getBrandById("brand-3")).thenReturn(Optional.of(brand3));

    // Act
    Map<String, BrandResponseDTO> result = scoringService.batchFetchBrandData(brandIds);

    // Assert
    assertEquals(2, result.size(), "Should only include brands that were found");
    assertTrue(result.containsKey("brand-1"));
    assertFalse(result.containsKey("brand-2"), "Missing brand should not be in result");
    assertTrue(result.containsKey("brand-3"));
  }

  @Test
  @DisplayName("batchFetchBrandData should handle API exception gracefully")
  void testBatchFetchBrandDataApiException() {
    // Arrange
    List<String> brandIds = List.of("brand-1", "brand-2", "brand-3");

    BrandResponseDTO brand1 = createMockBrand("brand-1", "Brand One");
    BrandResponseDTO brand3 = createMockBrand("brand-3", "Brand Three");

    when(brandService.getBrandById("brand-1")).thenReturn(Optional.of(brand1));
    when(brandService.getBrandById("brand-2"))
        .thenThrow(new RuntimeException("API connection failed"));
    when(brandService.getBrandById("brand-3")).thenReturn(Optional.of(brand3));

    // Act
    Map<String, BrandResponseDTO> result = scoringService.batchFetchBrandData(brandIds);

    // Assert: Should continue after exception and return available brands
    assertEquals(2, result.size(), "Should return other brands despite one failure");
    assertTrue(result.containsKey("brand-1"));
    assertFalse(result.containsKey("brand-2"), "Failed brand should not be in result");
    assertTrue(result.containsKey("brand-3"));
  }

  @Test
  @DisplayName("batchFetchBookingData should handle large inventory list (1000+)")
  void testBatchFetchBookingDataLargeList() {
    // Arrange: 1000 inventory IDs
    List<String> inventoryIds = new ArrayList<>();
    List<BookingData> mockBookingData = new ArrayList<>();

    for (int i = 0; i < 1000; i++) {
      String invId = "inv-" + i;
      inventoryIds.add(invId);
      mockBookingData.add(createBookingData(invId, START_DATE));
    }

    when(bookingRepository.findByInventoryIdInAndDateRange(inventoryIds, START_DATE, END_DATE))
        .thenReturn(mockBookingData);

    // Act
    long startTime = System.currentTimeMillis();
    Map<String, List<BookingData>> result =
        scoringService.batchFetchBookingData(inventoryIds, START_DATE, END_DATE);
    long duration = System.currentTimeMillis() - startTime;

    // Assert
    verify(bookingRepository, times(1))
        .findByInventoryIdInAndDateRange(inventoryIds, START_DATE, END_DATE);
    assertEquals(1000, result.size());

    System.out.printf("Batch fetched booking data for 1000 inventories in %d ms%n", duration);
  }

  // ---- Helper Methods ----

  private List<BookingData> createMockBookingData() {
    List<BookingData> bookingDataList = new ArrayList<>();

    bookingDataList.add(createBookingData("inv-1", LocalDate.of(2025, 1, 1)));
    bookingDataList.add(createBookingData("inv-1", LocalDate.of(2025, 1, 2)));
    bookingDataList.add(createBookingData("inv-2", LocalDate.of(2025, 1, 1)));
    bookingDataList.add(createBookingData("inv-3", LocalDate.of(2025, 1, 1)));

    return bookingDataList;
  }

  private BookingData createBookingData(String inventoryId, LocalDate date) {
    BookingData booking = new BookingData();
    booking.setInventoryId(inventoryId);
    booking.setDate(date);
    // Create sample hourly bookings for digital inventory
    Map<String, List<BookingData.DealBooking>> hourlyBookings = new HashMap<>();
    for (int hour = 9; hour <= 17; hour++) {
      List<BookingData.DealBooking> dealBookings = new ArrayList<>();
      dealBookings.add(
          BookingData.DealBooking.builder().dealId("deal-001").percentage(50.0).build());
      hourlyBookings.put(String.valueOf(hour), dealBookings);
    }
    booking.setHourlyBookings(hourlyBookings);
    return booking;
  }

  private BrandResponseDTO createMockBrand(String brandId, String brandName) {
    // BrandResponseDTO is from external library, use lenient mock
    // We only care about the object reference for testing, not the actual data
    return mock(BrandResponseDTO.class);
  }
}
