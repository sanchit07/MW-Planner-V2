# Planner ↔ IMS Dependency Map

This document records what MW Planner consumes from the Inventory Management System (IMS), and where the cinema, retail, and billboard work in this initiative either lines up with what IMS already exposes or requires new IMS support. The reference for IMS itself is the Confluence page **"IMS — Inventory Management System Documentation"** (page ID 155779159, version 1.7, March 2026).

The headline finding is reassuring: IMS has already done a great deal of the modelling work that cinema needs. Planner can adopt IMS conventions directly rather than inventing parallel concepts.

## What Planner already consumes from IMS

Inventory records — Planner queries IMS for the full inventory list (location, panel count, operating hours, base CPM, fill rate). IMS is the single source of truth for the screen-level catalog and Planner does not store its own copy.

Venue taxonomy — IMS classifies every Inventory using the OpenOOH three-tier hierarchy (Parent → Child → Grandchild). Planner's targeting and Plan Summary use the Tier 1 names verbatim ("Transit", "Retail", "Outdoor", "Leisure", "Education", "Office", "Health & Beauty", etc.). Planner's `hierarchical-venue-selector.tsx` mirrors this taxonomy and currently renders the same Tier 1 categories.

Pricing — IMS exposes Base CPM and Base CPS rate-card prices; Planner reads these for forecasting and the price-management surface, then applies the per-currency tiered margin ladder on top.

Availability — Planner's reservation and Plan Summary flows read IMS availability windows.

Network Mode (deferred) — IMS PRD flags that Network Mode (Homogeneous vs Heterogeneous, Section 4.6.5) is not yet implemented in the IMS prototype. Planner does not depend on this today.

## Cinema — alignment between IMS and Planner

This is the most important section because the user has asked for a deep cinema build. The IMS PRD already documents cinema as a first-class venue type, which means Planner's cinema work can adopt these conventions without coordination delay.

The taxonomy slot. Cinema is `Leisure > Movie Theatres` in the OpenOOH-aligned IMS taxonomy. IMS already triggers cinema-specific registration fields when this Tier 1+2 combination is selected. Planner's venue selector currently labels the parent category "Entertainment" instead of "Leisure" and has no Movie Theatres child — this is the only taxonomy gap on the Planner side, and it is fixable in `hierarchical-venue-selector.tsx`.

Cinema-specific fields IMS captures per Inventory:
- Auditorium Count
- Ad Placement Type — Pre-Show / Intermission / Post-Show
- Showtime Source — Manual or TMS API
- Average Showtimes Per Day
- Seating Capacity
- Occupancy Rate Default
- Lumens, Projection Aspect Ratio, Audio Format (5.1 / 7.1 / Dolby Atmos)

These map cleanly onto the cinema targeting fields the new Step 3 needs:
- Planner's "Showtime Band" (Weekday matinee / Weekday prime / Weekend) is a coarser bucket on top of IMS Average Showtimes Per Day plus operating hours.
- Planner's "Ad Placement" filter (Pre / Intermission / Post) reads IMS Ad Placement Type directly.
- Planner's "Operator" picker is an aggregation layer above IMS — operator is a tag/parent on the Inventory, derived from the media-owner organisation that registered the screen.

Cinema impression formula. IMS specifies the cinema impression model as `showtimes per day × seating capacity × occupancy rate × ad slots per showtime`. Planner's forecasting box currently uses the standard DOOH formula `plays per hour × audience per play`. **This is a real gap on the Planner side** — `forecasting-box.tsx` needs a branch when the inventory is cinema. We will address it in T006 / T007 by passing the venue type through to the forecaster.

Cinema delivery chain. IMS describes the chain as Advertiser → Campaign booked in Planner → Inventory resolved in IMS → Creative delivered to TMS → TMS inserts ad into SPL → Projector plays SPL at showtime → TMS confirms proof-of-play. Planner only owns the first two arrows; the remaining steps are downstream and out of scope for this initiative.

Content format. IMS notes that cinema requires DCP (Digital Cinema Package) with KDM (Key Delivery Message) for encryption, not the MP4/JPG/HTML5 stack used by standard DOOH. **This is a Creative Management dependency, not a Planner dependency** — Planner only needs to display the requirement to the user during inventory selection so they understand why the creative spec differs.

## Retail — alignment

Retail in IMS sits under `Retail > Shopping Mall / Retail Store / Market & Plaza`, with Tier 3 sub-categories like Atrium, Food Court, Department Store, Convenience Store. Planner already mirrors this in the venue selector. No IMS gap for the planned Retail work.

The one nuance is that some retail formats (point-of-purchase digital screens, queue boards) sit closer to "Other" or vendor-specific custom categories in IMS. We will treat these as specialised Tier 3 entries rather than separate Tier 1 categories.

## Billboard — alignment

Billboard in IMS is `Outdoor > Roadside Billboard` and `Outdoor > Street Furniture`. IMS exposes the standard DOOH fields (loop length, plays per hour, audience per play) which Planner already consumes. No new IMS work is needed for Billboard adjustments in this initiative. The Planner-side changes are purely UX (Step 1 channel selector, Plan Summary, Schedule rename).

## Gaps that need IMS team attention

The list is short and most items are nice-to-have rather than blockers:

| # | Gap | Severity | Why it matters | Workaround for now |
|---|---|---|---|---|
| 1 | No first-class "Cinema Operator" entity in IMS | Medium | We want the country-aware operator picker (PVR INOX, GSC, TGV…) keyed off a stable IMS identifier | Planner uses `shared/cinema-operators.ts` as a static catalog, joined to inventory rows by media-owner organisation name. Replace with IMS operator IDs once available. |
| 2 | No "Specific Film" feed exposed by IMS | Low | The optional Step 3 film picker would benefit from IMS knowing which films are scheduled at each cinema | Planner uses a stub list for now. Real implementation later. |
| 3 | Network Mode (Homogeneous/Heterogeneous) deferred per IMS PRD | None today | Required for accurate yield modelling when buying entire networks | Planner does not surface this concept yet; matches IMS. |
| 4 | TMS playlist confirmation timing | Low | For real-time delivery dashboards on cinema campaigns | Planner displays scheduled vs delivered using daily TMS reconciliation; live feed is a future enhancement. |

None of the four block this initiative. Item 1 is the only one worth raising with the IMS team in the medium term so we can replace the static operator catalog with a live IMS query.

## Decisions Planner is taking based on this map

1. The Planner venue selector will be updated to use `Leisure > Movie Theatres` exactly as IMS does, replacing the current "Entertainment > Cinema" labelling. (Task T008.)
2. The cinema branch in Step 3 Targeting will read operator names from the static catalog (`shared/cinema-operators.ts`) for now, with a comment marking the IMS replacement point.
3. The forecasting box will receive a `venueType` prop and switch to the cinema impression formula when the inventory is `Leisure > Movie Theatres`. (Task T006/T007.)
4. The new PRD will call out the four gaps above explicitly so they are visible to both teams.
