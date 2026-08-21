import { configureStore } from "@reduxjs/toolkit";
import { act, renderHook, waitFor } from "@testing-library/react";
import { type ReactNode } from "react";
import { Provider } from "react-redux";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import campaignReducer, {
  setRecommendationRun,
} from "../../../../../services/campaign/campaignSlice";
import { useRecommendationForecast } from "../useRecommendationForecast";

const generateTrigger = vi.hoisted(() => vi.fn());
const forecastTrigger = vi.hoisted(() =>
  vi.fn(() => ({
    unwrap: () =>
      Promise.resolve({ success: true, data: { totalInventories: 1 } }),
  })),
);

vi.mock("@services/inventory/inventorySlice", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@services/inventory/inventorySlice")>();
  return {
    ...actual,
    useLazyGenerateInventoryRecommendationQuery: () => [generateTrigger, {}],
    useLazyGetCampaignForecastQuery: () => [forecastTrigger, {}],
  };
});

function wrapper({ children }: { children: ReactNode }) {
  const store = configureStore({ reducer: { campaign: campaignReducer } });
  return <Provider store={store}>{children}</Provider>;
}

/** Wrapper whose store already holds a completed run for a given channel set. */
function makeCachedWrapper(mediaChannels: string[]) {
  return function CachedWrapper({ children }: { children: ReactNode }) {
    const store = configureStore({ reducer: { campaign: campaignReducer } });
    store.dispatch(
      setRecommendationRun({
        runId: "cached-run",
        signature: "cached-sig",
        mediaChannels,
      }),
    );
    return <Provider store={store}>{children}</Provider>;
  };
}

const completed = {
  isError: false,
  data: {
    data: { status: "COMPLETED", completionPercentage: 100, runId: "run-1" },
  },
};
const inProgress = {
  isError: false,
  data: { data: { status: "IN_PROGRESS", completionPercentage: 40 } },
};

describe("useRecommendationForecast — forceRegenerate", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    generateTrigger.mockResolvedValue(completed);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("sends forceRegenerate=true on the FIRST restore /generate call only", async () => {
    vi.useFakeTimers();
    // mount → COMPLETED, then restore: poll1 IN_PROGRESS, poll2 COMPLETED.
    generateTrigger
      .mockResolvedValueOnce(completed) // initial mount run
      .mockResolvedValueOnce(inProgress) // restore poll #1
      .mockResolvedValueOnce(completed); // restore poll #2

    const { result } = renderHook(
      () => useRecommendationForecast("campaign-1", "sig-1"),
      { wrapper },
    );

    // Flush the mount run.
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    // Mount run must not force.
    expect(generateTrigger).toHaveBeenNthCalledWith(1, {
      campaignId: "campaign-1",
      forceRegenerate: false,
    });

    // Trigger the restore-driven regeneration.
    await act(async () => {
      result.current.regenerateFromRestore();
      await Promise.resolve();
    });

    // Advance past the 5s poll delay so poll #2 fires.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
    });

    // Restore poll #1 forces; poll #2 does not.
    expect(generateTrigger).toHaveBeenNthCalledWith(2, {
      campaignId: "campaign-1",
      forceRegenerate: true,
    });
    expect(generateTrigger).toHaveBeenNthCalledWith(3, {
      campaignId: "campaign-1",
      forceRegenerate: false,
    });
  });

  it("failed-state retry never forces a regenerate", async () => {
    const { result } = renderHook(
      () => useRecommendationForecast("campaign-1", "sig-1"),
      { wrapper },
    );

    await waitFor(() => expect(result.current.status).toBe("completed"));

    await act(async () => {
      result.current.retry();
      await Promise.resolve();
    });

    expect(generateTrigger).not.toHaveBeenCalledWith(
      expect.objectContaining({ forceRegenerate: true }),
    );
  });

  it("sends mediaOwnerIds to /generate when provided", async () => {
    renderHook(
      () => useRecommendationForecast("campaign-1", "sig-1", ["mo-1", "mo-2"]),
      { wrapper },
    );

    await waitFor(() =>
      expect(generateTrigger).toHaveBeenCalledWith(
        expect.objectContaining({
          campaignId: "campaign-1",
          mediaOwnerIds: ["mo-1", "mo-2"],
        }),
      ),
    );
    // /forecast (POST) also carries the media owner ids.
    await waitFor(() =>
      expect(forecastTrigger).toHaveBeenCalledWith(
        expect.objectContaining({
          campaignId: "campaign-1",
          mediaOwnerIds: ["mo-1", "mo-2"],
        }),
      ),
    );
  });

  it("forces regenerate when the media channels changed since the cached run", async () => {
    renderHook(
      () =>
        useRecommendationForecast("campaign-1", "new-sig", [], ["DIGITAL_OOH"]),
      { wrapper: makeCachedWrapper(["CLASSIC_OOH"]) },
    );

    await waitFor(() =>
      expect(generateTrigger).toHaveBeenCalledWith(
        expect.objectContaining({
          campaignId: "campaign-1",
          forceRegenerate: true,
        }),
      ),
    );
  });

  it("does not force regenerate when the media channels are unchanged", async () => {
    renderHook(
      () =>
        useRecommendationForecast("campaign-1", "new-sig", [], ["DIGITAL_OOH"]),
      { wrapper: makeCachedWrapper(["DIGITAL_OOH"]) },
    );

    await waitFor(() => expect(generateTrigger).toHaveBeenCalled());
    expect(generateTrigger).not.toHaveBeenCalledWith(
      expect.objectContaining({ forceRegenerate: true }),
    );
  });

  it("omits mediaOwnerIds when none are provided", async () => {
    renderHook(() => useRecommendationForecast("campaign-1", "sig-1"), {
      wrapper,
    });

    await waitFor(() => expect(generateTrigger).toHaveBeenCalled());
    expect(generateTrigger).not.toHaveBeenCalledWith(
      expect.objectContaining({ mediaOwnerIds: expect.anything() }),
    );
  });
});
