import axiosBaseQuery, { SuccessResponse } from "@api/axiosBaseQuery";
import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import { createApi } from "@reduxjs/toolkit/query/react";
import { dashboardApi } from "@services/dashboard/dashboardSlice";

import {
  CampaignCreateRequest,
  CampaignCreateResponse,
  AutosaveParams,
  GetCampaignsQueryParams,
  GetCampaignsResponse,
  CountriesResponse,
  CountriesQueryParams,
  CountryMarketDetails,
  CountryItem,
  MobilityHeatmapResponse,
  CompaniesResponse,
  CompaniesFilterParams,
  ViewCampaign,
  CostSplitByCampaignData,
  MediaPlanResponse,
  CampaignApprovalDetails,
  CampaignHistoryResponse,
  CampaignHistoryQueryParams,
  CompanyMarketAccessResponse,
  ExecutionPlanResponse,
  ExecutionPlanStatus,
  ExecutionWorkspaceResponse,
  ApprovalInboxItem,
} from "../../types/campaign.types";
import { CampaignForecastData } from "../../types/inventory.types";

/**
 * Cached recommendation run for the inventory step. `signature` is a digest of
 * the inputs that affect the recommendation (budget, dates, goal, targeting);
 * when it still matches, the completed run is reused instead of regenerating.
 * `mediaChannels` records the channels that produced the run — when the current
 * selection differs, generation is forced (`forceRegenerate=true`) so the
 * backend rebuilds instead of returning its cached run.
 */
export interface RecommendationRun {
  runId: string;
  signature: string;
  mediaChannels: string[];
}

// Campaign state
export interface CampaignState {
  currentCampaignName: string;
  campaignId: string | null;
  isCreating: boolean;
  createError: string | null;
  // Edit mode state
  isEditMode: boolean;
  campaignData: CampaignCreateResponse | null;
  forecastData: CampaignForecastData | null;
  recommendationRun: RecommendationRun | null;
}

const initialState: CampaignState = {
  currentCampaignName: "",
  campaignId: null,
  isCreating: false,
  createError: null,
  // Edit mode initial state
  isEditMode: false,
  campaignData: null,
  forecastData: null,
  recommendationRun: null,
};

// Campaign slice
export const campaignSlice = createSlice({
  name: "campaign",
  initialState,
  reducers: {
    setCampaignId: (state, action: PayloadAction<string | null>) => {
      state.campaignId = action.payload;
    },
    setIsCreating: (state, action: PayloadAction<boolean>) => {
      state.isCreating = action.payload;
    },
    setCreateError: (state, action: PayloadAction<string | null>) => {
      state.createError = action.payload;
    },
    resetCampaignState: (state) => {
      state.isCreating = false;
      state.createError = null;
      // Reset edit mode state
      state.isEditMode = false;
      state.campaignData = null;
      state.forecastData = null;
      state.recommendationRun = null;
    },
    setForecastData: (state, action: PayloadAction<CampaignForecastData>) => {
      state.forecastData = action.payload;
    },
    setRecommendationRun: (
      state,
      action: PayloadAction<RecommendationRun | null>,
    ) => {
      state.recommendationRun = action.payload;
    },
    // Edit mode actions
    setIsEditMode: (state, action: PayloadAction<boolean>) => {
      state.isEditMode = action.payload;
    },
    setCampaignData: (state, action: PayloadAction<CampaignCreateResponse>) => {
      // Resolve brand/agency names: prefer the incoming value, but treat
      // "unknown"/"Unknown" (a backend placeholder) as missing. Fall back to
      // the previously stored name so autosave/reload don't wipe valid names.
      const isValid = (n: string | undefined | null): boolean =>
        !!n && n.toLowerCase() !== "unknown";

      const incomingBrand = action.payload.brand?.name;
      const incomingAgency = action.payload.agency?.name;
      const existingBrand = state.campaignData?.brand?.name;
      const existingAgency = state.campaignData?.agency?.name;

      const resolvedBrandName = isValid(incomingBrand)
        ? incomingBrand
        : isValid(existingBrand)
          ? existingBrand
          : incomingBrand;
      const resolvedAgencyName = isValid(incomingAgency)
        ? incomingAgency
        : isValid(existingAgency)
          ? existingAgency
          : incomingAgency;

      // dsp isn't always echoed back by reload/autosave-triggered GETs — fall
      // back to the previously stored value so it doesn't get wiped out.
      const resolvedDsp = action.payload.dsp ?? state.campaignData?.dsp;

      state.campaignData = {
        ...action.payload,
        dsp: resolvedDsp,
        brand: action.payload.brand
          ? {
              ...action.payload.brand,
              name: resolvedBrandName ?? action.payload.brand.name,
            }
          : action.payload.brand,
        agency: action.payload.agency
          ? {
              ...action.payload.agency,
              name: resolvedAgencyName ?? action.payload.agency.name,
            }
          : action.payload.agency,
      };
      // Set campaign name from loaded data
      state.currentCampaignName = action.payload.name;
    },
  },
});

export const {
  setCampaignId,
  setIsCreating,
  setCreateError,
  resetCampaignState,
  // Edit mode actions
  setIsEditMode,
  setCampaignData,
  setForecastData,
  setRecommendationRun,
} = campaignSlice.actions;

// Dashboard data (campaign counts, revenue/cost/reach summaries, sales
// performance) is served by a separate RTK Query API (`dashboardApi`) with
// its own cache. Creating, finalizing, or autosaving a campaign changes the
// numbers those widgets show, so every campaign-write mutation invalidates
// them here — otherwise a user who plans a campaign and immediately checks
// the dashboard would see stale, pre-change data for up to the dashboard
// cache's `keepUnusedDataFor` window (60s default) after navigating back.
const DASHBOARD_TAGS_TO_INVALIDATE = [
  "CampaignOverview",
  "CampaignPerformance",
  "PerformanceSummaryReach",
  "PerformanceSummaryCost",
  "SalesPerformanceSummary",
] as const;

// Campaign API
export const campaignApi = createApi({
  reducerPath: "campaignApi",
  baseQuery: axiosBaseQuery(),
  tagTypes: ["CampaignsList", "ExecutionPlan", "ApprovalInbox"],
  endpoints: (builder) => ({
    getSequencer: builder.query<SuccessResponse<number>, string>({
      query: (prefix) => ({
        url: `/sequencer/${prefix}`,
        method: "GET",
      }),
    }),
    createCampaign: builder.mutation<
      SuccessResponse<CampaignCreateResponse>,
      CampaignCreateRequest
    >({
      query: (campaignData) => ({
        url: `/campaigns`,
        method: "POST",
        data: campaignData,
      }),
      async onQueryStarted(_arg, { queryFulfilled, dispatch }) {
        try {
          await queryFulfilled;
          dispatch(
            dashboardApi.util.invalidateTags([...DASHBOARD_TAGS_TO_INVALIDATE]),
          );
        } catch {
          // Mutation failed — nothing to invalidate.
        }
      },
    }),
    autosaveCampaign: builder.mutation<
      SuccessResponse<CampaignCreateResponse>,
      AutosaveParams
    >({
      query: ({ id, data }) => ({
        url: `/campaigns/${id}/autosave`,
        method: "PATCH",
        data: data,
      }),
      async onQueryStarted(_arg, { queryFulfilled, dispatch }) {
        try {
          await queryFulfilled;
          dispatch(
            dashboardApi.util.invalidateTags([...DASHBOARD_TAGS_TO_INVALIDATE]),
          );
        } catch {
          // Mutation failed — nothing to invalidate.
        }
      },
    }),
    getCampaign: builder.query<SuccessResponse<CampaignCreateResponse>, string>(
      {
        query: (campaignId) => ({
          url: `/campaigns/${campaignId}`,
          method: "GET",
        }),
      },
    ),
    updateCampaign: builder.mutation<
      SuccessResponse<CampaignCreateResponse>,
      CampaignCreateResponse
    >({
      query: (campaignData) => ({
        url: `/campaigns/${campaignData.id}`,
        method: "PUT",
        data: campaignData,
      }),
      async onQueryStarted(_arg, { queryFulfilled, dispatch }) {
        try {
          await queryFulfilled;
          dispatch(
            dashboardApi.util.invalidateTags([...DASHBOARD_TAGS_TO_INVALIDATE]),
          );
        } catch {
          // Mutation failed — nothing to invalidate.
        }
      },
    }),
    getCampaigns: builder.query<
      SuccessResponse<GetCampaignsResponse>,
      GetCampaignsQueryParams
    >({
      query: (params = {}) => ({
        url: `/campaigns`,
        method: "GET",
        params: {
          page: params.page ?? 0,
          size: params.size ?? 10,
          sortBy: params.sortBy || "updatedAt",
          sortDir: params.sortDir || "desc",
          ...(params.nameContains && { nameContains: params.nameContains }),
          ...(params.statuses && { statuses: params.statuses }),
          ...(params.goalTypes && { goalTypes: params.goalTypes }),
          ...(params.userIds && { userIds: params.userIds }),
          ...(params.startDateFrom && { startDateFrom: params.startDateFrom }),
          ...(params.startDateTo && { startDateTo: params.startDateTo }),
          ...(params.companyId && { companyId: params.companyId }),
        },
      }),
      // Tag so cache can be invalidated when view changes (refetch page 0, clear other pages)
      providesTags: ["CampaignsList"],
      // Enable automatic cancellation of stale requests
      keepUnusedDataFor: 60, // Keep data for 60 seconds
    }),
    getCountries: builder.query<
      SuccessResponse<CountriesResponse>,
      CountriesQueryParams
    >({
      query: (params = {}) => ({
        url: `/countries`,
        method: "GET",
        params: {
          page: params.page ?? 0,
          size: params.size ?? 10,
          sortBy: params.sortBy || "updatedAt",
          sortDir: params.sortDir || "desc",
        },
      }),
    }),
    // TODO: remove after confirming market-access API covers all use cases
    getCountriesMarketDetails: builder.query<
      SuccessResponse<CountryMarketDetails[]>,
      void
    >({
      query: () => ({
        url: `/countries/market-details`,
        method: "GET",
      }),
    }),
    getCountryMarketDetailsByIso: builder.query<
      SuccessResponse<CountryMarketDetails[]>,
      string
    >({
      query: (countryIso) => ({
        url: `/countries/market-details`,
        method: "GET",
        params: { countryIso },
      }),
    }),
    getCountryByName: builder.query<SuccessResponse<CountryItem>, string>({
      query: (countryId) => ({
        url: `/countries/name/${countryId}`,
        method: "GET",
      }),
    }),
    // Audience mobility heatmap points for a country (optionally one
    // time-of-day bucket). Server-side aggregated + capped, so safe to render
    // directly as a Mapbox heatmap source.
    getMobilityHeatmap: builder.query<
      SuccessResponse<MobilityHeatmapResponse>,
      { countryId: string; timeBucket?: string }
    >({
      query: ({ countryId, timeBucket }) => ({
        url: `/mobility/heatmap`,
        method: "GET",
        params: {
          countryId,
          ...(timeBucket && timeBucket !== "ALL" && { timeBucket }),
        },
      }),
      keepUnusedDataFor: 300,
    }),
    // Get companies (media owners) with filters and pagination
    filterCompanies: builder.query<
      SuccessResponse<CompaniesResponse>,
      CompaniesFilterParams
    >({
      query: (params) => {
        const {
          offset = 0,
          limit = 50,
          company_type,
          search,
          country,
        } = params;
        return {
          url: `/companies/lookup`,
          method: "GET",
          params: {
            offset,
            limit,
            company_type,
            ...(search != null && search !== "" && { search }),
            ...(country != null && country !== "" && { country }),
          },
        };
      },
    }),
    viewCampaign: builder.query<SuccessResponse<ViewCampaign>, string>({
      query: (campaignId) => ({
        url: `/campaigns/${campaignId}/view-campaign`,
        method: "GET",
      }),
    }),
    splitCostCampaign: builder.query<
      SuccessResponse<CostSplitByCampaignData[]>,
      { campaignId: string; splitBy: string; language?: string }
    >({
      query: ({ campaignId, splitBy, language }) => ({
        url: `/campaigns/${campaignId}/cost-split-by?splitBy=${splitBy}`,
        method: "GET",
        headers: language ? { "Accept-Language": language } : undefined,
      }),
    }),
    submitForReview: builder.mutation<SuccessResponse<string>, string>({
      query: (campaignId) => ({
        url: `/campaign-approval-workflow/${campaignId}/submit-for-review`,
        method: "POST",
      }),
      invalidatesTags: ["ApprovalInbox"],
    }),
    getApprovalInbox: builder.query<
      SuccessResponse<ApprovalInboxItem[]>,
      { activeCompanyId?: string } | void
    >({
      query: (args) => ({
        url: `/campaign-approval-workflow/inbox`,
        method: "GET",
        headers: args?.activeCompanyId
          ? { "X-Company-Id": args.activeCompanyId }
          : undefined,
      }),
      providesTags: ["ApprovalInbox"],
    }),
    changeProposalStatus: builder.mutation<
      SuccessResponse<{ success: boolean; message?: string }>,
      string
    >({
      query: (proposalId) => ({
        url: `/campaign-proposal/change-proposal-status/${proposalId}`,
        method: "POST",
        data: {
          status: "APPROVED",
          comment: "Approved via planner",
        },
      }),
    }),
    getMediaPlan: builder.query<SuccessResponse<MediaPlanResponse>, string>({
      query: (campaignId) => ({
        url: `/campaigns/${campaignId}/media-plan`,
        method: "GET",
      }),
    }),
    getCampaignApprovalDetails: builder.query<
      SuccessResponse<CampaignApprovalDetails>,
      { campaignId: string; activeCompanyId: string }
    >({
      query: ({ campaignId, activeCompanyId }) => ({
        url: `/campaign-approval-workflow/${campaignId}/approval-details`,
        method: "GET",
        headers: { "X-Company-Id": activeCompanyId },
      }),
    }),
    updateApprovalStatus: builder.mutation<
      SuccessResponse<unknown>,
      {
        inProgressId: string;
        status: "APPROVED" | "REJECTED";
        comment: string;
        activeCompanyId: string;
      }
    >({
      query: ({ inProgressId, status, comment, activeCompanyId }) => ({
        url: `/campaign-approval-workflow/approval-status/${inProgressId}`,
        method: "PUT",
        data: {
          status,
          comment,
        },
        headers: { "X-Company-Id": activeCompanyId },
      }),
      // An approval decision moves the workflow forward: refresh the inbox and
      // the campaigns list (campaign status may change to APPROVED/REJECTED).
      invalidatesTags: ["ApprovalInbox", "CampaignsList"],
    }),
    getCampaignHistory: builder.query<
      SuccessResponse<CampaignHistoryResponse>,
      {
        campaignId: string;
        params?: CampaignHistoryQueryParams;
        language?: string;
      }
    >({
      query: ({ campaignId, params = {}, language }) => ({
        url: `/campaigns/${campaignId}/history`,
        method: "GET",
        params: {
          page: params.page ?? 0,
          size: params.size ?? 10,
        },
        headers: language ? { "Accept-Language": language } : undefined,
      }),
    }),
    bulkActionsCampaign: builder.mutation<
      SuccessResponse<unknown>,
      { campaignIds: string[]; action: "DUPLICATE" | "ARCHIVE" | "DELETE" }
    >({
      query: ({ campaignIds, action }) => ({
        url: `/campaigns/bulk-actions`,
        method: "POST",
        data: {
          campaignIds,
          action,
        },
      }),
    }),
    deleteCampaign: builder.mutation<SuccessResponse<unknown>, string>({
      query: (campaignId) => ({
        url: `/campaigns/${campaignId}`,
        method: "DELETE",
      }),
    }),
    getExecutionPlan: builder.query<
      SuccessResponse<ExecutionPlanResponse>,
      string
    >({
      query: (campaignId) => ({
        url: `/campaigns/${campaignId}/execution-plan`,
        method: "GET",
      }),
      providesTags: (_res, _err, campaignId) => [
        { type: "ExecutionPlan" as const, id: campaignId },
      ],
    }),
    getExecutionPlanStatus: builder.query<
      SuccessResponse<ExecutionPlanStatus>,
      string
    >({
      query: (campaignId) => ({
        url: `/campaigns/${campaignId}/execution-plan/status`,
        method: "GET",
      }),
      providesTags: (_res, _err, campaignId) => [
        { type: "ExecutionPlan" as const, id: campaignId },
      ],
    }),
    // Media-owner Execution Workspace (viewer-scoped; X-Company-Id is set by the
    // axios instance from the active company). activeCompanyId is part of the
    // cache key so switching tenants refetches.
    getExecutionWorkspace: builder.query<
      SuccessResponse<ExecutionWorkspaceResponse>,
      { campaignId: string; activeCompanyId?: string }
    >({
      query: ({ campaignId, activeCompanyId }) => ({
        url: `/campaigns/${campaignId}/execution-plan/workspace`,
        method: "GET",
        ...(activeCompanyId
          ? { headers: { "X-Company-Id": activeCompanyId } }
          : {}),
      }),
      providesTags: (_res, _err, { campaignId }) => [
        { type: "ExecutionPlan" as const, id: campaignId },
      ],
    }),
    updateExecutionLine: builder.mutation<
      SuccessResponse<ExecutionWorkspaceResponse>,
      {
        campaignId: string;
        lineId: string;
        activeCompanyId?: string;
        floorRate?: number;
        targetImpressions?: number;
        purchaseType?: string;
      }
    >({
      query: ({ campaignId, lineId, activeCompanyId, ...data }) => ({
        url: `/campaigns/${campaignId}/execution-plan/lines/${lineId}`,
        method: "PATCH",
        data,
        ...(activeCompanyId
          ? { headers: { "X-Company-Id": activeCompanyId } }
          : {}),
      }),
      invalidatesTags: (_res, _err, { campaignId }) => [
        { type: "ExecutionPlan" as const, id: campaignId },
      ],
    }),
    createExecutionLine: builder.mutation<
      SuccessResponse<ExecutionWorkspaceResponse>,
      {
        campaignId: string;
        activeCompanyId?: string;
        classification: string;
        purchaseType?: string;
        floorRate?: number;
      }
    >({
      query: ({ campaignId, activeCompanyId, ...data }) => ({
        url: `/campaigns/${campaignId}/execution-plan/lines`,
        method: "POST",
        data,
        ...(activeCompanyId
          ? { headers: { "X-Company-Id": activeCompanyId } }
          : {}),
      }),
      invalidatesTags: (_res, _err, { campaignId }) => [
        { type: "ExecutionPlan" as const, id: campaignId },
      ],
    }),
    moveExecutionInventory: builder.mutation<
      SuccessResponse<ExecutionWorkspaceResponse>,
      {
        campaignId: string;
        toLineId: string;
        fromLineId: string;
        inventoryId: string;
        activeCompanyId?: string;
      }
    >({
      query: ({ campaignId, toLineId, fromLineId, inventoryId, activeCompanyId }) => ({
        url: `/campaigns/${campaignId}/execution-plan/lines/${toLineId}/move-inventory`,
        method: "POST",
        data: { fromLineId, inventoryId },
        ...(activeCompanyId
          ? { headers: { "X-Company-Id": activeCompanyId } }
          : {}),
      }),
      invalidatesTags: (_res, _err, { campaignId }) => [
        { type: "ExecutionPlan" as const, id: campaignId },
      ],
    }),
    deleteExecutionLine: builder.mutation<
      SuccessResponse<ExecutionWorkspaceResponse>,
      { campaignId: string; lineId: string; activeCompanyId?: string }
    >({
      query: ({ campaignId, lineId, activeCompanyId }) => ({
        url: `/campaigns/${campaignId}/execution-plan/lines/${lineId}`,
        method: "DELETE",
        ...(activeCompanyId
          ? { headers: { "X-Company-Id": activeCompanyId } }
          : {}),
      }),
      invalidatesTags: (_res, _err, { campaignId }) => [
        { type: "ExecutionPlan" as const, id: campaignId },
      ],
    }),
    pushExecutionPlan: builder.mutation<
      SuccessResponse<ExecutionPlanResponse>,
      {
        campaignId: string;
        retryLineIds?: string[];
        lineIds?: string[];
        activeCompanyId?: string;
      }
    >({
      query: ({ campaignId, retryLineIds, lineIds, activeCompanyId }) => ({
        url: `/campaigns/${campaignId}/execution-plan/push`,
        method: "POST",
        data: retryLineIds?.length
          ? { retryLineIds }
          : lineIds?.length
            ? { lineIds }
            : {},
        ...(activeCompanyId
          ? { headers: { "X-Company-Id": activeCompanyId } }
          : {}),
      }),
      // Pushing takes the campaign live — refresh the plan, the campaigns
      // list (status badge) and dashboard widgets.
      invalidatesTags: (_res, _err, { campaignId }) => [
        { type: "ExecutionPlan" as const, id: campaignId },
        "CampaignsList",
      ],
      async onQueryStarted(_arg, { queryFulfilled, dispatch }) {
        try {
          await queryFulfilled;
          dispatch(
            dashboardApi.util.invalidateTags([...DASHBOARD_TAGS_TO_INVALIDATE]),
          );
        } catch {
          // Mutation failed — nothing to invalidate.
        }
      },
    }),
    resetExecutionPlan: builder.mutation<
      SuccessResponse<ExecutionPlanResponse>,
      string
    >({
      query: (campaignId) => ({
        url: `/campaigns/${campaignId}/execution-plan/reset`,
        method: "POST",
      }),
      invalidatesTags: (_res, _err, campaignId) => [
        { type: "ExecutionPlan" as const, id: campaignId },
      ],
    }),
  }),
});

export const {
  useGetSequencerQuery,
  useLazyGetSequencerQuery,
  useCreateCampaignMutation,
  useAutosaveCampaignMutation,
  useGetCampaignQuery,
  useLazyGetCampaignQuery,
  useUpdateCampaignMutation,
  useGetCampaignsQuery,
  useLazyGetCampaignsQuery,
  useGetCountriesQuery,
  useLazyGetCountriesQuery,
  useGetCountriesMarketDetailsQuery,
  useLazyGetCountriesMarketDetailsQuery,
  useGetCountryMarketDetailsByIsoQuery,
  useLazyGetCountryMarketDetailsByIsoQuery,
  useGetCountryByNameQuery,
  useLazyGetCountryByNameQuery,
  useGetMobilityHeatmapQuery,
  useLazyFilterCompaniesQuery,
  useLazyViewCampaignQuery,
  useViewCampaignQuery,
  useLazySplitCostCampaignQuery,
  useSplitCostCampaignQuery,
  useSubmitForReviewMutation,
  useChangeProposalStatusMutation,
  useGetMediaPlanQuery,
  useGetCampaignApprovalDetailsQuery,
  useLazyGetCampaignApprovalDetailsQuery,
  useUpdateApprovalStatusMutation,
  useGetCampaignHistoryQuery,
  useLazyGetCampaignHistoryQuery,
  useBulkActionsCampaignMutation,
  useDeleteCampaignMutation,
  useGetApprovalInboxQuery,
  useGetExecutionPlanQuery,
  useLazyGetExecutionPlanQuery,
  useGetExecutionPlanStatusQuery,
  useGetExecutionWorkspaceQuery,
  useUpdateExecutionLineMutation,
  useCreateExecutionLineMutation,
  useMoveExecutionInventoryMutation,
  useDeleteExecutionLineMutation,
  usePushExecutionPlanMutation,
  useResetExecutionPlanMutation,
} = campaignApi;
export default campaignSlice.reducer;

// Company API (uses proxy base URL)
export const companyApi = createApi({
  reducerPath: "companyApi",
  baseQuery: axiosBaseQuery("BACKEND_URL_PROXY"),
  endpoints: (builder) => ({
    getCompanyMarketAccess: builder.query<CompanyMarketAccessResponse, string>({
      query: (companyId) => ({
        url: `iam-api/api/v1/companies/${companyId}/market-access`,
        method: "GET",
      }),
    }),
  }),
});

export const { useGetCompanyMarketAccessQuery } = companyApi;
