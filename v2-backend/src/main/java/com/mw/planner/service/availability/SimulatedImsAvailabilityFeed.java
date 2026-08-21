package com.mw.planner.service.availability;

import com.mw.planner.domain.Inventory;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;

/**
 * Deterministic IMS availability feed simulation.
 *
 * <p>Mirrors the cinema IMS weekly feed approach: the feed content is generated, but stable — the
 * seed is the inventory external id plus the current ISO week's Monday, so repeated syncs within a
 * week return identical data (like a weekly IMS extract), and every surface reading the synced
 * store sees the same availability.
 */
@Slf4j
public class SimulatedImsAvailabilityFeed implements ImsAvailabilityFeed {

  private static final String[] DAY_KEYS = {
    "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
  };

  private static final String[] BRANDS = {
    "Acme Beverages", "Northwind Motors", "Skyline Telecom", "Bluebird Airlines", "Vertex Sports"
  };
  private static final String[] AGENCIES = {"Mediacom", "OMD", "Kinetic", "Posterscope", "Talon"};

  @Override
  public Map<String, Object> fetchAvailability(String externalId, Inventory inventory) {
    if (externalId == null || externalId.isBlank()) {
      throw new ImsFeedException("IMS feed request missing inventory external id");
    }

    LocalDate weekAnchor = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    Random rnd = new Random((externalId + "|" + weekAnchor).hashCode());

    String timeZone =
        inventory != null && inventory.getTimeZone() != null && !inventory.getTimeZone().isBlank()
            ? inventory.getTimeZone()
            : "Asia/Singapore";
    String name =
        inventory != null && inventory.getName() != null
            ? inventory.getName()
            : "Inventory " + externalId;

    boolean isClassic =
        inventory != null && "Classic".equalsIgnoreCase(inventory.getClassification());

    int loopDuration = 120; // seconds
    if (inventory != null
        && inventory.getDigitalFields() != null
        && inventory.getDigitalFields().getLoopDuration() != null
        && inventory.getDigitalFields().getLoopDuration() > 0) {
      loopDuration = inventory.getDigitalFields().getLoopDuration();
    }
    int spotsPerLoop = 10;
    if (inventory != null
        && inventory.getDigitalFields() != null
        && inventory.getDigitalFields().getSpotsPerLoop() != null
        && inventory.getDigitalFields().getSpotsPerLoop() > 0) {
      spotsPerLoop = inventory.getDigitalFields().getSpotsPerLoop();
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", externalId);
    payload.put("name", name);
    payload.put("timeZone", timeZone);
    payload.put("bookingMode", "loop");
    payload.put("loopDuration", loopDuration);
    payload.put("allocatedLoopSeconds", 0);
    payload.put("availableLoopSeconds", loopDuration);
    payload.put("schedule", buildSchedule(inventory, weekAnchor));
    payload.put("blackouts", buildBlackouts(externalId, weekAnchor, rnd));
    payload.put(
        "bookings", buildBookings(externalId, timeZone, weekAnchor, rnd, spotsPerLoop, isClassic));
    return payload;
  }

  private Map<String, Object> buildSchedule(Inventory inventory, LocalDate weekAnchor) {
    Map<String, Object> operatingTimes = new LinkedHashMap<>();

    Map<Inventory.Weekday, List<Inventory.OperatingTime>> fromInventory =
        inventory != null ? inventory.getOperatingTimes() : null;

    if (fromInventory != null && !fromInventory.isEmpty()) {
      for (Map.Entry<Inventory.Weekday, List<Inventory.OperatingTime>> e :
          fromInventory.entrySet()) {
        List<Map<String, Object>> ranges = new ArrayList<>();
        for (Inventory.OperatingTime ot : e.getValue()) {
          Map<String, Object> range = new LinkedHashMap<>();
          range.put("start", ot.getStart() != null ? ot.getStart() : "06:00:00");
          range.put("end", ot.getEnd() != null ? ot.getEnd() : "23:00:00");
          ranges.add(range);
        }
        if (!ranges.isEmpty()) {
          operatingTimes.put(e.getKey().name().toLowerCase(Locale.ROOT), ranges);
        }
      }
    }

    if (operatingTimes.isEmpty()) {
      for (String day : DAY_KEYS) {
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("start", "06:00:00");
        range.put("end", "23:00:00");
        operatingTimes.put(day, List.of(range));
      }
    }

    String ts = weekAnchor.atStartOfDay().toInstant(ZoneOffset.UTC).toString();
    Map<String, Object> schedule = new LinkedHashMap<>();
    schedule.put("operatingTimes", operatingTimes);
    schedule.put("createdAt", ts);
    schedule.put("createdBy", "ims-feed");
    schedule.put("updatedAt", ts);
    schedule.put("updatedBy", "ims-feed");
    return schedule;
  }

  private List<Map<String, Object>> buildBlackouts(
      String externalId, LocalDate weekAnchor, Random rnd) {
    List<Map<String, Object>> blackouts = new ArrayList<>();
    // ~30% of inventories carry one operator-blocked window in the next 90 days.
    if (rnd.nextInt(100) < 30) {
      LocalDate start = weekAnchor.plusDays(rnd.nextInt(75));
      LocalDate end = start.plusDays(2 + rnd.nextInt(5));
      Map<String, Object> blackout = new LinkedHashMap<>();
      blackout.put("id", "BLK-" + Math.abs((externalId + "-blk").hashCode()));
      blackout.put("startDate", start.toString());
      blackout.put("endDate", end.toString());
      blackout.put("reason", rnd.nextBoolean() ? "Scheduled maintenance" : "Operator hold");
      String ts = weekAnchor.atStartOfDay().toInstant(ZoneOffset.UTC).toString();
      blackout.put("createdAt", ts);
      blackout.put("createdBy", "ims-feed");
      blackouts.add(blackout);
    }
    return blackouts;
  }

  private List<Map<String, Object>> buildBookings(
      String externalId,
      String timeZone,
      LocalDate weekAnchor,
      Random rnd,
      int spotsPerLoop,
      boolean isClassic) {
    List<Map<String, Object>> bookings = new ArrayList<>();

    int bookingCount = 1 + rnd.nextInt(3); // 1..3
    boolean soldOutWindow = rnd.nextInt(100) < 20; // ~20% get a fully sold-out window

    for (int i = 0; i < bookingCount; i++) {
      boolean reserved = !soldOutWindow && rnd.nextInt(100) < 35;
      String status = reserved ? "reserved" : "booked";

      LocalDate start = weekAnchor.minusDays(14).plusDays(rnd.nextInt(90));
      LocalDate end = start.plusDays(4 + rnd.nextInt(17));

      // Sold-out window: first booking occupies every slot position.
      List<Integer> positions = new ArrayList<>();
      if (isClassic || (soldOutWindow && i == 0)) {
        for (int p = 1; p <= spotsPerLoop; p++) positions.add(p);
      } else {
        int take = 1 + rnd.nextInt(Math.max(1, spotsPerLoop / 2));
        int offset = rnd.nextInt(Math.max(1, spotsPerLoop - take + 1));
        for (int p = 1 + offset; p < 1 + offset + take && p <= spotsPerLoop; p++) {
          positions.add(p);
        }
      }

      ZoneId zone = safeZone(timeZone);
      Instant slotStart = start.atTime(6, 0).atZone(zone).toInstant();
      Instant slotEnd = end.atTime(23, 0).atZone(zone).toInstant();
      String bookingId = "IMSB-" + Math.abs((externalId + "-b" + i).hashCode());
      String ts = weekAnchor.atStartOfDay().toInstant(ZoneOffset.UTC).toString();

      Map<String, Object> slot = new LinkedHashMap<>();
      slot.put("id", bookingId + "-s1");
      slot.put("bookingId", bookingId);
      slot.put("bookingType", "guaranteed");
      slot.put("inventoryId", externalId);
      slot.put("startTime", slotStart.toString());
      slot.put("endTime", slotEnd.toString());
      slot.put("status", status);
      slot.put("slotPositions", positions);
      slot.put("loopSecondsAllocated", positions.size() * 60);
      slot.put("secondsAllocated", 0);
      slot.put("creativeDuration", 15);
      slot.put("timeZone", timeZone);
      slot.put("createdAt", ts);
      slot.put("createdBy", "ims-feed");
      slot.put("expiresAt", slotEnd.toString());

      Map<String, Object> booking = new LinkedHashMap<>();
      booking.put("id", bookingId);
      booking.put("bookingType", "guaranteed");
      booking.put("status", status);
      booking.put("brand", BRANDS[rnd.nextInt(BRANDS.length)]);
      booking.put("agency", AGENCIES[rnd.nextInt(AGENCIES.length)]);
      booking.put("dealId", "DL-" + Math.abs((bookingId + "-deal").hashCode()));
      booking.put("dealName", "IMS deal " + (i + 1));
      booking.put("slots", List.of(slot));
      booking.put("createdAt", ts);
      booking.put("createdBy", "ims-feed");
      booking.put("updatedAt", ts);
      booking.put("updatedBy", "ims-feed");
      booking.put("expiresAt", slotEnd.toString());
      booking.put("metadata", Map.of("source", "IMS"));
      bookings.add(booking);
    }
    return bookings;
  }

  private ZoneId safeZone(String timeZone) {
    try {
      return ZoneId.of(timeZone);
    } catch (Exception e) {
      return ZoneOffset.UTC;
    }
  }
}
