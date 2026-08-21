package com.mw.recommendation.engine.repository;

import com.mw.recommendation.engine.domain.BookingData;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository for BookingData entities. Provides basic CRUD operations for booking data. One
 * document per (inventoryId, date) combination.
 */
@Repository
public interface BookingRepository
    extends MongoRepository<BookingData, String>, BookingRepositoryCustom {

  /** Find booking data by inventory ID and date */
  Optional<BookingData> findByInventoryIdAndDate(String inventoryId, LocalDate date);

  /** Find booking data for multiple inventory IDs */
  @Query("{ 'inventoryId': { $in: ?0 } }")
  List<BookingData> findByInventoryIdIn(List<String> inventoryIds);

  /**
   * Find booking data for a specific inventory and date range. Used for availability scoring.
   *
   * @param inventoryId The inventory ID
   * @param startDate Start date
   * @param endDate End date
   * @return List of BookingData for the date range
   */
  @Query("{ 'inventoryId': ?0, 'date': { $gte: ?1, $lte: ?2 } }")
  List<BookingData> findByInventoryIdAndDateRange(
      String inventoryId, LocalDate startDate, LocalDate endDate);

  /**
   * Find booking data for multiple inventories and date range. Used for availability scoring.
   *
   * @param inventoryIds List of inventory IDs
   * @param startDate Start date
   * @param endDate End date
   * @return List of BookingData for the date range
   */
  @Query("{ 'inventoryId': { $in: ?0 }, 'date': { $gte: ?1, $lte: ?2 } }")
  List<BookingData> findByInventoryIdInAndDateRange(
      List<String> inventoryIds, LocalDate startDate, LocalDate endDate);
}
