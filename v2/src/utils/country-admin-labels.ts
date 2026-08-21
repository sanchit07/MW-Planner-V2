/**
 * Country-aware administrative area labels.
 *
 * Maps ISO 3166-1 alpha-2 country codes to local terminology for
 * Google Maps address component types. Used by getLocalNameTypeLabel()
 * and getPlaceTypeInfo() to show contextually correct labels like
 * "Prefecture" (Japan) instead of generic "State".
 */

/** Labels for each administrative level, keyed by Google address component type. */
export interface CountryAdminLabels {
  administrative_area_level_1?: string;
  administrative_area_level_2?: string;
  administrative_area_level_3?: string;
  locality?: string;
  sublocality?: string;
  country?: string;
}

/**
 * Curated mapping of 40+ countries with local administrative terminology.
 *
 * Only levels that differ from the generic defaults need to be specified.
 * Missing keys fall back to the generic default labels in LOCAL_NAME_TYPE_LABELS.
 */
export const COUNTRY_ADMIN_LABELS: Record<string, CountryAdminLabels> = {
  // ── Asia-Pacific ──────────────────────────────────────────────────────
  IN: {
    administrative_area_level_1: "State",
    administrative_area_level_2: "District",
    administrative_area_level_3: "Sub-district",
  },
  JP: {
    administrative_area_level_1: "Prefecture",
    administrative_area_level_2: "Sub-prefecture",
    administrative_area_level_3: "Municipality",
  },
  CN: {
    administrative_area_level_1: "Province",
    administrative_area_level_2: "Prefecture",
    administrative_area_level_3: "County",
  },
  KR: {
    administrative_area_level_1: "Province",
    administrative_area_level_2: "District",
    administrative_area_level_3: "Township",
  },
  MY: {
    administrative_area_level_1: "State",
    administrative_area_level_2: "District",
  },
  SG: {
    administrative_area_level_1: "Region",
    administrative_area_level_2: "Planning Area",
  },
  TH: {
    administrative_area_level_1: "Province",
    administrative_area_level_2: "District",
    administrative_area_level_3: "Sub-district",
  },
  ID: {
    administrative_area_level_1: "Province",
    administrative_area_level_2: "Regency",
    administrative_area_level_3: "District",
  },
  PH: {
    administrative_area_level_1: "Region",
    administrative_area_level_2: "Province",
    administrative_area_level_3: "Municipality",
  },
  VN: {
    administrative_area_level_1: "Province",
    administrative_area_level_2: "District",
    administrative_area_level_3: "Commune",
  },
  TW: {
    administrative_area_level_1: "County",
    administrative_area_level_2: "District",
    administrative_area_level_3: "Township",
  },
  AU: {
    administrative_area_level_1: "State",
    administrative_area_level_2: "Local Government Area",
  },
  NZ: {
    administrative_area_level_1: "Region",
    administrative_area_level_2: "District",
  },

  // ── Americas ──────────────────────────────────────────────────────────
  US: {
    administrative_area_level_1: "State",
    administrative_area_level_2: "County",
  },
  CA: {
    administrative_area_level_1: "Province",
    administrative_area_level_2: "Regional District",
  },
  MX: {
    administrative_area_level_1: "State",
    administrative_area_level_2: "Municipality",
  },
  BR: {
    administrative_area_level_1: "State",
    administrative_area_level_2: "Municipality",
  },
  AR: {
    administrative_area_level_1: "Province",
    administrative_area_level_2: "Department",
  },
  CO: {
    administrative_area_level_1: "Department",
    administrative_area_level_2: "Municipality",
  },
  CL: {
    administrative_area_level_1: "Region",
    administrative_area_level_2: "Province",
    administrative_area_level_3: "Commune",
  },
  PE: {
    administrative_area_level_1: "Region",
    administrative_area_level_2: "Province",
    administrative_area_level_3: "District",
  },

  // ── Europe ────────────────────────────────────────────────────────────
  GB: {
    administrative_area_level_1: "Nation",
    administrative_area_level_2: "County",
  },
  FR: {
    administrative_area_level_1: "Region",
    administrative_area_level_2: "Department",
    locality: "Commune",
  },
  DE: {
    administrative_area_level_1: "Federal State",
    administrative_area_level_2: "District",
    administrative_area_level_3: "Municipality",
  },
  IT: {
    administrative_area_level_1: "Region",
    administrative_area_level_2: "Province",
    administrative_area_level_3: "Municipality",
  },
  ES: {
    administrative_area_level_1: "Autonomous Community",
    administrative_area_level_2: "Province",
    administrative_area_level_3: "Municipality",
  },
  NL: {
    administrative_area_level_1: "Province",
    administrative_area_level_2: "Municipality",
  },
  BE: {
    administrative_area_level_1: "Region",
    administrative_area_level_2: "Province",
    administrative_area_level_3: "Municipality",
  },
  SE: {
    administrative_area_level_1: "County",
    administrative_area_level_2: "Municipality",
  },
  NO: {
    administrative_area_level_1: "County",
    administrative_area_level_2: "Municipality",
  },
  DK: {
    administrative_area_level_1: "Region",
    administrative_area_level_2: "Municipality",
  },
  FI: {
    administrative_area_level_1: "Region",
    administrative_area_level_2: "Municipality",
  },
  PL: {
    administrative_area_level_1: "Voivodeship",
    administrative_area_level_2: "County",
    administrative_area_level_3: "Commune",
  },
  CH: {
    administrative_area_level_1: "Canton",
    administrative_area_level_2: "District",
    administrative_area_level_3: "Municipality",
  },
  AT: {
    administrative_area_level_1: "Federal State",
    administrative_area_level_2: "District",
    administrative_area_level_3: "Municipality",
  },
  PT: {
    administrative_area_level_1: "District",
    administrative_area_level_2: "Municipality",
  },
  IE: {
    administrative_area_level_1: "Province",
    administrative_area_level_2: "County",
  },
  RU: {
    administrative_area_level_1: "Federal Subject",
    administrative_area_level_2: "District",
    administrative_area_level_3: "Municipality",
  },
  GR: {
    administrative_area_level_1: "Region",
    administrative_area_level_2: "Regional Unit",
    administrative_area_level_3: "Municipality",
  },
  CZ: {
    administrative_area_level_1: "Region",
    administrative_area_level_2: "District",
  },
  RO: {
    administrative_area_level_1: "County",
    administrative_area_level_2: "Municipality",
  },
  HU: {
    administrative_area_level_1: "County",
    administrative_area_level_2: "District",
  },

  // ── Middle East & Africa ──────────────────────────────────────────────
  AE: {
    administrative_area_level_1: "Emirate",
    administrative_area_level_2: "District",
  },
  SA: {
    administrative_area_level_1: "Region",
    administrative_area_level_2: "Governorate",
  },
  ZA: {
    administrative_area_level_1: "Province",
    administrative_area_level_2: "District Municipality",
    administrative_area_level_3: "Local Municipality",
  },
  NG: {
    administrative_area_level_1: "State",
    administrative_area_level_2: "Local Government Area",
  },
  EG: {
    administrative_area_level_1: "Governorate",
    administrative_area_level_2: "District",
  },
  KE: {
    administrative_area_level_1: "County",
    administrative_area_level_2: "Sub-county",
  },
  TR: {
    administrative_area_level_1: "Province",
    administrative_area_level_2: "District",
  },
  IL: {
    administrative_area_level_1: "District",
    administrative_area_level_2: "Sub-district",
  },
  QA: {
    administrative_area_level_1: "Municipality",
    administrative_area_level_2: "Zone",
  },
  BH: {
    administrative_area_level_1: "Governorate",
    administrative_area_level_2: "District",
  },
  KW: {
    administrative_area_level_1: "Governorate",
    administrative_area_level_2: "District",
  },
  OM: {
    administrative_area_level_1: "Governorate",
    administrative_area_level_2: "Wilayat",
  },
};

/**
 * Returns the country-specific label for a Google address component type,
 * or undefined if the country/type combination has no override.
 */
export function getCountryAdminLabel(
  countryCode: string,
  adminType: string,
): string | undefined {
  const labels = COUNTRY_ADMIN_LABELS[countryCode.toUpperCase()];
  if (!labels) return undefined;
  return labels[adminType as keyof CountryAdminLabels];
}
