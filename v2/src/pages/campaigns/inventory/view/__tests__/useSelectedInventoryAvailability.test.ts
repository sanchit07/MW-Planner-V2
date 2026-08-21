import { act, renderHook, waitFor } from "@testing-library/react";
import type { InventoryItem } from "src/types/inventory.types";
import { beforeEach, describe, expect, it, vi } from "vitest";

// --- Mocks -----------------------------------------------------------------

const triggerMock = vi.fn();

vi.mock("@services/inventory/inventorySlice", () => ({
  useLazyGetInventoryAvailabilityQuery: () => [triggerMock],
}));

// parseAvailabilityResponse: echo back the `inventories` map.
vi.mock("@utils/inventoryAvailabilityUI.utils", () => ({
  parseAvailabilityResponse: (resp: { inventories: Record<string, unknown> }) =>
    resp?.inventories ?? {},
}));

// buildAvailabilityIndex: pass the data through as the "index".
// getAvailabilityPercentFromIndex: read the pre-baked percent off the data.
vi.mock("@utils/inventoryavailability.utils", () => ({
  buildAvailabilityIndex: (data: { percent: number | null }) => data,
  getAvailabilityPercentFromIndex: (index: { percent: number | null }) =>
    index.percent,
}));

import { useSelectedInventoryAvailability } from "../useSelectedInventoryAvailability";

// --- Helpers ---------------------------------------------------------------

const makeItem = (id: string): InventoryItem =>
  ({
    detail: { id, externalId: `ext-${id}`, name: id },
  }) as unknown as InventoryItem;

/** Build a resolved availability response for the given externalId→percent. */
const resolveWith = (byExternalId: Record<string, number | null>) => ({
  unwrap: () =>
    Promise.resolve({
      inventories: Object.fromEntries(
        Object.entries(byExternalId).map(([extId, percent]) => [
          extId,
          { percent },
        ]),
      ),
    }),
});

const START = "2026-07-01T00:00:00";
const END = "2026-07-31T23:59:59";

beforeEach(() => {
  triggerMock.mockReset();
});

// --- Tests -----------------------------------------------------------------

describe("useSelectedInventoryAvailability", () => {
  it("fetches a single inventory on request and resolves its percentage", async () => {
    triggerMock.mockReturnValue(resolveWith({ "ext-a": 83 }));

    const { result } = renderHook(() =>
      useSelectedInventoryAvailability({ startDate: START, endDate: END }),
    );

    act(() => {
      result.current.requestAvailability(makeItem("a"));
    });

    await waitFor(() =>
      expect(result.current.availabilityById["a"]?.loading).toBe(false),
    );

    expect(triggerMock).toHaveBeenCalledTimes(1);
    expect(triggerMock.mock.calls[0][0].data.inventoryIds).toEqual(["ext-a"]);
    expect(result.current.availabilityById["a"]).toEqual({
      loading: false,
      percent: 83,
    });
  });

  it("does not refetch an inventory that was already requested", async () => {
    triggerMock.mockReturnValue(resolveWith({ "ext-a": 50 }));

    const { result } = renderHook(() =>
      useSelectedInventoryAvailability({ startDate: START, endDate: END }),
    );

    act(() => {
      result.current.requestAvailability(makeItem("a"));
    });
    await waitFor(() =>
      expect(result.current.availabilityById["a"]?.percent).toBe(50),
    );

    // Re-expand the same card — must not trigger another call.
    act(() => {
      result.current.requestAvailability(makeItem("a"));
    });

    expect(triggerMock).toHaveBeenCalledTimes(1);
  });

  it("fetches each distinct inventory once", async () => {
    triggerMock
      .mockReturnValueOnce(resolveWith({ "ext-a": 10 }))
      .mockReturnValueOnce(resolveWith({ "ext-b": 20 }));

    const { result } = renderHook(() =>
      useSelectedInventoryAvailability({ startDate: START, endDate: END }),
    );

    act(() => {
      result.current.requestAvailability(makeItem("a"));
    });
    await waitFor(() =>
      expect(result.current.availabilityById["a"]?.percent).toBe(10),
    );

    act(() => {
      result.current.requestAvailability(makeItem("b"));
    });
    await waitFor(() =>
      expect(result.current.availabilityById["b"]?.percent).toBe(20),
    );

    expect(triggerMock).toHaveBeenCalledTimes(2);
    expect(triggerMock.mock.calls[1][0].data.inventoryIds).toEqual(["ext-b"]);
    expect(result.current.availabilityById["a"]?.percent).toBe(10);
  });

  it("does not fetch when campaign dates are missing", () => {
    triggerMock.mockReturnValue(resolveWith({ "ext-a": 83 }));

    const { result } = renderHook(() =>
      useSelectedInventoryAvailability({
        startDate: undefined,
        endDate: undefined,
      }),
    );

    act(() => {
      result.current.requestAvailability(makeItem("a"));
    });

    expect(triggerMock).not.toHaveBeenCalled();
  });

  it("resolves the entry to null percent when the fetch fails", async () => {
    triggerMock.mockReturnValue({
      unwrap: () => Promise.reject(new Error("network")),
    });

    const { result } = renderHook(() =>
      useSelectedInventoryAvailability({ startDate: START, endDate: END }),
    );

    act(() => {
      result.current.requestAvailability(makeItem("a"));
    });

    await waitFor(() =>
      expect(result.current.availabilityById["a"]?.loading).toBe(false),
    );
    expect(result.current.availabilityById["a"]?.percent).toBeNull();
  });
});
