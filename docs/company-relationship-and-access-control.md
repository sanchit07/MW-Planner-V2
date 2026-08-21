# MW Platform Access Control

## Purpose

This document explains how the Account Management platform controls access across MW products. Every section follows a consistent pattern: what the user sees in the interface, what conditions apply, and how the system behaves when those conditions are met or not met.

---

## Account Management Platform Structure

The Account Management platform is organized into the following main sections:

```
Account Management
├── Organizations (Company management)
├── Brands (Master brand list)
├── Agencies (Agency-Media Owner mappings)
├── Contracts (Contract configuration per company)
├── Users (User management across companies)
├── Roles & Functions (Role templates and function mappings)
├── Features (Feature flag management)
└── Audit Logs (Activity tracking)
```

---

## Organizations

### Organization List Page

The Organization page displays all companies in the system. The list supports search and filtering through a side drawer.

**Columns Displayed:**

| Column | Description |
|--------|-------------|
| Company Name | Name with logo thumbnail |
| Type | Media Owner, Media Agency, Advertiser, Reseller, Internal, Business Partner, Tech Partner |
| Country | Primary country from address |
| Parent/Child | Badge indicating hierarchy status |
| Market Access | Countries where company can operate |
| Contract Expiry | Date contract expires (red if < 30 days) |
| Products | Icons for enabled products (Planner, Activate, etc.) |
| Users | Current count / limit |
| Status | Active, Pending, Inactive |

**Filters (Side Drawer):**

| Filter | Options |
|--------|---------|
| Company Type | Multi-select: Media Owner, Media Agency, Advertiser, Reseller, Internal, Business Partner, Tech Partner |
| Country | Multi-select country list |
| Hierarchy | Parent Only, Child Only, All |
| Has Market Access | Specific countries where company operates |
| Contract Expiry | Before date, After date, Expired |
| Product Access | Has Planner, Has Activate, Has Measure, etc. |
| User Count | Greater than X users |

**Search:** By company name, user name, or user email.

**Actions Menu (3-dot menu per row):**

| Action | Description | Who Can Access |
|--------|-------------|----------------|
| Edit Company | Modify company details | Internal Admin, Company Admin |
| Contract | Open contract configuration | Internal Admin only |
| Manage Users | View/add/edit users for this company | Internal Admin, Company Admin |
| Deactivate Company | Suspend company and all users | Internal Admin only |
| View Audit Log | See all activity for this company | Internal Admin only |

---

### Create Company

Clicking "Create New" opens a multi-section form.

**Section 1: Company Details**

| Field | Required | Validation | Notes |
|-------|----------|------------|-------|
| Company Name | Yes | Min 2 characters, unique | Cannot duplicate existing name |
| Business Type | Yes | Select one | Options vary—see below |
| Registration Number | No | Alphanumeric | Company registration/tax ID |
| Phone Number | No | Valid phone format | Country code auto-applied |
| Website | No | Valid URL | Must include http/https |
| WhatsApp Number | No | Valid phone format | For support notifications |
| Company Email | Yes | Valid email, unique | Primary contact email |
| Email for Notifications | No | Valid email | System notifications go here |
| Telegram ID | No | @username format | Alternative notification channel |
| Company Logo | No | Image upload | PNG/JPG, max 2MB |

**Business Type Options:**

| Type | Who Can Create | Special Fields |
|------|----------------|----------------|
| Media Owner | Internal Admin | Selling Terms section enabled |
| Media Agency | Internal Admin | Agency mapping section enabled |
| Advertiser | Internal Admin, Agency Admin | Brand association enabled |
| Reseller | Internal Admin | "Reseller of" dropdown enabled, Selling Terms enabled |
| Internal | Super Admin only | Regional office settings enabled |
| Business Partner | Super Admin only | White-label configuration enabled |
| Tech Partner | Super Admin only | API credentials section enabled |

**Condition: User selects "Reseller"**
```
IF business_type == "Reseller":
    SHOW "Reseller of" dropdown
    OPTIONS: List of Media Owners in system
    REQUIRED: Yes
    BEHAVIOR: Selected Media Owner receives notification
              Reseller appears in Media Owner's partner list
```

**Condition: User selects "Internal"**
```
IF business_type == "Internal":
    IF current_user.role != "Super Admin":
        DENY: "Only Super Admin can create Internal companies"
        BUTTON: Disabled with tooltip
    ELSE:
        SHOW: Regional office configuration
        SHOW: Territory assignment
```

**Section 2: Company Hierarchy**

| Field | Required | Options |
|-------|----------|---------|
| Hierarchy Type | Yes | Standalone, Parent, Child |
| Parent Company | If Child | Dropdown of existing companies of same type |

**Condition: User selects "Child"**
```
IF hierarchy_type == "Child":
    SHOW: Parent Company dropdown
    FILTER: Only companies of same business_type
    FILTER: Only companies in overlapping countries
    VALIDATION: Parent's market access must include child's country
```

**Section 3: Company Address**

Country must be selected first. Other fields adapt based on country.

| Field | Required | Auto-Fill Logic |
|-------|----------|-----------------|
| Country | Yes | Sets currency, tax rules, address format |
| PIN/Post/Zip Code | Depends on country | Triggers auto-fill |
| State/Province | Yes | Auto-filled from postal code if available |
| District | Depends on country | Auto-filled from postal code if available |
| City | Yes | Auto-filled from postal code if available |
| Street | No | Free text |
| Building | No | Free text |

**Condition: User enters postal code for supported country**
```
IF country IN ["India", "Malaysia", "Singapore", "USA", "UK"]:
    IF postal_code.length >= minimum_for_country:
        API_CALL: Get address details from postal code
        IF success:
            AUTO_FILL: State, District, City
            FIELDS: Read-only with "Edit" link
        ELSE:
            FIELDS: Remain editable
            WARNING: "Could not auto-fill. Please enter manually."
```

**Section 4: Tax Configuration**

| Field | Auto-Fill | Notes |
|-------|-----------|-------|
| Enable Tax | Toggle | Default: Off |
| Tax Name | Yes, from country | GST, VAT, SST, etc. |
| Tax Rate % | Yes, from country | Standard rate for country |

**System Behavior:**
```
IF enable_tax == true:
    TAX applies to: Billing invoices only
    TAX does NOT apply to: Campaign costs shown in Planner
    DISPLAY: Tax line item on all invoices for this company
```

**Section 5: Bank Details**

Fields adapt based on country selection.

| Country | Required Fields |
|---------|-----------------|
| India | IFSC Code, Account Number (Bank Name auto-fills from IFSC) |
| Malaysia | Bank Name, Account Number, Swift Code |
| Singapore | Bank Name, Account Number, Swift Code, Branch Code |
| USA | Routing Number, Account Number, Bank Name |

**Condition: User enters IFSC code (India)**
```
IF country == "India" AND ifsc_code.length == 11:
    API_CALL: Validate IFSC and get bank details
    IF valid:
        AUTO_FILL: Bank Name, Branch Name
    ELSE:
        ERROR: "Invalid IFSC code"
```

---

### After Company Creation

When a company is created, the system:

1. Generates unique company ID
2. Creates default roles based on business type
3. Sets company status to "Pending Setup"
4. Sends notification to Internal Admin for contract configuration
5. Company appears in Organization list but cannot be used until contract is configured

---

## Contracts

Contract configuration is a separate page accessed from the Organization list's action menu.

### Contract Configuration Page

**Header:** Company name, type, and status

**Section 1: Market Access**

| Field | Description |
|-------|-------------|
| Primary Country | Auto-set from company address (cannot change) |
| Additional Markets | Multi-select countries where company can operate |

**System Behavior:**
```
DEFAULT: Company can operate only in primary country
IF additional_markets added:
    COMPANY can: Create inventory in those countries (Media Owner)
    COMPANY can: Book inventory in those countries (Agency)
    COMPANY can: See inventory from those countries (All types)
```

**Section 2: Contract Terms**

| Field | Type | Notes |
|-------|------|-------|
| Contract ID | Text | Internal reference number |
| Contract Start Date | Date | When access begins |
| Contract Expiry Date | Date | When access ends |
| Upload Contract Copy | File | PDF/DOC upload |
| Auto-Renewal | Toggle | If yes, contract extends automatically |

**Condition: Contract expires**
```
IF current_date > contract_expiry_date:
    IF auto_renewal == false:
        STATUS: Company set to "Inactive"
        BEHAVIOR: All users cannot log in
        NOTIFICATION: Sent 30, 7, 1 days before expiry
        NOTIFICATION: Sent on expiry date
    ELSE:
        STATUS: Remains "Active"
        NOTIFICATION: "Contract auto-renewed for [period]"
```

**Section 3: Product Access**

| Product | Toggle | Sub-options when enabled |
|---------|--------|--------------------------|
| Planner | On/Off | Campaign creation, Proposals, Reports |
| Activate | On/Off | Programmatic features |
| Measure | On/Off | Analytics dashboards |
| Verify | On/Off | Proof of play, verification |
| Studio | On/Off | Creative management |

**System Behavior:**
```
IF product_toggle == OFF:
    PRODUCT does not appear in user's navigation
    API calls to product endpoints return 403
    No error message shown—product simply not visible
```

**Section 4: Limits**

| Field | Type | System Behavior When Exceeded |
|-------|------|-------------------------------|
| User Limit | Number | New user invitations fail with message |
| Brand Limit | Number | New brand creation fails with message |
| Campaign Limit | Number | New campaign creation fails with message |

**Condition: User limit reached**
```
IF current_users >= user_limit:
    BEHAVIOR when Admin invites new user:
        INVITATION: Saved with status "Pending - Limit Reached"
        MESSAGE: "User limit reached. Contact Moving Walls to upgrade."
        NOTIFICATION: Sent to MW Account Manager
        NEW USER: Cannot log in until limit increased or user deactivated
```

**Section 5: Platform Fees**

| Field | Type | Description |
|-------|------|-------------|
| Fee Type | Select | Percentage, Fixed, Tiered |
| Fee Percentage | Number | If percentage type |
| Fixed Amount | Currency | If fixed type |
| Fee Tiers | Table | If tiered (spend ranges with different rates) |

**System Behavior:**
```
PLATFORM FEE is:
    CALCULATED: Automatically on all bookings
    VISIBLE TO: MW Internal only
    HIDDEN FROM: Company users, their clients
    ADDED TO: MW revenue statements
```

**Section 6: Selling Terms (Media Owners and Resellers only)**

| Field | Description |
|-------|-------------|
| Payment Terms | Net 30, Net 60, Prepaid, etc. |
| Minimum Booking Value | Minimum order amount |
| Cancellation Policy | Terms for booking cancellations |
| Credit Limit | Maximum outstanding amount |

---

## Relationships

Relationships define what one company can see and do with another company's data.

### Creating a Relationship

From the Organization detail page, click "Add Relationship" to open the relationship builder.

**Step 1: Select Companies**

| Field | Description |
|-------|-------------|
| Company A | The company whose page you're on |
| Company B | Select from dropdown (filtered by country overlap) |

**Step 2: Define Access Permissions**

The permissions available depend on the products enabled for both companies.

**If Planner is enabled:**

| Permission | Options | Description |
|------------|---------|-------------|
| Tenant Switching | Yes/No | Can Company B users switch to Company A's tenant context? |
| View Access Level | Full, Partial, None | What data can Company B see when switched? |
| Edit Access Level | Full, Partial, None | What can Company B modify when switched? |
| Campaign Approval Required | Yes/No | Must Company B approve campaigns created by Company A? |

**Condition: Tenant Switching enabled**
```
IF tenant_switching == Yes:
    SHOW: View Access Level options
    SHOW: Edit Access Level options
    
    IF view_access == "Full":
        CAN SEE: All campaigns, costs, agency names, brand names, comments
    
    IF view_access == "Partial":
        SHOW: Checklist of visible fields
        OPTIONS: Campaign name, Status, Dates, Costs, Agency, Brand, Comments, Custom Fees
    
    IF view_access == "None":
        CANNOT: Switch tenant (option hidden in UI)
```

**Condition: Edit Access Level**
```
IF edit_access == "Full":
    CAN: Create, edit, delete campaigns
    CAN: Manage inventory
    CAN: Upload creatives
    CAN: Approve workflows

IF edit_access == "Partial":
    SHOW: Checklist of allowed actions
    OPTIONS: Create campaign, Edit own campaigns, Upload creatives, Comment only

IF edit_access == "None":
    VIEW ONLY: No edit buttons shown
    ALL FORMS: Disabled
```

**Step 3: Support Access**

| Field | Description |
|-------|-------------|
| Support Access Enabled | Yes/No |
| Support Duration | Days/Months |
| Support Scope | Full access, Read-only, Specific modules |

**System Behavior for Support:**
```
IF support_access_enabled == Yes:
    INTERNAL users with Support role can:
        SWITCH: To this company's tenant
        ACCESS: Based on support_scope setting
        AUDIT: All actions logged with "Support Access" flag
    
    WHEN support_duration expires:
        ACCESS: Automatically revoked
        NOTIFICATION: Sent to company admin
```

---

## Brands

Brands is a master data page listing all brands across the platform.

### Brand List Page

**Columns:**

| Column | Description |
|--------|-------------|
| Brand Name | Name of the brand |
| Logo | Brand logo thumbnail |
| Associated Companies | Companies that have access to this brand |
| Campaigns | Count of campaigns using this brand |
| Status | Active, Inactive |

**Actions:**

| Action | Description | Who Can Access |
|--------|-------------|----------------|
| Create Brand | Add new brand | Any company (within their limit) |
| Edit Brand | Modify name, logo | Brand creator, Internal Admin |
| Map to Company | Associate brand with additional companies | Internal Admin |
| Deactivate | Hide brand from selection | Brand creator, Internal Admin |

### Create Brand

| Field | Required | Validation |
|-------|----------|------------|
| Brand Name | Yes | Unique within company |
| Logo | No | PNG/JPG, max 2MB |
| Industry | No | Select from list |
| Website | No | Valid URL |

**System Behavior:**
```
WHEN brand created:
    MAPPED TO: Creating company automatically
    VISIBLE IN: Campaign creation for that company
    NOT VISIBLE TO: Other companies unless explicitly mapped
```

---

## Agencies

The Agencies page manages relationships between Media Owners and Agencies.

### Agency-Media Owner Mapping

This determines which agencies can see which media owner's inventory.

**Columns:**

| Column | Description |
|--------|-------------|
| Agency Name | Agency company name |
| Media Owner | Media owner company name |
| Inventory Access | All, Specific, Request Only |
| Rate Card Visibility | Full, Discounted, Hidden |
| Booking Rights | Direct, Request-based, RFP only |
| Creative Approval | Auto, Agency, Media Owner |
| Status | Active, Pending, Inactive |

### Create Mapping

| Field | Options | System Behavior |
|-------|---------|-----------------|
| Agency | Select from agencies list | Agency must be active |
| Media Owner | Select from media owners list | Must have overlapping markets |
| Inventory Access | All, Specific locations, Premium only | Controls what shows in agency's search |
| Rate Card Visibility | Full rates, Discounted rates, Request only | Controls price display |
| Booking Rights | Direct booking, Request approval, RFP only | Controls booking workflow |
| Creative Approval | Auto-approve, Agency approves, Media Owner approves | Controls creative workflow |
| Territory | Select countries | Limits scope of relationship |

**Condition: Agency searches for inventory**
```
FOR EACH media_owner_mapping WHERE agency == current_user.company:
    IF inventory_access == "All":
        SHOW: All active inventory from media_owner
    IF inventory_access == "Specific":
        SHOW: Only inventory in specified locations
    IF inventory_access == "Premium only":
        SHOW: Only inventory tagged as premium
    
    IF rate_card_visibility == "Full":
        SHOW: Actual rate card prices
    IF rate_card_visibility == "Discounted":
        SHOW: Discounted prices per contract
    IF rate_card_visibility == "Hidden":
        SHOW: "Request Quote" button instead of price
```

---

## Users

### User List Page

Accessed from Organization action menu or global Users page.

**Columns:**

| Column | Description |
|--------|-------------|
| Name | User's full name |
| Email | User's email |
| Company | Company association(s) |
| Role | Assigned role |
| Function | Mapped function (Ad Ops, Finance, etc.) |
| Last Active | Last login timestamp |
| Status | Active, Pending Invite, Inactive |

**Actions:**

| Action | Description |
|--------|-------------|
| Invite User | Send invitation email |
| Edit User | Modify role, function, status |
| Reset Password | Send password reset email |
| Deactivate | Disable user access |
| Bulk Assign Features | Select multiple users, assign features |

### Invite User

| Field | Required | Notes |
|-------|----------|-------|
| Email | Yes | Must be unique in system |
| First Name | Yes | |
| Last Name | Yes | |
| Company | Yes | Pre-selected if from company page |
| Role | Yes | Select from company's roles |
| Function | Yes | Select from function list |

**System Behavior:**
```
WHEN user invited:
    IF company.user_count >= company.user_limit:
        STATUS: Invitation saved as "Pending - Limit Reached"
        USER: Cannot log in
        ADMIN: Sees warning message
    ELSE:
        EMAIL: Invitation sent with login link
        STATUS: "Pending Invite"
        EXPIRY: Link valid for 7 days
```

---

## Roles & Functions

### Functions

Functions are fixed categories that map to feature sets. Functions are defined by MW and cannot be created by companies.

**Function List by Company Type:**

| Company Type | Available Functions |
|--------------|---------------------|
| Media Owner | Operations Manager, Creative Approver, Sales Rep, Billing Admin, Viewer |
| Media Agency | Campaign Planner, Media Buyer, Account Director, Creative Manager, Viewer |
| Advertiser | Brand Manager, Approver, Viewer |
| Reseller | Sales Manager, Operations, Viewer |
| Internal | Super Admin, Account Manager, Ad Ops, Support, Finance, Security |

**Each function has default feature mappings:**

| Function | Default Features Enabled |
|----------|--------------------------|
| Campaign Planner | Create campaign, Edit campaign, View inventory, Upload creatives, View reports |
| Media Buyer | Above + Negotiate prices, Book inventory, Manage budgets |
| Operations Manager | All inventory features, Availability management, Rate card management |
| Creative Approver | View campaigns, Approve/reject creatives |
| Viewer | Read-only access to permitted areas |

### Roles

Roles are created per company and map to functions.

| Field | Description |
|-------|-------------|
| Role Name | Custom name (e.g., "Junior Planner", "Senior Buyer") |
| Function | Select from available functions for company type |
| Feature Overrides | Optional: Enable/disable specific features |

**System Behavior:**
```
WHEN role assigned to user:
    BASE FEATURES: From function mapping
    OVERRIDES: From role-level feature overrides
    FINAL FEATURES: Base + Overrides
    
WHEN user accesses Planner:
    CHECK: User's final feature set
    SHOW: Only enabled features
    HIDE: Disabled features (no error, just not visible)
```

### Function-Feature Mapping (Admin Only)

Internal Admins can modify the default function-feature mappings.

| Action | Who Can Do | System Behavior |
|--------|------------|-----------------|
| View mappings | Internal Admin | See all functions with their features |
| Add feature to function | Internal Admin | All users with that function gain feature |
| Remove feature from function | Internal Admin | All users with that function lose feature |
| Add new feature | Super Admin | New features appear unmapped; must be assigned |

**Spreadsheet Reference:** See [MW-Planner-Feature-Inventory.xlsx](./MW-Planner-Feature-Inventory.xlsx) for complete feature list.

---

## Features

Features are the granular controls that determine what users can see and do.

### Feature Hierarchy

```
Level 0: Module (e.g., Campaigns)
    Level 1: Action (e.g., Create Campaign)
        Level 2: Section (e.g., Step 1 - Campaign Details)
            Level 3: Control (e.g., Campaign Name Input)
```

### Feature Assignment

Features are assigned at the user level, but can be bulk-assigned.

**Assignment Methods:**

| Method | Description |
|--------|-------------|
| Via Function | User gets function's default features |
| Via Role Override | Role adds/removes specific features |
| Direct Assignment | Admin assigns features directly to user |
| Bulk Assignment | Select multiple users, assign features to all |

**System Behavior:**
```
FEATURE EVALUATION ORDER:
1. Start with function's default features
2. Apply role-level overrides
3. Apply direct user assignments (highest priority)

RESULT: Final feature set for user
```

### Feature Inheritance

| Condition | Behavior |
|-----------|----------|
| Parent feature disabled | All child features auto-disabled |
| Parent feature enabled | Child features use their own settings |
| Child feature disabled | Only that child hidden; siblings unaffected |

**Example:**
```
IF "Campaigns" module disabled:
    ALL campaign-related features hidden
    User cannot: See campaigns in nav, create, edit, view

IF "Campaigns" enabled but "Create Campaign" disabled:
    User can: View campaigns, edit existing
    User cannot: See "Create Campaign" button
```

---

## Tenant Context Switching

Users with appropriate relationship permissions can switch between company contexts.

### When Switcher Appears

The company switcher appears in the top navigation when:

```
CONDITIONS for showing switcher:
    User has memberships in multiple companies
    OR User's company has support access to other companies
    OR User's company is parent with view access to children
    OR User's company has managed service relationship with others
```

### Switching Behavior

**When user switches from Company A to Company B:**

1. Interface reloads with Company B's context
2. Company B's branding applied (if white-labeled)
3. Navigation shows only Company B's enabled products
4. Data filtered to Company B only
5. User's actions limited by relationship permissions

**Audit Trail:**
```
EVERY ACTION in switched context:
    LOGS: User identity (original company)
    LOGS: Acting-as company
    LOGS: Action performed
    LOGS: Timestamp
    VISIBLE TO: Company B admins can see this audit
```

---

## Campaign Approval Workflow

Approval requirements are determined by relationships.

### Stage Evaluation

```
FOR EACH campaign:
    
    STAGE 1: Agency Acceptance
    IF campaign.creator.type != "Agency" 
       AND relationship.requires_agency_approval:
        ADD stage: Agency Acceptance
        WAIT FOR: Agency to approve
    ELSE:
        SKIP stage
    
    STAGE 2: Platform Review
    IF campaign.involves_managed_service_client
       AND managed_service_contract.requires_platform_review:
        ADD stage: Platform Review
        WAIT FOR: Internal Admin to approve
    ELSE:
        SKIP stage
    
    STAGE 3: Media Owner Approval
    FOR EACH media_owner IN campaign.inventory_owners:
        IF relationship.creative_approval == "Media Owner":
            ADD stage: Media Owner Approval for media_owner
            WAIT FOR: Media Owner to approve creatives
    
    STAGE 4: Advertiser Approval
    IF representation_relationship.exists
       AND campaign.budget > representation.approval_threshold:
        ADD stage: Advertiser Approval
        WAIT FOR: Advertiser to approve

    IF no stages added:
        CAMPAIGN: Auto-approved
```

### Approval Actions

| Stage | Available Actions | Who Can Act |
|-------|-------------------|-------------|
| Agency Acceptance | Approve, Request Changes, Decline | Agency Account Director |
| Platform Review | Approve, Request Changes, Decline | Internal Admin, Account Manager |
| Media Owner Approval | Approve, Request Changes, Decline | Media Owner Creative Approver |
| Advertiser Approval | Approve, Request Changes, Decline | Advertiser Brand Manager |

---

## Fee Visibility

Different parties see different cost breakdowns based on their relationships.

### Fee Types

| Fee Type | Created By | Default Visibility |
|----------|------------|-------------------|
| Platform Fee | MW (via contract) | MW Internal only |
| Media Cost | System (from booking) | All parties |
| Agency Fee | Agency | Agency + Advertiser (if relationship permits) |
| Custom Fee | Any party | Configurable per fee |

### Custom Fee Visibility Configuration

When creating a custom fee:

| Field | Options | Effect |
|-------|---------|--------|
| Show to Agency | Yes/No | Agency sees this fee in cost breakdown |
| Show to Advertiser | Yes/No | Advertiser sees this fee in cost breakdown |
| Show to Media Owner | Yes/No | Media Owner sees this fee |

**System Behavior:**
```
WHEN rendering cost breakdown for user:
    FOR EACH fee in campaign:
        IF fee.visibility[user.company.type] == true:
            INCLUDE fee in breakdown
        ELSE:
            EXCLUDE fee (no line item shown)
    
    TOTAL: Sum of visible fees only
    HIDDEN FEES: Not in total shown to user
```

---

## Audit Logging

All significant actions are logged.

### Logged Events

| Category | Events Logged |
|----------|---------------|
| Authentication | Login, logout, password change, failed attempts |
| Company | Create, edit, deactivate, contract changes |
| User | Invite, edit, deactivate, role change |
| Campaign | Create, edit, status change, approval actions |
| Inventory | Create, edit, rate changes, availability changes |
| Relationship | Create, modify, revoke |
| Tenant Switch | Context switch, actions in switched context |
| Support Access | Request, grant, revoke, actions taken |

### Audit Log Entry Structure

| Field | Description |
|-------|-------------|
| Timestamp | When action occurred |
| User | Who performed action |
| Acting As | If in switched context, which company |
| Action | What was done |
| Entity | What was affected |
| Before | Previous state (for edits) |
| After | New state (for edits) |
| IP Address | Request origin |
| Access Type | Normal, Support, Managed Service |

### Log Retention

| Access Type | Retention Period |
|-------------|------------------|
| Normal actions | 2 years |
| Support access actions | 7 years |
| Financial actions | 7 years |
| Security events | 7 years |

---

## Industry Comparison

This section summarizes how other platforms handle similar access control requirements.

### Google Campaign Manager 360

**Model:** Account → Subaccount → Advertiser hierarchy

**Key Patterns:**
- User profiles scoped to accounts
- Subaccounts provide tenant isolation within single account
- Filters on user profiles limit visibility to specific advertisers

**What MW Adopted:** Optional isolation layers through parent-child relationships

**What MW Extended:** Dynamic feature flags at field level instead of static role permissions

### The Trade Desk

**Model:** Partner Seat → Advertiser Seat hierarchy

**Key Patterns:**
- Partner-level access propagates to all advertisers
- Advertiser-level access is restricted
- Deals used for cross-entity access

**What MW Adopted:** Clear separation between company-wide and relationship-specific permissions

**What MW Extended:** Relationships govern all interactions, not just transactions

### Meta Business Manager

**Model:** Business → Assets with granular permissions

**Key Patterns:**
- Each asset (Page, Ad Account, Pixel) has independent permission grants
- Partners receive access to specific assets only
- Revocation is immediate

**What MW Adopted:** Inventory-level relationship scopes

**What MW Extended:** Custom permission combinations beyond View/Manage

### Vistar Media

**Model:** Separate SSP/DSP credentials

**Key Patterns:**
- Media owners authenticate to SSP endpoints
- Buyers authenticate to DSP endpoints
- Different credential types for different access

**What MW Adopted:** Company type determines default capabilities

**What MW Extended:** Unified identity with context switching for multi-role users

---

## Appendix A: Feature Inventory

The complete feature inventory is maintained in a separate spreadsheet with function mappings:

**File:** [MW-Planner-Feature-Inventory.xlsx](./MW-Planner-Feature-Inventory.xlsx)

**Structure:**

| Column | Description |
|--------|-------------|
| Level | 0-3 (Module → Control) |
| Hierarchy ID | Unique identifier (e.g., 3.2.2.1) |
| Feature Name | Display name |
| Description | What this feature does |
| Location | URL/route where feature appears |
| Default Functions | Which functions have this enabled by default |
| Screenshot Reference | Link to screenshot |

**Maintenance Process:**

1. When new feature added to product, add row to spreadsheet
2. Assign default function mappings
3. Internal Admin updates function-feature mappings in Account Management
4. Changes propagate to all users with those functions

---

## Appendix B: Decision Logic Reference

### Can User Switch Tenant?

```
CAN_SWITCH_TENANT(user, target_company):
    
    # Direct membership
    IF user.memberships.includes(target_company):
        RETURN true
    
    # Managed service relationship
    IF user.company.type == "Internal":
        relationship = getRelationship(user.company, target_company)
        IF relationship.tenant_switching == true:
            RETURN true
    
    # Parent-child relationship
    IF user.company.isParentOf(target_company):
        IF user.company.parent_view_access == true:
            RETURN true
    
    # Support access
    IF user.role == "Support":
        IF activeSupportAccess(user, target_company):
            RETURN true
    
    RETURN false
```

### What Can User See in Switched Context?

```
GET_VISIBLE_DATA(user, target_company, data_type):
    
    relationship = getRelationship(user.company, target_company)
    
    IF relationship.view_access == "Full":
        RETURN all_data
    
    IF relationship.view_access == "Partial":
        RETURN data WHERE field IN relationship.visible_fields
    
    IF relationship.view_access == "None":
        # Should not have switched—return empty
        RETURN empty
```

### Can User Create Campaign?

```
CAN_CREATE_CAMPAIGN(user):
    
    # Check feature first
    IF NOT user.features.includes("create_campaign"):
        RETURN false
    
    # Check company type
    IF user.company.type IN ["Agency", "Advertiser"]:
        RETURN true
    
    IF user.company.type == "Media Owner":
        IF user.company.contract.direct_sales_enabled:
            RETURN true
    
    IF user.company.type == "Internal":
        RETURN true
    
    RETURN false
```

---

## Appendix C: Glossary

| Term | Definition |
|------|------------|
| Organization | A company registered in the Account Management platform |
| Tenant | Same as Organization; the context a user operates within |
| Relationship | Configuration defining what one company can do with another's data |
| Function | Fixed category (e.g., Ad Ops) that maps to default features |
| Role | Custom name assigned to users, maps to a function |
| Feature | Individual UI element or capability that can be enabled/disabled |
| Market Access | Countries where a company can operate |
| Tenant Switching | Ability to change which company context you're operating in |
| Platform Fee | MW's revenue percentage, hidden from clients |
| Support Access | Time-limited access granted to Internal users for troubleshooting |
