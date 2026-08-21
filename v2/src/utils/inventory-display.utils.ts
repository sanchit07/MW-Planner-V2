import type {
  InventoryDisplayItem,
  InventoryItem,
  InventoryOperations,
  InventoryRecommendationItem,
  LatLong,
  Panels,
} from "src/types/inventory.types";

// How many venue types the inventory cards show inline before collapsing the
// rest into a "+N" overflow chip.
export const MAX_VISIBLE_VENUE_TYPES = 2;

export interface ChipOverflow {
  /** First `max` labels joined for the inline chip. */
  visibleText: string;
  /** How many labels are collapsed into the "+N" suffix. */
  overflowCount: number;
  /** All labels joined — shown in the overflow tooltip. */
  allText: string;
  /** All (cleaned) labels — e.g. to render the tooltip one per line. */
  labels: string[];
  /** Total label count (0 → render no chip). */
  count: number;
}

/**
 * Computes the inline / "+N" / tooltip parts for a list of chip labels, so the
 * "first N inline, rest collapsed with a hover tooltip" pattern is shared across
 * the inventory cards instead of duplicated per chip.
 */
export const getChipOverflow = (
  labels: string[] | null | undefined,
  max: number,
): ChipOverflow => {
  const clean = (Array.isArray(labels) ? labels : []).filter(Boolean);
  return {
    visibleText: clean.slice(0, max).join(", "),
    overflowCount: Math.max(0, clean.length - max),
    allText: clean.join(", "),
    labels: clean,
    count: clean.length,
  };
};

/**
 * Converts an InventoryItem to the common InventoryDisplayItem shape for unified display.
 * Maps detail, location, performance, operations, and schedules for card/drawer/list display.
 */
export function fromInventoryItem(item: InventoryItem): InventoryDisplayItem {
  const loc = item.location?.location;
  return {
    source: "inventory",
    detail: {
      id: item.detail?.id ?? "",
      referenceId: item.detail?.referenceId ?? "",
      name: item.detail?.name ?? "",
      thumbnail: item.detail?.thumbnail ?? "",
      images: item.detail?.images ?? [],
      inventoryType: item.detail?.inventoryType ?? "",
      category: item.detail?.category ?? "",
      venueTypes: item.detail?.venueType ?? [],
      format: item.detail?.format ?? "",
      environment: item.detail?.environment ?? "",
      mediaOwnerId: item.detail?.mediaOwnerId ?? "",
      mediaOwnerName: item.detail?.mediaOwnerName ?? "",
      panels: item.detail?.panels ?? [],
      screens: item.detail?.screens,
      sov: item.detail?.sov,
      isSelected: item.detail?.isSelected,
      isCompliant: item.detail?.isCompliant,
      size: item.detail?.size,
      cinemaFields: item.detail?.cinemaFields,
    },
    location: {
      location: {
        address: loc?.address ?? "",
        country: loc?.country ?? "",
        state: loc?.state ?? "",
        city: loc?.city ?? "",
        zipCode: loc?.zipCode,
        locationCoordinates: loc?.locationCoordinates,
      },
    },
    performance: {
      cpmRate: item.performance?.cpmRate ?? 0,
      spotRate: item.performance?.spotRate,
      estimatedCost: item.performance?.estimatedCost ?? 0,
      perDayCost: item.performance?.perDayCost ?? 0,
      perDayAdPlays: item.performance?.perDayAdPlays ?? 0,
      totalAdPlays: item.performance?.totalAdPlays ?? 0,
      plannedSot: item.performance?.plannedSot ?? 0,
      totalSot: item.performance?.totalSot ?? 0,
      // /filter response carries these for most digital inventory (raw field
      // is singular `estimatedImpression`); absent for some — left undefined.
      estimatedReach: item.performance?.estimatedReach ?? undefined,
      estimatedFrequency: item.performance?.estimatedFrequency ?? undefined,
      estimatedImpressions: item.performance?.estimatedImpression ?? undefined,
    },
    operations: item.operations,
    schedules: item.schedules ?? [],
    originalInventoryItem: item,
  };
}

/**
 * Converts an InventoryRecommendationItem to the common InventoryDisplayItem shape for unified display.
 * Maps inventoryDetails + top-level fields to the same detail/location/performance shape as inventory items,
 * plus recommendationInfo for score and component scores.
 */
export function fromRecommendationItem(
  item: InventoryRecommendationItem,
): InventoryDisplayItem {
  const details = item.inventoryDetails ?? {};
  const loc = details.location ?? {};
  const perf = item.performance ?? {};
  const address =
    details.address ||
    [loc.cityName, loc.stateName, loc.countryName].filter(Boolean).join(", ") ||
    "—";

  return {
    source: "recommendation",
    detail: {
      id: details.internalId ?? item.inventoryId ?? "",
      referenceId: item.referenceId ?? "",
      name: item.name ?? details.name ?? "",
      thumbnail: item.inventoryDetails?.thumbnailUrl ?? "",
      images: [],
      inventoryType: details.classification ?? "",
      category: details.type ?? "",
      venueTypes: details.venueTypes ?? [],
      format: details.format ?? "",
      environment: details.environment ?? "",
      mediaOwnerId: details.mediaOwnerId ?? "",
      mediaOwnerName: details.mediaOwnerName ?? "",
      // /results carries sizes as a flat string[] (no panel concept). Kept
      // mapped into panels for other consumers (e.g. media-plan transforms);
      // the size badge itself reads the flat `size` below.
      panels: (details.sizes ?? []).map((size) => ({ size }) as Panels),
      screens: undefined,
      sov: perf.sov ?? undefined,
      // Recommendations can carry multiple sizes; the badge shows one, so use
      // the first (smallest, per the API's own ordering).
      size: details.sizes?.[0],
    },
    location: {
      location: {
        address,
        country: loc.countryName ?? "",
        state: loc.stateName ?? "",
        city: loc.cityName ?? "",
        zipCode: undefined,
        locationCoordinates:
          loc.locationCoordinates?.coordinates?.length >= 2
            ? {
                type: loc.locationCoordinates.type ?? "",
                coordinates: [
                  {
                    longitude: loc.locationCoordinates.coordinates[0],
                    latitude: loc.locationCoordinates.coordinates[1],
                  } as LatLong,
                ],
              }
            : undefined,
      },
    },
    performance: {
      estimatedReach: perf.estimatedReach ?? undefined,
      estimatedFrequency: perf.estimatedFrequency ?? undefined,
      estimatedImpressions: perf.estimatedImpressions ?? undefined,
      cpmRate: perf.cpmRate ?? undefined,
      spotRate: perf.spotRate ?? undefined,
      estimatedCost: perf.estimatedCost ?? 0,
      perDayCost: perf.perDayCost ?? undefined,
      perDayAdPlays: perf.perDayAdPlays ?? undefined,
      totalAdPlays: perf.totalAdPlays ?? undefined,
      plannedSot: perf.plannedSot ?? undefined,
      totalSot: perf.totalSot ?? undefined,
    },
    operations: details.digitalFields
      ? ({
          slotDuration: details.digitalFields.spotDuration ?? 1,
          clientPerLoop: details.digitalFields.spotsPerLoop ?? 1,
        } as InventoryOperations)
      : undefined,
    schedules: [],
    recommendationInfo: {
      finalScore: item.finalScore ?? 0,
      why: item.why ?? "",
      selectionMode: item.selectionMode ?? "",
      isExcluded: item.isExcluded ?? false,
      componentScores: item.componentScores ?? {
        geoFit: 0,
        availability: 0,
        budgetFit: 0,
        audienceFit: 0,
        brandFit: 0,
        qualityFit: 0,
        timeFit: 0,
        measureFit: 0,
      },
      availability: item.availability ?? {
        availableDays: 0,
        totalDays: 0,
        availabilityPercentage: 0,
        summary: "",
        allAvailable: false,
      },
    },
    originalRecommendationItem: item,
  };
}

/**
 * Converts either an InventoryItem or an InventoryRecommendationItem to the common InventoryDisplayItem.
 * Use this wherever inventory is shown (cards, lists, map popups) for a single unified type.
 */
export function toInventoryDisplayItem(
  item: InventoryItem | InventoryRecommendationItem,
): InventoryDisplayItem {
  if (isInventoryItem(item)) {
    return fromInventoryItem(item);
  }
  return fromRecommendationItem(item);
}

/**
 * Human-readable location label for an inventory item.
 * Prefers the full address; otherwise joins the present parts (country, state)
 * so a missing state never renders "Country, undefined" or a dangling comma.
 */
export function formatInventoryLocation(location?: {
  address?: string;
  country?: string;
  state?: string;
}): string {
  if (location?.address) return location.address;
  return [location?.country, location?.state].filter(Boolean).join(", ");
}

/**
 * Type guard: true if the item is InventoryItem (has detail.location and performance with cpmRate/totalSot shape).
 */
export function isInventoryItem(
  item: InventoryItem | InventoryRecommendationItem,
): item is InventoryItem {
  return (
    "detail" in item &&
    typeof (item as InventoryItem).detail?.referenceId === "string" &&
    "location" in item &&
    typeof (item as InventoryItem).location?.location === "object"
  );
}

/**
 * Type guard: true if the item is InventoryRecommendationItem.
 */
export function isRecommendationItem(
  item: InventoryItem | InventoryRecommendationItem,
): item is InventoryRecommendationItem {
  return "inventoryId" in item && "inventoryDetails" in item;
}
