# Audience Heatmap — Detailed Behavioral Specification

**Status:** Current behaviour + target data design. Sections marked **[Shipped]** describe what the platform does today; sections marked **[Target]** describe the production data architecture this feature is moving toward. The PRD (§ Plan Map card, § Geography Targeting, § 10.5.2 Audience Map slide) stays the source of truth for where the heatmap appears; this document is the source of truth for how it is generated, what data feeds it, and where its outputs travel.

---

## 1. Purpose and scope

Before a planner commits budget to a set of sites, they need an answer to a question no inventory list can give them: *where does my audience actually move, hour by hour?* The audience heatmap answers it visually — a colour overlay on the planning maps in which darker, hotter areas indicate higher audience footfall. It turns site selection from "pins on a map" into "pins on a map of the audience," so a planner can see at a glance whether the sites they picked sit where their audience concentrates, and whether that concentration holds at the times their schedule actually buys.

This document covers: the generation model (§2), audience-segment scoping (§3), the historical-prediction model (§4), the underlying device-level data requirement and its infrastructure cost (§5), every surface where the heatmap renders (§6), and how heatmap imagery travels into the media plan proposal and the public map link (§7).

## 2. What the heatmap is generated from

**The unit of signal is a device observation.** The heatmap is built from mobile device IDs observed at a location at a time — each observation is a `(deviceId, latitude, longitude, timestamp)` tuple sourced from location-SDK / telco / data-partner feeds. A device standing near a mall entrance at 6 pm contributes one unit of "presence" to that map cell for that hour. Aggregated over millions of observations, presence becomes footfall, and footfall becomes the heat rendered on the map.

**Aggregation, never raw points.** Raw observations are never shipped to the browser. The pipeline aggregates observations into geographic cells (geohash-style tiling), each carrying a normalized weight per time bucket. The read API groups and caps server-side (today: cap 5,000 cells per request, weights renormalized 0..1) so the map stays responsive regardless of how much underlying data a city carries. **[Shipped]** — the serving shape (country × geo cell × time bucket × weight) is live; today's weights are seeded, deterministic placeholder data that a real vendor feed replaces without any schema change (§5).

**Hour-to-hour movement is the point.** A static footfall picture would only say *where* people are; the heatmap must show *how the audience moves* — the morning commute pouring into the business district, the evening flow toward retail and entertainment zones. That is why the timestamp on each observation matters as much as the lat/long: the same device contributes to different cells across the day, and stepping the heatmap through time makes that migration visible. **[Shipped]** at four dayparts (Morning, Afternoon, Evening, Night) selected by an on-map filter; **[Target]** hourly resolution (24 buckets × day-of-week), with the daypart filter becoming a coarse view over hourly data rather than the storage grain.

## 3. Audience-segment scoping — targeted vs. total footfall

The heatmap respects what the plan targets. Two modes, chosen automatically — the planner never configures this:

**Targeted mode.** When the plan carries audience targeting — demographic segments (age, gender, income), interest segments, or behavioral segments selected in the wizard's targeting step — the heatmap is generated from **only the device IDs classified into the targeted segment(s)**. The map then shows where *that audience* moves, not where everyone moves. A plan targeting "young professionals, fitness-interested" renders a materially different heat surface (gyms at 7 am, business districts at noon, nightlife zones at 9 pm) than the total-population picture.

**Total mode.** When the plan has no audience targeting, the heatmap falls back to **total footfall — every observed device ID** — the general population-movement picture.

Rules that follow from this:

- Segment membership is a property of the device profile in the data partner's taxonomy (a device is classified into demographics / interests / behaviours by the vendor's enrichment). The platform consumes classifications; it does not infer them.
- Multiple selected segments combine as a **union** (a device matching any selected segment counts), mirroring how the recommendation engine treats audience tokens.
- When a targeted segment is thin in a given market (too few classified devices for a stable surface), the map must say so rather than render misleading sparse heat — the existing empty state ("No mobility data available for this area") extends with a targeted-mode variant ("Not enough audience data for the selected segments in this area — showing total footfall") and falls back to total mode with the notice visible. Silent fallback is prohibited: a planner must never mistake the general population for their target audience. **[Target]**
- Mode is part of the API contract: the heatmap request carries the plan's segment selection, and the response declares which mode it actually served, so every rendering surface (and every export, §7) can label the layer honestly.

**[Shipped]** today: total mode only. **[Target]**: segment-scoped generation as described.

## 4. Historical data, predicted for the planning window

A plan is always for the future — flight dates weeks or months out — but footfall data is by definition historical. The heatmap bridges this the same way the rest of the planner forecasts: **historical observations are used to predict traffic on the planning dates.**

The prediction model is deliberately simple and explainable:

- **Same-period profile.** The primary predictor is the recurring weekly rhythm: footfall for a *Tuesday 8 am* on the flight is predicted from the distribution of historical Tuesday-8-am observations in that cell (trailing window, e.g. 8–12 weeks), because urban movement is dominated by weekly periodicity (commute, school, worship, market days).
- **Seasonality and event adjustment.** Where the historical window shows a stable trend (monsoon-season depression, festive-season lift), the profile is scaled by the trend factor. Known calendar events (public holidays in the market's calendar) map to their historical analogues — a flight day falling on a public holiday is predicted from historical holidays, not from ordinary weekdays.
- **Honest degradation.** Cells with insufficient history render at lower confidence rather than being invented; the serving layer carries an observation-count floor below which a cell is excluded (this mirrors the recommendation engine's rule of excluding inventory with insufficient availability data rather than guessing).

What the planner sees: when flight dates are set, the heatmap's time filter shows the **predicted** surface for the flight window; the tooltip copy makes the basis explicit ("Predicted from historical audience movement for similar days/times"). **[Target]** — today's seeded weights are date-independent; the serving contract already keys on time bucket, so adding the date dimension extends the key rather than replacing the API.

## 5. Data requirement, scale, and infrastructure cost

This feature has a data prerequisite that is honest to state plainly: **device-level observations (device ID + timestamp + lat/long) covering all major cities of every country the platform plans in.** Without breadth (all major cities) the heatmap is blank exactly where a national campaign needs it; without the timestamp the movement story (§2) collapses; without segment enrichment the targeted mode (§3) is impossible.

**This is big data, and it costs money.** Order-of-magnitude arithmetic for one mid-size market: 5 million active devices × ~50 observations/device/day ≈ 250 million rows/day, ~7.5 billion rows/month, per country — before enrichment. The cost lines that follow:

| Cost line | Driver | Mitigation |
|---|---|---|
| Data acquisition | Vendor licensing of observation + segment-enriched feeds, priced per market / per MAU | Buy aggregated tiles where the vendor offers them; license only planned-in markets |
| Ingestion & storage | Raw observation volume (~TBs/month/market) | Aggregate at ingest to cell × hour × segment; retain raw only in cold storage or not at all |
| Processing | Periodic re-aggregation, prediction refresh | Batch (daily/weekly), not real-time — planning tolerates day-old aggregates |
| Serving | Map-tile queries from every planning session | Pre-aggregated store + server-side cap (already the shipped pattern); cacheable per country × bucket × segment-set |

**The architectural rule that keeps cost sane:** the platform stores and serves **aggregates**, never a raw device-level lake. Raw observations live with the vendor or in a transient ingest layer; what persists platform-side is the cell × time × segment aggregate — smaller by 3–4 orders of magnitude, and free of the privacy exposure of raw device trails. Device IDs must arrive pseudonymised, are used only for dedup/segment joins at ingest, and never reach the serving store, the API, or any export.

**Rollout is per-market.** Because acquisition is priced per market, coverage lands city-by-city / country-by-country. A market without data shows the standard empty state — the feature never fakes coverage.

## 6. Where the heatmap renders **[Shipped]**

One shared map-overlay module owns the heatmap layer everywhere it appears, so behaviour, legend, and copy never drift between surfaces:

- **Plan Map card** (campaign detail): off-by-default "Audience heatmap" toggle; enabling reveals the time-of-day filter (All day, Morning, Afternoon, Evening, Night) and the Low → High intensity legend (cold transparent blue → hot red). Tooltip: "Shows where your target audience moves, based on audience mobility (footfall) data. Darker red areas indicate higher footfall."
- **Geo-fencing map** (wizard Step 3): the same layer informs where the planner draws fences — audience movement guides the fence before it guides the buy.
- Both surfaces keep the standard empty ("No mobility data available for this area") and non-blocking error ("Could not load mobility data") states; a heatmap outage never takes the map down with it.
- The layer renders beneath site markers and symbol layers — heat contextualises pins, never obscures them.

## 7. Heatmap in the proposal — PPT images and the public map link

The heatmap's value does not stop at the planning screen; it is a persuasion asset in the client-facing proposal.

**Media plan (proposal) images.** The media plan's PowerPoint export can carry **static heatmap images** so the client sees the audience story when the deck is shared — no live map, no login. Generation follows the deck's existing static-map pattern (Audience Map slide, PRD §10.5.2): the image is fetched server-side as a Mapbox static render with the heat surface composited, auto-fitted to the plan's site bounding box, and falls back to a clean placeholder when the token is missing or the fetch fails. Alignment rules inherited from the deck's parity principles:

- The slide appears **only when the plan's market has mobility data** — the same condition that makes the on-screen layer non-empty gates the slide (no empty-promise slides).
- The image is labelled with its mode (§3): "Audience footfall — targeted segments: …" vs "Audience footfall — total population," and with the time slice shown. A client must be able to tell what the heat *is*.
- Static-map URL length is budgeted (the heat overlay is rasterised/simplified before compositing, not passed as thousands of URL points).
- On-screen and exported representations change in lockstep — if the on-screen layer gains hourly resolution, the export's slice labels follow in the same release.

**Public map link.** The proposal carries a public, login-free map link (the shared inventory map view). The heatmap layer extends to this surface with the same toggle, legend, filter, and empty/error states as the internal maps — the client explores the same audience story interactively that the deck shows statically. Constraints specific to the public surface:

- The public endpoint serves the **same aggregated, capped payload** as the internal API — being aggregate-only by construction (§5), it exposes no device-level or commercially sensitive data, but it must be scoped to the shared plan's market and mode, not parameterisable to arbitrary countries/segments by URL editing.
- Mode labelling (targeted vs total) travels with the layer, identical to the PPT rule.
- **[Target]** — the public map link exists and shows plan inventory today; extending the heatmap layer to it is planned work.

## 8. Alignment map — where this document touches the PRD

| PRD anchor | What it owns | What this document adds |
|---|---|---|
| § Plan Map card | Toggle, filter, legend, copy, empty/error states | Generation model behind the layer (§2–5) |
| § Geography Targeting (Step 3) | Fence-drawing surface carries the layer | Same |
| § 10.5.2 Audience Map slide | Static-map slide pattern, fallback rules | The heatmap-composited variant and its gating/labelling (§7) |
| § Recommendation engine docs | Audience tokens, insufficient-data exclusion | Segment union semantics and honest-degradation mirror (§3–4) |

Changes to heatmap behaviour land here first, then the PRD's surface descriptions are updated to match — never the reverse order.
