import { getLatitude, getLongitude } from "@utils/inventory.utils";
import type { Map as MapboxMap, GeoJSONSource } from "mapbox-gl";

import type { MapInventoryItem } from "../../../types/inventory.types";

export interface MediaPlanMapView {
  center: [number, number]; // [lng, lat]
  zoom: number;
}

// Neutral whole-world framing. Used when the plan has no parseable
// coordinates — showing a specific country we didn't derive (the old Tokyo
// fallback) reads as wrong data to the user (SI 41).
export const WORLD_VIEW: MediaPlanMapView = {
  center: [0, 20],
  zoom: 1.5,
};

const DETAIL_ZOOM = 8;

const collectValidCoordinates = (
  locations: MapInventoryItem[],
): Array<{ lat: number; lng: number }> => {
  const validCoordinates: Array<{ lat: number; lng: number }> = [];

  locations.forEach((item) => {
    const lat = getLatitude(item.location?.location);
    const lng = getLongitude(item.location?.location);

    if (lat !== undefined && lng !== undefined && !isNaN(lat) && !isNaN(lng)) {
      validCoordinates.push({ lat, lng });
    }
  });

  return validCoordinates;
};

export const computeMediaPlanMapView = (
  locations: MapInventoryItem[],
): MediaPlanMapView => {
  const validCoordinates = collectValidCoordinates(locations);

  if (validCoordinates.length === 0) {
    return WORLD_VIEW;
  }

  const sumLat = validCoordinates.reduce((sum, coord) => sum + coord.lat, 0);
  const sumLng = validCoordinates.reduce((sum, coord) => sum + coord.lng, 0);

  return {
    center: [
      sumLng / validCoordinates.length,
      sumLat / validCoordinates.length,
    ],
    zoom: DETAIL_ZOOM,
  };
};

export interface MediaPlanMapBounds {
  sw: [number, number]; // [lng, lat]
  ne: [number, number]; // [lng, lat]
}

/**
 * Bounding box of every valid coordinate, for fitBounds — computeMediaPlanMapView's
 * centroid + fixed zoom only frames a ~city-scale area, so inventories spread across
 * a wider region (e.g. multiple states) fall outside the frame entirely. Returns null
 * when there are fewer than 2 distinct points — fitBounds on a zero-size box zooms to
 * the map's max zoom, which isn't useful for a single marker (computeMediaPlanMapView's
 * centroid + fixed zoom already frames that case fine).
 */
export const computeMediaPlanMapBounds = (
  locations: MapInventoryItem[],
): MediaPlanMapBounds | null => {
  const validCoordinates = collectValidCoordinates(locations);

  if (validCoordinates.length === 0) return null;

  const lats = validCoordinates.map((coord) => coord.lat);
  const lngs = validCoordinates.map((coord) => coord.lng);
  const sw: [number, number] = [Math.min(...lngs), Math.min(...lats)];
  const ne: [number, number] = [Math.max(...lngs), Math.max(...lats)];

  if (sw[0] === ne[0] && sw[1] === ne[1]) return null;

  return { sw, ne };
};

/** Count of inventory items that have a plottable coordinate. */
export const countPlottedSites = (locations: MapInventoryItem[]): number =>
  collectValidCoordinates(locations).length;

/** POI place with a plottable coordinate (Google Places lat/lng). */
interface PoiCoord {
  locationLat?: number;
  locationLng?: number;
}

/**
 * Fit the map to every plotted pin — inventory sites AND geofencing POIs, so a
 * plan whose inventory lacks coordinates (but has POIs) still frames correctly
 * instead of falling back to the whole-world view. Same framing as the
 * inventory manual-edit map (padding 60, maxZoom 15). A single point zooms to 15.
 */
export const fitMapToInventory = (
  map: MapboxMap,
  locations: MapInventoryItem[],
  pois: PoiCoord[] = [],
): void => {
  const coords = collectValidCoordinates(locations);
  pois.forEach((p) => {
    const lat = p.locationLat;
    const lng = p.locationLng;
    if (
      typeof lat === "number" &&
      typeof lng === "number" &&
      !isNaN(lat) &&
      !isNaN(lng)
    ) {
      coords.push({ lat, lng });
    }
  });
  if (coords.length === 0) return;
  const lngs = coords.map((c) => c.lng);
  const lats = coords.map((c) => c.lat);
  try {
    map.fitBounds(
      [
        [Math.min(...lngs), Math.min(...lats)],
        [Math.max(...lngs), Math.max(...lats)],
      ],
      { padding: 60, maxZoom: 15, duration: 500 },
    );
  } catch (error) {
    console.error("Failed to fit media-plan map to inventory:", error);
  }
};

const RADIUS_SOURCE_ID = "mp-inventory-radius";
const RADIUS_LAYER_ID = "mp-inventory-radius-fill";

/** Coverage radius in metres by inventory environment. */
const OUTDOOR_RADIUS_M = 250;
const INDOOR_RADIUS_M = 50;

// Indoor → 50 m; everything else (outdoor, semi-outdoor, in-transit, and
// missing/unknown environments) falls back to the outdoor 250 m radius.
const radiusForEnvironment = (environment?: string): number =>
  environment && environment.toLowerCase().includes("indoor")
    ? INDOOR_RADIUS_M
    : OUTDOOR_RADIUS_M;

/** Approximate a circle of `radiusM` metres around [lng,lat] as a polygon ring. */
const circleRing = (
  lng: number,
  lat: number,
  radiusM: number,
  steps = 64,
): number[][] => {
  // Metres → degrees (local flat-earth approximation, fine at these radii).
  const dLng = radiusM / (111320 * Math.cos((lat * Math.PI) / 180));
  const dLat = radiusM / 110574;
  const ring: number[][] = [];
  for (let i = 0; i < steps; i++) {
    const theta = (i / steps) * 2 * Math.PI;
    ring.push([lng + dLng * Math.cos(theta), lat + dLat * Math.sin(theta)]);
  }
  ring.push(ring[0]);
  return ring;
};

type RadiusFeatureCollection = GeoJSON.FeatureCollection<GeoJSON.Polygon>;

/**
 * Build a coverage-radius polygon per plotted inventory: 250 m for outdoor
 * environments, 50 m otherwise. Environment lives on the runtime InventoryItem
 * detail (not typed on MapInventoryItem), so it's read defensively.
 */
export const buildInventoryRadiusData = (
  locations: MapInventoryItem[],
): RadiusFeatureCollection => {
  const features: GeoJSON.Feature<GeoJSON.Polygon>[] = [];
  locations.forEach((item) => {
    const lat = getLatitude(item.location?.location);
    const lng = getLongitude(item.location?.location);
    if (lat === undefined || lng === undefined || isNaN(lat) || isNaN(lng)) {
      return;
    }
    const environment = (item.detail as { environment?: string } | undefined)
      ?.environment;
    features.push({
      type: "Feature",
      geometry: {
        type: "Polygon",
        coordinates: [circleRing(lng, lat, radiusForEnvironment(environment))],
      },
      properties: {},
    });
  });
  return { type: "FeatureCollection", features };
};

/**
 * Add (or refresh) an inventory coverage-radius layer: a translucent blue fill
 * (80% transparent) around each site. Deferred until the style has loaded.
 */
export const addInventoryRadiusLayer = (
  map: MapboxMap,
  locations: MapInventoryItem[],
): void => {
  const data = buildInventoryRadiusData(locations);
  if (data.features.length === 0) return;

  const apply = () => {
    const existing = map.getSource(RADIUS_SOURCE_ID) as
      | GeoJSONSource
      | undefined;
    if (existing) {
      existing.setData(data);
      return;
    }
    map.addSource(RADIUS_SOURCE_ID, { type: "geojson", data });
    map.addLayer({
      id: RADIUS_LAYER_ID,
      type: "fill",
      source: RADIUS_SOURCE_ID,
      paint: {
        "fill-color": "rgb(37,99,235)",
        "fill-opacity": 0.2,
        "fill-outline-color": "rgba(37,99,235,0.6)",
      },
    });
  };

  if (map.isStyleLoaded()) {
    apply();
  } else {
    map.once("load", apply);
  }
};
