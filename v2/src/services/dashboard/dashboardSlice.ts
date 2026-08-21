import axiosBaseQuery, { SuccessResponse } from "@api/axiosBaseQuery";
import { createApi } from "@reduxjs/toolkit/query/react";

// Widget types
export interface DashboardWidget {
  key: string;
  isEnable: boolean;
}

export interface DashboardWidgetsResponse {
  data: DashboardWidget[];
}

export interface UpdateWidgetsRequest {
  widgets: DashboardWidget[];
}

// Campaign Overview types
export interface CampaignOverviewByStatus {
  totalCampaigns: number;
  draftCampaigns: number;
  plannedCampaigns: number;
  reviewingCampaigns: number;
  negotiatingCampaigns: number;
  pendingCampaigns: number;
  approvedCampaigns: number;
  dealRequestedCampaigns: number;
  activeCampaigns: number;
  pauseCampaigns: number;
  completedCampaigns: number;
  rejectedCampaigns: number;
  archivedCampaigns: number;
}

export interface CampaignOverviewByStatusParams {
  startDate: string;
  endDate: string;
  companyId: string;
}

// Campaign Performance types
export interface CampaignPerformanceItem {
  id: string;
  name: string;
  userName: string;
  status: string;
  goals: {
    goalType: string;
    targetValue: number;
    typeName: string;
  };
  startDate: string;
  endDate: string;
  budget: number;
  currency: string;
  inventory: number;
  totalCost: number;
  estimatedImpression: number;
  estimatedReach: number;
  sov: number;
  totalSot: number;
  plannedSot: number;
  agencyName?: string;
  brandName?: string;
}

export interface CampaignPerformanceParams {
  startDate: string;
  endDate: string;
  sortBy?: string;
  sortDir?: string;
  status?: string;
  companyId: string;
}

// Performance summary: API returns wrapper with dateWiseSchedulePerDateRate and summary metrics
export type DayWiseMetricData = Record<string, Record<string, number>>;

export interface PerformanceSummarySummary {
  totalBudget?: number;
  lastPeriodTotalBudget?: number;
  totalCost?: number;
  remainingBudget?: number;
  averageRevenuePerUnit?: number;
  lastPeriodAverageRevenuePerUnit?: number;
  conversionRate?: number;
  lastPeriodConversionRate?: number;
  totalRevenue?: number;
  lastPeriodTotalRevenue?: number;
}

export interface PerformanceSummaryResponse {
  dateWiseSchedulePerDateRate?: DayWiseMetricData;
  currencyCode: string;
  totalBudget?: number;
  lastPeriodTotalBudget?: number;
  lastPeriodTotalCost?: number;
  totalCost?: number;
  remainingBudget?: number;
  averageRevenuePerUnit?: number;
  lastPeriodAverageRevenuePerUnit?: number;
  conversionRate?: number;
  lastPeriodConversionRate?: number;
  totalRevenue?: number;
  lastPeriodTotalRevenue?: number;
}

export interface PerformanceSummaryParams {
  startDate: string;
  endDate: string;
  type: "reach" | "cost";
  companyId: string;
  statuses?: string;
}

// Sales Performance Summary types
export interface TopCampaign {
  campaignId: string;
  campaignName: string;
  cost: number;
  revenue: number;
}

export interface SalesPerformanceSummaryItem {
  country?: string;
  city?: string;
  name?: string;
  agency?: string;
  team?: string;
  inventories: number;
  utilization: number;
  conversion: number;
  countCampaigns: number;
  cost: number;
  revenue?: number;
  share?: number;
  adPlays?: number;
  impressions?: number;
  topCampaigns?: TopCampaign[];
  sov?: number;
  region?: string;
  topCampaignsString?: string;
}

export interface SalesPerformanceSummaryResponse {
  content: SalesPerformanceSummaryItem[];
  pageable: {
    pageNumber: number;
    pageSize: number;
    sort: {
      orders: unknown[];
      unsorted: boolean;
      sorted: boolean;
      empty: boolean;
    };
    offset: number;
    paged: boolean;
    unpaged: boolean;
  };
  total: number;
  totalPages: number;
  totalElements: number;
  last: boolean;
  numberOfElements: number;
  sort: {
    orders: unknown[];
    unsorted: boolean;
    sorted: boolean;
    empty: boolean;
  };
  first: boolean;
  size: number;
  number: number;
  empty: boolean;
}

export interface SalesPerformanceSummaryParams {
  startDate: string;
  endDate: string;
  showBy: "country" | "city" | "advertiser" | "agency" | "team";
  companyId: string;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: "asc" | "desc";
}

// Dashboard API
export const dashboardApi = createApi({
  reducerPath: "dashboardApi",
  baseQuery: axiosBaseQuery(),
  // Dashboard data must always reflect current truth (campaign status changes,
  // other users' edits, etc. can happen at any time) — don't retain cached
  // results once a widget unsubscribes, so every mount/date-period change
  // fetches fresh instead of silently reusing a stale entry.
  keepUnusedDataFor: 0,
  tagTypes: [
    "DashboardWidgets",
    "CampaignOverview",
    "CampaignPerformance",
    "PerformanceSummaryReach",
    "PerformanceSummaryCost",
    "SalesPerformanceSummary",
  ],
  endpoints: (builder) => ({
    getDashboardWidgets: builder.query<
      SuccessResponse<DashboardWidget[]>,
      void
    >({
      query: () => ({
        url: `/dashboard/widgets`,
        method: "GET",
      }),
      providesTags: ["DashboardWidgets"],
    }),
    updateDashboardWidgets: builder.mutation<
      SuccessResponse<unknown>,
      DashboardWidget[]
    >({
      query: (widgets) => ({
        url: `/dashboard/widgets`,
        method: "PUT",
        data: widgets,
      }),
      invalidatesTags: ["DashboardWidgets"],
    }),
    getCampaignOverviewByStatus: builder.query<
      SuccessResponse<CampaignOverviewByStatus>,
      CampaignOverviewByStatusParams
    >({
      query: (params) => ({
        url: `/dashboard/campaign-overview-by-status`,
        method: "GET",
        params: {
          startDate: params.startDate,
          endDate: params.endDate,
          companyId: params.companyId,
        },
      }),
      providesTags: ["CampaignOverview"],
    }),
    getCampaignPerformance: builder.query<
      SuccessResponse<CampaignPerformanceItem[]>,
      CampaignPerformanceParams
    >({
      query: (params) => ({
        url: `/dashboard/campaign-performance`,
        method: "GET",
        params: {
          startDate: params.startDate,
          endDate: params.endDate,
          ...(params.sortBy && { sortBy: params.sortBy }),
          ...(params.sortDir && { sortDir: params.sortDir }),
          ...(params.status && { status: params.status }),
          ...(params.companyId && { companyId: params.companyId }),
        },
      }),
      providesTags: ["CampaignPerformance"],
    }),
    getPerformanceSummaryReach: builder.query<
      SuccessResponse<PerformanceSummaryResponse>,
      Omit<PerformanceSummaryParams, "type">
    >({
      query: (params) => ({
        url: `/dashboard/performance-summary`,
        method: "GET",
        params: {
          startDate: params.startDate,
          endDate: params.endDate,
          type: "reach",
          companyId: params.companyId,
          ...(params.statuses && { statuses: params.statuses }),
        },
      }),
      providesTags: ["PerformanceSummaryReach"],
    }),
    getPerformanceSummaryCost: builder.query<
      SuccessResponse<PerformanceSummaryResponse>,
      Omit<PerformanceSummaryParams, "type">
    >({
      query: (params) => ({
        url: `/dashboard/performance-summary`,
        method: "GET",
        params: {
          startDate: params.startDate,
          endDate: params.endDate,
          type: "cost",
          companyId: params.companyId,
        },
      }),
      providesTags: ["PerformanceSummaryCost"],
    }),
    getSalesPerformanceSummary: builder.query<
      SuccessResponse<SalesPerformanceSummaryResponse>,
      SalesPerformanceSummaryParams
    >({
      query: (params) => ({
        url: `/dashboard/sales-performance-summary`,
        method: "GET",
        params: {
          startDate: params.startDate,
          endDate: params.endDate,
          showBy: params.showBy,
          companyId: params.companyId,
          page: params.page ?? 0,
          size: params.size ?? 10,
          ...(params.sortBy && { sortBy: params.sortBy }),
          ...(params.sortDir && { sortDir: params.sortDir }),
        },
      }),
      providesTags: ["SalesPerformanceSummary"],
    }),
  }),
});

export const {
  useGetDashboardWidgetsQuery,
  useLazyGetDashboardWidgetsQuery,
  useUpdateDashboardWidgetsMutation,
  useGetCampaignOverviewByStatusQuery,
  useGetCampaignPerformanceQuery,
  useLazyGetCampaignPerformanceQuery,
  useGetPerformanceSummaryReachQuery,
  useGetPerformanceSummaryCostQuery,
  useGetSalesPerformanceSummaryQuery,
} = dashboardApi;
