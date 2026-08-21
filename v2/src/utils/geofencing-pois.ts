import type {
  CampaignCreateResponse,
  POIPlaceData,
} from "src/types/campaign.types";

interface PoiSourceEntry {
  poi?: string[];
  metadata?: Record<string, string>;
}

/**
 * Extract the POI places from a campaign's geofencing targeting (locations +
 * geometries), parsing the JSON stored in each entry's metadata.
 */
export function extractGeofencingPOIs(
  campaignData: CampaignCreateResponse | null | undefined,
): POIPlaceData[] {
  const geofencing = campaignData?.targeting?.geofencing;
  if (!geofencing) return [];

  const pois: POIPlaceData[] = [];
  const collect = (source?: PoiSourceEntry[]) => {
    source?.forEach((entry) => {
      if (!entry.poi?.length || !entry.metadata) return;
      entry.poi.forEach((poi) => {
        const raw = entry.metadata?.[poi];
        if (!raw) return;
        try {
          const parsed = JSON.parse(raw);
          if (parsed.places?.length) pois.push(...parsed.places);
        } catch {
          // ignore malformed metadata
        }
      });
    });
  };

  collect(geofencing.locations as never);
  collect(geofencing.geometries as never);
  return pois;
}

/**
 * Ray-casting point-in-polygon test.
 *
 * Google Places only supports a circular `locationRestriction`, so POIs for a
 * polygon are fetched within its circumscribing circle and then narrowed to
 * the actual shape here.
 *
 * @param point Longitude/latitude pair as `[lng, lat]`.
 * @param ring  Polygon outer ring as `[[lng, lat], ...]` (same order the map
 *              stores geometry coordinates in).
 */
export function isPointInPolygon(
  point: [number, number],
  ring: number[][],
): boolean {
  if (!ring || ring.length < 3) return false;
  const [x, y] = point;
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const xi = ring[i][0];
    const yi = ring[i][1];
    const xj = ring[j][0];
    const yj = ring[j][1];
    const intersect =
      yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi;
    if (intersect) inside = !inside;
  }
  return inside;
}

/**
 * Keep only the POI places whose coordinates fall inside the polygon ring.
 * Returns the input unchanged when the ring is missing/degenerate (fewer than
 * 3 points) so callers can pass it through safely for non-polygon geometry.
 */
export function filterPOIPlacesInsidePolygon<
  T extends { locationLat: number; locationLng: number },
>(places: T[], ring: number[][] | undefined | null): T[] {
  if (!ring || ring.length < 3) return places;
  return places.filter((p) =>
    isPointInPolygon([p.locationLng, p.locationLat], ring),
  );
}
