import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

import { useReachCurve } from "../useReachCurve";

// /selected-inventory/all — flat list of selected inventories with per-item
// performance (estimatedReach + estimatedCost).
const selectedTrigger = vi.hoisted(() =>
  vi.fn().mockReturnValue({
    unwrap: () =>
      Promise.resolve({
        success: true,
        data: [
          {
            inventoryId: "a",
            referenceId: "REF-1",
            performance: { estimatedReach: 20000, estimatedCost: 5000 },
          },
          {
            inventoryId: "b",
            referenceId: "REF-2",
            performance: { estimatedReach: 30000, estimatedCost: 10000 },
          },
        ],
      }),
  }),
);

const curveTrigger = vi.hoisted(() =>
  vi.fn().mockReturnValue({
    unwrap: () =>
      Promise.resolve({
        success: true,
        data: [
          {
            inventories: [],
            overallInventories: {
              overallReach: [0, 50, 100],
              overallsaturatedReachDate: "2025-01-02",
            },
          },
        ],
      }),
  }),
);

vi.mock("@services/inventory/inventorySlice", () => ({
  useLazyGetAllSelectedInventoryQuery: () => [selectedTrigger, {}],
  useLazyGetReachSaturationCurveQuery: () => [curveTrigger, {}],
}));

vi.mock("@hooks/useMediaOwnerIds", () => ({
  useMediaOwnerIds: () => [],
}));

describe("useReachCurve", () => {
  it("stays idle when disabled", () => {
    const { result } = renderHook(() =>
      useReachCurve("camp-1", false, "2025-01-01", "2025-01-03"),
    );
    expect(result.current.status).toBe("idle");
  });

  it("stays idle when there is no campaignId", () => {
    const { result } = renderHook(() =>
      useReachCurve("", true, "2025-01-01", "2025-01-03"),
    );
    expect(result.current.status).toBe("idle");
  });

  it("loads the selected inventory then the curve and exposes overall reach", async () => {
    const { result } = renderHook(() =>
      useReachCurve("camp-1", true, "2025-01-01", "2025-01-03"),
    );

    await waitFor(() => expect(result.current.status).toBe("ready"));
    expect(result.current.overallReach).toEqual([0, 50, 100]);
    expect(result.current.labels).toHaveLength(3);
    expect(result.current.inventoryCount).toBe(2);
    expect(selectedTrigger).toHaveBeenCalledWith({ campaignId: "camp-1" });

    // cpmBudget = performance.estimatedCost, reach = performance.estimatedReach
    expect(curveTrigger).toHaveBeenCalledWith(
      expect.objectContaining({
        inventories: [
          { referenceId: "REF-1", reach: 20000, cpmBudget: 5000 },
          { referenceId: "REF-2", reach: 30000, cpmBudget: 10000 },
        ],
        duration: 3,
        startDate: "2025-01-01",
        endDate: "2025-01-03",
      }),
    );
  });

  it.each([
    ["all-NaN (null)", [null, null, null]],
    ["all-zero", [0, 0, 0]],
  ])(
    "exposes empty overall reach when the curve is %s — no-data",
    async (_label, overallReach) => {
      curveTrigger.mockReturnValueOnce({
        unwrap: () =>
          Promise.resolve({
            success: true,
            data: [
              {
                inventories: [],
                overallInventories: {
                  overallReach,
                  overallsaturatedReachDate: "2025-01-02",
                },
              },
            ],
          }),
      });
      const { result } = renderHook(() =>
        useReachCurve("camp-1", true, "2025-01-01", "2025-01-03"),
      );

      await waitFor(() => expect(result.current.status).toBe("ready"));
      // No positive value → treated as no data so the empty-state shows.
      expect(result.current.overallReach).toEqual([]);
      expect(result.current.labels).toEqual([]);
    },
  );

  it("stays idle when no inventory is selected", async () => {
    selectedTrigger.mockReturnValueOnce({
      unwrap: () => Promise.resolve({ success: true, data: [] }),
    });
    const { result } = renderHook(() =>
      useReachCurve("camp-1", true, "2025-01-01", "2025-01-03"),
    );

    await waitFor(() => expect(selectedTrigger).toHaveBeenCalled());
    await waitFor(() => expect(result.current.status).toBe("idle"));
    expect(result.current.inventoryCount).toBe(0);
  });
});
