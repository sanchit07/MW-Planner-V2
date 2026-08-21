package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;

import com.mw.recommendation.engine.domain.BookingData;
import com.mw.recommendation.engine.domain.Inventory;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Conversion of canonical IMS availability payloads into engine BookingData. */
class PlannerAvailabilityServiceTest {

  private final PlannerAvailabilityService service =
      new PlannerAvailabilityService("", "mw-planner");

  private static final LocalDate START = LocalDate.of(2026, 9, 1);
  private static final LocalDate END = LocalDate.of(2026, 9, 10);

  private static Inventory digitalInventory(int spotsPerLoop) {
    Inventory inv = new Inventory();
    inv.setInventoryId("inv-1");
    Inventory.DigitalFields df = new Inventory.DigitalFields();
    df.setSpotsPerLoop(spotsPerLoop);
    inv.setDigitalFields(df);
    return inv;
  }

  private static Document slot(String start, String end, List<Integer> positions) {
    return new Document("id", "s1")
        .append("inventoryId", "inv-1")
        .append("timeZone", "UTC")
        .append("startTime", start)
        .append("endTime", end)
        .append("slotPositions", positions)
        .append("loopSecondsAllocated", positions.size() * 60);
  }

  private static Document booking(String status, Document... slots) {
    return new Document("id", "b1")
        .append("dealId", "DL-1")
        .append("status", status)
        .append("slots", List.of(slots));
  }

  @Test
  @DisplayName("fully booked window (all slot positions) yields 100% occupancy per hour")
  void fullyBookedDigital() {
    Document payload =
        new Document(
            "bookings",
            List.of(
                booking(
                    "booked",
                    slot("2026-09-01T06:00:00Z", "2026-09-10T23:00:00Z", List.of(1, 2, 3, 4)))));

    List<BookingData> data =
        service.convertPayload("inv-1", digitalInventory(4), payload, START, END);

    assertEquals(10, data.size()); // one doc per date
    BookingData first =
        data.stream().filter(d -> d.getDate().equals(START)).findFirst().orElseThrow();
    List<BookingData.DealBooking> hour10 = first.getHourlyBookings().get("10");
    assertNotNull(hour10);
    assertEquals(100.0, hour10.get(0).getPercentage(), 0.01);
  }

  @Test
  @DisplayName("partial booking yields proportional occupancy")
  void partialDigital() {
    Document payload =
        new Document(
            "bookings",
            List.of(
                booking(
                    "booked", slot("2026-09-01T06:00:00Z", "2026-09-03T23:00:00Z", List.of(1)))));

    List<BookingData> data =
        service.convertPayload("inv-1", digitalInventory(4), payload, START, END);

    assertEquals(3, data.size()); // clipped to booked dates only
    BookingData d = data.get(0);
    assertEquals(25.0, d.getHourlyBookings().get("12").get(0).getPercentage(), 0.01);
  }

  @Test
  @DisplayName("blackouts block 100% for classic inventory and are clipped to the plan window")
  void blackoutClassic() {
    Inventory classic = new Inventory();
    classic.setInventoryId("inv-1");
    Document payload =
        new Document(
            "blackouts",
            List.of(
                new Document("id", "BLK-1")
                    .append("startDate", "2026-08-28")
                    .append("endDate", "2026-09-02")
                    .append("reason", "Operator hold")));

    List<BookingData> data = service.convertPayload("inv-1", classic, payload, START, END);

    assertEquals(2, data.size()); // Sep 1 + Sep 2 only (clipped)
    for (BookingData d : data) {
      assertFalse(d.getDate().isBefore(START));
      assertEquals(100.0, d.getBooking().get(0).getPercentage(), 0.01);
      assertTrue(d.getBooking().get(0).getDealId().startsWith("blackout:"));
    }
  }

  @Test
  @DisplayName("expired bookings and out-of-window slots are ignored")
  void ignoredCases() {
    Document payload =
        new Document(
            "bookings",
            List.of(
                booking(
                    "expired", slot("2026-09-01T06:00:00Z", "2026-09-10T23:00:00Z", List.of(1, 2))),
                booking(
                    "booked",
                    slot("2026-10-01T06:00:00Z", "2026-10-05T23:00:00Z", List.of(1, 2)))));

    List<BookingData> data =
        service.convertPayload("inv-1", digitalInventory(4), payload, START, END);
    assertTrue(data.isEmpty());
  }

  @Test
  @DisplayName("disabled service returns empty map and never throws")
  void disabledService() {
    Map<String, List<BookingData>> result =
        service.fetchBookingData(List.of(digitalInventory(4)), START, END);
    assertTrue(result.isEmpty());
    assertFalse(service.isEnabled());
  }

  @Test
  @DisplayName("short intra-day slot only books its overlapping hours, not the whole day")
  void intraDaySlotHourBounded() {
    // Fully allocated one-hour slot 12:00-13:00 on Sep 1 only
    Document payload =
        new Document(
            "bookings",
            List.of(
                booking(
                    "booked",
                    slot("2026-09-01T12:00:00Z", "2026-09-01T13:00:00Z", List.of(1, 2, 3, 4)))));

    List<BookingData> data =
        service.convertPayload("inv-1", digitalInventory(4), payload, START, END);

    assertEquals(1, data.size());
    Map<String, List<BookingData.DealBooking>> hours = data.get(0).getHourlyBookings();
    assertEquals(1, hours.size(), "only the overlapping hour must be booked");
    assertEquals(100.0, hours.get("12").get(0).getPercentage(), 0.01);
    assertNull(hours.get("11"));
    assertNull(hours.get("13"));
  }

  @Test
  @DisplayName("multi-day slot clips boundary days to local start/end hours")
  void boundaryDayClipping() {
    // Sep 1 18:00 -> Sep 3 06:00 (ends exactly on the hour: hour 6 stays free)
    Document payload =
        new Document(
            "bookings",
            List.of(
                booking(
                    "booked",
                    slot("2026-09-01T18:00:00Z", "2026-09-03T06:00:00Z", List.of(1, 2)))));

    List<BookingData> data =
        service.convertPayload("inv-1", digitalInventory(4), payload, START, END);

    Map<LocalDate, BookingData> byDate = new java.util.HashMap<>();
    data.forEach(d -> byDate.put(d.getDate(), d));
    assertEquals(3, byDate.size());

    var day1 = byDate.get(LocalDate.of(2026, 9, 1)).getHourlyBookings();
    assertNull(day1.get("17"));
    assertNotNull(day1.get("18"));
    assertNotNull(day1.get("23"));

    var day2 = byDate.get(LocalDate.of(2026, 9, 2)).getHourlyBookings();
    assertEquals(24, day2.size(), "interior day covers all hours");

    var day3 = byDate.get(LocalDate.of(2026, 9, 3)).getHourlyBookings();
    assertNotNull(day3.get("5"));
    assertNull(day3.get("6"), "exact on-the-hour end is exclusive");
  }

  @Test
  @DisplayName("summary text flags partial availability")
  void summaryText() {
    assertEquals(
        "Limited availability for your dates: 3/10 days available",
        RecommendationAsyncService.buildAvailabilitySummaryText(3, 10, 30.0));
    assertEquals(
        "10/10 days available",
        RecommendationAsyncService.buildAvailabilitySummaryText(10, 10, 100.0));
  }
}
