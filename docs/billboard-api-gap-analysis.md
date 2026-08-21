# Billboard API Gap Analysis for MW Planner Integration

## Executive Summary

This document provides a comprehensive analysis of the Billboard API against all MW Planner use cases discovered through page-by-page examination of the codebase. The analysis identifies critical gaps that must be addressed for successful integration.

## Methodology

Examined all MW Planner pages and components that interact with inventory data:
- Inventories Page (inventory management and filtering)
- Inventory Side Panel (campaign creation inventory browsing)
- Media Selection Component (inventory selection for campaigns)
- Creative Assignment Page (matching creatives to inventory specs)
- Campaign Reservations Page (availability and reservation management)
- Map View Page (geographic visualization and targeting)
- Availability Calendar (booking timeline management)

## MW Planner Inventory Schema

**Current Schema Fields:**
- id, name, type, format
- location (JSON: {lat, lng, address})
- country, state, district
- dimensions (JSON: {width, height, unit})
- mediaOwnerId
- cpm (Cost Per Thousand Impressions)
- dailyImpressions
- availabilityCalendar (JSON with dates and availability)
- thumbnailUrl
- images (JSON array)
- venueType
- targetAudience (JSON)
- blacklist fields (isBlacklisted, blacklistReason, blacklistedBy, blacklistedDate)
- createdAt

## Use Case Breakdown

### 1. Inventory Listing & Management

**Inventories Page Requirements:**

Filtering capabilities needed:
- Country filter (United States, Malaysia, Singapore, Thailand, Indonesia, Philippines, Vietnam)
- State/province filter
- District filter
- Inventory type filter (billboard, transit, digital, street-furniture, etc.)
- Format filter (landscape, portrait, video, classic, 3D, etc.)
- Media owner filter
- Blacklist/whitelist status filter

Search capabilities:
- Text search across inventory name
- Search across location fields

Bulk operations:
- CSV upload for bulk inventory import
- Bulk blacklist/whitelist management
- Template creation from selected inventories

Grouping:
- Group inventories by media owner

**API Gaps:**

❌ No query parameters for filtering:
- `GET /billboards?country=Singapore&state=Downtown&district=Marina Bay`
- `GET /billboards?type=digital&format=video`
- `GET /billboards?media_owner_id=123`
- `GET /billboards?blacklisted=false`

❌ No search endpoint:
- `GET /billboards?search=Times Square`

❌ No pagination:
- `GET /billboards?page=1&limit=50`

❌ No sorting:
- `GET /billboards?sort_by=cpm&order=asc`

❌ No bulk import validation endpoint (CSV upload support exists but no validation)

❌ No media owner field in billboard response

❌ No blacklist/whitelist status fields

### 2. Campaign Creation - Inventory Selection

**Inventory Side Panel Requirements:**

Advanced filtering:
- State filter
- City filter
- Operation modes (Loop, Spot)
- Execution types (Programmatic, Traditional)
- Size categories (Small, Medium, Large, Extra Large)
- Format options (varies by type - Classic/Digital/3D for billboards, Bus/Subway/Taxi for transit, etc.)
- POI categories (Retail, Dining, Entertainment, Education, Healthcare, Transportation, Sports, Hotels, Offices, Residential)
- Venue types (Mall, Airport, Transit Station, Sports Arena, Theater, Campus, Public Plaza, Highway, Stadium, Park)
- Lat/Long with radius search

Display metrics per inventory:
- Impressions
- Reach
- Frequency
- CPM
- Cost
- Ad Plays
- Number of screens
- Thumbnails

Actions:
- View on map (requires lat/lng)
- View availability calendar
- SOV (Share of Voice) adjustment input
- Column visibility customization

**API Gaps:**

❌ No operation mode field (loop vs spot)
❌ No execution type field (programmatic vs traditional)
❌ No size category field
❌ No POI proximity data
❌ No venue type field in response (exists in some endpoints but not comprehensive)
❌ No radius search: `GET /billboards/search/radius?lat=1.29&lng=103.85&radius_km=5`
❌ No reach, frequency, ad plays metrics
❌ No screens count
❌ No SOV calculation support
❌ Missing comprehensive format taxonomy

### 3. Geographic Targeting & Map View

**Map View Requirements:**

Display capabilities:
- Show inventories on map with markers
- Color-code by type (digital, billboard, transit, street-furniture)
- Popup with: name, type, CPM, impressions, availability percentage

Drawing tools:
- Polygon drawing for area selection
- Circle drawing for radius selection
- Line drawing for route visualization

Search:
- Find inventories within drawn shapes
- Get inventories along routes (for transit)

Visualization:
- Heatmap of impressions by hour
- 3D building view
- Inventory clustering for performance

**API Gaps:**

❌ No geo-spatial search:
- `POST /billboards/search/polygon` (with GeoJSON polygon)
- `POST /billboards/search/circle` (with lat, lng, radius)
- `GET /billboards/search/bbox?neLat=40.8&neLng=-73.9&swLat=40.7&swLng=-74.0`

❌ No transit route data:
- `GET /billboards/{id}/route` (polyline for transit inventory routes)

❌ No hourly impression data for heatmaps

❌ No clustering metadata for map optimization

❌ Missing availability percentage field in response

### 4. Availability & Reservations

**Campaign Reservations Requirements:**

Availability checking:
- Check if inventory is available for date range
- Show availability percentage
- List conflicting campaigns with dates and percentages
- Blackout dates

Reservation management:
- Create reservation for inventory
- Set individual expiry dates per inventory
- Add notes per inventory reservation
- Bulk reservation creation

Display data:
- Price
- Impressions
- CPM
- ECPM
- Specifications (illumination, visibility, traffic)
- Resolution
- Size

**API Gaps:**

❌ CRITICAL: No availability endpoints at all:
- `GET /billboards/{id}/availability?start_date=2025-01-01&end_date=2025-03-31`
- `POST /billboards/check-availability` (bulk check for multiple billboards)

❌ CRITICAL: No reservation/booking system:
- `POST /billboards/{id}/reservations`
- `GET /billboards/{id}/reservations`
- `PUT /billboards/{id}/reservations/{reservation_id}`
- `DELETE /billboards/{id}/reservations/{reservation_id}`

❌ No conflicting campaigns data

❌ No blackout dates management

❌ No specifications field (illumination, visibility, traffic details)

❌ No ECPM calculation

❌ No size field in standard format

### 5. Creative Assignment

**Creative Assignment Requirements:**

Inventory specifications needed:
- Resolution requirements (1920x1080, 1280x720, etc.)
- Duration requirements (10 sec, 20 sec, 30 sec, etc.)
- Supported file formats (JPG, PNG, MP4, HTML5, etc.)
- File size limits
- Aspect ratios

Grouping:
- Group inventories by resolution and duration for bulk creative assignment

Validation:
- Check if creative meets inventory specifications
- Show compatible vs incompatible creatives

**API Gaps:**

❌ Resolution field not in standard billboard response (may be in panels, but not clear)

❌ Duration requirements missing

❌ File size limits missing

❌ Aspect ratio specifications missing

❌ No creative validation endpoint:
- `POST /billboards/{id}/validate-creative`

❌ Supported creative formats exist but unclear if comprehensive

### 6. Pricing & Cost Calculation

**Pricing Requirements:**

Display costs:
- CPM rates
- Monthly rates
- Spot rates
- Daily rates
- Seasonal pricing variations

Calculate costs:
- Price for specific date range
- Price with SOV adjustment
- Price with volume discounts
- Price breakdown (base + fees)

**API Gaps:**

✅ `GET /billboards/{id}/prices` exists

❌ No price calculation for date range:
- `GET /billboards/{id}/calculate-price?start_date=2025-01-01&end_date=2025-01-31&sov=25`

❌ No bulk pricing:
- `POST /billboards/calculate-prices` (for multiple billboards at once)

❌ No pricing rules/modifiers visible (volume discounts, seasonal adjustments, early bird)

❌ No currency field per price

❌ No price breakdown (base price + fees structure)

❌ Monthly/daily/spot rate types not clearly defined

### 7. Selling Terms & Booking Rules

**Selling Terms Requirements:**

Booking rules:
- Minimum booking days (e.g., must book minimum 4 days)
- Consecutive days requirement (must be consecutive vs can be spread)
- Booking increments (book in 1-day, 7-day, or 28-day increments)
- Lead time requirements (must book X days in advance)

Dayparting:
- Dayparting availability (yes/no)
- Minimum hours for dayparting
- Available daypart slots
- Daypart pricing

Content restrictions:
- Industry exclusions
- Content category restrictions
- Competitor exclusions

Cancellation:
- Cancellation policy
- Cancellation fees
- Modification policy

**API Gaps:**

❌ CRITICAL: No selling terms endpoint at all

Recommended new endpoint: `GET /billboards/{id}/selling-terms`
Should return:
- min_booking_days
- requires_consecutive_days (boolean)
- dayparting_enabled (boolean)
- dayparting_min_hours
- dayparting_slots (array of time ranges)
- booking_increment_days
- lead_time_days
- cancellation_policy
- modification_policy
- blackout_dates

✅ Content exclusions endpoint exists: `GET /billboards/{id}/content-exclusions`

### 8. Network & Series Management

**Network Requirements:**

Network features:
- Display series of billboards (e.g., screens across a road)
- Elevator screens across condo blocks
- Bus route with multiple stops

Network-level operations:
- Get all billboards in a network
- Network-level pricing
- Network-level availability
- Network total impressions/reach
- Network geographic coverage area

**API Gaps:**

✅ `GET /networks` exists
✅ `GET /networks/{id}` exists

❌ No network-billboard relationship:
- `GET /networks/{id}/billboards`

❌ No billboard field indicating which network it belongs to

❌ No network type/category (elevator network, roadside series, etc.)

❌ No network geographic extent

❌ No network-level metrics (total impressions, reach)

❌ No network-level pricing

### 9. SSP/Programmatic Filtering

**SSP Requirements:**

Platform filtering:
- Filter billboards by SSP (Influence, VIOOH, Place Exchange)
- Show which SSPs have access to inventory
- SSP-specific pricing
- SSP-specific deal types

**API Gaps:**

✅ `GET /billboards/{id}/exposures` exists (might be related)
✅ `GET /billboards/{id}/supported-programmatic-deal-types` exists

❌ No SSP filtering:
- `GET /billboards?ssp=VIOOH`

❌ Unclear if exposures relate to SSPs

❌ No SSP-specific pricing

### 10. Media Owner & Company Context

**Multi-Tenant Requirements:**

Company association:
- Which media owner owns which billboards
- Filter billboards by media owner
- Media owner contact information
- Company relationships

**API Gaps:**

❌ CRITICAL: No media owner ID field in billboard responses

❌ No media owner details endpoint:
- `GET /media-owners`
- `GET /media-owners/{id}`
- `GET /media-owners/{id}/billboards`

❌ No filter by media owner:
- `GET /billboards?media_owner_id=123`

### 11. Performance & Bulk Operations

**Performance Requirements:**

Pagination & performance:
- Paginate large result sets
- Cursor-based pagination for better performance
- Total count of results
- Response time optimization

Bulk operations:
- Bulk availability check
- Bulk pricing calculation
- Bulk reservation
- Bulk update

**API Gaps:**

❌ No pagination on any endpoints

❌ No total count in responses

❌ No bulk operations endpoints

## Critical Gaps Summary

### Must Have for MVP:

1. **Geographic Filtering** - Filter by country/state/city/district with query parameters
2. **Availability System** - Complete availability/booking/reservation system
3. **Selling Terms** - Minimum days, dayparting, booking rules endpoint
4. **Price Calculation** - Calculate price for date range with SOV
5. **Media Owner Relationship** - Media owner ID in responses + filtering
6. **Comprehensive Details Endpoint** - Single endpoint returning all billboard data including relations
7. **Pagination & Sorting** - Handle large datasets efficiently

### Important for Full Functionality:

8. **Geo-Spatial Search** - Polygon/circle/radius/bbox search
9. **Network-Billboard Relationship** - Get billboards in a network
10. **Transit Routes** - Polyline data for transit inventory routes
11. **Bulk Operations** - Availability check, pricing, reservations for multiple billboards
12. **Search** - Text search across inventory name/location
13. **Creative Validation** - Check if creative meets specs
14. **Specifications** - Illumination, visibility, traffic, size categories
15. **SSP Filtering** - Filter by programmatic platform

### Nice to Have:

16. **POI Proximity** - Nearby points of interest (may be MW Planner internal)
17. **Hourly Metrics** - Impressions by hour for heatmaps
18. **Clustering Metadata** - For map performance optimization
19. **Historical Data** - Past performance, pricing trends
20. **Image Galleries** - Multiple photos per billboard with categories

## Recommended API Enhancements

### New Query Parameters for GET /billboards:

```
GET /billboards?
  country=Singapore&
  state=Downtown&
  district=Marina+Bay&
  type=digital&
  format=video&
  media_owner_id=123&
  venue_type=mall&
  operation_mode=loop&
  execution_type=programmatic&
  size_category=large&
  available_from=2025-01-01&
  available_to=2025-03-31&
  min_cpm=10&
  max_cpm=50&
  min_impressions=100000&
  ssp=VIOOH&
  search=Times+Square&
  page=1&
  limit=50&
  sort_by=cpm&
  order=asc
```

### New Endpoints Needed:

**Availability & Reservations:**
- `GET /billboards/{id}/availability?start_date=X&end_date=Y`
- `POST /billboards/check-availability` (bulk)
- `POST /billboards/{id}/reservations`
- `GET /billboards/{id}/reservations`

**Pricing:**
- `GET /billboards/{id}/calculate-price?start_date=X&end_date=Y&sov=25`
- `POST /billboards/calculate-prices` (bulk)

**Selling Terms:**
- `GET /billboards/{id}/selling-terms`

**Geo-Spatial:**
- `POST /billboards/search/polygon`
- `POST /billboards/search/circle`
- `GET /billboards/search/radius?lat=X&lng=Y&radius_km=Z`
- `GET /billboards/search/bbox?neLat=X&neLng=Y&swLat=Z&swLng=W`

**Networks:**
- `GET /networks/{id}/billboards`

**Media Owners:**
- `GET /media-owners`
- `GET /media-owners/{id}`
- `GET /media-owners/{id}/billboards`

**Transit:**
- `GET /billboards/{id}/route` (polyline for transit)

**Details:**
- `GET /billboards/{id}/full` (comprehensive details with all relations)

### Response Enhancement Needed:

Add to billboard response:
- media_owner_id
- media_owner_name
- operation_mode (loop/spot)
- execution_types (array: [programmatic, traditional])
- size_category
- availability_percentage
- resolution
- duration_options
- file_size_limits
- aspect_ratios
- illumination_type
- visibility_details
- traffic_details
- screens_count
- blacklist_status
- currency
- monthly_rate
- spot_rate
- daily_rate

## Next Steps

1. Provide sample data for testing (inventories with different countries, media owners, types)
2. Prioritize which gaps to address first
3. Design API spec for critical missing endpoints
4. Implement filtering and search on existing list endpoints
5. Build availability and reservation system
6. Add selling terms endpoint
7. Implement price calculation logic
8. Add media owner relationship
9. Build geo-spatial search capabilities
10. Add pagination and bulk operations

## Conclusion

The Billboard API has a solid foundation with CRUD operations for different billboard types and relationship management for prices, panels, venues, and formats. However, significant gaps exist in:

1. Filtering and search capabilities
2. Availability and reservation management (CRITICAL)
3. Selling terms and booking rules (CRITICAL)
4. Price calculation for campaigns (CRITICAL)
5. Media owner relationships (CRITICAL)
6. Geographic search and targeting
7. Network operations
8. Performance optimization features

Addressing the Critical gaps is essential before MW Planner can effectively use this API for campaign planning workflows.
