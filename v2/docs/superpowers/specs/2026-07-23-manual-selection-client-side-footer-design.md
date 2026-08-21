# Manual Inventory Selection — Client-Side Selection + Stats Footer

**Date:** 2026-07-23
**Scope:** Campaign wizard, Step 4 (Inventories) → "Edit Manually" popup (`ManualSelectionPage.tsx`) only. Recommendation smart-suggestion list and all other selection UI are untouched.

## Summary

Rework the Edit-Manual inventory popup so inventory selection is **client-side only** (no API per toggle), persisted in a single batch when the user clicks **Save Selection**. Add a stats footer (selected count, estimated impressions, estimated total budget vs planned) with a Budget-by-channel breakdown popup.

## Behaviour changes

### 1. Selection is client-side (no API on toggle)

- **On popup open:** loop-fetch `/campaign-inventory/{id}/selected-inventory` (paginated) page by page until every page is loaded (`page = 0 … totalPages-1`), accumulating `content[]`. Seed a client `selectionMap: Map<inventoryId, SelectedStat>` where
  `SelectedStat = { id, estimatedCost, estimatedImpressions, classification, type }`
  (from `item.cost.estimatedCost`, `item.performance.estimatedImpression(s)`, `item.inventoryDetails.classification`, `item.inventoryDetails.type`). This is the **baseline** selection.
- **On individual select/deselect** (list checkbox / card click): NO API call. Add/remove the item in `selectionMap` and flip `detail.isSelected` in the in-memory list. New items pull their stat fields from the loaded list item (`InventoryItem` carries `cost` + `performance` + `inventoryDetails`).
- **Remove from `useManualInventorySelection`:** the per-toggle `/select` mutation call, the per-toggle `/forecast` reload, and the current per-toggle `/select-all` call. `handleItemSelection` becomes pure client-state mutation.
- The recommendation flow and any other callers of `useSelectInventoryMutation` / forecast keep their current behaviour.

### 2. Select All / Deselect All

- The Select-All checkbox stays.
- Select-All / Deselect-All is **client-side only — NO API on click** (same rule as individual toggles). Clicking it sets a session flag `bulkMode: "SELECT" | "DESELECT" | null` and visually marks the loaded list items. It is persisted only on **Save Selection** (see §3), and discarded by Cancel/X.
- **Footer during an active Select-All:** because filters can match more inventory than is loaded, the client does not hold every matched item's stats. So while `bulkMode` is active the footer is best-effort: Inventories count shows `totalElements` (all matched); Estimated Impressions / Total Budget are summed from the items currently in memory and may be partial. They become exact after Save Selection → parent refetch → reopen (which loop-fetches `/selected-inventory`). Individual selection is always exact.

### 3. Header X, footer Cancel / Save Selection

- **Header:** replace the current `Save` `<Button>` with a plain X icon button, copying `ViewInventoriesPage.tsx`:
  ```tsx
  <button
    type="button"
    onClick={onClose}
    aria-label={tCampaigns("inventories.manual.close")}
    className="text-mw-neutral-600 hover:text-mw-grey-900"
  >
    <X className="w-5 h-5" />
  </button>
  ```
- **Footer buttons:** `Cancel` (outline) + `Save Selection` (primary).
- **X and Cancel = discard:** close without any persist of individual toggles. Reopening re-fetches `/selected-inventory` → shows the last-saved set. Example: 10 selected → add 5, remove 3, click Cancel/X → reopen shows the original 10.
- **Save Selection = persist**, branching on whether Select-All/Deselect-All was used this session:
  - **If `bulkMode` is set** (user clicked Select-All or Deselect-All) → call the existing filter-based `/select-all?operationType={bulkMode}` (filters payload). If the user also made individual toggles _after_ the bulk action, additionally call `/bulk-select` for those deltas (SELECT and/or DESELECT id sets) so the manual edits on top of the bulk op are persisted.
  - **Else (manual only)** → persist the diff vs baseline via the new endpoint:
    - `added = current \ baseline` → `POST /campaign-inventory/{id}/bulk-select { campaignId, inventoryIds, operationType: "SELECT" }`
    - `removed = baseline \ current` → same endpoint with `operationType: "DESELECT"`
    - Skip a call when its id set is empty.
  - On success: close and let the existing `handleManualClose` in `InventoryPageForm` refresh forecast / reach-curve for downstream steps.
  - Show a loading state on the button while the calls run; on error show a toast and keep the popup open.

### New RTK Query mutation

Add to `src/services/inventory/inventorySlice.ts`, alongside the existing filter-based `bulkSelectInventory`:

```ts
bulkSelectByIds: builder.mutation<
  SuccessResponse<InventorySelectionResponse>,
  { campaignId: string; inventoryIds: string[]; operationType: "SELECT" | "DESELECT" }
>({
  query: ({ campaignId, inventoryIds, operationType }) => ({
    url: `/campaign-inventory/${campaignId}/bulk-select`,
    method: "POST",
    data: { campaignId, inventoryIds, operationType },
  }),
}),
```

`POST /campaign-inventory/{campaignId}/bulk-select` — body `{ campaignId, inventoryIds: string[], operationType }`. Distinct from the existing `/select-all` (filters) endpoint.

## Footer UI (mockup 1)

All values derived from `selectionMap`, recomputed on every toggle. Currency = `campaignData.currency`; total budget = `campaignData.budget`.

| Field                  | Value                                                                                |
| ---------------------- | ------------------------------------------------------------------------------------ |
| Inventories            | `selectionMap.size`, zero-padded (`03`)                                              |
| Estimated Impressions  | Σ `estimatedImpressions`, compact format (`2.30 M`)                                  |
| Estimated Total Budget | `Σ estimatedCost` shown as `MYR 130,000 of MYR 200,000` (of = `campaignData.budget`) |
| Budget by channel      | `Over-plan ⌄` toggle → opens breakdown popup                                         |

- **Over-budget warning:** when `Σ estimatedCost > campaignData.budget`, show a ⚠ icon next to Estimated Total Budget; hovering shows the tooltip (mockup 2): "Budget limit exceeding — You are exceeding your estimated budget {planned} by {overBy}."

## Budget-by-channel popup (mockup 3)

Opened from the `Over-plan ⌄` toggle. Table columns: **Channel | Planned | Selected | Difference**.

- **Rows** = only the campaign's selected `mediaChannels` (`DIGITAL_OOH` → classification `Digital`; `CLASSIC_OOH` → classification `Classic`), plus a **Total** row.
- **Planned** (per channel) = `budgetAllocation[key] / 100 × campaignData.budget`, where `budgetAllocation` is `{ digital, classic, transit, retail }` percentages (`campaign.types.ts`). Channel→key via the `CHANNEL_CONFIG` map (DIGITAL_OOH→digital, CLASSIC_OOH→classic).
- **Selected** (per channel) = Σ `estimatedCost` of `selectionMap` items whose `classification` matches that channel.
- **`n Inventories`** subcount per channel = count of `selectionMap` items in that channel.
- **Difference** = `Selected − Planned`; render orange with `+` prefix when over, neutral otherwise.
- **Total row** = column sums.
- **Note banner** (when any channel over or has unplanned channels): "Some channels are over their planned budget or include unplanned channels. You can still save — just confirm this is intentional."

## Components / files

- `ManualSelectionPage.tsx` — header X, mount the footer, own the popup-open loop-fetch + `selectionMap`, wire Save/Cancel.
- `useManualInventorySelection.ts` — strip per-toggle `/select`, `/forecast`, `/select-all`; add `selectionMap` state + `bulkMode` flag, loop-fetch-all on open, client-only toggle + select-all, save branching (`/select-all` vs `bulkSelectByIds`) per §3.
- **New** `manual-selection/SelectionFooter.tsx` — presentational footer (count / impressions / budget / over-plan toggle + buttons). Props in, callbacks out.
- **New** `manual-selection/BudgetByChannelPopup.tsx` — the channel breakdown table.
- **New** `manual-selection/useSelectionStats.ts` (optional) — pure helpers: totals + per-channel grouping from `selectionMap` + `campaignData`. Unit-testable in isolation.
- `inventorySlice.ts` — add `bulkSelectByIds` mutation + `useBulkSelectByIdsMutation` export.

## i18n

Add keys to `campaigns` `en.json` + `ja.json`: `inventories.manual.close`, footer labels (`footer.inventories`, `footer.estimatedImpressions`, `footer.estimatedTotalBudget`, `footer.of`, `footer.budgetByChannel`, `footer.overPlan`), warning tooltip, and channel-popup strings (title, subtitle, columns, `nInventories`, total, over-budget note).

## Testing

- `useSelectionStats` (or the stat helpers): totals, compact impressions, per-channel grouping incl. empty channels, over-budget threshold.
- `useManualInventorySelection`: open loop-fetches all pages; toggle mutates map with no API; Save sends correct SELECT/DESELECT diff; Cancel makes no persist call; Select-All calls `/select-all` then refetches.
- `ManualSelectionPage.test.tsx`: X and footer buttons render; Cancel/X discard; Save calls the mutations.
- `BudgetByChannelPopup`: rows match mediaChannels, difference sign/colour, total row.
- Update existing mocks in `ManualSelectionPage.test.tsx`, `useRestoreRecommendation.test.ts` (bulk-select hook rename impact), `InventoryPageForm.test.tsx`.

## Confirmed decisions

- Select-All/Deselect-All is client-side only; persisted via `/select-all` on Save; discarded by Cancel/X.
- `/bulk-select` SELECT/DESELECT are additive against server state (matches the sample curl) — no full-replace semantics.
- Footer during an active Select-All is best-effort (see §2); exact after Save + reopen.

## Non-goals

- No backend changes (uses existing `/select-all`, `/selected-inventory`, and the new `/bulk-select` already on staging).
- No changes to the recommendation smart-suggestion selection flow.
- No Cinema/Transit channel additions to the budget model.
