package com.mw.recommendation.engine.service;

import com.mw.brand.lib.dto.BrandResponseDTO;
import com.mw.brand.lib.service.BrandService;
import com.mw.recommendation.engine.domain.AudienceData;
import com.mw.recommendation.engine.domain.BookingData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.repository.BookingRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
import com.mw.recommendation.engine.util.NormalizationUtils;
import com.mw.recommendation.engine.util.ScoringWeights;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.geo.GeoJsonLineString;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;

/** Implementation of ScoringService. Calculates all scoring components and final weighted score. */
@Service
@Slf4j
@RequiredArgsConstructor
public class ScoringServiceImpl implements ScoringService {

  private final InventoryRepository inventoryRepository;
  private final BookingRepository bookingRepository;
  private final BrandService brandService;
  private final PlannerAvailabilityService plannerAvailabilityService;

  private final Map<String, Long> totalPossibleAdPlaysCache = new ConcurrentHashMap<>();

  // Default radius values for geo_fit calculation (in meters)
  private static final double R1_RADIUS = 1000.0; // 1km
  private static final double R2_RADIUS = 50000.0; // 50km

  // Budget fit thresholds
  private static final double BUDGET_FIT_EXCELLENT = 0.2; // <= 20% of budget
  private static final double BUDGET_FIT_GOOD = 0.4; // <= 40% of budget
  private static final double BUDGET_FIT_FAIR = 0.6; // <= 60% of budget

  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

  @Override
  public InventoryScore calculateScore(
      Inventory inventory, AudienceData audienceData, RecommendationRequestDTO request) {
    log.debug("Calculating score for inventory: {}", inventory.getReferenceId());

    // Calculate all component scores
    Double measureFit = null;
    if (request.getGoal() == RecommendationRequestDTO.CampaignGoal.SOV) {
      // SOV requires inventory context, calculate here
      // SOV = inventory_ad_plays / total_possible_ad_plays * 100
      // This SOV percentage (0-100) is the measure_fit score
      measureFit =
          calculateSOV(
              inventory, request.getCountry(), request.getStartDate(), request.getEndDate());
      // If goalValue is provided, calculate contribution fraction
      if (measureFit != null && request.getGoalValue() != null) {
        // Compare actual SOV to goal SOV
        double contributionFraction = Math.min(measureFit / request.getGoalValue(), 1.0);
        measureFit = contributionFraction * 100.0;
      }
    } else if (request.getGoal() == RecommendationRequestDTO.CampaignGoal.AD_PLAYS) {
      Long inventoryAdPlays =
          calculateInventoryAdPlays(inventory, request.getStartDate(), request.getEndDate());
      if (inventoryAdPlays != null
          && inventoryAdPlays > 0
          && request.getGoalValue() != null
          && request.getGoalValue() > 0) {
        double contributionFraction =
            Math.min((double) inventoryAdPlays / request.getGoalValue(), 1.0);
        measureFit = contributionFraction * 100.0;
      }
    } else {
      measureFit =
          calculateMeasureFit(
              audienceData,
              request.getStartDate(),
              request.getEndDate(),
              request.getGoal(),
              request.getGoalValue());
    }

    Double geoFit = calculateGeoFit(inventory, request.getGeographyTargeting());

    Double availability =
        calculateAvailability(inventory, request.getStartDate(), request.getEndDate());

    Double budgetFit =
        request.getBudget() != null
            ? calculateBudgetFit(
                inventory, request.getStartDate(), request.getEndDate(), request.getBudget())
            : 50.0; // Neutral if no budget provided

    Double audienceFit = calculateAudienceFit(audienceData, request.getAudienceTargeting());

    Double brandFit =
        calculateBrandFit(inventory, request.getBrandId(), request.getExcludedIabCategories());

    Double qualityFit = calculateQualityFit(inventory);

    Double timeFit =
        calculateTimeFit(audienceData, request.getStartDate(), request.getEndDate(), null);

    // Calculate final score with weight redistribution if measure_fit is null
    Double finalScore =
        calculateFinalScore(
            measureFit,
            geoFit,
            availability,
            budgetFit,
            audienceFit,
            brandFit,
            qualityFit,
            timeFit);

    // Generate explanation
    String explanation =
        generateExplanation(
            measureFit,
            geoFit,
            availability,
            budgetFit,
            audienceFit,
            brandFit,
            qualityFit,
            inventory);

    return InventoryScore.builder()
        .measureFit(measureFit)
        .geoFit(geoFit)
        .availability(availability)
        .budgetFit(budgetFit)
        .audienceFit(audienceFit)
        .brandFit(brandFit)
        .qualityFit(qualityFit)
        .timeFit(timeFit)
        .finalScore(finalScore)
        .explanation(explanation)
        .build();
  }

  @Override
  public InventoryScore calculateScore(
      Inventory inventory,
      AudienceData audienceData,
      RecommendationRequestDTO request,
      Map<String, List<BookingData>> bookingDataCache,
      Map<String, BrandResponseDTO> brandDataCache) {

    log.debug("Calculating score for inventory: {} (with cached data)", inventory.getReferenceId());

    // Calculate all component scores - IDENTICAL LOGIC except cached data usage
    Double measureFit = null;
    if (request.getGoal() == RecommendationRequestDTO.CampaignGoal.SOV) {
      measureFit =
          calculateSOV(
              inventory, request.getCountry(), request.getStartDate(), request.getEndDate());
      if (measureFit != null && request.getGoalValue() != null) {
        double contributionFraction = Math.min(measureFit / request.getGoalValue(), 1.0);
        measureFit = contributionFraction * 100.0;
      }
    } else if (request.getGoal() == RecommendationRequestDTO.CampaignGoal.AD_PLAYS) {
      Long inventoryAdPlays =
          calculateInventoryAdPlays(inventory, request.getStartDate(), request.getEndDate());
      if (inventoryAdPlays != null
          && inventoryAdPlays > 0
          && request.getGoalValue() != null
          && request.getGoalValue() > 0) {
        double contributionFraction =
            Math.min((double) inventoryAdPlays / request.getGoalValue(), 1.0);
        measureFit = contributionFraction * 100.0;
      }
    } else {
      measureFit =
          calculateMeasureFit(
              audienceData,
              request.getStartDate(),
              request.getEndDate(),
              request.getGoal(),
              request.getGoalValue());
    }

    Double geoFit = calculateGeoFit(inventory, request.getGeographyTargeting());

    // Use cached booking data instead of DB query
    Double availability =
        calculateAvailability(
            inventory, request.getStartDate(), request.getEndDate(), bookingDataCache);

    Double budgetFit =
        request.getBudget() != null
            ? calculateBudgetFit(
                inventory, request.getStartDate(), request.getEndDate(), request.getBudget())
            : 50.0;

    Double audienceFit = calculateAudienceFit(audienceData, request.getAudienceTargeting());

    // Use cached brand data instead of API call
    Double brandFit =
        calculateBrandFit(
            inventory, request.getBrandId(), request.getExcludedIabCategories(), brandDataCache);

    Double qualityFit = calculateQualityFit(inventory);

    Double timeFit =
        calculateTimeFit(audienceData, request.getStartDate(), request.getEndDate(), null);

    Double finalScore =
        calculateFinalScore(
            measureFit,
            geoFit,
            availability,
            budgetFit,
            audienceFit,
            brandFit,
            qualityFit,
            timeFit);

    String explanation =
        generateExplanation(
            measureFit,
            geoFit,
            availability,
            budgetFit,
            audienceFit,
            brandFit,
            qualityFit,
            inventory);

    return InventoryScore.builder()
        .measureFit(measureFit)
        .geoFit(geoFit)
        .availability(availability)
        .budgetFit(budgetFit)
        .audienceFit(audienceFit)
        .brandFit(brandFit)
        .qualityFit(qualityFit)
        .timeFit(timeFit)
        .finalScore(finalScore)
        .explanation(explanation)
        .build();
  }

  @Override
  public Double calculateMeasureFit(
      AudienceData audienceData,
      LocalDate startDate,
      LocalDate endDate,
      RecommendationRequestDTO.CampaignGoal goal,
      Long goalValue) {

    if (audienceData == null || goal == null || goalValue == null) {
      return null;
    }

    Long inventoryValue = null;

    if (goal == RecommendationRequestDTO.CampaignGoal.IMPRESSIONS) {
      inventoryValue = calculateImpressionsFromAudienceData(audienceData, startDate, endDate);
    } else if (goal == RecommendationRequestDTO.CampaignGoal.REACH) {
      inventoryValue = calculateReachFromAudienceData(audienceData, startDate, endDate);
    } else if (goal == RecommendationRequestDTO.CampaignGoal.SOV) {
      // SOV calculation requires inventory object, handled separately
      return null; // Will be calculated in calculateScore method with inventory context
    }

    if (inventoryValue == null || inventoryValue == 0) {
      return null;
    }

    // Contribution fraction: min(inventoryValue / goalValue, 1)
    double contributionFraction = Math.min((double) inventoryValue / goalValue, 1.0);

    // measure_fit = contributionFraction * 100
    return contributionFraction * 100.0;
  }

  /** Calculate impressions from audience data, using daily summary first, then monthly summary */
  private Long calculateImpressionsFromAudienceData(
      AudienceData audienceData, LocalDate startDate, LocalDate endDate) {

    // Try daily summary first
    if (audienceData.getDailySummary() != null && !audienceData.getDailySummary().isEmpty()) {
      long totalVisitors = 0;
      int dayCount = 0;

      for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
        int dayOfMonth = date.getDayOfMonth();
        AudienceData.DailySummary daily = audienceData.getDailySummary().get(dayOfMonth);
        if (daily != null && daily.getTotalVisitors() != null) {
          totalVisitors += daily.getTotalVisitors();
          dayCount++;
        }
      }

      if (dayCount > 0) {
        // Use actual daily data
        return totalVisitors;
      }
    }

    // Fallback to monthly summary
    if (audienceData.getMonthlySummary() != null
        && audienceData.getMonthlySummary().getTotalVisitors() != null) {
      long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
      // Estimate: monthly visitors * (days / 30)
      return (long) (audienceData.getMonthlySummary().getTotalVisitors() * days / 30.0);
    }

    return null;
  }

  /** Calculate reach from audience data, using daily summary first, then monthly summary */
  private Long calculateReachFromAudienceData(
      AudienceData audienceData, LocalDate startDate, LocalDate endDate) {

    // Try daily summary first
    if (audienceData.getDailySummary() != null && !audienceData.getDailySummary().isEmpty()) {
      long totalUniqueVisitors = 0;
      int dayCount = 0;

      for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
        int dayOfMonth = date.getDayOfMonth();
        AudienceData.DailySummary daily = audienceData.getDailySummary().get(dayOfMonth);
        if (daily != null && daily.getUniqueVisitors() != null) {
          totalUniqueVisitors += daily.getUniqueVisitors();
          dayCount++;
        }
      }

      if (dayCount > 0) {
        // Use actual daily data
        return totalUniqueVisitors;
      }
    }

    // Fallback to monthly summary
    if (audienceData.getMonthlySummary() != null
        && audienceData.getMonthlySummary().getUniqueVisitors() != null) {
      long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
      // Estimate: monthly unique visitors * (days / 30)
      return (long) (audienceData.getMonthlySummary().getUniqueVisitors() * days / 30.0);
    }

    return null;
  }

  /**
   * Calculate SOV (Share of Voice) for digital inventory. SOV = inventory_ad_plays /
   * total_possible_ad_plays * 100
   */
  private Double calculateSOV(
      Inventory inventory, String country, LocalDate startDate, LocalDate endDate) {

    // Only calculate SOV for digital inventories
    if (inventory.getDigitalFields() == null) {
      return null;
    }

    Inventory.DigitalFields digitalFields = inventory.getDigitalFields();

    // Check if digitalFields has required data
    if (digitalFields.getLoopDuration() == null
        || digitalFields.getSpotsPerLoop() == null
        || digitalFields.getLoopDuration() == 0) {
      log.debug(
          "Digital inventory {} missing required fields for SOV calculation",
          inventory.getReferenceId());
      return null;
    }

    // Calculate inventory_ad_plays
    Long inventoryAdPlays = calculateInventoryAdPlays(inventory, startDate, endDate);
    if (inventoryAdPlays == null || inventoryAdPlays == 0) {
      return null;
    }

    // Get or calculate total_possible_ad_plays (cached, country-specific)
    Long totalPossibleAdPlays = getTotalPossibleAdPlays(country, startDate, endDate);
    if (totalPossibleAdPlays == null || totalPossibleAdPlays == 0) {
      return null;
    }

    // Calculate SOV: inventory_ad_plays / total_possible_ad_plays * 100
    double sov = ((double) inventoryAdPlays / totalPossibleAdPlays) * 100.0;
    return Math.min(100.0, Math.max(0.0, sov));
  }

  /**
   * Calculate inventory_ad_plays for a digital inventory based on operating times and digital
   * fields
   */
  private Long calculateInventoryAdPlays(
      Inventory inventory, LocalDate startDate, LocalDate endDate) {

    Inventory.DigitalFields digitalFields = inventory.getDigitalFields();
    if (digitalFields == null) {
      return null;
    }

    Integer loopDuration = digitalFields.getLoopDuration();
    Integer spotsPerLoop = digitalFields.getSpotsPerLoop();

    if (loopDuration == null || spotsPerLoop == null || loopDuration == 0) {
      return null;
    }

    // Calculate loops per hour: 3600 seconds / loopDuration
    double loopsPerHour = 3600.0 / loopDuration;
    // Calculate spots per hour: loopsPerHour * spotsPerLoop
    double spotsPerHour = loopsPerHour * spotsPerLoop;

    long totalAdPlays = 0;

    // Iterate through each day in the date range
    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      Inventory.Weekday weekday = getWeekday(date);

      // Get operating times for this weekday
      if (inventory.getOperatingTimes() == null
          || !inventory.getOperatingTimes().containsKey(weekday)) {
        continue;
      }

      List<Inventory.OperatingTime> operatingTimes = inventory.getOperatingTimes().get(weekday);
      if (operatingTimes == null || operatingTimes.isEmpty()) {
        continue;
      }

      // Calculate total operating hours for this day
      double totalHours = 0.0;
      for (Inventory.OperatingTime time : operatingTimes) {
        try {
          LocalTime start = LocalTime.parse(time.getStart(), TIME_FORMATTER);
          LocalTime end = LocalTime.parse(time.getEnd(), TIME_FORMATTER);
          long seconds = ChronoUnit.SECONDS.between(start, end);
          if (seconds < 0) {
            // Handle overnight times (e.g., 22:00 to 02:00)
            seconds =
                ChronoUnit.SECONDS.between(start, LocalTime.MAX)
                    + ChronoUnit.SECONDS.between(LocalTime.MIN, end)
                    + 1; // +1 to include both start and end
          }
          totalHours += seconds / 3600.0;
        } catch (Exception e) {
          log.warn(
              "Error parsing operating time for inventory {}: {}",
              inventory.getReferenceId(),
              e.getMessage());
        }
      }

      // Calculate ad plays for this day: totalHours * spotsPerHour
      totalAdPlays += (long) (totalHours * spotsPerHour);
    }

    return totalAdPlays;
  }

  private Long getTotalPossibleAdPlays(String country, LocalDate startDate, LocalDate endDate) {
    String cacheKey = country + "|" + startDate + "|" + endDate;
    return totalPossibleAdPlaysCache.computeIfAbsent(
        cacheKey,
        k -> {
          log.debug(
              "Computing total_possible_ad_plays for country: {}, date range: {} to {}",
              country,
              startDate,
              endDate);
          List<Inventory> digitalInventories =
              inventoryRepository.findAllActiveDigitalInventoriesByCountry(country);
          long total = 0;
          for (Inventory inv : digitalInventories) {
            Long adPlays = calculateInventoryAdPlays(inv, startDate, endDate);
            if (adPlays != null) total += adPlays;
          }
          log.debug("Computed total_possible_ad_plays for country {}: {}", country, total);
          return total;
        });
  }

  @Override
  public Double calculateGeoFit(
      Inventory inventory, RecommendationRequestDTO.GeographyTargeting targeting) {

    if (targeting == null) {
      return 100.0; // No targeting means all locations are acceptable
    }

    if (inventory.getLocationCoordinates() == null) {
      return 0.0; // No location data
    }

    // Extract point(s) from inventory location (handle both Point and LineString)
    List<Point> inventoryPoints = extractPointsFromLocation(inventory.getLocationCoordinates());
    if (inventoryPoints.isEmpty()) {
      return 0.0; // No valid location points
    }

    // Use first point as primary location (for LineString, we'll check distance to
    // line)
    Point primaryPoint = inventoryPoints.getFirst();
    double lon = primaryPoint.getX();
    double lat = primaryPoint.getY();

    double minDistance = Double.MAX_VALUE;
    Double customR1 = null; // Custom R1 from geofence/POI radius

    // Check cities
    if (targeting.getCities() != null && !targeting.getCities().isEmpty()) {
      if (inventory.getLocationHierarchy() != null
          && inventory.getLocationHierarchy().getCityName() != null) {
        if (targeting.getCities().contains(inventory.getLocationHierarchy().getCityName())) {
          return 100.0; // Exact city match
        }
      }
    }

    // Check states
    if (targeting.getStates() != null && !targeting.getStates().isEmpty()) {
      if (inventory.getLocationHierarchy() != null
          && inventory.getLocationHierarchy().getStateName() != null) {
        if (targeting.getStates().contains(inventory.getLocationHierarchy().getStateName())) {
          return 95.0; // State match
        }
      }
    }

    // Check geofences
    if (targeting.getGeofences() != null && !targeting.getGeofences().isEmpty()) {
      for (RecommendationRequestDTO.Geofence geofence : targeting.getGeofences()) {
        double distance = Double.MAX_VALUE;

        if ("Circle".equals(geofence.getType())
            && geofence.getCenterLng() != null
            && geofence.getCenterLat() != null) {
          // Circle geofence - always uses 50KM radius
          distance =
              calculateDistanceInMeters(lon, lat, geofence.getCenterLng(), geofence.getCenterLat());
          // Use 50KM as R1 for circle geofences
          customR1 = 50000.0;
        } else if ("Polygon".equals(geofence.getType())
            && geofence.getCoordinates() != null
            && !geofence.getCoordinates().isEmpty()) {
          // Polygon geofence - check if point is inside
          if (isPointInPolygon(lon, lat, geofence.getCoordinates())) {
            distance = 0.0; // Point is inside polygon
          } else {
            // Point is outside, calculate distance to polygon boundary
            distance = calculateDistanceToPolygon(lon, lat, geofence.getCoordinates());
          }
        } else if ("LineString".equals(geofence.getType())
            && geofence.getCoordinates() != null
            && geofence.getCoordinates().size() >= 2) {
          // LineString geofence - calculate distance to line
          distance = calculateDistanceToLineString(lon, lat, geofence.getCoordinates());
        }

        minDistance = Math.min(minDistance, distance);
      }
    }

    // Calculate geo_fit based on distance
    if (minDistance < Double.MAX_VALUE) {
      // Use custom R1 if provided, otherwise use default
      double r1 = (customR1 != null) ? customR1 : R1_RADIUS;

      if (minDistance <= r1) {
        return 90.0; // Within R1
      } else if (minDistance <= R2_RADIUS) {
        // Distance decay: max(0, 100 * (1 - (distance - R1) / (R2 - R1)))
        double decayFactor = (minDistance - r1) / (R2_RADIUS - r1);
        return Math.max(0.0, 100.0 * (1 - decayFactor));
      } else {
        return 0.0; // Beyond R2
      }
    }

    // No matching targeting criteria
    return 0.0;
  }

  /** Extract point(s) from inventory location (handles both Point and LineString) */
  private List<Point> extractPointsFromLocation(Object locationCoordinates) {
    List<Point> points = new java.util.ArrayList<>();

    if (locationCoordinates instanceof GeoJsonPoint point) {
      points.add(new Point(point.getX(), point.getY()));
    } else if (locationCoordinates instanceof GeoJsonLineString lineString) {
      // For LineString, extract all points
      points.addAll(lineString.getCoordinates());
    }

    return points;
  }

  /** Check if a point is inside a polygon using ray casting algorithm */
  private boolean isPointInPolygon(double lon, double lat, List<List<Double>> polygonCoordinates) {
    if (polygonCoordinates == null || polygonCoordinates.size() < 3) {
      return false; // Need at least 3 points for a polygon
    }

    int intersections = 0;
    int n = polygonCoordinates.size();

    for (int i = 0; i < n; i++) {
      double x1 = polygonCoordinates.get(i).get(0); // longitude
      double y1 = polygonCoordinates.get(i).get(1); // latitude
      double x2 = polygonCoordinates.get((i + 1) % n).get(0);
      double y2 = polygonCoordinates.get((i + 1) % n).get(1);

      // Check if ray intersects with edge
      if (((y1 > lat) != (y2 > lat)) && (lon < (x2 - x1) * (lat - y1) / (y2 - y1) + x1)) {
        intersections++;
      }
    }

    return (intersections % 2) == 1; // Odd number of intersections means inside
  }

  /** Calculate distance from a point to the nearest edge of a polygon (in meters) */
  private double calculateDistanceToPolygon(
      double lon, double lat, List<List<Double>> polygonCoordinates) {
    if (polygonCoordinates == null || polygonCoordinates.size() < 2) {
      return Double.MAX_VALUE;
    }

    double minDistance = Double.MAX_VALUE;
    int n = polygonCoordinates.size();

    // Calculate distance to each edge of the polygon
    for (int i = 0; i < n; i++) {
      double x1 = polygonCoordinates.get(i).get(0);
      double y1 = polygonCoordinates.get(i).get(1);
      double x2 = polygonCoordinates.get((i + 1) % n).get(0);
      double y2 = polygonCoordinates.get((i + 1) % n).get(1);

      double distance = calculateDistanceToLineSegment(lon, lat, x1, y1, x2, y2);
      minDistance = Math.min(minDistance, distance);
    }

    return minDistance;
  }

  /** Calculate distance from a point to a LineString (in meters) */
  private double calculateDistanceToLineString(
      double lon, double lat, List<List<Double>> lineStringCoordinates) {
    if (lineStringCoordinates == null || lineStringCoordinates.size() < 2) {
      return Double.MAX_VALUE;
    }

    double minDistance = Double.MAX_VALUE;

    // Calculate distance to each segment of the LineString
    for (int i = 0; i < lineStringCoordinates.size() - 1; i++) {
      double x1 = lineStringCoordinates.get(i).get(0);
      double y1 = lineStringCoordinates.get(i).get(1);
      double x2 = lineStringCoordinates.get(i + 1).get(0);
      double y2 = lineStringCoordinates.get(i + 1).get(1);

      double distance = calculateDistanceToLineSegment(lon, lat, x1, y1, x2, y2);
      minDistance = Math.min(minDistance, distance);
    }

    return minDistance;
  }

  /**
   * Calculate distance from a point to a geodesic line segment (in meters) using GeographicLib.
   * Uses binary search to find the closest point on the geodesic segment.
   */
  private double calculateDistanceToLineSegment(
      double pointLon,
      double pointLat,
      double segLon1,
      double segLat1,
      double segLon2,
      double segLat2) {

    Geodesic geodesic = Geodesic.WGS84;

    // If segment endpoints are the same, return distance to that point
    if (segLon1 == segLon2 && segLat1 == segLat2) {
      GeodesicData result = geodesic.Inverse(pointLat, pointLon, segLat1, segLon1);
      return result.s12;
    }

    // Use binary search to find closest point on geodesic line segment
    // Sample points along the geodesic and find minimum distance
    double minDistance = Double.MAX_VALUE;
    int samples = 20; // Number of samples along the geodesic

    // Get distance and azimuth from start to end
    GeodesicData segData = geodesic.Inverse(segLat1, segLon1, segLat2, segLon2);
    double segmentLength = segData.s12;
    double azimuth = segData.azi1;

    // Sample points along the geodesic
    for (int i = 0; i <= samples; i++) {
      double fraction = (double) i / samples;
      double distanceAlongSegment = fraction * segmentLength;

      // Calculate point along geodesic using Direct method
      GeodesicData pointOnSegment =
          geodesic.Direct(segLat1, segLon1, azimuth, distanceAlongSegment);

      // Calculate distance from query point to this sampled point
      GeodesicData distanceData =
          geodesic.Inverse(pointLat, pointLon, pointOnSegment.lat2, pointOnSegment.lon2);
      minDistance = Math.min(minDistance, distanceData.s12);
    }

    return minDistance;
  }

  /** Calculate distance between two points in meters using Vincenty's Formulae */
  private double calculateDistanceInMeters(double lon1, double lat1, double lon2, double lat2) {
    Geodesic geodesic = Geodesic.WGS84;
    GeodesicData result = geodesic.Inverse(lat1, lon1, lat2, lon2);
    return result.s12; // Distance in meters
  }

  @Override
  public Double calculateAvailability(Inventory inventory, LocalDate startDate, LocalDate endDate) {
    if (inventory.getOperatingTimes() == null || inventory.getOperatingTimes().isEmpty()) {
      return 50.0; // Neutral if no operating times
    }

    // Check if inventory is Digital or Classic
    boolean isDigital = inventory.getDigitalFields() != null;

    if (isDigital) {
      return calculateDigitalAvailability(inventory, startDate, endDate);
    } else {
      return calculateClassicAvailability(inventory, startDate, endDate);
    }
  }

  /**
   * Calculate availability with pre-fetched cached booking data (Phase 1.5 optimization). Business
   * logic remains identical to calculateAvailability() above.
   */
  private Double calculateAvailability(
      Inventory inventory,
      LocalDate startDate,
      LocalDate endDate,
      Map<String, List<BookingData>> bookingDataCache) {

    if (inventory.getOperatingTimes() == null || inventory.getOperatingTimes().isEmpty()) {
      return 50.0;
    }

    boolean isDigital = inventory.getDigitalFields() != null;

    if (isDigital) {
      return calculateDigitalAvailability(inventory, startDate, endDate, bookingDataCache);
    } else {
      return calculateClassicAvailability(inventory, startDate, endDate, bookingDataCache);
    }
  }

  /**
   * Calculate availability for Digital inventory. Step 1: Identify operational days & hours for the
   * inventory Step 2: Calculate per hour availability percentage value for operational hours on
   * each date Step 3: Average hourly availability percentage value by each date Step 4: Average
   * date-wise averaged value for final availability percentage
   */
  private Double calculateDigitalAvailability(
      Inventory inventory, LocalDate startDate, LocalDate endDate) {
    // Fetch booking data for the date range
    List<BookingData> bookingDataList =
        bookingRepository.findByInventoryIdAndDateRange(
            inventory.getInventoryId(), startDate, endDate);

    return calculateDigitalAvailabilityFromData(inventory, startDate, endDate, bookingDataList);
  }

  /**
   * Calculate availability for Digital inventory with pre-fetched cached data (Phase 1.5
   * optimization).
   */
  private Double calculateDigitalAvailability(
      Inventory inventory,
      LocalDate startDate,
      LocalDate endDate,
      Map<String, List<BookingData>> bookingDataCache) {

    // Get booking data from cache instead of DB query
    List<BookingData> bookingDataList =
        bookingDataCache.getOrDefault(inventory.getInventoryId(), Collections.emptyList());

    return calculateDigitalAvailabilityFromData(inventory, startDate, endDate, bookingDataList);
  }

  /**
   * Shared calculation logic for digital availability (used by both cached and non-cached
   * versions).
   */
  private Double calculateDigitalAvailabilityFromData(
      Inventory inventory,
      LocalDate startDate,
      LocalDate endDate,
      List<BookingData> bookingDataList) {

    // Map booking data by date for quick lookup
    Map<LocalDate, BookingData> bookingDataByDate = new HashMap<>();
    for (BookingData bookingData : bookingDataList) {
      bookingDataByDate.put(bookingData.getDate(), bookingData);
    }

    // Step 1 & 2: Calculate per-hour availability for operational hours on each
    // date
    Map<LocalDate, List<Double>> hourlyAvailabilitiesByDate = new HashMap<>();

    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      Inventory.Weekday weekday = getWeekday(date);
      List<Inventory.OperatingTime> operatingTimes = inventory.getOperatingTimes().get(weekday);

      if (operatingTimes == null || operatingTimes.isEmpty()) {
        continue; // Skip non-operational days
      }

      // Use a Set to track which hours we've processed (to avoid duplicates from
      // overlapping
      // windows)
      Set<Integer> processedHours = new HashSet<>();
      List<Double> hourlyAvailabilities = new ArrayList<>();

      // For each operational time window, calculate hourly availability
      for (Inventory.OperatingTime operatingTime : operatingTimes) {
        LocalTime startTime = LocalTime.parse(operatingTime.getStart(), TIME_FORMATTER);
        LocalTime endTime = LocalTime.parse(operatingTime.getEnd(), TIME_FORMATTER);

        // Get the hour range for this operational window
        int startHour = startTime.getHour();
        int endHour = endTime.getHour() != 0 ? endTime.getHour() : 23;

        // If endTime has minutes/seconds, we still include that hour
        // (e.g., 17:30:00 means hour 17 is operational)

        // Iterate through each hour in the operational window
        for (int hour = startHour; hour <= endHour; hour++) {
          // Only process each hour once per date (even if it appears in multiple windows)
          if (processedHours.add(hour)) {
            String hourKey = String.valueOf(hour);

            // Get booked percentage for this hour
            double bookedPercentage =
                getBookedPercentageForHour(bookingDataByDate.get(date), hourKey);

            // Calculate availability percentage (100 - booked, min 0)
            double availabilityPercentage = Math.max(0.0, 100.0 - bookedPercentage);
            hourlyAvailabilities.add(availabilityPercentage);
          }
        }
      }

      if (!hourlyAvailabilities.isEmpty()) {
        hourlyAvailabilitiesByDate.put(date, hourlyAvailabilities);
      }
    }

    if (hourlyAvailabilitiesByDate.isEmpty()) {
      return 0.0; // No operational days in the date range
    }

    // Step 3: Average hourly availability percentage value by each date
    List<Double> dateAveragedAvailabilities = new ArrayList<>();
    for (Map.Entry<LocalDate, List<Double>> entry : hourlyAvailabilitiesByDate.entrySet()) {
      List<Double> hourlyAvailabilities = entry.getValue();
      double dateAverage =
          hourlyAvailabilities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
      dateAveragedAvailabilities.add(dateAverage);
    }

    // Step 4: Average date-wise averaged value for final availability percentage
    double finalAvailability =
        dateAveragedAvailabilities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

    return Math.max(0.0, Math.min(100.0, finalAvailability));
  }

  /**
   * Calculate availability for Classic inventory. Step 1: Identify operational days for the
   * inventory Step 2: Calculate per date availability percentage value for each date Step 3:
   * Average date-wise averaged value for final availability percentage
   */
  private Double calculateClassicAvailability(
      Inventory inventory, LocalDate startDate, LocalDate endDate) {
    // Fetch booking data for the date range
    List<BookingData> bookingDataList =
        bookingRepository.findByInventoryIdAndDateRange(
            inventory.getInventoryId(), startDate, endDate);

    return calculateClassicAvailabilityFromData(inventory, startDate, endDate, bookingDataList);
  }

  /**
   * Calculate availability for Classic inventory with pre-fetched cached data (Phase 1.5
   * optimization).
   */
  private Double calculateClassicAvailability(
      Inventory inventory,
      LocalDate startDate,
      LocalDate endDate,
      Map<String, List<BookingData>> bookingDataCache) {

    // Get booking data from cache instead of DB query
    List<BookingData> bookingDataList =
        bookingDataCache.getOrDefault(inventory.getInventoryId(), Collections.emptyList());

    return calculateClassicAvailabilityFromData(inventory, startDate, endDate, bookingDataList);
  }

  /**
   * Shared calculation logic for classic availability (used by both cached and non-cached
   * versions).
   */
  private Double calculateClassicAvailabilityFromData(
      Inventory inventory,
      LocalDate startDate,
      LocalDate endDate,
      List<BookingData> bookingDataList) {

    // Map booking data by date for quick lookup
    Map<LocalDate, BookingData> bookingDataByDate = new HashMap<>();
    for (BookingData bookingData : bookingDataList) {
      bookingDataByDate.put(bookingData.getDate(), bookingData);
    }

    // Step 1 & 2: Calculate per-date availability for operational days
    List<Double> dateAvailabilities = new ArrayList<>();

    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      Inventory.Weekday weekday = getWeekday(date);
      List<Inventory.OperatingTime> operatingTimes = inventory.getOperatingTimes().get(weekday);

      if (operatingTimes == null || operatingTimes.isEmpty()) {
        continue; // Skip non-operational days
      }

      // Get booked percentage for this date
      double bookedPercentage = getBookedPercentageForDate(bookingDataByDate.get(date));

      // Calculate availability percentage (100 - booked, min 0)
      double availabilityPercentage = Math.max(0.0, 100.0 - bookedPercentage);
      dateAvailabilities.add(availabilityPercentage);
    }

    if (dateAvailabilities.isEmpty()) {
      return 0.0; // No operational days in the date range
    }

    // Step 3: Average date-wise averaged value for final availability percentage
    double finalAvailability =
        dateAvailabilities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

    return Math.max(0.0, Math.min(100.0, finalAvailability));
  }

  /**
   * Get booked percentage for a specific hour from booking data.
   *
   * @param bookingData The booking data for the date (can be null)
   * @param hourKey The hour key (0-23 as String)
   * @return Total booked percentage for the hour
   */
  private double getBookedPercentageForHour(BookingData bookingData, String hourKey) {
    if (bookingData == null
        || bookingData.getHourlyBookings() == null
        || bookingData.getHourlyBookings().isEmpty()) {
      return 0.0; // No bookings for this date
    }

    List<BookingData.DealBooking> hourlyBookings = bookingData.getHourlyBookings().get(hourKey);
    if (hourlyBookings == null || hourlyBookings.isEmpty()) {
      return 0.0; // No bookings for this hour
    }

    // Sum all deal booking percentages for this hour
    return hourlyBookings.stream().mapToDouble(BookingData.DealBooking::getPercentage).sum();
  }

  /**
   * Get booked percentage for a specific date from booking data (for Classic inventory).
   *
   * @param bookingData The booking data for the date (can be null)
   * @return Total booked percentage for the date
   */
  private double getBookedPercentageForDate(BookingData bookingData) {
    if (bookingData == null
        || bookingData.getBooking() == null
        || bookingData.getBooking().isEmpty()) {
      return 0.0; // No bookings for this date
    }

    // Sum all deal booking percentages for this date
    return bookingData.getBooking().stream()
        .mapToDouble(BookingData.DealBooking::getPercentage)
        .sum();
  }

  @Override
  public Double calculateBudgetFit(
      Inventory inventory, LocalDate startDate, LocalDate endDate, BigDecimal budget) {

    if (budget == null || inventory.getPrices() == null || inventory.getPrices().isEmpty()) {
      return 50.0; // Neutral if no budget or pricing data
    }

    // Calculate estimated cost for the date range
    long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
    BigDecimal estimatedCost = BigDecimal.ZERO;

    // Use first available price model
    Inventory.PriceModel price = inventory.getPrices().getFirst();
    if (price.getMonthly() != null) {
      estimatedCost =
          BigDecimal.valueOf(price.getMonthly())
              .multiply(BigDecimal.valueOf(days))
              .divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
    } else if (price.getDaily() != null) {
      estimatedCost = BigDecimal.valueOf(price.getDaily()).multiply(BigDecimal.valueOf(days));
    } else if (price.getWeekly() != null) {
      estimatedCost =
          BigDecimal.valueOf(price.getWeekly())
              .multiply(BigDecimal.valueOf(days))
              .divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);
    }

    if (estimatedCost.compareTo(BigDecimal.ZERO) == 0) {
      return 50.0; // No cost estimate
    }

    // Calculate proportion of budget
    double proportion = estimatedCost.divide(budget, 4, RoundingMode.HALF_UP).doubleValue();

    // Map proportion to score
    if (proportion <= BUDGET_FIT_EXCELLENT) {
      return 95.0;
    } else if (proportion <= BUDGET_FIT_GOOD) {
      return 80.0;
    } else if (proportion <= BUDGET_FIT_FAIR) {
      return 60.0;
    } else if (proportion <= 1.0) {
      return 40.0;
    } else {
      return 20.0; // Exceeds budget
    }
  }

  @Override
  public Double calculateAudienceFit(
      AudienceData audienceData, RecommendationRequestDTO.AudienceTargeting targeting) {

    if (targeting == null) {
      return 50.0; // Neutral if no targeting
    }

    if (audienceData == null) {
      return 0.0; // No audience data
    }

    // Collect all scoring components
    List<Double> componentScores = new java.util.ArrayList<>();

    // 1. Score audience segments
    if (targeting.getAudienceSegments() != null && !targeting.getAudienceSegments().isEmpty()) {
      if (audienceData.getAudienceSegments() == null
          || audienceData.getAudienceSegments().isEmpty()) {
        componentScores.add(0.0); // No segments in data
      } else {
        int matchingSegments = 0;
        for (String targetSegment : targeting.getAudienceSegments()) {
          for (AudienceData.AudienceSegment segment : audienceData.getAudienceSegments()) {
            if (targetSegment.equalsIgnoreCase(segment.getSegmentName())) {
              matchingSegments++;
              break;
            }
          }
        }
        double segmentMatchRatio =
            (double) matchingSegments / targeting.getAudienceSegments().size();
        componentScores.add(segmentMatchRatio * 100.0);
      }
    }

    // 2. Score demographics (age, gender, income)
    if (targeting.getDemographics() != null && !targeting.getDemographics().isEmpty()) {
      Map<String, Double> demographicsData =
          (audienceData.getMonthlySummary() != null
                  && audienceData.getMonthlySummary().getDemographics() != null)
              ? audienceData.getMonthlySummary().getDemographics()
              : new HashMap<>();

      // Score age demographics
      if (targeting.getDemographics().containsKey("age")) {
        List<String> targetAges = targeting.getDemographics().get("age");
        if (targetAges != null && !targetAges.isEmpty()) {
          double ageScore = calculateAgeDemographicScore(targetAges, demographicsData);
          componentScores.add(ageScore);
        }
      }

      // Score gender demographics
      if (targeting.getDemographics().containsKey("gender")) {
        List<String> targetGenders = targeting.getDemographics().get("gender");
        if (targetGenders != null && !targetGenders.isEmpty()) {
          double genderScore = calculateGenderDemographicScore(targetGenders, demographicsData);
          componentScores.add(genderScore);
        }
      }

      // Score income demographics (from demographics map, same as age/gender)
      if (targeting.getDemographics().containsKey("income")) {
        List<String> targetIncomes = targeting.getDemographics().get("income");
        if (targetIncomes != null && !targetIncomes.isEmpty()) {
          double incomeScore = calculateIncomeDemographicScore(targetIncomes, demographicsData);
          componentScores.add(incomeScore);
        }
      }

      // Score interests demographics
      if (targeting.getDemographics().containsKey("interests")) {
        List<String> targetInterests = targeting.getDemographics().get("interests");
        if (targetInterests != null && !targetInterests.isEmpty()) {
          double interestsScore =
              calculateInterestsDemographicScore(targetInterests, demographicsData);
          componentScores.add(interestsScore);
        }
      }
    }

    // If no targeting criteria provided, return neutral score
    if (componentScores.isEmpty()) {
      return 50.0;
    }

    // Calculate average of all component scores
    double totalScore = componentScores.stream().mapToDouble(Double::doubleValue).sum();
    return totalScore / componentScores.size();
  }

  /**
   * Calculate age demographic score based on target age ranges and available demographics data.
   * Demographics keys are now normalized during message consumption to match request format (e.g.,
   * "18-24", "25-34"), so we can use direct lookups.
   */
  private double calculateAgeDemographicScore(
      List<String> targetAges, Map<String, Double> demographicsData) {
    if (targetAges == null || targetAges.isEmpty()) {
      return 50.0; // Neutral if no age targeting
    }

    if (demographicsData == null || demographicsData.isEmpty()) {
      return 0.0; // No demographic data available
    }

    int matchingAges = 0;
    for (String ageRange : targetAges) {
      // Normalize the age range key (remove spaces, ensure consistent format)
      String normalizedKey = ageRange.trim().replace(" ", "");

      // Direct lookup - keys are normalized during message consumption
      Double percentage = demographicsData.get(normalizedKey);
      if (percentage != null && percentage > 0.0) {
        matchingAges++;
      }
    }

    return ((double) matchingAges / targetAges.size()) * 100.0;
  }

  /**
   * Calculate gender demographic score based on target genders and available demographics data.
   * Demographics keys are now normalized during message consumption to match request format (e.g.,
   * "MALE", "FEMALE"), so we can use direct lookups.
   */
  private double calculateGenderDemographicScore(
      List<String> targetGenders, Map<String, Double> demographicsData) {
    if (targetGenders == null || targetGenders.isEmpty()) {
      return 50.0; // Neutral if no gender targeting
    }

    if (demographicsData == null || demographicsData.isEmpty()) {
      return 0.0; // No demographic data available
    }

    int matchingGenders = 0;
    for (String gender : targetGenders) {
      // Normalize gender key (uppercase, remove spaces)
      String normalizedKey = gender.trim().toUpperCase();

      // Direct lookup - keys are normalized during message consumption
      Double percentage = demographicsData.get(normalizedKey);
      if (percentage != null && percentage > 0.0) {
        matchingGenders++;
      }
    }

    return ((double) matchingGenders / targetGenders.size()) * 100.0;
  }

  /**
   * Calculate income demographic score based on target income brackets and demographics map.
   * Canonical values: Low, Lower-middle, Middle, Upper-middle, High. Keys are normalized during
   * message consumption to match request format.
   */
  private double calculateIncomeDemographicScore(
      List<String> targetIncomes, Map<String, Double> demographicsData) {
    if (targetIncomes == null || targetIncomes.isEmpty()) {
      return 50.0; // Neutral if no income targeting
    }

    if (demographicsData == null || demographicsData.isEmpty()) {
      return 0.0; // No demographic data available
    }

    int matchingIncomes = 0;
    for (String targetIncome : targetIncomes) {
      if (targetIncome == null) {
        continue;
      }
      String normalizedKey = normalizeIncomeKey(targetIncome);

      Double percentage = findDemographicsValueByKey(demographicsData, normalizedKey);
      if (percentage != null && percentage > 0.0) {
        matchingIncomes++;
      }
    }

    return ((double) matchingIncomes / targetIncomes.size()) * 100.0;
  }

  /**
   * Calculate interests demographic score based on target interests and demographics map. Canonical
   * values: Sports & Fitness, Technology, Food & Dining, Travel, Music, Entertainment, Home &
   * Garden, Health & Wellness, Automotive.
   */
  private double calculateInterestsDemographicScore(
      List<String> targetInterests, Map<String, Double> demographicsData) {
    if (targetInterests == null || targetInterests.isEmpty()) {
      return 50.0; // Neutral if no interests targeting
    }

    if (demographicsData == null || demographicsData.isEmpty()) {
      return 0.0; // No demographic data available
    }

    int matchingInterests = 0;
    for (String targetInterest : targetInterests) {
      if (targetInterest == null) {
        continue;
      }
      String normalizedKey = targetInterest.trim();

      Double percentage = findDemographicsValueByKey(demographicsData, normalizedKey);
      if (percentage != null && percentage > 0.0) {
        matchingInterests++;
      }
    }

    return ((double) matchingInterests / targetInterests.size()) * 100.0;
  }

  /** Normalize income key to canonical form (Low, Lower-middle, Middle, Upper-middle, High). */
  private String normalizeIncomeKey(String targetIncome) {
    if (targetIncome == null) {
      return null;
    }
    String trimmed = targetIncome.trim();
    return switch (trimmed.toLowerCase()) {
      case "low" -> "Low";
      case "lower-middle", "lower middle", "lowermiddle" -> "Lower-middle";
      case "middle", "mid" -> "Middle";
      case "upper-middle", "upper middle", "uppermiddle" -> "Upper-middle";
      case "high" -> "High";
      default -> trimmed; // Preserve if already canonical or unknown
    };
  }

  /**
   * Find demographics value by key; supports exact match and case-insensitive match for
   * request-style keys.
   */
  private Double findDemographicsValueByKey(Map<String, Double> demographicsData, String key) {
    if (key == null || demographicsData == null) {
      return null;
    }
    Double exact = demographicsData.get(key);
    if (exact != null) {
      return exact;
    }
    for (Map.Entry<String, Double> entry : demographicsData.entrySet()) {
      if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
        return entry.getValue();
      }
    }
    return null;
  }

  // FIXME: SRE member has made this change without any discussion with DEV team
  @Override
  public Double calculateBrandFit(
      Inventory inventory, String brandId, List<String> excludedIabCategories) {
    if (brandId != null) {
      BrandResponseDTO brandResponse = brandService.getBrandById(brandId).orElse(null);
      return calculateBrandFitFromData(inventory, brandResponse, excludedIabCategories);
    }
    return 100.0;
  }

  /**
   * Calculate brand fit with pre-fetched cached brand data (Phase 1.5 optimization). Business logic
   * remains identical to calculateBrandFit() above.
   */
  private Double calculateBrandFit(
      Inventory inventory,
      String brandId,
      List<String> excludedIabCategories,
      Map<String, BrandResponseDTO> brandDataCache) {

    if (brandId != null) {
      // Get brand data from cache instead of API call
      BrandResponseDTO brandResponse = brandDataCache.get(brandId);
      return calculateBrandFitFromData(inventory, brandResponse, excludedIabCategories);
    }
    return 100.0;
  }

  /** Shared calculation logic for brand fit (used by both cached and non-cached versions). */
  private Double calculateBrandFitFromData(
      Inventory inventory, BrandResponseDTO brandResponse, List<String> excludedIabCategories) {

    if (brandResponse != null) {
      // BrandResponseDTO might have category directly or via nested brand object
      // Try accessing category - the exact method depends on BrandResponseDTO
      // structure
      String brandCategory = null;
      try {
        // Try if BrandResponseDTO has getCategory() directly
        if (brandResponse.getCategory() != null) {
          brandCategory = brandResponse.getCategory();
        }
      } catch (Exception e) {
        // If getCategory() doesn't exist, try other approaches
        log.debug("Could not get category directly from BrandResponseDTO: {}", e.getMessage());
      }

      final String finalBrandCategory = brandCategory;
      if (inventory.getContentExclusions() != null
          && !inventory.getContentExclusions().isEmpty()
          && finalBrandCategory != null) {
        // Check if brand category matches any content exclusion name
        boolean isExcluded =
            inventory.getContentExclusions().stream()
                .anyMatch(exclusion -> finalBrandCategory.equals(exclusion.getName()));
        if (isExcluded) {
          return 0.0; // Match found, brand fit is 0
        } else {
          return 100.0; // No matches, brand fit is 100
        }
      } else {
        return 100.0; // No exclusions on inventory or no category on brand
      }
    } else {
      return 50.0; // Neutral if invalid brandId provided
    }
  }

  @Override
  public Double calculateQualityFit(Inventory inventory) {
    if (inventory.getQualityMetrics() != null
        && inventory.getQualityMetrics().getQualityScore() != null) {
      return inventory.getQualityMetrics().getQualityScore();
    }

    // Calculate quality score from available metrics
    double score = 50.0; // Base score

    // Factor in panel size
    if (inventory.getPanels() != null && !inventory.getPanels().isEmpty()) {
      Inventory.Panel panel = inventory.getPanels().getFirst();
      if (panel.getSize() != null) {
        switch (panel.getSize()) {
          case XL -> score += 20;
          case L -> score += 15;
          case M -> score += 10;
          case S -> score += 5;
          case XS -> score += 0;
        }
      }
    }

    // Factor in visibility
    if (inventory.getQualityMetrics() != null
        && inventory.getQualityMetrics().getVisibility() != null) {
      switch (inventory.getQualityMetrics().getVisibility().toLowerCase()) {
        case "high" -> score += 20;
        case "medium" -> score += 10;
        case "low" -> score += 0;
      }
    }

    return Math.min(100.0, score);
  }

  @Override
  public Double calculateTimeFit(
      AudienceData audienceData,
      LocalDate startDate,
      LocalDate endDate,
      Map<String, Double> daypartWeights) {

    if (audienceData == null
        || audienceData.getHourlySummary() == null
        || audienceData.getHourlySummary().isEmpty()) {
      return 50.0; // Neutral if no hourly data
    }

    // Calculate average frequency across all hours
    double totalFrequency = 0.0;
    int hourCount = 0;

    for (AudienceData.HourlySummary hourly : audienceData.getHourlySummary().values()) {
      if (hourly.getFrequency() != null) {
        totalFrequency += hourly.getFrequency();
        hourCount++;
      }
    }

    if (hourCount == 0) {
      return 50.0;
    }

    double avgFrequency = totalFrequency / hourCount;

    // Normalize frequency to 0-100 (assuming typical range 1-5)
    Double normalized = NormalizationUtils.normalize(avgFrequency, 1.0, 5.0);
    if (normalized == null) {
      return 50.0;
    }
    return normalized;
  }

  /** Calculate final weighted score from component scores */
  private Double calculateFinalScore(
      Double measureFit,
      Double geoFit,
      Double availability,
      Double budgetFit,
      Double audienceFit,
      Double brandFit,
      Double qualityFit,
      Double timeFit) {

    // Use redistributed weights if measure_fit is null
    boolean useRedistributed = (measureFit == null);

    double finalScore = 0.0;

    if (useRedistributed) {
      // Redistributed weights
      finalScore +=
          (ScoringWeights.RedistributedWeights.GEO_FIT_WEIGHT * (geoFit != null ? geoFit : 0.0));
      finalScore +=
          (ScoringWeights.RedistributedWeights.AVAILABILITY_WEIGHT
              * (availability != null ? availability : 0.0));
      finalScore +=
          (ScoringWeights.RedistributedWeights.BUDGET_FIT_WEIGHT
              * (budgetFit != null ? budgetFit : 0.0));
      finalScore +=
          (ScoringWeights.RedistributedWeights.AUDIENCE_FIT_WEIGHT
              * (audienceFit != null ? audienceFit : 0.0));
      finalScore +=
          (ScoringWeights.RedistributedWeights.BRAND_FIT_WEIGHT
              * (brandFit != null ? brandFit : 0.0));
      finalScore +=
          (ScoringWeights.RedistributedWeights.QUALITY_FIT_WEIGHT
              * (qualityFit != null ? qualityFit : 0.0));
      finalScore +=
          (ScoringWeights.RedistributedWeights.TIME_FIT_WEIGHT * (timeFit != null ? timeFit : 0.0));
    } else {
      // Default weights
      finalScore += ScoringWeights.MEASURE_FIT_WEIGHT * (measureFit != null ? measureFit : 0.0);
      finalScore += (ScoringWeights.GEO_FIT_WEIGHT * (geoFit != null ? geoFit : 0.0));
      finalScore +=
          (ScoringWeights.AVAILABILITY_WEIGHT * (availability != null ? availability : 0.0));
      finalScore += (ScoringWeights.BUDGET_FIT_WEIGHT * (budgetFit != null ? budgetFit : 0.0));
      finalScore +=
          (ScoringWeights.AUDIENCE_FIT_WEIGHT * (audienceFit != null ? audienceFit : 0.0));
      finalScore += (ScoringWeights.BRAND_FIT_WEIGHT * (brandFit != null ? brandFit : 0.0));
      finalScore += (ScoringWeights.QUALITY_FIT_WEIGHT * (qualityFit != null ? qualityFit : 0.0));
      finalScore += (ScoringWeights.TIME_FIT_WEIGHT * (timeFit != null ? timeFit : 0.0));
    }

    return Math.max(0.0, Math.min(100.0, finalScore));
  }

  /** Generate human-readable explanation for the score */
  private String generateExplanation(
      Double measureFit,
      Double geoFit,
      Double availability,
      Double budgetFit,
      Double audienceFit,
      Double brandFit,
      Double qualityFit,
      Inventory inventory) {

    StringBuilder explanation = new StringBuilder();

    if (geoFit != null && geoFit > 0) {
      if (inventory.getLocationHierarchy() != null
          && inventory.getLocationHierarchy().getCityName() != null) {
        explanation.append("Located in ").append(inventory.getLocationHierarchy().getCityName());
      }
    }

    if (measureFit != null && measureFit > 50) {
      explanation.append(" • Strong audience metrics");
    }

    if (availability != null && availability > 80) {
      explanation.append(" • High availability");
    }

    if (budgetFit != null && budgetFit > 80) {
      explanation.append(" • Budget-friendly");
    }

    return !explanation.isEmpty() ? explanation.toString() : "Standard inventory";
  }

  /** Get weekday from LocalDate */
  private Inventory.Weekday getWeekday(LocalDate date) {
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

  @Override
  public Double calculateRawSov(
      Inventory inventory, String country, LocalDate startDate, LocalDate endDate) {
    return calculateSOV(inventory, country, startDate, endDate);
  }

  @Override
  public Map<String, List<BookingData>> batchFetchBookingData(
      List<String> inventoryIds, LocalDate startDate, LocalDate endDate) {

    if (inventoryIds == null || inventoryIds.isEmpty()) {
      log.debug("No inventory IDs provided for batch booking fetch");
      return Collections.emptyMap();
    }

    long fetchStart = System.currentTimeMillis();

    // Single batch query instead of N individual queries
    List<BookingData> allBookings =
        bookingRepository.findByInventoryIdInAndDateRange(inventoryIds, startDate, endDate);

    long fetchDuration = System.currentTimeMillis() - fetchStart;

    // Group by inventoryId for O(1) lookup
    Map<String, List<BookingData>> bookingsByInventory =
        allBookings.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    BookingData::getInventoryId, java.util.stream.Collectors.toList()));

    log.info(
        "Batch fetched booking data for {} inventories in {}ms (found {} bookings across {} inventories)",
        inventoryIds.size(),
        fetchDuration,
        allBookings.size(),
        bookingsByInventory.size());

    // Overlay canonical IMS-synced availability (planner store). The canonical store is the
    // source of truth for sold-out/blocked windows, so where a record exists it replaces the
    // engine's own booking_data for that inventory. Disabled/unreachable → behaves as before.
    if (plannerAvailabilityService.isEnabled()) {
      List<Inventory> inventories = inventoryRepository.findByInventoryIdIn(inventoryIds);
      Map<String, List<BookingData>> canonical =
          plannerAvailabilityService.fetchBookingData(inventories, startDate, endDate);
      if (!canonical.isEmpty()) {
        Map<String, List<BookingData>> merged = new HashMap<>(bookingsByInventory);
        merged.putAll(canonical);
        return merged;
      }
    }

    return bookingsByInventory;
  }

  @Override
  public Map<String, BrandResponseDTO> batchFetchBrandData(List<String> brandIds) {

    if (brandIds == null || brandIds.isEmpty()) {
      log.debug("No brand IDs provided for batch brand fetch");
      return Collections.emptyMap();
    }

    // Remove duplicates
    Set<String> uniqueBrandIds = new HashSet<>(brandIds);

    long fetchStart = System.currentTimeMillis();
    Map<String, BrandResponseDTO> brandDataMap = new HashMap<>();

    // Fetch each unique brand (could be optimized with batch API if available)
    for (String brandId : uniqueBrandIds) {
      try {
        Optional<BrandResponseDTO> brandOpt = brandService.getBrandById(brandId);
        brandOpt.ifPresent(brand -> brandDataMap.put(brandId, brand));
      } catch (Exception e) {
        log.warn("Failed to fetch brand data for brandId {}: {}", brandId, e.getMessage());
      }
    }

    long fetchDuration = System.currentTimeMillis() - fetchStart;

    log.info(
        "Batch fetched brand data for {} unique brands in {}ms (found {} brands)",
        uniqueBrandIds.size(),
        fetchDuration,
        brandDataMap.size());

    return brandDataMap;
  }
}
