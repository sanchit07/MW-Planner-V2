import type {
  AvailabilitySyncInfo,
  InventoryAvailabilityData,
} from "src/types/price-management.types";

import type { SpotStatus } from "./inventoryavailability.utils";
import { getColorFromPercentage } from "./inventoryavailability.utils";

export interface StatusConfig {
  label: string;
  bgColor: string;
  textColor: string;
  progressColor:
    | "success"
    | "error"
    | "secondary"
    | "warning"
    | "primary"
    | "info"
    | "neutral"
    | "teal"
    | "lightGreen"
    | "orange"
    | undefined;
  showProgress: boolean;
}

type TFunction = (key: string) => string;

export function getStatusConfig(
  status: SpotStatus,
  bookedPercentage: number,
  showProgress: boolean = true,
  t?: TFunction,
): StatusConfig {
  const label = (key: string, fallback: string) =>
    t ? t(`calendar.${key}.label`) : fallback;

  switch (status) {
    case "available":
      return {
        label: label("available", "Available"),
        bgColor: "white",
        textColor: "text-mw-black",
        progressColor: getColorFromPercentage(bookedPercentage),
        showProgress: showProgress,
      };
    case "booked":
      return {
        label: label("booked", "Booked"),
        bgColor: "bg-mw-error-50",
        textColor: "text-mw-error-500",
        progressColor: getColorFromPercentage(bookedPercentage),
        showProgress: showProgress,
      };
    case "reserved":
      return {
        label: label("reserved", "Reserved"),
        bgColor: "bg-mw-primary-50",
        textColor: "text-mw-primary-500",
        progressColor: "primary",
        showProgress: bookedPercentage === 100 ? false : showProgress,
      };
    case "blocked":
      return {
        label: label("blocked", "Blocked"),
        bgColor: "bg-mw-neutral-100",
        textColor: "text-mw-neutral-500",
        progressColor: undefined,
        showProgress: false,
      };
    case "fully_booked":
      return {
        label: label("fully_booked", "Fully Booked"),
        bgColor: "bg-mw-error-200",
        textColor: "text-mw-error-500",
        progressColor: "error",
        showProgress: false,
      };
    default:
      return {
        label: label("available", "Available"),
        bgColor: "bg-white",
        textColor: "text-mw-black",
        progressColor: getColorFromPercentage(bookedPercentage),
        showProgress: showProgress,
      };
  }
}

export function formatDateForTooltip(date: Date): string {
  return date.toLocaleDateString("en-GB", {
    day: "2-digit",
    month: "long",
    year: "numeric",
  });
}

export function formatTimeForTooltip(hour: number): string {
  return `${String(hour).padStart(2, "0")}:00`;
}

export function parseAvailabilityResponse(
  response: unknown,
): Record<string, InventoryAvailabilityData> | null {
  if (!response || typeof response !== "object") return null;
  const r = response as Record<string, unknown>;
  if (r.success === true && r.data && typeof r.data === "object") {
    const data = (r.data as Record<string, unknown>).inventories;
    if (data && typeof data === "object" && data !== null) {
      return data as Record<string, InventoryAvailabilityData>;
    }
  }
  if (
    "inventories" in r &&
    typeof r.inventories === "object" &&
    r.inventories !== null
  ) {
    return r.inventories as Record<string, InventoryAvailabilityData>;
  }
  return null;
}

/**
 * Extract the IMS sync metadata (`{ lastSyncedAt, status, error }`) from an
 * availability response — either enveloped (`{success, data: {...}}`) or direct.
 */
export function parseAvailabilitySync(
  response: unknown,
): AvailabilitySyncInfo | null {
  if (!response || typeof response !== "object") return null;
  const r = response as Record<string, unknown>;
  const container =
    r.success === true && r.data && typeof r.data === "object"
      ? (r.data as Record<string, unknown>)
      : r;
  const sync = container.sync;
  if (!sync || typeof sync !== "object") return null;
  return sync as AvailabilitySyncInfo;
}

/** Availability data older than this is considered stale (matches the 6h sync cadence). */
export const AVAILABILITY_STALE_THRESHOLD_MS = 6 * 60 * 60 * 1000;

/**
 * Whether the availability data behind `sync` is stale: last synced more than
 * {@link AVAILABILITY_STALE_THRESHOLD_MS} ago. A missing/unparseable timestamp
 * is not treated as stale (the "not synced yet" label covers that case).
 */
export function isAvailabilitySyncStale(
  sync: AvailabilitySyncInfo | null | undefined,
  nowMs: number = Date.now(),
): boolean {
  if (!sync?.lastSyncedAt) return false;
  const syncedMs = Date.parse(sync.lastSyncedAt);
  if (Number.isNaN(syncedMs)) return false;
  return nowMs - syncedMs > AVAILABILITY_STALE_THRESHOLD_MS;
}

/**
 * Which warning (if any) the availability sync metadata warrants:
 * - "failed"  — the last IMS sync FAILED (takes precedence),
 * - "stale"   — data is older than the staleness threshold,
 * - null      — fresh data from a successful sync; no warning.
 */
export function getAvailabilitySyncWarning(
  sync: AvailabilitySyncInfo | null | undefined,
  nowMs: number = Date.now(),
): "failed" | "stale" | null {
  if (!sync) return null;
  if (sync.status === "FAILED") return "failed";
  if (isAvailabilitySyncStale(sync, nowMs)) return "stale";
  return null;
}
