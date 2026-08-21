# Media Plan Redesign — Bug Tracker

Only fixed bugs are recorded here.

## 2026-07-24 — Inventory Mix omitted targeted-but-unbooked channels

**Symptom:** Campaign had 2 media channels selected, but inventory was booked
only under Digital. Inventory Mix showed just 1 channel (Digital) — the second
targeted channel was missing.

**Cause:** Inventory Mix is driven by the cost-split-by-CHANNEL API, which only
returns channels that have actual booked inventory/cost. A targeted channel with
no inventory produces no cost-split row.

**Fix:** `MediaPlanInventoryMix` now merges the campaign's selected
`mediaChannels` (from campaign detail) with the cost-split. Channels targeted but
not present in the split render as zero rows (0 inventories / 0 impr / 0 cost /
0%). `mediaChannels` exposed via `useMediaPlanData`. Channel codes mapped to
display labels (`DIGITAL_OOH` → "Digital OOH", `CLASSIC_OOH` → "Classic OOH").

**Files:** `MediaPlanInventoryMix.tsx`, `useMediaPlanData.ts`,
`usePublicMediaPlanData.ts`, `ViewMediaPlanPage.tsx`, i18n en/ja.
**Tests:** merge + no-duplicate cases in `MediaPlanInventoryMix.test.tsx`.
