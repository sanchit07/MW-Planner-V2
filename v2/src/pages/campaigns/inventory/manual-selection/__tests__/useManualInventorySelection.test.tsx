import { configureStore } from "@reduxjs/toolkit";
import { act, renderHook, waitFor } from "@testing-library/react";
import { type ReactNode } from "react";
import { Provider } from "react-redux";
import { beforeEach, describe, expect, it, vi } from "vitest";

import campaignReducer from "../../../../../services/campaign/campaignSlice";
import type { InventoryFilters } from "../../../../../types/inventory.types";
import { useManualInventorySelection } from "../useManualInventorySelection";

const { showSuccess, showError } = vi.hoisted(() => ({
  showSuccess: vi.fn(),
  showError: vi.fn(),
}));

vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({ showSuccess, showError }),
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
}));

const bulkSelectInventory = vi.hoisted(() => vi.fn());
const bulkSelectInventoryByReferenceIds = vi.hoisted(() => vi.fn());
const fetchCampaignForecast = vi.hoisted(() =>
  vi.fn().mockReturnValue({
    unwrap: () =>
      Promise.resolve({
        success: true,
        data: { totalInventories: 10 },
      }),
  }),
);

vi.mock("@services/inventory/inventorySlice", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@services/inventory/inventorySlice")>();
  return {
    ...actual,
    useSelectInventoryMutation: () => [vi.fn(), {}],
    useBulkSelectInventoryMutation: () => [bulkSelectInventory, {}],
    useBulkSelectInventoryByReferenceIdsMutation: () => [
      bulkSelectInventoryByReferenceIds,
      {},
    ],
    useLazyGetCampaignForecastQuery: () => [fetchCampaignForecast, {}],
    useGetVenuesQuery: () => ({ data: [], isLoading: false }),
  };
});

function wrapper({ children }: { children: ReactNode }) {
  const store = configureStore({ reducer: { campaign: campaignReducer } });
  return <Provider store={store}>{children}</Provider>;
}

const emptyFilters: InventoryFilters = {
  mediaOwners: [],
  venueTypes: [],
  bookingMode: [],
  sizes: [],
  latitude: "",
  longitude: "",
  searchbyquery: "",
  environments: [],
  inventoryClassification: [],
  programmaticSupport: "ALL",
  dealTypes: [],
};

function renderSelection(campaignId = "campaign-1") {
  return renderHook(
    () => useManualInventorySelection(campaignId, null, emptyFilters),
    { wrapper },
  );
}

const loadedItem = (id: string, isSelected = false) => ({
  detail: { id, inventoryType: "Digital", isSelected },
});

// handleSelectAll is a client-only, synchronous optimistic update — it no
// longer calls bulkSelectInventory directly. The actual API call (and its
// error handling) happens later, inside saveSelection.
describe("useManualInventorySelection — handleSelectAll (local, client-side)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fetchCampaignForecast.mockReturnValue({
      unwrap: () =>
        Promise.resolve({ success: true, data: { totalInventories: 10 } }),
    });
  });

  it("marks loaded items selected and sets bulkMode to SELECT, without calling the API", () => {
    const { result } = renderSelection();

    act(() => {
      result.current.handleInventoryLoaded({
        content: [loadedItem("a"), loadedItem("b")] as never,
        totalElements: 2,
        last: true,
        append: false,
        sovValuesToMerge: {},
      });
    });

    act(() => {
      result.current.handleSelectAll(true);
    });

    expect(result.current.getSelectAllState()).toEqual({
      checked: true,
      indeterminate: false,
    });
    expect(result.current.selectionMap.size).toBe(2);
    expect(
      result.current.inventoryItems.every((item) => item.detail.isSelected),
    ).toBe(true);
    expect(bulkSelectInventory).not.toHaveBeenCalled();
  });

  it("clears the selection and sets bulkMode to DESELECT, without calling the API", () => {
    const { result } = renderSelection();

    act(() => {
      result.current.handleInventoryLoaded({
        content: [loadedItem("a", true), loadedItem("b", true)] as never,
        totalElements: 2,
        last: true,
        append: false,
        sovValuesToMerge: {},
      });
    });

    act(() => {
      result.current.handleSelectAll(false);
    });

    expect(result.current.getSelectAllState()).toEqual({
      checked: false,
      indeterminate: false,
    });
    expect(result.current.selectionMap.size).toBe(0);
    expect(
      result.current.inventoryItems.every((item) => !item.detail.isSelected),
    ).toBe(true);
    expect(bulkSelectInventory).not.toHaveBeenCalled();
  });
});

describe("useManualInventorySelection — saveSelection error handling", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fetchCampaignForecast.mockReturnValue({
      unwrap: () =>
        Promise.resolve({ success: true, data: { totalInventories: 10 } }),
    });
  });

  it("shows an error and resolves false when the bulk select-all call fails during saveSelection", async () => {
    bulkSelectInventory.mockReturnValue({
      unwrap: () => Promise.reject(new Error("Network error")),
    });
    const { result } = renderSelection();

    act(() => {
      result.current.handleInventoryLoaded({
        content: [loadedItem("a")] as never,
        totalElements: 1,
        last: true,
        append: false,
        sovValuesToMerge: {},
      });
    });
    act(() => result.current.handleSelectAll(true));

    let saved: boolean | undefined;
    await act(async () => {
      saved = await result.current.saveSelection();
    });

    expect(saved).toBe(false);
    expect(showError).toHaveBeenCalledWith("inventories.manual.saveFailed");
  });
});

describe("useManualInventorySelection — handlePasteReferenceIds", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fetchCampaignForecast.mockReturnValue({
      unwrap: () =>
        Promise.resolve({ success: true, data: { totalInventories: 10 } }),
    });
    bulkSelectInventoryByReferenceIds.mockReturnValue({
      unwrap: () =>
        Promise.resolve({ success: true, data: "2 inventories selected" }),
    });
    bulkSelectInventory.mockReturnValue({
      unwrap: () =>
        Promise.resolve({ success: true, data: "All inventories deselected" }),
    });
  });

  it("in add mode, only bulk-selects by reference ID without deselecting first", async () => {
    const { result } = renderSelection();

    await act(async () => {
      await result.current.handlePasteReferenceIds(["REF-1", "REF-2"], "add");
    });

    expect(bulkSelectInventory).not.toHaveBeenCalled();
    expect(bulkSelectInventoryByReferenceIds).toHaveBeenCalledWith({
      campaignId: "campaign-1",
      referenceIds: ["REF-1", "REF-2"],
      operationType: "SELECT",
    });
  });

  it("in replace mode, deselects everything matching current filters before bulk-selecting the pasted IDs", async () => {
    const { result } = renderSelection();

    await act(async () => {
      await result.current.handlePasteReferenceIds(
        ["REF-1", "REF-2"],
        "replace",
      );
    });

    expect(bulkSelectInventory).toHaveBeenCalledWith({
      campaignId: "campaign-1",
      operationType: "DESELECT",
      filters: {},
    });
    expect(bulkSelectInventoryByReferenceIds).toHaveBeenCalledWith({
      campaignId: "campaign-1",
      referenceIds: ["REF-1", "REF-2"],
      operationType: "SELECT",
    });
    // Deselect must resolve before the reference-ID select runs.
    const deselectOrder = bulkSelectInventory.mock.invocationCallOrder[0];
    const selectOrder =
      bulkSelectInventoryByReferenceIds.mock.invocationCallOrder[0];
    expect(deselectOrder).toBeLessThan(selectOrder);
  });

  it("returns the bulk-select result", async () => {
    const { result } = renderSelection();

    let response;
    await act(async () => {
      response = await result.current.handlePasteReferenceIds(["REF-1"], "add");
    });

    expect(response).toEqual({
      success: true,
      data: "2 inventories selected",
    });
  });

  it("no-ops when campaignId is empty", async () => {
    const { result } = renderSelection("");

    await act(async () => {
      await result.current.handlePasteReferenceIds(["REF-1"], "add");
    });

    expect(bulkSelectInventoryByReferenceIds).not.toHaveBeenCalled();
  });

  it("no-ops when referenceIds is empty", async () => {
    const { result } = renderSelection();

    await act(async () => {
      await result.current.handlePasteReferenceIds([], "add");
    });

    expect(bulkSelectInventoryByReferenceIds).not.toHaveBeenCalled();
  });

  it("clears isBulkSyncing once the calls settle", async () => {
    const { result } = renderSelection();

    await act(async () => {
      await result.current.handlePasteReferenceIds(["REF-1"], "replace");
    });

    await waitFor(() => expect(result.current.isBulkSyncing).toBe(false));
  });
});
