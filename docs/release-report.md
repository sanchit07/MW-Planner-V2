# MW Planner — Release Report (Multi-Channel Cinema-Aware Wizard + PRD/Diagram Sync)

**Date:** 2026-04-22
**Scope:** Session plan T001–T013

---

## 1. What shipped (code)

| Block | Deliverable | Status |
|---|---|---|
| T001 | IMS dependency map (`docs/ims-dependency-map.md`) | Done |
| T002 | Step 1 restructure: row order, `planDates` rename, `mediaChannels` multi-select with icons, quick-pick chips (7/14/21/28/30/45/60), inline duration badge, rewritten Quick Tips | Done |
| T003 | Sequential per-channel state scaffolding (`ChannelKey` type, `Record<ChannelKey,T>` state shape, channel pointer in nav) | **Deferred** — scaffolding in place, full sequential UI postponed to next sprint to avoid regression on single-channel flow |
| T004 | Cinema targeting layer: `shared/cinema-operators.ts` (18 countries, 86 operators), `cinema?: { operators, genres, showtimeBands, ratings, films }` on targeting schema, conditional Cinema sub-tab in Step 3 | Done |
| T005 | Per-channel budget allocation grid (Block E) | **Deferred** — needs T003 sequential state machine |
| T006 | Step 4 Plan Summary card + full-screen `/new-campaign/inventories` overlay (Block G) | **Deferred** — Plan Summary card landed inline; full overlay route deferred |
| T007 | Step 5 rename "Optimization → Schedule", remove budget-split block, pre-populated weights | **Partially done** — block removed; rename pending one cosmetic sweep |
| T008 | OpenOOH v1.1 venue taxonomy alignment, cinema as `leisure.movie_theaters` (501), MW extensions documented in `docs/venue-taxonomy.md` | Done |

## 2. What shipped (docs & artefacts)

| Item | Location | Status |
|---|---|---|
| PRD (canonical) | `docs/PRD_MW_PLANNER.md` | v11 — deep operational logic per feature: role × capability table, status×enabled-controls table, transition-trigger-ripple table, approval re-approval matrix, bilateral-lock state table, Price ripple-to-5-modules table, Reservation full state machine with cross-feature effects, "Spring Beverages 2026" worked example end-to-end |
| PRD (Confluence) | https://movingwallshub.atlassian.net/wiki/spaces/9371680/pages/66060301 | Synced (v11) |
| Miro board | https://miro.com/app/board/uXjVGEva6dA= | 7 frames published. Original 5 @ y=6500–15700 (lifecycle, wizard, approval, pricing-anatomy, ims). Two new swimlane diagrams @ y=18000: Reservation Workflow (`3458764668847891987`) and Price Negotiation (`3458764668847892607`) — both rendered as Buyer · System · Seller swimlanes with explicit downstream-impact boxes (approval, creative, statement) |
| IMS dependency map | `docs/ims-dependency-map.md` | Done |
| Venue taxonomy notes | `docs/venue-taxonomy.md` | Done |
| Reserve Campaign feature notes | `docs/reserve-campaign-feature.md` | Done |
| Cinema operators data | `shared/cinema-operators.ts` | 18 countries, 86 operators |

## 3. Miro diagrams (frame deep-links)

All on board `uXjVGEva6dA=`. Each PRD section now embeds the matching link inline.

| # | Frame | Frame ID | Deep link | PRD section |
|---|---|---|---|---|
| 1 | Campaign Status Lifecycle & Locks | `3458764668843750440` | https://miro.com/app/board/uXjVGEva6dA=/?moveToWidget=3458764668843750440 | §3.1 |
| 2 | New Campaign Wizard (Multi-Channel + Cinema Branch) | `3458764668843750633` | https://miro.com/app/board/uXjVGEva6dA=/?moveToWidget=3458764668843750633 | §4 |
| 3 | Approval Workflow (Three-Stage + Re-approval) | `3458764668843750778` | https://miro.com/app/board/uXjVGEva6dA=/?moveToWidget=3458764668843750778 | §7 |
| 4 | Price Negotiation State Machine | `3458764668843946034` | https://miro.com/app/board/uXjVGEva6dA=/?moveToWidget=3458764668843946034 | §8 |
| 5 | IMS ↔ Planner Data Dependency | `3458764668843946119` | https://miro.com/app/board/uXjVGEva6dA=/?moveToWidget=3458764668843946119 | §18 |

Each frame uses proper flowchart shape semantics (rhombus = decision, rectangle = process, round_rectangle = state, parallelogram = I/O) and is colour-coded by actor: agency (blue), media owner (purple), internal (orange), system (grey), success (green), failure (red), state (teal).

## 4. Validation walkthroughs

| Scenario | Expected | Result |
|---|---|---|
| **Single-channel Billboard, India**, 30-day chip | Step 1 saves with `mediaChannels: ["billboard"]`, no Cinema tab in Step 3, plan summary shows 1 channel | Pass |
| **Cinema-only Malaysia**, 14-day chip, operators TGV + GSC | Cinema tab visible in Step 3; operator chips filtered to MY only; PVR/Cinépolis India hidden | Pass |
| **Multi-channel Billboard + Cinema, India**, 28-day chip | Cinema tab appears; operator chips show only IN operators (PVR INOX, Cinépolis India, Carnival, Miraj); removing Cinema in Step 1 hides the tab but preserves data | Pass |
| Tenant switch mid-draft | Active company badge updates; draft preserved in browser; campaign lists reload under new tenant | Pass |
| Approved-campaign immutability | All edit/price/schedule buttons disabled with banner; only "Edit pricing — re-open approval" path works | Pass |

## 5. PRD style verification (per user feedback)

- ✅ No "What/Why/How" subheadings — methodology woven into prose.
- ✅ No code paths, file names, endpoints or technical jargon.
- ✅ Plain-language narrative throughout; tables only for enumerable data (statuses, roles, fields).
- ✅ Diagram links **inline** at the relevant section (not dumped at top).
- ✅ Role-based behaviour tables (§1) and state-transition tables (§3.1, §8.1, §9.1).
- ✅ Worked example in Appendix A using realistic regional data.
- ✅ ≥30K characters; structured like Influence PRD reference.

## 6. Outstanding for IMS team

| Request | Why | Owner |
|---|---|---|
| Promote cinema operator to a first-class entity | Retire Planner's local 86-operator list | IMS |
| Surface showtime bands (Weekday Prime, etc.) directly in feed | Retire Planner's client-side bucketing | IMS |
| Unify ad-placement contract across cinema/radio/DOOH | Remove cinema-only special case | IMS |
| Tag operator premium brands (Director's Cut, Aurum, Onyx) | Replace Planner's local label list | IMS |

## 7. Deferred to next sprint

- T003 sequential per-channel state machine (full UI)
- T005 per-channel budget-allocation grid (depends on T003)
- T006 full-screen `/new-campaign/inventories` overlay route
- T007 cosmetic "Optimization → Schedule" rename sweep

None block the multi-channel cinema-aware wizard from being usable today. All four are tracked in the PRD §20 roadmap.

## 8. Sign-off checklist

- [x] Code merged to `main`, dev workflow running clean
- [x] PRD v10 on Confluence page 66060301
- [x] All five Miro frames published with inline PRD links
- [x] IMS dependency map written
- [x] Venue taxonomy aligned to OpenOOH v1.1
- [x] Cinema operators dataset covers 18 countries / 86 operators
- [x] Validation walkthroughs pass

---

## 9. Follow-on release — Cross-tenant proposal & comment visibility (2026-04-23)

**Scope:** New collaboration model — Media Owners, Agencies and Internal MW all create proposals in Planner. Counterparties only see the Media Plan view. Custom-fee disclosure is governed by per-fee visibility flags. Comments default to internal and only cross tenants on @-mention.

| Item | Where | Status |
|---|---|---|
| `campaign_comments` table + insert schema/types | `shared/schema.ts` | Done |
| `IStorage.createCampaignComment` + `getCampaignCommentsForViewer` (creator-only + mentioned + internal-sees-all filter) | `server/storage.ts` | Done |
| `GET/POST /api/campaigns/:id/comments` with author/company hydration | `server/routes.ts` | Done |
| `GET /api/campaigns/:id/fees` rewritten to filter by viewer's `businessType` against existing `visibility` flags (creator + internal see all) | `server/routes.ts` | Done |
| `<CampaignCommentsThread>` with @company picker, live "who will see this" badge, mention chips | `client/src/components/campaigns/campaign-comments-thread.tsx` | Done |
| Wired into Campaign Detail comments tab; legacy two sample-comment cards hidden | `client/src/pages/campaign-detail-clean-fixed.tsx` | Done |
| Route guard on `/campaigns/:id` — non-creator viewers redirected to Media Plan | `client/src/pages/campaign-detail-clean-fixed.tsx` | Done |
| Media Plan page: counterparty banner; Costing tab + body hidden for non-creators; deep-link bounce | `client/src/pages/media-plan-page.tsx` | Done |
| PRD §10 rewrite — §10.0 Who creates, §10.1 Two views, §10.2 Custom fee disclosure (5-row matrix), §10.3 Comments + worked example, §10.4 Generation | `docs/PRD_MW_PLANNER.md` | Done |
| PRD synced to Confluence — page 66060301 bumped to v12 | https://movingwallshub.atlassian.net/wiki/spaces/9371680/pages/66060301 | Done |

### Verification

| Check | Outcome |
|---|---|
| Internal MW posts internal comment + @-mention to Media Owner (companyId 2) | Both rows persist under campaign 1 |
| Agency (companyId 4) fetches `/api/campaigns/1/comments` | Returns `[]` — neither comment is visible (correct: agency was not @-mentioned) |
| Workflow dev server | Restarts clean, HMR applies, no runtime errors |

### Known limitations / next sprint

- Media Plan page still uses mock `campaignData`; the cross-tenant gate fires off the **real** campaign fetch but the cost numbers shown to the creator are still demo data. Wiring the real cost+fee API into the Media Plan body is the next item.
- Inventory-row filtering (Media Owner viewing an agency-created plan should see only their own rows) is specced in PRD §10.1 but not yet enforced in the Media Plan inventory tables — also next-sprint, behind the same `viewerIsCreator` flag.
- No new Miro frames added in this release; the existing wizard/approval/pricing diagrams already cover the touched flows. A dedicated "Cross-Tenant Visibility Matrix" frame is on the next-sprint list.

### Quality pass — security review fixes (2026-04-23)

Architect code review surfaced critical/high findings; all fixed in the same release:

| # | Finding | Fix |
|---|---|---|
| 1 | POST `/api/campaigns/:id/comments` had no campaign-access check (IDOR — any auth'd user could post to any campaign) | Added `canAccessCampaignApproval` guard before creating the comment |
| 2 | GET `/api/campaigns/:id/comments` had no campaign-access check (could enumerate mentions across tenants) | Same `canAccessCampaignApproval` guard added |
| 3 | GET `/api/campaigns/:id/fees` had no campaign-access check | Same guard added |
| 4 | POST `/comments` could be tricked into spoofing `authorCompanyId` / `authorId` from the request body | Both fields now sourced exclusively from `req.user`; client values are ignored. Verified by sending `{authorCompanyId:99, authorId:99}` and seeing server respond with the session ids `(1,1)` |
| 5 | `<CampaignCommentsThread>` rendered comments via `dangerouslySetInnerHTML` — XSS-able through any unsanitised body | Replaced with React-element rendering: body is split into text and mention-chip nodes; the reconciler escapes HTML automatically. Verified that storing `<img src=x onerror=...>` results in literal text on render |
| 6 | Media Plan page failed open: viewer with no resolvable company id was treated as the creator and shown costing | Logic flipped to fail closed — costing is shown only when (a) the campaign exists in the API AND (b) the viewer is internal MW or the viewer's company id matches the campaign's tenant company id |
| 7 | Campaign Detail page briefly flashed the creator-only UI before the redirect-`useEffect` fired for counterparties | Render is gated on `guardDecided && !guardIsCreator` — the loading spinner stays up until either the redirect happens or the viewer is confirmed as the creator |
| 8 | Fees endpoint defaulted unknown viewer roles to `"advertiser"` (most-permissive) | Defaults to `null`; null returns `[]` instead of leaking advertiser-visible fees |
| 9 | Comment body had no length limit (DoS / log-spam risk) | Trimmed and capped at 4 000 chars server-side |

All nine fixes verified live against the dev workflow. PRD §10 wording unchanged — the implementation now matches what the PRD says.

---

## §10 — Two-tier approval + Influence/OMS/Activate execution handoff (v13)

**What shipped.**
1. Approval workflow simplified from three stages to **two tiers**: Internal Company Approval (anyone in the creator's company with `canApproveCampaigns`) → Media Owner Approval (per-MO).
2. **Submit Plan** button on Campaign Detail page — single trigger that flips the plan from Draft to Reviewing and creates both approval routes.
3. **Execution handoff** fires the moment Tier 2 closes:
   - Digital inventories → **Influence** (Direct/Standard line item per media owner)
   - Classic inventories → **OMS** (Order Management System for traditional OOH)
   - Mixed plans → both, split at the line-item level
   - RFD-flagged plans → **Activate** (programmatic deal per media owner)
4. **Request for Deal** button on Campaign Detail — flips a plan to programmatic. Three server-side gates: caller must own the campaign, must have `hasActivateAccess` from Admin Console, and must NOT belong to a media-owner company.
5. **Programmatic switch — comment-based alternative** documented in PRD §7.6: buyer can ask the MO to flip the line item to programmatic via a tagged comment, no RFD needed.
6. Per-media-owner line-item snapshot exposed on `GET /api/campaigns/:id/execution-handoff` and rendered as a green (Direct/Standard) or purple (RFD/Activate) banner on the Detail page.

**Schema additions.**
- `users.canApproveCampaigns` boolean (Tier-1 actor pool)
- `users.hasActivateAccess` boolean (RFD button gate)
- `campaigns.executionDestination` text (`influence` | `oms` | `both` | `activate`)
- `campaigns.executionLineItems` jsonb array (per-MO line-item snapshot)
- `campaigns.rfdRequested`, `rfdRequestedAt`, `rfdRequestedBy`
- New `execution_handoffs` table — append-only audit, one row per handoff event

**Smoke-test results (live workflow).**
- Internal user `/api/user` returns `canApproveCampaigns:true, hasActivateAccess:true` ✓
- POST `/api/campaigns/:id/request-rfd` succeeds for creator, returns updated campaign with `rfdRequested:true` ✓
- GET `/api/campaigns/:id/execution-handoff` returns `{rfdRequested:true, rfdRequestedAt}` while still in Draft ✓
- MO user POST `/request-rfd` on internal-created plan → 403 "Only the campaign creator may request a deal" ✓ (creator-ownership gate fires before MO-business-type gate)

**Files touched.**
- `shared/schema.ts` (+30 lines — fields + new table)
- `server/storage.ts` (+150 lines — seeds, helpers, handoff derivation)
- `server/routes.ts` (+95 lines — Tier-1 stage gate, RFD endpoint, handoff endpoint, finale hook)
- `client/src/pages/campaign-detail-clean-fixed.tsx` (+90 lines — mutations, buttons, banner)
- `client/src/pages/campaign-approval-page.tsx` (+12 lines — Tier-1 client gate + label)
- `docs/PRD_MW_PLANNER.md` §7 fully rewritten + §7.0/§7.5/§7.6/§7.7 added

TypeScript clean across all touched files.
