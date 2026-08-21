package com.mw.recommendation.engine.v3.support;

import java.util.List;

/**
 * Self-contained geo helpers for v3 scoring: haversine distance (± 0.5% vs geodesic — ample for
 * 0-100 scoring buckets), ray-casting point-in-polygon, and point-to-polygon-edge distance.
 * Coordinates are [lng, lat] pairs, matching the GeoJSON convention used by the inventory data.
 */
public final class GeoMath {

  private static final double EARTH_RADIUS_M = 6_371_000.0;

  private GeoMath() {}

  /**
   * Extracts [lat, lng] from an inventory's locationCoordinates ({@code GeoJsonPoint} or {@code
   * GeoJsonLineString} — first point). Returns null when unusable.
   */
  public static double[] latLng(Object locationCoordinates) {
    if (locationCoordinates
        instanceof org.springframework.data.mongodb.core.geo.GeoJsonPoint point) {
      return new double[] {point.getY(), point.getX()};
    }
    if (locationCoordinates
            instanceof org.springframework.data.mongodb.core.geo.GeoJsonLineString line
        && !line.getCoordinates().isEmpty()) {
      var first = line.getCoordinates().get(0);
      return new double[] {first.getY(), first.getX()};
    }
    return null;
  }

  public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2)
                * Math.sin(dLng / 2);
    return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  /** Ray-casting containment test. Polygon points are [lng, lat]. */
  public static boolean pointInPolygon(double lat, double lng, List<List<Double>> polygon) {
    boolean inside = false;
    int n = polygon.size();
    for (int i = 0, j = n - 1; i < n; j = i++) {
      double xi = polygon.get(i).get(0);
      double yi = polygon.get(i).get(1);
      double xj = polygon.get(j).get(0);
      double yj = polygon.get(j).get(1);
      boolean intersects =
          ((yi > lat) != (yj > lat)) && (lng < (xj - xi) * (lat - yi) / (yj - yi) + xi);
      if (intersects) {
        inside = !inside;
      }
    }
    return inside;
  }

  /** Minimum distance from a point to any polygon edge, via segment sampling. */
  public static double distanceToPolygonEdgeMeters(
      double lat, double lng, List<List<Double>> polygon) {
    double min = Double.MAX_VALUE;
    int n = polygon.size();
    for (int i = 0, j = n - 1; i < n; j = i++) {
      min =
          Math.min(
              min,
              distanceToSegmentMeters(
                  lat,
                  lng,
                  polygon.get(j).get(1),
                  polygon.get(j).get(0),
                  polygon.get(i).get(1),
                  polygon.get(i).get(0)));
    }
    return min;
  }

  private static double distanceToSegmentMeters(
      double lat, double lng, double lat1, double lng1, double lat2, double lng2) {
    // 20-point sampling along the segment — matches the v1 approach and is exact enough
    // for the R1/R2 score bands.
    double min = Double.MAX_VALUE;
    for (int i = 0; i <= 20; i++) {
      double t = i / 20.0;
      double sampleLat = lat1 + t * (lat2 - lat1);
      double sampleLng = lng1 + t * (lng2 - lng1);
      min = Math.min(min, distanceMeters(lat, lng, sampleLat, sampleLng));
    }
    return min;
  }
}
