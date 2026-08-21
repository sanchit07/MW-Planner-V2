// Campaign creation interfaces based on API specification

import { UserCompanyType } from "@services/user/userSlice";

import { CampaignForecastData } from "./inventory.types";

export interface Demographics {
  age: string[];
  gender: string[];
  venues: string[];
  behavior: string[];
  income: string[];
  interests: string[];
}

// Geometry interface for drawn shapes (polygons, lines, etc.)
export interface MapGeometry {
  id: string;
  type: "Polygon" | "LineString" | "Point";
  name: string;
  poi?: Array<string>;
  coordinates: number[][]; // Array of coordinate pairs [[lng, lat], ...] or GeoJSON format for complex polygons
  included: boolean;
  isShape: boolean; // Always true for geometries (not saved to payload, computed at runtime)
  radius?: number; // For circles stored as polygons
  center?: { lat: number; lng: number }; // For circles
  metadata?: Record<string, string>; // Additional metadata for geometries
}

// Map marker location interface (from search results or circles)
export interface MapMarkerLocation {
  id: string;
  lat: number;
  lng: number;
  name: string;
  poi?: Array<string>;
  address: string;
  radius?: number; // For location-based circles
  included: boolean;
  isShape: boolean;
  metadata?: Record<string, string>; // True for circles (has radius), false for search locations (not saved to payload, computed at runtime)
}

export interface Geofencing {
  geometries: MapGeometry[];
  locations: MapMarkerLocation[];
}

export interface Targeting {
  demographics: Demographics;
  geofencing: Geofencing;
  signals: string[];
  // Venue types picked on the Targeting step, split by media channel.
  venueTypes?: {
    digitalOoh?: string[];
    classicOoh?: string[];
  };
  // Inventory cluster checkboxes picked on the Targeting step (e.g.
  // ["DIGITAL", "DIGITAL_NETWORK", "CLASSIC_TRANSIT"]).
  inventoryCluster?: string[];
}

export interface ScheduleTargeting {
  weekdayDistribution: Record<string, number>;
  daypartDistribution: Record<string, number>;
}

export interface BudgetAllocation {
  digital: number;
  transit: number;
  retail: number;
  classic: number;
  cinema?: number;
}

export interface Goals {
  goalType: string;
  targetName: string;
  targetValue: number;
}

export interface Optimization {
  budgetAllocation: Record<string, string>;
  schedule: Record<string, string>;
  autoOptimize: boolean;
}

export interface CustomFee {
  name: string;
  amount: number;
  includeInPlan: boolean;
  description: string;
}

export interface CampaignBrand {
  id: string;
  name: string;
  categories?: Array<{
    id: string;
    name: string;
    fullPath?: string;
    tier?: number;
  }>;
}

export interface CampaignAgency {
  id: string;
  name: string;
}

export interface CampaignCreateRequest {
  name: string;
  description?: string;
  status: string;
  budget?: number;
  currency?: string;
  startDate: string;
  endDate: string;
  brand?: CampaignBrand;
  clientType: string;
  agency?: CampaignAgency;
  goals?: Goals;
  targeting?: Targeting;
  scheduleTargeting?: ScheduleTargeting;
  budgetAllocation?: BudgetAllocation;
  optimization?: Optimization;
  customFees?: CustomFee[];
  currentCompanyId?: string;
  currentCompanyName?: string;
  companyId?: string;
  mediaChannels?: string[];
  dsp?: string | null;
  seatId?: string;
}

export interface CampaignPerformance {
  totalInventories: number;
  estimatedImpression: number;
  estimatedReach: number;
  totalCost: number;
  estimatedFrequency: number;
  estimatedAdPlays: number;
  avgCpm: number;
  avgECpm: number;
  sov: number;
  plannedSot: number;
  totalSot: number;
}

export interface CampaignCreateResponse {
  id: string;
  planNumber?: string;
  name: string;
  description?: string;
  status: string;
  budget?: number;
  countryId: string;
  currency?: string;
  startDate: string;
  endDate: string;
  brand?: CampaignBrand;
  agency?: CampaignAgency;
  clientType: string;
  goals?: Goals;
  targeting?: Targeting;
  scheduleTargeting?: ScheduleTargeting;
  budgetAllocation?: BudgetAllocation;
  optimization?: Optimization;
  customFees?: CustomFee[];
  createdAt: string;
  updatedAt: string;
  inventoryCount?: number;
  skipRecommendation?: boolean;
  currentCompanyId?: string;
  currentCompanyName?: string;
  performance?: CampaignPerformance;
  mediaChannels?: string[];
  dsp?: string | null;
  isNegotiated?: boolean;
  dataMode?: "live" | "demo";
}

// Agencies API interfaces
export interface Country {
  id: string;
  name: string;
}

export interface State {
  id: string;
  name: string;
}

export interface District {
  id: string;
  name: string;
}

export interface Tax {
  label: string;
  percent: number;
}

export interface LocationDetails {
  street: string;
  country: Country;
  state: State;
  district: District;
  tax: Tax;
  postalCode: number;
}

export interface ContactDetails {
  website: string;
  registrationNumber: string;
  phoneNumber: string;
  faxNumber: string;
  companyEmail: string;
  notificationEmail: string;
  gstNumber: string;
}

export interface Brand {
  name: string;
  category: string;
  iabId: string;
  logo: string;
}

export interface Agency {
  id: string;
  name: string;
  countryId: string;
  countryName: string;
  activated: boolean;
  mediaOwnerId: string;
  mediaOwnerName: string;
  contactDetails: ContactDetails;
  locationDetails: LocationDetails;
  brandRefId: number;
  companyId: string;
  seatId: number;
  createdAt: string;
  updatedAt: string;
}

export interface SortOrder {
  direction: string;
  property: string;
  ignoreCase: boolean;
  nullHandling: string;
  ascending: boolean;
  descending: boolean;
}

export interface Sort {
  orders: SortOrder[];
  empty: boolean;
  sorted: boolean;
  unsorted: boolean;
}

export interface Pageable {
  pageNumber: number;
  pageSize: number;
  sort: Sort;
  offset: number;
  paged: boolean;
  unpaged: boolean;
}

export interface AgenciesResponse {
  data: Agency[];
  message: string;
  meta: Meta;
  success: boolean;
}

export interface Meta {
  limit: number;
  offset: number;
  total?: number;
}

export interface AgenciesQueryParams {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
  search?: string;
  company_id?: string;
  // When true, searches the global agency directory (all companies) instead
  // of scoping to company_id — used to find an existing agency to link
  // rather than creating a duplicate.
  all?: boolean;
}

// Create Agency API interfaces
export interface CreateAgencyRequest {
  name: string;
  companyEmail: string;
  domain?: string;
  seatId?: number;
  brandRefId?: string;
}

export interface CreateAgencyResponse {
  data: CreateAgencyData;
  message: string;
  success: boolean;
}

export interface CreateAgencyData {
  created_at: string;
  domain: string;
  external_id: string;
  id: string;
  is_active: boolean;
  linked_companies: LinkedCompany[];
  name: string;
  notification_email: string;
  seat_id: number;
  status: string;
}

export interface LinkedCompany {
  campaign_approval: string;
  company_type: string;
  company_type_code: string;
  creative_approval: string;
  domain: string;
  external_id: string;
  id: string;
  link_id: string;
  link_status: string;
  linked_at: string;
  name: string;
  seat_id: string;
  status: string;
}

export interface LinkAgencyRequest {
  agency_id: string;
  agency_ids?: string[];
  campaign_approval?: string;
  creative_approval?: string;
}

export interface LinkAgencyResponse {
  data: LinkAgencyResponseData;
  message: string;
  success: boolean;
}

export interface LinkAgencyResponseData {
  agency: CreateAgencyData;
  campaign_approval: string;
  creative_approval: string;
  link_id: string;
  linked_at: string;
}

// /companies/{id}/children is not paginated — returns every child company.
export interface ChildCompany {
  children: ChildCompanyDetails[];
  count: number;
}

export interface ChildCompanyDetails {
  // The API sometimes returns this as null (e.g. the linked company record
  // was deleted) — consumers must filter these out rather than assume it exists.
  company: ChildCompanyDetailsCompany | null;
  access_level: string;
  linked_at: string;
  allow_reporting_access: boolean;
  allow_billing_view: boolean;
  allow_inventory_management: boolean;
}

export interface ChildCompanyDetailsCompany {
  id: string;
  name: string;
  domain?: string;
  company_type: UserCompanyType;
}

// Countries API interfaces
export interface CountryTax {
  label?: string;
  percent: number;
}

export interface CountryItem {
  id: string;
  countryId: string;
  name: string;
  latitude: number;
  longitude: number;
  zoom: number;
  population: number;
  iso: string;
  postalformat: string;
  postalname: string;
  active: boolean;
  dialingCode?: string;
  tax: CountryTax;
  updatedAt: string;
}

export interface MobilityPoint {
  lat: number;
  lng: number;
  /** Normalized footfall weight, 0..1. */
  weight: number;
}
export interface CountriesResponse {
  content: CountryItem[];
  pageable: Pageable;
  total: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
  size: number;
  number: number;
  numberOfElements: number;
  sort: Sort;
  first: boolean;
  empty: boolean;
}

export interface CountryMarketDetails {
  id: string;
  countryId: string;
  countryName: string;
  population: number;
  inventoryCount: number;
  impressions: number;
  inventoryCountByClassification?: Record<string, number>;
}

export interface ChildCompanyItem {
  company: {
    id: string;
    name: string;
    domain: string;
    company_type: {
      id: string;
      name: string;
      code: string;
      is_supplier_side: boolean;
      is_demand_side: boolean;
      contract: boolean;
    };
  };
  access_level: string;
  linked_at: string;
  allow_reporting_access: boolean;
  allow_billing_view: boolean;
  allow_inventory_management: boolean;
}

export interface ChildCompaniesResponse {
  has_more: boolean;
  items: ChildCompanyItem[];
  limit: number;
  offset: number;
  total_count: number;
}

export interface CompanyMarketAccessItem {
  id: string;
  company_id: string;
  country_id: string;
  country_name: string;
  country_code: string;
  is_active: boolean;
}

export interface CompanyMarketAccessResponse {
  company_id: string;
  markets: CompanyMarketAccessItem[];
}

export interface CountriesQueryParams {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
}

// Companies/Media Owners API interfaces
export interface Company {
  id: string;
  name: string;
  child: unknown[];
  activated: boolean;
  companyId: string;
  contactEmail: string;
  contactPhone?: string;
  businessType: string;
  accountType: string;
  registeredCountry: string;
  marketAccess: unknown[];
  updatedAt: string;
}

export interface CompaniesResponse {
  data: Company[];
  message: string;
  success: boolean;
  meta: {
    limit: number;
    offset: number;
    total: number;
  };
}

export interface CompaniesFilterParams {
  offset?: number;
  limit?: number;
  search?: string; // Optional - only passed when user searches
  company_type: string; // Mandatory - fixed value
  country?: string; // Optional - filter by country code
}

// Get Campaigns API interfaces
export interface GetCampaignsQueryParams {
  nameContains?: string;
  statuses?: string;
  goalTypes?: string;
  userIds?: string;
  startDateFrom?: string;
  startDateTo?: string;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
  companyId?: string;
}

export interface CampaignListItem {
  id: string;
  planNumber?: string;
  name: string;
  userName: string;
  firstName: string;
  lastName: string;
  status: string;
  budget?: number;
  currency?: string;
  startDate: string;
  endDate: string;
  inventory: number;
  totalCost: number;
  estimatedImpression: number;
  estimatedReach: number;
  sov: number;
  sot: number;
  plannedSot: number;
  totalSot: number;
  brand?: CampaignBrand;
  categoryName: string;
  goals: {
    goalType: string;
    targetName: string;
    targetValue: number;
    typeName: string;
  };
  companyName: string;
  currentCompanyId?: string;
  currentCompanyName?: string;
}

export interface GetCampaignsResponse {
  totalElements: number;
  totalPages: number;
  size: number;
  content: CampaignListItem[];
  number: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
  sort: Sort;
  empty: boolean;
}

// Autosave API interfaces
export interface AutosaveRequest {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  [key: string]: any;
}

export interface AutosaveResponse {
  id: string;
  name: string;
  status: string;
  lastAutosavedAt: string;
}

export interface AutosaveParams {
  id: string;
  data: AutosaveRequest;
}

// Generic step form interface for any step component
export interface StepFormRef<TData = Record<string, unknown>> {
  submitForm: () => Promise<boolean>;
  getFormData: () => TData | null;
  isValid: () => boolean;
  validateStep: () => Promise<StepValidationResult>;
  resetForm: () => void;
  getNextLabel?: () => string;
}

// Generic step form props
export interface StepFormProps<TData = Record<string, unknown>> {
  onSubmit?: (data: TData) => void;
  initialData?: Partial<TData>;
  onValidationChange?: (isValid: boolean) => void;
  stepContext?: StepContext;
}

// Step context for sharing data between steps
export interface StepContext {
  currentStep: number;
  totalSteps: number;
  goToStep: (step: number) => void;
}

// Step validation result
export interface StepValidationResult {
  isValid: boolean;
  errors?: string[];
  warnings?: string[];
}

// Enhanced step configuration with generic typing
export interface StepConfig<TData = Record<string, unknown>> {
  id: number;
  title: string;
  subtitle: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  component: React.ComponentType<any>;
  validation?: {
    required?: boolean;
    customValidator?: (data: TData) => Promise<StepValidationResult>;
  };
  dependencies?: number[]; // Step IDs that must be completed before this step
  canSkip?: boolean;
  metadata?: {
    description?: string;
    icon?: React.ComponentType;
  };
}

// Step manager configuration
export interface StepManagerConfig {
  steps: StepConfig[];
  initialStep?: number;
  allowSkipping?: boolean;
  persistData?: boolean;
  onStepChange?: (currentStep: number, previousStep: number) => void;
  onComplete?: (allData: Record<string, Record<string, unknown>>) => void;
}

export interface ViewCampaign {
  id: string;
  planNumber?: string;
  proposalId?: string;
  name: string;
  status: string;
  currency: string;
  goals: {
    goalType: string;
    targetValue: number;
    achievedValue: number;
    weeklyBreakdown: Record<string, number>;
  };
  campaignDetail: {
    country: string;
    startDate: string;
    endDate: string;
    budget: number;
  };
  keyStakeholderDetail?: Record<string, string>;
  targeting?: {
    audienceDemographics?: {
      ageGroups?: string[];
      incomeLevel?: string[];
      interests?: string[];
      lifestyle?: string[];
    };
    geographicTargeting?: Record<string, Record<string, string | number>[]>;
  };
  inventoryOverview?: Record<string, number>;
  performance: CampaignForecastData | null;
  costBreakdown?: Record<string, number>;
  comments?: string[];
  isNegotiated?: boolean;
  dataMode?: "live" | "demo";
}

export interface CampaignCostBreakdown {
  mediaCost: number;
  platformFee: number;
  netCost: number;
  totalCustomFees: number;
  totalCost: number;
  customFees: CustomFee[];
}

export interface CostSplitByCampaignData {
  avgCpm: number;
  frequency: number;
  impressions: number;
  name: string;
  reach: number;
  totalAmount: number;
  totalAmountInPercentage: number;
  totalInventories: number;
  population?: number;
  /** Present on the CITY cost-split — the city's country. */
  country?: string;
}

export interface CampaignHistoryItem {
  id: string;
  campaignId: string;
  userId: string;
  companyId: string;
  role: string;
  createdBy: string;
  message: string;
  createdAt: string;
}

export interface CampaignHistoryResponse {
  content: CampaignHistoryItem[];
  pageable: Pageable;
  total: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
  size: number;
  number: number;
  numberOfElements: number;
  sort: Sort;
  first: boolean;
  empty: boolean;
}

export interface CampaignHistoryQueryParams {
  page?: number;
  size?: number;
}

export interface MediaPlanResponse {
  proposalId?: string;
  headerInfo: {
    id: string;
    name: string;
    startDate: string;
    endDate: string;
    budget: number;
    status: string;
    totalCost: number;
    impressions: number;
    reach: number;
    duration: number;
    currency: string;
    preparedBy: string;
    createdAt?: string;
    goalType?: string;
    companyDetails?: {
      name?: string;
      seatId?: string;
    };
    userEmail?: string;
    dsp?: string;
    targetValue?: number;
  };
  brandDetails: {
    id: string;
    name: string;
    category: string;
    categories?: Array<{
      id: string;
      name: string;
      fullPath: string;
      tier: number;
    }>;
    companyId: string;
    activated: boolean;
    description: string;
    websiteUrl: string;
    externalId: string;
    createdBy: string;
    lastModifiedBy: string;
    createdAt: string;
    updatedAt: string;
    logoUrl: string;
  };
  performanceMetrics: {
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
    warnings: string[];
  };
  audienceDemographics: {
    ageGroups: string[];
    incomeLevel: string[];
    interests: string[];
    lifestyle: string[];
  };
  geographicTargeting: {
    cities: Array<{
      name: string;
      impressions: number;
      adPlays: number;
      allocatedBudget: number;
    }>;
    venueTypes: Array<{
      name: string;
      impressions: number;
      adPlays: number;
      allocatedBudget: number;
    }>;
  };
  schedules: {
    dailySchedule: Record<string, number>;
  };
  selectedInventory: {
    summaryStatistics: {
      totalAssets: number;
      formatTypes: string[];
      totalFormatTypes: number;
      totalCities: number;
    };
    locations: Array<{
      name: string;
      country: string;
      state: string;
      city: string;
      type: string;
      impressions: number;
      cost: number;
      lat: number;
      lng: number;
      mediaOwnerName: string;
      scheduleDates: Array<{
        startDate: string;
        endDate: string;
        totalHours: number;
      }>;
      scheduleHours: Array<string[]>;
    }>;
  };
}

// Map POI Place interface (main interface)
export interface POIPlaceData {
  locationLat: number;
  locationLng: number;
  displayName: string;
  primaryType: string;
  primaryTypeDisplayName: string;
  address?: string;
  /** Google Places rating (0–5). Used to derive the popup "Busyness" label. */
  rating?: number;
}

// Campaign Approval Workflow interfaces
export interface ApprovalProgress {
  id: string;
  status: "COMPLETED" | "IN_PROGRESS" | "PENDING" | "REJECTED";
  approvalAuthority: "AGENCY" | "INTERNAL" | "MEDIA_OWNER";
  comment?: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  updatedBy: string;
  isExpanded?: boolean;
}

export interface CampaignApprovalDetails {
  campaignName: string;
  campaignId: string;
  planNumber?: string;
  status: string;
  budget: number;
  currency?: string;
  startDate?: string;
  endDate?: string;
  approvalProgress: ApprovalProgress[];
  approvalPermissions: string[];
  /** Per-media-owner progress on the Media Owner stage. Buyer-side viewers only. */
  mediaOwners?: MediaOwnerProgress[] | null;
  /** The viewing media owner's own slice (status, inventory count, media cost). */
  viewerProposal?: MediaOwnerProgress | null;
}

// ----- Plan Approval inbox -----

export interface ApprovalInboxItem {
  campaignId: string;
  campaignName: string;
  planNumber?: string;
  status: string;
  workflowStatus?: string | null;
  budget?: number | null;
  currency?: string | null;
  startDate?: string;
  endDate?: string;
  isNegotiated?: boolean | null;
  awaitingAuthority?: "AGENCY" | "INTERNAL" | "MEDIA_OWNER" | null;
  canAct: boolean;
  actionProgressId?: string | null;
  permissions?: string[];
  hasUnacceptedPrices: boolean;
  /** True when the viewer participates as a media owner on this external plan. */
  viewerIsMediaOwner?: boolean;
  /** Creator company name (populated for media-owner viewers). */
  createdByCompanyName?: string | null;
  /** Per-media-owner progress — buyer-side viewers only. */
  mediaOwners?: MediaOwnerProgress[] | null;
  /** The viewing media owner's own slice — media-owner viewers only. */
  viewerProposal?: MediaOwnerProgress | null;
}

export interface MediaOwnerProgress {
  mediaOwnerId: string;
  mediaOwnerName?: string | null;
  status?: "PENDING" | "APPROVED" | "NEGOTIATING" | "REJECTED" | null;
  inventoryCount: number;
  mediaCost?: number | null;
  hasOpenCounterOffer?: boolean;
}

// ----- Execution Plan -----

export interface ExecutionPlanInventoryItem {
  id: string;
  name?: string;
  classification?: string;
  type?: string;
  format?: string;
}

export interface ExecutionPlanLine {
  id: string;
  mediaOwnerId: string;
  mediaOwnerName?: string;
  classification: "DIGITAL" | "CLASSIC";
  destination: "INFLUENCE" | "OMS";
  purchaseType: "GUARANTEED" | "DIRECT" | "ORDER";
  inventoryCount: number;
  inventories: ExecutionPlanInventoryItem[];
  plannedCost?: number | null;
  plannedImpressions?: number | null;
  handoffStatus:
    | "PENDING_HANDOFF"
    | "QUEUED"
    | "SENT"
    | "ACKNOWLEDGED"
    | "FAILED";
  handoffError?: string | null;
  handedOffAt?: string | null;
  attemptCount?: number | null;
}

export interface ExecutionPlanSummary {
  lineCount: number;
  inventoryCount: number;
  totalPlannedCost?: number | null;
  totalPlannedImpressions?: number | null;
  queuedCount: number;
  sentCount: number;
  acknowledgedCount: number;
  failedCount: number;
}

export interface ExecutionPlanResponse {
  campaignId: string;
  campaignName?: string;
  campaignStatus?: string;
  budget?: number | null;
  currency?: string | null;
  locked: boolean;
  pushedAt?: string | null;
  canPush: boolean;
  pushBlockedReason?: "NOT_APPROVED" | "UNACCEPTED_PRICES" | "NO_LINES" | null;
  summary: ExecutionPlanSummary;
  lines: ExecutionPlanLine[];
}

// ---- Media-owner Execution Workspace ----

export interface ExecutionTimelineDay {
  date: string;
  capacity: number;
  bookedOwn: number;
  bookedOther: number;
  free: number;
}

export interface ExecutionWorkspaceInventory {
  id: string;
  name?: string;
  classification?: string;
  type?: string;
  format?: string;
  spotsPerLoop?: number | null;
  approvedCost?: number | null;
  plannedImpressions?: number | null;
  plannedAdPlays?: number | null;
  scheduleStart?: string | null;
  scheduleEnd?: string | null;
  impressionsPerSpotPerDay?: number | null;
  potentialImpressions?: number | null;
  timeline: ExecutionTimelineDay[];
}

export interface ExecutionWorkspaceLine {
  id: string;
  classification: "DIGITAL" | "CLASSIC";
  destination: "INFLUENCE" | "OMS";
  purchaseType: "GUARANTEED" | "DIRECT" | "ORDER";
  inventoryIds: string[];
  plannedCost?: number | null;
  plannedImpressions?: number | null;
  targetImpressions?: number | null;
  floorRate?: number | null;
  handoffStatus:
    | "PENDING_HANDOFF"
    | "QUEUED"
    | "SENT"
    | "ACKNOWLEDGED"
    | "FAILED";
  handoffError?: string | null;
  handedOffAt?: string | null;
  capacityImpressions?: number | null;
}

export interface ExecutionWorkspaceSummary {
  approvedCost?: number | null;
  plannedImpressions?: number | null;
  potentialImpressions?: number | null;
  plannedAdPlays?: number | null;
  inventoryCount: number;
  lineCount: number;
  committedImpressions?: number | null;
}

export interface ExecutionWorkspaceResponse {
  campaignId: string;
  campaignName?: string;
  planNumber?: string | null;
  campaignStatus?: string;
  agencyName?: string | null;
  goalType?: string | null;
  goalTarget?: number | null;
  startDate?: string | null;
  endDate?: string | null;
  currency?: string | null;
  approvedByViewer: boolean;
  viewerProposalStatus?: string | null;
  hasInfluenceAccess: boolean;
  locked: boolean;
  pushedAt?: string | null;
  canPush: boolean;
  pushBlockedReason?:
    | "NOT_APPROVED"
    | "UNACCEPTED_PRICES"
    | "NO_LINES"
    | "NO_INFLUENCE_ACCESS"
    | null;
  summary?: ExecutionWorkspaceSummary | null;
  inventories?: ExecutionWorkspaceInventory[] | null;
  lines?: ExecutionWorkspaceLine[] | null;
}

export type MobilityTimeBucket =
  | "ALL"
  | "MORNING"
  | "AFTERNOON"
  | "EVENING"
  | "NIGHT";

export interface ExecutionPlanStatus {
  campaignId: string;
  exists: boolean;
  locked: boolean;
  pushedAt?: string | null;
  lineCount: number;
  acknowledgedCount: number;
  failedCount: number;
  inProgressCount: number;
}

export interface MobilityHeatmapResponse {
  countryId: string;
  timeBucket: MobilityTimeBucket;
  availableTimeBuckets: string[];
  totalPoints: number;
  points: MobilityPoint[];
}
