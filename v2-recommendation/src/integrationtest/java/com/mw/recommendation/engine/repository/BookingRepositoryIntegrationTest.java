package com.mw.recommendation.engine.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.mw.recommendation.engine.TestcontainersConfiguration;
import com.mw.recommendation.engine.domain.BookingData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = TestcontainersConfiguration.class)
class BookingRepositoryIntegrationTest {

  @Autowired private BookingRepository bookingRepository;

  @Autowired private BookingRepositoryImpl bookingRepositoryImpl;

  private String inventoryId;
  private LocalDate date;

  @BeforeEach
  void setUp() {
    // Use unique inventory ID per test to avoid conflicts
    inventoryId = "test-inventory-" + UUID.randomUUID().toString().substring(0, 8);
    date = LocalDate.of(2024, 1, 15);
  }

  @AfterEach
  void tearDown() {
    // Clean up test data
    if (inventoryId != null) {
      bookingRepository
          .findByInventoryIdAndDateRange(
              inventoryId, LocalDate.of(2020, 1, 1), LocalDate.of(2030, 12, 31))
          .forEach(bookingRepository::delete);
    }
  }

  @Test
  void testUpsertBookingData_WhenNoExistingRecord_CreatesNewRecord() {
    // Arrange
    BookingData newBookingData = new BookingData();
    newBookingData.setInventoryId(inventoryId);
    newBookingData.setDate(date);

    Map<String, List<BookingData.DealBooking>> hourlyBookings = new HashMap<>();
    List<BookingData.DealBooking> dealBookings = new ArrayList<>();
    BookingData.DealBooking dealBooking = new BookingData.DealBooking();
    dealBooking.setDealId("deal-1");
    dealBooking.setPercentage(50.0);
    dealBookings.add(dealBooking);
    hourlyBookings.put("10", dealBookings);
    newBookingData.setHourlyBookings(hourlyBookings);

    // Act
    BookingData result = bookingRepositoryImpl.upsertBookingData(newBookingData);

    // Assert
    assertNotNull(result);
    assertNotNull(result.getId());
    Optional<BookingData> saved = bookingRepository.findByInventoryIdAndDate(inventoryId, date);
    assertTrue(saved.isPresent());
    assertEquals(inventoryId, saved.get().getInventoryId());
    assertEquals(date, saved.get().getDate());
  }

  @Test
  void testUpsertBookingData_WhenExistingRecord_MergesData() {
    // Arrange - Create existing record
    BookingData existing = new BookingData();
    existing.setInventoryId(inventoryId);
    existing.setDate(date);

    Map<String, List<BookingData.DealBooking>> existingHourlyBookings = new HashMap<>();
    List<BookingData.DealBooking> existingDealBookings = new ArrayList<>();
    BookingData.DealBooking existingDeal = new BookingData.DealBooking();
    existingDeal.setDealId("deal-1");
    existingDeal.setPercentage(30.0);
    existingDealBookings.add(existingDeal);
    existingHourlyBookings.put("10", existingDealBookings);
    existing.setHourlyBookings(existingHourlyBookings);

    bookingRepository.save(existing);

    // Create new booking data with same deal
    BookingData newBookingData = new BookingData();
    newBookingData.setInventoryId(inventoryId);
    newBookingData.setDate(date);

    Map<String, List<BookingData.DealBooking>> newHourlyBookings = new HashMap<>();
    List<BookingData.DealBooking> newDealBookings = new ArrayList<>();
    BookingData.DealBooking newDeal = new BookingData.DealBooking();
    newDeal.setDealId("deal-1");
    newDeal.setPercentage(20.0);
    newDealBookings.add(newDeal);
    newHourlyBookings.put("10", newDealBookings);
    newBookingData.setHourlyBookings(newHourlyBookings);

    // Act
    BookingData result = bookingRepositoryImpl.upsertBookingData(newBookingData);

    // Assert
    assertNotNull(result);
    Optional<BookingData> saved = bookingRepository.findByInventoryIdAndDate(inventoryId, date);
    assertTrue(saved.isPresent());
    // Percentages should be merged (30.0 + 20.0 = 50.0)
    assertEquals(50.0, saved.get().getHourlyBookings().get("10").get(0).getPercentage(), 0.01);
  }

  @Test
  void testFindByInventoryIdAndDateRange_ReturnsCorrectRecords() {
    // Arrange
    LocalDate startDate = LocalDate.of(2024, 1, 1);
    LocalDate endDate = LocalDate.of(2024, 1, 31);

    // Create booking data for multiple dates
    for (int day = 1; day <= 5; day++) {
      BookingData bookingData = new BookingData();
      bookingData.setInventoryId(inventoryId);
      bookingData.setDate(LocalDate.of(2024, 1, day));
      bookingRepository.save(bookingData);
    }

    // Act
    List<BookingData> results =
        bookingRepository.findByInventoryIdAndDateRange(inventoryId, startDate, endDate);

    // Assert
    assertNotNull(results);
    assertEquals(5, results.size());
  }
}
