# Campaign Creation Wizard — Field Map (As-Is)

**Active route:** `/new-campaign` → `client/src/pages/new-campaign-page.tsx`
**Legacy route (do not modify):** `/create-campaign` → `campaign-creation-page.tsx` (uses old `campaign-details.tsx`)

The active wizard is composed of six step components, each owning its own Zod schema and exported `*Data` type. Step 6 (Review) has no schema; it renders the consolidated values for confirmation. The field map below captures the schema as it stands today, before the multi-channel restructure begins.

## Step 1 — Campaign Details
File: `client/src/components/campaign-creation/campaign-details-new.tsx` (754 lines)

| Field | Type | Notes |
|---|---|---|
| `name` | string (1–150) | Campaign name, required |
| `externalId` | string (≤100), optional | Already exists; will move to row 2 next to plan dates |
| `dateRange` | `{ from: Date; to: Date }` | **Will be renamed to `planDates`** |
| `clientType` | `"direct" \| "agency"` | Required |
| `agencyId` | string, optional | Required only when `clientType === "agency"` |
| `dspId` | string, default `"activate"` | DSP selection per agency |
| `seatId` | string, default `"100001"` | Auto-populated from DSP |
| `brandId` | string, optional | Brand picker |
| `executionPlan` | `"quick_launch" \| "full_workflow" \| "request_for_deal"` | Default `"full_workflow"` |

**To add for multi-channel:** `mediaChannels: string[]` — multi-select with default `["billboard"]`, options [Billboard, Radio, Cinema, Retail, Mobile].

## Step 2 — Budget & Goal
File: `budget-goal.tsx`

| Field | Type | Notes |
|---|---|---|
| `countries` | string[] (≥1) | Drives currency + cinema operator list in Step 3 |
| `budget` | number (>0), optional | |
| `currency` | string | Required; per-currency tiered margin already implemented |
| `goalType` | string, optional | |
| `goalValue` | number (≥0), optional | |
| `goalUnit` | string, optional | |
| `customGoalName` | string, optional | |
| `carbonBudgetKg` | number (≥0), optional | |

**To add (Block E):** per-channel `budgetAllocation: Record<channel, Record<inventoryType, number>>` and a "split across inventory types" toggle.

## Step 3 — Targeting
File: `targeting.tsx`

| Field | Type |
|---|---|
| `demographics.ageGroups` | string[] (≥1) |
| `demographics.gender` | string[] |
| `demographics.income` | string[] |
| `demographics.interests` | string[] |
| `environment` | string[] |
| `audienceBehavior` | string[] |
| `signals` | string[] |
| `geofencing.targets` | `{ id, name, type, radius?, coordinates? }[]` |
| `geofencing.pois` | string[] |
| `geofencing.excludedAreas` | string[] |

**To add (Block F):** a Cinema-specific section that renders only when `mediaChannels` includes `cinema`:
- `cinemaOperators: string[]` — country-aware operator picker (PVR INOX, GSC, GV, etc.)
- `cinemaGenres: string[]` — Action, Family, Romance, Horror, Animation, Documentary…
- `cinemaShowtimeBands: string[]` — Weekday matinee, Weekday prime, Weekend
- `cinemaRatings: string[]` — local rating equivalents (G/PG/PG-13/R or U/UA/A)
- `cinemaSpecificFilms: string[]` — optional pinpoint film picker

## Step 4 — Media Selection (Inventory)
File: `media-selection.tsx`

| Field | Type |
|---|---|
| `selectedInventories` | string[] |
| `inventoryDetails` | any[] (loosely typed today) |
| `isAutoPlan` | boolean, optional |
| `autoPlanSummary` | object, optional |

**To rebuild (Block G):** Default view becomes a Plan Summary card; the inventory list moves into a full-screen overlay reachable via "View Inventories", with Mapbox on the right.

## Step 5 — Optimization → Schedule
File: `optimization.tsx`

| Field | Type |
|---|---|
| `budgetAllocation` | `Record<string, number>` (sums to 100) |
| `schedule.weekdayWeights` | `Record<string, number>` |
| `schedule.timeOfDayWeights` | `Record<string, number>` |
| `schedule.preset` | string, optional |

**To change (Block H):** rename to "Schedule"; remove `budgetAllocation` (lives in Step 2 now); pre-populate `weekdayWeights` and `timeOfDayWeights` per inventory; user only edits.

## Step 6 — Review
File: `review.tsx` — read-only summary of all five steps' data; no schema.

## Sequential multi-channel branching (Block D)

When `mediaChannels.length > 1`, the wizard runs one full pass per channel, in selection order. Each channel keeps its own copy of Steps 2–5 data. Step 6 (Review) renders a per-channel summary plus a combined plan total. Persistent navigation indicates "Channel 2 of 3 — Cinema, Step 4/6".
