# Selected-Inventory Availability % (View popup) — Design

Date: 2026-07-21
Status: Implemented

## Problem

In the "View Inventories" popup (`ViewInventoriesPage` → `InventoryMapView` left list),
each `SelectedInventoryCard`'s expanded section shows a labeled stack: **Media Owner / CPM /
Impressions / Availability**. The **Availability** value was a hardcoded placeholder
(`SelectedInventoryCard.tsx` rendered `{"--"}`). We need a real availability percentage per
inventory, over the campaign date range.

## Goal

When the user expands an inventory card, fetch that inventory's booking availability over the
campaign's `startDate → endDate`, compute an availability percentage, and show it (rounded
integer `%`, green) at the Availability label.

Formula (per inventory, summed across all campaign days):

```
sumTotal      = sumBooked + sumReserved + sumAvailable
availability% = round( sumAvailable / sumTotal * 100 )      // null when sumTotal === 0
```

Reserved counts as unavailable (`availableSpots` already excludes booked **and** reserved).

## Fetch trigger (key decision)

Availability is fetched **lazily, per inventory, on card expand** — NOT in bulk. When the
user clicks the expand arrow on a card, a single-inventory `/availability` call fires for
that inventory only. Each inventory is fetched at most once; the result is cached and
re-expanding never refetches. Cards that are never expanded never fetch.

## Non-Goals

- The Availability **calendar** view (`SelectedInventoryAvailability` /
  `InventoryAvailabilityCalendarView`) is untouched.
- The `/results` recommendation-score path (`useRecommendationScores`) is untouched.
- No threshold-based coloring — keep existing green styling.
- No bulk / pagination-driven fetching.

## Existing pieces reused

- `POST inventory-api/api/v1/inventories/availability` via
  `useLazyGetInventoryAvailabilityQuery` (`inventorySlice.ts`). `{ inventoryIds, startTime,
endTime }`; response keyed by inventory id.
- `parseAvailabilityResponse` — unwraps `{ inventories: Record<externalId, data> }`.
- `buildAvailabilityIndex(data, item)` — per-inventory index (derives
  `clientPerLoop`/`slotDuration`/`isClassic` from the `InventoryItem`).
- `getSpotDataFromIndex(date, undefined, "monthly", index)` — per-day spot data; handles
  blackout/closed days and classic inventory.
- `campaignData.startDate` / `endDate` already reach `InventoryMapView` as
  `campaignStartDate` / `campaignEndDate`.
- Each list item is an `InventoryItem` with `detail.id`, `detail.externalId`.

## Design — three isolated units

### 1. Pure util — `getAvailabilityPercentFromIndex(index, startDate, endDate)`

`src/utils/inventoryavailability.utils.ts`. Iterates each day in the inclusive range, sums
`totalSpots` and `availableSpots` via `getSpotDataFromIndex`, returns
`round(sumAvailable / sumTotal * 100)`, or `null` when `sumTotal <= 0` or `startDate >
endDate`. Pure and unit-testable.

### 2. Hook — `useSelectedInventoryAvailability({ startDate, endDate })`

`src/pages/campaigns/inventory/view/useSelectedInventoryAvailability.ts`. Returns:

```ts
{
  availabilityById: Record<string, { loading: boolean; percent: number | null }>;
  requestAvailability: (item: InventoryItem) => void;
}
```

`requestAvailability(item)`:

- No-op if the item has no `externalId`, campaign dates are missing, or its `externalId`
  was already requested (tracked in a `Set` ref → fetch-once).
- Marks `detail.id` `{ loading: true }`, fires a single-inventory bulk-shaped call
  (`inventoryIds: [externalId]`, campaign range), parses, `buildAvailabilityIndex`, computes
  percent, stores `{ loading: false, percent }` keyed by `detail.id`.
- On failure: `{ loading: false, percent: null }` (no crash).

Campaign date strings are parsed defensively (accepts `YYYY-MM-DD` or full ISO datetime).

### 3. Presentational wire-through

- `InventoryMapView` calls the hook. Its `toggleExpanded(item)` calls
  `requestAvailability(item)` the first time a card expands, and passes
  `availability={availabilityById[item.detail.id]}` to each `SelectedInventoryCard`.
- `SelectedInventoryCard` Availability value: `loading` → `<Spinner size="sm">`,
  `percent != null` → `` `${percent}%` `` (green), else `--`.

## Data flow

```
ViewInventoriesPage (campaignData.startDate/endDate)
  → InventoryMapView
      → useSelectedInventoryAvailability({ startDate, endDate })
      → card expand → requestAvailability(item) → POST /availability { [externalId], range }
      → SelectedInventoryCard (spinner / NN% / --)
```

## Error handling & edge cases

- Missing campaign dates or `externalId` → no fetch, `--`.
- API/network failure → that inventory resolves to `--`.
- Classic inventory → handled by `buildAvailabilityIndex` (`isClassic`).
- Blackout / non-operating days → 0 to both sums (excluded naturally).
- `sumTotal === 0` → `null` → `--`.
- Re-expand → served from cache, no refetch.

## Testing

- **Util**: no-bookings → 100; bookings → reduced integer < 100; `start > end` → null;
  empty operating schedule → null.
- **Hook**: single request fetches + resolves; re-request same id does not refetch; distinct
  ids fetched once each; missing dates → no fetch; failure → `percent: null`.
- **Card**: spinner / `NN%` / `--` states.
- **Regression**: `InventoryMapViewDrawer.test.tsx` stubs the hook (no Redux store needed).

## Files touched

| File                                                                 | Change                                |
| -------------------------------------------------------------------- | ------------------------------------- |
| `utils/inventoryavailability.utils.ts`                               | + `getAvailabilityPercentFromIndex`   |
| `pages/campaigns/inventory/view/useSelectedInventoryAvailability.ts` | new hook                              |
| `pages/campaigns/inventory/InventoryMapView.tsx`                     | call hook, fetch on expand, pass prop |
| `pages/campaigns/inventory/SelectedInventoryCard.tsx`                | render availability entry             |
| `__tests__` (util, hook, card, InventoryMapViewDrawer)               | tests added / stub added              |

This is a feature, not a bug fix → no `bug_tracker.md` entry.
