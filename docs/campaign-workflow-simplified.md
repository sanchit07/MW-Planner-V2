# Campaign Workflow - Simplified Approach

By Sanchit Neema (with Agent assistance)  
Last updated: November 2025

---

## 1. Problem Statement

The current campaign workflow in MW Planner creates confusion among users due to overlapping statuses, unclear next steps, and multiple paths that appear simultaneously. Users often ask "what do I do next?" because the interface presents too many options without context.

The primary issues identified are:

**Status Confusion**: Campaign-level statuses like `negotiating` and `deal_requested` conflate the overall campaign state with individual inventory item states. A campaign can have 10 inventories where 3 are negotiating, 5 are booked, and 2 are pending - but showing "Negotiating" as the campaign status is misleading.

**Execution Path Ambiguity**: Users don't clearly understand whether their campaign will execute as a traditional guaranteed booking (via Influence/adserver.movingwalls.com) or as an open auction programmatic buy (via Activate.movingwalls.com). The decision point is buried and the consequences are not explained upfront.

**Action Overload**: The actions menu presents Reserve, Price Management, Approval, Assign Creative, and Execute all at once, regardless of campaign state. Users don't know which action to take first or whether certain actions are even relevant to their use case.

This document proposes a simplified model that separates campaign-level status from item-level status, introduces clear execution path selection, and presents only contextually relevant actions.

---

## 2. Guiding Principles

The redesign follows these core principles derived from the IAB OOH Open Direct 2.1 specification and operational realities of the OOH industry.

**Separation of Concerns**: The campaign (order) level tracks the overall lifecycle from planning to completion. The line item (inventory/schedule) level tracks the booking state of each individual placement. These two levels have different status vocabularies and transition rules.

**Progressive Disclosure**: Show users only what they need to see at each stage. A campaign in Draft state doesn't need approval buttons. A campaign that's already Approved doesn't need reservation controls.

**Explicit Execution Selection**: Users must consciously choose between Quick Launch (open auction) and Full Workflow (traditional) paths. This decision has real consequences and should not be implicit or reversible without warning.

**IAB Alignment**: Status values and transitions should align with the OpenDirect 2.1 specification where applicable, ensuring interoperability with SSPs, DSPs, and other OOH tech stack components.

---

## 3. Status Model

### 3.1 Campaign-Level Status

The campaign status represents the overall state of the campaign as a planning and execution unit. It answers the question "where is this campaign in its lifecycle?"

| Status | Description | What User Sees |
|--------|-------------|----------------|
| Draft | Campaign is being created or edited. No commitments made. | Gray badge. Full editing enabled. |
| Planned | Campaign planning is complete. Ready for next action. | Blue badge. Execution path selection appears. |
| Reviewing | Campaign is in the approval workflow. Awaiting stakeholder decisions. | Amber badge. Progress tracker visible. |
| Approved | All required approvals obtained. Ready for execution. | Green badge. Execute button prominent. |
| Active | Campaign is live and delivering. | Bright green badge. Performance metrics visible. |
| Paused | Campaign temporarily stopped. Can resume. | Yellow badge. Resume button visible. |
| Completed | Campaign reached its end date and finished normally. | Purple badge. Reports available. |
| Rejected | Campaign declined during approval process. | Red badge. Reason logged. |

Note that `Negotiating`, `Pending`, and `Deal Requested` are deliberately removed from campaign-level status. These states apply to individual inventory items, not the campaign as a whole.

### 3.2 Item-Level Status (Inventory/Schedule)

Each inventory placement within a campaign maintains its own status. This status tracks the booking lifecycle for that specific placement and is aligned with IAB OpenDirect line status conventions.

| Status | Description | Transitions To |
|--------|-------------|----------------|
| pending | Item added to campaign but no action taken | reserved, negotiating, booked, removed |
| reserved | Inventory temporarily held with expiry date | booked, expired, released |
| negotiating | Price discussion active between buyer and seller | price_agreed, declined |
| price_agreed | Both parties accepted the negotiated price | booked, lapsed |
| booked | Confirmed and locked. Creative can be assigned. | inflight, cancelled |
| inflight | Currently delivering ads | paused, completed |
| paused | Temporarily stopped | inflight, cancelled |
| completed | Finished delivery | (terminal) |
| declined | Media owner rejected the booking request | pending (if retrying) |
| expired | Reservation timed out without conversion | pending (if retrying) |
| cancelled | Booking cancelled | (terminal) |

The campaign detail page shows a summary of item statuses: "3 booked, 2 negotiating, 1 pending" rather than forcing these into the campaign badge.

### 3.3 Approval Stage Status

When a campaign enters the Reviewing state, the approval workflow activates. Each approval stage maintains its own status.

| Stage | Status Options |
|-------|----------------|
| agency_acceptance | pending, in_progress, completed, changes_requested, skipped |
| platform_review | pending, in_progress, completed, changes_requested, skipped |
| media_owner_approval | pending, in_progress, completed, rejected, partial |

The stage advances sequentially. A rejection at any stage terminates the workflow and sets the campaign status to a special `Rejected` sub-state of Reviewing (user must revise and resubmit). The `skipped` status applies when the stage is not applicable - for example, if the campaign creator is an internal user, the agency_acceptance stage is skipped.

**Self-Approval**: If the user submitting the campaign IS the designated approver for a stage (e.g., they are their own manager or have self-approval rights configured), they can approve that stage directly without waiting. The system recognizes this and either auto-advances or shows an immediate "Approve" action.

**Note on Terminology**: The middle approval stage is called "Platform Review" (previously "internal_review"). This is when the Moving Walls commercial team validates deal structure, pricing, and compliance. From the agency or media owner perspective, this is the platform doing its due diligence before connecting buyer and seller.

### 3.4 Status Display Model

The campaign status and approval progress are displayed separately to avoid confusion. The status badge remains a single, clean word that's easy to filter and sort. The stage indicator and progress are shown alongside but distinct.

**Visual Layout**:

```
┌──────────────────────────────────────────────────────────────────┐
│  Campaign: Holiday Campaign 2024                                  │
│                                                                   │
│  [Reviewing]  •  Agency Acceptance                               │
│               ↓                                                   │
│  Stage Progress: Awaiting manager approval                       │
└──────────────────────────────────────────────────────────────────┘
```

**Status Progression Through Approval Workflow**:

| What Happens | Status Badge | Stage Indicator |
|--------------|--------------|-----------------|
| User submits campaign for approval | `Reviewing` | Agency Acceptance |
| Manager approves (or self-approves if permitted) | `Reviewing` | Platform Review |
| MW commercial team approves | `Reviewing` | Media Owner Approval |
| Media owners respond (mixed results) | `Reviewing` | Media Owner Approval (see progress) |
| Agency decides to proceed with approved MOs | `Approved` | (workflow complete) |

**Media Owner Approval Progress Display**:

When the campaign is at the `media_owner_approval` stage with multiple media owners, the progress is shown separately from the status badge:

```
┌──────────────────────────────────────────────────────────────────┐
│  [Reviewing]  •  Media Owner Approval                            │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  ✓ Approved: 3    ✗ Rejected: 1    ⏳ Pending: 1            │ │
│  │                                                              │ │
│  │  Clear Channel (2 items) .............. Approved ✓          │ │
│  │  Lamar Advertising (1 item) ........... Approved ✓          │ │
│  │  JCDecaux (2 items) ................... Approved ✓          │ │
│  │  OutFront Media (1 item) .............. Rejected ✗          │ │
│  │  Billboard Corp (1 item) .............. Pending ⏳ (Day 3)  │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                   │
│  [Proceed with Approved]  [Wait for Pending]  [View Details]     │
└──────────────────────────────────────────────────────────────────┘
```

**Key Principles**:

1. **Status badge is always a single word**: `Reviewing`, `Approved`, `Active`, etc. Never "Approved (3/5)" as a status.

2. **Stage indicator shows where in the workflow**: Displayed next to the status badge with a bullet separator.

3. **Progress details are expandable**: The counts and per-media-owner breakdown are shown in a detail section, not crammed into the badge.

4. **"Approved" means ready to execute**: The campaign only transitions to `Approved` when:
   - All media owners approved, OR
   - The agency consciously decides to proceed with partial approvals (excluding rejected/silent items)

5. **Filtering remains simple**: Users can filter campaigns by status (all `Reviewing` campaigns) without confusion about partial states.

---

## 4. Execution Paths

### 4.1 Quick Launch (Open Auction)

This path is for users who want to run campaigns programmatically via real-time bidding. It sends the campaign to Activate.movingwalls.com (the MW DSP).

**Characteristics**:
- No mandatory approval workflow (though user may optionally enable it)
- No price negotiation - pricing determined by auction
- No inventory reservation - bids placed in real-time
- Budget-based - system optimizes delivery within budget
- Available to: Advertisers, Agencies, Internal users

**When to use**: User has a budget and goals but doesn't need guaranteed placements. Wants to start immediately. Prefers efficiency over control.

**Workflow**: Planned → (optional Reviewing) → Active

### 4.2 Full Workflow (Traditional/Guaranteed)

This path is for traditional OOH buying where specific inventory is reserved, prices are negotiated, and delivery is guaranteed. It sends the campaign to Influence/adserver.movingwalls.com.

**Characteristics**:
- Approval workflow required (configurable stages)
- Price negotiation supported at campaign, inventory, and schedule levels
- Inventory reservation with expiry dates
- Creative assignment required before execution
- Guaranteed delivery of agreed impressions/plays
- Available to: All user types

**When to use**: User needs specific premium placements. Has clients who require guaranteed delivery. Wants to negotiate rates. Needs approval chain for compliance.

**Workflow**: Planned → (Reserve) → (Negotiate) → Reviewing → Approved → (Assign Creative) → Active

### 4.3 Hybrid Execution

Some campaigns may use both paths simultaneously. For example, an agency might reserve 5 premium billboards for guaranteed delivery while also running remaining budget on open auction to extend reach.

In hybrid mode, the campaign status reflects the traditional workflow (since it requires approvals), but specific inventory items may be flagged as "open auction" to indicate they'll be purchased programmatically. The campaign shows a dual badge indicating both execution methods are in use.

---

## 5. User Personas

User behavior in MW Planner varies significantly based on role, company type, and operational context. Understanding these personas is essential for implementing correct workflows and UI behaviors.

### 5.1 Persona Definitions

#### Agency Planner (Sarah)
**Role**: Media planner or buyer at an advertising agency  
**Company Type**: media_agency  
**Primary Goal**: Plan and execute OOH campaigns for advertiser clients  
**Key Behaviors**:
- Creates campaigns on behalf of advertisers
- Selects inventory across multiple media owners
- Requests holds (cannot directly reserve third-party inventory)
- Negotiates pricing with media owners
- Submits campaigns for internal approval before media owner stage
- Monitors campaign performance and reports to clients

**Reservation Rights**: Can REQUEST holds only. Media owner must confirm.  
**Approval Rights**: Cannot approve own campaigns (requires manager). Can approve if designated as stage approver.  
**Pricing Rights**: Can propose prices, accept/reject counter-offers.

#### Agency Manager (Tom)
**Role**: Account director or team lead at agency  
**Company Type**: media_agency  
**Primary Goal**: Oversee campaign quality, budget compliance, and client relationships  
**Key Behaviors**:
- Reviews and approves campaigns created by team members
- Ensures campaigns align with client objectives and budget
- Resolves escalations when media owners reject or counter
- May create campaigns directly for key accounts

**Reservation Rights**: Same as planner (REQUEST only).  
**Approval Rights**: Approves agency_acceptance stage. Can approve own campaigns if self-approval configured.  
**Pricing Rights**: Can approve budget increases beyond original allocation.

#### Media Owner Sales Rep (Mike)
**Role**: Sales executive at a media owner (billboard company, transit authority, etc.)  
**Company Type**: media_owner  
**Primary Goal**: Sell inventory, maximize revenue, maintain client relationships  
**Key Behaviors**:
- Creates campaigns/proposals for potential clients
- Directly reserves own company's inventory (hard hold)
- Approves or rejects booking requests from agencies
- Negotiates pricing within floor price limits
- May represent inventory proactively to attract buyers

**Reservation Rights**: Can RESERVE own inventory directly. Immediate calendar block.  
**Approval Rights**: Approves media_owner_approval stage for own inventory only.  
**Pricing Rights**: Can set prices within floor limits. Can accept/counter agency proposals.

#### Media Owner Admin (Jennifer)
**Role**: Operations or admin manager at media owner  
**Company Type**: media_owner  
**Primary Goal**: Manage inventory availability, approval workflows, team permissions  
**Key Behaviors**:
- Configures approval hierarchies for sales team
- Sets floor prices and pricing rules
- Manages landlord relationships for properties requiring additional approval
- Handles escalations and override situations

**Reservation Rights**: Same as sales rep plus can override/release holds.  
**Approval Rights**: Can configure delegation and timeout rules.  
**Pricing Rights**: Sets floor prices and discount limits.

#### Internal User (Priya)
**Role**: Account manager or operations staff at Moving Walls  
**Company Type**: internal  
**Primary Goal**: Facilitate deals, support partners, maintain platform health  
**Key Behaviors**:
- Creates campaigns on behalf of agencies or media owners
- Manages complex multi-vendor deals across regions
- Performs Platform Review stage approvals
- Intervenes in stuck negotiations or approval timeouts
- Creates Quick Agency records for non-onboarded agencies

**Reservation Rights**: Can reserve on behalf of either party. Can create priority holds.  
**Approval Rights**: Approves platform_review stage. Can accept on behalf of media owner if configured.  
**Pricing Rights**: Can suggest prices. Can facilitate negotiations. Cannot override agreed prices.

#### Advertiser User (Lisa)
**Role**: Marketing manager at the brand/advertiser  
**Company Type**: advertiser  
**Primary Goal**: Run advertising campaigns, monitor performance, control spending  
**Key Behaviors**:
- Creates campaigns directly or works with agency
- Approves budgets and creative concepts
- Monitors campaign delivery and ROI
- Less involved in tactical inventory selection

**Reservation Rights**: REQUEST only (same as agency).  
**Approval Rights**: May be included in agency_acceptance stage if configured.  
**Pricing Rights**: Sets budget limits. May require approval for overages.

#### Reseller User (Tom R.)
**Role**: Sales representative at a reseller company representing multiple small media owners  
**Company Type**: reseller  
**Primary Goal**: Sell inventory on behalf of represented media owners  
**Key Behaviors**:
- Acts on behalf of media owners without their own platform presence
- Can reserve represented inventory (if permitted by agreement)
- Negotiates within limits set by each media owner
- May have delegated approval authority

**Reservation Rights**: Can reserve represented media owner inventory if granted.  
**Approval Rights**: Approves on behalf of represented media owners if delegated.  
**Pricing Rights**: Negotiates within each media owner's floor price limits.

### 5.2 Persona Behavior Matrix

| Action | Agency Planner | Agency Manager | MO Sales Rep | Internal User | Advertiser |
|--------|---------------|----------------|--------------|---------------|------------|
| Create Campaign | ✓ | ✓ | ✓ | ✓ | ✓ |
| Request Hold | ✓ | ✓ | — | ✓ | ✓ |
| Direct Reserve | — | — | ✓ (own) | ✓ (on behalf) | — |
| Propose Price | ✓ | ✓ | ✓ | ✓ | Limited |
| Accept Counter | ✓ | ✓ | ✓ | ✓ | Limited |
| Approve Agency Stage | — | ✓ | — | — | Configurable |
| Approve Platform Stage | — | — | — | ✓ | — |
| Approve MO Stage | — | — | ✓ (own) | ✓ (on behalf) | — |
| View Other MO Inventory | Read-only | Read-only | — | ✓ | Read-only |
| Override Timeout | — | — | — | ✓ | — |

### 5.3 UI Implications by Persona

**What the UI shows based on logged-in user**:

| Element | Agency Users | Media Owner Users | Internal Users |
|---------|--------------|-------------------|----------------|
| Reservation Button | "Request Hold" | "Reserve" | "Reserve" or "Request Hold" |
| Pricing Actions | "Propose Price" | "Accept/Counter/Decline" | Both |
| Approval Panel | Shows when user is approver | Shows own inventory only | Shows all stages |
| Other MO Inventory | Visible (booking details hidden) | Hidden | Visible |
| Priority Hold Option | — | — | ✓ (if configured) |
| Accept on Behalf | — | — | ✓ (if configured) |

---

## 6. Basic Scenarios

### 6.1 Agency User - Quick Launch Scenario

**Persona**: Sarah is a media planner at a mid-size agency. She has a retail client launching a flash sale next week and needs OOH coverage immediately.

**Scenario Flow**:

Sarah creates a new campaign, entering budget ($50,000), target dates (next Monday to Sunday), and target locations (downtown shopping districts). She selects audience profiles and lets the system recommend inventory.

Upon completing the wizard, the campaign status becomes `Planned`. Sarah sees a decision panel: "How do you want to execute this campaign?" She clicks "Quick Launch (Open Auction)" because she doesn't have time for approvals and the client trusts her judgment.

The system asks for confirmation: "This will send your campaign to Activate for open auction bidding. Delivery is not guaranteed and depends on available inventory and bid competition. Continue?"

Sarah confirms. The campaign status changes directly to `Active` and the campaign appears in Activate.movingwalls.com within minutes. No approval workflow, no reservation, no negotiation. She monitors performance from the dashboard.

**Status Progression**: Draft → Planned → Active

### 6.2 Agency User - Full Workflow Scenario

**Persona**: James is a senior account director handling a luxury automotive brand. The client requires specific high-profile placements and has a corporate approval process.

**Scenario Flow**:

James creates a campaign for a new car launch, specifying premium inventory: Times Square digital spectacular, Hollywood Sunset Strip, and Chicago Magnificent Mile. Budget is $500,000.

Upon completing the wizard, status becomes `Planned`. James clicks "Full Workflow (Traditional)" because the client needs guaranteed premium placements.

James accesses the Campaign Workbench. He sees all three inventories in `pending` status. He selects Times Square and clicks "Request Hold" (agencies request, they don't directly reserve). The status changes to `reserved` with a 7-day expiry. He does the same for the other two.

While waiting for media owner confirmation, James opens Price Management. The rate card shows $150,000 for Times Square but he proposes $130,000 with justification. The inventory status becomes `negotiating`. Clear Channel's sales rep counter-offers $142,000. James accepts. Status becomes `price_agreed`.

Once all three inventories are `price_agreed` or `booked`, James clicks "Submit for Approval." Campaign status changes to `Reviewing`. His manager at the agency reviews and accepts (agency_acceptance stage completed). Since this agency has MW partnership, it goes to platform_review where MW commercial team validates the deal structure. Finally, media_owner_approval stage activates and each media owner confirms their inventory.

All three approve. Campaign status becomes `Approved`. James uploads creatives and assigns them to each inventory. He schedules the go-live date.

On launch day, the campaign status changes to `Active`. Inventory items change to `inflight`. James monitors delivery and performance.

**Status Progression**: Draft → Planned → Reviewing → Approved → Active

**Item Status Progression**: pending → reserved → negotiating → price_agreed → booked → inflight

### 6.3 Media Owner User - Reservation Scenario

**Persona**: Mike is a sales executive at Clear Channel responsible for the Times Square portfolio. He's meeting a potential client tomorrow and wants to show availability.

**Scenario Flow**:

Mike creates a campaign as a "pitch deck" for Coca-Cola's holiday campaign. He adds his own Times Square inventory to the plan.

Since Mike works for the media owner, he can directly reserve inventory without requesting. He clicks "Reserve" and sets an expiry of 14 days. The inventory status becomes `reserved`. The availability calendar now shows this timeslot as held.

Other sales reps at Clear Channel see the reservation when they try to book the same dates. They can contact Mike if there's a conflict.

Mike meets Coca-Cola's marketing team. They're interested but need internal budget approval. Mike extends the reservation by another 7 days.

Coca-Cola confirms. Mike converts the reservation to a booking. Since it's an internal campaign (media owner selling their own inventory), the approval workflow is simplified - platform_review and agency stages are skipped, going directly to confirmation.

**Status Progression**: Draft → Planned → Approved → Active

**Item Status Progression**: pending → reserved → booked → inflight

### 6.4 Internal (MW) User - Deal Creation Scenario

**Persona**: Priya is a Moving Walls account manager handling the APAC region. An agency approached MW for a multi-country campaign across 15 media owners.

**Scenario Flow**:

Priya creates the campaign on behalf of the agency (Quick Agency creation if the agency isn't onboarded yet). She selects inventories across Malaysia, Singapore, and Thailand from various media owners.

She chooses "Full Workflow" because this is a complex multi-vendor deal requiring approvals.

Priya manages the workbench, sending reservation requests to each media owner. As confirmations come in, she opens Price Management to negotiate consolidated rates. Some media owners counter-offer, some accept directly.

Once negotiations conclude, Priya submits for approval. The agency_acceptance stage requires the agency contact to formally accept the proposal (they receive an email with a magic link). Platform_review is completed by MW commercial. Media_owner_approval is a batch approval since Priya has delegated authority for small deals; larger deals require individual MO sign-off.

Upon approval, Priya has a choice: send to Influence for traditional execution, or create PG/PD deals. She creates PG deals for Malaysia and Singapore (guaranteed via Influence) and an open auction extension for Thailand (via Activate with remaining budget).

**Status Progression**: Draft → Planned → Reviewing → Approved → Active

### 6.5 Campaign Edit After Approval (Requires Re-Approval)

**Persona**: James's automotive campaign was approved last week. Today, the client calls to say they want to change the end date.

**Scenario Flow**:

James opens the approved campaign. Since it's `Approved`, the "Edit" button is disabled. He clicks "Request Change" instead, which opens a change order form.

James modifies the end date from Dec 15 to Dec 22. The system shows: "This change affects the campaign schedule. Re-approval will be required."

James submits the change. The campaign status changes from `Approved` to `Reviewing` with a flag indicating "Change Order Pending." The change goes through the approval workflow again, but only affected stages need to re-approve.

**Key Behavior**: Any change to an approved campaign requires re-approval. There are no automatic thresholds that bypass this.

**Status Progression**: Approved → Reviewing (Change Order) → Approved

### 6.6 Changing Execution Path (Re-Confirmation Workflow)

**Situation**: A user has selected an execution path (Quick Launch or Full Workflow) but later decides they need to change it. This is allowed only for campaigns in Draft or Planned status.

**Scenario 1 - Quick Launch to Full Workflow**:

Sarah launched a campaign as Quick Launch (Open Auction) but the client now wants guaranteed premium placements. She opens the campaign detail page and clicks "Change Path" next to the execution path badge.

The system presents the Execution Path Selection sheet with a warning:

```
┌──────────────────────────────────────────────────────────────────┐
│  SWITCHING TO FULL WORKFLOW                                       │
│                                                                   │
│  Your campaign will be moved from the open auction marketplace   │
│  to the traditional approval workflow. Any active bids will be   │
│  cancelled.                                                       │
│                                                                   │
│  THIS WILL:                                                       │
│  ⚠ Campaign will require approval stages                         │
│  ⚠ Media owner negotiations will be needed                       │
│  ⚠ Timeline will extend to 3-7 business days                     │
│  ⚠ Active auction bids will be cancelled                         │
│                                                                   │
│  [Go Back]                          [Confirm Change]              │
└──────────────────────────────────────────────────────────────────┘
```

Sarah confirms. The campaign status changes from `Active` to `Reviewing` and enters the approval workflow.

**Scenario 2 - Full Workflow to Quick Launch**:

James has a campaign in the approval workflow but needs to go live immediately. He clicks "Change Path" and selects Quick Launch.

The system warns:

```
┌──────────────────────────────────────────────────────────────────┐
│  SWITCHING TO OPEN AUCTION                                        │
│                                                                   │
│  Your campaign will be moved to the open auction marketplace.    │
│  Any existing reservations and negotiations will be reset.       │
│                                                                   │
│  THIS WILL:                                                       │
│  ⚠ Existing reservations will be released                        │
│  ⚠ Price negotiations will be cancelled                          │
│  ⚠ Approval stages will be bypassed                              │
│  ⚠ Guaranteed placements will become flexible                    │
│                                                                   │
│  [Go Back]                          [Confirm Change]              │
└──────────────────────────────────────────────────────────────────┘
```

James confirms. The campaign immediately becomes `Active` via the open auction path.

**Key Behaviors**:

1. **Availability**: The "Change Path" button appears only for campaigns in `Draft` or `Planned` status that already have an execution path set.

2. **Two-Step Confirmation**: Changing paths requires selecting the new path, then confirming after seeing the impact warning.

3. **No Path Change for Active/Approved**: Once a campaign reaches `Approved` or `Active` status, the execution path is locked. Users must cancel and create a new campaign if they need a different path.

4. **Status Updates**: 
   - Quick Launch → Full Workflow: Status becomes `Reviewing`
   - Full Workflow → Quick Launch: Status becomes `Active`

5. **Audit Trail**: All execution path changes are logged in the campaign history with the previous and new path, timestamp, and user who made the change.

---

## 7. Complex Scenarios and Edge Cases

This section covers real-world situations that go beyond the happy path. These scenarios help clarify system behavior when things don't go as planned.

### 7.1 Partial Media Owner Approval - Comprehensive Scenario

**Situation**: An agency creates a campaign with inventory from five media owners. After the campaign passes Agency Acceptance and Platform Review stages, it enters the Media Owner Approval stage. Here's how each media owner responds:

- **Clear Channel** (2 billboards): Approved ✓
- **Lamar Advertising** (1 billboard): Approved ✓
- **JCDecaux** (2 digital screens): Approved ✓
- **OutFront Media** (1 spectacular): Rejected ✗ - Reason: "Dates conflict with Pepsi category exclusivity for Q4"
- **Billboard Corp** (1 digital): Silent ⏳ - No response after 3 days

The agency wants to proceed with whoever has approved and not wait indefinitely.

**What the User Sees**:

The campaign remains in `Reviewing` status with the stage indicator showing `Media Owner Approval`. The progress panel displays:

```
┌──────────────────────────────────────────────────────────────────┐
│  [Reviewing]  •  Media Owner Approval                            │
│                                                                   │
│  APPROVAL STATUS                                                  │
│  ───────────────────────────────────────────────────────────────  │
│  ✓ Approved: 3 media owners (5 inventory items)                  │
│  ✗ Rejected: 1 media owner (1 inventory item)                    │
│  ⏳ Pending: 1 media owner (1 inventory item) - Day 3 of 5       │
│                                                                   │
│  BREAKDOWN BY MEDIA OWNER                                         │
│  ───────────────────────────────────────────────────────────────  │
│  ✓ Clear Channel                                                  │
│    └─ Times Square Digital (approved Dec 2)                      │
│    └─ Broadway Billboard (approved Dec 2)                        │
│                                                                   │
│  ✓ Lamar Advertising                                              │
│    └─ Sunset Strip LED (approved Dec 3)                          │
│                                                                   │
│  ✓ JCDecaux                                                       │
│    └─ Chicago Magnificent Mile #1 (approved Dec 3)               │
│    └─ Chicago Magnificent Mile #2 (approved Dec 3)               │
│                                                                   │
│  ✗ OutFront Media                                                 │
│    └─ Hollywood Spectacular (rejected Dec 3)                     │
│       Reason: "Dates conflict with Pepsi category exclusivity"   │
│                                                                   │
│  ⏳ Billboard Corp                                                 │
│    └─ Miami Beach Digital (pending - Day 3 of 5)                 │
│       Last contact: Notification sent Dec 1                      │
│                                                                   │
│  ───────────────────────────────────────────────────────────────  │
│  WHAT WOULD YOU LIKE TO DO?                                       │
│                                                                   │
│  [Proceed with Approved]  [Wait for Billboard Corp]              │
│  [Request MW Follow-up]   [Revise Rejected Item]                 │
└──────────────────────────────────────────────────────────────────┘
```

**Agency Clicks "Proceed with Approved"**:

The system asks for confirmation:

```
┌──────────────────────────────────────────────────────────────────┐
│  CONFIRM PARTIAL APPROVAL                                         │
│                                                                   │
│  You are choosing to proceed with approved media owners only.    │
│  The following items will be EXCLUDED from your campaign:        │
│                                                                   │
│  ✗ OutFront Media - Hollywood Spectacular                        │
│    Status: Rejected                                              │
│    Reason: Category exclusivity conflict                         │
│                                                                   │
│  ⊘ Billboard Corp - Miami Beach Digital                          │
│    Status: Excluded by buyer (no response received)             │
│                                                                   │
│  BUDGET IMPACT                                                    │
│  ───────────────────────────────────────────────────────────────  │
│  Original budget: $150,000 (7 inventory items)                   │
│  Revised budget:  $118,000 (5 inventory items)                   │
│  Savings: $32,000                                                │
│                                                                   │
│  ☐ I understand that excluded items cannot be added back         │
│    without creating a Change Order                               │
│                                                                   │
│  [Cancel]                          [Confirm and Proceed]         │
└──────────────────────────────────────────────────────────────────┘
```

**After Confirmation**:

1. Campaign status changes from `Reviewing` to `Approved`
2. The 5 approved inventory items change to `booked` status
3. OutFront inventory is marked `declined` and removed from campaign scope
4. Billboard Corp inventory is marked `excluded_by_buyer` and removed from campaign scope
5. Campaign history logs: "Dec 4: Agency proceeded with partial approval. Excluded: OutFront Media (rejected - category conflict), Billboard Corp (no response - excluded by buyer)."
6. The creator can now assign creatives and schedule the go-live date

**Alternative Path - Agency Clicks "Wait for Billboard Corp"**:

The campaign stays in `Reviewing` status. The pending countdown continues. Options available:

- Day 4: Reminder notification sent to Billboard Corp
- Day 5: If still no response, auto-escalation (if configured) or request expires
- Agency can change their mind and click "Proceed with Approved" at any time

**Alternative Path - Agency Clicks "Request MW Follow-up"**:

A request is sent to the MW platform team to reach out to Billboard Corp directly. This is logged in campaign history. The MW team can:
- Contact Billboard Corp sales team via phone/email
- If permitted and strategic, accept on behalf of Billboard Corp after exhausting outreach
- Report back that Billboard Corp is unavailable (agency then decides to proceed or wait)

**Alternative Path - Agency Clicks "Revise Rejected Item"**:

If the agency believes they can address OutFront's rejection reason (date conflict), they can:

1. Click "Revise Rejected Item"
2. Modify the OutFront inventory schedule to avoid the Pepsi exclusivity dates
3. This creates a Change Request specifically for OutFront
4. Campaign status shows: `Reviewing • Media Owner Approval (Change Request Pending)`
5. OutFront receives the revised request and can approve or reject again
6. Approvals from Clear Channel, Lamar, and JCDecaux are preserved (unchanged)

**Key Principles Demonstrated**:

1. **Campaign stays in Reviewing until agency decides**: The partial approval situation doesn't auto-resolve. The agency must consciously choose how to proceed.

2. **Status badge remains clean**: Throughout this process, the status is just `Reviewing`. Progress details are shown separately.

3. **Approved means ready**: Only when the agency clicks "Proceed" does the campaign become `Approved` - and at that point, it's truly ready for execution.

4. **Audit trail is complete**: Every decision is logged with timestamps, user actions, and reasons.

5. **Excluded items are clearly marked**: Items excluded by rejection vs. excluded by buyer choice are distinguished in the history.

### 7.2 Negotiation Deadlock with Timeout

**Situation**: An agency proposes $100,000 for a premium billboard. The media owner counters with $150,000. The agency counters back at $110,000. The media owner holds firm at $145,000. Neither side is budging, and the negotiation timeout (7 days) is approaching.

**System Behavior**:

The inventory item shows status `negotiating` with a visual timeline indicating:
- Day 1: Agency proposed $100,000
- Day 2: Media owner countered $150,000
- Day 3: Agency countered $110,000
- Day 4: Media owner countered $145,000
- Current: Day 6 of 7 - Timeout approaching

At Day 6, the system sends warning notifications to both parties: "Price negotiation for Times Square Billboard expires in 24 hours. If no agreement is reached, the negotiation will close and the inventory will return to available status."

If Day 7 passes without agreement, the inventory status changes from `negotiating` to `expired`. The reservation (if any) is also released. The inventory becomes available for other campaigns.

However, the campaign itself is not affected beyond this one inventory item. Other inventories in the campaign maintain their status. The creator can either remove the expired inventory or restart negotiation (which creates a new 7-day window).

If the MW platform team sees a strategic deal stuck in deadlock, they have the option to intervene. An internal user with appropriate permissions can send a message to both parties suggesting a middle ground, or in extreme cases, can set a "platform suggested price" that both parties can accept or reject.

### 7.3 Concurrent Campaign Conflict - Same Inventory, Overlapping Dates

**Situation**: Two different agencies, Agency A and Agency B, both want the same Times Square billboard for December 15-31. Agency A submits their request at 2:00 PM. Agency B submits at 2:05 PM.

**System Behavior**:

The platform operates on a first-come-first-served basis with explicit visibility into conflicts.

When Agency A requests a hold at 2:00 PM, the inventory shows as "Hold Requested" with Agency A's timestamp. The media owner (Clear Channel) receives a notification.

When Agency B submits at 2:05 PM, the system detects the overlap. Agency B sees a warning: "This inventory has a pending hold request from another buyer for overlapping dates. You can still request a hold, but it will be queued. If the first request is approved, your request will be declined automatically."

Agency B can choose to:
1. Submit anyway (enters a queue position)
2. Adjust dates to avoid overlap
3. Find alternative inventory

If Clear Channel approves Agency A's request, Agency B automatically receives a notification: "Your hold request for Times Square Billboard (Dec 15-31) has been declined because another booking was confirmed for overlapping dates. Would you like to explore alternative inventory?"

If Clear Channel declines Agency A's request, Agency B moves up in the queue and their request is presented to Clear Channel next.

The platform does not conduct bidding wars for traditional inventory. The first valid request has priority. However, if both requests come in within a very short window (say, under 60 seconds) and the media owner hasn't responded yet, the media owner sees both requests and can choose which to accept based on their business relationship, client prestige, or other factors.

### 7.4 Mid-Flight Change Order

**Situation**: James's luxury automotive campaign has been running for two weeks. The client calls with exciting news: they want to add two more billboards in Miami and extend the campaign by one additional week. The campaign is currently `Active` with inventory in `inflight` status.

**System Behavior**:

James opens the active campaign. Since the campaign is in `Active` status, direct editing is disabled. He sees a prominent "Request Change" button instead of "Edit."

Clicking "Request Change" opens a change order form. James can:
- Add inventory (he adds two Miami billboards)
- Extend dates (he changes end date from Dec 15 to Dec 22)
- Adjust budget (system auto-calculates new total: $535,000 from $500,000)

The system shows an impact analysis before James submits:

```
CHANGE ORDER SUMMARY
--------------------
Current campaign: 3 inventories, $500,000, ending Dec 15
Proposed changes:
  + Add: Miami Beach Digital ($18,000)
  + Add: Miami Downtown LED ($17,000)
  + Extend: 1 additional week (+$12,000 prorated)
  
New total: 5 inventories, $547,000, ending Dec 22

Impact on current delivery:
  - Current SOV: 23%
  - Projected SOV with changes: 28%
  - No disruption to existing placements
```

James confirms the change order. The system now triggers a mini approval workflow, but only for the changes:

1. **Agency acceptance**: James's manager reviews the additional $47,000 spend
2. **Platform review**: MW commercial validates the new inventory pricing
3. **Media owner approval**: Only the NEW media owners (Miami vendors) need to approve. Clear Channel, Lamar, and OutFront are not involved since their portion is unchanged.

While the change order is in review, the original campaign continues running normally. The status shows "Active (Change Order Pending)".

Once all approvals come through, the changes are merged into the live campaign. Miami inventory starts delivering. The extended dates take effect. History log shows the complete audit trail.

If the change order is rejected (say, the Miami media owner declines), James is notified. The original campaign continues unaffected. He can modify and resubmit the change order or cancel it.

### 7.5 Creative Rejection After Campaign Approval

**Situation**: A campaign is fully approved and ready to go live on Monday. However, the creative (advertisement artwork) needs landlord approval for one of the billboards (a building owner requires sign-off on content displayed on their property). The landlord reviews the creative on Friday and rejects it: "Image shows alcohol consumption, which violates our building policy."

**System Behavior**:

Creative approval is handled separately from campaign approval, but the two are linked. When a creative is rejected, the system takes the following approach:

The specific inventory item where the creative was rejected changes its creative status to `creative_rejected`. The inventory booking status remains `booked` (the placement is still reserved), but it cannot go `inflight` until creative is resolved.

The campaign cannot transition to `Active` if any inventory has unresolved creative issues. The creator sees a pre-flight checklist:

```
PRE-FLIGHT CHECKLIST
--------------------
✓ Campaign approved
✓ Budget confirmed
✓ All inventories booked
✗ Creative status: 1 item needs attention
  - Sunset Blvd Billboard: Creative rejected by landlord
    Reason: "Image shows alcohol consumption"
    Action required: Upload alternative creative

Campaign cannot go live until all items pass pre-flight check.
```

The campaign status remains `Approved` but with a visual indicator showing it's blocked from going live.

The creator has options:
1. Upload new creative for that specific inventory and resubmit for landlord approval
2. Remove that inventory from the campaign and proceed with remaining placements
3. Request an extension of the booking while creative is revised

If the creator uploads new creative and the landlord approves, the pre-flight check passes and the campaign can go live. The original Monday start date may need adjustment if the creative review caused delays.

Importantly, the campaign approval (commercial terms, pricing, dates) does not need to be re-done. Only the creative for that specific inventory needs landlord sign-off.

### 7.6 Multi-Country Campaign with Different Approval Rules

**Situation**: Priya is managing an APAC campaign spanning Malaysia, Singapore, and Indonesia. In Malaysia, certain billboard locations near religious sites require landlord approval. Singapore has no such requirement. Indonesia requires government media council approval for campaigns over a certain size.

**System Behavior**:

The platform supports country-specific and even location-specific approval rules configured in Account Management.

When Priya selects inventory, the system automatically applies the relevant rules:

```
APPROVAL REQUIREMENTS BY REGION
-------------------------------
Malaysia (3 inventories):
  - Standard: Agency → Platform → Media Owner
  - 1 inventory near religious site: +Landlord approval required

Singapore (4 inventories):
  - Standard: Agency → Platform → Media Owner
  - No additional requirements

Indonesia (2 inventories):
  - Standard: Agency → Platform → Media Owner
  - Campaign budget > $50K: +Government media council approval
  - Your campaign qualifies (budget $80K for Indonesia portion)
```

Priya sees this upfront during planning so she can set expectations with the client about timeline.

When the campaign enters `Reviewing` status, the approval workflow branches appropriately:

1. Agency acceptance (global - one approval covers all countries)
2. Platform review (global - MW commercial reviews the full deal)
3. Media owner approval (per media owner, may be different companies per country)
4. Landlord approval (only for the 1 Malaysian inventory that requires it)
5. Government council approval (only for Indonesian portion)

Stages 4 and 5 run in parallel where possible. The Indonesian government approval can proceed while waiting for the Malaysian landlord.

The campaign only reaches `Approved` status when ALL applicable approval stages are completed. Priya can see a detailed progress view showing which countries/inventories are approved and which are pending.

### 7.7 Reseller Acting on Behalf of Media Owner

**Situation**: AdVantage Media is a reseller that represents inventory from 12 small media owners who don't have their own sales teams. Tom works at AdVantage and is creating a campaign that uses inventory from 3 of those media owners.

**System Behavior**:

Resellers are configured in Account Management with explicit representation agreements. Tom's company (AdVantage) is linked to specific media owner companies with defined permissions.

When Tom creates a campaign using inventory from represented media owners, the system recognizes this relationship.

For reservation: Tom can reserve inventory on behalf of the media owners he represents, similar to how a media owner employee would. This is because AdVantage has been granted reservation rights by those media owners.

For pricing: Tom can negotiate prices within the bounds set by each media owner. If Media Owner A has set a floor price of $10,000, Tom cannot accept an offer below that. He can counter-offer, but the final accepted price must meet the media owner's configured minimums.

For approval: The media owner approval stage has two modes depending on configuration:
- **Delegated approval**: The media owner has fully delegated approval authority to AdVantage. Tom can approve on their behalf.
- **Notification approval**: The media owner wants to be notified and give final sign-off. Tom's acceptance triggers a notification to the media owner, who confirms or overrides within 48 hours.

In the campaign, this is transparent to the buyer. The agency sees "Sunset Boulevard Billboard - AdVantage Media (representing City Lights Outdoor)" and interacts with Tom. The approval history shows both Tom's action and, if applicable, the underlying media owner's confirmation.

### 7.8 Agency Brand Conflict and Exclusivity

**Situation**: The agency MediaHub manages accounts for both Coca-Cola and Pepsi (competitors). Sarah at MediaHub is creating a campaign for Coca-Cola. One of the billboards she wants has an existing booking from another MediaHub planner for Pepsi running during overlapping dates.

**System Behavior**:

The platform tracks brand-advertiser relationships and competitive categories. When Sarah selects inventory, the system checks for conflicts:

```
CONFLICT DETECTED
-----------------
Sunset Boulevard Digital
Dates: Dec 1-31

Your request: Coca-Cola Holiday Campaign (Dec 15-31)
Existing booking: Pepsi Winter Refresh (Dec 1-20)

These brands are flagged as competitors in category "Soft Drinks"
Your agency (MediaHub) manages both accounts.

Options:
1. Proceed anyway (dates overlap by 5 days: Dec 15-20)
2. Adjust dates to start Dec 21 (no overlap)
3. Select alternative inventory
```

If Sarah proceeds with overlapping dates, the system requires acknowledgment: "You are booking competing brands on the same inventory with overlapping dates. This will be logged for compliance. Please confirm this is intentional and approved by account leadership."

This is primarily a notification and audit mechanism, not a hard block. Some agencies may have legitimate reasons for such bookings (different markets, different dayparts, client awareness of sharing, etc.).

Additionally, the media owner may have their own exclusivity rules. If Clear Channel has granted Pepsi category exclusivity on that billboard for December, the system will block Coca-Cola regardless of what the agency wants: "This inventory has category exclusivity for Soft Drinks granted to another advertiser during your requested dates. Please select alternative inventory or different dates."

The conflict detection runs at both the agency level (same agency, competing brands) and media owner level (category exclusivity agreements).

### 7.9 Approval Timeout and Escalation

**Situation**: James submitted a campaign for approval 5 days ago. It's stuck at the agency_acceptance stage because his manager, who needs to approve, is on vacation and unresponsive.

**System Behavior**:

Each approval stage has configurable timeout rules set in Account Management. The default is:
- Warning at 3 days
- Escalation at 5 days
- Auto-action at 7 days

Day 3: The system sends a reminder to James's manager: "Campaign 'Automotive Launch' is awaiting your approval. Submitted 3 days ago."

Day 5: The system escalates. It looks up the approval hierarchy: if James's manager has a backup approver configured (common during vacation), the campaign is reassigned to the backup. If no backup is configured, it escalates to the next level up (department head or agency admin).

The campaign now shows: "Escalated to [Backup Approver Name] due to timeout."

Day 7 (if still no response): The system takes the configured auto-action:
- **Auto-approve**: For low-value campaigns below a threshold, the system may auto-approve to prevent bottlenecks
- **Auto-reject**: For campaigns where approval is critical, no response = rejection, and the creator is notified to resubmit
- **Notify admin**: Alert is sent to agency admin to manually intervene

The specific behavior is configurable per company. Some agencies prefer strict approval (auto-reject), others prefer efficiency (auto-approve for small deals), others want manual intervention (notify admin).

James sees the escalation in the campaign timeline: "Day 5: Escalated from [Manager] to [Backup] due to approval timeout."

### 7.10 Budget Overspend During Negotiation

**Situation**: An agency creates a campaign with a budget of $100,000 and adds 5 inventories. During negotiation, each media owner counters with prices higher than the rate card. By the time all prices are agreed, the total cost is $115,000 - 15% over the original budget.

**System Behavior**:

The platform tracks budget in real-time during negotiation. As each price is agreed, the "committed cost" updates.

When the committed cost exceeds the campaign budget, the system shows a warning but does not block the process:

```
BUDGET ALERT
------------
Campaign budget: $100,000
Current committed cost: $115,000
Over budget by: $15,000 (15%)

You can:
1. Increase campaign budget to $115,000
2. Remove some inventory to reduce cost
3. Renegotiate pricing on some items
4. Proceed over budget (requires approval flag)
```

If the agency chooses to proceed over budget, this is flagged for the approval workflow. When the campaign goes to agency_acceptance, the manager sees: "This campaign exceeds its original budget by 15%. Please confirm the budget increase is authorized."

The platform does not prevent over-budget campaigns because there are legitimate business reasons: client verbally approved the increase, agency is absorbing the difference, or the rate card was outdated. However, it ensures visibility so approvers can make informed decisions.

Some companies may configure hard limits: "Block campaigns more than 20% over budget" - in that case, the creator must adjust before proceeding.

### 7.11 Reservation Silence - Agency Request Without Media Owner Response

**Situation**: An agency requests a hold on premium inventory. The media owner does not respond for 48 hours. What happens?

**System Behavior**:

When an agency requests a hold, the media owner receives immediate notification. The inventory shows as "Hold Requested" (not fully reserved).

If the media owner does not respond within 48 hours, the request expires automatically. The agency is notified: "Your hold request for [Inventory Name] has expired without media owner response. The inventory is now available for other requests."

This is different from media owner silence AFTER a reservation is confirmed. If a reservation exists and the media owner is silent on a negotiation or approval, MW platform team can intervene if permitted.

Specifically, the platform has a configuration option: "Allow MW to accept on behalf of media owner if no response within X days." If enabled, and if a media owner is unresponsive on an approval decision for (say) 5 days, the MW internal team can accept on behalf to prevent deals from dying due to inaction.

This override is logged clearly in the campaign history: "Approved by [MW User Name] on behalf of [Media Owner Company] due to approval timeout (5 days without response)."

However, this is a safety net, not a default. MW only exercises this option for strategic deals after attempting direct outreach to the media owner.

### 7.12 Complete Approval Workflow Walkthrough with Status Display

**Situation**: James, an agency account director, creates a campaign and walks through the complete approval workflow. This scenario illustrates exactly what the status display shows at each step.

**Step 1: Campaign Created**

James completes the campaign wizard. 

```
Status Display: [Draft]
No stage indicator (not in approval workflow)
```

**Step 2: Campaign Finalized**

James clicks "Finalize Plan" and selects "Full Workflow (Traditional)".

```
Status Display: [Planned]
No stage indicator (not in approval workflow yet)
Primary action: [Submit for Approval]
```

**Step 3: Submitted for Approval - Agency Stage**

James clicks "Submit for Approval". His manager needs to approve.

```
Status Display: [Reviewing]  •  Agency Acceptance
Progress: Awaiting approval from Sarah Chen (Manager)
Submitted: 2 hours ago
```

**Step 4: Self-Approval Scenario (Alternative)**

If James IS the designated approver (e.g., he's a senior director with self-approval rights), the system recognizes this:

```
Status Display: [Reviewing]  •  Agency Acceptance
Progress: You are the designated approver for this stage
[Approve] [Request Changes] [Reject]
```

James clicks "Approve" and it auto-advances to Platform Review.

**Step 5: Manager Approves - Platform Review Stage**

Sarah Chen (James's manager) approves. Campaign advances to Platform Review.

```
Status Display: [Reviewing]  •  Platform Review
Progress: Awaiting approval from MW Commercial Team
Agency Acceptance: Completed ✓ (Sarah Chen, Dec 2)
```

**Step 6: MW Approves - Media Owner Stage**

MW commercial team approves. Campaign advances to Media Owner Approval.

```
Status Display: [Reviewing]  •  Media Owner Approval
Progress: Awaiting response from 3 media owners
  ⏳ Clear Channel (2 items) - Pending
  ⏳ Lamar Advertising (1 item) - Pending
  ⏳ JCDecaux (2 items) - Pending

Previous Stages:
  ✓ Agency Acceptance (Sarah Chen, Dec 2)
  ✓ Platform Review (MW Commercial, Dec 3)
```

**Step 7: Media Owners Respond**

Over the next 2 days, media owners respond.

```
Status Display: [Reviewing]  •  Media Owner Approval
Progress: 2 of 3 media owners responded
  ✓ Clear Channel (2 items) - Approved
  ✓ Lamar Advertising (1 item) - Approved
  ⏳ JCDecaux (2 items) - Pending (Day 2 of 5)

[Wait for JCDecaux] [Proceed with Approved]
```

**Step 8: All Media Owners Approve**

JCDecaux approves on Day 3.

```
Status Display: [Approved]
All approval stages completed ✓

Pre-flight Checklist:
  ✓ Campaign approved
  ✓ Budget confirmed ($250,000)
  ☐ Creative assignment (0 of 5 items)
  ☐ Go-live date scheduled

[Assign Creative] [Schedule Go-Live]
```

**Step 9: Campaign Goes Live**

James assigns creatives, schedules go-live for Monday, Dec 15.

```
Status Display: [Approved]  •  Scheduled for Dec 15
All pre-flight checks passed ✓

[Go Live Now] [Edit Schedule]
```

On Dec 15 at 00:00, status automatically changes:

```
Status Display: [Active]
Campaign is live and delivering

Delivery Progress:
  5 of 5 items in-flight
  Impressions: 45,230 / 500,000 (9%)
  Pacing: On track ✓
```

**Key Observations**:

1. **Status badge is always one word**: Draft, Planned, Reviewing, Approved, Active
2. **Stage indicator appears only during Reviewing**: Shows exactly where in the workflow
3. **Progress details are contextual**: Different information shown at each stage
4. **Previous stages shown for audit**: User can see history of approvals
5. **Actions change based on state**: Only relevant actions are shown

### 7.13 Detailed Negotiation Lifecycle - Price Counter-Offer Patterns

**Situation**: An agency selects 3 inventories from Clear Channel and initiates price negotiation on all of them simultaneously. Each inventory has a different rate card price and negotiation outcome.

**What the User Sees in Price Management**:

```
┌──────────────────────────────────────────────────────────────────┐
│  PRICE MANAGEMENT - Holiday Campaign 2024                        │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Status Legend:                                              │ │
│  │  ● Rate Card (original)  ○ Proposed (by you)                │ │
│  │  ◐ Counter (by MO)       ✓ Accepted  ✗ Declined             │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                   │
│  INVENTORY                      RATE CARD    YOUR PRICE   STATUS │
│  ─────────────────────────────────────────────────────────────── │
│  Times Square Digital            $150,000    $130,000     ◐      │
│  └─ Counter-offer received: $142,000 (Clear Channel, Dec 3)     │
│     [Accept $142,000] [Counter] [Decline]                        │
│                                                                   │
│  Broadway Billboard              $80,000     $70,000      ○      │
│  └─ Your proposal sent: Awaiting response (Day 2 of 7)          │
│     [Edit Proposal] [Withdraw]                                   │
│                                                                   │
│  Sunset Strip LED                $120,000    $110,000     ✓      │
│  └─ Price agreed: $115,000 (Dec 2)                              │
│     Proposed by: You ($110,000) → Counter: $118,000 → Final     │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

**Negotiation State Transitions**:

| Current State | Agency Actions | Media Owner Actions | Result |
|---------------|----------------|---------------------|--------|
| `pending` (no negotiation) | "Propose Price" | "Set Custom Price" | → `negotiating` |
| `negotiating` (agency proposed) | Wait / Withdraw | Accept / Counter / Decline | → varies |
| `negotiating` (MO countered) | Accept / Counter / Decline | Wait | → varies |
| `price_agreed` | (locked) | (locked) | Ready for approval |
| `declined` | Restart / Remove | — | Either retry or drop item |
| `expired` | Restart / Remove | — | 7-day timeout reached |

**Detailed Counter-Offer Scenario**:

1. Agency proposes $130,000 for Times Square (rate card: $150,000)
   - Status: `negotiating` (agency turn complete, waiting for MO)
   
2. Clear Channel counters at $142,000 with comment: "Best rate for this period"
   - Status: `negotiating` (MO turn complete, waiting for agency)
   - Agency sees counter-offer with MO's comment
   
3. Agency has three choices:
   - **Accept $142,000**: Status → `price_agreed`, both parties locked
   - **Counter at $138,000**: Status remains `negotiating`, back to MO
   - **Decline**: Status → `declined`, negotiation ends

4. If agency counters at $138,000, Clear Channel can:
   - **Accept**: Final price = $138,000, status → `price_agreed`
   - **Counter again**: New amount proposed (limited rounds - max 5 by default)
   - **Decline**: Status → `declined`

**Maximum Negotiation Rounds**:

By default, negotiations allow up to 5 counter-offers (configurable in Account Management). After 5 rounds, the system prompts: "Maximum negotiation rounds reached. Please accept current offer, decline, or request MW mediation."

**Bulk Negotiation Actions**:

The agency can select multiple inventories and apply bulk actions:
- "Accept All Counters" - Accept all pending counter-offers at once
- "Apply X% Discount" - Counter all selected with X% off rate card
- "Withdraw All Proposals" - Cancel all pending proposals

### 7.14 Reservation Extension and Expiry Patterns

**Situation**: An agency has inventory in `reserved` status with different expiry scenarios.

**What the User Sees in Workbench**:

```
┌──────────────────────────────────────────────────────────────────┐
│  RESERVATIONS - Holiday Campaign 2024                            │
│                                                                   │
│  INVENTORY                  STATUS        EXPIRES        ACTIONS │
│  ─────────────────────────────────────────────────────────────── │
│  Times Square Digital       Reserved      Dec 8 (3 days)   ⚠️   │
│  └─ Held by: Clear Channel for MediaHub Agency                  │
│     [Extend +7 days] [Release] [Convert to Booking]             │
│                                                                   │
│  Broadway Billboard         Reserved      Dec 15 (10 days)  ✓   │
│  └─ Held by: Clear Channel for MediaHub Agency                  │
│     [Extend] [Release] [Convert to Booking]                      │
│                                                                   │
│  Sunset Strip LED           Hold Requested  —               ⏳   │
│  └─ Requested Dec 3, awaiting Lamar response                    │
│     [Cancel Request]                                             │
│                                                                   │
│  Miami Beach Digital        Expired        Dec 1            ✗    │
│  └─ Reservation expired without conversion                       │
│     [Request New Hold] [Remove from Campaign]                    │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

**Reservation State Transitions**:

| Current State | User Action | Media Owner Action | System Event | Result |
|---------------|-------------|-------------------|--------------|--------|
| `pending` | Request Hold | — | — | → `hold_requested` |
| `hold_requested` | Cancel | Accept/Decline | 48hr timeout | → `reserved`/`pending`/`expired` |
| `reserved` | Extend | — | Expiry date reached | → `reserved` (new date) or `expired` |
| `reserved` | Release | — | — | → `released` (available again) |
| `reserved` | Convert to Booking | — | — | → `booked` (if price agreed) |
| `expired` | Request New Hold | — | — | → `hold_requested` |

**Extension Rules**:

1. **Before Expiry**: Extension allowed (default max 2 extensions, 7 days each)
2. **After Expiry**: Cannot extend - must request new hold
3. **During Negotiation**: Reservation auto-extends while negotiation is active
4. **Approval Submitted**: Reservation auto-extends while in approval workflow

**Extension UI**:

```
┌──────────────────────────────────────────────────────────────────┐
│  EXTEND RESERVATION                                              │
│                                                                   │
│  Times Square Digital                                            │
│  Current expiry: Dec 8, 2024                                     │
│                                                                   │
│  Extension options:                                              │
│  ○ 3 days (Dec 11)                                              │
│  ● 7 days (Dec 15) [recommended]                                │
│  ○ 14 days (Dec 22)                                             │
│  ○ Custom date: [__________]                                    │
│                                                                   │
│  Reason (optional):                                              │
│  [Client needs additional time for budget approval_____]        │
│                                                                   │
│  Note: This is extension 1 of 2 allowed. After that, you must   │
│  convert to booking or release the reservation.                  │
│                                                                   │
│  [Cancel]                                    [Extend Reservation] │
└──────────────────────────────────────────────────────────────────┘
```

### 7.15 Hold Request Acceptance and Decline Patterns

**Situation**: An agency requests holds on 5 inventories from 3 different media owners. Each media owner responds differently.

**What the Agency Sees**:

```
┌──────────────────────────────────────────────────────────────────┐
│  HOLD REQUESTS - Holiday Campaign 2024                           │
│                                                                   │
│  Summary: 2 Accepted, 1 Declined, 2 Pending                      │
│                                                                   │
│  CLEAR CHANNEL (2 items)                                         │
│  ─────────────────────────────────────────────────────────────── │
│  ✓ Times Square Digital - Accepted (Dec 3, 2:15 PM)             │
│    └─ Reserved until Dec 17 (14 days)                           │
│  ✓ Broadway Billboard - Accepted (Dec 3, 2:15 PM)               │
│    └─ Reserved until Dec 17 (14 days)                           │
│                                                                   │
│  LAMAR ADVERTISING (1 item)                                      │
│  ─────────────────────────────────────────────────────────────── │
│  ⏳ Sunset Strip LED - Pending (requested Dec 3, 10:00 AM)       │
│    └─ Day 2 of 2 before auto-expire                             │
│    └─ [Send Reminder] [Cancel Request]                          │
│                                                                   │
│  JCDECAUX (2 items)                                              │
│  ─────────────────────────────────────────────────────────────── │
│  ✗ Chicago Magnificent Mile #1 - Declined (Dec 3, 4:00 PM)      │
│    └─ Reason: "Already reserved for another client"             │
│    └─ [Find Alternative] [Remove from Campaign]                 │
│  ⏳ Chicago Magnificent Mile #2 - Pending (requested Dec 3)      │
│    └─ Day 1 of 2 before auto-expire                             │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

**What the Media Owner Sees (Clear Channel)**:

```
┌──────────────────────────────────────────────────────────────────┐
│  INCOMING HOLD REQUESTS                                          │
│                                                                   │
│  2 new requests from MediaHub Agency                             │
│  Campaign: Holiday Campaign 2024 (Coca-Cola)                     │
│  Requested by: Sarah Chen                                        │
│  Dates: Dec 15-31, 2024                                         │
│                                                                   │
│  ☐ Times Square Digital                                         │
│    Rate Card: $150,000 | Availability: ✓ Clear                  │
│                                                                   │
│  ☐ Broadway Billboard                                            │
│    Rate Card: $80,000 | Availability: ✓ Clear                   │
│                                                                   │
│  ─────────────────────────────────────────────────────────────── │
│  Hold Duration: [14 days ▼]                                     │
│                                                                   │
│  [Decline Selected]  [Accept Selected]  [Accept All]            │
│                                                                   │
│  Note: Accepting creates a reservation. The agency can then     │
│  proceed to price negotiation or submit for approval.           │
└──────────────────────────────────────────────────────────────────┘
```

**Decline with Alternative Suggestion**:

When a media owner declines a hold request, they can optionally suggest alternatives:

```
┌──────────────────────────────────────────────────────────────────┐
│  DECLINE HOLD REQUEST                                            │
│                                                                   │
│  Chicago Magnificent Mile #1                                     │
│  Requested dates: Dec 15-31                                      │
│                                                                   │
│  Reason for decline:                                             │
│  ● Already reserved for another client                          │
│  ○ Maintenance scheduled                                        │
│  ○ Pricing below minimum                                        │
│  ○ Other: [__________]                                          │
│                                                                   │
│  ☐ Suggest alternative inventory:                               │
│    [Chicago State Street Digital ▼] - Similar location          │
│    Available: Dec 15-31 | Rate: $42,000                         │
│                                                                   │
│  ☐ Suggest alternative dates:                                   │
│    Jan 5-20, 2025 - Same inventory available                    │
│                                                                   │
│  [Cancel]                              [Decline with Suggestion] │
└──────────────────────────────────────────────────────────────────┘
```

**Key Behaviors**:

1. **48-Hour Auto-Expire**: If media owner doesn't respond within 48 hours, hold request expires automatically
2. **Partial Acceptance**: Media owner can accept some and decline others in bulk
3. **Alternative Suggestions**: Declined requests can include suggestions (agency notified)
4. **Priority Queue**: If multiple agencies request the same inventory, first request has priority
5. **Batch Processing**: Media owners can process multiple requests from same agency together

---

## 8. Campaign Workbench - Unified Interface

The Campaign Workbench replaces the separate Price Management, Reservations, and Approval pages with a single unified interface. This addresses the core user confusion by presenting all relevant information and actions in context.

### 8.1 Layout

The workbench has three sections:

**Header Section**: Campaign name, status badge, execution path indicator (Traditional/Open Auction/Hybrid with dual badge for hybrid), and primary action button (changes based on state).

**Inventory Table Section**: All inventory items with their individual statuses, reservation expiry, pricing state, and approval state shown in columns. Bulk selection enables batch operations.

**Progress Section**: Approval workflow progress (when in Reviewing state), or next steps guidance (when in other states).

### 8.2 State-Dependent Views

When campaign is `Planned`:
- Primary action: "Choose Execution Path" or if already chosen, "Submit for Approval" (traditional) or "Launch" (open auction)
- Inventory table shows reservation and pricing controls
- Progress section shows guidance: "Reserve your inventories, negotiate prices, then submit for approval"

When campaign is `Reviewing`:
- Primary action depends on user role: "Approve" (if user is current approver), "View Progress" (if waiting on others)
- Inventory table shows read-only status; can expand to see negotiation history
- Progress section shows approval timeline with current stage highlighted

When campaign is `Approved`:
- Primary action: "Go Live" or "Schedule Launch"
- Inventory table shows creative assignment status
- Progress section shows pre-flight checklist: creative assigned (yes/no), tracking configured (yes/no), budget confirmed (yes/no)

When campaign is `Active`:
- Primary action: "View Performance" or "Pause Campaign"
- Inventory table shows delivery metrics: impressions delivered, % of goal, pacing
- Progress section shows campaign health indicators

### 8.3 Inventory Row Actions

Each inventory row in the table can be expanded to show:
- Reservation details (holder, expiry date, hold type)
- Pricing history (original rate, offers, counters, final price)
- Approval status for this specific inventory
- Creative assignment status
- Schedule details with hour grid visualization

Row-level actions (available based on state):
- Reserve / Release / Extend Reservation
- Propose Price / Accept Counter / Decline
- Assign Creative / Preview Creative
- View Details / View on Map

---

## 9. Actions Menu Reorganization

The campaign actions menu is reorganized into tiers based on frequency of use and contextual relevance.

### 9.1 Primary Actions (Visible as Buttons)

These appear as buttons in the campaign header, not in a dropdown. Only one or two primary actions show at a time based on campaign status.

| Campaign Status | Primary Action(s) |
|-----------------|-------------------|
| Draft | Finalize Plan |
| Planned (no path chosen) | Quick Launch, Start Workflow |
| Planned (path chosen) | Submit for Approval (trad) or Launch Now (open) |
| Reviewing | Approve / View Progress (role-dependent) |
| Approved | Go Live |
| Active | Pause, View Performance |
| Completed | View Report |

### 9.2 Secondary Actions (First Dropdown Section)

These are common operations that users might need but aren't the immediate next step.

- Edit Campaign (if editable; otherwise "Request Change")
- Workbench (unified reserve/price/approve interface)
- Assign Creative
- View Media Plan

### 9.3 Tertiary Actions (Second Dropdown Section)

Less frequent operations.

- History / Audit Log
- Duplicate Campaign
- Share / Export
- Download (PDF, Excel)

### 9.4 Destructive Actions (Separated at Bottom)

Always at the bottom with visual separation.

- Archive Campaign
- Cancel Campaign
- Delete Campaign (only for Draft status)

---

## 10. Reservation Rules by User Type

### 10.1 Media Owner Users

Media owners can directly reserve their own inventory. This is a "hard hold" that immediately blocks availability for other users.

When a media owner sales rep creates a reservation:
- Inventory calendar updates instantly
- Other sales reps at the same company see the hold
- Expiry date is mandatory (default 14 days, max 30 days)
- Can extend reservation before expiry
- Can convert to booking without external approval

### 10.2 Agency and Advertiser Users

Agencies and advertisers can request a reservation, but it requires media owner confirmation. This is a "soft hold" that notifies the media owner but doesn't guarantee availability.

When an agency requests a hold:
- Notification sent to media owner sales team
- Inventory shows as "Hold Requested" (not fully reserved)
- Media owner can accept (converts to hard hold) or decline
- If no response within 48 hours, request expires
- Once accepted, same rules as media owner reservation apply

### 10.3 Internal (MW) Users

Internal MW users can reserve on behalf of either party depending on context. If managing a media owner's inventory, they act as media owner. If facilitating an agency deal, they can request holds.

Additionally, internal users with special permissions can create "priority holds" that override standard availability logic (used for strategic accounts).

---

## 11. Approval Workflow Configuration

### 11.1 Stage Configuration

The approval workflow stages are configured at the company level in Account Management. Default configuration:

1. **agency_acceptance**: Required when campaign is created by agency user or on behalf of agency. Skipped for internal or media owner originated campaigns.

2. **platform_review**: Required when the agency has a partner relationship with MW. Skipped if direct media owner relationship. This is when the MW commercial team validates deal structure, pricing, and compliance.

3. **media_owner_approval**: Always required for traditional workflow. Each media owner with inventory in the campaign must approve their portion.

Administrators can customize:
- Whether stages are required or optional
- Timeout durations before auto-escalation
- Whether self-approval is allowed (creator can approve their own stage) - Note: System follows whatever hierarchy is set up; there is no automatic self-approval for small deals
- Parallel vs sequential processing for media owner stage

### 11.2 Skip Approval Configuration

At times, the full approval workflow is not required. Companies can configure "skip approval" rules in Account Management to streamline operations for specific scenarios.

**When Skip Approval Applies**:

| Scenario | What Gets Skipped | Configuration |
|----------|-------------------|---------------|
| Media Owner creating own campaign | Agency + Platform stages | Auto-detected |
| Internal user creating for partner | Agency stage (if no agency) | Auto-detected |
| Same-company booking | Platform review | Configurable |
| Pre-negotiated rate card pricing | Price acceptance step | Configurable |
| Trusted agency relationships | Platform review | Configurable per agency |

**UI for Skip Approval**:

When skip rules apply, the user sees a streamlined submission flow:

```
┌──────────────────────────────────────────────────────────────────┐
│  SUBMIT FOR APPROVAL                                             │
│                                                                   │
│  Based on your company settings, some approval stages will be   │
│  automatically skipped:                                          │
│                                                                   │
│  ✓ Agency Acceptance - SKIPPED (you are the media owner)        │
│  ✓ Platform Review - SKIPPED (internal booking)                 │
│  ○ Media Owner Approval - REQUIRED                               │
│                                                                   │
│  ℹ️ This campaign will go directly to your inventory team for   │
│  confirmation.                                                   │
│                                                                   │
│  [Cancel]                                        [Submit]        │
└──────────────────────────────────────────────────────────────────┘
```

**Direct Confirmation Option**:

For authorized users (typically media owner admins or internal users with elevated permissions), a "Confirm Directly" option bypasses all approval stages:

```
┌──────────────────────────────────────────────────────────────────┐
│  EXECUTION OPTIONS                                               │
│                                                                   │
│  ○ Submit for Approval                                          │
│    Standard workflow with applicable stages                      │
│                                                                   │
│  ● Confirm Directly                                             │
│    Skip all approval stages (authorized users only)             │
│                                                                   │
│  ⚠️ Direct confirmation is logged for audit purposes.           │
│                                                                   │
│  [Cancel]                                  [Confirm Booking]     │
└──────────────────────────────────────────────────────────────────┘
```

**Audit Trail for Skipped Approvals**:

When approvals are skipped, the campaign history records:
- Which stages were skipped
- Why (auto-detected reason or user choice)
- Who authorized the skip (if manual)
- Timestamp

Example history entry: "Dec 5: Agency Acceptance and Platform Review skipped (media owner internal booking). Submitted directly by Mike Chen for Media Owner Approval."

**Configuration in Account Management**:

```
APPROVAL WORKFLOW SETTINGS
─────────────────────────────────────────────────────────────────

Skip Rules:
☑ Skip Agency Acceptance for internal campaigns
☑ Skip Platform Review for same-company bookings
☐ Skip Platform Review for trusted agencies (configure list below)
☐ Allow direct confirmation for admins

Trusted Agencies (Platform Review skip):
  + Add agency...
  • MediaHub Agency ✕
  • Global Media Partners ✕

Direct Confirmation Users:
  + Add user...
  • Jennifer Park (Admin) ✕
  • Mike Chen (Senior Sales) ✕
```

### 11.3 Approval Behavior by Role

| Approver | Can See | Can Do | Notifications |
|----------|---------|--------|---------------|
| Creator | Everything | Edit until submitted; Comment always | On comment, rejection |
| Manager (same company) | Campaign details, pricing, history | Approve, Reject, Request Changes | On submission |
| Platform (MW) | Commercial terms, fees, compliance flags | Approve, Reject, Comment | On stage activation |
| Media Owner | Own inventory only; not other MO inventory | Approve, Reject own inventory | On stage activation |

### 11.4 Rejection Handling

A rejection at any stage terminates the current approval cycle. The campaign status becomes `Reviewing (Rejected)` with the rejection reason prominently displayed.

The creator can:
1. **Revise and Resubmit**: Make changes to address rejection reason, then resubmit. Previous approvals may need re-confirmation depending on what changed. Any change requires re-approval (no thresholds).
2. **Duplicate and Modify**: Create a copy of the campaign with modifications, starting fresh.
3. **Archive**: Accept the rejection and archive the campaign.

Media owner rejection of specific inventory does not necessarily reject the entire campaign. The creator can choose to remove that inventory and proceed with remaining items.

---

## 12. Integration with External Systems

### 12.1 Influence (adserver.movingwalls.com)

Traditional/guaranteed campaigns are executed via Influence. Upon going live:
- Campaign and booking details sync to Influence
- Creative assets transfer automatically
- Delivery tracking begins
- Proof of play/display reports generate

Users can view Influence-specific controls from a link in the workbench but don't need to access Influence directly for basic operations.

### 12.2 Activate (activate.movingwalls.com)

Open auction campaigns execute via the Activate DSP. Upon launch:
- Campaign targeting and budget transfer to Activate
- Real-time bidding begins
- Impression tracking via Activate's reporting
- Performance data syncs back to Planner for unified view

### 12.3 PG/PD Deal Creation

Media owners can create Programmatic Guaranteed (PG) or Programmatic Direct (PD) deals from approved campaigns. This creates deal IDs that buyers can target in their DSPs.

Deal creation is done from the workbench after campaign approval. The deal appears in Influence's deal management and can be discovered by connected DSPs.

---

## 13. Resolved Design Decisions

The following items were discussed and resolved:

1. **Agency Reservation Workflow**: Media owner acceptance is required. Silent non-response does not convert to approval. If both parties are silent and a strategic deal is at risk, MW internal team can accept on behalf of media owner if this permission is enabled in Account Management.

2. **Hybrid Campaign Status Display**: Show dual badge when a campaign uses both traditional and open auction execution methods.

3. **Change Order Threshold**: Any change to an approved or active campaign requires re-approval. There are no automatic thresholds that bypass approval.

4. **Self-Approval for Small Deals**: Not allowed. The system follows whatever approval hierarchy is configured in Account Management, regardless of deal size. However, if the user IS the designated approver for a stage, they can approve directly without waiting.

5. **Status Display Model**: Campaign status badge is always a single word (Draft, Planned, Reviewing, Approved, Active, etc.). The approval stage and progress are displayed separately alongside the status, not combined into the badge. This keeps filtering simple and avoids confusing displays like "Approved (3/5)". The campaign only transitions to `Approved` when either all media owners approve OR the agency consciously decides to proceed with partial approvals.

6. **Platform Review Terminology**: The middle approval stage is called "Platform Review" (not "Internal Review"). This is clearer for external users who see MW as the platform facilitating the transaction.

7. **Partial Media Owner Approval**: When some media owners approve, some reject, and some are silent, the agency has explicit control over how to proceed. They can: (a) proceed with approved only, (b) wait for pending responses, (c) request MW follow-up, or (d) revise rejected items. The campaign stays in `Reviewing` status until the agency makes a decision. Excluded items are clearly marked with their exclusion reason (rejected vs. excluded by buyer).

---

## 14. Appendix: IAB OpenDirect 2.1 Alignment

This section maps our status model to the IAB OpenDirect OOH specification for reference.

| MW Planner Status | OpenDirect Line Status |
|-------------------|------------------------|
| pending | DRAFT |
| reserved | RESERVED |
| booked | BOOKED |
| inflight | INFLIGHT |
| paused | PAUSED |
| completed | FINISHED |
| declined | DECLINED |
| expired | EXPIRED |
| cancelled | CANCELLED |

The OpenDirect spec uses PendingReservation and PendingBooking as transient states during API calls. In Planner UI, these map to loading/processing indicators rather than visible statuses.

---

*Document ends. For implementation details, see the task list and test campaign specifications in the development notes.*
