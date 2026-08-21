import { useBulkSelectInventoryMutation } from "@services/inventory/inventorySlice";
import { useCallback, useState } from "react";
import type { CampaignCreateResponse } from "src/types/campaign.types";

import { buildCampaignTargetingFilters } from "../inventoryFilters.utils";

export interface UseRestoreRecommendationResult {
  /** True while the deselect-all call is in flight (before regeneration). */
  isRestoring: boolean;
  /** Clear every selection, then trigger a fresh recommendation run. */
  restore: () => Promise<void>;
}

/**
 * Restore AI recommendation: bulk-deselects every current pick (the `/generate`
 * step does not unselect priors), then regenerates.
 */
export function useRestoreRecommendation(
  campaignId: string,
  campaignData: CampaignCreateResponse | null,
  regenerate: () => void,
): UseRestoreRecommendationResult {
  const [isRestoring, setIsRestoring] = useState(false);
  const [bulkSelectInventory] = useBulkSelectInventoryMutation();

  const restore = useCallback(async () => {
    if (!campaignId) return;
    setIsRestoring(true);
    try {
      await bulkSelectInventory({
        campaignId,
        operationType: "DESELECT",
        filters: buildCampaignTargetingFilters(campaignData),
      }).unwrap();
    } catch (error) {
      console.error("Failed to deselect all before restore:", error);
    } finally {
      setIsRestoring(false);
    }
    regenerate();
  }, [campaignId, campaignData, bulkSelectInventory, regenerate]);

  return { isRestoring, restore };
}
