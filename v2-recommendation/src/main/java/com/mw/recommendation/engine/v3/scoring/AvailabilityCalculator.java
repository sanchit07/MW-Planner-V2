package com.mw.recommendation.engine.v3.scoring;

import com.mw.recommendation.engine.domain.BookingData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.config.V3Properties;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * availability per PRD §5.5: floor(100 × available_slots / requested_slots) over the campaign
 * window, where a slot is an operating hour (digital) or a day (classic) whose booked percentage is
 * below the booked threshold. PRD adjustments applied:
 *
 * <ul>
 *   <li>≥ fullThreshold (80%) → treated as fully available (100)
 *   <li>&lt; excludeBelow (10%) → the scoring stage EXCLUDES the inventory
 *   <li>No booking data → fully available with "availability unconfirmed" flagged (PRD §14.1)
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AvailabilityCalculator {

  private final V3Properties props;

  /**
   * @param score 0-100 availability score after the ≥80→100 promotion
   * @param rawPct un-promoted availability percentage (drives the &lt;10% exclusion)
   * @param unconfirmed true when no booking data existed and full availability was assumed
   */
  public record Result(
      double score,
      double rawPct,
      int availableDays,
      int totalDays,
      boolean allAvailable,
      boolean unconfirmed) {}

  public Result calculate(
      Inventory inventory, List<BookingData> bookings, LocalDate start, LocalDate end) {

    int totalDays = (int) (end.toEpochDay() - start.toEpochDay() + 1);
    boolean digital = inventory.getDigitalFields() != null;
    double bookedThreshold = props.getAvailability().getBookedThresholdPct();

    if (bookings == null || bookings.isEmpty()) {
      // PRD §14.1: assume fully available, flag unconfirmed
      return new Result(100.0, 100.0, totalDays, totalDays, true, true);
    }

    Map<LocalDate, BookingData> byDate = new java.util.HashMap<>();
    for (BookingData booking : bookings) {
      byDate.put(booking.getDate(), booking);
    }

    long requestedSlots = 0;
    long availableSlots = 0;
    int availableDays = 0;

    for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
      BookingData booking = byDate.get(date);
      if (digital) {
        long dailyHours = MeasureFitCalculator.dailyOperatingHours(inventory);
        requestedSlots += dailyHours;
        long freeHours;
        if (booking == null || booking.getHourlyBookings() == null) {
          freeHours = dailyHours;
        } else {
          freeHours = 0;
          for (int hour = 0; hour < dailyHours; hour++) {
            List<BookingData.DealBooking> deals =
                booking.getHourlyBookings().get(String.valueOf(hour));
            if (bookedPct(deals) < bookedThreshold) {
              freeHours++;
            }
          }
        }
        availableSlots += freeHours;
        if (freeHours > 0) {
          availableDays++;
        }
      } else {
        requestedSlots += 1;
        double booked = booking == null ? 0.0 : bookedPct(booking.getBooking());
        if (booked < bookedThreshold) {
          availableSlots += 1;
          availableDays++;
        }
      }
    }

    double rawPct =
        requestedSlots == 0
            ? 0.0
            : Math.floor(100.0 * availableSlots / requestedSlots); // PRD floor()
    double score = rawPct >= props.getAvailability().getFullThresholdPct() ? 100.0 : rawPct;
    return new Result(score, rawPct, availableDays, totalDays, rawPct >= 100.0, false);
  }

  private static double bookedPct(List<BookingData.DealBooking> deals) {
    if (deals == null || deals.isEmpty()) {
      return 0.0;
    }
    double total = 0.0;
    for (BookingData.DealBooking deal : deals) {
      if (deal.getPercentage() != null) {
        total += deal.getPercentage();
      }
    }
    return total;
  }
}
