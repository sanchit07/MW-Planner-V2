package com.mw.recommendation.engine.v3.scoring;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import com.mw.recommendation.engine.v3.support.GeoMath;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * geoFit per PRD §5.4:
 *
 * <ul>
 *   <li>Inside a geofence → 100 (PRD; v1 scored 90 here)
 *   <li>Within R1 of the boundary → 90
 *   <li>Distance decay to R2: max(0, 100 × (1 − (d−R1)/(R2−R1)))
 *   <li>POIs → weighted min-distance (distance ÷ weight before banding)
 *   <li>Channel match (e.g. "airport") → +10 bonus, capped at 100
 *   <li>No geography → 100 only for the top audience-concentration cities (computed per run from
 *       Measure-synced audience data), 70 for other known cities, 50 when unknown — v1 blanket-100
 *       is replaced by the PRD's "favor high audience areas" rule
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class GeoFitCalculator {

  private static final double OTHER_CITY_SCORE = 70.0;
  private static final double UNKNOWN_CITY_SCORE = 50.0;

  private final V3Properties props;

  /**
   * @param topRegions top audience-concentration city names (lowercase), used when no geography
   *     targeting was supplied; empty set means "no audience data" → neutral for everyone.
   */
  public Double calculate(
      Inventory inventory, RecommendationV3RequestDTO request, Set<String> topRegions) {

    RecommendationV3RequestDTO.GeographyTargeting geo = request.getGeographyTargeting();
    boolean hasTargeting =
        geo != null
            && ((geo.getCities() != null && !geo.getCities().isEmpty())
                || (geo.getStates() != null && !geo.getStates().isEmpty())
                || (geo.getGeofences() != null && !geo.getGeofences().isEmpty())
                || (geo.getPois() != null && !geo.getPois().isEmpty()));

    double score;
    if (!hasTargeting) {
      score = noTargetingScore(inventory, topRegions);
    } else {
      score = targetedScore(inventory, geo);
    }

    // Channel bonus (PRD §5.4): requested channel matching classification/type/venueTypes
    if (score > 0 && channelMatches(inventory, request.getChannels())) {
      score = Math.min(100.0, score + props.getGeo().getChannelBonus());
    }
    return score;
  }

  private double noTargetingScore(Inventory inventory, Set<String> topRegions) {
    if (topRegions == null || topRegions.isEmpty()) {
      return UNKNOWN_CITY_SCORE; // no audience data to rank regions — neutral
    }
    String city =
        inventory.getLocationHierarchy() != null
            ? inventory.getLocationHierarchy().getCityName()
            : null;
    if (city == null) {
      return UNKNOWN_CITY_SCORE;
    }
    return topRegions.contains(city.toLowerCase(Locale.ROOT)) ? 100.0 : OTHER_CITY_SCORE;
  }

  private double targetedScore(
      Inventory inventory, RecommendationV3RequestDTO.GeographyTargeting geo) {

    // Exact city match → 100; state match → 95
    if (inventory.getLocationHierarchy() != null) {
      String city = inventory.getLocationHierarchy().getCityName();
      if (city != null
          && geo.getCities() != null
          && geo.getCities().stream().anyMatch(c -> c.equalsIgnoreCase(city))) {
        return 100.0;
      }
      String state = inventory.getLocationHierarchy().getStateName();
      if (state != null
          && geo.getStates() != null
          && geo.getStates().stream().anyMatch(s -> s.equalsIgnoreCase(state))) {
        return 95.0;
      }
    }

    double[] latLng = GeoMath.latLng(inventory.getLocationCoordinates());
    if (latLng == null) {
      return 0.0;
    }
    double lat = latLng[0];
    double lng = latLng[1];

    double minEffectiveDistance = Double.MAX_VALUE;
    boolean inside = false;

    if (geo.getGeofences() != null) {
      for (RecommendationV3RequestDTO.Geofence fence : geo.getGeofences()) {
        if ("Polygon".equalsIgnoreCase(fence.getType())
            && fence.getCoordinates() != null
            && fence.getCoordinates().size() >= 3) {
          if (GeoMath.pointInPolygon(lat, lng, fence.getCoordinates())) {
            inside = true;
            break;
          }
          minEffectiveDistance =
              Math.min(
                  minEffectiveDistance,
                  GeoMath.distanceToPolygonEdgeMeters(lat, lng, fence.getCoordinates()));
        } else if ("Circle".equalsIgnoreCase(fence.getType())
            && fence.getCenterLat() != null
            && fence.getCenterLng() != null) {
          double radius =
              fence.getRadiusMeters() != null && fence.getRadiusMeters() > 0
                  ? fence.getRadiusMeters()
                  : props.getGeo().getDefaultCircleRadiusMeters();
          double distance =
              GeoMath.distanceMeters(lat, lng, fence.getCenterLat(), fence.getCenterLng());
          if (distance <= radius) {
            inside = true;
            break;
          }
          minEffectiveDistance = Math.min(minEffectiveDistance, distance - radius);
        }
      }
    }
    if (inside) {
      return 100.0; // PRD: inside geofence = 100
    }

    // POIs: weighted min-distance — distance shrunk by the POI weight before banding
    if (geo.getPois() != null) {
      for (RecommendationV3RequestDTO.Poi poi : geo.getPois()) {
        if (poi.getLat() == null || poi.getLng() == null) {
          continue;
        }
        double weight = poi.getWeight() != null && poi.getWeight() > 0 ? poi.getWeight() : 1.0;
        double distance = GeoMath.distanceMeters(lat, lng, poi.getLat(), poi.getLng()) / weight;
        minEffectiveDistance = Math.min(minEffectiveDistance, distance);
      }
    }

    if (minEffectiveDistance == Double.MAX_VALUE) {
      return 0.0; // targeted elsewhere, no distance basis
    }
    return bandByDistance(minEffectiveDistance);
  }

  /**
   * PRD distance bands: ≤R1 → 90; otherwise the PRD §5.4 decay {@code max(0, 100 × (1 − d/R2))}
   * (worked example: 10 km with R2=50 km → 80), zero beyond R2.
   */
  private double bandByDistance(double distanceMeters) {
    double r1 = props.getGeo().getR1Meters();
    double r2 = props.getGeo().getR2Meters();
    if (distanceMeters <= r1) {
      return 90.0;
    }
    if (distanceMeters <= r2) {
      return Math.max(0.0, 100.0 * (1 - distanceMeters / r2));
    }
    return 0.0;
  }

  private static boolean channelMatches(Inventory inventory, List<String> channels) {
    if (channels == null || channels.isEmpty()) {
      return false;
    }
    for (String channel : channels) {
      if (channel == null || channel.isBlank()) {
        continue;
      }
      String needle = channel.toLowerCase(Locale.ROOT);
      if (containsIgnoreCase(inventory.getClassification(), needle)
          || containsIgnoreCase(inventory.getType(), needle)) {
        return true;
      }
      if (inventory.getVenueTypes() != null
          && inventory.getVenueTypes().stream().anyMatch(v -> containsIgnoreCase(v, needle))) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsIgnoreCase(String haystack, String needleLower) {
    return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needleLower);
  }
}
