package com.mw.recommendation.engine.repository;

import com.mw.recommendation.engine.domain.BookingData;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * Custom repository implementation for BookingData with atomic upsert operations. This provides a
 * fallback mechanism when race conditions occur.
 */
@Repository
@RequiredArgsConstructor
public class BookingRepositoryImpl implements BookingRepositoryCustom {

  private final MongoTemplate mongoTemplate;

  @Override
  public BookingData upsertBookingData(BookingData newBookingData) {
    String inventoryId = newBookingData.getInventoryId();
    LocalDate date = newBookingData.getDate();

    // Build query to find existing record
    Query query = new Query(Criteria.where("inventoryId").is(inventoryId).and("date").is(date));

    // Try to find existing record
    BookingData existing = mongoTemplate.findOne(query, BookingData.class);

    if (existing != null) {
      // Merge new booking data into existing record
      mergeBookingData(existing, newBookingData);
      return mongoTemplate.save(existing);
    } else {
      // Save new record
      return mongoTemplate.save(newBookingData);
    }
  }

  /**
   * Merge new booking data into existing booking data. Combines hourly bookings (for digital) or
   * day-level bookings (for classic).
   *
   * @param existing The existing booking data
   * @param newData The new booking data to merge
   */
  private void mergeBookingData(BookingData existing, BookingData newData) {
    // Merge hourly bookings (for digital)
    if (newData.getHourlyBookings() != null && !newData.getHourlyBookings().isEmpty()) {
      if (existing.getHourlyBookings() == null) {
        existing.setHourlyBookings(new HashMap<>());
      }
      mergeHourlyBookings(existing.getHourlyBookings(), newData.getHourlyBookings());
    }

    // Merge day-level bookings (for classic)
    if (newData.getBooking() != null && !newData.getBooking().isEmpty()) {
      if (existing.getBooking() == null) {
        existing.setBooking(new java.util.ArrayList<>());
      }
      mergeDealBookings(existing.getBooking(), newData.getBooking());
    }
  }

  /**
   * Merge hourly bookings from new data into existing data.
   *
   * @param existingHourlyBookings Existing hourly bookings map
   * @param newHourlyBookings New hourly bookings map
   */
  private void mergeHourlyBookings(
      Map<String, List<BookingData.DealBooking>> existingHourlyBookings,
      Map<String, List<BookingData.DealBooking>> newHourlyBookings) {

    for (Map.Entry<String, List<BookingData.DealBooking>> entry : newHourlyBookings.entrySet()) {
      String hourKey = entry.getKey();
      List<BookingData.DealBooking> newDealBookings = entry.getValue();

      List<BookingData.DealBooking> existingDealBookings =
          existingHourlyBookings.computeIfAbsent(hourKey, k -> new java.util.ArrayList<>());

      mergeDealBookings(existingDealBookings, newDealBookings);
    }
  }

  /**
   * Merge deal bookings from new list into existing list. If deal exists, add percentages.
   *
   * @param existingDealBookings Existing deal bookings list
   * @param newDealBookings New deal bookings list
   */
  private void mergeDealBookings(
      List<BookingData.DealBooking> existingDealBookings,
      List<BookingData.DealBooking> newDealBookings) {

    for (BookingData.DealBooking newDealBooking : newDealBookings) {
      String dealId = newDealBooking.getDealId();
      java.util.Optional<BookingData.DealBooking> existingDeal =
          existingDealBookings.stream().filter(db -> dealId.equals(db.getDealId())).findFirst();

      if (existingDeal.isPresent()) {
        // Add to existing percentage
        double newPercentage = existingDeal.get().getPercentage() + newDealBooking.getPercentage();
        existingDeal.get().setPercentage(newPercentage);
      } else {
        // Add new deal booking
        existingDealBookings.add(newDealBooking);
      }
    }
  }
}
