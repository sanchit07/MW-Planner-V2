import { useMediaOwnerIds } from "@hooks/useMediaOwnerIds";
import {
  useLazyGetAllSelectedInventoryQuery,
  useLazyGetReachSaturationCurveQuery,
} from "@services/inventory/inventorySlice";
import { useCallback, useEffect, useRef, useState } from "react";

export type ReachCurveStatus = "idle" | "loading" | "ready" | "failed";

export interface UseReachCurveResult {
  status: ReachCurveStatus;
  /** Cumulative overall reach (%) per day. */
  overallReach: number[];
  /** Date labels aligned to overallReach (one per day from startDate). */
  labels: string[];
  /** Number of selected inventories the curve is built from. */
  inventoryCount: number;
  /** Re-fetch the curve (used after the selection changes). */
  refetch: () => void;
}

export interface CurveInventory {
  referenceId: string;
  reach: number;
  cpmBudget: number;
}

const MS_PER_DAY = 86_400_000;

/** Inclusive day count between two ISO dates (e.g. 01-01 → 01-30 = 30). */
function inclusiveDays(startDate: string, endDate: string): number {
  const start = new Date(startDate).getTime();
  const end = new Date(endDate).getTime();
  if (Number.isNaN(start) || Number.isNaN(end) || end < start) return 0;
  return Math.round((end - start) / MS_PER_DAY) + 1;
}

/** Date labels (e.g. "Jan 01") starting at startDate, one per index. */
function buildDateLabels(startDate: string, count: number): string[] {
  const start = new Date(startDate);
  if (Number.isNaN(start.getTime())) return [];
  return Array.from({ length: count }, (_, i) => {
    const d = new Date(start);
    d.setDate(start.getDate() + i);
    return d.toLocaleDateString("en-US", { month: "short", day: "2-digit" });
  });
}

/**
 * Builds the Reach Build chart data from the campaign's selected inventories'
 * per-inventory reach + cost → reach-saturation-curve.
 *
 * Source: the campaign's selected-inventory list (`/selected-inventory/all`),
 * whose items carry `performance.estimatedReach` + `performance.estimatedCost`.
 * This is the single source for both the recommendation and skip/manual flows —
 * the selections are the same regardless of how they were made.
 *
 * `overrideInventories` lets a caller supply the per-inventory reach/cost list
 * directly (e.g. the media-plan pages already hold it), so the authed
 * `/selected-inventory/all` fetch is skipped — required on the public page,
 * which has no Bearer token. The reach-saturation-curve endpoint itself is
 * token-less, so the curve still renders. When omitted, the authed fetch runs.
 */
export function useReachCurve(
  campaignId: string,
  enabled: boolean,
  startDate: string | undefined,
  endDate: string | undefined,
  overrideInventories?: CurveInventory[],
): UseReachCurveResult {
  const [status, setStatus] = useState<ReachCurveStatus>("idle");
  const [overallReach, setOverallReach] = useState<number[]>([]);
  const [labels, setLabels] = useState<string[]>([]);
  const [inventoryCount, setInventoryCount] = useState(0);

  const [fetchSelectedInventory] = useLazyGetAllSelectedInventoryQuery();
  const [fetchCurve] = useLazyGetReachSaturationCurveQuery();

  const selectedRef = useRef(fetchSelectedInventory);
  selectedRef.current = fetchSelectedInventory;
  const curveRef = useRef(fetchCurve);
  curveRef.current = fetchCurve;

  const mediaOwnerIds = useMediaOwnerIds();
  const mediaOwnerIdsRef = useRef(mediaOwnerIds);
  mediaOwnerIdsRef.current = mediaOwnerIds;

  const tokenRef = useRef(0);

  // Per-inventory reach + cost from the campaign's selected inventories.
  const fromSelectedInventory = useCallback(
    async (token: number): Promise<CurveInventory[]> => {
      const res = await selectedRef
        .current({
          campaignId,
          ...(mediaOwnerIdsRef.current.length > 0
            ? { mediaOwnerIds: mediaOwnerIdsRef.current }
            : {}),
        })
        .unwrap();
      if (token !== tokenRef.current) return [];
      return (res?.data ?? []).map((item) => ({
        referenceId: item.referenceId,
        reach: item.performance?.estimatedReach ?? 0,
        cpmBudget: item.performance?.estimatedCost ?? 0,
      }));
    },
    [campaignId],
  );

  const load = useCallback(async () => {
    if (!enabled || !startDate || !endDate || !campaignId) {
      tokenRef.current += 1;
      setStatus("idle");
      setOverallReach([]);
      setLabels([]);
      setInventoryCount(0);
      return;
    }

    tokenRef.current += 1;
    const token = tokenRef.current;
    setStatus("loading");

    try {
      // Caller-supplied list (media-plan pages) skips the authed fetch;
      // otherwise fall back to the authed /selected-inventory/all call.
      const inventories =
        overrideInventories !== undefined
          ? overrideInventories
          : await fromSelectedInventory(token);
      if (token !== tokenRef.current) return;

      if (inventories.length === 0) {
        setStatus("idle");
        setOverallReach([]);
        setLabels([]);
        setInventoryCount(0);
        return;
      }

      const curveRes = await curveRef
        .current({
          inventories,
          duration: inclusiveDays(startDate, endDate),
          startDate,
          endDate,
        })
        .unwrap();
      if (token !== tokenRef.current) return;

      const overallRaw =
        curveRes?.data?.[0]?.overallInventories?.overallReach ?? [];
      // The backend can return an all-NaN series (normalized to null by
      // parseReachSaturationResponse) or an all-zero series. Treat a series with
      // no positive value as "no data" so the empty-state message shows instead
      // of a flat chart along the bottom.
      const hasData = overallRaw.some((v) => Number.isFinite(v) && v > 0);
      const overall = hasData ? overallRaw : [];
      setOverallReach(overall);
      setLabels(hasData ? buildDateLabels(startDate, overall.length) : []);
      setInventoryCount(inventories.length);
      setStatus("ready");
    } catch (error) {
      if (token !== tokenRef.current) return;
      console.error("Error loading reach saturation curve:", error);
      setStatus("failed");
    }
  }, [
    enabled,
    campaignId,
    startDate,
    endDate,
    fromSelectedInventory,
    overrideInventories,
  ]);

  const refetch = useCallback(() => {
    load();
  }, [load]);

  useEffect(() => {
    load();
    return () => {
      tokenRef.current += 1;
    };
  }, [load]);

  return { status, overallReach, labels, inventoryCount, refetch };
}
