import type { InventoryItem } from "src/types/inventory.types";

import { getLatitude, getLongitude } from "./inventory.utils";

const EARTH_RADIUS_METERS = 6_371_000;

const toRad = (deg: number): number => (deg * Math.PI) / 180;

/**
 * Great-circle distance in meters between two lat/lng points.
 * Standard haversine formula.
 */
export const haversineMeters = (
  aLat: number,
  aLng: number,
  bLat: number,
  bLng: number,
): number => {
  const dLat = toRad(bLat - aLat);
  const dLng = toRad(bLng - aLng);
  const lat1 = toRad(aLat);
  const lat2 = toRad(bLat);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(h));
};

export const DEFAULT_POI_MATCH_THRESHOLD_METERS = 150;

/**
 * Generic nearest-neighbor matcher. Returns the item whose extracted
 * coordinates are closest to the POI and within thresholdMeters, or null.
 * Items whose extractor returns null are skipped.
 */
export const findClosestWithinThreshold = <T>(
  items: ReadonlyArray<T>,
  extractor: (item: T) => { lat: number; lng: number } | null | undefined,
  poi: { locationLat: number; locationLng: number },
  thresholdMeters: number = DEFAULT_POI_MATCH_THRESHOLD_METERS,
): T | null => {
  let best: { item: T; d: number } | null = null;
  for (const item of items) {
    const coords = extractor(item);
    if (!coords) continue;
    const d = haversineMeters(
      poi.locationLat,
      poi.locationLng,
      coords.lat,
      coords.lng,
    );
    if (d <= thresholdMeters && (best === null || d < best.d)) {
      best = { item, d };
    }
  }
  return best?.item ?? null;
};

/**
 * Returns the inventory item closest to the POI within thresholdMeters,
 * or null. Thin wrapper over findClosestWithinThreshold for the raw
 * InventoryItem shape (uses the standard getLatitude/getLongitude path).
 */
export const findInventoryForPOI = (
  inventory: ReadonlyArray<InventoryItem>,
  poi: { locationLat: number; locationLng: number },
  thresholdMeters: number = DEFAULT_POI_MATCH_THRESHOLD_METERS,
): InventoryItem | null =>
  findClosestWithinThreshold(
    inventory,
    (item) => {
      const lat = getLatitude(item.location?.location);
      const lng = getLongitude(item.location?.location);
      if (lat === undefined || lng === undefined) return null;
      return { lat, lng };
    },
    poi,
    thresholdMeters,
  );
