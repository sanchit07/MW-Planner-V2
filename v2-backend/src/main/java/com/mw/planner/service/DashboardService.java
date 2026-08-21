package com.mw.planner.service;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.Inventory;
import com.mw.planner.domain.Schedule;
import com.mw.planner.domain.UserDashboardConfig;
import com.mw.planner.dto.BudgetPerformanceSummaryResponse;
import com.mw.planner.dto.BudgetSummary;
import com.mw.planner.dto.CampaignFilterDTO;
import com.mw.planner.dto.CampaignFilterResponseDTO;
import com.mw.planner.dto.CampaignStatistics;
import com.mw.planner.dto.CampaignSummaryRequestDTO;
import com.mw.planner.dto.CustomFeesContext;
import com.mw.planner.dto.DashboardWidgetConfigItem;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.dto.UserResponseDTO;
import com.mw.planner.dto.sales.SalesPerformanceCompanyItemDTO;
import com.mw.planner.dto.sales.SalesPerformanceLocationItemDTO;
import com.mw.planner.dto.sales.SalesPerformanceTeamItemDTO;
import com.mw.planner.dto.sales.TopCampaignCostDTO;
import com.mw.planner.enums.DashboardWidgetKey;
import com.mw.planner.enums.PerformanceSummaryType;
import com.mw.planner.enums.SalesPerformanceShowBy;
import com.mw.planner.exception.campaign.CampaignDateRangeException;
import com.mw.planner.exception.user.UserContextInvalidException;
import com.mw.planner.repository.CampaignRepository;
import com.mw.planner.repository.ScheduleRepository;
import com.mw.planner.repository.UserDashboardConfigRepository;
import com.mw.planner.service.dashboard.DashboardWidgetDefaultsProvider;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final CampaignService campaignService;
  private final UserDashboardConfigRepository userDashboardConfigRepository;
  private final DashboardWidgetDefaultsProvider dashboardWidgetDefaultsProvider;
  private final CampaignRepository campaignRepository;
  private final UserService userService;
  private final CampaignInventorySchedulesService campaignInventorySchedulesService;
  private final InventoryService inventoryService;
  private final ScheduleRepository scheduleRepository;
  private final CustomFeeService customFeeService;
  private final CompanyService companyService;
  private final AgencyService agencyService;
  private final TestModeService testModeService;
  private final com.mw.planner.repository.CreativeAssignmentRepository creativeAssignmentRepository;
  private final com.mw.planner.repository.CreativeRepository creativeRepository;
  private final com.mw.planner.repository.CampaignInventorySchedulesRepository
      campaignInventorySchedulesRepository;

  /**
   * Get campaign statistics for Campaign Overview by Status, optionally filtered by date range.
   * When startDate/endDate are provided, includes campaigns that overlap the range (campaign
   * intersects [startDate, endDate]).
   *
   * @param companyId Company ID to get statistics for
   * @param startDate Optional: filter range start (campaigns overlapping [startDate, endDate])
   * @param endDate Optional: filter range end
   * @return CampaignStatistics containing counts by status
   */
  public CampaignStatistics getCampaignOverviewByStatus(
      String companyId, LocalDate startDate, LocalDate endDate) {
    log.debug(
        "Fetching campaign statistics for dashboard, company ID: {}, startDate: {}, endDate: {}",
        companyId,
        startDate,
        endDate);

    // Validate date range early to avoid expensive DB work for invalid requests.
    if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
      throw new CampaignDateRangeException(startDate, endDate);
    }
    return campaignService.getCampaignStatistics(companyId, startDate, endDate);
  }

  /**
   * Returns dashboard widgets config for the given user.
   *
   * <p>If the user has no saved config (or it's empty), defaults are returned based on company
   * type.
   */
  /**
   * Get budget performance summary for the company and date range. For each date in [startDate,
   * endDate], computes aggregated budget, total cost, remaining and/or reach, impressions based on
   * optional type ("cost", "reach", or both when not provided).
   *
   * @param companyId Company ID (user's company)
   * @param startDate Range start (required)
   * @param endDate Range end (required)
   * @param type Optional: "cost" (cost data only), "reach" (reach/impressions only), or null for
   *     both
   * @param statuses Optional campaign status filter (null/empty = no filter, all statuses)
   * @return Response with dateWiseSchedulePerDateRate (date string -> BudgetSummary)
   */
  public BudgetPerformanceSummaryResponse getPerformanceSummary(
      String companyId,
      String userCurrencyCode,
      LocalDate startDate,
      LocalDate endDate,
      PerformanceSummaryType type,
      List<Campaign.Status> statuses) {
    if (startDate == null || endDate == null) {
      throw new CampaignDateRangeException(startDate, endDate);
    }
    if (startDate.isAfter(endDate)) {
      throw new CampaignDateRangeException(startDate, endDate);
    }

    boolean includeCostTotals = PerformanceSummaryType.COST == type;

    List<Campaign> currentCampaigns =
        campaignService.getCampaignsByCompanyOverlappingDateRange(
            companyId, startDate, endDate, statuses);

    LastPeriodRange lastPeriod =
        includeCostTotals ? computeLastPeriodRange(startDate, endDate) : null;
    List<Campaign> lastPeriodCampaigns = null;
    if (includeCostTotals && lastPeriod != null) {
      lastPeriodCampaigns =
          campaignService.getCampaignsByCompanyOverlappingDateRange(
              companyId, lastPeriod.start(), lastPeriod.end(), statuses);
    }

    if (includeCostTotals) {
      List<Campaign> campaignsForCostComputation =
          mergeCampaignsById(currentCampaigns, lastPeriodCampaigns);
      Map<String, List<CampaignInventorySchedules>> campaignToCisMap =
          buildCampaignToCisMap(campaignsForCostComputation);

      Map<String, BudgetSummary> combinedDateWiseCost =
          getBudgetSummaryByDate(
              lastPeriod.start(),
              endDate,
              campaignsForCostComputation,
              campaignToCisMap,
              companyId,
              PerformanceSummaryType.COST);

      Map<String, BudgetSummary> currentDateWise =
          filterDateWiseSummaryByRange(combinedDateWiseCost, startDate, endDate);
      Map<String, BudgetSummary> lastPeriodDateWise =
          filterDateWiseSummaryByRange(combinedDateWiseCost, lastPeriod.start(), lastPeriod.end());

      CostTotals currentTotals = sumCostSummaryFromDateWise(currentDateWise);
      CostTotals lastTotals = sumCostSummaryFromDateWise(lastPeriodDateWise);

      return BudgetPerformanceSummaryResponse.builder()
          .dateWiseSchedulePerDateRate(currentDateWise)
          .currencyCode(userCurrencyCode)
          .totalBudget(currentTotals.totalBudget())
          .lastPeriodTotalBudget(lastTotals.totalBudget())
          .lastPeriodTotalCost(lastTotals.totalCost())
          .totalCost(currentTotals.totalCost())
          .remainingBudget(currentTotals.remainingBudget())
          .totalRevenue(currentTotals.totalRevenue())
          .lastPeriodTotalRevenue(lastTotals.totalRevenue())
          .averageRevenuePerUnit(
              calculateAverageRevenuePerCampaign(currentTotals.totalRevenue(), currentCampaigns))
          .lastPeriodAverageRevenuePerUnit(
              calculateAverageRevenuePerCampaign(lastTotals.totalRevenue(), lastPeriodCampaigns))
          .conversionRate(calculateConversionRate(currentCampaigns))
          .lastPeriodConversionRate(calculateConversionRate(lastPeriodCampaigns))
          .build();
    }

    Map<String, List<CampaignInventorySchedules>> campaignToCisMap =
        buildCampaignToCisMap(currentCampaigns);
    Map<String, BudgetSummary> dateWiseSchedulePerDateRate =
        getBudgetSummaryByDate(
            startDate, endDate, currentCampaigns, campaignToCisMap, companyId, type);

    return BudgetPerformanceSummaryResponse.builder()
        .dateWiseSchedulePerDateRate(dateWiseSchedulePerDateRate)
        .currencyCode(userCurrencyCode)
        .build();
  }

  /**
   * Computes the previous period date range: same length as [startDate, endDate] (inclusive),
   * ending one day before startDate.
   */
  private static LastPeriodRange computeLastPeriodRange(LocalDate startDate, LocalDate endDate) {
    long durationDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
    LocalDate lastPeriodStart = startDate.minusDays(durationDays);
    LocalDate lastPeriodEnd = startDate.minusDays(1);
    return new LastPeriodRange(lastPeriodStart, lastPeriodEnd);
  }

  /**
   * Sums budget, cost, and remaining from a date-wise budget summary map. Uses 0 for null values.
   */
  private static CostTotals sumCostSummaryFromDateWise(Map<String, BudgetSummary> dateWise) {
    if (dateWise == null || dateWise.isEmpty()) {
      return new CostTotals(0.0, 0.0, 0.0);
    }
    double totalBudget = 0;
    double totalCost = 0;
    double remaining = 0;
    for (BudgetSummary s : dateWise.values()) {
      if (s != null) {
        totalBudget += Optional.ofNullable(s.getBudget()).orElse(0.0);
        totalCost += Optional.ofNullable(s.getCost()).orElse(0.0);
        remaining += Optional.ofNullable(s.getRemaining()).orElse(0.0);
      }
    }
    return new CostTotals(totalBudget, totalCost, remaining);
  }

  /** Filters date-wise summary by inclusive date range using yyyy-MM-dd lexicographic ordering. */
  private static Map<String, BudgetSummary> filterDateWiseSummaryByRange(
      Map<String, BudgetSummary> dateWise, LocalDate startDate, LocalDate endDate) {
    if (dateWise == null || dateWise.isEmpty()) {
      return Map.of();
    }
    String start = startDate.toString();
    String end = endDate.toString();

    Map<String, BudgetSummary> filtered = new LinkedHashMap<>();
    for (var entry : dateWise.entrySet()) {
      String date = entry.getKey();
      if (date.compareTo(start) >= 0 && date.compareTo(end) <= 0) {
        filtered.put(date, entry.getValue());
      }
    }
    return filtered;
  }

  /**
   * Merges two campaign lists by ID; keeps first occurrence. Used to build a single CIS map for
   * current and last period without duplicate campaign fetches.
   */
  private static List<Campaign> mergeCampaignsById(List<Campaign> first, List<Campaign> second) {
    Map<String, Campaign> byId = new LinkedHashMap<>();
    for (Campaign c : Optional.ofNullable(first).orElse(List.of())) {
      if (c != null && c.getId() != null) {
        byId.putIfAbsent(c.getId(), c);
      }
    }
    for (Campaign c : Optional.ofNullable(second).orElse(List.of())) {
      if (c != null && c.getId() != null) {
        byId.putIfAbsent(c.getId(), c);
      }
    }
    return new ArrayList<>(byId.values());
  }

  /** Average revenue per campaign for the given campaign set (0 when no campaigns). */
  private static double calculateAverageRevenuePerCampaign(
      double totalRevenue, List<Campaign> campaigns) {
    long campaignCount =
        Optional.ofNullable(campaigns).orElse(List.of()).stream()
            .filter(c -> c != null && c.getId() != null)
            .map(Campaign::getId)
            .distinct()
            .count();
    return campaignCount == 0 ? 0.0 : (totalRevenue / campaignCount);
  }

  /** Conversion rate = (completed + approved + active campaigns) / total campaigns * 100. */
  private static double calculateConversionRate(List<Campaign> campaigns) {
    if (campaigns == null || campaigns.isEmpty()) {
      return 0.0;
    }
    long totalCampaigns =
        campaigns.stream()
            .filter(c -> c != null && c.getId() != null)
            .map(Campaign::getId)
            .distinct()
            .count();
    if (totalCampaigns == 0) {
      return 0.0;
    }

    long convertedCampaigns =
        campaigns.stream()
            .filter(c -> c != null && c.getId() != null)
            .collect(Collectors.toMap(Campaign::getId, Function.identity(), (a, b) -> a))
            .values()
            .stream()
            .filter(c -> isConvertedStatus(c.getStatus()))
            .count();

    return (convertedCampaigns * 100.0) / totalCampaigns;
  }

  private static boolean isConvertedStatus(Campaign.Status status) {
    return status == Campaign.Status.COMPLETED
        || status == Campaign.Status.APPROVED
        || status == Campaign.Status.ACTIVE;
  }

  private record LastPeriodRange(LocalDate start, LocalDate end) {}

  private record CostTotals(double totalBudget, double totalCost, double remainingBudget) {
    double totalRevenue() {
      return totalCost;
    }
  }

  /**
   * Builds a map from campaign ID to the list of {@link CampaignInventorySchedules} for those
   * campaigns. Fetches all CIS records for the given campaign IDs in one call, then groups them by
   * {@link CampaignInventorySchedules#getCampaignId}. Returns an empty map if campaigns are null,
   * empty, or have no CIS data.
   *
   * @param campaigns list of campaigns whose CIS entries are to be loaded
   * @return map of campaign ID to list of CampaignInventorySchedules (never null)
   */
  private Map<String, List<CampaignInventorySchedules>> buildCampaignToCisMap(
      List<Campaign> campaigns) {

    if (campaigns == null || campaigns.isEmpty()) {
      return Map.of();
    }

    List<String> campaignIds =
        campaigns.stream().map(Campaign::getId).filter(Objects::nonNull).toList();

    if (campaignIds.isEmpty()) {
      return Map.of();
    }

    List<CampaignInventorySchedules> allCis =
        campaignInventorySchedulesService.findByCampaignIds(campaignIds);

    if (allCis == null || allCis.isEmpty()) {
      return Map.of();
    }

    return allCis.stream()
        .collect(Collectors.groupingBy(CampaignInventorySchedules::getCampaignId));
  }

  /**
   * Computes per-date budget summary (budget, totalCost, remaining and/or reach, impressions) for
   * each date in the range. Single-pass aggregation; only computes cost when type is "cost" or
   * null, and reach/impressions when type is "reach" or null.
   *
   * @param startDate Range start (inclusive)
   * @param endDate Range end (inclusive)
   * @param campaigns Campaigns to consider
   * @param campaignToCisMap campaignId -> list of CampaignInventorySchedules
   * @param userCompanyId User's company ID for price calculation
   * @param type "cost", "reach", or null for both
   * @return Map from date string (yyyy-MM-dd) to BudgetSummary
   */
  Map<String, BudgetSummary> getBudgetSummaryByDate(
      LocalDate startDate,
      LocalDate endDate,
      List<Campaign> campaigns,
      Map<String, List<CampaignInventorySchedules>> campaignToCisMap,
      String userCompanyId,
      PerformanceSummaryType type) {

    if (campaigns == null
        || campaigns.isEmpty()
        || campaignToCisMap == null
        || campaignToCisMap.isEmpty()) {
      return Map.of();
    }

    boolean includeCost = type == null || PerformanceSummaryType.COST == type;
    boolean includeReach = type == null || PerformanceSummaryType.REACH == type;

    // -------- Build Campaign Map --------
    Map<String, Campaign> campaignMap =
        campaigns.stream()
            .filter(c -> c != null && c.getId() != null)
            .collect(Collectors.toMap(Campaign::getId, Function.identity(), (a, b) -> a));

    // -------- fees Context Map --------
    Map<String, CustomFeesContext> feesContextMap =
        includeCost ? customFeeService.getActiveCustomFeesContextForCampaigns(campaigns) : null;

    if (campaignMap.isEmpty()) {
      return Map.of();
    }

    // -------- Collect Schedule IDs and Inventory IDs (for cost path) --------
    Set<String> scheduleIds = new HashSet<>();
    Set<String> inventoryIds = new HashSet<>();

    for (var cisList : campaignToCisMap.values()) {
      for (CampaignInventorySchedules cis : cisList) {
        if (cis == null) continue;
        if (cis.getScheduleIds() != null) {
          scheduleIds.addAll(cis.getScheduleIds());
        }
        if (includeCost && cis.getInventoryId() != null) {
          inventoryIds.add(cis.getInventoryId());
        }
      }
    }

    if (scheduleIds.isEmpty()) {
      return Map.of();
    }

    // -------- Load Schedules (single DB usage; shared for cost and reach) --------
    Map<String, Schedule> scheduleMap = new HashMap<>(scheduleIds.size());
    for (Schedule s : scheduleRepository.findAllById(scheduleIds)) {
      if (s != null && s.getId() != null) {
        scheduleMap.put(s.getId(), s);
      }
    }

    Map<String, Inventory> inventoryMap = null;
    Map<String, Double> proposedPriceMap = null;
    if (includeCost) {
      inventoryMap = new HashMap<>(inventoryIds.size());
      if (!inventoryIds.isEmpty()) {
        for (Inventory inv : inventoryService.findAllByIds(new ArrayList<>(inventoryIds))) {
          if (inv != null && inv.getId() != null) {
            inventoryMap.put(inv.getId(), inv);
          }
        }
      }
      proposedPriceMap =
          preloadProposedPrices(
              campaignToCisMap,
              campaignMap,
              scheduleMap,
              inventoryMap,
              feesContextMap,
              userCompanyId);
    }

    Map<String, DailyAggregate> dateAggregateMap = new HashMap<>();
    // A campaign's daily budget share (campaign.getBudget() / campaignDurationDays) is a
    // campaign-level quantity, not a per-schedule one — credit it to a given (campaign, date) at
    // most once, even though the loop below visits every schedule/inventory booked that day.
    // Without this, a campaign with N inventories booked on the same date would have its budget
    // share counted N times for that date. Actual cost (perDateRate) is genuinely per-schedule and
    // correctly keeps summing across every schedule.
    Set<String> creditedCampaignBudgetDates = new HashSet<>();

    for (var campaignEntry : campaignToCisMap.entrySet()) {
      Campaign campaign = campaignMap.get(campaignEntry.getKey());
      if (campaign == null) continue;

      long campaignDurationDays =
          includeCost
              ? ChronoUnit.DAYS.between(campaign.getStartDate(), campaign.getEndDate()) + 1
              : 0;

      for (CampaignInventorySchedules cis : campaignEntry.getValue()) {
        Inventory inventory = includeCost ? inventoryMap.get(cis.getInventoryId()) : null;
        if (includeCost && inventory == null) continue;

        List<String> scheduleIdList = cis.getScheduleIds();
        if (scheduleIdList == null || scheduleIdList.isEmpty()) continue;

        for (String scheduleId : scheduleIdList) {
          Schedule schedule = scheduleMap.get(scheduleId);
          if (schedule == null) continue;

          Map<String, List<Integer>> bookingMatrix = schedule.getBookingMatrix();
          if (bookingMatrix == null || bookingMatrix.isEmpty()) continue;

          // Per-schedule reach/impressions (computed once per schedule, reused for each date)
          double perHourReach = 0;
          double perHourImpressions = 0;
          if (includeReach) {
            double plannedSot =
                Optional.ofNullable(schedule.getPlannedSot()).filter(s -> s > 0).orElse(1.0);
            perHourReach =
                (schedule.getReach() != null ? schedule.getReach().doubleValue() : 0) / plannedSot;
            perHourImpressions =
                (schedule.getImpressions() != null ? schedule.getImpressions().doubleValue() : 0)
                    / plannedSot;
          }

          Double proposedPrice = null;
          long adPlays = 1L;
          long spotsPerHour = 0L;
          if (includeCost) {
            String priceKey = campaign.getId() + "|" + scheduleId + "|" + inventory.getId();
            proposedPrice = proposedPriceMap.get(priceKey);
            if (proposedPrice == null) continue;
            adPlays = Optional.ofNullable(schedule.getAdPlays()).filter(v -> v > 0).orElse(1L);
            spotsPerHour = Optional.ofNullable(schedule.getSpotsPerHour()).orElse(0L);
          }

          double spotRate = includeCost ? (proposedPrice / adPlays) : 0;

          for (var bmEntry : bookingMatrix.entrySet()) {
            String dateStr = bmEntry.getKey();
            if (dateStr.compareTo(startDate.toString()) < 0
                || dateStr.compareTo(endDate.toString()) > 0) {
              continue;
            }

            int hoursCount = bmEntry.getValue().size();
            if (hoursCount == 0) continue;

            DailyAggregate agg =
                dateAggregateMap.computeIfAbsent(dateStr, k -> new DailyAggregate());

            if (includeCost) {
              double perDateRate = spotRate * hoursCount * spotsPerHour;
              double perDateBudget;
              if (campaign.getBudget() != null && campaignDurationDays > 0) {
                boolean firstScheduleForThisCampaignDate =
                    creditedCampaignBudgetDates.add(campaign.getId() + "|" + dateStr);
                perDateBudget =
                    firstScheduleForThisCampaignDate
                        ? (campaign.getBudget() / campaignDurationDays)
                        : 0.0;
              } else {
                // No campaign-level budget to fall back on — perDateRate is the best per-schedule
                // proxy, and summing it per schedule is appropriate here (unlike the branch above).
                perDateBudget = perDateRate;
              }
              agg.addCost(perDateBudget, perDateRate);
            }
            if (includeReach) {
              agg.addReach(perHourReach * hoursCount, perHourImpressions * hoursCount);
            }
          }
        }
      }
    }

    // -------- Build result from aggregates --------
    Map<String, BudgetSummary> result = new LinkedHashMap<>();
    for (var entry : dateAggregateMap.entrySet()) {
      DailyAggregate agg = entry.getValue();
      BudgetSummary.BudgetSummaryBuilder b = BudgetSummary.builder();
      if (includeCost) {
        b.budget(agg.sumPerDateBudget)
            .cost(agg.sumPerDateRate)
            .remaining(Math.max(0, agg.sumPerDateBudget - agg.sumPerDateRate));
      }
      if (includeReach) {
        b.reach(agg.sumPerDateReach).impressions(agg.sumPerDateImpressions);
      }
      result.put(entry.getKey(), b.build());
    }
    return result;
  }

  /**
   * Preloads proposed prices for all (campaign, schedule, inventory) combinations used in the given
   * campaign-to-CIS mapping. Each key is computed once and cached so that per-date cost aggregation
   * can look up the price without recalculating. Keys use the format {@code
   * campaignId|scheduleId|inventoryId}.
   *
   * @param campaignToCisMap campaign ID to list of CampaignInventorySchedules
   * @param campaignMap campaign ID to Campaign entity
   * @param scheduleMap schedule ID to Schedule entity
   * @param inventoryMap inventory ID to Inventory entity
   * @param feesContextMap campaign ID to CustomFeesContext (for fee-aware pricing)
   * @param userCompanyId company ID used for price calculation
   * @return map of composite key to proposed price (Double)
   */
  private Map<String, Double> preloadProposedPrices(
      Map<String, List<CampaignInventorySchedules>> campaignToCisMap,
      Map<String, Campaign> campaignMap,
      Map<String, Schedule> scheduleMap,
      Map<String, Inventory> inventoryMap,
      Map<String, CustomFeesContext> feesContextMap,
      String userCompanyId) {

    Map<String, Double> priceMap = new HashMap<>();

    for (var entry : campaignToCisMap.entrySet()) {

      Campaign campaign = campaignMap.get(entry.getKey());
      if (campaign == null) continue;

      CustomFeesContext feesContext = feesContextMap.get(campaign.getId());

      for (CampaignInventorySchedules cis : entry.getValue()) {

        Inventory inventory = inventoryMap.get(cis.getInventoryId());
        if (inventory == null) continue;

        List<String> scheduleIds = cis.getScheduleIds();
        if (scheduleIds == null) continue;

        for (String scheduleId : scheduleIds) {

          Schedule schedule = scheduleMap.get(scheduleId);
          if (schedule == null) continue;

          String key = campaign.getId() + "|" + scheduleId + "|" + inventory.getId();

          priceMap.computeIfAbsent(
              key,
              k ->
                  campaignInventorySchedulesService.calculateProposedPriceForSchedule(
                      schedule, inventory, campaign, userCompanyId, feesContext));
        }
      }
    }

    return priceMap;
  }

  // private record DailyAggregate(double sumPerDateBudget, double sumPerDateRate) {}

  public List<DashboardWidgetConfigItem> getAvailableWidgets(IamUserContext userContext) {
    // Enforce required user keys (userId + companyId) and keep a consistent lookup.
    UserKey key = UserKey.from(userContext);
    return userDashboardConfigRepository
        .findByUserIdAndCompanyId(key.userId(), key.companyId())
        .map(UserDashboardConfig::getWidgets)
        .filter(widgets -> !CollectionUtils.isEmpty(widgets))
        .map(DashboardService::toDtoList)
        // Fallback to defaults if nothing has been configured yet.
        .orElseGet(() -> dashboardWidgetDefaultsProvider.defaultsFor(userContext));
  }

  /**
   * Updates widget config for the logged-in user (userId + companyId). Uses an atomic upsert so
   * this is a single database call.
   */
  public List<DashboardWidgetConfigItem> upsertWidgets(
      IamUserContext userContext, List<DashboardWidgetConfigItem> requestedWidgets) {
    // Normalize input (remove nulls, de-dupe keys, keep stable order) before persisting.
    List<DashboardWidgetConfigItem> normalized = normalize(requestedWidgets);
    UserKey key = UserKey.from(userContext);

    try {
      UserDashboardConfig cfg =
          userDashboardConfigRepository
              .findByUserIdAndCompanyId(key.userId(), key.companyId())
              .map(
                  existing -> {
                    existing.setWidgets(toDomainList(normalized));
                    return existing;
                  })
              .orElseGet(
                  () ->
                      UserDashboardConfig.builder()
                          .userId(key.userId())
                          .companyId(key.companyId())
                          .widgets(toDomainList(normalized))
                          .build());

      userDashboardConfigRepository.save(cfg);
      return normalized;
    } catch (DataIntegrityViolationException ex) {
      // If two requests raced to create the same (userId, companyId), the unique index can throw.
      // Retry by fetching then updating.
      UserDashboardConfig existing =
          userDashboardConfigRepository
              .findByUserIdAndCompanyId(key.userId(), key.companyId())
              .orElseThrow(() -> ex);
      existing.setWidgets(toDomainList(normalized));
      userDashboardConfigRepository.save(existing);
      return normalized;
    }
  }

  /**
   * Normalizes widget list input.
   *
   * <p>Rules: ignore null items / null keys, de-duplicate by key (last wins), preserve predictable
   * ordering.
   */
  private static List<DashboardWidgetConfigItem> normalize(
      List<DashboardWidgetConfigItem> requestedWidgets) {
    if (requestedWidgets == null) {
      return List.of();
    }

    // De-duplicate by key (last one wins), while keeping a predictable order.
    Map<DashboardWidgetKey, DashboardWidgetConfigItem> byKey = new LinkedHashMap<>();
    for (DashboardWidgetConfigItem item : requestedWidgets) {
      if (item == null || item.getKey() == null) {
        continue;
      }
      byKey.remove(item.getKey());
      byKey.put(item.getKey(), item);
    }
    return new ArrayList<>(byKey.values());
  }

  /** Maps stored widget domain objects to API DTOs (null-safe). */
  private static List<DashboardWidgetConfigItem> toDtoList(
      List<UserDashboardConfig.DashboardWidgetConfig> widgets) {
    if (widgets == null) {
      return List.of();
    }
    return widgets.stream()
        .filter(Objects::nonNull)
        .map(
            w ->
                DashboardWidgetConfigItem.builder()
                    .key(w.getKey())
                    .isEnable(w.getIsEnable())
                    .build())
        .toList();
  }

  /** Maps API DTOs to stored widget domain objects (null-safe). */
  private static List<UserDashboardConfig.DashboardWidgetConfig> toDomainList(
      List<DashboardWidgetConfigItem> widgets) {
    if (widgets == null) {
      return List.of();
    }
    return widgets.stream()
        .filter(Objects::nonNull)
        .map(
            w ->
                UserDashboardConfig.DashboardWidgetConfig.builder()
                    .key(w.getKey())
                    .isEnable(w.getIsEnable())
                    .build())
        .toList();
  }

  /**
   * Compact identity key for dashboard config lookups.
   *
   * <p>Centralizes validation so all widget-related methods enforce the same requirements.
   */
  record UserKey(String userId, String companyId) {
    /** Builds a validated key from the current user context. */
    static UserKey from(IamUserContext ctx) {
      if (ctx == null) {
        throw new UserContextInvalidException("userId, companyId");
      }
      boolean missingUserId = ctx.getUserId() == null || ctx.getUserId().isBlank();
      boolean missingCompanyId = ctx.getCompanyId() == null || ctx.getCompanyId().isBlank();
      if (missingUserId || missingCompanyId) {
        String missing =
            (missingUserId && missingCompanyId)
                ? "userId, companyId"
                : (missingUserId ? "userId" : "companyId");
        throw new UserContextInvalidException(missing);
      }
      return new UserKey(ctx.getUserId(), ctx.getCompanyId());
    }
  }

  /**
   * Sales Performance Summary.
   *
   * <p>- Returns {@code Page<?>} of grouped items.
   *
   * <p>Performance: uses batched loads for CampaignInventorySchedules, Inventory, Schedule, and
   * CustomFee context (no per-campaign DB fanout). Sorting is applied in-memory once; no extra DB
   * calls.
   */
  public Object getSalesPerformanceSummary(
      LocalDate startDate,
      LocalDate endDate,
      SalesPerformanceShowBy showBy,
      String sortBy,
      String sortDir,
      int page,
      int size) {
    // Sales summary always requires a concrete date range.
    validateDates(startDate, endDate);

    // Default showBy to country (controller accepts string and may pass null).
    SalesPerformanceShowBy effectiveShowBy =
        showBy != null ? showBy : SalesPerformanceShowBy.COUNTRY;
    // Clamp paging to keep API predictable and to prevent very large payloads.
    int validPage = Math.max(0, page);
    int validSize = Math.max(1, Math.min(500, size));
    Pageable pageable = PageRequest.of(validPage, validSize);

    // Read user context once (company + supplier-side flag affect filtering and revenue logic).
    IamUserContext userContext = userService.getIamUserContext();
    String userCompanyId = userContext.getCompanyId();
    boolean isSupplierSide = Boolean.TRUE.equals(userContext.getIsSupplierSide());

    // Load campaigns visible to this company that overlap the requested range.
    List<Campaign> campaigns =
        campaignRepository.findCampaignsOverlappingRange(
            userCompanyId, startDate, endDate, testModeService.getEffectiveDataMode());
    if (campaigns == null || campaigns.isEmpty()) {
      return Page.empty(pageable);
    }

    // Index campaigns for O(1) lookups while iterating schedules.
    Map<String, Campaign> campaignById =
        campaigns.stream()
            .filter(c -> c != null && c.getId() != null)
            .collect(Collectors.toMap(Campaign::getId, Function.identity(), (a, b) -> a));

    List<String> campaignIds = new ArrayList<>(campaignById.keySet());

    // Load all CampaignInventorySchedules once.
    List<CampaignInventorySchedules> allInventorySchedules =
        campaignInventorySchedulesService.findByCampaignIds(campaignIds);
    if (allInventorySchedules == null) {
      allInventorySchedules = List.of();
    }

    // Filter schedules for media owners: only keep schedules owned by the logged-in company.
    List<CampaignInventorySchedules> relevantInventorySchedules =
        allInventorySchedules.stream()
            .filter(Objects::nonNull)
            .filter(
                s -> {
                  Campaign c = campaignById.get(s.getCampaignId());
                  if (c == null) return false;
                  // Creator sees all schedules; media owner sees only its own schedules.
                  boolean isCreator = Objects.equals(c.getCompanyId(), userCompanyId);
                  return isCreator || Objects.equals(s.getMediaOwnerId(), userCompanyId);
                })
            .toList();

    if (relevantInventorySchedules.isEmpty()) {
      // If nothing is relevant after access filtering, behave like an empty result set.
      return Page.empty(pageable);
    }

    // Bulk load inventories (single call).
    List<String> inventoryIds =
        relevantInventorySchedules.stream()
            .map(CampaignInventorySchedules::getInventoryId)
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .toList();

    Map<String, Inventory> inventoryById = new HashMap<>();
    if (!inventoryIds.isEmpty()) {
      for (Inventory inv : inventoryService.findAllByIds(inventoryIds)) {
        if (inv != null && inv.getId() != null) {
          inventoryById.put(inv.getId(), inv);
        }
      }
    }

    // Bulk load schedules (single call).
    List<String> scheduleIds =
        relevantInventorySchedules.stream()
            .filter(s -> s.getScheduleIds() != null && !s.getScheduleIds().isEmpty())
            .flatMap(s -> s.getScheduleIds().stream())
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .toList();

    Map<String, Schedule> scheduleById = new HashMap<>();
    if (!scheduleIds.isEmpty()) {
      for (Schedule s : scheduleRepository.findAllById(scheduleIds)) {
        if (s != null && s.getId() != null) {
          scheduleById.put(s.getId(), s);
        }
      }
    }

    // Bulk load custom fee contexts (2 repository calls total).
    Map<String, CustomFeesContext> customFeesByCampaignId =
        customFeeService.getActiveCustomFeesContextForCampaigns(campaigns);

    // Precompute per-campaign totals (full schedule period) for scaling fees into date-range
    // values.
    Map<String, CampaignScale> scaleByCampaignId =
        computeCampaignScales(
            campaignById,
            relevantInventorySchedules,
            inventoryById,
            customFeesByCampaignId,
            scheduleById,
            userCompanyId);

    // Aggregate all requested metrics in a single pass across schedules.
    RangeAggregation agg =
        aggregateInRange(
            startDate,
            endDate,
            effectiveShowBy,
            campaignById,
            relevantInventorySchedules,
            inventoryById,
            scheduleById,
            scaleByCampaignId,
            isSupplierSide);

    // Build grouped items depending on the requested dimension.
    List<?> items =
        switch (effectiveShowBy) {
          case COUNTRY -> buildCountryItems(startDate, endDate, agg.country, inventoryById);
          case CITY -> buildCityItems(startDate, endDate, agg.city);
          case ADVERTISER -> buildAdvertiserItems(agg, campaignById);
          case AGENCY -> buildAgencyItems(agg, campaignById);
          case TEAM -> buildTeamItems(agg);
          default -> List.of();
        };

    // Single in-memory sort by requested field (no extra DB calls).
    List<?> sorted = sortSalesPerformanceItems(items, sortBy, sortDir);
    // Slice sorted list into a Spring Page to match other list endpoints.
    return toPage(sorted, pageable);
  }

  // ======= Sales summary implementation (private helpers) =======

  private record CampaignScale(double baseTotalFull, double proposedTotalFull, double scale) {}

  private record RangeAggregation(
      Map<LocalDate, Double> dayCost,
      Map<LocalDate, Double> dayRevenue,
      Map<String, GroupAgg> country,
      Map<String, GroupAgg> city,
      Map<String, CampaignRangeMetrics> campaign,
      Map<String, Set<String>> advertiserToCampaignIds,
      Map<String, Set<String>> agencyToCampaignIds,
      Map<String, Set<String>> userToCampaignIds) {}

  private record GroupAgg(
      Set<String> inventoryIds,
      Set<String> campaignIds,
      double cost,
      double revenue,
      long bookedHours,
      double adPlays,
      double impressions,
      double plannedSot,
      double totalSot) {}

  private static class CampaignRangeMetrics {
    double cost;
    double revenue;
    long bookedHours;
    double adPlays;
    double impressions;
    double reach;
    double plannedSot;
    double totalSot;
    // Running plannedSot-weighted SOV accumulators — see calculateInventorySov/calculateWeightedSov
    // in CampaignInventorySchedulesService. Final SOV = sovWeightedSum / sovWeight.
    double sovWeightedSum;
    double sovWeight;
  }

  /**
   * Computes a campaign-level scale factor so base-price shares can be converted into proposed
   * (discounted/custom-fee) cost shares.
   *
   * <p>Scale = proposedTotalFull / baseTotalFull (over full schedule period for the campaign).
   */
  private Map<String, CampaignScale> computeCampaignScales(
      Map<String, Campaign> campaignById,
      List<CampaignInventorySchedules> relevantInventorySchedules,
      Map<String, Inventory> inventoryById,
      Map<String, CustomFeesContext> customFeesByCampaignId,
      Map<String, Schedule> scheduleById,
      String userCompanyId) {

    Map<String, List<CampaignInventorySchedules>> byCampaign =
        relevantInventorySchedules.stream()
            .collect(Collectors.groupingBy(CampaignInventorySchedules::getCampaignId));

    Map<String, CampaignScale> result = new HashMap<>();

    for (Map.Entry<String, Campaign> entry : campaignById.entrySet()) {
      String campaignId = entry.getKey();
      Campaign campaign = entry.getValue();
      List<CampaignInventorySchedules> inventorySchedules =
          byCampaign.getOrDefault(campaignId, List.of());

      double baseTotal = 0.0;
      double proposedTotal = 0.0;

      // Use an empty fee context if none exists so the pricing method stays null-safe.
      CustomFeesContext ctx =
          customFeesByCampaignId.getOrDefault(campaignId, CustomFeesContext.builder().build());

      for (CampaignInventorySchedules cis : inventorySchedules) {
        if (cis == null) continue;
        Inventory inv = inventoryById.get(cis.getInventoryId());
        if (inv == null) continue;

        // Base total is computed from schedule base prices (used as denominator for scaling).
        if (cis.getScheduleIds() != null) {
          for (String sid : cis.getScheduleIds()) {
            Schedule s = scheduleById.get(sid);
            if (s != null && s.getBasePrice() != null) {
              baseTotal += s.getBasePrice();
            }
          }
        }

        // Proposed total includes discounts/custom fees and is used as numerator for scaling.
        Double proposed =
            campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
                cis, inv, campaign, userCompanyId, ctx, scheduleById);
        proposedTotal += proposed != null ? proposed : 0.0;
      }

      // If base is 0, scale is 0 and the campaign won't contribute to aggregates.
      double scale = baseTotal > 0.0 ? proposedTotal / baseTotal : 0.0;
      result.put(campaignId, new CampaignScale(baseTotal, proposedTotal, scale));
    }

    return Map.copyOf(result);
  }

  /**
   * Aggregates costs/revenue and KPI metrics within [startDate, endDate] (inclusive).
   *
   * <p>Also builds grouping maps (country/city/advertiser/agency/team) so downstream builders can
   * create DTOs without another full pass.
   */
  private RangeAggregation aggregateInRange(
      LocalDate startDate,
      LocalDate endDate,
      SalesPerformanceShowBy showBy,
      Map<String, Campaign> campaignById,
      List<CampaignInventorySchedules> relevantInventorySchedules,
      Map<String, Inventory> inventoryById,
      Map<String, Schedule> scheduleById,
      Map<String, CampaignScale> scaleByCampaignId,
      boolean isSupplierSide) {

    String startKey = startDate != null ? startDate.toString() : null;
    String endKey = endDate != null ? endDate.toString() : null;

    // Day-wise totals for overview charting.
    Map<LocalDate, Double> dayCost = new HashMap<>();
    Map<LocalDate, Double> dayRevenue = new HashMap<>();
    // Per-campaign metrics are used by advertiser/agency/team rollups.
    Map<String, CampaignRangeMetrics> campaignMetrics = new HashMap<>();

    Map<String, TmpGroup> country = new HashMap<>();
    Map<String, TmpGroup> city = new HashMap<>();

    Map<String, Set<String>> advertiserToCampaignIds = new HashMap<>();
    Map<String, Set<String>> agencyToCampaignIds = new HashMap<>();
    Map<String, Set<String>> userToCampaignIds = new HashMap<>();

    for (CampaignInventorySchedules cis : relevantInventorySchedules) {
      if (cis == null || cis.getCampaignId() == null) continue;

      Campaign campaign = campaignById.get(cis.getCampaignId());
      if (campaign == null) continue;

      CampaignScale scale = scaleByCampaignId.get(cis.getCampaignId());
      if (scale == null || scale.baseTotalFull() <= 0.0 || scale.scale() <= 0.0) {
        // Skip campaigns with no meaningful scale (prevents divide-by-zero and noise).
        continue;
      }

      Inventory inv = inventoryById.get(cis.getInventoryId());
      if (inv == null) continue;

      String countryKey = safe(inv.getLocation() != null ? inv.getLocation().getCountry() : null);
      String cityKey = safe(inv.getLocation() != null ? inv.getLocation().getCity() : null);
      String cityCompositeKey = cityKey + "||" + countryKey;

      if (cis.getScheduleIds() == null || cis.getScheduleIds().isEmpty()) continue;

      for (String sid : cis.getScheduleIds()) {
        Schedule schedule = scheduleById.get(sid);
        if (schedule == null
            || schedule.getBasePrice() == null
            || schedule.getBasePrice() <= 0.0
            || schedule.getBookingMatrix() == null
            || schedule.getBookingMatrix().isEmpty()) {
          // Ignore invalid schedules to avoid partial/incorrect aggregation.
          continue;
        }

        long totalHoursAll = totalHours(schedule.getBookingMatrix());
        if (totalHoursAll <= 0L) {
          // No booked hours => nothing to allocate across days.
          continue;
        }

        for (Map.Entry<String, List<Integer>> e : schedule.getBookingMatrix().entrySet()) {
          String day = e.getKey();
          if (day == null) continue;
          // Range filter (lexicographic works for ISO yyyy-MM-dd).
          if (startKey != null && day.compareTo(startKey) < 0) continue;
          if (endKey != null && day.compareTo(endKey) > 0) continue;

          int hours = e.getValue() != null ? e.getValue().size() : 0;
          if (hours <= 0) continue;

          // Allocate schedule base price across booked hours, then scale into proposed cost.
          double share = (double) hours / (double) totalHoursAll;
          double baseShare = schedule.getBasePrice() * share;
          double scaledCostShare = baseShare * scale.scale();
          // Supplier-side revenue is base share; demand-side revenue matches scaled cost share.
          double revenueShare = isSupplierSide ? baseShare : scaledCostShare;

          LocalDate date = LocalDate.parse(day);
          dayCost.merge(date, scaledCostShare, Double::sum);
          dayRevenue.merge(date, revenueShare, Double::sum);

          // Keep per-campaign totals so other groupings can be derived cheaply.
          CampaignRangeMetrics cm =
              campaignMetrics.computeIfAbsent(campaign.getId(), k -> new CampaignRangeMetrics());
          cm.cost += scaledCostShare;
          cm.revenue += revenueShare;
          cm.bookedHours += hours;
          cm.adPlays +=
              weightedLong(schedule.getAdPlays(), schedule.getSpotsPerHour(), hours, share);
          cm.impressions += weightedLong(schedule.getImpressions(), null, 0, share);
          cm.reach += weightedLong(schedule.getReach(), null, 0, share);
          cm.plannedSot += weightedDouble(schedule.getPlannedSot(), share);
          cm.totalSot += weightedDouble(schedule.getTotalSot(), share);

          // Classification-aware SOV for this schedule, weighted by its planned airtime within
          // the selected date range — summed and divided in buildCompanyItems so a company's
          // rollup blends classic (always 100%) and digital (booked-spot share) proportionally.
          double scheduleSov =
              CampaignInventorySchedulesService.calculateInventorySov(
                  inv.getClassification(),
                  schedule.getSpotsPerLoop(),
                  CampaignInventorySchedulesService.getInventoryMaxSpotsPerLoop(inv),
                  schedule.getTotalSot(),
                  schedule.getPlannedSot());
          double scheduleSovWeight = weightedDouble(schedule.getPlannedSot(), share);
          cm.sovWeightedSum += scheduleSov * scheduleSovWeight;
          cm.sovWeight += scheduleSovWeight;

          if (showBy == SalesPerformanceShowBy.COUNTRY || showBy == SalesPerformanceShowBy.CITY) {
            // Only compute country/city group metrics when requested (avoid extra work).
            country
                .computeIfAbsent(countryKey, k -> new TmpGroup())
                .add(
                    inv.getId(),
                    campaign.getId(),
                    scaledCostShare,
                    revenueShare,
                    hours,
                    schedule,
                    share);
            city.computeIfAbsent(cityCompositeKey, k -> new TmpGroup())
                .add(
                    inv.getId(),
                    campaign.getId(),
                    scaledCostShare,
                    revenueShare,
                    hours,
                    schedule,
                    share);
          }
        }
      }

      // Track campaign ownership by advertiser/agency/user for downstream rollups.
      if (campaign.getClientType() == Campaign.ClientType.DIRECT_ADVERTISER
          && campaign.getCompanyId() != null) {
        advertiserToCampaignIds
            .computeIfAbsent(campaign.getCompanyId(), k -> new LinkedHashSet<>())
            .add(campaign.getId());
      }
      if (campaign.getClientType() == Campaign.ClientType.AGENCY
          && campaign.getAgency() != null
          && campaign.getAgency().getId() != null) {
        agencyToCampaignIds
            .computeIfAbsent(campaign.getAgency().getId(), k -> new LinkedHashSet<>())
            .add(campaign.getId());
      }
      if (campaign.getUserId() != null) {
        userToCampaignIds
            .computeIfAbsent(campaign.getUserId(), k -> new LinkedHashSet<>())
            .add(campaign.getId());
      }
    }

    Map<String, GroupAgg> countryAgg = freezeGroupAgg(country);
    Map<String, GroupAgg> cityAgg = freezeGroupAgg(city);

    return new RangeAggregation(
        Map.copyOf(dayCost),
        Map.copyOf(dayRevenue),
        countryAgg,
        cityAgg,
        Map.copyOf(campaignMetrics),
        Map.copyOf(advertiserToCampaignIds),
        Map.copyOf(agencyToCampaignIds),
        Map.copyOf(userToCampaignIds));
  }

  private static class TmpGroup {
    final Set<String> inventoryIds = new LinkedHashSet<>();
    final Set<String> campaignIds = new LinkedHashSet<>();
    double cost = 0.0;
    double revenue = 0.0;
    long bookedHours = 0L;
    double adPlays = 0.0;
    double impressions = 0.0;
    double plannedSot = 0.0;
    double totalSot = 0.0;

    /** Adds a single allocated day-share into the group accumulator. */
    void add(
        String inventoryId,
        String campaignId,
        double scaledCostShare,
        double revenueShare,
        int hours,
        Schedule schedule,
        double share) {
      if (inventoryId != null) inventoryIds.add(inventoryId);
      if (campaignId != null) campaignIds.add(campaignId);
      cost += scaledCostShare;
      revenue += revenueShare;
      bookedHours += hours;
      adPlays += weightedLong(schedule.getAdPlays(), schedule.getSpotsPerHour(), hours, share);
      impressions += weightedLong(schedule.getImpressions(), null, 0, share);
      plannedSot += weightedDouble(schedule.getPlannedSot(), share);
      totalSot += weightedDouble(schedule.getTotalSot(), share);
    }
  }

  /** Freezes mutable accumulators into immutable group aggregates for safe downstream use. */
  private static Map<String, GroupAgg> freezeGroupAgg(Map<String, TmpGroup> tmp) {
    Map<String, GroupAgg> out = new HashMap<>();
    for (Map.Entry<String, TmpGroup> e : tmp.entrySet()) {
      TmpGroup g = e.getValue();
      out.put(
          e.getKey(),
          new GroupAgg(
              Set.copyOf(g.inventoryIds),
              Set.copyOf(g.campaignIds),
              g.cost,
              g.revenue,
              g.bookedHours,
              g.adPlays,
              g.impressions,
              g.plannedSot,
              g.totalSot));
    }
    return Map.copyOf(out);
  }

  /**
   * Converts country group aggregates into API DTOs. Includes classification counts when
   * inventoryById is provided.
   */
  private List<SalesPerformanceLocationItemDTO> buildCountryItems(
      LocalDate startDate,
      LocalDate endDate,
      Map<String, GroupAgg> groups,
      Map<String, Inventory> inventoryById) {
    return buildLocationItems(
        startDate, endDate, groups, SalesPerformanceShowBy.COUNTRY, inventoryById);
  }

  /** Converts city group aggregates into API DTOs. */
  private List<SalesPerformanceLocationItemDTO> buildCityItems(
      LocalDate startDate, LocalDate endDate, Map<String, GroupAgg> groups) {
    return buildLocationItems(startDate, endDate, groups, SalesPerformanceShowBy.CITY, null);
  }

  /**
   * Shared implementation for country/city items.
   *
   * <p>Computes utilization and conversion metrics using group totals and the requested day span.
   * When showBy is COUNTRY and inventoryById is provided, populates classification counts per
   * inventory classification (e.g. CLASSIC_NETWORK, Digital, Transit).
   */
  private List<SalesPerformanceLocationItemDTO> buildLocationItems(
      LocalDate startDate,
      LocalDate endDate,
      Map<String, GroupAgg> groups,
      SalesPerformanceShowBy showBy,
      Map<String, Inventory> inventoryById) {
    int days = daysInclusive(startDate, endDate);
    List<SalesPerformanceLocationItemDTO> out = new ArrayList<>();
    for (Map.Entry<String, GroupAgg> e : groups.entrySet()) {
      GroupAgg g = e.getValue();
      long inventories = g.inventoryIds().size();
      Double utilization = utilizationPct(g.bookedHours(), inventories, days);
      Double conversion = conversion(g.impressions(), g.adPlays());

      String country;
      String city;
      if (showBy == SalesPerformanceShowBy.CITY) {
        String[] parts = splitCityComposite(e.getKey());
        city = parts[0];
        country = parts[1];
      } else {
        city = null;
        country = e.getKey();
      }

      Map<String, Long> classification = null;
      if (showBy == SalesPerformanceShowBy.COUNTRY && inventoryById != null) {
        classification = new HashMap<>();
        for (String invId : g.inventoryIds()) {
          Inventory inv = inventoryById.get(invId);
          String c = inv != null ? inv.getClassification() : null;
          String key = (c != null && !c.isBlank()) ? c : "Unknown";
          classification.merge(key, 1L, Long::sum);
        }
      }

      out.add(
          SalesPerformanceLocationItemDTO.builder()
              .country(country)
              .city(city)
              .inventories(inventories)
              .utilization(utilization)
              .conversion(conversion)
              .countCampaigns(g.campaignIds().size())
              .cost(g.cost())
              .revenue(g.revenue())
              .classification(classification)
              .build());
    }
    return List.copyOf(out);
  }

  /** Builds advertiser rollup items and resolves company names (best-effort). */
  private List<SalesPerformanceCompanyItemDTO> buildAdvertiserItems(
      RangeAggregation agg, Map<String, Campaign> campaignById) {
    Map<String, String> nameCache = new HashMap<>();
    return buildCompanyItems(
        agg.advertiserToCampaignIds(),
        campaignById,
        agg.campaign(),
        id -> resolveCompanyName(id, nameCache));
  }

  /** Builds agency rollup items and resolves agency names (best-effort). */
  private List<SalesPerformanceCompanyItemDTO> buildAgencyItems(
      RangeAggregation agg, Map<String, Campaign> campaignById) {
    Map<String, String> nameCache = new HashMap<>();
    return buildCompanyItems(
        agg.agencyToCampaignIds(),
        campaignById,
        agg.campaign(),
        id -> resolveAgencyName(id, nameCache));
  }

  /**
   * Builds company rollups (advertiser/agency) from campaign-level metrics.
   *
   * <p>Also calculates revenue share (%) against the total revenue for the view.
   */
  private List<SalesPerformanceCompanyItemDTO> buildCompanyItems(
      Map<String, Set<String>> companyToCampaignIds,
      Map<String, Campaign> campaignById,
      Map<String, CampaignRangeMetrics> campaignMetrics,
      Function<String, String> nameResolver) {

    // Total revenue is used to compute the "share" percentage per company.
    double totalRevenue =
        companyToCampaignIds.values().stream()
            .flatMap(Set::stream)
            .map(campaignMetrics::get)
            .filter(Objects::nonNull)
            .mapToDouble(m -> m.revenue)
            .sum();

    List<SalesPerformanceCompanyItemDTO> out = new ArrayList<>();
    for (Map.Entry<String, Set<String>> entry : companyToCampaignIds.entrySet()) {
      String companyId = entry.getKey();
      Set<String> cids = entry.getValue();

      List<CampaignRangeMetrics> cms =
          cids.stream().map(campaignMetrics::get).filter(Objects::nonNull).toList();

      double revenue = cms.stream().mapToDouble(c -> c.revenue).sum();
      long impressions = Math.round(cms.stream().mapToDouble(c -> c.impressions).sum());
      long adPlays = Math.round(cms.stream().mapToDouble(c -> c.adPlays).sum());

      double plannedSot = cms.stream().mapToDouble(c -> c.plannedSot).sum();
      double totalSot = cms.stream().mapToDouble(c -> c.totalSot).sum();
      double sovWeightedSum = cms.stream().mapToDouble(c -> c.sovWeightedSum).sum();
      double sovWeight = cms.stream().mapToDouble(c -> c.sovWeight).sum();
      Double sov = sovWeight > 0 ? sovWeightedSum / sovWeight : 0.0;

      Double share = totalRevenue > 0.0 ? (revenue / totalRevenue) * 100.0 : 0.0;

      // Include a "top 5 campaigns" preview for quick drill-down.
      List<TopCampaignCostDTO> top5 =
          cids.stream()
              .map(
                  cid -> {
                    CampaignRangeMetrics m = campaignMetrics.get(cid);
                    Campaign c = campaignById.get(cid);
                    if (m == null || c == null) return null;
                    return TopCampaignCostDTO.builder()
                        .campaignId(cid)
                        .campaignName(c.getName())
                        .cost(m.cost)
                        .revenue(m.revenue)
                        .build();
                  })
              .filter(Objects::nonNull)
              .sorted(
                  Comparator.comparing(
                      TopCampaignCostDTO::getCost, Comparator.nullsLast(Comparator.reverseOrder())))
              .limit(5)
              .toList();

      out.add(
          SalesPerformanceCompanyItemDTO.builder()
              .companyId(companyId)
              .name(nameResolver.apply(companyId))
              .revenue(revenue)
              .countCampaigns(cids.size())
              .share(share)
              .adPlays(adPlays)
              .sov(sov)
              .impressions(impressions)
              .topCampaigns(top5)
              .build());
    }
    return List.copyOf(out);
  }

  /** Builds team rollup items by userId and resolves user name/region (best-effort). */
  private List<SalesPerformanceTeamItemDTO> buildTeamItems(RangeAggregation agg) {
    // Total revenue is used to compute the "share" percentage per user.
    double totalRevenue =
        agg.userToCampaignIds().values().stream()
            .flatMap(Set::stream)
            .map(agg.campaign()::get)
            .filter(Objects::nonNull)
            .mapToDouble(m -> m.revenue)
            .sum();

    Map<String, UserResponseDTO> userCache = new HashMap<>();

    List<SalesPerformanceTeamItemDTO> out = new ArrayList<>();
    for (Map.Entry<String, Set<String>> entry : agg.userToCampaignIds().entrySet()) {
      String userId = entry.getKey();
      Set<String> cids = entry.getValue();

      List<CampaignRangeMetrics> cms =
          cids.stream().map(agg.campaign()::get).filter(Objects::nonNull).toList();
      double revenue = cms.stream().mapToDouble(c -> c.revenue).sum();
      Double share = totalRevenue > 0.0 ? (revenue / totalRevenue) * 100.0 : 0.0;

      double impressions = cms.stream().mapToDouble(c -> c.impressions).sum();
      double adPlays = cms.stream().mapToDouble(c -> c.adPlays).sum();
      Double conversion = conversion(impressions, adPlays);

      UserResponseDTO user = resolveUser(userId, userCache);
      String name = user != null ? UserService.extractUserName(user) : "Unknown";
      String region = resolveRegion(user);

      out.add(
          SalesPerformanceTeamItemDTO.builder()
              .userId(userId)
              .name(name)
              .region(region)
              .countCampaigns(cids.size())
              .revenue(revenue)
              .conversion(conversion)
              .share(share)
              .build());
    }
    return List.copyOf(out);
  }

  /** Sorts sales summary items by the requested field/direction (reusable, single comparator). */
  private static List<?> sortSalesPerformanceItems(List<?> items, String sortBy, String sortDir) {
    if (items == null || items.isEmpty()) return List.of();
    Comparator<Object> comparator = SalesPerformanceSummarySort.getComparator(sortBy, sortDir);
    List<Object> copy = new ArrayList<>(items);
    copy.sort(comparator);
    return List.copyOf(copy);
  }

  /** Converts a sorted in-memory list into a Page using the requested pageable slice. */
  private static Page<?> toPage(List<?> items, Pageable pageable) {
    if (items == null || items.isEmpty()) {
      return Page.empty(pageable);
    }
    int p = pageable.getPageNumber();
    int s = pageable.getPageSize();
    int from = Math.min(p * s, items.size());
    int to = Math.min(from + s, items.size());
    List<?> content = List.copyOf(items.subList(from, to));
    return new PageImpl<>(content, pageable, items.size());
  }

  /** Counts total booked hours across the full booking matrix (all days). */
  private static long totalHours(Map<String, List<Integer>> bookingMatrix) {
    if (bookingMatrix == null || bookingMatrix.isEmpty()) return 0L;
    long total = 0L;
    for (List<Integer> hours : bookingMatrix.values()) {
      if (hours != null) {
        total += hours.size();
      }
    }
    return total;
  }

  /**
   * Returns a weighted value for a metric that can be represented either as a total (then
   * multiplied by share) or as spots/hour (then multiplied by hours).
   */
  private static double weightedLong(Long totalValue, Long spotsPerHour, int hours, double share) {
    if (totalValue != null) {
      return totalValue.doubleValue() * share;
    }
    if (spotsPerHour != null && spotsPerHour > 0 && hours > 0) {
      return (double) spotsPerHour * (double) hours;
    }
    return 0.0;
  }

  /** Returns totalValue * share, or 0 if totalValue is null. */
  private static double weightedDouble(Double totalValue, double share) {
    if (totalValue == null) return 0.0;
    return totalValue * share;
  }

  /** Calculates utilization percentage as bookedHours / (inventories * days * 24). */
  private static Double utilizationPct(long bookedHours, long inventories, int days) {
    if (inventories <= 0 || days <= 0) return 0.0;
    double denom = (double) inventories * (double) days * 24.0;
    if (denom <= 0.0) return 0.0;
    return ((double) bookedHours / denom) * 100.0;
  }

  /** Calculates conversion as impressions / adPlays (guarded for divide-by-zero). */
  private static Double conversion(double impressions, double adPlays) {
    if (adPlays <= 0.0) return 0.0;
    return impressions / adPlays;
  }

  /** Returns inclusive day count for the range, or 0 for invalid ranges. */
  private static int daysInclusive(LocalDate startDate, LocalDate endDate) {
    if (startDate == null || endDate == null || startDate.isAfter(endDate)) return 0;
    return (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1;
  }

  /** Null-to-zero helper for boxed doubles. */
  private static Double zero(Double v) {
    return v != null ? v : 0.0;
  }

  /** Normalizes blank strings to "Unknown" (used for grouping keys). */
  private static String safe(String v) {
    return (v == null || v.isBlank()) ? "Unknown" : v;
  }

  /** Splits "city||country" composite keys into [city, country] (null/blank-safe). */
  private static String[] splitCityComposite(String composite) {
    if (composite == null) return new String[] {"Unknown", "Unknown"};
    String[] parts = composite.split("\\|\\|", -1);
    String city = parts.length > 0 ? parts[0] : "Unknown";
    String country = parts.length > 1 ? parts[1] : "Unknown";
    return new String[] {safe(city), safe(country)};
  }

  /** Resolves company name for advertiser grouping (cached, best-effort). */
  private String resolveCompanyName(String companyId, Map<String, String> cache) {
    if (companyId == null || companyId.isBlank()) return "Unknown";
    if (cache.containsKey(companyId)) return cache.get(companyId);
    try {
      String name = companyService.getCompanyLookupWithCompanyId(companyId).getName();
      cache.put(companyId, name != null ? name : "Unknown");
      return cache.get(companyId);
    } catch (Exception e) {
      log.debug("Unable to resolve company name for id {}", companyId, e);
      cache.put(companyId, "Unknown");
      return "Unknown";
    }
  }

  /** Resolves agency name for agency grouping (cached, best-effort). */
  private String resolveAgencyName(String agencyId, Map<String, String> cache) {
    if (agencyId == null || agencyId.isBlank()) return "Unknown";
    if (cache.containsKey(agencyId)) return cache.get(agencyId);
    try {
      String name = agencyService.getNameById(agencyId);
      cache.put(agencyId, name != null ? name : "Unknown");
      return cache.get(agencyId);
    } catch (Exception e) {
      log.debug("Unable to resolve agency name for id {}", agencyId, e);
      cache.put(agencyId, "Unknown");
      return "Unknown";
    }
  }

  /** Resolves user details for team grouping (cached, best-effort). */
  private UserResponseDTO resolveUser(String userId, Map<String, UserResponseDTO> cache) {
    if (userId == null || userId.isBlank()) return null;
    if (cache.containsKey(userId)) return cache.get(userId);
    try {
      UserResponseDTO u = userService.getUserById(userId);
      cache.put(userId, u);
      return u;
    } catch (Exception e) {
      log.debug("Unable to resolve user {}", userId, e);
      cache.put(userId, null);
      return null;
    }
  }

  /** Derives a displayable region string from the user record (best-effort). */
  private static String resolveRegion(UserResponseDTO user) {
    if (user == null) return "Unknown";
    if (user.getLocation() != null && !user.getLocation().isBlank()) return user.getLocation();
    if (user.getCountryId() != null && !user.getCountryId().isBlank()) return user.getCountryId();
    return "Unknown";
  }

  /** Validates required date range inputs for sales performance summary. */
  private static void validateDates(LocalDate startDate, LocalDate endDate) {
    if (startDate == null || endDate == null) {
      throw new IllegalArgumentException("startDate and endDate are required");
    }
    if (startDate.isAfter(endDate)) {
      throw new CampaignDateRangeException(startDate, endDate);
    }
  }

  /**
   * Get top N campaigns by total cost (after calculating total cost for all in date range), then
   * sort only those top N by Impressions, Reach, Revenue (totalCost), SOV.
   *
   * @param request startDate, endDate, sortDir, limit (default 5)
   * @return list of campaign summaries with name, dates, brand, impressions, reach, totalCost, sov,
   *     status
   */
  public List<CampaignFilterResponseDTO> getCampaignPerformanceByTotalCost(
      CampaignSummaryRequestDTO request) {
    // Guard invalid date ranges at service layer for consistent 400 behavior.
    if (request.getStartDate() != null
        && request.getEndDate() != null
        && request.getStartDate().isAfter(request.getEndDate())) {
      throw new CampaignDateRangeException(request.getStartDate(), request.getEndDate());
    }
    // Only campaigns visible to the logged-in company are eligible for the dashboard.
    String companyId = request.getCompanyId();
    CampaignFilterDTO filter =
        CampaignFilterDTO.builder()
            .createdAtFrom(request.getStartDate())
            .createdAtTo(request.getEndDate())
            .statuses(request.getStatuses())
            .companyId(companyId)
            // Constrain to the caller's Test Mode partition (server-side, never client-driven).
            .dataMode(testModeService.getEffectiveDataMode())
            .build();

    // Unpaged read: we do ranking in-memory, then build DTOs only for the top N.
    List<Campaign> campaigns =
        campaignRepository.findCampaignsWithFilters(filter, Pageable.unpaged()).getContent();

    if (campaigns.isEmpty()) {
      return List.of();
    }

    // Clamp limit to [1..campaigns.size()] with default=5.
    int limit = Optional.ofNullable(request.getLimit()).filter(l -> l > 0).orElse(5);
    limit = Math.min(limit, campaigns.size());
    if (limit <= 0) {
      return List.of();
    }

    // Phase 1: compute totalCost only for ranking (avoid IAM/brand/status/proposal lookups for
    // all).
    // Keep the top N in a min-heap to avoid sorting the entire list.
    record CampaignCost(Campaign campaign, Double totalCost) {}
    Comparator<CampaignCost> byCostAsc =
        Comparator.comparing(
            CampaignCost::totalCost, Comparator.nullsFirst(Comparator.naturalOrder()));
    PriorityQueue<CampaignCost> topN = new PriorityQueue<>(limit + 1, byCostAsc);

    // Bulk compute total costs to avoid per-campaign DB/query fan-out.
    Map<String, Double> totalCostByCampaignId =
        campaignService.calculateTotalCostsForDashboard(campaigns);
    for (Campaign c : campaigns) {
      Double totalCost =
          (c != null && c.getId() != null) ? totalCostByCampaignId.get(c.getId()) : null;
      CampaignCost candidate = new CampaignCost(c, totalCost);
      if (topN.size() < limit) {
        topN.add(candidate);
      } else if (byCostAsc.compare(candidate, topN.peek()) > 0) {
        topN.poll();
        topN.add(candidate);
      }
    }

    // Highest-cost campaigns first.
    List<Campaign> topCampaigns =
        topN.stream()
            .sorted(
                Comparator.comparing(
                    CampaignCost::totalCost, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(CampaignCost::campaign)
            .toList();

    // Phase 2: build full DTOs only for top N.
    // Use sequential stream: conversion uses thread-local IAM/security context.
    List<CampaignFilterResponseDTO> topByCost =
        topCampaigns.stream().map(campaignService::convertToCampaignFilterResponseDTO).toList();

    // Apply secondary sort only to these top N: Impressions, Reach, Revenue (totalCost), SOV
    String sortDir = Optional.ofNullable(request.getSortDir()).orElse("desc");
    boolean descending = "desc".equalsIgnoreCase(sortDir);
    Comparator<CampaignFilterResponseDTO> secondarySort = secondarySortForTopCampaigns(descending);

    return topByCost.stream().sorted(secondarySort).toList();
  }

  /**
   * Comparator for sorting top campaigns by: Impressions, then Reach, then Revenue (totalCost),
   * then SOV. Applied only to the already-selected top N by total cost.
   */
  private Comparator<CampaignFilterResponseDTO> secondarySortForTopCampaigns(boolean descending) {
    Comparator<Long> longOrder =
        descending
            ? Comparator.nullsLast(Comparator.reverseOrder())
            : Comparator.nullsLast(Comparator.naturalOrder());

    Comparator<Double> costOrder =
        descending
            ? Comparator.nullsLast(Comparator.reverseOrder())
            : Comparator.nullsLast(Comparator.naturalOrder());
    Comparator<Double> doubleOrder =
        descending
            ? Comparator.nullsLast(Comparator.naturalOrder())
            : Comparator.nullsLast(Comparator.reverseOrder());
    return Comparator.comparing(CampaignFilterResponseDTO::getTotalCost, costOrder)
        .thenComparing(CampaignFilterResponseDTO::getEstimatedImpression, longOrder)
        .thenComparing(CampaignFilterResponseDTO::getEstimatedReach, longOrder)
        .thenComparing(CampaignFilterResponseDTO::getSov, doubleOrder);
  }

  /**
   * Creative Status Tracker — replaces V1's hardcoded mock endpoint with a real read over line
   * items (on Approved/Active campaigns for the acting company) and their creative assignments.
   */
  public com.mw.planner.dto.creative.CreativeStatusTrackerDTO getCreativeStatusTracker(
      String companyId) {
    List<Campaign> campaigns =
        campaignRepository.findByStatusInAndCompanyInvolved(
            java.util.List.of(Campaign.Status.APPROVED, Campaign.Status.ACTIVE), companyId);
    List<String> campaignIds = campaigns.stream().map(Campaign::getId).toList();

    List<com.mw.planner.domain.CampaignInventorySchedules> lineItems =
        campaignInventorySchedulesRepository.findByCampaignIdIn(campaignIds);

    java.util.Map<String, com.mw.planner.domain.CreativeAssignment> assignmentByLineItem =
        creativeAssignmentRepository.findByCampaignIdIn(campaignIds).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    com.mw.planner.domain.CreativeAssignment::getLineItemId, a -> a, (a, b) -> a));

    java.util.List<String> boundCreativeIds =
        assignmentByLineItem.values().stream()
            .filter(
                a ->
                    a.getBindingStatus()
                        != com.mw.planner.domain.CreativeAssignment.BindingStatus.REJECTED)
            .map(com.mw.planner.domain.CreativeAssignment::getCreativeId)
            .toList();
    java.util.Map<String, com.mw.planner.domain.Creative.Format> formatByCreativeId =
        creativeRepository.findAllById(boundCreativeIds).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    com.mw.planner.domain.Creative::getId,
                    com.mw.planner.domain.Creative::getFormat));

    java.util.Map<com.mw.planner.domain.Creative.Format, Long> boundByFormat =
        new java.util.EnumMap<>(com.mw.planner.domain.Creative.Format.class);
    java.util.List<com.mw.planner.dto.creative.CreativeStatusTrackerDTO.MissingLineItem> missing =
        new java.util.ArrayList<>();

    for (com.mw.planner.domain.CampaignInventorySchedules lineItem : lineItems) {
      com.mw.planner.domain.CreativeAssignment assignment =
          assignmentByLineItem.get(lineItem.getId());
      boolean bound =
          assignment != null
              && assignment.getBindingStatus()
                  != com.mw.planner.domain.CreativeAssignment.BindingStatus.REJECTED;
      if (bound) {
        com.mw.planner.domain.Creative.Format format =
            formatByCreativeId.get(assignment.getCreativeId());
        if (format != null) boundByFormat.merge(format, 1L, Long::sum);
      } else {
        missing.add(
            com.mw.planner.dto.creative.CreativeStatusTrackerDTO.MissingLineItem.builder()
                .lineItemId(lineItem.getId())
                .campaignId(lineItem.getCampaignId())
                .inventoryId(lineItem.getInventoryId())
                .build());
      }
    }

    List<com.mw.planner.dto.creative.CreativeStatusTrackerDTO.FormatRow> byFormat =
        java.util.Arrays.stream(com.mw.planner.domain.Creative.Format.values())
            .map(
                f ->
                    com.mw.planner.dto.creative.CreativeStatusTrackerDTO.FormatRow.builder()
                        .format(f.name())
                        .totalBound(boundByFormat.getOrDefault(f, 0L))
                        .build())
            .toList();

    return com.mw.planner.dto.creative.CreativeStatusTrackerDTO.builder()
        .totalLineItems(lineItems.size())
        .byFormat(byFormat)
        .missing(missing)
        .build();
  }

  /**
   * Tier 1 (internal approval) status counts for the acting company's creative library —
   * Processing/Accepted/Inadequate, per the creative-management spec. Distinct from {@link
   * #getCreativeStatusTracker}, which tracks per-line-item binding coverage, not the underlying
   * asset's own approval state.
   */
  public com.mw.planner.dto.creative.CreativeTier1SummaryDTO getCreativeTier1Summary(
      String companyId) {
    List<com.mw.planner.domain.Creative> creatives =
        creativeRepository.findByCompanyIdAndIsActiveTrue(companyId);

    long processing = 0;
    long accepted = 0;
    long inadequate = 0;
    long images = 0;
    long imagesAccepted = 0;
    long videos = 0;
    long videosAccepted = 0;

    for (com.mw.planner.domain.Creative creative : creatives) {
      boolean isAccepted =
          creative.getTier1Status() == com.mw.planner.domain.Creative.Tier1Status.ACCEPTED;
      switch (creative.getTier1Status()) {
        case PROCESSING -> processing++;
        case ACCEPTED -> accepted++;
        case INADEQUATE -> inadequate++;
        case ARCHIVE -> {}
      }
      if (creative.getFormat() == com.mw.planner.domain.Creative.Format.STATIC) {
        images++;
        if (isAccepted) imagesAccepted++;
      } else if (creative.getFormat() == com.mw.planner.domain.Creative.Format.VIDEO) {
        videos++;
        if (isAccepted) videosAccepted++;
      }
    }

    return com.mw.planner.dto.creative.CreativeTier1SummaryDTO.builder()
        .processing(processing)
        .accepted(accepted)
        .inadequate(inadequate)
        .totalCreatives(creatives.size())
        .images(images)
        .videos(videos)
        .imagesAcceptedPercent(images == 0 ? 0 : (int) Math.round(100.0 * imagesAccepted / images))
        .videosAcceptedPercent(videos == 0 ? 0 : (int) Math.round(100.0 * videosAccepted / videos))
        .build();
  }

  static class DailyAggregate {
    double sumPerDateBudget;
    double sumPerDateRate;
    double sumPerDateReach;
    double sumPerDateImpressions;

    void addCost(double budget, double rate) {
      this.sumPerDateBudget += budget;
      this.sumPerDateRate += rate;
    }

    void addReach(double reach, double impressions) {
      this.sumPerDateReach += reach;
      this.sumPerDateImpressions += impressions;
    }
  }
}
