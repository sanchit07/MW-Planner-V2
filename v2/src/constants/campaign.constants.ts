import { TreeNode } from "../components/ui/MultiSelect";
import { CampaignStatus } from "../types/campaign-display.types";

/**
 * Default page size for pagination
 */
export const DEFAULT_PAGE_SIZE = 10;

/**
 * Default page number
 */
export const DEFAULT_PAGE = 1;

/**
 * Default sort configuration
 */
export const DEFAULT_SORT = {
  sortBy: "updatedAt",
  sortDir: "desc" as const,
};

/**
 * Campaign status options
 */
export const CAMPAIGN_STATUSES: CampaignStatus[] = [
  "active",
  "paused",
  "draft",
  "completed",
];

/**
 * Campaign status filter options (for filter modals and dropdowns)
 * These match the API status values
 */
export const CAMPAIGN_STATUS_OPTIONS: TreeNode[] = [
  {
    id: "draft",
    label: "Draft",
    value: "DRAFT",
  },
  {
    id: "planned",
    label: "Planned",
    value: "PLANNED",
  },
  {
    id: "reviewing",
    label: "Reviewing",
    value: "REVIEWING",
  },
  {
    id: "negotiating",
    label: "Negotiating",
    value: "NEGOTIATING",
  },
  {
    id: "pending",
    label: "Pending",
    value: "PENDING",
  },
  {
    id: "approved",
    label: "Approved",
    value: "APPROVED",
  },
  { id: "active", label: "Active", value: "ACTIVE" },
  {
    id: "completed",
    label: "Completed",
    value: "COMPLETED",
  },
  {
    id: "archived",
    label: "Archived",
    value: "ARCHIVED",
  },
  { id: "rejected", label: "Rejected", value: "REJECTED" },
];

/**
 * Infinite scroll threshold (pixels from bottom)
 */
export const INFINITE_SCROLL_THRESHOLD = 200;

/**
 * Default currency if not provided
 */
export const DEFAULT_CURRENCY = "USD";

/**
 * Default fallback values
 */
export const FALLBACK_VALUES = {
  USER_NAME: "N/A",
  BRAND: "N/A",
  CATEGORY: "N/A",
  BUDGET: "N/A",
  TOTAL_COST: "N/A",
  GOAL_TYPE: "N/A",
  DAYS_LEFT: "--",
} as const;

export const INVENTORY_SIZE_LIST_MAP = [
  {
    id: "XL",
    value: "XL",
    label: "Extra Large",
    description:
      "Typically > 300 sqft (e.g., 14 x 48 ft (672 sqft) bulletin, large wallscapes, jumbotrons)",
  },
  {
    id: "L",
    value: "L",
    label: "Large",
    description:
      "Approximately 100 - 300 sqft (e.g., 10 x 22 ft posters, large digital billboards)",
  },
  {
    id: "M",
    value: "M",
    label: "Medium",
    description:
      "Approximately 30 - 100 sqft (e.g., bus shelter panels, standard digital panels)",
  },
  {
    id: "S",
    value: "S",
    label: "Small",
    description:
      "Approximately 5 - 30 sqft (e.g., bench ads, in-vehicle screens, small mall screens)",
  },
  {
    id: "XS",
    value: "XS",
    label: "Extra Small",
    description:
      "Less than 5 sqft (e.g., shelf-edge displays, small elevator screens)",
  },
];

/**
 * Coordinate validation constants
 */
export const COORDINATE_LIMITS = {
  MIN_LATITUDE: -90,
  MAX_LATITUDE: 90,
  MIN_LONGITUDE: -180,
  MAX_LONGITUDE: 180,
} as const;

/**
 * Duplicate detection threshold (approximately 10 meters in degrees)
 * Used to detect duplicate locations within ~10 meters
 */
export const DUPLICATE_DETECTION_THRESHOLD = 0.0001;

/**
 * Default radius for imported locations (in meters)
 */
export const DEFAULT_IMPORTED_LOCATION_RADIUS = 5000;

/**
 * Maximum POI count per location/geometry
 */
export const MAX_POI_COUNT = 5;
