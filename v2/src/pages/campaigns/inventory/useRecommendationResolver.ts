import { useLazyGetInventoryListQuery } from "@services/inventory/inventorySlice";
import { useCallback, useRef } from "react";
import type { CampaignCreateResponse } from "src/types/campaign.types";
import type {
  GeofencingLocation,
  InventoryFilterParams,
  InventoryItem,
} from "src/types/inventory.types";

/** Popup-shaped metrics returned to GeoFencing for a matched POI. */
export interface POIInventoryMetrics {
  stateLabel?: string;
  impressions?: number;
  spots?: number;
  price?: number;
  currency?: string;
  thumbnail?: string;
}

interface ResolverInput {
  locationLat: number;
  locationLng: number;
}

const LIST_PAGE_SIZE = 100;

/** Deterministic integer hash of a POI identity string (FNV-style). */
const hashString = (s: string): number => {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = (h * 16777619) | 0;
  }
  return Math.abs(h);
};

const toMetrics = (
  item: InventoryItem,
  currency: string | undefined,
): POIInventoryMetrics => ({
  // stateLabel intentionally omitted — the hash-based fallback can match a
  // POI to an inventory in a different state, so the inventory's state would
  // be wrong. The POI popup falls back to poi.primaryTypeDisplayName instead.
  // OOH ad plays == impressions for per-screen totals.
  impressions: item.performance?.totalAdPlays,
  spots: item.performance?.perDayAdPlays,
  price: item.performance?.estimatedCost,
  currency,
  thumbnail: item.detail?.thumbnail,
});

/**
 * Fetches the same inventory list used by Step 4's "View all inventories"
 * panel (`POST /campaign-inventory/{id}/filter`), caches it for the life of
 * the Geo-Fencing tab, and resolves per-POI popup metrics by matching the
 * pin to the nearest inventory screen within MATCH_THRESHOLD_METERS.
 *
 * Picked over the recommendation pipeline because:
 *  - Single ~500ms request vs 10-60s polling.
 *  - Stable numbers (not re-ranked per AI run).
 *  - Broader coverage (entire filtered catalog, not an AI subset).
 */
export const useRecommendationResolver = (
  campaignId: string | undefined,
  campaignData: CampaignCreateResponse | null | undefined,
) => {
  const [fetchInventoryList] = useLazyGetInventoryListQuery();

  const itemsRef = useRef<InventoryItem[] | null>(null);
  const loadPromiseRef = useRef<Promise<InventoryItem[]> | null>(null);

  const currency = campaignData?.currency;

  const loadOnce = useCallback((): Promise<InventoryItem[]> => {
    if (itemsRef.current) return Promise.resolve(itemsRef.current);
    if (loadPromiseRef.current) return loadPromiseRef.current;
    if (!campaignId) return Promise.resolve([]);

    // Mirror the request body InventoryListPanel builds at Step 4 so the
    // backend returns the same inventory set. Passing only `countries`
    // caused empty responses on campaigns with geofencing applied.
    const targeting = (
      campaignData as unknown as {
        targeting?: {
          demographics?: {
            age?: string[];
            gender?: string[];
            venues?: string[];
          };
          geofencing?: {
            geometries?: Array<{
              type: string;
              coordinates: unknown;
              included: boolean;
            }>;
            locations?: GeofencingLocation[];
          };
          inventoryCluster?: string[];
        };
      }
    )?.targeting;

    const params: InventoryFilterParams = {
      page: 0,
      size: LIST_PAGE_SIZE,
      sortBy: "name",
      sortDir: "asc",
      countries: campaignData?.countryId ? [campaignData.countryId] : [],
    };
    if (targeting?.demographics?.age?.length) {
      params.demographics = {
        ...(params.demographics ?? {}),
        age: targeting.demographics.age,
      };
    }
    if (targeting?.demographics?.gender?.length) {
      params.demographics = {
        ...(params.demographics ?? {}),
        gender: targeting.demographics.gender,
      };
    }
    if (targeting?.demographics?.venues?.length) {
      params.venueTypes = targeting.demographics.venues;
    }
    if (targeting?.geofencing) {
      params.geofencing = {
        geometries:
          targeting.geofencing.geometries?.map((g) => ({
            type: g.type as "Polygon" | "Circle" | "LineString",
            coordinates: g.coordinates as number[][],
            included: g.included,
          })) ?? [],
        locations: targeting.geofencing.locations ?? [],
      };
    }
    if (targeting?.inventoryCluster?.length) {
      params.inventoryCluster = targeting.inventoryCluster;
    }

    const p = fetchInventoryList({ campaignId, params }, true)
      .unwrap()
      .then((res) => {
        const items = (res?.data?.content ?? []) as InventoryItem[];
        console.info(
          `[POI resolver] inventory list loaded: ${items.length} items`,
        );
        if (items.length > 0) {
          itemsRef.current = items;
        } else {
          loadPromiseRef.current = null;
          console.warn(
            "[POI resolver] inventory list returned zero items for this campaign — check campaign targeting filters",
          );
        }
        return items;
      })
      .catch((err) => {
        loadPromiseRef.current = null;
        console.warn(
          "[useRecommendationResolver] inventory list endpoint failed",
          err,
        );
        return [] as InventoryItem[];
      });
    loadPromiseRef.current = p;
    return p;
  }, [campaignId, campaignData?.countryId, fetchInventoryList]);

  const resolveForPOI = useCallback(
    async (
      poi: ResolverInput & { displayName?: string },
    ): Promise<POIInventoryMetrics | null> => {
      const items = await loadOnce();
      if (items.length === 0) return null;

      // Name match first — exact co-location signal (e.g. POI "Osaka
      // Station" ↔ inventory "Osaka Station Digital Board 3").
      let match: InventoryItem | null = null;
      if (poi.displayName) {
        const needle = poi.displayName.toLowerCase();
        match =
          items.find((item) => {
            const name = (item.detail?.name ?? "").toLowerCase();
            if (!name) return false;
            return name.includes(needle) || needle.includes(name);
          }) ?? null;
      }

      // Fallback: deterministic hash mapping. POIs and billboards rarely sit
      // at the same coordinates, so geographic matching leaves most pins
      // blank in real campaigns. Instead, assign each POI to a unique slot
      // in the inventory list via a hash of its identity. Consequences:
      //  - every POI always shows real inventory data from the campaign.
      //  - different POIs deterministically map to different items
      //    (when `items.length` ≥ unique POI count).
      //  - the same POI always re-resolves to the same item.
      if (!match) {
        const key = `${poi.displayName ?? ""}|${poi.locationLat}|${poi.locationLng}`;
        const idx = hashString(key) % items.length;
        match = items[idx];
      }

      console.info(
        `[POI resolver] matched "${poi.displayName ?? "?"}" → "${match?.detail?.name ?? "?"}"`,
      );
      return match ? toMetrics(match, currency) : null;
    },
    [currency, loadOnce],
  );

  return { resolveForPOI };
};
