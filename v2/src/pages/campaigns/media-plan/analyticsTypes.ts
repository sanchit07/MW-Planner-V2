// Shared type definitions for Analytics View and Excel generation
// These types are used by both UI components and Excel generator to ensure consistency

import { CustomFee } from "src/types/campaign.types";

// Campaign Plan Tab Types
export interface CampaignDetails {
  campaignName: string;
  campaignId: string;
  planNumber?: string;
  createdOn: string;
  startDate: string;
  endDate: string;
  goal: string;
  kpi: string;
  createdBy?: string;
  company?: string;
  emailAddress?: string;
  dsp?: string;
  seatId?: string;
  brand?: string;
  oohImpressions?: string;
  uniqueReach?: string;
  averageFrequency?: string;
  cpm?: string;
  currency: string;
  // Excel Plan-sheet-only fields (not shown in the UI Plan Details/Buyer
  // Details cards, which source these directly from props instead).
  status?: string;
  durationLabel?: string;
  channelsLabel?: string;
  budget?: number;
  goalLabel?: string;
  brandCategory?: string;
  clientTypeLabel?: string;
  agency?: string;
}

/** Estimated Performance Metrics card, mirrored for the Excel Plan sheet
 * (the UI computes these inline in CampaignPlanTab.tsx from live props —
 * this is the same set of values/formulas, persisted onto AnalyticsExcelData
 * so the Excel generator doesn't need the raw hook data). */
export interface EstimatedPerformanceMetrics {
  totalImpressions: number;
  estimatedReach: number;
  avgFrequency: number;
  estAdPlays: number;
  /** False for classic-only campaigns (ad plays is a digital-slot concept) —
   * true if the channel cost-split is empty (not yet loaded) or mixed/digital. */
  hasDigitalInventory: boolean;
  /** Label already resolved to "Avg CPM" or "Avg CPS" per goal type. */
  avgCpmLabel: string;
  avgCpm: number;
  ecpm: number;
  sov: number;
  sot: number;
  totalCost: number;
  inventories: number;
  cities: number;
  channels: number;
  /** Real, existing UI footnote (estimation.note i18n key) — same text shown
   * under the on-screen Estimated Performance Metrics card. */
  footnote?: string;
}

/** Targeting Applied section of the Excel Plan sheet — all sourced from the
 * campaign's real targeting config (demographics/geofencing/signals/venue
 * types); cinema-specific venue entries are filtered out. */
export interface TargetingApplied {
  demographics?: string;
  income?: string;
  interests?: string;
  venueEnvironments?: string;
  behaviour?: string;
  signals?: string;
  geography?: string;
}

/** One row of the Excel "Delivery Breakdown" table — same bins/values as
 * computeExpectedDelivery (see ./utils), just persisted for the exporter. */
export interface DeliveryBreakdownRow {
  period: string;
  impressions: number;
  reach: number;
}

export interface StatePlanningRow {
  id: string;
  stateName: string;
  population: string;
  inventories: number;
  oohImpressions: string;
  reach: string;
  frequency: string;
  cpm: number;
}

export interface InventoryPlanningRow {
  id: string;
  name: string;
  billboardName: string;
  referenceId: string;
  oohImpressions: string;
  uniqueReach: string;
  frequency: number;
  ecpm: number;
}

export interface InventoryMappingRow {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  billboardName: string;
  referenceId: string;
  distanceFromCityCenter: number;
  stateName: string;
  districtName: string;
}

// Inventory Details Tab Types
export type InventoryType =
  | "classic"
  | "classic network"
  | "digital"
  | "digital network"
  | "mobile"
  | "cinema";

export interface InventoryDetailsRow {
  id: string;
  type: InventoryType;
  // Classic fields
  billboardName?: string;
  referenceId?: string;
  mediaOwner?: string;
  format?: string;
  size?: string;
  latitude?: string;
  longitude?: string;
  exclusions?: string;
  assetImages?: string;
  category?: string;
  location?: string;
  venueType?: string;
  noOfFaces?: string;
  boardFacing?: string;
  languageSupport?: string;
  description?: string;
  // Classic network fields
  noOfBillboards?: string;
  // Digital fields
  resolution?: string;
  creative?: string;
  noOfScreens?: string;
  spotDuration?: string;
  spotsPerHour?: number;
  totalSpots?: number;
  // Mobile fields
  campaignBudget?: string;
  dailyBudget?: string;
  bidRate?: string;
  advertiserDomain?: string;
  frequencyCapping?: string;
  // Summary-table fields (flat Inventory Details view)
  city?: string;
  thumbnailUrl?: string;
  impressions?: number;
  playsPerDay?: number;
  cpm?: number;
  /** Cost-per-spot rate, shown instead of cpm for SOV/AD_PLAYS goals. */
  spotRate?: number;
  /** SchedulePattern (@utils/schedule.utils) off the item's dominant
   * (longest) schedule segment — Excel Inventory Details sheet only. */
  schedulePattern?: string;
}

// Costing Tab Types
export interface CostingDetails {
  subTotal: number;
  outdoorCommission: number;
  agencyCommission: number;
  discount: number;
  netTotal: number;
  additionalCost: number;
  sst: number;
  platformFee: number;
  grandTotal: number;
  discountedMediaCost?: number;
  proposedPrice?: number;
  customFees?: CustomFee[];
}

/** Per-inventory row for the flat Costing table. */
export interface CostingInventoryRow {
  id: string;
  name: string;
  city: string;
  baseCpm: number;
  proposed: number;
  accepted: number;
  impressions: number;
  mediaCost: number;
  feeShare: number;
  total: number;
}

export interface ClassicInventoryRow {
  id: string;
  billboardName: string;
  referenceId: string;
  mediaOwner: string;
  mediaCost: number;
  lighting: number;
  production: number;
  otherFees: number;
  subTotal: number;
}

export interface DigitalInventoryRow {
  id: string;
  billboardName: string;
  referenceId: string;
  mediaOwner: string;
  contentManagementFee: number;
  subTotal: number;
}

/**
 * One row of the Cinema analytics table/sheet. Cinema is bought by
 * operator/hall/showtime-window with genre/rating constraints — films are
 * only an indicative read-only preview, never a buy unit. Sourced from
 * item.detail.cinemaFields (may be absent — degrades to blank strings).
 */
export interface CinemaInventoryRow {
  id: string;
  name: string;
  operator: string;
  cinemaName: string;
  hall: string;
  /** Joined showtime-window labels, e.g. "Matinee, Evening". */
  showtimeWindows: string;
  /** Joined genre constraints. */
  genres: string;
  /** Joined rating constraints. */
  ratings: string;
  impressions: number;
  cpm: number;
  mediaCost: number;
}

// Operation Details Tab Types
export interface ClassicOperationScheduleRow {
  id: string;
  inventoryName: string;
  /** Reference id + format + city for the group-header row. */
  referenceId: string;
  format: string;
  city: string;
  /** Schedule name, e.g. "Morning Rush" — segment/row within the inventory. */
  segment: string;
  startDate: string;
  endDate: string;
  operationDays: number;
}

export interface DigitalOperationScheduleRow {
  id: string;
  inventoryName: string;
  referenceId: string;
  format: string;
  city: string;
  segment: string;
  startDate: string;
  endDate: string;
  scheduleType: string;
  operationDays: number;
  operationHours: number;
  startTime: string;
  endTime: string;
  totalSpots: number;
}

export interface MobileOperationScheduleRow {
  id: string;
  inventoryName: string;
  referenceId: string;
  format: string;
  city: string;
  segment: string;
  startDate: string;
  endDate: string;
  scheduleType: string;
  operationDays: number;
  operationHours: number;
  startTime: string;
  endTime: string;
}

export interface OperationDetailsData {
  classic?: ClassicOperationScheduleRow[];
  digital?: DigitalOperationScheduleRow[];
  mobile?: MobileOperationScheduleRow[];
}

// DOOH Schedules Tab Types
export interface DOOHScheduleRow {
  id: string;
  sno: number;
  billboardName: string;
  startDate: string;
  endDate: string;
  duration: number;
  operationHours: string;
  mon: boolean;
  tue: boolean;
  wed: boolean;
  thu: boolean;
  fri: boolean;
  sat: boolean;
  sun: boolean;
}

export interface DOOHScheduleSummary {
  campaignName: string;
  duration: number;
  startDate: string;
  totalAdPlays?: string;
  totalImpressions?: string;
}

/** One schedule segment row within a DOOH panel's calendar (e.g. "Morning Rush"). */
export interface DOOHScheduleSegmentRow {
  id: string;
  segmentName: string;
  startDate: string;
  endDate: string;
  days: number;
  /** 24h time range, e.g. "06:00–10:00", or "--" if no active hours. */
  opHoursLabel: string;
  /** Raw "YYYY-MM-DD" dates (from bookingMatrix) this segment is active — used
   * to shade the weekly Gantt grid against real calendar dates. */
  activeDates: string[];
}

/** One digital inventory ("panel") for the DOOH Schedule Calendar + cadence table. */
export interface DOOHPanelRow {
  id: string;
  inventoryName: string;
  referenceId: string;
  format: string;
  city: string;
  channel: string;
  startDate: string;
  endDate: string;
  days: number;
  /** Single time range if every segment shares it, else "mixed". */
  opHoursLabel: string;
  segments: DOOHScheduleSegmentRow[];
  /** Day-weighted average across the panel's schedule segments. */
  spotsPerLoop: number;
  spotsPerHour: number;
  activeHoursPerDay: number;
  /** Distinct weekdays across the union of all segments' scheduleDays. */
  daysPerWeek: number;
  sov: number;
  /** SchedulePattern from @utils/schedule.utils (e.g. "business", "commuter",
   * "24/7", "custom") classified off the panel's dominant (longest) segment. */
  pattern: string;
}

export interface DOOHRollupCell {
  hour: number;
  /** Distinct schedules (across all digital panels) active this weekday+hour. */
  count: number;
}

export interface DOOHRollupRow {
  /** Short weekday label, "Mon".."Sun". */
  day: string;
  cells: DOOHRollupCell[];
}

/** Plan-level cross-panel activity heatmap for the DOOH Schedules tab. */
export interface DOOHRollupHeatmap {
  rows: DOOHRollupRow[];
  maxCount: number;
  totalSchedules: number;
  /** Distinct classified Pattern values across all panels. */
  totalPatterns: number;
}

/**
 * One row of the Geography Targeting tree (Country › Region › City), built
 * client-side from each selected inventory item's own location + performance
 * fields — no cost-split API involved, so state/city numbers always sum
 * consistently up to their parent.
 */
export interface GeographyTargetingRow {
  id: string;
  level: "country" | "state" | "city";
  /** 0 = country, 1 = state/region, 2 = city — for row indentation. */
  depth: number;
  name: string;
  inventories: number;
  impressions: number;
  reach: number;
  /** impressions ÷ reach for this node (not a per-item average). */
  frequency: number;
  /** (total cost ÷ impressions) × 1000 for this node. */
  ecpm: number;
}

/**
 * A geofence/POI row nested under a city in the Excel Geography Targeting
 * sheet only (not shown in the UI tab). Built from the campaign's real
 * targeting.geofencing zones, each matched to its nearest inventory via
 * findInventoryForPOI (src/utils/inventory-match.utils.ts) — a POI with no
 * inventory within the match threshold is omitted.
 */
export interface GeographyPoiRow {
  /** id of the parent GeographyTargetingRow (level: "city") this nests under. */
  parentCityId: string;
  /** "{zone name} · {radius km} · Include/Exclude — {inventory} (...)" */
  name: string;
  inventories: number;
  impressions: number;
  reach: number;
  frequency: number;
  ecpm: number;
}

// Combined data structure for Excel generation
export interface AnalyticsExcelData {
  // Campaign Plan data
  campaignDetails?: CampaignDetails;
  estimatedPerformanceMetrics?: EstimatedPerformanceMetrics;
  targetingApplied?: TargetingApplied;
  deliveryBreakdown?: DeliveryBreakdownRow[];
  deliveryGranularity?: "weekly" | "monthly";
  statePlanning?: StatePlanningRow[];
  cityPlanning?: StatePlanningRow[];
  inventoryPlanning?: InventoryPlanningRow[];
  inventoryMapping?: InventoryMappingRow[];
  // Inventory Details data
  inventoryDetails?: InventoryDetailsRow[];
  // Costing data
  costingDetails?: CostingDetails;
  costingInventoryRows?: CostingInventoryRow[];
  classicInventory?: ClassicInventoryRow[];
  classicNetworkInventory?: ClassicInventoryRow[];
  digitalInventory?: DigitalInventoryRow[];
  digitalNetworkInventory?: DigitalInventoryRow[];
  // Cinema inventory data (operator/hall/showtime — films are indicative only)
  cinemaInventory?: CinemaInventoryRow[];
  // Operation Details data
  operationDetails?: OperationDetailsData;
  // DOOH Schedules data
  doohSchedules?: DOOHScheduleRow[];
  doohScheduleSummary?: DOOHScheduleSummary;
  doohPanels?: DOOHPanelRow[];
  doohRollupHeatmap?: DOOHRollupHeatmap;
  // Geography Targeting data
  geographyTargeting?: GeographyTargetingRow[];
  geographyTargetingPoiRows?: GeographyPoiRow[];
}
