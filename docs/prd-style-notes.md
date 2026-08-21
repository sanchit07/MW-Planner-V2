# Reference PRD — Writing Style Notes

**Source:** Confluence page `Influence DOOH Adserver - PRD` (page ID 176095233, space PROD), version 7.12, last updated April 15, 2026.

These notes capture the rhetorical patterns from the reference PRD so the new MW Planner PRD reads in the same voice.

## Overall structure
The reference document opens with a Glossary, then walks through the platform feature by feature in numbered sections. Sections observed in this order:
1. Glossary
2. The Advertising Hierarchy
3. Dashboard
4. Campaigns
5. Line Items
6. Deal Desk
7. Content Hub
8. Signals
9. Ad Serving Process
10. Campaign-Centric Timezone Execution
11. External System Integrations
12. Inventory Integration
13. Integrations
14. User Interface Navigation
15. History Logs and Audit Trail
16. Recommendation Engine Integration
17. Proof of Play
18. Playlog Upload System
19. Configuration Menu
20. Settings Module
21. Header Bidding

Each section follows a consistent rhythm: a one- or two-paragraph narrative explaining *why the feature exists* and *what problem it solves*, then a table or sub-section enumerating the specifics.

## Voice and rhetorical moves

**Definitions are self-contained.** From the Glossary intro: *"Each definition is written to be self-contained, so you can refer back to this glossary at any point without losing context."* We mirror this — every definition or feature description must read on its own.

**Worked examples inline with definitions.** Notice the CPM entry: *"If a CPM is $10, the advertiser is charged $10 every time their ad is displayed 1,000 times across any combination of screens."* Concrete numbers, named actor, single sentence. We do the same for cinema (e.g. *"If PVR INOX charges INR 35,000 CPM for prime weekend slots, a 5 million-impression run costs INR 175,000."*).

**Signposting paragraph leads.** Reference uses phrases like *"Before diving into how Influence works, it is worth establishing a shared language"* and *"Understanding line items is critical because they are the level at which the ad server makes delivery decisions."* Use the same — paragraphs open by telling the reader why the next paragraph matters.

**Why-before-what.** Section 5 opens *"Line items are where advertising strategy becomes execution. While campaigns provide the organizational framework, line items specify exactly which ads to show…"* The function comes before the field list. We adopt the same: explain the purpose of each wizard step before listing fields.

**Tables for enumerable specifics, prose for everything else.** Tables are used for glossary terms, columns of a list view, status enums. Prose is used for behaviour, intent, edge cases. No bullet-dumps stand alone without a paragraph above them.

**No marketing tone.** The document never says "powerful," "seamless," or "world-class." It is matter-of-fact and treats the reader as a peer who already cares about the domain.

## Patterns to repeat in MW Planner PRD
- A Glossary at the very top covering OOH-specific terms (CPM, CPS, SOV, SOT, Plan, Schedule, Inventory Type, Cinema Operator, Showtime Band, etc.).
- Every feature section opens with a "why this exists" paragraph before specifics.
- A single named worked example threaded through the document (proposed: *"Spring Beverages 2026"* — a multi-channel campaign mixing Billboard and Cinema in Mumbai and Kuala Lumpur).
- Tables for: wizard step field maps, status enums, role × visibility matrices, currency tier ladders, cinema operators by country (linked out to the Excel sheet).
- Cross-references to IAB OpenOOH v1.1 venue taxonomy and OpenRTB 2.5 wherever the platform makes a programmatic-compatible choice, with a one-sentence justification each time.
