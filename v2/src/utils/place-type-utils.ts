import {
  BedDouble,
  BookOpen,
  Building2,
  Car,
  Coffee,
  Dumbbell,
  Film,
  Flag,
  Fuel,
  Globe,
  GraduationCap,
  Heart,
  Home,
  Landmark,
  LucideIcon,
  Mail,
  MapPin,
  MapPinned,
  Music,
  Palette,
  PawPrint,
  Pill,
  Plane,
  Scissors,
  Shield,
  ShoppingBag,
  ShoppingCart,
  Sparkles,
  Star,
  Train,
  Trees,
  Trophy,
  Utensils,
  Wrench,
} from "lucide-react";

import { getCountryAdminLabel } from "./country-admin-labels";

export interface PlaceTypeInfo {
  Icon: LucideIcon;
  label: string;
  /** Tailwind bg class for thumbnail backgrounds */
  bgClass: string;
  /** Tailwind text-color class for icons and labels */
  colorClass: string;
}

const DEFAULT_INFO: PlaceTypeInfo = {
  Icon: MapPin,
  label: "Place",
  bgClass: "bg-slate-100",
  colorClass: "text-slate-500",
};

// ---------------------------------------------------------------------------
// Type → visual mapping
// Keys match Google Places API type strings.
// ---------------------------------------------------------------------------
const PLACE_TYPE_MAP: Record<string, PlaceTypeInfo> = {
  // Food & Drink
  restaurant: {
    Icon: Utensils,
    label: "Restaurant",
    bgClass: "bg-orange-100",
    colorClass: "text-orange-600",
  },
  cafe: {
    Icon: Coffee,
    label: "Café",
    bgClass: "bg-amber-100",
    colorClass: "text-amber-700",
  },
  coffee_shop: {
    Icon: Coffee,
    label: "Coffee Shop",
    bgClass: "bg-amber-100",
    colorClass: "text-amber-700",
  },
  bar: {
    Icon: Music,
    label: "Bar",
    bgClass: "bg-purple-100",
    colorClass: "text-purple-600",
  },
  night_club: {
    Icon: Music,
    label: "Night Club",
    bgClass: "bg-purple-100",
    colorClass: "text-purple-600",
  },
  bakery: {
    Icon: Utensils,
    label: "Bakery",
    bgClass: "bg-orange-100",
    colorClass: "text-orange-500",
  },
  meal_delivery: {
    Icon: Utensils,
    label: "Delivery",
    bgClass: "bg-orange-100",
    colorClass: "text-orange-500",
  },
  meal_takeaway: {
    Icon: Utensils,
    label: "Takeaway",
    bgClass: "bg-orange-100",
    colorClass: "text-orange-500",
  },
  food: {
    Icon: Utensils,
    label: "Food",
    bgClass: "bg-orange-100",
    colorClass: "text-orange-600",
  },

  // Health & Medical
  hospital: {
    Icon: Heart,
    label: "Hospital",
    bgClass: "bg-red-100",
    colorClass: "text-red-600",
  },
  doctor: {
    Icon: Heart,
    label: "Doctor",
    bgClass: "bg-red-100",
    colorClass: "text-red-500",
  },
  dentist: {
    Icon: Heart,
    label: "Dentist",
    bgClass: "bg-red-100",
    colorClass: "text-red-500",
  },
  pharmacy: {
    Icon: Pill,
    label: "Pharmacy",
    bgClass: "bg-green-100",
    colorClass: "text-green-600",
  },
  drugstore: {
    Icon: Pill,
    label: "Drugstore",
    bgClass: "bg-green-100",
    colorClass: "text-green-600",
  },
  veterinary_care: {
    Icon: PawPrint,
    label: "Vet Clinic",
    bgClass: "bg-teal-100",
    colorClass: "text-teal-600",
  },
  health: {
    Icon: Heart,
    label: "Health",
    bgClass: "bg-red-100",
    colorClass: "text-red-500",
  },
  spa: {
    Icon: Sparkles,
    label: "Spa",
    bgClass: "bg-pink-100",
    colorClass: "text-pink-500",
  },

  // Education
  school: {
    Icon: GraduationCap,
    label: "School",
    bgClass: "bg-blue-100",
    colorClass: "text-blue-600",
  },
  university: {
    Icon: GraduationCap,
    label: "University",
    bgClass: "bg-blue-100",
    colorClass: "text-blue-700",
  },
  library: {
    Icon: BookOpen,
    label: "Library",
    bgClass: "bg-indigo-100",
    colorClass: "text-indigo-600",
  },
  book_store: {
    Icon: BookOpen,
    label: "Book Store",
    bgClass: "bg-indigo-100",
    colorClass: "text-indigo-500",
  },

  // Finance
  bank: {
    Icon: Landmark,
    label: "Bank",
    bgClass: "bg-emerald-100",
    colorClass: "text-emerald-600",
  },
  atm: {
    Icon: Landmark,
    label: "ATM",
    bgClass: "bg-emerald-100",
    colorClass: "text-emerald-500",
  },
  accounting: {
    Icon: Building2,
    label: "Accounting",
    bgClass: "bg-slate-100",
    colorClass: "text-slate-600",
  },
  insurance_agency: {
    Icon: Building2,
    label: "Insurance",
    bgClass: "bg-slate-100",
    colorClass: "text-slate-600",
  },

  // Shopping & Retail
  store: {
    Icon: ShoppingBag,
    label: "Store",
    bgClass: "bg-violet-100",
    colorClass: "text-violet-600",
  },
  supermarket: {
    Icon: ShoppingCart,
    label: "Supermarket",
    bgClass: "bg-violet-100",
    colorClass: "text-violet-600",
  },
  grocery_or_supermarket: {
    Icon: ShoppingCart,
    label: "Grocery",
    bgClass: "bg-violet-100",
    colorClass: "text-violet-500",
  },
  shopping_mall: {
    Icon: ShoppingBag,
    label: "Shopping Mall",
    bgClass: "bg-violet-100",
    colorClass: "text-violet-700",
  },
  clothing_store: {
    Icon: ShoppingBag,
    label: "Clothing Store",
    bgClass: "bg-violet-100",
    colorClass: "text-violet-500",
  },
  convenience_store: {
    Icon: ShoppingCart,
    label: "Convenience Store",
    bgClass: "bg-violet-100",
    colorClass: "text-violet-500",
  },
  electronics_store: {
    Icon: ShoppingBag,
    label: "Electronics",
    bgClass: "bg-violet-100",
    colorClass: "text-violet-600",
  },
  home_goods_store: {
    Icon: Home,
    label: "Home Goods",
    bgClass: "bg-violet-100",
    colorClass: "text-violet-500",
  },
  furniture_store: {
    Icon: Home,
    label: "Furniture Store",
    bgClass: "bg-violet-100",
    colorClass: "text-violet-500",
  },

  // Parks & Nature
  park: {
    Icon: Trees,
    label: "Park",
    bgClass: "bg-green-100",
    colorClass: "text-green-600",
  },
  zoo: {
    Icon: PawPrint,
    label: "Zoo",
    bgClass: "bg-green-100",
    colorClass: "text-green-600",
  },
  campground: {
    Icon: Trees,
    label: "Campground",
    bgClass: "bg-green-100",
    colorClass: "text-green-600",
  },
  national_park: {
    Icon: Trees,
    label: "National Park",
    bgClass: "bg-green-100",
    colorClass: "text-green-700",
  },
  nature_reserve: {
    Icon: Trees,
    label: "Nature Reserve",
    bgClass: "bg-green-100",
    colorClass: "text-green-700",
  },
  amusement_park: {
    Icon: Star,
    label: "Amusement Park",
    bgClass: "bg-yellow-100",
    colorClass: "text-yellow-600",
  },

  // Lodging
  hotel: {
    Icon: BedDouble,
    label: "Hotel",
    bgClass: "bg-amber-100",
    colorClass: "text-amber-600",
  },
  lodging: {
    Icon: BedDouble,
    label: "Lodging",
    bgClass: "bg-amber-100",
    colorClass: "text-amber-600",
  },
  motel: {
    Icon: BedDouble,
    label: "Motel",
    bgClass: "bg-amber-100",
    colorClass: "text-amber-500",
  },

  // Transit
  airport: {
    Icon: Plane,
    label: "Airport",
    bgClass: "bg-sky-100",
    colorClass: "text-sky-600",
  },
  train_station: {
    Icon: Train,
    label: "Train Station",
    bgClass: "bg-sky-100",
    colorClass: "text-sky-600",
  },
  transit_station: {
    Icon: Train,
    label: "Transit Station",
    bgClass: "bg-sky-100",
    colorClass: "text-sky-500",
  },
  bus_station: {
    Icon: Train,
    label: "Bus Station",
    bgClass: "bg-sky-100",
    colorClass: "text-sky-500",
  },
  subway_station: {
    Icon: Train,
    label: "Subway Station",
    bgClass: "bg-sky-100",
    colorClass: "text-sky-600",
  },
  light_rail_station: {
    Icon: Train,
    label: "LRT Station",
    bgClass: "bg-sky-100",
    colorClass: "text-sky-600",
  },
  taxi_stand: {
    Icon: Car,
    label: "Taxi Stand",
    bgClass: "bg-yellow-100",
    colorClass: "text-yellow-600",
  },

  // Religious
  church: {
    Icon: Building2,
    label: "Church",
    bgClass: "bg-purple-100",
    colorClass: "text-purple-600",
  },
  mosque: {
    Icon: Building2,
    label: "Mosque",
    bgClass: "bg-purple-100",
    colorClass: "text-purple-600",
  },
  hindu_temple: {
    Icon: Building2,
    label: "Hindu Temple",
    bgClass: "bg-purple-100",
    colorClass: "text-purple-600",
  },
  synagogue: {
    Icon: Building2,
    label: "Synagogue",
    bgClass: "bg-purple-100",
    colorClass: "text-purple-600",
  },
  place_of_worship: {
    Icon: Building2,
    label: "Place of Worship",
    bgClass: "bg-purple-100",
    colorClass: "text-purple-600",
  },

  // Culture & Entertainment
  museum: {
    Icon: Landmark,
    label: "Museum",
    bgClass: "bg-stone-100",
    colorClass: "text-stone-600",
  },
  art_gallery: {
    Icon: Palette,
    label: "Art Gallery",
    bgClass: "bg-pink-100",
    colorClass: "text-pink-600",
  },
  movie_theater: {
    Icon: Film,
    label: "Movie Theater",
    bgClass: "bg-red-100",
    colorClass: "text-red-500",
  },
  theater: {
    Icon: Film,
    label: "Theater",
    bgClass: "bg-red-100",
    colorClass: "text-red-500",
  },
  tourist_attraction: {
    Icon: Star,
    label: "Tourist Attraction",
    bgClass: "bg-yellow-100",
    colorClass: "text-yellow-600",
  },
  aquarium: {
    Icon: Star,
    label: "Aquarium",
    bgClass: "bg-sky-100",
    colorClass: "text-sky-600",
  },
  casino: {
    Icon: Star,
    label: "Casino",
    bgClass: "bg-yellow-100",
    colorClass: "text-yellow-600",
  },

  // Sports & Recreation
  gym: {
    Icon: Dumbbell,
    label: "Gym",
    bgClass: "bg-orange-100",
    colorClass: "text-orange-600",
  },
  stadium: {
    Icon: Trophy,
    label: "Stadium",
    bgClass: "bg-orange-100",
    colorClass: "text-orange-600",
  },
  sports_complex: {
    Icon: Trophy,
    label: "Sports Complex",
    bgClass: "bg-orange-100",
    colorClass: "text-orange-500",
  },
  golf_course: {
    Icon: Trophy,
    label: "Golf Course",
    bgClass: "bg-green-100",
    colorClass: "text-green-600",
  },

  // Beauty & Personal Care
  beauty_salon: {
    Icon: Scissors,
    label: "Beauty Salon",
    bgClass: "bg-pink-100",
    colorClass: "text-pink-500",
  },
  hair_care: {
    Icon: Scissors,
    label: "Hair Care",
    bgClass: "bg-pink-100",
    colorClass: "text-pink-500",
  },

  // Automotive
  gas_station: {
    Icon: Fuel,
    label: "Gas Station",
    bgClass: "bg-gray-100",
    colorClass: "text-gray-600",
  },
  car_repair: {
    Icon: Wrench,
    label: "Car Repair",
    bgClass: "bg-gray-100",
    colorClass: "text-gray-600",
  },
  car_dealer: {
    Icon: Car,
    label: "Car Dealer",
    bgClass: "bg-gray-100",
    colorClass: "text-gray-600",
  },
  car_wash: {
    Icon: Car,
    label: "Car Wash",
    bgClass: "bg-sky-100",
    colorClass: "text-sky-500",
  },

  // Government & Emergency
  police: {
    Icon: Shield,
    label: "Police",
    bgClass: "bg-blue-100",
    colorClass: "text-blue-700",
  },
  fire_station: {
    Icon: Shield,
    label: "Fire Station",
    bgClass: "bg-red-100",
    colorClass: "text-red-600",
  },
  post_office: {
    Icon: Mail,
    label: "Post Office",
    bgClass: "bg-yellow-100",
    colorClass: "text-yellow-600",
  },
  local_government_office: {
    Icon: Building2,
    label: "Government Office",
    bgClass: "bg-slate-100",
    colorClass: "text-slate-600",
  },
  courthouse: {
    Icon: Building2,
    label: "Courthouse",
    bgClass: "bg-slate-100",
    colorClass: "text-slate-600",
  },
  city_hall: {
    Icon: Building2,
    label: "City Hall",
    bgClass: "bg-slate-100",
    colorClass: "text-slate-600",
  },
  embassy: {
    Icon: Building2,
    label: "Embassy",
    bgClass: "bg-slate-100",
    colorClass: "text-slate-600",
  },

  // Residential & Real Estate
  real_estate_agency: {
    Icon: Home,
    label: "Real Estate",
    bgClass: "bg-teal-100",
    colorClass: "text-teal-600",
  },

  // Default location type
  premise: {
    Icon: MapPin,
    label: "Location",
    bgClass: "bg-slate-100",
    colorClass: "text-slate-500",
  },

  // Geographic / Administrative areas
  // Hierarchy (broad → specific): State → City (locality) → Sub-locality (sublocality) → Area
  locality: {
    Icon: Building2,
    label: "City",
    bgClass: "bg-blue-50",
    colorClass: "text-blue-600",
  },
  sublocality: {
    Icon: MapPinned,
    label: "Sub-locality",
    bgClass: "bg-slate-100",
    colorClass: "text-slate-600",
  },
  sublocality_level_1: {
    Icon: MapPinned,
    label: "Sub-locality",
    bgClass: "bg-slate-100",
    colorClass: "text-slate-600",
  },
  sublocality_level_2: {
    Icon: MapPinned,
    label: "Sub-locality",
    bgClass: "bg-slate-100",
    colorClass: "text-slate-500",
  },
  neighborhood: {
    Icon: Home,
    label: "Area",
    bgClass: "bg-amber-50",
    colorClass: "text-amber-600",
  },
  administrative_area_level_1: {
    Icon: Landmark,
    label: "State / Province",
    bgClass: "bg-slate-100",
    colorClass: "text-slate-600",
  },
  administrative_area_level_2: {
    Icon: MapPinned,
    label: "District",
    bgClass: "bg-slate-100",
    colorClass: "text-slate-500",
  },
  administrative_area_level_3: {
    Icon: MapPinned,
    label: "District",
    bgClass: "bg-slate-100",
    colorClass: "text-slate-500",
  },
  country: {
    Icon: Globe,
    label: "Country",
    bgClass: "bg-indigo-50",
    colorClass: "text-indigo-600",
  },
  colloquial_area: {
    Icon: MapPin,
    label: "Area",
    bgClass: "bg-slate-100",
    colorClass: "text-slate-500",
  },
  natural_feature: {
    Icon: Trees,
    label: "Natural Feature",
    bgClass: "bg-green-100",
    colorClass: "text-green-600",
  },
  route: {
    Icon: MapPin,
    label: "Street",
    bgClass: "bg-slate-100",
    colorClass: "text-slate-500",
  },
  street_address: {
    Icon: MapPin,
    label: "Address",
    bgClass: "bg-slate-100",
    colorClass: "text-slate-500",
  },
  postal_code: {
    Icon: Flag,
    label: "Postal Code",
    bgClass: "bg-slate-100",
    colorClass: "text-slate-400",
  },
};

// ---------------------------------------------------------------------------
// Types that are too generic to be useful as primary type labels
// ---------------------------------------------------------------------------
const GENERIC_TYPES = new Set([
  "point_of_interest",
  "establishment",
  "political",
  "geocode",
  "premise",
  "subpremise",
  "route",
  "street_address",
  "intersection",
  "country",
  "administrative_area_level_1",
  "administrative_area_level_2",
  "administrative_area_level_3",
  "administrative_area_level_4",
  "administrative_area_level_5",
  "locality",
  "sublocality",
  "sublocality_level_1",
  "sublocality_level_2",
  "postal_code",
  "natural_feature",
  "plus_code",
  "colloquial_area",
  "neighborhood",
]);

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Returns icon, label, and Tailwind color/bg classes for a Google Places type key.
 * Falls back to a generic pin if the type is unknown or null.
 */
export function getPlaceTypeInfo(
  typeKey: string | null,
  countryCode?: string | null,
): PlaceTypeInfo {
  if (!typeKey) return DEFAULT_INFO;
  const info = PLACE_TYPE_MAP[typeKey] ?? DEFAULT_INFO;
  if (countryCode) {
    const countryLabel = getCountryAdminLabel(countryCode, typeKey);
    if (countryLabel) return { ...info, label: countryLabel };
  }
  return info;
}

/**
 * Returns a human-readable label for a Google Places type key.
 */
export function getPlaceTypeLabel(
  typeKey: string | null,
  countryCode?: string | null,
): string {
  return getPlaceTypeInfo(typeKey, countryCode).label;
}

/**
 * Returns the Lucide icon component for a Google Places type key.
 */
export function getPlaceTypeIcon(typeKey: string | null): LucideIcon {
  return getPlaceTypeInfo(typeKey).Icon;
}

/**
 * Picks the most descriptive type from a Google Places types array.
 * Prefers types that have a known mapping and skips generic administrative types.
 * Returns null if the array is empty.
 */
export function pickPrimaryType(
  types: string[] | undefined | null,
): string | null {
  if (!types || types.length === 0) return null;

  // First pass: prefer a type that has a known rich mapping and is not generic
  for (const type of types) {
    if (PLACE_TYPE_MAP[type] && !GENERIC_TYPES.has(type)) {
      return type;
    }
  }

  // Second pass: any non-generic type
  const specific = types.find((t) => !GENERIC_TYPES.has(t));
  if (specific) return specific;

  // Fallback: first type in array
  return types[0] ?? null;
}

/**
 * Extracts a human-readable local region name from Google Maps address components.
 * Prefers locality (city), then sub-administrative area, then state/province.
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function extractLocalName(addressComponents: any[]): string | null {
  return extractLocalNameWithType(addressComponents)?.name ?? null;
}

/**
 * Extracts local region name AND the address component type it came from.
 * Use this when you need to show a prefix label ("City", "State", etc.) alongside the name.
 */
export function extractLocalNameWithType(
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  addressComponents: any[],
): { name: string; type: string } | null {
  if (!addressComponents || addressComponents.length === 0) return null;

  const preferredTypes = [
    "locality",
    "administrative_area_level_2",
    "administrative_area_level_1",
  ];

  for (const type of preferredTypes) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const component = addressComponents.find((c: any) =>
      Array.isArray(c.types) ? c.types.includes(type) : false,
    );
    if (component) {
      // New Places API uses longText; legacy API uses long_name
      const name = component.longText ?? component.long_name ?? null;
      if (name) return { name, type };
    }
  }

  return null;
}

/** Maps an address-component type to a short human-readable prefix label. */
const LOCAL_NAME_TYPE_LABELS: Record<string, string> = {
  locality: "City",
  sublocality: "Sub-locality",
  sublocality_level_1: "Sub-locality",
  sublocality_level_2: "Sub-locality",
  neighborhood: "Area",
  administrative_area_level_1: "State",
  administrative_area_level_2: "District",
  administrative_area_level_3: "District",
  country: "Country",
};

/**
 * Returns the display prefix for a localName type (e.g. "City", "State").
 * Returns null for unknown types so callers can skip rendering the prefix.
 */
export function getLocalNameTypeLabel(
  localNameType: string | null | undefined,
  countryCode?: string | null,
): string | null {
  if (!localNameType) return null;
  if (countryCode) {
    const countryLabel = getCountryAdminLabel(countryCode, localNameType);
    if (countryLabel) return countryLabel;
  }
  return LOCAL_NAME_TYPE_LABELS[localNameType] ?? null;
}
