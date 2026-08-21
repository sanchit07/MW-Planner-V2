# Sales Performance Summary Widget — Component Specification

Version: 1.0
Date: February 2026
Purpose: Complete specification for AI-assisted reconstruction of this widget in any application.

---

## 1. Widget Identity

| Attribute | Value |
|-----------|-------|
| Widget ID | `sales-performance-summary` |
| Display Title | Sales Performance Summary |
| Subtitle / Description | Team output, regional performance, and revenue pipeline strength |
| Default Size | `large` (full-width, 12 columns in a 12-column grid, 4 rows tall) |
| Default Position | Column 0, Row 0 (second widget slot in dashboard) |
| Is Default Widget | Yes (cannot be removed by user) |
| Visibility | Shown for `internal` and `media_owner` company types |
| Parent Container | Widget Framework (`WidgetDashboard`) — renders inside a Card with CardHeader (title) and CardContent (this component) |

---

## 2. Component Interface (Props)

```
Props:
  dateRange: string (optional, default "30days")
    Accepted values: "30days", "60days", "90days", "custom"
    Controls the time period for all data in the widget.

  startDate: Date (optional)
    Only used when dateRange = "custom". The beginning of the custom period.

  endDate: Date (optional)
    Only used when dateRange = "custom". The end of the custom period.
```

These props are passed down from the parent dashboard page, which manages a shared date range selector affecting all widgets.

---

## 3. Internal State Variables

| State Variable | Type | Default | Purpose |
|----------------|------|---------|---------|
| `activeTab` | string | `"overview"` | Controls which of the 4 tabs is active |
| `clientView` | string | `"agency"` | Toggles the Clients tab between "By Agency" and "By Advertiser" views |
| `selectedCountry` | string | `"all"` | Filters data by country (used in currency display) |
| `regionalView` | string | `"city"` | Toggles the Regional tab between "By City" and "By Country" views |
| `campaignType` | string | `"all"` | Filters data by campaign execution type |

---

## 4. Data Fetching

### 4.1 API Endpoint

```
GET /api/dashboard/sales-summary
```

### 4.2 Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `tenantCompanyId` | number | No | Filters data to a specific company (multi-tenant support) |
| `dateRange` | string | No | Time period filter: "30days", "60days", "90days", "custom" |

### 4.3 Authentication

Requires authenticated session. Returns 401 if not authenticated. Returns 403 if the user does not have access to the requested tenant company.

### 4.4 Cache Key Structure

```
["/api/dashboard/sales-summary", selectedCompanyId, dateRange]
```

When `selectedCompanyId` changes (user switches company context), all cached data is automatically invalidated and refetched.

### 4.5 API Response Data Shape

```
{
  totalProposalValue: number        // Total revenue across all deals (e.g., 2850000)
  averageDealSize: number           // Mean deal value (e.g., 45000)
  proposalsClosedThisMonth: number  // Count of closed deals (e.g., 18)
  conversionRate: number            // Percentage 0-100 (e.g., 72)
  monthOverMonthChange: number      // MoM percentage change (e.g., 15.2)
  averageTimeToClose: number        // Days to close (e.g., 12)

  regionalPerformance: [            // Country-level aggregation
    {
      region: string                // Country name (e.g., "Malaysia")
      deals: number                 // Deal count for this region
      value: number                 // Total revenue for this region
      conversion: number            // Conversion rate percentage
      color: string                 // Hex color for chart/table display (e.g., "#3b82f6")
    }
  ]

  cityPerformance: [                // City-level granularity
    {
      region: string                // Parent country name
      city: string                  // City name (e.g., "Kuala Lumpur")
      inventories: number           // Count of inventory units in this city
      utilization: number           // Utilization percentage 0-100
      conversion: number            // Conversion rate percentage
      deals: number                 // Deal count
      revenue: number               // Total revenue
    }
  ]

  teamMembers: [                    // Individual sales rep performance
    {
      name: string                  // Full name
      region: string                // Assigned region
      deals: number                 // Deal count
      value: number                 // Total revenue
      conversion: number            // Conversion rate percentage
      rank: number                  // Leaderboard position (1 = top)
    }
  ]

  agencyRevenue: [                  // Revenue grouped by agency
    {
      agency: string                // Agency name (e.g., "Publicis Malaysia")
      advertiser: string | null     // "Multiple" or null for direct clients
      revenue: number               // Total agency revenue
      deals: number                 // Deal count
      percentage: number            // Share of total revenue
      adPlays: number               // Total ad play count
      sov: number                   // Share of Voice percentage
      impressions: number           // Total impressions
      campaigns: [                  // Top campaigns under this agency
        { name: string, revenue: number }
      ]
    }
  ]

  advertiserRevenue: [              // Revenue grouped by advertiser
    {
      advertiser: string            // Advertiser/brand name
      agency: string                // Associated agency name (or "Direct")
      revenue: number
      deals: number
      percentage: number
      adPlays: number
      sov: number
      impressions: number
      campaigns: [
        { name: string, revenue: number }
      ]
    }
  ]
}
```

---

## 5. Layout Structure

The widget renders inside a Card component with two main zones:

### 5.1 Header Zone

The header sits at the top of the card and contains two elements side-by-side:

**Left side:**
- Icon: Target icon (blue, 20x20px)
- Title: "Sales Performance Summary" (CardTitle styling)
- Subtitle: "Team output, regional performance, and revenue pipeline strength" (CardDescription styling, muted gray)

**Right side:**
- Label: "Campaign Type:" (small, medium-weight, gray text)
- Dropdown selector with these options:
  - "All Campaigns" (default, value: "all")
  - "Traditional" (value: "traditional")
  - "All Programmatic" (value: "programmatic")
  - "Programmatic Guaranteed" (value: "programmatic-guaranteed")
  - "Preferred Deal" (value: "preferred-deal")
  - "Private Marketplace" (value: "private-marketplace")
  - "Evergreen PMP" (value: "evergreen-pmp")
  - "Open Auction" (value: "open-auction")
- Dropdown width: 224px (w-56)

### 5.2 Content Zone

Minimum height: 500px. Contains a tab interface with 4 tabs.

---

## 6. Tab 1: Overview

This is the default active tab. It contains two sections stacked vertically.

### 6.1 KPI Cards Row

A horizontal row of 4 metric cards arranged in a 2x2 grid on mobile, 4x1 on desktop.

| Card | Background | Icon | Icon Color | Value Color | Label | Data Field |
|------|-----------|------|------------|-------------|-------|------------|
| Card 1 | `bg-blue-50` | DollarSign | blue-500 | blue-600 | "Total Deal Value" | `totalProposalValue` formatted as currency |
| Card 2 | `bg-green-50` | Target | green-500 | green-600 | "Average Deal Size" | `averageDealSize` formatted as currency |
| Card 3 | `bg-purple-50` | FileText | purple-500 | purple-600 | "Deals Closed" | `proposalsClosedThisMonth` as integer |
| Card 4 | `bg-orange-50` | Percent | orange-500 | orange-600 | "Conversion Rate" | `conversionRate` with "%" suffix |

**Card internal layout:**
```
┌─────────────────────────────────┐
│ ┌───────────────────┐  ┌─────┐ │
│ │ Label (sm, gray)  │  │Icon │ │
│ │ Value (2xl, bold)  │  │32px │ │
│ └───────────────────┘  └─────┘ │
└─────────────────────────────────┘
```
- Padding: 16px
- Border radius: 8px (rounded-lg)
- Label: text-sm, text-gray-600
- Value: text-2xl, font-bold, colored per card
- Icon: 32x32px, positioned to the right

### 6.2 Revenue Trend Chart

**Chart Title:** Dynamic, derived from date range configuration:
- For 30-day range: "Weekly Revenue Trend"
- For 60-day range: "Weekly Revenue Trend"
- For 90-day range: "Weekly Revenue Trend"
- For custom range ≤14 days: "Daily Revenue Trend"
- For custom range 15–90 days: "Weekly Revenue Trend"
- For custom range >90 days: "Monthly Revenue Trend"

**Chart Type:** Vertical bar chart (Recharts BarChart)
**Chart Height:** 180px
**Chart Width:** 100% of container (ResponsiveContainer)

**Chart Configuration:**
- X-axis: Date range labels (e.g., "Jan 15 - Jan 21")
- Y-axis: Currency values, formatted with country-specific currency code
- Bars: Fill color `#3b82f6` (blue-500), top corners rounded (radius [4,4,0,0])
- Tooltip: Custom tooltip showing formatted currency value on white background with border and shadow

**Data Generation Logic (for date-range-based periods):**
```
If dateRange = "30days": Generate 4 weekly buckets, counting backwards from today
If dateRange = "60days": Generate 8 weekly buckets
If dateRange = "90days": Generate 12 weekly buckets
If dateRange = "custom":
  If span ≤ 14 days: Generate daily data points
  If span 15–90 days: Generate weekly data points
  If span > 90 days: Generate monthly data points

Each bucket label = "MMM dd - MMM dd" (e.g., "Jan 15 - Jan 21")
Each bucket value = randomized revenue figure (for demo/mock data)
```

---

## 7. Tab 2: Regional

This tab shows geographic performance data with a toggle between city-level and country-level views.

### 7.1 Controls Row

**Left side:**
- Icon: MapPin (20x20px)
- Title: "Regional Performance" (font-semibold)
- Dropdown: Toggle between "By City" and "By Country" (width 128px)

**Right side:**
- Summary card with light blue background (bg-blue-50, px-6, py-3, rounded-lg):
  - Label: "Total Cities" or "Total Countries" (dynamic based on toggle)
  - Value: Count of items (bold, text-xl, blue-600)
  - Icon: Building2 (24x24px, blue-500)

### 7.2 Data Table (City View — default)

Table inside a bordered, rounded container with subtle shadow.

**Header row:** Gradient background from blue-50 to indigo-50.

| Column | Header Text | Content |
|--------|------------|---------|
| 1 | City | Blue dot indicator (8x8px circle) + city name (font-medium) |
| 2 | Country | Badge with outline variant showing region name (text-xs) |
| 3 | Inventories | Formatted number with locale separators (font-medium, gray-700) |
| 4 | Utilization | Mini progress bar (48px wide, 8px tall, green fill) + percentage text |
| 5 | Conversion | Percentage value, color-coded: green-600 (≥80%), yellow-600 (70-79%), red-600 (<70%) |
| 6 | Deals | FileText icon (16px, gray-400) + deal count (font-medium) |
| 7 | Revenue | Currency-formatted value using country-specific currency (font-semibold, gray-900) |

**Row hover:** bg-gray-50 with transition

**Utilization progress bar specification:**
```
┌──────────────────────────────────────────────────┐
│ ████████████████████░░░░░░░░░  78.5%              │
│ ←── green fill ──→←── gray ──→                    │
└──────────────────────────────────────────────────┘
Width: 48px (w-12), Height: 8px (h-2)
Background: gray-200, Fill: green-500, rounded-full
Fill width = min(utilization, 100)%
```

### 7.3 Data Table (Country View)

Same table structure but without the "Country" column. Shows aggregated country-level data instead of city-level.

| Column | Content |
|--------|---------|
| Country | Colored circle (12px, color from data) + country name |
| Inventories | From `countryData` lookup |
| Utilization | Same progress bar format |
| Conversion | Same color-coding |
| Deals | Same format |
| Revenue | Formatted with "all" currency (USD) |

---

## 8. Tab 3: Clients

This tab shows revenue breakdown by either agency or advertiser, with a toggle control.

### 8.1 Controls Row

**Left side:**
- Icon: Building2 (20x20px)
- Title: "Client Revenue Overview" (font-semibold)
- Dropdown: Toggle between "By Agency" and "By Advertiser" (width 160px)

**Right side:**
- Summary card (bg-blue-50, px-6, py-3, rounded-lg):
  - Label: "Total Revenue by Agency" or "Total Revenue by Advertiser" (dynamic)
  - Value: Sum of all filtered items' revenue, formatted as currency (text-xl, bold, blue-600)
  - Icon: Target (24x24px, blue-500)

### 8.2 Data Filtering Logic

- **Agency view:** Excludes entries where `agency === "Direct Clients"` (these are not actual agencies)
- **Advertiser view:** Excludes entries where `agency === "Direct"` (these represent direct relationships, not agency-mediated)

### 8.3 Client Revenue Table

Table inside bordered, rounded container.

**Header:** bg-gray-50

| Column | Header Text (Agency view / Advertiser view) | Content |
|--------|---------------------------------------------|---------|
| 1 | Agency / Advertiser | Building2 icon (16px, gray-500) + name (font-medium, gray-900) |
| 2 | Revenue | Formatted currency value (font-medium) |
| 3 | Deals | Deal count (font-medium) |
| 4 | Share | Percentage value with blue-600 color (font-medium) |
| 5 | Plan | Two lines: Line 1 = "{adPlays} ad plays ({sov}% SOV)" (font-medium, gray-900, text-xs). Line 2 = "{impressions} impressions" (text-gray-600, text-xs) |
| 6 | Top Campaigns | List of top 2 campaigns with name and revenue. If >2 campaigns, shows "+N more" in gray-500. Each line = "{name}: {revenue}" (text-xs, gray-600) |

---

## 9. Tab 4: Team

This tab shows individual sales team member performance as a ranked leaderboard.

### 9.1 Section Header

- Icon: Users (20x20px)
- Title: "Sales Team Performance" (font-semibold, gray-900)

### 9.2 Team Performance Table

Table inside bordered, rounded container.

**Header:** bg-gray-50

| Column | Header | Content |
|--------|--------|---------|
| 1 | Rank | Circular badge (24x24px): Rank 1 = yellow-500 bg, white text. Rank 2 = gray-400 bg, white text. Rank 3 = orange-600 bg, white text. Rank 4+ = gray-200 bg, gray-700 text. Shows rank number inside circle. |
| 2 | Name | Full name (font-medium, gray-900) |
| 3 | Region | Badge component with outline variant showing region name |
| 4 | Deals | Deal count (font-medium) |
| 5 | Revenue | Currency-formatted value (font-medium) |
| 6 | Conversion | Percentage value, color-coded: green-600 (≥80%), yellow-600 (70-79%), red-600 (<70%) |

**Row highlighting:** Top 3 rows (index < 3) have bg-green-50 background.

---

## 10. Currency Formatting

### 10.1 Core Formatter

All monetary values use a shared `formatCurrency` utility function:

```
Input: (value: number, options?: { currency?: string, showDecimals?: boolean })
Output: "{CURRENCY_CODE} {formatted_number}"

Examples:
  formatCurrency(2850000) → "USD 2,850,000"
  formatCurrency(45000, { currency: "MYR" }) → "MYR 45,000"
  formatCurrency(1282500, { currency: "SGD" }) → "SGD 1,282,500"
```

Key rules:
- Currency CODE is displayed (USD, MYR, SGD, THB), never symbols ($, RM, S$, ฿)
- No decimal places by default
- Thousands separators (commas) are always shown
- Currency code appears BEFORE the number with a space

### 10.2 Country-Specific Currency Mapping

| Country | Currency Code | Applied When |
|---------|--------------|-------------|
| All Countries (default) | USD | selectedCountry = "all" |
| Malaysia | MYR | selectedCountry = "malaysia" |
| Singapore | SGD | selectedCountry = "singapore" |
| Thailand | THB | selectedCountry = "thailand" |

The Y-axis labels on the bar chart and the chart tooltip both use the country-specific currency.

---

## 11. Loading State

When data is being fetched (`isLoading = true`), the widget renders:

```
Card
  CardHeader
    CardTitle: "Sales Performance Summary"
  CardContent
    Skeleton placeholder: full width, 300px tall
```

The Skeleton component renders an animated gray pulse rectangle that indicates loading progress.

---

## 12. Country-Specific Reference Data

The widget maintains a static lookup of country-level metrics used for the Regional tab (country view) and currency formatting:

| Country | Currency | Total Inventories | Utilization % | Revenue (local currency) | Conversion % | Programmatic Deals | Traditional Deals |
|---------|----------|-------------------|---------------|--------------------------|--------------|-------------------|-------------------|
| All | USD | 2,847 | 76.3 | 2,850,000 | 72 | 14 | 12 |
| Malaysia | MYR | 1,245 | 78.5 | 5,580,000 | 78 | 8 | 4 |
| Singapore | SGD | 892 | 85.2 | 1,282,500 | 85 | 5 | 3 |
| Thailand | THB | 710 | 65.8 | 25,200,000 | 65 | 3 | 3 |

---

## 13. Multi-Tenant Behavior

### 13.1 Tenant Context

The widget reads the currently selected company from a shared Tenant Context (React Context). When the user switches companies in the application header, the `selectedCompanyId` changes, which:

1. Invalidates the cache key (triggers refetch)
2. Loads company-specific data from the API
3. All four tabs reflect the new company's data

### 13.2 Security

The API verifies tenant access:
1. Checks the user's associated companies list
2. Returns 403 if the user does not have access to the requested `tenantCompanyId`
3. If no `tenantCompanyId` is provided, returns data for the user's default company

---

## 14. Widget Framework Integration

### 14.1 Widget Registration

The widget is registered in the dashboard page's widget definition array:

```
{
  id: "sales-performance-summary",
  title: "Sales Performance Summary",
  description: "Team output, regional performance, and revenue pipeline strength",
  component: <SalesPerformanceSummary dateRange={dateRange} startDate={startDate} endDate={endDate} />,
  defaultSize: "large",
  defaultPosition: { x: 4, y: 0 },
  isDefault: true
}
```

### 14.2 Size Mapping

| Size | CSS Grid | Meaning |
|------|----------|---------|
| small | col-span-4, row-span-2 | 1/3 width, compact |
| medium | col-span-6, row-span-3 | Half width, moderate |
| large | col-span-12, row-span-4 | Full width, tall (default for this widget) |

### 14.3 Widget Card Wrapper

The Widget Framework wraps this component in:
```
Card (h-full)
  CardHeader (p-4)
    CardTitle (text-md, font-medium): "Sales Performance Summary"
    [Hover/Customize controls: move, resize, remove, info]
  CardContent (p-4, pt-0): <SalesPerformanceSummary />
```

The widget renders its OWN internal Card as well, creating a nested card structure. The outer Card comes from the framework; the inner Card comes from the component.

### 14.4 Customize Mode

In customize mode, users can:
- Drag to reorder widgets
- Cycle size between small → medium → large
- Cannot remove this widget (it is a default/required widget)

---

## 15. Dependency Map

### 15.1 UI Component Dependencies

| Component | Source | Usage |
|-----------|--------|-------|
| Card, CardContent, CardHeader, CardTitle, CardDescription | shadcn/ui | Outer container |
| Tabs, TabsList, TabsTrigger, TabsContent | shadcn/ui | 4-tab navigation |
| Select, SelectTrigger, SelectValue, SelectContent, SelectItem | shadcn/ui | Campaign type filter, regional view toggle, client view toggle |
| Badge | shadcn/ui | Region labels in tables |
| Button | shadcn/ui | (inherited from framework) |
| Skeleton | shadcn/ui | Loading state placeholder |

### 15.2 Charting Dependencies

| Component | Source | Usage |
|-----------|--------|-------|
| BarChart, Bar | recharts | Revenue trend bar chart |
| ResponsiveContainer | recharts | Auto-sizing chart wrapper |
| XAxis, YAxis | recharts | Chart axes |
| Tooltip, CartesianGrid | recharts | Chart interactivity and grid lines |

### 15.3 Icon Dependencies

| Icon | Source | Usage |
|------|--------|-------|
| Target | lucide-react | Title icon, Average Deal Size KPI, summary cards |
| DollarSign | lucide-react | Total Deal Value KPI |
| FileText | lucide-react | Deals Closed KPI, deal counts in tables |
| Percent | lucide-react | Conversion Rate KPI |
| Users | lucide-react | Team tab header |
| MapPin | lucide-react | Regional tab header |
| Building2 | lucide-react | Country/agency icons in tables, summary cards |
| TrendingUp | lucide-react | (available for growth indicators) |
| TrendingDown | lucide-react | (available for decline indicators) |
| UserCheck | lucide-react | (imported, available) |
| ChevronDown | lucide-react | (imported, available) |

### 15.4 Utility Dependencies

| Utility | Source | Purpose |
|---------|--------|---------|
| `formatCurrency(value, options)` | `@/lib/currency-utils` | Format monetary values with currency codes |
| `formatLargeNumber(value)` | `@/lib/currency-utils` | Abbreviate large numbers (e.g., 3.6M) |
| `formatImpressions(value)` | `@/lib/currency-utils` | Format impression counts |
| `chartCurrencyFormatter(value)` | `@/lib/currency-utils` | Chart tooltip formatting |
| `formatDateShort(date)` | `@/lib/date-utils` | Format dates as "MMM dd" for chart labels |
| `useTenant()` | `@/contexts/tenant-context` | Access currently selected company ID |

---

## 16. Visual Design Tokens

### 16.1 Color System

| Token | Hex | Usage |
|-------|-----|-------|
| Blue-50 | #EFF6FF | KPI card background (Total Deal Value), summary cards |
| Blue-500 | #3B82F6 | Bar chart fill, icons, highlighted values |
| Blue-600 | #2563EB | KPI value text, share percentages |
| Green-50 | #F0FDF4 | KPI card background (Average Deal Size), top-3 row highlighting |
| Green-500 | #22C55E | Progress bar fill |
| Green-600 | #16A34A | High conversion values (≥80%) |
| Purple-50 | #FAF5FF | KPI card background (Deals Closed) |
| Purple-600 | #9333EA | KPI value text |
| Orange-50 | #FFF7ED | KPI card background (Conversion Rate) |
| Orange-600 | #EA580C | KPI value text, rank 3 badge |
| Yellow-500 | #EAB308 | Rank 1 badge background |
| Yellow-600 | #CA8A04 | Medium conversion values (70-79%) |
| Red-600 | #DC2626 | Low conversion values (<70%) |
| Gray-50 | #F9FAFB | Table headers, hover states |
| Gray-200 | #E5E7EB | Progress bar backgrounds, rank 4+ badge |
| Gray-400 | #9CA3AF | Secondary icons, rank 2 badge |
| Gray-500 | #6B7280 | Subtle icons |
| Gray-600 | #4B5563 | Secondary text |
| Gray-700 | #374151 | Label text, table values |
| Gray-900 | #111827 | Primary text, names, revenue values |

### 16.2 Typography Scale

| Element | Size | Weight | Color |
|---------|------|--------|-------|
| Widget title | base (16px) | semibold | inherits |
| Widget description | sm (14px) | normal | muted |
| KPI value | 2xl (24px) | bold | per-card color |
| KPI label | sm (14px) | normal | gray-600 |
| Tab trigger text | sm (14px) | medium | inherits |
| Table header | sm (14px) | semibold or medium | gray-700 |
| Table body text | sm (14px) | medium | gray-900 or gray-700 |
| Badge text | xs (12px) | medium | inherits |
| Campaign list | xs (12px) | normal | gray-600 |
| Chart title | base (16px) | semibold | gray-900 |
| Summary card value | xl (20px) | bold | blue-600 |
| Summary card label | sm (14px) | normal | gray-600 |

### 16.3 Spacing

| Context | Value |
|---------|-------|
| Card padding | 16px (p-4) |
| KPI card padding | 16px (p-4) |
| KPI card grid gap | 16px (gap-4) |
| Tab content spacing | 24px between sections (space-y-6) |
| Table cell padding | 16px horizontal, 12-16px vertical (px-4 py-3 or py-4) |
| Summary card padding | 24px horizontal, 12px vertical (px-6 py-3) |
| Chart area height | 180px |
| Minimum content height | 500px |

---

## 17. Responsive Behavior

| Breakpoint | KPI Grid | Table | Tabs |
|------------|----------|-------|------|
| Mobile (<768px) | 2 columns (grid-cols-2) | Horizontal scroll | Stacked full-width (grid-cols-4 compresses) |
| Desktop (≥1024px) | 4 columns (lg:grid-cols-4) | Full display | 4 equal-width triggers |

Tables do not collapse into cards on mobile — they remain as tables with potential horizontal overflow.

---

## 18. Interaction Behaviors

### 18.1 Tab Switching

- Clicking a tab trigger immediately switches the visible content
- State is maintained locally (not persisted across page reloads)
- No animation between tab transitions

### 18.2 Dropdown Selections

- Campaign Type filter: Changing selection would filter API data (currently uses mock data)
- Regional View toggle: Instantly switches between city table and country table
- Client View toggle: Instantly switches between agency data and advertiser data, recalculates total revenue

### 18.3 Table Row Hover

- All table rows apply `hover:bg-gray-50` with `transition-colors` for smooth effect
- No click actions on rows (rows are display-only)

### 18.4 Chart Tooltip

- Appears on hover over bar segments
- Shows: Period label (bold) + "Value: {formatted currency}" (small, gray)
- White background, border, rounded corners, box shadow

---

## 19. Error and Edge Case Handling

### 19.1 Missing Data Fallbacks

The component uses optional chaining and defaults throughout:
```
data.cityPerformance?.length || 10     (fallback count)
data.cityPerformance?.map(...) || []   (fallback empty array)
data.totalProposalValue                (no explicit fallback — undefined renders blank)
```

### 19.2 Zero-State

If the API returns empty arrays or zero values, the widget still renders its full structure (headers, tabs, table skeletons) — it does not show a "no data" empty state message.

### 19.3 API Failure

If the fetch fails, the `useQuery` error state is not explicitly handled in the component. The widget would remain in loading state or show stale cached data (depending on TanStack Query configuration).

---

## 20. Conversion Threshold Color Logic

This logic is used consistently across all tabs wherever conversion percentages appear:

```
If conversion >= 80 → text-green-600  (strong performance)
If conversion >= 70 → text-yellow-600 (moderate performance)
If conversion < 70  → text-red-600    (needs improvement)
```

---

## 21. Reconstruction Checklist for AI Builders

To rebuild this widget in another application, implement in this order:

1. **Data model:** Define the API response shape (Section 4.5)
2. **Currency utility:** Create the `formatCurrency` function that outputs "CODE number" format (Section 10)
3. **Date utility:** Create `formatDateShort` for chart labels (Section 6.2)
4. **Loading skeleton:** Build the skeleton loading state (Section 11)
5. **KPI cards:** Build the 4-card grid with colored backgrounds and icons (Section 6.1)
6. **Bar chart:** Implement the date-range-aware trend chart with dynamic period calculation (Section 6.2)
7. **Regional table:** Build both city and country views with progress bars and color-coded conversions (Section 7)
8. **Client table:** Build with agency/advertiser toggle and campaign sub-lists (Section 8)
9. **Team table:** Build with ranked leaderboard and medal-style badges (Section 9)
10. **Tab container:** Wire all four views into a tab interface (Sections 6-9)
11. **Header controls:** Add the campaign type dropdown filter (Section 5.1)
12. **Multi-tenant context:** Connect to your application's company/tenant selector (Section 13)
13. **Widget framework:** Register as a dashboard widget with size/position defaults (Section 14)
