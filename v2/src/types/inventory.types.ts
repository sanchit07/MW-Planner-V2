// Inventory Types - Matching actual API response

export type dayString =
  | "MONDAY"
  | "TUESDAY"
  | "WEDNESDAY"
  | "THURSDAY"
  | "FRIDAY"
  | "SATURDAY"
  | "SUNDAY";

/** PascalCase alias for dayString (preferred in new code). */
export type DayString = dayString;

export interface InventoryLocation {
  address: string;
  country: string;
  state: string;
  city: string;
  zipCode: string;
  locationCoordinates: LocationCoordinates;
}

export interface LocationCoordinates {
  coordinates: LatLong[];
  type: string;
}

export interface LatLong {
  latitude: number;
  longitude: number;
}

export interface InventoryDetail {
  id: string;
  name: string;
  externalId: string;
  referenceId: string;
  mediaOwnerId: string;
  mediaOwnerName: string;
  inventoryType: string;
  category: string;
  venueType: string[];
  thumbnail: string;
  images: string[];
  format: string;
  environment: string;
  /** Panel size letter code, e.g. "xl" for extra large. */
  size: string;
  operationMode: string;
  execution: string;
  screens: number;
  sov: number;
  isSelected: boolean;
  isCompliant: boolean;
  bookingMode: string;
  panels: Panels[];
  /** Programmatic deal-type names; non-empty ⇒ inventory supports programmatic. */
  programmaticDealTypes?: string[];
  sellingTerm?: SellingTerm;
  /** Cinema buy attributes — present only when inventoryType/classification is Cinema. */
  cinemaFields?: CinemaFields;
}

/**
 * Cinema-specific buy attributes. The buy unit is the environment
 * (operator → cinema → hall → showtime window + genre/rating constraints);
 * films are only an indicative read-only preview, never a buy unit.
 */
export interface CinemaFields {
  operator?: string;
  operatorId?: string;
  cinemaName?: string;
  hallName?: string;
  hallNumber?: number;
  seats?: number;
  screenFormat?: string;
  showtimeWindows?: Array<{ label: string; start: string; end: string }>;
  genres?: string[];
  ratings?: string[];
}

export interface Panels {
  pixelWidth: number;
  pixelHeight: number;
  physicalWidth: number;
  physicalHeight: number;
  panelCount: number;
  unit: string;
  size: string;
}

export interface POIData {
  types: string[];
  nearbyPOIs: string[];
  categories: string[];
}

export interface DemographicsData {
  age: string;
  gender: string;
  overall: string;
  ageGender: string;
  income: string;
  behaviour: string;
  interest: string;
  highestIndexScore: string;
}

export interface InventoryLocationData {
  location: InventoryLocation;
  poi: POIData;
  demographics: DemographicsData;
  visibility?: string;
}

export interface InventoryPerformance {
  cpmRate: number;
  spotRate?: number;
  estimatedCost: number;
  perDayCost: number;
  perDayAdPlays: number;
  totalAdPlays: number;
  plannedSot: number;
  totalSot: number;
  sov?: number;
  estimatedImpression?: number;
  estimatedImpressions?: number;
  estimatedReach?: number;
  estimatedFrequency?: number;
}

export interface InventoryOperations {
  startTime?: string;
  endTime?: string;
  operationDays: dayString[];
  operatingTimes?: Record<dayString, Array<{ start: string; end: string }>>;
  maintenanceWindow: string;
  loopSize: number;
  slotDuration: number;
  clientPerLoop: number;
  cycleTime: number;
}

export interface InventoryItem {
  detail: InventoryDetail;
  location: InventoryLocationData;
  performance: InventoryPerformance;
  operations: InventoryOperations;
  scheduleDates?: Array<{
    startDate: string;
    endDate: string;
    totalHours: number;
  }>;
  scheduleHours?: string[][];
  schedules: InventorySchedule[];
  isExpanded?: boolean; // Local UI state
}

/** Single recommendation item from AI recommendation API */
export interface InventoryRecommendationItem {
  inventoryId: string;
  referenceId: string;
  name: string;
  finalScore: number;
  componentScores: {
    geoFit: number;
    availability: number;
    budgetFit: number;
    audienceFit: number;
    brandFit: number;
    qualityFit: number;
    timeFit: number;
    measureFit?: number;
  };
  why: string;
  selectionMode?: string;
  availability: {
    availableDays: number;
    totalDays: number;
    availabilityPercentage: number;
    summary: string;
    allAvailable: boolean;
  };
  forecast: {
    estimatedImpressions?: number;
    estimatedReach?: number;
    estimatedFrequency?: number;
  };
  cost: {
    estimatedCost?: number;
    currency?: string;
    totalAdPlays?: number;
  };
  inventoryDetails: {
    internalId: string;
    name: string;
    classification: string;
    type: string;
    format?: string;
    environment: string;
    venueTypes: string[];
    orientation?: string;
    sizes: string[];
    mediaOwnerId: string;
    mediaOwnerName: string;
    address?: string;
    location: {
      countryId: string;
      countryName: string;
      stateId: string;
      stateName: string;
      cityId: string;
      cityName: string;
      locationCoordinates: {
        type: string;
        coordinates: number[];
      };
    };
    thumbnailUrl?: string;
    externalRefIds?: Array<{ source: string; externalRefId: string }>;
    digitalFields?: Partial<DigitalFields>;
  };
  isExcluded: boolean;
  performance: {
    estimatedImpressions?: number;
    estimatedReach?: number;
    estimatedFrequency?: number;
    totalAdPlays?: number;
    perDayAdPlays?: number;
    estimatedCost?: number;
    perDayCost?: number;
    sov?: number;
    totalSot?: number;
    plannedSot?: number;
    cpmRate?: number;
    spotRate?: number;
  };
}

/** API response for recommendation list (run results) */
export interface InventoryRecommendationListResponse {
  runId: string;
  campaignId: string;
  productId: string;
  companyId: string;
  recommendations: InventoryRecommendationItem[];
  pagination: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    hasNext: boolean;
    hasPrevious: boolean;
  };
}

/**
 * Normalized display shape for inventory, usable for both InventoryItem and InventoryRecommendationItem.
 * Use toInventoryDisplayItem() to convert either type to this interface for unified display (cards, lists, map popups).
 */
export interface InventoryDisplayDetail {
  id: string;
  referenceId: string;
  name: string;
  thumbnail: string;
  images: string[];
  inventoryType: string;
  category?: string;
  venueTypes?: string[];
  format: string;
  environment: string;
  mediaOwnerId: string;
  mediaOwnerName: string;
  panels: Panels[];
  screens?: number;
  sov?: number;
  isSelected?: boolean;
  isCompliant?: boolean;
  /** Cinema buy attributes — present only for cinema inventory. */
  cinemaFields?: CinemaFields;
  /** Panel size letter code, e.g. "xl" for extra large. */
  size?: string;
}

export interface InventoryDisplayLocation {
  location: {
    address: string;
    country: string;
    state: string;
    city: string;
    zipCode?: string;
    locationCoordinates?: LocationCoordinates;
  };
}

export interface InventoryDisplayPerformance {
  estimatedReach?: number;
  estimatedFrequency?: number;
  estimatedImpressions?: number;
  cpmRate?: number;
  estimatedCost: number;
  perDayCost?: number;
  perDayAdPlays?: number;
  totalAdPlays?: number;
  plannedSot?: number;
  totalSot?: number;
  spotRate?: number;
}

export interface InventoryDisplayRecommendationInfo {
  finalScore: number;
  why: string;
  selectionMode: string;
  isExcluded: boolean;
  componentScores: InventoryRecommendationItem["componentScores"];
  availability: InventoryRecommendationItem["availability"];
}

export type InventoryDisplaySource = "inventory" | "recommendation";

export interface InventoryDisplayItem {
  source: InventoryDisplaySource;
  detail: InventoryDisplayDetail;
  location: InventoryDisplayLocation;
  performance: InventoryDisplayPerformance;
  operations?: InventoryOperations;
  schedules?: InventorySchedule[];
  recommendationInfo?: InventoryDisplayRecommendationInfo;
  /** Original item when source is "inventory" (for callbacks that need full InventoryItem) */
  originalInventoryItem?: InventoryItem;
  /** Original item when source is "recommendation" (for callbacks that need full InventoryRecommendationItem) */
  originalRecommendationItem?: InventoryRecommendationItem;
}

export interface SortInfo {
  empty: boolean;
  unsorted: boolean;
  sorted: boolean;
}

export interface PageableInfo {
  offset: number;
  unpaged: boolean;
  sort: SortInfo;
  paged: boolean;
  pageNumber: number;
  pageSize: number;
}

export interface InventoryListResponse {
  totalElements: number;
  totalPages: number;
  size: number;
  content: InventoryItem[];
  number: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
  sort: SortInfo;
  pageable: PageableInfo;
  empty: boolean;
}

export type InventoryRecommendationStatus =
  | "IN_PROGRESS"
  | "COMPLETED"
  | "FAILED";

/** Status API response only; use runId with getInventoryRecommendationList to fetch the list. */
export interface InventoryRecommendationResponse {
  runId: string;
  status: string;
  completionPercentage: number;
  campaignId: string;
  productId: string;
  companyId: string;
  generatedAt: string;
  metadata: {
    totalInventoriesEvaluated: number;
    totalInventoriesRecommended: number;
    totalInventoriesExcluded: number;
    exclusionReasons: {
      additionalProp1: number;
      additionalProp2: number;
      additionalProp3: number;
    };
    averageScore: number;
    seed: string;
    autoSelectedInventoryIds: string[];
  };
  warnings: string[];
}

// Filter interfaces
export interface Demographics {
  age?: string[];
  gender?: string[];
}

export interface GeofencingGeometry {
  type: "Polygon" | "Circle" | "LineString";
  coordinates: number[][];
  included: boolean;
}

export interface GeofencingLocation {
  lat: number;
  lng: number;
  radius: number;
  address: string;
  included: boolean;
}

export interface Geofencing {
  geometries?: GeofencingGeometry[];
  locations?: GeofencingLocation[];
}

export interface SizeFilter {
  minWidth?: number;
  maxWidth?: number;
  minHeight?: number;
  maxHeight?: number;
  unit?: string;
  minArea?: number;
  maxArea?: number;
}

export interface InventoryFilterRequest {
  countries?: string[];
  states?: string[];
  cities?: string[];
  demographics?: Demographics;
  geofencing?: Geofencing;
  mediaOwnerIds?: string[];
  inventoryTypes?: string[];
  inventoryCluster?: string[];
  type?: string[];
  bookingMode?: string[];
  inventorySizes?: SizeFilter;
  tags?: string[];
  poiCategories?: string[];
  poiIds?: string[];
  minDailySpots?: number;
  maxDailySpots?: number;
  minMonthlySpots?: number;
  maxMonthlySpots?: number;
  formats?: string[];
  venueTypes?: string[];
  venueTypeIdFilter?: {
    digitalOoh: string[];
    classicOoh: string[];
  };
  categories?: string[];
  groups?: string[];
  excludeInventoryIds?: string[];
  latitude?: string;
  longitude?: string;
  name?: string;
  sizes?: string[];
  environments?: string[];
  classifications?: string[];
  programmaticSupport?: "YES" | "NO";
  dealTypes?: string[];
  goalType?: string;
  startDate?: string;
  endDate?: string;
  /** Cinema film-genre constraints for the cinema buy. */
  cinemaGenres?: string[];
  /** Cinema content-rating constraints for the cinema buy. */
  cinemaRatings?: string[];
}

export type ProgrammaticSupportFilter = "ALL" | "YES" | "NO";

export interface InventoryFilters {
  mediaOwners: string[];
  venueTypes: string[];
  bookingMode: string[];
  sizes: string[];
  latitude: string;
  longitude: string;
  searchbyquery: string;
  environments: string[];
  inventoryClassification: string[];
  programmaticSupport: ProgrammaticSupportFilter;
  dealTypes: string[];
  /** Cinema film-genre constraints; only applies to Cinema inventory. */
  cinemaGenres?: string[];
  /** Cinema content-rating constraints; only applies to Cinema inventory. */
  cinemaRatings?: string[];
}

export interface InventoryFilterParams extends InventoryFilterRequest {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: "asc" | "desc" | "ASC" | "DESC";
}

// Selection API
export interface InventorySelectionRequest {
  campaignId: string;
  inventoryId: string;
  operationType: "SELECT" | "DESELECT";
  sov?: number;
}

export interface InventorySelectionResponse {
  success: boolean;
  data?: string;
}

// Bulk Selection API
export interface InventoryBulkSelectionRequest {
  campaignId: string;
  operationType: "SELECT" | "DESELECT";
  filters: InventoryFilterRequest;
}

// Bulk select/deselect by pasted reference IDs — matching happens server-side
// against the full campaign inventory set, not just the currently loaded page.
export interface InventoryBulkSelectByReferenceIdsRequest {
  campaignId: string;
  referenceIds: string[];
  operationType: "SELECT" | "DESELECT";
}

// Forecast API types
export interface CampaignForecastData {
  totalInventories: number;
  estimatedImpression: number;
  estimatedReach: number;
  estimatedFrequency: number;
  estimatedAdPlays: number;
  sov: number;
  avgCpm: number;
  avgECpm: number;
  totalCost: number;
  plannedSot: number;
  totalSot: number;
  /** Estimated CO₂ emission per ad play, in kg. */
  co2PerPlay?: number;
  warnings: string[];
}

// Reach and Frequency API types
export interface InventoryReachFrequencyRequest {
  inventories: {
    referenceId: string;
    type: string;
    spotsPerHour: number;
    dayparts?: { scheduledDate: string; scheduledTime: string[] }[];
  }[];
  duration: number;
}

export interface InventoryReachFrequencyResponse {
  reach?: number;
  status?: string;
  frequency?: number;
  impressions?: number;
}

// Campaign selected-inventory (all, unpaginated) — feeds the Reach Saturation
// Curve payload (referenceId, reach, cpmBudget per inventory). Each item carries
// the per-inventory performance, including estimatedReach + estimatedCost.
export interface SelectedInventoryPerformanceItem {
  inventoryId: string;
  referenceId: string;
  performance: {
    spotRate?: number;
    cpmRate?: number;
    estimatedCost?: number;
    perDayCost?: number;
    perDayAdPlays?: number;
    totalAdPlays?: number;
    totalSot?: number;
    plannedSot?: number;
    sov?: number;
    estimatedImpression?: number;
    estimatedReach?: number;
    estimatedFrequency?: number;
  };
}

// Reach Saturation Curve API (FREQUENCY_URL + v2/reach-saturation-curve)
export interface ReachSaturationCurveRequest {
  inventories: {
    referenceId: string;
    reach: number;
    cpmBudget: number;
  }[];
  duration: number;
  startDate: string;
  endDate: string;
}

export interface ReachSaturationCurveItem {
  referenceId: string;
  reach: number;
  cpmBudget: number;
  saturatedReach: number[];
  saturatedReachDate: string;
}

export interface ReachSaturationCurveResult {
  inventories: ReachSaturationCurveItem[];
  overallInventories: {
    overallReach: number[];
    overallsaturatedReachDate: string;
  };
}

/** The curve endpoint returns an array; the overall curve lives in element 0. */
export type ReachSaturationCurveResponse = ReachSaturationCurveResult[];

export interface InventoryCsvVerifyResult {
  id: string;
  row: number;
  message: string;
  type: string;
}

export interface InventoryCsvUploadResponse {
  results: InventoryCsvVerifyResult[];
  logs?: InventoryCsvVerifyResult[];
}

// CSV File List API types
export interface InventoryCsvFile {
  id: string;
  fileName: string;
  inventoryCount: number;
  createdBy: string;
  createdAt: string;
}

export interface InventoryCsvFileListResponse {
  totalElements: number;
  totalPages: number;
  size: number;
  content: InventoryCsvFile[];
  number: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
  sort: SortInfo;
  pageable: PageableInfo;
  empty: boolean;
}

export interface InventoryCsvFileListParams {
  countryName: string;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: "asc" | "desc" | "ASC" | "DESC";
}

export interface InventoryFileSelectedItem {
  srId: number;
  id: string;
  inventoryName: string;
  referenceId: string;
  type: string;
  category: string;
  location: {
    address: string;
    latitude: number;
    longitude: number;
    state: string;
    country: string;
  };
  thumbnail: string;
}

export interface InventoryFileListResponse {
  totalElements: number;
  totalPages: number;
  size: number;
  content: InventoryFileSelectedItem[];
  number: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
  sort: SortInfo;
  pageable: PageableInfo;
  empty: boolean;
}

// Location interface
export interface Location {
  address: string;
  country: string;
  state: string;
  city: string;
  zipCode: string;
  locationCoordinates: LocationCoordinates;
}

// POI interface
export interface POI {
  types: string[];
  nearbyPOIs: string[];
  categories: string[];
}

// Demographics interface
export interface InventoryDemographics {
  age: string;
  gender: string;
  overall: string;
  ageGender: string;
  income: string;
  behaviour: string;
  interest: string;
  highestIndexScore: string;
}

// Inventory detail interface
export interface MapInventoryDetail {
  id: string;
  externalId: string;
  referenceId: string;
  mediaOwnerId: string;
  mediaOwnerName: string;
  inventoryType: string;
  thumbnail: string;
  images: string[];
  format: string;
  /** Panel size letter code, e.g. "xl" for extra large. */
  size: string;
  operationMode: string;
  execution: string;
  screens: number;
  sov: number;
  isSelected: boolean;
  isCompliant: boolean;
  panels: Panels[];
}

// Inventory location interface
export interface MapInventoryLocation {
  location: Location;
  poi: POI;
  demographics: InventoryDemographics;
}

// Performance interface
export interface Performance {
  cpmRate: number;
  estimatedCost: number;
  perDayCost: number;
  perDayAdPlays: number;
  totalAdPlays: number;
}

// Operations interface
export interface Operations {
  startTime?: string;
  endTime?: string;
  operationDays: Array<dayString>;
  operationHours?: Record<dayString, Array<{ start: string; end: string }>>;
  maintenanceWindow: string;
  loopSize: number;
  slotDuration: number;
  clientPerLoop: number;
  cycleTime: number;
}

// Map inventory item interface (main interface)
export interface MapInventoryItem {
  id?: string;
  detail: MapInventoryDetail;
  location: MapInventoryLocation;
  performance?: Performance;
  operations?: Operations;
  poi?: string[];
  metadata?: Record<string, string>;
  radius?: number;
}

export interface InventorySchedulePayload {
  name: string;
  startDate: string;
  endDate: string;
  scheduleDays: Array<dayString>;
  bookingMatrix: Record<string, number[]>;
  duration: number;
  spotsPerLoop: number;
  spotsPerHour: number;
  order: number;
  pricing: number;
  inventoryId?: string;
}

export interface InventorySchedule {
  startDate: string;
  endDate: string;
  scheduleDays: Array<dayString>;
  bookingMatrix: Record<string, number[]>;
  duration: number;
  spotsPerHour: number;
  spotsPerLoop: number;
  impressions: number;
  reach?: number;
  frequency?: number;
  basePrice?: number;
  adPlays: number;
  sov: number;
  sot: number;
  plannedSot: number;
  name?: string; // Optional name field (e.g., "Schedule 1", "Schedule 2")
  id?: string;
  order: number;
  pricing: number;
  discount?: { value: number; discountType: string } | null;
  bonusType?: string;
  inventoryId?: string;
}

export interface InventorySchedules {
  inventoryId: string;
  schedules: Array<InventorySchedule>;
}

export type ScheduleDays = InventorySchedule["scheduleDays"];

// Campaign Schedule Prices API types
export interface CampaignSchedulePriceTimeslot {
  startTime: string;
  endTime: string;
}

export interface CampaignSchedulePriceSchedule {
  id: string;
  name: string;
  startDate: string;
  endDate: string;
  scheduleDays: dayString[];
  type: string;
  bookingMatrix: Record<string, number[]>;
  duration: number;
  spotsPerLoop: number;
  spotsPerHour: number;
  sov: number;
  adPlays: number;
  plannedSot: number;
  totalSot: number;
  order: number;
  currentRate: number;
  proposedRate: number;
  discount?: { value: number; discountType: string } | null;
  bonusType?: string;
}

export interface CampaignSchedulePriceItem {
  id: string;
  inventoryId: string;
  inventoryName: string;
  startDate: string;
  endDate: string;
  timeslot: CampaignSchedulePriceTimeslot;
  sov: number;
  adPlays: number;
  currentRate: number;
  proposedRate: number;
  mediaOwnerId: string;
  mediaOwnerName: string;
  monthlyRateCard: number;
  weeklyRateCard: number;
  dailyRate: number;
  cpmRate: number;
  discountPercent: number;
  bonusType?: string;
  reach: number;
  schedules: CampaignSchedulePriceSchedule[];
  /** Cinema buy attributes — present only for cinema inventory. Rendered as a
   * secondary line (operator · hall · showtime) under the inventory name in
   * Price Management. Absent for non-cinema rows. */
  cinemaFields?: CinemaFields;
}

export interface CampaignSchedulePriceResponse {
  totalElements: number;
  totalPages: number;
  size: number;
  content: CampaignSchedulePriceItem[];
  number: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
  sort: SortInfo;
  pageable: PageableInfo;
  empty: boolean;
}

export interface CampaignSchedulePriceFilters {
  name?: string;
  cities?: string[];
  inventoryTypes?: string[];
  mediaOwnerIds?: string[];
  minPricing?: number;
  maxPricing?: number;
}

export interface CampaignSchedulePriceParams {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: "asc" | "desc" | "ASC" | "DESC";
  filters?: CampaignSchedulePriceFilters;
}

// Apply Schedule Adjustment API types
export interface ScheduleAdjustmentDiscount {
  discountType: "PERCENTAGE" | "VALUE";
  value: string;
}

// Update Inventory Discount API types
export interface UpdateInventoryDiscountRequest {
  proposedPrice: number;
  scheduleId?: string;
}

export interface ScheduleAdjustmentRequest {
  scheduleIds: string[];
  actionType: "DISCOUNT" | "BONUS";
  discount?: ScheduleAdjustmentDiscount;
  bonus?: string;
}

// Price History API types
export interface PriceHistoryItem {
  oldPrice: number;
  newPrice: number;
  action: string;
  userId: string;
  companyId: string;
  createdBy: string;
  role: string;
  createdAt: string;
}

export interface PriceHistoryResponse {
  content: PriceHistoryItem[];
  pageable: {
    pageNumber: number;
    pageSize: number;
    sort: {
      orders: Array<{
        direction: string;
        property: string;
        ignoreCase: boolean;
        nullHandling: string;
        descending: boolean;
        ascending: boolean;
      }>;
      empty: boolean;
      sorted: boolean;
      unsorted: boolean;
    };
    offset: number;
    paged: boolean;
    unpaged: boolean;
  };
  total: number;
  last: boolean;
  totalElements: number;
  totalPages: number;
  sort: {
    orders: Array<{
      direction: string;
      property: string;
      ignoreCase: boolean;
      nullHandling: string;
      descending: boolean;
      ascending: boolean;
    }>;
    empty: boolean;
    sorted: boolean;
    unsorted: boolean;
  };
  first: boolean;
  size: number;
  number: number;
  numberOfElements: number;
  empty: boolean;
}

export interface PriceHistoryParams {
  campaignInventoryScheduleId: string;
  page?: number;
  size?: number;
}

// Price Summary API types
export interface PriceSummaryCustomFee {
  id: string;
  name: string;
  description: string;
  type: string;
  value: number;
  basedOn: string;
  isIncludeInMediaPlan: boolean;
  isActive: boolean;
  companyId: string;
  campaignId: string;
  createdAt: string;
  updatedAt: string;
  effectiveCustomFee: number;
}

export interface PriceSummaryResponse {
  currentPrice: number;
  proposedPrice: number;
  changeInPrice: number;
  changeInPercentage: number;
  mediaCost: number;
  discountedMediaCost: number;
  standardFees: number;
  customFees: PriceSummaryCustomFee[];
  isAllApproved: boolean;
}

export interface InventoryMappingItem {
  name: string;
  latitude: string;
  longitude: string;
  billboardName: string;
  referenceId: string;
  distanceMeters: number;
  stateName: string;
  districtName: string;
}

export interface ContentExclusion {
  createdAt: string;
  createdBy: string;
  name: string;
  taxonomyId: string;
  version: string;
}

export interface CreativeFormat {
  createdAt: string;
  createdBy: string;
  creativeType: string;
  format: string;
}

export interface DSP {
  createdAt: string;
  createdBy: string;
  dspId: number;
  dspName: string;
}

export interface ExternalId {
  createdAt: string;
  createdBy: string;
  externalId: string;
  platform: string;
}

export interface Media {
  url: string;
}

export interface Panel {
  cardinalDirection: string;
  createdAt: string;
  createdBy: string;
  id: string;
  orientation: string;
  physicalHeight: number;
  physicalWidth: number;
  pixelHeight: number;
  pixelWidth: number;
  screenCount: number;
  supportsAudio: boolean;
  supportsTouch: boolean;
  updatedAt: string;
  updatedBy: string;
}

export interface Price {
  cpm: number;
  cps: number;
  createdAt: string;
  createdBy: string;
  currency: string;
}

export interface ProgrammaticDealType {
  createdAt: string;
  createdBy: string;
  name: string;
  updatedAt: string;
  updatedBy: string;
}

export interface Schedule {
  createdAt: string;
  createdBy: string;
  notes: string;
  operatingTimes: Record<string, OperatingTime[]>;
  updatedAt: string;
  updatedBy: string;
}

export interface SellingTerm {
  createdAt: string;
  createdBy: string;
  dayPartGroups: Record<string, DayPartGroup>;
  leadDays: number;
  minConsecutiveDays: number;
  minConsecutiveHoursPerDay: number;
  minDays: number;
  minHours: number;
  minSpotsPerDay: number;
  updatedAt: string;
  updatedBy: string;
}

export interface Tag {
  createdAt: string;
  createdBy: string;
  hexColor: string;
  id: string;
  mediaOwnerId: string;
  name: string;
  updatedAt: string;
  updatedBy: string;
}
export interface DayPartGroup {
  end: string;
  start: string;
}

export interface OperatingTime {
  end: string;
  start: string;
}

export interface Venues {
  createdAt: string;
  createdBy: string;
  name: string;
  path: string;
  taxonomyId: string;
  version: string;
}

export interface DigitalFields {
  bookingMode: string;
  carbonEmissionRate: number;
  creativeSpecificationUrl: string;
  loopDuration: number;
  loopsPerHour: number;
  playerCount: number;
  playerSoftwareId: number;
  playerSoftwareName: string;
  spotDuration: number;
  spotsPerLoop: number;
}

export interface ClassicFields {
  illuminated: boolean;
}
export interface InventoryDetailsResponse {
  address: string;
  adminLevel0Id: string;
  adminLevel0Name: string;
  adminLevel1Id: string;
  adminLevel1Name: string;
  adminLevel2Id: string;
  adminLevel2Name: string;
  adminLevel3Id: string;
  adminLevel3Name: string;
  adminLevel4Id: string;
  adminLevel4Name: string;
  archived: boolean;
  classicFields: ClassicFields;
  contentExclusions: ContentExclusion[];
  createdAt: string;
  createdBy: string;
  creativeFormats: CreativeFormat[];
  description: string;
  digitalFields: DigitalFields;
  displayFormatId: number;
  displayFormatName: string;
  dsps: DSP[];
  environment: string;
  externalIds: ExternalId[];
  geoms: string[];
  id: string;
  languagePreferences: string[];
  mediaOwnerId: string;
  mediaOwnerName: string;
  medias: Media[];
  name: string;
  panels: Panel[];
  postalCode: string;
  prices: Price[];
  programmaticDealTypes: string[];
  referenceId: string;
  requiresContentApproval: boolean;
  scale: string;
  schedule: Schedule;
  sellingTerm: SellingTerm;
  size: string;
  tags: Tag[];
  thumbnailUrl: string;
  thumbnailUrlOriginal: string;
  timeZone: string;
  transitFields: {
    routeId: string;
    routeName: string;
  };
  typeName: string;
  typePath: string;
  updatedAt: string;
  updatedBy: string;
  venues: Venues[];
  viewingDistance: number;
  /** Cinema buy attributes — present only for cinema inventory. */
  cinemaFields?: CinemaFields;
}
