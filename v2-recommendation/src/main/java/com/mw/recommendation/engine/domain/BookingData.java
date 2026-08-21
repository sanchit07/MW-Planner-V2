package com.mw.recommendation.engine.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Booking data entity for storing per-date and per-hour booking information for inventories. One
 * document per (inventoryId, date) combination for scalability. Supports both Digital (hourly) and
 * Classic (daily) inventory types.
 */
@Document(collection = "booking_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@CompoundIndex(name = "inventory_date_idx", def = "{'inventoryId': 1, 'date': 1}", unique = true)
public class BookingData extends BaseEntity<String> {

  // Core identifiers - one document per (inventoryId, date) combination
  @Indexed private String inventoryId; // Links to Inventory.inventoryId

  @Indexed private LocalDate date; // Date in "yyyy-MM-dd" format

  // For Digital inventory: hourly bookings with percentage per hour
  // Key: hour (0-23 as String), Value: List of deal bookings with percentage
  private Map<String, List<DealBooking>> hourlyBookings;

  // For Classic inventory: day-level bookings with percentage
  // List of deal bookings with percentage for the entire day
  private List<DealBooking> booking;

  /**
   * Booking information for a specific deal/campaign. Stored as a map entry where key is dealId and
   * value is booking percentage.
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DealBooking {
    private String dealId; // Campaign/Deal identifier
    private Double percentage; // Booking percentage (0-100+)
  }
}
