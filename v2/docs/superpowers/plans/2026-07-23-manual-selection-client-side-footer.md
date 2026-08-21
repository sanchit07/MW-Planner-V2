# Manual Selection — Client-Side Selection + Stats Footer — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Step-4 "Edit Manually" inventory popup select/deselect fully client-side, batch-persist on Save Selection, and add a stats footer with a Budget-by-channel breakdown popup.

**Architecture:** `ManualSelectionPage` owns a client `selectionMap` seeded on open by loop-fetching every page of `/selected-inventory`. Toggles mutate the map with no API. Save diffs the map against the baseline and calls the new `/bulk-select` (ids) endpoint, or `/select-all` (filters) when a Select-All/Deselect-All was used. A pure helper module computes footer + per-channel stats from the map.

**Tech Stack:** React + TypeScript, Redux Toolkit / RTK Query, Tolgee i18n, Vitest + @testing-library/react, Tailwind v4.

## Global Constraints

- Node v20.19.3 for yarn/vitest/build (shell default v14 breaks vitest).
- `yarn lint` must pass with zero warnings; `import/order` enforced (builtin → external → internal → parent/sibling, alphabetized, newlines between groups).
- All user-visible strings via `useTranslate("campaigns")`; add keys to BOTH `src/assets/i18n/campaigns/en.json` and `ja.json`.
- Scope is `ManualSelectionPage` + `useManualInventorySelection` only. Do NOT change the recommendation smart-suggestion flow or other `useSelectInventoryMutation` callers.
- Currency formatting via `formatCurrencyWithLocale(value, currency, decimals)` from `@utils/currency`; compact numbers via `formatNumber` from `@utils/budget.utils`.
- Classification of an `InventoryItem`: `detail.inventoryType.toLowerCase().startsWith("digital")` ⇒ Digital, else Classic (matches existing filter logic in `inventory.utils.ts:426-434`).
- `/bulk-select` SELECT/DESELECT are additive against server state (no full-replace).
- Run a single test: `yarn test <path>` (Node 20).

---

## File Structure

- **Create** `src/pages/campaigns/inventory/manual-selection/selection-stats.utils.ts` — pure stat/grouping helpers + types.
- **Create** `src/pages/campaigns/inventory/manual-selection/SelectionFooter.tsx` — presentational footer.
- **Create** `src/pages/campaigns/inventory/manual-selection/BudgetByChannelPopup.tsx` — channel breakdown table.
- **Create** `src/pages/campaigns/inventory/manual-selection/__tests__/selection-stats.utils.test.ts`
- **Create** `src/pages/campaigns/inventory/manual-selection/__tests__/SelectionFooter.test.tsx`
- **Create** `src/pages/campaigns/inventory/manual-selection/__tests__/BudgetByChannelPopup.test.tsx`
- **Modify** `src/services/inventory/inventorySlice.ts` — add `bulkSelectByIds` mutation + hook export.
- **Modify** `src/pages/campaigns/inventory/manual-selection/useManualInventorySelection.ts` — client-side rewrite.
- **Modify** `src/pages/campaigns/inventory/manual-selection/ManualSelectionPage.tsx` — header X, footer, save/cancel wiring.
- **Modify** `src/pages/campaigns/inventory/InventoryListPanel.tsx` — selected-count badge from new `selectedCount` prop.
- **Modify** `src/assets/i18n/campaigns/en.json` + `ja.json` — new keys.
- **Modify** `src/pages/campaigns/inventory/manual-selection/__tests__/ManualSelectionPage.test.tsx` — align mocks with new flow.

---

## Task 1: `bulkSelectByIds` RTK Query mutation

**Files:**

- Modify: `src/services/inventory/inventorySlice.ts` (add endpoint near existing `bulkSelectInventory` ~line 99; add hook to the exports block ~line 896)

**Interfaces:**

- Produces: `useBulkSelectByIdsMutation()` returning a trigger `({ campaignId: string; inventoryIds: string[]; operationType: "SELECT" | "DESELECT" }) => ...` resolving to `SuccessResponse<InventorySelectionResponse>`.

- [ ] **Step 1: Add the mutation** inside the `endpoints: (builder) => ({ ... })` object, directly after the existing `bulkSelectInventory` mutation:

```ts
// Persist an explicit set of inventory ids in one call (client-side manual
// selection). Distinct from bulkSelectInventory which posts /select-all with
// filters. operationType applies to the whole id set (additive server-side).
bulkSelectByIds: builder.mutation<
  SuccessResponse<InventorySelectionResponse>,
  { campaignId: string; inventoryIds: string[]; operationType: "SELECT" | "DESELECT" }
>({
  query: ({ campaignId, inventoryIds, operationType }) => ({
    url: `/campaign-inventory/${campaignId}/bulk-select`,
    method: "POST",
    data: { campaignId, inventoryIds, operationType },
    timeout: 180000,
  }),
}),
```

- [ ] **Step 2: Export the hook.** In the `export const { ... } = inventoryApi;` block, add `useBulkSelectByIdsMutation` alphabetically near `useBulkSelectInventoryMutation`.

- [ ] **Step 3: Typecheck**

Run: `yarn build`
Expected: passes (no TS errors). If build is slow, `npx tsc -p tsconfig.app.json --noEmit`.

- [ ] **Step 4: Commit**

```bash
git add src/services/inventory/inventorySlice.ts
git commit -m "feat(inventory): add bulkSelectByIds mutation for /bulk-select"
```

---

## Task 2: Selection-stats pure helpers

**Files:**

- Create: `src/pages/campaigns/inventory/manual-selection/selection-stats.utils.ts`
- Test: `src/pages/campaigns/inventory/manual-selection/__tests__/selection-stats.utils.test.ts`

**Interfaces:**

- Produces:
  - `type SelectedStat = { id: string; estimatedCost: number; estimatedImpressions: number; inventoryType: string }`
  - `type ChannelKey = "digital" | "classic"`
  - `channelOfType(inventoryType: string): ChannelKey`
  - `statFromItem(item: InventoryItem): SelectedStat`
  - `type FooterTotals = { count: number; impressions: number; cost: number; overBudget: boolean; overBy: number }`
  - `computeFooterTotals(map: Map<string, SelectedStat>, budget: number): FooterTotals`
  - `type ChannelRow = { channel: MediaChannel; key: ChannelKey; planned: number; selected: number; difference: number; inventories: number }`
  - `computeChannelRows(map, mediaChannels: string[], budgetAllocation: BudgetAllocation | undefined, budget: number): ChannelRow[]` (excludes a Total row — the component sums it)

- [ ] **Step 1: Write the failing test**

```ts
import { describe, expect, it } from "vitest";

import { MediaChannel } from "@constants/inventory.constants";
import type { InventoryItem } from "src/types/inventory.types";

import {
  channelOfType,
  computeChannelRows,
  computeFooterTotals,
  statFromItem,
} from "../selection-stats.utils";

const stat = (id: string, cost: number, imp: number, type: string) => ({
  id,
  estimatedCost: cost,
  estimatedImpressions: imp,
  inventoryType: type,
});

describe("channelOfType", () => {
  it("maps digital-prefixed types to digital", () => {
    expect(channelOfType("Digital")).toBe("digital");
    expect(channelOfType("Digital Network")).toBe("digital");
    expect(channelOfType("DIGITAL")).toBe("digital");
  });
  it("maps everything else to classic", () => {
    expect(channelOfType("Transit")).toBe("classic");
    expect(channelOfType("OOH")).toBe("classic");
    expect(channelOfType("")).toBe("classic");
  });
});

describe("statFromItem", () => {
  it("reads id/cost/impression/type off an InventoryItem", () => {
    const item = {
      detail: { id: "a", inventoryType: "Digital" },
      performance: { estimatedCost: 100, estimatedImpression: 2000 },
    } as unknown as InventoryItem;
    expect(statFromItem(item)).toEqual(stat("a", 100, 2000, "Digital"));
  });
  it("falls back to plural estimatedImpressions and 0s", () => {
    const item = {
      detail: { id: "b", inventoryType: "OOH" },
      performance: { estimatedImpressions: 50 },
    } as unknown as InventoryItem;
    expect(statFromItem(item)).toEqual(stat("b", 0, 50, "OOH"));
  });
});

describe("computeFooterTotals", () => {
  const map = new Map([
    ["a", stat("a", 100, 2000, "Digital")],
    ["b", stat("b", 50, 500, "OOH")],
  ]);
  it("sums count/impressions/cost", () => {
    const t = computeFooterTotals(map, 1000);
    expect(t.count).toBe(2);
    expect(t.impressions).toBe(2500);
    expect(t.cost).toBe(150);
    expect(t.overBudget).toBe(false);
    expect(t.overBy).toBe(0);
  });
  it("flags over-budget and reports overBy", () => {
    const t = computeFooterTotals(map, 100);
    expect(t.overBudget).toBe(true);
    expect(t.overBy).toBe(50);
  });
});

describe("computeChannelRows", () => {
  const map = new Map([
    ["a", stat("a", 300, 0, "Digital")],
    ["b", stat("b", 200, 0, "Transit")],
  ]);
  it("builds one row per selected media channel with planned/selected/difference", () => {
    const rows = computeChannelRows(
      map,
      [MediaChannel.DIGITAL_OOH, MediaChannel.CLASSIC_OOH],
      { digital: 40, classic: 60, transit: 0, retail: 0 },
      1000,
    );
    const digital = rows.find((r) => r.key === "digital")!;
    const classic = rows.find((r) => r.key === "classic")!;
    expect(digital.planned).toBe(400);
    expect(digital.selected).toBe(300);
    expect(digital.difference).toBe(-100);
    expect(digital.inventories).toBe(1);
    expect(classic.planned).toBe(600);
    expect(classic.selected).toBe(200); // Transit counts as classic
    expect(classic.difference).toBe(-400);
    expect(classic.inventories).toBe(1);
  });
  it("returns rows only for the campaign's media channels", () => {
    const rows = computeChannelRows(
      map,
      [MediaChannel.DIGITAL_OOH],
      undefined,
      0,
    );
    expect(rows).toHaveLength(1);
    expect(rows[0].key).toBe("digital");
    expect(rows[0].planned).toBe(0);
  });
});
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `yarn test src/pages/campaigns/inventory/manual-selection/__tests__/selection-stats.utils.test.ts`
Expected: FAIL — cannot resolve `../selection-stats.utils`.

- [ ] **Step 3: Implement the helpers**

```ts
import { MediaChannel } from "@constants/inventory.constants";
import type { BudgetAllocation } from "src/types/campaign.types";
import type { InventoryItem } from "src/types/inventory.types";

export type ChannelKey = "digital" | "classic";

export interface SelectedStat {
  id: string;
  estimatedCost: number;
  estimatedImpressions: number;
  inventoryType: string;
}

export interface FooterTotals {
  count: number;
  impressions: number;
  cost: number;
  overBudget: boolean;
  overBy: number;
}

export interface ChannelRow {
  channel: MediaChannel;
  key: ChannelKey;
  planned: number;
  selected: number;
  difference: number;
  inventories: number;
}

// detail.inventoryType is free-form ("Digital", "Digital Network", "Transit",
// "OOH", …). Only "digital*" is the digital channel; everything else is classic
// (mirrors the classification prefix match in inventory.utils.ts).
export function channelOfType(inventoryType: string): ChannelKey {
  return (inventoryType ?? "").toLowerCase().startsWith("digital")
    ? "digital"
    : "classic";
}

export function statFromItem(item: InventoryItem): SelectedStat {
  const p = item.performance;
  return {
    id: item.detail.id,
    estimatedCost: p?.estimatedCost ?? 0,
    estimatedImpressions:
      p?.estimatedImpression ?? p?.estimatedImpressions ?? 0,
    inventoryType: item.detail?.inventoryType ?? "",
  };
}

export function computeFooterTotals(
  map: Map<string, SelectedStat>,
  budget: number,
): FooterTotals {
  let impressions = 0;
  let cost = 0;
  for (const s of map.values()) {
    impressions += s.estimatedImpressions;
    cost += s.estimatedCost;
  }
  const overBy = Math.max(0, cost - budget);
  return { count: map.size, impressions, cost, overBudget: overBy > 0, overBy };
}

const CHANNEL_KEY: Record<string, ChannelKey> = {
  [MediaChannel.DIGITAL_OOH]: "digital",
  [MediaChannel.CLASSIC_OOH]: "classic",
};

export function computeChannelRows(
  map: Map<string, SelectedStat>,
  mediaChannels: string[],
  budgetAllocation: BudgetAllocation | undefined,
  budget: number,
): ChannelRow[] {
  const selectedByChannel: Record<ChannelKey, { cost: number; count: number }> =
    {
      digital: { cost: 0, count: 0 },
      classic: { cost: 0, count: 0 },
    };
  for (const s of map.values()) {
    const k = channelOfType(s.inventoryType);
    selectedByChannel[k].cost += s.estimatedCost;
    selectedByChannel[k].count += 1;
  }
  return (mediaChannels ?? [])
    .map((ch) => CHANNEL_KEY[ch] && { ch, key: CHANNEL_KEY[ch] })
    .filter(Boolean)
    .map(({ ch, key }: { ch: string; key: ChannelKey }) => {
      const pct = budgetAllocation ? (budgetAllocation[key] ?? 0) : 0;
      const planned = (pct / 100) * (budget ?? 0);
      const selected = selectedByChannel[key].cost;
      return {
        channel: ch as MediaChannel,
        key,
        planned,
        selected,
        difference: selected - planned,
        inventories: selectedByChannel[key].count,
      };
    });
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `yarn test src/pages/campaigns/inventory/manual-selection/__tests__/selection-stats.utils.test.ts`
Expected: PASS (all describe blocks green).

- [ ] **Step 5: Commit**

```bash
git add src/pages/campaigns/inventory/manual-selection/selection-stats.utils.ts src/pages/campaigns/inventory/manual-selection/__tests__/selection-stats.utils.test.ts
git commit -m "feat(inventory): add selection-stats helpers for manual footer"
```

---

## Task 3: Rewrite `useManualInventorySelection` to client-side

**Files:**

- Modify: `src/pages/campaigns/inventory/manual-selection/useManualInventorySelection.ts`
- Test: `src/pages/campaigns/inventory/manual-selection/__tests__/useManualInventorySelection.test.ts` (create)

**Interfaces:**

- Consumes: `useBulkSelectByIdsMutation` (Task 1); `statFromItem`, `computeFooterTotals`, `SelectedStat` (Task 2); existing `useBulkSelectInventoryMutation` (`/select-all`), `useGetSelectedInventoryQuery`/lazy variant, `buildFiltersWithoutPagination`.
- Produces the hook's return object:

  ```ts
  {
    inventoryItems: InventoryItem[];
    totalElements: number;
    sovValues: Record<string, number>;
    selectionMap: Map<string, SelectedStat>;   // NEW — footer/channel source
    isLoadingSelection: boolean;                // NEW — open loop-fetch in flight
    isSaving: boolean;                           // NEW — Save in flight
    isSelecting: boolean;                        // kept, always false now (no per-toggle API)
    getSelectAllState: () => SelectAllState;
    handleSelectAll: (checked: boolean) => void; // client-only + sets bulkMode
    handleItemSelection: (id: string, selected: boolean) => void; // client-only
    handleInventoryLoaded: (payload: InventoryListLoadedPayload) => void;
    loadSelectedInventory: () => Promise<void>;  // NEW — loop-fetch all pages → selectionMap
    saveSelection: () => Promise<boolean>;       // NEW — persist per §3; resolves true on success
    resetSelectionState: () => void;             // NEW behaviour: clears map + bulkMode
  }
  ```

- [ ] **Step 1: Add the lazy selected-inventory query hook** to the import from `@services/inventory/inventorySlice` in the hook file. Confirm `useLazyGetSelectedInventoryQuery` is exported; if only `useGetSelectedInventoryQuery` exists, add the lazy export in `inventorySlice.ts` exports block (RTK auto-generates `useLazyGetSelectedInventoryQuery` for every `builder.query`, so just add the name to the export list).

- [ ] **Step 2: Write the failing test**

```ts
import { configureStore } from "@reduxjs/toolkit";
import { renderHook, act, waitFor } from "@testing-library/react";
import { Provider } from "react-redux";
import { describe, expect, it, vi, beforeEach } from "vitest";

// Mocks for the three mutations/queries the hook uses.
const bulkByIdsTrigger = vi.fn(() => ({ unwrap: () => Promise.resolve({ success: true }) }));
const selectAllTrigger = vi.fn(() => ({ unwrap: () => Promise.resolve({ success: true }) }));
const selectedInvTrigger = vi.fn();

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

import { useManualInventorySelection } from "../useManualInventorySelection";
import { store as appStore } from "../../../../../store";

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <Provider store={appStore}>{children}</Provider>
);

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
      .mockReturnvalueOnce?.(page([item("a", 100)], 2, 0));
    // fallback for envs without mockReturnValueOnce alias
    selectedInvTrigger
      .mockReturnValueOnce(page([item("a", 100)], 2, 0))
      .mockReturnValueOnce(page([item("b", 200)], 2, 1));

    const { result } = renderHook(
      () => useManualInventorySelection("c1", { id: "c1" } as never, {} as never, []),
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
      () => useManualInventorySelection("c1", { id: "c1" } as never, {} as never, []),
      { wrapper },
    );
    // seed a loaded list item so the map can pull its stats
    act(() => {
      result.current.handleInventoryLoaded({
        content: [item("x", 5) as never],
        totalElements: 1,
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
    selectedInvTrigger.mockReturnValue(page([item("keep", 1), item("drop", 1)], 1, 0));
    const { result } = renderHook(
      () => useManualInventorySelection("c1", { id: "c1" } as never, {} as never, []),
      { wrapper },
    );
    await act(async () => { await result.current.loadSelectedInventory(); }); // baseline {keep, drop}
    act(() => {
      result.current.handleInventoryLoaded({
        content: [item("add", 1) as never], totalElements: 1, append: false, sovValuesToMerge: {},
      });
    });
    act(() => result.current.handleItemSelection("add", true)); // +add
    act(() => result.current.handleItemSelection("drop", false)); // -drop
    await act(async () => { await result.current.saveSelection(); });
    expect(bulkByIdsTrigger).toHaveBeenCalledWith(
      expect.objectContaining({ inventoryIds: ["add"], operationType: "SELECT" }),
    );
    expect(bulkByIdsTrigger).toHaveBeenCalledWith(
      expect.objectContaining({ inventoryIds: ["drop"], operationType: "DESELECT" }),
    );
    expect(selectAllTrigger).not.toHaveBeenCalled();
  });

  it("saveSelection calls /select-all when Select-All was used", async () => {
    selectedInvTrigger.mockReturnValue(page([], 1, 0));
    const { result } = renderHook(
      () => useManualInventorySelection("c1", { id: "c1" } as never, {} as never, []),
      { wrapper },
    );
    await act(async () => { await result.current.loadSelectedInventory(); });
    act(() => result.current.handleSelectAll(true)); // bulkMode = SELECT
    await act(async () => { await result.current.saveSelection(); });
    expect(selectAllTrigger).toHaveBeenCalledWith(
      expect.objectContaining({ campaignId: "c1", operationType: "SELECT" }),
    );
  });
});
```

- [ ] **Step 3: Run the test, verify it fails**

Run: `yarn test src/pages/campaigns/inventory/manual-selection/__tests__/useManualInventorySelection.test.ts`
Expected: FAIL — hook still exposes forecast-based API, no `selectionMap`/`saveSelection`.

- [ ] **Step 4: Rewrite the hook.** Replace the body of `useManualInventorySelection.ts` with the client-side implementation below (keeps `buildBaseFilterParams`/`buildFiltersWithoutPagination`/`handleInventoryLoaded` and the venue map; removes forecast + per-toggle `/select`):

```ts
import { useAnnounce } from "@hooks/useAnnounce";
import {
  useBulkSelectByIdsMutation,
  useBulkSelectInventoryMutation,
  useGetVenuesQuery,
  useLazyGetSelectedInventoryQuery,
} from "@services/inventory/inventorySlice";
import { useTranslate, useTolgee } from "@tolgee/react";
import {
  buildVenueIdMap,
  buildVenueTypeIdFilter,
} from "@utils/inventory.utils";
import { useCallback, useMemo, useRef, useState } from "react";
import type { CampaignCreateResponse } from "src/types/campaign.types";

import { statFromItem, type SelectedStat } from "./selection-stats.utils";
import type {
  InventoryFilterRequest,
  InventoryFilters,
  InventoryItem,
} from "../../../../types/inventory.types";
import { buildCampaignTargetingFilters } from "../inventoryFilters.utils";
import type { InventoryListLoadedPayload } from "../InventoryListPanel";

export interface SelectAllState {
  checked: boolean;
  indeterminate: boolean;
}

export function useManualInventorySelection(
  campaignId: string,
  campaignData: CampaignCreateResponse | null,
  effectiveFilters: InventoryFilters,
  mediaOwnerIds: string[] = [],
) {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { showError } = useAnnounce();

  const [inventoryItems, setInventoryItems] = useState<InventoryItem[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [sovValues, setSovValues] = useState<Record<string, number>>({});
  const [selectionMap, setSelectionMap] = useState<Map<string, SelectedStat>>(
    new Map(),
  );
  const [isLoadingSelection, setIsLoadingSelection] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  // null = untouched, "SELECT"/"DESELECT" = a bulk select-all/deselect-all this session.
  const [bulkMode, setBulkMode] = useState<"SELECT" | "DESELECT" | null>(null);

  // Baseline snapshot (ids selected when the popup opened) for the Save diff.
  const baselineRef = useRef<Set<string>>(new Set());
  // Stat lookup for items encountered this session (loaded list + baseline).
  const statCacheRef = useRef<Map<string, SelectedStat>>(new Map());

  const mediaOwnerIdsRef = useRef(mediaOwnerIds);
  mediaOwnerIdsRef.current = mediaOwnerIds;

  const [bulkSelectByIds] = useBulkSelectByIdsMutation();
  const [bulkSelectInventory] = useBulkSelectInventoryMutation(); // /select-all
  const [fetchSelectedInventory] = useLazyGetSelectedInventoryQuery();

  const language = useTolgee(["language"]).getLanguage();
  const { data: venuesData = [] } = useGetVenuesQuery({ language });
  const venueIdMap = useMemo(() => buildVenueIdMap(venuesData), [venuesData]);

  const buildBaseFilterParams =
    useCallback((): Partial<InventoryFilterRequest> => {
      const f = effectiveFilters;
      const baseParams: Partial<InventoryFilterRequest> = {};
      if (f.mediaOwners?.length > 0) baseParams.mediaOwnerIds = f.mediaOwners;
      if (f.sizes?.length > 0) baseParams.sizes = f.sizes;
      if (f.venueTypes?.length > 0) {
        const venueTypeIdFilter = buildVenueTypeIdFilter(
          f.venueTypes,
          f.inventoryClassification,
          venueIdMap,
        );
        if (venueTypeIdFilter) baseParams.venueTypeIdFilter = venueTypeIdFilter;
      }
      if (f.bookingMode?.length > 0) baseParams.bookingMode = f.bookingMode;
      if (f.environments?.length > 0) baseParams.environments = f.environments;
      if (f.inventoryClassification?.length > 0)
        baseParams.classifications = f.inventoryClassification;
      if (f.latitude?.trim()) baseParams.latitude = f.latitude.trim();
      if (f.longitude?.trim()) baseParams.longitude = f.longitude.trim();
      if (f.searchbyquery?.trim()) baseParams.name = f.searchbyquery.trim();
      if (f.programmaticSupport && f.programmaticSupport !== "ALL")
        baseParams.programmaticSupport = f.programmaticSupport;
      if (f.dealTypes?.length > 0)
        baseParams.dealTypes = f.dealTypes.map((d) => d.toLowerCase());
      return baseParams;
    }, [effectiveFilters, venueIdMap]);

  const buildFiltersWithoutPagination =
    useCallback((): InventoryFilterRequest => {
      return {
        ...buildBaseFilterParams(),
        ...buildCampaignTargetingFilters(campaignData),
      };
    }, [campaignData, buildBaseFilterParams]);

  // Loop-fetch EVERY page of /selected-inventory into the selectionMap + baseline.
  const loadSelectedInventory = useCallback(async () => {
    if (!campaignId) return;
    setIsLoadingSelection(true);
    try {
      const owners = mediaOwnerIdsRef.current;
      const map = new Map<string, SelectedStat>();
      let page = 0;
      let totalPages = 1;
      do {
        const res = await fetchSelectedInventory({
          campaignId,
          params: { page, size: 100, sortBy: "name", sortDir: "asc" },
          ...(owners.length > 0 ? { mediaOwnerIds: owners } : {}),
        }).unwrap();
        const data = res?.data;
        if (!data) break;
        totalPages = data.totalPages ?? 1;
        for (const it of data.content ?? []) {
          const stat = statFromItem(it);
          map.set(stat.id, stat);
          statCacheRef.current.set(stat.id, stat);
        }
        page += 1;
      } while (page < totalPages);
      baselineRef.current = new Set(map.keys());
      setSelectionMap(map);
      setBulkMode(null);
    } catch (error) {
      console.error("Error loading selected inventory:", error);
      showError(tCampaigns("inventories.manual.loadFailed"));
    } finally {
      setIsLoadingSelection(false);
    }
  }, [campaignId, fetchSelectedInventory, showError, tCampaigns]);

  const handleInventoryLoaded = useCallback(
    (payload: InventoryListLoadedPayload) => {
      setInventoryItems((prev) =>
        payload.append ? [...prev, ...payload.content] : payload.content,
      );
      setTotalElements(payload.totalElements);
      setSovValues((prev) => ({ ...prev, ...payload.sovValuesToMerge }));
      // Cache stats for every loaded item so toggles/selectAll can price them.
      for (const it of payload.content) {
        const stat = statFromItem(it);
        statCacheRef.current.set(stat.id, stat);
      }
    },
    [],
  );

  const getSelectAllState = useCallback((): SelectAllState => {
    if (bulkMode === "SELECT") return { checked: true, indeterminate: false };
    if (bulkMode === "DESELECT")
      return { checked: false, indeterminate: false };
    const selected = selectionMap.size;
    if (totalElements === 0 || selected === 0)
      return { checked: false, indeterminate: false };
    if (selected >= totalElements)
      return { checked: true, indeterminate: false };
    return { checked: false, indeterminate: true };
  }, [bulkMode, selectionMap, totalElements]);

  // Client-only select-all: mark loaded items + record intent. Footer is
  // best-effort here (unloaded items unknown) until Save + reopen.
  const handleSelectAll = useCallback(
    (checked: boolean) => {
      setBulkMode(checked ? "SELECT" : "DESELECT");
      setInventoryItems((prev) =>
        prev.map((item) => ({
          ...item,
          detail: { ...item.detail, isSelected: checked },
        })),
      );
      setSelectionMap(() => {
        if (!checked) return new Map();
        const next = new Map<string, SelectedStat>();
        for (const item of inventoryItems) {
          const stat = statFromItem(item);
          next.set(stat.id, stat);
        }
        return next;
      });
    },
    [inventoryItems],
  );

  const handleItemSelection = useCallback((id: string, selected: boolean) => {
    setInventoryItems((prev) =>
      prev.map((item) =>
        item.detail.id === id
          ? { ...item, detail: { ...item.detail, isSelected: selected } }
          : item,
      ),
    );
    setSelectionMap((prev) => {
      const next = new Map(prev);
      if (selected) {
        const stat =
          statCacheRef.current.get(id) ??
          ({
            id,
            estimatedCost: 0,
            estimatedImpressions: 0,
            inventoryType: "",
          } as SelectedStat);
        next.set(id, stat);
      } else {
        next.delete(id);
      }
      return next;
    });
  }, []);

  // Persist per spec §3. Resolves true on success (caller closes the popup).
  const saveSelection = useCallback(async (): Promise<boolean> => {
    if (!campaignId) return false;
    setIsSaving(true);
    try {
      if (bulkMode) {
        const filters = buildFiltersWithoutPagination();
        await bulkSelectInventory({
          campaignId,
          operationType: bulkMode,
          filters,
        }).unwrap();
        // Individual toggles made AFTER the bulk action, relative to what the
        // bulk op produced (all ids if SELECT, none if DESELECT).
        const bulkBaseline =
          bulkMode === "SELECT"
            ? new Set(inventoryItems.map((i) => i.detail.id))
            : new Set<string>();
        const added = [...selectionMap.keys()].filter(
          (id) => !bulkBaseline.has(id),
        );
        const removed = [...bulkBaseline].filter((id) => !selectionMap.has(id));
        if (added.length)
          await bulkSelectByIds({
            campaignId,
            inventoryIds: added,
            operationType: "SELECT",
          }).unwrap();
        if (removed.length)
          await bulkSelectByIds({
            campaignId,
            inventoryIds: removed,
            operationType: "DESELECT",
          }).unwrap();
      } else {
        const baseline = baselineRef.current;
        const added = [...selectionMap.keys()].filter(
          (id) => !baseline.has(id),
        );
        const removed = [...baseline].filter((id) => !selectionMap.has(id));
        if (added.length)
          await bulkSelectByIds({
            campaignId,
            inventoryIds: added,
            operationType: "SELECT",
          }).unwrap();
        if (removed.length)
          await bulkSelectByIds({
            campaignId,
            inventoryIds: removed,
            operationType: "DESELECT",
          }).unwrap();
      }
      return true;
    } catch (error) {
      console.error("Error saving selection:", error);
      showError(tCampaigns("inventories.manual.saveFailed"));
      return false;
    } finally {
      setIsSaving(false);
    }
  }, [
    campaignId,
    bulkMode,
    selectionMap,
    inventoryItems,
    buildFiltersWithoutPagination,
    bulkSelectInventory,
    bulkSelectByIds,
    showError,
    tCampaigns,
  ]);

  const resetSelectionState = useCallback(() => {
    setInventoryItems([]);
    setSelectionMap(new Map());
    setBulkMode(null);
    baselineRef.current = new Set();
  }, []);

  return {
    inventoryItems,
    totalElements,
    sovValues,
    selectionMap,
    isLoadingSelection,
    isSaving,
    isSelecting: false,
    getSelectAllState,
    handleSelectAll,
    handleItemSelection,
    handleInventoryLoaded,
    loadSelectedInventory,
    saveSelection,
    resetSelectionState,
  };
}
```

- [ ] **Step 5: Run the test, verify it passes**

Run: `yarn test src/pages/campaigns/inventory/manual-selection/__tests__/useManualInventorySelection.test.ts`
Expected: PASS. (If the `mockReturnValueOnce?.` alias line errors in your Vitest, delete that single stray line — the two chained `mockReturnValueOnce` below it are the real setup.)

- [ ] **Step 6: Commit**

```bash
git add src/pages/campaigns/inventory/manual-selection/useManualInventorySelection.ts src/pages/campaigns/inventory/manual-selection/__tests__/useManualInventorySelection.test.ts
git commit -m "refactor(inventory): make manual selection client-side with batch save"
```

---

## Task 4: `SelectionFooter` component

**Files:**

- Create: `src/pages/campaigns/inventory/manual-selection/SelectionFooter.tsx`
- Test: `src/pages/campaigns/inventory/manual-selection/__tests__/SelectionFooter.test.tsx`

**Interfaces:**

- Consumes: `FooterTotals` (Task 2), `formatCurrencyWithLocale`, `formatNumber`.
- Produces: default export `SelectionFooter` with props:

  ```ts
  interface SelectionFooterProps {
    totals: FooterTotals; // count/impressions/cost/overBudget/overBy
    budget: number; // campaignData.budget
    currency: string; // campaignData.currency
    isSaving: boolean;
    onCancel: () => void;
    onSave: () => void;
    onToggleChannels: () => void; // opens BudgetByChannelPopup
    channelsOpen: boolean;
  }
  ```

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { TolgeeProvider } from "@tolgee/react";
import { tolgee } from "@config/tolgee"; // adjust if the test-utils provides a wrapper

import SelectionFooter from "../SelectionFooter";

const totals = {
  count: 3,
  impressions: 2_300_000,
  cost: 130000,
  overBudget: false,
  overBy: 0,
};

const renderFooter = (props = {}) =>
  render(
    <TolgeeProvider tolgee={tolgee}>
      <SelectionFooter
        totals={totals}
        budget={200000}
        currency="MYR"
        isSaving={false}
        onCancel={vi.fn()}
        onSave={vi.fn()}
        onToggleChannels={vi.fn()}
        channelsOpen={false}
        {...props}
      />
    </TolgeeProvider>,
  );

describe("SelectionFooter", () => {
  it("renders zero-padded count, compact impressions, budget of total", () => {
    renderFooter();
    expect(screen.getByText("03")).toBeInTheDocument();
    expect(screen.getByText(/2\.30\s*M/)).toBeInTheDocument();
    expect(screen.getByText(/MYR/)).toBeInTheDocument();
  });
  it("fires onSave and onCancel", async () => {
    const onSave = vi.fn();
    const onCancel = vi.fn();
    renderFooter({ onSave, onCancel });
    await userEvent.click(
      screen.getByRole("button", { name: /save selection/i }),
    );
    await userEvent.click(screen.getByRole("button", { name: /cancel/i }));
    expect(onSave).toHaveBeenCalled();
    expect(onCancel).toHaveBeenCalled();
  });
  it("shows the over-budget warning icon when over", () => {
    renderFooter({ totals: { ...totals, overBudget: true, overBy: 170000 } });
    expect(screen.getByTestId("budget-warning")).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `yarn test src/pages/campaigns/inventory/manual-selection/__tests__/SelectionFooter.test.tsx`
Expected: FAIL — cannot resolve `../SelectionFooter`. (If the Tolgee import path differs, use the project's existing test wrapper as seen in `ManualSelectionPage.test.tsx`.)

- [ ] **Step 3: Implement the component**

```tsx
import { Button } from "@components/ui/Button";
import { useTranslate } from "@tolgee/react";
import { formatNumber } from "@utils/budget.utils";
import { formatCurrencyWithLocale } from "@utils/currency";
import { clsx } from "clsx";
import { AlertTriangle, ChevronDown, ChevronUp } from "lucide-react";

import type { FooterTotals } from "./selection-stats.utils";

interface SelectionFooterProps {
  totals: FooterTotals;
  budget: number;
  currency: string;
  isSaving: boolean;
  onCancel: () => void;
  onSave: () => void;
  onToggleChannels: () => void;
  channelsOpen: boolean;
}

const SelectionFooter = ({
  totals,
  budget,
  currency,
  isSaving,
  onCancel,
  onSave,
  onToggleChannels,
  channelsOpen,
}: SelectionFooterProps) => {
  const { t } = useTranslate(["campaigns"]);
  return (
    <div className="flex items-center gap-6 border-t border-mw-neutral-100 px-4 py-3 shrink-0">
      <Stat label={t("inventories.manual.footer.inventories")}>
        {String(totals.count).padStart(2, "0")}
      </Stat>
      <Stat label={t("inventories.manual.footer.estimatedImpressions")}>
        {formatNumber(totals.impressions)}
      </Stat>
      <Stat
        label={t("inventories.manual.footer.estimatedTotalBudget")}
        icon={
          totals.overBudget ? (
            <span
              data-testid="budget-warning"
              title={t("inventories.manual.footer.overBudgetTooltip", {
                planned: formatCurrencyWithLocale(budget, currency, 0),
                overBy: formatCurrencyWithLocale(totals.overBy, currency, 0),
              })}
            >
              <AlertTriangle className="w-4 h-4 text-mw-error-500" />
            </span>
          ) : undefined
        }
      >
        <span className={clsx(totals.overBudget && "text-mw-error-500")}>
          {formatCurrencyWithLocale(totals.cost, currency, 0)}
        </span>
        <span className="text-mw-grey-400">
          {" "}
          {t("inventories.manual.footer.of")}{" "}
          {formatCurrencyWithLocale(budget, currency, 0)}
        </span>
      </Stat>

      <button
        type="button"
        onClick={onToggleChannels}
        className="flex flex-col items-start text-left"
      >
        <span className="text-xs text-mw-grey-500">
          {t("inventories.manual.footer.budgetByChannel")}
        </span>
        <span
          className={clsx(
            "flex items-center gap-1 text-sm font-semibold",
            totals.overBudget ? "text-mw-error-500" : "text-mw-grey-800",
          )}
        >
          {totals.overBudget
            ? t("inventories.manual.footer.overPlan")
            : t("inventories.manual.footer.onPlan")}
          {channelsOpen ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </span>
      </button>

      <div className="ml-auto flex items-center gap-2">
        <Button variant="outline" onClick={onCancel} disabled={isSaving}>
          {t("inventories.manual.footer.cancel")}
        </Button>
        <Button onClick={onSave} disabled={isSaving}>
          {t("inventories.manual.footer.saveSelection")}
        </Button>
      </div>
    </div>
  );
};

const Stat = ({
  label,
  icon,
  children,
}: {
  label: string;
  icon?: React.ReactNode;
  children: React.ReactNode;
}) => (
  <div className="flex flex-col">
    <span className="flex items-center gap-1 text-xs text-mw-grey-500">
      {label}
      {icon}
    </span>
    <span className="text-sm font-semibold text-mw-grey-800">{children}</span>
  </div>
);

export default SelectionFooter;
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `yarn test src/pages/campaigns/inventory/manual-selection/__tests__/SelectionFooter.test.tsx`
Expected: PASS. (Adjust the `text-mw-error-500` token if the project uses a different error colour token — grep `text-mw-error` / `text-mw-red` in the repo and match.)

- [ ] **Step 5: Commit**

```bash
git add src/pages/campaigns/inventory/manual-selection/SelectionFooter.tsx src/pages/campaigns/inventory/manual-selection/__tests__/SelectionFooter.test.tsx
git commit -m "feat(inventory): add SelectionFooter for manual selection"
```

---

## Task 5: `BudgetByChannelPopup` component

**Files:**

- Create: `src/pages/campaigns/inventory/manual-selection/BudgetByChannelPopup.tsx`
- Test: `src/pages/campaigns/inventory/manual-selection/__tests__/BudgetByChannelPopup.test.tsx`

**Interfaces:**

- Consumes: `ChannelRow`, `computeChannelRows` (Task 2); `formatCurrencyWithLocale`; `MediaChannel`.
- Produces: default export `BudgetByChannelPopup` with props:

  ```ts
  interface BudgetByChannelPopupProps {
    isOpen: boolean;
    onClose: () => void;
    rows: ChannelRow[]; // from computeChannelRows in the parent
    currency: string;
  }
  ```

  Channel label i18n: `optimization.budgetAllocation.{digital|classic|transit|retail}` (already exist).

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { TolgeeProvider } from "@tolgee/react";
import { tolgee } from "@config/tolgee";
import { MediaChannel } from "@constants/inventory.constants";

import BudgetByChannelPopup from "../BudgetByChannelPopup";

const rows = [
  {
    channel: MediaChannel.DIGITAL_OOH,
    key: "digital",
    planned: 50000,
    selected: 28300,
    difference: -21700,
    inventories: 0,
  },
  {
    channel: MediaChannel.CLASSIC_OOH,
    key: "classic",
    planned: 50000,
    selected: 23350,
    difference: -26650,
    inventories: 8,
  },
] as const;

describe("BudgetByChannelPopup", () => {
  it("renders a row per channel plus a Total row when open", () => {
    render(
      <TolgeeProvider tolgee={tolgee}>
        <BudgetByChannelPopup
          isOpen
          onClose={vi.fn()}
          rows={rows as never}
          currency="MYR"
        />
      </TolgeeProvider>,
    );
    expect(screen.getByText(/Digital/)).toBeInTheDocument();
    expect(screen.getByText(/Classic/)).toBeInTheDocument();
    expect(screen.getByText(/8 Inventories/i)).toBeInTheDocument();
    // Total selected = 51650
    expect(screen.getByText(/51,650/)).toBeInTheDocument();
  });
  it("renders nothing when closed", () => {
    const { container } = render(
      <TolgeeProvider tolgee={tolgee}>
        <BudgetByChannelPopup
          isOpen={false}
          onClose={vi.fn()}
          rows={rows as never}
          currency="MYR"
        />
      </TolgeeProvider>,
    );
    expect(container).toBeEmptyDOMElement();
  });
});
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `yarn test src/pages/campaigns/inventory/manual-selection/__tests__/BudgetByChannelPopup.test.tsx`
Expected: FAIL — cannot resolve `../BudgetByChannelPopup`.

- [ ] **Step 3: Implement the component**

```tsx
import { useTranslate } from "@tolgee/react";
import { formatCurrencyWithLocale } from "@utils/currency";
import { clsx } from "clsx";

import type { ChannelKey, ChannelRow } from "./selection-stats.utils";

interface BudgetByChannelPopupProps {
  isOpen: boolean;
  onClose: () => void;
  rows: ChannelRow[];
  currency: string;
}

const LABEL_KEY: Record<ChannelKey, string> = {
  digital: "optimization.budgetAllocation.digital",
  classic: "optimization.budgetAllocation.classic",
};

const BudgetByChannelPopup = ({
  isOpen,
  onClose,
  rows,
  currency,
}: BudgetByChannelPopupProps) => {
  const { t } = useTranslate(["campaigns"]);
  if (!isOpen) return null;

  const total = rows.reduce(
    (acc, r) => ({
      planned: acc.planned + r.planned,
      selected: acc.selected + r.selected,
      difference: acc.difference + r.difference,
    }),
    { planned: 0, selected: 0, difference: 0 },
  );
  const anyOver = rows.some((r) => r.difference > 0);
  const money = (v: number) => formatCurrencyWithLocale(v, currency, 0);
  const diffText = (v: number) => (v > 0 ? `+ ${money(v)}` : money(v));
  const diffClass = (v: number) =>
    clsx(v > 0 ? "text-mw-warning-500" : "text-mw-grey-700");

  return (
    <>
      {/* click-outside to close */}
      <div className="fixed inset-0 z-40" onClick={onClose} />
      <div className="absolute bottom-16 left-4 z-50 w-[520px] rounded-lg border border-mw-primary-300 bg-white p-4 shadow-lg">
        <h3 className="text-sm font-semibold text-mw-grey-900">
          {t("inventories.manual.channelPopup.title")}
        </h3>
        <p className="mt-1 text-xs text-mw-grey-500">
          {t("inventories.manual.channelPopup.subtitle")}
        </p>
        <table className="mt-3 w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-mw-grey-500">
              <th className="py-2">
                {t("inventories.manual.channelPopup.channel")}
              </th>
              <th className="py-2">
                {t("inventories.manual.channelPopup.planned")}
              </th>
              <th className="py-2">
                {t("inventories.manual.channelPopup.selected")}
              </th>
              <th className="py-2">
                {t("inventories.manual.channelPopup.difference")}
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.key} className="border-t border-mw-neutral-100">
                <td className="py-2">
                  <div className="text-mw-grey-900">{t(LABEL_KEY[r.key])}</div>
                  <div className="text-xs text-mw-grey-400">
                    {t("inventories.manual.channelPopup.nInventories", {
                      count: r.inventories,
                    })}
                  </div>
                </td>
                <td className="py-2">{money(r.planned)}</td>
                <td className="py-2">{money(r.selected)}</td>
                <td className={clsx("py-2", diffClass(r.difference))}>
                  {diffText(r.difference)}
                </td>
              </tr>
            ))}
            <tr className="border-t border-mw-neutral-200 font-semibold">
              <td className="py-2">
                {t("inventories.manual.channelPopup.total")}
              </td>
              <td className="py-2">{money(total.planned)}</td>
              <td className="py-2">{money(total.selected)}</td>
              <td className={clsx("py-2", diffClass(total.difference))}>
                {diffText(total.difference)}
              </td>
            </tr>
          </tbody>
        </table>
        {anyOver && (
          <div className="mt-3 rounded-md bg-mw-warning-50 p-3 text-xs text-mw-warning-600">
            {t("inventories.manual.channelPopup.overNote")}
          </div>
        )}
      </div>
    </>
  );
};

export default BudgetByChannelPopup;
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `yarn test src/pages/campaigns/inventory/manual-selection/__tests__/BudgetByChannelPopup.test.tsx`
Expected: PASS. (If `text-mw-warning-*` tokens don't exist, grep the repo for the orange/amber token used elsewhere — e.g. `text-mw-orange` — and substitute.)

- [ ] **Step 5: Commit**

```bash
git add src/pages/campaigns/inventory/manual-selection/BudgetByChannelPopup.tsx src/pages/campaigns/inventory/manual-selection/__tests__/BudgetByChannelPopup.test.tsx
git commit -m "feat(inventory): add BudgetByChannelPopup for manual footer"
```

---

## Task 6: i18n keys

**Files:**

- Modify: `src/assets/i18n/campaigns/en.json` (the `inventories.manual` block ~line 620)
- Modify: `src/assets/i18n/campaigns/ja.json` (matching block)

- [ ] **Step 1: Extend the `manual` block in `en.json`.** Replace the existing block:

```json
    "manual": {
      "title": "Inventories - Manual selection",
      "selectAll": "Select All",
      "selectFailed": "Failed to update selection. Please try again.",
      "save": "Save",
      "close": "Close",
      "loadFailed": "Failed to load selected inventory. Please try again.",
      "saveFailed": "Failed to save selection. Please try again.",
      "footer": {
        "inventories": "Inventories",
        "estimatedImpressions": "Estimated Impressions",
        "estimatedTotalBudget": "Estimated Total Budget",
        "of": "of",
        "budgetByChannel": "Budget by channel",
        "overPlan": "Over - plan",
        "onPlan": "On plan",
        "cancel": "Cancel",
        "saveSelection": "Save Selection",
        "overBudgetTooltip": "Budget limit exceeding. You are exceeding your estimated budget {planned} by {overBy}."
      },
      "channelPopup": {
        "title": "Budget vs. selection by channel",
        "subtitle": "Compare the budget you distributed by channel in Step 2 with the cost of your current selection. This is a guide only — you can save even if a channel is over.",
        "channel": "Channel",
        "planned": "Planned",
        "selected": "Selected",
        "difference": "Difference",
        "nInventories": "{count} Inventories",
        "total": "Total",
        "overNote": "Some channels are over their planned budget or include unplanned channels. You can still save — just confirm this is intentional."
      }
    },
```

- [ ] **Step 2: Add the same keys to `ja.json`** with Japanese translations (mirror the structure; if unsure of wording, reuse the English string as a placeholder value but keep every key present so lookups don't fall back). Example minimal block:

```json
    "manual": {
      "title": "インベントリ - 手動選択",
      "selectAll": "すべて選択",
      "selectFailed": "選択を更新できませんでした。もう一度お試しください。",
      "save": "保存",
      "close": "閉じる",
      "loadFailed": "選択済みインベントリの読み込みに失敗しました。",
      "saveFailed": "選択の保存に失敗しました。",
      "footer": {
        "inventories": "インベントリ",
        "estimatedImpressions": "推定インプレッション",
        "estimatedTotalBudget": "推定合計予算",
        "of": "/",
        "budgetByChannel": "チャネル別予算",
        "overPlan": "予算超過",
        "onPlan": "予算内",
        "cancel": "キャンセル",
        "saveSelection": "選択を保存",
        "overBudgetTooltip": "予算上限を超えています。推定予算 {planned} を {overBy} 超過しています。"
      },
      "channelPopup": {
        "title": "チャネル別の予算と選択",
        "subtitle": "ステップ2で配分した予算と現在の選択のコストを比較します。目安です。チャネルが超過していても保存できます。",
        "channel": "チャネル",
        "planned": "計画",
        "selected": "選択済み",
        "difference": "差分",
        "nInventories": "{count} インベントリ",
        "total": "合計",
        "overNote": "一部のチャネルは計画予算を超過しているか、計画外のチャネルを含みます。保存は可能です。意図的かご確認ください。"
      }
    },
```

- [ ] **Step 3: Validate JSON**

Run: `node -e "require('./src/assets/i18n/campaigns/en.json'); require('./src/assets/i18n/campaigns/ja.json'); console.log('ok')"`
Expected: prints `ok` (no JSON parse error).

- [ ] **Step 4: Commit**

```bash
git add src/assets/i18n/campaigns/en.json src/assets/i18n/campaigns/ja.json
git commit -m "i18n(campaigns): manual selection footer + channel popup keys"
```

---

## Task 7: Wire footer + header X + selected-count into `ManualSelectionPage` and `InventoryListPanel`

**Files:**

- Modify: `src/pages/campaigns/inventory/manual-selection/ManualSelectionPage.tsx`
- Modify: `src/pages/campaigns/inventory/InventoryListPanel.tsx:69-82,596-613`
- Modify: `src/pages/campaigns/inventory/manual-selection/__tests__/ManualSelectionPage.test.tsx`

**Interfaces:**

- Consumes: everything from Tasks 2–5 and the reworked hook (Task 3).
- Produces: no new public interface.

- [ ] **Step 1: Add a `selectedCount` prop to `InventoryListPanel`.** In `InventoryListPanelProps` (line ~69) add:

```ts
  /** Selected count for the header badge (client-side manual selection). */
  selectedCount?: number;
```

Destructure it in the component signature (near line 118) with a default: `selectedCount,`. Then change the count badge (line ~613) from:

```tsx
{forecastData?.totalInventories || 0}/{totalElements}
```

to:

```tsx
{(selectedCount ?? forecastData?.totalInventories) || 0}/{totalElements}
```

(Keeps the existing behaviour for any other caller that still passes `forecastData`.)

- [ ] **Step 2: Update `ManualSelectionPage` imports.** Add:

```tsx
import { X } from "lucide-react";
```

Add the local imports:

```tsx
import BudgetByChannelPopup from "./BudgetByChannelPopup";
import SelectionFooter from "./SelectionFooter";
import {
  computeChannelRows,
  computeFooterTotals,
} from "./selection-stats.utils";
```

Remove `Button` from the `@components/ui/Button` import only if it is no longer used after replacing the header button — it is still used elsewhere in the file (filter/upload buttons), so KEEP the `Button` import.

- [ ] **Step 3: Replace the header Save button** (lines ~287-293) with the X button:

```tsx
<button
  type="button"
  onClick={handleClose}
  aria-label={tCampaigns("inventories.manual.close")}
  className="text-mw-neutral-600 hover:text-mw-grey-900"
>
  <X className="w-5 h-5" />
</button>
```

- [ ] **Step 4: Load selection on open + add popup state.** Replace the reset effect (lines ~194-197) so opening also loop-fetches selection, and add channel-popup state near the other `useState`s:

```tsx
const [channelsOpen, setChannelsOpen] = useState(false);

// On open: reset transient state and loop-fetch the current selection.
useEffect(() => {
  if (isOpen) {
    sel.resetSelectionState();
    sel.loadSelectedInventory();
    setChannelsOpen(false);
  }
  // eslint-disable-next-line react-hooks/exhaustive-deps
}, [isOpen]);
```

- [ ] **Step 5: Add close/save handlers** (below the other handlers, before `if (!mounted) return null;`):

```tsx
// X / Cancel: discard — just close. Reopen re-fetches the saved selection.
const handleClose = () => onClose();

const handleSaveSelection = async () => {
  const ok = await sel.saveSelection();
  if (ok) onClose();
};

const budget = campaignData?.budget ?? 0;
const footerTotals = useMemo(
  () => computeFooterTotals(sel.selectionMap, budget),
  [sel.selectionMap, budget],
);
const channelRows = useMemo(
  () =>
    computeChannelRows(
      sel.selectionMap,
      campaignData?.mediaChannels ?? [],
      campaignData?.budgetAllocation,
      budget,
    ),
  [
    sel.selectionMap,
    campaignData?.mediaChannels,
    campaignData?.budgetAllocation,
    budget,
  ],
);
```

- [ ] **Step 6: Pass `selectedCount` to the panel.** In the `<InventoryListPanel ... />` props (near line 350) add:

```tsx
selectedCount={sel.selectionMap.size}
```

Also remove the now-unused `forecastData={sel.forecastData}` line (the hook no longer returns `forecastData`) — replace it with `forecastData={null}` to satisfy the prop type without reintroducing forecast, OR make the panel prop optional. Simplest: change the panel prop to `forecastData?: CampaignForecastData | null;` (add `?`) and drop the line here.

- [ ] **Step 7: Mount the footer + popup.** Immediately AFTER the body `</div>` that closes the "list left / map right" flex row (line ~393) and before the Details Drawer block, add:

```tsx
{
  /* Budget-by-channel popup (anchored above the footer) */
}
<div className="relative">
  <BudgetByChannelPopup
    isOpen={channelsOpen}
    onClose={() => setChannelsOpen(false)}
    rows={channelRows}
    currency={campaignCurrency}
  />
</div>;

{
  /* Stats footer */
}
<SelectionFooter
  totals={footerTotals}
  budget={budget}
  currency={campaignCurrency}
  isSaving={sel.isSaving}
  onCancel={handleClose}
  onSave={handleSaveSelection}
  onToggleChannels={() => setChannelsOpen((v) => !v)}
  channelsOpen={channelsOpen}
/>;
```

- [ ] **Step 8: Update `ManualSelectionPage.test.tsx` mocks.** In the `@services/inventory/inventorySlice` mock, add `useBulkSelectByIdsMutation: () => [vi.fn(() => ({ unwrap: () => Promise.resolve({ success: true }) }))]` and `useLazyGetSelectedInventoryQuery: () => [vi.fn(() => ({ unwrap: () => Promise.resolve({ success: true, data: { content: [], totalPages: 1, number: 0, totalElements: 0 } }) }))]`. Remove any assertion that a forecast/`/select` call fires on toggle. Add/adjust a test:

```tsx
it("shows the footer Save Selection and Cancel buttons", async () => {
  // render the page with isOpen (reuse the file's existing render helper)
  expect(
    await screen.findByRole("button", { name: /save selection/i }),
  ).toBeInTheDocument();
  expect(screen.getByRole("button", { name: /cancel/i })).toBeInTheDocument();
});
```

- [ ] **Step 9: Run the page tests, verify they pass**

Run: `yarn test src/pages/campaigns/inventory/manual-selection/__tests__/ManualSelectionPage.test.tsx`
Expected: PASS.

- [ ] **Step 10: Typecheck + lint the touched files**

Run: `yarn build && yarn lint`
Expected: build passes, lint zero warnings. Fix `import/order` groupings if lint complains.

- [ ] **Step 11: Commit**

```bash
git add src/pages/campaigns/inventory/manual-selection/ManualSelectionPage.tsx src/pages/campaigns/inventory/InventoryListPanel.tsx src/pages/campaigns/inventory/manual-selection/__tests__/ManualSelectionPage.test.tsx
git commit -m "feat(inventory): wire client-side footer + channel popup into manual selection"
```

---

## Task 8: Full regression sweep + bug_tracker note

**Files:**

- Modify (if broken by the hook change): `src/pages/campaigns/inventory/plan-summary/__tests__/useRestoreRecommendation.test.ts`, `src/pages/campaigns/inventory/__tests__/InventoryPageForm.test.tsx`
- Modify: `bug_tracker.md` (only if this fixes a tracked bug)

- [ ] **Step 1: Run the whole inventory test area**

Run: `yarn test src/pages/campaigns/inventory`
Expected: all PASS. `useRestoreRecommendation` still uses `useBulkSelectInventoryMutation` (`/select-all`) — unchanged — so it should be unaffected; fix only if a shared mock changed.

- [ ] **Step 2: Run the full suite**

Run: `yarn test`
Expected: all PASS (Node 20).

- [ ] **Step 3: Manual smoke (dev server)** — `yarn dev`, open a campaign at Step 4, click Edit Manually:
  - Popup opens, current selection loads (count in the list badge matches footer Inventories).
  - Toggle items → footer count/impressions/budget update instantly, NO network call on toggle (check devtools Network).
  - Over-budget → warning icon + tooltip; `Over - plan` toggle opens the channel table.
  - Cancel/X → reopen shows the original selection (no persist).
  - Save Selection → `/bulk-select` (or `/select-all` if Select-All used) fires once, popup closes.

- [ ] **Step 4: Update `bug_tracker.md`** only if this work closed a tracked bug (per repo convention). If it is a pure feature, skip.

- [ ] **Step 5: Commit any test fixes**

```bash
git add -A
git commit -m "test(inventory): align mocks with client-side manual selection"
```

---

## Self-Review Notes (author checklist — already applied)

- **Spec coverage:** X button (T7), footer stats (T2/T4/T7), over-budget tooltip (T2/T4), budget-by-channel popup (T2/T5/T7), client-side toggle no-API (T3), loop-fetch on open (T3), Save diff via `/bulk-select` (T1/T3), Select-All via `/select-all` (T3), Cancel/X discard (T7), i18n (T6), tests (all tasks + T8). Covered.
- **Type consistency:** `SelectedStat`, `ChannelRow`, `FooterTotals`, `channelOfType`, `statFromItem`, `computeFooterTotals`, `computeChannelRows`, `saveSelection`, `loadSelectedInventory`, `selectionMap` used identically across tasks.
- **Known adjustable spots flagged inline:** Tailwind error/warning colour tokens (grep and match), Tolgee test wrapper import path (match `ManualSelectionPage.test.tsx`), lazy query hook auto-export.

```

```
