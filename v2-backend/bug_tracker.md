# Bug Tracker

Log of bugs resolved in MW-Planner. Newest first.

---

## BUG-002 — Stale reach/impressions saved on schedule edit when only spots changed

- **Status:** Fixed
- **Date:** 2026-06-22
- **Area:** `PUT /api/v1/campaign-inventory/{campaignId}/schedules/{scheduleId}` (editScheduleById)
- **File:** `CampaignInventorySchedulesService.java` (editScheduleById, `needsReachAndFrequencyUpdate`)

### Symptom
Editing a schedule's `spotsPerLoop` (1→2, doubling `spotsPerHour` 10→20) saved **stale**
`impressions=114000`/`reach=13403` (the pre-edit values). A forecast moments later recomputed the
correct `228000`/`26806`. The persisted `basePrice` (1710) was likewise derived from the stale
impressions instead of the correct 3420.

### Root cause
The re-enrich trigger only watched `startDate` / `endDate` / `bookingMatrix`. But `spotsPerHour`
(and `spotsPerLoop`) also drive the Measure R&F payload. Changing only spots left the trigger
false → `enrichSchedulesWithReachAndFrequency` skipped → old impressions/reach kept → basePrice
recomputed from stale impressions. Confirmed by absence of any `R&F (bySites)` payload log during
the edit.

### Fix
Added `spotsPerHour` and `spotsPerLoop` to `needsReachAndFrequencyUpdate` (compares old
`existingSchedule` value against the newly computed locals, before the setters run). Now any change
to spots re-fetches R&F before save. Null legacy values compare unequal → safely re-trigger.

---

## ENH-002 — Omit hourly dayparts for full-day DAYPART schedules in Measure payload

- **Status:** Done
- **Date:** 2026-06-20
- **Area:** Measure (reach & frequency) request payload
- **File:** `MwMeasureService.java` (`buildInventoryDTOs`)

### Change
For a DAYPART schedule where **every booked date covers all 24 hours (0..23)**, the hourly
dayparts carry no information beyond the date range. So it now behaves exactly like a LOOP
schedule:
- schedule dates == campaign dates → **no dayparts** sent
- schedule dates differ → **date-only** dayparts (dates, no hours)

Otherwise (partial-hour dates) → unchanged: full hourly dayparts via
`convertBookingMatrixToDayparts`.

Shared the LOOP date-handling logic via new helpers `schedulesMatchCampaign`,
`buildDateOnlyDayparts`, `allDatesFullDay` (LOOP block refactored to reuse them — no duplication).

### Scope / impact
- Affects every caller of `buildInventoryDTOs`: the **selected** forecast path and the schedule
  **save/enrichment** path. Reach/impression numbers for full-day DAYPART inventories now come
  from a no-daypart (or date-only) request instead of a 24×N hourly payload.
- **Non-selected preview** path is unaffected — it uses `getReachFrequencyWithoutDayparts`, which
  never sends dayparts.
- `allDatesFullDay` returns false for an empty matrix (nothing booked ≠ full coverage).

---

## ENH-001 — Return reach / frequency / impressions in /filter performance

- **Status:** Done
- **Date:** 2026-06-20
- **Area:** `/api/v1/campaign-inventory/{campaignId}/filter`
- **Files:** `InventoryService.java` (`calculatePerformanceMetrics`),
  `CampaignInventoryFilterResponseDTO.java` (`Performance`)

### Change
Added `estimatedImpression`, `estimatedReach`, `estimatedFrequency` to `Performance`.

- **Selected rows:** read from the forecast already computed
  (`CampaignForecastDTO.getEstimatedImpression/Reach/Frequency`) — no extra API call.
- **Non-selected, CPM route:** the Measure call already made for cost now also surfaces
  reach/frequency (helper `getImpressionsFromMeasureApiWithoutDayparts` widened to return the full
  `MeasureReachFrequencyResponseDTO`, renamed `getReachFrequencyWithoutDayparts`).
- **Non-selected, spot route:** left null by design — spot pricing computes cost locally and the
  Measure API returns one aggregate per request (not per-inventory), so a page-batch cannot yield
  per-row values, and per-row calls were declined for latency.

Cost-estimation logic consolidated into `estimatePreviewMetrics` returning a `PreviewMetrics`
record (cost + R&F), replacing the earlier `estimateCpmCost` helper. `estimateSpotCost` retained.

### API contract note
`mwMeasureService.getReachAndFrequency` returns a **single aggregate** reach/freq/impressions for
the set of inventories in the request — NOT a per-inventory breakdown. Per-row metrics therefore
require one inventory per request.

---

## BUG-001 — Preview cost missing for non-selected inventories (cost jumps after selection)

- **Status:** Fixed
- **Date:** 2026-06-20
- **Area:** `/api/v1/campaign-inventory/{campaignId}/filter`
- **File:** `src/main/java/com/mw/planner/service/InventoryService.java` (`calculatePerformanceMetrics`)

### Symptom
Same inventory, same campaign: `performance.estimatedCost` was **absent** in the filter
response while the inventory was un-selected, then appeared as a real value (e.g. ¥148.8) the
moment it was selected. Preview cost did not match post-selection cost.

### Root cause
Campaign had no `goals` → `goalType == null`.

- **Non-selected path** (`calculatePerformanceMetrics`, `schedule == null` branch) handled only
  `IMPRESSIONS/REACH` and `SOV/ADPLAYS`. No `else` → `goalType == null` left `estimatedCost`
  null.
- **Schedule/selected path** (`CampaignInventorySchedulesService.calculateScheduleBasePriceForSchedule`)
  had a **default branch**: spot pricing if available, else CPM × impressions → produced
  `0.02 × 7440 = 148.8`.

The two pricing code paths diverged for the no-goal-type case.

### Fix
Gave the non-selected branch the same default fallback as the schedule path:
- default (`null` / `OTHER` / `ATTRIBUTION`): prefer spot (`spot × adPlays`, no API call), else
  CPM × impressions.
Extracted `estimateSpotCost` and `estimateCpmCost` helpers to share the cost math between the
goal-type branches (no duplication).

### Behavior preserved
- `IMPRESSIONS/REACH` and `SOV/ADPLAYS` branches produce identical values to before.
- `perDayCost` computed once after `estimatedCost`; stays null when cost is null.
- Selected path (`schedule != null`) untouched.

### Known trade-off
For **cpm-only** inventories on a goalless campaign, the default fallback triggers one Measure
API call per row per filter page (spot-priced inventories incur no extra call). Acceptable for
now; revisit if filter latency regresses.
