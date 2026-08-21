package com.mw.recommendation.engine.service;

import com.mw.recommendation.engine.domain.BookingData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.domain.RecommendationRun;
import com.mw.recommendation.engine.domain.RunScheduleRecommendation;
import com.mw.recommendation.engine.domain.SelectionMode;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.dto.RunSchedulesResponseDTO;
import com.mw.recommendation.engine.dto.ScheduleRecommendationRequestDTO;
import com.mw.recommendation.engine.dto.ScheduleRecommendationResponseDTO;
import com.mw.recommendation.engine.dto.ScheduleSummaryDTO;
import com.mw.recommendation.engine.dto.measure.MeasureInventoryDTO;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyRequestDTO;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyResponseDTO;
import com.mw.recommendation.engine.enums.ErrorCode;
import com.mw.recommendation.engine.exception.BaseException;
import com.mw.recommendation.engine.repository.BookingRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
import com.mw.recommendation.engine.repository.RecommendationResultRepository;
import com.mw.recommendation.engine.repository.RecommendationRunRepository;
import com.mw.recommendation.engine.repository.RunScheduleRecommendationRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for generating schedule recommendations based on inventory IDs and availability. Respects
 * operatingTimes and booking_data (hour &lt; 90% booked). Audience/impressions from Measure API
 * (seasonal factors); when Measure client is not available, basePrice/impressions/reach may be
 * null.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleRecommendationService {

  private static final double BOOKED_THRESHOLD = 90.0; // Hour/date included only if booked < 90%
  private static final double MIN_AVAILABILITY_TO_RECOMMEND =
      10.0; // Don't recommend if availability <= 10% (90%+ booked)
  private static final long RECOMMENDED_SPOTS_PER_LOOP =
      1L; // Recommended schedule always 1 spot per loop
  private static final DateTimeFormatter TIME_PARSER = DateTimeFormatter.ofPattern("HH:mm:ss");

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

  private final InventoryRepository inventoryRepository;
  private final BookingRepository bookingRepository;
  private final RunScheduleRecommendationRepository runScheduleRecommendationRepository;
  private final RecommendationRunRepository recommendationRunRepository;
  private final RecommendationResultRepository recommendationResultRepository;
  private final MeasureApiClient measureApiClient;

  /**
   * Generate schedule recommendations for the provided inventory IDs. Builds real schedules with
   * booking matrix (90% rules), enriches with impressions/reach from Measure API, and returns one
   * recommended schedule per inventory including estimatedImpressions and estimatedReach.
   */
  public ScheduleRecommendationResponseDTO generateScheduleRecommendations(
      ScheduleRecommendationRequestDTO request) {
    log.info(
        "Generating schedule recommendations for {} inventories", request.getInventoryIds().size());

    String requestId = UUID.randomUUID().toString();
    List<ScheduleRecommendationResponseDTO.InventorySchedule> inventorySchedules =
        new ArrayList<>();

    List<Inventory> inventories =
        request.getInventoryIds().stream()
            .map(inventoryRepository::findById)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .toList();

    if (inventories.isEmpty()) {
      log.warn("No inventories found for the provided IDs");
      return ScheduleRecommendationResponseDTO.builder()
          .requestId(requestId)
          .generatedAt(LocalDateTime.now())
          .schedules(new ArrayList<>())
          .build();
    }

    int totalDays = (int) ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1;
    Map<String, ScheduleSummaryDTO> scheduleMap =
        buildScheduleSummariesForInventories(
            inventories,
            request.getStartDate(),
            request.getEndDate(),
            request.getBudgetShare() != null && inventories.size() == 1
                ? Map.of(inventories.getFirst().getInventoryId(), request.getBudgetShare())
                : null,
            request.getGoal());

    for (Inventory inventory : inventories) {
      ScheduleSummaryDTO summary = scheduleMap.get(inventory.getInventoryId());
      List<ScheduleRecommendationResponseDTO.RecommendedSchedule> recommendedSchedules =
          new ArrayList<>();
      int availableDays = 0;
      if (summary != null) {
        recommendedSchedules.add(toRecommendedSchedule(summary));
        if (summary.getBookingMatrix() != null) {
          availableDays = summary.getBookingMatrix().size();
        }
      }
      double availabilityPct = totalDays > 0 ? (availableDays * 100.0 / totalDays) : 0.0;
      ScheduleRecommendationResponseDTO.InventorySchedule inventorySchedule =
          ScheduleRecommendationResponseDTO.InventorySchedule.builder()
              .inventoryId(inventory.getInventoryId())
              .referenceId(inventory.getReferenceId())
              .inventoryName(inventory.getName())
              .recommendedSchedules(recommendedSchedules)
              .availability(
                  ScheduleRecommendationResponseDTO.AvailabilitySummary.builder()
                      .totalDays(totalDays)
                      .availableDays(availableDays)
                      .availabilityPercentage(availabilityPct)
                      .summary(
                          availableDays
                              + "/"
                              + totalDays
                              + " days available"
                              + (summary != null && summary.getEstimatedImpressions() != null
                                  ? "; impressions/reach from Measure"
                                  : ""))
                      .allAvailable(availableDays >= totalDays)
                      .build())
              .build();
      inventorySchedules.add(inventorySchedule);
    }

    return ScheduleRecommendationResponseDTO.builder()
        .requestId(requestId)
        .generatedAt(LocalDateTime.now())
        .schedules(inventorySchedules)
        .build();
  }

  /**
   * SELECT operation: return schedules for the requested inventoryIds. Already-selected inventories
   * get their existing schedules; new inventories get freshly generated and enriched schedules.
   * Marks all requested inventories as MANUAL in recommendation results.
   */
  public ScheduleRecommendationResponseDTO selectInventoriesAndGetSchedules(
      String runId, List<String> inventoryIds) {

    RecommendationRun run =
        recommendationRunRepository
            .findByRunId(runId)
            .orElseThrow(
                () ->
                    new BaseException(
                        ErrorCode.RECOMMENDATION_RUN_NOT_FOUND,
                        "Recommendation run not found for runId: " + runId));

    if (run.getStatus() == RecommendationRun.RunStatus.IN_PROGRESS) {
      throw new BaseException(
          ErrorCode.RECOMMENDATION_IN_PROGRESS,
          "Cannot select inventories while recommendation is still in progress");
    }

    RecommendationRequestDTO runRequest = run.getRequest();
    LocalDate startDate = runRequest.getStartDate();
    LocalDate endDate = runRequest.getEndDate();
    String campaignId = run.getCampaignId();

    // Find existing schedules for the requested inventoryIds
    List<RunScheduleRecommendation> existingSchedules =
        runScheduleRecommendationRepository.findByRunIdAndInventoryIdIn(runId, inventoryIds);
    Set<String> existingInventoryIds =
        existingSchedules.stream()
            .map(RunScheduleRecommendation::getInventoryId)
            .collect(Collectors.toSet());

    // Identify inventoryIds that need new schedules
    List<String> newInventoryIds =
        inventoryIds.stream().filter(id -> !existingInventoryIds.contains(id)).toList();

    // Generate schedules for new inventories
    Map<String, ScheduleSummaryDTO> newScheduleMap = Map.of();
    if (!newInventoryIds.isEmpty()) {
      List<Inventory> newInventories =
          inventoryRepository.findByInventoryIdIn(new ArrayList<>(newInventoryIds));

      if (!newInventories.isEmpty()) {
        newScheduleMap =
            buildScheduleSummariesForInventories(
                newInventories, startDate, endDate, null, runRequest.getGoal());

        // Persist new schedule entries (without deleting existing ones for other inventories)
        List<RunScheduleRecommendation> newEntities = new ArrayList<>();
        for (Map.Entry<String, ScheduleSummaryDTO> e : newScheduleMap.entrySet()) {
          ScheduleSummaryDTO s = e.getValue();
          if (s == null) continue;
          newEntities.add(
              RunScheduleRecommendation.builder()
                  .runId(runId)
                  .campaignId(campaignId != null ? campaignId : "")
                  .inventoryId(e.getKey())
                  .scheduleId(s.getScheduleId())
                  .scheduleStartDate(s.getScheduleStartDate())
                  .scheduleEndDate(s.getScheduleEndDate())
                  .bookingMatrix(s.getBookingMatrix())
                  .adPlays(s.getAdPlays())
                  .plannedSot(s.getPlannedSot())
                  .totalSot(s.getTotalSot())
                  .spotsPerLoop(s.getSpotsPerLoop())
                  .spotsPerHour(s.getSpotsPerHour())
                  .duration(s.getDuration())
                  .basePrice(s.getBasePrice())
                  .estimatedImpressions(s.getEstimatedImpressions())
                  .estimatedReach(s.getEstimatedReach())
                  .currency(s.getCurrency())
                  .sellingTerm(s.getSellingTerm())
                  .build());
        }
        if (!newEntities.isEmpty()) {
          runScheduleRecommendationRepository.saveAll(newEntities);
        }
      }
    }

    // Mark all requested inventories as MANUAL in recommendation results
    var results = recommendationResultRepository.findByRunIdAndInventoryIdIn(runId, inventoryIds);
    for (var result : results) {
      result.setSelectionMode(SelectionMode.MANUAL);
    }
    if (!results.isEmpty()) {
      recommendationResultRepository.saveAll(results);
    }

    // Build response: combine existing + new schedules
    int totalDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    List<ScheduleRecommendationResponseDTO.InventorySchedule> inventorySchedules =
        new ArrayList<>();

    // Convert existing RunScheduleRecommendation entities
    for (RunScheduleRecommendation existing : existingSchedules) {
      inventorySchedules.add(toInventoryScheduleFromEntity(existing, totalDays));
    }

    // Convert newly generated schedules
    if (!newScheduleMap.isEmpty()) {
      List<Inventory> newInventories =
          inventoryRepository.findByInventoryIdIn(new ArrayList<>(newScheduleMap.keySet()));
      Map<String, Inventory> inventoryMap =
          newInventories.stream()
              .collect(Collectors.toMap(Inventory::getInventoryId, inv -> inv, (a, b) -> a));

      for (Map.Entry<String, ScheduleSummaryDTO> e : newScheduleMap.entrySet()) {
        Inventory inv = inventoryMap.get(e.getKey());
        ScheduleSummaryDTO summary = e.getValue();
        int availableDays =
            summary.getBookingMatrix() != null ? summary.getBookingMatrix().size() : 0;
        double availabilityPct = totalDays > 0 ? (availableDays * 100.0 / totalDays) : 0.0;
        inventorySchedules.add(
            ScheduleRecommendationResponseDTO.InventorySchedule.builder()
                .inventoryId(e.getKey())
                .referenceId(inv != null ? inv.getReferenceId() : null)
                .inventoryName(inv != null ? inv.getName() : null)
                .recommendedSchedules(List.of(toRecommendedSchedule(summary)))
                .availability(
                    ScheduleRecommendationResponseDTO.AvailabilitySummary.builder()
                        .totalDays(totalDays)
                        .availableDays(availableDays)
                        .availabilityPercentage(availabilityPct)
                        .summary(availableDays + "/" + totalDays + " days available")
                        .allAvailable(availableDays >= totalDays)
                        .build())
                .build());
      }
    }

    log.info(
        "SELECT for runId {}: {} existing, {} new schedules returned",
        runId,
        existingSchedules.size(),
        newScheduleMap.size());

    return ScheduleRecommendationResponseDTO.builder()
        .requestId(UUID.randomUUID().toString())
        .generatedAt(LocalDateTime.now())
        .schedules(inventorySchedules)
        .build();
  }

  /**
   * Mark inventories as selected (MANUAL) without generating or returning schedule recommendations.
   * Used when the caller (e.g. mw-planner) creates its own default schedules for manually selected
   * inventories. No schedule data is persisted or returned.
   */
  public void markInventoriesAsSelectedOnly(String runId, List<String> inventoryIds) {
    RecommendationRun run =
        recommendationRunRepository
            .findByRunId(runId)
            .orElseThrow(
                () ->
                    new BaseException(
                        ErrorCode.RECOMMENDATION_RUN_NOT_FOUND,
                        "Recommendation run not found for runId: " + runId));

    if (run.getStatus() == RecommendationRun.RunStatus.IN_PROGRESS) {
      throw new BaseException(
          ErrorCode.RECOMMENDATION_IN_PROGRESS,
          "Cannot select inventories while recommendation is still in progress");
    }

    var results = recommendationResultRepository.findByRunIdAndInventoryIdIn(runId, inventoryIds);
    for (var result : results) {
      result.setSelectionMode(SelectionMode.MANUAL);
    }
    if (!results.isEmpty()) {
      recommendationResultRepository.saveAll(results);
    }
    log.info(
        "Marked {} inventories as MANUAL for runId {} (no schedule recommendation)",
        results.size(),
        runId);
  }

  /**
   * DESELECT operation: remove schedules and clear selection mode for the requested inventoryIds.
   */
  public void deselectInventories(String runId, List<String> inventoryIds) {
    log.info(
        "DESELECT for runId {}: removing schedules for {} inventories", runId, inventoryIds.size());

    runScheduleRecommendationRepository.deleteByRunIdAndInventoryIdIn(runId, inventoryIds);

    var results = recommendationResultRepository.findByRunIdAndInventoryIdIn(runId, inventoryIds);
    for (var result : results) {
      result.setSelectionMode(null);
    }
    if (!results.isEmpty()) {
      recommendationResultRepository.saveAll(results);
    }
  }

  /**
   * Auto-optimize schedules for the given run and inventory IDs. Cleans up existing schedules for
   * these inventories, then runs Round 1 (day-based: minDays or 1 day + iteration, excluding
   * 100%-booked) and optionally Round 2 (hour-based: minHours for non-classic when a full day would
   * exceed budget) until budget or goal is met. Respects operational times and availability.
   */
  public RunSchedulesResponseDTO autoOptimizeSchedules(String runId, List<String> inventoryIds) {
    if (inventoryIds == null || inventoryIds.isEmpty()) {
      return RunSchedulesResponseDTO.builder().runId(runId).schedules(List.of()).build();
    }
    RecommendationRun run =
        recommendationRunRepository
            .findByRunId(runId)
            .orElseThrow(
                () ->
                    new BaseException(
                        ErrorCode.RECOMMENDATION_RUN_NOT_FOUND,
                        "Recommendation run not found for runId: " + runId));
    if (run.getStatus() == RecommendationRun.RunStatus.IN_PROGRESS) {
      throw new BaseException(
          ErrorCode.RECOMMENDATION_IN_PROGRESS,
          "Cannot auto-optimize while recommendation is still in progress");
    }
    runScheduleRecommendationRepository.deleteByRunIdAndInventoryIdIn(runId, inventoryIds);

    List<Inventory> inventories =
        inventoryRepository.findByInventoryIdIn(new ArrayList<>(inventoryIds));
    if (inventories.isEmpty()) {
      return RunSchedulesResponseDTO.builder().runId(runId).schedules(List.of()).build();
    }

    RecommendationRequestDTO request = run.getRequest();
    LocalDate startDate = request.getStartDate();
    LocalDate endDate = request.getEndDate();
    int totalRunDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    BigDecimal budget = request.getBudget();
    RecommendationRequestDTO.CampaignGoal goal = request.getGoal();
    Long goalValue = request.getGoalValue();
    String campaignId = run.getCampaignId() != null ? run.getCampaignId() : "";

    boolean hasBudget = budget != null && budget.compareTo(BigDecimal.ZERO) > 0;
    boolean hasGoal =
        goal != null
            && goalValue != null
            && goalValue > 0
            && (goal == RecommendationRequestDTO.CampaignGoal.IMPRESSIONS
                || goal == RecommendationRequestDTO.CampaignGoal.REACH);

    Map<String, ScheduleSummaryDTO> scheduleMap;
    if (!hasBudget && !hasGoal) {
      // Single iteration with per-inventory effective days (minDays or 1), then return
      Map<String, LocalDate> effectiveEndByInv = new HashMap<>();
      for (Inventory inv : inventories) {
        Integer minDays = getMinDays(inv);
        int baseDays = minDays != null ? minDays : 1;
        int effectiveDays = Math.min(baseDays, totalRunDays);
        LocalDate effectiveEnd = startDate.plusDays(effectiveDays - 1);
        if (effectiveEnd.isAfter(endDate)) effectiveEnd = endDate;
        effectiveEndByInv.put(inv.getInventoryId(), effectiveEnd);
      }
      scheduleMap =
          buildScheduleSummariesForInventories(
              inventories, startDate, endDate, effectiveEndByInv, null, goal);
    } else {
      scheduleMap =
          runRound1(
              inventories,
              startDate,
              endDate,
              totalRunDays,
              budget,
              goal,
              goalValue,
              hasBudget,
              hasGoal);
      if (scheduleMap == null) {
        scheduleMap = Map.of();
      }
      // Round 2: when budget is set and adding one full day would exceed, add minHours for
      // non-classic
      if (hasBudget
          && !scheduleMap.isEmpty()
          && !isBudgetOrGoalMet(scheduleMap, budget, goal, goalValue)) {
        scheduleMap =
            runRound2(inventories, startDate, endDate, scheduleMap, budget, goal, goalValue);
      }
    }

    if (scheduleMap.isEmpty()) {
      return RunSchedulesResponseDTO.builder().runId(runId).schedules(List.of()).build();
    }
    List<RunScheduleRecommendation> entities = new ArrayList<>();
    for (Map.Entry<String, ScheduleSummaryDTO> e : scheduleMap.entrySet()) {
      ScheduleSummaryDTO s = e.getValue();
      if (s == null) continue;
      entities.add(
          RunScheduleRecommendation.builder()
              .runId(runId)
              .campaignId(campaignId)
              .inventoryId(e.getKey())
              .scheduleId(s.getScheduleId())
              .scheduleStartDate(s.getScheduleStartDate())
              .scheduleEndDate(s.getScheduleEndDate())
              .bookingMatrix(s.getBookingMatrix())
              .adPlays(s.getAdPlays())
              .plannedSot(s.getPlannedSot())
              .totalSot(s.getTotalSot())
              .spotsPerLoop(s.getSpotsPerLoop())
              .spotsPerHour(s.getSpotsPerHour())
              .duration(s.getDuration())
              .basePrice(s.getBasePrice())
              .estimatedImpressions(s.getEstimatedImpressions())
              .estimatedReach(s.getEstimatedReach())
              .currency(s.getCurrency())
              .sellingTerm(s.getSellingTerm())
              .build());
    }
    runScheduleRecommendationRepository.saveAll(entities);

    List<RunSchedulesResponseDTO.RunScheduleItemDTO> items =
        scheduleMap.entrySet().stream()
            .filter(e -> e.getValue() != null)
            .map(
                e -> {
                  ScheduleSummaryDTO s = e.getValue();
                  return RunSchedulesResponseDTO.RunScheduleItemDTO.builder()
                      .inventoryId(e.getKey())
                      .scheduleId(s.getScheduleId())
                      .scheduleStartDate(s.getScheduleStartDate())
                      .scheduleEndDate(s.getScheduleEndDate())
                      .bookingMatrix(s.getBookingMatrix())
                      .adPlays(s.getAdPlays())
                      .plannedSot(s.getPlannedSot())
                      .totalSot(s.getTotalSot())
                      .spotsPerLoop(s.getSpotsPerLoop())
                      .spotsPerHour(s.getSpotsPerHour())
                      .duration(s.getDuration())
                      .basePrice(s.getBasePrice())
                      .estimatedImpressions(s.getEstimatedImpressions())
                      .estimatedReach(s.getEstimatedReach())
                      .currency(s.getCurrency())
                      .sellingTerm(s.getSellingTerm())
                      .build();
                })
            .toList();
    return RunSchedulesResponseDTO.builder().runId(runId).schedules(items).build();
  }

  private static Integer getMinDays(Inventory inv) {
    if (inv.getSellingTerm() == null) return null;
    return inv.getSellingTerm().getMinDays();
  }

  private static int getMinHours(Inventory inv) {
    if (inv.getSellingTerm() == null || inv.getSellingTerm().getMinHours() == null) return 1;
    return Math.max(1, inv.getSellingTerm().getMinHours());
  }

  private boolean isBudgetOrGoalMet(
      Map<String, ScheduleSummaryDTO> scheduleMap,
      BigDecimal budget,
      RecommendationRequestDTO.CampaignGoal goal,
      Long goalValue) {
    BigDecimal totalCost = totalCost(scheduleMap);
    long totalImpressions = totalImpressions(scheduleMap);
    long totalReach = totalReach(scheduleMap);
    if (budget != null
        && budget.compareTo(BigDecimal.ZERO) > 0
        && totalCost != null
        && totalCost.compareTo(budget) >= 0) {
      return true;
    }
    if (goal == RecommendationRequestDTO.CampaignGoal.IMPRESSIONS
        && goalValue != null
        && goalValue > 0
        && totalImpressions >= goalValue) {
      return true;
    }
    if (goal == RecommendationRequestDTO.CampaignGoal.REACH
        && goalValue != null
        && goalValue > 0
        && totalReach >= goalValue) {
      return true;
    }
    return false;
  }

  private BigDecimal totalCost(Map<String, ScheduleSummaryDTO> scheduleMap) {
    BigDecimal sum = BigDecimal.ZERO;
    for (ScheduleSummaryDTO s : scheduleMap.values()) {
      if (s != null && s.getBasePrice() != null) {
        sum = sum.add(BigDecimal.valueOf(s.getBasePrice()));
      }
    }
    return sum;
  }

  private long totalImpressions(Map<String, ScheduleSummaryDTO> scheduleMap) {
    long sum = 0;
    for (ScheduleSummaryDTO s : scheduleMap.values()) {
      if (s != null && s.getEstimatedImpressions() != null) {
        sum += s.getEstimatedImpressions();
      }
    }
    return sum;
  }

  private long totalReach(Map<String, ScheduleSummaryDTO> scheduleMap) {
    long sum = 0;
    for (ScheduleSummaryDTO s : scheduleMap.values()) {
      if (s != null && s.getEstimatedReach() != null) {
        sum += s.getEstimatedReach();
      }
    }
    return sum;
  }

  private Map<String, ScheduleSummaryDTO> runRound1(
      List<Inventory> inventories,
      LocalDate startDate,
      LocalDate endDate,
      int totalRunDays,
      BigDecimal budget,
      RecommendationRequestDTO.CampaignGoal goal,
      Long goalValue,
      boolean hasBudget,
      boolean hasGoal) {
    Map<String, ScheduleSummaryDTO> currentMap = new HashMap<>();
    int iteration = 0;
    int maxIterations = Math.max(totalRunDays, 1);
    List<BookingData> allBookings =
        bookingRepository.findByInventoryIdInAndDateRange(
            inventories.stream().map(Inventory::getInventoryId).toList(), startDate, endDate);

    while (iteration < maxIterations) {
      Map<String, LocalDate> effectiveEndByInv = new HashMap<>();
      for (Inventory inv : inventories) {
        Integer minDays = getMinDays(inv);
        int baseDays = minDays != null ? minDays : 1;
        int effectiveDays = Math.min(baseDays + iteration, totalRunDays);
        ScheduleSummaryDTO existing = currentMap.get(inv.getInventoryId());
        if (existing != null
            && existing.getBookingMatrix() != null
            && existing.getBookingMatrix().size() >= totalRunDays) {
          effectiveDays = totalRunDays;
        }
        LocalDate effectiveEnd = startDate.plusDays(effectiveDays - 1);
        if (effectiveEnd.isAfter(endDate)) effectiveEnd = endDate;
        effectiveEndByInv.put(inv.getInventoryId(), effectiveEnd);
      }
      currentMap =
          buildScheduleSummariesForInventories(
              inventories, startDate, endDate, effectiveEndByInv, null, goal);
      if (currentMap.isEmpty()) break;

      enrichSchedulesWithReachAndFrequency(
          currentMap,
          inventories,
          startDate,
          currentMap.values().stream()
              .map(ScheduleSummaryDTO::getScheduleEndDate)
              .filter(Objects::nonNull)
              .max(LocalDate::compareTo)
              .orElse(endDate),
          goal);

      if (isBudgetOrGoalMet(currentMap, budget, goal, goalValue)) {
        return currentMap;
      }
      boolean moreDaysPossible =
          currentMap.values().stream()
              .anyMatch(
                  s -> s.getBookingMatrix() != null && s.getBookingMatrix().size() < totalRunDays);
      if (!moreDaysPossible) {
        return currentMap;
      }
      boolean wouldExceedBudget =
          hasBudget
              && wouldAddingOneFullDayExceedBudget(
                  inventories, startDate, endDate, currentMap, budget, goal, allBookings);
      if (wouldExceedBudget) {
        break;
      }
      iteration++;
    }
    return currentMap;
  }

  private boolean wouldAddingOneFullDayExceedBudget(
      List<Inventory> inventories,
      LocalDate startDate,
      LocalDate endDate,
      Map<String, ScheduleSummaryDTO> currentMap,
      BigDecimal budget,
      RecommendationRequestDTO.CampaignGoal goal,
      List<BookingData> allBookings) {
    BigDecimal currentTotal = totalCost(currentMap);
    if (currentTotal == null) return false;
    for (Inventory inv : inventories) {
      if (isClassicInventory(inv)) continue;
      ScheduleSummaryDTO s = currentMap.get(inv.getInventoryId());
      if (s == null || s.getBookingMatrix() == null) continue;
      if (s.getBookingMatrix().size() >= (int) ChronoUnit.DAYS.between(startDate, endDate) + 1)
        continue;
      LocalDate nextEnd =
          s.getScheduleEndDate() != null
              ? s.getScheduleEndDate().plusDays(1)
              : startDate.plusDays(1);
      if (nextEnd.isAfter(endDate)) continue;
      List<BookingData> forInv =
          allBookings.stream()
              .filter(b -> inv.getInventoryId().equals(b.getInventoryId()))
              .toList();
      ScheduleSummaryDTO tentative =
          buildScheduleSummaryForInventory(inv, forInv, startDate, nextEnd, null, goal, null);
      if (tentative == null) continue;
      Map<String, ScheduleSummaryDTO> single = new HashMap<>();
      single.put(inv.getInventoryId(), tentative);
      enrichSchedulesWithReachAndFrequency(single, List.of(inv), startDate, nextEnd, goal);
      BigDecimal invCurrent =
          s.getBasePrice() != null ? BigDecimal.valueOf(s.getBasePrice()) : BigDecimal.ZERO;
      BigDecimal invTentative =
          tentative.getBasePrice() != null
              ? BigDecimal.valueOf(tentative.getBasePrice())
              : BigDecimal.ZERO;
      if (currentTotal.subtract(invCurrent).add(invTentative).compareTo(budget) <= 0) {
        return false;
      }
    }
    return true;
  }

  private Map<String, ScheduleSummaryDTO> runRound2(
      List<Inventory> inventories,
      LocalDate startDate,
      LocalDate endDate,
      Map<String, ScheduleSummaryDTO> currentMap,
      BigDecimal budget,
      RecommendationRequestDTO.CampaignGoal goal,
      Long goalValue) {
    List<Inventory> digitalInventories =
        inventories.stream().filter(inv -> !isClassicInventory(inv)).toList();
    if (digitalInventories.isEmpty()) return currentMap;

    List<BookingData> allBookings =
        bookingRepository.findByInventoryIdInAndDateRange(
            inventories.stream().map(Inventory::getInventoryId).toList(), startDate, endDate);
    Map<String, ScheduleSummaryDTO> map = new HashMap<>(currentMap);
    boolean progress = true;
    while (progress) {
      progress = false;
      String bestInventoryId = null;
      BigDecimal bestNewTotal = null;
      ScheduleSummaryDTO bestSummary = null;

      for (Inventory inv : digitalInventories) {
        ScheduleSummaryDTO s = map.get(inv.getInventoryId());
        LocalDate nextDate;
        if (s == null || s.getScheduleEndDate() == null) {
          nextDate = startDate;
        } else {
          nextDate = s.getScheduleEndDate().plusDays(1);
        }
        if (nextDate.isAfter(endDate)) continue;
        int minHours = getMinHours(inv);
        List<BookingData> forInv =
            allBookings.stream()
                .filter(b -> inv.getInventoryId().equals(b.getInventoryId()))
                .toList();
        ScheduleSummaryDTO withPartial =
            buildScheduleSummaryForInventory(
                inv, forInv, startDate, nextDate, null, goal, minHours);
        if (withPartial == null) continue;
        Map<String, ScheduleSummaryDTO> single = new HashMap<>();
        single.put(inv.getInventoryId(), withPartial);
        enrichSchedulesWithReachAndFrequency(single, List.of(inv), startDate, nextDate, goal);
        BigDecimal currentTotal = totalCost(map);
        BigDecimal invOld =
            s != null && s.getBasePrice() != null
                ? BigDecimal.valueOf(s.getBasePrice())
                : BigDecimal.ZERO;
        BigDecimal invNew =
            withPartial.getBasePrice() != null
                ? BigDecimal.valueOf(withPartial.getBasePrice())
                : BigDecimal.ZERO;
        BigDecimal newTotal =
            (currentTotal != null ? currentTotal : BigDecimal.ZERO).subtract(invOld).add(invNew);
        if (newTotal.compareTo(budget) > 0) continue;
        if (bestNewTotal == null || newTotal.compareTo(bestNewTotal) > 0) {
          bestNewTotal = newTotal;
          bestInventoryId = inv.getInventoryId();
          bestSummary = withPartial;
        }
      }
      if (bestInventoryId != null && bestSummary != null) {
        map.put(bestInventoryId, bestSummary);
        progress = true;
        if (isBudgetOrGoalMet(map, budget, goal, goalValue)) {
          return map;
        }
      }
    }
    return map;
  }

  /**
   * Build the best schedule for a single inventory that fits within the given budget cap. Uses
   * minDays (or 1) then adds full days until adding one more would exceed budget; for non-classic
   * inventories then adds single hours on the next day until the next hour would exceed budget.
   * Respects operational hours and booked hours via existing buildScheduleSummaryForInventory.
   *
   * @return empty if inventory cannot be scheduled or minDays schedule already exceeds budgetCap
   */
  public Optional<ScheduleSummaryDTO> buildBestScheduleForBudgetCap(
      Inventory inventory,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal budgetCap,
      RecommendationRequestDTO.CampaignGoal goal) {
    if (inventory == null
        || inventory.getInventoryId() == null
        || budgetCap == null
        || budgetCap.compareTo(BigDecimal.ZERO) <= 0) {
      return Optional.empty();
    }
    int totalRunDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    if (totalRunDays <= 0) {
      return Optional.empty();
    }
    Integer minDaysVal = getMinDays(inventory);
    int baseDays = minDaysVal != null ? minDaysVal : 1;
    int effectiveDays = Math.min(baseDays, totalRunDays);
    LocalDate effectiveEnd = startDate.plusDays(effectiveDays - 1);
    if (effectiveEnd.isAfter(endDate)) {
      effectiveEnd = endDate;
    }

    List<BookingData> forInv =
        bookingRepository.findByInventoryIdInAndDateRange(
            List.of(inventory.getInventoryId()), startDate, endDate);

    // Min-days schedule: do not add inventory if it already exceeds budget
    ScheduleSummaryDTO schedule =
        buildScheduleSummaryForInventory(
            inventory, forInv, startDate, effectiveEnd, null, goal, null);
    if (schedule == null || schedule.getBasePrice() == null) {
      return Optional.empty();
    }
    BigDecimal cost = BigDecimal.valueOf(schedule.getBasePrice());
    if (cost.compareTo(budgetCap) > 0) {
      return Optional.empty();
    }

    Map<String, ScheduleSummaryDTO> singleMap = new HashMap<>();
    singleMap.put(inventory.getInventoryId(), schedule);
    enrichSchedulesWithReachAndFrequency(
        singleMap, List.of(inventory), startDate, effectiveEnd, goal);
    schedule = singleMap.get(inventory.getInventoryId());
    if (schedule == null) {
      return Optional.empty();
    }
    cost = schedule.getBasePrice() != null ? BigDecimal.valueOf(schedule.getBasePrice()) : cost;

    // Add full days until adding one more would exceed budget
    while (effectiveEnd.isBefore(endDate)) {
      LocalDate nextEnd = effectiveEnd.plusDays(1);
      ScheduleSummaryDTO nextSchedule =
          buildScheduleSummaryForInventory(inventory, forInv, startDate, nextEnd, null, goal, null);
      if (nextSchedule == null || nextSchedule.getBasePrice() == null) {
        break;
      }
      BigDecimal nextCost = BigDecimal.valueOf(nextSchedule.getBasePrice());
      if (nextCost.compareTo(budgetCap) > 0) {
        break;
      }
      singleMap.clear();
      singleMap.put(inventory.getInventoryId(), nextSchedule);
      enrichSchedulesWithReachAndFrequency(singleMap, List.of(inventory), startDate, nextEnd, goal);
      nextSchedule = singleMap.get(inventory.getInventoryId());
      if (nextSchedule == null) {
        break;
      }
      schedule = nextSchedule;
      cost =
          nextSchedule.getBasePrice() != null
              ? BigDecimal.valueOf(nextSchedule.getBasePrice())
              : nextCost;
      effectiveEnd = nextEnd;
    }

    // Non-classic only: add single hours on next day until next hour would exceed budget
    if (!isClassicInventory(inventory) && effectiveEnd.isBefore(endDate)) {
      LocalDate nextDay = effectiveEnd.plusDays(1);
      int maxHoursToTry = 24;
      ScheduleSummaryDTO bestPartial = null;
      BigDecimal bestPartialCost = null;
      for (int h = 1; h <= maxHoursToTry; h++) {
        ScheduleSummaryDTO partial =
            buildScheduleSummaryForInventory(inventory, forInv, startDate, nextDay, null, goal, h);
        if (partial == null || partial.getBasePrice() == null) {
          break;
        }
        BigDecimal partialCost = BigDecimal.valueOf(partial.getBasePrice());
        if (partialCost.compareTo(budgetCap) > 0) {
          break;
        }
        singleMap.clear();
        singleMap.put(inventory.getInventoryId(), partial);
        enrichSchedulesWithReachAndFrequency(
            singleMap, List.of(inventory), startDate, nextDay, goal);
        partial = singleMap.get(inventory.getInventoryId());
        if (partial == null) {
          break;
        }
        bestPartial = partial;
        bestPartialCost =
            partial.getBasePrice() != null
                ? BigDecimal.valueOf(partial.getBasePrice())
                : partialCost;
      }
      if (bestPartial != null && bestPartialCost != null) {
        schedule = bestPartial;
      }
    }

    return Optional.of(schedule);
  }

  /**
   * Phase 3.1: Batch version of buildBestScheduleForBudgetCap. Builds best schedules for multiple
   * inventories that fit within budgetCap with a SINGLE Measure API call (instead of one call per
   * inventory per iteration). Generates all candidate schedules, enriches them all at once, then
   * selects the best per inventory.
   *
   * @param inventories List of inventories to build schedules for
   * @param startDate Campaign start date
   * @param endDate Campaign end date
   * @param budgetCap Maximum budget per inventory
   * @param goal Campaign goal (IMPRESSIONS, REACH, etc.)
   * @return Map of inventoryId to best schedule that fits budget
   */
  public Map<String, ScheduleSummaryDTO> buildBestSchedulesForBudgetCapBatch(
      List<Inventory> inventories,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal budgetCap,
      RecommendationRequestDTO.CampaignGoal goal) {
    if (inventories == null
        || inventories.isEmpty()
        || budgetCap == null
        || budgetCap.compareTo(BigDecimal.ZERO) <= 0) {
      return Map.of();
    }
    int totalRunDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    if (totalRunDays <= 0) {
      return Map.of();
    }

    // Step 1: Batch-fetch all booking data for all inventories
    List<String> inventoryIds =
        inventories.stream().map(Inventory::getInventoryId).filter(Objects::nonNull).toList();
    List<BookingData> allBookings =
        bookingRepository.findByInventoryIdInAndDateRange(inventoryIds, startDate, endDate);
    Map<String, List<BookingData>> bookingsByInventory =
        allBookings.stream()
            .collect(
                Collectors.groupingBy(
                    BookingData::getInventoryId, Collectors.mapping(b -> b, Collectors.toList())));

    // Step 2: Generate ALL candidate schedules (without Measure API enrichment yet)
    Map<String, List<ScheduleSummaryDTO>> candidatesByInventory = new HashMap<>();
    List<ScheduleSummaryDTO> allCandidates = new ArrayList<>();

    for (Inventory inventory : inventories) {
      List<BookingData> forInv =
          bookingsByInventory.getOrDefault(inventory.getInventoryId(), List.of());
      List<ScheduleSummaryDTO> inventoryCandidates = new ArrayList<>();

      Integer minDaysVal = getMinDays(inventory);
      int baseDays = minDaysVal != null ? minDaysVal : 1;
      int effectiveDays = Math.min(baseDays, totalRunDays);

      // Generate candidates: min days schedule + each additional full day
      for (int days = effectiveDays; days <= totalRunDays; days++) {
        LocalDate candidateEnd = startDate.plusDays(days - 1);
        if (candidateEnd.isAfter(endDate)) {
          candidateEnd = endDate;
        }
        ScheduleSummaryDTO candidate =
            buildScheduleSummaryForInventory(
                inventory, forInv, startDate, candidateEnd, null, goal, null);
        if (candidate != null && candidate.getBasePrice() != null) {
          inventoryCandidates.add(candidate);
          allCandidates.add(candidate);
        }
      }

      // For non-classic: add hourly candidates on the next day after full coverage
      if (!isClassicInventory(inventory)
          && totalRunDays < (int) ChronoUnit.DAYS.between(startDate, endDate) + 1) {
        LocalDate nextDay = startDate.plusDays(totalRunDays);
        for (int h = 1; h <= 24; h++) {
          ScheduleSummaryDTO partial =
              buildScheduleSummaryForInventory(
                  inventory, forInv, startDate, nextDay, null, goal, h);
          if (partial != null && partial.getBasePrice() != null) {
            inventoryCandidates.add(partial);
            allCandidates.add(partial);
          }
        }
      }

      candidatesByInventory.put(inventory.getInventoryId(), inventoryCandidates);
    }

    // Step 3: SINGLE batch Measure API call to enrich ALL candidates
    if (!allCandidates.isEmpty()) {
      Map<String, ScheduleSummaryDTO> candidateMap = new HashMap<>();
      for (int i = 0; i < allCandidates.size(); i++) {
        ScheduleSummaryDTO s = allCandidates.get(i);
        // Use a composite key: inventoryId + scheduleId to handle multiple candidates per inventory
        candidateMap.put(s.getScheduleId(), s);
      }
      LocalDate maxEnd =
          allCandidates.stream()
              .map(ScheduleSummaryDTO::getScheduleEndDate)
              .filter(Objects::nonNull)
              .max(LocalDate::compareTo)
              .orElse(endDate);
      enrichSchedulesWithReachAndFrequency(candidateMap, inventories, startDate, maxEnd, goal);

      // Update candidates with enriched data
      for (ScheduleSummaryDTO s : allCandidates) {
        ScheduleSummaryDTO enriched = candidateMap.get(s.getScheduleId());
        if (enriched != null) {
          // Replace with enriched version in the candidates list
          candidatesByInventory
              .get(inventoryIdForSchedule(s, inventories))
              .replaceAll(
                  candidate ->
                      candidate.getScheduleId().equals(s.getScheduleId()) ? enriched : candidate);
        }
      }
    }

    // Step 4: Select best schedule per inventory that fits within budget
    Map<String, ScheduleSummaryDTO> bestSchedules = new HashMap<>();
    for (Inventory inventory : inventories) {
      List<ScheduleSummaryDTO> candidates =
          candidatesByInventory.getOrDefault(inventory.getInventoryId(), List.of());
      ScheduleSummaryDTO best = null;
      BigDecimal bestCost = BigDecimal.ZERO;

      for (ScheduleSummaryDTO candidate : candidates) {
        if (candidate.getBasePrice() == null) {
          continue;
        }
        BigDecimal cost = BigDecimal.valueOf(candidate.getBasePrice());
        if (cost.compareTo(budgetCap) <= 0 && cost.compareTo(bestCost) > 0) {
          best = candidate;
          bestCost = cost;
        }
      }

      if (best != null) {
        bestSchedules.put(inventory.getInventoryId(), best);
      }
    }

    return bestSchedules;
  }

  /** Helper to find inventory ID for a schedule (used in batch processing) */
  private String inventoryIdForSchedule(ScheduleSummaryDTO schedule, List<Inventory> inventories) {
    // ScheduleId format: typically contains inventory reference
    for (Inventory inv : inventories) {
      if (schedule.getScheduleId().contains(inv.getInventoryId())) {
        return inv.getInventoryId();
      }
    }
    // Fallback: schedule should have been built from one of these inventories
    return inventories.isEmpty() ? null : inventories.get(0).getInventoryId();
  }

  /** Convert a persisted RunScheduleRecommendation entity to an InventorySchedule response. */
  private ScheduleRecommendationResponseDTO.InventorySchedule toInventoryScheduleFromEntity(
      RunScheduleRecommendation entity, int totalDays) {
    int availableDays = entity.getBookingMatrix() != null ? entity.getBookingMatrix().size() : 0;
    double availabilityPct = totalDays > 0 ? (availableDays * 100.0 / totalDays) : 0.0;

    Inventory inv = inventoryRepository.findById(entity.getInventoryId()).orElse(null);

    ScheduleRecommendationResponseDTO.RecommendedSchedule schedule =
        ScheduleRecommendationResponseDTO.RecommendedSchedule.builder()
            .scheduleId(entity.getScheduleId())
            .scheduleStartDate(entity.getScheduleStartDate())
            .scheduleEndDate(entity.getScheduleEndDate())
            .bookingMatrix(entity.getBookingMatrix())
            .adPlays(entity.getAdPlays())
            .plannedSot(entity.getPlannedSot())
            .totalSot(entity.getTotalSot())
            .spotsPerLoop(entity.getSpotsPerLoop())
            .spotsPerHour(entity.getSpotsPerHour())
            .duration(entity.getDuration())
            .basePrice(entity.getBasePrice())
            .estimatedImpressions(entity.getEstimatedImpressions())
            .estimatedReach(entity.getEstimatedReach())
            .currency(entity.getCurrency())
            .sellingTerm(entity.getSellingTerm())
            .type(ScheduleRecommendationResponseDTO.ScheduleType.LOOP)
            .build();

    return ScheduleRecommendationResponseDTO.InventorySchedule.builder()
        .inventoryId(entity.getInventoryId())
        .referenceId(inv != null ? inv.getReferenceId() : null)
        .inventoryName(inv != null ? inv.getName() : null)
        .recommendedSchedules(List.of(schedule))
        .availability(
            ScheduleRecommendationResponseDTO.AvailabilitySummary.builder()
                .totalDays(totalDays)
                .availableDays(availableDays)
                .availabilityPercentage(availabilityPct)
                .summary(availableDays + "/" + totalDays + " days available")
                .allAvailable(availableDays >= totalDays)
                .build())
        .build();
  }

  /**
   * Map ScheduleSummaryDTO to RecommendedSchedule including estimatedImpressions and
   * estimatedReach.
   */
  private ScheduleRecommendationResponseDTO.RecommendedSchedule toRecommendedSchedule(
      ScheduleSummaryDTO s) {
    return ScheduleRecommendationResponseDTO.RecommendedSchedule.builder()
        .scheduleId(s.getScheduleId())
        .scheduleStartDate(s.getScheduleStartDate())
        .scheduleEndDate(s.getScheduleEndDate())
        .bookingMatrix(s.getBookingMatrix())
        .adPlays(s.getAdPlays())
        .plannedSot(s.getPlannedSot())
        .totalSot(s.getTotalSot())
        .spotsPerLoop(s.getSpotsPerLoop())
        .spotsPerHour(s.getSpotsPerHour())
        .duration(s.getDuration())
        .basePrice(s.getBasePrice())
        .estimatedImpressions(s.getEstimatedImpressions())
        .estimatedReach(s.getEstimatedReach())
        .currency(s.getCurrency())
        .sellingTerm(s.getSellingTerm())
        .type(ScheduleRecommendationResponseDTO.ScheduleType.LOOP)
        .build();
  }

  /**
   * Build schedule summaries for multiple inventories in one go. Batch-loads booking_data for all
   * inventories and date range to minimize DB round trips. Impressions/reach are always populated
   * from Measure API (same as mw-planner); when API is not configured or fails, they remain null.
   */
  public Map<String, ScheduleSummaryDTO> buildScheduleSummariesForInventories(
      List<Inventory> inventories,
      LocalDate startDate,
      LocalDate endDate,
      Map<String, BigDecimal> budgetShareByInventoryId,
      RecommendationRequestDTO.CampaignGoal goal) {
    if (inventories == null || inventories.isEmpty()) {
      return Map.of();
    }
    List<String> ids =
        inventories.stream()
            .map(Inventory::getInventoryId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    List<BookingData> allBookings =
        bookingRepository.findByInventoryIdInAndDateRange(ids, startDate, endDate);
    Map<LocalDate, Map<String, BookingData>> bookingByDateAndInventory = new HashMap<>();
    for (BookingData bd : allBookings) {
      bookingByDateAndInventory
          .computeIfAbsent(bd.getDate(), d -> new HashMap<>())
          .put(bd.getInventoryId(), bd);
    }
    Map<String, ScheduleSummaryDTO> out = new HashMap<>();
    log.info("Inventories to process: {}", inventories.size());
    for (Inventory inv : inventories) {
      List<BookingData> forInv =
          allBookings.stream()
              .filter(b -> inv.getInventoryId().equals(b.getInventoryId()))
              .toList();
      BigDecimal share =
          budgetShareByInventoryId != null
              ? budgetShareByInventoryId.get(inv.getInventoryId())
              : null;
      ScheduleSummaryDTO summary =
          buildScheduleSummaryForInventory(inv, forInv, startDate, endDate, share, goal);
      if (summary != null) {
        out.put(inv.getInventoryId(), summary);
      }
    }
    // Enrich with impressions/reach from Measure API only (same as mw-planner addSchedule)
    enrichSchedulesWithReachAndFrequency(out, inventories, startDate, endDate, goal);
    return out;
  }

  /**
   * Build schedule summaries with per-inventory effective end dates (for auto-optimize). When
   * effectiveEndDateByInventoryId is null or does not contain an inventory, that inventory uses
   * endDate. Booking data is loaded for the full range [startDate, endDate].
   */
  public Map<String, ScheduleSummaryDTO> buildScheduleSummariesForInventories(
      List<Inventory> inventories,
      LocalDate startDate,
      LocalDate endDate,
      Map<String, LocalDate> effectiveEndDateByInventoryId,
      Map<String, BigDecimal> budgetShareByInventoryId,
      RecommendationRequestDTO.CampaignGoal goal) {
    if (inventories == null || inventories.isEmpty()) {
      return Map.of();
    }
    List<String> ids =
        inventories.stream()
            .map(Inventory::getInventoryId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    List<BookingData> allBookings =
        bookingRepository.findByInventoryIdInAndDateRange(ids, startDate, endDate);
    Map<String, ScheduleSummaryDTO> out = new HashMap<>();
    for (Inventory inv : inventories) {
      List<BookingData> forInv =
          allBookings.stream()
              .filter(b -> inv.getInventoryId().equals(b.getInventoryId()))
              .toList();
      LocalDate effectiveEnd =
          effectiveEndDateByInventoryId != null
                  && effectiveEndDateByInventoryId.containsKey(inv.getInventoryId())
              ? effectiveEndDateByInventoryId.get(inv.getInventoryId())
              : endDate;
      if (effectiveEnd.isBefore(startDate)) {
        continue;
      }
      BigDecimal share =
          budgetShareByInventoryId != null
              ? budgetShareByInventoryId.get(inv.getInventoryId())
              : null;
      ScheduleSummaryDTO summary =
          buildScheduleSummaryForInventory(inv, forInv, startDate, effectiveEnd, share, goal);
      if (summary != null) {
        out.put(inv.getInventoryId(), summary);
      }
    }
    LocalDate maxEnd =
        out.isEmpty()
            ? endDate
            : out.values().stream()
                .map(ScheduleSummaryDTO::getScheduleEndDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(endDate);
    enrichSchedulesWithReachAndFrequency(out, inventories, startDate, maxEnd, goal);
    return out;
  }

  /**
   * Enrich schedule summaries with impressions and reach from Measure API, and recalc basePrice
   * when CPM (align with mw-planner
   * CampaignInventorySchedulesService.enrichSchedulesWithReachAndFrequency).
   */
  private void enrichSchedulesWithReachAndFrequency(
      Map<String, ScheduleSummaryDTO> scheduleMap,
      List<Inventory> inventories,
      LocalDate startDate,
      LocalDate endDate,
      RecommendationRequestDTO.CampaignGoal goal) {
    if (scheduleMap == null || scheduleMap.isEmpty() || inventories == null) {
      return;
    }
    List<MeasureInventoryDTO> inventoryDTOs = new ArrayList<>();
    Map<String, Inventory> refIdToInventory = new HashMap<>();
    Map<String, String> refIdToInventoryId = new HashMap<>();
    int duration = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    for (Inventory inv : inventories) {
      ScheduleSummaryDTO s = scheduleMap.get(inv.getInventoryId());
      if (s == null || s.getBookingMatrix() == null || s.getBookingMatrix().isEmpty()) {
        continue;
      }
      String refId = inv.getReferenceId() != null ? inv.getReferenceId() : inv.getInventoryId();
      refIdToInventory.put(refId, inv);
      refIdToInventoryId.put(refId, inv.getInventoryId());
      // List<MeasureInventoryDTO.Dayparts> dayparts =
      //     convertBookingMatrixToDayparts(s.getBookingMatrix(), isClassicInventory(inv));
      inventoryDTOs.add(
          MeasureInventoryDTO.builder()
              .referenceId(refId)
              .type(mapInventoryClassificationToApiType(inv.getClassification()))
              .spotsPerHour(s.getSpotsPerHour() != null ? s.getSpotsPerHour().intValue() : 1)
              // .dayparts(dayparts)
              .build());
    }
    if (inventoryDTOs.isEmpty()) {
      return;
    }
    MeasureReachFrequencyRequestDTO request =
        MeasureReachFrequencyRequestDTO.builder()
            .inventories(inventoryDTOs)
            .duration(duration)
            .build();
    List<MeasureReachFrequencyResponseDTO> responses =
        measureApiClient.getReachAndFrequencyBySites(request, true);
    if (responses == null || responses.isEmpty()) {
      return;
    }
    Map<String, MeasureReachFrequencyResponseDTO> responseByRefId =
        responses.stream()
            .filter(r -> r.getReferenceId() != null)
            .collect(
                Collectors.toMap(
                    MeasureReachFrequencyResponseDTO::getReferenceId, r -> r, (a, b) -> a));
    for (Map.Entry<String, MeasureReachFrequencyResponseDTO> e : responseByRefId.entrySet()) {
      String refId = e.getKey();
      MeasureReachFrequencyResponseDTO resp = e.getValue();
      if (!"success".equals(resp.getStatus())) {
        continue;
      }
      if (resp.getImpressions() == null
          || resp.getImpressions() <= 0
          || resp.getReach() == null
          || resp.getReach() <= 0) {
        continue;
      }
      String inventoryId = refIdToInventoryId.get(refId);
      Inventory inv = refIdToInventory.get(refId);
      ScheduleSummaryDTO s = scheduleMap.get(inventoryId);
      if (inventoryId == null || inv == null || s == null) {
        continue;
      }
      Long impressions = resp.getImpressions();
      Long reach = resp.getReach();
      BigDecimal basePrice =
          calculateScheduleBasePrice(s.getAdPlays(), impressions, inv, goal, startDate, endDate);
      scheduleMap.put(
          inventoryId,
          ScheduleSummaryDTO.builder()
              .scheduleId(s.getScheduleId())
              .scheduleStartDate(s.getScheduleStartDate())
              .scheduleEndDate(s.getScheduleEndDate())
              .bookingMatrix(s.getBookingMatrix())
              .adPlays(s.getAdPlays())
              .plannedSot(s.getPlannedSot())
              .totalSot(s.getTotalSot())
              .spotsPerLoop(s.getSpotsPerLoop())
              .spotsPerHour(s.getSpotsPerHour())
              .duration(s.getDuration())
              .basePrice(basePrice != null ? basePrice.doubleValue() : null)
              .estimatedImpressions(impressions)
              .estimatedReach(reach)
              .currency(s.getCurrency())
              .sellingTerm(s.getSellingTerm())
              .build());
    }
  }

  private static List<MeasureInventoryDTO.Dayparts> convertBookingMatrixToDayparts(
      Map<String, List<Integer>> bookingMatrix, boolean isClassicInventory) {
    if (bookingMatrix == null || bookingMatrix.isEmpty()) {
      return List.of();
    }
    List<MeasureInventoryDTO.Dayparts> out = new ArrayList<>();
    for (Map.Entry<String, List<Integer>> entry : bookingMatrix.entrySet()) {
      String scheduledDate = entry.getKey();
      List<Integer> hours = entry.getValue();
      if (hours == null || hours.isEmpty()) {
        continue;
      }
      List<String> scheduledTimes = null;
      if (!isClassicInventory && hours.size() != 24) {
        scheduledTimes = hours.stream().sorted().map(h -> String.format("%02d", h)).toList();
      }
      out.add(
          MeasureInventoryDTO.Dayparts.builder()
              .scheduledDate(scheduledDate)
              .scheduledTime(scheduledTimes)
              .build());
    }
    return out;
  }

  private static boolean isClassicInventory(Inventory inv) {
    return inv.getClassification() != null
        && inv.getClassification().toLowerCase().contains("classic");
  }

  private static String mapInventoryClassificationToApiType(String classification) {
    if (classification == null) {
      return "billboard";
    }
    String c = classification.toLowerCase();
    if (c.contains("classic")) {
      return "static_billboard";
    }
    if (c.contains("network")) {
      return "network";
    }
    return "billboard";
  }

  /**
   * Build a single schedule summary for one inventory (no partial-day cap). Delegates to 7-arg
   * version with maxHoursOnLastDay = null.
   */
  public ScheduleSummaryDTO buildScheduleSummaryForInventory(
      Inventory inventory,
      List<BookingData> bookingDataForRange,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal budgetShare,
      RecommendationRequestDTO.CampaignGoal goal) {
    return buildScheduleSummaryForInventory(
        inventory, bookingDataForRange, startDate, endDate, budgetShare, goal, null);
  }

  /**
   * Build a single schedule summary for one inventory. Respects operatingTimes and booking_data:
   * hours and dates with &gt;= 90% booking are excluded from bookingMatrix; inventories with &lt;=
   * 10% availability (90%+ booked) are not recommended. Recommended schedule always uses 1 spot per
   * loop. Measure API for impressions/reach/basePrice when available.
   *
   * @param maxHoursOnLastDay when non-null, only the first N operational+available hours on the
   *     last day (endDate) are included (for Round 2 partial-day support)
   */
  public ScheduleSummaryDTO buildScheduleSummaryForInventory(
      Inventory inventory,
      List<BookingData> bookingDataForRange,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal budgetShare,
      RecommendationRequestDTO.CampaignGoal goal,
      Integer maxHoursOnLastDay) {
    if (inventory == null || inventory.getDigitalFields() == null) {
      return null;
    }
    Inventory.DigitalFields df = inventory.getDigitalFields();
    if (!isClassicInventory(inventory)
        && (df.getLoopDuration() == null
            || df.getLoopDuration() == 0
            || df.getSpotsPerLoop() == null)) {
      return null;
    }
    Map<LocalDate, BookingData> byDate =
        bookingDataForRange == null
            ? Map.of()
            : bookingDataForRange.stream()
                .collect(Collectors.toMap(BookingData::getDate, b -> b, (a, b) -> a));
    Map<String, List<Integer>> bookingMatrix = new LinkedHashMap<>();
    double totalSelectedHours = 0.0;
    for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
      List<Integer> availableHours = getAvailableHoursForDate(inventory, d, byDate.get(d));
      // Exclude date from matrix if date-level booking is >= 90%
      if (isClassicInventory(inventory)) {
        if (getBookedPercentageForDate(byDate.get(d)) >= BOOKED_THRESHOLD) {
          availableHours = List.of();
        }
      } else {
        if (getBookedPercentageForDateDigital(inventory, d, byDate.get(d)) >= BOOKED_THRESHOLD) {
          availableHours = List.of();
        }
      }
      // Round 2: on last day only include first maxHoursOnLastDay operational+available hours
      if (maxHoursOnLastDay != null
          && d.equals(endDate)
          && availableHours.size() > maxHoursOnLastDay) {
        availableHours = new ArrayList<>(availableHours.subList(0, maxHoursOnLastDay));
      }
      if (!availableHours.isEmpty()) {
        bookingMatrix.put(d.toString(), availableHours);
        totalSelectedHours += availableHours.size();
      }
    }
    double totalOperatingHours = computeTotalOperatingHoursInRange(inventory, startDate, endDate);
    if (totalOperatingHours <= 0) {
      return null;
    }
    // Don't recommend inventory if availability (share of hours under 90% booked) is <= 10%
    double availabilityPct = (totalSelectedHours / totalOperatingHours) * 100.0;
    if (availabilityPct <= MIN_AVAILABILITY_TO_RECOMMEND) {
      return null;
    }
    int loopsPerHour =
        (df.getLoopDuration() != null && df.getLoopDuration() > 0)
            ? 3600 / df.getLoopDuration()
            : 1;
    // Recommended schedule always 1 spot per loop
    long adPlays = (long) (loopsPerHour * RECOMMENDED_SPOTS_PER_LOOP * totalSelectedHours);
    double plannedSot = calculatePlannedSotFromBookingMatrix(bookingMatrix);
    // Align with mw-planner: spot * adPlays or (cpm/1000)*impressions when impressions from Measure
    BigDecimal basePrice =
        calculateScheduleBasePrice(adPlays, null, inventory, goal, startDate, endDate);
    String currency = null;
    if (inventory.getPrices() != null && !inventory.getPrices().isEmpty()) {
      Inventory.PriceModel price = inventory.getPrices().getFirst();
      currency = price.getCurrency();
    }
    Long duration = getSpotDuration(inventory);
    return ScheduleSummaryDTO.builder()
        .scheduleId(UUID.randomUUID().toString())
        .scheduleStartDate(startDate)
        .scheduleEndDate(endDate)
        .bookingMatrix(bookingMatrix)
        .adPlays(adPlays)
        .plannedSot(plannedSot)
        .totalSot(totalOperatingHours)
        .spotsPerLoop(RECOMMENDED_SPOTS_PER_LOOP)
        .spotsPerHour(loopsPerHour * RECOMMENDED_SPOTS_PER_LOOP)
        .duration(duration)
        .basePrice(basePrice != null ? basePrice.doubleValue() : null)
        .estimatedImpressions(null)
        .estimatedReach(null)
        .currency(currency)
        .sellingTerm(inventory.getSellingTerm())
        .build();
  }

  public long getSpotDuration(Inventory inventory) {
    return inventory.getDigitalFields() != null
            && inventory.getDigitalFields().getSpotDuration() != null
        ? inventory.getDigitalFields().getSpotDuration()
        : 0;
  }

  /**
   * Calculate Planned SOT from booking matrix. Planned SOT is the sum of hours in the booking
   * matrix.
   *
   * @param bookingMatrix The booking matrix mapping dates to hours
   * @return Planned SOT in hours (sum of all hours in booking matrix)
   */
  private double calculatePlannedSotFromBookingMatrix(Map<String, List<Integer>> bookingMatrix) {
    if (bookingMatrix == null || bookingMatrix.isEmpty()) {
      return 0.0;
    }
    return bookingMatrix.values().stream().mapToLong(List::size).sum();
  }

  /**
   * Calculate schedule base price using goal-based pricing (mirrors
   * calculateCostEstimateForSelectedInventory): IMPRESSIONS/REACH → CPM × impressions; SOV/AD_PLAYS
   * → spot × adPlays. A null goal is treated as IMPRESSIONS (CPM × impressions), matching the
   * default allocation ({@code getDefaultAllocationByGoal(null)}) and the displayed result cost;
   * spot × adPlays remains only the last-resort fallback (e.g. pre-enrichment, when impressions are
   * not yet known, or when the inventory has no CPM). Pricing a null-goal, budget-only schedule by
   * the full-run spot buyout made budget-aware selection reject every candidate.
   */
  private BigDecimal calculateScheduleBasePrice(
      Long adPlays,
      Long impressions,
      Inventory inventory,
      RecommendationRequestDTO.CampaignGoal goal,
      LocalDate startDate,
      LocalDate endDate) {
    if (inventory == null || inventory.getPrices() == null || inventory.getPrices().isEmpty()) {
      return null;
    }
    Inventory.PriceModel price = inventory.getPrices().getFirst();
    Double spot = price.getSpot();
    Double cpm = price.getCpm();
    if (goal == RecommendationRequestDTO.CampaignGoal.IMPRESSIONS
        || goal == RecommendationRequestDTO.CampaignGoal.REACH
        || goal == null) {
      if (cpm != null && cpm > 0 && impressions != null) {
        return BigDecimal.valueOf((cpm / 1000.0) * impressions);
      }
    } else if (goal == RecommendationRequestDTO.CampaignGoal.SOV
        || goal == RecommendationRequestDTO.CampaignGoal.AD_PLAYS) {
      if (spot != null && spot > 0 && adPlays != null) {
        return BigDecimal.valueOf(spot * adPlays);
      }
    }
    if (spot != null && adPlays != null) {
      return BigDecimal.valueOf(spot * adPlays);
    }
    if (cpm != null && impressions != null) {
      return BigDecimal.valueOf((cpm / 1000.0) * impressions);
    }
    // Classic inventories: monthly pricing
    if (price.getMonthly() != null && startDate != null && endDate != null) {
      long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
      return BigDecimal.valueOf(price.getMonthly())
          .multiply(BigDecimal.valueOf(days))
          .divide(BigDecimal.valueOf(30), 2, java.math.RoundingMode.HALF_UP);
    }
    return null;
  }

  /**
   * Fetch all recommended schedules for a run. Planner calls this with runId (e.g. after reading
   * RecommendationRun.autoSelectedInventoryIds) to sync schedules to its own DB.
   */
  public RunSchedulesResponseDTO getSchedulesByRunId(String runId) {
    return toRunSchedulesResponseDTO(runId, runScheduleRecommendationRepository.findByRunId(runId));
  }

  /**
   * Fetch only the schedules whose inventory was AUTO-selected for a run. Mirrors {@link
   * #getSchedulesByRunId(String)} but filters to inventories with {@link SelectionMode#AUTO} in
   * recommendation_results.
   */
  public RunSchedulesResponseDTO getSchedulesByRunIdAndSelectionModeAuto(String runId) {
    List<RecommendationResult> autoResults =
        recommendationResultRepository.findByRunIdAndSelectionMode(runId, SelectionMode.AUTO);
    List<String> inventoryIds =
        autoResults.stream().map(RecommendationResult::getInventoryId).distinct().toList();
    log.info("Inventory Ids count: {}", inventoryIds.size());
    if (inventoryIds.isEmpty()) {
      return toRunSchedulesResponseDTO(runId, List.of());
    }
    return toRunSchedulesResponseDTO(
        runId,
        runScheduleRecommendationRepository.findByRunIdAndInventoryIdIn(runId, inventoryIds));
  }

  /** Map a list of run schedules into the planner-facing response DTO. */
  private RunSchedulesResponseDTO toRunSchedulesResponseDTO(
      String runId, List<RunScheduleRecommendation> list) {
    log.info("Found {} schedules for runId {}", list.size(), runId);
    List<RunSchedulesResponseDTO.RunScheduleItemDTO> items =
        list.stream()
            .map(
                r ->
                    RunSchedulesResponseDTO.RunScheduleItemDTO.builder()
                        .inventoryId(r.getInventoryId())
                        .scheduleId(r.getScheduleId())
                        .scheduleStartDate(r.getScheduleStartDate())
                        .scheduleEndDate(r.getScheduleEndDate())
                        .bookingMatrix(r.getBookingMatrix())
                        .adPlays(r.getAdPlays())
                        .plannedSot(r.getPlannedSot())
                        .totalSot(r.getTotalSot())
                        .spotsPerLoop(r.getSpotsPerLoop())
                        .spotsPerHour(r.getSpotsPerHour())
                        .duration(r.getDuration())
                        .basePrice(r.getBasePrice())
                        .estimatedImpressions(r.getEstimatedImpressions())
                        .estimatedReach(r.getEstimatedReach())
                        .currency(r.getCurrency())
                        .sellingTerm(r.getSellingTerm())
                        .build())
            .toList();
    return RunSchedulesResponseDTO.builder().runId(runId).schedules(items).build();
  }

  /**
   * Persist schedule summaries for a run (replaces any existing schedules for this runId). Used by
   * async auto-select and by markSelectedInventories.
   */
  public void saveSchedulesForRun(
      String runId, String campaignId, Map<String, ScheduleSummaryDTO> scheduleSummaries) {
    runScheduleRecommendationRepository.deleteByRunId(runId);
    if (scheduleSummaries == null || scheduleSummaries.isEmpty()) {
      return;
    }
    List<RunScheduleRecommendation> entities = new ArrayList<>();
    for (Map.Entry<String, ScheduleSummaryDTO> e : scheduleSummaries.entrySet()) {
      ScheduleSummaryDTO s = e.getValue();
      if (s == null) {
        continue;
      }
      entities.add(
          RunScheduleRecommendation.builder()
              .runId(runId)
              .campaignId(campaignId)
              .inventoryId(e.getKey())
              .scheduleId(s.getScheduleId())
              .scheduleStartDate(s.getScheduleStartDate())
              .scheduleEndDate(s.getScheduleEndDate())
              .bookingMatrix(s.getBookingMatrix())
              .adPlays(s.getAdPlays())
              .plannedSot(s.getPlannedSot())
              .totalSot(s.getTotalSot())
              .spotsPerLoop(s.getSpotsPerLoop())
              .spotsPerHour(s.getSpotsPerHour())
              .duration(s.getDuration())
              .basePrice(s.getBasePrice())
              .estimatedImpressions(s.getEstimatedImpressions())
              .estimatedReach(s.getEstimatedReach())
              .currency(s.getCurrency())
              .sellingTerm(s.getSellingTerm())
              .build());
    }
    runScheduleRecommendationRepository.saveAll(entities);
  }

  private List<Integer> getAvailableHoursForDate(
      Inventory inventory, LocalDate date, BookingData bookingData) {
    List<Integer> operating = resolveOperatingHoursForWeekday(inventory, weekdayOf(date));
    List<Integer> available = new ArrayList<>();
    for (int h : operating) {
      if (getBookedPercentageForHour(bookingData, String.valueOf(h)) < BOOKED_THRESHOLD) {
        available.add(h);
      }
    }
    return available;
  }

  /**
   * Returns operating hours (0-23, sorted) for the given weekday. Classic inventories with no
   * operatingTimes defined are assumed 24h. Non-classic without operatingTimes return empty.
   */
  private List<Integer> resolveOperatingHoursForWeekday(
      Inventory inventory, Inventory.Weekday weekday) {
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> times = inventory.getOperatingTimes();
    if (times == null || times.get(weekday) == null || times.get(weekday).isEmpty()) {
      if (isClassicInventory(inventory)) {
        List<Integer> allHours = new ArrayList<>();
        for (int h = 0; h < 24; h++) allHours.add(h);
        return allHours;
      }
      return List.of();
    }
    Set<Integer> hours = new HashSet<>();
    for (Inventory.OperatingTime ot : times.get(weekday)) {
      try {
        LocalTime start = LocalTime.parse(ot.getStart(), TIME_PARSER);
        LocalTime end = LocalTime.parse(ot.getEnd(), TIME_PARSER);
        int startH = start.getHour();
        int endH = end.getHour();
        if (startH <= endH) {
          for (int h = startH; h <= endH; h++) hours.add(h);
        } else {
          for (int h = startH; h < 24; h++) hours.add(h);
          for (int h = 0; h <= endH; h++) hours.add(h);
        }
      } catch (Exception e) {
        log.warn(
            "Parse operating time for inventory {}: {}",
            inventory.getInventoryId(),
            e.getMessage());
      }
    }
    List<Integer> list = new ArrayList<>(hours);
    Collections.sort(list);
    return list;
  }

  private double getBookedPercentageForHour(BookingData bookingData, String hourKey) {
    if (bookingData == null
        || bookingData.getHourlyBookings() == null
        || bookingData.getHourlyBookings().isEmpty()) {
      return 0.0;
    }
    List<BookingData.DealBooking> hourlyBookings = bookingData.getHourlyBookings().get(hourKey);
    if (hourlyBookings == null || hourlyBookings.isEmpty()) {
      return 0.0;
    }
    return hourlyBookings.stream()
        .mapToDouble(b -> b.getPercentage() != null ? b.getPercentage() : 0.0)
        .sum();
  }

  /** Date-level booked percentage for Classic inventory (day-level booking). */
  private double getBookedPercentageForDate(BookingData bookingData) {
    if (bookingData == null
        || bookingData.getBooking() == null
        || bookingData.getBooking().isEmpty()) {
      return 0.0;
    }
    return bookingData.getBooking().stream()
        .mapToDouble(b -> b.getPercentage() != null ? b.getPercentage() : 0.0)
        .sum();
  }

  /** Average booked percentage over operating hours for a date (Digital inventory). */
  private double getBookedPercentageForDateDigital(
      Inventory inventory, LocalDate date, BookingData bookingData) {
    List<Integer> operatingHours = getOperatingHoursForDate(inventory, date);
    if (operatingHours.isEmpty()) {
      return 0.0;
    }
    double sum = 0.0;
    for (int h : operatingHours) {
      sum += getBookedPercentageForHour(bookingData, String.valueOf(h));
    }
    return sum / operatingHours.size();
  }

  /** Returns list of operating hour numbers (0-23) for the given date. */
  private List<Integer> getOperatingHoursForDate(Inventory inventory, LocalDate date) {
    return resolveOperatingHoursForWeekday(inventory, weekdayOf(date));
  }

  /** Total operating hours in the date range (for availability check). */
  private double computeTotalOperatingHoursInRange(
      Inventory inventory, LocalDate start, LocalDate end) {
    double totalHours = 0.0;
    for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
      totalHours += resolveOperatingHoursForWeekday(inventory, weekdayOf(d)).size();
    }
    return totalHours;
  }

  private static Inventory.Weekday weekdayOf(LocalDate date) {
    return switch (date.getDayOfWeek()) {
      case MONDAY -> Inventory.Weekday.MONDAY;
      case TUESDAY -> Inventory.Weekday.TUESDAY;
      case WEDNESDAY -> Inventory.Weekday.WEDNESDAY;
      case THURSDAY -> Inventory.Weekday.THURSDAY;
      case FRIDAY -> Inventory.Weekday.FRIDAY;
      case SATURDAY -> Inventory.Weekday.SATURDAY;
      case SUNDAY -> Inventory.Weekday.SUNDAY;
    };
  }
}
