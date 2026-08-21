import { useLazyGetInventoryRecommendationListQuery } from "@services/inventory/inventorySlice";
import { useEffect, useRef, useState } from "react";
import type { InventoryRecommendationItem } from "src/types/inventory.types";

const PAGE_SIZE = 100;
const MAX_PAGES = 50; // safety cap

export interface RecommendationScore {
  score: number;
  components: InventoryRecommendationItem["componentScores"];
  /** Availability annotation for the plan's dates from the recommendation run. */
  availability?: InventoryRecommendationItem["availability"];
  /**
   * True when the run excluded this inventory (e.g. sold out / blocked for the
   * plan's dates) — Step 4 shows an explicit "unavailable" state for these.
   */
  isExcluded?: boolean;
}

export type RecommendationScoreMap = Record<string, RecommendationScore>;

/**
 * Builds a `referenceId -> { finalScore, componentScores }` map from a
 * recommendation run's results (the same source the old recommendation list
 * used for its smart score). Pages through all results. Returns {} when
 * disabled / not yet loaded.
 */
export function useRecommendationScores(
  campaignId: string,
  runId: string | undefined,
  enabled: boolean,
): RecommendationScoreMap {
  const [scores, setScores] = useState<RecommendationScoreMap>({});
  const [fetchList] = useLazyGetInventoryRecommendationListQuery();

  const fetchRef = useRef(fetchList);
  fetchRef.current = fetchList;
  const tokenRef = useRef(0);

  useEffect(() => {
    if (!enabled || !runId || !campaignId) {
      tokenRef.current += 1;
      setScores({});
      return;
    }

    tokenRef.current += 1;
    const token = tokenRef.current;

    (async () => {
      const map: RecommendationScoreMap = {};
      try {
        for (let page = 0; page < MAX_PAGES; page++) {
          const res = await fetchRef
            .current({ campaignId, runId, page, size: PAGE_SIZE })
            .unwrap();
          if (token !== tokenRef.current) return; // superseded

          const recs = res?.data?.recommendations ?? [];
          recs.forEach((rec) => {
            if (rec.referenceId) {
              map[rec.referenceId] = {
                score: Number((rec.finalScore ?? 0).toFixed(2)),
                components: rec.componentScores,
                availability: rec.availability,
                isExcluded: rec.isExcluded ?? false,
              };
            }
          });

          if (!res?.data?.pagination?.hasNext) break;
        }
        if (token === tokenRef.current) setScores(map);
      } catch (error) {
        if (token === tokenRef.current) setScores({});
        console.error("Error loading recommendation scores:", error);
      }
    })();

    return () => {
      tokenRef.current += 1;
    };
  }, [campaignId, runId, enabled]);

  return scores;
}
