# MW Planner — Media Plan PRD

**Scope.** This document describes the Media Plan page of MW Planner — the page a planner opens after submitting a campaign, and the page an advertiser, an internal trader and a media owner all read to understand what is being bought. It is a scoped companion to `docs/PRD_MW_PLANNER.md` and follows the same voice, the same conditional-rule discipline, and the same worked-example chassis. Anything documented here overrides any earlier `media-plan-specification.md` content of the same name; anything not documented here defers to the main PRD.

**Style.** Paragraph-led, why-before-what, scenario-walked, tables only for enumerables, no marketing tone, every claim self-contained. A reader who has never seen the page should be able to picture it from this document, slide by slide and tab by tab, for the three worked variants introduced in §5.0.

**Out of scope.** Any change to the wizard, the campaign detail page, the approval workflows, the negotiation engine, the reservation state machine or the IMS handoff. Those behaviours are owned by the main PRD; this document only describes how the Media Plan page renders the artefacts they produce.

---

## §1. Purpose and audience

The Media Plan page is the place a campaign becomes a document an advertiser will read. Inside MW Planner the same record is called a *media plan* by the planner who drafted it and a *proposal* by the system that stores its approval status — two names for one artefact. The page is opened by four kinds of reader, and the page has to answer different questions for each.

The agency planner opens it to walk a brand client through what the agency has built — they want a story that holds together, in a deck they can present on a call without surprises. The brand-side advertiser opens it because they are about to spend money — they want to understand where their ad will run, who will see it, how often, what each outcome costs, and what the creative will physically look like in the world. The internal trader at Moving Walls opens it to confirm the agency's pitch is defensible against benchmark and against the catalogue of available alternatives — they read the same picture the agency sent but with every fee and every margin laid bare. The media owner opens it for one reason: to confirm which of their screens have been booked into the plan, for what window, at what rate, with what creative spec. The page presents one source of truth and lets each reader's role decide which slices of it they see.

Two views sit behind the same toggle in the page header. *Presentation* is a slide deck designed to be walked through on a call or exported to PowerPoint. *Analytics* is a seven-tab spreadsheet designed for desk-side review and Excel export. The toggle does not change the data, only the framing. A reader switching from one to the other sees the same numbers re-laid out for the format that suits the moment they are in.

The page is read-only. Every widget on it renders a campaign field; nothing on the page is edited in place. The reason is intentional — the Media Plan is the buyer-facing surface of the campaign and a buyer-facing surface that drifts away from the source of truth is a contract dispute waiting to happen. Edits happen on the wizard or on the campaign detail page and ripple back to the Media Plan on the next open. This is the design choice the rest of the page is built on; the consequences are unpacked in §3.

---

## §2. Glossary

Each definition is written to be self-contained, so a reader can refer back to this glossary at any point without losing context.

| Term | Definition |
|---|---|
| Media Plan | The on-screen, presentation- or analytics-formatted view of a campaign that a planner shares with another company. |
| Proposal | The database row that records the media plan's approval state. One per campaign. Identical artefact, different name for the system-of-record vs. the visual document. |
| Presentation View | The slide-deck view of the media plan. Read top-to-bottom, designed to be walked through on a call. |
| Analytics View | The seven-tab spreadsheet view of the same media plan. Designed for desk-side review and Excel export. |
| Slide | A single screen-sized panel inside the Presentation view. Slides are conditional — a slide that has nothing to say is not rendered. |
| Tab | A named pane inside the Analytics view. Tabs are persistent — an empty tab still appears, but its body explains why it is empty and deep-links the reader to the wizard step that would populate it. |
| Theme | A bundle of colours, fonts and section order. The page ships four (Default, Slate, Sunrise, Forest) and accepts tenant-specific themes when configured in Admin Console. |
| Viewer Mode | A dropdown in the page header that lets the planner preview the page as if they were a different role (Internal, Agency, Advertiser, Media Owner). It does not change permissions; it only previews them so the planner can see what each recipient will see before sharing. |
| Daypart | A named span of hours in a week. The page exposes a 7×24 heatmap that is the union of every per-inventory schedule grid. |
| Schedule Entry | One named time pattern attached to one inventory. An inventory can carry up to 15 entries (e.g. "Morning Rush", "Weekend Late Night") each with its own grid, spots-per-loop and spots-per-hour. |
| Spot per Loop | The number of times the ad is played per loop of the screen's content rotation. Decimal values are allowed (0.25, 0.5). |
| Spot per Hour | An optional override that fixes the absolute number of plays per hour regardless of loop length. |
| Geo POI | A geographic point of interest uploaded via CSV in Step 3 of the wizard. Each POI carries a name, lat/lng, radius and an include/exclude flag. |
| POI Match | An inventory whose location falls inside the radius of a POI. The page reports per-POI match counts so the planner can spot wasted POIs. |
| Inventory Type | The IMS-aligned classification of an inventory (Classic, Digital Screen, Transit, Retail, Network, Radio, Experiential, Cinema, Mobile). |
| Format | A finer subdivision of an inventory inside its type — "32-sheet billboard", "spectacular", "concourse digital", "in-cinema slide" — used when a reader needs more granularity than Type. |
| eCPM | Effective cost per thousand impressions, including every fee the viewer is allowed to see. |
| CPM | Media-only cost per thousand impressions, before any fee. |
| CPS | Cost per spot, used on programmatic-style audio and DOOH inventory where pricing is per spot rather than per impression. |
| SOV | Share of voice — percentage of impressions on a screen that belong to this campaign. |
| SOT | Share of time — percentage of looped time the ad occupies on a screen. |
| Reach | Estimated unique people exposed at least once across the plan period. |
| Frequency | Estimated average exposures per person across the plan period. |
| Carbon Footprint per Play | kg CO₂e per ad play, populated when the inventory has `hasCo2Data = true` in the catalogue. |
| Public Share Link | A read-only, tokenised URL that exposes the Media Plan to a recipient without a Planner account. The link locks the theme and the viewer mode at the moment of issuance and expires when the campaign is rejected or archived. |
| Adaptive Bin | The granularity (daily / weekly / monthly / quarterly) used by the Expected Delivery chart, chosen automatically from the plan duration. The exact thresholds are in §5.9. |
| Outlier Schedule | An inventory whose schedule grid deviates most from the plan-average grid, surfaced as a mini-heatmap on the Daypart slide so the reader can spot the unusual case without expanding every inventory. |
| Showtime Band | A named slot inside a cinema week — Weekday Matinee, Weekday Prime, Weekend Prime, Late Night — used as a cinema-specific targeting field. |
| Inventory-Type Ladder | The breakdown of cost and impressions on the Cost Breakdown slide, with one row per inventory type that appears in the plan. Types not present in the plan are not listed; the ladder grows and shrinks with the plan. |
| Patterns Row | A row of chips on the Daypart slide naming each schedule preset detected across the inventories (Commuter, Business Hours, Weekend, Nightlife, 24/7, Custom), with the count of inventories using each. |
| Multi-Spot Summary | A one-line summary on the Daypart slide of how many inventories carry more than one spot per loop, plus the plan-wide average spots-per-loop. |
| Country Census Panel | The fallback shown on the Audience Strategy slide when the planner did not enter any audience targeting — a country-level age × gender × income distribution from a static census lookup, clearly labelled as a generic distribution and not a projection. |
| IAB Tier-1 | The top level of the IAB Content Taxonomy used for brand category classification (Food & Drink, Sports, Automotive, etc.). Drives the brand-affinity descriptor on the Brand Context slide. |
| Brand Affinity Descriptor | A one-line generic sentence about how the brand's IAB category indexes against typical OOH dayparts in the targeted country. Sourced from an in-repo lookup table; not a research finding. |

---

## §3. How a Media Plan comes to life and stays in sync

A campaign becomes a Media Plan the moment its planner clicks **Submit Plan** at the end of the wizard. The submit click writes the proposal row, opens the approval workflow described in §7 of the main PRD, and unlocks the Media Plan link in the campaign's actions menu. There is no separate "generate media plan" button, no snapshotting step, and no per-share frozen copy — the Media Plan reads the live campaign on every open. Any subsequent edit by the planner on the wizard, any subsequent price agreement in Price Management, any subsequent Approve from a media owner in the Reservations queue is reflected the next time the page is opened. A reader who refreshes the page mid-call sees the freshest state.

This live link is the design choice the rest of the page is built on. Three consequences fall out of it, and the page's behaviour through the rest of this document follows from them.

**First, the page is deterministic given the wizard inputs.** There is no manual overlay layer. A planner cannot drag a slide around, edit a number directly on the deck, hide a slide they do not like or write a custom block of copy. Everything the deck shows is the rendering of a campaign field, and every conditional in this document names the campaign field that drives it. If a field is empty, the deck either omits the dependent block cleanly (the prevailing behaviour) or substitutes a generic block (the country-census panel on the Audience Strategy slide and the Empty Plan fallback are the only two cases in the deck where a substitution rather than an omission applies — every other case is "show what is there, hide what is not").

**Second, edits ripple immediately.** A planner who edits the Schedule tab on the campaign detail page changes the deck's Daypart slide on the next open. A media owner who Accepts a price moves the inventory's row in the Cost Breakdown slide from "Proposed" badge to "Accepted" badge. An advertiser who refuses to ship a brand logo and then ships one a week later sees the Cover slide gain the logo without anyone touching the deck. The Public Share Link is the one exception — its theme and viewer mode are locked at issuance, but its data is still live (a recipient refreshing the link sees the latest numbers).

**Third, the deck changes its tone with the campaign's status.** A Draft plan carries no status pill on the cover and the chrome behaves as if the deck is a work-in-progress preview. A Reviewing campaign carries an amber "Pending approval" pill that names the active stage and the actor it is waiting on. An Approved campaign carries a green lock banner with the approver's name and timestamp, and every Edit deep-link disables. A Rejected campaign greys the cover and the closing slide is replaced by a "Reason for rejection" slide quoting the comment recorded with the rejection. A Completed campaign swaps the forward-looking forecasting language for retrospective wording — "expected to deliver" becomes "delivered", the Expected Delivery chart's title becomes "Actual Delivery", reach reads from the post-flight measurement rather than the forecast. The slide bodies do not change; only the framing labels and the action affordances do.

The page therefore has no version history of its own. Every state it can be in is reproducible from the campaign at a moment in time, so the campaign's history log on the campaign detail page is the single source of audit truth. The page's own job is to render a moment, not to remember a past moment. The Public Share Link is the closest the page comes to versioning — when a planner issues a share link, the link is given a token that survives campaign edits, and a recipient who opens it after the campaign moves to a new state sees the new state under the link's locked theme.

The page never falls back to mock data. If a field the deck would normally render is empty, the deck omits it. If a list the deck would normally show is empty, the deck does not invent rows. The only place the deck shows generic content is the country-census fallback on the Audience Strategy slide, and that fallback is explicitly labelled "no audience targeting was applied; the figures below are country-level census estimates and not a projection of who will be reached".

---

## §4. Page chrome — what is always present

A small set of elements is always on screen, regardless of which campaign is loaded or what the planner did or didn't fill in. These are the page's chrome and they are listed once here so they are not repeated for each slide.

| Chrome element | Purpose | Notes |
|---|---|---|
| Tenant logo (top-left of every slide and every Analytics tab header) | Identifies the company that owns the plan | Reads from the active tenant's `companies.logoUrl`. Always present. Initials tile (two characters in the theme's primary colour) when no logo has been uploaded. Never empty. |
| Slide / Tab toggle | Switch between Presentation and Analytics | Defaults to Presentation. Sticky per user via localStorage. |
| Theme picker | Choose from the four shipped themes or the tenant's branded theme when configured | Default is the tenant's branded theme if configured, otherwise Default. |
| Viewer Mode dropdown | Preview as Internal, Agency, Advertiser or Media Owner | Read-only preview — does not change real permissions. Visible only to Internal and Agency roles; an Advertiser viewing the page does not see the dropdown at all because they have only one mode to be. |
| Export menu | PowerPoint, PDF, Excel, Public Share Link | Today every entry except Public Share Link is a placeholder that raises a toast — the implementation roadmap is in §18. |
| Proposal status badge | Reflects the campaign's current status (Draft, Reviewing, Approved, Rejected, Active, Completed) | Drives the slide-tone changes described in §3. Colour-coded per the main PRD status palette. |
| "Edit plan" deep link in the header | Returns the planner to the wizard | Disabled on Approved and Completed plans; visible-but-disabled with a tooltip ("This plan is approved — edits require re-approval") on Rejected. Hidden from viewers who are not the campaign creator. |
| Per-slide "Open in Campaign Detail" deep link | Lets the reader jump to the matching tab on the campaign detail page | The mapping is in §11. Hidden from Advertiser mode (advertisers do not have access to the campaign detail page). |
| Currency and locale formatting | Every monetary figure is rendered in the campaign's currency (USD, MYR, SGD, INR, AED…). Every date is rendered in the active tenant's timezone. Every distance is km or mi by tenant locale. | The page never falls back to a hard-coded USD or to the browser's locale. The currency code is rendered as a three-letter prefix ("MYR 7,719"), never as a symbol. |
| Print-safe footer on each slide | Plan name · campaign ID · "Generated <timestamp>" · page N of M | Present in both on-screen and exported renderings. The timestamp is the moment the deck was opened, not the moment the campaign was created. |

The chrome is the only common surface. Everything below the chrome is scenario-driven, and the next two sections walk through every scenario.

A note on the Viewer Mode dropdown's intent: it is the *planner's preview tool*, not the recipient's view selector. A planner who is about to send a share link uses Viewer Mode to confirm that an Advertiser will see the rolled-up media cost rather than the fee breakdown, that a Media Owner will see only their own inventory rows, and that the Cinema operator chips render with the correct logos. The dropdown is a quality-control instrument; the actual rendering for a recipient is decided server-side from the recipient's tenant and role.

---

## §5. Presentation view — slides and conditional rendering

The deck is up to twelve slides long. The exact length depends on what the planner entered. Each subsection below names the slide, the question it answers, the data it reads, and the conditional rule that decides what the slide looks like in the worked-example variants A, B and C introduced in §5.0.

### §5.0 The worked example — three variants of one campaign

The same imaginary advertiser, "Spring Beverages 2026", is used as a worked example throughout the rest of the document. Three variants are walked side-by-side wherever the conditional rules diverge.

| Variant | What the planner did in the wizard |
|---|---|
| **A — Coastline Coffee Q3** | Channel: Billboard only. Country: Malaysia. No brand selected. No goal. No targeting (demographics, behaviours, signals, venue types, cinema, geo CSV all left empty). 6 inventories chosen manually, Klang Valley only. Schedule: defaults applied (24/7 grid). |
| **B — Spring Beverages 2026** | Channels: Billboard + Cinema. Countries: India + Malaysia. Brand: "Spring Beverages" with logo and IAB category Food & Drink. Goal: Reach 4 M. Demographics: 25-44, urban, mid-high income. Behaviours: "evening commuters", "cinemagoers". Cinema operators: PVR INOX, Cinépolis (India), GSC, TGV (Malaysia). Geo CSV: 12 POIs across Mumbai and Kuala Lumpur with 1.5 km radius each. 22 inventories, mixed billboard + cinema. Schedule: Commuter preset on billboards; cinema inventories use prime weekend showtime band, 2 spots per loop. |
| **C — Velocity Sports Marathon Hype** | Channels: Billboard + Cinema + Retail. Country: Singapore. Brand: "Velocity Sports" with logo and IAB category Sports. No goal. No demographics, no behaviours. Venue types: shopping malls, transit, cinema lobbies. 14 inventories. Schedule: 4 distinct per-inventory grids — gym retail screens are 5–9 am and 6–10 pm, mall billboards are 11 am – 9 pm, cinemas are weekend prime only with 1.5 spots per loop. |

The point of the three variants is not their realism — it is that they exercise every conditional in the deck. **A** is the *minimum-input* limit, a campaign with only the mandatory wizard fields filled in. **B** is the *typical multi-channel with full targeting* case, exercising every conditional block in the deck. **C** is the *multi-channel with rich per-inventory schedule but sparse audience targeting* case, important because it exercises the conditionals that depend on schedule diversity and venue typing without exercising the conditionals that depend on demographics or behaviours. The doc returns to them inside every slide section so the reader can picture each.

### §5.1 Cover

The cover answers the question "what plan am I about to read". It is always the first slide. It always carries the tenant logo top-left and the plan name in the centre. Everything else on it is conditional.

The brand block in the top-right is rendered only when a brand was selected in Step 1. When rendered, it shows the brand's uploaded logo (a real image from the `brands.logoUrl` field, not an initial-tile or a hard-coded glyph), the brand name and the IAB category as a small line below the name. The page does not fall back to an initials tile for the brand logo — if `brands.logoUrl` is empty, the entire brand block is omitted rather than rendered with a placeholder, because a recognisable brand on the cover is the cover's whole job and an unrecognisable placeholder is worse than no block.

The five KPI tiles below the title are always there but their *content* depends on what was entered. The strip is always five tiles wide so the cover does not reflow. Budget is always present. Total cost is always present. Estimated impressions is always present. The fourth tile is reach when the underlying inventories carry impression data and a per-inventory reach model is available, otherwise duration (the plan length in days) so the strip keeps its five-wide shape. The fifth tile shows the campaign's primary goal if one was set ("Reach 4 M", "Impressions 50 M", "SOV 25 %"), or the channel chip strip ("Billboard · Cinema · Retail") if no goal was set.

The cover photography across the bottom third of the slide is a montage of the top three inventory thumbnails — real photos from the `inventories.images` array, picked by share of impressions. The picker prefers a thumbnail with a landscape orientation, takes the next best on tie. When the plan has fewer than three thumbnail-bearing inventories the montage falls back to a flat colour band in the theme's primary colour with the channel name written across it ("Billboard plan", "Cinema plan", "Multi-channel plan"). There is no Unsplash, Shutterstock or other stock fallback — the cover photography is either the planner's real inventory or the theme's primary colour, nothing in between.

The lower-right corner of the cover carries one further logo, the *agency logo* when the planner's company is an agency or the *advertiser logo* when the planner's company is a media owner selling directly. The logo identifies the buying side; the tenant logo top-left identifies the selling or planning side. When both companies are the same tenant (an in-house brand running a deck for its own marketing team) the lower-right corner is omitted to avoid duplicating the top-left logo.

| Cover element | Variant A | Variant B | Variant C |
|---|---|---|---|
| Tenant logo top-left | Tenant logo present | Tenant logo present | Tenant logo present |
| Brand block top-right | **Omitted** | "Spring Beverages" + logo + "Food & Drink" | "Velocity Sports" + logo + "Sports" |
| Channel chips strip | Billboard | Billboard · Cinema | Billboard · Cinema · Retail |
| KPI tile 1 (Budget) | MYR 80,000 | INR 12,000,000 | SGD 90,000 |
| KPI tile 2 (Cost) | MYR 74,500 | INR 11,420,000 | SGD 88,200 |
| KPI tile 3 (Impressions) | 14.2 M | 320 M | 18.6 M |
| KPI tile 4 (Reach / Duration) | Duration: 28 days (no reach model) | Reach: 3.2 M | Reach: 0.9 M |
| KPI tile 5 (Goal / Channels) | Billboard | Goal: Reach 4 M | Billboard · Cinema · Retail |
| Cover photography | Three Klang Valley billboard thumbnails | Top three thumbnails across India + Malaysia | Top three thumbnails across Singapore |
| Lower-right logo | Tenant is the agency — agency logo omitted (same logo as top-left) | Agency logo (the planner's company) | Agency logo (the planner's company) |

### §5.2 Plan Snapshot

The snapshot is the slide a reader can absorb in ten seconds. It always shows: countries, cities, number of inventories, number of operators, plan duration, plan budget. It conditionally shows a primary-goal tile (omitted when no goal was set) and four headline KPIs whose ordering depends on whether a goal was set.

When a goal was set, the four KPIs lead with the goal's metric — impressions for an Impressions goal, reach for a Reach goal, SOV for a SOV goal — followed by the remaining three in a fixed order (impressions, reach, frequency, CPM, eCPM with the goal metric removed from the list). The lead KPI carries a progress bar showing actual vs. goal. When no goal was set, the four KPIs lead with reach and impressions in that order, because reach is what an advertiser asks for first when they have not articulated a metric, followed by frequency and CPM.

The snapshot also carries a one-line "plan in plain English" sentence below the KPIs, generated from the campaign's actual data. The sentence has a fixed template: "Reaches up to {reach} unique adults across {city-count} cities in {country-list} between {start} and {end}". When reach is not computable the template degrades to "{impressions} estimated impressions across {city-count} cities in {country-list} between {start} and {end}". The sentence is one of the few places on the deck where generated copy appears, and it is intentionally formulaic — a planner who reads it back to a client should not be surprised by what it says.

| Snapshot element | Variant A | Variant B | Variant C |
|---|---|---|---|
| Goal tile | Omitted | "Reach 4 M" with progress bar (3.2 M of 4 M, 80 %) | Omitted |
| Lead KPI | Reach | Reach (goal-driven, 3.2 M) | Reach |
| KPI 2 | Impressions | Impressions | Impressions |
| KPI 3 | Frequency | Frequency | Frequency |
| KPI 4 | CPM | CPM | CPM |
| Country / city counts | Malaysia · 1 city | India · 4 cities; Malaysia · 2 cities | Singapore · 1 city |
| Inventory / operator count | 6 inventories · 2 operators | 22 inventories · 5 operators | 14 inventories · 4 operators |
| Plain-English sentence | "14.2 M estimated impressions across 1 city in Malaysia between 1 Jul and 28 Jul 2026" | "Reaches up to 3.2 M unique adults across 6 cities in India and Malaysia between 5 Apr and 4 Jul 2026" | "Reaches up to 0.9 M unique adults across 1 city in Singapore between 12 Aug and 25 Aug 2026" |

### §5.3 Brand Context — conditional, only when a brand was chosen

The brand context slide is rendered only when a brand was selected in Step 1. When rendered, it shows the brand logo at large size on the left third of the slide, the brand name and the IAB category in the centre, the parent advertiser when one is recorded on the brand, and a one-line audience-affinity descriptor on the right derived from the IAB code via a small static lookup table maintained in the repo (`shared/iab-affinity.ts`). No external call is made; the lookup is intentionally generic so the slide stays honest about what it can claim.

The descriptor is keyed by IAB tier-1 plus tenant country. For Spring Beverages's Food & Drink category in India + Malaysia it reads "Audiences indexing high on Food & Drink also over-index on commuter and cinema dayparts in IN and MY"; for Velocity Sports's Sports category in Singapore it reads "Audiences indexing high on Sports also over-index on weekend retail dwell in SG". The line is a starter sentence, not a research finding, and the page deliberately says so on hover ("Generic affinity descriptor based on IAB category and target country; not a research finding for this brand").

A small chip strip under the descriptor lists the IAB tier-2 sub-categories the brand sits in (Food & Drink → Coffee, Tea, Soft Drinks). The chips are sourced from the brand's `brands.iabTier2` array; when only tier-1 is recorded the chip strip is omitted.

In Variant A the slide is **not rendered at all** because no brand was selected. The deck does not insert a placeholder in its place — the next slide simply follows the Plan Snapshot.

| Brand Context element | Variant A | Variant B | Variant C |
|---|---|---|---|
| Whole slide rendered? | **No** | Yes | Yes |
| Brand logo (large, left) | — | Spring Beverages logo | Velocity Sports logo |
| IAB tier-1 | — | Food & Drink | Sports |
| IAB tier-2 chips | — | Coffee, Tea, Soft Drinks | Running, Fitness, Marathon |
| Parent advertiser | — | Springfield Beverages Group | Velocity Holdings |
| Affinity descriptor | — | "Audiences indexing high on Food & Drink also over-index on commuter and cinema dayparts in IN and MY" | "Audiences indexing high on Sports also over-index on weekend retail dwell in SG" |

### §5.4 Channel Mix — conditional, only when more than one channel was selected

The channel mix slide is rendered only when the wizard's Media Channels list has more than one entry. The slide's job is to tell the reader, in one glance, how the plan is split between channels. For each selected channel it shows: a channel-coloured header band, the count of inventories in that channel, the share of plan budget, the share of estimated impressions, the share of estimated reach when reach is computable, and a row of the top three inventory thumbnails for the channel. The channel colours match the channel colours used on the Geographic Plan slide map pins so a reader who has been told "yellow is cinema" on one slide does not have to re-learn it on the next.

When there is only one channel the slide is omitted entirely — there is nothing to compare, so showing a single block would be visual noise. This is the most-asked-about omission in the deck and worth naming explicitly: the deck does not render a "single-channel mix" slide because a 100 % bar is not a mix.

| Channel Mix element | Variant A | Variant B | Variant C |
|---|---|---|---|
| Whole slide rendered? | **No** (single channel) | Yes | Yes |
| Block 1 | — | Billboard — 15 inv · 47 % budget · 38 % impressions | Billboard — 6 inv · 34 % budget · 31 % impressions |
| Block 2 | — | Cinema — 7 inv · 53 % budget · 62 % impressions | Cinema — 4 inv · 41 % budget · 35 % impressions |
| Block 3 | — | — | Retail — 4 inv · 25 % budget · 34 % impressions |
| Thumbnails per block | — | Top three by impression share | Top three by impression share |

### §5.5 Geographic Plan

The geographic plan slide always renders for any plan with at least one inventory. It shows a country / city table with five columns (country, city, # inventories, est. impressions, est. cost, CPM) populated from a server-side rollup of the inventories array. The rollup is sorted by impressions descending so the highest-delivery city is at the top. Cost in the table is rendered in the campaign's currency, the same as the cover.

Below the table sits a static map preview generated at request time — a Mapbox static-image of the inventory pins, one pin per inventory, coloured by city. The static image is requested once per page load and cached for the session; the live interactive map lives on the campaign detail Targeting tab, accessed via the "Open in Campaign Detail" deep link in the slide's footer.

When a geo CSV was uploaded in Step 3, an extra block sits between the table and the map: a one-line summary ("12 coordinates uploaded — 9 with inventory matches in radius, 3 with no inventory in radius") and the names of the three unmatched POIs. The unmatched POIs are named in full because they are the actionable insight — the planner reading them knows immediately which radii were wasted and can adjust the CSV. When no CSV was uploaded the extra block is omitted; the slide reads as a clean country/city overview with the map.

The full per-POI breakdown — coordinate, radius, include/exclude flag, count of matched inventories, names of those inventories — lives in the Analytics view's Geography Targeting tab (§6.6), so the deck does not have to fit a long table on one slide. A "View full POI breakdown" deep link in the slide footer takes the reader directly there.

| Geographic Plan element | Variant A | Variant B | Variant C |
|---|---|---|---|
| Country/city table rows | 1 (Malaysia / Klang Valley) | 6 (Mumbai, Pune, Bangalore, Delhi, Kuala Lumpur, Penang) | 1 (Singapore / Singapore) |
| Pin count on static map | 6 | 22 | 14 |
| Pin colouring | By city (single colour) | By city (six colours) | By city (single colour) |
| POI block | Omitted | "12 uploaded · 9 matched · 3 unmatched" + names | Omitted |
| Transit footprint line (channel-conditional) | Omitted (no Transit channel) | Omitted (no Transit channel) | Present — "Coverage on Singapore MRT Downtown Line concourse" |

### §5.6 Featured Inventories

The featured inventories slide is the photographic showcase. It always renders for any plan with at least one inventory and shows up to six photo cards. Each card carries: a real `inventories.thumbnailUrl` photo (not a placeholder), the inventory name, the full address, the venue type (IAB OpenOOH tier-3 wording), the format and the physical dimensions, daily and monthly impressions, the cost for the plan period, the CPM, and the operating hours.

Cards are picked by share of impressions, so the most-delivering inventories are the ones the advertiser sees. Ties are broken first by share of cost (more expensive shown first, on the theory that expensive screens are the ones a planner most needs to justify) and then by alphabetical order on the inventory name (deterministic so repeat opens of the deck do not reorder cards).

When the plan has more than six inventories a "+ N more in the analytics view" footer is rendered, deep-linked to the Inventory Details tab. The footer is intentionally precise about the count rather than vague ("+ 16 more in the analytics view", not "more inventories available") so the advertiser is not left guessing whether the plan has eight inventories or eighty.

When no inventory has a thumbnail uploaded — a degenerate case for a cold catalogue, and the case Variant A nearly hits — the cards fall back to a venue-type glyph (a billboard glyph for Classic, a popcorn glyph for Cinema, a shopping-bag glyph for Retail, a transit-vehicle glyph for Transit, a screen glyph for Digital Screen and Network, an audio-wave glyph for Radio, an event glyph for Experiential) on a flat colour band in the theme's primary colour. The other six fields still render. The fallback is per-card, not per-slide — a plan with five thumbnail-bearing inventories and one without gets five photos and one glyph.

**Pagination.** When the plan has more than six inventories the deck does not stop at the six top cards; it paginates. The slide is emitted as multiple sub-slides numbered 5.6.1, 5.6.2, 5.6.3 and so on, each carrying up to six cards in a 3×2 grid, sorted by daily impressions descending. The pagination is the same in both on-screen and exported renderings, so a printed PowerPoint matches the on-screen deck exactly. The footer of every sub-slide reads "Inventory snapshots — page N of M".

| Featured Inventories element | Variant A | Variant B | Variant C |
|---|---|---|---|
| Card count | 6 | 6 cards on slide 5.6.1, 6 on 5.6.2, 6 on 5.6.3, 4 on 5.6.4 (22 inventories total) | 6 cards on slide 5.6.1, 6 on 5.6.2, 2 on 5.6.3 (14 inventories total) |
| Photo presence | Most billboards lack thumbnails — fallback glyph on 4 of 6 | All 22 cards have real photos | All 14 cards have real photos |
| Sub-slide count | 1 | 4 | 3 |
| "+ N more" footer text | Omitted (no overflow) | "Inventory snapshots — page 1 of 4" through "page 4 of 4" | "Inventory snapshots — page 1 of 3" through "page 3 of 3" |

### §5.7 Audience Strategy — four conditional blocks plus a fallback

The audience strategy slide is the page's most variable. It is composed of four blocks, each independently rendered. The slide's job is to tell the reader who the plan is for; when the planner has not articulated who the plan is for, the slide says so honestly rather than inventing an audience.

**Demographics block.** Rendered only when at least one of `targeting.demographics.{ageGroups, gender, income, interests}` is non-empty. Bars show the actual selections, not a generic 25-34 mock. Each bar is labelled with its targeting value ("25–34", "35–44", "Urban", "Mid-high income", "Coffee enthusiasts") and the relative weight when the wizard captured weights (the wizard does not always capture weights — when it does not, the bars render at equal height). Interests carry a small tag glyph; age and income carry no glyph. The block header reads "Demographic targeting"; when only one of the four sub-fields is present the header narrows to that sub-field's name ("Age targeting", "Gender targeting", "Income targeting", "Interest targeting") so the reader is not misled into thinking the block is more sophisticated than it is.

**Behaviours and Signals block.** Rendered only when `targeting.audienceBehavior` or `targeting.signals` is non-empty. Each entry is a chip with a one-line description sourced from the same in-repo lookup table the brand-affinity descriptor uses. Behaviours and signals are visually separated by a thin horizontal rule inside the block, with behaviours on top and signals below, because behaviours are typically what the planner *chose* (evening commuters) and signals are typically what an external feed *suggests* (high coffee-shop dwell) — the reader benefits from seeing the source.

**Venue Types block.** Rendered only when `targeting.environment` is non-empty. A stacked bar shows the contribution of each venue type to total estimated impressions. The bar is labelled with venue-type names from the IAB OpenOOH tier-2 vocabulary (Transit, Retail, Outdoor, Indoor, Cinema, Healthcare) so a reader from a different region recognises the term. When the plan picks venue types the planner did not also pick inventories from (a real case — the planner ticks "Cinema" but does not pick any cinema inventory in Step 4), the venue type is still listed but its bar segment is grey and labelled "0 impressions — no matching inventory selected", which is the page's way of flagging an actionable mistake.

**Cinema block.** Rendered only when Cinema is one of the selected channels and `targeting.cinema` has at least one operator, genre, showtime band, rating or film. Operators appear as logo chips (using `cinemaOperators[code].logoUrl` — real logos from the catalogue, no fallback initials tile for cinema operators because cinema operators are a recognisable consumer brand and an unrecognisable placeholder defeats the slide's purpose). Genres and ratings appear as text chips; showtime bands appear as a small bar showing impression contribution. Specific film picks (the planner pinpointed "Avatar 3 opening weekend") appear as a separate chip strip with a film glyph and the release date.

**Fallback country-census panel.** When **all four blocks are empty** — the Variant A case — the slide does not render the four-block layout at all. Instead it renders a single neutral panel: a country-census distribution (age × gender × income) sourced from a static census lookup keyed by country, with a clear line at the top stating "No audience targeting was applied. The figures below are country-level census estimates and not a projection of who will be reached." The honesty here is deliberate — the page does not pretend the planner targeted an audience they did not.

| Variant | Audience Strategy slide content |
|---|---|
| A | Fallback panel — Malaysia country-census distribution with the "no targeting applied" header line. No demographics, behaviours, venue or cinema blocks. |
| B | All four blocks rendered. Demographics: 25-44 / mid-high income. Behaviours: evening commuters · cinemagoers. Venue types: cinema · billboard street furniture. Cinema: PVR INOX · Cinépolis · GSC · TGV chips with logos, Weekend Prime band-impression bar, no genre or rating chips because none were set. |
| C | Two blocks rendered. Venue types: mall · transit · cinema lobby. Cinema: reduced to a single line "Cinema lobbies targeted across SG" because no operators were picked. Demographics and behaviours blocks omitted. |

### §5.8 Daypart and Schedule

The daypart slide is the one the previous spec was weakest on. The current page renders a hard-coded "6-9 AM High / 9-5 PM Medium / 5-8 PM High / 8-6 AM Low" strip regardless of what the planner did in Step 5. The rewritten slide replaces that with three blocks computed from the actual per-inventory schedules in `optimization.schedule.inventorySchedules[].grid`.

**Plan-level 7×24 heatmap.** A single heatmap aggregates every selected inventory's grid. Each cell's intensity is the percentage of inventories active in that hour-of-week. A planner who set Commuter on every inventory sees two clear vertical bands (one for the morning rush, one for the evening rush) plus a dim background outside them; a planner who set 24/7 on every inventory sees a uniform shade; a planner with a mix sees the union, with hot zones where multiple presets overlap. The heatmap is rendered as an SVG so it survives PowerPoint export without quality loss.

**Patterns row.** A chip per preset detected across the inventories — Commuter, Business Hours, Weekend, Nightlife, 24/7, Custom — each with a count of inventories using it. A planner who used Commuter on the billboards and a custom grid on the cinemas sees "Commuter · 15" and "Custom · 7". The chip ordering is by inventory count descending; ties are broken by preset name alphabetically. The patterns row is a one-glance audit of "did the planner remember to set schedules" — a row that reads "24/7 · 22" tells a reader at a glance that no schedule has been customised, which is sometimes intentional (network buys) and sometimes a mistake the planner needs to fix.

**Multi-spot summary.** One line that reads "X inventories run with more than 1 spot per loop (avg Y spots/loop)" plus up to three "outlier" mini-heatmaps for the inventories whose schedules deviate most from the plan-average grid. Outliers are picked by the L1 distance between each inventory's grid and the plan-average grid; the top three by distance are surfaced. Each outlier carries a one-sentence caption derived from the data, not hand-written ("PVR Phoenix Mumbai — Friday & Saturday evenings only · 2 spots/loop", "Mid Valley LED — every weekday 11 am to 9 pm · 1 spot/loop"). The captions follow a fixed template — "{inventory-name} — {detected-pattern} · {spots-per-loop} spots/loop" — so they read consistently from plan to plan.

A small footer on the slide reads "See the DOOH Schedules tab in the analytics view for the per-inventory grid" with the tab name as a deep link. This pointer matters because a media owner reading the deck will frequently want to drill into the grid for *their* inventory, and the analytics tab is where they do it.

| Variant | Daypart slide content |
|---|---|
| A | Heatmap is a uniform full shade (every inventory is 24/7 by default). Patterns row reads "24/7 · 6". Multi-spot summary reads "0 inventories with more than 1 spot per loop". No outliers — every grid is identical to the plan average. |
| B | Heatmap shows two bright commuter bands (6–9 am and 5–8 pm weekdays) plus a hot Friday/Saturday-evening strip from the cinema inventories. Patterns row reads "Commuter · 15", "Custom · 7". Multi-spot summary reads "7 inventories run > 1 spot per loop, avg 2.0". Outliers are PVR Phoenix Mumbai, Cinépolis Pune and TGV Sunway Pyramid. |
| C | Heatmap shows three distinct bright zones (gym morning + gym evening, mall daytime, cinema weekend prime). Patterns row reads "Custom · 14". Multi-spot summary reads "4 inventories run > 1 spot per loop, avg 1.5". Outliers are the three gym retail screens and one cinema. |

### §5.9 Expected Delivery Over Time — adaptive bins

The page has not historically carried a delivery-over-time chart. The rewritten slide adds one, using the same adaptive-binning helper the dashboard's Sales Performance widget already uses. The slide's job is to tell the reader when the impressions are going to land, not just how many in total. An advertiser planning a product launch wants to know whether the bulk of the delivery is in the launch week or smeared evenly across the flight.

| Plan duration | Bin granularity |
|---|---|
| ≤ 14 days | Daily |
| 15 to 90 days | Weekly |
| 91 to 365 days | Monthly |
| > 365 days | Quarterly |

The chart plots estimated impressions per bin as a bar chart with reach overlaid as a line when reach is computable. The bin labels are real ISO date ranges ("27 Apr – 03 May", not "Week 1") so a reader can tie a peak to an external event (a holiday, a product launch, a sports fixture) without having to count weeks from the start. The footer reports peak period and total period count ("Peak: 11–17 Aug · 26 weekly bins"). When the plan period crosses a calendar year the bins are still aligned to ISO weeks rather than artificially split at the year boundary — a week that runs Mon 28 Dec to Sun 3 Jan is one bin, not two.

When the campaign is Completed, the slide title changes from "Expected delivery over time" to "Actual delivery over time" and the bars are populated from the post-flight measurement on `deliveries[].actualImpressions` rather than the forecast. The bin granularity stays the same — daily for short campaigns, weekly for medium, monthly for long, quarterly for multi-year — so a reader comparing a Completed deck with the deck they saw at Approved time sees the bars in the same shape.

| Variant | Bin granularity | Bin count | Lead pattern |
|---|---|---|---|
| A (28 days) | Weekly | 4 bins | Even delivery — flat bars |
| B (91 days) | Monthly | 3 bins | Slight ramp — month 1 < month 2 ≈ month 3 |
| C (14 days) | Daily | 14 bins | Two peaks — weekend 1 and weekend 2 (cinema-driven) |

### §5.10 Cost Breakdown

The cost breakdown slide always renders. It opens with an *inventory-type ladder* — Classic, Digital Screen, Transit, Retail, Network, Radio, Experiential, Cinema, Mobile — with cost, share of cost, impressions and CPM per type. Types not present in the plan are omitted from the ladder; the ladder grows and shrinks with the plan rather than always showing the full nine rows. The ladder is sorted by cost descending so the largest spend is at the top, and the share-of-cost column is a thin horizontal bar visualisation so a reader can see the dominant cost without reading the numbers.

Below the ladder sits a media-cost-vs-fees table that honours the existing `canSeeCosting` and per-fee `includeInPlan` gates from the main PRD §10.2. The exact behaviour by viewer:

- **Internal** — every fee, with full breakdown by fee type, by company that levies the fee, and by which inventory or city or campaign-level it applies to.
- **Agency** — agency's own fees rendered with full breakdown; another agency's internal-only fees never appear. The table separates media cost (sum of the ladder) from fees (sum of the fee block) and reports the combined eCPM.
- **Media Owner** — only the rows for the inventory the media owner sells; the table collapses to those rows and the fees that apply to them.
- **Advertiser** — a single "Total cost" row, with eCPM. The ladder is collapsed to one row labelled "Media" and the fees block is collapsed into the total. The Advertiser does not see the individual fee names; they see one number.

A carbon footprint row sits at the bottom of the slide and is rendered only when at least one inventory in the plan has `hasCo2Data = true`. When carbon is rendered it shows total kg CO₂e for the plan, the per-thousand-impression equivalent (g CO₂e per CPM), and a one-line context sentence comparing the total to a recognisable benchmark ("equivalent to a London-to-Paris flight per person" or "equivalent to 12 km driven in a small petrol car"). The context sentence is sourced from a small static benchmark table; it is intentionally generic and is labelled as such on hover.

| Variant | Cost Breakdown rows |
|---|---|
| A | Ladder: Classic only (one row). No fee block (default plan, no custom fees). No carbon row (Klang Valley billboards have no CO₂ data in the catalogue today). Total eCPM = CPM. |
| B | Ladder: Classic, Digital Screen, Cinema (three rows sorted by cost). Fee block has agency fee (visible to agency, hidden from advertiser). Carbon row present (cinema inventories carry CO₂ data); total 142 kg CO₂e for the plan, 0.44 g/CPM. |
| C | Ladder: Classic, Digital Screen, Retail, Cinema (four rows). No custom fees. Carbon row present; total 38 kg CO₂e for the plan. |

The slide footer carries a deep link to Price Management ("See the proposed and counter-offer prices in Price Management") so a reader who wants to know the negotiation state can jump straight there. When the campaign is Approved, the link reads "View the approved prices in Price Management" and the underlying page renders in read-only mode.

A note on currency: when the plan currency differs from the active tenant's local reporting currency, the Cost Breakdown slide and the Costing tab in the analytics view show an FX disclosure line — "Prices shown in MYR; converted to USD using the FX rate as of {start-date} for tenant reporting". The FX rate used is the rate on the campaign's start date, captured once and held for the life of the campaign so that subsequent FX movements do not retroactively change the deck's numbers. When the plan currency matches the tenant currency, no FX disclosure renders.

### §5.11 Why This Plan Works — closing slide

The closing slide is a synthesis. It reads back the three strongest data-derived facts the deck has actually shown — never hand-written copy. Each fact is generated from a different region of the deck so a reader gets three angles rather than three restatements of the same point: one fact about scale (impressions or reach), one about efficiency (CPM relative to a benchmark, or share of voice), one about composition (channel mix, daypart concentration, geographic coverage or inventory quality).

Examples drawn from the worked variants:

| Variant | The three facts |
|---|---|
| A | "Reaches an estimated 1.4 M unique viewers across Klang Valley over four weeks", "USD 6.20 eCPM, 12 % below Klang Valley billboard benchmark", "100 % of impressions delivered on owned street-furniture inventory — no programmatic spill" |
| B | "Reaches 3.2 M unique adults across Mumbai, Pune, Bangalore, Delhi, Kuala Lumpur and Penang", "Concentrated on commuter and weekend prime cinema dayparts where Food & Drink audiences over-index 2.3×", "INR 4.10 eCPM, 18 % below Food & Drink category benchmark" |
| C | "Reaches 0.9 M unique adults across Singapore over two weeks", "Spans gym morning and evening dayparts, retail mid-day and cinema weekend prime — six distinct attention windows", "Cinema lobby coverage on every Velocity Sports rival's release weekend" |

Each line is computed at render time from the campaign's actual data — the impressions number is the same impressions number on the cover, the eCPM is the same eCPM on the cost breakdown, the benchmark is the same benchmark from the in-repo benchmark table. The deck never invents a benchmark — when no benchmark is available for the segment, that line is omitted and the slide ends with two facts instead of three. The slide is the only place on the deck where the page composes language; everywhere else it shows data verbatim.

When the campaign is Rejected the closing slide is replaced by a "Reason for rejection" slide quoting the comment recorded with the rejection. The replacement is the only deck-level slide substitution besides the Empty Plan fallback; in every other case the deck omits or includes a slide, never swaps it.

### §5.12 Empty Plan — single fallback

When the campaign has zero selected inventories — common in the few minutes between Submit Plan and the planner returning to add inventory — slides §5.4 onward are replaced with a single "Plan in progress" slide that links back to the wizard. The cover and snapshot still render, because they have something to show (name, dates, channels). The empty-plan fallback is the only place in the deck where a slide *list* is replaced rather than individual slides omitted; it is the deck's way of saying "this plan does not yet have a body — come back when it does", politely and without rendering a string of blank slides that would look like a broken page.

The "Plan in progress" slide carries the same chrome as every other slide (tenant logo, status badge, footer) so it does not look like an error. Its body is a single paragraph and a CTA: "This plan has no inventory yet. Open the wizard to add inventory in Step 4."

---

## §6. Analytics view — the seven tabs and what each one renders against the wizard

The Analytics view is laid out as a fixed seven-tab strip. Tabs are *persistent* — every tab is always rendered — because the Analytics view is a reference grid and a missing tab would be confusing. When a tab has no data to show, its body explains why and links back to the wizard step that would populate it. The contrast with the Presentation view's "show what is there, hide what is not" rule is intentional: in a deck, an empty section interrupts a story; in a reference grid, a known-empty tab is a useful piece of information.

| Tab | Always shows | Conditional content |
|---|---|---|
| Campaign Plan | Campaign name, dates, currency, status, planner, agency, advertiser, totals | Brand block (omitted when no brand was selected). Goal row (omitted when no goal). External ID row (omitted when not entered). Recipient list (omitted until the plan has been shared at least once). |
| Inventory Details | Every inventory in the plan, one row per inventory, with a thumbnail column at the front and full address columns at the back | A row-level expander reveals the inventory's 7×24 schedule grid and every Schedule Entry name + spots-per-loop + spots-per-hour. The expander is empty for inventories that took the default 24/7 schedule. |
| Costing | Every inventory's pricing including discounts, bonus and the resulting net cost. Per-city and per-inventory-type rollups at the bottom | Gated by `canSeeCosting`. When the viewer is not allowed, the tab body says so and links to the campaign owner. The FX disclosure line appears at the top of the tab when the plan currency differs from the tenant currency. |
| Operation Details | Every inventory's operating hours and active hours per week, computed from the schedule grid | Inventories with no schedule entries (default 24/7) show "168 h / week"; inventories with schedule entries show the computed total. A "deviation" column highlights inventories whose active hours fall below the inventory's published operating hours (an actionable mistake — the planner is paying for a window the screen does not run). |
| DOOH Schedules | Per-inventory mini-heatmap (7×24, green where active, intensity = spots per loop) | A toggle at the top switches between three layouts: "By inventory" (default), "By preset", "Plan-level rollup". A search filter narrows by city, type or operator. Inventories with multiple Schedule Entries get one mini-heatmap per entry, each labelled with the entry name and spots-per-loop. |
| Geography Targeting | The country/city table from §5.5 | When a CSV was uploaded: the full per-POI breakdown. When no CSV was uploaded: the tab body shows the demographic / behaviour / signal targeting summary instead of mock POIs, with a one-line note that POIs were not used and a deep link to Step 3. |
| Targeting | The full targeting object — demographics, environment, behaviours, signals, cinema | Each block is rendered only if non-empty. When everything is empty, the tab body shows a single neutral message ("No audience targeting was applied to this plan") and a deep link back to Step 3 of the wizard. |

The full descriptions of each tab follow.

### §6.1 Campaign Plan tab

The Campaign Plan tab is the analytics view's cover. It lists every campaign-level field a reader would want to see on one screen: campaign name, External ID (when entered), plan dates, currency, status, the planner who created the plan, the agency, the advertiser, the brand (when selected), the IAB category (when a brand is selected and has a category), the goal (when set), the carbon budget (when set), the recipient list (every party the plan has been shared with), the version number, and the totals row (budget, cost, impressions, reach, frequency, CPM, eCPM). The tab is the only place in the analytics view that mirrors the deck's cover; everywhere else the analytics view shows more than the deck.

The recipient list is the tab's most interactive element. Each recipient row carries the recipient's name, company, email, the timestamp the share link was issued, the timestamp the link was last opened (when telemetry is available), and a Revoke button. Revoking a recipient kills their share link without affecting other recipients; the row remains on the list with a "Revoked" badge.

When the campaign is Approved the totals row carries a green check; when Rejected, a red cross; when Active, a blue dot; when Completed, a grey check. The status badge in the analytics tab is intentionally smaller than the badge on the deck's cover — the deck is for the recipient, the analytics tab is for the planner who already knows the status.

### §6.2 Inventory Details tab

The Inventory Details tab is the spreadsheet a planner opens to see every inventory in the plan at once. One row per inventory. The column order is fixed:

1. Thumbnail (40 × 40 px) — picked from `inventories.thumbnailUrl`; venue-type glyph fallback when missing.
2. Inventory name.
3. Media owner (the company that sells the inventory).
4. Media owner logo — small, beside the name. Initials tile when missing.
5. Inventory type (Classic, Digital Screen, etc.).
6. Format (32-sheet, Spectacular, Concourse LED, etc.).
7. Dimensions (W × H, in metres).
8. Resolution (when digital — e.g. "1920 × 1080").
9. Venue (the building / location name).
10. Venue type (IAB OpenOOH tier-3).
11. City.
12. Country.
13. Latitude / Longitude.
14. Full address.
15. Operating hours per week.
16. Active hours per week (computed from the schedule).
17. Daily impressions.
18. Monthly impressions.
19. Plan-period impressions.
20. CPM.
21. Plan-period cost.
22. Carbon per play (when `hasCo2Data`).
23. Reservation state (Pending, Hold Requested, Reserved, Booked, Released, Declined, Expired).
24. Negotiation state (Rate Card, Proposed, Counter, Accepted, Declined, Expired).

A row-level expander reveals the inventory's 7×24 schedule grid and every Schedule Entry with its name, preset (or "Custom"), spots-per-loop, spots-per-hour (when set), and the entry's contribution to the inventory's active-hours total. The expander is empty for inventories that took the default 24/7 schedule; the row's "Schedule" column reads "Default 24/7" in that case so the reader knows the empty expander is intentional rather than a bug.

A filter strip above the table narrows by city, type, format, media owner, reservation state and negotiation state. A search box accepts inventory ID, inventory name and venue name. The filter strip is sticky as the user scrolls. An Export button at the top of the tab exports the filtered set to Excel — when the filter is empty the full plan is exported.

### §6.3 Costing tab

The Costing tab is the planner's pricing-audit grid. It lists every inventory with its rate-card price, any negotiated price (proposed, counter, accepted), the applied discount or bonus, the schedule-period cost, the eCPM and the fee allocation. At the bottom of the tab sit three rollups: by city, by inventory type, by media owner. Each rollup carries cost, share of cost, impressions and CPM.

The tab is gated by `canSeeCosting`. When a viewer is not allowed the tab body renders a neutral message ("Costing is hidden from your role on this plan; the plan owner can grant access in Admin Console") and a Contact button that opens a mailto pre-filled with the planner's address. The tab is never replaced with a blank screen; the gated message is the explicit signal.

The FX disclosure line lives at the top of the tab when the plan currency differs from the tenant reporting currency: "Prices below are in MYR. Tenant reports in USD using the FX rate as of 5 Apr 2026 (1 USD = 4.71 MYR), captured at campaign start." The FX rate is captured once at campaign-start time and held — subsequent FX moves do not retroactively change the tab's numbers. The tab also carries a small "Currency" toggle that switches between plan currency and tenant reporting currency for display purposes only; the underlying data does not change.

### §6.4 Operation Details tab

The Operation Details tab is the per-inventory operational audit. Each row carries the inventory name, the operating hours as published in the catalogue, the active hours per week as computed from the schedule grid, the deviation (active hours − operating hours; negative when the schedule asks for fewer hours than the inventory runs, zero when they match), the spots-per-loop (averaged when the inventory has multiple schedule entries) and the loop length (in seconds).

The deviation column is the actionable one. A negative deviation is normal (the planner is buying only a portion of the inventory's runtime); a positive deviation is impossible by construction (the schedule grid is clipped to operating hours on save); a zero deviation tells the planner the inventory will run the ad every hour it is on.

### §6.5 DOOH Schedules tab

The DOOH Schedules tab is the change with the most operational impact. The current page renders a hardcoded weekly calendar with two static rows. The rewritten tab is the only place in the product where the per-inventory schedule grid is reproduced visually — and is therefore the place a media owner reviews "did the planner really want my screen on at 3 a.m. on Tuesday".

A toggle at the top switches between three layouts:

- **By inventory** (default) — one mini-heatmap per inventory, in the same order as the Inventory Details tab. Each mini-heatmap is 7 columns × 24 rows, green where the schedule is active, intensity proportional to spots-per-loop (more spots = darker green). Inventories with multiple Schedule Entries get one mini-heatmap per entry, stacked vertically under the inventory name, each labelled with the entry name and spots-per-loop. A search filter narrows by city, type or operator; the search filters the inventory list, not the heatmap, so the layout stays consistent.

- **By preset** — inventories sharing the same preset are grouped, one heatmap per group with the inventory names listed underneath. The heatmap is the preset's grid (not a per-inventory grid), so the reader sees the canonical shape of "Commuter" once and the count of inventories using it once. Inventories with a Custom preset are grouped under a single "Custom" header, each listed with a thumbnail of its own grid.

- **Plan-level rollup** — the same heatmap the deck §5.8 shows, full-screen, with no inventory list. Useful for a media owner who wants to confirm "is my screen part of the bright zone or the dim zone".

The tab renames itself "Audio Schedules" when the plan contains only Radio inventories; the control is the same, just relabelled. When the plan contains a mix of DOOH and Radio inventories the tab keeps its DOOH name and the Radio inventories appear in the by-inventory list with an audio-wave glyph instead of a screen glyph.

### §6.6 Geography Targeting tab

The Geography Targeting tab is the change with the most planner-facing impact. The current page shows ten mock Pavilion-KL POIs even when the planner uploaded a different CSV. The rewritten tab uses the real CSV and reports per-POI inventory match counts.

The tab opens with the country/city table from §5.5 at the top, so a reader who landed on the tab from a deep link sees the same anchor they saw on the deck. Below the table sits the full per-POI breakdown when a CSV was uploaded — name, type, lat, lng, radius in metres, include/exclude flag, count of matched inventories, names of matched inventories (collapsed under a row expander when more than five), status badge ("✓ matched" green when at least one inventory in radius, "⚠ no inventory in radius" amber when zero, "⛔ excluded" grey for exclusion POIs). A summary line above the table reports the totals: "12 POIs uploaded · 9 matched · 3 no-match · 0 excluded".

When no CSV was uploaded the per-POI table is replaced by the demographic / behaviour / signal targeting summary — a compact rendering of the targeting object so the reader gets something useful instead of a blank tab. A one-line note above the summary reads "No geo CSV was uploaded; the targeting below is the audience-targeting summary instead. Upload a CSV in Step 3 to see per-POI breakdown here." The link to Step 3 is live for planners; for non-planner viewers it renders as inert text.

### §6.7 Targeting tab

The Targeting tab is the full dump of the targeting object — demographics, environment, behaviours, signals, cinema. Each block is rendered only if its sub-field is non-empty; the blocks render in a fixed order so a reader who has seen the tab before knows where to look. Demographics renders age groups, gender, income, interests, each as a bar list with the relative weight (when captured). Environment renders venue types as a chip list. Behaviours renders the audience-behaviour chips. Signals renders the signal chips separately from behaviours, with a small "signal source" glyph beside each chip identifying which signal feed it came from. Cinema renders operators (with logos), genres, showtime bands, ratings and specific films.

When everything is empty the tab body shows a single neutral message ("No audience targeting was applied to this plan") and a deep link back to Step 3 of the wizard. The Targeting tab and the Geography Targeting tab are the two analytics tabs that swap their bodies when empty; every other tab keeps its structure and shows the field-level empty state in place.

---

## §7. Common vs Dynamic — the at-a-glance matrix

Every visible element on the page is either common chrome (always rendered) or dynamic (depends on a wizard input). The matrix below names every element and, for the dynamic ones, the wizard field that drives it. The matrix is the spec of the page — every element on the page maps to a row above; nothing else renders.

### §7.1 Page chrome

| Element | Common or Dynamic | Driving field (when dynamic) |
|---|---|---|
| Tenant logo top-left | Common | — |
| Slide / Tab toggle | Common | — |
| Theme picker | Common | — |
| Viewer Mode dropdown | Common (Internal, Agency) / Hidden (Advertiser, Media Owner) | viewer's role |
| Export menu | Common | — |
| Proposal status badge | Dynamic | `campaigns.status` |
| Lock banner | Dynamic | `campaigns.status === Approved` |
| Rejection greyscale | Dynamic | `campaigns.status === Rejected` |
| Edit plan deep link | Dynamic | viewer is the creator AND status ∈ {Draft, Reviewing, Rejected} |
| Open in Campaign Detail per-slide deep links | Common (Internal, Agency, Media Owner) / Hidden (Advertiser) | viewer's role |
| Per-slide footer | Common | — |
| Currency formatting | Dynamic | `campaigns.currency` |
| Locale formatting | Dynamic | active tenant locale |

### §7.2 Cover (§5.1)

| Element | Common or Dynamic | Driving field |
|---|---|---|
| Plan name | Common (always present for any saved campaign) | — |
| Plan ID, period | Dynamic | `campaigns.{externalId, dateRange}` |
| Channel chips strip | Dynamic | `campaignDetailsData.mediaChannels` |
| Brand block | Dynamic | `campaigns.brandId` is set |
| Brand logo image | Dynamic | `brands.logoUrl` |
| KPI tile 1 (Budget) | Dynamic | `budgetGoalData.budget + currency` |
| KPI tile 2 (Cost) | Dynamic | sum of `inventories[].planPeriodCost` |
| KPI tile 3 (Impressions) | Dynamic | sum of `inventories[].planPeriodImpressions` |
| KPI tile 4 (Reach / Duration) | Dynamic | reach model output, else duration in days |
| KPI tile 5 (Goal / Channels) | Dynamic | `budgetGoalData.goalType` else `mediaChannels` |
| Cover photography | Dynamic | top-3 inventories' `thumbnailUrl` / `images`; else flat theme colour |
| Lower-right agency/advertiser logo | Dynamic | `companies.logoUrl` of the agency or advertiser, omitted when same as tenant |

### §7.3 Plan Snapshot (§5.2)

| Element | Common or Dynamic | Driving field |
|---|---|---|
| Goal tile | Dynamic | `budgetGoalData.goalType` is set |
| Lead KPI order | Dynamic | `budgetGoalData.goalType` |
| Country and city counts | Dynamic | distinct `inventories[].country` and `.city` |
| Inventory and operator counts | Dynamic | `inventories.length` and distinct `inventories[].companyId` |
| Plain-English sentence | Dynamic | reach (else impressions) + city-count + country-list + dates |

### §7.4 Brand Context (§5.3)

| Element | Common or Dynamic | Driving field |
|---|---|---|
| Whole slide | Dynamic | `campaigns.brandId` is set |
| Brand logo (large) | Dynamic | `brands.logoUrl` |
| IAB tier-1 | Dynamic | `brands.iabTier1` |
| IAB tier-2 chips | Dynamic | `brands.iabTier2` (omitted when empty) |
| Parent advertiser | Dynamic | `brands.parentAdvertiser` (omitted when empty) |
| Affinity descriptor | Dynamic | in-repo lookup keyed by `iabTier1 + tenant country` |

### §7.5 Channel Mix (§5.4)

| Element | Common or Dynamic | Driving field |
|---|---|---|
| Whole slide | Dynamic | `mediaChannels.length > 1` |
| Block per channel | Dynamic | one per entry in `mediaChannels` |
| Thumbnails per block | Dynamic | top-3 of channel's inventories by impression share |

### §7.6 Geographic Plan (§5.5)

| Element | Common or Dynamic | Driving field |
|---|---|---|
| Country/city table | Common (always renders for non-empty plan) | rollup of inventories |
| Static map | Common (always renders for non-empty plan) | pin set = `inventories[].coordinates` |
| POI block | Dynamic | `targetingData.geofencing.targets` non-empty |
| Unmatched POI names list | Dynamic | per-POI match count = 0 |
| Transit footprint line | Dynamic | `Transit` ∈ `mediaChannels` AND at least one Transit inventory in plan |

### §7.7 Featured Inventories (§5.6)

| Element | Common or Dynamic | Driving field |
|---|---|---|
| Whole slide(s) | Common (renders for any non-empty plan) | — |
| Card photos | Dynamic | per-inventory `thumbnailUrl` / `images` |
| Card fallback glyph | Dynamic | per-inventory thumbnail missing |
| Pagination | Dynamic | `inventories.length / 6` rounded up |
| "+ N more" footer | Dynamic | only when sub-slide count > 1 (otherwise no overflow message) |

### §7.8 Audience Strategy (§5.7)

| Element | Common or Dynamic | Driving field |
|---|---|---|
| Demographics block | Dynamic | any of `targetingData.demographics.*` non-empty |
| Behaviours / Signals block | Dynamic | `audienceBehavior` or `signals` non-empty |
| Venue Types block | Dynamic | `environment` non-empty |
| Cinema block | Dynamic | `Cinema` ∈ `mediaChannels` AND `targetingData.cinema` non-empty |
| Country census fallback panel | Dynamic | all four blocks empty |

### §7.9 Daypart and Schedule (§5.8)

| Element | Common or Dynamic | Driving field |
|---|---|---|
| Plan-level heatmap | Dynamic | aggregate of `optimization.schedule.inventorySchedules[].grid` |
| Patterns chip row | Dynamic | distribution of `inventorySchedules[].preset` |
| Multi-spot summary line | Dynamic | `inventorySchedules[].spotsPerLoop` distribution |
| Outlier mini-heatmaps | Dynamic | top-3 by L1 distance from plan-average grid |

### §7.10 Expected Delivery (§5.9)

| Element | Common or Dynamic | Driving field |
|---|---|---|
| Chart bars | Dynamic | per-bin impression forecast (or actuals if Completed) |
| Reach overlay line | Dynamic | per-bin reach model (when reach is computable) |
| Bin granularity | Dynamic | `dateRange` length (see §5.9 table) |
| Title ("Expected" vs "Actual") | Dynamic | `campaigns.status === Completed` |

### §7.11 Cost Breakdown (§5.10)

| Element | Common or Dynamic | Driving field |
|---|---|---|
| Inventory-type ladder rows | Dynamic | distinct `inventories[].type` |
| Media cost vs fees table | Dynamic | viewer role + per-fee `includeInPlan` flags |
| Carbon row | Dynamic | any `inventories[].hasCo2Data === true` |
| Carbon context sentence | Dynamic | total kg CO₂e bucket → static benchmark lookup |
| FX disclosure line | Dynamic | `campaigns.currency !== tenant.reportingCurrency` |

### §7.12 Why This Plan Works (§5.11)

| Element | Common or Dynamic | Driving field |
|---|---|---|
| Scale fact | Common (always present when ≥ 1 inventory) | reach or impressions |
| Efficiency fact | Dynamic | benchmark available for the segment |
| Composition fact | Dynamic | computed pattern across channels / dayparts / cities |
| Rejection-reason replacement | Dynamic | `campaigns.status === Rejected` |

### §7.13 Empty Plan fallback (§5.12)

| Element | Common or Dynamic | Driving field |
|---|---|---|
| Whole replacement | Dynamic | `selectedInventories.length === 0` |

### §7.14 Analytics tabs (§6)

| Element | Common or Dynamic | Driving field |
|---|---|---|
| Tab strip | Common (all seven always present) | — |
| Campaign Plan brand block | Dynamic | `campaigns.brandId` is set |
| Campaign Plan goal row | Dynamic | `budgetGoalData.goalType` is set |
| Campaign Plan recipient list | Dynamic | at least one share link issued |
| Inventory Details thumbnail column | Dynamic | per-inventory `thumbnailUrl` |
| Inventory Details schedule expander | Dynamic | per-inventory `scheduleEntries[]` non-empty |
| Costing tab body | Dynamic | viewer `canSeeCosting` |
| Costing FX disclosure | Dynamic | currency mismatch |
| Operation Details deviation column | Dynamic | per-inventory active hours − operating hours |
| DOOH Schedules per-inventory heatmaps | Dynamic | per-inventory `grid` |
| DOOH Schedules toggle | Common | — |
| DOOH Schedules tab name (DOOH vs Audio) | Dynamic | inventory composition (all radio → Audio) |
| Geography Targeting per-POI table | Dynamic | uploaded CSV |
| Geography Targeting demographic summary fallback | Dynamic | no CSV uploaded |
| Targeting tab blocks | Dynamic | each block independently per its targeting field |

---

## §8. Channel-specific notes

The deck is largely channel-agnostic — the same slides accept whatever inventory the plan picks — but several channels carry first-class affordances that do not exist for the others. This section names them.

### §8.1 Cinema

When Cinema is one of the selected channels, three behaviours change.

The Channel Mix slide §5.4 carries a popcorn glyph in the Cinema header band — the only channel-glyph the slide carries. The other channel header bands use a small dot rather than a glyph; the cinema glyph is the exception because cinema is the channel most readers most need to recognise immediately on the slide (cinema is sold differently — by showtime, by film, by operator — and the popcorn glyph signals that the rest of the slide will reflect that).

The Audience Strategy slide §5.7 renders the cinema block with operator logos picked up from `cinemaOperators[code].logoUrl` (PVR INOX, Cinépolis, GSC, TGV, Shaw, Golden Village, VOX, Reel, Roxy and the rest of the catalogue). The logos are the real consumer-recognisable images; the page never falls back to an initials tile for a cinema operator because the operator is a recognisable consumer brand and a placeholder defeats the slide's purpose. When the logo is missing from the catalogue, the chip is rendered with the operator name in the theme's primary colour and a small popcorn glyph beside it.

The Cost Breakdown slide §5.10 adds a "Showtime band" sub-row under the Cinema row of the inventory-type ladder when `targetingData.cinema.showtimeBands` is non-empty. The sub-row lists each band ("Weekday Prime", "Weekend Prime", "Late Night") with its share of cinema impressions and cinema cost, so the reader can see whether the spend is concentrated on the high-CPM Weekend Prime band or smeared across the week.

The Daypart slide §5.8 renders the per-inventory heatmaps with brighter intensity in cinema cells because cinema inventories typically have higher spots-per-loop (2 to 3 vs. 1 for billboards) — the intensity is the spots-per-loop value, so cinema cells are darker than billboard cells in the same heatmap.

The DOOH Schedules tab §6.5's "By preset" view groups cinema inventories by showtime band when the band is the only customisation (no per-day variation); inventories with both a band and a per-day customisation fall into the Custom group.

### §8.2 Retail

When Retail is selected, the Audience Strategy slide §5.7 surfaces a "dwell context" line under the venue block — "Mall atrium and food court audiences average 18 minutes of dwell time" — drawn from a static dwell-time lookup keyed by retail sub-type (mall atrium, food court, gym, supermarket aisle, drugstore, in-store digital, gas station). The line is generic; it is not a per-inventory dwell measurement. The lookup is keyed by retail sub-type and tenant country so a reader sees the regionally appropriate dwell average.

The Featured Inventories slide §5.6 uses a shopping-bag glyph fallback for retail inventories without a thumbnail. The Cost Breakdown slide §5.10 splits the Retail row by retail sub-type when sub-types differ — a plan with mall-atrium screens and gym screens shows two sub-rows under Retail, each with its own cost share and CPM.

### §8.3 Transit

When Transit is selected, the Geographic Plan slide §5.5 adds a "transit footprint" line below the country/city table — "Coverage on Mumbai BEST bus shelters and Mumbai Metro Aqua Line concourse" — drawn from the inventories' `venueType` tier-3 values. The line is composed by joining the unique venue-type tier-3 strings present in the transit inventories; when the list grows beyond three items the line truncates with "and N more".

The Cost Breakdown slide §5.10 splits the Transit row by sub-type (Airport vs Bus vs Rail vs Taxi) when sub-types differ, with the same rationale as Retail: a plan that mixes airport and bus inventory benefits from the breakdown because the CPMs are typically order-of-magnitude different.

### §8.4 Network

When Network is selected, the Cost Breakdown slide §5.10 collapses the network's many small inventories into a single row "Network — N screens across M sites" rather than listing each — networks are bought as a bundle and a row-per-screen would explode the slide. The collapsed row carries the bundle's CPM rather than per-screen CPMs.

The Featured Inventories slide §5.6 treats network inventories the same as any other inventory — the bundle has a representative thumbnail and the card carries the bundle name; the "address" field on a network card reads "Network — N sites" rather than a street address. The Inventory Details tab §6.2 lists each screen in the bundle as a separate row so a reader who needs the full list can still find it.

### §8.5 Radio

When Radio is selected, the Daypart slide §5.8 replaces the multi-spot summary with an audio-spot summary — "Y 30-second spots per day across N stations" — because radio is bought in spot-counts not loops. The patterns row is renamed "Daypart patterns" (the underlying logic is the same; only the label changes because "preset" is not a recognised term in radio buying). The DOOH Schedules tab §6.5 renames itself "Audio Schedules" when the plan contains only Radio inventories.

The Cost Breakdown slide §5.10 renders Radio's row with the per-spot rate alongside the CPM, because radio is the channel most likely to be quoted per spot rather than per impression. The Inventory Details tab §6.2 adds a "Station reach" column for radio inventories (the station's published weekly listenership) that does not apply to other channels.

### §8.6 Experiential

When Experiential is selected, no channel-specific affordances are added to the deck. The Cost Breakdown ladder gets an Experiential row, the inventories appear in Featured Inventories with a generic event glyph fallback, and the Inventory Details tab lists them like any other inventory. Experiential is one of the two channels (with Mobile) where the deck deliberately stays generic because the format is too varied to admit channel-wide affordances.

### §8.7 Mobile

When Mobile is selected, no channel-specific affordances are added to the deck. Mobile inventories appear in the Cost Breakdown ladder, in Featured Inventories with a phone glyph fallback, and in Inventory Details. The reason is the same as Experiential: the channel is too varied to admit channel-wide affordances. The single concession is the chip glyph on the Cover slide channel strip — Mobile gets a small phone glyph so a reader can spot the channel at a glance.

---

## §9. Logos — where every logo appears

The page is logo-heavy and the rules for which logo appears where are easy to get wrong. The table below is the authoritative map.

| Logo | Location on page | Source field | Fallback when missing |
|---|---|---|---|
| Tenant company logo | Top-left of every slide. Top-left of every Analytics tab header. Lower-left of the cover. | Active tenant's `companies.logoUrl` | Initials tile of two characters in the theme's primary colour |
| Brand logo | Top-right of cover (large). Brand Context slide §5.3 (very large). Featured Inventories cards (small, when the inventory's brand-affinity matches the plan's brand) | `brands.logoUrl` | The whole brand block is omitted; no placeholder is ever shown |
| Cinema operator logo | Audience Strategy slide §5.7 cinema chips. Featured Inventories cards for cinema inventories. DOOH Schedules tab "By preset" view headers when a preset corresponds to a cinema operator's showtime band. | `cinemaOperators[code].logoUrl` | A monochrome popcorn glyph beside the operator name in the theme's primary colour |
| Agency logo | Cover lower-right (when the planner's company is an agency). Cost Breakdown slide §5.10 next to "Agency fee" rows. Campaign Plan analytics tab agency row. | `companies.logoUrl` of the campaign's agency | Agency name in initials tile |
| Advertiser logo | Cover lower-right (when the planner's company is a media owner pitching to a brand directly — replaces the agency logo). Brand Context slide as a "parent advertiser" badge when the brand has a parent. | `companies.logoUrl` of the campaign's `advertiserCompanyId` | Advertiser name in initials tile |
| Media owner logo | DOOH Schedules tab and Inventory Details tab — small logo next to each inventory row. Audience Strategy cinema block (operators are also media owners). Costing tab next to media-owner rollup rows. | Inventory's `inventories.companyId → companies.logoUrl` | Media owner name in initials tile |
| IAB category icon | Brand Context slide as a small badge under the IAB category label. Plan Snapshot brand row. | Static glyph keyed by IAB tier-1 (food/sports/auto/etc.) | Generic tag glyph |

The cover slide therefore can carry **up to four logos at once**: tenant top-left, brand top-right, agency or advertiser bottom-right, and the IAB icon implicitly in the brand block.

A note on logo sizing: the page reserves fixed boxes for every logo so a missing image does not reflow the layout. The brand block at top-right reserves a 96 × 96 px image area; when the logo is missing the whole block (including the box) is omitted and the cover redistributes its top-right space to extend the KPI strip's right padding. The tenant logo reserves a 64 × 64 px area top-left and always renders (initials tile when the image is missing) because the top-left tenant logo is the anchor of the chrome and must always be present.

A note on logo licensing: cinema operator logos and the IAB category glyphs are bundled in the repo and version-controlled with the rest of the application. Brand logos, agency logos, advertiser logos and media owner logos are uploaded by the tenant or by Moving Walls staff and stored in the tenant's asset bucket. The page does not fetch from external CDNs at render time.

---

## §10. Worked examples — three campaigns walked end-to-end

The three variants introduced in §5.0 are walked through every slide and every tab below. The point is to make every conditional in §7 concrete, in three flavours.

### §10a Variant B — Spring Beverages 2026 (cinema-heavy, multi-channel, fully targeted)

The planner ticked Cinema in Step 1 alongside Billboard. In Step 2 they chose India and Malaysia and set the budget at INR 12 M with a goal of Reach 4 M unique adults. In Step 3 they opened the Cinema sub-tab and selected PVR INOX, Cinépolis (India), GSC and TGV (Malaysia) as operators, set the showtime band to Weekend Prime, left genres and ratings empty, and picked no specific films. They set demographics to 25-44, urban, mid-high income; behaviours to "evening commuters" and "cinemagoers". They uploaded a CSV with 12 POIs across Mumbai (4) and Kuala Lumpur (8), each with a 1.5 km radius. In Step 4 they let Auto Plan Creator pick 7 cinema inventories and 15 billboard inventories — 22 total, distributed across Mumbai, Pune, Bangalore, Delhi (India), Kuala Lumpur, Penang (Malaysia). In Step 5 they applied the Commuter preset to the billboards and a custom 2-spots-per-loop weekend-prime grid to the cinema inventories.

On the **cover**, the channel chip strip reads "Billboard · Cinema". The brand block top-right shows the Spring Beverages logo, "Spring Beverages" and "Food & Drink". The KPI strip is Budget INR 12 M, Cost INR 11.42 M, Impressions 320 M, Reach 3.2 M, Goal "Reach 4 M". Cover photography is the top three inventories' thumbnails — one PVR Phoenix Mumbai, one Mid Valley LED Kuala Lumpur, one billboard from Mumbai. The lower-right corner carries the agency logo.

On the **Plan Snapshot**, the goal tile shows "Reach 4 M" with a progress bar at 80 % (3.2 M of 4 M). The four headline KPIs lead with Reach (goal-driven), then Impressions, Frequency, CPM. The country/city counts read "India · 4 cities; Malaysia · 2 cities". The inventory/operator counts read "22 inventories · 5 operators" (PVR INOX, Cinépolis, GSC, TGV, plus the Mumbai billboard owner). The plain-English sentence reads "Reaches up to 3.2 M unique adults across 6 cities in India and Malaysia between 5 Apr and 4 Jul 2026".

The **Brand Context** slide renders. It shows the Spring Beverages logo at large size, "Food & Drink" as the tier-1, "Coffee, Tea, Soft Drinks" as the tier-2 chips, "Springfield Beverages Group" as the parent advertiser, and "Audiences indexing high on Food & Drink also over-index on commuter and cinema dayparts in IN and MY" as the affinity descriptor.

The **Channel Mix** slide renders two blocks — Billboard (15 inventories, 47 % of budget, 38 % of impressions) and Cinema (7 inventories, 53 % of budget, 62 % of impressions), each with three thumbnails. The Cinema block carries the popcorn glyph in its header band.

The **Geographic Plan** slide table has six rows (Mumbai 8 inv, Pune 3 inv, Bangalore 2 inv, Delhi 2 inv, Kuala Lumpur 5 inv, Penang 2 inv). The static map carries 22 pins coloured by city (six colours). The POI block reads "12 uploaded · 9 matched · 3 unmatched" and lists the three unmatched names ("Marina Square Mumbai", "Sunway Pyramid annex", "BTS Junction").

The **Featured Inventories** slide(s) paginate. Slide 5.6.1 carries the top 6 by impressions (3 cinema, 3 billboard). Slide 5.6.2 carries the next 6. Slide 5.6.3 carries the next 6. Slide 5.6.4 carries the last 4. Each card has a real photo (the catalogue is well-populated for these inventories), the address, the venue type, the format, the dimensions, daily and monthly impressions, the plan-period cost, the CPM, and the operating hours.

The **Audience Strategy** slide renders all four blocks. Demographics: bars for 25-34 and 35-44 (equal height, no weights captured), "Urban" chip, "Mid-high income" chip. Behaviours: chips for "Evening commuters" and "Cinemagoers", each with a one-line description. Venue types: stacked bar showing cinema (62 % of impressions) and street furniture (38 %). Cinema: four operator logo chips (PVR INOX, Cinépolis, GSC, TGV), a "Weekend Prime" band-impression bar showing 100 % concentration, no genre or rating chips because none were set.

The **Daypart** slide's heatmap shows two bright commuter bands (6–9 am and 5–8 pm weekdays) plus a hot Friday/Saturday-evening strip from the cinema inventories. Patterns row reads "Commuter · 15", "Custom · 7". Multi-spot summary reads "7 inventories run > 1 spot per loop, avg 2.0". Three outlier mini-heatmaps: PVR Phoenix Mumbai ("Friday & Saturday evenings only · 2 spots/loop"), Cinépolis Pune ("Friday & Saturday evenings only · 2 spots/loop"), TGV Sunway Pyramid ("Friday & Saturday evenings only · 2 spots/loop").

The **Expected Delivery** chart uses monthly bins (91-day plan). Three bars: "5 Apr – 4 May" 102 M impressions, "5 May – 4 Jun" 108 M, "5 Jun – 4 Jul" 110 M. Reach overlay reads 1.1 M, 1.2 M, 0.9 M (with reach saturating in month 3). Footer reads "Peak: 5 Jun – 4 Jul · 3 monthly bins".

The **Cost Breakdown** ladder has three rows — Classic (INR 4.2 M, 38 % impressions), Digital Screen (INR 1.5 M, 9 % impressions), Cinema (INR 5.7 M, 53 % impressions, with a "Showtime band: Weekend Prime" sub-row underneath). The fee block has one agency-fee row (10 % of media, visible to the agency, hidden from the advertiser). The carbon row reads "142 kg CO₂e total · 0.44 g per CPM · equivalent to 12 km driven in a small petrol car". The FX disclosure line reads "Prices in INR; converted to USD using the FX rate as of 5 Apr 2026 for tenant reporting".

The **Why This Plan Works** closing slide reads three lines: "Reaches 3.2 M unique adults across Mumbai, Pune, Bangalore, Delhi, Kuala Lumpur and Penang", "Concentrated on commuter and weekend prime cinema dayparts where Food & Drink audiences over-index 2.3×", "INR 4.10 eCPM, 18 % below Food & Drink category benchmark".

In the **Analytics view**, the Campaign Plan tab carries the brand block, the goal row ("Reach 4 M"), no external ID row, and the recipient list with one entry (the advertiser the deck was sent to). The Inventory Details tab carries 22 rows with thumbnails, every row's schedule expander populated. The Costing tab renders fully for an Internal viewer, with the agency fee visible; for an Advertiser the tab body says "Costing is hidden from your role". The Operation Details tab shows 22 rows; cinema rows show active hours of 16 per week (Friday + Saturday evenings only), billboard rows show 70 per week (commuter pattern). The DOOH Schedules tab in "By inventory" mode renders 22 mini-heatmaps; switching to "By preset" collapses them to two — one labelled "Commuter · 15 inventories" and one labelled "Custom · 7 inventories"; switching to "Plan-level rollup" shows the same heatmap as the deck §5.8. The Geography Targeting tab carries the 12 uploaded POIs, with 9 marked "✓ matched" and 3 marked "⚠ no inventory in radius" with the names listed. The Targeting tab carries the demographics block, the behaviours block, the venue types block and the cinema block.

If the planner had not picked Cinema in Step 1, the cover chip strip would read "Billboard" only, the Channel Mix slide would not render at all (only one channel), the Audience Strategy slide's cinema block would not render, and the Cost Breakdown ladder would have no Cinema row. The Daypart heatmap would lose the Friday/Saturday evening strip and the patterns row would read "Commuter · 15" only. The DOOH Schedules tab would still render but with 15 inventories instead of 22. Nothing else would change.

### §10b Variant C — Velocity Sports Marathon Hype (multi-channel, sparse targeting, rich per-inventory schedule)

The planner ticked Billboard, Cinema and Retail in Step 1. In Step 2 they chose Singapore only, set the budget at SGD 90 K with no goal. In Step 3 they did not set demographics, behaviours or cinema operators; they ticked the venue types "Shopping malls", "Transit", "Cinema lobbies"; they did not upload a CSV. In Step 4 they picked 14 inventories manually — 6 mall billboards, 4 cinema lobby screens, 4 gym retail screens. In Step 5 they built four distinct per-inventory grids: gym retail screens active 5–9 am and 6–10 pm Monday to Friday, mall billboards active 11 am – 9 pm every day, cinemas active Friday-evening to Sunday-evening only with 1.5 spots per loop, and one mall LED with a custom "lunch hour boost" that doubles the spots from 12–2 pm daily.

On the **cover**, the channel chip strip reads "Billboard · Cinema · Retail". The brand block top-right shows the Velocity Sports logo, "Velocity Sports" and "Sports". The KPI strip is Budget SGD 90 K, Cost SGD 88.2 K, Impressions 18.6 M, Reach 0.9 M, channels "Billboard · Cinema · Retail" (no goal tile because no goal was set). Cover photography pulls the top three thumbnails across the Singapore plan.

The **Plan Snapshot** has no goal tile. The four KPIs lead with Reach (0.9 M), Impressions, Frequency, CPM in that order. Country/city counts read "Singapore · 1 city". Inventory/operator counts read "14 inventories · 4 operators". The plain-English sentence reads "Reaches up to 0.9 M unique adults across 1 city in Singapore between 12 Aug and 25 Aug 2026".

The **Brand Context** slide renders. It shows the Velocity Sports logo at large size, "Sports" as the tier-1, "Running, Fitness, Marathon" as the tier-2 chips, "Velocity Holdings" as the parent advertiser, and "Audiences indexing high on Sports also over-index on weekend retail dwell in SG" as the affinity descriptor.

The **Channel Mix** slide renders three blocks — Billboard, Cinema, Retail — each with their share of budget and impressions and three thumbnails. The Cinema block carries the popcorn glyph. The Retail block carries no glyph (the page reserves the popcorn glyph for cinema; retail's block is unmarked).

The **Geographic Plan** slide table has one row (Singapore, 14 inventories). The static map carries 14 pins in a single colour (one city). The POI block is omitted (no CSV uploaded). A transit footprint line reads "Coverage on Singapore MRT Downtown Line concourse" because two of the mall billboards are inside MRT-adjacent malls flagged as transit-environment inventory.

The **Featured Inventories** slide paginates into three sub-slides (6 + 6 + 2 = 14 cards). Every card has a real photo. The gym retail cards show their operating hours as "5–9 am, 6–10 pm Mon–Fri".

The **Audience Strategy** slide renders two blocks. Venue types: stacked bar showing mall (44 % of impressions), transit (25 %), cinema lobby (31 %). The dwell context line under the venue block reads "Mall atrium and food court audiences average 18 minutes of dwell time in SG" (the retail-specific channel affordance from §8.2). Cinema: reduced to a single line "Cinema lobbies targeted across SG" because no operators were picked. The demographics and behaviours blocks are omitted because no demographics or behaviours were set.

The **Daypart** slide's heatmap shows three distinct bright zones: a vertical band 5–9 am Mon–Fri and 6–10 pm Mon–Fri (the gym retail screens), a wide block 11 am – 9 pm every day (the mall billboards), and a hot strip Friday-evening to Sunday-evening (the cinemas). Patterns row reads "Custom · 14" (every inventory has a custom grid; none use a preset). Multi-spot summary reads "4 inventories run > 1 spot per loop, avg 1.5". Three outlier mini-heatmaps: the three gym retail screens ("morning + evening commuter only"), one cinema ("Friday evening to Sunday evening only · 1.5 spots/loop") — and the lunch-hour-boost mall LED is included as the fourth outlier when it deviates more from the plan-average than one of the gyms (the page picks the top three outliers regardless of preset; the deck never lists more than three).

The **Expected Delivery** chart uses daily bins (14-day plan). 14 bars. Two peaks visible on the two weekends inside the flight (cinema-driven). Footer reads "Peak: 16 Aug · 14 daily bins".

The **Cost Breakdown** ladder has four rows — Classic (mall billboards), Digital Screen (the lunch-hour-boost LED), Retail (gym screens), Cinema. Retail is split into a "Gym" sub-row (the four gym screens). No custom fees. The carbon row reads "38 kg CO₂e total · 2.04 g per CPM · equivalent to a single short-haul taxi ride".

The **Why This Plan Works** closing slide reads "Reaches 0.9 M unique adults across Singapore over two weeks", "Spans gym morning and evening dayparts, retail mid-day and cinema weekend prime — six distinct attention windows", "Cinema lobby coverage on every Velocity Sports rival's release weekend".

In the **Analytics view**, the Campaign Plan tab carries the brand block, no goal row, no external ID. The Inventory Details tab carries 14 rows, every row's schedule expander populated (every inventory has a custom schedule). The DOOH Schedules tab in "By preset" mode collapses to one Custom group with 14 inventories listed underneath, each with a mini-heatmap of its own grid (because every inventory's grid is different). In "Plan-level rollup" the same triple-zone heatmap as the deck appears. The Geography Targeting tab shows the targeting-summary fallback (venue types + the dwell context line) with a one-line note that no CSV was uploaded.

### §10c Variant A — Coastline Coffee Q3 (minimum-input, single-channel)

The planner ticked Billboard in Step 1, did not select a brand, and entered no goal in Step 2. In Step 3 they did nothing — no demographics, no behaviours, no signals, no venue types, no cinema, no CSV. In Step 4 they picked 6 billboards in Klang Valley manually. In Step 5 they did nothing — every inventory took the default 24/7 grid.

On the **cover**, the channel chip strip reads "Billboard". The brand block top-right is **omitted**. The KPI strip is Budget MYR 80 K, Cost MYR 74.5 K, Impressions 14.2 M, Duration 28 days (no reach model is available for Klang Valley billboards in the current catalogue, so the fourth tile falls back to Duration), channels "Billboard". Cover photography pulls three Klang Valley billboard thumbnails — but four of the six inventories lack thumbnails, so the photography uses the two available and falls back to a flat MYR-coloured band for the third position (cards rather than slide-wide fallback, per §5.1).

The **Plan Snapshot** has no goal tile. The four KPIs lead with Reach (when reach is computable) or Impressions (when reach is not). For Variant A reach is not computable, so the four KPIs are Impressions, Frequency, CPM, eCPM. Country/city counts read "Malaysia · 1 city". Inventory/operator counts read "6 inventories · 2 operators". The plain-English sentence reads "14.2 M estimated impressions across 1 city in Malaysia between 1 Jul and 28 Jul 2026" (the impressions-fallback template kicks in because reach is not computable).

The **Brand Context** slide is **not rendered**. The deck does not insert a placeholder.

The **Channel Mix** slide is **not rendered** (single channel).

The **Geographic Plan** slide table has one row (Malaysia, Klang Valley, 6 inv). The static map carries 6 pins in a single colour. The POI block is omitted. No transit footprint line (no Transit channel).

The **Featured Inventories** slide carries 6 cards (no overflow, no pagination). Four cards use the billboard glyph fallback; two carry real photos.

The **Audience Strategy** slide renders the **country-census fallback panel**. The header line reads "No audience targeting was applied. The figures below are country-level census estimates and not a projection of who will be reached." The body shows a Malaysia age × gender × income distribution from the static census lookup. No four-block layout is rendered.

The **Daypart** slide's heatmap is a uniform full shade (every inventory is 24/7 by default). Patterns row reads "24/7 · 6". Multi-spot summary reads "0 inventories with more than 1 spot per loop". No outliers — every grid is identical.

The **Expected Delivery** chart uses weekly bins (28-day plan). 4 bars, flat (even delivery). Footer reads "Peak: even delivery · 4 weekly bins".

The **Cost Breakdown** ladder has one row — Classic. No fee block (default plan, no custom fees). No carbon row (Klang Valley billboards lack CO₂ data in the catalogue today). The FX disclosure line is omitted (the plan currency MYR matches the tenant reporting currency MYR for this Malaysian tenant).

The **Why This Plan Works** closing slide reads "Reaches an estimated 1.4 M unique viewers across Klang Valley over four weeks", "USD 6.20 eCPM, 12 % below Klang Valley billboard benchmark", "100 % of impressions delivered on owned street-furniture inventory — no programmatic spill" — three lines computed from the data available even though almost no wizard input was given.

In the **Analytics view**, the Campaign Plan tab omits the brand block, the goal row and the external-ID row. The Inventory Details tab carries 6 rows; every expander is empty (default 24/7); the "Schedule" column reads "Default 24/7" on every row. The DOOH Schedules tab in "By inventory" mode renders 6 uniform-shaded heatmaps; in "By preset" mode collapses to one "24/7 · 6 inventories" group with a uniform heatmap; in "Plan-level rollup" the same uniform heatmap appears. The Geography Targeting tab shows the targeting-summary fallback ("no audience targeting was applied"); the country/city table is at the top.

### §10d What changes if the planner had…

A short cross-walk table of "if the planner had…" deltas, ordered by frequency of the question in user research:

| If the planner had… | What changes in the deck |
|---|---|
| …added a brand mid-flight | Cover gains the brand block; Brand Context slide appears between Plan Snapshot and Channel Mix; Featured Inventories cards may carry the brand logo when an inventory's brand-affinity matches. |
| …added a goal mid-flight | Plan Snapshot gains the goal tile and re-orders KPIs; Cover KPI tile 5 switches from channels to goal. |
| …uploaded a CSV mid-flight | Geographic Plan gains the POI block; Geography Targeting tab gains the per-POI breakdown. |
| …added Cinema mid-flight | Cover chip strip widens; Channel Mix slide appears or gains a block; Audience Strategy slide may gain the cinema block; Cost Breakdown ladder gains a Cinema row; Daypart heatmap reflects cinema schedules. |
| …customised one inventory's schedule mid-flight | Daypart heatmap re-aggregates; patterns row recomposes; multi-spot summary may add the inventory to the outliers; DOOH Schedules tab in "By inventory" mode shows the new grid. |
| …raised the budget by 20 % mid-flight | Cover budget tile updates; Plan Snapshot budget updates; Cost Breakdown does not change unless the planner re-picks inventory (the deck reads what is currently selected, not what could be afforded). |
| …rejected a media owner mid-flight | Approval status pill changes to "Pending — Stage 3 (X media owner)"; Cost Breakdown ladder shows the offending row with an amber "Pending" badge; Why This Plan Works may lose its efficiency fact when benchmark calculation depends on the rejected row. |

---

## §11. Connection to the campaign detail page

The Media Plan and the campaign detail page share a single source of truth — both pages read the same view-model and call the same helper functions. The four behaviours that are shared one-to-one are:

| Behaviour | On the Media Plan | On the campaign detail page |
|---|---|---|
| Plan-level 7×24 daypart heatmap | Slide §5.8 | New "Daypart explained" panel above the existing per-inventory list on the Schedule tab |
| Adaptive-bin delivery chart | Slide §5.9 | New chart on the Performance tab; replaces the hard-coded weekly bins on the Goal tab "Expected Goal Achievement" section |
| Inventory thumbnails | Slide §5.6 cards and Analytics Inventory Details column | New thumbnail column on the Inventory tab list |
| City rollup | Slide §5.5 country/city table | New "By city" rollup card at the top of the Inventory tab |

Each Media Plan slide carries an "Open in Campaign Detail" deep link in its bottom-right corner that takes the reader to the matching tab on the campaign detail page. The mapping is:

- Cover → Campaign Detail header.
- Plan Snapshot → Goal tab.
- Brand Context → Campaign Detail header (the brand block).
- Channel Mix → Inventory tab with the channel filter pre-applied.
- Geographic Plan → Targeting tab.
- Featured Inventories → Inventory tab.
- Audience Strategy → Targeting tab.
- Daypart → Schedule tab.
- Expected Delivery → Performance tab.
- Cost Breakdown → Cost Breakdown tab (or Price Management when the campaign is in negotiation; the link picks the destination from the campaign status).
- Why This Plan Works → no link.

The campaign detail page does not gain any new tabs and does not lose any existing ones. The four additions above are the only changes — every other tab, button, banner and action stays exactly as documented in §3 of the main PRD.

The deep links are hidden in Advertiser viewer mode because the advertiser does not have access to the campaign detail page; they are present in Internal, Agency and Media Owner modes. For Media Owner the deep link target may be a campaign-detail page tab the media owner sees in a restricted form — for example, the Inventory tab is filtered to only the inventory the media owner sells when the viewer is a media owner.

---

## §12. Live edits and re-renders

The page is read-only — no widget on it is editable. All edits happen on the wizard or on the campaign detail page and ripple back to the Media Plan on the next open. The behaviours below are the most visible:

| Edit made | Slide / tab that changes |
|---|---|
| Planner uploads a brand logo | Cover §5.1 brand block, Brand Context §5.3, Featured Inventories §5.6 — all gain the logo |
| Planner adds a goal | Plan Snapshot §5.2 gains a goal tile and re-orders KPIs; Cover KPI tile 5 switches from channels to goal |
| Planner uploads a geo CSV | Geographic Plan §5.5 gains the POI block; Analytics Geography Targeting §6.6 gains the per-POI breakdown |
| Planner adds an inventory | Featured Inventories §5.6 reshuffles; Daypart §5.8 heatmap re-aggregates; Cost Breakdown §5.10 ladder re-totals; Channel Mix §5.4 re-renders or appears if it was a single-channel plan that becomes multi-channel |
| Planner removes an inventory | All the above in reverse; Channel Mix slide disappears if the removal brings the plan back to single-channel |
| Planner edits a per-inventory schedule grid | Daypart §5.8 re-aggregates; Analytics DOOH Schedules §6.5 re-renders; Operation Details §6.4 deviation column recomputes |
| Planner edits a per-inventory spots-per-loop | Daypart §5.8 multi-spot summary recomputes; Cost Breakdown CPM recomputes if the rate is per-play |
| Media owner Accepts a price | Cost Breakdown §5.10 row gains "Accepted" badge; Costing tab row gains "Accepted" badge |
| Media owner Counters a price | Cost Breakdown row carries an amber "Counter" badge; the Why This Plan Works efficiency fact recomputes if the row participated in the benchmark calculation |
| Both sides bilaterally Accept | Row turns green; Stage 3 approval can pass for the owner |
| Approval Tier 2 closes for an owner | Approval status pill on cover updates; that owner's row in §5.10 gains the green check |
| Campaign reaches Approved | Lock banner appears on cover; "Edit plan" deep link disables; share link gains "final" suffix in the recipient list |
| Campaign reaches Active | Status pill flips to blue; Expected Delivery chart starts overlaying actuals when measurement data lands |
| Campaign reaches Completed | Status pill flips to grey; Expected Delivery becomes "Actual delivery"; Why This Plan Works's reach fact switches from forecast to measured |

The page is therefore a live mirror of the campaign — never a snapshot. The only state the Media Plan stores of its own is the proposal row (status, version number, recipient list, sent timestamp). Even the version number is system-driven — it increments automatically when a sent proposal is regenerated because a price changed (the rule is in main PRD §10.7).

---

## §13. Viewer modes — what each role sees

| Role | What is shown | What is hidden |
|---|---|---|
| Internal | Everything | Nothing |
| Agency | The buyer-side view: rate-card / proposed / counter / accepted prices for every inventory; the deck's full slide set; the full analytics tabs except internal-only fees | Internal-only fees marked `showToAgency=false`; other agencies' fees |
| Media Owner | Only the inventory rows the media owner owns; only the negotiation thread for those rows; the deck's slides 5.1–5.7, 5.9–5.11 (cinema/retail/etc. blocks if their inventory is in those channels). Featured Inventories slide is filtered to the media owner's inventory; Cost Breakdown is collapsed to the media owner's rows | Other media owners' rows; the agency's internal fees; full inventory list across the plan; the Why This Plan Works slide's facts about competitors |
| Advertiser | The cleanest view — Cover, Plan Snapshot, Brand Context, Channel Mix, Geographic Plan, Featured Inventories, Audience Strategy, Daypart, Expected Delivery; cost rendered as one rolled-up media cost | Any fee not marked `showToAdvertiser=true`; price source labels; negotiation history; Cost Breakdown's inventory-type ladder is collapsed to one row; Operation Details and DOOH Schedules and Costing analytics tabs are not exposed |

Filtering happens server-side on every request. The viewer-mode dropdown in the page header is a *preview tool* for the planner — it forces the request through the chosen role's filter, so the planner can see exactly what an advertiser will see before sending the share link.

A note on what the preview does not preview. The dropdown does not preview the per-recipient share link's locked theme — a share link issued in the Sunrise theme will always render in Sunrise regardless of the recipient's tenant theme. The dropdown does preview every server-side filter — fee visibility, inventory visibility, slide visibility, tab visibility, deep-link visibility. A planner who has confirmed via the dropdown that an Advertiser sees the rolled-up media cost can safely send the link, because the same filter is what the server will apply to the recipient's request.

---

## §14. Themes and tenant branding

The page ships four themes: **Default** (Moving Walls navy), **Slate** (greys), **Sunrise** (warm orange), **Forest** (greens). Each theme is a palette — primary, secondary, accent, surface — applied uniformly across slide chrome, chip backgrounds, chart fills and the static map's inventory pins.

A tenant-branded theme is a custom palette plus a logo and an optional "preferred section order" hint that lets the tenant suppress the Brand Context slide or move the Cost Breakdown earlier in the deck. Tenant branding is configured in Admin Console (today: a roadmap item — see §18). Until a tenant has uploaded a branded theme, the Default theme is used and the tenant logo is rendered as an initials tile.

The theme picker in the page header lists every available theme. The planner's choice is sticky per user, per campaign — opening a different campaign reverts to that campaign's last-used theme, not the previous campaign's theme. A planner who has switched the theme of a sent proposal does not retroactively change the theme of any already-issued share link; each link is locked at issuance.

The export carries the same theme as the on-screen view, so a planner who switched to Sunrise gets a Sunrise PowerPoint. The Sunrise PowerPoint is bit-identical regardless of which user generates it from the same campaign — the export is a function of the campaign and the theme only, not of the generating user's preferences.

---

## §15. Export, share and download

Today four entries sit in the export menu: PowerPoint, PDF, Excel, Public Share Link. The PowerPoint, PDF and Excel buttons are placeholders that raise a toast notification — file generation is on the roadmap (see §18). The Public Share Link button is fully implemented and writes a tokenised, read-only URL that exposes the Media Plan to a recipient without a Planner account.

When the file generators land:

| Export | Format | Mirrors |
|---|---|---|
| PowerPoint | `.pptx` | The Presentation view, slide-for-slide, in the active theme. Inventory Snapshots pagination follows the on-screen rule (6 cards per sub-slide, numbered 5.6.1, 5.6.2, …). |
| PDF | A4 portrait, printable | The Presentation view in print styles (heatmaps remain readable, fonts switch to print-safe) |
| Excel | `.xlsx`, seven sheets | The Analytics view, one sheet per tab; the DOOH Schedules sheet includes the per-inventory cadence detail in a tabular format |

The Public Share Link contract has four rules. The link is read-only — the recipient cannot post comments, cannot revoke their own link, cannot change theme. The theme is locked at the moment of issuance — a later theme change does not propagate. The viewer mode is forced to the recipient's role — Advertiser for an external advertiser, Media Owner for an external media owner. The link expires automatically when the campaign is rejected or archived; an explicit Revoke button on the page header (and per-recipient in the Campaign Plan analytics tab) lets the planner kill it earlier. Revoking a link does not delete the recipient row; it marks the row with a "Revoked" badge so the audit history survives.

The export menu is hidden from the Advertiser viewer mode — an advertiser visiting a share link cannot re-export the deck (the link itself is the export). The menu is visible to Internal, Agency and Media Owner.

---

## §16. Multi-tenant behaviour

The page respects every tenant rule documented in §1 of the main PRD. A planner who switches active tenant mid-session has their open Media Plan close — the new tenant cannot see the old tenant's plans. Every monetary figure is in the campaign's currency and the locale formatting follows the active tenant's settings. A media owner viewing an agency-built plan sees the page chrome through the media owner's own brand — their tenant logo top-left, their colours if they have a branded theme — even though the body of the deck is the agency's plan.

The currency code is always rendered as a three-letter ISO 4217 prefix ("MYR 7,719", "USD 6.20 CPM"), never as a symbol. This matters because a `$` symbol is ambiguous across USD, SGD, HKD, MYR, AUD and NZD tenants; a "MYR" prefix is unambiguous.

A note on URL structure. Every Media Plan URL embeds the tenant slug in the path (`/p/{tenant-slug}/media-plan/{campaign-id}`). A user who copies a Media Plan URL from one tab and pastes it in another tab under a different active tenant is redirected to the tenant switcher; the page never opens cross-tenant by URL. Share links use a different URL space (`/share/{token}`) that does not require an active tenant, because the recipient may not have a tenant at all.

---

## §17. History and audit

The Media Plan does not have its own history view. Every edit that affects the page, every share-link issuance, every viewer-mode preview, every export click is recorded against the campaign's history log on the campaign detail page. A reader who wants to know "when was this plan last sent and to whom" clicks through to the campaign detail History tab.

The events recorded are:

- Share link issued — recipient, role, theme, timestamp, issuing user.
- Share link revoked — recipient, timestamp, revoking user.
- Share link opened — recipient, timestamp, IP-country (when telemetry is available; see §18).
- Theme changed — old theme, new theme, timestamp, changing user.
- Viewer mode previewed — mode, timestamp, previewing user (for the planner's own audit; not exposed to other roles).
- Export clicked — format, timestamp, exporting user.
- Proposal regenerated — old version, new version, reason (price change, manual regenerate), timestamp.

The events are scoped to the Media Plan's surface; edits to the underlying campaign (budget changes, inventory swaps, schedule edits) are recorded by the wizard and the campaign detail page in their own audit entries, not duplicated by the Media Plan log.

---

## §18. Roadmap and known gaps

**In flight** (Task #1, the Media Plan Overhaul):
- Every slide and every analytics tab driven by the rules in §5 and §6 above. The current page shows mock data; the overhaul replaces it with the dynamic-from-wizard rendering this PRD specifies.
- The redesigned DOOH Schedules tab (per-inventory mini-heatmaps + preset grouping + plan-level rollup).
- The redesigned Geography Targeting tab (real CSV with per-POI inventory match counts).
- The four campaign-detail-page additions in §11.
- The country-census fallback panel for the Audience Strategy slide.
- The adaptive-bin Expected Delivery chart.
- The Inventory Snapshots pagination with the 6-per-sub-slide rule and the 5.6.N numbering.

**Planned next:**
- Real export-file generation (PowerPoint, PDF, Excel) — currently placeholders that raise a toast.
- Tenant-branded themes — palette + logo upload in Admin Console.
- Public Share Link telemetry (number of opens, time-on-page, IP-country for audit).
- Live FX rates from a market data provider for cross-currency tenants (today FX is captured once at campaign start).
- Per-recipient view tracking ("Advertiser X has opened the link three times this week").

**Explicitly deferred** (no near-term plan):
- Real-time multi-user collaboration on the deck.
- In-deck commenting (commenting stays on the campaign detail page where every party's visibility is gated by the @-mention rule in main PRD §10.6).
- Side-by-side A/B comparison of two plans.
- Planner-editable slide order beyond the tenant-theme "preferred section order" hint.
- Custom slide insertion (a planner adding a hand-written slide between two generated slides) — explicitly out of scope because it violates the deterministic-from-wizard rule in §3.

---

## §19. What this document does not cover

This document covers the Media Plan page. It does not cover:

- The wizard itself (covered in main PRD §4).
- The approval workflow (covered in main PRD §7).
- The negotiation engine and bilateral lock contract (covered in main PRD §8).
- The reservation state machine (covered in main PRD §9).
- The IMS handoff (covered in main PRD §18).
- The Admin Console where themes are uploaded (covered in main PRD §1).
- Custom fees and statements (covered in main PRD §12).
- The campaign detail page itself (covered in main PRD §3), except the four shared behaviours in §11 of this document.
- The history log itself (covered in main PRD §17), except for the Media-Plan-specific events in §17 of this document.

Any behaviour not explicitly named here defers to the main PRD. Any conflict between this document and the main PRD is resolved in favour of the main PRD; this document is a scoped companion, not an override.

---

*End of Media Plan PRD.*
