package com.mw.recommendation.engine.v3.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.recommendation.engine.domain.BookingData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.config.V3Properties;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvailabilityCalculatorTest {

  private final V3Properties props = new V3Properties();
  private final AvailabilityCalculator calculator = new AvailabilityCalculator(props);

  private static final LocalDate START = LocalDate.of(2026, 8, 1);
  private static final LocalDate END = LocalDate.of(2026, 8, 10); // 10-day window

  private static Inventory classicInventory() {
    Inventory inventory = new Inventory();
    inventory.setInventoryId("inv-classic");
    // no digitalFields → classic path (daily slots)
    return inventory;
  }

  private static BookingData fullyBookedDay(LocalDate date) {
    BookingData booking = new BookingData();
    booking.setInventoryId("inv-classic");
    booking.setDate(date);
    booking.setBooking(
        List.of(BookingData.DealBooking.builder().dealId("deal-1").percentage(100.0).build()));
    return booking;
  }

  @Test
  void givenNoBookings_whenCalculate_thenFullyAvailableButUnconfirmed() {
    AvailabilityCalculator.Result result =
        calculator.calculate(classicInventory(), List.of(), START, END);

    assertThat(result.score()).isEqualTo(100.0);
    assertThat(result.rawPct()).isEqualTo(100.0);
    assertThat(result.unconfirmed()).isTrue();
    assertThat(result.allAvailable()).isTrue();
    assertThat(result.totalDays()).isEqualTo(10);
  }

  @Test
  void givenClassicWith4Of10DaysFullyBooked_whenCalculate_thenScore60() {
    List<BookingData> bookings =
        List.of(
            fullyBookedDay(START),
            fullyBookedDay(START.plusDays(1)),
            fullyBookedDay(START.plusDays(2)),
            fullyBookedDay(START.plusDays(3)));

    AvailabilityCalculator.Result result =
        calculator.calculate(classicInventory(), bookings, START, END);

    assertThat(result.rawPct()).isEqualTo(60.0); // floor(100 × 6/10)
    assertThat(result.score()).isEqualTo(60.0); // below the 80% promotion threshold
    assertThat(result.availableDays()).isEqualTo(6);
    assertThat(result.totalDays()).isEqualTo(10);
    assertThat(result.allAvailable()).isFalse();
    assertThat(result.unconfirmed()).isFalse();
  }

  @Test
  void givenRawPctAtLeast80_whenCalculate_thenScorePromotedTo100() {
    // 1 of 10 days booked → rawPct 90 → promoted to 100 (PRD §5.5 full threshold)
    List<BookingData> bookings = List.of(fullyBookedDay(START));

    AvailabilityCalculator.Result result =
        calculator.calculate(classicInventory(), bookings, START, END);

    assertThat(result.rawPct()).isEqualTo(90.0);
    assertThat(result.score()).isEqualTo(100.0);
    assertThat(result.availableDays()).isEqualTo(9);
    assertThat(result.allAvailable()).isFalse();
  }
}
