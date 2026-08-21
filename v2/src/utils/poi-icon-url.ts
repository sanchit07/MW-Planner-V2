import {
  POI_ICON_MANIFEST,
  type POICategory,
} from "@constants/poi-icons.generated";

// Icons are served via the planner frontend CloudFront distribution under
// `/POI-icons/{TitleCased_Category}.jpg` — e.g. `Accounting.jpg`,
// `Amusement_Park.jpg`. Manifest keys remain snake_case; they are title-cased
// at URL-build time in `buildIconUrl` below.
const ICON_BASE = "https://planner-stg.movingwalls.com/POI-icons";

const toIconFileName = (category: POICategory): string =>
  category
    .split("_")
    .map((word) => (word ? word[0].toUpperCase() + word.slice(1) : word))
    .join("_");

const buildIconUrl = (category: POICategory): string =>
  `${ICON_BASE}/${toIconFileName(category)}.jpg`;

// Inline SVG for an unknown category — Lucide `MapPin` at brand blue.
// Encoded as a data URL so it never touches the network.
const GENERIC_SVG = encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="32" viewBox="0 0 24 24" fill="#2176cc" stroke="#ffffff" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0"/><circle cx="12" cy="10" r="3" fill="#ffffff" stroke="none"/></svg>',
);
const GENERIC_DATA_URL = `data:image/svg+xml;utf8,${GENERIC_SVG}`;

export interface POIIconSrc {
  /** Primary image URL to try first. */
  src: string;
  /**
   * Ordered semantically-related URLs to try if `src` 404s
   * (e.g. `primary_school` → `school`). The marker component
   * falls through this list on <img> error. Empty when generic.
   */
  fallbackSrcs: string[];
  /** True when resolution fell through to the built-in generic pin. */
  isGeneric: boolean;
  /** Canonical category key, or `null` for generic. */
  category: POICategory | null;
}

const GENERIC: POIIconSrc = {
  src: GENERIC_DATA_URL,
  fallbackSrcs: [],
  isGeneric: true,
  category: null,
};

/**
 * Semantically-related fallback categories. When the primary icon 404s,
 * the marker tries each fallback in order before giving up and rendering
 * a plain pin. Keep these conservative — a bad fallback is worse than none.
 */
const RELATED_FALLBACKS: Partial<Record<POICategory, POICategory[]>> = {
  // Schools — every school subtype gets the School.jpg icon.
  primary_school: ["school"],
  secondary_school: ["school"],
  university: ["school"],

  // Lodging — Google often returns `hotel` / `motel` / `resort_hotel`
  // which aren't in our manifest. The semantic keyword map below routes
  // those to `lodging`. Nothing to chain from `lodging` itself.

  // Food — cafes, bakeries, meal services fall back to restaurant.
  meal_delivery: ["restaurant", "food"],
  meal_takeaway: ["restaurant", "food"],
  bakery: ["cafe", "restaurant", "food"],
  cafe: ["restaurant", "food"],
  food: ["restaurant"],
  night_club: ["bar"],

  // Retail — generic Store.jpg catches all specific-store subtypes.
  supermarket: ["store"],
  clothing_store: ["store"],
  electronics_store: ["store"],
  furniture_store: ["store"],
  home_goods_store: ["store"],
  shoe_store: ["store"],
  jewelry_store: ["store"],
  pet_store: ["store"],
  book_store: ["store"],
  convenience_store: ["store"],
  liquor_store: ["store"],
  department_store: ["store"],
  hardware_store: ["store"],
  bicycle_store: ["store"],
  florist: ["store"],

  // Health — all practitioners → doctor → health.
  drugstore: ["pharmacy"],
  pharmacy: ["drugstore"],
  physiotherapist: ["doctor", "health"],
  veterinary_care: ["doctor"],
  dentist: ["doctor", "health"],
  spa: ["health"],

  // Transit — sub-stations share the transit pin.
  light_rail_station: ["train_station", "transit_station"],
  subway_station: ["train_station", "transit_station"],
  bus_station: ["transit_station"],
  taxi_stand: ["transit_station"],
  train_station: ["transit_station"],

  // Worship — all religious buildings share the Place of Worship icon.
  hindu_temple: ["place_of_worship"],
  mosque: ["place_of_worship"],
  synagogue: ["place_of_worship"],
  church: ["place_of_worship"],

  // Places/areas — every administrative/sublocality level falls back to locality.
  administrative_area_level_1: ["locality"],
  administrative_area_level_2: ["locality"],
  administrative_area_level_3: ["locality"],
  administrative_area_level_4: ["locality"],
  administrative_area_level_5: ["locality"],
  administrative_area_level_6: ["locality"],
  administrative_area_level_7: ["locality"],
  sublocality: ["neighborhood", "locality"],
  sublocality_level_1: ["sublocality", "neighborhood", "locality"],
  sublocality_level_2: ["sublocality", "neighborhood", "locality"],
  sublocality_level_3: ["sublocality", "neighborhood", "locality"],
  sublocality_level_4: ["sublocality", "neighborhood", "locality"],
  sublocality_level_5: ["sublocality", "neighborhood", "locality"],
  neighborhood: ["locality"],
  point_of_interest: ["tourist_attraction", "landmark"],

  // Parks
  campground: ["park"],
  rv_park: ["campground", "park"],

  // Automotive
  gas_station: ["car_repair"],
  car_rental: ["car_dealer"],
  car_wash: ["car_repair"],
};

/**
 * Keyword-based semantic fallback for Google Places categories that aren't
 * in our manifest at all (e.g. `fast_food_restaurant`, `pizza_restaurant`,
 * `high_school`, `boarding_school`, `resort_hotel`, `guest_house`). The
 * Google Places taxonomy is larger than our icon set, so match by keyword
 * so the user still gets a sensible icon instead of the generic pin.
 *
 * Order matters — first match wins. Keep more specific before more generic.
 */
const SEMANTIC_KEYWORD_MAP: ReadonlyArray<[RegExp, POICategory]> = [
  [
    /(primary|secondary|high|middle|boarding|preschool|kindergarten)_?school/,
    "school",
  ],
  [/(university|college|academy)/, "school"],
  [/(hotel|motel|inn|guest_?house|hostel|resort)/, "lodging"],
  [/(restaurant|diner|eatery|steak_?house|pizzeria|bistro)/, "restaurant"],
  [/(fast_food|food_court|food_truck)/, "restaurant"],
  [/(coffee|espresso|tea_?house)/, "cafe"],
  [/(dessert|ice_cream|confectionery|candy|chocolate)/, "bakery"],
  [/(doctor|clinic|physician|medical|chiropractor)/, "doctor"],
  [/hospital/, "hospital"],
  [/pharmacy|chemist/, "pharmacy"],
  [/(store|shop|market|boutique|mart)$/, "store"],
  [/(church|cathedral|chapel|basilica)/, "church"],
  [/(temple|shrine|monastery)/, "place_of_worship"],
  [/(park|garden|playground)/, "park"],
  [/(museum|gallery|exhibition)/, "museum"],
  [/(cinema|theater|theatre|auditorium)/, "movie_theater"],
  [/(bank|atm|credit_union)/, "bank"],
  [/(gym|fitness|yoga|pilates)/, "gym"],
  [/(airport|airfield|heliport)/, "airport"],
  [/station/, "transit_station"],
  [/(police|sheriff)/, "police"],
  [/fire/, "fire_station"],
];

/**
 * Known display-name → canonical category aliases.
 *
 * Google's `primaryTypeDisplayName` is localized and occasionally diverges
 * from the Places API type enum (e.g. "ATM" vs `atm`, "RV Park" vs `rv_park`,
 * "Police Station" vs `police`). Normalize common forms here so the resolver
 * still hits the manifest when only the display name is available.
 */
const DISPLAY_NAME_ALIASES: Readonly<Record<string, POICategory>> = {
  atm: "atm",
  "rv park": "rv_park",
  "police station": "police",
  "place of worship": "place_of_worship",
  "point of interest": "point_of_interest",
  "natural feature": "natural_feature",
  "tourist attraction": "tourist_attraction",
  "shopping mall": "shopping_mall",
  "movie theater": "movie_theater",
  "post office": "post_office",
  "gas station": "gas_station",
  "fire station": "fire_station",
  "bus station": "bus_station",
  "train station": "train_station",
  "subway station": "subway_station",
  "light rail station": "light_rail_station",
  "transit station": "transit_station",
  "taxi stand": "taxi_stand",
  "city hall": "city_hall",
  "local government office": "local_government_office",
  "hindu temple": "hindu_temple",
  "amusement park": "amusement_park",
  "art gallery": "art_gallery",
  "bowling alley": "bowling_alley",
  "convenience store": "convenience_store",
  "department store": "department_store",
  "hardware store": "hardware_store",
  "electronics store": "electronics_store",
  "jewelry store": "jewelry_store",
  "shoe store": "shoe_store",
  "clothing store": "clothing_store",
  "furniture store": "furniture_store",
  "home goods store": "home_goods_store",
  "bicycle store": "bicycle_store",
  "book store": "book_store",
  "liquor store": "liquor_store",
  "pet store": "pet_store",
  "beauty salon": "beauty_salon",
  "hair care": "hair_care",
  "night club": "night_club",
  "veterinary care": "veterinary_care",
  "real estate agency": "real_estate_agency",
  "insurance agency": "insurance_agency",
  "travel agency": "travel_agency",
  "moving company": "moving_company",
  "funeral home": "funeral_home",
  "car dealer": "car_dealer",
  "car rental": "car_rental",
  "car repair": "car_repair",
  "car wash": "car_wash",
  "primary school": "primary_school",
  "secondary school": "secondary_school",
  "meal delivery": "meal_delivery",
  "meal takeaway": "meal_takeaway",
  "movie rental": "movie_rental",
  "roofing contractor": "roofing_contractor",
  "general contractor": "general_contractor",
};

const normalizePrimaryType = (value: string): string =>
  value.trim().toLowerCase().replace(/\s+/g, "_");

const matchSemanticKeyword = (value: string): POICategory | null => {
  for (const [pattern, category] of SEMANTIC_KEYWORD_MAP) {
    if (pattern.test(value)) return category;
  }
  return null;
};

const resolveCategory = (
  primaryType?: string | null,
  primaryTypeDisplayName?: string | null,
): POICategory | null => {
  if (primaryType) {
    const key = normalizePrimaryType(primaryType);
    if (key in POI_ICON_MANIFEST) return key as POICategory;
  }
  if (primaryTypeDisplayName) {
    const display = primaryTypeDisplayName.trim().toLowerCase();
    const alias = DISPLAY_NAME_ALIASES[display];
    if (alias) return alias;
    const snake = normalizePrimaryType(display);
    if (snake in POI_ICON_MANIFEST) return snake as POICategory;
  }
  // Final resort: keyword match. Handles categories that aren't in our
  // manifest at all (e.g. `high_school`, `fast_food_restaurant`) so the
  // pin still shows something meaningful instead of a generic Lucide pin.
  if (primaryType) {
    const key = normalizePrimaryType(primaryType);
    const semantic = matchSemanticKeyword(key);
    if (semantic) return semantic;
  }
  if (primaryTypeDisplayName) {
    const snake = normalizePrimaryType(primaryTypeDisplayName);
    const semantic = matchSemanticKeyword(snake);
    if (semantic) return semantic;
  }
  return null;
};

/**
 * Resolve a POI category → same-origin PNG icon path.
 *
 * Resolution order:
 *   1. `primaryType` (Places API snake_case enum) → manifest hit
 *   2. `primaryTypeDisplayName` → known alias → manifest
 *   3. `primaryTypeDisplayName` → snake-cased → manifest
 *   4. Built-in generic `MapPin` data-URL (never a black pin)
 *
 * Unknown inputs log once in dev so the team sees which categories to add.
 */
export const getPOIIconUrl = (
  primaryType?: string | null,
  primaryTypeDisplayName?: string | null,
): POIIconSrc => {
  const category = resolveCategory(primaryType, primaryTypeDisplayName);
  if (!category) {
    logMissOnce(primaryType, primaryTypeDisplayName);
    return GENERIC;
  }
  const fallbackSrcs = (RELATED_FALLBACKS[category] ?? []).map(buildIconUrl);
  return {
    src: buildIconUrl(category),
    fallbackSrcs,
    isGeneric: false,
    category,
  };
};

const missLogged = new Set<string>();
const logMissOnce = (
  primaryType?: string | null,
  primaryTypeDisplayName?: string | null,
) => {
  if (!import.meta.env.DEV) return;
  const key = `${primaryType ?? ""}|${primaryTypeDisplayName ?? ""}`;
  if (missLogged.has(key)) return;
  missLogged.add(key);
  console.warn("[poi-icon] no icon for", {
    primaryType,
    primaryTypeDisplayName,
  });
};

/** Test-only: clear the dev-warn dedupe cache. */
export const __resetPOIIconMissLogForTests = () => {
  missLogged.clear();
};
