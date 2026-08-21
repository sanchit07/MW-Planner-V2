package com.mw.recommendation.engine.service;

import com.mw.recommendation.engine.domain.BookingData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.ExternalBookingMessageDTO;
import com.mw.recommendation.engine.rabbitmq.BookingMessageConverter;
import com.mw.recommendation.engine.repository.BookingRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for processing booking messages and handling all business logic related to
 * booking data and their persistence.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingProcessingService {

  private final BookingMessageConverter messageConverter;
  private final BookingRepository bookingRepository;
  private final InventoryRepository inventoryRepository;

  // Maximum retry attempts for handling race conditions
  private static final int MAX_RETRY_ATTEMPTS = 3;

  /**
   * Process a booking message and handle all related business logic. Note: MongoDB operations are
   * atomic at the document level, so @Transactional is not needed.
   *
   * @param message The external booking message to process
   */
  public void processBookingMessage(ExternalBookingMessageDTO message) {
    String dealId = message.getDealId();
    log.info("Processing booking message for dealId: {}", dealId);

    try {
      if (message.getSlots() == null || message.getSlots().isEmpty()) {
        log.warn("No slots found in booking message for dealId: {}", dealId);
        return;
      }

      // Group slots by inventory ID
      Map<String, List<ExternalBookingMessageDTO.Slot>> slotsByInventory =
          message.getSlots().stream()
              .collect(
                  java.util.stream.Collectors.groupingBy(
                      ExternalBookingMessageDTO.Slot::getInventoryId));

      // Process each inventory's slots separately
      for (Map.Entry<String, List<ExternalBookingMessageDTO.Slot>> entry :
          slotsByInventory.entrySet()) {
        String inventoryId = entry.getKey();
        List<ExternalBookingMessageDTO.Slot> slots = entry.getValue();

        // Fetch inventory to determine if it's Digital or Classic
        Inventory inventory = inventoryRepository.findFirstByInventoryId(inventoryId).orElse(null);

        if (inventory == null) {
          log.warn(
              "Inventory {} not found for slots in dealId: {}, skipping booking data creation",
              inventoryId,
              dealId);
          continue;
        }

        // Create a temporary message with only this inventory's slots
        ExternalBookingMessageDTO tempMessage =
            ExternalBookingMessageDTO.builder()
                .id(message.getId())
                .dealId(message.getDealId())
                .slots(slots)
                .build();

        // Convert booking message to booking data (returns list of BookingData per date)
        List<BookingData> bookingDataList =
            messageConverter.convertToBookingData(tempMessage, inventory);

        // Save each booking data (one per inventoryId-date combination)
        for (BookingData bookingData : bookingDataList) {
          saveBookingData(bookingData);
        }
      }

      log.info("Successfully processed booking message for dealId: {}", dealId);

    } catch (Exception e) {
      log.error("Error processing booking message for dealId: {}: {}", dealId, e.getMessage(), e);
      throw new RuntimeException("Failed to process booking message", e);
    }
  }

  /**
   * Save booking data to database with upsert logic. Merges new booking data with existing data.
   * Uses atomic upsert operation to handle race conditions.
   *
   * @param newBookingData The new booking data to save (for a specific inventoryId-date
   *     combination)
   */
  private void saveBookingData(BookingData newBookingData) {
    String inventoryId = newBookingData.getInventoryId();
    java.time.LocalDate date = newBookingData.getDate();

    int retryCount = 0;
    boolean success = false;

    while (!success && retryCount < MAX_RETRY_ATTEMPTS) {
      try {
        // Try to find existing booking data
        BookingData existingBookingData =
            bookingRepository.findByInventoryIdAndDate(inventoryId, date).orElse(null);

        if (existingBookingData != null) {
          // Merge new booking data with existing data
          log.debug("Merging booking data for inventoryId: {} and date: {}", inventoryId, date);
          mergeBookingData(existingBookingData, newBookingData);
          bookingRepository.save(existingBookingData);
          log.debug("Updated booking data for inventoryId: {} and date: {}", inventoryId, date);
          success = true;
        } else {
          // Try to create new booking data
          log.debug(
              "Creating new booking data for inventoryId: {} and date: {}", inventoryId, date);
          try {
            BookingData savedBookingData = bookingRepository.save(newBookingData);
            log.debug(
                "Created new booking data with ID: {} for inventoryId: {} and date: {}",
                savedBookingData.getId(),
                inventoryId,
                date);
            success = true;
          } catch (org.springframework.dao.DuplicateKeyException e) {
            // Race condition: another thread created the record between our check and save
            // Retry by fetching and updating
            log.debug(
                "Duplicate key detected for inventoryId: {} and date: {}, retrying (attempt {})",
                inventoryId,
                date,
                retryCount + 1);
            retryCount++;
            if (retryCount >= MAX_RETRY_ATTEMPTS) {
              // Last attempt: use atomic upsert
              log.debug("Using atomic upsert for inventoryId: {} and date: {}", inventoryId, date);
              bookingRepository.upsertBookingData(newBookingData);
              success = true;
            }
            // Small delay to allow other thread to complete
            Thread.sleep(10);
          }
        }
      } catch (org.springframework.dao.DuplicateKeyException e) {
        // Handle duplicate key exception
        log.warn(
            "Duplicate key exception for inventoryId: {} and date: {}, retrying (attempt {})",
            inventoryId,
            date,
            retryCount + 1);
        retryCount++;
        if (retryCount >= MAX_RETRY_ATTEMPTS) {
          // Last attempt: use atomic upsert
          log.debug("Using atomic upsert for inventoryId: {} and date: {}", inventoryId, date);
          bookingRepository.upsertBookingData(newBookingData);
          success = true;
        } else {
          try {
            Thread.sleep(10);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during retry", ie);
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Interrupted during save operation", e);
      } catch (Exception e) {
        log.error(
            "Error saving booking data for inventoryId: {} and date: {}: {}",
            inventoryId,
            date,
            e.getMessage(),
            e);
        throw new RuntimeException("Failed to save booking data", e);
      }
    }

    if (!success) {
      throw new RuntimeException(
          "Failed to save booking data after " + MAX_RETRY_ATTEMPTS + " attempts");
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
        existing.setHourlyBookings(new java.util.HashMap<>());
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
      Map<String, java.util.List<BookingData.DealBooking>> existingHourlyBookings,
      Map<String, java.util.List<BookingData.DealBooking>> newHourlyBookings) {

    for (Map.Entry<String, java.util.List<BookingData.DealBooking>> entry :
        newHourlyBookings.entrySet()) {
      String hourKey = entry.getKey();
      java.util.List<BookingData.DealBooking> newDealBookings = entry.getValue();

      java.util.List<BookingData.DealBooking> existingDealBookings =
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
      java.util.List<BookingData.DealBooking> existingDealBookings,
      java.util.List<BookingData.DealBooking> newDealBookings) {

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
