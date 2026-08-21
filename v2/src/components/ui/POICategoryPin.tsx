import {
  POI_PIN_ICONS,
  type POIPinCategory,
} from "@constants/poi-pin-icons.generated";
import { useId } from "react";

/**
 * Inline-SVG POI map pins, keyed by the product's 101-category taxonomy
 * (see `poi-pin-icons.generated.ts`). Replaces the remote `.jpg` POI icons
 * (old `poi-icon-url.ts` pipeline). Self-contained — no network.
 *
 * `resolvePinCategory()` maps a backend POI's `primaryType` /
 * `primaryTypeDisplayName` to a pin key, covering Google-Places synonyms and
 * spelling differences, and falls back to `others` so every POI gets a pin.
 */

/**
 * Backend / Google-Places type → pin key, for values that differ in spelling
 * from our keys or aren't 1:1. Direct matches (key already in POI_PIN_ICONS)
 * skip this. Keep additions conservative.
 */
const ALIASES: Record<string, POIPinCategory> = {
  // transit
  bus_station: "bus_stand",
  // health
  dentist: "dental",
  drugstore: "drug_store",
  health: "hospital",
  // retail spelling
  electronics_store: "electronic_store",
  jewelry_store: "jewellery_shop",
  // services
  funeral_home: "funeral_house",
  // entertainment
  movie_theater: "movie_theatre",
  // civic
  police: "police_station",
  // roads / generic
  intersection: "junction",
  point_of_interest: "others",
  establishment: "others",
  premise: "others",
  // food
  food: "restaurant",
  // worship
  place_of_worship: "church",
};

const norm = (v: string): string =>
  v
    .trim()
    .toLowerCase()
    .replace(/\s+/g, "_")
    .replace(/[^a-z0-9_]/g, "");

const lookup = (v?: string | null): POIPinCategory | null => {
  if (!v) return null;
  const k = norm(v);
  if (k in POI_PIN_ICONS) return k as POIPinCategory;
  if (k in ALIASES) return ALIASES[k];
  return null;
};

/**
 * Resolve a POI to a pin category. Tries `primaryType`, then
 * `primaryTypeDisplayName`, then falls back to `others` when either input is
 * present. Returns `null` only when there is nothing to resolve (caller may
 * then use the legacy `.jpg` / generic marker).
 */
export const resolvePinCategory = (
  primaryType?: string | null,
  primaryTypeDisplayName?: string | null,
): POIPinCategory | null => {
  return (
    lookup(primaryType) ??
    lookup(primaryTypeDisplayName) ??
    (primaryType || primaryTypeDisplayName ? "others" : null)
  );
};

const SHARED_PIN = (
  <>
    <path
      d="M20 10C20 14.993 14.461 20.193 12.601 21.799C12.4277 21.9293 12.2168 21.9998 12 21.9998C11.7832 21.9998 11.5723 21.9293 11.399 21.799C9.539 20.193 4 14.993 4 10C4 7.87827 4.84285 5.84344 6.34315 4.34315C7.84344 2.84285 9.87827 2 12 2C14.1217 2 16.1566 2.84285 17.6569 4.34315C19.1571 5.84344 20 7.87827 20 10Z"
      fill="white"
      stroke="white"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="M12 13C13.6569 13 15 11.6569 15 10C15 8.34315 13.6569 7 12 7C10.3431 7 9 8.34315 9 10C9 11.6569 10.3431 13 12 13Z"
      fill="white"
      stroke="white"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </>
);

/** Prefix every `id`, `url(#…)` and `href="#…"` so multiple pins never collide. */
const namespaceIds = (markup: string, prefix: string): string =>
  markup
    .replace(/id="([^"]+)"/g, (_m, id) => `id="${prefix}-${id}"`)
    .replace(/url\(#([^)]+)\)/g, (_m, id) => `url(#${prefix}-${id})`)
    .replace(
      /(xlink:href|href)="#([^"]+)"/g,
      (_m, attr, id) => `${attr}="#${prefix}-${id}"`,
    );

/** True when an inline pin exists for this resolved category. */
export const hasPOIPin = (
  category: POIPinCategory | null | undefined,
): category is POIPinCategory => !!category && category in POI_PIN_ICONS;

interface POICategoryPinProps {
  category: POIPinCategory;
  /** Pin width in px (height matches; viewBox is square). */
  pinSize?: number;
}

/**
 * Teardrop POI map pin coloured + glyphed by category. Self-contained inline
 * SVG; replaces the remote `.jpg` icon. No selected state (POIs aren't
 * selectable).
 */
const POICategoryPin: React.FC<POICategoryPinProps> = ({
  category,
  pinSize = 40,
}) => {
  const rawId = useId();
  const prefix = rawId.replace(/[^a-zA-Z0-9_-]/g, "");
  const icon = POI_PIN_ICONS[category];
  if (!icon) return null;

  return (
    <svg
      width={pinSize}
      height={pinSize}
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      style={{
        display: "block",
        filter: "drop-shadow(0 2px 3px rgba(0,0,0,0.35))",
      }}
    >
      {SHARED_PIN}
      <path
        d="M12 18C16.4183 18 20 14.4183 20 10C20 5.58172 16.4183 2 12 2C7.58172 2 4 5.58172 4 10C4 14.4183 7.58172 18 12 18Z"
        fill={icon.color}
        stroke="white"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <g
        dangerouslySetInnerHTML={{
          __html: namespaceIds(icon.glyph, prefix),
        }}
      />
    </svg>
  );
};

export default POICategoryPin;
