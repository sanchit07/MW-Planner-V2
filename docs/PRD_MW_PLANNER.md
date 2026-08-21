# MW Planner — Product Requirements Document

MW Planner is the campaign-side workspace of the Moving Walls platform. Buyers — agencies, advertisers and internal trading desks — use it to plan, price, approve, schedule, run and bill Out-of-Home advertising campaigns across Digital OOH, Classic OOH, Cinema, Retail, Mobile, Radio and Web channels. Sellers — media owners — use the same workspace to receive booking requests, hold inventory, accept or counter prices, deliver creative and reconcile statements. The application is multi-tenant: a single user can act on behalf of more than one company, and everything they see is filtered by whichever company they have chosen as their active tenant.

This document is written for non-technical readers. Every feature section walks through the same four lenses, in plain English: who starts the action, who has to respond, what changes the moment each side clicks, and what the action blocks or unblocks elsewhere in the product. Wherever a flow involves a back-and-forth between buyer and seller or a hand-off between modules, the relevant diagram on the Moving Walls Miro board is linked inline at the relevant section. Diagrams are kept *in place* — when a feature changes, the Miro frame is updated rather than duplicated, so the link in this PRD always points at the current truth.

---

## 1. Authentication and multi-tenant company switching

Every session begins on the sign-in screen. The user enters a username or email and a password and is taken to the dashboard. The platform also runs a quiet idle timer in the background: after **29 minutes** without activity it raises a warning dialog — "You've been inactive for a while. Do you want to stay signed in?" — with a 60-second countdown, and at the **30-minute** mark, if the user has not responded, it signs them out and returns them to sign-in. The same timings apply to normal and impersonation sessions alike. This protects shared machines in agencies and trading desks where leaving a session unattended is common.

The platform exists in a multi-tenant world because holding companies, agency networks and media owner groups have employees who legitimately operate on behalf of more than one entity. Forcing them to log out and back in to switch companies loses any draft they were working on and produces gaps in the audit trail. Instead, the header bar shows the active company and a dropdown that lists every company the user is mapped to. Selecting another company quietly switches the entire workspace — every list, dashboard widget, forecast, reservation queue and approval inbox reloads under the new company without a page refresh. A draft campaign in progress is preserved across the switch and shown again on returning to the original tenant.

User accounts carry one of five roles. The role is the single biggest determinant of what the user sees and what they can do. The table below is the authoritative role × capability map referenced throughout the rest of this document:

| Role | Dashboard widgets | Create campaigns | Approve at | Negotiate price | IMS link | Build statements |
|---|---|---|---|---|---|---|
| Internal | Media owner set | Yes — for any tenant | Tier 1 | Yes — platform side | Yes (default) | Yes |
| Agency | Agency set | Yes — for the agency | Tier 1 | Yes — buyer side | Granted on request | Yes — agency-only |
| Media Owner | Owner set | Yes — usually self-billing | Tier 2 (own inventory only) | Yes — seller side | Yes (default) | Yes — owner-only |
| Advertiser | Advertiser set | Yes — direct buys | Cannot approve | Read-only | No | No |
| Reseller | Agency set | Yes | Tier 1 | Yes — buyer side | Granted on request | Yes |

The role and the active tenant together define every authorisation decision. Switching tenants in another browser tab does not retro-grant rights, because authorisation is checked per request against the active tenant on the server side. A user with two tenants in their dropdown — one as Agency, one as Media Owner — therefore behaves as a fundamentally different actor depending on which is selected.

---

## 2. Dashboard

The dashboard answers the first question every persona arrives with: "what needs my attention today?" The answer differs sharply by role. A media owner wants to know which campaigns are sitting in their approval queue, which reservations they need to respond to, what their inventory utilisation looks like this month, and whether any creative has failed to deliver. An agency planner wants to know which of their campaigns are missing creative, how their spend is pacing, which proposals are awaiting client sign-off, and which holds are about to expire. The dashboard inspects the active company's business type and renders a different widget set for each:

| Active company type | Widgets shown |
|---|---|
| Media Owner | Campaign Overview by Status · Sales Performance Summary · Creative Status Tracker · Inventory Utilisation Summary · Pending Hold Requests |
| Media Agency | Campaign Overview · Budget Tracker · Creative Status · Recent Activity · Expiring Holds |
| Advertiser | Campaign Overview · Spend Tracker · Creative Status |
| Internal | Same widget set as Media Owner — internal staff operate the trading desk |

Every widget is tenant-scoped, refreshes when the active tenant changes, and refreshes when the user adjusts the date range chip at the top of the page. A small badge in the header always shows the active company's name so the user is never confused about which side of the marketplace they are looking at. Each widget tile is a hand-off into the underlying module: clicking through the Pending Hold Requests tile lands the media owner directly in the Reservations queue with the Pending filter pre-applied, and clicking the Creative Status tile lands the agency planner in the Creatives library with the "missing assignment" filter on.

The Inventory Utilisation widget defaults to a by-type view — Classic, Digital Screen, Transit, Retail, Network, Radio, Experiential and Cinema — and offers a toggle to switch to a by-format breakdown for screen and audio dimensions. Every monetary figure on the dashboard uses the active tenant's currency code (USD, MYR, SGD, AED, INR and so on) instead of a generic dollar sign, because more than half of the platform's bookings are in a non-USD currency and the previous symbol-only display caused regular reading errors.

Several dashboard behaviours were tightened during the production-alignment pass. Share of voice is computed the same way everywhere: for digital inventory SOV is the plan's share of booked spots on its screens, while classic inventory always reports 100% (a printed billboard is never shared). The Budget Spent card respects the page's date range **and** status filters rather than reading an all-time total, and the Budget Tracker widget computes its period comparison and percentage from real spend in the selected window instead of a hardcoded figure. The performance table carries a **Total Cost** column, and the old "All Campaigns" dropdown above it is gone — the date-range picker is the single scoping control, and its quick-pick day list (7, 14, 21, 28, 30, 45, 60) is the same list used by the wizard's flight-date chips and the Plans list filter panel, so a user never has to re-learn period options between surfaces. The Audience Reach card gains a status dropdown and, when a plan has not yet gone live, an explanatory note that the figures shown are planned data rather than measured delivery. Finally, the sidebar and page vocabulary say **Plans** — campaigns are called plans everywhere in the UI, while the underlying records keep their original naming.

---

## 3. Campaigns and the campaign status lifecycle

A campaign is the top-level booking record that holds the buyer's intent — its name, its dates, its budget, its geography, its targeting, the inventory it has reserved and the line items it is running. Every other module in the system hangs off the campaign, which is why the campaigns list is the primary working surface for planners and traders.

The list is a paginated, sortable table with filters for status (including Archived), country, date range, media owner, agency, tag, goal type, planner and free-text search; the date filter offers the same "Next N days" quick-select chips (7, 14, 21, 28, 30, 45, 60) used everywhere else. Each row shows the plan's **12-digit plan number** as its ID (falling back to the internal numeric id for legacy rows), a combined Goal readout ("3.45M Impressions"), comma-separated numbers throughout, and share of time expressed in hours. The **Planned By** column names the real creator with their company beneath; when the creator's primary company is not the active tenant an amber **External user** badge appears with a tooltip explaining that the plan was created by someone outside the company, and plans created by since-removed users stay listed — the creator renders as "Former user" / "Access revoked" rather than the row disappearing. Separately, when the plan itself was **created by a different company** (e.g. an agency plan on a media owner's inventory, viewed from the media-owner tenant), the row carries an **External plan** badge with a tooltip naming the creating company (§8.4). The list is the only view — there is no grid/card view toggle. Archive is available directly from the list, including multi-select bulk archive/delete. Each row carries an actions menu that lets the user open, edit, duplicate or archive the campaign, jump into the approval side panel, open the price-management workspace, generate a report or — if the plan is still in flight — fire **Request for Deal** (§7.6). The actions menu deliberately does **not** carry a manual "Execute" / "Launch" button: launch is automatic the moment Tier 2 closes (§7.5), so a manual button would only ever be a footgun for users who pressed it before approval was actually complete. The detail view is split into six tabs — Campaign Plan, Inventory Details, Costing, Operation Details, DOOH Schedules and Geography Targeting — that walk a viewer from the deal-level summary all the way down to the per-screen schedule. The header shows the plan number as "Plan ID", the Cost Split block labels its sum as **"Total plan budget"** with an explanatory tooltip, and the Costing tab derives Media Cost, the 15% Platform Fee, Net Cost, custom fees and the Total Cost Summary from the plan's actual budget and fee list — every summary figure carries a plain-language tooltip. (There is no longer a separate Targeting tab: demographics, behaviours, signals, venue mix, cinema and retail targeting are folded into the **Targeting Applied** box on the Campaign Plan tab.)

### 3.1 The status ladder and what each status really means

Campaigns move through a clear status ladder. The platform deliberately separates this campaign-level status from the status of each individual line item, so a single misbehaving line — a creative not yet uploaded, an inventory pulled at the last minute — does not pull the parent campaign backwards. Status is more than a label: it is the master switch that controls who can edit what, which buttons are visible, whether reservations can be created, whether prices can be negotiated and whether creative can be assigned.

**Diagram —** [Campaign Status Lifecycle & Locks](https://miro.com/app/board/uXjVGEva6dA=/?moveToWidget=3458764668843750440) (open in Miro)

| Status | What the user sees | What is enabled | What is disabled / blocked |
|---|---|---|---|
| Draft | Wizard saves continuously | Edit any field; delete the draft; save negotiated prices (moves the plan to Negotiating, §8.2) | Cannot request holds; cannot generate proposal versioned for client; not visible to media owners |
| Planned | Wizard submitted, sitting in the agency planner's view | Withdraw to draft | Cannot edit budget, dates or targeting; cannot delete; cannot change inventory |
| Negotiating | Prices differ from the system-calculated baseline and the **creator can edit** — before the first Submit, or after a Tier-1 reject | The creator edits Price Management freely; save, submit | Not visible to media owners; execution blocked until approval |
| Reviewing | Sitting with internal or with media owners | Withdraw, comment, swap declined inventory through Modify Plan, propose price adjustments | Buyer cannot directly edit budget, dates or targeting without first withdrawing |
| Approved | Lock banner across detail view; immutability tooltip on every action | Read; assign creative (same-spec swap only); convert holds to bookings | Every edit button disabled; price changes require explicit "open back up" — see §8.5 |
| Active | Campaign is on-air | Pause, swap creative, adjust pacing weights, fire signals, view delivery | Cannot change budget, inventory list, dates or targeting |
| Paused | On-air paused mid-flight | Resume, edit pacing, edit creative | Spend stops accruing; inventory continues to be held; signals do not fire |
| Completed | End date reached | Read forever; pull reports; export to statement | All write actions disabled |
| Rejected | Approver declined; final | Duplicate as fresh draft | Original is final and audit-only |

### 3.2 What triggers each transition, and what each transition triggers elsewhere

| From → To | Triggered by | Immediate effect | Cross-feature ripples |
|---|---|---|---|
| Draft → Planned | Planner clicks Submit on Step 5 of the wizard | Campaign becomes visible to the approval inbox | Approval workflow initialises (two tiers); reservation requests are written for every line item; the submitted prices become visible to counterparties in Price Management (§8) |
| Planned → Reviewing | Automatic, the moment Submit lands | Approval workflow activates | Tier 1 (the creator's company sign-off) opens; any flag-holder in the creator's company can approve, and the creator may self-approve if they hold the flag |
| Draft/Planned → Negotiating | First save of negotiated prices through the Price Management Summary drawer (§8.3) | Plan is formally "in negotiation"; History captures the saved batch | View-detail shows the unsubmitted-changes message until Submit |
| Negotiating → Reviewing | Submit Plan on the view-detail page | Plan locks as an offer on the table — no off-turn changes accepted | Proposed prices become visible to counterparties; approval cycle (re-)enters |
| Reviewing → Negotiating | The **Tier-1 internal approver rejects** — the plan returns to the creator with Price Management unlocked | Creator's rows editable again; nothing was exposed to media owners yet | Creator adjusts and re-submits; approval cycle restarts |
| Reviewing (stays Reviewing) | A counterparty counters — Counter Offer from the approval drawer or saving new proposed prices during its turn | Campaign status does **not** change; that owner's per-owner state becomes **Countered** with a turn marker; only its rows unlock | Only that owner's approval resets; other owners' approvals stand (§8.2) |
| Reviewing → Approved | Both approval tiers pass | Lock banner appears; edit buttons disable | Every reservation flips from Reserved to Booked; creatives can now be assigned to line items (same-spec); statement-builder unblocks the campaign |
| Approved → Active | Execution handoff — automatic at Tier-2 close for agency-, advertiser- and internal-led plans (§7.5); via the media owner's explicit Push from the Execution Plan workbench for media-owner-led plans (§7.9) | Campaign goes live the moment the first line is handed off | Execution lines begin their staged lifecycle (§7.9); the campaign view gains the "Pushed to execution" panel |
| Reviewing → Rejected | The Tier 1 approver declines; or a Tier 2 (media-owner) decline leaves no remaining inventory after swaps | Campaign becomes final-rejected | All holds released; pricing closes read-only (§8.6); no statement can include this campaign |
| Approved → Reviewing (re-approval) | Any change to budget, dates or any line-item price | Lock banner removed only for the affected tier | The affected Tier 2 media-owner approvals reset to pending; only the affected tier re-runs; existing creative bindings preserved |
| Active → Paused | Planner clicks Pause | Spend pauses; signals stop evaluating | Reservations remain held; partial billing accrues to date |
| Active → Completed | Automatic on end date | Final delivery snapshot taken | Statement-builder picks up the campaign on its next cycle |

The re-approval rule is the platform's safety net. It reconciles the operational reality that live deals sometimes need amendments with the audit reality that every approval must be re-recorded. The PRD is explicit on a subtle point: re-approval does **not** reset Tier 1 (the creator's company already signed off) — it resets only the affected media-owner row at Tier 2, because it is that media owner's price that changed.

---

## 4. New Campaign wizard

The wizard collects everything required to create a campaign and submit it for approval. The flow is intentionally identical for internal, agency and media-owner users — there are no role-based variations in the form — because the platform takes the position that buyer and seller need a shared mental model of what a campaign is. A wizard is preferred over a single thirty-field form because campaign data has cross-step dependencies: countries chosen in step 2 filter the cinema operators offered in step 3, channels chosen in step 1 control which sub-tabs appear in step 3, and the inventory list chosen in step 4 drives the schedule grid in step 5. A stepwise progress indicator keeps the planner oriented through these dependencies.

**Diagram —** [New Campaign Wizard (Multi-Channel + Cinema Branch)](https://miro.com/app/board/uXjVGEva6dA=/?moveToWidget=3458764668843750633) (open in Miro)

Drafts are saved continuously — to the browser on every keystroke and to the server every thirty seconds — so a closed tab never loses work. Submitting on the last step moves the campaign into Planned and triggers the first stage of the approval workflow described in §7. The same Submit click also writes a reservation request for every line item (see §9) and exposes the submitted prices to counterparties in Price Management (see §8). These three downstream actions happen atomically — the planner sees a single confirmation, but three modules are simultaneously primed.

### 4.1 Step 1 — Campaign Details

Step 1 captures the identity of the campaign. Fields are arranged so the planner answers identity, then scope, then counterparty, in that order:

| Field | Type | Required | Notes |
|---|---|---|---|
| Campaign Name | text | yes | Unique within the active company; defaults to a generated name like "Campaign Apr 22 26 — 003" |
| External ID | text | no | A free-text reference for matching against other systems; available to all roles |
| Plan Dates | date range | yes | Quick-pick chips for 7, 14, 21, 28, 30, 45 and 60 days; a live duration badge ("31 days") sits next to the label |
| Brand | searchable select | no | "Create new brand" inline dialog |
| Media Channels | multi-select dropdown | yes | Only **Digital OOH** and **Classic OOH** are selectable; Digital OOH is the default; cannot be empty. The underlying schema still accepts the legacy values (Cinema, Retail, Mobile, Radio, Web) so plans saved before the narrowing continue to load; on hydration any legacy value is dropped from the selection (falling back to Digital OOH if nothing remains). Cinema is no longer a Step 1 channel — it is reached through Venue Types in Step 3 (§4.3) |
| Client Type | select | yes | Direct or Agency. Pre-set to "Agency" and frozen when the logged-in user's company is itself an agency |
| Agency | searchable select | conditional | Shown side-by-side with Client Type when Client Type = Agency; "Create new agency" inline dialog. Pre-selected with the user's own company and frozen for agency users |
| DSP / Seat | select | conditional | Shown when an agency is selected. The Seat ID field is hidden entirely while no DSP (or "None") is chosen |

When the logged-in user belongs to an agency, Client Type and Agency arrive pre-filled with their own company and both fields are locked — an agency plans for itself, so there is nothing to choose. If the agency has child companies, a small note explains: "To create a plan in a child company's account, switch to that company from the profile menu first." When a DSP is chosen, two lines of guidance appear beneath the dropdown: "For digital, only inventories exposed to the selected DSP will be recommended. Classic inventories are not affected by the DSP choice." and "Expecting a different DSP here? Add its seat ID at admin.movingwalls.com and it will appear in this list."

The chosen Media Channels propagate through the rest of the wizard. The most important downstream effects are that channel-specific sub-tabs in step 3 appear only when their channel is selected here (the **Cinema** tab is the exception — it is unlocked by a Venue Types selection, not a Step 1 channel; §4.3), and the channel budget allocator in step 2 appears only when two or more channels are chosen. If the planner returns to step 1 and removes a channel after configuring its targeting, the tab hides but the data is preserved — the planner only loses it when they finally submit without that channel selected.

### 4.2 Step 2 — Budget and Location

Step 2 captures the total budget, the currency, the goal type (Reach, Frequency, Impressions or Awareness) and one or more target countries. Country selection here matters for two later steps: it filters the cinema operator picker in step 3 (operators are inherently country-specific) and it shapes the candidate inventory pool that the Recommendation Engine draws from in step 4.

The currency defaults to the active tenant's currency but can be overridden per campaign — useful for an Indonesian agency running a USD-denominated regional brand campaign. The budget must be greater than zero, and the plan duration in step 1 must fall between one and 365 days; both are validated before the Next button is enabled. The market-insight card on this step (the "inventories available in your market" count) is filtered by the media channels chosen in step 1 as well as the selected countries, so removing or adding a channel visibly changes the count instead of showing a static catalogue-wide figure.

When two or more media channels are selected in step 1, a "Channel Budget Allocation" card appears below the budget input. The card opens with the budget already auto-distributed across the selected channels by inventory-count proportions — the planner does not have to make a yes/no choice up front. Each row shows one of the selected channels, the proposed percentage and the derived currency amount (read-only, computed from the total budget). The percentage inputs are read-only by default; if the planner wants to override the auto-split they click the "I want to adjust the distribution manually" link at the bottom of the card, which unlocks the inputs. A "Distribute evenly" button splits the budget equally across the selected channels, and a "Reset" button restores the inventory-count proportional defaults at any time. The allocation is advisory — a soft amber warning appears when the percentages do not sum to 100%, but the Next button is never blocked, so the planner can iterate freely. With only one channel selected the card does not appear and the single channel automatically gets 100% of the budget, because a single-channel plan has no split to express.

**Distribution formula.** Step 2 runs before any IMS query, so the wizard cannot use *live* inventory counts at this stage. Instead it uses a static reference table of typical inventory proportions per channel (`DEFAULT_INVENTORY_PROPORTIONS` in `shared/cinema-operators.ts`) as the seed weights, and renormalises them against the planner's selected subset so the percentages always sum to 100%. The reference weights are: Digital OOH 40, Classic OOH 25, Retail 10, Mobile 8, Radio 7, Cinema 5, Web 5 — derived from the average channel mix observed across the IMS catalogue and intentionally kept market-agnostic at this point in the flow.

The formula, where `c` ranges over the selected channels:

```
weight(c)     = DEFAULT_INVENTORY_PROPORTIONS[c]
selectedTotal = Σ weight(c) for c in selectedChannels
percent(c)    = weight(c) / selectedTotal × 100
amount(c)     = totalBudget × percent(c) / 100
```

**Worked example.** A planner enters a 10,000 budget and selects Digital OOH + Cinema only. With reference weights 40 and 5, `selectedTotal = 45`, so Digital OOH gets `40/45 = 88.9 %` (8,889) and Cinema gets `5/45 = 11.1 %` (1,111). Note that Digital OOH does **not** stay at its absolute 30 % share of the global IMS pool — once Cinema is its only competitor, Digital absorbs the share that Classic, Retail, Mobile, Radio and Web would otherwise have taken. This is the desired behaviour: the budget split should reflect the *relative* weight of the channels the planner actually wants to buy, not their share of the global catalogue.

Future enhancement (planned, not yet built): swap the static reference table for live IMS counts at the (country, channel) level looked up against the targeted countries from step 2, so a plan running only in Indonesia would see weights that reflect the Indonesian inventory mix rather than the global average. The override mechanism (manual edit, distribute evenly, reset) does not change.

### 4.3 Step 3 — Targeting

Step 3 is a tabbed sub-form covering Demographics, Geography, Inventory Type and — conditionally — Retail and Cinema. The Inventory Type tab appears whenever any media channel is selected in step 1, which is always the case because step 1 defaults to Digital OOH. The Retail tab appears only when Retail is among the selected channels. The **Cinema tab is venue-gated, not channel-gated**: it appears when the planner selects **Movie Theater → Auditorium (on-screen)** in the Venue Types section of the Inventory Type tab (and deselecting that venue hides it again; plans saved with the legacy Cinema channel keep access). When the tab first unlocks, its targeting defaults are seeded automatically. Demographic targeting does not apply to cinema buys — the Demographics tab always shows, carrying an inline note that its selections apply only to the non-cinema media when the Cinema tab is active. The country filter from step 2 is respected throughout — choosing only India and Malaysia in step 2 means the cinema Operators block will never show Shaw or Golden Village (those operators serve Singapore). Step 3 carries no recommendations banner; the planner lands directly on the sub-tabs, and the Cancel / step-progress / Back / Next controls sit in a sticky footer pinned to the bottom of the viewport, matching the fixed footer of steps 1 and 2.

**Demographics.** Age groups, gender, income brackets, interests, audience behaviour, and signals & triggers. Venue targeting does not live here — it moved to the Inventory Type tab, where it composes with the inventory bucket selections. When the Cinema tab is active (via the movie-theater venue selection) the Demographics tab carries an inline "not applicable to Cinema" note pointing planners to the Cinema tab for cinema targeting.

**Inventory Type.** The Inventory Type tab is organised as three numbered sections that build on each other:

1. **Programmatic Buying** — a switch to target only programmatic inventories (digital screens bought automatically through connected exchanges/DSPs). When on, programmatic-capable inventory is preferred first; if the budget cannot be fully consumed by programmatic inventory, the unspent portion shifts automatically to the other inventory types kept selected in section 2. Two validations apply: (a) if the plan does not include the Digital OOH channel, the switch is disabled with an inline explanation — non-digital inventory such as Classic OOH (printed billboards, wraps, posters) cannot be bought programmatically, so the planner must add Digital OOH in step 1 to enable it; the flag is also force-reset to off if the Digital OOH channel is removed after enabling it. (b) If the plan includes both Digital and Classic OOH with the switch on, an inline note explains the budget priority: programmatic-capable digital buckets (Digital Screens, Digital Network, Digital Transit) are filled first and the remaining budget flows to the Classic OOH selections. A third rule takes precedence over both when it applies: if a DSP was selected in step 1, the switch is forced **on** and locked, with the message "Programmatic buying is on because you selected a DSP in Step 1. To turn it off, set the DSP back to None." — a DSP-routed plan is programmatic by definition. The flag is stored as `inventoryTypes.programmaticOnly`.
2. **Inventory Types** — the unified view of every inventory type the campaign covers, listing all selected media channels as grouped sections, each showing the budget allocated in step 2. The Digital OOH and Classic OOH channel groups each expose **three buckets** as a card grid — **Digital Screens / Digital Network / Digital Transit** and **Classic Billboards / Classic Network / Classic Transit**. Screens/Billboards hold individually-bought Outdoor-domain panels, Network holds synchronised multi-location Outdoor-domain buys, and **Transit covers moving objects only** — screens and wraps on buses, taxis, trains, trams, trucks and ferries while they move. Fixed inventory at bus stations, rail platforms or airports (platform screens, turnstiles, terminal displays, escalator panels, station dominations) is deliberately **not** part of the Transit buckets — a section-level explainer directs the planner to target those environments via Venue Types in section 3. Every bucket is selected by default with all of its formats included. Each card carries a checkbox the planner can untick to exclude the entire type (the card then mutes to grey with a "Not included" label) and a "Click to edit" link that opens a side drawer pre-filled with all formats checked, ready for the planner to untick the ones they want excluded. Clearing every format inside the drawer also deselects the type. The non-OOH channels keep their line-item presentation: **Cinema** shows budget only and points to the Cinema tab for configuration; **Retail** offers its twelve formats through the shared side drawer (writing to the same field as the Retail tab); **Radio, Mobile, Web** show budget only, as no format taxonomy exists for them yet.
3. **Venue Types** — the hierarchical OpenOOH venue selector (previously on the Demographics tab), applied **on top of** the section-2 selections to narrow the plan to specific environments (transit stations, airports, malls, offices, and more). Stored in the `environment` field as before.

OOH format selections are keyed by classification and bucket slug (`digital:screens`, `digital:network`, `digital:transit`, `classic:screens`, `classic:network`, `classic:transit`) and stored in `inventoryTypes.formatsByType`. The buckets are drawn from the shared inventory taxonomy (`shared/inventory-taxonomy.ts`), which exposes `placementDomainForFormat()`, `isMovingTransitFormat()` and `getInventoryBucketsForClassification()` — Screens/Network stay scoped to the Outdoor placement domain, while the Transit bucket admits only moving-vehicle transit formats (§6.1.3).

**Cinema.** The Cinema tab is deliberately simple — two blocks, everything below the first driven by the operators' actual schedule:

- **Operators** — chips filtered by the countries chosen in step 2, exactly as IMS exposes them. The platform ships a curated list covering eighteen countries and eighty-six operators including PVR INOX, Cinépolis India, Carnival and Miraj for India; GSC and TGV for Malaysia; Shaw and Golden Village for Singapore; and VOX, Reel, Roxy and Cinépolis Middle East for the UAE.
- **Weekly schedule Gantt** — selecting an operator (e.g. GSC) renders its weekly schedule as a Gantt-style timeline across **all of the operator's venues in the market** (GSC appears at Mid Valley, 1 Utama, Pavilion, … — each venue is a collapsible section, not a single flagship): one row per hall/screen, bars for each film session positioned on a 10:00–02:00 time axis, with the dynamically generated ad slots marked inside each session bar (a pre-movie slot at the published start time and, in intermission markets, a mid-feature intermission slot). A day picker (Mon–Sun) switches the day, mirroring the operator "Weekly Session by Screen" reports where early matinees run Thursday–Sunday only. A free-text search matches movie title, operator name, venue and screen. Genre, film classification and slot type are offered as **filters beside the search, never as abstract buy bands** — they only narrow what is actually scheduled, so picking Horror when no horror film is playing shows an explicit "no scheduled movies match your filters" empty state rather than pretending availability exists. Hovering a session shows its exact showtimes, rating, genres, slot lengths and running days. **The Gantt is also the selection surface**: each screen row carries a checkbox to target that screen (with an "All screens" venue-level toggle), and clicking a session bar toggles targeting of that movie — all of its sessions highlight, and the selection is summarised in a bar above the chart with removable movie chips and a "Clear selection" action. No selection means "all screens & movies of the selected operators". Screen selections are firm targeting (`cinema.screens`); movie selections (`cinema.films`) are an indicative content preference, because the line-up rotates weekly (§4.3.1). The old Pre-Show/Intermission/Post-Show placement checkboxes, showtime-band buckets and stand-alone genre/rating pickers are gone — those concepts survive only as schedule filters (persisted in the same `cinema.genres`, `cinema.ratings` and `cinema.adPlacements` fields for downstream compatibility).

**Retail.** The Retail tab lists twelve formats spanning digital screens, static fixtures and one interactive unit, using searchable multi-select dropdowns grouped by Digital Retail and Classic Retail. The same formats are accessible from the Inventory Type tab's side drawer, and both UIs read and write the same underlying data. When two or more formats are selected, a budget-split section appears where the planner can distribute the channel's share of budget across the chosen formats as percentages. A "Distribute evenly" button and a soft-warning totals footer follow the same pattern as the channel-level allocator in step 2: the planner sees an amber hint when the percentages do not sum to 100%, but Next is never blocked.

All channel tabs share a design rule: format and budget data entered on a tab is preserved when the planner navigates away from step 3 and back, but is discarded on final submit if the corresponding channel was removed from step 1 before submission.

#### 4.3.1 Cinema schedules and the movie-preview layer

Cinema differs from every other channel in one fundamental way: the product a planner buys is the **environment** (operator → cinema → hall → showtime band), never a specific film. A campaign runs for weeks, the film line-up rotates weekly, and exhibitors finalise which movie plays in which hall close to showtime. So Planner deliberately does **not** let a planner "buy a movie" — the buy is expressed through operators and their schedules, with genre, classification and slot type acting only as filters over what is scheduled (§4.3 Cinema). A movie is something the planner *sees*, not something they *buy*.

What the IMS schedule feed adds is a read-only **movie-preview layer** that answers the question planners actually ask — "which movies will my ad run alongside?" — without turning a film into a line item. IMS publishes a rolling schedule of **at least one week ahead**: for each operator, cinema and hall it records the movie title, certification/rating, genre, language, screen format (2D / 3D / IMAX) and the actual showtimes. Planner ingests this feed as a **read-only** entity — the planner never edits it — and surfaces movie names on three surfaces:

- **Cinema targeting tab (Step 3)** — the weekly schedule Gantt described in §4.3 (Cinema): venues × halls × sessions × dynamically generated ad slots per selected operator, filterable by day, search, genre, classification and slot type, and doubling as the screen/movie selection surface. Screen picks are firm targeting; movie picks are an indicative content preference, never a contracted line item — a movie selection means "prefer sessions of this title while it plays", and the weekly rotation caveat below still applies.
- **Cinema / hall detail (side panel)** — a per-cinema, per-hall schedule table: movie · hall · showtimes · rating · genre · format, taken straight from the IMS feed.
- **Media Plan (Cinema)** — a per-hall screening schedule for the plan's booking window, per operator and cinema, with a **derived in-session ad window** (session slot length − feature runtime) and a fixed 2-minute pre-show, rendered as a dedicated Cinema analytics tab and Excel sheet (§10.5.3) that mirror the DOOH Schedules surface and ship in the PPTX/Excel exports alongside it. The **Cinema slide** previews the buy at deck altitude: its Featured Films block lists the **first three real movie names** from the scheduled line-up and collapses the rest into a `+N more` chip, and a **Cinema buy** stat strip (cost in the campaign currency code, impressions, CPM, average ad time per session) replaces the former ad-availability strip — the granular screens/sessions/pre-show detail lives on the analytics tab, which the slide's micro-copy points at. The PPTX Cinema slide mirrors both blocks 1:1.

Every movie-preview surface carries an **"indicative line-up, as of `<date>`"** label. Because the schedule is a rolling ~1-week window while a campaign spans longer, movie names illustrate the *kind* of audience the targeting delivers — they are **not** a contractual guarantee that a named film will be on screen when the ad runs. (Guaranteed single-film targeting could only ever be offered when the booking window falls entirely inside the known schedule window, and is out of scope for the first release.)

**Data shape.** The schedule entity is read-only and synced from IMS, keyed `operator → cinema/site → hall/screen`, each entry carrying `{ movieTitle, rating, genre, language, format, showtimes[], validFrom, validTo }`, and cached with a freshness timestamp so every preview surface can display its "as of" date and degrade gracefully when the feed is stale.

### 4.4 Step 4 — Inventories

The inventory step is where the campaign brief turns into a concrete plan. On first entry the planner is met by a **choice gate** — "How would you like to build this plan?" — with two cards: **Use AI recommendations** ("Generate a smart inventory recommendation based on your targeting, budget and dates") and **Pick inventory manually** ("Skip recommendations and choose inventory yourself from the full catalog"). The gate's caption makes the reversibility explicit: "You can switch to recommendations later at any time." The decision is stored on the campaign as a `skipRecommendation` flag (default false) so a planner who leaves and re-enters the wizard lands back in the mode they chose.

Choosing **AI recommendations** fires a request to the Recommendation Engine (§6) — a separate microservice connected to Planner — which generates a run ID and returns a scored set of inventories based on the campaign's budget, dates, channels, countries, targeting and audience signals. Planner ingests the scored list, applies each media owner's selling terms (rate card, minimum spend, availability windows) and creates schedules automatically. The planner sees the recommended plan as a ready-to-review inventory list with impressions, cost and schedule data already populated. The run ID is recorded on the campaign so the recommendation can be traced back to the engine's version and scoring model at audit time.

Choosing **manual** never calls the engine: the planner goes straight into the manual selection workspace with the full (reservation- and blacklist-filtered) catalogue. Opting back into AI recommendations later is deliberately **non-destructive**: if the planner has already made manual picks, the new recommendation run is generated without resyncing or deleting the existing selection — manual picks always survive the opt-in. Discarding manual work is a separate, explicit action: **Restore AI recommendation** asks "This regenerates the AI recommendation and discards your manual changes. Continue?" before deselecting everything and regenerating.

Two filters exclude inventory from the recommendation pool before the engine ever sees it:

- Any inventory under an active reservation by a different tenant is hidden — see §9 for the full reservation contract. This guarantees two buyers can never be steered towards the same screen at the same time.
- Any inventory blacklisted by its owner from the chosen execution path (open auction or guaranteed) is hidden.

This is the wizard's first cross-feature dependency on Reservations. The pool the planner browses — whether recommended or manually selected — is a function of who else is mid-deal at this exact moment.

**The Step 4 main view.** Once the engine returns, the planner sees a deliberately compact, summary-first surface — not a 3,000-row inventory table. The intent is that an experienced planner can read, sanity-check and submit the recommended plan without ever opening the inventory list. Three blocks stack vertically:

- **Auto-plan banner** — confirms the engine's run, shows how many inventories were picked and how the budget was distributed across channels, and exposes two CTAs: "View Inventories" (read-only side panel) and "Manual Edit" (full-page drawer). The banner is the only place the planner sees the run ID and the engine's confidence summary.
- **Plan Summary card** — the central artefact of the step. A compact 12-tile metric grid across the top, each tile carrying an info-icon tooltip with the full definition: **Impressions, Reach, Frequency, Ad Plays, Avg CPM, eCPM, CO₂ / Play, SOV, SOT, Inventories, Cost, Remaining**. SOT renders as an airtime figure in hours and minutes ("45H 30M") computed as `Ad Plays × 15s spot length`, never as a percentage — the percentage stays inside the tooltip for context. The CO₂ tile shows the per-play value (`Total CO₂e ÷ Ad Plays`) with the plan total and tracked-plays coverage in the tooltip. The **Cost** tile is the labelled headline for planned media spend; its mini progress bar fills against the campaign budget and turns red when the plan exceeds it. **Remaining** shows the leftover budget after the current plan. Underneath the grid sits a small SVG reach-build curve and a grouped breakdown table the planner can re-pivot through a single dropdown: All Inventories, City, Media Owner, Type, Venue Type. Each breakdown row shows count, impressions, reach and cost so the planner can see at a glance whether the plan is over-indexed on a city, owner or venue. Tooltips and tile definitions are kept semantically aligned with the campaign detail Performance tab so the planner and the post-submit buyer read the same numbers using the same vocabulary. The metrics degrade rather than block: when the external MW Measure reach-and-frequency service is unreachable, the platform falls back to schedule-stored impressions and reach sums — an upper-bound estimate, since summed per-site reach ignores cross-site audience overlap — so the plan pages keep rendering with indicative numbers instead of failing on a third-party outage.
- **Plan Map card** — an always-on Mapbox panel (no expand/collapse) with markers coloured by channel (Digital, Classic, Transit, Cinema, Retail, Radio, Mobile, Experiential — with billboard / street-furniture rolled into the Classic colour for legend consistency), a colour legend showing only channels actually present in the plan, and an off-by-default **"Audience heatmap"** toggle that overlays audience mobility (footfall) data — "Shows where your target audience moves, based on audience mobility (footfall) data. Darker red areas indicate higher footfall." Enabling it reveals a time-of-day filter (All day, Morning, Afternoon, Evening, Night) and a Low → High intensity legend running from cold transparent blue to hot red. The layer is served by a server-aggregated mobility API keyed on the campaign's country (with the point count capped so the map stays responsive) and renders beneath the map's symbol layers; the same heatmap is available on the geo-fencing map in Step 3, so audience movement informs both where the planner draws fences and which inventories they pick. An explicit empty state ("No mobility data available for this area") and a non-blocking error state ("Could not load mobility data") keep the map usable when the data is thin or the service is down. The map serves as a sanity check on geographic dispersion and channel mix before the planner commits.

**Lazy inventory metadata.** Inventory scoring and metadata is heavy. The Step 4 page renders the auto-plan banner, summary card and map without computing per-inventory recommendation scores; that work is gated until the planner opens **View Inventories** (a read-only side panel that shows the plan as a list with the same inventory side-panel used elsewhere in the platform, §5) or **Manual Edit** (the editable drawer). This keeps the initial paint of Step 4 fast, especially for plans with hundreds of selected inventories.

**Manual Edit drawer.** The drawer is the planner's complete inventory workspace and opens pre-populated with the current plan — there is no "Start fresh / keep recommendations" interstitial inside the drawer, because the AI-vs-manual decision was already made at the step's choice gate and the drawer simply edits whatever selection that decision produced. It is a full-screen workspace with a map on the left and the inventory table on the right; filters and bulk import are right-side slide-over panels rather than a permanent rail, so the table has the full width when the planner is not filtering. The drawer contains:

- **Header search + actions.** A wide search box runs across inventory **name, address, city, state and media owner**. Beside it are a **Filters** button (opens the filter slide-over, with a badge counting active filters) and a **Bulk import** button (opens the import slide-over).
- **Filters slide-over (right panel).** A searchable, collapsible panel. Each list filter is a multi-select with its own search box and a per-filter clear, so long option lists (e.g. media owners) stay usable. Filters provided: **country → state/region → district** (cascading — narrowing the country prunes the available states, narrowing the state prunes the districts), **city**, **media owner**, **inventory type**, **venue type**, **classification** (Digital/Static), **resolution**, **mode of operation** (loop/spot), **size**, and **deal type** (PG · Programmatic Guaranteed, PD · Preferred Deal, OA · Open Auction, PMP · Private Marketplace). A **Programmatic enabled** tri-state (Any / Yes / No), a **minimum recommendation score**, and a **goal-aware cost range** complete the set; a Reset clears everything but the search. The filters open pre-filled from the campaign's current targeting (cities and venue types), so the planner starts from the brief rather than a blank slate.
- **Goal-aware cost range.** The cost range filter (and the cost column) is **CPM** when the campaign goal is impression- or reach-based, and **CPS** (cost per spot) when the goal is spot/SOV/ad-play based, with a short caption explaining why. The default sort and default cost column follow the same rule.
- **Interactive map.** A Mapbox panel with the candidate pool plotted; selected inventories are visually distinguished (larger pin with a highlight ring) from unselected ones, and pins carry a white typed glyph per inventory/venue type (digital screen, transit, billboard, street furniture, airport). A **"Sync list to map view"** toggle ties the table to the map viewport: when on, panning or zooming the map filters the table to inventories within the visible bounds (and a badge on the table notes it is limited to the map view).
- **Inventory table.** One row per inventory with a checkbox, score badge, name and address, and a configurable set of columns. Sortable by every numeric/text column. A **column chooser** popover lets the planner show/hide columns (score, type, venue, classification, resolution, mode, size, deal, programmatic, city, state, district, media owner, CPM, CPS, impressions, reach, frequency, cost); the table scrolls horizontally when many columns are shown. A "Select all visible" toggle bulk-selects the current filtered view, and selected rows are tinted.
- **Bulk import slide-over (right panel).** Accepts either a CSV file drop or pasted reference IDs in one textarea, **one ID per row** (the format you get pasting a column straight out of Excel); it still tolerates commas, semicolons, tabs and whitespace, and CSV files contribute their first column (skipping an `id` header row). A "Validate" action categorises every parsed ID into three buckets — **valid**, **outside current targeting** (mismatch), and **unknown**. Mismatches render as an inline checklist with a select-all toggle and a per-row reason list ("Location \"…\" not in targeted geography", "Venue type \"…\" not in targeted venues", "Format \"…\" not in targeted formats"); only checked rows are added to the selection. This forces the planner to consciously confirm any out-of-targeting inclusions rather than silently overriding the brief.
- **Footer summary.** Running selected count and impressions, a **Goal readout** beside Impressions that projects the campaign's goal metric (impressions, reach, ad plays or share-of-voice) and colours it with the same 10/15/20% bands as the budget, applied to the absolute difference from target in either direction (within ±10% green, more than 10% off yellow, more than 15% orange, more than 20% red, with an explicit “X% over/under” annotation so over-delivery is as visible as a shortfall), a **Cost vs budget** block whose figure and progress bar change colour as the selection runs over budget — neutral up to 10% over, **yellow above 10%, orange above 15%, red above 20%** — and a **"Budget by channel"** popover that reconciles the Step 2 per-channel budget split against the cost of the current selection, rendered above the footer. Cancel and Save complete the footer.

**Bidirectional targeting sync.** Because the filters open from the campaign's targeting, the planner can also push refinements back. On Save, if the geography/venue filters differ from the campaign's targeting, a confirmation dialog shows exactly which dimensions changed (cities, countries, venue types) and offers **Apply & save** (copy the filter changes back into the campaign targeting so the rest of the wizard stays in sync) or **Keep targeting as-is** (save the selection only). When a proposal has already been generated for the campaign, the dialog adds a warning that changing targeting may require regenerating the proposal and re-running approvals. The merge back into targeting is conservative — it only toggles/adds the changed geography targets and replaces the venue (`environment`) list, leaving POIs, excluded areas, demographics and other targets untouched.

On save the drawer closes and the planner returns to the Step 4 main view with the updated plan, which is now treated as manually-edited: subsequent upstream changes (budget, dates, targeting) re-run the engine in the background but do not overwrite the planner's selections — instead the auto-plan banner shows a subtle "Targeting changed since your manual edits — apply the new plan?" prompt that the planner can accept or dismiss. The "Restore recommended plan" control appears **only after** the plan has been manually edited — an untouched auto-plan has nothing to restore. From Step 4 they continue to Step 5 (Optimization) as normal.

**Availability flags on the browse and selected lists.** Step 4's inventory surfaces carry the IMS-synced availability verdict (§18) on every card: inventories the engine found only partially free for the flight show an amber **"Limited availability"** badge (with the exact coverage spelled out, e.g. "Limited availability for your dates: 3/10 days available"), and inventories with essentially no availability show a red **"Unavailable for your dates"** flag — these are excluded from auto-selection but remain visible in the browse list so the planner understands *why* they were not picked, and can still consciously include them. Alongside the badges, a shared **availability-sync warning** appears whenever the availability data itself is suspect: "Availability data may be outdated" when the last IMS sync is more than six hours old, or "Last sync failed" when it errored, each with a tooltip carrying the last-synced timestamp and the failure detail. Fresh, successful data shows no warning — silence is the trust state.

**Goal banners on the inventories tab.** Above the inventory list, the step continuously compares the plan's projected delivery of the primary goal against the goal target and speaks up in three situations. When the plan is set to massively over-deliver (projected at three times the target or more) an over-achievement banner reads "Your plan is set to deliver about {X} impressions — well above your goal of {Y}. Keep it as is, or optimize to reduce budget and target the goal only?" with **Keep plan** (dismiss) and **Optimize to goal** (trim the selection down, keeping the highest-value inventories, until the projection roughly matches the target — this marks the plan manually edited). When the budget is too small to reach the goal, a shortfall banner reads "Your budget covers about {X} of your {Y} impression goal. Add budget, lower the goal, or continue with the shortfall." with **Adjust budget** and **Lower goal** (both return to step 2) and **Continue** (dismiss). When nothing matches the targeting at all: "No inventories match your current targeting. Loosen the filters or change the flight dates to see options." A plan that meets its goal within budget shows no banner — silence is the success state.

**Paste-to-select and workspace ergonomics.** The bulk-import paste path also drives a deliberate merge decision: when the plan already has inventories and the pasted reference IDs resolve to different ones, a prompt asks "You pasted {N} inventories that are not in the plan. Add them to the current selection, or replace the current selection with them?" with **Add to selection** / **Replace selection** buttons, and any IDs that match nothing are reported back. The map|table split is drag-resizable so a planner triaging a long list can give the table the full width, or a geography-led planner can grow the map. The inventory detail side panel had a round of clarity fixes: **Type** shows the step-3 inventory-type bucket label ("Digital Screens", "Classic Billboards", "Classic Transit") rather than a raw classification token; **Format** falls back to the venue type when no format is on file, showing "NA" only when both are missing; **Size** renders Small / Medium / Large / XL labels (mapped from the numeric 1–4 bands) with a tooltip explaining the bands; **Operation mode** maps raw values to proper labels ("Time-based (loop)") with a tooltip whose vocabulary matches the actual values; truncated inventory names get a full-name hover plus a copy-to-clipboard button (in the panel and in map popups); the **IMS reference ID** is shown under the name instead of the internal numeric id; duplicate badges are de-duplicated; and every field label carries a plain-language hover tooltip.

### 4.5 Step 5 — Optimization

Step 5 is the operational step that turns the picked plan into a runnable campaign. It exposes two tabs and nothing else: **Schedule** (default) and **Auto-Optimize**. There is no Budget Allocation tab here — the budget split is already expressed twice upstream, once by the planner in Step 2's channel allocator and again by the Step 4 recommendation engine when it picks the inventory plan, so the planner does not re-state the same intent on a third surface. The `campaign.optimization.budgetAllocation` field is kept on the campaign as a stub (seeded with an even split across selected inventory types when the wizard initialises) so the schema, the seed data and the Recommendation Engine and Media Plan deck readers work, but the planner does not edit it directly.

The Schedule tab renders a 7-day-by-24-hour grid for every inventory in the plan. The planner can apply preset patterns — Commuter, Nightlife, Business Hours, Weekend, 24/7 — or click individual cells. The grid validates against each inventory's posted operating hours and warns (but does not block) if a slot falls outside them. Selecting cells immediately recalculates the impression, ad-play, reach, share-of-voice and share-of-time forecasts visible in the right rail. Digital figures are formatted consistently: ad plays are comma-separated whole numbers and frequency reads like "15.6x". Overlap warnings fire only on genuinely overlapping schedule segments for the same inventory — never as a blanket warning across the plan. When an inventory carries multiple schedules, each schedule shows its own impressions and reach, derived proportionally from the inventory's metrics.

**Classic inventory follows different rules in this step.** A printed billboard has no ad plays, no spot duration and no loop, so classic inventories never show Ad Plays, Duration or Spots-per-Loop — they report days-based metrics instead. Classic cannot be bought by the hour: it follows a minimum number of days from the media owner's selling terms (defaulting to seven when the owner has not published one), and a classic schedule shorter than that minimum shows a validation error naming the rule. Wherever the hourly grid would prompt for hour selection on a classic site — including "Optimise Manually" — the message reads: "Hourly selection does not apply to classic sites. Classic follows the minimum number of days set in its selling terms." Hover copy that describes start and end times (for example on the Commuter pattern) is suppressed for classic sites, since a poster has no dayparts.

**Multi-segment dated flights.** A single inventory can carry more than one dated flight segment, each with its own date window and its own 7×24 grid — so a panel can run a commuter pattern for the first fortnight and a 24/7 burst for the closing week, or go dark in the middle. Each schedule entry gains optional `startDate`/`endDate` fields (ISO `yyyy-mm-dd`, constrained to the campaign flight); an entry with no dates means the whole campaign flight (the default, so existing single-flight plans are unchanged). The view-model derives each segment's schedule type (**Loop** when every operating hour is active, **Dayparted** when a subset is active, **Mixed** across segments), its operation-hours window (earliest start → latest end), its active-day count and its segment day-count from the date range. These segments are what the Operation Details and DOOH Schedules tabs (§10.5.3) render and what the Excel export mirrors.

The Auto-Optimize tab is a single opt-in toggle. When enabled and the planner clicks Run Optimization, the optimiser tightens pacing and surfaces a forecast uplift (modelled at +25% on the impressions, reach and SOV figures) which Submit then carries through to the Media Plan deck. The toggle is off by default; nothing is rewritten without an explicit run.

---

## 5. Inventory and the OpenOOH venue tree

The inventory module is the catalogue of physical and digital ad inventory plus the hierarchical tree that classifies each inventory by venue type. Targeting at "Times Square" or "any cinema in Mumbai" requires a structured taxonomy that planners, media owners and demand-side systems all agree on. The platform follows the OpenOOH industry standard and adds Moving Walls extensions only where the standard is silent — chiefly cinema sub-locations and a handful of transit micro-types.

The selector renders a three-tier expandable tree:

| Tier 1 (top) | Examples in Tier 2 | Examples in Tier 3 |
|---|---|---|
| Transit | Airport, Bus, Rail, Taxi/Rideshare | Airport — Departures, Arrivals, Baggage Claim |
| Retail | Shopping Mall, Convenience Store, Grocery | Mall — Atrium, Anchor Store, Food Court |
| Outdoor | Billboard, Street Furniture, Wallscape | Street Furniture — Bus Shelter, Kiosk |
| Health & Beauty | Gym/Health Club, Salon/Barber | — |
| Leisure | **Movie Theatres**, Bars | Movie Theatres — Lobby, Concession, Auditorium, Corridor |
| Education | College, K-12 | — |
| Office Buildings | Office Lobby, Elevator | — |
| Point of Care | Doctor's Office | — |

Inventory appears in two places: a list view used during planning and an explore view used for top-of-funnel discovery. Each row opens a side panel with tabs that move the viewer from "what is this site?" to "what does it cost and when can I book it?". The Performance tab leads with effective cost-per-thousand-impressions instead of cost-per-day and labels its estimated cost as monthly, because the previous mixed-cadence display was the single largest source of pricing confusion in support tickets.

Every inventory carries two simple flags that govern which execution paths it is eligible for: one keeps it off the open programmatic auction, the other keeps it off the traditional guaranteed workflow. This lets a media owner participate in one channel of demand without committing to the other.

**How Inventory connects to other modules.** The catalogue is the spine of the entire product. The wizard's Step 4 (§4.4) reads the same catalogue but pre-filters out inventory currently held by another tenant via the Reservation contract (§9). The Recommendation Engine (§6) reads it through the same filter. Price Management (§8) prices every inventory line on a campaign, and Creative Assignment (§11) drops creatives onto inventory line items subject to the format / aspect / duration spec carried on each inventory record. The Inventory side panel is the same component used in Explore (§17), the wizard map and the campaign detail view, so a planner sees one consistent fact sheet wherever they encounter the inventory.

---

## 6. Recommendation Engine

The Recommendation Engine is a separate microservice connected to Planner that takes a campaign brief and returns a ranked, budget-fitting inventory plan. A planner manually building a multi-channel multi-country plan can spend a working day choosing inventories, balancing share of voice and reconciling against budget; the engine scores and allocates in seconds, giving the planner a defensible starting point they can refine through the manual editing drawer in §4.4.

The engine is triggered when the planner chooses **Use AI recommendations** at Step 4's choice gate (§4.4) — a planner who picks manual mode never invokes it, and one who opts back in later gets a fresh run that leaves their manual picks untouched. It receives the campaign's budget, dates, channels, countries, targeting criteria and audience signals, pulls the candidate inventory pool (every inventory matching those parameters and not currently held under a reservation by another tenant), scores each candidate on audience match, cost efficiency, availability, geographic spread and historical performance, and allocates the budget across the ranked list until either the budget is consumed or the candidate pool is exhausted. The result is returned to Planner with a run ID that ties the recommendation to the engine's version and scoring model for auditability.

Planner ingests the scored inventories, applies each media owner's selling terms (rate cards, minimum spend thresholds, availability windows) and creates initial schedules. The planner sees a ready-to-review plan on step 4 that they can accept as-is or edit through the manual drawer.

The engine respects the same exclusion rules as the manual drawer: held-by-others inventory is invisible, blacklisted inventory is invisible. Two parallel planners running recommendations against overlapping criteria therefore receive non-overlapping plans because the candidate pool is pre-trimmed before scoring.

**Availability-aware scoring.** The engine overlays the IMS-synced availability store (§18) onto every candidate before allocation. For each campaign date it evaluates the inventory's bookable slots — operating-hour slots for digital, whole days for classic — treating a slot as available while its booked share is below 90%. The per-inventory availability score is the share of requested slots that are free, with 80%-or-better promoted to fully available so near-clean inventory is not penalised for noise. The consequences are graded rather than binary: an inventory below 10% availability is **excluded from auto-selection** and surfaces in the browse list flagged "Unavailable for your dates" (§4.4); a partially free inventory is still selectable but annotated "Limited availability for your dates: X/Y days available"; and an inventory with **no booking data at all is assumed available but unconfirmed** — absence of evidence never blocks a plan. This is what keeps two properties in tension both true: the engine never recommends a screen that is demonstrably sold out for the flight, and a thin availability feed never silently starves the candidate pool.

### 6.1 How a media channel becomes an inventory query — the placement-domain model

The single most common query mistake is to treat a media channel as a *classification* filter. It is not. A channel is a predicate over **two independent axes**, and querying on only one of them is what lets the wrong inventory leak in.

- **Placement domain** — *where* the panel physically lives, derived from the IMS venue type (the OpenOOH Venue Taxonomy v1.1 path). The five domains are `ooh`, `transit`, `retail`, `cinema` and `venue`.
- **Classification** — *what* the panel is, a separate IMS field with two values: `digital` or `classic`.

A channel therefore resolves to **placement-domain × classification**:

| Wizard channel | Placement domain | Classification | Query meaning |
|---|---|---|---|
| Digital OOH | `ooh` | `digital` | Outdoor (billboards, urban panels, bus shelters) that are digital |
| Classic OOH | `ooh` | `classic` | Outdoor that is static/printed |
| Cinema | `cinema` | *(any)* | Leisure › Movie Theatre screens |
| Retail | `retail` | *(any)* | In-store / mall / pharmacy / fuel screens |
| Mobile / Radio / Web | *(no OOH catalogue)* | — | Served by their own surfaces, not the OOH pool |

The placement domain is read from the OpenOOH path with two deliberate rules: **(1)** `Leisure (L1) › Movie Theatre (L2)` is the cinema exception and is checked *before* the broad `Leisure → venue` fallback; **(2)** `Outdoor › Bus Shelters` is `ooh`, **not** transit — only a screen *inside* a vehicle or *at* a transport hub (airport, rail, metro, taxi) is `transit`. Everything that is not Outdoor, Transit, Retail or Cinema (Health, Education, Office, non-cinema Leisure, …) collapses to `venue`.

The consequence the planner sees: selecting **Digital OOH** returns *only* digital Outdoor inventory. A digital screen in a mall (`retail`), a digital screen in an airport (`transit`) and a digital cinema screen (`cinema`) are all digital, but none of them is Outdoor, so none of them appears under Digital OOH. The same predicate governs all three selection surfaces — the auto-generated plan, the inventory side panel and the manual-edit drawer — so the planner never sees one set of candidates in the recommendation and a different set when they open the drawer.

#### 6.1.1 What the planner experiences — channel first, venue second

Selection happens in an order, and the order is deliberate. The planner chooses the **media channel(s) first** (Step 1 of the wizard) and only *later* narrows the plan by **venue type** and other targeting (the targeting and media-selection steps). Those two choices are not equal partners — they combine as a logical **AND**, with the channel acting as the **hard outer boundary**:

1. **Channel sets the universe.** Picking *Digital OOH* fixes the pool to digital Outdoor inventory and nothing else. This is the widest set of panels the plan can ever contain.
2. **Venue / targeting narrows within that universe.** A later choice — environment, city, audience signal or venue type — can only ever *remove* panels from the universe. It can never pull back in a panel the channel already excluded.

The practical, sometimes surprising, consequence: **if a later venue choice points outside the chosen channel's domain, the result is empty — and that is correct, not a bug.** Example: with *Digital OOH* selected, then choosing an **airport** environment returns nothing, because airport screens are `transit`, not Outdoor. The platform does **not** quietly widen the channel to satisfy the venue; to book airport screens the planner must change or add the appropriate channel. An inline note explains this whenever a venue/channel combination yields zero candidates.

The same rule runs in reverse. If the planner has already built a plan and then goes **back and changes the channel**, every selected inventory that no longer matches the new channel is **automatically dropped** — from the recommendation, the side panel, the plan map, the cost metrics and the final submit payload alike. An out-of-channel panel can never be carried forward by accident.

#### 6.1.2 How the engineering team queries it

The filter order mirrors the planner's mental model — channel boundary first, targeting refinements second — and lives in one module, `shared/placement-domain.ts` (`resolvePlacementDomain`, `resolveClassification`, `CHANNEL_PLACEMENT`, `inventoryMatchesChannel`, `filterInventoryByChannels`):

```
pool = allInventory
// 1. Channel boundary (placement-domain × classification) — the outer limit
pool = filterInventoryByChannels(pool, selectedChannels)
// 2. Targeting refinements — these only ever narrow the boundary, never widen it
pool = pool.filter(matchesGeography(targeting.locations))
pool = pool.filter(matchesEnvironment(targeting.environment))   // venue type
// 3. Availability / exclusion
pool = pool.filter(notHeldByOtherTenant && notBlacklisted)
// 4. Score, then allocate budget across the ranked list
plan = allocate(score(pool), budget)
```

Two invariants the engine must uphold:

- **Channel is applied before scoring**, on every selection surface (auto-generated plan, inventory side panel, manual-edit drawer), so the candidate set is identical wherever the planner looks.
- **Selection is re-validated on channel change.** Whenever `selectedChannels` changes, the kept selection is intersected with the channel-scoped pool — `selectedIds := selectedIds ∩ ids(filterInventoryByChannels(all, selectedChannels))`. This is what guarantees an out-of-channel panel cannot survive in metrics or the submit payload after the planner edits the channel.

When the selected channels carry no OOH catalogue at all (e.g. only Radio and Web), `filterInventoryByChannels` returns the pool unchanged rather than silently emptying the plan, leaving those channels to their own surfaces.

#### 6.1.3 The Step 3 Inventory Type picker follows the same rule

The same placement-domain rule governs the **format picker** the planner sees in Step 3 (Targeting → Inventory Type), not just the candidate query. Under the **Digital OOH** and **Classic OOH** channel groups the picker lists **only Outdoor-domain formats**. Formats whose placement domain is transit, retail, cinema or venue are excluded by design:

- **Cinema** screens (Cinema Screen Ad, Cinema Lobby Screen, …) appear only under the **Cinema** channel, which has its own operator / showtime model — never under Digital/Classic OOH.
- **Stationary transit** formats (airport, station, platform, terminal, escalator, turnstile screens and posters) are not shown under OOH; those environments are reached through the Venue Types selector in section 3 of the Inventory Type tab (§4.3).
- **Venue** formats (gym, hotel, bar/restaurant, stadium screens) are likewise excluded — they are not Outdoor.

One deliberate exception: **moving-vehicle transit** formats (bus, taxi, train, tram, truck, ferry screens and wraps) surface under the OOH channels as the dedicated **Digital Transit / Classic Transit** buckets, gated by `isMovingTransitFormat()`. This removes the earlier overlap where, for example, a *Cinema Screen Ad* could be ticked under **Digital OOH → Digital Screens** while *Cinema* was also selectable as its own channel — the same inventory surfacing under two channels. The picker resolves each format's domain via `placementDomainForFormat()` (the taxonomy-side mirror of `resolvePlacementDomain`), and `getInventoryBucketsForClassification()` groups each OOH channel into **Digital Screens / Digital Network / Digital Transit** (or **Classic Billboards / Classic Network / Classic Transit**): Screens and Network stay filtered to the Outdoor domain, while the Transit bucket admits only moving-vehicle formats.

---

## 7. Approval workflows

Approvals exist because a booking that involves an advertiser, an agency, an internal trading desk and one or more media owners cannot be activated by any single party — each has a discrete sign-off responsibility that has to be recorded for finance reconciliation and dispute resolution. The platform ships a **two-tier** workflow: a plan is signed off by the creator's company and then by each media owner on it. Folding the sign-off into the creator's company keeps the hop count low for the 95% of plans that aren't special-handling cases, without losing the audit trail.

**Diagram —** [Two-Tier Approval + Execution Handoff](https://miro.com/app/board/uXjVGEva6dA=/) (open in Miro — frame "Two-Tier Approval + Execution Handoff")

### 7.0 How a plan starts moving — the Submit Plan button

A plan sits in **Draft/Planned** while the creator is still building it, and moves to **Negotiating** the first time negotiated prices are saved (§8.2). Pressing **Submit Plan** on the Campaign Detail page is the single trigger that opens the approval workflow — there is no separate "start review" or "request approval" entry point. On submit the campaign status transitions **Draft/Planned/Negotiating → Reviewing**, the two approval routes are created, and Tier 1 enters **In Progress**. The button is rendered only for the campaign creator and only while status is Draft, Planned or Negotiating; everyone else sees the read-only state.

### 7.1 The two tiers and what each one means in practice

| Tier | Actor | Authority source | Action surface | Pass condition | Block condition |
|---|---|---|---|---|---|
| 1. Internal Company Approval | Anyone in the **creator's company** who carries the `canApproveCampaigns` flag (granted in Admin Console). The creator themselves may self-approve when they hold the flag. | `users.canApproveCampaigns` set per-user in Admin Console | Approve / Reject / Send back with comments | A single approve from any flag-holder in the creator's company | Reject from any flag-holder. No flag-holder in the creator's company → Tier 1 stalls until Admin Console grants the flag to someone |
| 2. Media Owner Approval | Each media owner with inventory in the plan. Independent per owner. | Owns at least one inventory line in the plan | Approve / Counter Offer — per-owner row | All media owners have approved the latest prices for their inventory — an owner with **no action for 72 hours is auto-approved** (reminder sent before the deadline; the approval is logged as automatic) | Any owner has not yet responded → tier shows as **Partial**. A counter does not reject the campaign and does not change its status — the campaign stays **Reviewing**; that owner's per-owner state becomes **Countered** and only its rows unlock |

The Tier-2 design is the most important detail. With three media owners on a plan — say PVR INOX, GSC and a billboard owner — the tier tracks each owner separately. The tier is **Approved** only when all three have approved. If GSC declines, the tier shows **Partial** with a flag against GSC's row, and the planner can swap out the GSC inventory through Modify Plan. The swap removes GSC's row from the tier entirely. If the planner instead replaces GSC with TGV, TGV's row is added to Tier 2 in **Pending**, and the campaign waits on TGV's response without re-asking PVR INOX or the billboard owner who already approved.

**What the approval drawer shows.** The approval side panel renders exactly two stages on its timeline — **"Company approval"** (stage 1, sign-off within the planning company) and **"Media owner approval"** (stage 2) — never the older three-stage sequence. Plans created before the two-tier consolidation may carry legacy stage names (platform review, agency acceptance, internal review) in their stored data; these are mapped onto the two-stage timeline for display, with no data migration. For media-owner-created plans the drawer shows the single "Media owner approval" self-approval stage.

**What a media owner sees is scoped to themselves.** When the viewer is a media owner on the plan, the drawer's Media Owner stage is resolved from **that owner's own proposal only** — they see their own pending/approved/countered state, not a roster of the other owners' progress. The buyer-side view keeps the full picture (per-owner rows and the Partial roll-up); the seller-side view is deliberately narrower, because one media owner's negotiation posture is commercially sensitive information the others have no business reading. The same scoping applies in the Plan Approval inbox (§7.8): a media-owner row carries only the viewer's own proposal summary, and the plan's total budget is withheld (returned as null by the server, not merely hidden by the UI).

**Self-approval at Tier 1 is allowed by design.** A senior agency planner who already has approval authority does not need a colleague to rubber-stamp their own plan — they click Approve themselves and Tier 1 closes in one step. The audit trail records the self-approval explicitly so finance can spot the pattern if it becomes a control concern.

**Media-owner-led plans collapse to one tier.** If the campaign creator's company is itself the media owner of every inventory in the plan, Tier 2's approval is implicit (the creator already owns the inventory), and the existing `checkSelfApprovalScenario` path skips Tier 2. Submit Plan in that case effectively requires only Tier 1.

### 7.2 The three security guards that run behind every approval click

| Guard | What it prevents | Why it matters |
|---|---|---|
| **Same-company gate (Tier 1)** | A user from a different company cannot approve another company's plan even if they themselves carry `canApproveCampaigns` | The flag is global on a user record but the gate compares `actor.primaryCompanyId === creator.primaryCompanyId` so the authority is scoped to the actor's own company |
| **Inventory-scope gate (Tier 2)** | A media owner whose inventory is not in the plan cannot click Approve, even if they're a media owner in good standing | An owner of Mumbai cinema screens cannot accidentally approve a Delhi billboard portion of the same campaign |
| **Tenant gate** | Switching tenant in another browser tab does not retro-grant approval rights | Every authorisation is checked against the active tenant on the request itself, not the session that opened the approval panel |

### 7.3 Re-approval — what triggers it, what re-fires, what does not

Re-approval is the most-asked question about this module. The rule is precise:

| Field changed on an Approved campaign | Tier 1 re-runs? | Tier 2 re-runs? |
|---|---|---|
| Budget | Yes | Yes — only the affected owner row |
| Dates | Yes | Yes — all owner rows (because availability windows may shift) |
| Targeting (countries, demographics, signals) | Yes | Yes — all owner rows |
| Inventory swap (add or remove a line item) | No | Yes — only the new owner row; previous-owner row is removed |
| Price on a single line item | No | Yes — only the affected owner row |
| Creative (same-spec swap) | No | No — does not re-fire |
| Creative (different spec or duration) | No | No — does not re-fire (creative spec lives on the line item, not the approval) |

The lock banner across the detail view comes off only for the tiers and rows that re-run.

### 7.4 What approval unblocks elsewhere

Reaching **Approved** is the start-state of three other things:

- Every reservation tied to the campaign flips from **Reserved** to **Booked** automatically (§9), removing the expiry countdown and locking the inventory for the campaign window.
- Creative assignment (§11) becomes possible: drag-and-drop is enabled, the bulk-assign button activates, and the dashboard's Creative Status Tracker begins reporting "missing" rows.
- Statement Builder (§12) starts including the campaign in the next cycle's draft for the appropriate tenant.

Reaching Approved also fires the **execution handoff** described in §7.5. A rejection at any tier instead releases all reservations, closes pricing read-only, and removes the campaign from any draft statement.

### 7.5 Execution handoff — what happens the moment Tier 2 closes

The instant Tier 2 closes (all media owners approved, or the single self-owned tier auto-closes), the platform writes an append-only handoff row and pushes one **line item per media owner** to a destination system. The destination is picked by the inventory class:

| Plan composition | Destination | Line-item type by default | Why |
|---|---|---|---|
| All inventories digital (DOOH/screens/transit/network) | **Influence** (OMS for digital workflow) | **Direct / Standard** | Influence is the system of record for digital direct-sold inventory; the Standard line item is the default booking unit per the Influence PRD |
| All inventories classic (static billboards, print, painted, experiential, radio) | **OMS** (the operational order-management system for traditional OOH) | **Direct / Standard** | Classic inventory does not run through Influence — it is fulfilled by the field-ops crews tracked in OMS |
| Mixed digital + classic | **Both** — Influence receives the digital lines, OMS receives the classic ones | Direct / Standard on each side | The split is automatic at the line-item level; the campaign remains a single record in Planner |
| Plan has the **RFD flag** (§7.6) | **Activate** (DSP) | **Programmatic Deal** | RFD plans skip the Influence Direct path because the buyer wants the deal exposed to a DSP rather than booked as guaranteed |

The handoff is recorded in the `execution_handoffs` table — one row per handoff event, with the per-media-owner line-item snapshot in JSON — and surfaced on the Campaign Detail page as a **green** banner (Direct/Standard handoff) or a **purple** banner (RFD/Activate handoff). The banner enumerates each line item: media owner name, inventory class, destination system, and line-item type. Counterparties see the same banner (no fee disclosure, just the routing fact).

The classification rule is keyword-based on `inventory.type`: tokens *digital, dooh, screen, transit, network, programmatic* mark a line as digital; everything else is classic. This rule is intentionally permissive — Planner does not own the canonical taxonomy, IMS does (§18) — and is a one-line change when IMS publishes its v2 venue-class enum.

**The one exception — media-owner-led plans defer the handoff.** The automatic-at-approval handoff described above applies to agency-, advertiser- and internal-led plans. When the plan was *created by a media owner* (creator role `media_owner`, or the creator's primary company `businessType = media_owner`), approval does **not** auto-fire the handoff. Instead the plan waits for the media owner to open the **Execution Plan** workbench (§7.9), review and fine-tune the line items, and push them by hand. This is because a media-owner-led plan is the one case where the seller is also the operator: they want to set purchase types, floor rates and dayparts deliberately before anything reaches Influence or OMS, rather than have the platform guess and fire on their behalf. Everyone else keeps the automatic handoff.

### 7.6 The two ways to switch a plan to programmatic

The default execution path is Direct/Standard. Two alternative paths exist when the buyer prefers a programmatic deal:

**Path A — Comments to the media owner.** The buyer leaves a comment on the campaign (§10.3) tagging the relevant media-owner company. The MO sees the comment in their tenant view and changes the line item to Programmatic Deal in Influence at their end. This is the lightweight path and the one used for one-off conversions where the buyer doesn't have a DSP seat.

**Path B — Request for Deal (RFD).** The buyer fires **Request for Deal** from the campaign row's Actions menu (or from the same action inside the Campaign Detail page — both surfaces hit the same endpoint). This sets `campaigns.rfdRequested = true` and, on final approval, routes the handoff to **Activate** instead of Influence Standard. In Activate the buyer (or their internal trader) configures the bidding strategy and points the deal at their DSP seat. The MO receives the RFD inside Influence as a deal request and either accepts it (deal goes live, buyer's DSP picks it up) or counters it. If the buyer has no DSP at all, Activate falls back to a Standard line item.

**Where the action lives, and when it is hidden.** RFD is available on plans in `draft`, `planned`, `reviewing` or `rejected`. It is hidden on `approved`, `active`, `paused` and `completed` because by that point the plan has already been routed to Influence/OMS as Direct/Standard line items — flipping the routing flag after the fact would orphan those line items and create a deal with no upstream bookings to back it. If the buyer wants to convert an already-approved plan to programmatic, the path is "duplicate plan, set RFD on the duplicate, re-approve". The Actions menu shows the disabled item with a tooltip explaining this rather than removing it entirely, so the workflow is discoverable.

The RFD action is gated by **three checks**, all enforced server-side:

| Check | Source | Failure response |
|---|---|---|
| Caller owns the campaign | `campaign.userId === req.user.id` | 403 "Only the campaign creator may request a deal" |
| Caller has Activate access | `users.hasActivateAccess` set in Admin Console | 403 "Request-for-Deal requires Activate access. Ask an Admin Console administrator to grant hasActivateAccess on your account." |
| Caller's company is **not** a media owner | `companies.businessType !== 'media_owner'` | 403 "Media-owner-created plans always use Direct/Standard line items. RFD is reserved for agency, advertiser, and internal/partner plans." |

**Why media owners cannot use RFD.** A media-owner-led plan is the exception in OOH workflows: it usually exists because the MO has a direct relationship with an advertiser whose agency is offline (e.g. the agency does not use Planner and shared the brief over email). When the MO approves the plan it goes straight to Influence as a Direct/Standard booking — there is no upstream DSP-seated buyer to route a programmatic deal to. Allowing the MO to flip their own plan into RFD would create a deal with no taker on the DSP side. The button is therefore hidden for MO users and the server would 403 even if the request were forged.

### 7.7 Admin Console flags that drive Tier 1 and RFD

Two boolean flags on the user record gate the Tier-1 actor pool and the RFD button respectively:

| Flag | Default by role | What it controls | Set in |
|---|---|---|---|
| `canApproveCampaigns` | Internal: true · Media owner: true · Agency: false (per-user grant) · Advertiser: false (per-user grant) | Whether the user can act on Tier 1 of any plan their company creates | Admin Console → Users → Edit → Approval Authority |
| `hasActivateAccess` | Internal: true · Agency: per-seat (depends on whether the agency holds a DSP seat) · Advertiser: per-seat · Media owner: **always false** (cannot be granted) | Whether the user can fire the Request for Deal button | Admin Console → Users → Edit → Activate Access |

Both flags are stored on `users` and surfaced on the `/api/user` response so the UI can hide buttons that would 403. The server is the source of truth — the client-side hide is best-effort UX, not a security boundary.

### 7.8 Plan Approval inbox — the bulk-approval working surface

Tier-1 approvers — internal ops leads at the agency or platform — typically have a dozen plans waiting on their review at any given moment. Walking into each campaign one at a time to click Approve is the kind of repetitive work that produces either approval fatigue (the user starts rubber-stamping) or queue starvation (plans sit for days). The **Plan Approval** page, surfaced as a top-level item in the left sidebar directly under Campaigns, exists specifically to make Tier 1 a bulk operation.

The page loads through a **single batched inbox endpoint** scoped to the active tenant — one request returns every waiting plan with its current stage, the viewer's eligibility and the per-plan summary already computed, rather than fanning out one approval-status call per row (the fan-out pattern collapsed under a queue of a few dozen plans). A row is **actionable** only when four things hold at once: the plan's first non-terminal stage is In Progress, the viewer holds the authority for that stage, the campaign is in Reviewing, and there are no unaccepted price changes outstanding — the server computes this `canAct` verdict, so the client never has to re-derive approval rules. Non-actionable rows are listed but not selectable, with the reason surfaced: a "Tier 2 — Media Owner" badge for plans that have already cleared Tier 1, or a "Not your authority" subtitle when the viewer is outside the approver pool.

What a row contains depends on who is looking. A buyer/agency viewer sees the full picture: the plan's budget and a chip per media-owner proposal (status, inventory count, base media cost, open-counter flag) so the Partial state of Tier 2 is legible at a glance. A **media-owner viewer gets the redacted seller view** (§7.1): only their own proposal's summary, the creator's name, and no plan budget — the redaction is applied server-side in the inbox payload, not by the UI.

For every actionable row the user gets a checkbox. A header checkbox selects all actionable rows. The bulk-approve button at the top right shows the live count and, on click, fans out one `POST /api/campaigns/:id/approve-stage` per selected row with `{stageId, action: "approve", comments: "Bulk approved from Plan Approval inbox"}`. The audit trail (§7.5 history table) carries the explicit "bulk-approved" comment so a later auditor can see which approvals were individual decisions and which were swept through the inbox. Each per-row approval is independent — a single failure (say, a self-approval block on one row because the user happens to also be the creator of that one plan) does not abort the rest. The toast at the end reads "*X* approved · *Y* failed" so the approver knows exactly how many slipped through.

The inbox is also the counterparty's working surface: **external plans** — plans created by another company on the tenant's inventory — appear here while they sit in Reviewing awaiting this company's Approve / Counter Offer decision (§8.4), each carrying its External plan badge.

Each row also surfaces three things at a glance: an **RFD** badge if the plan has `rfdRequested = true` (so the approver knows that approval will route the plan to Activate, not Influence Standard), the date range, and the budget. An **Open** link in the rightmost column jumps to the per-campaign approval page (`/campaigns/:id/approval`) for plans that need a deeper inspection — typically used when the approver wants to see the inventory mix, the negotiation history, or the change log before approving.

**Empty state.** When there is nothing waiting on the active tenant's review queue the page collapses to a single, deliberately calm card in the centre of the working area: a faded UserCheck glyph, the headline "**Inbox zero.**" and the line "*No plans are currently waiting for approval.*" There is no call-to-action, no list scaffolding, no fake placeholder rows — the absence of work is itself the message. This is the steady-state the page is designed to be in most of the time, and it is the goal an approver should be optimising for. If the user lands on the page without `canApproveCampaigns` on their account they additionally see an amber notice above the (still-empty) table explaining that they can browse the queue but cannot act on it, with a pointer to the Admin Console.

### 7.9 Execution Plan — the media-owner line-item workbench

The Execution Plan is where a media owner turns a campaign into the exact line items that get pushed to the downstream booking systems. Where §7.5 describes the *automatic* handoff that fires for agency- and internal-led plans, the Execution Plan is the *deliberate, reviewable* version of that same handoff for plans the media owner runs themselves. It is reached from the single **Execution Plan / Execution Status** button on the Campaign Detail page and from the **Execution Plan** item in the campaign Actions menu. The entry point is available for a campaign in **any status** and for every role that can reach the campaign, and it is the surface where the deferred handoff (§7.5) is finally fired.

**Who can open it, and in what mode.** Access is decided server-side by a single gate. Any caller who can already reach the campaign (the same approval-access check used across the other campaign surfaces) may open the Execution Plan on **any status**; the gate then decides edit-vs-read-only purely by whether the caller is a media owner:

| Caller | What they get | Why |
|---|---|---|
| **Media owner** (role `media_owner`, or primary company `businessType = media_owner`) | Full read/write Execution Plan | They are the operator — they set the line items and fire the push |
| **Everyone else** with approval access — agency, advertiser, **and internal / admin** | Read-only **Execution Status** (`readOnly = true`) | Transparency into how the plan is being executed, without any edit, reset or push rights |

For a read-only viewer the GET endpoint generates the baseline plan on the fly **without persisting it**, so a non-media-owner simply looking at the status never mutates the media owner's working state. The page retitles itself "Execution Status", shows a "Read-only" badge and an info banner, and every edit affordance (purchase-type and rate inputs, inventory moves, schedule editing, per-line and global Reset, Push and Retry) is hidden. The three write endpoints (`PUT`, `POST …/reset`, `POST …/push`) additionally reject a read-only caller with `403` as defence in depth behind the hidden UI.

**How the plan is generated.** The engine converts the approved campaign into line items grouped by where they have to go:

| Inventory class | Grouped by | Destination | Default purchase type |
|---|---|---|---|
| Classic | media owner | **OMS** (`destination = oms`) | **Order** (`order`) |
| Digital | media owner **and** recommended purchase type | **Influence** (`destination = influence`) | `direct` for non-programmatic; for programmatic digital the engine recommends **PMP** when booking pressure ≥ 0.82, **Preferred Deal** when ≥ 0.6, otherwise **Guaranteed** |

Each line item carries its inventories, a recommended purchase type, a floor/rate, a 7×24 daypart schedule grid, and a baseline snapshot (`baselineFloorRate`, planned cost, planned impressions, baseline schedule hours) captured at generation time so the platform can both reset the line and flag drift. The engine builds each inventory's schedule grid by OR-merging whatever was authored in the Optimization step (§4.5); if nothing was authored, it fills the inventory's operating window derived from its operating start/end hours and operating days.

These programmatic options (PMP, Preferred Deal, Guaranteed) are **Influence deal types** the media owner chooses *inside* their own booking system at execution time. They are a different axis from the buyer-led **Request for Deal** path of §7.6, which re-routes an entire plan away from Influence to **Activate / an external DSP**. RFD remains unavailable to media-owner-led plans (§7.6) — the §7.5/§7.6 statement that media-owner-led plans always route to *Influence* (rather than Activate) still holds; §7.9 only governs how the line is shaped once it is on the Influence/OMS side.

**What the media owner can change, and the guardrails.** The media owner can: change a line's **purchase type** (only within the legal set for that inventory class — classic stays `order`, digital may move between `direct`/`guaranteed`/`preferred_deal`/`pmp`), edit the **floor rate**, **move inventories** between line items whose purchase type the inventory can legally join (a digital inventory cannot drop into a classic Order line, and vice-versa — illegal destinations are disabled with an inline reason), and **re-author the daypart schedule**. Any of these edits flips the line's `isManuallyEdited` flag to `true`; a per-line **Reset** restores that line to its baseline (purchase type, rate and both schedule grids) and clears the flag, and a global **"Reset to recommendation"** discards every edit on the plan. Two guardrails run on every edit:

- **±15% tolerance.** Every line is continuously compared against its baseline. If edited cost or impressions drift more than 15% above or below baseline, the line shows a tolerance warning (cost-over / cost-under / impressions-over / impressions-under). These are advisory — they inform the operator, they do not block the push.
- **Operating-window bookability.** Schedule edits are clamped to the inventory's bookable operating window; cells outside it are greyed out and cannot be turned on.

**Editing the daypart.** Each inventory row has an **Edit schedule** action that opens a right-side drawer reusing the campaign wizard's interactive 24×7 grid (§4.5) — the same component, so the operator edits dayparts here exactly as they did at planning time. The drawer offers quick presets (Clear, Commuter, Business, Weekend, Nightlife, 24/7), shows live metrics as the operator toggles cells (scheduled hours, scaled planned impressions, booking pressure), warns when scheduled hours exceed the bookable maximum, and carries a legend. The schedule grid is **authoritative server-side**: on every save and every push the backend re-derives each inventory's scheduled hours from the grid, so a stale or tampered client-side hour total is ignored — the grid is the single source of truth and the impression/cost maths recompute from it.

**Pushing the handoff.** The global **Push** fires `POST …/execution-plan/push`, which validates the whole plan (legal purchase types, schedule availability) **before** persisting it, so an invalid plan is never stored. Three server-side guardrails must all pass before the push is even offered — the server returns a `canPush` verdict with an explicit blocking reason when one fails: the campaign must be **Approved** ("This plan cannot be pushed yet: the campaign must be approved first."), **no negotiated price may still be unsettled** ("…there are price changes awaiting acceptance." — in the §8 model campaign approval *is* the price acceptance, so this guard fires when a counter-offer is still open and the re-approval it triggers has not yet closed), and the plan must have **at least one execution line** ("…there are no execution lines. Add inventories to the plan first."). When the push is allowed, a confirmation dialog — "Push plan to execution?" — spells out the consequence ("All pending lines will be handed off to their execution systems (Influence for digital, OMS for classic). The plan will be locked and your plan will go live. This cannot be undone.") and summarises **what goes live**: execution line and inventory counts, planned cost against budget (over-budget highlighted), and planned impressions.

**The handoff is a staged, observable lifecycle, not a fire-and-forget flag.** Each line moves through **Pending Handoff → Queued → Sent → Acknowledged**, or lands in **Failed** with the downstream error, the handoff timestamp and the attempt count carried on the line. (In the current build the downstream transport is simulated — statuses advance through a stand-in dispatcher until the real Influence/OMS integration is wired up; the staged lifecycle, guardrails and retry contract described here are the product commitment that integration must honour.) The page polls while any line is still Queued or Sent and stops once every line settles, so the operator watches the handoff drain in real time. Pushes are per-line independent: a partial failure marks only the failed lines (inline error plus a **Retry** button), and a retry — confirmed via "Retry failed handoff?" — re-attempts just the selected failed lines via `retryLineIds` while leaving the acknowledged ones alone. The push event is written to campaign history as `execution_plan_pushed`, and pushing also **takes the campaign live**: as soon as the first line is handed off the campaign status flips to **Active** — but only from a pre-live state (planned / reviewing / approved / draft), so a paused or completed run is never silently downgraded.

**The buyer's window into execution.** After a successful push, the Campaign Detail page gains a **"Pushed to execution"** panel: the push timestamp, an "*X*/*Y* lines acknowledged" progress readout, an in-progress count while lines are still draining, a red failed count when any line needs attention, and a "View Execution Plan" link into the (read-only, for non-owners) workbench. Approved, active and completed campaigns also carry the Execution Plan navigation button, so execution state is never more than one click from the plan record.

**Lock and immutability.** A successful push **locks** the plan (`executionLocked = true`) and freezes the snapshot. After the lock, the workbench is read-only for everyone, and the server stops trusting any incoming `plan` payload — a retry pushes only the frozen stored snapshot, so a tampered floor rate sent on a locked retry is silently ignored. Any attempt to edit a locked plan returns `409`. The lock is what guarantees that what the field-ops and Influence teams received is exactly what is on record.

---

## 8. Price Management and Negotiation

Price Management is the workspace where the plan creator converges on final line-item prices before submitting the campaign, and where every party sees the agreed numbers afterwards. Rate cards are a starting point, not a contract — real-world OOH prices are negotiated per line, with price differences, bonus inventory and share-of-voice changes — and the platform records every proposal so neither party can later claim a different price was agreed.

**Diagram —** [Price Negotiation Swimlane (Buyer · System · Seller + ripple to Approval / Proposal / Statement)](https://miro.com/app/board/uXjVGEva6dA=/?moveToWidget=3458764668847892607) (open in Miro)

This section is the product-level summary; the thorough developer-facing behavior spec — including the History drawer, every resolved edge case and the exact warning/acknowledgement texts — lives in `docs/price-management-specification.md` and the two must stay aligned.

There is deliberately **no Accept action anywhere in the module** — neither in the bulk bar, nor per row, nor in any drawer. **Campaign approval is the acceptance.** The working flow is: the creator updates proposed prices in the table (§8.1), opens the **Summary** drawer (§8.3), reviews the changes and the cost cascade, confirms the approval acknowledgement and saves. Saving never silently withholds the new prices from the media plan — the plan is updated immediately, but approval is always required before execution. The map view and the calendar/availability view are strictly read-only visualisations in this module.

### 8.1 The pricing table — columns, Difference and two-way editing

The heart of the page is a single pricing table with one row per inventory line and an **accordion** under each row that expands to the inventory's schedules, so a price can be reviewed and edited at either level.

**Columns.** Default columns are always visible; the columns marked *on-modify* stay hidden until the user modifies the table (applies a Difference, bonus or an impression change), at which point they appear — and their visible state is **persisted per campaign, not per session**, so every party opening the campaign sees the same extended columns and can see what changed.

| Column | Visibility | Notes |
|---|---|---|
| Inventory Name | Default | Accordion handle — expands to the schedule rows beneath |
| Date Range | Default | The line item's flight window |
| Time Slot | Default | Daypart / slot for the schedule; rolled up at inventory level |
| SOV / Ad Plays | Default | Share of voice for digital loops, ad plays where applicable |
| Media Owner | Default | The counterparty whose approval this line will need |
| Impressions | On-modify | Appears once the table has been modified |
| Bonus Type | On-modify | Bonus inventory / value-add attached during negotiation |
| Difference | On-modify | The negotiated % between Initial and Proposed price — see below |
| Monthly Rate / CPM Rate / CPS Rate | Default, goal-driven | **Monthly Rate** for classic inventory. For digital: **CPM Rate** when the campaign goal is impressions or reach; **CPS Rate** when the goal is ad plays, share of voice, or no goal is selected. Same goal-driven rule the media plan deck uses (§10.5), so the table and the deck always show the same rate type |
| Initial Price | Default | **Immutable** — always the original system-calculated price; it never resets across counters or re-submits. Only Proposed moves; the History drawer records every step |
| Proposed Price | Default | The editable negotiated price |

**Why "Difference".** A proposed price can be *higher* than the initial price, so the column is deliberately not called Discount ("−12% discount" reads as nonsense). Difference is direction-neutral: it simply expresses how far the proposed price moved from the initial price, in either direction.

**Sign convention.** Difference % = (Initial − Proposed) ÷ Initial × 100.

- Price *reduction* → **positive** Difference. Initial 5,000 → proposed 4,000 gives **+20%** (the buyer pays 20% less).
- Price *increase* → **negative** Difference. Initial 5,000 → proposed 7,000 gives **−40%** (the buyer pays 40% more).

The same convention is used everywhere the number appears — the table, the Summary drawer, the History drawer, the audit history — so a positive Difference always means "cheaper than initial" on every surface. Because Initial is immutable, the table's Difference always reads as *total* negotiated movement from the original baseline; round-over-round movement is read in the History drawer. Two special display values: a bonus line (Initial = 0) shows a **dash** (the % is undefined), and an inventory whose schedules moved in opposite directions shows **"Mixed"** instead of a misleading blended % (the accordion shows each schedule's own Difference).

**Two-way editing.** Difference and Proposed Price are two views of one fact, editable from either side:

- The user enables the Difference column and types a percentage (positive or negative) directly in the table → the Proposed Price auto-calculates: `Proposed = Initial × (1 − Difference/100)`.
- The user overrules the calculated Proposed Price by typing an exact amount → the Difference % recalculates from the entered price. After an overrule the **entered price is the source of truth**: it is stored exactly as typed, and the Difference is a derived display value (shown to two decimals). The system never re-rounds a hand-entered price to make the percentage prettier.

**Inventory level vs schedule level.** Editing a schedule row inside the accordion changes only that schedule; the inventory-level Proposed Price becomes the sum of its schedules. Editing at the **inventory level** divides the entered price **equally across the inventory's schedules** (an even split, not weighted): a 12,000 proposed price over 3 schedules writes 4,000 to each. When the amount does not divide evenly, the remainder lands on the last schedule so the schedule prices always sum back to exactly the entered inventory price. The split is deliberately equal, not weighted — the user priced at inventory level, so the system has no basis for any other distribution; an inline message tells the user this is what will happen. If the even split would overwrite schedule prices the user had hand-tuned, the system **warns at Save** (listing the affected inventories) rather than interrupting every keystroke. An inventory-level Difference % applies the same percentage to every schedule, which is equivalent.

Above the table sits a tips carousel and a status legend (Rate Card, Proposed, Counter, Approved, Declined). The instant the user selects one or more rows, the carousel switches to an action bar with bulk operations: Apply Difference, Apply Bonus, Change SOV and Clear Selection — a bulk Difference applies the same percentage to every selected row using the same sign convention.

### 8.2 Turn-based negotiation — the heart of the module

There is no per-line handshake and no internal (same-company) acceptance step. Negotiation is a turn-based loop at campaign level:

1. **Edit and Save** — the plan owner edits freely while the campaign is in Draft, Planned or Negotiating; once submitted, only the party holding the turn edits its own inventory rows (prices, differences, bonuses, SOV). Every other row is read-only. The first save through the Summary drawer moves the plan into **Negotiating**; a counterparty's save during its turn leaves the campaign in **Reviewing** and marks that owner **Countered**. History captures each saved batch.
2. **Submit Plan** — the status becomes **Reviewing**, Price Management locks for the submitter (a Reviewing plan is a locked offer accepting no off-turn changes), the proposed prices become visible to counterparties, and the approval cycle starts.
3. **Counterparty decision** — each media owner, from the approval drawer, chooses **Approve** or **Counter Offer** for their own inventory. There is no Reject-with-reason handshake; disagreement is expressed as a counter.
4. **Counter Offer** — the campaign **stays Reviewing**; the countering owner's per-owner state becomes **Countered** with a **turn marker**. Only the countering party's own inventory rows unlock; everyone else's rows stay read-only. When the countering party re-submits, the turn flips back and the campaign re-enters approval. An owner that takes no action for **72 hours** is auto-approved (with a prior reminder; logged as automatic).
5. **Per-owner approval reset** — a re-submit resets approvals **only for parties whose rows changed**. Owners whose prices are untouched keep their approval. Approval completes when every owner has approved the latest prices for their inventory.

Special cases:

- **MW-internal approval** — once internal approval is granted the campaign goes **live directly**; there is no further approval round.
- **Single-party campaigns** — a media owner building a campaign on their own inventory can submit and self-approve.

**Multiple media owners on one plan.** Approval and negotiation are sliced per owner (per-owner states: Pending → Countered / Approved): from Submit onward the campaign stays **Reviewing** for the whole negotiation — counters never flip the campaign status — and becomes **Approved** only when *every* owner has approved the latest prices for its own inventory. The **approval drawer** shows the summarised rollup (e.g. *Reviewing (2/2)*, then *Approved (1/2) · Reviewing (1/2)* or *Countered (1/2) · Approved (1/2)*); the **Plan Approval page** is the granular surface with named per-owner rows and actions. A media owner sees **only its own state** — never the rollup counts or other owners' identities. One owner countering never blocks another from approving; a re-submit resets only the owners whose rows changed; and the status chip on the Plans page carries the partial-progress readout (e.g. "Reviewing — 2/3 owners approved") for the planning side. Full rules in `docs/price-management-specification.md` §5.3a.

The price-source label below each cell ("JD · MediaHub Agency", in amber if the proposer's company differs from the viewer's, muted-grey if same-company) lets a planner see at a glance who last drove each price. An inventory-level **Approved** badge above a group of schedule rows means every schedule of that inventory has been approved through campaign approval.

### 8.3 The Summary drawer — changes, fees and the cost cascade

Clicking **Summary** opens the drawer that tells the plan's whole money story. It has three jobs: show what changed, manage custom fees (the only place fees live — they never appear on schedule rows), and walk the user into the approval flow.

**Summary of changes.** The drawer lists every line whose proposed price differs from its initial price, with the Initial Price, the Proposed Price and the Difference % — using the exact sign convention of the table (§8.1), so a line reduced from 5,000 to 4,000 shows **+20%** in both places and a line raised to 7,000 shows **−40%** in both places. The aggregate Difference row is expandable to a per-inventory breakdown showing each inventory's blended Difference %.

**The cost cascade.**

| Line | Definition | Notes |
|---|---|---|
| Media Cost | Sum of initial prices | Read-only baseline |
| ± Difference | One aggregated row: Media Cost − sum of proposed prices | Positive when the plan got cheaper overall, negative when it got more expensive. Expandable to the per-inventory Difference breakdown. **Proposed Media Cost** = sum of proposed prices |
| + Custom fees **not** included in the media plan | Each fee listed individually, added **on the Media Cost** | Percentage fees compute on the proposed media cost. Such a fee is still part of the totals — it folds into the Net Cost rather than appearing as a separate line in the client-facing plan |
| **= Net Cost** | Proposed media cost plus not-included fees | **Current vs proposed comparison happens at the proposed-media-cost level** |
| + Platform Fee | Read-only, always visible, **% of Net Cost** | Set at company onboarding; never editable in Price Management |
| + Custom fees included in the media plan | Each fee listed individually, added **on top of the Net Cost** | Percentage fees compute on the Net Cost; these fees are itemised client-side alongside the Platform Fee |
| **= Total Cost** | Net Cost + Platform Fee + included fees | |

**Worked example.** Initial media cost 210,000; the planner proposes prices summing to 200,000 → Difference row shows **+10,000 (+4.76%)**, proposed media cost 200,000. An internal ops margin of 10% *not* included in the plan adds 20,000 on the media cost → **Net Cost 220,000**. Platform Fee at 5% of Net adds 11,000. An agency service fee of 8% *included* in the plan computes on Net (17,600) → **Total Cost 248,600**.

**Fee placement rule.** A custom fee's *include in media plan* toggle decides where it lands in the cascade: fees excluded from the plan are cost components added on the Media Cost (they are part of the Net Cost the buyer sees), while fees included in the plan are line items added on top of the Net Cost, next to the Platform Fee. Per-fee viewer visibility (§10.2) decides only whether the fee is *named* — never whether its amount is counted.

**The approval acknowledgement.** Beneath the cascade the drawer shows the message: *"Plan approval will be required. The media plan will be updated with the proposed prices, but approval is required for execution."* The user must tick this acknowledgement checkbox before the **Save** button enables — an unticked drawer cannot save, and the checkbox **resets every time the drawer opens**: it acknowledges this batch of changes, not a one-time consent. This makes the no-acceptance model explicit at the exact moment prices change: no price acceptance step follows the save; campaign approval is the acceptance.

**After Save — the guided handoff.** On save, the campaign status moves to **Negotiating** (§8.2) and the media plan page and the campaign view-detail page update immediately with the proposed prices. The drawer then explains the next step — *"To start approval, submit the plan from the plan detail page"* — with a **Go to plan detail** button that lands the user on the view-detail page, where the **Submit Plan** action lives. The Save confirmation toast carries the **same Submit call-to-action** so the creator can submit directly from Price Management; the toast CTA runs the identical eligibility checks and confirmation as the view-detail button. Saving prices and submitting the plan are deliberately two separate acts: save records the numbers and opens negotiation, submit starts the approval clock. While saved changes await submission, the **view-detail page** shows an "unsubmitted price changes" message — deliberately never on the Media Plan page, which is a client-facing document.

**The History drawer.** Beside the Summary button sits a **History** button opening a side drawer with the complete negotiation record: every inventory line, and under each, every change ever made — who (user + company), when, old → new — for prices at both levels, Difference applications, bonuses, SOV and impression edits. **Custom-fee history is included**: fee created / edited / deleted entries, subject to the same per-viewer visibility as the fees themselves. History is append-only; because Initial Price is immutable, this drawer is where round-over-round movement ("I offered 4,000, they countered 4,500…") is read.

### 8.4 Roles, submission and what happens after approval

**Media-owner planner (own inventory).** A media-owner user updates prices, saves through the Summary, then clicks **Submit Plan** on the view-detail page. If they hold the approval permission they approve it themselves; otherwise whoever on their team holds it approves. Once approved, the **Execution Plan** option enables:

- With **Influence access**, the user sets the execution plan and sends the campaign to Influence for automated playout.
- Without Influence access, the Execution Plan option stays unavailable and the media owner sees an explicit message: *"Your company doesn't have access to the Execution Plan (Influence). Execute this campaign offline in your own systems and keep the advertiser updated with delivery reports."* The campaign is executed manually in the owner's own system and simply **sits at Approved** in Planner (no "executed externally" state). Planner remains the system of record for the agreed prices either way, and statements build from the approved prices regardless of how playout happened.

**Agency / partner / internal planner (someone else's inventory).** The same sequence — propose prices, Summary, save, Submit Plan, own-side approval — after which the plan goes to the **respective media owner(s)** for their approval turn (§8.2). The media owner sees the media plan and the view-detail page, but with two deliberate blinds:

- **Comments** are invisible to them unless their **company** is tagged on a comment — tags are company-level, not user-level, and a tag reveals **only that particular comment**, never the surrounding thread. Any user of the tagged company sees it.
- **Custom fees** created by the planner are invisible unless the planner chose to include them in the media plan (§8.3 placement rule — and even then, per-fee visibility §10.2 governs whether the fee is named).

**What the counterparty media owner can and cannot do.** On a plan created by another company, the media owner works with the **media cost only** in Price Management — creator fees reach it only through the media plan document, per the visibility rules above — and it **cannot add custom fees** of its own: the fee section of the Summary drawer is hidden, and the view-detail page shows an explicit message: *"You can't add custom fees on this plan because your company is not the plan owner. You can only update proposed prices for your inventory."* The one thing it can do is update Proposed Prices on its own rows — individually, or in bulk via select-all + Apply Difference (§8.1). On the **Plans page**, such a plan carries an **External plan** badge (tooltip naming the creating company) so the owner can tell at a glance the plan came from outside, and while it sits in Reviewing awaiting the owner's response it also appears on the owner's **Plan Approval page** (§7.8) — the badge is the passive signal, Plan Approval is the actionable inbox.

**Viewer-relative status while approvals are partial.** Because each media owner approves independently and never sees the other owners, the status a counterparty sees is **their own slice status, not the campaign aggregate**: an owner who has approved sees the plan as *Approved* (and finds it under the "Approved" filter on the Plans page) even while the campaign as a whole is still Reviewing because other owners haven't responded. The creator's company alone sees the aggregate with the per-owner breakdown in the approval drawer. Labels that disclose the aggregate to an owner ("awaiting other parties", progress fractions) are prohibited — they leak the existence of other media owners; the neutral "Approved — pending buyer confirmation" is the only permitted qualifier. The rule applies server-side to status columns, filters, badges, counts and exports alike (detailed spec: Price Management doc §5.6).

### 8.5 The downstream impact of a price change — the "ripple table"

A price change on an approved campaign does not just trigger re-approval — it ripples through other modules:

| Action | Campaign status | Approvals | Reservations | Proposal | Statement | Audit history |
|---|---|---|---|---|---|---|
| Price saved on a Draft/Planned campaign | Moves to **Negotiating** | None yet | None yet | Not yet generated | Not yet eligible | Entry written |
| Price edited on a Negotiating campaign | Stays Negotiating | None yet | None yet | Not yet generated | Not yet eligible | Entry written |
| Campaign submitted | Enters approval | One approval row per media owner (+ internal where applicable) | Hold requests continue their own lifecycle | Next-generated proposal reflects the submitted prices | — | Entry written |
| Counter Offer from the approval drawer | Stays **Reviewing**; owner's per-owner state becomes **Countered** with a turn marker | Only the countering owner's approval resets | Preserved | — | — | "Counter by owner Y" entry |
| Tier-1 internal reject | Back to **Negotiating**; Price Management unlocks for the creator | Approval cycle restarts on re-submit | Preserved | — | — | "Rejected by internal approver" entry |
| Media owner takes no action for 72h | Owner's slice auto-approved | Counts toward the all-owners-approved pass condition | Preserved | — | — | Entry explicitly marked **automatic** |
| Re-submit after a counter | Re-enters approval | Approvals reset only for owners whose rows changed | Preserved | Versioned proposal generated | — | Entry written |
| "Edit pricing — re-open approval" on an Approved/Live campaign | Reverts to approval cycle | Only affected owners' approvals reset | Preserved | Existing sent proposal becomes a "previous version"; a new version is auto-drafted | Statement Builder removes the campaign from the current draft until re-approval completes | Two entries — "price changed by X" and "approval reset for owner Y" |

The single most important row is the last: **a price change on an Approved campaign re-runs approval only for the affected owners.** The creator's company is not asked to re-confirm what it already confirmed. The page enforces this by rendering Approved/Live campaigns read-only until the planner explicitly clicks "Edit pricing — re-open approval"; from that moment the audit trail records the planner as the owner of the re-approval cycle.

### 8.6 What blocks a price from being changed at all

- The line item belongs to a campaign in **Rejected** or **Completed** status — pricing is closed and cannot reopen.
- The line item belongs to another party's inventory while it is not that party's turn — rows unlock only for their owner during that owner's counter turn.
- The user is in the Advertiser role — they have read-only access to all pricing fields, even if they own the campaign.

---

## 9. Reservations

Reservations are short-lived holds on inventory that prevent another buyer from booking the same impressions while a deal is being finalised. OOH inventory is rivalrous — two campaigns cannot run on the same screen at the same time — and without a reservation primitive every concurrent buyer would see the same inventory as available, with double-bookings resolved on a first-to-submit basis that discourages careful planning.

**Diagram —** [Reservation Workflow Swimlane (Buyer · System · Seller + downstream impact on Approval / Creative / Statement)](https://miro.com/app/board/uXjVGEva6dA=/?moveToWidget=3458764668847891987) (open in Miro)

The buyer view shows every reservation grouped by campaign, with the inventory name, the requested period, the status and an expiry countdown per row. The seller view is the inverse: every hold requested against their inventory, with Approve and Decline buttons. A side panel for each reservation shows the requesting campaign, the requesting user, the requested time block and the original rate-card price.

### 9.1 The full state machine — what each transition means and what it ripples into

| State | Created by | What the buyer sees | What the seller sees | What it blocks elsewhere | What it unblocks elsewhere |
|---|---|---|---|---|---|
| Pending | Buyer added inventory in Step 4 of the wizard but has not yet submitted the campaign | Inventory appears in the plan but with a soft "Not yet held" badge | Nothing — the seller is not yet aware | Step 4 cannot finalise because a hold-request must be raised on submit | — |
| Hold Requested | Campaign was submitted (Draft → Planned), or the buyer explicitly clicked "Request Hold" | Status chip "Awaiting owner"; cannot extend; can withdraw | Pending Hold Requests dashboard tile increments by one; row appears in the queue for action | The line item shows as Held in the inventory pool — invisible to other tenants from the moment the request is written | — |
| Reserved | Seller clicked Approve in their queue | Status flips to Reserved; the 7-day expiry countdown begins; Extend, Release and Convert-to-Booking buttons enable | Row moves to "Active Holds" subview | Tier 2 of the approval workflow can advance for this owner — the owner has implicitly committed to honour the price | — |
| Expired | Server sweeper, when the 7-day window passes without conversion | Status flips to Expired; toast notification; the row stays for audit | Same; the owner's row in Active Holds disappears | — | The inventory becomes available again to other tenants in real time |
| Released | Buyer clicked Release | Status flips to Released; row stays for audit | Notification in the audit feed | — | Same as Expired — inventory returns to the pool |
| Declined | Seller clicked Decline | Status flips to Declined; toast tells the buyer to swap inventory through Modify Plan in Step 4 | Row moves out of the queue | Tier 2 of approval cannot pass for this owner row | — |
| **Booked** | Automatic — the campaign reached **Approved** in §3.1 | Reservation table now shows Booked; expiry timer disappears; the row is final | Row moves to "Confirmed Bookings" | — | Creative Assignment becomes possible (§11); Statement Builder can include the campaign |

### 9.2 The handshake from Buyer to Seller — what the seller actually has to do

The reservation is the only place where the seller has a routine inbound action queue that is not the approval workflow. The handshake works like this:

1. Buyer submits the campaign. The system writes one Hold Requested row per inventory in the plan and increments the seller's dashboard tile.
2. The seller opens their Reservations queue. Each row shows the campaign name, the agency, the requested dates, the inventory, and the rate-card price.
3. The seller has three options on the row: **Approve** (the hold becomes Reserved), **Decline** (the hold becomes Declined and the seller types a reason), or **Approve with conditions** (a comment thread opens; the buyer must respond before the hold is honoured).
4. The seller's response is broadcast to the buyer's dashboard within a few seconds. The buyer's Tier 2 approval row for that owner cannot advance until the response is in.

The seller has up to seven days to respond. Beyond seven days the system does not auto-decline — the hold expires through the normal sweeper but the seller's queue row remains as audit. A persistent "Aging Holds" badge appears on the seller's dashboard if any of their queue rows is older than three days.

### 9.3 The buyer's options once a hold is Reserved

A Reserved hold is an asset with a clock. The buyer can:

- **Extend** — request additional time. The new expiry is the seller's choice — the request opens a one-click Approve / Decline widget on the seller's side. Each extension increments an extension counter visible in the audit history.
- **Release** — voluntarily return the hold. The line item becomes Pending again; if the buyer wants to keep the inventory in the plan they must request a fresh hold. Release is irreversible.
- **Convert to Booking** — only available once the campaign reaches Approved. Conversion is automatic at that moment; a manual Convert button exists for the rare case where the system fails to fire.

### 9.4 Special cases the support team gets asked about most

| Scenario | What the system does | Why |
|---|---|---|
| Buyer's campaign is rejected at any approval stage | All Reserved holds for that campaign auto-Release; sellers see a "Hold released — campaign rejected" line in their audit feed | Inventory must return to the pool the moment the deal dies |
| Seller declines a hold mid-negotiation | The pricing turn stays with the seller (Counter), but no further action can advance Tier 2 for that owner row | The buyer must swap inventory or convince the seller to re-Approve — the seller sees a "Reconsider" button that re-opens the hold |
| Two buyers submit campaigns naming the same inventory within the same minute | First write wins; the second buyer's Step 4 inventory pool has already excluded that line by the time they reach Submit | The exclusion check runs at submit, not at plan-build, so Step 4 rebuilds the pool on Submit |
| Buyer extends a hold but the seller does not respond | The original 7-day clock keeps running — extension requests are non-blocking; the buyer is warned at Day 6 | Prevents indefinite shadow-holds caused by silent sellers |

---

## 10. Proposals

In MW Planner the words **proposal** and **media plan** refer to the same artefact. A proposal is a campaign object that has been turned into a sellable document — covers, executive summary, plan tables, maps, mock-ups — and shared with another company for sign-off. Internal campaign records are not what a brand or media owner sees; a formatted proposal with the originator's branding is the contract precursor.

### 10.0 Who creates a proposal — and what the other party sees

Planner is not a single-direction tool. **Three different company types create proposals**, and the cross-tenant visibility rules are the same in all three directions. The originator company is called the **creator company**; every other company that touches the proposal is a **counterparty**.

| Creator company | Why they create it | Who they pick as the client | What the counterparty sees in their own Planner account |
|---|---|---|---|
| **Media agency** | Standard agency workflow — they are planning for an advertiser | Direct advertiser, or just a brand if the advertiser has no Planner account | Each media owner whose inventory is on the plan sees the proposal as a Media Plan in the "Proposals received" list. The advertiser, if they have Planner access, sees the proposal in full. |
| **Media owner** | Outbound sales — the media owner's sales team builds a media plan and pitches it to a brand or to an agency | Direct advertiser **or** media agency | The picked agency (if it has Planner access) sees the Media Plan in the "Proposals received" list and can comment, accept, or counter-offer. |
| **Internal MW** | The MW team builds a recommended plan on behalf of either side | Same as agency — direct advertiser or agency | Same rules as agency-created — counterparties only ever see the Media Plan view. |

The selection of "Direct advertiser vs. agency" happens once, in Step 1 of the campaign wizard, and is recorded as `clientType`. If the chosen agency has Planner access, a row appears in their account under Proposals automatically — they do not need an email link to find it.

### 10.1 The two views: Campaign Detail vs. Media Plan

Every campaign has two distinct pages with very different access rules:

* **Campaign Detail page** — the creator's full workspace. Shows everything: full pricing breakdown, raw rate-card costs, all custom fees, internal notes, optimisation knobs, draft assets. **Only members of the creator company see this page.** The route guard redirects everyone else to the Media Plan page.
* **Media Plan page** — the shared view. The same campaign, but rendered as a sellable media plan: inventory list, geography, schedule, dates, total committed cost. **Counterparties only see the Media Plan page.** They can comment via the @-mention thread (see §10.3), accept, counter-offer on price (§8), or approve/decline reservations (§9). They cannot see the Campaign Detail tabs (optimisation, internal comments, full fee breakdown).

This is enforced server-side: when a user from a non-creator company hits the Campaign Detail route, the API returns a redirected response and the frontend takes them to the Media Plan view of the same campaign id.

### 10.2 Custom fee disclosure — the marked-up price contract

Custom fees (agency service fee, production cost, ad-server fee, etc.) live on the campaign and **each fee carries an explicit visibility flag** for each of the four roles (`showToAgency`, `showToMediaOwner`, `showToAdvertiser`, `showToInternal`). The flag controls two things at once: **whether the fee appears as a line in the media plan**, and **whether the original (rate-card) media cost is also visible**.

The rules — exactly as they fire in production — are:

| Scenario | What the counterparty sees on the Media Plan |
|---|---|
| **Agency creates plan, agency adds custom fee, fee marked "internal" only** | Media owner sees the original media cost. Agency's fee is **hidden**. Agency sees both. |
| **Agency creates plan, agency adds custom fee, "include in media plan" toggled ON** | Media owner sees the original media cost **plus** the agency fee as a separate line. Both parties see both numbers. |
| **Media owner creates plan, media owner adds custom fee, fee marked "internal" only** | Agency sees a **single combined media cost** = original rate-card + media owner fee, with no breakdown. Media owner sees both. |
| **Media owner creates plan, media owner adds custom fee, "include in media plan" toggled ON** | Agency sees the original media cost **plus** the media owner fee as a separate line. Both parties see both. |
| **Internal MW staff** | Always sees every fee with full breakdown, regardless of visibility flags. |
| **Direct advertiser (no agency in the middle)** | Sees only what `showToAdvertiser=true` exposes. The default for new fees is to show the advertiser the rolled-up media cost only. |

The "include in media plan" toggle is the `includeInPlan` boolean on each `customFees[]` entry on the campaign and is the same control as Step 1's "Show fees as separate lines on the media plan" switch in the wizard. When the toggle is OFF, the fee is silently absorbed into the media cost the counterparty sees; when ON, it appears as its own row in the cost table.

The pricing API enforces this server-side. The endpoint that returns the campaign's fees (`GET /api/campaigns/:id/fees`) inspects the requester's company `businessType` and prunes any fee whose visibility flag for that role is `false`. The Media Plan page consumes that filtered list — it cannot accidentally render a hidden fee. The same filter runs whether the viewer is on the web Media Plan page, downloading the PDF, or reading the proposal share-link.

### 10.3 Comments and @-mentions — internal by default, shared on tag

Every campaign carries one shared comments thread that all companies on the plan can post to. **The visibility rule is the inverse of the costing rule: a comment is INTERNAL by default and only crosses tenants when an @-mention is added.**

* Posting a comment with no @-mention → only members of the **author's company** can read it. The thread row is tagged "Internal" in the UI.
* Posting `@MediaOwnerXYZ this banner needs to be smaller` → members of MediaOwnerXYZ can read it; everyone else still cannot. The row is tagged "Shared".
* The author can mention multiple companies in one comment — every mentioned company sees that comment, and only that comment, on the thread.
* Untagged replies underneath a tagged thread remain internal — the system does **not** treat the parent's mention as inherited. This avoids accidentally leaking a half-finished side discussion.
* Internal MW staff can read every comment regardless of mentions; they are the platform operators and need full visibility for support and dispute resolution.

**Worked example.** An agency creates a plan that uses inventory from both ABC Media Owner and XYZ Media Owner. Three comments are posted in this order:
1. Agency planner writes: *"Need to confirm carbon report by Friday."* — only the agency sees it (Internal).
2. Agency planner writes: *"@XYZ Media Owner can you swap screen #4 for the larger LED?"* — only the agency and XYZ see it. ABC does not.
3. ABC Media Owner has nothing to read in the thread because nothing has been addressed to them yet. If ABC opens the comments tab they see an empty thread plus the legend "Comments are visible to your company once another party @-mentions you".

This is enforced server-side. The endpoint that returns comments (`GET /api/campaigns/:id/comments`) takes the requester's `primaryCompanyId`, looks up the company's `businessType`, and returns only comments where either the author is the requester's company **or** the requester's company id appears in the comment's `mentionedCompanyIds[]` (or where the requester is internal MW staff). The frontend never receives comments it is not allowed to see.

The composer enforces the rule before posting too: a live "visibility" badge under the textbox shows the user exactly who will see the comment ("Internal note — only your company will see this." or "Visible to your company + ABC Media Owner") so authors do not accidentally tag the wrong party.

### 10.4 Generating and sending the formatted proposal

From any campaign in Draft or Planned the user clicks Generate Proposal and picks a theme (each theme is a saved set of fonts, colours, logos and section order). The platform stitches the campaign data into the theme template — applying the same fee-visibility filter from §10.2 — and produces a downloadable PDF plus a shareable web link. The proposals list shows every proposal with its version, its status (Draft, Sent, Accepted or Rejected), the recipient and the timestamp.

### 10.1 What a price change does to a sent proposal

The Proposal module is downstream of Price Management in §8 and the rule is strict: **any change to a line-item price after a proposal is Sent automatically generates a new version of that proposal and demotes the previous version to "superseded"**. The recipient is notified by email that a new version is available, the share-link token is rotated so a leaked old link cannot be re-used, and the audit history records both the version bump and the prompting price change. The previous version remains immutable for audit. A proposal with a brand "Accepted" stamp on it cannot be regenerated silently — the planner must explicitly confirm "Generate new version, supersede the accepted one", which writes an extra audit entry that finance reconciliation depends on.

### 10.2 Proposal statuses and what they each block

| Status | Meaning | Blocks |
|---|---|---|
| Draft | Generated but not sent | Tier 1 of approval will not auto-pass on agency-self-submission until at least a Draft exists |
| Sent | Email + share link delivered | A new line-item price change forces a new version |
| Accepted | The brand clicked the Accept button on the share link | Required before the campaign can be moved to Active by the agency |
| Rejected | The brand clicked Reject | Campaign cannot reach Active; the planner must regenerate after addressing feedback |

### 10.5 The Media Plan page — the complete behavioural contract

The Media Plan page is the surface where a campaign stops being a working draft and becomes a document that another company is expected to read, sign, and pay against. Section 10.1 introduced the route guard that decides which page each viewer sees; this section is the full behavioural contract for what the page itself renders, gates, and exports. The exhaustive specification — every conditional, every logo placement, every chart binding, every server-side filtering rule — lives in `docs/media-plan-specification.md`. This section is the canonical summary that lives inside the main PRD so a reader without the spec in hand can still understand the page end to end.

**One source of truth, two views.** The page reads from a single server view-model (`GET /api/media-plan/:campaignId`) that derives every field — campaign meta, brand block, plan snapshot, geography rollup, featured inventories, audience strategy, daypart heatmap, expected delivery bins, three-tier cost ladder, fee table, carbon rollup, three insight facts — from the underlying campaign record. The same view-model feeds both Presentation and Analytics views, so a number on a slide and the same number on a tab cannot drift. The view-model is automatically filtered server-side based on the viewer's role and company before it is returned, so a hidden fee or a competitor's inventory row never reaches the browser of a role that should not see it; the filtering is invisible to the reader and not user-selectable.

#### 10.5.1 Page chrome and controls

The header is one row. On the left sit the tenant logo, the plan title with a status badge, then the View dropdown (Presentation / Analytics, defaults to Presentation) and the Theme picker. On the right sit the Download dropdown (PowerPoint, PDF, CSV) and the Share button. The status badge is clickable — it opens the Approval drawer (§7) without leaving the page. There is no second toggle bar below the header — the View dropdown is the only switcher. The View choice and theme persist per session.

A status chrome band sits between the header and the body to make the document state legible at a glance:

| Status | Visual treatment |
|---|---|
| Draft | "DRAFT" watermark across the page; Draft chip in the header |
| Reviewing | Amber band across the top with the current approval stage and the actor it is waiting on |
| Approved / Active | Lock banner with the approver, the timestamp and a link to Price Management for legitimate post-approval changes |
| Rejected | Red banner with the rejection reason and a link to the rejecting actor's comment |
| Public Share Link | Read-only banner pinned to the top with the link's expiry timestamp; theme picker, Download and Share are suppressed |

#### 10.5.2 Presentation View — the slide deck

The Presentation view is a vertical scroll of conditional slides. Every slide is conditional — a slide that has nothing to say is not rendered, and the deck closes up around it. Slides have no numeric labels in the UI or the exported deck; their order is fixed and meaningful by position. The deck deliberately ends on the **Why This Plan Works** insight slide rather than a closing/thank-you slide, so the reader leaves on a confident statement of value rather than a salutation.

| # | Slide | When it renders |
|---|---|---|
| 1 | Cover | Always — tenant logo, plan title, agency/advertiser logos, planner contact, plan dates, status pill |
| 2 | Plan Snapshot | Always — duration, geography, inventory count, total impressions, est. reach, average frequency, **Avg CPM (or Avg CPS in CPS pricing mode)**, **eCPM**, share of voice, share of time, total investment in the active currency. A note below the tiles explains eCPM (total cost ÷ total impressions × 1000) vs the rate-card Avg CPM/CPS |
| 3 | **Inventory Mix** | When at least one inventory is on the plan. Template-aligned composition table grouped by **Classification (Digital/Classic) × inventory type** — Digital Screens, Classic Billboard, Digital Cinema, Digital/Classic Retail, and the remaining wizard channels by their buyer-facing labels. Columns: Classification, Inventory Type, Inventories, Impressions, Media Cost, **Total Cost**, Avg CPM, Share. Each row's Total Cost allocates the plan's fees/platform proportionally to its media cost so the column reconciles exactly with the Cost Breakdown's Total Cost; a Total row plus three stat cards (inventory types, total inventories, total cost) close the slide. Wizard channels the planner selected but hasn't populated yet render as muted dash rows |
| 4 | **Cost Breakdown** | When the viewer's visible analytics tabs include Costing **and** at least one inventory is on the plan — same gate as the Costing tab so cost-sensitive fee lines never leak to restricted viewers. Two cards: **Cost Cascade** (Media Cost → fees not included in the plan → Net Cost → Platform Fee (% of Net) → fees included in the plan → Total Cost, per §8.3's fee placement rule) and **Custom Fees** (one box per visible fee: name, basis caption such as "12% of Media Cost", included/not-included placement caption, amount). Fees hidden from the active viewer stay in the maths: hidden pre-net amounts fold into the displayed Media Cost line, hidden post-net amounts aggregate into a single unnamed "Additional fees" line |
| 5 | Targeting (Audience Strategy) | When the planner has explicitly picked at least one demographic, behaviour, signal **or** cinema attribute. The auto-derived venue mix on its own does **not** trigger the slide — venue mix is already implicit in the Inventory Snapshots and Geographic Plan, so a venue-mix-only Targeting slide repeats data the buyer has already seen. Laid out as **four side-by-side full-height vertical columns — Demographics (age · gender · income), Venue Types, Behaviour and Interest** — with **no percentages, no facts strip and no inner scrollbars**; the strongest one-line strategy sentence sits in the slide header directly below the "Targeting" title rather than in a footer, and Cinema and Retail each break out onto their own dedicated slide |
| 6 | **Audience Trends** | When the plan has explicit audience targeting (`audienceTrends.topIndices.length > 0`) **or** the planner has authored at least one custom schedule (`daypart.hasSchedules`). With neither signal present the day-of-week panel collapses to seven flat 14.3% bars and the over-indices panel is empty — so the slide is suppressed entirely rather than rendering visibly empty charts |
| 7 | Geographic Plan | When at least one inventory is on the plan; static Mapbox preview keyed off the geography bbox; per-country/per-city table with cost rollups |
| 8 | **Audience Map** | When at least one inventory has plottable coordinates **or** at least one market cluster has coordinates (`inventoryMap.points.length > 0 || audienceHeatmap.clusters.length > 0`). A **single** Mapbox static map that layers the audience-traffic heatmap (up to the 20 densest markets as translucent density glows, sized and shaded by how many inventory sites cluster there) **beneath a pin on every inventory site** and an amber star marker on every included Point-of-Interest target the planner uploaded, auto-fitted to the bounding box of every panel. Merges the former separate Inventory Map and Audience Traffic Heatmap slides so one map shows both where audience exposure concentrates and exactly which panels cover it; sits directly after the Geographic Plan so the buyer moves from the city table to the physical layout |
| 9 | **Execution Plan** | Always — operational rollup that sits between Geography and the granular Daypart heatmap (active days, peak window, peak day, delivery shape, multi-spot rollup). Unlike the Daypart Heatmap, this slide reads sensibly under default 24/7 conditions because it expresses cadence in prose ("Default 24/7 schedule across N inventories"), so it is not gated on `hasSchedules` |
| 10 | Goals | When campaign objective + KPIs are set |
| 11 | **Inventory Snapshots** (5.6.1, 5.6.2, …) | When at least one inventory is on the plan. **Paginated 6 cards per slide in a 3×2 grid across ALL planned inventories** — slide numbers continue with sub-pagination (5.6.1, 5.6.2, …) so a 28-inventory plan emits 5 sub-slides without truncation. Each card shows thumbnail, channel glyph, name, format/city, impressions, ad plays (digital) or plays/day (classic), CPM, total cost and SOV. Sorted by daily impressions desc. Cinema sub-block included when channels include cinema. Cards with no thumbnail on file render a neutral channel-glyph fallback rather than a broken external image — the deck stays clean even when media owners have not yet uploaded inventory artwork |
| 12 | Daypart Heatmap | When the planner has authored at least one custom schedule (`daypart.hasSchedules`). The 24/7 default has nothing meaningful to put on a heatmap — every cell would be uniformly active and the named-pattern chips would all read "24/7" — so the slide is suppressed and the buyer reads the cadence story off the Execution Plan slide instead. When shown, the slide carries the plan-level 7×24 union heatmap, named-pattern chips, multi-spot summary, top-3 outlier mini-heatmaps and the **Excel/Analytics reference note** pointing buyers to the per-inventory cadence in the DOOH Schedules tab and Excel export |
| 13 | Expected Delivery | Always — adaptive-bin chart of impressions across the campaign window |
| 14 | Why This Plan Works | Always — three insight facts derived from the data, three-milestone roadmap (Ramp / Mid-flight / Closeout) and up to three inventory proof-thumbnails |

When the campaign has zero inventories, the Presentation view collapses to a single Empty Plan slide that explains what is missing and deep-links to the wizard step that would populate it.

Each content slide closes with a one-line muted footer note that explains what the slide shows and how the headline figure is computed; the per-slide generators and scenario branches are catalogued in §10.5.8. Operators who need the full editing surface land there from the page-level chrome (the campaign workspace tabs above the deck), not from a per-slide button — that keeps every slide visually flat and lets the footer carry the explanatory copy instead of navigation.

**Per-slide text and chart generation rules.** Every body sentence on every slide is computed deterministically from the campaign record — the deck never carries hand-written marketing copy and a planner cannot edit slide text directly. A slide that lacks the data behind a given bullet simply does not emit it. The same view-model feeds the Presentation deck, the Analytics tabs, the PowerPoint export, the PDF export and the Excel export, so a number that appears on a slide must agree with the same number on a tab. The rules below are the canonical generators; the implementation lives in `server/lib/media-plan-view.ts`.

**Plan Snapshot (slide 2).** All KPIs are derived, not stored:

- **Duration** = days between campaign start and end (inclusive).
- **Total impressions** = sum over inventories of `dailyImpressions × duration`.
- **Estimated reach** = `round(totalImpressions × 0.4)` when no measured reach is available — explicitly labelled "estimated" so the buyer never confuses it with a panelised number.
- **Average frequency** = `totalImpressions / estimatedReach`.
- **Pricing mode** is goal-driven: when the campaign goal is `impressions` or `reach` the plan is priced and reported on **CPM**; for `sov`, `ad_plays` or an unset goal it switches to **CPS** (cost per spot). This is computed once (`pricingModeFromGoal`) and threaded onto both the Plan Snapshot and the Estimated Performance Metrics so every surface labels the same metric the same way.
- **Avg CPM** = the impression-weighted blend of each inventory's media-owner rate-card `inv.cpm` (falling back to `(ownInventoryCost / totalImpressions) × 1000` for panels without a rate card). This is the *price* the media owners quoted, not the effective rate.
- **eCPM** = `(totalCost / totalImpressions) × 1000` — the **effective** cost per thousand impressions, computed from what the plan actually costs (media + included fees) rather than the quoted rate-card price. Always shown directly below Avg CPM/Avg CPS with an inline note explaining that eCPM is derived from total cost ÷ total impressions while Avg CPM is the rate-card price from media owners.
- **Avg CPS** = `ownInventoryCost / totalSpots` (total spots from `computeDigitalAdPlays` across the schedule grid). Shown **instead of** Avg CPM whenever the pricing mode is CPS.
- **Share of voice** = the campaign-level SOV the planner set (default 100%).
- **Share of time** = average of per-inventory SOT values when present, else falls back to SOV so the tile never displays a 0 placeholder.
- **Total investment** = the canonical §8.3 cascade total — media cost + fees not included in the plan (= Net Cost) + Platform Fee (% of Net) + fees included in the plan. The Snapshot tile, the Cost Breakdown slide, the Inventory Mix Total Cost column and the Costing tab all derive from this single number, so every surface and every viewer mode shows the same total. Per-fee visibility affects only whether a fee is *named* (hidden amounts fold into the Media Cost line pre-net, or an unnamed "Additional fees" aggregate post-net) — never the totals.

**Cost Breakdown (Costing tab).** Three-tier ladder built bottom-up: every inventory rolls into a type group (`inventory.format` or `inventory.type`), every type rolls into a channel (the seven canonical channels: digital screen, classic, transit, retail, network, radio, experiential, plus cinema as a sub-block). For each row the view-model computes cost, share-percent of total media, impressions, CPM and — for digital-screen rows only — ad plays (`computeDigitalAdPlays(scheduleGrid, days)` at 30 loops per hour × spots per loop). Fee handling follows the §8.3 placement rule: fees with `includeInPlan = false` are added on the Media Cost (they are part of the Net Cost), fees with `includeInPlan = true` are added on top of the Net Cost alongside the Platform Fee (% of Net Cost, from the planner's primary company). Per-fee visibility (§10.2) then decides naming only: amounts hidden from the active viewer fold into the displayed Media Cost line (pre-net) or an unnamed aggregate "Additional fees" line (post-net) — totals never change per viewer. **Advertiser viewer mode** further absorbs every billable, included fee into the headline media cost and suppresses the fee table entirely. The carbon row appears when the campaign has `carbonEstimatedKg` or any inventory carries measured CO₂; the note text adapts to coverage ("All inventories report measured CO₂" vs "X% of inventories report measured CO₂; the rest are estimated using AdGreen averages").

**FX disclosure on the Costing tab.** Plans that price in a currency other than the planner-tenant's local reporting currency carry a one-line italicised note beneath the totals box: *"Amounts shown in `<planCurrency>`. Costs converted from your account currency `<localCurrency>` at 1 `<localCurrency>` ≈ `<rate>` `<planCurrency>`, including a `<X>`% FX buffer to absorb rate movement before invoicing."* The disclosure appears on the Costing analytics tab so the buyer always sees the conversion explained in the same words. The local currency is derived from the tenant company's `country` field (`COUNTRIES[country].currencies[0]`) — the first currency in that country's currency list. The effective rate combines the USD-pivoted spot rate (`localCurrency.rateToUSD / planCurrency.rateToUSD`) with the plan-currency margin (`exchangeRateMargin`), so the rate the buyer sees is the rate the platform will price against. **No disclosure renders** when the plan currency matches the tenant's local currency (single-currency plan — the common case), when the tenant has no country recorded, or when either currency is absent from the FX table — the tab stays clean. The same data also drives the proposal/media-plan budget surface so a buyer never sees a converted total without the explanation. **Source of rates and margins.** The exchange rates and per-currency margins ship today as constants in `shared/countries-currencies.ts`; the production roadmap moves them to **Admin Console → Finance → FX Rates** so a tenant's finance team can update the spot rate, override the per-currency margin, and stage rate changes without a code release. The view-model contract is unchanged when that source flips — the Costing tab reads whichever rate the platform currently considers authoritative.

**Targeting / Audience Strategy (slide 5) — "Why this targeting strengthens the plan" justifications.** Up to **three sentences**, sliced from a strongest-signal-first ordered list. The slide renders only the bullets that have real data behind them — never a generic platitude. The rules, in order of precedence:

| Rank | Trigger | Sentence template |
|---|---|---|
| 1 | `demographics` is non-empty | "Concentrates spend on `<age cohorts>` cohorts so impressions land on people most likely to act on the brand." (When age bars exist, list them as `25-34 / 35-44 cohorts`. Otherwise list `N demographic segment(s)`.) |
| 2 | `behavioural environments` is non-empty | "`N` behavioural environment(s) (`a, b, c…`) filter inventory to high-engagement venues — the audience is in the right mindset when the ad surfaces." (List the first 3 environments inline; append an ellipsis when more exist.) |
| 3 | `audience signals` is non-empty | "`N` behavioural signal(s) narrow the buy to moments of receptivity (`a, b, c…`), trimming wasted impressions." |
| 4 | `venueTypes` is non-empty AND fewer than 3 bullets so far | "Venue mix is led by `<top.type>` (`<top.percent>`% of inventory) where the target audience accumulates the most attention seconds per impression." |
| 5 | Cinema is configured AND fewer than 3 bullets so far | "Cinema buy concentrates on `<showtimeBand>` (`X%`) — captive audience with no ad-skip option." |

Only the **strongest (first) justification** is surfaced on the redesigned slide — it becomes the slide's header subtitle directly below the "Targeting" title, falling back to "Who, where, and what they care about." when no signal fired; the remaining justifications are still computed but are no longer shown as a bullet panel. The slide body is **four side-by-side full-height vertical columns — Demographics, Venue Types, Behaviour and Interest** — carrying **no percentages, no facts strip and no inner scrollbars**. Demographics render as grouped Age / Gender / Income label chips (no bars); venue types as a chip cloud (cap 14); behaviour and interest tokens as chip clouds (cap 24). Every box collapses its overflow into a "+N more" chip rather than clipping, and the cinema and retail sub-blocks have moved onto their own dedicated slides.

**Audience Trends (slide 6).** Three feeds, all derived:

- **Reach buildup curve** = `1 - exp(-k·t)` with `k = 2.4`, where `t` is the bin index normalised to `[0, 1]` and the curve is renormalised by the `t = 1` value so the final bin lands at exactly 100% / `totalReach`. The headline ("Reaches X unique people by end of flight") therefore matches the last point on the chart instead of being ~9% above it. Bins use the same adaptive bucketing as Expected Delivery (daily / weekly / monthly / quarterly per §10.5.7).
- **Day-of-week activity bars** = the **spot-weighted** planned activity per weekday, expressed as a per-day share of the weekly total. Per screen, every scheduled hour contributes a weight equal to that hour's spots-per-loop; when several schedule entries on the same screen overlap an hour, the hour counts **once at the highest spots-per-loop among them** (overlaps never stack). Screens without any authored schedule count as **24/7 at weight 1**, so skipping day-parting doesn't hide a screen from the chart. Per-day weighted hours are summed across all screens, converted to percentages, and the peak-day callout names the highest day. The slide is suppressed entirely (per §10.5.2) when neither audience targeting nor a custom schedule exists, so the chart never renders in a degenerate "seven flat 14.3% bars" state. When the slide *does* render under a default 24/7 cadence (i.e. the planner has audience targeting but no custom schedule), the bars resolve to an even 14.3% per day; the peak-day callout is omitted (`peakDay = null`) rather than arbitrarily defaulting to Monday, because every day genuinely ties.
- **Top audience over-indices** = four fixed category labels — **Age & Gender, Income Group, Behavior, Interest** (in that order) — rather than specific cohort values, so the axis reads as a stable planning framework rather than a list of individual cohorts. Whenever the plan has any audience strategy, all four axes appear so the chart is a consistent framework across campaigns. Each label's index is a deterministic hash mapped into the **1.4× – 3.6×** range — the distribution OOH planners typically see in real audience research — seeded from the underlying segment tokens when that dimension is targeted and from a campaign-derived fallback otherwise, so it still varies campaign to campaign instead of being a constant per label. The slide is honest about this: it claims a comparative story, never a real third-party number.

**Geographic Plan (slide 7).** The view-model groups inventories by city, sums impressions and cost per city, sorts desc by impressions, and emits a per-country/per-city table capped at 8 rows (see §10.5.7). The static Mapbox preview is keyed off the bounding box of all pin coordinates; the pin colour matches the active theme.

**Audience Map (slide 6).** A **single** Mapbox Static Images map that layers the audience-traffic heatmap *beneath* a pin on every inventory site, so one frame shows both where audience exposure concentrates and exactly which panels cover it — it merges the two former separate slides (the old Inventory Map and Audience Traffic Heatmap). The map is auto-fitted (`/auto/`) to the bounding box of every plotted site so the deck frames exactly the markets on the plan rather than a fixed region; the densest market (the city holding the most pins) is surfaced in the subtitle, and three stat tiles beneath the map call out Sites Pinned, Total Inventory and the Densest Market. **Pins** use the same coordinates that key the Geographic Plan preview — one marker per inventory with a valid `location.lat/lng`, in the active theme's pin colour — and are appended last so Mapbox draws them on top of the glow. **The heatmap** clusters inventories by market (a `country|city` key, each glow centred on the cluster's pin centroid); every cluster carries a `siteCount` weight, the clusters arrive sorted densest-first, and up to the 20 densest are painted as stacks of concentric translucent rings (a large faint halo down to a small intense core) whose overlapping fills blend into a smooth radial glow — larger and darker where more inventory, and therefore more audience exposure, concentrates. Because the Mapbox Static Images API caps the request URL near 8 KB, the URL builder keeps the encoded GeoJSON lean (8-step circle polygons, 3-decimal coordinates, fill-only styling) and, whenever the assembled URL exceeds the ~7,800-character threshold, trims iteratively — sacrificing pin count first, then ring detail, then the lightest-weighted markets — so the densest markets and a representative pin spread always survive; the final fallback drops the heatmap entirely and renders pins only. Inventories without coordinates are silently excluded and reconciled in the footer note, and the map caps at 60 pins (see §10.5.7) — beyond that the first 60 pins in plan order are kept and the overflow dropped (the on-screen slide captions how many extra sites are on the plan; the PowerPoint export omits this caption). The render guard is `inventoryMap.points.length > 0 || audienceHeatmap.clusters.length > 0`, so the slide renders whenever the plan has either plottable pins or plottable markets and is suppressed only when it has neither; a missing Mapbox token does not suppress it — when data exists but no token is configured (or the static-image request fails) the slide still renders, degrading to a neutral placeholder block rather than a broken image. The slide sits immediately after the Geographic Plan so the buyer moves from the city table to the physical layout.

**Execution Plan (slide 7) — headline and justifications.** The headline is a `·`-joined string built from `durationDays · activeDaysLabel · "Peak <day> <hh AM – hh AM>"`. The `activeDaysLabel` recognises common patterns ("Mon–Sun", "Mon–Fri (weekdays)", "Sat & Sun (weekends)") before falling back to a comma-separated list. The peak window is the highest-summed 3-hour band across the unionGrid; ties surface as **"All hours equal"** rather than misleadingly pointing at the earliest window. Justifications follow two paths:

- **With schedules** (the campaign has at least one custom daypart): "Activates `D` days per week across `N` inventories, totalling `H` ad-display hours per week — directly supports the `M` impressions on the Plan Snapshot." → "Concentrates ad plays in the `<peak window>` on `<peak day>` when the targeted audience is most receptive — every spend dollar lands in a high-attention moment." → "`<top preset>` pattern applied to `K` schedules — uses a proven OOH preset rather than ad-hoc hours, reducing wastage." → one of three delivery-shape sentences depending on whether the campaign is **front-loaded** (last-third / first-third < 0.83), **even** (between 0.83 and 1.2) or **back-loaded** (> 1.2). When multi-spot loops are present a fifth bullet is appended ("…multi-spot loops compound frequency in the highest-attention hours.") **but the array is then sliced to 4**, so this bullet only surfaces when the peak-window or pattern bullet was suppressed (no peak day, or no recognised pattern).
- **Without schedules** (24/7 default): "Default 24/7 schedule across `N` inventories runs every available ad slot for the full `D`-day flight — maximises raw impression delivery without daypart restrictions." → "Even `<granularity>` pacing distributes the `M` planned impressions consistently…" → "Apply daypart presets (Commuter, Business Hours, Nightlife) in the campaign optimisation step to tighten delivery to peak audience moments."

The final justification array is always sliced to **at most 4 entries**.

**Inventory Snapshots (slide 9, paginated as 5.6.1 / 5.6.2 / …).** All planned inventories are sorted by daily impressions desc, then chunked into pages of 6 and rendered as a 3×2 grid per slide. Each card carries: thumbnail (channel glyph fallback), name, format, city, country, channel pill, impressions, ad plays (digital) or plays/day (classic), base CPM, total cost and SOV. The in-app card additionally surfaces the media-owner logo; the PPTX export keeps the card layout tight and omits the logo. **SOV** is computed per inventory as `round((activeHoursPerWeek / 168) × 100, 1)` where `activeHoursPerWeek` is the **maximum** active-hour count across the inventory's schedule entries (entries describe the same physical loop in different time slots, so they are not stacked into a union). When no schedule is on file the panel is treated as 24/7 → 168 hours/week → SOV = 100%. The cinema sub-block lists operators, showtime band and genre/rating/film chips when the plan includes cinema inventory.

**Daypart Heatmap (slide 12).** The 7×24 grid is the **cell-level union** across every schedule entry on every inventory: cell value = number of inventories active in that hour. Named-pattern chips count how many schedule entries match each preset (Commuter, Nightlife, Business Hours, Weekend, 24/7, Custom). The multi-spot summary appears when at least one inventory runs more than one spot per loop. The view-model also computes **up to three "outlier" mini-heatmaps** — inventories whose preset is `Custom` (genuinely bespoke schedules, not a recognised preset). These outlier mini-heatmaps render on the in-app Presentation slide; the **PowerPoint export omits them** to keep the deck slide visually clean and lets the Excel/Analytics reference note carry the long tail. The slide closes with that **Excel/Analytics reference note** pointing buyers to the per-inventory cadence table in the DOOH Schedules tab and Excel export — the heatmap stays readable because it does not try to enumerate every panel.

**Expected Delivery (slide 13).** Bin count and granularity follow §10.5.7's adaptive bucketing rules. Per-bin impressions are `totalImpressions / binCount`, modulated by a small `1 + sin(i · 0.6) × 0.15` wave for visual interest (the headline total still equals the sum of bins). Per-bin reach uses the same wave at half amplitude. The peak label is the highest-impression bin.

**Why This Plan Works (slide 12).** Three facts, ordered by data precedence: geography fact ("Concentrates `K` inventories in `<top city>`, generating `X.X`M impressions where the brand's audience density is highest.") → snapshot fact ("Delivers `X.X`M total impressions across `N` inventories at a blended CPM of `<currency> X.XX`.") → audience fact (demographics overlap when present, else venue mix, else a generic channel-benchmark sentence). The roadmap always emits exactly three milestones (Ramp / Mid-flight / Closeout); each milestone's cumulative achievement % is computed live by pacing the plan's forecast for the goal metric across the flight on an S-shaped OOH delivery curve and dividing by the goal target (falling back to planned impressions with no target — the roadmap then ends at 100%). Up to three inventory proof-thumbnails sit alongside as visual anchors.

**Edge cases — maximally broad targeting and budget allocation.** This sub-block documents how the deck behaves when a user pushes the targeting wizard to its limits (selecting every age group, every gender option, every income tier, every behaviour, every interest) and how the deck currently treats the wizard's per-channel **Budget Allocation** split (`campaign.optimization.budgetAllocation`). It captures both the **current** behaviour and the **planned** behaviour so the deck never silently misleads the buyer.

**No percentages — the slide reads as labels, not bars.** The redesigned Targeting slide (§5.7) renders four side-by-side full-height columns — **Demographics**, **Venue Types**, **Behaviour** and **Interests** — as plain label chips with **no percentages and no bar chart**. The earlier `percent = 100 / max(1, N)` demographic bar chart, and its flattening pathology where broad selections collapsed into near-uniform sub-5% stripes, has been removed, so selecting many tokens no longer produces a visually meaningless chart. Breadth is still honoured: the buyer sees every selected label up to each box's cap, then a `+N more` chip, and drills into the structured Targeting tab in Campaign Detail for the full list.

**Demographics — grouped by axis.** The Demographics box splits its chips into **Age**, **Gender** and **Income** sub-groups (stacked vertically inside the tall Demographics column, each under its own muted axis label with the chips wrapping within the column width), driven by the `category` the view-model (`buildAudienceStrategy`) assigns to every demographic token. Interest and lifestyle tokens are lifted into their own **Interests** box rather than mixed into Demographics, so the buyer reads "who" and "what they care about" independently. The demographic sub-groups simply wrap and carry no hard cap, because age/gender/income vocabularies are inherently small.

**Per-box caps for maximally broad targeting.** The chip-cloud boxes cap visible chips so the fixed-height boxes never clip: **Venue Types = 14**, **Behaviour = 24**, **Interests = 24**. Anything beyond a cap collapses into a single `+N more` chip whose hover `title` lists the hidden labels. This keeps the slide at one page even when a user selects every behaviour, interest and venue type, while still signalling that breadth is wider than what is drawn.

**"Why this targeting strengthens the plan" — strongest-signal header line.** The ranked justification list (rules above) is still computed strongest-first, but the redesigned Targeting slide surfaces **only its first entry**, as the one-line header subtitle beneath the "Targeting" title. The former three-bullet justification panel and the facts strip have been removed, so a buyer who picks demographics + behaviours + signals no longer crowds the cinema justification off the list — the cinema buy is narrated on its own dedicated Cinema slide instead.

**Audience Trends — over-index cap.** The "Top audience over-indices" chart on slide 4 renders **exactly four bars** — one per audience axis (Age & Gender, Income Group, Behavior, Interest) — whenever the plan has any audience strategy, rather than one bar per selected cohort. Selecting hundreds of tokens does not produce hundreds of bars: the tokens within each dimension only feed the deterministic hash that sets that axis's multiplier, they are not charted individually. Age and gender are folded into a single combined axis rather than charted as separate cohort multipliers.

**Budget Allocation (`campaign.optimization.budgetAllocation`) — captured, not yet surfaced.** The field carries an intended split across inventory types (e.g. `{ digital_screen: 60, classic: 40 }`). It is not user-editable in the wizard — Step 5 has no Budget Allocation tab because the Step 2 channel allocator and the Step 4 recommendation engine already express intent at a level the planner can reason about. The wizard seeds the field with an even split across the selected inventory types when the Optimization step initialises, and that seeded value is what persists on the campaign. Auto-population from the Step 4 auto-plan output (the per-channel `budgetAllocations` already computed in the auto-plan summary) is a planned wire-up; for now the value is a stub. The **current** Media Plan deck does not surface the value anywhere — the Costing tab is built bottom-up from the actual `mediaCost` of each picked inventory, so it shows the *realised* spend per channel rather than the recorded *intended* split. A buyer cannot tell from the deck whether the realised plan honoured the recorded allocation.

**Planned Budget Allocation visibility.** When `optimization.budgetAllocation` is non-empty the Costing tab will render an additional **"Allocation vs realised" strip** above the three-tier ladder:

- One row per channel that appears in the allocation map: target % vs realised %, in side-by-side bars.
- A drift chip per row: green ≤ ±2pp, amber ±2 – ±5pp, red > ±5pp. The slide subtitle picks up the worst-case chip ("Plan delivers within 3pp of the requested split" or "Plan over-allocates classic by 7pp").
- The "Re-run recommendation to honour allocation" deep link shows in the in-app slide only (suppressed in the PPTX export and in share-link mode), pointing the planner back to the inventory step to trigger a fresh recommendation.
- When `budgetAllocation` is empty (the user did not split intent) the strip is hidden entirely — the Costing tab stays exactly as it is today.

These rules sit in §10.5.2.1.1 so that the maximally broad targeting + budget-split scenario has a single canonical reference for both the implementation and the deck reviewer.

**Empty Plan slide.** When the campaign has zero inventories the entire deck collapses to a single explainer slide that names what is missing (no inventories, no targeting, no schedule) and deep-links to the wizard step that would populate it.

#### 10.5.3 Analytics View — the analytics tabs

The Analytics view is presented as an **Excel-style workbook**: every tab is a worksheet with gridlines, header rows and a frozen header, and the tab strip sits at the bottom as sheet-style tabs mirroring the worksheet names. What the viewer reads on screen is byte-for-byte the structure of the downloaded `.xlsx` — same sheet set, same column order, same hide-when-empty behaviour — so the on-screen view and the Excel export are a single source of truth. Tabs the viewer is allowed to see are always rendered, even when a body is empty (the body then explains why and deep-links to the wizard step that would populate it). **No field is ever rendered as "NA" or a blank row: a fact that has no value is omitted entirely.**

Across every surface the headline performance box uses one canonical term — **Estimated Performance Metrics** (replacing the older "Plan Forecast", "Forecasting", "Performance Metrics" and "Plan Snapshot" headings). Its fixed metric set and labels are: **Total Impressions, Estimated Reach, Avg Frequency, Est. Ad Plays, Avg CPM, eCPM, Share of Voice (SOV), Share of Time (SOT), Total Cost**, plus the context counts **Inventories, Cities, Channels**. Estimated Reach and Avg Frequency are omitted when reach cannot be estimated. **On the media-plan surfaces only** (the presentation Plan Snapshot slide, the Analytics Plan tab and the Excel/PPTX exports) the **Avg CPM** row is goal-driven: it renders as **Avg CPS** (cost per spot) when the pricing mode is CPS (campaign goal `sov`, `ad_plays` or unset) and as **Avg CPM** for impression/reach goals, with **eCPM** always directly below it (total cost ÷ total impressions × 1000) and an inline note distinguishing the effective eCPM from the rate-card Avg CPM/CPS. The same metric set and terminology are reused by the wizard forecasting box and the campaign detail performance tab. The **campaign detail Performance tab is also goal-driven**: when the pricing mode is CPS (goal `sov`, `ad_plays`, `carbon` or unset) the campaign-level headline shows **Avg CPS** (Total Media Cost ÷ Total Ad Plays, "Per Ad Play") and each inventory row / breakdown group shows a **CPS Rate** (row media cost ÷ row ad plays; N/A for static inventory with no plays) instead of CPM. The wizard forecasting box keeps the fixed **Avg CPM** label (the PPTX deck receives the terminology alignment only — no new tables).

| Tab | Reads | Notes |
|---|---|---|
| Plan | Three key-value boxes — **Plan Details** (name, external ID, status, flight dates, duration, currency, channels using the wizard taxonomy, countries, budget, goal), **Buyer Details** (brand, brand category + IAB category ID, client type Direct/Agency, agency, advertiser, DSP, seat ID, planned-by, company) and **Estimated Performance Metrics** (the canonical set above + carbon when present) — followed by a **Targeting Applied** summary, a **City Insights** grid and a **Delivery Breakdown** grid | The headline tab; opens by default. Every empty field/box is omitted. **Targeting Applied now carries the full targeting story in one box** — demographics (segment · share %), audience behaviours, venue environments, behavioural signals, venue mix (type · share %), **cinema mix and retail mix** — each block shown only when it has values; this is where the old Targeting tab's content now lives. Targeting Applied is hidden entirely when nothing is applied. City Insights columns are fixed in this exact order: **City Name · Population · Inventories · Impressions · Reach · Frequency · CPM · Audience Score** (Population is an indicative dummy estimate — no real population source exists yet — derived deterministically from the city name; Audience Score is a deterministic audience/geo-fit score). Delivery Breakdown shows projected **Period · Impressions · Reach** per flight bucket |
| Inventory Details | One row per inventory — Inventory · Channel · Format · City · Media Owner · Schedule · Impressions · Plays/day · CPM | Sortable, filterable |
| Costing | Per-inventory cost rows — Inventory · City · Base CPM · Proposed · Accepted · Impressions · Media Cost · Fee Share · Total, with a totals row; per-fee visibility flags applied | Hidden for advertiser viewers — the tab, the `costing` view array and the Excel sheet are all suppressed for that role. Media-owner viewers see only their own-inventory rows and own fees |
| Operation Details | Per-inventory flight blocks **grouped by classification — Classic, Digital and Mobile** — mirroring the reference media plan. Each inventory block lists one row per dated flight segment (§4.5) with class-specific columns: **Classic** — Start Date · End Date · Operation Days (+ Total Operation); **Digital** — Start · End · Schedule Type · Operation Days · Operation Hours · Start Time · End Time · Total Spots (+ Total Operation / Total Spots); **Mobile** — Start · End · Operation Days · Operation Hours · Start Time · End Time. Each group is hidden when it has no inventories | Hidden for advertiser viewers. Built on the multi-segment dated flights from §4.5 — a panel with two dated windows emits two rows |
| DOOH Schedules | A **week-by-week calendar Gantt** for every digital panel: per-panel header rows (Schedule No · Billboard Name · Start · End · Duration (days) · Operation Hours) with one sub-row per dated segment (1.1, 1.2 …), each shaded across a **Sun → Sat day grid** for the weeks it is active. Below the Gantt sits a **per-inventory cadence table** covering **every scheduled panel — classic and digital** (Inventory · Channel · Pattern · Spots/Loop · Spots/Hour · Active Hrs/Day · Days/Week · SOV), followed by the plan-level union heatmap, named-pattern chips and notable custom-schedule mini-heatmaps as supplementary detail | Built on the inventory-level scheduling system from §4.5. The calendar mirrors the reference sheet and the optimisation wizard's schedule grid so a buyer can verify the operational story without opening the editor. The 30-loops-per-hour digital-screen baseline (`LOOPS_PER_HOUR`) is shared with `computeDigitalAdPlays` so the slide, the analytics tab and the Excel export agree on the same arithmetic. **The tab and its Excel sheet appear under the same condition** — whenever any inventory carries an authored schedule, not only when a digital panel exists. The weekly Gantt is digital-only, but the cadence table spans all scheduled panels, so a classic-only campaign still renders the tab and emits the sheet (Gantt omitted, cadence table shown) — the on-screen view and the `.xlsx` never disagree on which sheets exist |
| Cinema | A per-hall screening schedule for every cinema and operator in the plan — **Operator · Cinema · Hall # · Seats · Session start–end · Movie · Genre · Rating · Runtime → derived ad window · Pre-show** — built from an evergreen film catalogue where every title carries its real runtime, genres, rating and language. The **in-session ad window is derived, not stored**: `ad window = session slot length − feature runtime`, and each session opens with a fixed **2-minute pre-show** premium unit. A plan-level availability strip sits above the tables (**cost in the campaign currency code, impressions**, cinemas, screens, sessions/day, avg ad window, pre-show, films in rotation), followed by a **Films in rotation table listing every scheduled movie** (title · genres · rating · language · runtime · sessions/day) — the slide previews only the first three names, this table carries them all. Each site header shows halls, seats/hall (hall capacity), shows/day, its **ad slots (Pre-show · Mid-show)** and its **site-level cost and impressions** badges. Every figure is deterministic (seeded by inventory × hall × slot) so the analytics tab, the Cinema slide, the Excel sheet **and the campaign detail page's Cinema Availability section render the identical schedule** | Appears only when the plan contains cinema inventory. Films and their runtimes are evidence of the buy environment, never a contracted line item; the tab and its Excel sheet (summary strip → films-in-rotation block → per-site cost/impressions/ad-slots line → screening rows with the Genres column) appear under the same condition, matching the on-screen/`.xlsx` parity rule that governs every other tab |
| Geography Targeting | The targeted geography as a **nested Country › Region/State › City/District tree** (always shown when any geography is targeted, not only when POIs exist). Each node carries per-area metrics — Inventories · Impressions · Reach · Frequency · eCPM · Audience Score. Two flat tables follow the tree, mirroring the reference sheet's Campaign Plan layout: an **Inventory Planning** table (per targeted coordinate mapped to its nearest billboard — Name · Billboard Name · Reference ID · Impressions · Reach · Frequency · CPM · Audience Concentration) and an **Inventories Mapping** table (Name · Lat · Lng · Billboard Name · Reference ID · Distance (m) · State · District). The **targeted POIs** are listed as their own table (Name · Lat · Lng · Radius · Include/Exclude · Inventories matched), not only fed to the map. **Each coordinate is mapped to its single nearest mapped inventory** (the "1 coordinate = 1 inventory" rule, by great-circle distance); an unmapped coordinate leaves the mapping columns blank rather than rendering "NA". A static Mapbox preview at the POI bbox sits alongside when a token and POIs are present. In Excel the tree is flattened into the same columns with the Geography column indented by depth, followed by the same Inventory Planning, Inventories Mapping and Targeted POIs tables | When no geography is targeted, body is the empty state with a deep link to Step 3 |

#### 10.5.4 Themes

Four themes ship in the platform, each carrying primary, secondary, accent, surface and text colours plus a Mapbox style id so chips, charts, slide chrome and the static map's pin colours flip in lockstep:

| Theme | Mood | Mapbox style |
|---|---|---|
| Default | MW blue | `streets-v12` |
| Slate | Editorial neutral | `light-v11` |
| Sunrise | Warm amber | `outdoors-v12` |
| Forest | Calm green | `dark-v11` |

The picker persists the choice per session. A future tenant-branded theme overlays the tenant's logo and palette over Default; this is the surface the white-label flag will hook into.

#### 10.5.5 Logos and fallbacks

Logo placement is uniform across the page so a reader's eye learns where to look:

| Position | Source of logo | Fallback when missing |
|---|---|---|
| Page header | Tenant company | Tenant initials chip in theme primary |
| Cover slide — buyer | Agency, else direct advertiser | Initials chip |
| Cover slide — recipient | Brand record on the campaign | Inventory channel glyph |
| Inventory Snapshots cards | Media owner per row | Channel glyph (digital screen, classic, transit, retail, network, radio, experiential, cinema) |
| Cinema sub-block | Cinema operator | Generic film-strip glyph |
| Audience signals | IAB tier-1 icon for the matched category | Generic tag glyph |

#### 10.5.6 Public share links

The Share button in the header opens a dialog that issues a tokenised, read-only URL. The token is the only credential — the recipient does not need a Planner account. Each link carries the theme (§10.5.4), an expiry (1, 7, 14, 30 or 90 days) and an optional internal-only note so the issuer remembers which buyer or stakeholder the URL went to. The dialog also lists every active link the issuer has previously created with one-click revoke; revocation is immediate and a revoked link returns a 404 from `GET /api/share/media-plan/:token` on the very next request.

Routes:

- `POST /api/media-plan/:campaignId/share` — issue. Authorisation: campaign owner or any user with approval access (§7).
- `GET /api/media-plan/:campaignId/share` — list issued links. Same authorisation.
- `DELETE /api/media-plan/share/:token` — revoke. Same authorisation.
- `GET /api/share/media-plan/:token` — public, no auth. Returns the same view-model the authenticated endpoint returns, with `viewer.isShareLink = true`.

The share-link page itself (`/share/media-plan/:token`) suppresses the theme picker, the View dropdown, Download and Share — it is the page in pure read-mode. A `noindex,nofollow` meta tag is set so search engines never index a leaked link.

#### 10.5.7 Adaptive period bucketing and slide-capacity caps

The Media Plan view is rendered to two surfaces — the in-app analytics tabs and a downloadable PowerPoint deck — and the same view-model drives both. Two design rules ensure that the same view-model never overflows either surface, regardless of campaign duration.

**Adaptive period bucketing.** The Expected Delivery chart and the Why-this-plan-works "Expected Goal Achievement" roadmap both compute the period unit from the campaign's start/end date rather than asking the operator to choose. A 2-week burst, a 3-month flight and a 2-year always-on programme each render a chart and a roadmap with the right number of bars and the right vocabulary; the operator never has to think about it. The rule lives in `shared/lib/adaptive-bins.ts` and is shared between the front-end card, the analytics CSV and the PowerPoint deck.

| Campaign duration | Delivery chart bins | Roadmap label vocabulary |
| --- | --- | --- |
| ≤ 14 days | Daily (one bar per day) | Day 1-5 / Day 6-10 / Day 11-14 |
| ≤ 90 days | Weekly (one bar per ISO week) | Week 1-2 / Week 3-7 / Week 8-13 |
| ≤ 365 days | Monthly | Month 1-3 / Month 4-8 / Month 9-12 |
| > 365 days | Quarterly | Quarter 1-2 / Quarter 3-5 / Quarter 6-8 |

The roadmap **always produces exactly three milestones** (Ramp / Mid-flight / Closeout). The achievement percentages are a live calculation, not fixed checkpoints: the plan's forecast for the goal metric is accrued across the flight days on an S-shaped OOH pacing curve (slow ramp while posters paste, acceleration through mid-flight, steady close), and each milestone shows cumulative planned delivery divided by the campaign's goal target. A plan forecast to over- or under-deliver therefore shows that directly; when the campaign has no numeric goal, the target defaults to the forecast so the roadmap ends at 100%. Three was chosen because it fits both a single PPT slide row and a single mobile screen without scrolling, and because operators consistently described "Ramp / Mid-flight / Closeout" as the three phases they actually report against.

**Slide-capacity caps.** Each slide in the deck has a hard visual budget that the view-model respects up-front, so the PPT renderer never has to truncate or wrap.

| Slide | Cap | Rationale |
| --- | --- | --- |
| Plan Snapshot | up to 11 KPI tiles in a responsive 2-/3-column grid | duration, geography, inventory count, total impressions, estimated reach, average frequency, **Avg CPM (or Avg CPS in CPS pricing mode)**, **eCPM**, share of voice, share of time, total investment. **Estimated Reach and Average Frequency tiles drop when measured reach is unavailable**, so a plan without reach data renders 9 tiles |
| Cost Breakdown (Costing tab) | three-tier ladder + fee table + carbon row | always-expanded grouped table; advertiser mode collapses fees into a single committed total. Lives on the Costing analytics tab, not as a presentation slide |
| Inventory Mix (slide 3) | up to 7 classification × type rows + Total row + 3 stat cards | one row per populated group; selected-but-empty wizard channels render as muted dash rows and never push the table past its budget |
| Cost Breakdown (slide 4) | full cascade + up to 6 custom-fee boxes | the PPTX fee card caps at 6 named boxes and collapses the remainder into one aggregate line; the cascade itself is uncapped because its line count is bounded by the fee count |
| Targeting (Audience Strategy) | 4 stacked label boxes — Demographics (Age/Gender/Income, uncapped) + Venue Types (14) + Behaviour (24) + Interests (24) — with the strongest justification in the header | no charts and no percentages; overflow past a cap collapses to a "+N more" chip; justifications still generated per §10.5.2.1 |
| Audience Trends | reach-buildup curve + 7 day-of-week bars + up to 4 over-index bars | three-feed layout |
| Geographic Plan | 8 city rows | one screen of striped table |
| Audience Map | up to 60 pins layered over up to 20 densest-market heat glows on one auto-fit static map | a single static map; pins are kept to the first 60 in plan order (overflow dropped — on-screen slide captions the count, PPTX omits it), and the heatmap trims iteratively under the Mapbox ~8 KB URL ceiling (pin count → ring detail → lightest markets, falling back to pins only) to stay under ~7,800 chars |
| Execution Plan | up to 4 justification bullets + headline + pattern chips | operational rollup, not a chart |
| Inventory Snapshots | **6 cards per slide (3×2), paginated across ALL planned inventories** | Sub-slide numbers 5.6.1 / 5.6.2 / … so a long plan never truncates. Sorted by daily impressions desc |
| Daypart Heatmap | 24×7 grid (fixed) + up to 5 pattern chips + up to 3 outlier mini-heatmaps (in-app slide only — PPTX export omits them) + Excel/Analytics reference note | standard daypart resolution; per-inventory cadence lives on the DOOH Schedules tab |
| Expected Delivery | bins from adaptive bucketing above | scales but never exceeds chart width |
| Why This Plan Works | 3 facts + 3 milestones + 3 inventory thumbnails | one slide, three columns, three rows |

The same caps are applied to the analytics tabs in the app so the two surfaces stay in lock-step. When more data is available than the cap allows, the view-model picks the top N by impressions or revenue (depending on the slide) and discards the tail; the campaign workspace tabs above the deck remain the path to the full editing surface for operators who need the long tail.

#### 10.5.8 Slide footer notes and exports

**Slide footer note.** Every content slide closes with a single line of muted micro-copy in the footer (`text-xs text-muted-foreground`, no heading, no icon, no navigation). The line is generated deterministically by the view-model from the same data that drives the slide body — it is a one-sentence read-out of what the slide shows and how the headline figure is computed, never a marketing line. The Cover, the empty-state Inventory Snapshots placeholder, and Why This Plan Works render no footer because those slides are themselves the explanation. When the source data is unusually thin the generator emits a short fall-back sentence ("Cities sorted by share of impressions; bounding box derived from selected inventories.") so the footer slot is never blank or showing the word "undefined".

**Per-slide generator.** The view-model owns the strings and the scenario branching is documented per slide below. Numbers are inlined from the same source fields that feed the slide body, with pluralisation handled inside the template so a one-city, one-day plan still reads naturally.

| Slide | Footer note generator (current behaviour) |
| --- | --- |
| 5.1 Cover | _no footer — the slide carries title, brand and prepared-for cards only_ |
| 5.2 Plan Snapshot | `Numbers derived from {N} planned inventor(y/ies) over {D} day(s); CPM is blended across all panels and reach uses the channel-mix benchmark.` Always rendered, and mirrored into the PPTX export as a second line under the pricing-explanation note. |
| 5.4 Goals | `Forecast {forecastLabel} against a target of {targetLabel} ({P}% achieved); achievement = forecast ÷ target.` `forecastLabel` and `targetLabel` carry their own units (impressions, reach, ad plays) and `P` is the integer percentage already used by the in-slide ring. |
| 5.5 Geographic Plan | `{C} cit(y/ies) across {K} countr(y/ies); top market {topCity} contributes {S}% of impressions.` When the plan has no cities (e.g. an empty plan), the fall-back reads `Cities sorted by share of impressions; bounding box derived from selected inventories.` |
| 5.5b Audience Map | `Warmer areas show where the target audience concentrates; each pin marks one inventory site covering it.` Fixed string — the layered glow + pins are the message. When pins overflow 60 or some inventories lack coordinates an extra muted line reads `Showing the first 60 pins; {N} additional sites are on the plan.` and/or `{N} inventor(y/ies) without coordinates {is/are} not shown.` so the pin count reconciles against the plan. |
| 5.6 Inventory Snapshots | Single-page plans render `Inventories sorted by daily impressions; {N} planned panel(s) in this plan.` Multi-page plans replace this on every sub-slide with `Inventories sorted by daily impressions; {N} panels paginated 6 per slide (this page shows {M}).` so the reader sees both the overall total and the per-page count without having to flip back. |
| 5.7 Targeting (Audience Strategy) | _no footer — the strategy sentence moved into the header._ The redesigned slide surfaces the **strongest** justification only, as the one-line subtitle beneath the "Targeting" title (the full list is still computed strongest-first per §10.5.2.1, but the slide shows just the first entry). When the view-model emits no justification (Demo A-style minimum plans), the header fall-back reads `Who, where, and what they care about.` |
| 5.7b Audience Trends | `Top over-indices ranked by signals → behaviours → demographics; index >100 means the audience over-uses an attribute relative to the city baseline.` Fixed string — the slide is itself a ranked feed so the footer explains the ranking rule rather than echoing numbers already on the slide. |
| 5.7c Execution Plan | When the view-model emits at least one cadence justification, the footer is the joined justification list (`bullet · bullet · bullet · bullet`, max four bullets per §10.5.7). Each bullet is a deterministic sentence covering the detected daypart pattern (Default 24/7, Commuter, Nightlife, Business Hours, Weekend or Custom), the flight duration, the active hours per day and the delivery shape (Even, Front-loaded, Back-loaded). When the cadence feed is empty (no schedules captured yet), the fall-back reads `Cadence summary derived from selected daypart patterns and active hours.` |
| 5.9 Daypart & Schedule | `Each cell counts the inventories active in that hour, so darker blocks mark hours where most of the plan runs simultaneously` + when at least one schedule pattern is detected: `; {K} distinct schedule pattern(s) detected across the plan`. The trailing fragment drops on Demo A-style plans with no schedules attached. |
| 5.10 Expected Delivery | `Adaptive {granularity} bins sized to the flight length; the highlighted bar marks {peakLabel}, the period forecast to deliver the most impressions.` `granularity` is the bucket label resolved by the adaptive bucketing rule in §10.5.5 (day, week or month). |
| 5.11 Why This Plan Works | _no footer — the entire slide is the explanation: three insight facts plus the milestone roadmap_ |

**Capacity and overflow.** The footer is one visual line in the on-screen layout and the body region above it uses `overflow: hidden` inside its fixed 16:9 rectangle, so the per-slide caps in §10.5.7 — not scrolling — are what guarantee no slide ever hides text that exists in the data: each capped feed collapses its tail into a `+N more` chip rather than clipping silently. The footer text itself is bound by the joined-justification ceilings already documented per slide (max four cadence bullets joined with " · " on Execution Plan, single-sentence everywhere else); the Targeting slide no longer carries a footer note — its strongest justification sits in the header instead.

**Exports.** The Download dropdown offers PowerPoint, PDF (presentation) and Excel (analytics tables). PowerPoint and PDF render the same per-slide content as the on-screen deck; the Excel workbook is built directly from the Analytics view with **one sheet per on-screen Analytics tab** (Plan, Inventory Details, Costing when the viewer can see costing, Operation Details, DOOH Schedules, Cinema when the plan contains cinema inventory, Geography Targeting), so the per-inventory cadence detail referenced from the Daypart slide and the DOOH Schedules tab ships as the DOOH Schedules sheet, and the workbook's sheet set, column order and hide-when-empty behaviour match the on-screen tabs 1:1. The tabs' explanatory note text and summary stats export too, as note/summary rows: the Costing sheet carries the FX disclosure when the campaign currency differs from the tenant's reporting currency, the Cinema sheet carries the ad-window explainer, and the Geography Targeting sheet carries the Include / Exclude / Matches POI summary shown above the on-screen POI table.

The PowerPoint export is a themed pptxgenjs deck designed to look like a real business document, not a wireframe. The order matches §10.5.2 — Cover, Plan Snapshot, Inventory Mix, Cost Breakdown, Targeting, Audience Trends, Geographic Plan, Audience Map, Execution Plan, Goals, Inventory Snapshots (paginated), Daypart, Expected Delivery, Why This Plan Works. The cover is full-bleed: it pulls the brand logo (or the first inventory thumbnail) as a hero image, lays two dark veils over it for legibility and stacks three glass-style info cards along the bottom (Prepared For, Brand with IAB category, Planned By) — every card pulls the corresponding logo where one is available. Plan Snapshot is a grid of big tinted KPI tiles with faded glyph watermarks, including a goal-driven **Avg CPM** (or **Avg CPS** in CPS pricing mode) tile and a dedicated **eCPM** tile whose context line explains it is the effective total-cost ÷ total-impressions rate. Targeting is a real horizontal bar chart for demographic segments, a venue-mix doughnut, chip clusters for behaviours and signals, and the up-to-three justification bullets generated per §10.5.2.1 — when cinema is in scope a dedicated operator block replaces the targeting summary. Audience Trends is a smoothed reach-buildup line chart, a 7-bar day-of-week chart and an over-index bar chart. Geographic Plan embeds a static Mapbox image with theme-coloured pins, a country-chip strip and a striped city table. The Audience Map is a single Mapbox static image that layers translucent per-market heat glows (larger and darker where more sites cluster) beneath one themed pin per site, auto-fitted to the bounding box of every plotted inventory, with three stat tiles calling out Sites Pinned, Total Inventory and the Densest Market; it fetches the image server-side and falls back to a clean placeholder block when the Mapbox token is missing or the static-image request fails. Execution Plan is a headline strip + four justification bullets + pattern chips + delivery-shape callout. Goals is a real native PowerPoint doughnut chart with the % overlay and three coloured panels (target, forecast, benchmark). **Inventory Snapshots paginates every planned inventory across multiple sub-slides (5.6.1, 5.6.2, …)** in a 3×2 card grid with cover-cropped thumbnails, channel pills and per-card impressions / ad plays / CPM / cost / SOV. The Daypart slide is a 24×7 heatmap painted from the theme's chart palette with an intensity legend, pattern pills, multi-spot summary, a "Notable schedules" text line listing the top-3 outlier inventories with their captions (the on-screen mini-heatmaps don't translate to a slide medium) and the Excel/Analytics reference note pointing to the per-inventory cadence table on the DOOH Schedules tab. Expected Delivery is a native bar chart mirroring the on-screen delivery bars — the peak bin's bar is highlighted in the theme accent — with three callout tiles for granularity, peak bin and peak impressions, matching the on-screen Goals slide's bottom row. Why This Plan Works is three coloured insight cards with watermark numerals beside the three-milestone roadmap and three proof-thumbnails. The deck deliberately ends on the insights — there is no closing or thank-you slide.

#### 10.5.9 History and re-renders

The Media Plan page does not store snapshots — it reads live from the campaign on every page load. Audit history lives on the campaign (§7.5 history) and on the proposal record (§10.4 versions). When the campaign moves through Approved → Active the page redraws automatically because the view-model derives status from the campaign record; no separate cache invalidation is needed.

---

## 11. Creatives and Creative Assignment

Creatives is the library of ad assets — images, videos, audio for radio, MP4 packs for cinema — and Creative Assignment is the binding of those assets to specific line items. A campaign with no creative cannot run, so the platform must verify creative dimensions match inventory specs, route each file to the right supply system, and report on which line items are still unfilled.

### 11.1 What blocks an assignment

The assignment screen is a drag-and-drop board with creatives on the left and line items on the right, plus a bulk-assign button that matches by aspect ratio. Five rules gate every drop:

| Rule | What it does | When the user can override |
|---|---|---|
| Aspect-ratio match | Portrait creative cannot drop on landscape inventory by default | Confirm-override prompt; logged as "forced match" in audit |
| Duration match (video/audio) | Creative duration must equal one of the inventory's accepted slot lengths | Cannot override — must edit the creative first |
| File-size cap | Hard limit per inventory's CMS spec | Cannot override |
| Campaign status | Campaign must be in Reviewing, Approved, Active or Paused — Draft and Planned cannot bind creative | Cannot override |
| Same-spec swap on Approved | On Approved campaigns, only same-spec swaps are allowed without re-approval | Different spec or duration triggers Tier 2 re-approval (§7.3) |

### 11.2 What the dashboard tile reads

The Dashboard's Creative Status Tracker is a read of the assignment table grouped by creative format (Video, Static, Audio). It reports total expected (one per line item on Approved or Active campaigns) versus total bound, and surfaces any line item with a missing or non-matching binding. Clicking a row in the widget jumps the user to the assignment board with that line item highlighted.

### 11.3 Status ripples on campaign state changes

| Campaign moves to | Effect on bound creatives |
|---|---|
| Paused | Delivery status flips to Inactive in performance snapshots; bindings preserved |
| Completed | Bindings preserved; bookings closed; performance snapshot finalises |
| Rejected | Bindings preserved for audit; no delivery |

**How Creatives connect to other modules.** Creative Assignment is the last gate before live spend can accrue. It reads the inventory spec sheet from §5 (aspect ratio, duration, file size) and the campaign status from §3.1 (only Reviewing, Approved, Active or Paused accept bindings). It writes back to the dashboard's Creative Status Tracker (§2) and to the campaign detail's Operation Details tab. On Approved campaigns the same-spec swap rule keeps the audit chain intact — a different-spec swap re-opens Tier 2 of approval (§7) for the affected media owner only, mirroring the price-change ripple in §8.5. A bound creative also generates a creative-delivery package that Planner sends to IMS (§18) so the in-cinema TMS or the digital screen CMS receives the file before air-time.

---

## 12. Statements and Custom Fees

Statements are the billing layer. A statement bundles one or more campaigns into a single invoice and applies custom fees on top. A campaign has at most one media-cost source of truth (the agreed prices in §8), but it may appear on many statements over its life.

### 12.1 What a statement reads, and what blocks the read

The statement builder reads each candidate campaign's agreed prices from Price Management. A campaign is a candidate only if it satisfies all of:

- Campaign status is Approved, Active, Paused, Completed or — for partial bills — Reviewing with at least one owner-approved line.
- Every line item the statement is meant to cover is approved through campaign approval per §8.2; lines still at Counter or Proposed are excluded from the line-item table with a "Pending" footnote.
- The campaign is not currently mid-re-approval after a price change (see §8.5 ripple table) — those campaigns are deferred to the next cycle automatically.

### 12.2 Custom fees and the three bases

Each fee is either a percentage or an absolute amount applied to one of three bases: campaign cost, subtotal (campaign cost + earlier fees), or total (subtotal + tax). The statement preview recomputes on every change so the agency can model fee scenarios before sending. A statement can be split — for example INV-123-A and INV-123-B — when the agency wants to bill different cost centres from the same set of campaigns.

### 12.3 Sync and what each integration locks

Sync status is tracked per finance integration. A statement sent to NetSuite cannot be edited on the Planner side once NetSuite confirms — Planner shows it as Locked and refers further edits to the finance system of record. Zoho and QuickBooks behave the same way. This protects the audit chain: once an invoice is in the books, only the books may amend it.

---

## 13. Carbon emission tracking

Each inventory carries a carbon-per-play value derived from screen wattage, the carbon intensity of the local power grid and the screen size. The campaign forecast multiplies expected plays by carbon-per-play; the actual footprint after run-time is computed from realised plays. The wizard's step 5 forecast box shows estimated carbon alongside impressions and reach, and a campaign-level Carbon Goal Mode (Cap or Minimize) feeds the Recommendation Engine scorer.

The carbon module's only cross-feature impact is on the Recommendation Engine in §6 (it down-ranks high-emission inventory under Minimize) and on the wizard's step 5 warning banner (it warns but does not block when Cap is exceeded). It does not gate approval, reservation or pricing.

**How Carbon connects to other modules.** Carbon is read-only outside its own module. The per-inventory carbon-per-play value lives on the Inventory record (§5) and is never mutated by Planner — IMS owns it. Planner reads it in three places: the wizard's Step 5 forecast box (§4.5), the Recommendation Engine scorer (§6) when goal mode is Minimize, and the Media Plan page's Costing tab (§10.5) where it appears as a Carbon row underneath the cost table. The campaign-level Carbon Goal Mode (Cap or Minimize) is set in the wizard's Step 2 alongside budget and is the only writable carbon field on the campaign — it changes the Recommendation Engine's behaviour but does not change any inventory value.

---

## 14. Tags

Tags are a unified labelling system that lets users group entities — campaigns, creatives, inventories, brands, statements — under arbitrary keywords. Filters on built-in fields like status, country and date cannot express every operational segmentation: a tag like "Q4 Beverages" or "Activate DSP only" collapses what would otherwise be multi-condition filter logic into a single click.

The tags page is the management surface where a user can create, rename, archive and see usage counts. On every entity's detail view, a tag picker with typeahead attaches tags. List views can filter by tag with one click. Tags are scoped to the active company. The most common production use of the system is auto-building statements from a tag — "bill every campaign tagged Q4 Beverages this month" — which lets the agency operate finance-by-segment without maintaining a separate selection list.

**How Tags connect to other modules.** Tags are a horizontal feature that touches almost every entity list. The Campaigns list (§3), the Inventory side panel (§5), the Creatives library (§11), the Brand list and the Statement Builder (§12) all read the same `tag_mappings` table to render their tag chips and filter chips. Usage counts on the tags management page are recomputed every time a mapping is added or removed so a planner can spot dead labels. Because tags are tenant-scoped, switching company in the header (§1) reloads the tag dictionary along with everything else — a tag created under MediaHub Agency does not leak into the user's other tenants.

---

## 15. Signals

Signals are trigger-based rules that change creative or pacing in real time based on external conditions: weather, footfall, traffic, sports outcomes. Composing a signal is a four-part declaration: source (Weather API, Footfall feed, Calendar event), condition ("temperature is below 15 degrees"), action (swap to creative X, pause line item Y, increase share-of-voice by Z%), and the line items the rule applies to. The rule is evaluated on a five-minute cadence in the background.

Signals fire only on **Active** campaigns; a draft, planned, reviewing or approved campaign with attached signals will not trigger them, because the inventory is not yet running. A Test mode lets the planner evaluate the rule against current conditions without firing the action — a dry run before going live.

**How Signals connect to other modules.** A signal is a thin orchestration layer that reads from external feeds and writes back into Creative Assignment (§11) and Pacing on the campaign. The condition language reuses the same data dictionary as the wizard's Audience Signals tab (§4.3) so a rule that targets "footfall above the city median" in the wizard means the same thing in a fired signal. A creative swap fired by a signal is logged in the campaign's audit history (§7.5) and counts against the same-spec rule (§11.1) — a signal cannot bind a non-matching creative. When a campaign moves Active → Paused (§3.1) every attached signal is suspended, and resumes automatically on Active.

---

## 16. Point-of-Interest management

Points of interest (POIs) are a library of locations — latitude, longitude and a radius — used for geofenced targeting and inventory proximity filtering. The user uploads a CSV of POIs (name, latitude, longitude, radius in metres, include or exclude, tags) or creates them manually using a map picker. Each POI carries a tag set, and step 3 of the wizard lets the planner pick one or more POI sets to drive geofencing on the campaign. Radii are in metres and capped at 50 kilometres, and a POI is either an inclusion zone or an exclusion zone, never both. The campaign detail view's Geography Targeting tab renders the uploaded set on an interactive map so the planner can verify the geofence visually after upload.

**How POIs connect to other modules.** A POI set is consumed in three places. First, the wizard's Step 3 (§4.3) writes the chosen sets onto `campaign.targeting.geofencing.pois`. Second, the Recommendation Engine (§6) uses those POIs as a haversine filter against the candidate inventory pool, so a campaign with 7 POIs in Mumbai will not be offered a billboard in Bangalore. Third, the Media Plan page (§10.5) renders each POI as a row in its Geography Targeting tab and as a pin on the static Mapbox preview, with a "Inventories matched" count computed by the same haversine helper the engine uses, so the planner and the counterparty see the same match math.

---

## 17. Explore

Explore is the top-of-funnel discovery surface for browsing every available inventory across the platform without committing to a plan. The page is an interactive map with clustering on the left and a filter panel on the right (country, city, venue type, format, owner, tag). Clicking a marker opens the same inventory side panel used elsewhere; selecting one or more inventories and clicking "Add to Plan" creates a draft campaign with those inventories pre-loaded and drops the user into the wizard. Explore is read-only — it never modifies inventory data — and its filter state is shareable as a link, so a planner can paste a filtered Explore view into a chat and the recipient sees the same set.

**How Explore connects to other modules.** Explore is the only entry point into the wizard that does not start at "name the campaign". The Add-to-Plan button creates a draft campaign with `clientType` defaulted to the active tenant's natural counterparty (Direct for an advertiser tenant, Agency for an agency tenant) and lands the planner on Step 4 with the chosen inventories pre-loaded as Pending reservations (§9.1). The same reservation filter that hides held-by-others inventory in the wizard (§4.4) also hides it in Explore, so a planner browsing Explore on Monday and starting a campaign on Tuesday does not get steered towards inventory that was reserved in between.

---

## 18. The IMS ↔ Planner relationship

Planner is the buyer-facing surface. IMS — the Inventory Management System — is the seller-side system of record. Both must agree on what an inventory is, what it costs, when it is available and how creative is delivered.

**Diagram —** [IMS ↔ Planner Data Dependency](https://miro.com/app/board/uXjVGEva6dA=/?moveToWidget=3458764668843946119_ims) (open in Miro)

Planner reads the inventory catalogue, the classification taxonomy, the cinema showtime feed, ad placement type for cinema, availability windows, rate cards, creative delivery metadata and the workflow integration with the in-cinema TMS. Planner writes back four event types: a booking request once a campaign is fully approved; a hold request whenever inventory is added to a plan or a reservation is made; a hold release on reservation release or expiry; and a creative-delivery package once a creative is assigned.

**The availability store — Planner's local, refreshable copy of IMS bookings.** Availability is too latency-sensitive to fetch from IMS on every plan interaction, so Planner maintains a canonical local availability store that is bulk-refreshed from the IMS feed. A **scheduled full sync runs every six hours**; a planner can also trigger a manual **"Sync now"** from the availability view — the trigger is asynchronous (accepted immediately, single-flight, so a second trigger while one is running is rejected rather than queued), and a status endpoint reports the run's state, trigger source, timestamps, inventory count and any error, which the frontend polls to flip the button back and refresh the data. Every availability read carries the sync metadata alongside the data, which is what powers the stale (>6 hours) and failed-sync warnings on the planning surfaces (§4.4). The recommendation engine reads the same store (§6), so what the planner sees on a card and what the engine scored are always the same facts.

Four known gaps in IMS today force Planner to carry a workaround locally:

| Gap | Planner workaround | When it can be retired |
|---|---|---|
| Cinema operator is not a first-class entity | Curated 86-operator list across 18 countries shipped with Planner | When IMS promotes operator |
| Showtime bands not bucketed in the IMS feed | Bucketing happens client-side | When IMS exposes bands |
| Ad placement type cinema-only | Planner reads cinema only | When IMS unifies the contract |
| Operator premium brands (Director's Cut, Aurum, Onyx) not surfaced as tags | Local label list | When IMS tags them |

---

## 19. Currency and localisation

The platform serves agencies and media owners across South-East Asia, the Middle East and the Americas. Hard-coded dollar signs are wrong nine times out of ten, so MW Planner displays the international currency code (USD, MYR, SGD, AED, IDR, PHP, VND, INR, GBP, EUR, AUD) inline with every monetary value rather than a symbol. Cross-campaign aggregations (statements, dashboard widgets) convert to the active company's reporting currency using the FX rate from the campaign's start date for stability.

**How currency rules apply across modules.** Currency is a per-campaign field, set in the wizard's Step 2 (§4.2) and frozen at submit. The dashboard widgets (§2), the Media Plan page (§10.5), Price Management (§8), Statement Builder (§12) and every export (PDF, PowerPoint, CSV) read the same field and prefix every figure with the same code. Cross-campaign rollups — the Sales Performance widget, the multi-campaign statement total — convert to the active tenant's reporting currency using the campaign-start FX snapshot, so a statement total never silently moves with today's spot rate. The "FX as of campaign start" rule is the single fact a finance reviewer needs to remember when reconciling Planner numbers against an external ledger.

---

## 20. Profile Settings

Profile Settings is a self-service page where users review their account information, change their password, and configure notification and privacy preferences. The page sits at `/profile`, accessible from the user avatar menu in the header. It is organised into four tabs that move from identity to security to communication preferences.

The **Profile** tab displays the user's personal information — full name, email address, phone number, role, company and country — alongside their avatar. Every field on this tab is read-only; personal details are managed by administrators through Admin Console, not by the users themselves, because role and company assignments carry authorisation consequences that a self-service edit would bypass.

The **Account** tab shows the account's status (an active badge with the creation date), a Change Password flow, a Two-Factor Authentication placeholder and an Active Sessions display. The Change Password button opens a side drawer with three fields — Current Password, New Password and Confirm New Password — and a live requirements checklist (eight or more characters, at least one uppercase letter, at least one lowercase letter, at least one number, passwords match). Each requirement flips from a grey cross to a green tick as the user types; the Update Password button stays disabled until all five are satisfied. The backend verifies the current password via scrypt, validates the new password's length, hashes and persists the change, and returns descriptive errors for a wrong current password or a too-short new password.

The **Notifications** tab provides toggle switches for Email Notifications, Push Notifications, Campaign Updates, Proposal Alerts, System Updates and Marketing Emails, with changes persisted via the notifications API. The **Privacy** tab offers a Profile Visibility dropdown (Everyone, Team Members Only, Private), plus toggles for Show Email Address, Show Phone Number and Allow Direct Messages, with changes persisted via the privacy API.

**How Profile Settings connects to other modules.** The page reads the user record written by Admin Console (§7.7) and the session established at sign-in (§1). The Change Password flow writes back to the same credential store that sign-in reads, so a password change takes effect on the next sign-in without a session reset. Notification preferences feed the same alert pipeline that the dashboard tiles, the reservation queue and the approval inbox draw from — toggling Campaign Updates off silences the toast and email notifications a planner would normally receive when a campaign they own changes status (§3.2).

---

## 21. Demo Mode and Test Mode

Demo Mode is the ecosystem-wide data partition that keeps demonstration and experimentation activity out of live commercial data. It follows Chapter 21 of the Admin Console PRD: every row of business data carries a `data_mode` stamp (`live` or `demo`), assigned by the server at creation time and never trusted from the client. Reads are partitioned by the caller's *effective mode*, so a user in demo mode sees only demo rows and a user in live mode sees only live rows — the two worlds never mix in lists, dashboards or analytics.

**Two switches, one effective mode.** An organisation can be flagged as a demo org (`is_demo` on the company record, set through Admin Console), and any individual user can flip a personal **Test Mode** switch in the Planner top bar — a Testnet-style toggle with a flask icon. The effective data mode is demo when *either* the org is a demo org *or* the user's Test Mode is on. For users of a demo org the switch is permanently locked ON: the toggle renders disabled with a tooltip and the API rejects any attempt to turn it off. The switch state is served by `GET /api/me/test-mode` (returning the flag, the org lock and the resulting effective mode) and changed via `PUT /api/me/test-mode`, which returns 403 for demo-org users. The personal Test Mode flag is **persisted server-side per user** — it survives sign-out, new sessions and new devices, so a user who leaves Test Mode on will re-enter the demo partition next time they sign in until they explicitly turn it off.

**Where the partition is enforced.** The stamp is assigned server-side at creation time (a client-supplied `data_mode` is ignored — the partition is spoof-proof) and read-side enforcement covers every surface a campaign is reachable from: campaign lists and by-ID reads (a cross-mode by-ID access returns 404, exactly as if the record did not exist), dashboard aggregates and analytics, the Plan Approval inbox, the price-management routes (including custom fees and price history reached via child-record ids), and **public share-link generation** — a planner can only mint a share link for a plan in their own partition. Share-link *consumption* stays mode-agnostic by design: the token itself is the access boundary for the anonymous viewer.

**What gets stamped.** Campaigns, proposals, clients, activities, brands, creative assets, statements, tags, POIs and inventories all carry `data_mode`. Creation endpoints stamp the value from the acting user's effective mode; the activity log derives its stamp centrally so audit entries always land in the same partition as the action that produced them. Rows created before the feature existed default to `live`.

**What the user sees.** While the effective mode is demo, an amber banner sits under the top bar stating that everything created is demo data and is excluded from analytics. Demo rows carry a small amber **DEMO** badge wherever they appear in lists and cards. Toggling Test Mode reloads the workspace so every partitioned cache is dropped — the flip is a hard boundary, not a soft filter.

**Analytics exclusion.** Dashboard statistics, sales aggregates and every other analytics read exclude demo rows for live-mode viewers. Demo activity therefore never contaminates revenue reporting, utilisation metrics or performance summaries.

**Demo-to-live conversion.** When a demo org converts to a paying customer, an internal-only purge hook (`POST /api/companies/:id/purge-demo-data`) deletes all of the org's demo-stamped rows in foreign-key-safe order — campaign children (approvals, negotiations, reservations, fees, history, comments, handoffs, share links, line items) before campaigns, statement children before statements, then the remaining demo entities — resets every member's Test Mode flag, and optionally clears the org's demo flag in the same call. The endpoint returns per-table deletion counts for the audit trail.

**How Demo Mode connects to other modules.** The org flag is written by Admin Console (§7.7) and read at every request; the Test Mode switch lives in the Planner header next to notifications (§3). Because stamping happens server-side in the same code paths used by campaign creation (§4), proposals (§10), statements (§12), tags (§14) and POI management (§16), no module needs demo-awareness of its own — the partition is enforced underneath all of them.

---

## 22. Open gaps vs competing planners

This section tracks planning-side feature gaps between MW Planner and the leading global OOH planning tools (Mediaocean Lumina, Talon Ada, Kinetic Journeys, Vistar, Hivestack, VIOOH, Place Exchange, AdQuick, Posterscope PRISM, Kantar Vivvix, Geopath Insights). Adserver, creative trafficking and programmatic execution gaps are out of scope here — those belong to Influence and Activate respectively and are tracked in their own product specs.

The convention for this section is: every gap below is a feature MW Planner does not yet ship. When the team builds a gap into the product, the entry is removed from this section and rewritten as a fully specified subsection in the relevant main section (§4, §6, §7, §10 etc.) — with user journeys, validations and acceptance criteria, in the same voice as the rest of this document. This section therefore shrinks over time and the main sections grow.

Each gap entry below states what the feature is, who in the market exposes the gap, what MW Planner does today in that direction (so the delta is clear), and which section of this PRD the built feature would live in.

### 22.1 Audience-first / movement-derived inventory recommendation

Modern audience-led planners — Talon Ada and Kinetic Journeys are the reference implementations — start the planning act from an audience rather than from the inventory catalogue. The planner picks a target ("evening commuters in Mumbai who index high on Food & Drink"), the system pulls mobile-panel movement data (Adsquare, Near, Locomizer, Cuebiq), models the likely commute graph, and proposes inventories *along the journey* rather than inventories *inside a radius*. The output is a route-aware plan with a defensible audience narrative attached to every selected panel.

MW Planner today supports demographics, behaviours, signals, geofencing POIs (CSV upload with lat/lng/radius) and venue types in step 3 of the wizard, and the Recommendation Engine (§6) intersects those against the candidate pool. What it does not do is walk a panel-derived journey graph or score inventories by their position on a commute path. The audience movement panel is properly the job of Measure / Seetar; the planner gap is that the wizard does not yet consume a "journey object" from Measure.

When built, this feature is an extension of §3 (Campaigns) targeting inputs, §4 (Wizard) step 3 and §6 (Recommendation Engine) scoring.

### 22.2 Scenario planning and side-by-side what-if

Mediaocean Lumina, Talon Ada and the Hivestack planner let a planner build two or three plan variants under a single brief — Hi/Med/Lo budget, or audience A vs. audience B, or classic-heavy vs. cinema-heavy — and compare cost, reach, frequency, CPM, carbon and SOV side by side before the brief is even sent for approval. The winning variant is promoted to the submitted plan; the losers are archived as alternatives on the same campaign record.

MW Planner today supports exactly one plan per campaign. A planner who wants to compare scenarios must duplicate the campaign, edit, and end up with two unrelated draft records, two approval threads, and no built-in comparison. This is the single most common reason a Tier-1 holdco evaluating MW Planner against Lumina raises a blocker.

When built, scenarios are a new "scenario set" parent that holds 2-3 plan variants and a comparison view. Lives in a new §4.6 (Scenarios), with knock-ons in §7 (only the promoted scenario enters approval) and §10 (the proposal can optionally show the comparison matrix as an annex).

### 22.3 Constraint-based goal optimisation

Modern planners expose an objective plus a set of constraints — "maximise reach, cap average frequency at 2.5, do not exceed MYR 80,000, deliver at least 30% of impressions to Klang Valley, exclude any inventory within 200 m of a competitor brand outlet" — and the optimiser proposes the inventory mix that satisfies all constraints simultaneously. Vistar and Hivestack offer this for programmatic execution; Lumina offers a direct-buy version.

MW Planner today captures one goal in step 2 (Reach, Frequency, Impressions, Awareness). The Recommendation Engine treats the goal as a forecast target, not a hard constraint. There is no multi-constraint solver, no frequency cap honoured by the optimiser, no minimum-per-region floor, no competitor-adjacency exclusion. Frequency *capping during delivery* is correctly Activate's job, but the planner cannot today *promise* a frequency-capped plan up front because the optimiser does not respect a cap.

When built, this is an extension of §6 (Recommendation Engine) — the scoring loop gains constraint terms — and §4 (Wizard) step 2 — the goal input becomes goal + constraints.

### 22.4 Brief intake and AI plan drafting

AdQuick, the newer Lumina releases and the Vistar 2026 roadmap accept a free-text brief (or an uploaded PDF RFP) and pre-populate the wizard — countries, budget, dates, channels and a candidate inventory list — before the planner touches a single field. The planner becomes an editor of an AI-drafted plan rather than the author of a blank one. The brief-to-draft moment is where junior planners save 60–80% of plan-creation time on a busy desk.

MW Planner today is a structured five-step wizard where every field is entered manually. The Recommendation Engine fires only after step 4 is reached, by which point all the structured input has already been hand-entered.

When built, this is a new §4.0 "Brief intake" that precedes the wizard's step 1 — an LLM call against the wizard's existing field schema produces a draft, the planner accepts/edits, the wizard opens pre-filled.

### 22.5 Plan templates and brand playbooks

Talon Ada and Posterscope PRISM let a senior planner save a brand playbook — "McDonald's QSR — auto-applies street furniture + airport + retail, default lunch/dinner dayparts, brand-safety exclusion list (competitor QSRs within 100 m), default carbon ceiling, default Tier-1 approver" — and a junior planner starts from the playbook rather than from blank. Brand standards are enforced by construction rather than by training. This is the operational moat for agencies running multi-market global brands.

MW Planner today lets a planner duplicate an existing campaign. There is no abstracted template, no parameterised playbook, no enforcement of brand standards beyond what gets hand-copied at duplication time. Tags (§14) organise but do not standardise.

When built, this is a new §4.7 (Templates) plus a small admin surface in the Admin Console for managing templates at agency/brand scope.

### 22.6 Competitive intelligence and benchmarks at plan time

Kantar Vivvix, Geopath Insights and the holdco planners (Talon, Kinetic) surface competitor spend live inside the planning UI: "Your brand is currently delivering 12% SOV in QSR across Mumbai DMA — your nearest competitor is at 31%; here is the inventory mix that would close the gap". The benchmark sits alongside the planner's working figures, not in a separate report.

MW Planner today renders one benchmark line in the Media Plan's *Why This Plan Works* slide ("18% below Food & Drink category benchmark"), but that benchmark is hard-coded from an in-repo table rather than fed from a live competitive-spend source. The data feed is properly Measure's job; the planner gap is that the wizard and Media Plan do not yet consume a competitor-spend feed.

When built, this slots into §4 (Wizard) step 4 as a benchmark strip and into §10 (Proposals) as a benchmark slide driven by live data rather than a hard-coded table.

### 22.7 RFP / brief broadcast to multiple media owners

Mediaocean, AdQuick and VIOOH let a planner author a single brief and broadcast it to N media owners simultaneously, collect their proposed inventory and pricing into a comparison matrix, and pick winners across owners. The brief carries targeting, budget envelope, dates and creative spec; the owners reply with their proposed lines; the planner picks across the merged set.

MW Planner today has *Request for Deal* (§7.6) which is the closest analogue, but its current scope is per-campaign / per-approver — an action against an in-flight plan rather than a discovery-side RFP broadcast. The discovery flow ("I don't know which owner has the right airport screens in three markets, please all propose") is the gap. This matters for mid-market agencies in emerging markets who lack direct relationships with every owner, and it is also a structural advantage MW Planner is uniquely positioned to build — the multi-tenant fabric already touches both buyer and owner.

When built, this becomes a new §4.8 (RFP broadcast) on the planner side and a new owner-side inbox view, with the reply flow reusing §8's negotiation infrastructure.

### 22.8 Programmatic deal-ID handoff to 3rd-party DSPs

Vistar, Hivestack, VIOOH, Place Exchange and Lumina all generate programmatic deal IDs (PG, PMP, OMP) that flow into The Trade Desk, DV360, Adelphic and the rest. The planner picks inventory in the planning tool, the system writes the deal, the buyer's DSP picks it up automatically and executes.

MW Planner today produces direct deals only — reservations and approvals are written into the platform's own bookings tables. The programmatic execution path is intentionally routed through Activate (Moving Walls' own DSP), so this gap is by design for the Activate-as-execution-layer positioning. The gap re-opens the moment a holdco trading desk wants to execute on TTD or DV360 instead — there is no IAB-OpenRTB-2.5-flavoured deal-ID handoff today. Whether to close this gap is a positioning decision rather than a missing capability.

When built, this is a new §9.x (Reservations → Deal IDs) surface that emits a deal ID per reservation, with the actual auction execution remaining outside MW Planner.

### 22.9 Secondary gaps

These each show up in agency RFPs and each is individually smaller than the eight above, but together they constitute the bulk of "feature parity" objections from buyers comparing tools side by side.

| Gap | What the competitor does | MW Planner today | Where the built feature would live |
|---|---|---|---|
| Plan-level frequency capping | Cap average frequency at plan submit | Goal type captures Frequency as a target, not a cap | §4 step 2 + §6 (define), Activate (enforce) |
| Carbon-constrained optimisation | Optimise within a CO₂e ceiling | `carbonBudgetKg` field exists; optimiser does not honour it | §6 (Recommendation Engine) + §13 (Carbon) |
| Forward inventory availability heatmap | Year-view "what's free in Q3" | Inventory Utilisation widget is historical | New subsection in §5 (Inventory) |
| Buyer-side brand safety / competitor adjacency | Exclude inventory within X m of named competitor outlets | Owner can blacklist; buyer cannot | §4 step 3 (Targeting) |
| Creative-spec fit at plan time | Validate creative aspect ratios / file sizes against picked inventory before submit | Validated downstream in Creative module | §4 step 4 preview + §11 (Creatives) |
| Virtual creative staging on actual screen | Render artwork on the real billboard / cinema lobby | Thumbnails only | §11 (Creatives), backed by Studio assets |
| Plan portfolio / multi-campaign view | "All my client's plans this quarter, by market, by spend, by SOV" | Per-campaign list with filters | New subsection in §3 (Campaigns) |
| Cross-plan audience dedup | Model unique reach across N concurrent plans for one client | Per-plan reach only | §6 + §3 portfolio view |
| Forecast vs delivered back-test | "Last quarter your forecasts under-delivered by 8% — here is the corrected curve" | Delivered shown in completed status; no calibration loop | §6 (Recommendation Engine) calibration |
| BI warehouse push | Push every plan into Snowflake / BigQuery for client BI | Excel / PPTX / PDF exports only | New subsection in §10 (Proposals) or a new §24 |
| Configurable approval workflows | Tenant defines 2/4/5-stage workflow with custom roles | Fixed two-tier (Agency → Internal → Owner) | §7 + Admin Console |
| ERP / PO finance reconciliation | Match delivered to insertion order in SAP / Oracle / Mediaocean Prisma | Statement builder is internal | §12 (Statements) + Admin Console finance integrations |
| 3rd-party audience segment marketplace | Import Adsquare / Lotame / LiveRamp segments at targeting time | Internal demographics + signals only | §4 step 3 (Targeting) + §15 (Signals) |
| Western-market currency body integration | Pull Geopath / Route / COMMB / MOVE 2.0 impressions natively | Internal model + IMS; Seetar in APAC/MEA | §18 (IMS) + Measure |
| Multi-market regional roll-up | "SEA region" plan that aggregates per-country plans with local fees and currencies | Countries multi-select per plan; no regional roll-up | New subsection in §3 (Campaigns) |
| Bulk campaign duplication across markets | Roll one playbook out to 8 countries in one click | Per-campaign duplicate | §3 (Campaigns) bulk actions |
| Recommendation explainability | "Top three features that put this inventory in the plan" | Run ID recorded; feature contributions not surfaced | §6 (Recommendation Engine) explainer panel |

### 22.10 Suggested build order

The five gaps below close the largest commercial deltas for the least build effort and are recommended for the next planning cycle. The remainder follow once the foundational ones are in.

1. **Scenario planning (§22.2)** — highest impact for contained build, biggest "we lost the deal because…" objection in evaluations.
2. **Plan templates / brand playbooks (§22.5)** — high impact, contained build, sticks senior planners to the tool because their IP gets built inside it.
3. **Brief intake and AI plan drafting (§22.4)** — strong sales demo, lowers junior-planner onboarding friction, contained LLM-against-schema build.
4. **Constraint-based optimiser (§22.3)** — high impact, larger build inside the Recommendation Engine v2 work that is already scoped (`docs/recommendation-engine-v2-planner.md`).
5. **RFP broadcast (§22.7)** — high impact for emerging-market positioning, plays to the multi-tenant marketplace edge, reuses negotiation infrastructure.

The remaining primary gaps — audience-first recommendation (§22.1), competitive benchmarks (§22.6) and deal-ID handoff (§22.8) — are larger and more dependent on sister products (Measure feeds the first two, Activate is the natural endpoint for the third), so they are better staged after the planner-internal gaps are closed.

---

## 23. Roadmap (deferred items)

| Item | Status |
|---|---|
| Sequential multi-channel state machine (full UI) | Deferred |
| Plan Summary overlay full-screen route | Deferred |
| Schedule rename cosmetic sweep | Deferred |
| Cinema operator first-class in IMS | Requested of IMS team |
| Showtime bands first-class in IMS | Requested of IMS team |
| Unified ad-placement contract | Requested of IMS team |
| Operator premium brand tags in IMS | Requested of IMS team |

---

## Appendix A — Worked example: "Spring Beverages 2026"

A regional beverage brand wants a 30-day awareness campaign across India and Malaysia, mixing premium cinema with high-traffic urban billboards. The brief: 4,000,000 USD, reach 35 million unique adults aged 18–44, peak in the second and third weeks.

**Step 1 — Wizard.** The planner — *Jane, MediaHub Agency* — names the campaign, picks the 30-day chip, sets channels Digital OOH + Cinema. In step 2 she enters 4,000,000 USD and splits the budget 60/40 between Digital OOH and Cinema using the channel allocator. In step 3 she opens the Inventory Type tab and selects Billboard, Street Furniture and Transit under Digital OOH, picking Bulletin, Digital Billboard and Wallscape formats from the side drawers. She then configures the Cinema sub-tab (PVR INOX, Cinépolis India, GSC, TGV; pre-show; weekday/weekend prime; selected genres and ratings). Step 4 fires the Recommendation Engine, which scores and returns a plan excluding 22 inventories already held by other tenants. Jane reviews the recommended set, opens the manual editor to swap two low-scoring Delhi panels for higher-traffic Gurugram alternatives, saves, and accepts the schedule defaults in step 5. Submit.

**Step 2 — What submit triggered.** The campaign moves Draft → Planned. Tier 1 — Jane's company sign-off — opens and waits on the line-item prices being proposed. Reservation requests are written for all 240 inventories — eight media-owner queues see new rows simultaneously. Negotiation threads open at rate-card for every line item; the 7-day Day-X-of-7 timer starts on each.

**Step 3 — Reservations come back.** Six of eight media owners approve their holds inside 24 hours. Two billboards in Delhi are declined; Jane sees the decline notifications and uses Modify Plan to swap them for two in Gurugram, which auto-write fresh hold requests. Within 48 hours all holds are Reserved.

**Step 4 — Negotiation.** Jane applies a +7% Difference (price reduction) on the Mumbai PVR cluster, saves through the Summary drawer and submits the campaign from plan detail. The PVR seller — *Raj, PVR INOX* — opens the approval drawer and counters with a +4% Difference plus a bonus week; the campaign stays Reviewing — PVR's per-owner state flips to Countered — and only PVR's rows unlock for Raj. Jane reviews the counter, agrees, and Raj re-submits; the turn flips and the campaign re-enters approval. When Raj approves, PVR's rows turn light-green with the inventory-level Approved badge — campaign approval is the acceptance; no per-row Accept clicks are involved.

**Step 5 — Approval completes.** Tier 1 — Jane's company — approves once every line has at least a Proposed price. Tier 2 advances per owner — four Approve, four already approved when their negotiation locked. Once the last owner row turns green, the campaign flips Reviewing → **Approved**. All 240 reservations auto-convert to Booked. The lock banner appears. Creative Status Tracker on Jane's dashboard now shows "240 line items, 0 creatives bound" in red.

**Step 6 — Mid-flight amendment.** A week into Active, the brand asks for a price reduction on Bangalore. Jane clicks "Edit pricing — re-open approval" on one Bangalore line. The campaign reverts to Reviewing. Tier 1 stays Approved. Only the Bangalore owner's Tier 2 row resets to Pending. The approved line returns to Proposed. The other 239 lines stay locked. The Bangalore owner approves the new price within hours; the campaign returns to Approved; spend continues uninterrupted at the new rate from the next billing cycle. Audit history shows: "Jane Doe re-opened pricing on line BLR-42 at 14:02; Bangalore Outdoor approved at 16:48; campaign re-Approved at 16:48".

**Step 7 — Statement.** End of month, Jane builds a statement for the Q2 cycle. The campaign is included with the new Bangalore price applied from the amendment date forward. Custom fee — 12% agency commission on subtotal — applied. Statement sent. NetSuite syncs overnight; the row in Planner now shows "Locked — see NetSuite for amendments". Audit complete.
