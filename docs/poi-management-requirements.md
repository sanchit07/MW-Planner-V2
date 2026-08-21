# POI Management - Complete Requirements Document

## 1. Overview

### 1.1 Purpose
POI (Point of Interest) Management enables users to create, manage, and organize location-based data for inventory placement, audience targeting, and campaign planning within MW Planner.

### 1.2 Scope
- POI CRUD operations (Create, Read, Update, Delete)
- Bulk import/export via Excel template
- Category-based organization with visual icons
- Geolocation integration with map visualization
- Performance metrics tracking (orders, revenue)
- Brand association and hierarchy

---

## 2. Data Model

### 2.1 POI Entity Schema

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `poi_id` | String (UUID) | Auto-generated | System-generated unique identifier. Leave blank for new POIs. |
| `poi_name` | String (255) | Yes | User-defined name for the POI |
| `brand` | String (100) | Yes | Brand name (must exist in system) |
| `latitude` | Decimal (10,8) | Yes | Geographic coordinate (latitude) |
| `longitude` | Decimal (11,8) | Yes | Geographic coordinate (longitude) |
| `country` | String (100) | Yes | Country name |
| `state` | String (100) | Yes | State/Province/Region |
| `district` | String (100) | Yes | District/City |
| `sub_district` | String (100) | No | Sub-district/Area (optional) |
| `poi_type` | String (50) | Yes | User-defined POI type |
| `poi_category` | Enum | Yes | Pre-defined category from system list |
| `gross_orders` | Integer | No | Number of orders per month |
| `gross_revenue` | Decimal (15,2) | No | Monthly revenue from POI |
| `status` | Enum | Yes | active, inactive, pending |
| `created_at` | DateTime | Auto | Creation timestamp |
| `updated_at` | DateTime | Auto | Last update timestamp |
| `created_by` | UUID | Auto | User who created the POI |
| `tenant_id` | UUID | Yes | Multi-tenant company association |

### 2.2 Database Schema (Drizzle ORM)

```typescript
// shared/schema.ts
export const pois = pgTable('pois', {
  id: uuid('id').primaryKey().defaultRandom(),
  poiName: varchar('poi_name', { length: 255 }).notNull(),
  brand: varchar('brand', { length: 100 }).notNull(),
  latitude: decimal('latitude', { precision: 10, scale: 8 }).notNull(),
  longitude: decimal('longitude', { precision: 11, scale: 8 }).notNull(),
  country: varchar('country', { length: 100 }).notNull(),
  state: varchar('state', { length: 100 }).notNull(),
  district: varchar('district', { length: 100 }).notNull(),
  subDistrict: varchar('sub_district', { length: 100 }),
  poiType: varchar('poi_type', { length: 50 }).notNull(),
  poiCategory: varchar('poi_category', { length: 50 }).notNull(),
  grossOrders: integer('gross_orders').default(0),
  grossRevenue: decimal('gross_revenue', { precision: 15, scale: 2 }).default('0'),
  status: varchar('status', { length: 20 }).notNull().default('active'),
  createdAt: timestamp('created_at').defaultNow(),
  updatedAt: timestamp('updated_at').defaultNow(),
  createdBy: uuid('created_by').references(() => users.id),
  tenantId: uuid('tenant_id').notNull().references(() => companies.id),
});
```

---

## 3. POI Categories with Icons

### 3.1 Category List

| Category | Icon | Description |
|----------|------|-------------|
| 🏪 Convenience Store | `Store` | Mini-marts, 7-Eleven, convenience shops |
| 🛒 Supermarket | `ShoppingCart` | Grocery stores, hypermarkets |
| 🏬 Department Store | `Building2` | Large retail department stores |
| 🛍️ Shopping Mall | `ShoppingBag` | Shopping centers, malls |
| 🍽️ Restaurant | `UtensilsCrossed` | Dining establishments |
| ☕ Cafe | `Coffee` | Coffee shops, tea houses |
| 🍔 Fast Food | `Sandwich` | Quick service restaurants |
| ⛽ Gas Station | `Fuel` | Petrol stations, fuel stops |
| 🏥 Hospital | `Hospital` | Medical facilities |
| 💊 Pharmacy | `Pill` | Drug stores, pharmacies |
| 🏦 Bank | `Landmark` | Financial institutions |
| 🏨 Hotel | `Hotel` | Accommodation, lodging |
| ✈️ Airport | `Plane` | Airports, terminals |
| 🚉 Train Station | `Train` | Rail stations, metro stops |
| 🚌 Bus Terminal | `Bus` | Bus stations, depots |
| 🎓 Education | `GraduationCap` | Schools, universities |
| 🏢 Office Building | `Building` | Commercial offices |
| 🏭 Factory | `Factory` | Industrial facilities |
| 🏟️ Sports Venue | `Trophy` | Stadiums, gyms, arenas |
| 🎭 Entertainment | `Ticket` | Cinemas, theaters |
| 🏛️ Government | `Landmark` | Government offices |
| ⛪ Religious | `Church` | Places of worship |
| 🏖️ Tourism | `Palmtree` | Tourist attractions |
| 🅿️ Parking | `ParkingCircle` | Parking lots, garages |
| 🏠 Residential | `Home` | Housing areas |
| 📦 Warehouse | `Warehouse` | Storage, distribution |
| 🔧 Service Center | `Wrench` | Repair, service shops |
| 💇 Salon | `Scissors` | Beauty, grooming |
| 🏋️ Fitness | `Dumbbell` | Gyms, fitness centers |
| 🎮 Gaming | `Gamepad2` | Arcades, gaming centers |

### 3.2 Category Icon Implementation

```typescript
// client/src/lib/poi-categories.ts
import {
  Store, ShoppingCart, Building2, ShoppingBag, UtensilsCrossed,
  Coffee, Sandwich, Fuel, Hospital, Pill, Landmark, Hotel,
  Plane, Train, Bus, GraduationCap, Building, Factory,
  Trophy, Ticket, Church, Palmtree, ParkingCircle, Home,
  Warehouse, Wrench, Scissors, Dumbbell, Gamepad2
} from 'lucide-react';

export const POI_CATEGORIES = [
  { value: 'convenience_store', label: 'Convenience Store', icon: Store, color: '#22c55e' },
  { value: 'supermarket', label: 'Supermarket', icon: ShoppingCart, color: '#3b82f6' },
  { value: 'department_store', label: 'Department Store', icon: Building2, color: '#8b5cf6' },
  { value: 'shopping_mall', label: 'Shopping Mall', icon: ShoppingBag, color: '#ec4899' },
  { value: 'restaurant', label: 'Restaurant', icon: UtensilsCrossed, color: '#f97316' },
  { value: 'cafe', label: 'Cafe', icon: Coffee, color: '#a16207' },
  { value: 'fast_food', label: 'Fast Food', icon: Sandwich, color: '#eab308' },
  { value: 'gas_station', label: 'Gas Station', icon: Fuel, color: '#ef4444' },
  { value: 'hospital', label: 'Hospital', icon: Hospital, color: '#dc2626' },
  { value: 'pharmacy', label: 'Pharmacy', icon: Pill, color: '#16a34a' },
  { value: 'bank', label: 'Bank', icon: Landmark, color: '#1d4ed8' },
  { value: 'hotel', label: 'Hotel', icon: Hotel, color: '#7c3aed' },
  { value: 'airport', label: 'Airport', icon: Plane, color: '#0ea5e9' },
  { value: 'train_station', label: 'Train Station', icon: Train, color: '#6366f1' },
  { value: 'bus_terminal', label: 'Bus Terminal', icon: Bus, color: '#14b8a6' },
  { value: 'education', label: 'Education', icon: GraduationCap, color: '#f59e0b' },
  { value: 'office_building', label: 'Office Building', icon: Building, color: '#64748b' },
  { value: 'factory', label: 'Factory', icon: Factory, color: '#71717a' },
  { value: 'sports_venue', label: 'Sports Venue', icon: Trophy, color: '#ca8a04' },
  { value: 'entertainment', label: 'Entertainment', icon: Ticket, color: '#e11d48' },
  { value: 'government', label: 'Government', icon: Landmark, color: '#0f766e' },
  { value: 'religious', label: 'Religious', icon: Church, color: '#a855f7' },
  { value: 'tourism', label: 'Tourism', icon: Palmtree, color: '#22d3ee' },
  { value: 'parking', label: 'Parking', icon: ParkingCircle, color: '#2563eb' },
  { value: 'residential', label: 'Residential', icon: Home, color: '#84cc16' },
  { value: 'warehouse', label: 'Warehouse', icon: Warehouse, color: '#78716c' },
  { value: 'service_center', label: 'Service Center', icon: Wrench, color: '#ea580c' },
  { value: 'salon', label: 'Salon', icon: Scissors, color: '#db2777' },
  { value: 'fitness', label: 'Fitness', icon: Dumbbell, color: '#059669' },
  { value: 'gaming', label: 'Gaming', icon: Gamepad2, color: '#7c3aed' },
] as const;

export type POICategory = typeof POI_CATEGORIES[number]['value'];
```

---

## 4. User Interface Requirements

### 4.1 POI List Page

#### 4.1.1 Header Section
- **Title**: "POI Management"
- **Action Buttons**:
  - `+ New POI` - Opens POI creation form
  - `Import POIs` - Opens bulk import modal
  - `Export POIs` - Downloads current filtered list as Excel
  - `Download Template` - Downloads blank Excel template

#### 4.1.2 Filter Section
| Filter | Type | Options |
|--------|------|---------|
| Search | Text | Search by POI name, brand |
| Country | Dropdown | All countries in dataset |
| State | Dropdown | States filtered by selected country |
| Category | Multi-select | All POI categories with icons |
| Status | Dropdown | Active, Inactive, All |
| Brand | Dropdown | All brands in system |

#### 4.1.3 Data Table Columns
| Column | Sortable | Description |
|--------|----------|-------------|
| POI ID | Yes | System-generated ID |
| POI Name | Yes | Name with category icon |
| Brand | Yes | Associated brand |
| Category | Yes | Category with icon |
| Location | No | District, State, Country |
| Coordinates | No | Lat/Long with map link |
| Gross Orders | Yes | Monthly orders |
| Gross Revenue | Yes | Monthly revenue (formatted) |
| Status | Yes | Active/Inactive badge |
| Actions | No | Edit, View, Delete |

#### 4.1.4 Row Actions
- **View**: Opens POI detail side panel
- **Edit**: Opens POI edit form
- **Delete**: Confirmation dialog, then soft delete
- **View on Map**: Opens map centered on POI

### 4.2 New POI / Edit POI Form

#### 4.2.1 Form Layout (2-column responsive)

**Left Column - Basic Information:**
```
POI Name*           [Text Input]
Brand*              [Searchable Dropdown]
POI Type*           [Text Input]
POI Category*       [Dropdown with Icons]
Status              [Toggle: Active/Inactive]
```

**Right Column - Location:**
```
Latitude*           [Decimal Input] [📍 Pick on Map]
Longitude*          [Decimal Input]
Country*            [Dropdown]
State*              [Dropdown - filtered by Country]
District*           [Dropdown - filtered by State]
Sub-District        [Text Input - optional]
```

**Bottom Section - Performance Metrics:**
```
Gross Orders (Monthly)    [Number Input]
Gross Revenue (Monthly)   [Currency Input]
```

#### 4.2.2 Map Picker Component
- Interactive Mapbox map
- Click to set coordinates
- Drag marker to adjust position
- Geocoding search bar
- Current location button

#### 4.2.3 Validation Rules
| Field | Validation |
|-------|------------|
| POI Name | Required, 1-255 characters |
| Brand | Required, must exist in system |
| Latitude | Required, -90 to 90 |
| Longitude | Required, -180 to 180 |
| Country | Required |
| State | Required |
| District | Required |
| POI Category | Required, from predefined list |
| POI Type | Required, 1-50 characters |
| Gross Orders | Optional, integer >= 0 |
| Gross Revenue | Optional, decimal >= 0 |

### 4.3 POI Detail Side Panel

#### 4.3.1 Header
- POI Name with category icon
- Status badge
- Edit button

#### 4.3.2 Sections
1. **Location Map** - Small map showing POI location
2. **Basic Info** - Name, Brand, Type, Category
3. **Address** - Full address breakdown
4. **Coordinates** - Lat/Long with copy button
5. **Performance** - Orders & Revenue metrics
6. **Metadata** - Created date, Updated date, Created by

---

## 5. Bulk Import/Export

### 5.1 Excel Template Structure

#### Sheet 1: "Instructions" (Read-only)
| Field | Description |
|-------|-------------|
| POI ID | System-generated ID. Leave blank for new POIs. Do not modify for existing POIs. |
| POI Name | Provide name of your POI as per your choice |
| Brand | Provide the brand name as it is created in the system |
| Latitude | Provide geo-coordinates of your POI (latitude) |
| Longitude | Provide geo-coordinates of your POI (longitude) |
| Country | Required field |
| State | Required field |
| District | Required field |
| Sub-District | Optional |
| POI Type | Input your POI type |
| POI Category | Choose category from the list of pre-defined categories |
| Gross Orders | Update number of orders per month |
| Gross Revenue | Update monthly revenue from the POI |

**Guidelines:**
- Do not delete or make changes in the first two rows of 'POI List' sheet
- Do not edit the template structure
- Download the latest template from system
- If you wish to update existing POIs, download the list from system and update values
- Make sure your brand is created in the system and the Brand Name is used exactly
- Do not make changes in the POI ID field if updating existing POIs

#### Sheet 2: "POI List" (Data entry)
| A | B | C | D | E | F | G | H | I | J | K | L | M |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| POI ID | POI Name | Brand | Latitude | Longitude | Country | State | District | Sub-District | POI Type | POI Category | Gross Orders | Gross Revenue |
| (leave blank) | Store ABC | BrandX | 3.1390 | 101.6869 | Malaysia | Selangor | Petaling Jaya | SS2 | Retail | convenience_store | 500 | 25000 |

#### Sheet 3: "Categories" (Reference, dropdown source)
| Category Value | Category Label | Icon |
|----------------|----------------|------|
| convenience_store | 🏪 Convenience Store | Store |
| supermarket | 🛒 Supermarket | ShoppingCart |
| ... | ... | ... |

### 5.2 Import Process

#### 5.2.1 Import Modal UI
1. **Step 1: Upload**
   - Drag & drop zone for Excel file
   - "Browse" button
   - Download template link
   
2. **Step 2: Validation**
   - Progress bar showing validation status
   - Summary: X valid rows, Y errors
   - Error table with row number and error message
   
3. **Step 3: Preview**
   - Table showing first 10 valid records
   - Map preview with all POI markers
   
4. **Step 4: Confirm**
   - Import summary
   - "Import X POIs" button

#### 5.2.2 Import Validation
| Check | Error Message |
|-------|---------------|
| Required fields empty | "Row X: [Field] is required" |
| Invalid latitude | "Row X: Latitude must be between -90 and 90" |
| Invalid longitude | "Row X: Longitude must be between -180 and 180" |
| Brand not found | "Row X: Brand '[name]' does not exist in system" |
| Invalid category | "Row X: Invalid POI Category '[value]'" |
| Duplicate POI ID | "Row X: POI ID already exists" |
| Invalid number format | "Row X: [Field] must be a valid number" |

#### 5.2.3 Import Modes
- **Create Only**: Skip rows with existing POI IDs
- **Update Only**: Only process rows with existing POI IDs
- **Create & Update**: Process all valid rows (default)

### 5.3 Export Process

#### 5.3.1 Export Options
- Export All (respects current filters)
- Export Selected (checkbox selection)
- Export Template (blank template)

#### 5.3.2 Export File Format
- Excel (.xlsx) with same structure as import template
- Filename: `poi_export_[tenant]_[date].xlsx`

---

## 6. API Endpoints

### 6.1 REST API Routes

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/pois` | List POIs with filtering/pagination |
| GET | `/api/pois/:id` | Get single POI details |
| POST | `/api/pois` | Create new POI |
| PATCH | `/api/pois/:id` | Update POI |
| DELETE | `/api/pois/:id` | Soft delete POI |
| POST | `/api/pois/import` | Bulk import from Excel |
| GET | `/api/pois/export` | Export POIs to Excel |
| GET | `/api/pois/template` | Download import template |
| GET | `/api/pois/categories` | List all POI categories |

### 6.2 Query Parameters (GET /api/pois)

| Parameter | Type | Description |
|-----------|------|-------------|
| `page` | number | Page number (default: 1) |
| `limit` | number | Items per page (default: 20, max: 100) |
| `search` | string | Search POI name, brand |
| `country` | string | Filter by country |
| `state` | string | Filter by state |
| `district` | string | Filter by district |
| `category` | string[] | Filter by categories |
| `status` | string | active, inactive |
| `brand` | string | Filter by brand |
| `sortBy` | string | Field to sort by |
| `sortOrder` | string | asc, desc |
| `tenantId` | UUID | Tenant company ID (auto from session) |

### 6.3 Request/Response Examples

#### Create POI
```json
// POST /api/pois
{
  "poiName": "Central Market Store",
  "brand": "BrandX",
  "latitude": 3.1412,
  "longitude": 101.6953,
  "country": "Malaysia",
  "state": "Kuala Lumpur",
  "district": "Kuala Lumpur",
  "subDistrict": "Pasar Seni",
  "poiType": "Retail",
  "poiCategory": "convenience_store",
  "grossOrders": 750,
  "grossRevenue": 45000.00
}

// Response: 201 Created
{
  "id": "uuid-generated",
  "poiName": "Central Market Store",
  "brand": "BrandX",
  ...
  "createdAt": "2026-02-01T10:30:00Z"
}
```

#### List POIs
```json
// GET /api/pois?country=Malaysia&category=convenience_store,supermarket&page=1&limit=20

// Response: 200 OK
{
  "data": [
    {
      "id": "uuid-1",
      "poiName": "Store ABC",
      "brand": "BrandX",
      "poiCategory": "convenience_store",
      "district": "Petaling Jaya",
      "state": "Selangor",
      "country": "Malaysia",
      ...
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 156,
    "totalPages": 8
  }
}
```

---

## 7. Permissions & Access Control

### 7.1 Role-Based Permissions

| Role | View | Create | Edit | Delete | Import | Export |
|------|------|--------|------|--------|--------|--------|
| Admin | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Manager | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Planner | ✓ | ✓ | ✓ | ✗ | ✓ | ✓ |
| Viewer | ✓ | ✗ | ✗ | ✗ | ✗ | ✓ |

### 7.2 Multi-Tenant Isolation
- All POI queries automatically filtered by `tenantId`
- Users can only see POIs belonging to their current tenant context
- Tenant switch triggers query invalidation

---

## 8. Integration Points

### 8.1 Map Integration (Mapbox)
- POI markers on campaign map view
- Proximity search for inventory near POIs
- Heat map visualization of POI density
- Geocoding for address lookup

### 8.2 Inventory Association
- Link inventories to nearby POIs
- Proximity radius configuration
- POI-based targeting in campaign creation

### 8.3 Campaign Planning
- Target audience near POI locations
- Include POIs in proposal generation
- Performance attribution to POIs

### 8.4 Analytics
- POI performance dashboard
- Revenue/Orders trends
- Geographic distribution analysis

---

## 9. Implementation Phases

### Phase 1: Core CRUD (MVP)
- [ ] Database schema creation
- [ ] Basic API endpoints (CRUD)
- [ ] POI list page with filters
- [ ] New/Edit POI form
- [ ] POI detail side panel
- [ ] Map picker component

### Phase 2: Bulk Operations
- [ ] Excel template generation
- [ ] Import with validation
- [ ] Export functionality
- [ ] Progress tracking

### Phase 3: Advanced Features
- [ ] Map visualization of all POIs
- [ ] Inventory proximity linking
- [ ] POI performance analytics
- [ ] Campaign targeting integration

---

## 10. Technical Notes

### 10.1 Dependencies
- `xlsx` or `exceljs` - Excel file handling
- `@mapbox/mapbox-gl-geocoder` - Address search
- `zod` - Validation schemas
- `react-dropzone` - File upload

### 10.2 Performance Considerations
- Index on `tenant_id`, `country`, `state`, `poi_category`
- Pagination required for large datasets
- Lazy loading for map markers (cluster at zoom out)
- Virtual scrolling for import preview

### 10.3 Validation Schema (Zod)
```typescript
export const insertPOISchema = createInsertSchema(pois).omit({
  id: true,
  createdAt: true,
  updatedAt: true,
}).extend({
  latitude: z.coerce.number().min(-90).max(90),
  longitude: z.coerce.number().min(-180).max(180),
  grossOrders: z.coerce.number().int().min(0).optional(),
  grossRevenue: z.coerce.number().min(0).optional(),
  poiCategory: z.enum(POI_CATEGORIES.map(c => c.value) as [string, ...string[]]),
});
```

---

## Appendix A: Sample Data

| POI Name | Brand | Category | Location |
|----------|-------|----------|----------|
| KLCC Food Court | FoodHub | 🍽️ Restaurant | Kuala Lumpur, KL |
| Bangsar Village Outlet | RetailCo | 🛍️ Shopping Mall | Bangsar, KL |
| Subang Parade Store | BrandX | 🏪 Convenience Store | Subang Jaya, Selangor |
| KLIA Terminal 1 | AirportOps | ✈️ Airport | Sepang, Selangor |
| Pavilion KL | LuxuryBrands | 🏬 Department Store | Bukit Bintang, KL |

---

*Document Version: 1.0*
*Last Updated: February 2026*
*Author: MW Planner Team*
