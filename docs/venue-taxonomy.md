# MW Planner — Venue Taxonomy Alignment

Status: 2026-04-22. Owner: Planner team.

## Why this document exists
Planner, IMS and downstream measurement all need to agree on what a "Mall Atrium" or a "Cinema Lobby" actually is. The industry reference is the **OpenOOH Venue Taxonomy v1.1**. This file records:

1. Where Planner's `hierarchical-venue-selector.tsx` aligns with OpenOOH.
2. Where Planner has added MW-specific extensions (and why).
3. Where IMS-side ids must match Planner-side ids when a buy round-trips.

If you change the taxonomy, update this file in the same PR.

## Top-level groups in Planner

| Planner id | Label | OpenOOH parent | Notes |
|---|---|---|---|
| `transit` | Transit | 100 — Transit | 1:1 with OpenOOH. |
| `retail` | Retail | 200 — Retail | 1:1. |
| `outdoor` | Outdoor | 300 — Outdoor | Includes billboards, urban panels, transit-adjacent street furniture. |
| `accommodation` | Accommodation | 400 — Hospitality (subset) | Planner narrows OpenOOH "Hospitality" to lodging only. |
| `office` | Office | 700 — Office Buildings | 1:1. |
| `health` | Health & Medical | 800 — Health Care | 1:1. |
| `leisure` | Leisure | **500 — Leisure** | **Added 2026-04-22 to support Cinema as a first-class venue type.** |

## Cinema venue type — exact mapping

OpenOOH defines:

```
500 Leisure
└── 501 Movie Theatres
    └── (no canonical sub-tier in v1.1 — vendors extend)
```

Planner extends `501` with operationally meaningful sub-locations because both creative requirements and audience profiles change between, say, the lobby and the auditorium:

| Planner id | Label | Why |
|---|---|---|
| `leisure-movie-theaters-lobby` | Cinema Lobby | High dwell, family/group context, supports digital posters and standees. |
| `leisure-movie-theaters-concession` | Concession Stand | Purchase intent at point of sale; QSR brands target this. |
| `leisure-movie-theaters-auditorium` | Auditorium (on-screen) | The cinema ad slot itself. Pairs with `targeting.cinema.adPlacements`. |
| `leisure-movie-theaters-corridor` | Cinema Corridor | Lower dwell but high reach because it is unavoidable. |

The auditorium child is the one that must round-trip to IMS; the others map to IMS DOOH inventory inside the same property.

## IMS round-trip

When Planner serialises a campaign with cinema targeting, the venue ids above are sent to IMS verbatim. IMS already models cinema under `Leisure > Movie Theatres` (see `docs/ims-dependency-map.md`), so no translation layer is required.

If IMS later renames or renumbers anything, update the table above and add an explicit migration step — never rename silently.

## Other extensions Planner adds beyond OpenOOH

These are MW-specific because the canonical OpenOOH leaves them implicit:

- `transit-airport-lounges` (under Airport) — distinct audience and pricing.
- `health-hospital-cafeteria` — high dwell, captive audience.
- `office-elevator-bank`, `office-lobby`, `office-cafeteria` — captured under `office`.
- All three-letter region prefixes inside `outdoor` (urban panel vs. spectacular vs. street furniture).

When OpenOOH adds an official id for any of the above, swap to the official id and keep the Planner id as a deprecated alias for one major release.

## What does **not** belong in this taxonomy

- Inventory format (digital vs. classic) — that lives on the inventory record.
- Operator brand (PVR, GSC, AMC) — that lives in `shared/cinema-operators.ts` and is selected separately during cinema targeting.
- Showtime bands — those live on the targeting payload, not on the venue.

Keep the taxonomy answering one question only: *"Where, physically, is the screen?"*
