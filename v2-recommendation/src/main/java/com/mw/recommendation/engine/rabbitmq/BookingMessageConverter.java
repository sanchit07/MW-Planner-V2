package com.mw.recommendation.engine.rabbitmq;

import com.mw.recommendation.engine.domain.BookingData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.ExternalBookingMessageDTO;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Converter service to transform external booking messages to internal BookingData entities.
 * Handles calculation of booking percentages for both Digital and Classic inventory types.
 */
@Component
@Slf4j
public class BookingMessageConverter {

  private static final int SECONDS_PER_HOUR = 3600; // For digital inventory hourly calculation
  private static final int SECONDS_PER_DAY = 86400; // For classic inventory daily calculation

  /**
   * Convert external booking message to internal BookingData entities. Processes all slots and
   * creates booking data for each (inventoryId, date) combination.
   *
   * @param externalMessage The external booking message
   * @param inventory The inventory entity (to determine if it's Digital or Classic)
   * @return List of BookingData (one entry per unique inventoryId-date combination)
   */
  public List<BookingData> convertToBookingData(
      ExternalBookingMessageDTO externalMessage, Inventory inventory) {
    log.debug(
        "Converting booking message for dealId: {} with {} slots",
        externalMessage.getDealId(),
        externalMessage.getSlots() != null ? externalMessage.getSlots().size() : 0);

    List<BookingData> bookingDataList = new ArrayList<>();

    if (externalMessage.getSlots() == null || externalMessage.getSlots().isEmpty()) {
      log.warn("No slots found in booking message for dealId: {}", externalMessage.getDealId());
      return bookingDataList;
    }

    // Determine if this is a digital inventory
    boolean isDigital = inventory != null && inventory.getDigitalFields() != null;

    // Map to track booking data by (inventoryId, date) combination
    Map<String, BookingData> bookingDataByKey = new HashMap<>();

    // Process each slot
    for (ExternalBookingMessageDTO.Slot slot : externalMessage.getSlots()) {
      String inventoryId = slot.getInventoryId();
      processSlot(
          slot, externalMessage.getDealId(), bookingDataByKey, inventoryId, isDigital, inventory);
    }

    bookingDataList.addAll(bookingDataByKey.values());

    log.debug("Converted booking message to {} booking data entries", bookingDataList.size());
    return bookingDataList;
  }

  /**
   * Process a single slot and update the booking data with calculated percentages.
   *
   * @param slot The slot to process
   * @param dealId The deal ID for this booking
   * @param bookingDataByKey Map of booking data by (inventoryId, date) key
   * @param inventoryId The inventory ID
   * @param isDigital Whether this is a digital inventory
   * @param inventory The inventory entity (for digital field calculations)
   */
  private void processSlot(
      ExternalBookingMessageDTO.Slot slot,
      String dealId,
      Map<String, BookingData> bookingDataByKey,
      String inventoryId,
      boolean isDigital,
      Inventory inventory) {

    String timeZone = slot.getTimeZone();
    Instant startTime = slot.getStartTime();
    Instant endTime = slot.getEndTime();

    if (startTime == null || endTime == null) {
      log.warn("Slot {} has null startTime or endTime, skipping", slot.getId());
      return;
    }

    ZoneId zoneId = ZoneId.of(timeZone);
    ZonedDateTime startZoned = startTime.atZone(zoneId);
    ZonedDateTime endZoned = endTime.atZone(zoneId);

    if (isDigital) {
      processDigitalSlot(
          slot, dealId, bookingDataByKey, inventoryId, startZoned, endZoned, inventory);
    } else {
      processClassicSlot(slot, dealId, bookingDataByKey, inventoryId, startZoned, endZoned);
    }
  }

  /**
   * Process a slot for digital inventory (hourly booking data).
   *
   * @param slot The slot to process
   * @param dealId The deal ID
   * @param bookingDataByKey Map of booking data by (inventoryId, date) key
   * @param inventoryId The inventory ID
   * @param startZoned Start time in timezone
   * @param endZoned End time in timezone
   * @param inventory The inventory entity
   */
  private void processDigitalSlot(
      ExternalBookingMessageDTO.Slot slot,
      String dealId,
      Map<String, BookingData> bookingDataByKey,
      String inventoryId,
      ZonedDateTime startZoned,
      ZonedDateTime endZoned,
      Inventory inventory) {

    // Determine booking mode and seconds allocated
    String bookingMode =
        inventory.getDigitalFields() != null ? inventory.getDigitalFields().getBookingMode() : null;
    Integer secondsAllocated = null;

    if ("loop".equalsIgnoreCase(bookingMode)) {
      secondsAllocated = slot.getLoopSecondsAllocated();
    } else if ("slot".equalsIgnoreCase(bookingMode)) {
      secondsAllocated = slot.getSecondsAllocated();
    } else {
      // Default: try loopSecondsAllocated first, then secondsAllocated
      secondsAllocated =
          slot.getLoopSecondsAllocated() != null
              ? slot.getLoopSecondsAllocated()
              : slot.getSecondsAllocated();
    }

    if (secondsAllocated == null || secondsAllocated <= 0) {
      log.warn(
          "Slot {} has no valid secondsAllocated or loopSecondsAllocated, skipping", slot.getId());
      return;
    }

    // Calculate percentage per hour (based on 3600 seconds)
    double percentagePerHour = (secondsAllocated / (double) SECONDS_PER_HOUR) * 100.0;

    // Iterate through each hour in the slot's time range
    // Start from the beginning of the first hour
    ZonedDateTime current = startZoned.withMinute(0).withSecond(0).withNano(0);
    ZonedDateTime endHour = endZoned.withMinute(0).withSecond(0).withNano(0);

    while (!current.isAfter(endHour)) {
      LocalDate date = current.toLocalDate();
      int hour = current.getHour();
      String dateKey = date.toString(); // "yyyy-MM-dd"
      String bookingKey = inventoryId + ":" + dateKey;

      // Get or create booking data for this (inventoryId, date) combination
      BookingData bookingData =
          bookingDataByKey.computeIfAbsent(
              bookingKey,
              k -> {
                BookingData bd = new BookingData();
                bd.setInventoryId(inventoryId);
                bd.setDate(date);
                bd.setHourlyBookings(new HashMap<>());
                return bd;
              });

      if (bookingData.getHourlyBookings() == null) {
        bookingData.setHourlyBookings(new HashMap<>());
      }

      // Get or create hourly booking list
      String hourKey = String.valueOf(hour);
      List<BookingData.DealBooking> hourlyBookings =
          bookingData.getHourlyBookings().computeIfAbsent(hourKey, k -> new ArrayList<>());

      // Add or update deal booking
      updateDealBooking(hourlyBookings, dealId, percentagePerHour);

      // Move to next hour
      current = current.plusHours(1);
    }
  }

  /**
   * Process a slot for classic inventory (daily booking data).
   *
   * @param slot The slot to process
   * @param dealId The deal ID
   * @param bookingDataByKey Map of booking data by (inventoryId, date) key
   * @param inventoryId The inventory ID
   * @param startZoned Start time in timezone
   * @param endZoned End time in timezone
   */
  private void processClassicSlot(
      ExternalBookingMessageDTO.Slot slot,
      String dealId,
      Map<String, BookingData> bookingDataByKey,
      String inventoryId,
      ZonedDateTime startZoned,
      ZonedDateTime endZoned) {

    Integer secondsAllocated = slot.getSecondsAllocated();

    if (secondsAllocated == null || secondsAllocated <= 0) {
      log.warn("Classic slot {} has no valid secondsAllocated, skipping", slot.getId());
      return;
    }

    // Calculate percentage per day (based on 86400 seconds)
    double percentagePerDay = (secondsAllocated / (double) SECONDS_PER_DAY) * 100.0;

    // Iterate through each day in the slot's time range
    ZonedDateTime current = startZoned.toLocalDate().atStartOfDay(ZoneId.of(slot.getTimeZone()));
    ZonedDateTime endDate = endZoned.toLocalDate().atStartOfDay(ZoneId.of(slot.getTimeZone()));

    while (!current.isAfter(endDate)) {
      LocalDate date = current.toLocalDate();
      String dateKey = date.toString(); // "yyyy-MM-dd"
      String bookingKey = inventoryId + ":" + dateKey;

      // Get or create booking data for this (inventoryId, date) combination
      BookingData bookingData =
          bookingDataByKey.computeIfAbsent(
              bookingKey,
              k -> {
                BookingData bd = new BookingData();
                bd.setInventoryId(inventoryId);
                bd.setDate(date);
                bd.setBooking(new ArrayList<>());
                return bd;
              });

      if (bookingData.getBooking() == null) {
        bookingData.setBooking(new ArrayList<>());
      }

      // Add or update deal booking
      updateDealBooking(bookingData.getBooking(), dealId, percentagePerDay);

      // Move to next day
      current = current.plusDays(1);
    }
  }

  /**
   * Update or add a deal booking to the list. If the deal already exists, add the percentages.
   *
   * @param dealBookings The list of deal bookings
   * @param dealId The deal ID
   * @param percentage The percentage to add
   */
  private void updateDealBooking(
      List<BookingData.DealBooking> dealBookings, String dealId, double percentage) {
    Optional<BookingData.DealBooking> existingDeal =
        dealBookings.stream().filter(db -> dealId.equals(db.getDealId())).findFirst();

    if (existingDeal.isPresent()) {
      // Add to existing percentage
      double newPercentage = existingDeal.get().getPercentage() + percentage;
      existingDeal.get().setPercentage(newPercentage);
    } else {
      // Create new deal booking
      dealBookings.add(
          BookingData.DealBooking.builder().dealId(dealId).percentage(percentage).build());
    }
  }
}
