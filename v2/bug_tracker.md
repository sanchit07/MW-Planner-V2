# Bug Tracker

Inventory step redesign — bugs found & fixed.

| #     | Bug                                                                                                                                                           | Root cause                                                                                                                                                                                                                                                                    | Fix                                                                                                                                                                                                                                                                                                                                                                              | Files                                                                                                                                                                                                                                      |
| ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1 & 2 | Open "Edit Manually" before recommendation finishes generating → empty / stale list (count updated but list empty, or old recommendation's selections shown). | The manual list uses `POST /campaign-inventory/{id}/filter`, only populated after recommendation **generation** completes. Opening early fetched an empty/mid-generation list. (Earlier mask+reload and skip-now approaches were tried but were awkward / raced the backend.) | **Disable until ready:** the "Edit Manually" and "View" buttons are disabled until `status === "completed"`, so the manual list is only openable when `/filter` is populated. Removed all skip plumbing (`skipRecommendation` param/path, `sessionSkipped`, autosave, `dataReady` loading-gate).                                                                                 | `InventoryPageForm.tsx`, `plan-summary/AiSmartRecommendationPanel.tsx`, `plan-summary/PlanSummaryPanel.tsx`, `plan-summary/useRecommendationForecast.ts`, `manual-selection/ManualSelectionPage.tsx`                                       |
| 4     | Reach Build chart didn't render in skip/manual mode (no recommendation run).                                                                                  | The reach curve was built only from `measure-summary`, which is keyed by `runId`. Skipped = no `runId` → curve never fetched.                                                                                                                                                 | `useReachCurve` now takes `campaignId` and, when there's no `runId`, builds the curve from the campaign's **selected-inventory** list (`/campaign-inventory/{id}/selected-inventory`, paginated) — those items carry `performance.estimatedReach` + `estimatedCost`. Added the missing reach/impression/frequency/sov fields to `InventoryPerformance`.                          | `plan-summary/useReachCurve.ts`, `InventoryPageForm.tsx`, `types/inventory.types.ts`, `__tests__/useReachCurve.test.ts`                                                                                                                    |
| 3     | "Restore AI recommendation" left prior selections — `/generate` does not unselect existing campaign-inventory picks.                                          | Restore alone (regenerate) doesn't reset selections.                                                                                                                                                                                                                          | Restore = bulk deselect-all (`POST /campaign-inventory/{id}/select-all?operationType=DESELECT` with `buildCampaignTargetingFilters`) + regenerate (`retry`). Button disabled during deselect (`isRestoring`) + while generating.                                                                                                                                                 | `InventoryPageForm.tsx`, `plan-summary/useRestoreRecommendation.ts`, `inventoryFilters.utils.ts`, `plan-summary/AiSmartRecommendationPanel.tsx`                                                                                            |
| 5     | Reach Build chart sourced per-inventory data from two endpoints (recommendation-run `measure-summary` keyed by `runId`, and paginated `selected-inventory`).  | `measure-summary` (RECOMMENDATION_URL host, `runId`-keyed) and the paginated `selected-inventory` returned the same selected picks two different ways — redundant, and the `runId` branch was dead once selections live on the campaign.                                      | Replaced both with a single campaign-keyed `GET /campaign-inventory/{id}/selected-inventory/all` (unpaginated). `useReachCurve` drops `runId` and maps `performance.estimatedReach` → `reach`, `performance.estimatedCost` → `cpmBudget`. Removed dead `recommendationApi`/`MeasureSummaryItem`; added `SelectedInventoryPerformanceItem`.                                       | `services/inventory/inventorySlice.ts`, `store.ts`, `types/inventory.types.ts`, `plan-summary/useReachCurve.ts`, `InventoryPageForm.tsx`, `plan-summary/__tests__/useReachCurve.test.ts`, `inventory/__tests__/InventoryPageForm.test.tsx` |
| 6     | `yarn build`/`tsc` broken after the Step-4 merge.                                                                                                             | The redesign (`e4c690f`) deleted `smart-suggestion-inventory/InventoryViewToggle.tsx`, but the merge re-added prod's orphaned `InventorySmartSuggestionList.tsx` + `InventoryDefaultSmartSuggestion.tsx`, which import it. Nothing else imports those two files.              | Deleted both orphaned files (dead code the redesign had already superseded). `tsc` clean.                                                                                                                                                                                                                                                                                        | `smart-suggestion-inventory/InventorySmartSuggestionList.tsx` (removed), `smart-suggestion-inventory/InventoryDefaultSmartSuggestion.tsx` (removed)                                                                                        |
| 7     | Reach Build chart blank when `/reach-saturation-curve` returns bare `NaN`/`Infinity` tokens (invalid JSON).                                                   | The FREQUENCY_URL axios instance (silentJSONParsing) can't parse bare `NaN`/`Infinity`, so it left the body as a raw string. `transformResponse` then indexed a string → `overallReach` fell back to `[]` → empty chart, no error.                                            | Added exported `parseReachSaturationResponse()` used by `getReachSaturationCurve` `transformResponse`: when the body is an unparsed string, replace `NaN`/`Infinity`/`-Infinity` → `null`, then `JSON.parse`. Already-parsed payloads pass through untouched. (Bad tokens only appear in per-inventory `saturatedReach`, which the chart doesn't read.)                          | `services/inventory/inventorySlice.ts`, `services/inventory/__tests__/parseReachSaturationResponse.test.ts`                                                                                                                                |
| 8     | `/generate` must send `forceRegenerate=true` once — only on the "Restore AI recommendation" click, and only on the first call (not the status-poll calls).    | Restore reused `retry`, which is shared with the failed-state Retry button; and the FE re-calls `/generate` on every poll until `COMPLETED`, so a naive flag would repeat on every poll and also fire on plain Retry.                                                         | Added `forceRegenerate?` to the `generateInventoryRecommendation` query (appends `?forceRegenerate=true`). `useRecommendationForecast` gained a dedicated `regenerateFromRestore` (sets the flag on the run's FIRST poll only, via a local var consumed on first `poll()`); `retry` stays flag-free. Restore now calls `regenerateFromRestore`; failed-Retry still uses `retry`. | `services/inventory/inventorySlice.ts`, `plan-summary/useRecommendationForecast.ts`, `InventoryPageForm.tsx`, `plan-summary/__tests__/useRecommendationForecast.test.tsx`, `inventory/__tests__/InventoryPageForm.test.tsx`                |

## BUG-001 — Stale venue types persist after changing media channel (edit mode)

**Date:** 2026-06-25

**Reported:** Create a campaign with Classic OOH media channel and Transit venue
types. Edit the campaign, change media channel to Digital. On the Targeting →
Inventory Types tab, the Classic venue types are still shown as selected but
disabled.

**Root cause:** `src/pages/campaigns/TargetingForm.tsx` stores venue types in two
fields (`venueTypes.digitalOoh`, `venueTypes.classicOoh`). Each `MultiSelect` is
only `disabled={!selectedChannels.includes(...)}` — the stored values are never
cleared. The form initialization effect runs once and reloads the persisted
(stale) selections from `campaignData.targeting.venueTypes`. No effect
reconciled venue types when the selected media channels changed.

**Fix:** Added a reconciliation `useEffect` in `TargetingForm.tsx` that watches
`selectedChannels`. When a channel is no longer selected, it clears that
channel's venue types from form state and persists the change via autosave
(`handleTargetingFieldChange`). Behaviour confirmed with the user: clear (drop)
the deselected channel's venue types.

- Classic OOH deselected → `venueTypes.classicOoh` cleared
- Digital OOH deselected → `venueTypes.digitalOoh` cleared
- Both selected → no change

**Trade-off:** re-selecting a previously-deselected channel starts with empty
venue types (prior picks are gone) — accepted.

**Tests:** `src/pages/campaigns/__tests__/TargetingForm.test.tsx` — new
"venue type reconciliation on media channel change" describe block (3 cases).

**Files changed:** `src/pages/campaigns/TargetingForm.tsx`,
`src/pages/campaigns/__tests__/TargetingForm.test.tsx`

## BUG-002 — Campaign filter date range persists in popup after "Clear all"

**Date:** 2026-07-14

**Reported:** On the Campaigns list, apply a Period (date range) filter. Use the
page-level "Clear all" to remove active filters. Reopen the filter popup — the
Period date range is still shown, out of sync with the cleared page state.

**Root cause:** `src/pages/campaigns/common/CampaignFilterModal.tsx` kept its own
private copy of the filters in `localStorage` (`campaign_filters`), separate from
the `campaignsUI` Redux slice that the page actually reads. On open it merged
stored values with `initialValues` via `period: initial.period ?? storedFilters.period`.
"Clear all" dispatches `setFilters({ period: null, ... })`, so `initialValues.period`
arrives as `null` — and `null ?? storedFilters.period` fell back to the stale
stored period. Slice and modal disagreed.

**Fix:** Made the `campaignsUI` slice the single source of truth and removed the
modal's private storage.

- Deleted `CAMPAIGN_FILTERS_STORAGE_KEY`, `loadFiltersFromStorage`, `mergeFilters`,
  and the `storage` import.
- State now seeds/syncs purely from `initialValues` via a `toFilters()` normalizer
  (`?? []` / `?? null`), which coerces both absent (undefined) and explicitly
  cleared (null) fields to defaults.
- `handleApply` no longer writes `localStorage`; `handleClose` reverts to
  `initialValues`.

The slice persists its own state (`persistState`), so no functionality is lost.

**Trade-off:** existing users may have a stale `campaign_filters` key in
localStorage — now orphaned (never read/written), harmless. No cleanup added.

**Tests:** `src/pages/campaigns/common/__tests__/CampaignFilterModal.test.tsx` —
dropped the storage mock + "saves filters to storage" test (behaviour gone);
enhanced the DateRangePicker mock to expose `data-has-value`; added a
"period — mirrors initialValues" describe block (3 cases, incl. the reported bug).

**Verification:** CampaignFilterModal (21) + campaignsUISlice (66) +
CampaignsPage (19) = 106 passed; `tsc --noEmit` clean; eslint clean.

**Files changed:** `src/pages/campaigns/common/CampaignFilterModal.tsx`,
`src/pages/campaigns/common/__tests__/CampaignFilterModal.test.tsx`

## BUG-002 — AI recommendation skipped for a new campaign that shares another campaign's inputs

**Date:** 2026-07-13

**Reported:** Create multiple campaigns with the same targeting/budget/dates in
one browser session (no page reload). Only the first campaign calls `/generate`;
later ones show a "completed" recommendation with an empty plan.

**Root cause:** The recommendation cache key (`signature` in
`InventoryPageForm.tsx`, and the stored `RecommendationRun`) excluded
`campaignId`. Two campaigns with identical budget/startDate/endDate/goalType/
targeting/mediaOwnerIds produced a byte-identical signature, so
`useRecommendationForecast` reused the first campaign's cached run and skipped
generation. Redux `recommendationRun` is never cleared between campaigns
(`resetCampaignState` is never dispatched; the wrapper unmount cleanup omits it),
so the stale run survives across the create flow.

**Fix:** Added `campaignId` to the `signature` digest (object + `useMemo` deps).
Different campaign → different signature → cache miss → generation runs.

**Tests:** `src/pages/campaigns/inventory/__tests__/InventoryPageForm.test.tsx`
— new "recommendation cache key" test: a run cached under the pre-fix
(campaignId-less) signature is not reused; `/generate` runs for the campaign.

**Files changed:** `src/pages/campaigns/inventory/InventoryPageForm.tsx`,
`src/pages/campaigns/inventory/__tests__/InventoryPageForm.test.tsx`

## BUG-003 — Inventory location renders "Country, undefined" when state is missing

**Date:** 2026-07-13

**Reported:** In the InventoryDetailCard and the Edit-Manually map popup, an
inventory with no address and no state showed e.g. `US, undefined` (literal word
"undefined") or a dangling comma.

**Root cause:** Both spots used
`address || \`${country}, ${state}\``. A template literal stringifies an
`undefined`/empty `state` into the visible label.

**Fix:** Added `formatInventoryLocation()` to `inventory-display.utils.ts` —
returns `address` when present, else joins the truthy parts of
`[country, state]` with `", "` (empty string when nothing is available). Used in
both the detail card and the map popup.

**Note (not a bug):** Per request, SOT is now displayed as raw `plannedSot`
hours + `H` suffix everywhere (backend returns hours; SOT is a time metric, never
a %). The `plannedSot/totalSot*100` `%` transform was removed from
`useRecommendationForecast.ts`, `useManualInventorySelection.ts`, and
`Optimization.tsx`; the Step-4 forecast tile (`ForecastTiles.tsx`) and the
Optimization-step forecast (`CampaignForecast.tsx`, changed from a `%` Progress
bar to a `{plannedSot}H` text row) both follow the Schedule page format. Added
`inventories.forecast.sot` label (en/ja).

**Tests:** `src/utils/__tests__/inventory-display.utils.test.ts` — new
"formatInventoryLocation" describe block (address / country+state / missing state
/ empty). `ForecastTiles.test.tsx` SOT assertion updated to `H`.

**Files changed:** `src/utils/inventory-display.utils.ts`,
`src/components/common/InventoryDetailCard.tsx`,
`src/pages/campaigns/inventory/InventoryMapPanel.tsx`,
`src/utils/__tests__/inventory-display.utils.test.ts`,
`src/pages/campaigns/inventory/plan-summary/ForecastTiles.tsx`,
`src/pages/campaigns/inventory/plan-summary/useRecommendationForecast.ts`,
`src/pages/campaigns/inventory/manual-selection/useManualInventorySelection.ts`,
`src/pages/campaigns/inventory/plan-summary/__tests__/ForecastTiles.test.tsx`

## BUG-004 — Media-channel change did not force the AI recommendation to rebuild

**Date:** 2026-07-13

**Reported:** Changing the media channel selection in Step 1 (Campaign Details)
did not regenerate the AI recommendation — the backend returned the run it had
cached for the old channels.

**Root cause:** (1) `mediaChannels` was absent from the recommendation
`signature`, so the change didn't even invalidate the frontend cache. (2) Even
on a cache miss, `/generate` is called with `forceRegenerate=false`, so the
backend could return its own cached run. The `forceRegenerate=true` flag was only
sent by the Restore-AI-recommendation path.

**Fix:** Added `mediaChannels` to the `signature` (invalidates the FE cache) and
to the stored `RecommendationRun`. `useRecommendationForecast` now takes the
current `mediaChannels`; in `startRun`, when the cached run's channels differ
from the current selection (order-insensitive `sameChannels`), it sends
`forceRegenerate=true` on the run's first `/generate` call. Only a channel change
forces — budget/date changes keep `forceRegenerate=false` (unchanged behavior).

**Tests:**
`src/pages/campaigns/inventory/plan-summary/__tests__/useRecommendationForecast.test.tsx`
— "forces regenerate when the media channels changed" + "does not force …
unchanged". Existing `recommendationRun` literals updated with `mediaChannels`.

**Files changed:** `src/services/campaign/campaignSlice.ts`,
`src/pages/campaigns/inventory/plan-summary/useRecommendationForecast.ts`,
`src/pages/campaigns/inventory/InventoryPageForm.tsx`,
`src/pages/campaigns/inventory/plan-summary/__tests__/useRecommendationForecast.test.tsx`,
`src/pages/campaigns/inventory/__tests__/InventoryPageForm.test.tsx`,
`src/services/campaign/__tests__/campaignSlice.test.ts`

## FEAT-005 — Media-owner filter lists owned + child companies

**Date:** 2026-07-13

**Requested:** Manual Edit → Filter → Media Owner dropdown showed the country-wide
company list from the API even for a logged-in media owner. A media owner should
instead see their own company + its direct child companies.

**Implementation:** `ManualSelectionPage` builds `mediaOwnerStaticOptions`
(`TreeNode[]` = owned company + `current_company.childCompanies.items`, from
user-info) when `isMediaOwner`, and passes it to `InventoryFilterDrawer`. The
drawer already forwards `staticOptions` to `MediaOwnerDropdown`, which renders a
static MultiSelect (no API) when `staticOptions` is set. Non-media-owners pass
`undefined` → the existing country-scoped API flow is unchanged. Direct children
only (flat list from user-info; no nested fetch).

**Tests:** `ManualSelectionPage.test.tsx` — media owner passes owned + children as
static options; non-media-owner leaves them undefined.

**Files changed:** `src/pages/campaigns/inventory/manual-selection/ManualSelectionPage.tsx`,
`src/pages/campaigns/inventory/manual-selection/__tests__/ManualSelectionPage.test.tsx`

## BUG-006 — All-NaN reach-saturation curve rendered a flat chart instead of "no data"

**Date:** 2026-07-13

**Reported:** When `/reach-saturation-curve` returns `overallInventories.overallReach`
as all `NaN`, the Reach Build chart showed an empty flat line instead of the
no-data message.

**Root cause:** `parseReachSaturationResponse` (BUG-007) normalizes `NaN`→`null`,
so `overallReach` became `[null, null, …]` — a non-empty array. `useReachCurve`
set that as `overallReach`, and `PlanSummaryPanel` only shows the no-data state
when `data.length === 0`, so a full-length null series rendered the chart.

**Fix:** In `useReachCurve.load`, treat a series with no finite value
(`!overallRaw.some(Number.isFinite)`) as no data — set `overallReach`/`labels` to
`[]` so the empty-state message shows. Interspersed nulls with real numbers still
render (chart handles gaps).

**Tests:** `useReachCurve.test.ts` — all-null curve → empty `overallReach`/`labels`.

**Files changed:** `src/pages/campaigns/inventory/plan-summary/useReachCurve.ts`,
`src/pages/campaigns/inventory/plan-summary/__tests__/useReachCurve.test.ts`

## BUG-007 — Estimated Cost on inventory cards showed no currency code

**Date:** 2026-07-13

**Reported:** The inventory list card's "Estimated Cost" rendered the amount with
no currency (e.g. `1,123,723.65`).

**Root cause:** `InventoryListPanel` passed the card a `formatCurrency` closure
`(value) => formatCurrency(value || 0)` that dropped the currency argument, so
`formatCurrencyWithLocale` ran with an empty currency and emitted just the number.
(The card already passes `campaignCurrency` as the 2nd arg.)

**Fix:** Forward the currency: `(value, currency) => formatCurrency(value || 0,
currency)`. Now shows e.g. `JPY 1,123,723.65`, consistent with the card's CPM/eCPM
rows.

**Files changed:** `src/pages/campaigns/inventory/InventoryListPanel.tsx`

## BUG-008 — Inventory size not shown for recommendation, and only one size shown per card

**Date:** 2026-07-14

**Reported:** Recommendation (`/results`) inventory cards showed no size badge, and
cards with more than one size only displayed the first.

**Root cause:**

- `fromRecommendationItem` hardcoded `panels: []` and never mapped
  `inventoryDetails.sizes[]` (raw `/results` sizes live in a flat `sizes` array,
  unlike `/filter` + `/selected-inventory` which carry `detail.panels[].size`).
  The card reads size only from `panels[].size`, so recommendation sizes were lost.
- Both list cards (`InventoryDetailCard` = Edit Manual popup, `SelectedInventoryCard`
  = View popup) rendered only `panels[0].size`, dropping any additional sizes.

**Fix:**

- `fromRecommendationItem` now maps `inventoryDetails.sizes[]` → `panels[{size}]`,
  so both sources expose sizes through one uniform `panels[]` path.
- Added shared `getSortedPanelSizes(panels)` util (dedupe + order S→XXL, unknown
  last) and `MAX_VISIBLE_SIZES` constant in `inventory-display.utils.ts`.
- Both cards now render one chip: first two sizes inline + `+N` overflow with a
  hover tooltip listing all sizes. View popup uses short codes (`S, L +2`), Edit
  Manual uses translated labels (`inventorySize.*.label`).

**Note:** The View popup list is fed by `/selected-inventory`; `/results` there only
supplies smart scores (not sizes). Sizes in both popups come from the `panels[]` of
the list item's own source.

**Tests:** `inventory-display.utils.test.ts` (sizes→panels mapping, `getSortedPanelSizes`),
`InventoryDetailCard.test.tsx` (size chip single + overflow), `SelectedInventoryCard.test.tsx`
(size chip single + overflow + none).

**Files changed:** `src/utils/inventory-display.utils.ts`,
`src/components/common/InventoryDetailCard.tsx`,
`src/pages/campaigns/inventory/SelectedInventoryCard.tsx` (+ their test files)

## BUG-009 — Finalise Campaign button missing when editing a finalized campaign

**Date:** 2026-07-14

**Reported:** Editing a campaign showed neither "Save as Draft" nor "Finalise
Campaign" on the last step (step 5).

**Root cause:** `CampaignWrapper` gated the whole action block on
`campaignData?.status !== "PLANNED"`. An already-finalized campaign is `PLANNED`,
so both buttons were hidden on edit.

**Fix:** Show the action block on the last step regardless of status; render
"Save as Draft" only when status !== `PLANNED` (a finalized campaign shouldn't
revert to draft), and always render "Finalise Campaign". New campaigns / drafts
keep both buttons.

**Tests:** `CampaignWrapper.test.tsx` — DRAFT shows both; PLANNED shows Finalise,
hides Save as Draft.

**Files changed:** `src/pages/campaigns/CampaignWrapper.tsx`

## BUG-008 follow-ups (2026-07-14)

- Size chip given a color (`outline-mw-orange-warning-500`) in `InventoryDetailCard`,
  matching the View popup card.
- Manual-selection popup header: replaced the `X` close icon with a "Save" button
  (i18n key `inventories.manual.save`, en/ja).

**Files changed:** `src/components/common/InventoryDetailCard.tsx`,
`src/pages/campaigns/inventory/manual-selection/ManualSelectionPage.tsx`,
`src/assets/i18n/campaigns/{en,ja}.json`

## BUG-008 follow-ups (2026-07-14, cont.)

- Map view: applied the same size chip (short codes + `+N` overflow + hover
  tooltip) to the inventory map popup. The popup mounts via `createRoot` inside a
  `TolgeeProvider`, so the Tooltip's hover/portal lifecycle works.

**Files changed:** `src/pages/campaigns/inventory/InventoryMapPanel.tsx`

## BUG-010 — Step-4 plan-summary SOT tile showed redundant "out of {total} H"

**Date:** 2026-07-14

**Reported:** The SOT tile in the step-4 inventory plan summary read
"4433.00H out of 535990 H"; the total was not needed there.

**Fix:** `ForecastTiles` SOT tile now shows only planned SOT ("4433.00H"). The
Optimization step's Campaign Forecast panel (`CampaignForecast.tsx`) keeps the
"out of" format unchanged (per request).

**Tests:** `ForecastTiles.test.tsx` — SOT tile asserts "2.50H".

**Files changed:** `src/pages/campaigns/inventory/plan-summary/ForecastTiles.tsx`

## BUG-011 — Editing budget allocation did not re-run the inventory recommendation

**Date:** 2026-07-15

**Reported:** Changing the budget distribution (per-channel split) on the Budget &
Goal step did not trigger a fresh `/generate` recommendation run on the Inventory
step — the cached run was reused.

**Root cause:** `InventoryPageForm.tsx` builds a `signature` digest of the inputs
that affect the recommendation; a cached run whose signature matches is reused and
`/generate` is skipped. `budgetAllocation` was absent from the signature, so an
allocation-only edit never invalidated the cache.

**Fix:** Added `budgetAllocation` to the `signature` object and its `useMemo`
dependency array. An allocation change now changes the signature → cache miss →
`/generate` re-runs (with `forceRegenerate=false` — see BUG-008; the flag is not
set by an allocation change). Chosen "signature-only" approach; works only if the
backend recomputes for the new allocation on a non-forced generate.

**Tests:** `InventoryPageForm.test.tsx` — new case "does not reuse a cached run
when only the budgetAllocation changed".

**Files changed:** `src/pages/campaigns/inventory/InventoryPageForm.tsx`,
`src/pages/campaigns/inventory/__tests__/InventoryPageForm.test.tsx`

## BUG-012 — "Distribute evenly" enabled with a single channel

**Date:** 2026-07-15

**Reported:** In the budget distribution edit popup, the "Distribute evenly" button
was active even when only one channel was selected — where an even split is
meaningless (that channel always gets 100%).

**Fix:** `BudgetAndGoalPage.tsx` — the button is `disabled` when
`selectedChannels.length <= 1`, with disabled styling
(`disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent`).

**Files changed:** `src/pages/campaigns/BudgetAndGoalPage.tsx`

## BUG-013 — Download action shown in campaign-list action menu

**Date:** 2026-07-15

**Reported:** The campaign list page action menu showed a "Download" option that
should be removed there.

**Fix:** Passed `hideNavigation={["download"]}` to `CampaignActionsDropdownContent`
at both campaign-list usages (table + card views). The dropdown is shared, so this
scopes the removal to the list only — detail, media-plan and price-management pages
keep Download.

**Files changed:** `src/pages/campaigns/components/CampaignsListView.tsx`,
`src/pages/campaigns/common/CampaignCard.tsx`

## BUG-014 — Brand creation IAB hierarchy fails to load when taxonomy v3 absent

**Date:** 2026-07-16

**Reported:** `BrandCreationForm` resolved the IAB taxonomy version by matching
`"3.1"` / `version.startsWith("3")` / `name.includes("3")`. If the
`/metadata/iab-taxonomy-versions` response has no v3 (only 2.x / 1.0), the match
returned `undefined`, the hierarchy query was skipped, and the category tree was
empty. The `name.includes("3")` fallback could also wrongly pick a v2 whose name
contained "3".

**Fix:** Extracted `resolveTaxonomyVersionId()` — uses exact version "3.1" when
present, otherwise falls back to `pickBestTaxonomyVersionId()`, which sorts
versions numerically (major\*1000+minor) and returns the highest available id
(3.x → 2.x → 1.x). `BrandCreationForm` now uses this util.

**Files changed:** `src/utils/iab-taxonomy.utils.ts` (new),
`src/utils/__tests__/iab-taxonomy.utils.test.ts` (new),
`src/pages/campaigns/BrandCreationForm.tsx`

## BUG-015 — Brand creation category picker empty when hierarchy API fails

**Date:** 2026-07-16

**Reported:** `BrandCreationForm` category tree depends on
`GET /metadata/iab-taxonomy-versions/{id}/hierarchy`. If that call errors or
returns an empty body, the picker rendered with no categories and brand creation
was blocked.

**Fix:** Added hardcoded IAB v3.1 taxonomy
(`src/constants/iab-taxonomy.constants.ts`, `IAB_TAXONOMY_HIERARCHY_FALLBACK`,
37 tier-1 categories / 704 nodes, matches `IabTaxonomyNode[]`). `BrandCreationForm`
now falls back to it when the hierarchy query errors, or resolves empty once
fetching has settled. Live API data still takes precedence when present.

**Files changed:** `src/constants/iab-taxonomy.constants.ts` (new),
`src/pages/campaigns/BrandCreationForm.tsx`,
`src/pages/campaigns/__tests__/BrandCreationForm.test.tsx`

**Follow-up:** Since both taxonomy calls now self-heal via fallback, their
failures should not toast. Added optional `suppressErrorToast?: boolean` to the
`axiosBaseQuery` request arg — when true, all error paths (network/CORS/timeout/
server/general) skip the toast but still return the error object (RTK Query
`isError` unaffected). Flag defaults false, so all other endpoints are unchanged.
Set on both `getIabTaxonomyVersions` and `getIabTaxonomyHierarchy`.

**Additional files:** `src/api/axiosBaseQuery.ts`,
`src/api/__tests__/axiosBaseQuery.suppressErrorToast.test.ts` (new),
`src/services/brand/brandSlice.ts`

## BUG-016 — Deleted inventory still shown in Step-5 "Optimise Manually" popup

**Date:** 2026-07-16

**Reported:** In Step 5 (Optimization), deleting an inventory (Step 4, or the
Step-5 inline list) still left it visible in the "Optimise Manually" popup's
Selected Inventories list.

**Root cause:** `OptimizeManuallyDrawer` is permanently mounted (only its
`isOpen` prop toggles) and its list is a one-time snapshot, not a live query.
The inner `useSelectedInventoryList` fetches once at mount into local `useState`
(`selectedItems`), then the drawer copies that into its own `inventoriesSelectedList`.
The hook only (re)fetches when its `enabled`/`campaignId` deps change, but
`enabled` was `(campaignId && isOpen) || !!campaignState.campaignData` — and
`campaignData` is always truthy while the drawer is rendered, so `enabled` was
pinned `true`, never flipped on close, `hasInitialLoadRef` never reset, and the
fetch fired exactly once. Deleting an inventory (`selectInventory`/
`bulkSelectInventory` `DESELECT`, server-only, no `invalidatesTags`) never
touched the frozen snapshot, so it kept showing. The Step-5 inline list is
correct because `handleInventoryRemove` calls `refetch()` on a _separate_
hook instance; the popup's instance is never told to refetch.

**Fix:** Gate the hook on `isOpen` only — `enabled: !!campaignId && isOpen`. Now
`enabled` flips to `false` on close (hook `reset()` runs → `hasInitialLoadRef`
clears) and back to `true` on open, so the hook refetches on every open. The
lazy query trigger uses `preferCacheValue=false`, so the reopen fetch always
hits the server and reflects deletions. (No `invalidatesTags` change needed —
the popup consumes a fresh lazy fetch, not a cache subscription.)

**Tests:** `OptimizeManuallyDrawer.test.tsx` — new cases assert the hook's
`enabled` follows `isOpen` (false when closed, true when open), guarding the
snapshot regression.

**Follow-up (same day):** the `enabled` fix made the selected-inventory fetch
resolve on the reopen render, which surfaced a latent forward-reference: the
on-open load effect (top of the component) called `fecthInventorySchedule` /
`handleSelectAllDays` / `handleSelectAllGrid`, all `const` arrows declared ~500
lines below → `ReferenceError: Cannot access 'fecthInventorySchedule' before
initialization`. Fixed by routing those follow-up calls through a
`runInitialScheduleSetupRef` (assigned after the handlers are defined) instead of
referencing them directly — the effect stays at the top so hook order is
unchanged.

**Files changed:** `src/pages/campaigns/optimization/OptimizeManuallyDrawer.tsx`,
`src/pages/campaigns/optimization/__tests__/OptimizeManuallyDrawer.test.tsx`

## BUG-017 — Login redirects to last visited page instead of dashboard

**Date:** 2026-07-17

**Reported:** Logout while on the Profile page, then log in again — the app
redirects back to the Profile page. Requirement: after login the user must
always land on the dashboard.

**Root cause:** a deep-link restore mechanism spanning three files. On logout,
`ProtectedRoute.tsx` redirects to `/login` with `state={{ from: location }}`
(the page the user was on). `LoginPage.tsx` persisted that path to
`sessionStorage["post_login_redirect"]` before the OAuth full-page redirect
(with a fallback to `localStorage["redirectURL"]`, written by
`clearAuthAndRedirect()` in `axiosInstance.ts` on token expiry).
`OAuthCallbackPage.tsx` then read the key after token exchange and navigated
to it instead of `/dashboard`.

**Fix:** removed the restore mechanism entirely — login always lands on
`/dashboard`:

- `LoginPage.tsx` — dropped the `location.state.from` / `redirectURL`
  persistence; already-authenticated users navigate straight to `/dashboard`.
- `OAuthCallbackPage.tsx` — always navigates to `/dashboard`; still removes
  the stale `post_login_redirect` / `redirectURL` keys left by older versions.
- `axiosInstance.ts` — `clearAuthAndRedirect()` no longer writes
  `redirectURL` (nothing consumes it anymore).

`ProtectedRoute.tsx` still passes `state={{ from }}`, but it is now inert —
nothing reads it.

**Tests:** `LoginPage.test.tsx` — "navigates to /dashboard even when location
state from is present" (was asserting the old restore behaviour) and a new
case asserting no `post_login_redirect` key is written before the OAuth
redirect. `OAuthCallbackPage.test.tsx` already asserted `/dashboard`.

**Files changed:** `src/pages/auth/LoginPage.tsx`,
`src/pages/auth/OAuthCallbackPage.tsx`, `src/api/axiosInstance.ts`,
`src/pages/auth/__tests__/LoginPage.test.tsx`

## BUG-018 — venueTypes filter never matched on the client-side /selected-inventory list

**Date:** 2026-07-21

**Context:** Manual-edit inventory list filters the `/selected-inventory`
response client-side (that endpoint ignores the inventory filters), while
`/filter` filters server-side. All filters except latitude/longitude are applied
on the client.

**Reported:** Applying a venue-type filter hid every selected item (none
matched), so the venueTypes filter was effectively broken for the selected list.

**Root cause:** the filter stores **hierarchical venue slugs**
(`"outdoor-billboards-roadside"`) while the `/selected-inventory` response
carries **display-name paths** (`venueType: ["Outdoor","Billboards"]`). The
first implementation compared the two as plain strings
(`includesCI(filters.venueTypes, name)`), which can never match. The `"-"`
separator is also ambiguous — a single name like `"Office Buildings"` slugifies
to `"office-buildings"` — so the slug cannot be naively split.

**Fix:** resolve both sides through the `/venues` tree.
`buildVenueSlugToNamePath()` maps each node's slug → its root→node display-name
path. An item matches a selected slug when that slug's name-path is a **prefix**
of the item's `venueType` name path (item is that node or a descendant), compared
case-insensitively with consecutive-duplicate names collapsed on both sides.
`filterSelectedInventoryClientSide` takes an options object
(`{ searchOverride, venueSlugToNamePath }`); when the slug map is absent the
venueTypes filter is skipped (never hide for a filter we cannot evaluate).
`InventoryListPanel` builds the map from the venues query and passes it in.

**Tests:** `inventory.utils.test.ts` — `buildVenueSlugToNamePath` + a
`venueTypes (slug → name-path)` suite (type-level match + descendants, leaf
match, item==node, subtype-only excludes broader item, match-ANY, duplicate-name
tolerance, skip when map absent). All 94 tests pass.

**Files changed:** `src/utils/inventory.utils.ts`,
`src/pages/campaigns/inventory/InventoryListPanel.tsx`,
`src/types/inventory.types.ts`,
`src/utils/__tests__/inventory.utils.test.ts`,
`src/pages/campaigns/inventory/__tests__/InventoryListPanel.test.tsx`

## BUG-019 — "No inventory items found" flashed while /filter was still loading

**Date:** 2026-07-22

**Reported:** In the edit/manual filter list, searching for an inventory whose
match is not in the `/selected-inventory` response but is served by a slow
`/filter` call showed the "No inventory items found" empty state at the same
time as the loading skeleton — looked broken (empty message + loading together).

**Root cause:** `InventoryListPanel` two-phase load (`selectedFirst`) runs
`/selected-inventory` first (`setIsLoading(true)` → `false` in its `finally`),
then chains `loadFilterPage(0, append=true)`, which sets `isLoadingMore` — NOT
`isLoading`. The empty-state branch only guarded `!isLoading`, so while the
`/filter` phase was in flight (`isLoading=false`, `isLoadingMore=true`) with an
empty merged list, the empty message rendered next to the load-more skeleton.

**Fix:** the empty-state branch now also guards `!isLoadingMore`, so
"No inventory items found" stays hidden until the `/filter` phase finishes.

**Tests:** `InventoryListPanel.test.tsx` — added a regression test: with
`/selected-inventory` empty and `/filter` still pending, the empty message is
absent and the load-more skeleton is shown. All 29 tests pass.

**Files changed:** `src/pages/campaigns/inventory/InventoryListPanel.tsx`,
`src/pages/campaigns/inventory/__tests__/InventoryListPanel.test.tsx`

## BUG-020 (DEF-PLN-001) — Campaigns list header subtitle grammar error

**Date:** 2026-07-24

**Reported:** On `/campaigns`, the header subtitle read "Manage your campaigns
ease and full efficiency" — grammatically wrong, missing "with". Visible on
every load.

**Root cause:** `campaigns.description` i18n key had a typo in the English
string.

**Fix:** `src/assets/i18n/campaigns/en.json` — "Manage your campaigns **with**
ease and full efficiency". `ja.json` unchanged (its phrasing is already
grammatical).

**Files changed:** `src/assets/i18n/campaigns/en.json`

## BUG-021 (DEF-PLN-002) — Dashboard Campaigns Performance column header not pluralized

**Date:** 2026-07-24

**Reported:** Dashboard → Campaigns Performance table column header read
"Impression" (singular), inconsistent with "Est. Impressions" used elsewhere.

**Root cause:** `campaignPerformance.columns.impression` i18n key had the
singular English string.

**Fix:** `src/assets/i18n/dashboard/en.json` — "Impression" → "Impressions"
(matches staging). `ja.json` unchanged (Japanese does not pluralize).

**Files changed:** `src/assets/i18n/dashboard/en.json`

## BUG-022 (DEF-PLN-003) — Dashboard "All Campaign" dropdown label not pluralized

**Date:** 2026-07-24

**Reported:** On `/dashboard`, the Campaign Type filter and Budget Tracker scope
selector both showed "All Campaign" (singular). Two places, same label.

**Root cause:** `campaignTypeDropdown.all-campaign` i18n key had the singular
string. Both dropdowns render the same `CampaignTypeDropdown` key.

**Fix:** `src/assets/i18n/dashboard/en.json` — "All Campaign" → "All Campaigns"
(matches staging). Enum value `"all-campaign"` unchanged. Test mock in
`CampaignTypeDropdown.test.tsx` updated to match. `ja.json` unchanged.

**Files changed:** `src/assets/i18n/dashboard/en.json`,
`src/components/dashboard/__tests__/CampaignTypeDropdown.test.tsx`

## BUG-023 (DEF-PLN-004) — Inconsistent en-US/en-GB spelling ("Customise" vs "Customize")

**Date:** 2026-07-24

**Reported:** App-wide, "Customize columns" (US) coexisted with "Customise Table
Elements" (GB, Dashboard column-customization drawer). Need one convention.

**Root cause:** `campaignPlanner.columnCustomization.title` used GB "Customise";
every other UI string (dashboard, price, campaigns) uses US "Customize".

**Fix:** standardize on US spelling (matches staging). `campaigns/en.json` —
"Customise Table Elements" → "Customize Table Elements". All other UI strings
already US. Left `scheduleDefaults.ts:103` "customised" (code comment, not
user-visible; staging left it too).

**Files changed:** `src/assets/i18n/campaigns/en.json`

## BUG-024 (DEF-PLN-005) — Campaign History audit entries show raw/unformatted data

**Date:** 2026-07-24

**Reported:** Campaign View → Campaign History tab. Audit messages exposed
developer-oriented fragments instead of natural language: raw map syntax
`Budget Allocation:{digital=100.0}`, raw enum `Client Type:DIRECT_ADVERTISER`,
colons with no space `Dates:08/07/2026`.

**Root cause:** the history cell rendered `message` verbatim from the audit
endpoint; no client-side formatting.

**Fix:** added `formatAuditMessage()` util (`src/utils/auditMessage.utils.ts`,
matches staging) — conservatively rewrites map syntax → `Key: value`,
SCREAMING_SNAKE enums → Title Case (single all-caps tokens like CPM/BRL left
untouched), inserts a space after colons. Ambiguous dates are only spaced, not
reordered (can't disambiguate DD/MM vs MM/DD client-side). `CampaignHistory.tsx`
wraps the rendered message in `formatAuditMessage()`.

**Tests:** `src/utils/__tests__/auditMessage.utils.test.ts` — 8 tests, all pass.

**Files changed:** `src/utils/auditMessage.utils.ts` (new),
`src/utils/__tests__/auditMessage.utils.test.ts` (new),
`src/pages/campaigns/campaign-view/CampaignHistory.tsx`

## BUG-025 (DEF-PLN-006) — Campaign wizard "Next" button label one step behind

**Date:** 2026-07-24

**Reported:** New Campaign wizard → Step 3 Targeting → Demographics sub-step. The
"Next" button read "Next: Demographics" while already on Demographics; should
preview the upcoming step ("Next: Geo-fencing"). Self-corrected on the next
sub-step.

**Root cause:** `CampaignWrapper` read the label off the step form ref during
render (`currentStepFormRef.current?.getNextLabel?.()`). The ref only reattaches
after commit, and React 18 batches the step change with the `isLoading` reset
into one render, so the ref still returned the previous step's label.

**Fix:** capture the label via a callback ref (`setStepFormRef`) into `nextLabel`
state, only setState when the value changes (guards infinite loop for step forms
with unstable imperative-handle deps, and makes Targeting sub-step tab changes
reactive). Button reads `nextLabel`. Exact match to staging's DEF-PLN-006 change.

**Note:** staging's CampaignWrapper commit also bundled an unrelated
company-switch-detection feature — deliberately NOT copied (out of scope for this
defect).

**Files changed:** `src/pages/campaigns/CampaignWrapper.tsx`

## BUG-026 (DEF-PLN-007) — Budgeting "Market Insight" card missing country name

**Date:** 2026-07-24

**Reported:** New Campaign wizard → Step 2 Budgeting → "Market Insight" card
showed only the currency, e.g. "Market / (AFN)" instead of "Afghanistan (AFN)".
Reproducible for any Target Country selected in Step 2.

**Root cause:** the card rendered `selectedCountryData?.countryName`, which is
undefined until the market-access lookup resolves the selected country, leaving
just the currency code.

**Fix:** fall back to the form's `watchedCountry` (the selected country name)
when `selectedCountryData?.countryName` is empty:
`{selectedCountryData?.countryName || watchedCountry} ({getCurrentCurrency()?.code})`.
Exact match to staging's DEF-PLN-007 line.

**Note:** staging's `BudgetAndGoalPage.tsx` diff also contained unrelated
budget-distribution changes (single-channel 100%, reset/manual-toggle gating,
CHANNEL_CONFIG trim) — deliberately NOT copied (out of scope for this defect).

**Files changed:** `src/pages/campaigns/BudgetAndGoalPage.tsx`

## BUG-027 (DEF-PLN-009) — Campaign grid card goal-type pill inconsistent casing ("N/a")

**Date:** 2026-07-24

**Reported:** Campaigns → Grid view cards. The goal-type pill near Brand rendered
with inconsistent casing — "N/a" (lowercase a) on cards with no goal type, where
it should read "N/A".

**Root cause:** the pill computed its label as
`goalType.charAt(0) + goalType.slice(1).toLowerCase()`. For the fallback value
"N/A" this produced "N" + "/a" = "N/a".

**Fix:** normalize the goal type via `normalizeGoalType()` and render the
translated `campaignsList.goalTypes.{KEY}` label for known types
(IMPRESSIONS/REACH/SOV/ADPLAYS); unknown values (including the "N/A" fallback)
pass through unchanged. Added `normalizeGoalType` import in `CampaignCard.tsx`.
Exact match to staging's DEF-PLN-009 change. Deps (`normalizeGoalType`,
`campaignsList.goalTypes.*` keys) already present on the branch.

**Files changed:** `src/pages/campaigns/common/CampaignCard.tsx`

## BUG-028 (DEF-PLN-010) — Inventory details panel field-value casing inconsistent

**Date:** 2026-07-24

**Reported:** Campaign View → Inventory side panel → Details tab. Field values had
inconsistent casing vs siblings — "Operation Mode: loop" (lowercase) next to
"Type: Digital" (capitalized). Size value also lowercase; should be capitalized.

**Root cause:** the Size and Operation Mode (`digitalFields.bookingMode`) values
were rendered raw from the API, which returns them lowercase.

**Fix:** added `capitalizeFirst()` helper to `stringManipulation.utils.ts`
(uppercases first char, leaves the rest untouched) and wrapped both
`inventoryDetails?.size` and `inventoryDetails?.digitalFields?.bookingMode` in
`InventoryDetailsDrawer.tsx`. Exact match to staging's DEF-PLN-010 change.

**Tests:** `stringManipulation.test.ts` — added `capitalizeFirst` suite
(4 cases); 16 tests total pass.

**Files changed:** `src/utils/stringManipulation.utils.ts`,
`src/utils/__tests__/stringManipulation.test.ts`,
`src/pages/campaigns/inventory/InventoryDetailsDrawer.tsx`

## BUG-029 (DEF-PLN-011) — Performance tab CPM/eCPM missing currency symbol

**Date:** 2026-07-24

**Reported:** Campaign View → Performance tab. Avg CPM Cost and eCPM rendered with
no currency ("59.08" / "0.00") while all other monetary fields showed the
campaign currency ("BRL 59.08").

**Root cause:** `PerformanceMetrics` formats CPM/eCPM via
`formatCurrencyWithLocale(value, campaignCurrency)`, but `CampaignDetails`
(the /view-campaign caller) did not pass the `campaignCurrency` prop, so no
symbol was rendered.

**Fix:** pass `campaignCurrency={campaignData.data?.currency}` to
`PerformanceMetrics` in `CampaignDetails.tsx`. Exact match to staging's
DEF-PLN-011 change. (Staging's `CampaignDetails` commit also bundled the
DEF-PLN-012 cost-split-total fix — deferred to that defect, not copied here.)

**Files changed:** `src/pages/campaigns/campaign-view/CampaignDetails.tsx`

## BUG-031 (DEF-PLN-015) — Performance tab Share of Time decimal formatting inconsistent

**Date:** 2026-07-24

**Reported:** Campaign View → Performance tab. Share of Time rendered
inconsistently — "27125.00 H out of 27125 H". Both the planned and total values
should carry decimals.

**Root cause:** the SOT value format interpolated `planned` via `.toFixed(2)` but
`total` (`forecastData.totalSot`) was passed raw, so only one side had decimals.

**Fix:** `total: forecastData.totalSot?.toFixed(2)` in `PerformanceMetrics.tsx`.
Exact match to staging's DEF-PLN-015 change.

**Files changed:** `src/pages/campaigns/campaign-view/PerformanceMetrics.tsx`

## BUG-030 (DEF-PLN-012) — Cost Split header chip shows empty total ("Total : -")

**Date:** 2026-07-24

**Reported:** Campaign View → Cost Split section. The header chip showed
"Total : -" instead of the computed total of the cost-split cards (both
campaigns viewed).

**Root cause:** the chip used `campaignData.data?.costBreakdown?.totalCost`,
which is not always populated by the backend; when absent it fell back to "--".

**Fix:** compute `costSplitTotal` (memoized) by summing `totalAmount` across the
cost-split cards (`costSplitData.data`), and use it as the fallback:
`formatCurrencyWithLocale(costBreakdown?.totalCost ?? costSplitTotal, currency)`.
Exact match to staging's DEF-PLN-012 change. Deps (`costSplitData`,
`CostSplitByCampaignData`) already present. `CampaignDetails.tsx` now
byte-identical to staging (this + DEF-PLN-011).

**Files changed:** `src/pages/campaigns/campaign-view/CampaignDetails.tsx`

## BUG-032 — Price management refresh sorted by a non-existent `srNo` field

**Date:** 2026-07-28

**Reported:** Found while reworking the price management table UI. The "Refresh"
action in the campaign actions dropdown re-fetched schedule prices with
`sortBy: "srNo"` whenever no sort was active.

**Root cause:** `CampaignPriceManagement.tsx` had two different fallbacks for
`sortBy`: `loadPriceData` used `"name"`, but the `onRefresh` handler passed to
`CampaignActionsDropdownContent` used `"srNo"`. `srNo` was a client-side row
index rendered in a column, never a sortable backend field — so a refresh with
no active sort asked the API to sort by a field it does not know, and the
returned order could differ from the order shown before the refresh. The
redesign removed the `srNo` column entirely, leaving the string with no
referent at all.

**Fix:** `onRefresh` now falls back to `"name"`, matching `loadPriceData`.

**Tests:** existing `CampaignPriceManagement.test.tsx` suite (24 tests) passes;
full suite 4642 tests pass.

**Files changed:** `src/pages/campaigns/price-management/CampaignPriceManagement.tsx`
