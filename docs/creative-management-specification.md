# Creative Management - Product Specification

## Document Purpose

This document describes the **intended design and functionality** of the Creative Management system in MW Planner. As a microservice architecture, it serves as both an independent platform and an integrated component within MW Planner, supporting campaign creative workflows across multiple platforms. This specification covers the two-tier approval system, content hub, creative assignment workflows, transcoding capabilities, and all business rules.

## Implementation Status

### Currently Implemented ✅

**Note**: Current implementation includes UI components and database schema with **mock data for demonstration**. Backend services for approval workflows, transcoding, and assignment validation are not yet implemented.

- **Creative Assets Database Schema**: `creative_assets` table with metadata, dimensions, file URLs, and tenant support
- **Content Hub (Creatives Page)**: UI for uploading, organizing, and managing creative assets (uses mock data)
- **Folder Organization**: UI for manual folder creation and creative categorization (frontend only)
- **Creative Assignment Page**: Drag-and-drop interface for assigning creatives to campaign inventories (simulated assignment)
- **Transcoding UI**: Dialog interface for selecting formats, resolutions, and quality presets (simulated progress)
- **Preview System**: Visual preview of images, videos, and HTML creatives (frontend rendering)
- **Tier 1 Status Badges**: UI indicators for Processing, Accepted, Inadequate states (static display)
- **Creative Asset APIs**: Backend routes for creating, reading, and updating creative assets (GET, POST, PATCH)
- **Multi-format Support**: Upload and storage of images, videos, HTML5, and native ad formats

### Planned for Implementation 🔄

The following features are documented in this specification but require engineering implementation:

- **Microservice API Architecture**: Separate service URL with token-based authentication for cross-platform access
- **Tier 1 Approval Workflow**: Processing → Accepted/Inadequate state management with role-based permissions
- **Tier 2 Approval Workflow**: Campaign-specific Pending → Approved/Rejected flow after creative assignment
- **Inadequate Creative Blocking**: Prevent assignment of inadequate creatives to campaigns
- **Archive Status**: Support for archiving creatives in both tier 1 and tier 2 workflows
- **Transcoding Job Processing**: Actual video/image format conversion with quality presets and progress tracking
- **Variant Management**: Auto-generation of new creative IDs for transcoded versions
- **Inventory Spec Validation**: Real-time validation of creative specs against inventory requirements during assignment
- **Grouped Assignment**: Assign creatives to multiple inventories by resolution, duration, or custom groupings
- **Schedule-level Assignment**: Assign creatives to specific schedules within inventories
- **Conditional Creative Rules**: Map creatives to weather conditions, signals, or other triggers
- **Campaign-specific Creative Approval**: Track Tier 2 approval status per campaign-inventory assignment
- **Permission-based Actions**: Role-based access control for upload, approval, assignment, and archival

### Technical Debt

- Current implementation uses mock data for demonstration purposes
- No actual transcoding backend service exists (simulated progress only)
- Tier 1 and Tier 2 approval workflows are UI-only without backend state management
- Creative assignment does not validate against inventory specifications
- No microservice API endpoint for external platform access
- Archive functionality not implemented

---

## Overview

**Creative Management** is a comprehensive system for managing advertising creative assets throughout their lifecycle—from upload and internal approval to campaign assignment and media owner approval. It operates as both a standalone microservice and an integrated component of MW Planner.

### Key Concepts

- **Content Hub**: Central repository where users upload, organize, and manage all creative assets
- **Tier 1 Approval**: Internal company-level approval before a creative can be used (Processing → Accepted/Inadequate)
- **Tier 2 Approval**: Media owner approval after a creative is assigned to a campaign (Pending → Approved/Rejected)
- **Creative Assignment**: Process of mapping approved creatives to specific campaign inventories and schedules
- **Transcoding**: Converting creative assets to different formats, resolutions, or quality levels
- **Microservice Architecture**: Independent service accessible via API by any platform with valid authentication

## Purpose and Business Value

Creative Management serves multiple critical business functions:

1. **Quality Control**: Ensure all creative assets meet technical and brand standards before use
2. **Operational Efficiency**: Centralize creative storage and eliminate duplicate uploads across campaigns
3. **Media Owner Compliance**: Validate that assigned creatives meet media owner requirements and receive approval
4. **Cross-platform Accessibility**: Allow multiple platforms (Planner, Publisher Portal, Mobile Apps) to access the same creative library
5. **Campaign Readiness**: Streamline the process of assigning and scheduling creatives for campaign execution
6. **Audit Trail**: Track creative approval history, usage, and modifications across campaigns

---

## Architecture

### Microservice Design

Creative Management operates as an **independent microservice** with the following characteristics:

#### Service Isolation
```
Creative Management Service (Separate URL)
├── Authentication Layer (Token-based)
├── Creative Asset API
├── Approval Workflow API
├── Transcoding Service API
└── File Storage Layer
```

#### Access Model

**Authentication**: 
- Token-based authentication (JWT or similar)
- Tokens issued by Account Management system
- Each request validates token and extracts user permissions

**Authorization**:
- Permissions managed via Account Management
- Role-based access control (RBAC) determines allowed operations
- Tenant context included in all requests for multi-tenant isolation

**API Usage**:
```
Platform A (MW Planner) → Token → Creative Management API
Platform B (Publisher Portal) → Token → Creative Management API
Platform C (Mobile App) → Token → Creative Management API
```

**Capabilities per Platform**:
- Any authenticated user can perform operations their role permits
- Examples: Upload, approve, assign, archive, transcode
- Platform-agnostic: functionality determined by permissions, not originating platform

#### Integration Points

**With MW Planner**:
- Content Hub page calls Creative Management API
- Creative Assignment page fetches approved creatives
- Campaign creation validates creative assignments

**With Other Platforms**:
- External platforms can integrate via documented REST API
- All creative operations available through API endpoints
- Consistent data model across all platforms

---

## Two-Tier Approval System

Creative Management uses a **two-tier approval workflow** to ensure quality control at both the internal company level and the media owner level.

### Tier 1: Internal Approval (Content Hub)

**Purpose**: Internal quality control within the company before creatives can be assigned to campaigns

**Applicable To**: All company types (Media Owner, Media Agency, Reseller, Partner, Internal)

**Workflow States**:

```
Upload → Processing → Accepted
                    → Inadequate (with reason)
                    → Archive
```

#### Status Definitions

| Status | Description | Actions Available | Assignment Allowed |
|--------|-------------|-------------------|-------------------|
| **Processing** | Creative uploaded and awaiting internal review | Preview, Edit metadata, Transcode | ❌ No |
| **Accepted** | Passed internal quality check, ready for use | Preview, Assign, Transcode, Archive | ✅ Yes |
| **Inadequate** | Failed internal review with specific reason | Preview, Re-upload, Archive | ❌ No |
| **Archive** | Removed from active use but retained for records | Preview only | ❌ No |

#### Approval Process

1. **Upload**: User uploads creative asset to Content Hub
2. **Auto-status**: Creative automatically set to "Processing"
3. **Review**: Internal manager or user with approval permission reviews creative
4. **Decision**: 
   - **Accept**: Creative becomes available for campaign assignment
   - **Mark Inadequate**: Creative blocked with specific reason (e.g., "Low resolution images and unclear call-to-action button")
5. **Notification**: Uploader notified of approval decision

#### Who Can Approve (Tier 1)?

- **Internal Managers**: Users with manager role within the company
- **Self-approval**: If uploader has manager permission, they can self-approve
- **Permission-based**: Any user with "creative_approval_tier1" permission in Account Management

#### Inadequate Creative Handling

**Key Rule**: Inadequate creatives **cannot be assigned** to campaigns

- Creatives marked "Inadequate" are hidden from assignment interfaces
- Assignment UI filters show only "Accepted" creatives by default
- Re-upload required: User must upload a corrected version (new ID generated)
- Original inadequate version remains in system for audit trail

### Tier 2: Media Owner Approval (Post-Assignment)

**Purpose**: Media owner validates that assigned creative meets their technical specifications and content policies

**Applicable To**: Campaign-specific assignments after creatives are assigned to inventories

**Workflow States**:

```
Assignment → Pending → Approved
                     → Rejected (with reason)
                     → Archive
```

#### Status Definitions (Tier 2)

| Status | Description | Visibility | Campaign Impact |
|--------|-------------|------------|----------------|
| **Pending** | Creative assigned but awaiting media owner approval | All stakeholders | Campaign cannot go live |
| **Approved** | Media owner confirmed creative meets requirements | All stakeholders | Campaign ready for execution |
| **Rejected** | Media owner rejected creative with specific reason | All stakeholders | Must reassign different creative |
| **Archive** | Creative assignment archived (campaign-specific) | Limited | Slot requires new creative |

#### Approval Process

1. **Assignment**: Agency/advertiser assigns accepted creative to campaign inventory
2. **Auto-status**: Assignment set to "Pending" (Tier 2)
3. **Notification**: Media owner notified of pending creative for review
4. **Media Owner Review**: Media owner user reviews creative in context of their inventory
5. **Decision**:
   - **Approve**: Creative cleared for playback on that inventory
   - **Reject**: Creative rejected with reason (e.g., "Resolution does not match screen specifications")
6. **Notification**: Campaign owner notified of approval decision

#### Who Can Approve (Tier 2)?

- **Media Owner Users**: Users associated with the media owner company that owns the inventory
- **Permission-based**: Users with "creative_approval_tier2" permission for the specific media owner tenant

#### Campaign and Inventory Specificity

**Key Rule**: Tier 2 approval is **always campaign and inventory specific**

- Same creative may be:
  - Approved for Campaign A, Inventory X
  - Rejected for Campaign B, Inventory Y
  - Pending for Campaign C, Inventory Z
- Each assignment has independent Tier 2 approval status
- Creative can have different Tier 2 states across multiple campaigns simultaneously

#### Rejected Creative Handling

- Assignment marked "Rejected" with specific reason
- Campaign owner must either:
  - **Reassign**: Select a different accepted creative
  - **Modify & Re-upload**: Upload corrected version (generates new creative ID)
- Original rejected assignment retained for audit trail
- Campaign cannot proceed to "Live" status with rejected creatives

---

## Content Hub (Creatives Page)

The **Content Hub** is the central interface for managing all creative assets. It provides upload, organization, preview, transcoding, and Tier 1 approval management.

### Core Features

#### 1. Creative Upload

**Supported Formats**:
- **Images**: JPG, PNG, GIF, WebP
- **Videos**: MP4, MOV, WebM
- **HTML5**: HTML, CSS, JS packages
- **Native Ads**: JSON ad specifications

**Upload Interface**:
- Drag-and-drop zone for bulk uploads
- File browser with multi-select
- Progress tracking with percentage complete
- Concurrent uploads supported
- Max file size: 100MB per file (configurable)

**Metadata Capture**:
- Name (auto-filled from filename, editable)
- Type (auto-detected: Image, Video, HTML5, Native)
- Format (auto-detected: MP4, JPG, etc.)
- Dimensions (auto-extracted: 1920x1080, 728x90, etc.)
- Duration (auto-detected for videos)
- Tags (user-assigned for searchability)
- Folder assignment (user-selected)

**Validation**:
- No format/resolution restrictions at upload time
- Validation occurs during assignment to campaign inventories
- System stores creatives of any specification for flexibility

#### 2. Folder Organization

**Purpose**: Help users organize creatives into logical groups

**Features**:
- Manual folder creation
- Drag-and-drop creatives into folders
- Nested folder structure support
- Folder-based filtering in list view
- Creatives can belong to one folder at a time
- Special "All Creatives" view shows unfiltered list

**Use Cases**:
- Organize by campaign: "Summer 2025", "Holiday Campaign"
- Organize by type: "Product Videos", "Banner Ads"
- Organize by client: "Client A Assets", "Client B Creatives"

#### 3. Search and Filtering

**Search Capabilities**:
- Text search across creative names
- Tag-based search
- Folder-based filtering
- Multi-criteria filtering

**Filter Options**:
- **Status**: Processing, Accepted, Inadequate, Archive
- **Type**: Image, Video, HTML5, Native
- **Format**: JPG, PNG, MP4, HTML, etc.
- **Folder**: Select specific folder
- **Tags**: Filter by assigned tags
- **Uploaded By**: Filter by user who uploaded
- **Upload Date Range**: Date-based filtering

#### 4. View Modes

**Grid View**:
- Visual thumbnail grid
- Status badge overlay (top-right corner)
- Quick actions dropdown
- Hover to show preview controls

**List View**:
- Tabular layout with key metadata
- Sortable columns (name, date, size, status)
- Inline status indicators
- Bulk selection checkboxes

#### 5. Creative Actions

**Preview**:
- Full-screen preview panel
- Play videos with controls
- Render HTML5 creatives in sandbox
- View native ad JSON structure
- Display metadata and technical specs

**Download**:
- Download original uploaded file
- Batch download multiple creatives (as ZIP)

**Transcode**:
- Open transcoding dialog
- Select target formats and resolutions
- Submit transcoding job
- Track progress

**Edit Metadata**:
- Update name, tags, folder
- Add descriptive notes
- Cannot edit auto-detected specs (resolution, format)

**Archive**:
- Mark creative as archived (Tier 1)
- Remove from active library
- Retain for audit trail
- Can be restored later

**Delete**:
- Permanently remove creative
- Only if not assigned to any campaigns
- Confirmation required

#### 6. Tier 1 Approval Interface

**Status Badge**:
- Color-coded badges indicate current state
  - **Blue** = Processing (new upload)
  - **Green** = Accepted
  - **Red** = Inadequate
  - **Gray** = Archive
- Hover tooltip shows rejection reason for inadequate creatives

**Approval Actions** (for managers):
- **Accept Creative**: Transition from Processing → Accepted
- **Mark Inadequate**: Transition to Inadequate, require reason input
- **Reason Dialog**: Text field for specific feedback (e.g., "Audio quality too low, file codec not supported")

**Bulk Operations**:
- Select multiple creatives
- Apply status change to all selected
- Batch archive/delete

---

## Creative Assignment

**Creative Assignment** is the process of mapping approved creatives to specific campaign inventories and schedules. It occurs within the campaign management workflow.

### Access Point

**Navigation**: `Campaign Details > Actions Menu > Creative Assignment`

**Prerequisites**:
- Campaign must have inventories selected
- Creatives must be in "Accepted" state (Tier 1)

### Assignment Modes

#### 1. Inventory-Level Assignment

**How it works**:
- Drag creative onto specific inventory card
- Creative assigned to **all schedules** within that inventory
- Single creative per inventory (or multiple if rotation enabled)

**Use Case**: Simple campaigns where same creative plays across all dates/times for an inventory

#### 2. Schedule-Level Assignment

**How it works**:
- Expand inventory to show schedule breakdown
- Drag creative onto specific schedule slot
- Different creatives for different time periods

**Use Case**: Campaigns with time-specific messaging (e.g., breakfast creative 6-9am, lunch creative 12-2pm)

#### 3. Grouped Assignment

**Purpose**: Assign one creative to multiple inventories that share specifications

**Grouping Options**:
- **By Resolution**: All 1920x1080 inventories
- **By Duration**: All 10-second slots
- **By Resolution + Duration**: All 1920x1080 @ 10 seconds
- **Custom Selection**: User manually selects multiple inventories

**How it works**:
1. User activates "Group View" mode
2. System automatically groups inventories by selected criteria
3. Display shows: "1920x1080 - 10 seconds (5 inventories)"
4. User drags creative onto group
5. Creative assigned to all inventories in that group

**Use Case**: Efficient assignment when campaign uses standardized creatives across many similar inventories

#### 4. Drag-and-Drop Interface

**Features**:
- Visual drag-and-drop from creative library to inventory slots
- Real-time validation: highlight compatible slots in green, incompatible in red
- Visual feedback: show thumbnail of dragged creative
- Drop confirmation with assignment preview
- Undo/redo assignment changes

**Validation During Drag**:
- **Resolution Match**: Creative dimensions must match inventory requirements
- **Duration Match**: Video length must meet inventory duration constraints
- **Format Support**: Inventory must support creative format (e.g., HTML5 support)

**Visual Indicators**:
- **Compatible**: Green border, "Drop here to assign"
- **Incompatible**: Red border with reason tooltip (e.g., "Resolution mismatch: needs 1280x720")
- **Already Assigned**: Yellow border, "Replace existing creative?"

### Creative Library in Assignment View

**Default Filter**: Show only **compatible creatives**
- Automatically filter creatives matching campaign inventory specs
- Hide creatives with wrong resolution, format, or duration

**Toggle**: "Show All Creatives"
- Display full library including incompatible creatives
- Incompatible creatives shown with warning badge
- Allows user to see all options but prevents invalid assignment

**Search and Filter**:
- Same filters as Content Hub
- Additional filter: "Show only compatible"
- Tag-based filtering for quick access

### Conditional Creative Rules

**Purpose**: Map different creatives to different conditions (weather, signals, triggers)

**How it works**:
1. User assigns creative to inventory
2. Expand "Conditional Rules" section
3. Add rule: "If weather = Sunny → Creative A"
4. Add default rule: "Otherwise → Creative B"

**Supported Conditions**:
- **Weather**: Sunny, Rainy, Cloudy, Snowy
- **Time of Day**: Morning, Afternoon, Evening, Night
- **Day of Week**: Weekday, Weekend, Specific days
- **Custom Signals**: User-defined triggers from external systems

**Visual Representation**:
- Each inventory shows rule chips
- Example: "☀️ Sunny: Summer Beach Ad | ☁️ Default: Generic Ad"

**Fallback Behavior**:
- Always require a "Default" creative for unmatched conditions
- Validation prevents saving without default assignment

### Assignment Summary

**Overview Panel**:
- Total inventories: 15
- Assigned inventories: 12
- Pending assignment: 3
- Creative usage count (how many inventories use each creative)

**Warnings and Alerts**:
- "3 inventories have no creatives assigned"
- "2 creatives are pending Tier 2 approval"
- "1 creative was rejected by media owner - reassignment required"

### Post-Assignment: Tier 2 Approval

**Automatic Workflow**:
1. User completes assignment
2. All assignments automatically set to "Pending" (Tier 2)
3. Notifications sent to media owners for approval
4. Campaign status remains "Pending Approval" until all creatives approved

**Tier 2 Status Tracking in Assignment View**:
- Each assigned creative shows Tier 2 status badge
  - **Yellow**: Pending media owner approval
  - **Green**: Approved by media owner
  - **Red**: Rejected by media owner
- Rejection reason displayed in tooltip
- Quick action: "Reassign Creative" for rejected slots

---

## Transcoding

**Transcoding** converts creative assets to different formats, resolutions, or quality levels to meet varying inventory requirements.

### Purpose

1. **Format Conversion**: Convert MP4 to WebM, JPG to PNG, etc.
2. **Resolution Adjustment**: Resize 4K video to 1080p for standard screens
3. **Quality Optimization**: Reduce file size while maintaining acceptable quality
4. **Batch Creation**: Generate multiple variants from single source creative

### Transcoding Interface

**Access**: Content Hub > Creative Actions > Transcode

**Dialog Components**:

#### 1. Source Creative Info
- Thumbnail preview
- Original specs: Format, Resolution, File Size, Duration
- Current status (must be Accepted to transcode)

#### 2. Target Format Selection
- Checkbox list of available formats
- **For Videos**: MP4, WebM, MOV
- **For Images**: JPG, PNG, WebP, GIF
- Multi-select enabled (create multiple format variants)

#### 3. Target Resolution Selection
- Checkbox list of standard resolutions
- **Common Options**: 1920x1080, 1280x720, 3840x2160, 728x90, 300x250
- Custom resolution input field
- Multi-select enabled (create multiple resolution variants)

#### 4. Quality Presets
- **High**: Maximum quality, larger file size (bitrate: 8 Mbps for video)
- **Medium**: Balanced quality and file size (bitrate: 4 Mbps for video) - **Default**
- **Low**: Reduced quality, smallest file size (bitrate: 2 Mbps for video)

#### 5. Compression Toggle
- Enable/disable additional compression
- Useful for reducing file size for low-bandwidth environments
- Checkbox: "Apply additional compression"

#### 6. Estimated Output
- Projected file sizes for each variant
- Total storage impact
- Estimated processing time

### Transcoding Process

**Workflow**:
1. User selects transcode options (formats, resolutions, quality)
2. Click "Start Transcoding"
3. Job submitted to transcoding queue
4. Progress bar shows overall completion
5. Individual variant progress displayed
6. On completion, variants added to Content Hub as separate creatives

**Job Tracking**:
- Real-time progress updates
- Status: Queued → Processing → Completed / Failed
- Percentage complete per variant
- Estimated time remaining
- Ability to cancel in-progress jobs

**Output Handling**:
- Each transcoded variant generates **new creative ID**
- Original creative unchanged
- Variants automatically tagged with: `transcoded`, `variant-of-{originalID}`
- Variants inherit folder and most metadata from original
- Variants set to "Processing" status (Tier 1 approval required)

### Transcoding Limitations

**Concurrent Jobs**:
- **One job at a time per user**
- Queue additional jobs if one already in progress
- Prevents system overload

**Format Support**:
- Limited to platform-supported codecs
- Some formats may not be available for all source types
- Error handling for unsupported conversions

**Quality Constraints**:
- Cannot upscale resolution (e.g., 720p → 1080p may result in poor quality)
- System warns if upscaling attempted
- Recommendation: Always start with highest quality source

---

## Data Model

### Creative Asset Schema

```typescript
{
  id: number,                    // Primary key
  name: string,                  // User-defined name
  type: string,                  // "image", "video", "html5", "native"
  format: string,                // "jpg", "png", "mp4", "html", etc.
  dimensions: {                  // Auto-detected
    width: number,
    height: number
  },
  fileUrl: string,               // Storage URL
  thumbnailUrl: string,          // Preview thumbnail URL
  fileSize: number,              // In bytes
  duration: number,              // For videos, in seconds
  userId: number,                // Uploader
  companyId: number,             // Tenant context
  tags: string[],                // User-assigned tags
  folder: string,                // Folder path
  tier1Status: string,           // "processing", "accepted", "inadequate", "archive"
  tier1RejectionReason: string,  // Only if inadequate
  tier1ApprovedBy: number,       // User ID of approver
  tier1ApprovedAt: timestamp,    // Approval timestamp
  isActive: boolean,             // Soft delete flag
  createdAt: timestamp,
  updatedAt: timestamp,
  
  // Variant tracking
  isVariant: boolean,            // True if transcoded variant
  parentCreativeId: number,      // If variant, reference to original
  transcodeJobId: number         // Reference to transcoding job
}
```

### Creative Assignment Schema

```typescript
{
  id: number,                    // Primary key
  campaignId: number,            // Campaign reference
  inventoryId: number,           // Inventory reference
  scheduleId: number,            // Optional: specific schedule slot
  creativeId: number,            // Creative reference
  
  // Tier 2 Approval
  tier2Status: string,           // "pending", "approved", "rejected", "archive"
  tier2RejectionReason: string,  // Only if rejected
  tier2ApprovedBy: number,       // Media owner user ID
  tier2ApprovedAt: timestamp,
  
  // Conditional Rules
  conditionalRules: {
    conditions: {
      type: string,              // "weather", "time", "signal"
      value: string              // "sunny", "morning", etc.
    }[],
    isDefault: boolean           // Fallback creative
  },
  
  assignedBy: number,            // User who assigned
  assignedAt: timestamp,
  updatedAt: timestamp
}
```

### Transcoding Job Schema

```typescript
{
  id: number,                    // Job ID
  sourceCreativeId: number,      // Original creative
  userId: number,                // User who initiated
  status: string,                // "queued", "processing", "completed", "failed"
  progress: number,              // 0-100 percentage
  
  // Job Configuration
  options: {
    targetFormats: string[],     // ["mp4", "webm"]
    targetResolutions: string[], // ["1920x1080", "1280x720"]
    quality: string,             // "high", "medium", "low"
    compress: boolean
  },
  
  // Output
  variants: number[],            // Array of created creative IDs
  errorMessage: string,          // If failed
  
  startedAt: timestamp,
  completedAt: timestamp,
  estimatedDuration: number      // Seconds
}
```

---

## API Endpoints

### Creative Asset Endpoints

**Base URL**: `https://creative-service.mwplanner.com/api/v1`

**Authentication**: All requests require valid JWT token in `Authorization: Bearer {token}` header

#### `GET /creatives`
**Purpose**: Fetch all creatives for authenticated user's company

**Query Parameters**:
- `status`: Filter by tier1Status (processing, accepted, inadequate, archive)
- `type`: Filter by type (image, video, html5, native)
- `folder`: Filter by folder path
- `tags`: Comma-separated tag list
- `search`: Text search on name and tags

**Response**:
```json
{
  "creatives": [
    {
      "id": 123,
      "name": "Summer Campaign Banner",
      "type": "image",
      "format": "jpg",
      "dimensions": { "width": 1920, "height": 1080 },
      "fileUrl": "https://cdn.mwplanner.com/creatives/123.jpg",
      "thumbnailUrl": "https://cdn.mwplanner.com/thumbnails/123_thumb.jpg",
      "fileSize": 2457600,
      "tier1Status": "accepted",
      "tags": ["summer", "banner", "retail"],
      "folder": "Summer 2025",
      "createdAt": "2025-01-15T10:30:00Z"
    }
  ],
  "total": 45
}
```

#### `POST /creatives`
**Purpose**: Upload new creative

**Request**: Multipart form data
- `file`: Creative file (required)
- `name`: Creative name (optional, defaults to filename)
- `tags`: JSON array of tags
- `folder`: Folder path

**Response**:
```json
{
  "id": 124,
  "name": "New Banner",
  "status": "processing",
  "uploadUrl": "https://cdn.mwplanner.com/creatives/124.jpg"
}
```

#### `PATCH /creatives/:id`
**Purpose**: Update creative metadata or status

**Request**:
```json
{
  "name": "Updated Name",
  "tags": ["new", "tag"],
  "folder": "New Folder",
  "tier1Status": "accepted",
  "tier1RejectionReason": "Reason if inadequate"
}
```

**Response**: Updated creative object

#### `DELETE /creatives/:id`
**Purpose**: Delete creative (only if not assigned to campaigns)

**Response**: `204 No Content`

#### `GET /creatives/:id/assignments`
**Purpose**: Get all campaign assignments for a creative

**Response**:
```json
{
  "assignments": [
    {
      "campaignId": 45,
      "campaignName": "Fall Brand Awareness",
      "inventoryId": 12,
      "inventoryName": "Times Square Billboard",
      "tier2Status": "approved"
    }
  ]
}
```

### Transcoding Endpoints

#### `POST /transcode`
**Purpose**: Start transcoding job

**Request**:
```json
{
  "creativeId": 123,
  "options": {
    "targetFormats": ["mp4", "webm"],
    "targetResolutions": ["1920x1080", "1280x720"],
    "quality": "medium",
    "compress": true
  }
}
```

**Response**:
```json
{
  "jobId": 789,
  "status": "queued",
  "estimatedDuration": 120
}
```

#### `GET /transcode/:jobId`
**Purpose**: Get transcoding job status

**Response**:
```json
{
  "jobId": 789,
  "status": "processing",
  "progress": 65,
  "variants": [124, 125],
  "startedAt": "2025-01-15T11:00:00Z"
}
```

### Assignment Endpoints

#### `POST /assignments`
**Purpose**: Assign creative to campaign inventory

**Request**:
```json
{
  "campaignId": 45,
  "inventoryId": 12,
  "scheduleId": null,
  "creativeId": 123,
  "conditionalRules": {
    "conditions": [
      { "type": "weather", "value": "sunny" }
    ],
    "isDefault": false
  }
}
```

**Response**:
```json
{
  "assignmentId": 456,
  "tier2Status": "pending",
  "validationErrors": []
}
```

#### `GET /assignments`
**Purpose**: Get all assignments for a campaign

**Query Parameters**:
- `campaignId`: Required
- `tier2Status`: Filter by approval status

**Response**: Array of assignment objects

#### `PATCH /assignments/:id`
**Purpose**: Update Tier 2 approval status (media owners only)

**Request**:
```json
{
  "tier2Status": "approved",
  "tier2RejectionReason": null
}
```

**Response**: Updated assignment object

---

## User Roles and Permissions

### Permission Model

**Creative Upload**:
- All authenticated users can upload to their company's library
- No special permission required

**Tier 1 Approval**:
- **Requires**: `creative_approval_tier1` permission
- **Typically Granted To**: Managers, Creative Directors
- **Self-approval**: Allowed if uploader has permission

**Tier 2 Approval**:
- **Requires**: `creative_approval_tier2` permission AND association with media owner company
- **Typically Granted To**: Media Owner account managers
- **Scope**: Only for inventories owned by their company

**Creative Assignment**:
- **Requires**: Permission to edit campaign
- **Typically Granted To**: Campaign creators, Account Managers

**Transcoding**:
- All users with access to creatives can initiate transcoding
- Job queue managed by system to prevent overload

**Archive/Delete**:
- **Archive**: Any user can archive their own uploads; managers can archive any creative
- **Delete**: Only if creative not assigned; requires manager permission

### Company Type Access

| Company Type | Can Upload | Tier 1 Approval | Tier 2 Approval | Can Assign to Campaigns |
|--------------|-----------|----------------|----------------|------------------------|
| **Media Agency** | ✅ | ✅ (internal) | ❌ | ✅ |
| **Advertiser** | ✅ | ✅ (internal) | ❌ | ✅ |
| **Media Owner** | ✅ | ✅ (internal) | ✅ (for own inventories) | ✅ |
| **Reseller** | ✅ | ✅ (internal) | ❌ | ✅ |
| **Internal** | ✅ | ✅ (internal) | ✅ (system-wide) | ✅ |

---

## Business Rules

### Creative Upload Rules

1. **No Format Restrictions**: System accepts all formats; validation happens during assignment
2. **File Size Limit**: 100MB per file (configurable)
3. **Automatic Metadata**: System auto-detects type, format, dimensions, duration
4. **Default Status**: All new uploads set to "Processing" (Tier 1)
5. **Tenant Isolation**: Creatives scoped to uploader's company; not visible to other tenants

### Tier 1 Approval Rules

1. **Inadequate Blocking**: Creatives marked "Inadequate" cannot be assigned to campaigns
2. **Rejection Reason Required**: Cannot mark inadequate without providing specific reason
3. **Re-upload for Inadequate**: Users must upload corrected version; original retained for audit
4. **Self-Approval Allowed**: If uploader has approval permission, can self-approve
5. **Archive Anytime**: Creatives can be archived at any Tier 1 status
6. **Archived Not Deletable**: Archived creatives retained; cannot be permanently deleted if assigned to campaigns

### Tier 2 Approval Rules

1. **Campaign-Specific**: Tier 2 status is unique per campaign-inventory assignment
2. **Media Owner Only**: Only media owner users can approve/reject for their inventories
3. **Auto-Pending**: All assignments default to "Pending" Tier 2 status
4. **Rejection Reason Required**: Cannot reject without specific feedback
5. **Reassignment on Rejection**: Rejected assignments require new creative selection
6. **Campaign Blocker**: Campaign cannot go "Live" with pending or rejected creatives
7. **Archive Assignment**: Can archive specific campaign assignments without affecting creative's Tier 1 status

### Assignment Validation Rules

1. **Resolution Match**: Creative dimensions must match inventory specifications
2. **Format Support**: Inventory must support creative format (e.g., some inventories don't support HTML5)
3. **Duration Constraints**: Video duration must fit inventory slot duration
4. **Tier 1 Prerequisite**: Only "Accepted" Tier 1 creatives can be assigned
5. **Default Creative Required**: If using conditional rules, must specify default fallback creative
6. **One Creative Per Slot**: Each inventory-schedule slot can have one primary creative (or rotation set)

### Transcoding Rules

1. **One Job Per User**: Users can only run one transcoding job at a time
2. **New IDs for Variants**: Each transcoded variant receives new unique ID
3. **Variant Tagging**: Auto-tag variants with `transcoded` and `variant-of-{originalID}`
4. **Approval Reset**: Transcoded variants set to "Processing"; require Tier 1 approval
5. **Quality Warning**: System warns if upscaling resolution (e.g., 720p → 1080p)
6. **Source Unchanged**: Original creative remains intact after transcoding

### Folder Organization Rules

1. **Manual Creation**: Users manually create folders; no auto-folders
2. **One Folder Per Creative**: Creative belongs to single folder
3. **Manual Assignment**: Drag-drop or dropdown selection to assign folder
4. **No Nested Hierarchy Enforcement**: System supports nested paths but doesn't enforce structure
5. **Folder Deletion**: Deleting folder moves creatives to "Uncategorized"

### Archive and Deletion Rules

1. **Archive Status**: Available for both Tier 1 (creative-level) and Tier 2 (assignment-level)
2. **No Expiry**: Archived creatives do not auto-delete; retained indefinitely for audit
3. **Restore Capability**: Archived creatives can be restored to previous status
4. **Delete Restriction**: Cannot delete creative if assigned to any campaign (past or present)
5. **Soft Delete Preferred**: System uses `isActive: false` flag rather than hard deletion

---

## User Workflows

### Workflow 1: Upload and Approve Creative (Tier 1)

**Actor**: Creative Designer (uploader) and Marketing Manager (approver)

1. Designer navigates to **Content Hub**
2. Clicks **Upload** button or drag-drops files
3. Selects files from computer (e.g., 5 banner images)
4. System uploads files with progress indicator
5. Designer adds tags: `summer`, `retail`, `sale`
6. Designer assigns to folder: `Summer 2025 Campaign`
7. System auto-sets status to **Processing**
8. Designer clicks **Save**

9. Marketing Manager receives notification: "5 new creatives pending approval"
10. Manager navigates to **Content Hub**
11. Filters by status: **Processing**
12. Reviews each creative:
    - Creative A: Good quality → Click **Accept**
    - Creative B: Low resolution → Click **Mark Inadequate** → Enter reason: "Resolution too low for digital screens (needs minimum 1920x1080)"
13. System updates statuses
14. Designer receives notification with feedback

**Result**: 4 creatives accepted and available for campaign assignment; 1 inadequate and blocked from use

---

### Workflow 2: Assign Creatives to Campaign

**Actor**: Campaign Manager

1. Campaign Manager creates campaign: "Fall Brand Awareness"
2. Selects 10 inventories with mixed specifications:
   - 6 inventories: 1920x1080, 10 seconds
   - 4 inventories: 1280x720, 15 seconds
3. Completes campaign setup
4. From campaign details page, clicks **Actions > Creative Assignment**

5. Assignment page loads with:
   - Left panel: Campaign inventories grouped by resolution/duration
   - Right panel: Approved creatives from Content Hub
6. Default filter: "Show only compatible creatives"
7. System highlights compatible creatives in library

8. Campaign Manager drags "Summer Banner A" onto "1920x1080 - 10 sec (6 inventories)" group
9. System validates: ✅ Resolution match, ✅ Format supported
10. Confirmation dialog: "Assign to all 6 inventories in this group?"
11. Manager confirms
12. Creative assigned to all 6 inventories; status set to **Pending** (Tier 2)

13. Manager drags "Product Video B" onto "1280x720 - 15 sec (4 inventories)" group
14. System validates: ✅ Resolution match, ✅ Duration fits
15. Creative assigned to all 4 inventories

16. Assignment summary shows:
    - 10/10 inventories assigned
    - All assignments: **Pending** Tier 2 approval
17. Manager clicks **Save Assignment**
18. System sends notifications to media owners for approval

**Result**: All campaign inventories have assigned creatives awaiting media owner approval

---

### Workflow 3: Media Owner Approves/Rejects Creative (Tier 2)

**Actor**: Media Owner Account Manager

1. Media Owner receives email: "New creative assignment pending your approval"
2. Clicks link → Navigates to **Pending Approvals** dashboard
3. Sees list of pending creative assignments:
   - Campaign: "Fall Brand Awareness"
   - Inventory: "Times Square Billboard"
   - Creative: "Summer Banner A"
4. Clicks **Review**

5. Preview panel opens showing:
   - Creative preview (full-screen render)
   - Creative specs: 1920x1080, JPG, 2.4 MB
   - Inventory specs: 1920x1080, supports JPG
6. Media Owner reviews creative content and technical quality

**Decision 1: Approve**
7a. Creative meets requirements
8a. Clicks **Approve**
9a. Status updated to **Approved** (Tier 2)
10a. Campaign owner notified: "Creative approved for Times Square Billboard"

**Decision 2: Reject**
7b. Creative has issue (e.g., branding conflict, low quality)
8b. Clicks **Reject**
9b. Modal prompts: "Enter rejection reason"
10b. Enters: "Creative contains competitor branding in background"
11b. Clicks **Confirm Rejection**
12b. Status updated to **Rejected** (Tier 2)
13b. Campaign owner notified with rejection reason

14. Campaign Manager sees rejection
15. Selects different creative: "Summer Banner C"
16. Reassigns to Times Square Billboard
17. New assignment set to **Pending** (Tier 2)
18. Media Owner reviews and approves

**Result**: All assignments approved; campaign ready to go live

---

### Workflow 4: Transcode Creative for Multiple Resolutions

**Actor**: Creative Designer

1. Designer has source video: "Product Demo" (4K resolution, MP4, 100 MB)
2. Campaign requires creatives in 1920x1080 and 1280x720
3. Designer navigates to **Content Hub**
4. Locates "Product Demo" creative
5. Clicks **Actions > Transcode**

6. Transcode dialog opens:
   - Source: Product Demo (3840x2160, MP4, 100 MB, 30 seconds)
7. Designer selects:
   - **Target Formats**: MP4 (keep same), WebM (add for web compatibility)
   - **Target Resolutions**: 1920x1080, 1280x720
   - **Quality**: Medium
   - **Compression**: Enabled
8. System calculates estimated output:
   - Variant 1: 1920x1080 MP4, ~20 MB
   - Variant 2: 1920x1080 WebM, ~18 MB
   - Variant 3: 1280x720 MP4, ~12 MB
   - Variant 4: 1280x720 WebM, ~10 MB
   - **Total**: 4 variants, ~60 MB, Est. time: 5 minutes

9. Designer clicks **Start Transcoding**
10. Job submitted; progress bar appears
11. Status updates in real-time: 25% → 50% → 75% → 100%
12. After 5 minutes, job completes

13. System creates 4 new creative assets:
    - "Product Demo - 1920x1080 MP4" (ID: 125)
    - "Product Demo - 1920x1080 WebM" (ID: 126)
    - "Product Demo - 1280x720 MP4" (ID: 127)
    - "Product Demo - 1280x720 WebM" (ID: 128)
14. All variants auto-tagged: `transcoded`, `variant-of-124`
15. All variants set to **Processing** status (Tier 1)
16. Designer receives notification: "Transcoding complete - 4 variants created"

17. Marketing Manager reviews and approves all 4 variants
18. Designer can now assign appropriate resolutions to different campaign inventories

**Result**: Single source video converted to 4 usable variants for different inventory specifications

---

## Integration with MW Planner

### Campaign Creation Workflow

**Integration Points**:

1. **Inventory Selection Phase**:
   - Capture inventory specifications (resolution, format, duration)
   - Store requirements for creative validation

2. **Creative Assignment Phase** (optional during creation):
   - User can assign creatives during campaign setup
   - Or defer assignment until after campaign is created

3. **Campaign Finalization**:
   - Validate all inventories have assigned creatives
   - Check Tier 2 approval status
   - Block "Go Live" if creatives pending/rejected

### Campaign Detail Page

**Creative Status Section**:
- Display creative assignment summary
- Show Tier 2 approval status per inventory
- Action button: **Assign Creatives** (opens assignment page)

**Alerts**:
- Warning: "3 inventories missing creative assignments"
- Warning: "2 creatives pending media owner approval"
- Error: "1 creative rejected - reassignment required"

### Price Management Integration

**No Direct Integration**: Creative Management and Price Management are independent systems

**Indirect Relationship**:
- Both are campaign attributes but managed separately
- Creatives validated by media owners; prices negotiated separately

---

## Multi-Tenant Considerations

### Tenant Isolation

**Creative Library Scoping**:
- Each company (tenant) has isolated creative library
- Users can only view/access creatives belonging to their company
- No cross-tenant creative sharing (unless explicit sharing feature added)

**Tenant Context in API**:
- All API requests include tenant ID (extracted from JWT token)
- Database queries automatically filter by tenant
- Storage paths segregated by tenant: `/creatives/{tenantId}/...`

### White-Label Support

**Branding in Creatives**:
- Tenant branding (logos, colors) can be applied to UI
- Does not affect creative assets themselves (client controls creative content)

**Multi-Company Users**:
- Users associated with multiple companies see combined creative libraries
- UI provides company switcher to filter creatives by tenant context

---

## Export and Reporting

### Creative Usage Report

**Purpose**: Show which creatives are used across campaigns

**Data Included**:
- Creative name, ID, type, format
- Number of campaigns using creative
- Total impressions delivered (if campaign live)
- Tier 2 approval rate (% approved vs. rejected)

**Export Formats**: Excel, CSV

### Approval Audit Trail

**Purpose**: Track approval history for compliance

**Data Included**:
- Creative ID, name
- Tier 1 status changes with timestamp and approver
- Tier 2 status changes per assignment with timestamp and approver
- Rejection reasons

**Export Formats**: Excel, PDF

### Creative Inventory Report

**Purpose**: Overview of creative library

**Data Included**:
- Total creatives by status (Processing, Accepted, Inadequate, Archive)
- Total storage used (GB)
- Breakdown by type (Image, Video, HTML5, Native)
- Breakdown by folder

**Export Formats**: Excel, PDF

---

## Acceptance Criteria

### Content Hub

- [ ] Users can upload creatives via drag-drop or file browser
- [ ] System auto-detects format, resolution, duration, file size
- [ ] Users can organize creatives into folders manually
- [ ] Search and filter by status, type, format, tags, folder
- [ ] Grid and list view modes available
- [ ] Preview displays images, plays videos, renders HTML5 creatives
- [ ] Download individual or batch creatives
- [ ] Managers can mark creatives as Accepted or Inadequate with reasons
- [ ] Status badges display current Tier 1 status with tooltips for inadequate creatives
- [ ] Archive creatives to remove from active library

### Creative Assignment

- [ ] Drag-drop creatives onto inventories or schedules
- [ ] Real-time validation highlights compatible/incompatible drop zones
- [ ] Grouped assignment assigns one creative to multiple inventories by resolution/duration
- [ ] Schedule-level assignment assigns different creatives to different time slots
- [ ] Conditional rules map creatives to weather, time, or signal conditions
- [ ] Assignment summary shows completion status and warnings
- [ ] All assignments default to Pending (Tier 2) status
- [ ] Cannot assign inadequate (Tier 1) creatives

### Tier 2 Approval

- [ ] Media owners receive notifications for pending approvals
- [ ] Media owners can approve or reject assignments with reasons
- [ ] Tier 2 status is campaign and inventory specific
- [ ] Campaign cannot go live with pending or rejected creatives
- [ ] Rejected assignments can be reassigned with different creative

### Transcoding

- [ ] Users can select target formats and resolutions
- [ ] Quality presets (High, Medium, Low) adjust output file size
- [ ] Transcoding jobs show real-time progress
- [ ] Only one job per user at a time
- [ ] Variants receive new creative IDs and auto-tags
- [ ] Variants set to Processing (Tier 1) and require approval

### API

- [ ] All endpoints require valid JWT authentication
- [ ] Endpoints filter data by tenant context
- [ ] GET /creatives returns filtered list with query parameters
- [ ] POST /creatives uploads new creative with metadata
- [ ] PATCH /creatives/:id updates metadata and status
- [ ] DELETE /creatives/:id prevents deletion if assigned to campaigns
- [ ] POST /transcode initiates transcoding job
- [ ] GET /transcode/:jobId returns job status and progress
- [ ] POST /assignments creates creative-inventory assignment
- [ ] PATCH /assignments/:id updates Tier 2 approval status

### Permissions

- [ ] All authenticated users can upload creatives
- [ ] Only users with `creative_approval_tier1` permission can approve/reject (Tier 1)
- [ ] Only media owner users with `creative_approval_tier2` permission can approve/reject for their inventories
- [ ] Tier 1 approvers can self-approve their own uploads if they have permission
- [ ] Users can only delete creatives not assigned to any campaigns

### Multi-Tenant

- [ ] Creative libraries isolated by tenant (company ID)
- [ ] API requests include tenant context from JWT
- [ ] Storage paths segregated by tenant
- [ ] Users can only access creatives belonging to their company

---

## Future Enhancements

### AI-Powered Features

- **Auto-Tagging**: Use computer vision to auto-generate tags from creative content
- **Quality Scoring**: AI assessment of creative quality (resolution, composition, branding consistency)
- **Content Moderation**: Auto-detect inappropriate content before Tier 1 approval

### Advanced Transcoding

- **Adaptive Bitrate**: Generate multiple quality streams for adaptive playback
- **AI Upscaling**: Use AI to improve quality when upscaling resolution
- **Watermarking**: Auto-apply tenant watermarks to creatives

### Collaboration Features

- **Comments**: Allow reviewers to leave feedback on specific creatives
- **Version History**: Track revisions and allow rollback to previous versions
- **Shared Libraries**: Enable cross-tenant creative sharing with permissions

### Analytics

- **Creative Performance**: Track impressions, clicks, conversions per creative across campaigns
- **A/B Testing**: Compare performance of different creatives on same inventory
- **Heatmaps**: Visual engagement analysis for interactive creatives

### Automation

- **Auto-Assignment**: AI suggests best creative for each inventory based on specs and performance history
- **Smart Expiry**: Auto-archive creatives not used in X days
- **Scheduled Publishing**: Set future dates for creative availability

---

## Appendix A: API Reference

### Authentication & Authorization

**Authentication Method**: JWT (JSON Web Token)

**Header Format**:
```
Authorization: Bearer {JWT_TOKEN}
```

**Token Payload** (decoded):
```json
{
  "userId": 123,
  "companyId": 45,
  "permissions": [
    "creative_upload",
    "creative_approval_tier1",
    "creative_approval_tier2",
    "creative_delete"
  ],
  "exp": 1738368000
}
```

**Permission Scopes**:

| Permission | Description | Grants Access To |
|------------|-------------|-----------------|
| `creative_upload` | Upload and manage own creatives | POST /creatives, PATCH /creatives/:id (own) |
| `creative_approval_tier1` | Approve/reject internal creatives | PATCH /creatives/:id (tier1Status) |
| `creative_approval_tier2` | Approve/reject campaign assignments | PATCH /assignments/:id (tier2Status) |
| `creative_delete` | Delete creatives | DELETE /creatives/:id |
| `creative_assign` | Assign creatives to campaigns | POST /assignments |
| `creative_transcode` | Initiate transcoding jobs | POST /transcode |

### API Endpoint Reference

#### GET /api/v1/creatives

**Description**: Retrieve creative assets for authenticated user's company

**Authentication**: Required

**Query Parameters**:
- `status` (string, optional): Filter by tier1Status (`processing`, `accepted`, `inadequate`, `archive`)
- `type` (string, optional): Filter by creative type (`image`, `video`, `html5`, `native`)
- `format` (string, optional): Filter by format (`jpg`, `png`, `mp4`, etc.)
- `folder` (string, optional): Filter by folder path
- `tags` (string, optional): Comma-separated list of tags
- `search` (string, optional): Text search across name and tags
- `page` (integer, optional, default: 1): Page number for pagination
- `limit` (integer, optional, default: 50): Items per page (max: 100)

**Example Request**:
```
GET /api/v1/creatives?status=accepted&type=video&page=1&limit=20
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

**Success Response** (200 OK):
```json
{
  "creatives": [
    {
      "id": 123,
      "name": "Summer Campaign Banner",
      "type": "image",
      "format": "jpg",
      "dimensions": { "width": 1920, "height": 1080 },
      "fileUrl": "https://cdn.mwplanner.com/creatives/123.jpg",
      "thumbnailUrl": "https://cdn.mwplanner.com/thumbnails/123_thumb.jpg",
      "fileSize": 2457600,
      "duration": null,
      "userId": 45,
      "companyId": 12,
      "tags": ["summer", "banner", "retail"],
      "folder": "Summer 2025",
      "tier1Status": "accepted",
      "tier1RejectionReason": null,
      "tier1ApprovedBy": 67,
      "tier1ApprovedAt": "2025-01-20T14:30:00Z",
      "isVariant": false,
      "parentCreativeId": null,
      "createdAt": "2025-01-15T10:30:00Z",
      "updatedAt": "2025-01-20T14:30:00Z"
    }
  ],
  "pagination": {
    "total": 145,
    "page": 1,
    "limit": 20,
    "totalPages": 8
  }
}
```

**Error Responses**:
- `401 Unauthorized`: Missing or invalid JWT token
- `403 Forbidden`: User lacks permission to access creatives
- `500 Internal Server Error`: Server-side error

---

#### POST /api/v1/creatives

**Description**: Upload new creative asset

**Authentication**: Required

**Permissions**: `creative_upload`

**Content-Type**: `multipart/form-data`

**Form Fields**:
- `file` (file, required): Creative file
- `name` (string, optional): Creative name (defaults to filename)
- `tags` (JSON array, optional): Array of tag strings
- `folder` (string, optional): Folder path

**Example Request**:
```
POST /api/v1/creatives
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Content-Type: multipart/form-data

file: [binary data]
name: "Product Demo Video"
tags: ["product", "demo", "video"]
folder: "Product Videos"
```

**Success Response** (201 Created):
```json
{
  "id": 124,
  "name": "Product Demo Video",
  "type": "video",
  "format": "mp4",
  "dimensions": { "width": 1920, "height": 1080 },
  "fileUrl": "https://cdn.mwplanner.com/creatives/124.mp4",
  "thumbnailUrl": "https://cdn.mwplanner.com/thumbnails/124_thumb.jpg",
  "fileSize": 47472640,
  "duration": 30,
  "tier1Status": "processing",
  "createdAt": "2025-01-31T10:15:00Z"
}
```

**Error Responses**:
- `400 Bad Request`: Invalid file format or missing required fields
- `401 Unauthorized`: Missing or invalid JWT
- `403 Forbidden`: User lacks `creative_upload` permission
- `413 Payload Too Large`: File exceeds maximum size (100MB)
- `500 Internal Server Error`: Upload failed

---

#### PATCH /api/v1/creatives/:id

**Description**: Update creative metadata or approval status

**Authentication**: Required

**Permissions**: 
- Own creative metadata: `creative_upload`
- Tier 1 status: `creative_approval_tier1`

**Request Body**:
```json
{
  "name": "Updated Creative Name",
  "tags": ["updated", "tags"],
  "folder": "New Folder Path",
  "tier1Status": "accepted",
  "tier1RejectionReason": "Required if status is 'inadequate'"
}
```

**Success Response** (200 OK):
```json
{
  "id": 123,
  "name": "Updated Creative Name",
  "tier1Status": "accepted",
  "tier1ApprovedBy": 67,
  "tier1ApprovedAt": "2025-01-31T11:20:00Z",
  "updatedAt": "2025-01-31T11:20:00Z"
}
```

**Error Responses**:
- `400 Bad Request`: Invalid status transition or missing rejection reason
- `401 Unauthorized`: Missing or invalid JWT
- `403 Forbidden`: User lacks permission to update this creative
- `404 Not Found`: Creative does not exist
- `500 Internal Server Error`: Update failed

---

#### DELETE /api/v1/creatives/:id

**Description**: Delete creative asset

**Authentication**: Required

**Permissions**: `creative_delete`

**Validation**: Creative must not be assigned to any campaigns

**Success Response** (204 No Content)

**Error Responses**:
- `400 Bad Request`: Creative is assigned to campaigns (cannot delete)
- `401 Unauthorized`: Missing or invalid JWT
- `403 Forbidden`: User lacks `creative_delete` permission
- `404 Not Found`: Creative does not exist
- `500 Internal Server Error`: Deletion failed

---

#### POST /api/v1/transcode

**Description**: Initiate transcoding job

**Authentication**: Required

**Permissions**: `creative_transcode`

**Request Body**:
```json
{
  "creativeId": 123,
  "options": {
    "targetFormats": ["mp4", "webm"],
    "targetResolutions": ["1920x1080", "1280x720"],
    "quality": "medium",
    "compress": true
  }
}
```

**Success Response** (202 Accepted):
```json
{
  "jobId": 789,
  "status": "queued",
  "estimatedDuration": 180,
  "message": "Transcoding job queued successfully"
}
```

**Error Responses**:
- `400 Bad Request`: Invalid creative ID or unsupported format/resolution
- `401 Unauthorized`: Missing or invalid JWT
- `403 Forbidden`: User lacks `creative_transcode` permission or creative not in accepted status
- `409 Conflict`: User already has active transcoding job
- `500 Internal Server Error`: Failed to queue job

---

#### GET /api/v1/transcode/:jobId

**Description**: Get transcoding job status and progress

**Authentication**: Required

**Success Response** (200 OK):
```json
{
  "jobId": 789,
  "sourceCreativeId": 123,
  "status": "processing",
  "progress": 65,
  "options": {
    "targetFormats": ["mp4", "webm"],
    "targetResolutions": ["1920x1080", "1280x720"],
    "quality": "medium",
    "compress": true
  },
  "variants": [124, 125],
  "startedAt": "2025-01-31T11:30:00Z",
  "estimatedCompletion": "2025-01-31T11:33:00Z"
}
```

**Completed Job Response**:
```json
{
  "jobId": 789,
  "status": "completed",
  "progress": 100,
  "variants": [124, 125, 126, 127],
  "completedAt": "2025-01-31T11:33:45Z"
}
```

**Error Responses**:
- `401 Unauthorized`: Missing or invalid JWT
- `404 Not Found`: Job does not exist
- `500 Internal Server Error`: Failed to retrieve job status

---

#### POST /api/v1/assignments

**Description**: Assign creative to campaign inventory

**Authentication**: Required

**Permissions**: `creative_assign`

**Request Body**:
```json
{
  "campaignId": 45,
  "inventoryId": 12,
  "scheduleId": null,
  "creativeId": 123,
  "conditionalRules": {
    "conditions": [
      { "type": "weather", "value": "sunny" }
    ],
    "isDefault": false
  }
}
```

**Success Response** (201 Created):
```json
{
  "assignmentId": 456,
  "campaignId": 45,
  "inventoryId": 12,
  "creativeId": 123,
  "tier2Status": "pending",
  "assignedBy": 67,
  "assignedAt": "2025-01-31T12:00:00Z",
  "validationWarnings": []
}
```

**Validation Errors** (400 Bad Request):
```json
{
  "error": "Validation failed",
  "validationErrors": [
    {
      "field": "creativeId",
      "message": "Creative resolution (1280x720) does not match inventory requirement (1920x1080)"
    },
    {
      "field": "creativeId",
      "message": "Creative status must be 'accepted' to assign to campaign"
    }
  ]
}
```

**Error Responses**:
- `400 Bad Request`: Validation errors (resolution mismatch, inadequate creative, etc.)
- `401 Unauthorized`: Missing or invalid JWT
- `403 Forbidden`: User lacks `creative_assign` permission or cannot access campaign
- `404 Not Found`: Campaign, inventory, or creative does not exist
- `500 Internal Server Error`: Assignment failed

---

#### PATCH /api/v1/assignments/:id

**Description**: Update assignment (primarily for Tier 2 approval status)

**Authentication**: Required

**Permissions**: `creative_approval_tier2` (for media owner approval)

**Request Body**:
```json
{
  "tier2Status": "approved",
  "tier2RejectionReason": null
}
```

**Rejection Example**:
```json
{
  "tier2Status": "rejected",
  "tier2RejectionReason": "Creative contains competitor branding in background"
}
```

**Success Response** (200 OK):
```json
{
  "assignmentId": 456,
  "tier2Status": "approved",
  "tier2ApprovedBy": 89,
  "tier2ApprovedAt": "2025-01-31T13:15:00Z",
  "updatedAt": "2025-01-31T13:15:00Z"
}
```

**Error Responses**:
- `400 Bad Request`: Invalid status transition or missing rejection reason
- `401 Unauthorized`: Missing or invalid JWT
- `403 Forbidden`: User not authorized to approve for this media owner's inventory
- `404 Not Found`: Assignment does not exist
- `500 Internal Server Error`: Update failed

---

#### GET /api/v1/assignments

**Description**: Retrieve creative assignments

**Authentication**: Required

**Query Parameters**:
- `campaignId` (integer, required): Campaign ID
- `tier2Status` (string, optional): Filter by approval status (`pending`, `approved`, `rejected`, `archive`)
- `inventoryId` (integer, optional): Filter by specific inventory

**Example Request**:
```
GET /api/v1/assignments?campaignId=45&tier2Status=pending
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

**Success Response** (200 OK):
```json
{
  "assignments": [
    {
      "assignmentId": 456,
      "campaignId": 45,
      "inventoryId": 12,
      "inventoryName": "Times Square Billboard",
      "scheduleId": null,
      "creativeId": 123,
      "creativeName": "Summer Campaign Banner",
      "tier2Status": "pending",
      "assignedBy": 67,
      "assignedAt": "2025-01-31T12:00:00Z"
    }
  ],
  "total": 15
}
```

**Error Responses**:
- `400 Bad Request`: Missing required campaignId parameter
- `401 Unauthorized`: Missing or invalid JWT
- `403 Forbidden`: User lacks permission to view campaign
- `500 Internal Server Error`: Query failed

---

## Appendix B: Workflow Acceptance Scenarios

### Scenario 1: Upload and Internal Approval

**Given**: Marketing designer has uploaded a new banner creative  
**When**: Manager reviews and approves the creative  
**Then**:
- Creative status transitions from "Processing" to "Accepted"
- Creative becomes visible in assignment interfaces
- Uploader receives approval notification
- Approval timestamp and approver ID recorded in database

**Given**: Manager reviews creative and finds quality issues  
**When**: Manager marks creative as "Inadequate" with reason "Low resolution images"  
**Then**:
- Creative status transitions to "Inadequate"
- Creative is hidden from assignment interfaces by default
- Uploader receives rejection notification with specific reason
- Creative retained in system with rejection reason stored

---

### Scenario 2: Creative Assignment with Validation

**Given**: Campaign has inventory requiring 1920x1080 resolution  
**When**: User attempts to assign 1280x720 creative  
**Then**:
- System highlights drop zone in red
- Tooltip displays: "Resolution mismatch: needs 1920x1080"
- Assignment is blocked; creative not assigned
- No database record created

**Given**: Campaign has inventory requiring 1920x1080 resolution  
**When**: User assigns matching 1920x1080 accepted creative  
**Then**:
- System highlights drop zone in green
- Assignment created successfully
- Tier 2 status automatically set to "Pending"
- Media owner receives approval notification
- Assignment visible in campaign creative summary

---

### Scenario 3: Media Owner Approval (Tier 2)

**Given**: Creative is assigned to media owner's inventory with status "Pending"  
**When**: Media owner user reviews and approves creative  
**Then**:
- Tier 2 status transitions from "Pending" to "Approved"
- Campaign owner receives approval notification
- Campaign readiness check updated (can proceed if all assignments approved)
- Approval timestamp and approver ID recorded

**Given**: Media owner reviews creative and finds content violation  
**When**: Media owner rejects with reason "Contains competitor branding"  
**Then**:
- Tier 2 status transitions to "Rejected"
- Campaign owner receives rejection notification with reason
- Campaign blocked from going live
- Campaign owner must reassign different creative or modify and re-upload

---

### Scenario 4: Grouped Creative Assignment

**Given**: Campaign has 6 inventories all with 1920x1080 @ 10 seconds  
**When**: User activates "Group by Resolution & Duration" view  
**Then**:
- System displays: "1920x1080 - 10 sec (6 inventories)"
- Single group card shown instead of 6 individual cards

**Given**: Grouped view active with "1920x1080 - 10 sec (6 inventories)"  
**When**: User drags accepted creative onto group  
**Then**:
- Confirmation dialog: "Assign to all 6 inventories?"
- User confirms
- 6 individual assignment records created
- All 6 assignments set to "Pending" Tier 2 status
- Assignment summary shows 6/6 assigned

---

### Scenario 5: Transcoding Workflow

**Given**: User has 4K video creative (3840x2160, MP4)  
**When**: User initiates transcode to 1920x1080 and 1280x720 in MP4 and WebM  
**Then**:
- Transcoding job queued with estimated duration
- Progress updates displayed in real-time
- On completion, 4 new creative variants created with new IDs
- Variants auto-tagged: `transcoded`, `variant-of-{originalID}`
- All variants set to "Processing" status (require Tier 1 approval)
- User notified: "Transcoding complete - 4 variants created"

**Given**: User attempts to start transcode while existing job active  
**When**: User clicks "Start Transcoding" on different creative  
**Then**:
- Error message: "You already have an active transcoding job. Please wait for it to complete."
- New job not queued
- User can cancel existing job or wait for completion

---

## Glossary

| Term | Definition |
|------|------------|
| **Content Hub** | Central interface for managing all creative assets |
| **Tier 1 Approval** | Internal company-level approval (Processing → Accepted/Inadequate) |
| **Tier 2 Approval** | Media owner approval after campaign assignment (Pending → Approved/Rejected) |
| **Inadequate** | Tier 1 status indicating creative failed internal review and cannot be assigned |
| **Transcoding** | Converting creative to different format, resolution, or quality |
| **Variant** | New creative generated from transcoding an original creative |
| **Assignment** | Mapping of a creative to a specific campaign inventory and schedule |
| **Conditional Rules** | Logic mapping different creatives to different conditions (weather, signals) |
| **Grouped Assignment** | Assigning one creative to multiple inventories by shared specifications |
| **Microservice** | Independent service accessible via API by multiple platforms |
| **Tenant** | Company/organization with isolated data and creative library |
| **Archive** | Status removing creative from active use while retaining for audit trail |

---

**Document Version**: 1.0  
**Last Updated**: January 31, 2025  
**Author**: Product Team  
**Status**: Product Specification (for stakeholder review and engineering planning)
