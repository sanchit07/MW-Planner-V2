# Reserve Campaign Feature

## Chapter 1: Introduction — Why Reservations Matter

In the world of Out-of-Home advertising, there is a universal tension that every media planner knows well. A planner identifies the perfect billboard — a massive digital screen overlooking a busy highway interchange, visible to thousands of commuters every morning. The client is thrilled. The location is ideal, the pricing is within budget, and the campaign dates align perfectly with the product launch.

But there is a problem. Your client has not yet approved the final creative design. The finance team is still reviewing the purchase order. And the legal department wants to see the contract before giving the green light. All of this will take several days, perhaps a week.

Meanwhile, three other agencies are also eyeing that very same billboard. In a first-come, first-served world, hesitation means loss.

This is the dilemma the Reserve Campaign feature was built to solve.

A reservation is, in its simplest form, a temporary hold placed on an inventory unit — a billboard, a digital screen, a transit display — that signals to everyone in the ecosystem: *"This space is spoken for, at least for now."* It does not commit anyone to a purchase. It does not lock in pricing. It simply buys time — structured, transparent, and fair time — for the planner to complete their internal processes while preventing the inventory from being snatched away by a competitor.

The Reserve Campaign feature brings formal structure to this process. It introduces clear rules, automated timers, priority queues, and approval workflows — all designed to make the holding process fair, efficient, and transparent for planners, agencies, and media owners alike.

> **Diagram: Reservation Creation Flow**
> See the visual workflow: [Miro Board — Reservation Creation Flow](https://miro.com/app/board/uXjVGEva6dA=)

---

## Chapter 2: Beginning the Reservation Journey

### 2.1 Where It All Starts

The reservation journey begins inside a campaign. From the campaign listing page, the planner opens the campaign they wish to work with, clicks the **Actions** menu (the three-dot menu or the dedicated action buttons), and selects **"Reserve Inventories."**

This action is not available for every campaign. The system enforces a clear rule:

> **Rule:** Reservations can only be created for campaigns in the following statuses: **Planned**, **Draft**, **Reviewing**, **Paused**, or **Rejected**.

The logic behind this rule is straightforward. A campaign in "Planned" or "Draft" status is still being shaped — this is precisely the stage where reservations are most needed. A campaign under "Reviewing" status may need inventory held while stakeholders evaluate the proposal. A "Paused" or "Rejected" campaign might be undergoing revisions and may need to hold onto its inventory positions.

Campaigns that have already been **Approved**, are **Active** (running), or have been **Completed** do not need reservations — they have moved past the planning stage entirely.

If a planner attempts to reserve inventory for a campaign in an ineligible status, the system displays:

> **Error:** "Cannot create reservations — this campaign must be in Planned, Draft, Reviewing, Paused, or Rejected status."

### 2.2 The Reservation Page

Once the planner clicks "Reserve Inventories," they are taken to the Reservations Page. This page is divided into three distinct sections, each serving a specific purpose.

**Section 1: Campaign Overview Card**

At the top of the page, a summary card displays essential context about the campaign. This includes the campaign name, its current status, the start and end dates, the total budget, and the target impressions. This information helps the planner make informed decisions about which inventories to hold. Every metric in this card includes a tooltip that explains what it means — for example, hovering over "Impressions" reveals a brief explanation of how impressions are calculated for OOH media.

**Section 2: Current Reservations Table**

If reservations already exist for this campaign, they appear here in a table. Each row represents one reserved inventory unit and shows:

- The inventory name and its location
- The **hold level** (Primary, Secondary, Tertiary, Quaternary, or Quinary — more on this later)
- The current **status**, shown as a colour-coded badge
- The **expiry date** with a countdown showing how many days remain
- Any notes the planner attached when creating the reservation
- Action buttons: **Extend** (to request more time) and **Cancel** (to release the hold)

The rows are visually styled to communicate status at a glance:
- A **green** left border means the reservation is confirmed
- An **amber** left border means the hold request is still pending
- A **red** left border means the request was declined
- An **orange** left border indicates a roadblock (scheduling conflict)
- A **red background tint** warns that the reservation expires within 48 hours

When one or more rows are selected using checkboxes, an **action bar** appears at the top of the table. This bar shows the count of selected items and provides two bulk action buttons: **"Extend All"** and **"Cancel All."** These bulk operations save considerable time when managing campaigns with many reserved inventories.

**Section 3: Inventory Selection Table**

Below the current reservations, the Inventory Selection Table displays all inventories associated with the campaign. This is where planners choose new inventories to reserve.

Each row in this table shows:
- The inventory name (with a link to its detailed specifications)
- The inventory type (Digital Billboard, Street Furniture, Transit Display, and so on)
- The physical location, with a map icon for quick reference
- Size and resolution specifications
- An **availability badge** (explained below)
- The media cost, in the campaign's configured currency
- Estimated impressions
- CPM (Cost Per Mille) and eCPM metrics

### 2.3 Understanding Availability

The availability badge beside each inventory unit communicates a critical piece of information: how much of the inventory's time is already committed during your campaign dates.

**Available (Green Badge):** The inventory has 100% availability for your campaign dates. No other campaign has claimed this time. This is the ideal scenario — reserving this inventory is straightforward and conflict-free.

**Partial (Yellow Badge with Percentage):** The inventory is partially booked by other campaigns. For example, you might see "65% Available." The tooltip reveals which campaigns have booked it, for what dates, and what percentage of time is occupied. For instance, "Nike — Winter Fashion Sale: Jun 1–15, 35% of time booked." This means you can still reserve the remaining availability, but you should be aware that you are sharing the slot.

**Reserved (Red Badge):** The inventory is fully booked or held during your campaign dates. You can still select it for reservation, but doing so will trigger a conflict warning, and the resulting reservation will be flagged as a **Roadblock** — a concept explained in detail in Chapter 6.

### 2.4 Selecting Inventories and Submitting

The planner selects the inventories they wish to reserve by clicking the checkbox beside each row. As inventories are selected, a **footer bar** appears at the bottom of the screen showing:

- The number of inventories selected
- The total media cost for the selected inventories
- The total estimated impressions
- The average CPM across all selections
- A prominent **"Save Reservations"** button

The **"Select All"** checkbox at the top of the table selects every inventory in the list — including those that are already reserved by others. If any of the selected inventories have conflicts, the system immediately shows a warning before proceeding (see Section 2.5).

For each selected inventory, the planner can:
- Set a **custom expiry date** (the default is 5 working days from today)
- Add **notes** explaining the purpose of the reservation — for instance, "Awaiting Q3 budget approval from client, expected by Friday"

### 2.5 What Happens When You Select Unavailable Inventory

Unlike many systems that simply prevent you from choosing unavailable options, MW Planner takes a deliberately flexible approach. You *can* reserve inventory that shows "Reserved" or "Fully Booked."

Why? Because advertising is fundamentally a negotiation business. The current holder might cancel their booking. They might be open to sharing the slot on a digital display with ad rotation. Their hold might expire before yours does.

When you select an inventory that has conflicts, the system displays a warning dialog:

> **"Availability Conflict Detected"**
>
> *"These items are held by others. Your hold will be queued at the next available priority level."*
>
> If you proceed, this schedule will be marked as a **Roadblock** — you can still reserve it, but you will need to resolve the conflict before converting it to a booking.

The planner then has two choices: **"Cancel"** to deselect the conflicting inventory, or **"Acknowledge & Proceed"** to continue with the reservation, accepting the roadblock flag.

### 2.6 After Submission

When the planner clicks **"Save Reservations,"** several things happen in rapid succession:

1. The system creates a reservation record for each selected inventory. The initial status is set to **"Hold Requested"** — not "Reserved." This distinction is important. A hold request signals intent, but the media owner has not yet confirmed it.

2. Each reservation is assigned a **default expiry** of 5 working days (unless the planner chose a custom date). The expiry is configurable at the organisation level — more on this in Chapter 8.

3. A **notification** is sent to the media owner who controls these inventory locations. The notification informs them that a new hold request has been received and requires their attention.

4. An **activity log entry** is created in the campaign history, recording who made the reservation, when, and for which inventories.

5. The **auto-approval timer** begins. If the media owner does not respond within 2 working days, the system will automatically approve the hold request. The planner sees a message: *"Pending approval from [Media Owner name]. Will auto-approve on [date]."*

At this point, the planner has done their part. The ball is now in the media owner's court.

---

## Chapter 3: The Media Owner's Response

### 3.1 Receiving Hold Requests

When a planner submits a hold request, the media owner who manages the requested inventory locations sees the pending requests in their dashboard. Each request shows the campaign name, the requesting planner's name and agency, the requested dates, and the auto-approval countdown.

> **Diagram: Media Owner Response Flow**
> See the visual workflow: [Miro Board — Media Owner Response Flow](https://miro.com/app/board/uXjVGEva6dA=)

### 3.2 The Two-Working-Day Window

The media owner has a window of **2 working days** to respond. This timer excludes weekends and configured public holidays. For example:

- A request submitted on **Wednesday** will auto-approve on **Friday** if no response is received.
- A request submitted on **Friday** will auto-approve on **Tuesday** (Monday and Sunday are not counted as working days). If Monday is a public holiday, it auto-approves on **Wednesday**.

This 2-working-day window can be configured (minimum: 1 working day, maximum: 5 working days), but the default of 2 days strikes a balance between giving media owners enough time to review and preventing planners from being blocked indefinitely.

### 3.3 Accepting a Hold Request

When the media owner clicks **"Accept"**:

- The reservation status changes from "Hold Requested" to **"Reserved"** (confirmed)
- The inventory is now **locked** for the planner's campaign until the expiry date
- The planner receives a notification: *"Your reservation has been confirmed"*
- An activity log entry records who accepted the hold and when

### 3.4 Declining a Hold Request

When the media owner clicks **"Decline"**:

- A confirmation dialog appears, asking for an optional **reason** (e.g., "Billboard under maintenance during these dates," or "Inventory reserved for priority client")
- The reservation status changes to **"Declined"**
- The reason is recorded in the campaign history
- The planner receives a notification with the decline reason
- If the declined inventory was the highest-priority hold, the next hold in the queue is **promoted** automatically (see Chapter 4)

### 3.5 Auto-Approval: When Silence Means Consent

If the media owner does not respond within the 2-working-day window, the system takes action on their behalf:

- The reservation status changes to **"Reserved"** (auto-confirmed)
- A special note is attached: *"Auto-approved after 2 working days with no response"*
- The planner is notified that their hold has been confirmed
- An activity log entry records the auto-approval

This mechanism ensures that non-responsive media owners do not become bottlenecks. The planner can proceed with confidence, knowing that their hold is secure.

### 3.6 Self-Approval Prevention

The system includes a safeguard to enforce separation of duties. Even if a member of the media owner's own team created the hold request (for example, an internal campaign), a *different* authorised user must approve it. You cannot approve your own requests. This prevents conflicts of interest and ensures that every hold goes through a genuine review process.

---

## Chapter 4: The Multi-Level Hold System

### 4.1 The Problem of Multiple Claimants

In a busy advertising market, it is entirely common for several planners to want the same billboard at the same time. Consider a prime digital screen at a major shopping mall entrance during the holiday season. Every agency wants it. But only one can ultimately book it.

The multi-level hold system provides a fair and transparent mechanism for managing this competition.

> **Diagram: Multi-Level Hold System**
> See the visual workflow: [Miro Board — Multi-Level Hold System](https://miro.com/app/board/uXjVGEva6dA=)

### 4.2 The Five Hold Levels

When multiple planners request a hold on the same inventory unit for overlapping dates, the system assigns priority levels based on who requested the hold first — strictly by timestamp:

| Priority | Name | What It Means |
|----------|------|---------------|
| Level 1 | **Primary** | The first planner to request a hold. They have the highest priority and the first right to convert the hold into a booking. |
| Level 2 | **Secondary** | The second planner to request. They are next in line if the Primary hold is released. |
| Level 3 | **Tertiary** | Third in the queue. |
| Level 4 | **Quaternary** | Fourth in the queue. |
| Level 5 | **Quinary** | Fifth in the queue — the maximum depth. |

The priority is determined **purely by timestamp**. There is no favouritism based on agency size, client budget, or relationship with the media owner. First come, first served — within the queue.

### 4.3 How Priority Shifting Works

When a higher-priority hold is released — whether through expiration, cancellation, or decline — every hold below it automatically shifts up by one level.

**Example:**

Suppose Billboard #A1 on MYY Highway has three holds:

| Before | Campaign | Hold Level |
|--------|----------|------------|
| 10:00 AM | Campaign A (Sarah, Creative Media Agency) | Primary |
| 10:15 AM | Campaign B (John, Brand Agency) | Secondary |
| 11:30 AM | Campaign C (Maria, AdCo) | Tertiary |

Now suppose Sarah's hold expires because she did not extend it in time.

| After | Campaign | Hold Level |
|-------|----------|------------|
| — | Campaign A (Sarah) | *Expired — removed* |
| — | Campaign B (John) | **Primary** (promoted from Secondary) |
| — | Campaign C (Maria) | **Secondary** (promoted from Tertiary) |

John and Maria do not need to do anything. The promotion happens automatically. The system sends them notifications: *"You have been promoted to Primary hold on Billboard #A1."*

### 4.4 Viewing the Hold Queue

When hovering over any inventory unit in the Inventory Selection Table, a **tooltip** appears showing the current hold queue:

- Each hold is listed with its level, the campaign name, and the requester's name and company
- The number of remaining available slots is shown at the bottom
- The tooltip updates in real time as holds are added, removed, or promoted

This transparency allows planners to make informed decisions. If a billboard already has a Primary and Secondary hold, a new planner can see that they would be placed at the Tertiary level — and decide whether that queue position is worth the wait.

### 4.5 What Happens When Someone Books

When any planner converts their reservation into a confirmed booking:

1. **All other holds** on that inventory are automatically **cancelled**
2. The cancelled holders receive notifications explaining that the inventory has been booked by another campaign
3. The booking is final — the competition phase for that inventory is over
4. Cancelled holds are moved to the archive for record-keeping

This is a decisive moment. Once a booking is confirmed, there is no further negotiation. The system enforces a clean break.

---

## Chapter 5: Extension Workflows

### 5.1 When You Need More Time

Reservations do not last forever — by design. The default expiry of 5 working days creates a natural rhythm: plan, evaluate, decide. But sometimes, legitimate circumstances require more time:

- The client is still reviewing creative concepts
- The finance department has not yet approved the purchase order
- Legal is reviewing contract terms
- The advertiser has not confirmed the budget
- The campaign is being coordinated with other media channels (television, digital, radio) and all channels need to align

In these situations, the planner can request an **extension**.

> **Diagram: Extension Workflow**
> See the visual workflow: [Miro Board — Extension Workflow](https://miro.com/app/board/uXjVGEva6dA=)

### 5.2 Requesting an Extension

To request an extension, the planner clicks the **"Extend"** button (shown as a clock icon) on the reservation row. This opens the Extension Dialog, which shows:

**Current Reservation Details:**
- The inventory name and location
- The current expiry date
- A countdown showing days remaining (colour-coded: green for 3+ days, amber for 1–2 days, red for less than 1 day)
- How many times this reservation has been previously extended
- The planner's current hold level (Primary, Secondary, etc.)

**New Expiry Selection:**
- A calendar date picker for selecting the new expiry date
- The default extension is **5 additional days**
- The system shows the extension duration (e.g., "Extension of 5 days")
- The system shows the total days from today to the new expiry

**Validation Rules:**
- The new expiry date **must be after** the current expiry date. If the planner selects an earlier date, the system displays: *"Error: New expiry date must be after the current expiry."*
- If the new date exceeds the campaign's end date, an **amber warning** appears: *"The new expiry date extends beyond the campaign end date."*
- If the extension would create new conflicts with other campaigns, a **red warning** appears

**Extension Reason:**
- A text field where the planner explains why more time is needed
- This information is shared with the media owner when they review the request

### 5.3 Extension Requires Media Owner Approval

This is an important distinction from some other systems where extensions are automatically granted. In MW Planner, **every extension request requires media owner approval**, following the same 2-working-day auto-approval mechanism as the initial hold request.

The workflow proceeds as follows:

1. The planner submits the extension request
2. The reservation status changes to **"Extension Pending"**
3. The **original expiry date is preserved** — the reservation does not expire while the extension is being reviewed
4. The media owner is notified of the extension request
5. The media owner has **2 working days** to respond
6. **If approved:** The expiry date is updated to the new date, and the status returns to "Reserved"
7. **If declined:** The original expiry remains unchanged, and the planner is notified. The reservation will expire on its original date.
8. **If no response:** The extension is **auto-approved** after 2 working days, with a note: *"Extension auto-approved after 2 working days with no response"*

The planner sees a banner on the reservation: *"Extension request pending approval. Will auto-approve on [date] if no action taken."*

### 5.4 Bulk Extensions

When managing campaigns with many reserved inventories, extending them one by one is tedious. The system provides a **Bulk Extend** operation:

1. Select multiple reservations using the checkboxes in the Current Reservations table
2. The action bar appears showing "[N] reservations selected" with an **"Extend All"** button
3. Click "Extend All"
4. Set the new expiry date (applied to all selected reservations)
5. Add a reason for the extension
6. Submit — all extension requests are created simultaneously

Each individual extension still requires media owner approval (or auto-approval after 2 working days).

### 5.5 Bulk Cancellation

Similarly, when a planner decides to release multiple reservations at once — perhaps because the campaign direction has changed, or alternative inventory has been secured — the **Bulk Cancel** operation provides a quick way to do so:

1. Select the reservations to cancel using the checkboxes in the Current Reservations table
2. The action bar appears showing "[N] reservations selected" with a **"Cancel All"** button
3. Click "Cancel All"
4. A confirmation dialog appears: *"Are you sure you want to cancel [N] reservations? This action cannot be undone."*
5. Confirm — all selected reservations are released immediately

Bulk cancellation is instant and does not require media owner approval. The cancelled holds are released, any lower-priority holds on the same inventory are promoted, and the affected planners and media owners are notified.

### 5.6 Extension Limits

There is no hard limit on the number of times a reservation can be extended. However, the system tracks the **extension count**, and media owners can see this information when evaluating requests. A reservation that has been extended five times may prompt a conversation between the media owner and the planner about whether the deal is truly progressing.

---

## Chapter 6: Roadblocks and Conflict Resolution

### 6.1 What Is a Roadblock?

A **Roadblock** is a special flag applied to a reservation that has a scheduling conflict with another campaign. It is MW Planner's way of saying: *"You can hold this inventory, but be aware — there is an existing claim that overlaps with your dates, and this conflict must be resolved before you can proceed to booking."*

> **Diagram: Roadblock & Conflict Resolution**
> See the visual workflow: [Miro Board — Roadblock & Conflict Resolution](https://miro.com/app/board/uXjVGEva6dA=)

### 6.2 How Roadblocks Are Created

A roadblock is created when a planner reserves inventory that is already held or booked by another campaign for overlapping dates. The process works like this:

1. The planner selects an inventory unit that shows "Reserved" or has partial availability conflicts
2. The system displays a **conflict warning** explaining which campaigns overlap and for what dates
3. The planner acknowledges the conflict and chooses to proceed anyway
4. The reservation is created, but the inventory schedule is marked with a **Roadblock** flag
5. In the campaign view, roadblocked items are marked with a distinctive indicator (typically a construction/warning icon)

### 6.3 Why Allow Roadblocks?

This design choice reflects a practical reality of the advertising industry. Inventory conflicts are not always permanent. The competing campaign might:

- Cancel their booking for reasons unrelated to your campaign
- Be willing to share the slot — particularly for digital displays that support ad rotation
- Have a hold that expires before yours does
- Be open to negotiating alternative dates

By allowing planners to register their interest even when conflicts exist, the system creates a record of demand that benefits everyone. The media owner sees that multiple parties want the same inventory (which may influence pricing or negotiation). The planner has a formal position in the queue rather than relying on informal conversations.

### 6.4 Resolving Roadblocks

Before a campaign with roadblocked inventory can proceed to the booking stage, the roadblocks must be resolved. There are several paths to resolution:

**Path 1: Wait for the Conflict to Disappear**
The competing hold might expire naturally, or the other campaign might cancel. When this happens, the roadblock is automatically cleared.

**Path 2: Negotiate with the Competing Campaign**
The planner can work with the media owner to find a solution. This might involve time-sharing on digital displays, adjusting campaign dates to avoid overlap, or arranging priority based on business relationship.

**Path 3: Choose Alternative Inventory**
If the conflict cannot be resolved, the planner can remove the roadblocked inventory from their campaign and substitute it with an alternative location. The Availability View (linked from the Reservations page) helps planners identify alternative options.

Until a roadblock is resolved, the system prevents conversion to a confirmed booking for that specific inventory unit. Other, non-roadblocked inventory in the same campaign can proceed normally.

---

## Chapter 7: The Reservation Lifecycle

### 7.1 Status Transitions

Every reservation moves through a series of statuses during its lifetime. Understanding these transitions is essential for both planners and media owners, because each status determines what actions are available and what will happen next.

> **Diagram: Reservation Lifecycle & Status Transitions**
> See the visual workflow: [Miro Board — Reservation Lifecycle](https://miro.com/app/board/uXjVGEva6dA=)

**Hold Requested** — The starting state. The planner has submitted a reservation, and the media owner has not yet responded. During this state, the auto-approval timer is running. The planner sees the message: *"Pending approval from [Media Owner]. Will auto-approve on [date]."*

**Reserved (Confirmed)** — The media owner has accepted the hold, or the auto-approval timer has elapsed. The inventory is now locked for the planner's campaign. From this state, the planner can extend the reservation, cancel it, or proceed to booking.

**Extension Pending** — The planner has requested an extension, and the media owner has not yet responded. The original expiry date remains in force while the extension is under review. The auto-approval timer is running for the extension.

**Declined** — The media owner has rejected the hold request. The inventory is released. If there are other holds in the queue, the next one is promoted. The planner must select alternative inventory or wait for the situation to change.

**Expired** — The reservation has passed its expiry date without being extended, cancelled, or converted to a booking. The hold is released automatically. Lower-priority holds are promoted.

**Released (Cancelled)** — The planner has manually cancelled the reservation. The hold is released, and lower-priority holds are promoted.

**Booked** — The campaign has been confirmed and the inventory is booked. All other holds on this inventory are cancelled.

### 7.2 Visual Indicators

The system uses a consistent set of colour-coded badges and icons to communicate status at a glance throughout the interface:

| Visual | Meaning |
|--------|---------|
| **Amber badge with clock icon** | Hold Requested — awaiting media owner response |
| **Green badge with checkmark** | Reserved / Confirmed |
| **Orange badge** | Extension Pending |
| **Red badge with X** | Declined or Expired |
| **Orange badge with warning icon** | Roadblock — scheduling conflict |
| **Pulsing warning indicator** | Expiring within 48 hours |
| **Blue info banner** | Auto-approval date notice |

These badges appear consistently across the Reservations page, the Campaign table listing, the Campaign detail view, and the Activity feed.

### 7.3 Key Rules Summary

1. **Default Expiry:** 5 working days. This is configurable at the organisation level in Settings → Reservation Settings.

2. **Auto-Approval:** If a media owner does not respond within 2 working days (excluding weekends and configured holidays), the hold request or extension request is automatically approved.

3. **Booking Cancels All Other Holds:** When an inventory unit is booked, all other reservations and holds on that same unit are automatically cancelled, and the affected users are notified.

4. **Priority Shifting:** When a higher-priority hold expires, is cancelled, or is declined, all lower-priority holds shift up by one level automatically.

5. **Execution Path Impact:** If a campaign switches its execution path from Full Workflow to Quick Launch (open auction), all existing reservations are released. The system warns the planner before this happens: *"Existing reservations will be released."*

6. **Extension Count Tracking:** The system records how many times each reservation has been extended. While there is no hard limit, media owners can see this count when evaluating extension requests.

---

## Chapter 8: Configurable Settings

### 8.1 Default Expiry Period

The default reservation expiry is **5 working days**, but organisations can adjust this to match their typical approval timelines.

**To change the default expiry:**
1. Navigate to **Settings → Reservation Settings**
2. Adjust the **"Default Reservation Expiry (Days)"** setting
3. Save changes

This change affects all new reservations created after the setting is changed. Existing reservations retain their original expiry dates.

**Recommended settings by organisation type:**
- **Fast-moving agencies** with quick approval processes: 3 days
- **Standard operations**: 5 days (the default)
- **Organisations with complex approval chains**: 7–10 days

### 8.2 Auto-Approval Timeout

The 2-working-day auto-approval window can also be configured:
- **Minimum:** 1 working day
- **Maximum:** 5 working days
- **Default:** 2 working days

The auto-approval timer respects the organisation's configured **holiday calendar**. If your organisation observes certain national or regional holidays, these are excluded from the working-day count.

---

## Chapter 9: Cross-Feature Impacts

Reservations do not exist in isolation. They interact with several other parts of the MW Planner system. Understanding these connections helps planners and media owners see the full picture.

### 9.1 Campaign Table

In the main campaign listing, campaigns that have active reservations display a **reservation badge**. This badge shows how many inventories are currently reserved, giving a quick visual indicator of the campaign's reservation activity without opening the campaign.

### 9.2 Price Management

The Price Management page reflects reservation statuses at the item level. Inventory items that are in "Hold Requested" or "Reserved" status are displayed accordingly in the pricing table. Price negotiations can begin once an inventory item reaches "Reserved" status — you cannot negotiate pricing for an inventory that has not yet been confirmed.

### 9.3 Campaign Approval Workflow

The Campaign Approval system includes an **item status summary** that accounts for reservation statuses. Approvers can see how many inventory units are reserved, how many are pending holds, and how many have been declined. This gives approvers a complete picture of whether the campaign's inventory is secure before they approve the overall campaign.

### 9.4 Execution Path Selection

When a campaign changes its execution path — for example, switching from **Full Workflow** (traditional guaranteed buying) to **Quick Launch** (open auction marketplace) — the system warns that all existing reservations will be released. The warning specifically states:

> *"Your campaign will be moved to the open auction marketplace. Any existing reservations and negotiations will be reset."*

This is a consequential action, and the system requires explicit confirmation before proceeding.

### 9.5 Availability View

The Reservations page links to the **Availability View** — a timeline-based calendar view that shows how inventory is booked across time. This visual tool helps planners identify open windows, see which campaigns overlap, and find alternative inventory when their preferred choices are unavailable or roadblocked.

### 9.6 Activity Feed

Every reservation action — creation, acceptance, decline, extension, auto-approval, cancellation, expiry — is recorded in the **Activity Feed**. These entries appear both in the campaign-specific history and in the system-wide activity feed. Each entry includes who performed the action, when, and what changed.

---

## Chapter 10: Error Conditions and Edge Cases

### 10.1 Common Error Messages

Throughout the reservation workflow, the system may display error messages in response to certain conditions. Here are the most common ones and what they mean:

| Condition | Error Message | What to Do |
|-----------|---------------|------------|
| Campaign in wrong status | "Cannot create reservations — campaign must be in Planned, Draft, Reviewing, Paused, or Rejected status" | Change the campaign status first, or work with your administrator |
| Extension date too early | "New expiry date must be after the current expiry" | Select a date that is later than the current expiry |
| Extension beyond campaign dates | "The new expiry date extends beyond the campaign end date" (warning) | You may proceed, but be aware the reservation will outlast the campaign dates |
| Conflict detected during selection | "These items are held by others. Your hold will be queued at the next priority level" | Acknowledge the warning and decide whether to proceed with a Roadblock |
| Booking blocked by roadblock | "Cannot convert to booking — unresolved roadblocks exist" | Resolve all roadblocked inventory items first |
| Self-approval attempt | "You cannot approve your own hold request" | A different authorised user must approve the request |

### 10.2 Special Scenarios

**Scenario: Five Planners Reserve the Same Inventory Simultaneously**

When multiple planners submit hold requests for the same inventory at nearly the same time, the system processes them in the order received (based on timestamp). Each planner is assigned the next available hold level. All five can reserve successfully, with hold levels from Primary through Quinary. The system handles this gracefully without requiring any manual intervention.

**Scenario: A Hold Expires During a Holiday Weekend**

If a reservation's expiry date falls on a weekend or holiday, the reservation expires as scheduled — the expiry timer is based on calendar days from the date of creation (5 working days calculated at creation time). However, the *auto-approval* timer for hold requests and extensions excludes non-working days. This means a hold request submitted on Friday will not auto-approve until Tuesday (assuming no Monday holiday).

**Scenario: Campaign Is Booked While Others Have Holds**

When one planner books the campaign, all other reservations and holds on the same inventory are cancelled immediately. All affected users receive notifications explaining that the inventory has been booked. This is a final, irreversible action.

**Scenario: Switching Execution Path Releases Reservations**

If a campaign switches from Full Workflow to Quick Launch, all reservations are released. The system warns the planner before this happens and requires explicit confirmation. Once confirmed, all holds are cancelled, and the inventory becomes available again.

---

## Chapter 11: The Micro-Availability Model

### 11.1 Beyond Simple "Available / Not Available"

Traditional OOH inventory systems treat availability as binary — a billboard is either free or it is not. This simplification works for classic static billboards, but it fails to capture the reality of modern OOH media, where multiple campaigns can share a single screen, where time-of-day matters, and where physical constraints introduce additional complexity.

MW Planner's reservation system accounts for three dimensions of availability, collectively known as the **Micro-Availability Model**. Each dimension describes a different aspect of how inventory capacity is consumed.

### 11.2 Dimension 1: Time

The **time dimension** measures which specific hours or days within the campaign period an inventory unit is occupied. A digital screen may be fully booked during the morning rush (7:00–10:00 AM) but entirely available during the afternoon. A billboard might be committed for the first two weeks of a month but open for the final two.

When a planner views the availability of an inventory unit, the system presents **time-granular availability data**. This means the planner does not simply see "65% available." Instead, they can see *which* hours and days are available and which are committed. This is surfaced through the inventory-level scheduling grid — a 24-hour by 7-day interactive interface that shows occupied and open time slots.

For reservations, the time dimension determines whether a hold request creates a conflict. If Campaign A holds a screen from 6:00 AM to 12:00 PM, Campaign B can hold the same screen from 12:00 PM to 11:00 PM without any conflict. Conflicts only arise when the requested time slots overlap with existing commitments.

### 11.3 Dimension 2: Share of Voice (SOV)

For digital OOH screens that support ad rotation — where multiple advertisements cycle on the same display — availability is measured not just in time but in **Share of Voice** (SOV). SOV represents the percentage of ad plays a campaign receives during a given time period.

A digital screen may support 10-second ad spots cycling every 60 seconds. This means the screen can accommodate six advertisers simultaneously, each receiving approximately 16.7% SOV. If two campaigns have already claimed 40% of the available SOV, the remaining 60% is available for new reservations.

When a planner reserves a digital screen, the system checks not only the time slots but also the remaining SOV within those slots. A reservation request specifying 30% SOV against a screen with only 20% available will trigger a partial availability warning. The planner can then decide whether to proceed with the available 20%, request the full 30% (which creates a conflict/roadblock for the missing 10%), or look for alternative inventory.

SOV-based availability is particularly relevant for high-traffic locations — transit stations, shopping mall entrances, highway digital boards — where multiple advertisers often share the same display.

### 11.4 Dimension 3: Physical Frame

The **physical frame dimension** applies to inventory that has multiple discrete display positions within a single location. Consider a bus shelter with three poster panels, or a retail environment with ten screen positions across different aisles.

In these cases, each physical frame is tracked independently. One poster panel at a bus shelter might be reserved while the other two remain available. The system distinguishes between the overall location (the bus shelter) and the individual frames (panels A, B, and C).

When viewing availability, the planner sees frame-level granularity. The overall location may show "33% available" if one of three panels is reserved, but the planner can drill down to see exactly which panel is held and which are open.

Reservations are placed at the frame level, not the location level. This prevents over-reservation — a planner cannot reserve "the bus shelter" generically; they must select specific panels. This precision ensures that availability data remains accurate and that conflicts are detected at the correct granularity.

### 11.5 How the Three Dimensions Interact

In practice, these three dimensions operate simultaneously. A single inventory unit might be:

- **Time:** Available from 2:00 PM to 10:00 PM on weekdays, but committed during morning hours
- **SOV:** 45% of the afternoon rotation is available (55% already claimed by two campaigns)
- **Frame:** Panel B is available; Panels A and C are committed

A new reservation request is evaluated against all three dimensions. The system calculates the available capacity for the requested time, SOV, and frame combination. If any dimension is insufficient, the planner receives a specific warning indicating which dimension is constrained — allowing them to adjust their request rather than simply being told "unavailable."

---

## Chapter 12: Advanced Scenarios

### 12.1 Partial Holds

A **partial hold** occurs when a planner reserves only a portion of an inventory unit's total available capacity. This commonly arises with digital screens that support SOV-based booking.

For example, a planner might reserve a digital screen for 25% SOV during peak hours (6:00–9:00 AM and 5:00–8:00 PM) while leaving the off-peak hours and the remaining 75% SOV open for others.

Partial holds are tracked at the same granularity as full holds. They participate in the priority queue, are subject to the same expiry and auto-approval rules, and can be extended or cancelled. The only difference is that a partial hold does not block other planners from reserving the remaining capacity.

The system calculates cumulative utilisation across all active holds. If a screen has partial holds totalling 90% SOV for a given time slot, a new request for 15% SOV will trigger a conflict warning — the requested capacity exceeds what remains available.

### 12.2 Loop Hijacking Prevention

**Loop hijacking** describes a scenario where a planner manipulates the hold queue by repeatedly letting their reservation expire and immediately re-reserving, effectively blocking competitors from advancing in the queue.

The system includes safeguards against this behaviour:

1. **Re-reservation cooldown:** If a planner's hold on a specific inventory unit expires or is cancelled, they cannot re-reserve the same unit for a configurable cooldown period (default: 24 hours). This gives other planners in the queue a fair opportunity to advance.

2. **Queue position tracking:** When a hold expires and the planner creates a new reservation for the same inventory, the new hold is placed at the end of the current queue — not restored to its previous position. The planner loses their queue advantage.

3. **Repeat-reservation alerting:** The system flags to media owners when a planner has reserved, expired, and re-reserved the same inventory unit multiple times. This pattern may indicate indecision, strategic blocking, or a deal that is not progressing, and gives the media owner relevant information when evaluating subsequent hold requests.

### 12.3 Cascading Expiry

**Cascading expiry** occurs when multiple reservations on the same inventory have expiry dates that are close together, causing a rapid chain reaction of expirations and promotions.

For example, suppose five planners hold a billboard with the following expiry dates:

| Hold Level | Campaign | Expiry |
|------------|----------|--------|
| Primary | Campaign A | Monday |
| Secondary | Campaign B | Monday |
| Tertiary | Campaign C | Tuesday |
| Quaternary | Campaign D | Tuesday |
| Quinary | Campaign E | Wednesday |

On Monday, Campaigns A and B both expire. Campaign C is promoted to Primary, Campaign D to Secondary, and Campaign E to Tertiary. On Tuesday, Campaigns C and D expire, promoting Campaign E to Primary.

The system handles this sequence automatically. Each expiration triggers an immediate promotion of all lower-priority holds. Notifications are sent to all affected planners as their position changes. The key principle is that promotions are processed **in order** — the system does not batch or delay them. Each expiration is treated as an independent event that triggers its own set of promotions and notifications.

In scenarios with extremely tight cascading (multiple expirations on the same day), the system processes them in chronological order of the original hold creation timestamp, ensuring deterministic and predictable behaviour.

### 12.4 Challenge Rules

A **challenge** occurs when a planner with a lower-priority hold attempts to displace a higher-priority holder. The system does not support arbitrary challenges — the priority queue is deterministic and based on timestamp order.

However, there are structured pathways for resolving priority disputes:

1. **Media Owner Override:** The media owner, as the ultimate authority over their inventory, can manually re-order the hold queue. This is a deliberate action with a full audit trail, and all affected planners are notified of the change with the media owner's stated reason.

2. **Price-Based Priority:** In configurations where the organisation has enabled price-based priority, a lower-priority holder who commits to a higher rate card price may request a priority review from the media owner. This does not automatically override the queue — it creates a request that the media owner evaluates.

3. **Expiry Waiting:** The most common resolution is simply waiting. If the higher-priority holder does not convert to a booking before their hold expires, the lower-priority holder advances automatically.

The system is deliberately conservative about priority overrides. The default position is that the queue order is respected. Any deviation requires explicit media owner intervention with documented justification.

### 12.5 Make-Good Calculations

A **make-good** is a compensatory offering when reserved inventory becomes unavailable due to circumstances beyond the planner's control — such as equipment failure, weather damage, construction obstruction, or unexpected maintenance.

When a confirmed reservation is affected by an inventory disruption, the system initiates a make-good workflow:

1. **Impact Assessment:** The system calculates the affected period — how many days or hours of the reservation are impacted, the estimated impression loss, and the financial value of the lost exposure.

2. **Alternative Suggestions:** The system identifies equivalent or comparable inventory units that are available during the affected period. Equivalency is assessed based on location proximity, audience profile similarity, impression volume, and format compatibility.

3. **Make-Good Proposal:** The media owner creates a make-good proposal specifying the alternative inventory, the compensatory period, and any rate adjustments. This proposal is sent to the planner for review.

4. **Planner Response:** The planner can accept the make-good (the reservation is transferred to the alternative inventory), reject it (the reservation is cancelled for the affected period with a credit note), or negotiate (counter-propose with a different alternative or extended dates).

5. **Audit Trail:** Every step of the make-good process is recorded in the campaign history, including the original disruption report, the impact calculation, the proposed alternatives, and the final resolution.

Make-good entitlements are calculated proportionally. If a 30-day reservation is disrupted for 5 days, the make-good covers the equivalent of 5 days of exposure, not the full 30 days.

### 12.6 Maintenance Blockouts

**Maintenance blockouts** are periods when inventory is taken offline for scheduled maintenance — screen replacement, structural repairs, content system upgrades, or physical cleaning.

Media owners configure maintenance blockouts in advance through the inventory management system. These blockouts are reflected in the availability data:

1. **Visibility:** Maintenance periods appear on the availability timeline as blocked segments, clearly distinguished from campaign bookings. Planners can see upcoming maintenance windows when evaluating inventory.

2. **Reservation Prevention:** The system prevents reservations that overlap with a configured maintenance blockout. If a planner selects dates that include a maintenance window, the system displays a warning: *"This inventory has scheduled maintenance from [date] to [date]. Reservations covering this period cannot be created."*

3. **Impact on Existing Reservations:** If a media owner schedules maintenance that overlaps with an existing reservation, the system flags this as a conflict. The media owner is prompted to either adjust the maintenance window or initiate a make-good process for the affected planner.

4. **Recurring Maintenance:** Some inventory requires regular maintenance (e.g., quarterly screen calibration). Media owners can configure recurring blockouts that automatically appear on the availability calendar for future periods.

Maintenance blockouts are a critical planning factor. They reduce the effective availability of inventory and must be accounted for when calculating campaign reach, frequency, and cost.

---

## Chapter 13: Pricing, Selling Terms, and Inventory Impact

### 13.1 How Reservations Interact with Pricing

Reservations and pricing are connected but sequential. A reservation secures the right to a position in the hold queue — it does not establish a price. Pricing discussions typically begin once a reservation reaches "Reserved" (confirmed) status.

However, the act of reserving can influence pricing in several ways:

**Demand Signal:** When multiple planners reserve the same inventory (creating a multi-level hold queue), this signals high demand to the media owner. Media owners may adjust rate card pricing in response to demonstrated demand — a billboard with five active holds is more valuable than one with no interest.

**Hold Duration Impact:** The length of a reservation hold may affect the terms offered. A planner who holds inventory for an extended period (through multiple extensions) ties up the media owner's capacity without generating revenue. Some media owners may factor holding duration into their pricing negotiations.

**SOV-Based Pricing:** For digital screens reserved at partial SOV, pricing is typically proportional to the share claimed. A 25% SOV hold at a rate card of 10,000 MYR per month would correspond to approximately 2,500 MYR, though the actual negotiated price may vary based on volume, relationship, and other factors.

### 13.2 Selling Terms and Reservation Constraints

The **selling terms** configured for each inventory unit can affect how reservations are created and managed:

**Minimum Booking Period:** If an inventory unit has a minimum booking period of 14 days, reservations for shorter periods are not permitted. The system enforces this constraint during reservation creation.

**Maximum SOV per Advertiser:** Some screens cap the SOV any single advertiser can hold — for example, no more than 50% per brand. Reservation requests that exceed this cap are rejected with a specific error message.

**Advance Booking Window:** Certain premium inventory may require reservations to be placed at least N days before the campaign start date. Last-minute reservation requests are blocked for these units.

**Contractual Commitments:** Some inventory is subject to long-term contracts that pre-allocate capacity. These contractual commitments reduce the available capacity visible to planners and are factored into the micro-availability calculations.

### 13.3 Currency and Cost Display

All monetary values displayed in the reservation workflow — media cost, estimated campaign spend, CPM, and eCPM — are shown in the currency configured for the campaign. MW Planner does not use currency symbols; instead, it displays the ISO currency code (MYR, USD, SGD, and so on) beside each value. This removes ambiguity when working across regional markets.

---

## Chapter 14: Visual Tools for Reservation Management

### 14.1 Availability Heatmap

The **Availability Heatmap** is a visual representation of inventory capacity across time. It displays a calendar-style grid where each cell represents a day (or an hour, depending on the zoom level), and the colour intensity indicates how much of the inventory's capacity is committed.

**Colour Scale:**
- **White / Light Green:** Fully available — no active reservations or bookings
- **Yellow:** Partially committed (1–40% capacity used)
- **Orange:** Heavily committed (41–75% capacity used)
- **Red:** Near capacity or fully committed (76–100%)
- **Grey:** Maintenance blockout — inventory offline

The heatmap can be filtered by inventory type, location, date range, and media owner. Planners use it to identify availability patterns — for instance, spotting a cluster of available days in an otherwise heavily booked month, or identifying inventory types that consistently have open capacity.

Clicking on any cell in the heatmap reveals a detail panel showing which campaigns occupy the capacity, their hold levels, and their expiry dates. This allows planners to assess whether the commitment is firm (booked) or potentially temporary (held with an approaching expiry).

### 14.2 Reservation Nutrition Label

The **Reservation Nutrition Label** is a summary panel that provides a structured overview of a reservation's key attributes — presented in a standardised format for quick scanning, much like a product information label.

The Nutrition Label appears on the reservation detail view and includes:

| Field | Description |
|-------|-------------|
| **Hold Level** | The reservation's position in the priority queue (e.g., Primary, Secondary) |
| **Status** | Current status with colour indicator |
| **Created** | Date and time the reservation was first created |
| **Expiry** | Current expiry date with countdown |
| **Extensions** | Number of times extended, with dates of each extension |
| **Auto-Approval Date** | When the hold will auto-approve if no media owner response |
| **Requested By** | Planner name, agency, and role |
| **Media Owner** | Name of the inventory owner |
| **Time Dimension** | Specific hours/days reserved (if applicable) |
| **SOV** | Share of Voice percentage (for digital screens) |
| **Physical Frame** | Specific frame/panel reserved (for multi-frame inventory) |
| **Conflicts** | Number and nature of any scheduling conflicts |
| **Campaign Association** | Campaign name, status, and dates |
| **Estimated Value** | Media cost for the reserved period/capacity |

This standardised summary allows any stakeholder — planners, media owners, approvers — to quickly understand the full context of a reservation without navigating multiple screens.

### 14.3 What-If Analysis

The **What-If Analysis** tool allows planners to model reservation scenarios before committing. It answers the question: *"If I reserve this inventory with these parameters, what will happen?"*

The tool accepts the following inputs:

- **Inventory selection:** One or more inventory units to evaluate
- **Date range:** The desired campaign period
- **SOV requirement:** For digital screens, the desired share of voice
- **Time slots:** Specific hours/days if applicable

Based on these inputs, the What-If Analysis generates a report showing:

1. **Availability Assessment:** For each selected inventory unit, the tool shows the current availability across all three micro-availability dimensions (time, SOV, frame). It identifies which units are fully available, partially available, or fully committed.

2. **Queue Position Forecast:** If existing holds are present, the tool shows what priority level the new reservation would receive and how many holders are ahead in the queue. It also estimates when higher-priority holds might expire, based on their current expiry dates.

3. **Conflict Identification:** The tool lists all potential conflicts — overlapping campaigns, maintenance windows, selling term constraints — and categorises them by severity (blocking, warning, informational).

4. **Cost Projection:** Based on the selected inventory and configuration, the tool estimates the total media cost, CPM, and impression volume for the proposed reservation.

5. **Alternative Recommendations:** If the selected inventory is constrained, the tool suggests alternative units with similar characteristics (location, audience, format) that have greater availability.

The What-If Analysis does not create any reservations. It is a planning tool that helps planners make informed decisions before committing to a hold request. Results can be exported or shared with team members for collaborative planning.

---

## Chapter 15: Campaign Status Logic and Reservations

### 15.1 The Relationship Between Campaign Status and Reservation Status

Campaign status and reservation status are two separate but related concepts. The campaign has an overall lifecycle status (Draft, Planned, Reviewing, Approved, Active, Completed, and so on), while each inventory item within the campaign has its own reservation status (Hold Requested, Reserved, Declined, Expired, and so on).

These two status tracks operate independently but influence each other at key decision points.

### 15.2 Which Campaign Statuses Allow Reservations

As established in Chapter 2, reservations can only be created when the campaign is in one of the following statuses: **Planned**, **Draft**, **Reviewing**, **Paused**, or **Rejected**.

This rule exists because these statuses represent campaigns that are still in the planning or revision phase — exactly the stage where temporary holds are most valuable. Campaigns that have progressed beyond planning (Approved, Active, Completed) have already committed their inventory through formal bookings.

### 15.3 Campaign Status Transitions That Affect Reservations

Certain campaign status changes have direct implications for existing reservations:

**Campaign → Approved:** When a campaign is approved, reservations are not automatically converted to bookings. The planner must explicitly initiate the booking process. However, the approval serves as a signal that the campaign is progressing, and the reservation data (which inventory is held, at what priority) feeds into the booking workflow.

**Campaign → Active:** Once a campaign becomes active (its start date has arrived and it is running), any remaining reservations that were not converted to bookings are flagged for review. The system displays a warning: *"Campaign is now active. The following reservations have not been converted to bookings."* This prompts the planner to either finalise the booking or release the holds.

**Campaign → Cancelled:** If a campaign is cancelled, all associated reservations are **automatically released**. The system processes this as a bulk cancellation, promoting any lower-priority holds on the affected inventory and notifying all relevant parties.

**Campaign → Paused:** A paused campaign retains its reservations, but the expiry timers continue to run. The planner should consider extending reservations if the pause is expected to last longer than the remaining hold period.

**Campaign → Rejected:** A rejected campaign also retains its reservations. The planner can modify the campaign, address the rejection feedback, and resubmit for approval — all while maintaining their inventory positions.

### 15.4 Inventory-Level Status Independence

A critical design principle is that reservation status is tracked at the **inventory level**, not the campaign level. This means a single campaign can have inventory items in different reservation states simultaneously:

- Item A: Reserved (confirmed)
- Item B: Hold Requested (pending)
- Item C: Declined (released)
- Item D: Roadblock (conflict)
- Item E: Extension Pending

The campaign's overall readiness for booking is assessed by examining the aggregate of its inventory-level statuses. The Campaign Approval workflow (Chapter 9.3) presents this aggregate view to approvers, showing a summary of how many items are in each state.

### 15.5 Status Considerations for Multi-Tenant Operations

In a multi-tenant environment where users manage multiple companies, reservation status is scoped to the **tenant (company) context**. When a user switches between companies, they see only the reservations belonging to the selected company. This ensures data isolation and prevents cross-company visibility of hold positions.

The hold priority queue, however, is evaluated globally — a Primary hold from Company A and a Secondary hold from Company B both compete for the same inventory. The priority is determined by timestamp regardless of company affiliation. The queue is fair and company-neutral.

---

## Chapter 16: Best Practices

### 16.1 For Media Planners

**Reserve early, but realistically.** Create reservations as soon as you identify target inventory, but set expiry dates that give you enough time without being excessive. Over-reserving for unnecessarily long periods creates friction with media owners and reduces goodwill.

**Include meaningful notes.** Help media owners understand your situation. A note like *"Awaiting Q3 budget approval from client, expected by Friday"* is far more helpful than no explanation at all. Media owners are more likely to approve holds — and extensions — when they understand the context.

**Monitor expiration warnings.** The 48-hour warning (shown as a pulsing indicator) is your cue to act. Either extend the reservation, finalize the booking, or release the inventory so others can use it. Letting reservations expire passively sends a signal that you were not serious about the inventory.

**Do not over-reserve.** Only reserve inventories you are genuinely considering. Holding inventory you do not intend to use reduces availability for the entire ecosystem and erodes trust with media owners.

**Use bulk operations.** When managing multiple reservations, use the Bulk Extend and Bulk Cancel features to save time. Select the relevant rows, and apply the action to all of them at once.

**Acknowledge conflicts consciously.** If you are reserving roadblocked inventory, have a plan for resolving the conflict. Do not create roadblocks without a strategy for clearing them.

### 16.2 For Media Owners

**Respond promptly.** Planners are waiting on your decision. Quick responses build trust and make your inventory more attractive. Remember: if you do not respond within 2 working days, the request auto-approves anyway. Proactive engagement gives you more control over your inventory.

**Provide clear decline reasons.** If you cannot accept a hold, explain why. *"Maintenance scheduled for June 10–15"* or *"Inventory committed to priority client"* helps planners plan alternatives and reduces follow-up questions.

**Consider extension requests fairly.** Multiple extensions might indicate a deal that is struggling to close, but they could also reflect a client with a genuinely complex approval process. Look at the context — the extension reason, the planner's notes, and the campaign details — before making your decision.

**Review extension counts.** Before approving an extension, check how many times the reservation has already been extended. Excessive extensions may warrant a direct conversation with the planner to understand the situation.

---

*Document Version: 4.0*
*Last Updated: February 2026*
*Applicable Statuses: Planned, Draft, Reviewing, Paused, Rejected campaigns*
*Chapters: 16 (Core Workflow: 1–10, Advanced Scenarios: 11–15, Best Practices: 16)*
*Miro Board: [Reserve Campaign Feature — Workflow Diagrams](https://miro.com/app/board/uXjVGEva6dA=)*
