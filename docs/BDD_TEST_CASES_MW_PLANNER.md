# MW Planner — BDD Test Cases

This document captures behaviour-driven test scenarios derived from `docs/PRD_MW_PLANNER.md`. Scenarios are written in Gherkin (`Given / When / Then`) and grouped by PRD section so a tester can trace every requirement to coverage. Each `Feature` block opens with the PRD section it covers; tags identify the role and module under test.

Conventions:
- **Roles**: `@internal`, `@agency`, `@media_owner`, `@advertiser`, `@reseller`.
- **Severity tags**: `@critical` (blocks release), `@high`, `@medium`, `@low`.
- **Cross-feature tags**: `@cross_feature` when the scenario verifies a ripple across modules.
- All currency values use the active tenant's currency code (USD, MYR, SGD, AED, …) — never a `$` symbol.

---

## 1. Authentication & Multi-tenant Switching (PRD §1)

### Feature: Sign-in and idle session timeout
```gherkin
Scenario: Successful sign-in lands on the dashboard @critical
  Given a registered user "jane@mediahub.com" with password "secret"
  When she submits valid credentials on the sign-in screen
  Then she lands on the dashboard
  And the header shows her primary tenant as the active company

Scenario: Idle warning fires after 30 minutes @high
  Given Jane is signed in and the page is idle
  When 30 minutes pass without keyboard, mouse or network activity
  Then an idle-warning dialog appears with a 60-second countdown

Scenario: Auto sign-out after grace period @high
  Given the idle-warning dialog is on screen with the 60-second countdown running
  When Jane does not interact for the full 60 seconds
  Then she is signed out and returned to the sign-in screen
  And any in-flight draft is preserved server-side

Scenario: Interacting during grace period cancels sign-out @medium
  Given the idle-warning dialog is on screen
  When Jane clicks "Stay signed in" before the countdown finishes
  Then the dialog dismisses and the idle timer resets to 30 minutes
```

### Feature: Multi-tenant switching
```gherkin
Scenario: User mapped to multiple companies sees the company switcher @critical
  Given Jane is mapped to "MediaHub Agency" and "MediaHub Network"
  When she opens the header company dropdown
  Then both companies appear in the list
  And the active company is marked

Scenario: Switching tenants reloads every tenant-scoped surface @critical @cross_feature
  Given Jane is on the Campaigns list under "MediaHub Agency"
  When she selects "MediaHub Network" from the company dropdown
  Then the campaigns list, dashboard widgets, reservation queue, approval inbox, and tags reload under the new tenant
  And no full page refresh is performed

Scenario: Draft campaign survives a tenant switch @high
  Given Jane has an unsaved draft on Step 3 of the wizard under "MediaHub Agency"
  When she switches to "MediaHub Network" and back to "MediaHub Agency"
  Then the draft is restored on Step 3 with all previously entered values

Scenario: Tenant switch in another tab does NOT retro-grant rights @critical @security
  Given Jane has tab A open under "MediaHub Agency" (role: Agency)
  And Jane has tab B open under "MediaHub Network" (role: Media Owner)
  When she attempts an Agency-only action from tab A
  Then the server authorises against the active tenant on the request, not the session token
  And the action succeeds under Agency rights — regardless of what tab B did
```

### Feature: Role × capability matrix (per PRD §1 table)
```gherkin
Scenario Outline: Role gates capability correctly @critical
  Given a user signed in with role <role>
  When they attempt to <capability>
  Then the action is <expected>

  Examples:
    | role          | capability                                   | expected   |
    | Advertiser    | negotiate a price                            | denied     |
    | Advertiser    | view a price                                 | allowed    |
    | Advertiser    | approve at any stage                         | denied     |
    | Media Owner   | approve plans for inventory they do not own  | denied     |
    | Media Owner   | approve plans for their own inventory        | allowed    |
    | Internal      | create a campaign for any tenant             | allowed    |
    | Agency        | build a statement                            | allowed    |
    | Reseller      | approve at Agency Acceptance                 | allowed    |
```

---

## 2. Dashboard (PRD §2)

### Feature: Role-specific widget set
```gherkin
Scenario Outline: Dashboard renders the widgets for the active company's business type @high
  Given the active company has business type <type>
  When the user opens the dashboard
  Then they see exactly the widgets <widgets>

  Examples:
    | type          | widgets                                                                                                        |
    | media_owner   | Campaign Overview by Status, Sales Performance Summary, Creative Status Tracker, Inventory Utilisation, Pending Hold Requests |
    | media_agency  | Campaign Overview, Budget Tracker, Creative Status, Recent Activity, Expiring Holds                            |
    | advertiser    | Campaign Overview, Spend Tracker, Creative Status                                                              |
    | internal      | Campaign Overview by Status, Sales Performance Summary, Creative Status Tracker, Inventory Utilisation, Pending Hold Requests |
```

### Feature: Widget tenant-scoping and date filtering
```gherkin
Scenario: Widgets refresh on tenant switch @critical
  Given the dashboard is showing data for "MediaHub Agency"
  When the user switches active tenant to "MediaHub Network"
  Then every widget re-fetches with the new tenantCompanyId
  And no stale numbers from the previous tenant remain on screen

Scenario: Widgets refresh on date-range change @high
  Given the user is on the dashboard
  When they pick a different date-range chip
  Then every tenant-scoped widget re-queries with the new date params via URLSearchParams

Scenario: Tenant indicator badge always shows the active company @medium
  Given the user has switched to "MediaHub Network"
  Then the header tenant badge reads "MediaHub Network"
```

### Feature: Inventory Utilisation widget toggle
```gherkin
Scenario: Default by-type view @high
  Given the dashboard is loaded for a media owner
  When the user views the Inventory Utilisation widget
  Then it defaults to "By Type" with Classic, Digital Screen, Transit, Retail, Network, Radio, Experiential, Cinema buckets

Scenario: Toggle to by-format view @medium
  Given the Inventory Utilisation widget is showing By Type
  When the user toggles to "By Format"
  Then the widget renders the format breakdown
```

### Feature: Click-through hand-offs
```gherkin
Scenario: Clicking Pending Hold Requests deep-links to filtered Reservations @medium
  Given the dashboard tile shows N pending holds
  When the media-owner clicks the tile
  Then they land on the Reservations queue with the Pending filter pre-applied

Scenario: Clicking Creative Status deep-links to Creatives library with "missing assignment" filter @medium
  Given the dashboard tile shows N missing creatives
  When the agency planner clicks the tile
  Then they land on the Creatives library with the "missing assignment" filter pre-applied
```

### Feature: Currency display
```gherkin
Scenario: Every monetary figure uses the tenant's currency code, not "$" @critical
  Given the active tenant's currency is MYR
  When any dashboard widget renders a monetary value
  Then the value reads e.g. "MYR 7,719" — never "$7,719"
```

---

## 3. Campaign Status Lifecycle (PRD §3)

### Feature: Status determines what is editable
```gherkin
Scenario Outline: Status-controlled edit gates @critical
  Given a campaign is in status <status>
  When a user attempts to <action>
  Then the action is <result>

  Examples:
    | status     | action                          | result   |
    | Draft      | edit any field                  | allowed  |
    | Draft      | request a hold                  | blocked  |
    | Planned    | edit budget/dates/targeting     | blocked  |
    | Planned    | withdraw back to Draft          | allowed  |
    | Reviewing  | swap declined inventory         | allowed  |
    | Reviewing  | directly edit budget            | blocked  |
    | Approved   | edit any field via standard UI  | blocked  |
    | Approved   | same-spec creative swap         | allowed  |
    | Active     | pause                           | allowed  |
    | Active     | change inventory list           | blocked  |
    | Paused     | edit creative                   | allowed  |
    | Completed  | write actions                   | blocked  |
    | Rejected   | duplicate as fresh draft        | allowed  |
```

### Feature: Status transitions and ripples
```gherkin
Scenario: Draft → Planned triggers approval, reservations and negotiation in one click @critical @cross_feature
  Given Jane is on Step 5 of the wizard with a complete plan
  When she clicks Submit
  Then the campaign status becomes Planned
  And an approval workflow with three stages is created
  And one reservation Hold Request is written per line item
  And one negotiation thread is opened per line item at rate-card price
  And Jane sees a single confirmation toast

Scenario: Reviewing → Approved flips reservations and unblocks creative @critical @cross_feature
  Given a campaign in Reviewing has every approval stage passed
  When the final stage is approved
  Then the campaign status becomes Approved
  And every reservation flips from Reserved to Booked
  And creative assignment becomes possible for the line items
  And the campaign appears in the next Statement Builder draft

Scenario: Approved → Reviewing re-approval is scoped to affected rows only @critical
  Given an Approved campaign with media owners A, B and C — all approved at Stage 3
  When the planner changes the price on a line item belonging to owner B
  Then the campaign reverts to Reviewing
  But Stage 1 (Agency Acceptance) stays Approved
  And Stage 2 (Internal) stays Approved
  And Stage 3 row for A stays Approved
  And Stage 3 row for B resets to Pending
  And Stage 3 row for C stays Approved

Scenario: Approved → Active happens automatically on start date @high
  Given an Approved campaign with start date = today
  When the scheduler ticks past 00:00 on the start date
  Then the campaign becomes Active without manual action

Scenario: Active → Completed happens automatically on end date @high
  Given an Active campaign with end date = yesterday
  When the scheduler runs
  Then the campaign becomes Completed and a final delivery snapshot is taken
```

---

## 4. New Campaign Wizard (PRD §4)

### Feature: Identical wizard for every role
```gherkin
Scenario Outline: Wizard shape is the same for all roles @high
  Given a user with role <role> starts a new campaign
  Then they see the same 5 steps, in the same order, with the same field set

  Examples:
    | role        |
    | Internal    |
    | Agency      |
    | Media Owner |
```

### Feature: Continuous draft autosave
```gherkin
Scenario: Browser autosave on every keystroke @medium
  Given the wizard is open with unsaved input
  When the user types into a text field
  Then localStorage is updated within one second

Scenario: Server autosave every 30 seconds @medium
  Given the wizard has been open for 30 seconds with changes
  Then the platform PUTs the draft to the server
  And a closed-tab recovery returns the most recent server draft
```

### Section 4.1 — Step 1 Campaign Details

```gherkin
Scenario: Campaign Name auto-generates a default @low
  When the user opens Step 1
  Then the Campaign Name field is pre-filled with "Campaign <MMM dd yy> — NNN"

Scenario: External ID field is available to all roles @medium
  Given a user with role <role: Internal | Agency | Media Owner | Advertiser>
  When they view Step 1
  Then the optional "External ID" text field is visible below Campaign Name

Scenario: Media Channels cannot be empty @critical
  Given the user has removed every media channel
  When they click Next
  Then a validation error is shown and Next stays disabled

Scenario: Media Channels default to Digital OOH @medium
  When a fresh Step 1 loads
  Then Digital OOH is pre-selected

Scenario: Quick-pick chips set Plan Dates @medium
  When the user clicks the "30 days" chip
  Then Plan Dates fills with start = today and end = today + 30 days
  And the duration badge reads "31 days"

Scenario: Client Type = Agency reveals Agency picker and DSP/Seat @high
  Given Client Type is set to "Direct"
  When the user changes Client Type to "Agency"
  Then the Agency searchable select appears side-by-side
  And once an agency is chosen the DSP / Seat select appears

Scenario: Quick Agency creation is available for internal users @high
  Given an Internal user opens the Agency picker
  When they click "Create new agency"
  Then a minimal agency record can be created inline without leaving the wizard
```

### Section 4.2 — Step 2 Budget & Location

```gherkin
Scenario: Budget must be > 0 and duration in 1–365 days @critical
  Given Step 2 with budget = 0
  When the user clicks Next
  Then the Next button stays disabled and an error appears

Scenario: Currency defaults to tenant currency, overridable @medium
  Given the active tenant currency is MYR
  When Step 2 loads
  Then the currency selector reads MYR
  And the user can override per campaign

Scenario: Channel Budget Allocation card hidden for single channel @high
  Given exactly one media channel is selected in Step 1
  When Step 2 loads
  Then the Channel Budget Allocation card is hidden
  And the single channel implicitly receives 100% of the budget

Scenario: Channel Budget Allocation auto-distributes by inventory-count weights @critical
  Given Step 1 has Digital OOH and Cinema selected
  And the total budget is 10000
  When Step 2 loads
  Then Digital OOH gets 88.9% (8889) and Cinema gets 11.1% (1111)
  And inputs are read-only

Scenario: Manual override unlocks the percentage inputs @high
  Given the auto-distribution is shown
  When the user clicks "I want to adjust the distribution manually"
  Then the percentage inputs become editable

Scenario: Distribute evenly and Reset buttons @medium
  Given two or more channels are selected
  When the user clicks "Distribute evenly"
  Then each channel receives 100/N %
  When the user clicks "Reset"
  Then the inventory-count proportional defaults are restored

Scenario: Soft warning when percentages do not sum to 100 @medium
  Given the user has edited percentages so they sum to 95
  Then an amber warning appears below the card
  But the Next button is NOT blocked
```

### Section 4.3 — Step 3 Targeting

```gherkin
Scenario: Sub-tab visibility follows Step 1 channels @high
  Given Step 1 has Cinema selected and Retail not selected
  When Step 3 loads
  Then the Cinema sub-tab is visible
  And the Retail sub-tab is hidden

Scenario: Country filter respected in Cinema operators @high
  Given Step 2 has India and Malaysia selected
  When the user opens Cinema → Operators
  Then PVR INOX, Cinépolis India, GSC and TGV appear
  And Shaw, Golden Village (Singapore) do NOT appear

Scenario: Inventory Type tab — all buckets selected by default @high
  Given Digital OOH is selected in Step 1
  When the user opens Inventory Type
  Then each of Airport, Bus, Commercial Fleet, Digital Screens, Rail & Metro, Taxi & Rideshare, Transit Stations is selected
  And the card checkbox is ticked

Scenario: Exclude a bucket via the checkbox @high
  When the user unticks the "Bus" card checkbox
  Then the card mutes to grey with a "Not included" label
  And `inventoryTypes.deselectedTypes` contains "digital:bus"

Scenario: Edit-formats drawer is pre-filled with every format ticked @medium
  When the user clicks "Click to edit" on the Airport card
  Then a side drawer opens with every Airport format checked

Scenario: Clearing every format in the drawer auto-deselects the type @high
  Given the drawer is open with all formats ticked
  When the user unticks every format
  Then the parent type card auto-deselects on drawer save

Scenario: Retail format selection is shared between Inventory Type drawer and Retail tab @medium
  Given the user selects "Mall Digital Screen" inside the Inventory Type drawer for Retail
  When they navigate to the Retail tab
  Then "Mall Digital Screen" is selected there as well

Scenario: Step 3 has its own Back / Next / Cancel, hiding the wizard footer @medium
  When the user is on Step 3
  Then the outer wizard footer is hidden
  And the Targeting component renders its own Back to Step 2, Next and Cancel
```

### Section 4.4 — Step 4 Inventories (current redesign)

```gherkin
Scenario: Auto-plan banner appears on first entry @critical
  Given the user reaches Step 4 for the first time
  Then a request to the Recommendation Engine fires immediately
  And a run ID is recorded against the campaign
  And the auto-plan banner shows the number of picked inventories and per-channel budget distribution
  And the banner exposes "Manual Edit" and "View Inventories" CTAs

Scenario: Plan Summary card renders six stats @high
  Given a plan has been auto-generated with N inventories
  When the user views the Plan Summary card
  Then the metric strip shows Inventories, Impressions, Reach, Frequency, Avg CPM, Spend
  And the Spend tile shows a budget-progress bar that turns red when over budget

Scenario: Plan Summary grouped breakdown re-pivots via dropdown @high
  Given the Plan Summary card is rendered
  When the user selects "City" from the Group-by dropdown
  Then the breakdown rows regroup by city, each showing count, impressions, reach, cost
  And the same applies for Media Owner, Type, Venue Type, All Inventories

Scenario: Plan Map is always visible once a plan exists @critical
  Given the plan has ≥ 1 selected inventory
  Then a Mapbox card is rendered with channel-coloured markers
  And an "Audience heatmap" checkbox is offered
  And the legend lists only the channels actually present in the plan

Scenario: Audience heatmap toggle overlays a density layer @medium
  When the user ticks "Audience heatmap"
  Then an impression-weighted heatmap renders on top of the markers

Scenario: View Inventories opens a read-only side panel and lazy-fetches metadata @high
  Given Step 4 has just rendered without any per-inventory scoring
  When the user clicks "View Inventories" in the Plan Map card footer
  Then per-inventory metadata is fetched/computed for the first time
  And the side panel opens read-only

Scenario: Manual Edit drawer is pre-populated with the current plan @critical
  When the user clicks "Manual Edit"
  Then the drawer opens with every currently-selected inventory ticked
  And no "Start fresh / keep recommendations" interstitial appears

Scenario: Recommendation pool excludes others' active reservations @critical @cross_feature
  Given inventory X is under an active reservation by tenant B
  When the planner under tenant A reaches Step 4
  Then inventory X is not offered in the recommended plan or the manual pool

Scenario: Recommendation pool excludes owner-blacklisted execution paths @high
  Given inventory Y is blacklisted by its owner from the "open auction" path
  When the planner selects the open-auction path and reaches Step 4
  Then inventory Y is not offered
```

### Section 4.4 — Manual Edit drawer bulk import

```gherkin
Scenario: Bulk import accepts a CSV file drop @high
  Given the Manual Edit drawer is open
  When the user drops "panels.csv" into the Bulk Import area
  Then the first column is parsed as reference IDs

Scenario: Bulk import accepts pasted IDs separated by commas, semicolons, spaces or newlines @high
  Given the Manual Edit drawer is open
  When the user pastes "INV-1, INV-2; INV-3\nINV-4" into the textarea
  Then four IDs are detected: INV-1, INV-2, INV-3, INV-4

Scenario: Validation categorises IDs into valid, mismatch, unknown @critical
  Given pasted IDs include valid IDs, IDs in cities outside the targeting, and unknown IDs
  When the user clicks Validate
  Then the panel shows three buckets: Valid, Outside current targeting, Unknown
  And each mismatch row lists a per-row reason

Scenario: Targeting mismatch on city @high
  Given the campaign targets "Mumbai" only
  When the user pastes an ID whose inventory is in "Delhi"
  Then it appears in "Outside current targeting" with reason 'City "Delhi" not in targeted geography'

Scenario: Targeting mismatch on venue type @high
  Given the campaign targets venues "transit, airport"
  When the user pastes an ID whose venue type is "retail"
  Then it appears in "Outside current targeting" with reason 'Venue type "retail" not in targeted venues'

Scenario: Targeting mismatch on format @high
  Given the campaign targets formats "Bulletin, Digital Billboard"
  When the user pastes an ID whose format is "Wallscape"
  Then it appears in "Outside current targeting" with reason 'Format "Wallscape" not in targeted formats'

Scenario: Region → state fallback when matching geography @medium
  Given the targeting includes the region "Maharashtra" but no city named "Pune"
  When a pasted ID is in Pune, Maharashtra
  Then it is treated as valid (region fallback)

Scenario: Select-all toggle on the mismatch checklist @high
  Given mismatches are rendered as a checklist
  When the user clicks the select-all toggle
  Then every mismatch row becomes ticked
  And clicking it again unticks them all

Scenario: Only ticked mismatch rows are added on Save @critical
  Given 5 mismatches are shown and 2 are ticked
  When the user clicks Save
  Then only those 2 mismatches plus all valid IDs are added to the selection

Scenario: Manually-edited plan does not silently overwrite on upstream change @high
  Given the user has manually edited the plan
  When budget, dates or targeting change upstream
  Then the auto-plan banner shows "Targeting changed since your manual edits — apply the new plan?"
  But the selection is NOT replaced automatically
```

### Section 4.5 — Step 5 Optimization

```gherkin
Scenario: Only Schedule and Auto-Optimize tabs are shown @high
  When the user reaches Step 5
  Then the tab bar shows Schedule (default) and Auto-Optimize — no Budget Allocation tab

Scenario: Inventory-level scheduling grid @high
  Given an inventory is selected in Step 4
  When the user opens its schedule editor in Step 5
  Then a 24×7 grid is rendered with preset patterns
  And selected cells respect the inventory's operating hours
  And metrics (impressions, ad plays, reach, SOV, SOT) update in real time
```

---

## 5. Inventory & OpenOOH Venue Tree (PRD §5)

```gherkin
Scenario: Inventory side panel is consistent across surfaces @medium
  Given an inventory pin is clicked
  When the side panel opens from Explore, Wizard Step 4 map, Campaign Detail, or Plan Map
  Then the same component renders the same data

Scenario: Performance tab content @medium
  When the user opens the Performance tab in the inventory side panel
  Then it shows monthly-average demographics, eCPM (not Cost per Day), and tooltips on every metric
  And the Est. Cost is clearly labelled as monthly data
  And Completion Rate and ROI Estimate are NOT present
```

---

## 6. Recommendation Engine (PRD §6)

```gherkin
Scenario: Run ID is persisted for audit @high
  When the engine returns a plan
  Then `campaigns.recommendationRunId` is stored
  And the audit trail can resolve the run ID to its scoring model version

Scenario: Carbon Goal Mode = Minimize downranks high-emission inventory @medium
  Given Carbon Goal Mode is "Minimize"
  When the engine scores candidates
  Then high carbon-per-play inventory is sorted lower
```

---

## 7. Approval Workflows (PRD §7)

### Feature: Submit Plan
```gherkin
Scenario: Submit Plan visible only to creator and only when Draft or Planned @critical
  Given the user is the campaign creator
  Then the Submit Plan button is visible while status ∈ {Draft, Planned}
  But hidden for status ∈ {Reviewing, Approved, Active, Paused, Completed, Rejected}
  And invisible to any non-creator user
```

### Feature: Tier 1 — Internal Company Approval
```gherkin
Scenario: Any flag-holder in the creator's company can approve at Tier 1 @critical
  Given the campaign creator's company has 3 users with canApproveCampaigns = true
  When any one of them clicks Approve
  Then Tier 1 closes

Scenario: Same-company gate prevents foreign approvers @critical @security
  Given a user with canApproveCampaigns = true belongs to a different company
  When they attempt to approve the plan
  Then the server returns 403
  And no transition occurs

Scenario: Tier 1 stalls when no flag-holder exists @medium
  Given no user in the creator's company has canApproveCampaigns = true
  When Submit Plan is fired
  Then Tier 1 stays In Progress until the Admin Console grants the flag

Scenario: Self-approval at Tier 1 is allowed and audited @medium
  Given the creator carries canApproveCampaigns = true
  When they approve their own plan
  Then Tier 1 closes
  And the audit history explicitly records "self-approved"
```

### Feature: Tier 2 — Media Owner Approval
```gherkin
Scenario: Tier 2 tracks each media owner independently @critical
  Given a plan has inventory from PVR INOX, GSC and a billboard owner
  When all three approve their rows
  Then Tier 2 is Approved
  But if any has not responded, Tier 2 shows Partial

Scenario: An inventory swap removes the prior owner's row and adds the new owner's @high
  Given GSC declined at Tier 2
  When the planner swaps GSC inventory for TGV inventory
  Then GSC's row is removed from Tier 2
  And TGV's row is added in Pending
  And PVR INOX and the billboard owner are NOT asked to re-approve

Scenario: Inventory-scope gate prevents foreign MO approvals @critical @security
  Given a media owner owns no inventory on the plan
  When they attempt to click Approve
  Then the server returns 403

Scenario: Media-owner-led plans collapse Tier 2 implicitly @medium
  Given the creator's company owns every inventory on the plan
  When Submit Plan is fired
  Then Tier 2 auto-passes via checkSelfApprovalScenario
  And only Tier 1 is required to reach Approved
```

### Feature: Re-approval matrix (per PRD §7.3 table)
```gherkin
Scenario Outline: Re-approval scope by changed field @critical
  Given an Approved campaign
  When the planner changes <field>
  Then Tier 1 re-runs = <t1>
  And Tier 2 re-runs = <t2>

  Examples:
    | field                       | t1  | t2                                   |
    | budget                      | yes | only affected owner row              |
    | dates                       | yes | all owner rows                       |
    | targeting                   | yes | all owner rows                       |
    | inventory swap              | no  | only the new owner row               |
    | line-item price             | no  | only the affected owner row          |
    | creative (same spec)        | no  | no                                   |
    | creative (different spec)   | no  | no                                   |
```

### Feature: Request for Deal (RFD)
```gherkin
Scenario: RFD action visible only in editable statuses @high
  Given a campaign in <status>
  Then the RFD menu item is <visible> and <enabled>

  Examples:
    | status     | visible | enabled |
    | draft      | yes     | enabled |
    | planned    | yes     | enabled |
    | reviewing  | yes     | enabled |
    | rejected   | yes     | enabled |
    | approved   | yes     | disabled with tooltip |
    | active     | yes     | disabled with tooltip |
    | completed  | yes     | disabled with tooltip |

Scenario: RFD denied to non-creator @critical @security
  When a user who is not the campaign creator fires RFD
  Then the server returns 403 "Only the campaign creator may request a deal"

Scenario: RFD denied without hasActivateAccess @critical @security
  Given the user lacks hasActivateAccess
  When they fire RFD
  Then the server returns 403 explaining the required flag

Scenario: RFD denied for media-owner-led plans @critical @security
  Given the user's company businessType = "media_owner"
  When they fire RFD
  Then the server returns 403

Scenario: Approved RFD routes the handoff to Activate @high @cross_feature
  Given a plan has rfdRequested = true
  When Tier 2 closes
  Then the execution handoff destination is Activate (Programmatic Deal)
  And no Influence Direct line items are written
```

### Feature: Execution Handoff (PRD §7.5)
```gherkin
Scenario Outline: Destination is picked by inventory class @critical
  Given a plan with composition <comp>
  When Tier 2 closes
  Then the handoff destination is <dest>
  And the line-item type is <type>

  Examples:
    | comp                          | dest        | type                |
    | all digital                   | Influence   | Direct / Standard   |
    | all classic                   | OMS         | Direct / Standard   |
    | mixed digital + classic       | Both        | Direct / Standard   |
    | RFD flag set                  | Activate    | Programmatic Deal   |

Scenario: Handoff banner colour @medium
  Given a Direct/Standard handoff has been written
  Then a green banner enumerating each line item appears on Campaign Detail
  Given an RFD handoff has been written
  Then the banner is purple
```

### Feature: Plan Approval Inbox
```gherkin
Scenario: Inbox lists every Reviewing campaign for the active tenant @high
  Given the tenant has 5 campaigns in Reviewing
  When the approver opens Plan Approval
  Then all 5 rows are listed

Scenario: Rows in Tier 2 are listed but not selectable @high
  Given 2 of the 5 are at Tier 2
  Then those 2 rows have a "Tier 2 — Media Owner" badge and no checkbox

Scenario: Bulk approve fans out one POST per row @critical
  Given the user has selected 3 actionable rows
  When they click Bulk Approve
  Then 3 POST /api/campaigns/:id/approve-stage requests fire
  And each carries the bulk-approved comment for audit
  And the final toast reads "X approved · Y failed"

Scenario: Inbox-zero empty state @low
  Given there are zero Reviewing campaigns
  When the page loads
  Then a calm "Inbox zero." card centres the working area
  And no fake placeholder rows are shown

Scenario: User without canApproveCampaigns sees amber notice @medium
  Then an amber notice above the table explains the user can browse but not act
  And the bulk-approve button is hidden
```

---

## 8. Price Management & Negotiation (PRD §8)

### Feature: Bilateral lock contract
```gherkin
Scenario: Single Accept does not lock the row @critical
  Given the buyer clicks Accept on the latest offer
  Then a single tick appears on the buyer side
  And the seller side shows "Awaiting your acceptance"
  And the row is NOT shaded green
  And the price field is NOT locked

Scenario: Bilateral Accept locks the row @critical
  Given both parties have ticked Accept on the same offer
  Then the row turns light-green
  And the price field is locked
  And Stage 3 approval can pass for this owner row

Scenario: Decline reverts to Rate Card and re-opens negotiation @high
  Given a Proposed or Counter offer is on the row
  When either side clicks Decline
  Then the row reverts to Rate Card
  And a new thread can be started
  And Stage 3 for that owner row cannot pass until resolved

Scenario: Expiry on Day 8 reverts to Rate Card @medium
  Given a Proposed offer sits without resolution for 7 days
  When the sweeper runs on day 8
  Then the row state becomes Expired and reverts to Rate Card
  And a toast notifies both parties

Scenario: Inventory-level Accepted badge requires all schedules locked @high
  Given an inventory has 3 schedule rows
  When only 2 are bilaterally accepted
  Then no inventory-level Accepted badge appears
  When the 3rd is bilaterally accepted
  Then the inventory-level Accepted badge appears

Scenario: Price source label colour-coding @medium
  Given the proposer is from the same company as the viewer
  Then the source label renders muted-grey
  Given the proposer is from a different company
  Then the source label renders amber
```

### Feature: Ripple of a price change on an Approved campaign
```gherkin
Scenario: Approved + price change reverts campaign to Reviewing, scoped to affected row @critical @cross_feature
  Given an Approved campaign with bilateral locks on every line
  When the planner changes the price on a single line for media owner X
  Then the campaign status reverts to Reviewing
  And Stage 1 stays Approved
  And Stage 2 stays Approved
  And Stage 3 row for X becomes Pending
  And the existing sent proposal becomes a previous version
  And a new proposal version is auto-drafted
  And Statement Builder removes the campaign from the current draft
  And two audit entries are written: "price changed by X" and "Stage 3 reset for owner X"

Scenario: Edit gate on Approved campaigns @critical
  Given the campaign is Approved
  When the planner opens Price Management
  Then every action button is disabled
  Until the planner clicks "Edit pricing — this will re-open approval"
```

### Feature: Three blocks that prevent any price change
```gherkin
Scenario Outline: Price change blocks @high
  Given the line item is <condition>
  When any user attempts a price change
  Then the action is blocked

  Examples:
    | condition                                          |
    | on a Rejected campaign                             |
    | on a Completed campaign                            |
    | on inventory with the "no negotiation" rate flag   |
    | viewed by an Advertiser role user                  |
```

### Feature: Bulk action bar
```gherkin
Scenario: Selecting rows switches the carousel to the action bar @medium
  Given the page shows the tips carousel
  When the user selects ≥ 1 row
  Then the carousel is replaced with the action bar (Accept Price, Clear Selection, Apply Discount, Apply Bonus, Change SOV)
  And the action bar uses a controlled-state pattern with bidirectional sync
```

---

## 9. Reservations (PRD §9)

```gherkin
Scenario Outline: Reservation state machine @critical
  Given a reservation is in state <from>
  When the trigger <trigger> fires
  Then the new state is <to>

  Examples:
    | from           | trigger                              | to             |
    | Pending        | Campaign submitted (Draft→Planned)   | Hold Requested |
    | Hold Requested | Seller clicks Approve                | Reserved       |
    | Reserved       | 7 days elapse without conversion     | Expired        |
    | Reserved       | Buyer clicks Release                 | Released       |
    | Hold Requested | Seller clicks Decline                | Declined       |
    | Reserved       | Campaign reaches Approved            | Booked         |

Scenario: Hold prevents another tenant from seeing the inventory @critical
  Given inventory X has a Hold Requested or Reserved by tenant A
  When tenant B opens Step 4 or Explore
  Then inventory X is not in the candidate pool

Scenario: Booked removes the expiry countdown and is final @high
  Given a reservation is Reserved
  When the campaign becomes Approved
  Then the reservation flips to Booked
  And the expiry timer disappears

Scenario: Decline tells the buyer to use Modify Plan in Step 4 @medium
  Given the seller declines a hold
  Then the buyer sees a toast linking to Step 4 Modify Plan
  And Stage 3 approval cannot pass for that owner row
```

---

## 10. Proposals & Media Plan (PRD §10)

### Feature: Custom fee disclosure (per PRD §10.2 table)
```gherkin
Scenario Outline: Fee visibility filter by viewer role @critical @security
  Given <creator> creates the plan
  And <creator> adds a custom fee marked as <visibility>
  When <viewer> opens the Media Plan
  Then they see <result>

  Examples:
    | creator      | visibility                | viewer       | result                                              |
    | Agency       | internal only             | Media Owner  | Original media cost only; fee hidden                |
    | Agency       | include in media plan ON  | Media Owner  | Original media cost + fee as separate line          |
    | Media Owner  | internal only             | Agency       | Single combined media cost (no breakdown)           |
    | Media Owner  | include in media plan ON  | Agency       | Original media cost + fee as separate line          |
    | anyone       | any                       | Internal     | Every fee with full breakdown                       |
    | anyone       | default                   | Advertiser   | Rolled-up media cost only                           |

Scenario: Server-side fee filter @critical @security
  Given a viewer role is Agency
  When GET /api/campaigns/:id/fees is requested
  Then the response excludes every fee whose showToAgency = false
```

### Feature: Comments and @-mentions
```gherkin
Scenario: Untagged comment is internal-only @critical @security
  Given an Agency planner posts "Need to confirm carbon by Friday"
  When ABC Media Owner opens the Comments tab
  Then they see an empty thread with the legend "Comments are visible once another party @-mentions you"

Scenario: @-mention shares only that comment @critical
  Given the planner posts "@XYZ Media Owner please swap screen #4"
  When XYZ opens comments
  Then they see exactly that one comment
  And ABC Media Owner still sees nothing

Scenario: Reply under a tagged thread is NOT auto-shared @high
  Given a tagged comment exists from the planner to XYZ
  When the planner posts a reply with no mention
  Then the reply is internal-only

Scenario: Live visibility badge on the composer @medium
  When the user types "@XYZ Media Owner …"
  Then the composer shows "Visible to your company + XYZ Media Owner"
  When the user removes the mention
  Then the badge reads "Internal note — only your company will see this."
```

### Feature: Versioning on price change
```gherkin
Scenario: Sent proposal gets a new version on price change @critical
  Given a Sent proposal exists for a campaign
  When any line-item price changes
  Then a new proposal version is auto-generated
  And the previous version is demoted to "superseded"
  And the share-link token is rotated
  And the recipient receives an email notification

Scenario: Regenerating supersedes an Accepted version with explicit confirm @high
  Given a proposal is in Accepted state
  When the planner clicks Generate Proposal
  Then a confirm dialog appears: "Generate new version, supersede the accepted one"
  And on confirm an extra audit entry is written
```

### Feature: Media Plan deck conditional rendering (PRD §10.5)
```gherkin
Scenario Outline: Slide rendering rules @high
  Given a campaign with <data condition>
  When the Media Plan page loads in Presentation view
  Then slide <slide> is <visible|suppressed>

  Examples:
    | data condition                                              | slide                       | visible|suppressed |
    | zero inventories                                            | Empty Plan only             | only this slide    |
    | no demographics, behaviours, signals or cinema attributes   | Targeting                   | suppressed         |
    | no audience targeting AND no custom schedule                | Audience Trends             | suppressed         |
    | no custom schedule (24/7 default)                           | Daypart Heatmap             | suppressed         |
    | inventory exists                                            | Inventory Snapshots         | visible            |

Scenario: Inventory Snapshots pagination @high
  Given a plan has 28 inventories
  When the Inventory Snapshots slide renders
  Then 5 sub-slides are emitted (5.6.1 through 5.6.5)
  And each sub-slide carries up to 6 cards in a 3×2 grid
  And cards are sorted by daily impressions desc

Scenario: Cinema card thumbnail fallback @low
  Given an inventory has no thumbnail on file
  When its card renders on Inventory Snapshots
  Then a neutral channel-glyph fallback is shown — never a broken external image

Scenario: SOV computation per inventory @medium
  Given an inventory has schedule entries with a max active-hour count of 84 per week
  Then SOV = round((84/168) × 100, 1) = 50.0
  Given the inventory has no schedule on file
  Then SOV = 100.0 (treated as 24/7)

Scenario: FX disclosure appears only on cross-currency plans @high
  Given the plan currency = MYR and the tenant local currency = USD
  Then the Cost Breakdown slide and Costing tab show the FX disclosure line
  Given the plan currency matches the tenant local currency
  Then no FX disclosure renders

Scenario: Status chrome band reflects current status @medium
  Given the campaign is Reviewing
  Then an amber band shows the active approval stage and the actor it waits on
  Given the campaign is Approved
  Then a lock banner shows approver, timestamp and a link to Price Management
```

### Feature: Exports
```gherkin
Scenario: PowerPoint export mirrors the on-screen deck @high
  When the user downloads PowerPoint
  Then slides match §10.5.2 order
  And Inventory Snapshots paginates the same way as on screen

Scenario: Excel export adds a Schedules sheet @medium
  When the user downloads Excel
  Then the workbook contains a dedicated "Schedules" sheet with per-inventory cadence detail

Scenario: Public share-link suppresses controls @high
  Given the page is loaded via a public share link
  Then the theme picker, Download and Share buttons are hidden
  And the read-only banner shows the link's expiry timestamp
```

---

## 11. Creatives & Creative Assignment (PRD §11)

```gherkin
Scenario: Aspect-ratio mismatch warns and allows override @high
  Given a portrait creative is dragged onto landscape inventory
  Then a confirm-override prompt appears
  When the user confirms
  Then the binding is created
  And the audit history records "forced match"

Scenario: Duration mismatch cannot be overridden @high
  Given a video creative duration ≠ any inventory slot length
  When the user drops the creative
  Then the drop is rejected with an error
  And no override is offered

Scenario: File-size cap is hard @critical
  Given a creative file exceeds the inventory's CMS spec size
  When the user attempts to bind
  Then the action is blocked

Scenario: Draft/Planned campaigns cannot bind creatives @high
  Given a campaign in Draft or Planned
  When the user attempts an assignment
  Then the assignment surface is read-only

Scenario: Different-spec swap on Approved triggers Stage 3 re-approval @critical @cross_feature
  Given an Approved campaign with a bound creative
  When the planner swaps in a creative with a different spec or duration
  Then Stage 3 re-approval fires for the affected media owner only
```

---

## 12. Statements & Custom Fees (PRD §12)

```gherkin
Scenario: Campaign mid-re-approval is deferred to next cycle @high
  Given a campaign is in Reviewing after a price change
  When Statement Builder runs
  Then the campaign is NOT included in the current draft

Scenario: Pending line items are footnoted, not included @medium
  Given a campaign has 5 lines at Locked and 1 at Counter
  When the statement is previewed
  Then the Counter line is excluded with a "Pending" footnote

Scenario: NetSuite-confirmed statement is locked on Planner side @critical
  Given a statement has been synced and confirmed by NetSuite
  When the user attempts to edit any field
  Then the action is blocked
  And the UI explains further edits must be made in NetSuite
```

---

## 13. Carbon Tracking (PRD §13)

```gherkin
Scenario: Cap mode warns but does not block @medium
  Given Carbon Goal Mode = Cap with a 100 kg threshold
  When the forecast exceeds 100 kg
  Then a warning banner appears on Step 5
  But the user is NOT blocked from continuing

Scenario: Minimize mode influences ranking (not blocking) @medium
  Given Carbon Goal Mode = Minimize
  When the engine returns the plan
  Then high-emission inventory ranks lower
  And the planner can still pick any inventory manually
```

---

## 14. Tags (PRD §14)

```gherkin
Scenario: Tags are tenant-scoped @critical @security
  Given the user creates tag "Q4 Beverages" under MediaHub Agency
  When they switch tenant to MediaHub Network
  Then "Q4 Beverages" does not appear in the tag dictionary

Scenario: Usage counts recompute on mapping change @medium
  Given a tag has usage count = 3
  When a new campaign is tagged with it
  Then the count becomes 4 on next render of the management page

Scenario: Auto-build statement from a tag @high
  Given a statement is built from the tag "Q4 Beverages"
  When 3 campaigns carry that tag
  Then those 3 campaigns appear on the statement
```

---

## 15. Signals (PRD §15)

```gherkin
Scenario: Signals only fire on Active campaigns @critical
  Given a signal is attached to a Draft, Planned, Reviewing or Approved campaign
  When the condition is true
  Then no action is taken

Scenario: Test mode runs a dry evaluation @medium
  Given a signal is in Test mode
  When the condition evaluates to true
  Then no creative swap or pause is performed
  And the planner sees the dry-run result

Scenario: Signal-fired creative swap must satisfy spec rules @high @cross_feature
  When a signal attempts to swap to a creative with a different spec
  Then the swap is blocked by the same-spec rule (§11.1)
```

---

## 16. POI Management (PRD §16)

```gherkin
Scenario: POI radius capped at 50 km @medium
  When the user uploads a POI with radius = 60000 m
  Then validation rejects the row

Scenario: POI is either inclusion or exclusion, never both @medium
  When the CSV row marks the same POI as both include and exclude
  Then validation rejects the row

Scenario: POI filters the recommendation pool by haversine @high @cross_feature
  Given a campaign has 7 POIs all in Mumbai
  When the engine builds the plan
  Then no inventory outside the union of POI radii is offered
```

---

## 17. Explore (PRD §17)

```gherkin
Scenario: Add to Plan creates a draft with chosen inventories pre-loaded @high
  Given the user has filtered Explore and selected 3 inventories
  When they click "Add to Plan"
  Then a draft campaign is created
  And the user lands on Step 4 of the wizard with the 3 inventories pre-loaded as Pending reservations

Scenario: Explore respects the reservation filter @critical
  Given inventory X is held by another tenant
  When the user browses Explore
  Then inventory X is hidden from the map and the list

Scenario: Explore filter state is shareable as a link @low
  When the user copies the URL
  Then opening it in another browser session restores the same filter state
```

---

## 18. IMS ↔ Planner (PRD §18)

```gherkin
Scenario: Planner writes a booking request on full approval @critical @cross_feature
  When a campaign reaches Approved
  Then Planner sends a booking-request event to IMS

Scenario: Planner writes a hold request on inventory selection @high
  When inventory is added to a plan
  Then a hold-request event is sent to IMS

Scenario: Planner writes a hold release on reservation release/expiry @medium
  When a reservation is Released or Expired
  Then a hold-release event is sent to IMS

Scenario: Planner sends creative-delivery package on assignment @high
  When a creative is bound to a line item
  Then a creative-delivery package is sent to IMS
```

---

## 19. Currency & Localisation (PRD §19)

```gherkin
Scenario Outline: Currency code rendering @high
  Given the tenant currency is <code>
  When any monetary value renders anywhere in the product
  Then it reads "<code> 7,719" — never "$7,719"

  Examples:
    | code |
    | USD  |
    | MYR  |
    | SGD  |
    | AED  |
    | IDR  |
    | PHP  |
    | VND  |
    | INR  |
    | GBP  |
    | EUR  |
    | AUD  |

Scenario: Cross-campaign rollups use start-date FX @medium
  Given a statement covers campaigns in MYR and SGD
  When the report converts to the tenant's USD reporting currency
  Then each campaign uses the FX rate from its own start date for stability
```

---

## 20. Profile Settings (PRD §20)

```gherkin
Scenario: Profile page has four tabs @medium
  When the user opens /profile
  Then Profile, Account, Notifications, Privacy tabs are visible

Scenario: Change Password drawer with live checklist @high
  When the user clicks Change Password on the Account tab
  Then a right-side drawer opens
  And typing into the New Password field updates a live requirements checklist
  And submission is blocked until all requirements are met and the backend validates the old password
```

---

## 21. Cross-cutting Security & Authorisation

```gherkin
Scenario: Password hash is never exposed by any auth endpoint @critical @security
  When any of /api/login, /api/user, /api/register returns a user object
  Then the response does NOT contain a passwordHash field

Scenario: Server enforces authorisation against the active tenant on every request @critical @security
  Given a session cookie was created under tenant A
  When the user sets the active tenant to B and fires a request
  Then the server reads the active-tenant header/cookie and authorises against B
  And NOT against A
```

---

## Coverage Matrix (PRD section → test count)

| PRD § | Section                                         | Scenarios |
|-------|-------------------------------------------------|-----------|
| 1     | Authentication & Multi-tenant                   | 9         |
| 2     | Dashboard                                       | 8         |
| 3     | Status lifecycle                                | 7         |
| 4.1   | Wizard Step 1                                   | 7         |
| 4.2   | Wizard Step 2                                   | 7         |
| 4.3   | Wizard Step 3                                   | 8         |
| 4.4   | Wizard Step 4 (Plan view + Manual Edit)         | 18        |
| 4.5   | Wizard Step 5                                   | 2         |
| 5     | Inventory side panel                            | 2         |
| 6     | Recommendation Engine                           | 2         |
| 7     | Approval workflows + RFD + Inbox                | 18        |
| 8     | Price Management                                | 9         |
| 9     | Reservations                                    | 4         |
| 10    | Proposals + Media Plan                          | 16        |
| 11    | Creatives                                       | 5         |
| 12    | Statements                                      | 3         |
| 13    | Carbon                                          | 2         |
| 14    | Tags                                            | 3         |
| 15    | Signals                                         | 3         |
| 16    | POI                                             | 3         |
| 17    | Explore                                         | 3         |
| 18    | IMS handoff                                     | 4         |
| 19    | Currency                                        | 2         |
| 20    | Profile                                         | 2         |
| —     | Cross-cutting security                          | 2         |
| **Total** |                                             | **149**   |

---

## Execution notes

- Scenarios tagged `@critical` should run on every PR; `@high` on nightly; the rest on weekly regression.
- All scenarios assume the tenant-scoped query invalidation pattern (`URLSearchParams` with `tenantCompanyId` + date params) is in place — when it is missing, the relevant scenario will fail with stale data.
- Cross-feature scenarios (`@cross_feature`) verify behaviour described in two or more PRD sections; they are the most valuable for catching regressions when a single module is refactored.
- The PRD's Miro diagrams listed in §3.1, §4 lead, §7, §8, §9 should be treated as the authoritative state diagrams when a scenario's expected transitions look ambiguous.
