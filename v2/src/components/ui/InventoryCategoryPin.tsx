import { useId } from "react";

/** Base type → pin color. */
const CLASSIC_COLOR = "#717171";
const DIGITAL_COLOR = "#4CB0E4";

type Base = "classic" | "digital";
type Variant = "plain" | "transit" | "retail";

export interface InventoryCategory {
  base: Base;
  variant: Variant;
}

/** Coerce a venueType (string | string[] | other) to a single lowercase string. */
function venueTypeToString(venueType: unknown): string {
  if (Array.isArray(venueType)) return venueType.join(" ").toLowerCase();
  if (typeof venueType === "string") return venueType.toLowerCase();
  return "";
}

/**
 * Classify an inventory into one of the 6 pin categories from its type +
 * venueType. `transit` wins over `retail`; anything else is plain
 * classic/digital. One substring match is enough (case-insensitive).
 */
export function getInventoryCategory(
  inventoryType: string | undefined,
  venueType: unknown,
): InventoryCategory {
  const base: Base = (inventoryType ?? "").toLowerCase().includes("digital")
    ? "digital"
    : "classic";
  const v = venueTypeToString(venueType);
  let variant: Variant = "plain";
  if (v.includes("transit")) variant = "transit";
  else if (v.includes("retail")) variant = "retail";
  return { base, variant };
}

const TEARDROP =
  "M15.0351 0H0.964861C0.434099 0 0 0.380222 0 0.845111V13.1691C0 13.6338 0.434099 14.0142 0.964861 14.0142H5.06817C5.26719 14.0142 5.44727 14.1323 5.52666 14.3148L7.5415 18.9461C7.71583 19.3468 8.28416 19.3468 8.45849 18.9461L10.4733 14.3145C10.5527 14.132 10.7328 14.014 10.9318 14.014H15.0351C15.5659 14.014 16 13.6338 16 13.1689V0.845111C15.9997 0.380222 15.5656 0 15.0351 0ZM10.9918 8.78244C10.9918 9.24733 10.5574 9.62756 10.0269 9.62756H5.97311C5.4426 9.62756 5.00825 9.24733 5.00825 8.78244V5.23156C5.00825 4.76689 5.4426 4.38644 5.97311 4.38644H10.0271C10.5577 4.38644 10.992 4.76667 10.992 5.23156L10.9918 8.78244Z";

const Glyph = ({ variant, color }: { variant: Variant; color: string }) => {
  const stroke = {
    stroke: color,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
  };
  if (variant === "transit") {
    return (
      <>
        <path
          d="M8.66602 9.00004V5.00004C8.66602 4.82323 8.59578 4.65366 8.47075 4.52864C8.34573 4.40361 8.17616 4.33337 7.99935 4.33337H5.33268C5.15587 4.33337 4.9863 4.40361 4.86128 4.52864C4.73625 4.65366 4.66602 4.82323 4.66602 5.00004V8.66671C4.66602 8.75511 4.70113 8.8399 4.76365 8.90241C4.82616 8.96492 4.91094 9.00004 4.99935 9.00004H5.66602"
          {...stroke}
        />
        <path d="M9 9H7" {...stroke} />
        <path
          d="M10.3327 8.99996H10.9993C11.0878 8.99996 11.1725 8.96484 11.2351 8.90233C11.2976 8.83982 11.3327 8.75503 11.3327 8.66663V7.44996C11.3325 7.37431 11.3067 7.30096 11.2593 7.24196L10.0993 5.79196C10.0682 5.75292 10.0286 5.72139 9.98362 5.69969C9.93861 5.678 9.88931 5.6667 9.83935 5.66663H8.66602"
          {...stroke}
        />
        <path
          d="M9.66667 9.66671C10.0349 9.66671 10.3333 9.36823 10.3333 9.00004C10.3333 8.63185 10.0349 8.33337 9.66667 8.33337C9.29848 8.33337 9 8.63185 9 9.00004C9 9.36823 9.29848 9.66671 9.66667 9.66671Z"
          {...stroke}
        />
        <path
          d="M6.33268 9.66671C6.70087 9.66671 6.99935 9.36823 6.99935 9.00004C6.99935 8.63185 6.70087 8.33337 6.33268 8.33337C5.96449 8.33337 5.66602 8.63185 5.66602 9.00004C5.66602 9.36823 5.96449 9.66671 6.33268 9.66671Z"
          {...stroke}
        />
      </>
    );
  }
  if (variant === "retail") {
    return (
      <>
        <path
          d="M9 10V8.33333C9 8.24493 8.96488 8.16014 8.90237 8.09763C8.83986 8.03512 8.75507 8 8.66667 8H7.33333C7.24493 8 7.16014 8.03512 7.09763 8.09763C7.03512 8.16014 7 8.24493 7 8.33333V10"
          {...stroke}
        />
        <path
          d="M9.92432 6.43663C9.85484 6.37011 9.76235 6.33297 9.66616 6.33297C9.56996 6.33297 9.47748 6.37011 9.40799 6.43663C9.25299 6.58447 9.04702 6.66694 8.83283 6.66694C8.61863 6.66694 8.41266 6.58447 8.25766 6.43663C8.18819 6.3702 8.09577 6.33313 7.99966 6.33313C7.90354 6.33313 7.81113 6.3702 7.74166 6.43663C7.58664 6.58456 7.3806 6.6671 7.16633 6.6671C6.95205 6.6671 6.74601 6.58456 6.59099 6.43663C6.5215 6.37011 6.42902 6.33297 6.33283 6.33297C6.23663 6.33297 6.14415 6.37011 6.07466 6.43663C5.92494 6.5795 5.72742 6.66155 5.52053 6.66681C5.31365 6.67207 5.11221 6.60016 4.95543 6.46509C4.79864 6.33001 4.69772 6.14142 4.67232 5.93604C4.64692 5.73066 4.69884 5.52317 4.81799 5.35396L5.78099 3.95929C5.84209 3.86913 5.92436 3.79531 6.02058 3.74429C6.11681 3.69327 6.22408 3.66661 6.33299 3.66663H9.66632C9.77492 3.66658 9.88189 3.69307 9.97792 3.74379C10.0739 3.7945 10.1561 3.86791 10.2173 3.95763L11.1823 5.35496C11.3015 5.5243 11.3534 5.73195 11.3278 5.93744C11.3022 6.14293 11.2011 6.33154 11.044 6.46652C10.887 6.6015 10.6853 6.67318 10.4783 6.6676C10.2714 6.66201 10.0738 6.57955 9.92432 6.43629"
          {...stroke}
        />
        <path
          d="M5.33398 6.65002V9.33336C5.33398 9.51017 5.40422 9.67974 5.52925 9.80476C5.65427 9.92979 5.82384 10 6.00065 10H10.0007C10.1775 10 10.347 9.92979 10.4721 9.80476C10.5971 9.67974 10.6673 9.51017 10.6673 9.33336V6.65002"
          {...stroke}
        />
      </>
    );
  }
  // plain — screen/display
  return (
    <>
      <path d="M4.66602 4H11.3327" {...stroke} />
      <path
        d="M11 4V6.82051C11 6.95652 10.9298 7.08696 10.8047 7.18313C10.6797 7.2793 10.5101 7.33333 10.3333 7.33333H5.66667C5.48986 7.33333 5.32029 7.2793 5.19526 7.18313C5.07024 7.08696 5 6.95652 5 6.82051V4"
        {...stroke}
      />
      <path d="M8 10V7.33337" {...stroke} />
    </>
  );
};

interface InventoryCategoryPinProps {
  inventoryType: string | undefined;
  venueType: unknown;
  /** Pin width in px (height scales 20/16). */
  pinSize?: number;
  /** Selected for the campaign — adds a green glow/ring. */
  selected?: boolean;
}

const SELECTED_GLOW = "#16a34a";

/**
 * Teardrop inventory map pin coloured + glyphed by category
 * (classic/digital × plain/transit/retail). Self-contained — replaces the
 * generic POI photo pin for inventory markers.
 */
const InventoryCategoryPin: React.FC<InventoryCategoryPinProps> = ({
  inventoryType,
  venueType,
  pinSize = 28,
  selected = false,
}) => {
  const clipId = useId();
  const { base, variant } = getInventoryCategory(inventoryType, venueType);
  const color = base === "digital" ? DIGITAL_COLOR : CLASSIC_COLOR;
  const width = pinSize;
  const height = Math.round(pinSize * (20 / 16));

  return (
    <svg
      width={width}
      height={height}
      viewBox="0 0 16 20"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      style={{
        display: "block",
        filter: selected
          ? `drop-shadow(0 0 5px ${SELECTED_GLOW}) drop-shadow(0 0 2px ${SELECTED_GLOW})`
          : "drop-shadow(0 2px 3px rgba(0,0,0,0.35))",
      }}
    >
      {selected && (
        <circle
          cx="8"
          cy="7"
          r="7.4"
          fill="none"
          stroke={SELECTED_GLOW}
          strokeWidth="1.2"
        />
      )}
      <path d={TEARDROP} fill={color} />
      <rect
        width="14"
        height="12"
        rx="1"
        transform="matrix(-1 0 0 1 15 1)"
        fill="white"
      />
      <g clipPath={`url(#${clipId})`}>
        <Glyph variant={variant} color={color} />
      </g>
      <defs>
        <clipPath id={clipId}>
          <rect width="8" height="8" fill="white" transform="translate(4 3)" />
        </clipPath>
      </defs>
    </svg>
  );
};

export default InventoryCategoryPin;
