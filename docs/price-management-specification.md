# Price Management — Detailed Behavioral Specification

Owner: Product (Sanchit Neema, with Agent assistance)
Last updated: August 5, 2026
Status: Canonical — this is the thorough, developer-facing behavior spec for the Price Management module. The PRD (§8) carries the product-level summary and must stay aligned with this document. Where the two disagree, fix the disagreement; do not pick a winner silently.

---

## 1. Purpose and scope

Price Management is the workspace where the plan creator converges on final line-item prices before submitting the campaign, and where every party later sees the agreed numbers. Rate cards are a starting point, not a contract: real OOH prices are negotiated per line, with price differences, bonus inventory and share-of-voice changes. The platform records every change so neither party can later claim a different price was agreed.

This document specifies, in full behavioral detail:

1. The pricing table — columns, the Difference model, two-way editing, the schedule accordion, even-split division.
2. The Summary drawer — summary of changes, custom fees, the cost cascade (Net Cost, Total Cost), the approval acknowledgement, the guided handoff.
3. The History drawer — the complete change log, including custom-fee history.
4. Submit Plan and the approval process.
5. The Execution Plan and Influence gating.
6. Comments and company-level tagging.
7. Roles and visibility rules.
8. Edge cases and their resolved answers.

**One rule frames everything: there is no Accept action anywhere in this module. Campaign approval IS the acceptance.** No per-row accept, no bilateral handshake, no internal acceptance step. Counterparties get exactly two moves: Approve or Counter Offer.

---

## 2. The pricing table

One row per inventory line item; an accordion under each row expands to that inventory's schedules. Prices can be reviewed and edited at either level.

### 2.1 Columns

| Column | Visibility | Behavior |
|---|---|---|
| Inventory Name | Default | Accordion handle; expands to the schedule rows beneath |
| Date Range | Default | The line item's flight window |
| Time Slot | Default | Daypart/slot at schedule level; rolled up at inventory level |
| SOV / Ad Plays | Default | Share of voice for digital loops; ad plays where applicable |
| Media Owner | Default | The counterparty whose approval this line will need |
| Impressions | **On-modify** | Hidden until the table is modified |
| Bonus Type | **On-modify** | Bonus inventory / value-add attached during negotiation |
| Difference | **On-modify** | The negotiated % between Initial and Proposed — see §2.3 |
| Monthly Rate / CPM Rate / CPS Rate | Default, goal-driven | See §2.2 |
| Initial Price | Default | Immutable baseline — see §2.4 |
| Proposed Price | Default | The editable negotiated price |

**On-modify columns** appear when the user modifies the table (applies a Difference, a bonus, or an impression change). Their visible/hidden state is **persisted per campaign, not per user session** — once the table has been modified, every party opening that campaign's Price Management sees the same extended column set. Rationale: the counterparty must be able to see *what changed*, not just the final number.

### 2.2 The goal-driven rate column

Exactly one rate column shows per row, chosen by inventory class and campaign goal:

- **Classic inventory** → **Monthly Rate**.
- **Digital inventory, goal = impressions or reach** → **CPM Rate**.
- **Digital inventory, goal = ad plays, share of voice, or no goal selected** → **CPS Rate**.

This is the same goal-driven rule the media plan deck uses (PRD §10.5), so the table and the deck always show the same rate type for the same plan.

### 2.3 Difference

**Why "Difference" and not "Discount".** A proposed price can be **higher** than the initial price; a discount would then read as a negative discount ("−12% discount"), which is nonsense. **Difference** is direction-neutral: it expresses how far the proposed price moved from the initial price, in either direction.

**Formula and sign convention (used on every surface — table, Summary, History, audit):**

```
Difference % = (Initial − Proposed) / Initial × 100
```

- Price **reduction** → **positive** Difference. Initial 5,000 → Proposed 4,000 = **+20%** (buyer pays less).
- Price **increase** → **negative** Difference. Initial 5,000 → Proposed 7,000 = **−40%** (buyer pays more).

A positive Difference always means "cheaper than initial", everywhere.

**Two-way editing.** Difference and Proposed Price are two views of one fact:

- User types a Difference % (positive or negative) → `Proposed = Initial × (1 − Difference/100)` auto-fills.
- User overrules the Proposed Price with an exact amount → Difference % recalculates from the entered price, displayed to two decimals. **After an overrule, the entered price is the source of truth**; the % is a derived display value. The system never re-rounds a hand-entered price to make the percentage prettier.

**Bulk operation.** Selecting rows switches the tips carousel to an action bar: Apply Difference, Apply Bonus, Change SOV, Clear Selection. A bulk Difference applies the same % to every selected row under the same sign convention.

**Special display values:**

- **Bonus lines (Initial = 0):** the Difference is mathematically undefined → show a **dash (—)**, never 0%, never ∞.
- **Mixed schedule movements:** if an inventory's schedules moved in different directions (e.g. one schedule −10%, another +15%), the inventory row shows **"Mixed"** instead of a misleading blended percentage. The accordion shows each schedule's own Difference. (The blended number still exists internally for the Summary aggregate; "Mixed" is a display rule for the inventory row only.)

### 2.4 Initial Price is immutable

**Initial Price never changes — not across counters, not across re-submits, not across approval rounds.** It is whatever the system originally calculated (rate card × flight math) when the line item entered the plan. Only the Proposed Price moves, and the History drawer (§4) records every move. Consequences developers must respect:

- The Difference % is always relative to the *original system-calculated* price, so it reads as "total negotiated movement to date", not "movement in this round".
- Round-over-round movement (e.g. "their counter vs my last offer") is a History-drawer concern, not a table concern.

### 2.5 Schedule accordion and inventory-level editing

- **Editing a schedule row** changes only that schedule. The inventory-level Proposed Price becomes the **sum of its schedules**.
- **Editing at the inventory level** divides the entered price **equally across the inventory's schedules** — an even split, deliberately not weighted. With 3 schedules and a 12,000 entry, each schedule gets 4,000. Rationale: the user priced at inventory level, so the system has no basis to know how the user wants it distributed; equal shares is the only honest default. A short inline message tells the user this is what will happen, and the user acknowledges it.
- **Rounding:** when the amount does not divide evenly, the remainder lands on the **last schedule**, so schedule prices always sum back to exactly the entered inventory price.
- **Overwrite warning:** if the user has hand-tuned schedule prices and *then* edits the inventory-level price, the even split would flatten that work. The system does **not** interrupt on each keystroke — it warns **when the user clicks Save** (in the Summary drawer): "Inventory-level prices you entered will be split equally across schedules, replacing schedule-level prices you set on: <list>." The user confirms or goes back.
- An inventory-level Difference % applies the same % to every schedule (mathematically identical to an even split of the resulting amount).

### 2.6 Read-only surfaces

The map view and the calendar/availability view are strictly read-only visualisations. No pricing is editable anywhere except the table.

---

## 3. The Summary drawer

Opened via the **Summary** button. Three jobs: show what changed, manage custom fees, and walk the user into the approval flow. Fees live **only** here — never on schedule rows.

### 3.1 Summary of changes

Lists every line whose Proposed differs from Initial, showing Initial Price, Proposed Price and Difference % — with the **same sign convention** as the table: 5,000 → 4,000 shows **+20%** in both places; 5,000 → 7,000 shows **−40%** in both places. The aggregate Difference row expands to a per-inventory breakdown of blended Difference %.

### 3.2 The cost cascade — Net Cost and Total Cost

| Line | Definition | Notes |
|---|---|---|
| Media Cost | Sum of Initial Prices | Read-only baseline |
| ± Difference | Media Cost − Σ Proposed Prices | Positive = plan got cheaper; negative = more expensive. **Proposed Media Cost** = Σ Proposed Prices |
| + Custom fees **not** included in the media plan | Each listed individually, added **on the Media Cost** | % fees compute on the Proposed Media Cost. These fees fold into Net Cost — the client-facing plan never shows them as separate lines |
| **= Net Cost** | Proposed Media Cost + not-included fees | Current-vs-proposed comparison happens at the Proposed-Media-Cost level |
| + Platform Fee | **% of Net Cost**, read-only | Set at company onboarding; never editable in Price Management |
| + Custom fees included in the media plan | Each listed individually, added **on top of Net Cost** | % fees compute on the Net Cost; itemised client-side alongside the Platform Fee |
| **= Total Cost** | Net + Platform Fee + included fees | The number every other surface (media plan deck, Costing tab, Snapshot, exports) must reconcile to |

**Worked example.** Initial media cost 210,000; proposed prices sum to 200,000 → Difference row **+10,000 (+4.76%)**; Proposed Media Cost 200,000. A 10% internal ops margin *not* in the plan adds 20,000 on media → **Net Cost 220,000**. Platform Fee 5% of Net = 11,000. An 8% agency service fee *included* in the plan computes on Net = 17,600 → **Total Cost 248,600**.

**Fee placement rule.** The fee's *include in media plan* toggle decides **placement**: excluded fees are cost components on the Media Cost (part of the Net Cost the buyer sees); included fees are line items on top of Net, next to the Platform Fee. Per-fee viewer **visibility** decides only whether the fee is *named* to a given viewer — never whether its amount is counted. Hidden pre-net amounts fold into the displayed Media Cost; hidden post-net amounts aggregate into one unnamed "Additional fees" line. Totals are identical for every viewer.

### 3.3 The approval acknowledgement

Beneath the cascade: *"Plan approval will be required. The media plan will be updated with the proposed prices, but approval is required for execution."*

- The user must tick this checkbox to enable the **Save** button. Unticked drawer cannot save.
- The checkbox **resets every time the drawer opens** — it is an acknowledgement of *this* batch of changes, not a one-time consent.

### 3.4 After Save — the guided handoff

On Save:

1. Any pending even-split overwrite warning fires first (§2.5); the user confirms or goes back.
2. The **media plan page and the campaign view-detail page update immediately** with the proposed prices.
3. The drawer explains the next step — *"To start approval, submit the plan from the plan detail page"* — with a **Go to plan detail** button landing on the view-detail page, where **Submit Plan** lives. The **Save confirmation toast carries the same Submit call-to-action**: the creator can submit directly from the toast without navigating to view-detail. The toast CTA runs the **identical eligibility checks and confirmation step** as the view-detail button (creator only, status Draft/Planned/Negotiating) — it is a shortcut, not a bypass. The toast is transient; the persistent Submit button on view-detail remains the canonical entry point.

Saving and submitting are deliberately two separate acts: **Save records the numbers; Submit starts the approval clock.**

**Save moves the plan into Negotiating.** The first time negotiated prices are saved on a Draft/Planned plan, the campaign status becomes **Negotiating** — the plan now carries prices that differ from the system-calculated baseline and is formally "in negotiation". A counterparty's save during its counter turn does **not** change the campaign status — the campaign stays **Reviewing** and the counter is carried in that owner's per-owner state (**Countered**, §5.3a). Every saved batch is captured in the History drawer (§4) at the moment of save.

**Unsubmitted-changes indicator.** If prices were saved but the plan not yet submitted, the **View Detail page** shows a message that price changes are awaiting submission. This message appears **only on View Detail — never on the Media Plan page** (the media plan is a client-facing document; workflow nags don't belong there).

---

## 4. The History drawer

Beside the Summary button sits a **History** button (exists in production; to be built in this repl). It opens a side drawer with the complete negotiation record.

### 4.1 Structure

- A list of **all billboards (inventory line items)** on the plan.
- Under each, every change ever made: who (user + company), when, what — old value → new value, expressed with the standard Difference sign convention where a price moved.
- Changes covered: Proposed Price (inventory and schedule level), Difference applications (including bulk), bonuses, SOV changes, impression edits.

### 4.2 Custom fees are part of History — do not miss this

The History drawer also records the full life of every **custom fee**: created (name, type, value, placement), edited (old → new for any field, including the include-in-plan toggle and per-viewer visibility), and deleted. Fee history entries respect the same visibility rules as the fees themselves — a viewer who cannot see a fee cannot see its history entries either.

### 4.3 History vs Initial Price

Because Initial Price is immutable (§2.4), the table's Difference always shows *total* movement from the system-calculated baseline. **Round-over-round movement lives here**: the History drawer is where a user reads "I offered 4,000, they countered 4,500, I re-proposed 4,200." History is append-only and never editable.

---

## 5. Submit Plan and the campaign approval process

### 5.1 Submit Plan — the single trigger

A plan sits in **Draft/Planned** while the creator builds it, and moves to **Negotiating** the first time negotiated prices are saved (§3.4). Pressing **Submit Plan** on the campaign view-detail page is the only action that starts approval — there is no separate "request approval" entry point. On submit:

- Status transitions **Draft/Planned/Negotiating → Reviewing**.
- Price Management **locks for the submitter** — every row read-only until the campaign either completes approval or comes back on a counter turn. While in Reviewing, the plan accepts **no changes from anyone whose turn it is not**; a Reviewing plan is a locked offer on the table.
- The proposed prices become **visible to counterparties** for the first time.
- The two approval tiers (§5.2) are created and Tier 1 enters In Progress.

The button renders only for the campaign creator and only while status is Draft, Planned or Negotiating; everyone else sees the read-only state. If prices were saved in the Summary but the plan not yet submitted, view-detail shows the "unsubmitted price changes" message (§3.4) until this button is pressed.

### 5.2 The two approval tiers

| Tier | Actor | Action surface | Pass condition |
|---|---|---|---|
| 1 — Internal company approval | Anyone in the **creator's company** holding the `canApproveCampaigns` permission (Admin Console — permission, not role, decides who approves; the submitter may self-approve if they hold it) | Approve / Reject | A single approve from any permission-holder |
| 2 — Media owner approval | Each media owner with inventory in the plan, **independently** | Approval drawer: **Approve** or **Counter Offer** per owner | Every owner has approved the latest prices for their own inventory — or has been **auto-approved after 72 hours** of no action (see below) |

**Tier-1 Reject** returns the plan to **Negotiating** and **unlocks Price Management for the creator** — the locked rows open again exactly as before submission, the creator adjusts and re-submits. Nothing has been exposed to media owners yet, so a Tier-1 reject is a purely internal round-trip.

**Tier-2 auto-approval (72-hour rule).** If a media owner takes no action — no Approve, no Counter — for **72 hours** after the plan reaches its queue, that owner's slice is **automatically approved** at the offered prices. A reminder notification goes to the owner before the deadline (e.g. at 48h), and the auto-approval is recorded in the History drawer and audit trail explicitly marked as **automatic**, never as a user action. The 72-hour clock resets whenever a new price reaches that owner (re-submit after a counter).

This is why "whoever in my team has approval permission will approve" — submission and approval are separated by **permission, not role**, identically for media-owner, agency, partner and internal companies.

Tier-2 details that matter to Price Management:

- The approval drawer lists **one row per media owner**. Tier 2 shows **Partial** until every owner has responded.
- A **Counter Offer** does not reject the campaign — the campaign **stays Reviewing**; the counter is expressed as that owner's per-owner state (**Countered**) with a turn marker, and only that owner's rows unlock in the pricing table (§5.3). Other owners' approvals stand.
- Swapping a declining owner's inventory out (Modify Plan) removes their row from Tier 2 entirely; a replacement owner's row is added as Pending without re-asking anyone who already approved.
- **MW-internal approval** — once granted, the campaign goes **live directly**; no further round.
- **Single-party campaigns** — a media owner planning on its own inventory submits and self-approves.

### 5.3 The turn-based counter loop

1. **Edit** — the plan owner edits freely while Draft or Planned (the first Summary save moves the plan to Negotiating, §3.4); from Negotiating onward only the party holding the turn edits its own inventory rows. Every other row is read-only.
2. **Submit Plan** — locks the submitter, exposes proposed prices, starts approval.
3. **Counterparty decision** — Approve or Counter Offer, per owner, from the approval drawer. No reject-with-reason handshake.
4. **Counter Offer** — the campaign stays **Reviewing**; the countering owner's per-owner state becomes **Countered** with a **turn marker**, and only that party's rows unlock. Their re-submit flips the turn and re-enters approval. (**Negotiating** is reserved for states where the *creator* can edit: pre-submit, and after a Tier-1 reject.)
5. **Per-owner approval reset** — a re-submit resets approvals **only for parties whose rows changed**. Untouched owners keep their approval.

Every proposed price written during a counter turn lands in the History drawer (§4) with the countering user + company as the author; Initial Price never moves (§2.4).

**Canonical two-party walkthrough (agency ↔ media owner).** This is the reference sequence every surface must support:

1. An **agency user creates a plan** and opens Price Management. They update Proposed Prices, open the **Summary**, and click **Save** → status becomes **Negotiating**; History captures the change batch.
2. The agency clicks **Submit Plan** → status becomes **Reviewing**; the plan is **locked** — it accepts no further changes while the offer is on the table.
3. The **media owner logs in** and sees the proposed prices. On the **Plans page**, the plan carries an **external-plan indication** (it was created by another company — see §5.5), and because it is in Reviewing awaiting this owner's response, it also surfaces on the owner's **Plan Approval page**.
4. The media owner **counters**: updates Proposed Price on its own rows (to reprice many inventories at once, select all and **bulk Apply Difference**, §2.3), opens Summary, and clicks **Save** → the campaign **stays Reviewing**; the owner's per-owner state becomes **Countered**. Because the media owner is **not the plan's owner**, it sees the **media cost only** and **cannot add custom fees** (§8).
5. The media owner clicks **Submit** → the media owner's pricing is locked and the **agency** sees the counter offer; the campaign remains **Reviewing** throughout.
6. The agency either counters again (repeat from step 1's edit) or **approves the campaign**. On agency approval, the plan appears again in the media owner's **Plan Approval** page; when the media owner also approves, pricing is **locked on both sides** and the plan is **Approved**.
7. The media owner proceeds to the **Execution Plan** if its company has Influence access; otherwise it executes offline (§6.4).

### 5.3a Multiple media owners on one plan

Approval and negotiation are **sliced per owner**; the campaign status is an **aggregate** of the per-owner states:

- Each media owner sees, prices and locks **only its own rows**. One owner countering never blocks another owner from approving, and an owner who has not yet opened the plan simply stays **Pending**.
- Per-owner states are **Pending → Countered / Approved** (an owner that ignores the plan for 72 hours moves to Approved automatically, §5.2), shown one row per owner in the approval drawer (§5.2) and on the Plan Approval page.
- **Campaign status rule:** from Submit onward the campaign stays **Reviewing** for the entire negotiation — counters do not flip it back — and becomes **Approved** only when *every* owner has approved the latest prices for its own inventory. **Negotiating** is reserved for creator-editable states: before the first Submit, and after a Tier-1 reject. So "one countered, one hasn't looked, one approved" renders as **Reviewing**, with the per-owner breakdown carrying the detail.
- **Drawer vs Plan Approval page.** The approval drawer is the **summarised** view: rollup counts per state — e.g. *Reviewing (2/2)*, then *Approved (1/2) · Reviewing (1/2)*, or *Countered (1/2) · Approved (1/2)* — alongside the plan details. The **Plan Approval page** is the **granular** surface: named per-owner rows, individual states, and the actions each party takes. Both read the same per-owner breakdown.
- A creator's **re-submit resets only the owners whose rows changed** (§5.3 rule 5); untouched owners keep their approval and are not re-asked.
- The status chip on the Plans page carries the partial-progress readout (e.g. "Reviewing — 2/3 owners approved") so no party has to open the plan to understand where it stands.
- **A media owner sees only its own state.** The rollup counts, other owners' names and other owners' states are creator-side only (§8). To owner A the drawer and Plan Approval page show A's slice alone — *Pending your review / Countered — awaiting response / Approved* — never "(1/2)" or any hint that owner B exists.

### 5.3b Worked example — agency plan across two media owners

The reference use case for the whole multi-owner flow, with the exact UI behavior at each step. Cast: **Priya**, a planner at **MediaHub Agency** (plan creator); **Owner A** (ClearVision Outdoor) and **Owner B** (CineMax Cinemas), each supplying part of the inventory.

1. **Priya builds the plan** across Owner A's billboards and Owner B's cinema screens, opens **Price Management**, adjusts Proposed Prices, opens the **Summary** and clicks **Save**.
   *UI:* status chip flips to **Negotiating**; the Save toast confirms the batch and — because Priya is the creator and the plan is in a submit-eligible state — carries a **Submit plan** shortcut with the same confirmation step as the plan-detail button. History logs the batch under Priya · MediaHub Agency.
2. **Priya submits** (from the toast or the plan-detail button) and confirms.
   *UI:* status chip flips to **Reviewing**; all pricing rows lock. If Tier-1 (internal) approval applies, the plan first sits with MediaHub colleagues holding the approval permission — a **Tier-1 Reject** here returns the plan to **Negotiating** and re-unlocks pricing for Priya, and neither owner ever sees it. On Tier-1 pass (or when no Tier-1 is configured), both owners' slices enter **Pending** and each owner's 72-hour clock starts.
   *Approval drawer (Priya):* **Reviewing (2/2)**. *Plans page:* "Reviewing — 0/2 owners approved".
3. **Owner A logs in.**
   *UI:* on Owner A's Plans page the row carries the **External plan** badge ("created by MediaHub Agency"), and the plan appears in Owner A's **Plan Approval** inbox. Owner A sees **only its own slice**: its billboard rows, media cost only (no agency fees), and its own state — *Pending your review*. Nothing anywhere hints that Owner B exists.
4. **Owner A approves** from the approval drawer.
   *UI (Owner A):* its state becomes **Approved**; its rows stay locked. *UI (Priya):* drawer now reads **Approved (1/2) · Reviewing (1/2)**; Plans-page chip reads "Reviewing — 1/2 owners approved". Campaign status is still **Reviewing**.
5. **Owner B counters instead**: it unlocks its own cinema rows, revises Proposed Prices (bulk Apply Difference if many rows), clicks **Save Changes** in the Summary drawer, then clicks **Submit Counter Offer** in the approval drawer. These are two distinct actions: Save only persists the prices (and logs them in History) — nothing is sent until Submit Counter Offer is clicked.
   *UI (Owner B):* on Save the campaign **stays Reviewing** — no status flip; Owner B's per-owner state becomes **Countered** with the turn marker on its side, then its rows re-lock on Submit Counter Offer. *UI (Priya):* drawer reads **Approved (1/2) · Countered (1/2)**; the countered rows show Owner B's provenance label in amber; Owner A's approval is untouched.
6. **Priya responds to the counter.** She re-opens Price Management — only Owner B's rows are editable (the turn is hers for that slice) — adjusts, clicks **Save Changes**, and then re-submits via **Submit plan** (plan-detail button or the Save-toast shortcut) — again, Save alone sends nothing.
   *UI:* the re-submit resets **only Owner B's** approval (rows changed) and restarts Owner B's 72-hour clock; Owner A is not re-asked. Drawer returns to **Approved (1/2) · Reviewing (1/2)**.
7. **Owner B approves** the revised prices — or does nothing: at **48 hours** it gets a reminder, and at **72 hours** its slice is **auto-approved** at the offered prices, logged in History as *automatic*, never as a user action.
   *UI:* the moment the last outstanding slice is approved (manually or automatically), the campaign flips to **Approved**; pricing locks on all sides; the drawer reads **Approved (2/2)** and each owner still sees only its own *Approved* state.
8. **Execution.** Priya proceeds to the Execution Plan; each owner executes its own slice (in-platform with Influence access, otherwise offline, §6.4).

The invariants this example demonstrates: the campaign never leaves **Reviewing** between Submit and final approval; **Negotiating** appears only where the creator can edit (step 1, or after a Tier-1 reject in step 2); owners are isolated from each other throughout; and re-submits reset only the slices that changed.

### 5.4 Re-approval on a live plan

Approved/Live campaigns render Price Management read-only until the planner explicitly clicks **"Edit pricing — re-open approval"**. From that moment:

- Only the **affected owners'** approvals reset — the creator's company is not asked to re-confirm what it already confirmed.
- Any sent proposal auto-versions (previous version demoted to "superseded", share-link token rotated).
- Statement Builder drops the campaign from the current draft until re-approval completes.
- The audit trail records the planner as the owner of the re-approval cycle.

### 5.5 Provenance labels

Each price cell carries a source label ("JD · MediaHub Agency") — amber when the proposer's company differs from the viewer's, muted grey when same-company. An inventory-level **Approved** badge above a schedule group means every schedule of that inventory is approved through campaign approval.

**External-plan indication on the Plans page.** When a plan involving the viewer's company was **created by a different company** (an agency plan on a media owner's inventory, seen from the media-owner tenant), the Plans page row carries an explicit **External plan** badge with a tooltip naming the creating company — the media owner must be able to tell at a glance that this plan "came from outside". In addition, any external plan sitting in **Reviewing** and awaiting this company's response appears on the company's **Plan Approval page**, which is the primary work queue for counterparty decisions (Approve / Counter Offer). The Plans-page badge is the passive signal; Plan Approval is the actionable inbox — both exist.

### 5.6 Viewer-relative status — what each party sees while approvals are partial

Because media owners approve **independently** (§5.2) and are **isolated from each other** (§5.3), a single campaign-level status cannot be shown to everyone without creating a perceived bug. The canonical case:

> A plan has three media owners. Owner A has approved, Owner B is still reviewing, Owner C is negotiating a counter. The campaign as a whole is still **Reviewing** — it stays Reviewing until *every* owner's slice is approved. Owner A, who cannot see the other owners, filters their Plans page by "Approved" and the plan does not appear, because the list was filtering on the campaign-level status. To Owner A this looks like data loss: "I approved this plan, where is it?"

The resolution is a single rule applied consistently across every surface:

**Status shown = the campaign-level status when the viewer is the creator's company; otherwise the viewer's own approval-slice status.**

- **Creator's company (buyer view):** sees the true aggregate — Reviewing until all owners have responded, with the per-owner breakdown in the approval drawer (Approved 1/3 · Reviewing 1/3 · Countered 1/3).
- **Each media owner:** sees only its own slice state — **Pending review**, **Countered** (turn marker on whichever side holds the turn), or **Approved**. Owner A in the example sees the plan as *Approved*; that is the truth of their world.
- This rule governs **all** of: the status column on the Plans page, the status **filter** (Owner A filtering "Approved" must find this plan), the plan-detail header badge, dashboard counts, and any export or API response. It is enforced **server-side** — deriving it only in the UI lets exports and direct API reads disagree with the screen.

**What the approved owner must *not* see.** Any label that discloses the aggregate — "Approved, awaiting other parties", progress fractions, or a campaign-level Reviewing badge alongside their own Approved state — leaks the existence and state of other media owners and violates the isolation rule. After approving, an owner sees plain **Approved**. If the gap between "I approved" and "nothing is live yet" needs explaining, the neutral phrasing **"Approved — pending buyer confirmation"** is acceptable; it attributes the wait to the buyer, never to unseen third parties. The owner learns of the aggregate outcome only when the campaign reaches a state they legitimately share — **Approved (all parties) / Live** — at which point all viewers converge on the same status.

**Why the plan stays listed on the Plans page at all.** Restricting cross-company plans to the Plan Approval page alone would sidestep the filter mismatch, but owners would lose the ability to find their approved and live external plans outside the inbox. The division of labour stands as defined in §5.5: **Plan Approval is the actionable inbox** (items awaiting this company's response), the **Plans page is the browsable record** of every plan involving the company — with viewer-relative status making its filters truthful for every persona.

---

## 6. Execution Plan and what happens after approval

### 6.1 Approval unblocks execution

The **Execution Plan** option stays disabled until campaign approval completes. Approval also flips reservations from Reserved to Booked, enables creative assignment, and makes the campaign statement-eligible — but from Price Management's point of view the key fact is: **the agreed prices are now frozen into the approved record**, and any later change goes through §5.4.

### 6.2 Who fires the handoff — automatic vs deliberate

- **Agency / advertiser / internal-led plans:** the execution handoff fires **automatically** the moment Tier 2 closes — one line item per media owner pushed to the destination system (Influence for digital lines, OMS for classic; both for mixed plans). The Campaign Detail page shows a handoff banner enumerating each line.
- **Media-owner-led plans:** approval does **not** auto-fire. The plan waits for the media owner to open the **Execution Plan workbench**, review and fine-tune the line items, and push deliberately. The seller is also the operator — they set purchase types, floor rates and dayparts before anything reaches Influence or OMS.

### 6.3 The Execution Plan workbench (media-owner surface)

Reached from the **Execution Plan / Execution Status** button on campaign detail. Access is decided server-side:

| Caller | Gets |
|---|---|
| Media owner (role or company businessType) | Full read/write workbench |
| Everyone else with campaign access (agency, advertiser, internal) | Read-only **Execution Status** — no edit, reset or push affordances; write endpoints reject with 403 |

The engine generates line items grouped by destination: classic → OMS (purchase type `order`); digital → Influence (`direct`, or a recommended programmatic type — PMP / Preferred Deal / Guaranteed — by booking pressure). The media owner can change purchase types (within the legal set per inventory class), edit floor rates, move inventories between compatible lines, and re-author the 24×7 daypart schedule. Guardrails: ±15% drift-vs-baseline warnings (advisory) and operating-window clamping. **Push** validates the whole plan first, hands lines off per-line (partial failures retry individually), **takes the campaign live** (status → Active from any pre-live state), and **locks** the plan — after a successful push the workbench is read-only for everyone and retries push only the frozen snapshot.

### 6.4 No Influence access — manual execution

If the media owner's company has **no Influence access**, the Execution Plan option remains unavailable, and the media owner sees an explicit message: *"Your company doesn't have access to the Execution Plan (Influence). Execute this campaign offline in your own systems and keep the advertiser updated with delivery reports."* The campaign is executed **manually in the owner's own system** and simply **sits at Approved** in Planner — there is no "executed externally" state or marker. Planner remains the system of record for the agreed prices, and statements build from the approved prices regardless of how playout happened.

---

## 7. Comments and company-level tagging

- Comments exist on the plan (view-detail surface), visible by default only to the **creating company's users**.
- **Tags are companies, not users.** The plan creator (agency / partner / internal) tags a media-owner **company** on a comment. Every user of that tagged company can then see **that particular comment only** — not the surrounding thread, not other comments.
- Untagged comments are invisible to counterparties, always.
- Example: an agency plan has 5 media owners. The planner writes one comment per owner, tagging each owner's company on their own comment. Each owner's users see exactly one comment; the agency's users see all five.

---

## 8. Roles and visibility

| Party | Can edit prices | Sees comments | Sees custom fees |
|---|---|---|---|
| Creator company (agency / partner / internal / media owner planning own inventory) | Own turn, own rows | All comments | All fees, full detail |
| Counterparty media owner | Only its own rows, only during its counter turn | Only comments where its **company** is tagged | Only fees the creator **included in the media plan** — and then only if per-fee visibility (§3.2) names the fee to them |

**Counterparty media owners cannot add custom fees.** On a plan created by another company, the media owner works with the **media cost only** in Price Management — creator fees surface to it only on the client-facing media plan document, and only when included there and named to it per per-fee visibility (§3.2) — and it can do exactly one thing: update Proposed Prices on its own rows (individually, or in bulk via select-all + Apply Difference, §2.3). The fee section of the Summary drawer is hidden for them, and the **view-detail page shows an explicit message**: *"You can't add custom fees on this plan because your company is not the plan owner. You can only update proposed prices for your inventory."* Fees belong exclusively to the plan-owning company's cost cascade.
| Advertiser role | Never — read-only on all pricing fields, even on own campaigns | Per tagging rule | Per visibility rule; billable included fees are absorbed into headline media cost in advertiser view |

**What blocks a price change entirely:**

- Campaign in **Rejected** or **Completed** — pricing closed, cannot reopen.
- Another party's rows off-turn.
- Advertiser role.
- Approved/Live campaigns are read-only until the planner explicitly clicks **"Edit pricing — re-open approval"**, which resets approvals only for the affected owners and versions any sent proposal (see PRD §8.5 ripple table for the full downstream impact on Proposals and Statements).

---

## 9. Resolved edge cases (developer FAQ)

| # | Question | Resolution |
|---|---|---|
| 1 | Does Initial Price reset after a counter/re-submit? | **No. Initial is immutable** — always the original system-calculated price. Only Proposed moves; the History drawer records every step (§2.4, §4). |
| 2 | Schedules moved in opposite directions — what does the inventory row's Difference show? | **"Mixed"**, not a blended % (§2.3). Accordion shows per-schedule values. |
| 3 | Difference on a bonus line (Initial = 0)? | **Dash (—)** — undefined, never 0% or ∞ (§2.3). |
| 4 | Inventory-level edit flattens hand-tuned schedule prices — warn? | **Warn at Save**, listing affected inventories; user confirms or goes back (§2.5). |
| 5 | Why equal split across schedules, not weighted? | User priced at inventory level; the system cannot know the intended distribution. Equal shares with an acknowledgement message (§2.5). |
| 6 | User saved but never submitted — where do we nudge? | **View Detail page only.** Never on the Media Plan page (§3.4). |
| 7 | Does the approval-acknowledgement checkbox persist? | **No — resets on every drawer open** (§3.3). |
| 8 | How is a manually-executed campaign (no Influence) marked? | It isn't — it **sits at Approved** (§6). |
| 9 | Tagging on comments — user or company? Whole thread? | **Company-level tag**; reveals **only that comment** (§7). |
| 10 | On-modify column state — per session or per campaign? | **Persisted per campaign** — all parties see the same extended columns (§2.1). |

---

## 10. Cross-surface reconciliation contract

The Summary drawer's Total Cost is the canonical number. The media plan deck's Cost Breakdown slide, the Costing analytics tab, the Plan Snapshot "total investment", the Inventory Mix Total Cost column, and every export (PPTX / PDF / Excel) must derive from the same cascade and reconcile to the same Total Cost for every viewer. Per-viewer fee visibility may change *naming*, never totals. Any change to the cascade must be applied to all surfaces in lockstep.
