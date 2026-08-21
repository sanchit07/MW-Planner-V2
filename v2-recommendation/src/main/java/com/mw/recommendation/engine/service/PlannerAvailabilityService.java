package com.mw.recommendation.engine.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mw.recommendation.engine.domain.BookingData;
import com.mw.recommendation.engine.domain.Inventory;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Bridges the planner's canonical IMS-synced availability store ({@code inventory_availability} in
 * the mw-planner database) into the recommendation engine's booking-data model, so candidate
 * scoring respects real availability for the plan's flight dates.
 *
 * <p>The planner records are keyed by {@code externalId}, which by contract equals the engine's
 * {@code Inventory.inventoryId} (both are the source inventory UUID). Each record's payload is the
 * inventory-api availability response shape: {@code bookings} (each with {@code slots}) and {@code
 * blackouts}.
 *
 * <p>Conversion semantics:
 *
 * <ul>
 *   <li>Digital inventory: a booking slot occupies {@code slotPositions.size() / spotsPerLoop} of
 *       every hour in its date range; a blackout occupies 100%.
 *   <li>Classic inventory: any booking or blackout occupies 100% of each covered day.
 *   <li>Overlapping deals accumulate, matching how {@code ScoringServiceImpl} sums booked
 *       percentages per hour/day.
 * </ul>
 *
 * <p>The bridge is disabled (returns empty, plans behave as before) when no planner Mongo URI is
 * configured or the store is unreachable — failures never break a recommendation run.
 */
@Service
@Slf4j
public class PlannerAvailabilityService {

  private static final String COLLECTION = "inventory_availability";

  private final String mongoUri;
  private final String databaseName;

  private volatile MongoClient client;
  private volatile boolean connectFailed;

  public PlannerAvailabilityService(
      @Value("${planner-availability.mongo-uri:}") String mongoUri,
      @Value("${planner-availability.database:mw-planner}") String databaseName) {
    this.mongoUri = mongoUri;
    this.databaseName = databaseName;
  }

  public boolean isEnabled() {
    return mongoUri != null && !mongoUri.isBlank() && !connectFailed;
  }

  /**
   * Fetches canonical availability for the given candidate inventories and converts it to
   * per-(inventoryId, date) {@link BookingData}, clipped to [startDate, endDate].
   *
   * @return map keyed by engine inventoryId; empty when disabled, unreachable, or no records.
   */
  public Map<String, List<BookingData>> fetchBookingData(
      Collection<Inventory> inventories, LocalDate startDate, LocalDate endDate) {
    if (!isEnabled() || inventories == null || inventories.isEmpty()) {
      return Map.of();
    }
    try {
      Map<String, Inventory> byId = new HashMap<>();
      for (Inventory inv : inventories) {
        if (inv.getInventoryId() != null) {
          byId.put(inv.getInventoryId(), inv);
        }
      }
      if (byId.isEmpty()) {
        return Map.of();
      }

      MongoCollection<Document> collection =
          client().getDatabase(databaseName).getCollection(COLLECTION);
      Map<String, List<BookingData>> result = new HashMap<>();
      collection
          .find(new Document("externalId", new Document("$in", new ArrayList<>(byId.keySet()))))
          .forEach(
              doc -> {
                String externalId = doc.getString("externalId");
                Inventory inventory = byId.get(externalId);
                Object payload = doc.get("payload");
                if (inventory == null || !(payload instanceof Document payloadDoc)) {
                  return;
                }
                // Always record the canonical result — even an empty list — so it
                // replaces stale engine booking_data. A synced record with no
                // bookings/blackouts in the window means "fully available".
                result.put(
                    externalId,
                    convertPayload(externalId, inventory, payloadDoc, startDate, endDate));
              });
      log.info(
          "Canonical availability: {} of {} candidates have IMS-synced records",
          result.size(),
          byId.size());
      return result;
    } catch (Exception e) {
      // Never break a recommendation run over availability enrichment.
      log.warn("Canonical availability fetch failed; scoring without it: {}", e.getMessage());
      return Map.of();
    }
  }

  private MongoClient client() {
    MongoClient local = client;
    if (local == null) {
      synchronized (this) {
        if (client == null) {
          try {
            client = MongoClients.create(mongoUri);
          } catch (Exception e) {
            connectFailed = true;
            throw e;
          }
        }
        local = client;
      }
    }
    return local;
  }

  @PreDestroy
  void close() {
    if (client != null) {
      client.close();
    }
  }

  // ---------------------------------------------------------------------
  // Payload → BookingData conversion
  // ---------------------------------------------------------------------

  List<BookingData> convertPayload( // package-private for tests
      String inventoryId,
      Inventory inventory,
      Document payload,
      LocalDate startDate,
      LocalDate endDate) {
    boolean digital = inventory.getDigitalFields() != null;
    Map<LocalDate, BookingData> byDate = new HashMap<>();

    List<Document> bookings = listOfDocuments(payload.get("bookings"));
    for (Document booking : bookings) {
      String status = booking.getString("status");
      if (status != null && "expired".equalsIgnoreCase(status)) {
        continue;
      }
      String dealId =
          booking.getString("dealId") != null
              ? booking.getString("dealId")
              : booking.getString("id");
      for (Document slot : listOfDocuments(booking.get("slots"))) {
        applySlot(byDate, inventoryId, inventory, digital, dealId, slot, startDate, endDate);
      }
    }

    for (Document blackout : listOfDocuments(payload.get("blackouts"))) {
      LocalDate from = parseLocalDate(blackout.getString("startDate"));
      LocalDate to = parseLocalDate(blackout.getString("endDate"));
      if (from == null || to == null) {
        continue;
      }
      String dealId = "blackout:" + blackout.getString("id");
      applyRange(
          byDate, inventoryId, digital, dealId, 100.0, max(from, startDate), min(to, endDate));
    }

    return new ArrayList<>(byDate.values());
  }

  private void applySlot(
      Map<LocalDate, BookingData> byDate,
      String inventoryId,
      Inventory inventory,
      boolean digital,
      String dealId,
      Document slot,
      LocalDate startDate,
      LocalDate endDate) {
    ZoneId zone = safeZone(slot.getString("timeZone"));
    ZonedDateTime fromDt = toZonedDateTime(slot.get("startTime"), zone);
    ZonedDateTime toDt = toZonedDateTime(slot.get("endTime"), zone);
    if (fromDt == null || toDt == null) {
      return;
    }
    LocalDate from = fromDt.toLocalDate();
    LocalDate to = toDt.toLocalDate();
    double pct = digital ? digitalOccupancyPct(inventory, slot) : 100.0;
    if (pct <= 0) {
      return;
    }
    if (!digital) {
      applyRange(byDate, inventoryId, false, dealId, pct, max(from, startDate), min(to, endDate));
      return;
    }
    // Digital: only mark the hours the slot actually overlaps, preserving the
    // local start/end times. Interior days cover all 24 hours; boundary days
    // are clipped to the slot's local hours (end is exclusive at exact :00).
    LocalDate clipFrom = max(from, startDate);
    LocalDate clipTo = min(to, endDate);
    if (clipFrom == null || clipTo == null || clipFrom.isAfter(clipTo)) {
      return;
    }
    for (LocalDate date = clipFrom; !date.isAfter(clipTo); date = date.plusDays(1)) {
      int hourFrom = date.equals(from) ? fromDt.getHour() : 0;
      int hourTo;
      if (date.equals(to)) {
        boolean onTheHour = toDt.getMinute() == 0 && toDt.getSecond() == 0;
        hourTo = onTheHour ? toDt.getHour() - 1 : toDt.getHour();
      } else {
        hourTo = 23;
      }
      if (hourTo < hourFrom) {
        continue; // e.g. slot ends exactly at midnight of this day
      }
      applyDigitalHours(byDate, inventoryId, dealId, pct, date, hourFrom, hourTo);
    }
  }

  private void applyDigitalHours(
      Map<LocalDate, BookingData> byDate,
      String inventoryId,
      String dealId,
      double pct,
      LocalDate date,
      int hourFrom,
      int hourTo) {
    BookingData data = digitalBookingDataFor(byDate, inventoryId, date);
    for (int hour = hourFrom; hour <= hourTo; hour++) {
      data.getHourlyBookings()
          .computeIfAbsent(String.valueOf(hour), h -> new ArrayList<>())
          .add(BookingData.DealBooking.builder().dealId(dealId).percentage(pct).build());
    }
  }

  private BookingData digitalBookingDataFor(
      Map<LocalDate, BookingData> byDate, String inventoryId, LocalDate date) {
    BookingData data =
        byDate.computeIfAbsent(
            date,
            d -> {
              BookingData bd = new BookingData();
              bd.setInventoryId(inventoryId);
              bd.setDate(d);
              bd.setHourlyBookings(new HashMap<>());
              return bd;
            });
    if (data.getHourlyBookings() == null) {
      data.setHourlyBookings(new HashMap<>());
    }
    return data;
  }

  /**
   * Digital occupancy of an hour: fraction of the loop taken by this slot's positions, falling back
   * to allocated loop seconds over the loop duration. Capped at 100%.
   */
  private double digitalOccupancyPct(Inventory inventory, Document slot) {
    Integer spotsPerLoop =
        inventory.getDigitalFields() != null
            ? inventory.getDigitalFields().getSpotsPerLoop()
            : null;
    List<?> positions = slot.get("slotPositions") instanceof List<?> l ? l : List.of();
    if (spotsPerLoop != null && spotsPerLoop > 0 && !positions.isEmpty()) {
      return Math.min(100.0, 100.0 * positions.size() / spotsPerLoop);
    }
    Integer loopSecondsAllocated = slot.getInteger("loopSecondsAllocated");
    Integer loopDuration =
        inventory.getDigitalFields() != null
            ? inventory.getDigitalFields().getLoopDuration()
            : null;
    if (loopSecondsAllocated != null && loopSecondsAllocated > 0) {
      int loop = loopDuration != null && loopDuration > 0 ? loopDuration : 3600;
      return Math.min(100.0, 100.0 * loopSecondsAllocated / loop);
    }
    return 0.0;
  }

  private void applyRange(
      Map<LocalDate, BookingData> byDate,
      String inventoryId,
      boolean digital,
      String dealId,
      double pct,
      LocalDate from,
      LocalDate to) {
    if (from == null || to == null || from.isAfter(to)) {
      return;
    }
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      BookingData data =
          byDate.computeIfAbsent(
              date,
              d -> {
                BookingData bd = new BookingData();
                bd.setInventoryId(inventoryId);
                bd.setDate(d);
                if (digital) {
                  bd.setHourlyBookings(new HashMap<>());
                } else {
                  bd.setBooking(new ArrayList<>());
                }
                return bd;
              });
      if (digital) {
        if (data.getHourlyBookings() == null) {
          data.setHourlyBookings(new HashMap<>());
        }
        for (int hour = 0; hour < 24; hour++) {
          data.getHourlyBookings()
              .computeIfAbsent(String.valueOf(hour), h -> new ArrayList<>())
              .add(BookingData.DealBooking.builder().dealId(dealId).percentage(pct).build());
        }
      } else {
        if (data.getBooking() == null) {
          data.setBooking(new ArrayList<>());
        }
        data.getBooking()
            .add(BookingData.DealBooking.builder().dealId(dealId).percentage(pct).build());
      }
    }
  }

  private static List<Document> listOfDocuments(Object value) {
    List<Document> docs = new ArrayList<>();
    if (value instanceof List<?> list) {
      for (Object o : list) {
        if (o instanceof Document d) {
          docs.add(d);
        }
      }
    }
    return docs;
  }

  private static ZonedDateTime toZonedDateTime(Object value, ZoneId zone) {
    try {
      if (value instanceof java.util.Date d) {
        return d.toInstant().atZone(zone);
      }
      if (value instanceof String s && !s.isBlank()) {
        return Instant.parse(s).atZone(zone);
      }
    } catch (Exception ignored) {
      // fall through
    }
    return null;
  }

  private static LocalDate parseLocalDate(String value) {
    try {
      return value == null ? null : LocalDate.parse(value);
    } catch (Exception e) {
      return null;
    }
  }

  private static ZoneId safeZone(String timeZone) {
    try {
      return timeZone == null ? ZoneOffset.UTC : ZoneId.of(timeZone);
    } catch (Exception e) {
      return ZoneOffset.UTC;
    }
  }

  private static LocalDate max(LocalDate a, LocalDate b) {
    return a.isAfter(b) ? a : b;
  }

  private static LocalDate min(LocalDate a, LocalDate b) {
    return a.isBefore(b) ? a : b;
  }
}
