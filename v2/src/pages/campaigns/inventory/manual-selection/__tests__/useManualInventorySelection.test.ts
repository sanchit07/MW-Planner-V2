import { renderHook, act } from "@testing-library/react";
import * as React from "react";
import { Provider } from "react-redux";
import { describe, expect, it, vi, beforeEach } from "vitest";

// Mocks for the three mutations/queries the hook uses.
const bulkByIdsTrigger = vi.fn(() => ({
  unwrap: () => Promise.resolve({ success: true }),
}));
const selectAllTrigger = vi.fn(() => ({
  unwrap: () => Promise.resolve({ success: true }),
}));
const selectedInvTrigger = vi.fn();

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
}));

vi.mock("@services/inventory/inventorySlice", async (orig) => {
  const actual = await (orig as () => Promise<Record<string, unknown>>)();
  return {
    ...actual,
    useBulkSelectByIdsMutation: () => [bulkByIdsTrigger],
    useBulkSelectInventoryMutation: () => [selectAllTrigger],
    useLazyGetSelectedInventoryQuery: () => [selectedInvTrigger],
    useGetVenuesQuery: () => ({ data: [] }),
  };
});

import { store as appStore } from "../../../../../store";
import { useManualInventorySelection } from "../useManualInventorySelection";

const wrapper = ({ children }: { children: React.ReactNode }) =>
  React.createElement(Provider, { store: appStore, children });

const page = (content: unknown[], totalPages: number, number: number) => ({
  unwrap: () =>
    Promise.resolve({
      success: true,
      data: { content, totalPages, number, totalElements: content.length },
    }),
});

const item = (id: string, cost: number, type = "Digital") => ({
  detail: { id, inventoryType: type, isSelected: true },
  performance: { estimatedCost: cost, estimatedImpression: 10 },
});

beforeEach(() => {
  bulkByIdsTrigger.mockClear();
  selectAllTrigger.mockClear();
  selectedInvTrigger.mockClear();
});

describe("useManualInventorySelection (client-side)", () => {
  it("loop-fetches every page into selectionMap on loadSelectedInventory", async () => {
    selectedInvTrigger
      .mockReturnValueOnce(page([item("a", 100)], 2, 0))
      .mockReturnValueOnce(page([item("b", 200)], 2, 1));

    const { result } = renderHook(
      () =>
        useManualInventorySelection(
          "c1",
          { id: "c1" } as never,
          {} as never,
          [],
        ),
      { wrapper },
    );
    await act(async () => {
      await result.current.loadSelectedInventory();
    });
    expect(selectedInvTrigger).toHaveBeenCalledTimes(2);
    expect(result.current.selectionMap.size).toBe(2);
    expect(result.current.selectionMap.get("a")?.estimatedCost).toBe(100);
  });

  it("handleItemSelection mutates the map with NO API call", async () => {
    const { result } = renderHook(
      () =>
        useManualInventorySelection(
          "c1",
          { id: "c1" } as never,
          {} as never,
          [],
        ),
      { wrapper },
    );
    // seed a loaded list item so the map can pull its stats
    act(() => {
      result.current.handleInventoryLoaded({
        content: [item("x", 5) as never],
        totalElements: 1,
        last: true,
        append: false,
        sovValuesToMerge: {},
      });
    });
    act(() => result.current.handleItemSelection("x", true));
    expect(result.current.selectionMap.get("x")?.estimatedCost).toBe(5);
    act(() => result.current.handleItemSelection("x", false));
    expect(result.current.selectionMap.has("x")).toBe(false);
    expect(bulkByIdsTrigger).not.toHaveBeenCalled();
    expect(selectAllTrigger).not.toHaveBeenCalled();
  });

  it("saveSelection sends SELECT + DESELECT diffs via bulk-select when no bulkMode", async () => {
    selectedInvTrigger.mockReturnValue(
      page([item("keep", 1), item("drop", 1)], 1, 0),
    );
    const { result } = renderHook(
      () =>
        useManualInventorySelection(
          "c1",
          { id: "c1" } as never,
          {} as never,
          [],
        ),
      { wrapper },
    );
    await act(async () => {
      await result.current.loadSelectedInventory();
    }); // baseline {keep, drop}
    act(() => {
      result.current.handleInventoryLoaded({
        content: [item("add", 1) as never],
        totalElements: 1,
        last: true,
        append: false,
        sovValuesToMerge: {},
      });
    });
    act(() => result.current.handleItemSelection("add", true)); // +add
    act(() => result.current.handleItemSelection("drop", false)); // -drop
    await act(async () => {
      await result.current.saveSelection();
    });
    expect(bulkByIdsTrigger).toHaveBeenCalledWith(
      expect.objectContaining({
        inventoryIds: ["add"],
        operationType: "SELECT",
      }),
    );
    expect(bulkByIdsTrigger).toHaveBeenCalledWith(
      expect.objectContaining({
        inventoryIds: ["drop"],
        operationType: "DESELECT",
      }),
    );
    expect(selectAllTrigger).not.toHaveBeenCalled();
  });

  it("saveSelection calls /select-all when Select-All was used", async () => {
    selectedInvTrigger.mockReturnValue(page([], 1, 0));
    const { result } = renderHook(
      () =>
        useManualInventorySelection(
          "c1",
          { id: "c1" } as never,
          {} as never,
          [],
        ),
      { wrapper },
    );
    await act(async () => {
      await result.current.loadSelectedInventory();
    });
    act(() => result.current.handleSelectAll(true)); // bulkMode = SELECT
    await act(async () => {
      await result.current.saveSelection();
    });
    expect(selectAllTrigger).toHaveBeenCalledWith(
      expect.objectContaining({ campaignId: "c1", operationType: "SELECT" }),
    );
  });

  it("saveSelection sends both /select-all and /bulk-select deltas after Select-All + an individual toggle", async () => {
    selectedInvTrigger.mockReturnValue(page([], 1, 0));
    const { result } = renderHook(
      () =>
        useManualInventorySelection(
          "c1",
          { id: "c1" } as never,
          {} as never,
          [],
        ),
      { wrapper },
    );
    await act(async () => {
      await result.current.loadSelectedInventory();
    }); // baseline {}
    act(() => {
      result.current.handleInventoryLoaded({
        content: [item("a", 1) as never, item("b", 1) as never],
        totalElements: 2,
        last: true,
        append: false,
        sovValuesToMerge: {},
      });
    });
    act(() => result.current.handleSelectAll(true)); // bulkMode = SELECT, map = {a, b}
    act(() => result.current.handleItemSelection("b", false)); // map = {a}; bulkBaseline = {a, b} -> removed = ["b"]
    await act(async () => {
      await result.current.saveSelection();
    });
    expect(selectAllTrigger).toHaveBeenCalledWith(
      expect.objectContaining({ campaignId: "c1", operationType: "SELECT" }),
    );
    expect(bulkByIdsTrigger).toHaveBeenCalledWith(
      expect.objectContaining({
        inventoryIds: ["b"],
        operationType: "DESELECT",
      }),
    );
    expect(bulkByIdsTrigger).toHaveBeenCalledTimes(1);
  });

  it("saveSelection does NOT deselect items paginated in after Select-All (regression)", async () => {
    selectedInvTrigger.mockReturnValue(page([], 1, 0));
    const { result } = renderHook(
      () =>
        useManualInventorySelection(
          "c1",
          { id: "c1" } as never,
          {} as never,
          [],
        ),
      { wrapper },
    );
    await act(async () => {
      await result.current.loadSelectedInventory();
    }); // baseline {}
    act(() => {
      result.current.handleInventoryLoaded({
        content: [item("a", 1) as never],
        totalElements: 2,
        last: false,
        append: false,
        sovValuesToMerge: {},
      });
    });
    act(() => result.current.handleSelectAll(true)); // bulkMode = SELECT, map = {a}
    // Pagination continues AFTER Select-All was triggered; newly-loaded
    // items must be backfilled into selectionMap, not left out of it.
    act(() => {
      result.current.handleInventoryLoaded({
        content: [item("b", 1) as never],
        totalElements: 2,
        last: true,
        append: true,
        sovValuesToMerge: {},
      });
    });
    expect(result.current.selectionMap.has("b")).toBe(true);
    await act(async () => {
      await result.current.saveSelection();
    });
    expect(selectAllTrigger).toHaveBeenCalledWith(
      expect.objectContaining({ campaignId: "c1", operationType: "SELECT" }),
    );
    expect(bulkByIdsTrigger).not.toHaveBeenCalled();
  });
});
