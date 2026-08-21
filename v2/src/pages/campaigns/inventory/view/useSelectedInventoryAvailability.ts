import { useLazyGetInventoryAvailabilityQuery } from "@services/inventory/inventorySlice";
import { toISODateString } from "@utils/dateUtils";
import {
  buildAvailabilityIndex,
  getAvailabilityPercentFromIndex,
} from "@utils/inventoryavailability.utils";
import { parseAvailabilityResponse } from "@utils/inventoryAvailabilityUI.utils";
import { useCallback, useRef, useState } from "react";
import type { InventoryItem } from "src/types/inventory.types";

/** Availability display state for a single inventory (keyed by `detail.id`). */
export interface AvailabilityEntry {
  loading: boolean;
  /** Rounded availability percentage, or `null` when it can't be computed. */
  percent: number | null;
}

interface UseSelectedInventoryAvailabilityParams {
  /** Campaign start date (ISO string, may include a time component). */
  startDate?: string;
  /** Campaign end date (ISO string, may include a time component). */
  endDate?: string;
}

interface UseSelectedInventoryAvailabilityResult {
  availabilityById: Record<string, AvailabilityEntry>;
  /**
   * Fetches availability for a single inventory over the campaign range.
   * No-op when campaign dates are missing, the inventory lacks an
   * `externalId`, or it has already been requested.
   */
  requestAvailability: (item: InventoryItem) => void;
}

/**
 * Parses a campaign date string (either `YYYY-MM-DD` or a full ISO datetime)
 * into a local `Date` at midnight. Returns `null` for missing/invalid input.
 */
const parseCampaignDate = (value?: string): Date | null => {
  if (!value) return null;
  const [datePart] = value.split("T");
  const [year, month, day] = datePart.split("-").map(Number);
  if (
    Number.isNaN(year) ||
    Number.isNaN(month) ||
    Number.isNaN(day) ||
    !year ||
    !month ||
    !day
  ) {
    return null;
  }
  return new Date(year, month - 1, day);
};

/**
 * Computes an availability percentage per inventory over the campaign date
 * range, fetching booking data on demand.
 *
 * Availability is fetched lazily — call `requestAvailability(item)` when the
 * user expands an inventory card. Each inventory is fetched at most once (its
 * result is cached in the returned map, keyed by `detail.id`); re-expanding
 * never refetches.
 *
 * When campaign dates are missing, no fetch happens and entries stay unset
 * (the card shows a placeholder).
 */
export function useSelectedInventoryAvailability({
  startDate,
  endDate,
}: UseSelectedInventoryAvailabilityParams): UseSelectedInventoryAvailabilityResult {
  const [getInventoryAvailability] = useLazyGetInventoryAvailabilityQuery();
  const [availabilityById, setAvailabilityById] = useState<
    Record<string, AvailabilityEntry>
  >({});
  // externalIds already requested — prevents duplicate fetches across renders
  // and repeated expand/collapse.
  const requestedExternalIds = useRef<Set<string>>(new Set());

  const requestAvailability = useCallback(
    (item: InventoryItem) => {
      const id = item.detail?.id;
      const externalId = item.detail?.externalId;
      if (!id || !externalId) return;
      if (requestedExternalIds.current.has(externalId)) return;

      const start = parseCampaignDate(startDate);
      const end = parseCampaignDate(endDate);
      if (!start || !end) return;

      requestedExternalIds.current.add(externalId);
      setAvailabilityById((prev) => ({
        ...prev,
        [id]: { loading: true, percent: null },
      }));

      void (async () => {
        try {
          const response = await getInventoryAvailability({
            data: {
              inventoryIds: [externalId],
              startTime: `${toISODateString(start)}T00:00:00`,
              endTime: `${toISODateString(end)}T23:59:59`,
            },
          }).unwrap();

          const byExternalId = parseAvailabilityResponse(response);
          const data = byExternalId?.[externalId];
          const index = data ? buildAvailabilityIndex(data, item) : null;
          const percent = index
            ? getAvailabilityPercentFromIndex(index, start, end)
            : null;

          setAvailabilityById((prev) => ({
            ...prev,
            [id]: { loading: false, percent },
          }));
        } catch (error) {
          console.error("Failed to fetch inventory availability:", error);
          setAvailabilityById((prev) => ({
            ...prev,
            [id]: { loading: false, percent: null },
          }));
        }
      })();
    },
    [getInventoryAvailability, startDate, endDate],
  );

  return { availabilityById, requestAvailability };
}
