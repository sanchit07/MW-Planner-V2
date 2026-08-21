import {
  useLazyGenerateInventoryRecommendationQuery,
  useLazyGetCampaignForecastQuery,
} from "@services/inventory/inventorySlice";
import { useCallback, useEffect, useRef, useState } from "react";

import { getNextPollDelayMs } from "./recommendationProgress";
import {
  setForecastData as setForecastDataRedux,
  setRecommendationRun,
} from "../../../../services/campaign/campaignSlice";
import {
  type RootState,
  useAppDispatch,
  useAppSelector,
} from "../../../../store";
import type { CampaignForecastData } from "../../../../types/inventory.types";

export type RecommendationForecastStatus =
  | "generating"
  | "completed"
  | "failed";

export const EMPTY_FORECAST: CampaignForecastData = {
  totalInventories: 0,
  estimatedImpression: 0,
  estimatedReach: 0,
  estimatedFrequency: 0,
  estimatedAdPlays: 0,
  sov: 0,
  avgCpm: 0,
  avgECpm: 0,
  totalCost: 0,
  plannedSot: 0,
  totalSot: 0,
  warnings: [],
};

export interface UseRecommendationForecastResult {
  status: RecommendationForecastStatus;
  progress: number;
  forecastData: CampaignForecastData;
  runId: string;
  /** Re-run generation from scratch (used by the failed-state Retry button). */
  retry: () => void;
  /**
   * Restore the AI recommendation: forces a brand-new backend run by sending
   * `forceRegenerate=true` on the first /generate call only. (A media-channel
   * change also sends the flag automatically; the failed-state `retry` never
   * does.)
   */
  regenerateFromRestore: () => void;
  /** Re-fetch only the forecast (used after the selection changes). */
  refetchForecast: () => void;
}

/** Order-insensitive equality for the media-channel selection. */
function sameChannels(a?: string[], b?: string[]): boolean {
  const setA = new Set(a ?? []);
  const setB = new Set(b ?? []);
  if (setA.size !== setB.size) return false;
  for (const v of setA) if (!setB.has(v)) return false;
  return true;
}

/**
 * Drives the inventory step's Plan Summary data:
 *   generate recommendation → poll until COMPLETED → fetch forecast.
 *
 * A completed run is cached in Redux keyed by `signature` (a digest of the
 * inputs that affect the recommendation). While the signature matches, the run
 * is reused and only the forecast is fetched — no regeneration.
 */
export function useRecommendationForecast(
  campaignId: string,
  signature: string,
  mediaOwnerIds: string[] = [],
  mediaChannels: string[] = [],
  /**
   * When false, /generate is never called — only the forecast for the current
   * selection is fetched (skip-recommendation / manual mode). Flipping it back
   * to true starts a normal run.
   */
  generationEnabled: boolean = true,
): UseRecommendationForecastResult {
  const dispatch = useAppDispatch();
  const cachedRun = useAppSelector(
    (state: RootState) => state.campaign.recommendationRun,
  );

  const [status, setStatus] =
    useState<RecommendationForecastStatus>("generating");
  const [progress, setProgress] = useState(0);
  const [forecastData, setForecastData] =
    useState<CampaignForecastData>(EMPTY_FORECAST);
  const [runId, setRunId] = useState("");

  const [generateRecommendation] =
    useLazyGenerateInventoryRecommendationQuery();
  const [fetchForecast] = useLazyGetCampaignForecastQuery();

  // Latest values held in refs so the run effect doesn't depend on them.
  const cachedRunRef = useRef(cachedRun);
  cachedRunRef.current = cachedRun;
  const generateRef = useRef(generateRecommendation);
  generateRef.current = generateRecommendation;
  const fetchForecastRef = useRef(fetchForecast);
  fetchForecastRef.current = fetchForecast;
  // Media-owner ids (company + child companies) sent to /generate so a media
  // owner's recommendation is scoped to their own inventory. Held in a ref so
  // the run effect doesn't depend on the array identity.
  const mediaOwnerIdsRef = useRef(mediaOwnerIds);
  mediaOwnerIdsRef.current = mediaOwnerIds;
  // Current media-channel selection. Held in a ref so the run effect doesn't
  // depend on the array identity; compared against the cached run's channels to
  // decide whether generation must be forced.
  const mediaChannelsRef = useRef(mediaChannels);
  mediaChannelsRef.current = mediaChannels;

  // Cancellation: bump the token to invalidate any in-flight poll/fetch.
  const runTokenRef = useRef(0);
  const pollTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pollAttemptRef = useRef(0);
  // Forces a fresh generation regardless of cache (retry).
  const forceRegenerateRef = useRef(false);

  const loadForecast = useCallback(async () => {
    if (!campaignId) return;
    try {
      const token = runTokenRef.current;
      const mediaOwnerIds = mediaOwnerIdsRef.current;
      const result = await fetchForecastRef
        .current({
          campaignId,
          ...(mediaOwnerIds.length > 0 ? { mediaOwnerIds } : {}),
        })
        .unwrap();
      if (token !== runTokenRef.current) return; // superseded
      if (result.success && result.data) {
        setForecastData({
          ...result.data,
          plannedSot: result.data.plannedSot ?? 0,
          totalInventories: result.data.totalInventories ?? 0,
        });
        dispatch(setForecastDataRedux(result.data));
      }
    } catch (error) {
      console.error("Error loading forecast data:", error);
    }
  }, [campaignId, dispatch]);

  const refetchForecast = useCallback(() => {
    loadForecast();
  }, [loadForecast]);

  const startRun = useCallback(
    (forceApiRegenerate = false) => {
      if (!campaignId) return;

      // Sent on this run's FIRST /generate call, then cleared so the following
      // status-poll calls omit it. Restore-AI-recommendation is the only caller
      // that passes it true.
      let sendForceRegenerate = forceApiRegenerate;

      // Invalidate any prior run, then take this run's token.
      runTokenRef.current += 1;
      const token = runTokenRef.current;
      if (pollTimeoutRef.current !== null) {
        clearTimeout(pollTimeoutRef.current);
        pollTimeoutRef.current = null;
      }
      pollAttemptRef.current = 0;

      // Manual (skip-recommendation) mode: never call /generate — just load
      // the forecast for whatever is currently selected.
      if (!generationEnabled) {
        setProgress(100);
        loadForecast().finally(() => {
          if (token === runTokenRef.current) setStatus("completed");
        });
        return;
      }

      const cached = cachedRunRef.current;
      if (
        !forceRegenerateRef.current &&
        cached &&
        cached.signature === signature
      ) {
        // Reuse the completed run — only refresh the forecast. Stay in the
        // generating state (progress shown) until the forecast resolves, so the
        // empty state doesn't flash with a stale 0 count.
        setRunId(cached.runId);
        setProgress(100);
        loadForecast().finally(() => {
          if (token === runTokenRef.current) setStatus("completed");
        });
        return;
      }
      forceRegenerateRef.current = false;

      // A media-channel change must force a backend rebuild — the backend would
      // otherwise return the run it cached for the old channels. (Other input
      // changes fall through to a plain regenerate.)
      if (
        cached &&
        !sameChannels(cached.mediaChannels, mediaChannelsRef.current)
      ) {
        sendForceRegenerate = true;
      }

      setStatus("generating");
      setProgress(0);

      const poll = () => {
        const forceRegenerate = sendForceRegenerate;
        sendForceRegenerate = false;
        const mediaOwnerIds = mediaOwnerIdsRef.current;
        generateRef
          .current({
            campaignId,
            forceRegenerate,
            ...(mediaOwnerIds.length > 0 ? { mediaOwnerIds } : {}),
          })
          .then((result) => {
            if (token !== runTokenRef.current) return; // superseded

            if (result.isError) {
              setStatus("failed");
              return;
            }

            const data = result.data?.data;
            const runStatus = data?.status;
            setProgress(data?.completionPercentage ?? 0);

            if (runStatus === "COMPLETED") {
              const completedRunId = data?.runId || "";
              setRunId(completedRunId);
              setProgress(100);
              dispatch(
                setRecommendationRun({
                  runId: completedRunId,
                  signature,
                  mediaChannels: mediaChannelsRef.current,
                }),
              );
              // Mark completed only after the forecast resolves, so the empty
              // state never flashes with a stale 0 count while it loads.
              loadForecast().finally(() => {
                if (token === runTokenRef.current) setStatus("completed");
              });
              return;
            }

            if (runStatus === "FAILED") {
              setStatus("failed");
              return;
            }

            // IN_PROGRESS — schedule the next poll.
            const attempt = pollAttemptRef.current;
            pollAttemptRef.current = attempt + 1;
            pollTimeoutRef.current = setTimeout(() => {
              pollTimeoutRef.current = null;
              if (token === runTokenRef.current) poll();
            }, getNextPollDelayMs(attempt));
          })
          .catch(() => {
            if (token !== runTokenRef.current) return;
            setStatus("failed");
          });
      };

      poll();
    },
    [campaignId, signature, dispatch, loadForecast, generationEnabled],
  );

  const retry = useCallback(() => {
    forceRegenerateRef.current = true;
    startRun();
  }, [startRun]);

  const regenerateFromRestore = useCallback(() => {
    forceRegenerateRef.current = true;
    startRun(true);
  }, [startRun]);

  useEffect(() => {
    startRun();
    return () => {
      // Invalidate the run and stop polling on unmount / input change.
      runTokenRef.current += 1;
      if (pollTimeoutRef.current !== null) {
        clearTimeout(pollTimeoutRef.current);
        pollTimeoutRef.current = null;
      }
    };
  }, [startRun]);

  return {
    status,
    progress,
    forecastData,
    runId,
    retry,
    regenerateFromRestore,
    refetchForecast,
  };
}
