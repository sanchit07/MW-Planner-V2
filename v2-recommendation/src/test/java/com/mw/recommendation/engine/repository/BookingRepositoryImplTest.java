package com.mw.recommendation.engine.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.BookingData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class BookingRepositoryImplTest {

  @Mock private MongoTemplate mongoTemplate;

  @InjectMocks private BookingRepositoryImpl bookingRepositoryImpl;

  private BookingData testBookingData;
  private String inventoryId;
  private LocalDate date;

  @BeforeEach
  void setUp() {
    inventoryId = "inventory-123";
    date = LocalDate.of(2024, 1, 15);

    testBookingData = new BookingData();
    testBookingData.setInventoryId(inventoryId);
    testBookingData.setDate(date);
  }

  @Test
  void testUpsertBookingData_WhenNoExistingRecord_CreatesNewRecord() {
    // Arrange
    when(mongoTemplate.findOne(any(Query.class), eq(BookingData.class))).thenReturn(null);
    when(mongoTemplate.save(testBookingData)).thenReturn(testBookingData);

    // Act
    BookingData result = bookingRepositoryImpl.upsertBookingData(testBookingData);

    // Assert
    assertNotNull(result);
    verify(mongoTemplate).findOne(any(Query.class), eq(BookingData.class));
    verify(mongoTemplate).save(testBookingData);
  }

  @Test
  void testUpsertBookingData_WhenExistingRecord_MergesAndUpdates() {
    // Arrange
    BookingData existing = new BookingData();
    existing.setInventoryId(inventoryId);
    existing.setDate(date);
    existing.setHourlyBookings(new HashMap<>());

    when(mongoTemplate.findOne(any(Query.class), eq(BookingData.class))).thenReturn(existing);
    when(mongoTemplate.save(existing)).thenReturn(existing);

    // Set up new booking data with hourly bookings
    Map<String, List<BookingData.DealBooking>> hourlyBookings = new HashMap<>();
    List<BookingData.DealBooking> dealBookings = new ArrayList<>();
    BookingData.DealBooking dealBooking = new BookingData.DealBooking();
    dealBooking.setDealId("deal-1");
    dealBooking.setPercentage(50.0);
    dealBookings.add(dealBooking);
    hourlyBookings.put("10", dealBookings);
    testBookingData.setHourlyBookings(hourlyBookings);

    // Act
    BookingData result = bookingRepositoryImpl.upsertBookingData(testBookingData);

    // Assert
    assertNotNull(result);
    verify(mongoTemplate).findOne(any(Query.class), eq(BookingData.class));
    verify(mongoTemplate).save(existing);
  }

  @Test
  void testUpsertBookingData_WhenExistingRecordWithSameDeal_MergesPercentages() {
    // Arrange
    BookingData existing = new BookingData();
    existing.setInventoryId(inventoryId);
    existing.setDate(date);

    // Existing hourly bookings
    Map<String, List<BookingData.DealBooking>> existingHourlyBookings = new HashMap<>();
    List<BookingData.DealBooking> existingDealBookings = new ArrayList<>();
    BookingData.DealBooking existingDeal = new BookingData.DealBooking();
    existingDeal.setDealId("deal-1");
    existingDeal.setPercentage(30.0);
    existingDealBookings.add(existingDeal);
    existingHourlyBookings.put("10", existingDealBookings);
    existing.setHourlyBookings(existingHourlyBookings);

    // New booking data with same deal
    Map<String, List<BookingData.DealBooking>> newHourlyBookings = new HashMap<>();
    List<BookingData.DealBooking> newDealBookings = new ArrayList<>();
    BookingData.DealBooking newDeal = new BookingData.DealBooking();
    newDeal.setDealId("deal-1");
    newDeal.setPercentage(20.0);
    newDealBookings.add(newDeal);
    newHourlyBookings.put("10", newDealBookings);
    testBookingData.setHourlyBookings(newHourlyBookings);

    when(mongoTemplate.findOne(any(Query.class), eq(BookingData.class))).thenReturn(existing);
    when(mongoTemplate.save(existing)).thenReturn(existing);

    // Act
    BookingData result = bookingRepositoryImpl.upsertBookingData(testBookingData);

    // Assert
    assertNotNull(result);
    assertEquals(50.0, existing.getHourlyBookings().get("10").get(0).getPercentage(), 0.01);
    verify(mongoTemplate).save(existing);
  }

  @Test
  void testUpsertBookingData_WhenExistingRecordWithDifferentDeal_AddsNewDeal() {
    // Arrange
    BookingData existing = new BookingData();
    existing.setInventoryId(inventoryId);
    existing.setDate(date);

    // Existing hourly bookings with deal-1
    Map<String, List<BookingData.DealBooking>> existingHourlyBookings = new HashMap<>();
    List<BookingData.DealBooking> existingDealBookings = new ArrayList<>();
    BookingData.DealBooking existingDeal = new BookingData.DealBooking();
    existingDeal.setDealId("deal-1");
    existingDeal.setPercentage(30.0);
    existingDealBookings.add(existingDeal);
    existingHourlyBookings.put("10", existingDealBookings);
    existing.setHourlyBookings(existingHourlyBookings);

    // New booking data with different deal
    Map<String, List<BookingData.DealBooking>> newHourlyBookings = new HashMap<>();
    List<BookingData.DealBooking> newDealBookings = new ArrayList<>();
    BookingData.DealBooking newDeal = new BookingData.DealBooking();
    newDeal.setDealId("deal-2");
    newDeal.setPercentage(20.0);
    newDealBookings.add(newDeal);
    newHourlyBookings.put("10", newDealBookings);
    testBookingData.setHourlyBookings(newHourlyBookings);

    when(mongoTemplate.findOne(any(Query.class), eq(BookingData.class))).thenReturn(existing);
    when(mongoTemplate.save(existing)).thenReturn(existing);

    // Act
    BookingData result = bookingRepositoryImpl.upsertBookingData(testBookingData);

    // Assert
    assertNotNull(result);
    assertEquals(2, existing.getHourlyBookings().get("10").size());
    verify(mongoTemplate).save(existing);
  }
}
