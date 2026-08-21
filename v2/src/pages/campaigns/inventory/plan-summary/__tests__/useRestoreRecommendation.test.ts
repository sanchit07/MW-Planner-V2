import { renderHook, act, waitFor } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

import type { CampaignCreateResponse } from "../../../../../types/campaign.types";
import { useRestoreRecommendation } from "../useRestoreRecommendation";

const bulkTrigger = vi.hoisted(() =>
  vi.fn().mockReturnValue({
    unwrap: () => Promise.resolve({ success: true }),
  }),
);

vi.mock("@services/inventory/inventorySlice", () => ({
  useBulkSelectInventoryMutation: () => [bulkTrigger, {}],
}));

const campaignData = {
  countryId: "Japan",
} as unknown as CampaignCreateResponse;

describe("useRestoreRecommendation", () => {
  it("deselects all then regenerates", async () => {
    bulkTrigger.mockClear();
    const regenerate = vi.fn();
    const { result } = renderHook(() =>
      useRestoreRecommendation("camp-1", campaignData, regenerate),
    );

    await act(async () => {
      await result.current.restore();
    });

    expect(bulkTrigger).toHaveBeenCalledWith(
      expect.objectContaining({
        campaignId: "camp-1",
        operationType: "DESELECT",
        filters: expect.objectContaining({ countries: ["Japan"] }),
      }),
    );
    expect(regenerate).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(result.current.isRestoring).toBe(false));
  });

  it("no-ops without a campaignId", async () => {
    bulkTrigger.mockClear();
    const regenerate = vi.fn();
    const { result } = renderHook(() =>
      useRestoreRecommendation("", campaignData, regenerate),
    );

    await act(async () => {
      await result.current.restore();
    });

    expect(bulkTrigger).not.toHaveBeenCalled();
    expect(regenerate).not.toHaveBeenCalled();
  });
});
