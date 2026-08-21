import {
  InventoryClassification,
  InventoryType,
  MediaChannel,
} from "src/constants/inventory.constants";
import {
  dayString,
  InventoryFilters,
  InventoryItem,
  InventoryLocation,
  LatLong,
} from "src/types/inventory.types";

/**
 * Gets the primary coordinates from an InventoryLocation
 * Returns the first coordinate from the locationCoordinates array, or null if not available
 */
export const getPrimaryCoordinates = (
  location: InventoryLocation | null | undefined,
): LatLong | null => {
  if (!location?.locationCoordinates?.coordinates?.length) {
    return null;
  }
  return location.locationCoordinates.coordinates[0];
};

/**
 * Gets the latitude from an InventoryLocation
 * Returns the latitude of the first coordinate, or undefined if not available
 */
export const getLatitude = (
  location: InventoryLocation | null | undefined,
): number | undefined => {
  const coords = getPrimaryCoordinates(location);
  return coords?.latitude;
};

/**
 * Gets the longitude from an InventoryLocation
 * Returns the longitude of the first coordinate, or undefined if not available
 */
export const getLongitude = (
  location: InventoryLocation | null | undefined,
): number | undefined => {
  const coords = getPrimaryCoordinates(location);
  return coords?.longitude;
};

export const sortDaysStartingFromMonday = (days: Array<dayString>) => {
  const dayOrder = [
    "MONDAY",
    "TUESDAY",
    "WEDNESDAY",
    "THURSDAY",
    "FRIDAY",
    "SATURDAY",
    "SUNDAY",
  ];
  return [...days].sort((a, b) => dayOrder.indexOf(a) - dayOrder.indexOf(b));
};

// Map each known media channel to the inventory classification it targets.
const CHANNEL_TO_CLASSIFICATION: Record<string, InventoryClassification> = {
  [MediaChannel.DIGITAL_OOH]: InventoryClassification.DIGITAL,
  [MediaChannel.CLASSIC_OOH]: InventoryClassification.CLASSIC,
  [MediaChannel.CINEMA]: InventoryClassification.CINEMA,
};

// Every classification a known media channel can map to. When the selected
// channels cover all of these, there is nothing to exclude, so the filter is
// omitted (empty array).
const ALL_CLASSIFICATIONS: InventoryClassification[] = [
  InventoryClassification.DIGITAL,
  InventoryClassification.CLASSIC,
  InventoryClassification.CINEMA,
];

/**
 * Maps selected campaign media channels to inventory classifications for the
 * /filter API payload.
 *
 * Each selected channel contributes its classification and the (de-duplicated)
 * union is returned so inventory outside the selection is excluded:
 * - Classic OOH only          -> ["Classic"]
 * - Digital OOH only          -> ["Digital"]
 * - Cinema only               -> ["Cinema"]
 * - Digital + Classic         -> ["Digital","Classic"] (cinema docs excluded)
 * - Digital + Cinema          -> ["Digital","Cinema"]
 * - ALL known channels        -> []  (no classification filter; return all)
 * - None / unknown selection  -> []
 */
export const mediaChannelsToClassifications = (
  mediaChannels: string[] | null | undefined,
): InventoryClassification[] => {
  if (!Array.isArray(mediaChannels) || mediaChannels.length === 0) {
    return [];
  }

  const union: InventoryClassification[] = [];
  mediaChannels.forEach((ch) => {
    const classification = CHANNEL_TO_CLASSIFICATION[ch];
    if (classification && !union.includes(classification)) {
      union.push(classification);
    }
  });

  if (union.length === 0) return [];
  // All known classifications selected -> no filter needed (return everything).
  if (union.length === ALL_CLASSIFICATIONS.length) return [];
  return union;
};

/**
 * Aligns the classification filter with the campaign's media channels.
 *
 * - Union length 1 (single channel): the classification is LOCKED to that
 *   channel — any manual or stale stored value is overridden.
 * - Union length 2 (e.g. Digital + Cinema): the classification is CONSTRAINED
 *   to the union — the user's picks are intersected with the union; if that
 *   intersection is empty (nothing valid picked yet) the whole union is used.
 * - Union empty (all/none/unknown channels): filters are returned untouched.
 */
export const applyChannelClassificationLock = (
  filters: InventoryFilters,
  mediaChannels: string[] | null | undefined,
): InventoryFilters => {
  const union = mediaChannelsToClassifications(mediaChannels);
  if (union.length === 0) {
    return filters;
  }
  if (union.length === 1) {
    return { ...filters, inventoryClassification: union };
  }
  // Two-classification union: constrain the user's picks to the union.
  const current = Array.isArray(filters.inventoryClassification)
    ? filters.inventoryClassification
    : [];
  const intersection = current.filter((c) =>
    union.includes(c as InventoryClassification),
  );
  return {
    ...filters,
    inventoryClassification: intersection.length > 0 ? intersection : union,
  };
};

// Minimal venue-tree node shape (from the /venues API) needed to map a venue's
// stringValue to its numeric enumerationId.
interface VenueTreeNode {
  enumerationId: number;
  stringValue: string;
  name?: string;
  children?: VenueTreeNode[];
}

/**
 * Flattens the /venues tree into a `stringValue -> enumerationId (as string)`
 * lookup, used to convert the filter's selected venue stringValues into the ids
 * the /filter API expects.
 */
export const buildVenueIdMap = (
  venues: VenueTreeNode[] | null | undefined,
): Record<string, string> => {
  const map: Record<string, string> = {};
  const walk = (nodes: VenueTreeNode[]) => {
    nodes.forEach((n) => {
      if (n.stringValue) map[n.stringValue] = String(n.enumerationId);
      if (n.children?.length) walk(n.children);
    });
  };
  if (Array.isArray(venues)) walk(venues);
  return map;
};

export interface VenueTypeIdFilter {
  digitalOoh: string[];
  classicOoh: string[];
}

/**
 * Builds the /filter `venueTypeIdFilter` payload from the selected venue
 * stringValues: maps each to its enumerationId (as a string) and buckets the
 * ids by the active classification filter — Digital → digitalOoh, Classic →
 * classicOoh, both/none selected → ids in both buckets. Returns null when
 * nothing resolves (no selection / ids not yet loaded), so the caller can omit
 * it.
 *
 * Classification (not media channel) drives the split: for a single-channel
 * campaign the classification is locked to that channel, and for a both-channel
 * campaign it follows the user's classification pick.
 */
export const buildVenueTypeIdFilter = (
  selectedStringValues: string[] | null | undefined,
  classifications: string[] | null | undefined,
  idMap: Record<string, string>,
): VenueTypeIdFilter | null => {
  if (!Array.isArray(selectedStringValues) || selectedStringValues.length === 0)
    return null;
  const ids = selectedStringValues
    .map((sv) => idMap[sv])
    .filter((id): id is string => Boolean(id));
  if (ids.length === 0) return null;

  const selected = Array.isArray(classifications) ? classifications : [];
  const hasDigital = selected.includes(InventoryClassification.DIGITAL);
  const hasClassic = selected.includes(InventoryClassification.CLASSIC);
  // No classification selected → send ids in both buckets so the filter still
  // applies to all shown inventory rather than silently dropping the selection.
  const bothFallback = !hasDigital && !hasClassic;
  return {
    digitalOoh: hasDigital || bothFallback ? ids : [],
    classicOoh: hasClassic || bothFallback ? ids : [],
  };
};

interface OperatingSlot {
  start: string;
  end: string;
}

// "HH:mm[:ss]" → minutes since midnight. An end of "00:00" is treated as
// end-of-day (24:00 = 1440) so it isn't mistaken for the earliest end.
const timeToMinutes = (time: string, isEnd = false): number => {
  const [h, m] = time.split(":");
  const minutes = (parseInt(h, 10) || 0) * 60 + (parseInt(m, 10) || 0);
  return isEnd && minutes === 0 ? 24 * 60 : minutes;
};

/**
 * Reduces a per-day operating schedule to the widest window the inventory is
 * open across any day: the earliest start across all slots and the latest end
 * across all slots. Returns null when there are no slots.
 */
export const getOperatingWindow = (
  operatingTimes:
    | Record<string, OperatingSlot[] | undefined>
    | null
    | undefined,
): { startTime: string; endTime: string } | null => {
  if (!operatingTimes) return null;
  const slots = Object.values(operatingTimes)
    .flat()
    .filter((s): s is OperatingSlot => Boolean(s?.start && s?.end));
  if (slots.length === 0) return null;

  let startTime = slots[0].start;
  let endTime = slots[0].end;
  for (const slot of slots) {
    if (timeToMinutes(slot.start) < timeToMinutes(startTime)) {
      startTime = slot.start; // earliest start
    }
    if (timeToMinutes(slot.end, true) > timeToMinutes(endTime, true)) {
      endTime = slot.end; // latest end
    }
  }
  return { startTime, endTime };
};

const TRANSIT_VENUE_TYPES = [
  InventoryType.TRANSIT,
  InventoryType.BUS,
  InventoryType.RAIL_METRO,
  InventoryType.TAXI_RIDESHARE,
  InventoryType.COMMERCIAL_FLEET,
].map((value) => value.toLowerCase());

/**
 * True if any of the inventory's venue entries fall under a transit venue
 * category (Transit, Bus, Rail & Metro, Taxi & Rideshare, Commercial Fleet).
 */
export const isTransitVenue = (
  venues?: Array<{ name?: string; path?: string }> | null,
): boolean => {
  if (!venues?.length) return false;
  return venues.some((venue) => {
    const name = venue.name?.toLowerCase() ?? "";
    const path = venue.path?.toLowerCase() ?? "";
    return TRANSIT_VENUE_TYPES.some(
      (transitType) => name.includes(transitType) || path.includes(transitType),
    );
  });
};

/**
 * Derives the "Digital"/"Digital Transit"/"Classic"/"Classic Transit" label
 * for the Inventory Details drawer's Type field — base classification from
 * `typeName`, transit-ness from the venue category — following the same
 * DIGITAL/DIGITAL_TRANSIT/CLASSIC/CLASSIC_TRANSIT split used in the
 * Targeting step's Inventory Types section.
 */
export const getInventoryTypeLabel = (
  typeName: string | undefined | null,
  venues: Array<{ name?: string; path?: string }> | undefined | null,
  t: (key: string) => string,
): string | null => {
  if (!typeName) return null;
  const isDigital = typeName.toLowerCase().includes("digital");
  const transit = isTransitVenue(venues);
  if (isDigital) {
    return transit
      ? t("inventoryDetails.typeLabel.digitalTransit")
      : t("inventoryDetails.typeLabel.digital");
  }
  return transit
    ? t("inventoryDetails.typeLabel.classicTransit")
    : t("inventoryDetails.typeLabel.classic");
};

// Shape of the venue-type selection captured on the Targeting step.
interface CampaignWithTargetingVenues {
  mediaChannels?: string[] | null;
  targeting?: {
    venueTypes?: {
      digitalOoh?: string[];
      classicOoh?: string[];
    } | null;
  } | null;
}

/**
 * Union of the venue types picked on the Targeting step (digital + classic),
 * de-duplicated. Used to pre-seed the inventory filter's Venue Type control.
 */
export const getTargetingVenueTypes = (
  campaignData: CampaignWithTargetingVenues | null | undefined,
): string[] => {
  const vt = campaignData?.targeting?.venueTypes;
  if (!vt) return [];
  const digital = Array.isArray(vt.digitalOoh) ? vt.digitalOoh : [];
  const classic = Array.isArray(vt.classicOoh) ? vt.classicOoh : [];
  return Array.from(new Set([...digital, ...classic]));
};

/**
 * Editable classification default derived from the Targeting venue types when
 * BOTH media channels are selected: if the user set venue types for only one
 * channel, default the classification to that channel. Empty when a single
 * channel is selected (that case is handled by applyChannelClassificationLock),
 * when both channels have venue types, or when neither does.
 */
export const getVenueDrivenClassificationDefault = (
  campaignData: CampaignWithTargetingVenues | null | undefined,
): InventoryClassification[] => {
  const channels = campaignData?.mediaChannels;
  const hasBothChannels =
    Array.isArray(channels) &&
    channels.includes(MediaChannel.DIGITAL_OOH) &&
    channels.includes(MediaChannel.CLASSIC_OOH);
  if (!hasBothChannels) return [];

  const vt = campaignData?.targeting?.venueTypes;
  const digital = vt?.digitalOoh?.length ? vt.digitalOoh.length : 0;
  const classic = vt?.classicOoh?.length ? vt.classicOoh.length : 0;

  if (digital > 0 && classic === 0) return [InventoryClassification.DIGITAL];
  if (classic > 0 && digital === 0) return [InventoryClassification.CLASSIC];
  return [];
};

/** Case-insensitive membership: is `value` present in `list`? */
const includesCI = (
  list: string[],
  value: string | undefined | null,
): boolean =>
  Boolean(value) &&
  list.some((v) => v.toLowerCase() === String(value).toLowerCase());

/**
 * Flattens the /venues tree into a `stringValue (slug) -> name-path` lookup,
 * where the name-path is the display names from the root down to that node
 * (e.g. "outdoor-billboards-roadside" -> ["Outdoor","Billboards","Roadside"]).
 *
 * Needed because the inventory filter stores hierarchical venue *slugs* while
 * the /selected-inventory response carries display-name paths; the slug's
 * name-path lets us compare the two without parsing the ambiguous "-"
 * separator (a single name such as "Office Buildings" also contains a hyphen
 * once slugified).
 */
export const buildVenueSlugToNamePath = (
  venues: VenueTreeNode[] | null | undefined,
): Record<string, string[]> => {
  const map: Record<string, string[]> = {};
  const walk = (nodes: VenueTreeNode[], trail: string[]) => {
    nodes.forEach((n) => {
      const path = n.name ? [...trail, n.name] : trail;
      if (n.stringValue) map[n.stringValue] = path;
      if (n.children?.length) walk(n.children, path);
    });
  };
  if (Array.isArray(venues)) walk(venues, []);
  return map;
};

/** Drops consecutive duplicate entries (case-insensitive input assumed). */
const dedupeConsecutive = (arr: string[]): string[] =>
  arr.filter((v, i) => i === 0 || v !== arr[i - 1]);

/**
 * Does an inventory item's venue-type display-name path match ANY of the
 * selected venue slugs? A slug matches when its name-path is a prefix of the
 * item's path — i.e. the item is that venue node or a descendant of it (a
 * type-level selection also matches its subtypes). Consecutive duplicate names
 * (seen in some responses, e.g. ["Office Buildings","Office Buildings","Lobby"])
 * are collapsed on both sides before comparing.
 */
const venueMatches = (
  itemVenueNames: string[] | undefined,
  selectedSlugs: string[],
  slugToNamePath: Record<string, string[]>,
): boolean => {
  const itemPath = dedupeConsecutive(
    (Array.isArray(itemVenueNames) ? itemVenueNames : []).map((n) =>
      n.toLowerCase(),
    ),
  );
  if (itemPath.length === 0) return false;
  return selectedSlugs.some((slug) => {
    const fp = dedupeConsecutive(
      (slugToNamePath[slug] ?? []).map((n) => n.toLowerCase()),
    );
    return fp.length > 0 && fp.every((seg, i) => itemPath[i] === seg);
  });
};

/**
 * Client-side filter for the /selected-inventory response.
 *
 * The /selected-inventory endpoint does NOT accept the inventory filters (its
 * body is only an optional mediaOwnerIds scope), so the selected items must be
 * narrowed on the client to stay consistent with the server-side /filter list.
 *
 * Only the filters whose data is present on the response `detail` are applied;
 * the rest are IGNORED (never hide an item for a filter we cannot evaluate):
 *   - supported:   mediaOwners, environments, venueTypes (match ANY),
 *                  sizes (any panel), bookingMode, inventoryClassification,
 *                  searchbyquery (name contains), programmaticSupport +
 *                  dealTypes (via detail.programmaticDealTypes)
 *   - unsupported: latitude, longitude (geo-radius query, no field to
 *                  replicate → left untouched)
 *
 * programmaticSupport gating: a non-empty `programmaticDealTypes` marks the item
 * as programmatic. support=YES keeps programmatic items (and, when dealTypes are
 * selected, only those whose deal types intersect the selection); support=NO
 * keeps non-programmatic items; support=ALL ignores both.
 *
 * Every active filter is ANDed; an empty/unset filter is skipped (passthrough).
 *
 * Options:
 *  - `searchOverride`: live search term when the redux `searchbyquery` value
 *    may still be stale in a closure.
 *  - `venueSlugToNamePath`: slug → name-path map (from buildVenueSlugToNamePath)
 *    needed to evaluate venueTypes. When absent, the venueTypes filter is
 *    skipped (we never hide an item for a filter we cannot evaluate).
 */
export interface SelectedInventoryFilterOptions {
  searchOverride?: string;
  venueSlugToNamePath?: Record<string, string[]>;
}

export const filterSelectedInventoryClientSide = (
  items: InventoryItem[],
  filters: InventoryFilters | null | undefined,
  options: SelectedInventoryFilterOptions = {},
): InventoryItem[] => {
  if (!Array.isArray(items) || items.length === 0) return items;
  if (!filters) return items;

  const { searchOverride, venueSlugToNamePath } = options;
  const search = (searchOverride ?? filters.searchbyquery ?? "").trim();

  return items.filter((item) => {
    const d = item.detail;
    if (!d) return true;

    if (
      filters.mediaOwners?.length &&
      !filters.mediaOwners.includes(d.mediaOwnerId)
    ) {
      return false;
    }

    if (
      filters.environments?.length &&
      !includesCI(filters.environments, d.environment)
    ) {
      return false;
    }

    if (
      filters.bookingMode?.length &&
      !includesCI(filters.bookingMode, d.bookingMode)
    ) {
      return false;
    }

    // venueTypes → the filter stores hierarchical slugs; match them against the
    // item's display-name path via the venue tree. Skipped when the slug map is
    // unavailable (cannot evaluate → do not hide).
    if (filters.venueTypes?.length && venueSlugToNamePath) {
      if (!venueMatches(d.venueType, filters.venueTypes, venueSlugToNamePath)) {
        return false;
      }
    }

    // sizes → match if ANY panel size is selected.
    if (filters.sizes?.length) {
      const panelSizes = Array.isArray(d.panels)
        ? d.panels.map((p) => p.size)
        : [];
      const anySize = panelSizes.some((s) => includesCI(filters.sizes, s));
      if (!anySize) return false;
    }

    // inventoryClassification ("Digital"/"Classic") vs detail.inventoryType
    // ("Digital", "Digital Network", …) → case-insensitive prefix match so
    // network variants still match their base classification.
    if (filters.inventoryClassification?.length) {
      const it = (d.inventoryType ?? "").toLowerCase();
      const anyClass = filters.inventoryClassification.some((c) =>
        it.startsWith(c.toLowerCase()),
      );
      if (!anyClass) return false;
    }

    if (
      search &&
      !(d.name ?? "").toLowerCase().includes(search.toLowerCase())
    ) {
      return false;
    }

    // Cinema genre / rating constraints — only evaluable when the item carries
    // cinemaFields; skipped otherwise (never hide an item we cannot evaluate).
    if (filters.cinemaGenres?.length && d.cinemaFields?.genres) {
      const genres = d.cinemaFields.genres ?? [];
      if (!genres.some((g) => includesCI(filters.cinemaGenres!, g))) {
        return false;
      }
    }
    if (filters.cinemaRatings?.length && d.cinemaFields?.ratings) {
      const ratings = d.cinemaFields.ratings ?? [];
      if (!ratings.some((r) => includesCI(filters.cinemaRatings!, r))) {
        return false;
      }
    }

    // programmaticSupport / dealTypes via detail.programmaticDealTypes.
    const deals = Array.isArray(d.programmaticDealTypes)
      ? d.programmaticDealTypes
      : [];
    const isProgrammatic = deals.length > 0;
    if (filters.programmaticSupport === "YES") {
      if (!isProgrammatic) return false;
      // dealTypes only apply when programmatic support is required.
      if (
        filters.dealTypes?.length &&
        !deals.some((dt) => includesCI(filters.dealTypes, dt))
      ) {
        return false;
      }
    } else if (filters.programmaticSupport === "NO") {
      if (isProgrammatic) return false;
    }

    return true;
  });
};
