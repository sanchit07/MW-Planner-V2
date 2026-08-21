# Recommendation Engine V2 - Planner

## 1. Overview & Purpose

Media planners today rely heavily on intuition when selecting OOH inventories. They browse a map, zoom into locations, and manually choose billboards based on their personal familiarity with an area. This approach works, but it is subjective, inconsistent across users, and not scalable. When campaigns get larger—across multiple cities or countries—the effort increases exponentially.

The Recommendation Engine is designed to shift this workflow from intuition-driven to system-driven. Instead of manually scanning hundreds of inventories, the system reads the user's inputs—dates, locations, audience, brand (if provided), budget, and optionally the campaign goal—and automatically suggests the most suitable set of inventories that match the requirements. The engine understands not just which inventories are relevant but also why they are relevant, combining Measure's audience data with location semantics, category relevance, and availability logic.

The same engine is also built to support external clients via our public API (api.movingwalls.com). This means external systems should receive the same recommendations that Planner users see—ensuring consistency across platforms.

At a high level, the recommendation engine answers a simple question:

> "Given what the user wants to achieve, what are the best possible inventories they should consider, and at what time should they run their ads?"

The engine handles both ideal scenarios (where complete data exists) and imperfect ones (missing measure data, ambiguous brand names, unclear audience targeting, etc.). It offers intelligent fallbacks so that no valid inventory is unfairly excluded unless essential data is missing.

Primary goal: shorten time-to-plan and increase proposal relevance by serving a ranked shortlist of inventories and recommended time windows that match campaign inputs (brand, audience, dates, budget, location).

### Success metrics

- Precision@10 ≥ baseline (measured by planner acceptance) — target 60% for Phase 1
- Reduction in inventory selection time (median) vs manual baseline — target 40% faster
- Top-3 acceptance rate for API clients (post-integration) > 50%
- Explainability tone: every recommendation returns a short "why" sentence


## 2. How Recommendation Works

When a user creates a campaign in Planner, they select a country, define their campaign dates, optionally specify targeting (audience / geography), and may or may not provide a brand name, budget, or goal. The system stores these inputs and passes them to the Recommendation Engine. From here, the workflow resembles how a seasoned media planner would think:

1. **Understand what the campaign is about.**
   If the brand is provided, the system identifies the brand category and category-related behaviors. If the brand name is invalid ("test", "demo"), the engine ignores category scoring entirely and shifts to purely location- and audience-driven recommendation.

2. **Find all inventories matching the country and availability.**
   If the user selects specific cities or geo-boundaries, the engine restricts its search accordingly. Country has to be selected but if no geography within is provided, the system analyzes Measure data to understand where audience concentration is highest nationwide and uses that as a starting point. Inventories which excludes Brand's category (as per IAB) will be filtered.

3. **Estimate the value each inventory brings.**
   If Measure returns data (75% of cases), it becomes the primary source of impressions, reach, SOV, ad plays, and visitation behavior. If Measure does not return data, the system attempts to source first-party data from the media owner. If both are missing → that inventory is excluded because the engine has no meaningful way to evaluate it.

4. **Score every inventory across multiple dimensions.**
   These dimensions include:
   - Budget relevance
   - Campaign goal match (impressions / reach / SOV / ad plays / carbon emission)
   - Geography relevance
   - Audience relevance
   - Brand/category relevance
   - Inventory quality
   - Time alignment
   - Measure confidence
   - Availability window
   
   Each dimension contributes to a final score. A low score in one area does not disqualify an inventory—it only shifts its ranking.

5. **Create a final recommendation list.**
   The system produces:
   - A ranked list of recommended inventories
   - A mix of premium, mid-tier, and filler sites
   - Time-window suggestions (where relevant)
   - A confidence percentage
   - A summary explaining why these inventories were recommended

6. **Planner displays the list in the Inventory Browser.**
   Users can override the recommendations, remove items, or add others manually. Recommendation is a guide, not a restriction.

Throughout the process, the engine handles missing data gracefully, ensuring that a lack of brand name, partial measure data, or missing venue type does not break the workflow.


## 3. Data Inputs & Dependencies

Below is an explanation of all data components the engine uses, how they interact, and how the system handles situations where some data is unavailable.

### 3.1 Inputs From User

Users may provide any combination of the following:

**Campaign Dates (mandatory)**
Used for availability, impression prediction, and time-based scoring.

**Country (mandatory)**
All recommendations are restricted within the selected country.

**Geography Targeting (optional)**
- City
- State
- Polygon / radius drawn
- POI targeting
- If absent, engine uses Measure to identify high-audience regions.

**Audience Targeting (optional)**
Helps match inventories with similar audience behavior from Measure.

**Brand Name (optional)**
- If valid → used to map brand category
- If invalid ("test", "demo", empty) → ignored
- If ambiguous → AI resolves it and find
- If still unclear → remove brand-fit scoring completely

**Budget (optional)**
Budget affects scoring but does NOT filter out inventories.
The engine assumes multiple inventories will be selected.

**Campaign Goal (optional)**
- Impressions - maximize total ad views
- Reach - maximize unique people who see the ad
- SOV (Share of Voice) - achieve target percentage of available ad plays
- Ad Plays - maximize number of times ad is displayed
- Carbon Emission - minimize environmental impact

Goal changes the scoring priorities and budget allocation across inventory types.

### 3.2 Inputs From Inventory System

Every inventory provides:
- Site ID
- Coordinates (lat/lng)
- Venue type (mall, roadside, transit, etc.)
- Format (static/digital/audio)
- Screen size, pitch, quality
- Availability logic (using APO)
- Pricing model (monthly, hourly, CPM, agency specific rates, ad-play rate)
- SSP source (Moving Walls / VIOOH / etc.)
- First-party or media owner data (if available)

If first-party data is also missing and Measure has no record, engine excludes the inventory.

### 3.3 Inputs From Measure

Measure provides:
- Impressions
- Reach and Frequency
- Audience profiles / segments
- Reach Saturation
- Hourly traffic curve
- Popularity index of inventory
- POI relevance (in future)

This data becomes the primary source of value scoring.

If Measure fails:
- Engine retries Measure once
- If still missing → attempts fallback with media-owner data
- If both missing → exclude that inventory

### 3.4 Inputs From AI (Gemini / fallback: OpenAI)

AI is used strategically, not randomly:

**A. Semantic understanding**

Used when brand name is vague. For example:
- Brand given = "AirAsia" → category = "Travel / Airlines"
- Brand = "TestBrand" or "No Brand"→ category = None
- Brand = "LifeStyleCo" → Gemini maps the name to possible industries
- If category (IAB) is known, proceed else fallback to backup logic explained in this document.

**B. Location semantics**

AI interprets text or metadata that is missing or incomplete:
- Example: "Tesco Main Entrance Screen 2"
- AI can infer:
  - It's a retail environment
  - High grocery shopper flow
  - Visits peak evenings & weekends
- If only coordinates are available:
  AI can describe the likely environment using map context and find most relevant IAB category such as IAB 4-12 etc.

**C. POI-to-brand mapping (category affinity)**

E.g., "Nike" campaign → prefer sports venues, gyms, youth hotspots. User might not have searched for POIs. Find out relevant POIs using AI. Even if user has selected POIs, find more, compile list of unique POIs by adding user's searched POI + AI suggested POI. Target is to have at-least 5 unique POIs for each campaign.

**D. Missing venue type inference**

Gemini analyses:
- inventory name
- lat/lng
- nearby POI
- SSP metadata
- map semantics (if description missing)

If Gemini fails → fallback to:
- neighbors
- cluster behavior
- inventory format patterns

If still unresolved → inventory remains but receives neutral score. AI is never used to hallucinate numbers. It only provides classification, embeddings, and semantic context.


## 4. End-to-End System Workflow

Below is the complete workflow from campaign input to final recommendation.

### 4.1 Diagram

[See attached diagram in original document]

### 4.2 Walkthrough of the Workflow

When the Recommendation Engine receives campaign details, it begins by pulling all inventories that exist within the chosen country. If the user has specified cities or drawn polygons, the list is restricted accordingly. The engine then checks availability for the campaign dates using the same APO logic that Influence uses. Only inventories with at least one available slot during the campaign window proceed to the next stage. Inventories which exclude IAB category of the Brand should be excluded as well.

The next step is to retrieve impression and reach data from Measure. Since Measure is the primary data source for 75% of inventories, most inventories will receive full audience modeling. Inventories for which Measure does not return data move to the fallback layer and take media owner's provided data. At this point, the engine attempts to fetch first-party audience data provided by media owners. This is common with malls, airports, and premium media owners. If first-party data is also missing, the inventory is excluded because the engine cannot compute any meaningful impact estimate.

Once the inventory passes the data stage, it moves to the AI layer. Here, the system determines whether the user-provided brand name is meaningful. If it is not ("demo", "test", empty), brand category scoring is entirely skipped. If it is valid, Gemini maps the brand to its probable IAB category. If the confidence level is above 0.75, the system uses category scoring to identify whether the inventory's environment matches the brand's natural audience context. For example, a travel brand would benefit from airport and transit environments, while FMCG brands benefit from supermarkets and malls.

AI is also used for location semantics. If venue type or environment description is missing, Gemini interprets the name, coordinates, and nearby POI to infer whether the site is retail, residential, transit, or CBD. This helps standardize inventories coming from different SSPs, especially when VIOOH or other networks provide only minimal metadata.

After that, the system evaluates audience relevance. If the user has selected specific audience groups, their affinity is matched with Measure's visitation behavior. If no audience is selected, the engine simply uses general audience reach.

Inventory quality is also evaluated—screen size, visibility, placement angle, dwell time, and presence of obstructions all contribute to the quality score. Retail networks receive a different treatment than roadside billboards to ensure fairness.

Once all these factors are computed, the engine evaluates whether the inventory is reasonable for the user's budget. Budget fit is not a filter; it adjusts scoring based on how the inventory's cost relates to the expected remaining budget distribution. If the user has also provided a campaign goal, such as impressions or reach, that goal becomes a dominant scoring factor.

Finally, all inventories with complete data move to the scoring and ranking layer. Each inventory receives a weighted score, and the system returns a ranked list that combines premium, mid-tier, and cost-efficient options. Inventories with missing essential data never appear in the list.


## 5. Scoring Logic

This is the heart of the engine. The objective is to convert heterogeneous signals (Measure impressions, POI proximity, availability, price models) into a single, explainable final_score (0–100) so we can rank sites. The score is built from component scores; each component has explicit computation rules and fallbacks. Everything is deterministic, auditable, and explainable. We use an additive, weighted scoring model where component values are normalized to 0–100 and then combined by configurable weights. We keep the model simple and transparent so product and ops can change weights per campaign type or per client.

### 5.1 Component list (what we score)

Core components used in Phase 1:

1. **measure_fit** — How well the inventory delivers the campaign goal (impressions/reach/SOV/ad plays/carbon emission) for the requested dates & audience. Primary driver when Measure data exists.
2. **geo_fit** — How well the inventory is located relative to user geofence, POIs, or target areas.
3. **availability** — Fraction of requested schedule that is available; penalizes partial availability.
4. **budget_fit** — How reasonable the site price is relative to the user budget (soft score).
5. **audience_fit** — Degree of overlap between inventory audience profile and campaign audience.
6. **brand_fit** — How well the inventory matches the brand category / semantics.
7. **quality_fit** — Physical and creative quality: screen size, pitch, angle, line-of-sight, format.
8. **time_fit** — Daypart / date alignment (low weight in Phase 1).

Each component maps to 0–100. Missing raw inputs trigger fallbacks described inline.

### 5.2 Normalization & basic math principles

All raw metrics first get converted to monotonic indicators (higher = better). Example: impressions → raw_imps, price → inverse_price_score. Each indicator is then min-max normalized using sensible bounds (not global min/max to prevent outliers from dominating). Bounds are configurable and applied per country/market. We cap and floor after normalization to avoid extreme influence. We keep all intermediate values (raw → normalized → component score) in the audit log for each run_id.

**Normalization example (impressions):**
- Assume per-day impressions range used as bounds: [min=1k, max=1,000,000].
- norm_imps = clamp( (raw_imps - min) / (max - min), 0, 1 ) * 100 → measure_fit base.
- If campaign goal is impressions and target is 1M, a site with 100k impressions might get a measure_fit=10 before goal scaling (goal scaling next).

We use clamp() to keep values 0–1. If a metric is missing, we return null and follow fallback rules.

### 5.3 Compute measure_fit

**Purpose:** measure how well the inventory contributes toward the campaign's goal for the given date range and audience.

**Inputs required:** Measure impressions for (inventory, date range, audience) OR first-party owner impressions for those dates (fallback). If no source => use null and fallback rule triggers.

**Primary calculation (when Measure available)**

1. site_imps = Measure.impressions(inventory, date_range, ad_plays)

2. If the user specified goal_type = **impressions** and goal_value = G:
   - Contribution fraction = cf = min(site_imps / G, 1)
   - measure_fit_raw = cf * 100 (caps at 100)

3. If goal_type = **reach**, we replace site_imps with site_reach and compute similarly.

4. If goal_type = **SOV**, we compute site SOV = site_ad_plays / total_possible_ad_plays in date range and map to 0–100 by site_sov * 100.

5. If goal_type = **ad_plays** and goal_value = G:
   - site_plays = inventory ad plays for date range (from loop frequency × hours × days)
   - Contribution fraction = cf = min(site_plays / G, 1)
   - measure_fit_raw = cf * 100

6. If goal_type = **carbon_emission** and goal_value = G (max CO2 in kg):
   - site_co2 = inventory co2PerPlayKg × estimated_plays
   - Lower CO2 = better fit. Invert scoring: measure_fit_raw = 100 - min((site_co2 / G) * 100, 100)
   - Example: If site produces 5kg CO2 and goal is 50kg max, measure_fit = 100 - (5/50)*100 = 90

**If user gave no explicit goal or budget:**
- Use impression-based proxy: measure_fit_raw = norm(site_imps) using per-market impression bounds.

**Fallbacks (when Measure missing)**
- First-party owner-provided impressions for that date range → compute measure_fit identical to above (marked with warning).
- If neither Measure nor first-party exists → set measure_fit = null and let scoring engine re-balance weights (see weight redistribution below). Unless the inventory has no other support signals, it will either be deprioritized or excluded depending on earlier rules.

**Example**
- Campaign wants G = 1,000,000 impressions.
- Inventory X has site_imps = 120,000.
- measure_fit = min(120k / 1M, 1) * 100 = 12.

### 5.4 Compute geo_fit

**Purpose:** reflect proximity and directness with respect to user-selected geography, POIs, or geofence.

**Inputs:** user_geofence (polygon) OR city list OR POI list; inventory lat/lon; inventory → POI distance.

**Rules**
- If inventory inside geofence → geo_fit = 100.
- If inventory within R1 meters of geofence boundary → geo_fit = 90.
- Otherwise, distance decay: geo_fit = max(0, 100 * (1 - dist / R2)) where R2 is the maximum radius beyond which geo relevance is zero (configurable, default 50 km for country-scope, smaller for city-scope).
- If user supplied POIs → best POI distance used. If multiple POIs, use weighted min-distance with POI weights (user can set weight for preferred POIs).

**Example: geo_fit Calculation**

Campaign targets: "Within 5km of KLCC Tower"
- KLCC Digital Billboard (0.2 km from KLCC): Inside radius → geo_fit = 100
- Pavilion Mall Screen (1.5 km from KLCC): Inside radius → geo_fit = 100
- Mid Valley (8 km from KLCC): Outside 5km radius
  - Distance decay: geo_fit = max(0, 100 * (1 - 8/50)) = 84
- Subang Jaya Billboard (25 km from KLCC):
  - Distance decay: geo_fit = max(0, 100 * (1 - 25/50)) = 50
- Johor Baru (300 km from KLCC):
  - Distance decay: geo_fit = max(0, 100 * (1 - 300/50)) = 0
- If user provided channels (e.g., "airport"), and inventory is classified as airport → add +10 bonus.

**Fallback**
- If no user geography given → compute geo_fit = 100 for inventories in top-N populous regions (as computed from Measure). In other words, when user doesn't constrain geography, we favor high audience areas.

**Example**
- User draws a 2km polygon around a stadium. Inventory Y is 300m from polygon edge → geo_fit = 90 (R1=1km rule).
- Inventory Z is 10km away and R2=50km: geo_fit = 100 * (1 - 10/50) = 80.

### 5.5 Compute availability

**Purpose:** quantify how much of the requested schedule is actually free.

**Inputs:** inventory_availability calendar (APO data). For each inventory, APO returns available slots for date range.

**Calculation**
- Let requested_slots = total theoretical slots for the date range (e.g., number of days × 1 slot/day for static, or available ad-plays for digital).
- Let available_slots = slots not reserved / blocked
- availability = floor(100 * (available_slots / requested_slots))

**Partial availability**
- If availability >= 80 → treat as full (100) for UX and reduce small friction.
- If availability < 10% → inventory should be excluded earlier.
- Provide availability_summary like "6/10 days available" for user.

**Example: availability Calculation**

Campaign: January 1-31, 2026 (31 days)
- KLCC Digital Billboard:
  - All 31 days available → availability = 100
- Pavilion Mall Screen:
  - Days 1-25 available, days 26-31 booked by another campaign
  - availability = floor(100 * 25/31) = 80 → treated as 100 (above 80% threshold)
  - availability_summary = "25/31 days available"
- KLIA Airport Network:
  - Days 1-5 available, days 6-31 reserved
  - availability = floor(100 * 5/31) = 16
  - Lower score significantly impacts final_score
- Penang Billboard:
  - Only days 1-2 available (2/31)
  - availability = floor(100 * 2/31) = 6 → **excluded** (below 10% threshold)

**Edge:** If inventory has no APO data but owner provides availability -> use owner data. If both missing -> mark availability = null, and add warning. In practice we expect APO for most inventories (Influence/SSP-synced).

### 5.6 Compute budget_fit

This requires converting inventory pricing models into a common currency per-campaign metric — typically estimated_cost_for_campaign_window and cost_per_estimated_impression (if impressions exist).

**Supported price models**
- Flat periodic (e.g., MYR 13,500/month)
- CPM (cost per thousand impressions)
- CPS / per-ad-play
- Custom (mix or negotiated price)

**Steps**

1. Normalize price into campaign_cost_estimate:
   - If monthly flat and campaign length d days in month of m days: cost = base_monthly * (d / m)
   - If CPM: cost = (CPM / 1000) * estimated_imps_for_window (use Measure or traffic proxy)
   - If CPS (per play): cost = CPS * estimated_ad_plays_in_window

2. Budget relationship
   - If user supplied total_budget = B, compute proportion = cost / B.
   - Score mapping:
     - proportion <= 0.2 → budget_fit = 95 (very affordable)
     - 0.2 < proportion <= 0.5 → 75
     - 0.5 < proportion <= 0.8 → 50
     - 0.8 < proportion <= 1.2 → 30
     - >1.2 → 10
   - Rationale: We favor options that let the planner mix multiple sites.

3. If no budget provided → budget_fit = 50 neutral.

**Edge cases**
- When inventory lacks price data → rely on owner average or popularity-index-derived price; add warnings: ["price estimate used"].

**Example**
- Inventory cost for window = MYR 4,500; budget B = MYR 30,000 → proportion = 0.15 → budget_fit = 95.

### 5.7 Compute audience_fit

**Purpose:** measure alignment between inventory audience and campaign audience.

**Inputs:** audience segments (user), Measure audience profile for inventory (segment %).

**Calculation**
- If user specified segments, compute overlap using Jaccard-like measure or direct % mapping:
  - overlap = sum(min(site_pct(segment), campaign_pct(segment)) over segments)
  - normalize to 0–100.
- If no audience specified → audience_fit = 50 (neutral).

**Fallbacks**
- If Measure missing but owner-provided visitor profile exists → use it.
- If neither → use site_type heuristics (airport → travelers high; mall → shoppers).

**Example**
- Campaign audience: 60% travelers, 40% adults 25–44.
- Inventory audience: 70% travelers, 20% adults 25–44.
- Overlap = min(0.6,0.7) + min(0.4,0.2) = 0.6 + 0.2 = 0.8 → audience_fit = 80.

### 5.8 Compute brand_fit

**Purpose:** reflect brand-to-site affinity.

**Inputs & methods**
- If brand provided and brand_category_map exists, check if inventory site_type intersects brand preferred site_types → score = 100 if exact match, lower for partials.
- Use semantic embeddings (Gemini) to compute similarity between brand tags and inventory keywords (normalized to 0–100).
- If brand is "demo"/"test"/invalid → brand_fit = 50 neutral and add warning.

**Example**
- Brand = "AirFly" (category travel). Inventory site_type = airport → brand_fit = 100.
- Inventory site_type = mall → brand_fit = 30.

### 5.9 Compute quality_fit

**Purpose:** per-inventory physical / creative quality.

**Inputs**
- screen area, pitch (for LED), format, obstructions, number of lanes visible, angle, line-of-sight rating, owner-rated quality index.

**Computation**
- Weighted sum of normalized sub-factors (size 30%, pitch 25%, visibility 25%, owner_quality 20%).
- Map to 0–100.

**Special handling for networks**
- For networks (many small screens), compute network-level quality: average quality or weighted by footfall.

### 5.10 Compute time_fit

**Purpose:** alignment between date/daypart and inventory peak performance.

**Inputs:** Measure daypart curve; user requested dayparts.

**Computation**
- time_fit = sum( user_daypart_weight * inventory_daypart_percent ) normalized to 0–100.
- Low default weight (5–8%) in Phase 1; we will expand this later.

### 5.11 Final weighted score and weight redistribution

**Default weight table (configurable)**

| Component | Default Weight (goal=impressions) |
|-----------|-----------------------------------|
| measure_fit | 20% |
| geo_fit | 20% |
| availability | 10% |
| budget_fit | 20% |
| audience_fit | 10% |
| brand_fit | 10% |
| quality_fit | 6% |
| time_fit | 4% |
| **Total** | **100%** |

**When Measure missing for candidate**
- If measure_fit is null, redistribute its 20% weight among geo_fit, quality_fit, audience_fit and brand_fit proportionally. Example: remove 20% then allocate +8% to geo, +5% to quality, +4% to audience, +3% to brand (predefined distribution). This ensures candidates without Measure do not automatically sink but they need stronger support from other signals.

**Final score calculation**
- final_score = sum(weight_i * component_i) where component_i is 0–100 and weight_i is fraction (sums to 1).

**Example full calculation (numbers)**

Assume inventory A:
- measure_fit = 70
- geo_fit = 90
- availability = 100
- budget_fit = 95
- audience_fit = 80
- brand_fit = 85
- quality_fit = 70
- time_fit = 60

Plug weights from table above (20/20/10/20/10/10/6/4):
```
final = 0.20*70 + 0.20*90 + 0.10*100 + 0.20*95 + 0.10*80 + 0.10*85 + 0.06*70 + 0.04*60
```

Compute:
- 14 + 18 + 10 + 19 + 8 + 8.5 + 4.2 + 2.4 = 84.1 → final_score=84.1

Record intermediate components for why and logging.

### 5.12 Introducing 10–15% recommendation variation across runs

We must not return the identical ranked list for repeated identical requests. Introduce controlled diversity so planners see alternative mixes.

**Principle:** preserve rank stability for top items but allow exploration.

**Algorithm**

1. Compute final_score for all candidates.
2. Convert scores to softmax probabilities p_i = softmax(final_score / T) with T = temperature. Choose T low (0.07–0.10) for stable ranking; window chosen to produce ~10–15% variability for top N.
3. For top K (default K=25) choose deterministic perturbation: sample one candidate in positions 5–15 to swap with a neighbor based on p_i (seeded RNG using run_id and minute). Or simpler: add small deterministic noise: final_score' = final_score * (1 + jitter) where jitter is drawn uniform in [-v, v] with v=0.075 (7.5%). This produces ~10–15% variation in ranking across runs.
4. Use seeded RNG (derived from run_id) so the same request within the same second reproduces the same variation. Variation must be auditable: run_id includes the seed in logs.

**Why this method**
- Keeps top-1 stable most of the time.
- Introduces exploration in mid-ranking to surface alternative mixes.
- Deterministic seed avoids surprises when debugging.

### 5.13 From single-site scores to multi-site plan suggestion (budget-aware mix)

Planners expect the recommendation to produce a set — not only a ranked list. For this the engine offers two outputs:

1. **Top-N ranked list (default)** — what we discussed.
2. **Suggested mix under budget (optional)** — a greedy packer that picks sites until budget exhausted.

**Greedy budget packer (simple and explainable)**
- Input: total_budget B or desired impression target G (if goal given).
- Items sorted by value_density = final_score / cost_estimate (score per unit cost).
- Select items in descending value_density until budget B exhausted or marginal value drops below threshold.
- For partial inventory (e.g., partially available), compute prorated cost and impressions.
- Return selected items + leftover budget + predicted impressions.

**Why greedy**
- Easy to explain: "we pick highest score per ringgit first."
- Fast and deterministic.
- Works well in practice for OOH because inventory sizes are coarse and volumes limited.

Future: a knapsack optimizer could improve optimality but increases complexity—defer to Phase 2.

**Example**
- Budget B = 30,000.
- Inventory A cost = 4,500, score=81 → density=0.018
- Inventory B cost = 10,000, score=85 → density=0.0085
- Inventory C cost = 2,500, score=60 → density=0.024
- Packer picks C (2,500), A(4,500), then maybe B depending on remaining budget.


## 6. Detailed Case Handling — edge cases and exact behavior

Below we enumerate the real-world messy cases and define exact deterministic behavior so the team can implement and product-test confidently.

### Case 6.1 — Measure exists for some inventories, but not for others (VIOOH or other SSPs present)

**Behavior**
- For Measure-enabled inventories: use measure_fit primary.
- For non-Measure inventories: attempt first-party owner data.
- If owner data exists → compute fallback measure_fit and mark it as owner-sourced.
- If owner data missing → exclude inventory from recommendation set (per your instruction).
  - Rationale: you insisted earlier that if neither Measure nor first-party owner data exist, the engine should exclude the inventory. We follow that rule.

**UX / Warnings**
- warnings include: "inventory excluded because no impressions data (Measure or owner) available" for transparency.

### Case 6.2 — User provides no brand, provides budget and dates

**Behavior**
- Skip brand_fit (treat neutral).
- Weight emphasis to measure_fit and geo_fit.
- Return top-N high-reach, high-availability items and a budget-pack suggestion.
- why should include: "No brand provided—recommendations prioritized for reach & budget efficiency."

### Case 6.3 — Brand provided but ambiguous / "test" / "demo"

**Behavior**
- Quick filter: check against brand_registry.
- If brand not found but name suspicious (contains "test", "demo", auto-generated) → ignore brand.
- If brand not found and looks like real name → call Gemini to try to map to categories. Use confidence threshold 0.75. If Gemini low-confidence → ignore brand and set warning.

### Case 6.4 — Partial availability (inventory partially booked)

**Behavior**
- Compute availability fraction and prorate cost_estimate and estimated_imps.
- availability < 10% → exclude (too little inventory).
- 10% ≤ availability < 80% → keep but show availability_summary and penalize availability component accordingly.

**Packer behavior**
- If selected via budget packer and only partial slots are available, continue by picking next best inventory to fill budget.

### Case 6.5 — Price model mix (monthly vs CPM vs CPT) across inventories

**Behavior**
- Convert each inventory into campaign_cost_estimate for the requested date window using the rules in section 5.6.
- Use Measure impressions to estimate cost_per_impression where possible.
- budget_fit uses campaign_cost_estimate.

**Edge**
- If inventory uses CPM but Measure missing (yet owner provides imps) → compute cost using owner-provided impressions.
- If both missing → exhaustion → exclude inventory.

### Case 6.6 — Tight geofence returns zero inventories

**Behavior**
- If no inventory passes the availability & impression checks, return:
  - status: no_matches
  - suggestions: list of nearest alternatives with distance and estimated impressions.
  - UX prompt: "Expand radius by X km or broaden dates."

### Case 6.7 — API consumer wants deterministic but diverse results

**Behavior**
- We produce seeded jitter based on run_id.
- Document jitter algorithm to API consumers.
- If consumer wants identical results each time, accept seed parameter in API call to control deterministic behavior.


## 7. AI Integration — detailed rules for Gemini & fallback to OpenAI

We use AI only for classification, embeddings, and short explainability text. We never use AI to compute numeric impressions, pricing, or availability. AI must be auditable: every classification stores raw input, raw output, confidence, and timestamp.

### 7.1 Use cases for Gemini

1. **Site-type classification when site_type missing:**
   - Input: inventory_name, inventory_description, nearby_POI_list, locale_code, SSP_metadata
   - Output: site_type + site_confidence + 3 keywords + optional short explanation.
   - Accept if site_confidence >= 0.75.

2. **Brand → category mapping when brand ambiguous:**
   - Input: brand name, brand description (if available), brand synonyms
   - Output: category + confidence + tags.

3. **Explainability text:**
   - Input: top-3 signals + inventory name + availability summary
   - Output: 1–2 sentence why string, limited to 120 characters, deterministic for the run_id.
   - Keep template fallback in case LLM fails.

4. **Embeddings for similarity:**
   - Use embeddings (Gemini) for brand ↔ inventory keyword similarity. Store embeddings in vector DB to avoid repeated calls.

### 7.2 When to call OpenAI

OpenAI is only used as a fallback when Gemini fails (timeout or returns error) and the engine truly needs an LLM for classification or explainability. This keeps cost and dependency controlled.

### 7.3 Examples of AI inputs & outputs

**Example call for site-type classification**

Input:
```
"Terminal 3 - LED Gantry - East Approach. Located at NAIA Terminal 3 loop road. Visible to arrivals and taxi lane. Owner: MediaCorp. Format: digital. No site_type specified."
```

Gemini output:
```json
{ 
  "site_type": "airport", 
  "confidence": 0.94, 
  "keywords": ["arrivals", "terminal", "taxi lane"], 
  "explanation": "Inventory faces arrivals loop at NAIA Terminal 3."
}
```

Accept and attach.

**Explainability input**
- Signals: measure_fit=70, geo_fit=90, availability=100
- Gemini returns: "Near NAIA T3 arrivals • strong traveler audience • fully available Dec 18–24"

If Gemini fails, we assemble the why string from templates.


## 8. Acceptance Criteria

| ID | Scenario | Expected result |
|----|----------|-----------------|
| AC-01 | Request with country + dates + brand + audience + budget | Returns top N recommendations each with score, components, why, availability_summary, forecasted_impressions (if Measure) |
| AC-02 | Candidate inventory without site_type but with name | Engine calls Gemini; if confidence ≥0.75, use site_type; otherwise fallback to keyword-map; warning present |
| AC-03 | Candidate inventory with no Measure but owner-provided imps | Use owner imps to compute measure_fit; response includes warning: owner data used |
| AC-04 | Inventory missing both Measure and owner imps | Inventory excluded and warnings show reason |
| AC-05 | Budget provided | budget_fit computed for each candidate and used in final score but not as hard filter (unless strict_budget flag set) |
| AC-06 | Partial availability | Candidate returned with prorated cost/imps and availability_summary (e.g., "6/10 days") |
| AC-07 | Brand="test" or "demo" | brand_fit ignored; warning included |
| AC-08 | Tight geofence with zero matches | API responds status: no_matches + alternative suggestions outside polygon with distances |
| AC-09 | Gemini failure | Fallback to keyword-map; if that fails, fallback to OpenAI; if both fail, proceed with neutral site_type and warning |
| AC-10 | Repeat identical request within same second | run_id seeded, results reproducible (identical) |
| AC-11 | Repeat identical request across different minutes | 10–15% variation in ranking due to deterministic jitter; run_id shows the seed |
| AC-12 | Large country-level query | Return city-clustered picks instead of full site list; warnings indicate "zoom in for site-level suggestions" |
| AC-13 | Response latency | Cached runs: 95% < 2s; uncached typical <5s |
| AC-14 | Explainability | why must reference computed signals only (no hallucination). If AI used, include ai_used: true and ai_confidence. |


## 9. Scoring pipeline

Final pipeline flow

[See attached diagram in original document]


## 10. Quick Implementation checklist (what dev & data teams must deliver)

- APO availability integration for all inventories (influence/APO).
- Measure API access and precomputed impressions for date ranges.
- Inventory canonical table with price models normalized.
- First-party owner ingestion for fallback.
- Keyword-map and initial brand_category_map.
- Gemini integration and embedding cache.
- Scoring engine with weight redistribution logic.
- Budget packer module (greedy).
- Explainability generator (templates + Gemini fallback).
- Run logging & run_id with seed.


## 11. Closing notes

This scoring model is intentionally simple to operate and explain. It is extendable: we can introduce a re-ranker ML model in Phase 2 that learns from accept/reject logs and adjusts weights per brand or account.

The jitter approach maintains predictable exploration without surprising planners.

The engine strictly excludes inventories when neither Measure nor owner first-party data exist — that rule reduces noise and avoids false positives. If you want to loosen that later, we can allow inclusion with very low base score and explicit user opt-in.


## 12. Auto Plan Creator

The Auto Plan Creator extends the Recommendation Engine to automatically create complete campaign plans. Instead of just ranking inventories, it selects inventories, allocates budget, and creates optimized schedules—all based on user inputs and campaign goals.

### 12.1 Overview

When a user provides their campaign parameters (budget, goal, targeting, brand category), the Auto Plan Creator:

1. Retrieves scored inventories from the Recommendation Engine
2. Applies smart selection logic based on venue context and goal type
3. Allocates budget across inventory types using goal-driven distribution
4. Creates optimized schedules based on lowest CPM by hour and reach curve data
5. Validates the plan meets budget constraints within +/-5% tolerance
6. Presents the plan for user review with option to override

The user can accept the auto-generated plan, modify individual selections, or switch to full manual mode.

**AI Usage Clarification**

The Auto Plan Creator uses **deterministic logic only**—no AI (Gemini/OpenAI) is called during the selection, allocation, or scheduling process. All calculations use predefined formulas, scoring weights, and allocation tables documented in this section.

AI (Gemini) is used only in the **upstream Recommendation Engine** (sections 3.4 and 7) for:
- Brand → category mapping (if brand name provided)
- Venue type inference (if venue_type field is missing)

Once inventories are scored by the Recommendation Engine, the Auto Plan Creator operates purely on scored data using arithmetic formulas. This ensures:
- Predictable, reproducible results
- Fast execution (no API latency)
- Full auditability of selection decisions

### 12.2 Goal-Driven Inventory Selection and Schedule Logic

This section explains how each campaign goal type fundamentally changes which inventories are selected and how schedules are created. The selection logic is not arbitrary—each decision is rooted in how the goal metric is calculated and what behaviors optimize it.

---

#### Goal Type 1: REACH (Maximize Unique People)

**Core Principle**: Reach is about how many *different* people see the ad. The key insight is that the same person seeing an ad multiple times does NOT increase reach—it only increases frequency. Therefore, the system must actively avoid audience repetition.

**Location Rotation Strategy**

The most effective way to maximize reach is to rotate across different locations over time rather than staying in one place for the entire campaign.

Example for a 4-week campaign in Kuala Lumpur:
- Week 1: KL Sentral transit hub (catches morning commuters)
- Week 2: Bangsar LRT station (different commuter catchment)
- Week 3: Pavilion Mall (shoppers, tourists)
- Week 4: KLCC area (business district, different demographic)

Each week reaches a largely different audience pool. If the campaign ran only at KL Sentral for all 4 weeks, the same commuters would see it repeatedly—high frequency but low reach.

**Hour Rotation Within Locations**

Even within a single location, varying the time slots helps reach different audience segments:
- 7-9 AM: Catches office workers arriving
- 12-2 PM: Catches lunch crowds and retail visitors
- 5-7 PM: Catches different office workers leaving (some work different schedules)
- 8-10 PM: Catches evening diners and entertainment seekers

A traffic signal screen near an office area illustrates this well. If the ad runs at 8 AM every day, the same people driving to work will see it daily—frequency increases but reach stagnates. Running at 8 AM on Monday, 5 PM on Tuesday, and 12 PM on Wednesday reaches three different audience segments.

**Using Reach Curve Data**

The Measure system provides reach curve data showing how unique reach accumulates over time for each inventory. A healthy reach curve climbs steadily. When the curve flattens, it signals audience saturation—the same people are being reached repeatedly.

The Auto Plan Creator monitors reach curves at the campaign level:
- If adding more budget to an inventory no longer increases reach (curve flattening), stop and reallocate to a different location
- If all locations in a city show flattening, add a new city
- The goal is to keep the campaign-level reach curve climbing throughout the campaign duration

**Venue-Specific Rules for Reach**

| Venue Type (IAB) | Max Days | Reasoning |
|------------------|----------|-----------|
| Office Building | 4-5 days | Same employees every day; saturates quickly |
| Residential/Condo | 5-7 days | Same residents; saturates quickly |
| Gym/Fitness | 5-7 days | Regulars visit 3-5x per week |
| Roadside Billboard | Full campaign | Different drivers each day; audience refreshes |
| Transit Hub | Full campaign | Mix of regular commuters and irregular travelers |
| Airport | Full campaign | Travelers are mostly one-time visitors |
| Mall (Destination) | 10-14 days | Weekly shoppers; moderate repetition |
| Mall (Convenience/Daily) | 5-7 days | Daily visitors; saturates faster |

---

#### Goal Type 2: AD PLAYS (Maximize Display Count)

**Core Principle**: Ad plays is the count of how many times the ad is displayed on a screen. The goal is to maximize this count within the user's budget and (if specified) within the target audience's environment.

**Pricing Model: CPS (Cost Per Slot)**

For Ad Plays goals, the system uses **CPS (Cost Per Slot)** pricing rather than CPM. This is because ad plays are about securing screen time, not impressions delivered.

- CPS pricing = cost per slot × number of slots purchased
- Total ad plays = slots purchased × loops per hour × operating hours × campaign days

**Three Key Factors: Slot Duration + Time + Budget**

Ad plays optimization requires balancing three interdependent factors:

| Factor | Impact on Ad Plays | Trade-off |
|--------|-------------------|-----------|
| Slot Duration | Shorter slots = more plays per hour | Shorter slots cost more per play but deliver higher counts |
| Operating Hours | More hours = more plays per day | 18-hour screens cost more than 12-hour screens |
| Campaign Days | More days = more total plays | Must fit within budget |

**Calculation Example:**

Screen A: 10-second slots, 18-hour operation, CPS = MYR 5
- Slots per hour: 60 min × 60 sec ÷ 10 sec = 360 potential slots/hour (at 100% SOV)
- User buys 10% SOV = 36 slots/hour
- Daily plays: 36 × 18 hours = 648 ad plays
- Cost per day: 36 slots × MYR 5 = MYR 180

Screen B: 30-second slots, 12-hour operation, CPS = MYR 3
- Slots per hour: 60 min × 60 sec ÷ 30 sec = 120 potential slots/hour
- User buys 10% SOV = 12 slots/hour
- Daily plays: 12 × 12 hours = 144 ad plays
- Cost per day: 12 slots × MYR 3 = MYR 36

Screen A delivers **4.5x more ad plays** but costs **5x more** per day. The system uses budget_fit scoring to find the optimal balance.

**Audience Consideration (When Targeted)**

If the user specifies a target audience, the system applies audience_fit filtering BEFORE selecting for ad plays:
1. Filter inventories to those with audience_fit > 0.5 (relevant audience present)
2. Within filtered set, optimize for ad plays count

Example: User targets "young professionals"
- Office lobby screens (high audience_fit for young professionals) + good loop frequency = selected
- Shopping mall screens (generic audience) + higher loop frequency = deprioritized despite more plays

**Budget Allocation Within Ad Plays Goal**

The system uses budget_fit score (section 5.6) to maximize plays within budget:
1. Calculate ad plays per MYR spent for each inventory
2. Rank by efficiency: plays per MYR (not just total plays)
3. Select inventories starting from most efficient until budget exhausted

| Inventory | Daily Plays | Daily Cost | Plays/MYR | Selection Order |
|-----------|-------------|------------|-----------|-----------------|
| Screen A | 648 | MYR 180 | 3.6 | 2nd |
| Screen B | 144 | MYR 36 | 4.0 | 1st (most efficient) |
| Screen C | 800 | MYR 300 | 2.7 | 3rd |

**Selection Priority for Ad Plays**

| Inventory Type | Priority | Reasoning |
|----------------|----------|-----------|
| Digital Screen Network (short slots) | Highest | Maximum loop frequency, often 10-second slots |
| Transit Digital (station screens) | High | Continuous operation, short slot durations |
| Retail Digital (in-store) | High | High loop frequency, captive audience |
| Office Lobby Digital | Medium | Good loop frequency but limited hours |
| Large Format Digital Billboard | Lower | Often 15-30 second slots (fewer plays) |
| Classic/Static | Excluded | Static displays have no "plays" concept |

---

#### Goal Type 3: IMPRESSIONS (Maximize Total Ad Views)

**Core Principle**: Impressions = ad plays × audience per play. Unlike reach (unique people), impressions count every viewing—if the same person sees the ad 10 times, that's 10 impressions.

**Pricing Model: CPM (Cost Per Mille)**

For Impressions goals, the system uses **CPM (Cost Per 1,000 Impressions)** pricing. This aligns cost directly with the goal metric.

- Impression-based cost = (impressions delivered ÷ 1,000) × CPM rate
- Budget efficiency = impressions per MYR = 1,000 ÷ CPM

**Budget Efficiency Calculation**

The system calculates impressions per MYR spent for each inventory:

| Inventory | CPM Rate | Impressions/MYR | Selection Priority |
|-----------|----------|-----------------|-------------------|
| Highway Billboard | MYR 8 | 125 per MYR | High efficiency |
| Mall Digital | MYR 15 | 67 per MYR | Medium efficiency |
| Airport Screen | MYR 25 | 40 per MYR | Lower efficiency |

Lower CPM = more impressions per budget = higher selection priority.

**High Traffic Locations Win**

The system prioritizes locations where the most eyeballs pass the screen:
- Major highway billboards (100,000+ daily vehicle counts)
- Transit hubs during peak hours
- Shopping mall entrances on weekends
- Airport arrival halls

**Audience per Play is the Multiplier**

Two screens with identical ad plays can deliver vastly different impressions:
- Screen A: 1,000 ad plays/day × 50 viewers per play = 50,000 impressions
- Screen B: 1,000 ad plays/day × 500 viewers per play = 500,000 impressions

Screen B delivers 10x more impressions because of its location and visibility.

**Selection Priority for Impressions**

| Inventory Type | Priority | Reasoning |
|----------------|----------|-----------|
| Highway Digital Billboard | Highest | Massive vehicle counts, often lower CPM |
| Major Transit Hub | High | High footfall, peak hour multiplier |
| Airport (arrival/departure) | High | Concentrated audience, high dwell time |
| Mall Entrance | Medium-High | Weekend spikes, good visibility |
| Urban LED Screen | Medium | Variable traffic depending on location |
| Office Lobby | Lower | Limited audience (building occupants only) |

---

#### Goal Type 4: SOV (Share of Voice)

**Core Principle**: SOV is the percentage of total advertising time/slots that your ad occupies. If a loop has 6 slots and you buy 2, your SOV is 33%.

**Pricing Model: CPS (Cost Per Slot)**

For SOV goals, the system uses **CPS (Cost Per Slot)** pricing. SOV is about securing a share of available slots, so slot-based pricing is the natural fit.

- SOV cost = (target SOV × total slots) × CPS × loops per hour × hours × days
- Higher SOV = more slots purchased = higher cost

**Controllability is Essential**

SOV requires precise control over how many slots you purchase. This favors:
- Programmatic inventory with real-time availability
- Digital screens where slot counts are well-defined
- Inventory sold by slot/play rather than by time period

**Calculation for SOV Goal**

If goal is 25% SOV:
- Inventory has 60-second loop with 6 slots = 6 ad plays per loop
- 25% SOV = 1.5 slots per loop → Round to 2 slots (33% actual SOV)
- Cost = 2 slots × CPS × loops_per_hour × hours × days

**Budget Efficiency for SOV**

The system calculates SOV percentage achievable per MYR. This is a simplified efficiency proxy for ranking purposes—actual cost calculations use the full formula (slots × CPS × loops × hours × days).

| Inventory | CPS Rate | Slots/Loop | Efficiency Proxy | Selection Priority |
|-----------|----------|------------|------------------|-------------------|
| Office Lobby | MYR 3 | 4 | High (low CPS) | 1st |
| Transit Screen | MYR 5 | 6 | Medium | 2nd |
| Large Format | MYR 15 | 4 | Lower (high CPS) | 3rd |

The system selects inventories where the target SOV can be achieved within budget, prioritizing those with lower CPS rates.

---

#### Goal Type 5: CARBON EMISSION (Minimize Environmental Impact)

**Core Principle**: Minimize the total CO2 produced by the campaign. Every inventory has CO2 data (co2PerPlayKg field) based on power consumption, screen type, and energy source.

**CO2 Ranking Within Format Allocations**

The system applies standard format allocation first, then uses CO2 data to rank inventories within each format bucket:
- Within Digital Screen budget: Select lowest-CO2 digital screens first (e.g., solar-powered LED < standard LED < plasma)
- Within Transit budget: Select lowest-CO2 transit options
- And so on for each format

**Static Formats Have Lower Carbon Footprint**

Classic billboards (static) consume no energy for display—they use ambient light. Budget allocation shifts toward:
- Classic: 45% (up from typical 25%)
- Transit: 30%
- Digital: 5% (reduced from typical 35%)

Within that 5% digital allocation, the greenest digital screens are selected first.

---

#### Venue Type Context (IAB Taxonomy)

The system uses IAB venue type taxonomy to classify inventory locations. When venue type is known, it informs selection rules:

| IAB Venue Type | Typical Audience Pattern | Selection Implication |
|----------------|--------------------------|----------------------|
| Transit: Airport | Low repeat (travelers) | Excellent for reach |
| Transit: Rail/Metro Station | Moderate repeat (commuters) | Limit days for reach goal |
| Retail: Shopping Center | Weekly repeat | Moderate reach efficiency |
| Retail: Convenience Store | Daily repeat | Lower reach efficiency |
| Point of Care: Gym | 3-5x/week repeat | Limit to 5-7 days for reach |
| Office Building | Daily repeat (staff) | Limit to 4-5 days for reach |
| Residential | Daily repeat (residents) | Limit to 5-7 days for reach |
| Billboard: Roadside | Low repeat (varied traffic) | Excellent for reach |
| Billboard: Highway | Very low repeat | Excellent for reach |

**When Venue Type is Unknown**

If an inventory lacks venue type classification:
1. System attempts AI inference using inventory name, coordinates, and nearby POI (section 3.4)
2. If inference confidence < 75%, venue type rules are not applied
3. Inventory is still selectable but receives neutral treatment (no venue-specific day limits)

### 12.3 Budget Allocation by Classification, Type, and Format

The Auto Plan Creator distributes budget using a hierarchical allocation system based on the MW Planner Inventory Taxonomy.

---

#### Inventory Taxonomy (Three Levels)

**Level 1: Classification** - The broadest categorization:
| Classification | Description |
|----------------|-------------|
| **Digital** | Any inventory with electronic/LED/LCD display capability |
| **Classic** | Static, printed, or non-electronic displays |
| **Audio** | Sound-based advertising (radio, podcasts, in-store audio) |

**Level 2: Type** - Categorization by venue/placement context:
| Type | Description | Example Classifications |
|------|-------------|------------------------|
| **OOH** | Outdoor/out-of-home placements | Digital or Classic |
| **Transit** | Transportation-related venues | Digital or Classic |
| **Retail** | Shopping and commercial spaces | Digital or Classic |
| **Network** | Connected multi-screen systems | Digital only |
| **Radio** | Audio broadcast | Audio only |
| **Experiential** | Interactive, ambient, pop-up | Digital or Classic |

**Level 3: Format** - Specific inventory format within each type:
| Type | Example Formats |
|------|-----------------|
| OOH | Digital Billboard, LED Screen, Bulletin, Wallscape, 48 Sheet, 6 Sheet Poster |
| Transit | Airport Digital, Bus Exterior, Train Wrap, Station Digital, Taxi Top |
| Retail | Mall Digital Screen, Floor Graphics, Shelf Talker, Shopping Cart Ad |
| Network | Airport Terminal Screens, Office Lobby Network, Elevator Screen |
| Radio | AM/FM Radio Spot, Streaming Audio, Podcast Sponsorship |
| Experiential | Pop-up Activation, Projection Mapping, Experiential Booth |

---

#### Budget Allocation Logic

**Step 1: Allocate by Classification Based on Goal + Pricing Model**

The first allocation level determines how much budget goes to Digital vs Classic vs Audio. This allocation is influenced by:
1. Which classification can achieve the goal metric
2. The pricing model applicable for the goal (CPM for Impressions, CPS for Ad Plays/SOV)
3. Budget efficiency within each classification

| Goal Type | Pricing Model | Digital | Classic | Audio |
|-----------|---------------|---------|---------|-------|
| Impressions | CPM | 60% | 35% | 5% |
| Reach | CPM | 55% | 40% | 5% |
| Ad Plays | CPS | 95% | 0% | 5% |
| SOV | CPS | 95% | 0% | 5% |
| Carbon Emission | CPM | 20% | 75% | 5% |

**Classification Selection Priority Order**

Inventory selection happens sequentially by classification priority, NOT by overall score ranking:

| Priority | Classification | Selection Rule |
|----------|----------------|----------------|
| 1st | **Digital** | Select top-scored Digital inventories until Digital budget cap exhausted |
| 2nd | **Classic** | Select top-scored Classic inventories until Classic budget cap exhausted |
| 3rd | **Audio** | Select top-scored Audio inventories until Audio budget cap exhausted (lowest priority) |

**Critical Rule**: Audio inventories are always lowest priority regardless of their individual scores. An Audio inventory with score 95 will NOT be selected before a Digital inventory with score 70 if Digital budget is not exhausted.

**Audio Availability Note**: Not all countries have Audio inventory. If no Audio inventory exists in the selected country, the Audio budget allocation is redistributed to Digital and Classic proportionally.

*Rationale*:

- **Impressions (CPM)**: Digital screens in high-traffic areas deliver highest impression counts per MYR. Classic adds geographic coverage. Allocation based on CPM efficiency across both.

- **Reach (CPM)**: **Both Digital and Classic can achieve reach effectively** depending on placement. Digital in transit/airports reaches varied travelers. Classic on highways reaches different drivers daily. The allocation is balanced (55/40) because:
  - Digital transit locations (airports, stations) have naturally high audience turnover
  - Classic roadside locations also have high turnover but less audience data
  - Slight preference for Digital because it enables better reach tracking/optimization

- **Ad Plays (CPS)**: Only Digital can generate "plays"—Classic is static and cannot produce this metric. 95% Digital; 5% Audio (radio spots count as plays).

- **SOV (CPS)**: Only Digital allows precise slot control required for SOV targeting. Classic has no slots. 95% Digital; 5% Audio.

- **Carbon Emission (CPM)**: Classic uses zero energy for display; heavily weighted toward Classic to minimize environmental impact.

**Step 2: Allocate by Type Within Each Classification**

Within the Digital classification allocation:

| Goal Type | OOH | Transit | Retail | Network | Justification |
|-----------|-----|---------|--------|---------|---------------|
| Impressions | 40% | 30% | 15% | 15% | OOH billboards have highest visibility and traffic volume |
| Reach | 35% | 35% | 15% | 15% | Transit + OOH have highest audience turnover (varied travelers/drivers) |
| Ad Plays | 20% | 25% | 25% | 30% | Network screens have highest loop frequencies and shortest slots |
| SOV | 20% | 25% | 25% | 30% | Network screens offer best programmatic slot control |

Within the Classic classification allocation:

| Goal Type | OOH | Transit | Retail | Justification |
|-----------|-----|---------|--------|---------------|
| Impressions | 60% | 30% | 10% | Roadside billboards have highest traffic exposure |
| Reach | 55% | 35% | 10% | Highway + transit posters reach varied audiences daily |
| Carbon Emission | 70% | 20% | 10% | All classic is zero-energy; OOH has widest coverage |

**How budget_fit Score Influences Selection Within Each Bucket**

After budget is allocated to each Type bucket, the system uses the budget_fit score (section 5.6) to select specific inventories:

1. **Calculate goal efficiency** for each inventory in the bucket:
   - For Impressions (CPM pricing): Impressions per MYR = 1,000 ÷ CPM
   - For Ad Plays (CPS pricing): Plays per MYR = daily_plays ÷ daily_cost
   - For SOV (CPS pricing): CPS efficiency ranking (lower CPS = higher efficiency)
   - For Reach (CPM pricing): Unique reach per MYR (from Measure data)
   - For Carbon (CPM pricing): CO2 saved per MYR = baseline_CO2 - inventory_CO2

2. **Combine with recommendation score**: Final selection score = (0.6 × efficiency_rank) + (0.4 × recommendation_score)

   *Note: The 60/40 weighting is a tunable heuristic. Higher efficiency weight prioritizes budget optimization; higher recommendation weight prioritizes audience/quality fit.*

3. **Select inventories** in descending order of final selection score until bucket budget is exhausted

**Pricing Model Selection Based on Selling Terms**

The pricing model (CPM vs CPS) is determined by the inventory's selling terms:
- If inventory is sold by impressions → use CPM
- If inventory is sold by slot/play → use CPS
- If both are available, use the model aligned with the campaign goal (CPM for Impressions/Reach, CPS for Ad Plays/SOV)

This ensures the system selects inventories that are BOTH highly scored (quality, relevance) AND budget-efficient for the selected goal.

---

#### Handling Missing Types or Formats in Geography

When a type or format does not exist in the selected geography, the system redistributes that allocation proportionally.

**Scenario: No Transit inventory in selected region**

Original allocation (Impressions goal, Digital):
- OOH: 40% → MYR 40,000
- Transit: 30% → MYR 30,000
- Retail: 15% → MYR 15,000
- Network: 15% → MYR 15,000

After redistribution (Transit's 30% distributed proportionally):
- OOH: 40% + (40/70 × 30%) = 40% + 17.1% = **57.1%** → MYR 57,100
- Retail: 15% + (15/70 × 30%) = 15% + 6.4% = **21.4%** → MYR 21,400
- Network: 15% + (15/70 × 30%) = 15% + 6.4% = **21.4%** → MYR 21,400

The system shows a warning: "No Transit inventory available in [Geography]. Budget redistributed to other types."

**Scenario: Very limited inventory options**

If a region has only OOH Classic inventory (small town with only static billboards):
- All applicable budget goes to available inventory
- User is warned: "Limited inventory options in [Region]. Only Classic OOH available."
- System suggests adding nearby cities if budget allows

---

#### Allocation Justification

Why these percentages? Each allocation is justified by how the goal is measured:

| Goal | Primary Allocation | Justification |
|------|-------------------|---------------|
| Impressions | Digital OOH (40%) | High-traffic digital screens deliver highest impression counts per dollar |
| Reach | Classic OOH (65% of Classic) | Varied roadside locations reach different drivers daily; minimal audience overlap |
| Ad Plays | Network (30%) | Connected screen networks have highest loop frequencies and shortest slot durations |
| SOV | Network (30%) | Programmatic networks allow precise slot control needed for SOV targeting |
| Carbon | Classic OOH (70% of Classic) | Static billboards use zero energy for display; lowest carbon footprint |

---

#### Step 3: Select Inventories Within Each Bucket

After allocation by Classification → Type, the system selects specific inventories within each bucket using:

1. **Recommendation Engine Score** (sections 1-10) - Primary ranking
2. **Venue type rules** (section 12.2) - Day limits for reach goal
3. **Selling terms validation** (section 12.4) - Operating hours, minimums
4. **Geographic diversity** (Part H.1) - City distribution requirements

**Step 4: Validate Budget Constraints**

Ensure total allocation matches user budget within tolerance (see Part F for tiered tolerance rules):
- Over budget: Remove lowest-scoring inventories until within tolerance
- Under budget: Add next-best inventories or increase schedule density

### 12.4 Smart Schedule Creation

The Auto Plan Creator generates schedules optimized for cost efficiency and goal achievement.

**CPM-Based Hour Selection**

For each selected inventory, analyze hourly CPM data and select hours with lowest effective CPM while maintaining goal delivery.

Steps:
1. Retrieve hourly impression data from Measure
2. Retrieve hourly pricing (if variable) or calculate prorated cost
3. Calculate effective CPM for each hour: (hourly_cost / hourly_impressions) × 1000
4. Rank hours by CPM efficiency
5. Select hours starting from lowest CPM until budget allocation for that inventory is consumed
6. Ensure minimum coverage across campaign dates

**Goal-Specific Schedule Optimization**

For **Reach** goal:
- Rotate hours across different time slots (morning one day, evening next day)
- Avoid booking the same hours every day (prevents reaching same commuters)
- See section 12.2 for location and hour rotation strategy

For **Ad Plays** goal:
- Maximize operating hours coverage (more hours = more plays)
- Prioritize screens with shorter slot durations (10s slots > 30s slots)

For **Impressions** goal:
- Weight toward peak traffic hours (highest audience per play)
- Focus on hours with best CPM efficiency

**Schedule Density Calculation**

For each inventory, calculate required ad plays to consume allocated budget:

`required_plays = allocated_budget / cost_per_play`

Distribute plays across selected hours:

`plays_per_hour = required_plays / selected_hours_count`

Adjust for hourly impression patterns to maximize reach or frequency based on goal.

---

### 12.5 Manual Override Option

The Auto Plan Creator generates an optimized plan, but users retain full control to override any decision. Manual adjustments are common and expected—the auto-generated plan is a starting point, not a mandate.

**Available Overrides:**
- Remove any auto-selected inventory
- Add inventories not in the recommendation (warning shown if score is low)
- Modify budget allocation percentages by type
- Edit schedules per inventory (hours, days)
- Switch entirely to manual mode (disable auto-planning)

When overrides are made, the system recalculates totals and forecasted metrics accordingly. Warnings are shown when overrides conflict with goal optimization (e.g., "Adding more office screens may reduce reach efficiency for your reach goal").

---

### 12.6 Comprehensive System Logic

This section documents the complete decision-making logic of the Auto Plan Creator, covering all scenarios, edge cases, and how it integrates with the Recommendation Engine scoring defined in sections 1-10.

---

#### PART A: RELATIONSHIP WITH RECOMMENDATION ENGINE (Sections 1-10)

**Critical Principle: Auto Plan Creator Does Not Reinvent Scoring**

The Auto Plan Creator USES the scores already calculated by the Recommendation Engine. It does not create its own scoring system.

Step-by-step relationship:

1. Recommendation Engine (sections 1-10) first calculates scores for all inventories:
   - measure_fit (impressions/reach delivery)
   - geo_fit (location relevance)
   - availability (schedule availability)
   - budget_fit (cost efficiency)
   - audience_fit (audience match)
   - brand_fit (brand category match)
   - quality_fit (screen quality)
   - time_fit (daypart alignment)

2. These 8 components are combined into a final_score (0-100) per inventory using the weight table in section 5.11.

3. Auto Plan Creator then RECEIVES this scored list and applies additional selection logic:
   - Venue-based filtering (office limits for reach)
   - Goal-value consideration (how much each inventory contributes toward target)
   - Budget allocation by type
   - Schedule creation respecting selling terms

4. The score displayed on each billboard IS the Recommendation Engine score, not a separate Auto Plan score.

**Why This Matters**

If a user manually browses inventories, they see scores calculated by sections 1-10. If they use Auto Plan Creator, they see the SAME scores. Consistency is critical. Auto Plan Creator is an automation layer on top of the Recommendation Engine, not a replacement.

---

#### PART B: COMPLETE GOAL TYPE HANDLING

The system supports 6 goal types. Each affects how inventories are selected and schedules are created.

**Goal Type 1: Impressions**

User wants to maximize total ad views.

Selection priority:
- High-traffic locations first (roadside, transit, malls)
- Digital screens with high ad play counts
- Large format billboards with high visibility

Budget allocation:
- Digital high-traffic: 35%
- Roadside/outdoor: 30%
- Transit: 20%
- Mall/retail: 10%
- Office/residential: 5%

When goal VALUE is provided (e.g., "10 million impressions"):
- Calculate contribution fraction per inventory: inventory_impressions / goal_value
- An inventory delivering 500K impressions toward a 10M goal contributes 5%
- Select inventories until cumulative contribution reaches 100% or budget exhausted
- If budget cannot deliver goal value, show warning: "Selected inventories forecast 8.2M impressions against 10M goal. Consider increasing budget."

**Goal Type 2: Reach**

User wants ads seen by as many different people as possible.

Selection priority:
- Locations with refreshing audiences (roadside where different cars pass daily)
- Avoid locations with captive audiences (office buildings where same staff passes)
- Prioritize high-traffic varied locations

Budget allocation:
- Roadside/outdoor: 40%
- Transit: 25%
- Mall/retail: 15%
- Airport/travel: 12%
- Office/residential: 8%

Venue-specific limits:
- Office screens: Maximum 4-5 days regardless of campaign length (audience saturates)
- Condo/residential: Maximum 5-7 days
- Gyms: Maximum 7 days (regulars visit 3-5x/week)

When goal VALUE is provided (e.g., "1 million unique people"):
- Use reach curve data to estimate unique reach per inventory
- Calculate contribution: inventory_reach / goal_value
- Apply reach saturation logic: if adding more budget to an inventory type no longer increases reach, stop and reallocate

**Goal Type 3: SOV (Share of Voice)**

User wants their ad to occupy a certain percentage of available ad time.

Selection priority:
- Focus on inventories where SOV can be guaranteed
- Prefer programmatic inventory with real-time availability
- Avoid fully booked or high-competition inventory

Budget allocation:
- Distribute evenly across selected inventory types
- No strong preference for venue type (SOV is about time share, not audience)

When goal VALUE is provided (e.g., "25% SOV"):
- Calculate required ad plays per inventory to achieve 25% of total loop
- If inventory has 100 ad plays per hour and you need 25%, you need 25 plays per hour
- Cost = 25 plays × cost_per_play × hours
- Select inventories where achieving target SOV fits within budget

**Goal Type 4: Ad Plays**

User wants to maximize the number of times their ad is displayed.

Selection priority:
- Digital screens with high loop frequency (more plays per hour)
- Network screens in high-footfall locations (airports, malls, offices)
- Inventories with 24/7 or extended operating hours

Budget allocation:
- Digital Screen: 40%
- Network: 30%
- Retail: 15%
- Transit: 10%
- Classic: 5%

When goal VALUE is provided (e.g., "1,000,000 ad plays"):
- Calculate contribution: inventory_plays_per_day × campaign_days / goal_value
- An inventory delivering 10,000 plays/day for 30 days = 300,000 plays = 30% of goal
- Select inventories until cumulative plays reach 100% of goal or budget exhausted
- If budget cannot deliver goal value, show warning: "Selected inventories forecast 800,000 plays against 1,000,000 goal."

**Example: Ad Plays Goal Calculation**

Campaign: 30 days, goal = 500,000 ad plays
- KLCC Digital (60 plays/hour × 18 hours × 30 days) = 32,400 plays → 6.5% of goal
- KLIA Network (60 plays/hour × 18 hours × 30 days × 23 screens) = 745,200 plays → **exceeds goal alone**
- For Ad Plays goal, KLIA Network alone could deliver 149% of target

**Goal Type 5: Carbon (Environmental Impact)**

User wants to minimize or track carbon footprint of the campaign.

**CO2 Data Availability**: All inventories have CO2 emission data (co2PerPlayKg field). Digital screens have varying CO2 values based on screen size, power consumption, and energy source. This enables intelligent selection within every format category.

Selection priority:
- Prefer inventories with low CO2-per-play metrics
- Favor solar-powered or energy-efficient screens
- Within Digital Screen allocation, select lowest-CO2 digital screens first

Budget allocation:
- Apply standard format allocation, then use CO2 data to rank inventories within each format
- Within each format bucket, sort by CO2 ascending (lowest first)
- May accept slightly lower reach/impressions for better sustainability score

When goal VALUE is provided (e.g., "Maximum 50kg CO2"):
- Calculate cumulative CO2 for each inventory based on co2PerPlayKg × estimated_plays
- Stop adding inventories when cumulative CO2 approaches limit
- Show comparison: "This plan produces 45kg CO2. A typical plan would produce 120kg CO2."

**Example: CO2-based selection within Digital Screen budget**

Digital Screen allocation: MYR 15,000 for Carbon Emission goal

Available digital screens in KL:
| Screen | CO2/Play (kg) | Score | Cost |
|--------|---------------|-------|------|
| KLCC LED | 0.008 | 92 | MYR 35,000 |
| Pavilion Solar-LED | 0.003 | 87 | MYR 22,000 |
| Mid Valley Standard | 0.012 | 82 | MYR 15,000 |

Selection order (CO2 ascending): Pavilion Solar-LED first (0.003), then KLCC LED (0.008), then Mid Valley (0.012). Budget of MYR 15,000 cannot afford Pavilion, so skip to smaller options or allocate partial days.

---

#### PART C: WHEN NO GOAL IS SELECTED

**Scenario: User skips goal selection entirely**

This is common—many users just want to buy some billboard time without a specific objective.

Fallback behavior:

1. Default to impressions-based optimization (maximize ad views per dollar)

2. Use balanced budget allocation:
   - Roadside/outdoor: 30%
   - Transit: 25%
   - Mall/retail: 20%
   - Digital high-traffic: 15%
   - Office/residential: 10%

3. No venue-specific day limits (office can run full campaign duration)

4. Score inventories using pure Recommendation Engine scores without goal-based weight adjustments

5. Show informational message: "No campaign goal specified. Optimizing for maximum ad views. For better targeting, consider setting a specific goal."

**Why Not Force Goal Selection?**

Some buyers are media owners testing the system, some are agencies with pre-defined plans, and some genuinely don't have a measurable goal. Forcing selection creates friction. The system should work without it.

---

#### PART D: GOAL VALUE CONSIDERATION IN SELECTION

**The Problem**

Knowing the goal TYPE is not enough. A campaign targeting 100,000 impressions should select different inventories than one targeting 10,000,000 impressions.

**How Goal Value Affects Selection**

Step 1: Calculate required inventory volume

If goal_type = impressions and goal_value = 10,000,000:
- Estimate impressions per dollar based on available inventory CPM
- If average CPM is $5, then $1000 = 200,000 impressions
- Budget of $50,000 = ~10,000,000 impressions
- This is achievable → proceed normally

If budget only supports 5,000,000 impressions:
- Show warning: "Budget may not achieve target. Expected: 5M vs Target: 10M"
- Option 1: Reduce goal and proceed
- Option 2: Focus on highest-CPM-efficiency inventories
- Option 3: User adds budget

Step 2: Contribution-weighted selection (Selection Heuristic, NOT a New Score)

**Important Clarification**: This is a SELECTION HEURISTIC, not a new scoring system. The Recommendation Engine score (0-100) remains the primary ranking. Contribution weighting is only used as a tie-breaker when multiple inventories have similar scores but different goal contributions.

Each inventory is evaluated on its CONTRIBUTION to the goal:
- Inventory A: 500K impressions / 10M goal = 5% contribution
- Inventory B: 200K impressions / 10M goal = 2% contribution
- Inventory C: 1.2M impressions / 10M goal = 12% contribution

Selection priority logic:
1. First, sort by Recommendation Engine score (highest first)
2. Among inventories with similar scores (within 5 points), prefer higher contribution
3. Example: Inventory A (score 90) is selected before Inventory C (score 85), even though C contributes more. But if both had score 88, prefer C for its higher contribution.

This preserves the "Auto Plan Creator does not reinvent scoring" principle from Part A while optimizing for goal achievement.

Step 3: Stop when goal is met

Once cumulative contribution reaches 100% (or budget exhausted), stop adding inventories. Leftover budget (if any) can be:
- Returned to user
- Used to increase schedule density on selected inventories
- Allocated to secondary goal (e.g., add reach once impressions goal is met)

---

#### PART E: INVENTORY SELLING TERMS VALIDATION

**Why Selling Terms Matter**

Each inventory has operational constraints that affect whether a schedule is valid:

1. Operating Hours: Screen only runs 6 AM to 10 PM
2. Operating Days: Mall screens closed on certain holidays
3. Minimum Booking: Must book at least 3 days
4. Loop Length: 10-second slots in a 60-second loop (6 plays per loop)
5. Spots Per Loop: Some inventories sell 0.25, 0.5, or 1 spot per loop
6. Minimum Spend: Some premium screens have $500/day minimum

**Validation During Schedule Creation**

When Auto Plan Creator generates a schedule, it MUST check:

Check 1: Operating Hours
- If inventory operates 6 AM - 10 PM, do not schedule 11 PM - 5 AM hours
- If user selects hours outside operating range, show warning
- Auto Plan Creator should only select operating hours by default

Check 2: Operating Days
- If inventory is closed on Sundays, do not schedule Sundays
- Holiday closures should be respected
- Check operating days from inventory data

Check 3: Minimum Booking Requirements
- If inventory requires 3-day minimum and campaign is 2 days, exclude this inventory
- Or extend booking to meet minimum and show cost adjustment

Check 4: Loop and Spot Constraints
- Calculate ad plays based on loop length and spots per loop
- A 60-second loop with 10-second spots = 6 slots per loop
- If buying 1 spot per loop and loop plays 60 times per hour = 60 ad plays per hour
- Schedule density cannot exceed inventory capacity

Check 5: Minimum Spend
- If daily minimum is $500 and allocated budget per inventory is $300, either:
  - Allocate more budget to meet minimum
  - Exclude this inventory
  - Show warning and let user decide

Check 6: Minimum Hours
- Some inventories require minimum hours per booking slot
- Example: KLIA airport screens require 8-hour minimum booking blocks
- If user wants only 2 hours, exclude inventory or extend to 8 hours with cost adjustment
- Minimum hours are separate from operating hours—an inventory may operate 18 hours but require 4-hour minimum blocks

**Example: Selling Terms Conflict Resolution**

Campaign: 3 days, 2 hours per day (8 AM - 10 AM)
Inventory: Office lobby screen

Selling terms:
- Operating hours: 7 AM - 7 PM (12 hours)
- Minimum days: 7 days
- Minimum hours: 4 hours per day
- Minimum spend: MYR 500/day

Conflict 1: Campaign is 3 days but minimum is 7 days
→ Option A: Exclude inventory
→ Option B: Extend to 7 days, show new cost (MYR 3,500 vs MYR 1,500)

Conflict 2: Campaign is 2 hours but minimum is 4 hours
→ Option A: Exclude inventory
→ Option B: Extend to 4 hours, recalculate impressions

Resolution: Auto Plan Creator excludes this inventory because extending both days AND hours would significantly exceed user expectations. Show in recommendations as "Available with modifications" with explanation.

**Schedule Creation Flow With Validation**

**Note**: This flow uses the existing schedule validation utilities defined in `shared/schedule-types.ts`, including `InventorySchedule` interface fields (operatingStartHour, operatingEndHour, operatingDays, spotsPerLoop, spotsPerHour) and the `validateScheduleAgainstOperatingHours` function. Auto Plan Creator should call these existing validators rather than implementing duplicate logic.

1. Get inventory operating hours and days (from InventorySchedule.operatingStartHour/operatingEndHour/operatingDays)
2. Filter available hours to only operating hours
3. Get hourly CPM data for operating hours
4. Sort by CPM efficiency (lowest first)
5. Calculate required ad plays to consume allocated budget (using spotsPerLoop and loop frequency)
6. Validate plays per hour does not exceed inventory capacity
7. Distribute plays across selected hours
8. Validate total meets minimum booking requirements
9. Call validateScheduleAgainstOperatingHours to check for out-of-bounds selections
10. If any validation fails, remove inventory from plan and redistribute budget

---

#### PART F: BUDGET TOLERANCE RULES

**The Problem with Flat 5% Tolerance**

5% of $10,000 = $500 → Acceptable variance
5% of $100,000 = $5,000 → Still acceptable
5% of $1,000,000 = $50,000 → May be significant
5% of $10,000 in USD = $500, but 5% of 10,000 MYR ≈ $2,200 USD equivalent

A flat percentage does not account for:
- Absolute amount impact
- Currency purchasing power
- Campaign size sensitivity

**Tiered Tolerance Rules**

**Note**: These tolerance rules are for PLAN GENERATION ONLY—determining whether the auto-generated plan is acceptable. This is separate from the budget_fit scoring in section 5.6, which affects how each inventory is scored relative to budget. Budget_fit is a scoring component; tolerance rules are a plan validation constraint.

Tier 1: Small budgets (under $10,000 / equivalent)
- Tolerance: ±10%
- Rationale: Small campaigns have fewer inventory options, harder to hit exact amount
- User impact: $1,000 variance on $10,000 budget is acceptable

Tier 2: Medium budgets ($10,000 - $100,000)
- Tolerance: ±7%
- Rationale: More inventory options, tighter control expected
- User impact: $7,000 variance on $100,000 budget is reasonable

Tier 3: Large budgets ($100,000 - $500,000)
- Tolerance: ±5%
- Rationale: Large campaigns have many options, closer matching expected
- User impact: $25,000 variance on $500,000 budget needs justification

Tier 4: Enterprise budgets (over $500,000)
- Tolerance: ±3%
- Rationale: High-value campaigns require precision
- User impact: $30,000 variance on $1M budget is significant

**Currency Considerations**

Convert all budgets to a reference currency (USD) for tier calculation:
- $50,000 USD → Tier 2 (±7%)
- 50,000 MYR (~$11,000 USD) → Tier 2 (±7%)
- 50,000 INR (~$600 USD) → Tier 1 (±10%)

**Absolute Cap**

Regardless of percentage, apply absolute caps:
- Maximum over-budget: $10,000 (Tier 1-2), $25,000 (Tier 3), $50,000 (Tier 4)
- Never exceed 15% over budget regardless of tier

**Under-Budget Handling**

Under-budget is less concerning but should still trigger action:
- If under budget by more than tolerance, add inventories or increase schedule density
- If no suitable inventories remain, return leftover and explain
- Show message: "Budget allocation is 92%. Remaining 8% ($4,000) could not be allocated efficiently."

---

#### PART G: EDGE CASES AND SPECIAL SCENARIOS

**Scenario 1: No inventories available in selected geography**

Response:
- Return empty plan with status: no_matches
- Suggest nearby regions with available inventory
- Show distances: "No inventory in Kuala Terengganu. Nearest options: Kuantan (150km), Kota Bharu (200km)"

**Scenario 2: Budget too small for any inventory**

Response:
- If minimum inventory cost exceeds budget, show warning
- Suggest: "Budget of $500 is below minimum for available inventory. Consider increasing to $1,000."
- Do not create a plan with 0 inventories

**Scenario 3: Campaign duration conflicts with inventory minimums**

Example: 2-day campaign but all inventories require 7-day minimum

Response:
- Show warning: "Available inventory requires minimum 7-day booking. Extend campaign or search different areas."
- Option to auto-extend to minimum
- Option to search in different geography

**Scenario 4: Goal value is unrealistic for budget**

Example: User wants 100M impressions with $1,000 budget

Response:
- Calculate realistic expectation: "Budget supports approximately 500,000 impressions"
- Show gap: "This is 0.5% of your goal"
- Suggest budget increase: "To achieve 100M impressions, estimated budget needed: $200,000"
- Offer to proceed with adjusted expectations

**Scenario 5: Conflicting user selections**

Example: User wants reach goal but manually adds 10 office building inventories

Response:
- Allow the selection (user override is respected)
- Show warning: "Selected inventory mix is not optimal for reach goal. Office buildings (35% of selection) will show ads to the same audience repeatedly."
- Suggest alternatives but don't force

**Scenario 6: Brand category excluded by inventory**

Example: Alcohol brand, but mall inventory excludes alcohol advertising

Response:
- Automatically exclude non-compliant inventory
- Show in warnings: "3 inventories excluded due to brand category restrictions"
- Do not show excluded inventories in selection

---

#### PART H: DIVERSITY MECHANISMS

**H.1 Geographic Diversity Rules**

Geographic diversity ensures campaign reach extends across targeted regions rather than concentrating in a single metro area.

**City-Level Budget Caps and Minimums**

When user selects multiple cities OR a country-level geography:

Step 1: Calculate city coverage requirement
- If 3+ cities in geography, each major city must receive at least 10% of budget
- No single city can exceed 50% of budget (unless only city selected)

Step 2: Force geographic spread
- After selecting top inventories, check city distribution
- If KL is at 60% and Penang at 0%, force next selection from Penang regardless of score
- Continue until all cities meet 10% minimum

Step 3: Allow user override
- User can disable geographic spread if they specifically want concentration
- Show checkbox: "Concentrate budget in top-performing locations" (unchecked by default)

**Example: Geographic Diversity in Action**

Campaign: Malaysia, MYR 100,000, Impressions goal

Without geographic diversity (pure score):
| City | Budget | % |
|------|--------|---|
| Kuala Lumpur | MYR 75,000 | 75% |
| KLIA (Sepang) | MYR 20,000 | 20% |
| Johor Baru | MYR 5,000 | 5% |
| Penang | MYR 0 | 0% |
| **Total** | **MYR 100,000** | **100%** |

With geographic diversity applied:
| City | Budget | % | Adjustment |
|------|--------|---|------------|
| Kuala Lumpur | MYR 50,000 | 50% | Capped at 50% |
| KLIA (Sepang) | MYR 18,000 | 18% | — |
| Johor Baru | MYR 17,000 | 17% | Minimum 10% met |
| Penang | MYR 15,000 | 15% | Added to meet minimum |
| **Total** | **MYR 100,000** | **100%** |

Within each city, still use score-based selection to pick best inventories.

**City Tier System**

City tiers are determined by population, economic activity, and OOH market maturity. The system uses predefined tier assignments per country—**no AI (Gemini) is used** for tier classification.

**Malaysia:**
| Tier | Cities | Criteria |
|------|--------|----------|
| Tier 1 | Kuala Lumpur, KLIA/Sepang | Capital region, population 8M+, major economic hub |
| Tier 2 | Penang, Johor Bahru, Ipoh, Kuching, Kota Kinabalu | State capitals, population 500K-2M, regional hubs |
| Tier 3 | Melaka, Alor Setar, Kuantan, Seremban, others | Smaller cities, population <500K |

**Japan:**
| Tier | Cities | Criteria |
|------|--------|----------|
| Tier 1 | Tokyo, Osaka | Population 10M+, major economic hubs |
| Tier 2 | Nagoya, Yokohama, Fukuoka, Kyoto, Sapporo, Kobe | Population 1.5M-4M, regional capitals |
| Tier 3 | Other prefectural capitals and smaller cities | Population <1.5M |

**Budget Allocation Rules by Tier:**

Tier 1 (Major metros):
- Minimum: 15% each when multiple selected
- Maximum: 50% each

Tier 2 (Secondary cities):
- Minimum: 10% each when included
- Maximum: 40% each

Tier 3 (Regional):
- Minimum: 5% each when included
- No maximum

**City Budget Allocation Scenarios**

The following scenarios explain how city-level budget allocation works in different targeting situations.

---

**Scenario A: Country-Level Targeting (No Specific Cities)**

User selects: Malaysia (country), MYR 100,000 budget

System behavior:
1. Identify all cities with available inventory
2. Apply geographic diversity rules (50% max per city, 10% minimum for major cities)
3. Allocate budget proportionally based on inventory availability and score potential

Result:
| City | Allocation | Rationale |
|------|------------|-----------|
| Kuala Lumpur | 50% | Capped at maximum despite higher score potential |
| Penang | 15% | Minimum met for Tier 2 city |
| Johor Bahru | 15% | Minimum met for Tier 2 city |
| KLIA/Sepang | 10% | Based on inventory availability |
| Melaka | 5% | Minimum met for Tier 3 city |
| Others | 5% | Distributed to remaining cities |

---

**Scenario B: User Selects Specific Cities**

User selects: Kuala Lumpur + Penang only, MYR 100,000 budget

System behavior:
1. Only consider inventory in selected cities
2. Apply diversity rules ONLY to selected cities
3. With 2 cities, each can receive 40-60% (no strict 50% cap)

Result:
| City | Allocation | Rationale |
|------|------------|-----------|
| Kuala Lumpur | 60% | Higher score potential, no cap needed with 2 cities |
| Penang | 40% | Balance of budget |

---

**Scenario C: User Selects Single City**

User selects: Kuala Lumpur only, MYR 100,000 budget

System behavior:
1. No geographic diversity rules apply (single city)
2. All budget allocated to that city
3. Format diversity rules still apply within the city

Result:
| City | Allocation | Rationale |
|------|------------|-----------|
| Kuala Lumpur | 100% | Single city = no geographic diversity needed |

Within KL, format allocation (Digital 40%, Transit 25%, etc.) still applies.

---

**Scenario D: User Has Venue Type Preference**

User selects: Malaysia (country), MYR 100,000 budget, Venue preference: Transit

System behavior:
1. Apply geographic diversity first (city allocations)
2. Within each city, prioritize Transit inventory over other formats
3. Format allocation is OVERRIDDEN by user preference

Result:
| City | Allocation | Format Priority |
|------|------------|-----------------|
| Kuala Lumpur | 50% | Transit first (LRT, KL Sentral, bus shelters) |
| Penang | 15% | Transit first (Rapid Penang buses, ferry) |
| Johor Bahru | 15% | Transit first (JB Sentral, CIQ) |
| Others | 20% | Transit first where available |

Within each city budget:
- Transit: 70% (elevated from default 25%)
- Digital Screen: 15% (reduced from default 35%)
- Classic: 10% (reduced from default 25%)
- Others: 5%

---

**Scenario E: User Has Venue Type + City Preference**

User selects: Kuala Lumpur + Johor Bahru, MYR 100,000 budget, Venue preference: Airports

System behavior:
1. Only consider KL and JB inventory
2. Within each city, prioritize Airport/Network inventory
3. If a city has no airport inventory, use next-best venue type

Result:
| City | Airport Available? | Allocation Strategy |
|------|-------------------|---------------------|
| Kuala Lumpur | No (KLIA is in Sepang, not KL) | Allocate to Transit hubs instead |
| Johor Bahru | Yes (Senai Airport) | Prioritize JB Senai Airport inventory |

Since user explicitly selected cities that don't all have airport inventory:
- System shows warning: "KL does not have airport inventory. Nearest airport is KLIA (Sepang)."
- Offers to add Sepang/KLIA to selection
- If user declines, KL budget goes to Transit (closest alternative to airport traffic)

---

**Scenario F: Mixed Preferences with Carbon Goal**

User selects: Malaysia, MYR 100,000, Goal: Carbon Emission, Venue preference: None

System behavior:
1. Apply geographic diversity (city allocations)
2. Apply Carbon-focused format allocation (Classic 45%, Transit 30%, Digital 5%)
3. Within each format bucket AND each city, sort by CO2 ascending

Result:
| City | Budget | Format Focus |
|------|--------|--------------|
| Kuala Lumpur | 50% | Classic billboards + Transit |
| Penang | 15% | Classic billboards (heritage area has many) |
| Johor Bahru | 15% | Transit + Classic |
| Melaka | 10% | Classic (UNESCO heritage zone) |
| Others | 10% | Lowest-CO2 options available |

Digital screens (5% allocation) are still included but limited. Within that 5%, system selects lowest-CO2 digital screens (e.g., solar-powered, LED over plasma).

**H.2 Format Diversity Rules**

Format diversity ensures campaigns utilize multiple inventory types for broader audience coverage.

**Inventory Classification Budget Allocation**

MW Planner uses 7 finalized inventory types:
1. **Classic** - Traditional static billboards, posters
2. **Digital Screen** - LED/LCD digital billboards
3. **Transit** - Bus, train, metro advertising
4. **Retail** - Mall, supermarket, convenience store screens
5. **Network** - Connected screen networks (airports, office buildings)
6. **Radio** - Audio advertising (specialized, opt-in only)
7. **Experiential** - Pop-up, interactive, ambient (specialized, opt-in only)

**Note on Radio and Experiential**: These formats are specialized and not auto-selected by default. Radio requires audio creative (not visual), and Experiential requires custom activation planning. Both default to 0% allocation unless user explicitly includes them. When Radio or Experiential inventory exists but receives 0% allocation, their budget is NOT redistributed—these are opt-in only formats that the user must explicitly request.

**Default Format Allocation by Goal**

When creating a plan, allocate budget across format types BEFORE selecting individual inventories:

For **Impressions** goal:
| Format | Allocation |
|--------|------------|
| Digital Screen | 35% |
| Classic | 25% |
| Transit | 20% |
| Retail | 10% |
| Network | 7% |
| Experiential | 3% |

For **Reach** goal:
| Format | Allocation |
|--------|------------|
| Classic | 30% |
| Transit | 25% |
| Retail | 15% |
| Digital Screen | 15% |
| Network | 10% |
| Experiential | 5% |

For **Ad Plays** goal:
| Format | Allocation |
|--------|------------|
| Digital Screen | 40% |
| Network | 30% |
| Retail | 15% |
| Transit | 10% |
| Classic | 5% |
| Experiential | 0% |

For **SOV** goal:
| Format | Allocation |
|--------|------------|
| Digital Screen | 40% |
| Network | 25% |
| Transit | 20% |
| Retail | 10% |
| Classic | 5% |

For **Carbon Emission** goal (sustainability focus):
| Format | Allocation |
|--------|------------|
| Classic | 45% |
| Transit | 30% |
| Retail | 15% |
| Digital Screen | 5% |
| Network | 5% |
| Experiential | 0% |

*Note: Carbon Emission goal shifts more budget toward static formats. Within each format allocation (including Digital Screen at 5%), inventories are still ranked by CO2 data—selecting the greenest options within that format first.*

**No goal specified**: Use Impressions allocation as default.

**Example: Format Diversity in Action**

Campaign: KL only, MYR 100,000, Reach goal

Step 1: Allocate by format
| Format | Allocation | Budget |
|--------|------------|--------|
| Classic | 30% | MYR 30,000 |
| Transit | 25% | MYR 25,000 |
| Retail | 15% | MYR 15,000 |
| Digital Screen | 15% | MYR 15,000 |
| Network | 10% | MYR 10,000 |
| Experiential | 5% | MYR 5,000 |

Step 2: Within each format bucket, select by score
- Classic (MYR 30K): Pick top-scoring classic billboards
- Transit (MYR 25K): Pick top-scoring transit inventory
- Retail (MYR 15K): Pick top-scoring mall screens
- etc.

Step 3: If a format bucket cannot be filled (no inventory), redistribute
- If no Experiential inventory in KL, redistribute 5% to Transit (now 30%)

Result: Diverse format mix, not just premium digital screens.

**Format Minimum Enforcement**

If any format receives less than 5% of budget:
- Either force selection from that format
- Or redistribute entirely and warn: "No Classic billboards available in selected geography"

**H.3 Selection Variation (Enhanced)**

Section 5.12 introduces 10-15% variation across runs. This section extends that with specific mechanisms:

**Variation Pool System**

1. Calculate scores for all inventories
2. Group inventories into score bands:
   - Band A: 90-100 (Premium)
   - Band B: 80-89 (High)
   - Band C: 70-79 (Good)
   - Band D: 60-69 (Acceptable)
   - Below 60: Excluded

3. For each selection round:
   - 60% probability: Pick from highest unfilled band
   - 30% probability: Pick randomly from next band down
   - 10% probability: Pick randomly from any remaining band

4. Within selected band, use seeded random (from run_id) to pick specific inventory

**Example: Selection Variation**

Inventories by score:
- Band A (90-100): KLCC (92), KLIA (91)
- Band B (80-89): Pavilion (87), KL Sentral (85), Mid Valley (82)
- Band C (70-79): Bangsar LRT (78), TTDI Plaza (74), Subang Parade (71)

Run 1 (seed 12345):
- Selection 1: Band A → KLCC (highest in band)
- Selection 2: Band A → KLIA
- Selection 3: 30% roll hits Band B → KL Sentral (random from B)
- Selection 4: Band B → Pavilion

Final: KLCC, KLIA, KL Sentral, Pavilion

Run 2 (seed 67890):
- Selection 1: Band A → KLCC
- Selection 2: 30% roll hits Band B → Mid Valley
- Selection 3: Band A → KLIA
- Selection 4: 10% roll hits Band C → TTDI Plaza

Final: KLCC, Mid Valley, KLIA, TTDI Plaza

**Same campaign, different but valid selection each time.**

**Variation Constraints**
- Top-1 inventory (highest score) is ALWAYS included—stability for the "best" option
- Variation applies to positions 2-N only
- User can disable variation with "Use exact same selection" preference

**Scenario 7: Partial availability during campaign dates**

Example: Inventory is available for 6 of 10 campaign days

Response:
- Include with prorated cost and impressions
- Show in schedule: "Available Oct 1-6 only (6 of 10 days)"
- Apply availability score penalty (section 5.5)

---

#### PART H: WHAT USERS SEE (UI SUMMARY)

**Media Selection Page**

- Each inventory card shows score (0-100) from Recommendation Engine
- Score badge is clickable for breakdown
- Tooltip shows top 3 scoring factors
- "Auto Create Plan" button visible

**Auto Plan Dialog**

After clicking Auto Create:
- Processing indicator with "Analyzing X inventories..."
- Result summary: Y inventories selected, $Z allocated, N impressions expected
- Breakdown by inventory type (chart or list)
- Warnings/recommendations if any
- Buttons: "Accept Plan", "Modify", "Start Over", "Manual Mode"

**Optimization Tab (After Accepting)**

If plan was auto-created:
- Summary card at top showing auto-plan details
- Message: "This plan was auto-optimized based on your goal and budget"
- Metrics: inventories, schedules, impressions, reach
- Budget breakdown by type
- Warnings/recommendations
- Explanation: "Manual optimization buttons are available if you make changes"

**When Auto Optimize Button Is Useful**

The manual "Auto Optimize" button recalculates allocation and schedules. It is useful when:
- User manually adds/removes inventories after accepting auto plan
- User changes budget or dates
- User wants to try different allocation strategy

If user accepted auto plan and made no changes, pressing "Auto Optimize" will produce the same result (already optimized).

---

#### PART I: PROCESS SUMMARY

Complete flow in plain language:

1. User enters campaign details: dates, budget, country, geography, optionally goal (type and value), optionally brand and audience

2. Recommendation Engine (sections 1-10) scores all available inventories in the geography using 8 factors

3. If user clicks "Auto Create Plan":
   a. Retrieve scored inventory list
   b. Determine goal handling (which of 6 types, or none)
   c. Apply goal-based budget allocation percentages
   d. Apply venue-specific rules (office limits for reach, etc.)
   e. If goal value provided, calculate contribution fractions
   f. Select inventories by score, respecting goal contribution
   g. For each selected inventory, check selling terms (operating hours, minimums)
   h. Generate schedule respecting operating hours, picking lowest CPM hours
   i. Calculate schedule metrics (impressions, reach, cost)
   j. Validate total cost is within budget tolerance (tiered by amount)
   k. If over tolerance, remove lowest-scoring inventories and recalculate
   l. If under tolerance significantly, add inventories or increase density
   m. Generate warnings for any issues
   n. Present plan to user

4. User reviews and decides: Accept, Modify, Start Over, or Manual Mode

5. If accepted, plan is stored and shown in Optimization tab with summary

This documentation provides the complete Auto Plan Creator logic, covering all scenarios and decision-making processes.

---

#### PART J: CONCRETE WALKTHROUGH EXAMPLE

**Scenario**
- Brand: AirAsia (airline/travel category)
- Country: Malaysia
- Campaign dates: January 1-31, 2026 (31 days)
- Budget: MYR 100,000
- Goal: 500,000 impressions

---

**STEP 1: RETRIEVE AVAILABLE INVENTORIES**

System queries database for all inventories in Malaysia that are:
- Available during Jan 1-31, 2026 (at least partially)
- Not blocked by brand exclusions (no "no airlines" restrictions)
- Active status

Result: 47 inventories found in Kuala Lumpur, Penang, Johor Bahru, etc.

---

**STEP 2: SCORE EACH INVENTORY (Recommendation Engine, Sections 1-10)**

For each of the 47 inventories, calculate a score (0-100) using the 8 scoring factors defined in section 5.11:

**Example: Inventory A - KLCC Digital Billboard**
| Factor | Raw Value | Weight | Contribution |
|--------|-----------|--------|--------------|
| measure_fit | 45K imps/day × 31 = 1.4M total, goal 500K → 1.4M/500K = 280% of goal, capped at 100 | 20% | 20.0 |
| geo_fit | Inside KL geofence, prime location | 20% | 18.0 |
| availability | 100% available all 31 days | 10% | 10.0 |
| budget_fit | MYR 35,000 fits within MYR 100K budget (35%) | 20% | 18.0 |
| audience_fit | 82% overlap with AirAsia travelers | 10% | 8.2 |
| brand_fit | Travel brand + tourist district = good fit | 10% | 8.5 |
| quality_fit | Digital HD screen, premium format | 6% | 5.4 |
| time_fit | All dayparts available | 4% | 3.6 |
| **Total Score** | | | **91.7 → 92** |

**Example: Inventory B - Petaling Jaya Bus Shelter**
| Factor | Raw Value | Weight | Contribution |
|--------|-----------|--------|--------------|
| measure_fit | 8K imps/day × 31 = 248K total, goal 500K → 50% of goal | 20% | 10.0 |
| geo_fit | Secondary suburb location | 20% | 12.0 |
| availability | 100% available | 10% | 10.0 |
| budget_fit | MYR 4,500 very affordable (4.5% of budget) | 20% | 20.0 |
| audience_fit | 65% overlap (suburban commuters) | 10% | 6.5 |
| brand_fit | Travel brand + bus shelter = moderate fit | 10% | 5.0 |
| quality_fit | Static poster, basic format | 6% | 3.0 |
| time_fit | Peak commute hours only | 4% | 2.5 |
| **Total Score** | | | **69.0 → 69** |

**Example: Inventory C - KLIA Airport Digital Network**
| Factor | Raw Value | Weight | Contribution |
|--------|-----------|--------|--------------|
| measure_fit | 96K imps/day × 31 = 2.98M total, goal 500K → exceeds goal, capped at 100 | 20% | 20.0 |
| geo_fit | Airport location, perfect for airline | 20% | 20.0 |
| availability | 80% available (some days booked) | 10% | 8.0 |
| budget_fit | MYR 85,000 = 85% of budget (high but fits) | 20% | 14.0 |
| audience_fit | 95% overlap (airport travelers = perfect) | 10% | 9.5 |
| brand_fit | Airline brand + airport = perfect match | 10% | 10.0 |
| quality_fit | Digital network, premium screens | 6% | 5.7 |
| time_fit | 18 hours/day operation | 4% | 3.6 |
| **Total Score** | | | **90.8 → 91** |

All 47 inventories are scored. Results sorted by score:
1. KLCC Digital Billboard - Score 92
2. KLIA Airport Digital Network - Score 91
3. Pavilion Mall Digital Screen - Score 87
4. KL Sentral Transit Hub - Score 85
5. Bangsar LRT Station - Score 78
... (42 more inventories)
47. Rural highway billboard - Score 52

---

**STEP 3: DETERMINE GOAL HANDLING AND CITY ALLOCATION**

Goal type: Impressions
Goal value: 500,000 impressions

From Part B, impressions goal means:
- Selection priority: High-traffic digital screens > Transit > Roadside
- Target format allocation: Digital (40%), Transit (25%), Roadside (20%), Mall (15%)

**City Budget Allocation (from Part H.1)**

Since this is a Malaysia-wide campaign (country-level geography with no specific cities selected), geographic diversity rules apply:

Step 3a: Identify cities with available inventory
- Kuala Lumpur: 18 inventories
- Penang: 9 inventories
- Johor Bahru: 8 inventories
- KLIA/Sepang: 5 inventories
- Melaka: 4 inventories
- Kuantan: 3 inventories

Step 3b: Apply diversity constraints (Part H.1 rules)
- No city > 50% of budget
- Major cities (KL, Penang, JB) must each receive at least 10%
- Secondary cities minimum 5%

Step 3c: Calculate city budget allocation
| City | Tier | Min % | Max % | Allocated |
|------|------|-------|-------|-----------|
| Kuala Lumpur | 1 | 15% | 50% | MYR 50,000 (50%) |
| KLIA/Sepang | 2 | 10% | 40% | MYR 18,000 (18%) |
| Penang | 2 | 10% | 40% | MYR 15,000 (15%) |
| Johor Bahru | 2 | 10% | 40% | MYR 12,000 (12%) |
| Melaka | 3 | 5% | - | MYR 5,000 (5%) |
| **Total** | | | | **MYR 100,000** |

KL is capped at 50% despite having highest-scoring inventories. Penang, JB, and Melaka receive allocations to ensure geographic spread.

---

**STEP 4: CALCULATE IF GOAL IS ACHIEVABLE**

Estimate average CPM from available inventory:
- Average CPM in Malaysia market: ~MYR 8-15
- Using MYR 10 average: MYR 100,000 budget ÷ MYR 10 CPM × 1000 = 10,000,000 potential impressions

Goal of 500,000 impressions vs potential of 10,000,000:
- Goal is **easily achievable** (only 5% of potential)
- No warning needed
- System can be selective and choose only top-scoring inventories

---

**STEP 5: SELECT INVENTORIES BY SCORE**

Start with highest-scoring inventories and add until goal is met or budget is consumed.

**Note on Impressions**: Throughout this example, "impressions" means audience impressions (people who see the ad). Impressions can come from two sources:

1. **Measure-provided** (section 5.3): For high-traffic venues like airports and major transit hubs, Measure provides daily impression figures based on actual foot traffic sensors and audience measurement. This is the preferred source when available.

2. **Calculated**: For venues without Measure data, impressions = ad plays × average audience per play.

The "Est. Impressions" column below uses Measure-based totals for full campaign duration. When budget constraints force partial bookings, actual impressions are prorated accordingly.

**Selection process (by city budget):**

The system selects inventories within each city's budget allocation, using scores to rank options within each city.

**Kuala Lumpur (MYR 50,000 allocation)**

Top KL inventories by score:
| Inventory | Score | Cost | Impressions |
|-----------|-------|------|-------------|
| KLCC Digital Billboard | 92 | MYR 35,000 | 1,395,000 |
| Pavilion Mall Screen | 87 | MYR 22,000 | 620,000 |
| KL Sentral Transit Hub | 85 | MYR 12,000 | 310,000 |

Selection: KLCC (MYR 35,000) + partial Pavilion (15 days @ MYR 10,645) = MYR 45,645
- Remaining KL budget: MYR 4,355
- Add Bangsar LRT (28 days @ MYR 4,342) = MYR 4,342

KL subtotal: MYR 49,987 | Impressions: 1,789,000

**KLIA/Sepang (MYR 18,000 allocation)**

Top inventory: KLIA Airport Network (score 91, full cost MYR 85,000)
- MYR 18,000 ÷ (MYR 85,000 ÷ 31 days) = 6.6 days
- KLIA has 7-day minimum → Can only book 7 days at MYR 19,194

Adjustment: Slightly exceed city allocation (MYR 19,194 vs MYR 18,000)
- System allows +/-5% variance per city when inventory minimums require it

KLIA subtotal: MYR 19,194 | Impressions: 672,000 (7 days × 96,000/day)

**Penang (MYR 15,000 allocation)**

Top Penang inventories:
| Inventory | Score | Cost | Impressions |
|-----------|-------|------|-------------|
| Gurney Plaza Digital | 79 | MYR 12,000 | 280,000 |
| Penang Airport | 76 | MYR 18,000 | 350,000 |
| Komtar Tower | 72 | MYR 6,000 | 150,000 |

Selection: Gurney Plaza (MYR 12,000) + partial Komtar (15 days @ MYR 2,903) = MYR 14,903

Penang subtotal: MYR 14,903 | Impressions: 352,580

**Johor Bahru (MYR 12,000 allocation)**

Top JB inventories:
| Inventory | Score | Cost | Impressions |
|-----------|-------|------|-------------|
| JB Sentral Transit | 75 | MYR 8,000 | 210,000 |
| City Square Mall | 73 | MYR 7,500 | 180,000 |

Selection: JB Sentral (MYR 8,000) + partial City Square (16 days @ MYR 3,871) = MYR 11,871

JB subtotal: MYR 11,871 | Impressions: 303,097

**Melaka (MYR 5,000 allocation)**

Top Melaka inventory:
| Inventory | Score | Cost | Impressions |
|-----------|-------|------|-------------|
| Dataran Pahlawan Mall | 68 | MYR 5,500 | 120,000 |
| Jonker Street Billboard | 65 | MYR 3,200 | 85,000 |

Selection: Jonker Street (MYR 3,200) + partial Dataran (10 days @ MYR 1,774) = MYR 4,974

Melaka subtotal: MYR 4,974 | Impressions: 123,710

---

**COMBINED SELECTION SUMMARY**

| City | Inventories | Budget Used | Impressions |
|------|-------------|-------------|-------------|
| Kuala Lumpur | KLCC, Pavilion (partial), Bangsar LRT | MYR 49,987 | 1,789,000 |
| KLIA/Sepang | KLIA Airport Network (7 days) | MYR 19,194 | 672,000 |
| Penang | Gurney Plaza, Komtar (partial) | MYR 14,903 | 352,580 |
| Johor Bahru | JB Sentral, City Square (partial) | MYR 11,871 | 303,097 |
| Melaka | Jonker Street, Dataran (partial) | MYR 4,974 | 123,710 |
| **TOTAL** | **9 inventories** | **MYR 100,929** | **3,240,387** |

Budget variance: +0.9% (slightly over due to KLIA minimum)
Goal achievement: 3.24M impressions vs 500K target = 648%

**Final remaining: Plan complete within tolerance.**

---

**STEP 6: CREATE SCHEDULES FOR EACH INVENTORY**

For each selected inventory, create an optimized schedule using operating hours and CPM data.

**Example: KLCC Digital Billboard Schedule**

Operating hours: 6 AM - 12 AM (18 hours/day)
Loop length: 60 seconds, 10-second spots = 6 slots per loop
Loops per hour: 60
Spots purchased: 1 per loop
Average audience per ad play: ~41 people (varies by hour)

Hourly CPM data (sample):
| Hour | CPM (MYR) | Audience/Play | Traffic Level |
|------|-----------|---------------|---------------|
| 6-7 AM | 6.00 | 25 | Low |
| 7-8 AM | 9.50 | 52 | High (commute) |
| 8-9 AM | 10.00 | 55 | High |
| 9-12 PM | 8.00 | 40 | Medium |
| 12-2 PM | 11.00 | 60 | High (lunch) |
| 2-5 PM | 7.50 | 38 | Medium |
| 5-7 PM | 12.00 | 65 | Peak (commute) |
| 7-10 PM | 10.50 | 50 | High |
| 10-12 AM | 5.50 | 20 | Low |

**CPM-optimized hour selection** (lowest CPM first to maximize impressions per MYR):

Priority order: 10-12 AM, 6-7 AM, 2-5 PM, 9-12 PM, 7-8 AM, 8-9 AM, 7-10 PM, 12-2 PM, 5-7 PM

**When CPM optimization applies**: CPM-based hour selection matters when budget is the constraint (you can't afford all hours). If budget exceeds capacity cost, you purchase all available hours and CPM ranking becomes irrelevant.

Budget allocated to KLCC: MYR 35,000 for 31 days

**Schedule calculation:**

**Understanding CPM**: CPM (Cost Per Mille) is the cost per 1,000 impressions. Impressions = audience views, not ad plays.
- If CPM = MYR 8.50, then 1,000 impressions cost MYR 8.50
- Impressions that MYR 35,000 can buy = (35,000 ÷ 8.50) × 1,000 = 4,117,647 impressions
- But KLCC has capacity limits based on physical ad plays and audience:
  - Max ad plays: 60 plays/hour × 18 hours × 31 days = 33,480 plays
  - Each ad play reaches average 41.7 people (varies by hour)
  - Max impressions: 33,480 × 41.7 = 1,395,000 impressions
- Budget of MYR 35,000 can buy 4.1M impressions, but KLCC can only deliver 1.4M impressions at max capacity
- Therefore, KLCC is **capacity-limited**, not budget-limited. CPM ranking is bypassed; all operating hours are selected.

Schedule created:
- Hours selected: 6 AM - 12 AM (all operating hours)
- Days: Jan 1-31, 2026 (all 31 days)
- Ad plays per hour: 60 (1 spot per loop × 60 loops)
- Daily ad plays: 1,080
- Monthly ad plays: 33,480
- Impressions: 1,395,000 (ad plays × average audience per play)

**KLIA Airport Network Schedule** (7 days):

**Note on KLIA Impressions**: KLIA's 96K impressions/day is a Measure-provided value (from section 5.3), not derived from ad plays × audience/play. Measure data incorporates actual foot traffic sensors and audience measurement systems at the airport.

- Hours selected: 6 AM - 12 AM (all operating hours during booked days)
- Days: Jan 1-7, 2026 (7 days - minimum booking)
- Ad plays per hour: 60
- Daily ad plays: 1,080
- Period ad plays: 7,560
- Impressions: 7 days × 96,000/day = 672,000 (Measure-based)

**Other City Schedules** (summarized):
- Pavilion Mall: 15 days, all operating hours
- Bangsar LRT: 28 days, all operating hours
- Gurney Plaza (Penang): 31 days, all operating hours
- Komtar Tower (Penang): 15 days, all operating hours
- JB Sentral: 31 days, all operating hours
- City Square (JB): 16 days, all operating hours
- Jonker Street (Melaka): 31 days, all operating hours
- Dataran Pahlawan (Melaka): 10 days, all operating hours

---

**STEP 7: VALIDATE AGAINST BUDGET TOLERANCE**

Total plan cost: MYR 100,929
Budget: MYR 100,000
Variance: +0.9% (over budget by MYR 929)

From Part F, budget tier:
- MYR 100,000 ≈ USD 22,000 → Tier 2 (±7% tolerance)

0.9% over budget is well within 7% tolerance. **PASS**

---

**STEP 8: GENERATE PLAN SUMMARY**

**Auto Plan Summary for AirAsia Malaysia Campaign**

| Metric | Value |
|--------|-------|
| Campaign Period | Jan 1-31, 2026 (31 days) |
| Budget | MYR 100,000 |
| Allocated | MYR 100,929 (+0.9%) |
| Goal | 500,000 impressions |
| Projected | 3,240,387 impressions (648% of goal) |
| Inventories Selected | 9 |
| Cities Covered | 5 (KL, Sepang, Penang, JB, Melaka) |

**Selected Inventories by City:**

| City | Inventory | Type | Days | Cost | Impressions | Score |
|------|-----------|------|------|------|-------------|-------|
| KL | KLCC Digital Billboard | Digital | 31 | MYR 35,000 | 1,395,000 | 92 |
| KL | Pavilion Mall Screen | Digital | 15 | MYR 10,645 | 300,000 | 87 |
| KL | Bangsar LRT Station | Transit | 28 | MYR 4,342 | 94,000 | 78 |
| Sepang | KLIA Airport Network | Network | 7 | MYR 19,194 | 672,000 | 91 |
| Penang | Gurney Plaza Digital | Digital | 31 | MYR 12,000 | 280,000 | 79 |
| Penang | Komtar Tower | Digital | 15 | MYR 2,903 | 72,580 | 72 |
| JB | JB Sentral Transit | Transit | 31 | MYR 8,000 | 210,000 | 75 |
| JB | City Square Mall | Retail | 16 | MYR 3,871 | 93,097 | 73 |
| Melaka | Jonker Street Billboard | Classic | 31 | MYR 3,200 | 85,000 | 65 |
| Melaka | Dataran Pahlawan Mall | Retail | 10 | MYR 1,774 | 38,710 | 68 |

**Budget by City:**
| City | Allocation | % |
|------|------------|---|
| Kuala Lumpur | MYR 49,987 | 50% |
| KLIA/Sepang | MYR 19,194 | 19% |
| Penang | MYR 14,903 | 15% |
| Johor Bahru | MYR 11,871 | 12% |
| Melaka | MYR 4,974 | 5% |

**Geographic Diversity Applied:**
- KL capped at 50% (would be 75%+ without diversity rules)
- Penang, JB, and Melaka each receive minimum allocations
- Campaign reaches 5 major cities across Malaysia

**Recommendations:**
- Plan significantly exceeds impression goal (3.2M vs 500K target)
- Geographic spread ensures nationwide brand visibility
- Consider reducing budget if 500K impressions is a firm cap

**Warnings:**
- KLIA booked for 7 days only (city budget + minimum booking constraint)
- Several inventories booked for partial campaign duration

---

**STEP 9: PRESENT TO USER**

User sees:
1. Processing message: "Analyzing 47 inventories in Malaysia..."
2. Result summary card with key metrics
3. List of 3 selected inventories with scores visible
4. Budget breakdown chart
5. Recommendations and warnings
6. Action buttons: "Accept Plan" | "Modify" | "Start Over" | "Manual Mode"

If user clicks "Accept Plan":
- Inventories added to campaign with schedules
- Optimization tab shows summary with "Auto-optimized" badge
- User can still make manual adjustments

---

**KEY TAKEAWAYS FROM THIS EXAMPLE**

1. **Scoring comes first** - All 47 inventories scored using 8 factors (section 5.11) before any selection
2. **Goal checked for feasibility** - 500K goal easily achievable with 10M potential capacity
3. **Score drives selection** - Highest-scoring inventories selected first (KLCC 92, KLIA 91)
4. **Selling terms enforced** - KLIA 7-day minimum respected (23 days > 7 days = valid)
5. **Budget constraint shapes plan** - KLIA partial booking (23 days) because full 31 days exceeds budget
6. **Impressions calculated consistently** - Ad plays × audience per play = impressions
7. **Tolerance validated** - 0.08% under budget is within Tier 2's ±7% tolerance


---

## 13. Auto Plan Creator Workflow

This section provides a complete view of the Auto Plan Creator workflow—from user input to final plan acceptance.

### 13.1 User Journey Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         AUTO PLAN CREATOR WORKFLOW                          │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   STEP 1    │───▶│   STEP 2    │───▶│   STEP 3    │───▶│   STEP 4    │
│ User Inputs │    │  Inventory  │    │  Selection  │    │  Schedule   │
│             │    │   Scoring   │    │   Logic     │    │  Creation   │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
      │                  │                  │                  │
      ▼                  ▼                  ▼                  ▼
 ┌─────────┐        ┌─────────┐        ┌─────────┐        ┌─────────┐
 │ Dates   │        │ 8-factor│        │ Goal    │        │ CPM-    │
 │ Budget  │        │ scoring │        │ handling│        │ based   │
 │ Country │        │ (Sec 5) │        │ (12.2)  │        │ hours   │
 │ Goal    │        │         │        │         │        │         │
 │ Brand   │        │ Score   │        │ Budget  │        │ Selling │
 │ Audience│        │ 0-100   │        │ alloc.  │        │ terms   │
 └─────────┘        └─────────┘        └─────────┘        └─────────┘
                                              │                  │
                                              ▼                  ▼
                                        ┌─────────────┐    ┌─────────────┐
                                        │   STEP 5    │    │   STEP 6    │
                                        │  Diversity  │───▶│  Validation │
                                        │   Rules     │    │             │
                                        └─────────────┘    └─────────────┘
                                              │                  │
                                              ▼                  ▼
                                        ┌─────────┐        ┌─────────┐
                                        │ City    │        │ Budget  │
                                        │ caps/   │        │ toler-  │
                                        │ minimums│        │ ance    │
                                        │         │        │         │
                                        │ Format  │        │ Goal    │
                                        │ spread  │        │ achiev- │
                                        └─────────┘        │ ability │
                                                           └─────────┘
                                                                 │
                                              ┌──────────────────┘
                                              ▼
                                        ┌─────────────┐
                                        │   STEP 7    │
                                        │   Present   │
                                        │   to User   │
                                        └─────────────┘
                                              │
                          ┌───────────────────┼───────────────────┐
                          ▼                   ▼                   ▼
                    ┌──────────┐        ┌──────────┐        ┌──────────┐
                    │  Accept  │        │  Modify  │        │  Manual  │
                    │   Plan   │        │   Plan   │        │   Mode   │
                    └──────────┘        └──────────┘        └──────────┘
                          │                   │                   │
                          ▼                   ▼                   ▼
                    ┌──────────┐        ┌──────────┐        ┌──────────┐
                    │ Campaign │        │ Recalc   │        │ Browse   │
                    │ created  │        │ & save   │        │ manually │
                    └──────────┘        └──────────┘        └──────────┘
```

### 13.2 Step-by-Step Workflow Description

**Step 1: User Provides Campaign Inputs**

User enters the following in the campaign creation wizard:
- Campaign dates (mandatory): Start and end date
- Country (mandatory): Campaign geography
- Budget (mandatory for auto-plan): Total campaign budget
- Goal type (optional but recommended): Impressions, Reach, SOV, Ad Plays, or Carbon Emission
- Goal value (optional): Specific target (e.g., 1 million impressions)
- Brand (optional): For category-based matching
- Audience (optional): Target demographic segments

**Step 2: Recommendation Engine Scores All Inventories**

The Recommendation Engine (sections 1-10) retrieves all available inventories in the selected geography and scores each using 8 factors:
- measure_fit, geo_fit, availability, budget_fit, audience_fit, brand_fit, quality_fit, time_fit

Each inventory receives a final score from 0-100.

**Step 3: Auto Plan Creator Applies Selection Logic**

Based on the selected goal type (section 12.2):
- Determines which venue types to prioritize/limit
- Calculates budget allocation by Classification → Type (section 12.3)
- Applies goal-specific rules (e.g., day limits for reach, slot duration preference for ad plays)

**Step 4: Schedule Creation**

For each selected inventory (section 12.4):
- Retrieves operating hours and selling terms
- Calculates hourly CPM data
- Selects hours starting from lowest CPM
- Validates against minimum booking requirements
- Creates optimized schedule

**Step 5: Diversity Rules Applied**

Geographic and format diversity (Part H):
- City budget caps (50% max per city)
- City minimums (10-15% for major cities)
- Format spread across Classification/Type buckets

**Step 6: Validation**

Budget tolerance check (Part F):
- Tiered tolerance based on budget size
- If over tolerance: Remove lowest-scoring inventories
- If under tolerance: Add inventories or increase density

Goal achievability check:
- Compare forecasted metrics to goal value
- Generate warnings if goal unlikely to be met

**Step 7: Present Plan to User**

User sees:
- Summary: Inventories selected, budget allocated, estimated metrics
- List of selected inventories with scores
- Budget breakdown by city and type
- Schedule summary
- Warnings and recommendations

**User Decision Options:**
- Accept Plan: Proceed with auto-generated plan
- Modify Plan: Edit specific inventories or schedules
- Start Over: Reset with different parameters
- Manual Mode: Abandon auto-plan, select manually

---

## 14. Data Dependencies, Fallback Logic, and Error Handling

This section provides comprehensive documentation of what data the Auto Plan Creator requires, what happens when data is missing, and when the system cannot proceed.

### 14.1 Required Data Sources

The Auto Plan Creator depends on data from multiple systems. Understanding these dependencies is critical for troubleshooting and integration.

#### From User Input (Campaign Creation)

| Data | Required? | Fallback if Missing |
|------|-----------|---------------------|
| Campaign Dates | Yes | Cannot proceed; show error |
| Country | Yes | Cannot proceed; show error |
| Budget | Yes (for auto-plan) | Cannot run auto-plan; show "Enter budget to use Auto-Create" |
| Goal Type | No | Default to Impressions optimization |
| Goal Value | No | Optimize for budget efficiency (no target) |
| Brand | No | Skip brand_fit scoring; neutral weight |
| Audience Segments | No | Skip audience_fit scoring; neutral weight |
| Geography (cities/polygon) | No | Use country-level; apply geographic diversity |

#### From Inventory System

| Data | Required? | Fallback if Missing |
|------|-----------|---------------------|
| Inventory ID | Yes | Cannot include inventory |
| Coordinates (lat/lng) | Yes | Cannot calculate geo_fit; exclude |
| Price Model | Yes | Cannot calculate budget_fit; exclude |
| Price Value | Yes | Cannot calculate cost; exclude |
| Availability Calendar | No | Assume 100% available; warning shown |
| Venue Type (IAB) | No | AI inference (section 3.4); if fails, neutral treatment |
| Classification/Type | Yes | Cannot allocate to bucket; use "Other" bucket |
| Operating Hours | No | Assume 24/7; may create invalid schedules |
| Loop Duration | No | Assume 60 seconds; may miscalculate ad plays |
| Slot Duration | No | Assume 10 seconds; may miscalculate ad plays |
| Minimum Days | No | Assume 1 day minimum; may create too-short bookings |
| Minimum Hours | No | Assume no minimum; may create invalid bookings |

#### From Measure System

| Data | Required? | Fallback if Missing |
|------|-----------|---------------------|
| Daily Impressions | No* | Use first-party data if available |
| Hourly Traffic Curve | No | Assume flat distribution |
| Reach Data | No* | Cannot calculate reach goal; fallback to impressions |
| Audience Profile | No | Skip audience_fit; neutral weight |
| Reach Curve | No | Cannot detect saturation; no day limits |

*If both Measure AND first-party data are missing for an inventory, that inventory is excluded (see section 3.3).

#### From AI System (Gemini)

| Data | Required? | Fallback if Missing |
|------|-----------|---------------------|
| Brand Category Mapping | No | Skip brand_fit; neutral weight |
| Venue Type Inference | No | Neutral venue treatment; no day limits |
| POI Recommendations | No | Use only user-provided POIs |

---

### 14.2 Fallback Logic Hierarchy

When primary data is unavailable, the system follows this fallback chain:

#### Impressions Data Fallback

```
Primary: Measure impressions for (inventory, date_range)
    ↓ If missing
Fallback 1: First-party data from media owner
    ↓ If missing
Fallback 2: Calculate from ad plays × estimated audience per play
    ↓ If missing
EXCLUDE: Inventory removed from recommendation set
```

#### Venue Type Fallback

```
Primary: venue_type field in inventory record
    ↓ If missing
Fallback 1: AI inference from inventory name + coordinates + nearby POI
    ↓ If confidence < 75%
Fallback 2: Keyword-based classification from inventory name
    ↓ If no match
NEUTRAL: Inventory included but venue-specific rules not applied
```

#### Price Fallback

```
Primary: Rate card price for campaign dates
    ↓ If missing
Fallback 1: Agency-specific negotiated rate
    ↓ If missing
Fallback 2: Historical average for similar inventory
    ↓ If missing
EXCLUDE: Cannot calculate budget_fit; inventory removed
```

#### Availability Fallback

```
Primary: APO calendar data
    ↓ If missing
Fallback 1: Media owner provided availability
    ↓ If missing
WARNING: Assume 100% available; flag as "Availability unconfirmed"
```

---

### 14.3 Error Cases and User Messages

The following scenarios prevent the Auto Plan Creator from generating a plan:

#### Critical Errors (Cannot Proceed)

| Error Condition | User Message | Resolution |
|-----------------|--------------|------------|
| No campaign dates | "Please select campaign dates" | User must enter dates |
| No country selected | "Please select a country" | User must select country |
| No budget entered | "Enter a budget to use Auto-Create" | User enters budget or uses manual mode |
| Zero inventories available | "No inventory available in [Geography] for [Dates]" | Suggest expanding dates or geography |
| All inventories excluded | "No inventory with sufficient data. Try expanding search." | Check Measure/first-party data availability |
| Budget below minimum | "Budget of $X is below minimum inventory cost ($Y)" | Suggest increasing budget |

#### Warnings (Can Proceed with Limitations)

| Warning Condition | User Message | System Behavior |
|-------------------|--------------|-----------------|
| No Measure data for some inventories | "X inventories using estimated data" | Include with owner data; flag |
| Venue type unknown | "Venue rules not applied to X inventories" | Include; no day limits |
| Partial availability | "[Inventory] available X/Y days" | Include; prorate cost/impressions |
| Goal may not be met | "Forecast: X impressions vs Y target" | Show gap; suggest budget increase |
| Minimum booking extended | "[Inventory] minimum 7 days; extended from 3" | Show adjusted cost |
| City allocation adjusted | "No inventory in [City]; reallocated" | Redistribute proportionally |
| Type allocation adjusted | "No [Type] inventory; reallocated" | Redistribute proportionally |

---

### 14.4 Selling Terms Validation

The Auto Plan Creator validates schedules against inventory selling terms before finalizing:

| Selling Term | Validation | If Violated |
|--------------|------------|-------------|
| Operating Hours | Selected hours must be within operating range | Remove out-of-range hours; recalculate |
| Operating Days | Selected days must be operating days | Skip non-operating days |
| Minimum Days | Campaign must meet minimum day requirement | Extend to minimum OR exclude inventory |
| Minimum Hours | Daily booking must meet minimum hour requirement | Extend to minimum OR exclude inventory |
| Minimum Spend | Daily cost must meet minimum spend | Extend schedule OR exclude inventory |
| Spots Per Loop | Cannot exceed available spots | Cap at maximum |
| Lead Time | Campaign must start after lead time | Exclude if start date too soon |

**Conflict Resolution Priority:**

1. First attempt: Extend booking to meet minimums
2. If extension exceeds budget allocation: Exclude inventory
3. If extension works: Show adjusted cost to user

---

### 14.5 Complete Example: Data Flow and Decision Points

**Scenario**: AirAsia Malaysia campaign, MYR 100,000, Reach goal, January 1-31, 2026

**Step-by-step data requirements and decisions:**

```
INPUT PHASE
├── User provides: dates ✓, country ✓, budget ✓, goal=Reach ✓, brand="AirAsia"
├── Brand lookup → category="Travel/Airlines" (AI confidence 0.98)
└── Proceed to inventory retrieval

INVENTORY RETRIEVAL
├── Query: Malaysia + Jan 1-31 availability
├── Result: 47 inventories found
└── Each inventory checked for required data:
    ├── KLCC Digital: All data present ✓
    ├── KLIA Airport: All data present ✓
    ├── Random Billboard #23: Missing price → EXCLUDED
    ├── Office Lobby #7: Missing Measure → Check first-party → Found ✓
    └── Street Poster #45: Missing Measure + first-party → EXCLUDED

SCORING PHASE
├── 45 inventories with valid data proceed to scoring
├── Each scored 0-100 using 8 factors
├── Brand_fit boosted for airport venues (travel brand affinity)
└── Ranked list produced

SELECTION PHASE (Reach Goal)
├── Budget allocation: Digital 40%, Classic 55%, Audio 5%
├── Venue rules applied:
│   ├── Office screens: Max 4-5 days
│   ├── Roadside: Full campaign
│   └── Transit: Full campaign
├── Geographic diversity:
│   ├── KL: 50% cap applied
│   ├── Penang: 15% minimum met
│   ├── JB: 12% allocated
│   └── Melaka: 5% allocated
└── 9 inventories selected

SCHEDULE CREATION
├── Each inventory:
│   ├── Get operating hours
│   ├── Check minimum requirements
│   ├── Select hours by lowest CPM
│   └── Validate against selling terms
├── KLIA: 7-day minimum enforced (campaign wanted 31, budget allows 7)
└── All schedules valid

VALIDATION
├── Total cost: MYR 100,929 (+0.9%)
├── Budget tier 2: ±7% tolerance
├── 0.9% within tolerance ✓
├── Forecasted reach: 2.1M unique people
└── Plan ready for presentation

PRESENTATION
├── Summary: 9 inventories, 5 cities, MYR 100,929
├── Warnings: "KLIA limited to 7 days (minimum booking)"
├── Recommendations: "Geographic diversity applied"
└── Actions: Accept | Modify | Start Over | Manual
```

---

### 14.6 API Error Codes

For API consumers, the Auto Plan Creator returns structured error responses:

| Error Code | Meaning | Resolution |
|------------|---------|------------|
| `NO_DATES` | Campaign dates not provided | Provide start_date and end_date |
| `NO_COUNTRY` | Country not selected | Provide country_code |
| `NO_BUDGET` | Budget not provided for auto-plan | Provide budget or use manual mode |
| `NO_INVENTORY` | No inventory in geography | Expand geography or dates |
| `INSUFFICIENT_DATA` | All inventories lack Measure/first-party data | Check data availability |
| `BUDGET_TOO_LOW` | Budget below minimum inventory cost | Increase budget |
| `GOAL_UNREACHABLE` | Goal cannot be achieved with available inventory | Reduce goal or increase budget |
| `INTERNAL_ERROR` | System error during processing | Retry; contact support if persists |

