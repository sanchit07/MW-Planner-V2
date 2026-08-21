package com.mw.recommendation.engine.v3.schedule;

import com.mw.recommendation.engine.domain.AudienceData;
import com.mw.recommendation.engine.domain.BookingData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.domain.RunScheduleV3;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import com.mw.recommendation.engine.v3.pipeline.ScoredInventoryV3;
import com.mw.recommendation.engine.v3.pipeline.V3RunContext;
import com.mw.recommendation.engine.v3.repository.ScheduleV3Repository;
import com.mw.recommendation.engine.v3.scoring.MeasureFitCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stage 6: schedules for auto-selected inventories per PRD §12.4 / Part E. Hours are chosen only
 * from operating windows whose booked percentage is below the threshold, ranked by effective CPM
 * (hour cost ÷ hourly audience — cheapest impressions first), with goal-specific strategies: REACH
 * rotates the daily window start so different dayparts (audiences) are hit across days; AD_PLAYS
 * books every available hour; IMPRESSIONS/others take the best-CPM hours. Selling terms are
 * validated with extend-or-exclude semantics; cost and impressions are prorated to the booked
 * fraction of the window (AC-06).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduleStage {

  private final SellingTermsValidator sellingTermsValidator;
  private final ScheduleV3Repository scheduleRepository;
  private final V3Properties props;

  public void buildSchedules(V3RunContext ctx) {
    Map<String, BigDecimal> selectedSpend = ctx.getSelectedSpend();
    if (selectedSpend.isEmpty()) {
      ctx.setSchedules(List.of());
      return;
    }

    Map<String, ScoredInventoryV3> byId = new LinkedHashMap<>();
    for (ScoredInventoryV3 item : ctx.getScored()) {
      byId.put(item.inventory().getInventoryId(), item);
    }

    List<RunScheduleV3> schedules = new ArrayList<>();
    List<String> confirmedSelection = new ArrayList<>();

    for (Map.Entry<String, BigDecimal> entry : selectedSpend.entrySet()) {
      ScoredInventoryV3 item = byId.get(entry.getKey());
      if (item == null) {
        continue;
      }
      RunScheduleV3 schedule = buildOne(ctx, item, entry.getValue());
      if (schedule != null) {
        schedules.add(schedule);
        confirmedSelection.add(entry.getKey());
      } else {
        ctx.getWarnings().exclude("SELLING_TERMS_CONFLICT");
        ctx.getWarnings()
            .warn(
                "Inventory "
                    + displayName(item.inventory())
                    + " deselected: selling terms could not be satisfied within its allocation");
      }
    }

    scheduleRepository.deleteByRunId(ctx.getRunId());
    if (!schedules.isEmpty()) {
      scheduleRepository.saveAll(schedules);
    }
    ctx.setSchedules(schedules);
    ctx.setAutoSelectedInventoryIds(confirmedSelection);
    log.info("v3 schedules run {}: {} persisted", ctx.getRunId(), schedules.size());
  }

  private RunScheduleV3 buildOne(V3RunContext ctx, ScoredInventoryV3 item, BigDecimal spend) {
    Inventory inventory = item.inventory();
    RecommendationV3RequestDTO request = ctx.getRequest();
    long campaignDays = ctx.campaignDays();

    // Days the allocation affords (full-window cost prorates per day)
    int affordableDays = (int) campaignDays;
    if (item.cost() != null && item.cost().present() && spend != null && spend.signum() > 0) {
      BigDecimal dailyCost =
          item.cost().cost().divide(BigDecimal.valueOf(campaignDays), 6, RoundingMode.HALF_UP);
      if (dailyCost.signum() > 0) {
        affordableDays =
            (int)
                Math.min(campaignDays, spend.divide(dailyCost, 0, RoundingMode.FLOOR).longValue());
      }
    }
    if (affordableDays <= 0) {
      affordableDays = 1;
    }

    SellingTermsValidator.Validation validation =
        sellingTermsValidator.validate(inventory, affordableDays, campaignDays);
    if (!validation.valid()) {
      return null;
    }
    int bookedDays = validation.adjustedDays();

    // Booking matrix: date → selected hours, honouring operating windows + booked threshold
    Map<String, List<Integer>> bookingMatrix = new LinkedHashMap<>();
    Map<LocalDate, BookingData> bookingByDate = bookingByDate(ctx, inventory);
    AudienceData audience = ctx.audienceFor(inventory);
    boolean digital = inventory.getDigitalFields() != null;
    long totalOperatingHours = 0;
    long selectedHours = 0;
    int dayIndex = 0;

    for (LocalDate date = request.getStartDate();
        !date.isAfter(request.getEndDate()) && bookingMatrix.size() < bookedDays;
        date = date.plusDays(1)) {

      List<Integer> operatingHours = operatingHoursFor(inventory, date.getDayOfWeek());
      totalOperatingHours += operatingHours.size();
      if (operatingHours.isEmpty()) {
        continue; // non-operating day — skipped (PRD Part E check 2)
      }
      List<Integer> availableHours =
          availableHours(operatingHours, bookingByDate.get(date), digital);
      if (availableHours.isEmpty()) {
        continue;
      }
      List<Integer> chosen =
          chooseHours(
              availableHours,
              audience,
              inventory,
              request.getGoal(),
              validation.minHoursPerDay(),
              dayIndex);
      if (chosen.size() < validation.minHoursPerDay()) {
        continue; // cannot satisfy the minimum-hours block on this date
      }
      bookingMatrix.put(date.toString(), digital ? chosen : List.of());
      selectedHours += digital ? chosen.size() : 24;
      dayIndex++;
    }

    if (bookingMatrix.isEmpty()) {
      return null;
    }

    // Proration: booked fraction of the full window drives price + forecast (AC-06)
    long windowHours =
        digital
            ? Math.max(1, MeasureFitCalculator.dailyOperatingHours(inventory) * campaignDays)
            : campaignDays * 24;
    double fraction = Math.min(1.0, selectedHours / (double) windowHours);

    Inventory.DigitalFields digitalFields = inventory.getDigitalFields();
    Integer loopDuration = digitalFields != null ? digitalFields.getLoopDuration() : null;
    int spotsPerHour =
        loopDuration != null && loopDuration > 0 ? Math.max(1, 3600 / loopDuration) : 0;
    long adPlays = digital ? (long) spotsPerHour * selectedHours : 0;

    V3RunContext.MeasureData measure = ctx.measureFor(inventory);
    Long impressions =
        measure != null && measure.impressions() != null
            ? Math.round(measure.impressions() * fraction)
            : null;
    Long reach =
        measure != null && measure.reach() != null ? Math.round(measure.reach() * fraction) : null;

    BigDecimal basePrice =
        item.cost() != null && item.cost().present()
            ? item.cost()
                .cost()
                .multiply(BigDecimal.valueOf(fraction))
                .setScale(2, RoundingMode.HALF_UP)
            : null;

    return RunScheduleV3.builder()
        .runId(ctx.getRunId())
        .campaignId(ctx.getCampaignId())
        .inventoryId(inventory.getInventoryId())
        .referenceId(inventory.getReferenceId())
        .scheduleId(UUID.randomUUID().toString())
        .startDate(request.getStartDate())
        .endDate(request.getEndDate())
        .bookingMatrix(bookingMatrix)
        .adPlays(adPlays)
        .plannedSot((int) selectedHours)
        .totalSot((int) totalOperatingHours)
        .spotsPerLoop(1)
        .spotsPerHour(spotsPerHour)
        .durationSeconds(
            digitalFields != null && digitalFields.getSpotDuration() != null
                ? digitalFields.getSpotDuration()
                : null)
        .basePrice(basePrice)
        .currency(item.cost() != null ? item.cost().currency() : null)
        .estimatedImpressions(impressions)
        .estimatedReach(reach)
        .adjustedForSellingTerms(!validation.adjustments().isEmpty())
        .adjustments(validation.adjustments())
        .build();
  }

  /**
   * Hour choice per goal (PRD §12.4): AD_PLAYS books everything; REACH rotates the window start
   * across days so different dayparts are covered; IMPRESSIONS/others take the best effective-CPM
   * hours (highest hourly audience first — cost per hour is uniform within one inventory at 1
   * spot/loop, so audience ranking IS the CPM ranking).
   */
  private List<Integer> chooseHours(
      List<Integer> availableHours,
      AudienceData audience,
      Inventory inventory,
      RecommendationV3RequestDTO.CampaignGoal goal,
      int minHoursPerDay,
      int dayIndex) {

    if (goal == RecommendationV3RequestDTO.CampaignGoal.AD_PLAYS
        || inventory.getDigitalFields() == null) {
      return availableHours;
    }

    if (goal == RecommendationV3RequestDTO.CampaignGoal.REACH && availableHours.size() > 4) {
      // Rotate a window of hours by day index so successive days hit different audiences
      int windowSize = Math.max(Math.max(4, minHoursPerDay), availableHours.size() / 2);
      int offset = (dayIndex * windowSize) % availableHours.size();
      List<Integer> rotated = new ArrayList<>(windowSize);
      for (int i = 0; i < windowSize && i < availableHours.size(); i++) {
        rotated.add(availableHours.get((offset + i) % availableHours.size()));
      }
      rotated.sort(Integer::compareTo);
      return rotated;
    }

    Map<Integer, AudienceData.HourlySummary> hourly =
        audience != null ? audience.getHourlySummary() : null;
    if (hourly == null || hourly.isEmpty()) {
      return availableHours;
    }
    // Best effective CPM = most audience per (uniform) hour cost
    List<Integer> ranked = new ArrayList<>(availableHours);
    ranked.sort(
        Comparator.comparingLong(
                (Integer hour) -> {
                  AudienceData.HourlySummary summary = hourly.get(hour);
                  return summary != null && summary.getTotalVisitors() != null
                      ? summary.getTotalVisitors()
                      : 0L;
                })
            .reversed());
    int keep = Math.max(Math.max(minHoursPerDay, 1), (int) Math.ceil(ranked.size() * 0.75));
    List<Integer> chosen = new ArrayList<>(ranked.subList(0, Math.min(keep, ranked.size())));
    chosen.sort(Integer::compareTo);
    return chosen;
  }

  private static List<Integer> availableHours(
      List<Integer> operatingHours, BookingData booking, boolean digital) {
    if (booking == null) {
      return operatingHours;
    }
    if (digital) {
      if (booking.getHourlyBookings() == null) {
        return operatingHours;
      }
      List<Integer> free = new ArrayList<>(operatingHours.size());
      for (Integer hour : operatingHours) {
        List<BookingData.DealBooking> deals = booking.getHourlyBookings().get(String.valueOf(hour));
        if (bookedPct(deals) < 90.0) {
          free.add(hour);
        }
      }
      return free;
    }
    return bookedPct(booking.getBooking()) < 90.0 ? operatingHours : List.of();
  }

  private static double bookedPct(List<BookingData.DealBooking> deals) {
    if (deals == null || deals.isEmpty()) {
      return 0.0;
    }
    return deals.stream()
        .filter(d -> d.getPercentage() != null)
        .mapToDouble(BookingData.DealBooking::getPercentage)
        .sum();
  }

  private List<Integer> operatingHoursFor(Inventory inventory, DayOfWeek dayOfWeek) {
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimes =
        inventory.getOperatingTimes();
    if (operatingTimes == null || operatingTimes.isEmpty()) {
      // No operating data: classic defaults to 24h, digital to a standard 6-24 window
      List<Integer> hours = new ArrayList<>(24);
      int start = inventory.getDigitalFields() != null ? 6 : 0;
      for (int hour = start; hour < 24; hour++) {
        hours.add(hour);
      }
      return hours;
    }
    Inventory.Weekday weekday = Inventory.Weekday.values()[dayOfWeek.getValue() % 7];
    List<Inventory.OperatingTime> windows = operatingTimes.get(weekday);
    if (windows == null || windows.isEmpty()) {
      return List.of();
    }
    List<Integer> hours = new ArrayList<>();
    for (Inventory.OperatingTime window : windows) {
      try {
        int start = Integer.parseInt(window.getStart().substring(0, 2));
        int end = Integer.parseInt(window.getEnd().substring(0, 2));
        if (end == 0 && start > 0) {
          end = 24;
        }
        if (end >= start) {
          for (int hour = start; hour < end; hour++) {
            hours.add(hour);
          }
        } else { // overnight window
          for (int hour = start; hour < 24; hour++) {
            hours.add(hour);
          }
          for (int hour = 0; hour < end; hour++) {
            hours.add(hour);
          }
        }
      } catch (Exception ignored) {
        // unparseable window — skip it
      }
    }
    return hours.stream().distinct().sorted().toList();
  }

  private Map<LocalDate, BookingData> bookingByDate(V3RunContext ctx, Inventory inventory) {
    List<BookingData> bookings = ctx.getBookingByInventoryId().get(inventory.getInventoryId());
    Map<LocalDate, BookingData> byDate = new LinkedHashMap<>();
    if (bookings != null) {
      for (BookingData booking : bookings) {
        byDate.put(booking.getDate(), booking);
      }
    }
    return byDate;
  }

  private static String displayName(Inventory inventory) {
    return inventory.getName() != null ? inventory.getName() : inventory.getInventoryId();
  }
}
