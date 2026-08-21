package com.mw.recommendation.engine.repository;

import com.mw.recommendation.engine.domain.BookingData;

/** Custom repository interface for BookingData with atomic upsert operations. */
public interface BookingRepositoryCustom {

  /**
   * Atomically upsert booking data. If a record exists for the (inventoryId, date) combination, it
   * will be updated. Otherwise, a new record will be created. This method is thread-safe and
   * handles race conditions.
   *
   * @param bookingData The booking data to upsert
   * @return The saved booking data
   */
  BookingData upsertBookingData(BookingData bookingData);
}
